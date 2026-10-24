#!/usr/bin/env python3
"""Helper for comment-only trims of one Java file to CLAUDE.md's "Comments stay lean".

Usage, from anywhere in the repository (FILE is any .java path, relative to the root or absolute):
  python docs/tools/lean.py stats FILE           lines, comment lines, review ids, long blocks not Adam's
  python docs/tools/lean.py blocks FILE [N]      comment blocks with N+ (default 10) lines not Adam-owned, review ids
  python docs/tools/lean.py show FILE A B        lines A..B tagged: A = Adam-owned, c = comment only, t = comment + code
  python docs/tools/lean.py digest FILE A B      the comment blocks in A..B with the code line after each
  python docs/tools/lean.py apply FILE SPEC      apply replacement hunks (bottom-up); refuses code and Adam-owned lines
  python docs/tools/lean.py ids FILE [REV]       OB/MT/FR ids gone since REV (default HEAD) and where else they appear
  python docs/tools/lean.py adamcount FILE       Adam-owned lines and comment lines

SPEC format:
  @@ A B          replace lines A..B (1-based, inclusive, as in the CURRENT file) with the lines that follow
  ...new lines... (may be none = delete); comment lines over 120 characters are re-wrapped

A line is Adam-owned when its last commit (git blame) has no "Co-Authored-By: Claude" trailer or predates 2026-05-01.
The owner of each commit is cached in --scratch DIR (or $LEAN_SCRATCH; default: the system temp folder).
The file must be CRLF; it is written back CRLF.
"""
import os, re, subprocess, sys, json, time, tempfile


def _root():
    here = os.path.dirname(os.path.abspath(__file__))
    out = subprocess.run(['git', '-C', here, 'rev-parse', '--show-toplevel'], stdout=subprocess.PIPE)
    if out.returncode == 0 and out.stdout.strip():
        return os.path.normpath(out.stdout.decode().strip())
    return os.path.normpath(os.path.join(here, '..', '..'))


ROOT = _root()
REL = None
PATH = None
CACHE = None


def _setup(path, scratch):
    global REL, PATH, CACHE
    full = os.path.abspath(path if os.path.isabs(path) else os.path.join(ROOT, path))
    if not os.path.isfile(full):
        full = os.path.abspath(path)
    if not full.endswith('.java') or not os.path.isfile(full):
        sys.exit('not a .java file: %s' % path)
    PATH = full
    REL = os.path.relpath(full, ROOT).replace(os.sep, '/')
    scratch = scratch or os.environ.get('LEAN_SCRATCH') or tempfile.gettempdir()
    os.makedirs(scratch, exist_ok=True)
    CACHE = os.path.join(scratch, 'lean-commit-owner.json')


TOKEN = re.compile(r'''
    (?P<comment>//[^\n]*|/\*.*?\*/)
  | (?P<string>"(?:\\.|[^"\\\n])*")
  | (?P<char>'(?:\\.|[^'\\\n])+')
  | (?P<word>[A-Za-z_$][\w$]*|\d[\w.]*)
  | (?P<space>\s+)
  | (?P<other>.)
''', re.S | re.X)
CITATION = re.compile(r'\b([A-Z][A-Z0-9]*)-[A-Z]?\d+')
TRACKER_PREFIXES = {'OB', 'MT', 'FR'}
TRACKER = re.compile(r'\b((?:OB|MT|FR)-\d+)\b')


def review_prefixes():
    """Prefixes of review ids: the first-column refs of docs/manual-tests/findings.tsv, before the dash."""
    out = set()
    with open(os.path.join(ROOT, 'docs', 'manual-tests', 'findings.tsv'), encoding='utf-8') as f:
        for l in f:
            ref = l.split('\t')[0]
            if not l.startswith('#') and '-' in ref:
                out.add(ref.split('-')[0])
    return out - TRACKER_PREFIXES


def read():
    with open(PATH, 'rb') as f:
        data = f.read().decode("utf-8")
    assert '\r\n' in data
    return data


