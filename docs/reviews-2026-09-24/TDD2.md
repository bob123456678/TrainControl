# TDD2 - Documents, the tracker, and the tests themselves (round 2, validation)

**Status:** open

**Prefix:** TDD2

**Validated:** branch `autonomy-diagram-r0` at `7b0d5c2f`, 2026-09-24. The range is `fc1ce476..7b0d5c2f`, 19 commits. Nothing was run: no JVM, no test, no `one.sh`, `battery.sh` or `mutate.py`, no git command that changes state. `cs2_sample_layout/` was not opened.

**Method.** I read `docs/reviews/FANOUT.md` and `docs/reviews/README.md` first, then round 1's `TDD.md` with its dispositions, and the `TDA.md` and `TDU.md` findings that four of them point to ("as TDA-B1", "as TDU-C3" and so on). For every commit in the range I read `git show`, then the code as it stands at HEAD. For every non-D finding of round 1 I checked four things: does the cited commit do what the disposition says, does the claim's assertion see the finding's own failure, which mutations were actually run, and did any survive. The mutation record is in the session's scratch area, and I read it read-only: `muta1`, `muta1b`, `mutot`, `muths`, `mutc7`, `mutfr`, `mutd`, `mutw` and `mutp`, each as a `.json` spec with its `.out`. The same folder holds the run logs (`a1*.log`, `ota.log`, `otg.log`) and the own-tail census the tracker quotes (`probeOwnTailCensus.java` and `c294.txt`). On the documents side I read the diffs of `behaviour.md`, `open-questions.md`, `issues.md` and `tests.md` in `28469a46`, plus MT-263, MT-564, MT-568, MT-570, MT-571 and MT-573 to MT-575 in full, `Automation.md` and the live-snapshot README. I read the store with `sqlite3` in `mode=ro`. I counted the Inbox headings, the ledger rows and the dispositions from the files themselves. Some tracker steps can only be judged by following the code under them, so I read those paths: what enables Return Home, what a page switch in the editor does to its panel, and when the locomotive list's Why window opens. Where a finding rests on behaviour I could not see by reading, it carries a verification request.

---

### TDD2-C1 - MT-571 still says no train of 9 or less is refused anywhere, from a census taken before the rule changed which return it names; TDD-C4's disposition says the comment qualifies that sentence, and it does not

| **Disposition** | Not a defect - the census re-run at HEAD (every station, arrival and route at 40 units; 1,282 of 2,686 routes refused) names 9 as its smallest figure, on 728 routes, none lower.  MT-571's comment says it was re-measured (244331ce). |
|---|---|

**The sentence.** MT-571's "What this is" (tests.md:28122) says: "no route between stations comes back to a train's own track in fewer than 9 units, so no train of 9 or less is refused by this anywhere".

**The disposition.** TDD-C4's disposition says this sentence "is qualified by the comment". The comment added in `28469a46` (tests.md:28126) ends: "Two changes since the entry was written: the figure named is the tightest return on the route, and a way round with nothing measured on it is not judged at all - on your railway the figure is still 9." That re-affirms 9 for BottomSecondary to LowerFront. It qualifies nothing about the railway-wide sentence, and a reader will take "still 9" to cover it.

**The census it rests on.** The census was the session's own probe (`c294.txt`, 14:27), run at length 40 before `1cd99bbc`. It recorded the FIRST return on each route whose way round was under 40. It found 1,267 routes refused, by gap: 9 on 688, 10 on 52, 11 on 191, 13 on 10, 15 on 152, 16 on 29, 27 on 145.

**What changed since.** `1cd99bbc` now names the SMALLEST way round over every return on the route. So a route the census recorded at 11 to 27 now names less if a later return on it is tighter, possibly less than 9. The two halves of the fix pull in different directions:
- TDA-B1's half can only remove returns, so it cannot lower the minimum.
- The tightest-return half can lower it.

The pin added in `c2251ace` covers BottomSecondary to LowerFront only.

