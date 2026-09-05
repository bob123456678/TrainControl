# Third validation pass, 2026-09-02: the tests written today, and the two runners

**Status:** open

**Prefix for citing this document: `V33`.** Cite findings from here as `V33-A1`, `V33-C4` and so on.
Nothing in `docs/reviews/` declares `V33`; the near-misses are `V2`-shaped (`FV2`, `SV2`, `TV2`) and
`VAL`/`VB`, all taken.

**Reviewed:** branch `autonomy-diagram-r0` at `2e83b737`, working tree as of 2026-09-02. Scope is
every test written or changed on 2026-09-02 (commits `409d4ce8` .. `2e83b737`) plus
`docs/tools/battery.sh` and `docs/tools/one.sh`. Severities per [README.md](README.md).

**Method, and its one limitation.** No test was run and no JVM was started - a battery was running
during this pass, and two runs redirecting the Java Preferences store at once is how the operator's
real railway was damaged on 2026-08-30. Every claim below is from reading the test, reading the
production method it exercises, and tracing what the mutation its javadoc names would change. Where
a conclusion depends on something only execution can settle, it says so.

`cs2_sample_layout/` was not read, written or checked out. Only this document was edited.

---

## Summary

| | Finding | Severity | Disposition |
|---|---|---|---|
| A1 | `one.sh` never reads TestNG's `Configuration Failures` line, so a class whose `@AfterClass` throws reports green - and two teardowns in this suite put Adam's real signals back | A | Open |
| B1 | `one.sh` does not call out `Skips:` or `Total tests run: 0`; the three cases `battery.sh` was repaired for are all still live in its sibling | B | Open |
| C1 | `battery.sh` reaps before each class and never after the last one, so the final class's leftovers survive the run - the exact state the new warning describes | C | Open |
| C2 | `one.sh` has no reaper at all, and tags its JVMs with no run id, so `reap.ps1` could not target them even if called | C | Open |
| C3 | `one.sh` ignores `javac`'s exit status and truncates its errors at eight lines | C | Open |
| C4 | `one.sh` always exits 0 - the trap `battery.sh` closed on purpose | C | Open |
| C5 | `testTheEditorWarnsAboutACopyWithNoWayOutOrIn`'s last assertion is orientation-dependent, which its javadoc says the test is not | C | Open |
| C6 | `testSwitchingAnAccessoryByHandAsksAboutProtectingSignals` does not catch one of the two mutations its javadoc names | C | Open |
| C7 | `testEveryDoorThatRunsARouteAsksIfItIsAlreadyRunning`'s negative half has no not-empty guard, so a renamed handler makes it vacuous | C | Open |
| C8 | `testTheAuditSeesTheReversalRoomRuleTheStagingPlannerDoesNotHave` has two preconditions that restate the two lines above them, with messages describing a state that cannot occur | C | Open |
| C9 | `testALongerApproachIsStillTriedWhenTheShortOneHasNoRoom` has no control that the short approach is refused, so it passes with the room rule absent | C | Open |
| C10 | The same test's "twenty times, because `getNeighbors` shuffles" reasoning does not hold - the defect is deterministic in that fixture | C | Open |
| C11 | `testThePaletteStillPlacesTiles` never disposes its `LayoutEditor`, unlike both siblings it was written from | C | Open |
| C12 | `testTheAffordancesAskTheGuardsOwnQuestion` says it reads the guard's question; it hardcodes both sides of the pair | C | Open |
| D1..D14 | Fourteen checks that came back clean, each with the mutation traced | D | Closed |

The reaper-path correction itself (`TS3-A1`) is sound and complete - see **D1**.

---

## A

### A1 - `one.sh` cannot see a teardown that threw

**Disposition: fixed, confirmed 2026-09-05** - fixed - `one.sh:470` reads `Configuration Failures` and counts it as a failure, citing this finding.

**Audited 2026-09-05.** The `**Status: open**` line below is the original and is kept as the historical record of what was believed when it was written.  It is NOT this finding's disposition.

**Status: open.**

`docs/tools/one.sh:174` decides what a class's run meant by grepping five patterns:

```sh
grep -E "Total tests run|FAILED|java.lang.Assertion|at regression|at core" "$S/one-run.txt"
```

TestNG's summary is two lines, and `battery.sh:398-407` says so in its own words, having been repaired
for it on 2026-08-25:

```
    Total tests run: 1, Failures: 0, Skips: 0
    Configuration Failures: 1, Skips: 0
```

None of `one.sh`'s five patterns matches the second line. `Configuration Failures` contains neither
`Total tests run` nor `FAILED` - the word on that line is `Failures`, and `grep -E` is case-sensitive.
So the line is not printed, and the only thing the operator sees is a first line reading
`Failures: 0`.

`battery.sh` treats that case as a failure, at `battery.sh:408` and `battery.sh:434-437`, and its
comment names what is at stake:

> The teardowns in this suite are load-bearing: `testBothProtectingSignalsAreThrown` puts two of
> Adam's real signals back to GREEN in its teardown and says why, and
> `testARouteDoesNotThrowSwitchesUnderATrain` clears the auto layout in its own. A teardown that threw
> would leave the railway changed and be reported as a clean run.

That sentence is still true of `one.sh`, which the header of that file correctly says is "the one used
all day". Graded A rather than B on the strength of that sentence: the consequence is the railway left
changed with the run reported clean, which is the definition A carries.

The fix is the two lines `battery.sh` already has - capture `Configuration Failures` and refuse to
call the class green when it is not `: 0` - and it belongs in the same commit as B1, because they are
one omission.

---

## B

### B1 - `one.sh` does not call out skips, or a class with no tests in it

**FIXED 2026-09-02 (`3c014e77`).**  `one.sh` counts skips and zero-test classes separately and says so, with `battery.sh`'s own sentence: a class that reported no failures because it ran nothing is not a green class.

**Disposition: fixed** - see the closure line above this one, which is where it was recorded; this audit confirms it.

**Audited 2026-09-05.** The `**Status: open**` line below is the original and is kept as the historical record of what was believed when it was written.  It is NOT this finding's disposition.

**Status: open.**

