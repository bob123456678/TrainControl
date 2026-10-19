#!/usr/bin/env python3
"""
Proves a change touched only comments: the Java token streams are equal, and so is the bytecode (FR-136).

Two checks, between a base revision and a head (a revision, or the working tree):
  1. Tokens.  Every .java file the change touches is read at both ends, its comments dropped, and its tokens
     (identifiers, keywords, literals, operators; whitespace ignored) compared.  The first difference is printed with
     its line at each end.
  2. Bytecode.  Both ends' src/ are compiled into a scratch folder and `javap -c -p` of every class compared, with
     line-number tables left out (`javap -c` does not print them; any that appear are dropped), so moving code up or
     down a file by removing comment lines is no difference.  Identical bytecode is no behaviour change by
     construction.

How the phases use it (docs/refactor-3.0.3.md, section 5):
  - Phase 2 (comment trim): every commit is checked before it is kept, e.g.
        python docs/tools/comment-strip-compare.py --base HEAD~1 --head HEAD --scratch C:/scratch/csc
    then only the bookkeeping classes are run (javadoc, citation, records count, message key); a battery per phase.
  - Any phase, for a commit that claims to be comment-only (a reworded javadoc during an extraction, say).
  A file outside src/ and test/ (behaviour.md, say) is listed as not checked; a .java change under test/ is checked by
  tokens only, as tests are not compiled here.

Exit status: 0 when both checks pass, 1 when either finds a difference, 2 when it could not run (a compile failed).

Examples, from the project root:
  python docs/tools/comment-strip-compare.py --base HEAD~1 --scratch C:/scratch/csc      (HEAD~1 to the working tree)
  python docs/tools/comment-strip-compare.py --base dev --head phase2 --tokens-only
"""
import argparse
import io
import os
import re
import shutil
import subprocess
import sys
import tarfile

CLASSPATH = ['resources/flatlaf-3.7.2.jar', 'resources/json-20260814.jar']
TOKEN = re.compile(r'''
    (?P<comment>//[^\n]*|/\*.*?\*/)
  | (?P<string>"(?:\\.|[^"\\\n])*")
  | (?P<char>'(?:\\.|[^'\\\n])+')
  | (?P<word>[A-Za-z_$][\w$]*|\d[\w.]*)
  | (?P<space>\s+)
  | (?P<other>.)
''', re.S | re.X)
BATCH = 200


