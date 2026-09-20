# The autonomy runtime and planner, 2.7.x to 3.0.0: the delta since AMR

**Status:** open 2026-09-19 - B1 fixed the same day to Adam's ruling (MT-463); C3 and C5 fixed; C1, C2 and C4 open

**Prefix:** RTX (checked free: `SELECT DISTINCT ref FROM finding WHERE ref LIKE 'RTX%'` in `docs/manual-tests/triage.db` returns nothing, no `RTX-` in `docs/manual-tests/findings.tsv`, and no `RTX-[A-D]` spelling in any document in `docs/reviews/`, `src/` or `test/`)

**Covers** `src/org/traincontrol/automation/**` - `Layout`, `Point`, `Edge`, `TimetablePath` and `HomeStaging` - at `5b8dc021` (2026-09-17, branch `autonomy-diagram-r0`), over `v2_7_4c..HEAD` (`1ef11b62`, 2026-07-25, to `5b8dc021`: 12,372 lines added to the package, `HomeStaging` wholly new).  One of four reviewers Adam asked for on the 2.7.x-to-3.0.0 delta; the sibling pass on the setup and editor is `2026-09-19-SET`.  **Read-only: nothing was run, built or edited**, so every proof below is a test to write or a probe to run, not a result.  Where a trace had to leave the package to find the layer that enforces a rule - `GraphReducer.roomAfterTheLastSwitch`, `placesAlong` and `deriveLocks`; `AutonomySession.faceTheWayItCameIn`; the hover worker in `AutoLocomotiveStatus`; the drain in `TrainControlUI` - it was read and is cited, not reviewed.

**What it deliberately leaves to the September reviews.**  AMR read `Layout`, `Point`, `Edge` and `TimetablePath` whole on 2026-09-15; AMG the build; AMS the session; AMH `HomeStaging` whole.  This pass does not re-walk their ground.  It reads (a) what landed after them - the AM rounds 1 to 3 (`64169b0b`, `c02f7000`, `a6ac7396`), AMR-C2 and AMR-C3 (`25ef2b35`, `1f8f11a7`, `397caacb`) - and the fortnight's length work they reviewed as fresh code, and (b) the interactions a per-method pass does not see: two rules that are each right and together wrong, what the runtime and the planner each believe about the same train, what the autonomy threads and the window hold, and what outlives a rebuild.  AMR-B3 (`whyABerthCannotHoldIt` reads only the last edge) is OPEN and parked for the measured railway; it is not re-filed, and nothing below depends on it.

**On the real data.**  `test/layouts/live-snapshot` carries no tile lengths (`"tileLengths": {}`), no `maxTrainLength`, 17 compulsory turns, 20 `autoDestination` entries and 2 assigned homes.  So on the frozen railway every length rule below is inert, as AMR-B3 and AMR-C2 already record; the findings say where each one becomes live.

---

## A - wrong behaviour on the layout, or data silently lost

None found.  Every length rule in the delta was traced in the admitting direction (D1 to D4) and refuses more than the physical truth, never less; the one runtime-versus-planner disagreement found (B1) is the planner refusing a plan the railway would drive, which is the safe direction.

---

## B - incorrect results or refusals in specific configurations

| id | status | where |
|---|---|---|
| RTX-B1 | fixed to Adam's ruling - MT-463 | `HomeStaging.astar` - a train that cannot reverse, standing at the START on a terminus or reversing point that is not its home, is treated as one the plan turned: it may move only to its home, and with no home it may not move at all.  behaviour.md section 6 promises the opposite |

### RTX-B1 - the turn rule reads the square for a train the plan never moved

**Where.**  `src/org/traincontrol/automation/HomeStaging.java` 953-980, inside `astar`:

```java
boolean turnedByThePlan = !l.isReversible() && !atHome(ownHome, at)
    && (turnsATrainArrivingAt(at)
        || turnedOnTheWay(this.movedAlong.get(l)));
...
if (turnedByThePlan && !atHome(ownHome, to)) continue;
```

`turnsATrainArrivingAt(at)` (2503-2506) asks whether the SQUARE the train stands on is a terminus or a reversing point.  It does not ask whether the plan put the train there.  `movedAlong` (82, 925) knows: a train the plan has moved has an entry, a train the railway had standing there at the start has none.

