# TDD4 - Documents, the tracker, and the tests themselves (round 4, validation)

**Status:** open

**Prefix:** TDD4

**Validated:** branch `autonomy-diagram-r0` at `7c38b51a`, 2026-09-24. The range is `67f79912..7c38b51a`, four commits: `6bfab5bb` (claims and pins), `32a75f8d` (fixes), `59cf3645` (text, and the crossing fixture), `7c38b51a` (records and tracker). Nothing was run: no JVM, no test, no compiler, no `one.sh`, `battery.sh` or `mutate.py`, and no git command that changes state. `cs2_sample_layout/` was not opened (`git status` lists two files in it as modified in the working tree; they were not read). The only file written is this one.

**Method.** I read `docs/reviews/FANOUT.md` and `docs/reviews/README.md` first, then `docs/reviews-2026-09-24/TDD3.md` with its dispositions, and `TDA3.md` and `TDU3.md` for the round-3 findings whose fixes landed in this lane's tests and documents. I read `git log --stat` and `git show` of all four commits. Tests, in full or in the parts the range touched: `testTheOwnTailArithmetic` (the new pin traced by hand at HEAD and under R3l), `testATailIsNotHiddenByAnother` (in full; R2e and R2f traced against `Layout.tailsOfEachStandingTrain`, `walkStandingTrains`, `walkOneTail`'s covered map and `tailLiesOn`, and `HomeStaging.passesTheTailsOfTrainsThatHaveNotMoved` and `liesAcrossAtStart`), `testMassAssignLengths` 2180-2530 (the claims, the pins and both forms of the crossing fixture), `testTheTailIsPickedOnTheDiagram` (the new helper, the claim, the three pins and the re-pointed source pin), `testNonAtomicRoutesNeedTheirLengths` (the gate test against the three door handlers), `testARouteDrivenLocomotiveIsNotEdited`, `testAThreeWayIsLitOnce`, `testApplyIsGreyedWithNothingToApply`, `testTheEditorSaysWhatItsToolsDo` (the pin MT-570 now names) and `build.xml`. Code, as far as the lane depends on it: `TailCrossedPrompt` (`placementStillStands`, `whereTheAnswerGoes`, `writeRoad`, `noteADroppedAnswer`, `DiagramPick.light` and `finish`), the three placement doors, the paste and cut in `TrainControlUI`, `AutonomySession.placeLocomotive` and `runInsShorterThanTheBerth`, `LayoutRightclickAutonomyMenu.removeLocomotiveHere`, `Layout.whyItWouldMeetItsOwnTail`, `Edge` and `Point` equality, `AutonomyEditorPanel.cycle`, `say` and `rebuildRunningLayoutSoon`, `RouteEditorFrame.highlightOnDiagram` and `highlightAccessories`, `LocomotiveFunctionAssign`, the two locomotive menus, and the Start, Execute Timetable and Return Home handlers. Documents: `tests.md` MT-437, MT-464, MT-531, MT-565, MT-568, MT-570, MT-573 and MT-576 to MT-578 and the waiting table; `issues.md` OB-286, OB-287, FR-098 and OB-297 and the receipts section with its rule; `behaviour.md` 5c and the run-in bullets; `open-questions.md`; `Automation.md`'s length section. The store: `docs/manual-tests/triage.db` read with `sqlite3` in `mode=ro` (rows by document, the TDD3 rows, every row of the day not Closed, MT dispositions), and the store's `test` blocks for eight entries compared with `tests.md` by a one-line read-only `python -c`; the Inbox counted with `grep`; the bundles checked for non-ASCII bytes with `tr` and `wc`. In the session scratchpad, read without changing anything: `r3red.log`, `r3red2.log`, `r3fix.log`, `r3txt.log`, `prb.log`, `live.log`, `rc3.log`, `mutr3.out`, `make_mutr3.py` (the R3 specs), and `mutr2.json`/`mutr2.out` for R2e and R2f. Every finding that rests on behaviour I could not see by reading carries a verification request.

