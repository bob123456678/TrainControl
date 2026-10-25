#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
action-inventory.py: what the user does, what happens to the state, and which tests exercise each (FR-136 Phase 6).

Adam (2026-10-19): coverage "meaningfully, through actions the user would take"; "Don't limit this to UI
interactions, it can also be state outcomes".  A static read of src/ and test/: it runs nothing, and JaCoCo is not
used.  Run it from anywhere; the project root defaults to the folder two above this file.

    python docs/tools/action-inventory.py                      every row, one line each
    python docs/tools/action-inventory.py --summary            totals per list and area, then the ranked gaps
    python docs/tools/action-inventory.py --json               every row with its evidence, as JSON
    python docs/tools/action-inventory.py --entry backupData   rows whose label, member or names match, with evidence
    python docs/tools/action-inventory.py --list state --area routes

THE TWO LISTS

  user   What the user does.  One row per:
         - component with a handler: every NetBeans .form component's EventHandler (initComponents), and every
           listener added by hand (addActionListener, addMouseListener, ...) in gui/; components that share one
           handler (the 64 keyboard switch buttons) are one row;
         - item built by a factory (item(I18n.t("key"), () -> ...)): one row per call site;
         - tab (JTabbedPaneConstraints in a .form, addTab in code);
         - keyboard shortcut: each if/else-if branch or case on KeyEvent.VK_* in a key handler;
         - diagram or panel gesture: each mouseClicked/Pressed/Released/Dragged/WheelMoved method of a mouse
           listener (Entered/Exited/Moved are listed as kind "hover");
         - dialog: each JOptionPane.showConfirm/Option/InputDialog call ("dialog"), and showMessageDialog ("notice").
  state  What happens to the state, whoever starts it.  One row per (starting event, outcome that event should
         change): eleven events (a route runs, an autonomy run, a timetable, a return home, a feedback, an echo or
         packet from the railway, an import, a save and a reload, a restart, a power cut, a layout switch) and six
         outcomes (where trains stand and face, claimed track, switch and signal positions, the saved setup and
         locomotive database, statistics, the commands sent to the railway); EVENTS lists which pairs are rows.

