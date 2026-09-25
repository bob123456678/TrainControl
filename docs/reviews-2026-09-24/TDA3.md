# TDA3 - Automation lane, round 3: the dispositions of TDA2, and what the round-2 fixes left behind

**Status:** open

**Prefix:** TDA3

**Reviewed:** branch `autonomy-diagram-r0` at `67f79912`, 2026-09-24. The range is the round-2 fixes, `7b0d5c2f..67f79912`. Read-only throughout.

**Method.** I read `docs/reviews/FANOUT.md` and `docs/reviews/README.md` first, then `docs/reviews-2026-09-24/TDA2.md` in full and the headings and dispositions of `TDD2.md` for the findings it shares with this lane (TDD2-C2 to C7 and C14). I read `git show` for every commit in the range that touches `automation/`, `automationui/`, the bundles or their tests: `f652c14f`, `baf8856c`, `7c7a0fb0`, `3a8813ca`, `ece635b0`, `84a89426` (all ten files), the lane's parts of `ef1adfb6`, and the `behaviour.md`, `open-questions.md`, `Automation.md` and `tests.md` parts of `244331ce`. From `5e1b0623` I read only the `TailCrossedPrompt` and `GraphLocAssign` parts, because they write the road that the tail walk reads. Then I read the code as it stands at HEAD. In `Layout`: both forms of `whyItWouldMeetItsOwnTail`, `bodyOfATrainAt`, the tail block of `isPathClear`, `walkStandingTrains`, `tailsOfEachStandingTrain`, `anotherTailOn`, `tailLiesOn` and `whyABerthCannotHoldIt`. In `HomeStaging`: the constructor, `search` and its budget, both `passesTheTails...` methods, `liesAcrossAtStart`, `stillWhereItStarted` and `blockedSensors`. In `AutonomySession`: `berthTrackBeforeTheStop`, `stationsWithAHalfMeasuredApproach`, `runInsShorterThanTheBerth`, `endsTheBerthsRoom`, `takesNoLength` and `assignStretchLength`. Also the run-in and half-measured parts of `AutonomyChecks`; `roomAfterTheLastSwitch`, `boundsTheRoom` and `deriveLocks` in `GraphReducer`; and the six changed or neighbouring keys in all eight bundles. I read these tests in full: `testTheOwnTailArithmetic` and `testATailIsNotHiddenByAnother`. From `testMassAssignLengths` I read lines 2130-2350 in full, and from `testReturnHomeKeepsClearOfTheTailsItLeaves` its replay helper. Without changing anything, I read the round's records in the session scratchpad: `mutr2.json/.out`, `mutr2b.json/.out`, the logs `a1r`, `a1g`, `a1p`, `dc`, `gt`, `gg`, `ot2`, `ot3`, `tt`, `cl`, `c6r`, `c6f`, `c7r`, `c7f`, `c13`, `cm`, `c294b` and `rc2`, and the `timing/` folder (`run.sh`, `no_own.py`, both logs, `verdict.txt`, and the JUnit XML of the four Return Home classes in `head/` and `noown/`). I checked the arithmetic by hand. **Nothing was executed**: no JVM, no test, no `one.sh`, `battery.sh` or `mutate.py`, and no git command that changes state. I did not open `cs2_sample_layout/`. One observation, not graded: the two mutation records end with a `git diff` showing uncommitted changes to `cs2_sample_layout/config/autonomy/configuration-Main.json` and `setup.json`. No commit in the range touches either file. Every finding that rests on behaviour I could not see by reading carries a verification request.

Grades: A is wrong behaviour on the layout, or data lost. B is incorrect results in specific configurations. C is a notice or text that is wrong, a narrow case, or a guard that proves less than it says. D is checked and clean.

---

### TDA3-C1 - For a parking berth with nothing measured between it and a crossing, the run-in notice still quotes the room walk's figure, which the berth rule never gives: a sibling that TDA2-C6's fix left behind at `before[0] > 0`

