# Review of the reversing-destination change (`e04ef83`) - 2026-08-01

**Prefix for citing this document: `RV`.**

**Version reviewed: `e04ef83` (HEAD), working tree clean, on 2026-08-01.** Scope: the commit
"Revert automation behavior" - one predicate in `pickPath`, a documentation rewrite, four tests,
and the `SF`/`CP` dispositions it carries. The intervening `9803969` commits this reviewer's own
`9fbc6d3` validation section and contains no code. This review knows the change is **deliberate**:
it implements an intended-behaviour decision, and the review's job is to verify the decision is
implemented coherently and its record is accurate - not to file the behaviour change as a
regression.

Findings use the A/B/C/D convention in [README.md](README.md). One C, three D.

| ID | Finding | Status |
|---|---|---|
| RV-C1 | `checkForSlowerLoc` decides yields from `getPossiblePaths`, the manual tier - a pre-existing divergence this change widens by one predicate, so autonomy can now repeatedly yield to a locomotive it will never dispatch | Fixed (`b43fd36`) - `hasAutonomousDestination` filters the enumeration on exactly the two divergent clauses; pinned by `testYieldingIgnoresALocomotiveAutonomyWillNeverDispatch`, control assertion first.  Two corrections to the finding's own prose accepted; see the validation sections |
| RV-D1 | The exclusion is at the right tier, and only there - selection, not execution - with all four behavioural quadrants pinned by tests | Clean |
| RV-D2 | The old Automation.md contradicted itself about reversing stations; the commit implements one sentence and repeals the other, and the record's "specified all along" claim is supported - by the sentence the diff does not show | Verified |
| RV-D3 | The `SF-C1` disposition inversion (leave the four parking points active) cross-checked point by point | Concurs |

---

## The change, and what was verified (RV-D1)

Full autonomy no longer chooses a reversing station as a destination: `pickPath` gains
`!end.isReversing()` in its end filter. Everything else that can reach a reversing station still
can - a manually picked route, a replayed timetable, and above all "return home", which is the
feature that fills parking tracks deliberately.

- **The tier placement is correct, and the code comment argues it precisely.** An
  `isAutoRunning()` fence in `isPathClear` would also refuse the staging run, because
  `executeTimetable` sets `running` - verified against the dispatch loop. Filtering at selection
  and never refusing at execution is the same split the excluded-locomotives rule uses, and the
  new tests explicitly guard against the exclusion being "simplified" into `isPathClear`, `bfs`
  or `canRest` later.
- **Departures are untouched.** `pickPath`'s start condition never consulted the flag, so a train
  standing on an active reversing station still leaves under full autonomy - which is now what the
  documentation says, and is pinned by `testALocomotiveStandingOnAReversingStationStillDeparts`.
- **Staging is untouched.** `HomeStaging` contains no reference to `isReversing` at all - it
  reaches destinations through `canRest` and `firstClearRoute` - so a reversing station remains a
  legitimate home, pinned by `testReturnHomeMayStillStageOntoAReversingStation`.
- **The four tests cover the four quadrants** - never chosen (including over a higher priority,
  and the all-reversing case answering null rather than falling back), still departs, still
  manually offered, still stageable - and each carries the tier rationale in its comment, so the
  next reader inherits the reasoning with the guard.
- **Through-traffic remains possible, deliberately.** `executePathInternal` handles intermediate
  reversing points (the mid-path direction flip at Layout.java:3195), and the new rule bars
  arrivals only - so full autonomy may still route *through* a parking area to a destination
  beyond it. That is the exposure `SF-C1`'s flag used to govern, and it is moot now that the
  `SF-B1` race is fixed: a traversal of a shared-sensor parking point is uneventful. Stated here
  so nobody reads the new rule as "autonomy stays out of the parking area" - it does not, and is
  not meant to.

## RV-D2: the documentation contradiction, and a near-miss recorded

The commit's code comment and the `SF` status update both say Automation.md "had specified all
along" that reversing stations are chosen only in semi-autonomous operation. The diff alone
appears to refute that: the sentence it *rewrites* said "paths that start from a non-station, or a
reversing station, will never automatically be chosen" - a bar on departures, not arrivals. This
review nearly filed a record error on that basis. The pre-commit file settles it the other way:
six lines above the rewritten sentence, the old document already said reversing stations "will
never be chosen in autonomous operation, rather only semi-autonomous operation where you pick the
route" - the arrival bar, verbatim, since before this cycle.

