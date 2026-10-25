# -*- coding: utf-8 -*-
"""Proves that `triagedb.py migrate` lost nothing: the markdown, the store and the text dump hold one record.

FR-136 Phase 1c.  Run it after `migrate` and before the markdown is deleted, at the repository root:

    py -3 docs/tools/prove-tracker-migration.py --before <a copy of triage.db taken before the migration>

It reads the markdown its own way - anchors, markers, headings and table rows by regular expression, not through
triage.py's parser or triagedb's cut - so a fault in the migration cannot hide by being made twice.  It counts:

  entries      every MT-### anchor, once (an entry kept word for word in two files counts once)
  comments     every line that opens with a `**Adam,` or `**Claude,` marker, in those entries
  verdicts     those whose marker line says one of the four results, and how many of them are Adam's
  **Adam,      every occurrence, in the entries, issues.md and tests.md's preamble - the generated parts of the
               files (the Ledger and archives sections, the archives' headers) and second copies are counted apart
  Inbox items  every `### OB-` / `### FR-` heading in issues.md's Inbox
  receipts     every row of its receipt table

and asks the same of the store and of the dump.  Then every entry's text, as `show` prints it and as the dump holds
it, is compared with its markdown block, normalising only the line endings; every Inbox item with its text; every
receipt with its row; and the findings and dead citations with the copy taken before.

Exit 0 when everything matches, 1 when anything does not.
"""

from __future__ import print_function

import argparse
import glob
import io
import os
import re
import sqlite3
import subprocess
import sys

ANCHOR = re.compile(r'^<a id="(mt-\d+)"></a>[ \t]*$', re.M)
MARKER = re.compile(r"^\*\*(Adam|Claude),[^*\n]*\*\*[ \t]*([^\n]*)$", re.M)
ITEM = re.compile(r"^### ((?:OB|FR)-\d+) - ", re.M)
RESULTS = ("works", "works, with notes", "does not work", "could not run this")
CELL = re.compile(r"(?<!\\)\|")


def read(path):
    with io.open(path, "rb") as fh:
        return fh.read().decode("utf-8").replace("\r\n", "\n")


def is_verdict(line_text):
    return line_text.strip().rstrip(".").strip().lower() in RESULTS


def from_markdown(folder):
    """The record as the markdown has it, read with nothing but regular expressions."""

    names = ["tests.md"] + sorted(os.path.basename(p) for p in glob.glob(os.path.join(folder, "tests-archive-*.md")))

    blocks = {}
    repeated = []
    adam_total = 0
    adam_elsewhere = 0

    for name in names:
        text = read(os.path.join(folder, name))

        adam_total += text.count("**Adam,")

        marks = list(ANCHOR.finditer(text))

        head = text[:marks[0].start()] if marks else text

        if name == "tests.md":
            # The preamble is kept; the Ledger and archives sections after it were generated.
            ledger = head.find("\n## Ledger")
            preamble = head[:ledger + 1] if ledger >= 0 else head
            adam_elsewhere += head.count("**Adam,") - preamble.count("**Adam,")
        else:
            adam_elsewhere += head.count("**Adam,")

        for i, m in enumerate(marks):
            block = text[m.start():marks[i + 1].start() if i + 1 < len(marks) else len(text)]

            tag = m.group(1).upper()

            if tag in blocks:
                if blocks[tag] != block:
                    raise SystemExit("%s is in two files with different text" % tag)

                repeated.append((tag, name))
                adam_elsewhere += block.count("**Adam,")
                continue

            blocks[tag] = block

    issues = read(os.path.join(folder, "issues.md"))

    adam_total += issues.count("**Adam,")

    inbox = issues[issues.index("\n## Inbox\n"):issues.index("\n## What has been picked up\n")]
    picked = issues[issues.index("\n## What has been picked up\n"):issues.index("\n## Where the older backlog is\n")]

    heads = list(ITEM.finditer(inbox))

    items = {}

    for i, m in enumerate(heads):
        items[m.group(1)] = inbox[m.start():heads[i + 1].start() if i + 1 < len(heads) else len(inbox)] \
            .rstrip("\n").rstrip("\n")

    rows = {}

    for line in picked.split("\n"):
        if not line.startswith("| ") or line.startswith("| Filed |"):
            continue

        cells = [c.strip().replace("\\|", "|") for c in CELL.split(line)[1:-1]]

        rows[cells[1]] = cells

    comments = verdicts = adam_verdicts = 0

    for block in blocks.values():
        for who, rest in MARKER.findall(block):
            comments += 1

            if is_verdict(rest):
                verdicts += 1
                adam_verdicts += who == "Adam"

    return {
        "blocks": blocks, "items": items, "rows": rows, "repeated": repeated,
        "counts": {
            "entries": len(blocks), "comments": comments, "verdicts": verdicts, "adam_verdicts": adam_verdicts,
            "adam_lines": adam_total - adam_elsewhere, "inbox": len(items), "receipts": len(rows),
        },
        "adam_total": adam_total, "adam_elsewhere": adam_elsewhere,
    }


