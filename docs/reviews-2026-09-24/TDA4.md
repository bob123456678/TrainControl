# TDA4 - Automation lane, round 4: the dispositions of TDA3, and what the round-3 fixes left behind

**Status:** closed

**Prefix:** TDA4

**Reviewed:** branch `autonomy-diagram-r0` at `7c38b51a`, 2026-09-24. The range is the round-3 fixes, `67f79912..7c38b51a` (`6bfab5bb`, `32a75f8d`, `59cf3645`, `7c38b51a`). Read-only throughout.

**Method.** I read `docs/reviews/FANOUT.md` and `docs/reviews/README.md` first, then `docs/reviews-2026-09-24/TDA3.md` in full. I read `git log --stat` for the range and `git show` for every part of it in this lane: from `32a75f8d`, `AutonomyChecks`, `AutonomySession` and the new bundle key; from `59cf3645`, `AutonomySession`, `testMassAssignLengths`, `testAutoLayout`, `Automation.md`, `behaviour.md`, `open-questions.md` and the da, de and fr bundles; from `6bfab5bb`, `testMassAssignLengths`, `testTheOwnTailArithmetic`, `testATailIsNotHiddenByAnother` and `build.xml`; from `7c38b51a`, `issues.md`, `behaviour.md`, `open-questions.md`, the heads of MT-576 to MT-578 and the TDA3 rows of `findings.tsv`. From `32a75f8d` I also read the `TailCrossedPrompt`, `GraphLocAssign` and `LayoutRightclickAutonomyMenu` parts, as far as the road they write reaches the tail walk. Then the code at HEAD. In `AutonomySession`: `takesNoLength`, `endsTheBerthsRoom`, `anythingMeasuredOn`, `berthTrackBeforeTheStop`, `stationsWithAHalfMeasuredApproach` and `runInsShorterThanTheBerth`. In `AutonomyChecks`: the two notices' javadocs and where the run-in finding is put together (`:1134-1149`). In `GraphReducer`: `placesAlong`, `lengthOf`, `boundsTheRoom`, `roomAfterTheLastSwitch` and `buildPoints`. In `AutonomyCompanionStore`: `answerTileLengthZero`, `isTileLengthAnswered` and `setTileLength`. In `Layout`: `whyABerthCannotHoldIt`, `measuredRouteIn`, the static `whyItWouldMeetItsOwnTail`, the switch branch of `measuredRoomAtTheEndOf` and `roadNamed`. Also `Edge.equals` and `Point.equals`, `HomeStaging.passesTheTailsOfTrainsThatHaveNotMoved` and `liesAcrossAtStart`, and how the editor and viewer panels word a finding. Tests read: `testMassAssignLengths` 2060-2110 and 2140-2535, `testATailIsNotHiddenByAnother` in full, the header and new pin of `testTheOwnTailArithmetic`, and `testAutonomyDiagramSession` 3325-3355. I grepped `test/` for every test that answers a zero or asks the run-in notice. Without changing anything, I read the round's records in the session scratchpad: `mutr3.json`, `mutr3.out`, `r3red.log`, `r3red2.log`, `r3fix.log`, `r3txt.log`, `live.log`, `prb.log`, `rc3.log`, `probeCrossingBerth.java`, and the R2e and R2f entries of `mutr2.json` and `mutr2.out`. From the frozen railway (`test/layouts/live-snapshot/`) I read the keys of `setup.json`, its `tileLengths` (158 entries, none of them 0, so no answered zeros), its excluded pages and disabled links, and which pages hold a crossing. I checked the arithmetic by hand. **Nothing was executed**: no JVM, no test, no `one.sh`, `battery.sh` or `mutate.py`, and no git command that changes state. I did not open `cs2_sample_layout/`. As in round 3, `mutr3.out` ends with a `git diff` showing uncommitted changes to Adam's two autonomy files there; no commit in the range touches them, and it is not graded. **The three issue-list items are not in this lane.** OB-286 (`RouteEditorFrame` and the highlight in `TrainControlUI`), FR-098 (`LocomotiveFunctionAssign`) and OB-287 (`TrainControlUI.refuseWhileARouteDrivesIt`, which asks the model's running route) are all in `gui/` and the model, and none of them reaches `automation/` or `automationui/`. I read their diffs only far enough to see that, and leave them to the UI lane. Every finding that rests on behaviour I could not see by reading carries a verification request.