def classify(text):
    """per line (0-based): (has_comment, has_code)."""
    n = text.count('\n') + 1
    com = [False] * n
    code = [False] * n
    line = 0
    for m in TOKEN.finditer(text):
        kind = m.lastgroup
        tok = m.group()
        nl = tok.count('\n')
        if kind == 'comment':
            for i in range(line, line + nl + 1):
                com[i] = True
        elif kind != 'space':
            for i in range(line, line + nl + 1):
                code[i] = True
        line += nl
    return com, code


def comment_texts(text):
    out = []
    line = 0
    for m in TOKEN.finditer(text):
        tok = m.group()
        if m.lastgroup == 'comment':
            for k, part in enumerate(tok.split('\n')):
                out.append((line + k, part))
        line += tok.count('\n')
    return out


def review_ids(text):
    """(line, id) of every review id in a comment of text."""
    pre = review_prefixes()
    out = []
    for ln, part in comment_texts(text):
        for m in CITATION.finditer(part):
            if m.group(1) in pre:
                out.append((ln, m.group(0)))
    return out


def long_blocks(text, adam, n):
    """(first, last), 0-based, of each run of comment-only lines holding at least n lines that are not Adam-owned."""
    com, code = classify(text)
    total = len(adam)
    out = []
    i = 0
    while i < total:
        if com[i] and not code[i]:
            j = i
            while j < total and com[j] and not code[j]:
                j += 1
            if (j - i) - sum(adam[i:j]) >= n:
                out.append((i, j - 1))
            i = j
        else:
            i += 1
    return out


def stats():
    text = read().replace('\r\n', '\n')
    com, code = classify(text)
    lines = text.split('\n')
    total = len(lines) - (1 if text.endswith('\n') else 0)
    any_c = sum(1 for i in range(total) if com[i])
    pure = sum(1 for i in range(total) if com[i] and not code[i])
    ids = review_ids(text)
    trk = sum(len(TRACKER.findall(part)) for _, part in comment_texts(text))
    nblk = len(long_blocks(text, owners(), 10))
    print("%s: lines %d, comment lines %d (pure %d), review ids %d, tracker ids %d, blocks of 10+ not Adam's %d"
          % (REL, total, any_c, pure, len(ids), trk, nblk))


def blocks(n):
    text = read().replace('\r\n', '\n')
    lines = text.split('\n')
    adam = owners()
    for a, b in long_blocks(text, adam, n):
        print('%5d-%-5d %3d lines (%d Adam) %s' % (a + 1, b + 1, b - a + 1, sum(adam[a:b + 1]), lines[a].strip()[:70]))
    for ln, rid in review_ids(text):
        print('review id %s at line %d' % (rid, ln + 1))


def owners():
    try:
        with open(CACHE) as f:
            cache = json.load(f)
    except Exception:
        cache = {}
    out = subprocess.run(['git', '-C', ROOT, 'blame', '--line-porcelain', '--', REL], stdout=subprocess.PIPE).stdout.decode('utf-8', 'replace')
    shas = []
    times = {}
    cur = None
    for l in out.split('\n'):
        m = re.match(r'^([0-9a-f]{40}) \d+ (\d+)', l)
        if m:
            cur = m.group(1)
            shas.append(cur)
        elif l.startswith('author-time ') and cur:
            times[cur] = int(l.split()[1])
    adam = []
    cutoff = time.mktime((2026, 5, 1, 0, 0, 0, 0, 0, -1))
    for s in shas:
        if s == '0' * 40:
            adam.append(False)
            continue
        if s not in cache:
            msg = subprocess.run(['git', '-C', ROOT, 'log', '-1', '--format=%B', s], stdout=subprocess.PIPE).stdout.decode('utf-8', 'replace')
            claude = re.search(r'co-authored-by:\s*claude', msg, re.I) is not None
            cache[s] = (not claude) or times.get(s, 0) < cutoff
        adam.append(cache[s])
    with open(CACHE, 'w') as f:
        json.dump(cache, f)
    return adam


