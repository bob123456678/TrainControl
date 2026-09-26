# Release-readiness validation, round 2 - documentation and tests (TrainControl 3.0.0)

**Status:** open

**Prefix:** RLD2

**Reviewed:** branch `autonomy-diagram-r0`, the fixes `146b25eb..7e2fc558` and the records commit `7e2fc558`, on 2026-09-25.  HEAD moved to `98b300d6` (the BPV records) while I read; that commit changes only records - findings.tsv, triage.db, BPV.md and the two counts in behaviour.md and open-questions.md - and was read too.

## Method

Read-only.  Nothing was run, compiled or started; no JVM.  No git state was changed: only `git log`, `git show`, `git grep`, `git diff` between commits and `git --no-optional-locks status`.  Nothing under `cs2_sample_layout/` was read.  The only file written is this report.  Two mechanical checks were made in memory by an inline `python -c`, writing nothing: (1) the seven message keys this round added or changed, in all eight bundles - present, English's placeholders, no straight apostrophe, none empty; (2) every string literal in `src/` that is a key of the English bundle, against the four roads `testMessageBundles.testEveryKeyAScreenAsksForIsInEveryLanguageWithAValue` reads, using that test's own regular expressions.

What was read:

- My document `docs/reviews-2026-09-25/RLD.md`, and `RLA.md` and `RLU.md`, with every disposition; `docs/reviews/README.md`, `FANOUT.md`, and the supersession rule in `docs/manual-tests/README.md`.
- Every commit of `146b25eb..HEAD` in full with `git show`: the claims `06ecf625`, `bb197b16`, `66319d3c`, `01cf0d72`, `e4f61822`, `b1b653a0`, `ef4eeee9`, `26b18432`, `11bf5b82`, `27c668af`; the fixes `be3019da`, `ab3a37c2`, `e7a2f1fa`, `2fa033f3`, `693dd0a1`, `babde2bb`, `3af66907`, `0167541e`, `459444f7`, `b5226b46`; the records `7e2fc558` and `98b300d6`.
- RLD-B1's port compared line by line with master's `86b8b73a`; master's current v2.8.2 entry through `git show master:Readme.md`.
- Around the fixes, at HEAD: `MarklinControlStation.saveState`/`restoreState`, `TrainControlUI.saveState`/`restoreState` and the Backup door's live save, `Util.getBackupPath`; `AutonomyViewerPanel.importConfiguration`, `activateTheConfigurationNamed`, `importLegacyGraph`, `loadAfterImport`, `load`, `revert`; `AutonomySession.importLegacy`, `configurationToLoadAfterImport`, `excludeRepeatedSensorPages`; `AutonomyCompanionStore.createConfiguration`, `snapshotSetup`, `restoreSetup`, `getConfigurationNames`; `Layout.sanitizeMultiUnits`, `whyItReachesNoStation`, `whyTheStartIsRefused`, `explainCannotStart`; `MarklinRoute.conflictingAccessoryAndReason`; `MarklinControlStation.routesSavedArmed`/`importRoutes`; `TailCrossedPrompt.askAfterPlacement`, `noteADroppedAnswer`, `whereTheAnswerGoes` and the three doors that ask it; `TrainControlUI.openLayoutEditorWindow`, `isLayoutEditorOpen`, `wrappedToFit`; `AutonomyMenu`'s configuration submenu; `LayoutEditorRightclickMenu`; `I18n.t`.
- The tests changed in the range: `testTheImportDoorReadsAnOldFile` whole; the new claims and helpers of `testControlStationFaults`, `testTheRoutesImportDoorAsksByName`, `testALocomotiveDoesNotEvictItself`, `testWhyStuck`, `testWhereHisTrainsMayBeSent`, `testTheTailIsPickedOnTheDiagram` and `testThePlaceDoorsKeepTheHeading`; `testMessageBundles`' two key scans; and, for what they supersede or share, `testATrainDoesNotRunIntoItsOwnTail.testTheRefusalNamesTheLongestTrainThatGoes`, `testAutonomyDiagramSession.testAnImportLeavesOneHomePerLocomotive` and `testMassAssignLengths`' keyboard helper.
- Documents: behaviour.md's import, OB-183, BPV-A1, section 7a, answered-0, OB-299 and tail-question paragraphs; Automation.md's "why isn't it moving" section; Readme.md's 3.0.0 entry; open-questions.md's two count paragraphs; issues.md's Inbox, counted; tests.md's ledger, counted against every heading's Disposition, and the entries MT-298, MT-491, MT-496, MT-497, MT-501, MT-502, MT-505, MT-507, MT-508, MT-520, MT-571 and MT-582.
- `docs/manual-tests/findings.tsv` searched before each finding: the prefix RLD2 was free, and nothing below repeats a catalogued row.

