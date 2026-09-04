# The final review before the tag: are the day's fixes what they say they are

**Status:** closed - FR3-B1 fixed and pinned; the C findings are dispositioned below

**Prefix for citing these findings elsewhere:** `FR3`

**Reviewed:** branch `autonomy-diagram-r0`, on 2026-09-04, at `3f4d7396`. The subjects, in order of
weight: the two commits no review has read (`2d7a9d5f`, which closed the second validation's findings,
and `3f4d7396`, DAY-C3/IPR-C5); the five tests the day leaned on hardest, each checked for passing for
the wrong reason - `testALockPhaseFailureLeavesTheTrainWhereItStands` by executing it and reading the
thrown exception's own stack out of the run log, the other four by reading; the areas the three prior
rounds jointly left alone (`MarklinControlStation`, the CS2 file parsing, the route editor, the
timetable, `LocIconCropDialog`), covered by two delegated read-only passes whose promoted claims were
re-verified here at their cited lines; and the release artefacts (`Readme.md`'s 3.0.0 changelog,
`docs/manual-tests/` via `triage.py verify-ledger`). Commit `e2afe88c`'s OB-173 and DAY-C2 halves -
the read ACC's verdict item (5) asked for and no round had done - were read here (FR3-D6).

Tests run through `one.sh`, one JVM each: `core.testAutoLayout` (26), `regression.testEditorSurfaceRules`
(38), `core.testConditionOutline` (21), `core.testAutonomyDiagramSession` (112), `core.testMessageBundles`
(13) - **210 tests, 0 failures, 0 skips**, and the live-layout guard printed nothing. `battery.sh` was
not run (it was run green at `3f4d7396` by Adam's own gate, per the commit message), and no headful
class was run - TrainControl itself may hold the foreground. `cs2_sample_layout/` was read and never
written; the two files modified in the working tree (`configuration-Main.json`, `setup.json`) were
already modified before this pass began - Adam's application writing `loc`/`facing` - and nothing here
touched them. Nothing in the tree was edited except this file.

---

## Verdict

**One thing should get your decision before you tag, and it is not in autonomy.** The three rounds
that closed today were aimed squarely at the release blocker and its neighbours, and on that ground
the tree is sound: ACC-A1's gate is correct and - verified here by **executing** the test and reading
the thrown exception's own stack out of the run log - its third-generation test now genuinely reaches
the lock loop, `handleMisconfiguredPath` and all (FR3-D1). Every commit from `v3_0_0_rc12` to
`3f4d7396` has now been read by at least one review, including the two that no round had seen
(`2d7a9d5f`, `3f4d7396`) and the two halves of `e2afe88c` that ACC's verdict flagged as unread
(FR3-D5). 210 tests re-run here are green with zero skips and the live-layout guard silent. But the
one area the rounds barely touched - the new route editor - carries a silent data corruption
(**FR3-B1**): open a route whose condition begins with a bracketed group, delete an unrelated later
condition, and `tidy()` flattens the group's first row so the outline reads back with its leading
**or turned into and** - `problems()` flags nothing, and Save writes the changed meaning. It needs a
specific outline shape and an edit, so I graded it B on the letter of the could/does rule, but what it
silently alters is when a route fires, in a headline 3.0.0 feature with no test over the path. Whether
that holds the tag is your call to make knowingly rather than by default; the fix is one line. Nothing
else here rises above C: five C findings on the day's own fixes (a precondition that cannot tell the
lock loop from the preview, a message branch and a source rule that shipped untested, a comment naming
a reverted mechanism, and a menu escape that renders bare in the one case it is for), and two delegated
passes over the untouched areas whose only sharp edge outside FR3-B1 is a longstanding route-name bug
identical in 2.8.1. The changelog is fit for a non-technical reader.

---

## Summary

