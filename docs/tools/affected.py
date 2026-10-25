#!/usr/bin/env python3
"""Lists the test classes a change should run, one pkg.Class per line, ready for TC_CLASSES.

    py -3 docs/tools/affected.py <base> [<head>] [-v] > classes.txt

Compares <base> with <head> (the working tree, untracked files included, when no head is given).  Run from the project
root.  A test class is listed when it
  - names a changed source class (its name as a word anywhere in the test), which covers imports, mentions and tests that
    read that file's source by path;
  - reads source through support.SourceOf and looks for a method that a changed source file declares;
  - names a message key whose line changed in a bundle, or a changed doc, resource or scenario folder by file name;
  - is itself changed, or names a changed test support class;
  - is a bookkeeping class the change calls for: a new test (testEveryTestIsInTheBattery, plus the census class when it
    builds a window, plus the scenario class when it names one), a comment line touched (testCommentsStayLean,
    testJavadocsAreAttached), a `synchronized` line touched (the railway monitor class), a bundle (testMessageBundles,
    testEveryMessageKeyIsAskedFor, testEveryLanguageFits), a source file (testNoNewCopies);
  - is paired by CLAUDE.md's standing rules (PAIRINGS below).
-v says on stderr why each class is there.  It over-includes for a change to a class most tests name (Layout, Util): the
list is then long, and that is the honest answer.  The full battery stays the orchestrator's.
"""
import os
import re
import subprocess
import sys

KNOWN_OUT = {"core.testAutoDetect"}

# CLAUDE.md: a changed file (by name) calls for these classes (by simple name) whether or not they name it.
PAIRINGS = [
    ("LayoutEditor.java", ["testTheEditorNamesItsShortcuts", "testTheTrackEditorByHand"]),
]

WINDOW = re.compile(r"new\s+(?:org\.traincontrol\.gui\.)?TrainControlUI\s*\(")


def git(*args):
    out = subprocess.run(["git"] + list(args), capture_output=True, stdin=subprocess.DEVNULL)
    return out.stdout.decode("utf-8", "replace")


def read(path):
    with open(path, "rb") as f:
        return f.read().decode("utf-8", "replace")


DECLARATION = re.compile(r"(?m)^[ \t]*(?:(?:public|private|protected|static|final|synchronized|abstract)\s+)*"
                         r"[\w<>\[\],.? ]+?\s+(\w+)\s*\([^;{}]*\)\s*(?:throws [\w., ]+)?\s*\{")


def without_comments(src):
    """The Java source with its comments removed and its strings kept."""
    out, i, n = [], 0, len(src)
    while i < n:
        if src.startswith("//", i):
            while i < n and src[i] != "\n":
                i += 1
        elif src.startswith("/*", i):
            j = src.find("*/", i + 2)
            i = n if j < 0 else j + 2
        elif src[i] in "\"'":
            q, j = src[i], i + 1
            while j < n and src[j] != q:
                j += 2 if src[j] == "\\" else 1
            out.append(src[i:j + 1])
            i = j + 1
        else:
            out.append(src[i])
            i += 1
    return "".join(out)


