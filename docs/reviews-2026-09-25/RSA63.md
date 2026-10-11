# RSA63 - Validation of rounds 124-128 (`39f93225..878bc6e7`, at `878bc6e7`): RSA62's B1 and C1 are fixed - on Adam's main page at 60 px the whole page repaints in 6-7 ms where it took 0.9 s, every kind of spill is under the captions on both surfaces - and RSA62's C2-C4 are fixed at every door that runs a journey; no regression found in the arrows, the chevrons or the timetable and Return Home power paths; seven Cs: the power's cut count and state are read apart (C1); a double-curve station never gets its terminus shape (C2); Show Trains off leaves the setup's star (C3); the editor's spill pass ignores the editor's shading and outlines (C4); a square out of service still takes direction clicks (C5); behaviour.md 4b (C6) and the guides (C7) describe the old icons - CONVERGED (TrainControl 3.0.0)

**Status:** open

**Prefix:** RSA63

**Reviewed:** branch `route-stop-only` at `878bc6e7` (*Round 128: every square out of service is one round sign, clear of the sensor's activation arrows*), merged since into `autonomy-diagram-r0` (merge `a800960b`, tracker `c10f5bdb`; the coordinator reports the merge's code identical): `git log 39f93225..878bc6e7` - round 124 (`128aed37`), round 125 (`2d4d7799`), round 126 (`8389b266`), records (`e41aa7ff`), round 127 (`b2bedaef`) and round 128 (`878bc6e7`), the last two added to the scope while I worked.  Read from `git archive` of `878bc6e7` (`d/`), `b2bedaef` (`c/`), `e41aa7ff` (`a/`) and `39f93225` (`b/`, the code RSA62 reviewed at `6ee05601`: `git diff 6ee05601 39f93225 -- src test` is empty), `cs2_sample_layout` excluded, each with an empty `cs2_sample_layout` folder for one.sh's guard, all under `validate30/rsa63/`.  None carries `LocDB.data` or `UIState.data`; none was copied.  The tracker read at `79abdd2b` and, once the coordinator named it, `c10f5bdb` (RSA62 catalogued).  2026-10-10 22:05 to 2026-10-11 01:20.

## Method

**Read whole:** the brief and the coordinator's three additions (rounds 127 and 128 into scope; Adam's focus - *"Focus on ensuring no regression and good editor and track diagram viewer icon clarity"* - with his decisions: the editor's icons white with their colour round them, out of service a white circle with an orange ring and X, the must-turn icon as it is for now, a yellow fill for unnamed stations in the editor perhaps later); `validate30/RSA62.md` and its dispositions (`round86/RSA62_record.md`); the six commit messages and every hunk of `git show` of each over `src test docs/reference`.  **At the commit:** `Layout.executePathInternal` (`:9975-10170`: `cutsAtDispatch` `:9985`, the stop and power questions `:10140-10160`), `giveBackUnrun`, `waitWhileThePowerIsOff`, `executePath`/`executePathByHand`, `runLocomotive` (`:5618-5710`), `executeTimetableInternal` and its entry thread (`:7500-7740`: `cutsAtAttempt` `:7608`, `cut` `:7657`, the power wait `:7729`, the re-read `:7731`); `MarklinControlStation`'s GO/STOP branch (`:2975-3005`), `setPowerState` (`:4327`), `getPowerCuts`, `stop`/`go`, the single-thread message processor; `ViewListener.getPowerCuts`; every caller of `executePath` (autonomy, the timetable entry thread - Execute Timetable and Return Home - and `TrainControlUI.sendATrainByHand`; no route sends a train).  `TileAnnotation` whole for the station icons, the spill, `spillShape`/`spillRegion`/`spillFor`, `paintArrivals`/`cornerOf`/`offsetCollidesWithTrack`, `heading`, `paintBadge`, `paintOutOfService`, `coverTheContact`, `paint`'s order, `outOfService`; `TilePorts.contactRoad`; `AutonomySession.stationRoad`, `isCurveSquare`, `deadEndSide`, `firstRoute` and its other callers, `staticAnnotationOf`, `isPointNameTaken`, `whyNotAPointName`; `AutonomyEditorPanel.setUsage`, `askTheNewStationsName`, `badgeFor`, `annotationFor`, `isCurved`, `clickChangesDirections`, `cycle`, `isIgnored`, `isFocusedOnSignals`, `owner`, the station menu (`:1540-1600`), Show Trains; `LayoutGrid.newDiagramContainer` (the spill pass, the arrows and captions over it, the trains, the addresses), the grid's Show Trains hand-over and registration; `LayoutLabel.paintComponent`, `paintCoveredMark`, `paintTrainOverCaptions`, `repaint`, `setAutonomyAnnotation`, `setAutonomyOverlay`, `theTrainsRoadCentre`; `DiagramTileRegistry.register`/`annotate`/`publish`; `DiagramExport.render`; `LayoutPopupUI` (pop-up windows at their own size); `StationCaption.paintHtmlHalo`; `AutoLocomotiveStatus.updateState`/`markFor`; `LayoutEditor.setAutonomyMode`/`isAutonomyMode` and Show Trains' mount; the keyboard shortcuts; behaviour.md 4b and 8; Automation.md and AutomationAPI.md (RSA62's unreached documentation).  Each finding was searched for in `findings.tsv` at `79abdd2b` and `c10f5bdb` before it was written: none is there.