| # | Severity | One line | Where |
|---|---|---|---|
| B1 | B | Deleting an unrelated condition flattens a leading bracketed group, silently turning its `or` into `and`; nothing flags it and Save writes the changed meaning. New in 3.0.0, no test | `RouteEditorFrame.java:3764, 2158`, `ConditionOutline.java:220, 288` |
| C1 | C | The release blocker's re-aimed test asserts only that the stack contains `configureEdge` - true in the preview too, so it cannot detect the exact drift `OV2-B1` caught | `testAutoLayout.java:1791`, `Layout.java:2513, 2991` |
| C2 | C | DAY-C3's new hand-dispatch message branch shipped with no test of either arm, the morning the pattern was named | `Layout.java:5158`, `test/` |
| C3 | C | The new `saveQuietly` source rule passes on any `!` in the statement and scans only four named files, so a discarding call with a nearby `!=`, or one in a fifth file, is invisible | `testEditorSurfaceRules.java:2978-3017` |
| C4 | C | The gutter test's javadoc describes the `paintChildren` mechanism that `15a2878d` reverted, not the `paint()` one it pins | `testTheDiagramPrintsItsCoordinates.java:264`, `LayoutGrid.java:743` |
| C5 | C | OV2-C1's lift is real, but with both destination lists empty - the case ACC-C10 is about - the "..." renders bare, no separator, no locomotive name | `LayoutRightclickAutonomyMenu.java:366, 445, 488` |
| D1 | D | The release blocker's gate and third test, verified by executing the test and reading the exception's stack: it reaches the lock loop, `handleMisconfiguredPath` runs | `Layout.java:5100-5248`, run log |
| D2 | D | The other four named tests are honest, each with the control that stops a degenerate pass; two carry stale comments (C3, C4) | below |
| D3 | D | The rest of `2d7a9d5f` (OV2-C2 through C5), fix by fix, matches its dispositions | `TrainControlUI.java:2293, 5586, 22583` |
| D4 | D | `3f4d7396`: DAY-C3's capture-before-stop shape and IPR-C5's comment are both correct; the message branch is untested (C2) | `Layout.java:5143`, `LocIconCropDialog.java:953` |
| D5 | D | `e2afe88c`'s OB-173 (editor `toFront`) and DAY-C2 (delete door's two repaints) halves, unread until now, are sound; all 13 commits since rc12 are now reviewed | `LayoutEditor.java:5402`, `TrainControlUI.java:18066` |
| D6 | D | The 3.0.0 changelog is fit for a non-technical reader; `triage.py verify-ledger` reports `clean: true`; MT-250/269/270 exist and carry the right questions | `Readme.md:362-445`, `docs/manual-tests/` |
| D7 | D | `MarklinControlStation`/CS2/CS3 delegated pass: three ACC-D14 claims re-verified, two C's folded (simulate IP, unguarded `view`); one latent route-name bug identical in `master`, not a 3.0.0 matter | below |
| D8 | D | Route editor/timetable/`LocIconCropDialog` delegated pass: round-trips and crop math sound; MT-005 three-way half is Adam's recorded decision; six C's folded | below |
| D9 | D | What this review did NOT cover - read before trusting its breadth | below |

---

## B findings

### FR3-B1 - Deleting one condition silently turns a route's leading "or" into "and", and Save writes it

| | |
|---|---|
| **Severity** | B - and the one finding here I would put to Adam before the tag; the A argument is in the last paragraph |
| **Disposition** | fixed 2026-09-04 - `tidy()`'s floor for row 0 is its own depth rather than zero, which is the one-line fix this finding proposes; row 0 has no row above it to be measured against.  The condition table gained the three test hooks the command table has had since it was written, and `testDeletingAConditionDoesNotChangeTheGroupAboveIt` pins it: the outline reads `And(Group(Or(x,x)),x)` before the delete and keeps its `Or` after.  MUTATION: restoring `at == 0 ? 0` fails it, with the message naming the corruption |
| **Confidence** | The whole chain was traced by hand and each link confirmed against the code: `write` emission, `removeAt`, `tidy`, `toExpression`, `problems`, and the save gate. Not executed - this pass edits nothing, and there is no unit fixture to run (`grep` over `test/` for `ConditionTable`/`removeAt`/`tidy` finds none touching this path). Found by the delegated route-editor pass; re-derived independently here from the source before filing. |

A condition that begins with a bracketed group is corrupted by deleting an **unrelated** later
condition. Every step is in code I read:

**The shape loads with row 0 one level in.** `(A or B) and C` is
`NodeAnd(NodeOr(A,B), C)`, and `ConditionOutline.write`/`writeChild` emit it as
`[A(1), or(1), B(1), and(0), C(0)]` - `writeChild` bumps the cross-operator left child a level
(`ConditionOutline.java:466`), so the group's rows are written at depth 1 before the outer `and(0)`.
`toExpression`'s own comment says this in as many words (`:208-220`): such a condition *"opens as an
outline whose first row is one level in."*

