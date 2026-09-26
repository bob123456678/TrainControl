# Release-readiness validation, round 3 - documentation and tests (TrainControl 3.0.0)

**Status:** open

**Prefix:** RLD3

**Reviewed:** branch `autonomy-diagram-r0` at `d779578b`, round 2's commits `98b300d6..HEAD` (claims `5322fb65` and `1d9c8c66`, fixes `cc56a7a8`, records `d779578b`), on 2026-09-25.

## Method

Read-only.  Nothing was compiled, run or started; no JVM of any kind.  No git state was changed: only `git log`, `git show`, `git grep`, `git blame`, `git ls-tree` and `git diff --name-status` between commits.  Nothing under `cs2_sample_layout/` was read or written.  The only file written is this report (and its folder).  Six mechanical checks were made in memory by inline `python -` heredocs that wrote nothing: (1) the eight bundles at HEAD through `git show` - key sets, duplicates, placeholder sets against English, bytes above 127, empty values, straight apostrophes in values with a placeholder, and the three keys this round added or changed printed in every language; (2) the orphaned-javadoc count, re-implemented from `testJavadocsAreAttached.orphansIn`, over every `src/` file at `98b300d6` and at HEAD; (3) the live-snapshot README's "Used by" list against every test class that names `"live-snapshot"`; (4) `docs/manual-tests/triage.db` opened read-only (`mode=ro` URI) - row and distinct-ref counts, the RLD and RLD2 rows, and every round-2 document's dispositions and statuses against its rows; (5) tests.md's headings and dispositions, and issues.md's Inbox, counted; (6) every MT entry superseded on 2026-09-25, with the test its comment names.

What was read:

- The brief; `docs/reviews/README.md`; my lane's `docs/reviews-2026-09-25/RLD2.md`, and `RLA2.md` and `RLU2.md` whole, with every disposition; RLU.md's RLU-B2; the supersession rule in `docs/manual-tests/README.md` (:57-63).
- The four commits in full with `git show`.
- Around the fixes, at HEAD: `AutonomyViewerPanel` - `importConfiguration`, `activateTheConfigurationNamed`, `importLegacyGraph`, `loadAfterImport`, `load`, `setSelectedConfiguration`, and every `JOptionPane`/`AutonomyReport.show` owner in the file; `AutonomyMenu`'s Configuration submenu; `AutonomySession` - `importLegacy`'s seed, placement, home and facing code, `findTheImportedFacings`, `LegacyImport`'s fields, `configurationToLoadAfterImport`; `AutonomyCompanionStore.getConfiguration`; `MarklinControlStation.syncWithCS2` (address branch, multi-unit branch, the new sweep), `isAutonomyRunning`, `getAutoLayout`/`clearAutoLayout`, and `TrainControlUI.syncWithCS2`'s wrapper; `MarklinLocomotive`'s multi-unit accessors and the comment `cc56a7a8` changed; `CS2File`'s multi-unit parse; `Layout.sanitizeMultiUnits`, `clearMultiUnitConflictsWith`, `whyItReachesNoStation`, `whyTheStartIsRefused`, `whyNoTrainIsStartedFrom`; `MarklinRoute.conflictingAccessoryAndReason` and the mid-route question; every dialog owner in `LayoutEditorRightclickMenu` and `LayoutRightclickAutonomyMenu`; the `setupStands` line of the three tail doors.
- Tests: `testTheImportDoorReadsAnOldFile` (the MT-298, RLA-B2, refusal, facing and homes claims, the helpers and the answerer); `testMockCentralStation`'s set-up and sync claim; `testALocomotiveDoesNotEvictItself`'s Central Station pin; `testWhyStuck`'s changes; `testTheRoutesImportDoorAsksByName`'s claim and `words`; `testMessageBundles`' new test, its helpers and the every-key javadoc; `testJavadocsAreAttached`; the new pins in `testWhereHisTrainsMayBeSent` and `testTheTailIsPickedOnTheDiagram`; `testThePlaceDoorsKeepTheHeading`'s javadoc; `testAutonomyDiagramSession.testAnImportLeavesOneHomePerLocomotive`; `testNothingOnTheEventThreadTakesTheRailwaysMonitor`'s allowances; the served `test/lokomotive.cs2`'s multi-unit.
- Documents: behaviour.md's tail-list, answered-0, locomotive-edit and sync, OB-183, import-facing and import sections and its count; Automation.md's "facing a way that leads to no station"; Readme.md's 3.0.0 entry (:370-460); open-questions.md's two count paragraphs; issues.md's OB-301, OB-302 and Inbox; tests.md's ledger, MT-298 and MT-491.
- `docs/manual-tests/findings.tsv` searched before each finding (the prefix RLD3 is free; sync and multi-unit, MT-298, AutonomyReport, duplicateHomes, loadAfterImport, standingIn, the ratchet): nothing below repeats a catalogued row beyond the ids it names.