**Consequence.** None on the railway. Adam is told a 9-unit train is never refused, so a refusal of one would read as a bug. Mitigated by the refusal naming its own figure, which is true for its route.

**Verification request.**
- Fixture: re-run the census probe (`probeOwnTailCensus.java`) at HEAD. For every station, arrival road and route, record the figure `whyItWouldMeetItsOwnTail` names at length 40.
- Proves the finding: a minimum below 9 on any route. MT-571 then needs a comment naming the route and its figure.
- Refutes it: a minimum of 9. The disposition should then say the census was re-measured at HEAD, not that a comment qualifies it.

---

### TDD2-C2 - The own-tail rule's timing of the route's own places has no claim; the mutation TDD-C4 named for it was never run

| **Disposition** | Fixed - as TDA2-C3. |
|---|---|

**Which mutation ran.** TDD-C4 named two mutations that keep the sentence consistent with itself:
1. A route place's free point taken at its near end.
2. A body place's free point taken at its far end.

The disposition cites OT4, `-lying.getValue() - 1`, which is a shift of the body - the second kind. The first was never run: `mutot.json` holds OT1 to OT4 and nothing else.

**The code that goes unclaimed.** `freeOnceTheTailPasses.put(place, travelled)` at Layout.java:10522 runs after the span is added. It is what judges a route that crosses its own earlier track, which the rule's javadoc names as a case ("the two roads through a crossing share their square and meet here"). Adam's railway has a crossover on these routes.

**Why no claim sees it.**
- Every route in `core.testTheOwnTailArithmetic` returns only to body places: Q R B A, then Q B A, then Q, R S, B.
- On the frozen railway the claims pin the minimum over body returns, which is 9.

Moving that line above `travelled += ...`, or deleting it, changes only returns to route places. So I expect it to survive every claim, including the class's "tightest return" line.

**Consequence.** Suppose this code regressed. A figure-of-eight route through a crossing would then be cleared for a train long enough to meet its own tail at the crossing. The code reads right today (TDD-D1).

**Verification request.**
- Mutations: delete the line; separately, move it above the span. Run each against `core.testTheOwnTailArithmetic` and `core.testATrainDoesNotRunIntoItsOwnTail`.
- Proves the finding: green.
- Claim to add: one hand-built edge over places Q, D, E, D, measured 1, 2, 3, 0. Then:
  - a 4-unit train is refused, naming 3;
  - a 3-unit train is clear;
  - under the near-end mutant the refusal names 5, and the 4-unit train goes.

---

### TDD2-C3 - The count of unmeasured stretches leaves out the stretch in which the train comes back

| **Disposition** | Fixed - as TDA2-C1. |
|---|---|

**The code.** `1cd99bbc` counts the route's edges from `edgeWhenLeft + 1` to `i - 1` (Layout.java:10513): "the stretches wholly between leaving the place and coming back to it". Edge `i` is never counted - the one in which the return is found. Suppose that stretch has nothing measured and has squares before the returning place. Those squares lie on the way round, have no length, and the refusal does not mention them.

**Example.** A body place B, 3 behind the head. A route of two stretches: [Q measured 1], then [R 0, B 0]. A 20-unit train is refused "after only 4 units", with no note, although measuring R is the other way past.

**Why the claim passes.** `testAPartlyMeasuredWayRoundSaysSo` passes because its return square B is alone in its stretch ([B 0]). That is exactly the case where leaving edge `i` out is right. Its expected figure, 1, pins the exclusion too.

**Consequence.** The message only. On a partly measured railway the refusal offers a shorter train as the only way past. Mitigated on Adam's railway, which is measured.

**Verification request.**
- Fixture: `core.testTheOwnTailArithmetic` with the route above.
- Proves the finding: the note is absent.
- Refutes it: the note says one stretch.

---

### TDD2-C4 - TDD-A1's second mutation survived as first written, was replaced by one that fails, and was not recorded; nothing pins that each train's record holds only its own tail

| **Disposition** | Fixed - as TDA2-C2. |
|---|---|

