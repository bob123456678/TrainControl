# TDA5 - Automation lane, round 5: the dispositions of TDA4, and what the round-4 fixes left behind

**Status:** open

**Prefix:** TDA5

**Reviewed:** branch `autonomy-diagram-r0` at `1d22c83f`, 2026-09-24. The range is the round-4 fixes, `7c38b51a..1d22c83f` (`114f1600`, `665569f3`, `6c7d5373`, `1d22c83f`). Read-only throughout.

**Method.** I read `docs/reviews/FANOUT.md` and `docs/reviews/README.md` first, then `docs/reviews-2026-09-24/TDA4.md` in full, with the TDD4-C2 and TDD4-C3 entries of `TDD4.md` that share TDA4-C4's fix. I read `git log --stat` for the range and `git show` for every part of it in this lane: from `665569f3`, the two new tests in `testMassAssignLengths` and the javadoc change in `testATailIsNotHiddenByAnother`; from `6c7d5373`, `AutonomySession`, `AutonomyChecks` and `behaviour.md`, and the `TailCrossedPrompt` and `TrainControlUI` parts only far enough to see that they do not change `Layout`; from `1d22c83f`, `open-questions.md`, `behaviour.md`, `issues.md` (OB-298), the `tests.md` head and MT-579, and the TDA4 rows of `findings.tsv`. `114f1600` touches no file in this lane. Then the code at HEAD. In `AutonomySession`: `anythingMeasuredOn`, `berthTrackBeforeTheStop`, `stationsWithAHalfMeasuredApproach`, `runInsShorterThanTheBerth` and its javadoc, `endsTheBerthsRoom`, `takesNoLength`. In `AutonomyChecks`: the run-in and half-measured javadocs, `Finding`'s second number, and where the run-in finding is put together (`:1135-1150`); and how the editor and viewer panels word it. In `GraphReducer`: `boundsTheRoom`, `roomAfterTheLastSwitch`, `crossesASwitch`, `getLength`, and the edge list OB-298 is about. In `TilePorts`: the port maps of `SWITCH_LEFT`, `CROSSING`, `CURVE`, `FEEDBACK` and the four permanent turnouts, and the rotation. In `Layout`: `whyABerthCannotHoldIt`, `whyTooLongForThisRoute`, `measuredRoomAtTheEndOf`, `tailsOfEachStandingTrain` and `walkStandingTrains`. In `HomeStaging`: `passesTheTailsOfTrainsThatHaveNotMoved` and `liesAcrossAtStart`. Tests read: `testMassAssignLengths` 2180-2640 (every crossing, switch and turnout test and all three fixtures), and `testATailIsNotHiddenByAnother` in full. I traced the geometry of each fixture by hand from the port maps. Without changing anything, I read the round's records in the session scratchpad: `mutr4.json`, `mutr4.out`, `r4red.log`, `r4red2.log`, `r4red3.log`, `r4fix.log`, `rc4.log`, `bk4.log`, and R2e and R2f in `mutr2.json` and `mutr2.out`. From the frozen railway (`test/layouts/live-snapshot/`) I read `setup.json`'s keys, its pages and `excludedPages`, and counted the element types on the five pages (the six permanent turnouts are all on `5 - Test`, which is excluded). **Nothing was executed**: no JVM, no test, no `one.sh`, `battery.sh` or `mutate.py`, and no git command that changes state. I did not open `cs2_sample_layout/`. `mutr4.out` ends, as `mutr2.out` did, with a `git diff` showing uncommitted changes to Adam's two autonomy files there (172 and 21 lines, the same as in round 2); no commit in the range touches them, and it is not graded. Every finding that rests on behaviour I could not see by reading carries a verification request.

Grades: A is wrong behaviour on the layout, or data lost. B is incorrect results in specific configurations. C is a notice or text that is wrong, a narrow case, or a guard that proves less than it says. D is checked and clean.

---

### TDA5-C1 - The run-in notice's 0 warns of a refusal no rule makes where every road over the stop ends at the berth: at every permanent turnout, and at a switch whose toe faces the berth

