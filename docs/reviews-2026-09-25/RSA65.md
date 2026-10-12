# RSA65 - Validation of rounds 131-136 (`b4786389..e7ea52f8`, code at `98c1072f`) and of the documentation at `adf3f4e4`: FR-121's allow-only list is asked through `Point.excludes` at every door and is a wall on a plain sensor, a locomotive added later included; OB-332's route button carries its direction into the built railway; the highlight shows through repeated changes and on a route clicked from the diagram (OB-335 not reproduced); the Medium size and the square-at-a-time scroll are right on every surface tried - but a station copy built as no destination (a barred arrival) counts as a non-station for the exclusion rule, so a station's list walls its excluded trains out of passing that way, and an allow-only list walls out every other train (B1); an old autonomy.json gap-filled into an allow-only square with an empty list is read the other way round (C1); the Readme says Bulk Tools can clear every train length (C2) - NOT CONVERGED (TrainControl 3.0.0)

**Status:** open

**Prefix:** RSA65

**Reviewed:** branch `autonomy-diagram-r0` at `e7ea52f8` (*Tracker: MT-726 validated on Adam's Works; his note answered by round 136; MT-727*), the code being `route-stop-only`'s `98c1072f`: `git log --first-parent b4786389..e7ea52f8` - round 131 (`74f6b921`), the exclusion docs (`514071ee`), round 132 (`28e4e138`), round 133 (`f68aabbc`, `3e7a0f56`), round 134 (`d28e2de1`), round 135 (`af2bf807`, Adam's `cd6f43d5`), round 136 (`98c1072f`, `0136099d`), the documentation (`03a69d09`, `7d7502af`) and the tracker.  Read from `git archive e7ea52f8` (`rsa65/d/`) and, for a control, `b4786389` (`rsa65/p/`), `cs2_sample_layout` excluded, each with an empty `cs2_sample_layout` folder for one.sh's guard; neither carries `LocDB.data` or `UIState.data`, and none was copied.  **Scope item 5, on the coordinator's update:** Readme.md, Automation.md and AutomationAPI.md read at `adf3f4e4` (`git show`, into `rsa65/w/docs-adf/`), after Adam's `4d4c87e7` (the changelog a sentence a change) and `adf3f4e4` (the Readme pass); `git diff e7ea52f8 adf3f4e4` touches no `src` or `test`.  2026-10-11 17:05 to 17:50.

## Method

**Read whole:** the brief; `validate30/RSA64.md`; every hunk of `git diff b4786389 e7ea52f8` over `src` (1,015 lines), the bundles' (400), `test` (1,572) and `docs/reference`, `Readme.md`, `Automation.md`, `AutomationAPI.md` (1,189); `git diff e7ea52f8 adf3f4e4 -- Automation.md` and the whole 3.0.0 changelog and rewritten sections of the Readme at `adf3f4e4`.  **At the commit:** `Point.excludes`, `isAllowOnlyListed`, `toJSON` (`:1426-1455`); every reader of an exclusion list - `Layout.isPathClear` (`:3135`), `hasAutonomousDestination` (`:5564`), `pickPath` (`:5927`), the two why-not reasons (`:6424`, `:6665`), the destination enumerator (`:6573`), `parseAuto`'s list and mode (`:14492-14533`), `locDeleted` (`:1181`); `HomeStaging` `:810`, `canEnter` (`:1876`, the rule at `:1944`), the home sentences (`:2251`), `canRestOnSquare` (`:2640`), `homeBrokenByExcluding` (no production caller); `AutoLocomotiveStatus.markFor` (`:170`); `AutonomyBuilder`'s copies (`stops = point.isStation() && arrivalAllowed(node)`, `:1113`) and its extras pass-through (`:1229-1272`); `AutonomySession` - `POINT_OPERATIONAL_KEYS`, the capture (`:6440`, `:6573`), `importLegacy`'s gap-fill (`:1932-1985`), `setPointProperty`, `isRunTile`, `canCarryDirection`, `setBarredArrivals`; `AutonomyCompanionStore.repairLocomotiveInPoints` (rename and delete sweep); `AutonomyEditorPanel` - the Advanced menu (`:1862`), `promptLocomotives` (`:6101-6219`), `homeBrokenBy` (both), `allowsOnlyListed`, `promptHome`/`mayRestHere`, `isIgnored` (`:10228`), `clickChangesDirections`, `cycle`, `tileClicked`; `TilePorts.isTransparent` (route tiles only); `LayoutLabel.setImageOnEDT` (`:997-1135`), `endAccessoryHighlight`, `flashHighlight`, `discardFlash`, `updateImage`, the route tile's click (`:398-735`), `MarklinAccessory.setSwitched`/`updateTiles`/`parseMessage`, `MarklinRoute.heldReason`; `LayoutDiagramComponent.getImage`/`getImageName`/`getImageKey`/`artSize`; `TrainControlUI` - `layoutSizes`, `MEDIUM_TILE_SIZE`, the size list's model (`:1063`), `mediumButtonActionPerformed`, the main window's repaint (`:33760-33870`), `restoreLayoutTitles`/`saveLayoutTitles`, `openLayoutEditor`; `LayoutPopupUI.drawGrid`; `LayoutGrid.scrollASquareAtATime`; `LayoutEditor`'s minimum size (`:6065`); `HandScrollListener`; `DiagramExport.render` (always built at 60, then scaled); `TileAnnotation.paintBadge` (classic branch, `:2089-2330`), `classicBox`, `paintOutOfService`, `spillsOverItsSquare`; behaviour.md 1, 4b; the eight bundles' new keys.  Each finding was searched for in the archive's `docs/manual-tests/findings.tsv` (5,581 lines): none is there (RLA3-B1, which C1 bears on, is Closed and is said apart).

**Runs.**  Every JVM through the archive's own `docs/tools/one.sh`, `TC_SCRATCH` a Windows-form folder per job under `rsa65/w/scratch`, the JVM's temporary folder `rsa65/w/tmp`, started by `rsa65/w/runjob.sh` (RSA64's runner re-pointed: it waits until no java.exe/javaw.exe whose command line names testng, TestNG, batteryRun, anyReceivePort, one-files or battery-files, no javac.exe and no battery lock have been seen in two checks 20 s apart - Adam's own TrainControl is not waited for - polls once a minute and never kills anything), one job at a time.  No other test JVM was seen at any start; none of mine overlapped another; one.sh never refused.  Model probes headless (`init(null, simulate = true, ...)`); window and picture probes with this machine's display (125 per cent).  No Central Station was reached; nothing hung, ran out of memory or tripped the layout guard.  Jobs ran 17:26:52 to 17:40:06.

**Scratch classes** (`rsa65/w/probes/`, copied into the archives' `test/`; nothing asserted; output in `rsa65/w/out/d/`):

- **`core.rsa65Excl`** - lines built by the setup's own builder (`AutonomySession` -> `buildConfiguration` -> `parseAuto`): Alpha - Beta - Gamma - Delta, the ends must-turn, two locomotives made by name.  (A) passage east through a station Beta, by list mode and with Beta's arrival from W barred or not: the two-edge path Alpha > Beta's copy > Gamma asked of `isPathClear` and `getPossiblePaths` for a listed and an unlisted train; (B) a plain sensor Beta allow-only, and a locomotive made after the build; (C) the JSON round trip - the mode absent, `blacklist`, `Whitelist`, `whitelist`, no list, an unknown name - each parsed and written back, a rename, and a capture; (D) an old autonomy.json gap-filled into a configuration whose Beta is allow-only with an empty list, and the same file into an unmarked Beta as the control.
- **`core.rsa65Live`** - Adam's frozen railway (`test/layouts/live-snapshot`, the locomotives its lists name made by name): every station copy built as no destination that carries a list, and every path through it.
- **`core.rsa65Route`** - OB-332: sensor - ROUTE - sensor, and sensor - straight - ROUTE - straight - sensor, a second button beside the line; the click cycle on the button through `AutonomyEditorPanel.tileClicked`, after each click the stored direction, the BUILT railway's edges between the sensors either side, the editor's grey and click answer, and the track diagram's marks.
- **`core.rsa65ExclClaims`** - `core.testLocomotiveExclusions` at the commit, unchanged but for one line making three locomotives by name after `init`.
- **`ui.rsa65Gallery`** - RSA64's gallery re-pointed to 30, 46 and 60 (the 46 art scaled from the 60 set as `getImage` scales it): every station kind, plain sensors and out of service, on straight, curve and double curve, both surfaces, at rest, with every arrow and arrival mark, and occupied with a caption; and the terminus sheets.
- **`ui.rsa65Medium`** - a real main window over the frozen railway (`1 - Main`, its four trains made by name, three units long): (M) the diagram at Small, Medium, Large, Medium, Small, Medium, the wheel with and without Shift, an arrow step, Show Addresses on and off; (P) the Medium pop-up; (E) the track diagram editor and the autonomy editor at 30, 46 and 60; (H) a route made in the model for a route tile on the page, commanding two switches the page draws, run by a left click on the tile at Medium; and one switch changed three times 400 ms apart, the yellow sampled every 40 ms.  Run twice (the second with the editors at three sizes and the yellow's on/off times).

**Ran, at `e7ea52f8`:** the probes above; `core.testAutonomyDiagramSession` **221 of 221** (round 133's `testAnAllowOnlyModeGoesIntoTheRailwayAndComesBack`, round 132's `testARouteButtonOnALineTakesADirectionClick`); `core.testAutonomyDiagramMonitor` **67 of 67** (round 131's five classic claims); `core.rsa65ExclClaims` **8 of 8** (round 133's four allow-only claims); `regression.testHomeAssignmentRules` 6 of 6; `core.testLayoutTiles` **12 of 12** (round 134's `testASecondHighlightIsDrawnYellow`); `ui.testDiagramLooksRight` **55 of 57** (round 135's and 136's four - Medium between Small and Large, the scaled art, the Medium pop-up, both scroll claims - green), the two red `testARealPathToACurvedStationIsDrawn` and `testEveryPixelOfARunLandsOnTrack` with *"Index: 0, Size: 0"* - no locomotive in the archive's database, as RSA60 to RSA64 found; `core.testLocomotiveExclusions` 0 of 8 for the same reason (hence the copy); `core.testAutoLayout` 40 of 41, the red `testAnAnsweredRailIsCoveredWhereTheRoadsPart` (*"Feedback 5 does not exist"*, not touched since RSA64) red alone too and **red alone at `b4786389`** - the sample layout the archive leaves out; `core.testMessageBundles` 23 of 23; `regression.testEditorSurfaceRules` 68 of 68; `core.testHomeStaging` 115 of 115; `regression.testTheEditorSaysWhatItsToolsDo` 10 of 10.  `ui.testTheGreyAppearsAtIdleToo` (seen once) not met; `testARetiredRailwaysJourneyEndsWithItsRailway` not in my scope.

**Not reached:** the real railway and real detection; a display at 150 per cent; the editor's exclusions dialog as a real modal (read, and its rule asked through `homeBrokenBy` and the claims); a locomotive deleted through the UI's door with an allow-only list (read: `TrainControlUI` `:23337` calls `Layout.locDeleted`, the store sweeps the name; the headless probe's `deleteLoc` has no view and so does not reach `locDeleted` - a probe artifact, not a finding); OB-335 through a real Central Station's echo (the simulation is mute; reached with a route the model runs).

**Nothing written** outside `validate30/rsa65/` and this file, apart from what the runner makes (the isolated preference node and data folder per run, which one.sh removes).  Nothing of mine called `saveState`.  **The real files:** `C:/Users/adamo`'s `LocDB.data` (`6ef569398873b0de`) and `UIState.data` (`9e32894e3bcfe28a`) and the main checkout's (`9a85023a6883ee81`, `2619d589fcfa2345`) unchanged byte for byte and time stamp from 17:08 to 17:43 (`rsa65/w/real-hashes-before.txt`, `-after.txt`).  The main checkout touched only through `git -C <main checkout> archive / log / show / diff` and to hash and stat the two files; `tc-30`, `r34fix` and `bat66` neither read nor run.  **Running:** no JVM, runner, queue, monitor or loop of mine is left (17:43: no java.exe, javaw.exe or javac.exe on the machine, no battery lock).  Nothing was refused by the permission system.

**Counts:** 0 A, 1 B, 2 C, 10 D.

**This round has NOT CONVERGED: one B.**  **What rounds 131-136 got right** (D1-D10): FR-121 is asked through one question at every door, a plain sensor's allow-only list is a wall to every train it does not name, one added later included, and the mode goes into the railway and back (D1); a route button on a line takes direction clicks whose every state reaches the built railway, and nothing off a line became clickable (D2); a square changed three times inside its hold is yellow throughout and ends on its current picture, and a route clicked from the diagram highlights its switches - OB-335 not reproduced (D3); the Medium size is right on the main window, through the page cache, in the pop-up and in both editors (D4); the diagram steps a square at a time at each size, with the wheel, Shift and the arrow buttons (D5); the classic icons read the same at 30, 46 and 60 on both surfaces and nothing spills (D6); the documentation at `adf3f4e4` keeps Adam's three rules (D7); the claims of every class rounds 131-136 touch are green (D8); no new race (D9); the archive's two environmental reds (D10).  **What remains:** a station's barred-arrival copy is built as no destination, and the exclusion rule takes "no destination" for "not a station", so a station's list stops the trains it keeps out from passing it that way - and an allow-only list stops every other train (B1, older in its blacklist form, widened by round 133, and contradicting the guides `514071ee` wrote); an old file's exclusion list gap-filled into an allow-only square whose list is empty is read as the only locomotives allowed (C1, new in round 133); and the Readme at `adf3f4e4` says Bulk Tools can clear every train length, which no item does (C2, new in `4d4c87e7`).

---

### RSA65-B1 - A station whose arrival from one side is barred is built with a copy that is no destination, and `isPathClear` and Return Home's `canEnter` take "no destination" for "not a station": so a station's exclusion list stops the trains it keeps out from driving through it that way - and since round 133 an allow-only list stops every train it does not name, a locomotive added later included, where both guides say an excluded train may still pass through a station

| | |
|---|---|
| **Disposition** | Fixed in round 138 (`d7a7d80d`), as the finding suggests: `Layout.isAStationSquare` asks whether any copy of the square is a destination, and `Layout.isPathClear` and `HomeStaging.canEnter` both ask it - `core.testLocomotiveExclusions.testABarredSideDoesNotTurnAStationsListIntoAWall` (built from this finding's probe: a blacklist and an allow-only list on a station barred from the west, both halves, and a plain sensor's wall as the control), seen red first; mutations ZA-ZD red. |
| **Grade** | B - a user meets wrong behaviour: routes through the station in the barred direction vanish for every train the list keeps out (with an allow-only list, every train but the few named), trains sit with "nowhere to go", and Why not Moving? says *"The intermediate point ... excludes this locomotive"* of a station.  It refuses more, never less, so nothing is put at risk; it contradicts Adam's rule confirmed on 11 October (*"if a locomotive is excluded from a STATION, it can still pass through on a route that heads somewhere else"*) and the code's own comment at `isPathClear`.  Not reached on Adam's frozen railway today (below). |
| **Names** | FR-121 (round 133), `514071ee` (the exclusion docs), RV-D1 (the tier rule), the 45 per cent note at `isPathClear`, barred arrivals (the station's `Trains May Arrive...`) |
| **Where** | `AutonomyBuilder` `:1113` - *"A station, unless a train arriving THIS way is not allowed to stop"*: `stops = point.isStation() && arrivalAllowed(node)`, written as `station`; every copy also carries the square's `excludedLocs` and `excludedLocsMode` (the extras pass-through, `:1229-1272`).  `Layout.isPathClear` `:3135`: `if (!e.getStart().isDestination() && e.getStart().excludes(loc))` refuses, under the comment *"Non-stations only, and deliberately: a station's exclusion list says the locomotive may not STOP there"*.  `HomeStaging.canEnter` `:1944`: `return p.isDestination() \|\| !p.excludes(loc);` - the same rule for Return Home and staging.  The guides: `Automation.md:184` (*"trains that may not stop here, though they can still pass through it"*), `AutomationAPI.md:509` (*"They can still pass through it on the way somewhere else, and Return Home routes them through it too"*), behaviour.md 1. |
| **Inputs** | A through station with an arrival side barred (one direction stops, the other only passes) and a locomotive list on it - a blacklist naming a train, or an allow-only list. |
| **Needs execution** | done - **`core.rsa65Excl`** (`w/out/d/E-excl.txt`), lines built by the builder, the path Alpha > Beta's copy > Gamma eastbound: Beta not barred, blacklist [Y] - *"R65 Y ... isPathClear true"*; **Beta barred from W, blacklist [Y]** - the copy passed *"destination false"*, *"R65 Y through Beta to Gamma: isPathClear false (The intermediate point RA3Beta excludes this locomotive); getPossiblePaths offers Gamma or Delta false"*; Beta not barred, allow-only [X] - Y passes; **Beta barred from W, allow-only [X]** - *"R65 Y ... isPathClear false (The intermediate point RA5Beta excludes this locomotive); getPossiblePaths offers Gamma or Delta false"*, X passes.  The controls with no list pass both ways.  **On Adam's frozen railway** (**`core.rsa65Live`**, `L-live.txt`): BottomInner bars arrivals from W and keeps six locomotives out; its eastbound copy is built as no destination with that list - *"BottomInner (eastbound): destination false, list [MF 5028 DSB, SP45-204, ER 2035 DSB, MY 1150 DSB, MZ 1425 DSB, EA 3005 DSB]"* - but has no way onward (*"paths through it: 0"*), so it bites nothing there today; BottomCrossover, a plain sensor with a list, refuses as the rule says.  **New or older:** the blacklist form is as old as barred arrivals; round 133 makes it every train but the named few.  **One way to settle it:** ask whether the copy's SQUARE is a station - another copy of the same square a destination (`isSamePlaceAs`), or the builder writing the square's station-ness beside `station` - rather than `isDestination()`, in both places. |

---

### RSA65-C1 - New in round 133: an old autonomy.json imported into a configuration gap-fills `excludedLocs` where the square has none, without asking the square's mode - so a square set to allow only the chosen locomotives with none chosen (every train kept out) takes the old file's exclusion list and reads it as the only locomotives allowed: the trains the file kept out become the only ones let in

| | |
|---|---|
| **Disposition** | Fixed in round 138 (`d7a7d80d`): `AutonomySession.importLegacy` leaves the list of a square set to allow only the chosen locomotives - `core.testLocomotiveExclusions.testAnOldFileDoesNotFillAnAllowOnlySquaresList` (the control: a square with no mode still takes the file's list), seen red first; mutation ZE red. |
| **Grade** | C - at rest, on a rare state (allow-only with an empty list) met by an import into that configuration; the result is the file's meaning inverted on one square, shown on the square's menu (`Allowed Locomotives Only (1)`).  Adam's ruling of 1 October caps an at-rest setup corner at C. |
| **Names** | FR-121, RLA3-B1 (Closed: a default stored as nothing takes the file's value), RLA4-C8 (an exclusion list emptied counts as not set), behaviour.md's import paragraph (*"an emptied exclusion list counts as not set and takes the file's value"*) |
| **Where** | `AutonomySession.importLegacy` `:1932-1985`: `if (!point.has(key) \|\| extras.has(key)) continue;` over `CARRIED_SETTINGS` (`"excludedLocs"` among them) - the square's `excludedLocsMode` is not asked, and since round 133 "no list" with `whitelist` is not a default but "no locomotive allowed". |
| **Needs execution** | done - **`core.rsa65Excl.legacyImportGapFill`** (`E-excl.txt` part D): before *"excludedLocs null, mode whitelist"*; the file excludes Y at the square; after *"excludedLocs ["R65 Y"], mode whitelist"*, built *"excludes X true, excludes Y false"*; the control into an unmarked square *"excludes X false, excludes Y true"*.  **One way to settle it:** treat a square whose mode is `whitelist` as having its list set, or carry the file's list only with the mode cleared. |

---

### RSA65-C2 - The Readme at `adf3f4e4` (`4d4c87e7`'s one-sentence changelog) says Bulk Tools *"can walk you through every missing track, station and train length, or clear them all"*; there is no item that clears train lengths - Bulk Tools clears every track length and every station's longest train

| | |
|---|---|
| **Disposition** | Fixed in the Readme (`a5be719f`): Bulk Tools "can walk you through every missing track, station and train length, and clear every track length or station's longest train at once". |
| **Grade** | C - a statement a user would act on (looking for a clear-all for train lengths) that the program does not do.  The sentence at `e7ea52f8` was right (*"It can also clear every track length, or every station's longest train, at once"*); the cut to one sentence widened it. |
| **Names** | Adam's *"Keep each update to a sentence, two max"* (`4d4c87e7`), SET-D5 (the clear items) |
| **Where** | `Readme.md:411` at `adf3f4e4`; the English bundle's clear items: `autolayout.ui.menuClearAllTrackLengths`, `menuClearAllMaxTrainLengths`, `menuClearAllHomeLocomotives`, `menuClearLocomotives` (placements) - none for a train's length. |
| **Needs execution** | read. |

---

### RSA65-D1 - FR-121: every rule that reads a locomotive list asks `Point.excludes`; a plain sensor's allow-only list is a wall to every train it does not name, one made after the railway was built included; the mode is written, read, captured and kept through a rename; the editor names the item by its mode and asks about the home in the mode chosen (but B1, C1)

| | |
|---|---|
| **Disposition** | Checked - clean (but B1, C1). |
| **Grade** | D |
| **Names** | FR-121, OB-022, MT-246, TD-8 |
| **Where** | the eleven callers listed in Method (`Layout` six, `HomeStaging` four, `AutoLocomotiveStatus` one) - no list is read elsewhere for a decision (`homeBrokenByExcluding` has no production caller); `parseAuto` `:14529`; `Point.toJSON` `:1450`; `POINT_OPERATIONAL_KEYS`; `promptLocomotives`, `homeBrokenBy(home, chosen, allowOnly)`, `allowsOnlyListed`; `repairLocomotiveInPoints` |
| **Needs execution** | done - **`core.rsa65Excl`** (B): *"plain sensor, whitelist [X] ... R65 Y ... isPathClear false (The intermediate point RB1Beta excludes this locomotive)"*, X clear; *"a locomotive added to the database after the build: excluded by the plain sensor true"*; an empty allow-only list refuses both.  (C): absent and `blacklist` read as exclusions, `whitelist` as allow-only, written back only when allow-only; no list or an unknown name with `whitelist` keeps every train out; *"rename W -> W2: excludes W2 false, written ["R65 W2","R65 X"]"*; a capture keeps the mode.  `Whitelist` with a capital is read as a blacklist - the API names the exact value, so not graded.  **Claims:** `rsa65ExclClaims` 8 of 8, `testAnAllowOnlyModeGoesIntoTheRailwayAndComesBack`, `testHomeAssignmentRules` 6 of 6, `core.testHomeStaging` 115 of 115.  **By reading:** the dialog's mode and list are two `setPointProperty` writes in one gesture, rebuilt once (`setupChanged`); Cancel restores the whole setup snapshot; the editor cannot open during a run. |

---

### RSA65-D2 - OB-332: a route button on a running line is not greyed, its click cycles both ways, one way each way and closed, and each state reaches the BUILT railway's edges between the sensors either side; a button beside the line stays greyed and its click changes nothing; only route tiles are transparent

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | OB-332, OB-321, `TileGraph.transparentRoutes` |
| **Where** | `AutonomyEditorPanel.isIgnored` `:10228`; `TilePorts.isTransparent` (ROUTE only); `AutonomySession.isRunTile` (a button is a run of its own); `cycle`, `clickChangesDirections` |
| **Needs execution** | done - **`core.rsa65Route`** (`R-route.txt`): sensor - ROUTE - sensor, *"after 1 click(s): direction TOWARD_A; built edges main:3,1 -> main:5,1: 1, back: 0"*, *"after 2 ... TOWARD_B; ... 0, back: 1"*, *"after 3 ... NONE; ... 0, back: 0"*, the track diagram's mark following each (*"[E-W:TOWARD_B ]"*); the same through track either side of the button; *"beside: greyed true"*, its click changing nothing.  On the frozen main page the off-line buttons are still drawn greyed in the autonomy editor (`E-autonomy-editor-46.png`).  Automation.md `:156` says so.  Claim `testARouteButtonOnALineTakesADirectionClick` green. |

---

### RSA65-D3 - Round 134: a switch changed three times inside the hold is yellow from the first change until 2.25 s after the last and ends on its current picture with no highlight armed; a route clicked on its tile on the diagram highlights both switches it throws - OB-335 not reproduced, at the Medium size

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | OB-333, OB-334, OB-335, VC2-C3, UIX-B2 |
| **Where** | `LayoutLabel.setImageOnEDT` `:1071-1124` (the old highlight ended before the new yellow is laid; `endAccessoryHighlight` puts back `lastIcon`, which is already the new picture); `flashHighlight` (ends the accessory highlight first); every caller on the event thread |
| **Needs execution** | done - **`ui.rsa65Medium`** (H) at 46 px: *"a click on the route tile: [128: yellow ... on@43 off@2289 ms] [9: ... on@221 off@2482 ms]"*; *"one switch changed three times, 400 ms apart: [3: yellow first at 51 ms, most 1872 px, at the end 0 px; yellow on@51 off@3095 ms]"*, *"after the hold: ... icon 46x46, highlight armed false"*.  **By reading:** a train's line and tail are painted over the icon (`paintCoveredMark`), so a highlight ending under them loses nothing; a page switched during a hold leaves the old label's timer restoring its own current picture, and a cached page not wired again is drawn afresh (`theCachedPageIsStillWired`).  `core.testLayoutTiles` 12 of 12. |

---

### RSA65-D4 - Round 135, the Medium size: the main window draws the page at 46 with every square on the 46 grid, and the page cache brings back an identical picture; the Medium pop-up's squares are 46 and its window is remembered as `<page>_46`; the track diagram editor and the autonomy editor at 46 are in proportion to 30 and 60; the art is the Large set scaled and cached apart

| | |
|---|---|
| **Disposition** | Checked - clean.  Softness is Adam's choice (*"Start with the cheap 60px scaling"*). |
| **Grade** | D |
| **Names** | FR-122, MT-096, RSA39 (the cache's wiring) |
| **Where** | `LayoutDiagramComponent.artSize`, `getImageKey` (the size in the key), `getImage`; `TrainControlUI.layoutSizes` (a `LinkedHashMap`, the size list built from it), `mediumButtonActionPerformed`, `restoreLayoutTitles`/`saveLayoutTitles` (per size, 46 included); `LayoutPopupUI.drawGrid` (`setWindowIndex(name + "_" + size)`); `LayoutEditor` `:6065`; `DiagramExport` (built at 60 whatever is shown) |
| **Needs execution** | done - **`ui.rsa65Medium`** (`w/out/d/medium/M-medium.txt`, `M-main-*.png`, `P-popup-Medium.png`, `E-*.png`): *"Medium (46 px): grid shown draws the page at this size true; 400 labels, tile widths [46], squares not on the 46 px grid 0"* each time; *"Medium again (from the page cache): pixels differing from the first Medium picture 0"* (twice); the pop-up *"size 46, tile widths [46], steps 46/46 ... remembered as '1 - Main_46'"*; editors' squares 32, 48, 62 (the size and its grid line) and palette 34, 50, 64, minimum sizes 700x680, 806x850, 900x1000.  Pictures at Medium against Large: captions, halos, trains and tails, station marks, address labels drawn over the icons (FR-116), the hand drag (pixel based, `HandScrollListener`) - nothing misplaced, cut off or mismatched with its neighbours; what overlaps at Medium overlaps the same at Large (75 407 DB's caption over the may-turn diamond below it and a signal beside it, EN57-947's icon over its own caption - OB-159's order).  **Claims:** `testTheMediumSizeIsBetweenSmallAndLarge`, `testAMediumSquareIsTheLargeArtScaledDown`, `testTheMediumButtonOpensAMediumPopUp` green.  The Danish S/M/L buttons read L, M, S (Lille, Mellem, Stor) - the language's own initials, Adam's to review. |

---

### RSA65-D5 - Round 136: the main window and the pop-up step a square at each size on both axes; a notch of the wheel moves three squares, across with Shift; an arrow step moves one square; nothing in the viewer scrolls to a square, so nothing was moved off one

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | MT-726, MT-727 |
| **Where** | `LayoutGrid.scrollASquareAtATime`, called after every repaint of the main window (the cache path included) and in `LayoutPopupUI.drawGrid`; `scrollRectToVisible` appears only in the route list |
| **Needs execution** | done - **`ui.rsa65Medium`** (M): steps *"30/30"*, *"46/46"*, *"60/60"* through Small, Medium, Large and back; *"wheel at Large, one notch of 3 with Shift: across 180 (room 791), down 0; without Shift: across 0, down 180"*; *"one arrow step at Medium: ... across 46"* (no vertical bar at Medium on that page).  Claims `testTheDiagramScrollsASquareAtATime`, `testAPoppedOutDiagramScrollsASquareAtATime` green.  The Readme's *"a square at a time in both directions"* is right; the earlier *"hold Shift to scroll across"* was too. |

---

### RSA65-D6 - Round 131, icon clarity: the classic circles, diamonds and squares are the sizes behaviour.md 4b gives at 30 and 60 and in proportion at 46, the same in the editor as on the diagram, the station's diamond on a bend on its rails and clipped to its square, out of service solid orange with a white X on a station and ringed on a sensor; nothing spills; nothing new reads worse than RSA63-D9 put to Adam

| | |
|---|---|
| **Disposition** | Checked - clean.  The look is Adam's to judge. |
| **Grade** | D |
| **Names** | round 131, FR-118 (kept behind `SHAPES`), RSA63-D9, MT-710 |
| **Where** | `TileAnnotation.paintBadge` (classic branch), `classicBox` (14/21/28 a station, 10/15/20 a sensor), the diamond 17/26/36, on a bend 15/22/29; `paintOutOfService`; `spillsOverItsSquare` (false with the classic icons) |
| **Needs execution** | done - **`ui.rsa65Gallery`** (`w/out/d/gallery/G-<diagram\|editor>-<30\|46\|60>-<rest\|arrows\|occupied>.png`, `crop-mayturn-curves.png`): every kind on straight, curve and double curve, both surfaces: the editor's marks filled as the diagram's; the arrowheads over the mark on a curve hide most of it at every size (RSA63-D9's observation, unchanged).  The arithmetic in 4b checked against the code (17 = round(14 x 1.35) - 2; 13 = round(10 x 1.35) - 1; 12 = 10 + 2).  Claims: `core.testAutonomyDiagramMonitor` 67 of 67. |

---

### RSA65-D7 - The documentation at `adf3f4e4`: the guide's quoted labels are the English bundle's; no dash is used as punctuation in Readme.md or Automation.md outside the program's labels, the + and - keys and old date ranges; every 3.0.0 bullet is one sentence or two; each sits in its theme group; what the new paragraphs say is what the program does (but B1, C2)

| | |
|---|---|
| **Disposition** | Checked - clean (but B1, C2). |
| **Grade** | D |
| **Names** | `03a69d09`, `7d7502af`, `4d4c87e7`, `adf3f4e4` |
| **Where** | `Readme.md` and `Automation.md` at `adf3f4e4` |
| **Needs execution** | done - a script over every backticked span of Automation.md against the English bundle and the source (155 spans; the 8 unmatched are templates: `Station (...)`, `Branch E to W`, `From A to B`, `The N Side`, `Go to the Other End (...)...`, `Configuration (`); a scan for em and en dashes and spaced hyphens (only `Yes - Trains Can Stop Here`-style labels, `+ and -`, `[11/2025 - 3/2026]`-style ranges); a sentence count over every changelog bullet from 3.0.0 to 2.8.x (none over two).  Read against the code: *"Routes while trains run"* (`MarklinRoute.heldReason` asks `isAutonomyRunning`; the click asks once; a sensor's route skips only those commands); the `.bak` note (`LayoutDiagram` `:773`, beside the page); the allow-only bullet and guide row (`Allow only the chosen locomotives ...`, `Allowed Locomotives Only`); the Medium, S/M/L and scroll bullets; Automation.md `:156` on route buttons.  Borderline, not graded: the Backup bullet sits under Central Station (it offers to download the diagram from it); *"exit guard signals, held at red while a train stands there"* leaves out that a route elsewhere over a guard turns it green (RSA41-C2/C3, behaviour.md 7c). |

---

### RSA65-D8 - No regression where rounds 131-136 touch: every claim of the classes they changed is green but for the archive's known environmental reds

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Where** | the classes in Method's **Ran** |
| **Needs execution** | done - 221, 67, 8 (copy), 6, 12, 55 of 57, 40 of 41, 23, 68, 115, 10. |

---

### RSA65-D9 - Race conditions: none new - the highlight's work is all on the event thread, the list's mode is set at parse before the railway is published, the icon choice is a volatile static, and the Medium art is decoded off the event thread into a concurrent cache keyed by size

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Where** | `LayoutLabel.setImage`/`setImageOnEDT` (`putIfAbsent`, the picture applied by `invokeLater` and keyed by the component's state when it runs, so a late decode draws the current state); `Point.allowOnlyListed` (written in `parseAuto`); `TileAnnotation.stationIcons` (volatile) |
| **Needs execution** | by reading, and D3's sampling (no stale picture after three changes). |

---

### RSA65-D10 - The archive's environmental reds: `testLocomotiveExclusions` 0 of 8 and two `testDiagramLooksRight` claims want a locomotive database; `testAutoLayout.testAnAnsweredRailIsCoveredWhereTheRoadsPart` wants the sample layout's feedback 5 and is red at `b4786389` too

| | |
|---|---|
| **Disposition** | Checked - not product. |
| **Grade** | D |
| **Needs execution** | done - the copy with locomotives made by name 8 of 8 (D1); *"Feedback 5 does not exist"* alone at both commits (`logs/D6.log`, `logs/P1.log`). |
