# Building the running graph from the track diagram, reviewed whole

**Status:** open 2026-09-15 - round 1 fixed B1, C1 and C2 (claims `8818d8cd`, fix `64169b0b`); B2 ruled by Adam, no change; C3 answered by claims

**Prefix:** AMG (checked free, with AMS, AMR, AMH and AMV: `SELECT DISTINCT ref FROM finding` in `docs/manual-tests/triage.db`, every declaration spelling in `docs/reviews/`, and a grep of `src/`, `test/` and `docs/`)

**Covers** the whole of `AutonomyBuilder`, `GraphReducer`, `TileGraph`, `TilePorts`, `StationIndex`, `AutonomyChecks`, `LayoutPageEdit` and `DiagramMonitor`, with `AutonomySession`'s builder inputs and `check()`, and `Layout.fromJSON`'s readers - one of four Fable reviewers Adam asked to look at the full autonomy model (AMG, AMS, AMR, AMH).  Read-only; every fix below was run.

**On the pending tests.**  B1 adds MT-447.  Nothing else touches a pending test.

---

## A - wrong behaviour on the layout

None.

---

## B - incorrect results or refusals in specific configurations

| id | status | where |
|---|---|---|
| AMG-B1 | Fixed | `AutonomyBuilder.build` - `"blockedBy": [null]` for a watched square off the graph |
| AMG-B2 | Ruled | `GraphReducer.roomAfterTheLastSwitch` - a permanent turnout or a diamond is summed as plain track |

### AMG-B1 - a restriction watching a square off the graph invalidated the whole configuration

| | |
|---|---|
| **Disposition** | Fixed |

The standing-train half of FR-001 named each watched square through the reduction's Points.  A square with none - a station on an excluded page (which the *Unavailable While Occupied* checklist offers), a station whose page was excluded after pairing, a sensor whose track was deleted - came out as `null`, `Layout.fromJSON` threw reading it, and the whole configuration was invalidated with no setup finding.  The lock-edge half was safe by construction, which is the asymmetry that hid it (FBR-C7 recorded the guard as dead and concluded nothing was dropped).

**Confirmed by running.**  `regression.testStationBlockedByAnotherPoint.testARestrictionWatchingASquareOffTheGraphIsLeftOut`.  Red: *"Bahnsteig is written as held back by nothing: \"blockedBy\" holds a null"*.

**Fixed, round 1.**  A watched square that is not a Point of the reduction is left out of `blockedBy`, as the lock-edge half has always left it out: the restriction quietly stops applying rather than taking the railway out of service.  No new setup check - that would be a new message in eight languages for a restriction that, like one on a deleted square, simply has nothing to watch.  MT-447.

### AMG-B2 - the room walk stops only at addressed switches

| | |
|---|---|
| **Disposition** | Ruled - Adam, 2026-09-15, asked where the walk should stop: **"Neither"**.  Only addressed switches bound a berth's room, as today, and that settles IND9X-A2 as well |

`roomAfterTheLastSwitch` and `unmeasuredAfterTheLastSwitch` stop at `isSwitch()`, which is the five addressed switch types and the scissors.  A permanent turnout or a diamond crossing is summed as plain track, so no `roomAtTheEnd` is written and a train can be admitted that comes to rest across it.  On the frozen railway every permanent turnout is on the excluded test page and no tile lengths are set, so nothing binds today.  Whether a diamond is "between the switch and the station" in the sense of his ruling is the question.

---

## C - documentation, tests, minor

| id | status | where |
|---|---|---|
| AMG-C1 | Fixed | `AutonomyChecks.isTerminus` - a stranded compulsory turn named as a station reaching nothing |
| AMG-C2 | Fixed | four comments said a null entry side means "arrived through a link" |
| AMG-C3 | Answered by claims | the parallel-route rule and the room walk's stopping tiles were under-pinned; two claims, both mutation-checked |