| | |
|---|---|
| **Disposition** | Fixed - claim testNoZeroWhereNothingRefuses in 9fd15b6f (red first), fix 0cf64c18: a berth's stop is the berth rule's only where another road, not starting or ending at the berth, runs over the square; behind a turnout or switch whose other road ends at the berth the run-in notice says nothing.  Mutation R5a red.  The crossing half narrows TDA3-C2's limit without closing it (f83eb22c): the reduction has a road between two sensors no train can reach, which the build drops - a pin for it came out red and is not kept. |

**Where.** `AutonomySession.java:9843-9849`, the branch that takes the berth rule's figure (`32a75f8d`). It takes it wherever `berthTrackBeforeTheStop` reaches a square `endsTheBerthsRoom` (`:3894-3902`) accepts: a switch, a permanent turnout or a crossing. `6c7d5373` documents the result as *"0 where every square before the switch or crossing that ends its room was answered 0"* (`AutonomyChecks.java:328-330`, `behaviour.md:1473-1477`). It pins it at one switch (`testNothingSpentBeforeTheSwitchIsSaidAsNothing`).

**Why the 0 is true on the pin and not in general.**
- **What the berth rule refuses on.** It refuses only on a road that shares a claimed place and neither starts nor ends at the berth. Its own way in and out is skipped (`Layout.java:10246-10266`, the skip at `:10250-10253`).
- **The pin's switch.** `SWITCH_LEFT` is toe S, straight N, branch W at orientation 0 (`TilePorts.java:292-294`), and orientation 3 turns it once clockwise. So at 3,1 the toe faces west and the berth 7,1 is on the straight side. The other road through the switch, 1,1 to 3,0, touches neither end of the berth. The rule claims the switch and refuses there, so the 0 holds, as TDA4-C1 said.
- **Every road over a turnout uses its toe:** left, right, Y, three-way or permanent. A double slip and a crossing have no toe. Where the toe faces the berth with no Point between them, every road over the turnout either arrives at the berth or leaves it, and the rule skips all of them. It then claims on past the turnout, spends what is measured beyond it, and refuses only if something further back is shared.
- **A permanent turnout is always that way round.** It may only be entered from a branch and left at the toe (`TilePorts.java:88-93`, `:317-326`), so a berth reached over one is on its toe side. The berth rule never refuses on a permanent turnout's square.
- **The room rule does not refuse either.** With nothing measured between the stop and the berth, the room walk answers -1 (`GraphReducer.java:1274-1276`; `boundsTheRoom` includes permanent turnouts, `:1215-1219`). `measuredRoomAtTheEndOf` then bounds the train by the whole edge's measured length (`Layout.java:10856-10876`). That is Adam's decided rule (`open-questions.md:165-167`).

**What the operator sees.** Take the existing fixture `openBerthBehindALongerRun(componentType.CUSTOM_PERM_LEFT, key(1, 1))`: the permanent turnout at 3,1 lets trains run from 7,1 to 1,1 only, as the test beside it says (`testMassAssignLengths.java:2189-2190`). Answer 1,1 and 2,1 with 0, set 4,1 to 5 and the maximum to 3.
- **The notice, by hand.**
  - The leg from 7,1: `berthTrackBeforeTheStop` stops at the turnout with `{0, 0, 1}`. 4,1 is measured, so the gate passes. The room walk says -1, so `room <= 0` takes the branch and the notice gets `{3, 0}`.
  - The leg from 3,0 has nothing measured and is skipped.
- **What the editor says:** *"... only 0 of track is measured between it and the switch, crossing or reversal behind it - so a train longer than 0 is refused here whatever the maximum says"*.
- **What the railway does.** A three-unit train is admitted.
  - The berth rule's only other road over the turnout, from 3,0, ends at the berth.
  - The room rule's bound is the edge's 5.
- **Before `32a75f8d`** there was no notice: the -1 was skipped as unknown. That was the right answer, since 5 is not short of 3.

**What round 4 left.**
- TDA4-C1 checked a berth off the switch's toe. Of the other case it said only *"where the switch's other road carries no rail"*. Here the other road carries rail, and the rule still does not refuse.
- The fix widened the documents to *"the switch or crossing that ends its room"*. Two limits still name only a crossing that no train can cross: `endsTheBerthsRoom`'s known limit (`:3886-3889`) and `open-questions.md:175-178`.

