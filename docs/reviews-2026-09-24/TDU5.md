# User interface - validation round 5 of the fixes `7c38b51a..1d22c83f`

**Status:** open

**Prefix:** `TDU5`

**Reviewed:** branch `autonomy-diagram-r0` at `1d22c83f` (HEAD), 2026-09-24.  The previous round's document `docs/reviews-2026-09-24/TDU4.md` and its dispositions, and the four commits `114f1600`, `665569f3`, `6c7d5373` and `1d22c83f`.  Lane: their `src/org/traincontrol/gui` and message-bundle halves, the tests that claim them, and the behaviour.md and tests.md text about the lane.

**Method:** I read `docs/reviews/FANOUT.md`, `docs/reviews/README.md` and `TDU4.md` first, then `git log --stat` of the range and `git show` of every lane part of the four commits: the three placement doors and `TailCrossedPrompt` in `6c7d5373`, the eight bundles, the new claims and pins in `665569f3`, the three UI claims `114f1600` touched, and the MT-576 comment, MT-579 and the catalogue rows in `1d22c83f`.  Then the code at HEAD each fix depends on: `TailCrossedPrompt` (`sameSetup`, `runningNow`, `whereTheAnswerGoes`, `writeRoad`, `noteADroppedAnswer`, `Answer`, `DiagramPick`); `TrainControlUI.rememberPlacement` and its caller, `getAutonomySession`, `resetAutonomySession`, `captureRunningLayout`, `mountAutonomyControls`, `unloadAutonomy`, `autonomySetupDeleted`, `autonomySetupRenamed`, `autonomyMenuActed`/`autonomySetupChanged`, `updateVisiblePoints`, `repaintAutoLocList`, `isAutonomyBusy`; `GraphLocAssign.commitAndRecord` and both its callers; `LayoutRightclickAutonomyMenu.placeFacing`; `AutonomyViewerPanel.load`/`duplicate`/`rename`/`save`/`refresh`; `AutonomyMenu.manageMenu`; `AutonomyCompanionStore` (`setActiveConfiguration`, `createConfiguration`, `renameConfiguration`, `save`); `MarklinControlStation.getAutoLayout`/`hasAutoLayout`/`clearAutoLayout`/`log`; the editor panel's own tail pick (`armTailPick`, `recordTailRoad`) and the session it is built with.  For each claim I asked whether it was red for the finding's own reason and whether its mutation reaches its assertion; the round's mutation specs and results were read from the session's scratch record (`make_mutr4.py`, `mutr4.out`), which lists R4a to R4i each red on the class named.  The bundles were checked with read-only `grep`/`tr`/`wc` pipelines: key counts, non-ASCII bytes, placeholders, apostrophes, and the menu labels the notices quote.  Nothing was run, compiled or started, and no file was written but this one.  One recursive `grep -rln` for the late-answer wording, run from the repository root, searched `cs2_sample_layout/` with everything else before its output was filtered; nothing from that folder was printed, read or used.  Outside the lane, and not read: `git status` shows `cs2_sample_layout/config/autonomy/configuration-Main.json` and `setup.json` modified in the working tree, and the round-4 mutation record's restore line lists the same two files - worth knowing who wrote them before the next battery.  Every finding that depends on behaviour says what execution would settle it.

## A - high

None found.

## B - medium

None found.

## C - low

### TDU5-C1 - renaming the loaded configuration, or making a new one from it, while the tail question waits drops the answer from the railway as well as the setup - round 3 wrote it - and the log gives "another configuration was loaded" as the reason

