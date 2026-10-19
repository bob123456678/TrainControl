#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
TrainControl manual-test triage - a companion window for the manual tests and the Inbox.

Run it beside TrainControl, work down the ledger, and answer one test at a time: what happened,
anything else you noticed, submit or skip.  Everything you type is written to triage.db, the store
that holds the record (README.md), added and never overwritten.

    py -3 docs\\manual-tests\\triage.py

Why this exists: the ledger is ninety entries long and the instruction for the one you are running
is four hundred lines away from the row that told you to run it.  Alt-tabbing between a long
document and a live railway is where results get lost.

What it writes, and where - all of it through triagedb.py, the store's one door:

  A test result             a comment on that entry, dated, signed, and stamped with the build it
                            was run against.
  A bug or feature request  an OB-### (bug) or FR-### (feature request) item in the Inbox - one
                            inbox for both, cross-referenced from the test's comment if it came
                            from one.

It also has a query API: run it with an argument (stats, tests, test TAG, issues, verify-ledger) and
it prints JSON to stdout and exits, no window involved.  See --help.

What it deliberately does NOT write: the **Disposition** line.  Rule 4 in README.md says
dispositions are Claude's to set and only Claude's, and that rule is the reason the record is
worth anything - it is the difference between "Adam says it works" and "somebody marked it done".
So this app records what you said and leaves the bookkeeping where it belongs.  Your rows keep
their old disposition until the next round reads them.

Safety.  Every write is one short transaction, and a comment is added as a row of its own rather
than by rewriting the entry, so the app and a script writing at the same time cannot undo each
other.  When another process writes to the store the app sees it within two seconds and shows what
is there now, keeping whatever you have typed.  Nothing is ever deleted or reordered.

Single file, no dependencies beyond the standard library.
"""

import glob
import io
import json
import os
import re
import shutil
import socket
import subprocess
import sys
import threading
import time
import datetime

import tkinter as tk
from tkinter import ttk, messagebox, simpledialog


# --------------------------------------------------------------------------------------------
# Where everything lives
# --------------------------------------------------------------------------------------------

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, os.pardir, os.pardir))

README_MD = os.path.join(HERE, "README.md")

BACKUP_DIR = os.path.join(HERE, ".triage-backups")
RUN_DIR = os.path.join(HERE, ".triage-runs")
STATE_FILE = os.path.join(HERE, ".triage-state.json")

BACKUPS_KEPT = 40

# The Central Station's port.  A second copy of TrainControl cannot have it, which is what MT-063
# is about; checking here means the launch button says so before the app does.
CS2_PORT = 15730

# Known good JDK on this machine.  find_java() below checks it ahead of PATH on purpose - see
# that function's docstring - and only ever falls through to it if TC_JAVA_HOME does not name a
# better answer.
FALLBACK_JAVA = r"C:\Program Files\Java\jdk1.8.0_361\bin\java.exe"

VALIDATED = "fixed validated"

# Not run, not counted, not on the ledger - see README.md rule 4.  Kept in the file because its tag is
# cited from commits and other entries, so deleting it would break a reference to explain a number.
SUPERSEDED = "superseded"

# One color per disposition (README.md's four states), used for the dot in the list legend and
# for the row text itself - color plus the word both, not color alone, since the Comments tab and
# the meta line under the title always spell the disposition out too.
DISPOSITION_COLORS = {
    "needs-test": "#1a5fb4",           # has not been looked at, or was deferred - still to do
    "fixed-unvalidated": "#8a5a00",    # Claude believes it, nobody on the railway has confirmed it
    "fixed-validated": "#7a7a7a",      # done; dimmed rather than removed, so it stays in the list
    "superseded": "#9a9a9a",           # wrong shape - tracked elsewhere, or replaced by a later entry
}

# A receipt's State has one more word than a test's disposition ever needs: a REQUEST can
# be declined, where a TEST - which by definition is already being worked - cannot.  Kept separate
# from DISPOSITION_COLORS on purpose, so the tests' four-word rule stays exactly four words.
ISSUE_STATE_COLORS = dict(DISPOSITION_COLORS)
ISSUE_STATE_COLORS["declined"] = "#8a3a3a"         # closed, deliberately not built - not grey (=validated)
# PICKED UP, NOT BUILT (Adam, 2026-09-14: "FR-002 should show as pending").  "needs test" said the
# wrong thing about a request nobody has coded - there is nothing to test - and "pending" was a word
# only an Inbox item could show.  Open, and coloured the way a pending Inbox item already is.
ISSUE_STATE_COLORS["pending"] = DISPOSITION_COLORS["needs-test"]

# README.md rule 4's four documented words, and no others - derived from DISPOSITION_COLORS
# rather than re-typed, so the two cannot say something different from each other.  Nothing
# checked an entry's Disposition against this before verify-ledger's own duplicate/href/date
# checks were added; this is that missing check.
VALID_DISPOSITIONS = set(slug.replace("-", " ") for slug in DISPOSITION_COLORS)

# A bug or feature request is CLOSED when it is validated or declined, and OUT OF VIEW when it was
# declined (Adam, 2026-09-13: "hide cancelled ones from view").  "cancelled" is not a documented
# word - "declined" is - but it was typed into the receipt table twice, and a word the app did not
# know was read as open, so both are accepted here rather than trusting the file to stay tidy.
HIDDEN_ISSUE_STATES = ("declined", "cancelled")
CLOSED_ISSUE_STATES = ("fixed validated",) + HIDDEN_ISSUE_STATES


def derived_disposition(tags, by_tag):
    """The state of a promoted bug or feature request: the state of the tests it became.

    A request promoted to a test has no State of its own - exactly one of State or Became
    is filled in - so its disposition is read from those tests rather than shown as a pointer.
    WORST OPEN STATE WINS: one test still needing a run keeps the request open however many of its
    siblings are validated.  A superseded test says nothing about the request, so it is set aside;
    a request whose every test was superseded answers "superseded", which reads as not done.

    :param tags: the MT-### tags the request became, in the order the row names them
    :param by_tag: the tests, by tag
    :return: the derived disposition, or None when none of the tags is in the store
    """

    states = [by_tag[tag].disposition.strip().lower() for tag in tags if tag in by_tag]

    if not states:
        return None

    live = [state for state in states if state != SUPERSEDED]

    if not live:
        return SUPERSEDED

    for word in ("needs test", "fixed unvalidated"):
        if word in live:
            return word

    return "fixed validated"


def disposition_slug(disposition):
    """'fixed unvalidated' -> 'fixed-unvalidated', a lookup key and a tag name in one."""

    return disposition.strip().lower().replace(" ", "-")


RESULTS = [
    ("works", "Works - does what the test says"),
    ("works with notes", "Works, with notes - right, but something about it is off"),
    ("does not work", "Does not work - the test fails"),
    ("could not run", "Could not run - blocked, missing something, or not reachable"),
]


# --------------------------------------------------------------------------------------------
# Reading and writing text files, carefully
# --------------------------------------------------------------------------------------------

def read_text(path):
    """Read a file, returning (text with \\n line endings, True if it was CRLF on disk)."""

    with io.open(path, "rb") as fh:
        raw = fh.read()

    text = raw.decode("utf-8")
    crlf = "\r\n" in text

    return text.replace("\r\n", "\n"), crlf


def write_text(path, text, crlf):
    """Back the file up, then replace it atomically.

    Used for the reference documents whose quoted counts the store rewrites (triagedb.render_counts):
    a half-written document would cost more than the count is worth.
    """

    if not os.path.isdir(BACKUP_DIR):
        os.makedirs(BACKUP_DIR)

    if os.path.exists(path):
        # %f (microseconds) too - to-the-second was not fine enough: three writes inside one
        # second, which submit()'s observations-then-comment sequence can produce on its own,
        # overwrote each other's backup under the same name.
        stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S-%f")
        name = "%s.%s.bak" % (os.path.basename(path), stamp)
        shutil.copy2(path, os.path.join(BACKUP_DIR, name))

    out = text.replace("\n", "\r\n") if crlf else text

    tmp = path + ".triage-tmp"

    try:
        with io.open(tmp, "wb") as fh:
            fh.write(out.encode("utf-8"))

        os.replace(tmp, path)

    finally:
        # os.replace raises PermissionError when OneDrive still holds `path` open - leaving the
        # temp file sitting there rather than the replace having happened.  Removed here rather
        # than left behind for the next write to trip over (or for someone to find and wonder
        # whether it is the real file).
        if os.path.exists(tmp):
            try:
                os.remove(tmp)
            except OSError:
                pass

    prune_backups()


def prune_backups():
    """Keep the last N backups per file, so the folder does not grow without limit."""

    try:
        by_file = {}

        for name in os.listdir(BACKUP_DIR):
            # The file's whole name, less the stamp: "open-questions.md.<stamp>.bak".  The first dot
            # used to end it, which put files of one stem in one pool of forty.
            base = name[:-len(".bak")].rsplit(".", 1)[0] if name.endswith(".bak") else name
            by_file.setdefault(base, []).append(name)

        for base, names in by_file.items():
            names.sort()

            for name in names[:-BACKUPS_KEPT]:
                os.remove(os.path.join(BACKUP_DIR, name))

    except OSError:
        pass


ANCHOR_RE = re.compile(r'^<a id="(mt-\d+)"></a>\s*$', re.M)
HEADING_RE = re.compile(r'^### (MT-\d+) - (\d{4}-\d{2}-\d{2}) - (.*)$', re.M)
FIELD_RE = r'^\*\*%s:?\*\*[ \t]*(.*?)[ \t]*$'


class Entry(object):
    """One MT-### test: its whole text as the store holds it, and the fields read out of that text."""

    def __init__(self, anchor, block):
        self.anchor = anchor
        self.block = block

        head = HEADING_RE.search(block)

        self.tag = head.group(1) if head else anchor.upper()
        self.date = head.group(2) if head else "?"
        self.title = head.group(3).strip() if head else "(no title)"

        self.disposition = self._field("Disposition") or "?"
        self.origin = self._field("From") or "-"
        self.written = self._field("Written") or self.date

        self.what = self._section_what()
        self.comments = self._section_comments()

    @property
    def reopened(self):
        """Whether this is waiting on Adam AGAIN - he judged it, and something has changed since.

        The Tests list could not tell these apart from entries nobody has ever run, which are the two
        things he most needs to tell apart: one is "look at this again, it should be different now" and
        the other is "nobody has ever tried this". Adam: "I can't easily find reopened MT's in the
        triage view."

        There is no fifth disposition for it and there should not be - the four states say what is TRUE
        of an entry, and "he looked at it before" is a fact about its history rather than its state. So
        it is computed from the comments, which already record that history: the newest comment is
        Claude's and there is an Adam verdict above it.
        """

        if not self.is_open:
            return False

        # Any '**Adam, ...**' marker, not only one this app itself wrote - "(triage)." used to
        # be required, so a verdict Adam typed straight into the file by hand never counted as
        # his, and an entry could sit reopened-in-fact with nothing on screen saying so.
        mine = [m.start() for m in re.finditer(r"\*\*Adam,[^*]*\*\*", self.block)]

        if not mine:
            return False

        theirs = [m.start() for m in re.finditer(r"\*\*Claude,[^*]*\*\*", self.block)]
        # AND the explicit marker this app writes when it reopens something. A reopening does not
        # always come with a Claude comment after it - MT-254 was reopened because the control it
        # tests is MOVING, which is a note about the entry rather than a reply to Adam - and
        # without this it read as never-triaged for six days, in the very view built to tell those
        # two apart.
        theirs += [m.start() for m in re.finditer(r"\*\*Re-opened\b", self.block)]

        return bool(theirs) and max(theirs) > max(mine)

    def _field(self, name):
        m = re.search(FIELD_RE % name, self.block, re.M)
        return m.group(1).strip() if m else None

    def _section_what(self):
        """Everything between the Written line and the Comments heading."""

        start = self.block.find("**What to do")

        if start < 0:
            m = re.search(FIELD_RE % "Written", self.block, re.M)
            start = m.end() if m else 0

        end = self.block.find("#### Comments")

        if end < 0:
            end = len(self.block)

        body = self.block[start:end].strip()

        return re.sub(r'^\*\*What to do\.?\*\*\s*', "", body).strip()

    def _section_comments(self):
        start = self.block.find("#### Comments")

        if start < 0:
            return ""

        body = self.block[start + len("#### Comments"):]

        cut = body.rfind("\n---")

        if cut >= 0:
            body = body[:cut]

        return body.strip()

    @property
    def is_open(self):
        """Whether this entry is still asking somebody for something.

        Superseded counts as closed. It is not finished in the sense fixed validated is - nobody ran
        it and nothing was proved - but it is not outstanding either, and the ledger is a list of what
        is outstanding. See README.md rule 4 for when that state may be used, which is narrower than it
        looks.
        """
        state = self.disposition.strip().lower()

        return state != VALIDATED and state != SUPERSEDED

def parse_tests_text(text):
    """A test file's text, split into the head and one Entry per anchor.

    The markdown is read once, by `triagedb.migrate`, and this is how.  Nothing else parses it.

    :param text: the whole file
    :return: (head, list of Entry)
    """

    marks = [(m.start(), m.group(1)) for m in ANCHOR_RE.finditer(text)]

    head = text[:marks[0][0]] if marks else text

    entries = []

    for i, (pos, anchor) in enumerate(marks):
        end = marks[i + 1][0] if i + 1 < len(marks) else len(text)
        entries.append(Entry(anchor, text[pos:end]))

    return head, entries


class TestsDoc(object):
    """Every test, read from the store (triagedb), with the one write this app makes to one: a comment."""

    def __init__(self, conn):
        """
        :param conn: an open connection to the store
        """

        self.conn = conn
        self.load()

    def load(self):
        """Reads every test from the store again.

        Imported inside the method rather than at the top of the file: triagedb imports this module,
        and doing it the other way round at import time would be a cycle.
        """

        import triagedb

        # What the store's write counter said when this was read: another process's commit moves it.
        self.version = triagedb.data_version(self.conn)

        self.entries = triagedb.entries(self.conn)

        # The store's key is the tag, so one cannot be held twice.  Kept for the status line's check.
        self.duplicate_tags = []

        self.by_tag = dict((e.tag, e) for e in self.entries)

    def changed_elsewhere(self):
        """Whether another process has written to the store since this was read.

        :return: true when it has
        """

        import triagedb

        try:
            return triagedb.data_version(self.conn) != self.version
        except Exception:
            return False

    def append_comment(self, tag, comment):
        """Adds one comment to one entry, in the store, and reads the store again.

        A comment is a row of its own: the entry is not read and written back, so a script writing to the
        store at the same moment cannot be undone by this, or undo it.  A comment written by this window
        is indistinguishable from one written by the API, which matters, because Adam's verdicts arrive
        through this window and everything that reads them arrives through the other door.

        :param tag: MT-###
        :param comment: the whole comment, already formatted
        :return: None, or a warning when the comment is in the store and the text dump could not follow
        """

        import triagedb

        try:
            triagedb.add_comment_text(self.conn, tag, comment)
        except triagedb.DumpNotWritten as bad:
            return str(bad)
        finally:
            self.load()

        return None


