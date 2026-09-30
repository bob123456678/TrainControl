# RSA4 - Release validation, round 19: the carry reaches the running configuration's timetable at a rename - not the trains, which a rename from the diagram after a run puts back where they started; not another configuration's timetable, a page ticked out and back in, or a train's road; a timetable's own loop outlives a stop and joins the next run; the Yes can still straddle a timetable's start; and three readers still read the hand-over journeys-first (TrainControl 3.0.0)

**Status:** open

**Prefix:** RSA4

**Reviewed:** branch `autonomy-diagram-r0` at `345dd13e`, read and run from `git archive 345dd13e` unpacked into `validate30/rsa4/a345/` (reading, and the baseline), and a copy of it, `validate30/rsa4/p1/`, with one scratch probe class added (`test/regression/rsa4Probes.java`, not in the battery); `cs2_sample_layout` excluded and an empty folder of that name made for the runner; `LocDB.data` and `UIState.data` beside each root are copies from TC30.  TC30's working tree and the main checkout's were never read; git was used only as `git -C tc-30 log / show / diff / archive / ls-tree`.  Round 19 is `d851714c..345dd13e`: the fixes for RSA3 (`116bec78`, `1fb38e7b`, `dd07d5b1`), their records (`29aa9e55`), Adam's two notes built beside them (`770dccd8`), his manual-test results (`f348e22c`), a merge touching one test (`18148b29`) and the tracker (`345dd13e`).  2026-09-29.

## Method

**Round 19, read whole:** the brief; `validate30/RSA3.md` and its dispositions at the commit (`docs/reviews-2026-09-25/RSA3.md`); the round's source diff (`Layout`, `AutonomyBuilder`, `AutonomySession`, `AutonomyEditorPanel`, `TrainControlUI`) and its claims (`core.testATrainIsDispatchedOnce`, `core.testAutonomyDiagramSession`), and each changed method in its context.  For RSA3-B1: `carryTheTimetableAcross` and `carry` whole, `deriveStationIndex` and its eight callers, `rebuild`, `open`, `discardEdits`, `getStationIndex`'s lazy path, `AutonomyBuilder.tilesByName`, `baseNames`, `uniqueNames`, `edgesByName`, `nodeName`, the store's `getConfiguration`, `createConfiguration`, `snapshotSetup`/`restoreSetup`, `repairLocomotive`/`repairLocomotiveInSetup`/`repairLocomotiveInTimetable`, `setActiveConfiguration` and its seven callers in `AutonomyViewerPanel`, `load`/`loadPrepared`/`revert`, the capture (`capture` whole), and every other reader of a Point name I could find: `whereTheTrainsAre`/`putTheTrainsBack`/`putTheRoadBack`, `rebuildRunningLayoutFromSetup` (both), `Point.toJSON`, `Layout.fromJSON`'s placement, side, road, `blockedBy`, home and exclusion restores, `roadNamed`/`namesOfRoad`, the builder's `blockedBy` emission.  For RSA3-C1/C2/C3: `whoseJourneyAwaits`, `isAlreadyUnderway`, `trainsUnderway`, the hand-over and both ends of a journey (`releasedEarly`, `locomotiveMilestones`, `clearedEdges` created and removed), the early release in `executePathInternal`, and every reader of `activeLocomotives` and `takingPath` together (`getActiveAccs`, `getLastPointsReached`, `stationsAheadItHolds`, `whereTheTrainIs`, `railHeldByThisTrain`, `walkStandingTrains`).  For RSA3-C4/C5: `stopEveryTrainWhereItIs`, `stopLocomotives`, `pauseUnlessStopped`, `pacedWaitMillis`, `pacedWait`, `executeTimetable`/`executeTimetableInternal` whole (the loop, the entry threads, the stuck limit, the completion wait), `runLocomotives`/`runLocomotive` and every pause they make, `isRunning`, every write and read of `running`; in the window the Yes (`prepareAutonomyReload`), Graceful Stop's handler and worker, Start's handler and worker, `requestStartAutonomy`, Execute Timetable's worker, `requestReturnToHome`, `isAutonomyBusy`, `refreshReturnHomeButton`, `HomeLocomotiveMenu`, `LayoutRightclickAutonomyMenu`'s Start and Return Home items.  For RSA3-C6: `Node`, `nodesFor`, `sidesBehindTheWaysOut`, `arrivalAllowed`, `homeFacingsAt`, `homeCopy`, `placementCopy`, `startableCopy`, `build`'s point emission, `HomeStaging.atHome`/`homeCopiesOf`/`canGetHome`, and the session's bar and facing readers.  `770dccd8` where it meets the railway (the guard window, the wrench).  `open-questions.md` and `behaviour.md` where round 19's rules are written, or not.

