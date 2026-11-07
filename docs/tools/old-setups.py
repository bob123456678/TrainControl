#!/usr/bin/env python3
"""Compares two builds' dumps of the same setups, file by file, for docs/tools/old-setups.sh (FR-111).

    py -3 docs/tools/old-setups.py <base-dump> <new-dump>

Byte for byte apart from CRLF.  The gate is that every file is the same: what each build wrote on a load and save with no
change (saved-unchanged/) and after the exit's capture (saved-captured/), every path answer, and every Return Home outcome,
audit, end position and direction.  It also needs some Return Home plans and timetable entries to have been compared at
all.  Exit 0 only when the gate holds.

Two things are printed as information and do not fail:

- How many saved files equal the file as it came: a build may well tidy an old file, and the question is whether the two
  builds tidy it alike.
- Each Return Home plan's moves (home-plans.txt).  NOT proved identical: HomeStaging's search has a wall clock budget that
  picks among plans, so one setup gives 4 moves or 8 run to run inside one JVM, in 3.0.8 as in later builds.
"""
import difflib
import json
import os
import re
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
        return f.read().replace(b"\r\n", b"\n")


def total(counts_text, key):
    return sum(int(v) for v in re.findall(r"(?:^|[{ ])" + re.escape(key) + r"=(\d+)", counts_text, re.M))


PLANS = "home-plans.txt"


def plan_sizes(path):
    return [line.strip() for line in read(path).decode("utf-8").splitlines() if line.startswith("round ")]


def how_it_differs(given, saved):
    try:
        a, b = json.loads(given), json.loads(saved)
    except ValueError:
        return "not JSON"
    if a == b:
        return "same content, other layout"
    if not isinstance(a, dict) or not isinstance(b, dict):
        return "content"
    return "keys " + ", ".join(sorted(k for k in set(a) | set(b) if a.get(k) != b.get(k)))


def main():
    base, new = sys.argv[1], sys.argv[2]
    a, b = files_under(base), files_under(new)
    different = identical = lines = 0
    saved_same = saved_total = 0
    plans_same = plans_total = 0
    plans_apart = []
    for rel in sorted(set(a) | set(b)):
        if rel.endswith("/" + PLANS) and rel in a and rel in b:
            plans_total += 1
            if read(a[rel]) == read(b[rel]):
                plans_same += 1
            else:
                plans_apart.append("%s: older %s; this build %s" % (rel, plan_sizes(a[rel]), plan_sizes(b[rel])))
            continue
        is_saved = "/saved-unchanged/" in rel
        if rel not in a or rel not in b:
            print("ONLY IN " + ("base" if rel in a else "new") + ": " + rel)
            different += 1
            saved_total += is_saved
            continue
        x, y = read(a[rel]), read(b[rel])
        lines += y.count(b"\n")
        saved_total += is_saved
        if x == y:
            identical += 1
            saved_same += is_saved
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

    matches_input = compared_to_input = 0
    how = {}
    for fixture in sorted(d for d in os.listdir(new) if os.path.isdir(os.path.join(new, d))):
        given = files_under(os.path.join(new, fixture, "input"))
        saved = files_under(os.path.join(new, fixture, "saved-unchanged"))
        for rel, path in saved.items():
            if rel not in given:
                how["no file came in"] = how.get("no file came in", 0) + 1
                continue
            compared_to_input += 1
            if read(given[rel]) == read(path):
                matches_input += 1
            else:
                reason = how_it_differs(read(given[rel]), read(path))
                how[reason] = how.get(reason, 0) + 1

    counts = os.path.join(new, "counts.txt")
    counts_text = open(counts, encoding="utf-8").read() if os.path.exists(counts) else ""
    print(counts_text.rstrip())
    ready = total(counts_text, "plans ready")
    played = total(counts_text, "timetable entries played")
    print("totals: %d path answers, %d simulated trips, %d Return Home plans ready (%d brought every train home), "
          "%d timetable entries played, %d recorded timetables ran to the end" %
          (total(counts_text, "path answers"), total(counts_text, "simulated trips"), ready,
           total(counts_text, "plans that brought every train home"), played,
           total(counts_text, "recorded timetables that ran to the end")))
    print("files compared: %d, identical: %d, different: %d; %d lines, CRLF aside" % (identical + different, identical,
                                                                                     different, lines))
    print("load and save with no change: %d of %d files the two builds write are the same, CRLF aside" %
          (saved_same, saved_total))
    print("information: %d of %d of those saved files equal the file as it came, CRLF aside; the others: %s" %
          (matches_input, compared_to_input, how))

    print("information, NOT proved identical: the moves of Return Home plans, as the planner's wall clock budget picks among "
          "plans within one build; the same moves on %d of %d setups" % (plans_same, plans_total))
    for line in plans_apart:
        print("  " + line)

    failed = different > 0 or saved_total == 0 or saved_same != saved_total or ready == 0 or played == 0
    if saved_total == 0 or ready == 0 or played == 0:
        print("NOT PROVED: nothing was compared for a load and save, a Return Home plan or a timetable run")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
