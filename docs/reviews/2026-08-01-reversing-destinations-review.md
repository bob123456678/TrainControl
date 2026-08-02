# Review of the reversing-destination change (`e04ef83`) - 2026-08-01

**Prefix for citing this document: `RV`.**

**Version reviewed: `e04ef83`, extended through `b56b407` (HEAD) by the dated sections below; on
2026-08-01.** Scope: the commit "Revert automation behavior" - one predicate in `pickPath`, a
documentation rewrite, four tests, and the `SF`/`CP` dispositions it carries - then its two
follow-ups: `b43fd36` (the `RV-C1` fix) and `b56b407` (berths barred as intermediates, correcting
a judgment this document itself had endorsed). The intervening `9803969` commits this reviewer's
own `9fbc6d3` validation section and contains no code. This review knows the original change is
**deliberate**: it implements an intended-behaviour decision, and the review's job is to verify
the decision is implemented coherently and its record is accurate - not to file the behaviour
change as a regression.

Findings use the A/B/C/D convention in [README.md](README.md). One C, three D.

| ID | Finding | Status |
|---|---|---|
| RV-C1 | `checkForSlowerLoc` decides yields from `getPossiblePaths`, the manual tier - a pre-existing divergence this change widens by one predicate, so autonomy can now repeatedly yield to a locomotive it will never dispatch | Fixed (`b43fd36`) - `hasAutonomousDestination` filters the enumeration on exactly the two divergent clauses; pinned by `testYieldingIgnoresALocomotiveAutonomyWillNeverDispatch`, control assertion first.  Two corrections to the finding's own prose accepted; see the validation sections |
| RV-C2 | Filed while validating `b56b407`: the through-berth filter has one call site, so `hasAutonomousDestination` - aligned with `pickPath` one commit earlier - is one predicate behind again, and the yield probe can over-report dispatchability where every berth-free destination is only reachable across a berth | Fixed - the probe now applies `passesThroughReversingStation` and enumerates with `uniqueDest` false, which sidesteps the under-reporting trap at zero cost; pinned by `testYieldingIgnoresALocomotiveWhoseOnlyRouteCrossesABerth`.  Measured unreachable on the live graph.  See the disposition |
| RV-D1 | The exclusion is at the right tier, and only there - selection, not execution - with all four behavioural quadrants pinned by tests | Clean - except its through-traffic bullet, whose judgment `b56b407` reverses; see "The third commit" |
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

With `RV-C1` fixed, this document was briefly closed - reopened by the section below, which files
`RV-C2`. Open across the folder: `RV-C2`, the `UC` record note on the stranded-javadoc detector,
and the parking-area activation above, which is an operational choice for the author rather than
a finding.

---

## The third commit: `b56b407`, and the judgment this review got wrong - 2026-08-01

The author identified and fixed what this review had recorded and then endorsed: reversing
STATIONS were usable as intermediate points, so full autonomy still routed trains *through* the
parking area on their way somewhere else - observed live as "BottomMainA to TopMainR2 via
TunnelLongPark".

**The miss, owned precisely, because the evidence was in this document's own text.** RV-D1's
through-traffic bullet cited `executePathInternal`'s mid-path handling of reversing points
(Layout.java:3195) and still called a berth traversal "uneventful". Those two statements do not
survive being read together: what happens at an intermediate reversing point is a stop and a
direction flip, so "through-traffic" across a berth is a halt-and-shunt inside the parking area
that nobody asked for - not a drive-through. The `SF` disposition's "autonomy may pass here"
framing was the author's recorded intent at the time, and this review adopted it instead of asking
what a pass physically is. Both the record and the concurrence were wrong; the correction came
from the layout, not the reading. Third tally entry, and the costliest kind: not a missed fact - a
recorded fact whose consequence went unexamined.

**The fix is validated clean:**

