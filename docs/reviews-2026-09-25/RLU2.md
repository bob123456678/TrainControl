# RLU2 - validation, round 2: the windows (every screen, dialog and menu) and the message bundles (TrainControl 3.0.0 release readiness)

**Status:** open

**Prefix:** RLU2

**Reviewed:** branch `autonomy-diagram-r0`, the fixes `146b25eb..7e2fc558` in `src/org/traincontrol/gui/` and the eight bundles, on 2026-09-25.  HEAD moved to `98b300d6` while I read; that commit is records only (BPV.md, findings.tsv, triage.db, and a count in behaviour.md and open-questions.md) and changes no source, so every line number below is HEAD's.

## Method

Read-only: nothing compiled or run, no JVM, no git state changed, nothing under `cs2_sample_layout/` opened, and this file is the only one written.  I read the brief, `docs/reviews/README.md`, and the three round-1 documents (`docs/reviews-2026-09-25/RLU.md` whole, `RLA.md` and `RLD.md` whole).  Then every commit of `git log 146b25eb..HEAD` as a diff (`git show`): `ab3a37c2`/`bb197b16` (RLU-A1), `e7a2f1fa` (its `TrainControlUI` half), `01cf0d72`/`2fa033f3` (the old-file import), `e4f61822`/`693dd0a1` (RLU-B2), `b1b653a0`/`babde2bb` (RLU-C2, RLU-C3), `3af66907` (OB-299's sentences and bundles), `26b18432` (RLU-C8), `11bf5b82`/`459444f7` (RLU-C9), `27c668af`/`b5226b46` (RLA-C4), and the records commits `7e2fc558` and `98b300d6`.  Around each I read the whole door and its siblings at HEAD: the Routes > Import door, `MarklinControlStation.routesSavedArmed`/`importRoutes`, `MarklinRoute.fromJSON`/`hasS88`, `ViewListener`; `AutonomyViewerPanel.importConfiguration`/`activateTheConfigurationNamed`/`importLegacyGraph`/`loadAfterImport`/`load`/`save`; `AutonomySession.importLegacy`/`captureFromLayout`/`configurationToLoadAfterImport`; `AutonomyCompanionStore.snapshotSetup`/`restoreSetup`/`createConfiguration`; `AutonomyMenu`'s Configuration submenu; `TailCrossedPrompt` (`askAfterPlacement`, `Answer`, `whereTheAnswerGoes`, `noteADroppedAnswer`, `DiagramPick.of`); the three placement doors (`TrainControlUI` paste 8130-8260, `LayoutRightclickAutonomyMenu` 1255-1374, `GraphLocAssign` 296-368); `TrainControlUI.isLayoutEditorOpen`/`openLayoutEditorWindow`/`wrappedToFit`; `LayoutEditor.mayLeave`/`hasUnsavedAutonomyWork`/`confirmExit`/`closeAutonomyMode`/`discardAutonomyWork`/`takeTheUndoPoint`/`dispose`; every `JOptionPane` call in `LayoutRightclickAutonomyMenu` and `AutonomyViewerPanel`, and in every other `JPopupMenu` subclass under `gui/`; `LayoutPopupUI`'s right-click; `Layout.whyItReachesNoStation`/`isSendableDestination`/`canReachAnyDestination`/`isABarredCopyOfAStation`; `Point.toJSON`.  Tests read: `testTheRoutesImportDoorAsksByName` and `testTheImportDoorReadsAnOldFile` whole; the new claims in `testWhereHisTrainsMayBeSent` (with its `@AfterMethod`), `testTheTailIsPickedOnTheDiagram` (with its two source pins at :629 and :1148), `testThePlaceDoorsKeepTheHeading`, and `testMessageBundles`' every-key test; and the structural tests today's source changes could move - `testJavadocsAreAttached`, `testNothingOnTheEventThreadTakesTheRailwaysMonitor`, `testNoSelfRecursiveWrappers`, `testAHandSendIsRefusedWhileTheSetupIsBroken.door`, `testASecondImportFillsGapsAndDoesNotOverwrite`'s source pin, `testAnImportSaysItsRoutesAreOff`.  **Mechanical checks, all in memory** (Python reading `git show` output through a pipe; no file written): the eight bundles (key sets, duplicates, placeholder sets, bytes above 127, empty values, straight apostrophes in values with a placeholder); every key of the English bundle that appears as a string literal in `src/`, against the every-key test's four patterns; the javadoc-orphan count and the monitor census, each re-implemented from its own test's logic and run at `146b25eb` and at HEAD; every string literal in `test/` against the changed source files at `146b25eb` and HEAD, to find a source pin a fix broke; the MT-491 and MT-298 files' points.  For RLU-A1's "carried to 2.8.2" I read `git show master:` and ran `git status --no-optional-locks` in the master worktree.  `docs/manual-tests/findings.tsv` was searched before each finding: the prefix RLU2 is free, and nothing here repeats a catalogued finding (DIR-C8 and IND9X-C5 are the popup-parent shape at other sites, both closed).  **Everything below is from reading**; each finding that needs a run says so, with the fixture and what proves or refutes it.

There is no A and no B.  Two of the Cs (RLU2-C1, RLU2-C2) are battery classes that my re-implementation of their own logic says are red at HEAD, both from `2fa033f3`; they cost nothing on the railway and should be fixed before the next battery.

---

### RLU2-C1 - `regression.testJavadocsAreAttached` is red at HEAD: RLU-C6's fix removed an orphan the exact ratchet still counts (86 found, 87 pinned)

| | |
|---|---|
| **Disposition** | Fixed - the ratchet is 86 with AutonomyViewerPanel at 2 (5322fb65); green in the fix run. |
| **Grade** | C - a battery class goes red; nothing on the railway.  (VD10-B3 graded the same shape B.)  Needs execution. |
| **Where** | `test/regression/testJavadocsAreAttached.java:58` (`ALLOWED = 87`), `:93` (`AutonomyViewerPanel.java (3)`), `:145` (`assertEquals(found, ALLOWED)`); `2fa033f3` |

RLU-C6 asked for the stray "Debug builds only" javadoc to go, and `2fa033f3` deleted it.  It was a javadoc stacked directly on another, which is exactly what this ratchet counts, and the ratchet is exact in both directions: it fails when the count rises and also when it falls ("Lower ALLOWED to N so the improvement is kept"), and it pins the per-file breakdown.  Counted with the test's own rule (a `/**` block followed by nothing but whitespace before the next `/**`): 87 at `146b25eb` with `AutonomyViewerPanel.java` at 3, and 86 at HEAD with `AutonomyViewerPanel.java` at 2; no other file changed its count, and today's insertions (`wrappedToFit`, the second `noteADroppedAnswer`, `anEditorOpenedInTheWait`, the second `loadAfterImport`) orphaned nothing.  So the fix is right and the test now fails on both its total and its file list.  **Fix:** `ALLOWED = 86`, `AutonomyViewerPanel.java (2)`, and a dated line in the header like the others.

**Verification request.**  Run `regression.testJavadocsAreAttached` at HEAD.  **Proves:** red, "86 orphaned javadocs remain, fewer than the 87 recorded".  **Refutes:** green.

---

### RLU2-C2 - `regression.testNothingOnTheEventThreadTakesTheRailwaysMonitor` is red at HEAD: RLA-C2's capture in `importLegacyGraph` is a door onto `Layout.toJSON` the census does not list

| | |
|---|---|
| **Disposition** | Fixed - the call is gone with the round-1 capture (cc56a7a8); the census is green in the fix run. |
| **Grade** | C - a battery class goes red; the call itself is safe.  Needs execution. |
| **Where** | `AutonomyViewerPanel.java:1251`; `test/regression/testNothingOnTheEventThreadTakesTheRailwaysMonitor.java:242` (the allowance `load` has), `:281` |

`2fa033f3` added, for RLA-C2, `session().captureFromLayout(ui.getModel().getAutoLayout().toJSON(), into)` inside `importLegacyGraph`.  `Layout.toJSON` is `synchronized` (`Layout.java:11849`), so this is a call from a user-interface class onto the railway's monitor, and the census requires every such call to be listed by `File.java#member` with the thread it is on.  `AutonomyViewerPanel.java#load` is listed for the same capture; `#importLegacyGraph` is not.  Re-implementing the census (its declaration pattern, its comment stripper, its four-space member rule) over `gui/` and `automationui/`: nothing unlisted at `146b25eb`, and exactly `AutonomyViewerPanel.java#importLegacyGraph` at HEAD.  The call is as safe as `load`'s - it runs only when `!ui.isAutonomyBusy()` - so the fix is the allowance, not a move: "ON THE EVENT THREAD: an old file imported into the configuration running captures it first, only while isAutonomyBusy() is false".  With RLU2-C1 this says the full battery was not run after `2fa033f3`; the 18 mutation runs of `7e2fc558` were per class.

**Verification request.**  Run `testEveryDoorOntoTheRailwaysMonitorIsWrittenDown` at HEAD.  **Proves:** red, naming `AutonomyViewerPanel.java#importLegacyGraph` and quoting line 1251.  **Refutes:** green.

---

### RLU2-C3 - RLU-C8 is fixed in part: the every-key check still does not see 343 keys of the English bundle, today's Import question among them

| | |
|---|---|
| **Disposition** | Fixed - every key of the English bundle is asked to have a value in every language, whoever asks for it (testEveryKeyHasAValueInEveryLanguage, 5322fb65); mutation S11 red. |
| **Grade** | C - a test that would not catch its own regression.  Needs execution. |
| **Where** | `test/core/testMessageBundles.java:912`, the fourth road at `:925`; `AutonomyViewerPanel.java:1063-1064` |

RLU-C8 found the empty-value half blind to keys held in constants, and `26b18432` added that road.  The class of the defect is wider than constants: a key that reaches `I18n` or the log by any road other than a literal first argument.  Counted at HEAD, 343 keys of the English bundle appear in `src/` as string literals that none of the four patterns sees: 247 only as the first argument of a `logf` call (the log window), and 96 elsewhere - chosen by a ternary, returned from a `switch`, handed to a helper, or held in an array or an enum.  Among the 96: `autosetup.ui.confirmImportFillsGaps` and `autosetup.ui.confirmImportOverwrites`, the question `2fa033f3` added today (`I18n.f(format == ... ? "..." : "...", ...)`); Why not Moving?'s two headers `autolayout.ui.whyHeaderByHand`/`whyHeaderCandidates`; `autolayout.ui.confirmClearAllTrackLengths`/`...MaxTrainLengths`; `autosetup.ui.errorEditorAlreadyOpen`; `layout.ui.confirmRouteActiveRoute`/`...ProtectingSignal`; the four `loc.error...AddressOutOfRange`; `route.ui.valueTrue`/`valueFalse`/`valueWould`/`valueWouldNot`; `autolayout.ui.sideN`/`E`/`S`/`W`.  RLU-C9's new line `autolayout.ui.logTailAnswerDroppedEditorOpened` is among the 247.  The test's javadoc still promises "give any asked-for key an empty value in one language, and this fails naming it".  Today every one of these has a value in all eight bundles (checked), so this is about the next edit.  **Direction:** replace the four roads with the one RLD-D3 used - every string literal in `src/` that is a key of the English bundle - keeping the built-family list and a floor.

**Verification request.**  Blank `autosetup.ui.confirmImportFillsGaps` in `messages_da.properties` and run `testEveryKeyAScreenAsksForIsInEveryLanguageWithAValue`.  **Proves:** green.  **Refutes:** red, naming the key.

---

### RLU2-C4 - RLU-C9's claim drives the paste door only; the same gate at the right-click Place and the locomotive dialog has no claim and no source pin

| | |
|---|---|
| **Disposition** | Fixed - the other two doors are pinned by their source (5322fb65); mutation S9 red. |
| **Grade** | C - a test that would not catch its own regression at two of the three doors.  Needs execution. |
| **Where** | `LayoutRightclickAutonomyMenu.java:1322`, `GraphLocAssign.java:326`; `test/regression/testTheTailIsPickedOnTheDiagram.java:1378` (the claim), `:629` and `:1148` (the pins) |

The disposition says "all three doors then drop the answer".  They do (RLU2-D7), but only the paste door is held: `testAnAnswerAfterAnEditorOpenedIsNotWritten` pastes with Control+V.  This class already holds the other two doors by their source, because each needs a menu or a dialog to reach - `testEveryPlacementDoorAsksWhetherItStillStands` and `testTheOtherDoorsKeepTheLateAnswerRules` ("undo any of the five at either door, and this fails naming it") - and neither was given RLU-C9's `&& !answer.anEditorOpenedInTheWait()`.  Delete it at either door and every test stays green, while a late answer given after an editor opened goes back into the setup the editor's Cancel restores.  **Fix:** a sixth rule in `testTheOtherDoorsKeepTheLateAnswerRules`: between `askAfterPlacement(` and `whereTheAnswerGoes(`, the door asks `anEditorOpenedInTheWait()`.

**Verification request.**  Remove `&& !answer.anEditorOpenedInTheWait()` at `LayoutRightclickAutonomyMenu.java:1322`; run `testTheTailIsPickedOnTheDiagram`.  **Proves:** green.  **Refutes:** red, naming the right-click door.

---

### RLU2-C5 - RLU-B2's claim holds one refusal of eleven re-parented messages; the other ten can go back to `this` unseen

| | |
|---|---|
| **Disposition** | Fixed - a pin reads every message on both right-click menus (5322fb65), red first against the editor's menu. |
| **Grade** | C - a test that would not catch its own regression.  Needs execution. |
| **Where** | `test/ui/testWhereHisTrainsMayBeSent.java:125`; `LayoutRightclickAutonomyMenu.java:463, 581, 869, 1416, 1429, 1455, 1478, 1488, 1541, 1549`; `AutonomyViewerPanel.java:1556` |

`693dd0a1` re-parented eleven messages on the main window.  `testARefusedSendSaysSoInFrontOfTheWindow` is a real claim - it builds the menu without showing it, so a message parented on the menu belongs to Swing's hidden frame, and it asserts `getOwner() == ui` - but it reaches only the power-off refusal (`:1416`).  The broken-setup, too-long, fouls-a-road and own-tail refusals of the same item, the check-the-log message, the three `e.getMessage()` catches, and `AutonomyViewerPanel.save`'s setup-tidy report have neither a claim nor a pin.  This shape has already come back once: DIR-C8 fixed a dialog parented on a closed popup, and IND9X-C5 found it again.  **Fix:** a source pin - no `JOptionPane.show...(this` in `LayoutRightclickAutonomyMenu.java`, and `AutonomyReport.show(ui,` in `AutonomyViewerPanel.save` - which holds all eleven in one assertion.

**Verification request.**  Put `this` back at `:1455` (too long for the route); run `testWhereHisTrainsMayBeSent`.  **Proves:** green.  **Refutes:** red.

---

### RLU2-C6 - the track-diagram editor's right-click keeps fourteen messages owned by its own popup - IND9X-C5's shape, not swept with RLU-B2

| | |
|---|---|
| **Disposition** | Fixed - the editor's right-click messages belong to the editor (cc56a7a8); the menus' pin red first; mutation S8 red. |
| **Grade** | C - latent: reached only when an editing action throws.  Needs execution. |
| **Where** | `LayoutEditorRightclickMenu.java:60, 179, 197, 219, 235, 257, 314, 341, 364, 389, 435, 461, 502, 540`; `LayoutEditor.java:5644` |

Every item of the layout editor's right-click menu (Paste, Undo, Redo, Cut, Copy, Rotate and the rest) catches an exception with `JOptionPane.showMessageDialog(this, e.getMessage())`, `this` being the popup - which has left its window by the time the item runs, so the message belongs to Swing's hidden frame.  The editor copies Window Always on Top from the main window (`LayoutEditor.java:5644`, on by default), so RLU-B2's consequence follows: a modal message that can open beneath the editor and hold every window.  The catches are for the unexpected - `LayoutEditor` throws nothing on purpose - so it is latent, and the message is a bare exception text ("null" for a NullPointerException).  The sibling menu `LayoutRightclickAutonomyMenu` was fixed this morning; this one is the other `JPopupMenu` under `gui/` with the same parent (every other popup there already uses its window).  **Fix:** `edit` (the editor window, the constructor's first argument) as the owner at all fourteen.

