# TDA2 - Automation lane, round 2: the dispositions of TDA, and what the fixes left behind

**Status:** closed

**Prefix:** TDA2

**Reviewed:** branch `autonomy-diagram-r0` at `7b0d5c2f`, 2026-09-24. The range is the round-1 fixes, `fc1ce476..7b0d5c2f`. Read-only throughout.

**Method.** I read `docs/reviews/FANOUT.md` and `docs/reviews/README.md` first. Then I read `docs/reviews-2026-09-24/TDA.md` and the parts of `TDD.md` that its dispositions share: TDD-A1, B1, C1, C3 and C4. I read `git show` for every commit in the range that touches `automation/`, `automationui/`, the bundles or their tests: `ce9dd7bd`, `e501b56d`, `c2251ace`, `1cd99bbc`, `7ecc2c65`, `22cf6fa7` and `10d7f523`, plus the `AutonomySession` part of `b62f1976` and the `behaviour.md`, `issues.md` and `tests.md` parts of `28469a46`. After that I read the code as it stands at HEAD:
- `Layout.isPathClear` (the tail block), `walkStandingTrains` and its new filter, `tailsOfEachStandingTrain`, `anotherTailOn`, `tailsOn` and `tailLiesOn`
- both forms of `whyItWouldMeetItsOwnTail`, `bodyOfATrainAt`, `whyNoRouteFitsTo` and `whyABerthCannotHoldIt`
- in `HomeStaging`: the tail fields and constructor, `firstClearRoute`, both `passesTheTails...` methods, `liesAcrossAtStart` and `blockedSensors`
- in `AutonomySession`: `stationsWithAHalfMeasuredApproach`, `endsTheBerthsRoom`, `runInsShorterThanTheBerth`, `assignStretchLength` and the piece model
- the run-in finding in `AutonomyChecks`, `GraphReducer.boundsTheRoom`, `TileGraph.isPermanentTurnout`, and the eight bundles

I read these tests in full: `testATailIsNotHiddenByAnother`, `testTheOwnTailArithmetic`, `testATrainDoesNotRunIntoItsOwnTail`, the TDA-C7 claim and its fixtures in `testMassAssignLengths`, the pairing guard in `testHomeStaging`, and the platform-wording pin in `testAutonomyDiagramSession`. To check "red first" and the named mutations, I read (without changing anything) the round's run and mutation records in the session scratchpad: `muta1.json/.out`, `muta1b.*`, `mutot.*`, `muths.*`, `mutc7.*`, `a1*.log`, `c7*.log`, `ota.log`, `otg.log` and `hs.log`. On the frozen railway (`test/layouts/live-snapshot/`) I read `setup.json`: its excluded pages, stations and tile lengths. I also read the tile grid of `1 - Main.cs2` around its one crossing, parsing it in memory with no file written. Arithmetic was checked by hand. **Nothing was executed**: no JVM, no test, no `one.sh`, `battery.sh` or `mutate.py`, and no git command that changes state. I did not open `cs2_sample_layout/`. Every finding that rests on behaviour I could not see by reading has a verification request.

Grades: A is wrong behaviour on the layout, or data lost. B is incorrect results in specific configurations. C is cosmetic, a narrow case, text, or a guard that proves less than it says. D is checked and clean.

---

### TDA2-C1 - The own-tail refusal's "measure them" note counts sensor-to-sensor legs, not the pieces lengths are given in, and skips the leg the return is on - so the missing way past from TDA-B1 can come back

| | |
|---|---|
| **Disposition** | Fixed - OB-297, done on Adam's word (*"Locations of switches are known."*): the build marks the places that cut a stretch into pieces and the note counts pieces.  Claims 7dc22256, fix f17f5a5c. |

**Where.** `Layout.java:10484-10488`, and the count at `10512-10516` (`1cd99bbc`):

```java
for (Integer span : spans) edgeMeasured += span == null ? 0 : Math.max(0, span);
edgeUnmeasured.add(edgeMeasured <= 0);
...
for (int k = edgeWhenLeft.get(place) + 1; k < i; k++)
{
    if (edgeUnmeasured.get(k)) tightestUnmeasured++;
}
```