def show(a, b):
    text = read().replace('\r\n', '\n')
    lines = text.split('\n')
    com, code = classify(text)
    adam = owners()
    rows = []
    for i in range(a - 1, min(b, len(lines))):
        tag = ('A' if i < len(adam) and adam[i] else ' ') + ('c' if com[i] and not code[i] else ('t' if com[i] else ' '))
        rows.append((i, tag))
    k = 0
    while k < len(rows):
        # collapse runs of Adam-owned code and Adam-owned comment lines
        j = k
        while j < len(rows) and rows[j][1] in ('A ', 'Ac', 'At'):
            j += 1
        if j - k > 6:
            for r in rows[k:k + 2] + [None] + rows[j - 2:j]:
                if r is None:
                    print('      ...  (%d Adam-owned lines)' % (j - k - 4))
                else:
                    print('%5d %s|%s' % (r[0] + 1, r[1], lines[r[0]]))
            k = j
            continue
        i, tag = rows[k]
        print('%5d %s|%s' % (i + 1, tag, lines[i]))
        k += 1


def apply(spec):
    with open(spec, encoding='utf-8') as f:
        raw = f.read().replace('\r\n', '\n').split('\n')
    hunks = []
    for l in raw:
        m = re.match(r'^@@ (\d+) (\d+)\s*$', l)
        if m:
            hunks.append([int(m.group(1)), int(m.group(2)), []])
        elif hunks:
            hunks[-1][2].append(l)
        elif l.strip():
            sys.exit('text before first hunk: %r' % l)
    def wrap(l):
        m = re.match(r'^(\s*(?://|\*) )', l)
        if len(l) <= 120 or not m:
            return [l]
        prefix = m.group(1)
        cut = l.rfind(' ', len(prefix) + 1, 121)
        while cut > 0 and l[cut - 1] == ' ':
            cut -= 1
        return [l[:cut]] + wrap(prefix + l[cut:].lstrip())
    def expand(lines_):
        out = []
        for l in lines_:
            m = re.match(r'^(\s*)/\*\* (.*) \*/$', l)
            if m and len(l) > 120:
                out += [m.group(1) + '/**', m.group(1) + ' * ' + m.group(2), m.group(1) + ' */']
            else:
                out.append(l)
        return out
    def reflow(lines_):
        lines_ = expand(lines_)
        out = []
        k = 0
        while k < len(lines_):
            m = re.match(r'^(\s*(?://|\*) )(\S.*)$', lines_[k])
            if not m or m.group(2).startswith('@'):
                out.extend(wrap(lines_[k]))
                k += 1
                continue
            prefix = m.group(1)
            group = [m.group(2)]
            j = k + 1
            while j < len(lines_):
                m2 = re.match(r'^(\s*(?://|\*) )(\S.*)$', lines_[j])
                if not m2 or m2.group(1) != prefix or m2.group(2).startswith('@'):
                    break
                group.append(m2.group(2))
                j += 1
            if any(len(prefix) + len(g) > 120 for g in group):
                text = ''
                for g in group:
                    text = g if not text else text + ('  ' if text.endswith('.') else ' ') + g
                out.extend(wrap(prefix + text))
            else:
                out.extend(prefix + g for g in group)
            k = j
        return out
    for h in hunks:
        while h[2] and h[2][-1] == '':
            h[2].pop()
        h[2] = reflow(h[2])
    text = read().replace('\r\n', '\n')
    lines = text.split('\n')
    com, code = classify(text)
    adam = owners()
    hunks.sort(key=lambda h: h[0])
    prev = 0
    bad = []
    for a, b, new in hunks:
        if a <= prev or b < a:
            bad.append('overlap/order at %d-%d' % (a, b))
        prev = b
        for i in range(a - 1, b):
            if code[i]:
                bad.append('line %d has code: %s' % (i + 1, lines[i].strip()[:80]))
            if adam[i] and lines[i].strip():
                bad.append('line %d is Adam-owned: %s' % (i + 1, lines[i].strip()[:80]))
        for l in new:
            s = l.strip()
            if s and not (s.startswith('//') or s.startswith('/*') or s.startswith('*')):
                bad.append('new line not a comment: %r' % l)
            if '\t' in l or l != l.rstrip():
                bad.append('new line has tab/trailing space: %r' % l)
            if len(l) > 120:
                bad.append('new line over 120 chars (%d): %r' % (len(l), l[:60]))
    if bad:
        print('\n'.join(bad))
        sys.exit(1)
    removed = 0
    covered = set()
    for a, b, new in hunks:
        covered.update(range(a, b + 1))
    for h in hunks:
        a, b, new = h
        if not new and a >= 2 and not lines[a - 2].strip() and b < len(lines) and not lines[b].strip()                 and not adam[b] and (b + 1) not in covered:
            h[1] = b + 1
            covered.add(b + 1)
    for a, b, new in sorted(hunks, key=lambda h: -h[0]):
        removed += (b - a + 1) - len(new)
        lines[a - 1:b] = new
    out = '\n'.join(lines).replace('\n', '\r\n')
    with open(PATH + '.tmp', 'wb') as f:
        f.write(out.encode("utf-8"))
    os.replace(PATH + '.tmp', PATH)
    print('applied %d hunks, net %d lines removed' % (len(hunks), removed))
    stats()


