# Autonomy parity: 2.8.1 against 3.0.0

Four trains on BottomMainA, BottomMainB, BottomMainC and BottomInner, on the same layout,
asked the same question through the same API. 3.0.0 should offer at least what 2.8.1 does.

3.0.0 splits every station by facing, so its facings are unioned before comparing: a route
counts as offered if it is available from **any** facing of the station a train stands at.
Reversing points are not counted against 3.0.0 as destinations, being parking.

| | 2.8.1 | 3.0.0 |
|---|---|---|
| points | 62 | 96 |
| routes enumerated | 26 | 45 |
| places the other lacks | 19 | 15 |

## 1. Destinations

**Every destination survives.** PARITY-901: 7, PARITY-902: 11, PARITY-903: 11, PARITY-904: 16.

## 2. Routes

**4 route(s) are missing or reduced in 3.0.0.**

| Train | From | To | What is missing |
|---|---|---|---|
| PARITY-901 | BottomMainA | BottomSecondary | 2 of 2 variant(s) gone |
| PARITY-902 | BottomMainB | BottomSecondary | 2 of 2 variant(s) gone |
| PARITY-903 | BottomMainC | BottomSecondary | 2 of 2 variant(s) gone |
| PARITY-904 | BottomInner | Tunnel | 1 of 2 variant(s) gone |

3.0.0 additionally offers 28 route(s) 2.8.1 did not, which is allowed.

## 3. Concurrency

Two routes can run at once exactly when the edges they lock do not intersect. Computed from
the lock sets, so it does not depend on two trains happening to be ready at the same moment.

- 2.8.1: 45 concurrent pair(s)
- 3.0.0: 397 concurrent pair(s)
- judgeable (both routes still exist): 27

**No pair that could run concurrently in 2.8.1, and still exists, has stopped.**

## 4. The timed run

Nothing recorded. Simulate mode says so itself - "Auto layout development / simulation
mode enabled. Trains will not run" - so timings need a real Central Station, or a
simulator that moves trains. Sections 1-3 do not depend on it.

