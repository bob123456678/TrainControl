# Direction, reversals and the doors that disagree: a review of 2026-09-06

**Status:** open

**Prefix for citing these findings elsewhere:** `DIR` (confirmed unused - `grep -rl "DIR-" docs/`
returns nothing).

**Reviewed:** branch `autonomy-diagram-r0`, working tree at `46a9823c`, on 2026-09-06. In scope:
`46a9823c` (MT-247), `6bd95b22`, `8fca0215` (the reversal policy and `flipFacing`), `a0512166`
(MT-270), `d4a3d498` (OB-177 and the V31-C3 correction), `2790d8bb` (OB-174/175/176/178), and the
four audit commits `fc836441`, `6fc243f9`, `1babe733`, `eb5c73a8`. `cs2_sample_layout/` was read and
never written; the two files under it that `git status` shows as modified were last written at
00:10 today, four hours before this pass began, and `one.sh`'s fingerprint guard was silent on all
six runs.

**Method.** Five temporary probe classes were written, run through `one.sh`, and deleted; then seven
shipped classes were run to confirm the tree came back clean. Two of the probes drove real trains
through `Layout.executePath` in simulate mode with a watchdog timeout, one drove `MarklinRoute.
execRoute` through a reflectively installed `View` stub, one exercised `GraphReducer` directly, and
one exercised `AutonomySession.flipFacing` against a temp-directory session. Every claim below says
whether it was established by **execution** or by **reading**. Nothing is left on disk.

**Test runs (all `Failures: 0, Skips: 0`).** After the probes were deleted:
`core.testNonReversibleTrains` 9, `core.testAccessory` 14, `regression.testEditorSurfaceRules` 39,
`core.testAutonomyDiagramReducer` 26, `core.testAutonomyDiagramSession` 116,
`regression.testTheCheckerAgreesWithTheBuild` 7,
`regression.testARouteDoesNotThrowSwitchesUnderATrain` 13. **Nothing in today's work is failing.**
Everything below is a gap the suite does not see.

---

## Verdict

**Both of today's rulings are implemented at one door and missing at its sibling, and in each case
the sibling is the one the ruling was actually about.**

The reversal policy is asked only at INTERMEDIATE reversing points. A hand-driven send whose
DESTINATION is a may-reverse point turns the train round without asking anybody - which is, word for
word, the case Adam described: *"if the user decides to send a train to a 'may reverse' point,
explicitly ask the user if the train should change direction."* That is `DIR-A2`, and it was measured
by driving a train: the policy was consulted zero times and the locomotive came back facing the other
way. The control in the shipped test, `testAManualSendDoesNotTurnATrainUnasked`, asks
`shouldReverseAt(ordinary, plain, ...)` - a journey ENDING somewhere else - so it agrees with the
code about the case the code handles and never asks about the one it does not.

And where the question IS asked, it is asked of an operator watching a train that is still moving.
`ManualReversalPrompt`'s javadoc says *"the train is standing still at the point while this is
asked."* Measured: at the instant the policy is consulted the locomotive's speed is 30, and five
seconds later it is still 30. The `setSpeed(0)` is INSIDE the `if` the answer decides, so the train
runs past the reversing point for as long as the dialog stands unanswered - which, on a headshunt, is
until it runs out of track. That is `DIR-A1` and it is the most serious thing here.

MT-247 has the same shape. The emergency-stop carve-out was removed from the MIDWAY question and left
in place at the PRE-ROUTE one, so Adam's ruling of 2026-09-01 - *"emergency stop should never conflict
or prompt"* - now holds at one door of the same manual gesture and not the other. Measured through a
stubbed window: a route carrying a stop is waved past `conflictingAccessoryAndReason` (which still
carves it out), reaches the loop, is asked about at the accessory, and a Cancel returns out of
`execRoute` with the power still on. `testARouteThatCutsThePowerIsNeverHeldUpByTheQuestion` still
passes, because it only tests the door that kept its carve-out; its own javadoc - *"So the question is
not asked about such a route"* - became false in the same commit. That is `DIR-A3`.