# A bug is OB-###; a feature request is FR-###.  Separate counters, because they used to share
# one - which was fine for telling entries apart, but meant a glance at the ref alone couldn't
# tell a reader which lifecycle an item was on (bugs promoted to a test, most feature requests
# tracked directly with their own State - see the retirement of MT-094).  OB keeps counting from
# where it already was; nothing already filed is renumbered.
ISSUE_PREFIXES = {"bug": "OB", "feature request": "FR"}


def normalize_kind(raw):
    """An Inbox item's Kind field, normalised - lowercase, and 'feature' folded into 'feature
    request' the way this file uses it everywhere else.

    Returns (kind, recognized).  A hand-written Kind reads fine to a person as 'Bug' or
    'feature', but compared with '==' against 'bug'/'feature request' - which is how the issue
    tabs, cli_issues --kind, and the numbering of a new item all read it - either one is
    exactly unequal, so the item was invisible in both tabs and in the API, and a feature
    request's ref got filed under the BUG counter (ISSUE_PREFIXES.get(kind, "OB") falls back to
    OB for anything that is not the exact string 'feature request').

    An unrecognised value (not bug/feature/feature request in any case) still falls back to
    'bug', the same default ISSUE_PREFIXES already used - but 'recognized' comes back False so
    the caller can warn about it rather than silently miscounting it, which is the one thing the
    old exact-match behaviour never did.
    """

    text = (raw or "").strip().lower()

    if text == "bug":
        return "bug", True

    if text in ("feature request", "feature"):
        return "feature request", True

    return "bug", False


def format_ref(kind, number):
    return "%s-%03d" % (ISSUE_PREFIXES.get(kind, "OB"), number)


# --------------------------------------------------------------------------------------------
# The Inbox and the receipt table, as the store holds them
# --------------------------------------------------------------------------------------------

ISSUE_ITEM_RE = re.compile(
    r'^### ((?:OB|FR)-\d+) - (\d{4}-\d{2}-\d{2}) - (.*)$', re.M)

# What a '**Field:**' line looks like, generically - bold text ending in a colon before the
# closing '**'.  Used to find where the block of fields ends and the free-text body begins,
# without naming any one field: a bold body phrase ("**Not to be fixed on its own.**") ends in a
# period, not a colon, so it never matches this.
FIELD_LINE_RE = re.compile(r'^\*\*[^*\n]+:\*\*')


class IssueItem(object):
    """One OB-### (bug) or FR-### (feature request) filed into the Inbox: the fields read out of its
    text as the store holds it.
    """

    def __init__(self, ref, filed_date, summary, block):
        self.ref = ref
        self.filed = filed_date
        self.summary = summary.strip()
        self.kind_raw = self._field(block, "Kind") or "?"
        self.kind, self.kind_recognized = normalize_kind(self.kind_raw)
        self.raised_from = self._field(block, "Raised from") or "-"
        self.filed_at = self._field(block, "Filed") or filed_date
        self.build = self._field(block, "Build") or "-"
        self.detail = self._detail(block)

    @staticmethod
    def _field(block, name):
        m = re.search(FIELD_RE % name, block, re.M)
        return m.group(1).strip() if m else None

    @staticmethod
    def _detail(block):
        """The free-text body: everything after the run of '**Field:**' lines that follows the
        heading - i.e. after the first blank line that closes that run - falling back to the
        whole post-heading body when there are no field lines at all.

        Used to be anchored on '**Build:**' specifically, which dropped the entire body of any
        hand-written item that has no Build line - OB-053, OB-025, FR-013, OB-067 and FR-017,
        including OB-025's recorded instruction "leave OB-25 open".  Structural instead, so it
        does not care which fields an entry happens to have or skip.
        """

        nl = block.find("\n")
        lines = block[nl + 1:].split("\n") if nl >= 0 else []

        i = 0

        while i < len(lines) and not lines[i].strip():
            i += 1

        saw_field = False

        while i < len(lines) and FIELD_LINE_RE.match(lines[i].strip()):
            saw_field = True
            i += 1

        if saw_field:
            while i < len(lines) and not lines[i].strip():
                i += 1

        return "\n".join(lines[i:]).strip()

    def as_dict(self):
        d = {
            "ref": self.ref,
            "filed": self.filed,
            "kind": self.kind,
            "summary": self.summary,
            "raised_from": self.raised_from,
            "build": self.build,
            "detail": self.detail,
        }

        if not self.kind_recognized:
            # kind above is already the normalize_kind() fallback ('bug') - this says so, rather
            # than leaving a caller to assume it was filed as a bug on purpose.
            d["kind_raw"] = self.kind_raw

        return d


class IssuesDoc(object):
    """The Inbox and the receipt table of what has been picked up, read from the store (triagedb).

    `pending` is what is in the Inbox and has no receipt; `picked` is the receipt table, newest first,
    as dicts keyed by its columns - filed, ref, kind, what, state, became, and `became_tags`, every
    MT-### the row names.

    PENDING MEANS NOT YET PICKED UP (Adam: "Note that I don't see anything for the outstanding OB's").
    An item with a receipt is its MT entry's business, not the Inbox's, whether or not it has been
    cleared out of it yet.
    """

    def __init__(self, conn):
        """
        :param conn: an open connection to the store
        """

        self.conn = conn
        self.load()

    def load(self):
        import triagedb

        self.picked = triagedb.receipts(self.conn)

        self.picked_up_refs = set(row["ref"] for row in self.picked)

        self.pending = [it for it in triagedb.inbox_items(self.conn) if it.ref not in self.picked_up_refs]

        # Anything once typed above the first item, by hand, with no structure: kept by the migration,
        # and shown in the status line so it is not as good as lost.
        self.freeform = triagedb.note(self.conn, "issues.inbox-lead").replace("## Inbox", "", 1).strip()

        # The store's key is the ref, so one cannot be filed twice.  Kept for the status line's check.
        self.duplicate_pending_refs = []

        # normalize_kind() keeps these visible (filed under 'bug', its own fallback); this is what lets
        # the caller warn about them too.
        self.unrecognized_kind_refs = [it.ref for it in self.pending if not it.kind_recognized]

    def file(self, kind, summary, detail, raised_from, build):
        """Files one item into the Inbox, through the store, and reads it again.

        The number is taken inside the store's write, so a script filing at the same moment cannot be
        handed the same one.

        :return: (the ref it was filed as, None or a warning when the text dump could not follow)
        """

        import triagedb

        warning = None

        try:
            ref = triagedb.file_issue(self.conn, kind, summary, detail, raised_from=raised_from, build=build)
        except triagedb.DumpNotWritten as bad:
            ref, warning = bad.result, str(bad)
        finally:
            self.load()

        return ref, warning


# --------------------------------------------------------------------------------------------
# Session state - the app's own scratch, not part of the record
# --------------------------------------------------------------------------------------------

class State(object):
    """Drafts, per-tag session marks and window geometry.  Local only; not committed."""

    def __init__(self):
        self.data = {"marks": {}, "drafts": {}, "geometry": "", "filter": "open"}

        try:
            with io.open(STATE_FILE, "r", encoding="utf-8") as fh:
                loaded = json.load(fh)

            if isinstance(loaded, dict):
                # A SESSION MARK DOES NOT SURVIVE THE SESSION, which is what it has always claimed.
                #
                # Adam, three times in one evening: "I don't see 20 in the tracker", "I don't see
                # anything after 087", and "I just restarted, still can't see the MTs".  The marks were
                # loaded straight back out of this file on every launch and nothing ever cleared them,
                # so "I dealt with this in this session" became "I dealt with this, ever" - and the
                # default filter, "open - not yet answered here", drops every entry carrying one.
                #
                # Measured before this was changed: 166 tags marked done, including ALL TWELVE tests
                # that were open at the time.  The list was empty and correct by its own rules, which
                # is the worst way for a tool to be wrong - it looked like nothing needed doing.
                #
                # The reopened override in _visible() was written for a narrower version of this same
                # staleness and is not enough on its own: it only lifts a mark on an entry that has
                # BOTH his verdict and a later reply, so a test he answered and nobody has touched
                # since stays hidden for ever.  That override stays; this is the other half.
                #
                # Everything else in the file is a setting and does survive: a half-typed comment lost
                # on restart would be its own bug, and so would a window that forgets where it was.
                loaded.pop("marks", None)

                self.data.update(loaded)

        except Exception:
            pass

    def save(self):
        try:
            with io.open(STATE_FILE, "w", encoding="utf-8") as fh:
                fh.write(json.dumps(self.data, indent=1, ensure_ascii=False))
        except Exception:
            pass

    def mark(self, tag, value=None):
        if value is None:
            return self.data["marks"].get(tag, "")

        self.data["marks"][tag] = value
        self.save()

    def clear_mark(self, tag):
        self.data["marks"].pop(tag, None)
        self.save()

    def draft(self, tag, value=None):
        if value is None:
            return self.data["drafts"].get(tag)

        self.data["drafts"][tag] = value

    def clear_draft(self, tag):
        self.data["drafts"].pop(tag, None)
        self.save()


# --------------------------------------------------------------------------------------------
# Launching TrainControl
# --------------------------------------------------------------------------------------------

TARGET_JAVA_VERSION = "1.8"       # nbproject/project.properties: javac.source=1.8, javac.target=1.8


def _java_version_report(path):
    """'java -version' prints to stderr, e.g. 'java version "1.8.0_361"'.  Empty on any failure."""

    try:
        out = subprocess.run([path, "-version"], capture_output=True, text=True, timeout=5)
        return (out.stderr or "") + (out.stdout or "")
    except Exception:
        return ""


def find_java(require_javac=False):
    """The java to launch or compile TrainControl with, verified rather than assumed.

    JAVA_HOME is not safe to trust blindly on a real dev machine.  On this one it points at
    Android Studio's bundled JetBrains Runtime - JDK 21, with its own IDE-tuned HiDPI scaling -
    because Android Studio sets it machine-wide for its own use.  Nothing about that is specific
    to TrainControl, so launching an old Java 8 Swing app under it is exactly how a launch works
    but "the display scaling is completely off": right app, wrong runtime, one nobody built or
    tested it against.

    So every candidate is checked with its own `-version` rather than trusted for existing, in
    this priority order:

      1. TC_JAVA_HOME.  Nothing else on this machine sets this variable, so if it is present it
         can only mean someone is deliberately pointing this tool at a JDK, which outranks any
         guess this function would otherwise make.
      2. The known-good install this project's own CLI test harness already uses (FALLBACK_JAVA).
         This goes ahead of PATH on purpose: on this machine PATH's `java` resolves to Oracle's
         auto-updater stub (java8path), which reports a perfectly convincing "1.8.0_something"
         but has no javac sitting next to it, so letting it win here would trade the JAVA_HOME
         scaling bug below for a silent compile failure instead of actually fixing anything.
      3. `java` on PATH.
      4. JAVA_HOME, checked last for the reason given above.

    A candidate is only ever returned if it reports 1.8.  If nothing on the machine verifies as
    1.8, the first candidate that exists is still returned so a launch remains possible, but the
    caller is told the version was never confirmed, since guessing silently is exactly the bug
    being fixed here.

    require_javac restricts candidates to ones with a javac.exe sitting next to the java.exe.
    Launching never needs a compiler, so launch_plan() leaves this False; compile_plan() sets it
    True, because deriving JAVA_HOME from a javac-less JRE hands Ant a folder it cannot compile
    with, and Ant's own error for that names neither the missing javac nor this script - see the
    java8path case above, which is exactly that trap.

    Returns (path_or_None, verified_bool, tried).  `tried` is every (label, path) candidate this
    call actually constructed, in the order checked, purely so a "nothing found" message can say
    what it looked for instead of just failing.
    """

    tried = []
    candidates = []

    def consider(label, path):
        if not path or path in [seen for _, seen in tried]:
            return

        tried.append((label, path))

        if not os.path.exists(path):
            return

        if require_javac and not os.path.exists(
                os.path.join(os.path.dirname(path), "javac.exe")):
            return

        candidates.append(path)

    override = os.environ.get("TC_JAVA_HOME")

    if override:
        consider("TC_JAVA_HOME", os.path.join(override, "bin", "java.exe"))

    consider("known-good default", FALLBACK_JAVA)

    consider("java on PATH", shutil.which("java"))

    home = os.environ.get("JAVA_HOME")

    if home:
        consider("JAVA_HOME", os.path.join(home, "bin", "java.exe"))

    for c in candidates:
        if TARGET_JAVA_VERSION in _java_version_report(c):
            return c, True, tried

    return (candidates[0], False, tried) if candidates else (None, False, tried)


def _describe_tried(tried):
    """Turns find_java()'s candidate trail into one line for an error message - what was
    checked, and whether it actually existed - so "nothing found" says where it looked instead
    of just failing.
    """

    if not tried:
        return "nothing to check - TC_JAVA_HOME, PATH and JAVA_HOME were all unset"

    return "; ".join(
        "%s (%s)%s" % (label, path, "" if os.path.exists(path) else " - not found")
        for label, path in tried)


def newest_mtime(folder):
    newest = 0

    for path, _dirs, files in os.walk(folder):
        for name in files:
            try:
                newest = max(newest, os.path.getmtime(os.path.join(path, name)))
            except OSError:
                pass

    return newest


def project_jars():
    """The runtime classpath, taken from project.properties so a version bump follows by itself."""

    jars = []

    props = os.path.join(ROOT, "nbproject", "project.properties")

    if os.path.exists(props):
        text, _ = read_text(props)

        for ref in re.findall(r'^file\.reference\.[^=]+=(.*)$', text, re.M):
            ref = ref.strip()

            if "resources_test" in ref:
                continue

            full = os.path.join(ROOT, ref.replace("/", os.sep))

            if os.path.exists(full):
                jars.append(full)

    # AbsoluteLayout comes from NetBeans itself rather than the project, so it is only ever found
    # next to the built jar.
    lib = os.path.join(ROOT, "dist", "lib")

    if os.path.isdir(lib):
        for name in sorted(os.listdir(lib)):
            if name.lower().endswith(".jar"):
                full = os.path.join(lib, name)

                if os.path.basename(full) not in [os.path.basename(j) for j in jars]:
                    jars.append(full)

    return jars


