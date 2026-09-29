# RLV11 - Validation, round 12: the fixes for RLV10 - Unload's placements while an edit waits, the turns at Unload and another configuration, the station ahead, the editor's outline, the sequential flag, the station labels' builder (TrainControl 3.0.0)

**Status:** open

**Prefix:** RLV11

**Reviewed:** branch `autonomy-diagram-r0` at `75c601b9`, read and run from `git archive 75c601b9` unpacked into `validate30/rlv11/` (`src75` for reading and the baseline; `p11` for the probes, instrumented; `mB`, `mF`, `mBL1` for mutations) and `git archive 54a6733a` (`base54`, for the controls); TC30's working tree and the main checkout's were never read.  Scope `54a6733a..75c601b9`: round 12's claims `446c997c`, its fixes `2573c432`, and the records `362701d2`, `75c601b9` (no code after `2573c432`).  2026-09-28.

## Method

**Read whole:** the brief; `docs/reviews/README.md`; RLV10.md as written (`validate30/RLV10.md`) and as dispositioned (`docs/reviews-2026-09-25/RLV10.md` at `75c601b9`, diffed against it - only the six dispositions of B1 and C1-C5 differ); every commit message in scope; the source diff of `2573c432` (Layout, AutonomySession, AutonomyViewerPanel, TrainControlUI) and its test and behaviour.md hunks; the claims `446c997c`; the records' hunks (behaviour.md, open-questions.md, the 16 RLV10 rows of findings.tsv); the round's mutation spec and log in the scratchpad (`mut_r12_spec.py`, `mut_r12.log`), its red and green logs (`r12-red.log`, `r12-green.log`, `r12-green2.log`), and - after it finished - the coordinator's battery log `battery-0928r12.log` and the report of the one class it failed.

**The code at 75c601b9, around each change:** `TrainControlUI`'s `unloadAutonomy`, `keepWhereTheTrainsStand`, `captureRunningLayout` (both forms), `carryTheTrainsAcross`, `keepThePendingTurnsAcross`, `takeThePendingTurns` / `putThePendingTurnsBack`, `whereTheTrainsAre`, `putTheTrainsBack` (all forms), `writeTheTurns`, `writeTheTurnsOwed`, `reconcileFacingWhenIdle`, `resetAutonomySession`, `forgetTheRailway`, `sourceIsNow`, `initializeTrackDiagram`, `prepareAutonomyReload`, `resetLayoutStationLabels`, the exit's fold in `saveState`, `rebuildRunningLayoutFromSetup`, `followDirectionChanges`, `updateVisiblePoints`, `updateStationLabels`, `facingArrowOf`, `getAutonomyPointForTile`, `getAutonomyOccupantsForTile`, `repaintAutoLocList` / `Lite` / `Full`, `refreshReturnHomeButton`, `openLayoutEditor`'s outline, the Return Home runner's end, `getAutonomySession`, `isAutonomyBusy`, `whyAHandSendIsRefused`; `AutonomyViewerPanel.load` / `loadPrepared` / `revert`, the import's capture and reload; `AutonomySession.capture` (both modes), `faceTheWayItCameIn`, `moveOntoFacingCopy`, `setFacing` / `writePointProperty`, `placeLocomotive`, `duplicate`'s store switch; `AutonomyEditorPanel`'s deep-menu placement doors, `placementChanged`, `rebuildRunningLayoutSoon`, the findings click; `Layout.getLastPointsReached`, `stationAheadItHolds`, `lastKnownPoint`, `toJSON(Map)`, `takeReversalsOnArrival` / `restoreReversalsOnArrival` and every writer of `reversedOnArrival`, the arrival's side-clearing, `executePath`'s retire checks and its three locomotive callbacks, `applyDefaultLocCallbacks`, `setTimetable`, `loadReturnToHomeTimetable`, `toJSON`'s sequential branch; `Point.setLocomotive` / `reserve`, `Layout.clearLocomotiveExcept`, `Edge.isOccupied`; `MarklinControlStation.getAutoLayout` / `getAutoLayoutIfLoaded` / `clearAutoLayout` / `isAutonomyRunning` and every other caller of `getAutoLayout` - 173 sites, swept by a script for the ones inside a posted or threaded lambda and read where one was; `testNothingOnTheEventThreadTakesTheRailwaysMonitor`'s allowances.

**Runs.**  Every JVM through a copy of the project's `docs/tools/one.sh` that passes `-methods` (`one-m.sh`, from `rlv9/p9`), `TC_SCRATCH` a Windows-form folder under `rlv11/scratch`, from a queue (`rlv11/tools/queue.sh`) that started each job only when no `java.exe`, `javaw.exe` or `javac.exe` had run for two checks 20 s apart, and would have retried a job the runner refused, deciding from that attempt's own log (none was refused).  The coordinator's battery held the machine until 08:58; everything below ran 08:58-09:19, one job after another.

