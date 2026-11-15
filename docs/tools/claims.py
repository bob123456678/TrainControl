#!/usr/bin/env python3
"""Finds a test claim without reading a test class: one line per @Test method with its line range.

    py -3 docs/tools/claims.py WORDS...          methods whose class or method name has every word (camelCase split)
    py -3 docs/tools/claims.py --id OB-123       methods citing the id in comments, and tests the tracker names for it
    py -3 docs/tools/claims.py --class testHomeStaging   every method of the class, then its helpers' ranges
    py -3 docs/tools/claims.py --uses Layout     test classes that name the production class as a word
    py -3 docs/tools/claims.py --selftest

Output: `test/<path>:<first>-<last>  <Class>.<method>  <ids>`.  The range covers the method's attached javadoc or
comment, its annotations and its body, so a ranged read of those lines is the whole claim.  Reads test/**/*.java on
every run (nothing committed, nothing stale); works from anywhere in the repository.  Read the ranges of the helpers a
method calls the same way (`--class` lists them).  `--uses` is by name, like docs/tools/affected.py, which answers
the same question for a change between two commits.
"""
import os
import re
import sqlite3
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
IDS = re.compile(r"\b(?:OB|MT|FR)-\d+\b")
ANNOTATION = re.compile(r"@\w+(?:\.\w+)*\s*(\((?:[^()]|\([^()]*\))*\))?")


TOKEN = re.compile(r"//[^\n]*|/\*.*?\*/|\"(?:\\.|[^\"\\\n])*\"|'(?:\\.|[^'\\\n])*'", re.S)
STRUCTURE = re.compile(r"[(){};]")
BLANK = re.compile(r"[^\n]")


def scan(src):
    """Code with comments and string contents blanked (same length, newlines kept), and the comments found.

    :return: (code, comments) where comments is a list of (first line, last line, text)
    """
    out, comments, last, line = [], [], 0, 1
    for m in TOKEN.finditer(src):
        out.append(src[last:m.start()])
        line += src.count("\n", last, m.start())
        text = m.group(0)
        if text[0] == "/":
            comments.append((line, line + text.count("\n"), text))
            out.append(BLANK.sub(" ", text))
        else:
            out.append(text[0] + BLANK.sub(" ", text[1:-1]) + text[-1])
        line += text.count("\n")
        last = m.end()
    out.append(src[last:])
    return "".join(out), comments


def members(src):
    """The members of the file's top-level class.

    :return: list of dicts: name, test (bool), first, last (lines), ids (sorted list)
    """
    code, comments = scan(src)
    starts = [0]
    for m in re.finditer("\n", code):
        starts.append(m.end())

    def line_of(pos):
        lo, hi = 0, len(starts)
        while hi - lo > 1:
            mid = (lo + hi) // 2
            if starts[mid] <= pos:
                lo = mid
            else:
                hi = mid
        return lo + 1

    top = re.search(r"\b(?:class|interface|enum)\s+\w+[^{;]*\{", code)
    if not top:
        return []
    out, depth, paren, mstart = [], 0, 0, top.end()
    for m in STRUCTURE.finditer(code, top.end()):
        c, i = m.group(0), m.start()
        if c == "(":
            paren += 1
        elif c == ")":
            paren -= 1
        elif paren:
            continue
        elif c == "{":
            depth += 1
        elif c == "}":
            if depth == 0:
                break
            depth -= 1
            if depth == 0 and not _initializer(code[mstart:i]):
                _add(out, code, src, mstart, i + 1, line_of, comments)
                mstart = i + 1
        elif depth == 0:
            _add(out, code, src, mstart, i + 1, line_of, comments)
            mstart = i + 1
    return out


def _initializer(text):
    """True when the text so far is a field with an initializer (a `=` before its first brace), not a block member."""
    return "=" in ANNOTATION.sub(" ", text).split("{", 1)[0]


def _add(out, code, src, a, b, line_of, comments):
    text = code[a:b]
    if not text.strip():
        return
    lead = len(text) - len(text.lstrip())
    first = line_of(a + lead)
    last = line_of(b - 1)
    # attached comments: directly above, each one touching the next
    edge = first
    for cs, ce, _ in reversed([c for c in comments if c[1] < first]):
        if ce >= edge - 1:
            edge = cs
        else:
            break
    body = [c for c in comments if edge <= c[0] and c[1] <= last]
    ids = sorted(set(x for c in body for x in IDS.findall(c[2])), key=lambda s: (s[:2], int(s.split("-")[1])))
    stripped = ANNOTATION.sub(" ", text)
    is_test = any(re.fullmatch(r"@Test\b.*", m.group(0).strip(), re.S) for m in ANNOTATION.finditer(text))
    head = stripped.split("{", 1)[0].split("=", 1)[0]
    m = re.search(r"(\w+)\s*\(", head)
    kind = re.search(r"\b(class|interface|enum)\s+(\w+)", head)
    if kind:
        name = kind.group(2)
    elif m:
        name = m.group(1)
    else:
        found = re.findall(r"\w+", head)
        name = found[-1] if found else "?"
    out.append({"name": name, "test": is_test, "first": edge, "last": last, "ids": ids})


def read(path):
    with open(path, "rb") as f:
        return f.read().decode("utf-8", "replace")


def classes(root=ROOT):
    """(path relative to the root, simple class name, source) for every test source file."""
    for folder, _, names in sorted(os.walk(os.path.join(root, "test"))):
        for name in sorted(names):
            if name.endswith(".java"):
                full = os.path.join(folder, name)
                yield os.path.relpath(full, root).replace(os.sep, "/"), name[:-5], read(full)


