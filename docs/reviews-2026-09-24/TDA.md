# TDA - Automation lane, round 1: the railway's rules in the commits of 2026-09-23 and 2026-09-24

**Status:** closed

**Prefix:** TDA

**Reviewed:** branch `autonomy-diagram-r0` at `fc1ce476` (2026-09-24 15:41). The range is `12d74ad5..fc1ce476`. This lane covers the 56 commits that touch `src/org/traincontrol/automation/` and `src/org/traincontrol/automationui/`, plus the tests that claim them.

**Method.** I read `docs/reviews/FANOUT.md` and `docs/reviews/README.md` first. Then I listed the lane with `git log --since="2026-09-23 00:00" -- src/org/traincontrol/automation src/org/traincontrol/automationui`. The five-lane review of 2026-09-23 already ran four validator rounds over `12d74ad5..6cb11933` and closed at `7a961962`, so I spent most of the time on the 22 lane commits after that close: `5f09c006` through `fab81423`. The earlier commits I read only where the new work depends on them: `walkOneTail`, the standing-train checks in `isPathClear`, and the lock reach of `12d74ad5`.

OB-294 got the most attention: `121b1c3c`, `f21fb8e4`, and the claims in `c3b8f1e7`, `00a2fc6d` and `c95e285b`. I read `whyItWouldMeetItsOwnTail` (both forms), `bodyOfATrainAt`, the `reach` plumbing through `walkOneTail`, `claimUpToWhereTheRailsPart` and the AUT-C3 own-square branch, the call in `isPathClear`, the one in `whyNoRouteFitsTo`, both hand doors, and `HomeStaging.firstClearRoute`. I checked the rule by hand-simulating its arithmetic in these cases:
- a head-on return and a same-direction chase
- a crossing, a double slip, an overpass and a double curve
- a turn where the train stands, and a turn on the way
- a loop inside one edge
- unmeasured squares, a train with no known side, and a train with a side but no road

I also read the frozen railway's `test/layouts/live-snapshot/config/autonomy/setup.json` for tile lengths, barred arrivals and turn flags. Other sources: `behaviour.md`, `issues.md`, and MT-552/MT-571/MT-572 in `tests.md`. **Nothing was executed.** Every finding that depends on behaviour says so and has a verification request. I did not open `cs2_sample_layout/`.

Grades: A: wrong behaviour on the layout, or data lost. B: incorrect results in specific configurations. C: cosmetic, a narrow case, or text. D: checked and clean. Each finding says what mitigates it.

---

### TDA-B1 - OB-294 refuses a loop that has no measured square on it, judging it by the train's own measured body, and gives no way past

| | |
|---|---|
| **Disposition** | Fixed - claim c2251ace (red first), fix 1cd99bbc: a return is judged only where the route has measured something since the head left the place, and a way round with unmeasured stretches says how many.  Mutation OT1 red. |

**Where.** `Layout.java:10384-10388` (seeding) and `10416-10424` (the test), commit `121b1c3c`:

```java
for (Map.Entry<String, Integer> lying : reach.entrySet())
{
    freeOnceTheTailPasses.put(lying.getKey(), -lying.getValue());
}
...
int wayRound = travelled - free;

if (wayRound > 0 && length > wayRound)
```

For a place `P` the body lies on, `free = -reach(P)`. That makes `wayRound = travelled + reach(P)`. `walkOneTail` claims a place only while the train has length left (`reachedAt(reach, place, L - left)` with `left > 0`). So **every claimed place has `reach(P) < L`**.

Suppose the route returns to such a place with nothing measured on the way round (`travelled == 0`). Then `wayRound = reach(P)`, which is above 0 for any body place past the standing square. Since `L > reach(P)` is always true, the train is **always refused, whatever its length**. The only place that escapes is one with `reach == 0` (the standing square, or unmeasured squares in front of the first measured one).

**What the documents say.**
- The javadoc (`Layout.java:10333`): *"a return with nothing measured on the way round is not judged"*. It adds that unmeasured squares within a measured way round refuse rather than permit. The code treats the body's own measured squares as part of "the way round", so a loop with no length on it at all is judged.
- The MT-552 deep-dive comment in `tests.md` (line 27897) told Adam: *"today's own-tail rule (OB-294): where nothing is measured, nothing is judged."* That is not true for this case.