So the accurate statement is: **the old document contradicted itself**, one sentence barring
arrivals and another barring departures, and the code implemented neither. The commit chooses the
arrival bar, implements it, repeals the departure bar (the new test's comment says it plainly:
that sentence "was wrong rather than unimplemented"), and adds text making the arrivals-only
semantics explicit, including the `active`-vs-`reversing` division of labour. That is the
deliberate change to intended behaviour this review was asked to note: not a revert to a single
documented rule, but a resolution of two conflicting documented rules in favour of the one that
matches what reversing stations are for.

Calibration: the near-miss goes in this document's tally at zero cost - the claim was checked
against the full pre-commit file before being contradicted, which is the `SF-D1` lesson
("verify - do not concede, and do not defend") doing its job. A review that had read only the
hunk would have filed a wrong finding against a right claim.

## RV-C1: the yield heuristic now models the wrong tier, slightly more than before

`checkForSlowerLoc` asks whether the longest-idle locomotive is worth yielding to by testing
`getPossiblePaths(minLoc, true).isEmpty()`. `getPossiblePaths` is the *manual* tier - by design it
ignores `isActive`, the excluded-locomotives set, and now `isReversing` too - so its answer is
"could the user send this train somewhere", not "will full autonomy ever dispatch it". A
locomotive whose only reachable destinations are reversing stations is exactly a parked train, and
parked trains are the ones that accumulate idle time: with `maxLocInactiveSeconds` enabled, a
running locomotive will now yield `YIELD_SECONDS` (30s) to it, repeatedly, for a dispatch that
`pickPath` will never make.

Pre-existing in kind - a locomotive with only inactive or excluding destinations already read as
yieldable - and bounded: each yield is a 30-second wait, the feature is off by default
(`maxLocInactiveSeconds` = 0), and nothing wedges. But this change makes the mismatch *routine*
rather than exotic, because parked-on-purpose is now a normal end state for several locomotives at
once. The fix is one selection-tier filter in `checkForSlowerLoc`'s candidate loop (skip
locomotives whose every possible destination reverses), or a `pickPath`-shaped dry-run instead of
the manual-tier query. C: efficiency of an opt-in feature, no wrong movement.

## RV-D3: the `SF-C1` inversion, cross-checked

The `SF` disposition's four-point table was verified against the rule and the code: the two
points that are stations and reversing (`ParkingTrack4`, `TunnelLongPark`) are exactly the ones
the new predicate removes from `pickPath`'s reach; the two non-stations were never eligible
(`pickPath` requires `isDestination()`); and the traversal exposure the `active` flag used to
govern is moot with `SF-B1` fixed, as noted under RV-D1. The operational advice inverts correctly:
deactivating a parking track would now cost its reachability from "return home" - the one flow
meant to fill it - in exchange for preventing nothing that the reversing flag does not already
prevent. The `CP` status update superseding its own "outstanding" lines follows the folder's
one-status rule the right way: corrected forward, stale lines left as the record of what was
believed.

With `SF-C1` closed by rule rather than by data edit, the folder's sole remaining open item is
the `UC` record note on the stranded-javadoc detector.

---

## Validation of this review - 2026-08-01

`RV-C1`'s mechanism was re-derived from the source and holds: `checkForSlowerLoc` gates yields on
`!getPossiblePaths(minLoc, true).isEmpty()`, and `getPossiblePaths` filters only on
`isDestination`/`isOccupied`, delegating the rest to `isPathClear`, which has no reversing check.
`RV-D1`, `RV-D2` and `RV-D3` are concurred with - `RV-D2` independently, by reading
`e04ef83^:Automation.md` rather than the diff, which confirms the arrival bar at line 397 predates
the cycle and the self-contradiction reading is right.

Two corrections to `RV-C1`, pulling in opposite directions, and one measurement that settles it.

- **It overstates the pre-existing divergence.** The finding says `getPossiblePaths` "by design
  ignores `isActive`, the excluded-locomotives set, and now `isReversing` too". `isActive` is not
  ignored in the context that matters: `isPathClear` refuses inactive points whenever
  `isAutoRunning()`, and `checkForSlowerLoc` runs only from inside `runLocomotive`'s loop, where
  running is true. Exclusions do diverge (`isPathClear:1347` tests them only on non-station edge
  starts, never on the destination). So the pre-existing case was exclusions alone - rarer than the
  finding claims, which *strengthens* its own argument that this change makes the mismatch routine.
- **It understates the reachability.** The C severity rests partly on the feature being opt-in and
  off by default. The author's live `autonomy.json` sets `maxLocInactiveSeconds` to 120: it is on.