**Deleting `C` leaves the group, flattened wrong.** `ConditionTable.removeAt`
(`RouteEditorFrame.java:3654`) removes `C(0)` and its preceding `and(0)` -> `[A(1), or(1), B(1)]`,
then calls `tidy()`. `tidy` forces row 0 to depth 0 unconditionally
(`RouteEditorFrame.java:3764`, `int most = at == 0 ? 0 : ...`) -> `[A(0), or(1), B(1)]`. That single
line is the defect: the group `A or B`, correctly read at its own outermost depth of 1, is now split
across depth 0 and depth 1.

**It reads back as AND, and nothing objects.** `toExpression` computes `outermost = 0`
(`:220-222`) and `read(depth 0)` takes `A` as a depth-0 item, then consumes `or(1), B(1)` as a
deeper sub-run that returns just `B` - the `or` has nothing to join inside a one-item run - so the
level-0 join word defaults to AND (`:288`): **`A and (B)`**. `problems()` flags nothing (the only
joiner, `or(1)`, is alone at its depth - `:184-187`), so no row goes red, and `everythingWrong`'s
only condition-shape gate is `conditions.hasProblems()` (`RouteEditorFrame.java:2158`). Save
proceeds and writes `A and B` where the operator wrote `A or B`. The reading under the table shows
the new sentence; the code's own comment two lines up (`:2156-2157`) names exactly this hazard -
*"the route would then fire at times nobody asked for."* `indent()` reaches the same `tidy()`
(`:3746`), so an indent gesture anywhere in such an outline corrupts it too.

This is new-in-3.0.0 code - the indented-outline route editor is a headline feature
(`Readme.md:388`) - and it is the class the review scope calls grade A: *a condition outline that
goes wrong silently.* I file it **B** on the letter of the could/does rule - it needs a group-first
condition **and** an edit - but the mitigation is thin: the edit that triggers it (deleting `C`) is
unrelated to the group it corrupts, the change is invisible without re-reading the outline, and what
is silently altered is authored data that decides when a route fires. **Whether that blocks the tag
is Adam's to weigh** - it is the one thing in this review that could, and it wants a decision rather
than a default. The fix is one line (`at == 0 ? rows.get(0).getDepth() : ...`, i.e. never raise
row 0's floor above its own depth) plus the fixture that pins it.

---

## C findings

### FR3-C1 - The release blocker's precondition cannot tell the lock loop from the preview

| | |
|---|---|
| **Severity** | C |
| **Disposition** | fixed - the precondition now also asserts `isPathClear` is NOT in the stack, which is what separates the lock loop from the preview; `configureEdge` alone is true of both, and that is the distinction the second fixture got wrong |
| **Confidence** | The strong-window claim is **executed, not argued**: `core.testAutoLayout` ran 26/0/0 here and the caught exception's stack was read out of the run log - `configureEdge(Layout.java:2635)` <- `configureAndLockPath(:2991)` <- `executePathInternal(:5408)` <- `executePath(:5069)`, with `handleMisconfiguredPath` genuinely running (its `errorPathMisconfigured` line and its guarded name-collection's own logged secondary NPE both appear in the log). The gap below is derived by reading `isPathClear` and `configureEdge`; no mutation was executed - this pass edits nothing but this file. |

The re-aimed `testALockPhaseFailureLeavesTheTrainWhereItStands` (`2d7a9d5f`,
`testAutoLayout.java:1699-1817`) does reach the lock loop today - verified by execution, above. That
is the third version of this test, and this time it is honest. What is weaker than its own account is
the precondition:

```java
assertTrue(trace.toString().contains("configureEdge"),
    "the failure did not come out of the lock loop, so this is a weaker window ...");
```

`configureEdge` is called from **two** places: the lock loop (`Layout.java:2991`) and `isPathClear`'s
preview (`:2513`), and the preview runs first, outside the try, before anything is locked. A throw
inside `configureEdge` during the preview - for instance, the `preConfigure` conflict arm calling
`.equals` on a stored null state (`:2620`), which two commands naming one accessory would reach -
produces a stack that also contains `configureEdge`, so the assert passes while the window has
silently become the weak one again: nothing locked, `handleMisconfiguredPath` never run, the start
reservation the fixture's own placement, not the recovery's. That is precisely the drift `OV2-B1`
caught the previous fixture in, and the javadoc's claim - *"a stack that never enters `configureEdge`
is not this failure"* - is true but one-directional: a stack that does enter it is not necessarily
this failure either.