**Input that goes wrong.** A railway measured at its stations only, which Mass Assign Lengths leaves between sittings and a user who types berth lengths first also has. Take a station square measured 2, an unmeasured approach behind it, and a loop back onto that approach, as at BottomSecondary.
- A 3-unit train that came in along the approach has its body claimed at `reach 2` over the approach squares.
- Every route round the loop back onto the approach has `travelled` = the measured squares passed on the loop = 0.
- It is refused with *"would run into its own tail on ...: the route comes back to track the train is still lying on after only 2 units"*.

The loop may be 30 units long. The same happens to a loop that is only partly measured: it under-counts, and that under-count is the ruled refusing direction (MT-364).

**What the railway does.** A route the train can drive is refused at every tier: autonomy, both hand doors and Return Home. The sentence gives a figure that is the train's own berth, not the loop. Its remedy ("a train of 2 units or shorter ... or send this one another way") never mentions measuring the loop. The berth rule learned exactly this in RTX-C2 (`errorBerthApproachPartlyUnmeasured` is appended there) and has the half-measured notice beside it. This rule has neither. Adam's standing preference is no check rather than one with no way past.

**Mitigation.** Adam's railway is not affected today. MT-571 says no route there comes back to a train's own track in under 9 measured units. If Adam rules that the body counts as measured way round, this drops to C: the refusal still needs the unmeasured count and "measure it" in its remedy.

**Verification request.** Use `core.testATrainDoesNotRunIntoItsOwnTail`'s fixture, then:
1. Clear every tile length (as the length guards do).
2. Set `1 - Main:13,11` (BottomSecondary) to 2 and `1 - Main:14,11` to 1.
3. Stand a 6-unit train with `standItAsArrived(6)` and ask `debugPath` to LowerFront.
- **Proves it:** every route refused with the own-tail sentence naming 2 or 3.
- **Refutes it:** routes clear, or refused for another reason.
- Control: the same with no length on 13,11 and 14,11 (reach all 0) should be clear.

---

### TDA-C1 - The own-tail refusal names the first return's figure, not the tightest, so its remedy can be false

| | |
|---|---|
| **Disposition** | Fixed - claim c2251ace (red first), fix 1cd99bbc: every return is asked and the tightest named.  Mutation OT2 red. |

**Where.** `Layout.java:10420-10424`: the loop returns at the first place where `length > wayRound`. For body places met in route order, `wayRound(next) = wayRound(this) + len(this) - len(next)`. So in a **same-direction return**, where the head comes up behind the tail and runs along the body towards where it stood, the figure falls wherever a longer place follows a shorter one. That includes a 0-length square in a piece whose length sits on the next square (`assignStretchLength` shares a piece unevenly).

**Input.** Hand arithmetic, with body places behind the head `A` (reach 1, length 2) then `B` (reach 3, length 0), and a route that reaches `B` after 5 measured units, then `A`:
- L = 20 is refused at `B` naming 8: "a train of 8 units or shorter is clear of it in time".
- L = 8 passes `B` (8 > 8 is false), then is refused at `A` naming 6.

The true limit is 6.

**Consequence.** The operator follows the remedy and is refused again with a smaller number. The refusal itself is safe.

**Test.** `testTheRefusalNamesTheLongestTrainThatGoes` pins the remedy on one frozen route only. That route is head-on, where the figure rises along the route (`+2 len`), so the first return is the minimum. The MT-571 note *"no route between stations comes back ... in fewer than 9 units, so no train of 9 or less is refused by this anywhere"* rests on the same first-return figure.

**Fix direction.** Take the minimum `wayRound` over the whole route before refusing, and name that.

**Verification request.** Call the package-private static `Layout.whyItWouldMeetItsOwnTail(List, Locomotive, Map)` by reflection with hand-built edges (`Edge.setPlaces`): places `[Q, B, A]` with lengths `[5, 0, 2]`, reach `{X:0, A:1, B:3}`, and trains of 20, then 8.
- **Proves it:** 8 refused naming 6.
- **Refutes it:** 8 clear.

---

### TDA-C2 - MT-571's step 3 (length 10 refused) depends on every route to LowerFront, and the class measures only the shortest

| | |
|---|---|
| **Disposition** | Fixed - pinned in c2251ace: at one unit past the figure every route to LowerFront is refused (green; true today).  MT-571 given a comment on what the doors show. |

