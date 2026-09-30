# RSA5 - Release validation, round 20: the carry takes a still-built name for the same Point, so a station renamed to another's name takes a train off the railway; a direction set against a standing train leaves it where it started; the kept timetable entries come back only while nothing else about the timetable differs; and a page renamed loses every leg through a square with no name of its own (TrainControl 3.0.0)

**Status:** open

**Prefix:** RSA5

**Reviewed:** branch `autonomy-diagram-r0` at `692a90d4`, read and run from `git archive 692a90d4` unpacked into `validate30/rsa5/a692/` (reading, and the baseline), and a copy of it, `validate30/rsa5/p1/`, with one scratch probe class added (`test/regression/rsa5Probes.java`, not in the battery); `cs2_sample_layout` excluded and an empty folder of that name made for the runner; `LocDB.data` and `UIState.data` beside each root are copies from TC30.  TC30's working tree and the main checkout's were never read, apart from copying (and, at the end, hashing) the two data files the brief says to copy from TC30; git was used only as `git -C tc-30 log / show / diff / archive / ls-tree`.  Round 20 is `2ee5a05a..692a90d4`: the fixes for RSA4 (`79e75e08`), their records (`7407f1c7`) and the tracker and store (`692a90d4`).  2026-09-30.

## Method

**Round 20, read whole:** the brief; `validate30/RSA4.md` and its dispositions at the commit (`docs/reviews-2026-09-25/RSA4.md`); the round's source diff (`Layout`, `AutonomyBuilder`, `AutonomySession`, `AutonomyMenu`, `AutonomyViewerPanel`, `TrainControlUI`, the message bundles), its docs diff (`behaviour.md` 5d and 8, `open-questions.md`) and its sixteen claims in `core.testATrainIsDispatchedOnce`, `core.testAutonomyDiagramSession`, `regression.testAnEditedPlacementSurvivesTheRebuild` and `regression.testTheImportDoorReadsAnOldFile`; each changed method in its context.  For RSA4-A1: `pointNamedNow`, `carryTheNamesAcross` and the maps it keeps, `AutonomyBuilder.uniqueNames`, `nodeName`, `nodesFor`, `tilesByName`, `baseNames`, `facingByName`, `turningNames`, `placementCopy`, `startableCopy`; `TrainControlUI.whereTheTrainsAre`, every `putTheTrainsBack` and `namedNow`, `rebuildRunningLayoutFromSetup`, `carryTheTrainsAcross`, `keepWhereTheTrainsStand`, `captureRunningLayout`, `resetAutonomySession`, `layoutRefreshCompleteInternal`; `AutonomyViewerPanel.load` and `loadPrepared`; `Layout.moveLocomotive`; `AutonomyEditorPanel.promptName`, `refusedWhileRunning`, `isIgnored`.  For RSA4-B1 and C4: `rename`, `carry`, `legsOf`, `squareCalled`, the store's `getConfigurationNames`/`getConfiguration`, `renamePage`, `moveTiles`, `repairLocomotive*`, `LayoutPageEdit.renameOrDuplicate`, `GraphReducer.generatedName`.  For RSA4-B2: the capture whole, `withTheEntriesItsLoadDropped`, `sameRun`, `namesAPointNotBuilt`, `Layout.toJSON`'s timetable (the loan), `Layout.fromJSON`'s timetable, `TimetablePath.fromJSON`/`toJSON`, `loadReturnToHomeTimetable`, `requestReturnToHome`, `deleteTimetableEntry`, `clearTimetable`, `updateTimetableDelay`, `addTimetableEntry`, `locDeleted`.  For RSA4-C1 and C2: `executeTimetable`/`executeTimetableInternal` whole, `pacedWait`, `pauseUnlessStopped`, `pacedWaitMillis`, `stopLocomotives`, `stopEveryTrainWhereItIs`, every write of `running` (`runLocomotives` among them), every caller of `executeTimetable` (Execute Timetable's worker, Return Home's), `isRunning`, `isAutonomyBusy`.  For RSA4-C3: `pathHeldBy` and its four readers, `getActiveAccs`, the hand-over, `configureAndLockPath`'s claim, every other removal of a claim, `getLastPointsReached`, `getPossiblePaths`.  For RSA4-C5: `importConfiguration` and every door into it.  The other stored Point names the brief lists: the pending turns (`takeThePendingTurns`, `putThePendingTurnsBack`, `restoreReversalsOnArrival`, `writeTheTurns`, `faceTheWayItCameIn`); homes, exclusions, `blockedBy` and the store's keys.  **What RSA4 did not reach, read:** `HomeStaging.canEnter`, `blockedSensors`, `passesTheTailsOfTrainsItHasMoved`, `passesTheTailsOfTrainsThatHaveNotMoved`, `liesAcrossAtStart`, `stillWhereItStarted`.  **Not reached:** `AutonomyEditorPanel` past its run refusal and the rename; `AutonomyCompanionStore`'s reconciliation, portals, captions and link names; `CS2File`; export past `exportBundle`; the rest of `HomeStaging` past what RSA3 and RSA4 read.  Every finding was searched for in `docs/manual-tests/findings.tsv` (4,980 rows) before it was written; none is catalogued.

