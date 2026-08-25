#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
TrainControl manual-test triage - a companion window for tests.md.

Run it beside TrainControl, work down the ledger, and answer one test at a time: what happened,
anything else you noticed, submit or skip.  Everything you type lands in the markdown files that
are already the source of truth, in the format README.md describes, appended and never overwritten.

    py -3 docs\\manual-tests\\triage.py

Why this exists: the ledger is ninety entries long and the instruction for the one you are running
is four hundred lines away from the row that told you to run it.  Alt-tabbing between a long
markdown file and a live railway is where results get lost.

What it writes, and where:

  A test result             appended under that entry's "#### Comments" in tests.md, dated, signed,
                            and stamped with the build it was run against.
  A bug or feature request  an OB-### (bug) or FR-### (feature request) item in issues.md - one
                            inbox for both, cross-referenced from the test's comments if it came
                            from one.

It also has a query API: run it with an argument (stats, tests, test TAG, issues, verify-ledger) and
it prints JSON to stdout and exits, no window involved.  See --help.

What it deliberately does NOT write: the **Disposition** line, and the ledger.  Rule 4 in README.md
says dispositions are Claude's to set and only Claude's, and that rule is the reason the file is
worth anything - it is the difference between "Adam says it works" and "somebody marked it done".
So this app records what you said and leaves the bookkeeping where it belongs.  Your rows keep
their old disposition until the next round reads them.

Safety.  Every write takes a timestamped copy of the file first, into .triage-backups/, and the
write itself is atomic (temp file, then replace).  If tests.md changes on disk underneath the app -
because Claude is editing it in another window - the app notices before writing and reloads instead
of clobbering.  Nothing is ever deleted or reordered.

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

TESTS_MD = os.path.join(HERE, "tests.md")
ISSUES_MD = os.path.join(HERE, "issues.md")
README_MD = os.path.join(HERE, "README.md")

BACKUP_DIR = os.path.join(HERE, ".triage-backups")
RUN_DIR = os.path.join(HERE, ".triage-runs")
STATE_FILE = os.path.join(HERE, ".triage-state.json")

BACKUPS_KEPT = 40

# The Central Station's port.  A second copy of TrainControl cannot have it, which is what MT-063
# is about; checking here means the launch button says so before the app does.
CS2_PORT = 15730

# Known good JDK on this machine, used only if nothing else answers.
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

# issues.md's State column has one more word than tests.md's disposition ever needs: a REQUEST can
# be declined, where a TEST - which by definition is already being worked - cannot.  Kept separate
# from DISPOSITION_COLORS on purpose, so tests.md's three-word rule stays exactly three words.
ISSUE_STATE_COLORS = dict(DISPOSITION_COLORS)
ISSUE_STATE_COLORS["declined"] = "#8a3a3a"         # closed, deliberately not built - not grey (=validated)

# README.md rule 4's four documented words, and no others - derived from DISPOSITION_COLORS
# rather than re-typed, so the two cannot say something different from each other.  Nothing
# checked an entry's Disposition against this before verify-ledger's own duplicate/href/date
# checks were added; this is that missing check.
VALID_DISPOSITIONS = set(slug.replace("-", " ") for slug in DISPOSITION_COLORS)


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
# Reading and writing the markdown, carefully
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

    The backup is not belt-and-braces.  These files hold months of Adam's answers and nothing else
    holds them; a half-written tests.md would cost more than every feature in this app is worth.
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
            base = name.split(".")[0]
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
    """One MT-### test, as it stands in the file right now."""

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

    def with_comment(self, comment):
        """This entry's block with a comment appended at the bottom of its Comments section.

        Append only, and above the rule that closes the entry - which is the last '---' line in the
        block, because the block runs anchor to anchor.
        """

        text = comment.strip() + "\n\n"

        cut = self.block.rfind("\n---")

        if cut < 0:
            return self.block.rstrip() + "\n\n" + text

        prefix = self.block[:cut + 1]

        if not prefix.endswith("\n\n"):
            # The rule that closes an entry does not always have a blank line before it - some
            # entries were hand-written without one.  Splicing straight in at that single '\n'
            # then glues the new comment onto the end of whatever paragraph precedes it, rather
            # than starting its own - a verdict that reads as part of the text above it.
            prefix += "\n"

        return prefix + text + self.block[cut + 1:]


class TestsDoc(object):
    """tests.md, parsed into a head and a list of entries."""

    def __init__(self, path):
        self.path = path
        self.load()

    def load(self):
        self.text, self.crlf = read_text(self.path)
        self.stat = self._stat()

        marks = [(m.start(), m.group(1)) for m in ANCHOR_RE.finditer(self.text)]

        self.head = self.text[:marks[0][0]] if marks else self.text
        self.entries = []

        for i, (pos, anchor) in enumerate(marks):
            end = marks[i + 1][0] if i + 1 < len(marks) else len(self.text)
            self.entries.append(Entry(anchor, self.text[pos:end]))

        # A duplicate MT-### is a real defect in the file, not something to paper over - two
        # entries with the same iid used to crash the window at startup the moment both
        # populate calls ran (tree.insert raises TclError on an existing iid).  Surfaced here so
        # the caller can warn instead, and the second entry keeps its place in self.entries even
        # though by_tag can only ever point at one of them.
        seen = set()
        self.duplicate_tags = []

        for e in self.entries:
            if e.tag in seen:
                self.duplicate_tags.append(e.tag)

            seen.add(e.tag)

        self.by_tag = dict((e.tag, e) for e in self.entries)

    def _stat(self):
        st = os.stat(self.path)
        return (st.st_mtime, st.st_size)

    def changed_on_disk(self):
        try:
            return self._stat() != self.stat
        except OSError:
            return True

    def append_comment(self, tag, comment):
        """Rewrite the file with one comment added to one entry.  Raises if the file moved."""

        if self.changed_on_disk():
            raise IOError(
                "tests.md changed on disk since it was loaded.  Reload (Ctrl+R) and try again - "
                "your draft is kept."
            )

        entry = self.by_tag[tag]

        pieces = []

        for e in self.entries:
            pieces.append(e.with_comment(comment) if e is entry else e.block)

        write_text(self.path, self.head + "".join(pieces), self.crlf)

        self.load()


# A bug is OB-###; a feature request is FR-###.  Separate counters, because they used to share
# one - which was fine for telling entries apart, but meant a glance at the ref alone couldn't
# tell a reader which lifecycle an item was on (bugs promoted to a test, most feature requests
# tracked directly with their own State - see the retirement of MT-094).  OB keeps counting from
# where it already was; nothing already filed is renumbered.
ISSUE_PREFIXES = {"bug": "OB", "feature request": "FR"}


