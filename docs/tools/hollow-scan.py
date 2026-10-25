#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
hollow-scan.py: flag test claims that look like they test something but may not (FR-136 Phase 1b).

Adam (2026-10-19): "a sweep for tests that look like they are testing something but aren't".

A static scan of test/ that flags CANDIDATES, one shape per rule.  It runs nothing and proves nothing: each
candidate is read, and a real one is proved by mutating the production code it is about and watching the claim stay
green (docs/refactor-3.0.3.md, 1b).  Run it from anywhere; the project root defaults to the folder two above this file.

    python docs/tools/hollow-scan.py                  every candidate, grouped by file
    python docs/tools/hollow-scan.py --summary        counts per shape, confidence and folder
    python docs/tools/hollow-scan.py --json           the same findings as JSON
    python docs/tools/hollow-scan.py --shape needle --min high test/regression
    python docs/tools/hollow-scan.py --help           what each shape means

Source needles are checked against src/ the way support.SourceOf reads it: line endings as `\\n`, comments blanked
only where the claim's read blanks them (allCode, withoutComments, codeUnder, occurrences), declarations found by
signature anywhere under src/.  A file read directly (Files.readAllBytes) keeps its CRLF, as the test sees it.
"""

import argparse
import bisect
import json
import os
import re
import sys
import time
from collections import OrderedDict

# ----------------------------------------------------------------------------------------------------------------------
# The shapes
# ----------------------------------------------------------------------------------------------------------------------

SHAPES = OrderedDict([
    ("needle", (
        "a source needle that is not where it is looked for",
        "A string handed to contains / indexOf / lastIndexOf / matches / Pattern.compile / SourceOf.occurrences, or to "
        "a helper of the test's that passes it on to one of those, over text the scan can work out (SourceOf.allCode, "
        "declaration, body, fileDeclaring, codeUnder, withoutComments, declarationIn, a src file read directly, and "
        "substrings of those cut by indexOf).  Flagged when: a positive claim's needle is not in the text (red today, "
        "or never reached); a positive claim's needle is in the text only inside a comment, and the read keeps "
        "comments (so a comment satisfies it); a negative claim's needle is spelled unlike the code although a "
        "near form (whitespace, `this.`) is there; an anchor (an indexOf kept in a variable) is missing; an "
        "unbounded indexOf(needle, from) first finds the needle outside the member that holds `from` (the 3,700-line "
        "case); a matches(\".*x.*\") without (?s) over text with line breaks, which can never match.")),
    ("guard", (
        "an assert behind a guard that can skip it without failing",
        "`if (x.isEmpty()) return;`, `if (found == null) continue;`, an early return before the asserts, or asserts "
        "wrapped in `if (!x.isEmpty()) { ... }` / `if (x != null) { ... }` with no else that fails; and a negative "
        "claim over text from a helper that answers \"\" or null when it finds nothing.  When the guarded thing goes "
        "(a method renamed, a lookup that finds nothing), the claim stops running and stays green. "
        "Display guards (GraphicsEnvironment.isHeadless) are not reported: the battery runs with a display.")),
    ("loop", (
        "asserts inside a loop over something that can be empty, with no floor",
        "A for / for-each / while(m.find()) / while(it.hasNext()) loop whose body asserts, over a collection that is "
        "not a literal list, an enum's values() or a literal range, with no floor: no assert on its size or emptiness, "
        "and no counter or flag set inside the loop that is asserted after it.  An empty collection runs no assert.")),
    ("absence", (
        "a zero or absence claim with no positive control",
        "assertFalse(x.contains(..)), assertTrue(!..), `== 0`, `< 0`, assertNull, assertTrue(x.isEmpty()), "
        "assertEquals(n, 0), with no positive assert in the same method over the same reading (the same variable, "
        "call or counter).  A reading that can no longer see anything (a changed colour, a wrong key, a renamed "
        "needle) satisfies an absence claim.  Pixel counts asserted zero (a getRGB colour matcher) rank high; "
        "source negatives are reported only when the needle's spelling or the cut is in doubt.")),
    ("catch", (
        "an assert inside a catch, or a catch that swallows the failure",
        "An assert in a catch block whose try has no fail() (if nothing throws, nothing is asserted); or a catch of "
        "Throwable / Error / AssertionError / InvocationTargetException / ExecutionException - or Exception around "
        "invokeAndWait, which wraps an AssertionError - around asserts, that neither rethrows, fails nor records the "
        "error for a later assert.  A catch (Exception) around plain asserts is reported lower: the AssertionError "
        "passes, but a throw before the asserts skips them.")),
    ("nosuch", (
        "a NoSuch*Exception (or ReflectiveOperationException) catch in a test",
        "A member reached by reflection that has been renamed or moved throws NoSuchMethod/FieldException.  Caught "
        "and turned into a return, a continue, a fallback value or nothing, the claim stops running.  A catch that "
        "fails or rethrows is listed low, for the record (the inventory listed 43 near TrainControlUI names).")),
    ("wait", (
        "a wait that times out and asserts nothing, or asserts queued where they cannot fail the test",
        "A poll loop (sleep inside, a deadline or count in the condition) after which nothing asserts the polled "
        "condition; a boolean wait helper or latch.await(n, unit) whose answer is thrown away; Thread.join(ms) "
        "with no isAlive check; asserts inside SwingUtilities/EventQueue.invokeLater, a new Thread or a Timer (an "
        "AssertionError there goes to the uncaught-exception handler, not to TestNG).  Asserts in a listener lambda "
        "are listed medium: they fail the test only if the event is fired on the test's own call stack.")),
    ("noassert", (
        "a @Test method with no assert",
        "No assert*, fail, throw, expectedExceptions, nor a call to a method that asserts (one of this file's, or "
        "Class.method of another test file such as SourceOf.declaration).  A helper that only throws when its "
        "fixture is missing does not count.  \"Did not throw\" is not an outcome.")),
    ("skip", (
        "a skip whose condition can be true in the battery",
        "throw new SkipException under a condition about the fixture (a page, a session, a square or a train not "
        "found), in a @BeforeClass (the whole class), unconditionally, or @Test(enabled = false).  The battery "
        "counts a skip apart, not as a failure.  Display skips (isHeadless) are not reported.")),
    ("echo", (
        "an assert whose expected value is the test's own input",
        "A colour (Color.X or new Color(..)) the test hands to the program and then expects back, e.g. a border "
        "built with Color.BLUE whose pixel is asserted to be Color.BLUE.  It proves the colour came back, not that "
        "the program chose it; the program's own constant is the expected value.")),
])

CONFIDENCES = ["low", "medium", "high"]

# ----------------------------------------------------------------------------------------------------------------------
# Java text
# ----------------------------------------------------------------------------------------------------------------------

TOKEN = re.compile(r'"(?:\\.|[^"\\\n])*"|\'(?:\\.|[^\'\\\n])*\'|//[^\n]*|/\*.*?(?:\*/|\Z)', re.S)

NOT_NEWLINE = re.compile(r'[^\n\r]')


def without_comments(s):
    """support.SourceOf.withoutComments: comments blanked to spaces, literals and offsets kept."""

    def blank(m):
        t = m.group(0)

        return t if t[0] in '"\'' else NOT_NEWLINE.sub(' ', t)

    return TOKEN.sub(blank, s)


_STRUCTURES = {}


def structure(s):
    """Comments AND the insides of literals blanked: brackets, keywords and commas are then all code."""
    if len(s) < 4000:
        hit = _STRUCTURES.get(s)

        if hit is None:
            hit = _structure(s)

            if len(_STRUCTURES) < 200000:
                _STRUCTURES[s] = hit

        return hit

    return _structure(s)


def _structure(s):

    def blank(m):
        t = m.group(0)

        if t[0] in '"\'':
            return t[0] + ' ' * (len(t) - 2) + t[-1] if len(t) >= 2 else t

        return NOT_NEWLINE.sub(' ', t)

    return TOKEN.sub(blank, s)


def lex(s):
    """(without_comments(s), structure(s)) in one pass."""
    code = []

    st = []

    last = 0

    for m in TOKEN.finditer(s):
        a = m.start()

        if a > last:
            gap = s[last:a]
            code.append(gap)
            st.append(gap)

        t = m.group(0)

        if t[0] in '"\'':
            code.append(t)
            st.append(t[0] + ' ' * (len(t) - 2) + t[-1] if len(t) >= 2 else t)
        else:
            b = NOT_NEWLINE.sub(' ', t)
            code.append(b)
            st.append(b)

        last = m.end()

    if last < len(s):
        code.append(s[last:])
        st.append(s[last:])

    return ''.join(code), ''.join(st)


def unescape(lit):
    """The value of a Java string or char literal, quotes included in `lit`."""
    body = lit[1:-1]

    out = []

    i = 0

    while i < len(body):
        c = body[i]

        if c == '\\' and i + 1 < len(body):
            n = body[i + 1]

            if n in 'ntrbf':
                out.append({'n': '\n', 't': '\t', 'r': '\r', 'b': '\b', 'f': '\f'}[n])
                i += 2
            elif n == 'u':
                j = i + 1

                while j < len(body) and body[j] == 'u':
                    j += 1

                try:
                    out.append(chr(int(body[j:j + 4], 16)))
                except ValueError:
                    return None

                i = j + 4
            elif n in '01234567':
                j = i + 1

                while j < len(body) and j < i + 4 and body[j] in '01234567':
                    j += 1

                out.append(chr(int(body[i + 1:j], 8)))
                i = j
            else:
                out.append(n)
                i += 2
        else:
            out.append(c)
            i += 1

    return ''.join(out)


def pair_brackets(st):
    """Matching offsets for (), {} and [], each kind on its own stack; and the offsets of ; { } outside parentheses."""
    stacks = {'(': [], '{': [], '[': []}

    closer = {')': '(', '}': '{', ']': '['}

    pairs = {}

    bounds = []

    for m in re.finditer(r'[(){}\[\];]', st):
        c = m.group()

        p = m.start()

        if c == ';':
            if not stacks['(']:
                bounds.append(p)

            continue

        if c in '({[':
            if c == '{' and not stacks['(']:
                bounds.append(p)

            stacks[c].append(p)
        else:
            o = closer[c]

            if stacks[o]:
                q = stacks[o].pop()
                pairs[q] = p
                pairs[p] = q

            if c == '}' and not stacks['(']:
                bounds.append(p)

    return pairs, bounds


KEYWORDS = {'if', 'for', 'while', 'switch', 'catch', 'synchronized', 'try', 'do', 'else', 'return', 'new', 'throw',
            'finally', 'case', 'super', 'this'}

METHOD_HEADER = re.compile(r'(\w+)\s*\(([^()]*(?:\([^()]*\)[^()]*)*)\)\s*(?:throws\s+[\w.,\s]+?)?\s*$')

ASSERT_CALL = re.compile(r'\b(?:assert[A-Z]\w*|fail|assertThat)\s*\(|\bthrow\s+new\s+(?:java\.lang\.)?AssertionError\b'
                         r'|\bAssert\.\w+\s*\(')

FAILING = re.compile(ASSERT_CALL.pattern + r'|\bthrow\s+new\s+[\w.]*(?:Exception|Error)\b|\bthrow\s+\w+\s*;')

HEADLESS = re.compile(r'isHeadless\s*\(')

CALL_NAME = re.compile(r'(?:(?<![\w.])|(?<=this\.))([A-Za-z_]\w{2,})\s*\(')

GENERIC_CALLS = {'get', 'size', 'toString', 'equals', 'valueOf', 'contains', 'isEmpty', 'asList', 'getName', 'add',
                 'put', 'format', 'trim', 'length', 'stream', 'collect', 'filter', 'map', 'keySet', 'values', 'join',
                 'getKey', 'getValue', 'hashCode', 'indexOf', 'substring', 'replace', 'toLowerCase', 'apply', 'run',
                 'call', 'invoke', 'getClass', 'iterator', 'next', 'hasNext', 'containsKey', 'entrySet',
                 'singletonList', 'emptyList', 'getOrDefault', 'orElse', 'isPresent', 'append', 'startsWith',
                 'endsWith', 'split', 'getText', 'getRGB'}

QUALIFIED_CALL = re.compile(r'\b([A-Z]\w*)\s*\.\s*([a-z]\w*)\s*\(')


def calls_in(body):
    """The unqualified call names in a span, and its `Class.name` calls."""
    return set(CALL_NAME.findall(body)), {a + '.' + b for a, b in QUALIFIED_CALL.findall(body)}


class Method(object):
    __slots__ = ('name', 'params', 'header', 'name_at', 'open', 'close', 'is_test', 'annotations', 'kind', 'calls',
                 'cache')

    def __init__(self, name, params, header, name_at, open_, close, is_test, annotations, kind):
        self.name = name
        self.params = params
        self.header = header
        self.name_at = name_at
        self.open = open_
        self.close = close
        self.is_test = is_test
        self.annotations = annotations
        self.kind = kind
        self.calls = set()
        self.cache = {}


def find_methods(st, pairs, bounds):
    """Every method and constructor body: header, name and the span of its braces."""
    methods = []

    for p in bounds:
        if st[p] != '{':
            continue

        k = bisect.bisect_left(bounds, p) - 1

        b = bounds[k] if k >= 0 else -1

        header = st[b + 1:p]

        m = METHOD_HEADER.search(header)

        if not m:
            continue

        name = m.group(1)

        if name in KEYWORDS:
            continue

        before = header[:m.start()].rstrip()

        if not before or not re.search(r'[\w>\]]$', before) or \
                re.search(r'(?:\bnew|\breturn|\bthrow|[=(,?:+\-*/&|!])\s*[\w.<>\[\]]*$', before):
            # `new Foo() {` is an anonymous class; a constructor has no return type but a modifier or annotation
            if not re.search(r'(?:public|private|protected|@\w+(?:\([^)]*\))?)\s*$', before):
                continue

        if re.search(r'\bnew\s+[\w.<>]*$', before):
            continue

        close = pairs.get(p)

        if close is None:
            continue

        params = [x.strip() for x in m.group(2).split(',') if x.strip()]

        annotations = header[:m.start()]

        is_test = bool(re.search(r'@Test\b', annotations))

        kind = 'test' if is_test else 'method'

        if re.search(r'@(?:BeforeClass|BeforeMethod|BeforeTest|BeforeSuite)\b', annotations):
            kind = 'before'

        methods.append(Method(name, params, header, b + 1 + m.start(1), p, close, is_test, annotations, kind))

    return methods


class JFile(object):
    """A Java file: its text, its code (comments blanked) and its structure (literals blanked too)."""

    def __init__(self, path, rel, text):
        self.path = path
        self.rel = rel
        self.text = text
        self.code, self.st = lex(text)
        self.pairs, self.bounds = pair_brackets(self.st)
        self._methods = None
        self.line_starts = [0] + [m.end() for m in re.finditer(r'\n', text)]
        self._spans = None
        self.braces = sorted(k for k in self.pairs if self.st[k] == '{')
        self._method_opens = None
        self.asserting = set()
        self.fields = {}
        self.scan = True

    @property
    def methods(self):
        if self._methods is None:
            self._methods = find_methods(self.st, self.pairs, self.bounds)

        return self._methods

    @property
    def method_opens(self):
        if self._method_opens is None:
            self._method_opens = sorted((m.open, m.close) for m in self.methods)

        return self._method_opens

    def assignments(self):
        """name -> sorted (start, end of `name =`) for every plain assignment in the file, found once."""
        if getattr(self, '_assignments', None) is None:
            found = {}

            for mm in re.finditer(r'(?<![\w.])([A-Za-z_]\w*)\s*=(?!=)', self.st):
                found.setdefault(mm.group(1), []).append((mm.start(), mm.end()))

            self._assignments = found

        return self._assignments

    def call_sites(self):
        """name -> offsets of every unqualified call of it, found once."""
        if getattr(self, '_calls', None) is None:
            found = {}

            for mm in re.finditer(r'(?<![\w.])([A-Za-z_]\w*)\s*\(', self.st):
                found.setdefault(mm.group(1), []).append((mm.start(), mm.end()))

            self._calls = found

        return self._calls

    def in_any_method(self, at):
        return any(o < at < c for o, c in self.method_opens)

    def line(self, at):
        return bisect.bisect_right(self.line_starts, at)

    def line_text(self, at):
        n = self.line(at) - 1

        start = self.line_starts[n]

        end = self.text.find('\n', start)

        return self.text[start:end if end >= 0 else len(self.text)]

    def method_at(self, at):
        """The innermost method whose body holds `at`."""
        best = None

        for m in self.methods:
            if m.open < at < m.close and (best is None or m.open > best.open):
                best = m

        return best

    def outer_method_at(self, at):
        """The outermost method whose body holds `at` - the test method, not a lambda's anonymous class."""
        for m in self.methods:
            if m.open < at < m.close:
                return m

        return None

    def member_span(self, at):
        """The innermost method (header included) that holds `at`, as (start, end)."""
        if self._spans is None:
            spans = []

            for m in self.methods:
                k = bisect.bisect_left(self.bounds, m.open) - 1

                spans.append((self.bounds[k] + 1 if k >= 0 else 0, m.close + 1))

            self._spans = spans

        best = None

        for s, e in self._spans:
            if s <= at < e and (best is None or s > best[0]):
                best = (s, e)

        return best


