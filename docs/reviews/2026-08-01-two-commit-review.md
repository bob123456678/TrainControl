# Review of the two commits above `5c92967` - 2026-08-01

**Prefix for citing this document: `CP`.**

**Version reviewed: `281dbf6` (HEAD), working tree clean, on 2026-08-01.** Scope: the two commits
`dff8e5a` ("General fixes", 03:53) and `281dbf6` ("Sim race condition", 04:40), hunk by hunk. No
code was changed by this review. The code of `281dbf6` was reviewed blind first - its own review
document ([2026-08-01-simulation-feedback-race-review.md](2026-08-01-simulation-feedback-race-review.md),
prefix `SF`, committed alongside the fix) was read only afterward; the cross-check section records
where the two readings agree and what each found that the other did not.

Findings use the A/B/C/D convention in [README.md](README.md). Two C, three D.

| ID | Finding | Status |
|---|---|---|
| CP-C1 | The epoch guard is per-`Layout`, so a detached clear that outlives its `Layout` bypasses the guard on the successor's run - the same wedge, cross-instance | Fixed - `simClearBehind` stands down when `!isCurrentLayout()`; pinned by `testAClearFromARetiredLayoutStandsDown` |
| CP-C2 | Filed after the review, from a field failure: the new race test constructs six `Layout` objects, and the version counter is **static**, so it retires the soak fixture in the same class - which then dispatches nothing and fails whenever TestNG orders the two methods the other way round | Fixed - the fixture reloads after every method; missed by both readings |
| CP-D1 | `dff8e5a` is the commit form of an already-validated tree | Verified identical |
| CP-D2 | The `SF-B1` fix, re-derived blind: mechanism, lock discipline, twin coverage and unshared-sensor behaviour all check out | Clean - concurs with `SF` |
| CP-D3 | `simClearBehind` would `synchronized (null)` if `simulate` could flip mid-path | Unreachable - recorded as a trap |

---

## CP-D1: the first commit is validated territory

`dff8e5a` commits, unchanged, the working tree that the `UC` document's validation rounds two
through four already read hunk by hunk: the `UC-C17`..`UC-C21` fixes, the launch-pad change, their
tests, and the report edits themselves. File list and content were compared against what those
rounds recorded; nothing rode along. Its record lives in `UC` ("Validation of the resolution" and
the sections after it) and is not repeated here.

## CP-D2: the race fix, read blind - clean

`281dbf6` closes a silent permanent stall in simulation mode: the detached clear-behind thread
cleared a sensor unconditionally, so when two consecutive path points share one s88, the stale
clear could destroy the next point's announcement - inside the 201ms occupancy-hold window or
between the announcement and the wait - leaving the waiter blocked on a sensor no producer would
ever set again. The fix (`simAnnounce`/`simClearBehind` in `Layout`) gives each sensor an epoch:
announcements bump it and set the sensor under the epoch's own lock; each clear captures its stamp
and stands down under the same lock if any later announcement re-armed the sensor.

Verified independently, before reading `SF`:

- **The lock discipline is right.** Bump-and-set and check-and-clear are each atomic under the
  per-sensor lock, so the fatal interleaving - clear checks, announce bumps, clear fires anyway -
  cannot be scheduled. The last occupant still clears, so unshared sensors keep the exact
  pulse-and-clear semantics the other simulation tests rely on.
- **The twin check passes.** The two helpers are the only feedback writers in the automation
  package, and both call-site pairs (intermediate points and the pre-arrival/destination block in
  `executePathInternal`) route through them. No third, unguarded writer exists.
- **The test builds the racing shape minimally and honestly** - two consecutive intermediates on
  one sensor, delays zero for maximal pressure, six iterations under a 15-second daemon watchdog,
  preconditions asserted, feedback addresses (474xx) clear of the other suites, cleanup by name.
  The red is probabilistic per iteration, and the test's comment says so rather than claiming
  determinism.

## CP-C1: the guard's lifetime is the `Layout`'s, and the clears outlive it

The epoch map is an instance field, and the clear threads are detached with a delay of
`minDelay`..`maxDelay` *seconds*. A run can therefore end - `activeLocomotives` empty, every
busy-predicate false, reload permitted - with clears still pending. Load a different autonomy
configuration, start a run, and a leftover clear from the previous `Layout` fires against the
shared model: its stand-down check consults the OLD instance's epoch map, which the new run's
announcements never bump, so it passes its own check and clears a sensor the new run may be
waiting on. The wedge `SF-B1` fixed, one `Layout` boundary later - and `SF`'s summary claim ("a
sensor stays set until the LAST train activity on it has passed") holds only within one instance.

The new test demonstrates the gap unintentionally: it creates a fresh `Layout` per iteration and
needs its 300ms settle sleep precisely so the previous iteration's clears drain before the next
begins - remove the sleep with nonzero delays and the iterations would wedge each other through
the model the same way.