def normalize_kind(raw):
    """issues.md's Kind field, normalised - lowercase, and 'feature' folded into 'feature
    request' the way this file uses it everywhere else.

    Returns (kind, recognized).  A hand-written Kind reads fine to a person as 'Bug' or
    'feature', but compared with '==' against 'bug'/'feature request' - which is how the issue
    tabs, cli_issues --kind, and next_ref_number()/format_ref() all read it - either one is
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


def next_ref_number(kind):
    """The next free number for this kind, across the issue inbox and tests.md - a plain int, so
    a caller filing several of the same kind in one go can increment it locally without re-reading
    the files after every one.
    """

    prefix = ISSUE_PREFIXES.get(kind, "OB")

    highest = 0

    for path in (ISSUES_MD, TESTS_MD):
        if not os.path.exists(path):
            continue

        text, _ = read_text(path)

        for found in re.findall(r'\b%s-(\d+)\b' % prefix, text):
            highest = max(highest, int(found))

    return highest + 1


def format_ref(kind, number):
    return "%s-%03d" % (ISSUE_PREFIXES.get(kind, "OB"), number)


INBOX_EMPTY = "*(empty)*"

# What inbox_span() treats as the end of the Inbox section - a '---' rule, or (its fallback) a
# '## ' heading.  A detail body containing either, typed by hand, reads as that same boundary to
# the parser: 'text.find("\n---")' can match a '---' inside the new item's OWN prose, and the
# '^## ' fallback can match a heading typed into a detail body - either way splicing the new item
# into the middle of an existing one instead of appending it.  Checked before a detail body can
# reach append_to_inbox() at all - see ObservationDialog._ok().
INBOX_BOUNDARY_LOOKALIKE_RE = re.compile(r'^(---|## )', re.M)


def inbox_span(text):
    """Where a file's '## Inbox' section runs: from just after the heading line to the '\\n---'
    rule that closes it, or - when there is no such rule, the normal case in issues.md - to the
    next '^## ' heading.  Never to end of file: that put every new item after the sections that
    follow Inbox, outside the section it was meant to go in and below "Where the older backlog
    is", which is where Adam went looking for one and did not find it.

    The one function both append_to_inbox() and IssuesDoc._inbox_span() call, on purpose - they
    used to each compute this themselves, and only one of the two got the '^## ' fallback when it
    was added, so a hand-added item and the parser disagreed about where the section ended.
    Returns None if there is no '## Inbox' heading at all.
    """

    start = text.find("\n## Inbox")

    if start < 0:
        return None

    body_from = text.index("\n", start + 1)

    end = text.find("\n---", body_from)

    if end < 0:
        heading = re.search(r'^## ', text[body_from:], re.M)

        end = body_from + heading.start() if heading else len(text)

    return body_from, end


def append_to_inbox(path, block):
    """Put an item at the bottom of a file's '## Inbox' section, above the rule that closes it."""

    text, crlf = read_text(path)

    span = inbox_span(text)

    if not span:
        raise IOError("%s has no '## Inbox' section" % os.path.basename(path))

    body_from, end = span

    section = text[body_from:end]

    if INBOX_EMPTY in section:
        section = section.replace(INBOX_EMPTY, "").rstrip() + "\n"

    section = section.rstrip("\n") + "\n\n" + block.strip() + "\n"

    tail = text[end:]

    if tail and not tail.startswith("\n"):
        # end landed exactly on the next '## ' heading (no closing rule) - without this the new
        # item is glued straight onto that heading with no blank line between them.  The '\n---'
        # case needs nothing extra: tail already starts with its own leading '\n'.
        section += "\n"

    write_text(path, text[:body_from] + section + tail, crlf)


# --------------------------------------------------------------------------------------------
# issues.md, parsed - the pending Inbox and the historical receipt table, both machine-readable
# --------------------------------------------------------------------------------------------

ISSUE_ITEM_RE = re.compile(
    r'^### ((?:OB|FR)-\d+) - (\d{4}-\d{2}-\d{2}) - (.*)$', re.M)

# What a '**Field:**' line looks like, generically - bold text ending in a colon before the
# closing '**'.  Used to find where the block of fields ends and the free-text body begins,
# without naming any one field: a bold body phrase ("**Not to be fixed on its own.**") ends in a
# period, not a colon, so it never matches this.
FIELD_LINE_RE = re.compile(r'^\*\*[^*\n]+:\*\*')