`one.sh:176-180` has exactly one verdict: a class that printed no summary "did not run". Everything
else is left to the operator's eye. `battery.sh` has four more branches, every one of them added
because the eye missed it:

- `battery.sh:438-450` - `Total tests run: 0` is not zero failures. Two support classes were counted
  among the green on every run for that reason.
- `battery.sh:452-467` - "GREEN IS NOT no failures". `core.testAutonomyDiagramSampleLayout` sat at
  13 tests, 0 passed, 13 skipped, and "every battery in this session counted it among the green".
- `battery.sh:414-428` - a heap failure said apart from a class fault, because "they read identically
  before this, which cost a round of hunting for a fault in three classes that were fine".

`one.sh` prints the summary line, so `Skips: 16` is on the screen - but the failure recorded in
`battery.sh`'s own comment is precisely that a human read that line and called it green, and anything
grepping this output for `Failures: 0` (which is the natural thing to do) gets a false pass with no
`***` line anywhere to contradict it. The memory of this project already carries the rule as
"green is not no failures ... require `Skips: 0` too"; only one of the two runners enforces it.

This is the same shape as the reaper path corrected today: a rule fixed in `battery.sh` and never
carried to the sibling that was extracted from it. `one.sh:9-20` states that exact history about the
concurrency guard ("not one of those corrections reached this file") and then does not check whether
any of the *reporting* corrections reached it either. They did not: none of the four did.

---

## C

### C1 - `battery.sh` never reaps after the last class

**FIXED 2026-09-02.**  The reap runs once more after the loop.  The finding's own point is what makes it worth fixing rather than shrugging at: the class most likely to leave a JVM behind is simply the last one alphabetically, so it is not a symptom of anything being wrong - and the JVM it leaves trips the NEXT run's probe with a message saying the check clears itself, which it does not.

**Disposition: fixed** - see the closure line above this one, which is where it was recorded; this audit confirms it.

**Audited 2026-09-05.** The `**Status: open**` line below is the original and is kept as the historical record of what was believed when it was written.  It is NOT this finding's disposition.

**Status: open.**

The reaper call is at the top of the loop body (`battery.sh:367-381`), before the `java` invocation
for the class about to run. There is no call after the loop (`battery.sh:470` onwards is the
fingerprint, the counts, and the exit code). So the last class of a run leaves whatever it leaves, and
the next run's start-of-run probe finds it.

That is verbatim the state the new warning was written to describe (`battery.sh:299-303`):

> A JVM left behind by one class poisons every class after it, and trips the next run's start-of-run
> probe with a message that says the check clears itself. It will not.

One `powershell.exe ... -File "$REAPER" -RunId "$RUN_ID"` after the loop closes it. Graded C because
it is pre-existing rather than introduced by today's correction, and because a leftover requires the
JVM to outlive the command substitution that waits on it.

Related and worth one line in the same commit: the warning is printed roughly forty lines before the
loop, and the summary at the end of a several-hundred-line run repeats none of it. The whole file's
doctrine is that "the runner decided what a run meant by looking at part of it" is the defect; a
warning only visible in the part that has scrolled away is the same shape.

### C2 - `one.sh` has no reaper, and its JVMs carry no run id

**FIXED 2026-09-02.**  `one.sh` names its run `one-$$`, passes `-Dtraincontrol.batteryRun` to every JVM it starts, resolves `reap.ps1` from its own directory the way `TS3-A1` made `battery.sh` do, and reaps before each class.  Proven by running: a JVM tagged `one-99991` was started and `reap.ps1 -RunId one-99991` killed it, leaving nothing else on the machine touched.  `one-` and `battery-` cannot collide because the id is matched whole.

**Disposition: fixed** - see the closure line above this one, which is where it was recorded; this audit confirms it.

**Audited 2026-09-05.** The `**Status: open**` line below is the original and is kept as the historical record of what was believed when it was written.  It is NOT this finding's disposition.

**Status: open.**

`one.sh` starts a JVM per class (`one.sh:171-172`) with `-Dtraincontrol.anyReceivePort=true` and
nothing else. `reap.ps1:47-51` matches on `traincontrol.batteryRun=<id>` and deliberately nothing
broader - the file records at `reap.ps1:17-21` that matching `anyReceivePort` killed hand-run tests.
So `one.sh`'s leftovers are not reapable by the existing tool at all, and there is no call site to
reap them from.

The consequence is the one C1 names, arriving from the other side: a leftover `one.sh` JVM trips the
next run's probe (`one.sh:86-103`, `battery.sh`'s equivalent) with the message "Nothing needs
deleting: this check clears itself when those processes exit" - which is the sentence
`battery.sh:299-303` now says is wrong.

The fix is two lines: give `one.sh` a `RUN_ID` of its own on `JAVA_FLAGS`, and call the same reaper
between classes and after the loop, resolved the way `battery.sh:295` now resolves it.

### C3 - `one.sh` does not check whether the tree compiled

**FIXED 2026-09-02.**  `javac`'s status is tested, not `head`'s.  A tree that does not compile now prints *THE WORKING TREE DOES NOT COMPILE - nothing was run*, lists the errors and exits 2 - proven by putting a broken file in `test/regression/` and watching it refuse, exit code and all.  This also closes `TS3-C5`, which is the same line read through the `| grep | head` hazard this file's own comment forty lines down warns about.

**Disposition: fixed** - see the closure line above this one, which is where it was recorded; this audit confirms it.

**Audited 2026-09-05.** The `**Status: open**` line below is the original and is kept as the historical record of what was believed when it was written.  It is NOT this finding's disposition.

**Status: open.**

```sh
"${TC_JAVAC:-...}" -nowarn -encoding UTF-8 \
    -d "$BUILD" -cp "$CP" @"$S/one-files.txt" 2>&1 | grep -i "error" | head -8
```

(`one.sh:153-154`.) The pipeline's status is `head`'s, it is not tested, and the script runs the tests
either way against a build directory that was emptied a few lines above. `battery.sh:258-265` does the
opposite and says why - `*** THE WORKING TREE DOES NOT COMPILE - nothing was run ***`, `exit 2`, and
twenty error lines rather than eight.