Not reported, as the brief says: RLU2-C9 and RLD-C1.  Everything below is from reading; each finding that needs a run says so, with the fixture and what proves or refutes it.

Counts: no A, no B, 6 C, 10 D.  **Nothing new above C** - by the brief's measure, the signal to stop.  RLD2's twelve C findings are fixed as their dispositions say except RLD2-C2, whose disposition is partial (RLD3-C1); the other five Cs are gaps in round 2's claims, records and comments.

---

### RLD3-C1 - RLD2-C2 was closed without the ruling it asked for: the refusal takes MT-298's second import away from the configuration being run, and MT-298's superseding test reaches that import only through a choice the menu cannot make

| | |
|---|---|
| **Disposition** | Fixed - a bundle is refused under the name of the configuration in use; an old file is imported into it again, placing none of its trains and naming them, with the running layout captured first; MT-298 is claimed as its steps run.  claims cca48e96 (red first), fix 81a9fffa; mutations U2 red, U3 red, U4 red. |
| **Grade** | C - needs Adam's ruling; a superseded MT its test no longer answers.  Nothing is lost: the refusal changes nothing. |
| **Names** | RLD2-C2 ("Fixed as RLA2-B1"), RLD2-C7, MT-298 |
| **Where** | `AutonomyViewerPanel.java:1012-1026` (the refusal); `AutonomyMenu.java:312-313` (choosing a configuration loads it); `testTheImportDoorReadsAnOldFile.java:245-281`; `tests.md:16773-16811`; `docs/manual-tests/README.md:57-63`; `messages.properties:1525` |
| **Needs execution** | yes |

RLD2-C2 left Adam a choice: the old file's placements onto the running railway, as round 1 had it, or placements left to the railway with homes kept.  `cc56a7a8` chose neither - Import refuses the name of the configuration in use.  That is the safe answer to RLA2-B1's backup restore, but nothing in the records has Adam choosing it (behaviour.md:2433 cites only RLA2-B1 and RLA2-B3; no quote, no open question), and it has a cost the records do not name: MT-298's case.

MT-298's steps are: import the file; change one setting by hand; import the same file again.  A setting of the imported configuration can be changed by hand only once it is chosen, and Autonomy > Configuration > (name) chooses by loading it (`setSelectedConfiguration(name)` then `load(name, true)`), which makes it the configuration in use.  Step 3, under the name the prompt suggests, is then refused: *"{0} is the configuration in use, so nothing was imported.  Import the file under another name, and choose it under {1} when you want to run it."*  The remedy it offers makes a new configuration from the file without the hand change - the opposite of MT-298's Expected.  The way that works - choose another configuration first, then import into this one by name - is not said.  (Only with nothing running, as after a start with Load Autonomy unticked, is a configuration chosen without being in use.)