# ----------------------------------------------------------------------------------------------------------------------
# src/, as support.SourceOf reads it
# ----------------------------------------------------------------------------------------------------------------------

class Source(object):
    def __init__(self, root, src):
        self.root = root
        self.src = src
        self.files = OrderedDict()
        paths = []

        for folder, _, names in os.walk(src):
            for n in names:
                if n.endswith('.java'):
                    full = os.path.join(folder, n)
                    paths.append(('src/' + os.path.relpath(full, src).replace(os.sep, '/'), full))

        paths.sort()

        for rel, full in paths:
            with open(full, 'r', encoding='utf-8', errors='replace', newline='') as f:
                self.files[rel] = f.read().replace('\r\n', '\n')

        self.code = OrderedDict((k, without_comments(v)) for k, v in self.files.items())
        self._all = None
        self._all_code = None
        self._starts = None
        self._jfiles = {}
        self._decl = {}
        self._squeezed = {}
        self._raw = {}

    def all(self):
        if self._all is None:
            self._all = '\n'.join(self.files.values())

        return self._all

    def all_code(self):
        if self._all_code is None:
            self._all_code = '\n'.join(self.code.values())

        return self._all_code

    def starts(self):
        """Where each file begins in all() / all_code(), for mapping an offset back to its file."""
        if self._starts is None:
            starts = []

            at = 0

            for k, v in self.files.items():
                starts.append((at, k))
                at += len(v) + 1

            self._starts = starts

        return self._starts

    def file_of(self, at):
        starts = self.starts()

        k = bisect.bisect_right([s for s, _ in starts], at) - 1

        return starts[k][1], at - starts[k][0]

    def jfile(self, rel):
        if rel not in self._jfiles:
            self._jfiles[rel] = JFile(rel, rel, self.files[rel])

        return self._jfiles[rel]

    def squeezed(self, key, text):
        if key not in self._squeezed:
            self._squeezed[key] = squeeze(text)

        return self._squeezed[key]

    def raw(self, rel):
        """A file under the project as a direct read sees it: line endings as they are on disk."""
        if rel not in self._raw:
            full = os.path.join(self.root, rel)

            if not os.path.isfile(full):
                self._raw[rel] = None
            else:
                with open(full, 'r', encoding='utf-8', errors='replace', newline='') as f:
                    self._raw[rel] = f.read()

        return self._raw[rel]

    def raw_code(self, rel):
        key = ('code', rel)

        if key not in self._raw:
            self._raw[key] = without_comments(self.raw(rel))

        return self._raw[key]

    def declaration(self, signature, body_only=False):
        """(file, start, end, text) of the one declaration starting with `signature`, or None."""
        key = (signature, body_only)

        if key in self._decl:
            return self._decl[key]

        signature = signature.replace('\r\n', '\n')

        found = []

        for rel, code in self.code.items():
            i = code.find(signature)

            while i >= 0:
                found.append((rel, i))
                i = code.find(signature, i + len(signature))

        result = None

        if len(found) == 1:
            rel, at = found[0]

            jf = self.jfile(rel)

            end, open_ = end_of(jf, at)

            if end is not None:
                if body_only:
                    if open_ is not None:
                        result = (rel, open_ + 1, end - 1, self.files[rel][open_ + 1:end - 1])
                else:
                    result = (rel, at, end, self.files[rel][at:end])

        self._decl[key] = result

        return result


def end_of(jf, at):
    """support.SourceOf.endOf: past the closing brace of the declaration at `at`, or past its `;`."""
    st = jf.st

    parens = 0

    i = at

    n = len(st)

    while i < n:
        c = st[i]

        if c == '(':
            parens += 1
        elif c == ')':
            parens -= 1
        elif parens <= 0 and c == ';':
            return i + 1, None
        elif parens <= 0 and c == '{':
            close = jf.pairs.get(i)

            return (close + 1 if close is not None else None), i

        i += 1

    return None, None


def squeeze(s):
    return re.sub(r'\s+', '', s.replace('this.', ''))


# ----------------------------------------------------------------------------------------------------------------------
# Small helpers over a test file's structure
# ----------------------------------------------------------------------------------------------------------------------

def args_of(jf, open_paren):
    """The spans of a call's arguments, split at top-level commas."""
    close = jf.pairs.get(open_paren)

    if close is None:
        return [], None

    st = jf.st

    parts = []

    depth = 0

    start = open_paren + 1

    for k in range(open_paren + 1, close):
        c = st[k]

        if c in '({[':
            depth += 1
        elif c in ')}]':
            depth -= 1
        elif c == ',' and depth == 0:
            parts.append((start, k))
            start = k + 1

    if st[start:close].strip():
        parts.append((start, close))

    return parts, close


def receiver_before(jf, dot):
    """The expression ending just before `dot` (a `.` of a call): a chain of names, calls and indexes."""
    st = jf.st

    i = dot - 1

    while i >= 0 and st[i] in ' \t\r\n':
        i -= 1

    while i >= 0:
        c = st[i]

        if c in ')]':
            o = jf.pairs.get(i)

            if o is None:
                break

            i = o - 1

            while i >= 0 and st[i] in ' \t\r\n':
                i -= 1

            if i >= 0 and (st[i].isalnum() or st[i] in '_$'):
                continue

            if i >= 0 and st[i] in ')]':
                continue

            break

        if c == '"':
            k = st.rfind('"', 0, i)

            i = k - 1

            break

        if c.isalnum() or c in '_$':
            while i >= 0 and (st[i].isalnum() or st[i] in '_$'):
                i -= 1

            j = i

            while j >= 0 and st[j] in ' \t\r\n':
                j -= 1

            if j >= 0 and st[j] == '.':
                i = j - 1

                while i >= 0 and st[i] in ' \t\r\n':
                    i -= 1

                continue

            if re.search(r'\bnew\s*$', st[max(0, j - 5):j + 1]):
                i = j - 3

            break

        break

    start = i + 1

    while start < dot and st[start] in ' \t\r\n':
        start += 1

    return start


def enclosing_call(jf, at, lo):
    """The innermost call whose parentheses hold `at`: (name, open_paren), or (None, None)."""
    st = jf.st

    depth = 0

    i = at - 1

    while i > lo:
        c = st[i]

        if c in ')]}':
            o = jf.pairs.get(i)

            if o is not None and o > lo:
                i = o - 1

                continue
        elif c == '{' or c == ';':
            return None, None
        elif c == '(':
            m = re.search(r'([\w.]+)\s*$', st[max(lo, i - 80):i])

            return (m.group(1) if m else ''), i

        i -= 1

    return None, None


def expr_end(st, start, limit):
    """The `;` (or `)` / `,` closing an enclosing call) that ends the expression starting at `start`."""
    depth = 0

    for k in range(start, limit):
        c = st[k]

        if c in '({[':
            depth += 1
        elif c in ')}]':
            if depth == 0:
                return k

            depth -= 1
        elif c in ';,' and depth == 0:
            return k

    return limit


def statement_after(jf, at, limit):
    """The span of the statement starting at `at`: a block to its brace, or a simple statement to its `;`."""
    st = jf.st

    i = at

    while i < limit and st[i] in ' \t\r\n':
        i += 1

    if i < limit and st[i] == '{':
        close = jf.pairs.get(i)

        return i, (close + 1 if close is not None else limit)

    m = re.match(r'(if|for|while|synchronized|switch)\s*\(', st[i:i + 20])

    if m:
        p = i + m.end() - 1

        close = jf.pairs.get(p)

        if close is None:
            return i, limit

        s, e = statement_after(jf, close + 1, limit)

        if m.group(1) == 'if':
            j = e

            while j < limit and st[j] in ' \t\r\n':
                j += 1

            if st.startswith('else', j) and not (st[j + 4:j + 5].isalnum()):
                s2, e2 = statement_after(jf, j + 4, limit)

                return i, e2

        return i, e

    k = i

    depth = 0

    while k < limit:
        c = st[k]

        if c in '({[':
            depth += 1
        elif c in ')}]':
            depth -= 1
        elif c == ';' and depth <= 0:
            return i, k + 1

        k += 1

    return i, limit


def ifs_in(jf, lo, hi):
    """Each if statement between lo and hi: (if_at, cond_span, then_span, else_span or None)."""
    st = jf.st

    out = []

    for m in re.finditer(r'\bif\s*\(', st[lo:hi]):
        at = lo + m.start()

        p = lo + m.end() - 1

        close = jf.pairs.get(p)

        if close is None:
            continue

        ts, te = statement_after(jf, close + 1, hi)

        j = te

        while j < hi and st[j] in ' \t\r\n':
            j += 1

        els = None

        if st.startswith('else', j) and not st[j + 4:j + 5].isalnum():
            els = statement_after(jf, j + 4, hi)

        out.append((at, (p + 1, close), (ts, te), els))

    return out


def loops_in(jf, lo, hi):
    """Each loop: (kind, at, header_span, body_span).  kind is for / foreach / while / do."""
    st = jf.st

    out = []

    for m in re.finditer(r'\b(for|while)\s*\(', st[lo:hi]):
        at = lo + m.start()

        p = lo + m.end() - 1

        close = jf.pairs.get(p)

        if close is None:
            continue

        kind = m.group(1)

        if kind == 'while':
            # the tail of a do-while: `} while (cond);`
            j = at - 1

            while j > lo and st[j] in ' \t\r\n':
                j -= 1

            if st[j] == '}':
                o = jf.pairs.get(j)

                if o is not None and re.search(r'\bdo\s*$', st[max(lo, o - 10):o]):
                    out.append(('do', o, (p + 1, close), (o, j + 1)))

                    continue

        bs, be = statement_after(jf, close + 1, hi)

        if kind == 'for' and re.search(r'^[^;]*:', st[p + 1:close]) and ';' not in st[p + 1:close]:
            kind = 'foreach'

        out.append((kind, at, (p + 1, close), (bs, be)))

    return out


def asserts_in(jf, lo, hi):
    return [lo + m.start() for m in ASSERT_CALL.finditer(jf.st[lo:hi])]


