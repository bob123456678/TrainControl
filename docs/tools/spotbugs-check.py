# Usage: python docs/tools/spotbugs-check.py <spotbugs.xml> [--update]
#
# SpotBugs' findings against docs/tools/spotbugs-baseline.txt: for each key found more often than the baseline holds,
# names every instance of it, and exits 1 when there is one.  A finding is keyed by its type, class, method and field, not by its line, so an edit
# elsewhere in the file does not make an old finding new; lambda and anonymous class numbers are left out for the
# same reason.  Findings fixed since are counted, and --update rewrites the baseline to what was found, banking them.
import collections, os, re, sys
import xml.etree.ElementTree as ET

BASELINE = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'spotbugs-baseline.txt')


def key(bug):
    def name(tag, attr):
        e = bug.find(tag)
        return e.get(attr) if e is not None else ''
    cls = re.sub(r'\$\d+', '$N', name('Class', 'classname'))
    method = re.sub(r'\$\d+$', '', name('Method', 'name'))
    return '\t'.join([bug.get('type'), cls, method, name('Field', 'name')])


def where(bug):
    line = bug.find('SourceLine')
    return 'src/%s:%s' % (line.get('sourcepath'), line.get('start')) if line is not None else ''


found = collections.Counter()
seen = collections.defaultdict(list)
for bug in ET.parse(sys.argv[1]).getroot().iter('BugInstance'):
    k = key(bug)
    found[k] += 1
    seen[k].append((int(bug.get('rank')), bug.get('category'), where(bug), bug.findtext('ShortMessage') or ''))

if '--update' in sys.argv:
    with open(BASELINE, 'w', encoding='utf-8', newline='\n') as f:
        f.write('# SpotBugs findings in src that were there when this was written: type, class, method, field, count.\n'
                '# Rewritten by `spotbugs-check.py <xml> --update`; docs/tools/spotbugs.sh says how a run is made.\n')
        for k in sorted(found):
            f.write('%s\t%d\n' % (k, found[k]))
    print('baseline rewritten: %d findings, %d keys' % (sum(found.values()), len(found)))
    sys.exit(0)

baseline = collections.Counter()
for line in open(BASELINE, encoding='utf-8'):
    if line.startswith('#') or not line.strip():
        continue
    *k, n = line.rstrip('\n').split('\t')
    baseline['\t'.join(k)] = int(n)

new = [(k, found[k] - baseline[k]) for k in found if found[k] > baseline[k]]
gone = sum(max(0, baseline[k] - found[k]) for k in baseline)

print('%d findings, %d in the baseline; %d new, %d fixed since' % (sum(found.values()), sum(baseline.values()),
                                                                   sum(n for _, n in new), gone))
# The baseline holds no lines, so which instance of a key is the new one is unknown: every instance is named
for k, n in sorted(new, key=lambda kn: min(seen[kn[0]])[0]):
    rank, category, _, message = min(seen[k])
    print('  NEW rank %d %s %s: %s (%d now, %d in the baseline)' % (rank, category, k.split('\t')[0], message,
                                                                   found[k], baseline[k]))
    for at in sorted(set(i[2] for i in seen[k])):
        print('      at ' + at)
if gone and not new:
    print('Run again with --update to bank the %d fixed.' % gone)
sys.exit(1 if new else 0)
