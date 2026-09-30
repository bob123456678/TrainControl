# RSA6 - Release validation, round 21: the direction refusal cannot see the railway until a station's Facing menu or the editor has told the session where it is; a page ticked out from the Autonomy menu forgets where the run left its trains, and its timetable entries are written twice going out and erased coming back; a link switched off, or a dead end made exit-only, still leaves a standing train facing a way no copy goes; a track edit's Save writes the moved names back (TrainControl 3.0.0)

**Status:** open

**Prefix:** RSA6

**Reviewed:** branch `autonomy-diagram-r0` at `23461fb3`, read and run from `git archive 23461fb3` unpacked into `validate30/rsa6/a234/` (reading, and the baseline), and a copy of it, `validate30/rsa6/p1/`, with two scratch probe classes added (`test/regression/rsa6Probes.java`, headless; `test/regression/rsa6Window.java`, one window; neither in the battery); `cs2_sample_layout` excluded and an empty folder of that name made for the runner; `LocDB.data` and `UIState.data` beside each root are copies from TC30.  TC30's working tree and the main checkout's were never read, apart from copying the two data files the brief names and hashing the real ones; git was used only as `git -C tc-30 log / show / diff / archive`.  Round 21 is `692a90d4..23461fb3`: the fixes for RSA5 (`0923c08c`), their records (`ab45e871`), the tracker and store (`23461fb3`).  2026-09-30.

## Method

**Round 21, read whole:** the brief; `validate30/RSA5.md` and its dispositions at the commit (`docs/reviews-2026-09-25/RSA5.md`); the round's source diff (`Point`, `Layout.fromJSON`, `AutonomyBuilder`, `AutonomyCompanionStore`, `AutonomySession`, `AutonomyEditorPanel`, `TrainControlUI`, the eight bundles), its docs diff (`behaviour.md` 6a and 8) and its test diff (the thirteen new claims and the rewritten Clear claim), and each changed method in its context.  **For A1/A2's fix:** `pointNamedNow` and `pointNamedByName` whole, `carryTheNamesAcross`, `carry`, `rename`, `legsOf`, `squareCalled`; `whereTheTrainsAre`, `squaresOfRoad`, `namedNow`, every `putTheTrainsBack`, `takeThePendingTurns`, `putThePendingTurnsBack`, `writeTheTurns`, `faceTheWayItCameIn`; `rebuildRunningLayoutFromSetup`, `carryTheTrainsAcross`, `keepWhereTheTrainsStand`, `captureRunningLayout`, `resetAutonomySession`, `layoutRefreshCompleteInternal`, `autonomyEditorClosed`; the whole of `capture` (the fold); `AutonomyBuilder.nodesFor`, `nodeName`, `placementCopy`, `startableCopy`, `facingOf`, `tilesByName`, `baseNames`, `facingByName`, `turningNames`, `uniqueNames`.  **For the direction refusal:** `setDirection` (both), `setDirections`, `record`, `apply`, `directionsTouched`, `trainsTheDirectionsTurn`, `setOneWayRun`, `applyOneWay`, `setRunDirection`, `carryTheOldDirections`, `setRunningLayoutSource` and every caller of it, `getAutonomySession`, `buildAutonomyTileMenu`, `buildAutonomyFacingMenu`, `LayoutRightclickAutonomyMenu`'s branches to them, the five editor doors and `refusedADirection`, the link items (`setPortalDisabled`, `unpairPortal`, `pairPortals`), `TileGraph.validatePortals`.  **For the timetable keep:** `withTheEntriesOfPagesOut`, `namesAPointOnAPageOut`, `squareOnAPageOut`, `sameRun`, `store.setPageExcluded`, and every door that ticks a page - `AutonomyMenu.pagesMenu`, `AutonomyViewerPanel.choosePages`, `AutonomyEditorPanel.setPageExcluded`, the combine door - with `reloadActiveDiagramConfiguration`, `AutonomyViewerPanel.load` and `loadPrepared`, `prepareAutonomyReload`; `clearTimetable`, `setGlobal`; `executePath`'s start check.  **For the names made from a page or a square:** `renamePage` and `renameStoredPoints`, `moveTiles` (store and session), `LayoutPageEdit.renameOrDuplicate`, the track editor's move doors (drag, paste, the four shifts) and its Save, `moveCaption`, `setLinkName`.  **What RSA5 did not reach, read:** the store's portals, captions and link names as they bear on Point names; `executePath`'s refusals.  **Not reached:** `AutonomyEditorPanel` past its direction, link and page doors; the editor's Cancel (`restoreSetup`) against a facing changed meanwhile, read only in outline; the store's reconciliation; `HomeStaging` past what RSA3 to RSA5 read; `CS2File`; export past `exportBundle`; the Return Home planner.  Every finding was searched for in `docs/manual-tests/findings.tsv` (4,996 rows) before it was written; none is catalogued.  The known fault in `regression.testConfirmedGoodState` was not re-run.

**Runs.**  Every JVM through the archive's own `docs/tools/one.sh`, `TC_SCRATCH` a Windows-form folder per job under `rsa6/scratch`, started by a queue (`rsa6/runjob.sh`, `rsa6/queue1.sh`) only once no `java.exe`, `javaw.exe` or `javac.exe` and no battery lock had been seen for two checks 20 s apart, each deciding from its own log.  The coordinator's battery held the machine from 10:17 until 11:22; everything below ran 11:22-11:26, one job at a time.