def in_lambda_or_inner(jf, method, at):
    """True when `at` sits in a lambda body or an anonymous class inside `method` (so a return there is not the
    test's)."""
    st = jf.st

    lo = bisect.bisect_right(jf.braces, method.open)

    hi = bisect.bisect_left(jf.braces, at)

    for p in jf.braces[lo:hi]:
        close = jf.pairs.get(p)

        if close is None or close < at:
            continue

        head = st[max(method.open, p - 120):p].rstrip()

        if head.endswith('->') or re.search(r'\bnew\s+[\w.<>]+\s*\([^;{}]*\)\s*$', head):
            return True

    return False


# ----------------------------------------------------------------------------------------------------------------------
# The scan
# ----------------------------------------------------------------------------------------------------------------------

class Hay(object):
    """Text a needle is looked for in: the text as read, its code (comments blanked), and where it came from."""

    def __init__(self, text, code, label, rel=None, base=None, blanked=False, exact=True):
        self.text = text
        self.code = code
        self.label = label
        self.rel = rel
        self.base = base
        self.blanked = blanked
        self.exact = exact
        self.raw = False


class Scanner(object):
    def __init__(self, root, src_dir, test_dir):
        self.root = root
        self.test_dir = test_dir
        self.source = Source(root, src_dir)
        self.findings = []
        self.keys = []
        self.files = []
        self.asserting = set()
        self.support_asserting = set()

    # ---- loading and the asserting-helper closure -------------------------------------------------------------------

    def load(self, only):
        paths = []

        for folder, _, names in os.walk(self.test_dir):
            for n in names:
                if n.endswith('.java'):
                    paths.append(os.path.join(folder, n))

        paths.sort()

        for full in paths:
            rel = os.path.relpath(full, self.root).replace(os.sep, '/')

            with open(full, 'r', encoding='utf-8', errors='replace', newline='') as f:
                text = f.read().replace('\r\n', '\n')

            jf = JFile(full, rel, text)

            jf.scan = (not only) or any(rel.startswith(o) or full.startswith(o) for o in only)

            self.files.append(jf)

        # A method asserts when its body asserts or calls one of its own file's (or test/support's) asserting methods.
        for jf in self.files:
            jf.asserting = set()

        # Only an assert, fail or AssertionError makes a helper asserting: a helper that throws an exception when its
        # fixture is missing is set-up, not a claim.  Calls are matched unqualified within a file, and as
        # `Class.name` across files (SourceOf.declaration, another test class's static helper).
        for jf in self.files:
            jf.cls = os.path.splitext(os.path.basename(jf.rel))[0]

            for m in jf.methods:
                body = jf.st[m.open:m.close]

                plain, qualified = calls_in(body)

                m.calls = (plain - {m.name}, qualified)

                if ASSERT_CALL.search(body):
                    jf.asserting.add(m.name)
                    self.support_asserting.add(jf.cls + '.' + m.name)

        changed = True

        while changed:
            changed = False

            for jf in self.files:
                for m in jf.methods:
                    if m.name in jf.asserting:
                        continue

                    if m.calls[0] & jf.asserting or m.calls[1] & self.support_asserting:
                        jf.asserting.add(m.name)
                        self.support_asserting.add(jf.cls + '.' + m.name)

                        changed = True

    def add(self, jf, at, method, shape, confidence, note, key=None):
        self.keys.append(key if key is not None else note)

        self.findings.append(OrderedDict([
            ("file", jf.rel),
            ("line", jf.line(at)),
            ("method", method.name if method else ""),
            ("shape", shape),
            ("confidence", confidence),
            ("excerpt", jf.line_text(at).strip()[:160]),
            ("note", note),
        ]))

    def calls_asserting(self, jf, lo, hi):
        """Whether the span asserts, directly or through a helper known to assert."""
        body = jf.st[lo:hi]

        if ASSERT_CALL.search(body):
            return True

        plain, qualified = calls_in(body)

        return bool(plain & jf.asserting or qualified & self.support_asserting)

    def run(self, only):
        self.load(only)

        for jf in self.files:
            if not jf.scan:
                continue

            for method in jf.methods:
                if method.is_test:
                    self.scan_noassert(jf, method)
                    self.scan_guards(jf, method)
                    self.scan_echo(jf, method)

                if method.is_test or method.name in jf.asserting:
                    self.scan_loops(jf, method)
                    self.scan_absence(jf, method)

            self.scan_catches(jf)
            self.scan_waits(jf)
            self.scan_skips(jf)
            self.scan_needles(jf)

        # one finding per file, line, shape and key; the same needle judged under several table rows or call sites
        # is one finding that says how many
        seen = OrderedDict()

        for f, k in zip(self.findings, self.keys):
            key = (f["file"], f["line"], f["shape"], k)

            if key in seen:
                seen[key][1] += 1
            else:
                seen[key] = [f, 0]

        unique = []

        for f, extra in seen.values():
            if extra:
                f["note"] += " [and %d more row(s) or call(s) like it]" % extra

            unique.append(f)

        self.findings = sorted(unique, key=lambda f: (f["file"], f["line"], f["shape"]))

    # ---- noassert ---------------------------------------------------------------------------------------------------

    def scan_noassert(self, jf, m):
        if 'expectedExceptions' in jf.code[m.name_at - len(m.annotations):m.name_at] or \
                'expectedExceptions' in m.annotations:
            return

        if re.search(r'enabled\s*=\s*false', m.annotations):
            return

        body = jf.st[m.open:m.close]

        if FAILING.search(body):
            return

        if self.calls_asserting(jf, m.open, m.close):
            return

        calls = set(re.findall(r'\b([a-z]\w*)\s*\(', body)) - KEYWORDS

        own = {x.name for x in jf.methods}

        local = sorted(c for c in calls if c in own)

        self.add(jf, m.name_at, m, "noassert", "high" if not local else "medium",
                 "no assert, fail or throw" + (" (calls " + ", ".join(local[:4]) + ", none of which asserts)"
                                                if local else ""))

    # ---- guard ------------------------------------------------------------------------------------------------------

    PRESENCE = re.compile(r'isEmpty\s*\(\)|==\s*null|!=\s*null|<\s*0|>=\s*0|>\s*0|==\s*-1|!=\s*-1|\.exists\s*\(\)|'
                          r'isDirectory\s*\(\)|isFile\s*\(\)|\.contains\s*\(|\.size\s*\(\)\s*(?:==|<|<=)\s*[01]\b|'
                          r'\bfound\b|isPresent\s*\(\)|\.length\s*(?:==|<|<=)\s*[01]\b|\binstanceof\b|'
                          r'containsKey\s*\(|\.matches\s*\(|\.find\s*\(\)')

    def scan_guards(self, jf, m):
        st = jf.st

        all_asserts = asserts_in(jf, m.open, m.close)

        if not all_asserts:
            return

        loops = loops_in(jf, m.open, m.close)

        for if_at, (cs, ce), (ts, te), els in ifs_in(jf, m.open, m.close):
            cond = jf.code[cs:ce]

            if HEADLESS.search(cond):
                continue

            then = st[ts:te]

            if 'SkipException' in then:
                continue

            if in_lambda_or_inner(jf, m, if_at):
                continue

            word = bail_word(then)

            if word and not FAILING.search(then):

                if word == 'return':
                    after = [a for a in all_asserts if a > te]

                    if not after:
                        continue

                    presence = bool(self.PRESENCE.search(cond))

                    before = [a for a in all_asserts if a < if_at]

                    conf = "high" if presence and not before else "medium" if presence else "low"

                    self.add(jf, if_at, m, "guard", conf,
                             "`if (%s) return` skips %d assert(s) after it%s" % (
                                 squash(cond, 60), len(after), "" if before else ", all of the method's"))
                else:
                    loop = None

                    for kind, lat, hs, (bs, be) in loops:
                        if bs <= if_at < be and (loop is None or bs > loop[3][0]):
                            loop = (kind, lat, hs, (bs, be))

                    if loop is None:
                        continue

                    after = [a for a in all_asserts if te <= a < loop[3][1]]

                    if not after:
                        continue

                    if self.loop_has_floor(jf, m, loop):
                        continue

                    presence = bool(self.PRESENCE.search(cond))

                    self.add(jf, if_at, m, "guard", "medium" if presence else "low",
                             "`if (%s) continue` can skip every assert in the loop, and nothing after it counts the "
                             "ones that ran" % squash(cond, 60))

                continue

            # asserts wrapped in a presence test with no failing else
            if not self.PRESENCE.search(cond):
                continue

            inner = asserts_in(jf, ts, te)

            if not inner:
                continue

            inner_text = jf.st[ts:te]

            if not re.search(r'\bassert(?!.*\bfail\b)', inner_text):
                continue

            if all(re.match(r'(fail|throw)', st[a:a + 5]) for a in inner):
                continue

            # `if (x) fail(..)`-style: the whole then-branch is the failure
            if re.match(r'\s*\{?\s*(fail|throw|assert\w*)\s*\(', then) and len(inner) == 1 and \
                    re.match(r'\s*\{?\s*fail', then):
                continue

            if els and FAILING.search(st[els[0]:els[1]]):
                continue

            subject = guard_subject(cond)

            if subject and any(re.search(r'\b' + re.escape(subject) + r'\b', st[a:a + 200]) for a in all_asserts
                               if a < if_at):
                continue

            # inside a loop: a filter per item, judged by the loop's floor
            loop = None

            for kind, lat, hs, (bs, be) in loops:
                if bs <= if_at < be and (loop is None or bs > loop[3][0]):
                    loop = (kind, lat, hs, (bs, be))

            if loop is not None and self.loop_has_floor(jf, m, loop):
                continue

            outside = [a for a in all_asserts if not (ts <= a < te)]

            conf = "high" if not outside else "medium"

            if loop is not None:
                conf = "low" if conf == "medium" else "medium"

            lookup = self.empty_on_miss_source(jf, m, subject, if_at)

            if lookup:
                conf = "high"

            self.add(jf, if_at, m, "guard", conf,
                     "%d assert(s) run only `if (%s)`, with no else that fails%s%s" % (
                         len(inner), squash(cond, 60), "" if outside else "; nothing else in the method asserts",
                         "; `%s` comes from %s(), which answers empty on a miss" % (subject, lookup) if lookup
                         else ""))

        self.scan_empty_reads(jf, m)

    def empty_on_miss(self, jf):
        """Helpers of the file that answer "" or null when what they look for is not there."""
        if not hasattr(jf, '_empty'):
            names = set()

            for h in jf.methods:
                body = jf.st[h.open:h.close]

                if re.search(r'\breturn\s*(?:""|null)\s*;', body) and re.search(r'indexOf\s*\(|\.find\s*\(|'
                                                                                  r'getDeclared\w*\s*\(', body):
                    names.add(h.name)

            jf._empty = names

        return jf._empty

    def empty_on_miss_source(self, jf, m, var, at):
        """The empty-on-miss helper a variable was assigned from, or None."""
        if not var or not re.match(r'^\w+$', var):
            return None

        span = self.assignment(jf, m, var, at)

        if span is None:
            return None

        call = re.match(r'\s*(\w+)\s*\(', jf.st[span[0]:span[1]])

        if call and call.group(1) in self.empty_on_miss(jf):
            return call.group(1)

        return None

    def scan_empty_reads(self, jf, m):
        """A negative claim over text from a lookup that answers "" on a miss: a renamed member makes it pass."""
        helpers = self.empty_on_miss(jf)

        if not helpers:
            return

        code = jf.code

        for mm in re.finditer(r'\bassert(False|True)\s*\(\s*(!?)\s*(\w+)(?:\s*\([^;]*?\))?\s*\.\s*contains\s*\(',
                              jf.st[m.open:m.close]):
            negative = (mm.group(1) == 'False') != (mm.group(2) == '!')

            if not negative:
                continue

            at = m.open + mm.start()

            name = mm.group(3)

            source = name if name in helpers else self.empty_on_miss_source(jf, m, name, at)

            if not source:
                continue

            # guarded by a positive over the same read (assertFalse(x.isEmpty()), assertTrue(x.contains(..)))
            if re.search(r'\bassert\w*\s*\([^;]*\b' + re.escape(name) + r'\b[^;]*(?:isEmpty|length|contains)',
                         code[m.open:at]) and name != source:
                continue

            self.add(jf, at, m, "guard", "high",
                     "a negative claim over %s(), which answers empty when it finds nothing: a renamed or moved "
                     "member makes it pass" % source)

    # ---- loop -------------------------------------------------------------------------------------------------------

    NONEMPTY = re.compile(r'^\s*(?:new\s+[\w.<>]+\s*\[\s*\]\s*\{|\{|Arrays\.asList\s*\(|'
                          r'java\.util\.Arrays\.asList\s*\(|List\.of\s*\(|Set\.of\s*\(|Collections\.singleton|'
                          r'EnumSet\.allOf\s*\(|'
                          r'[A-Z][\w.]*\.values\s*\(\)\s*$|"[^"]*"\s*\.toCharArray)')

    def collection_is_literal(self, jf, m, expr, at):
        expr = expr.strip()

        if self.NONEMPTY.search(expr):
            return True

        if re.match(r'^[\w.]*[A-Z][A-Z0-9_]+$', expr) or re.match(r'^\d+$', expr):
            return True

        v = re.match(r'^(\w+)$', expr)

        if v:
            init = self.assignment(jf, m, v.group(1), at)

            if init is not None and self.NONEMPTY.search(jf.code[init[0]:init[1]]):
                return True

        return False

    def loop_collection(self, jf, kind, header):
        h = jf.code[header[0]:header[1]]

        if kind == 'foreach':
            return h.split(':', 1)[1].strip()

        if kind == 'for':
            parts = h.split(';')

            if len(parts) >= 2:
                cond = parts[1]

                mm = re.search(r'<=?\s*(.+?)\s*$', cond)

                if mm:
                    bound = mm.group(1)

                    if not re.search(r'\.(?:size\s*\(\)|length\b|getItemCount|getRowCount|getComponentCount|'
                                     r'getMenuComponentCount|getTabCount)', bound):
                        # a count of the test's own (pixels, steps): not a collection that can come back empty
                        return None

                    bound = re.sub(r'\.(?:size\s*\(\)|length(?:\s*\(\))?|getItemCount\s*\(\)|getRowCount\s*\(\)|'
                                   r'getComponentCount\s*\(\)|getMenuComponentCount\s*\(\)|getTabCount\s*\(\))\s*'
                                   r'(?:[-+]\s*\d+)?$', '', bound)

                    return bound

            return None

        if kind == 'while':
            mm = re.match(r'\s*(\w+)\s*\.\s*(find|hasNext|next)\s*\(\)\s*$', h)

            if mm:
                return mm.group(1)

        return None

    def loop_has_floor(self, jf, m, loop):
        kind, at, header, (bs, be) = loop

        st = jf.st

        coll = self.loop_collection(jf, kind, header)

        all_asserts = asserts_in(jf, m.open, m.close)

        if coll:
            root = re.match(r'^([\w.]+?)(?:\s*\(|$|\.)', coll)

            name = re.escape(coll.strip())

            for a in all_asserts:
                close = st.find(';', a)

                seg = jf.code[a:close if close > 0 else a + 300]

                if re.search(name + r'\s*\.\s*(?:size|isEmpty|length)\b', seg) or \
                        re.search(r'assertEquals\s*\(\s*' + name + r'\s*[,)]', seg) or \
                        re.search(r'(?:assertNotEquals|assertEquals)\s*\([^;]*,\s*' + name + r'\s*[,)]', seg):
                    return True

        # a counter, list or flag written inside the loop and asserted after it
        written = set(re.findall(r'\b(\w+)\s*(?:\+\+|--|\+=|-=|\|=|&=)', st[bs:be]))
        written |= set(re.findall(r'(?:\+\+|--)\s*(\w+)\b', st[bs:be]))
        written |= set(re.findall(r'\b(\w+)\s*\.\s*(?:add|addAll|put|append|set|incrementAndGet|getAndIncrement|'
                                  r'offer|push)\s*\(', st[bs:be]))
        written |= set(re.findall(r'\b(\w+)\s*(?:\[[^\]]*\])?\s*=(?!=)', st[bs:be]))

        declared_inside = set(re.findall(r'\b(?:[\w.<>\[\]]+)\s+(\w+)\s*=(?!=)', st[bs:be]))

        written -= declared_inside

        written -= {'i', 'j', 'k'} - written

        if not written:
            return False

        after = [a for a in all_asserts if a >= be]

        for a in after:
            close = st.find(';', a)

            seg = st[a:close if close > 0 else a + 300]

            for w in written:
                if re.search(r'\b' + re.escape(w) + r'\b', seg):
                    return True

        # a helper that asserts, called after the loop with the counter
        for w in written:
            for c in re.finditer(r'\b(\w+)\s*\([^;]*\b' + re.escape(w) + r'\b', st[be:m.close]):
                if c.group(1) in jf.asserting or c.group(1) in self.support_asserting:
                    return True

        return False

    def scan_loops(self, jf, m):
        st = jf.st

        for loop in loops_in(jf, m.open, m.close):
            kind, at, header, (bs, be) = loop

            if kind == 'do':
                continue

            body = st[bs:be]

            if not ASSERT_CALL.search(body):
                continue

            hdr = jf.code[header[0]:header[1]]

            # poll loops belong to the wait shape
            if re.search(r'currentTimeMillis|nanoTime|deadline|until|tries|attempt', hdr, re.I) or \
                    (re.search(r'\bsleep\s*\(', body) and kind != 'foreach'):
                continue

            # asserts that only fail on a miss (`if (bad) fail(..)`) still need the loop to run, so they count
            coll = self.loop_collection(jf, kind, header)

            if kind == 'for' and coll is None:
                continue

            if kind == 'while' and coll is None:
                continue

            if coll is not None and self.collection_is_literal(jf, m, coll, at):
                continue

            if kind == 'for' and coll is not None and re.match(r'^\s*(?:\d+|[A-Z][A-Z0-9_.]*)\s*$', coll):
                continue

            if self.loop_has_floor(jf, m, loop):
                continue

            conf = "medium"

            if kind == 'while' or (coll and re.search(r'filter|collect|split|find|matcher|getRGB|keySet|values\(\)',
                                                      coll)):
                conf = "high" if kind == 'while' else "medium"

            if not m.is_test:
                conf = "low"

            self.add(jf, at, m, "loop", conf,
                     "asserts inside a loop over `%s`, with no floor before or after" % squash(coll or hdr, 60))

    # ---- absence ----------------------------------------------------------------------------------------------------

    def scan_absence(self, jf, m):
        st = jf.st

        code = jf.code

        asserts = []

        for mm in re.finditer(r'\b(assert\w+)\s*\(', st[m.open:m.close]):
            at = m.open + mm.start()

            p = m.open + mm.end() - 1

            parts, close = args_of(jf, p)

            if close is None or not parts:
                continue

            name = mm.group(1)

            first = code[parts[0][0]:parts[0][1]].strip()

            second = code[parts[1][0]:parts[1][1]].strip() if len(parts) > 1 else None

            kind, subject = classify_assert(name, first, second)

            asserts.append((at, close, name, kind, subject, first))

        if not asserts:
            return

        positives = [a for a in asserts if a[3] == 'positive']

        uses_pixels = bool(re.search(r'getRGB\s*\(', st[m.open:m.close]))

        counters = set(re.findall(r'\b(?:int|long)\s+(\w+)\s*=\s*0\b', st[m.open:m.close]))
        counters |= {c for x in re.findall(r'\bint\s+((?:\w+\s*=\s*0\s*,\s*)+\w+\s*=\s*0)', st[m.open:m.close])
                     for c in re.findall(r'(\w+)\s*=\s*0', x)}

        for at, close, name, kind, subject, first in asserts:
            if kind != 'absence' or not subject:
                continue

            key = subject_key(subject)

            if not key:
                continue

            # a source read: the needle shape judges it
            if re.search(r'SourceOf|allCode|codeOf|declaration|\bsource\b|\bcode\b|\bbody\b', first):
                continue

            is_counter = key in counters

            # the reading: the subject's name, and the calls that compute it when it is a local variable
            keys = {key}

            if not is_counter and re.match(r'^\w+$', key):
                span = self.assignment(jf, m, key, at)

                if span is not None:
                    init = jf.st[span[0]:span[1]]

                    keys |= {n for n in CALL_NAME.findall(init) if n not in GENERIC_CALLS}
                    keys |= {b for a, b in QUALIFIED_CALL.findall(init) if b not in GENERIC_CALLS}
                    keys |= {n for n in re.findall(r'\.\s*([a-z]\w*)\s*\(', init) if n not in GENERIC_CALLS}

            pattern = re.compile(r'\b(?:' + '|'.join(re.escape(k) for k in keys) + r')\b')

            if not is_counter:
                if any(pattern.search(code[pa[0]:pa[1]]) for pa in positives):
                    continue

                # a positive over the same reading anywhere in the class is near enough
                if any(pattern.search(t) for p0, t in positive_asserts(jf) if not m.open < p0 < m.close):
                    continue

            if is_counter:
                # any positive count beside it is a control for a counter
                if any(re.search(r'>\s*0|>=\s*[1-9]|assertNotEquals', code[pa[0]:pa[1]]) and
                       any(re.search(r'\b' + re.escape(c) + r'\b', code[pa[0]:pa[1]]) for c in counters)
                       for pa in positives):
                    continue

            if is_counter and uses_pixels:
                conf = "high"
                why = "a pixel count asserted zero with no count beside it that sees the colour"
            elif is_counter:
                conf = "medium"
                why = "a count asserted zero with no positive count beside it"
            elif name in ('assertNull',) or re.match(r'^[!\w.]+$', subject) and kind == 'absence' and \
                    name in ('assertFalse', 'assertTrue') and '(' not in subject:
                conf = "low"
                why = "an absence with no positive over `%s` in the method" % key
            else:
                conf = "low" if name in ('assertFalse', 'assertNull') else "medium"
                why = "an absence with no positive over `%s` in the method" % key

            self.add(jf, at, m, "absence", conf, why)

    # ---- echo -------------------------------------------------------------------------------------------------------

    COLOUR = re.compile(r'(?:java\.awt\.)?Color\.[A-Za-z_]+\b(?!\s*\()|new\s+(?:java\.awt\.)?Color\s*\([^()]*\)')

    def scan_echo(self, jf, m):
        code = jf.code

        st = jf.st

        given = {}

        asserted = []

        for c in self.COLOUR.finditer(code[m.open:m.close]):
            at = m.open + c.start()

            norm = re.sub(r'\s+|java\.awt\.', '', c.group(0))

            name, p = enclosing_call(jf, at, m.open)

            if name is None:
                continue

            outer = name

            # walk out to the outermost call of the statement, to see whether it is an assert
            q = p

            while q is not None:
                n2, p2 = enclosing_call(jf, q, m.open)

                if n2 is None:
                    break

                outer, q = n2, p2

            if re.match(r'assert', outer) or re.match(r'assert', name):
                asserted.append((at, norm))
            elif not re.search(r'(?:^|\.)(?:setColor|fill\w*|draw\w*|setBackground|setForeground|setPaint|clearRect|'
                               r'equals|getRGB|Color|put|add|asList|of)$', name):
                given.setdefault(norm, at)

        for at, norm in asserted:
            if norm in given and given[norm] < at:
                seg = code[at - 200:at + 80]

                conf = "high" if 'getRGB' in seg else "medium"

                self.add(jf, at, m, "echo", conf,
                         "expects %s, the colour the test itself handed in at line %d" % (norm, jf.line(given[norm])))

    # ---- catch and nosuch -------------------------------------------------------------------------------------------

    def scan_catches(self, jf):
        st = jf.st

        code = jf.code

        for t in re.finditer(r'\btry\s*(\([^{]*?\))?\s*\{', st):
            open_ = t.end() - 1

            close = jf.pairs.get(open_)

            if close is None:
                continue

            try_body = (open_ + 1, close)

            j = close + 1

            clauses = []

            while True:
                mm = re.match(r'\s*catch\s*\(', st[j:j + 200])

                if not mm:
                    break

                p = j + mm.end() - 1

                pc = jf.pairs.get(p)

                if pc is None:
                    break

                bm = re.match(r'\s*\{', st[pc + 1:pc + 50])

                if not bm:
                    break

                bo = pc + 1 + bm.end() - 1

                bc = jf.pairs.get(bo)

                if bc is None:
                    break

                clauses.append((j + mm.start() + len(mm.group(0)) - len(mm.group(0).lstrip()), code[p + 1:pc],
                                (bo + 1, bc)))

                j = bc + 1

            if not clauses:
                continue

            method = jf.outer_method_at(t.start())

            if method is None:
                continue

            try_text = st[try_body[0]:try_body[1]]

            try_fails = bool(re.search(r'\bfail\s*\(|throw\s+new\s+(?:java\.lang\.)?AssertionError', try_text))

            try_asserts = self.calls_asserting(jf, try_body[0], try_body[1])

            has_invoke_and_wait = bool(re.search(r'invokeAndWait\s*\(', try_text))

            for c_at, types, (cb, ce) in clauses:
                types_clean = re.sub(r'\s+\w+\s*$', '', types.strip())

                type_names = [x.strip().split('.')[-1] for x in types_clean.split('|')]

                catch_body = st[cb:ce]

                catch_at = c_at

                if any(re.match(r'NoSuch\w*Exception$|ReflectiveOperationException$', x) for x in type_names):
                    self.nosuch(jf, method, catch_at, type_names, catch_body, cb, ce, try_text)

                # (a) asserts in the catch with no fail in the try.  A catch whose only assert is a fail() is the
                # failure path of the try (a timeout, an unexpected throw), not a claim that waits on a throw.
                claims_in_catch = re.search(r'\bassert(?!True\s*\(\s*false\b)\w*\s*\(', catch_body)

                if claims_in_catch and not re.search(r'\bfail\s*\(', catch_body) and not try_fails and \
                        (method.is_test or method.name in jf.asserting):
                    recorded = self.records_for_later(jf, method, catch_body, ce)

                    if not recorded:
                        self.add(jf, catch_at, method, "catch", "high" if method.is_test else "medium",
                                 "asserts inside `catch (%s)` and the try has no fail(): if nothing throws, nothing "
                                 "is asserted" % squash(types_clean, 50))

                # (b) a catch that swallows
                if not try_asserts and not (has_invoke_and_wait and self.calls_asserting(jf, try_body[0],
                                                                                          try_body[1])):
                    continue

                if re.search(r'\bthrow\b|\bfail\s*\(|\bassert\w*\s*\(', catch_body):
                    continue

                if self.records_for_later(jf, method, catch_body, ce):
                    continue

                wide = {'Throwable', 'Error', 'AssertionError', 'InvocationTargetException', 'ExecutionException'}

                if any(x in wide for x in type_names):
                    conf = "high"
                elif any(x in ('Exception', 'RuntimeException') for x in type_names):
                    if has_invoke_and_wait:
                        conf = "high"
                    elif try_fails and not re.search(r'\bassert\w*\s*\(', try_text):
                        # try { op(); fail(); } catch (Exception expected): the AssertionError passes the catch
                        continue
                    else:
                        conf = "medium"
                else:
                    continue

                if any(re.match(r'NoSuch\w*Exception$|ReflectiveOperationException$', x) for x in type_names):
                    continue

                self.add(jf, catch_at, method, "catch", conf,
                         "`catch (%s)` around asserts neither rethrows, fails nor records the error%s" % (
                             squash(types_clean, 50),
                             " (invokeAndWait wraps an AssertionError in InvocationTargetException)"
                             if has_invoke_and_wait and conf == "high" else ""))

    def records_for_later(self, jf, method, catch_body, catch_end):
        """True when the catch keeps the error (or a flag) in a variable that an assert after it reads."""
        names = set(re.findall(r'\b(\w+)\s*(?:\[[^\]]*\])?\s*=(?!=)', catch_body))
        names |= set(re.findall(r'\b(\w+)\s*\.\s*(?:add|set|put|append|offer|incrementAndGet)\s*\(', catch_body))

        if not names:
            return False

        rest = jf.st[catch_end:method.close]

        for a in ASSERT_CALL.finditer(rest):
            seg = rest[a.start():rest.find(';', a.start()) + 1]

            if any(re.search(r'\b' + re.escape(n) + r'\b', seg) for n in names):
                return True

        # or a helper call that asserts on it
        for n in names:
            for c in re.finditer(r'\b(\w+)\s*\([^;]*\b' + re.escape(n) + r'\b', rest):
                if c.group(1) in jf.asserting or c.group(1) in self.support_asserting:
                    return True

            if re.search(r'\bif\s*\([^)]*\b' + re.escape(n) + r'\b[^)]*\)\s*\{?\s*(?:throw|fail)', rest):
                return True

            if re.search(r'\breturn\b[^;]*\b' + re.escape(n) + r'\b', rest):
                return True

        return False

    def nosuch(self, jf, method, at, types, body, cb, ce, try_text):
        text = body.strip()

        if re.search(r'\bfail\s*\(|throw\s+new\s+(?:java\.lang\.)?AssertionError|\bassert\w*\s*\(', text):
            conf, why = "low", "fails on a missing member"
        elif re.search(r'\bthrow\b', text):
            conf, why = "low", "rethrows (an error, not a failure, but loud)"
        elif not text:
            conf, why = "high", "empty: a missing member is swallowed"
        elif re.match(r'^(?:return|continue|break)\b', text) or re.search(r'^\s*(?:return|continue|break)\b[^;]*;\s*$',
                                                                           text):
            conf, why = "high", "turns a missing member into `%s`" % squash(text, 40)
        else:
            conf, why = "medium", "carries on after a missing member: `%s`" % squash(text, 50)

        self.add(jf, at, method, "nosuch", conf, "catch (%s) %s" % (" | ".join(types), why))

    # ---- wait -------------------------------------------------------------------------------------------------------

    def scan_waits(self, jf):
        st = jf.st

        code = jf.code

        # boolean wait helpers of this file
        waiters = set()

        for m in jf.methods:
            if re.search(r'\bboolean\s+$', jf.st[max(0, m.name_at - 40):m.name_at]):
                body = st[m.open:m.close]

                if (re.search(r'\bsleep\s*\(|\bawait\s*\(|\bwait\s*\(', body) or
                        re.match(r'(wait|await|poll|until|settle)', m.name, re.I)) and not FAILING.search(body):
                    waiters.add(m.name)

        for m in jf.methods:
            if not (m.is_test or m.name in jf.asserting):
                continue

            # (a) poll loops
            for kind, at, header, (bs, be) in loops_in(jf, m.open, m.close):
                hdr = code[header[0]:header[1]]

                body = st[bs:be]

                timed = re.search(r'currentTimeMillis|nanoTime|deadline|until|\btries\b|attempt|\bwaited\b|timeout',
                                  hdr, re.I)

                sleeps = re.search(r'\bsleep\s*\(|invokeAndWait\s*\(\s*\(\)\s*->\s*\{\s*\}|waitForIdle|realSync|'
                                   r'\bwait\s*\(\s*\d', body)

                if not (timed and sleeps) or kind == 'foreach':
                    continue

                if jf.outer_method_at(at) is not m:
                    continue

                if FAILING.search(body) and re.search(r'deadline|currentTimeMillis|nanoTime|timeout', body, re.I):
                    continue

                idents = set(re.findall(r'\b([a-zA-Z_]\w*)\b', hdr)) - {
                    'System', 'currentTimeMillis', 'nanoTime', 'deadline', 'until', 'while', 'for', 'int', 'long',
                    'i', 'tries', 'attempt', 'attempts', 'timeout', 'true', 'false', 'null', 'start', 'end', 'now',
                    'TimeUnit', 'MILLISECONDS', 'SECONDS', 'limit', 'waited'}

                idents = {x for x in idents if not re.match(r'^[A-Z][A-Z0-9_]+$', x)}

                clock = set()

                for x in idents:
                    span = self.assignment(jf, m, x, at + 1)

                    if span is not None and re.search(r'currentTimeMillis|nanoTime', jf.st[span[0]:span[1]]):
                        clock.add(x)

                idents -= clock

                after = asserts_in(jf, be, m.close)

                ok = False

                for a in after[:6]:
                    lead = max(st.rfind(';', be, a), st.rfind('{', be, a), st.rfind('}', be, a), be)

                    seg = code[lead:st.find(';', a) + 1]

                    if any(re.search(r'\b' + re.escape(x) + r'\b', seg) for x in idents):
                        ok = True
                        break

                if not ok:
                    for x in idents:
                        for c in re.finditer(r'\b(\w+)\s*\([^;]*\b' + re.escape(x) + r'\b', st[be:m.close]):
                            if c.group(1) in jf.asserting or c.group(1) in self.support_asserting:
                                ok = True

                if ok:
                    continue

                # a pure deadline loop that leaves on success: whatever follows it on the way out decides
                if not idents and re.search(r'\breturn\b|\bbreak\b', body):
                    if FAILING.search(st[be:m.close]):
                        continue

                    if not m.is_test:
                        # a helper that hands back what it found, or nothing: its callers decide
                        self.add(jf, at, m, "wait", "low",
                                 "a poll on `%s` that times out and carries on; the callers must check what it "
                                 "found" % squash(hdr, 60))

                        continue

                conf = "high" if not after else "medium"

                self.add(jf, at, m, "wait", conf,
                         "a poll on `%s` that times out, and %s" % (
                             squash(hdr, 60), "nothing after it asserts" if not after else
                             "no assert after it reads what was polled"))

            # (b) answers thrown away
            for mm in re.finditer(r'(?:^|[;{}])\s*((\w+)\s*\(|[\w.]+\.(await|join)\s*\()', st[m.open:m.close]):
                at = m.open + mm.start(1)

                if jf.outer_method_at(at) is not m:
                    continue

                name = mm.group(2)

                which = mm.group(3)

                p = at + len(mm.group(1)) - 1

                parts, close = args_of(jf, p)

                if close is None:
                    continue

                if not re.match(r'\s*;', st[close + 1:close + 5]):
                    continue

                if name and name in waiters:
                    self.add(jf, at, m, "wait", "high",
                             "`%s(..)` answers whether the wait succeeded, and the answer is thrown away" % name)
                elif which == 'await' and len(parts) == 2:
                    self.add(jf, at, m, "wait", "high",
                             "await with a timeout returns false on a timeout, and the answer is thrown away")
                elif which == 'join' and len(parts) == 1:
                    rest = st[close:m.close]

                    if not re.search(r'isAlive\s*\(', rest):
                        self.add(jf, at, m, "wait", "medium",
                                 "join(ms) returns on a timeout too, and nothing asks isAlive() after it")

        # (c) asserts that cannot reach TestNG
        handler = bool(re.search(r'setDefaultUncaughtExceptionHandler|setUncaughtExceptionHandler', st))

        for mm in re.finditer(r'\b(invokeLater|new\s+(?:java\.lang\.)?Thread|new\s+(?:javax\.swing\.)?Timer|'
                              r'add\w*Listener)\s*\(', st):
            p = mm.end() - 1

            close = jf.pairs.get(p)

            if close is None:
                continue

            inner = st[p:close]

            if not ASSERT_CALL.search(inner):
                continue

            m = jf.outer_method_at(mm.start())

            if m is None:
                continue

            if re.search(r'catch\s*\(\s*(?:Throwable|AssertionError|Error)\b', inner):
                continue

            kind = mm.group(1)

            if kind.startswith('add'):
                conf = "medium"
                why = "asserts inside a listener: they fail the test only if the event fires on the test's own stack"
            else:
                tail = st[close:m.close]

                last = not re.search(r'[;}]\s*\S', tail[tail.find(';') + 1:] if ';' in tail else '')

                conf = "medium" if handler else "high"
                why = "asserts inside %s: an AssertionError there goes to the uncaught-exception handler, not to " \
                      "TestNG%s" % (squash(kind, 30), " (and it is the method's last statement)" if last else "")

            self.add(jf, mm.start(), m, "wait", conf, why)

    # ---- skip -------------------------------------------------------------------------------------------------------

    def scan_skips(self, jf):
        st = jf.st

        code = jf.code

        for m in jf.methods:
            if m.is_test and re.search(r'enabled\s*=\s*false', m.annotations):
                self.add(jf, m.name_at, m, "skip", "high", "@Test(enabled = false): never runs, and nothing counts it")

        for mm in re.finditer(r'\bthrow\s+new\s+(?:org\.testng\.)?SkipException\s*\(', st):
            at = mm.start()

            m = jf.outer_method_at(at)

            if m is None:
                continue

            cond = None

            for if_at, (cs, ce), (ts, te), els in ifs_in(jf, m.open, m.close):
                if ts <= at < te:
                    cond = code[cs:ce]

                if els and els[0] <= at < els[1]:
                    cond = "not (" + code[cs:ce] + ")"

            if cond is not None and HEADLESS.search(cond):
                continue

            # a skip inside a lookup helper: the caller decides
            where = "the whole class" if m.kind == 'before' else ("its callers" if not m.is_test else "the test")

            if cond is None:
                conf = "high" if m.is_test or m.kind == 'before' else "medium"

                note = "an unconditional skip in %s" % where
            else:
                env = re.search(r'getProperty|getenv|os\.name|isDirectory|exists\(\)', cond)

                conf = "low" if env and not re.search(r'==\s*null|isEmpty', cond) else "medium"

                if m.kind == 'before':
                    conf = "high" if conf == "medium" else "medium"

                note = "skips %s when `%s`" % (where, squash(cond, 70))

            self.add(jf, at, m, "skip", conf, note)

    # ---- needle -----------------------------------------------------------------------------------------------------

    def scan_needles(self, jf):
        if not re.search(r'SourceOf|"src/|\bsrc/', jf.text):
            return

        helpers = self.needle_helpers(jf)

        for m in jf.methods:
            for b in (self.call_bindings(jf, m) if m.params and not m.is_test else [None]):
                for env in self.envs(jf, m):
                    if b:
                        env = dict(env)
                        env['#called at'] = 'line %d' % jf.line(b['#at'])

                    self.needles_in(jf, m, env, helpers, b)

    def call_bindings(self, jf, m):
        """For a helper of the test file: its parameters bound to the arguments of each call of it in the file (with
        the caller's table rows, when the call sits in a table loop), so the needles it looks for are judged as each
        caller sees them.  [None] when nothing calls it."""
        names = []

        for p in m.params:
            mm = re.search(r'(\w+)\s*$', p)

            names.append(mm.group(1) if mm else None)

        out = []

        for at, end in jf.call_sites().get(m.name, []):
            if at == m.name_at:
                continue

            parts, close = args_of(jf, end - 1)

            if close is None or len(parts) != len(names):
                continue

            caller = jf.method_at(at)

            if caller is None or caller is m:
                continue

            for env in self.envs(jf, caller):
                spans = [v for k, v in env.items() if k.startswith('@')]

                if spans and not all(s0 <= at < e0 for s0, e0 in spans):
                    continue

                b = {'#at': at}

                for k, n in enumerate(names):
                    if n is not None:
                        b[n] = (jf.code[parts[k][0]:parts[k][1]], caller, env, at)

                out.append(b)

                if len(out) >= 40:
                    return out

        return out or [None]

    def envs(self, jf, m):
        """Loop variables bound to each row of a literal table the method loops over, so table-driven claims are
        checked row by row.  Always at least the empty binding."""
        envs = [{}]

        for kind, at, header, (bs, be) in loops_in(jf, m.open, m.close):
            if kind != 'foreach':
                continue

            h = jf.code[header[0]:header[1]]

            mm = re.match(r'\s*(?:final\s+)?[\w.<>\[\]]+\s+(\w+)\s*:\s*(.+?)\s*$', h, re.S)

            if not mm:
                continue

            table = self.table(jf, m, mm.group(2), at)

            if not table:
                continue

            var = mm.group(1)

            new = []

            for e in envs:
                for row in table:
                    d = dict(e)
                    d[var] = row
                    d['@' + var] = (bs, be)
                    new.append(d)

            envs = new[:400]

        return envs

    def table(self, jf, m, expr, at):
        """The rows of a literal table a loop runs over: a local or field initialised with `{..}`, or written in
        place."""
        expr = expr.strip()

        mm = re.match(r'^(\w+)$', expr)

        if not mm:
            return literal_list(expr)

        span = self.assignment(jf, m, mm.group(1), at)

        if span is None:
            return None

        return literal_list(jf.code[span[0]:span[1]])

    def assignment(self, jf, m, name, at):
        """The span of the expression last assigned to `name` before `at` in the method, else a field's."""
        st = jf.st

        sites = jf.assignments().get(name)

        if not sites:
            return None

        best = None

        if m is not None:
            k = bisect.bisect_left(sites, (at, -1)) - 1

            if k >= 0 and sites[k][0] > m.open:
                best = sites[k][1]

        if best is None:
            # a field: outside every method
            if name not in jf.fields:
                found = None

                for start, end in sites:
                    if not jf.in_any_method(start):
                        found = end

                jf.fields[name] = found

            best = jf.fields[name]

        if best is None:
            return None

        end = expr_end(st, best, len(st))

        return best, end

    # -- needle-taking helpers of the test file

    def needle_helpers(self, jf):
        """Helpers of this file that look a String parameter up in text: name -> list of (needle index, hay expr)."""
        helpers = {}

        for m in jf.methods:
            names = []

            for p in m.params:
                mm = re.search(r'(\w+)\s*$', p)

                names.append(mm.group(1) if mm else None)

            if not names:
                continue

            body = jf.code[m.open:m.close]

            for i, n in enumerate(names):
                if n is None or not re.search(r'\bString\b', m.params[i]):
                    continue

                for mm in re.finditer(r'([\w.()\[\]]+?)\s*\.\s*(contains|indexOf|lastIndexOf)\s*\(\s*' + re.escape(n) +
                                      r'\s*[,)]', body):
                    helpers.setdefault(m.name, []).append((i, mm.group(1), names, m, len(names)))

                for mm in re.finditer(r'\boccurrences\s*\(\s*' + re.escape(n) + r'\s*\)', body):
                    helpers.setdefault(m.name, []).append((i, 'SourceOf.allCode()', names, m, len(names)))

        return helpers

    def needles_in(self, jf, m, env, helpers, b=None):
        st = jf.st

        code = jf.code

        lo, hi = m.open, m.close

        spans = [v for k, v in env.items() if k.startswith('@')]

        if spans:
            lo = max(s for s, e in spans)
            hi = min(e for s, e in spans)

        # receiver.contains / indexOf / lastIndexOf / matches
        for mm in re.finditer(r'\.\s*(contains|indexOf|lastIndexOf|matches)\s*\(', st[lo:hi]):
            dot = lo + mm.start()

            p = lo + mm.end() - 1

            if jf.method_at(dot) is not m and jf.outer_method_at(dot) is not m:
                continue

            parts, close = args_of(jf, p)

            if close is None or not parts:
                continue

            needle = self.eval_str(jf, m, code[parts[0][0]:parts[0][1]], dot, env, 0, b)

            if needle is None or not needle:
                continue

            start = receiver_before(jf, dot)

            recv = code[start:dot].strip()

            if not recv:
                continue

            hay = self.resolve(jf, m, recv, dot, env, 0, b)

            if hay is None:
                continue

            frm = None

            if len(parts) > 1 and mm.group(1) in ('indexOf', 'lastIndexOf'):
                frm = (self.eval_int(jf, m, code[parts[1][0]:parts[1][1]], dot, env, 0, b),
                       code[parts[1][0]:parts[1][1]].strip())

            self.judge(jf, m, start, close, mm.group(1), needle, hay, frm, env)

        # SourceOf.occurrences(needle)
        for mm in re.finditer(r'\b(?:SourceOf\s*\.\s*)?occurrences\s*\(', st[lo:hi]):
            at = lo + mm.start()

            p = lo + mm.end() - 1

            parts, close = args_of(jf, p)

            if close is None or len(parts) != 1:
                continue

            if re.search(r'\b(?:static|int|String)\s+$', st[max(0, at - 30):at]):
                continue

            needle = self.eval_str(jf, m, code[parts[0][0]:parts[0][1]], at, env, 0, b)

            if not needle:
                continue

            all_code = self.source.all_code()

            self.judge(jf, m, at, close, 'occurrences', needle,
                       Hay(all_code, all_code, 'SourceOf.allCode()', blanked=True), None, env)

        # Pattern.compile(regex)
        for mm in re.finditer(r'\bPattern\s*\.\s*compile\s*\(', st[lo:hi]):
            at = lo + mm.start()

            p = lo + mm.end() - 1

            parts, close = args_of(jf, p)

            if close is None or not parts:
                continue

            regex = self.eval_str(jf, m, code[parts[0][0]:parts[0][1]], at, env, 0, b)

            if not regex:
                continue

            flags = code[parts[1][0]:parts[1][1]] if len(parts) > 1 else ''

            mt = re.match(r'\s*\.\s*matcher\s*\(', st[close + 1:close + 30])

            if not mt:
                continue

            mp = close + 1 + mt.end() - 1

            margs, mclose = args_of(jf, mp)

            if mclose is None or not margs:
                continue

            hay = self.resolve(jf, m, code[margs[0][0]:margs[0][1]], at, env, 0, b)

            if hay is None:
                continue

            self.judge(jf, m, at, mclose, 'pattern', regex, hay, None, env, flags=flags)

        # helpers of this file that take a needle
        for name, uses in helpers.items():
            for mm in re.finditer(r'(?<![\w.])' + re.escape(name) + r'\s*\(', st[lo:hi]):
                at = lo + mm.start()

                if re.search(r'\b(?:static|void|int|boolean|String|long)\s+$', st[max(0, at - 30):at]):
                    continue

                p = lo + mm.end() - 1

                parts, close = args_of(jf, p)

                if close is None:
                    continue

                for idx, hay_expr, names, hm, arity in uses:
                    if len(parts) != arity or idx >= len(parts):
                        continue

                    needle = self.eval_str(jf, m, code[parts[idx][0]:parts[idx][1]], at, env, 0, b)

                    if not needle:
                        continue

                    bindings = {}

                    for k, n in enumerate(names):
                        if n is not None and k < len(parts):
                            e0 = code[parts[k][0]:parts[k][1]]

                            # an argument that is itself one of this helper's parameters stays bound to its caller
                            if b and re.match(r'^\s*(\w+)\s*$', e0) and e0.strip() in b:
                                bindings[n] = b[e0.strip()]
                            else:
                                bindings[n] = (e0, m, env, at)

                    hay = self.resolve(jf, hm, hay_expr, hm.close, {}, 0, bindings=bindings)

                    if hay is None:
                        continue

                    self.judge(jf, m, at, close, 'helper ' + name, needle, hay, None, env)

    # -- evaluation

    def eval_str(self, jf, m, expr, at, env, depth=0, bindings=None):
        if depth > 6:
            return None

        expr = expr.strip()

        if not expr:
            return None

        while expr.startswith('(') and expr.endswith(')') and matching_outer(expr):
            expr = expr[1:-1].strip()

        pieces = split_top(expr, '+')

        if pieces is not None and len(pieces) > 1:
            out = []

            for x in pieces:
                v = self.eval_str(jf, m, x, at, env, depth + 1, bindings)

                if v is None:
                    return None

                out.append(v)

            return ''.join(out)

        if re.match(r'^"(?:\\.|[^"\\])*"$', expr):
            return unescape(expr)

        if re.match(r"^'(?:\\.|[^'\\])'$", expr):
            return unescape(expr)

        mm = re.match(r'^(\w+)\s*\[\s*(\d+)\s*\]$', expr)

        if mm and mm.group(1) in env:
            row = env[mm.group(1)]

            k = int(mm.group(2))

            if isinstance(row, list) and k < len(row) and isinstance(row[k], str):
                return row[k]

            return None

        mm = re.match(r'^(\w+)$', expr)

        if mm:
            name = mm.group(1)

            if name in env and isinstance(env[name], str):
                return env[name]

            if bindings and name in bindings:
                e, cm, cenv, cat = bindings[name]

                return self.eval_str(jf, cm, e, cat, cenv, depth + 1)

            span = self.assignment(jf, m, name, at)

            if span is None:
                return None

            return self.eval_str(jf, m, jf.code[span[0]:span[1]], span[0], env, depth + 1, bindings)

        mm = re.match(r'^(?:java\.util\.regex\.)?Pattern\.quote\s*\((.*)\)$', expr, re.S)

        if mm:
            v = self.eval_str(jf, m, mm.group(1), at, env, depth + 1, bindings)

            return None if v is None else '\\Q' + v + '\\E'

        return None

    def eval_int(self, jf, m, expr, at, env, depth, bindings=None):
        if depth > 8:
            return None

        expr = expr.strip()

        while expr.startswith('(') and expr.endswith(')') and matching_outer(expr):
            expr = expr[1:-1].strip()

        if re.match(r'^-?\d+$', expr):
            return int(expr)

        for op in ('+', '-'):
            pieces = split_top(expr, op)

            if pieces is not None and len(pieces) > 1 and all(p.strip() for p in pieces):
                vals = [self.eval_int(jf, m, x, at, env, depth + 1, bindings) for x in pieces]

                if None in vals:
                    return None

                total = vals[0]

                for v in vals[1:]:
                    total = total + v if op == '+' else total - v

                return total

        mm = re.match(r'^Math\.(max|min)\s*\((.*)\)$', expr, re.S)

        if mm:
            pieces = split_top(mm.group(2), ',')

            if pieces and len(pieces) == 2:
                a = self.eval_int(jf, m, pieces[0], at, env, depth + 1, bindings)
                b = self.eval_int(jf, m, pieces[1], at, env, depth + 1, bindings)

                if a is None or b is None:
                    return None

                return max(a, b) if mm.group(1) == 'max' else min(a, b)

        mm = re.match(r'^(.*)\.\s*length\s*\(\)$', expr, re.S)

        if mm:
            s = self.eval_str(jf, m, mm.group(1), at, env, depth + 1, bindings)

            if s is not None:
                return len(s)

            h = self.resolve(jf, m, mm.group(1), at, env, depth + 1, bindings)

            return len(h.text) if h is not None and h.exact else None

        mm = re.match(r'^(.*?)\.\s*(indexOf|lastIndexOf)\s*\((.*)\)$', expr, re.S)

        if mm and matching_outer('(' + mm.group(3) + ')'):
            h = self.resolve(jf, m, mm.group(1), at, env, depth + 1, bindings)

            if h is None or not h.exact:
                return None

            args = split_top(mm.group(3), ',')

            if not args:
                return None

            n = self.eval_str(jf, m, args[0], at, env, depth + 1, bindings)

            if n is None:
                return None

            if len(args) > 1:
                f = self.eval_int(jf, m, args[1], at, env, depth + 1, bindings)

                if f is None:
                    return None
            else:
                f = None

            if mm.group(2) == 'indexOf':
                return h.text.find(n, max(f or 0, 0))

            return h.text.rfind(n, 0, (f + len(n)) if f is not None else len(h.text))

        mm = re.match(r'^(\w+)$', expr)

        if mm:
            name = mm.group(1)

            if bindings and name in bindings:
                e, cm, cenv, cat = bindings[name]

                return self.eval_int(jf, cm, e, cat, cenv, depth + 1)

            span = self.assignment(jf, m, name, at)

            if span is None:
                return None

            return self.eval_int(jf, m, jf.code[span[0]:span[1]], span[0], env, depth + 1, bindings)

        return None

    def resolve(self, jf, m, expr, at, env, depth, bindings=None):
        """The text an expression of the test holds, when it is a read of src/ the scan can follow; else None."""
        if depth > 8:
            return None

        expr = expr.strip()

        while expr.startswith('(') and expr.endswith(')') and matching_outer(expr):
            expr = expr[1:-1].strip()

        src = self.source

        mm = re.match(r'^(.*)\.\s*(toString|trim|intern)\s*\(\)$', expr, re.S)

        if mm:
            return self.resolve(jf, m, mm.group(1), at, env, depth + 1, bindings)

        mm = re.match(r'^(.*)\.\s*toLowerCase\s*\(\)$', expr, re.S)

        if mm:
            h = self.resolve(jf, m, mm.group(1), at, env, depth + 1, bindings)

            if h is None:
                return None

            return Hay(h.text.lower(), h.code.lower(), h.label + '.toLowerCase()', h.rel, h.base, h.blanked, h.exact)

        mm = re.match(r'^(.*)\.\s*replaceAll\s*\((.*)\)$', expr, re.S)

        if mm and matching_outer('(' + mm.group(2) + ')'):
            h = self.resolve(jf, m, mm.group(1), at, env, depth + 1, bindings)

            args = split_top(mm.group(2), ',')

            if h is None or not args or len(args) != 2:
                return h

            a = self.eval_str(jf, m, args[0], at, env, depth + 1, bindings)
            b = self.eval_str(jf, m, args[1], at, env, depth + 1, bindings)

            if a is None or b is None:
                return Hay(h.text, h.code, h.label, h.rel, None, h.blanked, False)

            try:
                rx = re.compile(java_regex(a))

                repl = java_replacement(b)

                return Hay(rx.sub(repl, h.text), rx.sub(repl, h.code), h.label + '.replaceAll', h.rel, None,
                           h.blanked, h.exact)
            except (re.error, ValueError, IndexError):
                return Hay(h.text, h.code, h.label, h.rel, None, h.blanked, False)

        # `cond ? read : ""` - the read, which is what a claim on it means to look at
        top = split_top(expr, '?')

        if top is not None and len(top) == 2:
            branches = split_top(top[1], ':')

            if branches is not None and len(branches) == 2:
                for branch in branches:
                    if branch.strip() not in ('""', 'null'):
                        h = self.resolve(jf, m, branch, at, env, depth + 1, bindings)

                        if h is not None:
                            return h

                return None

        mm = re.match(r'^(.*)\.\s*replace\s*\((.*)\)$', expr, re.S)

        if mm and matching_outer('(' + mm.group(2) + ')'):
            h = self.resolve(jf, m, mm.group(1), at, env, depth + 1, bindings)

            args = split_top(mm.group(2), ',')

            if h is None or not args or len(args) != 2:
                return h

            a = self.eval_str(jf, m, args[0], at, env, depth + 1, bindings)
            b = self.eval_str(jf, m, args[1], at, env, depth + 1, bindings)

            if a is None or b is None:
                return Hay(h.text, h.code, h.label, h.rel, None, h.blanked, False)

            return Hay(h.text.replace(a, b), h.code.replace(a, b), h.label, h.rel,
                       h.base if len(a) == len(b) else None, h.blanked, h.exact)

        mm = re.match(r'^(?:SourceOf\s*\.\s*)?allCode\s*\(\)$', expr)

        if mm:
            t = src.all_code()

            return Hay(t, t, 'SourceOf.allCode()', '*', 0, True)

        mm = re.match(r'^(?:SourceOf\s*\.\s*)?all\s*\(\)$', expr)

        if mm:
            return Hay(src.all(), src.all_code(), 'SourceOf.all()', '*', 0, False)

        mm = re.match(r'^(?:support\s*\.\s*)?SourceOf\s*\.\s*(declaration|body|fileDeclaring|codeUnder|withoutComments)'
                      r'\s*\((.*)\)$', expr, re.S)

        if mm and matching_outer('(' + mm.group(2) + ')'):
            what = mm.group(1)

            if what == 'withoutComments':
                h = self.resolve(jf, m, mm.group(2), at, env, depth + 1, bindings)

                if h is None:
                    return None

                return Hay(h.code, h.code, 'withoutComments(' + h.label + ')', h.rel, h.base, True, h.exact)

            arg = self.eval_str(jf, m, mm.group(2), at, env, depth + 1, bindings)

            if arg is None:
                return None

            if what == 'codeUnder':
                prefix = arg.replace('\\', '/')

                if not prefix.endswith('/'):
                    prefix += '/'

                parts = [v for k, v in src.code.items() if k.startswith(prefix)]

                if not parts:
                    return None

                t = '\n'.join(parts)

                return Hay(t, t, 'codeUnder(%s)' % arg, None, None, True)

            if what == 'fileDeclaring':
                d = src.declaration(arg)

                if d is None:
                    return None

                return Hay(src.files[d[0]], src.code[d[0]], d[0], d[0], 0, False)

            d = src.declaration(arg, body_only=(what == 'body'))

            if d is None:
                return None

            rel, s, e, text = d

            return Hay(text, src.code[rel][s:e], '%s(%s)' % (what, squash(arg, 40)), rel, s, False)

        mm = re.match(r'^(?:SourceOf\s*\.\s*)?declarationIn\s*\((.*)\)$', expr, re.S)

        if mm and matching_outer('(' + mm.group(1) + ')'):
            args = split_top(mm.group(1), ',')

            if not args or len(args) != 2:
                return None

            h = self.resolve(jf, m, args[0], at, env, depth + 1, bindings)

            sig = self.eval_str(jf, m, args[1], at, env, depth + 1, bindings)

            if h is None or sig is None or not h.exact:
                return None

            code = without_comments(h.text.replace('\r\n', '\n'))

            text = h.text.replace('\r\n', '\n')

            k = code.find(sig)

            if k < 0 or code.find(sig, k + len(sig)) >= 0:
                return None

            sub = JFile('', '', text)

            end, _ = end_of(sub, k)

            if end is None:
                return None

            return Hay(text[k:end], code[k:end], 'declarationIn(%s)' % squash(sig, 40), h.rel,
                       (h.base + k) if h.base is not None and '\r' not in h.text else None, False)

        mm = re.match(r'^(.*)\.\s*substring\s*\((.*)\)$', expr, re.S)

        if mm and matching_outer('(' + mm.group(2) + ')'):
            h = self.resolve(jf, m, mm.group(1), at, env, depth + 1, bindings)

            if h is None:
                return None

            args = split_top(mm.group(2), ',')

            if not args:
                return None

            a = self.eval_int(jf, m, args[0], at, env, depth + 1, bindings) if h.exact else None
            b = (self.eval_int(jf, m, args[1], at, env, depth + 1, bindings) if len(args) > 1 else len(h.text)) \
                if h.exact else None

            if a is None or b is None or a < 0 or b < a:
                return Hay(h.text, h.code, h.label + '.substring(?)', h.rel, None, h.blanked, False)

            cut = Hay(h.text[a:b], h.code[a:b], h.label + '.substring', h.rel,
                      (h.base + a) if h.base is not None and not h.raw else None, h.blanked, True)

            return cut

        if 'readAllBytes' in expr or 'readString' in expr or 'readAllLines' in expr:
            if 'readAllLines' in expr:
                return None

            pm = re.search(r'(?:Paths\.get|new\s+(?:java\.io\.)?File|Path\.of)\s*\(', expr)

            if pm:
                st = structure(expr)

                depth = 0

                end = None

                for k in range(pm.end() - 1, len(st)):
                    if st[k] == '(':
                        depth += 1
                    elif st[k] == ')':
                        depth -= 1

                        if depth == 0:
                            end = k
                            break

                if end is None:
                    return None

                rel = self.eval_str(jf, m, expr[pm.end():end], at, env, depth + 1, bindings)

                if rel is None:
                    return None

                raw = src.raw(rel)

                if raw is None:
                    return None

                h = Hay(raw, src.raw_code(rel) if rel.endswith('.java') else raw, rel, rel, 0, False)

                h.raw = True

                return h

            return None

        mm = re.match(r'^(\w+)$', expr)

        if mm:
            name = mm.group(1)

            if bindings and name in bindings:
                e, cm, cenv, cat = bindings[name]

                return self.resolve(jf, cm, e, cat, cenv, depth + 1)

            span = self.assignment(jf, m, name, at)

            if span is None:
                return None

            return self.resolve(jf, m, jf.code[span[0]:span[1]], span[0], env, depth + 1, bindings)

        # a helper of this file that returns a read
        mm = re.match(r'^(\w+)\s*\((.*)\)$', expr, re.S)

        if mm and matching_outer('(' + mm.group(2) + ')'):
            name = mm.group(1)

            args = split_top(mm.group(2), ',') if mm.group(2).strip() else []

            if args is None:
                return None

            for h in jf.methods:
                if h.name != name or len(h.params) != len(args):
                    continue

                ret = list(re.finditer(r'\breturn\s+', jf.st[h.open:h.close]))

                if not ret:
                    continue

                r0 = h.open + ret[-1].end()

                r1 = expr_end(jf.st, r0, h.close)

                b = {}

                for k, p in enumerate(h.params):
                    pn = re.search(r'(\w+)\s*$', p)

                    if pn:
                        b[pn.group(1)] = (args[k], m, env, at)

                return self.resolve(jf, h, jf.code[r0:r1], r0, {}, depth + 1, b)

        return None

    # -- judging one needle

    def judge(self, jf, m, at, close, how, needle, hay, frm, env, flags=''):
        st = jf.st

        pol = self.polarity(jf, m, at, close)

        where = "in %s" % hay.label

        row = ""

        if env:
            rows = [v for k, v in env.items() if not k.startswith('@')]

            rows = [v for v in rows if not isinstance(v, tuple)]

            row = " (row %s)" % squash(json.dumps(rows[0] if len(rows) == 1 else rows), 70)

        if how in ('matches', 'pattern'):
            self.judge_regex(jf, m, at, how, needle, hay, pol, flags, row)

            return

        text = hay.text

        found = text.find(needle)

        conf_cap = "high" if (m.is_test or m.name in jf.asserting) else "medium"

        if found < 0:
            # (a cut the scan could not size stands for its whole parent, so a miss there is a miss in the cut too)
            if pol in ('positive', 'anchor', 'cond'):
                near = squeeze(needle) and squeeze(needle) in squeeze(text)

                conf = "high" if pol == 'positive' else "medium" if pol == 'anchor' else "medium"

                self.add(jf, at, m, "needle", cap(conf, conf_cap),
                         "%s needle %r is not %s%s%s" % (pol, squash(needle, 60), where, row,
                                                         "; a near form is (whitespace or `this.`)" if near else
                                                         " - red today, or never reached"),
                         key=('missing', needle, pol))
            elif pol == 'negative':
                if hay.label.startswith('SourceOf.allCode') or hay.label.startswith('codeUnder') or hay.blanked:
                    sq = squeeze(needle)

                    if len(sq) >= 6 and sq in src_squeezed(self.source, hay):
                        self.add(jf, at, m, "needle", cap("high", conf_cap),
                                 "negative needle %r is spelled unlike the code: its squeezed form IS %s%s, so the "
                                 "claim would not see that code" % (squash(needle, 60), where, row))
                else:
                    sq = squeeze(needle)

                    if len(sq) >= 6 and sq in squeeze(text):
                        self.add(jf, at, m, "needle", cap("high", conf_cap),
                                 "negative needle %r is spelled unlike the code: its squeezed form IS %s%s"
                                 % (squash(needle, 60), where, row))

            return

        # present: is it only in comments, in a read that keeps them?
        if pol in ('positive', 'anchor') and not hay.blanked and hay.code is not None and len(hay.code) == len(text):
            if hay.code.find(needle) < 0 and not looks_like_comment_needle(needle):
                self.add(jf, at, m, "needle", cap("high", conf_cap),
                         "%s needle %r is %s only inside a comment: the read keeps comments, so a comment "
                         "satisfies it%s" % (pol, squash(needle, 60), where, row))

                return

        # an unbounded search from an anchor that lands outside the anchor's member
        if frm is not None and frm[0] is not None and hay.exact and hay.base is not None and hay.rel and \
                how == 'indexOf':
            start = frm[0]

            hit = text.find(needle, max(start, 0))

            if start >= 0 and hit >= 0:
                span = self.member_span(hay, start)

                if span is not None and hit >= span[1]:
                    rel, s_at = self.locate(hay, start)

                    _, h_at = self.locate(hay, hit)

                    jf_src = self.source.jfile(rel)

                    lines = jf_src.line(h_at) - jf_src.line(s_at)

                    self.add(jf, at, m, "needle", cap("high", conf_cap),
                             "indexOf(%r, %s) first finds it %d lines past `%s`, outside the member that holds "
                             "the anchor (%s:%d)%s" % (squash(needle, 40), squash(frm[1], 20), lines,
                                                       squash(jf_src.line_text(s_at).strip(), 50), rel,
                                                       jf_src.line(h_at), row))

    def judge_regex(self, jf, m, at, how, regex, hay, pol, flags, row):
        try:
            pyre = java_regex(regex)

            fl = 0

            if 'DOTALL' in flags:
                fl |= re.S

            if 'MULTILINE' in flags:
                fl |= re.M

            if 'CASE_INSENSITIVE' in flags:
                fl |= re.I

            compiled = re.compile(pyre, fl)
        except (re.error, ValueError, IndexError):
            return

        text = hay.text

        if how == 'matches':
            dotall = '(?s' in regex or (fl & re.S)

            if '\n' in text and not dotall and regex.startswith('.*') and compiled.fullmatch(text) is None:
                inner = regex[2:-2] if regex.endswith('.*') else regex[2:]

                try:
                    could = re.search(java_regex(inner), text) is not None
                except re.error:
                    could = False

                conf = "high" if pol == 'negative' else "medium"

                self.add(jf, at, m, "needle", conf,
                         "matches(%r) over text with line breaks can never match without (?s)%s%s" % (
                             squash(regex, 40), "; the inner pattern IS in the text" if could else "", row))

            return

        hit = compiled.search(text)

        if hit is None and pol in ('positive', 'anchor'):
            self.add(jf, at, m, "needle", "high",
                     "positive pattern %r finds nothing in %s%s" % (squash(regex, 60), hay.label, row))
        elif hit is not None and pol in ('positive',) and not hay.blanked and hay.code is not None and \
                len(hay.code) == len(text) and compiled.search(hay.code) is None:
            self.add(jf, at, m, "needle", "high",
                     "positive pattern %r matches %s only inside a comment%s" % (squash(regex, 60), hay.label, row))

    def polarity(self, jf, m, at, close):
        """negative / positive / anchor / cond / other, from what surrounds the needle call."""
        st = jf.st

        after = st[close + 1:close + 40]

        before = st[max(m.open, at - 60):at]

        neg = False

        if re.match(r'\s*\.\s*(?:matcher\s*\([^)]*\)\s*\.\s*)?find\s*\(\)', after):
            after = after[re.match(r'\s*\.\s*(?:matcher\s*\([^)]*\)\s*\.\s*)?find\s*\(\)', after).end():]

        if re.match(r'\s*(?:<\s*0|==\s*-1|<=\s*-1|==\s*0\b|<\s*1\b|<=\s*0\b)', after):
            neg = True
        elif not re.match(r'\s*(?:>=\s*0|>\s*-1|!=\s*-1|>\s*0|>=\s*1|>\s*\w|>=\s*\w)', after) and \
                re.match(r'\s*(?:<|>|<=|>=)\s*\w', after):
            return 'other'

        if re.search(r'!\s*$', before):
            neg = not neg

        if re.search(r'\|\|\s*!?\s*$', before) or re.match(r'\s*\|\|', after):
            return 'alternative'

        name, p = enclosing_call(jf, at, m.open)

        if name is not None:
            short = name.split('.')[-1]

            if short in ('for', 'while'):
                return 'count'

            if short in ('assertFalse', 'assertNull'):
                neg = not neg

                return 'negative' if neg else 'positive'

            if short in ('assertTrue', 'assertNotNull'):
                return 'negative' if neg else 'positive'

            if short in ('assertEquals', 'assertNotEquals'):
                parts, c2 = args_of(jf, p)

                if len(parts) > 1:
                    other = jf.code[parts[1][0]:parts[1][1]].strip()

                    first = jf.code[parts[0][0]:parts[0][1]].strip()

                    lits = (other, first)

                    zero = any(x in ('0', '-1', 'false') for x in lits)

                    plus = any(x == 'true' or re.match(r'^[1-9]\d*$', x) for x in lits)

                    if not zero and not plus:
                        return 'other'

                    if short == 'assertNotEquals':
                        zero = not zero

                    return 'negative' if zero else 'positive'

                return 'other'

        for fm in re.finditer(r'\bfor\s*\(', st[max(m.open, at - 300):at]):
            fp = max(m.open, at - 300) + fm.end() - 1

            if jf.pairs.get(fp, -1) > at:
                return 'count'

        # `int x = hay.indexOf(..)` keeps an anchor; `if (..)` is a condition
        line_start = max(st.rfind(';', m.open, at), st.rfind('{', m.open, at), st.rfind('}', m.open, at)) + 1

        head = st[line_start:at]

        if re.search(r'\bfor\s*\(', head):
            return 'count'

        if re.search(r'\bif\s*\(|\bwhile\s*\(|\?\s*$|&&|\|\|', head):
            return 'negative' if neg and re.search(r'\bif\s*\(', head) is None else 'cond'

        if re.search(r'\b(?:int|long)\s+\w+\s*=\s*$', head) or re.search(r'\b\w+\s*=\s*$', head) and \
                re.search(r'indexOf', st[at:close]):
            return 'anchor'

        if re.search(r'\bboolean\s+\w+\s*=\s*$', head):
            return 'negative' if neg else 'other'

        return 'negative' if neg else 'other'

    def member_span(self, hay, offset):
        rel, local = self.locate(hay, offset)

        if rel is None:
            return None

        jf = self.source.jfile(rel)

        span = jf.member_span(local)

        if span is None:
            return None

        # back into the hay's own offsets
        shift = offset - local

        return span[0] + shift, span[1] + shift

    def locate(self, hay, offset):
        """(src file, offset in that file) for an offset in a hay."""
        if hay.rel == '*':
            return self.source.file_of(offset)

        if hay.rel and hay.base is not None and hay.rel in self.source.files:
            if hay.raw:
                # a direct read keeps CRLF; the index of src/ has `\n` endings
                return hay.rel, hay.base + offset - hay.text.count('\r', 0, offset)

            return hay.rel, hay.base + offset

        return None, None