| | |
|---|---|
| **Disposition** | Fixed - claim testNothingSpentBeforeTheCrossingIsSaidAsNothing in 6bfab5bb (red first), fix 32a75f8d: with nothing spent before the stop, a parking berth's run-in notice says 0 where the squares were answered 0, and nothing where they were not - the half-measured notice names those.  Mutation R3e red. |

**Where.** `AutonomySession.java:9812` (`84a89426`):

```java
if (before[2] == 1 && before[0] > 0 && (room <= 0 || before[0] < room)) room = before[0];
```

The berth branch takes the berth rule's figure in place of the room walk's only where the berth rule has spent something before its stop. Suppose instead that it reaches the crossing having spent nothing, because every square between the berth and the crossing has no length, whether answered 0 or not. It then refuses every train: it claims the crossing's square before it spends on it (`Layout.java:10229-10266`). `room` stays the room walk's number, counted back to the switch with the crossing included, and that number is positive whenever the track between the crossing and the switch is measured. The notice then says *"only R of track is measured between it and the switch, crossing or reversal behind it - so a train longer than R is refused"*. That is true, but a one-unit train is refused too. The class states the rule this breaks at `:9746-9747`: *"A notice quoting a number the refusal would not quote is worse than no notice: the reader measures the wrong stretch."*

The guard carries over a doctrine that belongs to the other rule. *"UNMEASURED IS UNKNOWN, NOT SHORT"* (`:9824`) is the room rule's doctrine. Under the berth rule, once anything on the approach is measured (PRW-B1), an unmeasured square is claimed and counts nothing.

**Input.** `testMassAssignLengths.openBerthBehindACrossing()`, with the berth 7,1 and 6,1 given no length; 5,1 (the crossing) = 1, 4,1 = 3, 3,1 (the switch) = 1, 2,1 = 1; and a maximum of 5.
- Room walk: 4,1 + 5,1 + 6,1 + 7,1 = 4 (`roomAfterTheLastSwitch` counts the end square).
- `berthTrackBeforeTheStop` gives `{0, n, 1}`. n is 2 if the two squares are unanswered, 0 if they were answered 0.
- `runInsShorterThanTheBerth` gives `{5, 4}`: the notice says a train of up to 4 fits.
- If they are unanswered, the half-measured warning fires with 2, so the two notices disagree about one berth, which is TDA2-C6's shape. If they were answered 0, the half-measured warning is silent, and the only notice is the wrong one.
- `whyABerthCannotHoldIt` refuses a one-unit train, naming the crossing's road. When the zeros are answered, the refusal carries no partly-unmeasured note.

