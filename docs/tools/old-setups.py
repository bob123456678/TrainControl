#!/usr/bin/env python3
"""Compares two builds' dumps of the same setups, file by file and byte by byte, for docs/tools/old-setups.sh (FR-111).

    py -3 docs/tools/old-setups.py <base-dump> <new-dump>

Prints one line per fixture with what it compared, the totals, whether a save with no change equals the file as it came,
and the first differing lines of anything that differs.  Exit 0 only when every file is the same.
"""
import difflib
import os
import sys


def files_under(root):
    out = {}
    for folder, _, names in os.walk(root):
        for name in names:
            path = os.path.join(folder, name)
            out[os.path.relpath(path, root).replace(os.sep, "/")] = path
    return out


def read(path):
    with open(path, "rb") as f:
        return f.read()


def main():
    base, new = sys.argv[1], sys.argv[2]
    a, b = files_under(base), files_under(new)
    different = 0
    identical = 0
    lines = 0
    for rel in sorted(set(a) | set(b)):
        if rel not in a or rel not in b:
            print("ONLY IN " + ("base" if rel in a else "new") + ": " + rel)
            different += 1
            continue
        x, y = read(a[rel]), read(b[rel])
        lines += y.count(b"\n")
        if x == y:
            identical += 1
            continue
        different += 1
        print("DIFFERS: " + rel)
        diff = difflib.unified_diff(x.decode("utf-8", "replace").splitlines(), y.decode("utf-8", "replace").splitlines(),
                                    lineterm="", n=0)
        for shown, line in enumerate(diff):
            if shown >= 12:
                print("  ...")
                break
            print("  " + line[:400])

    fixtures = sorted(d for d in os.listdir(new) if os.path.isdir(os.path.join(new, d)))
    unchanged_same_as_input = 0
    unchanged_total = 0
    for fixture in fixtures:
        given = files_under(os.path.join(new, fixture, "input"))
        saved = files_under(os.path.join(new, fixture, "saved-unchanged"))
        for rel, path in saved.items():
            unchanged_total += 1
            if rel in given and read(given[rel]) == read(path):
                unchanged_same_as_input += 1

    counts = os.path.join(new, "counts.txt")
    if os.path.exists(counts):
        print(open(counts, encoding="utf-8").read().rstrip())
    print("files compared: %d, identical: %d, different: %d; %d lines" % (identical + different, identical, different, lines))
    print("saved with no change, new build: %d of %d files byte-identical to the file as it came" %
          (unchanged_same_as_input, unchanged_total))
    return 1 if different else 0


if __name__ == "__main__":
    sys.exit(main())