**What mitigates it.**
- It is a notice, the lowest grade. No train is sent anywhere differently.
- It needs every square between the berth and the stop answered 0, and something measured beyond. Answered zeros arrived on 2026-09-23 (OB-274).
- The frozen railway has no answered zeros. Its six permanent turnouts are all on `5 - Test`, which its setup excludes. Whether the operator's current railway has such a berth cannot be told by reading.
- It matters more than its grade because of TDA5-C2: the recommended answer to TDA4-C2 would turn this sentence into a warning that the berth "takes no train".

**Verification request.**
- **Fixture.** `openBerthBehindALongerRun(componentType.CUSTOM_PERM_LEFT, key(1, 1))`, then `answerTileLengthsZero(Arrays.asList(key(1, 1), key(2, 1)))`, `setTileLength(key(4, 1), 5)`, a maximum of 3 on 1,1, and `rebuild()`. Build the railway as `testTheBerthRuleRefusesAtTheCrossing` does, and take the edge into 1,1 whose places include 4,1.
- **Proves it.** `runInsShorterThanTheBerth()` maps 1,1 to `{3, 0}`, and for a three-unit train both `whyABerthCannotHoldIt` and `whyTooLongForThisRoute` return null on that edge.
- **Refutes it.** No entry, or either rule refuses the three-unit train.
- **The throwable case.** The same fixture with `SWITCH_LEFT` at 3,1, and its routes allowed into the toe so that a train can reach 1,1.
- **The likely fix.** Take the berth rule's figure only where a reduced edge that neither starts nor ends at the berth runs over the stop's square. That is the rule's own question, asked of `reducer.getEdges()`, and the same test would settle TDA3-C2's crossing limit. Pin it on this fixture, asking the rule as well as the notice. The half-measured count can keep its stop, because there the room rule refuses once anything before the stop is measured.

---

### TDA5-C2 - The question put to Adam for TDA4-C2 says the berth "takes no train", and recommends saying so at a warning's grade: the wording OP2-C12 took out of the half-measured warning

| | |
|---|---|
| **Disposition** | Fixed - 0cf64c18: the question put to Adam says the berth refuses every train with a length there, and states when. |

**Where.** The question is at `open-questions.md:140-145` (`1d22c83f`); TDA4-C2's row is "Open - Adam's decision".
- **The premise:** *"Where every square between a parking berth and the switch or crossing that ends its room was answered 0, the berth takes no train"*.
- **The recommendation:** a sentence of its own at the warning's grade, *"X takes no train: ..."*.