The `head -8` is also the same SIGPIPE construct `one.sh:158-166` explains at length that it removed
from the *run* loop ("`... | grep | head -30` closes the pipe as soon as head has its thirty lines").
It was left on the compile line.

### C4 - `one.sh` always exits 0

**FIXED 2026-09-02 (`3c014e77`).**  A `BAD` counter, and a non-zero exit when anything in the run was not clean.

**Disposition: fixed** - see the closure line above this one, which is where it was recorded; this audit confirms it.

**Audited 2026-09-05.** The `**Status: open**` line below is the original and is kept as the historical record of what was believed when it was written.  It is NOT this finding's disposition.

**Status: open.**

`one.sh:204` is a bare `exit 0`, reached whatever any class reported; the only non-zero path is the
live-layout fingerprint at `one.sh:201`. `battery.sh:500-521` added an exit code on purpose, with the
reason spelled out:

> Nothing reads it that way today - it is read by eye - so this costs nothing now and stops being a
> trap the first time somebody puts it behind `&&` or in CI, which is exactly when nobody would be
> watching the text.

Same argument, same file family, not applied. Cheap to fix alongside A1 and B1, since all three want
the same per-class verdict variable.

### C5 - the copy-check test's last assertion depends on which way round `TOWARD_A` points

**FIXED 2026-09-03.**  The trapping direction is put back before `subjectsOf` is asked, and it is asked
rather than assumed - `outA.contains("Middle") ? TOWARD_A : TOWARD_B` - so which of the two traps the
arrival stays the tile's business, which is this test's whole design.  The javadoc now names the last
assertion as the exception to "true whichever way round they are".

`test/core/testAutonomyDiagramSession.java:689`. The javadoc states the design:

> **Which of TOWARD_A and TOWARD_B points which way is the tile's business, not this test's**, so both
> are applied and the assertion is that exactly one of them traps the arrival. True whichever way round
> they are, and false if nothing is watching.

That holds for the three assertions that compare `outA` against `outB`. It does not hold for the
fourth (`testAutonomyDiagramSession.java:766-767`):

```java
assertFalse(subjectsOf(org.traincontrol.automationui.AutonomyChecks.COPY_NO_WAY_OUT).isEmpty(),
    "the session knows about the trapped arrival but the editor never reports it");
```

`subjectsOf` calls `session.check()` against the CURRENT state, and the current state is whatever the
last `setDirection` left - `Direction.TOWARD_B`, at line 741. Traced:

- `TilePorts.java:248,253` give `STRAIGHT` and `FEEDBACK` the route `route(Side.E, Side.W)`, so A is
  the east side and B the west; `TileGraph.java:1418` reads `TOWARD_A` as "may travel toward side A".
- Under `TOWARD_A` the far section is eastbound only. Middle's eastbound arrival can carry on east,
  so it has a way out; `outA` is empty and the finding under test does not exist.
- Under `TOWARD_B` the far section is westbound only. Middle's eastbound arrival has nowhere to go,
  its westbound sibling is healthy, `AutonomySession.badCopies` reports it, and the assertion passes.

So the test passes today, on the accident that the trapping direction is the one applied second.
Swap the two `setDirection` calls, or write `route(Side.W, Side.E)` in `TilePorts` - a rename with no
behavioural meaning - and this assertion fails with a message accusing the editor of not reporting a
trapped arrival that does not exist in that state.

Either evaluate it inside the branch that found `Middle`, or re-apply the trapping direction before
asking `subjectsOf`. Whichever is chosen, the javadoc's "true whichever way round they are" needs the
exception written into it.

### C6 - the protecting-signal test does not catch one of the two mutations it names

**FIXED 2026-09-03.**  The route door is read as a BODY now - `heldReason`, through `withoutComments` -
rather than as a whole file whose comments discuss the rule at length.  Mutation-confirmed by replacing
the call's condition with `false`: the test fails.

The tile door's half was already closed by `V32-B2`'s fix, which asserts the helper's body.

`test/regression/testEditorSurfaceRules.java:625`. Javadoc: *"MUTATION: deleting either call to
`protectsAnOccupiedSquare` fails this."* There are two such calls:

- `src/org/traincontrol/marklin/MarklinRoute.java:475` - pinned, by `route.contains("protectsAnOccupiedSquare")` at test line 650.
- `src/org/traincontrol/gui/LayoutLabel.java:1385` - **not pinned by anything**.

What the test asserts about `LayoutLabel` is that it calls `aboutToClearProtection` for both
accessories (lines 416-417 of that file) and that it contains
`if (accessory.isStraight()) return false;` (line 1383). Both of those are inside or around
`aboutToClearProtection`; neither says that method delegates to the shared rule. Replace
`LayoutLabel.java:1385` with `return false;` - the smallest change that still compiles - and the
diagram's accessory tile stops asking about protecting signals entirely while this test stays green.

One line closes it: `assertTrue(label.contains("protectsAnOccupiedSquare(accessory)"), ...)`.

Two smaller points on the same test, both worth fixing in the same edit because the file's other tests
already do it this way: the `label`, `route` and `layout` assertions read the whole file rather than a
method body, and none of them goes through `withoutComments`. `LayoutLabel.java:1367` is a comment
containing the string `protectsAnOccupiedSquare`; it happens to be in the file the test does *not*
grep for that string, which is luck rather than design.

### C7 - the route-door test's negative half can go vacuous silently

**FIXED 2026-09-03.**  `assertFalse(door.isEmpty(), ...)` guards the negative, so a renamed or
regenerated `//GEN-FIRST` handler fails the test instead of turning it into a no-op that reads as
protection.

`test/regression/testEditorSurfaceRules.java:602-608`:

```java
String door = withoutComments(bodyOf(ui,
    "private void RouteListMouseClicked(java.awt.event.MouseEvent evt)"));

assertFalse(door.contains("routesExecuting"), ...);
```

`bodyOf` returns `""` when the declaration is not found (`testEditorSurfaceRules.java:1076`). An empty
string contains nothing, so a renamed or regenerated handler turns this assertion into a no-op that
still reads as protection. `RouteListMouseClicked` is a NetBeans `//GEN-FIRST` method, which is
exactly the kind that gets regenerated.

