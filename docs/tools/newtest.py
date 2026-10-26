#!/usr/bin/env python3
"""Does the bookkeeping a new test class needs, and says what it changed.

    py -3 docs/tools/newtest.py ui.testSomething            dry run: prints what it would change
    py -3 docs/tools/newtest.py --write ui.testSomething    applies it

Run it from the project root once the class file exists under test/.  Three things, each skipped when already done:

  1. build.xml: a <test-one-class class="..."/> line after the last one (regression.testEveryTestIsInTheBattery).
  2. The window census in regression.testSwitchingToACentralStationLayout: the number of test classes that build a
     TrainControlUI is recounted the way that test counts it; when it differs, the pin and its message are set to the
     recount and a dated comment line names the class.
  3. "## Used by" in test/layouts/<scenario>/README.md for each scenario the class names in quotes
     (regression.testEveryScenarioIsUsedAndSaysSo).

It cannot do the rest of CLAUDE.md's list, so it prints a reminder: a class that opens a LayoutSandbox opens it first
inside its try (testEverySandboxIsOpenedInsideATry), and a class that builds a model without a sandbox is not allowed.
`--root DIR` works on another checkout (a scratch copy).  Exit 0 when nothing is left to do or the change was written, 1
when a dry run found work, 2 on a usage error.
"""
import datetime
import os
import re
import sys

CENSUS_TEST = "testSwitchingToACentralStationLayout.java"
WINDOW_BUILT = re.compile(r"new\s+(?:org\.traincontrol\.gui\.)?TrainControlUI\s*\(")
SANDBOX_OPENED = re.compile(r"LayoutSandbox\.open\s*\(")


def read(path):
    with open(path, "rb") as f:
        return f.read().decode("utf-8")


def write(path, text):
    with open(path, "wb") as f:
        f.write(text.encode("utf-8"))


def eol_of(text):
    return "\r\n" if "\r\n" in text else "\n"


def code_only(src):
    """The source with comments, string and char literals blanked, in the order the compiler reads them."""
    out, i, n = [], 0, len(src)
    while i < n:
        c = src[i]
        two = src[i:i + 2]
        if two == "//":
            while i < n and src[i] != "\n":
                i += 1
        elif two == "/*":
            j = src.find("*/", i + 2)
            i = n if j < 0 else j + 2
        elif c in "\"'":
            i += 1
            while i < n and src[i] != c:
                i += 2 if src[i] == "\\" else 1
            i += 1
            out.append(c + c)
        else:
            out.append(c)
            i += 1
    return "".join(out)


def java_files(root):
    for base, _dirs, names in os.walk(os.path.join(root, "test")):
        for name in names:
            if name.endswith(".java"):
                yield os.path.join(base, name)


def plan_build_xml(root, simple):
    path = os.path.join(root, "build.xml")
    text = read(path)
    line = '<test-one-class class="%s"/>' % simple
    if line in re.sub(r"(?s)<!--.*?-->", "", text):
        return None, "build.xml: already has %s" % line
    last = None
    for m in re.finditer(r"[ \t]*<test-one-class class=\"[^\"]+\"/>[ \t]*\r?\n", text):
        last = m
    if last is None:
        raise SystemExit("build.xml has no test-one-class line to put it after")
    indent = re.match(r"[ \t]*", last.group(0)).group(0)
    new = text[:last.end()] + indent + line + eol_of(text) + text[last.end():]
    return (path, new), "build.xml: add %s after the last one" % line


