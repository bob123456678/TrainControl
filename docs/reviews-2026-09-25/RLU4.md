# RLU4 - validation, round 4: the windows (every screen, dialog and menu) and the message bundles (TrainControl 3.0.0 release readiness)

**Status:** open

**Prefix:** RLU4

**Reviewed:** branch `autonomy-diagram-r0` at `f7e94a50` (working tree clean by `git --no-optional-locks status --short`), round 3's claims `cca48e96`, its fixes `81a9fffa` and its records `f7e94a50`, on 2026-09-25.

## Method

Read-only: nothing compiled or run, no JVM, no git state changed, nothing under `cs2_sample_layout/` opened, and this file is the only one written.  I read the brief, `docs/reviews/README.md`, my lane's round-3 document `docs/reviews-2026-09-25/RLU3.md` whole, `RLA3.md` and `RLD3.md` whole, and the dispositions of the earlier rows round 3 touched (RLU2-C10 to RLU2-C13 in `RLU2.md`, RLA2-B1, RLA2-B3 and RLA2-C4 in `RLA2.md`, RLD2-C2 and RLD2-C3 in `RLD2.md`, RLA-C2 and RLU-C4 as `f7e94a50` changed them).  Then the three commits of `git log d779578b..HEAD` with `git show`: `81a9fffa` in full for `gui/`, `automation/Layout.java`, `automationui/`, the eight bundles and `docs/`; `cca48e96` in full (`testWhyStuck`, `testTheImportDoorReadsAnOldFile`, `testWhereHisTrainsMayBeSent`, `testJavadocsAreAttached`, `testMessageBundles`, the monitor census); `f7e94a50` for `issues.md`, `tests.md`, `behaviour.md`, `open-questions.md` and the five review documents it changed.  Around each change I read at HEAD: `AutonomyViewerPanel.importConfiguration`, `activateTheConfigurationNamed`, `importLegacyGraph` whole, both `loadAfterImport`s, both `load`s, `revert`, `delete`, `save`, `exportConfiguration`; `AutonomyMenu`'s Configuration submenu, `guardWhileEditing` and `item`; `TrainControlUI.getActiveDiagramConfiguration`, `prepareAutonomyReload` and `isAutonomyBusy`; `AutonomySession.configurationToLoadAfterImport`, `ImportFormat` and `detectImportFormat`, `importBundle`, `importLegacy`'s placement, home and carried-settings branch, `captureFromLayout` whole with `POINT_OPERATIONAL_KEYS`, `snapshotSetup`; `AutonomyCompanionStore.importBundle`, `importConfiguration`, `fileNameTaken`, `snapshotSetup` and `restoreSetup`; `Layout.explainCannotStart` (both), `whyItReachesNoStation`, `whyTheStartIsRefused`, `whyNoTrainIsStartedFrom`, `isABarredCopyOfAStation`, `turnsEveryTrainAt` and `startableTwinOf`; the Trains May Arrive... menu in `AutonomyEditorPanel`; `HomeStaging`'s barred-start reasons; every `AutonomyReport.show` under `gui/`.  Documents: behaviour.md :705 and :2428-2449, `Automation.md` :311, open-questions.md's new Open paragraph, OB-303 to OB-305, MT-298's new comment, Readme.md's 3.0.0 import line.  **Mechanical checks, all in memory** (inline Python fed through a heredoc, reading `git show` output; nothing written): the eight bundles at `cca48e96` and HEAD (key sets, duplicates, placeholder sets, bytes above 127, empty values, straight apostrophes in values with a placeholder, the keys that changed), and the four changed keys, `confirmImportFillsGaps` and `errorImportIntoConfigurationInUse` decoded and read in all eight languages; the javadoc ratchet's orphan rule over every `src/` file at HEAD; the MT-491 file's placements and homes against the live snapshot's `configuration-Main.json`, and that file's top-level shape; `docs/manual-tests/triage.db` opened read-only (`mode=ro`) for every column of the rows named in RLU4-C8, and `docs/tools/catalog-findings.py`'s truncation lines.  `docs/manual-tests/findings.tsv` was searched before each finding (the prefix RLU4 is free; bare configuration, `ImportFormat.CONFIGURATION`, import while running or busy, and the either-way sentence): nothing below repeats a catalogued row beyond the ids it names.  Not reported, as the brief says: RLA3-B1 with RLU3-C4, OB-300 to OB-305, RLU2-C9 and RLD-C1.  **Everything below is from reading**; each finding that needs a run says so, with the fixture and what proves or refutes it.

