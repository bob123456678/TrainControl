# -*- coding: utf-8 -*-
"""The manual-test and issue tracker: a database, the API that reads and writes it, and a text dump of it.

Adam, 2026-08-25: "for the triage app, lets convert it to use a database and then add apis to check, update, and
file dispositions/cases. then you dont need to scan with regexes or use other tricks."  The first version put a
database in front of the markdown and left the markdown the record, so every reader had to sync first, and a stale copy
erased nine of his comments on 2026-09-15.  On 2026-10-19 he asked "why aren't we storing all tests in a database, why
have md files at all?" and chose "DB only, plus a text dump" (FR-136, Phase 1c).


THE RECORD IS triage.db
-----------------------

Every manual test, every verdict and comment, every Inbox item, every receipt and every review finding is a row in
`triage.db`.  The triage app, the command line at the bottom of this file and every script read and write it through
the functions here.  Nothing is parsed from markdown: `tests.md`, `issues.md` and the release archives were imported
once, by `migrate`, and deleted; their history is in git.

  test      one row per MT-###: the entry exactly as written up to its first comment (`body`), and the rule that
            closes it (`tail`).  The date, title, disposition, From and whether it is reopened are read out of the
            entry's text whenever it changes, and never set on their own, so a field cannot disagree with the text.
  comment   one row per comment, in the order written: everything from its `**Adam, ...**` or `**Claude, ...**` marker
            to the next one, as written.  Who, the date and whether it is one of the four verdicts are read out of it.
            A test's text is its body, its comments and its tail, joined - which is what `show` prints.  An OB or FR
            takes comments too.
  issue     one row per OB-### or FR-### filed into the Inbox: its text as filed.  `inbox` is 1 while it is in the
            Inbox and 0 once it has been cleared out (Adam, 2026-09-24: a done item leaves the Inbox and keeps its
            receipt); the row is never deleted.
  receipt   one row per line of the old "What has been picked up" table: Filed, Ref, Kind, What, State, Became.
  note      the prose that surrounded those, kept word for word (the conventions, and the notes under the table).
  finding, dead_citation   the review catalogue, as before.  `findings.tsv` is still rendered from them.
  meta      when the store was migrated, and from what.


WRITES
------

Every write is one short transaction begun with BEGIN IMMEDIATE, so two processes - Adam's triage app and a script, say
- queue for the lock rather than read the same row and both write it; a busy store is waited on for up to 30 seconds.
A comment is INSERTED, never written by rewriting an entry, so there is no copy of the entry to go stale.  The app
re-reads the store whenever another process commits to it (PRAGMA data_version).


THE DUMP
--------

After every write the store is also written out as plain text in `dump/` beside it: one file per table, one record per
row in tag order, one field per line, and every line of a text written behind "| ".  So a new comment is a few added
lines in comments.txt, and a changed disposition is one changed line in tests.txt.  It exists for git: a history and a
diff a person can read, which a binary database cannot give.  NOTHING READS IT BACK except the Java tests, which have no
SQLite driver; agents can grep it, but the README sends them to the command line.  `verify` says whether it is the
store's own rows.
"""

from __future__ import print_function

import argparse
import contextlib
import datetime
import glob
import hashlib
import io
import json
import os
import re
import sqlite3
import sys

HERE = os.path.dirname(os.path.abspath(__file__))

if HERE not in sys.path:
    sys.path.insert(0, HERE)

import triage  # noqa: E402  - the path has to be set first


DB_FILE = os.path.join(HERE, "triage.db")

# The text dump, beside the store it is written from.
DUMP_NAME = "dump"
DUMP_DIR = os.path.join(HERE, DUMP_NAME)

FINDINGS_MIRROR = os.path.join(HERE, "findings.tsv")

# The reference documents that quote the record's counts (see render_counts)
REFERENCE_DIR = os.path.join(os.path.dirname(HERE), "reference")
BEHAVIOUR_FILE = os.path.join(REFERENCE_DIR, "behaviour.md")
QUESTIONS_FILE = os.path.join(REFERENCE_DIR, "open-questions.md")

# The markdown `migrate` reads, once, and nothing else ever does.
TESTS_MD = "tests.md"
ISSUES_MD = "issues.md"
ARCHIVE_GLOB = "tests-archive-*.md"

# How long a write waits for another process's write to finish before it gives up.
BUSY_SECONDS = 30


FINDING_SCHEMA = """
CREATE TABLE IF NOT EXISTS finding (
    ref         TEXT NOT NULL,      -- MON-C1, RGD-B2, DD-A7 - the citation the code uses
    document    TEXT NOT NULL,      -- the review it was written in
    line        INTEGER,            -- where in that document, so the prose is one jump away
    severity    TEXT,               -- A / B / C / D as the review graded it
    title       TEXT,               -- the finding's own heading: its author's one-line summary
    disposition TEXT,               -- Open / Fixed / Cancelled / Ruled, wherever the document says it
    evidence    TEXT,               -- the first `File.java:123` the body cites
    commit_id   TEXT,               -- the first commit id the body cites
    cited_by    TEXT,               -- source files that name this ref, comma-separated
    status      TEXT,               -- OURS, not the document's: survives every reload
    status_note TEXT,               -- when it was set that way, and on what evidence
    PRIMARY KEY (ref, document)     -- nine refs mean different things in different reviews
);

CREATE TABLE IF NOT EXISTS dead_citation (
    ref       TEXT PRIMARY KEY,     -- cited from the code, a finding in no document
    cited_by  TEXT                  -- the files that cite it, comma-separated
);

CREATE INDEX IF NOT EXISTS finding_by_ref ON finding (ref);
CREATE INDEX IF NOT EXISTS finding_by_disposition ON finding (disposition);
"""

# The tests and the issues.  NO SEMICOLON IN A COMMENT: `migrate` runs these one statement at a time, split on
# them.  `migrate` drops the tables the markdown-era store had under these names (test, verdict,
# issue, doc - a cache of the markdown) and creates these.  No statement here may name a column the old `test` or
# `issue` table lacks: this runs, as IF NOT EXISTS, on every connect, including to a store not yet migrated.
RECORD_SCHEMA = """
CREATE TABLE IF NOT EXISTS meta (
    key     TEXT PRIMARY KEY,
    value   TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS test (
    tag         TEXT PRIMARY KEY,   -- MT-###
    ordinal     INTEGER NOT NULL,   -- the order written in, which the list shows them in
    anchor      TEXT NOT NULL,      -- mt-###, as its first line names it
    body        TEXT NOT NULL,      -- the entry as written, up to its first comment
    tail        TEXT NOT NULL,      -- the rule that closes it, as written
    date        TEXT,               -- these six are READ OUT OF the text, every time it changes
    title       TEXT,
    disposition TEXT,
    origin      TEXT,
    written     TEXT,
    reopened    INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS comment (
    tag     TEXT NOT NULL,          -- MT-###, OB-### or FR-###
    seq     INTEGER NOT NULL,       -- 0 is the oldest
    text    TEXT NOT NULL,          -- as written, from its marker to the next
    who     TEXT,                   -- these five are read out of the text
    date    TEXT,
    kind    TEXT NOT NULL,          -- 'verdict' (one of the four) or 'note' (prose)
    verdict TEXT,                   -- the canonical verdict, NULL for a note
    note    TEXT,                   -- what was said, on one line
    PRIMARY KEY (tag, seq)
);

CREATE TABLE IF NOT EXISTS issue (
    ref         TEXT PRIMARY KEY,   -- OB-### or FR-###
    ordinal     INTEGER NOT NULL,   -- the order filed in
    text        TEXT NOT NULL,      -- the item as filed, heading and all
    inbox       INTEGER NOT NULL DEFAULT 1,  -- 1 while it is in the Inbox, 0 once cleared out
    kind        TEXT,               -- these five are read out of the text
    title       TEXT,
    filed       TEXT,
    filed_at    TEXT,
    raised_from TEXT,
    build       TEXT
);

CREATE TABLE IF NOT EXISTS receipt (
    ref     TEXT PRIMARY KEY,       -- what was picked up
    seq     INTEGER NOT NULL,       -- the order written in (shown newest first)
    filed   TEXT,
    kind    TEXT,
    what    TEXT,
    state   TEXT,                   -- a feature request tracked directly: its State, or '-'
    became  TEXT                    -- the MT-### it became, or '-'
);

CREATE TABLE IF NOT EXISTS note (
    name    TEXT PRIMARY KEY,
    text    TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS comment_by_tag ON comment (tag, seq);
CREATE INDEX IF NOT EXISTS test_by_disposition ON test (disposition);
"""

# The tables `migrate` replaces: the markdown-era cache, and its own from an earlier run.
RECORD_TABLES = ("test", "verdict", "issue", "doc", "comment", "receipt", "note", "meta")


# --------------------------------------------------------------------------------------------
# what a comment is
# --------------------------------------------------------------------------------------------

# One place, once.  The shape it matches is the one the triage app writes:
#
#     **Adam, 2026-08-25 (triage).** Works.
#     **Claude, 2026-08-25.** Fixed, and my first attempt was ...
#
# Every part after the name is optional because entries predate the convention and Adam types verdicts by hand.  What is
# NOT optional is the '**' either side of the name, and requiring a space after '(triage).' - which is an asterisk in
# the text - is precisely the accident that made an audit report twenty-nine non-passing verdicts as none at all.
VERDICT_RE = re.compile(
    r"\*\*(Adam|Claude),\s*(\d{4}-\d\d-\d\d)?[^*\n]*?\*\*[ \t]*([^\n]*)\n(.*?)(?=\n\*\*|\n\*Run against|\Z)",
    re.S,
)


# The four things a triage run can conclude.  Anything else under an '**Adam, ...**' marker is prose - him explaining a
# decision, or answering a question - and calling that a verdict is how a count goes wrong quietly.
VERDICTS = {
    "works": "Works",
    "works, with notes": "Works, with notes",
    "does not work": "Does not work",
    "could not run this": "Could not run this",
}


def classify(text):
    """Whether a line under an Adam marker is one of the four verdicts.

    :param text: the text on the marker's own line
    :return: the canonical verdict, or None when this is prose rather than a result
    """

    return VERDICTS.get(text.strip().rstrip(".").strip().lower())


def verdicts_in(block):
    """Every Adam or Claude marker in some text, oldest first, each classified.

    :param block: the text
    :return: list of (who, date, kind, verdict, note), kind 'verdict' for one of the four results and 'note' otherwise
    """

    out = []

    for who, date, text, rest in VERDICT_RE.findall(block):
        verdict = classify(text)

        note = " ".join(rest.split())

        if verdict is None:
            # Not a result.  The line itself is the start of what was said, so it belongs in the note.
            out.append((who, date or "", "note", None, " ".join((text.strip() + " " + note).split())))
        else:
            out.append((who, date or "", "verdict", verdict, note))

    return out


def split_entry(block):
    """One test's text, cut where its comments start and where the rule that closes it is.

    Each comment runs from its marker to the next marker, or to the closing rule - the last line of three dashes, which
    is where every entry ends.  The pieces joined are the text again, byte for byte, whatever the entry's shape: some
    have a `#### Comments` heading and some do not, some carry a marker inside their steps, and some have no blank line
    before the rule.

    :param block: the entry as written, its anchor line to the end of its rule
    :return: (body, [comment texts], tail)
    """

    rule = block.rfind("\n---")

    cut = rule + 1 if rule >= 0 else len(block)

    starts = [m.start() for m in VERDICT_RE.finditer(block[:cut])]

    body = block[:starts[0]] if starts else block[:cut]

    comments = [block[a:b] for a, b in zip(starts, starts[1:] + [cut])]

    return body, comments, block[cut:]


def comment_fields(text):
    """Who said a comment, when, and whether it is a verdict - read out of its text.

    :param text: the comment as written, beginning with its marker
    :return: (who, date, kind, verdict, note)
    """

    found = verdicts_in(text)

    if not found:
        raise ValueError("a comment begins with a '**Adam, YYYY-MM-DD.**' or '**Claude, YYYY-MM-DD.**' marker, and this"
                         " does not: %r" % text[:80])

    return found[0]


# --------------------------------------------------------------------------------------------
# opening the store, and writing to it
# --------------------------------------------------------------------------------------------

class NotMigrated(IOError):
    """The store holds no record yet: `migrate` has not been run on it."""


class MarkdownGone(IOError):
    """`migrate` was asked to run and the markdown it imports is not there."""


class DumpNotWritten(IOError):
    """A write reached the store and something that follows it - the text dump, or a count a reference document
    quotes - could not be written.  THE WRITE STANDS: a caller that retried it would make it twice.  `result` is what
    the write returned - the ref filed, the comment written."""

    def __init__(self, message, result=None):
        IOError.__init__(self, message)

        self.result = result


def connect(path=DB_FILE, timeout=BUSY_SECONDS):
    """Opens the store, creating its tables if they are not there.

    :param path: the database file, or ":memory:"
    :param timeout: how long to wait for another process's write, in seconds
    :return: a connection with row access by name
    """

    conn = sqlite3.connect(path, timeout=timeout)
    conn.row_factory = sqlite3.Row
    conn.executescript(FINDING_SCHEMA + RECORD_SCHEMA)

    add_later_columns(conn)

    return conn


# Columns added to a table after it first shipped. `CREATE TABLE IF NOT EXISTS` will not add them to a store that
# already exists, and this one is tracked in git, so every checkout has an old copy.
LATER_COLUMNS = [
    ("finding", "status", "TEXT"),
    ("finding", "status_note", "TEXT"),
]


def add_later_columns(conn):
    """Adds any column that the schema has gained since a store was created.

    :param conn: an open connection
    :return: the columns added, for the caller that wants to say so
    """
    added = []

    for table, column, kind in LATER_COLUMNS:
        have = [r[1] for r in conn.execute("PRAGMA table_info(%s)" % table)]

        if column not in have:
            conn.execute("ALTER TABLE %s ADD COLUMN %s %s" % (table, column, kind))

            added.append("%s.%s" % (table, column))

    if added:
        conn.commit()

    return added


@contextlib.contextmanager
def writing(conn):
    """One write, as one transaction that holds the store's write lock from its first read.

    BEGIN IMMEDIATE, not the BEGIN Python would issue: a write that reads first - the next comment number, the next
    OB - must not read while another process is between its own read and write, or both take the same number, and with
    a deferred BEGIN the second to write is refused as a deadlock rather than queued.  Inside a write already begun,
    this joins it.

    :param conn: an open connection
    """

    if conn.in_transaction:
        yield conn
        return

    conn.execute("BEGIN IMMEDIATE")

    try:
        yield conn
    except BaseException:
        conn.rollback()
        raise

    conn.commit()