`testASecondImportFromTheMenuKeepsAHandMadeChange` cannot see this.  Its comment says the configuration is *"chosen as the operator chooses it"*, but it moves the store's pointer and rebuilds (:250-251), makes the change, and moves the pointer back (:279-280): the window's running configuration never changes, so its second import is never into the configuration in use.  Before `cc56a7a8` that difference decided nothing - an import into the running configuration by name went ahead; since, it decides the outcome.  The supersession rule asks for a test that *"drives the same door the steps do ... not a helper beneath it"*, and for 100% confidence; MT-298's comment still says *"Nothing to run by hand"*.

**Direction.** Put the refusal to Adam as the ruling RLD2-C2 asked for.  If it stands: the message names the second remedy; behaviour.md:2440 says "into a configuration that exists and is not in use"; and either MT-298 goes back to fixed unvalidated with a dated comment (the entry is append-only) saying step 3 needs his own configuration chosen first, or the claim chooses through `load` and chooses his configuration back through `load` before the second import, as he would have to, and its comment says so.

**Verification request.** In the MT-298 claim, replace the store-only choice at :248-252 with what the menu item runs - `setSelectedConfiguration("MT-298 import")` and `load("MT-298 import", true)` on the event thread - make the same change, and import again by that name without choosing the original back.  **Proves:** `said` holds `autosetup.ui.errorImportIntoConfigurationInUse` and not `confirmImportFillsGaps`, and the configuration is unchanged.  **Refutes:** the fill-gaps question is asked and the import runs.

### RLD3-C2 - RLA2-B1's claim imports an old file; the case RLA2-B1 is about - a bundle, the backup of the configuration running - has no claim

| | |
|---|---|
| **Disposition** | Fixed - the refusal's claim imports a bundle (cca48e96); mutation U1 red. |
| **Grade** | C - a test that would not catch its own regression |
| **Names** | RLA2-B1 ("claims 5322fb65 (red first) and 1d9c8c66 ... mutation S1 red") |
| **Where** | `AutonomyViewerPanel.java:1018-1026`, `:1093` (old-file branch), `:1097-1117` (bundle branch); `testTheImportDoorReadsAnOldFile.java:615-690` |
| **Needs execution** | yes |

RLA2-B1 was the bundle branch: Export suggests `<configuration>.json`, Import suggests the file's name, so restoring a backup of the configuration running lands on its name, and the reload's capture undid the restore.  The refusal sits before the file is read, so today it covers both kinds.  But the one claim, `testAnImportIntoTheConfigurationInUseIsRefused`, imports the MT-491 old file.  Move the check into `importLegacyGraph` - where the round-1 capture lived, and where the comment's second half (an old file's trains on the running railway) points - and every test stays green while a restored backup is again silently undone.  S1 removes the refusal whole and cannot tell the two apart.

**Direction.** The same claim with a bundle: `session.getStore().exportBundle(inUse)` (what Export writes) to a temp file, imported from the menu under that name; assert the refusal, and the configuration and `setup.json` unchanged.

**Verification request.** Mutation: move the check at :1018-1026 to the top of `importLegacyGraph`.  Run `testTheImportDoorReadsAnOldFile`.  **Proves:** green.  **Refutes:** red.

### RLD3-C3 - RLA2-B2's member half has no claim, and a Central Station multi-unit's members changed during a run are never swept afterwards

| | |
|---|---|
| **Disposition** | Fixed in the records (81a9fffa); the member half's claim is filed as OB-303. |
| **Grade** | C - a test that would not catch its own regression; and a narrow residue of RLA2-B2 (graded B): the same consequence, reached only when the Central Station multi-unit is edited during a run |
| **Names** | RLA2-B2 ("the sync sweeps every locomotive it re-addresses or gives other Central Station members"); behaviour.md's record of it (RLD2-C1's paragraph) |
| **Where** | `MarklinControlStation.java:1609-1616` (the address deferral), `:1686-1695`, `:1708-1722`; `behaviour.md:1959-1960`; `testMockCentralStation.java:434` |
| **Needs execution** | yes |