class IssueItem(object):
    """One structured OB-### (bug) or FR-### (feature request) entry sitting in the Inbox, not
    yet picked up.
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
    """issues.md: the pending Inbox (structured items, plus whatever does not parse) and the
    receipt table of items already picked up into tests.md.
    """

    def __init__(self, path):
        self.path = path
        self.load()

    def load(self):
        self.text, self.crlf = read_text(self.path)

        self.pending, self.freeform = self._parse_inbox()
        self.picked = self._parse_picked()

        # Same duplicate-iid hazard TestsDoc.load guards against, on the pending items that feed
        # the Feature requests / Bugs tabs (tree.insert(iid="pending:%s" % ref, ...)).
        seen = set()
        self.duplicate_pending_refs = []

        for it in self.pending:
            if it.ref in seen:
                self.duplicate_pending_refs.append(it.ref)

            seen.add(it.ref)

        # normalize_kind() already keeps these visible (filed under 'bug', its own fallback)
        # rather than invisible in both tabs - this is what lets the caller warn about them too,
        # since a Kind that could not be recognised is worth a person's eyes even though it did
        # not get dropped.
        self.unrecognized_kind_refs = [it.ref for it in self.pending if not it.kind_recognized]

    def _inbox_span(self):
        return inbox_span(self.text)

    def _parse_inbox(self):
        span = self._inbox_span()

        if not span:
            return [], ""

        section = self.text[span[0]:span[1]]

        marks = list(ISSUE_ITEM_RE.finditer(section))

        # Defence in depth alongside inbox_span()'s own boundary: a '## ' heading landing inside
        # what would otherwise be the last item's block - typed into a detail body by hand, or a
        # boundary bug like the one this file just had - must not let that item swallow
        # everything after it.  This is exactly how the last real Inbox item once absorbed the
        # receipt table and every section below it.
        headings = [h.start() for h in re.finditer(r'^## ', section, re.M)]

        items = []

        for i, m in enumerate(marks):
            block_end = marks[i + 1].start() if i + 1 < len(marks) else len(section)

            for h in headings:
                if m.start() < h < block_end:
                    block_end = h
                    break

            block = section[m.start():block_end]

            items.append(IssueItem(m.group(1), m.group(2), m.group(3), block))

        # Whatever is left once every structured item is cut out - free-hand notes Adam dropped
        # straight into the Inbox, which follow no format on purpose (see README.md).  Never
        # discarded, only set aside, so an API caller sees it exists without having to parse it.
        #
        # Reuses the SAME boundaries the first loop computed, rather than re-searching for a
        # literal "### OB-" - that string stopped being the only way a block starts the moment
        # FR-### existed, and re-deriving boundaries from scratch is exactly how the two would
        # drift.  Removed in reverse so each cut's positions, taken from the untouched 'section',
        # stay valid regardless of earlier cuts (all of which are further to the right).
        leftover = section

        for i in range(len(marks) - 1, -1, -1):
            start = marks[i].start()
            end = marks[i + 1].start() if i + 1 < len(marks) else len(section)

            leftover = leftover[:start] + leftover[end:]

        leftover = leftover.replace(INBOX_EMPTY, "").strip()

        return items, leftover

    def _parse_picked(self):
        """The receipt table, as dicts keyed by its own header - Filed/Ref/Kind/What/Became -
        rather than fixed positions, so a column added later doesn't silently misalign every
        reader of this method. 'became_tag' pulls the bare MT-### out of Became's markdown link,
        for anything that wants to jump straight to that entry in tests.md.
        """

        marker = "## What has been picked up"
        at = self.text.find(marker)

        if at < 0:
            return []

        header = None
        rows = []

        for line in self.text[at:].splitlines():
            line = line.strip()

            if not line.startswith("|") or not line.endswith("|"):
                continue

            cells = [c.strip() for c in line.strip("|").split("|")]

            if not cells:
                continue

            if set("".join(cells)) <= set("-: "):
                continue

            if header is None:
                header = [c.lower() for c in cells]
                continue

            row = dict(zip(header, cells))

            became = row.get("became", "")
            m = re.search(r'\[(MT-\d+)\]', became)
            row["became_tag"] = m.group(1) if m else None

            rows.append(row)

        return rows


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


def find_java():
    """The java to launch TrainControl with, verified rather than assumed.

    JAVA_HOME is not safe to trust blindly on a real dev machine.  On this one it points at
    Android Studio's bundled JetBrains Runtime - JDK 21, with its own IDE-tuned HiDPI scaling -
    because Android Studio sets it machine-wide for its own use.  Nothing about that is specific
    to TrainControl, so launching an old Java 8 Swing app under it is exactly how a launch works
    but "the display scaling is completely off": right app, wrong runtime, one nobody built or
    tested it against.

    So every candidate is checked with its own `-version` rather than trusted for existing, in
    the order most likely to actually be the project's JDK 8: the known-good install this
    project's own test harness already uses, then PATH, then JAVA_HOME last - and only ever
    returned if it reports 1.8.  If nothing on the machine verifies as 1.8, the first candidate
    that exists is still returned so a launch remains possible, but the caller is told the
    version was never confirmed, since guessing silently is exactly the bug being fixed here.
    """

    candidates = []

    if os.path.exists(FALLBACK_JAVA):
        candidates.append(FALLBACK_JAVA)

    found = shutil.which("java")

    if found and found not in candidates:
        candidates.append(found)

    home = os.environ.get("JAVA_HOME")

    if home:
        candidate = os.path.join(home, "bin", "java.exe")

        if os.path.exists(candidate) and candidate not in candidates:
            candidates.append(candidate)

    for c in candidates:
        if TARGET_JAVA_VERSION in _java_version_report(c):
            return c, True

    return (candidates[0], False) if candidates else (None, False)


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


def launch_plan():
    """What would be run, and how fresh it is.

    Prefers build/classes, which NetBeans refreshes on every Run, over dist/TrainControl.jar, which
    only moves on a Clean and Build.  Which one is in use is reported rather than assumed: a result
    recorded against a stale jar is the exact failure the disposition rule exists to stop.
    """

    java, verified = find_java()

    if not java:
        return None, "No java found.  Set JAVA_HOME, or put java on the PATH."

    java_note = java if verified else (
        "%s - COULD NOT CONFIRM THIS IS JAVA 8, the version TrainControl is built for; if the "
        "display looks wrong after launch, this is the first thing to check" % java)

    classes = os.path.join(ROOT, "build", "classes")
    jar = os.path.join(ROOT, "dist", "TrainControl.jar")

    args = ["0", "1", "1"]          # IP, debug, simulate - the Simulate run configuration

    if os.path.isdir(classes):
        stamp = newest_mtime(classes)

        if stamp:
            jars = project_jars()
            classpath = os.pathsep.join([classes] + jars)

            return (
                [java, "-cp", classpath, "TrainControl"] + args,
                "build\\classes, compiled %s - java: %s"
                % (time.strftime("%d %b %H:%M", time.localtime(stamp)), java_note),
            )

    if os.path.exists(jar):
        stamp = os.path.getmtime(jar)

        return (
            [java, "-jar", jar] + args,
            "dist\\TrainControl.jar, built %s (build\\classes is missing - this may be old) - "
            "java: %s" % (time.strftime("%d %b %H:%M", time.localtime(stamp)), java_note),
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
    project's compiler.
    """

    ant = find_ant()

    if not ant:
        return None, None, "No NetBeans ant found (looked for %s) and none on PATH." % ANT_GLOB

    java, verified = find_java()

    if not java:
        return None, None, "No java found to run Ant with.  Set JAVA_HOME, or put java on PATH."

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


