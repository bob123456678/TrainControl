# RSA7 - Release validation, round 22: the track editor's Cancel after a deletion erases what the Cancel has just put back, and the train standing there drops out of the railway; the direction refusal cannot read a train on a copy that records no facing, so one stale record refuses every direction and link on the railway; the Autonomy panel's Pages dialog is not refused during a run and never reloads; while a declined edit waits, the carry reads each train's square as it was before a move or a page rename (TrainControl 3.0.0)

**Status:** open

**Prefix:** RSA7

**Reviewed:** branch `autonomy-diagram-r0` at `10de4f45`, read and run from `git archive 10de4f45` unpacked into `validate30/rsa7/a10d/` (reading, and the baseline), and a copy of it, `validate30/rsa7/p1/`, with two scratch probe classes added (`test/regression/rsa7Probes.java` and `test/regression/rsa7Frozen.java`, both headless; neither in the battery); `cs2_sample_layout` excluded and an empty folder of that name made for the runner; `LocDB.data` and `UIState.data` beside each root are copies from TC30.  TC30's working tree and the main checkout's were never read, apart from copying the two data files the brief names and hashing the real ones; git was used only as `git -C tc-30 log / show / diff / archive`.  Round 22 is `23461fb3..10de4f45`: two javadocs moved back (`15d71e80`), the fixes for RSA6 (`3790dbdb`), their records (`02cbb783`), the tracker and store (`10de4f45`).  2026-09-30.

## Method