**The claim.**  `testASyncThatGivesAMemberAStandingTrainsAddressTakesThatTrainOff` drives the address half only.  Delete `if (membersChanged) sweptAfterTheSync.add(held);` (:1694) and every test stays green, while a standing train the Central Station adds to a standing multi-unit stands beside it again - the half RLA2-B2 called "worse placed".  The served fixture already has a multi-unit to build the claim on (`test/lokomotive.cs2`: OBB 1043, uid 0x2c01, with 1043 006-4 ÖBB and 1043 001-5 ÖBB).

**The running case.**  The address branch defers while autonomy runs (`loc.addressUpdateDeferredWhileRunning`), so the change is applied, and swept, at the first sync after the run.  The member branch has no deferral: it sets the Central Station's list on every sync, running or not, and the sweep is skipped while running.  The next sync compares the list with itself, finds nothing changed and sweeps nothing - so a member added during a run (staging counts) stays standing beside its multi-unit after the run, until a setup edit, a load or a Place of the multi-unit sweeps it.  The comment (:1713) says *"not while autonomy runs, as they are not"*; but the window's doors refuse the edit while running, and this applies it and drops the sweep.  behaviour.md:1959-1960 (*"when autonomy is not running"*), read beside the deferred address, reads as a deferral too.

Consequence, if reached: RLA2-B2's - autonomy runs the member as a train of its own while the multi-unit's commands move it.  Reach: a Central Station multi-unit edited on the station's own screen during a run, a sync before the run ends, and a standing train among the new members.

**Direction.** Keep a multi-unit whose members changed while running and sweep it at the first sync after, as the address is; a claim for each half; and behaviour.md says members are swept after the run.

**Verification request.** (1) Mutation as above; run `testMockCentralStation` and `testALocomotiveDoesNotEvictItself`.  **Proves:** green.  **Refutes:** red.  (2) The mock station; the database's OBB 1043 (a multi-unit) holding 1043 006-4 ÖBB only; OBB 1043 standing on one station of `model.getAutoLayout()` and 1043 001-5 ÖBB on another; `layout.setStagingInProgress(true)`; `model.syncWithCS2()`; `setStagingInProgress(false)`; `model.syncWithCS2()` again.  **Proves:** 1043 001-5 ÖBB still stands.  **Refutes:** it is taken off.

### RLD3-C4 - RLU2-C5's pin reads `JOptionPane` on the two right-click menus only: the setup-tidy reports, and every message of the panel that is never shown, can go back to an owner with no window

| | |
|---|---|
| **Disposition** | Fixed in part - the viewer panel's report is pinned (cca48e96); the right-click menu's two report calls are on the main window already and read by no pin. |
| **Grade** | C - a test that would not catch its own regression |
| **Names** | RLU2-C5 ("a pin reads every message on both right-click menus"); RLU-B2, IND9X-C4 |
| **Where** | `testWhereHisTrainsMayBeSent.java:202`; `LayoutRightclickAutonomyMenu.java:1358`, `:1599`; `AutonomyViewerPanel.java:1544` and its other dialog calls |
| **Needs execution** | yes |

`testNoMessageOnARightClickMenuBelongsToTheMenu` finds `JOptionPane.show` in `LayoutRightclickAutonomyMenu` and `LayoutEditorRightclickMenu` and fails on an owner of `this`.  It does not read `AutonomyReport.show(ui, session.save())`, which the right-click menu shows twice (:1358, :1599) and whose owner is any `Component`; nor `AutonomyViewerPanel` at all - the panel RLU-B2 found "built but not shown" (IND9X-C4), whose `save()` report RLU2-C5 named in its Where and in the pin it proposed (*"`AutonomyReport.show(ui,` in `AutonomyViewerPanel.save`"*).  Every one of those sites passes `ui` today; any of them back on `this` is IND9X-C4 again under Window Always on Top, with the pin green.

**Direction.** The pin reads `AutonomyViewerPanel.java` as well, and counts `AutonomyReport.show(` as a message.

**Verification request.** Mutation: `AutonomyReport.show(this, session.save())` at `LayoutRightclickAutonomyMenu.java:1358`, or `this` as the owner at `AutonomyViewerPanel.java:1548`.  Run `testNoMessageOnARightClickMenuBelongsToTheMenu`.  **Proves:** green.  **Refutes:** red.