**What mitigates it.**
- The refusal has its own sentence, naming the road.
- When the squares are unanswered, the right warning is shown beside the wrong figure.
- It is not reachable on the frozen railway. The one crossing on an included page, `1 - Main` 18,10, lies beyond the switch from every station, so the stop there is the switch (TDA2-C6's mitigation, still true).

**Verification request.** Build the input above. Answer 7,1 and 6,1 with 0 (Mass Assign's 0 on their piece), then rebuild.
- Proves it: `runInsShorterThanTheBerth()` maps 7,1 to `{5, 4}` and `stationsWithAHalfMeasuredApproach()` has no 7,1. Also build the running layout from the session and take the edge into 7,1, as TDA3-C2 describes; `whyABerthCannotHoldIt` then refuses a one-unit train on it.
- Refutes it: the run-in entry is absent or quotes 0, or the rule admits the one-unit train.
- The likely fix: for a parking berth whose stop is reached with nothing held, drop the run-in entry and leave the berth to the half-measured warning. When the squares are answered, quote 0.

---

### TDA3-C2 - The crossing claims check the notices against a model of the berth rule, never against the rule itself; and nothing pins the half of the run-in change that covers a leg with no switch

| | |
|---|---|
| **Disposition** | Fixed - the premise asked of the rule itself: testTheBerthRuleRefusesAtTheCrossing (59cf3645) builds the railway and asks whyABerthCannotHoldIt - two units held, three refused.  It first failed: the fixture's crossing had nothing leading onto its other road, so the build emitted no rail over it and the rule had nothing to refuse on; the fixture now has a road a train can drive, and a crossing nothing leads onto is a stated limit (open-questions Limits, endsTheBerthsRoom's javadoc) - the notices warn there, the rule does not refuse.  The leg with no switch pinned (testACrossingOnALegWithNoSwitchIsSaid); mutation R3f red. |

**Where.** `testMassAssignLengths.java:2187-2221`: TDA-C7's claim, which `ece635b0` extended. And `:2236-2276`: TDA2-C6's run-in claim.

1. **The premise is asserted nowhere.** Both claims rest on *"the berth rule refuses a three-unit train at the crossing"* (their messages, `:2214` and `:2265`), and neither asks the rule. Each one asserts what `berthTrackBeforeTheStop` computes. Its stop is `endsTheBerthsRoom`, which tests the tile type. That is a model of what `Layout.whyABerthCannotHoldIt` actually tests: a claimed place on a symmetric lock partner that does not touch the berth (`Layout.java:10246-10266`). TDA2-C6's verification request asked for both halves (*"while `Layout.whyABerthCannotHoldIt` refuses a 3-unit train on the built route"*), and the claim kept only the first. The two walks *"are written to agree and are not one piece of code"* (`Layout.java:10140`). Since `84a89426`, two notices depend on the model where before one did. By reading, the premise holds. The reducer locks a crossing's two roads and gives them one place: `testAutonomyDiagramReducer`'s `locksItsTwoRoads(CROSSING)` and `placesBothRoadsShare(CROSSING)`. So this is a missing guard, not a defect.
2. **The leg with no switch.** Where no switch lies on the arriving leg, the room walk answers `Integer.MIN_VALUE`. The `room <= 0 ||` at `AutonomySession.java:9812` now gives a parking berth with a crossing on such a leg the berth rule's figure. Before `84a89426`, that leg was skipped (`:9815-9822`). The claim's leg has a switch, with a room of 6, so by reading, deleting `room <= 0 ||` leaves the class green. That berth would then lose its notice again.

**What mitigates it.** The code is right by reading, so what is missing is only the guard. Neither shape is on the frozen railway.

**Verification request.**
- **The premise.** Use a class that builds the running layout from the session, as `testABerthAndAPlatformJudgeAnOverhangDifferently:107-109` does (`model.parseAuto(session.buildConfiguration())`, then `model.getAutoLayout()`). Build `openBerthBehindACrossing` with every square measured, as in `:2245-2251`. Take the edge into 7,1 and ask `whyABerthCannotHoldIt` about a two-unit and then a three-unit train.
  - Proves the premise: null for the two-unit train, and for the three-unit train the foul-a-road sentence naming the road through 5,0 - 5,2.
  - Refutes it, and then both notices are wrong: null for the three-unit train.
- **The leg with no switch.** Use the same page with 3,1 a `STRAIGHT` and no `wire`, every square measured, and a maximum of 3.
  - Expected: `runInsShorterThanTheBerth` maps 7,1 to `{3, 2}`.
  - Then run the class with `room <= 0 ||` deleted. If it stays green, the gap is proved.

---

### TDA3-C3 - Text that the round-2 fixes left behind or made false

| | |
|---|---|
| **Disposition** | Fixed - 32a75f8d and 59cf3645: both AutonomySession javadocs, both AutonomyChecks javadocs, the two test class javadocs by name (with R2b-R2e), Automation.md's caveat, and the Mass Assign label in da, de and fr. |

- **`AutonomySession.java:9625`**. The summary line of `stationsWithAHalfMeasuredApproach` still reads *"the state that closes a berth to every train"*. `84a89426` corrected the heading beneath it (TDA2-C7) to *"refuses trains that fit"*. Its `@return` at `:9647`, *"how many squares of its approach still have no length"*, now counts only the squares before the stop (TDA2-C6).
- **`AutonomySession.java:9748-9756`**. The javadoc of `runInsShorterThanTheBerth` gains *"at a crossing between the berth and its switch"*, which presumes a switch. The next paragraph, *"An edge crossing no switch is skipped unless the train turns at its far end"*, is no longer true for a parking berth with a crossing on that edge (TDA3-C2, point 2).
- **`AutonomyChecks.java:325-329`**. `RUN_IN_SHORTER_THAN_THE_BERTH` says the measured run-in is *"counted back from the platform to whichever of the last switch and a reversal is met first"*, and that *"two rules can refuse"*. For a parking berth, the count now also stops at a crossing, and the figure is the berth rule's.
- **`AutonomyChecks.java:380-382`**. `HALF_MEASURED_APPROACH` says *"the room walk judges as soon as anything on the approach is measured"*, but that is the berth rule. `84a89426` took the rule's name out of the notice in eight languages (TDD2-C14) and left it here.
- **`testTheOwnTailArithmetic.java:27-28`**. The class's MUTATION line counts *"the first claim ... the second ... and the third"*. `f652c14f` put two claims above the three it counted, so in file order the ordinals now land on `testARouteThatComesBackOverItselfIsJudged` and `testTheStretchTheTrainComesBackInIsCounted`. The class line names neither new mutation; the methods' own javadocs do.
- **`testATailIsNotHiddenByAnother.java:36-38`**. The class javadoc says R2e (every train's record holds every tail) makes *"the second fail at its control"*. By my reading, the control at `:259` still passes under R2e. This fixture has no lock partners, and the third train owns no place of `outOne`. The assertion that fails is the new one at `:264`. R2e also fails the third method (`mutr2.out`), which the line does not say.
- **`Automation.md:166`**. The new paragraph says *"where part of the loop has no length the message says how many stretches"*. The rule's javadoc (`Layout.java:10414-10419`) and TDA2-C1's disposition both say that between Mass Assign sittings the note can say fewer, or nothing. The operator's guide promises the count without that caveat. (`behaviour.md:1158` says the same, and rightly, because it states the intent. The piece grain is TDA2-C1's open half.)
- **The half-measured notice names Mass Assign Lengths by words that are not the menu's label, in three of the seven translations:**
  - da: *"Tildel længder i bulk"*; the menu says *"Tildel længder samlet..."*.
  - de: *"Längen sammelweise zuweisen"*; the menu says *"Längen gesammelt zuweisen..."*.
  - fr: *"l'attribution groupée des longueurs"*; the menu says *"Attribuer les longueurs en série..."*.
  - it and pl use a noun form of their labels, which a reader can still find. es and nl match.
  - This text is older than the range, but `84a89426` rewrote the line in all seven.

**What mitigates it.** It is text only. Every rule and notice that these describe behaves as the code says.

---

### TDA3-D1 - TDA2-C1, the fixed half: the disposition holds, and the deferred half is recorded where the disposition says

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **No train is refused or admitted differently.** `baf8856c` changes only `tightestUnmeasured`. No line touches `tightest`, `wayRound` or the gate. MT-571's census was re-run at a build that includes it (`c294b.log`, 18:56), and its figure is still 9.
- **The count, checked by hand at `Layout.java:10499-10513`.** It counts three disjoint groups of edges:
  - the edge the place was left in, where the route ran on in it;
  - the edges wholly between, `leftIn+1..i-1`;
  - edge `i`, where the head ran over something of it first.

  Each is counted only where the whole edge sums to 0, so *"It never counts a measured one"* holds. `edgeWhenLeft` and `leftAtTheEdgeEnd` are put together (`:10519-10531`) and cleared together (`:10541-10544`). A body seed has `leftIn` -1, which short-circuits before the Boolean is unboxed, so no null is unboxed.
- **The claim tests the finding's own failure.** Its first half is TDA2-C1's Fixture 1: `[Q:1]`, `[R:0, B:0]` at 20 must carry the note with 1. `ot2.log`, from before `f652c14f` was committed, shows 5 run and 1 failing. That failure is the claim; by hand, the pin beside it passes.
- **One record note.** TestNG stops at the first failing assertion, so the claim's second half (the stretch the place was left in) was never seen red on the unfixed code. R2d proves it: R2d removes the leaving count, and the method goes red with its first half passing. TDA2-D2 made the same note about OT3.
- **Each mutation undoes one half.** R2c and R2d are each red on the half they undo (`mutr2.out`).
- **The deferred half.** It is recorded in the store as "Open - deferred" and in the rule's javadoc (`:10414-10419`). It is not yet in `issues.md`. FANOUT has "left for a later session" items worked or filed there once the round's report lists them, so this is due at the report, not yet a finding.
- **The reason for deferring is sound by reading.** `GraphReducer.deriveLocks` locks every pair of edges that share any tile, except the two directions of one run (`:1451-1452`). The stem before a switch is therefore shared as well, so shared places alone do not show where a piece ends.

---

### TDA3-D2 - TDA2-C2, with the pins for TDD2-C4 and TDD2-C5: the disposition holds

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **The survivor is the mutation now run.** R2e is exactly `muta1.json`'s surviving A1b: `walkStandingTrains(covered, places, null)` in `tailsOfEachStandingTrain`. R2f is the second form. Both are red on the class, each on both Return Home methods (`mutr2.out`).
- **The third train is what makes R2e visible.** Under R2e every record holds every tail. The third train is unmoved, on a rail of its own, and its record then carries the second train's `inTwo`, so `passesTheTailsOfTrainsThatHaveNotMoved(inTwo, one, twoGone)` is false. With the code as it is, its record is `inThree` alone, and the answer is true. Which assertion fires is the text point in TDA3-C3.
- **The sensor pin (TDD2-C5).** With either train moved, the switch's sensor stays shut; with both moved, it is free. It is red under R2e and R2f. A sensor is freed only when every tail that reached it has gone, per train and per end (`HomeStaging.java:1902-1946`).
- **They are pins, not claims.** `tt.log` shows the class green at the pin commit, which is what a pin is. The dispositions call them pins.

---

### TDA3-D3 - TDA2-C3: the disposition holds

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **By hand.** The loop is `[Q2:2, SW:0, L1:3]`, `[L2:3, SW:0, Q2:2]` with no body.
  - SW is left at 2 and returned to at 8, so its figure is 6.
  - Q2 is also left at 2 and returned to at 8, so it too gives 6. That is not tighter, and on a tie the first return found (SW) is the one kept.
  - A seven-unit train is refused naming 6, and a six-unit train is cleared (`length <= tightest`).
- **The mutation is the finding's own.** R2b deletes the `freeOnceTheTailPasses.put` that TDA2-C3 named, and it is red on this pin and on `testTheStretchTheTrainComesBackInIsCounted`.
- **Only the route's timing can refuse.** The pin has no body, so only the route's own timing can refuse it, and that is the case the finding said nothing covered.

---

### TDA3-D4 - TDA2-C4: "Measured, not a cost" holds, as far as the times can say it

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **The records are fresh.** The JUnit XML in `timing/head/` and `timing/noown/` for the four classes come from the two runs: they are stamped 02:01-02:05 GMT, and the pairs hash differently. The unchanged `testHomeStaging` XML is byte-identical and stamped 01:35 in both folders, which is what a stale file looks like, and none of the four is stale.
- **Most of the plan test's time is fixed by the budget.** `testReturnHomeFindsAPlanOnAFullRailway`'s plan test took 13.920 s with the check and 14.096 s without it.
  - The shortest-plan shares run to their deadlines on that railway: 10 s if the budget is split in thirds, 7.5 s if in halves (`HomeStaging.java:863-898`). That part is the same by construction.
  - The part that could show a cost is the weighted share, which is the one that finds the plan. It took about 3.9 s (or 6.4 s) with the check, and 0.18 s longer without it, so there is no cost to see.
- **The other classes, one run each.** `testTrainsComeHomeToTheirPlatforms` took 42.737 s against 41.198 s, 3.6 % faster without the check. `testTrainsComeHomeFromAPinnedArrangement` took 38.121 s against 38.094 s. With one run each, "within run-to-run noise" is asserted, not measured.
- **What the times cannot say.** They cannot say how far a budgeted share gets before its deadline. Where a shortest-plan share times out anyway, as on the full railway, examining fewer states changes nothing. On an arrangement that a shortest share only just solves, it could. For that, TDA2-C4's request (states examined per share) is still the measurement.
- **Worth keeping.** On the full railway the plan is found about a second before the budget ends, so any future per-candidate cost will show there first.

---

### TDA3-D5 - TDA2-C5: the disposition holds

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **The three removed checks were dead.** `walkStandingTrains` skips every train the predicate refuses (`Layout.java:7196`) and fills its maps only with the train it walks. The predicate `!other.equals(loc)` and the removed `x.equals(loc)` are the same call on the same objects, so no value in the maps can equal `loc`, and that holds for a null `loc` too. Removing them changes no answer.
- **"A train never blocks itself" now rests on the filter.** In `isPathClear` the filter alone carries it. A revert of the filter (A1a) is still red on the TDD-A1 claim by the same reading as before: with the first train recorded on S, its way out is cleared, which the claim refuses.
- **`Layout.tailsOn` has gone cleanly.** No reference is left in `src/`, `test/`, `docs/reference/` or `Automation.md`, and the source-shape strings in `testHomeStaging:259-267` are still in the code.
- **One exclusion is still in the code, and it is harmless.** `anotherTailOn` keeps its own mover test. It is dead in `isPathClear`, and dead in `passesTheTailsOfTrainsItHasMoved` too, where the places are the moved train's own walk and that train is never the mover.
- **The planner's pruning comment is right.** The reason given at `HomeStaging.java:1335-1340` holds: `tightest` is never reset, and a turn clears only the maps, after that edge's returns have been counted. So an extension keeps every return and its figure.
- **One exception, found by reading and not raised.** An edge whose places and lengths differ in count makes the rule return null for the whole route (`Layout.java:10466`), throwing away a refusal already found on the prefix. Only a hand-damaged file can have such an edge; the build writes both lists together.

---

### TDA3-D6 - TDA2-C6 and TDA2-C7: the fixes do what the dispositions say, on the findings' own inputs

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **Half-measured.** The notice counts only the holes before the stop (`berthTrackBeforeTheStop`, `AutonomySession.java:9599-9622`). Whether it judges at all is still asked over the whole leg (`:9697-9707`), which matches the berth rule's `anyMeasured`, taken over every span (`Layout.java:10204-10213`). TDA-C7's crossing fixture now gives 1, only 6,1.
- **Run-in.** TDA2-C6's own input (7,1 = 1, 6,1 = 1, 5,1 = 1, 4,1 = 3, maximum 3) now gives `{3, 2}`, against a room walk of 6 that counts the crossing and the berth.
  - Where the stop is a switch or a permanent turnout, both walks stop at the same tile and count the same squares, so no ordinary berth's figure changes.
  - A platform autonomy may choose keeps the room walk's figure, and the control at `testMassAssignLengths:2269-2275` pins that gate.
  - What the fix did not reach is TDA3-C1.
- **The records.** `c6r.log` shows 2 failing at the claim commit, and `c6f.log` is green across the bundle, session and berth classes. R2g and R2j are each red on the claim they name.
- **The bundles.** I read the six keys in all eight bundles.
  - The half-measured notice says "the length rules" and "switch or crossing" in each.
  - The run-in notice says "switch, crossing or reversal".
  - The berth rule's partly-unmeasured note names no rule.
  - Both refusals are now followed by ". ", and neither refusal sentence ends in a stop in any language, so no stop is doubled. These two are the only concatenations of this kind in `src/`.
- **The javadoc.** The heading and the OB-288 block are corrected as the disposition says. The summary line beside them was left, and is in TDA3-C3.

---

### TDA3-D7 - TDA2-C8: the disposition holds

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **The two figures.** `behaviour.md` 5c (`244331ce`) now gives the two figures separately, and both match the code:
  - for a place the body lies on, the measured track the head runs, plus the body in front of that place: `travelled - free`, with `free = -reach`;
  - for a place the route ran over, the measured track since the head left it: `free` is the distance run after the place's own span.
- **When a return is judged.** Only where the route itself has measured something since leaving the place (`Layout.java:10489`), which is what 5c now says.
- **The doors.** A destination refused on every route is not offered, and that sentence is right. `Automation.md`'s new paragraph matches too, apart from the caveat in TDA3-C3.