- `passesThroughReversingStation` bars exactly berths - `isReversing() && isDestination()` - and
  deliberately spares reversing NON-stations, the loops and headshunts whose entire purpose is the
  mid-path flip. Both halves are pinned, control-first (`testAPathThroughAReversingStationIsNotChosen`
  proves an ordinary station still passes before flipping the flag;
  `testAPathThroughAReversingPointIsStillChosen` asserts its precondition that the loop is not a
  station).
- The origin stays exempt by construction - only edge ENDS are tested, the path's start is never
  one (BFS marks it visited first, so no edge can re-enter it), and departures from a berth remain
  free, consistent with the arrivals-only rule.
- The filter sits in `pickPath`'s selection loop, before `isPathClear`, and a refused path still
  enters `seenPaths` so alternatives are tried - a berth-free route to the same destination is
  found when one exists.
- The tier split holds: manual routes and "return home" still cross berths when the user or the
  stager chooses to, and the status panel now marks every station autonomy will not choose with
  one " -" (excluded or berth alike), per-destination and at the current station, with the tooltip
  updated in all eight bundles, ASCII-escaped.

**RV-C2 - the fix's own missed twin, filed open.** `passesThroughReversingStation` has one call
site. `hasAutonomousDestination` - the yield probe aligned with `pickPath` one commit earlier, in
`b43fd36`, precisely so the two could not disagree - was not given the new predicate, so they
disagree again: a locomotive whose berth-free destinations are all reachable only *across* a berth
answers "dispatchable" to the probe and "nowhere to go" to `pickPath`, and the false 30-second
yields `RV-C1` described return. Two honesty notes for whoever fixes it. First, the naive fix -
applying the filter to `getPossiblePaths`' one-path-per-pair answer - errs the other way: the one
returned path may cross a berth while a berth-free alternative exists, so the probe would
under-report and the fairness feature would quietly skip a dispatchable locomotive; erring toward
under-yielding is probably the better side, but it is a choice, not a free fix. Second, the
severity is settleable by the same measurement the `RV-C1` round used: whether any station pair on
the live graph has only berth-crossing routes. This is the cycle's signature error - instance
eight was fixing one of several identical entrances; this is aligning two entrances and then
moving one - and it is filed the day it was created rather than found by the next reader.

### Disposition of `RV-C2` - 2026-08-01

The finding is accepted in full, including its characterisation: the alignment existed precisely to
stop these two drifting, and it drifted one commit later. `hasAutonomousDestination` now applies
`passesThroughReversingStation` alongside the clauses it already mirrored.

**Both honesty notes are answered, and the first turns out not to be a trade-off.** The warning was
that filtering `getPossiblePaths`' one-path-per-pair answer would err the other way, under-reporting
when the kept path crosses a berth but a berth-free alternative exists. That is correct for
`uniqueDest` true - and avoidable, because the flag does not control the search. Reading the method
through: the `do`/`while` calls `bfs` against a growing `seenPaths` until it is exhausted, and
`uniqueDest` gates only whether a clear path is *added to the output*. Passing false therefore
returns every clear path at identical cost, and the probe can then ask the exact question `pickPath`
asks - does any clear, berth-free path to an eligible destination exist - with neither over- nor
under-reporting. The choice the note offered did not have to be made.

**The second note is answered by measurement, and settles the severity as the `RV-C1` round did.**
Barring berths mid-path removes no destination from any origin on the live graph: all eighteen
active origins keep their complete reachable set, so no station pair has only berth-crossing routes
and the divergence was unreachable there. Both `RV-C1` and `RV-C2` are therefore insurance rather
than repairs on this layout - real on a graph whose parking area sits on a through-route, which this
one does not.

**On the tally, the finding is right and the framing is worth keeping.** The lesson generalises past
this pair: any clause added to `pickPath`'s selection has to be mirrored in the probe, because the
probe's whole purpose is to predict what `pickPath` will do. That now lives in
`hasAutonomousDestination`'s javadoc rather than only here, since the next person to extend the
selection will be reading the code and not this folder.

With `RV-C2` fixed, this document is closed. Open across the folder: the `UC` record note on the
stranded-javadoc detector, and the parking-area activation, which remains an operational choice for
the author rather than a finding.