**Verification request.**  With Window Always on Top ticked and the editor maximised, make one action throw (for example a mutation that throws at the top of `LayoutEditor.rotate`) and choose Rotate from a tile's right-click.  **Proves:** the message's `getOwner()` is not the editor, and a screenshot shows the editor above it.  **Refutes:** the message opens above the editor.

---

### RLU2-C7 - RLU-C2's claim asks the model's list, not the question: a door that named every route saved armed again would pass all four claims

| | |
|---|---|
| **Disposition** | Fixed - the claim reads the question on screen (5322fb65); ViewListener's javadoc says what the list is; mutation S10 red. |
| **Grade** | C - a test whose fixture supplies the answer. |
| **Where** | `test/regression/testTheRoutesImportDoorAsksByName.java:154` and `:356`; `ViewListener.java:160` |

`testASavedArmedRouteWithNoSensorIsNotCountedAsArmed` asserts `assertFalse(run.savedArmed.contains(noSensor[0]), "the question names ...")`, but `run.savedArmed` is not the question: the helper fills it by calling `model.routesSavedArmed(json)` itself (`:356`).  The question is in `run.question` and this claim never reads it.  So the mutation that was run (the filter taken out of `routesSavedArmed`) is caught, and a door-side return of the defect is not: a door that built its list from the file's `auto` flags would name the route with no sensor, and still pass this claim (the model's list is unchanged), the armed-after check (the model arms by its own rule) and the count (the door counts enabled routes).  `testAnsweredNoEveryRouteArrivesOff` ties the question to the model's list, but its file has no route saved armed without a sensor.  **Fix:** assert on `run.question` - for example that its words do not include the no-sensor route's name as a list entry.  Also: `ViewListener.routesSavedArmed`'s javadoc still says "the routes a route export saved with their automatic firing on", where the method now returns only those with a sensor.