**What happened.** The first "A1b" in `muta1.json` handed `tailsOfEachStandingTrain`'s walk `null`, so every train was walked into every train's record. `muta1.out` reads: "Total tests run: 2, Failures: 0 | red: []". `muta1b.json` then replaced it with a spec that filters the shared map by owner, which went red. The disposition reports "Mutations A1a, A1b red".

**What FANOUT requires.** A survivor is either killed by a strengthened claim or recorded as equivalent. This one is neither, and it is not equivalent. With every train's claims in every record:
- `liesAcrossAtStart` answers `covered.containsKey(edge)`, and the lock-edge `covered.containsKey(sharing)`, without asking whose claim it is. So an edge lying under a train that HAS moved is refused while any other train has not.
- On an edge with no places, `blockedSensors` has every train account for every covered end.

**The effect.** Return Home over-refuses: it reports no plan on a railway that has one. HomeStaging's own javadoc says a planner must never go that way. The claim's control removes the only other train, so a third, unmoved train is what the fixture lacks.

**Consequence.** C: the code is right today.

**Verification request.**
- Re-run the first A1b in `muta1.json` against `core.testATailIsNotHiddenByAnother`. Green proves the finding; it was green once.
- Claim to add: a third train standing unmoved away from the switch. With the train whose tail covers an edge moved, a route over that edge is clear.

---

### TDD2-C5 - The sensor half of TDD-A1's Return Home fix has no claim

| **Disposition** | Fixed - pin testReturnHomeKeepsTheSensorShutWhileEitherTailIsOnIt in 7c7a0fb0: with either train moved the switch's sensor stays shut, with both it is free.  Mutations R2e and R2f red on it. |
|---|---|

**The change.** `e501b56d` also changed `HomeStaging.blockedSensors` (HomeStaging.java:1909) to read each train's own places. The commit message says: "its sensor bookkeeping reads each train's own places". The TDD-A1 claim calls only `passesTheTailsOfTrainsThatHaveNotMoved`, and `muta1b` turns only that red.

**What the change prevents.** Two tails can claim the square next to a sensor Point. The old single map credited that square to the train walked last, so the other train did not account for that end. Once the credited train moved, the sensor was freed although the other still lay there. That is the planner looser than the railway, which is OB-073.

**Consequence.** C.

**Verification request.**
- Fixture: extend `core.testATailIsNotHiddenByAnother`. Set A1_J's feedback on, then call `blockedSensors` (by reflection) twice: once with the first train moved, once with the second.
- Expected at HEAD: A1_J's sensor stays blocked both times.
- Under `muta1b`, one of the two frees it. Red there proves the claim sees the change; green there proves this finding.

---

### TDD2-C6 - TDD-A1 left the routed train excluded twice in isPathClear, and Layout.tailsOn with no caller

| **Disposition** | Fixed - as TDA2-C5. |
|---|---|

**Dead checks in `isPathClear`.** After `e501b56d` the walk leaves the routed train out (Layout.java:2690), so the three older exclusions can never fire:
- `lyingAcross.equals(loc)` at :2702, with OB-285's paragraph still explaining it as the mechanism;
- `onShared.equals(loc)` at :2724;
- the routed train passed to `anotherTailOn` at :2791, and the check at :2793.

One rule is now stated twice. At closing, any mutation of OB-285's line 2702 will survive and has to be recorded as equivalent (FANOUT, "Before closing").

**`Layout.tailsOn` (:7949) has no caller.** It lost its only caller, HomeStaging, in the same commit. Its javadoc still says the planner asks it, and still promises "EVERY train ... whose tail lies over metal this edge runs on" while reading one train per place from a single map - the defect TDD-A1 was about. A new caller brings TDD-A1 back.

**Consequence.** None today; a trap.

**Fix:** delete `tailsOn`, and say at :2702 that leaving the routed train out of the walk is what excludes it.

---

### TDD2-C7 - The One-Way Run pin checks the button, not the tool: deleting `tool = Tool.NONE` leaves the class green