TDA-B1's remedy had two halves. First, a way round with nothing measured on it is not judged, and that half is right (TDA2-D2). Second, where the way round is only partly measured, the refusal must say how many stretches have no length and that measuring them is the way past. The second half counts at the wrong grain, and it leaves out part of the way round:

1. **The grain is the leg (one `Edge`, from sensor to sensor).** The commit and the javadoc say this is "the grain lengths are given at". It is not. Lengths are given per **piece**, meaning a leg cut at its switches (`AutonomySession.stretchesALengthRuleReads`, MAL-B1: *"he ruled: at switches"*). Switches are asked for separately, with one turnout length per page. So a leg whose pieces all have no length, but which crosses a measured switch or ends at a measured station square, counts as measured and is left out of the note. The half-measured javadoc (`AutonomySession.java:9602-9604`) calls exactly that state ordinary: *"switches measured, some pieces still at 0" is what the tool leaves behind between sittings*.
2. **The leg the return happens on is never counted**, and neither is the leg where a route place was left. The loop covers only `k < i`, so an unmeasured run on the returning leg, before the train reaches its body, adds nothing.

**Input that goes wrong.** Using `testTheOwnTailArithmetic`'s body (X 0, A 1, B 3), take a route `[Q:1]`, `[R:0, B:0]` and a 20-unit train. It is refused naming 4 (1 on the route + 3 of body), and **no note** is added, although R has no length. On a railway between Mass Assign sittings, every leg of the loop that crosses a switch counts as measured. The note then undercounts, or is missing entirely when every leg crosses one. The operator gets "a train of N units or shorter ... or send this one another way", where N is switch lengths plus body. That is TDA-B1's complaint, in a narrower configuration.

**What mitigates it.**
- The refusal leans the way the rules intend: under-measured track refuses (MT-364).
- The Unmeasured Track display shows the pieces.
- Adam's railway is measured throughout. On it the figure is 9 and every leg on the way round is measured.

**Verification request.**
- Fixture 1: in `testTheOwnTailArithmetic`, `ask(Arrays.asList(edge([Q],[1]), edge([R,B],[0,0])), 20)`.
  - Proves it: refused and the result does not contain `errorOwnTailPartlyUnmeasured`.
  - Refutes it: the note is there with 1.
- Fixture 2: on the live-snapshot sandbox, clear every tile length, then `assignSwitchLength(switchesALengthRuleReads(), 1)` and rebuild. Stand the 20-unit train as arrived and call `debugPath` to LowerFront.
  - Proves it: an own-tail refusal whose note is missing, or counts fewer legs than `stretchesNeedingALength()` has pieces on that way round.
  - Refutes it: the counts agree.

---

### TDA2-C2 - The first A1b mutation survived and the record names a different one: nothing checks that Return Home's per-train tail record holds only that train's claims

| | |
|---|---|
| **Disposition** | Fixed - pin 7c7a0fb0: a third, unmoved train on a rail of its own is not blamed for the second train's tail once that train has moved.  Both forms of A1b are red now - R2e (every train's record holds every tail, the survivor) and R2f (one owner per place). |

TDD-A1 / TDA-C6's disposition reads "Mutations A1a, A1b red". The scratchpad record shows two different mutations under the name A1b:
- `muta1.json` "A1b planner one map for all" replaced `walkStandingTrains(covered, places, other -> other.equals(train))` in `tailsOfEachStandingTrain` with `walkStandingTrains(covered, places, null)`. `muta1.out`: **`Failures: 0` - survived.**
- `muta1b.json` "A1b planner reads the one shared map" filters the old single map down to the train's own entries. `muta1b.out`: red.

The survivor was neither strengthened nor recorded as equivalent, which FANOUT asks for; a different mutation was run and recorded instead. The class javadoc's line, *"take Return Home's record of the starting tails as one map for every train, and the second does"*, reads most naturally as the survivor.

