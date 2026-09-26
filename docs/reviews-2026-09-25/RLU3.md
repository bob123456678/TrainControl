# RLU3 - validation, round 3: the windows (every screen, dialog and menu) and the message bundles (TrainControl 3.0.0 release readiness)

**Status:** open

**Prefix:** RLU3

**Reviewed:** branch `autonomy-diagram-r0` at `d779578b` (working tree clean by `git --no-optional-locks status`), round 2's claims `5322fb65` and `1d9c8c66`, its fix `cc56a7a8` and its records `d779578b`, on 2026-09-25.

## Method

Read-only: nothing compiled or run, no JVM, no git state changed, nothing under `cs2_sample_layout/` opened, and this file is the only one written.  I read the brief, `docs/reviews/README.md`, my lane's round-2 document `docs/reviews-2026-09-25/RLU2.md` whole, `RLA2.md` and `RLD2.md` whole, and round 1's `RLU.md` whole and `RLA.md`'s RLA-C5.  Then every commit of `git log 98b300d6..HEAD` with `git show`: `cc56a7a8` in full for `gui/`, `model/ViewListener.java`, `automation/Layout.java`, `automationui/AutonomySession.java`, `marklin/`, the eight bundles, `behaviour.md`, `Automation.md` and `Readme.md`; `5322fb65` for `testMessageBundles`, `testJavadocsAreAttached`, `testTheRoutesImportDoorAsksByName`, `testTheTailIsPickedOnTheDiagram`, `testWhereHisTrainsMayBeSent` and `testTheImportDoorReadsAnOldFile`; `1d9c8c66` by its stat and message (MM2 addresses in two autonomy-lane claims); `d779578b`'s changes to `issues.md`, `tests.md`, `behaviour.md` and the test.  Around each change I read the door and its siblings at HEAD: `AutonomyViewerPanel.importConfiguration`, `activateTheConfigurationNamed`, `importLegacyGraph`, `loadAfterImport`, `load` (both), `revert`, `save`, `exportConfiguration`; `AutonomyMenu`'s Configuration submenu and its three calls into Import; `AutonomyCompanionStore.importBundle`, `importConfiguration`, `fileNameTaken`; `AutonomySession.configurationToLoadAfterImport`, `importLegacy`'s carried-settings loop, `captureFromLayout`'s merge and `POINT_OPERATIONAL_KEYS`, `hasNoMaximumTrainLength`, `assignMaxTrainLength`, `stationsWithoutMaxLength`, `clearEveryMaxTrainLength`, `setPointProperty`/`writePointProperty`; `AutonomyEditorPanel.promptNumber` and the station menu's maximum item; `Point.toJSON`; `AutonomyChecks`' `TERMINUS_WITH_TWO_WAYS_IN` and `NO_MAX_TRAIN_LENGTH`; `Layout.whyItReachesNoStation`, `explainCannotStart`, `whyTheStartIsRefused`, `whyNoTrainIsStartedFrom`, `isABarredCopyOfAStation`, `turnsEveryTrainAt`, `startableTwinOf`; `LayoutEditorRightclickMenu` whole and its one construction in `LayoutEditor`; every `JOptionPane` and `AutonomyReport.show` in `LayoutRightclickAutonomyMenu`, `AutonomyViewerPanel` and `LayoutEditor`, and in all eight `JPopupMenu` subclasses under `gui/`; the tail question's three doors (`TrainControlUI.rememberPlacement`, `LayoutRightclickAutonomyMenu`, `GraphLocAssign`) and the Control+X door into `rememberPlacement`; `TrainControlUI.openLayoutEditorWindow`/`isLayoutEditorOpen`; `TailCrossedPrompt`'s owner comment.  Tests read: the round-2 claims and pins named above; `testWhyStuck`'s three either-way tests and their fixture builder; the old every-key test's javadoc; the census and the ratchet whole.  **Mechanical checks, all in memory** (Python reading `git show` output through a pipe; nothing written): the javadoc ratchet re-implemented from its own rule and run at `1d9c8c66` and HEAD, total and per file against `ORPHANS_BY_FILE`; the monitor census re-implemented (its declaration pattern, comment stripper, four-space member rule, `REACHES_THE_MONITOR` and allowances) over `gui/` and `automationui/` at `1d9c8c66` and HEAD, with the every-allowance-is-still-a-door half; the eight bundles at `1d9c8c66` and HEAD (key sets, duplicates, placeholder sets, bytes above 127, empty values, straight apostrophes in values with a placeholder, the keys that changed), and the three changed keys and each bundle's neighbouring "home" keys decoded and read in all eight languages.  `docs/manual-tests/findings.tsv` was searched before each finding: the prefix RLU3 is free; GUI4-C3 (closed) is cited by RLU3-C1 as the ruling a fix departs from, not repeated; nothing else here repeats a catalogued row.  Not reported, as the brief says: RLU2-C9 and RLD-C1.  **Everything below is from reading**; each finding that needs a run says so, with the fixture and what proves or refutes it.