**The ruling it is enforcing, as recorded.**  behaviour.md section 6, lines 1319-1323 (AMH-B1, Adam 2026-09-15): *"a plan may stop such a train on one only when the train has a home and its next move takes it there; **a train the railway already had standing on one moves as before**."*  The AMH document says the same (AMH-B1: *"A train the railway already had standing on one moves as before"*), and the comment eleven lines above the code (963-969, AMW-B3) says the rule is *"CARRIED BY THE TRAIN, NOT READ OFF THE SQUARE ... What follows the train instead is the route that brought it here"* - and then the next statement reads it off the square.  The `turnsATrainArrivingAt(at)` clause is needed: `turnedOnTheWay` looks only at intermediates, so a route that ENDS on a turning square would otherwise leave the train free next move, which is the AMH-B1 case itself.  What is missing is the qualifier that it applies to a train the plan moved there, i.e. `this.movedAlong.containsKey(l)`.

**What the railway does wrong.**  Two shapes, one clause.

1. *Homed.*  A train that cannot reverse stands at the start on a parking berth or reversing point that is not its home - put there by hand, or standing on another train's berth after a session.  Its home is occupied and the only arrangement needs it to step aside first.  The greedy pass (`search`, 806-825) only tries it towards home, refused; A* then refuses it every `to` but the occupied home, so the arrangement is never explored and the answer is `NO_PLAN_FOUND`, with `whatCanBeSeen` (2005-2035) naming the occupied home and nothing else.  Adam's railway has non-reversible trains (EN57-203, 2-8-4 3505 SP, 75 407 DB by the September reviews) and twelve berths that are compulsory turns; a hand send of one of them into another's berth, then Return Home, reaches this.

2. *Homeless.*  The same train with NO home - which is what `Layout.claimHome` (1091-1136) makes of a train placed on a square that is already somebody's home, its comment calling that "a free agent ... which the staging planner may move anywhere free".  Then `atHome(null, to)` is false for every `to`, so `if (turnedByThePlan && !atHome(ownHome, to)) continue;` refuses every move: the train cannot be moved off the berth at all, and the train whose home it is standing on cannot come home.  Two gestures reach it: place a non-reversible train by hand on a berth whose owner is away, press Return Home.

Neither shape is what AMH-B1 was for.  The rule exists so that a plan does not TURN a train and then run it on backwards; a train the railway already had on a terminus was turned by an earlier arrival, or backed in, and section 6 says it moves as before.  The planner is the stricter half here, whose symptom AMH names as `NO_PLAN_FOUND`; the runtime would drive either plan (`isPathClear` has no reversibility rule, 2453-2470).

**What the tests pin, and do not.**  `turnedAndNotSentHome` (test/core/testHomeStaging.java 3942-3999) marks a train as owed to its home only when a MOVE of the plan turned it - so it agrees with the document, not the code: the plans below would pass it.  `testALocomotiveStartingOnATerminusIsPlannedHome` (4101) sends such a train straight home, which both readings allow.  `testATrainWithNoHomeThatCannotReverseIsNeverTurnedAside` (3492, AMV-C7) pins that a homeless train is never moved ONTO a turning square - the `ownHome == null && turnsATrainArrivingAt(to)` clause at 980 - and says nothing about one that starts on one.  `testAFreeAgentIsMovedOutOfTheWay` (1377) moves a free agent off a plain ring square, not a turning one.  Nothing pins either shape above.

**How to prove it.**  Two claims in `core.testHomeStaging`, each with the control that makes it meaningful, using the file's own `station`, `terminus`, `edge`, `json`, `load`, `assign` and `setReversible`:

- `testATrainTheRailwayHadOnATerminusMayStepAsideBeforeGoingHome` - three squares: `HS A` a terminus holding LOC_A, home `HS B` (assign); `HS B` holding LOC_B, home `HS A`; `HS C` empty and plain.  Edges `HS A <-> HS B`, `HS A <-> HS C`, and `HS C -> HS B` one way only, so LOC_B cannot step aside (its only exit runs into `HS A`) and the one plan is LOC_A `A -> C`, LOC_B `B -> A`, LOC_A `C -> B`.  Control first: with LOC_A reversible, assert `READY` and that the plan's first move ends at `HS C` (the fixture really does need the step-aside).  Then `setReversible(false, LOC_A)` and assert `READY` again, and `assertNull(turnedAndNotSentHome(layout, plan, false))` so the claim cannot pass by breaking AMH-B1.  **Red today:** `expected [READY] but found [NO_PLAN_FOUND]`.
- `testATrainWithNoHomeTheRailwayHadOnATerminusCanBeMovedOffIt` - a three-square ring with every edge both ways: `HS A` a terminus holding LOC_C, `HS C` holding LOC_A with `assign(layout, LOC_A, "HS A")` made before LOC_C's positional claim would land (as 3506-3509 does), `HS B` plain and empty.  Precondition `assertNull(layout.getHomeStations().get(loc(LOC_C)))`.  Control with LOC_C reversible: `READY`, LOC_C's move ends at `HS B`.  Then non-reversible: assert `READY` and the oracle null.  **Red today:** `NO_PLAN_FOUND` - LOC_C is refused every `to`.
- After the fix, `testATrainThatCannotReverseIsTurnedOnTheWayOnlyToGoHome` (3360) and `testATrainThatCannotReverseIsNotTurnedMidMoveAndSentOn` (3412) must stay green: they are the plan-moved case, and the `movedAlong.containsKey(l)` qualifier leaves it alone.  A mutation worth running once: drop `turnsATrainArrivingAt(at)` entirely and 3360 must go red, which proves the clause is still needed for a route that ENDS on a turning square.

**If Adam rules the other way** - that a non-reversible train found on a turning square may never be moved anywhere but home - then section 6's sentence and the AMH-B1 entry are what is wrong, the comment at 963 is wrong either way, and the homeless shape still needs an answer, because "never moved" strands the train whose home it is standing on.

---

## C - low: narrow edge cases, comments, traps for the next caller

| id | status | where |
|---|---|---|
| RTX-C1 | fixed 2026-09-19 - probe fired (994 of 83,881 keys); turned trains carry a suffix | `HomeStaging.tailKey` - the arrangement key carries the tails a route leaves and not whether it turned the train; `turnedByThePlan` reads the second on the next expansion |
| RTX-C2 | open | `whyABerthCannotHoldIt` and `walkOneTail` after Mass Assign's switch step: a measured switch and an unmeasured piece behind a berth refuse every train of every length |
| RTX-C3 | fixed - locDeleted clears it | `Layout.locDeleted` - the sweep does not clear `reversedOnArrival`, and the record is keyed by name |
| RTX-C4 | closed by Adam's ruling 2026-09-19 - the sensor stays on; behaviour.md 8 says so and AMR-D1 cites it | AMR-D1 and AMH-B2 rest on opposite statements about Adam's detection hardware |
| RTX-C5 | fixed - the javadoc says the worker | `Layout.whyNoRouteFitsTo` javadoc says it runs on the event thread; the hover moved to a worker (OB-079) |

### RTX-C1 - the key does not carry what the turn rule reads

`tailKey` (HomeStaging 2610-2647) identifies an arrangement by where the trains stand and the edges each moved train's tail covers - MFR-B2's fix, so that two orders leaving different tails are two states.  `astar` then keeps one `routesOf` entry per key (1017-1028, overwritten on a cheaper cost) and reads it back as `this.movedAlong` (925) - and `turnedByThePlan` (970-972) asks `turnedOnTheWay(this.movedAlong.get(l))` of it.  Two routes to the same square that leave the same covered edges but differ in whether they passed a reversing point - a short train, whose tail never reaches back to the turn, brought in over a headshunt and over the direct road - therefore fold into one key, and whichever was found first decides whether the train is restricted to its home next expansion.  MFR-B2's own shape one fact further on.

Reachable only where `firstClearRoute` yields different routes for the same (train, station) from different predecessor states, since it is deterministic per state (1185-1187); a narrow corner, and after B1 only for a train the plan moved.  **How to prove it:** a probe rather than a claim - log `nextKey` and `turnedOnTheWay(path)` at 1017 over the round-3 scatter (`core.testTrainsComeHomeFromAPinnedArrangement`) and grep for one key with both values.  If it never fires there, record that here and close it; if it does, the fix is a `/turned` suffix per train in `tailKey`, the same spelling `firstClearRoute` already uses for its own visited set (1351).