**Verification request.**  In the door (`TrainControlUI.java:25127-25128`) build `savedArmed` from the file's `auto` flags instead of `routesSavedArmed`; run `testTheRoutesImportDoorAsksByName`.  **Proves:** green.  **Refutes:** red.

---

### RLU2-C8 - behaviour.md and TailCrossedPrompt's comment still say the list opens from the editor whenever one is open; since `b5226b46` a minimised editor's list is the main window's

| | |
|---|---|
| **Disposition** | Fixed in the records - behaviour.md and TailCrossedPrompt's comment (cc56a7a8). |
| **Grade** | C - records. |
| **Where** | `docs/reference/behaviour.md:1142-1143`; `TailCrossedPrompt.java:212-216` |

behaviour.md: the list is asked "where an editor window is open ... - and then the list opens from the editor, in front of it, not from the main window it covers (MT-575)".  `b5226b46` (RLA-C4) made `openLayoutEditorWindow()` answer null for an editor that is minimised or not showing, so with a minimised editor the list is still asked (`DiagramPick.of` declines on `isLayoutEditorOpen()`, which a minimised editor is) and opens from the main window.  `b5226b46` changed only `TrainControlUI.java`; the rule's record and the inline comment at the call ("IN FRONT OF THE EDITOR WHERE ONE IS OPEN") were not touched.  One clause each: "...opens from the editor while it is showing, and from the main window while it is minimised (RLA-C4)".

