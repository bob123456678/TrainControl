# RSA3 - Release validation, rounds 17 and 18: a captured timetable names Points, and a rename - a station's in the editor, or round 18's of a square nothing arrives at - drops the entries through it and the next save erases them; the sensor rule reads the journeys before the claims, frees a sensor at the head rather than the unlock, and has an unclaimed half; the Yes now waits on the journeys' lock on the event thread (TrainControl 3.0.0)

**Status:** open

**Prefix:** RSA3

**Reviewed:** branch `autonomy-diagram-r0` at `d851714c`, read and run from `git archive d851714c` unpacked into `validate30/rsa3/a851/` (reading, and the baseline), a copy of it, `validate30/rsa3/p1/`, with two scratch probe classes added (`test/regression/rsa3Probes.java`, `test/regression/rsa3LiveProbes.java`, not in the battery), and a mutant copy, `validate30/rsa3/p2/` (p1 with the one mutation M1 below); `cs2_sample_layout` excluded and an empty folder of that name made for the runner; `LocDB.data` and `UIState.data` beside each root are copies from TC30.  TC30's working tree and the main checkout's were never read; git was used only as `git -C tc-30 log / diff / archive / ls-tree`.  Rounds 17 and 18 are `6e0bc2b8..d851714c` (fifteen commits: round 17's claims `02c4d0bc`, fixes `4824d40c`, the sharpened claims `18602a71`, the passed-sensor rule `3a8ff7c0`, RSA-C7's claim and fix `8a6c6f9a`/`daa52445`; round 18's batch and RSA2-C7 `240d5948`, its claim `c7a031ae`; records and the tracker).  2026-09-29.

## Method

**Rounds 17 and 18, read whole:** the brief; `validate30/RSA2.md`; the rounds' source diff (`Layout`, `Locomotive`, `AutoLocomotiveStatus`, `LayoutRightclickAutonomyMenu`, `TrainControlUI`, `AutonomyBuilder`, `AutonomyEditorPanel`, `AutonomyMenu`, `LocomotivePlaceholder`, the eight message files) and each changed method in its context - `stopEveryTrainWhereItIs`, `stopsOrdered`, `whoseJourneyAwaits` and its call in `isPathClear` (read whole), `drive`, `giveBackUnrun`, `runLocomotives`, `runLocomotive`, `executeTimetable(int)`/`executeTimetableInternal` with its entry threads, `pacedWait` and completion wait, `executePath` and `executePathInternal` whole (the count, the claim, the hand-over from claim to journey, every speed write and wait, the milestones, the fence, the arrival), `configureAndLockPath`, `isAlreadyUnderway`, `trainsUnderway`, `unlockPath`'s callers under the journeys' lock; `Locomotive`'s waits and `wakeEveryWait`; in the window `prepareAutonomyReload`, `sendATrainByHand`, `requestReturnToHome` whole, the Execute Timetable handler and worker, Start's worker, `checkAutoLayoutLatency` and the ping timer, `WindowClosed` and `saveState`'s capture condition, `repaintAutoLocList`, `repaintTimetable`, `rebuildRunningLayoutFromSetup`, `refreshCoveredTrack`'s railway, `resetAutonomySession`, the layout-folder chooser; `AutoLocomotiveStatus.updateState`; `LayoutRightclickAutonomyMenu.showFor` and `gatherPathOptions`; `AutonomyMenu`'s offer; the guard popups; round 18's `nodesFor`, `sidesBehindTheWaysOut`, and every reader of a copy - `splitSides`/`arrivalSidesOf`, `leavesBy`/`arrivesBy`, `blockFor`, `onwardFrom`, `facingOf`, `placementCopy`, `startableCopy`, `homeCopy`, `homeFacingsAt`, `arrivalAllowed`, `nodeName`, `baseNames`, `facingByName`, `tilesByName`, `build`'s point emission (station, block, terminus, copyFacing, home); `AutonomySession.facingChoices`, `facingsFor`, `placeableFacingsFor`, `departableFacingsFor`, `copyFacing`, `moveOntoFacingCopy`'s guard, `getBarredArrivals`, `barredArrivals`, `shutStations`, `badCopies`, `setPointName`, `setGlobal`, `capture`; `StationIndex.arrivalSidesAt`; `AutonomyChecks`' copy checks; `TimetablePath.fromJSON` and `Layout.fromJSON`'s timetable and road restore; `AutonomyCompanionStore.setPointName`, `repairLocomotiveIn`/`repairLocomotiveInTimetable`, `reconcile`; the carry (`carryTheTrainsAcross`, `whereTheTrainsAre`, `putTheTrainsBack`) and `AutonomyViewerPanel.load`/`loadPrepared`; the round's claims and the dispositions in `docs/reviews-2026-09-25/RSA2.md` and `RSA.md` (RSA-C7), and the decided entries in `open-questions.md`.

**What RSA2 did not reach, read lightly:** `HomeStaging` - `search` and its three budgets, `canEnter` (the shared-sensor rule), `blockedSensors`, `atHome`, `copiesOf`/`homeCopiesOf`; `TileGraph`'s constructor checks (scissors, turntables, permanent turnouts, a switch with no address) and `getRoutes`; the import door's busy guards in `AutonomyViewerPanel`.  **Not reached:** `HomeStaging`'s A* and route search in detail (`astar`, `firstClearRoute`, the heuristics); the rest of `AutonomySession` and `AutonomyEditorPanel`; `AutonomyCompanionStore` beyond names, bars and the timetable repair; import and export beyond their doors' guards; the timetable UI; `CS2File`; round 18's picture, wrench, crop and layout-folder work beyond where it meets the railway.  Every finding was searched for in `docs/manual-tests/findings.tsv` (4,946 rows) and in the store behind it (`triage.db`, read-only) before it was written.

**Runs.**  Every JVM through the archive's own `docs/tools/one.sh`, `TC_SCRATCH` a Windows-form folder per job under `rsa3/scratch`, started by a queue (`rsa3/runjob.sh`) only once no `java.exe`, `javaw.exe` or `javac.exe` had been seen and no battery lock existed for two checks 20 s apart, deciding from that attempt's own log.  The coordinator's battery held the machine until about 19:46; everything below ran 19:47-19:51, one job at a time.

- **Probes** (p1, `regression.rsa3Probes`, 8 run, 0 failures; `regression.rsa3LiveProbes`, 1 run, 0 failures - they assert nothing, and each writes what it saw to `p1/rsa3-out/`; simulated network, feedback set by the probe): Q1 `rsa3ProbeTheHandOverFromClaimToJourneyIsMissed` (RSA3-C1, with Q2's control inside it, RSA3-C2), Q3 `rsa3ProbeTheSensorIsFreedAtTheHeadNotAtTheUnlock` (C3), Q4 `rsa3ProbeTheYesWaitsOutAnotherTrainsSwitches` (C4), Q5 `rsa3ProbeARenamedStartDropsATimetableEntry` (B1), Q6 `rsa3ProbeTheLiveSnapshotsSquaresNothingArrivesAt` (B1's reach, on `test/layouts/live-snapshot` through `LayoutSandbox`), Q7 `rsa3ProbeAStaleBarredArrivalShutsACopy` and Q8 `rsa3ProbeAHomeFacingOnASquareNothingArrivesAt` (C6), Q9 `rsa3ProbeAnEntryFromARunTheYesEndedStopsTheNextRun` (C5), Q10 `rsa3ProbeARenamedStationDropsATimetableEntry` (B1).  Q1 holds its interleaving exactly by swapping three of the railway's maps for subclasses by reflection (a hand-over held at its first statement; a check held once between its two walks) - standing in for two threads descheduled there, and changing nothing the code under test does.  Q10's first fixture chose a route out of a dead-end station, which has no way out; it was corrected and run again alone (`probes2`, 1 run, 0 failures).
- **Baseline** (a851): `core.testATrainIsDispatchedOnce` 28 run, 0 failures; `regression.testNothingOnTheEventThreadTakesTheRailwaysMonitor` 5 run, 0 failures; and the rounds' other claims `core.testAutonomyDiagramSession.testASquareNothingArrivesAtFacesItsPlacedTrain`, `...testADoubleCurveNothingArrivesAtFacesItsPlacedTrain`, `...testTheDiagramsGatherAsksForTheRailwayOnce`, `regression.testTheRefusalsAreAskedAtTheDoors.testTheDoorsCarryTheStopsCountedWhereTheyChose`, `core.testAStopRouteStandsAlone.testTheLatencyCutOffIsSentWhateverThePowerFlagSays`, 5 run, 0 failures.
- **Mutant M1** (p2: the claims loop of `whoseJourneyAwaits` disabled, `if (false) for (...takingPath...)`, and nothing else): `core.testATrainIsDispatchedOnce` 28 run, 0 failures, and Q1 on the mutant, 1 run, 0 failures - the round's claims green on the mutant, and Q2 reporting the second train admitted where the archive refuses it (RSA3-C2).

**Nothing written** outside `validate30/rsa3/` and this file.  The runner takes the shared lock under `%TEMP%` and a preference node of its own, and removes both; the two nodes under `HKCU\Software\JavaSoft\Prefs\traincontrol-test-runs` (`battery-680`, `one-620`) are the two RSA and RSA2 found - not mine, not touched.  No git state changed; no commit.  The real `LocDB.data` and `UIState.data` - the main checkout's and `C:/Users/adamo`'s - were copied and hashed at 18:36, before the first JVM, and compared byte for byte after the last: unchanged (`6ef56939...`, `9e32894e...`, `662c188b...`, `f12a462c...`).  Nothing of mine is running: every queue and waiter has ended, no `java.exe`, `javaw.exe` or `javac.exe` is left, and the battery lock is gone.

**Counts:** 0 A, 1 B, 6 C, 8 D.

**Rounds 17 and 18's fixes are right where they were measured** (RSA3-D1 to D6): every door that chooses a journey carries the count from where it chose, the Yes clears `running` before it counts under the lock a timetable takes to start, a claim completed after it is given back and says so, the turn's resume gives up and a retirement wakes it, the Auto tab hides its paths while a staging flow owns the railway, the gather asks one railway, the latency cut-off is sent whatever the flag reads, closing warns and leaves the trains alone, and a square nothing arrives at stands its placed train on the copy it faces.  The sensor rule refuses a route to a sensor another journey awaits at every door, and HomeStaging keeps the same pairs apart by its own rule.  **What they leave:** the rule reads the journeys before the claims while the hand-over writes them the other way round, so a check across another train's hand-over sees it in neither (RSA3-C1, measured); its claim half - the half a real railway leans on while switches are confirmed - is in no claim (C2, measured on M1); it frees a sensor when the head reaches it, where Adam's condition says once unlocked (C3, measured); and the Yes's new lock puts the event thread behind an arrival that is waiting for another train's switches, with every train running on meanwhile (C4, measured: 2.2 s).  Round 18's copies record an arrival side nothing arrives by, and two readers take it for one (C6, measured).  **Older, exposed:** a captured timetable names Points, so a station renamed in the editor - ISD-B2's station half, never repaired - and now round 18's renaming of a square nothing arrives at drop the entries through it at the next load, and the next capture erases them (RSA3-B1, measured; none of the round-18 squares are on Adam's live snapshot); and a timetable entry that was refusing when the Yes came outlives its run and stops the next one (C5, measured).  **New in round 17:** C1, C2, C3, C4.  **New in round 18:** C6, and B1's second door.  **Older:** B1, C5.