| **Disposition** | Fixed - claim 5b656ebe: the tool is NONE (green - a pin) and its prompt goes (red first); fix 4776026a.  Mutation R2h (the tool left armed with its button up) red. |
|---|---|

**What the pin asserts.** `testTheOneWayButtonIsGreyedAndPutDownOnAPageLeftOut` (`a961695e`) asserts only the button: `oneWay.isEnabled()` and `oneWay.isSelected()`.

**Why the button and the tool can differ.** The disarm at AutonomyEditorPanel.java:9507-9512 sets three things. `setSelected(false)` fires no action event, and the tool buttons listen for actions (:978), so the button and the tool are independent. P2 deleted `tool = Tool.NONE` and `setSelected(false)` together, so it never tested them apart.

**The surviving mutation.** Delete only `tool = Tool.NONE` (:9509):
- The button reads unpressed, but the tool stays One-Way.
- On the page left out, the click guard (:7215) hides this.
- Tick the page back in, and the next click on a square starts a one-way run (`oneWayFrom` set, :7210) with the button up.

That is exactly MT-568's "nothing waits for a click". TDD-C7 asked for "the tool state after arming then excluding"; the pin asserts the button's.

**A second thing to look at.** The disarm leaves the hint line as the armed tool set it: there is no `say(hint, ...)` in that branch. Worth a look when MT-568 is run.

**Consequence.** C.

**Verification request.**
- Mutation: the disarm block with only its first statement removed, against `core.testMassAssignLengths`. Green proves the finding.
- Claim to add: after excluding the page and refreshing, `tool` is NONE (by reflection). Or: re-include the page, click a square, and `oneWayFrom` stays null.

---

### TDD2-C8 - MT-568 step 3 cannot fail: a page switch builds a new editor panel, so the armed tool is thrown away whatever OB-235 does

| **Disposition** | Fixed - comment on MT-568 (244331ce): arm One-Way Run, then tick Exclude Page on that same page; untick and click - nothing waits. |
|---|---|

**The step.** MT-568 step 3 (tests.md:28033): "Go back to 1 - Main, press One-Way Run, then switch to 4 - Combined before clicking anything". Expected: "the button is greyed and no longer pressed".

**What a page switch does.** In the editor a page switch rebuilds the panel:
- `LayoutEditor` sets the arriving page and calls `setAutonomyMode(null)`, which drops the panel (:1621-1624);
- then `setAutonomyMode(session)` builds a new `AutonomyEditorPanel` for the arriving page (:1678);
- the panel's `page` is final (AutonomyEditorPanel.java:94).

So the button on 4 - Combined is new and was never pressed. Step 3 passes with or without OB-235's disarm.

**Where the disarm is actually reached.** Only by leaving out the page the tool is armed on - ticking Exclude Page there. That is what the new pin does (`setPageExcluded` then refresh). Round 1's TDD-C7 took step 3 to be that part, and it is not.

**The citations are stale too.** MT-568's and MT-570's "What this is" still name only the old claims. Neither names the pin added for it:
- `testTheOneWayButtonIsGreyedAndPutDownOnAPageLeftOut` for MT-568;
- `testWhyNotMovingFollowsTheRunningRailway` for MT-570, whose new comment does not name it.

**Consequence.** Validating MT-568 by hand validates nothing about the disarm.

**Fix:** a comment on MT-568:
1. Arm One-Way Run on a page, then tick Exclude Page on that same page. Expect the button up and greyed, and a click there to do nothing.
2. Untick it and click a square. Expect no one-way prompt.

---

### TDD2-C9 - MT-573 step 3 presses Return Home without first giving it anything to do

| **Disposition** | Fixed - comment on MT-573 (244331ce): send one train away before breaking the setup. |
|---|---|

**The step.** MT-573 (tests.md:28172) breaks the setup, then says "Press Return Home". Expected: "no train moves; a message says the setup cannot be used yet".

