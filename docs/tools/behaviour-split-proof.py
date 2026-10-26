#!/usr/bin/env python3
"""Proves the behavior reference lost nothing when it was split into area files.

    py -3 docs/tools/behaviour-split-proof.py [--old <rev>]

Run from the project root.  Reads the single-file reference as <rev> had it (default: the commit before the one that
added `docs/reference/behaviour/`, or HEAD while that is uncommitted) and the new set as the working tree has it: the
index `docs/reference/behaviour.md` and every `docs/reference/behaviour/*.md`.  Checks, and prints the counts:
  - every OB-, MT- and FR- id in the old file appears in the new set;
  - every quote attributed to Adam appears verbatim in the new set (markdown emphasis, blockquote marks and line
    breaks aside).  A quote is Adam's when it is a "..." string in a paragraph that names him; an *italic* answer right
    after "Adam ...:"; or a blockquote whose paragraph, or the one before it, names him ("His ruling" included);
  - every heading of the old file is a heading exactly once in the new set, so "behaviour.md 5c" names one section;
  - the index has a row for every section (a numbered heading, or an unnumbered one at the top level of its area
    file), keyed by its number or its heading, linking to the area file that holds it.
Exits 1 on any loss.  The review ids (RSA52-B1 and the like) are counted, not required: the finding store keeps them.
"""
import argparse
import glob
import io
import os
import re
import subprocess
import sys

INDEX = "docs/reference/behaviour.md"
AREA_DIR = "docs/reference/behaviour"

TRACKER_ID = re.compile(r"\b(?:OB|MT|FR)-\d+\b")
REVIEW_ID = re.compile(r"\b(?!(?:OB|MT|FR)-)[A-Z][A-Z0-9]{1,7}-[A-Z]{0,2}\d+[a-z]?\b")
HEADING = re.compile(r"^(#{1,6})\s+(.*?)\s*$")
NUMBERED = re.compile(r"^(\d+[a-z]?)\.\s")
QUOTED = re.compile(r'"([^"]{3,}?)"', re.S)
ITALIC_ANSWER = re.compile(r'Adam[^.*]{0,200}?:\s*\*([^*"]{3,}?)\*')
INDEX_ROW = re.compile(r"^\|\s*([^|]*?)\s*\|\s*([^|]*?)\s*\|\s*\[[^\]]*\]\(behaviour/([^)#]+)")


def git(*args):
    return subprocess.check_output(("git",) + args).decode("utf-8")


def default_old_rev():
    added = git("log", "--diff-filter=A", "--format=%H", "--", AREA_DIR + "/").split()
    return added[-1] + "^" if added else "HEAD"


def lf(text):
    return text.replace("\r\n", "\n")


def flat(text):
    """The text as a reader sees it: no blockquote marks, no emphasis, one space for every run of white space."""
    text = re.sub(r"(?m)^\s*>\s?", "", text)
    text = text.replace("*", "").replace("’", "'")
    return re.sub(r"\s+", " ", text).strip()


def paragraphs(text):
    return [p for p in re.split(r"\n\s*\n", text) if p.strip()]


def adams_quotes(text):
    quotes = []
    paras = paragraphs(text)
    for i, para in enumerate(paras):
        names_him = "Adam" in para
        if names_him:
            quotes.extend(m.group(1) for m in QUOTED.finditer(para))
            quotes.extend(m.group(1) for m in ITALIC_ANSWER.finditer(para))
        if para.lstrip().startswith(">"):
            before = paras[i - 1] if i else ""
            if names_him or "Adam" in before or "His ruling" in before:
                quotes.append(para)
    seen, out = set(), []
    for q in quotes:
        f = flat(q)
        if f and f not in seen:
            seen.add(f)
            out.append(f)
    return out


def headings(text):
    out = []
    in_fence = False
    for line in text.split("\n"):
        if line.startswith("```"):
            in_fence = not in_fence
        if in_fence:
            continue
        m = HEADING.match(line)
        if m:
            out.append((len(m.group(1)), m.group(2)))
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--old", help="the revision holding the single-file reference")
    args = ap.parse_args()
    rev = args.old or default_old_rev()

    old = lf(git("show", "%s:%s" % (rev, INDEX)))
    with io.open(INDEX, encoding="utf-8") as fh:
        index = lf(fh.read())
    areas = {}
    for path in sorted(glob.glob(os.path.join(AREA_DIR, "*.md"))):
        with io.open(path, encoding="utf-8") as fh:
            areas[os.path.basename(path)] = lf(fh.read())
    new_all = index + "\n" + "\n".join(areas.values())
    new_flat = flat(new_all)
    failures = 0

    print("old: %s:%s, %d lines; new: index %d lines + %d area files, %d lines"
          % (rev, INDEX, old.count("\n"), index.count("\n"), len(areas), sum(t.count("\n") for t in areas.values())))

    ids = sorted(set(TRACKER_ID.findall(old)))
    lost = [i for i in ids if not re.search(r"\b%s\b" % re.escape(i), new_all)]
    print("tracker ids (OB/MT/FR): %d in the old file, %d kept, %d lost" % (len(ids), len(ids) - len(lost), len(lost)))
    for i in lost:
        print("  LOST id " + i)
    failures += len(lost)

    quotes = adams_quotes(old)
    lost = [q for q in quotes if q not in new_flat]
    print("quotes attributed to Adam: %d in the old file, %d kept verbatim, %d lost"
          % (len(quotes), len(quotes) - len(lost), len(lost)))
    for q in lost:
        print("  LOST quote: " + q[:160])
    failures += len(lost)

    old_heads = [h for _, h in headings(old)]
    where, level = {}, {}
    for name, text in sorted(areas.items()) + [("(index)", index)]:
        for lv, h in headings(text):
            where.setdefault(h, []).append(name)
            level[h] = lv
    bad = [h for h in old_heads if len(where.get(h, [])) != 1]
    numbered = [h for h in old_heads if NUMBERED.match(h)]
    print("headings: %d in the old file (%d numbered), %d each a heading exactly once in the new set"
          % (len(old_heads), len(numbered), len(old_heads) - len(bad)))
    for h in bad:
        print("  HEADING %r is a heading %d times: %s" % (h, len(where.get(h, [])), where.get(h, [])))
    failures += len(bad)

    rows = {}
    for line in index.split("\n"):
        m = INDEX_ROW.match(line)
        if m:
            key = m.group(1) if m.group(1) not in ("", "-") else m.group(2)
            rows[key] = m.group(3)
    # A section is a numbered heading, or an unnumbered one its area file puts at the top level (##).
    sections = [h for h in old_heads if where.get(h, ["(index)"])[0] != "(index)"
                and (NUMBERED.match(h) or level.get(h) == 2)]
    missing = []
    for h in sections:
        home = where[h][0]
        n = NUMBERED.match(h)
        key = n.group(1) if n else h
        if rows.get(key) != home:
            missing.append("%s -> %s (index says %s)" % (key, home, rows.get(key)))
    print("index rows: %d sections, %d mapped to the area file that holds them"
          % (len(sections), len(sections) - len(missing)))
    for m in missing:
        print("  INDEX " + m)
    failures += len(missing)

    review_old = set(REVIEW_ID.findall(old))
    review_new = set(REVIEW_ID.findall(new_all))
    print("review ids (not required): %d in the old file, %d still named" % (len(review_old), len(review_old & review_new)))

    print("RESULT: %s" % ("nothing lost" if failures == 0 else "%d LOST" % failures))
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
