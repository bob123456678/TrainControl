# -*- coding: utf-8 -*-
"""Compare two bounds dumps as multisets of rectangles.

Usage: compare.py <before dir> <after dir>

Every component is a (kind, text, x, y, w, h) tuple and the two runs are compared as bags of those.
Anything in one bag and not the other MOVED, APPEARED or WENT - which is the only question OB-115 and
FR-028's centring ask.  The class name is normalised, because renaming the label class reorders a
sorted file without moving a single pixel.
"""
import collections
import io
import os
import re
import sys

before_dir, after_dir = sys.argv[1], sys.argv[2]

LINE = re.compile(r'^(\S+)\s+(.*?)\s+x=\s*(-?\d+) y=\s*(-?\d+) w=\s*(-?\d+) h=\s*(-?\d+)\s*$')


def read(path):
    bag = collections.Counter()
    header = ''

    for line in io.open(path, encoding='utf-8'):
        line = line.rstrip('\n')

        if line.startswith('page '):
            header = line
            continue

        m = LINE.match(line)

        if not m:
            continue

        kind, text, x, y, w, h = m.groups()

        kind = 'tile' if kind == 'LayoutLabel' else 'label'

        bag[(kind, text, int(x), int(y), int(w), int(h))] += 1

    return header, bag


tiles_moved = 0
labels_moved = 0
pages = 0

for name in sorted(os.listdir(before_dir)):
    if not name.endswith('.txt'):
        continue

    after_path = os.path.join(after_dir, name)

    if not os.path.exists(after_path):
        print('%s: MISSING from the after run' % name)
        continue

    pages += 1

    head_a, a = read(os.path.join(before_dir, name))
    head_b, b = read(after_path)

    gone = a - b
    came = b - a

    tiles = sum(n for key, n in (gone + came).items() if key[0] == 'tile')
    labels = sum(n for key, n in (gone + came).items() if key[0] == 'label')

    tiles_moved += tiles
    labels_moved += labels

    flag = ''

    if head_a != head_b:
        flag = '   PANEL SIZE CHANGED'

    print('%-24s tiles differing: %-4d labels differing: %-4d%s' % (name, tiles, labels, flag))

    if head_a != head_b:
        print('      was %s' % head_a)
        print('      now %s' % head_b)

    # Named labels first: those are the ones a person can point at on the diagram.
    named = [(key, n) for key, n in gone.items() if key[0] == 'label' and key[1]]

    for key, n in sorted(named)[:6]:
        print('      gone : %-30s at (%d,%d) %dx%d' % (key[1][:30].encode('ascii','replace').decode('ascii'), key[2], key[3], key[4], key[5]))

    named = [(key, n) for key, n in came.items() if key[0] == 'label' and key[1]]

    for key, n in sorted(named)[:6]:
        print('      new  : %-30s at (%d,%d) %dx%d' % (key[1][:30].encode('ascii','replace').decode('ascii'), key[2], key[3], key[4], key[5]))

print('')
print('%d pages: %d tile placements differ, %d label placements differ'
      % (pages, tiles_moved, labels_moved))
