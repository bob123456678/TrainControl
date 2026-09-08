# -*- coding: utf-8 -*-
"""Reads every review document and writes one catalogue row per finding.

Adam, 2026-09-08, ruling on MON-C11 - 141 review documents, 139 effectively open, and a folder that
could no longer answer who needed to do what:

    "update our MT triage database to catalog each review item.  Now, no reference will ever be stale
    or lost, but useless prose will go away.  Then everything relevant, that needs to be in prose, will
    live in behaviour.md.  Expand the database to capture the line number/commit ID/filename of each
    issue, filling out what's relevant as needed."

So the catalogue is the index and the reviews become disposable.  What this must therefore capture is
everything a reader would otherwise have to open the document for:

  ref          the citation everything else uses - MON-C1, RGD-B2, DD-A7
  document     where it was written, so the prose is findable until it is deleted
  severity     A/B/C/D as the review graded it
  what         the finding's own heading, which is the one-line summary its author wrote
  disposition  Open / Fixed / Cancelled / Ruled, from the finding's own table where it has one
  file, line   the evidence, parsed from the body - `Foo.java:123` and its friends
  commit       any 8-hex id the body cites

**The database is authoritative**, exactly as it is for the tests and the issues: this loads the
`finding` table in `docs/manual-tests/triage.db` and renders `docs/reviews/findings.md` from it.  The
markdown is committed and greppable; the database is the thing to query.

Run:  python docs/tools/catalog-findings.py            (load the table, render the markdown)
      python docs/tools/catalog-findings.py --check    (report without writing anything)

Query it afterwards through `triagedb.findings(conn, ...)`, or in SQL:

    SELECT ref, disposition, cited_by FROM finding WHERE cited_by IS NOT NULL AND disposition = 'Open'

which is the question the review folder could not answer: what is still open AND still cited.

It is deliberately a SCRIPT and not a one-off edit: the reviews are still being written, and a
catalogue that cannot be regenerated is the next thing to go stale.
"""
import io
import os
import re
import sys

REVIEWS = "docs/reviews"
OUT = os.path.join(REVIEWS, "findings.md")

# A finding heading: "### MON-C1 - the thing" or "## RGD-B2 — the thing"
HEADING = re.compile(r"^#{2,4}\s+([A-Z][A-Z0-9]{1,7}-[A-Z]?\d+[a-z]?(?:\.\.[A-Z]?\d+[a-z]?)?)\s*[-—:]?\s*(.*)$")

# THE SHORT-FORM CONVENTION.  Sixty documents declare their prefix once and then number findings
# `### A1 - ...`, which the code cites as `RC-A1`.  Both declaration spellings are in use, with and
# without backticks, and several carry a sentence after the prefix - hence the loose tail.
# "**Prefix:**", "**Prefix for citing these findings elsewhere:**" and "**Citation prefix:**" are
# all in use, with and without backticks.
PREFIX = re.compile(r"^\*\*(?:Citation [Pp]refix|Prefix(?: for citing [^:]{0,40})?)\s*:\s*\**\s*`?([A-Z][A-Z0-9]{1,7})`?")

# A short heading: severity letter, number, optional sub-letter.  Anchored on the separator so that a
# section called `## B - medium` is not read as a finding named B.
# The separator is optional: one document writes bare `### A1` and puts the finding in the body.
# `## B - medium` cannot match either way - a severity banner carries no digit.
SHORT_HEADING = re.compile(r"^#{2,4}\s+([A-Z]\d{1,3}[a-z]?)\s*(?:[-\u2013\u2014:.]\s*(.*))?$")

# Evidence in a body: Foo.java:123, Foo.java:123-125
SOURCE = re.compile(r"`?([A-Za-z][A-Za-z0-9_]*\.(?:java|md|json|properties|xml|sh|py))`?:(\d+)(?:-(\d+))?")

# A commit id: eight or more hex, usually in backticks
COMMIT = re.compile(r"`([0-9a-f]{7,40})`")

# The finding's own disposition row, as docs/reviews/README.md prescribes
DISPOSITION = re.compile(r"\|\s*\*\*Disposition\*\*\s*\|\s*([^|]+?)\s*\|")

# Or a status table row: | MON-C1 | ... | C | ... | Fixed ... |
SEVERITY = re.compile(r"^([A-Z][A-Z0-9]{1,7})-([A-D])?(\d+)")