**The premise is false in four cases, and the recommended sentence would be a false warning in three of them.**
- **Nothing else on the leg is measured.** This is the case TDA4-C3's pin holds in the same round. The rule does not judge that leg and admits every train (`Layout.java:10204-10213`). The notice keeps that gate, but a new check written from the question's words would not have it. That is the drift TDA4-C3 was about.
- **A berth on the toe side of its stop**, which includes every permanent turnout (TDA5-C1). Trains up to the edge's measured length are admitted.
- **A crossing no train can cross** (TDA3-C2's limit, `open-questions.md:175-178`).
- **A locomotive with no train length.** Both rules return at once and admit it (`Layout.java:10168`, `:10630`).

OP2-C12 (closed 2026-09-20; `AutonomyChecks.java:387-392`) made this same correction on the half-measured warning, at the grade the question recommends, for two of these same reasons. The text went from *"takes no train at all"* to *"can refuse, not does"*. A lock edge must share a claimed place, and a locomotive with no length is admitted. The question does not mention it.

**What mitigates it.**
- Nothing has been built.
- The question is still waiting for Adam.
- The run-in notice itself fires only where the gate holds.

**The likely fix.** Amend the question before he answers:
- Word option (b) as "can refuse every train with a length", or say it only where the rule can refuse (after TDA5-C1).
- Cite OP2-C12 beside option (b).
- State the leg gate, so that whoever builds (b) keeps it.

No execution is needed to confirm this. It is text against text.

---

### TDA5-C3 - Three statements of the 0 rule leave out the gate TDA4-C3 pinned, and the method's own javadoc still reads as a crossing only

| | |
|---|---|
| **Disposition** | Fixed - 0cf64c18: behaviour.md, the runInsShorterThanTheBerth javadoc and the AutonomyChecks javadoc state both conditions - something on the leg measured, and another road over the stop. |

**The three statements.**
- **`behaviour.md:1475-1477`:** *"with nothing measured before the switch or crossing that ends its room the notice says 0 where those squares were answered 0"*.
- **`AutonomyChecks.java:329-330`:** *"and 0 where every square before the switch or crossing that ends its room was answered 0"*.
- **`AutonomySession.java:9769-9772`, `runInsShorterThanTheBerth`'s javadoc:** *"at a crossing between the berth and its switch, or on a leg with no switch at all (TDA2-C6, TDA3-C2) - and 0 where nothing before that stop is measured and its squares were answered 0"*. "That stop" here is the crossing or the leg with no switch. The switch, which TDA4-C1's fix added to the other two, is missing, and TDA4-C1's disposition names only those two.

**What the pin shows.** `testALegNothingMeasuresIsNotSaidAsNothing` (`665569f3`) answers 7,1 and 6,1 with 0, with the crossing next. So every square before the stop is answered 0, and nothing else on the leg is measured.
- All three texts say 0.
- The code says nothing, and rightly: the berth rule does not judge that leg. Adam, `behaviour.md:996-997`: *"a stretch whose answers are all 0 is still not judged"*.
- Only the inline comment (`:9827-9830`) and the new helper's javadoc state the gate.

**A smaller item of the same kind.** `AutonomyChecks.java:96` says the second number is *"zero when this finding is not a comparison"*. Since `32a75f8d` the run-in comparison's second number can be 0. Nothing reads a 0 as "no second number": the editor (`AutonomyEditorPanel.java:9563-9564`) and the viewer (`AutonomyViewerPanel.java:1621-1622`) both word it uniformly. So only the sentence is wrong.

**What mitigates it.** It is text only. The code, the pin and R4i agree. But `behaviour.md` is where Adam reads the intended behaviour, and a reader of it would expect a notice that the code rightly withholds.

**The likely fix.**
- Add "once anything on the leg is measured" to each of the three.
- Say "the switch or crossing that ends its room" in the javadoc.
- Carry TDA5-C1's answer into all three once it is settled.

---

### TDA5-D1 - TDA4-C1: the disposition holds as far as it goes

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **The text change.** `6c7d5373` changes `behaviour.md:1475-1477` and `AutonomyChecks.java:328-330` to "the switch or crossing that ends its room", as stated. The citations are right: TDA2-C6 and TDA3-C2 for the crossing and the leg with no switch, and TDA3-C1 and TDA4-C1 for the 0.
- **The pin is the finding's own fixture and input.** By hand:
  - One leg arrives at 7,1, from 1,1. From the branch a train leaves by the toe, so nothing from 3,0 reaches 7,1.
  - `berthTrackBeforeTheStop` gives `{0, 0, 1}` at the switch, 2,1 = 1 passes the gate, and the room walk's -1 takes the branch. The result is `{3, 0}`.
- **The rule agrees on this fixture.** The toe faces west, so the road from 1,1 to 3,0 shares 2,1 and 3,1 and does not touch the berth. A one-unit train claims 7,1 back to 2,1 and is refused. The pin does not ask the rule, as TDA4-C1's request did, but by reading it would pass.
- **The mutations.**
  - R3f, run again this round, is red on the pin (`mutr4.out`).
  - Restricting the branch to crossings, which TDA4-C1 warned against, leaves the room walk's -1 and so no entry. The pin catches it by reading.
  - The pin was green before the fix (`r4red.log`: 64 run, 0 failing). That is right for a pin of behaviour that `32a75f8d` already had.
- **What it did not reach** is TDA5-C1, and the third statement in TDA5-C3.

---

### TDA5-D2 - TDA4-C3: the disposition holds

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **One helper.** `anythingMeasuredOn` (`:9600-9610`) now answers both notices (`:9726`, `:9843`). The loop it replaced in the half-measured notice set the same flag over the same squares with the same route-tile skip, so that notice's answers are unchanged. The `false` it still starts from is now dead, and harmless.
- **The pin, by hand.**
  - The fixture is `openBerthBehindACrossing()`, with 7,1 and 6,1 answered 0 and 5,2 = 1 on the crossing's other road.
  - The only leg into 7,1 is from 1,1: the branch and the straight meet only at the toe.
  - Nothing on that leg is measured, so the branch does not fire, and the room walk's -1 is skipped.
  - Under R4i the branch fires with `{0, 0, 1}` at the crossing and gives `{5, 0}`.
  - `mutr4.out`: R4i is red on this test alone, 64 run.
- **Why 5,2 = 1 is there.** Without it, `measuresAnyTrack` returns the empty map first, and the assertion holds whatever the gate does. With it, R4i is red, so the pin does its job. It has no precondition assert that says so, which is worth adding if `measuresAnyTrack` ever changes.
- **The rule does not judge the leg.** Every span on it is 0 (`Layout.java:10204-10213`), so a train is admitted, and the pin's javadoc is true by reading.
- **The hand-off.** The run-in notice's `continue` (`:9845`) and the half-measured count now ask one gate. Their other gates still match, as TDA4-D1 read them. The second half of `testNothingSpentBeforeTheCrossingIsSaidAsNothing` asserts the hand-off.

---

### TDA5-D3 - TDA4-C4: the disposition holds, by reading

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **`testATailIsNotHiddenByAnother.java:36-41`**, traced at HEAD against R2e and R2f as `mutr2.json` gives them.
  - **R2e** gives each train the whole walk (`Layout.java:6944`, with a null filter). The claims at `:249` and `:253` still pass, because the other train lies across and has not moved. At the control (`:262`), the third train's record holds `outOne`, and `liesAcrossAtStart` answers true at `covered.containsKey(edge)` (`HomeStaging.java:1711`). The third train has not moved, so the control fails: "the unmoved third train is charged with the first train's own rail", as the line says.
  - **R2f**: the train walked last owns S. The other keeps only its own square, so `tailLiesOn` finds none of it on the other train's way out, and one of the two claims fails: "the train that does not own the switch's square losing it".
  - Both methods are red under both mutations (`mutr2.out`).
- **Not executed.** `mutr4.json` does not run R2e or R2f again, so TDD4-C2's request (read the two failure messages) is still waiting for the closing mutation run. Three readers now agree on where each one fails.
- **The other two items.** `behaviour.md:1478-1480` now carries the javadoc's exception, and the two bullets agree. The `AutonomyChecks` citation is corrected.

---

### TDA5-D4 - TDA4-C2: recorded as Adam's decision where FANOUT puts it

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **The store.** The row reads "Open - Adam's decision" in `findings.tsv`, and it is the only TDA4 row not Closed.
- **The documents.** The question is in `open-questions.md` under Length, with three options and a recommendation, and the commit message says it was put to Adam.
- **The document's status line.** It is `open`, which is right while the question stands.
- **The question's own wording** is TDA5-C2.

---

### TDA5-D5 - The rest of the range, in this lane

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **No behaviour change in `automation/` or `automationui/`.**
  - `6c7d5373` changes two javadocs there and moves the gate into a helper.
  - `665569f3` adds two pins and a javadoc.
  - `114f1600` is harness only.
  - `1d22c83f` is records.
- **The counts.**
  - 3,726 + 336 + 13 + 223 = 4,298 rows, and 173 + 50 = 223.
  - 3,891 + 50 = 3,941 findings, so the 357 more rows than findings is unchanged.
  - `rc4.log` shows `testTheRecordsCountTheStore` green.
- **OB-298's facts hold.** `GraphReducer` keeps one `ArrayList` of edges and clears it in place (`:334`, `:388`), and `AutonomyBuilder.splitSides` iterates it (`:463-467`). Whether any door builds off the event thread is the UI lane's question.
- **The late tail answer.** `runningNow` and `sameSetup` change which railway and which session the door asks. They do not change `Layout`, and `writeRoad`'s translation through `roadNamed` is as TDA4-D5 read it.
