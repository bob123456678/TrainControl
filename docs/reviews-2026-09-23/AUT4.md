# Autonomy validator, round 4 - round 3's fixes to AUT3, and the autonomy diff 2f4448b6..9f5d8e23

**Status:** open

**Prefix:** `AUT4`

**Reviewed:** branch `autonomy-diagram-r0` at `9f5d8e23`, 2026-09-23.  Baseline: AUT3 at `2f4448b6`; two-dot diffs `2f4448b6..9f5d8e23`, and each fix against its parent (`git show <hash>^:path`).

**Method:** a reading pass; nothing executed (Rule 1).  Read `git show` of `d65df6cb` (claims), `4be3798a` (fixes), `9a9a8564` (tracker), `031a7ddb` (text) and `9f5d8e23` (catalogue) for everything in `automation`, `automationui` and the gui doors that dispatch, place or put trains back; then, at HEAD: `moveLocomotive` (both forms), `isABarredCopyOfAStation`, `claimHome`, `rebuildHomeStations`, `putTheTrainsBack`/`whereTheTrainsAre` and the rebuild that calls them (`TrainControlUI.java:6640-6760`), every `moveLocomotive` caller; `flipFacing`, both `facingOnTheRailway`s, `facingChoices`, `facingsFor`, `copyFacing`, `placeableFacingsFor`, `moveOntoFacingCopy`, `faceTheWayItCameIn`, `placementCopy`/`startableCopy`/`nodeName`/the copy emission and `blockFor` in `AutonomyBuilder`, `homeFacingsAt`; `explainCannotStart` (both forms), `explainDestinations`/`firstClearOrWhyNot`, `getPossiblePaths`, `pickPath`'s start test, `isPathClear`'s start rules, every reader of `isAutonomyPaused`, `composeWhy`/`applyWhy`, `AutoLocomotiveStatus`'s two why windows, the right-click menu's path gate; `HomeStaging.snapshot`, the IMPOSSIBLE pre-scan, `whyNotHome`, `homeCopiesOf`, `canGetHome`, `canRest`, `atHome`; `writeHome`/`knownHomeFacing`/`homeFacingOf`/`setHome`; `captureFromLayout` whole; the legacy import's facing guess; the Facing menu.  Each claim in `d65df6cb` was read against the pre-fix code.  Railway facts were read with Python (read only, from stdin) from `test/layouts/live-snapshot/config/autonomy/` (stations, bars, turning squares, placements) and `test/baseline/configuration.json` (copy names and flags).  `findings.tsv` was grepped for the AUT2/AUT3 rows and the pause topic.

## A - high

(none)

## B - medium

### AUT4-B1 - the rebuild's put-back stands a train on a barred copy by NAME, even where a copy trains may arrive at faces the same way: on a square trains may turn at, barring the side a standing train came in by (or marking a barred square "may turn" under a reversed train) moves it off the startable copy the build chose onto one autonomy refuses, and both refusals then tell the operator to turn round a train that faces a way trains may arrive in