One B, eight C, seven D.  The B is new and is a regression of a B fix: round 3 narrowed RLA2-B1's refusal to a bundle, and the older, bare shape of configuration file - which is also what the setup folder itself holds - takes the bundle's road past it (RLU4-B1).  Of the Cs, three are about the old-file import round 3 let back into the configuration in use (RLU4-C1 to RLU4-C3), four about the Why not Moving? sentences round 3 rewrote (RLU4-C4 to RLU4-C7), and one is records (RLU4-C8).

---

### RLU4-B1 - Round 3 narrowed RLA2-B1's refusal to a bundle: a bare configuration file - the setup folder's own `configuration-<name>.json`, or an export from before 2026-08-17 - imported under the running configuration's name is replaced, then captured straight back over

| | |
|---|---|
| **Disposition** | Fixed - into the configuration in use every file that replaces it is refused, a bundle or a configuration of the older, bare shape; an old file alone fills gaps.  claims 6f02ff2e (red first), fix d5b97ee8; mutation V1 red. |
| **Grade** | B - RLA2-B1's consequence, as RLA2-B1 was graded: "Replace it with the imported one?" answered Yes does not replace - the file's placements, homes, settings and timetable are overwritten by the running railway's, after a message saying it was imported.  Nothing on the railway moves.  Reach is narrower than RLA2-B1's (a file of the older shape, under the running configuration's name).  Needs execution. |
| **Names** | RLA2-B1; RLD3-C2 (its claim); RLD3-C1 (the fix that narrowed the refusal, `81a9fffa`) |
| **Where** | `AutonomyViewerPanel.java:1060-1073` (`format == AutonomySession.ImportFormat.BUNDLE && inUse != null && ...`), `:1075-1086` (the question: `confirmImportOverwrites` for every kind but an old file), `:1109` (`importBundle`), `:1131` (`loadAfterImport(name.trim())`, which captures); `AutonomySession.java:1867-1891` (`detectImportFormat`: `points` as an object, or `globals`, is `CONFIGURATION`); `AutonomyCompanionStore.java:2093-2103` (no `configuration` key: `importConfiguration(name, file)`), `:2566-2577` (puts the file in place of the configuration of that name); `AutonomySession.captureFromLayout` (`:5337`; each point's operational keys replaced or removed, `globals` replaced whole at `:5598`); `AutonomyMenu.java:332-333`; `test/regression/testTheImportDoorReadsAnOldFile.java:617` |
| **Needs execution** | yes |

Round 2 refused the running configuration's name before the file was read, so every kind of file was refused.  `81a9fffa` moved the check below the format detection, to let an old file back in (RLD3-C1), and wrote it as `format == BUNDLE`.  The door knows three kinds that import: `BUNDLE`, `CONFIGURATION` and `LEGACY_GRAPH`.  `CONFIGURATION` - "a configuration on its own, as exporting wrote it before bundles existed", recognised by `points` as an object or by `globals` alone - takes the bundle's road, not the old file's: the question is "Replace it with the imported one?"; `importBundle` finds no `configuration` key and calls the store's `importConfiguration`, which puts the file in place of the running configuration; the save writes it; and `loadAfterImport(name)` loads the running configuration again with `load(name, true)`, whose capture replaces every point's `loc`, `home`, `active`, `priority`, `speedMultiplier`, `maxTrainLength`, `excludedLocs` and tail with the running railway's (removing them where the railway holds the default) and replaces `globals` whole, the timetable included.  That is RLA2-B1 exactly, for this kind of file.

Which files are this kind: the setup folder's own configuration files - the live snapshot's `configuration-Main.json` has `globals`, `name` and `points` as an object (read in memory) - so restoring from a backup of the configuration folder is one; and any export written before `319afe65` (2026-08-17), which Export named `<configuration>.json`, so the Import prompt suggests the running configuration's own name, as on RLA2-B1's path.  For the first the prompt suggests `configuration-Main`, and the operator types the name he means.

The refusal's own comment says "A bundle replaces the configuration it is imported into", which is as true of the bare shape, and `AutonomyMenu`'s comment repeats "it refuses a bundle".  The claim RLD3-C2 asked for imports what Export writes today, a bundle, so it cannot see the other kind that replaces.

**Direction:** refuse every kind that replaces - `format != LEGACY_GRAPH` - and give the refusal's claim the bare shape too.

**Verification request.**  `testAnImportIntoTheConfigurationInUseIsRefused`'s fixture (live-snapshot sandbox, the window open, Main running).  Write `session.getStore().getConfiguration(inUse)` to a temp file - the shape `configuration-Main.json` has on disk - with one change the running layout does not carry (a priority on a station that has none), and import it from the menu under `inUse`, answering Yes.  **Proves:** no `errorImportIntoConfigurationInUse`; `confirmImportOverwrites` is asked and `infoImported` shown; afterwards that station has no priority - captured over.  **Refutes:** the refusal is shown, and the configuration, the names and setup.json are unchanged.

---

### RLU4-C1 - Into the configuration in use while autonomy runs, an old file's settings are written, counted, and then captured over by the reload; behaviour.md says the reload does not capture, and Delete beside it refuses while trains move

| | |
|---|---|
| **Disposition** | Fixed as RLA4-C1. |
| **Grade** | C - RLA-C2's consequence (graded C), in the one case round 3's capture-first skips: what the dialog counted as carried is not in the configuration.  Nothing moves that was not told to.  Needs execution. |
| **Names** | RLD3-C1 (its fix), RLA-C2 |
| **Where** | `AutonomyViewerPanel.java:1264` (`!ui.isAutonomyBusy()`), `:1406` (`loadAfterImport(into, !captured)`), `:763` and `:773-786` (`load`: the stop question, then the capture), `:1535-1539` (`delete` refuses while busy); `TrainControlUI.java:4170` (`prepareAutonomyReload`), `:24328` (`isAutonomyBusy`); `AutonomyMenu.java:149-178` (only an open editor greys the menu); `docs/reference/behaviour.md:2436-2438` |
| **Needs execution** | yes |

The old file into the configuration in use captures first only while autonomy is not busy; otherwise `captured` stays false and the reload captures - "the reload stops them and captures where they stopped, as it always has", says the comment.  Followed through: the import writes its gap-fill (a switched-off square, a priority, a speed, exclusions, homes), saves, and shows "carried {3} other settings"; then `load` asks whether to stop the trains.  **Yes:** it stops them and captures from the running layout, which was built before the import, so every carried key on a point the railway holds is replaced by the railway's or removed where the railway holds the default - the import's point-level half is undone and saved (only what the capture has no key for, such as Can Be Chosen and the turning marks, survives).  **No:** the load returns; the import sits on disk and not on the railway, and the next capture into that configuration - choosing a configuration, or the exit - does the same.

behaviour.md states the rule without the exception: "what the running layout knows goes into the configuration first, the reload after the import does not capture again".  Reach: Import is never greyed while autonomy runs (`guardWhileEditing` greys the menu only for an open editor), and the panel's other door that changes a configuration mid-run, Delete, refuses with `autolayout.errorCannotEditWhileRunning`.  His MT-491 and MT-298 files carry no homes (read in memory), so on his railway what is lost is settings.

**Direction:** refuse an old file into the configuration in use while `isAutonomyBusy()`, with Delete's message - or, if it is to go ahead, say in behaviour.md and in the dialog that the stop's capture replaces what it carried.

**Verification request.**  Live-snapshot sandbox, the window, Main running.  Clear from Main a setting the MT-491 file carries onto a matched square (one of its priorities) and rebuild without capture (`load(inUse, false, false)`); make autonomy busy (a train dispatched, so `Layout.isRunning()` is true); import the MT-491 file from the menu under Main, Yes, and Yes to stopping the trains.  **Proves:** the square has no priority afterwards, while the message counted it among the settings carried.  **Refutes:** the file's priority is there.  **Control:** not busy, it stays (the claim RLU4-C2 asks for).

---

### RLU4-C2 - Neither in-use claim holds "the reload does not capture again": the reload's capture also puts the moved train where it went, so the claim's own MUTATION clause, made by deleting the capture, stays green

| | |
|---|---|
| **Disposition** | Fixed as RLA4-C2. |
| **Grade** | C - a test that would not catch its own regression; RLD2-C3's shape, on the half of RLA-C2 that keeps what the import brought.  Needs execution. |
| **Names** | RLD3-C1, RLD2-C3, RLA-C2 |
| **Where** | `test/regression/testTheImportDoorReadsAnOldFile.java:696-700` (the MUTATION line), `:702-800` (`testAnOldFileIntoTheConfigurationInUseLeavesItsTrainsToTheRailway`), `:811-891` (`testASecondImportIntoTheConfigurationInUseKeepsAHandMadeChange`); `AutonomyViewerPanel.java:1262-1278`, `:1406` |
| **Needs execution** | yes |

RLA-C2's fix has two halves that work only together: capture the running layout first, then reload without capturing, "so ... the import brought stay".  The new claim moves a train on the running railway, imports, and asserts placed 0, the same names standing, the moved train on its new square in the configuration, and the not-placed line.  Delete the capture block: `captured` stays false, the reload captures, and that capture writes the moved train onto its new square too - every assertion holds, while the carried settings are captured over (RLU4-C1's mechanism, now with nothing busy).  Keep the capture and let the reload capture as well (`loadAfterImport(into)`): the same.  Only a mutation that removes the capture and still tells the reload not to capture is red, so "leave out the capture that keeps a moved train where it stands" is true of one way of leaving it out.  The MT-298 claim asserts a maximum the rebuilt railway also carries, so a second capture writes the same value.  Neither asserts anything the file brings to Main - the file places four trains and names no homes, Main already homes its trains (in memory) - and what it brings is settings.

**Direction:** before the import, clear from Main one setting the file carries and rebuild without capture; after it, assert the setting is there.

**Verification request.**  Two mutations, one at a time: delete `AutonomyViewerPanel.java:1262-1278`; and replace `loadAfterImport(into, !captured)` with `loadAfterImport(into)` at `:1406`.  Run the two in-use methods.  **Proves:** green under each.  **Refutes:** red.

---

### RLU4-C3 - Into the configuration in use, the fill-gaps question still promises the file's trains - "Add to it what the file has and {0} does not?" - and Yes places none

| | |
|---|---|
| **Disposition** | Fixed - into the configuration in use the question says none of the file's locomotives is placed, and the message after says how to put one down.  claims 6f02ff2e (red first), fix d5b97ee8; mutation V4 red. |
| **Grade** | C - a question that promises more than the door does, as RLU2-C11 was; the message after names the trains, not how to place them.  No execution needed. |
| **Names** | RLD3-C1; RLU-B1 and RLA-B2 (the rule that the question says what Yes does) |
| **Where** | `AutonomyViewerPanel.java:1065` (`inUse` known before the question), `:1075-1086`; `messages.properties:1525` (`confirmImportFillsGaps`) and `:1494` (`infoLegacyNotPlacedInUse`), and the seven translations; `AutonomySession.java:1144-1147` |
| **Needs execution** | no |

The door reads the file before its question "so that it can say what Yes does" (RLA-B2), and at `:1065` it already knows whether the name typed is the configuration in use.  For an old file under that name it asks what it asks of any configuration: "A configuration named {0} already exists.  Add to it what the file has and {0} does not?  Nothing already set in {0} is changed."  Into the configuration in use the file's placements are the one thing it has and {0} does not that Yes does not add (`placeTrains` false).  The dialog after says so - "Not placed, because {1} is the configuration in use and where its trains stand is the railway's to say: {0}" - but not what to do if he wants them there (put each down from the diagram).  **Direction:** a clause in the question when the name is the configuration in use ("its trains are not placed: where a train stands is the railway's to say"), and "place them on the diagram" in the line after; eight languages.

---

### RLU4-C4 - RLU3-C1's fix takes RLU2-C10's remedy back everywhere, not only where every train turns: a station reached only across a closed side is again told to be switched on and ticked, which it is - and RLU2-C10's row still says Fixed

| | |
|---|---|
| **Disposition** | Fixed - the either-way sentence names the side a train would arrive on again, with GUI4-C3's exception in the words ("unless every train turns round there").  claims 6f02ff2e (red first), fix d5b97ee8; mutation V8 red. |
| **Grade** | C - RLU2-C10's own grade: a refusal whose remedy is incomplete.  Needs execution. |
| **Names** | RLU3-C1 (its fix, `81a9fffa`), RLU2-C10, GUI4-C3 |
| **Where** | `Layout.java:5080-5083`; `autolayout.why.startReachesNoStationEitherWay` (`messages.properties:1798` and the seven translations); `docs/reviews-2026-09-25/RLU2.md:143` and the store's RLU2-C10 row |
| **Needs execution** | yes |

RLU3-C1 offered two directions: name the side only where the station beyond is not one every train turns at, or put GUI4-C3's exception into the words.  `81a9fffa` took the remedy out.  At a station trains pass through (plain copies) whose side a train would arrive on is closed under Trains May Arrive..., opening it is the remedy, and raises no error (`checkTerminusTwoWaysIn` is about termini).  The either-way sentence now gives "switch it on and tick Can Be Chosen in Full Autonomy", both already true there - RLU2-C10's defect, back for every station that is not a terminus.  RLU2-C10's own verification fixture was a terminus, the one case where its remedy is wrong, so its request cannot tell the two apart.  RLU-C4's row was given a line ("the sentence now names only switching on and ticking (RLU3-C1)"); RLU2-C10's row still reads "Fixed - the either-way sentence names the side a train would arrive on".

**Direction:** RLU3-C1's second option - "...or open the side a train would arrive on under {2}, unless every train turns round there" needs no knowledge of which station is meant - and a line under RLU2-C10.

**Verification request.**  In `testWhyStuck`'s manner: a platform whose one way on reaches a through station S (plain copies, not a terminus) only at a copy barred under Trains May Arrive..., its other way a siding; a train on the platform.  **Proves:** the either-way sentence, whose only remedies are already true of S; and making that copy of S a station (opening the side) lets autonomy start the train.  **Refutes:** another sentence is chosen, or opening the side changes nothing.

---

### RLU4-C5 - RLU3-C1 and RLU3-C2 are fixed in the program only: the user guide and behaviour.md still send the operator to open the side under Trains May Arrive... - the remedy GUI4-C3 ruled out where every train turns - and know nothing of the two new sentences

| | |
|---|---|
| **Disposition** | Fixed in the records, as RLA4-C3 (d5b97ee8). |
| **Grade** | C - records, but the user guide is what an operator follows; followed at a terminus it is RLU3-C1's consequence, a setup that refuses to load until the side is closed again.  No execution needed. |
| **Names** | RLU3-C1 (whose Where named both lines), RLU3-C2 (whose Where named `Automation.md:311`), RLD2-C10 |
| **Where** | `Automation.md:311`; `docs/reference/behaviour.md:705`; `81a9fffa` changed neither (its documents diff is behaviour.md :1960 and :2433-2448, and open-questions.md :135) |
| **Needs execution** | no |

The user guide: "otherwise drive it off by hand, or let autonomy choose a station it can reach - switch that station on, tick Can Be Chosen in Full Autonomy, and open the side a train would arrive on under Trains May Arrive...".  behaviour.md: "...and the side a train would arrive on open under Trains May Arrive... (RLU-C4, RLU2-C10)".  The program says neither now.  Nor do they say what RLU3-C2 added: where the other way reaches a station a train may not be started from, Why not Moving? says so, with "open that side and turn it round" where not every train turns there and "drive it off by hand" where one does.  **Direction:** both lines restated as the program now answers, three cases.

---

### RLU4-C6 - The GUI4-C3 branch of RLU3-C2's new code has no claim: take out `&& !turnsEveryTrainAt(refused)` and every test stays green, while a train beside a terminus is told again to open its closed side

| | |
|---|---|
| **Disposition** | Fixed as RLA4-C4. |
| **Grade** | C - a test that would not catch its own regression, on the very rule RLU3-C1 was about.  Needs execution. |
| **Names** | RLU3-C2, RLU3-C1, GUI4-C3 |
| **Where** | `Layout.java:5070-5078`; `test/core/testWhyStuck.java:730-751` (WS11: a copy switched off, not barred), `:767-786` (WS12: barred, at a square with plain copies), `:792-821` (`platformWithASiding`, which builds no turning copy) |
| **Needs execution** | yes |

The new branch chooses `startReachesNoStationOtherWayBarred` ("open that side under "{1}" and turn it round") only where not every train turns at the square, and `startReachesNoStationOtherWayRefused` otherwise.  The two claims cover a switched-off copy and a barred copy of a square with plain copies; no test builds a square whose every copy turns a train, and `git grep` finds the Refused key in `test/` only at WS11.  The disposition's "mutation U5 red" is for RLU3-C2's sentence; nothing records a mutation of this clause.  **Direction:** a WS13 whose copies are all turning copies, one of them barred, asserting the Refused sentence and, as RLU3-C1's claim does, that the arrivals menu is not in it.

**Verification request.**  Delete `&& !turnsEveryTrainAt(refused)` at `Layout.java:5074`; run `core.testWhyStuck`.  **Proves:** green.  **Refutes:** red.

---

### RLU4-C7 - RLU3-C2's two sentences say less than their siblings: the menu without where it is, a refusal that names no reason, and the disposition's "switched off" in no sentence

| | |
|---|---|
| **Disposition** | Fixed - "in the autonomy editor" in the barred sentence; a barred copy where every train turns has its own sentence naming the reason; the refused sentence stays for a copy switched off or no station.  claims 6f02ff2e (red first), fix d5b97ee8. |
| **Grade** | C - cosmetic: each sentence is true and gives a remedy that works.  No execution needed. |
| **Names** | RLU3-C2 |
| **Where** | `messages.properties:1795` (`startFacingBarred`: "under "{1}" in the autonomy editor"), `:215` (`whyHomeStartFacingBarred`, the same), `:1799-1800` and the seven translations; `AutonomyEditorPanel.java:1612-1627` (the only Trains May Arrive... menu); RLU3.md :38 and `81a9fffa`'s message |
| **Needs execution** | no |

- `OtherWayBarred` says "open that side under "Trains May Arrive..."" where both its siblings add "in the autonomy editor" - the one place that menu exists - and Why not Moving? is read from the main window.  All eight languages follow English.
- `OtherWayRefused` says only "facing the other way autonomy starts no train there".  The disposition and the commit say the sentence says "switched off"; none does.  On a built railway its reachable case is the other copy barred at a square every train turns at (a switched-off square refuses the train's own copy first, `startInactive`), and there the reason is the barred side - which `startFacingBarredMustTurn` states, for a train on that copy, as "Trains may not arrive at {0} facing the way this one faces".
- Neither carries the either-way sentence's remedy for the train's own way - a station reachable facing this way, switched on and ticked - which helps as much here; `OtherWayRefused` leaves "drive it off by hand" alone.

**Direction:** "in the autonomy editor" in the Barred sentence; in the Refused one the reason the loop has computed; the disposition's "switched off" corrected.

---

### RLU4-C8 - Records round 3 left: seven rows describe code round 3 changed, RLA3-C6's two supersession lines never reached the store, and RLD3-C4 is deferred to nothing

| | |
|---|---|
| **Disposition** | Fixed in the records - a status note on each row round 3 or 4 superseded (RLA2-B1, RLA2-B3, RLA2-C4, RLD2-C2, RLD2-C3, RLU2-C10, RLU3-C1, RLU3-C2, RLU3-D7, RLA3-D1, RLD3-D2, RLA-C2, RLU-C4); RLU2-C13 reopened as OB-305's; RLD3-C4 closed by RLD4-C9's pin. |
| **Grade** | C - records.  No execution needed (the store was read with `mode=ro`). |
| **Names** | RLA2-B1, RLA2-B3, RLA2-C4, RLD2-C2, RLD2-C3, RLU2-C10, RLU2-C13; RLU3-D7, RLA3-D1, RLD3-D2; RLA3-C6 with RLA-C2 and RLU-C4; RLD3-C4 |
| **Where** | `RLA2.md:21`, `:65`, `:131`; `RLD2.md:49`, `:69`; `RLU2.md:143`, `:185`; `RLU3.md:182-190`; `RLA3.md:300-321`; `RLD3.md:142-149`; the `finding` rows in `docs/manual-tests/triage.db`; `docs/tools/catalog-findings.py:467`; `docs/manual-tests/issues.md` (no entry for RLD3-C4) |
| **Needs execution** | no |

- **Rows that describe what round 3 undid.**  RLA2-B1: "Import refuses the name of the configuration in use ...; the round-1 capture into the running configuration is gone" - both halves false since `81a9fffa` (a bundle alone is refused; the capture is back).  RLA2-B3: "on the running railway by RLA2-B1's refusal" - now by the file's trains not being placed.  RLA2-C4: "nothing is imported into the configuration running" - its superseded line is about maxima only.  RLD2-C2: "Fixed as RLA2-B1".  RLD2-C3: "the capture it asked to pin is gone" - it is back, and pinned (in part, RLU4-C2).  RLU2-C10: RLU4-C4.  RLU2-C13: "Not a defect", while RLU3-C5 filed the same thing as the bug OB-305.  And three D rows' titles now say the opposite of the code: RLU3-D7 and RLA3-D1 (refused "before it reads the file", "for both kinds of file") and RLD3-D2 ("the reload after an import captures as every reload does").
- **RLA3-C6's lines are in the documents only.**  `f7e94a50` appended "Superseded 2026-09-25: ..." to the end of RLA-C2's and RLU-C4's dispositions in `RLA.md` and `RLU.md`.  The catalog keeps a disposition's first 120 characters (`catalog-findings.py:467`) and a status note of 164; both dispositions are longer, and the store's rows still end "...the reload after the impor" and "...not one where t", with "Superseded" in no column.  The documents are deleted when the round closes, so the store is what a reader querying either id meets - RLA3-C6's case, still.  RLU2-C11 and RLA2-C4 were done the way that survives: their status note was set ("superseded by RLA3-B1 - ...").
- **RLD3-C4 is "Open - deferred" with nowhere to be deferred to.**  Its residue - the right-click menu's two `AutonomyReport.show(ui, session.save())` calls, read by no pin - is in no OB entry and no open question; OB-303 to OB-305 carry the round's other deferrals.

**Direction:** a status note on each row, as for RLU2-C11, rather than a line at the end of a disposition; an OB entry or a closure for RLD3-C4.

---

### RLU4-D1 - RLU3-C3: the pin reads the panel's own call, and `this` put back there is red

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |
| **Where** | `test/ui/testWhereHisTrainsMayBeSent.java:231-237`; `AutonomyViewerPanel.java:1587` |

The pin asks the panel's source for `AutonomyReport.show(ui, session().save())`, which occurs once in the file, in `save()`; RLU3-C3's mutation makes it absent and the assertion fails.  It is a presence pin, not a census of the panel: a new message there parented on `this` would not be seen, which is RLD3-C4's remainder (RLU4-C8).  The method's javadoc and MUTATION line still speak of the two menus only.

### RLU4-D2 - RLU3-C6: the ratchet's history and the every-key javadoc say what RLU3-C6 asked, and the orphan count is still 86

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |
| **Where** | `test/regression/testJavadocsAreAttached.java:58-60`; `test/core/testMessageBundles.java:906-910` |

The dated line "87 -> 86 on 2026-09-25: RLU-C6 ..." sits with the others; the old test's javadoc sends the keys it cannot see to `testEveryKeyHasAValueInEveryLanguage`.  The orphan rule, re-run in memory over every `src/` file at HEAD, counts 86 - `AutonomyViewerPanel.java` 2 and `AutonomySession.java` 9, as `ORPHANS_BY_FILE` says; `81a9fffa`'s new javadocs (the second `loadAfterImport`, the second `importLegacy`, `notPlacedInUse`) each sit on a member.

### RLU4-D3 - RLU3-C4 and RLU3-C5: carried where their dispositions say

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |
| **Where** | `docs/reference/open-questions.md:238-245`; `docs/manual-tests/issues.md` OB-305 |

open-questions.md's new Open paragraph states both halves of RLU3-C4 (a default stored as nothing; a captured 0 keeping the file's maximum out), with three options and a recommendation.  OB-305 states RLU3-C5's direction - save what the door wrote before asking, when no editor is open, and keep "save nothing" for the late answer - and the risk.  For Adam's question, not a new finding: since `81a9fffa` every old file into the configuration in use is preceded by a capture, which writes a maximum (0 where none) on every station the railway holds, so no maximum from the file ever reaches the configuration in use; recommendation (a)'s wording ("settings at their default take the file's") describes the other half only.

### RLU4-D4 - RLU3-C1 and RLU3-C2 in the program: the either-way sentence names no side in any language, and the new sentences are chosen as the dispositions say

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |
| **Where** | `Layout.java:5052-5084`, `:5017-5028`; `test/core/testWhyStuck.java:676-786` |

`whyItReachesNoStation` is asked only after `whyTheStartIsRefused` returns nothing, so the train's own copy is a station, open and switched on.  The loop skips copies that reach no station; the first that also passes `whyNoTrainIsStartedFrom` gives "turn it round"; otherwise the first refused one picks Barred or Refused; none gives the either-way sentence with `menuAutoDestination` alone.  All eight bundles dropped `{2}` and its clause, and the one caller passes two arguments; `testWhyStuck:706` still passes three, which the formatter ignores.  RLU3-C1's `assertFalse` was red at `cca48e96`, where `{2}` was the arrivals menu; the WS11 and WS12 claims name keys that did not exist at `cca48e96`, so both were red first.  What they leave is RLU4-C4 and RLU4-C6.

### RLU4-D5 - An old file into the configuration in use, with nothing moving: the capture is inside the snapshot, the trains are named and not placed, every message is on the main window, and the census allowance is true

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |
| **Where** | `AutonomyViewerPanel.java:1193`, `:1254-1280`, `:1330-1335`, `:1393-1402`, `:1411-1422`; `AutonomyCompanionStore.java:2459-2500`; `AutonomySession.java:1134-1147`; `Layout.java:11879`; `test/regression/testNothingOnTheEventThreadTakesTheRailwaysMonitor.java:247-249`; `testTheImportDoorReadsAnOldFile.java:782` |

`snapshotSetup` (the shared half, every configuration, the active name) is taken at `:1193`, before the capture at `:1269`, so a file that throws puts the capture back with everything else.  `placeTrains` is false only for the configuration running; a train that configuration has standing elsewhere is still named under `infoLegacyAlreadyPlaced`, the rest under `infoLegacyNotPlacedInUse`, whose `{1}` is the configuration in all eight languages.  The last line reads "Imported into the configuration {0}", and every dialog is owned by `ui`.  The census's new allowance is true: `Layout.toJSON` is synchronized, and is called here only while `isAutonomyBusy()` is false.  The claim's change in the fix commit fills the pattern's `{1}` and nothing else; at `cca48e96` it was red at `assertNotNull(placed, ...)`, the import refused, which is the reason it names.  In memory, the MT-491 file places four trains and Main has three of them standing, so ET22-245 is the one the line has to name.

### RLU4-D6 - The eight bundles at HEAD: 1,841 keys each, and the four keys round 3 changed say the same thing in every language

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |
| **Where** | `src/org/traincontrol/resources/messages*.properties` at `cca48e96` and HEAD |

Read in memory: all eight hold the same 1,841 keys (1,838 and the three new), no duplicates, every key's placeholder set equal to English's, no byte above 127, no straight apostrophe in a value with a placeholder, and the only empty values Italian's and Polish's plural suffix.  Between `cca48e96` and HEAD exactly four keys changed, in every language: `startReachesNoStationEitherWay` (its `{2}` and clause gone), `startReachesNoStationOtherWayRefused`, `startReachesNoStationOtherWayBarred` and `infoLegacyNotPlacedInUse` (`{0}` the trains, `{1}` the configuration).  Decoded and read in all eight, each says the same as English; the menu is quoted in each bundle's own marks, as its neighbours quote it.

### RLU4-D7 - A true bundle under the running configuration's name is still refused before anything is asked or written, on the main window, and its claim imports what Export writes

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |
| **Where** | `AutonomyViewerPanel.java:1060-1073`; `test/regression/testTheImportDoorReadsAnOldFile.java:617-686` |

The refusal now sits after the file is read and its kind known, and still before the question (`:1075`) and before anything is created or written (`:1109`); it is owned by `ui`, and its `{1}` is the heading of the submenu Import sits in.  The claim writes `exportBundle(inUse)` to a temp file, imports it from the menu under that name, and asserts the refusal, the configuration, the names and setup.json unchanged.  What it does not reach is RLU4-B1.

---

Counts: no A, 1 B, 8 C, 7 D.