class Triage(tk.Tk):

    def __init__(self):
        tk.Tk.__init__(self)

        self.title("TrainControl - manual test triage")

        self.state_ = State()
        self.doc = TestsDoc(TESTS_MD)
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
        # cleared the moment they are safely in issues.md.  See submit().
        self.pending_comment = None

        self.issues_doc = None
        self.issue_widgets = {}      # kind -> {tree, detail, request_cancel_button, open_button, open_tag}

        self._build_ui()
        self._refresh_list(select_first=True)
        self._refresh_issue_tabs()

        geometry = self.state_.data.get("geometry")

        self.geometry(geometry if geometry else "1220x820")

        self.protocol("WM_DELETE_WINDOW", self._on_close)

        self.bind("<Control-r>", lambda e: self.reload())
        self.bind("<Control-Return>", lambda e: self.submit())
        self.bind("<Control-l>", lambda e: self.launch())
        self.bind("<Control-n>", lambda e: self.free_observation())

        self.after(20000, self._autosave)

    # -- construction ------------------------------------------------------------------------

    def _build_ui(self):
        style = ttk.Style(self)

        try:
            style.theme_use("vista")
        except tk.TclError:
            pass

        style.configure("Tag.TLabel", font=("Segoe UI", 14, "bold"))
        style.configure("Sub.TLabel", foreground="#555555")
        style.configure("Big.TButton", font=("Segoe UI", 10, "bold"))

        self._build_menu()
        self._build_toolbar()

        panes = ttk.Panedwindow(self, orient=tk.HORIZONTAL)
        panes.pack(fill=tk.BOTH, expand=True, padx=PAD, pady=(0, PAD))

        panes.add(self._build_left(panes), weight=0)
        panes.add(self._build_detail(panes), weight=1)

        self.status = ttk.Label(self, anchor=tk.W, relief=tk.SUNKEN, padding=(6, 3))
        self.status.pack(fill=tk.X, side=tk.BOTTOM)

        self._say("Ready.  %d entries, %d open." % (
            len(self.doc.entries), sum(1 for e in self.doc.entries if e.is_open)))

    def _build_menu(self):
        bar = tk.Menu(self)

        f = tk.Menu(bar, tearoff=0)
        f.add_command(label="Reload from disk\tCtrl+R", command=self.reload)
        f.add_separator()
        f.add_command(label="Open tests.md", command=lambda: self._open(TESTS_MD))
        f.add_command(label="Open issues.md", command=lambda: self._open(ISSUES_MD))
        f.add_command(label="Open the backups folder", command=lambda: self._open(BACKUP_DIR))
        f.add_separator()
        f.add_command(label="Quit", command=self._on_close)
        bar.add_cascade(label="File", menu=f)

        t = tk.Menu(bar, tearoff=0)
        t.add_command(label="Launch TrainControl (simulate + debug)\tCtrl+L", command=self.launch)
        t.add_command(label="Show TrainControl output", command=self.show_output)
        t.add_separator()
        t.add_command(label="New issue…\tCtrl+N", command=self.free_observation)
        t.add_separator()
        t.add_command(label="Clear this session's marks", command=self.clear_marks)
        bar.add_cascade(label="Tools", menu=t)

        h = tk.Menu(bar, tearoff=0)
        h.add_command(label="How this works", command=self.about)
        bar.add_cascade(label="Help", menu=h)

        self.config(menu=bar)

    def _build_toolbar(self):
        bar = ttk.Frame(self, padding=(PAD, PAD, PAD, 4))
        bar.pack(fill=tk.X)

        self.compile_button = ttk.Button(bar, text="Compile", command=self.compile)
        self.compile_button.pack(side=tk.LEFT)

        ttk.Button(bar, text="\u25b6  Launch TrainControl", style="Big.TButton",
                   command=self.launch).pack(side=tk.LEFT, padx=(6, 0))

        self.run_label = ttk.Label(bar, text="not started", style="Sub.TLabel")
        self.run_label.pack(side=tk.LEFT, padx=(8, 0))

        ttk.Button(bar, text="Output\u2026", command=self.show_output).pack(side=tk.LEFT, padx=(8, 0))
        ttk.Button(bar, text="Compile output\u2026",
                   command=self.show_compile_output).pack(side=tk.LEFT, padx=(4, 0))

        ttk.Button(bar, text="New issue\u2026", style="Big.TButton",
                   command=self.free_observation).pack(side=tk.RIGHT)

        ttk.Label(bar, text="Show:").pack(side=tk.RIGHT, padx=(12, 4))

        self.filter_var = tk.StringVar(value=self.state_.data.get("filter", "open"))

        picker = ttk.Combobox(bar, textvariable=self.filter_var, width=26, state="readonly",
                              values=["open - not yet answered here",
                                      "open - everything not validated",
                                      "reopened - changed since your verdict",
                                      "answered this session",
                                      "everything, validated included"])

        picker.pack(side=tk.RIGHT)
        picker.bind("<<ComboboxSelected>>", lambda e: self._on_filter_changed())

        if self.filter_var.get() not in picker["values"]:
            self.filter_var.set("open - not yet answered here")

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
        frame = ttk.Frame(parent, width=380)

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

        self.tree = ttk.Treeview(frame, columns=columns, show="headings", selectmode="browse")

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

        bar = ttk.Scrollbar(frame, orient=tk.VERTICAL, command=self.tree.yview)
        self.tree.configure(yscrollcommand=bar.set)

        self.tree.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        bar.pack(side=tk.LEFT, fill=tk.Y)

        for slug, color in DISPOSITION_COLORS.items():
            self.tree.tag_configure(slug, foreground=color)

        self.tree.bind("<<TreeviewSelect>>", self._on_select)

        return frame

    def _build_issue_list(self, parent, kind):
        """One tab's worth: pending items of that kind, plus the ones tracked directly by their
        own State once picked up. A promoted item - one that earned an MT-### tag - hides under
        the 'open' filters, since its real home is the Tests tab while it's active work, and
        reappears under 'everything' so it can still be traced from filing to close from its own
        tab rather than only from tests.md. A read-only pane underneath shows whichever row is
        selected. Read-only on purpose - filing and answering both already have a home (New
        issue, and the Tests tab), so this tab is for seeing what is there, not a second way to
        write to either file.
        """

        frame = ttk.Frame(parent)

        # Anchored widgets are packed FIRST, from the bottom, so they claim only their natural
        # size; the tree is packed LAST with fill+expand so it gets everything left over.  Doing
        # it the other way round - tree first - was the previous bug: 'tree' had ended up a direct
        # child of 'frame' rather than of 'rows' (its scrollbar's actual parent), so it was
        # competing with 'rows' as an unrelated sibling instead of living inside it, and lost.
        button_row = ttk.Frame(frame)
        button_row.pack(side=tk.BOTTOM, fill=tk.X, pady=(4, 0))

        request_cancel_button = ttk.Button(button_row, text="Request cancel…", state=tk.DISABLED,
                                           command=lambda: self.request_cancel(kind))
        request_cancel_button.pack(side=tk.LEFT)

        open_button = ttk.Button(button_row, text="Open in Tests tab", state=tk.DISABLED,
                                 command=lambda: self._jump_to_test(
                                     self.issue_widgets[kind].get("open_tag")))
        open_button.pack(side=tk.RIGHT)

        detail = tk.Text(frame, wrap=tk.WORD, height=9, font=("Segoe UI", 10),
                         background="#fbfbfb", relief=tk.FLAT, padx=8, pady=6, state=tk.DISABLED)
        detail.pack(side=tk.BOTTOM, fill=tk.X, pady=(6, 0))

        rows = ttk.Frame(frame)
        rows.pack(side=tk.TOP, fill=tk.BOTH, expand=True)

        columns = ("state", "ref", "date", "what")

        tree = ttk.Treeview(rows, columns=columns, show="headings", selectmode="browse")

        for name, title, width in (
            ("state", "State", 92),
            ("ref", "Ref", 92),
            ("date", "Filed", 82),
            ("what", "What", 220),
        ):
            tree.heading(name, text=title)
            tree.column(name, width=width, anchor=tk.W, stretch=(name == "what"))

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
        self.issues_doc = IssuesDoc(ISSUES_MD) if os.path.exists(ISSUES_MD) else None

        for kind in self.issue_widgets:
            self._populate_issue_tree(kind)

    def _issue_include(self, is_open):
        """Same 'Show:' filter the Tests tab uses, applied to a bug/feature request's own
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
            tag = row.get("became_tag")

            if tag:
                # Promoted to a test - while it's still being worked, its home is the Tests tab,
                # not here, which is what the "open" filters enforce by hiding it outright
                # regardless of its own disposition.  "everything" is the exception: a bug or
                # feature request should be traceable from filing to close from its OWN tab, not
                # only from tests.md, so it comes back once the broad view is asked for.
                if not show_everything:
                    continue

                entry = self.doc.by_tag.get(tag)
                slug = disposition_slug(entry.disposition) if entry else None
                state_shown = "-> %s" % tag
            else:
                # Tracked directly: State IS the disposition, same three words tests.md uses,
                # Claude-set the same way.  Closed the same way fixed validated closes a test:
                # declined is the request-track's own terminal state, so it hides under the same
                # filter that hides fixed validated.
                state_text = (row.get("state") or "").strip()
                slug = disposition_slug(state_text) if state_text else None
                state_shown = state_text or "(no state recorded)"
                is_open = state_text.lower() not in ("fixed validated", "declined")

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
                # Only reachable under the "everything" filter - _populate_issue_tree hides a
                # promoted row otherwise, so its real home stays the Tests tab while it's active.
                text = "Picked up as %s.\n\nRef: %s\nFiled: %s\n\n%s" % (
                    tag, ref, row.get("filed", "") if row else "", row.get("what", "") if row else "")
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

        text = ("Pending - not yet picked up into tests.md.\n\n"
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
            # format_ref()/next_ref_number() file the cancellation request's own ref under, and a
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

        new_ref = format_ref(target_kind, next_ref_number(target_kind))

        block = (
            "### %s - %s - Cancel %s\n\n"
            "**Kind:** %s  \n"
            "**Raised from:** cancellation request for %s - %s  \n"
            "**Filed:** %s  \n"
            "**Build:** %s\n\n"
            "%s"
        ) % (
            new_ref,
            datetime.date.today().isoformat(),
            ref,
            target_kind,
            ref,
            summary,
            datetime.datetime.now().strftime("%Y-%m-%d %H:%M"),
            self._build_note(),
            reason.strip() or "(no reason given)",
        )

        try:
            append_to_inbox(ISSUES_MD, block)

        except Exception as bad:
            # Same failure modes as free_observation(): a missing '## Inbox' heading, or
            # OneDrive holding the file so os.replace raises PermissionError.  Left unhandled,
            # this callback would just stop - no dialog, nothing printed under pythonw - and the
            # cancellation request would look like it never happened.
            messagebox.showerror("Could not file the cancellation request", str(bad), parent=self)
            return

        self._refresh_issue_tabs()

        self._say("Cancellation requested for %s - filed as %s." % (ref, new_ref))

    def _jump_to_test(self, tag):
        if not tag:
            return

        if tag not in self.doc.by_tag:
            messagebox.showinfo("Not found", "%s is not in tests.md (yet)." % tag, parent=self)
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
        outer = ttk.Frame(parent)

        head = ttk.Frame(outer)
        head.pack(fill=tk.X)

        self.head_label = ttk.Label(head, text="", style="Tag.TLabel", anchor=tk.W)
        self.head_label.pack(fill=tk.X)

        meta = ttk.Frame(head)
        meta.pack(fill=tk.X, pady=(0, 6))

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

        self.meta_from = ttk.Label(meta, text="", style="Sub.TLabel")
        self.meta_from.pack(side=tk.LEFT, padx=(14, 0))

        book = ttk.Notebook(outer)
        book.pack(fill=tk.BOTH, expand=True)

        self.what_text = self._read_only(book)
        book.add(self.what_text.master, text="  What to do  ")

        self.comments_text = self._read_only(book)
        book.add(self.comments_text.master, text="  Comments so far  ")

        self.book = book

        answer = ttk.Labelframe(outer, text=" Your answer ", padding=PAD)
        answer.pack(fill=tk.X, pady=(PAD, 0))

        self.result_var = tk.StringVar(value="")

        row = ttk.Frame(answer)
        row.pack(fill=tk.X)

        for value, label in RESULTS:
            ttk.Radiobutton(row, text=label.split(" - ")[0], value=value,
                            variable=self.result_var).pack(side=tk.LEFT, padx=(0, 14))

        ttk.Label(answer, text="What happened (optional, but this is the part that gets read):",
                  style="Sub.TLabel").pack(anchor=tk.W, pady=(8, 2))

        self.feedback = tk.Text(answer, height=6, wrap=tk.WORD, font=("Segoe UI", 10))
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

        buttons = ttk.Frame(outer, padding=(0, PAD, 0, 0))
        buttons.pack(fill=tk.X)

        ttk.Button(buttons, text="\u25c0  Previous", command=lambda: self.step(-1)).pack(side=tk.LEFT)
        ttk.Button(buttons, text="Next  \u25b6", command=lambda: self.step(1)).pack(side=tk.LEFT, padx=4)

        ttk.Button(buttons, text="Submit and next   (Ctrl+Enter)", style="Big.TButton",
                   command=self.submit).pack(side=tk.RIGHT)

        self.skip_button = ttk.Button(buttons, text="Skip", command=self.toggle_skip)
        self.skip_button.pack(side=tk.RIGHT, padx=6)

        return outer

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
        """The 'Show:' dropdown drives all three tabs, not just Tests - a feature request or bug
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

        message = ("%d entries, %d not validated, %d of those changed since you judged them, "
                  "%d answered here this session.  Showing %d."
                  % (total, openn, again, done, len(self.tree.get_children())))

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
            # the app.  A preview here is not a substitute for reading it in issues.md, but it is
            # the difference between knowing it exists and not.
            preview = self.issues_doc.freeform.strip().replace("\n", " ")

            if len(preview) > 80:
                preview = preview[:77] + "..."

            bits.append("FREEFORM note(s) in the Inbox, not shown in either tab - see "
                       "issues.md: \"%s\"" % preview)

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

        self._fill(self.what_text, entry.what)
        self._fill(self.comments_text, entry.comments or "(nothing yet)")

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
        widget.config(state=tk.NORMAL)
        widget.delete("1.0", tk.END)
        widget.insert("1.0", text)
        widget.config(state=tk.DISABLED)

    def _refresh_observations(self):
        for child in self.obs_rows_frame.winfo_children():
            child.destroy()

        if not self.observations:
            ttk.Label(self.obs_rows_frame, text="(none yet)", style="Sub.TLabel").pack(anchor=tk.W)
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

        ref = format_ref(got["kind"], next_ref_number(got["kind"]))

        try:
            self._file_observation(got, ref, None)

        except Exception as bad:
            # append_to_inbox raises IOError with no '## Inbox' heading; write_text's os.replace
            # raises PermissionError when OneDrive still holds the file open.  Unhandled, either
            # one unwinds out of this Tk callback - no confirmation dialog, and under pythonw
            # nothing printed either, so from Adam's side the button just did nothing.  That is
            # what led to an item being filed twice: he clicked again because it looked like the
            # first click had not worked.
            messagebox.showerror("Could not file the issue", str(bad), parent=self)
            return

        messagebox.showinfo("Filed", "Filed as %s in issues.md." % ref, parent=self)

        self._say("%s filed." % ref)

    def _file_observation(self, ob, ref, from_entry):
        raised = ("%s (%s)" % (from_entry.tag, from_entry.title)) if from_entry else \
                 "noticed while testing - not from a particular test"

        block = (
            "### %s - %s - %s\n\n"
            "**Kind:** %s  \n"
            "**Raised from:** %s  \n"
            "**Filed:** %s  \n"
            "**Build:** %s\n\n"
            "%s"
        ) % (
            ref,
            datetime.date.today().isoformat(),
            ob["summary"],
            ob["kind"],
            raised,
            datetime.datetime.now().strftime("%Y-%m-%d %H:%M"),
            self._build_note(),
            ob["detail"].strip() or "(no further detail)",
        )

        append_to_inbox(ISSUES_MD, block)

        self._refresh_issue_tabs()

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

        # Observations first: if tests.md turns out to have moved underneath us, the item is
        # already filed and the comment can be re-submitted, which is the harmless order of the two.
        receipts = []

        try:
            counters = {}       # kind -> next free number, so two of the same kind in one
                                # submit don't collide without re-scanning the files each time

            for ob in self.observations:
                kind = ob["kind"]

                if kind not in counters:
                    counters[kind] = next_ref_number(kind)

                ref = format_ref(kind, counters[kind])
                counters[kind] += 1

                self._file_observation(ob, ref, entry)
                receipts.append((ref, ob))

        except Exception as bad:
            messagebox.showerror("Could not file an observation", str(bad), parent=self)
            return

        # A retry of a previously-failed submit reuses the exact comment text composed last
        # time, receipts and all, rather than recomposing one - self.observations was cleared
        # the moment those observations were safely filed, so a fresh _compose() here would
        # produce a comment that names none of them, even though they are sitting in issues.md.
        if self.pending_comment and self.pending_comment[0] == entry.tag:
            comment = self.pending_comment[1]
        else:
            comment = self._compose(result, feedback, receipts)

        try:
            self.doc.append_comment(entry.tag, comment)

        except Exception as bad:
            # The observations, if any, are already safely in issues.md - clearing them here
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
                "Could not write tests.md",
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

            lines.append("Filed from this test: %s.  They are in `issues.md` until they are picked up."
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

        command, note = launch_plan()

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

        self.run_label.config(text="running - %s" % note)

        self._say("TrainControl started, simulate + debug.  Output: %s"
                  % os.path.basename(self.log_path))

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
            self.doc = TestsDoc(TESTS_MD)
        except Exception as bad:
            messagebox.showerror("Could not read tests.md", str(bad), parent=self)
            return

        self.current = None
        self._refresh_list(select_first=True)
        self._refresh_issue_tabs()
        self._say("Reloaded.")

    def clear_marks(self):
        if messagebox.askyesno(
                "Clear session marks",
                "This forgets which entries you have answered or skipped IN THIS APP.  Nothing "
                "written to the markdown files is touched.", parent=self):

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
            "Submit appends your words under that test's Comments in tests.md, dated and stamped "
            "with the build.  Bugs go to issues.md as OB-### items, feature requests as FR-###, "
            "cross-referenced from the test.\n\n"
            "New issue files something that has nothing to do with the entry on screen - it goes to "
            "issues.md the same way, without a test to reference.\n\n"
            "Skip writes nothing at all - it just marks the row so you can find it again, and stays "
            "in the list rather than disappearing.  The same button becomes Unskip when you're back "
            "on a skipped row.\n\n"
            "This app never changes a Disposition or the ledger - Claude sets those from what you "
            "wrote, which is the rule that makes the file mean anything.\n\n"
            "Every write backs the file up first, into .triage-backups.  From a terminal, run this "
            "script with an argument (stats, tests, issues, verify-ledger, --help) for the same data "
            "as JSON, no window needed.", parent=self)

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

        self.geometry("+%d+%d" % (parent.winfo_rootx() + 120, parent.winfo_rooty() + 120))

        self.grab_set()
        parent.wait_window(self)

    def _ok(self):
        summary = self.summary.get().strip()

        if not summary:
            messagebox.showinfo("One line, please",
                                "A summary is what makes the list readable later.", parent=self)
            return

        detail = self.detail.get("1.0", tk.END).strip()

        boundary = INBOX_BOUNDARY_LOOKALIKE_RE.search(detail)

        if boundary:
            messagebox.showinfo(
                "A line looks like a section boundary",
                "A line starting with \"%s\" reads as the end of the Inbox section to this "
                "file's own parser - that is how a '---' or a '## heading' typed into a detail "
                "body ends up splicing this item into the middle of an existing one instead of "
                "adding a new one.\n\n"
                "Indent that line (a leading space is enough) or reword it, then Add again."
                % boundary.group(1), parent=self)
            return

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
#   2   not found (tests.md itself missing, issues.md missing, or a --tag that doesn't exist)
#   3   verify-ledger only: it ran fine, but the ledger is not clean - distinct from 1 so a
#       script can tell "verify-ledger says the FILE needs a hand edit" apart from "verify-ledger
#       itself broke", which used to be the same exit code either way.

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