def words_of(name):
    return re.sub(r"(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])", " ", name).lower()


def line(path, first, last, cls, name, ids):
    return "%s:%d-%d  %s.%s  %s" % (path, first, last, cls, name, " ".join(ids))


def tests_of(root=ROOT):
    """(path, class, member) for every @Test method."""
    for path, cls, src in classes(root):
        if "@Test" in src:
            for m in members(src):
                if m["test"]:
                    yield path, cls, m


def by_words(words, root=ROOT):
    ws = [w.lower() for w in words]
    for path, cls, m in tests_of(root):
        hay = " ".join([(cls + "." + m["name"]).lower(), words_of(cls), words_of(m["name"])])
        if all(w in hay for w in ws):
            print(line(path, m["first"], m["last"], cls, m["name"], m["ids"]))


def tracker_names(ref, root=ROOT):
    """(class, method or None) pairs that the tracker's comments, item and receipt for ref name as `pkg.testX[.method]`."""
    db = os.path.join(root, "docs", "manual-tests", "triage.db")
    if not os.path.isfile(db):
        return set()
    conn = sqlite3.connect("file:%s?mode=ro" % db.replace("\\", "/"), uri=True)
    try:
        texts = [r[0] for r in conn.execute("SELECT text FROM comment WHERE tag = ?", (ref,))]
        texts += [r[0] for r in conn.execute("SELECT text FROM issue WHERE ref = ?", (ref,))]
        texts += [r[0] or "" for r in conn.execute("SELECT what FROM receipt WHERE ref = ?", (ref,))]
    except sqlite3.Error:
        return set()
    finally:
        conn.close()
    found = set()
    for t in texts:
        for m in re.finditer(r"\b(?:\w+\.)?(test\w+)(?:\.(\w+))?", t):
            found.add((m.group(1), m.group(2)))
    return found


def by_id(ref, root=ROOT):
    ref = ref.upper()
    named = tracker_names(ref, root)
    shown = set()
    for path, cls, m in tests_of(root):
        if ref in m["ids"]:
            print(line(path, m["first"], m["last"], cls, m["name"], m["ids"]))
            shown.add((path, m["name"]))
    for path, cls, src in classes(root):
        for c, meth in sorted(named, key=lambda p: (p[0], p[1] or "")):
            if c != cls or "@Test" not in src:
                continue
            for m in members(src):
                if m["test"] and meth in (None, m["name"]) and (path, m["name"]) not in shown:
                    if meth is None:
                        print("%s  %s  (tracker names the class; --class %s lists its methods)" % (path, cls, cls))
                        break
                    print(line(path, m["first"], m["last"], cls, m["name"], m["ids"]) + "  (tracker)")
                    shown.add((path, m["name"]))


def by_class(name, root=ROOT):
    name = name.split(".")[-1]
    for path, cls, src in classes(root):
        if cls == name:
            ms = members(src)
            for m in ms:
                if m["test"]:
                    print(line(path, m["first"], m["last"], cls, m["name"], m["ids"]))
            helpers = ["%s:%d-%d" % (m["name"], m["first"], m["last"]) for m in ms if not m["test"]]
            print("%s  non-test members: %s" % (path, " ".join(helpers) or "none"))
            return
    print("no test class %s" % name, file=sys.stderr)


def uses(name, root=ROOT):
    pat = re.compile(r"\b%s\b" % re.escape(name))
    for path, cls, src in classes(root):
        if "@Test" in src and cls != name:
            n = len(pat.findall(src))
            if n:
                print("%s  %s  (%d mentions)" % (path, cls, n))


def selftest():
    sample = "\n".join([
        "package a;",                                                     # 1
        "public class testX {",                                           # 2
        "    private static final int[] K = { 1, 2 };",                   # 3
        "    /** Covers OB-12. */",                                       # 4
        "    @Test(groups = { \"a\" }, timeOut = 5)",                     # 5
        "    public void testOne(int x) throws Exception {",              # 6
        "        String s = \"}{ // not\"; char c = '}';",                # 7
        "        Runnable r = () -> { if (x > 0) { run(); } };  // MT-3", # 8
        "    }",                                                          # 9
        "",                                                               # 10
        "    private void helper() { }",                                  # 11
        "    @Test public void testTwoWordsHere() { }",                   # 12
        "}",                                                              # 13
    ])
    ms = members(sample)
    got = [(m["name"], m["test"], m["first"], m["last"], m["ids"]) for m in ms]
    want = [("K", False, 3, 3, []), ("testOne", True, 4, 9, ["MT-3", "OB-12"]), ("helper", False, 11, 11, []),
            ("testTwoWordsHere", True, 12, 12, [])]
    assert got == want, got
    assert words_of("testTheLoadAndExportDoors") == "test the load and export doors"
    print("selftest ok")


def main(argv):
    if len(argv) >= 2 and argv[1] == "--selftest":
        selftest()
    elif len(argv) == 3 and argv[1] == "--id":
        by_id(argv[2])
    elif len(argv) == 3 and argv[1] == "--class":
        by_class(argv[2])
    elif len(argv) == 3 and argv[1] == "--uses":
        uses(argv[2])
    elif len(argv) >= 2 and not argv[1].startswith("--"):
        by_words(argv[1:])
    else:
        print(__doc__, file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.stdout.reconfigure(newline="\n")
    sys.exit(main(sys.argv))
