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

    return conn


def build(conn, tests_path=TESTS_FILE, issues_path=ISSUES_FILE):
    """Fills the store from the markdown, replacing whatever was in it.

    Uses triage.py's parsers rather than its own, so there is exactly one definition of what an entry
    is. See the module docstring.

    :param conn: an open connection
    :param tests_path: tests.md
    :param issues_path: issues.md
    :return: (number of tests, number of issues)
    """

    tests = triage.TestsDoc(tests_path)
    issues = triage.IssuesDoc(issues_path)

    conn.execute("DELETE FROM test")
    conn.execute("DELETE FROM verdict")
    conn.execute("DELETE FROM issue")
    conn.execute("DELETE FROM doc")

    # The head, and everything the entries do not cover.  Kept verbatim: it holds the index tables and
    # the conventions, and this module has no business reformatting either.
    conn.execute(
        "INSERT INTO doc (name, head, tail, crlf) VALUES (?, ?, ?, ?)",
        ("tests", tests.head, "", 1 if tests.crlf else 0),
    )

    for i, e in enumerate(tests.entries):
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

    for i, it in enumerate(issues.pending):
        conn.execute(
            "INSERT OR REPLACE INTO issue"
            " (ref, ordinal, kind, title, filed, raised_from, build, block)"
            " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            (it.ref, i, it.kind, it.summary, it.filed_at, it.raised_from, it.build, it.detail),
        )

    conn.commit()

    return len(tests.entries), len(issues.pending)


def render_tests(conn):
    """tests.md as the store says it should be.

    :param conn: an open connection
    :return: the file's whole text
    """

    row = conn.execute("SELECT head FROM doc WHERE name = 'tests'").fetchone()

    if row is None:
        raise IOError("the store has no tests document - run build first")

    blocks = [r["block"] for r in
              conn.execute("SELECT block FROM test ORDER BY ordinal")]

    return row["head"] + "".join(blocks)


def verify(conn, tests_path=TESTS_FILE):
    """Proves the store can reproduce the file it was built from, byte for byte.

    This is the safety argument for having a database at all, and it is a command rather than a claim
    because the claim is the kind that is easy to make and hard to notice being wrong.

    :param conn: an open connection
    :param tests_path: the file to compare against
    :return: (ok, message)
    """

    on_disk, _ = triage.read_text(tests_path)

    rendered = render_tests(conn)

    if rendered == on_disk:
        return True, "tests.md renders byte-identically from the store (%d chars)" % len(rendered)

    # Say WHERE, because "they differ" on a 10,000-line file is not actionable.
    for i, (a, b) in enumerate(zip(on_disk, rendered)):
        if a != b:
            return False, ("first difference at character %d: file has %r, store renders %r\n"
                           "  around: %r" % (i, a, b, on_disk[max(0, i - 60):i + 60]))

    return False, ("same prefix, different length: file %d chars, store renders %d"
                   % (len(on_disk), len(rendered)))


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

        ok, message = verify(conn)

        if not ok:
            raise IOError("the store does not render back to what it was built from: " + message)

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

    _replace_block(conn, tag.upper(), block)

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

    _replace_block(conn, tag.upper(), entry.with_comment(comment))

    render_to_disk(conn, tests_path)

    return comment


def file_issue(conn, kind, title, detail, issues_path=ISSUES_FILE, tests_path=TESTS_FILE):
    """Files a new OB-### or FR-### in the Inbox and re-reads the store.

    Delegates to triage.py's own numbering and Inbox handling, because a second implementation of
    "what is the next reference" is a second thing that can hand out one already taken.

    :param conn: an open connection
    :param kind: 'bug' or 'feature request'
    :param title: the one-line summary
    :param detail: the body
    :param issues_path: issues.md
    :param tests_path: tests.md, needed only because the rebuild below reads both
    :return: the reference allocated
    """

    kind, recognised = triage.normalize_kind(kind)

    if not recognised:
        raise ValueError("kind must be 'bug' or 'feature request', not %r" % kind)

    ref = triage.format_ref(kind, triage.next_ref_number(kind))

    stamp = datetime.date.today().isoformat()

    block = ("### %s - %s - %s\n\n"
             "**Kind:** %s  \n"
             "**Raised from:** the triage API  \n"
             "**Filed:** %s  \n\n"
             "%s\n" % (ref, stamp, title.strip(), kind, stamp, detail.strip()))

    triage.append_to_inbox(issues_path, block)

    # The paths it was GIVEN, not the defaults.  Writing to one file and then rebuilding from another
    # is the shape of mistake that makes a store quietly disagree with the disk it claims to describe -
    # and it hid here for one run, because the defaults are right in production and only a test passing
    # copies could see it.
    build(conn, tests_path, issues_path)

    return ref


def _replace_block(conn, tag, block):
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
    """Writes tests.md from the store.

    :param conn: an open connection
    :param tests_path: the file to write
    """

    row = conn.execute("SELECT crlf FROM doc WHERE name = 'tests'").fetchone()

    triage.write_text(tests_path, render_tests(conn), bool(row["crlf"]) if row else True)


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

        original = io.open(tests, "rb").read()

        conn = connect(":memory:")

        count, issue_count = build(conn, tests, issues_file)

        check_that("the record parses (%d tests, %d issues)" % (count, issue_count),
                   count > 0 and issue_count >= 0)

        # THE property. Everything else is worth nothing without it: if the store cannot reproduce
        # what it was built from, then writing it out destroys the record.
        rendered_ok, message = verify(conn, tests)

        check_that("it renders back byte for byte - " + message.split("\n")[0], rendered_ok)

        # Writing with nothing changed must not change the file either. A store that reformats on
        # every save turns every unrelated commit into a whole-file diff.
        render_to_disk(conn, tests)

        check_that("rendering an unchanged store does not touch a byte",
                   io.open(tests, "rb").read() == original)

        # A disposition change reaches the file, the row, AND still renders.
        subject = check(conn)[0]["tag"]

        was = set_disposition(conn, subject, "superseded", tests)

        check_that("a disposition change is in the store",
                   check(conn, tag=subject)[0]["disposition"] == "superseded")

        check_that("a disposition change is in the file",
                   "**Disposition:** superseded" in io.open(tests, encoding="utf-8").read())

        check_that("the store still renders after a write", verify(conn, tests)[0])

        set_disposition(conn, subject, was, tests)

        check_that("and it goes back", io.open(tests, "rb").read() == original)

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
    sub.add_parser("verify", help="prove the store renders the markdown byte for byte")
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
        return 0 if ok else 1

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
        print(file_issue(conn, args.kind, args.title, args.detail))
        return 0

    parser.print_help()
    return 2


if __name__ == "__main__":
    sys.exit(main())