The positive assertions in the same test are self-guarding (an empty body fails them), and the sibling
test in `testErrorsStopTheSetupRunning.java:206,216` guards both of its bodies with
`assertFalse(x.isEmpty(), "... has moved or been renamed")`. This one needs the same line.

Everything else about the test is sound - see D6.

### C8 - two preconditions that restate the two lines above them

**FIXED, by `V36-B2` and `V37-B2`** (verified 2026-09-03).  Both restated preconditions are gone: what
stands there now says there is no precondition about `mustBackIn` at all, records the two wrong attempts
at one, and rules the confound out with a control - a train that fits reaches the berth.  See `V34-C1`,
which is the same finding one round later.

`test/core/testHomeStaging.java:181-197`:

```java
tooLong.setTrainLength(40);
tooLong.setReversible(true);

assertEquals(tooLong.getTrainLength(), Integer.valueOf(40),
    "precondition: the locomotive has no train length, so the rule under test is not armed ...");

assertTrue(tooLong.isReversible(),
    "precondition: a non-reversible train is refused a terminus by mustBackIn instead, ...");
```

`Locomotive.setTrainLength` (`Locomotive.java:1417`) and `setReversible` (`Locomotive.java:1372`) are
bare field assignments with no validation, so neither assertion can fail, and the message on the first
one describes a state - "the locomotive has no train length" - that the line above it has just made
impossible. This is the anti-pattern the README states as "assert the precondition that makes a test
meaningful", read backwards: a check that restates the fixture is not a check.

The test's real preconditions are the two that read back through the parse - `getPoint("HS D").isTerminus()`
and the `getEdge("HS A", "HS D").getLength()` band at lines 199-208 - and its real control is the
`isPathClear` assertion at 210-214. Those are all sound (D8). The two above are noise that reads as
rigour.

The newer test in the same file gets this right: `testALongerApproachIsStillTriedWhenTheShortOneHasNoRoom`
sets `loc.setTrainLength(8)` and asserts nothing about it, asserting the *parsed* edge lengths instead.
Delete the two lines, or replace them with the parse-side facts.

### C9 - the longer-approach test has no control, and passes with the rule absent

**FIXED, by the round that followed** (verified 2026-09-03).  The control this finding asked for is in
the test: a twenty-unit train, too long for either way in, must be refused - so "a plan was found" can
no longer be satisfied by the room rule's absence.  It is cited there as `V33-C9`.

`test/core/testHomeStaging.java:3353`. The whole of what it asserts about behaviour is
`assertEquals(refused, 0, ...)` at line 3406: over twenty attempts, `planReturnToHome()` must always
succeed. It never looks at the plan it got.

Delete `HomeStaging.java:1039-1044` - the room check inside `firstClearRoute` - and the test still
passes, because the planner then simply returns the direct `HS A -> HS D` route and the plan is
possible. What the fixture's preconditions establish is that the edges are 5, 5 and 5 and that HS D is
a terminus; nothing in the test establishes that the short approach is actually refused, which is the
premise the whole thing rests on.

The sibling test forty lines up does exactly this and is the model to copy: `testTheAuditSees...`
asserts `assertFalse(layout.isPathClear(layout.bfs(...), tooLong))` and calls it "control:" in the
message. Here the equivalent is one line - the direct path must be refused for this locomotive - plus,
ideally, an assertion that the returned move's path has two edges rather than one.

Two mitigations, which are why this is C and not B. The rule's *presence* in `firstClearRoute` is
pinned by the sibling test, through `auditAgainstRuntime() == 0`: remove the planner's copy and the
audit reports a disagreement and that test fails. And with the rule present and correctly ordered, the
long route is the only plan available in this fixture. So nothing is unprotected today; what is
missing is the test's ability to say why it passed.

### C10 - "twenty times, because `getNeighbors` shuffles" is not why

**FIXED, by the round that followed** (verified 2026-09-03).  The comment says the pre-fix code fails
all twenty and why - the queue is FIFO and both ways into HS D leave the origin, so the direct one is
recorded during HS A's own expansion whatever the shuffle did - and says why the repetition is kept
anyway.  Cited there as `V33-C10`.

`test/core/testHomeStaging.java:3383-3392` says a single attempt would pass about half the time,
because only the run that reaches the short approach first can show the defect. Traced through
`HomeStaging.firstClearRoute`, that is not so:

- The queue is an `ArrayDeque` polled from the head and added to the tail (`HomeStaging.java:943,
  1072`) - breadth-first, not scored.
- The expansion of `HS A` iterates `this.layout.getNeighbors(current.at)` in one `for` loop
  (`HomeStaging.java:976`), so **both** the direct `HS A -> HS D` edge and the `HS A -> HS B` edge are
  processed before `HS B` is ever polled, whatever order the shuffle put them in.
- Under the pre-fix ordering the direct arrival is written into `seen` under key `HS D/straight` with
  an empty command map, and `alreadyReached` (`HomeStaging.java:1154-1176`) treats an empty recorded
  map as dominating everything. So the later `HS A -> HS B -> HS D` arrival is pruned in **both**
  shuffle orders.

The defect therefore reproduces 20 times out of 20, which is what the commit message for `e6791631`
also says. Repeating twenty times is harmless and cheap; the reason written beside it is wrong, and in
this project the comment is the design record. Either correct the sentence or say the repeats are
belt-and-braces against a future scored queue.

### C11 - the palette test leaves its editor window undisposed

**FIXED, by `V33-C11`'s own commit, and improved 2026-09-03.**  The editor is disposed in the `finally`.
The sandbox restore that this arrangement put last is now in a `finally` of its own, so nothing the
disposals throw can skip the preference - see `V34-C7`.

`test/regression/testThePaletteStillPlacesTiles.java:188-198` disposes `ui[0]`, stops the model and
closes the sandbox. It never disposes `editor[0]`, which is a `LayoutEditor` and therefore a
`PositionAwareJFrame` (`LayoutEditor.java:46`) - a real top-level window.

Both siblings this test was written from do dispose it:

- `test/regression/testDiagramShiftKeepsSetup.java:410-412`
- `test/regression/testLayoutEditorBulkEdits.java:647-649` and `:807-809`

The window also outlives `sandbox.close()`. `PositionAwareJFrame` captures
`TrainControlUI.getPrefs()` at construction (`PositionAwareJFrame.java:24`) and writes on move, resize
and close, so an editor still alive after the sandbox has gone is at least a loose end near the thing
the sandbox exists to protect. Graded C rather than higher because the frame's `prefs` reference was
taken inside the sandbox and TestNG's `main` calls `System.exit`, so the leak is bounded by the JVM.

One line in the existing `finally`, matching the siblings.

(The undeleted `tc-palette` temp directory at line 73 is *not* a finding: both gesture-style siblings
leave theirs too, so it is a house habit rather than a deviation.)

### C12 - "the guard's question, whichever one that is" is not what the test does

**FIXED 2026-09-03** - the comment, which is what was wrong.

The claim "it reads the guard and requires the affordance to ask the same thing" is gone, and what
replaces it says why no textual rule could do that: the guard asks the SESSION (`hasErrors()`) and the
affordance asks the WINDOW's wrapper for it (`autonomyHasErrors()`), so the two literals differ by
design and no comparison can tell that pairing from a divergence.  What the test does is name both
halves in one place, so widening one and not the other fails here rather than on Adam's railway.

`test/regression/testErrorsStopTheSetupRunning.java:208-227`. The comment says:

> THE GUARD'S QUESTION, WHICHEVER ONE THAT IS (TS3-B6). ... It reads the guard and requires the
> affordance to ask the same thing.

It does not. It requires the guard body to contain the literal `hasErrors()` and, separately, the
affordance body to contain the literal `autonomyHasErrors()`. Both sides are hardcoded; what changed
is that there are now two literals instead of one. Widen the guard again and the *guard* assertion
fails, which is a fair thing to happen - but the mechanism is a pinned pair, not a comparison, and the
comment claims the second.

The finding is the comment, not the assertions: both are correct against the current code
(`TrainControlUI.java:5183` for the guard, `:20166-20167` and `:20183-20187` for the affordance, which
genuinely delegates to `session.hasErrors()`), and D11 records what was traced.

---

## D

Everything below was checked and found sound. Each entry says which mutation was traced to clear it.

### D1 - the reaper path correction (`TS3-A1`) is right, and complete

`battery.sh:295` now resolves `REAPER="$(cd "$(dirname "$0")" && pwd)/reap.ps1"`, guards it with
`-f`, warns and disarms rather than refusing (`:297-306`), and skips the call when disarmed
(`:377-381`).

- The file is there: `docs/tools/reap.ps1` exists, 2,909 bytes.
- The diagnosis in the comment is factually right. `git log --follow` shows `reap.ps1` moved from
  `tools/` to `docs/tools/` in `fb3722f5` on 2026-08-30, and that commit's own `--name-status`
  confirms `R100 tools/reap.ps1 -> docs/tools/reap.ps1` and `R099` for `battery.sh` itself. The old
  literal `$(pwd)/tools/reap.ps1` was correct up to that commit and dead after it.
- **Swept for twins**, which is this project's most repeated miss: `grep` for `pwd)/`, `$(dirname`,
  `tools/reap`, `sh tools/`, `tools/battery.sh` and `tools/one.sh` across `*.sh`, `*.md`, `*.xml`,
  `*.java` and `*.bat` outside `docs/tools/` returns nothing. `battery.sh` has no other
  self-relative path and `one.sh` has none at all. The correction is the only site.
- Resolution survives being invoked from elsewhere (`$0` is `docs/tools/battery.sh` from the root,
  `./battery.sh` from inside the folder; both give the right `dirname`), and the POSIX path handed to
  `powershell.exe -File` is the same form the pre-move version used successfully for five days, so
  MSYS argument conversion is not a new dependency.

MUTATION traced: point `REAPER` at a name that does not exist and the run prints the warning block and
skips every reap, rather than swallowing an error into `/dev/null` as it did from 2026-08-25 to
2026-09-02. C1 above is the gap that remains.

### D2 - `testTheEditorWarnsAboutACopyWithNoWayOutOrIn` (apart from C5)

`testAutonomyDiagramSession.java:689`. Fixture `deadEndRun()` is a straight run of seven squares with
sensors at 1, 4 and 7 on page `main`. Traced against `AutonomySession.badCopies`
(`AutonomySession.java:2001-2073`) and `hasAHealthySibling` (`:2249-2260`):

- The two opening assertions are a real control: with both ends marked `CAN_REVERSE` and no direction
  set, both lists are empty. Without the `CAN_REVERSE` on the stub the fixture would report two
  trapped arrivals before anything had been done to it, which the inline comment says and which is
  correct - a dead end nothing may turn at has no way out.
- With the far section barred one way, `Middle` splits and exactly one of its two copies is trapped;
  the other is healthy, so `hasAHealthySibling` lets the finding through. That is the only shape no
  square-level check can see, which is what the test claims.
- The `assertFalse(inA.contains("FarEnd") || inB.contains("FarEnd"))` half is real, not decorative:
  `FarEnd` emits one copy, that copy is stuck in both directions, and the healthy-sibling restriction
  is the only thing that keeps it out of the report. Remove that restriction
  (`AutonomySession.java:2067`) and this assertion fails.
- `assertTrue(outA.contains("Middle") != outB.contains("Middle"))` is the assertion that stops a check
  which ignores direction from passing.

MUTATION traced (the one the javadoc names): asking `reducer.getPoints()` - the squares - instead of
the built graph finds nothing here, because the square is on a perfectly connected run. Confirmed
against `AutonomyChecks`: every check in that file works in `TileKey`s, and `Middle` as a square is
reachable and reaches other squares in both states.

### D3 - `testTheEditorWarnsAboutACopyThatReachesNoOtherStation`

`testAutonomyDiagramSession.java:802`. Traced through
`AutonomySession.destinationCopiesReachingNoStation` (`:2124-2196`) and `reachesAStationElsewhere`
(`:2208-2246`).