There is no A and no B.  Six C: two about the Why not Moving? sentence round 2 reworded (RLU3-C1, RLU3-C2), one pin that stops short of its finding (RLU3-C3), two dispositions whose stated reasons do not hold as written (RLU3-C4, RLU3-C5), and records (RLU3-C6).  Nothing new above C.

---

### RLU3-C1 - RLU2-C10's new remedy tells the operator to open a closed side even at a station every train turns round at - the remedy Adam ruled out at GUI4-C3, which raises the blocking terminus error

| | |
|---|---|
| **Disposition** | Fixed - the either-way sentence no longer names a side to open.  claims cca48e96 (red first), fix 81a9fffa. |
| **Grade** | C - a refusal's remedy that, followed, makes autonomy refuse the whole setup until it is undone.  Needs execution. |
| **Names** | RLU2-C10 (its fix, `cc56a7a8`); GUI4-C3 (the ruling it departs from) |
| **Where** | `autolayout.why.startReachesNoStationEitherWay` (`messages.properties:1797` and the seven translations); `Layout.java:5052-5067`; `Layout.java:5100-5104` (GUI4-C3's branch for the train's own square); `AutonomyChecks.java:1236` (`TERMINUS_WITH_TWO_WAYS_IN`, an ERROR); `Automation.md:311`; `behaviour.md:705` |

`cc56a7a8` ended the either-way sentence, in all eight languages and both documents, with "switch it on, tick "{1}", and open the side a train would arrive on under "{2}"" - `{2}` being Trains May Arrive....  The clause is unconditional: `whyItReachesNoStation` does not know which station the operator is to fix, so it cannot ask what kind it is.  RLU2-C10's own fixture was a terminus.  At a station every train turns round at (`turnsEveryTrainAt`) that track reaches from a second side, the closed side is what keeps the setup valid: `checkTerminusTwoWaysIn` counts only sides not barred ("Barred arrivals do not count ... Shutting one side with the arrows is one of the three ways to fix this"), and its own message tells him to close one.  Opening it, as Why not Moving? says, gives that ERROR, and autonomy refuses the whole setup until the side is closed again - by a message that then tells him the opposite of the one he followed.

That is GUI4-C3's shape: Adam, 2026-09-24, *"offer the other two remedies only"* where every train turns, which `whyNoTrainIsStartedFrom` honours for the square the train stands on (`startFacingBarredMustTurn`, :5102).  The either-way sentence, a sibling of that one, now offers the ruled-out remedy.  Nothing on the railway moves; the setup stops loading until he undoes it.  **Direction:** name the side only where the station beyond it is not one every train turns at - which needs `whyItReachesNoStation` to find the station a train reaches only across a closed side (RLU2-C10's second option) - or put GUI4-C3's exception into the words.

**Verification request.**  A hand-built layout in `testWhyStuck`'s manner: a siding whose only way out runs into the back of a station T at which every train must turn round (a terminus), T also reached from its other side by a line with a station on it, T's siding side closed under Trains May Arrive; a train on the siding.  **Proves:** Why not Moving? gives `startReachesNoStationEitherWay`; opening T's closed side, as it says, makes `AutonomyChecks` report `autosetup.ui.checkTerminusTwoWaysIn` at ERROR and the configuration refuse to load.  **Refutes:** another sentence is chosen, or the setup has no such error once the side is open.

---