# ----------------------------------------------------------------------------------------------------------------------
# Little pure helpers
# ----------------------------------------------------------------------------------------------------------------------

def split_top(text, sep=','):
    """Split at top-level `sep` (outside brackets and literals); None for unbalanced text."""
    if text is None:
        return None

    st = structure(text)

    parts = []

    depth = 0

    start = 0

    for i, c in enumerate(st):
        if c in '({[':
            depth += 1
        elif c in ')}]':
            depth -= 1

            if depth < 0:
                return None
        elif c == sep and depth == 0:
            if sep in '+-':
                prev = st[:i].rstrip()

                if not prev or prev[-1] in '(=,+-*/<>!&|?:' or st[i + 1:i + 2] == sep or \
                        (i > 0 and st[i - 1] == sep):
                    continue

            parts.append(text[start:i])
            start = i + 1

    if depth != 0:
        return None

    parts.append(text[start:])

    return parts


def matching_outer(expr):
    st = structure(expr)

    depth = 0

    for i, c in enumerate(st):
        if c == '(':
            depth += 1
        elif c == ')':
            depth -= 1

            if depth == 0 and i != len(st) - 1:
                return False

    return depth == 0


def literal_list(text):
    """The values of a `{..}` / `new X[] {..}` / `Arrays.asList(..)` of literals: a string per item, or a list per row
    of a two-level table; None for an item that is not a literal."""
    text = text.strip()

    text = re.sub(r'^new\s+[\w.<>]+\s*(?:\[\s*\])+\s*', '', text)

    text = re.sub(r'^(?:java\.util\.)?Arrays\.asList\s*\(', '{', text)

    if text.endswith(')') and text.startswith('{'):
        text = text[:-1] + '}'

    if not text.startswith('{'):
        return None

    items = split_top(text[1:-1].rstrip().rstrip(',') if text.endswith('}') else None)

    if items is None:
        return None

    out = []

    for it in items:
        it = it.strip()

        if not it:
            continue

        if it.startswith('{'):
            row = split_top(it[1:-1].rstrip().rstrip(','))

            out.append([literal_value(x) for x in row] if row is not None else None)
        else:
            out.append(literal_value(it))

    return out or None