Two one-line discriminators are already in hand, either sufficient: assert the stack does **not**
contain `isPathClear` (the preview's frame, present in the weak window and absent in the strong one -
confirmed against both this run's trace and the one `OV2-B1` quoted), or assert
`getPathValidationFailureCount()` went up - it is incremented by `handleMisconfiguredPath`
(`Layout.java:3247`) and exposed for tests, and `OV2-B1` itself named it as *"the precondition that
would have caught this."* The fix chose the stack string and took the weaker of the two. Graded C
rather than B because the test executes the right code **today** and still pins the gate against
deletion in either window; the finding is that the assertion built to detect fixture drift cannot
detect the nearest drift.

### FR3-C2 - DAY-C3's new message branch shipped untested, the same morning the pattern was named

| | |
|---|---|
| **Severity** | C |
| **Disposition** | **accepted, not fixed** - the branch chooses a message key by `wasRunning`, and reaching it needs a RuntimeException out of a real dispatch with autonomy stopped.  Named in MT-271 rather than pretended into a unit test |
| **Confidence** | `grep -rn errorHandDispatchFailed test/` returns nothing; the branch was read, not executed. The key exists in all eight bundles (measured: one hit per file) and `testMessageBundles` ran 13/0/0 here, so the format side is pinned; only the branch choice is not. |

`3f4d7396` added `wasRunning` and the two-way message in `executePath`'s failure handler
(`Layout.java:5143, 5158-5160`):

```java
this.control.logf(wasRunning
    ? "autolayout.errorRunStoppedByFailure"
    : "autolayout.errorHandDispatchFailed", loc.getName());
```

The capture-before-the-recovery shape is right, and says so with the correct analogy to `hadItsPath`.
But no test drives `executePath` to failure with `running == false` and asserts the hand-dispatch
sentence - `errorHandDispatchFailed` appears nowhere in `test/`, and the existing lock-phase test
calls `runLocomotives()` first, so it exercises only the `true` arm (this run's log shows the
autonomy sentence). The cheap test exists: the new lock-phase fixture minus its `runLocomotives()`
line, asserting the other key's text in the log. Filed at C - it is a message, not a movement - but
it is the fourth fix today to ship without a test of its own branch, on the day `OPV-B1` made that
the headline, and the branch's whole point is which sentence an operator is sent to act on.

### FR3-C3 - The new saveQuietly source rule is satisfied by any `!` in the statement, and only in four files

| | |
|---|---|
| **Severity** | C |
| **Disposition** | fixed, after two wrong attempts of its own - the rule scans every .java under src/ and asks whether the answer GOES ANYWHERE (`saveQuietly();` and nothing else) rather than whether a `!` is nearby.  Asking that a `!` apply to the call was worse: it flagged `saveQuietly() != true`, which consults the answer.  Both directions checked by mutation - a discard is caught, a non-`!` use is accepted |
| **Confidence** | The rule was read and its acceptance derived; it ran green here (`testEditorSurfaceRules` 38/0/0), and all three current call sites were re-grepped - every one consults the answer, and all live inside the four scanned files, so the rule holds the tree it was written for. Not executed against a counter-example - that would mean editing source. |

`testEverySaveOfTheSetupReadsWhetherItWorked` (`testEditorSurfaceRules.java:2978-3017`, from
`2d7a9d5f`) takes the statement fragment from the previous `;` up to each `saveQuietly()` call and
requires it to contain `"!"`. Two holes, both in the direction of silence:

- **Any `!` satisfies it.** `if (this.model != null) session.saveQuietly();` discards the answer and
  passes the rule - the `!=` supplies the `!`. So does any discarding call whose statement region
  happens to hold a negation, e.g. a call placed first inside a brace after a guard clause. The
  stated mutation ("dropping the `!` from any call site fails this") is true; the untested direction
  is a **new** discarding call that arrives with a `!` nearby, which is how real call sites look.
- **Four files are named; the rule's name says "every".** All three current callers outside
  `AutonomySession` are inside them (`LayoutEditor.java:433, 605`, `TrainControlUI.java:22583` - all
  consulting), but a fourth door added in, say, `LayoutRightclickAutonomyMenu` or
  `MarklinControlStation` is outside the sweep entirely, silently.

The honest fix is small: scan every `.java` under `src/` (the loop already tolerates a missing file),
and tighten the acceptance to `!` immediately preceding the call or an `if (!...saveQuietly())`
shape. As it stands the rule catches the two historical shapes exactly and little else - protection
that reads wider than it is, which is this folder's stated reason such things get filed.

### FR3-C4 - The gutter test's javadoc describes the fix that was reverted, not the one it pins

| | |
|---|---|
| **Severity** | C |
| **Disposition** | fixed - the javadoc names `paint()`, and records that `paintChildren` was the first attempt and why it failed |
| **Confidence** | Both sites read at HEAD. The test itself is honest: `container.paint(g)` exercises the real `paint()` override, its first assertion stops the pass-by-painting-nothing, and the with/without-intruder comparison pins the redraw. Not run headfully here. |

`testAChildInTheGutterDoesNotRubOutTheNumbers` (`testTheDiagramPrintsItsCoordinates.java:264-265`)
says: *"`LayoutGrid.newDiagramContainer` now paints the ruler again at the end of its `paintChildren`
override - the same hook that already draws trains over captions."* That was `20acddde`, the first
attempt. `15a2878d` moved the redraw to `paint()` - and `LayoutGrid.java:743` records why in as many
words: *"In `paint` rather than at the end of `paintChildren`, which was the first attempt"*. The
test still exercises the right code (it calls `paint`), so this is `ACC-C1`/`OPV-C3`'s class - a
comment describing a superseded mechanism, in the one file a reader will open to understand OB-172 -
not a wrong test. One sentence.

### FR3-C5 - In the case ACC-C10 is about, the escape now appears - bare

| | |
|---|---|
| **Severity** | C |
| **Disposition** | fixed - when both lists are empty the escape adds its own separator and locomotive name, the same two lines `VD11-C3` uses, so it is never bare |
| **Confidence** | Derived from the `add`/`addSeparator` sequence at `LayoutRightclickAutonomyMenu.java:366-374, 445-505`, not observed on screen; there is still no test at the menu-assembly layer (ACC-D8, OV2-D10 both say so). |

`OV2-C1`'s fix (`2d7a9d5f`) lifted the escape out of the `otherPaths` branch, so with **both** lists
empty it finally renders - the functional half is real and this closes the reachability question that
went through three placements. But both heading gates are skipped in exactly that case: the top-level
separator-and-name block requires `!paths.isEmpty()` (`:366`), and the submenu's requires
`!otherPaths.isEmpty()` (`:445, 453`) - so the "..." is added directly under the menu's action items
with no separator and no locomotive name, which is the very presentation `OPV-C4` was filed about.
The comment at `:485-487` says the escape *"still lands under this train's own name when there is
one"* - an honest half-sentence that stops short of saying what renders when there is not. `OV2-C1`'s
proposed shape (the same two-line heading gate `VD11-C3` used) was not taken, and the `OV2-C1`
disposition claims only the lift, so nothing is overstated - this is the residue, named so the fourth
placement is also the last.

---

## D findings - checked and found sound, decisions noted, and coverage edges

### FR3-D1 - The release blocker's gate and its third test, verified by execution

The one question the day turned on. `core.testAutoLayout` ran 26/0/0 here and the exception
`testALockPhaseFailureLeavesTheTrainWhereItStands` catches was read out of the run log:
`configureEdge(Layout.java:2635)` <- `configureAndLockPath(:2991)` <- `executePathInternal(:5408)` <-
`executePath(:5069)` - the lock loop, inside the try, after `edgesLocked++`/`setOccupied()`/
`reserve()`. `handleMisconfiguredPath` genuinely ran: its `errorPathMisconfigured` line is in the log
(the very line whose absence convicted the previous fixture in `OV2-B1`), and so is the secondary NPE
its guarded name-collection caught at `:3201` - the guard behaving exactly as its comment at
`:3181-3186` says, at the price it names (the operator message listed no accessory). The handler at
HEAD was re-read whole (`Layout.java:5100-5248`): `hadItsPath` is captured inside the
`activeLocomotives` monitor on the line above the removal, the gate at `:5235` releases only a path
the locomotive actually got, and `3f4d7396`'s DAY-C3 insertion sits between the stop and the release
without touching either. The mutation ("removing the gate fails the last assertion") was re-derived,
not executed: with the gate gone, `unlockPath`'s `i == 0` clause clears the start the recovery had
just re-reserved, in the atomic and non-atomic branches both. Residue: FR3-C1.

### FR3-D2 - The other named tests are honest

- **`testAModernExportIsSpottedByEachOfItsKeys`** (`testAutonomyDiagramSession.java:5811-5836`): loops
  the three keys, one one-point file each, one report line asserted per key, then the control - a
  plain `{'name':'A','s88':1}` file must report nothing, which is what stops the three assertions
  being satisfied by a detector that fires on everything. The key is `block`, singular, matching
  `Point.toJSON`. Ran inside 112/0/0 here.
- **`testTwoGroupsAtOneIndentAreNotADisagreement`** (`testConditionOutline.java:702-735`): the
  accepting half is paired with a control that a genuine same-run disagreement is still flagged at
  its exact row index - so a degenerate `problems()` that flags nothing cannot pass. Ran inside
  21/0/0 here.
- **`testEverySaveOfTheSetupReadsWhetherItWorked`**: consulted all three real call sites (re-grepped:
  `LayoutEditor.java:433, 605`, `TrainControlUI.java:22583`, all inside the scanned files, all
  reading the answer). Its acceptance is looser than its name - FR3-C3.
- **`testAChildInTheGutterDoesNotRubOutTheNumbers`**: the fixture is sound - opaque white intruder
  across the whole gutter, ink counted against a no-intruder yardstick, and the first assertion is
  what stops a ruler that paints nothing from passing. Its javadoc names the wrong hook - FR3-C4.
  Not run here (headful; TrainControl itself may hold the foreground).

### FR3-D3 - The rest of `2d7a9d5f`, fix by fix, against its claims

- **OV2-C5**: one `wouldCapture` predicate (`TrainControlUI.java:2293-2297`), carrying all four of
  the capture's terms **and** the `getAutonomySession() != null` fifth the copy had dropped; the
  message branch reads `wouldCapture && setupEditDeclinedDuringRun`, the capture branch reads
  `wouldCapture`. The inner `session != null` re-check is kept with a written reason (the getter is
  not guaranteed to answer the same way twice). As dispositioned.
- **OV2-C2**: documented rather than changed, and the documentation matches the code - the excluded
  door really is the track diagram editor's close (all `autonomyEditorClosed()` callers are in
  `LayoutEditor.java`; the no-arg `rebuildRunningLayoutFromSetup()` at `:5529-5531` passes `false`),
  `refuseWhileEditorOpen()` exists and guards ten-plus doors, and the comment at `:5586-5596` says
  in as many words that relaxing OB-047 means giving the flag its own parameter. The unreachability
  itself rests on OV2's enumeration, not re-proved here.