**What the survivor does.** Every train's record would carry every train's covered edges. `liesAcrossAtStart`'s direct test (`covered.containsKey(edge)`) would then say yes for every train on any covered edge. An edge that a *moved* train's tail lay on would stay shut while *any* other train with a tail is unmoved. `blockedSensors` would charge every covered edge's sensors to every train. The planner would refuse too much and answer NO_PLAN_FOUND where the railway would have driven the plan. That is the direction HomeStaging's own javadoc says it must never take.

**Why the claim cannot see it.** Its only other train is the one lying across the switch, and its control removes that train. A third train, unmoved and standing elsewhere, is never there to be wrongly blamed.

**What mitigates it.** The code at HEAD is right (TDA2-D1). This is a missing guard, not a defect.

**Verification request.** Run the `muta1.json` A1b spec against the Return Home classes (`testHomeStaging`, `testReturnHomeKeepsClearOfTheTailsItLeaves`, `testReturnHomeFindsAPlanOnAFullRailway`, `testTrainsComeHomeToTheirPlatforms`).
- Proves it: all green.
- If it is still green, add a third train standing clear of the switch to `testReturnHomeAsksAboutEveryTailOnThePlace`'s control. The control must then pass with the second train moved.

---

### TDA2-C3 - The half of the own-tail rule that times the route's own places (a reversing loop, a figure of eight) is checked by no claim, and `1cd99bbc` edited it