With the stub reversible the graph is a cycle `NearEnd -> Middle_east -> Stub -> Middle_west ->
NearEnd`, every station copy reaches a station on another square, and the control is genuinely empty.
Taking the turn away removes the `Stub -> Middle_west` edge; `Middle_east` then walks only to `Stub`,
which is not a station, and its westbound sibling is healthy - so it is reported and its square is
not.

MUTATION traced (the one the javadoc names): make `reachesAStationElsewhere` count any Point rather
than a station, and `Middle_east` reaches `Stub` on a different square, returns true, and the map is
empty - `assertFalse(stranded.isEmpty())` fails. The `assertFalse(subjectsOf(STATION_REACHES_NOTHING).contains("Middle"))`
guard is also non-vacuous: `AutonomyChecks.java:1165-1168` builds that finding with
`station.getName()` as the subject, which is exactly the string `"Middle"`, so `List.contains` matches
if the square-level check ever fires.

The `assertFalse(String.valueOf(stranded).contains("7,1"))` line is weaker than it looks - the loop
iterates `stations` only, so a non-station can never be reported - but it costs nothing and is
harmless.

### D4 - `testAShutPlainSquareDrawsItsCrossOnTheRunningDiagram`

`testAutonomyDiagramSession.java:904`. `AutonomySession.staticAnnotationFor:4718` reads

```java
boolean worthABadge = store.isStation(tile) || isTurnAround(tile) || shut;
```

The fixture square is a plain sensor, so the first two terms are false and `shut` is the only thing
that can earn a badge - which makes the control (`open == null || open.getBadge() == null`) a real
one: the annotation is non-null, because the square is a Point, and its badge is null.
`Badge.isImpassable()` returns the ninth constructor argument (`TileAnnotation.java:283-286`), which
is `shut`, not the fourth (`shut || !isAutoDestination(tile)`), so the third assertion tests the flag
it names.

MUTATION traced (the one the javadoc names): drop `|| shut` and `worthABadge` is false, the badge is
`null`, and `assertNotNull(shut.getBadge())` fails while `assertNotNull(shut)` still passes - which is
the discrimination the test needs.

### D5 - `testAShutPlainSquareReachesTheRunningGraph`, and the `TS3-B1` repair to its control

`testAutonomyDiagramSession.java:958`. The repaired control now asks
`session.pointNamesFor(session.pointNameForTile(plain))` - the emitted copy names - rather than the
base name, which is the right correction: `AutonomyBuilder.nodeName:708-712` suffixes every copy of a
square that emits more than one Node, so the base name of a split square is never in the built
configuration's `points` array and the old control could not fail.

Verified that the two name spaces really do meet. `StationIndex` is built from
`builder.baseNames()`/`tilesByName()` (`StationIndex.java:71-75`), `baseNames()` maps every emitted
name including the unsplit case ("An unsplit Point maps to itself", `AutonomyBuilder.java:1199`), and
`buildConfigurationForInspection` uses `builder(globals())` while the index uses `builder(null)` -
`AutonomySession.builder:2283-2293` shows the naming inputs are identical in both, so the two cannot
disagree.

MUTATION traced (the one the javadoc names): restore
`if ("active".equals(key) && !point.isStation()) continue;` and neither copy of the plain square
carries `active: false` into the built graph, so the per-copy loop at the end fails.

### D6 - `testEveryDoorThatRunsARouteAsksIfItIsAlreadyRunning` (apart from C7)

`testEditorSurfaceRules.java:580`. Checked against the source it greps:

- `TrainControlUI.java:16107` is `public void executeRoute(String route)`, so `bodyOf` finds it.
- `:16121` is `if (route != null && routesExecuting.contains(route))`, real code, and `:16132` is
  `routeStarted(route);` - so the ordering assertion holds and is the right way round (an absent
  `routeStarted(` makes `indexOf` return -1 and the assertion fail, not pass).
- `RouteListMouseClicked` (`:19290`) mentions `routesExecuting` only inside comments, which
  `withoutComments` strips; its two calls to `executeRoute` are unguarded, which is the point.

MUTATION traced (the one the javadoc names): move the guard from `executeRoute` into the play-button
branch and the first assertion fails (the funnel no longer asks) and the third fails (the door now
does). Both halves react, as claimed.

### D7 - `testEveryLatchRaiseHasAWayDown`, the three-link version

`testEditorSurfaceRules.java:2193`, three new assertions. All three declarations resolve
unambiguously - `layoutEditingComplete(Runnable after)` at `TrainControlUI.java:19382`,
`layoutRefreshComplete(Runnable after)` at `:19436`, `layoutEditingCompleteThen(Runnable after)` at
`:19533`; the `(Runnable after)` in the search string keeps the no-arg overload and the `...Then`
variant apart.

- `layoutEditingCompleteThen` has the `catch (RuntimeException | Error ...)` and `once.run()` the
  first assertion demands, and no `finally` - so the old single-`finally` form fails it.
- `layoutEditingComplete` posts `invokeLater` from a `finally` around `refreshLayouts()`, and the
  `finally` precedes it in the body, so the index comparison holds.
- `layoutRefreshComplete` runs `after.run()` from a `finally` around `layoutRefreshCompleteInternal()`.

MUTATION traced: put the guarantee back as one `finally` in `layoutEditingCompleteThen` - the form
`SVN-A3` describes - and the first and third assertions both fail. The change from
`helper.contains("finally")` to `contains("catch") && contains("once.run()")` is what makes that so;
the old assertion would have passed the reverted code.

### D8 - `testTheAuditSeesTheReversalRoomRuleTheStagingPlannerDoesNotHave` (apart from C8)

`testHomeStaging.java:163`. The `shortBerth()` fixture is `HS A - HS B - HS C` plus `HS A - HS D`,
every edge measured at 5, with `HS D` a terminus and a 40-unit reversible train homed there.

- `Layout.measuredRoomToReverseInto` (`Layout.java:6201-6221`) sums *every* edge of the path and
  returns null when any is unmeasured - so the fixture's "every edge measured" note is load-bearing
  and correct, and the only route to HS D sums to 5.