---

### RSA3-B1 - A captured timetable names Points, and nothing carries a rename into it: a station renamed in the editor - and, since round 18, a square nothing arrives at, with no edit at all - drops every entry through it at the next load, and the next save erases them

| | |
|---|---|
| **Disposition** | Fixed - a captured timetable is carried to the names each build gives its Points (AutonomySession.carryTheTimetableAcross, wherever the station index is derived - every edit that can rename a Point, a rebuild or not): a name the build no longer has is traced to its square - by the builds this session has seen, or, for one written before it, as that square's own name with or without a heading - and then to the same copy under the square's name now, or, where that copy is gone, to the one copy every leg through it runs along.  So a station renamed in the editor, a square given more copies or fewer by a direction edit, and round 18's split keep their legs; a name that cannot be traced - its square gone from the diagram - is left, and the load drops that entry and logs it as before.  Not built: saying on screen that a load dropped an entry.  claims and fix 116bec78 and 1fb38e7b; mutations MB1 red, MB1s red, MB1n red, MB1g red.  The claims: a station renamed, with a leg only the heading can carry (a turning copy beside a plain one to the same place); and round 18's split read by a fresh session, as the first start after the upgrade reads it. |
| **Grade** | B.  The consequence is a user's data lost - legs of a timetable captured by driving the railway - on an ordinary edit, with one log line.  **How:** a captured timetable is kept in the configuration's `globals.timetable`, and every leg names its two Points (`{"start": "Beta (eastbound)", "end": "Gamma"}`); `TimetablePath.fromJSON` finds each by name, and an entry naming an edge that does not exist is dropped, one line in the log (BPV-C12's fix: the one entry, not the list).  A station's Points are named after the square - its name, plus a heading where the square is more than one copy - so renaming a station in the editor's Name item renames every Point of the square, and `setPointName` writes the name and nothing else.  The idle rebuild then loads without a fold (`load(active, false, false)`), so the stored entries are read against the new names and every entry with a leg through the renamed station is dropped; the next capture - the exit's save, a reload's fold, Unload's fold - writes the running layout's shorter timetable over the configuration, and the entries are gone.  A locomotive rename is carried into the same stored timetable (`repairLocomotiveInTimetable`, OB-069), and that method's own note says the other half: *"The entries also name POINTS, which a station rename breaks in the same way - that half is not repaired here, and is survivable now only because the loader drops the one entry rather than the list."*  **Round 18 adds a second door with no edit at all:** a square nothing arrives at, with more than one way out, was one Point called by its base name and is now one copy per way out, named with headings, so an entry starting there - the only way such a square appears in an entry, nothing arriving there - is dropped at the first load under round 18.  The same happens wherever a direction edit changes how many copies a square has (one copy keeps the base name, two take headings), an older door again.  **Reach:** Adam's live snapshot has no square round 18 splits by its ways out (Q6: 87 Points, 0), and its one timetable entry loads whole - so the upgrade itself costs his railway nothing today; a rename of any station a captured leg passes does.  **Older** (new since v2.7.4: the editor's Name item and the captured timetable in the configuration): ISD-B2 found it on 2026-08-24 as the station half of *"the fourth holder"*, fixed the locomotive half, and left this one open; the store closed ISD-B2 in the bulk closure of 2026-09-08 (*"anything still true will be found again by a test or a reader"*) - it is still true.  Graded B as the earlier timetable losses were (RLV8-B1, RLV9-B1). |
| **Names** | ISD-B2, OB-069, MT-149, BPV-C12, BPV-D3, RLV8-B1, RLV9-B1, RSA2-C7, UR-16, RLV7-C3 |
| **Where** | `TimetablePath.java:179-226` (`fromJSON`: every leg by `layout.getEdge(start, end)`); `Layout.java:13787-13830` (each entry that cannot be read dropped, `autolayout.warnTimetableEntry`); `AutonomySession.java:5354`, `:5640-5650` (the capture writes the running layout's timetable into `globals`); `AutonomyCompanionStore.java:1068-1078` (`setPointName`: the name only), `:1736-1805` (the locomotive half repaired, and the note on the station half); `AutonomyEditorPanel.java:6102-6131` (the Name item); `TrainControlUI.java:7030-7075` (`rebuildRunningLayoutFromSetup`: loads without a fold); `AutonomyBuilder.java:631-667` (`nodesFor`, round 18's copies), `:944-970` (`nodeName`: base name for one copy, a heading for two) |
| **Needs execution** | done - probe `rsa3ProbeARenamedStationDropsATimetableEntry` (Q10): stations Alpha, Beta, Gamma in a row, a stored entry `Beta (eastbound) -> Gamma`; before any rename the entry **loads** (1); Beta renamed Bravo, rebuilt, loaded: **0 entries**, logged *"A timetable entry could not be loaded and was skipped: Edge Beta (eastbound) -> Gamma does not exist"*; after the capture the stored timetable is **`[]`**.  Probe `rsa3ProbeARenamedStartDropsATimetableEntry` (Q5): the round's own straight, the middle square one-way away both sides, built as `main 3,1 (eastbound)` and `main 3,1 (westbound)`; a stored entry as a build before round 18 wrote it (`main 3,1 -> main 5,1`, the whole square's base name, which RSA2's P4 measured at `6e0bc2b8`) beside the same leg in this build's names: **1 of 2 loaded**, the old one skipped (*"Edge main 3,1 -> main 5,1 does not exist"*); after the capture only the new-named entry is stored.  Q6: the live snapshot builds 87 Points, **0** of them copies of a square nothing arrives at, and loads its 1 timetable entry. |