**Runs.**  Every JVM through the archive's own `docs/tools/one.sh`, `TC_SCRATCH` a Windows-form folder per job under `rsa5/scratch`, started by a queue (`rsa5/runjob.sh`, `rsa5/queue1.sh`) only once no `java.exe`, `javaw.exe` or `javac.exe` had been seen and no battery lock existed for two checks 20 s apart, each deciding from its own log.  The coordinator's battery (from `scratchpad/r20bat`) held the machine from before 07:30 until 08:35; everything below ran 08:35-08:38, one job at a time.

- **Probes** (p1, `regression.rsa5Probes`: 4 run, 0 failures, twice).  They assert nothing; each writes what it saw, preconditions first, to `p1/rsa5-out/`.  P1 for RSA5-B1 and C1 (eight cases, a control first); P2 for B2 (a control first); P3 for A1 (a control first); P4 for A2 (a control first, and the same railway folded before the gesture).  The first run's P1, P2 and P3 looked for legs out of a dead-end station, which has no way out, and found none (its output is kept in `p1/rsa5-out-run1/`); they were corrected and the class run again: the results below are the second run's.  P4's output is identical in both.
- **Baseline** (a692): the round's claims - `core.testATrainIsDispatchedOnce`'s `testTheRouteGuardSeesATrainHandingOver`, `testTheTailRuleSeesATrainHandingOver`, `testWhereATrainHandingOverIsIsItsStart`, `testATimetablesLoopIsRunningUntilItHasStopped`, `testTheYesAndATimetablesStartCannotStraddle`; `core.testAutonomyDiagramSession`'s `testARenamedStationKeepsEveryConfigurationsTimetable`, `testARenamedStationKeepsATrainsRoad`, `testAPageTickedOutKeepsItsTimetable`, `testARenamedStationKeepsALegOnlyAnotherConfigurationBuilds`, `testATimetableClearedWhileAPageIsOutStaysCleared`; `regression.testAnEditedPlacementSurvivesTheRebuild`'s `testATrainOnARenamedStationIsPutBackOnIt`, `testATrainOnASquareSplitSinceIsPutBackFacingItsWay`, `testATrainOnAStationWithNoFacingIsPutBackAfterARename` (13 run, 0 failures); `regression.testTheImportDoorReadsAnOldFile`'s `testAnImportIsRefusedWhileAutonomyRuns`, `testARenameAfterARunKeepsTheTrainOnTheStation` (2 run, 0 failures, with the window).  Fifteen of the sixteen: `testTheImportDoorReadsAnOldFile.testAnOldFileIntoTheConfigurationInUseIsRefusedWhileAutonomyRuns`, rewritten for the new refusal, was read, not run.

**Nothing written** outside `validate30/rsa5/` and this file.  Each run copied the data files into a folder of its own and a preference node of its own (`one.sh`), and removed both; the working copies' `LocDB.data` and `UIState.data` in `a692` and `p1` hash the same as the TC30 copies they came from.  The two nodes under `HKCU\Software\JavaSoft\Prefs\traincontrol-test-runs` (`battery-680`, `one-620`) are the ones RSA and RSA2 found - not mine, not touched.  No git state changed; no commit.  The real `LocDB.data` and `UIState.data` were copied and hashed at 07:30, before the first JVM, and again at the end: `C:/Users/adamo`'s and the main checkout's are unchanged byte for byte (`6ef56939...`, `9e32894e...`, `481311d4...`, `b80baf8d...`).  Nothing of mine is running: every queue and waiter has ended, no `java.exe`, `javaw.exe` or `javac.exe` is left, and the battery lock is gone.

**Counts:** 2 A, 2 B, 2 C, 10 D.