def main(argv):
    sys.stdout.reconfigure(newline="\n")  # a list file with CR line ends names classes that do not exist
    verbose = "-v" in argv
    refs = [a for a in argv[1:] if not a.startswith("-")]
    if not refs or not os.path.isfile("build.xml"):
        print(__doc__.split("\n\n")[0], file=sys.stderr)
        return 2
    base = refs[0]
    head = refs[1] if len(refs) > 1 else None
    span = [base] + ([head] if head else [])

    changed = {}  # path -> status
    for line in git("diff", "--name-status", "-M", *span).splitlines():
        parts = line.split("\t")
        changed[parts[-1]] = parts[0][0]
    if head is None:
        for p in git("ls-files", "--others", "--exclude-standard").splitlines():
            changed[p] = "A"

    def text_of(path):
        if head is None:
            return read(path) if os.path.isfile(path) else ""
        return git("show", "%s:%s" % (head, path))

    def diff_of(path):
        return git("diff", *(span + ["--", path]))

    tests = {}  # qualified -> text, for classes carrying @Test
    support = {}
    for folder in sorted(os.listdir("test")):
        d = os.path.join("test", folder)
        if not os.path.isdir(d):
            continue
        for name in sorted(os.listdir(d)):
            if name.endswith(".java"):
                text = read(os.path.join(d, name))
                (tests if "@Test" in text else support)["%s.%s" % (folder, name[:-5])] = text
    by_simple = {q.split(".")[1]: q for q in tests}

    why = {}

    def add(qualified, reason):
        if qualified in tests and qualified not in KNOWN_OUT:
            why.setdefault(qualified, []).append(reason)

    src_names, doc_names, keys, resources, scenarios, simple_changed = [], [], [], [], [], set()
    src_text = {}
    comment_touched = synchronized_touched = bundle = False

    for path, status in sorted(changed.items()):
        base_name = os.path.basename(path)
        simple_changed.add(base_name)
        diff = diff_of(path) if status != "A" or head else ""
        added_removed = [l[1:].strip() for l in diff.splitlines()
                         if l[:1] in "+-" and not l.startswith(("+++", "---"))]
        if status == "A" and head is None:
            added_removed = [l.strip() for l in text_of(path).splitlines()]
        if path.startswith("src/") and path.endswith(".java"):
            src_names.append(base_name[:-5])
            src_text[path] = text_of(path)
            if any(l.startswith(("//", "/*", "*")) for l in added_removed):
                comment_touched = True
            if any("synchronized" in l for l in added_removed):
                synchronized_touched = True
        elif path.endswith(".properties"):
            bundle = True
            for l in added_removed:
                m = re.match(r"([\w.]+)\s*=", l)
                if m:
                    keys.append(m.group(1))
        elif path.startswith("test/") and path.endswith(".java"):
            q = path.split("/")[1] + "." + base_name[:-5]
            if status == "D":
                continue
            if q in tests:
                add(q, "is itself changed")
            elif q in support:
                src_names.append(base_name[:-5])
            if status == "A" and q in tests:
                add("regression.testEveryTestIsInTheBattery", "new test %s" % q)
                if WINDOW.search(tests[q]):
                    add("regression.testSwitchingToACentralStationLayout", "new test %s builds a window" % q)
                for s in os.listdir("test/layouts") if os.path.isdir("test/layouts") else []:
                    if '"%s"' % s in tests[q]:
                        add("regression.testEveryScenarioIsUsedAndSaysSo", "new test %s names scenario %s" % (q, s))
            if any(l.startswith(("//", "/*", "*")) for l in added_removed):
                comment_touched = True
        elif path.startswith("test/layouts/"):
            scenarios.append(path.split("/")[2])
        elif path == "build.xml":
            add("regression.testEveryTestIsInTheBattery", "build.xml changed")
        else:
            doc_names.append(base_name)
        if path.startswith("src/") and not path.endswith(".java") and not path.endswith(".properties"):
            resources.append(base_name)

    words = [(n, re.compile(r"\b%s\b" % re.escape(n))) for n in sorted(set(src_names))]
    called = {}  # method names the changed source files declare
    for path, text in src_text.items():
        for m in set(DECLARATION.findall(text)):
            called.setdefault(m, set()).add(os.path.basename(path)[:-5])

    for q, full in tests.items():
        text = without_comments(full)
        for n, rx in words:
            if rx.search(text):
                add(q, "names %s" % n)
        if "SourceOf" in text:
            for lit in re.findall(r'"([^"\n]*\()', text):
                m = re.search(r"\w[\w<>\[\]]*\s+(\w+)\($", lit)
                if m and m.group(1) in called:
                    add(q, "reads the source of %s (%s)" % (sorted(called[m.group(1)])[0], m.group(1)))
                    break
        for k in set(keys):
            if k in text:
                add(q, "names the key %s" % k)
        for d in set(doc_names + resources):
            if d in text:
                add(q, "reads %s" % d)
        for s in set(scenarios):
            if '"%s"' % s in text:
                add(q, "uses scenario %s" % s)

    if any(p.startswith("src/") and p.endswith(".java") for p in changed) and "testNoNewCopies" in by_simple:
        add(by_simple["testNoNewCopies"], "a source file changed")
    if comment_touched:
        for n in ("testCommentsStayLean", "testJavadocsAreAttached"):
            if n in by_simple:
                add(by_simple[n], "a comment line changed")
    if synchronized_touched and "testNothingOnTheEventThreadTakesTheRailwaysMonitor" in by_simple:
        add(by_simple["testNothingOnTheEventThreadTakesTheRailwaysMonitor"], "a synchronized line changed")
    if bundle:
        for n in ("testMessageBundles", "testEveryMessageKeyIsAskedFor", "testEveryLanguageFits"):
            if n in by_simple:
                add(by_simple[n], "a bundle changed")
    for file, classes in PAIRINGS:
        if file in simple_changed:
            for n in classes:
                if n in by_simple:
                    add(by_simple[n], "CLAUDE.md pairs it with %s" % file)

    for q in sorted(why):
        print(q)
        if verbose:
            print("%s: %s" % (q, "; ".join(why[q][:3])), file=sys.stderr)
    print("%d classes for %d changed files" % (len(why), len(changed)), file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