- **OV2-C3**: the Combine Pages door now reads `saveQuietly()`'s answer and logs
  (`TrainControlUI.java:22583-22588`), with the cost written at the site; plus the source rule
  (FR3-C3 for its limits).
- **OV2-C4/OPV-C6 family**: the drifted citation now names `autonomyEditorClosed()` by symbol
  (`AutonomyEditorPanel.java:6458`).
- **OV2-C1**: the lift is real; the bare-rendering residue is FR3-C5.

### FR3-D4 - `3f4d7396`, the last commit before the tag

- **DAY-C3**: `wasRunning` captured before `stopLocomotives()` clears it - the right shape, with the
  right analogy written at it - and the new `errorHandDispatchFailed` key is present in all eight
  bundles (measured: one hit per file; `testMessageBundles` 13/0/0 here). The branch is untested -
  FR3-C2. The other half (a failed hand dispatch still stops the whole run) is Adam's 2026-09-03
  ruling applied as written, and the commit says so.
- **IPR-C5**: a comment-only change to `LocIconCropDialog.java:953-966`; the corrected sentence
  matches the call graph (see FR3-D8 - the delegated pass traced `startAtCover` -> `clampCenter` ->
  `getScale` independently and confirmed both the recursion and the guard that terminates it).

### FR3-D5 - Commit `e2afe88c`'s unreviewed halves, now read

