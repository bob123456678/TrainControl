# RLV10 - Validation, round 11: the fixes for RLV9 - Unload's fold, the borrowed timetable, the turns across a fold, the builders, the notice's outline, the station ahead, the jump, the Download door's source (TrainControl 3.0.0)

**Status:** open

**Prefix:** RLV10

**Reviewed:** branch `autonomy-diagram-r0` at `54a6733a`, read and run from `git archive 54a6733a` unpacked into `validate30/rlv10/` (`src` for reading; `p10`, `m1`, `m2`, `m3` for running); TC30's working tree and the main checkout's were never read.  Scope `1d4bf3ea..54a6733a`: round 11's claims `f97a12ad`, its fixes `75827053`, and the records `5d7ab87e`, `7ce29f9d`, `54a6733a` (no code after `75827053`: `git diff 75827053 54a6733a -- src test` is empty).  2026-09-28.

## Method

**Read whole:** the brief; `docs/reviews/README.md`; RLV9.md as written (`validate30/RLV9.md`) and as dispositioned (`docs/reviews-2026-09-25/RLV9.md` at `54a6733a`, diffed against it - only the ten disposition rows differ); every commit message in scope; the source diff of `75827053` (Layout, TileAnnotation, AutonomyEditorPanel, AutonomyViewerPanel, LayoutEditor, TrainControlUI) and its test and behaviour.md hunks; the claims `f97a12ad`; the records' hunks (behaviour.md, open-questions.md, findings.tsv); the round's mutation spec and log in the scratchpad (`mut_r11_spec.py`, `mut_r11.log`) and its red and green logs (`r11-red.log`, `r11-red2.log`, `r11-green.log`, `r11-green2.log`).

**The code at 54a6733a, around each change:** `TrainControlUI`'s `unloadAutonomy`, `autonomySetupDeleted`, `prepareAutonomyReload`, `captureRunningLayout` (both forms) and all its callers, `resetAutonomySession`, `forgetTheRailway`, `sourceIsNow`, `useTheDownloadedLayout`, `carryTheTrainsAcross`, `keepThePendingTurnsAcross`, `takeThePendingTurns`, `putThePendingTurnsBack`, `whereTheTrainsAre` (both forms), `putTheTrainsBack`, `rebuildRunningLayoutFromSetup`, `reconcileFacingWhenIdle`, `updateVisiblePoints`, `attachAutonomyRefresh`, `autonomyLoadedFromDiagram`, `autonomyEditorClosed`, the exit handler and `saveState`'s fold, `requestReturnToHome` and its runner, the timetable runner, `clearTimetable` and the entry delete, `refreshReturnHomeButton`, `paintReturnHomeFromTheAnswer`, `repaintAutoLocList` / `Lite` / `Full`, `buildAutonomyFacingMenu`, `openAutonomyEditor` (both forms), `openLayoutEditor` (all three forms), `initializeTrackDiagram`, `setViewListener`'s source, the five doors that write the layout source (Switch to Central Station layout, Choose Local Data Folder, Initialize Local Layout, the new-layout door, Download) and `MarklinControlStation`'s own fallback to the Central Station's; `Layout`'s `getLastPointsReached`, `stationAheadItHolds`, `lastKnownPoint`, the early release in the path loop, `toJSON` (both forms), `setTimetable`, `loadReturnToHomeTimetable`, `setTimetableSequential`, `fromJSON`'s timetable, `takeReversalsOnArrival`, `restoreReversalsOnArrival`; `AutonomySession.faceTheWayItCameIn` and the running-layout supplier's eleven readers (here and in `AutonomyEditorPanel`); `AutonomyViewerPanel`'s `load`, `loadPrepared`, the import's capture, the findings list and its click; `AutonomyEditorPanel`'s findings click, `clearGesture`, `tileClicked`'s head, `outlineTheNotice`, `isOutlined` and its reader, `outlineAndReveal` and its five callers; `LayoutEditor`'s jump handlers, `reveal`, `dispose`; `AutonomyMenu`'s Unload item and `guardWhileEditing`; `AutonomyCompanionStore`'s configuration discovery; `testControlStationFaults`' sequential-flag tests and `testHomeStaging`'s hand-back tests.

