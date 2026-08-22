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

  A test result            appended under that entry's "#### Comments" in tests.md, dated, signed,
                           and stamped with the build it was run against.
  A bug you noticed        an OB-### item in bug-reports.md, cross-referenced from the test's
                           comments if it came from one.
  A feature request        an OB-### item in feature-requests.md, the inbox that already existed.

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
from tkinter import ttk, messagebox


# --------------------------------------------------------------------------------------------
# Where everything lives
# --------------------------------------------------------------------------------------------

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, os.pardir, os.pardir))

TESTS_MD = os.path.join(HERE, "tests.md")
FEATURES_MD = os.path.join(HERE, "feature-requests.md")
BUGS_MD = os.path.join(HERE, "bug-reports.md")
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
        stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
        name = "%s.%s.bak" % (os.path.basename(path), stamp)
        shutil.copy2(path, os.path.join(BACKUP_DIR, name))

    out = text.replace("\n", "\r\n") if crlf else text

    tmp = path + ".triage-tmp"

    with io.open(tmp, "wb") as fh:
        fh.write(out.encode("utf-8"))

    os.replace(tmp, path)

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
FIELD_RE = r'^\*\*%s:?\*\*\s*(.*?)\s*$'


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
        return self.disposition.strip().lower() != VALIDATED

    def with_comment(self, comment):
        """This entry's block with a comment appended at the bottom of its Comments section.

        Append only, and above the rule that closes the entry - which is the last '---' line in the
        block, because the block runs anchor to anchor.
        """

        text = comment.strip() + "\n\n"

        cut = self.block.rfind("\n---")

        if cut < 0:
            return self.block.rstrip() + "\n\n" + text

        return self.block[:cut + 1] + text + self.block[cut + 1:]


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


def next_observation_number():
    """The next OB-### across both inboxes, so the two never collide."""

    highest = 0

    for path in (BUGS_MD, FEATURES_MD, TESTS_MD):
        if not os.path.exists(path):
            continue

        text, _ = read_text(path)

        for found in re.findall(r'\bOB-(\d+)\b', text):
            highest = max(highest, int(found))

    return highest + 1


INBOX_EMPTY = "*(empty)*"


def append_to_inbox(path, block):
    """Put an item at the bottom of a file's '## Inbox' section, above the rule that closes it."""

    text, crlf = read_text(path)

    start = text.find("\n## Inbox")

    if start < 0:
        raise IOError("%s has no '## Inbox' section" % os.path.basename(path))

    body_from = text.index("\n", start + 1)

    end = text.find("\n---", body_from)

    if end < 0:
        end = len(text)

    section = text[body_from:end]

    if INBOX_EMPTY in section:
        section = section.replace(INBOX_EMPTY, "").rstrip() + "\n"

    section = section.rstrip("\n") + "\n\n" + block.strip() + "\n"

    write_text(path, text[:body_from] + section + text[end:], crlf)


BUGS_SKELETON = u"""# Bug reports

Adam writes here - usually through [triage.py](triage.py), sometimes by hand. Claude reads here,
turns each item into a finding in `docs/reviews/` under the round's prefix, opens an `MT-###` entry
in [tests.md](tests.md) to cover it, and clears this file back to empty.

This is the bug half of the inbox that [feature-requests.md](feature-requests.md) is the feature
half of, and it works exactly the same way. The split is only so that "something is broken" and
"I would like something new" do not have to be sorted out later from one pile.

**Filing is not asking for it to be fixed.** Filing puts it on the list; asking gets it worked. The
one exception is your own judgement - say a bug is urgent in its text and it will be treated that
way.

Items keep their `OB-###` reference when they are picked up, so a bug can be traced from the test
that turned it up, through the finding that fixed it, to the test written to cover it.

---

## Inbox

*(empty)*

---

## What has been picked up

Newest first. This is only a receipt - once picked up, the item lives in `tests.md` under its own
`MT-###` tag, and that is where its state and its comments are.

| Filed | Ref | Raised from | What | Became |
|---|---|---|---|---|
"""


def ensure_bugs_file():
    if not os.path.exists(BUGS_MD):
        with io.open(BUGS_MD, "wb") as fh:
            fh.write(BUGS_SKELETON.encode("utf-8"))


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

