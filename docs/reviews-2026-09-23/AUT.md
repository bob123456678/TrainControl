# Autonomy engine since v2.8.1 - locking, tails, room, Return Home, copies

**Status:** open

**Open at close:** AUT-C2 - carried in the finding store.

**Prefix:** `AUT`

**Reviewed:** branch `autonomy-diagram-r0` at `281c79de`, 2026-09-23.  Baseline: `master` at `5f0a75e3` (v2.8.1), two-dot diffs.

**Method:** a reading pass, nothing executed (Rule 1).  The delta is ~38k lines, so I read by consequence: `Layout.isPathClear` whole, `configureAndLockPath`/`handleMisconfiguredPath`/`unlockPath`, the non-atomic early release in `executePathInternal`, the arrival block (including FR-096's `throwEntryGuard`), `walkStandingTrains`/`walkOneTail`/`tailAlong`, the room and berth rules (`measuredRouteIn`, `whyABerthCannotHoldIt`, `whyTooLongForThisRoute`, `measuredRoomAtTheEndOf`), `Edge`'s occupancy/`runOver` counters, homes (`claimHome`, `rebuildHomeStations`, `setHomeLocomotive`), `HomeStaging.search`/`astar`/`firstClearRoute`/the two tail checks/`atHome`, `AutonomyBuilder`'s copy emission (`nodesFor`, `leavesBy`, `facingOf`, `homeCopy`, edge/lock emission), `GraphReducer.deriveLocks`/`placesAlong`, `AutonomySession.moveOntoFacingCopy`/`faceTheWayItCameIn`/`walkBackFrom`/`routesBlockedByStandingTrains`/`foldRouteTileLengths`, capture's merge, `TailCrossedPrompt`'s walk, and `MarklinControlStation.parseAuto`/`getAutoLayout`.  Every commit of 2026-09-22/23 touching those packages was read as a diff (OB-269 `12d74ad5`, OB-278 `5cb7cf01`, OB-280, OB-282 `74e2d8a1`/`82bdfd12`, OB-270 `d30e3733`, FR-096 `a737360e`, the route-tile fold `c98a6166`, OB-273, `470079a3`).  Facts about Adam's railway were read from `test/layouts/live-snapshot` and `test/operator_layout` with Python (setup, configuration, and the `.cs2` pages parsed for tile types, rotations and duplicated addresses).  `findings.tsv` was grepped for each topic before writing (VAL8-A1, REG7-B3, REG9-A2/REG9V-B2, AUT9-*, GS-B3, GST-*); nothing below re-raises a closed finding.  Every finding rests on reading, and says so.

## A - high

None.  The strongest candidate (AUT-B1, a train routed under a standing tail) is held at B because the live s88 check refuses it wherever the tail physically lies on the sensor contact, and Adam has said his tails hold sensors.

## B - medium

### AUT-B1 - a standing train's tail never reaches the OTHER copies of the rail it lies on, so a train can be routed onto a turning copy under that tail

| | |
|---|---|
| **Disposition** | Fixed - ac5fe2f2 (claim 8873b0a4, red first): `Layout.anotherTailOn` - a standing tail blocks every copy of the metal it lies on |
| **Where** | `Layout.java:2674-2765` (`isPathClear`'s covered-track sweep), `Layout.java:7345-7356` (`walkOneTail`'s reverse-rail cover), `HomeStaging.java:1541-1560` (`passesTheTailsOfTrainsItHasMoved`), `AutonomyBuilder.java:1170-1315` (how copies get lock edges) |

**The rule and its two readings.**  `isPathClear` refuses a path edge `e` when (1) `coveredTrack.get(e)` names another train - Edge equality is by NAME (`Edge.equals`, `Edge.java:255`), i.e. by the start and end COPY names - or (2) some edge in `e.getLockEdges()` is covered, symmetric, and the tail lies on `e`'s places.  Nothing else in the sweep compares a candidate's places with the claimed places.

**What neither reading reaches.**  One `ReducedEdge` is emitted as several runtime `Edge`s - one per start copy that can leave by its exit side and per end copy that arrives by its entry side (`AutonomyBuilder.java:1175-1199`).  On a may-turn square `P` the arrival from the south is two Points, `P (northbound)` and `P (northbound, reverse)`, and so the rail `Q -> P` is two Edges with IDENTICAL places: `Q (northbound) -> P (northbound)` and `Q (northbound) -> P (northbound, reverse)`.  Those two are not each other's lock edges: `GraphReducer.deriveLocks` skips `a == b` (`GraphReducer.java:1449`), and the builder's lock emission only lists copies of OTHER reduced edges (`AutonomyBuilder.java:1301-1314`).  So:

- the tail walk (fork rule, `Layout.java:7254-7274`) at `here = P (northbound)` takes the incoming `Q (northbound) -> P (northbound)`, puts it in `covered`, and claims `P`'s tile and the `Q-P` tiles in `places`;
- `Q (northbound) -> P (northbound, reverse)` is neither that key nor any covered edge's lock partner, so a path ending on (or turning at) `P (northbound, reverse)` passes the sweep;
- `e.isOccupied(loc)` does not catch it either: it reads `P`'s BLOCK locomotive, and nobody is standing on `P` - the train stands at `S`, with its body lying back over `P`.

Scenario: `Q - P - S` northbound, `P` marked may-turn (`canReverse`), everything measured.  Train A drives `Q -> P -> S` and stops at `S` with its length reaching back over `P`'s square (the grey shows `P` claimed, OB-280).  Train B then stands at `Q` facing north (it followed A).  `Q (northbound) -> P (northbound)` is refused `errorTrackCoveredByStandingTrain`; `Q (northbound) -> P (northbound, reverse)` - a send to `P`, or any route that turns at `P` - is not.  B drives into A's tail.

**The reverse-rail cover in the walk does nothing on a built graph.**  `Layout.java:7347-7356` covers "both directions of the same rail" by IDENTITY (`sameRail.getStart() == segment.getEnd() && sameRail.getEnd() == segment.getStart()`).  On a builder graph the two directions run between DIFFERENT copies (`Q (northbound) -> P (northbound)` against `P (southbound) -> Q (southbound)`), so this matches nothing - exactly the defect OB-269 found in the lock counter and fixed there "by PLACE, NOT BY NAME" (`Layout.java:2497-2506`), whose comment then cites this loop as the covered set having "known this since VAL8-A1 / REG7-B3".  VAL8-A1's test (`testATrainCoversTheTrackBehindIt.testATailBlocksTheRailInBothDirections`, line 585) builds the two directions between the SAME two Point objects, which a build never emits.  I could not construct a route onto the reverse direction that does not first pass through the tail or a turning copy (the reverse rail's only entrances are the tail's own squares), so the turning copy is where this bites; but the loop's claim is false as written.

**What compensates, and how far.**  `isPathClear` also refuses an edge whose end's s88 reads occupied (`Layout.java:2433`), and Adam has said *"Yes, tails hold sensors"* (`HomeStaging.java:1740`).  So on his hardware the send is refused WHERE THE TAIL PHYSICALLY REACHES `P`'s CONTACT SECTION.  The places model claims `P`'s tile when the tail reaches into it by any amount (`Layout.java:7365`), so a tail lying on the near part of a two-unit sensor square is claimed, drawn grey, and not detected; and the software rule this section of `behaviour.md` 5c calls "the anti-collision rule" is absent there.  In simulation, or with rolling stock the contacts do not see, nothing refuses.  That is why this is B and not A.

**And the diagram says the opposite.**  The grey is `placesCoveredByStandingTrains` (`AutonomySession.routesBlockedByStandingTrains`), which DOES contain `P`'s tile and the `Q-P` tiles - so `behaviour.md` 5c's *"WHAT IS DRAWN IS WHAT IS REFUSED"* (OB-280) is false exactly here: the square is grey and a route onto it is offered and driven.

**The planner has the same hole and a worse consequence.**  `HomeStaging.passesTheTailsOfTrainsItHasMoved` asks the same two questions of the tails its own moves leave (`covered.containsKey(edge)`, then symmetric lock partners), and it does not have the live s88 for a tail that exists only in the plan.  So the planner can put a later leg onto the turning copy under a tail an earlier leg of the plan left, which the runtime then refuses on live feedback - OB-073, a plan that stops half way with the fleet scattered.

Rests on reading; I could not run it.  **Verification request (needs execution):** a hand-built graph modelled on `core.testATrainCoversTheTrackBehindIt` (it builds Points and Edges with `createPoint`/`createEdge` and places with `setPlaces`): Points `Q`, `P_plain`, `P_turn` (both `setBlock("P")`), `S`; edges `Q->P_plain` and `Q->P_turn` with the same places `["q1","P"]`, place lengths `[1,1]`, `setLength(2)`; `P_plain->S` places `["s1","S"]`, place lengths `[1,1]`, `setLength(2)` (give the edges `setEntrySide` so `entrySideOf` does not fall back to geometry); train A length 3 at `S` with `arrivedFrom` set to the side `P_plain->S` enters by and `arrivedAlong = [Q->P_plain, P_plain->S]`; train B at `Q`.  Feedback all clear.  **Proves it:** `placesCoveredByStandingTrains()` contains `"P"` for A AND `isPathClear([Q->P_turn], B)` is true.  **Control:** `isPathClear([Q->P_plain], B)` is false with `errorTrackCoveredByStandingTrain`.  **Refutes it:** the first call is false.  On the frozen railway the candidates are the four may-turn squares (`BottomMainB`, `RampDown`, `BottomMainPost`, `LowerFront`): place a train on a station reached through one of them by its plain copy, long enough to reach back over it, and ask `isPathClear` for the edge into the turning copy from the square before it.

**Suggested fix:** ask by place, as OB-269 does - in `isPathClear`'s sweep, after the identity and lock-partner tests, refuse `e` when `tailLiesOn(e, someoneElse, coveredPlaces)` for any other train (the places are exactly the metal `e` runs over; a sibling or reverse copy has the same ones), and drop or re-key the identity loop at 7347.  `HomeStaging.passesTheTailsOfTrainsItHasMoved` and `passesTheTailsOfTrainsThatHaveNotMoved` need the same test, or the planner drifts from the runtime.  Watch OB-207's narrowing: a direct places test on the SAME edge is what the "direct case stays whole-edge" bullet chose not to narrow - the places test is only being added, not replacing the whole-edge key.

### AUT-B2 - a home "facing" accepts its turning twin, which faces the other way; OB-282 still brings a train home turned round at a may-turn square

| | |
|---|---|
| **Disposition** | Fixed - ac5fe2f2, with TDY-B1 (the same defect) |
| **Where** | `HomeStaging.java:2504-2515` (`atHome`), `AutonomyBuilder.java:1136-1138` (`copyArrival`), `AutonomyBuilder.java:772-777` (`facingOf`), `test/core/testATrainComesHomeFacingTheWayItWasHomed.java:185-191` |

OB-282 (`74e2d8a1`) made a home carry a facing, and `atHome` now accepts a train only where `home.getCopyArrival()` equals `where.getCopyArrival()`:

```java
return !home.isHomeFacingFixed() || java.util.Objects.equals(home.getCopyArrival(), where.getCopyArrival());
```

with the justification *"that copy, or its turning twin, which is the same arrival and a different thing to do next"*.  But the copy's arrival is not its facing.  `AutonomyBuilder.facingOf` says what each copy faces: a plain copy faces straight through (`node.arrival.opposite()` on straight track), and a turning copy *"Turned round: pointing back at the side it came in by"* (`if (node.reverse) return node.arrival`).  So `X (westbound)` (arrived from E) faces W, and its turning twin `X (westbound, reverse)` (same arrival, E) faces E.  `copyArrival` is written identically on both (`AutonomyBuilder.java:1138`, and the comment there says so: *"A turning copy and its plain twin share it: one arrival"*).  The rule therefore counts as home exactly the copy that is the train turned round - which is the case OB-282 was raised to stop (`behaviour.md` 6: *"on a square with two platforms facing opposite ways brought trains home turned round"*).

And the model does put trains there turned: Return Home runs under `ALWAYS_REVERSE`, for which `Layout.turnsOnArrival` answers `arrived.isTerminus() || ...` (`Layout.java:6498`), and the builder emits a may-turn square's turning copy as a terminus - so a leg that ends on the turning twin switches the locomotive's direction on arrival (`Layout.java:6599`).

**Where it bites on Adam's railway:** `BottomMainB` is may-turn (`canReverse`) and is `EN57-947`'s home in both frozen railways (`1 - Main:20,13`).  Two ways in:
1. **Triage.**  Autonomy routinely ends journeys on turning copies (`Layout.java:6639-6643` measured the menu's BottomMainC path ending on `(eastbound, reverse)` five runs out of five).  A train left on `BottomMainB (westbound, reverse)` - facing east - whose home was set facing west on `BottomMainB (westbound)` is `ALREADY_HOME` (`HomeStaging.java:257`, `487`, `2024`), so Return Home never turns it back.
2. **Planning.**  The greedy pass routes to the exact home copy (`HomeStaging.java:812-817`), but A* (`HomeStaging.java:985-1040`) moves trains to every station and stops when `misplaced == 0`, which is `atHome` - so where A* runs, a final move onto the turning twin is a finished plan, and on arrival the train is turned to face the wrong way.

The one test (`testATrainComesHomeFacingTheWayItWasHomed`) cannot see this: its square, BottomMainA, is not may-turn, and its `arrival()` helper folds `", reverse)"` onto the plain name - *"one arrival, two things to do next"* - so it would call the turned-round train home too.

**The opposite omission is there as well.**  The copy that DOES face the home's way on a may-turn square - the turning copy of the OTHER arrival (`X (eastbound, reverse)` faces W) - is refused, so a train standing there facing correctly is "not home" and gets moved.  And the idle drain makes the answer unstable: `AutonomySession.moveOntoFacingCopy` (`:1530-1538`) re-stands a turned train on the FIRST copy in `facingsFor` order that faces its way, which (sides in `N, E, S, W` order, plain before turning) moves a train turned on `X (eastbound, reverse)` to `X (westbound)` - a different `copyArrival` - so a train Return Home has just called home can stop being home once the railway goes idle, and the next press moves it again.

Rests on reading.  **Verification request (needs execution):** model on `core.testATrainComesHomeFacingTheWayItWasHomed` (same `test/operator_layout` fixture, no bar-lifting needed).  Stand the train on `BottomMainB (westbound)`, `setHomeLocomotive` there (fixed facing), then `moveLocomotive` it to `BottomMainB (westbound, reverse)` and call `triageReturnToHome()`.  **Proves it:** outcome `ALREADY_HOME` while `AutonomyBuilder.facingByName()` gives the two copies opposite facings.  **Refutes it:** the train is reported away from home.  Second probe: move it to `BottomMainB (eastbound, reverse)` (faces W, the home's facing) - the finding predicts "away from home".

**Suggested fix:** compare facings, not arrivals - have the build write each copy's `facingOf(node)` (it already computes it for `facingByName`) and have `atHome` accept copies whose facing equals the home copy's.  Fix the test helper, which encodes the same misreading.  Adam should confirm that the plain copy of one arrival and the turning copy of the other are "the same facing" for a home, since the planner will then treat them as interchangeable goals.

## C - low

### AUT-C1 - `isAlreadyUnderway` is asked outside the monitor that takes the claim, and the dispatch that loses the race deletes the winner's claim

| | |
|---|---|
| **Disposition** | Fixed - 5e2fbda5 (claim 0d060ffb, red first), completed in 8370abb1: the caller's removal compared by value (AUT2-C1) |
| **Where** | `Layout.java:7973` (the check), `Layout.java:3657-3669` (the claim), `Layout.java:8040` (the removal) |

`executePathInternal` asks `isAlreadyUnderway(loc)` at `:7973`, unsynchronized, and the claim it guards (`takingPath.put(loc, path)`) is taken later inside `configureAndLockPath`'s `synchronized (this)`.  `configureAndLockPath` itself says why that shape is wrong, about the cap: *"Claimed HERE, in the same monitor that just did the counting.  Anywhere later and the check and the claim can be pulled apart by another thread doing its own check in between"* (`:3666-3668`).  The window is wide exactly when another train's `configureAndLockPath` holds the monitor (seconds - a sleep per accessory): two dispatches of train A (the Auto tab double-click and the right-click menu, the pair `isAlreadyUnderway`'s javadoc names) both pass `:7973` and queue on the monitor.

- Thread 1 locks P1 and leaves the monitor for `validatePathActuation`.  Thread 2 enters; P2 leaves A's square by the same side (a copy only leaves onward), so it shares P1's first tiles, `isPathClear` refuses it on occupancy, and `configureAndLockPath` returns false before touching `takingPath` (`:3660-3663`).
- Thread 2 then runs `this.takingPath.remove(loc)` at `:8040` - keyed by the locomotive, so it removes THREAD 1's claim.  Until thread 1 reaches `activeLocomotives.put` (`:8053`, after the validation wait) A is in neither map: `getActiveAccs` omits its freshly thrown turnouts (so `MarklinRoute.heldReason` will not refuse a route that re-throws one), `walkStandingTrains` walks one arbitrary reservation (the VD12-B3 window, reopened), `isAlreadyUnderway` answers false to a third dispatch, and the cap undercounts.
- Where P1 and P2 share NO tile - a square nothing arrives at is one unsplit Point that `leavesBy` lets leave by any side (`AutonomyBuilder.java:116`), or a hand-written configuration - both lock, and two threads drive one train.

Narrow (it needs a double dispatch of one train inside another train's configuration phase), which is why C.  `core.testATrainIsDispatchedOnce` tests the claim already registered before the second call and says the race is *"the case the guard was written for"*; it does not test the race.  Rests on reading.  **Verification request (needs execution, a fixture like `core.testATrainIsDispatchedOnce`'s):** hold the Layout monitor from the test thread (`synchronized (layout) { ... }`), start two `executePath` calls for the same train on two threads, let both block, release, and read `isAlreadyUnderway(loc)` immediately after the first `configureAndLockPath` returns and the second has returned false.  **Proves it:** `false` while the first dispatch is still validating.  **Suggested fix:** ask `isAlreadyUnderway` again inside `configureAndLockPath`'s monitor before `isPathClear`, and remove only this call's own claim (`takingPath.remove(loc, path)`, identity of the list).

### AUT-C2 - the entry guard throws a signal red with no look at the routes that are commanding it green

| | |
|---|---|
| **Disposition** | Open - Adam's decision: whether the entry guard should look at the routes commanding its signal before throwing it red |
| **Where** | `Layout.java:8570`, `Layout.java:9195-9212` (`throwEntryGuard`, FR-096) |

FR-096 throws each entry-guard signal of the arrival square RED.  By Adam's own description the signal is one routes cross - *"The next route sets it green"* - i.e. one some path's configuration commands GREEN (`TilePorts` gives a signal tile a GREEN command).  `throwEntryGuard` does not ask `getActiveAccs()` (or anything) whether another locked route is relying on that aspect.  In atomic mode that cannot happen for a signal on the arriving train's own last edge (the path is still held when this runs, `:8570` precedes `unlockPath` at `:8678`).  It can happen (a) for a guard signal placed on an EARLIER edge of the approach, which non-atomic mode may already have handed to a following train, and (b) for a guard signal on the station's OTHER approach (a platform reachable from two ends has one on each, `behaviour.md` 7c) where that approach is shared with a through road before the station's own switch - a through train holding a route across it.  On Marklin signals with a stop section that train is halted mid-route.  No collision; a stranded run.  The exit guard (`refreshOneSignal`) has the same shape and Adam ruled on it (2026-08-23, the two are different signals); this is the new list inheriting it without that ruling having been asked of it.  Reading only.  **Verification request (needs execution):** a hand-built graph, non-atomic, two trains, station B with entry guard S on the far approach, train C holding a locked route across S when train A arrives at B: assert S stays GREEN (proves a defect if it goes RED).  Adam may well say this is fine; worth one question.

### AUT-C3 - a train re-stood onto the other arrival's copy after turning has its own square left unspent by the tail walk, so it claims one square further back than OB-278 allows

| | |
|---|---|
| **Disposition** | Fixed - 6b7301fc (claim c7308e7c, red first): the standing square is spent off an arriving rail.  Second pass 12ed2faa: only where that rail's place provably is the square - on a hand-written graph the places are the track alone, and the first pass stopped the walk short (core.testHomeStaging, red in the round-1 battery) |
| **Where** | `Layout.java:7181-7245` and `7376-7409` (`walkOneTail`, first hop), `AutonomySession.java:1530-1591` (`moveOntoFacingCopy`) |

OB-278 (*"the 2 length tile with the s88 consumes 2 units of the train"*, *"Everywhere"*) is implemented in `walkOneTail` by walking the first hop's places from the END - which works only when the first hop is the copy of the rail that ARRIVES at the standing Point (`fromTheEnd = segment.getEnd() == here`), since only that copy's places include the standing square.  Where no arriving copy matches `arrivedFrom`, the walk takes the outgoing copy (`Layout.java:7239`, *"The other copy is still taken when there is no arriving one, which is the case at a square a train has been turned on"*), whose places start at the first tile AFTER the standing square - so that square is neither claimed nor spent, and the tail reaches one square-length further back.

That state is ordinary, not a corner: after a turn at a may-turn square the idle drain re-stands the train with `moveOntoFacingCopy`, which takes the FIRST copy in `facingsFor` order facing the new way and puts the old `arrivedFrom` back on it (`:1589`).  With sides ordered `N, E, S, W` and plain before turning, a train that arrived from the W (or S) and turned lands on the plain copy of the OTHER arrival (`X (westbound)`, arrived-by E) carrying `arrivedFrom = W`; no rail arrives at that copy from the W, so the first hop is the outgoing westbound rail.  The orange line (`AutonomySession.walkBackFrom`, `:6381-6388`) spends the standing square unconditionally, and the berth rule spends it on the approach, so the grey - "what routing refuses" (OB-280) - runs one square past the orange there.  On Adam's railway all four may-turn squares (`BottomMainB`, `RampDown`, `BottomMainPost`, `LowerFront`) measure 1, so a train turned there blocks one unit more track behind it than it lies on; where that unit is a switch, a road is refused that should not be.  Over-claiming is the safe direction, hence C, but it is the over-refusal OB-278 was written to remove.

Reading only.  **Verification request (needs execution):** hand-built, modelled on `core.testAStationsSizeIsAnAllowance`: square `X` split into `X_E` (arrived-by E, rail out west to `W1`) and a copy arrived-by W; `X` measures 2; train of length 2 on `X_E` with `arrivedFrom = "W"` (as the drain leaves it).  **Proves it:** `placesCoveredByStandingTrains()` contains the first place of `X_E -> W1`.  **Control:** the same train on the copy arrived-by W with `arrivedFrom = "W"` claims nothing behind `X`.  **Suggested fix:** on the outgoing-copy fallback, spend (and claim) the standing square first explicitly, as `walkBackFrom` does.

### AUT-C4 - the route-tile fold rests on "no rule reads a route tile's length", which is false, and it may fold onto a sensor square

| | |
|---|---|
| **Disposition** | Fixed - 3492e38c (claim 4ed14f85, red first): the fold skips sensor squares; javadoc corrected |
| **Where** | `AutonomySession.java` `foldRouteTileLengths` (added by `c98a6166`), `GraphReducer.java:1368-1378` (`sumLength`), `GraphReducer.java:1576-1593` (`placesAlong` via `lengthOf`) |

The fold's javadoc and commit say a route tile's length is read by nothing since OB-273, *"so each piece measured a unit less than he gave it"*.  OB-273's own commit (`9da4b29b`) says the opposite - *"the room and tail sums still count them"* - and the code agrees: `sumLength` and `lengthOf` read `authored.getTileLength` for every step, route tiles included, so an edge's `length` and its `places` always counted the unit.  The fold is therefore neutral for the run through the route tile (same total on the same edge) rather than a repair, and the comment will mislead the next reader about what reads lengths.

One case where it is not neutral: `trackBesideARouteTile` can return a SENSOR square, and "plain track first" treats a feedback tile as plain track.  A sensor square's length is the END place of every edge that arrives at it and the first thing OB-278 spends for a train standing there, so a unit folded onto it lengthens every OTHER approach to that sensor too - a train arriving from the other side then spends one more unit on the square and its tail is claimed one unit short (the under-claiming direction).  Checked on the frozen railway by parsing the pages: none of Adam's five route tiles (`5:6,7`, `5:16,12`, `5:15,13`, `1:19,3`, `1:4,11`) has a sensor beside it on its road (`5:6,7` conducts N-S between two straights; `Tunnel` at `5:7,7` is beside it but vertical, so not on its road).  So this is theoretical on his railway.  **Suggested fix:** correct the javadoc; exclude Points (sensor squares) from the fold targets, or fold only along the same reduced edge's interior steps.  Reading only; no verification needed beyond the javadoc.

## D - not defects

### AUT-D1 - OB-269's narrowing (`Edge.isRunOver`) holds

| | |
|---|---|
| **Disposition** | Closed - checked clean |

Traced every writer: `runOver` moves only in `setOccupied`/`setUnoccupied`, which also move `occupancy`, so the two stay balanced through the lock loop, `handleMisconfiguredPath`, the non-atomic early release (`Layout.java:8381`, `setUnoccupied`) and `unlockPath` (whose lock-partner-only branch uses `setLockedEdgeUnoccupied`, which never touched `runOver`).  Both orderings of an asymmetric lock still refuse (write side through `occupancy`, read side through `isRunOver`), and the FR-001 restriction half narrows to "a route actually arriving at the watched square", which is what FR-001 describes.  The place-based reverse-rail loop also catches a reverse pair on a DIFFERENT leg between the same two squares, which `deriveLocks` skips (`GraphReducer.java:1452` compares tile start/end only).  The self-exemption (`railHeldByThisTrain`) cannot hide another train's claim: two trains cannot both run over one edge.

### AUT-D2 - same-direction copies of one reduced edge are safe at the running side

| | |
|---|---|
| **Disposition** | Closed - checked clean |

`X (from W) -> Y` and `X (from E, reverse) -> Y` are not each other's lock edges, but both end on the same copy (or a copy in the same block) of `Y`, which the running train has reserved, so `Edge.isOccupied(loc, true)` refuses the twin.  The hole in AUT-B1 exists only where the copies end on a square nobody is standing on - under a tail.

### AUT-D3 - the lock relation is by tile, not by accessory address, and that is enough on this railway

| | |
|---|---|
| **Disposition** | Closed - checked clean |

Two turnout tiles on one decoder address are independent track to `deriveLocks`, and `isPathClear`'s configuration preview only checks one path against itself.  But a real crossover pair on one address is safe (every route that would disagree about the address shares one of the two turnout tiles), and the two active pages carry no duplicated TURNOUT address - only signals (`162` on 1 - Main; `232`, `176`, `74` on 2 - Bottom), which routes only ever command GREEN.  Checked by parsing the frozen `gleisbilder`.

### AUT-D4 - the new model fields survive the file

| | |
|---|---|
| **Disposition** | Closed - checked clean |

`Edge.toJSON` writes `places` (with `answered`), `entrySide`, `roomAtTheEnd`, and `Layout.fromJSON` reads all three; `Point.toJSON` writes `entrySignal`, `homeFacingFixed`, `copyArrival` and `fromJSON` reads them (`Layout.java:11162-11177`, `11265-11281`).  Capture writes `homeFacing` only for a home the running layout holds to its copy, and the merge removes a stale one (`AutonomySession.java:4777-4779`, `4857-4863`).

### AUT-D5 - the "farthest sensor" prompt's walk agrees with OB-278

| | |
|---|---|
| **Disposition** | Closed - checked clean |

`TailCrossedPrompt.back` spends `hop.getLength()`, which includes the arriving square, from the first hop (the standing square) onwards, and walks only arriving rails, so no square is charged twice - the PRW-C3 double-charge cannot arise there.

### AUT-D6 - `throwEntryGuard` is called outside every monitor

| | |
|---|---|
| **Disposition** | Closed - checked clean |

(`Layout.java:8570`, before the `synchronized (activeLocomotives)` at `:8676`), so the OB-192 accessory-repaint deadlock cannot come back through it.


## What this pass did not cover

- **Nothing was run.**  Every finding needs the coordinator's execution to confirm; B1, B2, C1 and C3 have requests written for a hand-built graph or a frozen railway.
- **`AutonomyCompanionStore` (6.2k lines), `TileAnnotation`, `TileOverlay`, `TilePorts`, `DiagramMonitor`, `StationIndex`, `LayoutPageEdit`, `AutonomyChecks`** - read only where a finding led there.  The store's round trip (the eleven collections, DD-C6) and the editor's checks were not reviewed.
- **`TileGraph` and `GraphReducer` below the lock/places layer** - the walk that builds `ReducedEdge`s, portals, crossings, permanent turnouts (5e), the switch-conflict refusals.  I trusted the edges it emits.
- **The rest of `AutonomySession` (9k lines)** beyond the methods named above - the setup gestures, rebuild-while-running (6a), import, the facing drain's caller `TrainControlUI.reconcileFacingWhenIdle`.
- **The timetable loop (`executeTimetableInternal`), `runLocomotive`/`runLocomotives`, the s88 wait (`waitForS88Reached`, `updatePendingS88`) and the simulation's pulsed feedback** - not read beyond what the dispatch path touches.
- **The non-atomic release bookkeeping** was read only to check OB-269's counter balances through it; its known lateness is GS-B3 (open, deferred) and was not re-examined.
- **`HomeStaging` outside the search and the tail checks** - `triage`, the IMPOSSIBLE proofs, `auditAgainstRuntime`, the budget split (MFV-B1) and determinism (AMH-C1, open) were not reviewed.
- **Threading census**: I did not re-audit which `synchronized` Layout methods the event thread calls (`testNothingOnTheEventThreadTakesTheRailwaysMonitor` owns that); GST-C1..C7 are open on neighbouring ground.