def db_path(conn):
    """The file a connection's store is in.

    :param conn: an open connection
    :return: its path, or None for a store in memory
    """

    for row in conn.execute("PRAGMA database_list"):
        if row[1] == "main":
            return row[2] or None

    return None


def is_migrated(conn):
    """Whether this store holds the record - `migrate` has been run on it.

    :param conn: an open connection
    :return: true when it has
    """

    try:
        return conn.execute("SELECT 1 FROM meta WHERE key = 'migrated'").fetchone() is not None
    except sqlite3.OperationalError:
        return False


def is_migrated_file(path=DB_FILE):
    """Whether the store in a file has been migrated, asked without writing a byte of it.

    `connect` creates any table the schema has that the store lacks, which is a write; this opens the file read-only.

    :param path: the database file
    :return: true when it has been migrated; false when it has not, or is not there
    """

    if not os.path.exists(path):
        return False

    conn = sqlite3.connect("file:" + os.path.abspath(path).replace("\\", "/") + "?mode=ro", uri=True)

    try:
        return is_migrated(conn)
    finally:
        conn.close()


def require_record(conn):
    """Refuses, with what to do, when the store has not been migrated.

    :param conn: an open connection
    """

    if not is_migrated(conn):
        raise NotMigrated("%s holds no record yet: it has not been migrated from the markdown.  Run"
                          " `py -3 docs/manual-tests/triagedb.py migrate` with the triage app closed"
                          % (db_path(conn) or "this store"))


def data_version(conn):
    """A number that changes whenever ANOTHER connection commits to the store - the app's cue to re-read it.

    :param conn: an open connection
    :return: the number
    """

    return conn.execute("PRAGMA data_version").fetchone()[0]


def _after_write(conn, result=None):
    """Brings the text dump up to date after a write, under the write lock so two processes' dumps cannot cross.

    The whole dump is rendered - a tenth of a second - and only the files whose bytes differ are written, so a write
    whose dump could not follow it, through a full disk or a file held open, is caught up by the next.

    :param conn: the connection that wrote
    :param result: what the write returns, carried by the DumpNotWritten raised when the dump cannot follow it
    :return: the files written
    """

    if not db_path(conn) or not is_migrated(conn):
        return []

    try:
        with writing(conn):
            return write_dump(conn)
    except (IOError, OSError, sqlite3.Error) as bad:
        raise DumpNotWritten("the change is in the store, but the text dump was not written (%s).  The next write"
                             " writes it, or run `py -3 docs/manual-tests/triagedb.py dump`" % bad, result)


# --------------------------------------------------------------------------------------------
# the review findings
# --------------------------------------------------------------------------------------------

def load_findings(conn, rows, force=False):
    """Replaces the finding catalogue with what the scanner found.

    Adam, 2026-09-08, ruling on MON-C11 - a review folder of 143 documents that could no longer say who needed to do
    what: *"update our MT triage database to catalog each review item.  Now, no reference will ever be stale or lost,
    but useless prose will go away... Expand the database to capture the line number/commit ID/filename of each
    issue."*

    :param conn: an open connection
    :param rows: dicts from `docs/tools/catalog-findings.py`
    :param force: True only to rebuild deliberately
    :return: how many were stored
    """
    # A LOAD MUST NOT BE ABLE TO EMPTY THIS TABLE. Once docs/reviews/ is deleted the scanner finds nothing, and a
    # wholesale replace would take the only remaining copy of thousands of findings with it.  Below half is a collapse,
    # not an edit.
    held = conn.execute("SELECT COUNT(*) FROM finding").fetchone()[0]

    if held and not force and len(rows) * 2 < held:
        raise ValueError(
            "refusing to load %d findings over the %d already stored: that is a collapse, not an "
            "update, and it would destroy the record. If the review documents have been deleted, the "
            "database IS the record now - do not run the scanner against an empty folder. Pass "
            "force=True only to rebuild deliberately." % (len(rows), held))

    # AND IT MUST NOT LOSE A DOCUMENT.  A wholesale replace drops the rows of every document the new rows do not
    # name - a deleted round - even when the new rows are many.  Rounds are added with `add_findings`.
    lost = sorted(set(r[0] for r in conn.execute("SELECT DISTINCT document FROM finding"))
                  - set(r.get("document") for r in rows))

    if lost and not force:
        raise ValueError(
            "refusing to load over %d documents the new rows do not make (%s ...): their rows would be dropped. "
            "A round joins the store with add_findings. Pass force=True only to rebuild deliberately."
            % (len(lost), ", ".join(lost[:3])))

    with writing(conn):
        # Statuses are ours and the documents know nothing about them, so they are carried over rather than
        # rewritten: the disposition column is what a document SAID.
        kept = {(r["ref"], r["document"]): (r["status"], r["status_note"])
                for r in conn.execute("SELECT ref, document, status, status_note FROM finding")}

        conn.execute("DELETE FROM finding")

        for r in rows:
            _put_finding(conn, r, kept.get((r.get("ref"), r.get("document")), (None, None)))

    _after_write(conn, len(rows))

    return len(rows)


def _put_finding(conn, r, status):
    """One finding row, from a scanner dict.

    :param conn: an open connection, in a write
    :param r: the scanner's dict
    :param status: (status, status_note) to keep
    """

    conn.execute(
        "INSERT OR REPLACE INTO finding"
        " (ref, document, line, severity, title, disposition, evidence, commit_id, cited_by,"
        "  status, status_note)"
        " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        (r.get("ref"), r.get("document"), r.get("line") or None, r.get("severity"),
         r.get("what"), r.get("disposition"), r.get("where") or None,
         r.get("commit") or None,
         ",".join(sorted(r.get("cited", []))) or None)
        + tuple(status))


def add_findings(conn, rows):
    """Merges findings into the catalogue without touching the rest of it.

    A row is identified by (ref, document), so re-running over the same review updates its findings in place rather than
    doubling them.  Statuses are kept for rows that already exist.

    :param conn: an open connection
    :param rows: dicts from `docs/tools/catalog-findings.py`
    :return: (added, updated)
    """
    added = 0
    updated = 0

    with writing(conn):
        for r in rows:
            held = conn.execute("SELECT status, status_note FROM finding WHERE ref = ? AND document = ?",
                                (r.get("ref"), r.get("document"))).fetchone()

            if held is None:
                added += 1
            else:
                updated += 1

            _put_finding(conn, r, tuple(held) if held is not None else (None, None))

    _after_write(conn, (added, updated))

    return added, updated


def prune_findings(conn, rows):
    """Deletes catalogued rows for these documents that the documents no longer make (NSV-B1).

    Re-adding a folder is AUTHORITATIVE for the documents in it; a document not in `rows` keeps every row it has.
    **This deletes our own answers along with the row**, so every removal is returned for the caller to print.

    :param conn: an open connection
    :param rows: the freshly parsed rows, from `docs/tools/catalog-findings.py`
    :return: the (document, ref) pairs removed
    """
    current = {}

    for r in rows:
        current.setdefault(r.get("document"), set()).add(r.get("ref"))

    removed = []

    with writing(conn):
        for document, refs in current.items():
            for row in conn.execute("SELECT ref FROM finding WHERE document = ?", (document,)).fetchall():
                if row[0] not in refs:
                    conn.execute("DELETE FROM finding WHERE ref = ? AND document = ?", (row[0], document))

                    removed.append((document, row[0]))

    _after_write(conn, removed)

    return removed


def load_dead(conn, orphans):
    """Stores the ids that are cited from the code and are findings nowhere.

    :param conn: an open connection
    :param orphans: {ref: iterable of file names}
    :return: how many
    """

    with writing(conn):
        conn.execute("DELETE FROM dead_citation")

        for ref in sorted(orphans):
            conn.execute("INSERT OR REPLACE INTO dead_citation (ref, cited_by) VALUES (?, ?)",
                         (ref, ",".join(sorted(orphans[ref])) or None))

    _after_write(conn, len(orphans))

    return len(orphans)


def close_findings(conn, note, severity=None, only_open=True):
    """Sets a status on findings, without touching what the documents said.

    Adam, 2026-09-08: *"in the database, mark all of them as closed or no longer relevant."*  The disposition column is
    a quotation - what a document said on the day it was written - and is left as it was; the status is the answer.

    :param conn: an open connection
    :param note: why, and on what evidence - this is the whole value of the row
    :param severity: restrict to one severity, or None for all
    :param only_open: only rows whose stated disposition reads open
    :return: how many were marked
    """
    where = ["1=1"]
    args = []

    if severity:
        where.append("severity = ?")
        args.append(severity)

    if only_open:
        where.append("(disposition IS NULL OR LOWER(disposition) LIKE '%open%' OR disposition = '-')")

    with writing(conn):
        n = conn.execute("UPDATE finding SET status = 'Closed', status_note = ? WHERE " + " AND ".join(where),
                         [note] + args).rowcount

    _after_write(conn, n)

    return n


def render_findings(conn, path=FINDINGS_MIRROR, force=False):
    """Writes the plain-text mirror of the catalogue, from the store.

    `testEveryCitationResolves` resolves every citation in the codebase against it - there is no SQLite driver on this
    project's classpath - and a person who has just found `RC-A1` in a comment wants one grep, not a query.  Rendering
    the repository's own mirror also rewrites the counts the reference documents quote of it.

    :param conn: an open connection
    :param path: where to write it
    :param force: write even over a mirror that holds ten times as many rows
    :return: how many findings were written
    """
    out = ["# Every review finding. Rendered from docs/manual-tests/triage.db - do not hand-edit.",
           "# Regenerate: python -c \"import triagedb; triagedb.render_findings(triagedb.connect())\"",
           "#",
           "# ref<TAB>document<TAB>status<TAB>disposition-as-stated<TAB>what it was about",
           "#",
           "# The DOCUMENTS ARE GONE - 143 deleted 2026-09-08 and the last 65 on 2026-09-21, and in git",
           "# history before that. This file and the database are what is left of them, and the last",
           "# column is here so that this file is enough on its own: somebody who has just found a",
           "# citation in a comment should not need a SQLite driver to find out what it referred to.",
           "#",
           "# `disposition` is what a document said ON THE DAY IT WAS WRITTEN, and nothing ever updated",
           "# one: of 63 A-severity findings still reading `open`, 13 were checked against the code and",
           "# all 13 had been fixed. `status` is the answer to that, set deliberately. Read a row to find",
           "# out WHAT a citation referred to - never whether it is still true. For that, read the code.",
           "#",
           "# Refs below the DEAD marker are cited from the code and are findings in no document."]

    n = 0

    for r in conn.execute("SELECT ref, document, status, disposition, title FROM finding"
                          " ORDER BY ref, document"):
        out.append("%s\t%s\t%s\t%s\t%s" % (
            r["ref"], r["document"], r["status"] or "-",
            " ".join((r["disposition"] or "-").split())[:90],
            " ".join((r["title"] or "-").split())[:150]))

        n += 1

    # IT WILL NOT EMPTY A FULL MIRROR (VD15-T3, VD17-C3).  A render from the wrong store - one with no findings, or a
    # handful - looks like a collapse rather than a change: an ordinary round adds tens of rows to thousands.
    if os.path.exists(path):
        held = 0

        for line in io.open(path, encoding="utf-8"):
            if line.strip() and not line.startswith("#"):
                held += 1

        if held > 0 and n * 10 < held and not force:
            raise IOError(
                "REFUSING to write %d rows over %d. A collapse like that is not an edit, so this is"
                " not the store the mirror comes from.  Render from triagedb.connect(), or pass"
                " force=True if you mean it." % (n, held))

    out.append("# DEAD - cited, no finding behind them")

    for r in conn.execute("SELECT ref FROM dead_citation ORDER BY ref"):
        out.append("%s\t-\t-\t-\t-" % r["ref"])

    out.append("")

    io.open(path, "w", encoding="utf-8", newline="\n").write("\n".join(out))

    # Only the repository's own mirror: a render to a temporary file is `verify_findings_mirror` asking a question,
    # and answering it must not rewrite a document.
    if os.path.abspath(path) == os.path.abspath(FINDINGS_MIRROR):
        render_finding_counts(conn)

    return n


def holdsFindings(conn):
    """Whether this connection is a store the catalogue lives in.

    :param conn: an open connection
    :return: true when it has findings in it
    """
    return conn.execute("SELECT COUNT(*) FROM finding").fetchone()[0] > 0


def verify_findings_mirror(conn=None, path=FINDINGS_MIRROR):
    """Whether the plain-text mirror on disk is what the store would render right now (VD15-T3).

    :param conn: an open connection, or None for the repository's store
    :param path: the mirror
    :return: (ok, message)
    """
    import tempfile

    ours = conn is None

    if ours:
        conn = connect()

    try:
        if conn.execute("SELECT COUNT(*) FROM finding").fetchone()[0] == 0:
            return True, "this store holds no findings, so the mirror is not rendered from it"

        handle, temp = tempfile.mkstemp(suffix=".tsv", prefix="mirror-")

        os.close(handle)

        try:
            render_findings(conn, temp)

            fresh = io.open(temp, "rb").read()
        finally:
            os.remove(temp)

        if not os.path.exists(path):
            return False, path + " is not there at all, and it is what Java reads the catalogue from"

        # Line endings aside, on both sides: git may check the file out with CRLF, and the render may write either.
        if io.open(path, "rb").read().replace(b"\r\n", b"\n") == fresh.replace(b"\r\n", b"\n"):
            return True, "the mirror is the store's own rows"

        return False, ("%s is not what the store renders - regenerate it with"
                       " triagedb.render_findings(triagedb.connect())" % path)
    finally:
        # ONLY WHAT WE OPENED, AND ALWAYS (VD16-C3).
        if ours:
            conn.close()


def findings(conn, ref=None, disposition=None, severity=None, cited=None):
    """The catalogue, filtered.

    :param conn: an open connection
    :param ref: one citation, exactly
    :param disposition: a substring - "Open", "Fixed"
    :param severity: A, B, C or D
    :param cited: True for only those something in src/ or test/ names
    :return: rows
    """
    where = []
    args = []

    if ref:
        where.append("ref = ?")
        args.append(ref)

    if disposition:
        where.append("disposition LIKE ?")
        args.append("%" + disposition + "%")

    if severity:
        where.append("severity = ?")
        args.append(severity)

    if cited:
        where.append("cited_by IS NOT NULL")

    sql = "SELECT ref, document, line, severity, title, disposition, evidence, commit_id, cited_by FROM finding"

    if where:
        sql += " WHERE " + " AND ".join(where)

    sql += " ORDER BY ref"

    return [dict(r) for r in conn.execute(sql, args)]


