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
import os
import json
import sys


def atom_of(node):
    """A stable name for a leaf, so the same sensor is the same variable in both trees."""
    rc = node.get("routeCommand") or node.get("command") or {}

    parts = []

    # CANONICALLY, not by repr (VD10-C13).
    #
    # A value that is itself a dict formatted with %s comes out in insertion order, so two engines
    # whose LinkedHashMap filled in a different order would give one leaf two names - and the tool
    # would print NOT equivalent for two trees the byte-identity check beside it calls identical.
    # That check uses sort_keys; this now does too.
    def rendered(value):
        if isinstance(value, (dict, list)):
            return json.dumps(value, sort_keys=True)

        return "%s" % (value,)

    for key in ("type", "address", "protocol", "setting", "name", "state", "s88"):
        if key in node:
            parts.append("%s=%s" % (key, rendered(node[key])))

        if key in rc:
            parts.append("rc.%s=%s" % (key, rendered(rc[key])))

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


def leaf(address):
    return {"type": "NodeRouteCommand", "address": address}


def self_test():
    """The three pairs that prove this can fail, kept rather than described (VD10-C12).

    They were run once when the comparison was written and thrown away, and the commit message is the
    only record that they passed.  A comparison nothing in the repo can fail is a comparison nobody
    can check again - which is exactly the property this module exists to check for somebody else.
    """
    AND = lambda l, r: {"type": "NodeAnd", "left": l, "right": r}
    OR = lambda l, r: {"type": "NodeOr", "left": l, "right": r}
    GRP = lambda x: {"type": "NodeGroup", "expressions": [x]}

    cases = [
        # same meaning, different shape - normalize inserting a bracket is the whole reason this
        # compares by truth table rather than by structure
        ("reshaped", AND(OR(leaf(1), leaf(2)), leaf(4)),
                     AND(GRP(OR(leaf(1), leaf(2))), leaf(4)), True),

        # the IPR-B2 corruption itself: an AND that became an OR
        ("mangled", OR(leaf(3), AND(GRP(OR(leaf(1), leaf(2))), leaf(4))),
                    OR(OR(leaf(3), GRP(OR(leaf(1), leaf(2)))), GRP(leaf(4))), False),

        ("identical", AND(leaf(7), leaf(8)), AND(leaf(7), leaf(8)), True),
    ]

    failures = []

    for name, a, b, expected in cases:
        found = set()

        atoms(a, found)
        atoms(b, found)

        order = sorted(found)

        got = table(a, order) == table(b, order)

        print("  %-10s equivalent=%-5s expected=%s" % (name, got, expected))

        if got != expected:
            failures.append(name)

    if failures:
        print("")
        print("*** THE COMPARISON DOES NOT DISCRIMINATE: %s ***" % failures)

        return 1

    print("")
    print("the comparison tells the three cases apart")

    return 0


def main(argv):
    if len(argv) > 1 and argv[1] == "--self-test":
        return self_test()

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

    compared = len(same) + len(different)

    # A FLOOR, because zero compared is not zero different (VD10-B5).
    #
    # The driver skips a route with no "conditions" key, writes its file even when that is no lines,
    # and exits 0 whatever happens.  So a routes.json in a shape this does not recognise - a renamed
    # key, a different wrapper - produced "NOT equivalent: 0" and a passing section, having read
    # nothing.  The test beside this one has carried the same floor since it was written, and says
    # why: "a green result here would mean nothing".
    #
    # Overridable for a deliberately small corpus, and loud when it bites.
    floor = int(os.environ.get("TC_CONDITION_FLOOR", "20"))

    print("conditions compared:      %d" % compared)
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

    if compared < floor:
        print("")
        print("*** ONLY %d CONDITION(S) WERE COMPARED, WHICH IS FEWER THAN %d ***" % (compared, floor))
        print("")
        print("So this section has not checked what it says it checked.  Either the routes file is in")
        print("a shape ConditionParityDriver does not recognise, or it is the wrong file.  Set")
        print("TC_CONDITION_FLOOR if the corpus really is this small.")

        return 2

    # AND ONTO DISK, which the usage line has always advertised (VD10-C10).
    #
    # Without this the answer lived in terminal scrollback: the parity report never mentioned
    # conditions, so "a section of the parity report" was true of nothing anybody could read later.
    if len(argv) > 3:
        with io.open(argv[3], "w", encoding="utf-8") as report:
            report.write(u"## Route conditions, 2.8.1 against 3.0.0" + u"\n\n")
            report.write(u"Each condition parsed by BOTH engines and compared by truth table, so two")
            report.write(u" trees that\nbracket differently and mean the same thing are not a")
            report.write(u" difference.\n\n")
            report.write(u"| | |\n|---|---|\n")
            report.write(u"| conditions compared | %d |\n" % compared)
            report.write(u"| logically equivalent | %d |\n" % len(same))
            report.write(u"| of which byte-identical | %d |\n" % identical)
            report.write(u"| of which reshaped | %d |\n" % (len(same) - identical))
            report.write(u"| **not equivalent** | **%d** |\n" % len(different))
            report.write(u"| could not be compared | %d |\n" % len(unreadable))
            report.write(u"| present in only one | %d |\n\n" % len(only_one))

            for name, a, b in different:
                report.write(u"### %s\n\n" % name)
                report.write(u"```\n2.8.1: %s\n3.0.0: %s\n```\n\n" % (a, b))

    return 1 if (different or unreadable or only_one) else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
