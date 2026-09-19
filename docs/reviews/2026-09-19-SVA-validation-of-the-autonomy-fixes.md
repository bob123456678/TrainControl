# Validation of the autonomy fixes from the 2026-09-19 round

**Status:** open
**Prefix:** SVA

**Covers:** the autonomy half of the 2026-09-19 fix round on branch `autonomy-diagram-r0`, at the commits below and
nothing after them. `0c3911d8` (SET-B1, SET-B3), `6d4aefee` (SET-B2), `0a12d732` (RTX-B1), `58f94664` (the low
findings - only SET-C2, SET-C3, RTX-C3 and RTX-C5 are in this half; CS3-C1/C2/C3 are not looked at here) and
`3a457b4c` (`behaviour.md`). Read-only: no file but this one was written, no test was run, nothing was compiled and
the application was not started. Every claim below about what a test proves is from reading the test against the code
it drives; every claim about a mutation is a prediction, not a measurement. Where a claim rests on the reducer's legs
over Adam's pages, the pages were read (`test/layouts/live-snapshot/config/gleisbilder/*.cs2`) but the reduction was
not run, and the entry says so.

The question asked of each fix was the SOP's: does it do what it says on every path, did its siblings get it, does the
test prove the claim or can it pass for another reason, and does it break a rule in `behaviour.md` or one pinned by a
neighbouring test. Nothing rose to A or B. The C findings are two gaps in the crossing rule's own definition, the
siblings SET-B2 and RTX-C3 left behind, and two bookkeeping errors in what the fixes say about themselves. D is the
larger part of this document on purpose: it is the record of what was actually checked.

---

## A - wrong behaviour on the layout, or data silently lost

| id | status | where |
|---|---|---|
| - | - | none found |

---

## B - incorrect results, or a refusal, in specific configurations

| id | status | where |
|---|---|---|
| - | - | none found; SVA-C2 becomes one if its probe finds a leg |

---

## C - low: narrow edge cases, comments, traps for the next caller

| id | status | where |
|---|---|---|
| SVA-C1 | open | `AutonomySession.sharedSquaresALengthRuleReads` 3240-3241 - "two roads" is asked of the geometry and "two legs" of the count, never of the same square together: a defective switch (`CUSTOM_PERM_*`) or a two-road square whose one road lies in front of a switch is asked for as a crossing |
| SVA-C2 | open | `AutonomySession.sharedSquaresALengthRuleReads` 3230-3233 - a crossing ONE leg runs over on both roads is deduplicated to one occurrence, stays in a piece, and the leg measures typed plus its share: the SET-B2 error in the shape the fix excludes |
| SVA-C3 | open | `AutonomyEditorPanel.nameForPrompt`, `autosetup.ui.infoNothingToMeasure`, `autosetup.ui.tooltipShowUnmeasured` - the siblings of the crossing step that still know only sensors and switches |
| SVA-C4 | open | `Layout.locDeleted` 1044 shipped with no test, and the rename half named in RTX-C3 is not fixed while the RTX table says "fixed" |
| SVA-C5 | open | `core.testMassAssignLengths.testTheSingleDoorSpeaksForTheWholeRunAfterAWalk` 903 - a headless skip on a test that opens no dialog; on a headless run the SET-B3 guard vanishes and reads as green |
| SVA-C6 | open | `AutonomyEditorPanel.clearAllMaxTrainLengths` 9720-9723 - the comment says `setupChanged` refreshes; it does not, and `clearAllHomes` 9750 keeps the extra `refresh()` the fix took out of its twin |

### SVA-C1 - the crossing predicate asks two questions of two different things

**Where.** `src/org/traincontrol/automationui/AutonomySession.java` 3220-3251. The count at 3228-3236 is per leg, over
intermediate squares; the filter at 3240-3241 is `count > 1 && !isSwitchSquare && getRoutes(tile).size() > 1`.
Reached from `stretchesALengthRuleReads` (2844), `squaresNeedingALength` (2998), `sharedSquaresNeedingALengthOn` (3262)
and so from the tile menu's count (`AutonomyEditorPanel` 2174), the walk (9337, 9400) and the Unmeasured Track display.

