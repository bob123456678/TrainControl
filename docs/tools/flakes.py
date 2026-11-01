# Usage: python docs/tools/flakes.py [flakes.tsv]
#
# Adds up battery.sh's rerun log per class: how often it failed and then passed alone (flaky), how often it failed
# both times, when it last did, and its last first failure.  Most often first.
import collections, sys

path = sys.argv[1] if len(sys.argv) > 1 else 'docs/tools/flakes.tsv'
rows = [line.rstrip('\n').split('\t') for line in open(path, encoding='utf-8')][1:]
by = collections.OrderedDict()

for date, commit, cls, outcome, first in (r + [''] * (5 - len(r)) for r in rows if r and r[0]):
    seen = by.setdefault(cls, {'flaky': 0, 'failed-twice': 0, 'last': '', 'said': ''})
    seen[outcome] = seen.get(outcome, 0) + 1
    seen['last'], seen['said'] = date + ' ' + commit, first

for cls, seen in sorted(by.items(), key=lambda kv: -(kv[1]['flaky'] + kv[1]['failed-twice'])):
    print('%-70s flaky %d  failed twice %d  last %s' % (cls, seen['flaky'], seen['failed-twice'], seen['last']))
    print('    ' + seen['said'][:160])

if not by:
    print('no reruns logged in ' + path)
