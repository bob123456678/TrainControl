# Autonomy validator, round 3 - round 2's fixes to AUT2, and the autonomy diff 08a47bdd..2f4448b6

**Status:** open

**Prefix:** `AUT3`

**Reviewed:** branch `autonomy-diagram-r0` at `2f4448b6`, 2026-09-23.  Baseline: AUT2 at `08a47bdd`; two-dot diffs `08a47bdd..2f4448b6`, and each fix commit against its parent (`git show <hash>^:path`).

**Method:** a reading pass; nothing executed (Rule 1).  Read every commit in `08a47bdd..2f4448b6` that touches `automation`, `automationui` or the doors that place or dispatch trains (`8346be65`, `8370abb1`, `4132d260`, `9c85db2a`, `1facc0c2`; `264f2a73`, `b53439dc`, `91daff27` and `2f4448b6` for what they touch here) as a diff, then the code around each change as it stands at HEAD: `placementCopy`/`startableCopy`/`homeCopy`, `copyFacing`/`placeableFacingsFor`/`moveOntoFacingCopy`, `flipFacing`, `faceTheWayItCameIn`, `setFacingAndMove`'s callers, the paste door (`TrainControlUI` 7150-7440, 8165-8185), `GraphLocAssign`, `putTheTrainsBack`, `moveLocomotive`, `parseAuto`'s placement, `captureFromLayout` whole, `explainCannotStart` and both why-not windows, `getPossiblePaths`, `isPathClear`'s start rules, `HomeStaging.snapshot`/the IMPOSSIBLE pre-scan/`whyNotHome`/`atHome`, `writeHome`/`homeFacingOf`/`knownHomeFacing`/`setHome`, `configureAndLockPath` whole with every exit, `executePathInternal` and `executePath`'s handler, and `walkStandingTrains`/`walkOneTail` whole with `getNeighborsAndIncoming`, `placeKey`, `entrySideOf`.  Each claim in `8346be65`/`9c85db2a` was read against its parent.  Railway facts were read with Python (read only, from stdin) from `test/baseline/configuration.json` (the blessed build - rails, places, copy names, blocks), and from `test/layouts/live-snapshot/config/autonomy/` (placements, homes, barred arrivals, names).  `findings.tsv` was grepped for every topic below.

## A - high

### AUT3-A1 - the throttle's direction-follow still pivots on the setup's stored facing, which after any run is missing or another train's, so a reversal made after a run is not followed (or is "followed" onto the copy the train is already on) and the train is dispatched against its decoder - TDY2-A1's consequence through the case its fix and claim do not reach

| | |
|---|---|
| **Disposition** | Fixed - 4be3798a (claim d65df6cb, red first), with TDY3-A2 |
| **Where** | `AutonomySession.java:1889` (`Side recorded = getFacing(tile);`), `:1900` (`if (recorded == null ...) continue;`), `:1915` (`now` = the other of `recorded`); caller `TrainControlUI.java:12648` (`followDirectionChanges`) |

**Pre-existing, not introduced by this range** - `flipFacing` has pivoted on the setup since DIR-B3 (2026-09-06).  It is raised here because `8370abb1`'s disposition for TDY2-A1/GUI2-A1 says *"the throttle's direction-follow ... now move the train"*, and that is true only while the setup's stored facing for the square agrees with the copy the train stands on.  The claim `testAReversalOnTheThrottleIsFollowed` sets that agreement up by hand (`session.setFacing(mainA, Side.E)` with the train on the E copy), so it cannot see the other two states.

`flipFacing` finds the square from the RUNNING layout (REG6-A2) but reads which way the train faces from the SETUP: `recorded = getFacing(tile)` is `getPointProperty(tile, FACING)`.  behaviour.md 6a states that record is stale exactly after a run: *"a run moves trains, where they ended up lives only in the running layout, and nothing writes it back to the setup when the run ends ... A recorded facing is also never cleared, so an empty square still carries the last occupant's"* - and §3 describes the drain's version of this pivot as the cause of OB-190.  Nothing captures at the end of a run (`captureRunningLayout` runs at editor open and close, a diagram reset, a page rename or delete; `captureFromLayout` also at exit and at a viewer reload).  So after a run, with the train on copy X of its arrival square and its decoder then reversed on the throttle while idle:

1. **No stored facing** (the ordinary case: on the frozen railway seven squares carry a stored `facing`, four of them under a placed train; BottomMainB, EN57-947's home, carries none): `recorded == null` -> `continue` -> `flipFacing` returns null.  Nothing is followed and nothing is logged.
2. **Another train's facing, opposite to X**: `now` = X, `setFacing(tile, X)`, `moveOntoFacingCopy(... X)` finds the copy the train is already on (`if (onto.getCurrentLocomotive() == train) return;`), and the log says `infoFacingFollowedDirection`.
3. Only a stored facing equal to X gives the right answer.

In cases 1 and 2 the train stays on copy X while its decoder now drives it the other way.  The runtime never commands an absolute direction (`Layout.java:8431`, `:8801` are the only `switchDirection` calls), so the next autonomy dispatch or hand send locks a route in X's direction and the train sets off the other way over track nothing reserved - the A consequence TDY2-A1 and GUI2-A1 were graded for.

**This is the drain's defect, fixed at the drain only.**  `regression.testTheTurnAtTheDestinationReachesTheDiagram`'s javadoc describes exactly these two failure modes for the idle drain (*"No stored facing ... flipFacing has nothing to flip"*; *"The turn is written backwards"*), and REV9-A1 fixed the drain by making `faceTheWayItCameIn` read the railway.  The operator's own reversal still goes through `flipFacing`.  CONF-B1/PRW-B3 (`facingOf`) and TDY-C2 (`homeFacingOf`) moved their siblings to "the running copy first" for the same staleness; this is the one left.

**Why nothing compensates.** `lastSeenDirection` is levelled when the railway goes idle, so the operator's reversal is a genuine change and reaches `flipFacing`; its null return is not logged.  `captureFromLayout` would write the running copy's facing, but only at the next editor open, which is after the reversal.

**Verification request (execution, deterministic, core, no window).**  In `core.testATrainIsPutOnlyWhereItCanStart`'s fixture (live snapshot, `session()`, `running = build(session)`, `session.setRunningLayoutSource(() -> running)`): take BottomMainB (`square(session, "BottomMainB")`); precondition `session.getFacing(bmb) == null` (the frozen setup has none).  Stand PROBE on a destination copy of it facing W (`stoodFacing(session, running, bmb, Side.W)`) WITHOUT `session.placeLocomotive` - which is all a run's arrival writes.  (1) `session.flipFacing(PROBE, running)`.  **Proves it:** returns null and PROBE still stands on a W-facing copy.  (2) Then `session.setFacing(bmb, Side.E)` (a previous occupant's facing) and flip again.  **Proves it:** returns `bmb`, `getFacing(bmb) == W`, PROBE still on a W-facing copy (`session.facingsFor(bmb).get(standingOn(running).getName()) == W`).  **Control:** `session.setFacing(bmb, Side.W)` and flip - PROBE moves to an E-facing copy.  **Refutes it:** PROBE ends on an E-facing copy in (1) or (2).

**Suggested fix.**  Pivot on the facing of the copy the running layout has the train on (`facingsFor(tile).get(point.getName())` for the Point found in the candidate loop), falling back to `getFacing(tile)` only for a candidate that came from the setup (the REG7-A2 branch) - the rule `facingOf(loc, running)` and `homeFacingOf` already follow.  The claim should leave the setup's facing absent.

## B - medium

### AUT3-B1 - a train stood on a barred copy by `8370abb1` is refused with sentences that call its station "not a station": Return Home refuses the whole railway saying *"it stands on BottomInner, which is not a station - place it on a station first"*, and following that advice at the same square turns the model round against the decoder

| | |
|---|---|
| **Disposition** | Fixed - 4be3798a (claim d65df6cb, red first): Return Home and Why not Moving? say the train faces the way trains may not arrive, by the square's name, and what to do |
| **Where** | `HomeStaging.java:550-559` (the IMPOSSIBLE pre-scan's `!isDestination()`), `:1946-1948` (`whyNotHome`), `Layout.java:3560` (`placeNameOf` strips the copy), `Layout.java:4944` (`explainCannotStart`); `messages.properties:213`, `:1784`, `:1799`; the doors at `TrainControlUI.java:7263`, `:8183`, `GraphLocAssign.java:238`, `:287`, `AutonomyEditorPanel.java:5445` |

`8370abb1` chose option (a) of GUI2-A1: where only a copy trains may not arrive at faces the recorded way, the train stands there, *"autonomy refuses to start it there and says why"* (its commit message; `copyFacing`'s javadoc: *"On a copy autonomy cannot start from it is refused with a sentence saying why"*).  The refusals are right.  The sentences are the pre-`1c855483` ones, and they do not say why:

- **Return Home** (`whyNotHome`): `autolayout.whyHomeStartNotAStation` = *"it stands on {0}, which is not a station - place it on a station first"*, filled with `Layout.placeNameOf(from)`, which strips the copy suffix (Adam, 2026-09-13: *"the whole copy thing needs to be masked from the user"*).  So it reads *"it stands on BottomInner, which is not a station"* - of a square the diagram shows as a station and the setup lists in `stations`.  And it is an IMPOSSIBLE, which refuses the WHOLE Return Home (*"IMPOSSIBLE refuses the WHOLE staging run"*, `HomeStaging.java:586`).
- **Autonomy** (`explainCannotStart`, shown by both why-not windows and the tooltip): *"It is standing on BottomInner (eastbound), which is not a station, so autonomy will not start it from there"* - the copy name unmasked, and "eastbound" is the arrival heading, not the way the train faces (S).  The editor prefixes it with *"<b>{0}</b> cannot be sent anywhere:"*.

**Reached on Adam's railway by one ordinary gesture.**  Frozen setup: EN57-947 stands at BottomInner (`5:13,9`) facing W, home BottomMainB; `barredArrivals` has `"5:13,9": "W"`.  Reverse EN57-947 on the throttle while idle: `flipFacing` -> S -> `moveOntoFacingCopy` -> `copyFacing(S)` finds no destination copy facing S and, since `8370abb1`, stands it on the barred copy.  Return Home now answers IMPOSSIBLE for every train, naming EN57-947 with the sentence above.  (Before `8370abb1` the same gesture left the train on the W copy and Return Home would have driven it the wrong way; the refusal is the better outcome.  The words are the defect.)

**The remedy it gives leads into AUT2-A1's door half** (AUT3-B2).  "Place it on a station": cut and paste it back onto BottomInner, or Place it there from the editor or the dialog, and each door records `facingAfterAPaste(placeableFacingsFor(...), S, ...)` - one placeable copy, so W - and stands it on the W copy, while its decoder was just reversed to drive S.  The next dispatch is then the wrong-way run `8370abb1` exists to prevent.  The real remedies are to reverse it back on the throttle, drive it off by hand (a hand send from a barred copy is allowed: `isPathClear`'s start rule is `isAutoRunning()`-only, `Layout.java:2438`), or lift the bar - and neither sentence names any of them.

(`error-must-have-a-remedy`: a fault whose stated remedy is wrong.  The load path's `warnLocomotivePlacedOnNonStation` names the train only and is fine.)

**Verification request (execution, core, no window).**  Live-snapshot fixture as above.  `stoodFacing(session, running, inner, Side.W)` at BottomInner, `session.placeLocomotive(inner, PROBE)`, `session.setFacing(inner, Side.W)`, `session.flipFacing(PROBE, running)`; give PROBE a home elsewhere (`running.setHomeLocomotive(<a free station Point's name>, PROBE)`).  Then `HomeStaging.snapshot(running).plan()`.  **Proves it:** outcome IMPOSSIBLE and `getReasons().get(probe)` contains *"BottomInner, which is not a station"*; `running.explainCannotStart(probe)` contains *"BottomInner (eastbound)"*; and `AutonomySession.facingAfterAPaste(session.placeableFacingsFor(inner, running), Side.S, null)` is W.  **Refutes it:** the train is not on a barred copy after the flip, or the sentence names the barred arrival.

**Suggested fix.**  A sentence of its own for a train on a copy of a station square that is not a destination, keyed by the square and the facing: *"{train} faces {S} at {BottomInner}, where trains may not arrive from the {W}: turn it round on the throttle, or drive it off by hand"* - for both `whyNotHome` and `explainCannotStart`, with `placeNameOf` kept.

### AUT3-B2 - AUT2-A1's door half, "kept ... and recorded for Adam", now contradicts the rule `8370abb1` adopted, and it was not recorded: the store row is Closed, and no open question names it

| | |
|---|---|
| **Disposition** | Open - Adam's decision: filed as OB-284 (9a9a8564), and AUT2-A1's row now says so |
| **Where** | `AutonomySession.java:7045` (`facingAfterAPaste`'s one-copy branch) fed `placeableFacingsFor` at `TrainControlUI.java:7263`, `:8183`, `GraphLocAssign.java:238`, `:287`, `AutonomyEditorPanel.java:5445`; `docs/manual-tests/findings.tsv` row AUT2-A1; `docs/reference/behaviour.md:637-639` |

**Restates AUT2-A1's second half** (*"The same substitution at the doors"*).  What is new: `8370abb1` kept that half on the ground that it is *"OB-270's 'no impossible facing is saved'"*, and in the same commit declared the facing it refuses a possible one.  `placementCopy`'s new comment: *"A barred arrival bars STOPPING there, not standing there: a train reversed on the throttle, or already standing when the side was barred, faces that way."*  So a facing only a barred copy holds is now built, captured (`captureFromLayout` writes it from the barred copy), followed from the throttle, offered and applied by the Facing menu (`facingChoices` = every copy) - and converted to the opposite facing by the three doors that place a train, which record the one placeable facing whatever the train's heading and stand it on that copy.

Two doors now answer one question two ways (*guard and affordance ask one question*).  Concretely, at BottomMainA on the frozen railway (E arrivals barred): the Facing menu turns 75 407 DB to face W and stands it on the barred copy; a cut and paste of the same train back onto BottomMainA - MT-368's gesture - then records E and stands it on the E copy (`cutFacing` = W from the running copy, `facingAfterAPaste({E-copy: E}, W, ...)` = E), with nothing said.  OB-270: *"no train should inadvertently change direction when pasted"*; MT-377: *"as long as the direction isnt flipped"*.  The decoder has not changed, so the model is now turned against it - the wrong-way dispatch again, by a door.

**And it is not in front of Adam.**  AUT2-A1's store row is `Closed` (its disposition field is truncated at *"The doors' one-placeable-copy"*), where every other decision waiting for him is `Open - Adam's decision` (AUT-C2, DCN-C3, REG2-C3, REG2-C7).  `open-questions.md`, `tests.md` and behaviour.md say nothing of it; behaviour.md:639 still states the door rule as settled (*"the heading is chosen and recorded over the copies a train may stand on, so no impossible facing is saved"*), in words `8370abb1`'s premise contradicts.

**Verification request.**  (1) No execution: `grep` the store and the two documents as above.  (2) Execution, core: live-snapshot fixture, `stoodFacing(E)` at BottomMainA, `session.placeLocomotive(mainA, PROBE)`, `session.setFacingAndMove(mainA, Side.W)` - PROBE on a W copy (the round-2 claim).  Then `AutonomySession.facingAfterAPaste(session.placeableFacingsFor(mainA, running), session.facingOf(PROBE, running), null)`.  **Proves it:** W before, E after.  A `ui` test driving the real cut and paste (`ui.testAPastedTrainFacesTheWayTheOperatorChose` style) would prove the copy too.

**Suggested disposition.**  Re-open AUT2-A1's door half as `Open - Adam's decision`: whether the placing doors should keep the heading where any copy faces it (the rule `placementCopy` and `copyFacing` now follow), or refuse/ask, rather than record the one placeable facing.  If the doors are changed, behaviour.md:639 changes with them.

## C - low

### AUT3-C1 - on Manual, Why Not Moving? answers "cannot be sent anywhere ... autonomy will not start it" for a train on a barred copy, while the right-click menu offers it hand sends from there

| | |
|---|---|
| **Disposition** | Fixed - 4be3798a (claim d65df6cb, red first) |
| **Where** | `AutonomyEditorPanel.java:7888-7893` (`explainCannotStart` asked before `byHand` is consulted); `AutoLocomotiveStatus.java:735`, `:897`; `Layout.java:4934-4948`; against `Layout.java:5779-5798` (`getPossiblePaths` - no start filter) and `:2438` (the start rule is `isAutoRunning()`-only) |

MT-434 split the editor's Why Not Moving? by tier: on Manual it gives *"the reasons a hand-driven send meets"* - *"what autonomy will not do is not the question a person sending a train has asked"* (`Layout.explainDestinationsGrouped(loc, byHand)`).  But `composeWhy` asks `explainCannotStart` first, for both tiers, and that answers `why.startNotStation` for any train on a copy that is not a destination.  On Manual the window then says *"EN57-947 cannot be sent anywhere: It is standing on BottomInner (eastbound), which is not a station, so autonomy will not start it from there"* - while `LayoutRightclickAutonomyMenu.gatherPathOptions` (`getPossiblePaths(loc, true)`, whose start loop has no `isDestination()` test) lists hand destinations for it, and `executePath` runs them (`isPathClear` refuses a non-station start only `if (this.isAutoRunning() ...)`).  The inactive-start line has the same shape against SPEC-B3's hand-send exemption.

Pre-existing (the window was tier-split after `explainCannotStart` was written); `8370abb1` makes the non-station half reachable again from the throttle and the Facing menu (AUT3-B1's scenario).  Explanation only - nothing is driven wrongly.  **Verification request (execution):** AUT3-B1's fixture after the flip; `running.getPossiblePaths(probe, true)` non-empty, and `running.explainCannotStart(probe)` non-null - the editor shows the second in place of the first on Manual.  **Suggested fix:** pass the tier to `explainCannotStart` and skip the two start rules a hand send does not have.

### AUT3-C2 - a home set for a train standing on a barred copy is saved with that copy's facing, which OB-282 calls impossible; `writeHome`'s "so no impossible facing is saved" was made false again by `8370abb1`, and the editor no longer asks

| | |
|---|---|
| **Disposition** | Fixed - 4be3798a (claim d65df6cb, red first) |
| **Where** | `AutonomySession.java:7410-7414` (`writeHome`: the comment and the unchecked `homeFacingOf` write), `:7444-7485` (`homeFacingOf` returns the running copy's facing, barred or not), `:7424` (`knownHomeFacing`); `AutonomyEditorPanel.java:5105` |

`writeHome` writes `homeFacingOf(tile, locomotive)` with no check, on the premise in its comment: *"Only the facing the train standing here has, which is a copy a train stands on, so no impossible facing is saved."*  That premise held between `1c855483` and `8370abb1`, when no train could stand on a barred copy.  Since `8370abb1` one can (the throttle, the Facing menu, the build).  So with 75 407 DB turned to W on BottomMainA's barred copy, Set Home at BottomMainA: `knownHomeFacing` = W, so the editor does not ask (TDY2-C4's new condition), and `setHome(tile, picked, null)` saves `homeFacing: W` - a facing `homeFacingsFor` excludes, and Adam's OB-282 ruling is *"we shouldn't allow an impossible facing to be saved"*.  The explicit-facing `setHome` overload does check `homeFacingsFor(tile).contains(facing)` (`:7360`); the implicit one does not.

Consequence is small, which is why C: `homeCopy` honours only an allowed facing, so the home lands on the E copy unfixed, and behaviour.md 6 already says *"a home with no facing - ... or a copy no train may arrive at - is still the square"*.  The file then carries a facing nobody chose and the rule excludes, and the comment is false.  **Verification request (execution):** the live-snapshot fixture, PROBE flipped onto BottomMainA's W copy as in AUT3-B2, then `session.setHome(mainA, PROBE, null)` and read `getPointProperty(mainA, "homeFacing")` (reflection) - `"W"` proves it.  **Suggested fix:** in `writeHome`, write `homeFacingOf` only when `homeFacingsFor(tile)` contains it (the overload's own test), and correct the comment.

### AUT3-C3 - the tail walk's first hop takes the first ARRIVING rail by name, which at a may-turn neighbour is its turning twin whenever that sorts first; the walk cannot go on from a turning copy, so the tail stops at that square without claiming it.  The round-2 fork-rule change copied the selection, though on a built graph it cannot fire

| | |
|---|---|
| **Disposition** | Open - for a follow-up: older than this review; the tail walk's first arriving rail is chosen by name |
| **Where** | `Layout.java:7275-7340` (the side rule, `if (candidate.getEnd() == here) { segment = candidate; break; }` at `:7326`), `:7421-7434` (`8370abb1`'s swap, same selection), `:2261` (`incoming.sort(... getStart().getName())`) |

**Mostly pre-existing (SVZ-B1's side rule); found while checking the TDY2-C5 swap, which the task asked about.**  Rails arriving at a standing copy D from a neighbour N come from the copies of N that face D: N's plain copy that arrived from the far side, and - where N may turn - N's turning copy that arrived from D's side.  `getNeighborsAndIncoming` lists incoming rails sorted by START NAME, and the rule takes the first matching one.  A plain copy is named by its travel heading, its turning twin by the opposite heading plus ", reverse", so the twin sorts first whenever the plain heading is southbound or westbound ("northbound, reverse" < "southbound").  From a turning copy every rail leads back to D's side, so at the next hop there is no way on (`neighbours` are all on D's side: a fork or nothing) and N's own square - which is not a place on a rail STARTING at N - is never claimed.

Measured on the blessed build (`test/baseline/configuration.json`, Python): with a recorded side, `BottomMainA (westbound)` and `BottomMainB (westbound)` arriving from E take `BottomMainPost (northbound, reverse)` although `BottomMainPost (southbound)` - the copy the train drove through - offers the same rail.  From the twin the road (`arrivedAlong`, MT-335's "follow its last route") cannot be followed north, because `cameFromAlong`'s Point is not a neighbour of the twin.  So a train that came down from the north through BottomMainPost and stands at BottomMainB, longer than the approach, claims nothing over BottomMainPost's square or north of it; from the plain copy the walk would claim BottomMainPost's square first and follow the road.  On the frozen live railway BottomMainPost may also turn, so EN57-947 coming home to BottomMainB from the north is this case.  Every route south through BottomMainPost still crosses the claimed approach, so what is lost is the orange shading beyond BottomMainPost (the MT-335 symptom) and the square itself - under-claiming, the permitting side, narrow.

**The TDY2-C5 swap** (`:7421-7434`) takes the first arriving rail from the neighbour's PLACE by the same order.  On a built graph it cannot fire: the fork rule counts neighbours by lane (`placeKey`, TLW-B1), and any neighbour joined both ways gives two lanes (its arrived-from-D lane and its facing-D plain lane), so the walk breaks before the swap.  Measured: of the 21 Points on the blessed build whose first hop has one neighbour, the swap changes none.  The only rail it could take on a built graph is a turning twin's (the one arrival from the arrived-from-D lane), which is this finding's landing again.

**Verification request (execution, deterministic).**  A hand-built graph in `testATrainCoversTheTrackBehindIt`'s style with blocks: square N with copies `"N (southbound)"` (block `"n"`, plain, rails to D and from M) and `"N (northbound, reverse)"` (block `"n"`, rail to D only), D a destination fed by both, M beyond N; places ending with the end square, lengths so a 5-unit train at D reaches past N.  Stand the train on D with `arrivedFrom` the N side and `arrivedAlong` = [M->N (southbound), N (southbound)->D].  **Proves it:** `placesCoveredByStandingTrains()` lacks N's square and M's rail.  **Control:** rename the twin to sort after the plain copy (e.g. `"N (zz, reverse)"`) - both claimed.  **Suggested fix:** among arriving rails from one place, prefer a start that is not terminus/reversing (the plain-first rule `copyFacing` and `placementCopy` use).

## D - not defects (checked and sound)

### AUT3-D1 - TDY2-A1 / GUI2-A1 / AUT2-A1 (the build, the throttle and the Facing menu keep the copy facing the record) verified

| | |
|---|---|
| **Disposition** | Closed - checked clean |

`placementCopy` now tries the allowed copies facing the record (plain first), then any copy facing it (plain first), and only then `startableCopy`; it never returns a copy facing another way while one faces the recorded way.  `copyFacing` does the same over `placeableFacingsFor` then `facingsFor`.  The two agree on "plain first": `placementCopy` asks `node.reverse`, `copyFacing` asks `!isTerminus() && !isReversing()`, and every turning copy is emitted with one of those flags (`AutonomyBuilder.java:1165-1172`, `stops ? "terminus" : "reversing"`), barred or not.  The blessed baseline is byte for byte what it was before `1c855483` (`git diff 1c855483^ 2f4448b6 -- test/baseline/` is empty).  The guard and the affordance now ask one question: `facingChoices` (Facing menu, `flipFacing`, `facingsThatCannotBeHeld`) lists every copy's facing, and `copyFacing` can now stand the train on any of them.  The claims in `8346be65` are red on their parent for the right reason: `testTheBuildKeepsTheFacingTheSetupRecords` asserts `facingsFor(mainA).get(standing) == W` - the variable AUT2-A1 asked for, where the old claim asserted `isDestination()`; the throttle and menu claims assert the facing of the copy PROBE stands on and that it moved.  (What the throttle claim does not reach is AUT3-A1.)

### AUT3-D2 - the runtime meets a train on a copy that is not a destination, and handles it everywhere the task named

| | |
|---|---|
| **Disposition** | Closed - checked clean |

- **Autonomy dispatch:** `getPossiblePaths(loc)`'s start loop requires `start.isDestination()` (`Layout.java:4740`); `isPathClear` refuses a non-station start while auto-running (`:2438`, `errorStartWithNonStation`).  The train is never dispatched; `explainCannotStart` says so (AUT3-B1 for the words).
- **Hand sends:** allowed from such a copy, as before `1c855483` (AUT3-C1 for the why-not).
- **Return Home:** `snapshot` records every occupied Point; the pre-scan makes a homed, not-at-home train on such a copy IMPOSSIBLE (`HomeStaging.java:550-559`), and `firstClearRoute` refuses it as an origin (`:1107`), so a homeless one is an immovable obstacle to the search - consistent (AUT3-B1 for the words).  `atHome` counts it home when its home carries no fixed facing, which is the "square" rule.
- **The idle drain:** unchanged in practice.  It writes the arrival side; a train turned at its destination arrived on a destination copy, and the turning copy of that same arrival shares its `arrivalAllowed`, so `copyFacing`'s first loop still answers and the new fallback is not reached.
- **`captureFromLayout`:** `facingByName` covers barred copies, so it writes the barred copy's facing, and `placementCopy` now honours it - a stable round trip (before `8370abb1` the next build turned it round).
- **Rebuilds (`putTheTrainsBack`):** the build puts the train on the barred copy by the setup's facing, so `back.getCurrentLocomotive() == train` and `moveLocomotive` (which refuses non-destinations) is not asked.  Adding a bar under a standing train now keeps it facing its way instead of turning it and then failing to put it back.
- **The tail:** `moveOntoFacingCopy` carries `arrivedFrom`/`arrivedAlong` onto the barred copy; the side rule then takes the outgoing rail on that side as its fallback, and the AUT-C3 read spends the square - the tail lies on the right side.

### AUT3-D3 - GUI2-B1 verified

| | |
|---|---|
| **Disposition** | Closed - checked clean |

Both early `return 0`s are `startableCopy(nodes)`, the same two loops `homeCopy` falls back to.  At BottomMainA it gives the E copy, at BottomMainPost the S-arrival plain copy (N arrivals barred), both destinations.  The claim `testATrainWithNoFacingIsPutWhereItCanStart` is red on `8346be65` (copy 0 is the barred one at both, by the N,E,S,W emission order) and `session.setFacing(square, null)` removes the key (`setPointProperty` with null), so it reaches the branch.

### AUT3-D4 - AUT2-C1 / TDY2-C6 verified: no claim can outlive its path

| | |
|---|---|
| **Disposition** | Closed - checked clean |

Every exit of `configureAndLockPath` walked: the in-monitor underway refusal and the `isPathClear` refusal return before `takingPath.put`; a throw from the lock loop is caught, released, `takingPath.remove(loc)`, rethrown; `configureFailed` and the validation failure each remove before returning false; a throw from `handleMisconfiguredPath` or `validatePathActuation` reaches `executePath`'s `catch (RuntimeException)`, which removes the claim (`:7949`).  So the removed caller line had nothing of its own to remove, as its new comment says.  The claim `testARefusedDuplicateLeavesTheWinnersClaim` is red on `8346be65` for the right reason: nothing in `executePath`/`executePathInternal` before `configureAndLockPath` takes the Layout monitor (`isValid`, `isCurrentLayout`, `isAlreadyUnderway` are unsynchronized), so both threads park in its `synchronized (this)`; the winner then parks on `activeLocomotives` holding its claim, and the loser is refused inside - the window AUT2-C1 described.  One residue, unreachable: `executePath`'s handler does `takingPath.remove(loc)` and `activeLocomotives.remove(loc)` unconditionally, so a LOSING duplicate that threw would clear the winner; no statement between the outer check and the in-monitor refusal can throw.

### AUT3-D5 - TDY2-C5's claim is red for the right reason; the swap does not change a built graph's tails, and its measurement follows the rail the side rule already reads

| | |
|---|---|
| **Disposition** | Closed - checked clean |

The claim `testATrainOnASquareEmittedOnceSpendsItFirst` builds a blockless square with an arriving and a leaving rail to one neighbour; before `8370abb1` the fork rule took the leaving rail (outgoing listed first) and claimed `t1` without the square.  On a built graph the swap cannot fire (AUT3-C3: two lanes for any neighbour joined both ways; 0 of 21 on the blessed build), so TDY2-C5's "dead-end terminus is the ordinary case" does not reach it - the walk breaks at the fork first, as before.  **The measurement rule:** where the swap does fire, `segment.getLength()` becomes the arriving rail's (its track plus the standing square) instead of the leaving rail's (track plus the neighbour's square).  That is the rail and the length a train with a recorded side already gets (SVZ-B1), so a no-side train is now measured as a sided one is.  The one outcome that changes is an unmeasured standing square and approach behind a measured neighbour: it now claims nothing, where it used to claim the approach and the neighbour - the measurement rule's own answer (*"if no length specified, just stop there"*).  On a hand-written graph with no places the charge is the arriving edge's own length; the two directions carrying different lengths would change the charge, which only a hand-built fixture can do.  **Two rails to one neighbour:** the fork counts lanes, so two rails reach the swap only from one lane, i.e. a plain copy and its turning twin - AUT3-C3's landing.

### AUT3-D6 - TDY2-C4 verified

| | |
|---|---|
| **Disposition** | Closed - checked clean |

`homeFacingOf` returns the running copy's facing where the railway has the train on this square, null where it has it anywhere else, and falls back to the setup only when the railway does not know the train.  The editor asks exactly when `knownHomeFacing` is null.  The claim `testATrainThatHasDrivenOffGivesNoFacing` is red on `9c85db2a` (the setup's E answered) and asserts the method the door now asks.  The door's condition change itself has no claim, but it reads one call.  (AUT3-C2 is the barred-copy case this answers without asking.)

### AUT3-D7 - AUT2-C3's comments verified, and the rest of the range

| | |
|---|---|
| **Disposition** | Closed - checked clean |

- `4132d260`: the three `Layout` sites now say `unlockPath` reads `releasedEarly`; `testAutoLayout`'s javadoc, MUTATION note and failure message name `releasedEarly`; the identity loop is described as seldom matching on a built graph (on the blessed build the two directions of a rail run between different copies, e.g. `TunnelLeftPark -> BottomMainAPre (eastbound)` against `BottomMainAPre (westbound) -> TunnelLeftPark`).  Cosmetic: `testAutoLayout.java:2253` reads *"again.The comment"* (a space lost).
- `8370abb1`'s `configureAndLockPath` and `executePathInternal` comments state what the code does (AUT3-D4).
- REG2-C2 (`whyAutonomyWillNotStart`): answers `isRemoteLayout()` first, as `canStartAutonomy` (`:24526`) and `refuseAutonomyStartWhileBroken` (`:5931`) do.
- `264f2a73` (a test's premise), `b53439dc` (tracker), `91daff27` (translations) and `2f4448b6` (the catalogue) change no autonomy behaviour; `2f4448b6`'s AUT2 dispositions are truthful except AUT2-A1's "recorded for Adam" (AUT3-B2).  Known and deferred items (AUT2-C2, GUI2-C2, GUI2-C4, REG2-C3, REG2-C7, AUT-C2, DCN-C3) were not re-examined.

## What this pass did not cover

- **Nothing was run.**  AUT3-A1, B1, C1-C3 each carry an execution request; AUT3-B2's record half is settled from the store and the documents.
- The GUI side of the placing doors beyond the facing they record (`GraphLocAssign`'s re-pointed `edit.p`, the right-click menu's own placement door at `LayoutRightclickAutonomyMenu.java:1245`, FacingPrompt), and `MarklinControlStation` (REG2-C6).
- Whether two different roads between the same two squares (a crossover pair) are counted as one lane by `placeKey`, and so walked as one way back rather than a fork - noticed, not traced.
- The frozen live railway has no committed build output, so AUT3-C3's and D5's measurements are on the blessed baseline's build; the live railway's copies at BottomMainPost were inferred from its setup (may turn, N barred), not read from a build.