def plan_census(root, qualified, source):
    path = None
    for p in java_files(root):
        if os.path.basename(p) == CENSUS_TEST:
            path = p
    if path is None:
        return None, "census: %s not found, nothing to check" % CENSUS_TEST
    count = 0
    for p in java_files(root):
        if os.path.basename(p) == "LayoutSandbox.java":
            continue
        if WINDOW_BUILT.search(code_only(read(p))):
            count += 1
    text = read(path)
    pin = re.search(r"assertEquals\(checked, (\d+),(\s*)checked \+ \" test classes were found to build a window, "
                    r"not the (\d+) there were", text)
    if pin is None:
        return None, "census: could not find the pinned count in %s; recount by hand (%d now)" % (CENSUS_TEST, count)
    if int(pin.group(1)) == count:
        return None, "census: already %d" % count
    builds = bool(WINDOW_BUILT.search(code_only(source)))
    eol = eol_of(text)
    start = text.rfind("\n", 0, pin.start()) + 1
    indent = re.match(r"[ \t]*", text[start:]).group(0)
    comment = ""
    if builds:
        sandbox = " which opens a sandbox first." if SANDBOX_OPENED.search(code_only(source)) else " which opens NO sandbox."
        comment = "%s// %d on %s: %s,%s%s" % (indent, count, datetime.date.today().isoformat(), qualified,
                                              sandbox, eol)
    fixed = text[:pin.start(1)] + str(count) + text[pin.end(1):pin.start(3)] + str(count) + text[pin.end(3):]
    fixed = fixed[:start] + comment + fixed[start:]
    what = "census: window count %s -> %d in %s%s" % (pin.group(1), count, CENSUS_TEST,
                                                      " (with a comment line naming the class)" if comment else
                                                      " (this class builds no window; the count moved for another)")
    return (path, fixed), what


def plan_used_by(root, qualified, source):
    out = []
    layouts = os.path.join(root, "test", "layouts")
    if not os.path.isdir(layouts):
        return out
    for scenario in sorted(os.listdir(layouts)):
        readme = os.path.join(layouts, scenario, "README.md")
        if not os.path.isfile(readme) or ('"%s"' % scenario) not in source:
            continue
        text = read(readme)
        bullet = "- `%s`" % qualified
        head = text.find("## Used by")
        if head < 0:
            out.append((None, "layouts/%s: README has no \"## Used by\" heading" % scenario))
            continue
        nxt = text.find("\n## ", head + 1)
        section_end = len(text) if nxt < 0 else nxt
        if bullet in text[head:section_end]:
            out.append((None, "layouts/%s: already lists %s" % (scenario, qualified)))
            continue
        eol = eol_of(text)
        last = None
        for m in re.finditer(r"^- `[^`\r\n]+`[^\r\n]*", text[head:section_end], re.M):
            last = m
        if last is None:
            at = text.find("\n", head) + 1
            new = text[:at] + eol + bullet + eol + text[at:]
        else:
            at = head + last.end()
            new = text[:at] + eol + bullet + text[at:]
        out.append(((readme, new), "layouts/%s: add %s under \"## Used by\"" % (scenario, bullet)))
    return out


def main(argv):
    args = argv[1:]
    do_write = "--write" in args
    root = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
    if "--root" in args:
        i = args.index("--root")
        root = os.path.abspath(args[i + 1])
        del args[i:i + 2]
    names = [a for a in args if not a.startswith("--")]
    if len(names) != 1 or "." not in names[0]:
        print(__doc__.split("\n\n")[0])
        return 2
    qualified = names[0]
    folder, simple = qualified.rsplit(".", 1)
    file = os.path.join(root, "test", folder, simple + ".java")
    if not os.path.isfile(file):
        print("no such test class: %s" % file)
        return 2
    source = read(file)
    steps = []
    if "@Test" in source:
        steps.append(plan_build_xml(root, simple))
    else:
        print("%s has no @Test, so it is a fixture: build.xml and the census are not touched" % qualified)
    steps.append(plan_census(root, qualified, source))
    steps.extend(plan_used_by(root, qualified, source))
    pending = {}
    for edit, what in steps:
        print(("would: " if edit and not do_write else "done: " if edit else "ok:    ") + what)
        if edit:
            pending[edit[0]] = edit[1]
    if SANDBOX_OPENED.search(code_only(source)):
        print("remember: open the sandbox as the first statement inside the try (sandbox = null before it, "
              "closed under if (sandbox != null)); a model built without one is not allowed")
    if do_write:
        for path, text in pending.items():
            write(path, text)
        return 0
    return 1 if pending else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
