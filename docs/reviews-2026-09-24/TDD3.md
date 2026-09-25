# TDD3 - Documents, the tracker, and the tests themselves (round 3, validation)

**Status:** open

**Prefix:** TDD3

**Validated:** branch `autonomy-diagram-r0` at `67f79912`, 2026-09-24. The range is `7b0d5c2f..67f79912`, 18 commits. Nothing was run: no JVM, no test, no compiler, no `one.sh`, `battery.sh` or `mutate.py`, and no git command that changes state. `cs2_sample_layout/` was not opened. The only file written is this one.

**Method.** I read `docs/reviews/FANOUT.md` and `docs/reviews/README.md` first, then `docs/reviews-2026-09-24/TDD2.md` with its dispositions, and `TDA2.md` and `TDU2.md` for the eight TDD2 findings dispositioned "as" one of theirs. I read `git show` of every commit in the range, then the code and documents as they stand at HEAD: `Layout.whyItWouldMeetItsOwnTail`, `walkStandingTrains` and `walkOneTail`, the `isPathClear` tail block, `anotherTailOn`, `TailCrossedPrompt.placementStillStands` and `takesTheClick`, the three placement doors, `LayoutLabel`'s sensor click, Start's and Return Home's workers and Execute Timetable's handler around the Atomic Routes gate, `AutonomySession.berthTrackBeforeTheStop` and both berth notices, `AutonomyEditorPanel.refresh`, `setPageExcluded` and the plain-click `cycle`, and the rebuild and capture paths in `TrainControlUI` (`rebuildRunningLayoutFromSetup`, `whereTheTrainsAre`, `captureRunningLayout`). I read these tests in full or in the parts the range touched: `testTheOwnTailArithmetic`, `testATailIsNotHiddenByAnother`, `testTheTailIsPickedOnTheDiagram`, `testNonAtomicRoutesNeedTheirLengths`, the TDA2-C6 and TDD2-C7 parts of `testMassAssignLengths`, `testTheEditorSaysWhatItsToolsDo`, `testTheLengthPromptHasTheKeyboard`, and the OB-285 claim's header. On the documents side I read the range's diffs of `Automation.md`, `behaviour.md`, `open-questions.md` and `tests.md`, then MT-568, MT-570, MT-571 and MT-573 in full, `Automation.md`'s track-length section in full, and behaviour.md's 5c tail bullets and the run-in notice bullets. I queried `docs/manual-tests/triage.db` with `sqlite3` in `mode=ro` (finding rows, statuses, status notes, the `test` blocks and `verdict` rows for the four MT entries) and grepped `findings.tsv`. In the session scratchpad I read, without changing anything, the round's mutation records (`mutr2.json/.out`, `mutr2b.json/.out`, `muts.json/.out`), its run logs (`a1r`, `a1g`, `a1p`, `dc`, `gt`, `gg`, `ot2`, `ot3`, `tt`, `cl`, `c6r`, `c6f`, `c7r`, `c7f`, `c13`, `c294b`, `cm`, `rc2`), the census probes (`probeOwnTailCensus.java`, `probeOwnTailCensus2.java`, `c294.txt`), and the scripts behind two fixes (`c13_names.py`, `catalog_r2.py`). Arithmetic on the own-tail routes was traced by hand against the code. Where a finding rests on behaviour I could not see by reading, it carries a verification request.

Grades are by consequence on the railway: A wrong behaviour on the layout or data lost, B incorrect results in a specific configuration, C text, a narrow case, or a guard that proves less than it says, D checked and clean.

---

### TDD3-B1 - MT-573's new comment moves a train between the step-1 backup and the step-5 restore, so the restore puts the setup's record of it back at the station it left; "send it back or press Return Home" then cannot work

