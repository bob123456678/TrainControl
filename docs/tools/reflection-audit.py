#!/usr/bin/env python3
"""
Which tests name a class's members as text: the list to check before code moves out of that class (FR-136).

A test that reaches a member by name - getDeclaredMethod("x"), a helper taking "x", a source scan for "void x(" -
breaks or, worse, goes quietly vacuous when the member moves to another class or file.  For each member declared in
the given source files this lists:

  1. every string literal in test/ equal to the member's name;
  2. every getDeclared*/getMethod/getField/getDeclaredConstructor call naming it, and every call naming it through a
     test helper that takes a name and hands it to one of those (a "name-taking helper");
  3. every quoted signature fragment ("name(" inside a string) in a test that reads src/ as text;
  4. every catch of NoSuchFieldException/NoSuchMethodException within a few lines after the name is used - the sites
     that may swallow a moved member and pass.

How the phases use it (docs/refactor-3.0.3.md, sections 2 and 5):
  - Phase 0: run on TrainControlUI (and the other giants) for the inventory of what the tests reach by name.
  - Phases 3-5, before an extraction: `--save before.json` on the class being split.
  - After it, on the same tree: `--compare before.json`.  It prints every member whose declaring file changed and
    the test lines that name it; each of those tests is edited in the same commit, or the step is not done.
  - Phase 1 (dead code): a candidate whose name appears here is kept until that test has been read.

The member list is a regex reading of the declarations (methods, fields with a modifier, and nested types), not a
compiler's: overloads share a name, and a local variable written with a modifier may be counted as a field.  Good
enough to find the tests; read each one before acting on it.

Examples, from the project root:
  python docs/tools/reflection-audit.py src/org/traincontrol/gui/TrainControlUI.java --summary
  python docs/tools/reflection-audit.py gui/TrainControlUI.java --save tcui-before.json
  python docs/tools/reflection-audit.py gui/TrainControlUI.java --compare tcui-before.json
"""
import argparse
import collections
import json
import os
import re
import sys

MODIFIERS = r'(?:public|private|protected|static|final|synchronized|abstract|native|transient|volatile|strictfp|default)'
METHOD = re.compile(r'^([ \t]+)(?:@\w+(?:\([^)]*\))?\s+)*((?:' + MODIFIERS + r'\s+)*)(?:<[^>]+>\s+)?'
                    r'[\w.$<>\[\],? ]+?\s+(\w+)\s*\(', re.M)
CONSTRUCTOR_OR_CALL = re.compile(r'^\s+(?:return|new|if|else|throw|for|while|switch|catch|case|try|do|synchronized)\b')
FIELD = re.compile(r'^([ \t]+)((?:' + MODIFIERS + r'\s+)+)[\w.$<>\[\],? ]+?\s+(\w+)\s*(?:=|;)', re.M)
TYPE = re.compile(r'^([ \t]+)(?:' + MODIFIERS + r'\s+)*(?:class|interface|enum)\s+(\w+)', re.M)
STRING = re.compile(r'"((?:\\.|[^"\\\n])*)"')
REFLECT = re.compile(r'\.(getDeclaredMethod|getDeclaredField|getMethod|getField|getDeclaredConstructor)\s*\(\s*"(\w+)"')
REFLECT_BY_PARAM = r'\.(?:getDeclaredMethod|getDeclaredField|getMethod|getField)\s*\(\s*{}\b'
HELPER = re.compile(r'^[ \t]+(?:' + MODIFIERS + r'\s+)*(?:<[^>]+>\s+)?[\w.$<>\[\],? ]+?\s+(\w+)\s*\(([^)]*)\)', re.M)
CATCH = re.compile(r'catch\s*\(\s*(?:final\s+)?(?:[\w.]+\s*\|\s*)*(?:java\.lang\.)?(?:ReflectiveOperationException|'
                   r'NoSuch(?:Field|Method)Exception)')
NEAR = 15


def read(path):
    with open(path, encoding='utf-8', errors='replace') as f:
        return f.read().replace('\r\n', '\n')


def java_files(folder):
    for top, dirs, files in os.walk(folder):
        dirs.sort()
        for name in sorted(files):
            if name.endswith('.java'):
                yield os.path.join(top, name)


def rel(path, root):
    return os.path.relpath(path, root).replace(os.sep, '/')


def resolve(name, root):
    for candidate in (name, os.path.join(root, name), os.path.join(root, 'src', name),
                      os.path.join(root, 'src', 'org', 'traincontrol', name)):
        if os.path.isfile(candidate):
            return candidate
    sys.exit('no such source file: ' + name)