def severity_of(ref, title, body):
    """A/B/C/D, from the ref's own letter where it has one, else from the body's Severity row."""
    m = SEVERITY.match(ref)

    if m and m.group(2):
        return m.group(2)

    m = re.search(r"\|\s*\*\*Severity\*\*\s*\|\s*([A-D])\b", body)

    if m:
        return m.group(1)

    m = re.search(r"\*\*Severity\s+([A-D])\b", body)

    return m.group(1) if m else "?"


# A summary-table row keyed by the finding's ref: | MON-C1 | ... | ... | Fixed 2026-09-08 ... |
# A table row naming a finding.  The ref may be bare, in backticks, or bolded - all three are in use,
# sometimes in one document.
FULL_ROW = r"\|\s*[`*]{0,2}\s*([A-Z][A-Z0-9]{1,7}-[A-Z]?\d+[a-z]?)\s*[`*]{0,2}\s*\|(.*)$"

SHORT_ROW = r"\|\s*[`*]{0,2}\s*([A-Z]\d{1,3}[a-z]?)\s*[`*]{0,2}\s*\|(.*)$"


def table_rows(text, prefix=None):
    """Every `| REF | ... |` row in a document, as its list of cells, by ref.

    Most reviews record their dispositions in ONE summary table rather than on each finding, so a
    catalogue that only reads per-finding tables sees 182 of 1252.  The last cell of such a row is the
    disposition by the README's own layout; where it is not, it is still the most specific thing the
    document says about that ref in one line.
    """
    out = {}

    for line in text.split("\n"):
        bare = line.strip()

        m = re.match(FULL_ROW, bare)

        ref = m.group(1) if m else None

        # SHORT FORM.  A document that declares a prefix numbers its table rows the same way it numbers
        # its headings, so `| C2 |` in the DOC review is DOC-C2.  Only tried when a prefix is known,
        # which keeps it away from tables of plain data.
        if not m and prefix:
            m = re.match(SHORT_ROW, bare)

            ref = prefix + "-" + m.group(1) if m else None

        if not m:
            continue

        cells = [c.strip() for c in m.group(2).split("|")]

        cells = [c for c in cells if c]

        if cells:
            out.setdefault(ref, cells)

    return out


def disposition_of(body, fromTable):
    m = DISPOSITION.search(body)

    if m:
        return " ".join(m.group(1).split())[:120]

    # A body that says it was fixed, in the house style, without a table
    if re.search(r"\*\*Fixed\b", body):
        return "Fixed (stated in the body)"

    if re.search(r"\*\*(Cancelled|Declined|Ruled)\b", body):
        return "Ruled (stated in the body)"

    if fromTable:
        return " ".join(fromTable.split())[:120]

    return "-"


def first_evidence(body):
    """The first source location and commit the body cites, which is where a reader should start."""
    where = ""

    m = SOURCE.search(body)

    if m:
        where = m.group(1) + ":" + m.group(2)

    commits = [c for c in COMMIT.findall(body) if not c.isdigit()]

    return where, (commits[0] if commits else "")


CITATION = re.compile(r"\b([A-Z][A-Z0-9]{1,7}-[A-Z]?\d+[a-z]?)\b")

# Shaped like a finding id, but not one: ticket prefixes, encodings, protocols - and `SU45`, a Polish
# diesel class, because `SU45-070` is a locomotive in a testLoadData fixture.  On a railway, real data
# is shaped like a citation.  Kept identical to NOT_A_FINDING in testEveryCitationResolves.java: two
# measurements of one quantity that disagree is the fault this catalogue exists to end.
# `F\d+` is a Marklin function range - `F0-F19` is a tab label, `F17-32` a comment about
# function numbers. `EN57` and `SU45` are locomotive classes. `Z0` is the tail of a
# character class in a regex. Real railway data is shaped like a finding id.
NOT_A_FINDING = re.compile(r"^(MT|FR|OB|SU45|EN57|F\d+|Z0|UTF|ISO|CS|MM|DCC|RGB|ARGB|HTTP|JSON|"
                           r"IPV4|SHA|MD5|"
                           r"JDK|API|UI|ID|X|Y|Z)-")

# The guard's own file, which NAMES the dead ids in its javadoc rather than citing them - scanned like
# any other file it reports itself, and the ids it lists are counted twice.
THE_ROLL_ITSELF = "testEveryCitationResolves.java"


