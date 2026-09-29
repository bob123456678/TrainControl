# RLV12 - Validation, round 13: the fixes for RLV11 - no setup edit during a run, the trains kept apart, no turn for a train under way, the railway asked and not built, the gap-fill's question (TrainControl 3.0.0)

**Status:** open

**Prefix:** RLV12

**Reviewed:** branch `autonomy-diagram-r0` at `98407a74`, read and run from `git archive 98407a74` unpacked into `validate30/rlv12/` (`src98` for reading and the baseline; `p12` for the probes; `mR`, `mO` for mutations), `cs2_sample_layout` excluded and an empty folder of that name made for the runner.  TC30's working tree and the main checkout's were never read; git was used only as `git -C tc-30 log / show / diff / archive`.  Scope `75c601b9..98407a74`: `8ca5eb12` (the monitor guard's allowance, RLV11-C4), `8a9575f2` (the runners), the claims `ae0285f3`, the fixes `fd31f56f`, and the records `23f4826d`, `98407a74`.  2026-09-28.

## Method

**Read whole:** the brief; `docs/reviews/README.md`; RLV11.md as written (`validate30/RLV11.md`) and as dispositioned (`docs/reviews-2026-09-25/RLV11.md` at `98407a74`, diffed against it - only the eight dispositions of B1, B2 and C1-C6 differ); every commit message in scope; the source diffs of `fd31f56f` (Layout, AutonomySession, AutonomyEditorPanel, AutonomyViewerPanel, TrainControlUI, MarklinControlStation, MarklinRoute, Route, the eight message bundles) and `8a9575f2` (`one.sh`, `battery.sh`); the claims `ae0285f3` and `8ca5eb12`'s allowance; the records' hunks (behaviour.md, open-questions.md, the 17 RLV11 rows of findings.tsv) and a copy of `triage.db`, queried; the round's mutation spec and log (`mut_r13_spec.py`, `mut_r13.log`: 13 of 13 red where the dispositions say), its red and green logs, and - after it finished - the coordinator's battery log `battery-r13.log` (317 classes green, none failing).

**The code at 98407a74, around each change:** `AutonomyEditorPanel`'s four factories and every call of them, the menu items built outside them, `refusedWhileRunning`, `placeLocomotive`, `addLocomotiveSettings`, `armTailPick`, `tileClicked`, `cycle`, `annotationsChanged`, `setupChanged`, `placementChanged`, `rebuildRunningLayoutSoon`; `TrainControlUI.buildAutonomyTileMenu` / `buildAutonomyFacingMenu`, `isAutonomyBusy`, `refuseWhileEditorOpen` and every door that asks it, `refuseWhileAutonomyRunning` and its doors, `openLayoutEditor`, `whyAutonomyEditorCannotOpen`, `autonomyEditorClosed`, `rebuildRunningLayoutFromSetup` (both forms, the flag and its three lowerings), the exit's branch in `saveState`, `locomotiveGestureOnDiagram`, `whereTheTrainsAre`, `putTheTrainsBack`, `writeTheTurns` / `writeTheTurnsOwed` / `reconcileFacingWhenIdle`, `followDirectionChanges`, `repaintAutoLocListFull`, `startAutonomyActionPerformed`, `requestReturnToHome`, `executeTimetableActionPerformed`, the Auto tab's settings handlers; `AutoLocomotiveStatus.locAvailPathsMouseClicked`; `LayoutRightclickAutonomyMenu` whole (`showFor`, `gatherPathOptions`, the constructor, `placeSomewhereLegal`, `removeLocomotiveHere`, `addSetupMenu`); `GraphLocAssign.commitAndRecord` / `commitChanges`; `TailCrossedPrompt.askAfterPlacement`, `DiagramPick`, `whereTheAnswerGoes`, `runningNow`; `LayoutEditor.saveButtonActionPerformed`, `arriveAt`; `AutonomyMenu` whole; `AutonomyViewerPanel.duplicate` / `rename` / `delete` and the import's question; `Layout.getLastPointsReached`, `keptApart`, `stationsAheadItHolds`, `lastKnownPoint`, `toJSON(Map)`, `isAlreadyUnderway`, `isRunning`, `executePath`'s count, `moveLocomotive`, `clearBlockExcept`, `locomotiveInBlock`, `applyDefaultLocCallbacks`, the arrival's turn and the last retire check before it; `Point.setLocomotive` / `reserve` / `toJSON` / `isSamePlaceAs`; `AutonomySession.faceTheWayItCameIn`, `flipFacing`, `moveOntoFacingCopy`, `capture` (both modes), `importLegacy`'s gap-fill; `MarklinControlStation.getAutoLayout` / `getAutoLayoutIfLoaded` / `hasAutoLayout` and every other `hasAutoLayout` caller in `src`; `LayoutLabel.aboutToClearProtection`; `reap.ps1`.

**Runs.**  Every JVM through the archive's own `docs/tools/one.sh`, `TC_SCRATCH` a Windows-form folder per job under `rlv12/scratch`, from a queue (`rlv12/tools/queue.sh`) that started each job only when no `java.exe`, `javaw.exe` or `javac.exe` had been seen for two checks 20 s apart and no battery lock file existed, and would have retried a job the runner refused, deciding from that attempt's own log (none was refused).  The coordinator's battery held the machine until 22:30; everything below ran 22:30-22:48, one job after another.

- **Baseline** (`src98`): `core.testATrainIsDispatchedOnce` 11/11, `regression.testNothingOnTheEventThreadTakesTheRailwaysMonitor` 5/5, `regression.testTheRecordsCountTheStore` 3/3, `regression.testTheImportDoorReadsAnOldFile` 39/39, `core.testAutonomyDiagramSession` 156/156; no skips, no class HUNG.
- **Probes** (scratch test code in `p12` only, each writing what it saw to `rlv12/out`): `rlv12ProbeAChainOfThree` and `rlv12ProbeASplitSquare` on `testATrainIsDispatchedOnce`'s fixture (RLV12-C1, C2); on the live-snapshot sandbox with the window, `rlv12ProbeTheAutoTabSendWithTheEditorOpenWatched` (B1; its first version, `...EditorStaysOpen...`, clicked a list the hidden tab had never laid out and dispatched nothing, and is not evidence), `rlv12ProbeTheDiagramsEditLocomotiveItemAfterARunBegan` (C3), `rlv12ProbeTheDirectionFollowReachesATrainUnderWay` (C4).
- **Mutations** (each a fresh archive with only its change, the claims run as methods): `mR` - the refusal taken out of `item`, `radio` and `directionItem` and left in `toggle`; `mO` - `keptApart`'s preference for the train its point still records taken out.  Results in RLV12-C7.  CR1, CRA, CC1-CC5b, CM1-CM5 and BL1b were not repeated; `mut_r13.log` has each red where the dispositions say.
- **The runner:** `one.sh` given one real method and one misspelled one (RLV12-C8).

**Nothing written** outside `validate30/rlv12/` and this file.  The runner itself takes the shared lock under `%TEMP%` and a preference node of its own, and removes both; every `state-one-*` folder of my runs is gone.  Two nodes under `HKCU\Software\JavaSoft\Prefs\traincontrol-test-runs` exist after my runs (`battery-680`, `one-620`); the trap that removed my runs' state folders deletes the run's node in its next line, so I believe neither is mine, and I did not touch them.  No git state changed; no commit.  No tree has anything in `cs2_sample_layout`.  `LocDB.data` and `UIState.data` in each tree are copies from TC30.  The real `LocDB.data` and `UIState.data` - the main checkout's and `C:/Users/adamo`'s - were copied and hashed before the first JVM and compared byte for byte after the last: unchanged (`3f54de3a...`, `3604bf5f...`, `6ef56939...`, `9e32894e...`).  Nothing of mine is running: the queues ended, the monitor expired, and no `java.exe`, `javaw.exe` or `javac.exe` is left.

**Counts:** 0 A, 1 B, 9 C, 9 D.

**The ruling holds at the diagram, and not at the editor** (RLV12-D1, RLV12-B1).  The four factories refuse and say so, and nothing that is not a setup edit is newly refused.  But the autonomy editor, opened at rest, stays open when the Auto tab's double-click sends a train - the one dispatch door that never asks `refuseWhileEditorOpen` - and a square clicked in it then edits the setup under the run and raises the waiting-edit flag, which the dispositions say only the one-event race can do; the diagram's own Edit Locomotive item, which no factory builds, writes the setup too (RLV12-C3).  **Round 13's other fixes are right at the arrangements they were written for** (RLV12-D2 to D5) and reach no further: `keptApart` pushes one step and counts Points, not squares (RLV12-C1, C2); RLV11-C3's rule sits in one of the two callers of the primitive that sweeps (RLV12-C4); and three of the model's check-then-build pairs remain beside the ones fixed (RLV12-C5).  The new question promises something the gap-fill does not do for a station's maximum (RLV12-C6), the claims reach one factory in four (RLV12-C7), the runner reads a misspelled method as absent rather than missing (RLV12-C8), and the store still calls RLA3-B1 undecided (RLV12-C9).  **New in round 13:** C3 (the refusal's missed sibling), C6, C7, C8, C9.  **Older:** B1 (exposed by the ruling), C1 (not reached), C2, C4, C5.

---

### RLV12-B1 - An editor opened at rest stays open for a train sent from the Auto tab, and edits the setup through the run: a square clicked in it writes the setup and raises the waiting-edit flag, so the ruling is not true at the editor and RLV11-B2's exit is back

| | |
|---|---|
| **Disposition** | Fixed through one gate (Adam, 2026-09-29: *"can all the checks go through a single door?"*): every door that sends a train - Start, Execute Timetable, Return Home, and TrainControlUI.sendATrainByHand, the one door both hand doors now send through - asks refusedToSendATrain (the editor, the setup, the power) before anything else, so no train is sent while the editor is open and OV2-C2's premise holds; the diagram's destinations, which asked nothing about an editor at the click, now ask too.  Claims cd19e9ca (red first: the Auto tab sent the train) and the diagram-destination claim in fa641a6e (red first on the code before the gate); mutations MB1 red, MG red.  The window claims moved to regression.testNoSetupEditDuringARun: with them the import door's class ran out of its heap. |
| **Grade** | B.  The ruling - *"There should be no setup edit possible during a run"* - and the dispositions of RLV11-B1 and B2 rest on two premises: the editor cannot be opened during a run (OB-047), and *"`refuseWhileEditorOpen()` guards every door that starts one"* (`TrainControlUI.java:6879-6890`, OV2-C2).  The second is false for the Auto tab's double-click, which asks power, setup errors, length, berth, tail, reversal and the atomic-routes gate, and never whether an editor is open.  The editor's square click goes through `cycle` - not one of the four factories - to `rebuildRunningLayoutFromSetup(true, ...)`, which, declined, raises `setupEditDeclinedDuringRun`.  With the flag up, every door RLV11 graded behaves as it did: the exit keeps nothing (RLV11-B2's measured outcome: after the next start the trains the run moved stand where the file had them, and the squares they really stand on read free), a switch of source the same, Unload the placement-only write, a load the carry.  The log says *"autonomy started between the edit and the moment it was to be applied"*, which is not what happened.  **Older** (the Auto tab's door never asked; OV2-C2's premise has been false since the door existed), **exposed by round 13**, whose ruling, behaviour.md sentence and dispositions depend on it. |
| **Names** | RLV11-B1, RLV11-B2, OB-047, MT-135, OV2-C2, ACC-B3, VD11-C8, GS-B1, TDU-B1 |
| **Where** | `AutoLocomotiveStatus.java:1057-1196` (the double-click: no editor question); the other dispatch doors that ask - `TrainControlUI.java:27408` (Start), `:27195` (Execute Timetable), `:24422` (Return Home), `LayoutRightclickAutonomyMenu.java:371-378` and `:222` (the diagram's menu and its paths); `AutonomyEditorPanel.java:7249` (`tileClicked`), `:7534` (`cycle`), `:9439-9453` (`annotationsChanged`), `:9525-9549` (the posted rebuild, `sayIfDeclined` true); `TrainControlUI.java:6889-6893` (the flag), `:6879-6890` (the premise), `:2552-2561` (the exit with the flag up); `LayoutEditor.java:7381-7388` (the editor's Save refused while running - so the edit waits in the session); `behaviour.md:2478-2480` (*"an edit waits only where a run begins in the moment between an edit and its rebuild"*) |
| **Needs execution** | done - probe `rlv12ProbeTheAutoTabSendWithTheEditorOpenWatched` |

**Measured** on the live-snapshot sandbox with the window: the autonomy editor opened at rest on `1 - Main:0,2`, a plain track square (busy false); the Auto tab's panel for 2-8-4 3505 SP found through the window, its list given the size it has on screen (the tab was not the one showing), and a double-click dispatched to its first row through the list's own listener - the production handler.

| | busy | editor open | 2-8-4 3505 SP | the square's direction | the waiting-edit flag | said |
|---|---|---|---|---|---|---|
| at rest | false | yes | standing at TopMainR2Inter | BOTH | false | - |
| 500 ms after the double-click | **true** | **yes** | under way (TopMainR2), signals being set | BOTH | false | nothing asked |
| after a click on `0,2` in the editor | true | yes | under way | **TOWARD_A** | **true** | the log line above; no refusal |

**Direction:** `refuseWhileEditorOpen()` in the Auto tab's double-click, beside the power question, as Start, Execute Timetable and Return Home ask it - the door the premise names; or refuse in the editor's own gestures while busy, as its Save already does.  Correct OV2-C2's comment and behaviour.md's sentence with it.  Claim: the probe, asserting no dispatch (or no edit) and the flag down.

---

### RLV12-C1 - The train moved on is moved one step only: a chain of three trains under way leaves the first where a standing train stands, and the fold erases it in every order

| | |
|---|---|
| **Disposition** | Fixed - keptApart moves trains on along the chain (Layout.movedOn): a train takes a station ahead that another is kept at where that one can be moved on in its turn - to a free station, or along the chain - and nothing changes unless the whole chain moves; each step is to a station ahead the train holds, so the walk ends.  The chain of three: A@P2, B@P3, C@P4 kept, read and written in all 120 orders.  claims cd19e9ca (red first), fix fa641a6e; mutation MC1 red. |
| **Grade** | C.  RLV9-C4's erasure through `keptApart`'s single push: a train out on the track is modelled nowhere after the reload or Unload, and the fold writes it out of the configuration.  RLV11-C1's arrangement with one more train under way - atomic routes off, four trains, none past a station sensor.  A train bound for another's kept station takes it only where that other can go on to a free station of its own; where the other's next station is a third train's kept point, nothing moves and the first stays on the standing train's point.  **Not reached by round 13** (`fd31f56f`); `75c601b9` gives the same outcome here by reading, since its rule passed over another's kept point outright. |
| **Names** | RLV11-C1, RLV10-C2, RLV9-C4 |
| **Where** | `Layout.java:1700-1717` (the push: `stationsAheadItHolds(other, ahead)` must yield a station nobody is kept at; no second push); `:1719-1722` (nothing left: the train stays at its point); `:12217-12227` (`toJSON(Map)` gives the point to the standing train); `TrainControlUI.java:6587-6594` (the carry skips a kept train where another stands) |
| **Needs execution** | done - probe `rlv12ProbeAChainOfThree` |

**Measured** on `testATrainIsDispatchedOnce`'s fixture: stations P1-P4 with sensors, points J1-J3 without; Z stands on P1; A's path P1-J1-P2 (A holds J1, P2), B's P2-J2-P3 (B holds J2, P3), C's P3-J3-P4 (C holds J3, P4); each train's milestones its start.  In 840 orders of 840 (four locomotives of seven): kept A@P1, B@P2, C@P3; the carry reads B, C and Z and not A; the fold writes B@P2, C@P3, Z@P1 and A nowhere.  The answer `keptApart`'s javadoc describes is A@P2, B@P3, C@P4.  **Direction:** push along the chain until a free station is found - each step moves a train to a station ahead that it holds, so the walk ends - or say in the javadoc that one step is the limit.  Claim: the probe.

---

### RLV12-C2 - `keptApart` keeps trains apart by Point, not by square: on a split square a train under way is kept on one copy while another stands on the other, the fold writes one of them, and the carry takes the standing train off the railway

| | |
|---|---|
| **Disposition** | Fixed - keptApart keeps trains apart by square: the block a split square's copies share is one place (Layout.placeOf), a standing train's square is its own, and no two trains under way are kept on one square; the carry and the fold agree and lose nobody, in all 42 orders of each arrangement.  claims cd19e9ca (red first), fix fa641a6e; mutation MC2 red. |
| **Grade** | C.  A split square is one piece of track (`Point.isSamePlaceAs`, the block), and the lock refuses a route onto one copy while another holds a train (`locomotiveInBlock`) - so once atomic routes off have released the copy a train set off from, another can stop on the square's other copy while the first is still kept at its own.  `keptApart`'s `standing` and `keptThere` are sets of Points, so both are kept on one square.  The fold's merge keeps the `loc` of whichever copy it reads last (`AutonomySession.java:5486-5489`) and erases the other; the carry's `moveLocomotive` clears the block before placing (`Layout.java:9900`), so putting the kept train back displaces the train standing there - off the railway, with a log line.  **Older** (neither `keptApart` nor the rule before it looked at blocks), exposed by the round's rule, which says *"none on a point a standing train stands on"*. |
| **Names** | RLV9-C4, RLV11-C1, RLV11-C2, MT-165, DAY-A1 |
| **Where** | `Layout.java:1651-1658`, `:1680` (Points, not blocks); `:11481-11495` (`locomotiveInBlock`), `:11610-11640` (`clearBlockExcept`), `:9900` (called by `moveLocomotive`); `AutonomySession.java:5486-5489` (the per-square merge); `TrainControlUI.java:6710-6740` (`putTheTrainsBack` moves each train in turn) |
| **Needs execution** | done - probe `rlv12ProbeASplitSquare` |

**Measured** on the fixture, SE and SW two copies of one square (one block), A's path SE-J-T (A holds J, T), A's milestones [SE], SE released; Z standing on SW.  In 42 orders of 42: A kept at SE; the carry reads A@SE and Z@SW; `toJSON` writes A on SE and Z on SW - two trains on one square; the carry onto a railway rebuilt with the same squares leaves **A@SE and Z nowhere**.  Two trains under way kept at the two copies with nobody standing (RLV11-C2 across copies): both kept, both written, and the carry leaves one of them off the railway (21 orders one, 21 the other).  **Direction:** ask the block wherever `keptApart` asks the Point - a standing train's square is its own, and no two kept trains on one square.  Claim: the probe.

---

### RLV12-C3 - The diagram's own Edit Locomotive item, which no factory builds, writes the setup and saves it when clicked after a run began - and a tail question asked at rest is answered and written during a run

| | |
|---|---|
| **Disposition** | Fixed - the diagram's Edit Locomotive refuses, and says so, when clicked after a run began, and again at OK (LayoutRightclickAutonomyMenu.refusedWhileRunning); and a tail question answered once trains run writes nothing: TailCrossedPrompt.whereTheAnswerGoes, which every door asks where to write, answers nowhere while the railway runs, and the log says why (autolayout.ui.logTailAnswerDroppedRunning, eight languages).  The placement made at rest before the question is still saved.  claims cd19e9ca (red first), fix fa641a6e; mutations MC3a red, MC3b red, MC3c red. |
| **Grade** | C.  The ruling's refusal was put in the four factories; the diagram's right-click menu builds its own items, offered only at rest and not asked again at the click.  Place and Remove refuse through `moveLocomotive` (a log line, W21-B3's design); Is Facing goes through `radio` and refuses.  **Edit Locomotive does not**: the dialog opens, OK runs `GraphLocAssign.commitAndRecord`, whose move is refused (the log line) while everything after it runs - the placement, facing and tail written into the setup, the tail onto the running Point, the train's reversibility, functions, speed and length set, and the setup saved.  The same race the claim builds, at the door beside it.  By reading, a second way in with no race: `TailCrossedPrompt.DiagramPick` asks where a placed train's tail lies on the diagram and *"keeps the window live to be clicked"* for up to 30 minutes; Start or a hand send in that wait is not refused, and the answer then writes the road into the setup and saves - for the diagram's Place and Edit Locomotive, whose train may by then be the one under way.  **New in round 13** (the ruling's refusal missed its sibling); the doors are older. |
| **Names** | RLV11-B1, W21-B3, REG8-B2, TDU2-A1, TDU3-B1, FR-100 |
| **Where** | `LayoutRightclickAutonomyMenu.java:788-826` (Edit Locomotive, no busy question at the click); `GraphLocAssign.java:221-370`, `:842-856` (`commitChanges`: the move refused, the settings set); `TailCrossedPrompt.java:209-226`, `:756-900` (the pick, non-modal); `:598-606` (`whereTheAnswerGoes`: the train still recorded on its start while it runs) |
| **Needs execution** | done - probe `rlv12ProbeTheDiagramsEditLocomotiveItemAfterARunBegan`; the tail question by reading |

**Measured** on the live-snapshot: the diagram's menu built at rest on TopMainR0Park (`1 - Main:4,5`, EN57-203 standing) - Start, Return Home, the heading, Place BR 628 2, Remove EN57-203, Edit Locomotive..., EN57-203 Is Facing..., Autonomy Setup; the square's `facing` taken out of the configuration; EN57-947 sent off (busy); Edit Locomotive clicked and its dialog answered OK.  Asked: the dialog only - no refusal.  Logged: *"Cannot edit auto layout while running."* (the move's).  The square's `facing` was **written back into the configuration during the run**, and the setup saved.  **Direction:** ask `isAutonomyBusy()` at the click, as the factories do, and refuse with the same sentence; and put the tail question down, or refuse its write, where a run began in the wait.

---

### RLV12-C4 - RLV11-C3's rule sits in one of the two callers of the primitive that sweeps: `flipFacing`, the direction follow's posted body, still stands a train under way on another copy of a square on its path

| | |
|---|---|
| **Disposition** | Fixed - the rule is in the primitive: moveOntoFacingCopy declines a train under way (activeLocomotives or takingPath), so the turn written after an arrival, the direction follow and the facing menu all have it; and the direction follow's posted work (TrainControlUI.followTheTurn) asks isAutonomyBusy again, so a change followed after a run began writes nothing.  claims cd19e9ca (red first), fix fa641a6e (the posted-work claim red first only because followTheTurn did not exist yet); mutations MC4a red, MC4b red. |
| **Grade** | C.  `moveOntoFacingCopy` stands a train on another copy through `Point.setLocomotive`, which sweeps it off every other point - the whole of a locked path.  Round 13 put *"not under way again"* into `faceTheWayItCameIn`; the other caller, `flipFacing`, takes its candidates from every square the running railway records the train on - a train under way is recorded on its whole path - and moves it on the first it can decide.  Its door is the direction follow: the Central Station's thread asks whether the railway is running and posts the flip to the event thread, which runs it later without asking again, so a hand send whose body was queued before the flip (the Auto tab's double-click runs in an `invokeLater`) is under way when the flip lands.  A narrow window - an operator's direction change arriving in the milliseconds around a hand send - and the harm is RLV11-C3's.  Start does not reach it: its worker waits on the event thread for the atomic-routes gate before dispatching.  **Older** (DIR-B4's posted body; the fix for RLV11-C3 did not sweep its sibling). |
| **Names** | RLV11-C3, DIR-B3, DIR-B4, REG6-A2, TDY3-A2, PRV-B2 |
| **Where** | `AutonomySession.java:2067-2114` (`moveOntoFacingCopy`), `:2196-2199` (the rule, in `faceTheWayItCameIn` only), `:2276-2378` (`flipFacing`: candidates from the running railway, `:2375` the move); `TrainControlUI.java:13011-13015` (the check on the Central Station's thread), `:13047-13068` (the posted body, not asking again) |
| **Needs execution** | done - probe `rlv12ProbeTheDirectionFollowReachesATrainUnderWay` (the primitive as the body runs it); the door by reading |

**Measured** on the live-snapshot: EN57-947 on BottomInner (northbound), a square with two facings, sent off along a path and under way holding 5 points; `session.flipFacing("EN57-947", the running railway)` on the event thread wrote the facing of **`1 - Main:7,7`** - a square in the middle of its path - and left it holding **1 point, Tunnel (northbound)**, still under way.  **Direction:** the rule in `moveOntoFacingCopy` - it declines for a train in `activeLocomotives` or `takingPath` - so both callers have it, or the posted body asks `isRunning` again before flipping.

---

### RLV12-C5 - RLV11-C5 reached where it was measured: three of the model's check-then-build pairs remain on the sync's and a route edit's threads, the window's `isAutonomyBusy` is one too, and the train still turns after Unload

| | |
|---|---|
| **Disposition** | Fixed - deleteRoute, changeRouteId, restoreRouteActivation, isAutonomyBusy, gatherPathOptions, aboutToClearProtection and TailCrossedPrompt.runningNow ask getAutoLayoutIfLoaded once, pinned by a source claim (mutations MC5m red, MC5b red, MC5g red, MC5l red); and the arrival's turn asks whether its railway is still the current one after the pause before it, so a train arriving on a railway retired on its way is not turned and owes no turn (mutation MC5t red).  claims cd19e9ca (red first), fix fa641a6e. |
| **Grade** | C.  RLV11-C5's family: `hasAutoLayout()` then `getAutoLayout()` off the event thread is a window of two lock acquisitions in which Unload can clear the model, and the second call then builds an empty railway that `hasAutoLayout` answers yes about.  The disposition says *"the model's pairs (isAutonomyRunning, the sync's sweep, a rename, a route's activation) ... ask once"*; `isRouteActivatedByAutonomy` was changed and its sibling `restoreRouteActivation`, called three lines later in the same sync and route edit, was not, nor `deleteRoute` or `changeRouteId`.  Off the event thread in the window: `TrainControlUI.isAutonomyBusy` (a pair itself) and the pairs after it in `LayoutRightclickAutonomyMenu.gatherPathOptions`, which runs on a worker by design; `LayoutLabel.aboutToClearProtection` on the switching thread.  And RLV11-C5's direction asked for *"a retire check before the arrival's turn and callbacks"*: not done and not declined - the train's thread still reverses the locomotive after Unload (`switchDirection`, recorded on the retired railway).  **Older**; the disposition overstates what was swept. |
| **Names** | RLV11-C5, RLV8-C2, RLV9-C2, CS3-C4, AC2-A1 |
| **Where** | `MarklinControlStation.java:3801-3806` (`deleteRoute`), `:3859-3866` (`changeRouteId`), `:3905-3910` (`restoreRouteActivation`); callers `:1556-1582` (`syncWithCS2`), `:2136-2169` (`editRoute`, reached from the window's worker, `TrainControlUI.java:21925-21950`); `TrainControlUI.java:24680-24688`; `LayoutRightclickAutonomyMenu.java:224-240`; `LayoutLabel.java:2009`, `:2033`, called at `:680-681`; `Layout.java:9261` (the last retire check), `:9398-9404` (the turn after it) |
| **Needs execution** | no - by reading, as RLV11-C5's own "by reading" list was |

**Direction:** `getAutoLayoutIfLoaded()` once in each (all three model methods treat no railway as nothing to do), and a retire check before the arrival's turn - or say in the disposition which of these were left and why.

---

### RLV12-C6 - The new question says a setting at its default takes the file's value; a station's maximum at its default does not, in any configuration that has run - and into the configuration in use, never

| | |
|---|---|
| **Disposition** | Fixed - both old-file questions add a sentence, autosetup.ui.importKeepsTheStationMaximums in eight languages: once the configuration has been loaded, each of its stations keeps its own maximum train length, even where that is no limit - the exception to the default rule, within RLA3-B1's option (a); behaviour.md's stale "is put to Adam" corrected.  claims cd19e9ca (red first), fix fa641a6e; mutation MC6 red. |
| **Grade** | C.  RLA3-B1's option (a), as built: *"A setting at its default, a home taken off or an emptied exclusion list counts as not set, and takes the file's value."*  True of every per-square setting but one: `Point.toJSON` writes `maxTrainLength` for every station, 0 included, so every fold (exit, Unload, load, editor open) leaves `maxTrainLength: 0` on each station with no limit, and the gap-fill reads that as set and keeps the file's maximum out - RLU2-C11's measured fact, dispositioned *"not a defect"* before any question claimed otherwise.  Into the configuration in use the import folds first, so no maximum from the file arrives at all (RLU4-D3, and open-questions.md's own text of RLA3-B1: *"a captured 0 keeps the file's maximum out"*).  The in-use question is the one that now says the opposite most plainly.  **New in round 13** (`fd31f56f`). |
| **Names** | RLA3-B1, RLU2-C11, RLU4-D3, RLA2-C4, MT-298 |
| **Where** | `messages*.properties` `autosetup.ui.importTakesTheFilesForDefaults`; `AutonomyViewerPanel.java:1227-1231`; `Point.java:1214-1217`; `AutonomySession.java:1187-1191` (the gap-fill); `open-questions.md:238-247` |
| **Needs execution** | no - by reading, on RLU2-C11's measurement |

**Direction:** name the exception in the sentence (a station's maximum left at *no limit* in a configuration that has run is kept), or have the fold stop writing a 0 maximum, which is the larger change RLA3-B1 weighed; Adam's call, since he agreed the wording of (a).

---

### RLV12-C7 - What round 13's claims do not reach: three of the four refusals, the rule that the train a point still records keeps it, and a train still locking its path from where it turned

| | |
|---|---|
| **Disposition** | Fixed - claims where the round's did not reach: every kind of setup item (a plain item, a check box, a choice of one, a direction) clicked during a run (mutations MRi red, MRr red, MRd red, each factory's refusal alone); the train its station still records keeps it (mutation MO red); and a turn owed to a train still locking its path writes nothing, the setup's record included (mutation MC7c red).  cd19e9ca, with the setup assertion added in fa641a6e. |
| **Grade** | C.  Claims that pass with their own round's rule undone.  **New in round 13** (`ae0285f3`). |
| **Names** | RLV11-B1, RLV11-B2, RLV11-C1, RLV11-C2, RLV11-C3; CR1, CC1, CC2, CC3 |
| **Where** | `AutonomyEditorPanel.java:2652`, `:4182`, `:9169` (the refusal in `item`, `radio`, `directionItem`); `Layout.java:1664-1667` (the own-point preference); `AutonomySession.java:2199`; `testTheImportDoorReadsAnOldFile.testASetupMenuOpenedAtRestRefusesOnceTrainsRun`, `testWritingTheTurnsLeavesATrainUnderWayOnItsPath`; `testATrainIsDispatchedOnce`'s two claims |
| **Needs execution** | done - mutations `mR`, `mO`; the third by reading |

| mutation | claim run | result |
|---|---|---|
| `mR` - the refusal taken out of `item`, `radio` and `directionItem`, left in `toggle` | `testASetupMenuOpenedAtRestRefusesOnceTrainsRun` | **green** (it clicks Can Be Chosen, a toggle) |
| `mO` - no preference for the train its point still records; by name only | `testATrainBoundForAnothersKeptStationTakesItAndTheOtherGoesOn`, `testTwoTrainsLastSeenAtOneStationAreBothKept` | **green** (neither fixture has a point recording its kept train) |

CR1 removed the refusal inside `refusedWhileRunning`, so it could not tell the four sites apart.  The C3 claim waits for the train to reach `activeLocomotives`; a train still in `takingPath` - locking its path from the square it turned on - is covered by `isAlreadyUnderway` and by no claim.  **Direction:** click one item of each factory in the refusal claim; a fixture where a train's kept point still records it (it tripped that station's sensor and is passing through) beside one that set off from there; the C3 claim once more while the path is still locking.

---

### RLV12-C8 - `one.sh` given a method list with a misspelled name runs the others and reports green

| | |
|---|---|
| **Disposition** | Fixed in fa641a6e - one.sh looks each method of a list up in the run's testng-results.xml (removed before the run) and reports a list with a name that did not run as DID NOT RUN EVERY METHOD ASKED FOR, counted as bad.  Measured before (a real and a misspelled name: 1 run, green) and after (the same list fails; a list of two real names passes).  The scratchpad's mutation runner flags such a method NOT RUN. |
| **Grade** | C.  The runner's new method form passes the list to TestNG's `-methods`, which drops a name that matches no method without a word; the summary counts only what ran, and the runner calls it clean and exits 0.  Only a list in which every name is wrong reads *RAN NOTHING*.  So a baseline or re-run of named claims can report a claim green that never ran, and a mutation targeted by method can read as survived - the scratchpad's `mutate_wt.py` reads the classes' reports and finds no red (it does not check that each named method has a result either).  This is the false green the brief asks about; the hang watch has none (RLV12-D6).  **New in round 13** (`8a9575f2`). |
| **Names** | 8a9575f2, V33-B1, V34-C3 |
| **Where** | `docs/tools/one.sh:471-474` (`TARGS="-methods ..."`), `:558`, `:594-610` (the verdict from the summary) |
| **Needs execution** | done - `one.sh "t:core.testATrainIsDispatchedOnce.testTwoTrainsLastSeenAtOneStationAreBothKept,core.testATrainIsDispatchedOnce.noSuchMethodAtAll"`: *Total tests run: 1, Failures: 0, Skips: 0*, exit 0 |

**Direction:** count the names asked for and compare with `Total tests run` (a method run has one test per name, barring data providers), or read `testng-results.xml` for each name and fail the run on a name with no result.

---

### RLV12-C9 - RLA3-B1 and the three findings it superseded still read "Open - Adam's decision" in the finding store after the decision and the fix

| | |
|---|---|
| **Disposition** | Fixed in the records - RLA3-B1, RLU3-C4, RLA2-C4 and RLU2-C11 are Closed in the store with Adam's decision of 2026-09-28 and fd31f56f, and findings.tsv re-rendered, in this round's store commit. |
| **Grade** | C.  open-questions.md moved RLA3-B1 to **Decided** (option (a), built in `fd31f56f`, CRA red) and the commit says so, but the store - which the paragraph closing open-questions.md says holds *"everything still open above"* so that it *"can be queried rather than re-read"* - was not updated: RLA3-B1, RLU3-C4, RLA2-C4 and RLU2-C11 are `Open - Adam's decision`, with status notes from 2026-09-25.  A query for what waits on Adam returns a question he has answered.  The drift README.md describes, in the direction it says audits find it.  **New in round 13** (`23f4826d`, `98407a74`). |
| **Names** | RLA3-B1, RLU3-C4, RLA2-C4, RLU2-C11, RLA4-C8 |
| **Where** | `docs/manual-tests/triage.db` (`finding` rows above); `open-questions.md:238`, `:431-432` |
| **Needs execution** | done - queried a copy of the store |

**Direction:** set the four rows Closed with the decision and `fd31f56f` (RLA4-C8 already is), and re-render findings.tsv.

---

### RLV12-D1 - The ruling at the diagram: the four factories refuse and say so, and nothing that is not a setup edit is newly refused

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RLV12-B1, C3 and C7. |
| **Grade** | D |
| **Names** | RLV11-B1, RLV11-B2, OB-047, MT-141 |
| **Where** | `AutonomyEditorPanel.java:2634-2643`, `:2652`, `:2768`, `:4182`, `:9169`; `LayoutRightclickAutonomyMenu.java:397`, `:928`; `TrainControlUI.java:7311-7316`, `:23029`, `:25889`, `:26214`, `:26307`, `:5305-5309`; `AutonomyMenu.java:738`; `AutonomyViewerPanel.java:1211` |
| **Needs execution** | done - the claim green in the baseline; CR1 red in the round's log |

Each factory asks before its action, and the answer is shown with `autolayout.errorCannotEditWhileRunning` in all eight languages - *"Cannot edit auto layout while running."*, the pre-3.0 name for the setup, the sentence the editor's own refusal has shown since OB-047.  A refused `toggle` or `radio` has already changed its box, but the menu closes and is rebuilt at the next opening.  The only factory item on the diagram's menu that is not an edit is Go To Link Partner, which opens the editor and is refused during a run anyway; reading, testing a path and sending a train are not factory items.  The other doors were already shut: the diagram's keys (log line), page rename, delete and combine, the Pages submenu, an import into the configuration in use, the editor's opening and its Save; the Auto tab's settings write only the running railway.  Duplicate and Rename are not refused during a run and write the store, but no setting the railway running reads (RLV11-D2 followed duplicate's switch).

### RLV12-D2 - `keptApart` at RLV11-C1's and C2's arrangements, and every reader of the rule

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RLV12-C1, C2 and C7. |
| **Grade** | D |
| **Names** | RLV11-C1, RLV11-C2, RLV10-C2, RLV9-C4, RLV8-C4 |
| **Where** | `Layout.java:1607-1766`; readers `TrainControlUI.java:3111`, `:3218`, `:6561`, `AutonomyViewerPanel.java:906` |
| **Needs execution** | done - both claims green (42 orders each); CC1 and CC2 red in the round's log |

The carry, the placement-only write and both folds read one method, and the carry and the fold agreed in every order of the probes, wrong answers included.  By reading: a train still locking its path is read at its start, which records it, and `stationsAheadItHolds` reads `takingPath` for it; a point without a sensor is never a kept point (a station needs one); a barred copy of a station counts ahead, as before.  A comparator read without the monitor can answer inconsistently while a point changes, but fewer than 32 trains are sorted by insertion, which does not throw.

### RLV12-D3 - RLV11-C3 at `faceTheWayItCameIn`, and every caller of it

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RLV12-C4 and C7. |
| **Grade** | D |
| **Names** | RLV11-C3, REV9-A1, RGD-C7 |
| **Where** | `AutonomySession.java:2182-2228`; `TrainControlUI.java:7761-7875` |
| **Needs execution** | done - the claim green; CC3 red in the round's log |

`isAlreadyUnderway` covers `activeLocomotives` and `takingPath`; the one caller, `writeTheTurns`, passes the railway, so the check is never skipped for want of one.  The idle drain runs only when `isRunning` is false, which a hand send's count keeps true from the start, so only Unload's and another configuration's writes meet a train under way; the record is kept on the railway, dropped with it, and the kept copy's facing is written by the fold as before (RLV11-C3's measurement).

### RLV12-D4 - RLV11-C5's changed sites

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RLV12-C5. |
| **Grade** | D |
| **Names** | RLV11-C5, RLV11-C6, BL1 |
| **Where** | `Layout.java:12113-12150`; `MarklinControlStation.java:1756-1761`, `:3388-3392`, `:3650-3653`, `:3887-3890`; `MarklinRoute.java:589-595`, `:644`, `:1025-1035`; `Route.java:345-350`; `TrainControlUI.java:13011-13015`, `:13056-13058`, `:29413-29424` |
| **Needs execution** | done - the claims green; CC5a, CC5b and BL1b red in the round's log |

Each asks once and treats no railway as nothing to do, as before it treated `hasAutoLayout()` false; `heldReason` uses the same railway for both halves; `flipFacing(name, null)` writes the setup alone (`AutonomySession.java:2296` skips the railway); the full status list returns an empty panel.  `testAutonomyDiagramSession`'s source check follows the new call.

### RLV12-D5 - The gap-fill's new sentence in eight languages

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RLV12-C6. |
| **Grade** | D |
| **Names** | RLA3-B1 |
| **Where** | `messages*.properties`; `AutonomyViewerPanel.java:1224-1236` |
| **Needs execution** | done - the two import claims green; CRA red in the round's log |

One key per bundle, ASCII with `\uXXXX` escapes, typographic apostrophes (`\u2019`) in English, French and Italian and no ASCII apostrophe (it is read with `I18n.t`, so none would matter); appended to both old-file questions and not to a bundle's, and read as a whole it follows the question it closes.  Not graded: the join puts two spaces before it in every language, where the translated questions separate sentences with one; and the translations name a home with a word their files already use elsewhere (de *Heimatbahnhof*, fr *gare d'attache*, nl *thuisstation*) beside the menus' *Heimat-Lokomotive*, *locomotive de garage*, *Thuisbasis-locomotief*.

### RLV12-D6 - The runners' hang watch

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RLV12-C8. |
| **Grade** | D |
| **Names** | 8a9575f2, V33-C1, V33-C2 |
| **Where** | `docs/tools/one.sh:476-530`; `docs/tools/battery.sh:654-716`; `docs/tools/reap.ps1` |
| **Needs execution** | done - ten `one.sh` runs of mine, none HUNG; the rest by reading |

A JVM is ended only as this run's: `reap.ps1` matches `traincontrol.batteryRun=<run id>` whole, and `kill -9` is the job's own `$!`, not yet waited for, so not reused.  A HUNG class is counted as a failure in both runners (`one.sh` also reports a missing summary; `battery.sh` skips the rest of the class's reading), so a hang cannot read green.  No test prints *Total tests run* early (one comment in `support.CS3TestServer` mentions it).  No JVM of mine was left running.

### RLV12-D7 - `8ca5eb12`: the monitor guard's allowance for Unload's placement-only write

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLV11-C4, RLV9-D9, OB-192 |
| **Where** | `testNothingOnTheEventThreadTakesTheRailwaysMonitor.java:279-283` (the allowance added at `:279`) |
| **Needs execution** | done - 5/5 in the baseline |

The allowance names the thread and the reason RLV11-C4 gave, as `captureRunningLayout`'s does.

### RLV12-D8 - Baseline

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | ae0285f3, fd31f56f |
| **Where** | the method |
| **Needs execution** | done |

Five classes, 214 tests, no failures, no skips, none HUNG, on the archive; the coordinator's battery on the same work reports 317 classes green.

### RLV12-D9 - The records

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RLV12-B1 (behaviour.md's sentence and B1's and B2's dispositions rest on its premise) and C9. |
| **Grade** | D |
| **Names** | RLV11 |
| **Where** | `docs/reviews-2026-09-25/RLV11.md`; `findings.tsv` (17 RLV11 rows); `behaviour.md:2478-2480`, `:2491-2494`, `:2537`; `open-questions.md:238`, `:429-432` |
| **Needs execution** | no |

The dispositions describe what `fd31f56f` does and say which parts are by reading (C5's).  The store's 17 rows (2 B, 6 C, 9 D, all Closed) and both counts (4,813 rows, 4,456 findings) agree with the documents and with a query of the store.
