# RLV8 - Validation, round 8: round 7's fixes - the route editor's Save, a reload with a train under way, the rule it is read by, the railway a reset forgets, and the refusal at another configuration (TrainControl 3.0.0)

**Status:** open

**Prefix:** RLV8

**Reviewed:** branch `autonomy-diagram-r0` at `5e3aeffa`, read and run from `git archive 5e3aeffa` unpacked into `validate30/rlv8/a8` (TC30's working tree was never read).  Scope `ab8aa1a9..5e3aeffa`: claims `631ebd2b`, fixes `1ed10c7f`, `a1241d73`, `46474dca`, the RLV7-C4 claim and fix `247a5fd1` and `60411a00`, the Readme `dfff7467`, the records `4f6314bf` and `79301459`, and the Return Home claim `5e3aeffa`.  Pre-round-8 behaviour was measured on `git archive ab8aa1a9` (`validate30/rlv8/o8`).  2026-09-27.

## Method

**Read whole:** the brief; `docs/reviews/README.md`; RLV7.md as written and as dispositioned (`docs/reviews-2026-09-25/RLV7.md` at `4f6314bf`); every commit message in scope; the source diff `ab8aa1a9..5e3aeffa` (Layout, AutonomyViewerPanel, DiagramMonitorDriver, RouteEditorFrame, TrainControlUI, MarklinControlStation, ViewListener, eight bundles); the claims' diffs in `testTheImportDoorReadsAnOldFile`, `testATrainIsDispatchedOnce` and `testReturnHomeShowsItIsWorking`; the behaviour.md, open-questions.md and issues.md hunks.

**The code at 5e3aeffa, around each change:** `AutonomyViewerPanel.load`, both `loadPrepared`s, `revert`, `refresh`, `delete`, the import's capture (:1380-1400) and `refreshRoster`; `TrainControlUI`'s exit save, `carryTheTrainsAcross`, `captureRunningLayout`, `resetAutonomySession`, `forgetTheRailway`, `prepareAutonomyReload`, `isAutonomyBusy`, `whereTheTrainsAre`, `putTheTrainsBack`, `rebuildRunningLayoutFromSetup`, `unloadAutonomy`, `autonomySetupDeleted`, `initializeTrackDiagram` and its three callers, `layoutEditingComplete` and every caller, `refuseWhileAutonomyRunning`, `locomotiveGestureOnDiagram`, `followDirectionChanges`, `updateVisiblePoints`, `refreshCoveredTrack`, `checkAutoLayoutLatency` and the ping timer, the Auto tab's settings handlers; `RouteEditorFrame.onSave`; `MarklinControlStation`'s `hasAutoLayout`, `getAutoLayout`, `getAutoLayoutIfLoaded`, `clearAutoLayout`, `parseAuto`, `rebindRouteTiles`, `isAutonomyRunning`; `Layout`'s constructor, `isCurrentLayout`, `isRunning`, `runLocomotive`, `configureAndLockPath`'s claim, `executePathInternal`'s milestone loop, early release and version fence, `getLastPointsReached`, `lastKnownPoint`, `isABarredCopyOfAStation`, `addTimetableEntry`; `AutonomySession.captureFromLayout`; `DiagramMonitor.attach`; `LayoutRightclickAutonomyMenu`'s constructor.

**A census** of all 194 `getAutoLayout()` calls and 73 `hasAutoLayout()` readers in `src/`, made by a read-only helper agent over the archive and spot-checked by me at every line cited below.

**Mechanical checks** (a Python script over the archive's bundles): the new key in all eight, ASCII bytes only, `\uXXXX` escapes, no straight apostrophe, placeholders `{0}` and `{1}` against the call's two arguments, line endings.

**Runs.**  Every JVM through the project's `docs/tools/one.sh` in a copy (`one-m.sh`, the same script with `-methods` for a method list and each class's output kept), `TC_SCRATCH` in `rlv8/scratch8`, started only after a poll found no `java.exe`, `javaw.exe` or `javac.exe` running; one start lost the runner's lock to a run of the round's other validator and waited.  Nothing was killed.

- **Baseline, 5e3aeffa:** `core.testATrainIsDispatchedOnce` 5/5, `ui.testReturnHomeShowsItIsWorking` 1/1, `core.testMessageBundles` 16/16, `regression.testEveryMessageKeyIsAskedFor` 2/2, `regression.testTheImportDoorReadsAnOldFile` 25/25; no skips.
- **Mutations, in a copy (`m8`), restored from the archive after each:** MA (the second loop of `whereTheTrainsAre` skipped), MC (`getLastPointsReached`'s still-locking branch removed) and ME (the diagram monitor's lookup builds again), together: the five under-way/switch/Unload claims and seven classes that read the carry or the monitor, 48 tests, all green - **all three survive**.  MD (`refreshRoster` builds again): `testAnUnloadForgetsTheRailway` **red** (1 of 1).  The dispatcher's X1-X14 were not repeated.
- **Probes** (a scratch class, `regression.rlv8Probe`, run on 5e3aeffa and, unchanged, on ab8aa1a9; the live-snapshot sandbox and the window, as the claims open them): a setting changed on the running layout, then a reload with a train under way and at rest; timetable capture across the same reload; Unload with a train under way, then its awaited sensor fired; a hand reversal after Unload; the grey wash across Unload; a load with nothing loaded raced by a model lookup, as the latency ping makes.  Results are in the findings.

**Nothing written** outside `validate30/rlv8/` and this file.  No git state changed; no commit.  Nothing under any `cs2_sample_layout/` was read or written by me; the runner fingerprints the archive's copy around each run and reported no change.  The real `LocDB.data` and `UIState.data` - the main checkout's and `C:/Users/adamo`'s - were hashed before the first JVM and after the last, and are unchanged (`c8abacb4...`, `1bd9acb2...`, `08d10085...`, `7be43483...`).  `rlv8/` also held an earlier, abandoned attempt's files dated 2026-09-26 (`tree/`, `work/`, `scratch/`, `logs/base.log`); none of them was used.

**Counts:** 1 A, 1 B, 7 C, 8 D.

**The fixes do what RLV7 asked at the doors it named** (RLV8-D1 to RLV8-D6).  **What round 8 broke is the hasAutoLayout() change** - the answer the brief singled out.  The empty railway the autonomy panel's list built at every panel mount was not only answering "yes" about nothing: its constructor ticks `Layout.layoutVersion`, and that tick is what retired the railway Unload discards.  Without it, the unloaded railway's trains are driven on (RLV8-A1).  And the empty railway still comes back within five seconds from the latency ping, so what reads `hasAutoLayout()` now answers by the track power and the clock (RLV8-C2, RLV8-C3), and a wash is left on the diagram (RLV8-C1).  Separately, the carry that replaced the fold at a reload confirmed during a run keeps only where the trains stand (RLV8-B1).  **Everything above D is new in round 8.**

---

### RLV8-A1 - Unload no longer retires the railway it unloads: a train that reaches the sensor its path was waiting for is driven on at its line speed by the unloaded railway's thread

| | |
|---|---|
| **Disposition** | Fixed - Unload retires the railway it drops (Layout.retireEveryLayout), as every load retires the one it replaces, so a path's thread on it stops its train at the next sensor.  claims 7aa18a31 (red first), fix 78074e09; mutation Z1 red. |
| **Grade** | A.  Wrong behaviour on the layout, from an ordinary gesture: Unload while a train is under way, answered Yes ("stop them").  Yes sets the train's speed to 0 and releases nothing.  If the train then trips the sensor it was waiting on - coasting onto it under the decoder's deceleration, or driven over it by hand - the thread of the unloaded railway commands it back to line speed.  It is stopped again at the thread's next check after something has built a Layout.  The milestone it has just reached posts a refresh that builds one on the event thread, and with the station connected and the power on the latency ping's next reply does too, within five seconds (RLV8-C2).  So the train runs for an instant where the event thread wins that race, and for a whole block where the thread reaches its next sensor's wait first.  Until a Layout is built, a train that trips its sensor is re-driven - and those first seconds are the ones in which a stopping train is still rolling.  **New in round 8:** `a1241d73` (the panel's list) and `1ed10c7f` (the diagram monitor); at `ab8aa1a9` the train stays stopped. |
| **Names** | RLV7-C2 (its roster fix), CS3-C4 (which counted the version tick as a harm); OB-143 (a stopped train between sensors) |
| **Where** | **The retirement that went:** `Layout.java:866-867` (the constructor: `Layout.layoutVersion += 1; this.version = Layout.layoutVersion;`); `:1679-1681` (`isCurrentLayout` compares the two); `AutonomyViewerPanel.java:1809-1816` (`refreshRoster`, run as `resetAutonomySession`'s `mountAutonomyControls` remakes the panel, `TrainControlUI.java:3169`, now asks `getAutoLayoutIfLoaded`); `DiagramMonitorDriver.java:312-318`.  **Unload retires nothing itself:** `TrainControlUI.java:8839-8855`; `MarklinControlStation.java:1049-1066` (`clearAutoLayout`: `invalidate()` and `stopLocomotives()`, which is `running = false`, `Layout.java:1912-1915`); nothing in the driving loop asks `isValid()`.  **The thread:** `Layout.java:8738-8751` (each point: `if (isCurrentLayout()) ... loc.setSpeed(calculatedSpeed)`); `:9073-9092` (the fence, after each sensor: `if (!isCurrentLayout()) { loc.setSpeed(0); ... return true; }`) |
| **Needs execution** | done - probe `probeUnloadThenTheSensorFires` |

**What happens.**  A path's thread waits at each sensor, then asks `isCurrentLayout()`: a railway replaced since it set off stops the train and returns.  Every load replaces the model's Layout with a new one, which ticks the version.  Unload does not: `clearAutoLayout` drops the reference and clears `running`.  Until round 8 the tick came anyway, by accident - Unload remakes the autonomy panel, and the panel's list asked `getAutoLayout()`, which built an empty Layout.  RLV7-C2 removed that lookup, and the diagram monitor's, as a defect (it was one).  Nothing took over the retirement.

**Measured,** on the live-snapshot sandbox with the window.  One standing train (EN57-947) is sent from BottomInner towards BottomMainC; its thread waits on sensor 1008.  Unload is answered Yes; its speed is 0.  Then sensor 1008 is set, as the train rolling onto it would.

| | 5e3aeffa | ab8aa1a9 |
|---|---|---|
| after Unload: `hasAutoLayout()` | false | true (the list's empty Layout) |
| after Unload: the unloaded railway `isCurrentLayout()` | **true** | false |
| highest speed commanded over the next six seconds | **30** | 0 |
| milestones reached | BottomInner, 1 - Main 12,7 | BottomInner |

At 5e3aeffa the thread passed the fence, recorded the milestone and set the train to 30 for what came next.  Within the six seconds it had set it to 0 again and ended, without the next sensor being set - so here the milestone's own refresh built a Layout on the event thread before the thread's next check.  With no station connected the probe has no ping.  On his railway the ping would build one at most five seconds after Unload, so the window is those seconds - during which a train stopping from speed is still rolling.

**Direction:** retire the railway where it is cleared, not by whoever next builds one.  `clearAutoLayout` should make `isCurrentLayout()` answer false for the Layout it drops - a version tick of its own, or a retired flag the fence asks - and stop its active trains, as a load does.  Claim: dispatch a train, Unload answering Yes, fire its awaited sensor, and assert its speed stays 0 and `hasAutoLayout()` stays false.

---

### RLV8-B1 - A reload confirmed during a run now folds nothing: the Auto tab's settings changed since the last save, and a timetable captured this session, go back to what the file had

| | |
|---|---|
| **Disposition** | Fixed - a reload confirmed with a train under way folds the running layout again, each train under way written on the one point it is kept at (Layout.toJSON(keptAt), Point.toJSON(standing)): the Auto tab's settings and a captured timetable are kept, and the train stands once.  Only an edit waiting makes the load carry instead.  Said in behaviour.md.  claims 7aa18a31 (red first), fix 78074e09; mutations Z2 red, Z8 red. |
| **Grade** | B.  A user's settings and a captured timetable are replaced, silently, by the file's.  This is reached by choosing a configuration or importing during a run and answering Yes to *"Reloading will stop them and abandon the current run"*.  The dialog says the run is abandoned; it does not say settings or a recorded timetable go with it.  Not A: no train moves, and settings can be set again, as the project has graded ACC-B3 and WKW-B2 (an edit silently lost).  The timetable is the heavier half; Adam may prefer A.  **New in round 8** (`1ed10c7f`): at `ab8aa1a9` the same reload kept both. |
| **Names** | RLV7-B1 (its direction: *"carry the trains there"*), RLV6-B1; ACC-B3, WKW-B2 |
| **Where** | **The carry instead of the fold:** `AutonomyViewerPanel.java:782-801` (`holdsAPath` is `isRunning()`, and with it the configuration running is carried, never folded); `:876-890` (`loadPrepared`'s fold now skips a running layout); `TrainControlUI.java:3025-3054` (the carry records `whereTheTrainsAre` and the pending turns, and nothing else).  **What only a fold kept:** `AutonomySession.java:5595` (`captureFromLayout` copies every top-level key of the running layout into the configuration's globals: `maxDelay`, `maxActiveTrains`, `timetable`, ...); `TrainControlUI.java:26745-26953` (the Auto tab's handlers write the running layout only, e.g. `setMaxActiveTrains`); `Layout.java:888-918` (`addTimetableEntry`: capture appends to the running layout's list).  **Why `isRunning()` is nearly every reload during a run:** `Layout.java:1865-1869` (`running`, or an active train, or a live locomotive thread); `:4609-4680` (each autonomy thread lives until its current path and delay end, after `running` is cleared).  **The record:** behaviour.md :2467-2470 says the load "carries the trains the same way and folds nothing", and nothing about the rest |
| **Needs execution** | done - probes `probeSettingUnderWay`, `probeSettingAtRest`, `probeTimetableCapturedUnderWay` |

**Measured,** on the live-snapshot sandbox with the window.  `maxDelay` (2) is set to 7 and `maxActiveTrains` (0) to 5 on the running layout, as the Auto tab's sliders do while idle.  For the timetable, capture is switched on and a train is sent, which captures one entry.  Main is then reloaded from the panel, answering Yes.

| | 5e3aeffa | ab8aa1a9 |
|---|---|---|
| at rest: `maxDelay` / `maxActiveTrains` after the reload, and in the configuration | 7 / 5, stored 7 / 5 | 7 / 5, stored 7 / 5 |
| a train under way: the same | **2 / 0, stored 2 / 0** | 7 / 5, stored 7 / 5 |
| a train under way: timetable entries before / after the drive / after the reload / stored | 1 / 2 / **1 / 1** | 1 / 2 / 2 / 2 |

**Why.**  RLV7-B1's direction was to fold no layout holding a path, and carry the trains.  The fold, though, is also the only door by which the Auto tab's settings, a captured timetable and the per-square keys the running layout holds reach the configuration.  The carry keeps placements and pending turns only.  With an edit waiting that trade was made on purpose (the edit is newer); with the flag down nothing is newer, and everything but the placements and the pending turns is simply dropped.  `holdsAPath` is also wider than its name: after Yes, every autonomy thread still in its path or its delay keeps `isRunning()` true, so almost any reload confirmed during an autonomy run takes this door, not only one with a train between sensors.

**Direction:** while no edit waits, keep the fold for everything but the trains under way.  Either fold the running layout with those trains placed by the carry's rule, or fold its globals and non-placement keys before carrying.  Say it in behaviour.md.  Claim: this probe's settings and timetable, asserted after a reload with a train under way.

---

### RLV8-C1 - After Unload the diagram keeps the unloaded configuration's grey wash: `updateVisiblePoints` returns before it refreshes the covered track when there is no railway

| | |
|---|---|
| **Disposition** | Fixed - with nothing loaded, updateVisiblePoints empties the covered track before returning.  claims 7aa18a31 (red first), fix 78074e09; mutation Z3 red. |
| **Grade** | C.  A stale mark: after Unload, the track the unloaded configuration's standing trains blocked stays faded, as if still refused.  It clears only when something builds a railway again and the diagram is next refreshed (RLV8-C2).  Cosmetic, but it is the diagram saying track is blocked when nothing is loaded.  **New in round 8** (`a1241d73`, `1ed10c7f`). |
| **Names** | RLV7-C2; W7B-B1, MT-278 (the wash) |
| **Where** | `TrainControlUI.java:28790-28792` (`if (!this.model.hasAutoLayout()) return;`, before `refreshCoveredTrack()` at :28801); `:7856-7870` (`refreshCoveredTrack` itself handles no railway: it empties both sets); `:7746-7749` (`isTrackBlocked` reads the set); `LayoutLabel.java:1486` (the fade, drawn whatever is loaded) |
| **Needs execution** | done - probe `probeTheWashAfterUnload` |

**Measured:** the covered and blocked sets hold 16 squares before Unload.  After Unload and a repaint, at 5e3aeffa they still hold 16 each; at ab8aa1a9 they hold 0, because the list's empty railway let `updateVisiblePoints` run on and recompute against nothing.

**Direction:** refresh the covered track before the early return (or at Unload), so no railway means no wash.

---

### RLV8-C2 - RLV7-C2's roster fix holds only offline or with the power off: the latency ping still builds the empty railway on every reply, so what reads `hasAutoLayout()` - the diagram's right-click menu, its Delete, Ctrl+X and Ctrl+V, the captions - now changes with the track power and the seconds since start-up or Unload

| | |
|---|---|
| **Disposition** | Fixed - the latency ping and the timetable repaint every milestone runs ask getAutoLayoutIfLoaded and build nothing (mutations Z4 red, Z5 red); so do Find Similar and the layout suppliers of the editor and the diagram's menus, whose readers each take no railway as nothing to offer - by reading, not claimed.  claims 7aa18a31 (red first), fix 78074e09. |
| **Grade** | C.  Behaviour that depends on the power and the clock.  Nothing here harms the railway; the fence of RLV8-A1 and the race of RLV8-C3 are the same cause's other consequences, graded apart.  **New in round 8** as an inconsistency: until `a1241d73` the list's lookup made the answer "yes" at every start, so every reader saw the same empty railway.  The builders themselves are older. |
| **Names** | RLV7-C2, CS3-C4; `fix-one-site-sweep-the-siblings` |
| **Where** | **The ping:** `TrainControlUI.java:10635-10640` (`checkAutoLayoutLatency`: `if (model.getPowerState()) { Layout l = model.getAutoLayout();`), reached from every ping reply (`:10627`) and the lost-connection branch (`:9880`); the timer, `:9863-9895`, every `PING_INTERVAL` (5000 ms, `:348`) from start-up.  **Other routine builders** (census, each checked): `:21569` then `:28974` (deleting a locomotive: `repaintTimetable`'s `this.model.getAutoLayout().getTimetableSnapshot()`); `:21160` (Find Similar); `:4792` (the diagram's Add to Autonomy supplier); `LayoutEditor.java:1705`, `:1711`, `:1717` (the editor's autonomy mode); `TrainControlUI.java:4065-4072` (every milestone's refresh).  **Readers whose answer the user sees change** (their "no" branches are the ones their comments intend): `LayoutRightclickAutonomyMenu.java:380-388` (no railway: only the setup submenu, and a background right-click shows no menu; an empty one: greyed Start and Return Home first); `TrainControlUI.java:7126` (no railway: Delete, Ctrl+X and Ctrl+V over a square mean the locomotive buttons; an empty one: swallowed, with *"no autonomy point here"* logged); `:1535-1539` with `:11684` (Show Inactive Labels off: captions shown with no railway, all hidden with an empty one) |
| **Needs execution** | yes - the menu and the keys with the station connected, before and after the first ping reply |

**What changed.**  RLV7-C2 replaced four lookups that built a railway (the carry, the rebuild, the monitor, the list), and RLV7-C2's disposition reads as though `hasAutoLayout()` now answers no when nothing is loaded.  It does, until the next of the others runs.  With the station connected and track power on, that is the ping's next reply - within five seconds of start-up with nothing loaded, and of every Unload.  Offline, or with the power off, it is whichever routine gesture comes first.  The readers were all written while the answer was always yes; about half behave differently on no, and a user now meets either behaviour depending on the power and the timing.

**Direction:** make the ping ask `getAutoLayoutIfLoaded()` (it has nothing to check on a railway that is not loaded), and sweep the other builders above with it.  That also closes RLV8-C3.  It lengthens RLV8-A1's window, though - to unbounded once every builder is swept - so A1's own fix must come first.

---

### RLV8-C3 - A load that starts with no railway in the model can be left retired by a lookup that lands in its parse: `parseAuto` assigns outside the lock, and a railway built meanwhile is newer than the one loaded

| | |
|---|---|
| **Disposition** | Fixed - parseAuto takes the parsed railway under the lock and makes it the current one (Layout.makeCurrent), whatever was built during the parse; the claim lands a lookup there through a seam, whileParsingForTest.  claims 7aa18a31 (red first), fix 78074e09; mutation Z6 red. |
| **Grade** | C.  Autonomy dead until the next load: the configuration loads, valid and named as running, but `isCurrentLayout()` is false, so a train sent on it never gets under way.  It needs a lookup that builds a railway during the parse, and the model empty when the load starts.  The ping is the one thread that does that routinely, and its own first reply after start-up or Unload (with the power on) also fills the model, so the window is narrow.  **New in round 8:** until `a1241d73` the model was never empty when a load began. |
| **Names** | CS3-C4 (the same interleaving, then with route threads); RLV8-C2 |
| **Where** | `MarklinControlStation.java:1107` (`this.autoLayout = Layout.fromJSON(s, this);`, outside `autoLayoutLock`; the field is not volatile, `:314`); `Layout.java:12040-12042` (`fromJSON` constructs first, ticking the version, then parses); `MarklinControlStation.java:1020-1036` (`getAutoLayout` builds under the lock when it reads null) |
| **Needs execution** | done - probe `probeALoadRacedByTheLatencyPing` |

**Measured:** after Unload, a thread watching `Layout.layoutVersion` calls `getAutoLayout()` the moment the load's Layout has been constructed and before the model holds it - as a ping reply landing in the parse would.  At 5e3aeffa it built a railway mid-load.  The loaded layout had 87 points, was valid, and Main read as running, but `isCurrentLayout()` was false, and no train could be sent on it.  At ab8aa1a9 the model still held the list's empty railway after Unload, the lookup built nothing, and a train was sent as usual.

**Direction:** assign in `parseAuto` under `autoLayoutLock`, and have `getAutoLayout` not build while a parse is in flight - or remove the builders (RLV8-C2).

---

### RLV8-C4 - The carry's second reading - a train whose last known point a release has cleared, atomic routes off - drops the train wherever any other train holds that point, including one only routed through it; and no claim reaches that branch

| | |
|---|---|
| **Disposition** | Fixed - a train is read (the carry) and written (the fold) at the point it was last seen though another train's path runs through it; only a train standing there displaces it.  claims 7aa18a31 (red first), fix 78074e09, the claim's fixture reserving as a locked path does; mutations Z7 red, Z8 red. |
| **Grade** | C.  DW-A1's shape (the train modelled on the square the setup had it on before the run), after two preconditions: a reload confirmed during a run with atomic routes off, and a second train that has since locked a path into or through the point the first one set off from or last tripped.  Unclaimed: MA and MC survive every claim.  **New in `1ed10c7f`.** |
| **Names** | RLV7-C1; DW-A1, OB-183; VD10-C15 (the note that his railway has run non-atomic) |
| **Where** | `TrainControlUI.java:6421-6431` (*"still there, unless another train stands on it now"*: the test is `kept.getValue().getCurrentLocomotive() != null`); `Layout.java:3854` and `Point.reserve` (a locked path makes its train every point's occupant); `:8990-8991` (non-atomic: the point behind is emptied once the tail has passed, so another train may lock it); `TrainControlUI.java:6552-6600` (`putTheTrainsBack` moves only the trains in `standing`); the snapshot's `configuration-Main.json` (`atomicRoutes: true`, which every claim runs) |
| **Needs execution** | yes - a claim with atomic routes off |

**What it does.**  With atomic routes off, a train's start (and a station it has passed) is released behind it.  So its last known point may no longer hold it, and the second loop records it there - unless the point's occupant is not null.  A second train that has locked a path through that point is its occupant, though it stands elsewhere and is itself carried to its own start.  The first train then has no entry at all.  `putTheTrainsBack` leaves it where the rebuild from the setup put it: where the last fold had it, before the run.  With the flag down that fold was the load before the run.

**Direction:** skip only where another train is recorded in `standing` at that point, not wherever the point has an occupant; and add a claim with atomic routes off, which is the mode the comment at `Layout.java:8933-8937` says his railway ran.

---

### RLV8-C5 - A switch of railway over the same folder, while an edit waits, now stands every train the run moved back where it set off - and the claim asserts that outcome

| | |
|---|---|
| **Disposition** | Fixed - a switch of layout source forgets the railway only when the source changes (railwaySource): the folder in use chosen again keeps it, and the load carries.  claims 7aa18a31 (red first), fix 78074e09, da4d1b24 (the claim's second half moves another train); mutation Z9 red. |
| **Grade** | C.  DW-A1's shape after the one-event race that raises the flag, and only when the folder chosen is the one in use - Choose Local Data Folder picking the same folder, to reload it.  Until round 8 the load after it carried the trains by Point name, which on the same railway is right (RLV7-C2 said so: *"Between two copies of the same railway, carrying is what OB-183 would want"*).  **New in `1ed10c7f`**, and codified by `631ebd2b`. |
| **Names** | RLV7-C2; DW-A1, OB-183 |
| **Where** | `TrainControlUI.java:10806-10810` (`initializeTrackDiagram` forgets the railway on every door); `:22884-22945` (Choose Local Data Folder: any folder, the one in use included); `testTheImportDoorReadsAnOldFile.java:2176-2238` (*"The door every switch of layout source ends in, `initializeTrackDiagram`, over the same folder"* - and it asserts the moved train is back on `move[1]`, where it set off) |
| **Needs execution** | no - the claim is the evidence |

**Direction:** forget the railway only when the folder changes (compare the layout path before and after), or keep the flag and the name and say why.  Then the claim should use a second folder, as RLV7-C2's request did.

---

### RLV8-C6 - behaviour.md: the new "Saving a route leaves autonomy alone" paragraph was put between the emergency-stop paragraph and its claims, so those claims now read as the route paragraph's, and the route paragraph names none of its own

| | |
|---|---|
| **Disposition** | Fixed in the records - the route paragraph moved below the emergency-stop citation, with its own claim (78074e09). |
| **Grade** | C.  A record that cites the wrong tests for two rules.  **New in `1ed10c7f`.** |
| **Names** | RLV7-A1 |
| **Where** | behaviour.md :2206-2211 (the paragraph, then with no blank line `` `core.testAStopRouteStandsAlone`, `ui.testCommandTableMarks.testAnEmergencyStopStandsAlone`, `ui.testARouteOverATrainAtItsDoors`. ``, which closed the emergency-stop paragraph above it) |
| **Needs execution** | no |

**Direction:** move the paragraph below the citation, and cite `regression.testTheImportDoorReadsAnOldFile.testSavingARouteWhileAutonomyRunsLeavesTheRunAlone`.

---

### RLV8-C7 - Claim gaps in the round's claims: the delete doors' forgetting has no claim, and the under-way reload with an edit waiting does not check the configuration still has the moved train where it set off, as RLV7-C5's disposition says every such claim does

| | |
|---|---|
| **Disposition** | Fixed - a claim deletes the setup with the flag up and asserts the flag and the name are gone (mutation Z10 red), and the under-way claim checks the configuration has the moved train where it set off.  7aa18a31. |
| **Grade** | C.  Claims that would not catch a regression of their own disposition's words.  **New in `631ebd2b`.** |
| **Names** | RLV7-C2, RLV7-C5 |
| **Where** | `TrainControlUI.java:8883-8891` (`autonomySetupDeleted` calls `forgetTheRailway`; no test calls it, nor the two doors that do, `AutonomyMenu.java:706` and `AutonomyViewerPanel.java:1690`); `testTheImportDoorReadsAnOldFile.java:1854-1866` (`reloadConfirmedWhileATrainIsUnderWay(true)`: an edit is written and the flag raised, and there is no `assertTheConfigurationHasItWhereItSetOff` after `moveAStandingTrain`, unlike `:1279`, `:1359`, `:1450` and `:1538`) |
| **Needs execution** | no - by `grep` and reading; MA, MC and ME are the run half (RLV8-C4, RLV8-C2) |

**Why each matters.**  By reading: without the forget at the delete doors, a flag left up and a name remembered for a configuration that no longer exists would make the next choice of another configuration try to carry the deleted one first.  That carry cannot leave a valid layout, so with the flag up the choice would be refused by RLV7-C4's message until Unload.  In the under-way claim, `assertStandsWhereItWasMoved` passes on a plain rebuild from the setup if the setup ever came to hold the move (RLV7-C5's argument).

**Direction:** a claim that deletes the running configuration with the flag up and asserts the flag and the name are gone; and the precondition in the under-way claim.

---

### RLV8-D1 - RLV7-A1: the route editor's Save needs nothing else `layoutEditingComplete` did, and every other door that completes a diagram edit is refused during a run

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLV7-A1; MKR-B1, OP2-C9 |
| **Where** | `RouteEditorFrame.java:3368-3382` (the comment at :3375); `MarklinControlStation.java:3715-3749` (`rebindRouteTiles`, from every route-database write: `:2277`, `:2316`, `:2362`, `:3781`, `:3824`); `LayoutLabel.java:371` (a tile resolves its route at the click); `TrainControlUI.java:23405-23529`; `:25661`, `:25986`, `:26079` (combine, rename or duplicate, delete a page: each `refuseWhileAutonomyRunning`); `:26250` (the legacy editor, `if (true) return;`) |
| **Needs execution** | no (the dispatcher's X1 went red) |

`layoutEditingComplete` re-read the pages, reset and reloaded autonomy, refreshed popped-out pages and re-applied always-on-top.  A route touches none of those.  Its tiles are rebound by id after every write to the route database, a tile asks for its route when clicked, and the Save still calls `refreshRouteList` and `repaintLayout`.  The route editor copies the main window's always-on-top rather than changing it (`RouteEditorFrame.java:285`).  A popped-out page draws the same rebound components.  The remaining callers are page edits: the Layout Editor, which cannot open during a run (OB-047), and three page operations that refuse one.

### RLV8-D2 - RLV7-B1: no fold reads a layout that holds a path any more, at any of the four

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLV7-B1; X3 (its disposition) |
| **Where** | `AutonomyViewerPanel.java:876-878`; `TrainControlUI.java:2545-2549` (exit), `:3087-3090` (`captureRunningLayout`); `AutonomyViewerPanel.java:1389-1390` (the import, `!isAutonomyBusy()`) |
| **Needs execution** | no - the claims ran green (baseline) |

These are the only four callers of `captureFromLayout`.  X3's survival is as its disposition says.  `loadPrepared`'s own guard is reached with a running layout only when the carry of the configuration running fails with the flag down.  That configuration built when it was loaded and no edit has reached it since, and a setup edit cannot make a blocking problem (those come from the graph).  So the guard is the siblings' rule, kept, and unreached.

### RLV8-D3 - RLV7-C1: `getLastPointsReached` reads the milestones as its javadoc says, and the hand-built claim holds it to that

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLV7-C1 |
| **Where** | `Layout.java:1588-1636`; `:8667-8668` (the start is milestone 0); `:9103-9109` (a sensor point is recorded after its sensor, a sensorless one as the loop reaches it); `:3817` (`takingPath` before the lock); `testATrainIsDispatchedOnce.java:328-414` |
| **Needs execution** | no |

The start comes first, a sensorless point is skipped, a sensor that is no station is skipped, and a station or a barred copy of one is kept.  One reading gap, not graded: a copy with a startable twin is not counted as a station, although `putTheTrainsBack` would stand the train on that twin.  So a train that tripped such a square is read at an earlier point.  That is conservative, and the rule itself already approximates.

### RLV8-D4 - RLV7-C2: a carry now happens only for the configuration running or the one the reset forgot, and every door that replaces the railway forgets it

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLV7-C2; X12 |
| **Where** | `TrainControlUI.java:3160-3164` (the one writer of `forgottenByTheReset`); `:3004-3009`, `:8850`, `:8889`, `:10810` (the three doors); `AutonomyViewerPanel.java:792-795`, `:806` |
| **Needs execution** | no - MD red, the claims green |

`resetAutonomySession` has four callers, and three of them are those doors.  The fourth, the reset after a diagram edit, reloads the name it forgot.  X12 is masked as its disposition says: after a switch the flag is down and a switch is refused while running, so the branch is never entered.  The exit save is unchanged by the hasAutoLayout change: it needs an active configuration.  A hand reversal after Unload is followed in neither version, since Unload nulls the session - measured by `probeADirectionFollowedWithNothingLoaded`: facing N before and after, at both commits.

### RLV8-D5 - RLV7-C4: the refusal fires only where the carry of the one running left no valid layout while the flag is up, counts what stops it, leaves the dropdown on the one running, and logs no start-up line

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLV7-C4; SVN-B10; X13, X14 |
| **Where** | `AutonomyViewerPanel.java:806-834`, `:863-917`, `:1756-1796` (`refresh` reselects the store's active configuration) |
| **Needs execution** | no - the claim green |

The menu's radio sets only the dropdown before `load`, so `revert` returns the store to the configuration running.  The import's reload asks for the configuration running whenever one is, so it never reaches this branch with another.  The count is the running configuration's own.  X14's explanation holds: a setup edit makes errors, not blocking problems.

### RLV8-D6 - The new key, in all eight bundles

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | `traincontrol-properties-ascii-only` |
| **Where** | `messages*.properties`, key `autosetup.ui.errorCannotKeepTrainsBeforeChoosing` (lines 1543-1546) |
| **Needs execution** | no - `core.testMessageBundles` 16/16, `regression.testEveryMessageKeyIsAskedFor` 2/2 |

Once in each of the eight bundles, with no byte above 127, and each accent as `\uXXXX`.  French and Italian use U+2019, and no language has a straight apostrophe.  Every language has `{0}` and `{1}`, and the one call passes two arguments: the configuration running, then the one chosen.  Line endings are CRLF throughout each file.  The term for the edit follows each language's `autosetup.log.setupEditNotApplied` - German *Änderung an der Konfiguration*, Dutch *wijziging van de instellingen*.

### RLV8-D7 - The claims that exist are real where I could test them, and the round's other test changes hold

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLV7-C2, RLV7-C5; `5e3aeffa` |
| **Where** | `testTheImportDoorReadsAnOldFile.java:2269-2340`; `testReturnHomeShowsItIsWorking.java:59-73`, `:106-107`; `a1241d73`'s `anEditWaits` |
| **Needs execution** | done - baseline and MD |

The Unload claim goes red when the list builds a railway again (MD).  The Return Home claim now waits for the start-up load and requires a valid layout rather than skipping, and ran 1/1.  `anEditWaits` saves the edit to the file, which is what a declined edit is.

### RLV8-D8 - RLV7-C3 is filed, and the round's records say what the code does, apart from RLV8-C6 and RLV8-B1's omission

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLV7-C3, OB-306 |
| **Where** | issues.md (OB-306, raised from RLV7-C3, with its reach and direction); behaviour.md :2467-2472; open-questions.md :42, :429-430 |
| **Needs execution** | no |

OB-306's text matches RLV7-C3 and its disposition.  behaviour.md's sentences on RLV7-B1, C1, C2 and C4 match the code.  Two exceptions: RLV8-B1's loss is unstated, and RLV8-C6 misplaces a paragraph.
