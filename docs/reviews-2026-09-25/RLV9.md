# RLV9 - Validation, rounds 9 and 10: Adam's notes of 26 September and FR-103; the fixes for RLV8 - Unload's retirement, the fold with a train under way, the builders, the parse, the carry's reading, the switch of source (TrainControl 3.0.0)

**Status:** closed

**Prefix:** RLV9

**Reviewed:** branch `autonomy-diagram-r0` at `1d4bf3ea`, read and run from `git archive 1d4bf3ea` unpacked into `validate30/rlv9/a9` (and, identical but for my scratch classes, `p9`); TC30's working tree and the main checkout's were never read.  Scope `5e3aeffa..1d4bf3ea`: Adam's layout edits `3410b453`, `df17e179` (not read: `cs2_sample_layout/`); the tracker `0a92be5a`; round 9's claims `bbb488c7`, fixes `0d597c1a`, tracker `b07b1d6a`; round 10's claims `7aa18a31`, `da4d1b24`, fixes `78074e09`; the records `75cb4f7a` and `1d4bf3ea`.  Round 8's behaviour was measured on `git archive 5e3aeffa` (`validate30/rlv9/o9`).  2026-09-27.

## Method

**Read whole:** the brief; `docs/reviews/README.md`; RLV8.md as written (`validate30/RLV8.md`) and as dispositioned (`docs/reviews-2026-09-25/RLV8.md` at `1d4bf3ea`, diffed against it); every commit message in scope; the source diffs of `0d597c1a` (AutonomyChecks, AutonomySession, TileAnnotation, AutonomyEditorPanel, LayoutLabel, eight bundles) and `78074e09` (Layout, Point, AutonomyViewerPanel, LayoutEditor, TrainControlUI, MarklinControlStation); the claims `bbb488c7`, `7aa18a31`, `da4d1b24`; the behaviour.md, open-questions.md, issues.md and tests.md hunks; the round's mutation specs and results in the scratchpad (`mut_r9_spec.py`, `mut_r9.log`, `mut_r10_spec.py`, `mut_r10.log`).

**The code at 1d4bf3ea, around each change:** `MarklinControlStation`'s `hasAutoLayout`, `getAutoLayoutIfLoaded`, `getAutoLayout`, `clearAutoLayout`, `parseAuto`, `isAutonomyRunning`, and `MarklinRoute.heldReason`; `Layout`'s constructor, `retireEveryLayout`, `makeCurrent`, `isCurrentLayout` and all ten of its readers, `isRunning`, `stopLocomotives`, `getLastPointsReached`, `lastKnownPoint`, `executePath` and `executePathInternal` (the lock, the start, the per-point loop, the fence, the arrival and its turn record), `announceRunFinished`, `takeReversalsOnArrival`, `restoreReversalsOnArrival`, `moveLocomotive`, `loadReturnToHomeTimetable`, `toJSON` and `toJSON(keptAt)`, `fromJSON`'s head; `Point.setLocomotive`, `reserve`, `assign`, `toJSON(standing)`; `AutonomySession.captureFromLayout`, `faceTheWayItCameIn`, `departableFacingsFor`, `setRunningLayoutSource` and its readers; `TrainControlUI`'s exit save and its confirmation, `carryTheTrainsAcross`, `captureRunningLayout`, `resetAutonomySession`, `forgetTheRailway`, `prepareAutonomyReload`, `isAutonomyBusy`, `whereTheTrainsAre`, `takeThePendingTurns`, `putTheTrainsBack`, `reconcileFacingWhenIdle`, `attachAutonomyRefresh`, `autonomyLoadedFromDiagram`, `unloadAutonomy`, `autonomySetupDeleted`, `setViewListener`'s head, `initializeTrackDiagram` and the four doors that change the layout source, `initializeEmptyLayout`, `unzipFile`, `checkAutoLayoutLatency`, `repaintTimetable`, `repaintAutoLocList` / `Lite` / `Full`, `refreshReturnHomeButton`, `paintReturnHomeFromTheAnswer`, the Return Home and timetable runners, `buildAutonomyFacingMenu`, Find Similar, `updateVisiblePoints`, `refreshCoveredTrack`, `repaintSwitch`; `AutonomyViewerPanel.load`, `loadPrepared`, the import's capture, the delete door, the findings list's click; `AutonomyEditorPanel`'s findings list and its click, `outlineAndReveal`, the bulk selection and its readers (`applyLength`, `applyLengthAnswer`, `cancelPendingGesture`, `clearGesture`), `massAssignTrainLengths`, `askForWholeLength`, every reader of `layoutSource` and `runningLayout`; `LayoutEditor`'s suppliers, `reveal` and the page jump; `AutonomyMenu`'s delete and Unload items.

**A census** of the 186 `getAutoLayout()` calls in `src/` (a script listing each with no `hasAutoLayout()`, `isAutonomyLoaded()` or configuration test in the 25 lines above it: 95), every one read at its call site for whether it can run with nothing loaded.

**Mechanical checks** (a Python script over the archive's bundles): the three changed keys and the station walk's key in all eight languages - count, ASCII bytes, `\uXXXX` escapes, apostrophes, placeholders, line endings.

**Runs.**  Every JVM through the project's `docs/tools/one.sh` in a copy (`one-m.sh`, the same script with `-methods` for a method list and each class's output kept), `TC_SCRATCH` in `rlv9/scratch9`, started only by a loop that waited until no `java.exe`, `javaw.exe` or `javac.exe` was running and retried whenever the runner refused (it refused 13 times while the main checkout's battery ran).  That loop's retry test reads the last 30 lines of its own log, where the refusals still stood after a successful run, so it ran the baseline a second time and the `5e3aeffa` probes six or seven times in all; every repeat gave the same results.  Nothing of anyone else's was stopped.  Of my own, the queued chain that would have run the mutations was stopped when the coordinator asked for this report (the harness's stop left its shell running, so that shell - `chain2.sh` - was ended by process id), and the `5e3aeffa` loop was left to finish its tests.

