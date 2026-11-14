#!/usr/bin/env python3
"""Outlines a source file without reading it: one line per method, constructor and type, with its line range.

    py -3 docs/tools/outline.py <Class or path> [word]     the outline, or only the members matching the word
    py -3 docs/tools/outline.py --selftest

Output: `first-last  size  signature  - first sentence of the javadoc`, nested and anonymous types indented under
their parent; a type line shows `class Name  first-last`.  The range runs from the declaration line to the closing
brace; the member's javadoc and annotations sit just above it (the source-side twin of docs/tools/claims.py).  Fields are left out, except one holding an anonymous class.
A NetBeans generated region is one line, `generated code, lines a-b`.  With a word, only members whose name,
signature or javadoc contains it (case-insensitive) show, under the types that hold them.

`<Class>` is a bare class name (or `pkg.Class`), looked up under src/ then test/; a path to a .java file works too.
Reads the file on every run (nothing committed, nothing stale); works from anywhere in the repository.
"""
import bisect
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import claims  # noqa: E402  (its scan() blanks comments and strings; its ANNOTATION strips annotations)

ROOT = claims.ROOT
GEN = re.compile(r"//GEN-BEGIN:(\w+)")
STRUCT = re.compile(r"[(){};]")
TYPE = re.compile(r"\b(class|interface|enum|record)\s+(\w+)")
NEW = re.compile(r"\bnew\s+([\w.]+(?:\s*<[^;(){}]*?>)?)\s*\(")
SENTENCE = re.compile(r"(?<=[.!?])\s+(?=[A-Z\"`(])|<p>|\s@\w")
WIDTH = 110


def squash(text):
    return re.sub(r"\(\s+", "(", re.sub(r"\s+", " ", text)).strip()


def cut(text):
    return text if len(text) <= WIDTH else text[:WIDTH - 3] + "..."


def blank_generated(src):
    """The source with each NetBeans generated region blanked (a `;` ends it), and the regions as (first, last, name)."""
    regions, lines = [], src.split("\n")
    i = 0
    while i < len(lines):
        m = GEN.search(lines[i])
        if m:
            end = next((j for j in range(i, len(lines)) if "//GEN-END:" + m.group(1) in lines[j]), None)
            if end is not None:
                regions.append((i + 1, end + 1, m.group(1)))
                for k in range(i, end + 1):
                    lines[k] = " " * len(lines[k])
                lines[end] = lines[end][:-1] + ";" if lines[end] else ";"
                i = end
        i += 1
    return "\n".join(lines), regions


def doc_text(raw):
    body = re.sub(r"^/\*+|\*+/$", "", raw.strip())
    return squash(re.sub(r"^\s*\*+ ?", "", body, flags=re.M))


def outline(src):
    """The file's outline as a list of nodes: dict(kind, sig, name, first, last, doc, kids).  kind: type, method, anon,
    field (holds an anonymous class) or generated."""
    src, regions = blank_generated(src)
    code, comments = claims.scan(src)
    codelines = code.split("\n")
    starts = [0] + [m.end() for m in re.finditer("\n", code)]
    ends = [c[1] for c in comments]
    match, stack = {}, []
    for m in STRUCT.finditer(code):
        c = m.group(0)
        if c in "({":
            stack.append(m.start())
        elif c in ")}":
            if not stack:
                raise ValueError("unbalanced %s at line %d" % (c, bisect.bisect_right(starts, m.start())))
            match[stack.pop()] = m.start()
    if stack:
        raise ValueError("unclosed bracket at line %d" % bisect.bisect_right(starts, stack[-1]))

    def line_of(pos):
        return bisect.bisect_right(starts, pos)

    def attached(first):
        """The javadoc text attached above this line."""
        edge, doc = first, ""
        for cs, ce, text in reversed(comments[:bisect.bisect_left(ends, first)]):
            if ce < edge - 1 or codelines[cs - 1].strip():
                break
            edge = cs
            if not doc and text.startswith("/**"):
                doc = doc_text(text)
        return doc

    def anons(a, b):
        out, i = [], a
        while True:
            m = NEW.search(code, i, b)
            if not m:
                return out
            q = match.get(m.end() - 1)
            q = q is not None and re.compile(r"\s*").match(code, q + 1).end()
            if q and code[q] == "{":
                out.append({"kind": "anon", "name": "new " + squash(m.group(1)), "sig": "new %s() {...}" % squash(m.group(1)),
                            "first": line_of(m.start()), "last": line_of(match[q]), "doc": "",
                            "kids": body(q + 1, match[q], False)})
                i = match[q] + 1
            else:
                i = m.end()

    def node(a, b, brace, in_enum):
        text = code[a:b]
        if not text.strip():
            return None
        head = claims.ANNOTATION.sub(" ", code[a:brace if brace is not None else b])
        before = head.split("=")[0]
        first, last = line_of(a + len(text) - len(text.lstrip())), line_of(b - 1)
        doc = attached(first)
        t = TYPE.search(before.split("(")[0])
        if t:
            return {"kind": "type", "name": t.group(2), "sig": squash(before.split("{")[0]), "first": first, "last": last,
                    "doc": doc, "kids": body(brace + 1, match[brace], t.group(1) == "enum") if brace is not None else []}
        if "(" in before and not in_enum:
            nm = re.search(r"(\w+)\s*\(", before)
            return {"kind": "method", "name": nm.group(1), "sig": squash(head.rstrip("{ \n;")), "first": first, "last": last,
                    "doc": doc, "kids": anons(brace, b) if brace is not None else []}
        kids = anons(a, b)
        if kids and not in_enum:
            nm = re.findall(r"\w+", before)
            return {"kind": "field", "name": nm[-1] if nm else "?", "sig": squash(before) + " = ...", "first": first,
                    "last": last, "doc": doc, "kids": kids}
        return None

    def body(lo, hi, enum):
        """The members of a class body between lo and hi."""
        out, start, i = [], lo, lo
        while True:
            m = STRUCT.search(code, i, hi)
            if not m:
                break
            c, p = m.group(0), m.start()
            if c in "({":
                head = claims.ANNOTATION.sub(" ", code[start:p])
                if c == "{" and "=" not in head:
                    n = node(start, match[p] + 1, p, enum and not out)
                    start = i = match[p] + 1
                    if n:
                        out.append(n)
                    enum = False if out else enum
                    continue
                i = match[p] + 1
            else:
                n = node(start, p + 1, None, enum and not out)
                start = i = p + 1
                enum = False
                if n:
                    out.append(n)
        return out

    top = body(0, len(code), False)
    for first, last, name in regions:
        holder = top
        while True:
            inner = [n for n in holder if n["kind"] == "type" and n["first"] <= first and last <= n["last"]]
            if not inner:
                break
            holder = inner[0]["kids"]
        holder.append({"kind": "generated", "name": "generated code",
                       "sig": "generated code, lines %d-%d" % (first, last), "first": first, "last": last, "doc": "",
                       "kids": []})
        holder.sort(key=lambda n: n["first"])
    return top