### AMG-C1 - the terminus message could not be chosen

`isTerminus` read the authored `terminus` key, which setting a square's flags clears, and fell back to "no reduced edge leaves the tile", never true of a compulsory turn.  **Confirmed by running:** `core.testAutonomyDiagramSession.testAStrandedCompulsoryTurnIsNamedAsATerminus`, red.  **Fixed:** a compulsory turn counts as a terminus there.

### AMG-C2 - "a null side means a link"

A walk through a paired portal lands on the partner with a null side, but the partner is never a Point; a reduced edge gets a null entry side only where `validatePortals` already blocks the setup.  **Fixed:** `AutonomyBuilder.splitSides` and `build`, `AutonomyChecks`' constant, `AutonomySession.tilesWithATrappedArrival` and `terminiWithTwoWaysIn` say what the squares actually are.

### AMG-C3 - under-pinned rules

| | |
|---|---|
| **Disposition** | Answered by claims, 2026-09-15 |

Nothing asserted that the SHORTER of two parallel routes survives, or that `WARN_PARALLEL_ROUTE` is recorded, or which tile types stop the room walk.  **Two claims, both in `core.testAutonomyDiagramReducer`, each shown failing under a mutation before being left green:**

- `testTheShorterOfTwoParallelRoadsIsTheOneKept` - the passing loop the sibling claim already builds, now asked which of the two roads came out of it.  The kept path must run through the direct track and over none of the loop, in both directions, and `WARN_PARALLEL_ROUTE` must be recorded and non-blocking.  Keeping the long way round would give the pair a length and a room measured over track no train is driven on, which is what makes this more than bookkeeping.  **Measured:** inverting the comparison keeps the seven-tile loop - `[main:1,1, main:2,1, main:2,0, main:3,0, main:4,0, main:5,0, main:5,1]` - and silencing both `noteOnce` calls leaves the reduction reporting nothing.
- `testTheRoomWalkStopsOnlyAtASwitchThatCanBeThrown` - and this is where **Adam's ruling on AMG-B2 is now written into a test**: asked whether a permanent turnout or a diamond crossing should stop the walk he answered *"Neither"*, so the walk counts through both and stops only at `isSwitch()`.  The claim asserts the room NUMBER rather than the predicate, because `isSwitch()` is shared with the drawing code and what breaks if its list changes is the stretch a train's length is judged against.  **Measured:** adding `CROSSING` and `CUSTOM_PERM_LEFT` to the stopping test answers 5 where the rule answers 10, on both halves of the fixture.

One thing the second claim taught while it was being written: `CUSTOM_PERM_*` roads are `into(toward, from)`, which is travel INTO the toe - so a permanent turnout carries its road one way only, and at orientation 1 the toe is east.  The first fixture put the toe west and produced no eastbound edge at all, which the fixture guard caught rather than the assertion.

---

## D - looked wrong and is not

| id | what |
|---|---|
| AMG-D1 | A train can arrive at a sensor against its own one-way marking: a sensor's direction constrains departures, and the copy reached is reported `ARRIVAL_TRAPPED` |
| AMG-D2 | A barred side of a compulsory turn is emitted as a plain reversing copy, so a hand or Return Home route may turn there mid-route - OB-120's "still carries traffic" |
| AMG-D3 | `hasAnyConnection` counts a neighbour facing a side the sensor has no port on, so such a sensor is reported `POINT_ISOLATED` rather than dropped silently |
| AMG-D4 | A two-sensor oval reduces to one half: edge identity is a Point pair, warned by `WARN_PARALLEL_ROUTE` |
| AMG-D5 | A station switched out of service is reported `STATION_UNREACHABLE` - deliberate and pinned |

---

## What the passes missed

- **Two halves of one setting, one of them safe by construction.**  The lock-edge half iterates what was emitted and cannot name a square that is not there; the standing-train half looks names up and can.  A review of the safe half concluded the other was safe too.