def literal_value(text):
    text = text.strip()

    if re.match(r'^"(?:\\.|[^"\\])*"$', text):
        return unescape(text)

    parts = split_top(text, '+')

    if parts and len(parts) > 1 and all(re.match(r'^\s*"(?:\\.|[^"\\])*"\s*$', p) for p in parts):
        return ''.join(unescape(p.strip()) for p in parts)

    return None


def java_regex(r):
    """A Java regex in Python's dialect, as far as these tests use it."""
    r = re.sub(r'\\Q(.*?)\\E', lambda mm: re.escape(mm.group(1)), r, flags=re.S)

    r = r.replace('\\p{Alpha}', 'a-zA-Z').replace('\\p{Upper}', 'A-Z').replace('\\p{Lower}', 'a-z')

    r = r.replace('\\p{javaJavaIdentifierPart}', '\\w').replace('\\p{Punct}', '!-/:-@\\[-`{-~')

    if re.search(r'\\p\{|[*+?}]\+', r):
        raise ValueError(r)

    return r


def java_replacement(b):
    """A Java replaceAll replacement as a Python one: `$1` is a group, `\\x` is x."""
    out = []

    i = 0

    while i < len(b):
        c = b[i]

        if c == '\\' and i + 1 < len(b):
            out.append(b[i + 1].replace('\\', '\\\\'))
            i += 2
        elif c == '$' and i + 1 < len(b) and b[i + 1].isdigit():
            out.append('\\g<' + b[i + 1] + '>')
            i += 2
        else:
            out.append(c.replace('\\', '\\\\'))
            i += 1

    return ''.join(out)