### RLU3-C2 - Since RLA-C5 the either-way sentence is also what a train gets where the other way does reach a station, and there it is untrue: the barred copy, pinned by WS12, is told "whichever way a train faces" and given remedies that are already true

| | |
|---|---|
| **Disposition** | Fixed - where the other way reaches a station but autonomy starts no train there, the sentence says so: switched off, or barred with "open that side and turn it round" where not every train turns there (GUI4-C3).  claims cca48e96 (red first), fix 81a9fffa; mutation U5 red. |
| **Grade** | C - a refusal whose statement is false and whose remedy does not reach the cause; no railway effect.  No execution needed: the committed claim asserts it. |
| **Names** | RLA-C5 (the fall-through, `3af66907`), RLA2-C6 (its pin, `5322fb65`), RLU2-C10 |
| **Where** | `Layout.java:5056-5067`; `test/core/testWhyStuck.java:761-777` (`testTurningRoundIsNotOfferedOntoACopyThatIsNoStation`) and its builder `:792-821`; `Automation.md:311` |

`whyItReachesNoStation` offers "turn it round" only onto another copy that passes `whyNoTrainIsStartedFrom` and reaches a station, and otherwise falls to the either-way sentence - RLA-C5's own direction.  The fall-through now holds two different cases: neither copy reaches a station (the sentence is true), and the other copy reaches one but a train may not be started on it - barred, switched off, or no station.  In the second, "From {0} no station autonomy may choose can be reached, whichever way a train faces" is false.

The pin RLA2-C6 asked for builds exactly that and asserts the false sentence: `platformWithASiding("WS12", true, false)` runs the westbound copy to WS12 Far, a station, and makes that copy the one trains may not arrive at; a train on the eastbound copy is told no station can be reached whichever way it faces.  The remedies that follow - switch the station on, tick Can Be Chosen in Full Autonomy, open the side a train would arrive on - are all already true of WS12 Far.  What would let autonomy start the train is what `startFacingBarred` says of that very copy: open that side of WS12 Platform under Trains May Arrive (where not every train turns there - GUI4-C3) and turn it round.  The operator is told his railway has no way out and sent to settings that are already right.  `Automation.md:311`'s "otherwise ..." says the same.  **Direction:** a sentence of its own for "facing the other way a station can be reached, but a train may not be started facing that way here", carrying the other copy's own reason, which the loop has just computed (`whyNoTrainIsStartedFrom(other)`); and the WS12 pin asserting it.

---

### RLU3-C3 - RLU2-C5 is pinned at ten of its eleven messages: the setup's tidy report in `AutonomyViewerPanel.save` - IND9X-C4's site - has no pin, and `this` put back there stays green

| | |
|---|---|
| **Disposition** | Fixed - the setup's tidy report is pinned with the menus (cca48e96). |
| **Grade** | C - a test that would not catch its own regression, at the one site of the eleven on an everyday path.  Needs execution. |
| **Names** | RLU2-C5, RLU-B2, IND9X-C4 |
| **Where** | `AutonomyViewerPanel.java:1544`; `test/ui/testWhereHisTrainsMayBeSent.java:202-233`; `AutonomyReport.java:68` |