**Round 22, read whole:** the brief; `validate30/RSA6.md` and its dispositions at the commit (`docs/reviews-2026-09-25/RSA6.md`); the round's source diff (`AutonomyBuilder`, `AutonomySession`, `AutonomyEditorPanel`, `TrainControlUI`, the eight bundles), its docs diff (`behaviour.md` 6a and 8, `open-questions.md`), its test diff (the seven new claims in `core.testAutonomyDiagramSession` and `regression.testAPendingTurnSurvivesTheRebuild.testTheSessionSeesTheRailwayFromTheStart`) and MT-599, and each changed method in its context.  **For A1's fix:** every construction of a session (one, `getAutonomySession`) and every setter of the running-layout source (five, all the same supplier), and every reader of it (`trainsTheSetupWouldTurn`, `setFacingAndMove`, `homeFacingOf`).  **For A2's and B2's fixes:** the whole of `capture` - the merge, the facing, the prune, the timetable - `squareOf`, `squaresOfTheDiagram`, `moveTiles`, `open`; every caller of `moveTiles` (the track editor's six doors) and every way out of the track editor (`confirmExit`, `leaveFor`, `saveBeforeLeaving`, `completeExitDiscard`, `maySettleBeforeExit`, the Save), with `undoAutonomyEdits`, `discardAutonomyWork`, `restoreSetup`, `layoutEditingComplete`, `layoutRefreshCompleteInternal`, `resetAutonomySession`, `captureRunningLayout`, `AutonomyViewerPanel.load` and `loadPrepared`, `carryTheTrainsAcross`, `whereTheTrainsAre`, both `putTheTrainsBack`, `pointNamedNow`.  **For A3's and B3's fixes:** `trainsTheSetupWouldTurn` whole against `rebuild`, `onlyWayOutFacing`, `sidesBehindTheWaysOut`, `facingOf`, `nodesFor`, `nodeName`, `placementCopy`, `startableCopy`, `facingByName`, the `COPY_FACING` writer, `setPortalDisabled`, `pairPortals`, `unpairPortal`, `TileGraph.validatePortals`, the link toggle and `refusedUnderATrain`, `placeLocomotive`, `getFacing`, `facingChoices`.  **For B1's fix:** `pagesTheRunningLayoutLeftOut`, `withTheEntriesOfPagesOut`, `namesAPointOnAPageOut`, `squareOnAPageOut`, and every door that ticks a page - `AutonomyMenu.pagesMenu`, `AutonomyViewerPanel.choosePages`, `AutonomyEditorPanel.setPageExcluded`, the combine door (`TrainControlUI` combine, which excludes a page it has just made), `excludeRepeatedSensorPages` (the legacy import) - and whether excluded pages are per configuration (they are not).  **For C1's fix:** `uniqueNames`, `namesOutOfPlay`, the one builder factory.  **Every other gesture that can take a copy away:** barred arrivals (`setBarredArrivals`, `arrivalAllowed`), "may" and "must turn round here" (`setPointFlag`), `setStation`, links paired and unpaired, a page ticked out beside a link (`ERROR_PORTAL_EXCLUDED` is blocking), the editor's Cancel and undo (`restoreSetup`, `restorePage`, `revertUnfinishedEdit`), track edits.  **Every other reader of the railway by name:** the station-index readers (`faceTheWayItCameIn`, `homeFacingOf`, `trainOnTheRailway`, `facingOnTheRailway`, `GraphLocAssign`, `TailCrossedPrompt`, `ManualReversalPrompt`) against the doors that change the naming without rebuilding the railway.  **Not reached:** `HomeStaging` and the Return Home planner past a survey (they read the railway's own Points, not the setup's naming); `CS2File`; export; the store's reconciliation; `AutonomyEditorPanel` beyond its direction, link, page and Facing doors.  The autonomy editor's Cancel restores directions through `restoreSetup` without the refusal - a train placed or turned in the same editor session onto a copy that edit made would go back where the setup had it - read, not measured, and not graded: the train must have been put there inside the session being cancelled.  Every finding was searched for in `docs/manual-tests/findings.tsv` (4,953 finding rows) before it was written; none is catalogued.  The known fault in `regression.testConfirmedGoodState` was not re-run.

**Runs.**  Every JVM through the archive's own `docs/tools/one.sh`, `TC_SCRATCH` a Windows-form folder per job under `rsa7/scratch`, started by two queues (`rsa7/runjob.sh`, `rsa7/queue1.sh`, `rsa7/queue2.sh`) only once no `java.exe`, `javaw.exe` or `javac.exe` and no battery lock had been seen for two checks 20 s apart, each deciding from its own log.  The coordinator's battery held the machine from 11:55 until about 13:01, and another run from 13:01 to 13:09; everything below ran 13:09-13:15, one job at a time.  The first attempts at the two probe classes did not compile (`rsa7Frozen` declared no checked exception on three helpers) and ran nothing; they were corrected and run again.

- **Probes** (p1).  They assert nothing; each writes what it saw, preconditions first, to `p1/rsa7-out/`.  `regression.rsa7Probes` (8 run, 0 failures): P1, the timetable keep through every door that ticks a page, sixteen cases (RSA6's eleven and five pairings of doors it did not try); P2, RSA6's five direction and link cases again; P2f, P2g and P2h, a train on a square of one copy that records no facing; P3, RSA6's move-and-Save cases again; P4, the carry across a load while a declined edit waits, after a move and after a page rename, each with a control; P6, the track editor's Cancel after a deletion, with a control.  `regression.rsa7Frozen` (1 run, 0 failures): P5, a sandbox copy of the frozen railway (`support.Scenario.open("live-snapshot")`) - the refusal's question with nothing changed, which pages the running layout left out, the names out of play, and its one captured timetable entry through the link to `2 - Bottom` switched off and the page ticked out and in, in both orders.
- **Baseline** (a10d): the round's seven claims and round 21's five in `core.testAutonomyDiagramSession`, `regression.testAnEditedPlacementSurvivesTheRebuild`'s `testADirectionUnderATrainTheRunLeftIsRefused` and `testARenameToAnotherStationsNameKeepsBothTrains`, and `regression.testJavadocsAreAttached.testNoNewOrphanedJavadocs` (15 run, 0 failures); `regression.testAPendingTurnSurvivesTheRebuild`'s `testTheSessionSeesTheRailwayFromTheStart` and `testATurnOwedAtARenamedStationFollowsTheRename`, with the window (2 run, 0 failures).

**Nothing written** outside `validate30/rsa7/` and this file.  Each run copied the data files into a folder of its own and a preference node of its own (`one.sh`) and removed both; the working copies' `LocDB.data` and `UIState.data` in `a10d` and `p1` hash the same as the TC30 copies they came from (`7a0737ae...`, `a64608f1...`).  The two nodes under `HKCU\Software\JavaSoft\Prefs\traincontrol-test-runs` (`battery-680`, `one-620`) are the ones RSA and RSA2 found - not mine, not touched.  No git state changed; no commit.  The real `LocDB.data` and `UIState.data` were hashed at 11:56, before the first JVM, and again at 13:15: `C:/Users/adamo`'s and the main checkout's are unchanged byte for byte (`6ef56939...`, `9e32894e...`, `481311d4...`, `b80baf8d...`).  Nothing of mine is running: both queues and every waiter have ended, no `java.exe`, `javaw.exe` or `javac.exe` is left, and the battery lock is gone.  No job hung, ran out of memory or was ended by the runner.

**Counts:** 1 A, 3 B, 0 C, 9 D.

**The review has not converged: one A and three B.**  **Round 22's fixes are right where they reach** (RSA7-D1 to D7, the round's claims green): every session reads the railway from the moment it is made; the fold writes each running Point into its own square with its copy's facing, so a page ticked out after a run keeps where the run left its trains; a link switched off under a train that came in through it is refused, the trial built as a rebuild builds; no fold follows a move; a dead end made one way away from a train facing the buffer is refused; names are settled over every page.  **Adam's question - is the timetable keep reliable now? - yes, at every door that ticks a page** (D4): sixteen cases, the Autonomy menu, the Autonomy panel's Pages dialog and the editor's box in every pairing, across an exit and a restart, a run's train on the page, a shared station name - every entry kept once each; and on the frozen railway his one captured entry survives `2 - Bottom`'s link switched off and the page ticked out and back, in either order.  **What the round leaves, measured:** the track editor's Cancel after a square was deleted has the reset's fold judge "a square whose tile is gone" against the page objects the editor mutated, so it erases the placement, home and settings the Cancel has just restored - the train standing there drops out of the railway and its station reads free (RSA7-A1, older); the refusal cannot tell which way a train on a copy that records no facing faces - it skips the railway's train and reads the setup's record, which there is often the facing the previous train left - so, with round 22's "faces its only way out", one such record refuses every direction and every link switched off anywhere on the railway, the link's sentence saying the train came in through it; and with no record, the rail made two way again is not refused and the train is left facing the buffer with no way out (B1, new in round 22); the Autonomy panel's Pages dialog is not refused during a run and never reloads, so the railway goes on running a page the setup says is out (B2, older); while a declined edit waits, the carry reads each train's square as it was before a track edit's move or a page rename, and puts every train one station back, or where the setup had it before the run (B3, older - round 21's carry by square; round 22 guarded the fold alone).

