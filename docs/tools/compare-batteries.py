# -*- coding: utf-8 -*-
"""Compare two batteries claim by claim: the same test methods ran, and the same ones passed (FR-136 Phase 1a).

Usage:

    python docs/tools/compare-batteries.py <before tng-run> <after tng-run> [--same-as-before CLASS ...]
                                           [--only CLASS ...]

Each argument is a battery's `tng-run` folder (TC_SCRATCH/tng-run), holding one TestNG output folder per class.
Every `test-method` that is not a configuration method (@Before..., @After...) is one claim, keyed by its class, its
name, its parameters, and its place among claims with the same three - so a data provider's rows are claims of their
own.  A class whose JVM left no testng-results.xml ran no claims at all, which is reported rather than skipped.

What it says:
- claims that ran in one battery and not the other (a class that did not run, a method gone or added);
- claims whose status differs (PASS, FAIL, SKIP);
- for the AFTER battery, every claim that is not PASS, except in the classes named after --same-as-before, which are
  held only to their status before: the classes that pass only in the main checkout, whose live layout comes from a
  preference keyed by the checkout's folder.
- the time each battery spent inside the tests, and the slowest classes' times before and after.

Exit status: 0 when the claims and their statuses match and every other claim after is PASS; 1 otherwise.
"""
import collections
import os
import sys
import xml.etree.ElementTree as ET


def claims_of(run_dir, only=None):
    """{class: {key: status}} and {class: suite milliseconds} for one battery's tng-run folder."""
    claims = {}
    millis = {}

    for name in sorted(os.listdir(run_dir)):
        folder = os.path.join(run_dir, name)

        if not os.path.isdir(folder) or (only and name not in only):
            continue

        results = os.path.join(folder, 'testng-results.xml')

        if not os.path.isfile(results):
            claims[name] = None
            continue

        root = ET.parse(results).getroot()

        millis[name] = sum(int(s.get('duration-ms', '0')) for s in root.iter('suite'))

        seen = collections.Counter()
        found = {}

        for cls in root.iter('class'):
            for m in cls.iter('test-method'):
                if m.get('is-config') == 'true':
                    continue

                params = tuple((v.text or '').strip() for v in m.iter('value'))
                base = (cls.get('name'), m.get('name'), params)

                seen[base] += 1

                found[base + (seen[base],)] = m.get('status')

        claims[name] = found

    return claims, millis


def describe(key):
    cls, method, params, n = key
    shown = '%s.%s' % (cls, method)

    if params:
        shown += '(%s)' % ', '.join(p[:40] for p in params)

    if n > 1:
        shown += ' #%d' % n

    return shown


def main(argv):
    args = list(argv[1:])
    same = set()
    only = set()
    paths = []
    bucket = paths

    for a in args:
        if a == '--same-as-before':
            bucket = same
        elif a == '--only':
            bucket = only
        elif bucket is paths:
            paths.append(a)
        else:
            bucket.add(a)

    if len(paths) != 2:
        print(__doc__)
        return 2

    before, before_ms = claims_of(paths[0], only)
    after, after_ms = claims_of(paths[1], only)

    problems = 0
    lines = []

    classes = sorted(set(before) | set(after))

    total_before = sum(len(c) for c in before.values() if c)
    total_after = sum(len(c) for c in after.values() if c)

    missing_classes = []
    status_changed = []
    only_before = []
    only_after = []
    not_passing = []
    same_kept = []

    for cls in classes:
        b = before.get(cls)
        a = after.get(cls)

        if b is None or a is None:
            missing_classes.append('%s: %s before, %s after' % (cls, 'no results' if b is None else '%d claims' % len(b),
                                                              'no results' if a is None else '%d claims' % len(a)))
            continue

        for key in sorted(set(b) - set(a)):
            only_before.append(describe(key))

        for key in sorted(set(a) - set(b)):
            only_after.append(describe(key))

        for key in sorted(set(a) & set(b)):
            if a[key] != b[key]:
                status_changed.append('%s: %s before, %s after' % (describe(key), b[key], a[key]))

        for key in sorted(a):
            if a[key] != 'PASS':
                if cls in same:
                    if key in b and b[key] == a[key]:
                        same_kept.append('%s: %s before and after' % (describe(key), a[key]))
                else:
                    not_passing.append('%s: %s' % (describe(key), a[key]))

    print('claims before: %d in %d classes; after: %d in %d classes' % (
        total_before, sum(1 for c in before.values() if c is not None),
        total_after, sum(1 for c in after.values() if c is not None)))

    print('time inside the tests: %.1f min before, %.1f min after' % (
        sum(before_ms.values()) / 60000.0, sum(after_ms.values()) / 60000.0))

    for title, items in (('classes without results in one battery', missing_classes),
                         ('claims that ran only before', only_before),
                         ('claims that ran only after', only_after),
                         ('claims whose status changed', status_changed),
                         ('claims not passing after (outside --same-as-before)', not_passing)):
        print('')
        print('%s: %d' % (title, len(items)))

        for item in items[:200]:
            print('  ' + item)

        if len(items) > 200:
            print('  ... and %d more' % (len(items) - 200))

        problems += len(items)

    if same_kept:
        print('')
        print('not passing, the same as before (--same-as-before): %d' % len(same_kept))

        for item in same_kept:
            print('  ' + item)

    print('')
    print('slowest classes, before -> after:')

    for cls in sorted(before_ms, key=lambda c: -before_ms[c])[:15]:
        print('  %7.1f s -> %7.1f s  %s' % (before_ms[cls] / 1000.0, after_ms.get(cls, 0) / 1000.0, cls))

    print('')
    print('SAME CLAIMS, SAME RESULTS' if problems == 0 else 'DIFFERENT: %d item(s) above' % problems)

    return 0 if problems == 0 else 1


if __name__ == '__main__':
    sys.exit(main(sys.argv))