Narrow by construction: simulation is debug-only, and the window is `maxDelay` seconds between one
run ending and the next starting on a reloaded configuration. C, not B, for that reachability -
but the shape is the fixed defect's, so it should not wait for a field report. The machinery for
the fix already exists: `Layout.isCurrentLayout()` was built to answer exactly the orphan-instance
question, and a stand-down in `simClearBehind` when the instance is no longer current closes the
boundary (announcements cannot come from an orphan - a run cannot span a reload - so only the
clear side needs it). Static per-sensor epochs would also work, at the cost of state outliving
every layout.

### Disposition - 2026-08-01

Fixed as the finding proposed: the fence, not static epochs, so no simulation state outlives the
layout that created it. `testAClearFromARetiredLayoutStandsDown` pins it deterministically, which
its racing sibling cannot be - a clear-behind is spawned with a multi-second delay, so the test
completes a one-edge path, asserts the destination sensor is still set **with its clear pending**,
retires the layout by constructing another, and requires the sensor to survive. The middle
assertion is the load-bearing one: it is what stops the test from starting to pass for the wrong
reason if the timing margin were ever lost. With the fence in place the racing sibling's 300ms
settle sleep is belt-and-braces rather than load-bearing, exactly as this finding predicted.

## CP-C2: the new test retires the fixture its own class depends on

Reported from the field after this review was written: `testSimulatedAutonomyRaisesNoWarning` began
failing with *"The simulated autonomy should have executed at least one path"*.

`Layout`'s version counter is **static** and every constructor bumps it, so each construction
retires all earlier instances; a retired layout refuses to dispatch, because `runLocomotives` spins
only `while (this.running && this.isCurrentLayout())`. The race test builds a fresh `Layout` per
iteration - six of them - and the soak test runs against the fixture that `@BeforeClass` parsed.
Whichever method TestNG happens to run first decides the outcome, and within-class order is
arbitrary reflection order: the suite passes or fails between runs of an unchanged tree. When the
race test goes first, the soak test's whole 120 seconds dispatches nothing at all.

Two things about this are worth keeping. First, it failed **loudly** only because the soak test
asserts its own non-vacuity - `sawActivity`, plus actuation and station-change floors. Without
those it would have reported a clean 120-second run in which no train ever moved, which is the
worst outcome a regression guard can have: green, and meaningless. Second, this document's blind
reading called the same test "minimal and honest" and checked its feedback addresses for collision
with other suites - the right instinct aimed at the wrong global. Shared *static* state was not on
either reading's list, and the collision was in the class under the reviewer's nose.

Fixed by making the fixture reload after every method (`@AfterMethod`), so it is always the newest
and therefore current instance whatever the order. That is deliberately a property of the class
rather than of the two tests in it: the next test added here will build a `Layout` too, and will
not have to know why that matters.

*Correction, validation round:* the mechanism sentence above names the wrong enforcing code. The
loop it quotes - `while (this.running && this.isCurrentLayout())` - is in
`executeTimetableInternal`, not `runLocomotives`, and it is not what starved the soak test, which
runs full autonomy: `runLocomotive`'s own loop tests only `running`, and the refusal happens one
layer down, at `executePathInternal`'s entry fence (Layout.java:3046, the July cycle's
fence-abandoned-path work) - every picked path is refused, so the loop spins and nothing
dispatches. The timetable loop the sentence quotes is the line this class's *other* retired-layout
test (`retired.executeTimetable()`) exercises. Same outcome, two different fences; the README's
"read the method that enforces the rule, not the one that looks like it should", filed against
this document's own text.

*Correction accepted, and its reach:* verified in the source before accepting - the
`while (this.running && this.isCurrentLayout())` loop is inside `executeTimetableInternal`,
`runLocomotive`'s own loop is a bare `while(running)` with no version test, and the entry fence at
`Layout.java:3046` is what turns every picked path away. The same misattribution had propagated
into `loadSanityFixture`'s javadoc, where it would have misled the next reader of the *code* rather
than of this folder; corrected there too, which is the reason a wrong sentence in a review document
is worth more than an erratum - it had already been copied.

One fact the correction surfaces deserves its own line, because it looks like a finding and is not.
A retired layout's full-autonomy threads keep calling `pickPath` rather than exiting, which is the
shape of the very defect the July fence work fixed in the timetable loop. It is unreachable in
production: `parseAuto` calls `stopLocomotives()` on the outgoing layout *before* replacing it, so
`running` is already false by the time the successor exists. Only a direct `new Layout(model)` -
what these tests do, and nothing else in the codebase - can retire a layout that is still running.
Recorded as a non-finding so the next reader who notices the asymmetry between the two loops does
not have to re-derive why it is safe.

## CP-D3: the `synchronized (null)` trap, unreachable today