---

### RLU2-C9 - RLU-A1's "carried to 2.8.2" is not on master, and nothing tracks the port (RLA-B1 the same)

| | |
|---|---|
| **Disposition** | Open - the master ports (RLA-B1, RLU-A1, and RLU-B2 with BPV-C8) go into 2.8.2 after its release validation reports. |
| **Grade** | C - records; the consequence, if 2.8.2 ships without it, is RLU-A1 itself on 2.8.2 (pre-existing there since 2.7.4). |
| **Where** | `docs/reviews-2026-09-25/RLU.md:40`; `git show master:src/org/traincontrol/gui/TrainControlUI.java`, line 13881 |

RLU-A1's disposition ends "2.7.4 read it the same way; carried to 2.8.2."  Master at `561c8ac3` still reads the file with no character set - `this.model.importRoutes(new String(Files.readAllBytes(Paths.get(f.getPath()))))` - and its worktree has no uncommitted change.  No Inbox entry, open question or commit on any branch names the port.  RLA-B1's disposition says the same in the future tense ("carried to 2.8.2 with the release's other ports"), with the same absence of a tracker.  The grade is RLU-A1's own: on Java 8 under Windows a restored route's accented locomotive name matches nothing and its command is skipped.  **Fix:** either port both before 2.8.2 is released and name the commits in the dispositions, or say "to be carried" and file the port where 2.8.2's release validation will see it.