- **Probes** (p1).  They assert nothing; each writes what it saw, preconditions first, to `p1/rsa6-out/`.  `regression.rsa6Probes` (3 run, 0 failures): P1 for B1, A2 and C1 - ten cases through the three doors that tick a page, each door emulated by the statements it runs; P2 for A1, A3 and B3 - five cases, a control first; P3 for A2 and B2 - an inserted row with and without the Save's fold, and two cases with trains.  `regression.rsa6Window` (1 run, 0 failures): W1, the main window over a sandbox copy of the frozen railway (`test/layouts/live-snapshot`), asked by reflection whether its session can see the running layout, after start-up, after a plain track square's Autonomy Setup menu is built, and after a station's Facing menu is built.
- **Baseline** (a234): the round's claims - `core.testAutonomyDiagramSession`'s `testARenameToAnotherStationsNameCarriesItsLegs`, `testADirectionThatWouldTurnAStandingTrainIsRefused`, `testAPageOutKeepsItsEntriesBesideOneItsLoadDropped`, `testAPageOutKeepsItsEntriesThroughAnEdit`, `testAnEntryNoPageOutExplainsIsErased`, `testAPageRenamedKeepsTheLegsThroughItsUnnamedSquares`, `testAnUnnamedSquareMovedTakesItsLegsWithIt`, `testARenameToAnotherStationsNameCarriesAnotherConfigurationsLegs`, and round 20's `testAPageTickedOutKeepsItsTimetable`; `regression.testAPendingTurnSurvivesTheRebuild.testATurnOwedAtARenamedStationFollowsTheRename`; `regression.testAnEditedPlacementSurvivesTheRebuild`'s `testARenameToAnotherStationsNameKeepsBothTrains` and `testADirectionUnderATrainTheRunLeftIsRefused` (12 run, 0 failures); `regression.testTheImportDoorReadsAnOldFile.testClearEmptiesTheTimetableTheConfigurationKeeps` (1 run, 0 failures, with the window).

**Nothing written** outside `validate30/rsa6/` and this file.  Each run copied the data files into a folder of its own and a preference node of its own (`one.sh`) and removed both; the working copies' `LocDB.data` and `UIState.data` in `a234` and `p1` hash the same as the TC30 copies they came from (`7a0737ae...`, `a64608f1...`).  The two nodes under `HKCU\Software\JavaSoft\Prefs\traincontrol-test-runs` (`battery-680`, `one-620`) are the ones RSA and RSA2 found - not mine, not touched.  No git state changed; no commit.  The real `LocDB.data` and `UIState.data` were hashed at 10:17, before the first JVM, and again at the end: `C:/Users/adamo`'s and the main checkout's are unchanged byte for byte (`6ef56939...`, `9e32894e...`, `481311d4...`, `b80baf8d...`).  Nothing of mine is running: every queue and waiter has ended, no `java.exe`, `javaw.exe` or `javac.exe` is left, and the battery lock is gone.  No job hung, ran out of memory or was ended by the runner.

**Counts:** 3 A, 3 B, 1 C, 9 D.

**Round 21's fixes are right where they reach** (RSA6-D1 to D8, the thirteen claims green): a train, its road and a turn owed are carried by the square and the copy's facing, and a stored name the build gives another square is traced from the square it named; only a page out explains a kept entry, and Clear empties the configuration's timetable; a page renamed carries the names made from it; each running Point says its square, in every reader and writer; the five direction doors say a refusal.  **What they leave, measured:** the refusal reads the railway only through a supplier two doors set - after start-up, or any reset, a direction from a plain track square's menu is judged against the setup alone, which is where the run began, and RSA5-A2's train is left where the setup last had it (RSA6-A1, new); the fold still reads the running layout by Point name through the setup's naming as the gesture has already changed it - a page ticked out from the Autonomy menu or the Autonomy panel's Pages after a run comes back with its trains where the setup last had them, and a track edit's Save writes two trains of one name onto each other's stations (A2, older); a link switched off beside a station leaves a train that came in through it with no copy facing its way, and nothing asks (A3, older, RSA5-A2's harm through a door that is not a direction).  **Adam's question - is the timetable keep reliable now? - no, and less than in round 20 at the door the diagram offers:** the Autonomy menu's Pages tick folds after the tick, so ticking a page out writes each of its entries twice and ticking it back in erases them all; only the editor's own box keeps them (B1, new).  And a track edit's Save writes back what `moveTiles` carried (B2, new); a direction that leaves a dead end with one copy and no facing is not refused, and the train facing the buffer is offered the way out behind it (B3, new); a station name two pages share still moves an out page's entry onto the other station (C1).  **New in round 21:** A1, B1, B2, B3.  **Older:** A2, A3, C1.

---

### RSA6-A1 - The direction refusal cannot see the railway until a station's Facing menu or the setup editor has told the session where it is: after start-up, or any reset of the session, a direction set from a plain track square's Autonomy Setup menu is judged against the setup - where the run began - so RSA5-A2's direction is set and the rebuild leaves the train where the setup last had it

