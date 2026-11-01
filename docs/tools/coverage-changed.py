# Usage: python docs/tools/coverage-changed.py <jacoco report.xml> <base ref> [out.md]
#
# The lines of src/ changed since <base ref> that no test ran, from a JaCoCo XML report of a battery (coverage.sh
# makes both).  A line counts when JaCoCo has bytecode for it: comments, blanks and declarations carry none and are
# not listed.  Written as markdown, file by file with the method each line is in, most uncovered first; the totals
# go to stdout as well.
import collections, re, subprocess, sys
import xml.etree.ElementTree as ET

report, base = sys.argv[1], sys.argv[2]
out = sys.argv[3] if len(sys.argv) > 3 else None

# Lines added or changed since base, per file, as HEAD numbers them
changed = collections.defaultdict(set)
diff = subprocess.run(['git', 'diff', '-U0', '--no-color', base + '..HEAD', '--', 'src'], capture_output=True,
                      text=True, encoding='utf-8', errors='replace').stdout
path = None
for line in diff.splitlines():
    if line.startswith('+++ '):
        path = line[6:] if line.startswith('+++ b/') else None
    elif line.startswith('@@') and path:
        m = re.match(r'@@ -\S+ \+(\d+)(?:,(\d+))? @@', line)
        start, count = int(m.group(1)), int(m.group(2) or 1)
        changed[path].update(range(start, start + count))

root = ET.parse(report).getroot()
rows, ran, missed = [], 0, 0
for package in root.iter('package'):
    for source in package.iter('sourcefile'):
        path = 'src/' + package.get('name') + '/' + source.get('name')
        if path not in changed:
            continue
        # The method each line belongs to: the last method starting at or before it
        starts = sorted((int(m.get('line') or 0), c.get('name').split('/')[-1] + '.' + m.get('name'))
                        for c in package.iter('class') if c.get('sourcefilename') == source.get('name')
                        for m in c.iter('method') if m.get('line'))
        uncovered = []
        for line in source.iter('line'):
            nr, mi, ci = int(line.get('nr')), int(line.get('mi')), int(line.get('ci'))
            if nr in changed[path]:
                if ci > 0:
                    ran += 1
                elif mi > 0:
                    missed += 1
                    owner = ''
                    for s, name in starts:
                        if s <= nr:
                            owner = name
                    uncovered.append((nr, owner))
        if uncovered:
            rows.append((path, uncovered))

total = ran + missed
summary = '%d changed lines with code since %s: %d run by a test, %d by none (%.0f%% covered)' % (
    total, base, ran, missed, 100.0 * ran / total if total else 100)
print(summary)

if out:
    with open(out, 'w', encoding='utf-8') as f:
        f.write('# Changed lines no test runs\n\n' + summary + '.\n\n')
        for path, uncovered in sorted(rows, key=lambda r: -len(r[1])):
            f.write('## %s (%d)\n\n' % (path, len(uncovered)))
            by = collections.OrderedDict()
            for nr, owner in uncovered:
                by.setdefault(owner, []).append(nr)
            for owner, lines in by.items():
                f.write('- `%s`: %s\n' % (owner or '(no method)', ', '.join(str(n) for n in lines)))
            f.write('\n')
    print('written to ' + out)