---

### RLU2-C10 - RLU-C4's either-way sentence still has no remedy for a station reachable only on a side trains may not arrive at

| | |
|---|---|
| **Disposition** | Fixed - the either-way sentence names the side a train would arrive on, under the arrivals menu (cc56a7a8); mutation S12 red. |
| **Grade** | C - a refusal whose remedy is incomplete; a trap for another railway more than for his.  Needs execution. |
| **Where** | `Layout.java:5052-5066`, `:9430`, `:11236`; `autolayout.why.startReachesNoStationEitherWay` (`messages.properties:1795` and the seven translations) |

`3af66907` made the sentence name what a reachable station needs - "switch it on and tick "Can Be Chosen in Full Autonomy" - a station where trains turn round is never chosen" - which is exactly `isSendableDestination`'s three conditions.  There is a fourth way a station can be out of reach: the copy the train would arrive at is barred ("Trains May Arrive..." closed on that side).  A barred copy is not a destination (`isABarredCopyOfAStation` requires `!isDestination()`), so `canReachAnyDestination` walks past it, and a siding whose only station lies beyond a closed side gets this sentence.  The operator finds that station switched on and ticked, and the sentence offers nothing else.  The neighbour sentence `autolayout.why.startFacingBarred` already has the words for it ("open that side under "{1}" in the autonomy editor").  **Direction:** add that remedy to the either-way sentence in all eight bundles, or have `whyItReachesNoStation` name a station it reaches only on a closed side.

**Verification request.**  A hand-built layout: a siding whose only way out leads to a terminus station entered on a side with Trains May Arrive closed; a train on the siding.  **Proves:** Why not Moving? gives the either-way sentence, and opening that side lets autonomy start the train.  **Refutes:** another sentence is chosen, or opening the side changes nothing.

---

### RLU2-C11 - "Add to it what the file has and {0} does not?" brings no station maximum into a configuration that has ever run: every station there states 0

| | |
|---|---|
| **Disposition** | Not a defect - a 0 is how a configuration states "no limit", by hand or by a capture; the gap-fill cannot tell the two apart and keeps both, so "nothing already set in it is changed" is true of it.  behaviour.md says so.  Superseded 2026-09-25: its premise was false - the editor stores a default as nothing, only a capture writes 0 (RLA3-B1, RLU3-C4) - and the question is put to Adam. |
| **Grade** | C - a question that promises more than the door does; the dialog's count stays true.  Needs execution. |
| **Where** | `autosetup.ui.confirmImportFillsGaps` (`messages.properties:1523`); `AutonomySession.java:1146` (the gap test), `:3209` (`POINT_OPERATIONAL_KEYS`); `Point.java:1196`; `AutonomyViewerPanel.java:1246-1260` |

RLU-B1 was a question whose Yes did something else; today's replacement reads "Add to it what the file has and {0} does not?  Nothing already set in {0} is changed."  For station maxima it is still not what happens once {0} has ever been the configuration running.  `Point.toJSON` writes `maxTrainLength` for every station, 0 included (`Point.java:1196`), and the capture copies it into the configuration (`POINT_OPERATIONAL_KEYS`), so every station of a configuration that has run - on a reload, at exit, or now at the start of an import into it by name (RLA-C2) - states a maximum of 0, which means no limit.  `importLegacy` fills a setting only where the configuration states nothing (`if (!point.has(key) || extras.has(key)) continue;`), so none of the file's station maxima arrive, while the same method treats the file's own 0 as "not a capacity" and skips it.  RLA-C2's disposition records this for the configuration running by name ("A maximum stays as the running configuration has it"); it holds for every configuration that has run, and the new question tells the operator the opposite.  The message's "carried {3} other settings" is true, since they are not counted.  **For Adam:** either word the question for it ("...a station's maximum is not added where {0} already has one, including none"), or have the gap test treat a configuration's 0 as a gap for `maxTrainLength`, as the file side already does.  The second overwrites a maximum cleared to none by hand, which is the trade to decide.

