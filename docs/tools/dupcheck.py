"""Merge check: which copy groups a branch's diff touches, and whether it touched every copy.

Usage: py -3 docs/tools/dupcheck.py <worktree> <base> [--scratch DIR]
Runs dupscan.py over <worktree>'s src/ and reads `git diff <base>..HEAD` there.  For each cluster with a copy on a
changed line it prints how many of its copies the diff touched:
  PARTIAL  some copies changed and not the others: the rule may now say two things
  NEW      every copy sits on added lines: the branch wrote a new copy
  ALL      every copy changed together
Exit code 1 when anything is PARTIAL or NEW.
"""
import json, os, re, subprocess, sys, tempfile

wt, base = sys.argv[1], sys.argv[2]
scratch = sys.argv[sys.argv.index('--scratch') + 1] if '--scratch' in sys.argv else tempfile.gettempdir()
here = os.path.dirname(os.path.abspath(__file__))
out = os.path.join(scratch, 'dupcheck-clusters.json')

subprocess.run([sys.executable, '-I', os.path.join(here, 'dupscan.py'), wt, out, '--no-old'], check=True,
               stdout=subprocess.DEVNULL, stdin=subprocess.DEVNULL)
clusters = json.load(open(out))

diff = subprocess.run(['git', '-C', wt, 'diff', '-U0', base + '..HEAD', '--', 'src'], capture_output=True,
                      text=True, encoding='utf-8', errors='replace', stdin=subprocess.DEVNULL).stdout
changed, added, path = {}, {}, None
for line in diff.splitlines():
    if line.startswith('+++ '):
        path = line[6:] if line.startswith('+++ b/') else None
    m = re.match(r'@@ -\d+(?:,(\d+))? \+(\d+)(?:,(\d+))? @@', line)
    if m and path:
        old_n = int(m.group(1)) if m.group(1) is not None else 1
        start, n = int(m.group(2)), int(m.group(3)) if m.group(3) is not None else 1
        lines = set(range(start, start + max(n, 1)))
        changed.setdefault(path, set()).update(lines)
        if old_n == 0:
            added.setdefault(path, set()).update(range(start, start + n))

flagged = 0
for i, c in enumerate(clusters, 1):
    members = c['members']
    touched, fresh = 0, 0
    for mbr in members:
        span = set(range(mbr['lines'][0], mbr['lines'][1] + 1))
        if span & changed.get(mbr['file'], set()):
            touched += 1
        if span <= added.get(mbr['file'], set()):
            fresh += 1
    if not touched:
        continue
    kind = 'NEW' if fresh == len(members) else 'ALL' if touched == len(members) else 'PARTIAL'
    flagged += kind != 'ALL'
    print(f"{kind:7s} {touched}/{len(members)} copies, {c['tokens']} tokens, same={c['identical_tokens']:.2f}: " +
          '; '.join(f"{m['file'].split('/')[-1]}:{m['lines'][0]}-{m['lines'][1]}" for m in members[:6]))

print(f"{flagged} cluster(s) to look at" if flagged else "no copy group was changed in part or written new")
sys.exit(1 if flagged else 0)