`testTheRefusalNamesTheLongestTrainThatGoes` picks the route with the **smallest** named gap and proves 9 goes and 10 does not on that route. MT-571 tells Adam that a 10-unit EN57-203 is "refused the same way" and a 9-unit one goes.

The right-click menu offers a destination if **any** route to it is clear. If one of the other routes BottomSecondary → LowerFront (another copy of LowerFront, or another way round by the tunnel) has a gap of 10 or more, step 3 is sent and Adam marks the test failed. That costs him a round trip, and the railway is behaving correctly.

**Verification request.** In `testALongTrainIsNotSentRoundIntoItsOwnTail`, print `gapNamedBy` for every route at 20.
- **Proves it:** a maximum of 10 or more (MT-571 step 3 needs rewording).
- **Refutes it:** every route names 9 or less.

---

### TDA-C3 - Return Home's own-tail check for a train the plan has already moved is not pinned by any test

| | |
|---|---|
| **Disposition** | Fixed - claim testReturnHomeJudgesAMovedTrainByTheRoadItCameAlong in 7ecc2c65; mutation H1 (the railway's record for a moved train) red. |

**Where.** `HomeStaging.java:1131-1138` (`f21fb8e4`):

```java
List<Edge> cameAlong = this.movedAlong.get(loc);
...
final Map<String, Integer> ownBody = this.layout == null ? null : this.layout.bodyOfATrainAt(from, loc, cameAlong);
```

The commit says the body comes from "the road the plan moved it along, or what the railway records". `testReturnHomePlansNoRouteIntoItsOwnTail` calls `firstClearRoute` on a fresh `snapshot`, where `movedAlong` is empty, so only the second half runs.

**Mutation that would survive:** pass `null` for `cameAlong` always. The planner would then read `from`'s railway record for a square the train is not on. That is null or another train's side, which gives an empty or wrong body. A second move by the same train would be planned round into the tail its first move left: a plan the railway refuses half way (OB-073).

**Verification request.** Run that mutation against the Return Home classes.
- **Proves it:** everything stays green.
- **Refutes it:** some class goes red.

The claim to add: a 20-unit train that the plan first moves to BottomSecondary (arriving down RampDown), then asks for LowerFront.

---

### TDA-C4 - Return Home walks the mover's body before its three cheap refusals, once per destination, inside a time-limited search

| | |
|---|---|
| **Disposition** | Fixed - 7ecc2c65: the body is walked after the three cheap refusals and memoised per train, square and road; the Return Home classes green. |

**Where.** In `HomeStaging.firstClearRoute` (`f21fb8e4`), `ownBody` (a full `walkOneTail`) is computed at line 1138. That is **before** `if (!from.isActive())`, `if (!from.isDestination())` and `if (!canRest(loc, to, state) || state.containsKey(to))`, which refuse most calls in A*. It also depends only on `(from, loc, movedAlong)`, yet A* calls it once per destination. Each walk scans every edge several times per hop: `getNeighborsAndIncoming`, `getIncomingEdges`, and the `edges.values()` loop.

On top of that, every BFS candidate now runs `whyItWouldMeetItsOwnTail` over the whole route prefix, and copies the `LinkedList` into an `ArrayList` first.

**Why it matters.** OB-230 (`8f55b863`) gives the weighted search the last third of a 15 s budget, and records that it *"found a ten-move plan in under five"*. That is inside a 5 s share. A constant-factor slowdown there turns a found plan into NO_PLAN_FOUND on a crowded railway, and nothing reports it.

**Verification request.** Time `core.testReturnHomeFindsAPlanOnAFullRailway` and `core.testReturnHomeKeepsClearOfTheTailsItLeaves` at `f21fb8e4^` and at HEAD. Log `examined` and the wall time of each `astar` share.
- **Proves it:** fewer states examined per share, or the weighted share now over 5 s.
- **Refutes it:** no measurable change.

The fix is cheap: compute `ownBody` after the early returns, or once per `(state, loc)` in the caller.

---

### TDA-C5 - `behaviour.md` does not state the OB-294 rule

| | |
|---|---|
| **Disposition** | Fixed - behaviour.md 5c states the rule (this round's documents commit). |

`grep` of `docs/reference/behaviour.md` finds no mention of a train running into its own tail round a loop. Section 5c says only *"A train never blocks itself — pulling forward off its own tail is how it leaves"*, and the OB-285 note under it.

The code's javadoc cites 5c for the "never blocks itself" half and states the new exception only in the code. The project's rule is that `behaviour.md` holds the intended behaviour, and OB-285 and every length rule of the same days were added there.

Missing from it:
- the rule ("the head may come back to a place only once the tail has left it")
- "only measured track binds"
- the reset after a turn
- that a train with no known side is not judged (TDA-D3)

---

### TDA-C6 - The tail-claim map records one train per square, so the moving train's own claim can hide another train's tail

| | |
|---|---|
| **Disposition** | Fixed - as TDD-A1: claim ce9dd7bd (red first), fix e501b56d. |

**Where.** `walkOneTail` writes `places.put(place, loc)` (last writer wins), and `walkStandingTrains` visits trains in `this.points` order, which is a `HashMap` keyed by Point name (`Layout.java:836`).

OB-285 (`68759c09`) made the mover's own tail "not the answer", so the other trains are asked. All three questions it asks read that single-owner map:
- `coveredTrack.get(sharing)`, then `tailLiesOn(e, onShared, coveredPlaces)`
- `anotherTailOn(e, loc, coveredPlaces)`

AUT2-C2 (`a6d3b476`) made the planner collect "every train" via `tailsOn` over the same kind of map (`placesCoveredAtStart`).

Where the mover's body and another train's tail both claim a square, whichever train was walked last owns it. If that is the mover, the other train is invisible on that square to all three questions.

**Input.** OB-285's own case with a longer turned train. The train turned at Tunnel (southbound) with its tail south over the column-7 points, and a 3-unit train in TunnelRightPark sticking out across the same points. `testATurnedTrainIsNotSentIntoAnotherTail` stands the turned train at length **1**, so its body never reaches the shared square and the overlap never arises.

**Consequence.** The OB-285 refusal depends on the hash order of the two Point names. When it is lost, the turned train is cleared over a square the other train's tail is claimed on. The mitigation is that on consistently measured track, two trains claiming one square usually means they already touch at the fouling point. The overlap is most likely where squares with no length are claimed for nothing by both walks. Graded C for that reason. It would be A if a physically clear overlap turns up.

**Verification request.** In `testATurnedTrainIsNotSentIntoAnotherTail`, raise the turned train's length until `placesCoveredByStandingTrains()` shows it on the column-7 switch square. Then ask `isPathClear` for the way south.
- **Proves it:** the owner there is the turned train and the route is cleared.
- **Refutes it (for this order):** the refusal stands. Then repeat on a hand-built graph with the two Point names chosen to reverse the hash order.

---

### TDA-C7 - The MT-552 half-measured count stops only at a switch; both rules it mirrors also stop at permanent turnouts and crossings. The method's javadoc and message are now stale

| | |
|---|---|
| **Disposition** | Fixed - claim 22cf6fa7 (red first), fix 10d7f523: the count stops at a switch, a permanent turnout or a crossing; its javadoc and sentence corrected in eight languages.  Mutations C7a, C7b red. |

**Where.** `AutonomySession.java:9681` (`615e6969`):

```java
if (getGraph() != null && isSwitchSquare(tile)) break;
```

`isSwitchSquare` is `LayoutDiagramComponent.isSwitch()`: the switch types and scissors. The two rules the count stands for stop earlier:
- **The room walk** stops at `GraphReducer.boundsTheRoom` = `isSwitch() || TileGraph.isPermanentTurnout(...)` (OB-233, *"a permanent turnout is still the last switch"*).
- **The berth rule** (`whyABerthCannotHoldIt`) refuses as soon as a claimed place is on any lock partner. A plain crossing on the approach is one, and so is a permanent turnout.

On a half-measured berth approach through either, the count runs past it and adds track beyond. The berth is then not warned while the rule refuses its longest train.

**Stale text in the same method.** The javadoc paragraph "Why half measured refuses everything" (`AutonomySession.java:9583-9590`) still says *"A one-unit train is refused as surely as a nine-unit one"*. That stopped being true with OB-278 and contradicts the new block comment eighty lines below. The notice `autosetup.ui.checkHalfMeasuredApproach` still says the rule *"claims the whole approach for any train whose length is known"*. Since OB-288 and MT-552, the notice fires only when the longest train reaches the switch.

**Mitigation.** A notice, not the refusal. The refusal keeps its own sentence. MT-552's deep dive measured 28 berth approaches on Adam's railway, none warned.

**Verification request.** In `testMassAssignLengths`, reuse `openBerthBehindALongerRun`'s shape with 4,1 as a `CROSSING` (or a permanent-turnout type) crossed by another sensor's road. Berth 1, 6,1 unmeasured, 5,1 = 1, 4,1 = 0, 3,1 switch, maximum 2, plus a length on the square beyond the crossing.
- **Proves it:** no half-measured warning while `whyABerthCannotHoldIt` refuses a 2-unit train.

---

### TDA-C8 - `e09fe989` stops asking for lengths on a barred approach at a turn-round square, but the build still lets trains arriving that way turn there

| | |
|---|---|
| **Disposition** | Fixed - Adam, 2026-09-24: *"Arrivals THAT STOP THERE should only be allowed from the configured side(s).  Turning shouldn't need to factor this in"* - the reversal notice asks about a barred side again.  Claim 7dc22256, fix f17f5a5c. |

**Where.** `AutonomySession.reversalsWithoutLength`, `AutonomySession.java:3294`:

```java
// A barred side is an approach no train uses ...
if (getBarredArrivals(tile).contains(arriving.getEntrySide())) continue;
```

The build disagrees. `AutonomyBuilder.java:630` emits a turning copy for **every** arrival side of a square trains may turn at (`if (canTurn) out.add(new Node(tile, side, true))`). For a barred side, `stops` is false, so that copy goes out as `"reversing"` (`AutonomyBuilder.java:1173`: *"somewhere trains turn round and nobody is sent"*).

A route may arrive by the barred side and turn there. `whyTooLongForThisRoute` judges exactly that turn: `comesToRest = berth || here.isReversing()`, over the stretch behind the arriving edge. That is the stretch this notice exists to ask about.

**On the frozen railway:** RampDown (`1 - Main:21,6`) has `canReverse: true` and `barredArrivals` `S`. BottomMainPost (`22,6`) has `canReverse: true` and `N`.

**The question for Adam.** He said of those two *"they only accept arrivals from one side"*.
- **If that includes turning:** the build is what is wrong. It should not emit a turning copy for a barred side.
- **If not:** the notice should keep asking about that approach.

The two berth checks in the same commit are right to skip a barred side, because nothing stops there.

**Verification request.** On the frozen railway, list the Points on RampDown's square and check for a `reversing` copy reached from the south. Then run `debugPath` by hand from a station on row 11 (e.g. BottomSecondary) to one reachable by turning at RampDown.
- **Proves it:** a route turning at the south-arrival copy exists.
- **Refutes it:** no edge arrives at that copy.

---

### TDA-C9 - The new "unavailable while occupied" notice describes a standing-train rule that a non-station never applies

| | |
|---|---|
| **Disposition** | Fixed - OB-295, on Adam's answer of 2026-09-24: the standing half of the restriction is asked of every square a route arrives at, at runtime, in Why not Moving? and in Return Home's planner.  Claims 7dc22256, fix f17f5a5c. |

`63c4fdc0` keeps the restriction on a square made pass-through, and lists every restriction as *"{0} is unavailable while {1} is occupied."* (`autosetup.ui.checkUnavailableWhileOccupied`).

On a non-station only the lock-edge half is live: `AutonomyBuilder`, line ~1378, gives "an edge arriving at a station somebody has held back" the edges ending at the watched square. The standing half (`blockedBy`, `Point.heldBackBy`) is asked only of a path **destination** in `isPathClear`, and a non-station is never one. So on a pass-through square:
- a train **standing** on the watched square holds nothing back;
- a **route running into** it does.

The sentence says the first.

Low cost: it is a notice, and the feature name is Adam's. A pass-through variant would read "... cannot be passed while a train is being sent to {1}".

---

### TDA-C10 - The platform run-in notice promises a longer train is admitted, where the railway refuses one longer than the measured route in

| | |
|---|---|
| **Disposition** | Fixed - Adam, 2026-09-24: *"Add the refusing figure where there is one."*  The platform notice's third number, read off the railway the setup builds (f17f5a5c, ac4c7567); on the frozen railway TopMainR1Inter 3 and LowerFront 4, each matching the railway's own refusal.  MT-583. |

`e09fe989` (MT-555) adds `RUN_IN_SHORTER_THAN_THE_PLATFORM`: *"a train longer than {3} stands across that switch while it is here, and may block ..."*.

`whyTooLongForThisRoute`'s FR-087 allowance admits it only *as far as the measured route in holds* (`theApproachItselfHoldsIt`). A train longer than that is still refused, with a figure the notice never shows.

The MT-552 deep-dive comment found one such case on Adam's railway: a 4-unit train from TopR1ParkShort refused at TopMainR1Inter under this notice. It was reported and *"nothing changed"*, but it was not filed, so nobody owns the wording. Grade C: the text is wrong in a narrow case, and the refusal has its own sentence.

---

### TDA-D1 - OB-294's arithmetic holds in the cases hand-checked

| | |
|---|---|
| **Disposition** | Checked - clean. |

Checked against `Layout.java:10351-10440` and `walkOneTail`'s `reach`:

- **Head-on return** (Adam's case: the head re-enters row 11 at the switch and runs towards the tail's end). Refusing when `L > T + reach` is exactly "the tail has passed the place's near end before the head arrives". `wayRound` rises along the route, so the first refusal is also the minimum.
- **Same-direction chase:** correct per place (bound `circumference - len`). The one weakness, its figure, is TDA-C1.
- **The standing square:** `reach 0`. A return to it compares `T` with `L`. The route never lists its own departing square (`placesAlong` includes the far end, not the near one), so `travelled` starts at the head.
- **Crossing and double slip:** one place id per tile (`GraphReducer.locationsOf` suffixes only `OVERPASS`, `DOUBLE_CURVE` and `FEEDBACK_DOUBLE_CURVE`), so both roads meet. An overpass's levels and a double curve's arms do not.
- **Double curves:** the `RouteId` is `(state, index)` with no direction, so a route leaving over its body along a double curve still matches the body's claim, and `leavesOverItsBody` works.
- **Turn where it stands:** with the train turned and the route leaving over its body, the same formula holds with the model point as the trailing end. Seeding is correctly skipped, and the route re-registers each body square as it passes.
- **Turn on the way:** the reset after an edge ending at a `reversing` copy is sound. The builder marks only the turning copy `reversing` (or a must-turn square emitted as one Point), so passing a plain copy does not reset. After the turn the model point re-registers every square it drives back over.
- **A loop inside one edge (balloon):** the repeated switch place gives `wayRound` = the balloon's length.
- **The `previous` skip** never fires on a built graph.
- **Legacy configs with no places:** nothing is judged.
- **The planner's pruning** is prefix-closed: the reset happens only after the prefix's last edge, which is past every check the prefix made. On `seen` dominance, a longer way round to the same square is dropped only if its commands are a superset, which needs two routes that part at no switch. I found no such shape; noted, not filed.

---

### TDA-D2 - OB-294's siblings are all asked, in the same order, and the event thread takes no monitor

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **The five callers:** `isPathClear` (`Layout.java:2862`), `whyNoRouteFitsTo` (`5648`), `AutoLocomotiveStatus` (`1164`), `LayoutRightclickAutonomyMenu` (`1376`), and `HomeStaging.firstClearRoute` (`1322`).
- **Order:** each asks it after `whyTooLongForThisRoute` and `whyABerthCannotHoldIt`, so the sentences come in the same order everywhere.
- **Event thread:** `whyItWouldMeetItsOwnTail` → `bodyOfATrainAt` → `walkOneTail` → `getNeighborsAndIncoming` / `getIncomingEdges` / `edges.values()` are all unsynchronized, as the OB-192 guard requires. `edgesATailWouldCover`, which is `synchronized`, is not on this path.
- **Bundles:** the key is in all eight, with `\uXXXX` escapes and typographic apostrophes, so there is no MessageFormat quote trap.
- **The claims' mutations** described in the test class javadoc would each be caught as described: no starting body, `>=` at the gap, no `leavesOverItsBody`, no reset after a turn, and no planner check.

---

### TDA-D3 - A train with no known side is not judged, which is consistent with every tail rule

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **No side at all:** with `arrivedFrom == null` at a through square, `walkOneTail`'s fork rule sees two neighbours and stops before claiming anything, so `reach` is empty and only returns to the route's own squares are judged. Every other tail rule behaves the same way (nothing claimed, nothing blocked), and FR-100 asks the operator.
- **A side but no road** (after a restart, or "Not known"): at BottomSecondary, MT-477's `claimUpToWhereTheRailsPart` claims the row-11 squares up to the switch where the RampDown and BottomCrossover rails part. The OB-294 return (`BottomMainAPre -> BottomCrossover`) enters over exactly those squares, so the case Adam raised is still caught.
- **A running train** is never asked about by `isPathClear` with a path starting mid-run. The check inside `configureAndLockPath` runs before `reserve`.

---

### TDA-D4 - OB-230's Return Home estimate never overstates the moves left, and its budget shares are as documented

| | |
|---|---|
| **Disposition** | Checked - clean. |

`movesStillNeeded` (`8f55b863`) adds three counts, and none of them can overstate:
- misplaced homed trains;
- homeless trains standing on the home of a train still away (a set, never counted by `misplaced`);
- one per closed ring of trains each standing on the next one's home. Chains that run into an earlier ring are not counted again, and a chain that loops back on itself counts only its ring.

At weight 1 the estimate never overstates, so A* still finds the shortest plan. The tie-break (fewer moves still needed) never overrides the score. The thirds and halves match the comment. The timing risk from OB-294 is TDA-C4.

---

### TDA-D5 - OB-291: a remembered maximum on a demoted station is read nowhere

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **Runtime:** the only reader is `Point.validateTrainLength`, which returns true off a destination, and it is reached from `whyTooLongForThisRoute`.
- **Setup:** `runInsShorterThanTheBerth`, `stationsWithAHalfMeasuredApproach`, `hasNoMaximumTrainLength` and `modelsAnyLength` all ask `store.isStation` first.
- **Clearing:** `tilesWithAMaxTrainLength` counts stations, plus a negative anywhere, which is right since `Layout.fromJSON` refuses a negative on any point.
- **Other mentions:** `HomeStaging.java:2082` uses it only in an explanation.

---

### TDA-D6 - AUT3-C3, MT-477 and OB-285's tail changes are as described

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **AUT3-C3** (`0e870b37`): at the first hop, the side rule and the TDY2-C5 swap each take an arriving rail from a plain copy (`isPlainCopy`), and fall back to the first arriving rail.
- **MT-477** (`06d3c063`): `claimUpToWhereTheRailsPart` drops twin copies over the same places (a set of place lists), claims the shared squares up to and including the switch, and records `reach` as it claims.
- **OB-285:** the mover's own direct cover no longer ends the question. The residual gap is TDA-C6.
- **`12d74ad5`:** the reverse-rail lock check and `railHeldByThisTrain` are as its commit says. That commit was covered by the 2026-09-23 rounds.

---

### TDA-D7 - e09fe989's barred-side test and FR-101's closed station

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **Barred-side test:** `getBarredArrivals(tile)` and `ReducedEdge.getEntrySide()` are the same `TilePorts.Side` type. In `runInsShorterThanTheBerth` and `stationsWithAHalfMeasuredApproach`, skipping a barred side is right, because no train stops there. The turn-round case is TDA-C8.
- **FR-101:** `reachableTiles` never reaches or enters a closed tile, so no station counts a closed one as reached. One remainder, not filed: a closed station is still used as a starting point in the "nothing can reach it" loop. That can hide `STATION_UNREACHABLE` for a station reachable only through it. In `testClosingASquareCutsOffWhatIsBeyondIt`'s last state, `far` reaches nothing and is warned anyway. This is consistent with the start exemption Adam kept ("driven out by hand").

---

### TDA-D8 - fab81423's `trainLengths()` and the Return Home own-tail claim's missing floor

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **`Layout.trainLengths()`** is keyed by name in a `TreeMap`. Locomotive names are unique in the database, so no train is lost.
- **`testReturnHomePlansNoRouteIntoItsOwnTail`** asserts on the 20-unit case only when the planner returns a route. It has no floor, but its 4-unit control rules out a blanket refusal, and its red was seen at `c95e285b`. It proves the claim. The branch it does not reach is TDA-C3.