def launch_plan(language=None, regular=False):
    """What would be run, and how fresh it is.

    :param language: one of LANGUAGES to run it in, or None for whatever this computer's is
    :param regular: True to start it as it starts on its own - no simulation, no debug, connecting to the Central
        Station (Adam, 2026-09-29: "launch TC in regular mode (without debug+simulate) via the tools menu")

    Prefers build/classes, which NetBeans refreshes on every Run, over dist/TrainControl.jar, which
    only moves on a Clean and Build.  Which one is in use is reported rather than assumed: a result
    recorded against a stale jar is the exact failure the disposition rule exists to stop.
    """

    java, verified, tried = find_java()

    if not java:
        return None, (
            "No Java 8 found.  Tried: %s.  Set TC_JAVA_HOME (or JAVA_HOME) to a JDK 8 install, "
            "or put java on the PATH." % _describe_tried(tried))

    java_note = java if verified else (
        "%s - COULD NOT CONFIRM THIS IS JAVA 8, the version TrainControl is built for; if the "
        "display looks wrong after launch, this is the first thing to check" % java)

    classes = os.path.join(ROOT, "build", "classes")
    jar = os.path.join(ROOT, "dist", "TrainControl.jar")

    # IP, debug, simulate - the Simulate run configuration; none at all in regular mode, as TrainControl is started on its
    # own: TrainControl.main reads simulate from a third argument and debug from a second
    args = [] if regular else ["0", "1", "1"]

    # IN THE LANGUAGE ASKED FOR, first in the note: the note is the running label, which a narrow window cuts off at the
    # end, and it is stamped on every result submitted, so a result says which language it was seen in.
    props = []
    # REGULAR MODE SAID FIRST, where it is also stamped on every result submitted from the run
    said = "regular mode (no simulate, no debug) - " if regular else ""

    if language:
        props = ["-Duser.language=%s" % language[0], "-Duser.country=%s" % language[1]]
        said += "in %s - " % language[2]

    if os.path.isdir(classes):
        stamp = newest_mtime(classes)

        if stamp:
            jars = project_jars()
            classpath = os.pathsep.join([classes] + jars)

            return (
                [java] + props + ["-cp", classpath, "TrainControl"] + args,
                "%sbuild\\classes, compiled %s - java: %s"
                % (said, time.strftime("%d %b %H:%M", time.localtime(stamp)), java_note),
            )

    if os.path.exists(jar):
        stamp = os.path.getmtime(jar)

        return (
            [java] + props + ["-jar", jar] + args,
            "%sdist\\TrainControl.jar, built %s (build\\classes is missing - this may be old) - "
            "java: %s" % (said, time.strftime("%d %b %H:%M", time.localtime(stamp)), java_note),
        )

    return None, "Neither build\\classes nor dist\\TrainControl.jar exists.  Build in NetBeans first."


# NetBeans' own bundled ant - the one "Build Project" (F11) itself runs.  Versioned installs are
# tried newest first; this machine has NetBeans-18, but nothing here should break for whoever
# upgrades it later.
ANT_GLOB = r"C:\Program Files\NetBeans-*\netbeans\extide\ant\bin\ant.bat"


def find_ant():
    found = sorted(glob.glob(ANT_GLOB), reverse=True)

    if found:
        return found[0]

    return shutil.which("ant")


def compile_plan():
    """The same thing NetBeans' own Build Project runs: 'ant compile' - not 'clean', which is the
    one step that fights OneDrive over a directory it may still be syncing (see
    traincontrol-cli-test-harness in Claude's memory).  Ordinary incremental compile just
    overwrites .class files in place and has never shown that problem.

    Forces JAVA_HOME to the same verified JDK 8 launch_plan() insists on, for the same reason:
    Ant is a JVM too, and this machine's ambient JAVA_HOME is Android Studio's JBR, not this
    project's compiler.  Unlike launch_plan(), this calls find_java(require_javac=True): Ant
    needs a real compiler, not just a runtime, so a java that verifies as 1.8 but has no javac
    next to it (PATH's java8path stub on this machine, for instance) is rejected here rather
    than being handed to Ant as JAVA_HOME and failing three steps later with no mention of why.
    """

    ant = find_ant()

    if not ant:
        return None, None, "No NetBeans ant found (looked for %s) and none on PATH." % ANT_GLOB

    java, verified, tried = find_java(require_javac=True)

    if not java:
        # A java that exists but was rejected for having no javac is a different, more specific
        # problem than no java at all, and worth naming as such rather than reporting the same
        # generic "not found" for both.
        any_java, _, any_tried = find_java()

        if any_java:
            return None, None, (
                "Found java (%s) but no javac next to it - that is a JRE, not a JDK, and Ant "
                "cannot compile without a compiler.  Set TC_JAVA_HOME (or JAVA_HOME) to a JDK 8 "
                "install, such as %s." % (any_java, FALLBACK_JAVA))

        return None, None, (
            "No java found to run Ant with.  Tried: %s.  Set TC_JAVA_HOME (or JAVA_HOME) to a "
            "JDK 8 install, or put java on PATH." % _describe_tried(any_tried))

    env = dict(os.environ)
    env["JAVA_HOME"] = os.path.dirname(os.path.dirname(java))     # .../bin/java.exe -> ...

    note = "ant compile, java: %s" % java

    if not verified:
        note += " (COULD NOT CONFIRM this is Java 8)"

    return [ant, "compile"], env, note


def port_held(port=CS2_PORT):
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)

    try:
        sock.bind(("", port))
        return False
    except OSError:
        return True
    finally:
        sock.close()


def git_build():
    try:
        out = subprocess.check_output(
            ["git", "rev-parse", "--short", "HEAD"], cwd=ROOT,
            stderr=subprocess.DEVNULL, universal_newlines=True,
        )
        return out.strip()
    except Exception:
        return None


# --------------------------------------------------------------------------------------------
# The window
# --------------------------------------------------------------------------------------------

PAD = 8

# The languages TrainControl has message bundles for, each named in itself: (language, country, name).  TrainControl
# takes its language from Java's default locale, which these set on the command line - and so do Swing's own OK and
# Cancel, which follow the same locale.
LANGUAGES = [
    ("en", "US", "English"),
    ("da", "DK", "Dansk"),
    ("de", "DE", "Deutsch"),
    ("es", "ES", "Español"),
    ("fr", "FR", "Français"),
    ("it", "IT", "Italiano"),
    ("nl", "NL", "Nederlands"),
    ("pl", "PL", "Polski"),
]

# What the Show menu offers.  The first is the default, and what an unknown saved value falls back to.
FILTERS = [
    "open - not yet answered here",
    "open - everything not validated",
    "reopened - changed since your verdict",
    "answered this session",
    "everything, validated included",
]

# Inline markdown the read-only panes draw: a code span, a link (shown as its text), bold, italic - tried in that
# order, so that nothing inside a code span is read as emphasis, and an asterisk inside a word is not italic.
_INLINE = re.compile(
    r"(?P<code>`[^`\n]+`)"
    r"|(?P<link>\[(?P<label>[^\]\n]+)\]\((?P<href>[^)\s]+)\))"
    r"|(?P<bold>\*\*(?=\S)(?P<btext>.+?)(?<=\S)\*\*)"
    r"|(?P<italic>(?<![*\w])\*(?=[^\s*])(?P<itext>[^*\n]+?)(?<=\S)\*(?![*\w]))")

# An MT-### named in an entry's text, or in a link's address - `tests.md#mt-123`, which the text keeps as it was
# written though the file has gone.  Either is drawn as a link that jumps to that entry.
_TAG_IN_TEXT = re.compile(r"\bMT-(\d+)\b")
_TAG_IN_HREF = re.compile(r"#mt-(\d+)\b", re.I)

# Which layout a saved window size belongs to.  2 is the stacked one (2026-09-24); a size saved by the side-by-side
# layout is 1024 pixels wide and is not restored into this one.
LAYOUT = 2


# A line that starts a new block in markdown rather than continuing the one above it: a list item, numbered or not, a
# heading, a table row, a quotation or a code fence.
_BLOCK_START = re.compile(r"^\s*(?:[-*+]\s|\d+[.)]\s|#|\||>|```)")


def reflow(text):
    """Markdown as it reads at the width on screen: the lines of one paragraph joined, everything else kept.

    Older entries are wrapped at about 110 characters in the file.  A window a third of the screen wide wraps each of
    those lines again, which left a stub of two or three words at the end of every one.  Joined, the paragraph wraps
    once.  Only what is shown changes; the file is written exactly as it was.

    A line joins the one above it when both have text, neither is the start of a list item, heading, table row,
    quotation or code fence (a list item's own indented continuation lines do join it), the line above does not end in
    a hard break, and it is not inside a fenced code block.
    """

    out = []
    fenced = False
    joinable = False

    for line in text.split("\n"):
        if line.strip().startswith("```"):
            fenced = not fenced
            out.append(line)
            joinable = False
            continue

        if fenced or not line.strip():
            out.append(line)
            joinable = False
            continue

        starts_block = bool(_BLOCK_START.match(line))

        if joinable and not starts_block:
            out[-1] = out[-1].rstrip() + " " + line.strip()
        else:
            out.append(line)

        last = out[-1]

        joinable = not (last.endswith("  ") or last.endswith("\\") or last.lstrip().startswith(("#", "|")))

    return "\n".join(out)


def work_area(widget):
    """The screen less the taskbar, as (left, top, right, bottom) in the coordinates Tk uses.

    Windows answers it; anywhere else, or if asking fails, the whole screen stands in.
    """

    try:
        import ctypes
        from ctypes import wintypes

        rect = wintypes.RECT()

        if ctypes.windll.user32.SystemParametersInfoW(0x0030, 0, ctypes.byref(rect), 0):     # SPI_GETWORKAREA
            return rect.left, rect.top, rect.right, rect.bottom
    except Exception:
        pass

    return 0, 0, widget.winfo_screenwidth(), widget.winfo_screenheight()


def on_a_screen(geometry):
    """Whether a window saved at this geometry, "WxH+X+Y", would show on a connected monitor.

    A window closed on a monitor since disconnected would otherwise open where nobody can see it, so a saved
    position is checked before it is used (Adam, 2026-10-12).  Windows is asked whether either end of the title
    bar is on a monitor; one end on screen is enough to see the window and drag it back.  Anywhere else, or if
    asking fails, the geometry counts as on a screen, as it always did.  A geometry that does not parse is not.
    """

    match = re.match(r"^(\d+)x(\d+)(\+-?\d+|-\d+)(\+-?\d+|-\d+)$", geometry or "")

    if not match:
        return False

    width = int(match.group(1))
    x = int(match.group(3).lstrip("+"))     # Tk writes a negative offset as "+-1382"
    y = int(match.group(4).lstrip("+"))

    try:
        import ctypes
        from ctypes import wintypes

        monitor_from_point = ctypes.windll.user32.MonitorFromPoint
        monitor_from_point.argtypes = [wintypes.POINT, wintypes.DWORD]
        monitor_from_point.restype = ctypes.c_void_p      # NULL comes back as None

        for px in (x + 40, x + width - 40):
            if monitor_from_point(wintypes.POINT(px, y + 10), 0):     # MONITOR_DEFAULTTONULL
                return True

        return False
    except Exception:
        return True