def from_store(store, folder):
    """The record as the store has it, asked through triagedb - the door `show` uses."""

    sys.path.insert(0, folder)

    import triagedb

    conn = triagedb.connect(store)

    counts = triagedb.record_counts(conn)

    texts = dict((r[0], triagedb.entry_text(conn, r[0])) for r in conn.execute("SELECT tag FROM test"))
    shown = dict((tag, triagedb.show(conn, tag)) for tag in texts)
    items = dict((r[0], triagedb.issue_text(conn, r[0])) for r in conn.execute("SELECT ref FROM issue WHERE inbox = 1"))
    rows = dict((r["ref"], [r[k] for k in triagedb.RECEIPT_CELLS]) for r in conn.execute("SELECT * FROM receipt"))

    out = {
        "texts": texts, "shown": shown, "items": items, "rows": rows,
        "counts": {
            "entries": counts["tests"], "comments": counts["comments"], "verdicts": counts["verdicts"],
            "adam_verdicts": counts["adam_verdicts"], "adam_lines": counts["adam_lines"], "inbox": counts["inbox"],
            "receipts": counts["receipts"],
        },
        "findings": [tuple(r) for r in conn.execute("SELECT * FROM finding ORDER BY ref, document")],
        "dead": [tuple(r) for r in conn.execute("SELECT * FROM dead_citation ORDER BY ref")],
    }

    conn.close()

    return out


def records(path):
    """A dump file's records: [(header, {field: value})], a text field's lines joined back as they were."""

    out = []
    fields = None
    name = None
    lines = []

    def close():
        if name is not None:
            fields[name[0]] = "\n".join(lines) + ("" if name[1] else "\n")

    for line in read(path).split("\n") + [""]:
        if line.startswith("|"):
            lines.append(line[2:] if line.startswith("| ") else "")
            continue

        close()
        name = None

        if line.startswith("#") or not line:
            continue

        if line.startswith("["):
            fields = {}
            out.append((line[1:-1], fields))
            continue

        key, _, value = line.partition(":")

        if key.endswith(" (no newline at end)") and not value:
            name = (key[:-len(" (no newline at end)")], True)
            lines = []
        elif not value:
            name = (key, False)
            lines = []
            fields[key] = ""
        else:
            fields[key] = value[1:]

    return out