- The rule in `isPathClear` (`Layout.java:2362-2421`) is **not** behind `isAutoRunning()`, so the
  control at test line 210 exercises it on a layout at rest. That was worth checking: three of the
  rules around it in that method are fenced, and if this one were, the control would have failed.
- The audit itself: `HomeStaging.auditAgainstRuntime` (`HomeStaging.java:602-692`) compares
  `getPossiblePaths` against `firstClearRoute` per station. With the rule on both sides both answer
  `{HS B, HS C}` and the count is 0; delete the planner's copy (`HomeStaging.java:1039-1044`) and the
  planner adds `HS D`, giving one disagreement.

MUTATION traced (both the javadoc names): take the length off the edge, or the train length off the
locomotive, and `measuredRoomToReverseInto` returns null, the runtime stops refusing, and the audit
falls to zero - which is why the edge-length and terminus preconditions are real checks even though
the two locomotive ones (C8) are not.

The `TS3-B2` shared-state repair is correct and complete: `getTrainLength()`/`isReversible()` are read
before the mutation and restored in a `finally` (lines 175-176, 232-237), and those are the only two
setters the test calls on a locomotive the other 84 tests in the class share. The newer
`testALongerApproachIsStillTried...` does the same at lines 3359-3360 and 3414-3417.

### D9 - `testTwoHomesOnOneSquareDoNotBothSurviveTheLoader`

`testHomeStaging.java:3269`. This is the one I expected to find passing for the wrong reason, because
`Layout` has two rules that could drop the second assignment. Traced
`Layout.rebuildHomeStations:1112-1180`:

1. The assignment loop runs **first** ("Assignments win, and are applied first", `:1101`), so both
   `home` entries are considered before any positional claim.
2. `HS W1` takes `LOC_A`. `HS W2` is not refused by the *locomotive* rule at `:1122-1137` - it names
   `LOC_B`, which has no home yet - so it reaches the new square rule at `:1139-1169` and is dropped
   there.
3. Only then does the fallback `claimHome` loop run, giving `LOC_B` a home at `HS B`.

So the pre-existing rule cannot account for the result. Remove the square rule and `LOC_B` is homed on
`HS W2`, `homedThere` becomes 2, and the first assertion fails - which is the mutation the javadoc
names. The result is also order-independent: if the points map yields `HS W2` first, `LOC_B` wins and
`LOC_A` falls through to `HS A`, and both counts are still 1.

The `isSamePlaceAs` precondition is a real check (shared s88 *and* shared block, both in the fixture),
and the second assertion - that the loser's Point really was cleared rather than merely skipped -
covers the "keeping the loser re-warns on every load" half. Swept for twins: `homeStations` is written
in exactly two places, `Layout.java:1093` (`claimHome`, which has had the square rule since MT-165)
and `:1171` (the assignment loop, which now has it). There is no third door.

### D10 - `testNothingIsLoadedWhenAlreadyHome`, with the sequential flag

`testHomeStaging.java:944`. `Plan.isPossible()` is `outcome == READY` (`HomeStaging.java:314-317`),
so an `ALREADY_HOME` plan is not possible and `loadReturnToHomeTimetable` returns at
`Layout.java:6676`.

MUTATION traced (the one the javadoc names): delete that line and execution falls through to
`setTimetable(staged)` with an empty list - which clears the flag (`Layout.java:6555-6563`) - and then
to the unconditional `this.timetableSequential = true;` at `:6711`. The timetable is still empty, so
the old assertion is unmoved; the new one fails. The javadoc's claim that the empty timetable "does
not catch it" is exactly right.

The positive control lives in the same file rather than the same method - `:915-926` shows an ordinary
timetable false, a staged plan true, and the next ordinary load false again - so the new assertion is
not asserting a value that has never been seen to change.

### D11 - `testTheAffordancesAskTheGuardsOwnQuestion` (apart from the comment, C12)

`testErrorsStopTheSetupRunning.java:198`. The `TS3-B6` correction is right and matters: the previous
version required `canStartAutonomy()` to contain the literal `autonomyErrorCount()`, so after the
guard was widened to `hasErrors()` the test was *enforcing* the divergence it exists to catch.

Verified that the new pair is a real pair rather than two names: `refuseAutonomyStartWhileBroken`
refuses on `getAutonomySession().hasErrors()` (`TrainControlUI.java:5183`), `canStartAutonomy` asks
`!autonomyHasErrors()` (`:20166-20167`), and `autonomyHasErrors()` is
`session != null && session.hasErrors()` (`:20183-20187`). Same question, one delegation apart. The
`withoutComments` on both bodies is necessary here and present - the guard's comments mention
`hasErrors()` three times.

The message edit at `testErrorsStopTheSetupRunning.java:88-92` is now true where the old one was:
`hasErrors()` had no caller in `src/` and now has two.

MUTATION traced: narrow either side back to `autonomyErrorCount()` and one of the two assertions
fails - the guard one if the guard is narrowed, the affordance one if the affordance is.

### D12 - the four smaller repairs

- **`testCopiesOfOneSquareAreTheSamePlaceAndNeighboursAreNot`** (`testAutonomyDiagramSession.java:3809`).
  The move from `pageOnDisk()` to `pageWithATwoEndedStation()` is correct: the middle sensor at (3,1)
  has track on both sides and splits, so the nested loop now evaluates `sameSquare(c1, c2)` on two
  different names rather than `sameSquare(x, x)`, which `AutonomySession.sameSquare:2564` short-circuits
  on `a.equals(b)`. The new `copies.size() >= 2` precondition is a real check and fails loudly if the
  split ever stops happening. The negative half is also sound and I checked the trap it could have
  fallen into: `elsewhere` is the *base* name of (1,1), which would be unknown to the index if that
  square split - but (1,1) is an end sensor with one arrival side, and `baseNames()` maps an unsplit
  Point to itself, so it is a real emitted name and the `assertFalse` is meaningful.
  MUTATION traced: make `sameSquare` compare sensors instead of squares and the negative half fails
  (both stations are on different sensors here, so it would still pass) - the half this repair fixed
  is the positive one, and with one copy it could not fail at all.