RLU2-C5 counted eleven messages `693dd0a1` re-parented and asked for "a source pin - no `JOptionPane.show...(this` in `LayoutRightclickAutonomyMenu.java`, and `AutonomyReport.show(ui,` in `AutonomyViewerPanel.save`".  The pin `5322fb65` added reads `JOptionPane.show` in the two right-click menus, and the disposition says so ("every message on both right-click menus").  `save()`'s `AutonomyReport.show(ui, session().save())` is in neither file and is not a `JOptionPane` call.  `AutonomyReport.show` takes a `Component`, so `this` - the panel "built but not shown", which is exactly what `693dd0a1` replaced there - compiles; and nothing in `test/` names the site (searched for `AutonomyReport.show`, `IND9X-C4` and the file's path: the two tests that read `AutonomyViewerPanel.java` pin other lines).  `save()` runs on every configuration load (RLU-B2's second point), so this is the re-parented message an operator meets most often, and the one that can go back unseen.  **Fix:** add `AutonomyViewerPanel.java` to the pin, reading `AutonomyReport.show(` as well as `JOptionPane.show`.

**Verification request.**  Put `AutonomyReport.show(this, session().save())` back at `AutonomyViewerPanel.java:1544` and run `ui.testWhereHisTrainsMayBeSent`.  **Proves:** green.  **Refutes:** red, naming the line.

---

### RLU3-C4 - RLU2-C11's "not a defect" rests on a 0 nobody types: by hand "no limit" is stored as no key, only the capture writes 0, and every other reader of a maximum calls a 0 none - the import alone calls it set

| | |
|---|---|
| **Disposition** | Open - Adam's decision: a gap-fill cannot tell a setting returned to its default from one never set - every editor door stores a default as nothing, and a capture writes a station maximum of 0 - so a second import of an old file fills both.  Put to him in the report and open-questions.md: keep the gap-fill (and say so in its question), or import an old file only into a configuration of its own, or remember what the first import wrote. |
| **Grade** | C - a question that promises what the door does not bring, and the setup's own notice then says the opposite; for Adam.  Needs execution. |
| **Names** | RLU2-C11, RLA2-C4 |
| **Where** | `AutonomySession.java:1167` (the gap test); `AutonomyEditorPanel.java:1542-1543` and `:4287` (`value == unset ? null : value`, unset 0); `AutonomySession.java:8363-8370` (`clearEveryMaxTrainLength` removes the key); `Point.java:1196-1198` and the capture (`POINT_OPERATIONAL_KEYS` `:3230`, merged `:5469-5473`); `AutonomySession.java:3787-3818` (`assignMaxTrainLength`, `hasNoMaximumTrainLength`), `:9721` (`stationsWithoutMaxLength`, feeding `NO_MAX_TRAIN_LENGTH`); `behaviour.md:2445` |

The disposition: "a 0 is how a configuration states 'no limit', by hand or by a capture; the gap-fill cannot tell the two apart and keeps both"; and behaviour.md now says "A station maximum of 0 is a statement - 'no limit' - and is kept".  By hand it is not stated as 0.  The editor's prompt writes no key for 0 (`value == unset ? null : value`, the maximum's unset being 0), Clear All removes the key, and `assignMaxTrainLength`'s javadoc says the store writes 0 "as no setting at all".  The one writer of a 0 is the capture, which copies `Point.toJSON`'s `maxTrainLength` for every station whether anyone set one or not.  So what decides whether a file's maximum arrives is not anything the operator stated but whether the configuration has been captured since it was last edited - in practice, whether it has ever run (and the one running is now refused, RLA2-B1).  A configuration made with New, run once, and then gap-filled from his old file gets its placements, homes and settings and not one station maximum.

And the rest of the program reads that 0 the other way.  The editor shows it as "any"; `hasNoMaximumTrainLength` counts it as none; so the setup check says "{0} has no maximum train length, so no train is ever too long for it" (`NO_MAX_TRAIN_LENGTH`), and Mass Assign Station Max Train Lengths fills it "if it has none yet".  After "Add to it what the file has and {0} does not?  Nothing already set in {0} is changed." and Yes, the file's maxima are not there, the message does not say so (it counts only what it carried), and the setup's own notice lists those stations as having none.  On the railway the configuration is no worse than before the import, but the protection the operator imported to get is silently absent.  RLA2-C4 ("Whether a 0 blocks a file's value is Adam's call") and RLU2-C11 ("For Adam") both put this to him; the fix decided it, and nothing in open-questions.md or the Inbox carries it to him.  **For Adam:** keep it - and then behaviour.md should say a captured 0 blocks the file, and the question should say maxima are not added where a configuration has run - or read a configuration's 0 as the rest of the program does, as a gap, which also fills a maximum cleared by hand once that configuration has run: the trade RLU2-C11 named.

**Verification request.**  Live-snapshot sandbox with his configuration X running, as `testTheImportDoorReadsAnOldFile` opens it.  Make a configuration Y with New; choose Y; choose X again (the load captures Y).  Read Y's points: every station has `maxTrainLength` 0.  Import the MT-491 file into Y by name, Yes; choose Y.  **Proves:** every station the file gives a maximum above 0 still has 0 in Y, and Y's findings include `NO_MAX_TRAIN_LENGTH` for them.  **Refutes:** the file's maxima are in Y.  **Control:** the same file into a new configuration brings them (MT-491's claim).