**Round 20's fixes are right where they reach** (RSA5-D1 to D8, the claims green): the timetable's loop is counted and woken; the Yes and a timetable starting cannot straddle; every reader of one train's path reads the claims first; Import is refused before it asks; the written rules match the code for the sensor and the Yes; a rename carries each configuration's names and each road; and a page ticked out and back in keeps its entries across a reload, the exit's fold and a restart.  **What they leave:** the carry of the trains - and of every stored name - takes a name the new build still gives as the same Point, and `uniqueNames` hands a station's plain name to whichever square sorts first: a station renamed to a name another has, or one of two that share a name renamed, takes a train off the railway with the square it stands on reading free (RSA5-A1, measured - older, kept as the round's first rule); a direction set against a train the last run left on a station has no copy facing its way, and the rebuild leaves the train where it started - RSA4-A1's harm through a gesture RSA4-A1 named (A2, measured); and **Adam's question - is the entries' coming back reliable? - no**: they come back only while every other stored entry loads and the visible timetable is untouched; an entry for a locomotive the database no longer has, a leg whose rail is gone, one entry deleted or one recorded while the page is out, and every entry of that page is written away (B1, measured).  **Older, found asking the round's question of every stored Point name:** a page renamed, or a tile moved in the track diagram, renames every Point with no name of its own, and nothing carries it into a timetable or a road - the frozen copy of Adam's railway holds one captured entry, and it runs through `1 - Main 12,7` (B2, measured); an entry no load can read again is now kept for ever, and a cleared timetable of a page's entries comes back (C1, measured); the pending turns are carried by Point name (C2, read).  **New in round 20:** A2, B1, C1.  **Older:** A1, B2, C2.

---

### RSA5-A1 - A station renamed to the name another station has - or one of two stations that share a name renamed - moves that name onto another square; the carry takes "the name itself where it is still built" as the same Point, so the rebuild stands one train on the wrong station and takes the other off the railway, the square it stands on reading free, and the timetable carry erases the leg

| | |
|---|---|
| **Disposition** | Fixed - a train is carried across a rebuild by the square its Point was a copy of, not by the name: the build writes each Point's square on it (Point.getSquare), whereTheTrainsAre records it with the copy's facing for each train, its road and each turn owed, and AutonomySession.pointNamedNow(name, square, facing) answers the same name only where the rebuild still gives it to that square - then the same copy under the square's name now, then the copy facing the same way - and none where the square is gone.  The carry of the stored names (carry, rename) traces a name the build now gives another square from the square it named, in every configuration.  Not built: a word at the Name item that a name is another station's - the naming suffixes it, and nothing is carried wrongly now.  claims and fix 0923c08c; mutations MA1n red, MA1b red, MA1w red, MA1c red, MA1r red; and round 20's on the rewritten namer, mutations MA1f red, MA1d red. |
| **Grade** | A.  **How:** the diagram's Name item takes any name - `promptName` refuses only a name ending in a heading (`whyNotAPointName`), and `AutonomyBuilder.uniqueNames` settles collisions itself: the squares are sorted by tile key and the first to carry a name gets it plain, the next `"Name (2)"`.  So a rename can move a Point's name from one square to another in one gesture: rename the station at `main:3,1` to the name of the one at `main:5,1`, and `main:3,1` is now `Gamma` and `main:5,1` is `Gamma (2)`; or, of two stations both called `Hbf`, rename the first, and the second becomes plain `Hbf`.  Round 20's `pointNamedNow` answers **"the name itself where it is still built"** before it traces anything, and the recorded name *is* still built - on the other square.  The rebuild then stands the train recorded there on the renamed station, and `moveLocomotive` displaces whoever is already on that square (`clearBlockExcept`): the train that really stands on the renamed station is taken off the railway, and the square the other stands on reads free.  The next fold writes both.  The timetable carry (`carry`, and `rename` for the other configurations) skips a still-built name the same way (`tiles.containsKey(name)`), so a leg out of the square that lost its name now names the renamed one, fails to load, and - its Points built - is erased by the next fold.  The consequence is RSA4-A1's: the railway holds a train where it is not, the next dispatch into the square it does stand on is admitted by the model, and a dispatch of the displaced train starts from nowhere the model knows.  **Reach:** a rename to a name another station already has - swapping two stations' names from the Name item passes through exactly that state (Adam's own names run in series, `ParkingTrack4` to `ParkingTrack12`) - or any rename of one of two stations that share a name, with a train the last run left on either and no fold since.  His frozen railway has no two stations of one name today.  **Older:** the carry by name is OB-183's and RSA3-B1's; round 20 keeps it as `pointNamedNow`'s first rule, and made the two-train collision possible where before one of the two was left where the setup had it. |
| **Names** | RSA4-A1, OB-183, RSA3-B1, RSA4-B1, TDR-C11, OB-034 |
| **Where** | `AutonomySession.java:501-506` (`pointNamedNow`: a name in `squareOfNameNow` is returned as it is), `:582-597` (every name's square overwritten by the latest build to give it), `:765` and `:714-715` (`carry` and `rename` skip a name the build has); `AutonomyBuilder.java:1677-1724` (`uniqueNames`: the plain name to the first square by tile key); `AutonomyEditorPanel.java:6102-6131` (`promptName`: no refusal of a name in use); `TrainControlUI.java:6952-6995` (`putTheTrainsBack`: `built.getPoint(name)`, then `moveLocomotive`); `Layout.java:10348` (`clearBlockExcept`: the occupant displaced) |
| **Needs execution** | done - probe `rsa5ProbeP3ARenameThatMovesAName` (P3): Alpha, Beta, Gamma, Delta in a row; X placed at Alpha and Y at Delta by the setup; the run's result set on the running layout - X on `main:3,1`'s eastbound copy, Y on `main:5,1`'s; a captured leg for Y out of `main:5,1` to Delta; then the rename and its rebuild as `rebuildRunningLayoutFromSetup(true, none)` makes it (`whereTheTrainsAre`, the build, `putTheTrainsBack` with `session::pointNamedNow`).  **Control, Beta renamed Bravo:** X on `Bravo (eastbound)`, Y on `Gamma (eastbound)`; the next fold places X at `main:3,1`, Y at `main:5,1`; Y's leg kept.  **Beta renamed Gamma:** both recorded names answered `Gamma (eastbound)` - now `main:3,1`'s copy; after the rebuild **Y is held on `main:3,1` and X is nowhere**, `main:5,1` (`Gamma (2)`) holding nothing; the next fold places **X NOWHERE, Y at `main:3,1`**; **Y's leg erased** (`[0]`).  **Beta and Gamma both called Hbf, the first renamed Hbf Nord:** X's recorded `Hbf (eastbound)` is still built, now on `main:5,1`; after the rebuild Y is on `main:5,1` and **X is nowhere**, `Hbf Nord` holding nothing. |

**Direction:** trace every recorded name through the build it was recorded from - the square that build gave it - rather than asking whether the latest build still gives the name; the same for the timetable and road carry.  And say, at the Name item, when a name is already another station's, since the naming then moves names between squares.

---

### RSA5-A2 - A direction set from the diagram's menu through a station, against the facing of a train the last run left there: no copy faces its way, `pointNamedNow` answers the name unchanged, and the rebuild leaves the train where the setup last had it - the square it stands on reading free

| | |
|---|---|
| **Disposition** | Fixed - a direction that would leave a standing train on a square with no copy facing its way is refused and put back before anything is derived from it (AutonomySession.directionsTouched, trainsTheDirectionsTurn: the railway's trains, and the setup's where the railway has none of them, read against a trial reduction of the graph).  Every direction door - a click on the track, a switch's arms, the per-route radio, All branches, One-Way Run - says why in a popup that names the train and the station (autosetup.ui.errorDirectionTurnsATrain).  And where no copy faces a train's way, the carry offers none rather than one facing the other way (the copy is the direction).  claims and fix 0923c08c; mutations MA2 red, MA2r red, MA2s red, MA2p red.  Not run: a door that does not say it - the session refuses either way, and the popup is the door's only part. |
| **Grade** | A.  RSA4-A1's harm, through a gesture RSA4-A1 named (*"a direction set"*).  **How:** a station on a two-way line is two copies, `Beta (eastbound)` and `Beta (westbound)`.  A run leaves a train on the westbound copy - facing west - and nothing folds it into the setup.  The line either side is then made one-way east from the diagram's menu, at rest: Beta is one copy, `Beta`, facing east.  `pointNamedNow` finds no copy of that name, no same copy under the square's name, and no copy facing west, and returns the name as recorded; `putTheTrainsBack` finds no Point by it and `continue`s - *"left where the rebuild put it"*, which is where the setup last had the train: where the run began.  The disposition's reason for that branch is *"A train whose square is gone has no Point to go back on"*; the square is not gone, only the copy facing its way.  So the model holds the train elsewhere, Beta reads free, and the next dispatch of the train locks a route from a square it is not on.  **Folded first** - the editor, a reload - the same railway takes the build's own last fallback instead (`placementCopy` -> `startableCopy`): the train is stood on `Beta`, facing east, turned round in silence.  The two doors disagree, and neither says anything.  Only the facing that matches is claimed (`testATrainOnASquareSplitSinceIsPutBackFacingItsWay`, the other way round).  **Reach:** a direction set at rest on the rails either side of a station, against a train the last run left on it facing the other way, with no fold since.  **New in round 20**, as a gap in RSA4-A1's fix. |
| **Names** | RSA4-A1, OB-183, TDY2-A1, AUT2-A1, GUI2-B1, RSA2-C7 |
| **Where** | `AutonomySession.java:514-532` (`pointNamedNow`: no copy facing that way, the name returned as it is); `TrainControlUI.java:6964-6966` (`back == null`, `continue`), `:6821-6825` (the javadoc: only a square gone); `AutonomyBuilder.java:846-863` (`placementCopy`: no copy facing, `startableCopy`); `regression.testAnEditedPlacementSurvivesTheRebuild.testATrainOnASquareSplitSinceIsPutBackFacingItsWay` |
| **Needs execution** | done - probe `rsa5ProbeP4ADirectionSetUnderAStandingTrain` (P4): Alpha, Beta, Gamma, two-way; the train placed at Gamma (`main:5,1`) by the setup; the run's result on the running layout; then one way east on `main:2,1` and `main:4,1` (`Direction.TOWARD_A`, as the claim sets it) and its rebuild.  **Control, the train on `Beta (eastbound)`:** answered `Beta`, and put back on it.  **The train on `Beta (westbound)`:** answered `Beta (westbound)` unchanged; after the rebuild the train is **held at `Gamma`**, and `Beta` - facing E, the only copy - holds nothing.  **Folded first, then built:** the fold places it at `main:3,1` facing W; the build stands it on **`Beta`, facing E**. |

**Direction:** where no copy of the square faces the recorded way, keep the train on its square and say so - or refuse the direction while a train stands on it facing the other way; in either case the same answer at the carry and at the build.

---

### RSA5-B1 - Adam's question: the entries of a page ticked out come back only while every other stored entry loads and the visible timetable is untouched - an entry for a locomotive the database no longer has, a leg whose rail is gone, one entry deleted or one recorded, and every entry of the page is written away at the next fold

| | |
|---|---|
| **Disposition** | Fixed - a fold keeps each stored timetable entry on a page out of autonomy through whatever else happens to the timetable (AutonomySession.withTheEntriesOfPagesOut): an entry dropped by the load for another reason, one deleted, one recorded.  Each goes back after the entry its nearest earlier neighbour became, or first.  An entry is on a page out where one of its names is traced there - by the builds this session has seen, or by the name of a square on that page, its own or the one made from where it is.  Adam's question answered: they come back after a restart, a reload, Unload, Return Home, a run, an edit of the rest, and beside an entry the load dropped for its own reason.  claims and fix 0923c08c; mutations MB1k red, MB1s red. |
| **Grade** | B.  RSA4-B2's loss, through every difference the rule reads as the operator's.  **How:** `withTheEntriesItsLoadDropped` walks the stored timetable against the running one: an entry the running one has is taken from it, one naming a Point the running layout does not build is kept, and **anything else - one stored entry that loaded neither way - abandons the walk and stores the running timetable as it is**, as does a running one longer than what it matched.  A load drops an entry for other reasons than a page out of play: its locomotive not in the database (`TimetablePath.fromJSON`), or a leg with no edge between two Points that are built.  Such an entry names only built Points, so it is neither matched nor kept, and every entry of the page out goes with it - with nothing said; the log names only the entry dropped for its own reason.  The same happens on any edit while the page is out, and the operator cannot see what the edit costs: the page's entries are not in the timetable they are editing.  behaviour.md 8 says the entries are kept *"where the timetable is otherwise the one stored"*; in the first two cases it is.  **Where they come back** (measured or read): a tick out and back in (P1 a); a restart with the page out, through the exit's fold (P1 g, and Unload's fold is the same); a reload; Return Home (the loan writes the owner's timetable, and hands it back whole: `toJSON`, `requestReturnToHome`'s `finally`); a timetable executed or a delay changed (`sameRun` reads neither); a locomotive renamed, or deleted in TrainControl (the store and the running layout are repaired together); a station renamed on a page in play (carried, then rebuilt).  A station on the page out cannot be renamed from the diagram while it is out (`isIgnored`).  **Where they do not:** the four cases below, and RSA5-C1's.  **Reach:** a page ticked out holding captured entries, and - the case the brief asks about - one other entry whose locomotive the database no longer has: a locomotive deleted while another railway is loaded (the repair reaches the setup of the railway loaded), or a database restored or replaced.  **New in round 20**, as a gap in RSA4-B2's fix. |
| **Names** | RSA4-B2, RSA3-B1, ISD-B2, BPV-C12, OB-069, AD-A7 |
| **Where** | `AutonomySession.java:6046-6060` (the fold asks), `:6081-6110` (`withTheEntriesItsLoadDropped`: `return running` at `:6104` and `:6108`), `:6112-6135` (`sameRun`), `:6137-6152` (`namesAPointNotBuilt`); `TimetablePath.java:179-213` (a locomotive or an edge not found); `Layout.java:13939-13946` (the entry dropped, one log line); `TrainControlUI.java:27984` (`deleteTimetableEntry`), `Layout.java:922-940` (`addTimetableEntry`); `TrainControlUI.java:4648-4688` (the repair reaches the loaded railway's setup); `docs/reference/behaviour.md:2463-2472` |
| **Needs execution** | done - probe `rsa5ProbeP1WhereATickedOutPagesEntriesComeBack` (P1): two pages of three stations; the page `second` out, the timetable stored with an entry `Epsilon (eastbound) -> Zeta` on it, loaded; then the tick back in and the menu's reload as `load(active, true)` makes it (the fold, then the build).  **(a) Control**, `[Beta->Gamma, Epsilon->Zeta]`: loaded 1, stored 2; ticked in: **stored 2, running 2**.  **(b)** an entry for `rsa5 no such locomotive` beside them: loaded 1 (logged: *"Locomotive rsa5 no such locomotive does not exist"*, *"Edge Epsilon (eastbound) -> Zeta does not exist"*); ticked in: **stored 1, running 1 - the entry on `second` gone**.  **(c)** an entry `Alpha -> Gamma` (both built, no edge) beside them: the same - **stored 1**.  **(d)** a third entry `Beta (westbound) -> Alpha`, deleted from the running timetable as `deleteTimetableEntry` does: **stored 1**.  **(e)** one entry recorded into the running timetable: **stored 3, the entry on `second` gone**.  **(g)** the exit's fold, a new session with the page still out, then the tick: **stored 2, running 2**. |

**Direction:** keep a stored entry the running layout cannot build whatever else differs - splice it back in by its neighbours - and leave the operator's changes to the entries they can see; or keep the page's entries apart from the timetable while the page is out.

---

### RSA5-B2 - A page renamed, or a tile moved in the track diagram, renames every Point on it that has no name of its own, and nothing carries that into a timetable leg or a train's road: the load drops them, and a timetable leg through such a sensor - the one entry on Adam's frozen railway has one - is lost to the operator

| | |
|---|---|
| **Disposition** | Fixed - a page renamed, or a square moved on its page, carries the names made from it (page x,y) into every configuration's timetable legs and roads (AutonomyCompanionStore.renamePage, moveTiles, renameStoredPoints), as the store carries its square keys - with a session or on disk (renamePageOnDisk goes through renamePage).  claims and fix 0923c08c; mutations MB2p red, MB2m red, MB2r red. |
| **Grade** | B.  RSA3-B1's loss, through the renames it never covered.  **How:** a square with no name of its own is named after its coordinates, *page x,y* (`GraphReducer.generatedName`), and a timetable leg or a road step is an edge between Points - every sensor on the way, not only the stations - so a captured path through the unnamed sensors between two stations names them.  A page renamed rekeys everything the setup holds by square (`AutonomyCompanionStore.renamePage`) and nothing it holds by name; the session is then reset (`markPagesStale`, `layoutRefreshCompleteInternal`), so the carry's memory of the builds it has seen is gone, and the fallback for a name written before the session traces it only to a square still called by its base - which `main 3,1` no longer is.  So the next load drops every leg through the page's unnamed sensors, one log line each; the road is dropped whole and erased by the next fold; and since round 20 the entry is kept, unloadable, for ever (RSA5-C1).  A tile moved in the track diagram does the same: `moveTiles` moves the square-keyed setup and not the names, and a sensor moved takes a new name - or another sensor's old one, which the fallback then traces to the wrong square.  behaviour.md 8 says *"A Point's name is carried wherever a configuration stores it"*.  **Reach:** the frozen copy of Adam's railway (`test/layouts/live-snapshot`, 2026-09-23) holds one captured timetable entry, and its path runs `BottomInner (northbound) -> 1 - Main 12,7 -> Tunnel (southbound) -> ...`; a rename of the page `1 - Main`, or a diagram edit that moves that sensor, loses it.  OB-306 reasons that a page rename is safe for trains because *"only a station with no name of its own is renamed with its page"* - true of where trains stand, and not of the paths they were sent along.  **Older** (the page rename and the moves predate the carry); found asking round 20's question. |
| **Names** | RSA3-B1, ISD-B2, OB-306, RLV7-C3, OB-092, RSA4-C4, RSA4-B2 |
| **Where** | `GraphReducer.java:962-965` (`generatedName`); `AutonomyCompanionStore.java:2798-2865` (`renamePage`: tile keys only), `:3045-3130` (`moveTiles`: the same); `LayoutPageEdit.java` `renameOrDuplicate` (the store renamed, `markPagesStale`); `TrainControlUI.java:23994-24029` (the session reset, the load); `AutonomySession.java:679-702` (`squareCalled`), `:765-782` (`carry`: a name not traced is left); `Layout.java:13939-13946`, `:7557` (`roadNamed`: all or nothing); `docs/reference/behaviour.md:2463`; `test/layouts/live-snapshot/config/autonomy/configuration-Main.json` (the entry) |
| **Needs execution** | done - probe `rsa5ProbeP2APageRenameAndTheNamesStored` (P2): Alpha, a sensor at `3,1` that is no station, Gamma, Delta; an entry `Gamma (westbound) -> <middle> (westbound) -> Alpha`, and a train at Alpha with that road; then the page `main` renamed `yard` as `renameOrDuplicate` makes it (the store renamed, the session marked stale and written), a new session over the renamed page, the load, the next fold.  **Control, the middle sensor named Mid:** loaded 1 entry, a road of 2 rails; after the rename **1 entry, a road of 2**; kept by the fold.  **The middle sensor unnamed** (`main 3,1`): after the rename the Points are `yard 3,1 (eastbound)`/`(westbound)`; **0 entries loaded** (*"Edge Gamma (westbound) -> main 3,1 (westbound) does not exist"*), **road null**; after the next fold the entry is still stored, naming `main 3,1`, and **the road is gone**. |

**Direction:** carry a page rename into every stored name that begins with the page's name, and a tile move into every name made from the moved square's coordinates, in every configuration - the timetable legs and the roads - as the store carries its square keys.

---

### RSA5-C1 - An entry no load can read again is now kept for ever, unseen and undeletable, and a timetable cleared while every entry of it is on a page out comes back with the page

| | |
|---|---|
| **Disposition** | Fixed - only the entries a page out of autonomy explains are kept; any other entry a load could not read - its station gone, its locomotive gone - is erased by the next fold, as before round 20.  Clear empties the configuration's timetable at once, so a page's kept entries go with the rest.  Round 20's claim for a timetable cleared while a page is out (testATimetableClearedWhileAPageIsOutStaysCleared) is replaced by the Clear door's.  claims and fix 0923c08c; mutations MC1o red, MC1c red. |
| **Grade** | C.  **How:** the fold keeps *every* stored entry naming a Point the running layout does not build - not only one whose page is out of play.  A station removed from the diagram, a page renamed (RSA5-B2), an import naming another setup's Points: before round 20 the load dropped such an entry and the next fold erased it, which RSA3-B1's disposition described (*"the load drops that entry and logs it as before"*); now each fold keeps it.  It is in no timetable the operator can see, so no Delete reaches it; it is logged whenever a load drops it; and if a later edit builds its names again - a page given its old name back, a sensor put back at those coordinates - it loads again, over whatever that track then is.  And the rule cannot tell a cleared timetable from one whose every entry was on the page out: both run empty, so Clear pressed then is undone when the page comes back - where RSA4-B2's disposition, behaviour.md 8 and the round's claim say a timetable cleared meanwhile *"is stored as it is"* (the claim clears with an entry on the page in play beside it).  **New in round 20.** |
| **Names** | RSA4-B2, RSA3-B1, BPV-C12 |
| **Where** | `AutonomySession.java:6081-6110`, `:6137-6152`; `core.testAutonomyDiagramSession.testATimetableClearedWhileAPageIsOutStaysCleared`; `docs/reference/behaviour.md:2468-2471` |
| **Needs execution** | done - probe P1.  **(h)** Zeta's square removed from the page while it was out: ticked in, the entry does not load (running 1) and stays stored (2); **two more reloads: still stored 2, running 1**.  **(f)** the timetable `[Epsilon->Zeta]` only, the page out, loaded (0), cleared: ticked in, **stored 1, running 1**. |

**Direction:** keep only what a page out of play explains - a Point on a page excluded now - and let the fold erase the rest, as before; and keep the page's entries apart from what Clear clears, or say what comes back.

---

### RSA5-C2 - The turns the railway owes are carried across a rebuild by the Point name the train turned at, which `pointNamedNow` does not reach: after a rename, a pending turn is never written and never forgotten

| | |
|---|---|
| **Disposition** | Fixed - the turns owed are carried across a rebuild by the square and facing of the Point each was made at (takeThePendingTurns records them, putThePendingTurnsBack asks pointNamedNow); where the rebuild has no such copy the name recorded stays, owed as before.  claims and fix 0923c08c; mutation MC2 red. |
| **Grade** | C.  **How:** every rebuild drains the turns not yet written (`takeThePendingTurns`, locomotive name to Point name) and puts them back afterwards (`putThePendingTurnsBack` -> `restoreReversalsOnArrival`), by the recorded name.  Round 20 carries the trains and their roads to the names the rebuild gives (`namedNow`) and not the turns.  After a rename `writeTheTurns` finds no Point by the recorded name, `faceTheWayItCameIn` answers null for a null Point, and the turn is kept to try again - at every idle refresh, never writable - while the train stands on the arrival copy, facing the way it came in: OB-189's wrong heading for the next dispatch.  **Reach:** small - a turn is written at the first idle refresh after the run, so one is still pending at a rename only where that write failed.  **Older** (the drain and restore by name are D3-C5's); found by the brief's list of stored Point names. |
| **Names** | D3-C5, RGD-C7, OB-189, RSA4-A1 |
| **Where** | `TrainControlUI.java:6791-6816` (`takeThePendingTurns`, `putThePendingTurnsBack`), `:7217`, `:7249`, `:3071`, `:3092` (both carries), `:8011-8060` (`writeTheTurns`: kept when unwritten); `AutonomySession.java:2578-2581` (`faceTheWayItCameIn`: null for a null Point); `Layout.java:4234-4245` |
| **Needs execution** | no - by reading |

**Direction:** carry the pending turns with the trains, through `pointNamedNow`.

---

### RSA5-D1 - RSA4-C1: a timetable's own loop counted while it dispatches, and its pause woken by every stop

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RSA4-C1, RSA3-C5 |
| **Where** | `Layout.java:6877-6880`, `:6899-6912`, `:7111`, `:7115-7118`, `:6771-6781`, `:2341-2375`, `:7133-7144` |
| **Needs execution** | done - the round's claim green on the archive (baseline); the rest by reading |

The loop is counted from just before its first entry until it leaves, uncounted in a `finally` before its completion wait (which asks `isRunning` and so must not count it); `pacedWait` now waits on `entryPause`, which every stop notifies after clearing `running`, and is called only inside `while (this.running ...)`, so a zero delay cannot spin.  Every other pause a timetable makes - an entry's retry pause, the completion poll - is counted or exits on `isRunning`.  The loop is counted from after its start's log line rather than from its start: a stop and a Start inside those three statements would reach RSA4-C1 again, which no hand can do.

---

### RSA5-D2 - RSA4-C2: the Yes and a timetable starting held apart by `stopLock`

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RSA4-C2, RSA3-C4, RSA2-C1, RSA2-C2 |
| **Where** | `Layout.java:2204-2221`, `:6836-6846`, `:2341-2351`, `:2396-2401`; `TrainControlUI.java:24787`, `:24869`, `:27651-27652` |
| **Needs execution** | done - the round's claim green on the archive (baseline); the rest by reading |

Only the Yes and `executeTimetableInternal` take `stopLock`, and inside it neither does more than write `running`, read the count and `stopLocomotives` (which takes `entryPause` only to notify); nothing takes `stopLock` while holding `entryPause`, so there is no cycle, and the event thread waits on no journey.  The other write of `running = true`, `runLocomotives`, reads no count by design (autonomy's thread reads it before its choice and `running` after).  Return Home counts at its press; Execute Timetable's worker reads the count as it starts, a thread hop after the press.

---

### RSA5-D3 - RSA4-C3: every reader of one train's path reads the claims first

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RSA4-C3, RSA3-C1, RC-A10, VD12-B3 |
| **Where** | `Layout.java:6325-6330`, `:1066-1072`, `:1818`, `:5270`, `:6308`, `:7995`, `:4293-4319`, `:9274-9278`, `:1643-1661` |
| **Needs execution** | done - the round's claims green on the archive (baseline); the rest by reading |

A claim is added only under the railway's monitor after `isAlreadyUnderway` refuses a train already out, so one train is in both maps only across its hand-over, with the same path; `pathHeldBy` reads the claim first, and a train missing from it is among the journeys.  `getActiveAccs` copies the claims and lets a journey win.  `getLastPointsReached` already read the claims first.  `getPossiblePaths` reads the journeys alone, under the monitor: a train still claiming is offered paths in the Auto tab, and a dispatch of it is refused as already under way.

---

### RSA5-D4 - RSA4-C5: Import refused while autonomy runs, before it asks for a file

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RSA4-C5, RLU4-C1, RLD-C3 |
| **Where** | `AutonomyViewerPanel.java:1140-1152`; `AutonomyMenu.java:309`, `:365`; `AutonomyViewerPanel.java:428` |
| **Needs execution** | done - the round's claim green on the archive (baseline); the rest by reading |

Every Import door calls `importConfiguration`, which asks `isAutonomyBusy` first; the chooser, the name and the confirmation after it are modal, so no door starts a run meanwhile.

---

### RSA5-D5 - RSA4-C6: the sensor rule and the Yes written as built

| | |
|---|---|
| **Disposition** | Checked - clean, apart from behaviour.md 8's name-carry paragraph (RSA5-A1, B1, B2, C1). |
| **Grade** | D |
| **Names** | RSA4-C6, RSA2-B1, RSA3-C3, RSA-C1 |
| **Where** | `docs/reference/behaviour.md:1690-1699`, `:2474-2481`; `docs/reference/open-questions.md:247-253`; `Layout.java:2244-2285`, `:2204-2221` |
| **Needs execution** | no - by reading |

5d's sensor held until the rail is given back, the claims read first, and 8's account of the Yes, the loop and the count match the code; the decided entry in open-questions.md states the rule built.

---

### RSA5-D6 - RSA4-A1 where the names stay on their squares: a plain rename, a square split since, turning and must-turn copies, a station with no facing

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RSA5-A1 (a name moved to another square) and A2 (no copy facing its way). |
| **Grade** | D |
| **Names** | RSA4-A1, OB-183, RLV6-B1 |
| **Where** | `AutonomySession.java:501-532`, `:555-599`; `TrainControlUI.java:6834-6844`, `:6894-6950`, `:3067-3096`, `:7198-7226`; `AutonomyBuilder.java:646-723`, `:1603-1638` |
| **Needs execution** | done - the round's claims green on the archive (baseline), P3's and P4's controls; the rest by reading |

Both doors that carry trains across a load - the setup gesture's rebuild and `carryTheTrainsAcross` - go through `pointNamedNow`, with the roads; the doors that fold first (the editor, a reload, the page and diagram resets) need no carry.  A name no longer built goes to the same copy under the square's new name, or to the copy facing the same way, the plain before the turning: a one-copy station given turning copies lands on the plain copy facing its way, a must-turn station on the turning copy facing its way.

---

### RSA5-D7 - RSA4-B1 and C4: every configuration's timetable and roads carried across a rename

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RSA5-A1 (a name moved to another square) and B2 (a page rename, a tile moved). |
| **Grade** | D |
| **Names** | RSA4-B1, RSA4-C4, RSA3-B1 |
| **Where** | `AutonomySession.java:555-599`, `:629-671`, `:704-747`, `:754-848` |
| **Needs execution** | done - the round's claims green on the archive (baseline), P2's control; the rest by reading |

Roads are stored as JSON arrays of name pairs and read as such; the configuration in use is carried by its build, every other across a renamed square alone, and a name only another configuration builds is remembered by its square when first seen; a name on a page out of the build is left for the build that has it.

---

### RSA5-D8 - The other stored Point names: homes, exclusions, `blockedBy`, locks

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | OB-282, AMW-B2 |
| **Where** | `AutonomySession.java:3655-3668`; `AutonomyBuilder.java:1130`; `AutonomyCompanionStore.java` (the keys it writes) |
| **Needs execution** | no - by reading |

Homes and exclusions are stored per square, with locomotive names as values; `blockedBy` is emitted by each build from the setup; no lock is stored.  The only stored Point names are the timetable's legs, the roads (both carried) and the pending turns (RSA5-C2).

---

### RSA5-D9 - HomeStaging's `canEnter`, `blockedSensors` and the tail helpers

| | |
|---|---|
| **Disposition** | Checked - clean, as far as read. |
| **Grade** | D |
| **Names** | SG-A4, AMH-B2, AMW-B2, TDD-A1, OB-184, AUT2-C2 |
| **Where** | `HomeStaging.java:1614-1787`, `:1796-1865`, `:1893-1971` |
| **Needs execution** | no - by reading |

`canEnter` refuses an occupied point, a blocked sensor, any sibling on the same sensor (the mover not excepted), and a non-station a locomotive is excluded from; `blockedSensors` frees a sensor only when every tail accounting for it has moved and only at the end a tail reached; the tail helpers ask every train lying across an edge and whether it is still where it started.  Each errs toward refusing, and each leg is asked of `isPathClear` again as it runs.

---

### RSA5-D10 - The probes' controls, and the baseline

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | - |
| **Where** | `validate30/rsa5/p1/test/regression/rsa5Probes.java`; `validate30/rsa5/p1/rsa5-out/`; `validate30/rsa5/logs/` |
| **Needs execution** | done |

Each probe states its precondition before its result, and each measured defect has a control beside it that answers the other way: P1 (a) and (g) for B1; P2's named middle sensor for B2; P3's rename to a new name for A1; P4's east-facing train for A2.  The baseline: 13 and 2 run, 0 failures; the probe jobs 4 and 4 run, 0 failures; every job exited 0, and none hung, ran out of memory or was ended by the runner.