### RLD3-C5 - behaviour.md says a placed train faces the file's way "whatever facing the square's last occupant left there"; where the file cannot say, the last occupant's stays, and the log does not count it as the guess the code calls it

| | |
|---|---|
| **Disposition** | Fixed in the records (81a9fffa). |
| **Grade** | C - records; the uncounted guess predates the round |
| **Names** | RLA2-B3 |
| **Where** | `behaviour.md:2444-2445` and `:2038-2039`; `AutonomySession.java:707`, `:714-715`, `:740`, `:1141` |
| **Needs execution** | no |

`cc56a7a8` asks the file for every placed train's facing (:1141) and keeps a square's recorded facing only where the file cannot say (:740, `if (ran == null && getFacing(tile) != null) continue;`), with a javadoc paragraph saying so.  behaviour.md's new sentence states the first half as the whole rule; and its import-facing paragraph (:2038) still says a facing is guessed wherever the edges cannot say - not where the square records one.  The kept facing is, in the method's own words, *"a guess as good as the first copy's"*, yet it reaches neither `facingsInvented` nor the log line, which the same javadoc (:707) counts *"so the log can say how many were guessed"* (ACC-C4).  Before the round those squares were skipped whole, so the silence is older; the round wrote down why it should be counted.

**Direction.** A clause in each behaviour.md sentence ("where the file says; where it cannot, the square's recorded facing stays"); and count the kept facing as guessed, or say in the javadoc why it is not.

### RLD3-C6 - Comments and bookkeeping the round left behind

| | |
|---|---|
| **Disposition** | Fixed - the comments, the ratchet's line, the class javadoc and the MUTATION line (cca48e96, 81a9fffa); `standingIn` is used again; the unshown duplicate homes are filed as OB-304. |
| **Grade** | C - records |
| **Where** | as listed |
| **Needs execution** | no |

- `MarklinLocomotive.java:1180` (rewritten for RLA2-C9): `clearMultiUnitConflictsWith` is *"reached from placing, loading and every edit door's sweep"* - since the same commit, from the Central Station sync's too.
- `AutonomyMenu.java:332`: Import *"does not act on the configuration that is running - it refuses that name"*.  Every import then reloads the running configuration (`loadAfterImport` -> `configurationToLoadAfterImport` -> `load`, which captures, and asks to stop trains that are moving).  The clause is older; the round rewrote the sentence around it.
- `AutonomyViewerPanel.java:661`, `:666`: `loadAfterImport` *"Loads what was just imported, when nothing is running yet ... Only when nothing is loaded"* - untrue since `bd9409ec` (2026-08-18) made it load the running configuration again; `cc56a7a8` edited the method beneath it.
- `AutonomySession.java:532`: `duplicateHomes` is *"Counted so it can be said out loud"*, and is still said nowhere in `src/`.  The round made the configuration's kept homes said (`homesKept`), not the file's twice-named ones; `testAutonomyDiagramSession`'s message (*"so nothing can tell the user"*) stays true.
- `testJavadocsAreAttached.java:56-58`: `ALLOWED = 86` with no dated line for 87 -> 86, which RLU2-C1's fix asked for "like the others"; the header is the ratchet's history.
- `testTheImportDoorReadsAnOldFile.java:921`: `standingIn` is unused since `5322fb65` removed the claim it served; the class javadoc (:36) still names *"the replace question"*, where the door now asks one of two questions, or refuses.
- `testMessageBundles.java:906`: `testEveryKeyAScreenAsksForIsInEveryLanguageWithAValue`'s MUTATION still says *"give any asked-for key an empty value in one language, and this fails naming it"* - this method still cannot see about 340 of them; `testEveryKeyHasAValueInEveryLanguage` is the one that fails.

---

### RLD3-D1 - RLD2-C1: behaviour.md and `sanitizeMultiUnits`' summary now state the rule the code has

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |

behaviour.md:1953-1963 now says an edit sweeps from the edited train where it stands and from every standing multi-unit that drives it, a consist linked here or one the Central Station holds, asking what putting it down again would; the summary line of `Layout.sanitizeMultiUnits` says the same and its body agrees (the self-sweep, then each standing head found by `isLinkedTo` or `getModelMultiUnitLocomotives().contains`, each checked as still standing).  The sync sentence the fix added is RLD3-C3.

### RLD3-D2 - RLD2-C3: the capture it asked to pin is gone, and the reload after an import captures as every reload does

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |

`cc56a7a8` removes the capture into the running configuration and the `loadAfterImport(name, captureRunningState)` overload; the refusal means `into` is never the configuration running.  `loadAfterImport` calls `load(toLoad, true)`, which captures the running layout into the running configuration before the rebuild, so a train a run moved keeps its square - the property RLD2-C3's strengthened claim asked for now rests on `load`'s own capture, which `testARunSurvivesADiagramEdit` and `testARunSurvivesAPageRename` hold at other doors.  The comment at :1242 says why.  The monitor census lists only `AutonomyViewerPanel.java#load`, and the call RLU2-C2 found is gone.

### RLD3-D3 - RLD2-C4 (as RLU2-C7): the Routes claim reads the question on screen

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |

The names are cut from `words(run.question)` by the template's own text either side of a marker (`route.ui.confirmRearmImported` has `{0}` only), split as the door joins them, and asserted to leave out the sensorless route and to equal the model's list.  A door that built its list from the file's `auto` flags names that route, and the claim is red.  `ViewListener.routesSavedArmed`'s javadoc now says "and a sensor to watch".

### RLD3-D4 - RLD2-C5 (as RLU2-C3): every key has a value in every language, and the eight bundles agree at HEAD

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |

`testEveryKeyHasAValueInEveryLanguage` reads English's keys and each bundle's value for them, allowing empty only to a `...Suffix` key - there is one, `stats.ui.valuePluralSuffix`.  In memory at HEAD: 1,838 keys in all eight, no duplicates, every placeholder set equal to English's, no byte above 127, no straight apostrophe in a value with a placeholder, and the only empty values Italian's and Polish's plural suffix.  The round's three keys - `autosetup.ui.infoLegacyHomesKept`, `autosetup.ui.errorImportIntoConfigurationInUse` and `autolayout.why.startReachesNoStationEitherWay` with its new `{2}` - are in all eight with English's placeholders.

### RLD3-D5 - RLD2-C6, and the round's other new claims: their fixtures cannot supply the answer

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |

- **The Central Station multi-unit.**  The pin builds `new Layout(model)`, which the model's own re-address cannot sweep, asserts the member is not linked in TrainControl, and fails if heads are found by links alone (S6).
- **The homes.**  `getConfiguration` returns the store's own object, so the home moved to BottomMainC is what the second import seeds from; one home and the named message are asserted.  The train is now named from the file (`d779578b`).
- **The no-station copy.**  The westbound copy is built as no station and asserted to be a barred copy; since `whyItReachesNoStation` asks `whyNoTrainIsStartedFrom` of the other copy, the barred, station and switched-on clauses are one predicate that the start's own claims also hold.
- **The refusal** asserts the name is the window's running configuration first, then the configuration, the names and `setup.json` unchanged and the message.  **The facing** picks a square the first import faced from the file (MT-491's four are all the file's), sets the other side in the live configuration, and fails under the old skip.  **The sync** asserts the served address arrived before asserting the eviction, and no other sweep can reach its two squares.

### RLD3-D6 - RLD2-C7, RLD2-C9, RLD2-C10 and RLD2-C11, and the round's records and counts

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |

- RLD2-C7: MT-298 has a dated comment (`d779578b`); the javadocs at :208 and on `importFromTheMenu` say what the door asks.  (What MT-298 now meets is RLD3-C1.)
- RLD2-C9: Readme.md:400 matches `MarklinRoute` - a stop route meets no question at either door (`conflictingAccessoryAndReason` returns null; `askable` is false), the per-command check still skips the switch under a train, and the stop goes out.
- RLD2-C10: Automation.md:311 and behaviour.md:705 give the two remedies `whyItReachesNoStation` and the new sentence give.
- RLD2-C11: 3,726 + 336 + 13 + 258 + 52 + 48 + 51 + 30 + 59 = 4,573; the store holds 4,573 rows for 4,216 findings, as behaviour.md:2484 says.  Inbox: 57 OB and 32 FR, 89, as open-questions.md says.  tests.md: 586 headings, 453 fixed validated, 121 superseded, 10 fixed unvalidated, 2 needs test; the ledger's 12 rows and "574 of 586".
- The store: every round-2 disposition matches its document's; RLA2-C2 and RLU2-C12 read Open - deferred, RLU2-C9 Open, the rest Closed.

### RLD3-D7 - RLD2-C12 (as RLU2-C6): the editor's right-click messages belong to the editor

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |

All fourteen `catch` sites in `LayoutEditorRightclickMenu` pass `edit` (thirteen) or the new `owner` field (:549); the pin reads the file and was red first against it.

### RLD3-D8 - RLD2-C8: the battery the disposition cites predates round 2; nothing in round 2 moves either method, and the final battery has to say so again

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right, on the record |
| **Needs execution** | yes - the release's final battery |

The evidence is battery-0925g at `98b300d6`.  Round 2 touched neither method's path: `testTheMaximumWalkOnHisRailway`'s mass-assign code is not in `cc56a7a8`, and `testASecondImportFromTheMenuKeepsAHandMadeChange` imports under "MT-298 import", which the refusal does not reach (RLD3-C1 is why that is not the whole answer).  But the rule is "green in a full battery", and both can skip - one where the desktop does not give the prompt the keyboard, one where no named station has a maximum - as can the three new import claims without a display.

**Verification request.** From the release's final battery, the per-method status of those two and of the three new methods in `testTheImportDoorReadsAnOldFile`.  **Proves:** all passed.  **Refutes:** any skipped - then its MT goes back on Adam's list and the report says so.

### RLD3-D9 - build.xml, the window census, the live-snapshot README and the javadoc ratchet need nothing; round 2's D findings still hold

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |

- `git diff --name-status --diff-filter=ADR 98b300d6 HEAD` lists only the three round-2 documents: no test class added or removed, so build.xml's registrations and the window census stand.  The new methods open their sandbox as the first statement inside the try.
- The live-snapshot README's "Used by" names 78 classes, exactly the 78 that name `"live-snapshot"`, with `testTheEditorSaysWhatItsToolsDo` added in `5322fb65`.
- The orphan rule, re-run in memory: 86 at `98b300d6` and at HEAD, `AutonomyViewerPanel.java` at 2 - what the ratchet pins.  `cc56a7a8`'s new javadocs (`whyNoTrainIsStartedFrom`, `homesKept`, `owner`) each sit on a member.
- RLD2-D2's `configurationToLoadAfterImport` before `save()`, RLD2-D5's three tail doors (now pinned, RLU2-C4) and RLD2-D6's character sets are unchanged.

### RLD3-D10 - RLA2-C5's test javadoc and RLU2-D8 disagree about whether the editor's Place can reach a minimised editor; the owner rule holds either way

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |

`testThePlaceDoorsKeepTheHeading`'s new paragraph says *"the editor's own door cannot be used while it is minimised"*; RLU2-D8 says the Place dialog is owned by the main window, *"so the editor can be minimised behind it"*.  The first is about opening the door, the second about the moment its dialog is up; whether Windows lets a window blocked by a modal dialog be minimised decides which is the reachable case.  Either way `openLayoutEditorWindow` answers null for a minimised editor, the claim pins that owner rule, and behaviour.md:1144-1145 states it as a rule, not as a door - so nothing needs changing unless Adam wants the word "today" out of the javadoc.