| **Disposition** | Fixed - a comment on MT-573 (round 3's tracker commit) that replaces the one before it: send one train away before step 1, so the backup records it there; after step 5 and a restart, Return Home brings it back. |
|---|---|

**What TDD2-C9 and TDU2-C6 asked, and what was written.** Return Home is greyed when every train is home, so MT-573 step 3 could not be done. The comment added in `244331ce` (tests.md:28193) says: before step 2, send one train by hand to another station; "After step 5, send it back or press Return Home."

**The steps it sits in** (tests.md:28178-28182):
1. Close TrainControl and copy `config/autonomy` somewhere safe.
2. Break the setup (untick Exclude Page on 4 - Combined, close the editor saving).
3. Press Return Home. 4. Execute Timetable.
5. Close TrainControl and copy the saved `config/autonomy` back.

**Why the order goes wrong.**
- Step 1 closes TrainControl, so the train is sent after the backup: the copy has it at its first station, A.
- Where each train stands lives in the setup in that folder. The editor's open and close and the exit all capture the running layout's placements, sides and roads into it (TrainControlUI.java:6827-6839, Adam: *"on exit or editor load or editor close, save to the setup file"*; roads captured since WK7-B1, AutonomySession.java `captureFromLayout`).
- After step 2 the hand doors refuse (MT-263, TDU-B1), so the train cannot be brought back before step 5.
- Step 5's copy-back therefore writes the train back at A while it stands at B. The next start builds the running layout from that file.
- behaviour.md section 6 names this state: *"occupancy is the model's own record rather than the sensors: the next dispatch could then be routed into a block that is physically occupied."*
- The comment's last sentence cannot be followed. After the restart the model has the train at A, its home, so Return Home has nothing to do for it. "Send it back" sends a train that the model has in the wrong place.

**A smaller point.** "After a restart every train is at home" is true only of trains with no home set. Homes can be set from the diagram's right-click menu (the rebuild comment at TrainControlUI.java:6671). The instruction to send one away still works.

**Consequence.** Following the entry as written leaves one train recorded at the station it left. Another train can then be routed into the block it really occupies, and the train itself can be driven along a route planned from the wrong station. Graded B:
- The step is Adam's own copy.
- The diagram draws the train at A while B's sensor is lit.
- It would be A if he dispatched before noticing.

**Fix.** Another comment on MT-573. After step 4, tick Exclude Page on 4 - Combined again and save, so the setup mends. Then send the train back, or press Return Home, and only then do step 5. Alternatively, say that after step 5 the train must first be placed on the square it really stands on, before anything moves.

**Verification request.**
- Fixture: a live-snapshot sandbox. Keep the setup files' bytes, place train X at station A, and move it to B on the running layout. Run the editor-close capture (`captureRunningLayout`), then write the kept bytes back and rebuild the running layout from the setup, as a start does.
- Proves it: X stands at A.
- Refutes it: X stands at B, which would mean some start-up step reconciles placements from somewhere else.

---

### TDD3-C1 - TDD2-C2's disposition holds only for the deletion: the near-end mutation it named first survives, because the pin's return square has no length

| **Disposition** | Fixed - pin testARouteIsTimedFromTheFarEndOfAPlace in 6bfab5bb, the finding's own route: 4 refused naming 3, 3 clear.  Mutation R3l (the near end) red. |
|---|---|

**What TDD2-C2 asked.** It named two mutations of the route-place timing:
1. Take a route place's free point at its near end: move `freeOnceTheTailPasses.put(place, travelled)` above `travelled += ...` (Layout.java:10517-10519).
2. Delete the line.

It gave a fixture that separates the first: one edge over Q, D, E, D measured 1, 2, 3, 0.

**What was done.** The disposition is "Fixed - as TDA2-C3". That is the pin `testARouteThatComesBackOverItselfIsJudged` (testTheOwnTailArithmetic.java:147-163) with R2b. R2b is the deletion only (`mutr2.json`), and no near-end mutation was run.

**Why the pin cannot see the near-end mutation.** The pin's loop returns to the switch square, `OT:SW`, measured 0. For a place with no length the near end and the far end are the same point.

Traced by hand, under the mutant:
- SW's return still names 8 - 2 = 6.
- Q2's return grows from 6 to 8. The tie was already broken in SW's favour.
- So 7 is still refused naming 6, 6 is still clear, and the class stays green.

**What the mutant does on a real route.** A train returning to a measured square of its own route - a crossing on a figure of eight, or a loop's switch that carries a length - is timed from the square's near end. The figure grows by that square's length, and a train that long is cleared into its own tail. The code is right today, so this is a missing guard, as TDD2-C2 was.

**Verification request.**
- Mutation: swap the two statements at Layout.java:10517 and 10519. Run it against `core.testTheOwnTailArithmetic` and `core.testATrainDoesNotRunIntoItsOwnTail`.
- Proves it: green.
- Claim to add: TDD2-C2's own route. One edge `[OT:Q 1, OT:D 2, OT:E 3, OT:D 0]` with an empty body. A 4-unit train is refused naming 3, and a 3-unit train is clear. Under the mutant the refusal names 5 and the 4-unit train goes.

---

### TDD3-C2 - TDD2-C13's disposition says every comment and test message names the renamed items as labelled; the third item, Clear All Station Max Train Lengths, still goes by its old name at seven sites, five of them ones TDD2-C13 listed

| **Disposition** | Fixed - 6bfab5bb: the seven sites name Clear All Station Max Train Lengths. |
|---|---|

`ef1adfb6` was made by `c13_names.py`, which rewrites only "Mass Assign ... Max Train Lengths" and "Mass Assign ... Train Lengths". The bundle's label is `Clear All Station Max Train Lengths ({0})` (messages.properties:377). "Clear All Max Train Lengths" remains in `core.testMassAssignLengths` at these lines:
- :750 (javadoc);
- :799 (`"the Bulk Tools menu has no Clear All Max Train Lengths item"`);
- :1558, :1971, :2740 and :2772 (failure messages);
- :1947 (javadoc).

TDD2-C13 named :750, :799, :1923, :1947 and :2687 at `7b0d5c2f`. Those are now :750, :799, :1947, :1971 and :2772, all unchanged.

**Consequence.** A red names a menu item Adam cannot find, as TDD2-C13 said. C.

---

### TDD3-C3 - MT-568's new comment tells Adam to click a square with no tool pressed, which changes which way trains may run over that stretch of 1 - Main; and it describes the result as "nothing"

| **Disposition** | Fixed - a comment on MT-568 correcting the one before it: do not click a square; the check is the message, and a clicked square is clicked back or the editor cancelled. |
|---|---|

**The comment** (`244331ce`, tests.md:28044) ends: *"Untick Exclude Page and click a square: nothing waits for a second click."* This is TDD2-C8's suggested step 2, taken over as written.

**What that click does when the fix works.** With One-Way Run put down, the tool is NONE. A left-click on a track square then falls to `default: cycle(tile)` (AutonomyEditorPanel.java:7390-7397). That cycles the whole run through both ways, one way, the other way and closed (javadoc :7407-7419). The hint the fix itself puts up says so: *"Click any piece of track to change which way trains may run"* (messages.properties:1639).

So the check, done as written on 1 - Main, turns one run of Adam's main page one-way:
- An arrow appears, and the message says what the run became.
- Under the bug the same click would name the first end instead.
- The comment's expected result, "nothing", describes neither case.

**Consequence.** Visible, and not saved until the setup is saved. If Adam closes the editor with Save, as MT-573 has him do, autonomy stops routing that way over the run. C.

**Fix.** A further comment. The check is the message line reading "Click any piece of track to change which way trains may run", with One-Way Run up. Do not click, or if a square was clicked, click it three more times to bring it back to both ways, or close the editor with Cancel.

**Verification request.**
- Fixture: `core.testMassAssignLengths`' One-Way fixture after the exclude-and-untick, then one `MOUSE_CLICKED` on a straight track square.
- Proves it: the run's stored direction has changed.
- Refutes it: unchanged.

---

### TDD3-C4 - TDD2-C8's second half, MT-570's citation, was dropped without a word

| **Disposition** | Fixed - a comment on MT-570 naming testWhyNotMovingFollowsTheRunningRailway. |
|---|---|

**What TDD2-C8 said.** *"MT-568's and MT-570's 'What this is' still name only the old claims."* For MT-570 the missing claim is `testWhyNotMovingFollowsTheRunningRailway` (TDU-C11's pin).

**What the disposition covers.** Only the MT-568 comment. MT-570 (tests.md:28072-28097) still names only `testWhyNotMovingOutlinesTheTrains`, and its one comment does not name the pin.

MT-570's step 1 is exactly the question that pin answers: "every square with a train on it is outlined", where the setup and the running railway can disagree.

**Consequence.** None on the railway. The entry sends a reader to a claim that does not cover the case TDU-C11 fixed. C.

**Fix.** A comment on MT-570 naming the pin, or record the point as declined.

---

### TDD3-C5 - Automation.md's new loop paragraph sits above "When to set them", which still says every rule above asks only about the station stretch and that plain running line is not worth measuring; neither is true of the loop rule

| **Disposition** | Fixed - 59cf3645: When to set them says to measure round any loop a train can be sent round, and the loop paragraph says an unmeasured loop is not checked and the count can miss a stretch measured only at its switch. |
|---|---|

**The paragraph `244331ce` added** (Automation.md:166, TDD2-C11) is correct as far as it goes. But the section that follows it was not touched (:170-172): *"Measure the squares a train comes to rest on, and the run back to the switch behind each one. That is the stretch every rule above asks about. ... there is no benefit in measuring plain running line that nothing stops on."*

**What the loop rule reads instead.** It reads the way round - the running line of the loop - and binds only where that is measured (Layout.java:10363-10371; behaviour.md 5c: *"a return is judged only where the route itself ... has measured something"*). A user who follows the guide gets one of two results:
- The return is judged on the few measured squares only. It is refused with a figure far below the loop's real length, and the note then points at the unmeasured stretches the guide has just said are not worth measuring.
- Nothing on the way round is measured, so the return is not checked at all.

The new paragraph frames measuring only as the way to be allowed ("the trip may turn out to be long enough"). It never says it is also what turns the check on.

**A smaller point.** "This is the one rule that reads the track round a loop" overlooks the paragraph just below it: Over the shortest and Over the longest track read every section of a route.

**Consequence.** Text. Other operators, not Adam: his railway is measured throughout. C.

**Fix.** In "When to set them", add that the loop rule is the exception: measure the track round any loop a train can be sent round and back onto its own line. And in the new paragraph, say that with nothing measured round the loop the rule does not check it.

---

### TDD3-C6 - TDU2-A1's new rule, that a tail question's answer is written only while the placement still stands, is in no document and has no tracker entry

| **Disposition** | Fixed - behaviour.md 5c states the rule (59cf3645), and MT-576 is its hands-on test. |
|---|---|

**What changed.** `5e1b0623` changed what all three placement doors do. An answer given after the square changed - after a run, another placement or a removal - is dropped: a click on a lit sensor, Not known or Cancel then writes nothing, and nothing says so.

**What the documents say.** behaviour.md 5c's tail-question paragraph (:1101-1107) still describes only TDU-B2's half: *"the paste reads everything about its landing before the question waits, because the window stays live while it does"*. No MT entry covers it, although TDU2-A1 was graded A and its own verification request gave the railway check:
- 75 407 DB pasted at Tunnel from the north, the question left open;
- the train hand-sent away, and a second five-unit train hand-sent into Tunnel southbound;
- then Cancel, and the grey behind Tunnel runs on to TunnelPre.

FANOUT's round, step 5, asks for a tracker entry for anything Adam has to see on the railway. Round 1's TDU-B3 and TDU-C1 got theirs (MT-574, MT-575).

**Consequence.** Text and the tracker. An operator who answers late sees his answer do nothing, and neither the intended behaviour nor a hands-on test says that is right. C.

**Fix.** A sentence in behaviour.md 5c after the TDU-B2 clause. Also an MT with the steps above, whose expected result is that the second train's grey reaches TunnelPre.

---

### TDD3-C7 - TDU2-A1's claim changes the train and the road at once, so each of the three conditions in `placementStillStands` can be deleted with the class green

| **Disposition** | Fixed - pins in 6bfab5bb, one per condition: the road alone, the train alone, the side alone.  Mutations R3b, R3c, R3d red. |
|---|---|

**The rule** (TailCrossedPrompt.java:536-542) has three conditions: the copy still holds the placed train, by reference; its side is unchanged; its road is unchanged. The disposition says the door writes *"only while its placement still stands - the train, the side and the road it left."*

**The claim's fixture changes two of the three at once.** `testALateAnswerDoesNotWriteOverAnotherTrain` (testTheTailIsPickedOnTheDiagram.java:499):
- It puts train B on the copy in place of A.
- It gives the copy TunnelPre's road, where A's question saw none.
- It keeps the side, "N".

So:
- Deleting the train condition leaves the road condition to refuse, and the reverse.
- Deleting the side condition changes nothing the fixture can see.

S1 to S3 (`muts.json`) replace the whole call or the whole body. No single condition was mutated. The source pin `testEveryPlacementDoorAsksWhetherItStillStands` asks only that the call comes between the question and the write.

**What each condition alone protects**, by TDU2-A1's own cases:
- The road alone: the same train sent away and brought back onto the same copy by a run. A late Cancel writes no road over the road it drove in by, and its tail stops at the switch with another train routable into it.
- The train alone: a second train placed by hand on the copy with no road. The first door's answer writes the first train's facing over it.

**Consequence.** None today - the code is right. A guard that reads as covering three things covers one. C.

**Verification request.**
- Mutations: delete each of the three `&&` terms in turn, and run each against `regression.testTheTailIsPickedOnTheDiagram`.
- Proves it: green for all three.
- Claims to add, both with the claim's scaffolding:
  - Same train, new road: move A away and back onto Tunnel southbound, give the copy TunnelPre's road, then Cancel. The road must stand.
  - Different train, same road: B put on the copy with no road, then Cancel. B's facing in the setup must stand.

---

### TDD3-C8 - TDU2-C2's "a gate that fails stops Start" claim was never seen red, and "stops Return Home" has no claim at all

| **Disposition** | Fixed - testAGateThatFailsStopsEveryRunDoor in 6bfab5bb names each door; Start's old assertion is taken out of the ordering method.  Mutations R3g, R3h, R3i red. |
|---|---|

**Why the Start claim was never seen red.** `49a4f0b7` added three assertions to two methods of `ui.testNonAtomicRoutesNeedTheirLengths`. In `testStartAndReturnHomeAskTheGateAfterTheirRefusals`, the Return Home ordering assertion (`homeGate > possible`, :638) comes before the Start assertion (:662). TestNG stops a method at its first failure.

Before `aee9d2bf`, Return Home's gate sat above `plan.isPossible()`, so the method failed at :638 and never reached :662. `gt.log` (18:19:50) is 9 run, 2 failed: one per method. So the red recorded for "a gate that fails stops Start" is really the Return Home order's.

**What the Start assertion checks.** It is a substring test: any `return;` between the gate call and `started.set(true)` passes it.

**What has no claim.** The same commit gave Return Home's worker a new `catch ... { log; return; }` (TrainControlUI.java:24076-24081), and nothing claims it. No mutation of TDU2-C2 is in the round's record (R2a-R2j).

**The fix itself is right.** TDD2-C17's disposition holds: Start now returns on a failed gate (:26991-26996), and Return Home's `finally` hands the timetable and buttons back.

**Consequence.** None today. C.

**Verification request.**
- Mutations: delete the `return;` in Start's catch (TrainControlUI.java:26995); separately, delete the one in Return Home's (:24080). Run each against `ui.testNonAtomicRoutesNeedTheirLengths`.
- Proves it: Start's is red and Return Home's is green - the second is the missing claim.
- Better: split Start's assertion into its own method so its red can be seen alone, and add the Return Home twin.

---

### TDD3-C9 - behaviour.md's run-in notice bullet still says the notice quotes the room to the last switch; since `84a89426` a parking berth's notice quotes the berth rule's room to a crossing

| **Disposition** | Fixed - 59cf3645: behaviour.md's run-in bullet says a parking berth's figure is the berth rule's, and what it says with nothing before the crossing. |
|---|---|

**What behaviour.md says** (:1464): *"The room is `ReducedEdge.getRoomAtTheEnd()`: the track from the last switch on the arriving edge to the platform ... A notice quoting a number the refusal would not quote sends the reader to measure the wrong stretch."*

**What changed.** TDA2-C6's fix (AutonomySession.java, `runInsShorterThanTheBerth`) makes a parking berth's notice quote the measured track before the berth rule's stop, where a crossing lies nearer than the switch. The javadoc says so; the document Adam reads as the intended behaviour does not.

**A sentence that now reads too broadly.** behaviour.md's *"A crossing still does not stop the walk"* (:1631) is right about the room walk. Read next to the notice bullet, it now says the opposite of what a berth's notice does.

**Consequence.** Text. The case is unreachable on the frozen railway (TDA2-C6's mitigation). C.

**Fix.** One sentence under the notice bullet: for a parking berth the number is the berth rule's, which stops at a crossing between the berth and its switch.

---

### TDD3-C10 - TDA2-C1's deferred remainder is kept only in a store note cut off before it says what is deferred; behaviour.md 5c and Automation.md still promise the count it cannot always give

| **Disposition** | Fixed - filed as OB-297 in issues.md with its reason; behaviour.md 5c and Automation.md say the count can be short (59cf3645). |
|---|---|

**The disposition** is "Fixed in part ... Counting pieces needs the build to mark switch places in the configuration; left for later."

**Where it is recorded.**
- The store row is `Open - deferred`, and its `status_note` is the disposition cut at 120 characters: *"... the note counts the stretch the place was left in, where the r"* (triage.db). `findings.tsv` carries a shorter cut.
- The remainder is not in `issues.md`, where GUI2-C4's deferral went (OB-286).
- It is not under open-questions.md's Limits, which is for deliberate under-claims.

Once the round's documents are deleted as FANOUT's closing says, only the parent commit says what was deferred.

**What the documents promise.** behaviour.md 5c says *"where stretches of it have no length the refusal says how many"*, and Automation.md:166 says *"the message says how many stretches"*. The rule's javadoc (Layout.java:10414-10419) says instead that between Mass Assign sittings the note *"can say fewer than there are, or nothing"*.

**Consequence.** None on the railway. The limit is written only where the operator does not read it, and the deferred work only in a document due for deletion. C.

**Fix.** File the remainder in `issues.md` with its reason, or put it under Limits in open-questions.md. Qualify the two promises.

---

### TDD3-C11 - `testTheOwnTailArithmetic`'s class MUTATION line points at claims by position, and the two new claims went in above them

| **Disposition** | Fixed - 6bfab5bb: the class MUTATION line names each claim, R2b-R2d included. |
|---|---|

**The line.** The class javadoc (testTheOwnTailArithmetic.java:27-28) says: *"judge a return whose way round has nothing measured on it, and the first claim fails; refuse at the first return rather than the tightest, and the second does; leave the unmeasured stretches out of the sentence, and the third."*

**What changed under it.** `f652c14f` put `testARouteThatComesBackOverItselfIsJudged` and `testTheStretchTheTrainComesBackInIsCounted` first in the file. Read in order, "the first claim" is now the loop pin, which OT1 does not turn red, and "the second" is the returning-stretch claim, which OT2 does not either. The class's first line also still names only the round-1 findings.

**Consequence.** Text. At closing, FANOUT's mutation run is written from lines like this one. C.

**Fix.** Name the claims rather than count them, and add R2b, R2c and R2d.

---

### TDD3-D1 - TDD2-C3 (as TDA2-C1): the claim is the finding's own route, and was red first

| **Disposition** | Checked - clean. |
|---|---|

- **The claim is TDD2-C3's example.** The first half of `testTheStretchTheTrainComesBackInIsCounted` is TDD2-C3's own example: `[Q:1]`, `[R:0, B:0]` with B 3 behind the head, and a 20-unit train. Traced: B's return is judged at travelled 1, figure 4. The between-loop is empty, and the returning edge now counts (`leftIn < i`, `runInThisEdge` 1, edge unmeasured).
- **The second half** is the leaving-edge case: `[P:0, U:0]`, `[M:5]`, `[P:0]`, empty body.
- **Mutations.** R2c and R2d each remove one counter, and each turns the method red (`mutr2.out`).
- **Red first.** `ot2.log` (18:22) shows the class 5 run, 1 failed, before `baf8856c`. The test-file lines `baf8856c` touched are a javadoc only.
- **No null risk in the new map.** `leftAtTheEdgeEnd.get(place)` is reached only when `leftIn >= 0`, and it is put and cleared with `edgeWhenLeft`.

---

### TDD3-D2 - TDD2-C4 and TDD2-C5 (pins `7c7a0fb0`) hold

| **Disposition** | Checked - clean. |
|---|---|

- **The third train is TDD2-C4's fixture.** It stands unmoved on a rail of its own (`A1:T`, `A1:c`), sharing nothing with the switch.
- **R2e and R2f** (the survivor A1b and `muta1b`) are each red on both Return Home methods (`mutr2.out`).
- **Under R2e, the failure is at the controls.** The pre-existing control in the second method fails first, because the unmoved third train is charged with the first train's tail. The sensor method fails at its both-moved control.
- **Under R2f, the failure is at the sensor claims.** One owner per place frees the switch's sensor when that owner moves.
- **The sensor pin is order-free.** It asserts both one-moved maps, so it does not depend on which train the walk records last.
- **No pre-existing claim loses meaning.** The third train's rail touches nothing the earlier claims read.

---

### TDD3-D3 - TDD2-C6 (as TDA2-C5, `3a8813ca`) holds

| **Disposition** | Checked - clean. |
|---|---|

- **The three checks are gone.** `isPathClear` builds its maps locally with the routed train filtered out (Layout.java:2690), so none of the three removed `equals(loc)` checks could fire, and none remains.
- **The OB-285 paragraph** now says the walk excludes the train (:2694-2701).
- **`tailsOn` is deleted.** Nothing in `src/`, `test/` or `docs/` outside the review folder names it.
- **The source pin still matches.** `testHomeStaging`'s pin on `lyingAcross = anotherTailOn(e, loc, coveredPlaces);` still matches, and `anotherTailOn` keeps its mover argument because HomeStaging shares it.
- **The planner's prefix-closed reason is true.** Every return is kept, so an extension's tightest figure can only fall.

---

### TDD3-D4 - TDD2-C7 (claim `5b656ebe`, fix `4776026a`) holds

| **Disposition** | Checked - clean. |
|---|---|

- **The tool assertion is a pin.** It reads `tool` by reflection and was green at the claim. R2h - `tool = Tool.NONE` deleted with the button still put down - turns it red (`mutr2b.out`), which is the mutation TDD2-C7 said survived.
- **The hint assertion was red first.** `c7r.log` (18:45) shows 1 failure before the fix. The fix writes `hintClickToCycle`, the hint the editor shows with no tool.
- **Its consequence for MT-568's hand check** is TDD3-C3.

---

### TDD3-D5 - TDD2-C12 and the round's counts hold

| **Disposition** | Checked - clean. |
|---|---|

- **Every open decision is in open-questions.md.** The store has five rows `Open - Adam's decision`, and each is in open-questions.md under the section the disposition names:
  - TDA-C8 under Reversals;
  - TDA-C10 and TDU-C6 under Length;
  - TDD-C11 and TDU2-C3 under the new Setup and start-up section.
- **The follow-ups are there too.** OB-295 (TDA-C9) and OB-296 (TDU-B4) are carried as well, and TDU-B1 is under decided-while-away.
- **The counts agree with the store.** It holds 4,201 rows for 3,844 refs, and behaviour.md and open-questions.md now say so. 3,726 + 336 + 13 + 126 = 4,201, and 126 = 71 + 55. `findings.tsv` has the 55 round-2 rows.
- **The store will not erase the new comments.** The three new MT comments are in the store's `test.block` for MT-568, MT-571 and MT-573, so a render will not remove them.
- **A note for closing.** TDD2.md's own status line reads `open` while every TDD2 row is Closed in the store. With TDD3-B1, C1, C2, C3 and C4 above, `reopened` naming TDD2-C2, C8, C9 and C13 is what it should read.

---

### TDD3-D6 - TDD2-C14 (as TDA2-C7, `84a89426`) holds in all eight bundles

| **Disposition** | Checked - clean. |
|---|---|

- **Each bundle names the switch or crossing, and no single rule.** In each of the eight, `checkHalfMeasuredApproach` says the length rules, not one rule, and names the switch or crossing ("Weiche oder Kreuzung", "l'aiguillage ou le croisement", "lo scambio o l'incrocio", and so on).
- **The berth run-in says switch, crossing or reversal**, and the platform run-in still says switch or reversal. That matches the two rules.
- **Encoding and placeholders are sound.** Every file has zero non-ASCII bytes. The three changed keys carry the same placeholders in every language.
- **The full stop is in both places.** The notes after a refusal now follow ". " at both sites.

---

### TDD3-D7 - TDD2-C15 (pin in `ef1adfb6`) holds

| **Disposition** | Checked - clean. |
|---|---|

- **What the pin asserts.** Each guard item's tooltip, with its tags stripped and its whitespace collapsed, must start with the first 40 characters of its own key's text.
- **Mutation.** R2i gives the exit item the entry text, and it turns the pin red (`mutr2b.out`).

---

### TDD3-D8 - TDD2-C16 (as TDU2-C1, `fea75279`) holds

| **Disposition** | Checked - clean. |
|---|---|

- **The guard is used once.** `takesTheClick` now clears `answeredBy` on the first call with no question armed. Only `LayoutLabel`'s sensor `mouseClicked` calls it (LayoutLabel.java:330) - no press or release does - so the answering double-click's count-2 click is swallowed and nothing later is.
- **The claim.** A later count-1 then count-2 on TunnelPre must leave the feedback state as it was. R2a (the clear removed) turns it red.
- **The red-first record.** No run log shows 809d91a0 red on its own: `dc.log` (18:17:39) is the green after the fix. R2a is the evidence that it discriminates.

---

### TDD3-D9 - TDD2-C1's "not a defect" holds, with two notes on the census behind it

| **Disposition** | Checked - clean. |
|---|---|

- **What the census counts.** `probeOwnTailCensus2.java` stands a 40-unit train at every destination, arriving along each incoming edge, and asks `whyItWouldMeetItsOwnTail` of every route `debugPath` returns. It prints every refusal figure under 9, including a -1 for a message the pattern does not read, so a clean run is what the sentence needs.
- **The arrival road is one edge long.** Past a fork beyond that edge the body stops, so a real arrival can lie further back than the census's body does. That cannot produce a figure under 9, because a body place's figure is the distance run plus its distance behind the head, and only places within 9 of the head could give one. A return to such a place past a fork after under 9 minus that distance of measured running is not a route this railway has.
- **The output was not kept.** `c294b.log` holds only "1 run, 0 failures". The printed totals (1,282 of 2,686, and 728 at 9) are recorded only in the disposition.
- **The totals rose between the two runs.** The first census had 2,680 routes and 1,267 refused. The round's own-tail changes can only remove refusals: the new gate is stricter, and the minimum refuses the same trains the first refusing return did. So the rise came from other changes to routes or bodies in the range. The minimum was re-measured, which is what MT-571's sentence needs.

---

### TDD3-D10 - TDD2-C10's "not a defect" holds, and MT-571's new comment is right about both windows

| **Disposition** | Checked - clean. |
|---|---|

- **Why not Moving? asks the tier Path Type selects.** It reaches `explainDestinationsGrouped(standing, byHand)` (AutonomyEditorPanel.java:7979), and the probe asked both values at 20 and at 10.
- **The Why window opens only while the train has nowhere to go.** It opens from the destination label only while that label reads No available paths, which is what the comment now says.
- **Where the probe's railway differs from Adam's.** The probe cleared every other train first, so on the real railway another reason may be printed for LowerFront ahead of the own-tail one. The comment's "it gives the sentence" is true of the case MT-571 sets up.