- **Baseline** (`src75`): `regression.testTheImportDoorReadsAnOldFile` 34/34, `core.testATrainIsDispatchedOnce` 9/9, `core.testHomeStaging` 111/111, `core.testAutonomyDiagramSession` 156/156, `core.testControlStationFaults` 11/11, `ui.testADirectionChangeIsNotSwallowed` 3/3, `regression.testTheTurnAtTheDestinationReachesTheDiagram` 4/4, `regression.testAPendingTurnSurvivesTheRebuild` 2/2, `regression.testTheGraphIsToldWhenARunEnds` 3/3; no skips.  The B1 claim alone six times: 6/6 green.  `regression.testNothingOnTheEventThreadTakesTheRailwaysMonitor`: 4/5 (RLV11-C4).
- **Probes** (scratch test code in `p11` and `base54` only, each writing what it saw to `rlv11/out`): two in `core.testATrainIsDispatchedOnce` on its hand-built fixture over 42 ordered pairs of locomotives (RLV11-C1, C2); `regression.rlv11Probe` on the live-snapshot sandbox with the window - a placement edit waiting, then Unload or a load (B1); the exit's save while an edit waits (B2); a turned train set off again, then the turns written directly, at Unload, and at another configuration (C3); Unload with a train under way whose sensors then trip, and Unload while a train turns at its destination (C5); an edit meant to re-split the square a moved train stands on (it did not split it; D1).
- **Instrumented** (`p11` only): `MarklinControlStation.getAutoLayout` wrote a stack trace to a file whenever it built a railway, and `clearAutoLayout` and `parseAuto` a line each, so every railway built after an Unload is on record: 22 JVMs, 12 Unloads (the B1 claim three times, four other Unload claims, five probes) and one build (RLV11-C5).
- **Mutations** (each a fresh archive with only its change): `mB` - the placement-only write writes no tail, writes the globals, prunes squares gone and clears a home's facing; `mF` - it writes no facing; `mBL1` - BL1 again (`facingArrowOf` asks `getAutoLayout`), its claim run alone six times.  Results in RLV11-C6.  BB1-BC3, V1, V3, V5 and BL1 were not repeated as the round ran them; `mut_r12.log` has each red where the dispositions say, and BC2b green.

**Nothing written** outside `validate30/rlv11/` and this file, with two exceptions, both mine and both harmless: one command in a shell edit wrote and at once deleted an empty `/tmp/x.txt`, and one `git -C tc-30 status --short` read TC30's state (it printed nothing).  The runner itself takes the shared lock under `%TEMP%` and its own preference node, and removes both.  No git state changed; no commit.  No tree has anything in `cs2_sample_layout` (excluded when unpacked; an empty folder made for the runner's root check).  `LocDB.data` and `UIState.data` in each tree are copies from TC30.  The real `LocDB.data` and `UIState.data` - the main checkout's and `C:/Users/adamo`'s - were hashed before the first JVM and after the last and are unchanged (`41d35b92...`, `a09792d2...`, `686b629e...`, `6a50a2f2...`).  Nothing of mine is running: the queues ended, the monitor expired, and no `java.exe` or `javaw.exe` is left.

**Counts:** 0 A, 2 B, 6 C, 9 D.

**Round 12's fixes do what RLV10 asked at the arrangements RLV10 measured** (RLV11-D1 to D7), and their claims are real.  **What they did not reach is the neighbouring case of each.**  The placement-only write keeps the run's moves at Unload and now takes away a placement the waiting edit made - the exception RLV10-B1's direction named - as the load's carry always has (RLV11-B1); and the exit, the door a session most often ends by, still keeps nothing while an edit waits, now that a way to keep both exists (RLV11-B2).  The station-ahead rule passes over another's kept point even where it is the only station the train holds, so that train is erased in every order where the old code erased the other in half (RLV11-C1); two trains kept at one point nobody stands on were never separated (RLV11-C2).  The turns written during a run move a train that turned and set off again, taking its reservation (RLV11-C3).  The monitor guard is red on the new fold (RLV11-C4).  And a railway is still built after Unload, by the arrival callback of a train turning when it lands (RLV11-C5) - the answer to the question the coordinator added.  **New in round 12:** B1 (at Unload), C1, C3, C4, C6.  **Older:** B1 (at every load), B2, C2, C5.

---

### RLV11-B1 - A train placed by hand while a run declined the rebuild is put back where the railway had it: at Stop Using Autonomy now, as at every load since the carry, against the message that promises the edit at the next load

