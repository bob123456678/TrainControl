# TrainControl: rules for anyone building here

Java 8 Swing; the owner is Adam.  These rules bind every builder, human or agent; a brief may add to them, never take
them away.  This file stays on the development branch (`docs/tools/release-tree.sh` leaves it off master).

## Comments stay lean (Adam's rule)

Agents pay to read every comment line, so:

- A comment says only what the code cannot: a non-obvious why, a constraint, a contract.  None where the code already
  says it; never restate the code in prose.  Private and package-private javadoc is 1 to 3 lines; longer is a public
  contract or too long.
- **No history:** what the code used to do, which review round found it, who said what when, how a bug was found.
  That lives in the tracker row the id resolves to, and in git: write the commit message, not a comment.
- **Only Adam's tracker ids** (OB-###, MT-###, FR-###), at most one per rule.  Review-finding ids (RSA52-B1, GST-C3,
  RC-B2 and every other prefix in `docs/manual-tests/findings.tsv`) never go in code comments.
- **Quote Adam only where his words are the rule**, verbatim, without the date.
- A block over 15 lines is a javadoc contract or too long.  Javadoc sits directly on its declaration.
- Do not copy the density of the comments around you: older code is still being trimmed.
- In tests, keep what the test asserts and why it matters, the `MUTATION:` note (the production change that turns it
  red), and why a non-obvious wait or fixture was chosen.  Never edit assertion messages to shorten them.
- `regression.testCommentsStayLean` counts history, long blocks and review ids in comments; its numbers only go down.
- A comment-only change still runs the tests that read that file's source (grep `test/` for its file name): a few
  find code by a comment's words.

Good: `// A train standing across the switch blocks both roads behind it (OB-271).`
Not: `// OB-271 (2026-09-14): Adam reported ... Round 112 found ...; RSA61-B2 widened it.  Before that ...`

## How to build here