ACC's verdict item (5) asked for a read of this commit before the tag; OPV/OV2 covered its OB-172
`LayoutGrid` half (twice superseded), and the other two halves had been read by nobody:

- **OB-173** (`LayoutEditor.java:5402-5421`): `toFront()` + `requestFocus()` after `setVisible(true)`
  in the editor's open path - the same two calls `showOpenEditor()` already makes for the same
  window, so the two doors now agree. The comment correctly distinguishes this from
  `comeToTheForeground`'s always-on-top start-up case (the OB-170 one-window rule is about start-up
  foreground, not a click on a window that has it). Sound.
- **DAY-C2** (`TrainControlUI.java:18066-18083`): the delete door gains `updateVisiblePoints()` and
  `repaintTimetable()`, the two calls both rename doors carry, with the OB-081 reasoning written at
  the site. Sound.

With these, **every commit from `v3_0_0_rc12` to `3f4d7396` (13) has now been read by at least one
review** - `489439fa` by ACC/OPV, `e2afe88c` across OPV/OV2/here, the eleven since by the four
documents of 2026-09-04.

### FR3-D6 - The release artefacts

- **`Readme.md`'s 3.0.0 section** (`:362-445`) was read end to end. It is written for the
  non-technical reader throughout: features described by what a user sees, bug fixes only for
  defects a user of 2.8.1 could actually have hit, no finding IDs, no internals beyond the one-line
  "Code" entry (library versions - the same shape prior versions use). Spot-checked against code:
  the destination-menu sentence is the corrected ACC-C3 wording (`:380`); the bold downgrade warning
  (`:381`) matches ACC-C8's verified mechanism; the routes-defer-to-autonomy paragraph (`:386`)
  matches the doctrine ACC-D14 traced. Fit to ship.