def from_dump(dump):
    """The record as the dump has it, read with this file's own reader."""

    tests = dict((h.split(" ")[1], f) for h, f in records(os.path.join(dump, "tests.txt")))

    said = {}
    comments = verdicts = adam_verdicts = adam = 0

    for header, f in records(os.path.join(dump, "comments.txt")):
        _, tag, seq = header.split(" ")

        said.setdefault(tag, []).append((int(seq), f["text"]))

        adam += f["text"].count("**Adam,")

        if tag.startswith("MT-"):
            comments += 1

            if f.get("kind") == "verdict":
                verdicts += 1
                adam_verdicts += f.get("who") == "Adam"

    texts = {}

    for tag, f in tests.items():
        adam += f["body"].count("**Adam,") + f["tail"].count("**Adam,")

        texts[tag] = f["body"] + "".join(t for _, t in sorted(said.get(tag, []))) + f["tail"]

    issues = records(os.path.join(dump, "issues.txt"))

    for _, f in issues:
        adam += f["text"].count("**Adam,")

    rows = records(os.path.join(dump, "receipts.txt"))

    for header, f in rows:
        adam += sum(f.get(k, "").count("**Adam,") for k in ("filed", "kind", "what", "state", "became"))
        adam += header.count("**Adam,")

    for _, f in records(os.path.join(dump, "notes.txt")):
        adam += f["text"].count("**Adam,")

    return {
        "texts": texts,
        "counts": {
            "entries": len(tests), "comments": comments, "verdicts": verdicts, "adam_verdicts": adam_verdicts,
            "adam_lines": adam, "inbox": sum(1 for _, f in issues if f.get("inbox") == "yes"), "receipts": len(rows),
        },
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--folder", default=os.path.join("docs", "manual-tests"),
                        help="where the markdown, triagedb.py and triage.db are")
    parser.add_argument("--store", help="the migrated store (default: triage.db in the folder)")
    parser.add_argument("--before", required=True, help="a copy of the store taken before the migration")
    args = parser.parse_args()

    folder = os.path.abspath(args.folder)
    store = args.store or os.path.join(folder, "triage.db")
    dump = os.path.join(os.path.dirname(os.path.abspath(store)), "dump")

    md = from_markdown(folder)
    db = from_store(store, folder)
    dm = from_dump(dump)

    bad = []

    print("%-14s %10s %10s %10s" % ("", "markdown", "store", "dump"))

    for key in ("entries", "comments", "verdicts", "adam_verdicts", "adam_lines", "inbox", "receipts"):
        a, b, c = md["counts"][key], db["counts"][key], dm["counts"][key]

        print("%-14s %10d %10d %10d  %s" % (key, a, b, c, "ok" if a == b == c else "MISMATCH"))

        if not a == b == c:
            bad.append(key)

    print("  ('**Adam,' in the markdown files: %d in all, of which %d in generated parts and second copies)"
          % (md["adam_total"], md["adam_elsewhere"]))

    for tag, name in md["repeated"]:
        print("  %s was also in %s, word for word" % (tag, name))

    def same(name, mine, theirs):
        differing = sorted(k for k in set(mine) | set(theirs) if mine.get(k) != theirs.get(k))

        print("%-60s %s" % (name, "ok (%d)" % len(mine) if not differing else "DIFFER: %s" % differing[:10]))

        if differing:
            bad.append(name)

    same("every entry's text, as the store joins it", md["blocks"], db["texts"])
    same("every entry, as `show` prints it (its text first)",
         md["blocks"], dict((t, s[:len(md["blocks"].get(t, ""))]) for t, s in db["shown"].items()))
    same("every entry's text, as the dump holds it", md["blocks"], dm["texts"])
    same("every Inbox item's text", md["items"], dict((r, t.rstrip("\n")) for r, t in db["items"].items()))
    same("every receipt's cells", md["rows"], db["rows"])

    # The command line, for a few: what `show` prints to a terminal is the text, byte for byte, before anything else.
    script = os.path.join(folder, "triagedb.py")

    if os.path.abspath(store) == os.path.abspath(os.path.join(folder, "triage.db")):
        tags = sorted(md["blocks"])[::max(1, len(md["blocks"]) // 6)]

        for tag in tags:
            out = subprocess.run([sys.executable, script, "show", tag], stdout=subprocess.PIPE).stdout

            if not out.decode("utf-8").replace("\r\n", "\n").startswith(md["blocks"][tag]):
                bad.append("show " + tag)

        print("%-60s %s" % ("`triagedb.py show` on the command line, %d of them" % len(tags),
                            "ok" if not [b for b in bad if b.startswith("show ")] else "DIFFER"))

    before = sqlite3.connect("file:" + os.path.abspath(args.before).replace("\\", "/") + "?mode=ro", uri=True)

    findings = [tuple(r) for r in before.execute("SELECT * FROM finding ORDER BY ref, document")]
    dead = [tuple(r) for r in before.execute("SELECT * FROM dead_citation ORDER BY ref")]

    before.close()

    for name, was, now in (("finding rows", findings, db["findings"]), ("dead citations", dead, db["dead"])):
        print("%-60s %s" % (name + ", against the copy taken before", "ok (%d)" % len(was) if was == now else
                            "DIFFER: %d before, %d after" % (len(was), len(now))))

        if was != now:
            bad.append(name)

    print("")
    print("NOTHING LOST" if not bad else "MISMATCH: " + ", ".join(bad))

    return 0 if not bad else 1


if __name__ == "__main__":
    sys.exit(main())
