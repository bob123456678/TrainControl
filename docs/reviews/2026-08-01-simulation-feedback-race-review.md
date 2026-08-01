# Simulation feedback race - field investigation and fix - 2026-08-01

**Prefix for citing this document: `SF`.**

**Version investigated:** the working tree above `5c92967`, during the launch-pad round. The fix
this document reports landed in the same tree. **Trigger:** a field report - a full-autonomy run
stalled silently after "reached milestone BottomMainPost", reproduced both in the suite
(`testReturnHomeOnRealLayout` and later deterministically) and in a manual session on the real
layout. Findings use the A/B/C/D convention in [README.md](README.md).

---

## Status

| # | Finding | Severity | Status |
|---|---|---|---|
| SF-B1 | The simulation's detached clear-behind thread clears a sensor unconditionally, so when two consecutive path points share one s88 the stale clear destroys the next point's announcement and the run stalls forever, silently | B | Fixed - epoch-guarded clears (`simAnnounce`/`simClearBehind` in `Layout`); each announcement bumps the sensor's epoch under its own lock, each clear stands down if any later announcement re-armed the sensor.  Pinned by `testSharedSensorPulsesDoNotWedgeThePath`, confirmed red by the author before the fix |
| SF-C1 | Four parking points (`TunnelLongPark`, `TunnelLongParkReverse`, `LowerParkingInner`, `ParkingTrack4`) lost their `active: false` between the 07-27 and 07-31 saves of autonomy.json, so full autonomy now legitimately routes through the parking area - which is what made SF-B1 reachable | C (data, not code) | Closed, remedy withdrawn - the recommendation inverted when reversing stations stopped being autonomy destinations; the four points should now be left ACTIVE.  See the disposition below.  Only two writers of that flag exist in the codebase (the right-click Active checkbox and JSON load), so the change came from a UI session, most plausibly during the home-staging configuration window that also added the two `home` assignments in the same diff |
| SF-D1 | Withdrawn: a proposed validation rule "a non-reversible locomotive may not traverse a reversing point" | D | Withdrawn by the author before implementation - the `reversible` flag gates TERMINUS arrivals only, by design; reversing points are for every locomotive.  Recorded because the wrong fix was fully argued and offered, and the rejection is the calibration |
| SF-D2 | The inactive-point gate itself (`isPathClear`, auto-running only) was suspected and verified intact - byte-identical to its pre-cycle form apart from the enumeration-logging flag | D | Clean |
| SF-D3 | Regression dating: the race is NOT recent - introduced 2023-08-11 by `ea78c5b` ("Fix deadlock"), which first made simulation drive real feedback state and created the detached clear in the same hunk.  Latent for two years; exposed last week by SF-C1 | D | Recorded |

---

## SF-B1 - the mechanism, with every log line accounted for

The simulation handles each s88-bearing path point in three steps
(`Layout.executePathInternal`, both the intermediate and destination blocks):

1. delay, then set the point's sensor - synchronous, on the path thread;
2. `waitForOccupiedFeedback(s88)` - which requires occupancy to HOLD for
   `FEEDBACK_DURATION_THRESHOLD` = 201ms, else it starts over;
3. spawn a **detached thread**: delay, then clear the sensor "behind the train".

The clear had no relevance check. `BottomMainPost` and `TunnelLongParkReverse` share s88 2013 and
are consecutive on the wedged path:

| time | event | meaning |
|---|---|---|
| .753 | Set 2013 | announcement for BottomMainPost |
| .969 | milestone BottomMainPost | its waiter held occupancy 201ms (.753 + 201 + scheduling) |
| .969 | Set 2013 | announcement for TunnelLongParkReverse - state no-op, already set |
| .970 | Not set 2013 | BottomMainPost's STALE clear-behind, landing after the next announcement |

TunnelLongParkReverse's waiter passed its entry check, then sat in the 201ms hold window - the
stale clear landed inside it, the waiter started over, and no producer would ever set 2013 again.
A permanent stall with nothing in the log but silence. The other interleaving (clear landing
between the announcement and the wait) wedges identically.