class Triage(tk.Tk):

    def __init__(self):
        tk.Tk.__init__(self)

        self.title("TrainControl - manual test triage")

        import triagedb

        self.state_ = State()
        self.conn = triagedb.connect()
        self.doc = TestsDoc(self.conn)
        self.build = git_build()

        self.process = None
        self.log_path = None
        self.launched_from = None

        self.compile_process = None
        self.compile_log_path = None

        self.current = None
        self.observations = []       # pending, for the entry on screen

        # Set when a comment fails to write after its observations were already filed - (tag,
        # composed comment text) - so a retry reuses the exact text that names those observations'
        # refs instead of recomposing one that would show none filed, since self.observations is
        # cleared the moment they are safely in the Inbox.  See submit().
        self.pending_comment = None

        self.issues_doc = None
        self.issue_widgets = {}      # kind -> {tree, detail, request_cancel_button, open_button, open_tag}

        self._build_ui()
        self._refresh_list(select_first=True)
        self._refresh_issue_tabs()

        # A THIRD OF THE SCREEN, FULL HEIGHT, the first time (Adam, 2026-09-24: "I have about 1/3 width and full
        # height available").  A size saved by the side-by-side layout is not this window's, so it starts in the right
        # third instead - once; from then on its own size and dividers are remembered.
        geometry = self.state_.data.get("geometry")

        # A position on a monitor since disconnected would open off screen, where nobody can see it.
        if geometry and self.state_.data.get("layout") == LAYOUT and on_a_screen(geometry):
            self.geometry(geometry)
            self.after(200, self._restore_dividers)
        else:
            self.geometry("500x860")
            self.after(200, lambda: self.dock("right"))

        self.state_.data["layout"] = LAYOUT

        self.protocol("WM_DELETE_WINDOW", self._on_close)

        self.bind("<Control-r>", lambda e: self.reload())
        self.bind("<Control-Return>", lambda e: self.submit())
        self.bind("<Control-l>", lambda e: self.launch())
        self.bind("<Control-n>", lambda e: self.free_observation())

        self.after(20000, self._autosave)

        # ANOTHER PROCESS'S WRITE IS SHOWN, not written over: a round adding a comment while this is open
        # is on screen within two seconds (see _reread).
        self.after(2000, self._watch_store)

    # -- construction ------------------------------------------------------------------------

    def _build_ui(self):
        style = ttk.Style(self)

        try:
            style.theme_use("vista")
        except tk.TclError:
            pass

        style.configure("Tag.TLabel", font=("Segoe UI", 12, "bold"))
        style.configure("Sub.TLabel", foreground="#555555")
        style.configure("Big.TButton", font=("Segoe UI", 10, "bold"))

        # Before the menu, whose Show items are bound to it.
        self.filter_var = tk.StringVar(value=self.state_.data.get("filter", FILTERS[0]))

        if self.filter_var.get() not in FILTERS:
            self.filter_var.set(FILTERS[0])

        # The language TrainControl is launched in, by its name; remembered with the rest of the state.
        names = [name for _, _, name in LANGUAGES]

        self.language_var = tk.StringVar(value=self.state_.data.get("language", names[0]))
        self.regular_var = tk.BooleanVar(value=bool(self.state_.data.get("regular", False)))

        if self.language_var.get() not in names:
            self.language_var.set(names[0])

        self._build_menu()
        self._build_toolbar()

        # THE STATUS LINE BEFORE THE PANES.  Packed after them it was given what they left, and together they ask for
        # more height than the window has, so it was given nothing - and it is where the Show filter is named.
        self.status = ttk.Label(self, anchor=tk.W, relief=tk.SUNKEN, padding=(6, 3))
        self.status.pack(fill=tk.X, side=tk.BOTTOM)

        # THE LIST ABOVE THE TEST, NOT BESIDE IT (2026-09-24).  This runs beside TrainControl in about a third of the
        # screen's width and all of its height.  Side by side, the list and the steps each had half of a third, and the
        # toolbar ran off the edge; stacked, each has the whole width, and the height - of which there is plenty - is
        # shared by three panes whose dividers can be dragged: the list, the steps, and the answer.
        self.panes = ttk.Panedwindow(self, orient=tk.VERTICAL)
        self.panes.pack(fill=tk.BOTH, expand=True, padx=PAD, pady=(0, PAD))

        detail, self.answer_pane = self._build_detail(self.panes)

        self.panes.add(self._build_left(self.panes), weight=0)
        self.panes.add(detail, weight=1)
        self.panes.add(self.answer_pane, weight=0)

        self._say("Ready.  %d entries, %d open." % (
            len(self.doc.entries), sum(1 for e in self.doc.entries if e.is_open)))

    def _build_menu(self):
        bar = tk.Menu(self)

        f = tk.Menu(bar, tearoff=0)
        f.add_command(label="Reload from the store\tCtrl+R", command=self.reload)
        f.add_separator()

        # THE MARKDOWN HAS GONE (FR-136 Phase 1c), and with it "Open tests.md".  The text dump is the
        # record written out as plain text on every write - for reading, never for editing.
        f.add_command(label="Open the text dump folder", command=self._open_dump)
        f.add_separator()
        f.add_command(label="Quit", command=self._on_close)
        bar.add_cascade(label="File", menu=f)

        # THE SHOW FILTER IS A MENU (Adam, 2026-09-24: "move the "show" options into the menubar").  It drives all
        # three tabs (_on_filter_changed); which one is chosen is ticked here and named at the start of the status line.
        s = tk.Menu(bar, tearoff=0)

        for choice in FILTERS:
            s.add_radiobutton(label=choice, value=choice, variable=self.filter_var,
                              command=self._on_filter_changed)

        bar.add_cascade(label="Show", menu=s)

        t = tk.Menu(bar, tearoff=0)
        t.add_command(label=self._launch_label(), command=self.launch)

        # A TOGGLE between the usual launch and a regular one (Adam, 2026-09-29): "make it be a toggle that switches
        # between current and regular."  Remembered with the language, and the Launch item above says which it is.
        t.add_checkbutton(label="Launch in regular mode (no simulate, no debug)", variable=self.regular_var,
                          command=self._on_mode_changed)
        self.tools_menu = t

        # One click to a language: choosing one here launches in it, and it stays the choice beside the Launch button.
        languages = tk.Menu(t, tearoff=0)

        for _, _, name in LANGUAGES:
            languages.add_radiobutton(label=name, value=name, variable=self.language_var,
                                      command=self._launch_in_chosen_language)

        t.add_cascade(label="Launch TrainControl in", menu=languages)
        t.add_command(label="Show TrainControl output", command=self.show_output)
        t.add_separator()
        t.add_command(label="New issue…\tCtrl+N", command=self.free_observation)
        t.add_separator()
        t.add_command(label="Clear this session's marks", command=self.clear_marks)
        bar.add_cascade(label="Tools", menu=t)

        v = tk.Menu(bar, tearoff=0)
        v.add_command(label="Fill the right third of the screen", command=lambda: self.dock("right"))
        v.add_command(label="Fill the left third of the screen", command=lambda: self.dock("left"))
        v.add_separator()
        v.add_command(label="Reset the dividers", command=self._default_dividers)
        bar.add_cascade(label="View", menu=v)

        h = tk.Menu(bar, tearoff=0)
        h.add_command(label="How this works", command=self.about)
        bar.add_cascade(label="Help", menu=h)

        self.config(menu=bar)

    def _build_toolbar(self):
        # TWO ROWS, because one did not fit a third of the screen: its right-hand end - New issue, and the Show filter -
        # was cut off.  The filter is the Show menu now.
        bar = ttk.Frame(self, padding=(PAD, PAD, PAD, 4))
        bar.pack(fill=tk.X)

        top = ttk.Frame(bar)
        top.pack(fill=tk.X)

        self.compile_button = ttk.Button(top, text="Compile", command=self.compile)
        self.compile_button.pack(side=tk.LEFT)

        ttk.Button(top, text="\u25b6  Launch TrainControl", style="Big.TButton",
                   command=self.launch).pack(side=tk.LEFT, padx=(6, 0))

        # THE LANGUAGE IT LAUNCHES IN, beside the button that launches it (Adam, 2026-09-24: "so that I can easily
        # launch in a chosen language").
        picker = ttk.Combobox(top, textvariable=self.language_var, state="readonly", width=10,
                              values=[name for _, _, name in LANGUAGES])
        picker.pack(side=tk.LEFT, padx=(4, 0))
        picker.bind("<<ComboboxSelected>>", lambda e: self._remember_language())

        ttk.Button(top, text="New issue\u2026", style="Big.TButton",
                   command=self.free_observation).pack(side=tk.RIGHT)

        under = ttk.Frame(bar)
        under.pack(fill=tk.X, pady=(4, 0))

        ttk.Button(under, text="Compile output\u2026",
                   command=self.show_compile_output).pack(side=tk.RIGHT, padx=(4, 0))
        ttk.Button(under, text="Output\u2026", command=self.show_output).pack(side=tk.RIGHT)

        self.run_label = ttk.Label(under, text="not started", style="Sub.TLabel")
        self.run_label.pack(side=tk.LEFT, fill=tk.X, expand=True)

    def _build_left(self, parent):
        """A tab per kind of thing this app tracks, so a feature request never has to borrow the
        Tests list to be seen - it was showing up there as an MT-### row the moment it was picked
        up, indistinguishable from an actual hands-on test, which is exactly the complaint that
        led to this.
        """

        self.left_book = ttk.Notebook(parent)

        self.left_book.add(self._build_list(self.left_book), text="  Tests  ")
        self.left_book.add(self._build_issue_list(self.left_book, "feature request"),
                           text="  Feature requests  ")
        self.left_book.add(self._build_issue_list(self.left_book, "bug"), text="  Bugs  ")

        return self.left_book

    def _build_list(self, parent):
        frame = ttk.Frame(parent)

        search = ttk.Frame(frame)
        search.pack(fill=tk.X, pady=(0, 4))

        ttk.Label(search, text="Find:").pack(side=tk.LEFT)

        self.search_var = tk.StringVar()
        box = ttk.Entry(search, textvariable=self.search_var)
        box.pack(side=tk.LEFT, fill=tk.X, expand=True, padx=(4, 0))
        box.bind("<KeyRelease>", lambda e: self._refresh_list())

        legend = ttk.Frame(frame)
        legend.pack(fill=tk.X, pady=(0, 4))

        for slug, label in (
            ("needs-test", "needs test"),
            ("fixed-unvalidated", "fixed unvalidated"),
            ("fixed-validated", "fixed validated"),
            ("superseded", "superseded"),
        ):
            dot = tk.Label(legend, text="●", fg=DISPOSITION_COLORS[slug],
                          font=("Segoe UI", 10))
            dot.pack(side=tk.LEFT, padx=(0, 2))

            ttk.Label(legend, text=label, style="Sub.TLabel").pack(side=tk.LEFT, padx=(0, 10))

        columns = ("mark", "again", "tag", "date", "what")

        self.tree = ttk.Treeview(frame, columns=columns, show="headings", selectmode="browse", height=8)

        for name, title, width, anchor in (
            ("mark", "", 26, tk.CENTER),
            ("again", "", 22, tk.CENTER),
            ("tag", "ID", 68, tk.W),
            ("date", "Date", 82, tk.W),
            ("what", "What", 200, tk.W),
        ):
            self.tree.heading(name, text=title)
            self.tree.column(name, width=width, anchor=anchor,
                             stretch=(name == "what"))

        # NO DATE ON SCREEN: in a third of the screen its column was a fifth of the width the title had.  The entry's
        # date is in the heading over its steps.
        self.tree.configure(displaycolumns=("mark", "again", "tag", "what"))

        bar = ttk.Scrollbar(frame, orient=tk.VERTICAL, command=self.tree.yview)
        self.tree.configure(yscrollcommand=bar.set)

        self.tree.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        bar.pack(side=tk.LEFT, fill=tk.Y)

        for slug, color in DISPOSITION_COLORS.items():
            self.tree.tag_configure(slug, foreground=color)

        self.tree.bind("<<TreeviewSelect>>", self._on_select)

        return frame

    def _build_issue_list(self, parent, kind):
        """One tab's worth: pending items of that kind, plus every picked-up one. A row tracked
        directly shows its own State; a promoted item - one that earned MT-### tags - shows the
        state of those tests, worst open state winning, and is open or closed by that the same
        way a direct row is (Adam, 2026-09-13). Declined rows are hidden under every filter. A
        read-only pane underneath shows whichever row is
        selected. Read-only on purpose - filing and answering both already have a home (New
        issue, and the Tests tab), so this tab is for seeing what is there, not a second way to
        write to either file.
        """

        frame = ttk.Frame(parent)

        # THE LIST IS THE LAST THING TO GIVE WAY.  Tk's packer gives each widget its asked-for size in the order they
        # were packed and squeezes the last ones when the pane is short - so the buttons go first, then the list
        # (fill+expand, so it takes whatever is spare), then the detail pane, which is what shrinks.  Packed the other
        # way round - the list last - a short top pane squeezed the list to its heading and left a blank detail box: in
        # the third-of-the-screen layout Adam saw both tabs empty (2026-09-24: "why is the FR and OB list in my app
        # empty?") with 30 and 56 rows in them.  (The list's own scrollbar shares 'rows' with it, which is where an
        # older version went wrong: the tree a direct child of 'frame', competing with 'rows' as a sibling.)
        button_row = ttk.Frame(frame)
        button_row.pack(side=tk.BOTTOM, fill=tk.X, pady=(4, 0))

        request_cancel_button = ttk.Button(button_row, text="Request cancel…", state=tk.DISABLED,
                                           command=lambda: self.request_cancel(kind))
        request_cancel_button.pack(side=tk.LEFT)

        open_button = ttk.Button(button_row, text="Open in Tests tab", state=tk.DISABLED,
                                 command=lambda: self._jump_to_test(
                                     self.issue_widgets[kind].get("open_tag")))
        open_button.pack(side=tk.RIGHT)

        rows = ttk.Frame(frame)
        rows.pack(side=tk.TOP, fill=tk.BOTH, expand=True)

        detail = tk.Text(frame, wrap=tk.WORD, height=4, font=("Segoe UI", 10),
                         background="#fbfbfb", relief=tk.FLAT, padx=8, pady=6, state=tk.DISABLED)
        detail.pack(side=tk.BOTTOM, fill=tk.X, pady=(6, 0))

        columns = ("state", "ref", "date", "what")

        # Three rows asked for, not six: what it asks for is what it keeps when the pane is short, and the detail pane
        # under it needs some of that too.  A taller pane gives the list the rest.
        tree = ttk.Treeview(rows, columns=columns, show="headings", selectmode="browse", height=3)

        for name, title, width in (
            ("state", "State", 92),
            ("ref", "Ref", 92),
            ("date", "Filed", 82),
            ("what", "What", 220),
        ):
            tree.heading(name, text=title)
            tree.column(name, width=width, anchor=tk.W, stretch=(name == "what"))

        # The Filed date is in the detail underneath, for the same reason the Tests list has none.
        tree.configure(displaycolumns=("state", "ref", "what"))

        bar = ttk.Scrollbar(rows, orient=tk.VERTICAL, command=tree.yview)
        tree.configure(yscrollcommand=bar.set)

        tree.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        bar.pack(side=tk.LEFT, fill=tk.Y)

        for slug, color in ISSUE_STATE_COLORS.items():
            tree.tag_configure(slug, foreground=color)

        tree.tag_configure("pending", foreground=DISPOSITION_COLORS["needs-test"])

        self.issue_widgets[kind] = {
            "tree": tree, "detail": detail, "request_cancel_button": request_cancel_button,
            "open_button": open_button, "open_tag": None,
        }

        tree.bind("<<TreeviewSelect>>", lambda e, k=kind: self._on_issue_select(k))

        return frame

    def _refresh_issue_tabs(self):
        self.issues_doc = IssuesDoc(self.conn)

        for kind in self.issue_widgets:
            self._populate_issue_tree(kind)

    def _issue_include(self, is_open):
        """Same Show filter the Tests tab uses, applied to a bug/feature request's own
        open/closed state.  Issues have no per-session mark the way a test's Skip/Submit does -
        nothing is ever 'answered' through a read-only tab - so the two 'open' modes collapse
        into one question here (still open, or not), and 'answered this session' has nothing to
        show, honestly, rather than a made-up substitute for a state these tabs don't have.
        """

        mode = self.filter_var.get()

        if mode.startswith("open - not yet") or mode.startswith("open - everything"):
            return is_open

        if mode.startswith("answered"):
            return False

        return True     # everything, validated included

    def _populate_issue_tree(self, kind):
        widgets = self.issue_widgets[kind]
        tree = widgets["tree"]

        selected = tree.selection()

        tree.delete(*tree.get_children())

        if not self.issues_doc:
            return

        for it in self.issues_doc.pending:
            if it.kind != kind:
                continue

            # A pending item has not been picked up, let alone finished - always open.
            if not self._issue_include(True):
                continue

            self._insert_row(tree, "pending:%s" % it.ref, ("pending",),
                             ("pending", it.ref, it.filed, it.summary))

        show_everything = self.filter_var.get().startswith("everything")

        for row in self.issues_doc.picked:
            # The receipt table's Kind column is free text, same as the Inbox item it came from -
            # normalised for the same reason IssueItem.kind is, so a hand-typed 'Bug' or
            # 'feature' row is not invisible in both tabs the way an exact '==' left it.
            if normalize_kind(row.get("kind"))[0] != kind:
                continue

            ref = row.get("ref", "")
            tags = row.get("became_tags") or []

            if tags:
                # PROMOTED: its state is the state of the tests it became (Adam, 2026-09-13: "clean
                # up the disposition of FR's in the '-' state").  This showed only "-> MT-###" and hid
                # the row under both open filters, so a request whose tests were all validated and one
                # still waiting on a run read the same, and neither could be seen without asking for
                # everything.  Worst open state wins - see derived_disposition.
                state_text = derived_disposition(tags, self.doc.by_tag) or ""
                slug = disposition_slug(state_text) if state_text else None
                state_shown = "%s  (%s)" % (state_text or "tests not found", ", ".join(tags))
            else:
                # Tracked directly: State IS the disposition, same words the tests use, Claude-set
                # the same way.  A bare "-" is an empty cell, not a word.
                state_text = (row.get("state") or "").strip()

                if state_text == "-":
                    state_text = ""

                slug = disposition_slug(state_text) if state_text else None
                state_shown = state_text or "(no state recorded)"

            # CANCELLED IS OUT OF VIEW, under every filter (Adam, 2026-09-13).  It stays in the file,
            # which is the record; the list is for what is still in play or has been done.
            if state_text.lower() in HIDDEN_ISSUE_STATES:
                continue

            is_open = state_text.lower() not in CLOSED_ISSUE_STATES

            if not self._issue_include(is_open):
                continue

            row_tags = (slug,) if slug in ISSUE_STATE_COLORS else ()

            self._insert_row(tree, "picked:%s" % ref, row_tags,
                             (state_shown, ref, row.get("filed", ""), row.get("what", "")))

        if selected and selected[0] in tree.get_children():
            tree.selection_set(selected[0])

    def _on_issue_select(self, kind, _event=None):
        widgets = self.issue_widgets[kind]
        tree = widgets["tree"]
        detail = widgets["detail"]
        cancel_button = widgets["request_cancel_button"]
        open_button = widgets["open_button"]

        selection = tree.selection()

        cancel_button.config(state=tk.NORMAL if selection else tk.DISABLED)

        if not selection:
            self._fill(detail, "")
            widgets["open_tag"] = None
            open_button.config(state=tk.DISABLED)
            return

        iid = selection[0]

        if iid.startswith("picked:"):
            ref = iid[len("picked:"):]
            row = next((r for r in self.issues_doc.picked if r.get("ref") == ref), None) \
                if self.issues_doc else None

            tag = row.get("became_tag") if row else None

            if tag:
                # Every test it became, each with its own state, under the state they add up to - so
                # the one keeping a request open is named rather than left to be looked for.
                tags = row.get("became_tags") or [tag]

                each = "\n".join(
                    "  %s - %s" % (t, self.doc.by_tag[t].disposition if t in self.doc.by_tag
                                   else "not in the store")
                    for t in tags)

                text = "Picked up as %s - state: %s.\n\n%s\n\nRef: %s\nFiled: %s\n\n%s" % (
                    ", ".join(tags), derived_disposition(tags, self.doc.by_tag) or "tests not found",
                    each, ref, row.get("filed", ""), row.get("what", ""))
            else:
                state_text = (row.get("state") if row else "") or "(no state recorded)"
                text = "Tracked directly - state: %s.\n\nRef: %s\nFiled: %s\n\n%s" % (
                    state_text, ref, row.get("filed", "") if row else "",
                    row.get("what", "") if row else "")

            self._fill(detail, text)
            widgets["open_tag"] = tag
            open_button.config(state=tk.NORMAL if tag and tag in self.doc.by_tag else tk.DISABLED)
            return

        ref = iid[len("pending:"):]
        it = next((x for x in self.issues_doc.pending if x.ref == ref), None) \
            if self.issues_doc else None

        if not it:
            return

        text = ("Pending - not yet picked up.\n\n"
               "Kind: %s\nRaised from: %s\nFiled: %s\nBuild: %s\n\n%s") % (
                   it.kind, it.raised_from, it.filed_at, it.build, it.detail)

        self._fill(detail, text)
        widgets["open_tag"] = None
        open_button.config(state=tk.DISABLED)

    def request_cancel(self, kind):
        """Adam's half of cancelling something: a request, not a decision - only Claude sets
        State, same as everywhere else it appears, so this files a note for the next round
        rather than writing 'declined' itself.  Works on a pending item or an already-picked-up
        one the same way, since both are things a feature request can need cancelling from.
        """

        widgets = self.issue_widgets[kind]
        tree = widgets["tree"]

        selection = tree.selection()

        if not selection or not self.issues_doc:
            return

        iid = selection[0]

        if iid.startswith("pending:"):
            ref = iid[len("pending:"):]
            it = next((x for x in self.issues_doc.pending if x.ref == ref), None)
            summary = it.summary if it else ref
            target_kind = it.kind if it else kind      # already normalize_kind()'d
        else:
            ref = iid[len("picked:"):]
            row = next((r for r in self.issues_doc.picked if r.get("ref") == ref), None)
            summary = row.get("what", "") if row else ref
            # The receipt table's Kind column is free text too - normalised for the same reason
            # IssueItem.kind is: an exact-match 'feature request' here decides which counter
            # counter the cancellation request's own ref is taken from, and a
            # cased or shortened Kind used to fall through to OB regardless of which one it named.
            target_kind = normalize_kind(row.get("kind"))[0] if row else kind

        reason = simpledialog.askstring(
            "Request cancel",
            "Cancel %s - %s?\n\n"
            "Optional reason.  This files a request; Claude reads it next round and marks %s "
            "declined - nothing changes here until then." % (ref, summary, ref),
            parent=self)

        if reason is None:
            return

        try:
            new_ref, warning = self._issues().file(
                target_kind, "Cancel %s" % ref, reason.strip() or "(no reason given)",
                "cancellation request for %s - %s" % (ref, summary), self._build_note())

        except Exception as bad:
            # The store refused, or could not be written - busy past its wait, say.  Left unhandled,
            # this callback would just stop - no dialog, nothing printed under pythonw - and the
            # cancellation request would look like it never happened.
            messagebox.showerror("Could not file the cancellation request", str(bad), parent=self)
            return

        if warning:
            messagebox.showwarning("Filed, with a warning", warning, parent=self)

        self._refresh_issue_tabs()

        self._say("Cancellation requested for %s - filed as %s." % (ref, new_ref))

    def _jump_to_test(self, tag):
        if not tag:
            return

        if tag not in self.doc.by_tag:
            messagebox.showinfo("Not found", "%s is not in the store." % tag, parent=self)
            return

        self.left_book.select(0)

        if tag not in self.tree.get_children():
            # Whatever filter is active right now doesn't include it - the point of the button is
            # to get there, not to first explain why it's hidden.  A live search in the Find box
            # can hide it too, independently of the filter - cleared here for the same reason.
            self.filter_var.set("everything, validated included")
            self.search_var.set("")
            self._on_filter_changed()

        if tag in self.tree.get_children():
            self.tree.selection_set(tag)
            self.tree.see(tag)

    def _build_detail(self, parent):
        """The entry on screen, and the answer to it - two panes, so the steps can be given the height and the answer
        keeps what it needs.

        :return: (the entry's pane, the answer's pane)
        """

        outer = ttk.Frame(parent)

        head = ttk.Frame(outer)
        head.pack(fill=tk.X)

        self.head_label = ttk.Label(head, text="", style="Tag.TLabel", anchor=tk.W, justify=tk.LEFT)
        self.head_label.pack(fill=tk.X)

        meta = ttk.Frame(head)
        meta.pack(fill=tk.X)

        self.meta_written = ttk.Label(meta, text="", style="Sub.TLabel")
        self.meta_written.pack(side=tk.LEFT)

        # The oval echoes the same color as the row's dot in the list, so the two views never
        # disagree about what a disposition looks like - one legend, read two ways.
        bg = ttk.Style(self).lookup("TFrame", "background") or "SystemButtonFace"

        self.disp_dot = tk.Canvas(meta, width=12, height=12, highlightthickness=0, bd=0,
                                  background=bg)
        self.disp_dot.pack(side=tk.LEFT, padx=(10, 4))
        self.disp_dot_oval = self.disp_dot.create_oval(1, 1, 11, 11, fill="", outline="")

        self.meta_disposition = ttk.Label(meta, text="", style="Sub.TLabel")
        self.meta_disposition.pack(side=tk.LEFT)

        self.meta_from = ttk.Label(head, text="", style="Sub.TLabel", anchor=tk.W, justify=tk.LEFT)
        self.meta_from.pack(fill=tk.X, pady=(0, 6))

        # WRAPPED TO THE WIDTH THERE IS.  A title is often longer than a third of the screen, and cut off it lost the
        # half that says what the test is about; "from" can name several entries.
        def wrap(event):
            self.head_label.config(wraplength=max(120, event.width - 4))
            self.meta_from.config(wraplength=max(120, event.width - 4))

        head.bind("<Configure>", wrap)

        book = ttk.Notebook(outer)
        book.pack(fill=tk.BOTH, expand=True)

        self.what_text = self._read_only(book)
        book.add(self.what_text.master, text="  What to do  ")

        self.comments_text = self._read_only(book)
        book.add(self.comments_text.master, text="  Comments so far  ")

        self.book = book

        lower = ttk.Frame(parent)

        answer = ttk.Labelframe(lower, text=" Your answer ", padding=PAD)
        answer.pack(fill=tk.X, pady=(PAD, 0))

        self.result_var = tk.StringVar(value="")

        row = ttk.Frame(answer)
        row.pack(fill=tk.X)

        # One row (Adam, 2026-09-24), which fits a third of the screen with the gaps kept small.
        for value, label in RESULTS:
            ttk.Radiobutton(row, text=label.split(" - ")[0], value=value,
                            variable=self.result_var).pack(side=tk.LEFT, padx=(0, 10))

        ttk.Label(answer, text="What happened (optional, but this is the part that gets read):",
                  style="Sub.TLabel").pack(anchor=tk.W, pady=(8, 2))

        self.feedback = tk.Text(answer, height=3, wrap=tk.WORD, font=("Segoe UI", 10))
        self.feedback.pack(fill=tk.X)

        obs = ttk.Frame(answer)
        obs.pack(fill=tk.X, pady=(8, 0))

        ttk.Label(obs, text="Other things you noticed:", style="Sub.TLabel").pack(side=tk.LEFT)

        ttk.Button(obs, text="+ Feature request",
                   command=lambda: self.add_observation("feature request")).pack(side=tk.RIGHT, padx=4)
        ttk.Button(obs, text="+ Bug",
                   command=lambda: self.add_observation("bug")).pack(side=tk.RIGHT)

        # Rows are built fresh in _refresh_observations() - one line per queued observation, each
        # with its own x to remove just that one, rather than a Listbox plus a Remove button that
        # only acts on whichever row happened to be selected.
        self.obs_rows_frame = ttk.Frame(answer)
        self.obs_rows_frame.pack(fill=tk.X, pady=(4, 0))

        buttons = ttk.Frame(lower, padding=(0, PAD, 0, 0))
        buttons.pack(fill=tk.X)

        ttk.Button(buttons, text="\u25c0  Previous", command=lambda: self.step(-1)).pack(side=tk.LEFT)
        ttk.Button(buttons, text="Next  \u25b6", command=lambda: self.step(1)).pack(side=tk.LEFT, padx=4)

        ttk.Button(buttons, text="Submit and next   (Ctrl+Enter)", style="Big.TButton",
                   command=self.submit).pack(side=tk.RIGHT)

        self.skip_button = ttk.Button(buttons, text="Skip", command=self.toggle_skip)
        self.skip_button.pack(side=tk.RIGHT, padx=6)

        return outer, lower

    # -- where the window sits ---------------------------------------------------------------

    def dock(self, side):
        """Fills the right or left third of the screen, top to bottom - where this sits beside TrainControl.

        The frame Windows draws round the window is outside the size Tk sets, so it is measured - where Tk says the
        inside is, against where the frame is - rather than assumed.

        :param side: "right" or "left"
        """

        left, top, right, bottom = work_area(self)

        if self.wm_state() == "zoomed":
            self.wm_state("normal")

        self.update_idletasks()

        border = max(0, self.winfo_rootx() - self.winfo_x())
        title = max(0, self.winfo_rooty() - self.winfo_y())

        third = (right - left) // 3

        x = left if side == "left" else right - third

        self.geometry("%dx%d+%d+%d" % (third - 2 * border, (bottom - top) - title - border, x, top))

        self.after(150, self._default_dividers)

    def _default_dividers(self):
        """About a quarter of the height to the list, what it asks for to the answer, and the rest to the steps."""

        self.update_idletasks()

        total = self.panes.winfo_height()

        if total < 200:
            return

        first = int(total * 0.27)
        second = max(first + 120, total - self.answer_pane.winfo_reqheight() - 6)

        self.panes.sashpos(0, first)
        self.panes.sashpos(1, second)

    def _restore_dividers(self):
        saved = self.state_.data.get("dividers")

        if not isinstance(saved, list) or len(saved) != 2:
            self._default_dividers()
            return

        self.update_idletasks()

        self.panes.sashpos(0, saved[0])
        self.panes.sashpos(1, saved[1])

    def _read_only(self, parent):
        frame = ttk.Frame(parent)

        text = tk.Text(frame, wrap=tk.WORD, height=12, font=("Segoe UI", 10),
                       background="#fbfbfb", relief=tk.FLAT, padx=8, pady=6)

        bar = ttk.Scrollbar(frame, orient=tk.VERTICAL, command=text.yview)
        text.configure(yscrollcommand=bar.set, state=tk.DISABLED)

        text.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        bar.pack(side=tk.LEFT, fill=tk.Y)

        return text

    # -- the list ----------------------------------------------------------------------------

    def _visible(self):
        mode = self.filter_var.get()
        needle = self.search_var.get().strip().lower()

        out = []

        for e in self.doc.entries:
            mark = self.state_.mark(e.tag)

            # A mark says "I dealt with this in this session".  It goes stale the moment the entry
            # MOVES: Adam submits a verdict, the mark drops the row out of the queue, Claude then fixes
            # the thing and writes it up - and the row stays hidden, for ever, because the mark is
            # still there.  That hid MT-124 for a whole round: its disposition was open, it had been
            # acted on, and it never came back in front of him.
            #
            # Reopened is exactly "he judged it and something has changed since", so it is the right
            # thing to override a stale mark with.  Within one session nothing changes: an entry he has
            # just submitted has no newer Claude comment yet, so it is not reopened and the mark still
            # hides it, which is what the mark is for.
            #
            # Applied only to the "open" and "reopened" branches - NOT "answered this session",
            # which needs the raw mark.  _say_counts() counts self.state_.mark(e.tag) == "done"
            # directly, with no override, so applying it here too used to make the two disagree:
            # the status line said N were answered this session, and the list - using the
            # overridden mark - hid a reopened one of them anyway.
            if (mode.startswith("open") or mode.startswith("reopened")) and e.reopened:
                mark = None

            if mode.startswith("open - not yet"):
                # Skipped is a marker, not an exit - Adam still wants that row in front of him, just
                # flagged, so only a real Submit (mark == "done") drops an entry out of this view.
                if not e.is_open or mark == "done":
                    continue
            elif mode.startswith("open - everything"):
                if not e.is_open:
                    continue
            elif mode.startswith("reopened"):
                # The ones he has already judged and which have moved since - a retest queue
                # rather than a first pass.  mark is always None here already, from the override
                # above, so the only real condition left is e.reopened itself.
                if not e.reopened:
                    continue
            elif mode.startswith("answered"):
                if mark != "done":
                    continue

            if needle:
                hay = " ".join([e.tag, e.date, e.title, e.origin, e.what]).lower()

                if needle not in hay:
                    continue

            out.append(e)

        return out

    def _on_filter_changed(self):
        """The Show menu drives all three tabs, not just Tests - a feature request or bug
        that's done closing shouldn't need its own separate control to hide it.
        """

        self._refresh_list()
        self._refresh_issue_tabs()

    @staticmethod
    def _insert_row(tree, iid, tags, values):
        """tree.insert(), safe against a duplicate iid.  Tk's Treeview raises TclError on an
        iid that already exists, which used to take the whole window down mid-populate the
        moment two entries shared one MT-### or issue ref - see TestsDoc.duplicate_tags /
        IssuesDoc.duplicate_pending_refs, which is where the real fix (fixing the file) starts.
        This just keeps the app alive and both rows visible in the meantime, one of them under a
        suffixed iid.
        """

        real_iid = iid
        n = 1

        while real_iid in tree.get_children():
            n += 1
            real_iid = "%s#%d" % (iid, n)

        tree.insert("", tk.END, iid=real_iid, tags=tags, values=values)

        return real_iid

    def _refresh_list(self, select_first=False):
        self.state_.data["filter"] = self.filter_var.get()

        keep = self.current.tag if self.current else None

        self.tree.delete(*self.tree.get_children())

        for e in self._visible():
            mark = self.state_.mark(e.tag)

            glyph = {"done": "\u2713", "skipped": "\u2192"}.get(mark, "")

            if not glyph and self.state_.draft(e.tag):
                glyph = "\u2022"

            slug = disposition_slug(e.disposition)
            tags = (slug,) if slug in DISPOSITION_COLORS else ()

            # An entry he has judged before and which has moved since.  Marked rather than
            # recoloured: the colour already says the disposition, and two meanings in one colour is
            # how a legend stops being read.
            again = "↺" if e.reopened else ""

            self._insert_row(self.tree, e.tag, tags, (glyph, again, e.tag, e.date, e.title))

        rows = self.tree.get_children()

        if keep and keep in rows:
            self.tree.selection_set(keep)
        elif rows and (select_first or self.current is None):
            self.tree.selection_set(rows[0])
        elif not rows:
            self._show(None)

        self._say_counts()

    def _say_counts(self):
        total = len(self.doc.entries)
        openn = sum(1 for e in self.doc.entries if e.is_open)
        done = sum(1 for e in self.doc.entries if self.state_.mark(e.tag) == "done")

        again = sum(1 for e in self.doc.entries if e.reopened)

        # What is shown FIRST: the Show menu no longer says it on screen, and a narrow window cuts this line's end off.
        message = ("Showing %d: %s.  %d entries, %d not validated, %d of those changed since you judged them, "
                  "%d answered here this session."
                  % (len(self.tree.get_children()), self.filter_var.get(), total, openn, again, done))

        message += self._data_warnings()

        self._say(message)

    def _data_warnings(self):
        """Things the row list itself cannot say, appended to the status line so they are seen
        without going looking for them.
        """

        bits = []

        dup = list(self.doc.duplicate_tags)

        if self.issues_doc:
            dup += self.issues_doc.duplicate_pending_refs

        if dup:
            # A duplicate MT-### or issue ref is a file defect, not just a display nuisance - it
            # used to crash the window outright (tree.insert raises TclError on an existing iid,
            # and both populate calls run inside __init__).  The tree survives it now (see
            # _insert_row), but Adam still needs to know a tag means two different things in the
            # file.
            bits.append("DUPLICATE TAG(S) IN THE FILE - fix by hand: %s"
                       % ", ".join(sorted(set(dup))))

        if self.issues_doc and self.issues_doc.unrecognized_kind_refs:
            # Filed under 'bug' by normalize_kind()'s own fallback, so not invisible - but a
            # Kind that could not be recognised as bug/feature/feature request is worth a look,
            # since it may be filed under the wrong tab.
            bits.append("UNRECOGNISED Kind, filed under Bugs for now: %s"
                       % ", ".join(sorted(self.issues_doc.unrecognized_kind_refs)))

        if self.issues_doc and self.issues_doc.freeform:
            # IssuesDoc.freeform is real content - a free-hand note Adam dropped straight into
            # the Inbox with no structure - and used to be computed and then never shown
            # anywhere in the window, which is as good as dropped for anyone who only looks at
            # the app.  A preview here is not a substitute for reading it whole, but it is
            # the difference between knowing it exists and not.
            preview = self.issues_doc.freeform.strip().replace("\n", " ")

            if len(preview) > 80:
                preview = preview[:77] + "..."

            bits.append("FREEFORM note(s) in the Inbox, not shown in either tab - see "
                       "`triagedb.py notes issues.inbox-lead`: \"%s\"" % preview)

        return ("  " + "  |  ".join(bits) + ".") if bits else ""

    def _on_select(self, _event=None):
        selection = self.tree.selection()

        if not selection:
            return

        tag = selection[0]

        if self.current and self.current.tag == tag:
            return

        self._stash_draft()
        self._show(self.doc.by_tag.get(tag))

    def _show(self, entry):
        self.current = entry

        if not (self.pending_comment and entry and self.pending_comment[0] == entry.tag):
            # A pending retry only applies to the entry it was composed against - leaving here
            # abandons it rather than risking it firing later against whatever this box now
            # holds.
            self.pending_comment = None

        if entry is None:
            self.head_label.config(text="Nothing to show")
            self.meta_written.config(text="Change the filter, or clear the search box.")
            self.meta_disposition.config(text="")
            self.meta_from.config(text="")
            self.disp_dot.itemconfig(self.disp_dot_oval, fill="", outline="")
            self._fill(self.what_text, "")
            self._fill(self.comments_text, "")
            self._sync_skip_button()
            return

        self.head_label.config(text="%s  \u2014  %s" % (entry.tag, entry.title))

        self.meta_written.config(text="written %s" % entry.written)
        self.meta_disposition.config(text="disposition: %s" % entry.disposition)
        self.meta_from.config(text="from: %s" % entry.origin)

        dot_color = DISPOSITION_COLORS.get(disposition_slug(entry.disposition), "")
        self.disp_dot.itemconfig(self.disp_dot_oval, fill=dot_color, outline=dot_color)

        self._fill(self.what_text, reflow(entry.what))
        self._fill(self.comments_text, reflow(entry.comments) if entry.comments else "(nothing yet)")

        draft = self.state_.draft(entry.tag) or {}

        self.result_var.set(draft.get("result", ""))

        self.feedback.delete("1.0", tk.END)
        self.feedback.insert("1.0", draft.get("feedback", ""))

        self.observations = list(draft.get("observations", []))
        self._refresh_observations()

        self._sync_skip_button()

        self.book.select(0)

    def _sync_skip_button(self):
        """The one button toggles between Skip and Unskip, so undoing a skip is exactly as easy
        as making one - no separate control to remember, no way to be unsure which state you're in.
        """

        if not self.current:
            self.skip_button.config(text="Skip", state=tk.DISABLED)
            return

        skipped = self.state_.mark(self.current.tag) == "skipped"

        self.skip_button.config(text="Unskip" if skipped else "Skip", state=tk.NORMAL)

    def _fill(self, widget, text):
        """Shows `text` read-only, with its markdown drawn rather than spelled out (Adam, 2026-09-24: "The markdown shows
        raw - can you render bold in the steps?").

        Bold, italic, code spans, links as their text, headings in bold, list dashes as bullets, and a fenced block in
        the code face without its fences.  Tables and everything else are shown as written.  Only what is shown
        changes: nothing here is written back.
        """

        widget.config(state=tk.NORMAL)
        widget.delete("1.0", tk.END)

        fenced = False

        for number, line in enumerate(text.split("\n")):
            if number:
                widget.insert(tk.END, "\n")

            if line.strip().startswith("```"):
                fenced = not fenced
                continue

            if fenced:
                widget.insert(tk.END, line, self._style(widget, ("code",)))
                continue

            # A rule - the "---" that closes every entry in the file - is a gap, not three dashes.
            if re.match(r"^\s*(?:-{3,}|\*{3,}|_{3,})\s*$", line):
                continue

            heading = re.match(r"^\s*#{1,6}\s+(.*)$", line)

            if heading:
                self._inline(widget, heading.group(1), ("bold",))
                continue

            bullet = re.match(r"^(\s*)[-*+]\s+(.*)$", line)

            if bullet:
                widget.insert(tk.END, bullet.group(1) + "•  ")
                line = bullet.group(2)

            self._inline(widget, line, ())

        widget.config(state=tk.DISABLED)

    def _inline(self, widget, text, styles):
        """One line's inline markdown, inserted at the end of `widget` in `styles` and whatever it adds to them."""

        position = 0

        for match in _INLINE.finditer(text):
            if match.start() > position:
                self._plain(widget, text[position:match.start()], styles)

            if match.group("code"):
                widget.insert(tk.END, match.group("code")[1:-1], self._style(widget, styles + ("code",)))
            elif match.group("link"):
                # A link to an entry - `[MT-123](tests.md#mt-123)` - jumps to it.  Its address names a file that
                # has gone, and stays as written; the tag it names is in the store.
                named = _TAG_IN_HREF.search(match.group("href")) or _TAG_IN_TEXT.search(match.group("label"))

                if named:
                    widget.insert(tk.END, match.group("label"),
                                  self._style(widget, styles) + (self._jump_tag(widget, int(named.group(1))),))
                else:
                    self._inline(widget, match.group("label"), styles)
            elif match.group("bold"):
                self._inline(widget, match.group("btext"), styles + ("bold",))
            else:
                self._inline(widget, match.group("itext"), styles + ("italic",))

            position = match.end()

        if position < len(text):
            self._plain(widget, text[position:], styles)

    def _plain(self, widget, text, styles):
        """Text with no markdown in it, with every MT-### it names made a link to that entry."""

        position = 0

        for match in _TAG_IN_TEXT.finditer(text):
            if match.start() > position:
                widget.insert(tk.END, text[position:match.start()], self._style(widget, styles))

            widget.insert(tk.END, match.group(0),
                          self._style(widget, styles) + (self._jump_tag(widget, int(match.group(1))),))

            position = match.end()

        if position < len(text):
            widget.insert(tk.END, text[position:], self._style(widget, styles))

    def _jump_tag(self, widget, number):
        """The Text tag that draws a link to MT-number and jumps to it on a click, made the first time it is asked for.

        :param widget: the read-only pane
        :param number: the entry's number
        :return: the tag's name
        """

        tag = "MT-%03d" % number

        name = "jump-" + tag

        if name not in widget.tag_names():
            widget.tag_configure(name, foreground="#1a5fb4", underline=True)
            widget.tag_bind(name, "<Button-1>", lambda _e, t=tag: self._jump_to_test(t))
            widget.tag_bind(name, "<Enter>", lambda _e: widget.config(cursor="hand2"))
            widget.tag_bind(name, "<Leave>", lambda _e: widget.config(cursor=""))

        return name

    @staticmethod
    def _style(widget, styles):
        """The Text tag for a set of styles, made the first time it is asked for.  One tag per combination, because Tk
        takes a run's font from one tag rather than merging them - bold and italic together is a font of its own.
        """

        styles = sorted(set(styles))

        if not styles:
            return ()

        name = "md-" + "-".join(styles)

        if name not in widget.tag_names():
            if "code" in styles:
                widget.tag_configure(name, font=("Consolas", 9, "bold" if "bold" in styles else "normal"),
                                     background="#eceef1")
            else:
                widget.tag_configure(name, font=("Segoe UI", 10) + tuple(
                    style for style in ("bold", "italic") if style in styles))

        return (name,)

    def _refresh_observations(self):
        for child in self.obs_rows_frame.winfo_children():
            child.destroy()

        # Nothing at all when there is nothing: a "(none yet)" line was a line of height the steps could use.
        if not self.observations:
            return

        for index, ob in enumerate(self.observations):
            row = ttk.Frame(self.obs_rows_frame)
            row.pack(fill=tk.X)

            ttk.Label(row, text="[%s]  %s" % (ob["kind"], ob["summary"]), anchor=tk.W,
                     font=("Segoe UI", 9)).pack(side=tk.LEFT, fill=tk.X, expand=True)

            remove = tk.Label(row, text="✕", fg="#a33333", font=("Segoe UI", 9), cursor="hand2")
            remove.pack(side=tk.RIGHT, padx=(6, 0))
            remove.bind("<Button-1>", lambda _e, i=index: self._remove_observation_at(i))

    # -- drafts ------------------------------------------------------------------------------

    def _stash_draft(self):
        if not self.current:
            return

        feedback = self.feedback.get("1.0", tk.END).strip()
        result = self.result_var.get()

        if feedback or result or self.observations:
            self.state_.draft(self.current.tag, {
                "result": result,
                "feedback": feedback,
                "observations": self.observations,
            })
        else:
            self.state_.data["drafts"].pop(self.current.tag, None)

        self.state_.save()

    def _autosave(self):
        self._stash_draft()
        self.after(20000, self._autosave)

    # -- observations ------------------------------------------------------------------------

    def add_observation(self, kind):
        got = ObservationDialog(self, kind).result

        if got:
            self.observations.append(got)
            self._refresh_observations()
            self._stash_draft()
            self._refresh_list()

    def _remove_observation_at(self, index):
        if 0 <= index < len(self.observations):
            del self.observations[index]
            self._refresh_observations()
            self._stash_draft()

    def free_observation(self):
        """A new issue - bug or feature request - that has nothing to do with the entry on screen."""

        got = ObservationDialog(self, "bug", standalone=True).result

        if not got:
            return

        try:
            ref, warning = self._file_observation(got, None)

        except Exception as bad:
            # The store refused, or could not be written - busy past its wait, say.  Unhandled, it
            # unwinds out of this Tk callback - no confirmation dialog, and under pythonw nothing
            # printed either, so from Adam's side the button just did nothing.  That is what led to an
            # item being filed twice: he clicked again because it looked like the first click had not
            # worked.
            messagebox.showerror("Could not file the issue", str(bad), parent=self)
            return

        if warning:
            messagebox.showwarning("Filed, with a warning", warning, parent=self)

        messagebox.showinfo("Filed", "Filed as %s, in the Inbox." % ref, parent=self)

        self._say("%s filed." % ref)

    def _file_observation(self, ob, from_entry):
        """Files one bug or feature request into the Inbox.

        :param ob: the dialog's answer - kind, summary, detail
        :param from_entry: the test it was noticed on, or None
        :return: (the ref it was filed as, None or a warning)
        """

        raised = ("%s (%s)" % (from_entry.tag, from_entry.title)) if from_entry else \
                 "noticed while testing - not from a particular test"

        filed = self._issues().file(ob["kind"], ob["summary"], ob["detail"].strip() or "(no further detail)",
                                    raised, self._build_note())

        self._refresh_issue_tabs()

        return filed

    def _issues(self):
        """The Inbox, read if the window has not read it yet.

        :return: an IssuesDoc
        """

        if self.issues_doc is None:
            self.issues_doc = IssuesDoc(self.conn)

        return self.issues_doc

    def _build_note(self):
        bits = []

        if self.build:
            bits.append("commit %s" % self.build)

        if self.launched_from:
            bits.append(self.launched_from)

        return ", ".join(bits) if bits else "not recorded"

    # -- submitting --------------------------------------------------------------------------

    def submit(self):
        entry = self.current

        if not entry:
            return

        result = self.result_var.get()
        feedback = self.feedback.get("1.0", tk.END).strip()

        if not result and not feedback and not self.observations:
            messagebox.showinfo(
                "Nothing to submit",
                "Pick a result, write something, or add an observation.  Skip moves on without "
                "writing anything.", parent=self)
            return

        if not result:
            if not messagebox.askyesno(
                    "No result chosen",
                    "You have written something but not said whether it works.  Submit anyway?",
                    parent=self):
                return

        # Observations first: if the comment then cannot be written, the items are already filed and
        # the comment can be re-submitted, which is the harmless order of the two.
        receipts = []
        warnings = []

        try:
            # Each takes its number inside the store's write, so two in one submit cannot collide.
            for ob in list(self.observations):
                ref, warning = self._file_observation(ob, entry)

                receipts.append((ref, ob))

                # Filed: it leaves this submit at once, so a failure further down cannot file it twice.
                self.observations.remove(ob)

                if warning:
                    warnings.append(warning)

        except Exception as bad:
            self._refresh_observations()
            self._stash_draft()

            messagebox.showerror("Could not file an observation", "%s%s" % (bad, (
                "\n\n%d filed before it: %s.  They are no longer in this submit." % (
                    len(receipts), ", ".join(ref for ref, _ in receipts))) if receipts else ""), parent=self)
            return

        # A retry of a previously-failed submit reuses the exact comment text composed last
        # time, receipts and all, rather than recomposing one - self.observations was cleared
        # the moment those observations were safely filed, so a fresh _compose() here would
        # produce a comment that names none of them, even though they are sitting in the Inbox.
        if self.pending_comment and self.pending_comment[0] == entry.tag:
            comment = self.pending_comment[1]
        else:
            comment = self._compose(result, feedback, receipts)

        try:
            warning = self.doc.append_comment(entry.tag, comment)

            if warning:
                warnings.append(warning)

        except Exception as bad:
            # The observations, if any, are already safely in the Inbox - clearing them here
            # stops a retry from filing them a second time under new refs, which is the failure
            # this file itself records happening once already ("filed twice because the app
            # appeared not to have filed it the first time").  The comment stays in the boxes,
            # and self.pending_comment keeps its exact text so Submit tries the same thing again
            # rather than composing a new comment that would undercount what was filed.
            self.observations = []
            self._refresh_observations()
            self.pending_comment = (entry.tag, comment)
            self._stash_draft()

            messagebox.showerror(
                "Could not write the comment",
                "%s\n\nYour observations were filed and removed from this submit - retrying "
                "will not file them again.  The comment itself was not written; it is still in "
                "the boxes, and Submit will try the exact same text again." % bad, parent=self)
            return

        self.pending_comment = None

        self.state_.clear_draft(entry.tag)
        self.state_.mark(entry.tag, "done")

        # Clear what is on screen, not just the saved draft - _advance() -> _refresh_list() ->
        # _on_select() calls _stash_draft() BEFORE _show() on whatever row comes next, and if
        # these boxes still held the answer just submitted, that is exactly what got stashed
        # back as a fresh draft on THIS entry a moment after it was marked done.  Same reason the
        # 20-second autosave needed this: it stashes on a timer, not only on navigation.
        self.result_var.set("")
        self.feedback.delete("1.0", tk.END)
        self.observations = []
        self._refresh_observations()

        self._say("%s answered%s." % (
            entry.tag, " and %d observation(s) filed" % len(receipts) if receipts else ""))

        if warnings:
            messagebox.showwarning("Written, with a warning", "\n\n".join(warnings), parent=self)

        self._advance()

    def _compose(self, result, feedback, receipts):
        said = {
            "works": "Works.",
            "works with notes": "Works, with notes.",
            "does not work": "Does not work.",
            "could not run": "Could not run this.",
        }.get(result, "")

        lines = ["**Adam, %s (triage).** %s" % (datetime.date.today().isoformat(), said)]

        if feedback:
            lines.append("")
            lines.append(feedback)

        if receipts:
            lines.append("")

            listed = ", ".join(
                "%s (%s - %s)" % (ref, ob["kind"], ob["summary"]) for ref, ob in receipts)

            lines.append("Filed from this test: %s.  They are in the Inbox until they are picked up."
                         % listed)

        lines.append("")
        lines.append("*Run against %s.*" % self._build_note())

        return "\n".join(lines)

    def toggle_skip(self):
        if not self.current:
            return

        tag = self.current.tag

        if self.state_.mark(tag) == "skipped":
            # Undo only, not a submit: stay put rather than jumping to the next row, so unskipping
            # something puts you right back in front of the thing you meant to unskip.
            self.state_.clear_mark(tag)
            self._sync_skip_button()
            self._refresh_list()
            self._say("%s unskipped." % tag)
            return

        self._stash_draft()
        self.state_.mark(tag, "skipped")

        self._say("%s skipped - nothing written.  The Skip button is now Unskip if that was a "
                  "mistake." % tag)
        self._advance()

    def _advance(self):
        rows = list(self.tree.get_children())

        here = rows.index(self.current.tag) if self.current.tag in rows else -1

        self._refresh_list()

        rows = list(self.tree.get_children())

        if not rows:
            self._show(None)
            return

        if self.current and self.current.tag in rows:
            # The row just answered is still in view - a skip no longer hides its row, so "the
            # same index" would just be the row itself.  Step to the next one instead, so Skip
            # still moves forward the way it always did.
            idx = rows.index(self.current.tag)
            target = rows[idx + 1] if idx + 1 < len(rows) else rows[idx]
        else:
            # It dropped out of view (a Submit, under a filter that hides done) - whatever now
            # sits at its old position is the next thing to look at.
            target = rows[min(here, len(rows) - 1)] if here >= 0 else rows[0]

        self.tree.selection_set(target)
        self.tree.see(target)

    def step(self, by):
        rows = list(self.tree.get_children())

        if not rows or not self.current:
            return

        if self.current.tag not in rows:
            self.tree.selection_set(rows[0])
            return

        where = rows.index(self.current.tag) + by

        if 0 <= where < len(rows):
            self.tree.selection_set(rows[where])
            self.tree.see(rows[where])

    # -- TrainControl ------------------------------------------------------------------------

    def _language(self):
        """The chosen one of LANGUAGES."""

        return next((language for language in LANGUAGES if language[2] == self.language_var.get()), LANGUAGES[0])

    def _remember_language(self):
        self.state_.data["language"] = self.language_var.get()
        self.state_.save()

    def _launch_label(self):
        """The Tools menu's Launch item, saying which way it starts TrainControl."""
        return ("Launch TrainControl (regular)\tCtrl+L" if self.regular_var.get()
                else "Launch TrainControl (simulate + debug)\tCtrl+L")

    def _on_mode_changed(self):
        self.state_.data["regular"] = bool(self.regular_var.get())
        self.state_.save()
        self.tools_menu.entryconfig(0, label=self._launch_label())
        self._say("Launch will start TrainControl %s."
                  % ("in regular mode - no simulation, no debug, connected to the Central Station"
                     if self.regular_var.get() else "in simulation, with debug"))

    def _launch_in_chosen_language(self):
        self._remember_language()
        self.launch()

    def launch(self):
        if self.process and self.process.poll() is None:
            messagebox.showinfo("Already running",
                                "TrainControl was started from here and is still running.",
                                parent=self)
            return

        if port_held():
            messagebox.showwarning(
                "Something already has the port",
                "UDP %d is in use, which means a copy of TrainControl - or a test run - is already "
                "going.  Only one can have it.  Close that one first.\n\nIf you started it from "
                "NetBeans, stop it there rather than killing it." % CS2_PORT, parent=self)
            return

        command, note = launch_plan(self._language(), bool(self.regular_var.get()))

        if not command:
            messagebox.showerror("Cannot launch", note, parent=self)
            return

        if "COULD NOT CONFIRM" in note and not messagebox.askokcancel(
                "Java version not confirmed",
                "TrainControl is built for Java 8, and the java this would launch with did not "
                "report itself as 1.8 (see the status bar for which one).  This is exactly what "
                "wrong display scaling looks like - a different runtime, tuned for something "
                "else, drawing an old Swing app.\n\n"
                "Launch anyway, or fix JAVA_HOME / PATH first and try again?", parent=self):
            self._say("Launch cancelled - java version unconfirmed.")
            return

        if not os.path.isdir(RUN_DIR):
            os.makedirs(RUN_DIR)

        self.log_path = os.path.join(
            RUN_DIR, "traincontrol-%s.log" % datetime.datetime.now().strftime("%Y%m%d-%H%M%S"))

        handle = io.open(self.log_path, "wb")

        handle.write(("%s\n%s\n\n" % (note, " ".join(command))).encode("utf-8"))
        handle.flush()

        try:
            self.process = subprocess.Popen(
                command, cwd=ROOT, stdout=handle, stderr=subprocess.STDOUT)

        except Exception as bad:
            handle.close()
            messagebox.showerror("Cannot launch", str(bad), parent=self)
            return

        self.launched_from = note

        # The commit as of THIS launch: read once when the window opened, it stayed on that commit through every rebuild
        # after it, and a verdict said "Run against commit 4aa7b98d" for a build compiled from a later one (2026-10-03).
        self.build = git_build()

        self.run_label.config(text="running - %s" % note)

        self._say("TrainControl started, %s.  Output: %s"
                  % ("regular mode" if self.regular_var.get() else "simulate + debug", os.path.basename(self.log_path)))

        threading.Thread(target=self._watch, args=(handle,), daemon=True).start()

    def _watch(self, handle):
        process = self.process

        process.wait()

        try:
            handle.close()
        except Exception:
            pass

        code = process.returncode

        self.after(0, lambda: self.run_label.config(
            text="stopped (exit %s)" % code if code else "stopped"))

    def show_output(self):
        if not self.log_path or not os.path.exists(self.log_path):
            messagebox.showinfo("No output yet",
                                "Launch TrainControl from here first.", parent=self)
            return

        LogWindow(self, self.log_path)

    def compile(self):
        """Runs the same 'ant compile' NetBeans' own Build Project button runs, so Launch picks
        up a change without switching to the IDE first.  Not a Clean and Build - see compile_plan().
        """

        if self.compile_process and self.compile_process.poll() is None:
            messagebox.showinfo("Already compiling",
                                "A compile is still running.  Check Compile output… if it's "
                                "taking a while.", parent=self)
            return

        command, env, note = compile_plan()

        if not command:
            messagebox.showerror("Cannot compile", note, parent=self)
            return

        if "COULD NOT CONFIRM" in note and not messagebox.askokcancel(
                "Java version not confirmed",
                "%s\n\nCompile anyway, or fix JAVA_HOME / PATH first and try again?" % note,
                parent=self):
            self._say("Compile cancelled - java version unconfirmed.")
            return

        if not os.path.isdir(RUN_DIR):
            os.makedirs(RUN_DIR)

        self.compile_log_path = os.path.join(
            RUN_DIR, "compile-%s.log" % datetime.datetime.now().strftime("%Y%m%d-%H%M%S"))

        handle = io.open(self.compile_log_path, "wb")

        handle.write(("%s\n%s\n\n" % (note, " ".join(command))).encode("utf-8"))
        handle.flush()

        try:
            self.compile_process = subprocess.Popen(
                command, cwd=ROOT, env=env, stdout=handle, stderr=subprocess.STDOUT)

        except Exception as bad:
            handle.close()
            messagebox.showerror("Cannot compile", str(bad), parent=self)
            return

        self.compile_button.config(text="Compiling…", state=tk.DISABLED)
        self._say("Compiling - ant compile, same as NetBeans' own Build Project…")

        threading.Thread(target=self._watch_compile, args=(handle,), daemon=True).start()

    def _watch_compile(self, handle):
        process = self.compile_process

        process.wait()

        try:
            handle.close()
        except Exception:
            pass

        code = process.returncode

        self.after(0, lambda: self._compile_finished(code))

    def _compile_finished(self, code):
        self.compile_button.config(text="Compile", state=tk.NORMAL)

        if code == 0:
            self._say("Compile finished cleanly.  Launch will pick up the new build/classes.")
        else:
            self._say("Compile failed (exit %s) - see Compile output…" % code)
            messagebox.showerror(
                "Compile failed",
                "ant compile exited %s.  See Compile output… for what javac said." % code,
                parent=self)

    def show_compile_output(self):
        if not self.compile_log_path or not os.path.exists(self.compile_log_path):
            messagebox.showinfo("No output yet", "Run Compile from here first.", parent=self)
            return

        LogWindow(self, self.compile_log_path)

    # -- odds and ends -----------------------------------------------------------------------

    def reload(self):
        self._stash_draft()

        try:
            self.doc.load()
        except Exception as bad:
            messagebox.showerror("Could not read the store", str(bad), parent=self)
            return

        self.current = None
        self._refresh_list(select_first=True)
        self._refresh_issue_tabs()
        self._say("Reloaded.")

    def _watch_store(self):
        """Every two seconds: has another process written to the store?  If so, show what it holds now."""

        try:
            if self.doc.changed_elsewhere():
                self._reread()
        except Exception:
            pass

        self.after(2000, self._watch_store)

    def _reread(self):
        """Shows what the store holds now, after another process wrote to it - keeping the entry on screen and
        whatever has been typed into the answer, which is stashed as a draft and put back.
        """

        self._stash_draft()

        keep = self.current.tag if self.current else None

        self.doc.load()

        self.current = None

        self._refresh_list()

        if keep and keep in self.doc.by_tag and keep in self.tree.get_children():
            self.tree.selection_set(keep)
            self._show(self.doc.by_tag[keep])

        self._refresh_issue_tabs()

        self._say_counts()

    def clear_marks(self):
        if messagebox.askyesno(
                "Clear session marks",
                "This forgets which entries you have answered or skipped IN THIS APP.  Nothing "
                "in the store is touched.", parent=self):

            # A draft left behind on a tag already marked "done" is a stale echo of the answer
            # already submitted for it - see submit()'s own clearing.  Marks are being wiped
            # anyway, which would put a done row back in front of Adam with its old draft
            # pre-filled the moment he reopens it, ready to be re-submitted as if it were new.
            for tag, mark in list(self.state_.data["marks"].items()):
                if mark == "done":
                    self.state_.data["drafts"].pop(tag, None)

            self.state_.data["marks"] = {}
            self.state_.save()
            self._refresh_list()

    def about(self):
        messagebox.showinfo(
            "How this works",
            "Work down the list.  For each test: say whether it works, write what happened, add "
            "anything else you noticed, then Submit.\n\n"
            "Submit adds your words to that test's comments in the store, dated and stamped with the "
            "build.  Bugs go to the Inbox as OB-### items, feature requests as FR-###, "
            "cross-referenced from the test.\n\n"
            "New issue files something that has nothing to do with the entry on screen - it goes to "
            "the Inbox the same way, without a test to reference.\n\n"
            "Skip writes nothing at all - it just marks the row so you can find it again, and stays "
            "in the list rather than disappearing.  The same button becomes Unskip when you're back "
            "on a skipped row.\n\n"
            "This app never changes a Disposition - Claude sets those from what you wrote, which is "
            "the rule that makes the record mean anything.  An MT-### in an entry's text is a link: "
            "click it to go there.\n\n"
            "View > Fill the right third of the screen puts this window beside TrainControl, top to bottom.  The "
            "dividers between the list, the steps and your answer can be dragged, and are remembered.\n\n"
            "Everything is kept in triage.db, and written out as plain text in the dump folder after "
            "every write (File > Open the text dump folder).  From a terminal, run this script with "
            "an argument (stats, tests, issues, verify-ledger, --help) for the same data as JSON, no "
            "window needed.", parent=self)

    def _open_dump(self):
        import triagedb

        self._open(triagedb.DUMP_DIR)

    def _open(self, path):
        try:
            os.startfile(path)
        except Exception as bad:
            messagebox.showerror("Could not open", str(bad), parent=self)

    def _say(self, text):
        self.status.config(text=text)

    def _on_close(self):
        self._stash_draft()

        self.state_.data["geometry"] = self.geometry()

        try:
            self.state_.data["dividers"] = [self.panes.sashpos(0), self.panes.sashpos(1)]
        except tk.TclError:
            pass

        self.state_.save()

        if self.process and self.process.poll() is None:
            if not messagebox.askyesno(
                    "TrainControl is still running",
                    "TrainControl was started from here and is still running.  Leave it running "
                    "and close this window?", parent=self):
                return

        if self.compile_process and self.compile_process.poll() is None:
            if not messagebox.askyesno(
                    "A compile is still running",
                    "ant compile hasn't finished.  Close anyway and let it keep running "
                    "unattended?", parent=self):
                return

        self.destroy()