| | |
|---|---|
| **Disposition** | Answered by Adam's ruling (2026-09-28: *"There should be no setup edit possible during a run"*): the diagram's setup menu was already offered only at rest, and its items now also refuse, and say so, when clicked after a run has begun (AutonomyEditorPanel.refusedWhileRunning, in item, toggle, radio and directionItem) - so no edit can wait against a running railway for a door to keep.  The waiting-edit machinery stays as a safety net for the one-event race between an edit and its posted rebuild.  claims ae0285f3 (red first), fix fd31f56f; mutation CR1 red. |
| **Grade** | B.  DW-A1's shape for the train placed: a placement from the diagram's Autonomy Setup menu writes the setup only, a run declines the rebuild and raises the flag, and the next load - and now Unload - stands the train back where the railway had it, with the square it was put on reading free.  The log said *"The edit IS saved ... it will be picked up the next time the setup is loaded"* (`messages.properties:396`).  **New in round 12 at Unload** (at `54a6733a` Unload kept this edit and lost the run's moves - RLV10-B1; now it keeps the moves and loses this edit); **older at every load** (the carry, RLA5-B1, RLV6-B1).  RLV10-B1's direction named the case: *"placements only ... except a placement the edit itself made"*; neither the fix nor its disposition mentions it. |
| **Names** | RLV10-B1, D2-A1, W7-A1, MT-337, OB-183, RLA5-B1, RLV6-B1 |
| **Where** | `TrainControlUI.java:3099-3119` (`keepWhereTheTrainsStand`); `AutonomySession.java:5425`, `:5527-5531` (placement-only: `loc` replaced from the railway, removed where the railway has no train); `TrainControlUI.java:3075` (the carry: `putTheTrainsBack(standing, null)` - the railway wins for every train); `:6887-6893` (a declined rebuild raises the flag and forgets which placements it carried); `AutonomyEditorPanel.java:9510-9517` (`placementsJustEdited` drained into that declined rebuild); `:1407-1408`, `:5518-5529` (the deep menu's placement, no busy guard) |
| **Needs execution** | done - probes `probePlacementEditWaitsThenUnloadAndLoad`, `probePlacementEditWaitsThenLoad` |

**Measured** on the live-snapshot sandbox with the window.  The run moves EN57-203 from TopMainR0Park to BottomMainC (railway only); EN57-947, standing on BottomInner (`1 - Main:13,9`), is placed by hand on the empty station `1 - Main:14,3` as the deep menu places it (`placeLocomotive`, saved); the flag is raised as a declined edit raises it; then Unload and a load, or a load alone.

| | Unload, then load | load alone (carry) |
|---|---|---|
| `54a6733a` | EN57-947 on `14,3` (**edit kept**); EN57-203 back on TopMainR0Park (move lost - RLV10-B1) | EN57-947 back on BottomInner (**edit lost**); EN57-203 on BottomMainC |
| `75c601b9` | EN57-947 back on BottomInner, the setup `13,9` (**edit lost**); EN57-203 on BottomMainC | the same as `54a6733a` |

The `Remove` item (`placeLocomotive(tile, null)`) is the same door with the opposite answer and goes the same way, by reading.

**Direction:** keep the names the declined rebuild was handed with the flag, instead of draining them into it, and honour them at both doors - `putTheTrainsBack` already takes them, and the placement-only write can skip those trains and their squares.  That is D2-A1's provenance carried across the decline.  Or offer no setup-only placement while the railway is busy, as `moveLocomotive` refuses.  Claim: the probe, at Unload and at a load.

---

### RLV11-B2 - The exit, and a switch of layout source, still keep nothing while an edit a run declined waits: the next start builds every train the run moved back where the file had it

| | |
|---|---|
| **Disposition** | Answered by Adam's ruling (2026-09-28: *"There should be no setup edit possible during a run"*): the diagram's setup menu was already offered only at rest, and its items now also refuse, and say so, when clicked after a run has begun (AutonomyEditorPanel.refusedWhileRunning, in item, toggle, radio and directionItem) - so no edit can wait against a running railway for a door to keep.  The waiting-edit machinery stays as a safety net for the one-event race between an edit and its posted rebuild.  And *"closing TC should keep no edit, correct"*: the exit and a switch of layout source fold the last valid state as before, with no waiting edit to keep.  claims ae0285f3 (red first), fix fd31f56f; mutation CR1 red. |
| **Grade** | B.  RLV10-B1's harm, at the two doors round 12 did not reach: the square a moved train stands on reads free after the next start, and Start can send a train into it.  The exit says so in the log (`autosetup.log.placementsNotSaved`), at the moment the window closes; the switch says nothing.  **Older**: ACC-B3's trade of 2026-09-04 - *"The cost is this session's train positions, which the next run re-establishes"* (`TrainControlUI.java:2536-2537`) - was made when the only write was the whole fold.  RLV10-B1 measured that the next run does not re-establish them, and round 12 wrote the placement-only write that keeps both; the exit and the switch are the doors left.  Adam may prefer C for the exit, since it is logged. |
| **Names** | RLV10-B1, ACC-B3, OPV-C5, DW-A1, RLV7-C2, OB-183; GST-C7 (the exit while running, a different case) |
| **Where** | `TrainControlUI.java:2552-2562` (`wouldCapture && setupEditDeclinedDuringRun` - logged, nothing written); `:2530-2538` (the trade); `:11025-11028` (`initializeTrackDiagram`: the reset's fold returns at the flag, `:3211`, then `sourceIsNow` forgets the railway and lowers the flag, `:3011-3016`, so no load carries anything); `messages.properties:400` |
| **Needs execution** | done for the exit - probe `probeTheExitSaveWhileAnEditWaits`; the switch by reading |

**Measured:** EN57-203 moved from TopMainR0Park (`1 - Main:4,5`) to BottomMainC (`1 - Main:20,14`) on the railway, the flag raised, `saveState(false)` - the exit's save.  The configuration file then places EN57-203 on `1 - Main:4,5` only: the next start stands it on TopMainR0Park, with BottomMainC reading free.

**Direction:** call `keepWhereTheTrainsStand` in the exit's branch that now only logs (it asks the flag, the session and the name itself), and before the reset where a switch of source is about to forget the railway; correct the exit's comment.  Claim: the probe.

---

### RLV11-C1 - The station ahead passes over another train's kept point even where that is the only station the train holds - its destination - so the train stays where another now stands and the fold erases it: 42 orders of 42, where `54a6733a` erased the other train in 21

| | |
|---|---|
| **Disposition** | Fixed - Layout.keptApart: a train that cannot keep its point takes a free station ahead it holds, or one another train is kept at where that one can go on to a free station of its own - so a train bound for the station another was last seen at takes it and the other goes on, in all 42 orders.  claims ae0285f3 (red first), fix fd31f56f; mutation CC1 red. |
| **Grade** | C.  RLV9-C4's erasure through its second fix: a train out on the track is modelled nowhere after the reload or Unload, and the fold erases it from the configuration.  RLV10-C2's arrangement with A's path ending at the station C set off from - atomic routes off, three trains.  Before round 12 the outcome followed the map's order (C lost in 21 of 42, and in the other 21 both were written where they are bound); now A is lost in every order.  **New in round 12** (`2573c432`). |
| **Names** | RLV10-C2, RLV9-C4, RLV8-C4 |
| **Where** | `Layout.java:1672-1682` (`anotherIsKeptThere` skips TS, and no station is left: null); `:1631-1644` (null leaves A kept at TA, where B stands); `:12143-12151` (`toJSON` gives TA to B and writes A nowhere); `TrainControlUI.java:6583-6602` (the carry reads A nowhere) |
| **Needs execution** | done - probe `rlv11ProbeTheDestinationIsAnothersKeptPoint` on `54a6733a` and `75c601b9` |

**Measured** on `testATrainIsDispatchedOnce`'s fixture: TA, TS, TC stations and TJ a point that is none; A's path TA-TJ-TS (A holds TJ and TS), C's TS-TC (C holds TC); B stands on TA; A's milestones [TA], C's [TS].

| | A written nowhere | C written nowhere | both written (A on TS, C on TC) |
|---|---|---|---|
| `54a6733a` | 0 | 21 | 21 |
| `75c601b9` | **42** | 0 | 0 |

C is past TS - TS was released behind it and then locked for A - so C "gives way" to a train that is not passing through TS but bound for it.  **Direction:** when a train's kept point is held by another as a station on that other's path, the kept train is past it: look ahead for it too (C to TC) rather than having the holder pass over its own destination; the claim's fixture with TB removed is the claim.

---

### RLV11-C2 - Two trains under way kept at one point that neither holds and nobody stands on are written as one: the other is erased in every order, and the carry and the fold keep different trains

| | |
|---|---|
| **Disposition** | Fixed - Layout.keptApart keeps no two trains on one point: the one recorded on it first, then the others by name, keep their point, and the rest go on to a station ahead; the carry and the fold read one map, so they agree.  claims ae0285f3 (red first), fix fd31f56f; mutation CC2 red. |
| **Grade** | C.  The same erasure, needing atomic routes off and one station behind two departures: L1 sets off from P and P is released behind it; L2 arrives at P, sets off by another road, and P is released again; neither trips a station sensor.  Both are kept at P (their milestones' start), the point reads no train, and the rule looks ahead only for a train whose point another stands on.  **Older** (RLV9-C4's rule never looked at two trains kept at one point; unchanged by round 12). |
| **Names** | RLV9-C4, RLV10-C2 |
| **Where** | `Layout.java:1631-1644` (`there == null`: both stay at P); `:12132-12141` (`keptHere`: the last of the map wins); `TrainControlUI.java:6583-6602` (the carry: the first wins) |
| **Needs execution** | done - probe `rlv11ProbeTwoTrainsKeptAtOnePoint` on both commits |

**Measured** (KP a station with two roads, KP-KJ1-KS1 held by L1 and KP-KJ2-KS2 by L2, both kept at KP): on both commits, one of the two is written nowhere in 42 orders of 42, and in all 42 the carry reads one train and the fold writes the other.  **Direction:** where two trains are kept at one point, keep one (the later to set off) and give the other the first station ahead it holds, as RLV9-C4 does for a train standing there; at the least have the carry and the fold choose the same one.

---

### RLV11-C3 - Writing the turns during a run moves a train that turned and has set off again from where it turned, sweeping its reservation while it is under way - and a refused load of another configuration keeps that railway

| | |
|---|---|
| **Disposition** | Fixed - faceTheWayItCameIn writes no turn for a train under way again (it owes nothing: its next arrival writes or clears the record), and writeTheTurnsOwed's premise says so.  claims ae0285f3 (red first), fix fd31f56f; mutation CC3 red. |
| **Grade** | C.  `writeTheTurnsOwed`'s premise is *"a turn is written only for a train still standing where it turned (`faceTheWayItCameIn`), which is no such train"*, and the disposition repeats it.  The test is the Point's train, which the train's own locked path sets on its start, and the record of a turn is dropped only at the train's next arrival; so a train that turned at its destination and was dispatched again before the railway went idle is written, and `moveOntoFacingCopy` stands it on another copy through `Point.setLocomotive`, which sweeps it off every other point - its whole path.  At Unload the swept railway is dropped and what is written is what `54a6733a` wrote (measured); at another configuration loaded the same fold runs before the railway is replaced.  Where the other configuration's load is refused, by reading, that railway stays loaded with the train still under way and its points - its destination among them - reading free, while its edges still read occupied.  PRV-B2's comment names this hazard (`Layout.java:9392-9395`: *"moving the train first gives them up early"*).  **New in round 12** (`2573c432`). |
| **Names** | RLV10-C1, RLV9-C1, REV9-A1, PRV-B2, GUI-B1 |
| **Where** | `TrainControlUI.java:7819-7832` (`writeTheTurnsOwed` and its premise); `AutonomyViewerPanel.java:856` (before `loadPrepared`, whose refusals at `:923-950` and `:971-976` keep the railway); `AutonomySession.java:2187-2193` (the test), `:2067-2144` (`moveOntoFacingCopy`); `Point.java:541-566`, `Layout.java:11566-11576` (the sweep); `Layout.java:9303-9308` (a start's side cleared only at the next arrival), `:9345-9361` (the record cleared only by an arrival) |
| **Needs execution** | done - probes `probeTurnedTrainUnderWayDirect`, `...Unload`, `...RefusedOther`, `...RefusedOtherOnAStation` on `75c601b9`, and the last three on `54a6733a` |

**Measured** on the live-snapshot: EN57-947 on BottomInner (northbound), side S, whose copy facing S is BottomInner (eastbound); a turn record for it there; it is then sent along BottomInner (northbound) - Tunnel (southbound) - BottomMainBCPre (eastbound) - BottomMainC and waits on its first sensor (atomic routes on).

| | points it holds after | points of its path reading free | still under way |
|---|---|---|---|
| before | 5 (its whole path) | 0 of 4 | yes |
| `writeTheTurnsOwed` alone (`75c601b9`) | **1** (BottomInner (eastbound)) | **4 of 4** | yes |
| Unload / another configuration (`75c601b9`) | 1, on the railway dropped | 4 of 4 | yes |
| Unload / another configuration (`54a6733a`) | 5 | 0 of 4 | yes |

The configuration Unload writes is the same on both commits (`1 - Main:13,9` facing W: the kept copy's facing overwrites the turn's).  Two attempts to make the other configuration refuse its load failed - a train placed twice is an error, and a setup with errors loads (SVN-B10) - so the refused door is by reading; a hand send is refused only for setup errors, not while a train is under way (`TrainControlUI.whyAHandSendIsRefused`).

**Direction:** write no turn for a train in `activeLocomotives` or `takingPath` - it owes nothing, since its next arrival replaces or clears the record - and correct the premise.  Claim: the direct probe.

---

### RLV11-C4 - Round 12 left the event-thread monitor guard red: `keepWhereTheTrainsStand` takes the railway's monitor (`toJSON`) and is not written down

| | |
|---|---|
| **Disposition** | Fixed in 8ca5eb12 - the monitor guard lists keepWhereTheTrainsStand with its thread and reason, as the reload's fold is (RLV9-D9); 5/5 green, and the battery on 8ca5eb12 is green. |
| **Grade** | C.  A red class in the battery, and a door the guard exists to make somebody justify.  In substance it is the wait `captureRunningLayout(true)` already makes one line above at Unload, which the guard lists: after Yes a train still locking its path holds the monitor across a `CONFIGURE_SLEEP` per edge and accessory, since Yes clears only the dispatch flag.  **New in round 12** (`2573c432`, whose commit reports 38 classes green - not this one). |
| **Names** | OB-192, E8-C4 |
| **Where** | `TrainControlUI.java:3111`; `testNothingOnTheEventThreadTakesTheRailwaysMonitor.java:276-277` (`captureRunningLayout`'s allowance: *"on an explicit gesture"*) |
| **Needs execution** | done - the class on the archive: 4/5, `testEveryDoorOntoTheRailwaysMonitorIsWrittenDown` naming `TrainControlUI.java#keepWhereTheTrainsStand`.  The coordinator's battery after `75c601b9` failed the same test (`battery-0928r12.log`: 316 classes green, this one red). |

**Direction:** the allowance, with the thread and the reason `captureRunningLayout`'s gives - or both folds' serialisation off the event thread.

---

### RLV11-C5 - A railway is still built after Unload: a train turning at its destination when Unload lands calls `getAutoLayout` from its arrival callback, on its own thread

| | |
|---|---|
| **Disposition** | Fixed - the departure and arrival callbacks ask getAutoLayoutIfLoaded (mutations CC5a red, CC5b red, the claim firing both after Unload); the model's pairs (isAutonomyRunning, the sync's sweep, a rename, a route's activation), the routes' (heldReason, the autonomy-lights command, a condition on an autonomy train) and the two posted window bodies (the direction follow, the full status list) ask once - by reading, not claimed.  claims ae0285f3 (red first), fix fd31f56f. |
| **Grade** | C.  RLV8-C2's and CS3-C4's family, and the answer to the question the coordinator added: `hasAutoLayout` then answers yes about an empty railway, current since its construction bumped the layout version.  `applyDefaultLocCallbacks` gives every placed train a departure and an arrival callback that ask `lc.getModel().getAutoLayout()` with no check.  The arrival callback fires after the thread's last retire check (`Layout.java:9185`) and after the turn's delays (`loc.delay(min, max)`, then 1 s), so Unload confirmed in that window leaves a railway behind; the thread also still turns the train after Unload, and asks the railway it has just built whether to switch the functions off.  **Older** (neither the callbacks nor the check moved in round 12). |
| **Names** | RLV8-C2, RLV9-C2, CS3-C4, RLV8-A1, RLV10-B1's builder |
| **Where** | `Layout.java:12060-12070` (`CB_ROUTE_END`, `getAutoLayout` at `:12063`), fired at `:9400-9402`; `:12035-12045` (`CB_ROUTE_START`, `:12038`), fired at `:8809-8811` with no retire check after `configureAndLockPath` (`:8755`); `MarklinControlStation.java:1030-1034` (CS3-C4's comment: *"the clear and the create cannot interleave"* - true of those two statements, not of a caller's `hasAutoLayout` then `getAutoLayout`) |
| **Needs execution** | done - probe `probeUnloadWhileATrainTurnsOnArrival` with the instrumented `getAutoLayout` |

**Measured** (simulation on, delays raised to 4 s once the train had stopped at BottomMainC, where it turns): Unload took 556 ms and left no railway; 1.8 s later the train's thread built one - `MarklinControlStation.getAutoLayout` from `Layout.lambda$applyDefaultLocCallbacks$17` (`Layout.java:12063`) from `executePathInternal` (`:9402`) - and `hasAutoLayout` answered true about a railway that is not the one unloaded.  In the other 11 Unloads traced (the B1 claim three times, four more Unload claims, four probes - one with a train under way whose sensors then tripped) nothing built one, nor did the other-configuration claim: the station labels' fix holds.

**By reading, the rest:** the departure callback after a lock that outlasts Unload's clear - narrower, since Unload's fold waits on the monitor the lock holds and the callback usually fires first; off-thread `hasAutoLayout`-then-`getAutoLayout` pairs, each a window of two lock acquisitions: `MarklinControlStation.isAutonomyRunning` (`:3385-3386`) and the sync's sweep (`:1756-1758`), `Route.java:345-357`, `MarklinRoute.java:589-591` and `:640`; and two posted bodies that build if an event run before Unload's posts them after it - `followDirectionChanges`' flip (`TrainControlUI.java:13044-13053`, posted only at idle) and `repaintAutoLocListFull` (`:29403-29430`, posted by event-thread callers that ask first).  No other posted caller in the sweep asks `getAutoLayout` unguarded.

**Direction:** `getAutoLayoutIfLoaded` in the two callbacks (no railway: no functions, which after Unload is right), a retire check before the arrival's turn and callbacks and after the lock, and the model's pairs asked once.

---

### RLV11-C6 - What the round's claims do not reach: the placement-only write's tail, facing, globals and squares gone, and the station labels' builder caught four times in six

| | |
|---|---|
| **Disposition** | Fixed - claims: Stop Using Autonomy while an edit waits writes a train's tail and facing, and not the settings, a square gone or a home's facing (mutations CM1 red, CM2 red, CM3 red, CM4 red, CM5 red); and the station labels, run after Unload, build nothing (mutation BL1b red, deterministic where RLV10-B1's claim caught it 4 times in 6).  ae0285f3. |
| **Grade** | C.  Claims that pass with their own round's rule undone.  **New in round 12** (`446c997c`, `2573c432`). |
| **Names** | RLV10-B1, RLV10-C5, BL1, BB2 |
| **Where** | `AutonomySession.java:3267`, `:5425`, `:5448` (tails), `:5453-5461` (facing), `:5541-5550` (a home's facing), `:5618-5628` (squares gone, globals); `TrainControlUI.java:5715-5752`, `:5893-5899`, `:5956` |
| **Needs execution** | done - mutations `mB`, `mF`, `mBL1` |

| mutation | claim run | result |
|---|---|---|
| `mB` - the placement-only write writes no tail (`loc` only), writes the globals, prunes squares gone, clears a home's facing | `testAnUnloadWhileAnEditWaitsKeepsWhereTheRunLeftTheTrains` | **green** |
| `mF` - it writes no facing | the same | **green** (the moved train stands on an unsplit square) |
| `mBL1` - BL1: `facingArrowOf` asks `getAutoLayout` | the same, alone, six times | red 4, **green 2** (*"precondition: Unload left a railway loaded"*) |

The claim moves its train with `moveLocomotive`, which clears the tail, onto an unsplit station, and its edit is a priority: it cannot see a tail, a facing, a global or a square.  The labels' builder is caught only when the posted label update lands after Unload, which is a matter of timing.  `getAutonomyPointForTile`, `getAutonomyOccupantsForTile` and `updateStationLabels`' `shown` are unmutated and, their callers being on the event thread, equivalent by reading.  No claim reaches C1's, C2's or C3's arrangements.

**Direction:** a placement-only claim on a split square with a side and a road, with a global and a square gone on the railway; a deterministic claim for the labels - the label update posted after Unload, asserting `hasAutoLayout` false.

---

### RLV11-D1 - RLV10-B1 at Unload: the placement-only write keeps the run's moves and the edit, and touches what the carry would put back and nothing else

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RLV11-B1, B2, C4 and C6. |
| **Grade** | D |
| **Names** | RLV10-B1, ACC-B3, OB-183, TLR-A2, RGD-B1, OB-282 |
| **Where** | `TrainControlUI.java:3099-3119`, `:9036-9068`; `AutonomySession.java:5369-5640` |
| **Needs execution** | done - the claim 6/6 alone and three times traced; probe `probePlacementEditWaitsThenUnloadAndLoad` (EN57-203 kept on BottomMainC) |

Against the whole fold: it writes `loc`, and `arrivedFrom` / `arrivedAlong` from the occupied copy only, with the old occupant's tail removed where the train changed (TLR-A2, RGD-B1), and `facing` for a split copy holding a train - the train, its tail and its copy, which is what `putTheTrainsBack` stands on the next railway.  It leaves priorities, homes and their facing (the clear is guarded), speeds and exclusions, the globals and the timetable, and squares gone, which is what makes it safe against an edit that is not a placement.  It runs only while the flag is up, after the fold that returned at the flag, and saves; the turns are written before it and it writes the copy they moved the train onto.  A square whose copies the waiting edit renamed would be skipped (`tilesByName` is the edited naming) where the carry leaves the train on the setup's square; the probe's edit (trains may turn round on BottomMainC) did not re-split that square, so this is unmeasured.

### RLV11-D2 - RLV10-C1: the turns at Unload, at another configuration, and after the carry before it; the drain extracted

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RLV11-C3. |
| **Grade** | D |
| **Names** | RLV10-C1, RGD-C7, REV9-A1, RLV7-C4 |
| **Where** | `TrainControlUI.java:7761-7832`, `:7848-7875`, `:9044`; `AutonomyViewerPanel.java:791-857` |
| **Needs execution** | done - the claims green; BC1a and BC1b red in the round's log; probes as C3 |

Unload writes the turns before its fold, and the fold (or the placement-only write) writes the facing of the copy the turn moved the train onto, so the configuration left is right even where the store's active configuration is another - `duplicate` switches it without a load, and `setFacing` writes there, as the idle drain always has.  Another configuration: with the flag down the turns go before `loadPrepared`'s fold; with it up the carry reloads the configuration running, puts the turns back on the new railway, and the same line writes them; the RLV7-C4 refusal returns before it.  `writeTheTurns` is the drain's loop, 48 lines of 48 identical, and the drain still calls it inside its catch.  The two new doors call it outside any: an exception from `faceTheWayItCameIn` would abandon Unload after its Yes, with the records put back so a retry meets it again.  No way was found to make it throw; not graded.

### RLV11-D3 - RLV10-C2 for the arrangement it measured, and every reader of the rule

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RLV11-C1 and C2. |
| **Grade** | D |
| **Names** | RLV10-C2, RLV9-C4, BC2b |
| **Where** | `Layout.java:1608-1690`; readers `TrainControlUI.java:3111`, `:3218`, `:6561`, `AutonomyViewerPanel.java:906` |
| **Needs execution** | done - the claim green, 42 pairs, both orders |

BC2b is equivalent as the disposition says: a train is moved only onto a station no train in `read` is kept at, so no entry of `out` becomes a point another's entry of `read` names, and the gives-way test answers the same from either map.  No two moved trains can share a station, since each must be the train holding it.  The carry, the three folds and the placement-only write all read one method.

### RLV11-D4 - RLV10-C3: one predicate at both lists

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLV10-C3, RLV9-C5 |
| **Where** | `TrainControlUI.java:5421-5425`; `AutonomyEditorPanel.java:1181`, `:1193-1198` |
| **Needs execution** | no - the claim green; BC3 red in the round's log |

The Auto tab's door, the page jump and the editor's own list now all outline only where the finding names other squares; an editor already open is brought forward without one, as before.

### RLV11-D5 - RLV10-C4: the sequential flag's comments

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLV10-C4, RLV9-B1 |
| **Where** | `Layout.java:11916-11930`, `:12194-12204`; `testControlStationFaults.java:256-262` |
| **Needs execution** | no |

`setTimetableSequential` has no caller in `src` (two tests), `fromJSON` sets the field, and the loan keeps the branch from writing a plan; the comments now say so.

### RLV11-D6 - RLV10-C5's claims

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RLV11-C6. |
| **Grade** | D |
| **Names** | RLV10-C5; V1, V3, V5 |
| **Where** | `testHomeStaging.java:1103-1151`, `testATrainIsDispatchedOnce.java:697-817`, `testAutonomyDiagramSession.java:7505-7536` |
| **Needs execution** | done - green in the baseline; V1, V3, V5 red in the round's log |

The loan's end, the station test (TJ is no station and nearer) and the clearing click are each asserted where the mutation breaks them.  The page jump's handler and the Auto tab's click remain by reading, as the disposition says.

### RLV11-D7 - The station labels' builder that RLV10-B1's claim found

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RLV11-C5 and C6. |
| **Grade** | D |
| **Names** | RLV8-C2, RLV9-C2, BL1 |
| **Where** | `TrainControlUI.java:5893-5899`, `:5715-5752`, `:5956`, `:29017-29044` |
| **Needs execution** | done - traced (RLV11-C5); the claim 6/6 alone |

The posted label update was the path, by reading: a refresh posted by the claim's move runs before Unload and posts the labels after it.  It now asks once and builds nothing; in 11 of 12 traced Unloads nothing did.

### RLV11-D8 - Baseline

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RLV11-C4. |
| **Grade** | D |
| **Names** | 446c997c, 2573c432 |
| **Where** | the method |
| **Needs execution** | done |

Nine classes, 333 tests, no failures and no skips on the archive.

### RLV11-D9 - The records

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RLV11-C3 (C1's disposition repeats its premise). |
| **Grade** | D |
| **Names** | RLV10 |
| **Where** | `docs/reviews-2026-09-25/RLV10.md`; `findings.tsv` (16 RLV10 rows); `behaviour.md:2472-2479`, `:2533`; `open-questions.md:429-430` |
| **Needs execution** | no |

The dispositions describe what `2573c432` does and say which parts are by reading.  The store's 16 rows (1 B, 5 C, 10 D, all Closed) and both counts (4,796 rows, 4,439 findings) agree with the documents.  behaviour.md's new sentences are true of the code, including that Unload writes where the trains stand *"as a load then carries them"* - which is also true of RLV11-B1.