def class_body_lines(text):
    """The line numbers (1-based) that start directly inside a class, interface or enum body, not inside code."""
    stack = []
    inside = set()
    line = 1
    i = 0
    n = len(text)
    header = ''
    while i < n:
        c = text[i]
        if c == '\n':
            line += 1
            if stack and stack[-1]:
                inside.add(line)
            i += 1
            continue
        if text.startswith('//', i):
            j = text.find('\n', i)
            i = n if j < 0 else j
            continue
        if text.startswith('/*', i):
            j = text.find('*/', i + 2)
            j = n if j < 0 else j + 2
            for _ in range(text.count('\n', i, j)):
                line += 1
                if stack and stack[-1]:
                    inside.add(line)
            i = j
            continue
        if c in '"\'':
            j = i + 1
            while j < n and text[j] != c and text[j] != '\n':
                j += 2 if text[j] == '\\' else 1
            header += ' '
            i = j + 1
            continue
        if c == '{':
            stack.append(bool(re.search(r'\b(class|interface|enum)\s+\w+[^;(]*$', header))
                         and not re.search(r'\bnew\s', header))
            header = ''
        elif c == '}':
            if stack:
                stack.pop()
            header = ''
        elif c == ';':
            header = ''
        else:
            header += c
        i += 1
    return inside


def members(text):
    """name -> (kind, first line) of what the source declares directly in a class body."""
    found = {}
    body_lines = class_body_lines(text)
    for regex, kind in ((TYPE, 'type'), (FIELD, 'field'), (METHOD, 'method')):
        for m in regex.finditer(text):
            number = text.count('\n', 0, m.start()) + 1
            if kind != 'type' and number not in body_lines:
                continue
            line = text[m.start():text.find('\n', m.start())]
            if kind == 'method' and (CONSTRUCTOR_OR_CALL.match(line) or '=' in line.split('(')[0]):
                continue
            name = m.group(m.lastindex)
            if name not in found:
                found[name] = (kind, number)
    return found


def declaring_files(names, root):
    """name -> sorted list of src files whose class-level declarations include it."""
    where = collections.defaultdict(set)
    for path in java_files(os.path.join(root, 'src')):
        declared = members(read(path))
        for name in names:
            if name in declared:
                where[name].add(rel(path, root))
    return {name: sorted(where.get(name, ())) for name in names}


def helpers_in(text):
    """Names of methods in a test that take a String parameter and reflect on it."""
    found = set()
    for m in HELPER.finditer(text):
        params = re.findall(r'String\s+(\w+)', m.group(2))
        if not params:
            continue
        body = text[m.end():m.end() + 3000]
        end = body.find('\n    }')
        body = body[:end if end > 0 else len(body)]
        if any(re.search(REFLECT_BY_PARAM.format(re.escape(p)), body) for p in params):
            found.add(m.group(1))
    return found


def audit(class_files, root):
    declared = {}
    for path in class_files:
        for name, (kind, line) in members(read(path)).items():
            if len(name) > 2:
                declared.setdefault(name, (kind, rel(path, root), line))
    tests = {rel(p, root): read(p) for p in java_files(os.path.join(root, 'test'))}

    hits = collections.defaultdict(lambda: collections.defaultdict(list))
    for path, text in tests.items():
        lines = text.split('\n')
        reads_source = '"src/' in text or '"src"' in text or 'SourceOf.' in text
        helpers = helpers_in(text)
        helper_call = re.compile(r'\b(' + '|'.join(map(re.escape, sorted(helpers))) + r')\s*\(') if helpers else None
        string_lines = collections.defaultdict(list)
        for number, line in enumerate(lines, 1):
            for m in STRING.finditer(line):
                literal = m.group(1)
                if literal in declared:
                    hits[literal]['literal'].append('%s:%d' % (path, number))
                    string_lines[literal].append(number)
                if reads_source:
                    for name in re.findall(r'(?<![\w.])(\w+)\(', literal):
                        if name in declared:
                            hits[name]['signature'].append('%s:%d' % (path, number))
                            string_lines[name].append(number)
            for m in REFLECT.finditer(line):
                if m.group(2) in declared:
                    hits[m.group(2)]['reflect'].append('%s:%d %s' % (path, number, m.group(1)))
            if helper_call:
                for m in helper_call.finditer(line):
                    call = line[m.end():m.end() + 200]
                    for literal in STRING.findall(call.split(';')[0]):
                        if literal in declared:
                            hits[literal]['helper'].append('%s:%d %s()' % (path, number, m.group(1)))
        for name, numbers in string_lines.items():
            for number in sorted(set(numbers)):
                for after in range(number, min(len(lines), number + NEAR)):
                    if CATCH.search(lines[after]):
                        hits[name]['catch'].append('%s:%d' % (path, after + 1))
                        break
    for name in hits:
        for kind in hits[name]:
            hits[name][kind] = sorted(set(hits[name][kind]))
    return declared, hits