class ObservationDialog(tk.Toplevel):
    """One bug or one feature request, in as few boxes as it can be said in."""

    def __init__(self, parent, kind, standalone=False):
        tk.Toplevel.__init__(self, parent)

        self.result = None

        self.title("New issue" if standalone else "Something you noticed")
        self.transient(parent)
        self.resizable(True, False)

        frame = ttk.Frame(self, padding=PAD)
        frame.pack(fill=tk.BOTH, expand=True)

        self.kind = tk.StringVar(value=kind)

        row = ttk.Frame(frame)
        row.pack(fill=tk.X)

        ttk.Label(row, text="This is a:").pack(side=tk.LEFT)
        ttk.Radiobutton(row, text="bug", value="bug", variable=self.kind).pack(side=tk.LEFT, padx=8)
        ttk.Radiobutton(row, text="feature request", value="feature request",
                        variable=self.kind).pack(side=tk.LEFT)

        if standalone:
            ttk.Label(frame, style="Sub.TLabel",
                      text="Filed on its own, not against the test on screen.").pack(
                          anchor=tk.W, pady=(6, 0))

        ttk.Label(frame, text="One line, so it reads well in a list:").pack(anchor=tk.W, pady=(10, 2))

        self.summary = ttk.Entry(frame, width=72)
        self.summary.pack(fill=tk.X)

        ttk.Label(frame, text="Detail - what you saw, what you expected, how to get there:").pack(
            anchor=tk.W, pady=(10, 2))

        self.detail = tk.Text(frame, height=8, wrap=tk.WORD, font=("Segoe UI", 10))
        self.detail.pack(fill=tk.BOTH, expand=True)

        buttons = ttk.Frame(frame, padding=(0, PAD, 0, 0))
        buttons.pack(fill=tk.X)

        ttk.Button(buttons, text="Add", style="Big.TButton", command=self._ok).pack(side=tk.RIGHT)
        ttk.Button(buttons, text="Cancel", command=self.destroy).pack(side=tk.RIGHT, padx=6)

        self.bind("<Escape>", lambda e: self.destroy())

        self.summary.focus_set()

        self.update_idletasks()

        # Inside the screen: from a window in the right third, 120 pixels in put most of the dialog past its edge.
        x = min(parent.winfo_rootx() + 40, self.winfo_screenwidth() - self.winfo_reqwidth() - 16)

        self.geometry("+%d+%d" % (max(0, x), parent.winfo_rooty() + 120))

        self.grab_set()
        parent.wait_window(self)

    def _ok(self):
        summary = self.summary.get().strip()

        if not summary:
            messagebox.showinfo("One line, please",
                                "A summary is what makes the list readable later.", parent=self)
            return

        detail = self.detail.get("1.0", tk.END).strip()

        self.result = {
            "kind": self.kind.get(),
            "summary": summary,
            "detail": detail,
        }

        self.destroy()