def matches(n, word):
    return word in (n["name"] + " " + n["sig"] + " " + n["doc"]).lower()


def show(nodes, word, depth=0, out=None):
    """The outline's lines; with a word, only matching members and the types holding them."""
    out = [] if out is None else out
    for n in nodes:
        sub = show(n["kids"], word, depth + 1, [])
        if word and not sub and not matches(n, word):
            continue
        pad = "  " * depth
        if n["kind"] in ("generated",):
            out.append("%s%d-%d  %d  %s" % (pad, n["first"], n["last"], n["last"] - n["first"] + 1, n["sig"]))
        else:
            size = n["last"] - n["first"] + 1
            sentence = cut(SENTENCE.split(n["doc"])[0]) if n["doc"] and n["doc"][0] != "@" else ""
            out.append("%s%d-%d  %d  %s%s" % (pad, n["first"], n["last"], size, cut(n["sig"]),
                                              "  - " + sentence if sentence else ""))
        out.extend(sub)
    return out


def find(name):
    """The source file for a bare class name, `pkg.Class` or a path."""
    if os.path.isfile(name):
        return name
    stem = name.replace("\\", "/").split("/")[-1]
    stem = stem[:-5] if stem.endswith(".java") else stem.split(".")[-1]
    for tree in ("src", "test"):
        for folder, _, names in sorted(os.walk(os.path.join(ROOT, tree))):
            if stem + ".java" in names:
                return os.path.join(folder, stem + ".java")
    return None


def selftest():
    sample = "\n".join([
        "package a;",                                                           # 1
        "public class X {",                                                     # 2
        "    private int f = 3;",                                               # 3
        "    /** Opens it. Then more. */",                                      # 4
        "    public <T extends Comparable<T>> List<T> open(T x) {",             # 5
        "        String s = \"}{ )\"; char c = '}'; // } {",                    # 6
        "        /* } */ Runnable r = new Runnable() {",                        # 7
        "            public void run() { go(); }",                              # 8
        "        };",                                                           # 9
        "        return null;",                                                 # 10
        "    }",                                                                # 11
        "    /** No sentence end here */",                                      # 12
        "    X() { }",                                                          # 13
        "    // <editor-fold defaultstate=\"collapsed\">//GEN-BEGIN:initComponents",  # 14
        "    private void initComponents() { {",                                # 15
        "    }// </editor-fold>//GEN-END:initComponents",                       # 16
        "    static class In { void deep() { } }",                              # 17
        "}",                                                                    # 18
    ])
    got = [(n["kind"], n["name"], n["first"], n["last"]) for n in outline(sample)[0]["kids"]]
    want = [("method", "open", 5, 11), ("method", "X", 13, 13), ("generated", "generated code", 14, 16),
            ("type", "In", 17, 17)]
    assert got == want, got
    kids = outline(sample)[0]["kids"]
    assert [(k["name"], k["first"], k["last"]) for k in kids[0]["kids"][0]["kids"]] == [("run", 8, 8)], kids[0]["kids"]
    assert kids[0]["kids"][0]["kind"] == "anon" and kids[2]["kids"] == [] and kids[3]["kids"][0]["name"] == "deep"
    lines = show(outline(sample), "")
    assert lines[1].endswith("open(T x)  - Opens it."), lines
    assert lines[4].endswith("X()  - No sentence end here"), lines
    assert lines[5] == "  14-16  3  generated code, lines 14-16", lines
    assert show(outline(sample), "runnable") == lines[:3]
    assert len(show(outline(sample), "deep")) == 3
    print("selftest ok")


def main(argv):
    if len(argv) == 2 and argv[1] == "--selftest":
        selftest()
        return 0
    if len(argv) not in (2, 3) or argv[1].startswith("--"):
        print(__doc__, file=sys.stderr)
        return 2
    path = find(argv[1])
    if not path:
        print("no source file %s" % argv[1], file=sys.stderr)
        return 1
    src = claims.read(path)
    print("%s  %d lines" % (os.path.relpath(path, ROOT).replace(os.sep, "/"), src.count("\n") + 1))
    print("\n".join(show(outline(src), argv[2].lower() if len(argv) == 3 else "")))
    return 0


if __name__ == "__main__":
    sys.stdout.reconfigure(newline="\n")
    sys.exit(main(sys.argv))