def citations_in_the_code():
    """Which source files cite each finding id.

    This is the half that decides whether a review can be deleted: a finding nothing cites is prose,
    and a finding cited from four files is load-bearing whatever its disposition says.
    """
    used = {}

    for root in ("src", "test"):
        for base, dirs, files in os.walk(root):
            for name in files:
                # THE SAME EXTENSIONS `testEveryCitationResolves` READS.  The test is the thing
                # that fails a build, so this must not count citations it cannot see - a catalogue
                # saying twelve beside a guard saying ten is two answers to one question.
                if not name.endswith((".java", ".properties", ".xml")):
                    continue

                if name == THE_ROLL_ITSELF:
                    continue

                path = os.path.join(base, name)

                try:
                    body = io.open(path, encoding="utf-8", errors="replace").read()
                except Exception:
                    continue

                for ref in set(CITATION.findall(body)):
                    if NOT_A_FINDING.match(ref):
                        continue

                    used.setdefault(ref, set()).add(name)

    return used


def collect():
    rows = []

    for name in sorted(os.listdir(REVIEWS)):
        if not name.endswith(".md") or name == "findings.md" or name == "README.md":
            continue

        path = os.path.join(REVIEWS, name)

        text = io.open(path, encoding="utf-8", errors="replace").read().replace("\r", "")

        lines = text.split("\n")

        # The declared prefix, if this document uses the short-form convention.  Looked for in the head
        # of the file only: the word appears again in prose further down, and in summary tables that
        # tabulate OTHER documents' prefixes.
        prefix = None

        for line in lines[:40]:
            declared = PREFIX.match(line)

            if declared:
                prefix = declared.group(1)

                break

        summary = table_rows(text, prefix)

        marks = []

        for i, line in enumerate(lines):
            m = HEADING.match(line)

            if m:
                marks.append((i, m.group(1), m.group(2).strip()))

                continue

            if not prefix:
                continue

            short = SHORT_HEADING.match(line)

            if short:
                marks.append((i, prefix + "-" + short.group(1), (short.group(2) or "").strip()))

        # TABLE-ONLY FINDINGS.  A review that lists its findings in one table and never writes a
        # heading for them is still assigning ids that the code goes on to cite - 363 citations
        # resolved to nothing until these were collected.
        headed = set(ref for _, ref, _ in marks)

        for ref, cells in summary.items():
            if ref in headed:
                continue

            # The first cell is the document's own one-line description; the last is its disposition by
            # the README's layout.  With only one cell it is the disposition, and there is no title.
            described = " ".join(cells[0].split())[:150] if len(cells) > 1 else ""

            rows.append({
                "ref": ref,
                "document": name,
                "severity": severity_of(ref, "", ""),
                "what": described or "(listed in a table, no section of its own)",
                "disposition": " ".join(cells[-1].split())[:120] if cells else "-",
                "where": "",
                "commit": "",
                "line": 0,
            })

        for n, (at, ref, title) in enumerate(marks):
            stop = marks[n + 1][0] if n + 1 < len(marks) else len(lines)

            body = "\n".join(lines[at:stop])

            where, commit = first_evidence(body)

            rows.append({
                "ref": ref,
                "document": name,
                "severity": severity_of(ref, title, body),
                # Headings read `### FP-B1. Rename-on-import can delete...`, so the remainder after the
                # ref starts with the separator.  Stripped, or every title in the catalogue opens ". ".
                "what": " ".join(title.split()).lstrip(".:-\u2013\u2014 ")[:150] or "(no heading text)",
                "disposition": disposition_of(body, " ".join(summary.get(ref, [""])[-1].split())),
                "where": where,
                "commit": commit,
                "line": at + 1,
            })

    return rows


def disambiguate(rows):
    """A ref that means two things is a citation nobody can follow.

    Nine of them exist: `IR-B1` is one finding in the July full review and a different one in the
    August week review, and the same for `FV-*` and `AC2-C1`. A comment in the code saying "IR-B1" has
    therefore been ambiguous for six weeks.

    They keep their own ref AND gain a dated one, so an existing citation still finds a row and a new
    one can be written unambiguously.
    """
    where = {}

    for r in rows:
        where.setdefault(r["ref"], set()).add(r["document"])

    clashes = 0

    for r in rows:
        if len(where[r["ref"]]) > 1:
            date = r["document"][:10]

            r["unique"] = "%s @%s" % (r["ref"], date)

            clashes += 1
        else:
            r["unique"] = r["ref"]

    return clashes