def src_squeezed(source, hay):
    return source.squeezed(hay.label, hay.text)


def squash(s, n):
    s = re.sub(r'\s+', ' ', s or '').strip()

    return s if len(s) <= n else s[:n - 3] + '...'


def cap(conf, ceiling):
    return conf if CONFIDENCES.index(conf) <= CONFIDENCES.index(ceiling) else ceiling


def looks_like_comment_needle(n):
    return n.lstrip().startswith(('//', '/*', '*')) or 'TODO' in n


def bail_word(then):
    """`return` or `continue` when a then-branch only bails out (no block inside it), else None."""
    t = then.strip()

    if t.startswith('{') and t.endswith('}'):
        t = t[1:-1].strip()

    if '{' in t or '}' in t:
        return None

    statements = [x.strip() for x in t.split(';') if x.strip()]

    if not statements:
        return None

    mm = re.match(r'^(return|continue)\b', statements[-1])

    return mm.group(1) if mm else None


def positive_asserts(jf):
    """(offset, text) of every assertTrue / assertNotNull / assertEquals in the file, computed once."""
    if not hasattr(jf, '_positives'):
        out = []

        for mm in re.finditer(r'\bassert(?:True|NotNull|Equals)\s*\(', jf.st):
            close = jf.pairs.get(mm.end() - 1)

            if close is not None:
                out.append((mm.start(), jf.code[mm.start():close]))

        jf._positives = out

    return jf._positives