def summary(declared, hits, out):
    files = set()
    counts = collections.Counter()
    for name, kinds in hits.items():
        for kind, sites in kinds.items():
            counts[kind] += len(sites)
            files.update(s.split(':')[0] for s in sites)
    by_file = collections.Counter()
    for name, kinds in hits.items():
        for f in set(s.split(':')[0] for sites in kinds.values() for s in sites):
            by_file[f] += 1
    named = [n for n in hits if hits[n]]
    out.write('members declared: %d; named in tests: %d, in %d test files\n' % (len(declared), len(named), len(files)))
    out.write('sites: string literals %d, reflection calls %d, name-taking helper calls %d, quoted signatures %d, '
              'NoSuch*Exception catches near a name %d\n' % (counts['literal'], counts['reflect'], counts['helper'],
                                                             counts['signature'], counts['catch']))
    out.write('\nmost named (test files): ')
    ranked = sorted(named, key=lambda n: -len(set(s.split(':')[0] for v in hits[n].values() for s in v)))
    out.write(', '.join('%s (%d)' % (n, len(set(s.split(':')[0] for v in hits[n].values() for s in v)))
                        for n in ranked[:25]) + '\n')
    out.write('\ntest files naming the most members: ')
    out.write(', '.join('%s (%d)' % (os.path.basename(f)[:-5], c) for f, c in by_file.most_common(15)) + '\n')
    catches = sorted(set(s for v in hits.values() for s in v.get('catch', ())))
    if catches:
        out.write('\ncatches to read (they may swallow a moved member): ' + ', '.join(catches) + '\n')


def detail(declared, hits, out):
    for name in sorted(hits, key=lambda n: (declared[n][1], declared[n][2])):
        kind, path, line = declared[name]
        out.write('\n%s  (%s, %s:%d)\n' % (name, kind, path, line))
        for what in ('reflect', 'helper', 'signature', 'catch', 'literal'):
            for site in hits[name].get(what, ()):
                out.write('    %-9s %s\n' % (what, site))


def main():
    parser = argparse.ArgumentParser(description=__doc__.split('\n\n')[0].strip(), epilog=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('classes', nargs='+', help='source files of the class(es) to audit, e.g. gui/TrainControlUI.java')
    parser.add_argument('--root', default='.', help='project root (default: the current folder)')
    parser.add_argument('--summary', action='store_true', help='print the counts and the most-named members only')
    parser.add_argument('--save', metavar='JSON', help='also write the members, their declaring files and every site')
    parser.add_argument('--compare', metavar='JSON', help='a --save from before a step: list the members whose '
                        'declaring file changed since, and the tests that name them')
    args = parser.parse_args()

    root = os.path.abspath(args.root)
    class_files = [resolve(c, root) for c in args.classes]

    if args.compare:
        with open(args.compare, encoding='utf-8') as f:
            before = json.load(f)
        now = declaring_files(list(before['declared_in']), root)
        moved = [n for n in sorted(before['declared_in']) if now[n] != before['declared_in'][n]]
        print('%d of %d members audited before are declared in a different file now' % (len(moved),
                                                                                         len(before['declared_in'])))
        for name in moved:
            sites = sorted(set(s for v in before['sites'].get(name, {}).values() for s in v))
            print('\n%s: %s -> %s' % (name, ', '.join(before['declared_in'][name]) or '(nowhere)',
                                      ', '.join(now[name]) or '(nowhere)'))
            for site in sites:
                print('    ' + site)
            if not sites:
                print('    (no test names it)')
        return

    declared, hits = audit(class_files, root)
    if args.summary:
        summary(declared, hits, sys.stdout)
    else:
        summary(declared, hits, sys.stdout)
        detail(declared, hits, sys.stdout)
    if args.save:
        with open(args.save, 'w', encoding='utf-8') as f:
            json.dump({'classes': [rel(c, root) for c in class_files],
                       'declared_in': declaring_files(sorted(declared), root),
                       'sites': {n: dict(v) for n, v in hits.items()}}, f, indent=1, sort_keys=True)


if __name__ == '__main__':
    main()