**Why the button is probably greyed:**
- The button is enabled only when triage finds work: `returnHomeButton.setEnabled(nothingToDo == null)` at TrainControlUI.java:24383. Triage answers ALREADY_HOME when no train is away from home.
- The station right-click item reads the button (`HomeLocomotiveMenu.addReturnHomeItem`).
- Step 1 closes TrainControl. On reopening, a train with no assigned home is at home wherever it stands (AutomationAPI.md: "a locomotive's home is simply the station it occupied when the layout was loaded").

So on Adam's railway the button is likely greyed, and step 3 cannot be done. After step 2 the hand doors refuse, so any train has to be moved away before the setup is broken.

**Consequence.** A hands-on test that cannot be run as written; Adam may mark it Does not work.

**Fix:** a comment: before step 2, send one train by hand to another station.

**Verification request.**
- Fixture: on the frozen railway, with every train at its derived home, read `isReturnHomeOffered()`.
- Proves the finding: false.
- Refutes it: true.

---

### TDD2-C10 - MT-571's comment sends Adam to two windows; one opens only when the train has nowhere to go, and the other may give autonomy's reason

| **Disposition** | Not a defect in either window; the comment corrected.  Probe at HEAD, the OB-294 train at BottomSecondary as arrived: at 20 and at 10 both tiers' explanations give LowerFront the own-tail sentence.  The Why window opens only while the train has nowhere to go, and MT-571's comment now says to use Why not Moving? otherwise (244331ce). |
|---|---|

**The comment.** tests.md:28126: "The sentence the steps quote is shown by Why not Moving? on the diagram, clicked on BottomSecondary, and in the locomotive list's Why window."

**The locomotive list's Why window.** It opens from the destination label only while that label reads "No available paths": `if (noPathsNow) showWhyNot();` at AutoLocomotiveStatus.java:841, with the flag set at :411. A 10-unit EN57-203 that any other station will take has no such window.

**Why not Moving?** It answers in the tier Path Type selects (AutonomyEditorPanel.java:7929). Outside Manual, LowerFront's line comes from `firstClearOrWhyNot`, which:
- reports the reason for the last route it tried;
- says "through reversing" for a route that reverses on the way, before `isPathClear` is ever asked.

Round 1 named these two windows (TDD-C1) without checking either.

**What is pinned, and true.** LowerFront is not offered at 20 or at 10: `testALongTrainIsNotSentRoundIntoItsOwnTail`, and TDA-C2's every-route loop.

**Verification request.**
- Fixture: on live-snapshot, the OB-294 class's own train at BottomSecondary as arrived, at 20 and at 10 units.
  - (a) `getPossiblePaths(train, true)`.
  - (b) `explainDestinationsGrouped(start, false)` and `explainDestinationsGrouped(start, true)`.
- Proves the finding: (a) non-empty at either length, so the Why window cannot be opened then; or LowerFront's reason in (b) is not the own-tail sentence in one of the two tiers.
- Refutes it: (a) empty at both lengths, and the own-tail sentence in both tiers.

---

### TDD2-C11 - Automation.md, the guide the operator reads, still has no own-tail rule; TDD-C2's disposition covers behaviour.md only

| **Disposition** | Fixed - 244331ce: Automation.md's What lengths govern has the loop rule and what its refusal says. |
|---|---|

TDD-C2 named both documents. `28469a46` added the rule to behaviour.md 5c.

Automation.md's "What lengths govern" (:158) still lists four things lengths decide. It does not list the fifth: a train is refused a route that brings it back onto its own tail before the tail has left, and the refusal names the longest train that goes.

The refusal sentence is new to the operator, and this is the page he would look it up in.

**Consequence.** C.

---

### TDD2-C12 - open-questions.md says "Open: none" under Reversals and under Length while three of the round's findings are open as Adam's decisions

| **Disposition** | Fixed - 244331ce: open-questions.md carries TDA-C8 and OB-296 under Reversals, TDA-C10 and TDU-C6 under Length, OB-295 under Routing tiers, and TDU2-C3 and TDD-C11 under a new Setup and start-up section, with TDU-B1 as decided while Adam was away. |
|---|---|