def cli_stats(_args):
    doc = TestsDoc(TESTS_MD)
    issues = IssuesDoc(ISSUES_MD) if os.path.exists(ISSUES_MD) else None

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
    doc = TestsDoc(TESTS_MD)

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
    if not os.path.exists(ISSUES_MD):
        return {"error": "issues.md not found"}, 2

    doc = IssuesDoc(ISSUES_MD)

    pending = doc.pending

    if args.kind:
        wanted = "feature request" if args.kind.startswith("feat") else "bug"
        pending = [it for it in pending if it.kind == wanted]

    out = {"pending": [it.as_dict() for it in pending], "freeform_pending": doc.freeform}

    if args.all:
        out["picked_up"] = doc.picked

    return out, 0


LEDGER_CELL_SPLIT_RE = re.compile(r'(?<!\\)\|')
LEDGER_TAG_CELL_RE = re.compile(r'^\[(MT-\d+)\]\(([^)]*)\)$')


def _ledger_row_cells(line):
    """A markdown table row's cells - tolerant of an escaped '\\|' inside one.

    The five-group regex this replaced (five '[^|]*' groups) went blind the moment a cell
    contained a '|' at all, escaped or not: that one character made the whole row fail to match,
    so the row was reported as simply missing from the ledger rather than as a row with an
    unusual What column.  Splitting on '|' and counting cells sees the row either way.

    Returns None for a line that is not a '| ... | ... |' row at all (ordinary prose, or a
    blank line between rows).
    """

    line = line.strip()

    if not line.startswith("|") or not line.endswith("|"):
        return None

    parts = LEDGER_CELL_SPLIT_RE.split(line)

    if len(parts) < 2 or parts[0].strip() or parts[-1].strip():
        return None

    return [p.strip() for p in parts[1:-1]]