**Real hardware is immune**: a physical sensor spanning both points simply stays held; only the
per-point pulse model manufactures the false gap. With action delays of 0 (the test configuration)
the wedge is near-certain per traversal; with the layout's real random delays it is intermittent -
matching both the flaky suite hang and the reproducible-by-patience manual one.

**The fix** models the physical truth: a sensor stays set until the LAST train activity on it has
passed. Each simulated announcement bumps a per-sensor epoch (`AtomicLong`, per-sensor lock); the
spawned clear captures the epoch and stands down if a later announcement re-armed the sensor -
whether from the same path (the shared-consecutive case) or another locomotive's. Unshared sensors
behave exactly as before, so the pulse-and-clear semantics every other simulation test relies on
are unchanged.

**The test** (`testAutonomySimulationSanity.testSharedSensorPulsesDoNotWedgeThePath`) rebuilds the
shape minimally - two consecutive intermediates sharing one sensor, delays 0 - and runs the path
six times under a 15s daemon watchdog. The red is probabilistic per iteration because this is a
race; the author confirmed red before the fix was applied, per the README's discipline.

## SF-C1 - how a two-year-old bug surfaced this week

Diffing the 07-27 backup against the current autonomy.json: four parking points lost
`active: false`, and two `home` assignments were added (`BottomMainA -> 065 001-0 DB`,
`BottomSecondary -> SM31-108`) - the signature of the home-staging configuration sessions. With
the parking area active, `pickPath` legitimately offers it, paths through `TunnelLongParkReverse`
become pickable in full autonomy, and its shared sensor meets SF-B1. The 07-30 debug capture
already shows such paths being enumerated (and refused only for occupancy), which brackets the
data change to 07-27..07-30. The excludedLocs differences in the same diff are set-ordering noise.

### Disposition - 2026-08-01: the remedy inverts, and the four points are not alike

This finding told the author to restore `active: false`. That advice is now wrong, and the four
points turn out to divide two ways that the original reading treated as one:

| Point | station | reversing | ever an autonomy destination? |
|---|---|---|---|
| `ParkingTrack4` | yes | yes | yes - **now excluded**, being a reversing station |
| `TunnelLongPark` | yes | yes | yes - **now excluded**, being a reversing station |
| `TunnelLongParkReverse` | no | yes | no - `pickPath` requires `isDestination()` |
| `LowerParkingInner` | no | no | no - same |

Full autonomy no longer chooses reversing stations as destinations, so the two that *were*
selectable are now structurally out of reach of a random parking; the other two never were
destinations at all, and their `active` flag only ever governed pass-through. So on the
"locomotives parked at random" concern the flag is now redundant for all four - by the new rule for
two of them, and by `isDestination()` for the other two all along.

What the flag still governs is traversal: `isPathClear` refuses any path touching an inactive point
while auto-running, which is what kept autonomy out of the parking *area* rather than merely out of
its berths. That is the exposure this finding actually described - and it is moot, because the race
it exposed (`SF-B1`) is fixed. A path through `TunnelLongParkReverse`'s shared sensor is now
uneventful.

So the four should be left **active**, and the reason is not indifference: `ParkingTrack4` and
`TunnelLongPark` are stations, and deactivating them would put them out of reach of "return home",
which is the feature meant to fill them at the end of a session. Deactivating a parking track now
costs the thing parking tracks are for. (`TunnelLongParkReverse` and `LowerParkingInner` are not
stations, so nothing can come to rest on them either way.)

The general lesson is worth more than the data point: `active` and `reversing` were doing the same
job badly. `active` is a blunt instrument - it removes a point from every flow at once, staging
included - where `reversing` says the precise thing the author meant, which is "autonomy may pass
here and may leave here, but may not park here." The finding is closed by the rule, not by an edit
to the file.

## SF-D1 - the withdrawn hypothesis, kept per the record's rules

The first proposed fix was a validation rule refusing reversing points to non-reversible
locomotives, argued from the terminus precedent (`isPathClear:1332`, `canRest`). The author
rejected it before implementation: `reversible` gates terminus arrivals only, by design -
reversing points perform their direction change for any locomotive. The hang that motivated the
hypothesis was SF-B1 all along; the withdrawal is recorded because a review that deletes its wrong
turns teaches nothing about how much to trust its right ones.