The closed-square correction is one call site short in the same way. `reachableTiles` was taught about
closures and `findPath` was not, so the editor's path test now draws a route straight through a square
the findings panel simultaneously reports as cutting off everything beyond it (`DIR-B1`). Two comments
in the tree assert this cannot happen - `AutonomyEditorPanel:5901` (*"the path test and the findings
panel cannot disagree"*) and `AutonomyChecks:1233` (*"the same one Layout.bfs and the editor's path
test ask"*) - and both are now false. Measured on a three-sensor run: A reaches B and not C, while
`findPath(A, C)` returns a route.

`flipFacing` is sound in its ordinary case and blind in exactly the two states the same day's other
work exists to surface: a locomotive recorded on two squares gets no follow at all, and a recorded
facing the square cannot hold - the OB-177 state the facing menu was just taught to display - is the
one case it declines (`DIR-C3`, `DIR-C4`). It also writes only the setup: nothing moves the
locomotive between the split copies of the running Layout, so after a direction command the arrow on
the diagram and the destinations the railway will offer disagree until the next build (`DIR-B3`). And
it does all of that - a station-index derive, a full findings recompute and a grid rebuild - on the
Central Station's locomotive-message thread, inside `synchronized repaintLoc` (`DIR-B4`).

`MT-270`, `OB-174` through `OB-178` and the audit commits are sound as far as this pass reached;
`enteredS88` is a proper single reader and its test discriminates. Details in `DIR-D1`.

---

## Summary

| # | Severity | One line | Where |
|---|---|---|---|
| A1 | A | The reversal question is put to the operator while the train is still running at line speed, and it keeps running for as long as the dialog stands - the `setSpeed(0)` is inside the `if` the answer decides | `Layout.java:5670-5686`, `ManualReversalPrompt.java:38-45` |
| A2 | A | A hand-driven send whose DESTINATION is a may-reverse point turns the train round without asking - the exact case Adam described. The policy is consulted only at intermediate points; the arrival flip is unconditional | `Layout.java:5963-5971` |
| A3 | A | The emergency-stop carve-out was removed from the midway question and left at the pre-route one, so a manual Cancel discards a power cut. Adam's 2026-09-01 ruling now holds at one door of two | `MarklinRoute.java:371`, `:706`, `:773-789` |
| B1 | B | `findPath` never got the `closed` set `reachableTiles` gained, so the editor's path test routes through a closed square while the findings call the station beyond unreachable - and two comments say that cannot happen | `GraphReducer.java:475`, `AutonomyEditorPanel.java:5694,5913-5917` |
| B2 | B | `route.cancelledByOperator` says "nothing in the route ran" in all eight languages; the log prints it two lines under "power turned off due to condition" | `messages*.properties`, `MarklinRoute.java:786` |
| B3 | B | `flipFacing` writes the setup's facing and nothing moves the locomotive between the running Layout's split copies, so the diagram arrow and the destinations the railway offers disagree until the next build | `AutonomySession.java:1416-1444` |
| B4 | B | `followDirectionChanges` runs a station-index derive, the whole findings recompute and a grid rebuild on the CS locomotive-message thread, inside `synchronized repaintLoc`; every other caller of `autonomySetupChanged()` is on the event thread | `TrainControlUI.java:9812-9860` |
| C1 | C | Three javadocs became false in the commits that made them false: `View.confirmRouteConflictMidway` twice, and the test whose subject is the carve-out that was removed | `View.java:113-126`, `testARouteDoesNotThrowSwitchesUnderATrain.java:995-1010` |
| C2 | C | The pre-route refusal logs nothing while the midway cancel logs two lines - the same operator gesture, two doors, one record | `TrainControlUI.java:17040-17046`, `LayoutLabel.java:622-626` |
| C3 | C | `flipFacing` returns at the FIRST placement it finds, so a locomotive recorded on two squares gets no follow at all - not even on the square that could take one | `AutonomySession.java:1423-1440` |
| C4 | C | The one state `flipFacing` declines is the one OB-177 shipped the same day to make visible: a recorded facing the square cannot hold | `AutonomySession.java:1434` |
| C5 | C | `lastSeenDirection` is keyed by name and is never repaired on rename nor evicted on delete, so the first direction change after a rename is silently swallowed | `TrainControlUI.java:9802`, `:9835` |
| C6 | C | `placedLocomotives()` is a fifth walk of the same map WK3-C3 consolidated four of - and it already carries the qualification that helper's javadoc names as the danger, and reads the name untrimmed where `getLocomotiveNameAt` trims | `AutonomySession.java:1463-1501`, `:4357-4388` |
| C7 | C | The name-check census is per FILE, so `TrainControlUI`'s second door cannot fail it; and the CS2/CS3 sync creates locomotives from Central Station names without asking at all | `testEditorSurfaceRules.java:88-101`, `MarklinControlStation.java:1428` |
| C8 | C | The reversal dialog is parented on a `JPopupMenu` that was dismissed when the train was dispatched, so it is owned by no window by the time it appears | `LayoutRightclickAutonomyMenu.java:1018` |
| C9 | C | The timetable and Return Home get `ALWAYS_REVERSE` by falling through the 4-argument overload, and nothing says that was decided; `HomeStaging`'s "in one statement" comment is stale | `Layout.java:4785`, `HomeStaging.java:965` |
| C10 | C | `reachableTiles`'s new closed semantics are the MANUAL tier's, and both callers ask the autonomy question, where `isPathClear` refuses a closed square at either end too | `GraphReducer.java:749-760`, `Layout.java:2243,2320` |
| D1 | D | What was checked and found sound, and what this pass did not reach | - |

---

## DIR-A1 - The operator is asked about a train that is still moving, and it keeps moving until they answer

| | |
|---|---|
| **Severity** | A - a reversing point is a headshunt or a stub. The dialog is modal and unbounded, so the train runs past it for as long as it takes somebody to notice the window, and nothing stops it in the meantime. The javadoc asserts the opposite, so this was reasoned rather than measured |
| **Disposition** | fixed |
| **Confidence** | **Measured by execution.** A probe drove a train A -> M -> B in simulate mode with M a reversing point and a policy that recorded `train.getSpeed()` when consulted, slept five seconds, and recorded it again: `speedAtTheMomentTheOperatorIsAsked=30 speedFiveSecondsLater=30`. What I did NOT reach: whether a real dialog on a real railway is answered quickly enough to matter in practice, and what the train physically does past the point - that is Adam's layout, not something a probe can say |

`executePathInternal` reads:

```java
if (isCurrentLayout() && shouldReverseAt(current,
    path.get(path.size() - 1).getEnd(), loc, reversals))
{
        this.control.logf("autolayout.infoIntermediateReversingForLocomotive", loc.getName());
        loc.setSpeed(0)
        .switchDirection()
        ...
}
```

`shouldReverseAt` calls `reversals.shouldReverse(loc, current)`, which is `ManualReversalPrompt.ask`,
which is `SwingUtilities.invokeAndWait` on a modal `showOptionDialog`. Everything that stops the
train is inside the branch the answer decides.

Nothing before it stops the train either. An intermediate point's handling is a speed multiplier and
`loc.waitForOccupiedFeedback(current.getS88(), ...)` - arriving at the sensor, not stopping at it. The
`setSpeed(0)` that a destination gets is in the `else` arm, for the last point only.

So the sequence at a may-reverse point on a hand-driven send is: the train reaches the sensor at line
speed, a modal dialog appears, and the train carries on. `ManualReversalPrompt`'s own javadoc says
*"`invokeAndWait` is what makes the answer available to the caller that needs it, and the train is
standing still at the point while this is asked."* The second half is false.

This is the same hazard `LayoutLabel` was rewritten for, and it says so in as many words: *"Asking the
question here would hold it for as long as the dialog stands unanswered, and no tile anywhere would
respond - the same freeze the power-state wait was given a deadline to avoid, forty lines above,
differing only in that a person ends this one."* There the thing being held was a switching thread.
Here it is the thread driving a train, and the train is not held with it.

The remedy has the shape the rest of this file already uses: stop first, ask second, and set the speed
back up whichever way the answer goes - which is what the block does anyway on a yes, and is a no-op
the train can sit through on a no. `shouldReverseAt` cannot do that itself (it is pure, and that is
worth keeping), so the stop belongs at the call site, above the `if`, guarded by `current.isReversing()`
so it costs nothing anywhere else.

---

## DIR-A2 - A journey that ENDS at a may-reverse point is never asked about, which is the case that was asked for

| | |
|---|---|
| **Severity** | A - it is the literal sentence the feature was built from, and a reversing station is manually selectable by design: the locomotive tooltip says so (*"- marks a station autonomy will never choose ... or because it is a reversing station"*), and `isAutoDestination` is asked only by `pickPath`. So the commonest way to reach a may-reverse point by hand is the one way that does not ask |
| **Disposition** | fixed |
| **Confidence** | **Measured by execution.** Three probes drove trains through `executePath` in simulate mode with a recording policy that answered no. Destination IS the may-reverse point: `policyAsked=0 turned=true`. Control, may-reverse point in the MIDDLE and a plain destination: `policyAsked=1 turned=false`. Control, terminus destination through a may-reverse point: `policyAsked=0`, net direction unchanged (turned at the middle and again on arrival). So the mechanism works exactly where it is wired and is not wired to the case in question. What I did NOT reach: whether Adam would rather the destination case asked, or turned silently as it does today - the ruling is his |

The policy is consulted from one place, and that place is inside `if (i != path.size() - 1)`. The last
point is turned by a different statement, forty lines below the loop, which no policy reaches:

```java
// Reverse at terminus station
if (path.get(path.size() - 1).getEnd().isTerminus() || path.get(path.size() - 1).getEnd().isReversing())
{
    this.control.logf("autolayout.infoLocomotiveReachedTerminusOrFinalReversingStation", loc.getName());
    loc.delay(...).switchDirection().delay(1000);
}
```

The message key names the case out loud - `...TerminusOrFinalReversingStation` - so the code knows the
difference between the two and turns both regardless.

Adam's ruling has two halves and the second one is what this misses: *"no unprompted reversals in
manual mode unless going to a true terminus (must reverse)."* A final reversing station is not a true
terminus; the two flags are mutually exclusive, which the shipped test's own comment establishes
(*"Terminus stations cannot be set as reversing"*). So a hand-driven send to a may-reverse point is
precisely an unprompted reversal in manual mode to somewhere that is not a terminus.

**Why the shipped test does not see it.** `testAManualSendDoesNotTurnATrainUnasked` asks
`Layout.shouldReverseAt(ordinary, plain, loc, ...)` - the reversing point in the middle, the
destination elsewhere - four times, and `(plain, terminus, ...)` once. `shouldReverseAt` is never asked
about a journey whose destination IS the reversing point, because the code path that handles that case
does not go through `shouldReverseAt` at all. The rule is pinned; the case is not in the rule.

This is the shape the same commit's own javadoc warns about - *"naming a rule moves the defect to the
call"* - arriving one call site further out than the pin that was written for it. The companion test
`testTheRunAsksThatRule` checks the source contains the one call that exists; a second such check for
the arrival statement would have had nothing to match.

---

## DIR-A3 - The emergency-stop carve-out now holds at one door of the manual gesture and not the other, and Cancel discards the stop

| | |
|---|---|
| **Severity** | A - the operator is asked about a turnout and, by answering that question, silently declines a power cut they were never told about. Adam's ruling of 2026-09-01 is unambiguous (*"emergency stop should never conflict or prompt"*), and this project has already filed and fixed the identical defect once: *"a validation pass proved what that costs ... The emergency stop did not run. Measured, not reasoned: `getPowerState()` was still true afterwards"* |
| **Disposition** | fixed |
| **Confidence** | **Measured by execution.** A probe installed a `View` stub by reflection and ran four cases through a real conflicting route while a path was locked. `manual/cancel/with-stop`: `preRouteConflict=null(nothing to confirm) midwayAsked=2 powerStillOn=true` - the pre-route door was disarmed by the carve-out, the midway door asked anyway, and the stop was discarded. `auto/with-stop`: `midwayAsked=0 powerStillOn=false` - the s88 door is correct and unchanged. `manual/ok/with-stop`: `powerStillOn=false turnoutThrown=true` - OK still runs everything. `manual/cancel/no-stop` is the control: `preRouteConflict=Switch 84/...`, so the pre-route door does see the conflict when there is no stop. What I did NOT reach: whether Adam prefers this to the alternative - his MT-247 ruling and his 2026-09-01 ruling genuinely conflict, and only he can say which wins |

There are two places that decide whether a conflict is a question, and only one of them was changed:

- `conflictingAccessoryAndReason()` (`:355-372`) opens with `if (this.hasEmergencyStop()) return null;`
  and is what `askAboutRouteConflict` consults **before** the route starts, at both human doors.
- `heldReason(rc)` (`:435`) has no such clause and is what the loop consults **at each accessory**.
  MT-247 removed `!this.hasEmergencyStop()` from the condition that guarded it.

The consequence is not confined to a conflict that appears part way through. Because the pre-route
door answers "nothing to confirm" for any route carrying a stop, such a route is never screened at all
before it starts - so a conflict that was already there when the operator pressed the button is met for
the first time at the midway question. That is exactly what the probe reproduced.

The comment written for the change argues the cost away: *"A person who cancels a route that would have
cut the power has the Stop button in front of them."* That is true of somebody who knows what they
cancelled. The dialog names one accessory and asks whether to set it; nothing in it mentions the power.
`conflictingAccessoryAndReason`'s own javadoc makes the same argument in the other direction and is
still in the file: *"Whichever way the operator answered, they were not answering about the power."*

Whatever the ruling turns out to be, the two doors have to give the same answer. Either the carve-out
goes from both (and `conflictingAccessoryAndReason` starts screening stop-carrying routes again, so at
least the question is asked before the route runs rather than after its first commands), or it stays at
both and `heldReason`'s caller re-acquires it. What cannot stand is one of each, which is what shipped.

---

## DIR-B1 - `findPath` never got the `closed` set, so the editor's path test and the findings panel now disagree

| | |
|---|---|
| **Severity** | B - the editor's "test a path" tool exists so a route can be read off the track rather than out of a sentence, and it now draws routes the runtime refuses. Not A: it misleads rather than moves anything, and `isPathClear` still refuses the path when a train is actually sent |
| **Disposition** | fixed |
| **Confidence** | **Measured by execution.** A probe built a three-sensor run A-B-C, closed B, and asked both walks: `reachableWithClosed=[A, B]`, `containsC=false`, and `findPathAtoC=FOUND (through the closed square)`. The closed square is still a valid destination in both (`closedDestinationReachable=true`), which is the half the correction got right. What I did NOT reach: how many squares on Adam's own railway are closed, so how visible this is in practice |

`GraphReducer.reachableTiles` gained a fifth argument and a `continue` that stops the walk entering a
closed square. `GraphReducer.findPath` has four overloads and none of them takes one. Both are called
from the editor, three lines apart in the same file, on the same `mayTurn`/`mustTurn`/`barred` sets.

Two comments in the tree state the invariant this breaks:

- `AutonomyEditorPanel:5901` - *"The same turn sets the reachability check uses, from the one place
  that computes them, so the path test and the findings panel cannot disagree about which way a train
  may go."*
- `AutonomyChecks:1233` - *"`reducer.reachableTiles` answers the split-aware question instead, which is
  the same one `Layout.bfs` and the editor's path test ask."*

Both were true when written and neither is now. This is the same checker-versus-build disagreement
`testTheCheckerAgreesWithTheBuild` exists to catch, in the one direction that test does not look: it
compares the checker against the BUILD, and the editor's path test is a third walk that nothing
compares against either.

The remedy is a fifth argument on `findPath` and `session.shutTiles()` at all three editor call sites -
the same edit that was made to `AutonomyChecks`, applied to the sibling that was in the same file.

---

## DIR-B2 - The cancellation message says nothing ran, printed under the line saying what ran

| | |
|---|---|
| **Severity** | B - a false claim in the log about what a route did to the railway, in all eight languages, in the one record the operator has of a route they cancelled. Not A: nothing acts on it |
| **Disposition** | fixed |
| **Confidence** | **Measured by execution.** A probe built a route whose commands are `[stop, accessory]` with the accessory on a locked path and the operator answering no. The log came back with the two lines two hundred milliseconds apart: `Route PC route power turned off due to condition` then `Route PC route was cancelled: you declined its conflicting accessory command, so nothing in the route ran.` The probe's own oracle agreed: `powerStillOn=false` |

`execRoute` walks `this.route` in order and the cancel is a `return` from inside the loop. Everything
before the conflicting accessory has already gone out: the stop, the speeds, the functions, any earlier
accessory, and any chained route. The code knows this - forty lines above, `execRoute` says *"A
conflict appearing PART WAY through still leaves the earlier ones set, at every door. That is
unavoidable"* - and the message written in the same commit says otherwise.

The English is `route.cancelledByOperator=Route {0} was cancelled: you declined its conflicting
accessory command, so nothing in the route ran.` All seven translations carry the same clause. The
honest sentence is about what will NOT happen from here - "nothing further in the route was run" - which
is what the two neighbouring refusal messages were corrected to say for the same reason.

---

## DIR-B3 - The facing follows the railway; the placement does not, so the diagram and the destinations disagree

| | |
|---|---|
| **Severity** | B - the arrow the operator reads and the list of destinations the operator is offered are now two answers to one question, and they can differ for as long as it takes to open an editor. Before the change both were stale together, which is wrong but consistent |
| **Disposition** | open - needs Adam.  `flipFacing` writes the setup and nothing rebuilds the running layout, so the diagram's arrow and the destination list answer for different facings until the next build - and `captureFromLayout` will then overwrite the setup from the running copy, undoing it.  Which of the two wins is a decision about the railway, and guessing at it is what produced `DIR-A2` and the reverted reversal rule earlier today. |
| **Confidence** | **By reading**, with the mechanism confirmed by execution in the flipFacing probe (`setFacing` writes the setup and `getFacing` reads it back; the running Layout is never touched). `flipFacing` calls only `placedLocomotives`, `getFacing`, `facingChoices` and `setFacing`, and `setPointProperty` derives the station index without rebuilding. What I did NOT reach: running the whole window with a real session and a real layout to see the two side by side - that needs the application, not a probe |

A square is several Points, one per side a train can arrive by, and which copy a locomotive stands on
is what decides where it can go next. `AutonomyBuilder.placementCopy` reads the setup's `facing` to
choose that copy - but only at BUILD time. `flipFacing` writes the property and nothing rebuilds the
running layout, so:

- the diagram's caption arrow flips at once (`facingArrow` reads the setup, and says so: *"Read from
  the setup rather than worked out from the running graph"*);
- the running `Layout` still has the locomotive on the copy it was placed on, so `getPossiblePaths`,
  the right-click destination list and `explainDestinations` all still answer for the OLD facing.

`captureFromLayout` then makes it worse in the other direction: it derives the facing from whichever
split copy the locomotive is actually on and writes that back over the setup, unconditionally
(*"Only ever written here, never cleared"*). So the next capture - opening the editor is enough - will
silently undo `flipFacing`'s write, because the running layout never learned about it.

Which of the two should win is a design question and Adam's to answer. What is not in doubt is that
they are two records of one fact with no arbiter, which is the state the same file's javadoc calls out
about facing in general: *"Following the echo as well would have two writers for one fact, and the
loser would be whichever finished second."* That sentence was written about autonomy runs and applies
unchanged to this.

---

## DIR-B4 - A direction echo now runs the whole autonomy refresh on the Central Station's message thread

| | |
|---|---|
| **Severity** | B - `autonomySetupChanged()` is the heaviest refresh in the window (findings recompute, locomotive panels, grid rebuild) and every other caller reaches it from the event thread; one of them wraps it in `invokeLater` explicitly. This one reaches it from `locMessageProcessor`, holding the `TrainControlUI` monitor |
| **Disposition** | fixed |
| **Confidence** | **By reading.** The call chain is `MarklinControlStation:2429` (inside `this.locMessageProcessor.submit`) -> `repaintLoc(false, locList)` (declared `synchronized`) -> `followDirectionChanges` -> `flipFacing` -> `setPointProperty` -> `deriveStationIndex()` -> back in the UI, `autonomySetupChanged()` -> `refreshAutonomyTabState` + `refreshStaticAutonomyLayer` (which runs `refreshAutonomyFindings`) + `repaintAutoLocList` + `repaintLayout`. What I did NOT reach: whether this actually deadlocks or throws in practice - reproducing it needs a window and a Central Station, and this pass ran headless |

Three separate things follow from the placement:

- **Thread.** `AutonomySession` has had exactly two writers of `setFacing` until today, both on the
  event thread (`AutonomyEditorPanel:2850` and `LayoutRightclickAutonomyMenu:980`). `flipFacing` is the
  first off-EDT writer of the store, and the store is a `JSONObject` tree the event thread reads and
  writes elsewhere. `deriveStationIndex()` is a full builder construction, which `AutonomySession`'s own
  comments describe as expensive enough to have been optimised out of two bulk gestures.
- **Swing.** `refreshAutonomyTabState` calls `isEnabledAt` / tab-state methods, `repaintAutoLocList` and
  `repaintLayout` are both `synchronized` methods that touch components. `TrainControlUI:3756` reaches
  the same method as `SwingUtilities.invokeLater(() -> autonomySetupChanged())`, which is the shape this
  one should have.
- **Cost.** `refreshAutonomyFindings` runs `session.check()`, which walks `reachableTiles` once per
  station over the whole railway. That is now on the path of every locomotive direction change.

The comment placing the call above the concurrency guard is right about what it is for - *"the graph has
to follow every direction change, not only the ones that arrive when the renderer happens to be idle"* -
and the fix for that is to keep the CHEAP half (the `lastSeenDirection` bookkeeping and the decision)
where it is, and marshal the expensive half onto the event thread.

---

## DIR-C1 - Three javadocs became false in the commits that made them false

| | |
|---|---|
| **Severity** | C - documentation, but the kind a reader checks against. `View.java`'s is the contract every implementer of the interface reads |
| **Disposition** | fixed |
| **Confidence** | **By reading**, with both underlying behaviours measured (see `DIR-A3`) |

`View.confirmRouteConflictMidway` (`:113-126`) now says two things that are not true:

- *"**A route carrying an emergency stop never gets here.** `MarklinRoute` tests `hasEmergencyStop()`
  before asking"* - it does get here; the probe measured `midwayAsked=2` on a stop-carrying route.
- *"@return true to set it anyway and finish the route, false to skip this accessory and every later
  one; **in both cases the rest of the route runs once this returns**"* - false now cancels the route.

And `testARouteThatCutsThePowerIsNeverHeldUpByTheQuestion`'s javadoc says *"So the question is not
asked about such a route"*, which was the point of the test and is now true only of the door it
happens to exercise.

---

## DIR-C2 - The same refusal is a two-line log record at one door and silence at the other

| | |
|---|---|
| **Severity** | C - the operator declines a route at the pre-route dialog and nothing anywhere says the route did not run; they decline the same route at the midway dialog and get two lines |
| **Disposition** | fixed |
| **Confidence** | **By reading.** `askAboutRouteConflict` returning `REFUSED` is handled at `TrainControlUI:17044` (`refreshRouteList(); return;`) and `LayoutLabel:624` (`return;`); neither logs. The midway branch logs `now[1]` and then `route.cancelledByOperator` |

MT-247's ruling - *"cancel should cancel everything"* - was already the behaviour at the pre-route door,
which returns without running anything. So today's change made the two doors agree about the ACT and
left them disagreeing about the RECORD. `executeRoute`'s own comment states the principle it needs:
*"Logged rather than silent, because unlike the greyed button these two doors say nothing on their
own - the operator confirmed a dialog and is owed a reason why nothing happened."*

---

## DIR-C3 - A locomotive recorded on two squares gets no facing follow at all

| | |
|---|---|
| **Severity** | C - the state is real (the checks report it, and `captureFromLayout` and a hand-edited file can both produce it), and the failure is silent: `flipFacing` returns null, so `followDirectionChanges` `continue`s and not even the log line is written |
| **Disposition** | fixed |
| **Confidence** | **Measured by execution.** A probe wrote the same locomotive onto two squares, gave a facing to the second only, and called `flipFacing`: `moved=null facingAafter=null facingBafter=E`. Neither square moved, including the one that could have |

The loop is:

```java
for (Map.Entry<TileKey, String> placed : placedLocomotives().entrySet())
{
    if (!locomotive.equals(placed.getValue())) continue;
    ...
    if (recorded == null || choices.size() != 2 || !choices.contains(recorded)) return null;
    ...
}
```

The `return null` is inside the loop, so the FIRST placement that cannot be decided abandons the whole
locomotive. Which placement that is depends on `JSONObject.keySet()` iteration order. A `continue` in
place of the `return` would try the other square; whether it should flip both, or refuse a locomotive
in two places on purpose, is a decision - but it should be one, and it should be the same one whatever
order the file happens to be in.

---

## DIR-C4 - The one state `flipFacing` declines is the one shipped the same day to make it visible

| | |
|---|---|
| **Severity** | C - two features went in on 2026-09-06 about the same state and only one of them knows it exists |
| **Disposition** | fixed |
| **Confidence** | **Measured by execution.** A probe recorded facing `N` on a square whose choices are `[W, E]` - the `facingsThatCannotBeHeld` state - and called `flipFacing`: `moved=null after=N` |

OB-177 taught the facing menu to show a recorded facing the square cannot hold, because *"That state
is real and already has a name: `facingsThatCannotBeHeld` reports it, so the setup knew and the menu
was the one place that did not say."* `flipFacing`'s guard is `!choices.contains(recorded)`, so that
same state is exactly where direction-following gives up - silently, with no log line, for as long as
the mismatch stands. A square in that state is one somebody needs to correct, and the correction it
would most naturally get - turning the train on the track - is the one that does nothing.

A dead end is a separate and correct case: a probe on the end square of a run measured
`choices=[W]`, size 1, so `flipFacing` returns null there. That is right - there is only one legal
facing - and no finding.

---

## DIR-C5 - `lastSeenDirection` is not repaired on rename and not evicted on delete

| | |
|---|---|
| **Severity** | C - one direction change is silently swallowed after every rename, and a name that comes back on a different locomotive inherits the old one's remembered direction |
| **Disposition** | fixed |
| **Confidence** | **By reading.** `grep -rn "lastSeenDirection"` returns exactly two lines, both inside `followDirectionChanges`. `autonomyLocomotiveRenamed` calls only `repairAutonomyLocomotive`, and `autonomyLocomotiveDeleted` does not touch the map |

This is the by-name state pattern the repository has already paid for: a rename door and a delete door
both exist on `View` for precisely this, and were added because by-name state needs repairing at both.

The rename cost is small and real: after `A -> B`, the map still holds `A` and has no `B`, so the first
direction command for `B` finds `was == null`, teaches, and does not act. The comment justifying that
branch - *"The first sighting of a locomotive teaches rather than acts. Otherwise every start-up would
flip half the railway"* - is about start-up, where there is genuinely nothing to compare against. After
a rename there is; it is filed under the wrong key.

The delete case is the one worth guarding: delete `A`, create a new `A` at a different address, and the
stale entry makes the new locomotive's first direction message a CHANGE rather than a first sighting -
which flips a facing nobody asked to flip.

---

## DIR-C6 - The fifth walk of the points map was left outside the helper written to stop there being five

| | |
|---|---|
| **Severity** | C - `tilesWhere`'s javadoc names the exact danger and `placedLocomotives()` already embodies it |
| **Disposition** | fixed |
| **Confidence** | **By reading.** `placedLocomotives()` (`:1463`) walks `configuration.getJSONObject("points")` with its own copy of the six lines `tilesWhere` was extracted from, plus `if (store.getExcludedPages().contains(tile.getPage())) continue;` |

WK3-C3 folded four walks into `tilesWhere` and says why: *"If one gains a qualification - homes on
excluded pages, squares the diagram no longer draws - the others will not have it, and the button will
act on a set the findings never mentioned."* `placedLocomotives()` is a fifth walk of the same map that
already has that qualification, and it was not folded in. The new `shutTiles()`, which went through the
helper, therefore does NOT exclude excluded pages while `placedLocomotives()` does. Harmless today - an
excluded page contributes no reduced Points, so a closed square on one cannot match - and exactly the
asymmetry the helper exists to prevent.

Separately, the two readers of the same `loc.name` field disagree about whitespace.
`getLocomotiveNameAt` trims (*"One place that knows the shape, because there were three and they did not
agree"*); `placedLocomotives` trims only for the emptiness test and returns the raw string. A probe
confirmed the consequence: a placement written as `" Loc A"` is not matched by `flipFacing("Loc A")` -
`moved=null` - while the caption for the same square would read `Loc A`.

---

## DIR-C7 - The name-check census cannot see a second door in the same file, and the sync door is not in it

| | |
|---|---|
| **Severity** | C - a guard that reports clean about cases it never heard of, and the guard's own MUTATION note claims otherwise |
| **Disposition** | fixed |
| **Confidence** | **By reading.** `testEveryDoorThatNamesALocomotiveChecksTheName` lists three FILES and asserts `source.contains("isNameUsable(")` for each. `TrainControlUI.java` holds two independent doors (`:17478` the rename dialog, `:23507` the Central Station name), so removing the call from either leaves the other's occurrence satisfying the test |

The javadoc says *"MUTATION: remove the call from any listed door and this fails, naming it."* For the
two doors that share a file, it does not. The census would have to look for the call near each door, or
list call sites rather than files.

And there is a door it does not list. `MarklinControlStation:1428` creates a locomotive from a name the
Central Station supplied, during a sync:

```java
newLocomotive(l.getName(), l.getAddress(), l.getDecoderType(), ...);
```

No `isNameUsable`. The commit's own reasoning applies unchanged - *"What it costs is not this
locomotive: it is every route that later mentions it"* - and a CS2 file can carry any name its owner
typed into the Central Station. It is a genuinely different door (there is no dialog to refuse at, so
the answer is probably a log line and a skip, or the same treatment `loc.importSkippedDuplicateName`
already gets two lines above) but it is a door, and it is the one the operator cannot see.

---

## DIR-C8 - The reversal dialog is parented on a popup menu that closed when the train was dispatched

| | |
|---|---|
| **Severity** | C - a modal question that stops a journey, owned by no window. Not B: `LayoutRightclickAutonomyMenu` already parents its two existing dialogs the same way, so this is inherited rather than introduced - but those fire within a second of the click, and this one can fire minutes later |
| **Disposition** | fixed |
| **Confidence** | **By reading.** `askAboutReversing` passes `this`, and `this` is the `JPopupMenu` - the class is `final class LayoutRightclickAutonomyMenu extends JPopupMenu`. The item's listener starts a thread and the popup is dismissed by the click; `executePath` then blocks until the train reaches the reversing point. What I did NOT reach: showing that `JOptionPane` actually falls back to the shared root frame for a dismissed popup - that needs a display, and this pass ran headless |

The sibling door is better behaved by accident: `AutoLocomotiveStatus` passes `this`, a panel that is
still in the window when the question arrives. `ManualReversalPrompt` was written as one class for both
doors precisely so *"two spellings of one question"* could not drift - and the two callers hand it
different kinds of parent. `ui` (the `TrainControlUI` frame) is available in `destinationItem` and is
what the question should be centred on.

---

## DIR-C9 - Two callers of `executePath` get `ALWAYS_REVERSE` by falling through, and nothing says that was decided

| | |
|---|---|
| **Severity** | C - the answer is probably right for both; what is missing is the sentence saying so, which is what the next person to add a caller will look for |
| **Disposition** | fixed |
| **Confidence** | **By reading.** There are four call sites. `Layout:3686` (autonomy's own loop) and `Layout:4785` (the timetable, which Return Home loads) take the 4-argument overload and so get `ALWAYS_REVERSE`; the two hand-driven doors pass a prompt |

The timetable case is defensible and worth writing down: `executeTimetableInternal` sets `running`, so
a Return Home run IS an autonomous run by every other test in the file, and `HomeStaging` plans the
reversals it needs - `connected` reasons about them explicitly. Asking the operator about a turn the
planner chose would be asking about a decision they already approved by pressing the button.

But that argument lives nowhere. `ReversalPolicy`'s javadoc says *"Autonomy answers yes without asking
anybody - it chose the path, and `pickPath` already refuses any path that reverses along the way"* -
which is an argument about `pickPath`, and `HomeStaging` does not use `pickPath`. The staging planner is
the caller whose reversals are real and deliberate, and it is not mentioned.

Related and stale: `HomeStaging:965` says *"`executePath` flips direction on arrival at a terminus and at
a reversing point alike, in one statement - which is true"*. It is now two statements, only one of which
takes a policy, and the distinction is exactly what `DIR-A2` is about.

---

## DIR-C10 - The closed-square walk models the manual tier, and both its callers ask the autonomy question

| | |
|---|---|
| **Severity** | C - an overstatement in the safe direction (the findings will say a closed station is reachable when autonomy will never route there), so nothing is hidden that was not hidden before. Worth stating because the correction was made ON the strength of the manual rule and both readers are autonomy checks |
| **Disposition** | fixed |
| **Confidence** | **By reading**, with the walk's behaviour measured (`closedDestinationReachable=true`). `isPathClear` refuses an intermediate closed square unconditionally (`:2299`) and refuses a closed final point only `if (this.isAutoRunning())` (`:2320`); `:2243` additionally refuses any edge with a closed endpoint while auto is running |

`reachableTiles`'s new comment quotes `isPathClear` correctly: a closed square may start a manual route
and may finish one. But `checkReversingGoesSomewhere` asks "can autonomy get a train from this reversing
point to a station" and `checkStations` asks "can these stations reach each other", and under
`isAutoRunning()` a closed square is neither a legal end nor a legal start. So a closed STATION still
counts toward "this station reaches somewhere", which for the autonomy question it does not.

The correction as shipped is the right one for `testTheCheckerAgreesWithTheBuild` and for the manual
tier, and the residue is small. It is worth a sentence in the javadoc saying which tier the walk models,
because the next reader will find `isPathClear` asking two different questions and no note about which
one was copied.

---

## DIR-D1 - What was checked and found sound, and what this pass did not reach

**Found sound, by execution:**

- `Layout.shouldReverseAt` itself. All five branches behave as documented and the shipped test's
  assertions all discriminate: a non-reversing point is never turned at, a null policy means always, a
  terminus destination overrides a no, and yes and no differ. The defect is where it is asked, not what
  it answers.
- The reversal policy at INTERMEDIATE points. A probe drove a train through a may-reverse point on the
  way to a plain destination and measured `policyAsked=1 turned=false` with a no, which is precisely
  Adam's ruling working.
- Backing into a terminus. A probe drove a train through a may-reverse point to a terminus with a
  policy answering no: `policyAsked=0`, turned at the middle, turned again on arrival, net direction
  unchanged. MT-245 still holds.
- `MarklinRoute.respondToConflict`. All four inputs answer correctly, and the probe confirmed each
  answer end to end through a real route: yes runs everything (`powerStillOn=false turnoutThrown=true`),
  the auto door skips only the accessories (`midwayAsked=0 powerStillOn=false`), and a stray yes at the
  unattended door changes nothing.
- The auto (s88) door of MT-247 is unchanged and correct, which was the load-bearing half.
- `flipFacing`'s ordinary case. A probe measured `E -> W -> E` over two calls on a square with two
  choices, returning the tile both times. The geometry is right: it picks the other of `facingChoices`,
  never a compass opposite.
- `GraphReducer.reachableTiles` with the closed set does what its comment says: the closed square is
  reached and not walked through, and it is still offered as a destination.
- The whole tree is green. Seven shipped classes, 224 tests, `Failures: 0, Skips: 0`, and the
  live-layout fingerprint guard silent on all six runs.

**Found sound, by reading:**

- The message bundles. Both new keys plus `route.cancelledByOperator` and
  `loc.ui.errorLocomotiveNameUnusable` are present in all eight languages, and every new line is pure
  ASCII with the non-ASCII characters properly `\uXXXX`-escaped. `loc.ui.errorLocomotiveNameUnusable`
  is reused from the rename doors rather than duplicated, which is the right call.
- `RouteEditorFrame.enteredS88` (OB-178). One reader for the validation and the save, so the two cannot
  disagree about a blank field; `-1` for a negative or malformed value rather than `abs()`, which keeps
  the old comment's point; auto-fire with no sensor still refused. `testAManualRouteNeedsNoSensor`
  states honestly what it does not assert and why, which is the right way to handle an unreachable
  branch.
- `Layout.releasesBeforeThrows` (TCX-B4) is a faithful extraction of the inline comparator, its test
  covers all four combinations plus reflexivity, and the call site is pinned separately.
- `AddLocomotive`'s new check (MT-270) sits after the empty, length and duplicate checks and before
  anything is created, so a refused name creates nothing. The four existing doors all still ask.
- `LayoutEditor.showTextLabels` is idempotent and one-way, and does not touch the flag when nothing
  needs changing - which is what makes OB-174's auto-tick safe to apply to both caption switches.
- `AutonomySession.tilesWhere` (WK3-C3) is a faithful consolidation of the four walks it replaced, and
  `testTheHomeWalksAgree` compares the two that must agree rather than restating either.
- `AutonomyEditorPanel`'s OB-177 change copies `facingChoices` before adding to it, so the session's
  list is not mutated, and adds the recorded facing rather than correcting it - which is the right
  call, as the javadoc argues.

**Not reached:**

- `TileAnnotation.offsetCollidesWithTrack` (OB-175). The geometry is plausible and the "both sides used
  is not a reason to move" rule is the right conservative default, but I did not render a curve and look
  at where the arrow lands. It needs eyes on a diagram.
- `AutonomyEditorPanel`'s `AncestorListener` focus grab (OB-176). Needs a display.
- The four audit commits (`fc836441`, `6fc243f9`, `1babe733`, `eb5c73a8`) - `TCX-B3/B7/B12`, `TS3-B7`,
  `WK3-C2`, and the 49 disposition changes - were not audited. `V31-C3` and `WK3-C3` were, because
  today's later commits touched the same code.
- `test/core/testHomeStaging.java`'s 85 new lines and the remaining tests in
  `test/ui/testRouteEditorValidation.java` and `regression/testTheGoldenLayoutHoldsTogether.java` were
  read only far enough to establish that they do not cover the gaps above.
- Whether `DIR-A1` and `DIR-A3` are reachable often enough on Adam's own railway to matter - both were
  reproduced on synthetic fixtures, and how frequently a manual send crosses a reversing point, or a
  stop-carrying route meets a locked accessory, is a fact about his layout.
- The threading in `DIR-B4` was not driven to a deadlock or a corrupted store; it is named as a hazard
  from the call chain, not as a reproduced failure.