Grades are by consequence on the railway: A wrong behaviour on the layout or data lost, B incorrect results in a specific configuration, C text, a narrow case, or a guard that proves less than it says, D checked and clean.

---

### TDD4-C1 - OB-287's receipt says it became MT-531, which checks F0 to F4 on an MM2 locomotive; the triage app therefore shows OB-287 validated, on Adam's "Works" for a different test

| **Disposition** | Fixed - OB-287's receipt carries a State, fixed unvalidated, with the test named, in place of MT-531 (round 4's tracker commit). |
|---|---|

**Where.** `issues.md:1294` (the receipt row, Became `MT-531`); `issues.md:1278-1288` (the rule: Became names the MT tag and the state lives there from then on; the app shows the worst state of the tests a row became); `tests.md:26754-26780` (MT-531: its steps open the function-number cell; fixed validated on Adam's Works of 2026-09-24); `open-questions.md:48` (the clearing rule).

**What OB-287 is.** MT-464's automated test was asked for and only half built: the refusal to delete or rename a locomotive a running route drives. Its fix is a test, `regression.testARouteDrivenLocomotiveIsNotEdited` (`6bfab5bb`). No MT was written for it, and none is wanted: Adam asked on MT-464 for an automated test instead of a hand one.

**What the receipt does.**
- MT-531 is the F0-to-F4 half split out of MT-464. Its "What was wrong" mentions OB-287 as work in progress; none of its steps touches the refusal.
- Through the Became link OB-287 takes MT-531's state, fixed validated, so the app lists it as validated on the railway.
- By `open-questions.md:48`'s rule ("every entry whose receipt, or the hands-on test it became, is fixed and validated") it is now due to leave the Inbox on that validation.

**Consequence.** None on the railway: the refusal is claimed and pinned (TDD4-D16). The record says a thing was validated by hand when it was proved by a test, and it says so through a test about something else. C.

**Fix.** A State in place of the Became, as the table's rule provides for something tracked directly (exactly one of the two, `issues.md:1285`) - `fixed unvalidated`, or whatever Adam takes an automated-only receipt to be - with the test named in What.

---

### TDD4-C2 - testATailIsNotHiddenByAnother's new MUTATION line puts R2e's and R2f's failures at the third-train assertion; by reading, R2e fails at the control before it and R2f at the TDD-A1 claims, so neither reaches the assertion it names

| **Disposition** | Fixed - as TDA4-C4. |
|---|---|

**Where.** `test/core/testATailIsNotHiddenByAnother.java:36-40` (the line, rewritten in `6bfab5bb` as TDA3-C3's fix); `:248-254` (the TDD-A1 claims); `:256-262` (the control); `:264-268` (the third-train assertion, TDA2-C2 and TDD2-C4); `Layout.java:6944`, `:7713`, `:7722`; `HomeStaging.java:1711`.

**What the line says.** R2e and R2f make `testReturnHomeAsksAboutEveryTailOnThePlace` fail "where the third train is blamed for the rail the second one's tail lay along" - the assertion at `:266`. That follows TDA3-C3, which read the control at `:261` as passing under R2e because "the third train owns no place of outOne". TDD3-D2 read it the other way. Traced at HEAD, TDD3-D2 was right.

**Why.**
- The fixture writes each rail both ways between the same two Points. On such a graph `walkOneTail` covers the reverse rail too (`Layout.java:7713` and the same-rail loop to `:7722`), so the first train's walk covers both `inOne` and `outOne`.
- `liesAcrossAtStart` asks `covered.containsKey(edge)` first, and does not ask whose edge it is (`HomeStaging.java:1711`).
- R2e (`walkStandingTrains(covered, places, null)` at `Layout.java:6944`) gives every train the whole walk. At `:261` the unmoved third train's record holds `outOne`, so `passes(outOne, one, twoGone)` is false and the control fails. Its message - "control: with the second train moved, Return Home still refuses the first train's way out, so the claims above are not about the second train's tail" - tells the reader the fixture is at fault.
- R2f (each train keeps the entries of the one-owner walk that are its own) leaves the switch square S with one owner. The train that does not own it loses S, and the TDD-A1 claim for the other train's way out fails at `:248` or `:252`. The third train's record is its own rail only, so `:266` would pass.
- So `:266` is reached by neither. Under R2e the control catches the mutation (the third train is what makes it catch, as TDA3-D2 said); under R2f the original TDD-A1 claim does.

**Consequence.** None on the railway, and both mutations are still red on the class (`mutr2.out`). The line exists to steer the closing mutation run, and it names the wrong assertion for both; a reader who sees R2e's red is sent to the fixture. C.

**Fix.** Say what happens - R2e fails the control, where the unmoved third train is charged with the first train's own rail; R2f fails the TDD-A1 claim - or move the `:266` assertion above the control, so R2e is caught where the line says.

**Verification request.**
- Mutations: R2e and R2f as specified in the session's `mutr2.json`, each against `core.testATailIsNotHiddenByAnother`. Read the failure message of `testReturnHomeAsksAboutEveryTailOnThePlace`.
- Proves it: R2e's message is the control's ("control: with the second train moved ..."); R2f's is "Return Home let the first (or second) train out through the switch ...".
- Refutes it: either message is "with the second train moved, the rail its tail lay along is still refused - blamed on the third train ...".

---

### TDD4-C3 - behaviour.md's run-in bullets now contradict each other: the new sentence gives a parking berth the berth rule's figure "on a leg with no switch", and the bullet under it still says such an edge is skipped

| **Disposition** | Fixed - as TDA4-C4, behaviour.md's bullet. |
|---|---|

**Where.** `behaviour.md:1471-1478`; `AutonomySession.java:9777-9778` (the javadoc TDA3-C3's fix corrected), `:9846` (`room <= 0 ||`), `:9855` (the skip).

`59cf3645` extended the room bullet (TDD3-C9, TDA3-C2): for a parking berth the number is the berth rule's "at a crossing between the berth and its switch, or on a leg with no switch". The bullet directly under it was not touched: "An arriving edge crossing no switch is skipped unless trains turn round where it starts". For a parking berth with a crossing on a switchless leg the code now takes the berth rule's figure at `:9846` - the room walk answers `Integer.MIN_VALUE`, so `room <= 0` holds - and never reaches the skip at `:9855`. The javadoc of `runInsShorterThanTheBerth` gained exactly this exception in the same round ("- or, for a parking berth, a crossing on it ends the berth rule's room (above)"); the document Adam reads as the intended behaviour did not.

**Consequence.** Text. Not on the frozen railway: its one crossing on an included page lies beyond the switch from every station (TDA2-C6's mitigation, still true). C.

**Fix.** The javadoc's clause, added to the second bullet.

---

### TDD4-C4 - MT-576's second half cannot show TDU2-A1's fix by what Adam sees, the case TDD3-C6 asked the tracker for is not in it, and it ends with 75 407 DB taken off the railway

| **Disposition** | Fixed - a comment on MT-576 (the log line as it now reads; put 75 407 DB back) and MT-579 for the second-train case TDD3-C6 asked for. |
|---|---|

**Where.** `tests.md:28259-28285` (MT-576); `TDD3.md:161-177` (TDD3-C6, its railway check); `LayoutRightclickAutonomyMenu.java:744-755` and `:1480-1520` (Remove {0}); `Layout.walkStandingTrains` (a tail is walked only from a Point with a train on it); `TailCrossedPrompt.java:599-601` and `messages.properties:1861` (the log line).

TDD3-C6's disposition is "behaviour.md 5c states the rule (59cf3645), and MT-576 is its hands-on test". The behaviour.md half holds: `behaviour.md:1107-1111` matches `whereTheAnswerGoes`, `placementStillStands` and the three doors, the facing written before the question included. MT-576's first half holds too: steps 1-4 show TDU3-B1's fix (without `32a75f8d` the grey stops at the switch). The second half does not show the rule TDU2-A1 was graded A for.

1. **Steps 5-7 take the train off and then answer.** With nothing on the copy, nothing is drawn behind Tunnel whether the answer is dropped or written: the grey is the tail walk, and the walk starts only from a Point with a train on it. An answer written onto the empty copy, as before `5e1b0623`, leaves a road nobody walks, which the next occupant's `setLocomotive` clears (TDU3-D1). So "Step 7: nothing is drawn behind Tunnel" is true of the fixed and the unfixed code. Only the log line tells them apart, and that line is TDU3-C2's, new in `32a75f8d`.
2. **The case with a railway consequence is missing.** Another train brought onto the copy by a run while the question waits, whose driven road a late answer must not overwrite, is TDD3-C6's own request: "a second five-unit train hand-sent into Tunnel southbound; then Cancel, and the grey behind Tunnel runs on to TunnelPre".
3. **Step 6 takes 75 407 DB off Tunnel, and nothing puts it back.** The entries before it stood it at Tunnel (MT-437 "Stand 75 407 DB at Tunnel", MT-565, MT-574). After MT-576 the model has it nowhere, whatever it is standing on.
4. **A note for the UI lane.** The line step 7 produces ends "Set it again under Where the tail lies, on the right-click menu of Tunnel (southbound)": advice about a square with no train on it by then, and naming the copy rather than the square.

**Consequence.** The tracker proves less than the disposition says. The entry also leaves the model without a train that may still stand at Tunnel; Adam removes it himself in plain view, and its sensor is lit if it stands on one, which mitigates it. C.

**Fix.** A comment on MT-576 (the entry is append-only): after step 7, put 75 407 DB back where it stands. If the A-grade case is to be seen by hand, TDD3-C6's steps as an entry of their own, whose expected result is that the second train's grey still runs on to TunnelPre after the late Cancel.

**Verification request** (point 1, at the desk).
- Fixture: `regression.testTheTailIsPickedOnTheDiagram`'s `pasteAndAnswerLate`, with `meanwhile` taking the train off as the Remove item does (`model.getAutoLayout().moveLocomotive(null, "Tunnel (southbound)", true)`), then a click on TunnelPre. Afterwards read `model.getAutoLayout().placesCoveredByStandingTrains()`.
- Run it at HEAD, and again with `whereTheAnswerGoes` made to return `asked` and `placementStillStands` to return true, so the answer is written.
- Proves it: no place of TunnelPre's road is claimed in either run.
- Refutes it: the second run claims them.

---

### TDD4-C5 - two round-3 fixes shipped with no claim: the log line for a dropped answer (TDU3-C2) and the facing moved above the question (TDU3-C1); behaviour.md now states both as the intent

| **Disposition** | Fixed - claims testOnlyADroppedAnswerIsLogged and testTheFacingIsWrittenBeforeTheQuestion in 665569f3.  Mutations R4d, R4g red. |
|---|---|

**Where.** `TrainControlUI.java:8195-8198`, `LayoutRightclickAutonomyMenu.java:1261`, `GraphLocAssign.java:336` (`noteADroppedAnswer`); `TrainControlUI.java:8150-8154` and `LayoutRightclickAutonomyMenu.java:1216-1218` (the facing, written before `askAfterPlacement`); `behaviour.md:1107-1111`; `test/core/testAPasteDoesNotTurnTheTrainRound.java:654`.

FANOUT's round, step 3: every fix ships with a test seen failing on the unfixed code.
- **The log line.** TDU3-C2's disposition names no test, and there is none: nothing under `test/` names `noteADroppedAnswer` or `autolayout.ui.logTailAnswerDropped`.
- **The facing.** TDU3-C1's disposition credits R3g-R3i, which are its gate half. The facing half has no mutation and no assertion. The late-answer claim and pins assert only the road, and `testAPasteDoesNotTurnTheTrainRound:654` looks for `session.setFacing(tile, facingChosen != null ? facingChosen`, which is still there if the write is moved back below the question and guarded.

Both are the stated intent now - behaviour.md 5c: *"Otherwise the answer is dropped and the log says so, naming the train and the square; the facing was written before the question was asked, so only the road waits for it."* MT-576 step 7 checks the first by hand; nothing checks the second.

**Consequence.** None today; either can be undone with every class green. C.

**Verification request.**
- Mutation A: delete the `else { ... noteADroppedAnswer(...) }` branch at `TrainControlUI.java:8195-8198`. Run `regression.testTheTailIsPickedOnTheDiagram` and `core.testMessageBundles`.
- Mutation B: move `session.setFacing(tile, ...)` (`TrainControlUI.java:8152-8154`) below the `whereTheAnswerGoes` call, inside `if (landing != null)`. Run `regression.testTheTailIsPickedOnTheDiagram`, `core.testAPasteDoesNotTurnTheTrainRound`, `core.testAPastedTrainKeepsItsDirection` and `ui.testAPastedTrainFacesTheWayTheOperatorChose`.
- Proves it: all green under each.
- Refutes it: any red.
- Claims to add: in `testALateAnswerIsNotWrittenForTheOtherSide`, whose answer is dropped, that the setup's facing for Tunnel is the paste's all the same; and one assertion that a dropped answer leaves the log line naming the train.

---

### TDD4-C6 - MT-577 step 1 sends Adam to a Manage Locomotive... submenu on the locomotive list; that submenu exists only on a keyboard button's right-click

| **Disposition** | Fixed - a comment on MT-577 with both real paths. |
|---|---|

**Where.** `tests.md:28302`; `RightClickMenuListener.java:186-218` (a locomotive button: Manage Locomotive... > Customize Function Icons); `RightClickSelectorMenu.java:54` (the locomotive database list: Customize Function Icons at the top level); `messages.properties:593`, `:608`.

Step 1 reads: "Right-click a locomotive in the locomotive list, open Manage Locomotive..., and choose Customize Function Icons."
- `loc.ui.submenuManageLocomotive` is used once, by the keyboard buttons' menu.
- The locomotive database list offers Customize Function Icons directly, with no submenu.
- In this tracker "the locomotive list" is the Autonomy tab's (MT-499, MT-571's comments), which offers neither.
- Adam's own words on MT-466, "manage locomotive -> customize function icons", are the button's path.

**Consequence.** A step naming a submenu that is not where he is sent. Either real path opens the same dialog with Apply shown. C.

**Fix.** A comment on MT-577: right-click the locomotive's button on the keyboard, Manage Locomotive..., Customize Function Icons - or, in the locomotive database, Customize Function Icons.

---

### TDD4-D1 - TDD3-B1 (a comment on MT-573) holds

| **Disposition** | Checked - clean. |
|---|---|

- The comment (`tests.md:28208`) sends the train away **before** step 1. The exit capture writes it there, so the step-1 backup holds it away.
- Steps 2 to 4 refuse without moving it. Step 5 restores a backup that agrees with the railway, and after a restart the model has it away from home, so Return Home is offered and brings it back.
- It says it replaces the comment before it, which is how an append-only entry corrects itself.

---

### TDD4-D2 - TDD3-C1 (pin testARouteIsTimedFromTheFarEndOfAPlace) holds

| **Disposition** | Checked - clean. |
|---|---|

- **The route is the finding's own:** one edge `[OT:Q5 1, OT:D5 2, OT:E5 3, OT:D5 0]`, no body.
- **At HEAD** (traced through `Layout.java:10458-10534`): D is left at 3 and returned to at 6. The route has measured 3 since, so the return is judged, and the figure is 3. No stretch is counted, because leaving and return share one edge. A four-unit train is refused naming 3 (" 3 " is in "after only 3 units"); a three-unit train is clear.
- **Under R3l** (the `put` above `travelled +=`, `make_mutr3.py`): D's free point is 1, the figure 5, and the four-unit train goes, so `assertNotNull` fails. `mutr3.out` records it red on this method alone.
- It is a pin: green at `6bfab5bb` (`r3red.log`, 6 run, 0 failed), and the disposition calls it one.

---

### TDD4-D3 - TDD3-C2 (the third renamed item) holds

| **Disposition** | Checked - clean. |
|---|---|

- The seven sites in `testMassAssignLengths` now say Clear All Station Max Train Lengths, as `messages.properties:377` labels it.
- No "Clear All Max Train Lengths" is left in `src/`, `test/`, the reference documents or `Automation.md`. The remaining uses are the older MT-457, MT-518 and MT-519 titles and FR-092, which TDD-C6 answered with comments giving the label.

---

### TDD4-D4 - TDD3-C3 (a comment on MT-568) holds

| **Disposition** | Checked - clean. |
|---|---|

- The comment says not to click, and names the check as the message and the button.
- It gives the way back. A straight run cycles both ways, one way, the other way, closed (`AutonomyEditorPanel.java:7411`), so three more clicks restore it; Cancel discards the edit.
- Messages go to the strip across the top of the editor (`say`, `:591-603`), which is where the comment sends him.

---

### TDD4-D5 - TDD3-C4 (a comment on MT-570) holds

| **Disposition** | Checked - clean. |
|---|---|

- The comment names `regression.testTheEditorSaysWhatItsToolsDo.testWhyNotMovingFollowsTheRunningRailway`, which exists (`:160`).
- That test builds a railway whose train stands where the setup has none, which is step 1's case.

---

### TDD4-D6 - TDD3-C5 (Automation.md round a loop) holds, with a note

| **Disposition** | Checked - clean. |
|---|---|

- "When to set them" (`Automation.md:172`) now makes the loop the exception.
- The loop paragraph (`:166`) says an unmeasured way round is not checked, and that a stretch measured only at its switch is missed. Both match `Layout.java:10363-10371` and `:10414-10419`.
- The smaller point is answered too: "Of the rules that stop a train, this is the one that reads the track round a loop".
- **Note, not raised:** "or that trip is not checked" is the case with nothing measured round the loop. A reader who follows the first sentence measures stations and the run back from each, so a loop through them is partly measured. It is then checked on a short figure, and it refuses trains that would clear. The loop paragraph above says so ("measure them, and the trip may turn out to be long enough").

---

### TDD4-D7 - TDD3-C7 (three pins, one per condition) holds

| **Disposition** | Checked - clean. |
|---|---|

- **One condition each.** Each pin changes one condition of `placementStillStands` and keeps the other two as they were at the question:
  - the road alone: the same train, side N, TunnelPre's road, then Cancel;
  - the train alone: a second train, side N, no road, then a click;
  - the side alone: the same train, side S, no road, then a click.
- **Each is red under its own mutation**, on one method each: R3b the train, R3c the side, R3d the road (`mutr3.out`). Each spec deletes one term (`make_mutr3.py`).
- **They are pins.** `r3red2.log`, the last run before `6bfab5bb`, shows 2 of 12 failing. At that commit the claim `testAnAnswerAfterARebuildReachesTheRailway` and the source pin (re-pointed at `whereTheAnswerGoes`, not yet in `src/`) must fail, so the three pins passed on the unfixed code. `r3red.log`'s 4 failures are an earlier run of the file.
- **A rebuild alone does not drop an answer.** `Edge` and `Point` compare by name (`Edge.java:255-263`), so a road compared across a rebuild compares its edge names.

---

### TDD4-D8 - TDD3-C8 (testAGateThatFailsStopsEveryRunDoor) holds

| **Disposition** | Checked - clean. |
|---|---|

- **One method, one door at a time**, each failure naming the door.
- **The regions are the doors:** Start and Execute Timetable by their GEN markers; Return Home from `requestReturnToHome()` to the first four-space closing brace, which is its own (`TrainControlUI.java:23984-24221`).
- **What it asks of each:** the gate call is the first in each region, the catch comes within 400 characters, and the first block after the catch holds `return;`. Execute Timetable's must also hold `setEnabled(true)`.
- **Red first, for TDU3-C4's reason:** Execute Timetable's check failed (`r3red.log`: 10 run, 1 failed).
- **Each mutation is caught.** R3g, R3h and R3i each take out one of the three and are each red on this method (`mutr3.out`); each spec matches its door's catch uniquely.
- Start's substring assertion is gone from the ordering method.
- **Note:** Execute Timetable catches `RuntimeException` on the event thread; the other two catch `InvocationTargetException` from `invokeAndWait`, which wraps an `Error` too. The gate reads two lists and can hardly throw either.

---

### TDD4-D9 - TDD3-C9 (behaviour.md's run-in figure) holds

| **Disposition** | Checked - clean. |
|---|---|

- `behaviour.md:1471-1476` says a parking berth's number is the berth rule's where that stops first.
- It says what the notice gives with nothing measured before the crossing: 0 where the squares were answered, nothing where they were not. That is `AutonomySession.java:9846-9862`.
- The bullet beside it is TDD4-C3.

---

### TDD4-D10 - TDD3-C10 (the deferred count) holds

| **Disposition** | Checked - clean. |
|---|---|

- OB-297 is in the Inbox (`issues.md:1267-1274`) with its reason.
- `behaviour.md:1163-1165` and `Automation.md:166` now say the count can be short.
- The store row for TDA2-C1 reads Open, with a status note naming OB-297 in full rather than cut.
- `open-questions.md` counts OB-297 in the Inbox.

---

### TDD4-D11 - TDD3-C11 (testTheOwnTailArithmetic's MUTATION line) holds

| **Disposition** | Checked - clean. |
|---|---|

- The line names every claim and pin by method, with R2b to R2d and the near-end mutation. Each method it names exists.
- Its attributions match `mutr2.out` and `mutr3.out`.
- The first line names the round-2 and round-3 findings.

---

### TDD4-D12 - The records commit: counts, the store, the tracker

| **Disposition** | Checked - clean. |
|---|---|

- **The finding count.** The store holds 4,248 rows for 3,891 refs. The day's validation added 173 rows: TDA 19, TDU 24, TDD 28, TDA2 13, TDU2 17, TDD2 25, TDA3 10, TDU3 15, TDD3 22. 3,726 + 336 + 13 + 173 = 4,248, as `behaviour.md` and `open-questions.md` say.
- **Statuses.** All 47 round-3 rows are Closed. The eight rows of the day not Closed are the five decisions and the three follow-ups (OB-295, OB-296, OB-297).
- **A render will not drop anything.** The store's blocks for MT-531, MT-568, MT-570, MT-571, MT-573 and MT-576 to MT-578 appear verbatim in `tests.md`, so the new comments and entries survive.
- **The waiting table.** 45 fixed unvalidated and 2 needs test; 454 fixed validated and 77 superseded; 578 in all - "531 of 578".
- **The Inbox.** It holds 52 OB and 32 FR headings before "What has been picked up": 84.
- **Append-only.** The tracker diff only appends entries and comments, apart from the generated table.
- **Citations.** `rc3.log`: `testTheRecordsCountTheStore` and `testEveryCitationResolves` green after cataloguing.

---

### TDD4-D13 - The crossing fixture changed under four claims after their mutation run; by reading, none of them proves less

| **Disposition** | Checked - clean. |
|---|---|

- **The timing.** `59cf3645` (20:17) rebuilt `openBerthBehindACrossing` so the crossing's other road can be driven, and made 5,2 a station. The round's mutation run ended at 20:10 (`mutr3.out`), so R3e and R3f ran on the fixture before the change. Only a green class run (`live.log`) followed it, and the records commit says R3a-R3n red.
- **TDA3-C1's claim, traced on the new fixture.**
  - Answered zeros give `{0, 0, 1}` from `berthTrackBeforeTheStop` and the entry `{5, 0}`.
  - Unanswered squares give `{0, 2, 1}` and no entry.
  - R3e (the `continue` at `:9848` deleted) gives `{5, 0}` for the second half, which fails it.
- **The leg-with-no-switch pin.** The room walk still finds no switch on row 1, because a crossing does not bound it. R3f (`room > 0 &&`) skips the berth, and the pin fails.
- **The older claims read only row 1.** TDA-C7's count and TDA2-C6's run-in ask about tiles along row 1 (`endsTheBerthsRoom` asks the tile type), which the change did not touch. The new station at 5,2 is on another leg.
- **The premise is asked now.** `testTheBerthRuleRefusesAtTheCrossing` asks the rule itself on the built railway. It failed first on the old fixture (`r3txt.log`), which proved TDA3-C2's point that the premise was unasked. The limit it exposed is stated (`open-questions.md:168`, `AutonomySession.java:3886`).
- **For the closing run:** R3e and R3f are worth running again on this fixture.

---

### TDD4-D14 - OB-286: the claim, the fix, MT-578

| **Disposition** | Checked - clean. |
|---|---|

- **The claim.** `testAThreeWayIsLitOnce` takes a three-way on the frozen railway's 1 - Main that answers to its second decoder. The route commands the first decoder and checks the second, and the test compares the label's pixels with the commanded wash. It was red first (`r3red.log`) and is red under R3k.
- **The fix.** The checked wash now leaves out every square the commanded addresses reach, in either order of the two decoders (`highlightAccessories` with `notThese`), so the square is counted once. `RouteEditorFrame` is the only caller.
- **MT-578.** The tooltip gives both addresses (`layout.switchThreeWayAddr`, `LayoutDiagramComponent.toSimpleString`). "Yellow" and "orange" are the button's own tooltip (`messages.properties:875`).

---

### TDD4-D15 - FR-098: the claim, and MT-577's steps 2 to 4

| **Disposition** | Checked - clean. |
|---|---|

- **What it compares.** `somethingToApply` compares the icon, the trigger and the custom picture on show with what the locomotive holds for that function.
- **When it is asked again:** on either list changing, on another function being shown, after Apply, and on choosing or clearing a picture.
- **Both directions are claimed.** Apply is greyed on opening, live on a change, greyed when changed back and after Apply, and live when a picture is taken off. The claim was red first and is red under R3j.
- **A greyed Apply loses nothing.** The two lists and the picture buttons do not write through before Apply. Copy Customizations does, and Apply then correctly has nothing to add.
- **MT-577.** Steps 2 to 4 match the code: Apply moves on to the next function and greys again. Step 1 is TDD4-C6.

---

### TDD4-D16 - OB-287: the test

| **Disposition** | Checked - clean. |
|---|---|

- **The two doors a person uses** are driven on a real window while a route runs that fires a function on the locomotive. Each must show the refusal naming the route, and the locomotive must survive.
- **Each door is caught alone.** R3m and R3n each remove one door's call and are each red (`mutr3.out`).
- **Nothing reaches a station.** The model is simulated (`init(null, true, ...)`), so the route's command goes nowhere.
- **The name proposal** is a source pin: both names are asked (`TrainControlUI.java:26312`, `:26314`) before the only `renameLoc(` after them (`:26330`).
- Only the receipt is wrong (TDD4-C1).

---

### TDD4-D17 - The other text of 59cf3645

| **Disposition** | Checked - clean. |
|---|---|

- **Door counts agree.** Start's handler ("THE THIRD DOOR ... the file door"), the claim's javadoc ("The other two doors ... the file door") and `testAutoLayout`'s "seven ... the checkbox, the load door, and the five that dispatch a train" all agree with the gate's javadoc: six callers, plus the checkbox. No "both file doors", "both load doors" or "fourth door" is left about this gate.
- **The hand door's comment.** `AutoLocomotiveStatus`'s comment names every door that moves a train.
- **The bundles.** The Mass Assign label in da, de and fr now matches the menu. Every bundle has zero non-ASCII bytes.
- **open-questions.md** carries TDU2-C3's recommendation, and the crossing limit under Limits.