`simClearBehind` fetches the epoch with `get`, not `computeIfAbsent` - correct, since a clear is
only ever scheduled after its announce created the entry. That pairing rests on `simulate` being
stable across one point's handling: if the flag could flip mid-path, an announce skipped while
false plus a clear spawned while true would pass stamp 0 for a sensor with no epoch entry, and the
clear thread would `synchronized (null)`. It cannot flip: `setSimulate` refuses while
`isRunning()`, and `executePath` holds the locomotive in `activeLocomotives`, which keeps
`isRunning()` true throughout. The enforcing layer is real; recorded so the next person to relax
`setSimulate`'s guard knows what rests on it.

---

## Cross-check against the `SF` review

`SF` is the fix's own record - field investigation, mechanism, and fix, written by the author. The
blind reading and `SF` were then compared:

- **On `SF-B1` the two readings agree completely** - mechanism (both interleavings), fix mechanics,
  the last-occupant property, the unshared-sensor invariant, and the test's probabilistic-red
  honesty. Two independent readers deriving the same account of a race from opposite directions
  (one from a field log, one from the diff) is the strongest confirmation this folder's method
  produces.
- **`SF` holds what a code reading cannot reach**, and this document defers to it: `SF-C1` (the
  autonomy.json data regression that made the race reachable - four parking points lost
  `active: false` between the 07-27 and 07-31 saves; still open, restore from the 07-27 backup),
  `SF-D3` (the race dates to `ea78c5b`, 2023-08-11, latent two years), and `SF-D1` (the withdrawn
  reversing-point hypothesis, kept per the record's rules - `reversible` gates terminus arrivals
  only).
- **`CP-C1` is this document's addition** - `SF` does not consider the `Layout` boundary, and its
  fix summary implicitly claims coverage the per-instance epoch map does not have. The finding
  does not reopen `SF-B1`: within one instance the fix is correct and complete.

Open across the folder after this review: `SF-C1` (author's data - four parking points to restore
from the 07-27 backup) and the `UC` document's record note on the uncommitted stranded-javadoc
detector. `CP-C1` and `CP-C2` are fixed above; `SF-B1`'s summary claim that "a sensor stays set
until the last train activity on it has passed" is true without qualification now that the clear
side is fenced at the layout boundary.

---

## Validation of the fix commit `fc09a99` - 2026-08-01

The commit was read against `CP-C1` and `CP-C2` the way the earlier rounds were. Both fixes are
correct; one record error in this document's own `CP-C2` section was found and corrected in place
above.

- **The `CP-C1` fence is at the right layer and only the layer that needs it.** The stand-down is
  the first statement of `simClearBehind`, before the epoch fetch - which also removes the
  `CP-D3` `synchronized (null)` path for orphans - and the announce side correctly has no fence,
  since a run cannot span a reload. The deterministic test's middle assertion (destination sensor
  still set *with its clear pending*, before the retirement) is genuinely load-bearing, as its
  comment claims: without it, a lost timing margin would turn the test vacuous rather than red.
  The test's own two `Layout` constructions would have re-broken the soak fixture, and the
  `CP-C2` `@AfterMethod` reload heals exactly that - the two fixes are coherent as a pair.
- **Two bounded residuals, recorded and accepted.** The fence is check-then-act: no lock is shared
  across instances (each has its own epoch map), so between an orphan's `isCurrentLayout()` read
  and its clear there is an instruction-scale window in which a reload plus a new run's
  announcement on the same sensor could theoretically land - but those two events take a JSON
  parse and a dispatch, orders of magnitude longer than the window, so the interleaving is not
  schedulable in practice. Second, `layoutVersion` is a non-volatile static, so an orphan's read
  could in principle miss a just-constructed layout's bump; same practical bound, same verdict.
  Closing either airtight needs cross-instance state (static epochs or a global lock), which
  `CP-C1`'s disposition already rejected for good reason. Recorded so nobody mistakes the fence
  for a total order; neither residual is worth its fix.
- **The `CP-C2` twin check passes.** Every other test class that constructs `Layout` objects
  either dispatches on the instance it just built (newest, therefore current - the home-staging
  and rename suites) or constructs exactly once in `@BeforeClass` and never again
  (`testLayoutTimetable`), and cross-class order is safe because each class's setup runs after
  the previous class finishes. The sanity class was the only one mixing a class fixture with
  per-test constructions, so the `@AfterMethod` reload closes the defect's whole population.
- **Calibration, owned.** `CP-C2`'s "missed by both readings" includes this one: the blind round
  read the race test closely enough to note its per-iteration `Layout` constructions and reason
  about their effect on *production* reloads (`CP-C1`), and still did not ask what those same
  constructions do to the static version counter's other consumer sitting in the same class. The
  correction under `CP-C2` is the second entry in this document's error tally: one miss, one
  misattribution, both now in the record.