# --------------------------------------------------------------------------------------------
# the counts the reference documents quote
# --------------------------------------------------------------------------------------------

# Three sentences quote what the record holds, and `testTheRecordsCountTheStore` reads them as these do.  One group
# per value.  A space inside a phrase may be a line break in the file: behaviour.md wraps its sentence.
ROWS_QUOTE_RE = re.compile(r"(?<![\d,])(\d[\d,]*)\s+rows\s+for\s+(\d[\d,]*)\s+findings")
ROWS_NOW_RE = re.compile(r"(?<![\d,])(\d[\d,]*) finding rows in the store now")
INBOX_QUOTE_RE = re.compile(
    r"Today it holds (\d+) entries - (\d+) OB and (\d+) FR,\s+recounted\s+from\s+the\s+store\s+on\s+"
    r"(\d{4}-\d{2}-\d{2})\s+\(((?:OB|FR)-\d+)\s+the\s+newest\)")


def _requote(path, pattern, form, choose):
    """Rewrites the values one quoted sentence carries, and only when one of them is wrong.

    :param path: the document
    :param pattern: finds the sentence, one group per value it quotes
    :param form: how the sentence reads, for the error
    :param choose: takes the values now quoted, returns the values to quote - the same ones when they are right
    :return: true when the file was rewritten
    """

    with io.open(path, "rb") as fh:
        text = fh.read().decode("utf-8")

    found = pattern.search(text)

    if found is None:
        raise IOError("%s has no sentence in the form %s, so the counts it quotes cannot be rewritten."
                      " Restore the form, or change the pattern in triagedb.py"
                      % (os.path.basename(path), form))

    now = list(found.groups())
    want = choose(now)

    if [v.replace(",", "") for v in want] == [v.replace(",", "") for v in now]:
        return False

    # From the last value back, so each span is still where the match found it.
    for at in range(len(want), 0, -1):
        text = text[:found.start(at)] + want[at - 1] + text[found.end(at):]

    # crlf=False: the text keeps its own line endings, and none is added.
    triage.write_text(path, text, False)

    return True


def render_finding_counts(conn, behaviour_path=BEHAVIOUR_FILE, questions_path=QUESTIONS_FILE):
    """Rewrites the finding counts the reference documents quote, from the store.

    A finding written up in two documents has a row for each, so rows and findings differ (VD15-T5).

    :param conn: an open connection to the store the findings live in
    :param behaviour_path: behaviour.md
    :param questions_path: open-questions.md
    :return: the documents it rewrote, none when both were right
    """

    rows, findings_ = conn.execute("SELECT COUNT(*), COUNT(DISTINCT ref) FROM finding").fetchone()

    if rows == 0:
        raise IOError("REFUSING to quote 0 finding rows. This is not the store the findings live in -"
                      " render from triagedb.connect().")

    changed = []

    if _requote(behaviour_path, ROWS_QUOTE_RE, '"N rows for M findings"',
                lambda now: [format(rows, ","), format(findings_, ",")]):
        changed.append(behaviour_path)

    if _requote(questions_path, ROWS_NOW_RE, '"N finding rows in the store now"',
                lambda now: [format(rows, ",")]):
        changed.append(questions_path)

    return changed


def inbox_counts(conn):
    """What the Inbox holds, counted as `testTheRecordsCountTheStore` counts it from the dump.

    :param conn: an open connection
    :return: (OB entries, FR entries, the newest entry's reference)
    """

    refs = [r[0] for r in conn.execute("SELECT ref FROM issue WHERE inbox = 1 ORDER BY ordinal")]

    if not refs:
        raise IOError("the Inbox holds no entries, so there is no newest one to name")

    return (sum(1 for ref in refs if ref.startswith("OB-")),
            sum(1 for ref in refs if ref.startswith("FR-")),
            refs[-1])


def render_inbox_count(conn, questions_path=QUESTIONS_FILE):
    """Rewrites the Inbox count `open-questions.md` quotes, from the store.

    The sentence carries the day it was recounted, so it gets today's date when a number or the newest entry changes
    and keeps its date when they are all right.

    :param conn: an open connection
    :param questions_path: open-questions.md
    :return: the documents it rewrote, none when the count was right
    """

    obs, frs, newest = inbox_counts(conn)

    def choose(now):
        want = [str(obs + frs), str(obs), str(frs), now[3], newest]

        if want != now:
            want[3] = datetime.date.today().isoformat()

        return want

    if _requote(questions_path, INBOX_QUOTE_RE,
                '"Today it holds T entries - X OB and Y FR, recounted from the store on YYYY-MM-DD'
                ' (REF the newest)"', choose):
        return [questions_path]

    return []


def render_counts(conn, behaviour_path=BEHAVIOUR_FILE, questions_path=QUESTIONS_FILE):
    """Rewrites every count the reference documents quote, so none is written by hand.

    :param conn: an open connection to the repository's store
    :param behaviour_path: behaviour.md
    :param questions_path: open-questions.md
    :return: the documents it rewrote
    """

    changed = render_finding_counts(conn, behaviour_path, questions_path)

    for path in render_inbox_count(conn, questions_path):
        if path not in changed:
            changed.append(path)

    return changed


def _is_the_repositorys(conn):
    """Whether a connection is to the repository's own store, whose counts the reference documents quote.

    :param conn: an open connection
    :return: true for docs/manual-tests/triage.db
    """

    path = db_path(conn)

    return bool(path) and os.path.normcase(os.path.abspath(path)) == os.path.normcase(os.path.abspath(DB_FILE))


# --------------------------------------------------------------------------------------------
# reading the record
# --------------------------------------------------------------------------------------------

def _tag_order(tag):
    """Sort key putting MT-9 before MT-10, which a string sort does not.

    :param tag: MT-###, OB-###, FR-###
    :return: (prefix, number)
    """

    match = re.match(r"([A-Za-z]+)-0*(\d+)$", tag or "")

    return (match.group(1).upper(), int(match.group(2))) if match else (tag or "", 0)


def _comment_texts(conn, ref):
    """The comments on one test or issue, as written, oldest first."""

    return [r[0] for r in conn.execute("SELECT text FROM comment WHERE tag = ? ORDER BY seq", (ref,))]


def entry_text(conn, tag):
    """One test as it was written: its body, its comments and its rule, joined.

    :param conn: an open connection
    :param tag: MT-###
    :return: the text, or None when there is no such test
    """

    row = conn.execute("SELECT body, tail FROM test WHERE tag = ?", (tag.upper(),)).fetchone()

    if row is None:
        return None

    return row["body"] + "".join(_comment_texts(conn, tag.upper())) + row["tail"]


def issue_text(conn, ref):
    """One Inbox item as it was filed, with anything said on it since.

    :param conn: an open connection
    :param ref: OB-### or FR-###
    :return: the text, or None when it was never an Inbox item
    """

    row = conn.execute("SELECT text FROM issue WHERE ref = ?", (ref.upper(),)).fetchone()

    if row is None:
        return None

    return row["text"] + "".join(_comment_texts(conn, ref.upper()))


def receipt_line(row):
    """A receipt as the line of the receipt table it was: `| Filed | Ref | Kind | What | State | Became |`.

    :param row: a receipt row or dict
    :return: the line
    """

    return "| " + " | ".join((row[k] or "").replace("|", "\\|") for k in RECEIPT_CELLS) + " |"


RECEIPT_CELLS = ("filed", "ref", "kind", "what", "state", "became")


def entries(conn):
    """Every test, as the triage app lists them: in the order written, each a `triage.Entry` of its whole text.

    :param conn: an open connection
    :return: list of triage.Entry
    """

    said = {}

    for r in conn.execute("SELECT tag, text FROM comment WHERE tag LIKE 'MT-%' ORDER BY tag, seq"):
        said.setdefault(r["tag"], []).append(r["text"])

    return [triage.Entry(r["anchor"], r["body"] + "".join(said.get(r["tag"], [])) + r["tail"])
            for r in conn.execute("SELECT tag, anchor, body, tail FROM test ORDER BY ordinal")]


def inbox_items(conn, everything=False):
    """The items in the Inbox, each a `triage.IssueItem`, in the order filed.

    :param conn: an open connection
    :param everything: True to include the items cleared out of it
    :return: list of triage.IssueItem, each with `inbox` and `receipted` set
    """

    receipted = set(r[0] for r in conn.execute("SELECT ref FROM receipt"))

    out = []

    for r in conn.execute("SELECT ref, title, filed, inbox FROM issue %s ORDER BY ordinal"
                          % ("" if everything else "WHERE inbox = 1")):
        item = triage.IssueItem(r["ref"], r["filed"] or "", r["title"] or "", issue_text(conn, r["ref"]))

        item.inbox = bool(r["inbox"])
        item.receipted = r["ref"] in receipted

        out.append(item)

    return out


def receipts(conn):
    """The receipt table, newest first, as dicts keyed by its columns - with `became_tags`, every MT-### Became names.

    :param conn: an open connection
    :return: list of dicts
    """

    out = []

    for r in conn.execute("SELECT * FROM receipt ORDER BY seq DESC"):
        row = dict((k, r[k] or "") for k in RECEIPT_CELLS)

        row["seq"] = r["seq"]
        row["became_tags"] = re.findall(r"MT-\d+", row["became"])
        row["became_tag"] = row["became_tags"][0] if row["became_tags"] else None

        out.append(row)

    return out


def note(conn, name):
    """One piece of the prose the markdown carried around its entries.

    :param conn: an open connection
    :param name: e.g. "issues.preamble"
    :return: its text, or "" when there is none of that name
    """

    row = conn.execute("SELECT text FROM note WHERE name = ?", (name,)).fetchone()

    return row[0] if row else ""


def check(conn, disposition=None, verdict=None, triaged=None, reopened=None, tag=None):
    """Tests matching whatever is asked, each with its latest verdict.

    :param disposition: 'needs test', 'fixed unvalidated', 'fixed validated', 'superseded'
    :param verdict: one of VERDICTS' values, matched against the LATEST Adam verdict
    :param triaged: True for entries Adam has ruled on, False for those he never has
    :param reopened: True for entries he ruled on that have changed since
    :param tag: one specific MT-###
    :return: list of dicts
    """

    sql = ["SELECT t.tag, t.title, t.disposition, t.reopened,"
           " (SELECT c.verdict FROM comment c WHERE c.tag = t.tag AND c.who = 'Adam'"
           "  AND c.kind = 'verdict' ORDER BY c.seq DESC LIMIT 1) AS latest,"
           " (SELECT c.date FROM comment c WHERE c.tag = t.tag AND c.who = 'Adam'"
           "  AND c.kind = 'verdict' ORDER BY c.seq DESC LIMIT 1) AS latest_date"
           " FROM test t WHERE 1 = 1"]

    args = []

    if tag:
        sql.append(" AND t.tag = ?")
        args.append(tag.upper())

    if disposition:
        sql.append(" AND t.disposition = ?")
        args.append(disposition)

    if reopened is not None:
        sql.append(" AND t.reopened = ?")
        args.append(1 if reopened else 0)

    if triaged is True:
        sql.append(" AND EXISTS (SELECT 1 FROM comment c WHERE c.tag = t.tag AND c.who = 'Adam' AND c.kind = 'verdict')")

    if triaged is False:
        sql.append(" AND NOT EXISTS (SELECT 1 FROM comment c WHERE c.tag = t.tag AND c.who = 'Adam'"
                   " AND c.kind = 'verdict')")

    sql.append(" ORDER BY t.ordinal")

    rows = [dict(r) for r in conn.execute("".join(sql), args)]

    if verdict:
        rows = [r for r in rows if (r["latest"] or "") == verdict]

    return rows


def stale_queue(conn):
    """Entries sitting in Adam's queue that he has already passed.

    An entry he judged `Works`, that nothing has reopened, and that still reads `needs test` or `fixed unvalidated`:
    he has run it, it passed, and it is taking a line in the list of things he has not run.

    :param conn: an open connection
    :return: the rows
    """
    passed = ("Works", "Works, with notes")

    return [r for disposition in ("needs test", "fixed unvalidated")
            for r in check(conn, disposition=disposition, triaged=True, reopened=False)
            if (r["latest"] or "") in passed]


def history(conn, tag):
    """Everything said about one test or issue, oldest first.

    :param conn: an open connection
    :param tag: MT-###, OB-### or FR-###
    :return: list of dicts
    """

    return [dict(r) for r in conn.execute(
        "SELECT seq, who, date, kind, verdict, note FROM comment WHERE tag = ? ORDER BY seq", (tag.upper(),))]


def issues(conn, kind=None):
    """The items in the Inbox that have not been picked up: no receipt yet.

    :param conn: an open connection
    :param kind: 'bug' or 'feature request', or None for both
    :return: list of dicts
    """

    sql = ("SELECT ref, ordinal, kind, title, filed, filed_at, raised_from, build, text FROM issue"
           " WHERE inbox = 1 AND ref NOT IN (SELECT ref FROM receipt)")

    if kind:
        rows = conn.execute(sql + " AND kind = ? ORDER BY ordinal", (kind,))
    else:
        rows = conn.execute(sql + " ORDER BY ordinal")

    return [dict(r) for r in rows]


def ledger(conn):
    """Everything still asking Adam for something - needs test and fixed unvalidated - in tag order.

    It was a table in tests.md that had to be regenerated; it is a query now, and cannot drift.

    :param conn: an open connection
    :return: list of dicts: tag, date, title, disposition, origin
    """

    rows = [dict(r) for r in conn.execute("SELECT tag, date, title, disposition, origin FROM test")]

    return sorted((r for r in rows if (r["disposition"] or "").strip().lower()
                   not in (triage.VALIDATED, triage.SUPERSEDED)), key=lambda r: _tag_order(r["tag"]))