---

### RSA7-A1 - The track editor's Cancel after a square was deleted, cut or cleared: the Cancel puts the setup back, and the reset's fold then judges "a square whose tile is gone" against the page objects the editor mutated in place - so it erases the placement, the home and every setting of each deleted square, and a train standing on one drops out of the railway

| | |
|---|---|
| **Disposition** | Fixed - no fold follows a square removed or built over until the setup is opened again (AutonomySession.moveTiles sets squaresMoved for builtOver, as it does for a move): the track editor's Cancel or Discard puts the setup back as the editor opened it, and the reset's fold no longer judges those squares against the page the editor had emptied.  Opening the editor folded already, and nothing runs while it is open.  claims and fix 38dff3bb; mutation MA1 red.  The claim deletes as the editor does - the page's square emptied, forgetTiles - then restores the snapshot and folds the railway running. |
| **Grade** | A.  It loses a user's data - a square's home, its "trains may turn round here", priority, maximum length, excluded locomotives, facing - and a train standing there leaves the model while it stands on the track: the station reads free and the next dispatch into it is admitted.  **How:** the track editor edits the model's own `LayoutDiagram` objects, which the autonomy session holds too.  Delete (`LayoutEditor.delete`) empties the square in that object and tells the setup to forget it (`forgetTiles`), and writes the setup.  Cancel (`confirmExit`, after `mayLeave`'s Yes) first puts the whole setup back as the editor opened it (`undoAutonomyEdits` -> `restoreSetup`, which rebuilds the graph from the session's page objects - still without the square), then posts `layoutEditingComplete`, which re-reads the pages into new objects and resets the session - and the reset folds first (`resetAutonomySession` -> `captureRunningLayout`).  The fold's prune keeps nothing for "a square whose TILE is gone", judged against `graph.getTiles()` of the old session - the mutated pages - so every configuration key of each deleted square, just restored, is removed and written (`saveWithoutReconciling`).  The new session, over the re-read pages where the square is back, builds no train there and no home.  `captureRunningLayout`'s own comment names this hazard - *"on the editor path the pages this session holds are the ones the editor mutated in place ... reconciling against them deleted every setting on every square they had deleted and thought better of"* - and answers it for the store's reconcile, not for the fold's own prune.  A page or mode switch answered Discard (`leaveFor`'s track branch) takes the same order.  **Not** the application's exit (its fold runs before `completeExitDiscard`), and not a move (`squaresMoved` refuses that fold).  **Reach:** any square with configuration settings or a train - a station, a home, a terminus - deleted, cut or cleared in the track editor and the edit then cancelled or discarded, with a configuration loaded.  **Older** (the prune and the Cancel's order predate round 22); found asking the round's question of every fold. |
| **Names** | SA-A3, AMS-A1, DW-C1, OB-144, OB-183, X8-B4 |
| **Where** | `LayoutEditor.java:6419-6439` (`confirmExit`: `undoAutonomyEdits` at 6431, then `layoutEditingComplete`), `:631-644` (`undoAutonomyEdits` -> `restoreSetup`), `:6117-6130` (`leaveFor`'s track branch, the same order), `:3617-3672` (`delete`: the page object emptied, `forgetTiles`); `AutonomySession.java:3155-3164` (`restoreSetup`: `rebuild` from the pages this session holds), `:6099-6161` (`capture`'s prune: `stillThere` from `graph.getTiles()`, `gone` on a page in play, every key removed); `TrainControlUI.java:24131-24166` (`layoutRefreshCompleteInternal` -> `resetAutonomySession`), `:3251-3256`, `:3198-3244` (`captureRunningLayout`, the comment at 3232-3238) |
| **Needs execution** | done - **P6** (`rsa7ProbeP6TheCancelAfterADelete`): Alpha, Beta, Gamma; `EN71-010` on Beta, `EP05-001` its home; the editor's opening fold and undo point; Beta's tile deleted as `delete` does it (forgotten: true); Cancel's `restoreSetup` puts back **loc `EN71-010`, home `EP05-001`**; **(a)** the reset's fold: **loc null, home null**; the next session over the pages re-read: **loc null, home null, the railway `[]`** - `EN71-010` stands on Beta and never moved.  **Control (a, no fold after the Cancel):** the next session has both, and `EN71-010` on `Beta (westbound)`. |