**What RSA3 did not reach, read:** `HomeStaging.plan`, `search`, `astar` and `firstClearRoute` whole; `AutonomyEditorPanel.promptName` and the path from a diagram-menu gesture to the running layout (`setupChanged`, `rebuildRunningLayoutSoon`); the import door past its busy checks (`AutonomyViewerPanel.importConfiguration`, `importLegacyGraph`, `loadAfterImport`, the store's `importBundle`, `exportBundle`), the Autonomy menu's Import item and Pages ticks, `choosePages`; the timetable UI's doors (`deleteTimetableEntry`, `updateTimetableDelay`, `restartTimetable`, `clearTimetable`).  **Not reached:** `HomeStaging.canEnter`, `blockedSensors` and the tail helpers past what RSA3 read; `AutonomyEditorPanel` past the rename and the guard window; `AutonomyCompanionStore`'s reconciliation, portals, captions and link names; `CS2File`; export past `exportBundle`.  Every finding was searched for in `docs/manual-tests/findings.tsv` (4,961 rows) before it was written.

**Runs.**  Every JVM through the archive's own `docs/tools/one.sh`, `TC_SCRATCH` a Windows-form folder per job under `rsa4/scratch`, started by a queue (`rsa4/runjob.sh`) only once no `java.exe`, `javaw.exe` or `javac.exe` had been seen and no battery lock existed for two checks 20 s apart, each deciding from its own log.  The coordinator's battery held the machine until 22:36 and Adam's TrainControl was open from the main checkout before 21:55 and again 22:06-22:14; everything below ran 22:37-22:54, one job at a time.

- **Probes** (p1, `regression.rsa4Probes`: 11 run, 0 failures).  They assert nothing; each writes what it saw, preconditions first, to `p1/rsa4-out/`.  Q0 (control), Q1 and Q2 for RSA4-C1; Q3 for C2; Q4 for B1; Q5 for C4; Q6 and Q7 for C3; Q8 for C5; Q9 for B2; Q10 for A1.  Q3, Q6 and Q7 hold their interleavings exactly: Q3 holds the `entryPause` monitor, so the Yes blocks between its two statements, and holds the timetable at the log line after its read of the count; Q6 and Q7 use the round's own hand-over hold, extended to `get` and `size`, which these readers use.  The first job did not compile (`LayoutDiagram.addComponent` declares `IOException`) and ran nothing.  Three probes were corrected and run again alone: Q5 and Q9 first sent legs out of dead-end stations, which have no way out (2 run, 0 failures); Q6's train never reached its hand-over - the simulated station sends no echo, so a switch on the path is never confirmed - and was given the echo, and a hook on `size`, since copying an empty map reads no entry (Q6 and Q7, 2 run, 0 failures).
- **Baseline** (a345): the round's claims - `core.testATrainIsDispatchedOnce`'s `testTheSensorRuleSeesATrainHandingOver`, `testATrainHandingOverIsStillUnderWay`, `testATrainHandingOverIsCounted`, `testARouteMayNotWaitOnASensorAClaimAwaits`, `testASensorIsHeldUntilTheRailToItIsGivenBack`, `testTheYesWaitsForNoLockAnArrivalHolds`, `testATimetableTheCountRefusesLeavesNothingRunning`, `testAnEntryIsRunningUntilItHasLeft`, `testAnEntryTheYesFoundRefusingLeavesWithItsRun`, `testASensorAJourneyHasPassedIsFreeAgain`, `testARouteMayNotWaitOnASensorAnotherTrainAwaits`, and `core.testAutonomyDiagramSession`'s `testAStaleBarDoesNotShutASquareNothingArrivesAt`, `testASquareNothingArrivesAtOffersNoHomeFacing`, `testARenamedStationKeepsItsTimetable`, `testASquareSplitByItsWaysOutKeepsItsTimetable`: 15 run, 0 failures.

**Nothing written** outside `validate30/rsa4/` and this file.  Each run copied the data files into a folder of its own and a preference node of its own (`one.sh`), and removed both; even the working copies' `LocDB.data` and `UIState.data` in `a345` and `p1` are unchanged since they were copied.  The two nodes under `HKCU\Software\JavaSoft\Prefs\traincontrol-test-runs` (`battery-680`, `one-620`) are the ones RSA and RSA2 found - not mine, not touched.  No git state changed; no commit.  The real `LocDB.data` and `UIState.data` were copied and hashed at 21:26, before the first JVM: `C:/Users/adamo`'s are unchanged byte for byte (`6ef56939...`, `9e32894e...`).  **The main checkout's two changed** (`481311d4...` to `dffe3617...`, `f12a462c...` to `b80baf8d...`), both written at 22:52:48 - between two of my jobs (22:51:07-22:51:29 and 22:53:36-22:53:58), with no JVM of mine alive, and none of mine ran from that folder or wrote any data file.  Adam's TrainControl runs from that folder and writes both files when it closes; I did not see it at 22:52 and cannot say more than that the write was not mine.  Nothing of mine is running: every queue and waiter has ended, no `java.exe`, `javaw.exe` or `javac.exe` is left, and the battery lock is gone.

**Counts:** 1 A, 2 B, 6 C, 10 D.

**Round 19's fixes are right where they reach** (RSA4-D1 to D6, the round's fifteen claims green): the sensor rule, `isAlreadyUnderway` and the cap read the claims first; a sensor is held until the rail to it is given back; the Yes takes no lock the event thread can wait on; a timetable's entry threads are counted and woken; a copy of a square nothing arrives at is no arrival for any reader; and the configuration running keeps its captured timetable through a rename, a split or a copy more or fewer.  **What they leave:** the carry reaches that one configuration - another configuration's timetable is lost once TrainControl has restarted (RSA4-B1, measured); the entry threads are counted and the loop that starts them is not, so a stop in its pause lets the next run start while it lives, and the old timetable then sends its next train into that run and stops it (C1, measured); the Yes's two statements can still bracket a timetable's two (C2, measured - microseconds); three more readers take the hand-over journeys-first, the route guard's and the tail rule's among them (C3, measured); and the decided entry still states the old sensor rule (C6).  **Older, found asking the round's own question - every other reader of a Point name:** the trains themselves are carried across a setup gesture's rebuild by Point name, so a station renamed from the diagram after a run puts a train the run left there back where it started, the square it stands on reading free (A1, measured); a page ticked out of autonomy and back in loses its timetable entries (B2, measured); a driven train's road is dropped by a rename (C4, measured); and an import into another configuration while trains run renames Points under the next fold (C5, measured).  **New in round 19:** B1, C2, C6.  **Older:** A1, B2, C1 (RSA3-C5's fix incomplete), C3, C4, C5.

---

### RSA4-A1 - A gesture from the diagram's setup menu that renames a Point - a station renamed, above all - puts a train the last run left there back where the setup last had it: the rebuild carries the trains across by Point name, nothing has folded since the run, and the square the train stands on reads free