| | |
|---|---|
| **Disposition** | Fixed - 6cb11933 (claim 1c6ae0fe, red first), with TDY4-B1 |
| **Where** | `TrainControlUI.java:6498` (`built.moveLocomotive(was.getKey(), was.getValue()[0], false, true)`); `Layout.java:9022-9031` (`isABarredCopyOfAStation` - no facing test); `Layout.java:9037-9040` (the four-argument form's javadoc: *"at a square whose other facing only such a copy holds"*); against `AutonomyBuilder.java:744-757` (`placementCopy`'s allowed-first loops, before the any-copy loops at `:766-774`) and `AutonomySession.java:1591-1603` (`copyFacing`'s placeable-first loop) |

**New with `4be3798a` (TDY3-A1's fix), and not a restatement of TDY3-A1:** TDY3-A1 is the case where the barred copy is the ONLY copy facing the train's way (BottomMainA, not a turning square), and there the fix is right.  This is the case the fix's own javadoc excludes and its code does not.

The relaxation's javadoc states its precondition: a train can be on a barred copy *"reversed on the throttle, or turned by the Facing menu, at a square whose other facing only such a copy holds - the copy is its direction."*  `isABarredCopyOfAStation` asks only "no station itself, and some other copy of the square is one" - it never asks whether a copy trains may arrive at faces the same way.  On a square trains may turn at, one always can: a barred side's plain copy faces the same way as the OTHER side's turning copy, which is a terminus and a destination (`stops = point.isStation() && arrivalAllowed(node)`, `AutonomyBuilder.java:990`; the turning copy of an allowed side is emitted `terminus`, `:1172`).  Every other door that picks a copy for a facing takes that one first - `placementCopy` (allowed copies facing the record, plain then turning, before any barred one), `copyFacing` (placeable first), and DCN3-C3's claim `testTheBuildPrefersACopyItCanStartFrom` pins it for the build.  The put-back reverses that choice, because it re-stands by the Point NAME `whereTheTrainsAre` recorded (`TrainControlUI.java:6347`), and a name recorded before the edit is the plain copy.

**Scenario A - a bar added under a standing train** (the live-snapshot railway: BottomMainB `5:20,13` may turn, no bar).  A train comes in from the east and stands on `BottomMainB (westbound)` (plain, arrival E, a station).  The operator opens the editor (which captures: setup `loc` BottomMainB, `facing: W`) and unticks East under Trains May Arrive...  `setArrivalAllowed` -> `setupChanged` -> rebuild (`TrainControlUI.java:6699-6726`, `placementsJustEdited` names nobody):
1. The build: `placementCopy(W)` - loop 1 (plain, allowed, facing W) finds nothing, `BottomMainB (westbound)` now being barred; loop 2 (allowed, facing W) takes `BottomMainB (eastbound, reverse)` - the WEST arrival's turning copy (named by its arrival heading, `nodeName`, `AutonomyBuilder.java:874`; it faces W), a terminus, a destination.  The train is startable.
2. `putTheTrainsBack`: `back` = `BottomMainB (westbound)` holds nobody, so `moveLocomotive(train, "BottomMainB (westbound)", false, true)` - `isABarredCopyOfAStation` is true - `clearBlockExcept` + `setLocomotive` move the train OFF the turning copy onto the barred one.  Before `4be3798a` this was refused and `continue`d, and the train stayed on the startable copy.
3. Now: `pickPath` never starts it (`start.isDestination()`), `explainCannotStart` says *"It is facing the way trains may not arrive at BottomMainB, so autonomy will not start it there.  Turn it round, or open that side..."* - false: trains may arrive at BottomMainB and stand facing west (from the west, turning); and if the train is homed anywhere else and away from it, Return Home answers IMPOSSIBLE for the WHOLE railway (`HomeStaging.java:552`), naming it with *"it faces the way trains may not arrive at BottomMainB - turn it round, or open that side"*.
4. The first remedy offered is wrong in kind: "turn it round" (`flipFacing` -> E -> `copyFacing(E)` -> `BottomMainB (eastbound)`) makes the operator reverse a train that already faces a startable way.  The one that works without changing anything physical - picking W again in the Facing menu (`setFacingAndMove(W)` -> `copyFacing(W)` -> the turning copy) - is named nowhere.  The second ("open that side") undoes the bar he just set.

(From the diagram's right-click menu, which captures nothing, the stale setup may have the build stand the train on another square entirely - TDY3-A1's case, which `4be3798a` does improve - and it ends on the same barred copy.  AUT3-D2's bullet *"the build puts the train on the barred copy by the setup's facing, so ... `moveLocomotive` ... is not asked"* was true only of a square that does not turn.)

(A train that had turned on arrival - on `BottomMainB (westbound, reverse)`, facing E - meets the same thing: barring E makes that copy a `reversing` non-station, the build takes `BottomMainB (eastbound)` (arrival W, plain, facing E), and the put-back moves it onto the barred reversing Point.  What standing on a reversing Point does to a hand send's first edge I did not trace.)

**Scenario B - "may turn" set under a reversed train** (live snapshot, BottomMainA: E arrivals barred, not a turning square).  The round-3 claim's own state: PROBE reversed onto `BottomMainA (westbound)` - the barred copy, correctly, because it is the only W copy.  The operator then marks BottomMainA "trains may turn here" (`setTurning` -> `setupChanged`).  The rebuild now emits `BottomMainA (eastbound, reverse)` (arrival W, allowed, terminus, faces W); `placementCopy(W)` puts PROBE there; the put-back moves it back to the barred plain copy by name.  The precondition the javadoc names (*"whose other facing only such a copy holds"*) stopped being true in this very edit.

**On Adam's railway.**  behaviour.md §7: *"A BARRED ARRIVAL STOPS BEING SENT THERE AND NOTHING ELSE"*, and of the two squares that ruling was made for, *"Both of his squares are `canReverse`"* (RampDown, BottomMainPost).  On the frozen railway seven stations carry a bar; those two may turn, the other five (BottomMainA, BottomInner, Tunnel, TopMainR1Inter, TopMainR2Inter) do not and are TDY3-A1's shape, where the fix is right.  Four stations may turn (BottomMainB, RampDown, BottomMainPost, LowerFront).  On RampDown and BottomMainPost as they stand no train can be on the barred plain copy - the build, the flip and the Facing menu all take the allowed copy - so what reaches this is a bar set on a turning platform with a train standing there that came in that way, or "may turn" set on a barred platform with a train reversed onto its barred copy.  A compulsory-turn square is not affected: all its copies turn, so a barred copy there is the only one facing its way.

**Why nothing compensates.**  The state is re-created by every later rebuild (the running layout's copy name is carried again), and undone only by a restart, which builds from the setup alone and stands the train on the turning copy - so whether autonomy will start that train depends on whether the application has been restarted since.  `captureFromLayout` writes `facing: W` either way (`facingByName` of either copy), so the file cannot tell the two apart.

Reading only; every step is quoted code.  **Verification request (execution, deterministic, core, no window)** - in `core.testATrainIsPutOnlyWhereItCanStart`'s fixture, beside `testATrainPutBackStandsWhereItStood`:
- **B (fewest moves):** `stoodFacing(session, running, mainA, Side.E)`, `session.placeLocomotive(mainA, PROBE)`, `session.setFacingAndMove(mainA, Side.W)` (PROBE on the barred W copy - the existing claim's precondition).  Then `session.setPointFlag(mainA, AutonomyBuilder.CAN_REVERSE, true)`; `where = TrainControlUI.whereTheTrainsAre(running)`; `rebuilt = build(session)`; **control:** `standingOn(rebuilt)` is a destination and `session.facingsFor(mainA).get(it.getName()) == W` (the build's choice).  Then `TrainControlUI.putTheTrainsBack(rebuilt, where, null)`.  **Proves it:** `standingOn(rebuilt)` is NOT a destination while a destination copy of mainA facing W exists in `rebuilt`, and `rebuilt.explainCannotStart(probe)` equals `autolayout.why.startFacingBarred`.  **Refutes it:** PROBE ends on a destination copy facing W.
- **A:** `mainB = square(session, "BottomMainB")`; precondition `session.arrivalSides(mainB)` contains E and `mainB` may turn; `stoodFacing(session, running, mainB, Side.W)` (first W station copy = the plain E-arrival copy), `session.placeLocomotive(mainB, PROBE)`, `session.setFacing(mainB, Side.W)`; `session.setBarredArrivals(mainB, EnumSet.of(Side.E))`; then as above.  Same proof and refutation.

**Suggested fix.**  Enforce the javadoc's precondition where the relaxation is granted: accept a barred copy only when no destination copy of the same square has its copy facing (`Point.getCopyFacing()`); otherwise stand the train on that destination copy - plain first, as `copyFacing` - and put the side and road back on THAT Point (the lines after the move write them onto `back`).  In `putTheTrainsBack` the cheap form is: when `back` is barred and the build already has this train on a destination copy of the same square with `back`'s copy facing, keep it there and restore side and road onto it.

## C - low

### AUT4-C1 - `explainCannotStart(loc, true)` is not the question a hand send meets, in either direction: it keeps "paused", which no hand send asks, and drops the two start rules `isPathClear` still applies to a hand send while autonomy is running

| | |
|---|---|
| **Disposition** | Fixed - 6cb11933: by hand, the start rules while autonomy runs (as `isPathClear` applies them), none otherwise, and no pause.  The running half has no claim: Why not Moving? cannot be open while autonomy runs (OB-047) |
| **Where** | `Layout.java:4931-4946` (the new by-hand branch and its comment *"BY HAND, ONLY WHAT STOPS ANY ROUTE"*); `Layout.java:4462`, `:4697` (the only readers of `isAutonomyPaused` outside the two explanations); `Layout.java:2428-2445` (`isPathClear`'s inactive and non-station start rules, both `this.isAutoRunning() && ...`); `Layout.java:5698-5699` (`firstClearOrWhyNot`'s substitution); caller `AutonomyEditorPanel.java:7891` |

The task asked whether anything that dispatches by hand now goes ahead where it should have been told why not.  **No dispatch door consults `explainCannotStart`** - its one by-hand caller is `composeWhy`, an explanation - so nothing is driven differently.  What the new branch gets wrong is the explanation, both ways:

- **Paused is reported, and stops no hand send.**  `isAutonomyPaused` is read by `pickPath` (`:4697`) and `checkForSlowerLoc` (`:4462`) and nowhere else: not `getPossiblePaths` (`:5811`), not `isPathClear`, not `executePath`, not the right-click menu (grep: no `Paused` in `LayoutRightclickAutonomyMenu`).  The pause button's own tooltip: *"Temporarily pause this locomotive from automatically running."*  So on Path Type Manual the editor says *"**X** cannot be sent anywhere: This locomotive is paused."* while the diagram's menu lists that train's hand destinations and runs them - AUT3-C1's shape, for the one autonomy reason the fix kept, under a comment that now says it stops any route.  Pre-existing (the Manual answer was autonomy's before `4be3798a`); what is new is the comment asserting it.
- **The start rules are dropped unconditionally, and they are conditional.**  While autonomy is running `isPathClear` refuses a hand route from an inactive square or a non-station copy (`:2429`, `:2438`).  The by-hand branch now returns null for such a train, so `composeWhy` goes on to `explainDestinationsGrouped(standing, true)`, where every station's route fails `isPathClear` and `firstClearOrWhyNot` substitutes, because autonomy is running, *"Blocked by a train or a route in progress."* (`:5699`) - "wait a minute", about a refusal waiting does not clear until autonomy stops.  Before `4be3798a` the same window named the start.  Reachable where the editor is open during a run (the `setupEditDeclinedDuringRun` machinery exists for edits made then); I did not trace whether the Why tool is reachable in that state, so this half rests on reading.

**Verification request.**  (1) Execution, core, no window: live-snapshot fixture, PROBE stood on any station copy; `model.getLocByName(PROBE).setAutonomyPaused(true)`.  **Proves the first half:** `running.getPossiblePaths(probe, true)` is non-empty AND `running.explainCannotStart(probe, true)` equals `autolayout.why.paused`.  (2) The second half needs `isAutoRunning()` true (a run started, or the flag set by reflection): PROBE on BottomMainA's barred W copy; `running.explainCannotStart(probe, true)` null, `running.getPossiblePaths(probe, true)` empty, and every station of `running.explainDestinations(probe, true)` that is not occupied or excluding PROBE answers `autolayout.why.blockedWhileRunning`.  **Suggested fix:** in the by-hand branch drop "paused", and keep the two start sentences when `isAutoRunning()` - the same fence `isPathClear` puts on them.

### AUT4-C2 - sentences the round made false or left beside their contradiction, and a second walk of one question

| | |
|---|---|
| **Disposition** | Fixed - 6cb11933: the import comment rewritten with REG4-A1, `placeableFacings`, the single-facing tick; the private `facingOnTheRailway` renamed `facingOfTrainOnTheRailway` and asked of the station index |
| **Where** | `AutonomySession.java:789-792`; `TrainControlUI.java:8223`; `AutonomyEditorPanel.java:3750`; `AutonomySession.java:1632` against `:7316` |

- `AutonomySession.java:789-792` (the legacy import): *"Not legality-checked, unlike placing one by hand on the diagram: that asks the RUNNING graph ... and during an import there is no running graph to ask"* now stands directly above the REG3-C1 paragraph that DOES check the guess, against `homeFacingsFor` (the builder's arrival rule, which needs no running graph).  The lead sentence contradicts the code under it - the OPV-C3 shape the same comment block records having fixed once.
- `TrainControlUI.java:8223` (`placeableFacings`' javadoc, rewritten in `031a7ddb`): a copy trains may not arrive at is one *"a train can come to stand there only by turning where it is"*.  Also by standing there when the side is barred, and - since `4be3798a` - by the rebuild's put-back (AUT4-B1).  `placementCopy`'s own comment names both ways (*"a train reversed on the throttle, or already standing when the side was barred"*).
- `AutonomyEditorPanel.java:3750`: *"So the one facing is shown, ticked"* - since GUI3-C4 the tick is `recorded != null && facing == recorded`, so a one-facing square whose train the railway does not hold and the setup gives no facing shows it unticked.  (Narrow: the railway normally holds the train, and then it is ticked.)
- `facingOnTheRailway` is now two methods of one name: the new private `(tile, locomotive, running)` at `:1632` and the public `(square, running)` at `:7316` (OB-181's), with `homeFacingOf`'s own loop (`:7501`) and `facingOf(loc, running)` a third and fourth walk of "the copy the railway has this train on".  In `flipFacing` every candidate square already holds this train, so the public one gives the same answer.  Cosmetic - noted because a second author for one question is how this repository's siblings drift.

Reading only; no execution needed.

## D - not defects (checked and sound)

### AUT4-D1 - AUT3-A1 / TDY3-A2 verified: the direction-follow reads the railway, cannot leave `facingChoices`, and DIR-C3 is intact

| | |
|---|---|
| **Disposition** | Closed - checked clean |

`flipFacing` now takes `recorded` from `facingOnTheRailway(tile, locomotive, running)` - `facingsFor(tile).get(point.getName())` for the Point the running layout has this train on - and the setup's `FACING` only when that is null.  **Can it flip where the railway's facing is not one of `facingChoices`?**  No: `facingChoices(tile)` is the distinct values of the same `facingsFor(tile)` map (`AutonomySession.java:6226`), so a facing read off a copy is always among them; DIR-C4's `!choices.contains(recorded)` skip is now reachable only through the setup fallback, where it was written.  **DIR-C3's two-square case:** it arises only when the railway does not have the train, and then `facingOnTheRailway` loops over Points none of which holds it and returns null for every candidate, so each square falls back to `getFacing` exactly as before; `testADirectionChangeDoesNotFlipTheSquareTheTrainLeft` still drives that path (`new Layout(null)`).  A train the railway holds under a name the station index cannot place yields no railway candidate and no railway facing - the setup path, unchanged.  AUT3-A1's case 1 (no stored facing), case 2 (another train's, opposite) and the variant where the setup does not place the train at all are all answered by the railway read, which never consults the setup's `loc`.  **The claim** `testAReversalIsFollowedWhateverTheSetupLastSaid` is red on `4be3798a^` for the right reason in both iterations (`null`: `continue`, the flip returns null; `W`: `now` = E, `moveOntoFacingCopy` finds PROBE already on the E copy), and the assertion that carries it is the facing of the copy PROBE stands on - the variable, not a control.

### AUT4-D2 - AUT3-B1 verified, with AUT4-B1's exception

| | |
|---|---|
| **Disposition** | Closed - checked clean |

Both refusals now name the square (`placeNameOf`) and give a remedy; the other two start sentences name the square too.  `whyNotHome`'s new branch is an if/else-if over the pre-scan's own `!isDestination()` (`HomeStaging.java:552`), so every non-destination start still gets exactly one sentence, and a square demoted whole (no destination sibling) still gets *"not a station"* - which is true there.  `this.layout` is never null in `whyNotHome` (`snapshot` is the only constructor path and dereferences the layout first), and what it reads - `isDestination` and the block - is fixed at parse, so the class's "nothing afterwards reads live state" holds in substance.  The claim `testATrainFacingABarredWayIsToldWhy` is red on `d65df6cb` for the right reasons (the copy's name and "not a station"; the by-hand stub delegated; Return Home's old sentence).  The sentence is false only in AUT4-B1's state, where a copy trains may arrive at faces the train's way.

### AUT4-D3 - AUT3-C1 verified for the barred copy; the call site is read, not claimed

| | |
|---|---|
| **Disposition** | Closed - checked clean |

`composeWhy` passes the tier (`AutonomyEditorPanel.java:7891`), and on Manual a train on a barred copy or a switched-off square now gets the hand destinations the right-click menu offers (outside a run - AUT4-C1 for inside one, and for "paused").  The claim asserts the rule (`running.explainCannotStart(train, true) == null`), not the call: a caller left on the one-argument form would pass it.  The call is one line and reads right.  `AutoLocomotiveStatus` (`:735`, `:897`) keeps the autonomy form, which is its question (the locomotive panel's windows are autonomy's).

### AUT4-D4 - AUT3-C2 / GUI3-C2 verified

| | |
|---|---|
| **Disposition** | Closed - checked clean |

`writeHome` writes `knownHomeFacing`, which returns a facing only if `homeFacingsFor(tile)` contains it; the explicit overload keeps its own test; clearing a home (`locomotive == null`) writes null.  The editor asks exactly when `knownHomeFacing` is null (TDY2-C4's condition), so a train on a barred copy is now asked rather than silently saved - the door and the rule ask one question.  `homeFacingsAt` includes the turning copies of allowed sides, so on a turning square the facing of a barred plain copy is still saved when the other side's turning copy holds it - correct, `homeCopy` can honour it.  The claim `testAHomeIsNotSetFacingAWayNoTrainArrives` is red on `4be3798a^` (`"W"` saved) and asserts the stored property.  `captureFromLayout`'s `HOME_FACING` write comes only from a builder-emitted fixed home, which `homeCopy` places on an allowed facing, and `Layout.setHomeLocomotive` (which would fix a home to any copy) has no caller outside tests - no second way to save a barred home facing.

### AUT4-D5 - the relaxation's reach: who can pass `true`, and what the runtime does with a train stood there

| | |
|---|---|
| **Disposition** | Closed - checked clean |

- **Only one caller passes `true`**: `putTheTrainsBack`'s railway-wins branch (`TrainControlUI.java:6498`).  Every placement door - `GraphLocAssign.java:811`, `LayoutRightclickAutonomyMenu.java:1205`, the paste at `TrainControlUI.java:7299` - uses the three-argument form and still refuses; `:1450` and `:7344` are removals.
- **`isABarredCopyOfAStation` is exact on a built graph** as to WHICH Points are barred copies: a station square's only non-destination copies are barred ones (`stops = point.isStation() && arrivalAllowed(node)`, `AutonomyBuilder.java:990`); a square demoted whole has no destination sibling and is still refused (AMS-C2's `continue` kept); the tile-wide copy is never barred.  Two-road squares (double curve, overpass) key the block per road, so a barred copy never borrows the OTHER road's station; a one-copy road has no block and answers false (the put-back refuses, the sentence says "not a station") - narrow, and the safe side.  (What it does not ask - the facing - is AUT4-B1.)
- **The runtime, for a train stood there**: autonomy never starts it (`pickPath`'s start test; `isPathClear`'s auto-running start rule); hand sends leave from it outside a run; the HomeStaging pre-scan and `firstClearRoute` (`HomeStaging.java:1107`) refuse it as an origin; the tail walk gets its side and road back (`putTheTrainsBack` writes both onto `back` after the move); `captureFromLayout` writes the copy's facing and the next build honours it (`placementCopy`'s third loop) where no allowed copy faces that way - a stable round trip; the turning-square case is AUT4-B1.
- **`claimHome`** inside the four-argument `moveLocomotive` can give a homeless train a positional home on the barred copy.  Harmless: `homeCopiesOf` is every copy for an unfixed home, `canRest` requires a destination, and `atHome` compares the square - the home behaves as the square; positional homes are not written to the setup (`Point.toJSON` writes only the assigned `homeLoc`).
- **The claim** `testATrainPutBackStandsWhereItStood` is red on `4be3798a^` for the right reason (the three-argument form refused the barred copy and PROBE stayed on BottomMainPost, the setup's square), and asserts the Point PROBE stands on.  It exercises only a square where the barred copy is the only one facing that way - AUT4-B1 is the other case.

### AUT4-D6 - REG3-C1 / TDY3-B1 (the import's guess) verified; GUI3-C4 read

| | |
|---|---|
| **Disposition** | Closed - checked clean |

The legacy import now guesses the first facing in `homeFacingsFor(tile)` - the builder's arrival rule over the store's barred arrivals (`builder(null)` applies `withBarredArrivals`, `AutonomySession.java:4074`), available at import with no running graph - and the first copy's only where none is arrivable.  The claim `testAnImportedFacingGuessCanStart` asserts the recorded guess is in `mayArrive`, on a fixture whose first-emitted copy it checks is barred.  (AUT4-C2 for the comment above it.)  GUI3-C4: the Facing menu ticks only `recorded` (railway first, then setup); its claim is a source-shape guard on one spelling (`facing == facings.get(0)`), which knows only what it lists - the one line reads right.

### AUT4-D7 - the open and decided items, and the rest of the range

| | |
|---|---|
| **Disposition** | Closed - checked clean |

- **AUT3-B2** - disposition truthful: OB-284 is filed in `issues.md` (`9a9a8564`) as Adam's call with both answers stated; AUT2-A1's store row and AUT2.md now read "Partly fixed ... Open - Adam's decision ... OB-284"; `open-questions.md`'s Inbox count moves 65 -> 66 with the entry named; behaviour.md 639 now describes what the paste does and points at OB-284 instead of calling the door rule settled.
- **AUT3-C3** - "Open - for a follow-up" is accurate; nothing in the range touches the tail walk's first hop.
- **`031a7ddb`** in this lane's files: the `startableCopy` sentences, the `copyFacing` caller note at `TrainControlUI.java:7437-7439`, `setAtomicRoutes`' "gives an edge back twice" and the fixed joins read right against the code (AUT4-C2 for `:8223`).
- **The two new sentences** (`autolayout.whyHomeStartFacingBarred`, `autolayout.why.startFacingBarred`) exist in all eight bundles with `{0}` and `{1}`, `\uXXXX`-escaped, and carry no ASCII apostrophe - `I18n.f` is `MessageFormat.format`, where one would swallow a placeholder (the French and Italian elisions are U+2019).  `{1}` is filled with `autosetup.ui.menuArrivalsGroup`, the menu's own label, so the sentence names the control the operator will see.
- **`9f5d8e23`**: AUT3's store rows match AUT3.md's dispositions.  AUT3-B1's "say the train faces the way trains may not arrive" is true except in AUT4-B1's state.
- Known and deferred (AUT2-C2, GUI2-C2, GUI2-C4, AUT3-C3; OB-284, OB-283, REG2-C3, REG2-C7, AUT-C2, DCN-C3, REG-B1's import question) were not re-examined.

## What this pass did not cover

- **Nothing was run.**  AUT4-B1 and AUT4-C1 carry execution requests (B1's variant B is the cheapest: four lines added to the round-3 claim's own setup); AUT4-C2 is settled by reading.
- The live snapshot's BottomMainB was taken to arrive by E and W and to split into four copies from its setup (`canReverse`, no bar); the frozen railway has no committed build, so AUT4-B1 variant A states that as a precondition for the probe to check.
- What a train standing on a `reversing` Point (AUT4-B1's turned variant) meets at a hand send's first edge (`reversesAlongTheWay`, the terminus rule) - not traced.
- Whether the editor's Why tool can be used while a run is going with Path Type on Manual (AUT4-C1's second half) - not traced.
- Neither `explainCannotStart` form says a train is underway; the right-click menu's per-locomotive gate hides its paths while `explainDestinations` would still answer - pre-existing in both tiers, noticed, not examined.
- The GUI side of the round (the Facing menu beyond its tick, the paste and Place doors' OB-284 behaviour) and the translations of the two new sentences beyond their keys and placeholders.