class LogWindow(tk.Toplevel):
    """The tail of TrainControl's console, which in debug mode is where the answers are."""

    def __init__(self, parent, path):
        tk.Toplevel.__init__(self, parent)

        self.path = path

        self.title(os.path.basename(path))
        self.geometry("980x600")

        frame = ttk.Frame(self, padding=6)
        frame.pack(fill=tk.BOTH, expand=True)

        top = ttk.Frame(frame)
        top.pack(fill=tk.X, pady=(0, 4))

        self.follow = tk.BooleanVar(value=True)

        ttk.Checkbutton(top, text="Follow", variable=self.follow).pack(side=tk.LEFT)
        ttk.Button(top, text="Refresh", command=self.refresh).pack(side=tk.LEFT, padx=6)
        ttk.Button(top, text="Open in editor",
                   command=lambda: os.startfile(self.path)).pack(side=tk.LEFT)

        self.text = tk.Text(frame, wrap=tk.NONE, font=("Consolas", 9),
                            background="#1e1e1e", foreground="#d8d8d8", insertbackground="#d8d8d8")

        bar = ttk.Scrollbar(frame, orient=tk.VERTICAL, command=self.text.yview)
        self.text.configure(yscrollcommand=bar.set)

        self.text.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        bar.pack(side=tk.LEFT, fill=tk.Y)

        self.refresh()
        self._tick()

    def refresh(self):
        try:
            with io.open(self.path, "rb") as fh:
                raw = fh.read()
        except OSError:
            return

        lines = raw.decode("utf-8", "replace").splitlines()[-600:]

        at = self.text.yview()

        self.text.delete("1.0", tk.END)
        self.text.insert("1.0", "\n".join(lines))

        if self.follow.get():
            self.text.see(tk.END)
        else:
            self.text.yview_moveto(at[0])

    def _tick(self):
        if not self.winfo_exists():
            return

        if self.follow.get():
            self.refresh()

        self.after(1500, self._tick)