**Verification request.**  Live-snapshot sandbox; the configuration in use (which has run); a station the MT-491 file gives a maximum above 0.  Import the MT-491 file from the menu under that configuration's name, Yes.  **Proves:** the station's `maxTrainLength` is still 0 afterwards.  **Refutes:** the file's maximum is there.

---

### RLU2-C12 - an old-file import that fails puts back the page exclusions its first dialog has just announced

| | |
|---|---|
| **Disposition** | Follow-up - filed as OB-302: narrow and cosmetic; the next import excludes the pages again and says so. |
| **Grade** | C - narrow and cosmetic: a message made untrue by the rollback that follows it.  Needs execution. |
| **Where** | `AutonomyViewerPanel.java:1177`, `:1220-1226`, `:1383-1387` |

RLA-C3/RLU-C1's fix takes the snapshot at the top of `importLegacyGraph`, before `excludeRepeatedSensorPages()`.  When that call shuts pages, the door says so at once ("autosetup.ui.infoPagesExcludedForSensors").  If the file then fails before the save, `restoreSetup(was)` puts back the shared half, and the excluded pages are part of it, so the pages it just said were left out are in again, with nothing said.  behaviour.md's "leaves the setup as it was" is kept.  It needs both a setup whose repeated pages were never settled and a file that fails part way.  **Fix:** snapshot after the exclusion, or say in the failure message that nothing was changed.

**Verification request.**  A setup with an unsettled repeated page; import the MT-491 file with `"timetable": {}` (as `testAFileItCannotReadLeavesTheSetupAsItWas` builds it).  **Proves:** the exclusion message, then the failure, then that page not excluded.  **Refutes:** the page stays excluded, or no exclusion message was shown.

---

### RLU2-C13 - RLU-C9's "save nothing" also leaves unsaved the placement the door wrote before its question: after an editor opened in the wait, nothing writes where the train now stands until the next save

| | |
|---|---|
| **Disposition** | Not a defect - the placement is in memory and in the editor's copy, and the next save writes it (the editor's own, any later one, the exit); saving it then would commit the editor's unsaved work with it.  Only a crash before any save loses it, as for any edit held in memory. |
| **Grade** | C - narrow: it needs the RLU-C9 sequence and then a crash or power loss before anything else saves the setup; the consequence then is a train the setup does not have where it stands.  Needs execution. |
| **Where** | `TrainControlUI.java:8117-8141` and `:8225-8264` (`rememberPlacement`); `LayoutRightclickAutonomyMenu.java:1290-1348`; `GraphLocAssign.java:325-357`; `LayoutEditor.java` `confirmExit`/`closeAutonomyMode` |

Each placement door writes the placement, the facing and the side into the setup in memory, asks the tail question, and saves once at the end.  RLU-C9's fix makes an editor opened in the wait clear `setupStands`, and the save is skipped with the answer, so the placement is not saved either.  It is in the editor's snapshot, so the editor's Cancel with changes (`discardAutonomyWork` -> `restoreSetup`, which saves) or its OK writes it.  But an autonomy editor closed with nothing changed goes out through `closeAutonomyMode`, which saves nothing, and its pre-edit note is forgotten.  From then until the next save - another placement, a configuration load, the capture at exit - `setup.json` still has the train where it was before the door ran.  After Control+X, which saved the cleared square at once, that is nowhere.  A crash or power loss in that window starts the next session with the train missing from the square it stands on, which the setup's other doors exist to prevent (REG8-B2, VAL9-B1).  Before the fix the late save wrote it, road and all.  TDU4-C2's "save nothing" is safe because the reset that replaced the setup wrote it; here nothing does.  The occupied sensor still refuses a path into the platform where the train stands on it.  **Direction:** save what the door wrote before its question before asking it - no editor can be open then, since every main-window door refuses with one open - and keep "save nothing" for the late answer alone.

**Verification request.**  The RLU-C9 claim's fixture: Control+X 75 407 DB from BottomMainB, Control+V at Tunnel from the north so the question waits; open the autonomy editor; click TunnelPre; close the editor with nothing changed; read `setup.json` from the sandbox before anything else saves.  **Proves:** its configuration has 75 407 DB on no square.  **Refutes:** it has it on Tunnel.

---

### RLU2-D1 - RLU-A1: Routes > Import reads UTF-8, which was the only file read in `src/` that did not, and its claim is red on a code page and skipped, not passed, on UTF-8

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |
| **Where** | `TrainControlUI.java:25118`; `test/regression/testTheRoutesImportDoorAsksByName.java:177` |

