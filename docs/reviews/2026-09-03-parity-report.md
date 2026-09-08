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

**Adjudicated 2026-09-08 (MON-C16).**  The four rows sat here unexplained for five days, which is
exactly as useful as no report: a parity regression list whose exceptions are unread cannot tell you
whether the railway lost something.

- **PARITY-901, 902, 903 - `BottomMain*` to `BottomSecondary`: EXPECTED, and the point of the exercise.**
  Adam ruled 2.8.1 wrong to offer exactly this, and the ruling is written down at
  `2026-08-18-manual-test-plan.md:178`: *"it should NOT - a red signal after the end requires a stop at
  TopMainR1 or TopMainR2, a constraint that lived in the hand-authored edge config commands and that the
  derivation cannot currently express."*  He called it "the clearest example of the gap, and worth
  reporting first" - so 3.0.0 not offering it is the fix, not the regression.  Three rows, one ruling.
- **PARITY-904 - `BottomInner` to `Tunnel`, one variant of two: UNEXPLAINED, and the only one that is.**
  Nothing in `docs/` accounts for it.  The other variant survives, so the destination is still
  reachable and no train is stranded, which is why this is worth a question rather than an alarm.
  **What would settle it:** name the two 2.8.1 variants and diff their edge lists - if the lost one is
  the one through a square 3.0.0 now treats as a compulsory turn or a shut arm, it is the same class as
  the three above and equally intended.

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

