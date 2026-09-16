# The runtime railway, reviewed whole

**Status:** open 2026-09-15 - round 1 fixed B2 and C1 (claims `8818d8cd`, fix `64169b0b`); round 2 fixed B1 to Adam's ruling (claims and fix `c02f7000`); B3 measured and left open; C2 and C3 open

**Prefix:** AMR (checked free, with AMG, AMS, AMH and AMV: `SELECT DISTINCT ref FROM finding` in `docs/manual-tests/triage.db`, every declaration spelling in `docs/reviews/`, and a grep of `src/`, `test/` and `docs/`)

**Covers** the whole of `Layout`, `Point`, `Edge` and `TimetablePath` - one of four Fable reviewers Adam asked to look at the full autonomy model rather than the latest fix (AMG, AMS, AMR, AMH).  Read-only; every fix below was run, and B1 and B3 were measured by a probe on the `live-snapshot` fixture (not committed).

**On the pending tests.**  B1 bears on MT-441.  B2 changes what MT-439's Manual step shows for two kinds of station, and adds MT-444.

---

## A - wrong behaviour on the layout

None.

---

## B - incorrect results or refusals in specific configurations

| id | status | where |
|---|---|---|
| AMR-B1 | Fixed | `Layout.bfs` - a route may pass another copy of its own start square or of its destination square |
| AMR-B2 | Fixed | `Layout.explainDestinations(loc, true)` - listed stations the right-click menu does not offer |
| AMR-B3 | Open | `Layout.whyABerthCannotHoldIt` - reads only the last edge |

### AMR-B1 - a route through another copy of the train's own square

| | |
|---|---|
| **Disposition** | Fixed, round 2, to Adam's ruling |

The search marks points visited by name, so another copy of the square a train stands on is new ground, and since OB-229 a route extends round a loop.  `getPossiblePaths` and `pickPath` test only the END against the train's square; `isPathClear` treats an intermediate copy of the train's own square as occupied by the train itself, which is exempt.  So a route may turn round by passing back through its own platform, or reach a destination by passing its other copy first - Adam's *"we should never do a round trip just to change direction"*, with the turn made on the way rather than at the end.

**Measured on the frozen railway** (45 starts, a reversible train of no length):

- 1066 clear routes counting every alternative; **22 pass another copy of the start square**, and 20 of those turn at a reversing square first - `BottomMainPost (southbound) -> BottomMainB (westbound, reverse) -> BottomMainPost (northbound) -> RampUp -> ...`, backing into the next platform and driving forward through its own.  Full autonomy never takes those (`reversesAlongTheWay`); the right-click menu offers **14** of them as the one route to a destination.
- **2** pass both the start's and the destination's other copy and do not turn on the way, so autonomy could take them: `BottomMainC (westbound, reverse) -> BottomMainPost (northbound) -> RampUp -> ... the top level ... -> RampDown -> BottomSecondary -> Tunnel -> BottomMainBCPre -> BottomMainC (eastbound) -> BottomMainPost (northbound, reverse)` - a full lap through its own platform to the turning copy of the square next door.
- No menu route passes the destination's other copy.

**Adam's ruling, shown those numbers:** *"We need to refuse both.  A copy makes a cycle."*

**Confirmed by running.**  `core.testARouteIsFoundPastATerminus.testNoRoutePassesAnotherCopyOfTheTrainsOwnSquare` and
`testNoRoutePassesAnotherCopyOfItsDestination` - a loop out of one copy and back into the other, and a destination
reached only by passing its twin.  Both red: *"expected [false] but found [true]"*.

**Fixed, round 2.**  Neither `Layout.bfs` nor `HomeStaging.firstClearRoute` extends a route through another copy of
its start or its end, so the menu, autonomy, Why Not Moving? and Return Home all refuse the lap.  A station left
unreachable by it gets a sentence of its own - *"The only track route there doubles back through a square this
journey already uses."* - because the question `firstClearOrWhyNot` puts to the TRACK may still walk a lap, and
blaming a terminus for it was what the third claim caught: `testWhyNotMovingSaysTheOnlyWayThereIsALap`.