The store has four rows with status "Open - Adam's decision":
- TDA-C8: a turn-round square on a barred approach - a Reversals question;
- TDA-C10: the platform run-in notice's "may block" - a Length question;
- TDU-C6: answered-0 track and the Atomic Routes refusal - a Length question;
- TDD-C11: the legacy-import untick.

open-questions.md still says "Open: none." under Reversals (:65) and under "Length, blocking, and the tail" (:128). `28469a46` edited this document and did not add them. The precedent was to carry such a question there: DCN-C14 became OB-283, with an entry under Reversals.

Once the round's documents are deleted, the store's disposition text is the only place these questions are written.

**Consequence.** None on the railway. The document says nothing is open where four things are.

---

### TDD2-C13 - The renamed Bulk Tools items keep their old names in comments and in test failure messages

| **Disposition** | Fixed - ef1adfb6: every comment and test message names the items as they are labelled. |
|---|---|

TDD-C5, TDD-C6 and TDD-C10 were fixed in behaviour.md and the tracker. The old labels remain in:
- **Source comments:**
  - AutonomySession.java:3679 ("Mass Assign Max Train Lengths");
  - AutonomyEditorPanel.java:10023 and :10028, the javadoc `858317f2` itself edited ("Mass Assign Train Lengths", "Mass Assign Max Train Lengths' walk");
  - AutonomyEditorPanel.java:10152.
- **Test failure messages** in `core.testMassAssignLengths`, among them :636, :750, :778, :799, :1368, :1377, :1698, :1923, :1947, :2687.
- **`ui.testTheLengthPromptHasTheKeyboard`** :24 and :170.

A red names a menu item Adam cannot find.

**Consequence.** C.

---

### TDD2-C14 - The half-measured notice names "the switch behind it" where the count now also stops at a crossing or a permanent turnout

| **Disposition** | Fixed - as TDA2-C7. |
|---|---|

`10d7f523` (TDA-C7) made the count stop at a switch, a permanent turnout or a crossing: `endsTheBerthsRoom`, AutonomySession.java:9695.

The same commit rewrote the notice in all eight languages to say "its longest train is not spent before the switch behind it, and is refused there" (messages.properties:1395). At a berth whose room ends at a crossing, the notice points Adam at a switch that is not there.

**Consequence.** C.

---

### TDD2-C15 - TDD-C9's third point, that the guard items' tooltips can be swapped with the class green, was dropped without a word

| **Disposition** | Fixed - pin in ef1adfb6: each guard item's tooltip starts with its own key's text.  Mutation R2i (the two swapped) red. |
|---|---|

TDD-C9 said: "testTheGuardItemsSayWhatTheGuardsDo asserts only that a tooltip is non-empty, so swapping the exit and entry texts would survive."

Its disposition, "Fixed - as TDU-C11", covers two things only: the railway branch of `whyWaitsOn` and the outlines going. The guard-tooltip claim (testTheEditorSaysWhatItsToolsDo.java:74) is unchanged. The two items are wired correctly today (AutonomyEditorPanel.java:1692 and :1706).

**Fix:** either assert that each item's tooltip is its own key's text, or record the point as declined as minor.

**Consequence.** C.

---

### TDD2-C16 - The sensor that answered a tail question swallows the second click of every later double-click on it

| **Disposition** | Fixed - as TDU2-C1. |
|---|---|

**The code.** `fc574093` (TDU-B3) keeps the answering label in `TailCrossedPrompt.answeredBy` (:560), set at :743 and :756, and never clears it. `takesTheClick(label, clickCount)` (:550) then returns true for any click counted above 1 on that label, while no question is armed.

**The effect.** Long after the answer, a double-click on TunnelPre flips its sensor once instead of twice, and the faked occupancy change is left standing. That is the class of event TDU-B3 was graded B for.

**Documents and claim.**
- behaviour.md:1105 describes a narrower rule: "the rest of a double-click on the sensor that answered".
- The claim proves the second click is swallowed; it does not prove that the swallowing ends.

