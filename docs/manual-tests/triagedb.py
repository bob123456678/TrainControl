# -*- coding: utf-8 -*-
"""The triage store, as a database with an API on it.

Adam, 2026-08-25: "for the triage app, lets convert it to use a database and then add apis to check,
update, and file dispositions/cases. then you dont need to scan with regexes or use other tricks."

The tricks he means are real and this session produced two of them within an hour: an audit of his own
triage verdicts that reported "nothing outstanding" because its regular expression wanted a space
where the file has an asterisk, and a second one that missed twenty-nine non-passing verdicts for the
same reason. A question worth asking about the record should not be answerable wrongly by accident.


WHAT IS AUTHORITATIVE
---------------------

The database. Adam chose that over keeping the markdown as the truth, so `tests.md` and `issues.md`
are *rendered* from it and are no longer edited by hand.

They are still written, still committed, still readable and still greppable, because every `MT-###`
and `OB-###` in this repository is cited from commit messages and review documents and has to keep
resolving to something a person can open.

Since FR-136 the tests are rendered into more than one file: tests.md keeps the open entries, and each
closed one is kept in the archive of the release it shipped in, `tests-archive-<release>.md`.  Where an
entry is kept is worked out from the store every time the files are written (`home_for`), and every
file is read back into one table, so the record is the same whichever file an entry is in.


HOW THE RENDER CAN BE TRUSTED
-----------------------------

Every row keeps the exact markdown block it came from, alongside the fields parsed out of it. So
rendering is `head + "".join(blocks)` and is byte-identical to the input **by construction**, not by
careful re-serialisation that has to be kept in step with a format.

`verify` proves it: it builds the database from the current files, renders it back, and compares the
bytes. That check is the whole safety argument for the migration, and it is a command rather than a
claim.

An update rewrites the one block it touches - the same operation the app already performs to append a
comment - and the fields are re-parsed from the rewritten block, so the two halves of a row cannot
drift apart.


WHY THE PARSING IS NOT HERE
---------------------------

It reuses `triage.py`'s `TestsDoc`, `Entry`, `IssuesDoc` and `IssueItem`. Writing a second parser
would mean two things that have to agree about what a heading is, and the first disagreement would be
silent. There is one parser; this module puts a queryable store and an API in front of it.

The regular expressions therefore still exist. What changes is that they run **once, in one place, at
import time**, against a file whose shape they were written for - rather than being reinvented by
whoever needs an answer, which is where the accidents come from.
"""

from __future__ import print_function

import argparse
import io
import json
import os
import re
import datetime
import sqlite3
import sys

HERE = os.path.dirname(os.path.abspath(__file__))

if HERE not in sys.path:
    sys.path.insert(0, HERE)

import triage  # noqa: E402  - the path has to be set first


DB_FILE = os.path.join(HERE, "triage.db")
TESTS_FILE = os.path.join(HERE, "tests.md")
ISSUES_FILE = os.path.join(HERE, "issues.md")

# THE ARCHIVES (FR-136).  tests.md had grown to 1.86 MB, 770 of its 772 entries closed, and every reader paid for all of
# it.  A closed entry leaves it once the release it was closed in has shipped, into one file per release beside it, and
# the store reads them all back: an entry in an archive is as much in the record as one in tests.md.
#
# One row per release, oldest first: the archive's name and the day the release shipped.  An entry belongs to the first
# release on or after the last day anything was said on it, so a release's own day counts in it.  "pre-3.0.0" ends on
# 2.8.2's day (tag v2_8_2), the last release before 3.0.0: everything closed by then shipped before 3.0.0.
#
# ADD A ROW WHEN A RELEASE SHIPS, then run `triagedb.py archive`.  Until then what was closed since the last row stays in
# tests.md, which is where a comment on an archived entry brings it back to as well: unreleased work is still news.
ARCHIVES = [
    ("pre-3.0.0", "2026-09-26"),
    ("3.0.0", "2026-10-11"),
    ("3.0.1", "2026-10-14"),
    ("3.0.2", "2026-10-18"),
]

TESTS_NAME = "tests.md"

ARCHIVE_GLOB = "tests-archive-*.md"

# The reference documents that quote the record's counts (see render_counts)
REFERENCE_DIR = os.path.join(os.path.dirname(HERE), "reference")
BEHAVIOUR_FILE = os.path.join(REFERENCE_DIR, "behaviour.md")
QUESTIONS_FILE = os.path.join(REFERENCE_DIR, "open-questions.md")


SCHEMA = """
CREATE TABLE IF NOT EXISTS doc (
    name    TEXT PRIMARY KEY,   -- 'tests' or 'issues'
    head    TEXT NOT NULL,      -- everything before the first entry, verbatim
    tail    TEXT NOT NULL,      -- everything after the last one, verbatim
    crlf    INTEGER NOT NULL    -- how the file was written, so the render matches
);

CREATE TABLE IF NOT EXISTS test (
    tag         TEXT PRIMARY KEY,   -- MT-###
    ordinal     INTEGER NOT NULL,   -- position in the file; the render sorts by it
    anchor      TEXT NOT NULL,
    date        TEXT,
    title       TEXT,
    disposition TEXT,
    origin      TEXT,
    written     TEXT,
    reopened    INTEGER NOT NULL DEFAULT 0,
    block       TEXT NOT NULL       -- the markdown, exactly
);

CREATE TABLE IF NOT EXISTS verdict (
    tag     TEXT NOT NULL,
    seq     INTEGER NOT NULL,       -- 0 is the oldest
    who     TEXT NOT NULL,          -- 'Adam' or 'Claude'
    date    TEXT,
    kind    TEXT NOT NULL,          -- 'verdict' (one of the four) or 'note' (prose)
    verdict TEXT,                   -- the canonical verdict, NULL for a note
    note    TEXT,                   -- whatever was said
    PRIMARY KEY (tag, seq)
);

CREATE TABLE IF NOT EXISTS issue (
    ref         TEXT PRIMARY KEY,   -- OB-### or FR-###
    ordinal     INTEGER NOT NULL,
    kind        TEXT,
    title       TEXT,
    filed       TEXT,
    raised_from TEXT,
    build       TEXT,
    block       TEXT NOT NULL
);

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

CREATE INDEX IF NOT EXISTS test_by_disposition ON test (disposition);
CREATE INDEX IF NOT EXISTS verdict_by_tag ON verdict (tag, seq);
"""


# --------------------------------------------------------------------------------------------
# reading the record
# --------------------------------------------------------------------------------------------

# One place, once, at import.  The shape it matches is the one this application writes:
#
#     **Adam, 2026-08-25 (triage).** Works.
#     **Claude, 2026-08-25.** Fixed, and my first attempt was ...
#
# Every part after the name is optional because entries predate the convention and Adam types
# verdicts in by hand.  What is NOT optional is the '**' either side of the name, and requiring a
# space after '(triage).' - which is an asterisk in the file - is precisely the accident that made an
# audit this session report twenty-nine non-passing verdicts as none at all.
VERDICT_RE = re.compile(
    r"\*\*(Adam|Claude),\s*(\d{4}-\d\d-\d\d)?[^*\n]*?\*\*[ \t]*([^\n]*)\n(.*?)(?=\n\*\*|\n\*Run against|\Z)",
    re.S,
)


# The four things a triage run can conclude.  Anything else under an '**Adam, ...**' marker is prose -
# him explaining a decision, or answering a question - and calling that a verdict is how a count goes
# wrong quietly.
#
# It matters in both directions.  Reading a prose attribution as a verdict overstates how much has
# been triaged; refusing to read a verdict he typed by hand because it lacks the '(triage)' marker
# understates it.  Both have happened.
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
    """Every Adam or Claude marker in one entry's markdown, oldest first.

    Each is classified: a `kind` of 'verdict' is one of the four triage results, and 'note' is
    everything else - an explanation, an answer to a question, a ruling. Both are kept, because both
    are things he said and the record should hold them, but only the first sort answers "has this been
    triaged, and did it pass".

    :param block: the entry's raw markdown
    :return: list of (who, date, kind, verdict, note)
    """

    out = []

    for who, date, text, rest in VERDICT_RE.findall(block):
        verdict = classify(text)

        note = " ".join(rest.split())

        if verdict is None:
            # Not a result. The line itself is the start of what he said, so it belongs in the note
            # rather than being thrown away.
            out.append((who, date or "", "note", None,
                        " ".join((text.strip() + " " + note).split())))
        else:
            out.append((who, date or "", "verdict", verdict, note))

    return out


def connect(path=DB_FILE):
    """Opens the store, creating it if it is not there.

    :param path: the database file
    :return: a connection with row access by name
    """

    conn = sqlite3.connect(path)
    conn.row_factory = sqlite3.Row
    conn.executescript(SCHEMA)

    migrate(conn)

    return conn


# Columns added to a table after it first shipped. `CREATE TABLE IF NOT EXISTS` will not add them to a
# store that already exists, and this one is tracked in git, so every checkout has an old copy.
LATER_COLUMNS = [
    ("finding", "status", "TEXT"),
    ("finding", "status_note", "TEXT"),
]