def show(conn, ref):
    """One test, OB or FR in full: its text exactly as written, then what the store reads out of it.

    :param conn: an open connection
    :param ref: MT-###, OB-### or FR-###
    :return: the text to print, or None when nothing has that tag
    """

    ref = ref.strip().upper()

    if ref.startswith("MT-"):
        text = entry_text(conn, ref)

        if text is None:
            return None

        row = conn.execute("SELECT disposition, reopened FROM test WHERE tag = ?", (ref,)).fetchone()

        said = history(conn, ref)

        lines = ["==== %s in the store: %s%s; %d comment%s" % (
            ref, row["disposition"], ", reopened" if row["reopened"] else "", len(said),
            "" if len(said) == 1 else "s")]

        for h in said:
            lines.append("  %-3d %-7s %-7s %-10s %s" % (h["seq"], h["kind"], h["who"], h["date"] or "-",
                                                        h["verdict"] or (h["note"] or "")[:70]))

        return text + "\n".join(lines) + "\n"

    text = issue_text(conn, ref)

    receipt = conn.execute("SELECT * FROM receipt WHERE ref = ?", (ref,)).fetchone()

    if text is None and receipt is None:
        return None

    out = text or ""

    if text is None:
        # Picked up before the store, and cleared out of the Inbox then: its receipt is all that was kept.
        out = receipt_line(receipt) + "\n" + "".join(_comment_texts(conn, ref))

    row = conn.execute("SELECT inbox FROM issue WHERE ref = ?", (ref,)).fetchone()

    if row is None:
        lines = ["==== %s in the store: a receipt - its Inbox item had been cleared out before the store held it" % ref]
    else:
        lines = ["==== %s in the store: %s" % (ref, "in the Inbox" if row["inbox"] else "cleared out of the Inbox"),
                 "  receipt: " + receipt_line(receipt) if receipt is not None else "  not picked up yet"]

    return out + ("" if out.endswith("\n") else "\n") + "\n".join(lines) + "\n"


# --------------------------------------------------------------------------------------------
# writing the record
# --------------------------------------------------------------------------------------------

def _derive_test(conn, tag):
    """Reads a test's fields out of its text again, after the text changed.

    :param conn: an open connection, in a write
    :param tag: MT-###
    """

    row = conn.execute("SELECT anchor FROM test WHERE tag = ?", (tag,)).fetchone()

    entry = triage.Entry(row["anchor"], entry_text(conn, tag))

    conn.execute("UPDATE test SET date = ?, title = ?, disposition = ?, origin = ?, written = ?, reopened = ?"
                 " WHERE tag = ?", (entry.date, entry.title, entry.disposition, entry.origin, entry.written,
                                    1 if entry.reopened else 0, tag))


def _derive_issue(text):
    """An issue's fields, read out of its text.

    :param text: the item as filed
    :return: (kind, title, filed, filed_at, raised_from, build)
    """

    head = triage.ISSUE_ITEM_RE.search(text)

    if head is None:
        raise ValueError("an Inbox item begins '### OB-123 - YYYY-MM-DD - what', and this does not: %r" % text[:80])

    item = triage.IssueItem(head.group(1), head.group(2), head.group(3), text)

    return item.kind, item.summary, item.filed, item.filed_at, item.raised_from, item.build