**Direction:** make the reset after a track-mode Cancel or Discard not fold - the opening fold already captured, and nothing runs while the editor is open (as `squaresMoved` does for a move) - or judge the prune against the pages as they are on disk; and a claim that deletes, cancels and reloads as the editor does.

---

### RSA7-B1 - The direction refusal cannot tell which way a train on a copy that records no facing faces: it skips the railway's train and reads the setup's record, often the facing the previous train there left - so with round 22's "a copy recording no facing faces its only way out", one stale record refuses every direction and every link switched off on the railway, and no record lets the rail be made two way under the train, which is then left facing the buffer with no way out

| | |
|---|---|
| **Disposition** | Fixed - the refusal asks only about a train the change turns: one on a copy facing its way in the naming before the change (a trial built at the first direction of a batch, or before a link is switched), and on a square with no copy its way after.  A train on a square of one copy recording no facing faces its only way out, on the railway's half and the setup's, whatever facing an earlier train left recorded.  So a stale record refuses nothing, and a dead end made two way under a train facing out is refused.  claims and fix 38dff3bb; mutations MB1s red, MB1a red, MB1b red, MB1n red, MB1l red.  Four claims: the stale record (kept by either half of the fix, so only the refusal as it was turns it red), the dead end made two way with no record and with a stale one, and a train facing no copy that a change leaves alone. |
| **Grade** | B.  A guard that refuses where it should not and not where it should, around a train the operator can see.  **How:** a square nothing arrives at with one way out - a dead end whose only rail leads away - is one copy with no facing, and its running Point writes no copy facing.  `trainsTheSetupWouldTurn` takes the railway's train only where it has a facing (`copy facing null` and no facing by name: skipped) and then the setup's record of that train, `getFacing` - a record the fold never clears when a train leaves (*"a square with no train on it still remembers which way the last one was pointing"*), that a placement inherits, and that the Facing menu cannot correct there (`facingChoices` is empty).  Round 22 made the trial treat such a copy as facing its only way out (`onlyWayOutFacing`, RSA6-B3), so a record facing the buffer makes that train "turned" before anything changes: **every** direction door and every link switched off, anywhere, is refused naming it - *"stands on Alpha facing the other way"*, and for a link *"came in through this link, so it was not switched off"*, which is false.  The state comes through today's doors: trains stood at a dead end facing the buffer (the fold writes that facing), the last one left, the rail out made one way away while the square was empty (allowed), and another train placed there by hand.  The way past is to send that train out or take it off and place it again (removal clears the facing), which nothing tells the operator.  **And the other way:** with no record, the railway's train is skipped and nothing asks - the rail made two way again leaves the square one copy facing the buffer, the carry keeps the train by its unchanged name, and the train, placed facing out, is now recorded facing the buffer with no way out (the mirror of RSA6-B3).  **Reach:** a dead end made one way away that a train is later placed on by hand; on the frozen railway the refusal's question is empty today (P5).  **New in round 22** for the lock-out (`onlyWayOutFacing`); the skip is round 21's. |
| **Names** | RSA6-B3, RSA5-A2, RSA2-C7, RSA6-A3 |
| **Where** | `AutonomySession.java:7328-7428` (`trainsTheSetupWouldTurn`: the railway's train skipped where its facing is null, `:7340-7349`; the setup's record, `:7353-7367`; `onlyWayOutFacing`, `:7412`), `:7494-7516` (`setPortalDisabled`'s sentence), `:6075-6082` (the fold writes a facing and never clears one), `:7651` (`placeLocomotive` clears a facing only when a train is taken off), `:7879` (`facingChoices`); `AutonomyBuilder.java:1279` (no copy facing on a square of one copy), `:1631-1636`; `messages.properties` `autosetup.ui.errorLinkTurnsATrain` |
| **Needs execution** | done - **P2h** (`rsa7ProbeP2hTheStateThroughTheDoors`, two pages, `main` Alpha a dead end): a train run into Alpha and folded (the setup's facing at Alpha **W**), run out to Gamma and folded (Alpha empty, facing still **W**); one way east out of Alpha: refused null, Alpha's copies `{}`, facing choices `[]`; `EP08-009` placed at Alpha by hand: facing there **W**; the refusal's question with nothing changed: **`{EP08-009=Alpha}`**; one way on the other page: **refused** - *"EP08-009 stands on Alpha facing the other way"*.  **P2f** (the state set directly, a paired link on the other page): the direction there refused; the link there switched off: **refused, "came in through this link"**; control, no facing recorded: `{}`, both set.  **P2g**: no facing recorded, the rail made two way again: **refused null**, Alpha's copies now `{Alpha=W}`, `pointNamedNow -> Alpha`, after the rebuild the train on Alpha, **ways out `[]`**; control, facing E recorded: refused. |

**Direction:** read a train on a copy that records no facing as facing that copy's only way out on both halves - the railway's and the setup's - and never from a record another train left; and let the guard ask only about the trains the change can turn, or say which gesture of the operator's it refused and why in words about that train.

---

### RSA7-B2 - The Autonomy panel's Pages dialog is not refused during a run and never reloads: a page ticked out there goes on being run - its stations dispatched to, its timetable entries executed - until something else loads, and a page ticked in is not run

| | |
|---|---|
| **Disposition** | Fixed - the Pages dialog is refused while autonomy runs, before it opens and again when applied, asks first where a train is moving, and rebuilds the railway after it (AutonomyViewerPanel.applyPages, which its OK calls), as the Autonomy menu's tick does.  claims and fix 38dff3bb; mutations MB2r red, MB2l red.  The claim drives applyPages on the frozen railway's window: refused and unchanged while busy; at rest the railway rebuilt with no Point on the page ticked out. |
| **Grade** | B.  Adam's rule is that no setup edit is possible during a run (MT-141); the Autonomy menu's own Pages tick refuses while autonomy is busy and reloads at rest, and its comment names what the dialog still does - *"ticking a page off mid-run turned the strip red, took the station captions away and made right-clicks on that page's stations do nothing, while the layout carried on routing trains to those very stations"*.  **How:** `choosePages` (the Pages... button on the Autonomy panel, and the item on its Manage menu) sets each page, saves and refreshes the panel; it asks nothing about a run and loads nothing, and nothing disables the button.  So during a run the setup is edited and written, the session rebuilt under the running railway, and the page's labels go; at rest the railway keeps running the page the setup says is out, full autonomy and the Auto tab included, until an unrelated load.  Round 22 made the timetable follow a railway built either way (D4); the railway itself does not follow the dialog.  **Reach:** the Pages dialog, during a run or at rest.  **Older**; RSA4-D9 read the Autonomy menu's tick, and RSA6-B1 noted that the dialog "loads nothing" for the timetable's sake. |
| **Names** | MT-141, RSA4-D9, RSA6-B1 |
| **Where** | `AutonomyViewerPanel.java:591-635` (`choosePages`: no busy question, `setPageExcluded`, `save`, `refresh`), `:352-353`, `:432-433` (its two doors), `:1788-1832` (`refresh` loads nothing); against `AutonomyMenu.java:761-769` (refused while busy) and `:816` (`reloadActiveDiagramConfiguration`) |
| **Needs execution** | done for the reload - **P1 (E), (K), (M)**: after the dialog ticks `second` out, the running timetable is still **`[2]`**, `E 991: Epsilon (eastbound) -> Zeta` among it, and the railway still has the page (M: `E 991` on Zeta); only the next start builds without it.  The busy half by reading: the method has no refusal. |

**Direction:** refuse the dialog while autonomy is busy, as the menu does, and reload after it as the menu does.

---

### RSA7-B3 - While a declined edit waits, the carry across a load reads each train's square as it was before a track edit's move or a page rename: after a move every train is put on the station that has taken its old square, and after a rename on nothing - so it is left where the setup had it before the run, and the square it stands on reads free

| | |
|---|---|
| **Disposition** | Fixed - a move, or a page renamed, moves the squares the railway's Points say they are copies of with the setup's (AutonomySession.moveTheRailwaysSquares, from moveTiles and the new renamePage, which LayoutPageEdit now calls), and restoreSetup puts them back, so a Cancel undoes a move on the railway too.  The carry while a declined edit waits reads each train's square as it now is.  claims and fix 38dff3bb; mutations MB3m red, MB3r red, MB3c red.  The Cancel half is a sibling the sweep found: moving the railway's squares without it would have sent each train, after a cancelled move, to the station on its moved square. |
| **Grade** | B, as RLV6-B1 was for the same state.  **How:** while a setup edit a run declined waits, no door folds the railway - the editor's opening fold and the rename's fold included - and every load carries the trains across instead (`carryTheTrainsAcross`: `whereTheTrainsAre` records each train's Point, square and copy facing; `putTheTrainsBack` puts each on the Point the session's `pointNamedNow` gives).  Round 21 made that answer by square: a name is kept only where the new build still gives it to the **recorded square**, and otherwise the square's own name now takes it.  A track edit's Save or a page rename resets the session and loads through that carry - and the recorded squares are the squares as they were.  After columns inserted, the name `Gamma (eastbound)` is on another square, and the recorded square holds Beta: the train at Gamma is put on Beta, the one at Beta on Alpha - every train one station back, the last station reading free.  After a page rename no recorded square exists, so every train on the page is left where the setup had it before the run.  By name, as before round 21, each lands right.  Round 22's `squaresMoved` says the same thing of the railway - *"that railway names and places what it knows by the squares as they were"* - and refuses the fold alone; the carry the same state takes is left.  **Reach:** a setup edit made as a run starts (its rebuild declined), then, before any load has replaced the railway, a track edit that moves squares and Save, or a page renamed.  **Older** - round 21's carry by square (RSA5-A1); round 22 left it out. |
| **Names** | RLV6-B1, RLA5-B1, AMS-B1, RSA5-A1, RSA6-A2, RSA6-B2, DW-A1, OB-183 |
| **Where** | `TrainControlUI.java:3072-3101` (`carryTheTrainsAcross`), `:6737-6790` (`whereTheTrainsAre`: square and copy facing recorded, `:6759-6761`), `:6902-6912` (the session's `pointNamedNow`), `:3208-3222` (no fold while the edit waits), `:3277-3280`, `:24155-24166` (the reset, then `load`); `AutonomyViewerPanel.java:791-805` (the carry chosen); `AutonomySession.java:512-571` (`pointNamedNow`: the name kept only on the recorded square, `:522`; the square gone, `:524-527`; the same copy under the square's name now, `:530-537`), `:3270-3278` (`squaresMoved`, the fold only) |
| **Needs execution** | done - **P4** (`rsa7ProbeP4TheCarryAfterAMoveOrARename`): Alpha, Beta, Gamma, Delta; the setup has `EN57-925` at Alpha and `EN57-947` on Beta; the run's result, on the running layout alone, `EN57-925` on `Gamma (eastbound)`.  **(a) two columns inserted, the session's carry:** `pointNamedNow(Gamma (eastbound), main:5,1, E) -> Beta (eastbound)`, `(Beta (westbound), main:3,1, W) -> Alpha`; after the carry **`EN57-925` on `Beta (eastbound)` [main:5,1], `EN57-947` on Alpha [main:3,1]** - they stand at Gamma [main:7,1] and Beta.  **Control (a), by the names recorded:** `EN57-925` on `Gamma (eastbound)` [main:7,1], `EN57-947` on `Beta (westbound)` [main:5,1].  **(b) the page renamed:** both `-> null`; after the carry **`EN57-925` on Alpha**, where the setup had it; **control (b):** on `Gamma (eastbound)`. |

**Direction:** record, with each train, the square as the carry will read it - move the recorded squares with a move and a rename, as the store's keys are moved - or carry by name first where the recorded square no longer is what it was; and a claim that carries across a move and a rename.

---

### RSA7-D1 - RSA6-A1: every session reads the railway from the moment it is made

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RSA6-A1, RSA5-A2 |
| **Where** | `TrainControlUI.java:2872`, `:2987`, `:4817-4826`; `LayoutEditor.java:1704-1712`; `AutonomySession.java:7332`, `:8957-8968`, `:8989-9004`, `:9304` |
| **Needs execution** | done - the window claims green (baseline); P2 (a); the rest by reading |

There is one construction of a session, and it sets the supplier; the other four setters set the same supplier.  Its three readers - the refusal, the Facing menu's move and a home's facing - are editor doors that were told already, so nothing else changed behaviour.

---

### RSA7-D2 - RSA6-A2: the fold writes each running Point into its own square, with its copy's facing

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RSA7-A1 (the prune after a Cancel). |
| **Grade** | D |
| **Names** | RSA6-A2, OB-183 |
| **Where** | `AutonomySession.java:5895-6192`, `:6348-6373` |
| **Needs execution** | done - P1 (I), (J), (M): `E 991` on Zeta, where the run left it, after the menu's tick, the editor's box and the Pages dialog with a restart between |

`capture` is the only fold; a Point whose square the diagram has is written there, on a page out too, and the facing comes from the copy where it has one and from today's naming only for a name still on that square.

---

### RSA7-D3 - RSA6-A3: a link switched off under a train that came in through it is refused and put back

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RSA7-B1 (which trains the refusal can read). |
| **Grade** | D |
| **Names** | RSA6-A3, OB-031, GSE-B1 |
| **Where** | `AutonomySession.java:7373-7381` against `:437-468`, `:7494-7516`, `:9858-9872`; `AutonomyEditorPanel.java:1955-1969`; `TileGraph.java:906-951` |
| **Needs execution** | done - P2 (d): refused, the link on, the train on `Gamma (westbound)` after the rebuild; (e): unpairing is still 2 blocking |

The trial is built as `rebuild` builds the graph - pages, excluded pages, the store applied, the portals validated, the reduction - and the switch says the refusal before `setupChanged`.  Pairing switches both ends on and adds ways in; unpairing, and a pairing left pointing at a page out, are blocking, so their rebuilds are refused and the railway is kept.

---

### RSA7-D4 - RSA6-B1, and Adam's question: the timetable keep is reliable at every door that ticks a page

| | |
|---|---|
| **Disposition** | Checked - clean.  Since then (round 23, Adam 2026-09-30: "Do the simplification of the timetables") a page out's entries are kept in a list of their own beside the timetable, which only a build with the page back in, or Clear, touches; the claims of rounds 20 to 22 read the timetable as that keeps it. |
| **Grade** | D |
| **Names** | RSA6-B1, RSA5-B1, RSA4-B2, RSA5-C1 |
| **Where** | `AutonomySession.java:6180-6187`, `:6209-6257`, `:6263-6310`, `:6381-6408`; `AutonomyMenu.java:795-816`; `AutonomyViewerPanel.java:628-634`; `AutonomyEditorPanel.java:9947-9977`; `TrainControlUI.java:26427-26443`; `AutonomySession.java:2228-2281` |
| **Needs execution** | done - P1, sixteen cases; P5 |

Which pages are out is read off the running layout's own Points, so every door is the same to it.  **P1:** out from the start and in by the menu or the editor (A1, A2); out by the menu and in by the editor across an exit (B), by the menu both ways (C), by the editor both ways (D), by the Pages dialog both ways across an exit and a restart each way (E), a dead locomotive's entry beside them (F), one deleted and recorded again (G), a name shared with the other page (H), a run's train on the page (I, J, M), and the pairings RSA6 did not try - dialog then menu (K), menu then dialog (L), editor then menu (N), menu then editor (O): **every case ends `stored [2]`, each entry once**, and F erases only the dead locomotive's.  **P5, the frozen railway:** the running layout left out `[5 - Test, 3 - Top Parking, 4 - Combined]`, its two pages in play both have Points, and its one captured entry (`EN57-947`, all on `1 - Main`) is `stored [1]` at every step of `1 - Main 15,5`'s link switched off then `2 - Bottom` ticked out and in, and of the other order, where the tick's load is refused (1 blocking) until the link is off.  The combine door and the import's `excludeRepeatedSensorPages` tick pages the running layout never had; excluded pages are one set for every configuration.

---

### RSA7-D5 - RSA6-B2: no fold follows a move of squares

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RSA7-B3 (the carry that state takes). |
| **Grade** | D |
| **Names** | RSA6-B2, RSA5-B2 |
| **Where** | `AutonomySession.java:101-130`, `:3243-3305`, `:5919`; `LayoutEditor.java:3089`, `:3256`, `:4609`, `:4666`, `:4711`, `:4768`, `:6030-6131`, `:6419-6439`, `:7381-7412` |
| **Needs execution** | done - P3 (b): `main 3,2` kept through the Save's fold and the reload; (d): the two `Hbf` trains each on its own station |

Every caller of `moveTiles` is the track editor, and every way out of track mode ends in `layoutEditingComplete`, whose reset makes a new session - so the flag cannot outlive the editor.  The opening fold is refused only while a declined edit waits, which is B3's state.

---

### RSA7-D6 - RSA6-B3: a dead end made one way away from a train facing the buffer is refused

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RSA7-B1. |
| **Grade** | D |
| **Names** | RSA6-B3, RSA2-C7 |
| **Where** | `AutonomyBuilder.java:611-639`, `:646-723`, `:908-930`, `:1631-1636`; `AutonomySession.java:7396-7419` |
| **Needs execution** | done - P2 (c): refused, nothing stored, the train on Alpha facing W |

`onlyWayOutFacing` is asked of the trial's own builder, and a square with no way out, or more than one, answers none.

---

### RSA7-D7 - RSA6-C1: names settled over every page

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RSA6-C1 |
| **Where** | `AutonomyBuilder.java:1693-1770`; `AutonomySession.java:5054-5066`, `:6316-6339` |
| **Needs execution** | done - P1 (H); the claim green (baseline); P5: no names out of play on the frozen railway |

Every builder comes from one factory, so the naming, the file, the trial and the station index settle the same names; a page out's named squares take their turn and are not emitted.

---

### RSA7-D8 - The other gestures that change a square's copies keep a standing train's

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RSA5-A2, TDY3-A1, AUT2-A1 |
| **Where** | `AutonomyBuilder.java:443-460`, `:496-514`, `:646-723`, `:959-984`; `AutonomySession.java:5277-5281`, `:5790-5798`, `:7558`; `TrainControlUI.java:7084-7128` |
| **Needs execution** | by reading; P5: the refusal's question empty on the frozen railway |

A barred side keeps its copy (it bars stopping, not standing); every Point splits by the edges that reach it, station or not; "may turn" adds turning copies and "must turn" swaps plain for turning ones with the same facings; a dead end is one copy either way and keeps its plain name, which the carry keeps.

---

### RSA7-D9 - The probes' controls, and the baseline

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | - |
| **Where** | `validate30/rsa7/p1/test/regression/rsa7Probes.java`, `rsa7Frozen.java`; `validate30/rsa7/p1/rsa7-out/`; `validate30/rsa7/logs/` |
| **Needs execution** | done |

Each probe states its precondition before its result, and each measured defect has a control beside it that answers the other way: P6's control for A1; P2f's control and P2g's for B1; the running layout after the menu's and the editor's ticks for B2; P4's two controls for B3.  The baseline: 15 and 2 run, 0 failures; the probe jobs 8 and 1 run, 0 failures, after a first attempt that did not compile and ran nothing; no job hung, ran out of memory or was ended by the runner.