def ids(rev):
    head = subprocess.run(['git', '-C', ROOT, 'show', rev + ':' + REL], stdout=subprocess.PIPE).stdout.decode('utf-8', 'replace')
    before = set(TRACKER.findall(head))
    after = set(TRACKER.findall(read()))
    gone = sorted(before - after)
    for i in gone:
        hits = subprocess.run(['git', '-C', ROOT, 'grep', '-l', '-w', i, '--', 'src', 'test'], stdout=subprocess.PIPE).stdout.decode().split()
        hits = [h for h in hits if h != REL]
        print('%s gone from ' + os.path.basename(REL) + '; elsewhere in src/test: %s' % (i, ', '.join(hits) if hits else 'NOWHERE'))
    print('%d tracker ids gone, %d remain' % (len(gone), len(after)))



def digest(a, b):
    """comment blocks in a..b with the next code line; Adam-owned runs collapsed."""
    text = read().replace('\r\n', '\n')
    lines = text.split('\n')
    com, code = classify(text)
    adam = owners()
    i = a - 1
    end = min(b, len(lines))
    while i < end:
        if com[i] and not code[i]:
            j = i
            while j < end and com[j] and not code[j]:
                j += 1
            blk = list(range(i, j))
            ad = [k for k in blk if adam[k]]
            if len([k for k in blk if not adam[k]]) < int(os.environ.get('LEAN_MIN', '0')) and not any(re.search(r'20\d\d-\d\d-\d\d|used to|[Rr]ound \d|\b[A-Z]{2,5}\d?-[A-Z]?\d+\b', lines[k]) and not re.search(r'\b(OB|MT|FR)-\d+', lines[k]) for k in blk):
                i = j
                continue
            if len(ad) == len(blk) and len(blk) > 2:
                print('%5d-%d  [ADAM block, %d lines] %s' % (i + 1, j, len(blk), lines[i].strip()[:60]))
            else:
                for k in blk:
                    print('%5d %s|%s' % (k + 1, 'A' if adam[k] else ' ', lines[k]))
            k = j
            while k < len(lines) and not lines[k].strip():
                k += 1
            if k < len(lines):
                print('%5d >|%s' % (k + 1, lines[k].strip()[:110]))
            i = j
        elif com[i] and code[i]:
            print('%5d %s~|%s' % (i + 1, 'A' if adam[i] else ' ', lines[i].strip()[:150]))
            i += 1
        else:
            i += 1


if __name__ == '__main__':
    args = sys.argv[1:]
    scratch = None
    if '--scratch' in args:
        k = args.index('--scratch')
        scratch = args[k + 1]
        del args[k:k + 2]
    if len(args) < 2:
        sys.exit(__doc__)
    cmd, rest = args[0], args[2:]
    _setup(args[1], scratch)
    if cmd == 'stats':
        stats()
    elif cmd == 'blocks':
        blocks(int(rest[0]) if rest else 10)
    elif cmd == 'digest':
        digest(int(rest[0]), int(rest[1]))
    elif cmd == 'show':
        show(int(rest[0]), int(rest[1]))
    elif cmd == 'apply':
        apply(rest[0])
    elif cmd == 'ids':
        ids(rest[0] if rest else 'HEAD')
    elif cmd == 'adamcount':
        text = read().replace('\r\n', '\n')
        com, code = classify(text)
        a = owners()
        print('Adam-owned lines %d, of them comment lines %d' % (sum(a), sum(1 for i, x in enumerate(a) if x and com[i])))
    else:
        sys.exit(__doc__)