---

### RLU3-C5 - RLU2-C13's disposition answers a save when the late answer comes back, which RLU2-C13 did not propose; its direction - save before the question, when the door has just refused any open editor - is not addressed

| | |
|---|---|
| **Disposition** | Follow-up - filed as OB-305. |
| **Grade** | C - narrow, as RLU2-C13: the sequence and then a crash before anything else saves.  Needs execution. |
| **Names** | RLU2-C13, RLU-C9 |
| **Where** | `TrainControlUI.java:8117-8265` (`rememberPlacement`: placement, facing and side written before the question, one save at `:8255-8260`), reached from Control+X, which saves the emptied square at once; `LayoutRightclickAutonomyMenu.java:1281-1358` |

The disposition: "saving it then would commit the editor's unsaved work with it.  Only a crash before any save loses it, as for any edit held in memory."  Both halves are about a save made when the answer comes back with an editor open.  RLU2-C13's direction was the other end: "save what the door wrote before its question before asking it - no editor can be open then, since every main-window door refuses with one open - and keep 'save nothing' for the late answer alone."  At that moment there is no editor and no editor work to commit (RLU-D1: the tile menu, the keys and the right-click all refuse with an editor open), so the reason given does not reach the proposal.  Nor is it "any edit held in memory": each of these doors otherwise saves before it returns, and Control+X, which this sequence starts with, wrote the square empty to setup.json at once - so this is the one path on which setup.json holds a standing train on no square for as long as nothing else saves.  The consequence stays RLU2-C13's (a crash in that window restarts with the train missing; the occupied sensor still guards its platform), hence C.  **Direction:** save before the question, as proposed; or record the risk as accepted against that proposal, not against a save at the answer.

**Verification request.**  RLU2-C13's own: `testTheTailIsPickedOnTheDiagram`'s fixture, Control+X 75 407 DB from BottomMainB, Control+V at Tunnel from the north so the question waits; open the autonomy editor; click TunnelPre; close the editor with nothing changed; read setup.json from the sandbox before anything else saves.  **Proves:** its configuration has 75 407 DB on no square.  **Refutes:** it has it on Tunnel.

---

### RLU3-C6 - Two records round 2 left: the ratchet's history has no line for 87 -> 86, and the old every-key test still promises to catch an emptied key it cannot see

| | |
|---|---|
| **Disposition** | Fixed - the ratchet's dated line, and the old every-key test's javadoc (cca48e96). |
| **Grade** | C - records.  No execution needed. |
| **Names** | RLU2-C1, RLU2-C3 |
| **Where** | `test/regression/testJavadocsAreAttached.java:44-58`; `test/core/testMessageBundles.java:906-907` |

- **The ratchet.**  Every lowering of `ALLOWED` has a dated line naming the finding that earned it ("Lowered so the improvement cannot be given back"), down to "88 -> 87 on 2026-09-24: TDY4-C1".  `5322fb65` set 86 with no line for it; RLU2-C1's fix asked for "a dated line in the header like the others".  One line: 87 -> 86 on 2026-09-25, RLU-C6, the stray "Debug builds only" javadoc above `activateTheConfigurationNamed`, deleted by `2fa033f3`.
- **The old every-key test.**  `testEveryKeyAScreenAsksForIsInEveryLanguageWithAValue`'s javadoc still says "give any asked-for key an empty value in one language, and this fails naming it".  It still cannot see the ~340 keys RLU2-C3 counted; `testEveryKeyHasAValueInEveryLanguage` (`:1028`) is what now catches them.  One clause saying so.

---

### RLU3-D1 - RLU2-C1 and RLU2-C2: both battery classes, re-run by their own rules in memory, are green at HEAD, and the census was red at the claims commit on exactly the call the fix removed

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |
| **Where** | `test/regression/testJavadocsAreAttached.java:58`, `:93`; `test/regression/testNothingOnTheEventThreadTakesTheRailwaysMonitor.java`; `cc56a7a8` |

