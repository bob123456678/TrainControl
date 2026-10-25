"""Clone scan over src/: runs of code that are identical or the same up to names and literals.

Usage: py -3 dupscan.py <repo> <out.json> [--min 60]
Comments and NetBeans GEN regions are skipped.  Each cluster says how alike its copies are (raw-token
identity) and how much of each copy's lines already stood in v2_7_0.
"""
import json, os, re, subprocess, sys
from collections import defaultdict

repo, out = sys.argv[1], sys.argv[2]
MIN = int(sys.argv[sys.argv.index('--min') + 1]) if '--min' in sys.argv else 60

KEYWORDS = set("""abstract assert boolean break byte case catch char class const continue default do double else enum
extends final finally float for goto if implements import instanceof int interface long native new package private
protected public return short static strictfp super switch synchronized this throw throws transient try void volatile
while true false null""".split())
TOK = re.compile(r'''//[^\n]*|/\*.*?\*/|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|[A-Za-z_$][\w$]*|\d[\w.]*|\S''', re.S)


def tokens(path):
    text = open(path, encoding='utf-8', errors='replace').read()
    out, skip = [], False
    for m in TOK.finditer(text):
        t = m.group(0)
        line = text.count('\n', 0, m.start()) + 1
        if t.startswith('//'):
            if 'GEN-BEGIN' in t: skip = True
            elif 'GEN-END' in t: skip = False
            continue
        if t.startswith('/*') or skip:
            continue
        if t in ('import', 'package'):
            pass
        if t[0] in '"\'':
            n = 'L'
        elif t[0].isdigit():
            n = 'L'
        elif re.match(r'[A-Za-z_$]', t):
            n = t if t in KEYWORDS else 'I'
        else:
            n = t
        out.append((n, t, line))
    # drop import/package statements
    res, i = [], 0
    while i < len(out):
        if out[i][1] in ('import', 'package') and (i == 0 or out[i - 1][1] in (';', '}')):
            while i < len(out) and out[i][1] != ';': i += 1
            i += 1; continue
        res.append(out[i]); i += 1
    return res


files = []
for root, _, names in os.walk(os.path.join(repo, 'src')):
    for n in names:
        if n.endswith('.java'):
            files.append(os.path.join(root, n))
files.sort()
toks = {f: tokens(f) for f in files}

buckets = defaultdict(list)
for f, ts in toks.items():
    norm = [t[0] for t in ts]
    h = None
    for i in range(0, len(ts) - MIN + 1):
        buckets[hash(tuple(norm[i:i + MIN]))].append((f, i))

pairs = []
seen = set()
for key, locs in buckets.items():
    if len(locs) < 2 or len(locs) > 40:
        continue
    for a in range(len(locs)):
        for b in range(a + 1, len(locs)):
            (fa, ia), (fb, ib) = locs[a], locs[b]
            na, nb = toks[fa], toks[fb]
            # only maximal starts
            if ia > 0 and ib > 0 and na[ia - 1][0] == nb[ib - 1][0]:
                continue
            if fa == fb and abs(ia - ib) < MIN:
                continue
            k = 0
            while ia + k < len(na) and ib + k < len(nb) and na[ia + k][0] == nb[ib + k][0]:
                if fa == fb and ia + k >= ib: break
                k += 1
            if k < MIN or (fa, ia, fb, ib) in seen:
                continue
            seen.add((fa, ia, fb, ib))
            same = sum(1 for j in range(k) if na[ia + j][1] == nb[ib + j][1]) / k
            pairs.append((fa, ia, fb, ib, k, same))

# cluster fragments that overlap
frag_id = {}
parent = []


def find(x):
    while parent[x] != x:
        parent[x] = parent[parent[x]]; x = parent[x]
    return x


frags = []


def frag(f, i, k):
    for idx, (ff, fi, fk) in enumerate(frags):
        if ff == f and fi < i + k and i < fi + fk and min(fi + fk, i + k) - max(fi, i) >= 0.7 * min(fk, k):
            if i + k > fi + fk or i < fi:
                frags[idx] = (f, min(fi, i), max(fi + fk, i + k) - min(fi, i))
            return idx
    frags.append((f, i, k)); parent.append(len(parent)); return len(frags) - 1


edges = []
for fa, ia, fb, ib, k, same in pairs:
    x, y = frag(fa, ia, k), frag(fb, ib, k)
    parent[find(x)] = find(y)
    edges.append((x, y, k, same))

old_lines = set()
try:
    listing = subprocess.run(['git', '-C', repo, 'ls-tree', '-r', '--name-only', 'v2_7_0', 'src'],
                             capture_output=True, text=True, stdin=subprocess.DEVNULL).stdout.split()
    for p in listing:
        if p.endswith('.java'):
            body = subprocess.run(['git', '-C', repo, 'show', 'v2_7_0:' + p], capture_output=True,
                                  text=True, encoding='utf-8', errors='replace', stdin=subprocess.DEVNULL).stdout
            old_lines.update(l.strip() for l in body.splitlines() if len(l.strip()) > 3)
except Exception as e:
    print('v2_7_0 lookup failed:', e)

clusters = defaultdict(list)
for idx in range(len(frags)):
    clusters[find(idx)].append(idx)

report = []
for root, members in clusters.items():
    if len(members) < 2:
        continue
    copies = []
    for idx in members:
        f, i, k = frags[idx]
        ts = toks[f]
        l0, l1 = ts[i][2], ts[min(i + k, len(ts)) - 1][2]
        src = open(f, encoding='utf-8', errors='replace').read().splitlines()[l0 - 1:l1]
        code = [l.strip() for l in src if len(l.strip()) > 3]
        old = sum(1 for l in code if l in old_lines) / max(1, len(code))
        copies.append({'file': os.path.relpath(f, repo).replace('\\', '/'), 'lines': [l0, l1], 'tokens': k,
                       'pre_2_7_0': round(old, 2)})
    sims = [s for x, y, k, s in edges if find(x) == root]
    tok = max(c['tokens'] for c in copies)
    report.append({'tokens': tok, 'copies': len(copies), 'score': tok * (len(copies) - 1),
                   'identical_tokens': round(max(sims), 2), 'members': sorted(copies, key=lambda c: c['file'])})

report.sort(key=lambda r: -r['score'])
json.dump(report, open(out, 'w'), indent=1)
total = sum(r['score'] for r in report)
print(f'{len(files)} files, {sum(len(t) for t in toks.values())} tokens, {len(report)} clusters, '
      f'{total} duplicated tokens')
for r in report[:60]:
    m = r['members']
    print(f"{r['score']:6d} tok={r['tokens']:4d} x{r['copies']} same={r['identical_tokens']:.2f}  " +
          '; '.join(f"{c['file'].split('/')[-1]}:{c['lines'][0]}-{c['lines'][1]}(old {c['pre_2_7_0']:.0%})" for c in m[:4]))