- **Baseline, 1d4bf3ea** (`p9`): `core.testATrainIsDispatchedOnce` 7/7, `regression.testTheImportDoorReadsAnOldFile` 27/27, the four round-9 claims (`core.testAutonomyDiagramSession.testClickingAGuardNoticeOutlinesItsSignal` and `testAGuardOffTheWayInIsNoticed`, `core.testAutonomyDiagramMonitor.testASquareAutonomyWillNotChooseIsGrey`, `regression.testTheEditorSaysWhatItsToolsDo.testTheGuardItemsSayWhatTheGuardsDo`) 4/4, `core.testMassAssignLengths` 74/74, `core.testMessageBundles` 16/16, `regression.testEveryMessageKeyIsAskedFor` 2/2, `regression.testAPendingTurnSurvivesTheRebuild` 2/2; no skips.
- **Probes** (two scratch classes, `regression.rlv9Probe` - six probes on the live-snapshot sandbox with the window, as the claims open it - and `core.rlv9GuardProbe` on the session claim's fixture; the first run unchanged on `5e3aeffa`, `o9`): Unload with a train under way and its sensor then set; a destination turn owed across a reload with a train under way; a train under way whose start another train has since stopped on, across the same reload; Unload at rest after a train was moved and a setting changed, then a load, and its control without the Unload; a reload during Return Home; a guard notice clicked, then a length set on another square.  Results are in the findings.
- **Mutations** (`m9`, applied together to a copy and restored from the archive afterwards): W1 (`initializeTrackDiagram` no longer records the source it switched to), W2 (the prefilled box not selected), W3 (a point's side written for whichever train the fold writes there), W4 (a kept train written though the railway no longer shows it anywhere).  Queued behind the battery and not run before this report was asked for; each is assessed by reading in RLV9-C7.  (The round's own Z1-Z10 and Y1-Y6 were not repeated; their results in the scratchpad logs are as the dispositions say.)

**Nothing written** outside `validate30/rlv9/` and this file.  No git state changed; no commit.  Nothing under any `cs2_sample_layout/` was read or written by me; the runner fingerprints the archive's copy around each run.  `LocDB.data` and `UIState.data` in each tree are copies from TC30.  The real `LocDB.data` and `UIState.data` - the main checkout's and `C:/Users/adamo`'s - were hashed before the first JVM and after the last, and are unchanged (`3f54de3a...`, `3604bf5f...`, `6ef56939...`, `9e32894e...` - taken at 23:22, after the sixth `5e3aeffa` run and while its loop's last run may still have been starting; that loop only runs the probe class on `o9`'s sandbox).

**Counts:** 1 A, 1 B, 8 C, 10 D.

**Round 10's fixes do what RLV8 asked at the doors it named** (RLV9-D1 to RLV9-D6): measured, a train Unload stopped stays stopped when its sensor fires, and the round's claims are green.  **What they did not reach is a sibling at each of three doors.**  The fold that replaced the carry at a reload during a run (RLV8-B1) now also folds what a run only borrows - Return Home's plan, which it writes into the configuration as its timetable (RLV9-B1) - and drops what the carry used to take across, the destination turns not yet written (RLV9-C1).  RLV8-C2's sweep took one of the two builders out of the refresh every run ends with, so Unload during a run still leaves an empty railway behind (RLV9-C2).  And the switch-of-source rule is kept by three of the four doors (RLV9-C8).  **The one A is older:** Stop Using Autonomy clears the model before the reset's fold, so it has never folded anything - every train a run moved goes back where the file had it at the next load (RLV9-A1).  Round 9's changes are right as far as Adam's notes go; the guard notice's outline is also a bulk selection that the next length is written to (RLV9-C3).  **New in round 9:** C3, C5, C6 (part), C7 (part).  **New in round 10:** B1, C1, C4 (the erasure), C6 (part), C7 (part), C8; C2 is an older builder round 10's sweep left.  **Older:** A1, and C4's effect on the railway.

---

### RLV9-A1 - Stop Using Autonomy folds nothing: every train the run moved goes back, at the next load, to where the file had it before the run - and so do the Auto tab's settings and a captured timetable

| | |
|---|---|
| **Disposition** | Fixed - Stop Using Autonomy folds the running layout into the configuration before it clears the model, each train under way written on the one point it is kept at, as a reload's fold writes it; so a load after it has every train where the run left it, and the Auto tab's settings as they were.  Not while an edit a run declined waits, as at every door.  Said in behaviour.md.  claims f97a12ad (red first), fix 75827053; mutations AA1 red, AA2 red. |
| **Grade** | A.  DW-A1's shape from an ordinary gesture: a run, Stop Using Autonomy, then the configuration loaded again - from the menu, the banner, or the start-up load after a restart.  Every train the run moved is modelled where it stood before the run; the square it really stands on reads free, and occupancy is read from placements, so Start can send a train into it.  Nothing says so: the item's tooltip says it unloads *"without deleting anything"*.  **Older**: Unload has cleared the model before resetting the session since it was added (`ddff66e0`; the same order at `ab8aa1a9` and `5e3aeffa`).  Not changed by rounds 9 and 10, but round 10 worked this door (the retirement) and built the fold it lacks (`toJSON(keptAt)`, which a train under way no longer defeats). |
| **Names** | DW-A1, OB-183, OB-144; RLV8-B1 (the same loss at the reload door, fixed there); RLV7-C2 |
| **Where** | `TrainControlUI.java:8859-8874` (`unloadAutonomy`: `this.model.clearAutoLayout()` at `:8866`, then `resetAutonomySession()`); `:3142-3147` (the reset's first act is `captureRunningLayout()`); `:3092-3100` (which returns at once when `!this.model.hasAutoLayout()` - always, here); `:2552-2556` (the exit save needs an active configuration, which the reset has just nulled); `:8903-8911` (`autonomySetupDeleted`, the same order, where there is nothing to keep); `messages.properties:1599` (*"Unloads the running configuration without deleting anything"*) |
| **Needs execution** | done - probes `probeUnloadAfterARunThenLoadAgain` and its control `probeReloadAfterARunWithoutUnload` |

**What happens.**  A run moves trains, and where they stand lives only in the running layout until a fold writes it into the configuration - a load, an editor door, the exit.  Unload clears the model first and resets the session second; the reset's fold asks the model for its railway, finds none, and returns.  So nothing is folded, the configuration still has every train where the last fold left it, and the name is forgotten - so the exit save, which needs a configuration running, folds nothing either.  The next load builds from the file.

**Measured,** on the live-snapshot sandbox with the window.  EN57-203 is moved from TopMainR0Park to BottomMainC on the running railway, as a run moves it, and the Auto tab's maximum delay is changed from 2 to 7 on the running layout, as its slider does.  Then the configuration is loaded again, with and without Stop Using Autonomy first (at rest: nothing is under way, nothing is asked).

| | 1d4bf3ea | 5e3aeffa |
|---|---|---|
| load again, no Unload: EN57-203 / max delay | BottomMainC / 7 | BottomMainC / 7 |
| Unload, then load again: EN57-203 / max delay | **TopMainR0Park / 2** | **TopMainR0Park / 2** |

The configuration still placed EN57-203 at TopMainR0Park before either load; only the fold moves it.  By reading, the same holds for a run's destination turns not yet written, a captured timetable, and every other key the fold carries (RLV8-B1's list); and with a train under way at Unload, answered Yes, it is the same.

**Direction:** fold before clearing, as a reload does - the running layout's `toJSON(getLastPointsReached())` into the configuration running, unless an edit waits (then keep the name for a carry, as the flag already does) - then clear and retire.  Claim: this probe, and the same with a train under way answered Yes.

---

### RLV9-B1 - A reload confirmed while Return Home runs now writes Return Home's plan into the configuration as its timetable, and saves it

| | |
|---|---|
| **Disposition** | Fixed - while a staging plan occupies the timetable the railway keeps the one it borrowed, and a fold writes that one, with no one-at-a-time flag (Layout.timetableOnLoan, set and cleared with timetableSequential).  So a reload confirmed during Return Home, or Unload, keeps the configuration's own timetable.  claims f97a12ad (red first), fix 75827053; mutations AB1 red, AB2 red. |
| **Grade** | B.  The configuration's own timetable - one captured, or made by hand - is replaced on disk by the staging plan, marked sequential, and nothing says so.  The same grade RLV8-B1 gave a timetable lost at this door (*"Adam may prefer A"*).  Reached by choosing a configuration, or importing, while Return Home is running, and answering Yes.  **New in round 10** (`78074e09`), as a widening: at `5e3aeffa` a layout holding a path was carried, not folded, so the plan reached the file only in the instant between two moves when no train was under way; now it does whenever the reload lands. |
| **Names** | RLV8-B1, RLV7-B1; S14-C3 |
| **Where** | `TrainControlUI.java:24286` (*"Borrowed, not replaced ... An abrupt exit is safe by construction: the timetable only reaches disk when the autonomy file is saved, so a plan that was never saved cannot outlive the session"*), `:24422` (handed back in the runner's `finally` - onto the railway the reload has already replaced); `Layout.java:11815-11825` (`loadReturnToHomeTimetable` puts the plan in the timetable and sets `timetableSequential`); `:12100-12108` (`toJSON` writes `timetable`, and `timetableSequential` when set); `AutonomySession.java:5591-5598` (`captureFromLayout` copies every top-level key into the configuration's globals); `AutonomyViewerPanel.java:873-880` (the fold, now with no `isRunning()` in its condition), `:938` (`save()`) |
| **Needs execution** | done - probe `probeAReloadDuringReturnHome` |

**What happens.**  Return Home borrows the timetable: it puts its plan there, marks the timetable sequential, runs it, and hands the original back in its `finally`.  Round 8 carried a layout holding a path, so the borrowed plan never reached a fold.  Round 10 folds it, and the fold writes the layout's timetable - the plan - and `timetableSequential` into the configuration, which the load then saves.  The runner's `finally` later hands the original back to the railway the reload has already replaced, where nothing reads it.

**Measured,** on the live-snapshot sandbox with the window.  The configuration's own timetable has one entry.  Return Home's plan is loaded (one move) and a train sent, as the plan's first move would be.  Then the configuration running is reloaded, answering Yes.

| after the reload | 1d4bf3ea | 5e3aeffa |
|---|---|---|
| configuration: `timetableSequential` | **true** | absent |
| the new railway's timetable: sequential | **true** | false |

The entry counts are equal (one each), so the flag is what tells the plan from his timetable: only the plan sets it.

**Direction:** fold the owner's timetable, not the borrowed one - while a staging flow owns the layout, write the borrowed original (or leave `timetable` and `timetableSequential` out of the fold).  Claim: this probe.

---

### RLV9-C1 - The fold that replaced the carry at a reload during a run does not take the pending destination turns across: every turn the run made is dropped with the old railway

| | |
|---|---|
| **Disposition** | Fixed - a load that folds the configuration running keeps the destination turns the railway owes across it, as the carry and the rebuild do (TrainControlUI.keepThePendingTurnsAcross).  A load of another configuration does not: the turns are about the railway left.  claims f97a12ad (red first), fix 75827053; mutation AC1 red. |
| **Grade** | C.  D3-C5's loss, at the reload door: a turn the railway made at a destination is written into the setup only when the railway is next idle, and during a run it never is - so every turn made since the run started is still owed when a reload is confirmed, and the fold drops them.  Where the train stands on the copy its turn already agrees with, the fold's placement carries the facing anyway and nothing is lost but the record; where it stands on a copy facing the way it came in (a turn at a square it may turn at, or one the drain has declined to write), it is modelled facing the wrong way.  **New in round 10** (`78074e09`): round 8's carry took the turns across (`takeThePendingTurns` / `putThePendingTurnsBack`); before round 8 the fold lost them too.  Unclaimed. |
| **Names** | D3-C5, RGD-C7, REV9-A1, OB-189; RLV8-B1 |
| **Where** | `AutonomyViewerPanel.java:781` (only an edit waiting carries now), `:873-880` (the fold); `TrainControlUI.java:3032-3060` (`carryTheTrainsAcross` drains and restores the turns), `:6466-6491`; `:7621-7700` (`reconcileFacingWhenIdle` returns while the railway is running, `:7631`, and is the only writer); `Layout.java:3549-3561`, `:3768-3779` (*"Not a cure for a configuration RELOAD between the turn and the write - that swaps this object entirely and the pending records go with it"*); `MarklinControlStation.java:1085-1140` (`parseAuto` keeps the capture flag and the visit history across the parse, and not the turns); `regression.testAPendingTurnSurvivesTheRebuild` (the rebuild door only) |
| **Needs execution** | done - probe `probeAPendingTurnAcrossAReloadWithATrainUnderWay`, on both commits |

**Measured,** on both commits, on the live-snapshot sandbox with the window.  EN57-947 is sent from BottomInner towards BottomMainC and stays under way.  Two turns are then owed on the running layout, as a run leaves them: one the drain can never write (a train and a point that do not exist, as `testAPendingTurnSurvivesTheRebuild` uses), and one for 2-8-4 3505 SP, standing on TopMainR2Inter (southbound) with its side recorded, N.  The drain is asked and declines, since the railway is running.  Then the configuration is reloaded, answering Yes, and the new railway's idle drain is run.

| after the reload and the drain | 1d4bf3ea | 5e3aeffa |
|---|---|---|
| the unwritable turn | **gone** | still owed |
| 2-8-4 3505 SP stands on | **TopMainR2Inter (southbound)** | TopMainR2Inter (westbound) |
| the setup's facing for its square | **E** (unchanged) | N |

At `5e3aeffa` the carry drained both turns and put them back on the new railway, whose drain then wrote 3505's.  At `1d4bf3ea` the fold replaced the railway with the records on it.

**Direction:** drain the turns before the fold and put them back after the load, as the carry and the rebuild do - or let `parseAuto` keep them, as it keeps the visit history.  Claim: this probe's unwritable record, asserted after a reload with a train under way.

---

### RLV9-C2 - RLV8-C2's sweep missed the other half of the refresh it named: `refreshReturnHomeButton` still builds an empty railway, and every run that Unload drops ends by calling it

| | |
|---|---|
| **Disposition** | Fixed - the Return Home button asks getAutoLayoutIfLoaded, so the refresh an unloaded railway fires as its last thread ends builds nothing (mutation AC2 red, the claim firing that refresh as the thread's end fires it).  Its answer's check, the timetable runner (which now asks once for the railway it runs on) and the facing menu's two suppliers were swept with it - by reading, not claimed.  claims f97a12ad (red first), fix 75827053. |
| **Grade** | C.  RLV8-C2's consequence, on the one path Unload most often takes: after Unload during a run, the model holds an empty railway again once the dropped railway's last thread has ended, and `hasAutoLayout()` answers yes about nothing - the right-click menu, Delete / Ctrl+X / Ctrl+V and the captions behave as RLV8-C2 described.  No train moves: the empty railway is newer than the one dropped, and RLV8-A1's retirement already stopped that one's threads.  **Left by round 10** (`78074e09` swept `repaintTimetable` and the ping, not their sibling); the builder is older. |
| **Names** | RLV8-C2, CS3-C4; `fix-one-site-sweep-the-siblings` |
| **Where** | **The builder:** `TrainControlUI.java:24549-24562` (`refreshReturnHomeButton`: `Layout layout = ... this.model.getAutoLayout(); if (layout == null)` - a null test `getAutoLayout` can never satisfy).  **Who calls it after a run is dropped:** `:4072-4105` (the refresh attached to every loaded railway, fired by `Layout.announceRunFinished` when its last thread ends, `Layout.java:8546-8551`, `:4711-4715`) through `repaintAutoLocListLite` (`TrainControlUI.java:29131-29139`); the timetable and Return Home runners' `finally` (`:24438`, `:27183`), and `:27161` beside it (`this.model.getAutoLayout().getUnfinishedTimetablePathIndex()`, read after `executeTimetable` returns); the triage's answer (`:24675-24679`, `this.model.getAutoLayout() != asked`).  **The same shape, unswept, on a gesture:** `:4704-4714` (`buildAutonomyFacingMenu`'s two running-layout suppliers, beside the `setLayoutSource` round 10 did sweep at `:4800`).  **The claim:** `testTheImportDoorReadsAnOldFile.java` `testAnUnloadForgetsTheRailway` calls the ping and `repaintTimetable` itself, so it cannot see a builder on the refresh's other call |
| **Needs execution** | done - probe `probeUnloadThenTheSensorFires`, on both commits |

**Measured,** on both commits (probe `probeUnloadThenTheSensorFires`): EN57-947 sent from BottomInner towards BottomMainC; Unload answered Yes; its awaited sensor 1008 then set.

| | 1d4bf3ea | 5e3aeffa |
|---|---|---|
| after Unload: `hasAutoLayout()` | false | false |
| highest speed commanded after the sensor | 0 | 30 |
| the dropped railway's thread ended at | 256 ms | 3685 ms |
| `hasAutoLayout()` first true at | **256 ms** | 1438 ms |
| the model then holds | **an empty railway, current** | an empty railway, current |

At `1d4bf3ea` the fence stops the thread at once (RLV9-D1), its `finally` announces the run finished, and in the same instant the model holds an empty railway: `repaintTimetable` no longer builds, and `refreshReturnHomeButton`, posted by the same refresh, does.  An Unload at rest that followed a placement by a moment (probe `probeUnloadAfterARunThenLoadAgain`) likewise left the model holding a railway, on both commits.  The round's claim passes because it asks the ping and `repaintTimetable` directly, before any refresh has run.

**Direction:** `refreshReturnHomeButton` asks `getAutoLayoutIfLoaded()` (its null branch is already written), and so do `:27161`, `:24679` and the facing menu's suppliers; the Unload claim drives the refresh the dropped railway's last thread fires, rather than two of its callees.

---

### RLV9-C3 - A click on a guard notice leaves the station and its signal as the editor's bulk selection, and the next Segment Length - on any square - is written to those two instead

| | |
|---|---|
| **Disposition** | Fixed - a clicked finding's squares are outlined through a set of their own, not the bulk selection, so the next length goes where it is asked; the outline goes with the next click on the diagram, the next finding, Escape or a tool.  claims f97a12ad (red first), fix 75827053; mutations AC3 red, AC3b red (AC3b: MT-505's outline itself). |
| **Grade** | C.  A length typed for one square lands on two others, overwriting theirs; lengths are what the release behind a train is proved with.  The outline and the hint (*"2 selected"*, `messages.properties:1472`) do show it, and Escape, a tool button or a walk clears it; nothing else does.  **New in round 9** (`0d597c1a`): a click on a finding used to flash its square and select nothing. |
| **Names** | MT-505; SET-B3 (a selection is squares, not runs); WK-B1 |
| **Where** | `AutonomyEditorPanel.java:1159-1181` (the click calls `outlineAndReveal`), `:10271-10285` (which writes the squares into `selection`); `:375` (*"Bulk selection, so a one-way run is set in one gesture"*); `:7008-7021` and `:7110-7150` (`applyLength` / `applyLengthAnswer`: a non-empty selection is the target, whatever square the menu or Control+E was on); `:5870-5883`, `:2015-2021` (both doors); `cancelPendingGesture` (`:934`) keeps the selection, on purpose, for the shift-click case |
| **Needs execution** | done - probe `core.rlv9GuardProbe` |

**Measured** (probe `core.rlv9GuardProbe`, the claim's own fixture): the guard notice clicked, the editor's selection is the station (main:1,1) and the signal (main:2,3).  A length of 9 then set on main:4,1, as its Segment Length... item ends: station 9, signal 9, main:4,1 unchanged at 0.  Before round 9 the click flashed the station and left the selection empty, so the length went where it was asked.

**Direction:** outline the notice's squares without making them the bulk selection (a highlight set of its own, as the walks' prompts could use too), or clear the selection on the next click of a square.

---

### RLV9-C4 - Where another train has since stopped on the point a train under way is kept at, the reload leaves the train under way on no square - and the fold now erases its placement from the configuration too

| | |
|---|---|
| **Disposition** | Fixed - where another train now stands on the point a train under way is kept at, it is kept on the first station ahead on its path that it still holds, its destination at worst (Layout.getLastPointsReached), so the carry and the fold both write it there, once.  A train only passing through still gives way (RLV8-C4).  claims f97a12ad (red first), fix 75827053; mutation AC4 red. |
| **Grade** | C.  A train that is out on the track is modelled nowhere after the reload, so the track it is on is not protected, and with round 10 its placement is gone from the file as well, so no later load brings it back.  RLV8-C4's direction was about a train only passing through the point, and that case is fixed (RLV9-D4); this is the case it left to the train standing there, and it does win.  Needs atomic routes off, a second train that has stopped on the point the first set off from or last tripped while the first is still under way, and a reload confirmed then.  Two trains kept at one point is the same gap: the fold writes whichever `keptAt`'s hash order yields last, the carry whichever it yields first.  **Older** in its effect on the railway (round 8's carry left the train nowhere too, measured); the erasure is **new in round 10** (`78074e09`). |
| **Names** | RLV8-C4, RLV7-C1; DW-A1; VD10-C15 |
| **Where** | `Layout.java:12042-12065` (`keptHere` is used only where no train stands; a train kept where one does is written on no point, and `keptHere.put` keeps one train per point); `TrainControlUI.java:6433-6450` (the carry's same rule); `AutonomySession.java:5484-5498` (the capture then removes the square's `loc`) |
| **Needs execution** | done - probe `probeAKeptTrainWhoseStartAnotherNowStandsOn`, on both commits (the release and the arrival emulated on the live snapshot, which runs atomic) |

**Measured,** on both commits.  EN57-947 is sent from BottomInner (northbound) towards BottomMainC; then BottomInner is given to EN57-203, standing, as a release behind EN57-947 and EN57-203's arrival would leave it.  EN57-947's last known point is BottomInner.  Then the configuration is reloaded, answering Yes.

| | 1d4bf3ea | 5e3aeffa |
|---|---|---|
| the carry reads EN57-947 at | nowhere | nowhere |
| the fold writes EN57-947 on | **no point** | (no fold) |
| after the reload EN57-947 stands on | nothing | nothing |
| the configuration places EN57-947 on | **0 squares** | 1 square (where the file had it) |

So both leave a train that is out on the track modelled nowhere after the reload - at `5e3aeffa` because the carry put EN57-203 onto the square the rebuild had put EN57-947 on - and round 10 also removes its placement from the configuration, so no later load brings it back.

**Direction:** where the kept point is taken, write the train under way on the next point of its path it still holds (its destination at worst), which is at least track it is heading over - or refuse the reload with the trains named, as RLV7-C4's refusal does.

---

### RLV9-C5 - MT-505's outline reaches one of the three ways to a guard notice's square: the Auto tab's list opens the editor at the station only, and a notice about a station on another page loses its signal in the jump

| | |
|---|---|
| **Disposition** | Fixed - a finding on another page takes the squares it names with it (setOnJumpToNotice), and the Auto tab's list opens the editor with them (openAutonomyEditor(tile, related)); the editor opened outlines them beside the square it reveals.  The editor's jump is claimed (mutation AC5 red); the Auto tab's door and the outline on opening are by reading.  Only the guard findings name other squares, and of those only a guard on both lists, a warning, is on the Auto tab.  claims f97a12ad (red first), fix 75827053. |
| **Grade** | C.  Adam's note is answered where he clicked (the editor's list); the same notice clicked on the Auto tab, or about another page, still shows only the station.  **New in round 9** (`0d597c1a`), as siblings not swept. |
| **Names** | MT-505, AUT-C2; `fix-one-site-sweep-the-siblings` |
| **Where** | `AutonomyViewerPanel.java:486-505` (the Auto tab's list: a click opens the editor at `finding.getTile()`, the station); `AutonomyEditorPanel.java:1150-1157` (a finding on another page hands only `at` to `onJumpToPage`, and `LayoutEditor.java:1765-1775` reopens the editor at that one square); `:1159-1181` (the new branch, reached only on this page) |
| **Needs execution** | no |

**Direction:** carry the finding's related squares through both doors - `openAutonomyEditor` and the jump - and outline them on arrival.

---

### RLV9-C6 - Comments the round made false: the parking badge is "orange" in two of TileAnnotation's explanations, and the editor's layout supplier "builds a Layout"

| | |
|---|---|
| **Disposition** | Fixed - TileAnnotation's two explanations and the switched-off-square test's javadoc say the inactive colour is grey since FR-103; the editor's layout supplier comment says it asks getAutoLayoutIfLoaded; the two code comments with the walk's old name, and the under-way claim's javadoc (which still said the carry), were corrected with them (75827053, f97a12ad). |
| **Grade** | C.  Comments are meant to be authoritative and self-contained; these now say the opposite of the code beside them.  **New in round 9** (`0d597c1a`, the first two) **and round 10** (`78074e09`, the third). |
| **Names** | FR-103, RLV8-C2; `traincontrol-comments-self-contained` |
| **Where** | `TileAnnotation.java:1672-1674` (*"orange again for the badge on a square autonomy leaves alone, and the same orange for the train mark"*); `:1777-1797` (*"ORANGE WHENEVER AUTONOMY WILL NOT SEND A TRAIN HERE"*, *"Orange rather than blue because the graph window already paints an inactive point orange"*, *"a switched-off square was ALWAYS orange"*, *"must still read as orange ... and the test pins that"*) - the code under them now draws `POINT_INACTIVE`, grey; `AutonomyEditorPanel.java:7831-7833` (*"`layoutSource.get()` is `getModel().getAutoLayout()`, which BUILDS a `Layout` when there is none"* - it is `getAutoLayoutIfLoaded` since `LayoutEditor.java:1718`) |
| **Needs execution** | no |

The claim beside the colour says it too: `testAutonomyDiagramMonitor.java:1076-1088` (*"Orange, because the graph window paints an inactive point orange"*, *"parking was already the orange one"*).  The class javadoc and the legend at `:130-140` and `:1717` were updated; these were not.  The `:1916` sentence is history ("in the same orange as a parking station" describes the defect OB-167 fixed) and can stand.

---

### RLV9-C7 - What the round's claims do not reach: the source remembered after a switch, the prefill's selection, the refresh RLV9-C2 names, and the round's unclaimed sweeps

| | |
|---|---|
| **Disposition** | Fixed - claims for each gap: the switch claim goes back to the first source (W1), the MT-566 claim asserts the selection when the box takes the keyboard (W2), the RLV8-C4 claim gives the kept point a side recorded for the train passing through (W3), and the RLV9-C4 claim keeps a train the railway shows nowhere (W4); RLV9-C2's refresh is claimed as that finding says.  f97a12ad; mutations W1 red, W2 red, W3 red, W4 red. |
| **Grade** | C.  Claims that would not catch the undoing of their own round's words.  **New in round 9** (`bbb488c7`) **and round 10** (`7aa18a31`, `da4d1b24`). |
| **Names** | RLV8-C5, MT-566, RLV8-C2 |
| **Where** | `TrainControlUI.java:10840` (W1); `AutonomyEditorPanel.java:10388-10397` (W2); `testTheImportDoorReadsAnOldFile.java` `testASwitchOfRailwayForgetsTheRailwayItLeaves` (its two switches both start from the start-up source), `testAnUnloadForgetsTheRailway` (calls two callees of the refresh, not the refresh); `testMassAssignLengths.testWithEveryTrainMeasuredTheWalkShowsEachAndSkipKeepsIt` (reads the box's text only) |
| **Needs execution** | yes - W1-W4 are prepared in `rlv9/mutate.py` and were not run (see the method); assessed by reading |

Each by reading the claim against the mutation; the run that would have measured W1-W4 together (`m9`, prepared and verified by diff) was queued behind the battery and not made before this report was asked for.

- **W1** - `initializeTrackDiagram` stops recording the source it switched to.  The C5 claim switches twice, both times away from the start-up source (the same folder, then that folder with a separator added), and the second switch is followed only by restoring the preference; with W1 both compare against the start-up value and answer as they do now.  **Survives.**  Undone, a switch to B followed by choosing A again keeps B's flag and name (RLV7-C2's carry across railways), and choosing B again forgets them (RLV8-C5).
- **W2** - the prefilled box not selected.  The MT-566 claim reads `getText()` only.  **Survives**; typing then appends to the length it shows (5 becomes 57).
- **W3** - a point's side written for whichever train the fold writes there.  The C4 claim's kept point, RC_x, has no side (`reserve` clears it), and the under-way claim's train is kept on the point it occupies.  **Survives.**
- **W4** - a kept train written though the railway no longer shows it anywhere.  Every train in both claims is on some point.  **Survives.**
- **RLV9-C2's builder** - `testAnUnloadForgetsTheRailway` calls `checkAutoLayoutLatency` and `repaintTimetable` itself; nothing in it fires the refresh a run's end fires, so `refreshReturnHomeButton` building (as it does, measured) leaves it green.  The dispositions also record Find Similar and the suppliers as unclaimed.

**Direction:** a C5 claim that switches A to B and back to A; the MT-566 claim asserting the selection (`getSelectedText()` after the prompt has the keyboard); a fold claim with a kept point that has a side from another train, and with a kept train off the railway; the Unload claim driving the refresh (`announceRunFinished`, as the dropped railway's last thread does).

---

### RLV9-C8 - One door switches the source without the rule: Download from the Central Station writes the folder and never reaches `initializeTrackDiagram`, so the source a later switch compares with is still the Central Station's

| | |
|---|---|
| **Disposition** | Fixed - the Download door records the folder as the source a later switch compares with (useTheDownloadedLayout), through the same rule initializeTrackDiagram now calls (sourceIsNow).  claims f97a12ad (red first), fix 75827053; mutation AC8 red. |
| **Grade** | C.  Narrow: after a download, a run on the downloaded folder with the flag raised, then that folder chosen again, is read as a switch to another railway - RLV8-C5's outcome (every train the run moved stood back where it set off).  **New in round 10** (`78074e09`); the door's skipping of the reset is older. |
| **Names** | RLV8-C5, RLV7-C2 |
| **Where** | `TrainControlUI.java:26398-26440` (the preference written at `:26426`, then `syncWithCS2`, `repaintLayout`, `showLayoutTab` - no `initializeTrackDiagram`, so `railwaySource` keeps the `""` it was given on the Central Station layout); `:10836-10840` |
| **Needs execution** | no |

The other doors are right.  Initialize Local Layout over the folder in use keeps the source and, as it should, the railway: `unzipFile` copies without `REPLACE_EXISTING`, so an existing layout's files are left as they are (`unzipFile`, `:22136`, `Files.copy(in, f.toPath())` at `:22187`, whose failure is logged and skipped).

**Direction:** set `railwaySource` where the Download door writes the preference - or reach `initializeTrackDiagram` from it, as the other three doors do.

---

### RLV9-D1 - RLV8-A1: Unload retires the railway it drops, and every reader of the version counter answers as a reload makes it answer

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLV8-A1; CS3-C4 |
| **Where** | `Layout.java:792-795` (the version is volatile, the counter ticked under `VERSIONS`), `:1679-1685` (`retireEveryLayout`), `:1715-1718`; `MarklinControlStation.java:1055-1078` (`clearAutoLayout`, the retirement at `:1071`, after the model is emptied under its lock); the ten readers of `isCurrentLayout()` in `Layout.java` - `:1872` (the simulated clear behind a train), `:6385` and `:6609` (the timetable's loop and completion wait), `:8584` (before a path is locked), `:8774`, `:8791`, `:8873`, `:8916`, `:9060` (the per-point speed, sensor wait, turn, release and pre-arrival blocks), `:9110` (the fence) |
| **Needs execution** | done - probe `probeUnloadThenTheSensorFires` |

**Measured,** on both commits (probe `probeUnloadThenTheSensorFires`, the RLV8-A1 probe repeated): EN57-947 sent from BottomInner, Unload answered Yes, sensor 1008 set.  At `1d4bf3ea` the dropped railway is not current as soon as Unload returns, the highest speed commanded afterwards is 0, the thread ends 256 ms after the sensor at the fence, and the only milestone is where it set off.  At `5e3aeffa` it was driven to 30 and recorded the next milestone (RLV8-A1).

Each reader answers "retired" as it would after a reload: the timetable loop and its completion wait end, the pending simulated clears stand down, and a path in progress stops its train at its next check.  One window is older and unchanged: a dispatch still locking its path when Unload (or any reload) lands starts its train once the lock completes (`Layout.java:8739`, unfenced after `configureAndLockPath` at `:8680`) and stops it at the first check of the loop, a moment later.

### RLV9-D2 - RLV8-C3: the parsed railway is taken under the lock and made current, and nothing else builds a Layout that could outrank it

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLV8-C3; CS3-C4 |
| **Where** | `MarklinControlStation.java:1001` (the seam), `:1118-1130`; `Layout.java:1692-1699` (`makeCurrent`); the three constructions of a Layout in `src/` - `Layout.fromJSON` (`:12129`), the builder in `getAutoLayout` (`MarklinControlStation.java:1037`) and `examples/FullAutonomyExample.java:24` |
| **Needs execution** | no - the claim ran green; Z6 is its mutation |

`parseAuto` is the only caller of `fromJSON`, and its only caller is `AutonomyViewerPanel.java:929`, so every load passes `makeCurrent`.  A lookup landing in the parse now either finds the old railway (a reload: it was invalidated and stopped first) or builds an empty one that `makeCurrent` then retires.  The failed parse returns an invalid Layout rather than null, and is made current as such, which is harmless.  `whileParsingForTest` is null in the application and is cleared in the claim's `finally`.

### RLV9-D3 - RLV8-C1: no wash after Unload

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLV8-C1 |
| **Where** | `TrainControlUI.java:28822-28831`; `refreshCoveredTrack` (captures no railway and empties both sets on the worker, then repaints only the squares that changed) |
| **Needs execution** | no - the claim ran green; Z3 is its mutation |

### RLV9-D4 - RLV8-C4: the carry and the fold now read a train under way by one rule, and the claim holds both

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RLV9-C4. |
| **Grade** | D |
| **Names** | RLV8-C4, RLV7-C1 |
| **Where** | `TrainControlUI.java:6433-6450` (the carry's second loop); `Layout.java:12031-12067` (the fold); `Point.java:1192`, `:1389`, `:1403` |
| **Needs execution** | no - the claim ran green; Z7 and Z8 are its mutations |

Both skip a train's kept point only where another train is recorded as standing on it, and both let a train passing through give way.  The side and the road are written only for the train a point records, which matches the carry's `null, null` for a train read at a released point, and a mid-path station reserved by its path has no side anyway: `Point.assign` clears it when the occupant changes.  Where the two can still differ is RLV9-C4.

### RLV9-D5 - RLV8-C5: the source is compared at the one door every switch of source ends in, and each door sets the preference before it gets there

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RLV9-C8. |
| **Grade** | D |
| **Names** | RLV8-C5, RLV7-C2 |
| **Where** | `TrainControlUI.java:614`, `:9512` (set when the window is given its model), `:10830-10840` (inside the posted runnable, so the preference is read after the door has written it); the doors - Switch to Central Station layout (`:22855` then `:22874`), Choose Local Data Folder (`:22928`, then `:22972` once the sync has worked), Initialize Local Layout (`:10779` then `:10791`) |
| **Needs execution** | no - the claim ran green; Z9 is its mutation |

A failed Choose Local Data Folder does not reach `initializeTrackDiagram`, so the remembered source stays the one whose railway is still loaded - which is right: choosing that folder again is the same railway.

### RLV9-D6 - RLV8-C6 and RLV8-C7: the records and the claims

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLV8-C6, RLV8-C7 |
| **Where** | behaviour.md :2203-2213; `testTheImportDoorReadsAnOldFile.java:1860` (the under-way claim's new precondition), `:2405-2470` (the delete claim); `AutonomyMenu.java:706`, `AutonomyViewerPanel.java:1687` (both delete doors call `autonomySetupDeleted`) |
| **Needs execution** | no |

The route paragraph now stands below the emergency-stop citation and names its own claim.  The delete claim calls the window's method rather than pressing either delete door; both doors call it, and are refused while autonomy is busy.

### RLV9-D7 - Round 9's bundles, in all eight languages

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | MT-567, MT-569; `traincontrol-properties-ascii-only` |
| **Where** | `messages*.properties`, keys `autosetup.ui.menuMassAssignTrainLengths`, `...Counted`, `autosetup.ui.tooltipExitGuard` (lines 1440-1446, 1706-1712) |
| **Needs execution** | no - a Python check over the archive; `core.testMessageBundles` and `regression.testEveryMessageKeyIsAskedFor` green |

Every file is ASCII only, with CRLF throughout; each key is present once in each language.  The counted label has `{0}` in all eight and no apostrophe of any kind; the uncounted one has no argument.  Every language now says the station is occupied (*optaget, belegt, ocupada, occupée, occupata, bezet, zajęta*), and French keeps U+2019.  The station walk's label is untouched in all eight, and each language tells the two walks apart.  The walk's dialog title is the same key, so it changed with the menu.  Two code comments still use the old name (`AutonomyEditorPanel.java:2350`, `:10194`); no text a user reads does.

### RLV9-D8 - Round 9's prefill, getRelated and grey do what the notes asked

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RLV9-C3, RLV9-C5 and RLV9-C6. |
| **Grade** | D |
| **Names** | MT-566, MT-505, FR-103, MT-587 |
| **Where** | `AutonomyEditorPanel.java:10131-10160` (the prefill only in the walk through every train, the same prefill on the re-ask), `:10384-10400` (selected on focus, the dialog's own focus request selecting it); `AutonomyChecks.java:200-210`, `:863-882`; `AutonomySession.java:6216-6280`; `TileAnnotation.java:139`, `:1804`; `LayoutLabel.java:1673` |
| **Needs execution** | no - the claims ran green |

OK or Enter on the prefilled box writes the length the train already has, which is harmless.  Each (station, signal) pair is its own finding, as it was, now naming its signal's square.  The grey is `POINT_INACTIVE`'s only use; the train mark keeps its own orange constant.  The new grey, rgb(128,130,134), is 68 levels darker than the grey label fill, rgb(196,198,202), which the claim allows to 90; Adam's words were *"just slightly darker"*, and MT-587 leaves the shade to him - worth his looking at on the grey labels, which are not the default.

### RLV9-D9 - The fold now takes the railway's monitor on the event thread during a run, and no load door can deadlock on it

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | OB-192 |
| **Where** | `AutonomyViewerPanel.java:879` (`toJSON(Map)` is `synchronized`, `Layout.java:12016`); `Layout.java:3801-3827` (`configureAndLockPath` holds it and reaches `TrainControlUI.repaintSwitch`, `synchronized` on the window, `:11340`); the load doors (`AutonomyMenu.java:313`, the panel's own, `TrainControlUI.java:6834`, `:8832`, `:23548`) |
| **Needs execution** | no |

Round 8's carry read the railway without its monitor; the fold with a train under way takes it on the event thread, after Yes.  The one thread that can then hold it for long is a dispatch still locking its path, waiting on the Central Station - seconds.  None of the load doors runs inside a method synchronized on the window, so the AB-BA of OB-192 cannot form; the worst case is the window pausing until that path is locked.

### RLV9-D10 - The round's claims ran green on the archive

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | 7aa18a31, da4d1b24, bbb488c7 |
| **Where** | the baseline in the method |
| **Needs execution** | done |

`core.testATrainIsDispatchedOnce` 7/7, `regression.testTheImportDoorReadsAnOldFile` 27/27, the four round-9 claims (`core.testAutonomyDiagramSession.testClickingAGuardNoticeOutlinesItsSignal` and `testAGuardOffTheWayInIsNoticed`, `core.testAutonomyDiagramMonitor.testASquareAutonomyWillNotChooseIsGrey`, `regression.testTheEditorSaysWhatItsToolsDo.testTheGuardItemsSayWhatTheGuardsDo`) 4/4, `core.testMassAssignLengths` 74/74, `core.testMessageBundles` 16/16, `regression.testEveryMessageKeyIsAskedFor` 2/2, `regression.testAPendingTurnSurvivesTheRebuild` 2/2; no skips.