def migrate(conn):
    """Adds any column that SCHEMA has gained since a store was created.

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


def load_findings(conn, rows, force=False):
    """Replaces the finding catalogue with what the scanner found.

    Adam, 2026-09-08, ruling on MON-C11 - a review folder of 143 documents that could no longer say
    who needed to do what: *"update our MT triage database to catalog each review item.  Now, no
    reference will ever be stale or lost, but useless prose will go away... Expand the database to
    capture the line number/commit ID/filename of each issue."*

    Replaced wholesale rather than merged, for the same reason `build` replaces the tests: the
    documents are the source and a row that has disappeared from them should disappear from here.

    :param conn: an open connection
    :param rows: dicts from `docs/tools/catalog-findings.py`
    :return: how many were stored
    """
    # A LOAD MUST NOT BE ABLE TO EMPTY THIS TABLE. Once docs/reviews/ is deleted the scanner finds
    # nothing, and a wholesale replace would take the only remaining copy of thousands of findings with it -
    # by way of a script that had always been safe to run. Below half is a collapse, not an edit.
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

    # Statuses are ours and the documents know nothing about them, so they are carried over rather than
    # rewritten. See the module docstring: the disposition column is what a document SAID.
    kept = {(r["ref"], r["document"]): (r["status"], r["status_note"])
            for r in conn.execute("SELECT ref, document, status, status_note FROM finding")}

    conn.execute("DELETE FROM finding")

    for r in rows:
        conn.execute(
            "INSERT OR REPLACE INTO finding"
            " (ref, document, line, severity, title, disposition, evidence, commit_id, cited_by,"
            "  status, status_note)"
            " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (r.get("ref"), r.get("document"), r.get("line") or None, r.get("severity"),
             r.get("what"), r.get("disposition"), r.get("where") or None,
             r.get("commit") or None,
             ",".join(sorted(r.get("cited", []))) or None)
            + kept.get((r.get("ref"), r.get("document")), (None, None)))

    conn.commit()

    return len(rows)


def add_findings(conn, rows):
    """Merges findings into the catalogue without touching the rest of it.

    `load_findings` REPLACES, which is right for a sweep of the whole folder and fatal for anything
    less. A review written after 2026-09-08 is a handful of documents beside a catalogue of thousands of
    findings from documents that no longer exist, so it has to be added rather than loaded.

    A row is identified by (ref, document), so re-running over the same review updates its findings in
    place rather than doubling them - which is what makes this safe to run as often as you like.

    Statuses are preserved for rows that already exist, for the same reason `load_findings` preserves
    them: the disposition is what a document said, and the status is our answer to it.

    :param conn: an open connection
    :param rows: dicts from `docs/tools/catalog-findings.py`
    :return: (added, updated)
    """
    added = 0
    updated = 0

    for r in rows:
        key = (r.get("ref"), r.get("document"))

        held = conn.execute("SELECT status, status_note FROM finding WHERE ref = ? AND document = ?",
                            key).fetchone()

        if held is None:
            added += 1
        else:
            updated += 1

        conn.execute(
            "INSERT OR REPLACE INTO finding"
            " (ref, document, line, severity, title, disposition, evidence, commit_id, cited_by,"
            "  status, status_note)"
            " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (r.get("ref"), r.get("document"), r.get("line") or None, r.get("severity"),
             r.get("what"), r.get("disposition"), r.get("where") or None,
             r.get("commit") or None,
             ",".join(sorted(r.get("cited", []))) or None)
            + (tuple(held) if held is not None else (None, None)))

    conn.commit()

    return added, updated


def prune_findings(conn, rows):
    """Deletes catalogued rows for these documents that the documents no longer make.

    `add_findings` only ever adds or updates, which is what makes it safe to re-run - and it is also why
    a row the catalogue should never have had cannot be removed by running it again.  A parser fault put
    fifteen findings into the catalogue under `X8V-validation.md` that the document never made, taken
    from a table in its Method section, and fixing the parser did not remove them (NSV-B1).

    So re-adding a folder is AUTHORITATIVE for the documents in it: whatever those documents say now is
    what the catalogue holds for them.  Nothing else is touched - a document not in `rows` keeps every
    row it has.

    **This deletes our own answers along with the row** - `status` and `status_note` go with it - so
    every removal is printed.  That is the cost of the rule, and it is the right way round: a row whose
    document does not make the finding is a row nobody can act on, and a catalogue that cannot drop one
    is a catalogue that accumulates them.

    :param conn: an open connection
    :param rows: the freshly parsed rows, from `docs/tools/catalog-findings.py`
    :return: the (document, ref) pairs removed
    """
    current = {}

    for r in rows:
        current.setdefault(r.get("document"), set()).add(r.get("ref"))

    removed = []

    for document, refs in current.items():
        held = conn.execute("SELECT ref FROM finding WHERE document = ?", (document,)).fetchall()

        for row in held:
            if row[0] not in refs:
                conn.execute("DELETE FROM finding WHERE ref = ? AND document = ?", (row[0], document))

                removed.append((document, row[0]))

    conn.commit()

    return removed


def load_dead(conn, orphans):
    """Stores the ids that are cited from the code and are findings nowhere.

    In the store rather than only in the generated file, for the same reason as everything else here:
    the documents are gone and a roll that only exists in a rendered artifact is one `rm` from lost.

    :param conn: an open connection
    :param orphans: {ref: iterable of file names}
    :return: how many
    """
    conn.execute("DELETE FROM dead_citation")

    for ref in sorted(orphans):
        conn.execute("INSERT OR REPLACE INTO dead_citation (ref, cited_by) VALUES (?, ?)",
                     (ref, ",".join(sorted(orphans[ref])) or None))

    conn.commit()

    return len(orphans)


def close_findings(conn, note, severity=None, only_open=True):
    """Sets a status on findings, without touching what the documents said.

    Adam, 2026-09-08, having been shown that the open dispositions are stale: *"in the database, mark
    all of them as closed or no longer relevant."*

    The disposition column is left exactly as it was. It is a quotation - what a document said on the
    day it was written - and rewriting a quotation to match today loses the only evidence of when the
    claim was made. The status column is the answer to it.

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

    sql = "UPDATE finding SET status = 'Closed', status_note = ? WHERE " + " AND ".join(where)

    n = conn.execute(sql, [note] + args).rowcount

    conn.commit()

    return n


FINDINGS_MIRROR = os.path.join(HERE, "findings.tsv")