One argument, at the one door: every `readAllBytes`, `InputStreamReader`, `readAllLines` and `new String(bytes)` in `src/` now names a character set, and every writer of a routes file writes UTF-8.  The claim renames a route saved armed with a u-umlaut, writes the file as UTF-8, imports it through the menu, and compares the route names; on a JVM whose default is UTF-8 it throws `SkipException`, so a battery run there reads as "tested nothing", not green.  The locomotive-name half of RLU-A1 is decoded by the same `new String`, so the route name stands for both.  (That the port to 2.8.2 has not happened is RLU2-C9.)

---

### RLU2-D2 - RLU-B1 (as RLA-B2): the question follows the file's kind, and a train or a home the configuration already has is not placed again

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |
| **Where** | `AutonomyViewerPanel.java:1024-1070`; `AutonomySession.java:951-976`, `:1106-1109` |

The file is read and its kind decided before the name question, so an old file into a name that exists asks `confirmImportFillsGaps` and a bundle still asks `confirmImportOverwrites`; an unreadable or unrecognised file is refused before either.  `importLegacy` seeds `standingAlready` and `homedAlready` from the configuration it writes into (the store's active one, which the door has just made `into`), so RLU-B1's case - a train moved since the first import - leaves it where it was and names it in `infoLegacyAlreadyPlaced`.  A train standing on the very square the file names is skipped earlier and silently, as before, so "already standing elsewhere" is never said of the same square.  `testASecondImportDoesNotStandATrainTwice` is a real claim: it moves the train in the configuration and asserts one square and the message, so starting the set empty again makes it red for the right reason.  (A home the configuration already has is skipped and only counted in `duplicateHomes`, which no message shows; that was so before, and behaviour.md promises the name only for trains.)

---

### RLU2-D3 - RLU-C1 (as RLA-C3), with RLD-C3: a failed old-file import leaves the setup as it was, and the running configuration is chosen before the save

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |
| **Where** | `AutonomyViewerPanel.java:1177`, `:1262-1277`, `:1380-1391` |

The snapshot is taken before `activateTheConfigurationNamed`, `whatALegacyImportLeaves` (the strict reads) now runs before the save, and a `RuntimeException` before the save restores the store and rebuilds the session without writing, so the new configuration is neither kept nor chosen, in memory or on disk.  That covers RLU-C1's case (a throw out of `importLegacy` itself) as well as RLA-C3's (the timetable).  The running configuration is made the store's choice before `save()`, so a declined or refused reload leaves `setup.json` naming it (RLD-C3).  Both claims read the setup on disk and fail for the reason they name.  The one thing the rollback also undoes, the page exclusions, is RLU2-C12.

---

### RLU2-D4 - RLU-C6 and RLU-C7 (as RLA-C1): the stray javadoc is gone with no new orphan from today's insertions, and the record, the code and the message agree on where the trains went

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |
| **Where** | `AutonomyViewerPanel.java:1136-1161`, `:1360-1371`; `docs/reference/behaviour.md` (the old-file import paragraph); `AutonomyMenu.java:297` |

The orphan is deleted and `activateTheConfigurationNamed`'s own javadoc now says "chooses", not "makes the one in use"; the orphan count fell by exactly one (the ratchet's side of that is RLU2-C1).  behaviour.md now says the imported configuration is chosen only while the import writes and loaded only where nothing was running, which is `configurationToLoadAfterImport`.  The message's `{2}`, "Autonomy > Configuration ({1})", is built from `autosetup.ui.menuAutonomy` and `autosetup.ui.menuConfigurations` with the configuration running - the same two keys and the same argument `AutonomyMenu` uses for the submenu heading - so it names the real menu in every language.  (The claim's helper `whereChosen` builds the same string, so it would not notice the menu's heading moving to another key; the door is right today.)

---

### RLU2-D5 - RLU-B2: every site the finding named, and the setup's tidy report, now belong to the main window, which lifts them above itself

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |
| **Where** | `LayoutRightclickAutonomyMenu.java:463, 581, 869, 1416-1549`; `AutonomyViewerPanel.java:1556` |