The ratchet's rule (a `/**` block followed by nothing but whitespace before the next) counts 86 at `1d9c8c66` and 86 at HEAD, in 21 files, each file's count equal to its `ORPHANS_BY_FILE` entry (`AutonomyViewerPanel.java` 2).  `cc56a7a8`'s new javadocs (the menu's `owner` field, `whyNoTrainIsStartedFrom`, `homesKept`) and the removed second `loadAfterImport` orphan nothing.  The census re-implemented: at `1d9c8c66` one unlisted door, `AutonomyViewerPanel.java#importLegacyGraph`; at HEAD none, and every allowance still names a call it covers.  `cc56a7a8` adds no call from `gui/` or `automationui/` onto a synchronized `Layout` member; the sync's new `synchronized (layout)` block is in `marklin/`, outside the surfaces.

---

### RLU3-D2 - RLU2-C3: the new every-key test asks the bundle, not the callers, and would name an emptied key

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |
| **Where** | `test/core/testMessageBundles.java:1014-1054` |

`testEveryKeyHasAValueInEveryLanguage` takes every key of `messages.properties` and reports a value that trims to nothing in each of the eight files `bundles()` lists, English included, exempting names ending "Suffix" - there is one, `stats.ui.valuePluralSuffix`, whose Italian and Polish values are the only empty ones today.  A key missing from a translation is left to `testTranslationsMatchEnglishKeySet`, as before.  RLU2-C3's verification (Danish `confirmImportFillsGaps` blanked) now fails this method, naming it.  (The old method's javadoc is RLU3-C6.)

---

### RLU3-D3 - RLU2-C4: the pin reads each door's own gate

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |
| **Where** | `test/regression/testTheTailIsPickedOnTheDiagram.java:1193-1199`; `LayoutRightclickAutonomyMenu.java:1321-1322`; `GraphLocAssign.java:325-326` |

For each of the two doors the pin takes the first `setupStands =` after `askAfterPlacement(` up to its semicolon and requires `anEditorOpenedInTheWait()` in it.  The declarations `boolean setupStands = true;` sit before the question (`:1281`, `:290`), so they are not what it reads; what it reads is the assignment after the answer (`:1321`, `:325`).  RLU2-C4's mutation - the clause deleted at `LayoutRightclickAutonomyMenu.java:1322` - leaves the assignment without it and fails naming the file.  The paste door stays held by its claim.

---

### RLU3-D4 - RLU2-C6 (and RLD2-C12): every message of the editor's right-click belongs to the editor, and no popup under `gui/` owns one

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |
| **Where** | `LayoutEditorRightclickMenu.java:69-549`; `LayoutEditor.java:2203`; `test/ui/testWhereHisTrainsMayBeSent.java:202-233` |

All fourteen catches now pass `edit`, or at `:549`, in `addShift`, where the constructor's argument is out of scope, the new `owner` field holding the same object.  The menu's one construction passes the editor itself (`LayoutEditor.java:2203`, a `PositionAwareJFrame`), so each message is owned by the window that takes the main window's Always on Top.  Of the eight `JPopupMenu` subclasses under `gui/`, none has a `JOptionPane` owned by the menu or by null.  The editor's own messages - its keyboard doors for the same actions - are owned by the frame, so the right-click and the keys now agree.  The pin reads both menus' 25 calls, a call broken after its bracket included, and would name a `this` put back at any of them.

---

### RLU3-D5 - RLU2-C7: the claim reads the question on screen

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |
| **Where** | `test/regression/testTheRoutesImportDoorAsksByName.java:154-164`; `ViewListener.java:159-161` |