def entries_without_a_separator(tests_text):
    """Test entries whose section does not end with the rule that closes it.

    Every entry runs from its own anchor to the next one and closes with a line of three dashes. An
    entry missing that runs on into the next one - so the next entry's anchor sits inside its body,
    and anything reading the file by section gets one entry where there are two.

    Adam, 2026-08-25: "MT-177 works but I cannot submit it in the triage app for some reason."

    It was not about MT-177. Moving the picked-up OB entries into the MTs their receipts name
    inserted at the end of the captured section, which is AFTER the closing rule - so the rule ended
    up mid-entry and three sections ran together. This is cheap to check and was not being checked,
    which is why an editing script could do it silently and the first sign was an application
    refusing a submission.
    """

    import re as _re

    anchors = [m for m in _re.finditer(r'^<a id="(mt-\d+)"></a>$', tests_text, _re.M)]

    out = []

    for at, anchor in enumerate(anchors):
        start = anchor.end()
        end = anchors[at + 1].start() if at + 1 < len(anchors) else len(tests_text)

        body = [line.strip() for line in tests_text[start:end].split("\n") if line.strip()]

        if not body:
            continue

        if body[-1] != "---":
            out.append({"ref": anchor.group(1).upper(), "ends_with": body[-1][:60]})

    return out