**The probe was run, 2026-09-19, and it fires.**  `nextKey` and `turnedOnTheWay(path)` were logged at every
expansion over `core.testReturnHomeOnRealLayout`: **994 of 83,881 keys were reached with both answers**, on
Adam's own railway.  So the fold is not theoretical, and the fix named above went in - `tailKey` now carries a
`/turned` list, and the loop that dropped a train from the key when its tail covered nothing is exactly where
the two roads were otherwise indistinguishable.  Pinned by
`core.testHomeStaging.testARoadThatTurnedIsNotTheSameStateAsOneThatDidNot`; every planner class and the blessed
baseline are unchanged by it.

### RTX-C2 - a measured switch behind an unmeasured piece closes the berth to everything

`whyABerthCannotHoldIt` (Layout 9046-9082) declines only when NO place on the approach but the berth's own square is measured (PRW-B1, SVV-B1), and then spends the train's length backwards claiming each place BEFORE spending it, an unmeasured place costing nothing (9061-9082).  `walkOneTail` does the same for a parked train (6928-6980: "claimed BEFORE its length is spent ... an unmeasured place the train lies over for nothing is claimed too").  Both are behaviour.md 5c's "refusing side of a rounding", and MON-C13 bullet 1 is Adam's ruling that one positive length on a leg counts.

What the fortnight changed is how that state is reached.  MAL-B1's ruling gives every switch on a page one length in a separate step from the pieces (`assignSwitchLength`, "One length for all switches"), and MT-455 as rewritten tells the operator to reach that prompt by Skipping a piece.  So the ordinary intermediate state of the tool is: switch squares measured, some pieces still at 0.  A berth behind such a piece then has an approach whose places are `[..., switch(s), unmeasured..., berth(d)]`: `anyMeasured` is true on the switch alone, the walk claims the berth, walks the unmeasured squares for nothing and claims the switch - for a one-unit train as much as a nine-unit one - and refuses the berth against every road over that switch, quoting the road and the train's length.  A parked train there blocks the switch the same way.  Every berth on the page behind a skipped piece closes to every train with a length until the piece is measured.

Ruled behaviour on both sides, and the refusing direction; filed because the two rulings meet in a state MT-455's own steps produce, and the refusal says nothing about the unmeasured piece being why.  **How to prove it:** on `core.testABerthAndAPlatformJudgeAnOverhangDifferently`'s berth fixture, set the switch tile's length to 1 and every other tile to 0, give the train length 1, and assert `whyABerthCannotHoldIt` refuses; the same railway with the piece measured at 3 admits it.  Whether the first assertion is the intended behaviour is Adam's to say; if it is, MT-455 should carry the sentence, and the refusal could name the unmeasured piece.  SET-B2 is beside this on the setup side.

### RTX-C3 - the delete sweep and the name-keyed turn record

`locDeleted` (Layout 1012-1072) is written as a list of everything that names a locomotive, its own comment at 1062-1064 saying "a list is a thing one can be missing from".  `reversedOnArrival` (686, written at 8217) is not on it: a turn the railway made at a destination and has not yet written into the setup survives the train's deletion.  It is also the one per-train record keyed by NAME rather than by the `Locomotive` object, so a rename orphans it the same way (nothing in `Layout` hears a rename).

Traced to nothing visible today.  The drain (`TrainControlUI` 7191-7239) hands each record to `AutonomySession.faceTheWayItCameIn` (1549-1590), which writes nothing unless a train of that name stands on that Point, so an orphan is put back by `restoreReversalsOnArrival` and retried on every idle refresh - a map copy per refresh, no log line, carried across every rebuild by `putThePendingTurnsBack`.  The only way it acts is a later train given the old name and standing on the very square, which would then have its facing written from a turn it never made.  Filed as the missing sweep entry, not as a defect anybody will meet.  **How to prove it:** in `core.testHomeStaging` or `testLayout`, record a turn through `executePath` on the fixture that drives one (`core.testATailFollowsTheRouteItCameIn` has the machinery), call `locDeleted(loc)`, and assert `takeReversalsOnArrival()` is empty.  Red today.

### RTX-C4 - two records, opposite hardware