def git(*args, binary=False):
    out = subprocess.run(['git'] + list(args), stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if out.returncode != 0:
        sys.stderr.write(out.stderr.decode('utf-8', 'replace'))
        sys.exit(2)
    return out.stdout if binary else out.stdout.decode('utf-8', 'replace')


def tokens(text):
    """(token, line) pairs of Java source, comments and whitespace dropped."""
    out = []
    line = 1
    for m in TOKEN.finditer(text):
        kind = m.lastgroup
        if kind not in ('comment', 'space'):
            out.append((m.group(), line))
        line += m.group().count('\n')
    return out


def text_at(revision, path):
    if revision is None:
        with open(path, encoding='utf-8', errors='replace') as f:
            return f.read()
    listed = git('ls-tree', '--name-only', revision, '--', path).strip()
    return git('show', '%s:%s' % (revision, path)) if listed else None


def compare_tokens(base, head, paths):
    differ = 0
    for path in paths:
        a = text_at(base, path)
        b = text_at(head, path) if head is not None or os.path.exists(path) else None
        if a is None or b is None:
            print('TOKENS  %s: %s at one end only - not a comment change' % (path, 'added' if a is None else 'deleted'))
            differ += 1
            continue
        ta, tb = tokens(a), tokens(b)
        if [t for t, _ in ta] == [t for t, _ in tb]:
            print('tokens  %s: equal (%d tokens)' % (path, len(ta)))
            continue
        differ += 1
        at = next((i for i, (x, y) in enumerate(zip(ta, tb)) if x[0] != y[0]), min(len(ta), len(tb)))
        where_a = ta[at][1] if at < len(ta) else 'end'
        where_b = tb[at][1] if at < len(tb) else 'end'
        print('TOKENS  %s: differ at base line %s / head line %s' % (path, where_a, where_b))
        print('        base: %s' % ' '.join(t for t, _ in ta[max(0, at - 5):at + 6]))
        print('        head: %s' % ' '.join(t for t, _ in tb[max(0, at - 5):at + 6]))
    return differ


def jdk_tool(name):
    home = os.environ.get('TC_JDK', 'C:/Program Files/Java/jdk1.8.0_361')
    path = os.path.join(home, 'bin', name + ('.exe' if os.name == 'nt' else ''))
    if not os.path.isfile(path):
        sys.exit('no %s at %s - set TC_JDK to a JDK 8 folder' % (name, path))
    return path


def export_src(revision, into):
    """src/ at a revision (or the working tree when None), copied into a fresh folder."""
    if os.path.isdir(into):
        shutil.rmtree(into)
    os.makedirs(into)
    if revision is None:
        shutil.copytree('src', os.path.join(into, 'src'))
    else:
        archive = git('archive', '--format=tar', revision, 'src', binary=True)
        with tarfile.open(fileobj=io.BytesIO(archive)) as tar:
            tar.extractall(into)
    return os.path.join(into, 'src')


def compile_tree(src, out):
    os.makedirs(out, exist_ok=True)
    listing = out + '-files.txt'
    with open(listing, 'w', encoding='utf-8') as f:
        for top, dirs, files in os.walk(src):
            for name in files:
                if name.endswith('.java'):
                    f.write('"%s"\n' % os.path.abspath(os.path.join(top, name)).replace('\\', '/'))
    cp = os.pathsep.join([src] + [os.path.abspath(j) for j in CLASSPATH])
    status = subprocess.call([jdk_tool('javac'), '-nowarn', '-encoding', 'UTF-8', '-proc:none', '-d', out, '-cp', cp,
                              '@' + listing])
    if status != 0:
        print('could not compile %s' % src)
        sys.exit(2)


def disassembled(folder):
    """class name -> javap -c -p text without line-number tables."""
    names = []
    for top, dirs, files in os.walk(folder):
        for name in files:
            if name.endswith('.class'):
                names.append(os.path.relpath(os.path.join(top, name), folder)[:-6].replace(os.sep, '.'))
    names.sort()
    out = {}
    for at in range(0, len(names), BATCH):
        text = subprocess.run([jdk_tool('javap'), '-J-Dfile.encoding=UTF-8', '-c', '-p', '-cp', folder] + names[at:at + BATCH],
                              stdout=subprocess.PIPE, stderr=subprocess.PIPE, encoding='utf-8',
                              errors='replace').stdout
        current = None
        for line in text.splitlines():
            if line.startswith('Compiled from'):
                current = None
                continue
            if current is None and line.rstrip().endswith('{') and not line.startswith(' '):
                current = line.rstrip()
                out[current] = []
                continue
            if current is None or 'LineNumberTable' in line or re.match(r'\s+line \d+: \d+$', line):
                continue
            out[current].append(line)
    return out


def compare_bytecode(base, head, scratch):
    trees = {}
    for end, revision in (('base', base), ('head', head)):
        src = export_src(revision, os.path.join(scratch, end))
        classes = os.path.join(scratch, end + '-classes')
        if os.path.isdir(classes):
            shutil.rmtree(classes)
        print('compiling %s (%s) ...' % (end, revision or 'working tree'))
        compile_tree(src, classes)
        trees[end] = disassembled(classes)
    a, b = trees['base'], trees['head']
    differ = 0
    for header in sorted(set(a) | set(b)):
        if a.get(header) != b.get(header):
            differ += 1
            if header not in a or header not in b:
                print('BYTECODE %s: only at the %s' % (header, 'head' if header not in a else 'base'))
                continue
            first = next((i for i, (x, y) in enumerate(zip(a[header], b[header])) if x != y),
                         min(len(a[header]), len(b[header])))
            print('BYTECODE %s: differs at disassembly line %d' % (header, first + 1))
            print('        base: %s' % (a[header][first].strip() if first < len(a[header]) else '(end)'))
            print('        head: %s' % (b[header][first].strip() if first < len(b[header]) else '(end)'))
    print('bytecode: %d classes compared, %d differ' % (len(set(a) | set(b)), differ))
    return differ


def main():
    parser = argparse.ArgumentParser(description=__doc__.split('\n\n')[0].strip(), epilog=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--base', required=True, help='the revision before the change (e.g. HEAD~1)')
    parser.add_argument('--head', help='the revision after it (default: the working tree)')
    parser.add_argument('--scratch', help='a folder outside the project for the two compiled trees '
                        '(required unless --tokens-only)')
    parser.add_argument('--tokens-only', action='store_true', help='skip the bytecode check')
    args = parser.parse_args()

    if not os.path.isdir('src') or not os.path.isdir('.git') and not os.path.isfile('.git'):
        sys.exit('run this from the project root')
    if not args.tokens_only and not args.scratch:
        sys.exit('--scratch is needed for the bytecode check (or pass --tokens-only)')

    names = git('diff', '--name-only', args.base, *( [args.head] if args.head else [] )).split()
    java = [n for n in names if n.endswith('.java')]
    for n in names:
        if not n.endswith('.java'):
            print('not checked (not Java): %s' % n)
    differ = compare_tokens(args.base, args.head, java)
    print('tokens: %d Java files compared, %d differ' % (len(java), differ))
    if not args.tokens_only and any(n.startswith('src/') for n in java):
        scratch = os.path.abspath(args.scratch)
        if scratch.startswith(os.path.abspath('.') + os.sep):
            sys.exit('--scratch must be outside the project')
        differ += compare_bytecode(args.base, args.head, scratch)
    elif not args.tokens_only:
        print('bytecode: no file under src/ changed, nothing to compile')
    print('COMMENT-ONLY' if differ == 0 else 'NOT COMMENT-ONLY: %d difference(s)' % differ)
    sys.exit(1 if differ else 0)


if __name__ == '__main__':
    main()