Grades: A is wrong behaviour on the layout, or data lost. B is incorrect results in specific configurations. C is a notice or text that is wrong, a narrow case, or a guard that proves less than it says. D is checked and clean.

---

### TDA4-C1 - The run-in notice's new 0 is said behind a switch as well as behind a crossing, and every document says crossing

| | |
|---|---|
| **Disposition** | Fixed - pin testNothingSpentBeforeTheSwitchIsSaidAsNothing (665569f3) on the finding's own fixture, {3, 0}; behaviour.md and the AutonomyChecks javadoc say the switch or crossing that ends the berth's room (6c7d5373). |

**Where.** `AutonomySession.java:9846-9852` (`32a75f8d`):

```java
if (before[2] == 1 && anythingMeasuredOn(squares) && (room <= 0 || before[0] < room))
{
    if (before[0] == 0 && before[1] > 0) continue;

    room = before[0];
    theBerthRulesFigure = true;
}
```

`berthTrackBeforeTheStop` stops wherever `endsTheBerthsRoom` says the room ends: at a switch, a permanent turnout or a crossing (`:3894-3902`). The branch takes its figure whatever the stop is. Take a parking berth behind a plain switch, with every square between the two answered 0 and something beyond the switch measured.
- **Before `32a75f8d`.** The room walk answers -1 for those squares (`GraphReducer.java:1274-1276`: nothing measured, so -1). The old guard `before[0] > 0` was false, so the -1 stood and was skipped as "unmeasured is unknown" (`:9867`). The notice said nothing.
- **Now.** The branch fires, `theBerthRulesFigure` lets the 0 past that guard, and the notice says 0.

**The 0 is true.** Something on the approach is measured, so `whyABerthCannotHoldIt` judges it (`Layout.java:10204-10213`). It claims the answered squares for nothing, claims the switch, and refuses on the branch's rail: every train. The room rule already refused anything longer than the whole edge's length (`Layout.java:10856-10876`). So nothing about the railway changes; a new notice appears.

**What the documents say instead.**
- `behaviour.md:1473-1476`: *"with nothing measured before that crossing the notice says 0 where its squares were answered 0"*. A crossing only.
- `AutonomyChecks.java:328-329`: *"and for a parking berth to a crossing, where the berth rule stops there first, which makes the figure the berth rule's (TDA2-C6, TDA3-C1)"*. A crossing only, no 0, and TDA3-C1 cited for the crossing figure, which is TDA2-C6's.
- `32a75f8d`'s message: *"nothing spent before a berth's crossing is said as 0"*.
- TDA3-D6: *"Where the stop is a switch or a permanent turnout, both walks stop at the same tile and count the same squares, so no ordinary berth's figure changes."* That was true when it was written. Since `32a75f8d` it is not: a berth behind a switch whose squares were answered 0 now gets a figure.
- The claim and both pins test only a crossing.

So an operator who has answered 0s behind a berth will see a new notice, and nothing mentions it. One more edge: where the switch's other road carries no rail, the berth rule does not refuse there. That is the limit TDA3-C2 states for a crossing, and `HALF_MEASURED_APPROACH`'s javadoc states it for a branch (*"CAN refuse, not does"*). The new 0 is then on the warning side, as it is at such a crossing, and `open-questions.md:168` names only the crossing.

