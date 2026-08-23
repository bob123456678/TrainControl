# -*- coding: utf-8 -*-
"""
Which squares the autonomy FILES say a locomotive is standing on.

For MT-072, and for anything else where the question is "did that really get saved, or am I looking at
a stale screen". It reads the configuration files directly and reports nothing else - so if a train is
on the diagram and not in this list, the diagram is out of date; if it is in this list and not on the
diagram, the file is right and the view is wrong.

    py -3 docs\\manual-tests\\where-are-the-trains.py <layout folder>

The layout folder is the one holding config\\autonomy and config\\gleisbilder - the folder the
application loads your railway from. With no argument it tries cs2_sample_layout.
"""

import glob
import io
import json
import os
import sys


def placements(path):
    """Every square this configuration puts a locomotive on, as (square, name)."""

    try:
        data = json.load(io.open(path, encoding='utf-8'))
    except ValueError as bad:
        return None, str(bad)

    out = []

    for square, extras in sorted(data.get('points', {}).items()):
        if not isinstance(extras, dict):
            continue

        loc = extras.get('loc')

        # A placement is an object; a hand-edited file can carry a bare string
        if isinstance(loc, dict):
            out.append((square, loc.get('name')))
        elif isinstance(loc, str) and loc.strip():
            out.append((square, loc))

    return out, None


def main(argv):
    folder = argv[1] if len(argv) > 1 else 'cs2_sample_layout'

    pattern = os.path.join(folder, 'config', 'autonomy', 'configuration-*.json')

    files = sorted(glob.glob(pattern))

    if not files:
        print('No configuration files under %s' % pattern)
        print('')
        print('That is either the wrong folder, or this layout has no autonomy configuration at all.')
        print('The right folder is the one holding config\\autonomy beside config\\gleisbilder.')

        return 1

    total = 0

    for path in files:
        found, bad = placements(path)

        print('')
        print('== %s' % os.path.basename(path))

        if bad is not None:
            print('   UNREADABLE - %s' % bad)
            print('   That is a finding on its own: the application cannot load this either.')
            continue

        if not found:
            print('   no locomotives placed')
            continue

        for square, name in found:
            print('   %-28s %s' % (square, name))

        total += len(found)

    print('')
    print('%d placement(s) in the files.' % total)

    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
