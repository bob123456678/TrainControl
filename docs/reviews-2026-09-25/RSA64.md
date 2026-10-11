# RSA64 - Validation of round 129 (`878bc6e7..2bef7a3b`, at `2bef7a3b`) and of rounds 124-129 for regression and icon clarity: RSA63's seven Cs are fixed - the power is one epoch read once, and a STOP landing at any point round dispatch or a timetable attempt is seen; a double-curve station whose track runs out is a terminus; Show Trains takes the setup's train mark; a shaded station spills nothing and a neighbour's outline is drawn over a spill; a square out of service takes no direction click and says why; the documents say what the icons are - with no regression found in any picture, power path or claim; two Cs: Show Trains no longer repaints the window, so a train the railway has where the setup has none is left drawn (C1), and the code's own comments still describe the editor's icons as they were (C2) - CONVERGED (TrainControl 3.0.0)

**Status:** open

**Prefix:** RSA64

**Reviewed:** branch `route-stop-only` at `2bef7a3b` (*Round 129: the power epoch compared, nothing more - the odd-at-dispatch checks were redundant*): `git log 878bc6e7..2bef7a3b` - round 129 (`1c124337`), RSA63's records (`dc924a3d`) and the follow-up (`2bef7a3b`); and `git diff 128aed37^ 2bef7a3b -- src test docs/reference` for rounds 124-129.  Read from `git archive` of `2bef7a3b` (`rsa64/d/`) and, for controls, of `878bc6e7` (`rsa64/p/`, the commit RSA63 reviewed), `cs2_sample_layout` excluded, each with an empty `cs2_sample_layout` folder for one.sh's guard.  Neither carries `LocDB.data` or `UIState.data`; none was copied.  The tracker read at `c10f5bdb` and, once the coordinator named it, `179be553` (RSA63's eighteen catalogued).  2026-10-11 01:27 to 03:05.

## Method

**Read whole:** the brief; `validate30/RSA63.md` and its dispositions (`round86/RSA63_record.md`); the three commit messages and every hunk of `git diff 878bc6e7 2bef7a3b` over `src test docs/reference`, `Automation.md` and `AutomationAPI.md`; the rounds 124-129 diff of `TileAnnotation`, `AutonomySession`, `AutonomyEditorPanel`, `LayoutLabel`, `LayoutGrid`, `LayoutEditor`, `TilePorts`, `MarklinControlStation`, `ViewListener` and `Layout`.  **At the commit:** `MarklinControlStation.setPowerState` (`:4327`), `getPowerEpoch`, the GO/STOP branch (`:2975-3005`), the simulation's start (`:5344`), `waitForPowerState`; every reader of `getPowerState` and `getPowerEpoch` (`Layout` `:2464`, `:2486`, `:7608`, `:7659`, `:7733`, `:9988`, `:10159`; `TrainControlUI` `:7135`, `:12249`, `:12901`, `:24844`, `:30226`; `MarklinRoute`); `Layout.executePathInternal` (`:9975-10215`), `configureAndLockPath`'s per-switch sleep (`:3858`), `executeTimetableInternal`'s entry thread (`:7590-7745`); `AutonomySession.deadEndSide` (`:11763`), `stationRoad` (`:11873`), `staticAnnotationOf`, `getLocomotiveNameAt`; `AutonomyEditorPanel` - the Show Trains listener (`:911`), `refresh()` (`:10369-10568`) and both `onChanged` runnables (`LayoutEditor:1805`, `TrainControlUI:5342`), `annotationFor` (`:9713`), `badgeFor`, `clickChangesDirections` (`:8020`), `isOutOfService` (`:8049`), `cycle` (`:8067`), `tileClicked`, `leaderOf`, `setUsage`, the findings list's listener; `LayoutEditor.refreshAutonomyAnnotations` (`:2267`); `LayoutLabel.setAutonomyAnnotation` (`:1394`), `repaint`, `paintComponent`, `paintCoveredMark`, `coveredRoads`, `paintTrainOverCaptions` (`:2222`), `paintStationSpill`, `paintArrowsOverSpill`; `LayoutGrid.newDiagramContainer` and the Show Trains hand-over (`:1383`); `TileAnnotation.paint`, `paintBadge`, `paintBadgeOverRun`, `paintStation`, `coverTheContact`, `spillsOverItsSquare` (`:2424`), `paintArrowsAgain` (`:2498`), `paintOutOfService` (`:2746`), `inTheEditor`; `TileOverlay.paint`/`parked`; `DiagramExport.render`; behaviour.md 2, 4b, 6a, 8 and the editor's keyboard doors; `Automation.md` and `AutomationAPI.md` where round 129 changed them; the eight new sentences (`autosetup.ui.infoOutOfServiceHasNoDirection`).  Each finding was searched for in `findings.tsv` at `c10f5bdb` and `179be553` before it was written: neither is there (RSA63-C3 and RSA63-C6 are Closed; C1 and C2 say why each is new).

**Runs.**  Every JVM through the archives' own `docs/tools/one.sh`, `TC_SCRATCH` a Windows-form folder per job under `rsa64/w/scratch`, the JVM's temporary folder `rsa64/w/tmp`, started by `rsa64/w/runjob.sh` (RSA63's runner, re-pointed: it waits until no `java.exe`, `javaw.exe` or `javac.exe` and no battery lock have been seen in two checks 20 s apart, polling once a minute, never kills anything, and treats a `HOLD` file as busy) one job at a time from `w/q1.sh`.  Read and probe-writing only while battery 81 held the lock (from 01:28; lock gone 02:44); no JVM, javac included, of mine ran before 02:45:23.  My jobs ran 02:45:23 to 02:58:11.  Model probes headless (`init(null, simulate = true, ...)`); picture and window probes with this machine's display (125 per cent).  No Central Station was reached; nothing hung, ran out of memory or tripped the layout guard; one.sh never refused a run.  The coordinator reports battery 81 on this commit green apart from the four main-checkout classes and one `core.testMaxActiveTrains` timing flake; I did not see its results.

**Scratch classes** (`rsa64/w/probes/`, copied into the archives' `test/`; nothing asserted; output in `w/out/<d|p>/`):

- **`core.rsa64Power`** - RSA63's `rsa63Power` re-pointed at the epoch: a line of eight edges with a switch on each, Path Integrity Validation off, the power moved only as the Central Station moves it (`setPowerState` or `receiveMessage`), the log tapped.  (g) STOPs, responses and a short frame through `receiveMessage`; (a) autonomy, (b) Execute Timetable, a cut that lasts; (d) autonomy, (e) a hand send, a cut begun and ended inside the route's setting; (c) a sequential timetable (Return Home's shape), three 3 s cuts; (h) a parallel timetable, `TIMETABLE_STUCK_MS` 3 s, four cut attempts; (i) Echo Sent Commands.  **Round 129's interleavings**, with a `ViewListener` proxy placing the STOP: (f1a) STOP 150 ms into the route, GO 300 ms later; (f1b) the STOP right after the dispatch's one epoch read (`:9988`), GO 300 ms later; (f1c) RSA63-C1's instant at the new code - the STOP's increment taken in at the dispatch read, its state written 5 ms later, GO 300 ms later; (f1d) the STOP between the check's state read and its epoch read (`:10159`), GO 300 ms later; (f1e) the power off at the dispatch read and on again 30 ms later, before the route's first switch; (f1f) a STOP that lasts, landed after the dispatch read.  `f2Only`: a STOP between each of three timetable attempts' power wait and their epoch read (`:7608`, `:7733`), GO 600 ms later, in a sequential run.  `jBlocked`: the control - a sequential leg whose end is occupied, the power on throughout.
- **`core.rsa64DispClaims`** - `core.testATrainIsDispatchedOnce` at the commit, unchanged but for one line making 24 locomotives by name after `init`.
- **`core.rsa64Names`** - `core.testAutonomyDiagramSession` at the commit plus RSA63's `rsa63DeadEndProbe` (a double-curve station at four orientations, a sensor missing beyond each side in turn: the badge's dead end, `deadEndSide`, and the icon drawn against the terminus on its own road) and `rsa63OutOfServiceProbe` (the editor's click on a square in and out of service), re-pointed, the second now reading the strip's line after the click.
- **`ui.rsa64ShowTrains`** - the autonomy editor at 30 px over Adam's frozen main page (`test/layouts/live-snapshot`, `1 - Main`, its four trains made by name so the railway places them): every dirty region asked during a click on Show Trains, recorded by a `RepaintManager`, against the squares the railway's trains are drawn on - (A) the setup and the railway agreeing, (B) one train taken off its station in the setup only, as while the setup cannot build; (C) the setup (`snapshotSetup`), the railway's identity and the findings list's selection before and after.  And `exportBesideA30pxGrid`: a pop-up-style grid of the page at 30 painted before and after a `DiagramExport` at 60, the export made twice, crops round the curve stations.  Run at `2bef7a3b` and, `showTrains` alone, at `878bc6e7`.
- **`ui.rsa64EditorSpill`**, **`ui.rsa64Spill`**, **`ui.rsa64Trains`**, **`ui.rsa64Marks`**, **`ui.rsa64Gallery`** - RSA63's probes re-pointed; the gallery with a sheet added (`DC-<diagram|editor>-<30|60>.png`): the terminus on a curve and a double curve, flat at either end of the contact's road, at rest and with every arrow and arrival mark.  `rsa64EditorSpill` and `rsa64Marks` run at both commits; the gallery's twelve sheets compared with RSA63's pixel for pixel (`w/compare.py`).

**Ran, at `2bef7a3b`:** the probes above; `core.rsa64DispClaims` **50 of 50** (round 129's `testThePowersEpochIsOddWhileThePowerIsOff` and `testAJourneyDispatchedAsThePowerWentOffIsGivenBack`, round 125's `testAJourneyIsNotSetGoingIfThePowerWentOffAndOnWhileItsRouteWasSet` and `testPowerCutsDoNotEndAReturnHomeLeg`, and the seen-once `testARetiredRailwaysJourneyEndsWithItsRailway` among them); `core.testAutonomyDiagramSession` **218 of 218** (round 129's `testADoubleCurveStationWhoseTrackRunsOutIsATerminus`, `testShowTrainsOffHidesTheSetupsTrainMarkToo`, `testAClickChangesNoDirectionOnASquareOutOfService`); `core.testAutonomyDiagramMonitor` **62 of 62** (`testAShadedStationSpillsNothing`); `regression.testEditorSurfaceRules` 68 of 68; `core.testHomeStaging` 115 of 115; `core.testControlStationFaults` 12 of 12; `core.testMessageBundles` 23 of 23; `regression.testTheEditorSaysWhatItsToolsDo` 10 of 10; `ui.testDiagramLooksRight` 50 of 52 (`testANeighboursOutlineIsDrawnOverASpill` green), the two red `testARealPathToACurvedStationIsDrawn` and `testEveryPixelOfARunLandsOnTrack` with *"Index: 0, Size: 0"* - no locomotive in the archive's database, as RSA60 to RSA63 found.  `ui.testTheGreyAppearsAtIdleToo` (seen once) not met.

**Not reached:** real detection and the real railway; a display at 150 per cent (this machine is at 125); RSA62-B1's cost in Adam's own window (round 129 changes no paint the viewer makes; RSA63 measured it offscreen); a pop-up window at another size as a real window (reached as a pop-up-style grid at 30 beside an export's grid at 60, sharing the registry's annotations - D10).

**Nothing written** outside `validate30/rsa64/` and this file, apart from what the runner makes (the isolated preference node and data folder per run, which one.sh removes).  Nothing of mine called `saveState`.  **The real files:** `C:/Users/adamo`'s `LocDB.data` (`6ef569398873b0de`) and `UIState.data` (`9e32894e3bcfe28a`) and the main checkout's (`dc812c1a271f4b5a`, `2619d589fcfa2345`) unchanged byte for byte and time stamp from 01:29 to 02:59 (`rsa64/w/real-hashes-before.txt`, `-after.txt`).  The main checkout touched only through `git -C <main checkout> archive / log / show / diff` and to hash and stat the two files; `tc-30`, `r34fix` and `bat66` neither read nor run.  **Running:** no JVM, runner, queue, monitor or loop of mine is left (02:59: no `java.exe`, `javaw.exe` or `javac.exe` on the machine, no battery lock, no `HOLD` file).  Nothing was refused by the permission system.

**Counts:** 0 A, 0 B, 2 C, 11 D.

**This round has CONVERGED: no A and no B.**  **What round 129 got right** (D1-D11): the power is one number read once - a STOP landing after the dispatch read, between the check's two reads, at the very instant RSA63-C1 named, or between a timetable attempt's power wait and its read, is seen every time, and Return Home no longer ends "blocked" for it (D1); every power path behaves as before, and a refusal by the track is still counted (D2); a double-curve station whose track runs out on the contact's curve is a terminus facing that way at every orientation (D3); Show Trains takes the setup's mark, writes nothing and rebuilds nothing (D4); a shaded station spills nothing and a neighbour's outline is whole beside a spill (D5); a square out of service takes no direction click and says why (D6); behaviour.md 4b and the guides say what the icons are (D7); no picture changed anywhere else - 256 of 256 marks and 12 of 12 gallery sheets identical (D8); the new terminus pictures read as the curve's (D9); an export and a grid at another size share the annotations cleanly (D10); no new race (D11).  **What remains, both C:** the Show Trains switch now repaints only the squares whose annotation changes, so where the railway has a train the setup does not place - the setup unable to build, the last valid railway kept - its icon stays drawn with the trains switched off (C1); `TileAnnotation`'s and `AutonomyEditorPanel`'s own comments still describe the editor's icons as rounds 127 and 128 left them behind (C2).

---

### RSA64-C1 - New in round 129 (RSA63-C3's fix): the editor's Show Trains switch runs the panel's `refresh()` where it repainted the window, and a refresh repaints only the squares whose annotation changes - so a train the railway has on a square where the setup places none (the setup unable to build, the last valid railway kept) keeps its icon when the trains are switched off, and has none drawn back when they are switched on, until something else repaints that square

| | |
|---|---|
| **Disposition** | Fixed in round 130 (`63e74f85`), as the finding suggests: the switch repaints its window's root pane after the refresh - `core.testAutonomyDiagramSession.testShowTrainsRedrawsEverySquareInItsWindow` (red first). |
| **Grade** | C - a picture left wrong (the switch says the trains are hidden; one is drawn), on a corner at rest while the setup is mid-edit, which Adam's ruling of 1 October grades C at most.  With the setup and the railway agreeing - the ordinary case - every train's square is repainted (D4).  Any hover over the square, or anything else that repaints it, puts it right. |
| **Names** | RSA63-C3 (Closed; its fix replaced the repaint), FR-119, RSA23-C1 (the 3 x 3 repaint round a train), Adam's 1 October ruling (*"the last valid state gets used"*) |
| **Where** | `AutonomyEditorPanel`'s Show Trains listener (`:911`) calls `refresh()`, which ends in `onChanged.run()` (`:10567`) - in the editor `LayoutEditor.refreshAutonomyAnnotations` (`:2267`) and the main window's static layer - where it called `window.repaint()`.  `LayoutLabel.setAutonomyAnnotation` (`:1394`) returns at once for an equal annotation (`:1400`), and the switch changes an annotation only where `locomotiveAt(tile)` - the setup's placement - names a train (`annotationFor`, the `withTrain` line).  The trains' icons are drawn from the railway's overlays by `paintTrainOverCaptions` (`:2222`), which asks the switch as it paints, so a square the switch does not repaint keeps whatever it last drew. |
| **Needs execution** | done - **`ui.rsa64ShowTrains`** (`w/out/d/T2-showtrains.txt`), the editor over Adam's frozen main page, trains drawn on four squares: (A) setup and railway agreeing - *"Show Trains -> false: train squares 4, repainted 4; dirty regions asked 4"*, and the same back on; (B) *"the setup's EN57-203 taken off 1 - Main:4,5 in the setup only (the railway keeps it there) ... the railway's train still drawn there: true"*, then *"Show Trains -> false: train squares 4, repainted 3, NOT repainted at 4,5"* and *"Show Trains -> true: ... NOT repainted at 4,5"*.  **Control at `878bc6e7`** (`w/out/p/T2-showtrains.txt`): *"repainted 4; dirty regions asked 0 (the whole window)"* in all four cases.  **One way to settle it:** the listener repaints the diagram (or the window, as before) after `refresh()`. |

---

### RSA64-C2 - The code's own comments still describe the editor's icons as they were before rounds 127 and 128: `TileAnnotation.inTheEditor` says the one thing it changes is that a bend's badge moves out to the corner, `paintBadge` that a station on a bend in the editor goes off the rails, `paintStation`'s table that out of service is the orange X over the block in grey, and `AutonomyEditorPanel.annotationFor` that `.inTheEditor()` moves the badge to the corner

| | |
|---|---|
| **Disposition** | Fixed in round 130 (`63e74f85`): `inTheEditor`, `isBlank`, `paintBadge`, `paintStation`'s table, `equals` and `annotationFor` now say what the editor's icons are - white with their colour round them, on their rails, and the round orange sign for a square out of service. |
| **Grade** | C - documentation, in the code: round 129 brought behaviour.md 4b and the guides up to date (RSA63-C6, C7) and left these four, which a reader of the paint code meets first and which now say the opposite of what it does. |
| **Names** | RSA63-C6 (Closed; behaviour.md only), round 127 (the editor's icons white on their rails), round 128 (the out-of-service sign) |
| **Where** | `TileAnnotation.inTheEditor`'s javadoc `:652-662` (*"One thing turns on it: where a badge sits on a square whose track bends.  In the editor the badge moves out to the corner"*; what `editing` turns on since round 127 is the white fill, and the badge stays on its rails); `paintBadge` `:2114` (*"A STATION ON A BEND, IN THE EDITOR, GOES OFF THE RAILS"*); `paintStation`'s javadoc `:2230` (*"out of service  the orange X over the station's block in grey"* - `paintOutOfService` since round 128) and `:2233` (*"filled when named and white ringed in the colour when not, as before"* - not in the editor since round 127); `AutonomyEditorPanel.annotationFor` `:9816-9819` (*"Which moves a badge on a bend out to the corner ... the only one where the badge has to give way"*). |
| **Needs execution** | done - read against `paintStation` (`spills = known && bends`, no editing branch), `paintBadge`'s fill (`badge.isNamed() && !editing`), `paintOutOfService`, and the pictures in D8/D9. |

---

### RSA64-D1 - RSA63-C1 fixed: the power is one epoch, read once at dispatch and once at each timetable attempt; a STOP landing after the dispatch read, at RSA63-C1's own instant, between the check's two reads, or between an attempt's power wait and its read, is seen every time - the route given back, the train left standing, the attempt not counted

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RSA63-C1, RSA62-C2, RSA62-C3, RSA61-C1, GST-B1, S14-C6 |
| **Where** | `MarklinControlStation.setPowerState` `:4327` (the epoch moved on every change, then the state written, under the monitor, on the one message thread); `Layout.executePathInternal` `:9988` (one read), `:10159` (`!getPowerState() \|\| getPowerEpoch() != powerAtDispatch`); the entry thread `:7608`, `:7659`, `:7733` |
| **Needs execution** | done - **`core.rsa64Power`** (`w/out/d/P-power-clean.txt`): f1a to f1f each *"the train given its speed false"*, *"R64 f1x was not sent: the track power went off while its route was being set."* - f1b read at `executePathInternal:9988`, the GO at 1/8 switches; f1c (the increment taken in, the state written 5 ms later) the same; f1d read at `:10159`; f1e (off at the read, on 30 ms later) given back; f1f (a lasting STOP) given back, power still off.  **f2**: *"epoch reads at lines [7608, 7659, 7733, 7659, 7733, 7659, 7733] ... the leg set off true ... 'not sent ... power' 3 times, 'stayed blocked' 0 times"* - RSA63's same probe at `878bc6e7` ended *"Could not run R63 QK ... the path stayed blocked"*.  **By reading:** any change after the one read moves the epoch, and a power off throughout reads off at the check, so the parity tests `2bef7a3b` removed added nothing (and their removal makes the check indifferent to the simulation's start, which writes `powerState` without moving the epoch, `:5344`); the check runs at least `CONFIGURE_SLEEP` (150 ms) after the route's last switch (`:3858`), so a STOP in that time is seen; one after the check is the train's speed on a railway whose switches were all commanded with the power on, as before round 125.  **Claims:** `testThePowersEpochIsOddWhileThePowerIsOff`, `testAJourneyDispatchedAsThePowerWentOffIsGivenBack` green. |

---

### RSA64-D2 - The power paths round 129 touches are as they were: autonomy, Execute Timetable, Return Home and a hand send give a cut route back and go on; a STOP repeated or echoed moves the epoch once; Echo Sent Commands moves it, the bare simulation does not; and a leg the track refuses with the power on is still counted and ends the run

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RSA63-D1, RSA62-C2 to C4, OB-251, behaviour.md 8 |
| **Where** | `MarklinControlStation` `:2975-3005`; `Layout.waitWhileThePowerIsOff` `:2464`; the entry thread's `cut` `:7659`; every `getPowerState` reader (none changed) |
| **Needs execution** | done - **`core.rsa64Power`**: (g) *"STOP 1, its response 1, STOP again 1 (power off true), GO 2, GO's response 2, a 4-byte system frame 2, STOP (response only) 3; power now true, count 4"* - odd exactly while off; (a) given back, *"Track power is off - autonomy is waiting"*, then *"given its speed again within 8 s true"*; (b) set off within 10 s; (d) not given its speed within 1.5 s of the GO, sent on a later choice; (e) the hand door answering false with the log line; (c) three give-backs, *"the leg set off within 8 s true"*; (h) *"said 'stuck' 0 times, 'not sent ... power' 4 times"*, set off; (i) Echo: `stop()` 26 -> 27 power off, `go()` 28; without it 28 -> 28, power on; **(j)** the occupied leg: three *"path invalid: Edge is occupied"*, *"the path stayed blocked.  Stopping."*, *"'not sent ... power' 0 times"*.  **Claims:** `rsa64DispClaims` 50 of 50; `core.testHomeStaging` 115 of 115; `core.testControlStationFaults` 12 of 12. |

---

### RSA64-D3 - RSA63-C2 fixed: `deadEndSide` asks `stationRoad`, so a double-curve station whose track runs out on the contact's curve is a terminus facing that side at every orientation, drawn as the terminus is; where the track runs out on the other curve it stays a block

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RSA63-C2, MT-712, FR-118, behaviour.md 4b |
| **Where** | `AutonomySession.deadEndSide` `:11763`, `stationRoad` `:11873` (the only two callers of `deadEndSide` are the two badges; for every square but a double curve `stationRoad` is the first road, as before) |
| **Needs execution** | done - **`core.rsa64Names.rsa64DeadEndProbe`** (`w/out/d/D-deadend.txt`), 20 cases: the eight where the track runs out on the contact's curve (o0 E, S; o1 N, E; o2 N, W; o3 S, W) - *"badge dead end E, deadEndSide E; its track runs out on its own road at E; pixels differing from that road's terminus 0"*; the eight on the other curve and the four with nothing missing - dead end null, drawn as the block.  At `878bc6e7` RSA63 measured 225-248 px differing in the eight.  Claim `testADoubleCurveStationWhoseTrackRunsOutIsATerminus` green (it takes one side; the probe takes both). |

---

### RSA64-D4 - RSA63-C3 fixed, apart from C1: with Show Trains off the editor no longer marks the setup's train; the switch's refresh writes nothing to the setup, rebuilds no railway, keeps the findings list's choice, and with the setup and the railway agreeing repaints every square a train is drawn on

| | |
|---|---|
| **Disposition** | Checked - clean (but C1). |
| **Grade** | D |
| **Names** | RSA63-C3, FR-119, AR-13 |
| **Where** | `AutonomyEditorPanel.annotationFor` (`locomotiveAt(tile) != null && isShowingTrains()`), the listener `:911`, `refresh()`; the editor's `onChanged` (`LayoutEditor:1805`: the annotations and the main window's static layer - no save; the save is the track diagram's menu-only panel's, `TrainControlUI:5342`, which has no Show Trains) |
| **Needs execution** | done - **`ui.rsa64ShowTrains`** (C): *"the setup unchanged true, the running railway the same object true, the findings list's selected row 1 -> 1"*; (A) 4 of 4 train squares repainted each way.  **By reading:** `refresh()` is what the Track Lengths, Unmeasured Track and directions switches already run, so Show Trains now behaves as its neighbours do; it reads the findings (`check()`, cached per event since round 57) and writes nothing.  Claim `testShowTrainsOffHidesTheSetupsTrainMarkToo` green; it sets the box without firing it, so the switch's refresh is pinned by no claim - C1 is what that leaves. |

---

### RSA64-D5 - RSA63-C4 fixed: a square the editor shades spills nothing, and a neighbour's selection outline is drawn again over a spill - no ink of a shaded station on the squares round it, and none of a neighbour's outline lost; the track diagram, which never shades a station or outlines a square, is untouched

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RSA63-C4, OB-040, round 127 |
| **Where** | `TileAnnotation.spillsOverItsSquare` `:2424` (`!ignored`), `paintArrowsAgain` `:2498` (the outline as `paint` draws it, under the spill's clip and the square's own bounds, before the captions are drawn again); `LayoutLabel.setAutonomyAnnotation`/`repaint` (shading on: the old annotation spilled, its 3 x 3 repainted; shading off: the new one spills, its reach repainted) |
| **Needs execution** | done - **`ui.rsa64EditorSpill`** (`w/out/d/E-editorspill.txt`): *"outline orange 0 of 217"* at 30 and *"0 of 456"* at 60, length and trace 0 as before; everything shaded - *"the station's blue on its own square 0 px, on the squares round it 0 px"* at both sizes.  **The same probe at `878bc6e7`** (`w/out/p/`): 17 of 217, 34 of 456; 24 and 58 px.  **By reading:** the viewer's only `ignored` annotation is a link switched off, which has no badge (`staticAnnotationOf`), and the viewer never sets `selected`.  Claims `testAShadedStationSpillsNothing`, `testANeighboursOutlineIsDrawnOverASpill` green. |

---

### RSA64-D6 - RSA63-C5 fixed: a square out of service offers no hand pointer or click hint and its click changes nothing, saying why in the strip; the pointer and the click still ask one question, and links, crossings and run leaders answer as before

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RSA63-C5, FR-112, MT-697, behaviour.md 2 |
| **Where** | `AutonomyEditorPanel.clickChangesDirections` `:8020`, `isOutOfService` `:8049` (the Point's `active` false - what `setUsage`, `badgeFor` and the track diagram's `shut` read), `cycle` `:8067`; `autosetup.ui.infoOutOfServiceHasNoDirection` |
| **Needs execution** | done - **`core.rsa64Names.rsa64OutOfServiceProbe`** (`w/out/d/O-outofservice.txt`): in service *"clickChangesDirections true; direction before the click BOTH, after TOWARD_A ... 43 arrow pixels"*; out of service *"clickChangesDirections false; direction before the click TOWARD_A, after TOWARD_A ... 0 arrow pixels at 60; the hint after the click: ... Nothing can pass a square out of service, so it has no directions to set."*  **By reading:** a sensor is never a follower in a run (`walk` stops at it), so `leaderOf` of an out-of-service square is itself and no plain track is refused for a neighbour; a link answers first, as before; no keyboard door sets a direction.  The eight sentences carry the bundles' own words (German's *"kann nichts passieren"* is `autolayout.errorInactiveStation`'s); `core.testMessageBundles` 23 of 23.  Claims `testAClickChangesNoDirectionOnASquareOutOfService`, `regression.testTheEditorSaysWhatItsToolsDo` 10 of 10. |

---

### RSA64-D7 - RSA63-C6 and C7 fixed: behaviour.md 4b, Automation.md and AutomationAPI.md say what the icons and the Auto tab are now (but C2)

| | |
|---|---|
| **Disposition** | Checked - clean (but C2). |
| **Grade** | D |
| **Names** | RSA63-C6, RSA63-C7, MT-709, MT-712, FR-119, FR-120 |
| **Where** | behaviour.md 4b (`:905-934`); `Automation.md:96`, `:126`, `:449`; `AutomationAPI.md:539` |
| **Needs execution** | done - read against the code: the out-of-service sign (`paintOutOfService`), the editor's white icons (`badge.isNamed() && !editing`), the contact covered on a bend too (`paintStation`'s cover, `coverTheContact`), the spill and the outline drawn again over it, the double curve's terminus; the house beside the card's second line (`AutoLocomotiveStatus.markFor`, for an assigned home on an active Point); the name asked as a sensor becomes a station, a name another square has refused; Show Trains under Visible Elements (`LayoutEditor:1998`, `autosetup.ui.btnShowTrains`).  Every test named in 4b exists.  Not said in behaviour.md, and not graded: that the editor's click declines a square out of service (round 129) - 4b says only that the square has no arrows. |

---

### RSA64-D8 - No regression in any picture rounds 124-129 draw: from `878bc6e7` to `2bef7a3b` sixteen square types at every orientation, both sizes and both surfaces are identical; the gallery's twelve sheets are identical to RSA63's; every kind's spill on every curve is as RSA63 measured it

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RSA63-D3, RSA63-D4, RSA63-D8, round 124 to round 128 |
| **Where** | `TileAnnotation.paint`, `paintBadge`, `paintStation`, `paintArrows`, `paintArrivals`, `paintOutOfService`; `LayoutGrid.newDiagramContainer` |
| **Needs execution** | done - **`ui.rsa64Marks`** at both commits: *"identical 256 of 256"*; **`ui.rsa64Gallery`** against `rsa63/w/out/d/gallery`: *"identical 12 of 12"* (every kind on straight, curve and double curve, both surfaces, 30 and 60, at rest, with every arrow and arrival mark, occupied with a caption); **`ui.rsa64Spill`** (144 cases): its 149 lines equal RSA63's apart from timings - no spill ink outside its region, nothing in front of an opaque caption, out of service *"region NONE"*. |

---

### RSA64-D9 - Icon clarity, Adam's question: the one picture round 129 adds - a double-curve station's terminus - is the curve's terminus, flat against the side its track runs out on, on both surfaces at 30 and 60; nothing is drawn on the wrong square or under what it should be over, and nothing reads worse than RSA63-D9 already put to Adam

| | |
|---|---|
| **Disposition** | Checked - clean.  The look is Adam's to judge. |
| **Grade** | D |
| **Names** | RSA63-D9, RSA63-C2, MT-712, MT-710 |
| **Where** | `rsa64/w/out/d/gallery/DC-<diagram|editor>-<30|60>.png` (rows a curve and a double curve, columns the dead end at the contact road's first and second side, at rest, then with every arrow and arrival mark); `G-*.png` as RSA63's |
| **Needs execution** | done - **`ui.rsa64Gallery.dcTerminus`**: the terminus on the double curve is the same shape, place and spill as on the curve, its bar against the right side, the other curve's arrows along their own rails and its chevrons clear of the icon; with every arrow drawn the two arrowheads cover most of it, the block and terminus hard to tell apart at 30 - RSA63-D9's first observation, now on one more square, not a new one. |

---

### RSA64-D10 - What RSA63 did not reach: `DiagramExport`, and a second grid at another size sharing the registry's annotations - a 30 px grid of Adam's main page paints the same picture before and after an export at 60, and two exports are identical; the export draws the spills and the trains

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RSA63's method paragraph, RSA62-B1 (the region built once a size), RSA63-D2 |
| **Where** | `DiagramExport.render` (a grid at 60 in a plain panel, the diagram's own container inside it); `DiagramTileRegistry.register`; `TileAnnotation.spillShape` |
| **Needs execution** | done - **`ui.rsa64ShowTrains.exportBesideA30pxGrid`** (`w/out/d/T2-showtrains.txt`, `X-*.png`): *"a 30 px grid of 1 - Main: 384 squares, 22 annotated, 3 spilling; painted before and after an export at 60: pixels differing 0 of 395200; the export 1510x1030, exported twice: pixels differing 0"*; the crop round 1,10 (`X-export-60-1_10.png`) shows the curve station spilling and a standing train's locomotive.  Not reachable: the real railway, a 150 per cent display, a pop-up as a real window (Method). |

---

### RSA64-D11 - Race conditions round 129 introduces: none - the epoch is moved by one message thread under the monitor before the state is written, the readers need no ordering between their two reads, and the seen-once dispatch claim is green again

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RSA63-D11, S14-C6, RSA61 (`testARetiredRailwaysJourneyEndsWithItsRailway`) |
| **Where** | `MarklinControlStation.setPowerState` (`cs2-loc-messages`, a pool of one; the `AtomicInteger` moved before the volatile `powerState` is written, so a reader that sees the new state sees the new epoch); `Layout` `:10159` (either read's order is safe: a STOP landing between them moves the epoch the second read sees); `AutonomyEditorPanel` (Show Trains, the click, all on the event thread) |
| **Needs execution** | done - by reading and `core.rsa64Power` f1c, f1d (D1).  `testARetiredRailwaysJourneyEndsWithItsRailway` green (50 of 50); `ui.testTheGreyAppearsAtIdleToo` not met. |