| | |
|---|---|
| **Disposition** | Fixed - a rebuild puts each train back on the Point the rebuild gives the copy it stood on (AutonomySession.pointNamedNow, which TrainControlUI.putTheTrainsBack asks at every door that carries the trains across): the name recorded where it is still built; otherwise the same copy under its square's name now, or, where that copy is gone, the copy of the square facing the same way, a turning copy last - never a copy facing another way.  Each name is traced as the latest build to give it had it; a rename rebuilds at once, the setup's doors offering one only at rest.  The trains' roads are carried the same way.  claims and fix 79e75e08; mutations MA1 red, MA1d red, MA1s red, MA1f red.  The claims: a station renamed after a run, at the door on his railway and on a three-station line; a square made two copies since; and a station with no facing renamed.  The door's claim was written with the fix and seen red under MA1d, which takes the door's part of it away. |
| **Grade** | A.  **How:** where a train stands after a run lives on the running layout alone - *"nothing folds it into the setup when the run ends"* (`rebuildRunningLayoutFromSetup`'s own comment) - and OB-183's answer is that every setup gesture's rebuild reads each train off the running layout by the POINT NAME it stands on (`whereTheTrainsAre`), loads from the setup without a fold (`load(active, false, false)`), and puts each train back by that name (`putTheTrainsBack`).  A station renamed from the diagram's own right-click setup menu - its Name item, offered at rest - renames every Point of that square; the rebuild then finds no Point by the recorded name (`built.getPoint(name)` is null) and leaves the train *"where the rebuild put it"*: where the setup last had it, which is the square it stood on before the run.  So the railway holds the train where it is not, and the square it does stand on reads free.  Its next dispatch - Start, a timetable entry, a hand send from where the diagram now draws it - locks a route from the square it is not on and sets it running from the one it is on, over track nothing locked; and the tail walk anchors at the recorded square, so the track its body lies across behind the platform is claimed by nobody.  (The occupied sensor still refuses a route INTO the platform, and nothing asks that a route's START sensor reads the train.)  The comment beside the skip reads *"An edit that renamed or removed a square is an edit about that square, and the setup's answer is the only one left"* - but that answer is the one OB-183 found stale, and the javadoc beside it says so: *"the record is newer where a run moved a train after the last capture"*.  The autonomy editor is safe - opening it folds the running layout first (`openLayoutEditor`) - and the diagram's menu is not: *"nothing on that path ever captures the running layout"*.  The same holds for every gesture on that menu that renames a copy: a station on a one-way line marked as somewhere trains may turn (its one copy gains a heading), a direction set, a must-turn mark.  **Round 19 carried the captured timetable across exactly these renames, by square (RSA3-B1); this reader of a Point name - where the trains stand - it left going by name.**  **Reach:** any of Adam's 33 named stations, renamed from the diagram at rest with a train the last run left on it and no fold since (a load, the editor, Unload, the exit).  **Older:** the carry by name is OB-183's (2026-09-08).  RLV7-C3 found this shape at a PAGE rename with a declined edit waiting, deferred as OB-306 because only an unnamed station is renamed with its page and a one-event race is needed; this door needs neither.  RSA3-D5 called it *"the known limit (RLV7-C3)"*, which it is not. |
| **Names** | OB-183, DW-A1, RLV7-C3, OB-306, OB-144, MT-337, D2-A1, RSA3-B1, RSA3-D5, OB-034 |
| **Where** | `AutonomyEditorPanel.java:6102-6173` (`promptName`: `setPointName`, then `setupChanged`), `:9536-9595` (`setupChanged` -> `rebuildRunningLayoutSoon` -> `rebuildRunningLayoutFromSetup(true, edited)`); `TrainControlUI.java:7030-7188` (the rebuild: `whereTheTrainsAre` `:7135`, `load(active, false, false)` `:7161`, `putTheTrainsBack` `:7163`; *"nothing folds it"* `:7092-7094`; *"nothing on that path ever captures"* `:7127-7129`), `:6728-6779` (`whereTheTrainsAre`, by Point name), `:6889-6903` (`putTheTrainsBack`: `built.getPoint(recorded)` null, `continue`), `:6819-6823` (the rule), `:6868-6877` (the stale record admitted), `:5389-5394` (the editor's fold on opening); `Layout.java:2924-2990` (`isPathClear` asks every end sensor, and not the start's) |
| **Needs execution** | done - probe `rsa4ProbeQ10ARenameAfterARunPutsATrainBackWhereItStarted` (Q10): Alpha, Beta, Gamma in a row, the train placed at Alpha and loaded; the run's result set on the running layout - the train on `Beta (eastbound)`, the setup still placing it at `main:1,1`; then the gesture and its rebuild as `rebuildRunningLayoutFromSetup(true, none)` makes it (`whereTheTrainsAre`, the load without a fold, `putTheTrainsBack` - the window's own static methods).  **Control**, a priority set on Gamma from the same menu: the train held at `Beta (eastbound)`, and the next fold records it at `main:3,1`.  **Beta renamed Bravo:** the train **held at Alpha**; `Bravo (eastbound)`, the copy it stands on, **holds nothing**; the next fold records it at **`main:1,1`**. |

**Direction:** carry each train across by square and copy, as the capture does and as round 19 carries the timetable - trace the recorded Point name to its square through the builds the session has seen, and stand the train on the copy of that square facing the same way - or fold the running layout before any gesture that renames a Point, as opening the editor does.

---

### RSA4-B1 - The timetable is carried into the configuration running and no other: a station renamed while one configuration runs leaves another configuration's captured timetable naming the old Points, and once TrainControl has been restarted nothing traces them - that configuration's next load drops the entries and its next fold erases them

| | |
|---|---|
| **Disposition** | Fixed - the names every configuration stores are carried, not only the one in use's (AutonomySession.carryTheNamesAcross): the configuration in use by its build, as round 19 carried it; every other across a renamed square alone - to the same heading under the square's new name - since its own build can differ, which squares turn trains round being its own.  A name only another configuration's build gives is remembered by the square it begins with when a session opens, so a rename made with another configuration in use carries it too.  claims and fix 79e75e08; mutations MB1o red, MB1r red; and round 19's, re-aimed at the rewritten carry, mutations MB1 red, MB1n red, MB1s red. |
| **Grade** | B.  RSA3-B1's loss, through the configurations round 19's carry does not reach.  **How:** `carryTheTimetableAcross` reads `store.getActiveConfiguration()` and carries that configuration's `globals.timetable` only.  A setup holds any number of configurations - the Autonomy menu's list, New, Import - sharing the setup's station names, each with its own captured timetable.  Rename a station while configuration A runs and A's legs are carried; B's still name the old Points.  In the same session B is still carried when it is loaded: `loadPrepared` makes it active and rebuilds, and the carry traces the old names through `squareOfNameSeen`, the builds this session has made.  After a restart it cannot: a new session has seen only the new names, and the fallback for a name written before the session traces it only to a square still CALLED by its base (`name.startsWith(called + " (")`), which a renamed station is not.  So B's first load in a later session drops every entry through the renamed station (one log line each), and B's next fold - a reload, Unload, the exit - writes the shorter timetable over it.  The locomotive half has done better since OB-069: `AutonomyCompanionStore.repairLocomotive` repairs EVERY configuration.  **Reach:** a setup with a second configuration holding a captured timetable; Adam's live snapshot has one configuration.  **New in round 19**, as a gap in RSA3-B1's fix; the loss itself is ISD-B2's station half. |
| **Names** | RSA3-B1, ISD-B2, OB-069, BPV-C12, RLV8-B1, RLV9-B1 |
| **Where** | `AutonomySession.java:482-506` (`carryTheTimetableAcross`: the active configuration's timetable only, `:490`), `:549-567` (the fallback: a square still called by the name's base), `:464-467` (`squareOfNameSeen`, one session's builds); `AutonomyViewerPanel.java:918-921` (`loadPrepared`: made active, then rebuilt); `AutonomyCompanionStore.java:1575-1583` (`repairLocomotive`: every configuration) |
| **Needs execution** | done - probe `rsa4ProbeQ4AnotherConfigurationsTimetableAfterARename` (Q4): configuration A with a captured leg `Beta (eastbound) -> Gamma`, B a copy of A, saved; Beta renamed Bravo with A active, saved - A stores `Bravo (eastbound) -> Gamma`, **B still `Beta (eastbound) -> Gamma`**.  **Same session** (control): B made active and rebuilt, as `loadPrepared` does - carried to `Bravo (eastbound)`, 1 entry loaded, kept by B's fold.  **The next session** (a new `AutonomySession` opened on the same folder): B made active and rebuilt - **not carried**; loaded, **0 entries**, logged *"A timetable entry could not be loaded and was skipped: Edge Beta (eastbound) -> Gamma does not exist"*; after B's next fold B stores **`[]`**. |

**Direction:** carry every configuration's timetable at the rename, as a locomotive rename repairs every configuration.

---

### RSA4-B2 - A page ticked out of autonomy and back in loses every timetable entry through it: the tick's reload drops the entries it cannot build, and the next fold - the tick back in is one - writes the shorter timetable over the configuration

| | |
|---|---|
| **Disposition** | Fixed - a fold keeps each stored timetable entry its load could not read, in its place, where the running timetable is otherwise the one stored (AutonomySession.withTheEntriesItsLoadDropped): a page ticked out and back in keeps its entries.  Where the operator has changed the timetable meanwhile - recorded, cleared, deleted or reordered an entry - the running one is theirs, and is stored as it is.  claims and fix 79e75e08; mutations MB2 red, MB2e red. |
| **Grade** | B.  The loss RSA3-B1 was graded for, on a gesture the setup is built to undo.  **How:** ticking a page off in the Autonomy menu's Pages list is reversible by design - the capture judges only squares on pages in play, since *"excluding a page has to be reversible, or a page ticked off and back on has silently lost its placements, its facings and its markings"* - and the tick reloads the configuration running (`reloadActiveDiagramConfiguration`: `load(active, true)`), which folds the running layout first and then builds from the setup.  The fold writes the whole of `globals` from the running layout, timetable included (`configuration.put("globals", globals)`); nothing keeps an entry the running layout does not have.  So: ticked out, the fold keeps the entries (the running layout still has them) and the load drops every entry with a leg on that page, one log line each, because the build has no such Point; the next fold - Unload, the exit, any reload, or ticking the page back in, whose reload folds first - writes the shorter timetable over the configuration, and the page comes back without them.  The carry leaves such a name untouched, rightly - its square is not in the build - and the disposition's *"a name that cannot be traced - its square gone from the diagram - is left, and the load drops that entry and logs it as before"* reads a reversible gesture as a deletion.  **Reach:** a captured timetable with a leg on a page later ticked out - a yard page taken out of autonomy for an evening, say.  **Older** (the tick's reload and the whole-globals fold predate the round); found asking round 19's question. |
| **Names** | RSA3-B1, AD-A7, BPV-C12, OB-130, ISD-B2 |
| **Where** | `AutonomyMenu.java:741-817` (the Pages tick: `setPageExcluded`, save, `reloadActiveDiagramConfiguration` `:816`); `TrainControlUI.java:25575-25582` (`load(active, true)`); `AutonomyViewerPanel.java:899-914` (the fold before the load), `:956` (the build); `AutonomySession.java:5768-5810` (the capture keeps squares on pages out of play), `:5821-5829` (`globals` written whole from the running layout); `Layout.java` `fromJSON` (an entry naming a Point the build lacks is dropped, BPV-C12) |
| **Needs execution** | done - probe `rsa4ProbeQ9APageTickedOutAndBackInLosesItsTimetable` (Q9): two pages and a captured leg on each (`Beta (eastbound) -> Gamma` on `main`, `Epsilon (eastbound) -> Zeta` on `second`), both loaded - 2 entries.  `second` ticked out and the menu's reload made as `load(active, true)` makes it (the fold, then the build): **1 entry loaded, 2 stored**.  Ticked back in, the same reload: **1 loaded, 1 stored** - the entry on `second` gone - while the placement on `second` came back. |

**Direction:** keep, at a fold, the stored entries whose Points the build lacks because their page is out of play - as the capture keeps those squares' placements.

---

### RSA4-C1 - RSA3-C5's fix counts a timetable's entry threads and wakes their pauses, and the timetable's own loop still pauses between entries in a sleep no stop cuts short and `isRunning()` does not count: after a stop with no train moving the railway reads idle, Start comes back, and a run started then is joined by the old timetable's remaining entries - whose last one stops it

| | |
|---|---|
| **Disposition** | Fixed - the timetable's own loop is counted in timetableEntries while it dispatches, and its pause between entries (pacedWait) waits on the monitor every stop notifies, as an entry's own pause does: after a stop the railway reads busy until the loop has left, and it leaves at once.  claims and fix 79e75e08; mutations MC1c red, MC1p red.  The claim holds the pause's monitor, so a stop has cleared running and the loop cannot yet leave, asks isRunning, and then times the loop's leaving. |
| **Grade** | C.  **How:** `executeTimetableInternal` walks the entries on its caller's thread, and between them - waiting out an entry's delay, or for the one before to start or finish - it pauses in `pacedWait`, `Locomotive.delay`: a plain sleep of the operator's delays (1 to 2 s on Adam's railway, 1 to 5 by default).  That thread is counted by nothing: `isRunning()` is `running`, the journeys, the autonomy threads and, since round 19, the entry threads.  So a stop - Graceful Stop, or the Yes with its load refused - made while the loop pauses and no train moves leaves `isRunning()` false with the loop still inside the timetable: Graceful Stop's worker sees idle and gives Start back at once, and Start's worker asks `isAutonomyBusy()`, which reads the same.  Pressed within the pause, Start sets `running`; the old loop wakes to it and goes on with the timetable - after Graceful Stop it SENDS the next entry's train into the autonomy run (the count has not moved), and when the timetable's last entry arrives its thread calls `stopLocomotives()` and stops autonomy; after the Yes the entry is refused on the count until the stuck limit and then stops the run blaming the track - RSA3-C5's outcome exactly.  When the stopped timetable's call does return, its worker also greys Graceful Stop under the new run.  Return Home can take the same gap (its button is repainted as the railway reads idle), and then two loops walk the plan it borrows the timetable for.  RSA3-C5's claims run a one-entry timetable, whose loop is in the completion wait - counted - and never in this pause.  Graded C with RSA3-C5: the stop must fall in a pause with nothing moving, and Start within the pause that follows.  **Older** (the loop's pause and the uncounted loop predate round 19): RSA3-C5's fix, incomplete. |
| **Names** | RSA3-C5, RSA2-C1, BR-C3, T3, OB-143, UXR-B7 |
| **Where** | `Layout.java:6845-7073` (the loop: `while (this.running && this.isCurrentLayout())` `:6860`, `this.pacedWait(ttp.getLoc())` `:7072`), `:6736-6753` (`pacedWait`: `loc.delay`), `:2123-2127` (`isRunning`), `:7051-7061` (the last entry's `stopLocomotives()`); `Locomotive.java:1182-1207` (`delay`: `Thread.sleep`); `TrainControlUI.java:27811-27865` (Graceful Stop's worker gives Start back once `isRunning()` is false), `:27722` (Start's worker asks `isAutonomyBusy()`), `:27598-27606` (the timetable worker's return greys Graceful Stop), `:24906-24916` (`isAutonomyBusy`); `core.testATrainIsDispatchedOnce.testAnEntryIsRunningUntilItHasLeft`, `testAnEntryTheYesFoundRefusingLeavesWithItsRun` (one entry each) |
| **Needs execution** | done - probes Q0, Q1 and Q2 (`rsa4ProbeQ0TheMainLoopAfterAGracefulStopControl`, `...Q1TheMainLoopJoinsTheNextRunAfterAGracefulStop`, `...Q2TheMainLoopJoinsTheNextRunAfterTheYes`): two entries (TA -> TB, then TC -> TD 5000 ms after it), delays 3 s, the stuck limit lowered to 4 s; entry 1 run and arrived by 440 ms; the stop at 1.2 s, with nothing moving.  In all three `isRunning()` read **false** at the stop and 300 ms on, **with the timetable's call alive**.  **Q0** (control: Graceful Stop, no Start): the call returned at 3,016 ms; entry 2 never sent.  **Q1** (Graceful Stop, then Start at 1,523 ms, the railway reading idle as its door asks): **entry 2 SENT at 6,188 ms** (`BR 03 1022`, a journey), and on its arrival **`isAutoRunning` true -> false at 6,933 ms** - the old timetable's last entry stopped the run started after it; the call returned at 7,087 ms.  **Q2** (the Yes, then Start at 1,510 ms): entry 2 refused three times (*"was not sent: the trains were stopped before it set off"*), then *"...has been unable to run for some time, so the timetable has been stopped.  The track it needs never became free."* - **the run started after the Yes stopped at 12,021 ms**. |

**Direction:** count the loop as the entries are counted, from its start until it reaches its completion wait (which asks `isRunning()` and so must not count itself), and pause it in `pauseUnlessStopped`, so that a stop ends it and the railway reads idle only once it has stopped dispatching.

---

### RSA4-C2 - The Yes and a timetable's start no longer share a lock, and the one order the round's reasoning leaves out starts the run after the Yes: the Yes clears `running`, the timetable sets it and reads the count, the Yes counts

| | |
|---|---|
| **Disposition** | Fixed - the Yes's two statements and a timetable starting's two are held by stopLock, which no journey takes, and inside which neither waits on anything but entryPause: a timetable starting sees the whole Yes or none of it.  The event thread waits on no journey (RSA3-C4 holds: MC4, the Yes taking the journeys' lock, red).  claims and fix 79e75e08; mutations MC2y red, MC2t red, MC4 red, MC4t red.  The claim holds the Yes between its two statements and starts a timetable meanwhile. |
| **Grade** | C.  RSA3-C4's fix takes the journeys' lock out of the Yes and has a timetable set `running` before it reads the count, clearing it again where the count has moved; the code's own account is *"a Yes before this line is seen by the question below, one after it clears what this set, and one between the two does both"*, and the Yes's javadoc says *"whichever comes first the run is not started"*.  Each of the three is right, and the fourth order is not among them: the Yes's first statement (`running` cleared, inside `stopLocomotives`) before the timetable's first, and the Yes's second (the count) after the timetable's second.  The run then starts, with `running` set after the Yes has returned.  Every journey it chooses carries the old count and is refused before its claim, so nothing moves; the parallel entry retries until the stuck limit (three minutes), the sequential one three times, then the run stops and logs *"The track it needs never became free"* - and until then the railway reads busy and the doors refuse.  The window is the Yes's two statements - microseconds, with a monitor between them now (`entryPause`) - against the timetable's two, so C; the harm is a run that stands for up to three minutes behind a log line naming the wrong cause, not a train moved.  The round's claim (`testATimetableTheCountRefusesLeavesNothingRunning`) asks only a Yes made wholly before the start.  **New in round 19.** |
| **Names** | RSA3-C4, RSA2-C1, RSA3-C5 |
| **Where** | `Layout.java:2191-2203` (the Yes: `stopLocomotives()` `:2198`, the count `:2200`; its javadoc `:2185-2189`), `:2323-2333` (`stopLocomotives`: `running` cleared, then the `entryPause` monitor), `:6804-6814` (the timetable: `running` set `:6807`, the count read `:6809`); `core.testATrainIsDispatchedOnce.testATimetableTheCountRefusesLeavesNothingRunning` |
| **Needs execution** | done - probe `rsa4ProbeQ3TheYesStraddlesATimetableStart` (Q3), held exactly: the Yes started while the probe held `entryPause`, so it blocked after clearing `running` and before counting (seen BLOCKED, count 0); the timetable started with the count read where it was chosen, and was held at the log line that follows its read of the count (*"Starting timetable execution from index 1"*); the Yes was then let go - it counted (1) and returned.  **After the Yes returned: `isAutoRunning` true, `isRunning` true**, the call alive; the entry refused five times (*"was not sent..."*), then *"...The track it needs never became free."* at the lowered 4 s limit (180 s in the application), and only then did the railway read idle.  The train never moved (highest speed 0). |

**Direction:** give the Yes's clear-and-count and the timetable's set-and-read a small lock of their own that no journey holds - the journeys' lock was the trouble, not the lock.  Not the two statements of the Yes swapped: autonomy's thread reads the count before its choice and `running` after, and counting first would let a thread that read the new count find `running` still set and dispatch after the Yes (RSA2-C2).

---

### RSA4-C3 - RSA3-C1's order - the claims before the journeys - reached three readers; three more read the same hand-over journeys-first, among them the route guard's list of active accessories and the tail rule's anchor for a train under way

| | |
|---|---|
| **Disposition** | Fixed - every reader of one train's path asks Layout.pathHeldBy, which reads the claims first: walkStandingTrains (the tail rule and the covered track), whereTheTrainIs, stationsAheadItHolds and railHeldByThisTrain; and getActiveAccs copies the claims and then the journeys, a journey still winning where both hold the train.  The claims use the round's hand-over hold, its maps now saying when they are read by get and size too.  claims and fix 79e75e08; mutations MC3a red, MC3p red, MC3w red, MC3t red.  Not run: the same revert at stationsAheadItHolds and railHeldByThisTrain, whose order changes no outcome at a hand-over: the first is asked only for a train under way whose last point another train stands on, which a train at its hand-over - not yet off its start - cannot have; the second only for the train asking, which is refused as already under way while it hands over (isAlreadyUnderway).  MC3p holds the rule they share. |
| **Grade** | C.  The hand-over writes the journey and then takes the claim away, outside the railway's monitor, and round 19 made the sensor rule, `isAlreadyUnderway` and `trainsUnderway` read the claims first.  `getActiveAccs` - what `MarklinRoute`'s guard asks before a route sets an accessory, and the reason it takes the union at all (RC-A10) - copies the journeys and then walks the claims, so a call across a train's hand-over sees it in neither and reports none of its route's switches: a sensor-triggered route may throw one on the path the train is about to run.  `walkStandingTrains` - the tail rule inside `isPathClear`, and the covered track - asks `activeLocomotives.get` and then `takingPath.get` for each train (*"THE PATH IT IS TAKING, WHICHEVER MAP HOLDS IT YET"*): across the hand-over it gets neither, anchors the tail at whichever reserved Point the map yields first - often the destination - and the track behind the train's start is claimed by nobody, so a second train's route over it is admitted.  `whereTheTrainIs` reads the same way for the tail overlay.  The windows are RSA3-C1's - two adjacent statements against two adjacent reads - so C; the consequences are A's.  **Older** (the unions are RC-A10's and VD12-B3's); round 19 fixed the order where RSA3-C1 named it and left these. |
| **Names** | RSA3-C1, RC-A10, VD12-B3, AU-A2, MT-438 |
| **Where** | `Layout.java:1029-1060` (`getActiveAccs`: `new LinkedHashMap<>(this.activeLocomotives)` `:1050`, then `takingPath` `:1052`), `:7939-7951` (`walkStandingTrains`: journeys, then claims), `:5252-5254` (`whereTheTrainIs`), `:9221-9234` (the hand-over: `activeLocomotives.put` `:9230`, then `takingPath.remove` `:9234`); `MarklinRoute.java:595` (the guard) |
| **Needs execution** | done - probes Q6 and Q7, with the round's own hand-over hold (X held at the first statement of its hand-over; the reader let past its first map only once the hand-over has finished).  **Q6** (`rsa4ProbeQ6TheRouteGuardAcrossAHandOver`; X's path over a switch whose echo the probe supplies): X held as a claim - the switch **reported**; across the hand-over - **0 accessories, the switch missing**; after it - reported.  **Q7** (`rsa4ProbeQ7TheTailRuleAcrossAHandOver`; X 150 long on PXA, its tail over `PW -> PXA` and `PV -> PW`; Y's road `PU -> PV -> PW`): at rest and with X held as a claim, Y **refused** (*"...standing across PV -> PW..."*); across the hand-over, Y's road **clear** (the error text printed beside it is the earlier one, `getLastError` not being cleared); after it, refused. |

**Direction:** the claims first in each - or one method that answers "the path this train holds", asked claims-first, for every reader.

---

### RSA4-C4 - The road a driven train's tail follows is stored as Point names, and the carry does not reach it: a station renamed drops the whole road of every train whose last route ran through it, and the tail falls back to stopping at the first fork

| | |
|---|---|
| **Disposition** | Fixed - each standing train's road is carried with the timetable (AutonomySession.legsOf): its steps are pairs of Point names like a timetable's legs, and go the same way, in every configuration.  claims and fix 79e75e08; mutation MC4r red. |
| **Grade** | C.  WK7-B1 gave a driven train a road - `arrivedAlong`, `[start, end]` Point-name pairs saved with its placement - so its tail follows the route it came in on through a junction (MT-335) rather than stopping at the fork.  `Layout.roadNamed` drops a road WHOLE where any pair names an edge the build lacks - *"a square re-split, a point renamed"* - and the walk then keeps the fork rule, *"which claims less"*.  A station renamed renames the Points in every stored road that ran through it, anywhere along it - the road is the train's whole last route - so the rebuild's load drops it and the next fold writes the placement without it.  The timetable's legs are carried across the same rename; the roads beside them are not.  The consequence is MT-335's: the covered track stops at the fork behind the train, and a second train can be sent over the rail the first one's body lies across beyond it.  Graded C: it needs a standing train whose body reaches past a fork behind it and whose last route ran through the station renamed.  Round 18's split does the same to a road that set off from a square nothing arrives at (none on Adam's railway).  **Older**, exposed by round 19's question. |
| **Names** | WK7-B1, MT-335, TLV-B1, RSA3-B1, RLV7-C3 |
| **Where** | `Layout.java:7503-7553` (`roadNamed`: all or nothing), `:14034-14045` (the load applies it); `Point.java:1394-1406` (the road written by name); `AutonomySession.java:8252-8265` (`setArrivedAlong`), `:5716-5722` (the fold removes a road the running layout no longer has); `TrainControlUI.java:6708-6716` (`putTheRoadBack`, by name) |
| **Needs execution** | done - probe `rsa4ProbeQ5ARenameDropsADrivenTrainsRoad` (Q5): a train standing at Gamma with side W and the road `[["Beta (eastbound)","Gamma"]]`, and a captured leg `Beta (eastbound) -> Gamma`; loaded - a road of 1 rail, 1 entry.  Beta renamed Bravo: the leg carried (`Bravo (eastbound) -> Gamma`), **the road not** (`[["Beta (eastbound)","Gamma"]]`); loaded - the train at Gamma with **road null**, 1 timetable entry; after the next fold the stored road **null** (side W kept). |

**Direction:** carry the stored roads with the timetable - the same tracing by square, over every configuration's `arrivedAlong` - or keep the part of a road the build still has.

---

### RSA4-C5 - Import is never greyed, and into a configuration not in use it writes the setup's shared half - names among it - while trains run; the reload it then asks may be declined, and nothing marks the setup newer, so the next fold reads the running railway through names the import changed

| | |
|---|---|
| **Disposition** | Fixed - Import is refused while autonomy runs, into any configuration, before it asks for a file - as Delete is, on Adam's "There should be no setup edit possible during a run" - and autosetup.ui.errorImportWhileRunning says what to do instead.  The refusal into the configuration in use alone, and its key, are retired.  RLD-C3's claim - an import while trains moved, its reload declined - is replaced by this one: that reload is no longer reached from Import.  claims and fix 79e75e08; mutation MC5 red. |
| **Grade** | C.  *"There should be no setup edit possible during a run"* (Adam, 28 September), and MT-141 names *"the autonomy config"*.  The Import item is *"never greyed"*; into the configuration in use it refuses a bundle, and an old file while autonomy runs - but into ANOTHER configuration it proceeds while trains run.  A bundle fills the gaps of the setup's shared half (`importBundle`: *"only the gaps are filled"* - a name for a station that has none, a length, a direction); an old autonomy.json does the same through `importLegacy` (names, stations, one-way running where none is set), and may shut pages.  Both save, and then reload the configuration RUNNING through `prepareAutonomyReload`, whose No keeps the run.  That door raises no `setupEditDeclinedDuringRun`, so each fold after it - Unload, the exit, a reload - folds the running layout through the new naming: a train standing on a square whose Point the import renamed is not found by name, so a train the run moved ONTO such a square is written nowhere, and one it moved OFF one stays recorded there as well as where it is - one train in two places, which refuses the whole configuration (MT-135).  **Reach:** the import must fill a gap that renames a Point - a station with no name of its own, or a square's copies changed by a direction - and Adam's stations are all named and his directions set.  **Older** (the import doors of RLA to RLU); found as the brief's *"import ... where they reach the railway"*. |
| **Names** | RLA2-B1, RLU4-C1, RLD4-C3, RLV6-B1, RLV6-C1, MT-141, MT-135, OB-183 |
| **Where** | `AutonomyMenu.java:355-369` (*"Import is never greyed"*); `AutonomyViewerPanel.java:1195-1218` (the refusals: only into the configuration in use), `:1265-1287` (a bundle: `importBundle`, save, `loadAfterImport`), `:1392`, `:1438-1453` (an old file: pages shut, `importLegacy`, save), `:697-724`, `:769-777` (the reload, declinable); `AutonomyCompanionStore.java:2095` (`importBundle`: the gaps filled); `TrainControlUI.java:7033-7074` (the flag, raised only by the rebuild door), `:2558-2567` (the exit's fold asks it); `AutonomySession.java:5606-5612` (the capture skips a Point name its naming lacks) |
| **Needs execution** | done - probe `rsa4ProbeQ8AnImportDuringARunRenamesUnderTheFold` (Q8): Alpha, a middle station with no name of its own (`main 3,1 (eastbound)` and `(westbound)`), Gamma; the train placed at Alpha and loaded, and the run's result set on the running layout (the train on `main 3,1 (eastbound)`).  **Control** (no import): the fold records it at `main:3,1`.  **A bundle of the same railway, its middle station named Beta, imported into another configuration** (`importBundle`, as the door allows while trains run): 1 shared entry filled, the middle station now called Beta, the configuration running still the active one; the fold records the train **NOWHERE**. |

**Direction:** refuse the import while autonomy runs, as the Pages tick and Delete are refused - or raise the flag when an import writes the shared half during a run.

---

### RSA4-C6 - open-questions.md's decided entry for RSA2-B1 still states the rule round 19 replaced: "once a journey has passed a sensor it waits on it no more"

| | |
|---|---|
| **Disposition** | Fixed - open-questions.md's decided entry for RSA2-B1 states the rule round 19 built: the sensor held until the rail to it is given back.  behaviour.md writes it (5d), and the Yes, the carry of Point names and the fold's kept entries (8), and Import refused while autonomy runs.  claims and fix 79e75e08. |
| **Grade** | C.  The written rule for a sensor another journey awaits lives only in `open-questions.md` - behaviour.md says nothing of it, nor of the Yes.  Its decided entry reads *"once a journey has passed a sensor it waits on it no more, and the lock and release rules decide the track as before"*: the reading RSA3-C3 disputed, and which round 19 replaced in the code with a sensor held until the rail to it is given back (early in non-atomic mode, at the journey's end in atomic mode).  Round 19's record commit changed that file's counts paragraph only.  **New in round 19.** |
| **Names** | RSA2-B1, RSA3-C3 |
| **Where** | `docs/reference/open-questions.md:247-251`; `Layout.java:2216-2267` (`whoseJourneyAwaits`) |
| **Needs execution** | no - by reading |

**Direction:** restate the decided entry as built, with Adam's words, and give the rule - and the Yes - a paragraph in behaviour.md.

---

### RSA4-D1 - RSA3-C1 and C2: the claims read before the journeys in the three readers named, and the claim half of the sensor rule claimed with a real hold

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RSA4-C3 (the readers not named). |
| **Grade** | D |
| **Names** | RSA3-C1, RSA3-C2, AUT-C1 |
| **Where** | `Layout.java:2226-2267`, `:2879-2885`, `:2895-2907`, `:9221-9234`; `core.testATrainIsDispatchedOnce.testTheSensorRuleSeesATrainHandingOver`, `testATrainHandingOverIsStillUnderWay`, `testATrainHandingOverIsCounted`, `testARouteMayNotWaitOnASensorAClaimAwaits` |
| **Needs execution** | done - the round's claims green on the archive (baseline); the rest by reading |

The hand-over writes the journey (with fresh milestone, cleared and released sets, all put before it) and then takes the claim away; each of the three readers now reads the claims and then the journeys, so a train missing from the first read is present in the second - `ConcurrentHashMap`'s iteration is weakly consistent, and the put happens before the remove.  The claims hold the train at the hand-over's first statement and let the reader's second read go on only once the hand-over has finished, which is the interleaving RSA3-C1 described; the claim-half claim asks with the train held as a claim.  A claim is added only under the railway's monitor, which the readers inside `isPathClear` hold, so the other transition needs no order.

---

### RSA4-D2 - RSA3-C3: a sensor held until the rail to it is given back

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RSA4-C6 (the decided entry). |
| **Grade** | D |
| **Names** | RSA3-C3, RSA2-B1, GUI-A1, WK-B1 |
| **Where** | `Layout.java:2243-2264`, `:9531-9577`, `:9226-9230`, `:9887-9890`, `:9027` |
| **Needs execution** | done - the round's claims green on the archive (baseline); the rest by reading |

`releasedEarly` records an edge only in non-atomic mode and only behind `tailHasProvablyPassed`, the proof that unlocks it; the edge whose END is the sensor's Point is given back once the tail has passed that Point, so the sensor is free exactly when the metal is.  In atomic mode nothing is recorded and the sensor is held to the journey's end.  Each journey's set is new at its hand-over, made before the journey is visible, and removed at both of its ends; a reader that finds the journey without its milestones reads the sensor as held.  Every change is in the refusing direction.

---

### RSA4-D3 - RSA3-C4: the Yes takes no lock the event thread can wait on

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RSA4-C2 (the order the reasoning leaves out). |
| **Grade** | D |
| **Names** | RSA3-C4, RSA2-C1, RSA2-C2 |
| **Where** | `Layout.java:2191-2203`, `:2323-2357`, `:6804-6814`, `:9214-9219`, `:2279-2290` |
| **Needs execution** | done - the round's claims green on the archive (baseline); the rest by reading |

The Yes clears `running`, notifies the entry pauses, counts, and stops every train under way, taking only `entryPause`, which is held to notify or to begin a wait and never across another lock; so RSA3-C4's freeze behind an arrival waiting for the railway's monitor is gone.  A journey that passed its count check before the Yes and hands over after it is refused its speed by `drive`; one claiming is given back unrun.  Autonomy's thread reads the count before its choice and `running` after, which the Yes's order serves.

---

### RSA4-D4 - RSA3-C5: a timetable's entry threads counted while they live, and their pauses woken by every stop

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RSA4-C1 (the loop that starts them). |
| **Grade** | D |
| **Names** | RSA3-C5, T3, SG-A5 |
| **Where** | `Layout.java:589-596`, `:2123-2127`, `:2323-2373`, `:6891-6894`, `:6999-7011`, `:7062-7066`, `:5093-5171` |
| **Needs execution** | done - the round's claims green on the archive (baseline); the rest by reading |

Each entry is counted before its thread starts and uncounted in that thread's `finally`; its pause asks `running` under the monitor it waits on, and a stop clears `running` before it takes that monitor, so no wake is lost.  An entry of a stopped run cannot wake into another run, because the doors that start one ask `isRunning()`, which counts it.  The other pauses a run makes - autonomy's delays, its no-path idle, the yield's bounded wait - are on threads `locomotiveThreads` counts; `pacedWaitMillis` keeps `pacedWait`'s arithmetic.

---

### RSA4-D5 - RSA3-C6: a copy of a square nothing arrives at is no arrival, for every reader

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RSA3-C6, RSA2-C7, SA-B1, OB-282 |
| **Where** | `AutonomyBuilder.java:84-105`, `:450-465`, `:666-676`, `:756-790`, `:814-890`, `:1077`, `:1275-1286`, `:1578-1590`; `AutonomySession.java:280-305`; `HomeStaging.java:2228-2297` |
| **Needs execution** | done - the round's claims green on the archive (baseline); the rest by reading |

Only `nodesFor`'s split of a square nothing arrives at marks its copies.  `arrivalAllowed` reads no bar against them, so the station flag, `placementCopy`, `startableCopy` and `homeCopy` agree; `homeFacingsAt` offers none of their facings and no home is held to one, so HomeStaging's home there is the square - and a train away from such a home is IMPOSSIBLE, correctly, since nothing reaches it.  The editor offers bars only on arrival sides (none here), and the save drops a bar whose side has gone.

---

### RSA4-D6 - RSA3-B1 in the configuration running: renames, round 18's split, a copy more or fewer, the editor's Cancel and undo

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RSA4-A1, B1, B2 and C4 (the readers and configurations it does not reach). |
| **Grade** | D |
| **Names** | RSA3-B1, ISD-B2, RSA2-C7 |
| **Where** | `AutonomySession.java:427-631`, `:5072-5086`; `AutonomyCompanionStore.java:1964-1967`, `:2463-2501` |
| **Needs execution** | done - the round's claims green on the archive (baseline); the rest by reading |

The carry touches only a name the build does not have; a name this session built is traced by the square it stood for, one written before it by the square still called by its base, and a leg is moved to the same heading under the new name, or else to the one copy every leg through it can run along.  `getConfiguration` hands back the live object, so the load and the capture read what the carry wrote.  The editor's Cancel restores a deep-copied snapshot and rebuilds, and an undo's rebuild re-traces the names it restores.  It runs in `deriveStationIndex`, on the event thread where every caller is.

---

### RSA4-D7 - Round 19's notes beside the fixes: the guard window and the wrench

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | MT-505, MT-588, MT-591, MT-593 |
| **Where** | `AutonomyEditorPanel.java:7432-7464`; `TrainControlUI.java:28359-28420` |
| **Needs execution** | no - by reading |

A square that is not a signal, clicked while a guard is chosen, now disarms the choice and reopens the guard window; the editor cannot be open during a run, and the pairing is written only by a signal clicked.  The wrench is drawn.  Neither reaches the railway.

---

### RSA4-D8 - HomeStaging's search: the pre-scan, the greedy pass, A* in its budget shares, and the route search

| | |
|---|---|
| **Disposition** | Checked - clean, as far as read. |
| **Grade** | D |
| **Names** | OB-228, OB-230, OB-294, OB-295, AMH-C1, SG-A2, SPEC-B3 |
| **Where** | `HomeStaging.java:455-673`, `:816-1112`, `:1130-1490`, `:2204-2297` |
| **Needs execution** | no - by reading |

`plan` proves IMPOSSIBLE only from the stateless rules and a shared detection section, and otherwise searches; each search returns a whole plan from where it starts; `firstClearRoute` searches by edge name, refuses an inactive or non-station origin and a lap onto its own square, and asks the planned occupancy for FR-001, the tails of trains moved and not, the train's own tail, and room where it stops or turns.  A plan runs one leg at a time through `executePath`, so the runtime asks `isPathClear` of every leg - a planner mistake is a refused leg, not a train on occupied track.

---

### RSA4-D9 - The timetable's doors and the page doors ask whether autonomy is busy

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RSA4-C1 (the gap in what "busy" reads) and C5 (Import). |
| **Grade** | D |
| **Names** | OB-101, UXR-C19 |
| **Where** | `TrainControlUI.java:27883-27995`, `:22773-22800`; `AutonomyMenu.java:741-817`; `TrainControlUI.java:4149-4156` |
| **Needs execution** | no - by reading |

Delete, delay, restart and clear refuse while `isAutonomyBusy()`; the Autonomy menu's Pages tick refuses while busy.  The configuration panel's own Pages button and Manage menu have no busy check, and are unreachable: the panel is *"built but not shown"*.

---

### RSA4-D10 - The probes' controls, and the baseline

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | - |
| **Where** | `validate30/rsa4/p1/test/regression/rsa4Probes.java`; `validate30/rsa4/p1/rsa4-out/`; `validate30/rsa4/logs/` |
| **Needs execution** | done |

Each probe states its precondition before its result, and each measured defect has a control beside it that answers the other way: Q0 (no Start: the call returns and entry 2 is never sent) for Q1 and Q2; the Yes seen BLOCKED at its count and the timetable seen past its read for Q3; the same session for Q4; the timetable leg carried beside the road for Q5; X held as a claim and after its hand-over for Q6 and Q7; no import for Q8; both entries loaded before the tick for Q9; a gesture that renames nothing for Q10.  The baseline: the round's fifteen claims, 15 run, 0 failures; the probe jobs 11, 2 and 2 run, 0 failures; every job exited 0 but the first (which did not compile and ran nothing), and none hung, ran out of memory or was ended by the runner.
