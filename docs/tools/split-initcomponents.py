# Usage: python docs/tools/split-initcomponents.py <TrainControlUI.java> [max lines per piece]
#
# FOR A COVERAGE COPY ONLY, never the tree: coverage.sh runs it on TrainControlUI.java in a throwaway worktree.
# JaCoCo cannot instrument TrainControlUI as written, because its probes push the GUI Builder's initComponents past the
# JVM's 64 KB limit for one method ("Method too large"), and then the whole class goes unmeasured.  This cuts the body
# into a chain of methods, initComponents calling initComponents2 and so on, each well under the limit.
#
# A cut goes only between two top-level statements, and only where no local variable declared earlier in the method is
# used later (the GUI Builder's GroupLayout locals), so every piece compiles alone.  The cut is written at the end of an
# existing line, so every line keeps its number and the report reads against the real file.
import re, sys

path = sys.argv[1]
limit = int(sys.argv[2]) if len(sys.argv) > 2 else 700
raw = open(path, 'rb').read().decode('utf-8')
nl = '\r\n' if '\r\n' in raw else '\n'
lines = raw.split(nl)

begin = next(i for i, l in enumerate(lines) if '//GEN-BEGIN:initComponents' in l)
end = next(i for i in range(begin, len(lines)) if '//GEN-END:initComponents' in lines[i])
# The body runs from the line after the opening brace to the line before the closing one
open_at = next(i for i in range(begin, end) if lines[i].rstrip().endswith('{'))
body = range(open_at + 1, end)
signature = lines[begin]

STRING = re.compile(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'')
DECL = re.compile(r'^\s*(?:final\s+)?[\w.]+(?:<[^>]*>)?(?:\[\])*\s+(\w+)\s*(?:=|;)')

# Depth before each line, and the locals declared at depth 0 with the last line that names each
depth, depths, declared = 0, {}, {}
for i in body:
    depths[i] = depth
    code = STRING.sub('""', lines[i].split('//')[0])
    if depth == 0:
        m = DECL.match(code)
        if m and m.group(1) not in ('return', 'new'):
            declared[m.group(1)] = i
    depth += code.count('{') + code.count('(') - code.count('}') - code.count(')')
last_use = {}
for name, at in declared.items():
    word = re.compile(r'\b' + re.escape(name) + r'\b')
    last_use[name] = max(i for i in body if word.search(STRING.sub('""', lines[i])))

# A local that is only looked up (the GUI Builder's `bundle`) or declared with no value is declared again at the top of
# each later piece rather than holding a cut back
CARRY = {name for name, at in declared.items()
         if 'ResourceBundle.getBundle(' in lines[at] or re.match(r'^[^=]*;\s*$', lines[at].split('//')[0])}

cuts, since = [], 0
for i in body:
    since += 1
    nxt = i + 1
    boundary = nxt in depths and depths[nxt] == 0 and lines[i].rstrip().endswith((';', '}'))
    live = any(at <= i < last_use[name] for name, at in declared.items() if name not in CARRY)
    if since >= limit and boundary and not live and nxt < end:
        cuts.append(i)
        since = 0

for n, i in enumerate(cuts, 2):
    carried = ' '.join(lines[declared[name]].strip() for name in sorted(CARRY) if declared[name] < i < last_use[name])
    lines[i] = lines[i].rstrip() + '  initComponents%d(); }  private void initComponents%d() { %s' % (n, n, carried)

open(path, 'wb').write(nl.join(lines).encode('utf-8'))
print('initComponents (%d lines) cut into %d pieces at lines %s' % (end - open_at - 1, len(cuts) + 1,
                                                                  ', '.join(str(c + 1) for c in cuts)))
