#!/usr/bin/env python3
"""
Snapshots the members of compiled classes with javap, and diffs two snapshots (FR-136).

An extraction must move only the members it planned to move, and must not change a public signature at all.  This
records, per class, the member lines `javap -p` prints (every member, private ones included) or `javap -public`
prints (the API), sorted so that a member moving within a class is not a difference, and compares two such records.

How the phases use it (docs/refactor-3.0.3.md, section 5):
  - Phases 3-5, before an extraction step:
        python docs/tools/javap-members.py build --out <scratch>/before-classes
        python docs/tools/javap-members.py snapshot --classes <scratch>/before-classes --out before-p.txt
        python docs/tools/javap-members.py snapshot --classes <scratch>/before-classes --public --out before-pub.txt
    and the same after it into after-*.txt.
  - `diff before-p.txt after-p.txt` must show only the planned moves: the members leaving TrainControlUI, the new
    package-private helper class and its members, and the one-line delegators that stay.
  - `diff before-pub.txt after-pub.txt` must be empty for Layout, MarklinControlStation, Locomotive, Point, Edge,
    model/View and every public gui class - which, with every class snapshotted, is "must be empty" outright unless
    the step adds a public class on purpose.
  - Exit status: `diff` exits 1 when the snapshots differ, so it can gate a script.

`build` compiles src/ with the JDK the test runner uses (TC_JDK, or C:/Program Files/Java/jdk1.8.0_361), on the same
classpath as docs/tools/one.sh, into a folder of your choosing; it never writes into the project.

Examples, from the project root:
  python docs/tools/javap-members.py build --out C:/scratch/classes-before
  python docs/tools/javap-members.py snapshot --classes C:/scratch/classes-before --out before-p.txt
  python docs/tools/javap-members.py snapshot --classes C:/scratch/classes-before --public \\
      --only org.traincontrol.automation.Layout org.traincontrol.marklin.MarklinControlStation --out api.txt
  python docs/tools/javap-members.py diff before-p.txt after-p.txt
"""
import argparse
import difflib
import os
import subprocess
import sys
import tempfile

CLASSPATH = ['resources/flatlaf-3.7.2.jar', 'resources/json-20260814.jar']
BATCH = 200


def jdk_tool(name):
    home = os.environ.get('TC_JDK', 'C:/Program Files/Java/jdk1.8.0_361')
    path = os.path.join(home, 'bin', name + ('.exe' if os.name == 'nt' else ''))
    if not os.path.isfile(path):
        sys.exit('no %s at %s - set TC_JDK to a JDK 8 folder' % (name, path))
    return path


def java_sources(folder):
    for top, dirs, files in os.walk(folder):
        dirs.sort()
        for name in sorted(files):
            if name.endswith('.java'):
                yield os.path.join(top, name)


def build(src, out, root):
    """Compiles every .java under src into out; returns javac's exit status."""
    os.makedirs(out, exist_ok=True)
    with tempfile.NamedTemporaryFile('w', suffix='-javap-files.txt', delete=False, encoding='utf-8') as listing:
        for path in java_sources(src):
            listing.write('"%s"\n' % os.path.abspath(path).replace('\\', '/'))
    cp = os.pathsep.join([os.path.abspath(src)] + [os.path.join(root, j) for j in CLASSPATH])
    try:
        return subprocess.call([jdk_tool('javac'), '-nowarn', '-encoding', 'UTF-8', '-proc:none', '-d', out, '-cp', cp,
                                '@' + listing.name])
    finally:
        os.unlink(listing.name)


def class_names(folder):
    names = []
    for top, dirs, files in os.walk(folder):
        for name in files:
            if name.endswith('.class'):
                path = os.path.relpath(os.path.join(top, name), folder)
                names.append(path[:-6].replace(os.sep, '.'))
    return sorted(names)


def javap(folder, names, flags):
    """class name -> sorted member lines, from javap run in batches."""
    members = {}
    for at in range(0, len(names), BATCH):
        batch = names[at:at + BATCH]
        text = subprocess.run([jdk_tool('javap'), '-J-Dfile.encoding=UTF-8'] + flags + ['-cp', folder] + batch, stdout=subprocess.PIPE,
                              stderr=subprocess.PIPE, encoding='utf-8', errors='replace').stdout
        current = None
        for line in text.splitlines():
            if line.startswith('Compiled from'):
                continue
            if not line.startswith(' ') and line.rstrip().endswith('{'):
                current = line.rstrip()[:-1].strip()
                members[current] = []
            elif line.strip() == '}':
                current = None
            elif current is not None and line.strip():
                members[current].append(line.strip())
    return {k: sorted(v) for k, v in members.items()}


def snapshot(folder, only, public, out):
    names = only or class_names(folder)
    members = javap(folder, names, ['-public'] if public else ['-p'])
    with open(out, 'w', encoding='utf-8', newline='\n') as f:
        for header in sorted(members):
            f.write(header + '\n')
            for line in members[header]:
                f.write('    ' + line + '\n')
    print('%d classes, %d members -> %s' % (len(members), sum(len(v) for v in members.values()), out))


def diff(before, after):
    with open(before, encoding='utf-8') as f:
        a = f.read().splitlines(True)
    with open(after, encoding='utf-8') as f:
        b = f.read().splitlines(True)
    lines = list(difflib.unified_diff(a, b, before, after, n=1))
    sys.stdout.writelines(lines)
    if not lines:
        print('no difference')
    return 1 if lines else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__.split('\n\n')[0].strip(), epilog=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest='command')
    b = sub.add_parser('build', help='compile src/ into a folder outside the project')
    b.add_argument('--src', default='src', help='source folder (default: src)')
    b.add_argument('--out', required=True, help='folder for the classes')
    s = sub.add_parser('snapshot', help='write the sorted member list of compiled classes')
    s.add_argument('--classes', required=True, help='a folder of compiled classes (from build)')
    s.add_argument('--public', action='store_true', help='public members only (javap -public), the API check')
    s.add_argument('--only', nargs='+', metavar='CLASS', help='fully qualified classes (default: every class)')
    s.add_argument('--out', required=True, help='the snapshot file to write')
    d = sub.add_parser('diff', help='compare two snapshots; exit 1 when they differ')
    d.add_argument('before')
    d.add_argument('after')
    args = parser.parse_args()

    if args.command == 'build':
        sys.exit(build(args.src, args.out, os.getcwd()))
    if args.command == 'snapshot':
        snapshot(args.classes, args.only, args.public, args.out)
    elif args.command == 'diff':
        sys.exit(diff(args.before, args.after))
    else:
        parser.print_help()


if __name__ == '__main__':
    main()