def render_findings(conn, path=FINDINGS_MIRROR, force=False):
    """Writes the plain-text mirror of the catalogue, from the store.

    Two readers need this and neither can open a database. `testEveryCitationResolves` resolves every
    citation in the codebase against it - there is no SQLite driver on this project's classpath - and a
    person who has just found `RC-A1` in a comment wants one grep, not a query.

    The reviews folder was deleted on 2026-09-08, so this file and the store are the only copies of what
    thousands of findings were about. Keep both in git.

    Rendering the repository's own mirror also rewrites the counts the reference documents quote of it
    (`render_finding_counts`); a render to any other path does not.

    :param conn: an open connection
    :param path: where to write it
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

    # IT WILL NOT EMPTY A FULL MIRROR (VD15-T3, second attempt).
    #
    # This file and the `finding` table are the only record of what thousands of findings were about, and
    # this function will write a header and nothing else from a store that holds none - which is
    # exactly the store the CLI hands every command: `connect(":memory:")`, built from the markdown,
    # which has the tests and the issues and no findings at all.  The first version of the mirror
    # check called this from `sync` with that connection and emptied the file.
    #
    # `catalog-findings.py` refuses the same way for the same reason.  The refusal is here as well
    # because it is this function that does the writing.
    if os.path.exists(path):
        held = 0

        for line in io.open(path, encoding="utf-8"):
            if line.strip() and not line.startswith("#"):
                held += 1

        # NOT ONLY WHEN THE STORE IS EMPTY (VD17-C3).  The first version refused at `n == 0`, so a
        # store holding ONE finding - a half-built one, a test fixture, a connection part-way through
        # a rebuild - would still flatten every row in the file.  What is being guarded against is a
        # render from the wrong store, and that looks like a collapse rather than a change: an
        # ordinary round adds tens of rows to thousands, so an order of magnitude is the line.
        if held > 0 and n * 10 < held and not force:
            raise IOError(
                "REFUSING to write %d rows over %d. A collapse like that is not an edit, so this is"
                " not the store the mirror comes from - the CLI builds an in-memory one from the"
                " markdown for every command, and findings live only in the file database."
                " Render from triagedb.connect(), or pass force=True if you mean it." % (n, held))

    out.append("# DEAD - cited, no finding behind them")

    for r in conn.execute("SELECT ref FROM dead_citation ORDER BY ref"):
        out.append("%s\t-\t-\t-\t-" % r["ref"])

    out.append("")

    io.open(path, "w", encoding="utf-8", newline="\n").write("\n".join(out))

    # Only the repository's own mirror: a render to a temporary file is `verify_findings_mirror` asking a
    # question, and answering it must not rewrite a document.
    if os.path.abspath(path) == os.path.abspath(FINDINGS_MIRROR):
        render_finding_counts(conn)

    return n


def holdsFindings(conn):
    """Whether this connection is a store the catalogue lives in, rather than a command's copy.

    :param conn: an open connection
    :return: true when it has findings in it
    """
    return conn.execute("SELECT COUNT(*) FROM finding").fetchone()[0] > 0


def verify_findings_mirror(conn=None, path=FINDINGS_MIRROR):
    """Whether the plain-text mirror on disk is what the store would render right now (VD15-T3).

    `findings.tsv` is the only form of the catalogue Java can read - there is no SQLite driver on
    this project's classpath - so `testEveryCitationResolves` and `testTheRecordsCountTheStore`
    both read it and neither can tell a current mirror from a stale one.  A store written without
    a render leaves every one of their claims about rows that are no longer there.

    :param conn: an open connection
    :param path: the mirror
    :return: (ok, message)
    """
    import tempfile

    # THE STORE THE MIRROR COMES FROM, not whichever one the caller happens to hold.  A connection
    # with no findings is the in-memory copy the CLI builds from the markdown, and the mirror is
    # none of its business - answering "they disagree" for that store is what led to it being
    # overwritten with nothing.
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

        if io.open(path, "rb").read() == fresh:
            return True, "the mirror is the store's own rows"

        return False, ("%s is not what the store renders - regenerate it with"
                       " triagedb.render_findings(triagedb.connect())" % path)
    finally:
        # ONLY WHAT WE OPENED, AND ALWAYS (VD16-C3).  A caller's connection is the caller's to
        # close; this one's own is a second handle on the same file and was left open.
        if ours:
            conn.close()


# --------------------------------------------------------------------------------------------
# the counts the reference documents quote
# --------------------------------------------------------------------------------------------

# Three sentences quote what the record holds, and `testTheRecordsCountTheStore` reads them as these do.  One group
# per value.  A space inside a phrase may be a line break in the file: behaviour.md wraps its sentence.
ROWS_QUOTE_RE = re.compile(r"(?<![\d,])(\d[\d,]*)\s+rows\s+for\s+(\d[\d,]*)\s+findings")
ROWS_NOW_RE = re.compile(r"(?<![\d,])(\d[\d,]*) finding rows in the store now")
INBOX_QUOTE_RE = re.compile(
    r"Today it holds (\d+) entries - (\d+) OB and (\d+) FR,\s+recounted\s+from\s+the\s+file\s+on\s+"
    r"(\d{4}-\d{2}-\d{2})\s+\(((?:OB|FR)-\d+)\s+the\s+newest\)")


def _requote(path, pattern, form, choose):
    """Rewrites the values one quoted sentence carries, and only when one of them is wrong.

    The file is read and written as it stands - UTF-8, its own line endings, the sentence wrapped wherever it
    wraps - and only the matched values are replaced, so nothing else in the document can move.

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

    `behaviour.md` says how many rows the `finding` table holds and for how many distinct refs, and
    `open-questions.md` repeats the rows. A finding written up in two documents has a row for each, so the two
    numbers differ and are not interchangeable (VD15-T5).

    :param conn: an open connection to the store the findings live in
    :param behaviour_path: behaviour.md
    :param questions_path: open-questions.md
    :return: the documents it rewrote, none when both were right
    """

    rows, findings = conn.execute("SELECT COUNT(*), COUNT(DISTINCT ref) FROM finding").fetchone()

    # NOT A STORE WITHOUT FINDINGS: that is the CLI's in-memory copy, and "0 rows" would be quoted as the count.
    if rows == 0:
        raise IOError("REFUSING to quote 0 finding rows. This is not the store the findings live in -"
                      " render from triagedb.connect(), not from a connection built from the markdown.")

    changed = []

    if _requote(behaviour_path, ROWS_QUOTE_RE, '"N rows for M findings"',
                lambda now: [format(rows, ","), format(findings, ",")]):
        changed.append(behaviour_path)

    if _requote(questions_path, ROWS_NOW_RE, '"N finding rows in the store now"',
                lambda now: [format(rows, ",")]):
        changed.append(questions_path)

    return changed


def inbox_counts(issues_path=ISSUES_FILE):
    """What the Inbox holds, counted as `testTheRecordsCountTheStore` counts it.

    :param issues_path: issues.md
    :return: (OB entries, FR entries, the newest entry's reference) - entries are appended, so that is the last
    """

    text, _ = triage.read_text(issues_path)

    start = text.find("## Inbox")
    end = text.find("## What has been picked up")

    if start < 0 or end < start:
        raise IOError("%s has no '## Inbox' section followed by '## What has been picked up', which is"
                      " what its entries are counted between" % os.path.basename(issues_path))

    entries = re.findall(r"^### ((OB|FR)-\d+)", text[start:end], re.M)

    if not entries:
        raise IOError("the Inbox of %s holds no entries, so there is no newest one to name"
                      % os.path.basename(issues_path))

    return (sum(1 for ref, kind in entries if kind == "OB"),
            sum(1 for ref, kind in entries if kind == "FR"),
            entries[-1][0])


def render_inbox_count(issues_path=ISSUES_FILE, questions_path=QUESTIONS_FILE):
    """Rewrites the Inbox count `open-questions.md` quotes, from issues.md.

    The sentence carries the day it was recounted, so it gets today's date when a number or the newest entry
    changes and keeps its date when they are all right.

    :param issues_path: issues.md
    :param questions_path: open-questions.md
    :return: the documents it rewrote, none when the count was right
    """

    obs, frs, newest = inbox_counts(issues_path)

    def choose(now):
        want = [str(obs + frs), str(obs), str(frs), now[3], newest]

        if want != now:
            want[3] = datetime.date.today().isoformat()

        return want

    if _requote(questions_path, INBOX_QUOTE_RE,
                '"Today it holds T entries - X OB and Y FR, recounted from the file on YYYY-MM-DD'
                ' (REF the newest)"', choose):
        return [questions_path]

    return []


def render_counts(conn, issues_path=ISSUES_FILE, behaviour_path=BEHAVIOUR_FILE, questions_path=QUESTIONS_FILE):
    """Rewrites every count the reference documents quote, so none is written by hand.

    Filing an issue calls `render_inbox_count` and rendering the mirror calls `render_finding_counts`; this
    does both, and `triagedb.py counts` runs it after a hand edit to issues.md. Nothing is written when
    every number is already right.

    :param conn: an open connection to the store the findings live in
    :param issues_path: issues.md
    :param behaviour_path: behaviour.md
    :param questions_path: open-questions.md
    :return: the documents it rewrote
    """

    changed = render_finding_counts(conn, behaviour_path, questions_path)

    for path in render_inbox_count(issues_path, questions_path):
        if path not in changed:
            changed.append(path)

    return changed


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

    sql = "SELECT ref, document, line, severity, title, disposition, evidence, commit_id, cited_by"
    sql += " FROM finding"

    if where:
        sql += " WHERE " + " AND ".join(where)

    sql += " ORDER BY ref"

    return [dict(r) for r in conn.execute(sql, args)]

def build(conn, tests_path=TESTS_FILE, issues_path=ISSUES_FILE):
    """Fills the store from the markdown, replacing whatever was in it.

    Uses triage.py's parsers rather than its own, so there is exactly one definition of what an entry
    is. See the module docstring.

    The tests are read from tests.md AND every archive beside it (FR-136), into one table: where an entry
    is kept is not part of the record, and is worked out again by `homes` whenever the files are written.

    :param conn: an open connection
    :param tests_path: tests.md; its archives are the `tests-archive-*.md` files beside it
    :param issues_path: issues.md
    :return: (number of tests, number of issues)
    """

    # tests.md and every archive beside it (FR-136): an entry in an archive is in the record like any other.
    files = read_tests_files(tests_path)

    tests_crlf = files[0][2]

    head = triage.parse_tests_text(files[0][1])[0]

    issues_doc = triage.IssuesDoc(issues_path)

    conn.execute("DELETE FROM test")
    conn.execute("DELETE FROM verdict")
    conn.execute("DELETE FROM issue")
    conn.execute("DELETE FROM doc")

    # Which file each entry was read from, the head each file had, and any tag read twice.  TEMPORARY, so this
    # connection's own: it is a fact about the files just read rather than about the record, and the store in git
    # keeps the schema it has.
    conn.executescript(
        "CREATE TEMP TABLE IF NOT EXISTS placed (tag TEXT PRIMARY KEY, file TEXT NOT NULL);"
        "CREATE TEMP TABLE IF NOT EXISTS read_head (file TEXT PRIMARY KEY, head TEXT NOT NULL);"
        "CREATE TEMP TABLE IF NOT EXISTS duplicate (tag TEXT NOT NULL, file TEXT NOT NULL, same INTEGER NOT NULL);"
        "DELETE FROM placed; DELETE FROM read_head; DELETE FROM duplicate;")

    # The head, and everything the entries do not cover.  Kept verbatim: it holds the index tables and
    # the conventions, and this module has no business reformatting either.
    conn.execute(
        "INSERT INTO doc (name, head, tail, crlf) VALUES (?, ?, ?, ?)",
        ("tests", head, "", 1 if tests_crlf else 0),
    )

    # A TAG READ TWICE KEEPS THE FIRST COPY, tests.md's before an archive's, and says so.  `INSERT OR REPLACE` used to
    # keep the second without a word.  Two copies that differ are refused at the next write (render_to_disk): which one
    # holds the newer comment is not something a parser can know.
    sequences = []
    first = {}

    for name, text, _ in files:
        file_head, file_entries = triage.parse_tests_text(text)

        conn.execute("INSERT INTO read_head (file, head) VALUES (?, ?)", (name, file_head))

        kept = []

        for e in file_entries:
            if e.tag in first:
                conn.execute("INSERT INTO duplicate (tag, file, same) VALUES (?, ?, ?)",
                             (e.tag, name, 1 if first[e.tag].block == e.block else 0))
                continue

            first[e.tag] = e
            kept.append(e)

            conn.execute("INSERT INTO placed (tag, file) VALUES (?, ?)", (e.tag, name))

        sequences.append(kept)

    entries = _merge_by_tag(sequences)

    for i, e in enumerate(entries):
        conn.execute(
            "INSERT OR REPLACE INTO test"
            " (tag, ordinal, anchor, date, title, disposition, origin, written, reopened, block)"
            " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (e.tag, i, e.anchor, e.date, e.title, e.disposition, e.origin, e.written,
             1 if e.reopened else 0, e.block),
        )

        for seq, (who, date, kind, verdict, note) in enumerate(verdicts_in(e.block)):
            conn.execute(
                "INSERT OR REPLACE INTO verdict (tag, seq, who, date, kind, verdict, note)"
                " VALUES (?, ?, ?, ?, ?, ?, ?)",
                (e.tag, seq, who, date, kind, verdict, note),
            )

    issues_text, issues_crlf = triage.read_text(issues_path)

    conn.execute(
        "INSERT INTO doc (name, head, tail, crlf) VALUES (?, ?, ?, ?)",
        ("issues", issues_text, "", 1 if issues_crlf else 0),
    )

    for i, it in enumerate(issues_doc.pending):
        conn.execute(
            "INSERT OR REPLACE INTO issue"
            " (ref, ordinal, kind, title, filed, raised_from, build, block)"
            " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            (it.ref, i, it.kind, it.summary, it.filed_at, it.raised_from, it.build, it.detail),
        )

    conn.commit()

    return len(entries), len(issues_doc.pending)


def archive_file(name):
    """The file one archive is written to.

    :param name: a name from ARCHIVES - "3.0.1", "pre-3.0.0"
    :return: its basename, beside tests.md
    """

    return "tests-archive-%s.md" % name


def read_tests_files(tests_path=TESTS_FILE):
    """tests.md and every archive beside it, in the order the store reads them.

    tests.md first, then the archives ARCHIVES names in release order, then any other `tests-archive-*.md` on disk, by
    name.  A file ARCHIVES does not name is still READ, so that nothing in it can drop out of the record; `verify` says
    it should not be there, and the next write puts its entries where the rule says.

    :param tests_path: tests.md; the archives are the files beside it
    :return: [(basename, text with \\n endings, crlf)]
    """

    import glob

    text, crlf = triage.read_text(tests_path)

    out = [(TESTS_NAME, text, crlf)]

    folder = os.path.dirname(os.path.abspath(tests_path))

    known = [archive_file(name) for name, _ in ARCHIVES]

    on_disk = set(os.path.basename(p) for p in glob.glob(os.path.join(folder, ARCHIVE_GLOB)))

    for name in known + sorted(on_disk - set(known)):
        if name in on_disk:
            text, crlf = triage.read_text(os.path.join(folder, name))

            out.append((name, text, crlf))

    return out


def tests_paths(tests_path=TESTS_FILE):
    """Every file the tests are kept in that is on disk now: tests.md, then its archives.

    For a reader that wants the text rather than the store - the triage app's ref counter and its separator check.

    :param tests_path: tests.md
    :return: absolute paths
    """

    folder = os.path.dirname(os.path.abspath(tests_path))

    return [tests_path if name == TESTS_NAME else os.path.join(folder, name)
            for name, _, _ in read_tests_files(tests_path)]


def _merge_by_tag(sequences):
    """One order for the entries of several files, which keeps each file's own order.

    Repeatedly takes, from the heads of the files, the entry with the lowest tag.  Within a file the order is untouched,
    so each file renders back as it was read; across files it is roughly tag order, so an entry that moves into a file
    lands among its neighbours by number rather than at the bottom.  Read from one file - tests.md before any archive
    existed - it is that file's order exactly.

    :param sequences: one list of entries per file, in file order
    :return: every entry, once
    """

    at = [0] * len(sequences)
    out = []

    while True:
        best = None

        for i, seq in enumerate(sequences):
            if at[i] < len(seq) and (best is None or
                                     _tag_order(seq[at[i]].tag) < _tag_order(sequences[best][at[best]].tag)):
                best = i

        if best is None:
            return out

        out.append(sequences[best][at[best]])

        at[best] += 1


ISO_DAY_RE = re.compile(r"^\d{4}-\d\d-\d\d$")


def home_for(disposition, last_said):
    """The file an entry belongs in: THE MOVE RULE.

    Open - anything but fixed validated or superseded - is tests.md.  Closed is the archive of the release it shipped in:
    the first in ARCHIVES whose day is on or after the last day anything was said on it.  Closed after the last release
    is still tests.md, because it has not shipped.

    So a closed entry that is commented on, or reopened, comes back to tests.md the next time the store is written, and
    goes out again, into the archive of the release after, once that release is in ARCHIVES.

    :param disposition: the entry's Disposition line
    :param last_said: the latest of its heading date and every dated Adam or Claude marker in it
    :return: a basename
    """

    if (disposition or "").strip().lower() not in (triage.VALIDATED, triage.SUPERSEDED):
        return TESTS_NAME

    # An entry with no readable date cannot be placed in a release; tests.md is where it will be looked at.
    if not ISO_DAY_RE.match(last_said or ""):
        return TESTS_NAME

    for name, day in ARCHIVES:
        if last_said <= day:
            return archive_file(name)

    return TESTS_NAME


def homes(conn):
    """Where the rule puts every entry in the store.

    The date is the store's own: the heading's and the verdict table's, which is every `**Adam, YYYY-MM-DD` and
    `**Claude, YYYY-MM-DD` marker the one parser found.  A date mentioned in prose is not something said on the day.

    :param conn: an open connection
    :return: {tag: basename}
    """

    out = {}

    for r in conn.execute(
            "SELECT t.tag, t.disposition, t.date,"
            " (SELECT MAX(v.date) FROM verdict v WHERE v.tag = t.tag AND v.date != '') AS said"
            " FROM test t"):
        dates = [d for d in (r["date"], r["said"]) if d and ISO_DAY_RE.match(d)]

        out[r["tag"]] = home_for(r["disposition"], max(dates) if dates else None)

    return out


ARCHIVES_HEADING = "## The archives"


def _release_window(name):
    """How an archive's header describes the releases it holds.

    :param name: a name from ARCHIVES
    :return: (the sentence's subject, the day it ends)
    """

    names = [n for n, _ in ARCHIVES]

    at = names.index(name)

    day = ARCHIVES[at][1]

    if at == 0:
        return "shipped before 3.0.0 - closed on or before %s, the day 2.8.2 was released" % day, day

    return ("shipped in %s - closed after %s and on or before %s, the day %s was released"
            % (name, ARCHIVES[at - 1][1], day, name)), day


def render_archive_index(placement):
    """The section of tests.md that says where the closed entries went, and keeps every one's address.

    `tests.md#mt-NNN` is how every link to an entry was written - from issues.md's receipts, from other entries, from
    findings.tsv - and some of those are quotations nobody may edit.  So every archived tag keeps an anchor here, on a
    line linking to where it is now: the old link still lands, one click from the entry.  Generated on every write, so
    a line never names a file the entry has left.

    :param placement: {tag: basename}
    :return: the section, beginning with a newline and ending with the closing rule
    """

    counts = {}

    for name in placement.values():
        counts[name] = counts.get(name, 0) + 1

    lines = ["", ARCHIVES_HEADING, "",
             "A closed entry - **fixed validated** or **superseded** - leaves this file once the release it was closed",
             "in has shipped, and is kept whole, anchor and Comments and all, in that release's archive.  The archives",
             "are written from the store with this file, so nobody moves an entry by hand: one that is reopened, or",
             "commented on, comes back here by itself (README.md, \"The archives\").  The triage app shows them under",
             "**Show > everything, validated included**, and an answer to one is written where it is.",
             "",
             "Agents: ask `triagedb.py` or `triage.py tests --all` - never read these files whole.",
             ""]

    for name, day in reversed(ARCHIVES):
        lines.append("- [%s](%s) - %d entr%s, %s" % (
            archive_file(name), archive_file(name), counts.get(archive_file(name), 0),
            "y" if counts.get(archive_file(name), 0) == 1 else "ies", _release_window(name)[0]))

    archived = sorted((tag for tag, name in placement.items() if name != TESTS_NAME), key=_tag_order)

    lines += ["",
              "Every archived entry keeps its address in this file: a link to `tests.md#mt-NNN` lands on its line",
              "below, which links to where it is now.",
              ""]

    for tag in archived:
        lines.append('- <a id="%s"></a>[%s](%s#%s)' % (tag.lower(), tag, placement[tag], tag.lower()))

    return "\n".join(lines) + "\n\n---\n"


def render_archive_head(name, placement, blocks):
    """The header of one archive, all of it generated.

    It carries an anchor for every entry its own entries link to as `#mt-NNN` that is kept in another file, so a link
    written when both were in tests.md still lands - on a line linking to where the other one is now.

    :param name: a name from ARCHIVES
    :param placement: {tag: basename}
    :param blocks: the blocks this archive holds, in order
    :return: everything before the first entry
    """

    here = archive_file(name)

    linked = set()

    for block in blocks:
        for m in re.finditer(r"\]\(#(mt-\d+)\)", block):
            linked.add(m.group(1).upper())

    elsewhere = sorted((tag for tag in linked if placement.get(tag) not in (None, here)), key=_tag_order)

    window, _ = _release_window(name)

    lines = ["# Manual tests - archive %s" % name, "",
             "The entries of [tests.md](tests.md) that were closed - **fixed validated** or **superseded** - and",
             "%s." % window, "",
             "GENERATED from the store by `triagedb.py`, with tests.md - do not edit this file by hand.  Every entry is",
             "exactly as it was written and keeps its tag for life.  One that is reopened, or commented on, moves back",
             "to tests.md by itself (README.md, \"The archives\").",
             "",
             "Agents: ask `triagedb.py` or `triage.py tests --all` - never read this file whole.",
             "",
             "%d entr%s." % (len(blocks), "y" if len(blocks) == 1 else "ies")]

    if elsewhere:
        lines += ["", "Linked from the entries below and kept in another file:", ""]

        for tag in elsewhere:
            lines.append('- <a id="%s"></a>[%s](%s#%s)' % (tag.lower(), tag, placement[tag], tag.lower()))

    return "\n".join(lines) + "\n\n---\n\n## The tests\n\n\n"


def _with_archive_index(head, index):
    """tests.md's head with the archives section put in, or put back.

    It sits between the Ledger and "## The tests"; a head written before there were archives has none, and gets one.

    :param head: the head as stored
    :param index: render_archive_index's section
    :return: the head to write
    """

    end = head.find("\n## The tests")

    if end < 0:
        raise IOError("cannot find '## The tests' in the head of tests.md, which is where the archives section goes")

    start = head.find("\n" + ARCHIVES_HEADING)

    if start < 0 or start > end:
        start = end

    return head[:start] + index + head[end:]


def render_files(conn):
    """tests.md and every archive, as the store says they should be.

    Each entry goes where `homes` puts it, in store order, so every file is a function of the store: render it twice and
    it is the same twice, and render it after any write and every entry is where the rule says.

    :param conn: an open connection
    :return: [(basename, text with \\n endings)], tests.md first; every archive ARCHIVES names, an empty one included
    """

    row = conn.execute("SELECT head FROM doc WHERE name = 'tests'").fetchone()

    if row is None:
        raise IOError("the store has no tests document - run build first")

    placement = homes(conn)

    names = [TESTS_NAME] + [archive_file(name) for name, _ in ARCHIVES]

    blocks = dict((name, []) for name in names)

    for r in conn.execute("SELECT tag, block FROM test ORDER BY ordinal"):
        blocks[placement[r["tag"]]].append(r["block"])

    out = [(TESTS_NAME, _with_archive_index(row["head"], render_archive_index(placement))
            + "".join(blocks[TESTS_NAME]))]

    for name, _ in ARCHIVES:
        mine = blocks[archive_file(name)]

        out.append((archive_file(name), render_archive_head(name, placement, mine) + "".join(mine)))

    return out


def render_tests(conn):
    """tests.md as the store says it should be.

    :param conn: an open connection
    :return: the file's whole text
    """

    return render_files(conn)[0][1]


def render_as_read(conn):
    """Every file as it was READ: its own head, and the entries read from it, in the store's order.

    The proof that the store holds everything the files did.  `render_files` cannot be it, because it puts each entry
    where the rule says, which after a hand edit is not where it was.

    :param conn: a connection `build` filled
    :return: [(basename, text with \\n endings)], in the order the files were read
    """

    out = []

    for r in conn.execute("SELECT file, head FROM read_head ORDER BY rowid"):
        blocks = [b["block"] for b in conn.execute(
            "SELECT t.block FROM test t JOIN placed p ON p.tag = t.tag WHERE p.file = ? ORDER BY t.ordinal",
            (r["file"],))]

        out.append((r["file"], r["head"] + "".join(blocks)))

    return out


LEDGER_HEADING = "## Ledger - where your attention is needed"

LEDGER_INTRO = (
    "Everything still asking you for something - **needs test** and **fixed unvalidated**, in tag\n"
    "order.  **Superseded** is off it as well as **fixed validated**: nobody ran a superseded entry\n"
    "and nothing was proved, but it is not outstanding either, and this is a list of what is\n"
    "outstanding.\n"
    "\n"
    "GENERATED from the store by `triagedb.regenerate_ledger` - do not hand-edit it, because the\n"
    "next regeneration cannot know what you meant.  Adam, 2026-09-09: *\"regenerate it based on\n"
    "authoritative data.\"*  A note you want kept against an entry belongs in that entry's Comments,\n"
    "which is where `triage.py verify-ledger` reads the truth from anyway."
)

LEDGER_COLUMNS = "| Tag | Date | What | Disposition | From |\n|---|---|---|---|---|"


def _ledger_cell(text):
    """One table cell's worth of text, with anything that would break the row escaped.

    A pipe inside a title would split the row into six cells, and triage.py's own ledger parser
    COUNTS cells - so such a row would be reported as malformed rather than as wrong, which is a
    worse answer. Newlines cannot appear in a row at all.

    :param text: the field
    :return: the cell body
    """

    return (text or "").replace("|", "\\|").replace("\r", " ").replace("\n", " ").strip()


def _tag_order(tag):
    """Sort key putting MT-9 before MT-10, which a string sort does not.

    :param tag: MT-###
    :return: (prefix, number)
    """

    match = re.match(r"([A-Za-z]+)-0*(\d+)$", tag or "")

    return (match.group(1).upper(), int(match.group(2))) if match else (tag or "", 0)


def render_ledger(conn):
    """The Ledger section as the store says it should be, heading to closing rule.

    THE SHAPE IS triage.py's LEDGER PARSER'S, and that is the point of writing it here rather than by
    hand: five cells, the href exactly '#' plus the lowercased tag, and the What and From columns
    copied VERBATIM from the entry rather than shortened. `verify-ledger` tolerates a shortened What -
    README.md calls the column "one line about it" - and a generator has no business spending that
    tolerance, because a generated line that merely passes the check is a line nobody can trust.

    :param conn: an open connection
    :return: the section text, beginning with a newline and ending with the closing rule
    """

    rows = [dict(r) for r in conn.execute(
        "SELECT tag, date, title, disposition, origin FROM test")]

    open_rows = sorted(
        (r for r in rows if (r["disposition"] or "").strip().lower()
         not in (triage.VALIDATED, triage.SUPERSEDED)),
        key=lambda r: _tag_order(r["tag"]))

    lines = []

    for r in open_rows:
        lines.append("| [%s](#%s) | %s | %s | %s | %s |" % (
            r["tag"],
            r["tag"].lower(),
            _ledger_cell(r["date"]),
            _ledger_cell(r["title"]),
            _ledger_cell(r["disposition"]),
            _ledger_cell(r["origin"]),
        ))

    validated = sum(1 for r in rows
                    if (r["disposition"] or "").strip().lower() == triage.VALIDATED)
    superseded = sum(1 for r in rows
                     if (r["disposition"] or "").strip().lower() == triage.SUPERSEDED)

    # COUNTED, NOT COPIED. The sentence this replaces said "235 of 262" against a file holding 340
    # entries, and read as though the superseded ones were still on the list. Every number here is a
    # count of the rows the table above was built from.
    #
    # Counted over the ARCHIVES as well (FR-136): the store is built from them too, so these are every entry ever
    # written, wherever it is kept now.
    summary = ("Everything else - %d of %d - needs nothing from you unless the area changes again:\n"
               "%d **fixed validated** and %d **superseded**.  Those that have shipped are in the archives,\n"
               "below." % (validated + superseded, len(rows), validated, superseded))

    return "\n%s\n\n%s\n\n%s\n%s\n\n%s\n\n---\n" % (
        LEDGER_HEADING, LEDGER_INTRO, LEDGER_COLUMNS, "\n".join(lines), summary)


def ledger_end(text):
    """Where the Ledger section of tests.md ends: at the archives section, or at "## The tests" before there was one.

    :param text: the head, or the whole file
    :return: the offset of the newline before the next section's heading, or -1
    """

    ends = [at for at in (text.find("\n" + ARCHIVES_HEADING), text.find("\n## The tests")) if at >= 0]

    return min(ends) if ends else -1


def regenerate_ledger(conn, tests_path=TESTS_FILE, write=True):
    """Rewrites the Ledger table from the store, and writes tests.md.

    Adam, 2026-09-09: *"regenerate it based on authoritative data."*  `triage.py verify-ledger`
    reported 31 stale rows and 8 disposition drifts against a hand-maintained table, and that command
    is deliberately READ-ONLY: a ledger row was allowed to carry a hand note, and a wholesale rewrite
    would erase one without knowing it was there. This is the other half - the rewrite, done from the
    store - and it retires the hand-note convention in the intro it writes rather than silently
    breaking it.

    THE LEDGER LIVES IN THE HEAD. `render_tests` is `head + "".join(blocks)` and the Ledger is part of
    the head, so this updates the doc row and re-renders. That is what keeps `verify`'s byte-for-byte
    guarantee true afterwards: the file and the store agree because the file was written from the
    store.

    :param conn: an open connection
    :param tests_path: tests.md
    :param write: False to change the store only, for a caller that wants to look first
    :return: a dict saying what changed
    """

    row = conn.execute("SELECT head FROM doc WHERE name = 'tests'").fetchone()

    if row is None:
        raise IOError("the store has no tests document - run build first")

    head = row["head"]

    start = head.find("\n## Ledger")
    end = ledger_end(head)

    if start < 0 or end < 0 or end <= start:
        raise IOError("cannot find the Ledger section in the head of tests.md"
                      " (looked for '## Ledger' followed later by '## The archives' or '## The tests')")

    was = head[start:end]
    now = render_ledger(conn)

    conn.execute("UPDATE doc SET head = ? WHERE name = 'tests'",
                 (head[:start] + now + head[end:],))
    conn.commit()

    if write:
        render_to_disk(conn, tests_path)

    def _tags(section):
        return set(re.findall(r"^\| \[([A-Za-z]+-\d+)\]", section, re.M))

    before, after = _tags(was), _tags(now)

    return {
        "rows_before": len(before),
        "rows_after": len(after),
        "removed": sorted(before - after, key=_tag_order),
        "added": sorted(after - before, key=_tag_order),
        "changed": was != now,
    }


def verify(conn, tests_path=TESTS_FILE):
    """Proves the store can reproduce the file it was built from, byte for byte.

    This is the safety argument for having a database at all, and it is a command rather than a claim
    because the claim is the kind that is easy to make and hard to notice being wrong.

    :param conn: an open connection
    :param tests_path: the file to compare against
    :return: (ok, message)
    """

    # EVERY FILE (FR-136): tests.md and each archive, and no archive on disk that the store would not write.
    on_disk = dict((name, text) for name, text, _ in read_tests_files(tests_path))

    rendered = render_files(conn)

    total = 0

    for name, text in rendered:
        total += len(text)

        why = _first_difference(name, on_disk.get(name), text)

        if why:
            return False, why + _moves_note(conn)

    stray = sorted(set(on_disk) - set(name for name, _ in rendered))

    if stray:
        return False, ("%s %s on disk and ARCHIVES names no such archive, so nothing will write it again; its entries"
                       " are now in the files the rule puts them in - delete it" % (", ".join(stray),
                                                                                    "is" if len(stray) == 1 else "are"))

    return True, ("tests.md and its %d archives render byte-identically from the store (%d chars)"
                  % (len(rendered) - 1, total))


def _first_difference(name, on_disk, rendered):
    """Where one file and its render part, said so it can be acted on.

    :param name: the file's basename
    :param on_disk: its text, or None when it is not there
    :param rendered: what the store renders for it
    :return: None when they are the same, else a sentence
    """

    if on_disk == rendered:
        return None

    if on_disk is None:
        return "%s is not on disk, and the store renders %d chars for it" % (name, len(rendered))

    # Say WHERE, because "they differ" on a 10,000-line file is not actionable.
    for i, (a, b) in enumerate(zip(on_disk, rendered)):
        if a != b:
            return ("%s: first difference at character %d: file has %r, store renders %r\n"
                    "  around: %r" % (name, i, a, b, on_disk[max(0, i - 60):i + 60]))

    return ("%s: same prefix, different length: file %d chars, store renders %d"
            % (name, len(on_disk), len(rendered)))


def misplaced(conn):
    """The entries that are not in the file the rule puts them in, as the files were read.

    :param conn: a connection `build` filled
    :return: [(tag, file it is in, file it belongs in)], in store order
    """

    placement = homes(conn)

    return [(r["tag"], r["file"], placement[r["tag"]]) for r in conn.execute(
        "SELECT p.tag, p.file FROM placed p JOIN test t ON t.tag = p.tag ORDER BY t.ordinal")
        if placement[r["tag"]] != r["file"]]


def _moves_note(conn):
    """What `archive` would move, for a message saying the files are not as the store renders them.

    :param conn: a connection `build` filled
    :return: a line to append, or nothing
    """

    try:
        moves = misplaced(conn)
    except sqlite3.OperationalError:
        # A connection that did not come through `build` has no record of where anything was read from.
        return ""

    if not moves:
        return ""

    return ("\n  %d entr%s not where the rule puts %s (%s%s): `triagedb.py archive` moves %s"
            % (len(moves), "y is" if len(moves) == 1 else "ies are", "it" if len(moves) == 1 else "them",
               ", ".join("%s %s -> %s" % m for m in moves[:5]), ", ..." if len(moves) > 5 else "",
               "it" if len(moves) == 1 else "them"))


def verify_as_read(conn, tests_path=TESTS_FILE):
    """Proves the store holds every entry of every file it was built from, byte for byte, wherever each was read.

    `verify` asks whether the files are what the store would WRITE; after a hand edit - a comment typed into an
    archived entry, a row added to ARCHIVES - they are not, and nothing has been lost.  This asks the other question:
    whether writing the store out could lose a word.  Each file is rendered from the entries read from it, under the
    head it had, and compared.

    :param conn: a connection `build` filled from these files
    :param tests_path: tests.md; the archives are beside it
    :return: (ok, message)
    """

    differing = [r["tag"] for r in conn.execute("SELECT DISTINCT tag FROM duplicate WHERE same = 0")]

    if differing:
        return False, ("%s %s in two files with different text, and the store keeps only the first copy: say which"
                       " is right by deleting the other by hand" % (", ".join(differing),
                                                                     "is" if len(differing) == 1 else "are"))

    on_disk = dict((name, text) for name, text, _ in read_tests_files(tests_path))

    for name, text in render_as_read(conn):
        why = _first_difference(name, on_disk.get(name), text)

        if why:
            # A copy read twice is the one way a file can hold more than the store does, and it is harmless only when
            # the two are the same - which the check above has already established.
            if conn.execute("SELECT 1 FROM duplicate WHERE file = ?", (name,)).fetchone():
                continue

            return False, why

    return True, "the store holds every entry of every file, byte for byte (%d files)" % len(on_disk)



# WHERE A FINDING'S STATUS NOTE SAYS ITS WORK LIVES (GSB-C3).  "Now tracked as OB-253 in the Inbox", "In the Inbox as
# OB-237", "filed as OB-300", "deferred as OB-306": each names an Inbox item, and the Inbox is append-only - an item
# stays in it for life, see triage.IssuesDoc.load - so the OB it names has to be a `### OB-n` heading there, today and
# from then on.  A mention in passing ("Adam, 2026-09-22, on OB-248: ...") claims nothing and is not read.
INBOX_CLAIM_RE = re.compile(r"\b(?:[Tt]racked as|[Ii]n the Inbox as|filed as|deferred as)\s+(OB-\d+)\b")


def verify_inbox_claims(store_path=DB_FILE, issues_path=ISSUES_FILE):
    """Whether every OB a finding's status note says it is tracked as is an item in the Inbox or receipted (GSB-C3).

    Nothing checked these.  A status note is the store's own answer, kept through every reload of the catalogue, and
    "tracked as OB-n" is all it says about where the work went - so a note naming an OB that was never filed, or a
    slip of the keyboard, sends its reader to nothing.  Java cannot catch it: there is no SQLite driver on the
    classpath, and `findings.tsv` does not carry the note.

    Read from the file directly, not through `connect`, which would create a store that is not there and migrate one
    that is: this only reads.

    :param store_path: the findings store - the default one, or a copy
    :param issues_path: issues.md, whose Inbox headings are the items
    :return: (ok, message, offenders), offenders as (ref, document, OB) for each claim that names no Inbox item
    """
    if not os.path.exists(store_path):
        return False, store_path + " is not there, and the status notes live only in it", []

    text, _ = triage.read_text(issues_path)

    span = triage.inbox_span(text)

    section = text[span[0]:span[1]] if span else ""

    # The one parser's headings, not a second regular expression written for the occasion (see the module docstring)
    inbox = set(m.group(1) for m in triage.ISSUE_ITEM_RE.finditer(section))

    # A done item leaves the Inbox and keeps its receipt (Adam, 2026-09-24), so the receipt answers the claim too
    receipted = set(triage.IssuesDoc(issues_path).picked_up_refs)

    conn = sqlite3.connect(store_path)

    try:
        rows = conn.execute("SELECT ref, document, status_note FROM finding"
                            " WHERE status_note LIKE '%OB-%' ORDER BY ref, document").fetchall()
    finally:
        conn.close()

    claims = 0
    offenders = []

    for ref, document, note in rows:
        for m in INBOX_CLAIM_RE.finditer(note):
            claims += 1

            if m.group(1) not in inbox and m.group(1) not in receipted:
                offenders.append((ref, document, m.group(1)))

    if offenders:
        return False, ("%d of %d status-note claims name an OB neither in the Inbox nor receipted in %s:"
                       % (len(offenders), claims, issues_path)), offenders

    return True, ("all %d status-note claims name an item in the Inbox or a receipt (%d OB items there)"
                  % (claims, sum(1 for ref in inbox if ref.startswith("OB-")))), []


# --------------------------------------------------------------------------------------------
# the API: check, update, file
# --------------------------------------------------------------------------------------------

def sync(conn=None):
    """Rebuilds the store from the markdown and proves the result renders back identically.

    The migration and the refresh are the same operation. It refuses to leave behind a store that
    cannot reproduce what it was built from, because such a store would overwrite the record the
    moment anything wrote it out.

    :param conn: an open connection, or None to use the default file
    :return: (tests, issues)
    """

    own = conn is None

    conn = conn or connect()

    try:
        counts = build(conn)

        ok, message = verify_as_read(conn)

        if not ok:
            raise IOError("the store does not render back to what it was built from: " + message)

        # AND WHETHER EVERY ENTRY IS WHERE THE RULE PUTS IT (FR-136).  Said, not refused: an entry in the wrong file has
        # lost nothing - a comment typed into an archived entry by hand, or a release just added to ARCHIVES, is how one
        # gets there - and the next write of the store puts it right.  `verify` is the command that fails on it.
        placed_ok, placed_message = verify(conn)

        if not placed_ok:
            print("the files are not yet as the store writes them - " + placed_message)

        # AND THE MIRROR JAVA READS IS THE STORE'S OWN ROWS (VD15-T3).  It is derived, so a
        # disagreement is repaired rather than refused - but never silently: a sync that rewrites
        # the file every time means something is writing the store without rendering it.
        #
        # **ONE STORE, ASKED AND THEN WRITTEN FROM (VD16-B1).**  The first version asked the file
        # store when this connection had no findings - which from the CLI is always, since `main`
        # builds `connect(":memory:")` - and then repaired from `conn` regardless.  So the repair
        # either raised out of `sync` (the writer's refusal, doing its job on a store that should
        # never have been handed to it) or, with no mirror on disk for that refusal to protect,
        # wrote a header over nothing and called it a repair.  The store is chosen once here.
        store = conn if holdsFindings(conn) else connect()

        try:
            fresh, note = verify_findings_mirror(store)

            if not fresh:
                render_findings(store)

                print("findings.tsv was not the store's own rows and has been regenerated - " + note)
        finally:
            # AND CLOSED, WHICH IS THE RULE THE FUNCTION BELOW STATES (VD17-C2).  This opens a second
            # handle on the file store every time the CLI runs `sync`, because the CLI's own store is
            # the in-memory one - and left it open, one function above the comment saying a caller's
            # connection is the caller's to close.
            if store is not conn:
                store.close()

        return counts

    finally:
        if own:
            conn.close()


def check(conn, disposition=None, verdict=None, triaged=None, reopened=None, tag=None):
    """Tests matching whatever is asked, each with its latest verdict.

    Every argument is optional and they combine. This is the query that replaces reading the file with
    a regular expression, and the reason it can be trusted where that could not is that the
    classifying happened once, at build time, over the whole record - not per question, per caller.

    :param disposition: 'needs test', 'fixed unvalidated', 'fixed validated', 'superseded'
    :param verdict: one of VERDICTS' values, matched against the LATEST Adam verdict
    :param triaged: True for entries Adam has ruled on, False for those he never has
    :param reopened: True for entries he ruled on that have changed since
    :param tag: one specific MT-###
    :return: list of dicts
    """

    sql = ["SELECT t.tag, t.title, t.disposition, t.reopened,"
           " (SELECT v.verdict FROM verdict v WHERE v.tag = t.tag AND v.who = 'Adam'"
           "  AND v.kind = 'verdict' ORDER BY v.seq DESC LIMIT 1) AS latest,"
           " (SELECT v.date FROM verdict v WHERE v.tag = t.tag AND v.who = 'Adam'"
           "  AND v.kind = 'verdict' ORDER BY v.seq DESC LIMIT 1) AS latest_date"
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
        sql.append(" AND EXISTS (SELECT 1 FROM verdict v WHERE v.tag = t.tag"
                   " AND v.who = 'Adam' AND v.kind = 'verdict')")

    if triaged is False:
        sql.append(" AND NOT EXISTS (SELECT 1 FROM verdict v WHERE v.tag = t.tag"
                   " AND v.who = 'Adam' AND v.kind = 'verdict')")

    sql.append(" ORDER BY t.ordinal")

    rows = [dict(r) for r in conn.execute("".join(sql), args)]

    if verdict:
        rows = [r for r in rows if (r["latest"] or "") == verdict]

    return rows


def stale_queue(conn):
    """Entries sitting in Adam's queue that he has already passed.

    An entry he judged `Works`, that nothing has reopened, and that still reads `needs test`. There is
    nothing for him to do with one: he has run it, it passed, and it is taking a line in the list of
    things he has not run.

    Four were in this state on 2026-09-08, the oldest since 2026-08-24, and two carried a follow-up he
    had raised in the same breath that was never filed as an issue - which is the reason to look at
    these rather than just close them.

    :param conn: an open connection
    :return: the rows
    """
    passed = ("Works", "Works, with notes")

    # BOTH DISPOSITIONS THE LIST SHOWS (2026-09-29).  This read "needs test" alone, and the list shows "fixed
    # unvalidated" as well: MT-571, passed on 2026-09-26, stayed in Adam's queue three days, with seven more of the
    # kind beside it by the next triage.  A passed entry still in the queue is the same miss under either word.
    return [r for disposition in ("needs test", "fixed unvalidated")
            for r in check(conn, disposition=disposition, triaged=True, reopened=False)
            if (r["latest"] or "") in passed]


def history(conn, tag):
    """Everything said about one test, oldest first.

    :param conn: an open connection
    :param tag: MT-###
    :return: list of dicts
    """

    return [dict(r) for r in conn.execute(
        "SELECT seq, who, date, kind, verdict, note FROM verdict WHERE tag = ? ORDER BY seq",
        (tag.upper(),))]


def issues(conn, kind=None):
    """The items in the Inbox that have not been picked up.

    :param conn: an open connection
    :param kind: 'bug' or 'feature request', or None for both
    :return: list of dicts
    """

    if kind:
        rows = conn.execute("SELECT * FROM issue WHERE kind = ? ORDER BY ordinal", (kind,))
    else:
        rows = conn.execute("SELECT * FROM issue ORDER BY ordinal")

    return [dict(r) for r in rows]


def set_disposition(conn, tag, disposition, tests_path=TESTS_FILE):
    """Changes one test's disposition, in the store and in the file.

    The block is rewritten and then RE-PARSED, so the row's fields and its markdown cannot disagree -
    which is the failure a store holding both would otherwise invite, and the one that would make the
    whole design worse than the markdown it replaced.

    :param conn: an open connection
    :param tag: MT-###
    :param disposition: the new disposition
    :param tests_path: where to render
    :return: the previous disposition
    """

    # triage.py's own set, derived there from the colour table so the two cannot disagree. Asked of it
    # rather than re-typed here, for the same reason the parsing is not duplicated.
    if disposition not in triage.VALID_DISPOSITIONS:
        raise ValueError("%r is not one of %s" % (disposition, sorted(triage.VALID_DISPOSITIONS)))

    row = conn.execute("SELECT block, disposition FROM test WHERE tag = ?",
                       (tag.upper(),)).fetchone()

    if row is None:
        raise KeyError("no such test: %s" % tag)

    # The TRAILING whitespace is kept, and it is not cosmetic: two spaces at the end of a line is
    # markdown's hard line break, and these field lines use it to stack. Dropping it merges the
    # Disposition line into the one below - invisible in the store, visible in the rendered file.
    #
    # Caught only because the self test sets a value, sets the old one back, and demands the bytes
    # match. Neither write looked wrong on its own.
    block, changed = re.subn(r"(?m)^(\*\*Disposition:\*\*[ \t]*).*?([ \t]*)$",
                             lambda m: m.group(1) + disposition + m.group(2),
                             row["block"], count=1)

    if not changed:
        raise IOError("%s has no Disposition line to change" % tag)

    replace_block(conn, tag.upper(), block)

    render_to_disk(conn, tests_path)

    return row["disposition"]


def add_comment(conn, tag, who, text, verdict=None, tests_path=TESTS_FILE):
    """Appends a comment to one test, in the store and in the file.

    :param conn: an open connection
    :param tag: MT-###
    :param who: 'Adam' or 'Claude'
    :param text: what to say underneath
    :param verdict: one of VERDICTS' values, to make this a triage result rather than a note
    :param tests_path: where to render
    :return: the comment as written
    """

    if verdict is not None and verdict not in VERDICTS.values():
        raise ValueError("%r is not one of %s" % (verdict, sorted(VERDICTS.values())))

    row = conn.execute("SELECT block FROM test WHERE tag = ?", (tag.upper(),)).fetchone()

    if row is None:
        raise KeyError("no such test: %s" % tag)

    stamp = datetime.date.today().isoformat()

    head = "**%s, %s%s.**" % (who, stamp, " (triage)" if verdict else "")

    comment = head + ((" " + verdict + ".") if verdict else "") + "\n\n" + text.strip() + "\n"

    # Through triage.py's own append, so a comment written here lands exactly where one written by the
    # application lands - above the rule that closes the entry, with the blank line it needs.
    entry = triage.Entry(tag.lower(), row["block"])

    replace_block(conn, tag.upper(), entry.with_comment(comment))

    render_to_disk(conn, tests_path)

    return comment


def file_issue(conn, kind, title, detail, issues_path=ISSUES_FILE, tests_path=TESTS_FILE,
               raised_from=None):
    """Files a new OB-### or FR-### in the Inbox and re-reads the store.

    Delegates to triage.py's own numbering and Inbox handling, because a second implementation of
    "what is the next reference" is a second thing that can hand out one already taken.

    :param conn: an open connection
    :param kind: 'bug' or 'feature request'
    :param title: the one-line summary
    :param detail: the body
    :param issues_path: issues.md
    :param tests_path: tests.md, needed only because the rebuild below reads both
    :param raised_from: what this came from - an MT-###, a finding, a person - or None for the
                        default. WORTH GIVING: the field is what `issues` and `verify-ledger`
                        report, and 'the triage API' answers the question 'where did this come
                        from' with 'through a door', which is not an answer. A follow-up filed
                        out of a test's Comments has an obvious one, and losing it means the
                        only trace back is prose in the body.
    :return: the reference allocated
    """

    kind, recognised = triage.normalize_kind(kind)

    if not recognised:
        raise ValueError("kind must be 'bug' or 'feature request', not %r" % kind)

    ref = triage.format_ref(kind, triage.next_ref_number(kind))

    stamp = datetime.date.today().isoformat()

    block = ("### %s - %s - %s\n\n"
             "**Kind:** %s  \n"
             "**Raised from:** %s  \n"
             "**Filed:** %s  \n\n"
             "%s\n" % (ref, stamp, title.strip(), kind,
                            (raised_from or "the triage API").strip(), stamp,
                            detail.strip()))

    triage.append_to_inbox(issues_path, block)

    # The paths it was GIVEN, not the defaults.  Writing to one file and then rebuilding from another
    # is the shape of mistake that makes a store quietly disagree with the disk it claims to describe -
    # and it hid here for one run, because the defaults are right in production and only a test passing
    # copies could see it.
    build(conn, tests_path, issues_path)

    return ref


def replace_block(conn, tag, block):
    """Puts a rewritten block back, re-parsing every field out of it.

    :param conn: an open connection
    :param tag: MT-###
    :param block: the new markdown for that entry
    """

    entry = triage.Entry(tag.lower(), block)

    conn.execute(
        "UPDATE test SET date = ?, title = ?, disposition = ?, origin = ?, written = ?,"
        " reopened = ?, block = ? WHERE tag = ?",
        (entry.date, entry.title, entry.disposition, entry.origin, entry.written,
         1 if entry.reopened else 0, block, tag),
    )

    conn.execute("DELETE FROM verdict WHERE tag = ?", (tag,))

    for seq, (who, date, kind, verdict, note) in enumerate(verdicts_in(block)):
        conn.execute(
            "INSERT INTO verdict (tag, seq, who, date, kind, verdict, note)"
            " VALUES (?, ?, ?, ?, ?, ?, ?)",
            (tag, seq, who, date, kind, verdict, note),
        )

    conn.commit()


def render_to_disk(conn, tests_path=TESTS_FILE):
    """Writes tests.md and its archives from the store, each entry where the rule puts it.

    Only a file whose bytes change is written, each through triage.py's backed-up atomic write.  The files that GAIN an
    entry are written before the ones that lose one, so that an interruption between two writes leaves an entry in two
    files - the same text twice, which the next build reads once - rather than in none.

    REFUSES when two files held one tag with different text: the store kept the first, and writing would erase the
    other, which may be the one with the newer comment.

    :param conn: an open connection
    :param tests_path: tests.md; the archives are written beside it
    :return: the basenames written
    """

    try:
        differing = [r["tag"] for r in conn.execute("SELECT DISTINCT tag FROM duplicate WHERE same = 0")]
    except sqlite3.OperationalError:
        differing = []      # a store not filled by `build` read no files, so it read none twice

    if differing:
        raise IOError("REFUSING to write: %s %s in two of the test files with different text, and writing would keep one"
                      " and erase the other.  Delete the wrong copy by hand, then write again."
                      % (", ".join(differing), "is" if len(differing) == 1 else "are"))

    row = conn.execute("SELECT crlf FROM doc WHERE name = 'tests'").fetchone()

    crlf = bool(row["crlf"]) if row else True

    folder = os.path.dirname(os.path.abspath(tests_path))

    def path_of(name):
        return tests_path if name == TESTS_NAME else os.path.join(folder, name)

    def tags_in(text):
        return set(m.group(1) for m in triage.ANCHOR_RE.finditer(text))

    pending = []

    rendered = render_files(conn)

    for name, text in rendered:
        path = path_of(name)

        out = text.replace("\n", "\r\n") if crlf else text

        if os.path.exists(path):
            with io.open(path, "rb") as fh:
                held = fh.read()

            if held == out.encode("utf-8"):
                continue

            gains = bool(tags_in(text) - tags_in(held.decode("utf-8")))
        else:
            gains = True

        pending.append((not gains, name, path, text))

    # False sorts first: the gaining files, then the rest, each group in the order rendered.
    pending.sort(key=lambda p: p[0])

    for _, name, path, text in pending:
        triage.write_text(path, text, crlf)

    # The files now hold what the store renders, so that is where each entry is, under the head each was given.  A
    # file the store does not write - an archive ARCHIVES no longer names - is left for `verify` to name.
    try:
        conn.execute("DELETE FROM placed")
        conn.execute("DELETE FROM read_head")

        conn.executemany("INSERT INTO placed (tag, file) VALUES (?, ?)", sorted(homes(conn).items()))

        conn.executemany("INSERT INTO read_head (file, head) VALUES (?, ?)",
                         [(name, triage.parse_tests_text(text)[0]) for name, text in rendered])

        # A second copy in a file just written is gone from it.
        conn.executemany("DELETE FROM duplicate WHERE file = ?", [(name,) for name, _ in rendered])

        conn.commit()
    except sqlite3.OperationalError:
        pass

    return [p[1] for p in pending]


def snapshot(conn):
    """Everything the store says about the tests, as one value two stores can be compared by.

    Every entry's block and fields, and every comment marker the verdict table holds - not where any of them is kept,
    and not the order, which `archive` is allowed to change across files.

    :param conn: an open connection
    :return: (entries, comments): {tag: row tuple}, {tag: [comment tuples]}
    """

    entries = dict((r[0], tuple(r)) for r in conn.execute(
        "SELECT tag, anchor, date, title, disposition, origin, written, reopened, block FROM test"))

    comments = {}

    for r in conn.execute("SELECT tag, seq, who, date, kind, verdict, note FROM verdict ORDER BY tag, seq"):
        comments.setdefault(r[0], []).append(tuple(r))

    return entries, comments


def archive(conn, tests_path=TESTS_FILE, issues_path=ISSUES_FILE):
    """Puts every entry in the file the rule puts it in, and proves the record is the same afterwards.

    THE ARCHIVE IS A FUNCTION OF THE STORE.  Run it on any checkout, as often as you like: the second run writes
    nothing.  It is what moves the closed entries out of tests.md when a release is added to ARCHIVES, and what puts an
    entry right after a hand edit has left it in the wrong file.  Every other write does the same as a side effect.

    The proof is a second store, built from the files just written, compared with the first entry by entry and comment
    by comment; then that the files are byte for byte what it renders.  Either failing raises, and the files written are
    in `.triage-backups/`.

    :param conn: a connection `build` filled from these files
    :param tests_path: tests.md; the archives are beside it
    :param issues_path: issues.md, which the rebuild reads as well
    :return: dict - "moved": [(tag, from, to)], "written": [basename], "entries", "comments"
    """

    ok, why = verify_as_read(conn, tests_path)

    if not ok:
        raise IOError("REFUSING to archive: " + why)

    moves = misplaced(conn)

    before = snapshot(conn)

    # AND THE LEDGER, which is generated from the store as well, and whose closing sentence says where the closed
    # entries went: one command at merge time rather than two that have to be remembered in order.
    regenerate_ledger(conn, tests_path, write=False)

    written = render_to_disk(conn, tests_path)

    again = connect(":memory:")

    try:
        build(again, tests_path, issues_path)

        after = snapshot(again)

        if after != before:
            lost = sorted(set(before[0]) - set(after[0]), key=_tag_order)
            changed = sorted((t for t in before[0] if t in after[0] and before[0][t] != after[0][t]), key=_tag_order)
            said = sorted((t for t in set(before[1]) | set(after[1])
                           if before[1].get(t) != after[1].get(t)), key=_tag_order)

            raise IOError("the files written do not hold the record they were written from: lost %s, changed %s,"
                          " comments differ on %s - the previous files are in .triage-backups/"
                          % (lost or "none", changed or "none", said or "none"))

        ok, why = verify(again, tests_path)

        if not ok:
            raise IOError("the files written do not render back from the store built from them: " + why)

        stray = misplaced(again)

        if stray:
            raise IOError("after writing, %d entries are still not where the rule puts them: %s"
                          % (len(stray), stray[:5]))
    finally:
        again.close()

    return {"moved": moves, "written": written, "entries": len(before[0]),
            "comments": sum(len(v) for v in before[1].values())}


# --------------------------------------------------------------------------------------------
# the self test
# --------------------------------------------------------------------------------------------

def selftest(tests_path=TESTS_FILE, issues_path=ISSUES_FILE):
    """Exercises the whole store against COPIES of the real record, and says what it found.

    There is no Python test harness in this repository, so this is the harness: a command that can be
    run before trusting the thing, and whose failure is a sentence rather than a stack trace.

    Copies, always. The record it is checking is the one the project keeps its testing history in, and
    a self test that can damage the thing it is testing is worse than none.

    :param tests_path: the real tests.md, copied before anything touches it
    :param issues_path: the real issues.md, likewise
    :return: (passed, list of result lines)
    """

    import shutil
    import tempfile

    results = []
    ok = [True]

    def check_that(claim, condition):
        results.append(("PASS  " if condition else "FAIL  ") + claim)

        if not condition:
            ok[0] = False

    work = tempfile.mkdtemp(prefix="triagedb-selftest-")

    try:
        tests = os.path.join(work, "tests.md")
        # NOT named `issues`: that is the name of the function three lines down that
        # asks the store what is in the Inbox, and shadowing it here would make the
        # self test call a string.
        issues_file = os.path.join(work, "issues.md")

        shutil.copyfile(tests_path, tests)
        shutil.copyfile(issues_path, issues_file)

        # And the archives beside it (FR-136), which hold most of the record.
        for path in tests_paths(tests_path)[1:]:
            shutil.copyfile(path, os.path.join(work, os.path.basename(path)))

        def every_file():
            return [io.open(path, "rb").read() for path in tests_paths(tests)]

        conn = connect(":memory:")

        count, issue_count = build(conn, tests, issues_file)

        check_that("the record parses (%d tests, %d issues)" % (count, issue_count),
                   count > 0 and issue_count >= 0)

        # THE property. Everything else is worth nothing without it: if the store cannot reproduce
        # what it was built from, then writing it out destroys the record.  Asked of every file as it
        # was read, wherever each entry is kept (FR-136).
        rendered_ok, message = verify_as_read(conn, tests)

        check_that("it renders back byte for byte - " + message.split("\n")[0], rendered_ok)

        # THE ARCHIVE KEEPS THE RECORD (FR-136).  On these copies as they came - before the archive was
        # first run, that moves 770 entries; after, nothing - and the record is compared both ways.
        record = snapshot(conn)

        adam = sum(text.count("**Adam,") for _, text, _ in read_tests_files(tests))

        done = archive(conn, tests, issues_file)

        check_that("archiving keeps every entry and every comment (%d and %d, %d moved)"
                   % (done["entries"], done["comments"], len(done["moved"])), snapshot(conn) == record)

        check_that("and every '**Adam,' line, across tests.md and the archives (%d)" % adam,
                   sum(text.count("**Adam,") for _, text, _ in read_tests_files(tests)) == adam)

        check_that("and the files are now as the store writes them", verify(conn, tests)[0])

        original = every_file()

        # Writing with nothing changed must not change the file either. A store that reformats on
        # every save turns every unrelated commit into a whole-file diff.
        render_to_disk(conn, tests)

        check_that("rendering an unchanged store does not touch a byte", every_file() == original)

        # THE LEDGER IS GENERATED, and generating it must leave the store able to render.
        #
        # This is the operation with the widest blast radius in the module: it rewrites the HEAD
        # of tests.md, which is the half `render_tests` does not reconstruct from blocks, so a
        # mistake here is not one entry but the whole document.
        #
        # Asserted against the store rather than against a copy of what it wrote: the count of
        # rows in the table has to equal the count of entries that are neither fixed validated
        # nor superseded, which is what the ledger IS.
        moved = regenerate_ledger(conn, tests)

        outstanding = sum(1 for r in check(conn)
                          if r["disposition"] not in ("fixed validated", "superseded"))

        check_that("the regenerated ledger has a row per open entry (%d)" % outstanding,
                   moved["rows_after"] == outstanding)

        regenerated_ok, regenerated_why = verify(conn, tests)

        check_that("and the store still renders after it - " +
                   regenerated_why.splitlines()[0], regenerated_ok)

        # AND IT IS IDEMPOTENT.  A generator whose second run differs from its first turns every
        # later commit into a whole-file diff, which is the same complaint the unchanged-render
        # check above exists for.
        again = regenerate_ledger(conn, tests)

        check_that("running it twice changes nothing the second time", not again["changed"])

        # A disposition change reaches the file, the row, AND still renders.
        subject = check(conn)[0]["tag"]

        was = set_disposition(conn, subject, "superseded", tests)

        check_that("a disposition change is in the store",
                   check(conn, tag=subject)[0]["disposition"] == "superseded")

        # In whichever file it is kept in: tests.md, or an archive (FR-136).
        check_that("a disposition change is in the file",
                   any("**Disposition:** superseded" in e.block
                       for _, text, _ in read_tests_files(tests)
                       for e in triage.parse_tests_text(text)[1] if e.tag == subject))

        check_that("the store still renders after a write", verify(conn, tests)[0])

        set_disposition(conn, subject, was, tests)

        check_that("and it goes back", every_file() == original)

        # A verdict is readable as a verdict afterwards, which is the whole point of the exercise.
        before = len(history(conn, subject))

        add_comment(conn, subject, "Adam", "A self test wrote this.", "Does not work", tests)

        after = history(conn, subject)

        check_that("a verdict is recorded", len(after) == before + 1)

        check_that("and reads back as a verdict, not as prose",
                   after[-1]["kind"] == "verdict" and after[-1]["verdict"] == "Does not work")

        check_that("and check() sees it as the latest",
                   check(conn, tag=subject)[0]["latest"] == "Does not work")

        check_that("the store still renders after a comment", verify(conn, tests)[0])

        # Filing allocates a reference and the store sees it, from the paths it was given.
        issues_before = len(issues(conn))

        ref = file_issue(conn, "bug", "Filed by the self test", "Nothing to see.", issues_file, tests)

        check_that("filing allocates a reference (%s)" % ref, bool(ref))

        check_that("and the store sees it", len(issues(conn)) == issues_before + 1)

        # And the guards refuse what they should.
        try:
            set_disposition(conn, subject, "not a real disposition", tests)
            check_that("an invalid disposition is refused", False)
        except ValueError:
            check_that("an invalid disposition is refused", True)

        try:
            add_comment(conn, subject, "Adam", "x", "Sort of works", tests)
            check_that("an invalid verdict is refused", False)
        except ValueError:
            check_that("an invalid verdict is refused", True)

        try:
            set_disposition(conn, "MT-999999", "needs test", tests)
            check_that("an unknown tag is refused", False)
        except KeyError:
            check_that("an unknown tag is refused", True)

        # THE ARCHIVES (FR-136), on the copies as the writes above have left them.
        conn = connect(":memory:")

        build(conn, tests, issues_file)

        record = snapshot(conn)

        archive(conn, tests, issues_file)

        placement = homes(conn)

        check_that("tests.md keeps every open entry",
                   all(placement[r["tag"]] == TESTS_NAME for r in check(conn)
                       if r["disposition"] not in (triage.VALIDATED, triage.SUPERSEDED)))

        again = archive(conn, tests, issues_file)

        check_that("archiving twice writes nothing the second time", not again["written"] and not again["moved"])

        archived = [tag for tag, name in sorted(placement.items(), key=lambda p: _tag_order(p[0]))
                    if name != TESTS_NAME]

        check_that("there is an archived entry to try the rest on", bool(archived))

        if archived:
            subject = archived[0]

            kept_in = placement[subject]

            # An answer to an archived entry is kept, and brings it back to tests.md: it is news again.
            add_comment(conn, subject, "Adam", "A self test wrote this on an archived entry.", "Works", tests)

            def kept_in_file(name):
                return any(e.tag == subject for e in triage.parse_tests_text(
                    triage.read_text(os.path.join(work, name))[0])[1])

            check_that("a comment on an archived entry brings it back to tests.md", kept_in_file(TESTS_NAME))

            check_that("and it is no longer in %s" % kept_in, not kept_in_file(kept_in))

            check_that("and the files still render back from the store", verify(conn, tests)[0])

            reread = connect(":memory:")

            build(reread, tests, issues_file)

            check_that("and a store read back from them holds the comment",
                       history(reread, subject)[-1]["note"].startswith("A self test wrote this"))

            reread.close()

            # The rule is a function of the store: a release that shipped after it takes it out again.
            ARCHIVES.append(("9.9.9", "9999-12-31"))

            try:
                moved = archive(conn, tests, issues_file)["moved"]

                check_that("a release added to ARCHIVES takes it out again, into that release's archive",
                           (subject, TESTS_NAME, archive_file("9.9.9")) in moved)
            finally:
                ARCHIVES.pop()

            check_that("into that file", kept_in_file(archive_file("9.9.9")) and not kept_in_file(TESTS_NAME))

            # A release taken out of ARCHIVES again leaves its file behind, holding the entry: still read, so nothing
            # is lost, and named by `verify` as a file nothing will write again.
            conn = connect(":memory:")

            build(conn, tests, issues_file)

            check_that("a file ARCHIVES no longer names is still read", verify_as_read(conn, tests)[0]
                       and (subject, archive_file("9.9.9"), TESTS_NAME) in misplaced(conn))

            check_that("and verify names it", archive_file("9.9.9") in verify(conn, tests)[1])

            render_to_disk(conn, tests)

            os.remove(os.path.join(work, archive_file("9.9.9")))

            conn = connect(":memory:")

            build(conn, tests, issues_file)

            check_that("and once it is written out and the file deleted, the entry is in tests.md and nothing is lost",
                       kept_in_file(TESTS_NAME) and verify(conn, tests)[0]
                       and set(snapshot(conn)[0]) == set(record[0]))

        # Two copies of one entry that disagree are refused, not resolved by guessing.
        first_tag = check(conn)[0]["tag"]

        block = conn.execute("SELECT block FROM test WHERE tag = ?", (first_tag,)).fetchone()[0]

        stray = os.path.join(work, archive_file("0.0.0-selftest"))

        io.open(stray, "w", encoding="utf-8", newline="\n").write(
            "# a stray archive\n\n" + block.rstrip("\n") + "\n\nA different word.\n")

        conn = connect(":memory:")

        build(conn, tests, issues_file)

        check_that("a tag kept twice with different text is reported", not verify_as_read(conn, tests)[0])

        try:
            render_to_disk(conn, tests)
            check_that("and no write is made over it", False)
        except IOError:
            check_that("and no write is made over it", True)

        os.remove(stray)

    finally:
        shutil.rmtree(work, ignore_errors=True)

    return ok[0], results


# --------------------------------------------------------------------------------------------
# the command line
# --------------------------------------------------------------------------------------------

def _print_rows(rows):
    if not rows:
        print("(none)")
        return

    for r in rows:
        print("%-8s %-20s %-18s %s" % (r["tag"], (r["disposition"] or "?")[:20],
                                       (r["latest"] or "never triaged")[:18],
                                       (r["title"] or "")[:60]))

    print("")
    print("%d entries" % len(rows))


def main(argv=None):
    """The command line over the store.

    :param argv: arguments, or None for sys.argv
    :return: a process exit code
    """

    parser = argparse.ArgumentParser(
        prog="triagedb",
        description="The triage store: check, update and file without reading the markdown.")

    sub = parser.add_subparsers(dest="command")

    sub.add_parser("sync", help="rebuild the store from the markdown and verify it renders back")
    sub.add_parser("regenerate-ledger",
                   help="rewrite the Ledger table in tests.md from the store")
    sub.add_parser("archive",
                   help="put every entry in the file the rule puts it in - closed and released ones in"
                        " tests-archive-<release>.md - and prove the record is the same afterwards")
    sub.add_parser("counts",
                   help="rewrite the counts behaviour.md and open-questions.md quote, from the store and"
                        " issues.md")
    p = sub.add_parser("verify", help="prove the store renders the markdown byte for byte, and that every OB a"
                                      " finding's status note says it is tracked as is in the Inbox")
    p.add_argument("--store", default=DB_FILE,
                   help="the findings store whose status notes are checked - a copy, to see the check fail")
    sub.add_parser("selftest", help="exercise the whole store against copies of the real record")

    p = sub.add_parser("check", help="query the tests")
    p.add_argument("--disposition")
    p.add_argument("--verdict")
    p.add_argument("--tag")
    p.add_argument("--untriaged", action="store_true", help="only entries Adam has never ruled on")
    p.add_argument("--reopened", action="store_true", help="only entries that changed after a ruling")
    p.add_argument("--json", action="store_true")

    p = sub.add_parser("show", help="one test's whole history")
    p.add_argument("tag")

    p = sub.add_parser("issues", help="the Inbox")
    p.add_argument("--kind", choices=["bug", "feature request"])

    p = sub.add_parser("set", help="change a test's disposition")
    p.add_argument("tag")
    p.add_argument("disposition")

    p = sub.add_parser("comment", help="append a comment, optionally as a triage verdict")
    p.add_argument("tag")
    p.add_argument("text")
    p.add_argument("--who", default="Claude")
    p.add_argument("--verdict")

    p = sub.add_parser("file", help="file a new issue")
    p.add_argument("kind", choices=["bug", "feature request"])
    p.add_argument("title")
    p.add_argument("detail")
    p.add_argument("--raised-from", dest="raised_from")

    args = parser.parse_args(argv)

    if not args.command:
        parser.print_help()
        return 2

    # Built fresh for every command while the markdown is still what git tracks. It is a tenth of a
    # second over half a megabyte, and it removes the one failure this design could otherwise have:
    # answering from a store that is older than the file somebody just edited.
    conn = connect(":memory:")

    build(conn)

    if args.command == "sync":
        tests, items = sync(conn)
        print("%d tests, %d issues; renders back byte for byte" % (tests, items))
        return 0

    if args.command == "regenerate-ledger":
        # AND VERIFIED, in the same breath.  This writes tests.md from the store, so the one way
        # it could go wrong is writing a file the store can no longer reproduce - which would
        # make the next update overwrite the record.  Saying "regenerated" without checking that
        # is exactly the claim `verify` exists to stop anybody making.
        changed = regenerate_ledger(conn)

        ok, message = verify(conn)

        print("ledger: %d rows -> %d rows" % (changed["rows_before"], changed["rows_after"]))

        if changed["removed"]:
            print("  no longer open: %s" % ", ".join(changed["removed"]))

        if changed["added"]:
            print("  newly open:     %s" % ", ".join(changed["added"]))

        if not changed["changed"]:
            print("  (already up to date)")

        print(message)

        return 0 if ok else 1

    if args.command == "archive":
        # THE MERGE-TIME COMMAND (FR-136): re-runnable, and the second run writes nothing.
        done = archive(conn)

        for tag, was, now in done["moved"]:
            print("  %s: %s -> %s" % (tag, was, now))

        print("moved %d entries; wrote %s" % (len(done["moved"]), ", ".join(done["written"]) or "nothing"))

        print("%d entries and %d comments, the same before and after; every file renders back byte for byte"
              % (done["entries"], done["comments"]))

        return 0

    if args.command == "counts":
        # The file store: `conn` was built from the markdown and holds no findings.
        store = connect()

        try:
            changed = render_counts(store)
        finally:
            store.close()

        for path in changed:
            print("rewrote " + os.path.basename(path))

        if not changed:
            print("(already up to date)")

        return 0

    if args.command == "selftest":
        passed, lines = selftest()

        for line in lines:
            print(line)

        print("")
        print("PASSED" if passed else "FAILED")

        return 0 if passed else 1

    if args.command == "verify":
        ok, message = verify(conn)
        print(message)

        # AND EVERY OB A FINDING SAYS IT IS TRACKED AS IS IN THE INBOX (GSB-C3).  From the file store, never this
        # command's own: `conn` was built from the markdown and holds no findings.
        claims_ok, claims_message, offenders = verify_inbox_claims(args.store)

        print(claims_message)

        for ref, document, ob in offenders:
            print("  %s (%s) says %s" % (ref, document, ob))

        return 0 if ok and claims_ok else 1

    if args.command == "check":
        rows = check(conn,
                     disposition=args.disposition,
                     verdict=args.verdict,
                     tag=args.tag,
                     triaged=False if args.untriaged else None,
                     reopened=True if args.reopened else None)

        if args.json:
            print(json.dumps(rows, indent=1))
        else:
            _print_rows(rows)

        return 0

    if args.command == "show":
        for h in history(conn, args.tag):
            print("%-7s %-8s %-10s %s" % (h["kind"], h["who"], h["date"] or "-",
                                          h["verdict"] or (h["note"] or "")[:70]))
        return 0

    if args.command == "issues":
        for it in issues(conn, args.kind):
            print("%-8s %-16s %s" % (it["ref"], (it["kind"] or "?")[:16], (it["title"] or "")[:60]))
        return 0

    if args.command == "set":
        was = set_disposition(conn, args.tag, args.disposition)
        print("%s: %s -> %s" % (args.tag.upper(), was, args.disposition))
        return 0

    if args.command == "comment":
        add_comment(conn, args.tag, args.who, args.text, args.verdict)
        print("%s: comment added" % args.tag.upper())
        return 0

    if args.command == "file":
        print(file_issue(conn, args.kind, args.title, args.detail,
                         raised_from=args.raised_from))
        return 0

    parser.print_help()
    return 2


if __name__ == "__main__":
    sys.exit(main())