- **`docs/manual-tests/`**: `python docs/manual-tests/triage.py verify-ledger` reports **`"clean":
  true`** (25 ledger rows, zero drift in every category). MT-250, MT-269 and MT-270 all exist with
  disposition `needs test` and carry exactly the questions the reviews route to them.
- The 2026-09-03 parity report still has no status line and is still a generated report in this
  folder (`OV2-D8`'s two unclaimed halves) - unchanged, known, not a tag matter.

### FR3-D7 - `MarklinControlStation` and CS2/CS3 parsing: delegated pass, findings folded

The three areas the prior rounds barely touched here, read end to end by a delegated read-only pass;
its three prior-review claims and its promoted findings were re-verified here at their cited lines.

**Confirmed sound** (the pass's checks, its evidence read here for the three that ACC-D14 leaned on):

- **CS2Message short-frame guard**: `getSubCommand()` returns `-1` when the declared payload length
  `< 5`, and `-1` matches neither GO (1) nor STOP (0) - a truncated frame is no longer read as an
  emergency stop. As ACC-D14 claimed.
- **MFX UID-as-address**: `CS2File.java:1786-1789` subtracts `MFX_BASE` when `address >
  MFX_MAX_ADDR (0x3FFF)`, the exact mirror of the DCC branch.
- **Per-page fault-tolerant layout load**: `parseLayout` catches per page and `syncLayouts` throws
  only when `parsed.isEmpty() && couldNotBeRead > 0`, **before** `clearLayouts()` - the on-screen
  diagram survives a partial read.

**Two narrow items, both graded C, folded here rather than given their own sections because neither
is a HEAD regression:**

- A **simulate-mode launch removes the saved IP preference** (`MarklinControlStation.java:4049-4052`):
  the `!getNetworkCommState()` removal of `IP_PREF` fires in simulate mode too, because `on` never
  went true against loopback and `model.simulation` is set nine lines later, at `:4061`. Verified the
  positions by reading. Effect: the next real launch re-prompts for the IP. Guardable with the local
  `simulate` parameter already in scope.
- **`exportLocsToCSV` dereferences `view` unguarded** (`:3548`, `this.view.getAllLocButtonMappings`)
  in a class ~30 of whose sites guard `view != null` and whose `init(..., showUI=false, ...)` path
  leaves `view` null. Confirmed the null-view construction path; no in-tree headless caller was found,
  so the defect is the unguarded public-API override, not a live crash.

There is a real **B-shaped latent defect that is not a 3.0.0 matter**: route import trims only the map
KEY, not the `Route` object's name (`MarklinControlStation.java:1882-1883` vs `Route.java:34`), so a
Central Station route whose name carries surrounding whitespace stops syncing and cannot be fired or
deleted by name. I confirmed the asymmetry - and confirmed it is **byte-identical in `master`
(`:1561-1563`)**, so it is longstanding, not introduced here, and its trigger (a CS emitting a
whitespace-padded route name) is unproven against Adam's own data, which carries no routes file. Not a
tag matter; recorded so it is not lost. The pass read every scope commit since 2026-08-22 and judged
each to do what its message claims.

### FR3-D8 - The route editor, timetable, and `LocIconCropDialog`: delegated pass, findings folded

Read end to end by a second delegated pass. Its strongest finding is promoted to FR3-B1 above and was
re-derived independently here. The rest:

**Confirmed sound**: the command round-trip both ways (every `CommandRow.of`/`toCommand` kind,
protocol carriage, delay-as-absent-key symmetry, signal/switch vocabulary); the three-way
round-trip; unknown/kept commands preserved in order; grouped conditions preserved in meaning
(single-element `NodeGroup` wrappers are transparent); `everythingWrong` pre-validating every row so
`onSave` cannot throw post-dialog; locked CS routes inert; the timetable execution guards
(capture-during-execution, per-entry JSON tolerance, stuck-entry bounds, completion `isCurrentLayout`
fence); and the crop math (`clampCenter` inequalities, `contentOf` single-resample). **IPR-C5's
recursion claim it verified independently** - `startAtCover` -> `clampCenter` -> `getScale` does
recur, and `startAtCover`'s `viewStarted` guard is what terminates it, exactly as the new comment
says and the old one denied.

**C-grade items folded (delegated evidence, structurally spot-checked here, not each re-executed):**

- **MT-005's three-way half is unreachable as written** (`RouteEditorFrame.java:900`, `asShown`
  extended to THREE_WAY): no call site hands `asShown` a THREE_WAY row, and the live edit path
  (`setValueAt:2950`) re-infers only for ACCESSORY/SIGNAL - so typing a signal's address into a
  three-way row still leaves it a three-way pointing at a signal. **Adam ruled "current behavior
  accepted" on 2026-08-23** (MT-005), so this is a recorded decision, not an open defect; noted
  because the fix reads as live and is not.
- The timetable **delete confirmation shows the 0-based row index** ("entry #0" for the first row;
  `TrainControlUI.java:23548/23555` vs the 1-based table at `:25083`); the delete itself hits the
  right entry.
- The **first timetable entry's delay is shown but never honoured** (`Layout.java:4724` skips the
  gate for the first dispatched entry, while the UI lets one be set and renders it).
