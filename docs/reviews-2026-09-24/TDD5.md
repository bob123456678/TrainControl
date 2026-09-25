# TDD5 - Documents, the tracker, and the tests themselves (round 5, validation)

**Status:** open

**Prefix:** TDD5

**Validated:** branch `autonomy-diagram-r0` at `1d22c83f`, 2026-09-24. The range is `7c38b51a..1d22c83f`, four commits: `114f1600` (harness: the three new classes open their sandbox inside the try, the window census, the live snapshot's users), `665569f3` (claims and pins), `6c7d5373` (fixes and text), `1d22c83f` (records and tracker). Nothing was run: no JVM, no test, no compiler, no `one.sh`, `battery.sh` or `mutate.py`, and no git command that changes state. `cs2_sample_layout/` was not opened. `mutr4.out` ends, as `mutr2.out` and `mutr3.out` do, with a diffstat of two autonomy files in that folder: the same 147 insertions and 46 deletions in all three logs. No commit in the range touches them, and they are not graded. The only file written is this one.

**Method.** I read `docs/reviews/FANOUT.md` and `docs/reviews/README.md` first, then `docs/reviews-2026-09-24/TDD4.md` with its dispositions. I also read `TDA4.md` and `TDU4.md`, because their fixes land in this lane's tests and documents. I read `git log --stat` for the range and `git show` for all four commits.

Tests:
- **`testTheTailIsPickedOnTheDiagram`**: the five new methods, the new pin in `testAnAnswerAfterARebuildReachesTheRailway`, `pasteAndAnswerLate`, `clearTunnel`, the three older late-answer claims and `testEveryPlacementDoorAsksWhetherItStillStands`.
- **`testMassAssignLengths`**: the two new pins, the crossing claims and both fixtures.
- **`testATailIsNotHiddenByAnother`**: in full, with R2e and R2f traced.
- **The harness**: the three classes changed by `114f1600`, and the window census and sandbox ratchets in `testSwitchingToACentralStationLayout`.

Code, as far as the lane depends on it:
- **`TailCrossedPrompt`**: `Answer`, `Reply`, `reply`, `placementStillStands`, `whereTheAnswerGoes`, `writeRoad`, `runningNow`, `sameSetup`, `noteADroppedAnswer`, and the small window's buttons.
- **The three placement doors**: `rememberPlacement`, `placeFacing` and `GraphLocAssign.commitAndRecord`.
- **`AutonomySession`**: `save`, `saveWithoutReconciling`, `setFacing`, `setPointProperty`, `deriveStationIndex`, `anythingMeasuredOn`, `berthTrackBeforeTheStop`, `stationsWithAHalfMeasuredApproach` and `runInsShorterThanTheBerth`.
- **Elsewhere**: `AutonomyViewerPanel.load`; `AutonomyCompanionStore.save`; `AutonomyChecks`' javadoc; `Layout.whyABerthCannotHoldIt`; `AutonomyBuilder.nodesFor`; `GraphReducer`'s edge list; `TrainControlUI.getAutonomySession`, `resetAutonomySession` and `autonomyLocomotiveDeleted`; `MarklinControlStation.deleteLoc`; the menus MT-577 names; and `battery.sh`'s launch line.

Documents:
- **`behaviour.md`**: 5c, the run-in bullets and the answered-zero paragraph.
- **`open-questions.md`**: the Inbox paragraph, the Length section and the counts.
- **`issues.md`**: the receipts rule, OB-287's row, OB-298, and the Inbox headings, counted with `grep`.
- **`tests.md`**: MT-576, MT-577, MT-579 and the waiting table, with dispositions counted with `grep`.
- **`findings.tsv`.**

The store: `docs/manual-tests/triage.db`, read with Python's `sqlite3` in `mode=ro`. I read the rows by document and status, the round-4 rows and the day's rows that are not Closed. I compared the `test` blocks of MT-576 to MT-579 with `tests.md`. The bundles were checked for non-ASCII bytes with `tr` and `wc`.

In the session scratchpad, read without changing anything: `bk4.log`, `r4red.log` to `r4red3.log`, `r4fix.log`, `mutr4.json`, `mutr4.out`, `rc4.log`, `fix_newline.py`, `battery-0924m/console.txt`, `mutr2.json`, `mutr2.out`, `mutr3.out` and `probeCrossingBerth.java`.

Every finding that rests on behaviour I could not see by reading carries a verification request.

Grades are by consequence on the railway:
- **A**: wrong behaviour on the layout, or data lost.
- **B**: incorrect results in a specific configuration.
- **C**: text, a narrow case, or a guard that proves less than it says.
- **D**: checked and clean.

---

### TDD5-C1 - After another configuration is loaded in the wait, the answer is now dropped even where the train it describes stands on the railway that is running; behaviour.md 5c says that answer is written, and nothing records the choice TDU4-C1 left open

| **Disposition** | Fixed - Adam, 2026-09-24: *"Follow the train."*  The late answer goes onto the railway running and into the configuration now active; only a replaced session drops it.  Claim 7dc22256, fix f17f5a5c. |
|---|---|

**Where.** `TailCrossedPrompt.java:615-619` (`sameSetup`, `6c7d5373`) and `:564-572` (`whereTheAnswerGoes`). The three doors: `TrainControlUI.java:8184`, `LayoutRightclickAutonomyMenu.java:1249` and `GraphLocAssign.java:326`. `behaviour.md:1107-1111`. `messages.properties:1861`, the log line as `6c7d5373` rewrote it. TDU4-C1 and its disposition.

**The case is TDU4-C1's.** A configuration is loaded while the question waits, and it stands the same train on the same copy, from the same side, with no road.

**At `7c38b51a`:**
- The door wrote the answer into the configuration just loaded.
- It also wrote it onto the running railway's copy, through `writeRoad`'s `running.roadNamed`.
- Only the configuration the question was asked in missed it. TDU4-C1: *"The answer is true of the physical train, so the configuration loaded now is right to hold it."*

**At HEAD:**
- `sameSetup` is false, because the active configuration has changed.
- `whereTheAnswerGoes` returns null, and the answer goes nowhere: not into the configuration asked, not into the one loaded, and not onto the railway that is running.
- The running copy holds the train with no road, so the tail walk stops at the switch (the fork rule).
- The track beyond the switch, where the operator has just said the tail lies, is free to route another train over.
- That is TDU2-A1's consequence, on the railway in front of him rather than on a configuration loaded later.

**The log line.**
- It is written, because the reply was an answer, and it lists "another configuration was loaded" among its causes.
- It no longer says how to set the tail. TDU4-C3's fix took that out because in most causes it cannot be done.
- Here it can: the train still stands on the square, and its right-click menu offers **Where the tail lies**.

**`behaviour.md` 5c** still defines the rule by the copy alone: *"the square's copy on the railway running then still holds the train, with the side it was put down with and the road it had - a railway rebuilt in the wait counts, its new copy being the one asked"*.
- In this case every condition it names holds, and loading a configuration builds the railway anew. So the document says the answer is written, and the code drops it.
- None of TDU4-C1, TDU4-C2 or TDU4-C3 is cited anywhere in `docs/reference/`.

**The choice.** TDU4-C1 offered two fixes: drop the answer, or keep writing it and follow the physical train. The disposition took the first. Neither `open-questions.md` nor `behaviour.md` records that choice, or what it costs on the railway.

**What mitigates it.**
- The case is narrow: a configuration load during the wait, onto a configuration that places the same train on the same copy, from the same side, with no road.
- The log says the answer was not recorded.
- The grey on the diagram visibly stops at the switch.
- The right-click menu can set the tail.

**Consequence.** C, graded as TDU4-C1 was. It is this round's one item with a consequence on the railway: in this sequence, a tail the operator gave is missing from the running railway.

**Fix (Adam's decision).** Two options:
- Write the answer where the train stands - the running copy and the configuration now active - whenever the copy still holds the placement, and drop only a write into a configuration other than the one asked.
- Or keep the drop, and do three things: say so in `behaviour.md` 5c, list it in the session report as decided on his behalf, and give the log line its remedy where the train still stands on the square.

**Verification request.**
- **Fixture:** `regression.testTheTailIsPickedOnTheDiagram.testAnAnswerIsNotWrittenIntoAnotherConfiguration` as committed. After `pasteAndAnswerLate` returns, and before the `finally`, read `model.getAutoLayout().getPoint("Tunnel (southbound)").getArrivedAlong()` and `model.getAutoLayout().placesCoveredByStandingTrains()`.
- **Runs:** at HEAD, and under R4a (`sameSetup` without its configuration term).
- **Proves it:** at HEAD, the copy holds the train with no road, and none of the places on TunnelPre's road beyond the switch are claimed. Under R4a the copy has TunnelPre's road and those places are claimed.
- **Refutes it:** at HEAD, the copy has TunnelPre's road.

---

### TDD5-C2 - The round-4 door changes are claimed at the paste door only: at the right-click Place and the locomotive dialog, the setup check, the skipped save, the answer-only log line and its square name are held by nothing, and so is the right-click door's facing before the question

| **Disposition** | Fixed - as TDU5-C4. |
|---|---|

**Where.**
- The three doors after `6c7d5373`: `TrainControlUI.java:8083-8214`, `LayoutRightclickAutonomyMenu.java:1208-1276` and `GraphLocAssign.java:290-353`.
- `testTheTailIsPickedOnTheDiagram.java:1047-1063`, the only source pin the round added.
- `:606-636`, TDU2-A1's source pin, whose javadoc gives the precedent.
- `mutr4.json`: every R4 mutation of a door is in `TrainControlUI.java`.
- FANOUT's closing mutation run: *"A spec must name a class whose fixture reaches the mutated line."*

What `6c7d5373` changed at each door, and what holds each change:

| What changed | Paste door | Right-click Place | Locomotive dialog |
|---|---|---|---|
| The setup check, a call to `sameSetup` (TDU4-C1, TDU4-C2) | `testAnAnswerIsNotWrittenIntoAnotherConfiguration`, `testAnAnswerAfterTheSetupIsReplacedIsNotWritten` (R4a, R4b) | nothing (`:1249`) | nothing (`:326`) |
| No save once the setup is let go (TDU4-C2) | R4c | nothing (`:1276`) | nothing (`:353`) |
| Only an answer is logged (TDU4-C3) | R4d | nothing (`:1267`) | nothing (`:339`) |
| The square named, not the copy (TDU4-C3) | R4e | nothing (`:1270`) | nothing (`:342`) |
| The railway asked for without making one (TDU4-C2, TDU4-C4) | source pin (R4f) | source pin | source pin |
| The facing written before the question (TDU3-C1, TDU4-C4, TDD4-C5) | R4g | nothing (`:1221`) | written before, as it always was |

Why the shared helper and the existing tests do not cover the other two doors:
- **The helper.** R4a and R4b change `sameSetup` itself, so they reach every door through the paste door's claims. A door that stops calling the helper is not caught: put `ui.getAutonomySession() == session` back at `:1249`, or `edit.parent.getAutonomySession() == session` at `:326`, and nothing turns red.
- **The precedent.** TDU2-A1's fix was claimed at the paste door and pinned by source at the other two, *"because each needs a menu or a dialog to reach"* (`:606-611`). This round's source pin carries that precedent for one of its six changes.
- **No fixture reaches the other two doors' answer branches.** `placeFacing` is named in `test/` only by `testNothingOnTheEventThreadTakesTheRailwaysMonitor`'s allowlist. `testAPlacedTrainRecordsWhereItCameFrom` calls `commitAndRecord` with nothing changing while a question waits. So the closing mutation run cannot name a class for these lines.

**What mitigates it.** By reading, the code at both doors does what the dispositions say today.

**Consequence.** Each of these changes can be undone at the right-click Place or the locomotive dialog with every class green. The first four are exactly the kind of change a later edit of one door misses at the other two. C.

**Verification request.** Apply each mutation alone:
- **M1:** `LayoutRightclickAutonomyMenu.java:1249-1250` `TailCrossedPrompt.sameSetup(session, ui.getAutonomySession(), configurationAsked)` → `ui.getAutonomySession() == session`.
- **M2:** `:1276` drop `&& setupStands`.
- **M3:** `:1267` `else if (answer.wasAnswered() && landing != null)` → `else if (landing != null)`.
- **M4:** `:1270` `session.baseNameOf(landing.getName())` → `landing.getName()`.
- **M5:** move `:1221`'s `if (facing != null) session.setFacing(station, facing);` into the `if (setupStands && (...))` block at `:1257`.
- **M6:** `GraphLocAssign.java:326` → `setupStands = edit.parent.getAutonomySession() == session;`.
- **M7:** `:353` delete `if (!setupStands) return;`.
- **M8:** `:339` `else if (answer.wasAnswered())` → `else`.

Run each against `regression.testTheTailIsPickedOnTheDiagram`, `regression.testAPlacedTrainRecordsWhereItCameFrom`, `core.testAPasteDoesNotTurnTheTrainRound`, `core.testAPastedTrainKeepsItsDirection`, `ui.testACutTrainArrivesTheWayItWouldDrive` and `regression.testNothingOnTheEventThreadTakesTheRailwaysMonitor`.
- **Proves it:** all green under each mutation.
- **Refutes it:** any red.

**Likely fix.** Read each door's source, as `testEveryPlacementDoorAsksWhetherItStillStands` already does:
- `sameSetup(` sits between `askAfterPlacement(` and `whereTheAnswerGoes(`;
- `noteADroppedAnswer(` is guarded by `wasAnswered()`, and its third argument contains `baseNameOf(`;
- the door's `save()` comes after a `setupStands` guard;
- at the right-click door, `setFacing(` comes before `askAfterPlacement(`.

---

### TDD5-C3 - The measured-anywhere gate TDA4-C3's pin holds is in no document: behaviour.md's rewritten bullet, both javadocs and the question put to Adam say answered zeros give 0, or a berth that takes no train, where a leg nothing else measures takes every train and gets no notice

| **Disposition** | Fixed - as TDA5-C2 and TDA5-C3. |
|---|---|

**Where.**
- `behaviour.md:1471-1477`, rewritten in `6c7d5373`; and `:996-997`.
- `AutonomyChecks.java:328-330`, rewritten in `6c7d5373`.
- `AutonomySession.java:9769-9772`, `runInsShorterThanTheBerth`'s javadoc, not touched by the range.
- `open-questions.md:140-145`, TDA4-C2, added in `1d22c83f`.
- `core.testMassAssignLengths.testALegNothingMeasuresIsNotSaidAsNothing` (`665569f3`).
- The gate itself: `AutonomySession.java:9846`.

**The code.**
- The berth rule's figure, 0 included, is taken only where `anythingMeasuredOn(squares)` holds: something on the leg is measured, which is when the berth rule judges the leg at all (PRW-B1).
- TDA4-C3's fix made both notices ask it.
- The new pin asserts that a leg of answered zeros, with nothing else on it measured, gets no notice. R4i is red on it.

**What the text says instead.**
- **`behaviour.md`:** *"with nothing measured before the switch or crossing that ends its room the notice says 0 where those squares were answered 0"*. On the pin's own fixture - the berth and 6,1 answered 0, nothing else on the leg measured - that predicts 0, while the code and the pin say nothing. The bullet's "Silent where either side is missing" covers a railway that measures no track at all; the pin's railway measures 5,2.
- **`AutonomyChecks`' javadoc:** *"0 where every square before the switch or crossing that ends its room was answered 0"*. The same omission.
- **`runInsShorterThanTheBerth`'s own javadoc** still gives the 0 *"where nothing before that stop is measured"*, where "that stop" is a crossing or the end of a leg with no switch. That is the crossing-only wording TDA4-C1 found in the other two places; the fix's sweep did not reach this sibling, and it lacks the gate too. The comment inside the method (`:9827-9831`) has both.
- **`open-questions.md`, the question put to Adam:** *"Where every square between a parking berth and the switch or crossing that ends its room was answered 0, the berth takes no train"*. Where nothing else on the leg is measured, the berth takes every train. `behaviour.md:996-997` records his own confirmation: *"a stretch whose answers are all 0 is still not judged"*. The recommended sentence, *"X takes no train"*, is true only under the gate.

**What mitigates it.** The notice is gated in the code, so nothing on screen is wrong.

**Consequence.** Text. The document Adam reads as the intended behaviour, and the question he is being asked, both say that answering a leg 0 closes the berth. Where nothing else is measured, that is the opposite of his own ruling. C.

**Fix.** Add the condition in all four places: "once anything on the leg is measured, which is when the berth rule judges it". In `open-questions.md`, that is "where something else on the leg is measured".

---

### TDD5-C4 - noteADroppedAnswer's javadoc says a dropped Not known is not logged, but the code logs it; its parameter still names the copy; and the log line's list of causes leaves out a replaced setup and a turned train

| **Disposition** | Fixed - as TDU5-C3. |
|---|---|

**Where.**
- `TailCrossedPrompt.java:623-632`, the javadoc as `6c7d5373` wrote it.
- `:303-307`, `Answer.wasAnswered`: *"a sensor, or Not Known"*.
- `:212` and `:820`: Not known is `Reply(true, null)`, which becomes `new Answer(true, null)`.
- The three `else if (answer.wasAnswered())` branches.
- `messages.properties:1861`.

**The javadoc.** It says: *"Only for an answer (TDU4-C3) - a Cancel or Not known asked for nothing to be recorded"*.
- A dropped Not known is logged: it is an answer that forgets a road (`Answer`'s javadoc, `behaviour.md` 5c), and each door's guard is `wasAnswered()`, which is true for it.
- Logging it is right: a dropped Not known leaves in place a road the operator asked to forget. So the javadoc is what is wrong, not the code.
- TDU4-C3's suggested fix was `Reply.answered`, which is what was built.

**The parameter.** `@param where` still says "the copy it was put on". Every door now passes the square's name (`baseNameOf`).

**The log line.** Its causes are *"the train was moved, another train stands there, or another configuration was loaded"*. Two ways an answer is dropped are missing:
- **The setup replaced in the wait**: the track-diagram editor closed, a re-download, or Unload. This is TDU4-C2's case, where `sameSetup` is false.
- **The train turned round in the wait**: `testALateAnswerIsNotWrittenForTheOtherSide`'s case.

In both, the operator is given three reasons, none of which happened. This one is for the UI lane.

**Consequence.** Text; the behaviour is right. C.

**Verification request** (the Not known half, although by reading it is certain).
- **Fixture:** `pasteAndAnswerLate`, with the train taken off in the wait, and then the small window's **Not known** button pressed (`autosetup.ui.tailCrossedNotKnown`) instead of Cancel.
- **Proves it:** the dropped-answer line is logged.
- **Refutes it:** it is not logged.

---

### TDD5-D1 - TDD4-C1 (OB-287's receipt a State) holds

| **Disposition** | Checked - clean. |
|---|---|

- **The row.** `issues.md:1303` has State `fixed unvalidated` and Became `-`, with the test named in What. That is exactly one of the two columns, as the rule at `:1287-1293` requires.
- **It is this file's usual receipt for a fix proved only by a test:** OB-233, OB-238, OB-244, OB-246, OB-248, OB-269 and OB-271 carry it too. OB-183's row records Adam closing such rows on his word (*"close the 11 internal ones"*).
- **The Inbox.** The OB-287 heading stays in the Inbox (`:1140`), counted among its 53 OB entries.

### TDD5-D2 - TDD4-C2 (the MUTATION line of testATailIsNotHiddenByAnother) holds

| **Disposition** | Checked - clean. |
|---|---|

- **R2e.** `testATailIsNotHiddenByAnother.java:36-41` now says R2e fails at the control. Traced: under R2e every train's record holds the whole walk, and on this doubled-rail fixture the first train's walk covers `outOne`. So the unmoved third train's record holds it, and `passes(outOne, one, twoGone)` is false at `:262`.
- **R2f.** The line says R2f fails at the two-train claims. Traced: under R2f, S has one owner. The other train's record lacks S, and one of `:249` or `:253` fails first, depending on which train was walked last. The third train's own rail is never involved.
- **The second method.** "Both fail `testReturnHomeKeepsTheSensorShutWhileEitherTailIsOnIt` too" is what `mutr2.out` records for both mutations.
- **Not rerun in round 4.** Where each mutation fails rests on reading. TDD4 and TDA4 did that reading independently, and I repeated it.

### TDD5-D3 - TDD4-C3 (behaviour.md's second bullet) holds

| **Disposition** | Checked - clean. |
|---|---|

- **The bullet.** `behaviour.md:1478-1480` now ends *"- or, for a parking berth, a crossing on it ends the berth rule's room, as above"*.
- **The javadoc.** That is the clause `runInsShorterThanTheBerth`'s javadoc carries (`:9774-9775`).
- **The code.** It matches `:9846` and `:9855`: for such a berth the room walk answers `Integer.MIN_VALUE`, `room <= 0` takes the berth rule's figure, and the skip is not reached. The gate the first bullet omits is TDD5-C3.

### TDD5-D4 - TDD4-C4 (MT-576's comment and MT-579) holds

| **Disposition** | Checked - clean. |
|---|---|

- **MT-576's comment quotes the line correctly.** It quotes *"...Tunnel changed while the question waited - the train was moved, another train stands there, or another configuration was loaded"*. That is the new bundle text, with {1} = `baseNameOf("Tunnel (southbound)")` = "Tunnel": the logging claim builds its needle that way and is green at HEAD.
- **Step 7 still produces the line.** Step 7 is a click, which is an answer, so the line is written under the new answer-only rule.
- **"Put 75 407 DB back"** is there, and the comment points to MT-579 for the second-train case.
- **MT-579 is TDD3-C6's case.** A second five-unit train is sent into Tunnel southbound while the first train's question waits, and then Cancel is pressed.
  - The step can tell fixed code from unfixed. `placementStillStands` sees another train on the copy and writes nothing. Without the train term, the Cancel writes `roadToRecord(...)` - null, or the first train's earlier road - over the second train's.
  - "What this is" names an existing method, which puts the second train there by hand rather than by a run but asks the same check.
- **Note, not raised.** The entry does not say where the second train should start. "So it arrives over TunnelPre" is stated as the condition, and a train reaching Tunnel from the north can also come along 12,7 (the class javadoc).
- **The index.** The index row is there, and the count reads "531 of 579".

### TDD5-D5 - TDD4-C5 (the two claims) holds at the paste door

| **Disposition** | Checked - clean. |
|---|---|

- **`testTheFacingIsWrittenBeforeTheQuestion`.**
  - The fixture: it clears the facing, sets `facingChosenAtTheLanding` to S, takes the train off during the wait and presses Cancel, so the answer is dropped. It asserts that the setup has a facing for Tunnel.
  - R4g deletes the paste door's `setFacing`. TDD4-C5's Mutation B moves it inside `if (landing != null)`. Both reach the same assertion, because `landing` is null here.
  - R4g being red shows that nothing else in the door writes a facing.
- **`testOnlyADroppedAnswerIsLogged`.**
  - The answered half fails if the log branch is deleted (TDD4-C5's Mutation A) or if the door names the copy (R4e).
  - The Cancel half fails under R4d.
- **The gap.** The right-click door's facing is TDD5-C2.

### TDD5-D6 - TDD4-C6 (MT-577's comment) holds

| **Disposition** | Checked - clean. |
|---|---|

- **The keyboard button's path.** Manage Locomotive... then Customize Function Icons: `RightClickMenuListener.java:187` and `:218`.
- **The locomotive database's path.** Customize Function Icons at the top level: `RightClickSelectorMenu.java:54`.
- **The labels** are `messages.properties:593` and `:608`. The comment says the rest of the entry stands as written.

### TDD5-D7 - The TDU4 claims and pins: red for their findings' reasons, and each R4 mutation reaches its assertion

| **Disposition** | Checked - clean. |
|---|---|

- **`testAnAnswerIsNotWrittenIntoAnotherConfiguration`.**
  - `createConfiguration` copies the state held in memory, which includes the placement written before the question.
  - `load` captures into the configuration asked, by name, and saves every configuration (`AutonomyCompanionStore.save` writes them all). So skipping the door's save loses nothing.
  - The precondition asks that the copy hold the train, from N, with no road.
  - At `7c38b51a` the door wrote into the configuration just loaded, so the claim was red for TDU4-C1's reason. R4a reaches it.
- **`testAnAnswerAfterTheSetupIsReplacedIsNotWritten`.**
  - The reset captures and saves without reconciling, and the new session is loaded from that file. The file's time is read after that, followed by a 1.1-second wait.
  - At `7c38b51a` the paste door saved the old session, which rewrites every configuration file. So the claim was red at its second assertion, for TDU4-C2's reason.
  - R4b reaches the first assertion: the new railway is built from the setup the reset captured, so the copy holds the train from N with no road. R4c reaches the second.
- **`testEveryDoorAsksTheRailwayRunningAtTheAnswer`.** Each file has exactly one `whereTheAnswerGoes(`, and its first argument is read up to the first comma. The test was red at `665569f3`, when `runningNow` did not exist yet.
- **The log listener** is added in a static block. `battery.sh:654` runs one JVM per class, so it lives only as long as this class's run.
- **The rebuild pin (R4h)** asserts that each edge of the road is the running railway's own edge of that name. That is stricter than behaviour requires: TDU4-C4 thought the old edges might block the same track. The pin settles the question by asserting what `writeRoad`'s javadoc says.
- **Note, not raised: the Cancel half.**
  - The needle is the whole formatted line, with "Tunnel" as {1}. At `665569f3` the line named "Tunnel (southbound)", so the Cancel half passed there. The method was red first because of the name, and the Cancel half was never seen red on the unfixed code.
  - It catches R4d now only because the door names the square.
  - The variable `first` (the line's first 25 characters, which do not depend on {1}) is computed at `:965` but used only in the failure message. Used as the Cancel half's needle, it would not depend on the name.

### TDD5-D8 - The two berth pins (TDA4-C1, TDA4-C3): each discriminates, and each premise holds by reading

| **Disposition** | Checked - clean. |
|---|---|

- **`testNothingSpentBeforeTheSwitchIsSaidAsNothing`** uses TDA4-C1's own fixture and input.
  - Traced: `berthTrackBeforeTheStop` gives `{0, 0, 1}`, `anythingMeasuredOn` is true on 2,1, and the room walk gives -1. So the branch takes 0, and the entry is `{3, 0}`.
  - It was green at `665569f3` (`r4red.log`: 64 run, 0 failed), so it is a pin, as the disposition says. R3f is red on it (`mutr4.out`).
- **`testALegNothingMeasuresIsNotSaidAsNothing`** measures 5,2, so `measuresAnyTrack` passes and only the gate decides. Traced: no entry at HEAD, and `{5, 0}` under R4i, which is red.
- **The premises, which neither pin asserts.**
  - **The switch.** The branch rail from 1,1 to 3,0 shares 2,1 and the switch with the approach, and touches neither end of the berth. `AutonomyBuilder.nodesFor` (`:618-625`) keeps a dead-end sensor's plain copy, with rails into it. Both roads leave 1,1 by the same side, so a fixture that builds the berth's rail also builds the branch's. A one-unit train therefore claims 2,1 and the switch, and is refused.
  - **The leg nothing measures.** `whyABerthCannotHoldIt` finds nothing measured among the approach's places and returns null.
- **Not raised.** When the crossing's premise went unasked, it turned out false (TDA3-C2). By reading, both of these hold. A single assertion of the kind `testTheBerthRuleRefusesAtTheCrossing` makes would settle each.

### TDD5-D9 - The harness commit 114f1600

| **Disposition** | Checked - clean. |
|---|---|

- **The sandbox, opened inside the try.** `testARouteDrivenLocomotiveIsNotEdited`, `testAThreeWayIsLitOnce` and `testApplyIsGreyedWithNothingToApply` now open their sandbox inside the `try` and close it only if it was opened. It is still opened before the model and before the window, so both ratchets still hold.
- **The window census.** It goes from 60 to 62 for the two classes that write `new TrainControlUI(`. `testAThreeWayIsLitOnce` builds its window through `init(..., showUI)`, so it is counted by the model ratchet instead, where its sandbox comes before `init`.
- **The live snapshot.** Its README now lists `testAThreeWayIsLitOnce`.
- **The runs.** `bk4.log`: all five classes green. `battery-0924m` found both failures. The three classes had passed the round-3 records, and TDD4 did not see it either.

### TDD5-D10 - The records commit: counts, the store, the tracker, OB-298

| **Disposition** | Checked - clean. |
|---|---|

- **The finding count.** The store holds 4,298 rows for 3,941 refs. Round 4 adds 50 rows (TDA4 10, TDU4 17, TDD4 23):
  - 49 are Closed, and TDA4-C2 is "Open - Adam's decision";
  - 173 + 50 = 223, and 3,726 + 336 + 13 + 223 = 4,298, as `behaviour.md` and `open-questions.md` say;
  - `findings.tsv` has the 50 rows.
- **The day's open rows** are nine: six decisions (TDA-C8, TDA-C10, TDD-C11, TDU-C6, TDU2-C3, TDA4-C2) and three follow-ups (OB-295, OB-296, OB-297).
- **The tracker.**
  - There are 579 MT headings: 46 fixed unvalidated, 2 needs test, 454 fixed validated and 77 superseded. The waiting table has 48 rows, and "531 of 579" is right.
  - The store's blocks for MT-576, MT-577 and MT-579 appear verbatim in `tests.md`, with the new comments, so a render will not drop them.
- **The Inbox.** It has 53 OB and 32 FR headings before "What has been picked up", 85 in all, as `open-questions.md` says. `rc4.log`: the count and citation checks are green.
- **OB-298's mechanism is as stated.** `GraphReducer` keeps one `ArrayList` of edges and clears it in place (`:334`, `:388`), and `AutonomyBuilder.splitSides` iterates it (`:467`).
- **Note, not raised.** `testTheFacingIsWrittenBeforeTheQuestion:916` calls `setFacing` from the test thread, and `setFacing` builds the station index (`deriveStationIndex` constructs a builder), which is OB-298's shape. By reading, nothing is rebuilding at that moment: the preceding door has returned after its save, and `deleteLoc`'s repair saves the store without rebuilding.
- **The bundles.** All eight have zero non-ASCII bytes, and `logTailAnswerDropped` carries {0} and {1} once each in every language.

### TDD5-D11 - TDD4-D13's request is done: R3e and R3f were rerun on the crossing trains can drive

| **Disposition** | Checked - clean. |
|---|---|

- **R3e** is red on `testNothingSpentBeforeTheCrossingIsSaidAsNothing`.
- **R3f** is red on `testACrossingOnALegWithNoSwitchIsSaid`, and on the new switch pin as well (`mutr4.out`).