**Consequence.** C: one sensor, until another question is answered or the diagram is redrawn.

**Verification request.**
- Fixture: in `regression.testTheTailIsPickedOnTheDiagram`, after the double-click claim and with nothing armed, `click(q.label, 1); click(q.label, 2)`.
- Proves the finding: TunnelPre's feedback state has changed.
- Refutes it: the state is unchanged.

---

### TDD2-C17 - Start now asks the Atomic Routes gate from its worker, and a gate that throws no longer stops Start

| **Disposition** | Fixed - as TDU2-C2. |
|---|---|

**The change.** `492ad392` (TDU-C8) moved Start's gate into the worker as `invokeAndWait(() -> keepAtomicRoutesOnWhileTheRailwayCouldReleaseTrack())`. It catches `InterruptedException` and `InvocationTargetException` by logging, then goes on to `runLocomotives` (TrainControlUI.java:26955-26975).

**Before.** The gate ran first in the event handler. An exception there ended the press before the button was greyed or anything was dispatched.

**Now:**
- The gate reads `unmeasuredTrackAutonomyRunsOver()` and `trainsWithoutALength()` before it calls `setAtomicRoutes(true)` (:6120-6126). A throw in either now lets autonomy start non-atomic over exactly the track the gate protects.
- An interrupt starts the run while the gate may still run afterwards.

The sibling doors, Return Home and Execute Timetable, still ask the gate on the event thread, where a throw stops them.

**Consequence.** If reached: edges handed back under a train. Likelihood: it needs a throw in two read-only walks. Graded C for that.

**Fix:** return on the catch, as every other refusal on that worker returns.

---

### TDD2-D1 - TDD-A1's two claims do not depend on the map's order, and the fix reaches every reader that asks about one train's route

| **Disposition** | Checked - clean. |
|---|---|

- **The claims.** `ce9dd7bd` makes each train the routed one in turn. Whichever train the map records on S, one assertion is the bug's.
  - A1a turned the class red (`muta1.out`).
  - The session's probe `core.probeSharedClaim` (`a1.log`, green) is what "the frozen railway's order was benign" rests on.
- **The walk.** The predicate filters before the once-each set, and one train's walk reads nothing of another's. So leaving the routed train out changes no other train's claims.
- **The other readers.** The single maps are also read at AutonomySession.java:6802, :6863 and :6985, and all three only draw. `passesTheTailsOfTrainsItHasMoved` walks each moved train alone. So `isPathClear` and the two Return Home methods are the whole of it.
- **behaviour.md.** Its new 5c bullet says what the code does.

---

### TDD2-D2 - TDD-B1 and TDD-C3, fixed as TDA-B1 and TDA-C1, hold

| **Disposition** | Checked - clean. |
|---|---|

- **Only a measured way round is judged.**
  - A return to a body place is judged only once the route has measured something.
  - A return to a route place keeps the old test, `wayRound > 0`.
  - OT1 turns `testAWayRoundWithNothingMeasuredIsNotJudged` red, and its control is judged on one measured square.
- **The tightest return is named.** Every return is asked and the smallest named; a train is refused exactly when it is longer than that. OT2 turned the 8-then-6 route red.
- **Red first.** `ota.log` shows all three new claims red before `1cd99bbc`.
- **A guard that cannot fire.** `if (ids.size() != spans.size()) return null` (Layout.java:10482) now sits inside a loop that collects returns, where it would throw away a refusal already found. But `Edge.setPlaces` refuses lists of different lengths (Edge.java:402), so it cannot fire.

---

### TDD2-D3 - TDD-C8 (as TDU-C3) holds, with one note

| **Disposition** | Checked - clean. |
|---|---|

**Both named mutations fail the claim.** `testABrokenSetupIsRefusedAndAMendedOneIsNot` asks the window's own method before the setup is broken and after. So:
- `if (true) return null;` fails it at `outcome[2]`;
- the inverted guard fails it at `outcome[0]` (D2, `mutd.out`).