| | |
|---|---|
| **Disposition** | Fixed - claim testARenameInTheWaitKeepsTheAnswer in 9fd15b6f (red first), fix 0cf64c18: the same railway running is the same setup under any name - a rename or New Configuration keeps the answer.  Mutation R5b red. |
| **Where** | `TailCrossedPrompt.java:615-620` (`sameSetup` compares the store's active configuration BY NAME); `AutonomyCompanionStore.java:2601-2622` (`renameConfiguration` moves `activeConfiguration` to the new name; the configuration object is the same); `AutonomyViewerPanel.java:1313-1344` (`duplicate`: `createConfiguration`, `setActiveConfiguration(copy)`, `save`, no load) and `:1346-1378` (`rename`); `AutonomyMenu.java:575-598` (Manage > New configuration, Rename, enabled while a configuration is loaded); `TrainControlUI.java:3864-3869` (`autonomyMenuActed`: refresh and repaint, no rebuild); the doors `TrainControlUI.java:8175`/`8184-8189`, `GraphLocAssign.java:316`/`326-329`, `LayoutRightclickAutonomyMenu.java:1238`/`1249-1255`; commit `6c7d5373` |

The TDU4-C1 fix treats a change in the NAME of the store's active configuration as "not the same setup".  Two menu actions change that name and leave the railway alone:

- **Rename** of the loaded configuration.  `renameConfiguration` moves `activeConfiguration` from the old name to the new, keeping the same configuration object.
- **Manage > New configuration** (`duplicate`).  It copies the chosen configuration and makes the copy active without loading it.  The running railway is still the original's build, the same Point objects.

Neither rebuilds the railway: `autonomyMenuActed` refreshes the tab and repaints the diagram, and the waiting question survives the repaint because it finds its labels by square.  So, answered after either, the copy the question was asked about is the running copy.  It still holds the train from the same side with the same road, and the answer is true of it.  But `sameSetup` is false, `whereTheAnswerGoes` returns null, and the door writes neither the railway nor the setup.  It logs the drop and skips its save; the viewer's own `save()` has already written the setup.

Round 3's check was the session alone, and it wrote the answer in both cases: to the running copy, and to the store's active configuration (the renamed one, or the copy that is now active).

The log line then reads "... Tunnel changed while the question waited - the train was moved, another train stands there, or another configuration was loaded".  For Rename none of the three happened.  For New configuration the third is near enough.

A related note on the comments.  The three save-skip comments (`TrainControlUI.java:8212-8213`, `GraphLocAssign.java:352`, `LayoutRightclickAutonomyMenu.java:1275`, and `sameSetup`'s javadoc) justify the skip by "the reset that replaced it wrote what it held".  In the configuration cases there is no reset.  `load()`, `rename()` and `duplicate()` each call the viewer's `save()`, so the skip loses nothing, but the comments explain only half of what `setupStands` covers.

**On the railway.**  The standing train's tail is not recorded.  The tail walk stops at the switch, and another train can be routed into the tail: TDU2-A1's consequence, in two cases the round-3 code handled.

What mitigates it:
- It needs Rename or New configuration from the Autonomy menu while the question waits.
- The log says the answer was not recorded, though it gives the wrong reason.
- The grey visibly stops at the switch.
- "Where the tail lies" on the right-click menu sets the road again.

**Verification request.**
- **Fixture:** `regression.testTheTailIsPickedOnTheDiagram.pasteAndAnswerLate`.  In the wait, on the event thread, `ui.getAutonomySession().getStore().renameConfiguration(was, was + " TDU5")` then `ui.autonomySetupRenamed(was, was + " TDU5")`: what `rename()` does, without its input dialog.
- **Precondition:** `model.getAutoLayout().getPoint("Tunnel (southbound)")` is the same object as before the wait and holds the train, from N, with no road.  Then click TunnelPre, and rename back in `finally`.
- **Proves it:** `late.tunnelNow.getArrivedAlong()` is null and the `logTailAnswerDropped` line is logged.
- **Refutes it:** the copy has TunnelPre's road.
- **The New configuration case:** the same, with `createConfiguration(copy, was)` and `setActiveConfiguration(copy)` in the wait.

**Suggested fix.**  Ask whether the configuration is the same object, not the same name: `store.getConfiguration(active)` read before the question and again at the answer.  A rename keeps the object; loading another configuration changes it.  Or skip the configuration question when the railway was not rebuilt at all (`running.getPoint(name) == asked`).  Whether the setup half should follow New configuration's copy is Adam's call; the railway half should stand either way.

### TDU5-C2 - behaviour.md 5c still states round 3's rule - "a railway rebuilt in the wait counts" - and says nothing about the setup the question was asked for

| | |
|---|---|
| **Disposition** | Fixed - 0cf64c18: behaviour.md 5c states the setup condition and what keeps and drops an answer. |
| **Where** | `docs/reference/behaviour.md:1107-1111`; `TailCrossedPrompt.sameSetup`; `TrainControlUI.java:23339` (the track-diagram editor's close resets the session); commit `6c7d5373`, which edited behaviour.md for TDA4 only |

5c says an answer is written where *"the square's copy on the railway running then still holds the train, with the side it was put down with and the road it had - a railway rebuilt in the wait counts, its new copy being the one asked.  Otherwise the answer is dropped and the log says so"*.

The code now has a condition the document does not state: the answer counts only while the setup is still the one the question was asked for, meaning the same session with the same configuration active.  Two ordinary ways of rebuilding the railway in the wait now drop the answer, and 5c's sentence reads as covering both:
- **Loading another configuration** (TDU4-C1).
- **Closing the track-diagram editor**, which resets the session and reloads it (TDU4-C2's path).

Two more things are left unsaid:
- The door does not save a setup the window has let go.
- A right-click setting change, which is MT-576's step 2, is the rebuild that still counts.

**On the railway.**  None; documentation.  But behaviour.md is where Adam reads the intent.  An operator who closes the editor while the question waits sees the answer dropped, which 5c describes as a defect.

Suggested fix: one clause, e.g. *"...its new copy being the one asked, as long as the setup is still the one the question was asked for - loading another configuration, or closing the track-diagram editor, in the wait drops it, and nothing is saved for a setup the window has let go (TDU4-C1, TDU4-C2)."*  Take TDU5-C1's outcome into account first.

### TDU5-C3 - the dropped-answer line: its javadoc says Not known is not logged, and the code logs it; and its list of causes leaves out the setup being replaced, the case TDU4-C2 is about

| | |
|---|---|
| **Disposition** | Fixed - 0cf64c18: the javadoc says Not known is logged; the reasons include a train turned and a setup reloaded, in eight languages; a comment on MT-576 quotes the line as it now reads. |
| **Where** | `TailCrossedPrompt.java:622-635` (the javadoc) against `:303-307` (`wasAnswered`: "a sensor, or Not Known"), `:212`, `:409-410` and `:820` (Not known is an answered reply with no choice, in both the list and the diagram pick); the guards `TrainControlUI.java:8204`, `GraphLocAssign.java:339`, `LayoutRightclickAutonomyMenu.java:1267`; `messages.properties` `autolayout.ui.logTailAnswerDropped` and its seven translations; `testOnlyADroppedAnswerIsLogged` (a Cancel and a sensor click only); commit `6c7d5373` |

**Not known.**  The new javadoc says *"Only for an answer (TDU4-C3) - a Cancel or Not known asked for nothing to be recorded"*.  The guard is `answer.wasAnswered()`, which is true for Not known in both the list and the diagram pick.  So a Not known given after the train was moved is logged as "was not recorded".

The code follows TDU4-C3's suggested fix (`Reply.answered`), and the javadoc follows its complaint.  The code's position is the consistent one: `Answer`'s javadoc and `roadToRecord` treat Not known as an answer that forgets a road.  So it is the comment that the change made false.  Nothing tests Not known either way.

**The causes.**  The line reads *"... {1} changed while the question waited - the train was moved, another train stands there, or another configuration was loaded"*.  The answer is also dropped in cases that list does not name:
- **The setup was replaced**, by closing the track-diagram editor, Unload or a re-download.  This is the case TDU4-C2 is about.
- **The road or side changed under the same train.**  A run brought it back, or the operator answered from the right-click radio while the squares were still lit.

In those cases the sentence names causes that did not happen, and for Rename it names one that did not (TDU5-C1).  The list is a second statement, in eight languages, of what `sameSetup` and `placementStillStands` decide, and it has already fallen behind them.

**On the railway.**  None; it is a log line.  Its first half, "was not recorded", is right in every case, and that is the part the operator needs.

**Verification request.**
- **Fixture:** `pasteAndAnswerLate` with the train taken off in the wait (`model.getAutoLayout().moveLocomotive(null, "Tunnel (southbound)", true)`).  Then press the small window's `autosetup.ui.tailCrossedNotKnown` button instead of Cancel.
- **Proves it:** the dropped line is logged.
- **Refutes it:** nothing is logged.

**Suggested fix.**
- Take "or Not known" out of the javadoc.  Or, if Adam wants Not known silent, guard on `answer.getRoad() != null` and say so.
- Make the list plainly non-exhaustive, e.g. *"- for example the train was moved, another train stands there, or the setup was reloaded"*, ASCII-escaped in all eight bundles.

### TDU5-C4 - round 4's claims hold the paste door only: the right-click door's facing order, the other two doors' save skip, and their log guard and square name are each unclaimed, where the dispositions speak of every door

| | |
|---|---|
| **Disposition** | Fixed - pin testTheOtherDoorsKeepTheLateAnswerRules in 9fd15b6f reads the right-click and locomotive-dialog doors for all four rules.  Mutations R5c, R5d, R5e, R5f red. |
| **Where** | TDU4-C4's own table, row 1 (*"the same at the right-click door"*); `LayoutRightclickAutonomyMenu.java:1219-1221` (the facing), `:1257`, `:1267-1270`, `:1276`; `GraphLocAssign.java:337`, `:339-343`, `:353`; R4c, R4d, R4e and R4g all mutate `TrainControlUI.java` (scratch `make_mutr4.py`); `testTheFacingIsWrittenBeforeTheQuestion`, `testAnAnswerAfterTheSetupIsReplacedIsNotWritten` and `testOnlyADroppedAnswerIsLogged` all drive `rememberPlacement` |

TDU4-C4's disposition reads *"the facing before the question (R4g red)"*.  That is true of the paste door; TDU4-C4's first row named the right-click door as well.  TDU4-C2's (*"a door whose setup was let go in the wait saves nothing"*) and TDU4-C3's (*"only an answer is logged, the square named as the diagram names it"*) are claimed at the paste door alone.  The shared pieces are covered: `sameSetup` (R4b) and `whereTheAnswerGoes`' railway argument at all three doors (the source pin).  The per-door halves are not:

| Mutation | What catches it today |
|---|---|
| Right-click door: move `if (facing != null) session.setFacing(station, facing);` into the `setupStands && (...)` branch after the question - the pre-TDU3-C1 shape | Nothing |
| `GraphLocAssign`: delete `if (!setupStands) return;` | Nothing |
| Right-click door: drop `&& setupStands` from the save condition | Nothing |
| Either door: `else` for `else if (answer.wasAnswered() ...)`, or `point.getName()` / `landing.getName()` for `session.baseNameOf(...)` | Nothing |
| Either door: `writeRoad` given the pre-question railway (`layout` / `running`) instead of `runningNow(...)` | Nothing.  R4h's claim is the paste door's; this may be equivalent, as TDU4-C4 said of its own last row |

**On the railway.**  None today.  I checked by reading that the code at HEAD does what the dispositions say at every door.  But these are the halves a later edit at one door can undo with nothing going red.  The right-click door's facing is the one TDU3-C1 and TDU4-C4 each asked for by name.

**Verification request.**  `mutate.py` with each row above, against `regression.testTheTailIsPickedOnTheDiagram` and every class citing TDU3-C1, TDU4-C2, TDU4-C3 or TDU4-C4.
- **Proves it:** every row survives.
- **Refutes it:** any row goes red.

Suggested fix: extend the source pins beside `testEveryDoorAsksTheRailwayRunningAtTheAnswer` to the two menu doors, since each needs a menu or a dialog to reach:
- in `LayoutRightclickAutonomyMenu`, `setFacing(` before `askAfterPlacement(`;
- `setupStands` in each save guard;
- `wasAnswered()` in each log branch, with `baseNameOf(` in its argument.

## D - checked and clean

### TDU5-D1 - TDU4-C1: `sameSetup` asks for the configuration, and the claim is red for the finding's reason

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **The code.**  `sameSetup(asked, now, configurationAsked)` requires the same session and an equal active-configuration name.  Each door reads `configurationAsked` after its writes before the question and before `askAfterPlacement` (`TrainControlUI.java:8175`, `GraphLocAssign.java:316`, `LayoutRightclickAutonomyMenu.java:1238`), and passes the result into `whereTheAnswerGoes`.
- **Loads that still land the answer.**  A refused load puts the name back through `revert`, so the answer still lands.  A reload of the same configuration keeps the name, so the answer lands on the rebuilt copy.
- **The claim.**  `testAnAnswerIsNotWrittenIntoAnotherConfiguration` loads a copy of the configuration in the wait.  It asserts the precondition the finding needs (the running Tunnel holds the same train, from N, with no road) and reads the road the setup now holds for that square.  Round 3's session-only check passed, so the answer was written there: red for the finding's own reason.  R4a (ask only the session) is red in the round's record.
- **The false drops** the name check adds are TDU5-C1.

### TDU5-D2 - TDU4-C2: a let-go setup is saved at no door, and the railway is asked for without making one

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **The save.**  `TrainControlUI.java:8214` and `GraphLocAssign.java:353` return before the save, and the right-click door's save condition carries `setupStands` (`:1276`).  Nothing the returns skip was needed.  The paste handler's `updateVisiblePoints` and `repaintAutoLocList` run in the caller (`:7334-7335`); `GraphLocAssign`'s callers repaint or post `setupChanged` after it returns.
- **The railway.**  `runningNow` is used at all six post-question sites: three `whereTheAnswerGoes` calls and three `writeRoad` calls.  The remaining post-question readers (`updateVisiblePoints`, `repaintAutoLocList`) ask `hasAutoLayout` first.  `clearAutoLayout` has two callers, `unloadAutonomy` and `autonomySetupDeleted`, and both reset the session.  So a null railway only comes with `sameSetup` false, where `whereTheAnswerGoes` returns null before it asks the copy.
- **`getAutonomySession()`.**  The doors still call it after the wait, and it builds a session from disk when there is none.  That is not a new side effect: `resetAutonomySession` ends in `mountAutonomyControls`, whose first line (`:3904`) builds one at once.
- **The claim.**  `testAnAnswerAfterTheSetupIsReplacedIsNotWritten` replaces the session as the editor's close does and compares the configuration file's modification time.  Round 3's door saved the old session there, so the claim was red for the finding's reason.  R4b and R4c are red.  It also catches dropping `!sameSetup ||` from `whereTheAnswerGoes`: the new build holds the train from N with no road, so the road would be written.
- **The editor panel's copy of the locomotive dialog** passes the main window's session (`editableAutonomySession`), so `sameSetup` is not false there by construction.

### TDU5-D3 - TDU4-C3: a Cancel is not logged, the square is named as the question names it, and no remedy is given

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **The guard and the name.**  All three doors guard the log with `wasAnswered()` and name the square through `session.baseNameOf`.  That is the function each door hands the question itself as `shown`, so the log and the question name the square alike.
- **The bundles.**  In all eight the remedy is gone, with `{0}` and `{1}` once each and no straight apostrophe (French and Italian use `’`).
- **The claim.**  `testOnlyADroppedAnswerIsLogged` was red first for the name half.  At `665569f3` the door named the copy ("Tunnel (southbound)"), so the answered assertion could not find the line naming "Tunnel".
- **The Cancel half** could not be red at that commit for its own reason: the Cancel's line also named the copy, so it never matched the expected text either.  Its first red is R4d (log every reply), recorded red.  The disposition holds.
- **Not known:** TDU5-C3.

### TDU5-D4 - TDU4-C4: the pins that exist prove what they say

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **The facing.**  R4g deletes the paste door's `setFacing`.  The shape TDU4-C4 named - moved below the question, inside `if (landing != null)` - fails the same assertion, because the fixture takes the train off in the wait and `landing` is null.  The field `facingChosenAtTheLanding` is set to S, so the facing written cannot be null by rule.
- **The session.**  R4b is red through the TDU4-C2 claim.  Dropping `!sameSetup ||` is caught by both new claims (TDU5-D2).
- **The railway argument.**  The pin reads the first `whereTheAnswerGoes(` in each of the three files.  By grep it is the only occurrence in each, so the pin reads the call itself.
- **The edges.**  R4h is red on `testAnAnswerAfterARebuildReachesTheRailway`'s new loop, which compares each edge by identity with the running railway's edge of that name.
- **Gaps:** TDU5-C4.

### TDU5-D5 - TDU4-C5: all eight bundles quote the Mass Assign Lengths label verbatim

| | |
|---|---|
| **Disposition** | Checked - clean. |

Each bundle's `autosetup.ui.checkHalfMeasuredApproach` contains its own `autosetup.ui.menuMassAssignLengths` text without the ellipsis:
- Italian: *"Assegna lunghezze in blocco"*.
- Polish: *"Przypisz długości zbiorczo"*.
- The other six unchanged, as round 4 found them.

Nothing else in the change was touched.  No other English string names that tool; the other Mass Assign keys are menu items of their own.

### TDU5-D6 - the message bundles after the range

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **Keys:** 1828 in each of the eight bundles.
- **Encoding:** no non-ASCII byte in any file (`tr -d '\000-\177' | wc -c` is 0 for all eight).
- **The one key the range rewrote everywhere**, `autolayout.ui.logTailAnswerDropped`, has `{0}` and `{1}` once each and no straight apostrophe, so MessageFormat cannot eat a placeholder.

### TDU5-D7 - the tracker text against the code: MT-576's comment and MT-579

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **MT-576's comment** quotes the English line as it now reads.  Step 7 is a click, not a Cancel, so the line still appears after TDU4-C3's change.  Step 2's right-click setting rebuilds the railway in the same session and configuration, so step 3's answer still lands under TDU4-C1's check.
- **MT-579.**  Its Cancel comes after a second train has come onto the same copy, which `placementStillStands` drops on the train alone.  A Cancel is not logged, so the expected result names only the grey, which is right.  Its cited claim, `testALateAnswerDoesNotWriteOverAnotherTrain`, has the same shape.
- **The catalogue.**  Every TDU4 row is Closed in `findings.tsv`.  `TDU4.md`'s status line still reads open, as every document in the folder does, until FANOUT's "Closing the round" step.

### TDU5-D8 - the harness commit's three UI claims

| | |
|---|---|
| **Disposition** | Checked - clean. |

`114f1600` opens `LayoutSandbox` inside the `try`, and closes it only when it is not null, in:
- `testApplyIsGreyedWithNothingToApply`;
- `testAThreeWayIsLitOnce`, still before `init`, as its comment requires;
- `testARouteDrivenLocomotiveIsNotEdited`.

A throw from `open` now leaves the finally with nothing to close.  A throw after `open` still closes the sandbox, so the layout preference cannot be left pointing into `%TEMP%`.  Nothing else in the three classes changed.