**What it costs, measured:** 14 of 572 reachable pairs lose their last route, every one from `BottomMainPost`; the
census's routable pairs fall from 1354 to 1301, its refusals at a turn from the 110-210 band to 15, and its refusals
on the way from 50-75 to 0 - the journeys those two rules were refusing were the laps.  behaviour.md sections 5b and
7 carry it.  MT-448.

### AMR-B2 - Why Not Moving? on Manual listed stations the menu leaves out

| | |
|---|---|
| **Disposition** | Fixed |

On Manual the explanation skipped autonomy's standing bars wholesale, but both hand doors filter with `isOfferableToOperator`, which leaves out a station that excludes the train (Adam, 2026-09-04, *"don't even include it in the list"*) and a compulsory-turn station autonomy may choose, for a train that cannot reverse (OB-205, MT-367).  behaviour.md §7 contradicted itself about it.

**Confirmed by running.**  `core.testWhyStuck.testByHandAStationTheMenuDoesNotOfferSaysWhy`.  Red: *"WSX_B excludes 02 0314-1 DDR and the menu does not offer it, yet Why Not Moving? on Manual lists it as somewhere to go"*.  Its control - the same station with nothing set - holds before and after.

**Fixed, round 1.**  `isOfferableToOperator`'s rule moved into `whyNotOfferedToOperator`, which answers with the reason; `isOfferableToOperator` asks it, and so does the Manual explanation - one rule, two callers.  §7 corrected.  MT-444.

### AMR-B3 - the berth rule reads only the last edge

| | |
|---|---|
| **Disposition** | Open - measured; binds on nothing today |

`whyABerthCannotHoldIt` claims only the approach edge's places.  Where the approach crosses no switch, `measuredRoomAtTheEndOf` walks back onto the previous leg and may admit a train longer than the approach, whose overhang then lies on metal the rule never asked about - while the tail walk after parking does claim it.

**Measured on the frozen railway:** of 41 approaches into parking berths, **5 cross no switch** - the two into `RampDown (southbound)` from `TopMainPost`, the two into `BottomMainPost (southbound)` from `1 - Main 7,1`, and `TopMainR0Park`.  None of the railway's tiles carries a length, and the rule declines to judge an unmeasured approach (PRW-B1), so nothing is refused or admitted by it today.  It becomes live the day those approaches are measured.

---

## C - documentation, tests, minor

| id | status | where |
|---|---|---|
| AMR-C1 | Fixed | `Point.validateTrainLength` - unboxed a null train length |
| AMR-C2 | Open | Auto-tier Why Not Moving? on a barred berth asks the length rule and not the berth rule |
| AMR-C3 | Open | `Layout.fromJSON` invalidates the whole configuration for a placed train not in the database, or a lock edge naming a missing edge |

### AMR-C1 - a null length

Every other reader treats a null train length as nothing known; `validateTrainLength` unboxed it inside the route checks.  Unreachable from today's doors.  **Confirmed by running:** `core.testWhyStuck.testATrainWithNoLengthRecordedFitsAStationWithALimit`, red with an exception.  **Fixed:** null fits.

### AMR-C2 - the berth rule in the Auto explanation

MT-262's rule is that a physical refusal outranks a preference; the standing-bar branch asks `whyNoRouteFitsTo` and not `whyABerthCannotHoldIt`.  Manual shows it.  Open.

### AMR-C3 - the legacy loader's all-or-nothing refusals

The Load JSON door only; homes, exclusions, restrictions and roads are dropped with a log line where these two invalidate.  Open.

---

## D - looked wrong and is not

| id | what |
|---|---|
| AMR-D1 | `isPathClear` refuses an edge end whose sensor reads occupied, including one sharing the standing train's sensor; Adam's runs show the sensor clears under a standing train, so it does not bite - correct by a hardware property rather than by the model |
| AMR-D2 | The train cap fences on the run's flag, not the dispatch's tier; unreachable, because both hand doors decline to offer paths while a run is going |

---

## What the passes missed

- **A rule copied into an explanation without the question the doors ask first.**  MT-434 took autonomy's bars out of the Manual answer, and took the menu's own filter with them.
