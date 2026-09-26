# Release-readiness validation, round 4 - documentation and tests (TrainControl 3.0.0)

**Status:** open

**Prefix:** RLD4

**Reviewed:** branch `autonomy-diagram-r0` at `f7e94a50` (working tree clean by `git --no-optional-locks status --porcelain`), round 3's commits `d779578b..HEAD` (claims `cca48e96`, fixes `81a9fffa`, records `f7e94a50`), on 2026-09-25.

## Method

Read-only.  Nothing was compiled, run or started; no JVM of any kind.  No git state was changed: only `git log`, `git show`, `git grep`, `git ls-tree`, `git diff --name-status`/`--stat` between commits and `git --no-optional-locks status`.  Nothing under `cs2_sample_layout/` was read or written.  The only file written is this report (and its folder, which already existed with the other lanes' reports in it).  Six mechanical checks were made in memory by inline `python -` heredocs that wrote nothing: (1) the eight bundles at `81a9fffa^` and HEAD through `git show` - key sets, duplicates, placeholder sets against English, bytes above 127, empty values, straight apostrophes in values with a placeholder - and the five import and Why-not-Moving keys the round added, changed or now reaches differently, decoded in every language; (2) the orphaned-javadoc rule re-implemented from `testJavadocsAreAttached.orphansIn` over every `src/` file at `cca48e96^` and HEAD, total and per file; (3) the live-snapshot README's "Used by" list against every test class that names `"live-snapshot"`; (4) `docs/manual-tests/triage.db` opened read-only (`mode=ro` URI) - row and distinct-ref counts, every RLA3, RLU3 and RLD3 row's disposition against its document's (45 of 45), the rows not closed, and the full rows of RLA-C2, RLU-C4, RLA2-C4, RLU2-C11 and RLD2-C2; (5) tests.md's headings and dispositions, and issues.md's Inbox by kind; (6) every MT entry with a 2026-09-25 comment, with the tests it names.

What was read:

- The brief; `docs/reviews/README.md`; my lane's `RLD3.md` whole with every disposition; `RLA3.md` and `RLU3.md` whole; `RLD2.md`'s RLD2-C2 and RLD2-C3; `RLA.md`'s RLA-C2; the dispositions of RLA2-B1, RLA2-B3 and RLA2-C4; the supersession rule in `docs/manual-tests/README.md`.
- The three commits in full with `git show`; `2fa033f3`'s `importLegacyGraph`, for round 1's capture-first.
- Around the fixes, at HEAD: `AutonomyViewerPanel` - `importConfiguration`, `importLegacyGraph`, `activateTheConfigurationNamed`, both `loadAfterImport`s, `load`, `save`, `delete`, `exportConfiguration`; `AutonomyMenu`'s Configuration submenu, `item`, `guardWhileEditing` and `deleteEverything`; `AutonomySession.importLegacy` (both overloads, the placement, home and carried-settings branches), `LegacyImport`, `captureFromLayout` (both), `POINT_OPERATIONAL_KEYS`, `configurationToLoadAfterImport`; `TrainControlUI.isAutonomyBusy`, `prepareAutonomyReload`, `captureRunningLayout` and its javadoc, the exit save's fold, `rebuildRunningLayoutFromSetup` and `setupEditDeclinedDuringRun`, `autonomyMenuIsUsable`; `AutonomyEditorPanel.rebuildRunningLayoutSoon`; `LayoutRightclickAutonomyMenu.addSetupMenu` and its two `AutonomyReport.show` sites; `Layout.whyItReachesNoStation`, `whyTheStartIsRefused`, `whyNoTrainIsStartedFrom`, `isRunning`; the sync comment in `MarklinControlStation`; `MarklinLocomotive:1180`; the round's comment changes in `AutonomySession`, `GraphReducer` and `AutonomyEditorPanel`.
- Tests: `testTheImportDoorReadsAnOldFile` - the refusal claim, the two new claims, `testASecondImportFromTheMenuKeepsAHandMadeChange`, which configuration each other method imports into, and the helpers (`standingIn`, `placedIn`, `importFromTheMenu`, `Answerer`, `before`, `maxOf`); `testWhyStuck`'s three either-way tests; `testATrainIsPutOnlyWhereItCanStart`'s GUI4-C3 claim; the pin in `testWhereHisTrainsMayBeSent`; `testMessageBundles`' javadoc; `testJavadocsAreAttached`; the new allowance in `testNothingOnTheEventThreadTakesTheRailwaysMonitor`; the window census in `testSwitchingToACentralStationLayout`.
- Documents: behaviour.md's import (:2428-2451), import-facing (:2030-2046), Why-not-Moving (:705), sync (:1957-1960) and store-count (:2486) paragraphs; `Automation.md:311`; open-questions.md's Inbox, Setup and store-count paragraphs; issues.md's Inbox and OB-303 to OB-305; tests.md's ledger and MT-298; Readme.md's 3.0.0 entry for import and Why not Moving?.
- `docs/manual-tests/findings.tsv` searched before each finding: the prefix RLD4 is free; declined, captureRunningLayout, import with reload, busy or running, MT-298, RLD3-C4, GUI4-C3 and turnsEveryTrainAt, Automation.md, the bundle claim.  Nothing below repeats a catalogued row beyond the ids it names.

Not reported, as the brief says: RLA3-B1 with RLU3-C4, OB-300 to OB-305, RLU2-C9 and RLD-C1.  Everything below is from reading; each finding that needs a run says so, with the fixture and what proves or refutes it.

Counts: no A, no B, 9 C, 7 D.  **Nothing new above C** - by the brief's measure, the signal to stop.  The C findings are: one door round 3 reopened (an import while autonomy runs, RLD4-C1) and one fold it added that skips a guard (RLD4-C3); four claims that would not catch their own regression (RLD4-C2, C4, C6, C9); and records the round left behind (RLD4-C5, C7, C8).

---

### RLD4-C1 - RLD3-C1's fix while autonomy runs: an old file imported into the running configuration keeps none of the homes and settings it brought - the capture-first is skipped, the reload's capture writes the running railway back over them, and behaviour.md states the rule with no exception

| | |
|---|---|
| **Disposition** | Fixed as RLA4-C1. |
| **Grade** | C - RLA-C2's consequence (graded C there) through the one case round 3's fix leaves open: homes and settings the import's dialog has just counted, then undone.  Nothing moves that was not told to. |
| **Names** | RLD3-C1 ("with the running layout captured first"), RLA-C2 (its R9), RLA2-B1 |
| **Where** | `AutonomyViewerPanel.java:1264` (`&& !ui.isAutonomyBusy()`), `:1280`, `:1406`; `load` `:763`, `:773-786`; `AutonomySession.captureFromLayout` (each `POINT_OPERATIONAL_KEYS` key replaced, or removed where the layout has none); `AutonomyMenu.java:332` (Import is never greyed) against `AutonomyViewerPanel.delete` (:1535) and `AutonomyMenu.deleteEverything` (:674), both refused while busy; `behaviour.md:2435-2438` |
| **Needs execution** | yes |

Import is never greyed, and nothing in `importConfiguration` asks whether autonomy is busy.  Into the running configuration by name while a run is going (or staging, or trains coasting after a graceful stop - all `isAutonomyBusy`): `intoTheOneRunning` is true, so the file places no train; the capture-first is skipped by its `!ui.isAutonomyBusy()`; `importLegacy` fills the configuration's gaps - homes, `active`, priority, speed, maxima; the setup is saved and the dialog counts them; then `loadAfterImport(into, !captured)` passes true and `load` asks to stop the trains.

- **Yes:** `load` captures the running layout into the running configuration before it rebuilds, and the capture replaces or removes each operational key on every point the running layout holds.  In the fix's own comment (:1255-1256): *"captured on the reload, as every reload is, it wrote the running railway back over the homes and settings the import had just brought."*
- **No:** nothing reloads.  setup.json keeps the writes and the running layout does not have them, so the next fold - the exit save, an editor door's `captureRunningLayout`, a load - removes them, unless a setup edit's rebuild reaches them first.  The import sets no `setupEditDeclinedDuringRun`, the flag that holds a setup write made during a run against the exit's and the editor doors' folds (ACC-B3).

The comment's last sentence (:1258-1259), *"Not while trains are moving: the reload stops them and captures where they stopped, as it always has"*, is true; it is the capture the sentences before it call the defect.  behaviour.md:2435-2438 says *"what the running layout knows goes into the configuration first, the reload after the import does not capture again"*, with no exception.  No claim reaches the branch: both new claims import with nothing running.  Round 1's fix (`2fa033f3`) had the same exception, round 2's refusal covered it, and round 3 restored it.  The exception itself is right - a capture taken while trains move, and not repeated, would record where they were rather than where they stop (OB-183) - so the remedy belongs at the door.

**Direction.** Refuse an old-file import into the running configuration while `isAutonomyBusy()`, as Delete and Delete Setup are refused (`autolayout.errorCannotEditWhileRunning`), naming the remedy (stop autonomy first, or import under another name); behaviour.md says so; a claim with autonomy busy.

**Verification request.** `testAnOldFileIntoTheConfigurationInUseLeavesItsTrainsToTheRailway`'s fixture: live-snapshot sandbox, his configuration running.  First open a gap the file fills on a square the running layout holds: take off a home the MT-491 file gives (`setPointProperty(square, "home", null)`, then `load(inUse, false, false)` as the editor's rebuild does), and assert the configuration has none for that train.  Make autonomy busy (start it on the sandbox's simulated railway, or set `TrainControlUI.stagingFlowActive` by reflection).  Import MT-491 from the menu under the running configuration's name, answering Yes to the fill-gaps question and Yes to stopping the trains.  **Proves:** the import's message does not name that train among the homes kept, and after the reload the configuration has no home for it.  **Refutes:** the home is there after the reload.

### RLD4-C2 - RLA-C2's other half is unclaimed again: with the reload capturing after the import (R9), both of round 3's claims for the configuration in use stay green

| | |
|---|---|
| **Disposition** | Fixed as RLA4-C2. |
| **Grade** | C - a test that would not catch its own regression |
| **Names** | RLD3-C1's claims (`cca48e96`); RLA-C2 (*"mutation R9 red"*, held then by placements: *"placed 3, the reload took all 3 back"*); RLD2-C3 |
| **Where** | `testTheImportDoorReadsAnOldFile.java:688-801` (`testAnOldFileIntoTheConfigurationInUseLeavesItsTrainsToTheRailway`), `:802-889` (`testASecondImportIntoTheConfigurationInUseKeepsAHandMadeChange`); `AutonomyViewerPanel.java:1406`; `:675-678` and `:1131` (the one-argument `loadAfterImport`, which captures, used by the bundle branch) |
| **Needs execution** | yes |

RLA-C2's fix has two halves.  Capture first, so a train a run moved keeps its square (RLD2-C3).  And no capture on the reload, so what the import brought is not folded away (R9).

The in-use claim holds the first half: its moved train is RLD2-C3's fixture, red with the capture deleted and `captured` left true.  Nothing holds the second.  Round 1's claim held R9 through placements, and round 3 places none into the running configuration.  So the only things a reload's capture can undo there are homes and settings, and neither claim asserts one:

- **The in-use claim** asserts placed 0, the same names standing, the moved train at its new square, and the not-placed line.  Each is true under either capture: the moved train is in the running layout that both captures read.
- **The MT-298 claim**'s hand change is in the running layout too, because the claim rebuilt the railway after making it.  So a capture on the reload writes the same maximum back.

The capturing overload is one edit away at :1406, and the bundle branch calls it at :1131.  The in-use claim's javadoc says the import *"fills what it does not have"*, but the claim asserts nothing filled.

**Direction.** In the in-use claim, open a gap the file fills on a square the running layout holds (as in RLD4-C1's request, with nothing running), and assert the gap is filled after the import.

**Verification request.** Mutation: `loadAfterImport(into, true)` (or `loadAfterImport(into)`) at :1406.  Run the two claims.  **Proves:** both green.  **Refutes:** either red.

### RLD4-C3 - The import's capture-first is a fold that does not ask the declined-edit flag: a setup edit declined as a run started, and not yet rebuilt, is written over by an import into the running configuration - and `captureRunningLayout`, which asks, still calls itself the only fold

| | |
|---|---|
| **Disposition** | Fixed - the capture before an import and `load`'s capture ask whether a declined edit waits for its rebuild, as the exit save and the editor doors do; captureRunningLayout's javadoc names every fold.  claims 6f02ff2e (red first), fix d5b97ee8; mutations V5 red, V6 red. |
| **Grade** | C - ACC-B3's and WKW-B2's consequence (both graded B: an edit the log promised to keep is silently deleted), through a door reached only after a race-declined edit and before any rebuild |
| **Names** | RLD3-C1; ACC-B3, WKW-B2, AMS-B1 |
| **Where** | `AutonomyViewerPanel.java:1264-1278`; `TrainControlUI.java:596` (the flag), `:2540` (the exit save asks it), `:2968` and `:3015` (`captureRunningLayout`: *"This is the ONLY thing that does that"*, and it asks); `AutonomyEditorPanel.rebuildRunningLayoutSoon` (where the flag is set); `messages.properties:396` (`autosetup.log.setupEditNotApplied`) |
| **Needs execution** | yes |

`setupEditDeclinedDuringRun` marks a configuration on disk that is newer than the running layout: an edit whose coalesced rebuild found that a run had started in between.  The log promises the edit *"will be picked up the next time the setup is loaded"*.  The exit save and `captureRunningLayout` (the fold behind opening and closing an editor, re-downloading, and renaming or deleting a page - WKW-B2) refuse to fold while the flag is up.

The capture-first calls `session().captureFromLayout` directly and asks only `!isAutonomyBusy()`.  Suppose the run has stopped and nothing has rebuilt since.  An old file imported into the running configuration then folds the pre-edit layout over the edit, replacing or removing every operational key on each point.  The reload after it does not capture, but it builds from the folded configuration, so the edit is gone.

The records say otherwise.  `captureRunningLayout`'s javadoc names three callers and calls itself the only fold, yet `load`'s own capture (:773) and the exit save's (:2556) already call `captureFromLayout` directly.  The exit save asks the flag and `load` does not.  The import is now a third direct fold.

`load`'s capture is the same gap when the Autonomy menu chooses a configuration.  That predates the round and is not catalogued (searched: declined, captureRunningLayout).

**Reach:** only after the race, since the right-click's setup menu is refused while autonomy is busy (`LayoutRightclickAutonomyMenu.java:928`), and then only this import, before any setup gesture rebuilds.  Hence C.

**Direction.** Fold through one method that asks the flag - route both of `AutonomyViewerPanel`'s folds through `TrainControlUI`, or expose the flag - and correct the javadoc.

**Verification request.** Live-snapshot sandbox, his configuration running, autonomy not busy.  Write a setting the MT-491 file does not carry for that square (a priority on a station the file gives none) with `setPointProperty` and no rebuild, and set `setupEditDeclinedDuringRun` true by reflection, as a race-declined edit leaves them.  Import MT-491 from the menu under the running configuration's name, Yes.  **Proves:** the priority is gone from the configuration after the import.  **Refutes:** it is there.  **Control:** `captureRunningLayout` (by reflection) with the flag up leaves it.

### RLD4-C4 - RLD3-C2's claim imports a bundle identical to the configuration it would replace, so only its message assertion bites: a refusal that shows its message and then falls through to "Replace it?" can stay green

| | |
|---|---|
| **Disposition** | Fixed - the bundle claim asserts the replace question is not asked.  Claims 6f02ff2e; mutation V7 red. |
| **Grade** | C - a test that would not catch its own regression (a fixture that supplies the answer) |
| **Names** | RLD3-C2 (*"the refusal's claim imports a bundle (cca48e96); mutation U1 red"*) |
| **Where** | `testTheImportDoorReadsAnOldFile.java:617-685`; `AutonomyViewerPanel.java:1067-1072` |
| **Needs execution** | yes |

The bundle is `exportBundle(inUse)`: the configuration it is then imported over, as Export writes it.  That is the right case (the backup round trip), but it makes the three before-and-after comparisons weak:

- **What they compare:** the configuration's JSON, the list of names, and setup.json.
- **What a replacement would show them:** little.  Replacing a configuration with its own export changes nothing they compare.  What remains is whatever the reload's capture adds: a `maxTrainLength` on every station, and the tail keys of occupied copies.  On a configuration that has run, and been captured on exit, those are the same values.

The assertion that bites is the message, so moving the check (RLD3-C2's mutation) is red.  Deleting the `return;` at :1071 is not caught, however:

- the refusal is shown;
- then the replace question is asked, and the answerer answers Yes;
- the configuration is replaced by itself.

Nothing asserts that the question was not asked.

**Direction.** Assert that `said` does not contain `confirmImportOverwrites`, or change one maximum in the configuration after the export so that a replacement is visible.

**Verification request.** Mutation: delete `return;` at `AutonomyViewerPanel.java:1071`.  Run `testAnImportIntoTheConfigurationInUseIsRefused`.  **Proves:** green.  **Refutes:** red.

### RLD4-C5 - RLU3-C1 and RLU3-C2 fixed the sentences, not the documents: the user guide and behaviour.md still send the operator to open a side under Trains May Arrive..., and name neither new sentence

| | |
|---|---|
| **Disposition** | Fixed in the records, as RLA4-C3 (d5b97ee8). |
| **Grade** | C - the user guide still gives the remedy RLU3-C1 took out of the sentence because, at a station every train turns round at, following it raises the error that refuses the whole setup |
| **Names** | RLU3-C1 (its Where names `Automation.md:311` and `behaviour.md:705`), RLU3-C2 (names `Automation.md:311`), GUI4-C3 |
| **Where** | `Automation.md:311`; `docs/reference/behaviour.md:705`; `autolayout.why.startReachesNoStationEitherWay`, `...OtherWayRefused`, `...OtherWayBarred` |
| **Needs execution** | no |

`81a9fffa` changed behaviour.md only at :1960 and :2433-2448, and did not touch Automation.md.  Both documents still carry the side remedy the sentence has now dropped:

- **Automation.md:311** - the guide Adam's users read - still ends: *"switch that station on, tick Can Be Chosen in Full Autonomy, and open the side a train would arrive on under Trains May Arrive..."*.
- **behaviour.md:705** still ends: *"... and the side a train would arrive on open under Trains May Arrive... (RLU-C4, RLU2-C10)"*.
- **The sentence** now ends *"switch it on and tick "{1}""*.

Neither document says what Why not Moving? now says in the two new cases:

- **Where the other way reaches a station** but autonomy starts no train facing it: *"Drive it off by hand."*
- **Where that way is barred:** *"open that side ... and turn it round"*, offered only where not every train turns there.

**Direction.** Rewrite both paragraphs as the three sentences have it.

### RLD4-C6 - RLU3-C2's GUI4-C3 exception has no claim: "open that side and turn it round" can be offered again at a station every train turns round at, with every test green

| | |
|---|---|
| **Disposition** | Fixed as RLA4-C4; testWhyStuck's third argument is read again, since the either-way sentence names the side (RLU4-C4). |
| **Grade** | C - a test that would not catch its own regression; the regression is RLU3-C1's (a remedy whose following makes autonomy refuse the whole setup) |
| **Names** | RLU3-C2 (*"mutation U5 red"*), RLU3-C1, GUI4-C3 |
| **Where** | `Layout.java:5074`; `test/core/testWhyStuck.java:767-786` (the WS12 claim), `:704-708`; `test/core/testATrainIsPutOnlyWhereItCanStart.java:829` (the own-square sibling's claim) |
| **Needs execution** | yes |

The new branch gives `startReachesNoStationOtherWayBarred` only where `!turnsEveryTrainAt(refused)`, and the refused sentence otherwise.  That is GUI4-C3's rule (Adam, 2026-09-24: *"offer the other two remedies only"*), applied to the other copy.

Neither of the branch's claims reaches the exception:

- **The barred sentence's one claim (WS12)** builds a platform that trains may leave both ways, so it never meets a station every train turns at.
- **`OtherWayRefused`** is asserted only for a switched-off copy (WS11).

Delete `&& !turnsEveryTrainAt(refused)` and every test stays green.  A train at a terminus reached from two sides is then told to open the side whose closing keeps the setup loadable.  The own-square sentence has a claim for this rule (`startFacingBarredMustTurn`, :829), but its sibling here does not.

Separately, `testACopyThatReachesNoStationSaysSo` (:704-708) still passes Trains May Arrive... as a third argument to the either-way sentence.  That sentence now has two placeholders.  The extra argument does not affect the comparison, but it reads as though the sentence still names the side.

**Direction.** Add a WS12-shaped claim whose station is one every train turns round at, asserting `startReachesNoStationOtherWayRefused`, and drop the third argument.

**Verification request.** Mutation: delete `&& !turnsEveryTrainAt(refused)` at `Layout.java:5074`.  Run `core.testWhyStuck`, `core.testATrainIsPutOnlyWhereItCanStart` and `ui.testThePlaceDoorsKeepTheHeading`.  **Proves:** green.  **Refutes:** red.

### RLD4-C7 - RLD3-C5 is fixed in one of the two sentences it named: behaviour.md:2038 still says the import guesses a facing trains may arrive in wherever the edges cannot say, which :2446 now contradicts

| | |
|---|---|
| **Disposition** | Fixed in the records, as RLA4-C5 (d5b97ee8). |
| **Grade** | C - records |
| **Names** | RLD3-C5 (*"Fixed in the records (81a9fffa)"*; its Where named `behaviour.md:2444-2445` and `:2038-2039`), RLA3-C4, OB-304 |
| **Where** | `docs/reference/behaviour.md:2037-2039`, `:2445-2446` |
| **Needs execution** | no |

`81a9fffa` rewrote :2445-2446 to say *"where the file cannot say, the last occupant's stays"*.  :2038-2039 is unchanged: an import *"takes each train's facing from the side its point's one-way edges leave by, guessing one trains may arrive in only where they cannot say (REG3-C1, REG4-A1, REG4-C1)"*.

That is not what the code does where the edges cannot say and the square records a facing.  There the code keeps the recorded facing, which is neither a guess nor REG3-C1's preference for a way trains may arrive; that gap is OB-304.  So the document now states two rules for the same case.

**Direction.** Add the same clause at :2038, naming OB-304.

### RLD4-C8 - Round 3 reversed round 2's import rule for old files and left the records of that rule standing: four dispositions and three D findings still say the door refuses and the capture is gone, and RLA-C2's and RLU-C4's rows do not carry the supersession RLA3-C6's disposition says they do

| | |
|---|---|
| **Disposition** | Fixed in the records - status notes on the rows (RLU4-C8), the bundle claim's javadoc, and the Inbox's recount date. |
| **Grade** | C - records; each is what a reader querying the id is told once the round's documents are deleted |
| **Names** | RLA3-C6 (*"RLA-C2's and RLU-C4's round-1 rows say what superseded them"*); RLA2-B1, RLA2-B3, RLD2-C2, RLD2-C3; RLD3-D2, RLA3-D1, RLU3-D7 |
| **Where** | `docs/reviews-2026-09-25/RLA2.md:21`, `:65`; `RLD2.md:49`, `:69`; `RLD3.md:142-149`; `RLA3.md:300-321`; `RLU3.md:182-190`; `triage.db` rows RLA-C2 and RLU-C4; `testTheImportDoorReadsAnOldFile.java:606-610`, `:617`; `open-questions.md:42` |
| **Needs execution** | no |

- **The dispositions round 3 reversed.** Each was true at `d779578b` and is false for an old file now, and none has a dated line saying so; the round gave one to RLA-C2, RLU-C4, RLA2-C4 and RLU2-C11:
  - **RLA2-B1** says Import refuses the name of the configuration in use and the round-1 capture is gone.  Since `81a9fffa` an old file is imported under that name, and the capture is back (:1264).
  - **RLA2-B3** says *"on the running railway by RLA2-B1's refusal"*.  It now holds by placing none (RLD3-C1).
  - **RLD2-C2** says *"Fixed as RLA2-B1"*.
  - **RLD2-C3** says *"Fixed as RLA2-B1 - the capture it asked to pin is gone"*.  The capture is back, and the in-use claim's moved train now pins it.
  - **Three D findings** say the same: my RLD3-D2 (*"the capture ... is gone"*), RLA3-D1 (*"for both kinds of file"*) and RLU3-D7 (*"the round-1 capture has gone with its loadAfterImport(name, boolean) overload"* - the overload is back too).
- **RLA-C2's and RLU-C4's store rows.** `f7e94a50` appended *"Superseded 2026-09-25: ..."* to both dispositions in the documents.  But the store keeps only a disposition's first 120 characters, and each status note is the same prefix.  So both rows still read only the round-1 fix: *"the running layout is captured first and the reload after the impor"* and *"... not one where t"* (trains turn round).  That is RLA3-C6's case unchanged.  RLA2-C4 and RLU2-C11 avoided it with a status note of their own (*"superseded by RLA3-B1"*).
- **The bundle claim's javadoc.** Its first paragraph now says bundle, but its second still argues that *"a file belongs in a configuration of its own"*.  Its name, `testAnImportIntoTheConfigurationInUseIsRefused`, cited from the records, still states round 2's rule.
- **open-questions.md:42.** *"Today it holds 92 entries ... recounted from the file on 2026-09-24"*.  The count is right (60 OB, 32 FR), but it was recounted on 2026-09-25.

**Direction.** Five changes:

- a dated supersession line under each of the seven, naming RLD3-C1;
- a status note on the RLA-C2 and RLU-C4 rows, as RLA2-C4 has;
- the bundle claim's second paragraph rewritten to the bundle case;
- a sentence saying an old file is not refused, in place of a rename that would break the citations;
- the date corrected.

### RLD4-C9 - RLD3-C4's remainder rides on nothing, and round 3's statuses disagree about one defect: RLD3-C4 is "Open - deferred" with no Inbox entry and no reason, while RLD3-C3 reads Closed where RLA3-C1 and RLA3-C2 - the same two gaps, under the same OB-303 - read "Open - deferred"

| | |
|---|---|
| **Disposition** | Fixed - the pin counts the tidy report among the messages of both menus and the viewer panel (claims 6f02ff2e); RLD3-C3 has its twins' status, Open - deferred as OB-303; mutation V10 red. |
| **Grade** | C - records, and a pin that would not catch its own regression at two of its three sites |
| **Names** | RLD3-C4 (*"Fixed in part - the viewer panel's report is pinned (cca48e96); the right-click menu's two report calls are on the main window already and read by no pin"*); RLD3-C3, RLA3-C1, RLA3-C2, OB-303; RLU3-C3 |
| **Where** | `test/ui/testWhereHisTrainsMayBeSent.java:231-237`; `LayoutRightclickAutonomyMenu.java:1358`, `:1599`; `triage.db` rows RLD3-C3 and RLD3-C4; issues.md's Inbox |
| **Needs execution** | yes - the mutation |

RLD3-C4 asked the pin to read `AutonomyViewerPanel.java` the way it reads the menus, and to count `AutonomyReport.show(` as a message.  `cca48e96` added one `contains` of the literal `AutonomyReport.show(ui, session().save())`.  That proves the call is there.  It does not prove that no message in the panel is owned by the panel.  Today all 29 `JOptionPane.show` calls and the one report pass `ui`.

Two gaps remain:

- **The unpinned sites.** The right-click's two `AutonomyReport.show(ui, session.save())` calls are still read by no pin.  The disposition gives that as its reason, which only restates the finding.
- **No carrier.** Every other split disposition of the round names the OB that carries the rest (RLA3-C4, RLD3-C3 and RLD3-C6 do).  RLD3-C4 names none, and no Inbox entry mentions it.

The statuses disagree too:

- **RLD3-C3** (*"Fixed in the records; the member half's claim is filed as OB-303"*) reads Closed.
- **RLA3-C1 and RLA3-C2** report the same running-members defect and the same missing claim, are carried by the same OB-303, and read Open - deferred.

So a query for the round's open work finds one of them and not the other.

**Direction.** The pin counts `AutonomyReport.show(` among the messages of the two menu files it already reads, or the remainder is filed.  RLD3-C3 is given the status its twins have.

**Verification request.** Mutation: `AutonomyReport.show(this, session.save())` at `LayoutRightclickAutonomyMenu.java:1358`.  Run `ui.testWhereHisTrainsMayBeSent`.  **Proves:** green.  **Refutes:** red.

---

### RLD4-D1 - RLD3-C1 with nothing running: an old file into the configuration in use is read, captured first, gap-filled, places no train and names the trains it left; the reload does not capture; MT-298's new claim runs its steps at its door

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |
| **Where** | `AutonomyViewerPanel.java:1060-1072`, `:1254-1280`, `:1330-1335`, `:1406`; `AutonomySession.importLegacy` (the branch after `standingAlready`); `testTheImportDoorReadsAnOldFile.java:802-889`; `tests.md:16812-16814` |

**The door.** The refusal now sits after the file is read (:1067) and fires for a bundle only.  An old file reaches `importLegacyGraph`, where `intoTheOneRunning` does two things: it gates the capture-first, and it passes `placeTrains = false` to the new `importLegacy` overload.  That flag's only effect is one branch, placed after the unknown-name and already-standing branches:

- unknown and already-standing trains are named as before;
- every other train the file places goes to `notPlacedInUse`, which the dialog names together with the configuration (`infoLegacyNotPlacedInUse`, in all eight languages);
- no facing is asked, since `facingToFind` is filled only on the placement branch.

`loadAfterImport(into, !captured)` then reloads without a second capture.  A file train whose square the capture gives to another train is not named; that is OB-304's silent skip, already filed.

**The ruling.** The rule is the second of RLD2-C2's two options, the one RLA-C2 itself called Adam's rule (OB-183, *"where a train IS is a fact"*), so the ruling RLD2-C2 asked for exists.  The bundle refusal is RLA3-D1's note for Adam.

**MT-298's claim.**

- It chooses the imported configuration through `load`.  That call is not interactive, where the menu's is; the difference is only in how a failure is reported.
- It changes a maximum as the editor's rebuild does (`load(name, false, false)`).
- It imports the file into that configuration by name through the menu.
- It asserts that the import ran and the change stayed.  That is every outcome the Expected names as the 2026-09-24 comment amends it: *"the import works, and after the second import your changed maximum is still there"*.
- A station missing from the fixture is a failed precondition, not a skip.

Where the claim falls short is RLD4-C1 and RLD4-C2.

### RLD4-D2 - RLD3-C6: each item is fixed as its disposition says

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |

- `MarklinLocomotive.java:1180` names the Central Station sync's sweep.
- `AutonomyMenu.java:332-335` says what Import does into the configuration running.
- `loadAfterImport`'s javadoc (:661-672) and the new overload's (:680-686) say what each loads and when it captures.
- `duplicateHomes` is carried by OB-304.
- The ratchet has its dated line for 87 -> 86.
- `standingIn` is used by the in-use claim, and the class javadoc names "the question about a configuration of that name".
- `testMessageBundles`' javadoc names the method that holds a key asked for by another road, and its MUTATION line is narrowed to what the method sees.

### RLD4-D3 - RLD3-C3's records: the sync's comment and behaviour.md say what the code does, and OB-303 carries the defect and both claims

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |

`MarklinControlStation.java:1714-1716` and behaviour.md:1960 say that new members of a Central Station multi-unit are taken at once, and that one arriving during a run is swept by the next load or Place.  That holds: placing sweeps through `clearMultiUnitConflictsWith` (`Layout.java:9617`, `:12710`).  OB-303 names the running members, the two unclaimed halves and the redraw.  RLD3-C3's status is RLD4-C9.

### RLD4-D4 - The eight bundles at HEAD: 1,841 keys each, with round 3's three new keys and its one changed sentence in all eight, carrying English's placeholders

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |

Read in memory at `81a9fffa^` and at HEAD:

- 1,838 keys, then 1,841, in each of the eight files;
- no duplicates, and nothing missing or extra;
- every placeholder set equal to English's;
- no byte above 127, and no straight apostrophe in a value with a placeholder;
- the only empty values are Italian's and Polish's `stats.ui.valuePluralSuffix`.

Decoded in all eight languages:

- `infoLegacyNotPlacedInUse`: `{0}` the trains, `{1}` the configuration.
- `startReachesNoStationOtherWayRefused`: `{0}`.
- `startReachesNoStationOtherWayBarred`: `{0}`, with `{1}` the Trains May Arrive... label the code passes.
- `startReachesNoStationEitherWay`: the side clause and its `{2}` are gone in every language.
- `errorImportIntoConfigurationInUse`: unchanged, and now reached for a bundle only, which its words fit ("Import the file under another name").

### RLD4-D5 - The ratchet, the live-snapshot README, build.xml and the window census need nothing; the census's new allowance is `load`'s, for the same capture

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |

- **The ratchet.** The orphan rule, re-run in memory, finds 86 in 21 files at `cca48e96^` and at HEAD, each file matching `ORPHANS_BY_FILE`.  `81a9fffa`'s new javadocs (the two overloads, `notPlacedInUse`) each sit on a member.
- **build.xml and the window census.** `git diff --name-status --diff-filter=ADR d779578b HEAD` lists only the three round-3 documents.  No test class was added or removed, so build.xml's registrations (`testTheImportDoorReadsAnOldFile` at :541) and the window census (64 classes) stand.  The two new methods open the sandbox as the first statement inside the try, and dispose the window in `finally`.
- **The live-snapshot README.** "Used by" names 78 classes, exactly the 78 that name `"live-snapshot"`.
- **The monitor census.** The allowance for `AutonomyViewerPanel.java#importLegacyGraph` covers the same capture `load` makes, behind the same `!isAutonomyBusy()`.  Its "nothing is holding the monitor" overstates one case: round 2's Central Station sweep holds the layout off the event thread while autonomy is not running.  But that sweep hands the event thread only an `invokeLater` log line (RLA3-D2), so the wait is short and cannot deadlock, and `load`'s allowance carries the same exposure.
- **Readme.md.** The round did not touch it.  Its 3.0.0 entry describes the import (:374) and Why not Moving? (:386) in terms the round did not change.

### RLD4-D6 - The round's counts and rows agree with the store and the files

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |

- **The store.** triage.db holds 4,618 rows for 4,261 refs, as behaviour.md:2486 and open-questions.md say, and 4,573 + 45 = 4,618.  RLA3 has 14 rows, RLU3 15 and RLD3 16.  All 45 round-3 dispositions match their documents' up to the store's 120-character cut.  The rows not closed are RLA3-B1 and RLU3-C4 (Adam's decision), and RLA3-C1, C2, C3 and C5, RLU3-C5 and RLD3-C4 (deferred): 37 closed, as `f7e94a50` says.
- **The Inbox.** issues.md's Inbox holds 60 OB and 32 FR, 92 in all.  OB-303 to OB-305 say what the dispositions filing them say.
- **tests.md.** 586 headings: 453 fixed validated, 121 superseded, 10 fixed unvalidated and 2 needs test.  The ledger has its 12 rows and "574 of 586".
- **The superseded MTs.** MT-298's new comment names the method and says what it asserts.  Every other MT entry with a 2026-09-25 comment names tests the round did not change, with one exception: the import class.  Its other methods import into configurations of their own, which the new branch does not reach.

### RLD4-D7 - The final battery must show the round's display-needing claims passed, not skipped

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right, on the record |
| **Needs execution** | yes - the release's final battery |

Both new import claims throw `SkipException` without a display, as the refusal claim does and as the two methods RLD3-D8 named can.  MT-298's supersession rests on "green in a full battery".

**Verification request.** From the release's final battery, the per-method status of five methods:

- `testAnOldFileIntoTheConfigurationInUseLeavesItsTrainsToTheRailway`
- `testASecondImportIntoTheConfigurationInUseKeepsAHandMadeChange`
- `testAnImportIntoTheConfigurationInUseIsRefused`
- `testASecondImportFromTheMenuKeepsAHandMadeChange`
- `core.testMassAssignLengths.testTheMaximumWalkOnHisRailway`

**Proves:** all passed.  **Refutes:** any skipped - MT-298 then goes back on Adam's list, and the report says so.
