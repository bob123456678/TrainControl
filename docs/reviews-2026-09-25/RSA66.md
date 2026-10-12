# RSA66 - Validation of rounds 137-140 (`98c1072f..afa263f5`, at `6c4caaa7`), round 141 (`5526ba19`, `62c9b06d`, at `e603a2ff`) and the documentation since `adf3f4e4`: a route may set a held switch to the setting its path holds and only a change is refused, at all three doors, on both drives of a three-way, and where two paths disagree every command is refused; a station's list is read by its square, so its barred side lets the trains it keeps out pass; an old file leaves an allow-only list alone; the train's line over its own run line is now the orange of a standing train's; the diagram and every pop-up step half a square - but round 137's allowance stops at a signal that guards the path's own platform (C1); behaviour.md 7a does not record the rule (C2); round 141's walk counts as "a way to reverse in" a route no door drives and not the one Return Home does drive (C3); AutomationAPI.md says Return Home insists on backing a non-reversible train in, which it has not done since 4 September (C4); and a standing train's own square draws its line twice, a lighter orange than the rest of the train (C5, older, recoloured by round 139) - CONVERGED (TrainControl 3.0.0)

**Status:** open

**Prefix:** RSA66

**Reviewed:** branch `autonomy-diagram-r0` at `6c4caaa7` (*Tracker: Adam's MT-727 Works validated, his note answered by round 140; MT-728 (round 139) and MT-729 (round 140); the Readme's scroll line; the live layout*), the code being `route-stop-only`'s `afa263f5`: `git log 98c1072f..afa263f5` - round 137 (`7c530780`, `0e96ccd3`), round 138 (`bb3108ab`, `d7a7d80d`), the RSA65 record (`47fbc5d4`), round 139 (`1caa0191`, `d3526ef8`), round 140 (`d1756364`, `afa263f5`); and `git diff adf3f4e4 6c4caaa7 -- Readme.md Automation.md AutomationAPI.md docs/reference`.  **Added mid-round by the coordinator:** round 141 (OB-336) - `8cde6cb8`, `5526ba19`, `62c9b06d`, merged at `e603a2ff`, `git diff afa263f5 62c9b06d -- src test`.  Read from `git archive 6c4caaa7` (`rsa66/d/`), `git archive e603a2ff` (`rsa66/e/`) and, for a control, `git archive 98c1072f` (`rsa66/p/`), `cs2_sample_layout` excluded, each with an empty `cs2_sample_layout` folder for one.sh's guard; none carries `LocDB.data` or `UIState.data`, and none was copied.  2026-10-11 18:49 to 19:56.

## Method