**Design**
- **One rule, one place.**  Before writing a check, find the method that already answers it (grep the message key or
  the rule's name) and call it; a guard and the control it grays out ask the same method.  A rule spelled twice is
  this project's most repeated defect.
- **Fix the rule, then its siblings:** grep a changed rule's other callers and copies; change or test them in the same
  commit.
- **Refuse little.**  A warning or error the user can act on beats refusing the edit.  Every guard needs a way past;
  every error names its remedy.
- **Do what was asked.**  No widening, renaming or tidying beyond the brief.  A rule's second copy, code from before
  v2.7.0, and public methods on the API classes go only on Adam's word: report them instead.
- **Decide from the store the event writes** (the setup, or the running layout).  A new field needs its place in the
  copy constructor, the snapshot and undo.
- **A window's actions** act on the page being edited even when the main window shows another; a refused or canceled
  action leaves the window as it was; dialogs hang off the window that asked; unsaved work is asked about only once
  the action will go ahead.  Hold the work to these whether or not the brief states them.
- **Keys keep working:** a new control is `setFocusable(false)` where the window's keys must work with it clicked.  A
  change to `LayoutEditor` runs `ui.testTheEditorNamesItsShortcuts` and `ui.testTheTrackEditorByHand`.
- **US English** everywhere: gray, canceled, behavior, color, -ize.

**Tests**
- Red before green: show each new claim failing without the change; a claim that never failed proves nothing.
- Test what the user does and what comes of it, through the real door, not the record the code just wrote.
- One `MUTATION:` note per claim.  Run mutations only when the brief asks or the claim is new.
- A test that copies Adam's settings sets what it needs, never toggles them; one that sets a preference puts it back.
- Never edit an existing guard or ratchet test to make it pass: report it (a brief may name a baseline to update).
- Read the "Total tests run" line: a class that skipped everything is not green, and a failed compile leaves old
  reports behind.
- Known red: `core.testMockCentralStation` (4 failures, the baseline).  A record class red over a tracker count is the
  orchestrator's: report it, don't chase it.

**Working efficiently**
- Run classes with `TC_SCRATCH=<scratch dir> bash docs/tools/one.sh pkg.ClassA pkg.ClassB`
  (`label:pkg.Class.m1,pkg.Class.m2` for single methods); several in lanes with
  `TC_LANES=auto TC_CLASSES=<file of class names> TC_SCRATCH=<dir> bash docs/tools/battery.sh`, the whole suite
  without `TC_CLASSES`.  Run them as ONE background command and wait for its notification.  Both wait for a free
  test slot (`docs/tools/wait-for-slot.sh`; `TC_WAIT=0` refuses at once instead): never write a retry loop, never
  `sleep`, never poll the output file, and don't read the runners' source.
- Run only the classes your change can affect, plus the bookkeeping classes it touches: a new test class,
  `testEveryTestIsInTheBattery` (and `testSwitchingToACentralStationLayout` if it builds a window); a bundle,
  `testMessageBundles` and `testEveryMessageKeyIsAskedFor`; comments or javadoc, `testCommentsStayLean` and
  `testJavadocsAreAttached`; a `synchronized` member, `testNothingOnTheEventThreadTakesTheRailwaysMonitor`.  The full
  battery is the orchestrator's.
- Tools in `docs/tools/`, each documented in its header:
  - `affected.py <base> [<head>]` prints the classes a change should run, for `TC_CLASSES`.
  - `newtest.py [--write] <pkg.Class>` does a new test class's bookkeeping (`build.xml`, census, "Used by"; see
    `test/README.md`).  A new test class must be in `build.xml`.
  - `redgreen.sh (--base <commit> | --patch <file>) <claim>...` runs a claim red on the old code or a mutation,
    then green.
  - `env.sh <worktree>` sets up a worktree's `LocDB.data` and scratch folder and prints its `TC_SCRATCH`.
  - `support.DialogWatchdog` ends a test JVM stuck behind an unanswered modal dialog after 60 s (`TC_DIALOG_WATCH`)
    and reports its class with the dialog's title and text.
  - `regression.testNoNewCopies` fails on a new copy of 60 tokens or more, naming where; merge it, don't re-pin it.
- Grep first, then read the range you need (about 120 lines); never read a giant file whole.  Pipe long output
  through `head`.
- Edit with the Edit tool; a script, write with the Write tool (heredocs eat backslashes); never `sed -i` a Java file.
- A worktree you were given is clean and on the right commit: start work, don't re-check it.
- Answer your own questions from the repository first: this file, the brief, `docs/refactoring.md`,
  `docs/reference/behaviour.md` (the index of the area files in `docs/reference/behaviour/`), `triagedb.py show <id>`.
  Ask only for a decision that is Adam's.
- Report what changed, the evidence (red, green, the Total lines), and what is left.  No screenshots unless asked.

## Working here

- Java files check out CRLF; git normalizes line endings on commit (`core.autocrlf`) and Edit keeps them: don't check
  them by hand.  Message bundles (`src/org/traincontrol/resources/messages*.properties`, eight languages) are ASCII
  only: every other character as `\uXXXX`.
- New UI is hand-written Swing.  Never edit the NetBeans `//GEN-BEGIN`..`//GEN-END` regions or the variables block.
- Never touch `LocDB.data`, `UIState.data`, Adam's preferences, or anything under `cs2_sample_layout/` (his live
  railway).  Never connect to a Central Station.
- Stage files by name (never `git add -A`); never `git stash` (the stash is shared by every worktree).  Work on `dev`
  or a branch of it; moving or pushing `master` is a release and needs Adam.
- The tracker (MT-###, OB-###, FR-###, findings) is `docs/manual-tests/triage.db`, read and written only through
  `py -3 docs/manual-tests/triagedb.py`.  Manual-test entries are append-only.
- Docs for users (Readme.md, Automation.md) use no em dashes and no spaced hyphens as dashes.