# --------------------------------------------------------------------------------------------
# Query API - the same data the window shows, as JSON, no GUI, meant to be called programmatically
# --------------------------------------------------------------------------------------------
#
# This exists so that reading the ledger stops being something a round does by eye.  "read the
# file, scan for open rows, re-derive the counts" is exactly the kind of step that quietly drifts
# from what the file actually says; a command that returns the same structured answer every time
# does not.  Every subcommand prints one JSON value to stdout and sets its exit code - nothing
# else goes to stdout, so the output is always valid to parse on its own.  The codes:
#
#   0   found / fine
#   1   an unexpected error (an exception - see the try/except in run_cli)
#   2   not found (the store not migrated yet, or a --tag that doesn't exist)
#   3   verify-ledger only: it ran fine, but the record is not clean - distinct from 1 so a
#       script can tell "verify-ledger found something to fix" apart from "verify-ledger itself
#       broke", which used to be the same exit code either way.

def entry_dict(e, full=True):
    d = {
        "tag": e.tag,
        "date": e.date,
        "title": e.title,
        "disposition": e.disposition,
        "from": e.origin,
        "written": e.written,
        "open": e.is_open,
    }

    if full:
        d["what"] = e.what
        d["comments"] = e.comments

    return d


def _store():
    """The store, for one command: migrated, or the command is refused before it reads anything."""

    import triagedb

    conn = triagedb.connect()

    triagedb.require_record(conn)

    return conn