**Read whole:** the brief and the coordinator's addition; `validate30/RSA65.md`; every hunk of `git diff 98c1072f afa263f5` over `src` (341 lines) and `test` (570), of `git diff afa263f5 62c9b06d -- src test` (262), and of the documentation diff (81).  **At the commit:** `MarklinRoute.conflictingAccessoryAndReason`, `accessoryHeldByAutonomy`, `heldReason` (`:585-660`), `execRoute`'s command loop (`:717-1000`, the per-command check, the s88 door's skip, the midway question); `Layout.getActiveAccs`/`getActiveAccSettings` (`:1083-1194`), `protectsAnOccupiedSquare`, `protectsAStandingTrain`, `isProtectedBy`, `clearsProtection` (`:11987-12119`), `configureAndLockPath`'s validation gate and `validatePathActuation` (`:4845-4990`), the started callback (`:10225-10250`), `Edge.isLockHeld`; the other two readers of `getActiveAccs` - the diagram tile's click (`LayoutLabel` `:479-560`) and the switch keyboard (`TrainControlUI.keyboardAutonomyAllows`, `:24934`); the doors (`TrainControlUI.askAboutRouteConflict`, `executeRoute`, `confirmRouteConflictMidway`, the route tile `LayoutLabel` `:677`); `Accessory.isThrow`, `MarklinControlStation.getAccessoryByName`/`getAccessoryByAddressIfPresent`/`setAccessoryState`, `TilePorts` (a three-way commands both drives).  Round 138: `Layout.isAStationSquare` (`:9587`), `isABarredCopyOfAStation`, `isPathClear` (`:3123-3200`), every reader of `Point.excludes` (`Layout` `:3166, 5595, 5958, 6455, 6604, 6696`; `HomeStaging` `:810, 1947, 2254, 2643`; `AutoLocomotiveStatus.notChosenByAutonomy`) and every reader of `isDestination()` in `automation`, `automationui` and `gui` for the list question; `AutonomyBuilder`'s copies (`stops`, `blockFor`, the turning-copy flags `:1299`); `AutonomySession.importLegacy` (`:1932-1990`), `AutonomyChecks.NO_ARRIVALS_LEFT`.  Round 139: `LayoutLabel.paintComponent` (`:1515-1610`), `paintCoveredMark` (`:1913-1985`), `TailColour`, `DiagramColours`; `TileOverlay.isBlank`, `paint`, `paintTrain`; `DiagramMonitor.compute`, `lay`, `markTheParkedTrains`, `markTrain`; `TrainControlUI.refreshCoveredTrack`.  Round 140: `LayoutGrid.scrollHalfASquareAtATime`, its two callers.  Round 141: `AutonomySession.berthsATrainCanBackInto` (`e` `:5309`) and its call (`:7739`), `AutonomyChecks.checkHomesThatNeedReversing` (`e` `:1125`), `HomeStaging`'s move rules (`:1040-1175`, `turnsATrainArrivingAt`, `turnedOnTheWay`, the `mustBackIn` history `:1295`, `:2659`).  behaviour.md 1, 3, 6, 7a, 7c and the import paragraph; Automation.md and AutomationAPI.md on routes, exclusions, termini and Return Home; the Readme's 3.0.0 lines the documentation diff touched.  Each finding was searched for in the archive's `docs/manual-tests/findings.tsv` (5,595 lines): none is there.

**Runs.**  Every JVM through the archive's own `docs/tools/one.sh`, `TC_SCRATCH` a Windows-form folder per job under `rsa66/w/scratch`, the JVM's temporary folder `rsa66/w/tmp`, started by `rsa66/w/runjob.sh` (RSA65's runner re-pointed: it waits until no java.exe/javaw.exe naming testng, TestNG, batteryRun, anyReceivePort, one-files or battery-files, no javac.exe and no battery lock have been seen in two checks 20 s apart - Adam's own TrainControl is not waited for - polls once a minute and never kills anything), one job at a time.  One job (the guard probe's second try - the first had stopped at a fixture error of mine) waited from 19:07 to 19:22 while another session's test JVMs ran; nothing of mine overlapped another test JVM, and one.sh never refused.  Model probes headless; window probes with this machine's display (125 per cent).  No Central Station was reached; nothing hung, ran out of memory or tripped the layout guard.  Jobs ran 19:07:18 to 19:55:36.

**Scratch classes** (`rsa66/w/probes/`, copied into the archives' `test/`; nothing asserted; output in `rsa66/w/out/<archive>/`):

- **`regression.rsa66Guard`** (round 137) - inside a real dispatch (the started callback, path locked and train registered, as the claim asks): a path holding a turnout STRAIGHT, a turnout TURN, a three-way's drives TURN and STRAIGHT, a signal GREEN guarding the square the train stands on, a signal GREEN guarding the path's destination, a signal GREEN guarding an empty square off the path; routes asked through `conflictingAccessoryAndReason` for the held setting and the other; a route mixing the held setting with a change, asked and then fired at the s88 door (`execRoute(true)`); and the null case - two further claims in `takingPath` over one accessory at opposite settings, made by reflection.
- **`core.rsa66Excl`** (round 138) - lines built by the setup's own builder (Alpha - Beta - Gamma - Delta): Beta a station barred from W, from E, from both, a plain sensor, unbarred; blacklist and allow-only lists; each asked of `isPathClear`, Return Home's `canEnter`, `getPossiblePaths`, the hand menu's `isOfferableToOperator` and `explainDestinations(loc, true)`, and the setup check; a Java-API railway with no blocks; the legacy import into allow-only with a list, allow-only with none, the mode written `Whitelist`, no mode, each imported twice.
- **`core.rsa66Live`** - Adam's frozen railway (`live-snapshot`, its locomotives made by name): every copy carrying a list and `isAStationSquare`'s answer (by reflection, so the class compiles at `98c1072f` too); `getPossiblePaths` for every locomotive, 20 times a block, eight blocks, run alternately at `6c4caaa7` and `98c1072f`; `isAStationSquare` 100,000 times.
- **`ui.rsa66Window`** (rounds 139, 140) - a real main window over the frozen railway, its four trains made by name, three units long: (S) the steps at Small, Medium, Large and back through the page cache, a notch of the wheel with and without Shift, an arrow press, and the S, M and L pop-ups; (T) at rest at each size, every square a train's line lies on - the overlay the diagram gives it and the oranges its tile paints - with the soft orange and the coaches, and a picture of the diagram; (R) one train sent along a 30-odd-square path in simulation, every 300 ms each covered square's run state and oranges, and pictures.  Run again at `98c1072f` (T only) and with `-Dflatlaf.uiScale=1.5` (S and T).
- **`core.rsa66Berth`** (round 141, in `e`) - the claim's own line, B (a parking berth every train turns at, home of a locomotive that cannot reverse) - X - R, with R a plain sensor, a reversing point, an active terminus station, another berth, a reversing point out of service, behind closed track, behind one-way track either way, and a may-turn square: the warning, the walk's answer (by reflection), and, from each copy of X, what Return Home plans and what the hand menu offers; then the frozen railway: the walk's set, the homes warned, the turning copies, the walk's cost.
- **Claim copies:** `regression.rsa66RouteClaims` and `ui.rsa66TailClaims` - `testARouteDoesNotThrowSwitchesUnderATrain` and `testTheTrainIsShownAsALine` at the commit, unchanged but for one line making their locomotives by name after `init`.

**Ran:** the probes above; `regression.rsa66RouteClaims` **18 of 18** (round 137's `testARouteMaySetTheSettingThePathAlreadyHolds`); `core.testLocomotiveExclusions` round 138's two claims **2 of 2**; `ui.rsa66TailClaims` **12 of 12** (round 139's `testARunningTrainIsTheOrangeOfAStandingOne`); `ui.testDiagramLooksRight` **55 of 57** (round 140's two scroll claims green), the two red `testARealPathToACurvedStationIsDrawn` and `testEveryPixelOfARunLandsOnTrack` with *"Index: 0, Size: 0"* - no locomotive in the archive's database, as RSA60 to RSA65 found; `core.testLayoutTiles` 12 of 12; `core.testAutonomyDiagramMonitor` 67 of 67; in `e`: `core.testAutonomyDiagramSession` **221 of 221** (round 141's `testAHomeIsWarnedAboutTurningOnlyAtABerthNoTrainCanBackInto`), `core.testASecondImportFillsGapsAndDoesNotOverwrite` 5 of 5, `core.testHomeStaging` 115 of 115, `regression.testTheImportDoorReadsAnOldFile` 12 of 42 with a display (skipped whole headless) - the same 30 red at `98c1072f` (D10).  `ui.testTheGreyAppearsAtIdleToo` (seen once) not met; `testARetiredRailwaysJourneyEndsWithItsRailway` not in my scope.

**Not reached:** a real 150 per cent display - emulated only through FlatLaf's own scale, which leaves the diagram pixel for pixel as it was (D5); the exclusions dialog as a real modal and a locomotive deleted through the window's door (RSA65's two, neither touched by rounds 137-141: read, not driven); Path Integrity Validation on a real railway (the simulation skips it - read); a real Central Station's echo.

**Nothing written** outside `validate30/rsa66/` and this file, apart from what the runner makes (the isolated preference node and data folder per run, which one.sh removes).  Nothing of mine called `saveState`.  **The real files:** `C:/Users/adamo`'s `LocDB.data` (`6ef569398873b0de`) and `UIState.data` (`9e32894e3bcfe28a`) unchanged byte for byte and time stamp from 18:49 to 19:55 (`rsa66/w/real-hashes-before.txt`, `-after.txt`).  The main checkout's pair was written at 19:53:18 - `UIState.data` the same bytes (`2619d589fcfa2345`), `LocDB.data` `bd7e96b317743f72` to `f7c66b9f50aacb4a` - between two of my jobs (one ended 19:53:02, the next began 19:53:24), and every job of mine ran on its own isolated data folder in an archive that has no `LocDB.data`; the folder's only other user is Adam's TrainControl (seen at 18:49, the main checkout's `build\classes`), and at 19:55 no Java process was running at all - his application closing.  The main checkout touched only through `git -C <main checkout> archive / log / show / diff` and to hash and stat the two files; `tc-30`, `tc-fr121`, `r34fix` and `bat66` neither read nor run.  **Running:** no JVM, runner, queue, monitor or loop of mine is left (19:55: no java.exe, javaw.exe or javac.exe on the machine, no battery lock).  Nothing was refused by the permission system.

**Counts:** 0 A, 0 B, 5 C, 10 D.

**This round has CONVERGED: no A and no B.**  **What rounds 137-141 got right** (D1-D10): a route may now set a held switch the way its path holds it and only a change is refused, at every door and on both drives of a three-way, and where two paths disagree every command on the accessory is refused (D1); no race is widened - the setting is read with the set, per command, immediately before it goes out (D2); a station's list is read by its square, so a barred side passes the trains it keeps out and an allow-only list no longer walls off every other train, at every door that reads it, for no measurable cost (D3); an old file leaves an allow-only square's list alone (D4); the diagram and every pop-up step half a square at every size, through the page cache (D5); a running train's line over its own run line is now the orange of a standing one's, at every size and on every kind of square the run met (D6); the documentation's changed lines are right (D7); round 141 removes Adam's BottomMainC warning, honours closed, one-way and out-of-service track, and costs nothing per check (D8); every claim of the classes the rounds touch is green but for the archive's environmental reds (D9, D10).  **What remains, all C:** a signal a locked path holds GREEN is still refused to a route setting it GREEN wherever it guards the path's own start or destination - the exit and home signals of 7c (C1, round 137 incomplete); behaviour.md 7a still says a switch under a train is refused (C2, new in round 137); round 141's walk counts a turn at a plain reversing point passed on the way, which neither Return Home nor the hand menu drives, and not a rest at a terminus, which Return Home does plan - so the warning is silent on the claim's own fixture and shown where Return Home backs the train in (C3, new in round 141); AutomationAPI.md says Return Home insists on backing a non-reversible train into a terminus home (C4, older, since 2026-09-04); and a standing train's own square is drawn twice, its line a lighter orange than the rest of the train (C5, older, recoloured by round 139).

---

### RSA66-C1 - Round 137's allowance stops at a signal: a route setting a signal to the GREEN a locked path holds it at is still refused - asked at the Routes tab and the route square, skipped at the s88 door - wherever that signal guards a square the path's own train stands on or reserves, which is the exit guard a departing train's path turns green and the home signal a path's last edge turns green (7c)

| | |
|---|---|
| **Disposition** | Fixed in round 142 (`7873e860`), as the finding suggests: `MarklinRoute.heldReason` asks the protecting-signal rule only of a GREEN no locked path already holds - `regression.testARouteDoesNotThrowSwitchesUnderATrain.testARouteMaySetAGuardTheGreenItsPathHolds` (the control: a guard no path holds, over the platform the train is bound for, still refused), seen red first.  The fix-validation fanout (2026-10-11, one reader a fix) found it wide: one signal may guard two platforms, and protection may have set it red for a train standing beside another path's, so GREEN is a change there.  Round 143 (`dfe817f3`) lets a held GREEN through only while the guard shows it - the same claim's third case, seen red first; mutation CA red.  Left as it is: the diagram's tile click asks rather than refuses, for any accessory a path holds, as before round 137. |
| **Grade** | C - a refusal of a command that changes nothing; no harm, and the operator can say OK at a human door.  But the rule the brief records is general - *"A route may set an accessory a locked path holds to the setting the path holds it at ... only a change is refused"* (Adam: *"make sure the code allows switching to the correct (same) setting that autonomy is using"*) - and the guards are exactly the signals a path holds GREEN on its own platform. |
| **Names** | round 137, SVN-B16 (the protecting-signal half), GSR-C2 (the reserved-platform sentence), RSA41-C2/C3 and behaviour.md 7c (a route over a guard sets it GREEN), Adam's ruling that signals are optional |
| **Where** | `MarklinRoute.heldReason` (`:628`, the same-setting check, then `:648`): `if (!rc.getSetting() && railway.protectsAnOccupiedSquare(accessory))` is asked whether or not the path holds the signal GREEN; `Layout.protectsAnOccupiedSquare` (`:12023`) counts any Point with a current locomotive, and a locked path reserves every Point along it (`Point.reserve`) - its start, where the train stands, and its destination. |
| **Inputs** | a path whose config commands hold a signal GREEN, the signal being a protecting signal of the path's start or destination (an exit guard on the way out, or a home signal on the way in, as 7c describes); a route commanding that signal GREEN. |
| **Needs execution** | done - **`regression.rsa66Guard`** (`w/out/d/G-guard.txt`), inside the dispatch: *"SG held GREEN, guards R66_A where the train stands, route GREEN: [Switch 145, route.refusedSignalProtectingOccupiedPlatform]"*; *"SG2 held GREEN, guards R66_B the destination, route GREEN: [Switch 146, route.refusedSignalProtectingReservedPlatform]"*; the control, a signal held GREEN guarding an empty square off the path: *"null (not refused, not asked)"*.  **New or older:** refused before round 137 too (as a held accessory); round 137 lets the same GREEN through the first check and the second refuses it.  **One way to settle it:** skip the protection question for a command equal to the setting a locked path holds (`locked.get(accessory)`), or put it to Adam whether the guards are meant to be outside his rule. |

---

### RSA66-C2 - New in round 137: behaviour.md 7a still reads *"Only the switch under the train is refused"* and *"throwing one there moves metal under a train"*, with no word of Adam's same-setting rule - a switch a path holds is now refused only to a command that would change it, and every command is refused where two paths disagree

| | |
|---|---|
| **Disposition** | Fixed in behaviour.md 7a (`ffbb4a39`): *"The setting the train's path already holds is not a change"*, with Adam's words, the guards (C1) and the two-paths case. |
| **Grade** | C - the written rule no longer says what the code does (the brief: *"Where the code and behaviour.md disagree, that is a finding either way"*); the user guides are right (Automation.md's *"setting it the way that train's route already has it is fine"*, the Readme's *"no longer changes a switch"*). |
| **Names** | round 137, `0e96ccd3`, `a5be719f` (the guides), BCR-A1 (7a's "under a train") |
| **Where** | `docs/reference/behaviour.md` 7a `:2475-2492`; `git diff 98c1072f 6c4caaa7 -- docs/reference` touches only the finding count and the Inbox count. |
| **Needs execution** | read. |

---

### RSA66-C3 - New in round 141: the walk reads *"a way to reverse in"* as a route on through a reversing point, which no door drives, and not as a rest at a terminus, which is how Return Home backs a train that cannot reverse into its berth - so on the claim's own line the warning is silenced where nothing can back the train in, and shown where Return Home does

| | |
|---|---|
| **Disposition** | Fixed in round 142 (`7873e860`): `AutonomySession.berthsATrainCanBackInto` reads a way to reverse in as Return Home does it - another terminus leading straight to the berth, not through a terminus or a reversing point (Adam's two-step parking, AMH-B1); a reversing point passed on the way is no way in.  The claim `core.testAutonomyDiagramSession.testAHomeIsWarnedAboutTurningOnlyAtABerthNoTrainCanBackInto` now has R a reversing point (warned) and R a terminus (not), seen red first; mutations BA-BB, BD-BF red.  Unpinned: mutation BC (walk on through a reversing point) survives - only a route doubling back through a reversing point toward a berth, at a junction, can tell, and the fixture tried (`platformBehindASwitch`) builds no edge into the berth at all, so that claim was withdrawn.  The fix-validation fanout (2026-10-11, one reader a fix) found round 142 had dropped the reversing point passed on the way (MT-245's S - reversing M - terminus T, which Return Home drives).  Round 143 (`dfe817f3`) counts the turns on the way: a train rested on a terminus sets off turned, each reversing point it passes turns it again, and an odd count at another terminus brings it in tail first; never through a terminus or a square out of service, and never straight back from a reversing point to the square before it (a stub).  `core.testAutonomyDiagramSession.testAWayToBackInCountsTheTurnsOnTheWay`, seen red first; mutations CB-CF red. |
| **Grade** | C - an advisory warning shown and withheld the wrong way round on simple lines; nothing is driven differently, berths were never warned before round 141 (MT-552), and on the frozen railway no home is warned either way.  It contradicts Adam's wording (*"The warning should only be shown for a parking spot (terminus, not chooseable by autonomy) without a way to reverse in"*) in both directions. |
| **Names** | round 141, OB-336, MT-552, MT-245, AMH-B1 and AMV-B1 (Adam: *"It should be the first option, but the locking mechanism will refuse it.  That's why we started the 2 step process for parking, which is OK in my opinion."*), ACC-C1 (the warning is the only protection left), C4 |
| **Where** | `AutonomySession.berthsATrainCanBackInto` (`e` `:5309`): seeds every active point, `turned` where it is `reversing`; follows built edges; never through a terminus and never seeds one as turned; records a terminus reached `turned`.  `AutonomyChecks.checkHomesThatNeedReversing` (`e` `:1140`).  Against `HomeStaging`'s moves (`:1110-1149`): a plan rests a train that cannot reverse on a terminus or a reversing point only when its next move takes it home (`turnsATrainArrivingAt`), and the stations it may rest on are destinations - a plain reversing point is not one; `Layout.bfs` does not walk a route that doubles back. |
| **Inputs** | the claim's line B (berth, every train turns, home of a locomotive that cannot reverse) - X (a station) - R. |
| **Needs execution** | done - **`core.rsa66Berth`** (`w/out/e/B-berth.txt`): **R a reversing point (the claim's case):** *"warned 0; the walk's berths [main:1,1]"*, yet *"from BBX (eastbound): Return Home IMPOSSIBLE; by hand, routes to B: offerable true"* (no route offered) and *"from BBX (westbound): Return Home READY \| BBX (westbound) -> BBB"* - straight in, nose first, the thing the warning says; the may-turn square the same.  **R an active terminus, or another berth:** *"warned 1; the walk's berths []"*, yet *"from BCX (eastbound): Return Home READY \| BCX (eastbound) -> BCR \| BCR -> BCB"* and *"from BDX (eastbound): ... BDX (eastbound) -> BDR \| BDR -> BDB"* - rested at the terminus, turned, and driven home tail first: backed in.  **The other questions:** parity - a route through two reversing points has a suffix from the last one, which the walk seeds as turned, so two turns never decide an answer the last turn alone would not; closed track (*"warned 1"*), one way towards the berth (*"warned 0"*) and away (*"warned 1"*), R out of service (*"warned 1"*) all as built; a berth's own barred side is a `reversing` copy and so a seed - a way that starts at the berth itself counts, on the same reading, though a plan can no more stop a train there than on a plain reversing point.  **One way to settle it:** walk what Return Home does - seed a train resting at any active destination that turns trains (`terminus` or `reversing` and a destination) as turned, and drop the pass-through of a `reversing` copy - or put the reading to Adam. |

---

### RSA66-C4 - AutomationAPI.md says of Return Home that *"the planner insists the way there turns it round first - so it backs in and can leave forwards.  Where no such route exists it will not set off and fail; it simply finds no arrangement"*; since 2026-09-04 (`mustBackIn` removed, *"Return home is manual, take the rule out and say why"*) it drives a train that cannot reverse straight into a terminus home, nose first - which is what round 141's warning says

| | |
|---|---|
| **Disposition** | Fixed in AutomationAPI.md (`ffbb4a39`): Return Home rests a train that cannot reverse on another terminus that leads straight home and backs it in, and where none does drives it in nose first; the setup check's warning named.  The fix-validation fanout (2026-10-11, one reader a fix) found that still overstated: `1c91a3e0` says Return Home takes the first clear route, nose first, and backs a train in only where it cannot get there directly. |
| **Grade** | C - a user-facing statement the program does not do, written on 2026-09-01 (`c9153aaf`) and left when the rule came out three days later (ACC-C1 corrected the comments, not this guide); it now contradicts the setup check's own warning text (*"enters this home nose first and has to be backed out by hand"*). |
| **Names** | CMT-B4 (the earlier correction), ACC-C1, ACC-D1, round 141 (C3) |
| **Where** | `AutomationAPI.md:425` at `6c4caaa7` and `e603a2ff`; `HomeStaging` `:1295-1302`, `:2657-2660` (the rule's removal recorded in the code). |
| **Needs execution** | done - **`core.rsa66Berth`**: *"from BAX (westbound): Return Home READY \| BAX (westbound) -> BAB"* - a berth home every train turns at, no turning place anywhere, a train that cannot reverse: planned straight in. |

---

### RSA66-C5 - A standing train's own square draws its line twice - under everything, and again over the diagram's mark for the train - so it comes out rgb(239,134,65) where the rest of the train is rgb(209,125,69); the same on the half of a run's first and last squares its run line does not reach.  Older (FR-106's second pass keyed on any overlay, and every standing train has one since 2026-10-01); round 139 changed its colour from rgb(234,112,31)

| | |
|---|---|
| **Disposition** | Fixed in round 142 (`7873e860`), as the finding suggests: the line is drawn again only where a run line lies (`TileOverlay.runArea` - the whole square for a segment side to side, the half toward the side it leaves for one that ends there) - `ui.testTheTrainIsShownAsALine.testAStandingTrainsOwnSquareIsOneOrange` (under a standing train's mark, and a run ending on the square), seen red first (94 pixels laid twice).  The fix-validation fanout (2026-10-11, one reader a fix) passed it, with four small residues left as they are: the cap's nub where the second pass is cut, the other road of a crossing, a curve with no annotation, and the held wash under the line. |
| **Grade** | C - a picture slightly wrong: one train, two oranges, next to its locomotive icon, at every size, at rest and during runs; nothing about the railway.  Round 139 brought the two closer (30 shades apart in red, where they were 25 in red and 38 in blue) without making them one, which is what it was for (*"so a train is the same orange running as standing"*, its javadoc). |
| **Names** | round 139, FR-106 (the tail over the run line), OB-317 and Adam's 2026-10-01 parked marks (`markTheParkedTrains`), MT-309 (the train under everything autonomy draws) |
| **Where** | `LayoutLabel.paintComponent` `:1585`: `if (overlay != null && !overlay.isBlank())` draws `paintCoveredMark(tail, true)` (`:1591`) in `TRAIN_OVER_RUN` over the whole of the square's covered roads; `TileOverlay.isBlank` (`:378`) is false for any overlay with a train on it, and `DiagramMonitor.markTheParkedTrains` (`:525`) gives every standing train's square `TileOverlay.parked`, which has no run line.  So the second pass is laid over plain track there, on top of the first. |
| **Needs execution** | done - **`ui.rsa66Window`** (`w/out/d/window/W-window.txt`, `T-rest-*.png`, `crop-head-zoom.png`): at Large *"1 - Main:20,12 FEEDBACK ... overlay IDLE+parked:E: ... top rgb(239,134,65)x187"* against the train's other squares *"overlay none: ... top rgb(209,125,69)x210"*; the same at Small and Medium, on curves (*"13,9 FEEDBACK_CURVE ... rgb(239,134,65)"*) and with the coaches; during the run, the start square *"REACHED+parked:E[null->E/REACHED]: ... rgb(239,134,65)x40 rgb(210,125,69)x25"* and the arrival square *"REACHED+parked:N[S->null/REACHED, ...]: ... rgb(210,125,69)x98 rgb(239,134,65)x89"* - two oranges on one square.  **At `98c1072f`** (`w/out/p/window/W-window.txt`): the same squares *"rgb(234,112,31)x187"*.  The soft orange is one colour everywhere (*"rgb(245,140,60)"*).  **One way to settle it:** draw the second pass only where the overlay has a run line (`!overlay.getSegments().isEmpty()`), clipped to it. |

---

### RSA66-D1 - Round 137: a route may set a held turnout to the setting its path holds and only a change is refused - on both drives of a three-way, through the screen and the s88 door alike; a route mixing the two is asked about the change; where two paths hold one accessory at different settings every command on it is refused; the other two readers of `getActiveAccs` toggle and so always change

| | |
|---|---|
| **Disposition** | Checked - clean (but C1, C2). |
| **Grade** | D |
| **Names** | round 137, AU-A2, MT-247, RC-A10, V34-C5 (the three-way at the tile) |
| **Where** | `Layout.getActiveAccSettings` (`:1098`; `Accessory.isThrow` the one sense, true for TURN and RED, as `RouteCommand.getSetting`); `MarklinRoute.heldReason` `:628`; `conflictingAccessoryAndReason` (the Routes tab and the route square through `askAboutRouteConflict`), the per-command check in `execRoute` (the s88 door and the midway question); `LayoutLabel` `:479` (a tile click toggles, a three-way cycles - every click a change) and `TrainControlUI.keyboardAutonomyAllows` (a toggle button) still ask `getActiveAccs` and need no setting; `TilePorts` commands both of a three-way's drives on every road. |
| **Needs execution** | done - **`regression.rsa66Guard`**: *"T1 held STRAIGHT, route STRAIGHT: null"*, *"route TURN: [Switch 141, route.refusedAccessoryOnActivePath]"*; T2 held TURN the mirror; *"3-way held D1 TURN D2 STRAIGHT, route D1 TURN + D2 STRAIGHT: null"*, *"route D1 STRAIGHT: [Switch 143, ...]"*, *"route D2 TURN: [Switch 144, ...]"*; *"route T1 STRAIGHT (same) then T2 STRAIGHT (change): [Switch 142, ...]"*; the s88 door with that route: *"T2 ... switched true -> true"* (left alone); the null case: *"held null (key present true)"*, both settings refused.  Claims `rsa66RouteClaims` 18 of 18. |

---

### RSA66-D2 - Race conditions in round 137: none widened - the setting is read with the set it belongs to, without the monitor as before, per command and immediately before the command goes out; a path replaced, released or handed over between the screen and the command is asked about again at the command; the null case cannot arise on a railway the builder makes

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | IAR-B2, RSA4-C3, AU-A2, RC-A10 |
| **Where** | `getActiveAccSettings` walks `takingPath` then `activeLocomotives` (claims first, so a hand-over is seen on one side or both, and both carry the same edges and settings); an edge counts while `isLockHeld` and not `cleared`; `execRoute` asks `heldReason` for each accessory command and sends it at once (`:851-993`), so the window is the one AU-A2 left - between the read and `setAccessoryState` - and a held-same command in it is no more exposed than a free one was: the path must give the accessory up and another claim it inside that window.  `isPathClear` keeps one setting per accessory within a path, and lock edges keep a second path off a turnout another holds, so two settings at once need a hand-made railway (reached in the probe by reflection only).  Path Integrity Validation (`:4853-4870`, skipped in simulation) waits for confirmations of the path's own settings; a route's same command confirms the same state. |
| **Needs execution** | by reading, and D1's probe from inside a real dispatch. |

---

### RSA66-D3 - Round 138: a station's list is read by its square at both doors that read it as a wall - a barred side lets the trains it keeps out pass, and an allow-only list no longer walls off every other train; a plain sensor's list is still a wall; every other reader asks about stopping and is right to ask the copy; no measurable cost

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RSA65-B1, FR-121, `514071ee` |
| **Where** | `Layout.isAStationSquare` (`:9587`), asked by `isPathClear` (`:3166`, after `excludes`, so only for a train the list keeps out) and `HomeStaging.canEnter` (`:1947`); the stopping readers (`pickPath`, `hasAutonomousDestination`, the why-not reasons, the destination enumerator, `HomeStaging` `:810`/`:2254`/`:2643`, the Auto tab's dash) ask a destination copy and need no change; the timetable and the hand send pass through `isPathClear`. |
| **Needs execution** | done - **`core.rsa66Excl`** (`w/out/d/E-excl.txt`): barred W, blacklist [Y] - the copy passed *"destination false, ... isAStationSquare true"*, *"R66 Y east through SA1Beta (eastbound): isPathClear true; Return Home canEnter true; getPossiblePaths offers Gamma/Delta true"*, while the hand menu still leaves the station out for Y (*"This station does not accept R66 Y."*); allow-only [X] the same for Y; barred E the same; the plain sensor *"isPathClear false (The intermediate point SA5Beta excludes this locomotive)"*.  **Barred on both sides** (no copy a destination): *"isAStationSquare false"* and the list walls Y - but that square is one the editor refuses to make with track on both sides (`AutonomyChecks.NO_ARRIVALS_LEFT`: reached only by deleting the track to the other side, after which nothing passes anyway); the probe made it through the store.  A square of one copy is the same case.  **A Java-API railway** (no blocks): *"J_S (station, lists Y): block null, isAStationSquare true"*, the plain point a wall.  **Adam's frozen railway** (`core.rsa66Live`): *"BottomInner (eastbound): destination false, ... isAStationSquare true"* (RSA65-B1's copy), BottomCrossover *"isAStationSquare false"*.  **Cost:** 100,000 calls 54-105 ms; `getPossiblePaths` for eleven locomotives, best of eight blocks of 20, run alternately: 625 ms here against 587 ms at `98c1072f`, then 930 against 941 - noise (no train standing on the frozen railway is on a list, so the new call is not even reached).  Claims: round 138's two green. |

---

### RSA66-D4 - RSA65-C1's fix: an old file leaves an allow-only square's list as it is - empty or not, imported once or twice - and still fills a square with no mode; a mode written in another case is read as a blacklist by both the import and the build, so the two agree

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RSA65-C1, RLA3-B1 |
| **Where** | `AutonomySession.importLegacy` `:1957-1962`; `Layout.parseAuto`'s mode (`Point.ALLOW_ONLY_LISTED.equals`) |
| **Needs execution** | done - **`core.rsa66Excl`** (C): allow-only with [X] - *"after a second import: list ["R66 X"], mode whitelist"*, built *"excludes X false, excludes Y true"*; allow-only, none listed - *"list null"* after both imports, built excluding both; *"mode written 'Whitelist' ... after one import: list ["R66 Y"]"*, built *"allowOnly false, ... excludes Y true"* (the file's meaning kept); no mode - filled as before.  The editor writes only the exact value.  The import's question (`autosetup.ui.importTakesTheFilesForDefaults`, *"an emptied exclusion list counts as not set"*) is not made false: an allow-only square's mode is set.  Claims: `testAnOldFileDoesNotFillAnAllowOnlySquaresList`, `core.testASecondImportFillsGapsAndDoesNotOverwrite` 5 of 5. |

---

### RSA66-D5 - Round 140: the main window steps half a square at Small, Medium and Large (15, 23, 30 px), back through the page cache; a notch of the wheel moves three steps, across with Shift; an arrow press one; the S, M and L pop-ups the same; FlatLaf's 1.5 scale changes nothing on the diagram

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | MT-726, MT-727 |
| **Where** | `LayoutGrid.scrollHalfASquareAtATime` (`:677`, `Math.max(1, size / 2)`), called from the main window's repaint (`TrainControlUI` `:33853`, the cache path included) and `LayoutPopupUI`'s constructor |
| **Needs execution** | done - **`ui.rsa66Window`** (S): *"Small: tile widths [30], steps 15/15"*, *"Medium: ... 23/23"*, *"Large: ... 30/30"*, and again after Large -> Small -> Large -> Medium; *"Large: a notch of 3 with Shift: across 90 ... without Shift: across 0, down 90 ...; one arrow press: down 30, across 30"*; *"Medium: a notch of 3 with Shift: across 69"*; pop-ups *"size 30 ... steps 15/15"*, *"size 46 ... 23/23"*, *"size 60 ... 30/30"*.  With `-Dflatlaf.uiScale=1.5`: the same steps, and the pictures at each size differ from 100 per cent in 0 pixels.  The Readme's *"half a square at a time in both directions"* is right.  Claims `testTheDiagramScrollsHalfASquareAtATime`, `testAPoppedOutDiagramScrollsHalfASquareAtATime` green. |

---

### RSA66-D6 - Round 139: over its own run line a running train's line is rgb(210,125,69), a standing train's rgb(209,125,69), at 30, 46 and 60 px and on every kind of square a 30-odd-square run crossed - straight, curve, switch, double curve, signal, route button - with the held wash under it; the soft orange and the coaches unchanged (but C5)

| | |
|---|---|
| **Disposition** | Checked - clean (but C5). |
| **Grade** | D - the look is Adam's. |
| **Names** | round 139, MT-728, FR-106, `LayoutLabel.TailColour` |
| **Where** | `DiagramColours.TRAIN_OVER_RUN` (`:99`); `paintCoveredMark(g, true)`; `TailColour.SOFT` keeps one colour both ways |
| **Needs execution** | done - **`ui.rsa66Window`** (R, `R-run-*.png`): every REACHED square of the run *"top rgb(210,125,69)"* (*"14,11 SWITCH_LEFT"*, *"11,11 DOUBLE_CURVE"*, *"6,11 CURVE"*, *"5,11 SIGNAL"*, *"17,11 ROUTE ... rgb(211,126,70)"*), the running train's own square the same; at rest the soft orange *"rgb(245,140,60)"* on every square.  **Seen, not graded:** a square the window already counts as covered while the diagram still draws it ahead (blue) for up to one 200 ms tick - the two are worked out on separate threads - shows the line over the blue as a pinkish rgb(188,104,98) (`crop-run1-start.png`); before round 139 the same moment was rgb(140,56,90).  The restriction arrows are hidden while trains run (`theAutoTabWaits`), so the line never meets them running. |

---

### RSA66-D7 - The documentation since `adf3f4e4`: the Readme's three changed bullets (Bulk Tools' clears, a route no longer changes a switch under a train, half a square a step) and Automation.md's route paragraph say what the program does, one sentence or two, with no dash as punctuation (but C2, C4)

| | |
|---|---|
| **Disposition** | Checked - clean (but C2, C4). |
| **Grade** | D |
| **Names** | `a5be719f`, RSA65-C2, `6c4caaa7` |
| **Where** | `Readme.md:411, 436, 453`; `Automation.md:439`; the clear items in the English bundle; `heldReason` |
| **Needs execution** | read, against D1 and D5.  Borderline, not graded: Automation.md's *"nor turn a signal green at a platform a train is standing at"* leaves out a platform only reserved, which `heldReason` refuses too (GSR-C2; older, unchanged). |

---

### RSA66-D8 - Round 141's other questions: Adam's active terminus is no longer warned; closed, one-way and out-of-service track are honoured; nothing is said when the setup does not build; the walk costs 0.07 ms a check on Adam's railway (but C3)

| | |
|---|---|
| **Disposition** | Checked - clean (but C3). |
| **Grade** | D |
| **Names** | OB-336, MT-730 |
| **Where** | `checkHomesThatNeedReversing` (`e` `:1125-1150`): a home on a must-turn square warned only where it is a parking berth and the walk does not reach it; `berthsATrainCanBackInto` returns null without a build |
| **Needs execution** | done - a home on an active terminus every train turns at is not warned (the claim's first assertion, green); **`core.rsa66Berth`**: frozen railway *"homes warned: []"*, the walk's set 13 squares (BottomMainB, BottomMainC, TopMainR0Park, the Bottom parking tracks ...); *"the walk x1000: 67 ms; points 87, edges 125"*; `check()` 38 ms a call off the event thread, kept for the event on it (round 57).  Claim `testAHomeIsWarnedAboutTurningOnlyAtABerthNoTrainCanBackInto` green in 221 of 221. |

---

### RSA66-D9 - No regression where rounds 137-141 touch: every claim of the classes they changed is green but for the archive's environmental reds

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Where** | the classes in Method's **Ran** |
| **Needs execution** | done - 18 (copy), 2, 12 (copy), 55 of 57, 12, 67, 221, 5, 115. |

---

### RSA66-D10 - The archive's environmental reds: two `testDiagramLooksRight` claims want a locomotive database; `testTheImportDoorReadsAnOldFile` 30 of 42 red, the same 30 at `98c1072f`, all of them on Adam's trains and timetable

| | |
|---|---|
| **Disposition** | Checked - not product. |
| **Grade** | D |
| **Needs execution** | done - *"Index: 0, Size: 0"* (the known two); the import door's 30, identical by name at both commits (`w/scratch/C3`, `w/scratch/C4`), 27 of them preconditions (*"precondition: no train stands on his railway"* and the like), the other three an *"Index: 0, Size: 0"* and two readings of the live layout's timetable and configurations the archive does not carry. |