| | |
|---|---|
| **Disposition** | Fixed - every session sees the running railway from the moment the window makes it (TrainControlUI.getAutonomySession sets its running-layout source), not only once a station's Facing menu or the editor has told it - so after start-up or any reset the refusal of a direction, or a link, asks where the railway's trains stand.  claims and fix 3790dbdb; mutation MA1 red.  The claim reads the session the window made; round 21's headless claim sets the source as the window now always does. |
| **Grade** | A.  RSA5-A2's harm, through the door RSA5-A2 named.  **How:** `trainsTheDirectionsTurn` reads *"the railway's trains, and the setup's where the railway has none of them"*; the railway's come from `runningLayout`, a supplier that is null *"until the window sets it"*.  The window sets it in two places only: `buildAutonomyFacingMenu` - built by a right-click on a square whose Point is a destination or holds a train - and the track editor entering autonomy mode.  `getAutonomySession` builds the session without it, `buildAutonomyTileMenu` (the setup submenu a right-click on plain track builds, where One-Way Run, All branches and the per-route radio live) never sets it, and every reset of the session (a diagram edit's Save or Cancel, a page renamed or deleted, a re-download, a switch of railway) starts a new session without it.  With it null the refusal asks the setup alone, and after a run the setup is where the trains started (OB-183: nothing folds a run).  So: start TrainControl, run, stop, right-click the straight beside a station a train was left on facing west, One-Way Run east - the direction is set, `pointNamedNow` finds no copy facing west and answers null, `namedNow` leaves the train out, and the rebuild stands it where the setup had it: the model holds it elsewhere, the station it stands on reads free, and the next dispatch into it is admitted.  **The claim supplies the answer:** `testADirectionUnderATrainTheRunLeftIsRefused` calls `session.setRunningLayoutSource(...)` itself before the gesture (*"from the diagram's menu"*), so it passes where the menu cannot; `testADirectionThatWouldTurnAStandingTrainIsRefused` places the train in the setup.  behaviour.md 6a says every direction door *"asks first, of the railway's trains and the setup's"*.  **Reach:** a direction set from a track square's menu after a run, in a session in which no station's right-click menu has been opened and the setup editor has not been entered since it was made.  **New in round 21**, as a gap in RSA5-A2's fix (the refusal is round 21's; the supplier's wiring is VAL8-B1's and SPEC-B4's). |
| **Names** | RSA5-A2, RSA4-A1, OB-183, VAL8-B1, SPEC-B4 |
| **Where** | `AutonomySession.java:7192-7210` (`trainsTheDirectionsTurn`: `runningLayout == null`, the railway's trains skipped), `:8770-8790` (the supplier, *"Null until the window sets it"*); `TrainControlUI.java:2863-2987` (`getAutonomySession`: not set), `:4800-4823` (`buildAutonomyFacingMenu`: set), `:4826-4905` (`buildAutonomyTileMenu`: not set), `:3246-3251` (`resetAutonomySession`: a new session); `LayoutEditor.java:1704-1712` (set in autonomy mode); `LayoutRightclickAutonomyMenu.java:532`, `:891` (the Facing menu only on a destination or a held square); `TrainControlUI.java:6999-7004` (`namedNow`: null, left out); `regression.testAnEditedPlacementSurvivesTheRebuild.java:720-734`; `docs/reference/behaviour.md:2010-2015` |
| **Needs execution** | done - **W1** (`rsa6ProbeW1WhichRailwayTheDirectionRefusalReads`, the window over the frozen railway, `Main` loaded, 87 Points, 4 trains): *"after start-up: the session reads the railway? false"*; after the Autonomy Setup menu of the straight at `1 - Main:0,2` is built (6 items): **false**, same session; after the Facing menu of the station at `1 - Main:13,9` is built: **true**.  **P2** (`rsa6ProbeP2TheDirectionRefusal`): Alpha, Beta, Gamma two-way; the setup has the train at Gamma; the run's result on the running layout alone, the train on `Beta (westbound)`; All branches one-way east on `main 2,1` and `main 4,1`, then the rebuild as `rebuildRunningLayoutFromSetup` makes it.  **(a) Control, the session told:** refused - *"EN57-1022 stands on Beta facing the other way, so this direction was not set.  Move the train first."*; nothing stored; the train back on `Beta (westbound)`.  **(b) The session not told:** **refused: null**, `TOWARD_A` stored, Beta rebuilt as one copy facing east, `pointNamedNow -> null`, **the train stood on Gamma**, Beta holding nothing; the next fold writes it at `main:5,1`. |

**Direction:** give every session its railway when it is made, in `getAutonomySession`, rather than from two menus; and a claim that builds the session as the window does.

---

### RSA6-A2 - The fold reads the running layout by Point name through the setup's naming as the gesture has already changed it: a page ticked out from the Autonomy menu, or the Autonomy panel's Pages, after a run forgets where the run left the trains on it - they come back where the setup last had them - and a track edit's Save writes two trains of one name onto each other's stations

| | |
|---|---|
| **Disposition** | Fixed - the fold writes each running Point into the square it says it is a copy of (Point.getSquare, checked against every square of the diagram), with its copy's facing, and falls back to its name only where the railway does not say - so a page ticked out after a run keeps where the run left its trains.  The refusal reads the railway's trains the same way.  The Save's swap of two same-named stations is closed with RSA6-B2: no fold follows a move.  claims and fix 3790dbdb; mutation MA2 red.  Not run: the refusal's railway half by name - at rest the railway running and the naming agree, so no claim holds them apart. |
| **Grade** | A.  OB-183's harm, and RSA5-A1's, through the fold rather than the carry.  **How:** `capture` maps each running Point to a square by its name through `builder(null).tilesByName()` - the session's reduction as it stands, not the one the running layout was built from - and skips a name it does not find.  Round 21 wrote each Point's square onto the running layout (`Point.getSquare`, 87 of 87 on the frozen railway) and the fold does not read it.  Two gestures change the naming and then fold.  **A page ticked out** from the Autonomy menu's Pages (`setPageExcluded`, `save`, `reloadActiveDiagramConfiguration` -> `load(active, true)` -> the fold, then the build) or the Autonomy panel's Pages dialog (no load; the next fold): the naming no longer has the page, so every train on it is skipped and the setup keeps where each stood before the run.  Ticked back in, each is built there: the model holds the train where it is not, and the square it stands on reads free.  The editor's own box does not lose it only because opening the editor folded first.  **A track edit's Save** (`layoutEditingComplete` -> `resetAutonomySession` -> `captureRunningLayout`): the moves have already rebuilt the naming (`moveTiles` -> `touched`), and the running layout still carries the names from before them.  `uniqueNames` gives a shared name plain to the square first by tile key *as a string*, so a column inserted at the left moves `main:9,1`/`main:11,1` to `main:10,1`/`main:12,1` and swaps which is `Hbf`: the fold writes each train onto the other's station.  Two unnamed stations shifted onto each other's coordinates write one train in two places.  `writeTheTurns` (`faceTheWayItCameIn`, through the station index) and `trainsTheDirectionsTurn` read the railway by name the same way.  **Reach:** the page tick - a page with trains the run moved, ticked out from the Autonomy menu (on the menu bar and on the diagram's right-click) or the Autonomy panel before anything folded; the swap - two stations of one name, or unnamed stations, and a move that reorders them: the frozen railway has neither, all 33 of its stations named.  **Older:** the fold by name predates round 21; found asking the round's question of every fold. |
| **Names** | OB-183, RSA5-A1, RSA4-A1, DW-A1, MT-135, OB-306, RLV10-B1 |
| **Where** | `AutonomySession.java:5907-5926` (`capture`: `tilesByName.get(point.optString("name"))`, skipped when absent), `:9653-9657` (`setPageExcluded` -> `touched`), `:3233-3285` (`moveTiles` -> `touched`); `AutonomyMenu.java:795`, `:816`; `TrainControlUI.java:25763-25775`; `AutonomyViewerPanel.java:899-914` (the fold before the build), `:591-635` (`choosePages`: no load); `LayoutEditor.java:7380-7412` (Save), `:3089`, `:3256`, `:4609`, `:4666`, `:4711` (the moves); `TrainControlUI.java:24116-24150`, `:3246-3251`, `:3193-3239`; `AutonomyBuilder.java:1681-1728` (`uniqueNames`, sorted by `getTile().toString()`); `TrainControlUI.java:8116-8140`, `AutonomySession.java:2663-2700` |
| **Needs execution** | done - **P1 (I)**: two pages; the train `E 991` placed at `second:3,1` (Epsilon); the run's result, on the running layout alone, `E 991` on Zeta (`second:5,1`); `second` ticked out and back in as the Autonomy menu does: **with the page back `E 991` stands on `Epsilon (westbound)`** (the run left it on Zeta), the setup places it at `second:3,1`.  **Control (J)**: the same with the editor's opening fold first, then its box out and in: `E 991` on Zeta.  **P3 (d)**: stations `Hbf` at `main:9,1` and `main:11,1` (`Hbf (2)` and `Hbf`), `EN57-1000` on the first and `EG 3101 DSB` on the second, the editor's opening fold (each placed right), one column inserted at the left and `moveTiles` (each placed right, at `10,1` and `12,1`), then the Save's fold: **`EG 3101 DSB` placed at `main:10,1`, `EN57-1000` at `main:12,1`**; the reload stands each on the other's station.  **P3 (c)**: two unnamed stations, a train on the second, two columns inserted: the Save's fold places it at **`[main:7,1, main:5,1]`**, and the reload logs *"duplicate locomotive"*. |

**Direction:** fold each running Point into the square it says it is a copy of (`Point.getSquare`), and the facing its copy has, rather than by its name through today's naming; the same for the turns written and the refusal's railway half.

---

### RSA6-A3 - "Use this link" switched off beside a station where the run left a train that came in through the link: the station is left with one copy, facing the other way, nothing asks, and the rebuild leaves the train where the setup last had it

| | |
|---|---|
| **Disposition** | Fixed - a link switched off where a train came in through it is refused and put back, as a direction is (AutonomySession.setPortalDisabled asks trainsTheSetupWouldTurn), and its switch says why (autosetup.ui.errorLinkTurnsATrain).  The refusal's trial is now a graph made as a rebuild makes it, from the setup as it stands, which a link switched off reaches and the graph held does not.  claims and fix 3790dbdb; mutations MA3 red, MA3t red. |
| **Grade** | A.  RSA5-A2's harm through a gesture that is not a direction.  **How:** a station beside a paired link is two copies; a train that came in through the link stands on the copy facing away from it.  Switched off from the link's own menu (*Use this link*, `setPortalDisabled` -> `setupChanged` -> the rebuild), the link is a missing edge, the station has one arrival side and one copy facing the link, and `pointNamedNow` answers null for the train's copy - so `namedNow` leaves the train out and the rebuild stands it where the setup last had it.  The refusal lives in `directionsTouched` alone; `setPortalDisabled`, like every other setup door, calls `touched()`.  Unpairing makes the setup unbuildable (two blocking problems), so its rebuild is refused and the railway is kept - that door is safe.  **Reach:** a train standing next to a paired link it came through, and that link switched off at rest.  **Older** (the carry has dropped such a train since RSA4-A1's carry; round 21 made the copy-facing rule the refusal's and did not extend it here). |
| **Names** | RSA5-A2, RSA4-A1, OB-031, GSE-B1, VD10-B2 |
| **Where** | `AutonomyEditorPanel.java:1955-1966` (the toggle), `:1990-1999` (Unpair); `AutonomySession.java:7334-7338` (`setPortalDisabled` -> `touched`), `:7160-7185` (the refusal only in `directionsTouched`), `:502-561` (`pointNamedNow`: null); `TileGraph.java:931` (an unpaired link is blocking) |
| **Needs execution** | done - **P2 (d)**: a loop - link (`main 0,1`), Alpha, Beta, Gamma, link (`main 6,1`) - the links paired, 0 blocking; the setup has the train at Alpha; the run's result `Gamma (westbound)`; the east link switched off: **refused: null**, 0 blocking, Gamma one copy facing east, `pointNamedNow -> null`, **after the rebuild the train stands on Alpha**, Gamma holding nothing.  **(e)** the link unpaired instead: 2 blocking, so the load a rebuild makes refuses it. |

**Direction:** ask the refusal's question after every setup gesture that rebuilds - one place, in `touched` - rather than after the direction doors alone.

---

### RSA6-B1 - Adam's question: no.  The Autonomy menu's Pages tick folds after the tick: a page ticked out has each of its timetable entries written twice, and ticked back in has them all erased; the Autonomy panel's Pages dialog does the same at the next fold.  Only the editor's own box keeps them

| | |
|---|---|
| **Disposition** | Fixed - which pages are out is asked of the running layout, by its Points' squares (pagesTheRunningLayoutLeftOut), not of the setup a tick has just changed: a page ticked out from the Autonomy menu, the Autonomy panel or the editor keeps its entries once each, and back in has them all.  A page out's squares are the page's own, which the graph leaves out, so the entries are traced after a restart too.  Adam's question answered for every door: yes, now.  claims and fix 3790dbdb; mutations MB1p red, MB1g red.  The claims tick as the menu does - the page, the save, the fold of the railway running - and build what a load reads. |
| **Grade** | B.  RSA4-B2's and RSA5-B1's loss, graded as they were; worse than round 20 at the door the diagram offers.  **How:** `withTheEntriesOfPagesOut` now asks which pages are out *when it folds* (`store.getExcludedPages()`), and puts each stored entry of a page out after the running entry its neighbour became - without asking whether the running timetable already holds it.  The Autonomy menu's tick sets the page, saves, and reloads with a fold (`load(active, true)`), so the fold always sees the page's new state and the running layout's old one.  **Ticked out:** the running layout still has the page and its entries, the page is now out, so each of its entries is kept *and* run: stored twice.  **Ticked back in:** the running layout, built with the page out, has none of its entries, the page is no longer out, `pagesOut` is empty and the running timetable is stored as it is: every entry of the page erased.  Both ways through the menu, the page returns with nothing; out through the menu and back through the editor's box, with every entry twice (the second is then refused as not standing at its start).  The Pages dialog (`choosePages`) changes the page and loads nothing, so the next fold - the exit's, the editor's, a reload - does the same.  Round 20's rule asked the running layout's own build, and RSA5 measured the menu's tick back in keeping them (its P1 a: *"stored 2, running 2"*).  **The claims tick without the door's fold:** `testAPageTickedOutKeepsItsTimetable`, `testAPageOutKeepsItsEntriesBesideOneItsLoadDropped` and `testAPageOutKeepsItsEntriesThroughAnEdit` call `setPageExcluded` and `rebuild` and fold only a running layout already built without the page.  behaviour.md 8 says the page *"ticked out of autonomy and back in keeps its timetable entries"*.  **Reach:** any page with captured entries ticked from the Autonomy menu or the Autonomy panel.  **New in round 21.** |
| **Names** | RSA5-B1, RSA4-B2, RSA5-C1, RSA3-B1 |
| **Where** | `AutonomySession.java:6171-6220` (`withTheEntriesOfPagesOut`: `pagesOut` read at the fold, `:6175` running returned when none is out, `:6188-6192` kept beside running), `:6226-6245`; `AutonomyMenu.java:795`, `:816`; `TrainControlUI.java:25763-25775`; `AutonomyViewerPanel.java:769-860`, `:899-914`, `:591-635`; `AutonomyEditorPanel.java:9943-9973` (the box: the rebuild, no fold); `core.testAutonomyDiagramSession.java:507`, `:813`, `:849`; `docs/reference/behaviour.md:2483-2490` |
| **Needs execution** | done - **P1** (`rsa6ProbeP1TheTimetableKeepAtEveryTickDoor`): two pages of three stations, an entry on each (`Beta (eastbound) -> Gamma`, `Epsilon (eastbound) -> Zeta`).  **(A1) out from the start, in by the menu:** stored 2, loaded 1; ticked in: **stored 1, running 1**.  **(C) in play, out by the menu, in by the menu:** out: **stored 3** (`Epsilon -> Zeta` twice), running 1; in: **stored 1, running 1**.  **(B) out by the menu, the exit, in by the editor's box:** stored 3; **running 3**, `E 991: Epsilon -> Zeta` twice.  **(E) out by the Pages dialog, the exit, a restart, in by the dialog, the exit:** the exit's fold **stored 3**; after it **stored 1**, the next start running 1.  **Controls, the editor's box:** (A2) stored 2, running 2; (D) out and in, an exit between: 2 and 2; (F) an entry for a locomotive gone beside them: that one erased, the page's kept, 2 and 2; (G) one deleted and recorded again: 3 and 3. |

**Direction:** decide at the fold from the running layout's own build - an entry is the page's where its Points are on a page the running layout left out - and never store an entry that the running timetable already holds; or make each tick door rebuild without a fold, as the editor's box does.

---

### RSA6-B2 - RSA5-B2's move half is undone at the track editor's Save: `moveTiles` carries the names made from a moved square, and the reset's fold writes the running layout's old names back over them, so every leg and road through an unnamed square moved is lost

| | |
|---|---|
| **Disposition** | Fixed - no fold follows a move of squares until the setup is opened again (AutonomySession.squaresMoved, as a page rename's pagesStale): the railway running still names and places what it knows by the squares as they were, and opening the editor folded already.  claims and fix 3790dbdb; mutations MB2 red, MB2s red. |
| **Grade** | B.  RSA5-B2's loss, at the door that reaches it.  **How:** the track editor moves tiles, then tells the session (`moveTiles`), whose store now renames `main 3,1` to `main 3,2` in every timetable leg and road (round 21).  Save calls `layoutEditingComplete`, whose reset folds the running layout first (`captureRunningLayout`) - and the running layout was built before the move and was never rebuilt while the editor was open (OB-047: no run meanwhile).  The fold writes its timetable, with the old names, over the configuration's (`withTheEntriesOfPagesOut` returns it as it is), and its roads onto each moved square it can still name.  The session after the reset loads neither: the entry is dropped (*"Edge main 3,1 (eastbound) -> Gamma does not exist"*), the road with it, and the next fold erases both.  A page renamed is safe because its rename sets `pagesStale`, which refuses exactly this fold; a move sets nothing.  The claim (`testAnUnnamedSquareMovedTakesItsLegsWithIt`) reads the store after `store.moveTiles` and never saves.  **Reach:** the frozen railway's one captured entry runs through `1 - Main 12,7`; a row or column inserted above or left of it on `1 - Main`, or that sensor dragged, and Save.  **New in round 21**, as a gap in RSA5-B2's fix. |
| **Names** | RSA5-B2, RSA3-B1, DW-A1, MT-135, TR-A1 |
| **Where** | `AutonomyCompanionStore.java:3210-3231` (the rename in `moveTiles`); `AutonomySession.java:3233-3285`, `:5898` (`pagesStale`, set only by a page rename), `:6143-6150`; `LayoutEditor.java:7400-7412`; `TrainControlUI.java:24116-24150`, `:3246-3251`, `:3193-3239`; `core.testAutonomyDiagramSession.java:998-1024` |
| **Needs execution** | done - **P3** (`rsa6ProbeP3AMoveAndTheSavesFold`): Alpha, an unnamed station at `main 3,1`, Gamma; the entry `main 3,1 (eastbound) -> Gamma`; a train at Alpha with the road `main 3,1 (westbound) -> Alpha`; a row inserted above (every tile to row 2) and `session.moveTiles`.  **(a) Control, no fold after it:** stored `main 3,2 (eastbound) -> Gamma` and road `main 3,2`; a new session loads 1 entry and the road; kept by the next fold.  **(b) The Save's fold:** stored **`main 3,1 (eastbound) -> Gamma`**, road **`main 3,1`**; the reload: **running 0, road null**; after the next fold **stored 0, road null**. |

**Direction:** refuse this fold as a page rename refuses it, or fold by `Point.getSquare` (RSA6-A2), which a move carries.

---

### RSA6-B3 - A direction that leaves a dead-end station with one copy and no facing is not refused: a train the run left facing the buffer stays on it, and is offered the only way out, behind it

| | |
|---|---|
| **Disposition** | Fixed - a copy that records no facing faces its only way out (AutonomyBuilder.onlyWayOutFacing), so a direction that leaves a dead end facing away from a train there is refused.  claims and fix 3790dbdb; mutation MB3 red. |
| **Grade** | B.  Between RSA2-C7 (C: a facing-less square offering a train both ways out) and AUT2-A1/TDY2-A1 (A: a train stood on a copy facing the other way).  **How:** a dead-end station on a two-way line is one copy, arrived at from the open side, facing the buffer; a train sent there stands facing it.  One way *away* from the station on the rail out of it - the natural answer to the trapped-arrival warning such a station raises - leaves it a square nothing arrives at with one way out: one Point, no facing (RSA2-C7's rule).  `trainsTheDirectionsTurn` skips every copy with no facing (`way == null`), so nothing is refused; `pointNamedNow` keeps the train by its unchanged name; the fold records no facing for it.  The train facing the buffer is then offered `Alpha -> Beta` - a route locked away from the way its decoder drives, since the runtime never commands a direction.  **Reach:** a train standing nose-in at a dead end and the rail out of it made one-way away at rest.  **New in round 21**, as a gap in RSA5-A2's fix. |
| **Names** | RSA5-A2, RSA2-C7, AUT2-A1, TDY2-A1 |
| **Where** | `AutonomySession.java:7250-7264` (a facing-less copy skipped), `:512` (the name kept); `AutonomyBuilder.java:656-676` (one way out: one Point, no facing), `:1607-1622` |
| **Needs execution** | done - **P2 (c)**: Alpha a dead end, Beta, Gamma two-way, the session told; the run's result the train on `Alpha` (its copy facing W); one way east on `main 2,1`: **refused: null**, `TOWARD_A` stored, `pointNamedNow -> Alpha`, the train on Alpha, Alpha's facings now `{}`, **ways out of it: `[Alpha -> Beta (eastbound)]`**; the next fold: the setup's facing at Alpha null. |

**Direction:** treat a copy with no facing as facing its only way out, and refuse where that is not the train's way.

---

### RSA6-C1 - A station name two pages share: a page ticked out and back in keeps its entries only where its stations sort after the other page's - otherwise the plain name moves across while it is out, the carry sends the page's entry to the other station when it returns, and the next fold erases it

| | |
|---|---|
| **Disposition** | Fixed - names are settled over every page (AutonomyBuilder.withNamesOutOfPlay): the named squares of a page out of autonomy take their turn in uniqueNames without being emitted, so a name two pages share is suffixed the same way whichever is out, and a tick moves nothing.  claims and fix 3790dbdb; mutation MC1 red. |
| **Grade** | C.  **How:** `uniqueNames` gives a shared name plain to the first square by tile key and suffixes the rest.  Two pages each with a `Beta`: `main`'s is `Beta`, `second`'s `Beta (2)`.  Tick `main` out and `second`'s becomes plain; round 21's carry (a name the build now gives another square) rightly moves `second`'s entry to `Beta (eastbound)`, and leaves `main`'s alone, its square not built - so both entries name `Beta (eastbound)`.  Tick `main` back in and the carry traces `Beta (eastbound)` from the square it was last seen at - `second`'s - and moves *both* to `Beta (2) (eastbound)`: `main`'s entry now runs from the other page's station, does not load, and the next fold erases it.  **Reach:** two pages with a station of one name - none on the frozen railway.  **Older** (the shifting suffix); the round's trace makes the second move. |
| **Names** | RSA5-A1, RSA5-B1, RSA3-B1 |
| **Where** | `AutonomyBuilder.java:1681-1728`; `AutonomySession.java:834-933` (`carry`, the memory at `:663-668`) |
| **Needs execution** | done - **P1 (H)**: `main` Alpha, Beta, Gamma; `second` Delta, Beta, Zeta; entries `Beta (eastbound) -> Gamma`, `Beta (2) (eastbound) -> Zeta`; `main` out by the editor's box, the exit, back in: stored **`Beta (2) (eastbound) -> Gamma`** beside `Beta (2) (eastbound) -> Zeta`, running 1 (*"Edge Beta (2) (eastbound) -> Gamma does not exist"*); after the next fold **stored 1**. |

**Direction:** carry a name by the square it was built on in the build it came from, not by the square it was last seen at.

---

### RSA6-D1 - RSA5-A1: a train, its road and each stored name carried by the square it named

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RSA6-A2 (the fold) and C1 (a shared name across a page tick). |
| **Grade** | D |
| **Names** | RSA5-A1, RSA4-A1, RSA4-B1, OB-183 |
| **Where** | `AutonomySession.java:502-611`, `:634-676`, `:783-933`; `TrainControlUI.java:6732-6813`, `:6961-7055`, `:3067-3096`, `:7198-7356` |
| **Needs execution** | done - the round's claims green on the archive (baseline), P2 (a); the rest by reading |

Both carry doors go through `pointNamedNow` with the square and facing `whereTheTrainsAre` records, the road by `squaresOfRoad`; a name is kept only where the rebuild still gives it to the same square, the square gone answers none, and the fall-backs are the same copy under the square's new name, then the copy facing that way.  The configuration in use and every other trace a name the build now gives another square from the square it named; the memory is written after each carry, so each carry reads the build before its own.

---

### RSA6-D2 - RSA5-C2: the turns owed carried by square and facing

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RSA5-C2, D3-C5, RGD-C7 |
| **Where** | `TrainControlUI.java:6826-6879`, `:3067-3096`, `:3137-3149`, `:7322-7355` |
| **Needs execution** | done - the round's claim green (baseline); the rest by reading |

Each drain records the Point's square and copy facing off the railway before the load replaces it; each put-back asks `pointNamedNow` and keeps the recorded name only where there is no such copy, as the turn was owed before.

---

### RSA6-D3 - RSA5-C1: only a page out explains a kept entry, and Clear empties the configuration's timetable

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RSA6-B1 (which pages are out is asked at the fold). |
| **Grade** | D |
| **Names** | RSA5-C1, RSA4-B2 |
| **Where** | `AutonomySession.java:6171-6272`, `:6320-6342`; `TrainControlUI.java:22944-22992` |
| **Needs execution** | done - the Clear claim green with the window (baseline), P1 (F); the rest by reading |

An entry for a locomotive gone is erased beside the page's kept one (P1 F); the frozen railway's pages out have no names of their own, so no name of a station removed elsewhere can be taken for one of theirs.  Clear is refused while autonomy is busy, and writes the empty timetable into the configuration in use at once.

---

### RSA6-D4 - RSA5-B1 through the editor's own box

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RSA5-B1, RSA4-B2 |
| **Where** | `AutonomyEditorPanel.java:9943-9973`; `AutonomySession.java:6171-6220` |
| **Needs execution** | done - P1 (A2), (D), (F), (G), (J) |

Where no fold straddles the tick - the box rebuilds with `load(active, false, false)`, and the editor folded as it opened - every case came back: the plain case, an exit while out, an entry dropped for its own reason beside them, one deleted and recorded again, and a train the run moved on the page.

---

### RSA6-D5 - RSA5-B2's page-rename half

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RSA5-B2, DW-A1, MT-135, OB-049 |
| **Where** | `AutonomyCompanionStore.java:2862-2931`, `:1437-1442`; `LayoutPageEdit.java:288-298`; `AutonomySession.java:5898` |
| **Needs execution** | done - the round's claim green (baseline); the rest by reading |

The rename door folds before the store is rekeyed, the rekey renames every *page x,y* name in every configuration's legs and roads (anchored on the page's own name and a coordinate, so a page whose name begins another's is not touched), and `pagesStale` refuses the reset's fold, so nothing writes the old names back - the guard RSA6-B2's moves lack.

---

### RSA6-D6 - The Point's square in every JSON reader and writer

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RSA5-A1 |
| **Where** | `AutonomyBuilder.java:1079-1085`; `Point.java:1156-1173`, `:1289-1293`; `Layout.java:13053-13058`; `AutonomyCompanionStore.java:5962-5981` |
| **Needs execution** | done - W1: 87 of 87 running Points on the frozen railway say their square |

The build writes it, `Point.toJSON` writes it back, and `Layout.fromJSON` is the only reader of a Point; there is no copy constructor, and `Layout.renamePoint` has no caller.  `parseTileKey` splits at the last colon, so a page name with a colon still parses; a hand-written railway has none and falls back to the name.

---

### RSA6-D7 - The five direction doors say a refusal, and a refusal changes nothing

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RSA6-A1 (which trains it sees), A3 and B3 (what it asks). |
| **Grade** | D |
| **Names** | RSA5-A2 |
| **Where** | `AutonomyEditorPanel.java:4293-4312`, `:7419-7430`, `:7636-7645`, `:7713-7771`, `:9254-9266`; `AutonomySession.java:7112-7304`, `:9519-9568`, `:9936-9960`; the eight bundles' `autosetup.ui.errorDirectionTurnsATrain` |
| **Needs execution** | done - P2 (a); the rest by reading |

Every session door that sets a direction ends in `directionsTouched`; a refusal puts each route back through `apply` - graph and store - before anything is derived, so nothing is rebuilt; each of the five editor doors takes the refusal and says it before `setupChanged`.  The sentence is in all eight bundles, escaped.  The legacy import records directions and calls `touched`, but only on a diagram with none set, which Adam's is not.

---

### RSA6-D8 - Captions, link names and unpairing name no Point and carry no train off

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RSA5 (not reached), OB-031, GSE-B1 |
| **Where** | `AutonomySession.java:3327-3340`, `:9659-9663`, `:9690`; `TileGraph.java:910-935` |
| **Needs execution** | done - P2 (e); the rest by reading |

A caption points at a station and a link's name names the link, neither a Point; an unpaired link is a blocking problem, so the rebuild after it is refused and the railway is kept (P2 e: 2 blocking).

---

### RSA6-D9 - The probes' controls, and the baseline

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | - |
| **Where** | `validate30/rsa6/p1/test/regression/rsa6Probes.java`, `rsa6Window.java`; `validate30/rsa6/p1/rsa6-out/`; `validate30/rsa6/logs/` |
| **Needs execution** | done |

Each probe states its precondition before its result, and each measured defect has a control beside it that answers the other way: P2 (a) for A1; W1's third reading for W1's first two; P1 (J) for A2's page tick and P3 (d)'s opening fold for its swap; P1 (A2), (D), (F), (G) for B1; P3 (a) for B2.  The baseline: 12 and 1 run, 0 failures; the probe jobs 3 and 1 run, 0 failures; every job exited 0, and none hung, ran out of memory or was ended by the runner.