| | |
|---|---|
| **Disposition** | Fixed - pin testARouteThatComesBackOverItselfIsJudged in f652c14f, the loop asked for: [Q:2, SW, L1:3], [L2:3, SW, Q:2] with no body - 7 refused naming 6, 6 clear.  Mutation R2b (the route's own places not timed) red. |

`whyItWouldMeetItsOwnTail` judges two kinds of return:
- to a place the body lay on at the start, which is seeded from `reach`;
- to a place the route itself ran over earlier, registered at `Layout.java:10522` (`freeOnceTheTailPasses.put(place, travelled)`) and gated at `10500` by `travelled - routeRunWhenLeft.get(place) > 0`.

The second kind covers a train driven into a reversing loop and out through the same switch, or over its own path at a crossing. TDA-D1 checked it by hand ("the repeated switch place gives wayRound = the balloon's length"), and `1cd99bbc` rewrote the gate it goes through. But no claim returns to a route place:
- Every route in `testTheOwnTailArithmetic` comes back to BODY places (B, A).
- Every frozen-railway route in `testATrainDoesNotRunIntoItsOwnTail` comes back onto the body.
- The turn-on-the-way claim clears the map before any revisit.

**Mutation that would survive:** delete the `freeOnceTheTailPasses.put(place, travelled)` at `:10522`. A body place is still judged against its seed, and a route place is then never judged. Reading every claim, none of them changes.

**Consequence if it regresses.** A train longer than a measured reversing loop is cleared round it, and its head comes back through the loop's switch while its tail is still on it. That is a collision.

**What mitigates it.** The code is right today by reading. Only the guard is missing.

**Verification request.**
- Run that deletion against `testTheOwnTailArithmetic` and `testATrainDoesNotRunIntoItsOwnTail`. Proves it: both green.
- The claim to add, in the arithmetic class: a route `[Q:2, SW:0, L1:3]`, `[L2:3, SW:0, Q:2]` with an empty body. A train of 7 should be refused naming 6 (the loop L1+L2 back to SW). A train of 6 should be clear.

---

### TDA2-C4 - TDA-C4's disposition covers the body walk; the per-candidate cost it also named, and its timing request, are unanswered, and `1cd99bbc` added to that cost

| | |
|---|---|
| **Disposition** | Measured, not a cost.  The four Return Home classes on the frozen railway, at HEAD and with the own-tail check and its body walk taken out of the planner: testReturnHomeFindsAPlanOnAFullRailway 14.2 s and 14.3 s, testTrainsComeHomeToTheirPlatforms 42.7 s and 41.2 s, testTrainsComeHomeFromAPinnedArrangement 38.1 s and 38.1 s, testReturnHomeKeepsClearOfTheTailsItLeaves 0.1 s both - every class green both ways, and the differences within run-to-run noise.  States examined per share were not logged; the plans are found in the same time. |

TDA-C4 raised two costs inside Return Home's time-limited search:
1. the body walked before the cheap refusals, once per destination;
2. *"every BFS candidate now runs `whyItWouldMeetItsOwnTail` over the whole route prefix, and copies the `LinkedList` into an `ArrayList` first"*.

It asked for the searches to be timed at `f21fb8e4^` and at HEAD. `7ecc2c65` fixed (1): the body is now walked after the cheap refusals and memoised by square, train and road, and the memo key is sound (TDA2-D3). Nothing addressed (2). `hs.log` records pass counts, not times.

`1cd99bbc` then made each call heavier:
- It seeds three maps with the whole body, where there used to be one.
- It keeps a per-leg list.
- It no longer returns at the first refusing return. A refused candidate now scans to the end of its route.

This runs for every candidate `firstClearRoute` expands. OB-230 gives the weighted search the last third of a 15-second budget.

**What mitigates it.** The expensive part was the body walk (`walkOneTail` scanning every edge), and that is memoised. What is left is a constant factor on a short loop. Every Return Home class is green within its budget.

**Verification request.** Log `examined` and the wall time of each `astar` share in `core.testReturnHomeFindsAPlanOnAFullRailway` and `core.testReturnHomeKeepsClearOfTheTailsItLeaves`, at `f21fb8e4^` and at HEAD.
- Proves it: fewer states examined per share at HEAD.
- Refutes it: no measurable change. Then record that TDA-C4's second half was measured and was not a cost.

---

### TDA2-C5 - Code and comments the two fixes made false: "the question stops at the first return", three mover guards that can no longer fire, and `Layout.tailsOn`, now uncalled, whose javadoc still says the planner uses it

| | |
|---|---|
| **Disposition** | Fixed - 3a8813ca: the three own-train checks in isPathClear removed, the OB-285 comment saying the walk leaves the routed train out; Layout.tailsOn deleted; the planner's prefix-closed comment gives the reason that holds since TDA-C1 (an extension keeps every return, so its tightest can only be smaller). |

- **`HomeStaging.java:1336-1339`** says *"Prefix-closed ... the question stops at the first return, so no extension of a route that meets the tail can clear it"*. That has not been true since `1cd99bbc` (TDA-C1): every return is asked. The conclusion still holds, because the smallest way round over a longer route can only be smaller, so pruning is still sound. The stated reason is the old one.
- **`Layout.java:2702`, `2724` and `2793`**: `lyingAcross.equals(loc)` / `onShared.equals(loc)`. Since `e501b56d`, `isPathClear` walks every train except `loc`, so no map entry can be `loc`. The OB-285 comment above `2702` (*"ITS OWN TAIL IS NOT THE ANSWER ... Taken as the answer, it ended the question"*) describes a lookup that now always misses. A reader will take it that the mover's claims are still in the map.
- **`Layout.tailsOn` (`:7937-7960`)** has no caller in `src/` or `test/` since `e501b56d`. Its javadoc still says *"The planner asks whether each has MOVED"* through it.

**What mitigates it.** Nothing behaves wrongly. The risk is the next change being written against the old reasoning.

---

### TDA2-C6 - For a parking berth whose approach runs through a crossing, the half-measured count now stops at the crossing but the run-in notice still counts past it, so the two notices disagree about the same berth

| | |
|---|---|
| **Disposition** | Fixed - claims ece635b0 (red first), fix 84a89426: both berth notices stop where the berth rule does - the half-measured count counts only the holes before the stop, and a parking berth's run-in room ends at a crossing nearer than its switch; the run-in sentence says switch, crossing or reversal in eight languages.  Mutations R2g, R2j red. |

`10d7f523` (TDA-C7) made `stationsWithAHalfMeasuredApproach` stop at a crossing, because the berth rule refuses there. The notice for a berth that is **fully** measured but short is `runInsShorterThanTheBerth` → `RUN_IN_SHORTER_THAN_THE_BERTH`: *"only {3} of track is measured between it and the switch ... a train longer than {3} is refused"*. That notice still reads `ReducedEdge.getRoomAtTheEnd()`, and `GraphReducer.boundsTheRoom` stops that at a switch or a permanent turnout, not at a crossing (`AutonomySession.java:~9769`). The fix reached one of the two notices that describe where a berth's room ends.

**Input.** `testMassAssignLengths.openBerthBehindACrossing()`: berth 7,1 = 1, 6,1 = 1, crossing 5,1 = 1, 4,1 = 3, switch 3,1 = 1, 2,1 = 1, with a maximum of 3.
- The berth rule spends 1 + 1 and claims the crossing with 1 left, which is the other road, so **a 3-unit train is refused**.
- Half-measured: nothing is unmeasured, so it is silent.
- Run-in: room to the switch is 6, which is not below 3, so it is silent.

So the berth refuses its own maximum and no notice says so. Now leave 2,1 and the switch unmeasured instead. The half-measured notice fires, but the squares it tells the operator to measure are **beyond the crossing**, and measuring them changes nothing. This is because the `unmeasured` count still runs over the whole leg while `held` stops early. That was true before at a switch; the fix moved the stop, and the count came with it.

**What mitigates it.**
- The refusal has its own sentence, naming the road.
- On the frozen railway it is not reachable. The permanent turnouts are all on 5 - Test, which is excluded (`excludedPages` has page id 4). The one crossing on an included page, 1 - Main 18,10, sits beyond the switch at 19,10 from every station. So MT-564's "no station warned" still holds there.

**Verification request.** Build that fixture with every square measured and a maximum of 3.
- Proves it: `runInsShorterThanTheBerth()` and `stationsWithAHalfMeasuredApproach()` both have no entry for 7,1, while `Layout.whyABerthCannotHoldIt` refuses a 3-unit train on the built route.
- The likely fix is that the run-in count for a berth (not a platform) also stops at a crossing, since the berth rule, not the room rule, is what refuses there.

---

### TDA2-C7 - The half-measured notice after `10d7f523`: English now says "berth rule" and the seven translations still say "room rule"; it says "the switch" where a crossing or turnout is meant; its javadoc heading still says half measured "refuses everything"

| | |
|---|---|
| **Disposition** | Fixed - 84a89426: the notice names the length rules, not one rule, and says switch or crossing, in all eight languages; the javadoc heading and the OB-288 block corrected; both notes after a refusal follow a full stop. |

- **The rule is named differently by language.** `autosetup.ui.checkHalfMeasuredApproach` in English now says *"the berth rule counts an unmeasured square as nothing"*. All seven translations were edited in the same commit, and each still names the room rule: da *rumreglen*, de *die Platzregel*, es *la regla de espacio*, fr *la règle de place*, it *la regola dello spazio*, nl *de ruimteregel*, pl *reguła miejsca*. The two stops disagree too. At a crossing the refusal is the berth rule's. At the claim's permanent turnout it is the room rule's (`boundsTheRoom`), because the berth rule skips a partner that ends at the berth, as the turnout's merging leg does there. So neither word is right for every case the count now covers.
- **"is not spent before the switch behind it, and is refused there".** The count now stops at a permanent turnout or a crossing, and a crossing is not a switch to anyone reading the notice.
- **`AutonomySession.java:9594`.** The heading **"Why half measured refuses everything."** sits above the body `10d7f523` corrected, which now says a short train is not refused. The OB-288 block at `:9679` also still ends "the switch ends the count".
- **Own-tail note.** The note appended to the own-tail refusal follows a sentence that has no full stop, so it reads *"... or send this one another way 2 stretch(es) of track ..."*. This copies the berth rule's RTX-C2 precedent in all eight languages.

**What mitigates it.** These are notices and the text next to them. The refusal itself is right.

---

### TDA2-C8 - behaviour.md 5c's own-tail bullet uses "way round" for two different quantities, and says both hand doors show the sentence when they normally don't offer the destination at all

| | |
|---|---|
| **Disposition** | Fixed - 244331ce: 5c gives the figure for a body place and for a route place separately, says the route's own measured track is what makes a return judged, and says a door does not offer a destination refused on every route. |

`behaviour.md:1149-1160` (`28469a46`):
- **Two meanings of "way round".** It says *"A train longer than the measured track run between leaving a place and coming back to it is refused"*. For a place on the body, that figure includes the body in front of the place (`travelled + reach`). Two sentences later it says *"a way round with nothing measured on it is not judged"*. The code's test ignores the body there: a loop with no length is passed even when the body in front of the square it returns to is measured. The javadoc spells out that the way round is *"the route the head drives ... not the body in front of it"*. 5c does not, and it is that reading which decides TDA-B1's own case.
- **"both hand doors (with the sentence)".** The doors build their lists from `getPossiblePaths`. A train the rule refuses on every route is not offered the destination. The sentence appears only on a menu built before the length changed (TDD-C1). The MT-571 comment in the same commit says so. 5c, which Adam reads as the intended behaviour, says the opposite.

**What mitigates it.** It is text. The code and the MT-571 comment are right.

---

### TDA2-D1 - TDA-C6 / TDD-A1: the disposition holds

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **The claim tests the finding's own failure, and does not depend on order.** In `testATailIsNotHiddenByAnother`, both trains claim `A1:S` (the precondition asserts each alone does). Whichever train the single map recorded on S, one of the two `isPathClear` asserts, and one of the two `passesTheTailsOfTrainsThatHaveNotMoved` asserts, had the mover as the recorded owner. So each method was red before the fix. `a1r.log` shows 2 of 2 failing. The controls remove the other train.
- **A1a** (`walkStandingTrains(..., null)` in `isPathClear`) turns the runtime claim red (`muta1.out`). The second A1b variant turns the planner claim red. The first variant is TDA2-C2.
- **Walking one train at a time gives the same claims as walking them together.** `walkOneTail` reads nothing from the maps it fills; it spends from a local `spent` set. Locomotive equality is identity (`MarklinLocomotive.equals`), so the filters `!other.equals(loc)` and `other.equals(train)` pick exactly one object.
- **The runtime and the planner still agree.** The runtime refuses on any other train's claim, and a single map is enough for that once the mover is out of it. The planner asks each unmoved train against its own record, and `blockedSensors` now frees a sensor only when every train whose tail reached it has moved.
- **No other routing reader of the single-owner maps is left.** Their other callers are `AutonomySession`'s drawing. The grey reads only the key set, so ownership does not matter. The orange line is drawing only: it can credit a shared square to the other train, and that square is still drawn in the same orange.

---

### TDA2-D2 - TDA-B1 and TDA-C1: the rule change is what the dispositions say, and nothing else moved

| | |
|---|---|
| **Disposition** | Checked - clean. |

Hand-checked against `Layout.java:10414-10545`:
- **For a place the route itself ran over, the new gate is the old test.** `free` equals the distance run when the place was left, so `travelled - routeRunWhenLeft > 0` is exactly the old `wayRound > 0`. Only a place on the body changed: it is now judged only once the route has measured something (`travelled > 0`). So TDA-B1's own case (nothing measured on the loop, the body measured) is now cleared.
- **TDA-C1 changes the figure, not which trains are refused.** Refusing when `length > min(wayRound)` refuses exactly the trains the old first-refusing-return test refused. This also means MT-571's "no train of 9 or less is refused anywhere" can only have become truer.
- **Pruning is still sound.** Extending a route can only add returns, and the gate and figures of the earlier part are unchanged, so the minimum can only fall. Return Home's pruning stays sound (its comment's reason is stale: TDA2-C5).
- **No null risk.** `routeRunWhenLeft` and `edgeWhenLeft` are always put and cleared together with `freeOnceTheTailPasses`, so `get(place)` is never null where `free` is not.
- **The named mutations are caught by the claims named.** OT1 (the old gate) is red on `testAWayRoundWithNothingMeasuredIsNotJudged`. OT2 (first return named) is red on `testTheRefusalNamesTheTightestReturn`, whose 20-unit refusal must name 6 (the first return names 8). OT3 is red on `testAPartlyMeasuredWayRoundSaysSo`. OT4 is red on the 9 pin. The source is `mutot.out`. `ota.log` shows the class 3 of 3 red at `c2251ace`.
- **One record note.** The third claim was rewritten from squares to legs in the fix commit itself. Its red at `c2251ace` was the missing key, which is true under either grain, and OT3 is what proves the version now in the file.