def write(rows, orphans=None, used=None, outside=None, elsewhere=None):
    out = []

    out.append("# Every review finding, catalogued")
    out.append("")
    out.append("**Generated by `docs/tools/catalog-findings.py`. Do not hand-edit: regenerate it.**")
    out.append("")
    out.append("Adam's ruling on MON-C11, 2026-09-08: *\"update our MT triage database to catalog each")
    out.append("review item. Now, no reference will ever be stale or lost, but useless prose will go")
    out.append("away. Then everything relevant, that needs to be in prose, will live in behaviour.md.")
    out.append("Expand the database to capture the line number/commit ID/filename of each issue.\"*")
    out.append("")
    out.append("The review folder had grown to 143 documents, 139 of them effectively open, and could no")
    out.append("longer answer who needed to do what. This is the index that survives them: every id that")
    out.append("anything cites, what it was about, where the evidence is, and what happened to it.")
    out.append("")
    out.append("**A row here is the citation's home.** The document column says where the prose still is,")
    out.append("for as long as it is worth keeping.")
    out.append("")
    bySeverity = {}
    open_rows = 0

    for r in rows:
        bySeverity[r["severity"]] = bySeverity.get(r["severity"], 0) + 1

        if r["disposition"] in ("-", "Open", "open"):
            open_rows += 1

    out.append("## What is in here")
    out.append("")
    out.append("| | |")
    out.append("|---|---|")
    out.append("| findings | %d |" % len(rows))
    out.append("| documents they came from | %d |" % len(set(r["document"] for r in rows)))
    out.append("| carrying a file and line | %d |" % sum(1 for r in rows if r["where"]))
    out.append("| carrying a commit | %d |" % sum(1 for r in rows if r["commit"]))
    out.append("| with no disposition recorded anywhere | %d |" % open_rows)
    out.append("")
    out.append("By severity: " + ", ".join("**%s** %d" % (k, bySeverity[k])
                                           for k in sorted(bySeverity)))
    out.append("")
    twice = sorted(set(r["ref"] for r in rows if " @" in r["unique"]))

    out.append("**Refs that appear in two documents** are shown as `REF @date`, and there are %d of them."
               % len(twice))
    out.append("Some are one finding revisited by a later pass; some are two different findings that were")
    out.append("given the same id. Both rows are here either way, so nothing is lost, but a citation of a")
    out.append("bare one is ambiguous and a NEW citation should use the dated form.")
    out.append("")
    out.append("They are: " + ", ".join("`%s`" % t for t in twice) + ".")
    out.append("")
    out.append("## The catalogue")
    out.append("")
    out.append("| Ref | Sev | What | Disposition | Cited by | Evidence | Commit | Document |")
    out.append("|---|---|---|---|---|---|---|---|")

    for r in rows:
        cited = r.get("cited", [])

        out.append("| `%s` | %s | %s | %s | %s | %s | %s | %s%s |" % (
            r["unique"], r["severity"], r["what"].replace("|", "\\|"),
            r["disposition"].replace("|", "\\|"),
            ("%d: %s" % (len(cited), ", ".join(sorted(cited)[:3]))) if cited else "",
            "`" + r["where"] + "`" if r["where"] else "",
            "`" + r["commit"] + "`" if r["commit"] else "",
            r["document"], (":%d" % r["line"]) if r["line"] else ""))

    out.append("")

    # THE ROLL.  `testEveryCitationResolves` holds the length of this list, and its javadoc sends the
    # reader here, so it has to exist - it did not, for the first four runs of this script.
    #
    # A bullet list rather than a table, deliberately: the guard reads the catalogue above by its table
    # rows, and a roll written as a table would be read back as ten findings that resolve themselves.
    out.append("## Citations with no finding behind them")
    out.append("")

    if orphans:
        out.append("These ids are cited from `src/` or `test/` and are **not findings anywhere**. Whatever")
        out.append("assigned them is gone. The citation now leads here, which is the most that can be")
        out.append("recovered: the comment is still explained by the file it sits in, and the reader is no")
        out.append("longer hunting a document that does not exist.")
        out.append("")
        out.append("`testEveryCitationResolves.testEveryCitationLeadsSomewhere` holds this list at **%d**."
                   % len(orphans))
        out.append("Add a citation to a finding that does not exist and it fails, naming the file.")
        out.append("")
        out.append("They cluster by prefix rather than scattering, which is the tell: a whole document is")
        out.append("missing, not a line. Where a row says *mentioned in* there is prose to read, but no")
        out.append("finding was ever filed under that id.")
        out.append("")

        for ref in orphans:
            where = sorted(used.get(ref, [])) if used else []

            mention = (outside or {}) and ref in (outside or [])

            out.append("- **`%s`** - cited in %s%s" % (
                ref,
                ", ".join("`%s`" % w for w in where) or "?",
                (" - mentioned in `%s`" % elsewhere[ref]) if mention and elsewhere else ""))
    else:
        out.append("None. Every id cited from `src/` or `test/` resolves to a finding above.")
        out.append("")
        out.append("Lower `DEAD_CITATIONS` in `testEveryCitationResolves` to 0 to keep it that way.")

    out.append("")

    io.open(OUT, "w", encoding="utf-8", newline="\n").write("\n".join(out))