All nine lines RLU-B2 listed and the two it listed in brackets now pass `ui`; `AutonomyViewerPanel` has no `JOptionPane` or `AutonomyReport.show` left on anything but `ui`; every `AutonomyReport.show` in `src/` has a shown window for its owner (`ui`, or the editor's `owner()`).  A dialog owned by an always-on-top window is always-on-top too, so the Window Always on Top case of RLU-B2 is closed at these sites.  The same menu opens from a popped-out page window (`LayoutPopupUI`, `LayoutLabel.openStationMenu`): its refusals now open over the main window rather than the popped-out one - above everything, but perhaps on the other screen; `getInvoker()`'s window would put them where the click was, if Adam uses popped-out pages.  (The unguarded sites are RLU2-C5; the editor's own popup is RLU2-C6.)

---

### RLU2-D6 - RLU-C2 and RLU-C3: the question names exactly the routes the model arms, is wrapped in every language, and RLU-D2's count is still the model's

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |
| **Where** | `MarklinControlStation.java:4189-4215`, `:4226-4290`; `TrainControlUI.java:6768-6795`, `:25127-25142` |

`routesSavedArmed` now keeps a route only where `Math.abs(optInt("s88", 0)) > 0`, which is `hasS88()` of the route `MarklinRoute.fromJSON` builds from the same entry, so the question, the model's arming and the door's count all use one list.  A route saved armed with no sensor is no longer named, and it could not have fired anyway.  `wrappedToFit` breaks only at spaces, at most 90 characters a line, leaves an unbreakable run whole, keeps the paragraph break, and runs after `MessageFormat`, so it is language-blind; the claim's 100-character bound fails on the old single line.  The question still starts on No (`YES_NO_OPTS[1]`).  `testAnImportSaysItsRoutesAreOff`'s fixture routes all have a sensor, so it is unaffected by the filter.  (The claim's weak spot is RLU2-C7.)

---

### RLU2-D7 - RLU-C9: all three doors drop a late answer from both stores and save nothing; the editor's own door is untouched

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |
| **Where** | `TailCrossedPrompt.java:201-225`, `:681-694`; `TrainControlUI.java:8225-8255`; `LayoutRightclickAutonomyMenu.java:1321-1348`; `GraphLocAssign.java:325-353`; `autolayout.ui.logTailAnswerDroppedEditorOpened` |

`askAfterPlacement` notes whether an editor was open when it asked and whether one is open when the answer comes back; only "opened in the wait" sets the flag.  Each door folds it into `setupStands`, so `whereTheAnswerGoes` returns null - nothing on the running railway either, which keeps the two stores agreeing - and the save is skipped.  The log line names the train and the square in all eight languages, with the same placeholders, and gives a remedy (place it again).  A question asked from inside the editor (`GraphLocAssign` via the editor's Place, whose parent is the main window) had the editor open when asked, so it is never dropped this way.  An editor opened and closed again in the wait leaves the flag clear, and the answer then lands in whatever setup the close left, which is right.  Unanswered replies now come back as `new Answer(false, null, flag)` instead of the shared `NOT_ASKED`; nothing in `src/` or `test/` compares an `Answer` by identity.  (The two doors without a claim are RLU2-C4; what "save nothing" also leaves unsaved is RLU2-C13.)

---

### RLU2-D8 - RLU-D1's "the two cannot disagree" is out of date since `b5226b46`, and the disagreement is the fix

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right (a withdrawn clause of my own round-1 D) |
| **Where** | `TrainControlUI.java:6738-6751`, `:6797-6811`; `TailCrossedPrompt.java:782-791` |

RLU-D1 argued that `openLayoutEditorWindow()` asked exactly what `isLayoutEditorOpen()` asks, so the pick declined exactly when the list hung from the editor.  Since RLA-C4's fix they differ for a minimised (or not showing) editor: the pick still declines, since the editor is open, and the list hangs from the main window, where it can be seen.  That is the intended split, and no other caller shares `openLayoutEditorWindow()`.  RLU-D1's other half stands: no main-window door reaches the question with an editor open, minimised or not.  So RLA-C4's claim, which calls `askAfterPlacement(..., ui, ...)` directly "as the main window's own doors ask it", covers a case only the editor's own Place door can reach (its dialog is owned by the main window, so the editor can be minimised behind it); the claim is still the right unit claim of the owner rule.  (The records that still describe the old rule are RLU2-C8.)

---

### RLU2-D9 - the eight bundles at HEAD: 1,836 keys, identical in every language; the five new keys and the reworded OB-299 sentences say the same thing everywhere

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |
| **Where** | `src/org/traincontrol/resources/messages*.properties` at HEAD |

Read in memory: all eight hold the same 1,836 keys (1,831 plus the four of `2fa033f3` and the one of `459444f7`), no duplicates, every key's placeholder set equal to English's, no byte above 127, no straight apostrophe in any value with a placeholder, and the only empty values are Italian's and Polish's `stats.ui.valuePluralSuffix`.  The new keys (`confirmImportFillsGaps`, `infoLegacyAlreadyPlaced`, `infoLegacyImportedInto`, `infoLegacyImportedNotInUse`, `logTailAnswerDroppedEditorOpened`) carry the same sense and the same placeholders in each language, `{0}` three times in the question.  RLU-C5: Spanish writes "Dele la vuelta" now, and no "Déle" is left in any bundle.  RLD-C8: Danish, Spanish, Italian and Dutch now use their neighbours' word for autonomy (checked against `startFacingBarred` and `startNotStation`).  RLU-C4's three conditions are named in all eight, with `{1}` still the editor's own label.  (What the sentence still leaves out is RLU2-C10.)

---

Counts: no A, no B, 13 C, 9 D.