---

### TDA2-D3 - TDA-C2, TDA-C3 and TDA-C4's memo: the dispositions hold

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **TDA-C2.** At `gap + 1` (10, since the gap is pinned at 9), every route to LowerFront must be refused with the own-tail sentence. That is what MT-571 step 3 needs, and the MT-571 comment says what the doors actually show.
- **TDA-C3.** `testReturnHomeJudgesAMovedTrainByTheRoadItCameAlong` empties BottomSecondary on the railway and gives the plan the RampDown road through `movedAlong`. H1 (`cameAlong = null`) turns it red (`muths.out`). That red also establishes that the 20-unit refusal is the own-tail rule's and not the room rule's: under H1 the other rules still run, and the plan was found.
- **TDA-C4's memo.** The key is `(from, loc, cameAlong)`. `Point` and `Edge` compare by name, which is fixed for a planning run, and `Locomotive` by identity. A train's length, which the walk also reads, does not change during a plan. HomeStaging is single-threaded, so the plain `HashMap` is safe. The remaining cost is TDA2-C4.
- **Outside this lane, for the documentation validator.** TDD-C4's disposition says the census sentence in MT-571 is *"qualified by the comment"*. The comment added in `28469a46` qualifies only the BottomSecondary figure ("on your railway the figure is still 9"), not "no train of 9 or less is refused by this anywhere".