def into_the_database(rows):
    """Loads the catalogue into the triage store, which is what everything else here queries.

    Imported lazily and failing softly: this script is also run from a checkout where the triage
    database has never been built, and a catalogue that refuses to render because a database is absent
    would be a worse tool than one that renders and says so.

    :param rows: the catalogue
    :return: how many rows were stored, or 0
    """
    try:
        sys.path.insert(0, os.path.join("docs", "manual-tests"))

        import triagedb

        conn = triagedb.connect()

        return triagedb.load_findings(conn, rows)

    except Exception as cannot:
        print("the finding table was not loaded (%s) - the markdown above is still current" % cannot)

        return 0


def main():
    rows = collect()

    clashes = disambiguate(rows)

    used = citations_in_the_code()

    for r in rows:
        r["cited"] = used.get(r["ref"], set())

    defined = set(r["ref"] for r in rows)

    # AND ANYWHERE ELSE IN docs/ THAT DEFINES ONE.  Not every finding was written in a review: the
    # reference documents and the manual-test entries assign ids too, and a citation that resolves
    # THERE is not an orphan - it just does not live in this folder.
    elsewhere = {}

    for base, dirs, files in os.walk("docs"):
        if ".triage-backups" in base.replace("\\", "/"):
            continue

        for name in files:
            if not name.endswith(".md") or name == "findings.md":
                continue

            path = os.path.join(base, name)

            if path.replace("\\", "/").startswith("docs/reviews/"):
                continue

            try:
                body = io.open(path, encoding="utf-8", errors="replace").read()
            except Exception:
                continue

            for ref in set(CITATION.findall(body)):
                elsewhere.setdefault(ref, path.replace("\\", "/"))

    # WHAT THE GUARD HOLDS: cited, and not a catalogued finding. An id that some document mentions in
    # passing without ever filing it is still a dead end - `VAL-C1` is named in one review whose own
    # prefix is `VB`, and there is no VAL document anywhere. Counting those as resolved is how the
    # earlier index reported a third as many dead citations as there are.
    orphans = sorted(ref for ref in used if ref not in defined)

    # Of those, the ones a document at least mentions. Worth separating in the roll: for these there is
    # something to read, even though there is no finding.
    outside = sorted(ref for ref in orphans if ref in elsewhere)

    seen = {}
    duplicates = []

    for r in rows:
        if r["ref"] in seen and seen[r["ref"]] != r["document"]:
            duplicates.append("%s in %s and %s" % (r["ref"], seen[r["ref"]], r["document"]))

        seen[r["ref"]] = r["document"]

    print("findings:   %d" % len(rows))
    print("documents:  %d" % len(set(r["document"] for r in rows)))
    print("with evidence: %d" % sum(1 for r in rows if r["where"]))
    print("with commit:   %d" % sum(1 for r in rows if r["commit"]))
    print("dispositioned: %d" % sum(1 for r in rows if r["disposition"] != "-"))
    print("refs used in two documents: %d (%d rows dated to disambiguate)"
          % (len(duplicates), clashes))
    print("ids cited from src/ or test/: %d" % len(used))
    print("  of those, catalogued:       %d" % (len(used) - len(orphans)))
    print("  defined outside docs/reviews:      %d" % len(outside))
    print("  ORPHANED - cited, defined nowhere: %d" % len(orphans))

    for ref in orphans[:10]:
        print("      %-12s cited in %s" % (ref, ", ".join(sorted(used[ref])[:2])))

    for d in duplicates[:8]:
        print("   ", d)

    if "--check" not in sys.argv:
        write(rows, orphans, used, outside, elsewhere)

        print("wrote", OUT)

        stored = into_the_database(rows)

        print("loaded %d rows into the finding table" % stored)


if __name__ == "__main__":
    main()