The claim cuts the list out of `run.question` (whitespace folded, so the wrapping cannot move it; the template's own words either side of `{0}` taken off), asserts the no-sensor route is not among its entries, and asserts the entries are the model's list in order.  A door that built its list from the file's `auto` flags names the no-sensor route and fails the first; a door that dropped a route the model arms fails the second.  `ViewListener.routesSavedArmed`'s javadoc now says the list is the routes saved armed with a sensor, the ones Yes arms.

---

### RLU3-D6 - RLU2-C8: the records now say what `b5226b46` does

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |
| **Where** | `docs/reference/behaviour.md:1143-1145`; `TailCrossedPrompt.java:212-217`; `TrainControlUI.java:6797-6811` |

behaviour.md and the comment at the call both say the list opens from the editor while it is showing and from the main window while it is minimised, which is `openLayoutEditorWindow` (displayable, showing, not iconified) and its javadoc.  `testThePlaceDoorsKeepTheHeading`'s RLA-C4 claim now says it pins the owner rule rather than a door any operator reaches (RLA2-C5).

---

### RLU3-D7 - Import refuses the configuration in use at the one door all three menu entries share, before it reads the file, over the main window, with the way on in all eight languages - and its claim is real

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |
| **Where** | `AutonomyViewerPanel.java:999-1026`; `AutonomyMenu.java:267`, `:286`, `:341`; `AutonomyViewerPanel.java:423`; `AutonomyCompanionStore.java:2093-2119`, `:2566-2568`, `:5349-5361`; `test/regression/testTheImportDoorReadsAnOldFile.java` (`testAnImportIntoTheConfigurationInUseIsRefused`) |

`importConfiguration` is the only import door: the Autonomy menu's two entries and the panel's own button call it.  The refusal compares the name typed with `getActiveDiagramConfiguration()` - the name the Configuration submenu is headed with (`AutonomyMenu.java:267`) - after the name prompt and before the file is read, its kind decided or anything created, so neither branch can reach the running configuration by name, and the round-1 capture has gone with its `loadAfterImport(name, boolean)` overload; the only caller left passing `false` to `load`'s capture is the rebuild after a setup change (`TrainControlUI.java:6693`).  The message is owned by `ui`.  Its `{1}` is `menuAutonomy` + " > " + `menuConfigurations(inUse)`, the heading of the submenu Import sits in and where an imported configuration appears, so "choose it under" names a real place; the key is in all eight bundles with `{0}` and `{1}`, saying the same thing in each bundle's own register.  A name differing from the running one only in case falls to GSP-B1's `errorNameInUse`: `fileNameTaken` compares `File`s, which Windows compares without case, and `importBundle` asks the store's `importConfiguration` before it merges anything.  The claim compares the configuration, the list of names and setup.json on disk before and after, and the message; with the refusal taken out, the old-file branch writes into the configuration and the first assertion fails.  RLA2-C3's homes line joins the same dialog after the placements line.

---

### RLU3-D8 - The eight bundles at HEAD: 1,838 keys each, round 2's three keys in all eight with English's placeholders, and the dropped clause gone everywhere

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |
| **Where** | `src/org/traincontrol/resources/messages*.properties` at `1d9c8c66` and HEAD; `Layout.java:5066-5067`; `test/core/testWhyStuck.java:706`, `:736`, `:773` |

Read in memory: all eight hold the same 1,838 keys (1,836 plus `infoLegacyHomesKept` and `errorImportIntoConfigurationInUse`), no duplicates, every key's placeholder set equal to English's, no byte above 127, no straight apostrophe in a value with a placeholder, and the only empty values Italian's and Polish's plural suffix.  Between the two commits exactly three keys changed, in every language: the two new ones and `startReachesNoStationEitherWay`.  That sentence carries `{2}` in all eight; its one caller passes `menuArrivalsGroup`, and all three tests that build it pass it too, so none compares against a literal "{2}".  "The side a train would arrive on" is rendered as the side of entry in each (on, by, from, through).  RLA2-C7's clause about stations where trains turn round is gone from all eight, from `Automation.md:311` and from `behaviour.md:705`.  `infoLegacyHomesKept` says the same in each; its word for a home is its bundle's own in seven (German "Heimat", Danish "hjemsted", Spanish "base", Italian "deposito", Dutch "thuis", Polish "baza"), while French says "Garages" where its neighbours say "dépôt", "base" or "attitrée" - the variation RLU-D3 already recorded as not a finding.

---

### RLU3-D9 - RLU2-C12's follow-up is filed as its disposition says, and waiting is justified

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |
| **Where** | `docs/manual-tests/issues.md:1316-1324` (OB-302) |

OB-302 states the defect, why it waits and the direction (take the snapshot after the exclusion, which is a decision about the track rather than the file).  The exclusion is one the next import makes again and announces again, so nothing is lost in the meantime.

---

Counts: no A, no B, 6 C, 9 D.