def _put_comment(conn, ref, seq, text):
    """One comment row, its fields read out of its text.

    :param conn: an open connection, in a write
    :param ref: what it is on
    :param seq: its place, 0 the oldest
    :param text: as written
    """

    who, date, kind, verdict, said = comment_fields(text)

    conn.execute("INSERT INTO comment (tag, seq, text, who, date, kind, verdict, note) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                 (ref, seq, text, who, date, kind, verdict, said))


def _next_seq(conn, ref):
    """The next comment's place on one test or issue.

    :param conn: an open connection, in a write
    :param ref: the tag
    :return: the number
    """

    return conn.execute("SELECT COALESCE(MAX(seq), -1) + 1 FROM comment WHERE tag = ?", (ref,)).fetchone()[0]


def set_disposition(conn, tag, disposition):
    """Changes one test's disposition: the Disposition line of its text, and the field read out of it.

    :param conn: an open connection
    :param tag: MT-###
    :param disposition: the new disposition
    :return: the previous disposition
    """

    require_record(conn)

    # triage.py's own set, derived there from the colour table so the two cannot disagree.
    if disposition not in triage.VALID_DISPOSITIONS:
        raise ValueError("%r is not one of %s" % (disposition, sorted(triage.VALID_DISPOSITIONS)))

    tag = tag.upper()

    with writing(conn):
        row = conn.execute("SELECT body, disposition FROM test WHERE tag = ?", (tag,)).fetchone()

        if row is None:
            raise KeyError("no such test: %s" % tag)

        # The TRAILING whitespace is kept, and it is not cosmetic: two spaces at the end of a line is markdown's hard
        # line break, and these field lines use it to stack.
        body, changed = re.subn(r"(?m)^(\*\*Disposition:\*\*[ \t]*).*?([ \t]*)$",
                                lambda m: m.group(1) + disposition + m.group(2), row["body"], count=1)

        if not changed:
            raise IOError("%s has no Disposition line to change" % tag)

        conn.execute("UPDATE test SET body = ? WHERE tag = ?", (body, tag))

        _derive_test(conn, tag)

    _after_write(conn, row["disposition"])

    return row["disposition"]


def add_comment(conn, tag, who, text, verdict=None):
    """Says something on one test or issue, dated today: `**Who, YYYY-MM-DD.**` and the text under it.

    :param conn: an open connection
    :param tag: MT-###, OB-### or FR-###
    :param who: 'Adam' or 'Claude'
    :param text: what to say
    :param verdict: one of VERDICTS' values, to make this a triage result rather than a note
    :return: the comment as written
    """

    if verdict is not None and verdict not in VERDICTS.values():
        raise ValueError("%r is not one of %s" % (verdict, sorted(VERDICTS.values())))

    if who not in ("Adam", "Claude"):
        raise ValueError("a comment is Adam's or Claude's, not %r" % who)

    stamp = datetime.date.today().isoformat()

    head = "**%s, %s%s.**" % (who, stamp, " (triage)" if verdict else "")

    comment = head + ((" " + verdict + ".") if verdict else "") + "\n\n" + text.strip() + "\n"

    try:
        add_comment_text(conn, tag, comment)
    except DumpNotWritten as bad:
        bad.result = comment
        raise

    return comment


def add_comment_text(conn, ref, comment):
    """Appends one comment, already written out, to a test or an issue.  The triage app's Submit comes through here.

    An INSERT: the entry's own text is not read and written back, so a write that crosses another cannot undo it.  The
    blank line a comment needs before it is part of its own text when what precedes it does not end in one - some
    older entries have none before their rule.

    :param conn: an open connection
    :param ref: MT-###, OB-### or FR-###
    :param comment: the whole comment, from its `**Adam, ...**` or `**Claude, ...**` marker
    :return: the text stored
    """

    require_record(conn)

    ref = ref.strip().upper()

    comment = comment.strip()

    if not VERDICT_RE.match(comment + "\n"):
        raise ValueError("a comment begins with its '**Adam, YYYY-MM-DD.**' or '**Claude, YYYY-MM-DD.**' marker: %r"
                         % comment[:80])

    with writing(conn):
        if ref.startswith("MT-"):
            row = conn.execute("SELECT body FROM test WHERE tag = ?", (ref,)).fetchone()

            if row is None:
                raise KeyError("no such test: %s" % ref)

            before = row["body"]
        else:
            row = conn.execute("SELECT text FROM issue WHERE ref = ?", (ref,)).fetchone()

            if row is None and conn.execute("SELECT 1 FROM receipt WHERE ref = ?", (ref,)).fetchone() is None:
                raise KeyError("no such issue: %s" % ref)

            before = row["text"] if row else "\n\n"

        before += "".join(_comment_texts(conn, ref))

        lead = "" if before.endswith("\n\n") else ("\n" if before.endswith("\n") else "\n\n")

        text = lead + comment + "\n\n"

        _put_comment(conn, ref, _next_seq(conn, ref), text)

        if ref.startswith("MT-"):
            _derive_test(conn, ref)

    _after_write(conn, text)

    return text


def _next_number(conn, prefix):
    """The next free number for OB, FR or MT: one past the highest the record names anywhere.

    Anywhere, not only as a tag: a ref named in a comment or a receipt is taken, whether or not it was filed.

    :param conn: an open connection, in a write
    :param prefix: "OB", "FR" or "MT"
    :return: the number
    """

    pattern = re.compile(r"\b%s-(\d+)\b" % prefix)

    highest = 0

    for sql in ("SELECT tag || ' ' || body || tail FROM test", "SELECT tag || ' ' || text FROM comment",
                "SELECT ref || ' ' || text FROM issue", "SELECT ref || ' ' || COALESCE(what, '') || ' '"
                " || COALESCE(became, '') FROM receipt", "SELECT text FROM note"):
        for (text,) in conn.execute(sql):
            for found in pattern.findall(text or ""):
                highest = max(highest, int(found))

    return highest + 1


def add_test(conn, title, origin, body, disposition="needs test"):
    """Writes a new MT-### entry, numbered after the last, in the shape every recent entry has.

    Entries are append-only (README.md rule 5): after this, only its Disposition and its comments change.

    :param conn: an open connection
    :param title: the one line it is about
    :param origin: where it came from - a finding, an OB or FR, or "hands-on testing" (rule 3)
    :param body: the steps and the expected result, as markdown
    :param disposition: needs test, normally
    :return: its tag
    """

    require_record(conn)

    if disposition not in triage.VALID_DISPOSITIONS:
        raise ValueError("%r is not one of %s" % (disposition, sorted(triage.VALID_DISPOSITIONS)))

    if not title.strip() or not origin.strip() or not body.strip():
        raise ValueError("a test has a title, a From and some steps")

    if re.search(r"^\*\*(Adam|Claude),", body, re.M) or re.search(r'^<a id="mt-', body, re.M):
        raise ValueError("the steps of a new test hold a comment marker or an anchor; a comment is added with"
                         " add_comment, after it is written")

    day = datetime.date.today().isoformat()

    with writing(conn):
        number = max([_tag_order(r[0])[1] for r in conn.execute("SELECT tag FROM test")] + [0]) + 1

        tag = "MT-%03d" % number

        text = ('<a id="%s"></a>\n### %s - %s - %s\n\n**Disposition:** %s\n**From:** %s\n\n**Written:** %s\n\n%s\n\n'
                % (tag.lower(), tag, day, " ".join(title.split()), disposition, " ".join(origin.split()), day,
                   body.strip("\n")))

        ordinal = conn.execute("SELECT COALESCE(MAX(ordinal), -1) + 1 FROM test").fetchone()[0]

        conn.execute("INSERT INTO test (tag, ordinal, anchor, body, tail) VALUES (?, ?, ?, ?, ?)",
                     (tag, ordinal, tag.lower(), text, "---\n\n"))

        _derive_test(conn, tag)

    _after_write(conn, tag)

    return tag


def file_issue(conn, kind, title, detail, raised_from=None, build=None):
    """Files a new OB-### (a bug) or FR-### (a feature request) into the Inbox.

    The number is taken inside the write, so two processes filing at once cannot be handed the same one.

    :param conn: an open connection
    :param kind: 'bug' or 'feature request'
    :param title: the one-line summary
    :param detail: the body
    :param raised_from: what this came from - an MT-###, a finding, a person.  WORTH GIVING: it is what `issues`
                        reports, and 'the triage API' answers 'where did this come from' with 'through a door'
    :param build: the build it was seen on, for one filed while testing
    :return: the reference allocated
    """

    require_record(conn)

    kind, recognised = triage.normalize_kind(kind)

    if not recognised:
        raise ValueError("kind must be 'bug' or 'feature request', not %r" % kind)

    if not title.strip():
        raise ValueError("an issue has a one-line summary")

    now = datetime.datetime.now()

    with writing(conn):
        ref = triage.format_ref(kind, _next_number(conn, triage.ISSUE_PREFIXES[kind]))

        fields = ["**Kind:** %s" % kind,
                  "**Raised from:** %s" % " ".join((raised_from or "the triage API").split()),
                  "**Filed:** %s" % now.strftime("%Y-%m-%d %H:%M")]

        if build:
            fields.append("**Build:** %s" % " ".join(build.split()))

        text = "### %s - %s - %s\n\n%s\n\n%s\n\n" % (ref, now.date().isoformat(), " ".join(title.split()),
                                                    "  \n".join(fields), detail.strip() or "(no further detail)")

        ordinal = conn.execute("SELECT COALESCE(MAX(ordinal), -1) + 1 FROM issue").fetchone()[0]

        conn.execute("INSERT INTO issue (ref, ordinal, text, inbox, kind, title, filed, filed_at, raised_from, build)"
                     " VALUES (?, ?, ?, 1, ?, ?, ?, ?, ?, ?)", (ref, ordinal, text) + _derive_issue(text))

    _after_inbox_write(conn, ref, ref)

    return ref


def add_receipt(conn, ref, what, state="-", became="-", kind=None, filed=None):
    """Picks an item up: a new row in the receipt table.

    A bug names the MT-### it became in `became` and leaves `state` '-'; a feature request tracked directly has a
    State - pending, needs test, fixed unvalidated, fixed validated or declined - and no Became.

    :param conn: an open connection
    :param ref: the OB-### or FR-###
    :param what: one line about what was done
    :param state: its State, or '-'
    :param became: the MT-### tags it became, or '-'
    :param kind: bug or feature request; by default the Inbox item's, or what the ref says
    :param filed: the day it was filed; by default the Inbox item's, or today
    """

    require_record(conn)

    ref = ref.strip().upper()

    _check_state(state)

    with writing(conn):
        if conn.execute("SELECT 1 FROM receipt WHERE ref = ?", (ref,)).fetchone():
            raise ValueError("%s already has a receipt; change it with update_receipt" % ref)

        item = conn.execute("SELECT kind, filed FROM issue WHERE ref = ?", (ref,)).fetchone()

        kind = kind or (item["kind"] if item else ("feature request" if ref.startswith("FR-") else "bug"))
        filed = filed or (item["filed"] if item else datetime.date.today().isoformat())

        seq = conn.execute("SELECT COALESCE(MAX(seq), -1) + 1 FROM receipt").fetchone()[0]

        conn.execute("INSERT INTO receipt (ref, seq, filed, kind, what, state, became) VALUES (?, ?, ?, ?, ?, ?, ?)",
                     (ref, seq, filed, kind, " ".join(what.split()), state or "-", became or "-"))

    _after_write(conn, None)


RECEIPT_STATES = ("-", "pending", "needs test", "fixed unvalidated", "fixed validated", "declined")


def _check_state(state):
    """Refuses a State that is not one of the receipt table's words.

    :param state: the word
    """

    if state is not None and state not in RECEIPT_STATES:
        raise ValueError("%r is not one of %s" % (state, list(RECEIPT_STATES)))


def update_receipt(conn, ref, state=None, became=None, what=None):
    """Changes a receipt's State, Became or What.

    :param conn: an open connection
    :param ref: the OB-### or FR-###
    :param state: its new State, or None to leave it
    :param became: its new Became, or None to leave it
    :param what: its new What, or None to leave it
    :return: the receipt as it was, as a dict
    """

    require_record(conn)

    _check_state(state)

    with writing(conn):
        row = conn.execute("SELECT * FROM receipt WHERE ref = ?", (ref.upper(),)).fetchone()

        if row is None:
            raise KeyError("%s has no receipt" % ref)

        was = dict(row)

        conn.execute("UPDATE receipt SET state = ?, became = ?, what = ? WHERE ref = ?",
                     (state if state is not None else row["state"], became if became is not None else row["became"],
                      " ".join(what.split()) if what is not None else row["what"], ref.upper()))

    _after_write(conn, was)

    return was


def clear_from_inbox(conn, ref):
    """Takes a done item out of the Inbox.  It keeps its row, its text and its receipt; it only stops being listed.

    Adam, 2026-09-24: a done item leaves the Inbox and keeps its receipt - so one with no receipt is refused, because it
    would leave the Inbox with nothing anywhere saying what became of it.

    :param conn: an open connection
    :param ref: the OB-### or FR-###
    """

    require_record(conn)

    ref = ref.strip().upper()

    with writing(conn):
        if conn.execute("SELECT 1 FROM issue WHERE ref = ? AND inbox = 1", (ref,)).fetchone() is None:
            raise KeyError("%s is not in the Inbox" % ref)

        if conn.execute("SELECT 1 FROM receipt WHERE ref = ?", (ref,)).fetchone() is None:
            raise ValueError("%s has no receipt: give it one (add_receipt) before it leaves the Inbox" % ref)

        conn.execute("UPDATE issue SET inbox = 0 WHERE ref = ?", (ref,))

    _after_inbox_write(conn, ref, None)


def _after_inbox_write(conn, ref, result):
    """What follows a write to the Inbox: its dump file, and the count open-questions.md quotes of it - for the
    repository's own store, not a copy.

    Either failing raises DumpNotWritten, whose `result` is the write's: the item IS filed, or cleared, and an error
    that said only what failed would invite doing it again.

    :param conn: the connection that wrote
    :param ref: the item written
    :param result: what the write returns
    """

    problems = []

    try:
        _after_write(conn, result)
    except DumpNotWritten as bad:
        problems.append(str(bad))

    if _is_the_repositorys(conn):
        try:
            render_inbox_count(conn)
        except Exception as bad:
            problems.append("its count in open-questions.md was not updated: %s" % bad)

    if problems:
        raise DumpNotWritten("%s is written to the store, but %s" % (ref, "; and ".join(problems)), result)


# --------------------------------------------------------------------------------------------
# the text dump
# --------------------------------------------------------------------------------------------

DUMP_FILES = ["tests.txt", "comments.txt", "issues.txt", "receipts.txt", "notes.txt", "findings.txt",
              "dead-citations.txt", "meta.txt"]

_ABOUT = {
    "tests.txt": "the `test` table: one record per manual test, in tag order.  Its comments are in comments.txt",
    "comments.txt": "the `comment` table: every comment on a test or an issue, by tag, oldest first",
    "issues.txt": "the `issue` table: every OB and FR filed into the Inbox, in ref order (`inbox: no` once cleared"
                  " out of it)",
    "receipts.txt": "the `receipt` table: what has been picked up, in the order written (the app shows it newest"
                    " first)",
    "notes.txt": "the `note` table: the prose the markdown carried around its entries, by name",
    "findings.txt": "the `finding` table: every review finding, by ref and document",
    "dead-citations.txt": "the `dead_citation` table: refs cited from the code that are findings in no document",
    "meta.txt": "the `meta` table",
}


def _text_field(name, text):
    """A text column as dump lines: its name, then every line behind "| ", so nothing in it can read as a field.

    :param name: the column
    :param text: its value
    :return: lines
    """

    if text is None:
        return [name + ":"]

    ends = text.endswith("\n")

    body = text[:-1] if ends else text

    lines = body.split("\n") if (ends or body) else []

    return [name + (":" if ends else " (no newline at end):")] + [("| " + line) if line else "|" for line in lines]


def _short_field(name, value):
    """A one-line column as a dump line: `name: value`.

    :param name: the column
    :param value: its value
    :return: lines
    """

    if value is None or value == "":
        return [name + ":"]

    value = "%s" % value

    if "\n" in value:
        return _text_field(name, value)

    return ["%s: %s" % (name, value)]


def _yes(value):
    return "yes" if value else "no"


def _records(name, conn):
    """One dump file's records, each a list of lines.

    :param name: the file
    :param conn: an open connection
    :return: list of line lists, in the file's order
    """

    out = []

    if name == "tests.txt":
        for r in sorted(conn.execute("SELECT * FROM test"), key=lambda r: _tag_order(r["tag"])):
            out.append(["[test %s]" % r["tag"]]
                       + _short_field("ordinal", r["ordinal"]) + _short_field("anchor", r["anchor"])
                       + _short_field("date", r["date"]) + _short_field("title", r["title"])
                       + _short_field("disposition", r["disposition"]) + _short_field("from", r["origin"])
                       + _short_field("written", r["written"]) + _short_field("reopened", _yes(r["reopened"]))
                       + _text_field("body", r["body"]) + _text_field("tail", r["tail"]))

    elif name == "comments.txt":
        for r in sorted(conn.execute("SELECT * FROM comment"), key=lambda r: (_tag_order(r["tag"]), r["seq"])):
            out.append(["[comment %s %d]" % (r["tag"], r["seq"])]
                       + _short_field("who", r["who"]) + _short_field("date", r["date"])
                       + _short_field("kind", r["kind"]) + _short_field("verdict", r["verdict"])
                       + _text_field("text", r["text"]))

    elif name == "issues.txt":
        for r in sorted(conn.execute("SELECT * FROM issue"), key=lambda r: _tag_order(r["ref"])):
            out.append(["[issue %s]" % r["ref"]]
                       + _short_field("ordinal", r["ordinal"]) + _short_field("inbox", _yes(r["inbox"]))
                       + _short_field("kind", r["kind"]) + _short_field("title", r["title"])
                       + _short_field("filed", r["filed"]) + _short_field("filed_at", r["filed_at"])
                       + _short_field("raised_from", r["raised_from"]) + _short_field("build", r["build"])
                       + _text_field("text", r["text"]))

    elif name == "receipts.txt":
        for r in conn.execute("SELECT * FROM receipt ORDER BY seq"):
            out.append(["[receipt %s]" % r["ref"]] + _short_field("seq", r["seq"])
                       + [line for k in RECEIPT_CELLS if k != "ref" for line in _short_field(k, r[k])])

    elif name == "notes.txt":
        for r in conn.execute("SELECT * FROM note ORDER BY name"):
            out.append(["[note %s]" % r["name"]] + _text_field("text", r["text"]))

    elif name == "findings.txt":
        for r in conn.execute("SELECT * FROM finding ORDER BY ref, document"):
            out.append(["[finding %s in %s]" % (r["ref"], r["document"])]
                       + [line for k in ("line", "severity", "title", "disposition", "evidence", "commit_id",
                                         "cited_by", "status", "status_note") for line in _short_field(k, r[k])])

    elif name == "dead-citations.txt":
        for r in conn.execute("SELECT * FROM dead_citation ORDER BY ref"):
            out.append(["[dead %s]" % r["ref"]] + _short_field("cited_by", r["cited_by"]))

    elif name == "meta.txt":
        for r in conn.execute("SELECT * FROM meta ORDER BY key"):
            out.append(["[meta %s]" % r["key"]] + _short_field("value", r["value"]))

    else:
        raise KeyError(name)

    return out


def render_dump(conn, files=None):
    """The text dump, as the store says it should be.

    :param conn: an open connection
    :param files: the files to render, or None for all
    :return: [(file name, text)]
    """

    out = []

    for name in (files or DUMP_FILES):
        lines = ["# docs/manual-tests/dump/%s - %s." % (name, _ABOUT[name]),
                 "# WRITTEN BY triagedb.py FROM triage.db ON EVERY WRITE.  Never edit it, and never read it back: ask the",
                 "# store (py -3 docs/manual-tests/triagedb.py show MT-123).  Only the Java tests read it, having no SQLite.",
                 "# A record is a [header] line and one field per line; every line of a text field is written behind \"| \".",
                 ""]

        for record in _records(name, conn):
            lines += record + [""]

        out.append((name, "\n".join(lines)))

    return out


def write_dump(conn, files=None, folder=None):
    """Writes the text dump, each file only when its bytes change, each by an atomic replace.

    :param conn: an open connection
    :param files: the files to write, or None for all
    :param folder: where, or None for `dump/` beside the store's file
    :return: the files written
    """

    if folder is None:
        path = db_path(conn)

        if not path:
            return []

        folder = os.path.join(os.path.dirname(path), DUMP_NAME)

    if not os.path.isdir(folder):
        os.makedirs(folder)

    written = []

    for name, text in render_dump(conn, files):
        path = os.path.join(folder, name)

        data = text.encode("utf-8")

        if os.path.exists(path):
            with io.open(path, "rb") as fh:
                if fh.read().replace(b"\r\n", b"\n") == data:
                    continue

        temp = path + ".tmp"

        try:
            with io.open(temp, "wb") as fh:
                fh.write(data)

            os.replace(temp, path)
        finally:
            if os.path.exists(temp):
                try:
                    os.remove(temp)
                except OSError:
                    pass

        written.append(name)

    return written


def verify_dump(conn, folder=None):
    """Whether the text dump on disk is the store's own rows.

    :param conn: an open connection
    :param folder: the dump, or None for `dump/` beside the store
    :return: (ok, message)
    """

    folder = folder or os.path.join(os.path.dirname(db_path(conn) or DB_FILE), DUMP_NAME)

    stale = []

    for name, text in render_dump(conn):
        path = os.path.join(folder, name)

        if not os.path.exists(path):
            stale.append(name + " (missing)")
            continue

        with io.open(path, "rb") as fh:
            if fh.read().replace(b"\r\n", b"\n") != text.encode("utf-8"):
                stale.append(name)

    if stale:
        return False, ("the dump is not the store's own rows: %s.  Write it again with"
                       " `py -3 docs/manual-tests/triagedb.py dump`" % ", ".join(stale))

    return True, "the dump is the store's own rows (%d files)" % len(DUMP_FILES)


def verify_record(conn):
    """Whether the record holds together: every field is what its text says, and every comment is on something.

    :param conn: an open connection
    :return: (ok, list of problems)
    """

    problems = []

    for r in conn.execute("SELECT * FROM test"):
        text = entry_text(conn, r["tag"])

        entry = triage.Entry(r["anchor"], text)

        if (entry.date, entry.title, entry.disposition, entry.origin, entry.written, 1 if entry.reopened else 0) != \
                (r["date"], r["title"], r["disposition"], r["origin"], r["written"], r["reopened"]):
            problems.append("%s: its fields are not what its text says" % r["tag"])

        if entry.disposition.strip().lower() not in triage.VALID_DISPOSITIONS:
            problems.append("%s: %r is not one of the four dispositions" % (r["tag"], entry.disposition))

        if r["tail"].strip() != "---":
            problems.append("%s: it does not end with its rule" % r["tag"])

    for r in conn.execute("SELECT * FROM comment"):
        if comment_fields(r["text"]) != (r["who"], r["date"], r["kind"], r["verdict"], r["note"]):
            problems.append("%s #%d: its fields are not what its text says" % (r["tag"], r["seq"]))

    for r in conn.execute("SELECT DISTINCT tag FROM comment WHERE tag NOT IN (SELECT tag FROM test)"
                          " AND tag NOT IN (SELECT ref FROM issue) AND tag NOT IN (SELECT ref FROM receipt)"):
        problems.append("%s: comments on nothing the store holds" % r["tag"])

    for r in conn.execute("SELECT * FROM issue"):
        if _derive_issue(r["text"]) != (r["kind"], r["title"], r["filed"], r["filed_at"], r["raised_from"], r["build"]):
            problems.append("%s: its fields are not what its text says" % r["ref"])

    return not problems, problems


# --------------------------------------------------------------------------------------------
# where a finding's status note says its work lives (GSB-C3)
# --------------------------------------------------------------------------------------------

# "Now tracked as OB-253 in the Inbox", "filed as OB-300", "deferred as OB-306": each names an Inbox item, so the OB it
# names has to be an item in the Inbox or one with a receipt.  A mention in passing claims nothing and is not read.
INBOX_CLAIM_RE = re.compile(r"\b(?:[Tt]racked as|[Ii]n the Inbox as|filed as|deferred as)\s+(OB-\d+)\b")


def verify_inbox_claims(store_path=DB_FILE):
    """Whether every OB a finding's status note says it is tracked as is an item in the Inbox or receipted (GSB-C3).

    Read from the file read-only, not through `connect`, which would create a store that is not there.

    :param store_path: the store - the default one, or a copy
    :return: (ok, message, offenders), offenders as (ref, document, OB) for each claim that names no Inbox item
    """
    if not os.path.exists(store_path):
        return False, store_path + " is not there, and the status notes live only in it", []

    conn = sqlite3.connect("file:" + os.path.abspath(store_path).replace("\\", "/") + "?mode=ro", uri=True)

    try:
        rows = conn.execute("SELECT ref, document, status_note FROM finding"
                            " WHERE status_note LIKE '%OB-%' ORDER BY ref, document").fetchall()

        try:
            inbox = set(r[0] for r in conn.execute("SELECT ref FROM issue WHERE inbox = 1"))
            receipted = set(r[0] for r in conn.execute("SELECT ref FROM receipt"))
        except sqlite3.OperationalError:
            return False, store_path + " has not been migrated: it holds no Inbox to check the claims against", []
    finally:
        conn.close()

    claims = 0
    offenders = []

    for ref, document, said in rows:
        for m in INBOX_CLAIM_RE.finditer(said):
            claims += 1

            if m.group(1) not in inbox and m.group(1) not in receipted:
                offenders.append((ref, document, m.group(1)))

    if offenders:
        return False, ("%d of %d status-note claims name an OB neither in the Inbox nor receipted:"
                       % (len(offenders), claims)), offenders

    return True, ("all %d status-note claims name an item in the Inbox or a receipt (%d OB items there)"
                  % (claims, sum(1 for ref in inbox if ref.startswith("OB-")))), []


# --------------------------------------------------------------------------------------------
# THE MIGRATION: the markdown into the store, once
# --------------------------------------------------------------------------------------------

ISSUES_INBOX = "## Inbox"
ISSUES_PICKED = "## What has been picked up"
ISSUES_BACKLOG = "## Where the older backlog is"
RECEIPT_HEADER = "| Filed | Ref | Kind | What | State | Became |"
RECEIPT_RULE = "|---|---|---|---|---|---|"

# A cell boundary in a markdown table row: a pipe nobody escaped.
_CELL_SPLIT_RE = re.compile(r"(?<!\\)\|")


def _sha(path):
    """A file's sha256 with its line endings as git stores them, so a checkout's CRLF does not change it."""

    with io.open(path, "rb") as fh:
        return hashlib.sha256(fh.read().replace(b"\r\n", b"\n")).hexdigest()


def read_markdown(folder=HERE):
    """The markdown record as it stands, cut into what the store keeps - and proved to be all of it.

    Every test file is cut into entries, anchor to anchor, and each entry into its body, comments and rule; issues.md
    into its prose, its Inbox items and its receipt rows.  Each cut is checked to give back exactly what it was cut
    from, so what is left over is only what was generated from the record: the Ledger and archives sections of
    tests.md, the archives' headers, and the receipt table's header.

    :param folder: where tests.md, its archives and issues.md are
    :return: dict - tests [(tag, anchor, block)] in tag order, items [(ref, text)] in Inbox order, receipts [dict]
             newest first, notes {name: text}, sources [(file, sha256)], duplicates [(tag, file)]
    """

    tests_path = os.path.join(folder, TESTS_MD)
    issues_path = os.path.join(folder, ISSUES_MD)

    if not os.path.exists(tests_path) or not os.path.exists(issues_path):
        raise MarkdownGone("REFUSING to migrate: %s or %s is not in %s.  The markdown has gone, so the store is the"
                           " record now and there is nothing to import - and importing anything else over it would"
                           " lose what has been written since" % (TESTS_MD, ISSUES_MD, folder))

    names = [TESTS_MD] + sorted(os.path.basename(p) for p in glob.glob(os.path.join(folder, ARCHIVE_GLOB)))

    sources = []
    tests = {}
    duplicates = []
    notes = {}

    for name in names:
        path = os.path.join(folder, name)

        sources.append((name, _sha(path)))

        text, _ = triage.read_text(path)

        head, found = triage.parse_tests_text(text)

        if head + "".join(e.block for e in found) != text:
            raise IOError("%s does not cut into its entries without loss" % name)

        if name == TESTS_MD:
            at = head.find("\n## Ledger")

            notes["tests.preamble"] = head[:at + 1] if at >= 0 else head

        for e in found:
            if e.tag in tests:
                if tests[e.tag][1] != e.block:
                    raise IOError("REFUSING to migrate: %s is in two files with different text, and which one holds the"
                                  " newer comment is not something to guess.  Delete the wrong copy by hand" % e.tag)

                duplicates.append((e.tag, name))
                continue

            body, comments, tail = split_entry(e.block)

            if body + "".join(comments) + tail != e.block:
                raise IOError("%s does not cut into its body, comments and rule without loss" % e.tag)

            tests[e.tag] = (e.anchor, e.block)

    text, _ = triage.read_text(issues_path)

    sources.append((ISSUES_MD, _sha(issues_path)))

    inbox_at = text.find("\n" + ISSUES_INBOX + "\n") + 1
    picked_at = text.find("\n" + ISSUES_PICKED + "\n") + 1
    backlog_at = text.find("\n" + ISSUES_BACKLOG + "\n") + 1

    if not 0 < inbox_at < picked_at < backlog_at:
        raise IOError("issues.md does not have its three sections in order: %s, %s, %s"
                      % (ISSUES_INBOX, ISSUES_PICKED, ISSUES_BACKLOG))

    notes["issues.preamble"] = text[:inbox_at]

    section = text[inbox_at:picked_at]

    marks = list(triage.ISSUE_ITEM_RE.finditer(section))

    lead = section[:marks[0].start()] if marks else section

    if lead.strip() != ISSUES_INBOX:
        # Anything typed above the first item: kept, word for word.
        notes["issues.inbox-lead"] = lead

    items = []

    for i, m in enumerate(marks):
        items.append((m.group(1), section[m.start():marks[i + 1].start() if i + 1 < len(marks) else len(section)]))

    if lead + "".join(t for _, t in items) != section:
        raise IOError("the Inbox does not cut into its items without loss")

    if len(set(ref for ref, _ in items)) != len(items):
        raise IOError("REFUSING to migrate: a ref is in the Inbox twice")

    picked = text[picked_at:backlog_at]

    lines = picked.split("\n")

    try:
        top = lines.index(RECEIPT_HEADER)
    except ValueError:
        raise IOError("the receipt table's header is not %r" % RECEIPT_HEADER)

    if lines[top + 1] != RECEIPT_RULE:
        raise IOError("the receipt table's rule is not %r" % RECEIPT_RULE)

    end = top + 2

    while end < len(lines) and lines[end].startswith("|"):
        end += 1

    notes["issues.receipts.before"] = "\n".join(lines[:top]) + "\n"
    notes["issues.receipts.after"] = "\n".join(lines[end:])
    notes["issues.backlog"] = text[backlog_at:]

    receipts_ = []

    for line in lines[top + 2:end]:
        cells = [c.strip().replace("\\|", "|") for c in _CELL_SPLIT_RE.split(line)[1:-1]]

        if len(cells) != len(RECEIPT_CELLS):
            raise IOError("a receipt row has %d cells, not %d: %r" % (len(cells), len(RECEIPT_CELLS), line[:100]))

        row = dict(zip(RECEIPT_CELLS, cells))

        if receipt_line(row) != line:
            raise IOError("a receipt row does not read back as it was written: %r" % line[:100])

        receipts_.append(row)

    if len(set(r["ref"] for r in receipts_)) != len(receipts_):
        raise IOError("REFUSING to migrate: a ref has two receipts")

    rebuilt = (notes["issues.preamble"] + section + notes["issues.receipts.before"] + RECEIPT_HEADER + "\n"
               + RECEIPT_RULE + "\n" + "".join(line + "\n" for line in lines[top + 2:end])
               + notes["issues.receipts.after"] + notes["issues.backlog"])

    if rebuilt != text:
        raise IOError("issues.md does not cut into its prose, items and receipts without loss")

    return {
        "tests": [(tag,) + tests[tag] for tag in sorted(tests, key=_tag_order)],
        "items": items,
        "receipts": receipts_,
        "notes": notes,
        "sources": sources,
        "duplicates": duplicates,
    }


def _import(conn, record):
    """Writes a record `read_markdown` cut into the store's tables.

    :param conn: an open connection, in a write, its record tables empty
    :param record: from read_markdown
    """

    for ordinal, (tag, anchor, block) in enumerate(record["tests"]):
        body, comments, tail = split_entry(block)

        conn.execute("INSERT INTO test (tag, ordinal, anchor, body, tail) VALUES (?, ?, ?, ?, ?)",
                     (tag, ordinal, anchor, body, tail))

        for seq, text in enumerate(comments):
            _put_comment(conn, tag, seq, text)

        _derive_test(conn, tag)

    for ordinal, (ref, text) in enumerate(record["items"]):
        conn.execute("INSERT INTO issue (ref, ordinal, text, inbox, kind, title, filed, filed_at, raised_from, build)"
                     " VALUES (?, ?, ?, 1, ?, ?, ?, ?, ?, ?)", (ref, ordinal, text) + _derive_issue(text))

    # Newest first in the file, so the last row read is the first written.
    for seq, row in enumerate(reversed(record["receipts"])):
        conn.execute("INSERT INTO receipt (ref, seq, filed, kind, what, state, became) VALUES (?, ?, ?, ?, ?, ?, ?)",
                     (row["ref"], seq, row["filed"], row["kind"], row["what"], row["state"], row["became"]))

    for name, text in sorted(record["notes"].items()):
        conn.execute("INSERT INTO note (name, text) VALUES (?, ?)", (name, text))


def _record_differences(conn, record):
    """Where the store and a record cut from the markdown part.

    :param conn: an open connection
    :param record: from read_markdown
    :return: a list of sentences, empty when they hold the same
    """

    out = []

    tests = dict((tag, block) for tag, _, block in record["tests"])
    held = set(r[0] for r in conn.execute("SELECT tag FROM test"))

    for tag in sorted(set(tests) ^ held, key=_tag_order):
        out.append("%s is in %s only" % (tag, "the markdown" if tag in tests else "the store"))

    for tag in sorted(set(tests) & held, key=_tag_order):
        if entry_text(conn, tag) != tests[tag]:
            out.append("%s's text differs" % tag)

    items = dict(record["items"])
    stored = dict((r[0], r[1]) for r in conn.execute("SELECT ref, inbox FROM issue"))

    for ref in sorted(set(items) ^ set(stored), key=_tag_order):
        out.append("%s is in %s's Inbox only" % (ref, "the markdown" if ref in items else "the store"))

    for ref in sorted(set(items) & set(stored), key=_tag_order):
        if issue_text(conn, ref) != items[ref] or not stored[ref]:
            out.append("%s differs" % ref)

    rows = [dict((k, r[k]) for k in RECEIPT_CELLS) for r in conn.execute("SELECT * FROM receipt ORDER BY seq DESC")]

    if rows != record["receipts"]:
        out.append("the receipts differ")

    if dict((r[0], r[1]) for r in conn.execute("SELECT name, text FROM note")) != record["notes"]:
        out.append("the notes differ")

    return out


def record_counts(conn):
    """What the store holds, counted the way the migration's proof counts the markdown.

    :param conn: an open connection
    :return: dict
    """

    def one(sql):
        return conn.execute(sql).fetchone()[0]

    adam = 0

    for sql in ("SELECT body || tail FROM test", "SELECT text FROM comment", "SELECT text FROM issue",
                "SELECT " + " || ' ' || ".join("COALESCE(%s, '')" % k for k in RECEIPT_CELLS) + " FROM receipt",
                "SELECT text FROM note"):
        for (text,) in conn.execute(sql):
            adam += (text or "").count("**Adam,")

    return {
        "tests": one("SELECT COUNT(*) FROM test"),
        "comments": one("SELECT COUNT(*) FROM comment WHERE tag LIKE 'MT-%'"),
        "verdicts": one("SELECT COUNT(*) FROM comment WHERE tag LIKE 'MT-%' AND kind = 'verdict'"),
        "adam_verdicts": one("SELECT COUNT(*) FROM comment WHERE tag LIKE 'MT-%' AND kind = 'verdict' AND who = 'Adam'"),
        "issue_comments": one("SELECT COUNT(*) FROM comment WHERE tag NOT LIKE 'MT-%'"),
        "inbox": one("SELECT COUNT(*) FROM issue WHERE inbox = 1"),
        "issues": one("SELECT COUNT(*) FROM issue"),
        "receipts": one("SELECT COUNT(*) FROM receipt"),
        "notes": one("SELECT COUNT(*) FROM note"),
        "adam_lines": adam,
        "findings": one("SELECT COUNT(*) FROM finding"),
        "dead_citations": one("SELECT COUNT(*) FROM dead_citation"),
    }


def migrate(conn, folder=HERE, force=False):
    """Imports the markdown into the store: the one time the record is read from it.

    RE-RUNNABLE.  Run again on the same markdown it changes nothing.  Once the store has been written since - a comment,
    a disposition - the markdown is no longer the record, and it refuses rather than write the older text over the
    newer; `force` imports anyway, losing what was written since.  It refuses once the markdown has gone.

    The findings and dead citations are not touched: they have lived only in the store since 2026-09-08.

    :param conn: an open connection to the store
    :param folder: where tests.md, its archives and issues.md are
    :param force: True to import over a store written since its migration
    :return: dict - "changed", "written" (dump files), "duplicates", and record_counts
    """

    record = read_markdown(folder)

    if is_migrated(conn) and not force:
        differences = _record_differences(conn, record)

        if not differences:
            out = {"changed": False, "written": _after_write(conn), "duplicates": record["duplicates"]}
            out.update(record_counts(conn))
            return out

        raise IOError("REFUSING to migrate again: the store has been written since it was migrated, so the markdown is"
                      " no longer the record (%s%s).  Pass --force only to throw those writes away"
                      % ("; ".join(differences[:5]), "; ..." if len(differences) > 5 else ""))

    with writing(conn):
        for table in RECORD_TABLES:
            conn.execute("DROP TABLE IF EXISTS %s" % table)

        for statement in RECORD_SCHEMA.split(";"):
            if statement.strip():
                conn.execute(statement)

        _import(conn, record)

        differences = _record_differences(conn, record)

        if differences:
            raise IOError("the store does not hold what was imported (%s) - nothing was written"
                          % "; ".join(differences[:5]))

        conn.executemany("INSERT INTO meta (key, value) VALUES (?, ?)", [
            ("migrated", datetime.datetime.now().strftime("%Y-%m-%d %H:%M")),
            ("migrated from", "; ".join("%s sha256 %s" % s for s in record["sources"])),
        ])

    out = {"changed": True, "written": _after_write(conn), "duplicates": record["duplicates"]}
    out.update(record_counts(conn))

    return out


# --------------------------------------------------------------------------------------------
# the self test
# --------------------------------------------------------------------------------------------

# A record in markdown, small, holding every shape the real one has that the cut has to survive: an entry with a
# Comments heading and unmarked prose under it, one with no heading and a marker in its steps, one with no blank line
# before its rule, one kept twice (identically) in two files, CRLF, a receipt with an escaped pipe, notes under the
# table, a ref (OB-999) named only in prose, and lines ending in markdown's hard break - two spaces, spelled <HB> here
# because an editor that trims trailing space would take them.
_FIXTURE_TESTS = """# Manual tests

The preamble.

---

## Ledger - where your attention is needed

| Tag | Date | What | Disposition | From |
|---|---|---|---|---|

---

## The tests


<a id="mt-001"></a>
### MT-001 - 2026-08-01 - first, with a Comments section

**Disposition:** fixed validated
**From:** hands-on testing

**Written:** 2026-08-01

**What to do.** Run it.  Then see [MT-002](tests.md#mt-002).

#### Comments

A line nobody signed.

**Adam, 2026-08-02 (triage).** Works.

All good.

*Run against commit abc.*

**Claude, 2026-08-03.**

Thanks; OB-999 was the other one.

---

<a id="mt-002"></a>
### MT-002 - 2026-08-04 - second, no heading, no blank line before its rule

**Disposition:** needs test<HB>
**From:** OB-950

**Written:** 2026-08-04

**Steps**

**Claude, 2026-08-04.** A marker in the steps, before any comment.

1. Look.  "A trailing hard break"<HB>

**Adam, 2026-08-06.** Does not work.
Broken.
---

"""

_FIXTURE_ARCHIVE = """# Manual tests - archive pre-3.0.0

Generated.

---

## The tests


<a id="mt-003"></a>
### MT-003 - 2026-07-01 - third, archived

**Disposition:** superseded
**From:** hands-on testing

**Written:** 2026-07-01

Do nothing.

**Adam, 2026-07-02.** Could not run this.

---

"""

_FIXTURE_ISSUES = """# Issues

The preamble.

---

## Inbox

### OB-950 - 2026-08-10 - a bug

**Kind:** bug<HB>
**Raised from:** MT-001 (first)<HB>
**Filed:** 2026-08-10 10:00<HB>
**Build:** commit abc

It breaks.

**Claude, 2026-08-11.** Picked up.

### FR-020 - 2026-08-12 - a feature

**Kind:** feature request<HB>
**Raised from:** noticed while testing - not from a particular test<HB>
**Filed:** 2026-08-12 11:00<HB>

Make it so.

## What has been picked up

Newest first.

| Filed | Ref | Kind | What | State | Became |
|---|---|---|---|---|---|
| 2026-08-11 | OB-950 | bug | The bug, with a pipe: "A \\| B". | - | MT-002 |
| 2026-08-01 | FR-010 | feature request | An older request. | fixed validated | - |

*A note under the table.*

## Where the older backlog is

The backlog.
"""


def selftest():
    """Exercises the store on copies, never on the record itself, and says what it found.

    There is no Python test harness in this repository, so this is it: a command to run before trusting the store,
    whose failure is a sentence.  The migration is tried on a small markdown record written here; the writes on a store
    migrated from it; and, once the repository's store has been migrated, the record checks on it (read only) and a
    write on a copy of it.

    :return: (passed, list of result lines)
    """

    import difflib
    import shutil
    import tempfile
    import threading
    import time

    results = []
    ok = [True]

    def check_that(claim, condition):
        results.append(("PASS  " if condition else "FAIL  ") + claim)

        if not condition:
            ok[0] = False

    def skip(claim):
        results.append("SKIP  " + claim)

    def refused(call, kind):
        try:
            call()
        except kind:
            return True
        except Exception:
            return False

        return False

    work = tempfile.mkdtemp(prefix="triagedb-selftest-")

    try:
        md = os.path.join(work, "md")

        os.makedirs(md)

        def put(name, text, crlf):
            text = text.replace("<HB>", "  ")

            with io.open(os.path.join(md, name), "wb") as fh:
                fh.write((text.replace("\n", "\r\n") if crlf else text).encode("utf-8"))

        put(TESTS_MD, _FIXTURE_TESTS, True)
        put("tests-archive-pre-3.0.0.md", _FIXTURE_ARCHIVE, False)
        put("tests-archive-3.0.0.md", _FIXTURE_ARCHIVE.replace("pre-3.0.0", "3.0.0"), False)
        put(ISSUES_MD, _FIXTURE_ISSUES, True)

        store = os.path.join(work, "triage.db")
        dump = os.path.join(work, DUMP_NAME)

        conn = connect(store)

        # The findings are already the store's, and the migration must leave them exactly as they were.
        conn.execute("INSERT INTO finding (ref, document, title, status, status_note) VALUES"
                     " ('ZZ-A1', 'ZZ.md', 'a finding', 'Closed', 'tracked as OB-950')")
        conn.execute("INSERT INTO dead_citation (ref, cited_by) VALUES ('ZZ-B9', 'X.java')")
        conn.commit()

        findings_before = [tuple(r) for r in conn.execute("SELECT * FROM finding")] + \
                          [tuple(r) for r in conn.execute("SELECT * FROM dead_citation")]

        check_that("nothing is written to a store that has not been migrated",
                   refused(lambda: add_comment(conn, "MT-001", "Adam", "x"), NotMigrated))

        # ---- the migration

        done = migrate(conn, md)

        markdown = dict((name, io.open(os.path.join(md, name), "rb").read().decode("utf-8").replace("\r\n", "\n"))
                        for name in os.listdir(md))

        blocks = {}

        for name in (TESTS_MD, "tests-archive-pre-3.0.0.md"):
            for e in triage.parse_tests_text(markdown[name])[1]:
                blocks[e.tag] = e.block

        markers = sum(len(re.findall(r"^\*\*(?:Adam|Claude),", b, re.M)) for b in blocks.values())
        adam = sum(text.count("**Adam,") for name, text in markdown.items() if name != "tests-archive-3.0.0.md")

        check_that("the migration imports every entry, once, though one is in two files (%d)" % done["tests"],
                   done["tests"] == len(blocks) == 3 and done["duplicates"] == [("MT-003", "tests-archive-pre-3.0.0.md")])

        check_that("and every comment, wherever it sits in its entry (%d)" % done["comments"],
                   done["comments"] == markers == 5)

        check_that("and every verdict (%d)" % done["verdicts"], done["verdicts"] == 3)

        check_that("and every '**Adam,' line (%d)" % done["adam_lines"], done["adam_lines"] == adam)

        check_that("and every Inbox item and receipt (%d and %d)" % (done["inbox"], done["receipts"]),
                   done["inbox"] == 2 and done["receipts"] == 2)

        check_that("show gives back every entry exactly as written",
                   all(show(conn, tag).startswith(block) and entry_text(conn, tag) == block
                       for tag, block in blocks.items()))

        check_that("and every Inbox item", all(issue_text(conn, ref) == text for ref, text in read_markdown(md)["items"]))

        check_that("a receipt keeps its escaped pipe", receipt_line(conn.execute(
            "SELECT * FROM receipt WHERE ref = 'OB-950'").fetchone()) in markdown[ISSUES_MD])

        check_that("the findings and dead citations are as they were",
                   [tuple(r) for r in conn.execute("SELECT * FROM finding")]
                   + [tuple(r) for r in conn.execute("SELECT * FROM dead_citation")] == findings_before)

        check_that("the fields are read out of the text: MT-002 needs test and is from OB-950",
                   check(conn, tag="MT-002")[0]["disposition"] == "needs test"
                   and conn.execute("SELECT origin FROM test WHERE tag = 'MT-002'").fetchone()[0] == "OB-950")

        check_that("a verdict reads as a verdict, and the latest is the one check() gives",
                   check(conn, tag="MT-002")[0]["latest"] == "Does not work")

        check_that("the record holds together", verify_record(conn)[0])

        check_that("the dump is written, and is the store's own rows", verify_dump(conn, dump)[0])

        files = dict((name, io.open(os.path.join(dump, name), "rb").read()) for name in DUMP_FILES)

        check_that("the dump gives back every entry exactly as written", all(
            _from_dump(files["tests.txt"], files["comments.txt"], tag) == block for tag, block in blocks.items()))

        again = migrate(conn, md)

        check_that("migrating again changes nothing", not again["changed"] and not again["written"]
                   and dict((name, io.open(os.path.join(dump, name), "rb").read()) for name in DUMP_FILES) == files)

        check_that("the dump renders the same twice", render_dump(conn) == render_dump(conn))

        # ---- the writes

        add_comment(conn, "MT-001", "Adam", "A self test wrote this.", "Does not work")

        check_that("a comment is a row, and reads back as a verdict",
                   history(conn, "MT-001")[-1]["verdict"] == "Does not work"
                   and check(conn, tag="MT-001")[0]["latest"] == "Does not work")

        check_that("and show ends with it, under what was there", show(conn, "MT-001").startswith(
            blocks["MT-001"][:-len("---\n\n")]) and "A self test wrote this.\n\n---\n\n" in show(conn, "MT-001"))

        def changed(name):
            now = io.open(os.path.join(dump, name), "rb").read().decode("utf-8").split("\n")
            was = files[name].decode("utf-8").split("\n")

            diff = [line for line in difflib.unified_diff(was, now, lineterm="", n=0)
                    if line[:1] in "+-" and not line.startswith(("+++", "---"))]

            return [line for line in diff if line.startswith("-")], [line for line in diff if line.startswith("+")]

        gone, added = changed("comments.txt")

        check_that("and in the dump it is a small added hunk: %d lines added, none removed" % len(added),
                   not gone and 0 < len(added) <= 12)

        gone, added = changed("tests.txt")

        check_that("and the entry's own record is untouched", not gone and not added)

        check_that("and the dump is still the store's own rows", verify_dump(conn, dump)[0])

        files = dict((name, io.open(os.path.join(dump, name), "rb").read()) for name in DUMP_FILES)

        was = set_disposition(conn, "MT-002", "fixed unvalidated")

        gone, added = changed("tests.txt")

        check_that("a disposition change is one line of the text and the field read out of it",
                   len(gone) == len(added) == 2 and any("| **Disposition:** fixed unvalidated  " == a[1:]
                                                       for a in added))

        set_disposition(conn, "MT-002", was)

        check_that("and it goes back, byte for byte",
                   io.open(os.path.join(dump, "tests.txt"), "rb").read() == files["tests.txt"])

        ref = file_issue(conn, "bug", "Filed by the self test", "Nothing to see.", raised_from="MT-002 (second)")

        check_that("filing takes the number after the highest named anywhere, prose included (%s)" % ref,
                   ref == "OB-1000")

        gone, added = changed("issues.txt")

        check_that("and in the dump it is one record added to issues.txt (%d lines)" % len(added),
                   not gone and 0 < len(added) <= 25)

        check_that("and the item is in the Inbox, not picked up", [i["ref"] for i in issues(conn)] == ["FR-020", ref])

        check_that("an item with no receipt cannot leave the Inbox",
                   refused(lambda: clear_from_inbox(conn, ref), ValueError))

        add_receipt(conn, ref, "Picked up by the self test.", became="MT-002")

        clear_from_inbox(conn, ref)

        check_that("one with a receipt can, and keeps its row and its text",
                   ref not in [i.ref for i in inbox_items(conn)] and issue_text(conn, ref) is not None
                   and receipts(conn)[0]["ref"] == ref)

        add_comment(conn, "FR-010", "Claude", "A receipt takes a comment.")

        check_that("an issue takes a comment as a test does", "A receipt takes a comment." in show(conn, "FR-010"))

        real_dump = write_dump

        def stuck(*_args, **_kwargs):
            raise IOError("the disk said no")

        globals()["write_dump"] = stuck

        got = []

        try:
            for write in (lambda: file_issue(conn, "bug", "Filed while the dump is stuck", "-"),
                          lambda: add_comment_text(conn, "MT-001", "**Claude, 2026-10-19.** Said while it is stuck.")):
                try:
                    write()
                    got.append(None)
                except DumpNotWritten as bad:
                    got.append(bad.result)
        finally:
            globals()["write_dump"] = real_dump

        check_that("a write the dump cannot follow stands, and its error carries what it wrote (%s)" % got[0],
                   got[0] is not None and got[0] in [i["ref"] for i in issues(conn)]
                   and got[1] is not None and got[1].strip().startswith("**Claude, 2026-10-19.** Said while")
                   and "Said while it is stuck." in entry_text(conn, "MT-001") and not verify_dump(conn, dump)[0])

        tag = add_test(conn, "A test the self test wrote", "hands-on testing", "**Steps**\n\n1. Nothing.")

        check_that("and the next write, of anything, catches the dump up", verify_dump(conn, dump)[0])

        check_that("a new test is numbered after the last (%s), and reads as it was written" % tag,
                   tag == "MT-004" and check(conn, tag=tag)[0]["disposition"] == "needs test"
                   and entry_text(conn, tag).endswith("1. Nothing.\n\n---\n\n"))

        check_that("an invalid disposition is refused",
                   refused(lambda: set_disposition(conn, "MT-001", "nearly"), ValueError))

        check_that("an invalid verdict is refused",
                   refused(lambda: add_comment(conn, "MT-001", "Adam", "x", "Sort of works"), ValueError))

        check_that("an unknown tag is refused", refused(lambda: set_disposition(conn, "MT-999", "needs test"), KeyError))

        check_that("a comment without a marker is refused",
                   refused(lambda: add_comment_text(conn, "MT-001", "unsigned"), ValueError))

        check_that("the record still holds together after every kind of write", verify_record(conn)[0])

        check_that("and the dump is still the store's own rows", verify_dump(conn, dump)[0])

        check_that("migrating over a store written since is refused", refused(lambda: migrate(conn, md), IOError))

        # ---- two writers at once: the app and a script

        other = connect(store)

        seen = data_version(conn)

        add_comment(other, "MT-001", "Claude", "Another process wrote this.")

        check_that("a write by another process is seen, for the app to re-read", data_version(conn) != seen)

        # Each writer reads the next number and then, slowly, writes it.  Under BEGIN IMMEDIATE the second waits for
        # the first and reads the number after; both are kept.
        real = _next_seq
        errors = []

        def slow(conn_, ref_):
            n = real(conn_, ref_)
            time.sleep(0.4)
            return n

        globals()["_next_seq"] = slow

        try:
            def write(_, words):
                try:
                    c = connect(store, timeout=10)
                    add_comment(c, "MT-002", "Claude", words)
                    c.close()
                except Exception as bad:
                    errors.append(bad)

            before = len(history(conn, "MT-002"))

            # A connection each, opened in its own thread, as two processes would.
            threads = [threading.Thread(target=write, args=(None, "Writer %d." % i)) for i in range(2)]

            for t in threads:
                t.start()

            for t in threads:
                t.join()
        finally:
            globals()["_next_seq"] = real

        check_that("two writers that cross both keep their comment (%s)" % (errors or "no errors"),
                   not errors and len(history(conn, "MT-002")) == before + 2)

        refs = []

        def file_one():
            try:
                c = connect(store, timeout=10)
                refs.append(file_issue(c, "feature request", "Crossed", "-"))
                c.close()
            except Exception as bad:
                errors.append(bad)

        threads = [threading.Thread(target=file_one) for _ in range(3)]

        for t in threads:
            t.start()

        for t in threads:
            t.join()

        check_that("three filings at once take three numbers (%s)" % ", ".join(sorted(refs)),
                   not errors and len(set(refs)) == 3)

        check_that("and the dump caught up with every write", verify_dump(conn, dump)[0])

        other.close()
        conn.close()

        # ---- the markdown has gone

        os.remove(os.path.join(md, TESTS_MD))

        conn = connect(os.path.join(work, "fresh.db"))

        check_that("the migration refuses once the markdown has gone", refused(lambda: migrate(conn, md), MarkdownGone))

        conn.close()

        # ---- the repository's own store

        # Asked read-only first: connecting would add the record's tables to a store not yet migrated.
        real_store = connect(DB_FILE) if is_migrated_file(DB_FILE) else None

        if real_store is None:
            skip("the repository's store has not been migrated, so its record is not checked")
        else:
            try:
                held, problems = verify_record(real_store)

                check_that("the repository's record holds together (%s)" % ("; ".join(problems[:3]) or "no problems"),
                           held)

                check_that("its dump is its own rows", verify_dump(real_store)[0])

                counts = record_counts(real_store)

                # In a folder of its own, so its dump is its own too.
                os.makedirs(os.path.join(work, "real"))

                copy = os.path.join(work, "real", "triage.db")

                shutil.copyfile(DB_FILE, copy)

                c = connect(copy)

                write_dump(c)

                def dumped():
                    return dict((name, io.open(os.path.join(work, "real", DUMP_NAME, name), "rb").read())
                                for name in DUMP_FILES)

                was = dumped()

                tag = check(c, disposition="needs test")[0]["tag"] if check(c, disposition="needs test") else \
                    check(c)[-1]["tag"]

                add_comment(c, tag, "Adam", "A self test wrote this on a copy.", "Works")

                check_that("a comment on a copy of it reads back (%s)" % tag,
                           show(c, tag).count("A self test wrote this on a copy.") == 1
                           and record_counts(c)["comments"] == counts["comments"] + 1)

                now = dumped()

                grown = now["comments.txt"].decode("utf-8").split("\n")
                held = was["comments.txt"].decode("utf-8").split("\n")

                gone = [line for line in difflib.unified_diff(held, grown, lineterm="", n=0)
                        if line.startswith("-") and not line.startswith("---")]

                check_that("and in the copy's dump it is a hunk added to comments.txt (%d lines), and the dump is the"
                           " copy's own rows" % (len(grown) - len(held)),
                           verify_dump(c)[0] and not gone and 0 < len(grown) - len(held) <= 12
                           and all(now[name] == was[name] for name in DUMP_FILES
                                   if name not in ("comments.txt", "tests.txt")))

                c.close()
            finally:
                real_store.close()

    except Exception:
        # A claim that cannot even be asked is a red one, and the claims already answered are still worth reading.
        import traceback

        check_that("the self test ran to its end - it stopped at %s"
                   % traceback.format_exc().strip().splitlines()[-1], False)

    finally:
        shutil.rmtree(work, ignore_errors=True)

    return ok[0], results


def _from_dump(tests_txt, comments_txt, tag):
    """A test's text as the dump holds it - for the self test's question whether the dump lost a word.  Nothing else
    reads the dump back.

    :param tests_txt: tests.txt's bytes
    :param comments_txt: comments.txt's bytes
    :param tag: MT-###
    :return: the text
    """

    def fields(data, header):
        lines = data.decode("utf-8").split("\n")

        found = {}
        name = None
        lines_of = []
        inside = False

        for line in lines + [""]:
            if line.startswith("["):
                inside = line == header
                continue

            if not inside:
                continue

            if line.startswith("|"):
                lines_of.append(line[2:] if line.startswith("| ") else "")
                continue

            if name is not None:
                found[name[0]] = "\n".join(lines_of) + ("" if name[1] else "\n")
                name = None

            if line.endswith(":") and not line.startswith(" "):
                name = (line[:-1].split(" ")[0], " (no newline at end)" in line)
                lines_of = []

        return found

    test = fields(tests_txt, "[test %s]" % tag)

    said = []

    seq = 0

    while True:
        found = fields(comments_txt, "[comment %s %d]" % (tag, seq))

        if "text" not in found:
            break

        said.append(found["text"])

        seq += 1

    return test["body"] + "".join(said) + test["tail"]


# --------------------------------------------------------------------------------------------
# the command line
# --------------------------------------------------------------------------------------------

def _print(text):
    """Writes text to stdout as UTF-8, whatever the console's code page.

    :param text: what to write
    """

    stream = getattr(sys.stdout, "buffer", None)

    if stream is None:
        sys.stdout.write(text)
    else:
        stream.write(text.encode("utf-8"))
        stream.flush()


def _print_rows(rows):
    if not rows:
        print("(none)")
        return

    for r in rows:
        _print("%-8s %-20s %-18s %s\n" % (r["tag"], (r["disposition"] or "?")[:20],
                                          (r["latest"] or "never triaged")[:18], (r["title"] or "")[:60]))

    print("")
    print("%d entries" % len(rows))


# Commands of the markdown era, and what took their place.
GONE = {
    "sync": "there is nothing to sync: triage.db is the record (FR-136 Phase 1c)",
    "regenerate-ledger": "the ledger is a query now: `triagedb.py ledger`",
    "archive": "nothing is archived any more: every entry is in the store, wherever it was kept",
}


def main(argv=None):
    """The command line over the store.

    :param argv: arguments, or None for sys.argv
    :return: a process exit code
    """

    argv = list(sys.argv[1:] if argv is None else argv)

    if argv and argv[0] in GONE:
        print("`%s` is gone: %s" % (argv[0], GONE[argv[0]]))
        return 2

    parser = argparse.ArgumentParser(
        prog="triagedb",
        description="The manual-test and issue tracker.  triage.db is the record; every command reads or writes it.")

    sub = parser.add_subparsers(dest="command")

    p = sub.add_parser("migrate", help="import tests.md, its archives and issues.md into the store, once; re-runnable,"
                                       " and refused once the markdown has gone")
    p.add_argument("--folder", default=HERE, help="where the markdown is (default: beside this script)")
    p.add_argument("--force", action="store_true", help="import over a store written since its migration, losing"
                                                        " what was written")

    p = sub.add_parser("show", help="one MT, OB or FR in full: its text exactly as written, then its comments")
    p.add_argument("tag")

    p = sub.add_parser("check", help="query the tests")
    p.add_argument("--disposition")
    p.add_argument("--verdict")
    p.add_argument("--tag")
    p.add_argument("--untriaged", action="store_true", help="only entries Adam has never ruled on")
    p.add_argument("--reopened", action="store_true", help="only entries that changed after a ruling")
    p.add_argument("--json", action="store_true")

    sub.add_parser("ledger", help="everything still asking Adam for something")

    p = sub.add_parser("issues", help="the Inbox items not yet picked up")
    p.add_argument("--kind", choices=["bug", "feature request"])
    p.add_argument("--all", action="store_true", help="every item in the Inbox, picked up or not, and every receipt")

    p = sub.add_parser("set", help="change a test's disposition")
    p.add_argument("tag")
    p.add_argument("disposition")

    p = sub.add_parser("comment", help="say something on a test or an issue, optionally as a triage verdict")
    p.add_argument("tag")
    p.add_argument("text")
    p.add_argument("--who", default="Claude")
    p.add_argument("--verdict")

    p = sub.add_parser("add-test", help="write a new MT-### entry; its steps from a file (or - for stdin)")
    p.add_argument("title")
    p.add_argument("--from", dest="origin", required=True, help="where it came from: a finding, an OB or FR, or"
                                                               " 'hands-on testing'")
    p.add_argument("--steps", required=True, help="a file holding the steps and the expected result, as markdown")
    p.add_argument("--disposition", default="needs test")

    p = sub.add_parser("file", help="file a new issue into the Inbox")
    p.add_argument("kind", choices=["bug", "feature request"])
    p.add_argument("title")
    p.add_argument("detail")
    p.add_argument("--raised-from", dest="raised_from")
    p.add_argument("--build")

    p = sub.add_parser("receipt", help="pick an item up: a new row in the receipt table")
    p.add_argument("ref")
    p.add_argument("what")
    p.add_argument("--state", default="-")
    p.add_argument("--became", default="-")
    p.add_argument("--kind")
    p.add_argument("--filed")

    p = sub.add_parser("update-receipt", help="change a receipt's State, Became or What")
    p.add_argument("ref")
    p.add_argument("--state")
    p.add_argument("--became")
    p.add_argument("--what")

    p = sub.add_parser("clear", help="take a done item, one with a receipt, out of the Inbox; it is kept")
    p.add_argument("ref")

    p = sub.add_parser("notes", help="the prose the markdown carried, by name")
    p.add_argument("name", nargs="?")

    sub.add_parser("dump", help="write the text dump (every write does this itself)")

    p = sub.add_parser("verify", help="the record holds together, the dump and findings.tsv are the store's own rows,"
                                      " and every OB a finding says it is tracked as is in the Inbox")
    p.add_argument("--store", default=DB_FILE, help="the store whose status notes are checked - a copy, to see the"
                                                     " check fail")

    sub.add_parser("counts", help="rewrite the counts behaviour.md and open-questions.md quote, from the store")
    sub.add_parser("selftest", help="exercise the store on copies")

    args = parser.parse_args(argv)

    if not args.command:
        parser.print_help()
        return 2

    if args.command == "selftest":
        passed, lines = selftest()

        for line in lines:
            _print(line + "\n")

        print("")
        print("PASSED" if passed else "FAILED")

        return 0 if passed else 1

    conn = connect()

    try:
        return _command(args, conn)
    except (NotMigrated, MarkdownGone, KeyError, ValueError) as bad:
        print("triagedb: %s" % (bad.args[0] if bad.args else bad), file=sys.stderr)
        return 1
    except DumpNotWritten as bad:
        print("triagedb: %s" % bad, file=sys.stderr)
        return 1
    finally:
        conn.close()


def _command(args, conn):
    """Runs one command.

    :param args: parsed
    :param conn: the store
    :return: an exit code
    """

    if args.command == "migrate":
        done = migrate(conn, args.folder, args.force)

        print("%s: %d tests, %d comments (%d verdicts), %d '**Adam,' lines, %d Inbox items, %d receipts, %d notes;"
              " %d findings and %d dead citations untouched"
              % ("migrated" if done["changed"] else "already migrated, nothing changed", done["tests"],
                 done["comments"], done["verdicts"], done["adam_lines"], done["inbox"], done["receipts"],
                 done["notes"], done["findings"], done["dead_citations"]))

        for tag, name in done["duplicates"]:
            print("  %s was also in %s, word for word, and is kept once" % (tag, name))

        print("dump: %s" % (", ".join(done["written"]) or "unchanged"))

        return 0

    if args.command == "counts":
        changed = render_counts(conn)

        for path in changed:
            print("rewrote " + os.path.basename(path))

        if not changed:
            print("(already up to date)")

        return 0

    if args.command == "verify":
        ok = True

        if is_migrated(conn):
            held, problems = verify_record(conn)

            print("the record holds together" if held else "the record does not hold together:")

            for problem in problems:
                print("  " + problem)

            dumped, message = verify_dump(conn)

            print(message)

            ok = held and dumped
        else:
            print("the store has not been migrated: run `triagedb.py migrate`")

            ok = False

        mirrored, message = verify_findings_mirror(conn)

        print(message)

        claims_ok, claims_message, offenders = verify_inbox_claims(args.store)

        print(claims_message)

        for ref, document, ob in offenders:
            print("  %s (%s) says %s" % (ref, document, ob))

        return 0 if ok and mirrored and claims_ok else 1

    require_record(conn)

    if args.command == "show":
        text = show(conn, args.tag)

        if text is None:
            print("triagedb: nothing in the store is %s" % args.tag.upper(), file=sys.stderr)
            return 2

        _print(text)

        return 0

    if args.command == "check":
        rows = check(conn, disposition=args.disposition, verdict=args.verdict, tag=args.tag,
                     triaged=False if args.untriaged else None, reopened=True if args.reopened else None)

        if args.json:
            _print(json.dumps(rows, indent=1, ensure_ascii=False) + "\n")
        else:
            _print_rows(rows)

        return 0

    if args.command == "ledger":
        rows = ledger(conn)

        for r in rows:
            _print("%-8s %-10s %-18s %s  [%s]\n" % (r["tag"], r["date"], r["disposition"], r["title"], r["origin"]))

        print("%d open of %d" % (len(rows), conn.execute("SELECT COUNT(*) FROM test").fetchone()[0]))

        return 0

    if args.command == "issues":
        if args.all:
            for item in inbox_items(conn):
                _print("%-8s %-16s %-10s %s\n" % (item.ref, item.kind[:16], "picked up" if item.receipted else
                                                  "pending", item.summary[:60]))

            print("")

            for row in receipts(conn):
                _print(receipt_line(row) + "\n")

            return 0

        for it in issues(conn, args.kind):
            _print("%-8s %-16s %s\n" % (it["ref"], (it["kind"] or "?")[:16], (it["title"] or "")[:60]))

        return 0

    if args.command == "set":
        was = set_disposition(conn, args.tag, args.disposition)
        print("%s: %s -> %s" % (args.tag.upper(), was, args.disposition))
        return 0

    if args.command == "comment":
        add_comment(conn, args.tag, args.who, args.text, args.verdict)
        print("%s: comment added" % args.tag.upper())
        return 0

    if args.command == "add-test":
        if args.steps == "-":
            steps = sys.stdin.read()
        else:
            steps = io.open(args.steps, encoding="utf-8").read()

        print(add_test(conn, args.title, args.origin, steps.replace("\r\n", "\n"), args.disposition))
        return 0

    if args.command == "file":
        print(file_issue(conn, args.kind, args.title, args.detail, raised_from=args.raised_from, build=args.build))
        return 0

    if args.command == "receipt":
        add_receipt(conn, args.ref, args.what, args.state, args.became, args.kind, args.filed)
        print("%s: receipted" % args.ref.upper())
        return 0

    if args.command == "update-receipt":
        update_receipt(conn, args.ref, args.state, args.became, args.what)
        print("%s: receipt updated" % args.ref.upper())
        return 0

    if args.command == "clear":
        clear_from_inbox(conn, args.ref)
        print("%s: out of the Inbox; its row and receipt are kept" % args.ref.upper())
        return 0

    if args.command == "notes":
        if args.name:
            _print(note(conn, args.name))
        else:
            for (name,) in conn.execute("SELECT name FROM note ORDER BY name"):
                print(name)

        return 0

    if args.command == "dump":
        written = _after_write(conn)
        print("wrote %s" % (", ".join(written) or "nothing: the dump was already the store's own rows"))
        return 0

    return 2


if __name__ == "__main__":
    sys.exit(main())