**What the javadoc claims and what the code tests.** The javadoc (3204-3210) says *"Both halves are required - the
geometry, and legs actually running over it on more than one of those roads"*. The code never asks which road a leg
uses. It asks whether the geometry HAS two roads and, separately, whether two or more legs pass the square. Two legs
over the SAME road of a two-road square satisfy it. Two shapes reach that:

1. **A defective switch.** `TilePorts` 318-325 gives `CUSTOM_PERM_LEFT/RIGHT/Y` two `into` routes and `THREEWAY` three,
   and `LayoutDiagramComponent.isSwitch()` (221-230) does not list them. Every leg through either branch runs over the
   square, so it is counted twice, has more than one route, and is not a switch - and is cut out of its pieces and
   asked for under *"Squares on this page that two roads cross"*, with one length shared with the real crossings, while
   the switch step (*"one turnout length"*) does not see it. The arithmetic is the same as a switch's - cut out, one
   length, added once to each leg - so nothing measures wrongly; the operator is told a turnout is a crossing.
2. **A double curve or crossing with track on both roads, where only one road is on any leg** and that road lies in
   front of a switch (two legs over it). Same classification, same correct arithmetic, same wrong sentence.

**On Adam's pages** (read from the `.cs2` files, reduction not run): the six `custom_perm_*` tiles are all on
`5 - Test`, in pairs facing each other with no other track on any side, so no leg reaches them. Every `doppelbogen` on
pages 1-4 has track on all four sides. So neither shape is live today; this is a trap for the next page.

**Prove it.** In `core.testMassAssignLengths`, a fixture with a sensor, a straight, a `CUSTOM_PERM_LEFT` whose toe faces
the straight, and a sensor on each of its two branches. Assert `session.sharedSquaresALengthRuleReads().isEmpty()` -
red today - or, if Adam wants defective switches asked for at all, assert they are asked in the switch step by
name. The fix that makes the javadoc true: record, per leg and per square, the route the leg uses (`TileStep` carries
the entry side and `TilePorts.Route.other` the exit, which is how `runs()` already walks a tile), and count a square
as shared only when the set of routes used has two members.

### SVA-C2 - one leg over both roads of a crossing is not a crossing to the rule

**Where.** `AutonomySession.sharedSquaresALengthRuleReads` 3230-3233: `onceEach` collapses a leg's several passes over
one square to one, so a square a single leg crosses on both roads - a figure-of-eight between two sensors, or a loop
that returns over its own crossing before the next sensor - counts 1 and is not shared. It therefore stays inside that
leg's piece (2853-2854 cuts only at `shared`), `assignStretchLength` (3038-3065) gives it one share, and
`GraphReducer.sumLength` (1320-1330) adds every step of the path, the square twice. The leg measures what was typed
plus one share, which is the SET-B2 over-measurement in a shape the fix does not reach.

Adam's ruling is *"count that length once in each direction"*, and a figure-of-eight leg does run over the square in
each direction; MAL-D2 accepted the double count for a bridge before the ruling existed. Under the ruling the right
treatment is the same as for two legs: cut it out, ask for it once, let the reduction add it per pass.

**Reachability.** Unknown. `1 - Main` has switches on two sides of the crossing at 18,10 and a pair of double curves
at 20,10-21,10 beside more switches; whether any single leg between two sensors passes one of them twice needs the
reducer's edges, which this pass did not compute. Narrow on a real railway; raised here because it is the same defect
one configuration over, and because the probe is cheap.