**Runs.**  Every JVM through a copy of the project's `docs/tools/one.sh` (`one-k.sh`: the same script, keeping each class's output, and polling before the compile and before every class until no `java.exe`, `javaw.exe` or `javac.exe` runs - giving up after ten minutes and saying which class it stopped before), `TC_SCRATCH` = `rlv10/scratch10` in Windows form.  The trees were unpacked with `cs2_sample_layout` excluded and an empty folder of that name made, which is all the runner's root check needs; none of the classes run reads that folder.  A queue (`rlv10/queue.sh`) started each job only when no such process ran and no live process held the shared lock, twice 20 seconds apart, and decided each retry from that attempt's own log.  Its first version, without the lock test, tried once between two of the battery's classes and was refused by the runner's probe; I stopped it (the harness's stop left its shell and a `sleep` running, which I ended by process id - both mine) and started the second.  The battery ended at about 01:30; everything below ran 01:30-01:50, one job after another.  An expired monitor of mine left a `tail` running, ended by process id.  Nothing of anyone else's was stopped.

- **Baseline** (`p10`): `regression.testTheImportDoorReadsAnOldFile` 30/30, `core.testATrainIsDispatchedOnce` 8/8, `core.testAutonomyDiagramSession` 155/155, `core.testMassAssignLengths` 74/74, `regression.testEditorSurfaceRules` 60/60, `core.testAutonomyDiagramMonitor` 29/29, `regression.testAPendingTurnSurvivesTheRebuild` 2/2, `core.testHomeStaging` 110/110, `core.testControlStationFaults` 11/11; no skips.
- **Probes** (scratch classes in `p10` only): `regression.rlv10Probe` - six probes on the live-snapshot sandbox with the window, as the claims open it (Unload and, as control, a reload, with the flag an edit a run declined raises; a destination turn owed with a train under way, then a reload, Unload, or another configuration chosen - a copy of the sandbox's configuration named Other, written into the sandbox's temporary folder; Unload during Return Home); `core.rlv10KeptProbe` - three trains on the hand-built fixture of `testATrainIsDispatchedOnce`, over 42 ordered pairs of locomotives; `regression.rlv10EditorProbe` - the Auto tab's door and the one-argument door into the editor.  Results are in the findings.
- **Mutations** (each tree a fresh archive with only its change): `m1` - V1 (`setTimetable` no longer ends the loan) and V5 (the notice's outline is never cleared: not by a click on the diagram, a tool or Escape, or the next finding); `m2` - V2 (the station ahead need not be held by the train); `m3` - V3 (the point ahead need not be a station).  Results in RLV10-C5.  AA1-AC8 and W1-W4 were not repeated; `mut_r11.log` has each red where the dispositions say.

**Nothing written** outside `validate30/rlv10/` and this file.  No git state changed; no commit.  The first unpack, `rlv10/src` (read, never run), was a plain `git archive | tar` and so holds the archive's copy of `cs2_sample_layout`; I did not open anything in it, and the four trees that ran have none.  `LocDB.data` and `UIState.data` in each tree are copies from TC30.  The real `LocDB.data` and `UIState.data` - the main checkout's and `C:/Users/adamo`'s - were hashed before the first JVM and after the last, and are unchanged (`3f54de3a...`, `3604bf5f...`, `6ef56939...`, `9e32894e...`).  Nothing of mine is running.

**Counts:** 0 A, 1 B, 5 C, 10 D.

**Round 11's fixes do what RLV9 asked at the doors RLV9 named** (RLV10-D1 to RLV10-D8), and their claims are real: green on the archive, each red with its fix undone (the round's log).  **What they did not reach is the same three shapes as last round, one door further out.**  Unload folds now, except while an edit a run declined waits - the one case RLV9-A1's own direction named - where it neither folds nor carries, so the next load puts every train the run moved back where the file had it (RLV10-B1).  The turns are kept across a reload of the configuration running, and still dropped at the other two doors that fold and leave the railway, Unload and another configuration (RLV10-C1).  And the station-ahead rule gives a train a point that another train under way is kept at, so which of the two keeps it follows the hash order - and RLV9-C4's erasure comes back in about half the orders (RLV10-C2).  The Auto tab's new door outlines the square of every finding it opens, not only the guard's (RLV10-C3); the sequential flag's explanations still describe a plan being saved, which RLV9-B1's fix ended (RLV10-C4); and four of the round's rules are unclaimed (RLV10-C5).  **New in round 11:** C2, C3, C4, C5.  **Older:** B1 (left by the round, and now described as done), C1.

---

### RLV10-B1 - Stop Using Autonomy while an edit a run declined waits still keeps nothing: the next load puts every train the run moved back where the file had it, and nothing says so

| | |
|---|---|
| **Disposition** | Fixed - while an edit a run declined waits, Stop Using Autonomy writes where the trains stand and nothing else into the configuration running (AutonomySession.captureWhereTheTrainsStand: each square's train, its tail's side and road, and its copy's facing), as a load in that state carries them; the edit is kept.  The comment and behaviour.md say so.  claims 446c997c (red first), fix 2573c432; mutations BB1 red, BB2 red (BB2: the whole fold, which takes the edit away).  Its claim, red 5 times in 6 run alone, also found the station labels' posted update asking getAutoLayout after Unload (facingArrowOf) and building an empty railway - RLV8-C2's family, missed by two sweeps; it and the two tile lookups beside it now ask once, not built (6 of 6 green alone; mutation BL1 red).  The exit with a train under way is still GST-C7. |
| **Grade** | B.  RLV9-A1's harm - DW-A1's shape: the square a moved train really stands on reads free, and occupancy is read from placements, so Start can send a train into it - on the path the round left, which needs a setup edit made during the run (any setting from the diagram's right-click menu while a train moves raises the flag).  The load door's version of this loss was graded B (RLA5-B1, RLV6-B1); Adam may prefer A, as RLV9-A1 was.  The log message the edit wrote invites exactly this sequence: *"stop autonomy and make it again to have it take effect now, or it will be picked up the next time the setup is loaded"* (`messages.properties:396`).  **Older**: Unload has never kept the trains in this state; round 11 fixed the case without an edit and now says this one is *"as at every door"* - which it is not: while the flag is up every load carries the trains across, and the exit says in the log what it did not save. |
| **Names** | RLV9-A1, DW-A1, RLA5-B1, RLV6-B1, ACC-B3, OB-183; GST-C7 |
| **Where** | `TrainControlUI.java:8969-8975` (the comment *"nothing is folded while an edit a run declined waits, as at every door"*, then `captureRunningLayout(true)`), `:3181` (which returns while the flag is up), `:8977-8981` (the model cleared, then `forgetTheRailway` lowers the flag and the forgotten name, so no load afterwards carries anything); against `AutonomyViewerPanel.java:791-803` (every load while the flag is up carries the trains, `carryTheTrainsAcross`) and `TrainControlUI.java:2558-2561` (the exit, skipping the same fold, logs `autosetup.log.placementsNotSaved`); `behaviour.md:2475-2476` (*"Unload folds the running layout into the configuration first, as a reload does, so a load after it has the trains where the run left them"*) |
| **Needs execution** | done - probes `probeUnloadWithAnEditWaiting` and its control `probeReloadWithAnEditWaiting` |

**Measured,** on the live-snapshot sandbox with the window.  EN57-203 is moved from TopMainR0Park to BottomMainC on the running railway, as a run moves it; the configuration still places it on TopMainR0Park's square (`1 - Main:4,5`).  Then the flag `setupEditDeclinedDuringRun` is raised, as a declined edit raises it, and the configuration is loaded again, with and without Stop Using Autonomy first (at rest: nothing is asked).

| | load again (control) | Stop Using Autonomy, then load again |
|---|---|---|
| EN57-203 after the load | BottomMainC | **TopMainR0Park** |
| the flag after the load | down (the carry carried the edit) | down (Unload forgot it) |
| the configuration places EN57-203 on | `1 - Main:4,5` (a carry writes nothing) | `1 - Main:4,5` |

Unload's fold returned at the flag, the model was cleared, and the forget lowered the flag with nothing kept; the load then built from the file.  The same holds, by reading, with a train under way at Unload answered Yes.

**Direction:** keep where the trains stand across Unload as a load does - write the placements alone into the configuration (`whereTheTrainsAre`, as the rebuild's put-back uses; placements only, which is what makes that safe against the edit - ACC-B3 - except a placement the edit itself made), or refuse Unload while the flag is up and say to load the configuration instead.  At the least, log what the exit logs, and correct the comment and behaviour.md.  Claim: the probe.  (The exit with a train under way is GST-C7, still open; now that Unload folds a railway holding a path, the exit is the one fold left that refuses it.)

---

### RLV10-C1 - The destination turns owed during a run are still dropped at the two other doors that fold and leave the railway: Stop Using Autonomy, and choosing another configuration

| | |
|---|---|
| **Disposition** | Fixed - Stop Using Autonomy, and choosing another configuration, write the turns the railway owes into the configuration they leave before its fold (TrainControlUI.writeTheTurnsOwed, the idle drain's own loop, extracted as writeTheTurns); a turn is written only for a train still standing where it turned.  claims 446c997c (red first), fix 2573c432; mutations BC1a red, BC1b red. |
| **Grade** | C.  RLV9-C1's loss at the doors its fix did not reach: a turn made at a destination during the run is written into the setup only when the railway is next idle, and these two doors replace or drop the railway first.  The fold writes where the turned train stands - the copy facing the way it arrived - so the configuration has it facing the wrong way, and the next dispatch is offered paths for that heading (OB-189).  The disposition declines the second door on purpose - *"the turns are about the railway left"* - but the railway left is the configuration being folded, whose trains they are; that is a reason to write them into it, not to drop them.  **Older** in effect: before round 11 Unload lost the turns with everything else, and the other-configuration door has lost them since the fold replaced the carry (round 10); round 11's fix reached only the reload of the configuration running. |
| **Names** | RLV9-C1, D3-C5, RGD-C7, REV9-A1, OB-189 |
| **Where** | `AutonomyViewerPanel.java:846-855` (`keepThePendingTurnsAcross` only where the name loaded is the one running); `TrainControlUI.java:8962-8983` (`unloadAutonomy`: the fold, then the model cleared with the records on it); `:7734` (`reconcileFacingWhenIdle`, the only writer, returns while the railway runs - and after Yes a train stopped between sensors keeps it running) |
| **Needs execution** | done - probes `probeTurnOwedThenReload`, `probeTurnOwedThenUnload`, `probeTurnOwedThenAnotherConfiguration` |

**Measured,** on the live-snapshot sandbox with the window, the same way RLV9 measured the reload door.  EN57-947 is sent from BottomInner and stays under way; 2-8-4 3505 SP stands on TopMainR2Inter (southbound) with its side recorded, N, and the setup's facing for its square is E.  Two turns are then owed on the running layout: 3505's at TopMainR2Inter, and one nothing can write (a train and a point that do not exist).  The drain declines while the railway runs.  Then one of three doors, answering Yes, and the new railway's idle drain.

| door | still owed after it | 3505 after the idle drain | setup facing for its square |
|---|---|---|---|
| reload of Main (RLV9-C1's door, fixed) | the unwritable one | TopMainR2Inter (westbound) | N |
| Stop Using Autonomy, then Main loaded | **nothing** | **TopMainR2Inter (southbound)** | **E** |
| Other chosen, then Main chosen again | **nothing** (none on Other's railway either) | **TopMainR2Inter (southbound)** | **E** |

**Direction:** before a fold that leaves the railway - Unload's, and the one choosing another configuration makes of the one running - write the owed turns into the configuration being folded.  `faceTheWayItCameIn` needs only the train still standing where it turned and the side it came in by, both true of a turn record, and writes absolutely, so a retry is harmless; "only when idle" is about trains between two copies, which a completed arrival is not.  Or keep them with the configuration's name for its next load.  Claim: the two probes' unwritable record, and the turned train's copy.

---

### RLV10-C2 - The station ahead can be another train's kept point: which of the two keeps it follows the hash order, and in about half the orders the fold writes one of them on no point - RLV9-C4's erasure, through its fix

| | |
|---|---|
| **Disposition** | Fixed - the station ahead is decided against the map as first read, and passes over a station another train under way is kept at (Layout.getLastPointsReached, stationAheadItHolds), so the two trains are kept apart in every order: the claim covers all 42 ordered pairs of seven locomotives.  claims 446c997c (red first), fix 2573c432; mutation BC2a red.  BC2b (the gives-way check read from the map as rewritten) survives and is equivalent once the station ahead passes over another's kept point: no train is moved onto a point another is kept at, so the two maps agree wherever that check reads them. |
| **Grade** | C.  A train that is out on the track is modelled nowhere after the reload or Unload, and its placement is erased from the configuration, so no later load brings it back - exactly what RLV9-C4 fixed, reached one step further.  Needs atomic routes off (Adam's configuration) and three trains: C has set off from station S and tripped no station sensor since, and S has been released behind it; A's path has since been locked through S, A has set off from P, and P has been released behind it; B has stopped on P; then a reload or Unload is confirmed.  **New in round 11** (`75827053`). |
| **Names** | RLV9-C4, RLV8-C4, RLV7-C1 |
| **Where** | `Layout.java:1628-1640` (the loop reads `out` while it rewrites it: whether C - kept at S, which A holds - "gives way" depends on whether A has already been moved to S, `:1634`); `:1652-1676` (`stationAheadItHolds` takes the first station ahead the train holds, whoever else is kept there); `:12112-12125` (`keptHere` keeps one train per point); `TrainControlUI.java:6532-6553` (the carry skips a kept train where another is recorded) |
| **Needs execution** | done - probe `core.rlv10KeptProbe` |

**Measured** on the hand-built fixture of `testATrainIsDispatchedOnce`: TA, TS, TB and TC are stations, TA-TS-TB is A's path and TS-TC is C's; A holds TS and TB, C holds TC, B stands on TA; A's milestones are [TA], C's [TS].  For each of the 42 ordered pairs of the first seven locomotives as A and C (B another):

| | pairs |
|---|---|
| A kept at TS, C kept at TC; the carry reads and the fold writes both | 22 |
| **A and C both kept at TS; the carry reads C nowhere, and the fold writes C on no point** | **20** |

The split follows the two trains' order in the map: every pair whose C lies in a lower hash bucket than A (the probe prints both) keeps them together; C in a higher bucket, or the same one, keeps them apart.  Where A is moved first, C then reads as standing under A and is moved ahead too; where C comes first, A's move to TS arrives after C has been left there.

**Direction:** decide against the map as read rather than as rewritten, and have `stationAheadItHolds` pass over a station another train is kept at (the next station ahead - A's TB here).  Claim: this fixture, asserted over enough pairs to cover both orders (or with the two trains' order forced).

---

### RLV10-C3 - The Auto tab's door outlines the square of every finding it opens, not only a guard notice's; the editor's own list outlines only a finding that names other squares

| | |
|---|---|
| **Disposition** | Fixed - the editor opened for a finding outlines only a finding that names squares beside its own, as the editor's own list does.  claims 446c997c (red first), fix 2573c432; mutation BC3 red. |
| **Grade** | C.  Cosmetic: the outline is drawn exactly as the bulk selection is, so an ordinary warning clicked on the Auto tab opens the editor with its square looking selected until the next click, which the same finding clicked in the editor never does.  Nothing is written to it (RLV9-C3's fix holds).  **New in round 11** (`75827053`). |
| **Names** | RLV9-C5, RLV9-C3, MT-505 |
| **Where** | `AutonomyViewerPanel.java:513` (the click passes the row's related squares), `:1920` (`finding.getRelated()` - *"never null; empty for most findings"*, `AutonomyChecks.java:203-210`); `TrainControlUI.java:5387-5393` (`if (outline != null && ...) outlineTheNotice(reveal, outline)`, which outlines `reveal` itself); against `AutonomyEditorPanel.java:1181`, `:1193-1206` (the editor's list outlines, and jumps with its squares, only where the list is not empty) |
| **Needs execution** | done - probe `regression.rlv10EditorProbe` |

**Measured** on the live-snapshot sandbox with the window: the Auto tab's first row with a square is a finding on `2 - Bottom:8,7` naming no other square (related `[]`).  `openAutonomyEditor(tile, [])`, as the list's click calls it, opens the editor with that square outlined (bulk selection empty); `openAutonomyEditor(tile)` opens it with nothing outlined.

**Direction:** `outline != null && !outline.isEmpty()` in `openLayoutEditor` (or pass null from the Auto tab for an empty list), so the two lists agree.

---

### RLV10-C4 - Records the round made false: the sequential flag is still explained as what makes a saved Return Home plan reload safely, and RLV9-B1's fix means a plan is never saved

| | |
|---|---|
| **Disposition** | Fixed - the sequential flag's two comments in Layout and the test's javadoc say a fold no longer writes a plan, and what the flag's branch keeps working is a file written before the loan (2573c432). |
| **Grade** | C.  Comments are meant to be authoritative; these now describe a save that cannot happen.  Since the loan, a fold while the plan occupies the timetable writes his timetable and no flag (`owners == null` is false), so the `timetableSequential` branch of `toJSON` only writes back a flag read from a file - one an RLV9-B1-era fold wrote, with the plan as that configuration's timetable, which nothing repairs (only a configuration saved by the round-10 build during Return Home).  **New in round 11** (`75827053`). |
| **Names** | RLV9-B1, S14-C3; `traincontrol-comments-self-contained` |
| **Where** | `Layout.java:12176-12181` (*"Without this a saved return-home plan reloaded as an ordinary timetable: entries dispatched as soon as the previous one STARTED rather than arrived"*); `:11905-11906` (`setTimetableSequential`: *"including for the file that has to remember it"* - it has no caller in `src`, and `fromJSON` sets the field directly); `testControlStationFaults.java:250-258` (`testAStagingPlanIsStillSequentialAfterASaveAndLoad`: *"saving a return-home plan and reloading it brought back exactly the failure the flag was added to prevent"* - it sets the flag by hand, with no loan, a state no production path now reaches) |
| **Needs execution** | no |

**Direction:** say what the branch is now for (a file written before the loan), and point the test at that, or at the loan: a plan loaded with `loadReturnToHomeTimetable` and saved comes back as the owner's timetable, not sequential.

---

### RLV10-C5 - What the round's claims do not reach: the loan's end, the station test in the rule ahead, the outline's clearing, and the doors the dispositions call by reading

| | |
|---|---|
| **Disposition** | Fixed - claims for the loan's end (testHomeStaging, V1), the station test ahead (a point that is no station on the C2 claim's path, V3) and the outline's clearing by a click on the diagram (V5); the outline on opening is claimed with RLV10-C3.  V2 is equivalent, as the report says.  The page jump's handler in LayoutEditor and the Auto tab's click remain by reading.  446c997c; mutations V1 red, V3 red, V5 red. |
| **Grade** | C.  Claims that would not catch the undoing of their own round's words.  **New in round 11** (`f97a12ad`). |
| **Names** | RLV9-B1, RLV9-C3, RLV9-C4, RLV9-C5, RLV9-C2 |
| **Where** | `Layout.java:11724` (V1), `:1669` (V2, V3); `AutonomyEditorPanel.java:1040`, `:1201-1206`, `:7224-7230` (V5); `LayoutEditor.java:1784-1795`, `AutonomyViewerPanel.java:513`, `TrainControlUI.java:5387-5393` (the doors by reading) |
| **Needs execution** | done - mutations V1, V2, V3, V5 |

| mutation | classes run | result |
|---|---|---|
| V1 - `setTimetable` no longer ends the loan: after a Return Home every fold writes the timetable as it stood when Return Home began, so one captured, cleared or edited afterwards is never saved | the Return Home, timetable and import classes, and the session class: 12 classes, 346 tests | **all green** |
| V5 - the notice's outline is never cleared (V1 and V5 in one tree, so each survived alone) | `core.testAutonomyDiagramSession` among them, 155 | **all green** |
| V2 - the station ahead need not be held by the train | `core.testATrainIsDispatchedOnce` 8 | green - but equivalent where it matters: every station ahead of a train on its path is still held by it, since release happens only behind, so not a gap |
| V3 - the first point ahead the train holds, station or not | `core.testATrainIsDispatchedOnce` 8 | **green**: every point in the claim's fixture is a station, so it cannot tell a station from a turnout; undone, a train is written onto a square no train can be put back on (`moveLocomotive`) |

The dispositions already say, and I confirm by reading, that no claim reaches `LayoutEditor`'s notice jump (it could open the editor with the station alone), the Auto tab's door (`openAutonomyEditor(tile, null)` would pass every claim), the outline on opening, or the C2 sweep past the Return Home button.

**Direction:** a hand-back claim that asserts the fold after `setTimetable(borrowed)` writes a timetable changed since; a station-ahead fixture with a non-station point between the kept point and the station; a click on the diagram after the notice's, asserting the outline gone; a claim through the Auto tab's click (`findingRows` with a guard notice) into the editor's `isOutlined`.

---

### RLV10-D1 - RLV9-A1 at its own door: Unload folds before it clears, a train under way once, and the reset after it has nothing left to fold

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RLV10-B1. |
| **Grade** | D |
| **Names** | RLV9-A1, RLV8-B1, DW-A1 |
| **Where** | `TrainControlUI.java:8962-8983`; `:3157-3200` (`captureRunningLayout(boolean)`); `:3210-3240` (the reset's fold finds no railway, then the name is forgotten); `AutonomyMenu.java:149` (`guardWhileEditing` greys Unload while an editor is open) |
| **Needs execution** | done - the claim ran green; probe `probeUnloadDuringReturnHome` |

The fold is `toJSON(getLastPointsReached())`, as the reload's, after Yes has stopped the trains; `underWayToo` is passed only here, and every other caller keeps refusing a railway holding a path.  Unload cannot be reached with an editor open, so the fold's save commits no half-made edit.  Every fold now writes a train under way on its kept point or runs at rest, where the map is empty: the load's, the editor doors' and the reset's (`captureRunningLayout`), the import's capture (refused while busy) and the exit's (refused while running - GST-C7).  Unload during Return Home keeps his timetable (probe: one entry, his, no `timetableSequential`; the load after it runs it in parallel), and writes the train under way where it set off.

### RLV10-D2 - RLV9-B1: the loan against every writer and reader of the timetable

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RLV10-C4 and a two-statement window. |
| **Grade** | D |
| **Names** | RLV9-B1, S14-C3 |
| **Where** | `Layout.java:11846-11900` (`loadReturnToHomeTimetable`: an impossible plan returns at `:11850`, before the timetable is touched), `:11714-11725` (`setTimetable` ends the loan), `:12148-12185` (`toJSON`); `TrainControlUI.java:24392`, `:24528` (the hand-back, in the runner's `finally`, onto the railway the plan ran on); `:22469`, `:27562` (Clear and Delete refused while busy) |
| **Needs execution** | done - the claim ran green; probe `probeUnloadDuringReturnHome` |

The loan is set only with a plan that runs, and ended by every other timetable load: the hand-back however the run ends, a parse, a clear.  Capture is excluded for the run (`timetableExecuting`); the Auto tab shows the plan while it runs, as before; the exit's fold between two moves now writes his timetable too.  A reload during planning folds his (the plan is not loaded yet), and the runner then hands back to the retired railway.  One window remains: `loadReturnToHomeTimetable` replaces the timetable (`:11882`) two statements before it records the loan (`:11895`), unsynchronized while `toJSON` is synchronized, so a fold landing between them writes the plan, without its flag.  Two statements on the worker against a confirmed load on the event thread; not graded.

### RLV10-D3 - RLV9-C1 at the reload door: the turns are kept, and drained on the new railway

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RLV10-C1. |
| **Grade** | D |
| **Names** | RLV9-C1, D3-C5 |
| **Where** | `TrainControlUI.java:3101-3113`; `AutonomyViewerPanel.java:846-855` |
| **Needs execution** | done - probe `probeTurnOwedThenReload` (RLV10-C1's table) |

Drained before the fold, put back in a `finally` onto whatever railway the load leaves - the old one where the load is refused or will not build - and only where absent, so a newer turn is not overwritten.  Measured: the unwritable record survives the reload and 3505 is drained to its westbound copy, facing N, as the carry did at `5e3aeffa`.

### RLV10-D4 - RLV9-C2: nothing on the refresh builds a railway, and every reader of the swept suppliers takes none as nothing to show

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLV9-C2, CS3-C4 |
| **Where** | `TrainControlUI.java:24663`, `:24789`, `:27279-27281`, `:4775`, `:4782`; `AutonomySession.java:7969`, `:8281`; `AutonomyEditorPanel.java:3602`, `:3700`, `:3764`, `:3926`, `:3980`, `:4005`, `:5348`, `:7194` |
| **Needs execution** | no - the claim ran green; AC2 is its mutation |

Each reader of the two suppliers already read `runningLayout == null ? null : runningLayout.get()` and handles null.  The refresh's other callees (`repaintAutoLocList`, `updateVisiblePoints`, `refreshActivateRoutesControls`) ask `hasAutoLayout` first, and the status panels hold the railway they were built on.

### RLV10-D5 - RLV9-C3: the outline is its own set, with one reader and three clears, and the bulk readers no longer see a notice

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RLV10-C5 (V5). |
| **Grade** | D |
| **Names** | RLV9-C3, MT-505 |
| **Where** | `AutonomyEditorPanel.java:8974` (`isOutlined`, the only reader, in the tile's paint); `:1040` (`clearGesture` - a tool, Escape), `:7224-7230` (any click on the diagram), `:1201-1206` (the next finding), `:10315` (a new notice); `:10342` (`outlineAndReveal`, still the selection, for the walks, which clear it at their end) |
| **Needs execution** | no - the claims ran green; AC3 and AC3b are their mutations |

`applyLength`, `applyLengthAnswer` and the direction gestures read only `selection`, which a notice no longer fills.  `LayoutEditor.reveal` scrolls and flashes and clicks nothing, so revealing does not clear the outline it follows.

### RLV10-D6 - RLV9-C4: the rule against its readers and a train still locking its path

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RLV10-C2 and RLV10-C5 (V3). |
| **Grade** | D |
| **Names** | RLV9-C4, RLV8-C4 |
| **Where** | `Layout.java:1606-1676`; its readers: `TrainControlUI.java:6506-6556` (the carry), `:3188`, `AutonomyViewerPanel.java:902` (the folds) |
| **Needs execution** | done - probe `core.rlv10KeptProbe`: the carry and the fold agreed in all 42 pairs |

The carry and the three folds read one map, so they agree wherever it is right.  A train still locking its path is kept at its start, where it stands and is recorded, so the rule never moves it.  A train only passing through still gives way: the RC claim fails if that line goes, by reading (RC_x's first train holds RC_b ahead).

### RLV10-D7 - RLV9-C5: the jump carries the squares, and the editor opened outlines them

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RLV10-C3. |
| **Grade** | D |
| **Names** | RLV9-C5, MT-505, OB-058 |
| **Where** | `AutonomyEditorPanel.java:1181-1190`; `LayoutEditor.java:1784-1795` (the page jump's handler, with the squares); `TrainControlUI.java:4905-4921`, `:5197-5230` (the three-argument form still passes `remember` false), `:5382-5393` |
| **Needs execution** | done - probe `regression.rlv10EditorProbe` |

A finding on another page naming nothing still takes the page jump, as before.  An editor already open is brought forward, as before, without the outline.  `testEditorSurfaceRules` reads the refusals from the five-argument form that now holds them, and is green.

### RLV10-D8 - RLV9-C6 and RLV9-C8: the comments, and the switch-of-source rule at every door

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLV9-C6, RLV9-C8, RLV8-C5, RLV7-C2, FR-103 |
| **Where** | `TileAnnotation.java:1670-1677`, `:1775-1800`; `AutonomyEditorPanel.java:7861-7869`; `TrainControlUI.java:3025-3043`, `:10946`, `:9623`; the doors `:22961`, `:23009`, `:23034`, `:10890`, the Download door; `MarklinControlStation.java:587` |
| **Needs execution** | no - the claim ran green; AC8 is its mutation |

The three comments now say the grey and the supplier's `getAutoLayoutIfLoaded`.  Every door that writes the source reaches `sourceIsNow`: four through `initializeTrackDiagram`, the Download door directly.  Download is offered only on the Central Station's layout, whose remembered source is `""`, so the forget it now makes drops nothing a configuration was running on.  The model's own fallback to `""` when a folder will not read reaches no `initializeTrackDiagram`, and leaves the remembered source as the folder whose autonomy railway is still loaded - right, since choosing it again is the same railway.

### RLV10-D9 - The round's claims ran green on the archive, and each goes red with its fix undone

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RLV10-C5. |
| **Grade** | D |
| **Names** | f97a12ad; AA1-AC8, W1-W4 |
| **Where** | the baseline in the method; `mut_r11.log` |
| **Needs execution** | done |

The baseline is in the method.  Each claim failed on `1d4bf3ea` for its finding (`r11-red.log`: import 5, dispatch 1, session 3), and each of the fifteen mutations turned the claim it names red.  The mutation log records its wait step missing (*"waitjava.sh: No such file or directory"* before every run), so those runs did not wait for other JVMs; the runner's own probe still refuses alongside test JVMs, and the log's last line (00:31) precedes the battery's start (00:37), so the results stand.

### RLV10-D10 - The records

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RLV10-B1 (behaviour.md's Unload sentence). |
| **Grade** | D |
| **Names** | RLV9 |
| **Where** | `docs/reviews-2026-09-25/RLV9.md`; `findings.tsv` (20 RLV9 rows); `behaviour.md:2469-2479`, `:2530`; `open-questions.md:429-430` |
| **Needs execution** | no |

The dispositions describe the code: every one says what `75827053` does, and those that are by reading say so.  The store's 20 rows and both counts (4,780 rows, 4,423 findings) agree with the documents.  RLV9's status line reads `open`, as every record in the folder does.