---

### TDA2-D4 - TDA-C7: the fix and its mutations hold; the "red first" record is for a turnout fixture that was replaced

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **`endsTheBerthsRoom` is right.** It is `isSwitch() || isPermanentTurnout || CROSSING`, and it is asked after `takesNoLength`, which is true only for transparent tiles, so a crossing is not skipped. A crossing is one place id shared with its other road, so `whyABerthCannotHoldIt` refuses on it through a symmetric lock partner. At a permanent turnout, the room rule's `boundsTheRoom` is what refuses.
- **C7a and C7b are each red on the half they undo** (`mutc7.out`).
- **The record overstates the turnout half.** The turnout half of `22cf6fa7`'s claim put the berth at 7,1, where no train arrives through the turnout. It was red for a reason the fix could not change: `c7d.log` is still red after the fix. `10d7f523` rewrote it (berth at 1,1). So "red first" is true of a fixture that was then replaced. The replacement is proved by C7a, not by a red.
- **The javadoc and the sentence were corrected as the disposition says.** The text left behind is TDA2-C7.
- **Not reachable on the frozen railway.** See TDA2-C6's mitigation, so MT-564 stays true there.

---

### TDA2-D5 - TDA-C5, TDA-C8, TDA-C9 and TDA-C10: the dispositions are true and the decisions are well posed

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **TDA-C5.** behaviour.md 5c now states the rule, that only measured track binds, the reset after a turn, and the tightest figure. The no-side case is covered by "the body as it stands is the one the walk above claims", read with the MT-477 bullet. Its two textual problems are TDA2-C8. (The user guide `Automation.md` does not mention the refusal; that belongs to TDD-C2's lane.)
- **TDA-C8** (Adam's decision) is well posed. `AutonomyBuilder.arrivalAllowed` bars only *stopping*, and the javadoc at `:326-333` says trains still run over a barred copy. `:630` emits a turning copy for every arrival side of a may-turn square, and `:1173` writes it as `reversing` where it does not stop. So the question of whether "only accept arrivals from one side" includes turning is real.
- **TDA-C9** is filed as OB-295 in the `issues.md` Inbox with the finding's content.
- **TDA-C10** (Adam's decision). The pin exists: `testAutonomyDiagramSession:3837` asserts the platform sentence does not say "refused". The TopR1ParkShort → TopMainR1Inter case is in the MT-564 comment (`tests.md:27918`), as cited.