- **`testAnUnmarkedLayoutIsUntouched`** (`testAutonomyDiagramReversal.java:357`). The added
  `assertNotEquals(withTheStationMarked, withoutFeature)` is a genuine control, and it can fire: the
  `reduce()` helper opens every route both ways (`:527-534`), so the sensor at (5,2) in `junction()`
  has track at (4,2) and (6,2), two arrival sides, and marking it reversible changes the emitted
  configuration. MUTATION traced: a builder that ignored `withReversibleTiles` entirely fails the new
  assertion and passes the old one exactly as before, which is the vacuity `TCX-B5` named.
- **`badgeAt`** (`testAutonomyDiagramMonitor.java:1207-1240`). `parking` and `shut` were one argument
  and are now two. Checked that the split does not silently change the existing fixtures: the
  three-argument overload passes `parking = false`, and where `shut` is true the colour is still
  `POINT_INACTIVE` because `TileAnnotation.java:1531` reads `isParking() || isImpassable()`. The new
  `assertNotEquals` at `:1062-1071` discriminates because `TileAnnotation.java:1649` draws the cross on
  `isImpassable()` alone. MUTATION traced: `isImpassable() { return parking; }` would have passed every
  assertion in that method before this change and fails the new one.
- **`testFullAutonomyDoesNotDriveThroughAReversingPoint`** (`testLayoutPickPath.java:472`). The control
  now runs before `setReversing(true)` and asks the same tier as the assertion it protects
  (`pickPath` both times). MUTATION traced (the one the javadoc names): `pickPath` returning null
  unconditionally passes the refusal and fails the control. The re-ordering also moved
  `setLocomotive` above the control, which it has to be.

### D13 - the two integration-test repairs, and the fixture re-sync

- **`testAMovedTileCarriesItsSetup`** (`testAMovedTileCarriesItsSetup.java:227-256`). Replacing the
  error *count* with "no error may APPEAR", keyed by `messageKey | subject`, is the right shape: the
  isolated-square half of the test moves a station somewhere that emits no arrival copies, so a
  copy-level finding legitimately disappears and a count assertion written to catch damage fails on an
  improvement. The round-trip assertion still demands the count come back exactly (`:224`), which is
  where a genuinely lost finding shows. One residual risk, not a finding today: the helper's comment
  says the subject "is the station's name, which travels with the tile", and that is true of the
  ERROR-severity findings this fixture produces but not of `AutonomyChecks` in general -
  `AutonomyChecks.java:403-413` builds diagram problems with `String.valueOf(problem.getTile())` as the
  subject. If a future ERROR is keyed by square, this test fails on a move that damaged nothing, which
  is the failure it was just repaired for.
- **`testTrainsComeHomeToTheirPlatforms`**. Three changes, all sound. `ordinaryCopy()` prefers a
  non-terminus destination copy and is now used by both the floor and the placement, which is the
  right answer to "LowerFront emits `(eastbound)` and `(eastbound, reverse)` and the first one wins".
  `arrival()` folds the turning twin onto the plain copy; the suffix it strips is exactly what
  `AutonomyBuilder.nodeName:712` emits (`", reverse)"`), and the two headings it must *not* fold
  (`(northbound)` / `(southbound)`) are untouched. The deleted westbound placement was genuinely
  stale: `PLATFORMS` (`:105-108`) has not contained `BottomMainC` for some time, so the `i == 4` block
  was forcing a westbound copy of `LowerFront` while its comment argued about `BottomMainC`. No stale
  claim about that placement survives in the file - the remaining `BottomMainC` mentions at `:618` and
  `:626` are generic examples of the naming convention.
- **The `test/operator_layout/` changes** in `409d4ce8` and `56c6080e` are a re-sync of Adam's own
  diagram (locomotives moved between platforms, a new portal pair on page 5, `2 - Bottom:8,7` switched
  out of service), not a fixture edited to make a test pass. They match the sweep described in the two
  commit messages.

### D14 - `testThePaletteStillPlacesTiles` as a test (apart from C11)

Checked separately because it is the round's only new class.

- The sandbox is opened before the model *and* before the window (`:54-64`), which is the rule
  `testSwitchingToACentralStationLayout` enforces.
- The `17 -> 18` bump in that enforcing test is correct: `WINDOW_BUILT`
  (`testSwitchingToACentralStationLayout.java:748-749`) is a regex that accepts the fully qualified
  `new org.traincontrol.gui.TrainControlUI(` this class uses, `SANDBOX_OPENED` (`:760-761`) matches
  `LayoutSandbox.open(` regardless of the `support.` prefix, and the sandbox's index precedes the
  window's. One window, one sandbox, so the counting branch is satisfied.
- The two guards that stop it passing by accident are real: the fixture square must carry a component
  (`:123-128`) and the palette tile chosen must be a different type from what is on it (`:130-137`).
- MUTATION traced (the one the javadoc names): remove the `placingFromPalette()` early return at
  `LayoutEditor.java:1131` and the press on the occupied square falls into
  `this.initCopy(label, null, true)` at `:1143`, re-arming the clipboard as a MOVE off that square;
  `endDrag` then sees press and release on one label, calls it a click, clears the clipboard, and
  `receiveClickEvent` has no tool to run - so `diagram.getComponent(4, 2)` is still the STRAIGHT and
  the first failure branch fires. The test drives `beginDrag`/`endDrag`/`receiveClickEvent` rather
  than `executeTool`, which is what makes that reachable at all.

---

## What this pass did not cover

- Nothing was executed, so every "this passes" above is a trace, not a measurement. C5 in particular
  is a claim about which of two enum constants the tiles resolve to, read out of
  `TilePorts.java:248` and `TileGraph.java:1418`; a run would settle it in one line.
- The production changes of 2026-09-02 were read only where a test's claim rested on them. The three
  copy checks, the room rule and the loader's square rule were traced; the affordance sweep in
  `2e83b737` and the eight comment corrections were not reviewed as changes in their own right.
- The message bundles were not checked for the new keys beyond noticing that
  `autolayout.warnHomeSquareAssignedTwice` was added to all of them in `8d1c17ca` with `\uXXXX`
  escapes intact.