**The note.** D2's red lands on the assertion labelled "precondition: the frozen railway's setup is refused before anything is broken". That message blames the fixture for what is really the claim's own failure.

**No hidden dependency.** `getAutonomySession` builds a session from the layout path whatever Load Autonomy says, so the claim does not depend on Adam's preference.

---

### TDD2-D4 - TDD-C12's "not a defect" holds

| **Disposition** | Checked - clean. |
|---|---|

H2 (`muths.json`) made the first A* search weighted - the one from where the greedy pass left things. That is "search for any plan first". `testAnEasyArrangementStillGetsTheShortestPlan` went red, so the MUTATION line holds.

---

### TDD2-D5 - TDD-C5, TDD-C6, TDD-C10 and TDD-C11 say what the code and the bundle say

| **Disposition** | Checked - clean. |
|---|---|

- **TDD-C5.** behaviour.md's Bulk Tools paragraph now says:
  - a length is 1 to 40;
  - the three items by their new names;
  - the item is never greyed and counts the trains missing a length;
  - with none missing, it goes through every train.
- **TDD-C6.** The comments on MT-518 to MT-521 give the labels exactly as the bundle has them.
- **TDD-C10.** `858317f2`:
  - removes the stale inline comment;
  - makes the javadoc describe both modes;
  - answers 0 with `errorTrainLengthOutOfRangeKnown` when the train has a length (W1 turned two claims red);
  - gives the tooltip for no train placed.

  The new key is in all eight bundles.
- **TDD-C11.** `865b4168`'s javadoc is true: the log line (`autoLoadOffAfterImport`) and the changelog both tell Adam to tick Load Autonomy again.

---

### TDD2-D6 - TDD-C13, TDD-C14 and every count the round wrote

| **Disposition** | Checked - clean. |
|---|---|

- **The store.**
  - 4,146 rows for 3,789 refs, and 3,726 + 336 + 13 + 71 = 4,146.
  - AR-17 to AR-23 and LR-1 to LR-6 are marked "no document".
  - The round's rows: 19 TDA + 24 TDU + 28 TDD = 71.
  - Their statuses match the dispositions: 65 Closed, TDA-C9 and TDU-B4 Open, and four "Open - Adam's decision".
  - `findings.tsv` carries the 71 rows.
  - TDD2 appears nowhere in the store or the mirror.
- **The Inbox.** 83 headings: 51 OB and 32 FR.
- **The ledger.** 44 rows, which are 42 fixed unvalidated and 2 needs test. The rest: 454 fixed validated (422 + 32 with a trailing space) and 77 superseded, which is 531 of 575.
- **The OB-235 receipt.** Filed is the filing date, like the rows around it; the table is ordered by pick-up, not by filing.

---

### TDD2-D7 - The range's claims are registered, and the files they depend on agree

| **Disposition** | Checked - clean. |
|---|---|

- **build.xml.** `testATailIsNotHiddenByAnother` and `testTheOwnTailArithmetic` are in it; every other class changed in the range was already there.
- **The live-snapshot README.** Its Used-by list equals the set of 71 classes that open it.
- **The bundles.** Every bundle is ASCII-only, and the new keys are in all eight. The French and Italian texts use the typographic apostrophe (U+2019), so no placeholder is swallowed by quoting.
- **TDA-C7's claim.** It was edited in its own fix commit. C7a and C7b each turn it red on the unfixed shape, one half at a time, so the edited claim is proven.

---

### TDD2-D8 - behaviour.md's "no tier moves trains over a setup with errors" names every door that moves trains

| **Disposition** | Checked - clean. |
|---|---|

These are the doors in `src` that dispatch trains:
- Start (`refuseAutonomyStartWhileBroken`);
- Execute Timetable (TrainControlUI.java:26654);
- Return Home, through `requestReturnToHome`, from the button and from the station right-click item;
- the two hand doors.

`restartTimetable` only resets, and `Layout.executeTimetable` is reached only through those doors. The sentence holds.