AMR-D1 (2026-09-15-AMR, line 141) closes the shared-sensor question with *"Adam's runs show the sensor clears under a standing train, so it does not bite"*.  AMH-B2 (2026-09-15-AMH, line 42) opens the tail question with Adam asked directly: *"Yes, tails hold sensors."*  A tail cannot hold a sensor the locomotive clears; one of the two describes the simulation (`HomeStaging.snapshot` 214-219 says the simulated feedback is pulsed) and the other the railway.  What turns on it: `Layout.isPathClear` 2362 refuses an edge whose end reports a set sensor, and `HomeStaging.canEnter` 1663-1701 refuses a point whose sensor sibling holds a train - on latching detection both bite at every shared-address pair, and MT-440's expected "no plan" and MT-450 read differently.  Neither is a code defect.  The record should say which hardware Adam has, once, and the other entry should cite it.

### RTX-C5 - a stale thread claim

`whyNoRouteFitsTo`'s javadoc (Layout 5174-5177) says the method "runs on the event thread when the operator hovers the 'no available paths' label".  The hover was moved to a worker (`AutoLocomotiveStatus` 852-870, OB-079), and `explainDestinations`, which reaches it, is `synchronized` - the rule of behaviour.md section 6 forbids exactly what the sentence describes.  A reader deciding how expensive this method may be is told the wrong constraint.  One sentence to change.

---

## D - looked wrong and is not, and checks that came back clean

| id | what |
|---|---|
| RTX-D1 | **The three length rules agree between runtime and planner, with the reversal stop in both.**  `whyTooLongForThisRoute` (9133-9277) judges the destination and any reversing square on the way with `measuredRoomAtTheEndOf` of the prefix, lets `theApproachItselfHoldsIt` admit at a station autonomy may choose, then asks `whyABerthCannotHoldIt`; `HomeStaging.firstClearRoute` (1285-1349) asks the same three of the same statics in the same order, the stated capacity through `canRest` (1321-1335).  `measuredRouteIn` (8940-8949) and `measuredRoomAtTheEndOf` (9381-9384) both stop at a reversing END of a non-last edge and exempt the last, and both treat an unmeasured leg as the end of the count.  The FR-087 message quotes the larger bound (9265-9266).  Nothing here disagrees |
| RTX-D2 | **The room rule counts the resting square and the tail does not spend it - deliberate on each side, and the pair over-blocks, never under.**  `roomAfterTheLastSwitch` (GraphReducer 1229-1231) starts the room at the arriving square's own length; `walkOneTail` skips that square's span (`spendableAllowance`, 7069-7085; 6953-6971) and spends the whole train behind it, on Adam's allowance ruling.  So a train admitted at exactly-fits past the switch has its tail modelled onto the switch square, which under MAL-B1 carries a length, and roads over it are refused to others - the stranding cost behaviour.md 5a accepts at a station, and at a parking berth `whyABerthCannotHoldIt` uses the tail's arithmetic (9077-9079) and refuses, which is the right outcome.  MAL-B1 and MAL-B2 record the same arithmetic |
| RTX-D3 | **FR-087 lets a tail lie back past a through station on the route, and the place walk protects that station.**  Traced for a route S1 -> S2 -> S3 with a train longer than the last leg: the tail claims S2's square as the last place of `S1 -> S2` (`placesAlong`, GraphReducer 1514, includes the far endpoint), and a second way into S2 must run through a switch tile that both approaches share, so it is a lock edge (`deriveLocks` 1370-1409 keys on path tiles) and `tailLiesOn` refuses it; the way in from S3's side is the covered rail's opposite (6885-6896).  No admitting hole found |
| RTX-D4 | **The bounded-but-unmeasured bound (`roomAtTheEnd == -1`, 9403-9424) and FR-087 compose in the ruled direction.**  A last leg measured only before its switch gives `bound = leg length`; `measuredRouteIn` counts the same leg whole plus earlier legs; so a train longer than the last leg is admitted at a station autonomy may choose to stand across a switch nobody measured past - which is what 5a's platform relaxation says, and a berth still gets the berth rule |
| RTX-D5 | **`executePathInternal` clears `arrivedFrom` on the departed square (8173-8177) but not `arrivedAlong`.**  Harmless: `unlockPath` reaches `setLocomotive(null)` on the start in both atomic and non-atomic modes (3856-3858, 3951-3954, or the early release at 7987), and `Point.setLocomotive` clears both on a change of occupant (570-574).  The line at 8176 is redundant rather than half of a pair |
| RTX-D6 | **A reserved intermediate claims no tail.**  `walkStandingTrains` (6648-6658) walks every Point whose `currentLoc` is set, reservations included (`reserve`, Point 593-600); a reserved through square has a null side and two unwalked neighbours, so the fork rule (6788-6852) stops it at once and nothing is claimed.  A dead-end intermediate cannot exist |
| RTX-D7 | **Thread-safety of the fortnight's additions.**  `edgesATailWouldCover` and `placesATailWouldCover` (7017-7041) are `synchronized` and asked only from the planner, which runs when nothing moves; `reversedOnArrival` and `lastArrival` are concurrent maps (686, 452); `isPathClear` from the unsynchronized `pickPath` reads `arrivedFrom`/`arrivedAlong` off Points another driver thread may be writing (8164-8168) - a reference swap of an unmodifiable list, so a torn read cannot happen and a stale one is re-checked under the monitor in `configureAndLockPath` (3479-3486).  The DR-B7 hazard on the point and edge maps is unchanged and documented at `getEdges` (8524-8567) |
| RTX-D8 | **What outlives a rebuild is what is meant to.**  Pending turns are drained and put back only if absent (3201-3211, 3420-3431; D3-C5); `arrivedAlong` crosses by name and is dropped whole when a rail is gone (`roadNamed`, 6346-6386); a placement AMR-C3 drops still has its `arrivedFrom`/`arrivedAlong` written onto the empty Point (11515-11530), which no reader consults without a locomotive and `setLocomotive` clears on the next occupant.  `HomeStaging`'s caches (`coveredByAMove`, `placesByAMove`, 147-150) live with one plan |
| RTX-D9 | **AMR-C2's `whyNoRouteFitsTo` (5184-5234) asks both physical refusals of every alternative and answers "no refusal" on the first route that passes both** - the shape `isPathClear` uses, one route being enough to say no.  On Manual the menu's reason wins (5108-5117, AMV-C5) |
| RTX-D10 | **`TimetablePath`'s delta is a comment** (X8-C4: the field is milliseconds despite its name).  Nothing to review |