**Prove it.** A probe first, over the live snapshot: for every leg of `legsOnce()`, count the occurrences of every
square whose `getRoutes(tile).size() > 1`, and assert none exceeds 1 - if that assertion is red, this finding is a B
and the test below is the guard. Then a fixture in `core.testMassAssignLengths`: one sensor, a `CROSSING`, and curves
joining its north leg round to its east leg and its south leg round to its west leg, so the one leg leaves the sensor,
crosses, loops, crosses on the other road and returns. Assert `sharedSquaresALengthRuleReads()` contains the crossing -
red today - and after assigning the piece and the crossing, that the leg's `getLength()` is the piece's total plus
twice the crossing's, which is the ruling. The fix is to count passes rather than legs at 3233 (`merge(tile, passes,
sum)` where `passes` is the number of times the leg's list holds the square), keeping the endpoint exclusion.

### SVA-C3 - the siblings of the crossing step

Three places that describe the walk still know only sensors and switches; none is wrong enough to mislead about a
number, all are wrong enough to be noticed on MT-459:

- `AutonomyEditorPanel.nameForPrompt` (9566-9582): a piece's end is named by the setup's
  point name, or by *"the switch at x,y"* when it is a switch (MAL-C3), or else by `String.valueOf(tile)` - and a piece
  now ends at a crossing, which is neither. The stretch prompt on the crossing fixture reads *"between 1,2 and
  <key text>"*. A `crossingAtSquare` key beside `switchAtSquare` is the fix; the MT-459 step that reads the prompt
  is where somebody will see it.
- `autosetup.ui.infoNothingToMeasure` (*"Every stretch of track and every switch on this page already has a length"*)
  is said by the tile menu's tooltip (2190) and the walk (9341) after a page whose crossings have lengths too.
- `autosetup.ui.tooltipShowUnmeasured` describes the highlight as stretches and switches; `squaresNeedingALength`
  (2997-3000) now highlights crossings as well. `behaviour.md` 5b says the display highlights them; the tooltip does
  not.

All eight bundles for each. **Prove it** by reading: `I18n.f("autosetup.ui.promptMassAssignLength", ...)` built for the
crossing fixture's second piece contains the crossing's key text rather than a word.

### SVA-C4 - RTX-C3: no test, and half the finding

**Where.** `src/org/traincontrol/automation/Layout.java` 1044, inside `locDeleted`; the record is `reversedOnArrival`
(686-687, keyed by locomotive NAME). Commit `58f94664` adds tests for CS3-C1 and SET-C2 and none for this. The RTX
entry gave the recipe and said *"Red today"*; it has never been seen red, which is the rule the SOP puts first under
Fixing and the one `docs/reviews/README.md` says was broken most often.

**The half not fixed.** RTX-C3's own text: *"It is also the one per-train record keyed by NAME rather than by the
Locomotive object, so a rename orphans it the same way (nothing in Layout hears a rename)."* Still true.
`MarklinControlStation.renameLoc` (3399) calls `l.rename(newName)`, `view.autonomyLocomotiveRenamed(name, newName)`
and each route's `locomotiveRenamed`; nothing reaches `Layout`, and `TrainControlUI.autonomyLocomotiveRenamed` does
not touch the record. What that costs is not the orphan RTX-C3 traced (harmless, retried forever) but its mirror: the
train that WAS turned now stands there under a new name, the drain (`TrainControlUI` 7233-7281) hands
`faceTheWayItCameIn(oldName, point)` a name no standing train has, so the turn the railway made is never written into
the setup for the renamed train - and *"the next dispatch offered paths for the wrong heading"* (the drain's own
comment at 7222-7225) is exactly the symptom the record exists to prevent. The window is one idle refresh, between the
arrival and the drain, which is why it is a C and not a B.

**Prove it.** Two tests in `core.testHomeStaging` or `core.testLayout`, on the machinery `core.testATailFollowsTheRouteItCameIn`
uses to drive a turn through `executePath`: (a) the RTX-C3 recipe - record the turn, `locDeleted(loc)`, assert
`takeReversalsOnArrival()` is empty; this should be seen red once by reverting 1044, and is the test the fix should
have shipped with. (b) Record the turn, rename the locomotive, assert `takeReversalsOnArrival()` holds the NEW name
against the point - red today. The fix for (b) is a `locRenamed(String was, String now)` on `Layout` that moves the
entry, called from `renameLoc` beside the other doors it already notifies; the RTX table's "fixed" should read "fixed
for delete; rename open" until then.

### SVA-C5 - the SET-B3 test skips where it need not

**Where.** `test/core/testMassAssignLengths.java` 903: `if (GraphicsEnvironment.isHeadless()) throw new
SkipException("the panel needs a display")`. The test then calls `lengthShownFor` and `setRunLength` and reads the
store; it opens no dialog. The same file builds `AutonomyEditorPanel` with no guard at 264, 291 and 668, so the
constructor itself is known to work headless; the three tests that do guard (359, 429, 575) say why - *"the walk's
prompt needs a display"* - and this one copied the guard without the reason.

What it costs: on any headless run of the battery the only guard on SET-B3 is skipped and the class reads as passed
(the memory note "green is not no failures" is this shape). **Prove it** by removing the skip and running the test
headless; if the constructor genuinely needs a display there, the reason belongs in the message.

Recorded with it, not as a separate finding: the wiring in `applyLength` (6686-6699) - that the empty-selection branch
calls `setRunLength` and the prefill calls `lengthShownFor` - is untestable because the method is modal, exactly as
`squareTheLengthWouldGoOn`'s javadoc says of its own door. The mutation *"applyLength writes the leader only, as
before"* passes the whole suite. That is the accepted cost of the seam and MT-460 is the test of it; it is written here
so nobody reads the unit test as covering the door.

### SVA-C6 - SET-C3's comment miscounts, and its twin was not swept

**Where.** `src/org/traincontrol/gui/AutonomyEditorPanel.java` 9720-9723: *"`setupChanged` refreshes, and `item()`
refreshes again after the action, so the `refresh()` that stood here was a third full pass."* `setupChanged`
(8874-8899) nulls `unmeasuredSquares`, runs `onDiagramChanged` and posts `rebuildRunningLayoutSoon` (8908-8933).
`onDiagramChanged` in the editor is `LayoutEditor.refreshAutonomyAnnotations` plus `refreshStaticAutonomyLayer`,
neither of which calls `refresh()`; the only `autonomyPanel.refresh()` in `LayoutEditor` is the address checkbox
(7549). So after the fix there is one redraw - `item()`'s (2437-2468) - which is what SET-C3 asked for, and the
comment's account of where it comes from is wrong. Fix the sentence.

**The twin.** `clearAllHomes` (9750) calls `refresh()` and is also reached through `item()`, so it draws twice for one
press - the shape SET-C3 named, one door over. `clearAllTileLengths` (9661) and `clearAllPlacements` (9867) are clean.
**Prove it** by reading; a count of `refresh()` calls per bulk door is the check.

---

## D - not defects: looked wrong and is not, and checks that came back clean

| id | what |
|---|---|
| SVA-D1 | **SET-B1 holds on every door.** The negative is refused in `promptNumber` (4058-4065), which is the one method behind the station menu's item and Control+B (`promptMaxTrainLengthFor` 5696-5713 -> `promptNumber`), on both panels (`LayoutEditor` 1675, `TrainControlUI` 4673 share the class). The bulk door `assignMaxTrainLength` refuses `< 1` (3136) and its field is `digitsOnly`, so a minus cannot be typed; the capture writes `Point` values, which `Point.setMaxTrainLength` (1004) holds at 0 or above; `Layout.fromJSON` 10718-10728 still refuses `< 0`, now unreachable from the editor and the remedy for a hand-written file. `tilesWithAMaxTrainLength` at `!= 0` still excludes the explicit `maxTrainLength: 0` the capture writes on every destination, so SET-D9's argument stands. `hasNoMaximumTrainLength` (`<= 0`, 3149-3158) and the clear now disagree about a negative - Mass Assign Max offers the station, Clear All counts it - and both are ways out, which the SET-B1 entry asked for. Mutations the test catches: `!= 0` back to `> 0` (the `assertEquals` on the list is red), `whyNotThisNumber` returning null (red). Not caught: removing the call at 4058 - modal, stated in the javadoc |
| SVA-D2 | **SET-B3 does what it says, and what it says is narrower than "the stretch".** Empty selection: `applyLength` writes `setRunLength(leader, n)` (8684-8693) - the leader gets `n`, every follower 0 - and prefills `lengthShownFor` (8664-8671), the sum over `runTilesOf` (8703-8717: the leader plus every key in `runLeaders` whose value is it). A selection is written square by square as before, and the comment at 6693-6695 says so. `runLeaders` is refreshed in `refresh()` (8960) and not in `setupChanged`; a run does not depend on lengths, so a stale map after Control+E is harmless. A run is plain track only - `isRunTile` (7477-7486) refuses feedback, disqualified and every tile with more than one route - so **the number shown is the piece minus its end squares**: on the test's fixture the stretch typed 7 is 2,2,1,2 over sensor-plain-plain-station and the dialog opens on 3. That is what `behaviour.md` 887 says (*"what the RUN measures"*) and what the SET-B3 entry proposed (*"the station and sensor squares at a piece's ends ... would keep MAL-B2's unit"*); MT-460 does not state the number, and should say "3 for a 7" so the tester does not file it. Clearing the field writes 0 to the whole run; the piece's end squares keep their units, so the piece stays measured - Adam's whole-piece ruling, unchanged |
| SVA-D3 | **SET-B2's reduction is right and the walk's three questions agree.** `sumLength` (1320-1330) adds every step of the path; a crossing is an intermediate of both legs, so once per road. The boundary at 2853-2854 cuts it from every piece, and `to`/`from` take the crossing (2869-2870). `assignSwitchLength` (3075-3093) has no switch check, so it serves the crossing step; Skip returns -1 and continues to the next step, Cancel returns null and `stopped` (9397, added with the step) ends the walk - the same shape the pieces loop has. The menu's count (2170-2183) and the walk's guard (9335-9337) ask the same three questions; `squaresNeedingALength` adds crossings with no length (2997-3000) and the cache is nulled in both `refresh` and `setupChanged`. The room rule (`roomAfterTheLastSwitch` 1239, `unmeasuredAfterTheLastSwitch` 1309) bounds at `isSwitch()` only, so a crossing is not a room boundary, which is right: the ruling is about counting, not room. The endpoint exclusion (3232) is what keeps the three `s88doppelbogen` on Adam's pages - Points with two routes - out of the list |
| SVA-D4 | **SET-B2 on Adam's pages.** Read from the `.cs2` files: 3 `kreuzung`, 15 `doppelbogen`, 3 `s88doppelbogen`, 1 `unterfuehrung`, 6 `custom_perm_*`. On `1 - Main` the crossing at 18,10 and the double curves at 20,10, 21,10 and 11,11 are the four SET-B2 named, each with track on all four sides; the same is true of every double curve on `2 - Bottom`, `3 - Top Parking` and `4 - Combined` (listed by neighbour: 15,5 / 15,6 / 6,8 / 10,13; 6,13 / 18,16; 5,7 / 2,8 / 12,10 / 25,10 / 27,10 / 28,10 / 18,11). So on the used pages the geometry half and the leg-count half of the predicate should agree, and SVA-C1's shapes are not live. The `custom_perm_*` and the overpass-plus-crossing pair are on `5 - Test` with no track beside them. Whether any leg passes a crossing twice (SVA-C2) was not computable here |
| SVA-D5 | **RTX-B1 leaves AMH-B1 enforced, across several moves of one train.** `movedAlong` is the arrangement's own map - `routesOf.get(currentKey)` at 925, copied and extended per move at 1024-1026, an entry never removed within a plan - so after any number of moves a moved train has one, `turnsATrainArrivingAt(at)` (2514-2517) asks the square its LAST move ended on and `turnedOnTheWay` (2560-2570) the last route, and only `atHome` releases it. The greedy pass (795-831) moves trains only to their homes and so needs no rule; the retry from the railway as it stands (849-853) empties `greedyMovedAlong` first, so `startRoutes` (876-881) is empty there and the qualifier reads correctly on both A* entries. The homeless clause at 991 and the mid-move clause at 1008-1019 are untouched. The oracle `turnedAndNotSentHome` (4087-4144) is carried per train and marks a train owed only from a MOVE, which is the reading the fix adopts; both new tests call it, and each asserts its control (a reversible train's plan is READY and steps aside / moves the free agent) before the claim. Predicted mutations: drop `containsKey` - both new tests red; drop `turnsATrainArrivingAt(at)` - 3360 red (RTX's own mutation); invert the qualifier - 3360 and 3412 red. `testALocomotiveStartingOnATerminusIsPlannedHome` still expects one move straight home, which A*'s cost keeps. The runtime has no start-side reversibility rule - every `isReversible()` in `Layout` (4203, 4464, 4846, 4943, 6189) and `AutoLocomotiveStatus` (162) is about the destination - so a step-aside the planner may now make is one the runtime drives; and the runtime rule OB-205 (4784-4846) is about arriving at a terminus, which the planner's line 991 already respects for a homeless train. RTX-C1 stays open and, after B1, bites only a train the plan moved |
| SVA-D6 | **SET-C2 has one door and its write is inert where it looks odd.** The only demotion path is `AutonomySession.setStation` (5468-5511), reached from the panel's own `setStation` (3963/3975); `store.setStation(tile, true)` at 830 is load-time promotion. `writePointProperty(tile, "maxTrainLength", null)` creates an empty `{}` entry under `points` for a square that had none and marks the setup dirty - the same shape `setPointProperty(..., null)` has always had from `promptNumber`'s "leave it unset" path, and every reader of a point entry asks a key (`tilesWhere` predicates, 825, the builder's per-key copy), so an empty one decides nothing. Cancel restores it through `snapshotSetup` (SET-D1). The test asserts its precondition (the count is 1 before the demotion) |
| SVA-D7 | **RTX-C3's delete half is safe where it mutates.** 1044 runs under `locDeleted`'s `synchronized`, the map is a `ConcurrentHashMap` (686-687), and the two spans that hold a copy outside it - the idle drain (`TrainControlUI` 7233-7281) and the rebuild bracket (6482-6513) - are each one synchronous stretch on the thread that runs them, so a delete cannot land between the take and the put-back. `moveLocomotive` (8497) already removes the record on a hand placement, which is the precedent the fix follows |
| SVA-D8 | **`behaviour.md` says what the code does.** 5b's crossing paragraph (870-877) matches 3220-3251 including the one-leg clause (which SVA-C1 shows is true of the intent and looser in the code); the three-doors paragraph (878-885) matches the walks and the two clears, and its *"a negative is refused"* for Mass Assign Max is true twice over (`< 1` at 3136, `digitsOnly`); the Segment Length paragraph (887-890) matches `applyLength`; the Control+B row (1283) matches `promptMaxTrainLengthFor`'s refusal on a non-station; section 6's sentence (1353-1354) is the one the RTX-B1 fix enforces. The two new bundle keys are in all eight files, ASCII-only, and `tooltipMassAssignLengths` carries `{2}` in all eight. MT-458, MT-459, MT-460 and MT-463 exist, dispositioned "fixed unvalidated", and MT-459's expected lines are what the code does |
| SVA-D9 | **SET-C3's sibling sweep, measured.** `refresh()` calls inside the four bulk doors: `clearAllMaxTrainLengths` 0, `clearAllTileLengths` 0, `clearAllPlacements` 0, `clearAllHomes` 1 (SVA-C6). `item()` adds one for each |
| SVA-D10 | **The crossing test's arithmetic is the reduction's.** Four pieces of two squares at 6 each are 3 and 3 (the standing square first, MAL-B2), the crossing 3; each road is 3+3+3+3+3 less the square the train starts on, 12, which is what both `assertEquals` on `edge(...).getLength()` expect. `assertNoSquareIsInTwoPieces` and `describe` exist (1218, 1239); `set` (1165) and `edge` (1197) exist |

---

## What this pass did not cover

- Nothing was compiled or run. `I18n.f` taking a third argument is assumed from the two-argument calls beside it.
- The reducer's legs over the live snapshot were not computed, so SVA-C2's reachability and the claim in SVA-D4 that
  every double curve's both roads are on legs are read from the drawings, not measured.
- `core.testMassAssignLengths.testEveryLegIsCutIntoPiecesAtItsSwitches`, which the SET-B2 javadoc cites as the reason
  for the geometry half of its predicate, was not read; whether it pins that a plain straight in front of a switch
  stays inside a piece - which is what would catch a mutation dropping `getRoutes(tile).size() > 1` - is unverified.
- `regression.testControlEAsksTheMenusQuestion` and `testControlBAsksTheMenusQuestion` were not re-read; SET-B3
  changes what the door writes, not where, so their claim should be unaffected, but that is inference.
- The runtime readers of a leg's length after the crossing change - `roomAfterTheLastSwitch`, `measuredRouteIn`, the
  tail and berth walks (RTX-D1) - were not re-traced past confirming that the room rule does not bound at a crossing.
- The `TrainControlUI.autonomyTileMenus` panel's `runLeaders` freshness (it has no page and refreshes on its own
  schedule) was not checked; it predates SET-B3 and affects `leaderOf` as much as the new helpers.
- RTX-C5: the claim that the hover moved to `AutoLocomotiveStatus`'s worker (OB-079) was taken from the RTX entry; the
  worker was not re-read.
- CS3-C1, CS3-C2 and CS3-C3 in `58f94664` are the control-station half and were not looked at.