Not reported, as the brief says: the 46-against-44 citation count, and RLD-C1.  Everything below is from reading.  Each finding that needs a run says so, with the fixture and what proves or refutes it.

Counts: no A, no B, 12 C, 6 D.  Nothing new above C.

---

### RLD2-C1 - behaviour.md and the summary line of `sanitizeMultiUnits` still say an edit to a train standing nowhere sweeps nothing; RLA-B1's fix sweeps the standing multi-units that drive it

| | |
|---|---|
| **Disposition** | Fixed in the records (cc56a7a8). |
| **Grade** | C - records |
| **Where** | `docs/reference/behaviour.md:1951-1958`; `Layout.java:9232-9233` |
| **Needs execution** | no |

`be3019da` changed the rule and rewrote the javadoc's body, but not its first sentence, and not behaviour.md:

- behaviour.md:1954-1956: *"their sweep for multi-unit conflicts asks only when the edited train stands on the graph: a train that stands nowhere clashes with nothing that stands."*  Since `be3019da` the sweep also asks, of every standing head that is linked to the edited train or holds it as a Central Station member (`Layout.java:9259-9272`), what putting that head down again would.  A member that stands nowhere now takes another train off the graph, which is exactly RLA-B1's fix.
- `Layout.java:9233`, the summary line: *"...has made conflict with it; nothing where it stands nowhere (BPV-A1)."*  The paragraph under it (9235-9237) says the opposite.

behaviour.md is where the intended rule lives, and its sentence now describes the defect RLA-B1 fixed.  My RLD-D6 (*"BPV-A1: the documents say what the code does"*) is stale for the same reason.  Fix: one clause in each - "...when the edited train stands on the graph, or is driven by a multi-unit that does".

### RLD2-C2 - Importing an old file into the running configuration by name now stands the file's trains on the running railway: RLA-C2's fix reversed the half RLA-C2 recorded as Adam's OB-183 rule, without a ruling

| | |
|---|---|
| **Disposition** | Fixed as RLA2-B1. |
| **Grade** | C - needs Adam's ruling |
| **Where** | `AutonomyViewerPanel.java:1238-1260` and `:1375`; `behaviour.md:1965-1985` and `:2435-2437`; `AutonomyMenu.java:332-333`; `testTheImportDoorReadsAnOldFile.java:613-666` |
| **Needs execution** | no - the claim asserts it |

RLA-C2 said, of the reload after an import into the loaded configuration: placements taken back by its capture are *"Adam's rule - 'where a train IS is a fact' (OB-183) - but for homes and maxima it is an import silently undone."*  The fix (`2fa033f3`) captures the running layout first and then reloads without capturing, and its claim was *"reworked to a placement"*: `testAnImportIntoTheConfigurationInUseKeepsWhatItBrought` asserts that the trains the import placed are standing after the reload.  Maxima still cannot come in - the capture writes a maximum for every square the railway holds and the gap-fill keeps it, as the commit says.  So the fix delivers the half RLA-C2 called ruled, and not one of the two halves it called wrong (homes do come in now).

