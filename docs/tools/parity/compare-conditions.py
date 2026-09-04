# -*- coding: utf-8 -*-
"""Are 2.8.1's parsed route conditions logically equivalent to 3.0.0's?

Adam, 2026-09-03: *"we would want to parse the JSON into a NodeExpression in the old one, and see if
3.0.0 has logically equivalent expressions in those routes."*

`ConditionParityDriver` runs under each jar and re-emits what that engine's `fromJSON` actually built.
This compares the two, and it compares them by **truth table** rather than by shape - the whole reason
the question is worth asking is that `normalize` inserts brackets, so two trees that differ
structurally may mean exactly the same thing, and a structural diff would report noise while missing
the case that matters.

Each condition is a boolean function of the accessory/feedback states it names.  Enumerate every
assignment of those atoms, evaluate both trees, and compare the result vectors.  Two trees are
equivalent exactly when the vectors match.

Usage: compare-conditions.py <old.tsv> <new.tsv> [report.md]
"""
from __future__ import print_function

import io
import itertools
import json
import sys


def atom_of(node):
    """A stable name for a leaf, so the same sensor is the same variable in both trees."""
    rc = node.get("routeCommand") or node.get("command") or {}

    parts = []

    for key in ("type", "address", "protocol", "setting", "name", "state", "s88"):
        if key in node:
            parts.append("%s=%s" % (key, node[key]))

        if key in rc:
            parts.append("rc.%s=%s" % (key, rc[key]))

    if not parts:
        # Fall back to the whole leaf, which is still stable between the two engines
        parts.append(json.dumps(node, sort_keys=True))

    return "|".join(parts)


def atoms(node, into):
    t = node.get("type")

    if t in ("NodeAnd", "NodeOr"):
        atoms(node["left"], into)
        atoms(node["right"], into)
    elif t == "NodeGroup":
        for e in node.get("expressions") or []:
            atoms(e, into)
    else:
        into.add(atom_of(node))


def evaluate(node, values):
    t = node.get("type")

    if t == "NodeAnd":
        return evaluate(node["left"], values) and evaluate(node["right"], values)

    if t == "NodeOr":
        return evaluate(node["left"], values) or evaluate(node["right"], values)

    if t == "NodeGroup":
        inside = node.get("expressions") or []

        if not inside:
            return True

        # A group is its contents; more than one is an implicit AND, which is how the writer emits it
        out = evaluate(inside[0], values)

        for e in inside[1:]:
            out = out and evaluate(e, values)

        return out

    return values[atom_of(node)]


def table(tree, order):
    """The truth vector over a fixed atom order, as a string."""
    bits = []

    for combo in itertools.product((False, True), repeat=len(order)):
        values = dict(zip(order, combo))

        bits.append("1" if evaluate(tree, values) else "0")

    return "".join(bits)


def load(path):
    rows = {}

    for line in io.open(path, encoding="utf-8"):
        line = line.rstrip("\n").rstrip("\r")

        if not line:
            continue

        parts = line.split("\t")

        if len(parts) < 4:
            parts = parts + [""] * (4 - len(parts))

        label, name, status, payload = parts[0], parts[1], parts[2], parts[3]

        rows[name] = (status, payload)

    return rows


def main(argv):
    if len(argv) < 3:
        print(__doc__)

        return 2

    old = load(argv[1])
    new = load(argv[2])

    same = []
    different = []
    unreadable = []
    only_one = []

    for name in sorted(set(old) | set(new)):
        if name not in old or name not in new:
            only_one.append(name)
            continue

        old_status, old_json = old[name]
        new_status, new_json = new[name]

        if old_status != "OK" or new_status != "OK":
            unreadable.append((name, old_status, new_status))
            continue

        a = json.loads(old_json)
        b = json.loads(new_json)

        found = set()

        atoms(a, found)
        atoms(b, found)

        order = sorted(found)

        if len(order) > 20:
            unreadable.append((name, "too many atoms (%d)" % len(order), ""))
            continue

        if table(a, order) == table(b, order):
            same.append((name, json.dumps(a, sort_keys=True) == json.dumps(b, sort_keys=True)))
        else:
            different.append((name, old_json, new_json))

    identical = sum(1 for _, s in same if s)

    print("conditions compared:      %d" % (len(same) + len(different)))
    print("logically equivalent:     %d" % len(same))
    print("  of which byte-identical:%d" % identical)
    print("  of which reshaped:      %d" % (len(same) - identical))
    print("NOT equivalent:           %d" % len(different))
    print("could not be compared:    %d" % len(unreadable))
    print("present in only one:      %d" % len(only_one))

    for name, a, b in different:
        print("")
        print("  DIFFERENT: %s" % name)
        print("    2.8.1: %s" % a)
        print("    3.0.0: %s" % b)

    for name, o, n in unreadable:
        print("  UNCOMPARED: %s (%s / %s)" % (name, o, n))

    for name in only_one:
        print("  ONE SIDE ONLY: %s" % name)

    return 1 if (different or unreadable or only_one) else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