- **Measured, it does not bite this layout.** All 18 active stations reach at least one
  non-reversing destination; all five placed locomotives have 15-16 autonomy destinations; and the
  case that matters - a train parked on any of the 16 parking tracks - still reaches 16
  non-reversing destinations, before and after activating them. No locomotive on this graph can be
  stranded with only reversing destinations. `RV-C1` is a latent trap for a layout whose parking
  area is a cul-de-sac, which this one is not.

**Not a fix: disabling it during return home.** It is already inert there, structurally.
`checkForSlowerLoc` is called only from `runLocomotive`'s loop, which `runLocomotives` starts;
staging runs through `executeTimetableInternal`, which never enters it. The defect is inside full
autonomy, not at the boundary with staging.

**The fix, when it is worth doing,** is not the predicate extraction first considered - `pickPath`
cannot serve as a dry run, because it calls `loc.delay(minDelay, maxDelay)` when it finds nothing,
so probing with it would sleep. Filtering the enumeration instead is ~10 lines and duplicates
nothing, since `getPossiblePaths` plus `isPathClear` already enforce every other clause: walk
`getPossiblePaths(loc, true)` and answer true on the first end that neither reverses nor excludes
the locomotive.

### The finding this review did not make: the parking area is still switched off

Fourteen of the sixteen parking tracks carry `active: false` - `ParkingTrack5`..`12`,
`TopMainR0Park`, `TopR1ParkLong`, `TopR1ParkShort`, `TunnelCenterPark`, `TunnelLeftPark`,
`TunnelRightPark` - so "return home" can currently reach exactly two of them, `ParkingTrack4` and
`TunnelLongPark`. `SF-C1`'s inversion was framed around the four points that finding happened to
name; the general consequence of the new rule is the whole parking area. Activating all sixteen
gives staging sixteen berths instead of two, and autonomy still declines every one of them.

One conflict to expect on doing so: `TopMainR0` and `TopMainR0Park` share s88 **1010**, so
activating the latter makes them a single detection section and therefore mutually exclusive -
staging will not place trains on both. The other fifteen have no shared-sensor partner.

---

## Validation of the `RV-C1` fix (`b43fd36`) - 2026-08-01

The fix is correct, and its filter is exactly the divergent set - verified rather than assumed:

- **The two clauses are complete for the context the probe runs in.** `checkForSlowerLoc` is
  reachable only from `runLocomotive`'s loop behind `isAutoRunning() && maxLocInactiveSeconds > 0`
  (Layout.java:2346), so `isPathClear`'s inactive-point gate is armed inside every
  `getPossiblePaths` call the probe makes - `isActive` needs no re-test, exactly as the fix's
  comment claims. Destination-side exclusions never reach `isPathClear` at all (its exclusion
  guard at Layout.java:1346 tests non-station edge *starts* only, deliberately, per its own
  comment), and reversing is the new predicate - so `!end.isReversing()` and the exclusion test
  are the whole divergence, no more and no less.
- **The rejected alternative is rightly rejected.** `pickPath` cannot serve as a dry-run probe:
  its no-free-paths tail calls `loc.delay(minDelay, maxDelay)`, so probing with it would sleep
  the caller - re-verified at the source.
- **The test's control-first design is the "prove the guard actually guards" discipline.** The
  obvious wrong fix - filtering until nothing is ever yield-worthy - would pass a null-only test;
  the leading control assertion (a parked locomotive with a real destination is still yielded to)
  fails on exactly that, and the idle-ordering precondition stops the assertions passing while
  testing nothing. The test's `Layout` constructions cannot recreate the `CP-C2` shape: this
  class exercises selection functions only, and none of them consult `isCurrentLayout()`.
- **The validation section's two corrections to `RV-C1` are accepted, one now verified
  independently**: the `isActive` overstatement (confirmed from the call-site guard) and the
  exclusion-placement detail (confirmed at Layout.java:1346). The overstatement is this
  document's tally entry: the finding's prose claimed a three-way divergence where the armed
  context had two, and the error survived because the finding was drafted from
  `getPossiblePaths`'s own filter without asking which of `isPathClear`'s gates are conditional
  on the caller.
- **The data claims spot-check clean against the live `autonomy.json`**: `TopMainR0` and
  `TopMainR0Park` do share s88 1010 and are the only shared-sensor pair among the parking
  berths (the other parking-adjacent shared sensors - 14, 2012, 2013 - pair a berth's
  *reversing point* with through-track, not the berth itself); sixteen points are inactive and
  every one of them is a reversing point, consistent with the parking-area note above.

With `RV-C1` fixed, this document is closed. Open across the folder: the `UC` record note on the
stranded-javadoc detector, and the parking-area activation above, which is an operational choice
for the author rather than a finding.