def find_java():
    home = os.environ.get("JAVA_HOME")

    if home:
        candidate = os.path.join(home, "bin", "java.exe")

        if os.path.exists(candidate):
            return candidate

    found = shutil.which("java")

    if found:
        return found

    if os.path.exists(FALLBACK_JAVA):
        return FALLBACK_JAVA

    return None


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

    java = find_java()

    if not java:
        return None, "No java found.  Set JAVA_HOME, or put java on the PATH."

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
                "build\\classes, compiled %s" % time.strftime("%d %b %H:%M", time.localtime(stamp)),
            )

    if os.path.exists(jar):
        stamp = os.path.getmtime(jar)

        return (
            [java, "-jar", jar] + args,
            "dist\\TrainControl.jar, built %s (build\\classes is missing - this may be old)"
            % time.strftime("%d %b %H:%M", time.localtime(stamp)),
        )

    return None, "Neither build\\classes nor dist\\TrainControl.jar exists.  Build in NetBeans first."


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

        ensure_bugs_file()

        self.state_ = State()
        self.doc = TestsDoc(TESTS_MD)
        self.build = git_build()

        self.process = None
        self.log_path = None
        self.launched_from = None

        self.current = None
        self.observations = []       # pending, for the entry on screen

        self._build_ui()
        self._refresh_list(select_first=True)

        geometry = self.state_.data.get("geometry")

        self.geometry(geometry if geometry else "1220x820")

        self.protocol("WM_DELETE_WINDOW", self._on_close)

        self.bind("<Control-r>", lambda e: self.reload())
        self.bind("<Control-Return>", lambda e: self.submit())
        self.bind("<Control-l>", lambda e: self.launch())

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

        panes.add(self._build_list(panes), weight=0)
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
        f.add_command(label="Open bug-reports.md", command=lambda: self._open(BUGS_MD))
        f.add_command(label="Open feature-requests.md", command=lambda: self._open(FEATURES_MD))
        f.add_command(label="Open the backups folder", command=lambda: self._open(BACKUP_DIR))
        f.add_separator()
        f.add_command(label="Quit", command=self._on_close)
        bar.add_cascade(label="File", menu=f)

        t = tk.Menu(bar, tearoff=0)
        t.add_command(label="Launch TrainControl (simulate + debug)\tCtrl+L", command=self.launch)
        t.add_command(label="Show TrainControl output", command=self.show_output)
        t.add_separator()
        t.add_command(label="File an observation not tied to a test", command=self.free_observation)
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

        ttk.Button(bar, text="\u25b6  Launch TrainControl", style="Big.TButton",
                   command=self.launch).pack(side=tk.LEFT)

        self.run_label = ttk.Label(bar, text="not started", style="Sub.TLabel")
        self.run_label.pack(side=tk.LEFT, padx=(8, 0))

        ttk.Button(bar, text="Output\u2026", command=self.show_output).pack(side=tk.LEFT, padx=(8, 0))

        ttk.Button(bar, text="Observation\u2026", command=self.free_observation).pack(side=tk.RIGHT)

        ttk.Label(bar, text="Show:").pack(side=tk.RIGHT, padx=(12, 4))

        self.filter_var = tk.StringVar(value=self.state_.data.get("filter", "open"))

        picker = ttk.Combobox(bar, textvariable=self.filter_var, width=26, state="readonly",
                              values=["open - not yet answered here",
                                      "open - everything not validated",
                                      "answered this session",
                                      "everything, validated included"])

        picker.pack(side=tk.RIGHT)
        picker.bind("<<ComboboxSelected>>", lambda e: self._refresh_list())

        if self.filter_var.get() not in picker["values"]:
            self.filter_var.set("open - not yet answered here")

    def _build_list(self, parent):
        frame = ttk.Frame(parent, width=380)

        search = ttk.Frame(frame)
        search.pack(fill=tk.X, pady=(0, 4))

        ttk.Label(search, text="Find:").pack(side=tk.LEFT)

        self.search_var = tk.StringVar()
        box = ttk.Entry(search, textvariable=self.search_var)
        box.pack(side=tk.LEFT, fill=tk.X, expand=True, padx=(4, 0))
        box.bind("<KeyRelease>", lambda e: self._refresh_list())

        columns = ("mark", "tag", "date", "what")

        self.tree = ttk.Treeview(frame, columns=columns, show="headings", selectmode="browse")

        for name, title, width, anchor in (
            ("mark", "", 26, tk.CENTER),
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

        self.tree.tag_configure("validated", foreground="#7a7a7a")
        self.tree.tag_configure("unvalidated", foreground="#8a5a00")

        self.tree.bind("<<TreeviewSelect>>", self._on_select)

        return frame

    def _build_detail(self, parent):
        outer = ttk.Frame(parent)

        head = ttk.Frame(outer)
        head.pack(fill=tk.X)

        self.head_label = ttk.Label(head, text="", style="Tag.TLabel", anchor=tk.W)
        self.head_label.pack(fill=tk.X)

        self.meta_label = ttk.Label(head, text="", style="Sub.TLabel", anchor=tk.W)
        self.meta_label.pack(fill=tk.X, pady=(0, 6))

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

        ttk.Button(obs, text="Remove", command=self.remove_observation).pack(side=tk.RIGHT)
        ttk.Button(obs, text="+ Feature request",
                   command=lambda: self.add_observation("feature request")).pack(side=tk.RIGHT, padx=4)
        ttk.Button(obs, text="+ Bug",
                   command=lambda: self.add_observation("bug")).pack(side=tk.RIGHT)

        self.obs_list = tk.Listbox(answer, height=3, font=("Segoe UI", 9))
        self.obs_list.pack(fill=tk.X, pady=(4, 0))

        buttons = ttk.Frame(outer, padding=(0, PAD, 0, 0))
        buttons.pack(fill=tk.X)

        ttk.Button(buttons, text="\u25c0  Previous", command=lambda: self.step(-1)).pack(side=tk.LEFT)
        ttk.Button(buttons, text="Next  \u25b6", command=lambda: self.step(1)).pack(side=tk.LEFT, padx=4)

        ttk.Button(buttons, text="Submit and next   (Ctrl+Enter)", style="Big.TButton",
                   command=self.submit).pack(side=tk.RIGHT)
        ttk.Button(buttons, text="Skip", command=self.skip).pack(side=tk.RIGHT, padx=6)

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

            if mode.startswith("open - not yet"):
                if not e.is_open or mark in ("done", "skipped"):
                    continue
            elif mode.startswith("open - everything"):
                if not e.is_open:
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

    def _refresh_list(self, select_first=False):
        self.state_.data["filter"] = self.filter_var.get()

        keep = self.current.tag if self.current else None

        self.tree.delete(*self.tree.get_children())

        for e in self._visible():
            mark = self.state_.mark(e.tag)

            glyph = {"done": "\u2713", "skipped": "\u2192"}.get(mark, "")

            if not glyph and self.state_.draft(e.tag):
                glyph = "\u2022"

            tags = ()

            if not e.is_open:
                tags = ("validated",)
            elif e.disposition.strip().lower() == "fixed unvalidated":
                tags = ("unvalidated",)

            self.tree.insert("", tk.END, iid=e.tag, tags=tags,
                             values=(glyph, e.tag, e.date, e.title))

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

        self._say("%d entries, %d not validated, %d answered here this session.  Showing %d."
                  % (total, openn, done, len(self.tree.get_children())))

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

        if entry is None:
            self.head_label.config(text="Nothing to show")
            self.meta_label.config(text="Change the filter, or clear the search box.")
            self._fill(self.what_text, "")
            self._fill(self.comments_text, "")
            return

        self.head_label.config(text="%s  \u2014  %s" % (entry.tag, entry.title))

        self.meta_label.config(text="written %s     disposition: %s     from: %s"
                               % (entry.written, entry.disposition, entry.origin))

        self._fill(self.what_text, entry.what)
        self._fill(self.comments_text, entry.comments or "(nothing yet)")

        draft = self.state_.draft(entry.tag) or {}

        self.result_var.set(draft.get("result", ""))

        self.feedback.delete("1.0", tk.END)
        self.feedback.insert("1.0", draft.get("feedback", ""))

        self.observations = list(draft.get("observations", []))
        self._refresh_observations()

        self.book.select(0)

    def _fill(self, widget, text):
        widget.config(state=tk.NORMAL)
        widget.delete("1.0", tk.END)
        widget.insert("1.0", text)
        widget.config(state=tk.DISABLED)

    def _refresh_observations(self):
        self.obs_list.delete(0, tk.END)

        for ob in self.observations:
            self.obs_list.insert(tk.END, "[%s]  %s" % (ob["kind"], ob["summary"]))

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

    def remove_observation(self):
        picked = self.obs_list.curselection()

        if picked:
            del self.observations[picked[0]]
            self._refresh_observations()
            self._stash_draft()

    def free_observation(self):
        """Something you noticed that no test asked about.  Filed on its own."""

        got = ObservationDialog(self, "bug", standalone=True).result

        if not got:
            return

        number = next_observation_number()

        self._file_observation(got, number, None)

        messagebox.showinfo("Filed", "Filed as OB-%03d in %s."
                            % (number, os.path.basename(
                                BUGS_MD if got["kind"] == "bug" else FEATURES_MD)), parent=self)

        self._say("OB-%03d filed." % number)

    def _file_observation(self, ob, number, from_entry):
        raised = ("%s (%s)" % (from_entry.tag, from_entry.title)) if from_entry else \
                 "noticed while testing - not from a particular test"

        block = (
            "### OB-%03d - %s - %s\n\n"
            "**Kind:** %s  \n"
            "**Raised from:** %s  \n"
            "**Filed:** %s  \n"
            "**Build:** %s\n\n"
            "%s"
        ) % (
            number,
            datetime.date.today().isoformat(),
            ob["summary"],
            ob["kind"],
            raised,
            datetime.datetime.now().strftime("%Y-%m-%d %H:%M"),
            self._build_note(),
            ob["detail"].strip() or "(no further detail)",
        )

        append_to_inbox(BUGS_MD if ob["kind"] == "bug" else FEATURES_MD, block)

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
            number = next_observation_number()

            for ob in self.observations:
                self._file_observation(ob, number, entry)
                receipts.append((number, ob))
                number += 1

        except Exception as bad:
            messagebox.showerror("Could not file an observation", str(bad), parent=self)
            return

        comment = self._compose(result, feedback, receipts)

        try:
            self.doc.append_comment(entry.tag, comment)

        except Exception as bad:
            messagebox.showerror(
                "Could not write tests.md",
                "%s\n\nYour observations were filed.  The comment was not written - it is still in "
                "the boxes." % bad, parent=self)
            return

        self.state_.clear_draft(entry.tag)
        self.state_.mark(entry.tag, "done")

        self.observations = []

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
                "OB-%03d (%s - %s)" % (n, ob["kind"], ob["summary"]) for n, ob in receipts)

            lines.append("Filed from this test: %s.  They are in `%s` until they are picked up."
                         % (listed, "bug-reports.md / feature-requests.md"
                            if len(set(ob["kind"] for _n, ob in receipts)) > 1
                            else ("bug-reports.md" if receipts[0][1]["kind"] == "bug"
                                  else "feature-requests.md")))

        lines.append("")
        lines.append("*Run against %s.*" % self._build_note())

        return "\n".join(lines)

    def skip(self):
        if not self.current:
            return

        self._stash_draft()
        self.state_.mark(self.current.tag, "skipped")

        self._say("%s skipped - nothing written." % self.current.tag)
        self._advance()

    def _advance(self):
        rows = list(self.tree.get_children())

        here = rows.index(self.current.tag) if self.current.tag in rows else -1

        self._refresh_list()

        rows = list(self.tree.get_children())

        if not rows:
            self._show(None)
            return

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
        self._say("Reloaded.")

    def clear_marks(self):
        if messagebox.askyesno(
                "Clear session marks",
                "This forgets which entries you have answered or skipped IN THIS APP.  Nothing "
                "written to the markdown files is touched.", parent=self):

            self.state_.data["marks"] = {}
            self.state_.save()
            self._refresh_list()

    def about(self):
        messagebox.showinfo(
            "How this works",
            "Work down the list.  For each test: say whether it works, write what happened, add "
            "anything else you noticed, then Submit.\n\n"
            "Submit appends your words under that test's Comments in tests.md, dated and stamped "
            "with the build.  Bugs and feature requests go to bug-reports.md and "
            "feature-requests.md as OB-### items, cross-referenced from the test.\n\n"
            "Skip writes nothing at all.\n\n"
            "This app never changes a Disposition or the ledger - Claude sets those from what you "
            "wrote, which is the rule that makes the file mean anything.\n\n"
            "Every write backs the file up first, into .triage-backups.", parent=self)

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

        self.destroy()


class ObservationDialog(tk.Toplevel):
    """One bug or one feature request, in as few boxes as it can be said in."""

    def __init__(self, parent, kind, standalone=False):
        tk.Toplevel.__init__(self, parent)

        self.result = None

        self.title("Something you noticed")
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

        self.result = {
            "kind": self.kind.get(),
            "summary": summary,
            "detail": self.detail.get("1.0", tk.END).strip(),
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


def main():
    if not os.path.exists(TESTS_MD):
        print("tests.md not found next to this script (%s)" % HERE)
        return 1

    Triage().mainloop()

    return 0


if __name__ == "__main__":
    sys.exit(main())