def entries_without_kind(issues_text):
    """Inbox entries with no **Kind:** line.

    The triage app defaults a missing Kind to "bug".  That is harmless for an OB, which is a bug
    anyway, and wrong for an FR - so a feature request filed without one is silently filed as a bug
    and shows up in the wrong list, which is how Adam found it on 2026-08-24: "the state of FR-018
    seems odd, I see it in the bug list, not in the MT list."

    Six of the ten open entries were in this state.  Only the FR was visible, because for the other
    five the wrong default happened to be the right answer - which is exactly the kind of fault that
    sits there until the one case where it matters.
    """

    import re as _re

    inbox_at = issues_text.find("## Inbox")

    if inbox_at < 0:
        return []

    end = issues_text.find("\n## ", inbox_at + 1)
    inbox = issues_text[inbox_at:end if end > 0 else len(issues_text)]

    out = []

    for entry in _re.finditer(r"### ((?:OB|FR)-\d+) - .*?(?=\n### (?:OB|FR)-|\Z)", inbox, _re.S):
        if "**Kind:**" not in entry.group(0):
            out.append({"ref": entry.group(1)})

    return out


def undated_followups(issues_text):
    """Inbox entries whose later additions do not say who wrote them and when.

    Adam, 2026-08-24: "once there is a follow up, we need to clearly demarcate old comments (such as
    with a date)".  `tests.md` has had this as rule 6 since the file existed; `issues.md` did not, so
    an item picked up, deferred and re-scoped over four days read as one voice speaking once.

    The shape checked is the one the rule asks for: every paragraph that OPENS in bold, after the
    first, must carry a date.  A paragraph that opens in bold is how both files mark a new voice, and
    the ones that do not are ordinary prose inside somebody's comment.

    Deliberately blind to the metadata the triage app stamps - Kind, Raised from, Filed, Build - which
    open in bold on their own line and belong to the original filing.
    """

    import re as _re

    inbox_at = issues_text.find("## Inbox")

    if inbox_at < 0:
        return []

    end = issues_text.find("\n## ", inbox_at + 1)
    inbox = issues_text[inbox_at:end if end > 0 else len(issues_text)]

    authors = ("Claude", "Adam")

    out = []

    for entry in _re.finditer(r"### ((?:OB|FR)-\d+) - .*?(?=\n### (?:OB|FR)-|\Z)", inbox, _re.S):
        ref = entry.group(1)

        for run in _re.finditer(r"\*\*([^*]{2,90}?)\*\*", entry.group(0)):
            label = run.group(1).strip()

            if not any(label.startswith(a) for a in authors):
                continue

            if not _re.search(r"20\d\d-\d\d-\d\d", label):
                out.append({"ref": ref, "attribution": label[:70]})

    return out