def guard_subject(cond):
    m = re.search(r'([\w.]+)\s*(?:\.\s*isEmpty\s*\(\)|[!=]=\s*null|\.\s*size\s*\(\))', cond)

    if m:
        return m.group(1).split('.')[0]

    m = re.search(r'(\w+)\s*(?:<|>=|>|==|!=)\s*-?\d', cond)

    return m.group(1) if m else None


def classify_assert(name, first, second):
    """('absence' | 'positive', subject) for an assert's first two arguments."""
    if name == 'assertNull':
        return 'absence', first

    if name == 'assertNotNull':
        return 'positive', first

    if name == 'assertFalse':
        if first.startswith('!'):
            return 'positive', first[1:]

        if re.search(r'\.\s*isEmpty\s*\(\)$', first):
            return 'positive', first

        if re.search(r'\.\s*equals\s*\(', first) or re.search(r'&&|\|\||==|!=|<|>', first):
            return 'other', first

        return 'absence', first

    if name == 'assertTrue':
        f = first

        if re.match(r'^!\s*[\w.]', f) and not re.search(r'&&|\|\|', f):
            if re.search(r'\.\s*isEmpty\s*\(\)$', f):
                return 'positive', f[1:].strip()

            if re.search(r'\.\s*equals\s*\(', f):
                return 'other', f

            return 'absence', f[1:].strip()

        m = re.match(r'^(.*?)\.\s*isEmpty\s*\(\)$', f, re.S)

        if m and not f.startswith('!'):
            return 'absence', m.group(1)

        m = re.match(r'^(.*?)\s*(?:==\s*0|<\s*0|==\s*-1|<=\s*0)$', f, re.S)

        if m and not re.search(r'&&|\|\|', f):
            return 'absence', m.group(1)

        return 'positive', f

    if name == 'assertEquals' and second is not None:
        if second in ('0', '0L', '-1') and not re.match(r'^-?\d', first):
            return 'absence', re.sub(r'\.\s*size\s*\(\)$', '', first)

        if first in ('0', '0L') and not re.match(r'^-?\d', second):
            return 'absence', re.sub(r'\.\s*size\s*\(\)$', '', second)

        return 'positive', first

    return 'positive', first


