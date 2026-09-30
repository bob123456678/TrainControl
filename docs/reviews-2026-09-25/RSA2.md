# RSA2 - Release validation, round 16 and the autonomy graph: a feedback double curve is two places on one sensor, so one arrival ends two trains' journeys and hands back the route of the one that has not arrived; round 16's stop holds where it was measured and leaves a Return Home, two windows and an unclaimed wake (TrainControl 3.0.0)

**Status:** open

**Prefix:** RSA2

**Reviewed:** branch `autonomy-diagram-r0` at `6e0bc2b8`, read and run from `git archive 6e0bc2b8` unpacked into `validate30/rsa2/a6e0/` (reading, and the baseline), a copy of it, `validate30/rsa2/p1/`, with one scratch probe class added (`test/regression/rsa2Probes.java`, not in the battery), and a mutant copy, `validate30/rsa2/p2/` (p1 with the one mutation M1 below); `cs2_sample_layout` excluded and an empty folder of that name made for the runner; `LocDB.data` and `UIState.data` beside each root are copies from TC30.  TC30's working tree and the main checkout's were never read; git was used only as `git -C tc-30 log / show / diff / blame / archive`.  Round 16 is `375b542a..6e0bc2b8` (four commits: the claims `02bbd438`, the fix `e17e7062`, the records `4db0fddb` and `6e0bc2b8`); the release scope is `c469b81c..6e0bc2b8`.  2026-09-29.

## Method

**Round 16, read whole:** the brief; `validate30/RSA.md`; the round's source diff (`Layout`, `Locomotive`, `AutoLocomotiveStatus`, `LayoutRightclickAutonomyMenu`, `TrainControlUI`, `MarklinRoute`) and each changed method in its context - `stopEveryTrainWhereItIs`, `drive`, `giveBackUnrun`, `stopLocomotives`, `runLocomotives`, `runLocomotive`, `executePath` and `executePathInternal` whole (every speed write, both waits, the turn, the early release, the fence, the arrival, the re-stand), `executeTimetable(Internal)` with its entry threads and completion wait, `loadReturnToHomeTimetable`, the Layout constructor, `retireEveryLayout`, `makeCurrent`, `isCurrentLayout`, `configureAndLockPath`, `handleMisconfiguredPath`, `unlockPath`, `standOnTheCopyItDidNotTurnOn`, `clearLocomotiveExcept`; `Locomotive`'s five waits, `wakeEveryWait`, `setSpeed`/`_setSpeed`, `Feedback._setState`; `MarklinControlStation.parseAuto`, `clearAutoLayout`, `getAutoLayout`, `isAutonomyRunning`, `stop`; `MarklinRoute`'s stop; in the window `prepareAutonomyReload`, `unloadAutonomy`, `autonomySetupDeleted` and both delete doors, `requestReturnToHome` whole, `startAutonomyActionPerformed`, `executeTimetableActionPerformed`, `gracefulStopActionPerformed`, `sendATrainByHand`, `whyNoTrainMayBeSent`, `refuseWhileAutonomyRunning`, `promptTrainLength`, `applyTrainLength`, `checkAutoLayoutLatency`, `emergencyStopTriggered`; `AutonomyViewerPanel.load` and `loadPrepared`; `AutoLocomotiveStatus.updateState`, `findPaths` and the double-click; `LayoutRightclickAutonomyMenu.showFor`, `gatherPathOptions`, `destinationItem`, the Edit Locomotive item; `GraphLocAssign.commitChanges` and its two doors; every writer of `setTrainLength`, `setReversible`, `setPreferredSpeed`, `setArrivalFunc`/`setDepartureFunc`; the round's claims in `testATrainIsDispatchedOnce` and `testNoSetupEditDuringARun`, and the dispositions in `docs/reviews-2026-09-25/RSA.md`.