def cli_stats(_args):
    conn = _store()
    doc = TestsDoc(conn)
    issues = IssuesDoc(conn)

    by_disposition = {}

    for e in doc.entries:
        by_disposition[e.disposition] = by_disposition.get(e.disposition, 0) + 1

    out = {
        "tests": {
            "total": len(doc.entries),
            "open": sum(1 for e in doc.entries if e.is_open),
            "by_disposition": by_disposition,
        },
        "issues": {
            "pending": len(issues.pending) if issues else 0,
            "pending_by_kind": {},
            "has_freeform_pending": bool(issues.freeform) if issues else False,
            "picked_up": len(issues.picked) if issues else 0,
        } if issues is not None else None,
    }

    if issues:
        for it in issues.pending:
            k = it.kind
            out["issues"]["pending_by_kind"][k] = out["issues"]["pending_by_kind"].get(k, 0) + 1

    return out, 0


def cli_tests(args):
    doc = TestsDoc(_store())

    if args.tag:
        e = doc.by_tag.get(args.tag.upper())

        if not e:
            return {"error": "no such entry: %s" % args.tag}, 2

        return entry_dict(e), 0

    wanted = doc.entries if args.all else [e for e in doc.entries if e.is_open]

    if getattr(args, "reopened", False):
        wanted = [e for e in wanted if e.reopened]

    return [entry_dict(e, full=args.full) for e in wanted], 0


def cli_issues(args):
    doc = IssuesDoc(_store())

    pending = doc.pending

    if args.kind:
        wanted = "feature request" if args.kind.startswith("feat") else "bug"
        pending = [it for it in pending if it.kind == wanted]

    out = {"pending": [it.as_dict() for it in pending], "freeform_pending": doc.freeform}

    if args.all:
        out["picked_up"] = doc.picked

    return out, 0


def entries_without_kind(items):
    """Inbox entries with no **Kind:** line.

    The triage app defaults a missing Kind to "bug".  That is harmless for an OB, which is a bug
    anyway, and wrong for an FR - so a feature request filed without one is silently filed as a bug
    and shows up in the wrong list, which is how Adam found it on 2026-08-24: "the state of FR-018
    seems odd, I see it in the bug list, not in the MT list."

    Six of the ten open entries were in this state.  Only the FR was visible, because for the other
    five the wrong default happened to be the right answer - which is exactly the kind of fault that
    sits there until the one case where it matters.

    :param items: [(ref, text)] - each Inbox item's text as the store holds it
    """

    return [{"ref": ref} for ref, text in items if "**Kind:**" not in text]


def undated_followups(items):
    """Inbox entries whose later additions do not say who wrote them and when.

    Adam, 2026-08-24: "once there is a follow up, we need to clearly demarcate old comments (such as
    with a date)".  `tests.md` has had this as rule 6 since the file existed; `issues.md` did not, so
    an item picked up, deferred and re-scoped over four days read as one voice speaking once.

    The shape checked is the one the rule asks for: every paragraph that OPENS in bold, after the
    first, must carry a date.  A paragraph that opens in bold is how both files mark a new voice, and
    the ones that do not are ordinary prose inside somebody's comment.

    Deliberately blind to the metadata the triage app stamps - Kind, Raised from, Filed, Build - which
    open in bold on their own line and belong to the original filing.

    :param items: [(ref, text)] - each Inbox item's text, with its comments, as the store holds it
    """

    authors = ("Claude", "Adam")

    out = []

    for ref, text in items:
        for run in re.finditer(r"\*\*([^*]{2,90}?)\*\*", text):
            label = run.group(1).strip()

            if not any(label.startswith(a) for a in authors):
                continue

            if not re.search(r"20\d\d-\d\d-\d\d", label):
                out.append({"ref": ref, "attribution": label[:70]})

    return out


def cli_verify_ledger(_args):
    """Checks the record, as this command checked tests.md before the store held it.

    The ledger was a table in tests.md, copied from the entries and liable to drift from them; it is a
    query now (`triagedb.py ledger`), and cannot.  What is left to check is the record itself: a
    disposition that is not one of rule 4's four words, an Inbox item with no Kind, an addition to an
    Inbox item that does not say who wrote it and when, and anything `triagedb.verify_record` finds -
    a field that is not what its entry's text says, an entry that does not end with its rule, or a
    comment on nothing the store holds.  Read-only.
    """

    import triagedb

    conn = _store()

    doc = TestsDoc(conn)

    items = [(it.ref, triagedb.issue_text(conn, it.ref)) for it in triagedb.inbox_items(conn)]

    # README.md rule 4: exactly four words, and no others - every entry, not just the open ones.
    invalid_disposition = [
        {"tag": e.tag, "disposition": e.disposition}
        for e in doc.entries
        if e.disposition.strip().lower() not in VALID_DISPOSITIONS
    ]

    undated = undated_followups(items)
    no_kind = entries_without_kind(items)

    _, problems = triagedb.verify_record(conn)

    out = {
        "should_be_open": sum(1 for e in doc.entries if e.is_open),
        "invalid_disposition": invalid_disposition,
        "undated_followups": undated,
        "entries_without_kind": no_kind,
        "record_problems": problems,
        "clean": not (invalid_disposition or undated or no_kind or problems),
    }

    return out, (0 if out["clean"] else 3)


CLI_COMMANDS = {
    "stats": (cli_stats, "Counts: tests by disposition, issues pending.  Does NOT check the "
                        "ledger - see verify-ledger for that."),
    "tests": (cli_tests, "List entries. Default: open only. --all for everything, a TAG "
                        "argument (positional, not --tag) for one."),
    "issues": (cli_issues, "List Inbox items not yet picked up. --kind bug|feature, --all adds picked-up."),
    "verify-ledger": (cli_verify_ledger, "Check the record: dispositions, Inbox items, fields. Read-only."),
}


def build_arg_parser():
    import argparse

    parser = argparse.ArgumentParser(
        prog="triage.py",
        description="Query the manual tests and the Inbox (triage.db) as JSON.  Run with no "
                    "arguments to open the window instead.")

    sub = parser.add_subparsers(dest="command")

    sub.add_parser("stats", help=CLI_COMMANDS["stats"][1])

    p_tests = sub.add_parser("tests", help=CLI_COMMANDS["tests"][1])
    p_tests.add_argument("tag", nargs="?", default=None,
                         help="Look up one entry by tag (e.g. MT-089) instead of listing.")
    p_tests.add_argument("--all", action="store_true", help="Include fixed validated entries too.")
    p_tests.add_argument("--open", dest="all", action="store_false",
                         help="Only entries not fixed validated (default).")
    p_tests.add_argument("--full", dest="full", action="store_true", default=True,
                         help="Include What/Comments text (default).")
    p_tests.add_argument("--brief", dest="full", action="store_false",
                         help="Tag/date/title/disposition only, no body text.")
    p_tests.add_argument("--reopened", action="store_true",
                         help="Only entries Adam has judged before that have changed since.")

    p_test = sub.add_parser("test", help="Look up one entry by tag, in full.")
    p_test.add_argument("tag")

    p_issues = sub.add_parser("issues", help=CLI_COMMANDS["issues"][1])
    p_issues.add_argument("--kind", choices=["bug", "feature"], default=None)
    p_issues.add_argument("--all", action="store_true", help="Also include the picked-up receipt table.")

    sub.add_parser("verify-ledger", help=CLI_COMMANDS["verify-ledger"][1])

    return parser


def run_cli(argv):
    parser = build_arg_parser()
    args = parser.parse_args(argv)

    if not args.command:
        return None       # no subcommand - caller opens the GUI instead

    import triagedb

    try:
        _store().close()
    except triagedb.NotMigrated as bad:
        print(json.dumps({"error": str(bad)}), file=sys.stderr)
        return 2

    try:
        if args.command == "test":
            # 'test TAG' is 'tests TAG' under a shorter name - reuse the same handler.
            args.all = False
            args.full = True
            out, code = cli_tests(args)
        else:
            handler = CLI_COMMANDS[args.command][0]
            out, code = handler(args)

    except Exception as bad:
        print(json.dumps({"error": str(bad)}), file=sys.stderr)
        return 1

    # Written as BYTES, in UTF-8, rather than printed.
    #
    # On Windows a redirected or piped stdout defaults to the ANSI codepage, cp1252 here, and
    # json.dumps with ensure_ascii=False hands it whatever the file holds.  One U+2212 MINUS SIGN in
    # one entry was enough to take `tests --open` down with a UnicodeEncodeError - and that is the
    # command the SOP tells a round to use INSTEAD of reading the ledger by eye, so the failure mode
    # was a round falling back to scanning the table by hand, which is what the command exists to
    # avoid.  `stats` and `issues` were fine only because nothing in them happened to be non-ASCII.
    #
    # ensure_ascii stays False: escaping the output would keep it parseable but make the human-read
    # half of it - which is most of what these commands are for - unreadable.
    text = json.dumps(out, indent=2, ensure_ascii=False, sort_keys=False) + "\n"

    stream = getattr(sys.stdout, "buffer", None)

    if stream is None:
        # A stdout with no byte layer under it - a GUI console, or a test capturing it.  Nothing to
        # be done but hand it the string and let its own encoding decide.
        sys.stdout.write(text)
    else:
        stream.write(text.encode("utf-8"))
        stream.flush()

    return code


def main():
    if len(sys.argv) > 1:
        code = run_cli(sys.argv[1:])

        if code is not None:
            return code

    import triagedb

    try:
        _store().close()
    except triagedb.NotMigrated as bad:
        print(str(bad))
        return 1

    Triage().mainloop()

    return 0


if __name__ == "__main__":
    sys.exit(main())