What the operator meets: Autonomy > Configuration > Import... of his 2.7.4c file, typing the running configuration's name, Yes to *"Add to it what the file has and {0} does not?"* - and every locomotive the file places that is in the database and not already standing anywhere appears on the file's square, on the railway autonomy is about to run.  Where those locomotives physically are, nothing knows; Start dispatches them from the file's squares.

The records now disagree with the door:

- behaviour.md's OB-183 section (1968-1985): *"where the two disagree about a train the railway wins"*, with the exceptions named door by door - the editor's Place, taking one off, clearing every placement.  The import into the running configuration is a fourth, unnamed.
- `AutonomyMenu.java:332-333`: *"Import is never greyed. It does not act on the configuration that is running"*.  By name, it now captures into it, writes into it and reloads it.

**Mitigations:** reached only by typing the running configuration's name (the prompt offers the file's); a train already standing is not placed again (RLA-B2's `standingAlready`); the message counts what was placed; importing into a new configuration and choosing it stands the same trains the same way.  So C - but the choice is Adam's: the file's placements onto the running railway, as now, or placements left to the railway with homes kept, which is RLA-C2's own reading.  Either way behaviour.md should name the door, and the AutonomyMenu comment should say it.

### RLD2-C3 - RLA-C2's claim cannot see the capture-first half: its railway agrees with its configuration, so removing the capture passes - and regressed, a moved train is put back where it set off

| | |
|---|---|
| **Disposition** | Fixed as RLA2-B1 - the capture it asked to pin is gone. |
| **Grade** | C - a test that would not catch its own regression |
| **Where** | `AutonomyViewerPanel.java:1251`, `:1375`; `testTheImportDoorReadsAnOldFile.java:613-666` |
| **Needs execution** | yes |

The fix's own comment names the hazard: *"Captured first, where a train stands is still the running layout's fact (OB-183)"*.  Without that capture, and with `captured` still true so the reload does not capture either, the reload builds from the configuration's pre-run placements: a train that a run moved is put back on the square it set off from, in the model and then on disk - the case behaviour.md:1976-1978 says can route the next dispatch into a block that is physically occupied.

`testAnImportIntoTheConfigurationInUseKeepsWhatItBrought` opens the frozen railway and imports at once.  The running layout was just built from the configuration, so capturing it first changes nothing, and the claim reads the same with the capture line deleted.  R9 (reload with the capture) is red, as the disposition says; that mutation is the other half.

**Verification request.**
- **Mutation:** delete `session().captureFromLayout(ui.getModel().getAutoLayout().toJSON(), into);` at `:1251`, leaving `captured = true;`.  Run the claim as it stands.  **Proves the gap:** green.  **Refutes:** red.
- **The claim strengthened:** before the import, move one standing train on the running railway to an empty station, as a run leaves it (`moveLocomotive(train, station, false)` on the event thread, not written to the configuration); import MT-491 by the running configuration's name; assert that train stands where it was moved, in the configuration and on the railway.  **Proves it a claim:** red under the mutation, green without it.

### RLD2-C4 - RLU-C2's claim reads the model's list, not the question the door showed

| | |
|---|---|
| **Disposition** | Fixed as RLU2-C7. |
| **Grade** | C - a test that would not catch its own regression |
| **Where** | `testTheRoutesImportDoorAsksByName.java:154-155` and `:356`; `TrainControlUI.java:25128` |
| **Needs execution** | yes |

`testASavedArmedRouteWithNoSensorIsNotCountedAsArmed` asserts `assertFalse(run.savedArmed.contains(noSensor[0]), "the question names ...: " + run.question)`.  `run.savedArmed` is `model.routesSavedArmed(json)`, called by the test (`:356`) - the method under test, not what the door put on screen; `run.question` appears only in the failure message.  The claim that does compare the question, `testAnsweredNoEveryRouteArrivesOff`, builds its expected sentence from the same `run.savedArmed`, over a file with no sensorless route armed.  So R13 (the s88 filter taken out of `routesSavedArmed`) is caught, but a door that builds its own list from the file's `auto` flags names the sensorless route in the question, and both claims stay green.

**Fix:** assert on the question itself - that `run.question` does not name the sensorless route (checking first that its name is not part of another route's).

**Verification request.** In the door, replace `this.model.routesSavedArmed(json)` with the names of every route whose `auto` is true; run the class.  **Proves:** green.  **Refutes:** red.

### RLD2-C5 - The every-key check still sees none of about 340 keys asked through a ternary, a helper or the log, and `2fa033f3` took the import's replace question out of its sight

| | |
|---|---|
| **Disposition** | Fixed as RLU2-C3. |
| **Grade** | C - a test that would not catch its own regression |
| **Where** | `testMessageBundles.java:906-907`, `:919-925`; `AutonomyViewerPanel.java:1063-1064`; `TrainControlUI.java:19920` |
| **Needs execution** | yes |

RLU-C8's fix added a fourth road - a key held in a `static final String` - and the javadoc keeps its mutation claim: *"give any asked-for key an empty value in one language, and this fails naming it"*.  The four roads see a key only as the first literal argument of `I18n.t`/`I18n.f`, a generated `bundle.getString`, a form entry, or a constant.  Scanned in memory with the test's own expressions, 343 keys of the English bundle appear as string literals in `src/` and are seen by none of them.  Among them, screens and this round's own keys:

- `autosetup.ui.confirmImportFillsGaps` and `autosetup.ui.confirmImportOverwrites`, chosen by a ternary inside `I18n.f(...)` (`AutonomyViewerPanel.java:1063-1064`).  The second was seen before `2fa033f3`, where it was the literal first argument; the fix moved it out of sight.
- `layout.ui.confirmRouteActiveRoute` and `layout.ui.confirmRouteProtectingSignal` - the route-over-a-train question MT-507 and MT-508 are about - chosen by a ternary (`TrainControlUI.java:19920`).
- `autolayout.ui.confirmClearAllMaxTrainLengths` and `...TrackLengths` (through `bulkClearWarning`), `route.ui.valueTrue`/`valueFalse`, `autosetup.ui.stepInitialize`.
- Log lines through `logf`: RLD-B1's `log.databaseSaveFailed` and `ui.errorSavingUiState`, and RLU-C9's new `autolayout.ui.logTailAnswerDroppedEditorOpened`.

A missing key still throws (`I18n.t` is `bundle.getString`), and `testTranslationsMatchEnglishKeySet` still holds the key sets equal; only the empty-value half is blind, as RLU-C8 said of the constants.  Nothing is empty today (RLU-D3; and checked again for the seven keys this round touched).

**Fix,** simpler than a fifth road: every key of the English bundle is non-empty in every bundle, except the `...Suffix` keys - no need to discover who asks for it.

**Verification request.** Set Danish `autosetup.ui.confirmImportFillsGaps=` to empty and run `testEveryKeyAScreenAsksForIsInEveryLanguageWithAValue`.  **Proves:** green.  **Refutes:** red, naming it.

### RLD2-C6 - Three fixes have a claim for one branch of several: RLA-B1's Central Station multi-unit, RLA-B2's homes, RLA-C5's barred and not-a-station copies

| | |
|---|---|
| **Disposition** | Fixed - pins for the Central Station multi-unit, the homes and the no-station copy (5322fb65, 1d9c8c66); mutations S6 red, S4 red, S7 red. |
| **Grade** | C - a test that would not catch its own regression |
| **Where** | `Layout.java:9267`, `:5059`; `AutonomySession.java:974`, `:534`, `:1138` |
| **Needs execution** | yes |

- **RLA-B1.**  Heads are found by `head.isLinkedTo(l) || head.getModelMultiUnitLocomotives().contains(l)`.  `testReAddressingAMemberOntoAStandingTrainTakesThatTrainOff` links the member in TrainControl; no claim stands a Central Station multi-unit.  Mutation: drop the second disjunct.
- **RLA-B2's homes.**  `homedAlready` is seeded from the configuration imported into, so a train that already has a home gets no second one.  No claim reaches it: `testASecondImportDoesNotStandATrainTwice` moves a train, not a home, and `testAnImportLeavesOneHomePerLocomotive` imports into an empty configuration.  Mutation: delete the seeding at `:974`.  Also, the disposition and the fix's message say homes already had are *"named in the message"*; only trains are (`alreadyPlaced`) - `duplicateHomes` is counted and shown nowhere.  behaviour.md:2433-2434 says it right.
- **RLA-C5.**  Turning round is offered only onto a copy that is not barred, is a station, and is switched on.  `testTurningRoundIsOfferedOnlyOntoACopyATrainMayStartFrom` switches the other copy off; the barred copy - the one RLA-C5 named first - and a copy that is no station have no claim.  Mutations: drop `!isABarredCopyOfAStation(other)`, or `other.isDestination()`.

Each is a guard a later edit could drop with every test green.  **Verification request:** run each of the four mutations against its claim's class (`testALocomotiveDoesNotEvictItself`, `testTheImportDoorReadsAnOldFile`, `testWhyStuck`).  **Proves:** green.  **Refutes:** red.

### RLD2-C7 - MT-298's superseding comment, and the test's own javadoc, still say the second import answers Yes to replacing the configuration

| | |
|---|---|
| **Disposition** | Fixed - MT-298 has a comment, and the test's javadocs say what the door asks (5322fb65). |
| **Grade** | C - records |
| **Where** | `docs/manual-tests/tests.md:16806`; `testTheImportDoorReadsAnOldFile.java:208`, `:731-732` |
| **Needs execution** | no |

MT-298's comment of 2026-09-25: *"imports the same file again from the menu into the same configuration (answering Yes to replacing it)"*.  Since `2fa033f3` the door asks *"Add to it what the file has and {0} does not?"*, and the same test asserts the replace question is not asked (`:288`).  The javadoc above the method (`:208`: *"answered Yes when the door asks whether to replace the configuration of that name"*) and the answerer's (`:731-732`: *"Yes to replacing a configuration of that name"*) say the same.  It is RLA-B2's contradiction, left in the record Adam reviews the supersession by.  **Fix:** a dated Comment on MT-298 (entries are append-only), and the two javadocs.

### RLD2-C8 - RLD-C6 is half done: a superseding test that skips itself reads as an answer, and the disposition does not say how the report will tell

| | |
|---|---|
| **Disposition** | Fixed - in the battery of 98b300d6 (battery-0925g) both methods ran and passed; nothing skipped. |
| **Grade** | C |
| **Where** | `testMassAssignLengths.java:1675` (reached from `testTheMaximumWalkOnHisRailway`, `:978`); `testTheImportDoorReadsAnOldFile.java:269`; `docs/reviews/FANOUT.md:92-93` |
| **Needs execution** | yes - the final battery's per-method results |

RLD-C6's verification request asked for the battery after each superseding commit, each class *"green, not skipped"*, and named MT-520's `testMassAssignLengths.testTheMaximumWalkOnHisRailway`, which skips where the desktop does not give the prompt the keyboard.  The disposition answers only *"The final battery's result is recorded in the report"*, and FANOUT gives the report's battery *"in one sentence ... with the counts"*.  A second superseding method skips itself too: `testASecondImportFromTheMenuKeepsAHandMadeChange` (MT-298), where the first import gave no named station a maximum.  A skipped method answers nothing, and the README's rule is *"green in a full battery"*.

**Verification request.** From the final battery, the per-method status of `testTheMaximumWalkOnHisRailway` and `testASecondImportFromTheMenuKeepsAHandMadeChange`.  **Proves the disposition complete:** both passed.  **Refutes:** either skipped - then MT-520 or MT-298 goes back on his list, as MT-505 did, and the report names it.

### RLD2-C9 - The Readme's new route sentence states as release behaviour the stop-route rule MT-507 and MT-508 have just asked Adam to confirm, and "never refused" reads as "runs in full"

| | |
|---|---|
| **Disposition** | Fixed - the line states today's rule plainly: such a route runs without asking, its stop is always sent, and a switch under a train is still left alone (cc56a7a8); MT-507 and MT-508 put the rule to Adam. |
| **Grade** | C - records |
| **Where** | `Readme.md:400`; `MarklinRoute.java:402-416`; tests.md MT-507, MT-508 |
| **Needs execution** | no |

`0167541e` (RLD-C2's fix, 15:02) added: *"A route with an emergency stop in it is never refused, and its stop is always sent."*  Fifty minutes later `7e2fc558` put MT-507 and MT-508 back on Adam's list asking him to *"say which you want for a route with a stop in it: never asked, as now, or asked like any other"* - so the release notes publish one answer to an open question.

And the wording: such a route is never asked about, but the switch under a train is still skipped at every door - `conflictingAccessoryAndReason` returns null for it, and *"The conflicting accessory is still skipped and still logged by the per-command check below"*.  Beside a sentence about being asked whether to go ahead, "never refused" reads as "runs everything".  **Fix, after his answer:** for example *"A route with an emergency stop in it runs without asking: its stop is always sent, and a switch under a train is still left alone"* - or drop the sentence, keeping the changelog non-technical.

### RLD2-C10 - The user guide's new remedy (RLD-C9's fix) puts "a train may start from" on the station reached, not on the platform's other way

| | |
|---|---|
| **Disposition** | Fixed (cc56a7a8). |
| **Grade** | C - records |
| **Where** | `Automation.md:311`; `Layout.java:5059` |
| **Needs execution** | no |

*"where the platform's other way leads to a station a train may start from, turn the train round"*.  The code (`3af66907`) asks it of the platform's other copy - not barred, a station, switched on - and that copy must reach a station autonomy may choose.  As written, a reader checks the destination.  behaviour.md:705 has it right (*"a train may be started from that copy"*).  **Fix:** "where the platform's other way is one a train may start from and leads to a station autonomy may choose, turn the train round".

### RLD2-C11 - open-questions.md says its listed additions "make" 4,514 finding rows; they make 4,433

| | |
|---|---|
| **Disposition** | Fixed (cc56a7a8). |
| **Grade** | C - records |
| **Where** | `docs/reference/open-questions.md:418-419` |
| **Needs execution** | no |

3,726 + 336 + 13 + 258 + 52 + 48 = 4,433, the total before this review.  `7e2fc558` moved the figure to 4,484 without naming round 1's 51 rows (RLA, RLU and RLD), and `98b300d6` to 4,514 without the 30 BPV rows.  behaviour.md:2476 and the store agree with 4,514; only the sentence's arithmetic is off - the shape the paragraph above it records (*"wrong in the same way, by being written rather than recounted"*).  `testTheRecordsCountTheStore` compares the total with the store, not the sum.  **Fix:** "...and the 3.0.0 release review added 51 in its first round (RLA, RLU and RLD) and the 2.8.2 backport validation 30 (BPV), which makes 4,514".

### RLD2-C12 - RLU-B2's sibling: the layout editor's right-click still parents its messages on the popup, under an editor that is always on top whenever the main window is

| | |
|---|---|
| **Disposition** | Fixed as RLU2-C6. |
| **Grade** | C - rare path |
| **Where** | `LayoutEditorRightclickMenu.java:60, 179, 197, 219, 235, 257, 314, 341, 364, 389`; `LayoutEditor.java:5644` |
| **Needs execution** | yes |

`693dd0a1` moved the autonomy right-click's messages to the main window: a message parented on a popup whose item is running belongs to Swing's hidden frame, and with Window Always on Top (the default) it opens beneath the topmost window while holding every window.  The layout editor takes its parent's always-on-top (`LayoutEditor.java:5644`), and its right-click menu's ten `catch (Exception e)` sites still call `JOptionPane.showMessageDialog(this, e.getMessage())`.  They are exception paths - paste, undo, redo, edit address and the like - not everyday refusals (the paste's own refusal is parented on the editor, `LayoutEditor.java:2970`), so this is rare; but when one fires the editor looks frozen.  **Fix:** the editor (`edit`) as the owner.

**Verification request.** `e4f61822`'s claim transposed: the layout editor open over the sandbox, Window Always on Top ticked, an item whose action throws (Undo, with the editor's `undo()` made to throw by a mutation).  Read the dialog's `getOwner()`.  **Proves:** not the editor.  **Refutes:** the editor.

---

### RLD2-D1 - RLD-B1: the port is master's, and its claims assert the file

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |

`e7a2f1fa` is `86b8b73a` applied to 3.0's two saves, line for line but for the comments' provenance.  The mark is cleared only after `Files.copy` returns; a failed copy is logged and the database save returns, and the UI state is left unwritten while the rest of that save (the session capture, the titles) goes on.  The Backup door's live save inherits it, and its archive then holds the unreadable files as they are, which is right.  `Util.getBackupPath` falling back to the working folder opens no gap: a copy that lands there has succeeded.  Both claims assert the file's own bytes, then unblock the copy and assert one copy was kept and the save went ahead - a fixture that merely broke the save could not pass the second half.  They skip rather than pass outside one.sh/battery.sh, since they write the run's copy of the data; in NetBeans they report skipped.  The 3.0.0 changelog line (`Readme.md:449`) stays true.

### RLD2-D2 - RLD-C3 and RLD-C4 are fixed as their dispositions say, at the doors they name

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |

- **RLD-C3.**  `configurationToLoadAfterImport(running, into)` is chosen before `save()` (`AutonomyViewerPanel.java:1271-1277`), so a declined reload (`prepareAutonomyReload` false) or a refused rebuild (`revert(previous)`, where `previous` is now the running configuration `loadAfterImport` just chose) leaves setup.json naming the running one.  behaviour.md:2423-2429 and `activateTheConfigurationNamed`'s javadoc say what the door does.  `testADeclinedReloadLeavesHisConfigurationChosen` reads setup.json from disk, and its precondition - the reload question was asked - proves the save ran, since the question comes after it.  Asserting that the disk also holds "MT-491 declined" would make that explicit.
- **RLD-C4, with RLA-B2's trains.**  The file's kind is read before the question; an old file is asked `confirmImportFillsGaps` and a bundle `confirmImportOverwrites`.  A train the configuration already has standing is reported through `infoLegacyAlreadyPlaced` and not placed.  Both asserted at the door, on his own files.
- **RLA-C3, which RLD-C3's rollback leans on.**  The snapshot is restored only while nothing is saved, and `createConfiguration`, `setActiveConfiguration` and `excludeRepeatedSensorPages` are in memory, so behaviour.md's *"nothing made, chosen or saved"* holds.  One cosmetic edge: a pages-excluded message shown before a failure is undone by the rollback without a word; it needs a repeated-sensor page and an unreadable file together.

### RLD2-D3 - RLD-C5, RLD-C7 and RLD-C6's tracker half: the claim and the ledger say what the dispositions say

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |

- **RLD-C5.**  The claim ticks Load Autonomy before the import - its default is ticked (`TrainControlUI.java:1250`), so a run killed mid-test leaves the default - asserts it is still ticked, and asserts the log holds no line with the Load Autonomy label, which is what `ee407002`'s untick logged.  Put back in `finally`.
- **RLD-C7.**  MT-491, MT-501, MT-502 and MT-582 now name `testAnOldFileFromTheMenuGoesIntoANewConfiguration`; MT-571's return names `testTheRefusalNamesTheLongestTrainThatGoes`, which exists and holds the 20, the 9 and the 10.
- **RLD-C6.**  MT-505, MT-507, MT-508 and MT-571 read fixed unvalidated, each with a comment saying why.  Counted from tests.md: 586 headings - 453 fixed validated, 121 superseded, 10 fixed unvalidated, 2 needs test; the ledger lists the 12, and its footer reads 574 of 586, 453 and 121.  The Inbox holds 55 OB and 32 FR headings, the 87 open-questions.md gives.

### RLD2-D4 - The bundle and record fixes of RLD-C2 (its sensor half), RLD-C8, RLD-C10 and RLD-C11

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |

- The seven keys this round added or changed - the four import messages, the tail-drop log line, and OB-299's two sentences - are in all eight bundles with English's placeholders, no straight apostrophe, none empty.  Danish, Spanish, Italian and Dutch now use their neighbours' word for autonomy, and Spanish writes "dele" (RLD-C8, RLU-C5).
- RLD-C10: `ratioOf`'s javadoc and behaviour.md's answered-0 paragraph now say the divisor's floor ties a route of 0 with a route of 1, which is what `Math.max(1, lengthOf(path))` does.
- RLD-C2's first half: *"a route fired by a sensor skips only the switches and signals a train is on or standing at"* matches section 7a and `MarklinRoute`.  The stop sentence is RLD2-C9.
- RLD-C11: `wrappedToFit` breaks at spaces to 90 characters; the claim asserts no line over 100 with a list of 1,058 characters, and was red with the names on one line.

### RLD2-D5 - RLU-C9's fix reaches all three doors, and RLA-C4's minimised editor is a state no door reaches

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |

- **RLU-C9.**  The question records whether an editor was open when it was asked and whether one is when it is answered; the paste door, the right-click Place and the editor's Place all AND it into `setupStands`, so `whereTheAnswerGoes` returns null, nothing is written to the setup or the running railway, and each logs the new line.  An editor whose closing replaces the session was already dropped by `sameSetup` (TDU4-C2).  behaviour.md:1151 matches.  The claim asserts the road and the log line, which appears only on this path.
- **RLA-C4.**  With any editor open the main window's doors refuse - tile menu, keys, right-click (RLU-D1) - and the editor's own Place asks at once, modally, while the editor shows; so the question is never asked with an editor already minimised.  The claim calls `askAfterPlacement` directly for that reason.  The fix is a harmless guard, and behaviour.md:1142-1143 (*"the list opens from the editor"*) stays true for every reachable case.  `openLayoutEditorWindow` and `isLayoutEditorOpen` now differ for a minimised editor, as intended; RLU-D1's *"the two cannot disagree"* is history.

### RLD2-D6 - build.xml, the window census and the live-snapshot README need nothing this round, and Routes > Import was the only file read without a character set

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |

- No test class was added or removed in `146b25eb..HEAD` - `git diff --diff-filter=AD` lists only the four review documents - so build.xml's registrations stand.  The class that gained sandboxes this round, `testTheImportDoorReadsAnOldFile`, was already registered (`build.xml:541`) and in the live-snapshot README's "Used by".  `testControlStationFaults`' new window is allocated without its constructor and opens nothing.
- RLU-A1: after `ab3a37c2` no `new String(readAllBytes(...))` without a character set, `FileReader`, charset-less `InputStreamReader`, `FileWriter` or bare `getBytes()` on file content remains in `src/` (the one `getBytes()` left hashes the folder path).  The claim skips rather than passes on a JVM whose default is UTF-8.