- **Hardcoded English** in the otherwise-I18n timetable table (`TrainControlUI.java:25085`,
  `"Pending Start +Xs"`).
- Several **stale javadocs / an unterminated javadoc block** in `RouteEditorFrame.java` (153-167,
  1323-1325) and `LocIconCropDialog.java` (`sourceRect` 1559, `getCroppedImage` 1487) - remnants of
  removed panels and superseded clamping; comment-only.
- **`viewIsUsable` does not bound `frameSize`** (`LocIconCropDialog.java:866-878`): a corrupt sidecar
  with `frameSize` far above its 0.05-1.0 range yields an off-screen crop window recoverable only by
  Reset. Narrow.

None of these is above C, and none touches the railway or saved autonomy data.

### FR3-D9 - What this review did NOT cover

- **No mutation was executed and no source was edited** - every "removing X fails Y" claim here is
  re-derived by reading, as in the three prior rounds.
- **No headful class was run** (`ui.testTheDiagramPrintsItsCoordinates` and siblings): TrainControl
  itself may hold the foreground, and a focus failure would be unattributable. The gutter test was
  audited by reading only.
- **`battery.sh` was not re-run.** The 148-classes-green result is `3f4d7396`'s own, accepted as
  Adam's gate; the five classes re-run here (210 tests) are the ones today's commits touch.
- **The delegated passes' clean lists** (FR3-D7, FR3-D8) are reported with their evidence, not
  re-derived line by line; everything either pass promoted to a finding was re-read here at the
  cited lines before filing (FR3-B1's four consequence paths, the `master` comparison, FR3-C6's
  unguarded block and the `simulate` flag's position, FR3-C7's unguarded `view`).
- **FR3-B1's premise** - that a Central Station will actually hand over a route name with
  surrounding whitespace - was not established; Adam's `cs2_sample_layout` carries no routes file to
  check against, and that is exactly why the finding is graded on the could/does line rather than
  higher.
- **MT-250's departure half and MT-269's three questions** remain what ACC left them: recorded,
  priced, and needing Adam's hands on the railway, not code.


