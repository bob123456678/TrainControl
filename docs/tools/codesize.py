#!/usr/bin/env python3
"""The size and shape of the Java code at a revision, or two side by side.

Usage, from anywhere in the repository:

    py -3 docs/tools/codesize.py [REF] [--vs BASE] [--top N] [--packages]
    py -3 docs/tools/codesize.py --selftest

REF is any revision (default HEAD); WORKTREE reads the files on disk, uncommitted ones included.  With --vs, every
figure is shown for BASE and REF with the change, e.g. `codesize.py dev --vs v3_0_8`.  Reads git objects directly, so
nothing is checked out or built and a run takes a few seconds.

What it reports, for src/ and test/ separately:
  - files, lines, and those lines split into code, comment and blank (a line with any code counts as code);
  - lines per file (mean, median), the files over 1,000 and 3,000 lines, the share of all lines in the 10 largest
    files, and how few files hold half the lines: the concentration, which says whether code is piling up in a few
    classes;
  - nested classes (one top-level type per file is the rule here), with --packages the files and lines per src/
    package;
  - the N largest files (default 10);
  - the message bundle's keys, and the pins the guards hold at that revision (testNoNewCopies' copy groups and copied
    test tokens, testCommentsStayLean's long blocks), so a copy or comment trend needs no test run; "-" where that
    revision's guard held no such pin.
"""
import argparse
import os
import re
import subprocess
import sys
from statistics import median

AREAS = ("src", "test")

BUNDLE = "src/org/traincontrol/resources/messages.properties"

PINS = (
    ("copy groups allowed", "test/regression/testNoNewCopies.java", r"GROUPS_ALLOWED = (\d+);"),
    ("copied test tokens allowed", "test/regression/testNoNewCopies.java", r"TEST_COPIED_TOKENS_ALLOWED = (\d+);"),
    ("long comment blocks in src", "test/regression/testCommentsStayLean.java", r" LONG_BLOCKS_ALLOWED = (\d+);"),
    ("long comment blocks in test", "test/regression/testCommentsStayLean.java", r"TEST_LONG_BLOCKS_ALLOWED = (\d+);"),
)

TYPE = re.compile(r"^\s*(?:(?:public|protected|private|static|final|abstract|sealed|strictfp)\s+)*"
                  r"(?:class|interface|enum|@interface)\s+\w")


def git(*args, data=None):
    return subprocess.run(("git",) + args, input=data, stdout=subprocess.PIPE, check=True).stdout


def root():
    return git("rev-parse", "--show-toplevel").decode().strip()


def read_tree(ref):
    """{path: text} for every .java under src/ and test/, plus the bundle and the guard files."""
    wanted = lambda p: (p.endswith(".java") and p.split("/")[0] in AREAS) or p == BUNDLE

    if ref == "WORKTREE":
        out = {}
        base = root()

        listed = git("-C", base, "ls-files", "-z", "--cached", "--others", "--exclude-standard", "--", *AREAS)

        for path in listed.decode("utf-8").split("\0"):
            full = os.path.join(base, path)

            if path and wanted(path) and os.path.isfile(full):
                with open(full, "rb") as f:
                    out[path] = f.read().decode("utf-8", "replace")

        return out

    listing = git("ls-tree", "-r", "-z", ref, "--", *AREAS).decode("utf-8")

    entries = []

    for row in listing.split("\0"):
        if not row:
            continue

        meta, path = row.split("\t", 1)
        kind, sha = meta.split()[1:3]

        if kind == "blob" and wanted(path):
            entries.append((path, sha))

    batch = git("cat-file", "--batch", data="".join(sha + "\n" for _, sha in entries).encode())

    out = {}
    at = 0

    for path, _ in entries:
        header_end = batch.index(b"\n", at)
        size = int(batch[at:header_end].split()[2])
        start = header_end + 1

        out[path] = batch[start:start + size].decode("utf-8", "replace")
        at = start + size + 1

    return out


def lines_of(text):
    """The lines as `wc -l` counts them: split at newlines only, a last line without one still counted."""
    out = re.split(r"\r?\n", text)

    return out[:-1] if out and out[-1] == "" else out


def classify(text):
    """(code, comment, blank) line counts: a line with any code is code, one with only comment is comment."""
    code = comment = blank = 0
    in_block = False

    for line in lines_of(text):
        has_code = has_comment = False
        i, n = 0, len(line)
        quote = None

        while i < n:
            c = line[i]

            if in_block:
                has_comment = True

                if line.startswith("*/", i):
                    in_block = False
                    i += 2
                    continue
            elif quote:
                has_code = True

                if c == "\\":
                    i += 2
                    continue

                if c == quote:
                    quote = None
            elif line.startswith("//", i):
                has_comment = True
                break
            elif line.startswith("/*", i):
                in_block = True
                has_comment = True
                i += 2
                continue
            elif c in "\"'":
                quote = c
                has_code = True
            elif not c.isspace():
                has_code = True

            i += 1

        if has_code:
            code += 1
        elif has_comment:
            comment += 1
        else:
            blank += 1

    return code, comment, blank