def subject_key(subject):
    """The name that ties an absence to its control: a receiver, a call's name or a variable."""
    s = subject.strip()

    s = re.sub(r'^\(+|\)+$', '', s)

    m = re.match(r'^([\w]+)\s*\.\s*(?:contains|indexOf|containsKey|isEmpty|size|get|stream)\b', s)

    if m:
        return m.group(1)

    m = re.match(r'^(?:[\w]+\s*\.\s*)*(\w+)\s*\(', s)

    if m:
        return m.group(1)

    m = re.match(r'^(\w+)$', s)

    if m:
        return m.group(1)

    m = re.match(r'^(\w+)', s)

    return m.group(1) if m else None


# ----------------------------------------------------------------------------------------------------------------------
# Command line
# ----------------------------------------------------------------------------------------------------------------------

def shape_help():
    lines = ["shapes (each finding names one):", ""]

    for k, (title, text) in SHAPES.items():
        lines.append("  %-9s %s" % (k, title))

        words = text.split()

        line = "            "

        for w in words:
            if len(line) + len(w) + 1 > 116:
                lines.append(line.rstrip())
                line = "            "

            line += w + " "

        lines.append(line.rstrip())
        lines.append("")

    lines.append("confidence: high = the shape is plainly there and nothing beside it makes up for it; medium = there, "
                 "but")
    lines.append("            a reading of the code may show it is fine; low = listed for the record (a guard on a "
                 "flag,")
    lines.append("            a catch that rethrows).  A candidate is a reason to read the claim, not a verdict.")

    return "\n".join(lines)


def main(argv):
    here = os.path.dirname(os.path.abspath(__file__))

    parser = argparse.ArgumentParser(
        prog="hollow-scan.py",
        description="Flag test claims that look like they test something but may not (FR-136 Phase 1b).  "
                    "Static: nothing is compiled or run.",
        epilog=shape_help(),
        formatter_class=argparse.RawDescriptionHelpFormatter)

    parser.add_argument("paths", nargs="*",
                        help="test files or folders to report on (default: all of test/); every test file is still "
                             "read, so helpers in other files are known")
    parser.add_argument("--root", default=os.path.normpath(os.path.join(here, "..", "..")),
                        help="the project root (default: two folders above this script)")
    parser.add_argument("--src", help="the source folder (default: <root>/src)")
    parser.add_argument("--test", help="the test folder (default: <root>/test)")
    parser.add_argument("--json", action="store_true", help="print the findings as JSON")
    parser.add_argument("--summary", action="store_true", help="print counts per shape, confidence and folder only")
    parser.add_argument("--shape", action="append", choices=list(SHAPES.keys()),
                        help="report only this shape (repeatable)")
    parser.add_argument("--min", choices=CONFIDENCES, default="low", help="lowest confidence to report (default low)")

    args = parser.parse_args(argv)

    root = os.path.abspath(args.root)

    src = os.path.abspath(args.src or os.path.join(root, "src"))

    test = os.path.abspath(args.test or os.path.join(root, "test"))

    if not os.path.isdir(src) or not os.path.isdir(test):
        sys.stderr.write("no src/ or test/ under %s (use --root, --src, --test)\n" % root)

        return 2

    only = []

    for p in args.paths:
        full = os.path.abspath(p)

        only.append(os.path.relpath(full, root).replace(os.sep, '/') if full.startswith(root) else full)

    started = time.time()

    scanner = Scanner(root, src, test)

    scanner.run(only)

    floor = CONFIDENCES.index(args.min)

    findings = [f for f in scanner.findings
                if CONFIDENCES.index(f["confidence"]) >= floor and (not args.shape or f["shape"] in args.shape)]

    elapsed = time.time() - started

    if args.json:
        json.dump({"root": root, "seconds": round(elapsed, 2), "count": len(findings), "findings": findings},
                  sys.stdout, indent=1)
        sys.stdout.write("\n")

        return 0

    if not args.summary:
        last = None

        for f in findings:
            if f["file"] != last:
                sys.stdout.write("\n%s\n" % f["file"])
                last = f["file"]

            sys.stdout.write("  %5d  %-8s %-6s %s: %s\n         %s\n" % (
                f["line"], f["shape"], f["confidence"], f["method"], f["note"], f["excerpt"]))

    sys.stdout.write("\n%d candidate(s) in %.1f s\n" % (len(findings), elapsed))

    counts = OrderedDict()

    for f in findings:
        folder = f["file"].split('/')[1] if f["file"].count('/') >= 2 else ''

        counts.setdefault(f["shape"], OrderedDict())
        counts[f["shape"]].setdefault(folder, [0, 0, 0])
        counts[f["shape"]][folder][CONFIDENCES.index(f["confidence"])] += 1

    sys.stdout.write("%-9s %-11s %5s %6s %4s\n" % ("shape", "folder", "high", "medium", "low"))

    for shape in SHAPES:
        for folder, (lo, me, hi) in counts.get(shape, {}).items():
            sys.stdout.write("%-9s %-11s %5d %6d %4d\n" % (shape, folder, hi, me, lo))

    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