---

## What this pass did not cover

- **Nothing was run.**  Every "red today" above is read, not measured; B1's two claims are the first thing to write, and the mutation in its last bullet is what proves the fix keeps AMH-B1.
- **The probes AMR and AMH built** on the frozen railway were not rebuilt; the counts they report (41 unmeasured approaches, 14 lost routes, the 15-second budget) are cited as theirs.
- **The reducer and the session** were read only where a `Layout` rule needed its input traced (`roomAfterTheLastSwitch`, `placesAlong`, `deriveLocks`, `faceTheWayItCameIn`); SET reviews them.
- **`executeTimetableInternal`'s retry and abandonment**, `configureEdge`, `validatePathActuation` and the signal refresh were not re-read beyond what B1's execution path needed; AMR read them whole and nothing in the delta touches them.
- **Pasting, facing and the direction reconciliation** (behaviour.md 3 and 4) are `gui`/`automationui` doors and were left to their reviewers.
- **The A* budget** (AMH-C1, OB-230) is deferred at Adam's word and was not measured again; C1 is the one thing this pass adds to it.

## Seen outside my scope

- **The working tree was already dirty under `cs2_sample_layout/` when this pass began** - `git status` shows `config/gleisbild.cs2` and `config/gleisbilder/1 - Main.cs2` modified and `config/autonomy/Main_bak.json` untracked, plus an untracked `random_test_layout/`.  Nothing here touched them; whoever did should know they are unstaged.
- **`GraphReducer.roomAfterTheLastSwitch` includes the arriving square in the room** (1229-1231, and its javadoc says so) while `unmeasuredAfterTheLastSwitch` asks for it too - consistent with each other; D2 records what the pair costs against the tail walk.  SET-B2's shared-square finding sits on the same reducer and would move `measuredRouteIn`'s number by a share.
- **`AutoLocomotiveStatus` 404-410 and 852-870** carry two long comments about the same freeze; the second is the live one.  Cosmetic.