**The graph, read whole or nearly so:** `AutonomyBuilder` (`nodesFor`, `splitSides`, `onwardFrom`, `blockFor`, `Node.leavesBy`/`arrivesBy`, `placementCopy`, `startableCopy`, `homeCopy`, `facingOf`, and `build`'s point, edge, place and lock-edge emission); `GraphReducer` (`reduce`, `buildPoints`, `walkEdges`/`continueWalk`, `collectCommands`, `deriveLocks`, `locationsOf`, `placesAlong`, `roomAfterTheLastSwitch`, `unmeasuredAfterTheLastSwitch`); `TilePorts`' port table, `deriveToe` and the helpers; `TileGraph`'s `RouteId`, `exits`, `landing`, `defaultDirection`, `directionAllows`, `transparentRoutes`, `validatePortals`, `getFeedbackTiles`; `AutonomyChecks`' station, isolated-point, closed-run and repeated-sensor checks and `AutonomySession.repeatedSensorPages`, `departableFacingsFor`, `copyFacing`; `Edge`'s occupancy, lock and run-over counts; `Point`'s `setLocomotive`, `reserve`, `assign`, `isSamePlaceAs`, `getBlockLocomotive` and `Layout.locomotiveInBlock`; `isPathClear` whole; `walkStandingTrains`, `theHeadOfTheRun`, `whatItHasDriven`, `walkOneTail`, `anotherTailOn`, `tailLiesOn`; the length rules' bodies - `whyTooLongForThisRoute`, `measuredRoomAtTheEndOf`, `whyABerthCannotHoldIt`, both `whyItWouldMeetItsOwnTail`s and `bodyOfATrainAt`; behaviour.md 5c (the blocks paragraph), 5d, 6a, 7a and section 8's load and Unload paragraph; the live snapshot's page list and setup (`test/layouts/live-snapshot/config`).

**Read lightly:** `StationIndex` and `TileAnnotation` (their outlines: the translation between squares and Points for the interface, and the editor's drawing - neither is read by the runtime's safety rules); `HomeStaging` only where it meets the runtime (`loadReturnToHomeTimetable` and the sequential timetable it hands over); the first-hop rules of `walkOneTail` (read, not re-derived case by case - fourteen rounds went over them).  **Not reached:** `HomeStaging`'s search itself; the rest of `AutonomySession` (10,562 lines) and `AutonomyEditorPanel`; `TileGraph`'s checks beyond portals and route tiles; `AutonomyCompanionStore`; import, export and the configurations' files; the timetable UI and the route editor beyond the stop; `CS2File`.  Every finding was searched for in `docs/manual-tests/findings.tsv` (4,869 rows) before it was written.

**Runs.**  Every JVM through the archive's own `docs/tools/one.sh`, `TC_SCRATCH` a Windows-form folder per job under `rsa2/scratch`, started by a queue (`rsa2/runjob.sh`) only once no `java.exe`, `javaw.exe` or `javac.exe` had been seen and no battery lock existed for two checks 20 s apart, deciding from that attempt's own log (no retry was needed).  The coordinator's battery held the machine until 06:33; everything below ran 06:33-06:36, one job at a time.

- **Baseline** (a6e0): `core.testATrainIsDispatchedOnce` 22 run, 0 failures; and the round's claims `core.testTrainTailClearsEdges.testALengthChangedDuringARunDoesNotShortenWhatTheTrainHolds`, `core.testTheArrivalHonoursTheAnswer.testTheReStandNeverShowsTheSquareEmpty`, `core.testAStopRouteStandsAlone.testARoutesStopIsSentWhenThePowerAlreadyReadsOff`, 3 run, 0 failures.
- **Probes** (p1, `regression.rsa2Probes`, 5 run, 0 failures - they assert nothing, and each writes what it saw to `p1/rsa2-out/`; simulated network, feedback set by the probe): P1 `rsa2ProbeOneSensorTwoPlaces` (RSA2-B1), P2 `rsa2ProbeTheYesBetweenTheRunningCheckAndTheCount` (RSA2-C2), P3 `rsa2ProbeReturnHomeAfterTheYes` (RSA2-C1), P4 `rsa2ProbeAWholeSquareOffersBothWaysOut` (RSA2-C7), P5 `rsa2ProbeUnloadsRetirementEndsAnUntimedWait` (RSA2-C4's control).  P1 and P4 build their railways through `AutonomySession` from pages made in the probe, in session folders inside p1.
- **Mutant M1** (p2: `Locomotive.wakeEveryWait()` removed from `Layout.retireEveryLayout` and nowhere else): the round's RSA-B1 claim `core.testATrainIsDispatchedOnce.testARetiredRailwaysJourneyEndsWithItsRailway` and P5, 2 run, 0 failures - the claim green on the mutant, and P5 reporting the journey still waiting (RSA2-C4).

**Nothing written** outside `validate30/rsa2/` and this file.  The runner takes the shared lock under `%TEMP%` and a preference node of its own, and removes both; two nodes under `HKCU\Software\JavaSoft\Prefs\traincontrol-test-runs` exist (`battery-680`, `one-620`) - the two RSA found; not mine, not touched.  No git state changed; no commit.  The real `LocDB.data` and `UIState.data` - the main checkout's and `C:/Users/adamo`'s - were copied and hashed at 05:31, before the first JVM, and compared byte for byte after the last: unchanged (`3f54de3a...`, `3604bf5f...`, `6ef56939...`, `9e32894e...`).  Nothing of mine is running: every queue and waiter has ended, no `java.exe`, `javaw.exe` or `javac.exe` is left, and the battery lock is gone.

**Counts:** 0 A, 1 B, 8 C, 9 D.

**Round 16's fixes are right where they were measured** (RSA2-D1 to D4): the Yes is counted before anything else, every speed a journey writes asks it, a claim completed after it is given back exactly as a misconfigured one is, a retirement wakes every sensor wait and a journey's code after a given-up wait is fenced, the length a journey hands track back by is the one it was dispatched with and no door writes one while trains run, and the re-stand, the route's stop, the volatile flag and the gathered railway are as the dispositions say.  **What they leave:** a Return Home that was planning when the Yes came runs its plan when the load is then refused (RSA2-C1, measured); the count is read after the `running` question it must overrule, and one wait - the turn's resume - is untimed and woken by nothing (RSA2-C2, the first measured); the Auto tab's new refusal is silent under a prompt to double-click (RSA2-C3); and Unload's wake is in the code and in no claim - the claim passes with it removed (RSA2-C4, measured).  **The graph keeps two trains apart by shared intermediate tiles, the runtime's same-rail check by place and the blocks of split squares, and that holds** (RSA2-D5, D6) - **except at the one tile that is two places on one sensor**: since OB-238 a feedback double curve's two arcs are two blocks, so two trains are sent to them at once and the first arrival ends both journeys (RSA2-B1, measured); and a square nothing arrives at is built with no facing at all, so a train standing on it is offered both ways out (RSA2-C7, measured).  **New in round 16:** C3, C4, C8.  **New since v2.7.4:** B1, C1, C2, C5, C7.  **Older:** C6.

---

### RSA2-B1 - A feedback double curve is two places on one sensor: two trains are sent to its two arcs at once, the first arrival ends both journeys, and the second is recorded as arrived with its route handed back while it is still on it

| | |
|---|---|
| **Disposition** | Fixed - option (b), agreed by Adam (2026-09-29: "For the sensors, that is OK as long as non-atomic rules are respected (it should be allowed once unlocked)."): a route may not end at, or pass, a sensor another train's journey is still waiting on (Layout.whoseJourneyAwaits in isPathClear, refused in the words of autolayout.errorFeedbackAwaitedByAnotherTrain).  A journey waits on every sensor of its route it has not reached, and one still claiming its route on all of them, so the first arrival can no longer end another train's journey; a feedback double curve's two arcs stay two pieces of metal (Adam, OB-238) and two places, and the same holds for any sensor two places share.  Once a journey has passed a sensor it waits on it no more, and the other place on it is offered again, the lock and release rules deciding the track as before (claim 3a8ff7c0).  claims 02c4d0bc (red first), fix 4824d40c; mutations MB1 red, MB2 red. |
| **Grade** | B.  The consequence is A's - a switch or a crossing under a train, and the train's tail left unclaimed - and the reach is a `FEEDBACK_DOUBLE_CURVE` (a CS2 `s88doppelbogen`) with track on both arcs on a page autonomy uses.  The live snapshot has three, on `3 - Top Parking`, `4 - Combined` and `5 - Test`, all excluded by its setup, so not on Adam's railway today; any diagram can carry one, and the build accepts it with no finding.  **How:** OB-238 (`470079a3`, 2026-09-22) made `blockFor` group the copies of `DOUBLE_CURVE`, `FEEDBACK_DOUBLE_CURVE` and `OVERPASS` per road.  For the two plain types that is right - two roads, two pieces of metal, no sensor.  The feedback type is one Point per arrival side on ONE s88: it is now two places sharing one sensor, which is the thing the model says it cannot tell apart (*"nothing downstream can say which of them a train is standing on"*, OB-150).  At that square two trains are kept apart by the block (`Edge.isOccupied` asks `getBlockLocomotive`) and by the end sensor at dispatch (`isPathClear`: feedback not clear) and by nothing else - the square is an endpoint, in no edge's locations, so no lock edge covers it.  A train sent to one arc reserves that arc's block; a second train's route to the other arc finds its block free and the sensor clear, because the first has not arrived, and is admitted.  Both arcs' copies are stations wherever the square is (`station` is per tile), so autonomy can make this choice by itself.  Both journeys then wait on the same s88, and the first arrival ends both waits: the second train's thread commands it to 0 wherever it is on its last leg, records it on its arc, and unlocks its path - so its switches and crossings are free for any other train while it stands on them, and its tail is walked back from a square it has not reached.  At an intermediate point the same event moves a passing train's journey on a section ahead of the train.  **The ruling this turns on:** behaviour.md 5c calls the per-tile grouping *"the refusing direction"* - true of the metal, but for the feedback variant the tile's one block was also what kept two trains off one sensor.  And `GraphReducer`'s own record of the removed double-curve warning says Adam asked for *"a specific failure scenario"* and was given none; this is one, whichever way the contact is wired (on one arc only, the older case, a train sent to the other never sees its sensor at all).  **New since v2.7.4** (OB-238's per-road block for the feedback type).  The sensor shared between different tiles that RSA-D4 accepted as older is the hand-written form, and `repeatedSensorPages` reports only a sensor repeated across pages; this one the build makes from a single square. |
| **Names** | OB-238, IND9X-C9, AUR-B1, OP2-D6, RSA-D4, OB-150, TCX-D7 |
| **Where** | `AutonomyBuilder.java:525-550` (`blockFor`: `neverMeet` includes `FEEDBACK_DOUBLE_CURVE`), `:1009-1026` (every copy a station, one `s88`, a block per road); `GraphReducer.java:902-955` (one Point per feedback tile), `:918-939` (the removed warning and the ruling), `:1498-1516` (a Point tile is no location); `Layout.java:2780-2846` (the per-edge checks: the block, the end sensor), `:9394-9425` (the destination wait, then `setSpeed(0)`), `:9657-9664` (the arrival's unlock); `Point.java:980-990` (`getBlockLocomotive`); `Locomotive.java:814-903` (the wait, keyed by sensor); `AutonomySession.java:10496-10560` (repeated sensors: across pages only); behaviour.md 1246-1257 |
| **Needs execution** | done - probe `rsa2ProbeOneSensorTwoPlaces`: a page with a feedback double curve D (s88 40, arcs N-W and E-S), X approaching its north arm from a sensor at (3,1), Y approaching its east arm from a sensor at (5,3) across a crossing at (4,3), and a third road north-south over that crossing; built through `AutonomySession` with every sensor a station, loaded by `parseAuto`, the two trains sent by `executePath` as a door sends them. |

| step | what the railway says |
|---|---|
| the build | four Points on s88 40, all stations: `main 3,3 (southbound)` and `(eastbound)` in block `main:3,3/N`, `(westbound)` and `(northbound)` in block `main:3,3/E` |
| X sent to D's north arm | under way; Y's road to D's east arm **clear** while X runs to the north arm |
| Y sent | under way; Y's rail run over and held; the crossing road over (4,3) **refused** (*"Edge is occupied"*) |
| sensor 40 occupied once - X reaching its arm | X's journey ended at `main 3,3 (southbound)`; **Y's journey ended too**: speed 0, recorded at `main 3,3 (westbound)`, thread gone, its rail neither run over nor held - Y never having moved in the probe; **the crossing road over the square Y's approach runs through reads clear** |

**Would a test have caught it?**  No.  `core.testAutonomyDiagramSession.testEachArcOfADoubleCurveIsItsOwnPieceOfMetal` asserts the two blocks, which is the change; no claim sends two trains to the two arcs.  The claim that would: the probe's sequence, asserting that the second train's journey is still under way after the first arrives - or that its dispatch was refused.

**Direction:** Adam to choose.  (a) Group `FEEDBACK_DOUBLE_CURVE` by the tile again - the one sensor is the reason the refusal was right there - keeping per-road places for locks and tails; or (b) refuse a dispatch whose route ends at or passes a sensor another train's claimed route is waiting on, whichever place it is - the general form, which also covers the sensor repeated within a page.

---

### RSA2-C1 - Return Home carries on after the reload's Yes: where the load is then refused, the plan it was making runs, after the operator was told the run would be abandoned

| | |
|---|---|
| **Disposition** | Fixed - Return Home counts the stops at the press and runs its plan with them (Layout.executeTimetable(int)), and a timetable asks the count under the lock the Yes now takes to clear running and count its stop, so a Yes while it plans starts nothing, whether the load then succeeds or is refused.  claims 02c4d0bc (red first), fix 4824d40c; mutations MC1 red, MC1h red. |
| **Grade** | C.  Return Home plans on a worker with nothing yet moving - it will not start while anything is busy - so during its planning the railway is busy only by the staging flag.  A load chosen then asks the Yes (*"Locomotives are still running.  Reloading will stop them and abandon the current run.  Continue?"*), and `stopEveryTrainWhereItIs` counts a stop and clears `running`; there is no train under way to stop.  If the load succeeds the railway is retired, and the worker's `executeTimetable` on it dispatches nothing (RSA-D7).  If the load is refused - the configuration has blocking problems, or the one running cannot be used with an edit that waits (RLV7-C4), or a carry fails - nothing is retired: the worker finishes its plan, asks its gate on the event thread (after the load's event, or inside the refusal's own dialog), and calls `executeTimetable`, which sets `running` again, and every leg reads the stop count at its own dispatch, after the Yes.  `loadReturnToHomeTimetable` refuses only a railway `isRunning()`, which is false.  So trains start moving after a Yes that said the run was abandoned and a load that was refused.  The disposition of RSA-C1 says *"A load refused after the Yes leaves every train stopped ... and none given its speed back"*: true of autonomy and of a timetable run, whose cleared `running` nothing sets again; not of this.  No two trains meet - every leg passes `isPathClear` - and the operator did press Return Home; a C, as RSA-C1 was.  **New since v2.7.4** (the stop at the Yes, `5fd1a54f`); the round's claims do not reach it. |
| **Names** | RSA-C1, RSA-D7, RLV6-B1, RLV7-C4, OB-073 |
| **Where** | `TrainControlUI.java:4373-4404` (`prepareAutonomyReload`), `:24607-24839` (`requestReturnToHome`: planning `:24699`, the gate `:24721-24739`, `executeTimetable` `:24761`); `AutonomyViewerPanel.java:923-946` (a refused load: message, `revert`); `Layout.java:2170-2177` (`stopEveryTrainWhereItIs`), `:6616-6630` (`executeTimetableInternal` sets `running`), `:6716` (each entry dispatched while `running`), `:8859` (the count read at dispatch), `:12112-12123` (`loadReturnToHomeTimetable`: refuses only `isRunning()`) |
| **Needs execution** | done - probe `rsa2ProbeReturnHomeAfterTheYes`, the worker's model calls replayed on a current railway: the staging flag raised as the press raises it, the Yes (`stopEveryTrainWhereItIs`), no retirement, then `loadReturnToHomeTimetable` and `executeTimetable`.  After the Yes: `autoRunning=false`, `isRunning=false`, still current.  Then: plan possible, **the train driven after the Yes** (speed 15, under way).  The event-thread half - the gate running after a refused load - by reading. |

**Direction:** let the Yes end a staging flow as it ends autonomy's choosing - for instance, Return Home's worker reads the stop count at the press and gives up before running its plan where it has changed (or the legs are dispatched with that count, so the first `drive` refuses).

---

### RSA2-C2 - The Yes's stop count is read after the question it has to overrule, and a turn's resume waits, untimed and unwoken, for a speed only the next driver will give

| | |
|---|---|
| **Disposition** | Fixed - every journey carries the stops counted where it was chosen: autonomy reads the count before its choice, a hand send at the click, a timetable at its start, Return Home at the press (executePath's stopsAtChoice; -1 reads it at dispatch); a journey chosen before a stop sends nothing and says so.  The Yes clears running before it counts, under the lock, so a thread that reads the new count reads running cleared.  A turn's resume wait gives up on a stop or a retired railway (waitForSpeedAtOrAbove with a give-up condition) and a retirement wakes the speed waits too.  claims 02c4d0bc (red first), fix 4824d40c; mutations MC2e red, MC2s red, MC2a red, MC2h red, MC2w red, MC2n red, MC2r red.  The Yes's order is by reading: no fixture lands a thread between its two statements. |
| **Grade** | C.  **(a) The count after the check.**  Autonomy's thread asks `running` once its choice is made, and `executePathInternal` reads the stop count as its first line - a few instructions and a call later, not in the same moment.  The Yes counts first and clears `running` second, so a thread that has read `running` true and reads the count after the increment dispatches with the new count and obeys nothing: its departure, next leg and pre-arrival speed are all written.  The same between a timetable entry's `while (running && !executePath(...))` and the count, for a timetable or Return Home leg.  The window is nanoseconds unless the thread is descheduled there; then the train runs until the load retires the railway and the wake stops it mid-section (beyond where the fold wrote it - RLV7-C1's gap), or, with the load refused, to its destination.  The disposition's *"autonomy's thread sends nothing it was choosing when the Yes came"* is true of a thread still choosing, which the claim holds in `pickPath`.  **(b) The turn's resume.**  `if (drive(loc, resume, ...)) loc.waitForSpeedAtOrAbove(resume)`: a Yes landing after `drive`'s second look and before the wait reads the speed leaves the wait false.  `waitForSpeedAtOrAbove` is untimed and waits on `speedMonitor`, which `wakeEveryWait` does not notify, so the thread outlives the retirement until the locomotive's speed next reaches `resume` - the next railway, or the operator, driving it - and then runs to the fence and commands it to 0: RSA-B1's defect by the one wait the round did not reach.  **New since v2.7.4**; (b)'s wait is older, and round 16 fenced its write but not the wait. |
| **Names** | RSA-C1, RSA-B1, GS-C1, RLV7-C1 |
| **Where** | `Layout.java:4941-4957` (`runLocomotive`: `running` asked after `pickPath`, then `executePath`), `:8603-8644` (`executePath`'s preamble), `:8859` (the count read), `:6716` (a timetable entry), `:2170-2177` (the Yes: count, then `running`), `:9207-9211` (the resume and its wait); `Locomotive.java:664-692` (`waitForSpeedAtOrAbove`: untimed, `speedMonitor`), `:792-798` (`wakeEveryWait`: `monitor` only) |
| **Needs execution** | (a) done - probe `rsa2ProbeTheYesBetweenTheRunningCheckAndTheCount`: a railway whose `executePath` holds autonomy's thread once, after it has asked `running` and before the count is read (standing in for a thread descheduled there).  Held: speed 0, not under way, `autoRunning=true`.  The Yes: `autoRunning=false`.  Released: **the train set off after the Yes** (speed 15, under way).  (b) by reading - no fixture lands a stop in that instant, as the disposition says of `drive`'s second look. |

**Direction:** read the count where the choice begins - before `pickPath`, at the head of a timetable entry's loop, at a hand door's click - and hand it to `executePath`; and let the resume's wait give up on a changed count or a retired railway (timed, or `speedMonitor` notified at the Yes and at every retirement).

---

### RSA2-C3 - The Auto tab's new refusal says nothing, under a list that still reads "Double-click a path to execute"

| | |
|---|---|
| **Disposition** | Fixed - the Auto tab hides its paths while a staging flow owns the railway, as it does while autonomy runs, and Return Home refreshes it from its worker before it plans, so the offer and the refusal agree.  claims 02c4d0bc (red first), fix 4824d40c; mutations MC3 red, MC3r red. |
| **Grade** | C.  While Return Home plans - up to fifteen seconds on a full railway - the Auto tab keeps each train's list on screen with its prompt, because `updateState` hides the list only while a train is under way or autonomy's own flag is up.  RSA-C2's fix makes the double-click do nothing while a staging flow owns the railway, and it says nothing: no dialog, no log line, the list unchanged.  The diagram's door offers no destinations while the railway is busy, so there the offer and the refusal agree; here the list invites the gesture the handler drops.  The claim asserts only that nothing was dispatched.  **New in round 16.** |
| **Names** | RSA-C2, OB-057, OB-090 |
| **Where** | `AutoLocomotiveStatus.java:358-438` (the list shown whenever autonomy's flag is down, `:384` the prompt), `:1079` (the refusal, silent); `messages.properties:311` (`autolayout.ui.doubleClickExecute`); `regression.testNoSetupEditDuringARun.testTheAutoTabSendsNothingWhileReturnHomeOwnsTheRailway` |
| **Needs execution** | no - by reading |

**Direction:** hide the list while `isStagingInProgress()` too, as it is hidden while autonomy runs - or say why at the double-click.

---

### RSA2-C4 - Unload's wake is reached by no claim: the RSA-B1 claim retires its railway by building a new one, which wakes the waits twice over, and passes with Unload's wake removed

| | |
|---|---|
| **Disposition** | Fixed - claims: each retirement alone - Unload's (retireEveryLayout), a load made current (makeCurrent), a railway built - ends a journey whose wait is untimed, the advisory lowered so it is untimed at once; the code was right, the claim was missing.  Coverage, mutations MC4u red, MC4m red, MC4c red.  02c4d0bc. |
| **Grade** | C.  The disposition: *"a retirement (a new railway, a load made current, Unload's retirement) wakes every sensor wait"* - three calls.  The claim retires with `new Layout(model)` and `makeCurrent()`, each of which wakes every wait by itself, and never calls `retireEveryLayout`, which is Unload's.  So no single one of the three can be removed and turn it red, and Unload's cannot be caught at all.  It matters: every real sensor wait goes untimed once its advisory is given, after five minutes, so without that wake a train Unload's Yes stopped after five minutes' waiting leaves its thread to stop it whenever its sensor is next occupied - RSA-B1 again, on Unload.  The code is right today; the claim is what is missing.  **New in round 16.** |
| **Names** | RSA-B1, RLV8-A1 |
| **Where** | `Layout.java:876-885` (the constructor's wake), `:1876-1887` (`retireEveryLayout`'s), `:1894-1906` (`makeCurrent`'s); `Locomotive.java:844-851` (untimed once advised); `core.testATrainIsDispatchedOnce.java:1604-1670` (the claim), `:1925-1941` (`aForkedRailway`: constructor, then `makeCurrent`) |
| **Needs execution** | done - mutant M1 (`wakeEveryWait` removed from `retireEveryLayout` only): the claim **green**.  Probe `rsa2ProbeUnloadsRetirementEndsAnUntimedWait` (the advisory lowered to 50 ms, so the wait is untimed as a real one is after five minutes; the Yes, then `Layout.retireEveryLayout()` alone): on the archive the journey **ended within 2 s**; on M1 it **was still waiting** 2 s later. |

**Direction:** a claim that retires by `Layout.retireEveryLayout()` (or `clearAutoLayout`) with the wait past its advisory - the probe is that claim - and, for the other two, one claim per door that retires.

---

### RSA2-C5 - The diagram's path gather still asks the model for the railway twice per path on its worker: Unload during it builds an empty railway, and the questions go to a railway the paths did not come from

| | |
|---|---|
| **Disposition** | Fixed - the diagram's gather asks every question of the railway it read once (running), not the model's again; a claim reads its body for any getAutoLayout().  claims 02c4d0bc (red first), fix 4824d40c; mutation MC5 red. |
| **Grade** | C.  RLV12-C5's disposition names `gatherPathOptions` fixed, and its head is: it reads the railway once, `getAutoLayoutIfLoaded()`.  But the loop below asks `ui.getModel().getAutoLayout()` - build-on-miss - twice for every path, on the gather's own thread.  Unload on the event thread in between leaves the model empty, and the next of those calls builds an empty railway that `hasAutoLayout` then answers yes about (CS3-C4, RLV11-C5, RLV12-C5's family); a load in between asks the new railway about the old railway's Points, which is the rule RSA-C6 set - the gathered railway for the whole gesture - carried to the dispatch and not to the gather.  The source claim `testTheModelAndTheDoorsOffTheEventThreadAskForTheRailwayOnce` looks for `hasAutoLayout()` beside `getAutoLayout()`, and this body now has the second without the first, so it reads clean.  **New since v2.7.4** (`170f2e25`, 2026-09-09); older than round 16. |
| **Names** | RLV12-C5, RLV11-C5, CS3-C4, RSA-C6, D3-A1 |
| **Where** | `LayoutRightclickAutonomyMenu.java:96-122` (the worker), `:237-335` (`gatherPathOptions`: `running` read once at `:250-251`, `getAutoLayout()` at `:312` and `:324`); `core.testAutonomyDiagramSession.java:8692-8749` (the source claim) |
| **Needs execution** | no - by reading |

**Direction:** ask `running` at `:312` and `:324`, and teach the source claim that `getAutoLayout()` on a worker is the defect whether or not `hasAutoLayout()` is beside it.

---

### RSA2-C6 - The latency cut-off is asked only while the model believes the power is on: RSA-C4's rule, not carried to its sibling

| | |
|---|---|
| **Disposition** | Fixed - the latency cut-off sends its stop whatever the power flag says, as a route's stop does (RSA-C4); the flag chooses the log line and the dialog.  claims 02c4d0bc (red first), fix 4824d40c; mutation MC6 red. |
| **Grade** | C.  `checkAutoLayoutLatency` cuts the power when a ping's latency exceeds the configured maximum while trains run - a late stop command is a train that overruns - but only `if (model.getPowerState())`, and that is the last GO or STOP echo heard.  One lost GO leaves it reading off with the track live, and the cut-off never fires.  RSA-C4's disposition fixed the route's stop on exactly this ground (*"sent whatever the power flag says ... the flag decides the notice and the log line"*), and this is the one other place a stop is gated on the flag.  **Older**: the same in v2.7.4 (`e5c7f019`, 2025-02-24). |
| **Names** | RSA-C4, SVN-A4, BPV-C1 |
| **Where** | `TrainControlUI.java:11023-11049` (`checkAutoLayoutLatency`); `MarklinControlStation.java:4076` (`getPowerState`) |
| **Needs execution** | no - by reading |

**Direction:** as RSA-C4: send the stop whatever the flag says while the limit is exceeded and trains run, and let the flag choose only whether the dialog is shown.

---

### RSA2-C7 - A square nothing arrives at is built whole, with no facing, and offers a train standing on it both ways out

| | |
|---|---|
| **Disposition** | Fixed (round 18, `240d5948`) - Adam chose (a) (2026-09-29: "Go with a.").  A square nothing arrives at, with track leaving it by more than one side, is built as one copy per way out, each given the side a train facing that way would have come in by - so `leavesBy` lets it out ahead only, `facingOf` names that way, `copyFacing` is written, and the facing the train is placed with chooses its copy (`placementCopy`) as everywhere else; one way out, or none, is still one Point.  The copies share the square's block, and nothing is sent into either, as nothing arrives.  Claim `core.testAutonomyDiagramSession.testASquareNothingArrivesAtFacesItsPlacedTrain` (the probe's railway: the editor offers both facings, and the train placed facing either way stands on the copy facing it and is offered only the station that way), red on the whole square; mutations MC7 red on both claims, MC7f (each copy given the side it leaves by) red on the double curve - on the straight it builds the same two copies. |
| **Grade** | C.  The runtime never commands a direction - *"the copy IS the facing"* - so the one-way edges the build writes are the whole of what the railway knows about which way a train points.  `nodesFor` splits a square by the sides trains arrive by, and a square nothing arrives at is emitted as one Point with no arrival side, *"since there is no facing to record"*; `leavesBy` then allows every way out, `copyFacing` is written only on split squares, and `placementCopy` has one copy to choose.  But a train standing there does face a way.  Sent the other way, it drives off in the direction it points, over track nothing locked, waiting on a sensor it is moving away from - A's consequence.  The reach is narrow: a station no track arrives at - which the switches' default (out of the toe only) makes of any square between two toes until the trailing moves are opened - with a train placed on it by hand, sent the way it does not face.  The checks call such a station unreachable (a warning) and say nothing about a train on it.  **New since v2.7.4** (`8e64830d`, 2026-08-16); older than round 16. |
| **Names** | AMG-D1, IP-D5, TDY2-A1, AUT2-A1, GUI2-B1 |
| **Where** | `AutonomyBuilder.java:595-608` (`nodesFor`: whole where nothing arrives), `:114-116` (`leavesBy`: any side for an unsplit copy), `:746-753`, `:809-822` (`placementCopy`, `startableCopy`), `:1207` (`copyFacing` only where a square splits); `AutonomyChecks.java:1508-1527` (`STATION_UNREACHABLE`, a warning); `TileGraph.java:736-761` (the default direction) |
| **Needs execution** | done - probe `rsa2ProbeAWholeSquareOffersBothWaysOut`: three stations in a row, the straights either side of the middle one made one-way away from it.  Built: the middle square one Point, no block, no `copyFacing`; edges only out of it, west and east.  Findings: `checkStationUnreachable` (warning) on the middle square, nothing about direction (the fixture's stations are unnamed, which the checks also report; named, nothing else changes).  A train placed on it: both `main 3,1 -> main 5,1` and `main 3,1 -> main 1,1` offered, both clear. |

**Direction:** split such a square by the sides trains LEAVE by, one copy per way out, so a placed train's facing chooses its copy as it does elsewhere; or refuse to dispatch from a Point that has ways out on more than one side and no copy to say which way its train points.

---

### RSA2-C8 - The records: a journey given back unrun says nothing, so a hand send says "check log" over a log with no line; and the fence's comment describes the case round 16 made the rare one

| | |
|---|---|
| **Disposition** | Fixed - a journey the Yes overtook says so (autolayout.log.notSentAfterTheStop), whether it was chosen before the stop or its claim was given back after it, so a hand send's "check log" has a line; and the fence's comment names both ways in.  claims 02c4d0bc (red first), fix 4824d40c; mutations MC8g red, MC2e red. |
| **Grade** | C.  `giveBackUnrun` releases and returns false without a log line, and `sendATrainByHand` answers a false with *"Auto route could not be executed: check log."* - so a train sent by hand whose claim completes after the reload's Yes is reported as a failure the log does not explain (every other refusal in `executePathInternal` logs its reason).  And the fence's comment says the train *"is standing at a known milestone point right now, which is exactly where a graceful stop would have put it"*: since round 16 the fence is reached mostly from a wait given up at retirement, with the train stopped by the Yes between two sensors.  **New in round 16.** |
| **Names** | RSA-C1, RSA-B1, RLV8-A1 |
| **Where** | `Layout.java:2210-2225` (`giveBackUnrun`), `:8993-9001` (its call), `:9429-9448` (the fence and its comment); `TrainControlUI.java:6627-6633` |
| **Needs execution** | no - by reading |

**Direction:** a log line naming the train given back after the operator's stop; the fence's comment to name both ways in.

---

### RSA2-D1 - The Yes as the railway's own stop: counted first, asked around every speed a journey writes, and a claim completed after it given back exactly as a misconfigured one is

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RSA2-C1, C2 and C8. |
| **Grade** | D |
| **Names** | RSA-C1, GS-C1, ACC-A1, AUT-C1 |
| **Where** | `Layout.java:2152-2233`, `:8854-9101`, `:9187-9212`, `:9373-9425`; `TrainControlUI.java:4373-4404` |
| **Needs execution** | done - the round's claims green on the archive (baseline); the rest by reading |

`stopEveryTrainWhereItIs` counts, clears `running` and stops what is registered, and takes no monitor on the event thread.  Every speed the journey writes goes through `drive` - the departure (GS-C1's write), the next leg, the pre-arrival speed and the turn's resume - and `drive` asks both the count and the railway, and asks the count again after writing, so a Yes landing mid-write wins.  The stops the journey writes (at a turn, at the destination, at the fence) are unconditional, the safe direction.  `giveBackUnrun` is `handleMisconfiguredPath`'s release under the same monitor - every edge given back once, each end cleared only where this train holds it, the start re-reserved - and drops the claim; nothing else had been registered, called back or announced.  Its callers end: autonomy's thread and a timetable entry leave on `running` false; a hand send reports (RSA2-C8).  After a refused load the journeys stand with their paths held (RLV6's state, accepted) and none writes a speed; a load or Unload then ends them.

---

### RSA2-D2 - A retirement wakes every sensor wait, and a journey past a given-up wait commands nothing but its stop

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RSA2-C2 (b) and C4. |
| **Grade** | D |
| **Names** | RSA-B1, RLV8-A1, RLV8-C3, RLV11-C5, TR-D9 |
| **Where** | `Layout.java:876-885`, `:1876-1906`, `:2158-2159`, `:9104-9137`, `:9187`, `:9231`, `:9373-9448`; `Locomotive.java:758-903`; `Feedback.java:69-82`; `MarklinControlStation.java:1055-1135` |
| **Needs execution** | done - probe `rsa2ProbeUnloadsRetirementEndsAnUntimedWait` on the archive (ended within 2 s); the rest by reading |

Every way a railway is replaced ticks the version and wakes: a build (the load's `fromJSON`, and `getAutoLayout` on an empty model), `makeCurrent`, and Unload's `retireEveryLayout`; `clearAutoLayout` and `parseAuto` are the model's only replacements, and both delete doors refuse while busy.  The wake takes only `Locomotive.monitor` - the lock every sensor event takes - and the waits' new question under it reads the railway's version, a volatile, and takes no lock, so being called inside the model's lock (a build on an empty model) adds no order a sensor wait could reverse.  A wait asks whether to give up in its loop condition, so a wake before the wait is not lost.  After a given-up wait every arm but the stops is fenced by `isCurrentLayout()` - the speed writes, the turn, the early release, the simulated sensor's clearing - and the thread reaches the fence at once, before any new railway can drive the train.  The run then ends on the retired railway: autonomy's thread, a timetable and Return Home's worker each see `running` false or the railway retired, and the borrowed timetable goes back to the railway it came from.

---

### RSA2-D3 - A train's length: no door writes one while trains run, and a journey measures what it hands back by the length it was dispatched with

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RSA-A1, GST-B2, VD16-B2, GS-B1, MT-141 |
| **Where** | `TrainControlUI.java:6436-6444`, `:28355-28436`; `LocomotiveMenuItems.java:143`; `GraphLocAssign.java:842-856`; `LayoutRightclickAutonomyMenu.java:816-850`; `AutonomyEditorPanel.java:10223`, `:10325-10331`; `Layout.java:9076-9078`, `:9289-9300`, `:13160` |
| **Needs execution** | done - the round's claim green on the archive; the rest by reading |

The writers of `setTrainLength` are four: `applyTrainLength` (refused at the click and at OK by `refuseWhileAutonomyRunning`, which asks `isAutonomyRunning` - any train under way or claiming, and a staging flow), the Edit Locomotive dialog (refused at its click and OK, and the editor's copy cannot be open during a run), Mass Assign Train Lengths in the editor (through `applyTrainLength`), and `fromJSON` (a load, after the Yes, on the railway that replaces the one running).  The journey reads the length after its claim rather than at its first line, which no door can reach in between.  The other readers of a running train's length - the covered-track walk, the route guard's cleared set - read the same value for the same reason.

---

### RSA2-D4 - The re-stand, the route's stop, the volatile flag and the gathered railway: as the dispositions say

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RSA2-C5 (the gather's own lookups) and C6 (the sibling). |
| **Grade** | D |
| **Names** | RSA-C3, RSA-C4, RSA-C5, RSA-C6, PRW-A1, PRV-B2 |
| **Where** | `Layout.java:3872-3927`, `:11818-11828`, `:684-687`; `Point.java:541-641`; `MarklinRoute.java:989-1021`; `MarklinControlStation.java:3153-3166`; `LayoutRightclickAutonomyMenu.java:160-174`, `:334`, `:1428-1441` |
| **Needs execution** | done - `testTheReStandNeverShowsTheSquareEmpty` and `testARoutesStopIsSentWhenThePowerAlreadyReadsOff` green on the archive; the rest by reading |

The re-stand reserves the plain copy (no sweep), carries the side and road across, and only then clears the other copies by `clearLocomotiveExcept`, whose `setLocomotive(null)` clears the side and road on the copy left - so the square always holds the train and its tail, and after the unlock nothing else of this run is left to sweep.  The route's STOP goes out unconditionally and `stop()` itself asks nothing.  `atomicRoutes` is volatile.  The destination item sends on `PathOptions.railway`, the one gathered, and a railway replaced since is refused at its fence.

---

### RSA2-D5 - Lock edges: every pair of edges that share an intermediate square locks, per road on the three two-road types, and the pairs the reduction leaves unlocked are kept apart by the runtime

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | OB-269, AUR-B1, IP-D5, RC-A9, VAL8-A1, AUT-B1, RGD-B2 |
| **Where** | `GraphReducer.java:1013-1147`, `:1434-1516`, `:1603-1644`; `AutonomyBuilder.java:1239-1435`; `TilePorts.java:246-355`; `TileGraph.java:209-252`, `:774-899`, `:1190-1210`; `Edge.java:597-779`; `Layout.java:2780-2942`, `:4491-4634` |
| **Needs execution** | no - by reading |

A reduced edge's locations are its intermediate squares - a sensor is never one - keyed by the square, or square and road on an overpass and both double curves; `RouteId` is state and index, the same both ways along a road, so two trains on one arc in opposite directions share a location.  Every pair sharing one locks both ways, and the builder emits every copy of each, so a lock between two rails holds between all their copies.  Crossings, double slips, three-ways and route squares are one location (a route square with rails on four sides carries two roads and locks them together, Adam's "as if it were a crossing").  The pairs left unlocked are exactly the two directions of one run by endpoints - and the runtime refuses those by place (`isPathClear`'s same-rail-back arm, on run-over counts), which also covers a reverse run by a different parallel route, while a reverse run's endpoints stay reserved until the edge is released.  Two copies of one reduced edge are not each other's lock partners and share both end squares, so the block refuses the second while the first holds its end; a standing train's tail is refused by place whichever copy.  The FR-001 lock edges are one-way, and the covered-track and berth rules tell them apart by symmetry (RGD-B2).  `isRunOver` narrows the candidate's read to rail actually run over, and the write still reaches every lock partner, so both orderings of an asymmetric relation refuse.

---

### RSA2-D6 - Places and one-way edges: a square's copies grouped by the tile, facing written as which copy, and a train's copy chosen by its facing

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RSA2-B1 (the feedback double curve's two places on one sensor) and C7 (a square nothing arrives at). |
| **Grade** | D |
| **Names** | OB-238, SA-A1, GUI-B1, TDY2-A1, AMG-D1 |
| **Where** | `AutonomyBuilder.java:77-137`, `:481-655`, `:688-862`, `:953-1219`; `Point.java:943-990`; `Layout.java:11657-11673`; `TileGraph.java:736-761`, `:1534-1542` |
| **Needs execution** | no - by reading |

A square is split one copy per side trains arrive by, and a turning copy per side where trains may turn; a plain copy leaves only by the track it is on and not by the side it came in, a turning copy only by that side; a compulsory turn emits no plain copies, and a dead end none where a turning copy carries the arrival.  All copies of a square carry the tile as their block (per road on the three two-road types), and occupancy asks the block, so a train on any copy is on the square.  A placed train's copy is the one facing the way recorded, a copy trains may arrive at first, a copy facing that way anyway before any other; the facing written on a copy is the far side of the road it arrived on, or its arrival side for a turning copy.

---

### RSA2-D7 - The length rules' bodies: one walk per question, measured track only, and the refusals the rulings name

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | MT-262, MT-333, FR-087, OB-278, OB-294, TDA-B1, PRW-B1, ADA-A1 |
| **Where** | `Layout.java:10793-10910`, `:10925-11228`, `:11258-11575`, `:7903-8347` |
| **Needs execution** | no - by reading (their claims run in the battery) |

`whyTooLongForThisRoute` asks the room at every square the train comes to rest - the destination and any turning square - through the one prefix walk `measuredRoomAtTheEndOf`, which stops at the last switch, a turn, or unmeasured track, and treats an answered zero as measured; a station autonomy may choose takes a train its measured route in holds.  `whyABerthCannotHoldIt` spends the train back over the approach's places in `walkOneTail`'s order, judges nothing where nothing is measured, and refuses only on a shared-metal partner (symmetric) that does not touch the berth.  `whyItWouldMeetItsOwnTail` judges a return only where the way round is measured, keeps the tightest return, restarts after a turn, and reads the body from the same walk.  None of them reads a length the round's door could now change during a run.

---

### RSA2-D8 - Return Home where it meets the runtime: a plan refused on a running railway, run one leg at a time, every leg checked again

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RSA2-C1. |
| **Grade** | D |
| **Names** | OB-073, OB-192, RLV9-B1, RSA-D7 |
| **Where** | `Layout.java:12112-12166`, `:6616-6943`; `TrainControlUI.java:24607-24839` |
| **Needs execution** | no - by reading |

The plan is loaded only on a railway not `isRunning()`, as a sequential timetable (one train at a time), with the operator's timetable kept for a fold and handed back in the worker's `finally`; each leg goes through `executePath`, so through `isPathClear` under the monitor, and a stale plan fails a leg rather than letting two trains meet.

---

### RSA2-D9 - The probes' controls, and the baseline

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | - |
| **Where** | `validate30/rsa2/p1/test/regression/rsa2Probes.java`; `validate30/rsa2/p1/rsa2-out/`, `validate30/rsa2/p2/rsa2-out/`; `validate30/rsa2/logs/` |
| **Needs execution** | done |

Each probe states its precondition before its result: P1 that the crossing road is refused while Y's route is held (so its release is what clears it) and that both trains were under way before the sensor; P2 that the thread was held after asking `running` true and that nothing was under way before the Yes; P3 that nothing was running and the railway was current after the Yes; P5, on the archive, that Unload's retirement does end the wait (the control for M1).  The baseline: `core.testATrainIsDispatchedOnce` 22 run and the three round-16 claims 3 run, 0 failures; the probe job 5 run and the mutant job 2 run, 0 failures; every job exited 0 and none hung, ran out of memory or was ended by the runner.