**Direction:** carry a rename into `globals.timetable` as a locomotive rename is carried - before the rebuild, map each stored leg's Point to its square and copy through the builder that named it, and write the names the new build gives - or store a leg by square and heading rather than by Point name; and let a load that drops an entry say so where the operator will see it, not only in the log.

---

### RSA3-C1 - The sensor rule reads the journeys under way before the claims, and a hand-over from claim to journey writes the journey before it takes the claim away: a check across another train's hand-over sees it in neither, and RSA2-B1's two trains on one sensor come back

| | |
|---|---|
| **Disposition** | Fixed - the sensor rule, isAlreadyUnderway and trainsUnderway read the claims before the journeys: the hand-over writes the journey and then takes the claim away, so a train missing from the claims is among the journeys read after them.  The claims hold a train at the first statement of its hand-over and let each check's second read go on only once the hand-over has finished.  claims and fix 116bec78 and 1fb38e7b; mutations MC1j red, MC1u red, MC1c red. |
| **Grade** | C.  `whoseJourneyAwaits` walks `activeLocomotives` and then `takingPath`.  A journey's hand-over - after `configureAndLockPath` has returned, under the journeys' lock and not the railway's monitor - puts the train into `activeLocomotives` and then removes it from `takingPath`.  Another train's `isPathClear` runs under the railway's monitor, which the first train no longer holds (on a real railway it left it before `validatePathActuation`'s wait; in simulation, at once), so the check and the hand-over interleave: the walk of the journeys ends before the put, the walk of the claims begins after the remove, and the first train is in neither.  The second train is admitted to the other place on the first train's sensor, and the first arrival ends both journeys - RSA2-B1's outcome: the second recorded as arrived while it has not, its route handed back under it.  The window is the few instructions between the two walks against two adjacent writes - microseconds - so C; the consequence is A's.  The same order is read by `isAlreadyUnderway` (one train dispatched twice across its own hand-over, AUT-C1's family) and `trainsUnderway` (the cap undercounted by one), both older.  **New in round 17** for the sensor rule. |
| **Names** | RSA2-B1, AUT-C1, TDY2-C6, OB-238 |
| **Where** | `Layout.java:2208-2239` (`whoseJourneyAwaits`: journeys `:2212-2225`, then claims `:2227-2236`), `:2918-2926` (its call in `isPathClear`), `:4195-4221` (the check and the claim, under the monitor), `:9134-9148` (the hand-over: `activeLocomotives.put` `:9143`, then `takingPath.remove` `:9147`, under the journeys' lock), `:2803-2806` (`isAlreadyUnderway`), `:2816-2824` (`trainsUnderway`) |
| **Needs execution** | done - probe `rsa3ProbeTheHandOverFromClaimToJourneyIsMissed` (Q1), RSA2's crossroads (feedback double curve D on s88 40; X from (3,1) to the north arm, Y from (5,3) over a crossing to the east arm), built through `AutonomySession`, three maps swapped by reflection for subclasses that hold X at the first statement of its hand-over and hold Y's check once, inside `whoseJourneyAwaits`, as its walk of the journeys ends. |

| step | what the railway says |
|---|---|
| control, nothing held: X sent | Y's road to the east arm **refused**: *"Feedback 40 is still awaited by 02 0314-1 DDR"* |
| the race: X claimed, held at its hand-over | X a claim, not a journey; its rail run over |
| Y's dispatch; its check held between its two walks while X's hand-over completes | the hook fired once; X's claim was taken away during Y's check |
| both | **X and Y both under way** (speed 15 each, both journeys) |
| sensor 40 occupied once - X reaching its arm | X's journey ended at `main 3,3 (southbound)`; **Y's journey ended too**, at `main 3,3 (westbound)`, speed 0, thread gone, its rail neither run over nor held - Y never having moved |

**Direction:** read the claims first and the journeys second - the hand-over writes the journey before it removes the claim, so a train missing from the claims is then among the journeys - and the same order in `isAlreadyUnderway` and `trainsUnderway`.

---

### RSA3-C2 - No claim reaches the claim half of the sensor rule, and that is the half a real railway leans on while a route's switches are confirmed: the round's claims pass with it taken out

| | |
|---|---|
| **Disposition** | Fixed - claim: the first train held as a claim at its hand-over, the route to the other place on its sensor refused; the code was right.  claims and fix 116bec78 and 1fb38e7b; mutation MC2 red. |
| **Grade** | C.  The rule's second walk refuses a route to a sensor on another train's route while that train is a claim - locked, not yet a journey.  On a real railway that state lasts as long as `validatePathActuation` waits for the Central Station to confirm the route's accessories, outside the monitor, which is exactly when another dispatch gets the monitor and is checked; without the second walk the other train is admitted there and RSA2-B1 comes back on every route with a switch.  But in the test railways the state has no length - a path with no accessories confirms at once, and simulation skips the wait - so the round's claim (`testARouteMayNotWaitOnASensorAnotherTrainAwaits`) asks only with the first train under way, and `testASensorAJourneyHasPassedIsFreeAgain` about a journey too.  The disposition's mutations MB1 and MB2 do not name this half.  The code is right; the claim is what is missing.  **New in round 17.** |
| **Names** | RSA2-B1, RSA2-C4 (the same shape: code right, claim missing) |
| **Where** | `Layout.java:2227-2236` (the claims walk); `:4221` (the claim), `:4325`, `:4345` (`validatePathActuation`, outside the monitor); `core.testATrainIsDispatchedOnce.testARouteMayNotWaitOnASensorAnotherTrainAwaits`, `testASensorAJourneyHasPassedIsFreeAgain` |
| **Needs execution** | done - mutant M1 (the claims walk disabled): `core.testATrainIsDispatchedOnce` **28 run, 0 failures**.  Q2, inside Q1, asked with X held as a claim: on the archive Y's road **refused** (*"Feedback 40 is still awaited by 02 0314-1 DDR"*); on M1 **clear**. |

**Direction:** a claim with the first train held as a claim - a route with an accessory whose confirmation the fixture withholds, or Q1's hold - asking the other place on its sensor.

---

### RSA3-C3 - A sensor is free again the moment a journey's head reaches it, not when the track to it is unlocked as Adam's condition says: between the two, only the sensor's own reading keeps a second train off the other place on it, and a gap between two axles reads clear

| | |
|---|---|
| **Disposition** | Fixed - a sensor is held for the train whose route runs to it until the rail to it is given back (releasedEarly), not only until its head reaches it - Adam's "once unlocked": one sensor on where the track is unmeasured, once the tail has provably passed where it is measured, and not before the journey ends in atomic mode.  testASensorAJourneyHasPassedIsFreeAgain now passes a further sensor before it asks.  claims and fix 116bec78 and 1fb38e7b; mutations MC3 red, MC3f red. |
| **Grade** | C.  Adam's condition for RSA2-B1: *"For the sensors, that is OK as long as non-atomic rules are respected (it should be allowed once unlocked)."*  `3a8ff7c0` frees a sensor once the journey's milestones hold its Point - recorded when the head's wait on it ends - and the decided entry reads that as *"once a journey has passed a sensor it waits on it no more, and the lock and release rules decide the track as before"*.  But the lock and release rules decide nothing about the OTHER place on the sensor - a different block, often different metal - so from the head's arrival until the tail has left the contact, the only thing keeping a second train's route off that place is `isPathClear`'s one read of the sensor.  The rail into the Point is unlocked later: in non-atomic mode when the tail has provably passed it (or, unmeasured, one sensor on), in atomic mode at the arrival.  A clear read in between - a gap between two wagons' axles over the contact, a bogie, or current-sensing detection that does not see unlit stock - admits the second train, and the first train's next axle ends its journey at once.  The code's own debounce (`FEEDBACK_DURATION_THRESHOLD`) is there because such reads happen.  Adam's detection holds under tails (AMH-B2), so on his railway only a gap does it, and only at two places on one sensor (a feedback double curve, or an address shared by hand).  **New in round 17** (`3a8ff7c0`). |
| **Names** | RSA2-B1, AMH-B2, OB-238, GUI-A1 |
| **Where** | `Layout.java:2212-2225` (`reached`, the milestones), `:9595-9602` (a milestone recorded after the head's wait), `:9368-9472` (the non-atomic release, by `tailHasProvablyPassed`), `:9796-9798` (the atomic release, at the arrival), `:2904-2910` (the one read of the sensor); `open-questions.md:247-251` |
| **Needs execution** | done - probe `rsa3ProbeTheSensorIsFreedAtTheHeadNotAtTheUnlock` (Q3): X (150 long) PXA -> PXM -> PXB, non-atomic, every rail 100 long, PXM on sensor 3101; Y PYA -> PYP, PYP a second place on 3101.  Before X reaches 3101: Y **refused** (*"still awaited"*).  X's head at PXM, 3101 occupied: Y **refused** (*"Expects feedback 3101 to be clear"*).  3101 reads clear for a moment, X's rail into PXM still run over and held (not unlocked): Y's road **clear**; Y sent, under way; 3101 occupied again by X's next axle: **Y's journey ended at PYP, speed 0, Y never having moved**; X still under way. |

**Direction:** free the sensor when the rail into its Point is released - `releasedEarly` in non-atomic mode, the arrival in atomic mode - which is Adam's word; or, at the least, admit a route to a second place on a sensor only when the sensor has read clear for the debounce, not for one read.

---

### RSA3-C4 - The Yes now takes the journeys' lock on the event thread, and an arriving journey holds that lock while it waits for the railway's monitor: the window freezes, and every train runs on, for as long as another train's route throws its switches

| | |
|---|---|
| **Disposition** | Fixed - the Yes takes no lock: it clears running, counts, and stops the trains; a timetable sets running and then asks the count, clearing it again where the count has moved, so every order of the two ends with the run not started.  Not built: teaching the monitor guard to follow a synchronized block to what its holders wait for - the claims hold the lock and ask the Yes, and start a timetable the count refuses and find nothing running.  claims and fix 116bec78 and 1fb38e7b; mutations MC4 red, MC4t red. |
| **Grade** | C.  Round 17 put `stopLocomotives()` and the count inside `synchronized (this.activeLocomotives)`, so a timetable starting cannot interleave with the Yes (RSA2-C1).  The Yes runs on the event thread.  An arrival, and a failed path's release, take the journeys' lock and then call `unlockPath`, which is `synchronized` on the railway - the order `repaintTimetable`'s comment writes down (*"activeLocomotives -> Layout"*) - and `configureAndLockPath` holds the railway's monitor for 150 ms a rail while it throws a route's switches (and a path search holds it for its search).  So a Yes that lands while an arrival waits for the monitor waits with it: the event thread is frozen - no button, including the window's emergency stop, answers - and the Yes's own `setSpeed(0)` for every train comes after the lock, so every train under way runs on for the length of the wait, past where the operator stopped it.  A train claiming its route when the Yes was asked can finish the claim before the Yes counts, and is then held as a journey rather than given back unrun (RSA-C1's promise for a claiming train).  RSA2-D1 recorded the Yes as taking *"no monitor on the event thread"*; the monitor guard reads for calls to `synchronized` members of `Layout` and cannot see a block one hop away.  Graded C: the freeze needs an arrival blocked on the monitor at the moment of the Yes, and is bounded by the other train's claim - seconds on a long route.  **New in round 17.** |
| **Names** | RSA2-C1, RSA2-C2, RSA2-D1, RSA-C1, OB-192, IR-B2, PRV-B2 |
| **Where** | `Layout.java:2176-2185` (`stopEveryTrainWhereItIs`: the lock `:2178`, the stop sweep after it `:2185`), `:9796-9798` (the arrival: journeys' lock, then `unlockPath`), `:8909-8936` (a failed path, the same), `:4571` (`unlockPath`, `synchronized`), `:4195-4290` (`configureAndLockPath`, `CONFIGURE_SLEEP` per rail under the monitor); `TrainControlUI.java:4385-4413` (the Yes, on the event thread), `:29642-29656` (the lock order, in `repaintTimetable`'s comment); `regression.testNothingOnTheEventThreadTakesTheRailwaysMonitor` |
| **Needs execution** | done - probe `rsa3ProbeTheYesWaitsOutAnotherTrainsSwitches` (Q4): A under way to QA1; C under way to QC1 (speed 15); B sent on a sixteen-rail route, claiming after 28 ms; A's sensor occupied - **A blocked in `unlockPath` holding the journeys' lock** at 247 ms; the Yes run on the event thread by `invokeAndWait`: sampled 300 ms in, **the event thread BLOCKED at `Layout.java:2180`** and **C still at speed 15**; the Yes **took 2,227 ms**; C at 0 once it returned; B, claiming when the Yes was asked, finished its claim at 2,489 ms and **stood as a journey holding its route** (speed 0, never given back).  `regression.testNothingOnTheEventThreadTakesTheRailwaysMonitor`: 5 run, 0 failures on the archive. |

**Direction:** take no lock on the event thread for this - for instance let `executeTimetableInternal` set `running`, then compare the count and clear `running` itself where it has changed (the Yes clears `running` and then counts, so every interleaving ends with the run not started), and stop the trains first; and teach the monitor guard to follow a `synchronized` block to what its holders wait for.

---

### RSA3-C5 - A timetable entry that was refusing when the Yes came outlives its run: the next run sets `running` again, the entry wakes into it with its old count, can never be sent, and at its stuck limit stops the run it never belonged to - blaming the track

| | |
|---|---|
| **Disposition** | Fixed - a timetable entry is counted as running from before its thread starts until it leaves (timetableEntries, in isRunning), and its pause between refusals waits on a monitor every stop notifies (pauseUnlessStopped), so an entry the Yes finds refusing leaves at once, and its run's call returns only once it has.  The claims: the next run is not stopped by an entry of the run the Yes ended, and an entry held at its last line is still counted as running.  claims and fix 116bec78 and 1fb38e7b; mutations MC5n red, MC5p red, MC5c red. |
| **Grade** | C.  A parallel timetable's entry thread loops `while (this.running && !executePath(...))`, pausing `pacedWait` - the delay settings, seconds - between refusals, and is counted by nothing while it pauses.  The Yes clears `running`; with no train under way the railway then reads idle, the run's call returns, and the doors come back.  A run started before the entry's pause ends - Start, Execute Timetable, Return Home, after a load the Yes asked for was refused - sets `running` again, and the old entry wakes into it: since round 17 it carries its own run's count, so every `executePath` answers *"was not sent: the trains were stopped before it set off"*, and at the stuck limit (three minutes) it calls `stopLocomotives()` - a graceful stop of the new run - and logs *"has been unable to run for some time, so the timetable has been stopped.  The track it needs never became free."*  The run stops for a reason the operator cannot see, and the log names the wrong one.  **Older:** an entry thread has always read the railway's `running`, not its own run's; before round 17 it would have been sent into the new run wherever its path had cleared.  Round 17 makes the stop certain, and the sentence untrue. |
| **Names** | RSA2-C1, BR-C3, T3, IP-C1 |
| **Where** | `Layout.java:6817-6905` (the entry thread's loop, the stuck limit, `stopLocomotives()` `:6900`), `:6656-6673` (`pacedWait`), `:6990-7012` (the completion wait: `isRunning()`, which an entry in its pause is not), `:722` (`TIMETABLE_STUCK_MS`), `:2302-2306` (`runLocomotives` sets `running`) |
| **Needs execution** | done - probe `rsa3ProbeAnEntryFromARunTheYesEndedStopsTheNextRun` (Q9, the stuck limit lowered to 4 s, delays 3 s): one entry TA -> TB refused (TB holds another train); the Yes at 600 ms - `running` false, `isRunning` false, the timetable's call **returned** at 795 ms; the next run started at 795 ms (`runLocomotives`, `running` true); the old entry logged *"was not sent: the trains were stopped before it set off"* twice, then *"...so the timetable has been stopped.  The track it needs never became free."*, and **the next run was stopped at 6,036 ms**. |

**Direction:** give an entry thread its run's identity - the count it carries will do - and let it leave when the railway's is no longer its own, rather than asking `running`; and count the entry threads in `isRunning()` while they pause.

---

### RSA3-C6 - Round 18's copies record an arrival side nothing arrives by, and two readers take it for one: a stale barred side now shuts a copy the editor cannot show, and a home facing is offered where no train can ever be brought home

| | |
|---|---|
| **Disposition** | Fixed - a copy of a square nothing arrives at is marked as one (Node.nothingArrives): arrivalAllowed reads no bar against it, homeFacingsAt offers none of its facings, and no home is held to its facing (homeFacingFixed), so the home is the square as it was before round 18.  claims and fix 116bec78 and 1fb38e7b; mutations MC6a red, MC6h red, MC6f red. |
| **Grade** | C.  A copy of a square nothing arrives at is `new Node(tile, side, false)` with `side` the side a train facing its way out would have come in by - so `leavesBy`, `facingOf` and `placementCopy` work as they do elsewhere.  Two readers ask it a different question.  **(a) `arrivalAllowed`** checks it against the store's barred sides, and the build is handed the store's bars unfiltered.  The editor offers bars only on sides trains arrive by, and hides a bar whose side has gone - SA-B1's fix, on the ground that *"The stale side is dead in the build - there is no copy for it"*.  Round 18 made a copy for it: a side barred while trains arrived by it, the track then made one-way away, now matches one of the new copies and builds it as no station.  A train placed facing that way stands on a copy autonomy will not start, told that trains may not arrive there facing its way - a bar the editor shows as nothing and cannot clear.  **(b) `homeFacingsAt`** offers the facing of every copy whose arrival side is not barred, meaning *"a copy trains may not arrive at is not one a train can be brought home to, so its facing is not offered"* (OB-282: *"we shouldn't allow an impossible facing to be saved"*).  On such a square no train can arrive at any copy, and both facings are offered; homed facing one way, a train standing on it the other way was home before round 18 (the home was the square) and is now one Return Home calls IMPOSSIBLE.  Both narrow - a stale bar, a home facing chosen on a square nothing reaches.  **New in round 18.** |
| **Names** | RSA2-C7, SA-B1, SA-C4, OB-282, GUI3-C2, TDY4-D4, AUT3-B1, GUI-B1 |
| **Where** | `AutonomyBuilder.java:640-660` (the copies), `:438-445` (`arrivalAllowed`), `:1062` (`stops`), `:1560-1570` (`homeFacingsAt`); `AutonomySession.java:4554` (the build takes `barredArrivals()`), `:4737-4760` (the editor's bars filtered to sides that exist - none here); `HomeStaging.java:2685-2698` (`atHome`) |
| **Needs execution** | done - probe `rsa3ProbeAStaleBarredArrivalShutsACopy` (Q7): the round's straight with both ways open - arrival sides `[E, W]`; barred from the east; then one-way away from the middle: arrival sides `[]`, **the editor reads barred `[]`, the store holds `[E]`**; built: `main 3,1 (eastbound)` station=true, **`main 3,1 (westbound)` station=false**.  Probe `rsa3ProbeAHomeFacingOnASquareNothingArrivesAt` (Q8): the middle square, arrival sides `[]`, **home facings offered `[E, W]`**; a train placed facing E and homed facing W: Return Home **IMPOSSIBLE**, *"there is no route it may take from main 3,1 to its home main 3,1"*. |

**Direction:** mark the copies of a square nothing arrives at as such, and let `arrivalAllowed` read no bar against them and `homeFacingsAt` offer none of them - or drop a square's bars when its last arrival side goes.

---

### RSA3-D1 - The Yes carried from where a journey is chosen, asked under the lock a timetable takes to start, and a claim completed after it given back and said

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RSA3-C4 (the lock's place) and C5 (an entry thread of an ended run). |
| **Grade** | D |
| **Names** | RSA2-C1, RSA2-C2, RSA2-C8, RSA-C1, GS-C1 |
| **Where** | `Layout.java:2176-2196`, `:2250-2289`, `:5020-5045`, `:6684-6730`, `:6817-6819`, `:8704-8765`, `:8977-8990`, `:9123-9131`; `TrainControlUI.java:4385-4413`, `:6618-6651`, `:24640-24803`, `:27584-27587` |
| **Needs execution** | done - the rounds' claims green on the archive (baseline); the rest by reading |

Every door that chooses a journey carries the count from where it chose: autonomy's thread reads it before `pickPath` and asks `running` after, a hand send at the click (after the reversal question, which is modal), Return Home at the press, a timetable at its start, and the three `-1` overloads (the API and the tests) at dispatch.  The Yes clears `running` and then counts, so a thread that reads the new count reads `running` cleared, and `executeTimetableInternal` compares the count and sets `running` under the same lock, so a Yes during Return Home's planning starts nothing whether the load then succeeds or is refused - and `prepareAutonomyReload` raises `gracefulStopRequested`, so Return Home says nothing about the run it did not make.  Execute Timetable reads the count on its worker, a few instructions after the posted checks; no gesture can reach the Yes into that gap, and a load that finds nothing yet running asks no Yes and retires the railway, where nothing is dispatched (RSA-D7).  A journey whose count has moved is refused before its claim or given back after it, and both say *"was not sent: the trains were stopped before it set off"* in all eight languages.

---

### RSA3-D2 - The turn's resume gives up on the Yes or a retired railway, a retirement wakes the speed waits, and every other wait a journey makes ends

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RSA2-C2, RSA2-C4, RSA-B1, RLV8-A1 |
| **Where** | `Locomotive.java:659-700`, `:790-817`, `:820-905`; `Layout.java:9317-9350`, `:9255-9256`, `:9543-9544`, `:5070-5080`, `:4325-4345` |
| **Needs execution** | no - by reading (the rounds' claims, green on the archive, pin the resume and each retirement) |

`waitForSpeedAtOrAbove(threshold, abandon)` asks its give-up in the loop condition, so a wake before the wait is not lost; the Yes's `setSpeed(0)` on each train under way notifies `speedMonitor` after the count, and `wakeEveryWait` now notifies it at every retirement, and reading the version there is ordered by that monitor.  The other waits: both sensor waits give up on a retired railway (RSA-B1); the turn's stop is `setSpeed(0)` then `waitForSpeedBelow(1)`, immediate; `validatePathActuation` is timed; the yield's `blockUntilMotion` is bounded (30 s) and entered only while `running`; the delays are sleeps.  After a Yes whose load is refused the journeys stand with their paths held and write no speed - RLV6's state, accepted.

---

### RSA3-D3 - The sensor rule: every edge end of every candidate, at every door, and the same pairs kept apart by HomeStaging

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RSA3-C1 (the order of its two walks), C2 (its claim half unclaimed) and C3 (when a sensor is free again). |
| **Grade** | D |
| **Names** | RSA2-B1, OB-164, SG-A4, AMH-B2 |
| **Where** | `Layout.java:2208-2239`, `:2831-2940`, `:6478`, `:9134-9148`, `:9595-9602`; `HomeStaging.java:1796-1880`, `:1893-1970` |
| **Needs execution** | done - Q1's control (a journey under way refuses the other place on its sensor) and Q2 (a claim does, on the archive); the rest by reading |

`isPathClear` asks every edge's end sensor of the candidate - its destination and every intermediate - against every other journey's sensors not yet reached and every claim's, so the offers (`getPossiblePaths`), autonomy's choice and the dispatch itself agree, and the Auto tab's second train by hand (OB-164) meets the same refusal in the same words, logged at the dispatch.  A start sensor is not asked, and needs not be: a train standing on it holds the sensor, which the feedback read refuses.  Return Home runs one leg at a time and refuses to start while anything runs, so no other journey is waiting; its planner keeps two trains off one detection section by its own shared-sensor rule (`canEnter`), which already refuses the pairs this rule does.  The milestones are a copy-on-write list per journey, read without a lock; a journey arriving holds its sensor, which the feedback read refuses until it clears.

---

### RSA3-D4 - The Auto tab's paths while a staging flow owns the railway, the gather's one railway, the latency cut-off, and closing while trains run: as the dispositions say

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RSA2-C3, RSA2-C5, RSA2-C6, RSA2-C8, RSA-C7, GST-C7 |
| **Where** | `AutoLocomotiveStatus.java:339-345`; `TrainControlUI.java:24719-24735`, `:29720-29760`, `:11046-11080`, `:10270-10300`, `:20134-20190`, `:2553-2562`; `LayoutRightclickAutonomyMenu.java:237-335` |
| **Needs execution** | done - the rounds' claims green on the archive; the rest by reading |

The list is hidden while `isStagingInProgress()`, and Return Home's worker refreshes it after the flag is up; the refresh is throttled, and the one in flight reads the flag when it draws.  The gather asks the railway it read once, for every path.  The cut-off sends STOP whatever the power flag reads and lets the flag choose only the log line and the dialog; the ping timer's lost-connection arm asks the same method.  Closing asks while `isRunning()` - which covers a hand send and a timetable between legs, the warning Adam asked for *"whenever TrainControl drives a train in any tier"* - defaults to keeping TrainControl open, touches no train, and the save that follows captures nothing while anything runs.

---

### RSA3-D5 - Round 18's copies through every door: the editor's facing choices, placement, the block, the station index, capture, the checks and the carry

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RSA3-B1 (the rename) and C6 (the arrival side two readers take for real). |
| **Grade** | D |
| **Names** | RSA2-C7, TDY4-C1, GUI2-B1, OB-238, D3F-C1, RLV7-C3 |
| **Where** | `AutonomyBuilder.java:77-137`, `:453-560`, `:596-667`, `:741-900`, `:1030-1270`, `:1511-1600`; `AutonomySession.java:1893-2060`, `:4114-4260`, `:6907-6930`; `StationIndex.java:112`, `:132`; `TrainControlUI.java:6728-6960` |
| **Needs execution** | done - the round's two claims green on the archive; Q6 (the live snapshot: 0 such squares); the rest by reading |

A copy leaves by the way it faces only, and nothing arrives at either, so no edge lands on one.  On a straight or a curve both copies carry the square as their block; on a double curve each is on its own arc, with its own place, as OB-238 has it.  The editor's facing choices are the build's own list (`facingsByName`), so both ways are offered, and a facing chosen picks its copy; a placement with no facing - every placement made on such a square before round 18 - is TDY4-C1's accepted guess (`startableCopy`, the first way out), which the editor offers the choice to correct.  `copyFacing` is written, capture reads it back by name, the station index takes arrival sides from `splitSides` (none, so no bar is offered), the copy checks report neither copy (both lack a way in, so neither has a healthy sibling), and a must-turn square keeps its terminus flag on each copy, which a train may start from.  The carry is by Point name and, where a rebuild renamed the copy, leaves the setup's placement - the known limit (RLV7-C3).

---

### RSA3-D6 - Round 18's batch outside the runtime: nothing in it reaches the railway

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | OB-307, OB-308, MT-505, MT-548, FR-104 |
| **Where** | `TrainControlUI.java:3246-3285`, `:4329-4331`, `:8245-8255`, `:31304-31374`; `LayoutRightclickAutonomyMenu.java:64-71`; `AutonomyMenu.java:244-280`; `AutonomyEditorPanel.java:6657-6780`, `:7433-7452` |
| **Needs execution** | no - by reading |

The trains' lines are drawn only with a setup loaded, a display question - the route guard reads `getActiveAccs`, not these marks; a square's autonomy menu opens only with a setup loaded, and no setup is unloaded while a train runs; the offer of a layout appears only on a Central Station layout, where no setup can be loaded; the last layout folder is a preference keyed by the working folder; the guard refusals become popups inside the editor, which cannot be open during a run; the picture's wrench and crop fetch on their own thread and write only the icon folder.

---

### RSA3-D7 - What RSA2 did not reach, read lightly: TileGraph's checks, HomeStaging's plan and home rules

| | |
|---|---|
| **Disposition** | Checked - clean, as far as read. |
| **Grade** | D |
| **Names** | OB-228, OB-230, AMW-B2, SG-A4, OB-282 |
| **Where** | `TileGraph.java:612-661`, `:1116-1175`; `HomeStaging.java:816-906`, `:1796-1970`, `:2204-2242`, `:2685-2698` |
| **Needs execution** | no - by reading |

The graph refuses a diagram with scissors or a switch missing an address (blocking, scanned per tile, not per walk) and warns of a turntable and a permanently set turnout.  The planner's greedy pass, then A* from its arrangement, then from the railway as it stands, then weighted, each return a whole plan from where it starts, never one appended to another; a planned leg is refused where a sibling on its sensor holds a train, the mover included, and a sensor is freed only when every tail that accounted for it has gone.  A home held to a facing is home only on a copy facing that way.

---

### RSA3-D8 - The probes' controls, the mutant and the baseline

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | - |
| **Where** | `validate30/rsa3/p1/test/regression/rsa3Probes.java`, `rsa3LiveProbes.java`; `validate30/rsa3/p1/rsa3-out/`, `validate30/rsa3/p2/rsa3-out/`; `validate30/rsa3/logs/` |
| **Needs execution** | done |

Each probe states its precondition before its result: Q1 that the rule refuses with nothing held and that the hook fired once, with X's claim removed during Y's check; Q3 that Y is refused before X reaches the sensor and while it reads occupied; Q4 that A was blocked in `unlockPath` before the Yes was asked; Q5 that the entry in the new names loads beside the dropped one; Q7 and Q8 the arrival sides before and after; Q9 that the timetable's call had returned and the next run was running; Q10 that the entry loads before the rename.  The mutant changed one loop and is reported against the archive's answer to the same question.  The baseline: `core.testATrainIsDispatchedOnce` 28 run, the monitor guard 5, the rounds' other claims 5, 0 failures; the probe jobs 8, 1 and 1 run, the mutant job 28 and 1, 0 failures; every job exited 0 and none hung, ran out of memory or was ended by the runner.