def measure(ref, top):
    files = read_tree(ref)

    result = {"ref": ref, "areas": {}, "packages": {}, "pins": {}, "keys": None}

    for area in AREAS:
        rows = []

        for path, text in files.items():
            if not path.endswith(".java") or not path.startswith(area + "/"):
                continue

            code, comment, blank = classify(text)
            nested = sum(1 for line in lines_of(text) if TYPE.match(line) and line[:1].isspace())

            rows.append({"path": path, "lines": code + comment + blank, "code": code, "comment": comment,
                         "blank": blank, "nested": nested})

            if area == "src":
                package = os.path.dirname(path)[len("src/"):].replace("/", ".") or "(default package)"
                files_in, lines_in = result["packages"].get(package, (0, 0))
                result["packages"][package] = (files_in + 1, lines_in + code + comment + blank)

        sizes = sorted((r["lines"] for r in rows), reverse=True)
        total = sum(sizes)

        half, running = 0, 0

        for s in sizes:
            if running * 2 >= total:
                break

            running += s
            half += 1

        result["areas"][area] = {
            "files": len(rows),
            "lines": total,
            "code": sum(r["code"] for r in rows),
            "comment": sum(r["comment"] for r in rows),
            "blank": sum(r["blank"] for r in rows),
            "nested classes": sum(r["nested"] for r in rows),
            "mean lines/file": round(total / len(rows)) if rows else 0,
            "median lines/file": round(median(sizes)) if rows else 0,
            "files >= 1000 lines": sum(1 for s in sizes if s >= 1000),
            "files >= 3000 lines": sum(1 for s in sizes if s >= 3000),
            "% of lines in largest 10": round(100.0 * sum(sizes[:10]) / total, 1) if total else 0,
            "files holding half the lines": half,
            "largest": sorted(rows, key=lambda r: -r["lines"])[:top],
            "by path": {r["path"]: r["lines"] for r in rows},
        }

    for name, path, pattern in PINS:
        m = re.search(pattern, files.get(path, ""))

        result["pins"][name] = int(m.group(1)) if m else None

    if BUNDLE in files:
        result["keys"] = sum(1 for line in files[BUNDLE].splitlines()
                             if line.strip() and not line.lstrip().startswith(("#", "!")) and "=" in line)

    return result


FIGURES = ("files", "lines", "code", "comment", "blank", "nested classes", "mean lines/file",
           "median lines/file", "files >= 1000 lines", "files >= 3000 lines", "% of lines in largest 10",
           "files holding half the lines")


def number(v):
    if v is None:
        return "-"

    return f"{v:,.1f}" if isinstance(v, float) else f"{v:,}"


def change(a, b):
    if a is None or b is None:
        return ""

    d = b - a
    sign = "+" if d > 0 else ""
    text = f"{sign}{d:,.1f}" if isinstance(d, float) else f"{sign}{d:,}"

    if a and not isinstance(a, float):
        text += f" ({sign}{100.0 * d / a:.1f}%)"

    return text


def report(now, base, packages):
    def table(title, rows):
        print(f"\n{title}")

        width = max(len(r[0]) for r in rows) + 2

        for row in rows:
            if base:
                print(f"  {row[0]:<{width}}{number(row[1]):>12}{number(row[2]):>12}   {change(row[1], row[2])}")
            else:
                print(f"  {row[0]:<{width}}{number(row[2]):>12}")

    print(f"{base['ref']} -> {now['ref']}" if base else now["ref"])

    for area in AREAS:
        b = base["areas"][area] if base else {}
        n = now["areas"][area]

        table(f"{area}/", [(f, b.get(f), n[f]) for f in FIGURES])

        print(f"\n  largest in {area}/ at {now['ref']}:")

        for r in n["largest"]:
            was = b.get("by path", {}).get(r["path"]) if base else None
            grew = "" if not base else ("  new" if was is None else f"  {change(was, r['lines'])}")

            print(f"    {r['lines']:>7,}  {r['path'][len(area) + 1:]}{grew}")

        if base:
            gone = set(b["by path"]) - set(n["by path"])
            new = set(n["by path"]) - set(b["by path"])

            print(f"\n  files added {len(new)}, removed {len(gone)}")

    table("guards and bundle", [(k, base["pins"][k] if base else None, now["pins"][k]) for k in now["pins"]]
          + [("message keys (English)", base["keys"] if base else None, now["keys"])])

    if packages:
        names = sorted(set(now["packages"]) | set(base["packages"] if base else ()))

        print("\nsrc/ packages: files, lines")

        for p in names:
            nf, nl = now["packages"].get(p, (0, 0))

            if base:
                bf, bl = base["packages"].get(p, (0, 0))
                print(f"  {p:<44}{bf:>5} {bl:>8,}  ->{nf:>5} {nl:>8,}   {change(bl, nl)}")
            else:
                print(f"  {p:<44}{nf:>5} {nl:>8,}")


def selftest():
    sample = "\n".join([
        "package a;",                         # code
        "",                                   # blank
        "// a comment",                       # comment
        "/* opens",                           # comment
        "   still */ int x = 1;",             # code
        "String s = \"// not a comment\";",  # code
        "/** one line */",                    # comment
        "char c = '\\'';  // after code",     # code
        "   ",                                # blank
    ])

    got = classify(sample)

    assert got == (4, 3, 2), got

    assert len(lines_of("a\r\nb\x0cc\n")) == 2 and len(lines_of("a\nb")) == 2

    assert TYPE.match("public final class Foo") and TYPE.match("    static final class Bar extends X")
    assert not TYPE.match("    // a class of its own") and not TYPE.match("String classy = x;")

    print("selftest ok")


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("ref", nargs="?", default="HEAD")
    ap.add_argument("--vs", metavar="BASE")
    ap.add_argument("--top", type=int, default=10)
    ap.add_argument("--packages", action="store_true")
    ap.add_argument("--selftest", action="store_true")
    a = ap.parse_args()

    if a.selftest:
        selftest()
        return

    os.chdir(root())

    report(measure(a.ref, a.top), measure(a.vs, a.top) if a.vs else None, a.packages)


if __name__ == "__main__":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except AttributeError:
        pass

    main()
