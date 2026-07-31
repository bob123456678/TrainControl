# Home-parity review - 2026-07-31

**Prefix for citing this document: `HP`.**

**Version reviewed:** commit `c5c499c`, branch `master`, working tree clean. **Scope:** the latest
three commits - `21ede98` ("Fixes", the I18n number-grouping change), `eeabc31` ("UI home
prettification"), and `c5c499c` ("Home logic fixes", the return-home round). The commit between them
and the previous review (`319399c`) is the `SWC` document's own record plus the `SWC-C9` punctuation
fix, and needs no separate coverage. **Reviewed:** 2026-07-31. **No code was changed as part of this
review, and no tests were run** - the author builds and tests in NetBeans. Claims were verified by
reading the enforcing method on both sides of every parity assertion, per [README.md](README.md).

**The commissioning note, taken as ground truth:** the return-home specification changed
deliberately, to ensure parity with the main graph routing. Accordingly this review does not treat
the spec changes as regressions; it asks whether the implementation achieves the parity it claims,
and it reads each planner rule against the runtime rule it mirrors - the method
[HS](2026-07-27-home-staging-review.md) established for exactly this code. The three actual spec
changes, confirmed against the diff: the shared-sensor rule became structural (mutual exclusion of
active points sharing an address, in `canEnter`) instead of a function of snapshot feedback plus a
measured hardware property; the A* search gained a 15-second wall-clock budget beside a halved state
limit; and path enumeration stopped logging its refusals. The station-exclusion split
(stop-forbidden vs pass-forbidden) is **not** a change - it matches what `HS-D1` recorded - but it
was re-documented at both enforcement sites after a stricter reading was tried and reverted, and it
is newly pinned by tests.

Findings use the A/B/C/D convention in [README.md](README.md).

---

## Status

| # | Finding | Severity | Status |
|---|---------|----------|--------|
| HP-C1 | `canEnter`'s new shared-sensor rule skips inactive siblings, so a train parked on a deactivated point does not close the section it physically holds - the planner routes another train through the active twin and the runtime refuses it at execution, aborting the run | C | Fixed - the sibling skip no longer tests isActive, so a train on a deactivated point closes its section.  Pinned by `testATrainOnAnInactivePointStillClosesItsSection` |
| HP-C2 | The planner never tests the origin, but the runtime's inactive-point rule tests every edge start including the first - so a locomotive standing on an inactive point is planned home and refused at departure | C | Fixed - `firstClearRoute` refuses an inactive origin, matching the rule `isPathClear` applies to the first edge.  Pinned by `testALocomotiveOnAnInactivePointIsNotPlannedHome` |
| HP-C3 | `auditAgainstRuntime` compares the planner against `getPossiblePaths`, which offers destinations that exclude the locomotive (it lacks `pickPath`'s filter) - a permanent phantom disagreement on any layout with a free excluded station, logged by the very instrument the round exists to keep trustworthy | C | Fixed - the audit skips excluded destinations, symmetric with the inactive skip beside it.  `getPossiblePaths` is left alone, since changing it would change two UI surfaces.  Pinned by `testTheParityAuditIsSilentWhenTheTwoAgree`, the audit's first test |
| HP-C4 | Two home assignments on one shared-sensor section are provably impossible but burn the full 15-second budget before answering NO_PLAN_FOUND, because neither `triage` nor the unreachable check knows about detection-section conflicts between goals | C | Fixed - homes are scanned pairwise for shared sections at plan time, so conflicting goals answer IMPOSSIBLE with both locomotives named instead of spending the budget to say maybe.  The existing shared-sensor test now asserts the outcome, not just the refusal |
| HP-C5 | `HomeLocomotiveMenu.confirmExclusion` calls the Layout-synchronized `setHomeLocomotive` on the EDT, which blocks for up to a path's whole configuration time if autonomy is driving - the `TCR-C1` shape at a new entrance | C | Fixed - `confirmExclusion` clears the home on a worker.  The other entrance named here, `HomeLocomotiveMenu.apply`, is untouched and still on the EDT |
| HP-C6 | Fix residue, three locations: the javadoc of the deleted `restingTrainsHoldSensors` field now documents the `HomeStaging` constructor; `describe`'s javadoc in `testReturnHomeOnRealLayout` is stranded above `describeLocation`'s; `logPathError` gained a stray double blank line | C | Fixed - all three: the deleted field's javadoc, the stranded `describe` javadoc, and the double blank line in `logPathError` |
| HP-D1-D10 | Clean checks: the parity table re-read rule by rule, both spec changes verified against the runtime, the I18n conversion, the Point invariant, the logging change's caller census, and the fixture questions behind the tests | D | Verified clean |

No A findings and no B findings: nothing in the three commits produces wrong behaviour on the
layout, and each C above either needs a narrow configuration, degrades an answer rather than an
action, or is cosmetic. HP-C1 is the closest call and its severity is argued in its entry.

**Resolution - 2026-07-31.** All six fixed the same day, each verified against the enforcing method
on both sides before being changed.  The two spec-parity findings were accepted as stated: HP-C1
because a detection section is electrical and the active flag is a routing concept, and HP-C2
because staging executes with autonomy running, which is the case `isPathClear` applies its
inactive rule to.

Four tests were added or strengthened, covering what the round noted as unexercised: the audit had
no test at all and now has one; HP-C1 and HP-C2 each got a train-on-an-inactive-point fixture; and
the shared-sensor conflict test now asserts IMPOSSIBLE with both locomotives named rather than only
that no plan came back - the assertion that distinguishes HP-C4's fix from the behaviour it
replaced, since the old answer was also not-possible, just fifteen seconds later on a real layout.

Not done, and deliberately: `getPossiblePaths` still lacks `pickPath`'s exclusion filter.  Adding it
would change what `AutoLocomotiveStatus` and `LayoutRightclickAutonomyMenu` offer an operator, which
this document correctly calls a spec decision rather than a bug fix.

---

## The three commits, verified

**`21ede98` - whole numbers in messages are identifiers.** `I18n.f` now passes Integer, Long, Short
and Byte arguments to `MessageFormat` as text, so a feedback UID of 1001 renders as "1001" and not
"1,001" (or "1.001" on European locales, since the static `MessageFormat.format` uses the default
locale, not the bundle's). The conversion was checked at its edges: empty varargs pass through, a
null element fails both `instanceof` tests and passes through, floats and doubles still go to
`NumberFormat` (nothing passes one today, and for a measured value that would even be right). The
premise the change stands on - no placeholder in any bundle asks for its own format, because
`{0,number}` handed a String throws at run time - is held by `testNoPlaceholderAsksForItsOwnFormat`,
which scans every line of all eight bundles, and the rendering itself by
`testAWholeNumberInAMessageIsNotGrouped`, asserted on digits so the test is locale-independent (D6).

**`eeabc31` - home outline dots on a reversing cross.** The dot width drops from 4px to 3px only for
reversing stations. The load-bearing claim is that `isReversing()` exactly identifies the cross
shape because a terminus can never be reversing - and that was verified at the enforcing methods,
not the comment: `Point.setTerminus` throws if the point is reversing and `Point.setReversing`
throws if it is a terminus, so the invariant holds from both directions
([Point.java](../../src/org/traincontrol/automation/Point.java)), and
`testAPointCannotBeBothTerminusAndReversing` pins both doors plus the control case (D7). The
GraphStream claim (`DotsShapeStroke` dashes as `{width, width}`) is a library internal that cannot
be pinned from here; its consequences are purely visual and were validated by eye.

**`c5c499c` - the return-home round.** Verified piece by piece below; the parity table is D1-D5,
the findings are what the tracing turned up at the edges of the new rules.

---

## The parity core, rule by rule

The planner cannot call `isPathClear` - it reasons about hypothetical futures, and `isPathClear`
reads live feedback - so it re-implements the traversal rules, and the whole review question is
whether the copy matches the original. Each row was read in both sources.

- **Shared sensors (the spec change).** Runtime: every edge end's feedback must read clear, so on
  hardware where a resting train holds its detector, a train standing anywhere on a shared section
  blocks the whole section. Planner, now: `blockedSensors` blocks only sensors *no known locomotive
  explains* - something is on the track the graph cannot move - and the known-train case became
  structural in `canEnter`: two active points reporting one sensor never both hold a train
  ([HomeStaging.java:741](../../src/org/traincontrol/automation/HomeStaging.java)). For known trains
  on active points this matches the runtime on holding hardware exactly, including arrivals the plan
  itself creates - which is the case `HS-B4` was about, fixed then by measuring the hardware and
  fixed now by refusing the combination outright. On pulsed hardware the structural rule is
  deliberately more pessimistic than the feedback the runtime would read; that is the safe
  direction, it removes `HS-B4`'s recorded mixed-hardware limitation, and the moving train is
  correctly exempt from its own reflection (`there.equals(loc)`) so a locomotive can still traverse
  the sibling of its own origin. The rule's two edges that do *not* match are HP-C1 (inactive
  siblings) and HP-C4 (conflicting goals).
- **Station exclusions.** Unchanged, and now stated identically at both sites: `isPathClear` refuses
  excluded *non-station* intermediates only ([Layout.java:1288](../../src/org/traincontrol/automation/Layout.java)),
  `pickPath` refuses excluded destinations at selection, and the planner splits the same way -
  `canEnter` lets a locomotive drive through an excluding station, `canRest` refuses to park it
  there. `testAStationExclusionStopsParkingNotPassing` uses a line graph so the pass-through is the
  only route and the test cannot be satisfied by the long way round;
  `testANonStationExclusionBlocksPassage` is its control. The tried-and-reverted stricter reading
  (45% of station pairs lost on the author's layout) is recorded in both comments, which is exactly
  where the next person will trip over it.
- **Terminus.** Arrived at, never expanded (`firstClearRoute`), reversibility demanded by `canRest`;
  the runtime says the same from the other end. Newly pinned in all four directions: intermediate
  stop allowed, non-reversible refused, drive-through impossible, departure from a terminus fine -
  and the drive-through test first asserts the reachable control case before asserting IMPOSSIBLE,
  so it cannot pass vacuously.
- **Reversing stations.** No rule on either side, and the absence is now pinned
  (`testAReversingStationIsAnOrdinaryStationToThePlanner`) so that a runtime rule added later must
  teach the planner in the same change.
- **Lock edges, occupancy, conflicts.** Re-read against `Edge.isOccupied` ("end point holds someone
  else, or the locked flag"): `canEnter`'s state occupancy and `lockEdgesFree`'s endpoint check
  mirror the at-rest half, the flag half stays correctly unmodelled (nothing holds a lock in a
  planned state), and `withCommandsOf` still mirrors the one-setting-per-accessory rule. Unchanged
  by this round; confirmed still true (D2).

---

## C findings

### HP-C1 - the structural rule stops at the active flag, but the electricity does not

**Where:** [HomeStaging.java:751](../../src/org/traincontrol/automation/HomeStaging.java) -
`if (sibling.equals(p) || !sibling.isActive()) continue;`

A detection section is electrical: a train standing on a point holds the sensor whether or not the
planner considers the point active. A train parked on a *deactivated* point (a siding taken out of
service with a locomotive stored on it - `snapshot` records occupants of inactive points, so the
planner knows it is there) leaves its sensor explained, hence unblocked; the sibling skip then lets
the planner route a second train into the active twin of that section. On holding hardware the
runtime reads the real sensor and refuses the move, and the run aborts partway - the `HS-B4`
failure mode at a hole one flag narrower. C rather than `HS-B4`'s B because the configuration needs
a train parked on an inactive point *and* a shared address *and* a route through the twin; the
`HS-B4` argument ("shared addresses are not exotic") covers only the second. Dropping
`!sibling.isActive()` from the skip closes it: nothing else can put a train on an inactive sibling,
so the condition's only effect is this hole.

### HP-C2 - departures from inactive points are planned and then refused

**Where:** `canEnter`'s contract ("the origin is never tested",
[HomeStaging.java:724](../../src/org/traincontrol/automation/HomeStaging.java)) against
`isPathClear`'s inactive rule, which tests `e.getStart().isActive()` for every edge *including the
first* ([Layout.java:1305](../../src/org/traincontrol/automation/Layout.java)).

The origin exemption exists so the moving train's own sensor cannot block its own departure, and for
sensors it is right. But it also exempts the origin from the active test, and the runtime does not:
staging execution runs with autonomy running (`HS-D1` verified that), so a locomotive standing on an
inactive point is planned home and refused at its first edge. Pre-existing - `HS-D1`'s inactive row
verified entered points and never asked about the origin - and first coverage here. Same
reachability shape as HP-C1 (a train on a deactivated point), same graceful failure, C for the same
reason. The debug audit would flag it as a planner-optimistic divergence when the state occurs.

### HP-C3 - the parity audit has a phantom-divergence class it already knows how to filter

**Where:** [HomeStaging.java:318](../../src/org/traincontrol/automation/HomeStaging.java)
(`runtimeSays` built from `getPossiblePaths`), [Layout.java:2429](../../src/org/traincontrol/automation/Layout.java)
(`getPossiblePaths`' destination filter: not equal, not occupied, is a station - no exclusion test),
[Layout.java:2305](../../src/org/traincontrol/automation/Layout.java) (`pickPath`, which does test
`!end.getExcludedLocs().contains(loc)`).

The audit compares planner and runtime "for the ONE state where both can be asked". But its runtime
oracle is `getPossiblePaths`, which offers a free station that excludes the locomotive, while the
planner's `canRest` correctly never will - so every audit on a layout with a reachable free excluded
station logs a disagreement that is neither side misreading the spec, just two runtime methods
disagreeing with each other (`pickPath` filters; its enumeration twin does not). The author's own
layout carries station exclusions, so the instrument cries wolf exactly where it is meant to be
listened to. Debug-gated (`planReturnToHome` runs it only under `isDebug()`), hence C. The audit
already handles the analogous case - it skips inactive destinations from `runtimeSays` with a
comment explaining the divergence is correct - so the smallest fix is the symmetric skip for
excluded ones. Giving `getPossiblePaths` the `pickPath` filter instead would also change what two UI
surfaces offer (`AutoLocomotiveStatus`, `LayoutRightclickAutonomyMenu`) - defensible if exclusions
should bind operator-facing lists, but that is a spec decision, not a bug fix, and it belongs to the
author.

### HP-C4 - conflicting homes on one section burn the budget before saying "maybe"

**Where:** `plan()`'s unreachable check ([HomeStaging.java:267](../../src/org/traincontrol/automation/HomeStaging.java)),
which tests each home with `canRest` and `connected` - both single-locomotive questions.

Assign two locomotives homes on two active points sharing one sensor (a platform and its bypass -
the easiest wrong click on a layout that shares addresses, and nothing in the assignment UI warns:
`canBeHome` is a one-station question). The goal state itself now violates the structural rule, so
no plan exists; but neither `triage` nor the unreachable check looks at goals *pairwise*, so the
search exhausts - on the fixture's four points it empties its queue instantly, which is why
`testTwoActivePointsSharingASensorAreNeverBothOccupied` runs fast, but on the author's 62-point
layout it is the full 15 seconds - and then answers NO_PLAN_FOUND, whose text says "may still be
possible". The answer is never *wrong* (NO_PLAN_FOUND claims less than it could), and the budget
now caps the cost at 15 seconds where it used to be minutes - the round made this strictly better.
What remains is that a cheap pairwise scan of homes over `pointsBySensor` at triage time would turn
"maybe, after 15 seconds" into IMPOSSIBLE-with-names immediately, the same upgrade this round's
`canRest`/`connected` pre-check gave single unreachable homes.

### HP-C5 - the exclusion guard writes through a Layout-synchronized method on the EDT

**Where:** [HomeLocomotiveMenu.java](../../src/org/traincontrol/gui/HomeLocomotiveMenu.java)
(`confirmExclusion` → `setHomeLocomotive`, which is `synchronized` on the Layout), called from the
right-click editor and the Ctrl+E keystroke, both on the EDT.

If the operator confirms while autonomy is configuring a path, the EDT waits for the Layout monitor,
which `configureAndLockPath` holds through `CONFIGURE_SLEEP` per accessory command - a ten-command
path is a 1.5-second freeze. This is `TCR-C1`'s shape (EDT blocking on the Layout monitor) at a new
entrance, and `HomeLocomotiveMenu.apply` already had it; the new call just adds a second door.
Mitigating: exclusions are usually edited with the layout at rest, and the wait is bounded by one
path's configuration. Recorded so the eventual TCR-C1-style fix (bounce the write off the EDT)
knows all its entrances - the count of which is exactly the lesson of the cycle's signature error.

### HP-C6 - fix residue, three locations, one finding

The deleted `restingTrainsHoldSensors` field left its javadoc behind, and javadoc attaches forward:
the `HomeStaging` constructor is now documented as "Whether this layout's hardware holds a sensor
under a resting train" ([HomeStaging.java:87](../../src/org/traincontrol/automation/HomeStaging.java)),
plus a stray blank line where the measurement was. In `testReturnHomeOnRealLayout`, `describe`'s
javadoc now sits stranded above `describeLocation`'s own, leaving `describe` undocumented and a
dangling comment describing the wrong method. And `logPathError` gained a double blank line at the
early return ([Layout.java:1221](../../src/org/traincontrol/automation/Layout.java)). All cosmetic,
all the same class as `SWC-C9`/`PV-C5`: the record of the change disagreeing with the change.

---

## D - clean checks

**D1 - the IMPOSSIBLE proof is still sound after the spec change.** IMPOSSIBLE is claimed only from
per-locomotive facts no rearrangement can alter: `canRest` at the home and `connected`, which
ignores occupancy on purpose and honours the terminus-not-through rule. It under-claims (exclusions
and inactive points on the only route yield NO_PLAN_FOUND, not IMPOSSIBLE - the conservative
direction), and nothing in the new rules feeds it, so the structural sibling rule cannot produce a
false IMPOSSIBLE. The new terminus test pins the one IMPOSSIBLE this geometry can prove, and pins
its own control case first.

**D2 - the search-budget change.** Deadline computed once, checked per expansion beside the halved
state limit; both exits fall to the same `null` and the same NO_PLAN_FOUND, whose "may still be
possible" wording is precisely the honest claim for a cut-short answer. The stale-entry guard
(`polled.score != score.get(currentKey)`) and closed-set semantics are unchanged.

**D3 - the logging change's caller census.** `isPathClear` has exactly four callers: the validator
(`configureAndLockPath`, still logging) and three enumerators (`pickPath`, `debugPath`,
`getPossiblePaths`, now silent), with `lastError` still written for `debugPath`'s per-destination
reasons. No enumerator logs, no validator was silenced. The magnitude claim in the comment (143,353
lines, 36MB) is the author's measurement and is consistent with `auditAgainstRuntime`'s shape -
every path for every locomotive - which is also why the audit became bearable to leave in debug
builds.

**D4 - `blockedSensors`' rewrite drops nothing it needed.** The old `stillHeld` term (block while
any reporting point is occupied in the hypothetical state) is subsumed: the occupied point itself is
blocked by state occupancy, and the active sibling by the structural rule. The one case the old
term also covered and the new rule does not is HP-C1's inactive sibling. Unexplained sensors block
forever, exactly as before, and `pointsBySensor.get` cannot NPE - `sensorsSet` is populated only
alongside it.

**D5 - `homeBrokenByExcluding` and its wiring.** Null-safe on all three inputs, compares by name
(homes are stored by name - the by-name/by-reference distinction that bit this codebase before is
respected), and both UI entrances route through `confirmExclusion`; the keystroke path checks
`getActiveLoc() != null` before building the singleton list. Answering No discards the exclusion
edit, answering Yes clears the home before applying it - ordered so the two states cannot disagree.
The state remains reachable in the other order (exclude, then assign), which the chooser warns
about; the guard's own javadoc says so.

**D6 - the I18n conversion** (detailed under the commit above): edge cases traced, both premises
pinned by tests that scan the real bundles rather than samples.

**D7 - the Point invariant** behind the dot-width choice: enforced at both setters, pinned in both
orders with a control case.

**D8 - `optBoolean` and its twin.** Both `getBoolean("station")` sites changed in the same commit -
the point creation and the placed-on-non-station warning - so the fix does not repeat the
one-of-two-entrances error, and `testAPointWithoutTheStationKeyIsANonStation` pins the load path
end to end.

**D9 - test quality.** The new suites assert their preconditions (shared addresses proven equal,
terminus flags proven set, fixtures asserting their splices landed), state their controls
explicitly, and restore what they mutate (`setReversible` returns the previous state rather than
assuming it). The shared-sensor conflict test resolves fast only because its four-point graph
exhausts A*'s queue - the same assertion on a large layout would sit on the 15-second budget, which
is HP-C4 seen from the test bench.

**D10 - the real-layout test's diagnostics.** The settle-timeout failure now names each stuck
locomotive, its path and its position - the right response to a randomized test whose failures
cannot be re-run - and `getActiveLocomotives` provides what it reads.

---

## Comparison with the existing record

This round is the successor to the `HS` document, and the relationship is direct:

- **`HS-B4` is superseded, not just fixed differently.** Its fix measured whether hardware holds
  sensors and blocked arrivals conditionally; its writeup recorded a mixed-hardware limitation. The
  structural rule deletes the measurement and the limitation together, and covers arrivals
  unconditionally. The cost - pessimism on pulsed hardware - is the direction `HS-B4`'s own caveat
  identified as safe. HP-C1 is the one flag-width gap the new rule leaves against the old fix's
  coverage.
- **`HS-D1`'s parity table was re-read row by row** at the new code: exclusions unchanged (and now
  tested), terminus unchanged (and now tested in four directions), lock edges and conflicts
  unchanged, sensors-at-rest rewritten as above. The inactive row survives for entered points;
  HP-C2 is its origin-shaped corner, which no prior pass had asked about.
- **The audit** the round leans on was built in the `HS` era; HP-C3 is the first look at what its
  oracle actually offers, and finds `pickPath`'s missing twin filter in `getPossiblePaths` - the
  cycle summary's signature error, standing since before the audit was written.
- **`TCR-C1`** named the EDT-on-Layout-monitor shape; HP-C5 is a new entrance to it, created by an
  otherwise-correct guard.

**New errors introduced by these commits:** HP-C6's three cosmetic residues are the only defects the
round itself created. HP-C1 is half-new - the structural rule is new, and its inactive-sibling skip
is narrower than the coverage the `HS-B4` fix it replaces had on holding hardware - and everything
else predates the round. No functional regression was found in any of the three commits; the I18n
change, the dot-width change, the budget, the logging split, and the `optBoolean` fix all verify
clean at their enforcing methods.

**Testing gaps, named so they are not mistaken for coverage:** nothing exercises HP-C1 or HP-C2 (a
fixture with a train on an inactive point is buildable with the existing helpers); the audit has no
test at all, so HP-C3's phantom class would survive a fix unpinned; and no test asserts how long a
provably-conflicted goal state takes to answer (HP-C4), which is the difference between a test bench
observation and an operator staring at a frozen button for fifteen seconds.