**What mitigates it.**
- The figure is the true one wherever the switch's other road is driven.
- Answered zeros arrived on 2026-09-23 (OB-274), and the frozen railway has none (`setup.json`'s 158 lengths include no 0), so no berth there changes. Whether one on the operator's current railway does cannot be told by reading.

**Verification request.**
- **Fixture.** `openBerthBehindALongerRun(componentType.SWITCH_LEFT, key(7, 1))`. Answer 4,1, 5,1, 6,1 and 7,1 with 0 (`answerTileLengthsZero`), set 2,1 to 1 and the maximum to 3, then rebuild.
- **Proves it.** At HEAD, `runInsShorterThanTheBerth()` maps 7,1 to `{3, 0}`; at `6bfab5bb` it has no entry. On the railway built from the session (as `testTheBerthRuleRefusesAtTheCrossing` builds it), `whyABerthCannotHoldIt` refuses a one-unit train and names the branch's road.
- **Refutes it.** No entry at HEAD.
- **The likely fix.** Say "before the switch or crossing that ends the berth's room" in `behaviour.md` and the `AutonomyChecks` javadoc, and pin the switch case beside the crossing claim. Restricting the branch to a crossing would make the notice silent about a berth that takes no train.

---

### TDA4-C2 - The 0 arrives in a notice written for a berth that holds something: "that may be right" and "still needs measuring", at the lowest grade, about a berth that takes no train (Adam's decision)

| | |
|---|---|
| **Disposition** | Fixed - Adam, 2026-09-24: its own sentence, as a warning.  Claims 7dc22256, fix f17f5a5c. |

**Where.** `messages.properties:1396` (`autosetup.ui.checkRunInShorterThanTheBerth`), raised as a NOTICE at `AutonomyChecks.java:1141-1147`. Its grade is argued at `AutonomyChecks.java:333-336` and `behaviour.md:1482-1483`, on the ground that *"his own example is a railway with nothing wrong with it"*. Since `32a75f8d`, the room `{3}` can be 0, and only where every square before the stop was answered 0, the berth's own included. For a berth set to 5, the operator then reads:

*"... is set to take a train of 5, but only 0 of track is measured between it and the switch, crossing or reversal behind it - so a train longer than 0 is refused here whatever the maximum says.  That may be right: a platform of 5 can be two stretches either side of a switch.  Otherwise the track still needs measuring."*

- **"That may be right"** and the platform example are about a berth that holds some train. This berth holds none.
- **"The track still needs measuring"** is said of squares answered on purpose. `behaviour.md:1002-1005`: *"an answered 0 is not listed as missing ... the half-measured berth notice, the reversal notice and the berth refusal's 'N squares still have no length' leave answered squares out"*. This notice now fires only because of them.
- **The grades are upside down.** With the squares unanswered, the same berth is the half-measured WARNING. Answered, it is a NOTICE, the grade given for "nothing wrong".
- **How it came about.** The previous round's likely fix said *"When the squares are answered, quote 0"*, and the fix followed it. The wording question was never put.

**What mitigates it.**
- The figure is true, and the refusal on the railway names the road.
- No train is sent anywhere differently.
- The case is narrow: every square before the stop answered 0.

**The question for Adam.** What should the editor say about a parking berth whose track before its switch or crossing was all answered 0, so that it takes no train?
- (a) Keep the run-in notice's 0 as it is.
- (b) Give it a sentence of its own at the half-measured warning's grade. Something like *"X takes no train: the track between it and the switch or crossing behind it was given no length, so every train reaches it and is refused - give that track its length, or clear X's maximum"*.
- (c) Say nothing, since the operator answered.

Recommended: (b). The berth takes no train, and the operator has told us why. Checked by reading: the notice is worded uniformly from its two numbers (`AutonomyEditorPanel.java:9563-9564`), so the sentence above is what shows. To see it, `session.check()` on the first half of `testNothingSpentBeforeTheCrossingIsSaidAsNothing` should carry that sentence with 5 and 0.

---

### TDA4-C3 - The fix's measured-anywhere gate is a second copy of the half-measured notice's, and no test holds it

| | |
|---|---|
| **Disposition** | Fixed - 6c7d5373: both berth notices ask anythingMeasuredOn; pin testALegNothingMeasuresIsNotSaidAsNothing (665569f3).  Mutation R4i red. |

**Where.** `AutonomySession.java:9598-9608`, `anythingMeasuredOn`, new in `32a75f8d`. And `:9721-9731`, the same loop written inline in `stationsWithAHalfMeasuredApproach`. Both stand for the berth rule's own gate (PRW-B1, `Layout.java:10204-10213`): an approach with nothing measured is not judged.

1. **Two copies of one rule, and the fix leans on their agreeing.**
   - The run-in notice's `continue` at `:9848` hands a berth with unanswered squares before its stop to the half-measured warning.
   - That warning fires only if its own copy of the gate says the leg is judged. Today the two copies are identical, and the warning's other gates match the run-in's too (D1).
   - If one copy is changed and the other is not (say, taught to leave out the berth's own square, or answered squares), that berth gets neither notice. That is TDA2-C6's shape again.
   - The helper was written beside the loop and is not called from it.
2. **Nothing holds the run-in's copy.**
   - The gate decides anything only where every square before the stop is answered 0 and nothing on the leg is measured.
   - There the rule does not judge the approach and admits every train. Adam: *"a stretch whose answers are all 0 is still not judged"* (`behaviour.md:996-997`). So the notice is right to stay silent.
   - The one test with answered zeros before a stop is `testNothingSpentBeforeTheCrossingIsSaidAsNothing`, and it measures 4,1 = 3 on the same leg, so the gate is true there.
   - By reading, replacing `anythingMeasuredOn(squares)` with `true` leaves `testMassAssignLengths` and `testAutonomyDiagramSession` green. The editor would then tell the operator *"a train longer than 0 is refused here"* at a berth that refuses nothing.
   - R3e undoes only the `continue`.

**What mitigates it.** The code is right by reading. What is missing is the guard, and the single place to state the rule.

**Verification request.**
- **The gap.** Replace `anythingMeasuredOn(squares)` with `true` at `:9846` and run `core.testMassAssignLengths`. Green proves the gap; red refutes it.
- **A claim for it.** Take `openBerthBehindACrossing()`. Answer 7,1 and 6,1 with 0, set 5,2 to 1 (the station on the crossing's other road: the railway then measures track and this leg does not), set the maximum to 5, and rebuild.
  - Expected: no 7,1 in `runInsShorterThanTheBerth()`. On the built railway, `whyABerthCannotHoldIt` returns null for a one-unit train, because the rule does not judge the approach.
  - Under the mutation: `{5, 0}`.
- **The likely fix.** One helper, called from both notices.

---

### TDA4-C4 - Text the round-3 fixes left behind or made false

| | |
|---|---|
| **Disposition** | Fixed - 665569f3 and 6c7d5373: the class javadoc says R2e fails at the control and R2f at the two-train claims; behaviour.md's second bullet takes the javadoc's clause; the AutonomyChecks citation corrected. |

- **`testATailIsNotHiddenByAnother.java:36-40`** (`6bfab5bb`, fixing TDA3-C3). The line reads: *"give each train Return Home's whole record of the starting tails (R2e), or keep one owner per place in it (R2f), and `testReturnHomeAsksAboutEveryTailOnThePlace` fails where the third train is blamed for the rail the second one's tail lay along"*.
  - That is true of R2e.
  - R2f (`mutr2.json`) walks every train into one map, so the train walked last keeps S, and then gives each train only the entries it owns. So one of the first two trains' records lacks S.
  - `liesAcrossAtStart` then finds no one on the way out through S (`HomeStaging.java:1706-1755`), and the method fails at `:248` or `:252`. That is the TDD-A1 claim itself, before the method reaches the third train at `:266`.
  - `mutr2.out` names only the method, so the record cannot settle it either way; this is by reading.
- **`behaviour.md:1477-1478`.** *"An arriving edge crossing no switch is skipped unless trains turn round where it starts."* The bullet above it now says that a parking berth's leg with no switch and a crossing is not skipped (TDA3-C2). The javadoc gained that exception (`AutonomySession.java:9777-9778`); `behaviour.md` did not, so two bullets in a row now disagree about the same leg.
- **`AutonomyChecks.java:328-329`** cites TDA3-C1 for the crossing figure. That figure is TDA2-C6's, and the leg with no switch is TDA3-C2's. TDA3-C1 is the 0, which the sentence does not mention. This is counted under TDA4-C1 and listed here only for the citation.

**What mitigates it.** It is text only. The mutation still fails the class, and the rule and notice behave as the code says.

---

### TDA4-D1 - TDA3-C1: the disposition holds

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **The three cases, by reading.** Take a parking berth where the stop is reached and something on the leg is measured.
  - Nothing is held and some squares are unanswered: no entry (`:9848`).
  - Nothing is held and none are unanswered: 0. `theBerthRulesFigure` lets it past the unknown-is-not-short guard (`:9867`).
  - Something is held: the branch works as it did before.
- **The unanswered half really is named.** For such a berth, `stationsWithAHalfMeasuredApproach` asks the same questions: is it a station, does autonomy choose it, which sides are barred, is anything on the leg measured. Its `before[0] >= longest` skip is false at 0, because the run-in notice runs only with a maximum above 0. So every berth the `continue` drops is in the warning's map, with its unanswered count. The claim's second half asserts this on its own fixture.
- **The gate matches the rule's.** The rule's places are the path plus the end, near end excluded (`GraphReducer.placesAlong`, `:1576-1593`), which is the session's `squares`. Their lengths are `max(0, getTileLength)` (`:1193-1196`). There is one difference, older than the range: a route tile is skipped by the session and not by the rule, and the half-measured loop has had the same difference since OB-273.
- **The claim is the finding's own input, and was red first.** The input is TDA3-C1's: 7,1 and 6,1 answered, then unanswered; 5,1 = 1, 4,1 = 3, 3,1 = 1, 2,1 = 1; a maximum of 5.
  - `r3red.log` shows 61 run and 1 failing at `6bfab5bb`.
  - The failure can only be this claim. The no-switch pin passes on `84a89426`'s `room <= 0 ||`, and the other changes to the class at that commit are assertion messages.
  - By hand, the first assertion sees `{5, 4}` there.
- **The mutations.**
  - R3e deletes the `continue`, and is red on the claim (`mutr3.out`).
  - The javadoc's own mutation, back to `before[0] > 0`, is red by hand at the first assertion (`{5, 4}`).
  - Dropping `&& !theBerthRulesFigure` is red by hand at the first assertion (no entry).
- **Order of the records.** At `6bfab5bb` the claim's fixture had no rail over the crossing (TDA3-C2's disposition). So at the claim commit it asserted a notice about a refusal its own fixture's rule would not make. Since `59cf3645` the premise holds (D2). R3e was run before that change (`mutr3.out` at 20:10, `59cf3645` at 20:17). By reading, the change does not reach it, because the other road meets the berth's leg only at 5,1.
- **Not reachable on the frozen railway.** It has no answered zeros, and its one crossing on an included page lies beyond the switch from every station.

---

### TDA4-D2 - TDA3-C2: the disposition holds, and the fixture change leaves the older crossing claims meaning what they say

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **The premise is asked of the rule itself.** `testTheBerthRuleRefusesAtTheCrossing` builds the railway and asks `whyABerthCannotHoldIt`. By hand:
  - A three-unit train claims 7,1, 6,1 and 5,1. The only rail that shares any of them and does not touch the berth is the crossing road's (4,0 to 5,2). The branch rail shares 2,1 and 3,1, and neither is claimed. So the non-null can only be the crossing's refusal.
  - A two-unit train claims 7,1 and 6,1, which only the berth's own rails share, so it is held.
  - `r3txt.log` (62 run, 1 failing) and then `live.log` (62, green) bear out "it first failed".
- **The fixture change.** The other road is now fed from the switch's branch: a curve at 3,0, a sensor at 4,0, a curve at 5,0, over 5,1 to a station at 5,2, which autonomy may choose by default.
  - Berth 7,1's leg is unchanged, so these are unchanged by hand: TDA-C7's half-measured count (1), TDA2-C6's `{3, 2}`, and its control (a room of 6).
  - 5,2 has no maximum and is autonomy's to choose, so it adds no entry to either map these tests read.
- **The leg with no switch.**
  - `openBerthBehindACrossing(false)` gives `{2, 0, 1}` with a room of `Integer.MIN_VALUE`. The branch takes it, which gives `{3, 2}`.
  - R3f (`room > 0 &&`) is the deletion of `room <= 0 ||` that TDA3-C2 asked for, and it is red on the pin.
  - The fixture now feeds the crossing road from 1,0, so the rule's premise holds on that leg too, by the same reading.
- **The limit.** It is stated where the disposition says: `endsTheBerthsRoom`'s javadoc (`:3886-3889`) and `open-questions.md:168`. On the frozen railway it is not reachable: the one crossing on an included page is `1 - Main` 18,10, beyond the switch.
- **For the closing mutation run.** R2g, R2j, R3e and R3f were all run on the fixture before `59cf3645`. By reading they stay red on the new one.

---

### TDA4-D3 - TDA3-C3: the disposition holds for every item it lists

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **`AutonomySession`.** The summary line and `@return` of `stationsWithAHalfMeasuredApproach` are corrected, and so are both paragraphs of `runInsShorterThanTheBerth`'s javadoc.
- **`AutonomyChecks`.** `:325-331` now names the crossing; its citation is in TDA4-C4. `:381` now says "the berth rule judges".
- **`testTheOwnTailArithmetic`.** The MUTATION line names all six methods, and pairs R2b, R2c and R2d as `mutr2.out` records them. R2b also fails `testTheStretchTheTrainComesBackInIsCounted`, which the line does not say; that is harmless.
- **`testATailIsNotHiddenByAnother`.** Rewritten by name; its R2f clause is in TDA4-C4.
- **`Automation.md`.** Three changes, each true:
  - "usually says how many stretches" carries the caveat;
  - the stretch whose only length is on its switch is named;
  - "with nothing measured round the loop the trip is not checked at all" is true by `Layout.java:10489`.
- **The bundles.** The half-measured notice's da, de and fr text now uses the menu's label exactly: *Tildel længder samlet*, *Längen gesammelt zuweisen*, *Attribuer les longueurs en série*.

---

### TDA4-D4 - The own-tail work in the range: TDD3-C1's far-end pin, and OB-297

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **`testARouteIsTimedFromTheFarEndOfAPlace`, by hand at `Layout.java:10458-10534`.**
  - The route runs Q (1), D (2), E (3), then D again, and D is left at 3. The head is at 6 when it comes back to D, so the way round is 3.
  - A four-unit train is refused naming 3, and a three-unit train is cleared.
  - R3l times D from its near end: D is left at 1, the figure is 5, and the four-unit train goes. So it is red, and `mutr3.out` agrees.
- **OB-297.** It is filed in the Inbox, with the reason it waits, and that reason is true: a place in the configuration carries only `at`, `length` and `answered` (`AutonomyBuilder.java:1282-1298`), so the runtime cannot tell a switch's place from any other. `behaviour.md` 5c and `Automation.md` now state the leg grain, which the rule's javadoc (`:10414-10419`) already did.
- **One bookkeeping note.** TDA2-C1's row stays Open, which is right, but its disposition says only "left for later". It does not name OB-297, where TDA-C9's row says "filed as OB-295". FANOUT has the report say which items were filed, so this is due there, not a finding.

---

### TDA4-D5 - The late tail answer's write, read as far as the tail walk depends on it

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **Which copy is asked.** `TailCrossedPrompt.whereTheAnswerGoes` asks the running railway's copy, by name.
- **How the road is written.** `writeRoad` translates the answered road onto the new railway through `Layout.roadNamed`, and that is all or nothing (`Layout.java:6771-6824`). If a named edge is gone, the whole road is dropped, and the tail walk falls back on the fork rule, which claims less.
- **How the road is compared.** `placementStillStands` compares roads with `Edge.equals`, which compares by name (`Edge.java:255-263`), so an unchanged road on a rebuilt railway compares equal.
- **Nothing in `Layout` changed.** As far as the tail walk reads it, this is clean. Whether each door asks at the right moment is the UI lane's question.

---

### TDA4-D6 - The records for this lane

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **TDA3's rows.** All ten are Closed in `findings.tsv`, with the dispositions the document carries.
- **The counts.**
  - 3,726 + 336 + 13 + 173 = 4,248 (`behaviour.md`, `open-questions.md`), and 173 = 126 + 47.
  - OB-297 is counted into the Inbox: 84 entries, 52 of them OB.
- **The status line.** The document's status line still reads `open` while its rows are Closed. FANOUT sets it when the round closes, as it is for `TDA.md` and `TDA2.md`.