def cli_verify_ledger(_args):
    """Compare the ledger table in tests.md against what the entries themselves say.

    Read-only on purpose - the ledger allows hand notes on a row (README.md: "crossing something
    out or adding a note to it is fine"), and a wholesale rewrite here would erase them without
    knowing which rows carried one.  This reports exactly what a human edit needs to fix.
    """

    text, _ = read_text(TESTS_MD)

    doc = TestsDoc(TESTS_MD)

    ledger_start = text.find("\n## Ledger")
    ledger_end = text.find("\n## The tests") if ledger_start >= 0 else -1

    if ledger_start < 0 or ledger_end < 0:
        return {"error": "could not find the '## Ledger' section in tests.md"}, 2

    section = text[ledger_start:ledger_end]

    ledger_rows = {}
    duplicate_ledger_rows = []
    malformed_ledger_rows = []
    bad_href = []

    for line in section.splitlines():
        cells = _ledger_row_cells(line)

        if not cells:
            continue

        tag_match = LEDGER_TAG_CELL_RE.match(cells[0])

        if not tag_match:
            continue    # the header row, the '|---|...' rule row, or not a tag cell at all

        tag, href = tag_match.group(1), tag_match.group(2)

        if len(cells) != 5:
            # A tag cell that parsed but the row still doesn't have five cells - a stray '|'
            # that could not be told apart from a column break, most likely.  Recorded rather
            # than silently skipped, and not folded into ledger_rows since which of its cells is
            # Date/What/Disposition/From is not knowable.
            malformed_ledger_rows.append({"tag": tag, "cells": len(cells)})
            continue

        if tag in ledger_rows:
            # Two ledger rows sharing one iid used to collapse into a dict silently, keeping
            # whichever came last and reporting a ledger that looked entirely fine.
            duplicate_ledger_rows.append(tag)
            continue

        if href != "#" + tag.lower():
            # [MT-084](#mt-048) - a mistyped link - used to verify clean, because the href was
            # matched (`[^)]*`) and then thrown away without being looked at again.
            bad_href.append({"tag": tag, "href": href})

        ledger_rows[tag] = {
            "date": cells[1],
            "what": cells[2],
            "disposition": cells[3],
            "from": cells[4],
        }

    should_be_open = dict((e.tag, e) for e in doc.entries if e.is_open)

    missing = sorted(tag for tag in should_be_open if tag not in ledger_rows)
    stale = sorted(tag for tag in ledger_rows if tag not in should_be_open)

    # Disposition drift is unambiguous: the two either say the same word or they don't, and if they
    # don't the ledger is simply wrong.  Date is the same - it is copied from the entry, not
    # composed, so any difference at all is drift.
    #
    # "What" allows a little more: README.md calls it "one line about it", not a verbatim copy of
    # the title, so this only flags a pair where NEITHER side is contained in the other
    # (case-insensitively) - the same tolerance "from" already used, for the same reason: it
    # still catches a ledger that was never updated after a title changed, without flagging every
    # ledger row that legitimately shortens its ONE LINE version of a long title.
    #
    # "From" is judged the same way as What, for the same reason - the ledger's column is a
    # deliberately short label ("Tier 1", "AR-20") while the entry's own From field is sometimes
    # the long sentence it was filed under, so equality would flag that shorthand as broken on
    # nearly every Tier 1-6 row, which is noise, not a finding.  It still catches a ledger citing
    # a tag the entry does not mention at all (MT-036: ledger says AR-20, the entry says
    # "hands-on testing" - not a shorthand of it) and a ledger missing a tag the entry has gained
    # since (MT-030 the other way round).
    disposition_drift = []
    date_drift = []
    what_notes = []
    from_notes = []

    def _neither_contains(a, b):
        a, b = a.lower(), b.lower()
        return a not in b and b not in a

    for tag, row in ledger_rows.items():
        e = should_be_open.get(tag)

        if not e:
            continue

        if row["disposition"] != e.disposition:
            disposition_drift.append({
                "tag": tag, "ledger": row["disposition"], "actual": e.disposition,
            })

        if row["date"] != e.date:
            date_drift.append({"tag": tag, "ledger": row["date"], "actual": e.date})

        if _neither_contains(row["what"], e.title):
            what_notes.append({"tag": tag, "ledger_what": row["what"], "actual_title": e.title})

        if _neither_contains(row["from"], e.origin):
            from_notes.append({"tag": tag, "ledger_from": row["from"], "actual_from": e.origin})

    # README.md rule 4: exactly four words, and no others.  Checked against every entry, not just
    # the open ones - a validated or superseded entry can still be misspelled or hand-typed wrong,
    # and would otherwise never be looked at again once it drops off the "should be open" list.
    invalid_disposition = [
        {"tag": e.tag, "disposition": e.disposition}
        for e in doc.entries
        if e.disposition.strip().lower() not in VALID_DISPOSITIONS
    ]

    # Two items sharing one ref, which is the failure this check was added for: a feature request
    # filed through the app and a second one written by hand both took FR-014, and the list showed
    # two rows under one identifier.  Cheap to detect, and impossible to notice by eye in a file this
    # size.
    duplicate_refs = []

    if os.path.exists(ISSUES_MD):
        issues_text, _ = read_text(ISSUES_MD)

        seen = {}

        for ref in re.findall(r'^### ((?:OB|FR)-\d+) - ', issues_text, re.M):
            seen[ref] = seen.get(ref, 0) + 1

            if seen[ref] == 2:
                duplicate_refs.append(ref)

    undated = undated_followups(read_text(ISSUES_MD)[0])
    no_kind = entries_without_kind(read_text(ISSUES_MD)[0])
    no_rule = entries_without_a_separator(read_text(TESTS_MD)[0])

    out = {
        "duplicate_tags": doc.duplicate_tags,
        "duplicate_refs": duplicate_refs,
        "duplicate_ledger_rows": duplicate_ledger_rows,
        "malformed_ledger_rows": malformed_ledger_rows,
        "bad_href": bad_href,
        "ledger_rows": len(ledger_rows),
        "should_be_open": len(should_be_open),
        "missing_from_ledger": missing,
        "stale_in_ledger": stale,
        "disposition_drift": disposition_drift,
        "date_drift": date_drift,
        "what_notes": what_notes,
        "from_notes": from_notes,
        "invalid_disposition": invalid_disposition,
        "undated_followups": undated,
        "entries_without_kind": no_kind,
        "entries_without_a_separator": no_rule,
        "clean": not (missing or stale or disposition_drift or date_drift or duplicate_refs
                      or doc.duplicate_tags or duplicate_ledger_rows or malformed_ledger_rows
                      or bad_href or invalid_disposition or undated
                      or no_kind or no_rule),
    }

    return out, (0 if out["clean"] else 3)


CLI_COMMANDS = {
    "stats": (cli_stats, "Counts: tests by disposition, issues pending.  Does NOT check the "
                        "ledger - see verify-ledger for that."),
    "tests": (cli_tests, "List entries. Default: open only. --all for everything, a TAG "
                        "argument (positional, not --tag) for one."),
    "issues": (cli_issues, "List Inbox items not yet picked up. --kind bug|feature, --all adds picked-up."),
    "verify-ledger": (cli_verify_ledger, "Diff the ledger table against the entries. Read-only."),
}


def build_arg_parser():
    import argparse

    parser = argparse.ArgumentParser(
        prog="triage.py",
        description="Query tests.md / issues.md as JSON.  Run with no arguments to open the window "
                    "instead.")

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

    if not os.path.exists(TESTS_MD):
        print(json.dumps({"error": "tests.md not found next to this script"}), file=sys.stderr)
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

    if not os.path.exists(TESTS_MD):
        print("tests.md not found next to this script (%s)" % HERE)
        return 1

    Triage().mainloop()

    return 0


if __name__ == "__main__":
    sys.exit(main())