**Runs.**  Every JVM through the archives' own `docs/tools/one.sh`, `TC_SCRATCH` a Windows-form folder per job under `rsa63/w/scratch`, the JVM's temporary folder `rsa63/w/tmp`, started by `rsa63/w/runjob.sh` (RSA62's runner, re-pointed: it waits until no `java.exe`, `javaw.exe` or `javac.exe` and no battery lock have been seen in two checks 20 s apart, never kills anything) one job at a time from `w/q1.sh` and `w/q2.sh`.  **Held** from the coordinator's first message at 22:21 (received before any JVM of mine had started) through its notices of rounds 127 and 128 and battery 80, until its "the machine is free" at about 00:53; no JVM, javac included, of mine ran before 00:54:13.  My jobs ran 00:54:13 to 01:09:39.  Model probes headless (`init(null, simulate = true, ...)`); picture probes with this machine's display (125 per cent).  No Central Station was reached; nothing hung, ran out of memory or tripped the layout guard; one.sh never refused a run.

**Scratch classes** (`rsa63/w/probes/`, copied into the archives' `test/`; nothing asserted; output in `w/out/<a|b|d>/`):

- **`core.rsa63Power`** (`d`) - eight edges with a switch on each, Path Integrity Validation off, the power moved only as the Central Station moves it - the model's own `setPowerState` (which counts the cut) or `receiveMessage` (the real message thread) - never by writing the field, which RSA62's probe did and which the count cannot see; the model's log tapped: (g) STOPs, responses and a short frame through `receiveMessage`; (a) autonomy, a cut that lasts; (b) Execute Timetable, the same; (d) autonomy, a cut begun and ended inside the route's setting (RSA62-C2's own case); (e) a hand send, the same; (c) a sequential timetable (Return Home's shape), three 3 s cuts each 300 ms into the leg's route (RSA62-C3's case); (h) a parallel timetable, `TIMETABLE_STUCK_MS` 3 s, four attempts each cut and restored inside the route's setting; (i) Echo Sent Commands; (f1) a STOP landing between `executePathInternal`'s two reads of the power, forced with a `ViewListener` proxy that lets it in between them (`:9985`), GO 300 ms later; (f2, method `f2Only`) a STOP landing between an attempt's power wait and its count read (`:7608`, `:7731`), GO 600 ms later, three times in a sequential run.
- **`core.rsa63DispClaims`** - `core.testATrainIsDispatchedOnce` at the commit, unchanged but for one line making 24 locomotives by name after `init`.
- **`ui.rsa63Spill`** - nine squares in the diagram's container, every kind of station (named, unnamed, grey berth, terminus at either end, may-turn, must-turn named and unnamed, out of service) on each of the four curves at 30 and 60, on the track diagram and in the editor: the spill's ink against the region `spillShape` names, any pixel in front of opaque captions over all eight squares round it, the neighbours' arrows against no station; the first paint's cost (region built), a later one, one after a paint at the other size.
- **`ui.rsa63Cost`** - Adam's frozen main page (`test/layouts/live-snapshot`, `1 - Main`) at 30 and 60: the same three costs for the curve stations at 1,10, 0,11 and 13,9, the whole page and the 3 x 3 repaint round 1,10.
- **`ui.rsa63Look`** - RSA62's `rsa62Look`, re-pointed, every page of the frozen railway, and a second run at 60 px alone (`-Drsa63.sizes=60`), which gives the 60 px grid its caption text (built second, it had none - RSA61's and RSA62's unreached 60 px picture).
- **`ui.rsa63Marks`** (`b`, `a`, `d`) - sixteen square types at every orientation at 30 and 60, on both surfaces, every road one way and, on a sensor, a named station on its badge road with an arrival mark at every side - the `curved` flag and the badge road as each commit's session gives them - written as raw pictures and compared afterwards (`w/out/<b|a|d>/marks-*`).
- **`core.rsa63Names`** - `core.testAutonomyDiagramSession` at the commit plus three methods: `rsa63NamingProbe` (FR-120 beyond its claim), `rsa63DeadEndProbe` (a double-curve station at four orientations, a sensor missing beyond each side in turn), `rsa63OutOfServiceProbe` (the editor's click on a square out of service).
- **`core.rsa63Monitor`** - `core.testAutonomyDiagramMonitor` at the commit plus `rsa63OutOfServiceEveryRoad`: the round 128 claim's measures on both straights and all four curves, both surfaces.
- **`ui.rsa63Trains`**, **`ui.rsa63EditorSpill`** - Show Trains against the setup's star; the editor's spill against its shading and its own marks on the squares round it.
- **`ui.rsa63Gallery`** - for Adam's icon-clarity question: twelve contact sheets (`w/out/d/gallery/G-<diagram|editor>-<30|60>-<rest|arrows|occupied>.png`, halves in `gallery/crops/`), rows a straight sensor, a curved one, a double curve, columns the eleven kinds (named, unnamed, grey, terminus, may-turn, must-turn, out of service, plain, plain may-turn, plain must-turn, plain out of service), each cell three squares by three in the diagram's container with the real tile art (the occupied art in the third state) and plain track beside it; "arrows" draws every arrow and an arrival mark open and barred; "occupied" stands a train there and puts a caption beside.

**Ran, at `878bc6e7`:** the probes above; `core.rsa63DispClaims` **48 of 48** (round 125's `testAJourneyIsNotSetGoingIfThePowerWentOffAndOnWhileItsRouteWasSet` and `testPowerCutsDoNotEndAReturnHomeLeg`, round 123's, GST-B1's and the seen-once `testARetiredRailwaysJourneyEndsWithItsRailway` among them); `core.testMessageBundles` 23 of 23; `core.testAutonomyDiagramMonitor` **61 of 61** (rounds 124, 127 and 128's claims among them); `core.testAutonomyDiagramSession` **215 of 215** (rounds 124 and 126's); `core.testHomeStaging` 115 of 115; `regression.testEditorSurfaceRules` 68 of 68; `core.testControlStationFaults` 12 of 12; `ui.testRenderingCost` 8 of 8; `ui.testDiagramLooksRight` 49 of 51, run twice, the two red each time `testARealPathToACurvedStationIsDrawn` and `testEveryPixelOfARunLandsOnTrack` with *"Index: 0, Size: 0"* - no locomotive in the archive's database, as RSA60, RSA61 and RSA62 found (its rounds 124 and 126 claims green).  `ui.testTheGreyAppearsAtIdleToo` (seen once) not met.  The coordinator reports battery 80 on this commit green apart from the four main-checkout classes; I did not see its results.

**Not reached:** real detection and the real railway; a display at 150 per cent (this machine is at 125; Java 8 draws the diagram at its own pixels whatever the scale, RSA62-D6); B1 in Adam's own window (measured in the same container and paint code, offscreen); a pop-up window and the main window at two sizes at once in a real window (measured as the shared annotation painted at 30 and 60 in turn, D2); `DiagramExport` (read: it builds a grid at 60, which takes the shared annotations at registration and paints on the event thread - the same paint and the same cache, D2, D11).

**Nothing written** outside `validate30/rsa63/` and this file, apart from what the runner makes (the isolated preference node and data folder per run, which one.sh removes).  Nothing of mine called `saveState`.  **The real files:** `C:/Users/adamo`'s `LocDB.data` (`6ef569398873b0de`) and `UIState.data` (`9e32894e3bcfe28a`) and the main checkout's (`dc812c1a271f4b5a`, `2619d589fcfa2345`) unchanged byte for byte and time stamp from 22:09 to 01:10 (`rsa63/w/real-hashes-before.txt`, `-after.txt`).  The main checkout touched only through `git -C <main checkout> archive / log / show / diff / blame / worktree list / for-each-ref` and to hash and stat the two files; `tc-30`, `r34fix` and `bat66` neither read nor run.  **Running:** no JVM, runner, queue or loop of mine is left (01:10: no `java.exe`, `javaw.exe` or `javac.exe` on the machine, no battery lock).  Nothing was refused by the permission system.

**Counts:** 0 A, 0 B, 7 C, 11 D.

**This round has CONVERGED: no A and no B.**  **What the rounds got right** (D1-D11): a power cut while a route is set - lasting, or begun and ended inside it - gives the route back unrun at autonomy, Execute Timetable, Return Home and a hand send, says so in its own log line, and is counted against neither a sequential run's attempts nor a parallel run's clock (D1); the spill's region is built once a size from the icon's body - on Adam's main page at 60 px the whole page paints in 6-7 ms and the 3 x 3 round 1,10 in 0.3-0.4 ms, where RSA62 measured 904 ms and 692 ms, and a shared annotation painted at the other size rebuilds in 0.1-0.7 ms (D2); every kind's spill on every curve at both sizes, on both surfaces, lies inside its region and under the captions, and at 60 px with real caption text Adam's main page matches its ideal (D3); the double curve's badge is on the contact's curve at every orientation, its other curve's arrows lie along their rails, and no other square's arrows or chevrons changed but the single curves' at 30 px, now exactly along the chord (D4); the HTML halo and the house (D5); Show Trains apart from C3 (D6); FR-120's one door (D7); rounds 127 and 128 apart from C4 and C5 (D8); the icons as drawn, for Adam's clarity question (D9); what RSA62 did not reach (D10); no new race but C1's (D11).  **What remains, all C:** the cut count and the power state are two reads, so a STOP landing between them is missed - shown with the reads forced apart: a train given its speed over a route set while the power was off, and three such cuts ending Return Home "blocked" (C1); a double-curve station's terminus shape is worked out on the other curve, so it is never drawn (C2); Show Trains off leaves each placed train's star (C3); the editor's spill draws a curve station's ends on the squares round it while the editor shades everything for a signal pick, and over a neighbour's outline (C4); a square out of service shows no arrows but its click still changes them (C5); behaviour.md 4b (C6) and the guides (C7) describe what the icons and the Auto tab no longer are.

---

### RSA63-C1 - New in round 125: the cut count and the power state are read one after the other, not together, so a STOP landing between the two reads is missed - at dispatch the train is then given its speed over a route set while the power was off (RSA62-C2's hazard), and in a timetable the give-back is counted as a refusal (RSA62-C3's)

| | |
|---|---|
| **Disposition** | Fixed in round 129 (`1c124337`), by the validator's direction in one read: the power's epoch (`getPowerEpoch`) moves at every cut and every return and is odd while the power is off, read once at dispatch, once the route is set, and once at each timetable attempt - `core.testATrainIsDispatchedOnce.testThePowersEpochIsOddWhileThePowerIsOff`, `testAJourneyDispatchedAsThePowerWentOffIsGivenBack` (red first). |
| **Grade** | C - the window is a few instructions wide at dispatch (the two volatile reads, against `setPowerState`'s increment-then-write), about a millisecond before a timetable entry's first attempt (the main loop's power wait, a log line and a thread start), and a few instructions before each later one; and the cut must then end before the route is set.  Improbable, but it is exactly the hazard round 125 closes: switches commanded on a railway with no power to move them, the train then driven over them. |
| **Names** | RSA62-C2, RSA62-C3, RSA61-C1, GST-B1, S14-C6 (the power flag read without the monitor) |
| **Where** | `Layout.executePathInternal` `:9985`: `cutsAtDispatch = this.control.getPowerState() ? this.control.getPowerCuts() : -1` - the state first, then the count; `MarklinControlStation.setPowerState` `:4332`: the count incremented, then `powerState` written.  A reader that reads the state before the write and the count after the increment holds the cut in `cutsAtDispatch` with the power "on"; if GO comes before the route is set, the check at `:10155` (`!getPowerState() \|\| getPowerCuts() != cutsAtDispatch`) passes.  `executeTimetableInternal`'s entry thread: `cutsAtAttempt` read at `:7608` (thread start) and `:7731` (after the power wait at `:7729`); a STOP landing after the wait's last read of the power and before the count read is in `cutsAtAttempt`, the dispatch reads the power off (`-1`) and gives the route back, and if GO has come by the give-back `cut` (`:7657`) is false - an attempt, and the start of a parallel run's clock. |
| **Needs execution** | done - **`core.rsa63Power` (f1)**: with the STOP let in between the dispatch's two reads (at `executePathInternal:9985`) and GO 300 ms later, *"the train given its speed true; speed 30, underway true, active true ... switches thrown 8/8"*, nothing in the log about the power; the control (STOP 150 ms into the route, GO 300 ms later) *"given its speed false"*, *"R63 QG was not sent: the track power went off while its route was being set."*  **(f2, `f2Only`)**: a STOP between each of three attempts' power wait and count read (*"count reads at lines [7608, 7657, 7731, 7657, 7731, 7657]"*), GO 600 ms later: three *"not sent: the track power went off"*, then *"Could not run R63 QK from QK0 to QK8 - the path stayed blocked.  Stopping. ..."* - the end round 125 removed.  **One way to settle it:** read the count first and the state second (`int c = getPowerCuts(); cutsAtDispatch = getPowerState() ? c : -1`), and read `cutsAtAttempt` before the power wait (or have `executePath` say that a false was the power's give-back, which the loop then asks instead of the count). |

---

### RSA63-C2 - New in round 124: a double-curve station's badge is on the curve with the contact, but its terminus side is still worked out on the square's first road - the other curve - so a double-curve station whose track runs out is never drawn as a terminus, on the track diagram or in the editor

| | |
|---|---|
| **Disposition** | Fixed in round 129 (`1c124337`): `deadEndSide` asks `stationRoad` - `core.testAutonomyDiagramSession.testADoubleCurveStationWhoseTrackRunsOutIsATerminus` (red first). |
| **Grade** | C - a picture that is wrong (Adam: a station with one way out is a terminus, drawn flat against that side - behaviour.md 4b), on a rare square: the frozen railway's three double-curve sensors are all on pages left out of autonomy.  Before round 124 the badge and the dead end both used the first road and agreed. |
| **Names** | MT-712, FR-118 (the terminus shape), behaviour.md 4b (*"A terminus is a station whose track runs out on one side"*) |
| **Where** | `AutonomySession.deadEndSide` `:11763` asks `firstRoute(tile)` (`:11767`, its javadoc: *"The road is the badge's, `firstRoute`"*); the badge's road is `stationRoad` since round 124 (`:11711`; the editor's `badgeFor` `:9992`), on a double curve the second road (`TilePorts.contactRoad`).  `TileAnnotation.paintStation` keeps a dead end only on the badge's own road (`deadEnd = ... badge.getDeadEnd() == badge.getA() \|\| == badge.getB()`), so a dead end on the other curve is dropped, and one on the contact's curve is never found.  `deadEndSide` is asked only for the two badges (`:11725`, `AutonomyEditorPanel:10014`). |
| **Needs execution** | done - **`core.rsa63Names.rsa63DeadEndProbe`** (`w/out/d/D-deadend.txt`): a named double-curve station at 2,2, turned 0 to 3, with a sensor beyond each side but one.  At every orientation, where the track runs out on the contact's curve the badge's dead end is null - *"o0 no sensor beyond E: ... badge road E-S, badge dead end null, deadEndSide null; its track runs out on its own road at E; pixels differing from that road's terminus 245"* (225-248 at 60 px in the eight such cases) - and where it runs out on the other curve the badge carries that side, which the icon ignores (*"o0 no sensor beyond N: ... badge dead end N"*, drawn as a block).  **One way to settle it:** `deadEndSide` asks `stationRoad`. |

---

### RSA63-C3 - New in round 126: Show Trains off hides each train's icon and tail in the editor but leaves the star the editor's own annotation draws where the setup places a train - hidden under the icon while the trains show, it appears when they are switched off

| | |
|---|---|
| **Disposition** | Fixed in round 129 (`1c124337`): the setup's train is marked only while Show Trains is on, and the switch refreshes the annotations - `core.testAutonomyDiagramSession.testShowTrainsOffHidesTheSetupsTrainMarkToo` (red first). |
| **Grade** | C - the switch says it *"Draws the trains on this editor's diagram"* (`autosetup.ui.tooltipShowTrains`) and Adam asked that it *"toggles the locomotive icons"*: the icons go, but a train mark the user never sees while the trains are shown comes up on every occupied station when they are hidden.  Whether the star should go with them is his to say; it reads as a train left behind. |
| **Names** | FR-119, AR-13 (the star), MT-099/MT-124 (the star over the badge), OB-317 (the icon where a train stands) |
| **Where** | `AutonomyEditorPanel.annotationFor` `:9813`: `if (locomotiveAt(tile) != null) annotation.withTrain()`, whatever `showTrains` says; `TileAnnotation.paint` `:1049` and `paintBadgeOverRun` `:2010` draw `paintTrainMark` for `occupied`.  Show Trains gates only `LayoutLabel.paintCoveredMark` (`:1897`) and `paintTrainOverCaptions` (`:2226`); its listener only repaints, so the editor's annotations are not rebuilt either. |
| **Needs execution** | done - **`ui.rsa63Trains`** (`w/out/d/T-trains.txt`, pictures `T-on-star-*`, `T-off-star-*`): a named station square with a parked train and the editor's `withTrain` annotation - Show Trains on, the star adds *"0"* pixels (under the icon); off, *"pixels the star adds 65"* at 30 and *"267"* at 60 (`z-trains-shaded-marks-60.png`, second panel: the white six-armed star on the station).  The editor's annotation is built with `withTrain` for a placed train by reading (`:9813`).  **One way to settle it:** `annotationFor` asks `isShowingTrains()` before `withTrain`, and the switch refreshes the annotations. |

---

### RSA63-C4 - New in round 127: the editor's spill pass does not know what the editor draws round it - while the editor shades every square that is not a signal (picking a protecting signal, OB-040) a curve station's icon is not drawn on its square but its two ends are drawn on the squares beside it, and a neighbour's outline is drawn under the end of a spill where on its own square it is over the icon

| | |
|---|---|
| **Disposition** | Fixed in round 129 (`1c124337`): a shaded square spills nothing, and a neighbour's outline is drawn again over a spill - `core.testAutonomyDiagramMonitor.testAShadedStationSpillsNothing`, `ui.testDiagramLooksRight.testANeighboursOutlineIsDrawnOverASpill` (red first). |
| **Grade** | C - pictures that are wrong in the editor only: fragments of an icon with no icon, in the one gesture OB-040 de-clutters (*"de-clutter the diagram as much as possible so users can clearly see the signals"*); and a broken outline (the editor's `selected` outline - a finding's or Mass Assign's square) where a spill reaches it.  The track diagram never shades a station and draws no outlines. |
| **Names** | round 127 (*"Let's make all of the icons white with a blue border in the editor"*), OB-040, RSA61-C2 (the spill's depth), round 119 |
| **Where** | `TileAnnotation.spillsOverItsSquare` `:2424` asks the badge only, not `ignored`; `paint` returns after the shading (`:927`, no badge), but `LayoutGrid.newDiagramContainer`'s spill pass (`:887`) calls `paintStationSpill` for every spilling square; `AutonomyEditorPanel.annotationFor` gives a shaded square its badge (`:9794`, `badgeFor(tile), shaded`).  On the squares round a spill the container draws again only their arrows and arrival marks (`paintArrowsAgain`) and the captions; a neighbour's length, trace and outline, which its own paint draws over its badge, are not - the outline is the one the spill reaches. |
| **Needs execution** | done - **`ui.rsa63EditorSpill`** (`w/out/d/E-editorspill.txt`, `E-shaded-*.png`, `E-marks-*.png`): (B) everything shaded, a named curve station in the middle - *"the station's blue on its own square 0 px, on the squares round it 24 px"* at 30 and *"58 px"* at 60 (none with no station); (A) the neighbours' own marks - *"length red 0 of 216 px covered, trace yellow 0 of 642, outline orange 34 of 456"* at 60, 17 of 217 at 30.  `z-trains-shaded-marks-60.png`, fourth and fifth panels.  **One way to settle it:** no spill from a shaded annotation (`spillsOverItsSquare` asks `!ignored`), and the outline drawn again over a spill as the arrows are, or left to be broken. |

---

### RSA63-C5 - New in round 127: a square out of service draws no arrows, but in the editor its click still changes its directions - the hand pointer and *"Click to change allowed directions"* are still offered, the click cycles the hidden setting, and the square looks the same afterwards

| | |
|---|---|
| **Disposition** | Fixed in round 129 (`1c124337`): no direction click on a square out of service; the click says why - `core.testAutonomyDiagramSession.testAClickChangesNoDirectionOnASquareOutOfService` (red first). |
| **Grade** | C - the message strip says what the click set (`autosetup.ui.cycledTo`), so it is not silent, but the click changes something nothing shows, and the next time the square is put back in service its arrows are not the ones last seen.  FR-112's rule is that the pointer and the click agree about what a click does; round 127 made what it does invisible there. |
| **Names** | round 127 (*"For ones out of service, should arrows be drawn at all, since no trains can pass?"*), FR-112, MT-697 |
| **Where** | `AutonomyEditorPanel.clickChangesDirections` `:8022` (what `LayoutEditor.showWhatAClickDoes` asks) and `tileClicked`/`cycle` ask nothing about the square being out of service (`isIgnored` is excluded pages, blanks, lamps and transparent squares); `TileAnnotation.paintArrows` `:1352` and `paintArrivals` `:1207` return for `outOfService()`. |
| **Needs execution** | done - **`core.rsa63Names.rsa63OutOfServiceProbe`** (`w/out/d/O-outofservice.txt`): the editor over `pageWithATwoEndedStation`, its middle sensor - in service *"clickChangesDirections true; direction before the click BOTH, after TOWARD_A; ... draws 43 arrow pixels"*; out of service *"clickChangesDirections true; direction before the click TOWARD_A, after TOWARD_B; the editor's annotation (impassable badge true) draws 0 arrow pixels"*.  **One way to settle it:** `clickChangesDirections` and `tileClicked` decline a square out of service (the strip saying why), or the editor draws its arrows faintly - Adam's to choose. |

---

### RSA63-C6 - behaviour.md 4b describes the icons as they were: an out-of-service station as *"the orange X over a grey block"* (round 128 made it a white circle, orange ring and X, for every kind and a plain sensor), the editor's curve station *"off the rails, clear of the direction arrows"* (round 127 put it on its rails, white, spilling), *"filled when the station is named"* (not in the editor since round 127), and a curve's icon *"moved in off the corner until all of it is inside its square, smaller"* - the spill (round 119) is in no line of behaviour.md

| | |
|---|---|
| **Disposition** | Fixed in round 129 (`1c124337`): behaviour.md 4b says what the icons are now. |
| **Grade** | C - documentation.  The brief's rule: where the code and behaviour.md disagree, that is a finding either way.  Rounds 127 and 128 add three disagreements; the curve sentence (written 2026-10-09, `65aa84b3`) has been stale since round 119.  Nor does 4b say that a square out of service draws no arrows or arrival marks and no wash, that a double curve's station is drawn on the contact's curve (MT-712), or that a plain sensor's turn mark covers its contact. |
| **Names** | FR-118, MT-710, MT-712, round 119, round 127, round 128, OB-167 |
| **Where** | `docs/reference/behaviour.md` 4b, the table row `:905` and the paragraphs `:907-925` (*"Blue where autonomy uses the station ... filled when the station is named, white ringed in the colour when it is not"*, *"A passing point ... keeps its small mark"*, *"On a curve the icon lies along the line between the curve's two ends, moved in off the corner ... in the editor a station on a curve sits off the rails"*). |
| **Needs execution** | done - read against `TileAnnotation.paintBadge`, `paintStation`, `paintOutOfService` and `spillsOverItsSquare` at the commit, and the pictures in D9. |

---

### RSA63-C7 - What RSA62 did not reach, the documentation outside `docs/reference`: the guide and the API guide still say a train at its home is shown in teal (MT-709 made it a house); the guide's first steps say to make a sensor a station and then name it (FR-120 asks the name as it becomes one); and its list of Visible Elements has no Show Trains

| | |
|---|---|
| **Disposition** | Fixed in round 129 (`1c124337`): Automation.md and AutomationAPI.md - the house, the name asked as a sensor becomes a station, Show Trains. |
| **Grade** | C - documentation and wording (Adam reviews wording himself). |
| **Names** | MT-709, FR-119, FR-120 |
| **Where** | `Automation.md:449` (*"a train standing at its own home is shown in teal"*); `AutomationAPI.md:539` (*"shows its location highlighted in teal"*); `Automation.md:96` (*"choose `Station (...)` → `Yes - Trains Can Stop Here`, and name it with `Rename...`"*); `Automation.md:126` (*"`Visible Elements` - `Grid` ..., `Addresses` ..., `Track Lengths` ... and `Unmeasured Track`"*).  `Readme.md:532` is the 2.8.0 changelog and stands. |
| **Needs execution** | done - read. |

---

### RSA63-D1 - Round 125, RSA62-C2, C3 and C4, apart from C1: a cut while a route is set - lasting, or begun and ended inside it - is seen at autonomy, Execute Timetable, Return Home and a hand send; the train stays on its start with no speed, the log says why in its own words, the run waits and goes on; a power give-back counts against neither a sequential run's attempts nor a parallel run's clock; a STOP repeated or echoed is one cut

| | |
|---|---|
| **Disposition** | Checked - clean (but C1). |
| **Grade** | D |
| **Names** | RSA62-C2, RSA62-C3, RSA62-C4, RSA61-C1, GST-B1, OB-251 |
| **Where** | `Layout.executePathInternal` `:9985`, `:10140-10160`; `giveBackUnrun`; the entry thread `:7608-7731`; `MarklinControlStation` `:2975-3005`, `:4327`; `autolayout.log.notSentPowerOff` (eight bundles); behaviour.md 8 |
| **Needs execution** | done - **`core.rsa63Power`** (`w/out/d/P-power.txt`): (g) through `receiveMessage` *"count from 0: STOP 1, its response 1, STOP again 1 (power off true), GO 1, GO's response 1, a 4-byte system frame 1, STOP (response only) 2"*; (a) autonomy, cut 3.5 s - *"speed 0, underway false, active false, at start true"*, *"R63 QA was not sent: the track power went off while its route was being set."*, *"Track power is off - autonomy is waiting"*, then *"given its speed again within 8 s true"*; (b) Execute Timetable - *"entry's execution time 0, timetable running true"*, *"set off within 10 s true"*; (d) **RSA62-C2's case**, off with 1/8 switches thrown and on with 4/8 - *"the train given its speed within 1.5 s of the GO false"*, given back, then sent again on the next choice with its route set afresh; (e) a hand send, the same - *"executePathByHand ... answering false"* (the door's "check log"), the log line; (c) **RSA62-C3's case**, three 3 s cuts - three give-backs, *"after the third cut, power on: the leg set off within 8 s true"*, no "path stayed blocked"; (h) a parallel run with `TIMETABLE_STUCK_MS` 3 s and four cut attempts - *"said 'stuck' 0 times, 'not sent ... power' 4 times"*, set off; (i) Echo Sent Commands - `stop()` counts one cut, `go()` none; without it the simulation's power never moves (behaviour.md 8).  **By reading:** the count is moved only by the message thread (a single-thread executor), on to off, so a STOP the Central Station repeats or echoes is one cut; no ordinary run moves it, so no route is given back on Adam's railway that was not before; a cut before dispatch with the power back by then sets the route with the power on, as it should; the only `executePath` callers are autonomy, the timetable entry thread (Execute Timetable and Return Home) and the hand send - no route sends a train; `getPowerCuts` is an `AtomicInteger`, read on the journey's own thread; the eight new sentences read well (*"... while its route was being set"* also covers a power already off at dispatch, where it is a little loose).  **Claims:** `rsa63DispClaims` 48 of 48. |

---

### RSA63-D2 - Round 124, RSA62-B1: the spill's region is built once a size, from the icon's body - on Adam's main page at 60 px the whole page paints in 6-7 ms and the 3 x 3 round 1,10 in 0.3-0.4 ms (RSA62: 904 ms and 692 ms); a shared annotation painted at the other size - a pop-up window or an export - rebuilds in 0.1-0.7 ms

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RSA62-B1, rounds 51, 55, 57, 58 (the speed rules), round 119 |
| **Where** | `TileAnnotation.paintStation` (the region under `spills && spillFor` differs), `spillShape`; `LayoutLabel.setAutonomyAnnotation` (an equal annotation keeps the old instance and its region); `DiagramTileRegistry.register`/`annotate` (one instance on every window's copy of a square); `LayoutPopupUI` (its own size); `DiagramExport.render` (a grid at 60, on the event thread) |
| **Needs execution** | done - **`ui.rsa63Cost`** (`w/out/d/C-cost.txt`), medians: at 60, 1,10's first paint (region built) **154 us**, a later paint 64, a paint after one at 30 **135**; 0,11 149/62/142; 13,9 132/62/142; the whole page **7,523 us**, the 3 x 3 round 1,10 **421 us**.  At 30: 759/312/687, 365/190/332, 276/113/273; page 8,709, 3 x 3 829.  **`ui.rsa63Look`**: *1 - Main @60* page 6,119 us, round 1,10 293, 0,11 251, 13,9 232, no spill near 114; @30 page 6,747.  **`ui.rsa63Spill` (4)**: a named block on N-E at 60, 102 us first, 37 later, 103 after a paint at 30.  **By reading:** every input to the region - the badge, the marks, `editing` - is final or set by a builder before the annotation is first painted, and a change makes a new annotation, so the size is the only key needed; the region is written and read on the event thread only (an export paints there too).  The claim `testASpillsRegionIsBuiltOnceAndQuickly` times `spillShape` after a paint - the cached read - so its "quickly" is not what it measures; the numbers above are. |

---

### RSA63-D3 - Round 124, RSA62-C1, and the spill as a whole on both surfaces: every kind on every curve at 30 and 60 draws no ink outside its region, nothing of it stands in front of a caption over any of the eight squares round it, the neighbours' arrows are as they are with no station, and at 60 px with real caption text Adam's main page matches its ideal

| | |
|---|---|
| **Disposition** | Checked - clean (but C4). |
| **Grade** | D |
| **Names** | RSA62-C1, RSA61-C2, round 120, OB-117, OB-159 |
| **Where** | `TileAnnotation.paintStation`, `spillShape`, `spillsOverItsSquare` `:2424`; `LayoutGrid.newDiagramContainer` `:880-960`; `LayoutLabel.repaint`, `paintStationSpill`, `spillShapeOnTheDiagram` |
| **Needs execution** | done - **`ui.rsa63Spill`** (`w/out/d/S-spill.txt`, 144 cases): *"WORST: pixels in front of an opaque caption 5; spill ink outside the region 0"* - the 1-5 any-colour pixels are, by the ink measure (nothing above alpha 20 outside the region), faint edge pixels; RSA62 measured 44 and 64 at 60 for a block and a terminus; out of service draws no spill (*"region NONE"*, round 128); the neighbours' arrow pixels equal the no-station count in 140 cases and are 1-3 more in the four terminus cases at 60 (arrows drawn twice inside the region, antialiased).  **`ui.rsa63Look`, 60 px alone** (`w/out/d60/look-live-snapshot/`): 13,9 under its *"—"* pill *"10 px differ by more than 8, 0 by more than 40, most 29"*, 1,10 and 0,11 *"0 px"*; at 30 13,9 *"5 px ..., most 28"* - the translucent pill laid twice in the region's 1 px margin, as RSA62-D4.  **By reading:** every caption kind - the pill, the placeholder, on end, ringed - is a `StationCaption` drawn again inside the region; trains and addresses come after; a station's change repaints its reach (`LayoutLabel.repaint`), and a neighbour's own repaint redraws the spill over it. |

---

### RSA63-D4 - Round 124, MT-712 and MT-714: a double-curve station's badge is on the contact's curve at every orientation, the other curve's arrows lie along its rails, two chevrons in one corner stand apart; no straight, switch, crossing, permanent turnout, signal or buffer changed, and single curves changed only at 30 px, where an arrow is now aimed exactly along the chord

| | |
|---|---|
| **Disposition** | Checked - clean (but C2). |
| **Grade** | D |
| **Names** | MT-712, MT-714, OB-175, TD-4 |
| **Where** | `TilePorts.contactRoad`, `AutonomySession.stationRoad`, `isCurveSquare`, the editor's `isCurved` and `badgeFor`; `TileAnnotation.heading`, `offsetCollidesWithTrack`, `cornerOf`, `paintArrivals`; `LayoutLabel.theTrainsRoadCentre` |
| **Needs execution** | done - **`ui.rsa63Marks`** at `39f93225`, `e41aa7ff` and `878bc6e7` (16 types x 4 orientations x 2 sizes x 2 surfaces): from before round 124 to after it, STRAIGHT, FEEDBACK, every switch, crossing, permanent turnout, SIGNAL and END identical on both surfaces; DOUBLE_CURVE (diagram) and FEEDBACK_DOUBLE_CURVE changed at every orientation, as intended; CURVE and FEEDBACK_CURVE changed only at 30 px, orientations 0 and 2 (`z-curve-arrows-pre-post.png`): `trackCentre`'s integer middle (22,22) aimed the arrow 41° where the chord is 45°, and `curved` now aims it from the other end of the chord - so the commit's *"on a single curve the angle is the same either way"* is true at 60 and nearly so at 30.  From `e41aa7ff` to `878bc6e7` the diagram is identical and the editor's sensor squares changed (round 127's icons).  **Claims:** `testADoubleCurveStationIsDrawnOnTheCurveWithTheContact` (the contact read off the art at four orientations), `testADoubleCurvesArrowsLieAlongBothItsCurves`, `testADoubleCurvesArrivalArrowsKeepOffItsArrowsAndEachOther` green.  **By reading:** on straights and single curves no two arrivals share a corner, so `along` is the quarter it was and the new collision rule answers as the old one; arrival marks are drawn on sensors only; a train standing on a double curve is drawn on the road its facing names (`trainSides`), the contact's when it names none - the one-Point double curve as Adam ruled. |

---

### RSA63-D5 - Round 124, MT-716 and MT-709: the halo is drawn round writing set as HTML, line by line, and a train at its home shows a house beside its second line, with the (i) where both

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | MT-716, FR-116, MT-709 |
| **Where** | `StationCaption.paintHtmlHalo`; `LayoutGrid` (`"<html><br>" + text`); `AutoLocomotiveStatus.updateState`, `markFor`, `BothMarks` |
| **Needs execution** | done - by reading and the claims (`testWritingThatSharesItsSquareHasAHaloToo`, `testATrainAtHomeShowsAHouseNotTeal` green).  The halo takes each document line from its first letter, at the view's own position for it, in the label's font - the font the HTML view draws in; the grid's HTML is one `<br>` and the words with `&nbsp;`, so no line wraps; at 125 per cent Java 8 draws as at 100.  The house is shown where the teal was - a train at its assigned home on an active Point, in every branch (running, waiting, standing), the (i) beside it where the card has a reason to hover for. |

---

### RSA63-D6 - Round 126, FR-119, apart from C3: Show Trains hides the trains' icons and tails in the autonomy editor only, on by default and remembered; a page or mode switch hands the switch to the new squares; the track diagram, its pop-up windows and the track editor are untouched

| | |
|---|---|
| **Disposition** | Checked - clean (but C3). |
| **Grade** | D |
| **Names** | FR-119 |
| **Where** | `AutonomyEditorPanel` (`showTrains`, `PREF_TRAINS`, `isShowingTrains`); `LayoutGrid` (the supplier for `autonomyEditor` only, given at every build); `LayoutLabel.trainsShown`; `LayoutEditor` (the mount) |
| **Needs execution** | done - by reading and **`ui.rsa63Trains`** (*"Show Trains ON ... the icon's own pixels 1204"* at 60; off, none of the icon).  `isAutonomyMode` is `autonomyPanel != null && visible`, so the grid's check never meets a missing panel, and every refresh after a new panel builds the squares again with its switch; no train can run while an editor is open (the gate), so no running train is hidden.  Claims `testTheEditorsShowTrainsSwitchHidesItsTrains`, `testTheEditorHasAShowTrainsSwitch` green. |

---

### RSA63-D7 - Round 126, FR-120: the station menu's Yes - the one door that makes a square a station, in the editor and on the track diagram's own menu - asks an unnamed sensor its name on the event thread, over the right window; Cancel or a blank leaves it as it was; a name `whyNotAPointName` refuses or another square has, in any case, is asked again with what was typed; a named square is not asked; the editor's Cancel takes the name back with the station

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | FR-120, TDR-C11, OB-019 |
| **Where** | `AutonomyEditorPanel.setUsage` `:4579`, `askTheNewStationsName` `:4631`, `owner`/`dialogOwner` (`TrainControlUI:5364-5368`); `AutonomySession.isPointNameTaken`, `setStation` (no other caller makes a station: `importLegacy` names before it marks, a configuration read is no door); `LayoutEditor.undoAutonomyEdits` |
| **Needs execution** | done - **`core.rsa63Names.rsa63NamingProbe`** (`w/out/d/N-names.txt`): *"'Yard (eastbound)' then 'Yard': asked 2 times"*, the second with the heading refusal; *"'NORTH YARD' then Cancel: asked 2 times; c station false named null"*; *"demoted and promoted again: asked 0 times"*; the editor's Cancel (`restoreSetup`) - *"c station false named [null]"*.  Two things for Adam, not graded: quotes are taken out silently (*"'\"Q\" Yard' ... named [Q Yard]"*) where Rename says so (`warnQuotesStrippedFromName`); and the check compares trimmed names without case, so *"North  Yard"*, two spaces, is not a duplicate of *"North Yard"*.  No shortcut makes a station (Control+N places a caption), the borrowed menu is refused during a run as before, and the modal question blocks every window meanwhile.  Claim `testAnUnnamedSensorIsNamedAsItBecomesAStation` green. |

---

### RSA63-D8 - Rounds 127 and 128, apart from C4 and C5: in the editor every icon is white with its colour round it and, on a curve, on its rails, spilling and repainting as on the track diagram; a square out of service is one round sign on both surfaces, drawn over the contact and clear of the occupied sensor's red arrows on every road at both sizes, with no arrows, arrival marks, wash or spill; a plain sensor's turn mark covers the contact

| | |
|---|---|
| **Disposition** | Checked - clean (but C4, C5). |
| **Grade** | D |
| **Names** | round 127, round 128, OB-167, FR-118 |
| **Where** | `TileAnnotation.paintBadge`, `paintStation`, `paintOutOfService` `:2737`, `coverTheContact`, `outOfService`, `paint` (no wash out of service), `spillsOverItsSquare`; `LayoutGrid.newDiagramContainer`; `LayoutLabel.repaint` |
| **Needs execution** | done - **`core.rsa63Monitor.rsa63OutOfServiceEveryRoad`** (`w/out/d/R-ring.txt`): W-E, N-S and the four curves, 30 and 60, diagram and editor - the ring 14 and 28 across, *"activation red ... covered 0; contact left showing 0"* in all 24 cases (the claim takes W-E and E-S).  **`ui.rsa63Spill`, editor**: the same region, captions and arrows as the diagram (D3).  **Claims:** `core.testAutonomyDiagramMonitor` 61 of 61 (`testASquareOutOfServiceIsAnOrangeRingedCircle`, `testASquareOutOfServiceHasNoArrows`, `testTheEditorsIconsAreWhiteWithTheirColourRound`, `testTheEditorsIconOnACurveIsOnItsRails`, `testAPlainTurnMarkCoversTheContact`, `testTheSpillIsOnlyWhatFallsOutsideTheSquare`).  **By reading:** the editor's annotations are its own (`inTheEditor`; the registry never annotates an edit-mode square), so its regions are never shared with the diagram's; the editor's container is the diagram's, so its spill gets the same pass; a station put out of service repaints the reach of the spill it had (`setAutonomyAnnotation` asks the old annotation).  The out-of-service sign hides the station's kind - Adam's decision, *"regardless of the type of station"*. |

---

### RSA63-D9 - Icon clarity, Adam's question, on both surfaces at 30 and 60 px: rendered, every kind on straight, curve and double curve, at rest, with every arrow and arrival mark, and occupied with a train and a caption - nothing is drawn on the wrong square or under what it should be over (but C3, C4); what reads least well is listed below, for him

| | |
|---|---|
| **Disposition** | Checked - clean.  The observations below are not graded: they are the look, his to judge. |
| **Grade** | D |
| **Names** | round 127, round 128, MT-710 (*"We need to render red arrows on top of stations"*), OB-317 (the train where it stands) |
| **Where** | `rsa63/w/out/d/gallery/G-<diagram|editor>-<30|60>-<rest|arrows|occupied>.png`, halves in `gallery/crops/` (`-A` the six station kinds, `-B` out of service and the plain sensors); closer crops `crops/z-*.png` |
| **Needs execution** | done - **`ui.rsa63Gallery`**.  Read well: the out-of-service sign on both surfaces and every road (it reads at 30 as at 60, and the occupied sensor's red arrows show round it); the editor's white icons under arrows at 60 (`G-editor-60-arrows-A`) - the arrows read clearly, as asked; a station's icon on its curve, spilling; the double curve's two curves each with its own arrows and chevrons.  **Read least well:** (1) on a curve, with every arrow drawn (Display Allowed Directions, or the editor's Show All), the two arrowheads sit on the icon's two ends and cover most of it - the block, terminus and hexagons are hard to tell apart there at 60 and not at all at 30 (`G-diagram-60-arrows-A`, rows 2-3; `G-editor-30-arrows-A`); with restrictions only, as both surfaces open, one red arrow covers one end; (2) the plain sensor's diamond and square on a curve at 30 under the same two arrowheads show only an edge of blue (`G-diagram-30-arrows-B`); (3) a standing train covers its station's icon entirely - its kind, and the out-of-service sign of a station it stands on, do not show while it is there (`G-*-occupied`); (4) in the editor a grey berth at 30 is a light grey 1.5-pixel outline on white, the faintest mark on the sheets (`crops/z-ed30-rest-curve-0to3.png`, third); (5) in the editor a named and an unnamed station are the same picture (decided; a yellow fill offered later); (6) the must-turn pair at 30 is the busiest mark (decided: kept for now); (7) with every arrow drawn, a square out of service, unwashed, shows its track solid black between washed neighbours (`G-diagram-30-arrows-B`, first and last columns); (8) on a curve the plain turn marks stand square to the page while the track runs at 45°, the contact's cover showing as two black stubs at their corners (`crops/z-diag30-plainmust-curve.png`). |

---

### RSA63-D10 - What RSA62 did not reach: the 60 px picture with Adam's real captions (D3: it matches its ideal); the documentation outside `docs/reference` (C7); B1 measured again (D2); the rest is out of reach here

| | |
|---|---|
| **Disposition** | Checked - clean (but C7). |
| **Grade** | D |
| **Names** | RSA62's method paragraph, RSA61-D5 |
| **Where** | `ui.rsa63Look` at 60 alone; `Automation.md`, `AutomationAPI.md`, `Readme.md` |
| **Needs execution** | done - the 60 px grid built first carries its caption text, which a second build did not (RSA61's and RSA62's *"the probe's second grid of each page carried no caption text"* - a probe's artefact, not the product's).  Not reachable: the real railway, a 150 per cent display, Adam's own window (Method). |

---

### RSA63-D11 - Race conditions the rounds introduce: none but C1 - the cut count is moved by one message thread and read atomically, the spill region is written and read on the event thread, Show Trains is read where the squares paint

| | |
|---|---|
| **Disposition** | Checked - clean (but C1). |
| **Grade** | D |
| **Names** | RSA62-D7, S14-C6 |
| **Where** | `MarklinControlStation.setPowerState`, `getPowerCuts`; `TileAnnotation.spillRegion`/`spillFor`; `DiagramExport.render`; `LayoutLabel.trainsShown` |
| **Needs execution** | done - by reading.  `setPowerState` runs on `cs2-loc-messages`, a pool of one, so GO and STOP are applied in order; the count is an `AtomicInteger`.  `spillRegion` and `spillFor` are two fields, but every writer (`paintStation`) and reader (`spillShape`) is on the event thread, the export's paint included (`invokeAndWait`), and a pop-up at another size rebuilds rather than reads a torn pair.  FR-120's question and Show Trains' switch are on the event thread.  `testARetiredRailwaysJourneyEndsWithItsRailway` green again (48 of 48). |