HOW A TEST IS CLASSIFIED

  A "test" is a @Test or @Before* method together with the test-side helpers it calls, three calls deep (unqualified
  calls resolve in its own class, Class.method() calls in that test or support class).  Comments are ignored; a
  String constant of the test class (static final String MAXIMA = "key") counts where its name is used.  A test with
  no assert/fail anywhere in that closure counts for nothing ("did not throw" is not an outcome).

  as-the-user     (list user) the test names the component AND drives a real one the way Swing delivers it:
                  doClick (also ::doClick), a listener from getActionListeners/getMouseListeners, dispatchEvent or
                  postEvent of a MouseEvent or KeyEvent, setSelectedIndex/Item/Component (combo boxes and tabs).
                  Named means: its field name or setName("x") name as a string, or its getter getName() - with the
                  owning class named too when another class has a component of that name - or an I18n key of its
                  text or tooltip in the same method that drives, or in a method that hands the component back.
                  A handler on mouseReleased/Clicked/Pressed needs a dispatched MouseEvent of that kind (doClick does
                  not fire it); a shortcut needs a dispatched KeyEvent with its VK code and modifiers, in a test that
                  names the listening class.  A dialog counts when its message key is named and the test answers a
                  real dialog (finds the window and presses its button, or sets the JOptionPane's value).
  as-the-railway  (list state) the test starts the event as the program meets it - the public API (execRoute,
                  executePath, executeTimetable, planReturnToHome, setFeedbackState, receiveMessage, importLegacy,
                  syncWithCS2, saveState or a save then a new session, MarklinControlStation.stop, ...), a packet
                  through receiveMessage, DEBUG_SIMULATE_PACKETS or CS3TestServer, or a pressed component (the
                  Return Home, Start, timetable buttons) - AND reads that outcome (the getters OUTCOMES lists).
                  A restart is two models or windows built in one test, a window disposed and built again, or the
                  window's restoreState().
  by-handler      the test calls the handler, or what the listener calls (a method declared in one source file
                  only), directly or by reflection (getDeclaredMethod("xActionPerformed") then invoke); or names a
                  dialog's message and answers it through a test hook (answerForTests, updateQuestionForTests) or
                  not at all; or calls the one door that asks a question and answers a dialog.  A shortcut is
                  by-handler when its VK code is handed to the key handler by reflection.  For list state: the event
                  is started only through a door named by reflection.
  none            no test does either.

  A row's "how" is the best over its tests.  Every credit carries its evidence (--entry or --json): the token that
  named the entry and the way it was driven.  A key on more than two rows (a dialog title such as "Please confirm")
  or a plain button word ("Cancel", "OK") names nothing.

FREQUENCY AND SIZE (for ranking the gaps)

  freq   my judgement, from the label and member: 3 = every session (speed, direction, functions, the keyboard
         and its switch buttons, power, routes, the diagram, autonomy start/stop, Return Home); 1 = setup or rare
         (import, export, backup, update, preferences, debug, layout files, about); 2 = everything else (editing).
  lines  the handler's own lines plus the methods it calls, two calls deep, as far as a static read can resolve a
         name (a name declared in more than three source files, or a common one like run/get/repaint, is not
         followed).  Over-counts overloads; under-counts calls through interfaces, lambdas stored elsewhere, and
         reflection.
  rank   gaps ("none" and "by-handler" only) by freq, then "none" before "by-handler", then lines; shortcuts that
         differ only in their key (F1..F24) rank once, as a family.
  Tier 1 the five TrainControlUI clusters docs/refactor-3.0.3.md section 2 extracts first (CLUSTERS): a row belongs
         to one when its handler calls a cluster member, directly, through a thin handler or two calls deep; a
         dialog belongs to its door's cluster.

WHERE IT IS WEAK (read a row's evidence before trusting it)

  - A test that drives one component and names another by field name (a menu label in an assertion message) credits
    both; a component found by position (getComponent(1)) or by walking a menu is not named, and is missed.
  - Shared message keys (a dialog text used by two doors) credit both doors.
  - by-handler through "what it calls" credits a test that calls that method for its own reasons, and does not tell
    the arguments apart (Enable and Disable both call enableOrDisableRoute).
  - Gestures on a component that is the listener (a diagram square) are matched by event type and the class's name,
    not by the square or the tool in hand.
  - Keyboard shortcuts are matched by VK code and modifiers; the window the event went to is not checked.
  - Factory menus built from data (one item per locomotive, per page) are one row, not one per item.
  - State outcomes are recognised by the getters a test calls: one that reads the state through a helper of another
    name is missed, and one that calls a getter for a precondition, or after an event it started for setup (a power
    cut, a route), is credited.  An event is credited to every outcome its test reads, whichever event caused it.
  - Notices (showMessageDialog) are counted only when a test names their message; most are error paths.

Examples, from the project root:
  python docs/tools/action-inventory.py --summary
  python docs/tools/action-inventory.py --gaps 40 --area layout
  python docs/tools/action-inventory.py --entry "returnHome" --list user
"""

import argparse
import bisect
import collections
import json
import os
import re
import sys
import time
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_ROOT = os.path.dirname(os.path.dirname(HERE))

AREAS = collections.OrderedDict([
    ('main', 'the main window and locomotives'),
    ('layout', 'the layout editor and diagram'),
    ('autonomy', 'the autonomy editor and its runs'),
    ('routes', 'routes, timetable and the railway\'s own events'),
])

JAVA_KEYWORDS = set('''if for while switch catch synchronized return new throw else do try finally case default
super this assert instanceof'''.split())

# Names too common to follow when counting reach or crediting a by-handler call.
COMMON = set('''run get set add remove put contains size isEmpty equals hashCode toString valueOf format t f
repaint revalidate validate dispose setVisible isVisible setEnabled isEnabled setText getText setToolTipText
requestFocus requestFocusInWindow invokeLater invokeAndWait printStackTrace getMessage println print log logf
getName getModel getSource getX getY getButton getKeyCode getComponent getParent getWidth getHeight setSize
append length charAt substring indexOf trim split replace replaceAll matches startsWith endsWith isSelected
setSelected getSelectedIndex getSelectedItem setSelectedIndex setSelectedItem iterator next hasNext keySet values
entrySet stream map filter collect forEach addAll clear sort max min abs round parseInt toUpperCase toLowerCase
getString getInt getBoolean getJSONObject getJSONArray has opt optString optInt optBoolean show pack
setLocationRelativeTo getContentPane add addSeparator addActionListener addMouseListener start join sleep wait
notify notifyAll interrupt currentTimeMillis nanoTime arraycopy asList emptyList singletonList unmodifiableList
newArrayList ofNullable orElse isPresent getKey getValue apply accept test call getClass getSimpleName cast
setLayout setBorder setBackground setForeground setFont getFont setIcon setPreferredSize getPreferredSize
update paint paintComponent getGraphics init stop go exec delay getPoint consume getValueAt convertMouseEvent
convertPoint dispatchEvent getWheelRotation getPreciseWheelRotation isPopupTrigger getClickCount getModifiers
getRowCount getColumnCount rowAtPoint columnAtPoint locationToIndex getSelectedValue getLocationOnScreen
getBounds contains setCursor getCursor isShowing getTopLevelAncestor getWindowAncestor setValue getValue
isDisplayable doClick fireTableDataChanged getTile getPage getKeyChar getID getWhen getText'''.split())

STRIP = re.compile(r'//[^\n]*|/\*.*?\*/|"(?:\\.|[^"\\\n])*"|\'(?:\\.|[^\'\\\n])+\'', re.S)
CALL = re.compile(r'(?<![\w.$])(?:new\s+)?([A-Za-z_]\w*)\s*\(|\.\s*([A-Za-z_]\w*)\s*\(|::\s*([A-Za-z_]\w*)')
QUALIFIED = re.compile(r'\b([A-Z]\w*)\s*\.\s*([a-z_]\w*)\s*\(')

UNESCAPE = re.compile(r'\\u([0-9a-fA-F]{4})')


# --------------------------------------------------------------------------------------------------------------------
# Reading Java
# --------------------------------------------------------------------------------------------------------------------

class JavaFile(object):
    """A source file with comments blanked and string contents blanked (positions kept), its literals, its methods."""

    def __init__(self, path, root):
        self.path = path
        self.rel = os.path.relpath(path, root).replace(os.sep, '/')
        with open(path, encoding='utf-8', errors='replace') as f:
            self.text = f.read().replace('\r\n', '\n')
        self.literals = []          # (pos, value)
        self.code = STRIP.sub(self._blank, self.text)
        self.lit_pos = [p for p, _ in self.literals]
        self.newlines = [i for i, c in enumerate(self.text) if c == '\n']
        self.cls = os.path.basename(path)[:-5]
        self.methods = []           # dicts: name, cls, start, body_start, end, header, line, end_line
        self._scan()
        self.method_starts = [m['start'] for m in self.methods]

    def _blank(self, m):
        s = m.group(0)
        if s.startswith('"'):
            self.literals.append((m.start(), s[1:-1]))
            return '"' + ' ' * (len(s) - 2) + '"'
        if s.startswith("'"):
            return "'" + ' ' * (len(s) - 2) + "'"
        return re.sub(r'[^\n]', ' ', s)

    def line(self, pos):
        return bisect.bisect_left(self.newlines, pos) + 1

    def literals_in(self, lo, hi):
        i = bisect.bisect_left(self.lit_pos, lo)
        out = []
        while i < len(self.literals) and self.literals[i][0] < hi:
            out.append(self.literals[i][1])
            i += 1
        return out

    def _scan(self):
        code = self.code
        stack = []        # (kind, name, open_pos, header_start)
        last = 0
        for m in re.finditer(r'[{};]', code):
            c = m.group(0)
            pos = m.start()
            if c == '{':
                header = code[last:pos]
                kind, name = 'block', None
                top = stack[-1][0] if stack else 'file'
                cm = re.search(r'\b(class|interface|enum)\s+([A-Za-z_]\w*)', header)
                if cm and not re.search(r'\bnew\s', header) and '->' not in header:
                    kind, name = 'class', cm.group(2)
                elif top in ('class', 'file') and not re.search(r'\bnew\s', header):
                    mm = re.search(r'([A-Za-z_]\w*)\s*\(([^()]*(?:\([^()]*\)[^()]*)*)\)\s*(?:throws\s+[\w.\s,]+)?\s*$',
                                   header)
                    if mm and mm.group(1) not in JAVA_KEYWORDS:
                        kind, name = 'method', mm.group(1)
                elif re.search(r'\bnew\s', header) and top in ('class',):
                    kind = 'anon'
                stack.append((kind, name, pos, last, header))
                last = pos + 1
            elif c == '}':
                if stack:
                    kind, name, open_pos, header_start, header = stack.pop()
                    if kind == 'method':
                        owner = next((s[1] for s in reversed(stack) if s[0] == 'class'), self.cls)
                        name_at = header_start + header.rfind(name + '(') if (name + '(') in header else open_pos
                        if name_at < header_start:
                            name_at = open_pos
                        self.methods.append({'name': name, 'cls': owner, 'start': header_start,
                                             'body_start': open_pos, 'end': pos + 1, 'header': header,
                                             'line': self.line(name_at), 'end_line': self.line(pos)})
                last = pos + 1
            else:
                last = pos + 1
        self.methods.sort(key=lambda d: d['start'])

    def method_at(self, pos):
        """The innermost class-level method whose body holds pos."""
        best = None
        i = bisect.bisect_right(self.method_starts, pos) - 1
        while i >= 0:
            m = self.methods[i]
            if m['body_start'] <= pos < m['end']:
                if best is None or m['body_start'] > best['body_start']:
                    best = m
            if m['end'] < pos and best is not None:
                break
            i -= 1
            if pos - m['start'] > 400000:
                break
        return best


def balanced(code, open_pos):
    """Index just past the bracket matching code[open_pos] ('(' or '{')."""
    pairs = {'(': ')', '{': '}', '[': ']'}
    opener = code[open_pos]
    closer = pairs[opener]
    depth = 0
    i = open_pos
    n = len(code)
    while i < n:
        c = code[i]
        if c == opener:
            depth += 1
        elif c == closer:
            depth -= 1
            if depth == 0:
                return i + 1
        i += 1
    return n


def calls_of(code):
    out = set()
    for m in CALL.finditer(code):
        name = m.group(1) or m.group(2) or m.group(3)
        if name and name not in JAVA_KEYWORDS:
            out.add(name)
    return out


def keys_of(jf, lo, hi):
    """I18n keys named between lo and hi (the literal that follows I18n.t( / I18n.f( / getString()."""
    keys = []
    for m in re.finditer(r'(?:I18n\s*\.\s*[tf]|getString|countForm)\s*\(\s*"', jf.code[lo:hi]):
        at = lo + m.end() - 1
        i = bisect.bisect_left(jf.lit_pos, at)
        if i < len(jf.literals) and jf.literals[i][0] == at:
            keys.append(jf.literals[i][1])
    return keys


def java_files(folder):
    for top, dirs, files in os.walk(folder):
        dirs.sort()
        for name in sorted(files):
            if name.endswith('.java'):
                yield os.path.join(top, name)


def load_bundle(root):
    path = os.path.join(root, 'src', 'org', 'traincontrol', 'resources', 'messages.properties')
    out = {}
    if not os.path.isfile(path):
        return out
    with open(path, encoding='latin-1') as f:
        lines = f.read().split('\n')
    i = 0
    while i < len(lines):
        line = lines[i].rstrip('\r')
        while line.endswith('\\') and i + 1 < len(lines):
            i += 1
            line = line[:-1] + lines[i].strip()
        i += 1
        if not line.strip() or line.lstrip().startswith('#') or '=' not in line:
            continue
        k, v = line.split('=', 1)
        out[k.strip()] = UNESCAPE.sub(lambda m: chr(int(m.group(1), 16)), v.strip())
    return out


# --------------------------------------------------------------------------------------------------------------------
# The program: methods, reach
# --------------------------------------------------------------------------------------------------------------------

class Program(object):
    def __init__(self, root):
        self.files = {}
        for path in java_files(os.path.join(root, 'src')):
            jf = JavaFile(path, root)
            self.files[jf.rel] = jf
        self.by_name = collections.defaultdict(list)    # name -> [(rel, method)]
        for rel, jf in self.files.items():
            for m in jf.methods:
                m['calls'] = calls_of(jf.code[m['body_start']:m['end']])
                m['lines'] = m['end_line'] - jf.line(m['start'] + len(m['header']) - len(m['header'].lstrip())) + 1
                self.by_name[m['name']].append((rel, m))
        self.files_declaring = {n: len(set(r for r, _ in v)) for n, v in self.by_name.items()}

    def distinctive(self, name):
        return name in self.by_name and name not in COMMON and self.files_declaring.get(name, 9) <= 3

    def resolve(self, name, rel):
        if name in COMMON or name not in self.by_name:
            return []
        same = [(r, m) for r, m in self.by_name[name] if r == rel]
        if same:
            return same
        if self.files_declaring[name] <= 3:
            return self.by_name[name]
        return []

    def find(self, name, prefer=None):
        """(rel, method) for a declared name, preferring a file whose path ends with prefer."""
        cands = self.by_name.get(name, [])
        if prefer:
            pref = [c for c in cands if c[0].endswith(prefer)]
            if pref:
                return pref[0]
        return cands[0] if cands else None

    def reach(self, calls, rel, depth=2):
        """Unique methods reached from a call set, depth calls deep; returns {(rel, name, line): lines}."""
        seen = {}
        frontier = [(c, rel) for c in calls]
        for _ in range(depth):
            nxt = []
            for name, at in frontier:
                for r, m in self.resolve(name, at):
                    key = (r, m['name'], m['line'])
                    if key in seen:
                        continue
                    seen[key] = m['lines']
                    nxt.extend((c, r) for c in m['calls'])
            frontier = nxt
        return seen


# --------------------------------------------------------------------------------------------------------------------
# The tests: per-method features, closures
# --------------------------------------------------------------------------------------------------------------------

DRIVE_CLICK = re.compile(r'\.doClick\s*\(|::\s*doClick\b|getActionListeners\s*\(\s*\)|\.setSelectedIndex\s*\(|\.setSelectedItem\s*\('
                         r'|\.setSelectedComponent\s*\(|getAction\s*\(\s*\)\s*\.\s*actionPerformed|\.postEvent\s*\(')
DISPATCH = re.compile(r'\.dispatchEvent\s*\(|getMouseListeners\s*\(\s*\)|getKeyListeners\s*\(\s*\)'
                      r'|getMouseMotionListeners\s*\(\s*\)|\.postEvent\s*\(|dispatchKeyEvent\s*\(')


def vks_in(body):
    """VK codes a method names, with a loop from VK_A to VK_Z (or VK_0 to VK_9) read as every code between."""
    found = set(VK.findall(body))
    for lo, hi in (('A', 'Z'), ('0', '9')):
        if lo in found and hi in found and re.search(r'VK_' + lo + r'\s*;[^;]*<=?\s*[\w.]*VK_' + hi, body):
            found |= set(chr(c) for c in range(ord(lo), ord(hi) + 1))
    return found
MOUSE_ID = re.compile(r'MouseEvent\s*\.\s*MOUSE_(\w+)|\.mouse(Clicked|Pressed|Released|Dragged|Moved|Entered|Exited)\s*\(')
VK = re.compile(r'\bVK_(\w+)')
MODS = re.compile(r'\b(CTRL|ALT|SHIFT|META)_(?:DOWN_)?MASK\b')
REFLECT = re.compile(r'\.\s*(?:getDeclaredMethod|getMethod)\s*\(')
INVOKE = re.compile(r'\.invoke\s*\(')
ASSERT = re.compile(r'\b(?:assert\w*|fail|expectThrows)\s*\(')
ANSWER_REAL = re.compile(r'getWindows\s*\(\s*\)|getOwnedWindows\s*\(\s*\)|instanceof\s+(?:javax\.swing\.)?'
                         r'(?:JOptionPane|JDialog)\b|JOptionPane\s*\.\s*class|OptionPane\s*\.\s*\w+ButtonText|'
                         r'getRootPane\s*\(\s*\)\s*\.\s*getDefaultButton')
ANSWER_HOOK = re.compile(r'\banswer\w*ForTests\s*\(|\bupdateQuestionForTests\s*\(')
ANSWERS = [(re.compile(r'answeringYes|answeringNamesAndYes|yesButtonText|YES_OPTION'), 'yes'),
           (re.compile(r'NO_OPTION|noButtonText|message\s*->\s*1\b'), 'no'),
           (re.compile(r'CANCEL_OPTION|cancelButtonText|message\s*->\s*2\b'), 'cancel'),
           (re.compile(r'CLOSED_OPTION|WINDOW_CLOSING'), 'closed'),
           (re.compile(r'OK_OPTION|okButtonText|message\s*->\s*0\b'), 'ok/first')]
RESTART_MODEL = re.compile(r'\bMarklinControlStation\s*\.\s*init\s*\(|(?<![\w.])init\s*\(\s*null')
RESTART_WINDOW = re.compile(r'new\s+TrainControlUI\s*\(')


class Tests(object):
    def __init__(self, root, program):
        self.files = {}
        self.methods = {}                         # id -> features
        self.by_class = collections.defaultdict(lambda: collections.defaultdict(list))
        self.roots = []
        for path in java_files(os.path.join(root, 'test')):
            jf = JavaFile(path, root)
            self.files[jf.rel] = jf
            # a String constant of the test class (static final String MAXIMA = "key") is read where its name is used
            constants = {}
            for cm in re.finditer(r'\bfinal\s+(?:static\s+)?String\s+([A-Z_][A-Z0-9_]*)\s*=\s*"', jf.code):
                found = jf.literals_in(cm.end() - 1, cm.end())
                if found:
                    constants[cm.group(1)] = found[0]
            for m in jf.methods:
                body = jf.code[m['body_start']:m['end']]
                mid = '%s#%s:%d' % (jf.rel, m['name'], m['line'])
                lits = set(jf.literals_in(m['body_start'], m['end']))
                for name in set(re.findall(r'\b([A-Z_][A-Z0-9_]*)\b', body)) & set(constants):
                    lits.add(constants[name])
                f = {
                    'id': mid, 'rel': jf.rel, 'cls': jf.cls, 'name': m['name'], 'line': m['line'],
                    'lits': lits,
                    'calls': calls_of(body),
                    'qcalls': set(QUALIFIED.findall(body)),
                    'click': bool(DRIVE_CLICK.search(body)),
                    'dispatch': bool(DISPATCH.search(body)),
                    'returns_component': bool(re.search(r'\b(?:J[A-Z]\w*|AbstractButton|Component|MenuElement)\s+'
                                                        + re.escape(m['name']) + r'\s*\(', m['header'])),
                    'mouse': set(a or b.upper() for a, b in MOUSE_ID.findall(body)),
                    'mouse_event': 'MouseEvent' in body or 'MouseWheelEvent' in body,
                    'key_event': 'KeyEvent' in body,
                    'vk': vks_in(body),
                    'mods': set(MODS.findall(body)),
                    'reflect': bool(REFLECT.search(body)) and bool(INVOKE.search(body)),
                    'asserts': len(ASSERT.findall(body)),
                    'answer_real': bool(ANSWER_REAL.search(body)),
                    'answer_hook': bool(ANSWER_HOOK.search(body)),
                    'answers': set(a for rx, a in ANSWERS if rx.search(body)),
                    'restarts': (len(RESTART_MODEL.findall(body)), len(RESTART_WINDOW.findall(body))),
                    'types': set(re.findall(r'\b([A-Z][A-Za-z0-9_]+)\b', body)),
                    'code': body,
                }
                for e in EVENTS:
                    f.setdefault('starts', {})
                    how = None
                    for rx, kind in e['starters']:
                        if re.search(rx, body):
                            how = best_how(how, kind)
                    if how:
                        f['starts'][e['id']] = how
                f['checks'] = set(o['id'] for o in OUTCOMES if re.search(o['rx'], body))
                self.methods[mid] = f
                self.by_class[jf.cls][m['name']].append(mid)
                if re.search(r'@\s*(?:Test|BeforeClass|BeforeMethod|BeforeTest|BeforeSuite)\b', m['header']):
                    self.roots.append(mid)
        self.closures = {}
        for r in self.roots:
            self.closures[r] = self.closure(r)

    def callees(self, mid):
        f = self.methods[mid]
        out = []
        own = self.by_class[f['cls']]
        for c in f['calls']:
            if c in own:
                out.extend(own[c])
        for cls, name in f['qcalls']:
            if cls in self.by_class and name in self.by_class[cls]:
                out.extend(self.by_class[cls][name])
        return out

    def closure(self, root, depth=3):
        members = [root]
        seen = {root}
        frontier = [root]
        for _ in range(depth):
            nxt = []
            for mid in frontier:
                for c in self.callees(mid):
                    if c not in seen:
                        seen.add(c)
                        nxt.append(c)
                        members.append(c)
            frontier = nxt
        fs = [self.methods[m] for m in members]
        cl = {
            'id': root, 'members': members,
            'lits': set().union(*[f['lits'] for f in fs]),
            'calls': set().union(*[f['calls'] for f in fs]),
            'click': any(f['click'] for f in fs),
            'selects_tab': any(re.search(r'\.setSelected(?:Index|Component)\s*\(', f['code']) for f in fs),
            'dispatch': any(f['dispatch'] for f in fs),
            'mouse': set().union(*[f['mouse'] for f in fs]),
            'mouse_drive': any(f['dispatch'] and f['mouse_event'] for f in fs),
            'key_drive': any(f['dispatch'] and f['key_event'] for f in fs),
            'key_handler': any(f['key_event'] and not f['dispatch'] for f in fs),
            'vk': set().union(*[f['vk'] for f in fs]),
            'mods': set().union(*[f['mods'] for f in fs]),
            'reflect': any(f['reflect'] for f in fs),
            'asserts': sum(f['asserts'] for f in fs),
            'answer_real': any(f['answer_real'] for f in fs),
            'answer_hook': any(f['answer_hook'] for f in fs),
            'answers': set().union(*[f['answers'] for f in fs]),
            'restarts': max(sum(f['restarts'][0] for f in fs), sum(f['restarts'][1] for f in fs)),
            'types': set().union(*[f['types'] for f in fs]),
            'starts': {},
            'checks': set().union(*[f['checks'] for f in fs]),
        }
        for f in fs:
            for e, how in f['starts'].items():
                cl['starts'][e] = best_how(cl['starts'].get(e), how)
        if cl['restarts'] >= 2:
            cl['starts']['restart'] = best_how(cl['starts'].get('restart'), 'railway')
        # a component or door named as a string: pressed (railway, as the user starts it) or reflected (handler)
        for e in EVENTS:
            for lit, kind in e.get('literals', []):
                if lit not in cl['lits']:
                    continue
                if kind == 'drive' and (cl['click'] or cl['dispatch']):
                    cl['starts'][e['id']] = best_how(cl['starts'].get(e['id']), 'railway')
                elif kind == 'reflect' and cl['reflect']:
                    cl['starts'][e['id']] = best_how(cl['starts'].get(e['id']), 'handler')
        return cl

    def label(self, root):
        f = self.methods[root]
        return '%s.%s' % (f['cls'], f['name'])


HOW_ORDER = {None: 0, 'none': 0, 'handler': 1, 'by-handler': 1, 'railway': 2, 'user': 2, 'as-the-user': 2,
             'as-the-railway': 2}


def best_how(a, b):
    return b if HOW_ORDER.get(b, 0) > HOW_ORDER.get(a, 0) else a


# --------------------------------------------------------------------------------------------------------------------
# List 2: events and outcomes
# --------------------------------------------------------------------------------------------------------------------

# starters: (regex on a test method's code, 'railway' | 'handler'); literals: (string, 'drive' | 'reflect')
EVENTS = [
    {'id': 'route', 'outcomes': ['switches', 'trains', 'wire'], 'label': 'a route runs', 'area': 'routes',
     'members': [('execRoute', 'MarklinControlStation.java'), ('execRoute', 'MarklinRoute.java'),
                 ('executeRoute', 'TrainControlUI.java')],
     'starters': [(r'\.execRoute\s*\(', 'railway'), (r'\.executeRoute\s*\(', 'handler')],
     'literals': [('executeRoute', 'reflect'), ('routeList', 'drive'), ('RoutesTable', 'drive')]},
    {'id': 'autonomy', 'outcomes': ['trains', 'claimed', 'switches', 'saved', 'stats', 'wire'], 'label': 'an autonomy run', 'area': 'autonomy',
     'members': [('executePath', 'Layout.java'), ('runLocomotives', 'Layout.java'),
                 ('configureAndLockPath', 'Layout.java')],
     'starters': [(r'\.executePath(?:ByHand)?\s*\(|\.runLocomotives?\s*\(|\.configureAndLockPath\s*\(', 'railway')],
     'literals': [('startAutonomy', 'reflect'), ('startAutonomy', 'drive'), ('dispatch', 'reflect')]},
    {'id': 'timetable', 'outcomes': ['trains', 'claimed', 'switches', 'saved', 'stats'], 'label': 'a timetable', 'area': 'routes',
     'members': [('executeTimetable', 'Layout.java'), ('resetTimetable', 'Layout.java')],
     'starters': [(r'\.executeTimetable\s*\(', 'railway')],
     'literals': [('timetableStop', 'reflect'), ('executeTimetable', 'reflect'), ('timetableStop', 'drive'),
                  ('executeTimetable', 'drive')]},
    {'id': 'home', 'outcomes': ['trains', 'claimed', 'switches', 'saved'], 'label': 'a return home', 'area': 'autonomy',
     'members': [('requestReturnToHome', 'TrainControlUI.java'), ('planReturnToHome', 'Layout.java'),
                 ('loadReturnToHomeTimetable', 'Layout.java')],
     'starters': [(r'\.planReturnToHome\s*\(|\.loadReturnToHomeTimetable\s*\(|\.triageReturnToHome\s*\(', 'railway'),
                  (r'\.requestReturnToHome\s*\(', 'handler')],
     'literals': [('returnHomeButton', 'drive'), ('requestReturnToHome', 'reflect')]},
    {'id': 'feedback', 'outcomes': ['trains', 'claimed', 'switches', 'wire'], 'label': 'a feedback (s88) from the railway', 'area': 'routes',
     'members': [('setFeedbackState', 'MarklinControlStation.java'), ('feedbackChanged', 'MarklinControlStation.java'),
                 ('feedbackChanged', 'TrainControlUI.java')],
     'starters': [(r'\.setFeedbackState\s*\(|\.feedbackChanged\s*\(|CMD_S88', 'railway')]},
    {'id': 'echo', 'outcomes': ['trains', 'switches', 'stats'], 'label': 'an echo or packet from the Central Station', 'area': 'routes',
     'members': [('receiveMessage', 'MarklinControlStation.java')],
     'starters': [(r'\.receiveMessage\s*\(|DEBUG_SIMULATE_PACKETS|CS3TestServer', 'railway')]},
    {'id': 'import', 'outcomes': ['trains', 'claimed', 'saved'], 'label': 'an import', 'area': 'autonomy',
     'members': [('importLegacy', 'AutonomySession.java'), ('importBundle', 'AutonomySession.java'),
                 ('importRoutes', 'MarklinControlStation.java'), ('syncWithCS2', 'MarklinControlStation.java')],
     'starters': [(r'\.importLegacy\s*\(|\.importBundle\s*\(|\.importRoutes\s*\(|\.syncWithCS2\s*\(', 'railway')],
     'literals': [('importLegacy', 'reflect'), ('importBundle', 'reflect')]},
    {'id': 'save', 'outcomes': ['trains', 'saved', 'stats'], 'label': 'a save and a reload', 'area': 'main',
     'members': [('saveState', 'TrainControlUI.java'), ('saveState', 'MarklinControlStation.java'),
                 ('restoreState', 'MarklinControlStation.java')],
     'starters': [(r'\.saveState\s*\(', 'railway'),
                  (r'\.save(?:Quietly|WithoutReconciling)?\s*\(\s*\)[\s\S]*(?:new\s+(?:[\w.]+\.)?AutonomySession\s*\('
                   r'|\.load\s*\(\s*\)|parseAuto\s*\()', 'railway')],
     'literals': [('saveState', 'reflect'), ('backupData', 'reflect')]},
    {'id': 'restart', 'outcomes': ['trains', 'claimed', 'switches', 'saved', 'stats'], 'label': 'a restart', 'area': 'main',
     'members': [('init', 'MarklinControlStation.java'), ('restoreState', 'TrainControlUI.java')],
     # also: two constructions of the model or window in one closure, or a window disposed and built again (Tests)
     'starters': [(r'\.restoreState\s*\(\s*\)', 'railway'),
                  (r'\.dispose\s*\([\s\S]*new\s+TrainControlUI\s*\(', 'railway')]},
    {'id': 'power', 'outcomes': ['trains', 'claimed', 'wire'], 'label': 'a power cut', 'area': 'routes',
     'members': [('stop', 'MarklinControlStation.java'), ('setPowerState', 'MarklinControlStation.java'),
                 ('waitWhileThePowerIsOff', 'Layout.java'), ('updatePowerState', 'TrainControlUI.java')],
     'starters': [(r'\b(?:model|getModel\s*\(\s*\)|control|station|cs)\s*\.\s*stop\s*\(\s*\)', 'railway'),
                  (r'receiveMessage\s*\([\s\S]*CMD_SYSTEM|CMD_SYSTEM[\s\S]*receiveMessage\s*\(', 'railway')],
     'literals': [('setPowerState', 'reflect'), ('PowerOff', 'drive'), ('PowerOff', 'reflect')]},
    {'id': 'switch', 'outcomes': ['trains', 'claimed', 'saved'], 'label': 'a layout switch', 'area': 'layout',
     'members': [('switchCSLayoutMenuItemActionPerformed', 'TrainControlUI.java'),
                 ('chooseLocalDataFolderMenuItemActionPerformed', 'TrainControlUI.java'),
                 ('refreshLayouts', 'MarklinControlStation.java')],
     'starters': [(r'\.refreshLayouts\s*\(|\.clearLayouts\s*\(', 'railway')],
     'literals': [('switchCSLayoutMenuItem', 'drive'), ('chooseLocalDataFolderMenuItem', 'drive'),
                  ('initializeLocalLayoutMenuItem', 'drive'),
                  ('switchCSLayoutMenuItemActionPerformed', 'reflect'),
                  ('chooseLocalDataFolderMenuItemActionPerformed', 'reflect'),
                  ('initializeLocalLayoutMenuItemActionPerformed', 'reflect'), ('layoutFolderIs', 'reflect')]},
]

OUTCOMES = [
    {'id': 'trains', 'label': 'where trains stand and face',
     'rx': r'getCurrentLocomotive\s*\(|getLocomotiveLocation\s*\(|whereTheTrainIs\s*\(|goingForward\s*\(|'
           r'goingBackward\s*\(|getDirection\s*\(|getCopyFacing\s*\(|getArrivedFrom\s*\(|getArrivedAlong\s*\(|'
           r'getBlockLocomotive\s*\(|\bFACING\b|getLastPointsReached\s*\(|\b\w*[Ff]acing\w*\s*\('},
    {'id': 'claimed', 'label': 'claimed track',
     'rx': r'getLockEdges\s*\(|getPointsInActivePath\s*\(|holdsAPath\s*\(|isPathClear\s*\(|coveredTrack|'
           r'getActiveLocomotives\s*\(|isOccupied\s*\(|isFeedbackOccupied\s*\(|releasedBehind\s*\(|'
           r'getBlockedBy\s*\(|isActive\s*\(\s*\)'},
    {'id': 'switches', 'label': 'switch and signal positions',
     'rx': r'isSwitched\s*\(|isGreen\s*\(|isRed\s*\(|isStraight\s*\(|isTurned\s*\(|isThrow\s*\(|'
           r'getAccessoryState\w*\s*\(|getActiveAccSettings\s*\(|getNumActuations\s*\('},
    {'id': 'saved', 'label': 'the saved setup and locomotive database',
     'rx': r'LocDB\.data|autonomy\.json|UIState\.data|readAllBytes\s*\(|Files\s*\.\s*read\w*\s*\(|'
           r'FileInputStream|getConfiguration\s*\(|exportRoutes\s*\(|\.cs2"'},
    {'id': 'stats', 'label': 'statistics',
     'rx': r'getTotalRuntime\s*\(|getHistoricalOperatingTime\s*\(|getNumPaths\s*\(|getNumDaysRun\s*\(|'
           r'getRuntimeToday\s*\(|getDailyCountStats\s*\(|getDailyRuntimeStats\s*\(|getTotalLocStats\s*\(|'
           r'getVisitHistory\s*\(|getLastPathTime\s*\(|getRuntimeOnDay\s*\(|getReachedMilestones\s*\('},
    {'id': 'wire', 'label': 'commands sent to the railway',
     'rx': r'WireRecorder|setSentMessageObserver\s*\(|CS3TestServer|getNumMessagesProcessed\s*\(|CS2Message\s*\.\s*CMD_'},
]


# --------------------------------------------------------------------------------------------------------------------
# List 1: entries from the forms and the code
# --------------------------------------------------------------------------------------------------------------------

GEN_REGION = re.compile(r'//GEN-BEGIN:initComponents.*?//GEN-END:initComponents', re.S)
LISTENER = re.compile(r'(?:([A-Za-z_][\w.]*?)\s*\.\s*)?add(Action|Item|Mouse|MouseMotion|MouseWheel|Change|Key|'
                      r'ListSelection)Listener\s*\(')
CONSTRUCT = re.compile(r'(?:\b([A-Za-z_]\w*)\s*=\s*)?new\s+(?:javax\.swing\.)?(J(?:MenuItem|CheckBoxMenuItem|'
                       r'RadioButtonMenuItem|Button|ToggleButton|CheckBox|RadioButton|ComboBox|Menu|TabbedPane|Slider|'
                       r'List|Table|TextField))\b')
MOUSE_METHOD = re.compile(r'\bvoid\s+(mouse(?:Clicked|Pressed|Released|Dragged|WheelMoved|Moved|Entered|Exited)|'
                          r'actionPerformed|stateChanged|itemStateChanged|valueChanged|key(?:Pressed|Released|Typed))'
                          r'\s*\(')
DIALOG = re.compile(r'JOptionPane\s*\.\s*show(Confirm|Option|Input|Message|InternalConfirm|InternalMessage)Dialog\s*\(')
KIND_OF_CLASS = {'JMenuItem': 'menu item', 'JCheckBoxMenuItem': 'checkbox', 'JRadioButtonMenuItem': 'radio',
                 'JButton': 'button', 'JToggleButton': 'button', 'JCheckBox': 'checkbox', 'JRadioButton': 'radio',
                 'JComboBox': 'combo', 'JMenu': 'menu', 'JTabbedPane': 'tab', 'JSlider': 'slider', 'JList': 'list',
                 'JTable': 'list', 'JTextField': 'field', 'JTextArea': 'field', 'JLabel': 'gesture',
                 'JPanel': 'gesture', 'JScrollPane': 'gesture', 'JMenuBar': 'menu'}
EDITOR_FILES = ('LayoutEditor.java', 'AutonomyEditorPanel.java', 'RouteEditorFrame.java')

FILE_AREA = {
    'LayoutEditor': 'layout', 'LayoutEditorRightclickMenu': 'layout', 'LayoutEditorAddressPopup': 'layout',
    'LayoutLabel': 'layout', 'LayoutGrid': 'layout', 'LayoutPopupUI': 'layout', 'DiagramExport': 'layout',
    'HandScrollListener': 'layout',
    'StationCaption': 'layout', 'AxisRuler': 'layout',
    'AutonomyEditorPanel': 'autonomy', 'AutonomyViewerPanel': 'autonomy', 'AutonomyMenu': 'autonomy',
    'LayoutRightclickAutonomyMenu': 'autonomy', 'GraphLocAssign': 'autonomy', 'AutoJSONExport': 'autonomy',
    'AutoLocomotiveStatus': 'autonomy', 'AutonomyBanner': 'autonomy', 'AutonomyOverlayToggle': 'autonomy',
    'FacingPrompt': 'autonomy', 'ArrivalSidePrompt': 'autonomy', 'TailCrossedPrompt': 'autonomy',
    'ManualReversalPrompt': 'autonomy', 'HomeLocomotiveMenu': 'autonomy', 'AutonomyReport': 'autonomy',
    'DiagramMonitorDriver': 'autonomy',
    'RouteEditorFrame': 'routes', 'RightClickRouteMenu': 'routes', 'RightClickTimetableMenu': 'routes',
    'RouteRunningMarks': 'routes',
}


PATH_AREA = [(re.compile(r'[Aa]uto(?!matic)|[Gg]raph'), 'autonomy'), (re.compile(r'[Rr]oute|[Tt]imetable'), 'routes'),
             (re.compile(r'[Ll]ayout|[Dd]iagram'), 'layout')]


def area_for(jf_cls, text, path=()):
    if jf_cls in FILE_AREA:
        return FILE_AREA[jf_cls]
    if jf_cls != 'TrainControlUI':
        return 'main'
    for segment in list(path)[1:-1]:
        for rx, area in PATH_AREA:
            if rx.search(segment):
                return area
    t = text
    if re.search(r'[Rr]oute|[Tt]imetable|[Ff]eedback', t):
        return 'routes'
    if re.search(r'[Aa]uto(?!matic)|[Aa]utonomy|[Gg]raph|[Rr]eturn ?[Hh]ome|[Ss]taging|[Pp]ath|[Hh]ome[A-Z]', t):
        return 'autonomy'
    if re.search(r'[Ll]ayout|[Dd]iagram|[Pp]age|[Tt]ile|[Ee]ditor|[Cc]aption|[Ss]how[A-Z]\w*Number', t):
        return 'layout'
    return 'main'


FREQ_HIGH = re.compile(r'[Ss]peed|[Ss]lider|[Dd]irection|[Ff]orward|[Bb]ackward|[Rr]everse|[Ff]unction|UpdateSwitch|'
                       r'LetterButton|[Pp]ower|[Ss]top|[Ee]mergency|[Ee]xecute|[Rr]unRoute|[Pp]lay|[Ss]end|[Dd]ispatch|'
                       r'[Ss]tart[A-Z]|startAutonomy|[Rr]eturn ?[Hh]ome|[Mm]apping|[Kk]eyboard|Arrow|Spacebar|'
                       r'LayoutList|receiveClickEvent|[Pp]ause|^key |[Ss]witch[A-Z]?\w*State|[Tt]ab\b|'
                       r'[Gg]o[A-Z]|LocControlPanel|setLocomotive|[Pp]lace')
FREQ_LOW = re.compile(r'[Ii]mport|[Ee]xport|[Bb]ackup|[Uu]pdate[A-Z]?\w*(?:Check|Flow|Menu)|checkForUpdates|[Aa]bout|'
                      r'[Dd]ebug|[Dd]ocumentation|[Rr]eleases|[Pp]reference|[Ss]etting|[Ss]tartup|[Aa]lwaysOnTop|'
                      r'[Ff]ont|[Ll]anguage|deleteLayout|duplicateLayout|renameLayout|initialize|chooseLocal|'
                      r'[Dd]ownload|[Dd]atabase|JSON|[Jj]son|[Cc]lear[A-Z]|[Qq]werty|[Aa]zerty|[Qq]wertz|[Ss]ize|'
                      r'[Ww]indow|[Ss]tats|[Hh]istogram|[Ee]cho|[Ss]ync|[Ff]older|[Cc]rop|[Ii]con|[Ll]og[A-Z]|'
                      r'CSV|csv|[Cc]ombine|[Hh]int|[Ss]how[A-Z]|toggleMenuBar|[Ee]xit')


FREQ_SETTING = re.compile(r'[Ss]ettings|[Pp]references|[Aa]rrival|[Dd]eparture|[Ll]atency|[Dd]efault|[Dd]elay|'
                          r'[Mm]ax[A-Z]|[Mm]in[A-Z]|[Ss]imulat|[Ss]equential|[Aa]tomic|[Cc]apture|[Pp]reArrival')


FREQ_EDIT = re.compile(r'^Edit|[Ee]dit[A-Z ]|[Rr]ename|[Cc]hange|[Dd]uplicate|[Dd]elete|^Add |[Cc]opy|[Pp]aste|'
                       r'[Rr]eplace')


def frequency(entry):
    text = ' '.join([entry['label'], entry['member'], ' '.join(entry['names'][:3]), entry['kind']])
    if entry['kind'] in ('hover', 'notice'):
        return 1
    if FREQ_SETTING.search(' '.join(entry.get('path', [])[:-1])) or FREQ_SETTING.search(entry['member']):
        return 1
    if entry['kind'] == 'key':
        return 3
    if FREQ_LOW.search(text):
        return 1
    if FREQ_EDIT.search(entry['label']):
        return 2
    if FREQ_HIGH.search(text):
        return 3
    return 2


class Inventory(object):
    def __init__(self, root):
        self.root = root
        self.program = Program(root)
        self.bundle = load_bundle(root)
        self.entries = []
        self.gui = {rel: jf for rel, jf in self.program.files.items() if '/gui/' in rel}
        self.form_components = {}       # (cls, name) -> component info
        self._forms()
        self._listeners()
        self._keys()
        self._tabs_in_code()
        self._dialogs()
        # a form key handler whose branches are listed one by one is not a row of its own; its components (the
        # keyboard tab, the panels) are what a test must name to have sent the key to it
        branched = set(e['member'] for e in self.entries if e['kind'] == 'key')
        listening = collections.defaultdict(set)
        for e in self.entries:
            if e['kind'] == 'key handler':
                listening[(e['file'], e['member'])] |= set(e['names'])
        for e in self.entries:
            if e['kind'] == 'key':
                e['listening'] = sorted(listening.get((e['file'], e['member']), ()))
        self.entries = [e for e in self.entries if not (e['kind'] == 'key handler' and e['member'] in branched)]
        for i, e in enumerate(self.entries):
            e['id'] = 'U%04d' % (i + 1)
        # a key on more than two rows (a dialog title such as "Please confirm"), or a plain button word ("Cancel"),
        # names none of them
        uses = collections.Counter(k for e in self.entries for k in set(e['keys']))
        plain = set('cancel ok yes no close apply save done back next delete remove edit add help error warning'
                    .split())
        for e in self.entries:
            not_plain = [k for k in e['keys'] if self.bundle.get(k, '').strip(' .!:').lower() not in plain]
            own = [k for k in not_plain if uses[k] <= 2]
            # with nothing of its own, a component with no name keeps its shared label (two menus' "Edit Route")
            e['match_keys'] = own or ([] if e['kind'] in ('dialog', 'notice') or e['names'] else not_plain)
        # a component name no other class's component has needs no class named beside it
        owners = collections.defaultdict(set)
        for e in self.entries:
            for n in e['names']:
                owners[n].add(e['file'])
        for e in self.entries:
            e['unique_names'] = [n for n in e['names'] if len(owners[n]) == 1 and not re.match(r'^j[A-Z]\w*\d+$', n)]
        # a door that asks one question only: a test that calls the door and answers a dialog answered that one
        asks = collections.Counter((e['file'], e['member']) for e in self.entries if e['kind'] == 'dialog')
        for e in self.entries:
            e['only_dialog'] = e['kind'] == 'dialog' and asks[(e['file'], e['member'])] == 1
        for e in self.entries:
            self._size(e)
            e['freq'] = frequency(e)

    # ---- helpers -----------------------------------------------------------------------------------------------

    def text_of(self, keys):
        for k in keys:
            if k in self.bundle:
                return self.bundle[k]
        return None

    def add(self, **kw):
        e = {'list': 'user', 'kind': '', 'label': '', 'file': '', 'member': '', 'line': 0, 'names': [], 'keys': [],
             'handlers': [], 'targets': [], 'needs': 'click', 'vk': [], 'mods': [], 'event': '', 'code_calls': set(),
             'own_lines': 0, 'cluster': None}
        e.update(kw)
        e['id'] = 'U%04d' % (len(self.entries) + 1)
        jf_cls = os.path.basename(e['file'])[:-5]
        e['area'] = area_for(jf_cls, ' '.join([e['label'], e['member']] + e['names'] + e['keys'] + e['handlers']
                                               + e['targets']), e.get('path', ()))
        self.entries.append(e)
        return e

    def _targets(self, calls, rel):
        """What a listener calls that names one thing: declared in exactly one source file, and not a common name."""
        out = []
        for c in sorted(calls):
            if self.program.distinctive(c) and self.program.files_declaring.get(c) == 1:
                out.append(c)
        return out

    # ---- forms ------------------------------------------------------------------------------------------------

    def _forms(self):
        gui_dir = os.path.join(self.root, 'src', 'org', 'traincontrol', 'gui')
        # Walked, not listed: gui has sub-packages.  Ordered by file name, as the flat listing was.
        forms = [(name, folder) for folder, _, names in os.walk(gui_dir) for name in names if name.endswith('.form')]
        for name, folder in sorted(forms):
            rel = os.path.relpath(os.path.join(folder, name[:-5] + '.java'), self.root).replace(os.sep, '/')
            jf = self.program.files.get(rel)
            try:
                tree = ET.parse(os.path.join(folder, name))
            except ET.ParseError:
                continue
            cls = name[:-5]
            by_handler = collections.OrderedDict()
            tabs = []

            def prop_key(el, prop):
                for p in el.findall('./Properties/Property'):
                    if p.get('name') == prop:
                        rs = p.find('./ResourceString')
                        if rs is not None:
                            return ('key', rs.get('key'))
                        if p.get('value'):
                            return ('text', p.get('value'))
                return None

            def walk(el, path):
                comp_name = el.get('name')
                comp_class = (el.get('class') or '').split('.')[-1]
                here = path + [comp_name] if comp_name else path
                if comp_name and comp_class:
                    keys, texts = [], []
                    for prop in ('text', 'toolTipText', 'title'):
                        v = prop_key(el, prop)
                        if v and v[0] == 'key':
                            keys.append(v[1])
                        elif v:
                            texts.append(v[1])
                    self.form_components[(cls, comp_name)] = {'class': comp_class, 'keys': keys, 'texts': texts,
                                                              'path': here}
                    for c in el.findall('./Constraints/Constraint/JTabbedPaneConstraints'):
                        title = c.get('tabName')
                        tkeys = [rs.get('key') for rs in c.findall('.//ResourceString')]
                        tabs.append((comp_name, path[-1] if path else '', title, tkeys))
                    for ev in el.findall('./Events/EventHandler'):
                        h = ev.get('handler')
                        by_handler.setdefault(h, []).append((comp_name, comp_class, ev.get('event'), keys, texts,
                                                             here))
                for child in list(el):
                    walk(child, here)

            walk(tree.getroot(), [])
            for handler, comps in by_handler.items():
                found = self.program.find(handler, cls + '.java')
                line, calls, own = 0, set(), 0
                if found:
                    line, calls, own = found[1]['line'], found[1]['calls'], found[1]['lines']
                names = [c[0] for c in comps]
                keys = [k for c in comps for k in c[3]]
                event = comps[0][2]
                comp_class = comps[0][1]
                text = self.text_of(keys) or (comps[0][4][0] if comps[0][4] else '')
                if len(comps) > 1:
                    label = '%s (%d components: %s...)' % (text or handler, len(comps), ', '.join(names[:3]))
                else:
                    label = '%s [%s]' % (text, names[0]) if text else names[0]
                kind = KIND_OF_CLASS.get(comp_class, 'control')
                if event.startswith('key'):
                    kind = 'key handler'
                elif event in ('mouseClicked', 'mouseReleased', 'mousePressed') and kind not in ('button', 'checkbox',
                                                                                                'radio', 'menu item'):
                    kind = 'gesture'
                elif event in ('mouseMoved', 'mouseEntered', 'mouseExited'):
                    kind = 'hover'
                elif event == 'windowClosing':
                    kind = 'window'
                if kind == 'button' and rel.endswith(EDITOR_FILES):
                    kind = 'editor tool'
                needs = 'click'
                if comp_class == 'JTabbedPane' and event == 'stateChanged':
                    needs = 'tab'
                elif event.startswith('mouse'):
                    needs = 'mouse'
                elif event.startswith('key'):
                    needs = 'key'
                self.add(kind=kind, label=label, file=rel, member=handler, line=line, names=names, keys=keys,
                         handlers=[handler], targets=self._targets(calls, rel) if own <= 15 else [],
                         needs=needs, event=event, code_calls=set(calls) | {handler}, own_lines=own,
                         source='form', component_class=comp_class, path=comps[0][5])
            for comp, pane, title, tkeys in tabs:
                text = self.text_of(tkeys) or title
                pane_handlers = [h for h, cs in by_handler.items() if any(c[0] == pane and c[2] == 'stateChanged'
                                                                          for c in cs)]
                self.add(kind='tab', label='tab "%s" [%s in %s]' % (text, comp, pane), file=rel,
                         member=pane_handlers[0] if pane_handlers else pane, line=0, names=[comp], keys=tkeys,
                         handlers=pane_handlers, needs='tab', event='stateChanged',
                         code_calls=set(pane_handlers), source='form', component_class='JTabbedPane',
                         tab_title=title, path=[pane, comp, comp], pane=pane)

    # ---- hand-written listeners ---------------------------------------------------------------------------------

    def _construction(self, jf, subject, before, method):
        """(class, keys, construct_end) of the latest `subject = new J...(` before pos in the method or as a field."""
        lo = method['body_start'] if method else 0
        best = None
        for m in CONSTRUCT.finditer(jf.code, lo, before):
            if m.group(1) == subject or (m.group(1) is None and re.search(r'\b' + re.escape(subject) + r'\s*=\s*$',
                                                                          jf.code[max(0, m.start() - 80):m.start()])):
                best = m
        if best is None:
            # a field, or a local declared `JButton x = new JButton(...)`
            for m in CONSTRUCT.finditer(jf.code):
                if m.group(1) == subject:
                    best = m
                    break
        if best is None:
            return None
        paren = jf.code.find('(', best.end() - 1)
        end = balanced(jf.code, paren) if paren >= 0 else best.end()
        return best.group(2), keys_of(jf, best.start(), end), paren, end

    @staticmethod
    def _set_names(jf, var, method):
        """The component names given to var with setName("x") in the method (tests find components by them)."""
        lo, hi = (method['body_start'], method['end']) if method else (0, len(jf.code))
        out = []
        for m in re.finditer(r'\b' + re.escape(var) + r'\s*\.\s*setName\s*\(\s*"', jf.code[lo:hi]):
            found = jf.literals_in(lo + m.end() - 1, lo + m.end())
            if found:
                out.append(found[0])
        return out

    def _listeners(self):
        for rel, jf in sorted(self.gui.items()):
            gen = [(m.start(), m.end()) for m in GEN_REGION.finditer(jf.text)]
            factories = {}
            for m in LISTENER.finditer(jf.code):
                if any(a <= m.start() < b for a, b in gen):
                    continue
                subject = (m.group(1) or 'this').split('.')[-1]
                ltype = m.group(2)
                open_paren = m.end() - 1
                end = balanced(jf.code, open_paren)
                arg = jf.code[open_paren + 1:end - 1]
                method = jf.method_at(m.start())
                member = method['name'] if method else ''
                cons = self._construction(jf, subject, m.start(), method) if subject not in ('this', 'super') else None
                comp_class, keys = None, []
                form = self.form_components.get((jf.cls, subject))
                if form:
                    comp_class, keys = form['class'], list(form['keys'])
                if cons:
                    comp_class = comp_class or cons[0]
                    keys = keys + cons[1]
                    params = re.findall(r'(?:String|Object)\s+(\w+)', method['header']) if method else []
                    cons_args = jf.code[cons[2] + 1:cons[3] - 1].strip() if cons[2] >= 0 else ''
                    if method and params and (cons_args in params or re.match(r'^\w+$', cons_args or '-')
                                              and cons_args in params):
                        factories[member] = (method, ltype, comp_class)
                        continue
                if subject not in ('this', 'super'):
                    for km in re.finditer(r'\b' + re.escape(subject) + r'\s*\.\s*set(?:Text|ToolTipText)\s*\(', jf.code):
                        keys.extend(keys_of(jf, km.end(), balanced(jf.code, km.end() - 1)))
                keys = list(OrderedDict_fromkeys(keys))
                is_field = subject not in ('this', 'super') and bool(form or re.search(
                    r'^\s+(?:(?:private|protected|public|final|static)\s+)+[\w.<>\[\]]+\s+' + re.escape(subject)
                    + r'\s*[;=]', jf.code, re.M))
                names = [subject] if is_field else []
                if subject not in ('this', 'super'):
                    names += self._set_names(jf, subject, method)
                text = self.text_of(keys)
                kind = KIND_OF_CLASS.get(comp_class or '', '')
                start_line = jf.line(m.start())
                sub = list(MOUSE_METHOD.finditer(arg))
                if ltype in ('Mouse', 'MouseMotion', 'MouseWheel') or (sub and sub[0].group(1).startswith('mouse')):
                    if not sub:
                        # a listener object built elsewhere (new RightClickRouteMenu(ui), this::x)
                        calls = calls_of(arg)
                        self.add(kind='gesture', label='%s mouse on %s -> %s' % (jf.cls, subject, ', '.join(
                            sorted(calls))[:60]), file=rel, member=member, line=start_line, names=names,
                            keys=keys, targets=self._targets(calls, rel), needs='mouse', event='mouse',
                            code_calls=calls, own_lines=arg.count('\n') + 1, source='code', listener_cls=jf.cls,
                            subject=subject)
                        continue
                    for i, sm in enumerate(sub):
                        body_open = arg.find('{', sm.end())
                        if body_open < 0:
                            continue
                        body_end = balanced(arg, body_open)
                        body = arg[body_open:body_end]
                        calls = calls_of(body)
                        if not calls or (len(body.strip('{} \n')) == 0):
                            continue
                        ev = sm.group(1)
                        k = 'hover' if ev in ('mouseMoved', 'mouseEntered', 'mouseExited') else 'gesture'
                        flavour = []
                        if re.search(r'isRightMouseButton|BUTTON3|isPopupTrigger', body):
                            flavour.append('right')
                        if re.search(r'getClickCount\s*\(\s*\)\s*(?:==|>=|>)\s*2', body):
                            flavour.append('double')
                        if re.search(r'isControlDown|isShiftDown|isMetaDown|isAltDown', body):
                            flavour.append('modifier')
                        self.add(kind=k, label='%s %s on %s%s -> %s' % (jf.cls, ev, subject,
                                                                       (' (' + '/'.join(flavour) + ')') if flavour
                                                                       else '', ', '.join(sorted(
                                                                           self._targets(calls, rel)))[:70]),
                                 file=rel, member=member, line=start_line + arg[:sm.start()].count('\n'),
                                 names=names, keys=keys, targets=self._targets(calls, rel), needs='mouse', event=ev,
                                 code_calls=calls, own_lines=body.count('\n') + 1, source='code',
                                 listener_cls=jf.cls, subject=subject)
                    continue
                if ltype == 'Key':
                    continue        # shortcuts are listed branch by branch in _keys
                calls = calls_of(arg)
                if not kind:
                    kind = 'control'
                if kind == 'button' and rel.endswith(EDITOR_FILES):
                    kind = 'editor tool'
                label = '%s [%s]' % (text, subject) if text else '%s.%s (%s listener)' % (jf.cls, subject, ltype)
                self.add(kind=kind, label=label, file=rel, member=member, line=start_line, names=names, keys=keys,
                         targets=self._targets(calls, rel), needs='tab' if comp_class == 'JTabbedPane' else 'click',
                         event=ltype, code_calls=calls, own_lines=arg.count('\n') + 1, source='code',
                         component_class=comp_class or '', path=form['path'] if form else [])
            # factory call sites: item(I18n.t("key"), () -> action())
            for fname, (fmethod, ltype, comp_class) in factories.items():
                for cm in re.finditer(r'(?<![\w.])' + re.escape(fname) + r'\s*\(', jf.code):
                    if fmethod['start'] <= cm.start() < fmethod['end']:
                        continue
                    op = cm.end() - 1
                    end = balanced(jf.code, op)
                    keys = keys_of(jf, op, end)
                    arg = jf.code[op + 1:end - 1]
                    calls = calls_of(arg) - {'t', 'f'}
                    encl = jf.method_at(cm.start())
                    text = self.text_of(keys)
                    kind = KIND_OF_CLASS.get(comp_class or '', 'menu item')
                    if kind == 'button' and rel.endswith(EDITOR_FILES):
                        kind = 'editor tool'
                    assigned = re.search(r'\b(\w+)\s*=\s*$', jf.code[max(0, cm.start() - 80):cm.start()])
                    set_names = self._set_names(jf, assigned.group(1), encl) if assigned else []
                    self.add(kind=kind, label='%s [%s() in %s]' % (text or (keys[0] if keys else '?'), fname,
                                                                  encl['name'] if encl else '?'),
                             file=rel, member=encl['name'] if encl else fname, line=jf.line(cm.start()),
                             names=set_names,
                             keys=keys, targets=self._targets(calls, rel), needs='click', event=ltype,
                             code_calls=calls, own_lines=arg.count('\n') + 1, source='factory',
                             component_class=comp_class or '')

    # ---- keyboard shortcuts --------------------------------------------------------------------------------------

    def _keys(self):
        for rel, jf in sorted(self.gui.items()):
            code = jf.code
            for m in re.finditer(r'\b(?:else\s+)?if\s*\(', code):
                op = m.end() - 1
                end = balanced(code, op)
                cond = code[op + 1:end - 1]
                letters = 'buttonMapping' in cond and 'keyCode' in cond
                if 'VK_' not in cond and not letters:
                    continue
                i = end
                while i < len(code) and code[i] in ' \t\n':
                    i += 1
                if i < len(code) and code[i] == '{':
                    body = code[i:balanced(code, i)]
                else:
                    body = code[i:code.find(';', i) + 1]
                calls = calls_of(body)
                if not body.strip() or (len(calls) == 0 and 'return' not in body):
                    continue
                vks = sorted(set(VK.findall(cond)))
                mods = []
                for name, rx in (('Ctrl', r'(?<!!)\b(?:controlPressed|isControlDown\s*\(\s*\)|ctrl\w*)\b(?!\s*==\s*false)'),
                                 ('Alt', r'(?<!!)\b(?:altPressed|isAltDown\s*\(\s*\))'),
                                 ('Shift', r'(?<!!)\b(?:shiftPressed|isShiftDown\s*\(\s*\))')):
                    if re.search(rx, cond):
                        mods.append(name)
                if letters:
                    vks = [chr(c) for c in range(ord('A'), ord('Z') + 1)]
                method = jf.method_at(m.start())
                member = method['name'] if method else ''
                label = '%s%s in %s.%s -> %s' % ('+'.join(mods) + '+' if mods else '',
                                                 'A..Z (mapped buttons)' if letters else '/'.join(vks[:4]), jf.cls,
                                                 member, ', '.join(self._targets(calls, rel))[:60])
                # alternatives joined by || each carry their own modifiers: none of them is required to match
                required = [] if '||' in cond else mods
                self.add(kind='key', label=label, file=rel, member=member, line=jf.line(m.start()),
                         names=[], keys=[], handlers=[member] if member else [], targets=self._targets(calls, rel),
                         needs='key', event='key', vk=vks, mods=required, code_calls=calls,
                         own_lines=body.count('\n') + 1, source='key', listener_cls=jf.cls)
            for m in re.finditer(r'\bcase\s+(?:KeyEvent\s*\.\s*)?VK_(\w+)\s*:', code):
                nxt = re.compile(r'\bcase\b|\bdefault\s*:|\bbreak\s*;|\breturn\b').search(code, m.end())
                body = code[m.end():nxt.end() if nxt else m.end() + 400]
                calls = calls_of(body)
                method = jf.method_at(m.start())
                member = method['name'] if method else ''
                self.add(kind='key', label='%s in %s.%s -> %s' % (m.group(1), jf.cls, member,
                                                                  ', '.join(self._targets(calls, rel))[:60]),
                         file=rel, member=member, line=jf.line(m.start()), handlers=[member] if member else [],
                         targets=self._targets(calls, rel), needs='key', event='key', vk=[m.group(1)], mods=[],
                         code_calls=calls, own_lines=body.count('\n') + 1, source='key', listener_cls=jf.cls)
            for m in re.finditer(r'getKeyStroke\s*\(', code):
                op = m.end() - 1
                end = balanced(code, op)
                lits = jf.literals_in(op, end)
                vks = VK.findall(code[op:end]) or [l.split()[-1] for l in lits if l]
                window = code[end:end + 600]
                if '"none"' in code[end:end + 20] or 'none' in jf.literals_in(end, end + 40):
                    continue
                calls = calls_of(window.split(';')[0] + ';' + ';'.join(window.split(';')[1:3]))
                method = jf.method_at(m.start())
                member = method['name'] if method else ''
                mods = [w.capitalize() for w in ' '.join(lits).split() if w in ('ctrl', 'alt', 'shift', 'control')]
                if 'CTRL' in code[op:end] or 'CTRL_DOWN' in code[op:end]:
                    mods.append('Ctrl')
                self.add(kind='key', label='keystroke %s in %s.%s' % ('+'.join(mods + vks), jf.cls, member),
                         file=rel, member=member, line=jf.line(m.start()), handlers=[member] if member else [],
                         targets=self._targets(calls, rel), needs='key', event='key', vk=vks, mods=mods,
                         code_calls=calls, own_lines=5, source='key', listener_cls=jf.cls, keystroke=True)

    # ---- tabs added in code ------------------------------------------------------------------------------------------

    def _tabs_in_code(self):
        for rel, jf in sorted(self.gui.items()):
            gen = [(g.start(), g.end()) for g in GEN_REGION.finditer(jf.text)]
            for m in re.finditer(r'(\w+)\s*\.\s*(?:addTab|insertTab)\s*\(', jf.code):
                if any(a <= m.start() < b for a, b in gen):
                    continue        # the form's own tabs, listed from the .form
                op = m.end() - 1
                end = balanced(jf.code, op)
                keys = keys_of(jf, op, end)
                method = jf.method_at(m.start())
                text = self.text_of(keys)
                self.add(kind='tab', label='tab "%s" [%s]' % (text or '?', m.group(1)), file=rel,
                         member=method['name'] if method else '', line=jf.line(m.start()), names=[m.group(1)],
                         keys=keys, needs='tab', event='stateChanged', code_calls=set(), own_lines=1, source='code',
                         component_class='JTabbedPane')

    # ---- dialogs -----------------------------------------------------------------------------------------------------

    def _dialogs(self):
        for rel, jf in sorted(self.program.files.items()):
            if '/gui/' not in rel and not rel.endswith('MarklinControlStation.java'):
                continue
            code = jf.code
            for m in DIALOG.finditer(code):
                op = m.end() - 1
                end = balanced(code, op)
                keys = keys_of(jf, op, end)
                method = jf.method_at(m.start())
                if not keys and method:
                    # the message built a few lines above: String message = I18n.f("key", ...)
                    idents = set(re.findall(r'\b([a-z]\w*)\b', code[op:end]))
                    lo = max(method['body_start'], code.rfind('\n', 0, max(0, m.start() - 4000)))
                    for im in re.finditer(r'\b(\w+)\s*=\s*', code[lo:m.start()]):
                        if im.group(1) in idents:
                            stmt_end = code.find(';', lo + im.end())
                            keys.extend(keys_of(jf, lo + im.end(), stmt_end))
                kind = 'notice' if m.group(1) in ('Message', 'InternalMessage') else 'dialog'
                text = self.text_of(keys)
                member = method['name'] if method else ''
                self.add(kind=kind, label='%s: "%s"' % (m.group(1).lower(), (text or (keys[0] if keys else '?'))[:70]),
                         file=rel, member=member, line=jf.line(m.start()), names=[], keys=list(
                             OrderedDict_fromkeys(keys)), handlers=[member] if member else [], needs='dialog',
                         event='dialog', code_calls={member} if member else set(), own_lines=0, source='dialog')

    # ---- size --------------------------------------------------------------------------------------------------------

    def _size(self, e):
        rel = e['file']
        calls = set(e['code_calls'])
        reached = self.program.reach(calls, rel, depth=2)
        e['reach_lines'] = sum(reached.values()) + (e['own_lines'] if e['source'] != 'form' else 0)
        e['reach'] = sorted(set(k[1] for k in reached))


def OrderedDict_fromkeys(items):
    return collections.OrderedDict((k, None) for k in items if k)


# --------------------------------------------------------------------------------------------------------------------
# Classifying
# --------------------------------------------------------------------------------------------------------------------

def classify_user(inv, tests, entry, index):
    names = set(entry['names'])
    getters = set('get' + n[0].upper() + n[1:] for n in names if n)
    keys = set(entry['match_keys'])
    handlers = set(entry['handlers'])
    targets = set(entry['targets'])
    needs = entry['needs']
    cands = set()
    for tok in names | keys | handlers | targets | getters:
        cands |= index['lits'].get(tok, set())
        cands |= index['calls'].get(tok, set())
    for vk in entry['vk']:
        cands |= index['vk'].get(vk, set())
    if entry['needs'] == 'mouse' and entry.get('listener_cls'):
        cands |= index['mouse_types'].get(entry['listener_cls'], set())
    credits = {}
    for root in cands:
        cl = tests.closures[root]
        if cl['asserts'] == 0:
            continue
        how, why = None, ''
        owner = os.path.basename(entry['file'])[:-5]
        by_name = (names & cl['lits']) or (getters & cl['calls'])
        if by_name and not (by_name & set(entry['unique_names'])) and owner not in cl['types'] \
                and owner not in cl['lits']:
            by_name = set()         # a name another class's component also has, and this class is not named
        by_key = keys & cl['lits']
        if by_key and needs not in ('dialog',):
            # a component found by its text is driven where the text is named (the same method holds both), or the
            # method naming it hands the component back to a caller that drives
            by_key = set(k for mm in cl['members'] for k in (keys & tests.methods[mm]['lits'])
                         if tests.methods[mm]['click'] or tests.methods[mm]['dispatch']
                         or (tests.methods[mm]['returns_component'] and (cl['click'] or cl['dispatch'])))
        named = by_name or by_key
        if needs == 'dialog':
            door = (handlers & cl['lits'] and cl['reflect']) or (handlers & cl['calls'])
            if keys & cl['lits']:
                if cl['answer_real']:
                    how, why = 'as-the-user', 'names %s; answers the real dialog (%s)' % (
                        sorted(keys & cl['lits'])[0], '/'.join(sorted(cl['answers'])) or 'button')
                elif cl['answer_hook']:
                    how, why = 'by-handler', 'names %s; answered through a test hook' % sorted(keys & cl['lits'])[0]
                else:
                    how, why = 'by-handler', 'names %s; no real dialog answered' % sorted(keys & cl['lits'])[0]
            elif door and (cl['answer_real'] or cl['answer_hook']) and entry.get('only_dialog'):
                how, why = 'by-handler', 'calls %s and answers a dialog it does not name' % sorted(handlers)[0]
            if how:
                credits[root] = (how, why)
            continue
        elif needs == 'key':
            vks = set(entry['vk'])
            if vks & cl['vk']:
                mods_ok = all(m.upper() in cl['mods'] for m in entry['mods'])
                cls_named = entry.get('listener_cls', '') in cl['types'] or entry.get('listener_cls', '') in cl['lits']
                # a handler on named components (the main window's keyboard tab and panels) hears a key sent to one
                # of them; one on the frame itself, a key sent to that class
                listening = set(entry.get('listening', []))
                sent_to = bool(listening & cl['lits']) or bool(
                    set('get' + n[0].upper() + n[1:] for n in listening) & cl['calls']) if listening else cls_named
                if entry.get('keystroke'):
                    # a binding on one window's root pane: the test opens that window, through the method that binds
                    sent_to = sent_to and entry['member'] in (cl['lits'] | cl['calls'])
                if cl['key_drive'] and mods_ok and sent_to:
                    how, why = 'as-the-user', 'dispatches VK_%s%s' % (sorted(vks & cl['vk'])[0],
                                                                        ' with ' + '+'.join(entry['mods'])
                                                                        if entry['mods'] else '')
                elif cl['key_handler'] and mods_ok and cls_named and (handlers & cl['lits'] or handlers & cl['calls']):
                    how, why = 'by-handler', 'hands VK_%s to %s' % (sorted(vks & cl['vk'])[0], sorted(handlers)[0])
        elif needs == 'mouse':
            ev = entry['event']
            want = {'mouseClicked': 'CLICKED', 'mousePressed': 'PRESSED', 'mouseReleased': 'RELEASED',
                    'mouseDragged': 'DRAGGED', 'mouseMoved': 'MOVED', 'mouseEntered': 'ENTERED',
                    'mouseExited': 'EXITED', 'mouseWheelMoved': 'WHEEL'}.get(ev)
            subject = entry.get('subject', '')
            subject_named = bool(named)
            if not subject_named and entry.get('listener_cls', '@') in cl['types']:
                if subject in ('this', 'super', ''):
                    # the listening class is the component (a diagram square, the grid)
                    subject_named = entry['listener_cls'] not in ('TrainControlUI', 'AutonomyEditorPanel')
                else:
                    # a local component: the test must name it, its door, or what it calls
                    near = {subject} | targets | ({entry['member']} - {entry['listener_cls']})
                    subject_named = bool(near & (cl['lits'] | cl['calls'] | cl['types']))
            if cl['mouse_drive'] and subject_named and (want is None or want in cl['mouse']):
                how, why = 'as-the-user', 'dispatches MOUSE_%s to a %s' % (want or '*', named and sorted(
                    named)[0] or entry.get('listener_cls'))
        elif needs == 'tab':
            pane = entry.get('pane')
            tab_named = named or (pane and pane in cl['lits'] and owner in cl['types']
                                  and ({entry.get('tab_title')} | set(entry['keys'])) & cl['lits'])
            selects = cl['selects_tab'] or (cl['dispatch'] and cl['mouse_drive'])
            if tab_named and selects:
                how, why = 'as-the-user', 'selects the tab (%s)' % (sorted(named)[0] if named else pane)
        else:
            drive = cl['click'] or cl['dispatch']
            if needs == 'mouse':
                drive = cl['mouse_drive']
            if named and drive:
                how, why = 'as-the-user', 'names %s; drives it (%s)' % (
                    sorted(named)[0], 'doClick/listener' if cl['click'] else 'dispatchEvent')
        if how is None:
            # a key handler holds every shortcut: calling it is credited to the branch whose code it sends, above
            hit = needs != 'key' and ((handlers & cl['lits'] and cl['reflect']) or (handlers & cl['calls']))
            if hit:
                how, why = 'by-handler', 'calls %s' % sorted(handlers & (cl['lits'] | cl['calls']))[0]
            else:
                t = (targets & cl['calls']) or (targets & cl['lits'] if cl['reflect'] else set())
                if t:
                    how, why = 'by-handler', 'calls what it calls: %s' % sorted(t)[0]
        if how:
            prev = credits.get(root)
            if prev is None or HOW_ORDER[how] > HOW_ORDER[prev[0]]:
                credits[root] = (how, why)
    return credits


def build_index(tests):
    idx = {'lits': collections.defaultdict(set), 'calls': collections.defaultdict(set),
           'vk': collections.defaultdict(set), 'mouse_types': collections.defaultdict(set)}
    for root, cl in tests.closures.items():
        for l in cl['lits']:
            idx['lits'][l].add(root)
        for c in cl['calls']:
            idx['calls'][c].add(root)
        for v in cl['vk']:
            idx['vk'][v].add(root)
        if cl['mouse_drive']:
            for t in cl['types']:
                idx['mouse_types'][t].add(root)
    return idx


CLUSTERS = collections.OrderedDict([
    ('LocIconActions (icon crop)', ['setLocIcon', 'cropLocIcon', 'recropLocIcon', 'rememberCropSource',
                                    'cropTheCentralStationPicture', 'deleteLocIconFile', 'TurningArc', 'RevertMark',
                                    'WrenchMark', 'CropMark']),
    ('UpdateFlow (update check)', ['offerTheUpdate', 'askAboutTheUpdate', 'downloadTheUpdate', 'installTheDownload',
                                   'whenNoModalDialog', 'closeForTheUpdate']),
    ('BackupAction (backup)', ['prefixForBackup', 'backupData', 'writeTheBackup', 'BackupOutcome',
                               'askOnEventThread']),
    ('RouteListPanel (route list)', ['executeRoute', 'runAndTimeTheRoute', 'refreshRouteList', 'editRoute',
                                     'duplicateRoute', 'BulkEnableOrDisable', 'findRoute', 'routeNamedIn',
                                     'CustomTableRenderer']),
    ('LayoutFileActions (layout files and pages)', ['duplicateOrRenameCurrentLayout', 'combineLinkedPages',
                                                    'writeIndexAndKeepLinksAimed', 'initializeEmptyLayout',
                                                    'unzipFile', 'downloadCentralStationLayout', 'createNewLayout',
                                                    'copyCurrentPage', 'pasteCopiedPage', 'clearCurrentPage',
                                                    'doClearCurrentPage', 'useTheDownloadedLayout',
                                                    'createAndApplyEmptyLayout']),
])


# Members every part of the window calls (a refresh, a renderer, a wait): calling one does not make a row the cluster's.
CLUSTER_INTERNALS = {'refreshRouteList', 'CustomTableRenderer', 'routeNamedIn', 'whenNoModalDialog', 'prefixForBackup'}


def cluster_of(entry, program):
    """The Tier 1 cluster an entry's own code calls into: directly, or one call further through a thin handler."""
    if not entry['file'].endswith(('TrainControlUI.java', 'RightClickRouteMenu.java', 'RightClickPageMenu.java',
                                   'LocIconCropDialog.java', 'AutonomyMenu.java', 'LocomotiveMenuItems.java')):
        return None
    direct = (set(entry['code_calls']) | set(entry['handlers'])) - CLUSTER_INTERNALS
    if entry['kind'] in ('dialog', 'notice'):
        direct.add(entry['member'])
    for name, members in CLUSTERS.items():
        if direct & set(members):
            return name
    if entry['kind'] in ('dialog', 'notice'):
        return None         # a question takes its door's cluster (run), not what its method reaches
    thin = set()
    for c in direct:
        for rel, m in program.resolve(c, entry['file']):
            if m['lines'] <= 12:
                thin |= m['calls']
    thin -= CLUSTER_INTERNALS
    for name, members in CLUSTERS.items():
        if thin & set(members):
            return name
    # two calls deep, the way a handler reaches createAndApplyEmptyLayout and then initializeEmptyLayout
    reach = set(entry.get('reach', [])) - CLUSTER_INTERNALS
    for name, members in CLUSTERS.items():
        if reach & set(members):
            return name
    return None


def run(root):
    t0 = time.time()
    inv = Inventory(root)
    tests = Tests(root, inv.program)
    idx = build_index(tests)
    rows = []
    for e in inv.entries:
        credits = classify_user(inv, tests, e, idx)
        how = 'none'
        for r, (h, _) in credits.items():
            how = best_how(how, h)
        e['how'] = {'none': 'none'}.get(how, how)
        e['tests'] = sorted(({'test': tests.label(r), 'how': h, 'why': w} for r, (h, w) in credits.items()),
                            key=lambda d: (-HOW_ORDER[d['how']], d['test']))
        e['cluster'] = cluster_of(e, inv.program)
        rows.append(e)
    # a question belongs to the cluster of the handler that asks it
    by_handler = {}
    for e in rows:
        if e['kind'] not in ('dialog', 'notice') and e['cluster']:
            for h in e['handlers'] + [e['member']]:
                by_handler.setdefault((e['file'], h), e['cluster'])
    for e in rows:
        if e['kind'] in ('dialog', 'notice') and not e['cluster']:
            e['cluster'] = by_handler.get((e['file'], e['member']))
    # list 2
    state_rows = []
    n = 0
    for ev in EVENTS:
        where = []
        for name, pref in ev['members']:
            found = inv.program.find(name, pref)
            if found:
                where.append('%s#%s:%d' % (found[0].replace('src/org/traincontrol/', ''), name, found[1]['line']))
        reach = inv.program.reach(set(n_ for n_, _ in ev['members']), '', depth=2)
        lines = sum(reach.values())
        for oc in OUTCOMES:
            if oc['id'] not in ev['outcomes']:
                continue
            n += 1
            credits = []
            for root, cl in tests.closures.items():
                if cl['asserts'] == 0 or ev['id'] not in cl['starts'] or oc['id'] not in cl['checks']:
                    continue
                h = cl['starts'][ev['id']]
                credits.append({'test': tests.label(root), 'how': 'as-the-railway' if h == 'railway' else 'by-handler',
                                'why': 'starts %s (%s); checks %s' % (ev['id'], h, oc['id'])})
            how = 'none'
            for c in credits:
                how = best_how(how, c['how'])
            credits.sort(key=lambda d: (-HOW_ORDER[d['how']], d['test']))
            state_rows.append({'id': 'S%03d' % n, 'list': 'state', 'kind': 'outcome', 'event': ev['id'],
                               'outcome': oc['id'], 'label': '%s -> %s' % (ev['label'], oc['label']),
                               'area': ev['area'], 'file': where[0].split('#')[0] if where else '',
                               'member': ', '.join(where), 'line': 0, 'how': how, 'tests': credits,
                               'freq': EVENT_FREQ.get(ev['id'], 2), 'reach_lines': lines, 'own_lines': 0,
                               'names': [], 'keys': [], 'cluster': None})
    return rows, state_rows, tests, time.time() - t0


EVENT_FREQ = {'route': 3, 'autonomy': 3, 'feedback': 3, 'echo': 3, 'power': 3, 'save': 3, 'restart': 3,
              'timetable': 2, 'home': 2, 'switch': 1, 'import': 1}


# --------------------------------------------------------------------------------------------------------------------
# Output
# --------------------------------------------------------------------------------------------------------------------

def gap_score(r):
    return (r['freq'], 1 if r['how'] == 'none' else 0, r['reach_lines'])


def short_how(h):
    return {'as-the-user': 'user', 'as-the-railway': 'railway', 'by-handler': 'handler', 'none': 'none'}[h]


def print_rows(rows, out):
    for r in rows:
        tests = ', '.join('%s(%s)' % (t['test'], short_how(t['how'])) for t in r['tests'][:4])
        more = ' +%d' % (len(r['tests']) - 4) if len(r['tests']) > 4 else ''
        out.write('%s %-8s %-11s %-8s f%d %5dL  %s  [%s#%s:%s]  %s%s\n' % (
            r['id'], r['area'], r['kind'][:11], short_how(r['how']), r['freq'], r['reach_lines'], r['label'][:90],
            r['file'].replace('src/org/traincontrol/', ''), r['member'][:60] if r['list'] == 'user' else '',
            r['line'], tests or '-', more))


def summary(rows, state_rows, elapsed, gaps, out):
    out.write('action-inventory: %d user rows, %d state rows, %.1f s\n\n' % (len(rows), len(state_rows), elapsed))
    for lst, rs, hows in (('user', rows, ('as-the-user', 'by-handler', 'none')),
                          ('state', state_rows, ('as-the-railway', 'by-handler', 'none'))):
        out.write('List %s:\n' % lst)
        out.write('  %-48s %6s %8s %8s %6s\n' % ('area', 'rows', short_how(hows[0]), 'handler', 'none'))
        for area, label in AREAS.items():
            sub = [r for r in rs if r['area'] == area]
            c = collections.Counter(r['how'] for r in sub)
            out.write('  %-48s %6d %8d %8d %6d\n' % (label, len(sub), c[hows[0]], c['by-handler'], c['none']))
        c = collections.Counter(r['how'] for r in rs)
        out.write('  %-48s %6d %8d %8d %6d\n' % ('all', len(rs), c[hows[0]], c['by-handler'], c['none']))
        if lst == 'user':
            out.write('\n  by kind:\n')
            for kind in sorted(set(r['kind'] for r in rs)):
                sub = [r for r in rs if r['kind'] == kind]
                c = collections.Counter(r['how'] for r in sub)
                out.write('  %-48s %6d %8d %8d %6d\n' % (kind, len(sub), c[hows[0]], c['by-handler'], c['none']))
        out.write('\n')
    g = sorted([r for r in rows + state_rows if r['how'] in ('none', 'by-handler')], key=gap_score, reverse=True)
    # shortcuts that differ only in their key (F1..F24, the digits) rank once, as a family
    families = collections.OrderedDict()
    for r in g:
        key = ((r['kind'], r['file'], r['member'], tuple(r.get('targets', [])), r['how']) if r['kind'] == 'key'
               else r['id'])
        families.setdefault(key, []).append(r)
    out.write('Top %d gaps (freq, none before by-handler, lines reached; alike shortcuts once):\n' % gaps)
    for members in list(families.values())[:gaps]:
        print_rows(members[:1], out)
        if len(members) > 1:
            out.write('      ... and %d alike: %s\n' % (len(members) - 1, ', '.join(m['id'] for m in members[1:])))
    out.write('\nTier 1 clusters (before Phase 3):\n')
    for name in CLUSTERS:
        sub = [r for r in rows if r['cluster'] == name]
        c = collections.Counter(r['how'] for r in sub)
        out.write('  %s: %d rows, user %d, handler %d, none %d\n' % (name, len(sub), c['as-the-user'],
                                                                    c['by-handler'], c['none']))
        print_rows(sorted(sub, key=lambda r: r['id']), out)


def main():
    parser = argparse.ArgumentParser(description=__doc__.split('\n\n')[0].strip(), epilog=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--root', default=DEFAULT_ROOT, help='project root (default: two folders above this script)')
    parser.add_argument('--summary', action='store_true', help='totals per list, area and kind; the ranked gaps; '
                        'the Tier 1 clusters')
    parser.add_argument('--json', action='store_true', help='every row with its tests and evidence, as JSON')
    parser.add_argument('--list', choices=('user', 'state'), help='only this list')
    parser.add_argument('--area', choices=list(AREAS), help='only this area')
    parser.add_argument('--how', choices=('as-the-user', 'as-the-railway', 'by-handler', 'none'), help='only rows '
                        'classified so')
    parser.add_argument('--entry', metavar='REGEX', help='only rows whose label, member, names or keys match; prints '
                        'every test credited and why')
    parser.add_argument('--gaps', type=int, default=30, metavar='N', help='how many gaps --summary ranks (default 30)')
    args = parser.parse_args()

    root = os.path.abspath(args.root)
    rows, state_rows, tests, elapsed = run(root)
    allrows = rows + state_rows
    if args.list:
        allrows = [r for r in allrows if r['list'] == args.list]
    if args.area:
        allrows = [r for r in allrows if r['area'] == args.area]
    if args.how:
        allrows = [r for r in allrows if r['how'] == args.how]
    if args.entry:
        rx = re.compile(args.entry, re.I)
        allrows = [r for r in allrows if rx.search(' '.join([r['label'], r['member']] + r['names'] + r['keys']))]

    if args.json:
        out = []
        for r in allrows:
            d = {k: v for k, v in r.items() if k not in ('code_calls', 'reach')}
            d['file'] = r['file']
            d['reach'] = r.get('reach', [])[:40]
            out.append(d)
        json.dump({'elapsed_s': round(elapsed, 1), 'rows': out}, sys.stdout, indent=1, sort_keys=True,
                  default=lambda o: sorted(o) if isinstance(o, set) else str(o))
        sys.stdout.write('\n')
        return
    if args.summary:
        summary([r for r in allrows if r['list'] == 'user'], [r for r in allrows if r['list'] == 'state'], elapsed,
                args.gaps, sys.stdout)
        return
    print_rows(allrows, sys.stdout)
    if args.entry:
        for r in allrows:
            sys.stdout.write('\n%s %s\n  where: %s#%s:%s  names=%s keys=%s handlers=%s targets=%s\n' % (
                r['id'], r['label'], r['file'], r['member'], r['line'], r['names'], r['keys'], r.get('handlers'),
                r.get('targets')))
            for t in r['tests']:
                sys.stdout.write('    %-14s %-70s %s\n' % (t['how'], t['test'], t['why']))
    sys.stdout.write('\n%d rows, %.1f s\n' % (len(allrows), elapsed))


if __name__ == '__main__':
    main()
