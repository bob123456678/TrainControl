# Autonomy algorithm and routing review — v3.0.0, the 2026-09-02 fan-out

**Status:** open
**Prefix:** `RT3` — cite findings from this document as RT3-B1, RT3-D1, and so on.

**Reviewed:** branch `autonomy-diagram-r0` at `cf048f9b`, 2026-09-02, **by reading only** — no test,
build, or JVM was run by this pass. Line numbers are from the working tree at that commit. The working
tree additionally carries uncommitted modifications to `cs2_sample_layout/config/` (see RT3-D14);
nothing in this pass read from or wrote to that folder — the frozen copy under `test/operator_layout/`
was used where real data was needed.

**Scope, per assignment:** the autonomy algorithm and the routing changes — `automation/Layout.java`
(isPathClear, pickPath, hasAutonomousDestination, barredFromAutonomy, getPossiblePaths, executePath,
runLocomotives, the timetable executor, the reversal-room guard, `measuredRoomToReverseInto`,
`protectsAnOccupiedSquare`, claimHome/rebuildHomeStations), `automation/HomeStaging.java` in full,
`Point.java` and `Edge.java` in full, `automationui/AutonomyBuilder.java` and
`automationui/GraphReducer.java` in full, `automationui/AutonomyChecks.java` and the graph-facing parts
of `automationui/AutonomySession.java` (the copy checks, `badCopies`, the badge gate expression,
`hasErrors`/`errorCount`). The five commits of 2026-09-02 (`409d4ce8` … `cf048f9b`) were read as
diffs against `828b1ff1`, the version the previous pass over this scope (`RTG`) reviewed, so
everything new since that review was read in full. What was *not* read is listed at the end of the
D section.

**Method note.** This pass deliberately re-asked the assignment's standing questions of the freshest
code: does the planner still agree with the runtime after `975f157d`; is the proof (`connected`) still
looser than the search (`firstClearRoute`); has a tier rule leaked into an execution refusal; do the
new per-copy checks double-report the square-level ones. Two of the three findings below are defects
*in or beside* fixes that landed hours before this review, which is where the briefing said to look.

---

## Summary

| Finding | Severity | One line | Status |
|---|---|---|---|
| RT3-B1 | B | The reversal-room `continue` in `firstClearRoute` (975f157d) is defeated by the search's own dominance pruning: the "longer approach with more room" its comment promises is pruned in the common geometry, so the planner refuses berths the runtime accepts | open |
| RT3-B2 | B | `auditAgainstRuntime` has no exemption for the planner's non-station-origin rule — a sixth correct divergence with no exemption, exactly the shape of the still-open fifth (`TV2-B1`) | open |
| RT3-C1 | C | `connected` carries a pre-stop reversal credit *through* a terminus stop, an additional way the proof is looser than the search beyond the seed already filed as `TV2-B2` | open |
| RT3-D1–D15 | D | Withdrawn finding (one), fix verifications, clean checks, and what was not looked at | — |

No A findings. Nothing this pass found produces a wrong movement on the layout or loses data; both
B findings are refusals or false diagnostics in specific configurations.

---

## B findings

### RT3-B1 — the reversal-room rule's `continue` is defeated by the route search's own dominance pruning

| | |
|---|---|
| **Status** | open — defect in the `975f157d` fix for `TCX-A2`; planner-stricter-than-runtime shape (refusals only, never a wrong movement) |

`975f157d` added the reversal-room rule to `firstClearRoute`, with the refusal written as a `continue`
on the stated theory that the search will find a roomier alternative
(`HomeStaging.java:1043-1049`):

```java
                    // `continue` rather than a refusal: another route to the same berth may be longer,
                    // and a longer approach is more room.
                    Integer room = Layout.measuredRoomToReverseInto(route, loc);

                    if (room != null && loc.getTrainLength() > room) continue;
```

The search cannot generally find that alternative, because its visited-set bookkeeping runs **before**
the destination test and dismisses states by command-dominance (`HomeStaging.java:1017-1022`):

```java
                String key = next.getUniqueId() + (turned ? "/turned" : "/straight");

                if (alreadyReached(seen, key, commands)) continue;

                if (!seen.containsKey(key)) seen.put(key, new ArrayList<>());
                seen.get(key).add(commands);
```

`alreadyReached` (`:1140-1162`) dismisses a candidate when any earlier arrival at the same
(point, turned) state committed only settings the candidate has also committed. That pruning was sound
for as long as acceptance at the destination depended only on things inside the key — the point, the
turned flag, and the commands. The July pass said so in exactly those terms and was right
(`2026-07-27-home-staging-review.md:518-520`: "the route search's dominance pruning (`alreadyReached`)
keeps it sound when the same point is reachable under different settings"). Before `975f157d` it was
also true *at the destination specifically*: a `to/turned` entry could only exist if the search had
already **returned** (a turned arrival always returned, `:1051`), and a `to/straight` entry could only
suppress other straight arrivals that would have been refused for the same `mustBackIn` reason. The
room rule is the first refusal of an otherwise-accepting arrival that leaves its `seen` entry behind —
and room depends on the path's accumulated measured length, which is in neither the key nor the
dominance relation. The precondition that made the pruning sound was removed by the fix, silently.

**Two limbs, and the second is the common geometry:**

1. **The poisoned arrival.** BFS delivers the fewest-edge arrival at `to` first — typically the short
   direct approach, typically the one without room. It is recorded in `seen` at `:1022`, *then*
   refused for room at `:1049`. Every later arrival at `to` in the same turned-state whose committed
   commands are a superset of the first's — which is any longer route through the same final switch —
   is dismissed at `:1019` before the room check can ever see it. This includes a longer route with an
   **unmeasured** leg, for which `measuredRoomToReverseInto` answers null and both sides deliberately
   accept: the exact "routed the long way round" behaviour the commit's own fixture note describes
   (`test/core/testHomeStaging.java`, `shortBerth()`: "the planner simply routed the long way round
   instead … whose middle leg had no length").

2. **The shared corridor.** Alternate approaches to one berth almost always rejoin at the final
   junction before it. At that junction X, the short route's partial (fewer commitments) is recorded
   first and dominates the longer route's partial (same commitments plus the detour's), so the longer
   route is pruned at X — it never even reaches the arrival test. Fixing limb 1 alone (testing the
   arrival before the bookkeeping, as `connected` does — `:1766-1768`, "Arriving is checked before the
   visited set") does not reach this limb; only room-aware search state does, or the runtime's own
   device for exactly this problem — `pickPath`/`getPossiblePaths` re-run `bfs` excluding each refused
   path (`Layout.java:3788-3827`, `:4368-4405`) until an acceptable one is found or none is left.

**The runtime is not similarly limited.** `getPossiblePaths` enumerates every bfs alternative and asks
`isPathClear` per path (`Layout.java:4368-4405`), and `isPathClear` asks the identical
`measuredRoomToReverseInto` (`:2398-2412`) — so a longer approach that fits, or an unmeasured one the
rule declines to judge, is offered by the runtime and unfindable by the planner. That is the
planner-is-the-stricter-half shape this file's history treats as a defect every time
(`D24-B1`, `SV2-A1`, the lock-edges history at `HomeStaging.java:988-1005`), whose symptom is
`NO_PLAN_FOUND` after the full 15-second budget, or a berth silently unusable to Return Home.

**What partially compensates, and when it does not.** `astar` can sometimes route around a poisoned
single-move search by splitting the journey at an intermediate station — each move is a fresh
`firstClearRoute` with a fresh `seen`, which is how the commit's intermediate fixture found its long
way round. That works only when the longer approach passes a free, restable station. The typical
alternate — a loop of plain track between the same two stations — has none, and the greedy pass
(`search()`, `:735-773`) never decomposes at all. `auditAgainstRuntime` compares single
`firstClearRoute` calls against `getPossiblePaths`, so it reports the divergence regardless (correctly,
this time) — but only in debug mode (`Layout.java:6577-6580`).

**Reachability.** Needs a locomotive with a train length, a fully-measured short approach to a
terminus/reversing destination that is too short for it, and an alternative approach whose committed
settings include the short one's. On the operator's railway today the guard is live at two berths with
room 4 and room 2 (`FX2-3`, confirmed against `test/operator_layout/config/`), and 42 of 54
locomotives carry a length greater than 2 — but the one locomotive *homed* at such a berth (EN57-947)
has no length set, so Return Home does not currently hit it there. It arms the moment a
measured-length locomotive is homed at a measured berth, which is what the editor's
`REVERSAL_NEEDS_LENGTH` notice is actively asking him to set up. Also note the shuffle in
`getNeighbors` (`Layout.java:2052`) means which commands the first arrival carries varies per call, so
the refusal can be intermittent run-to-run — the worst kind to chase from a log.

**Fix shape (not applied, per the briefing):** at minimum, test the `next.equals(to)` arrival —
including the room rule — *before* the `alreadyReached` bookkeeping, and do not record an arrival
refused for room (recording is only dedup; `to` is never expanded, `:1058`). That cures limb 1 and is
one move of code. Limb 2 needs the accumulated measured length in the search state (Pareto rather
than dominance), or a refused-prefix retry like the runtime's `seenPaths` loop; which of those is
worth the cost is a decision for the fixer, and the residual should be recorded at the `continue` if
only limb 1 is taken. The new test
(`testTheAuditSeesTheReversalRoomRuleTheStagingPlannerDoesNotHave`) pins only the every-route-refused
case — its ring measures every edge precisely so that no alternate exists — so a fix for this finding
needs its own fixture with two approaches, seen failing first.

### RT3-B2 — the staging audit has no exemption for the planner's non-station-origin rule

| | |
|---|---|
| **Status** | open — same shape as the still-open `TV2-B1`/`SVN-C5` (the fifth divergence); this is a sixth, unfiled until now |

`SG-A2` (`fd31d2b2`, 2026-08-30) taught `firstClearRoute` the second half of the runtime's origin
rule (`HomeStaging.java:928`, `:940`):

```java
        if (!from.isActive()) return null;
        ...
        if (!from.isDestination()) return null;
```

Both refusals are correct — staging executes with autonomy running, and `isPathClear` refuses a first
edge starting on a non-station in auto running (`Layout.java:2233`, "Starting point is not a station -
do not pick it in fully autonomous mode"). `RTG-D1` verified the pair as faithful copies and this pass
agrees.

But `auditAgainstRuntime` compares the planner against `getPossiblePaths` **at rest**, where that
runtime rule is fenced off (`:2233` is behind `isAutoRunning()`), and its origin-side skip covers only
half of the pair (`HomeStaging.java:607-616`):

```java
            // A locomotive standing on a deactivated point is the third correct divergence ...
            if (!e.getKey().isActive()) continue;
```

There is no matching skip for `!e.getKey().isDestination()`. `getPossiblePaths` has no
start-is-a-station filter of its own (`Layout.java:4357-4364` — the start is any point holding the
locomotive; the filters at `:4364` are all about the *end*), so for a locomotive standing on a plain
sensor at rest it returns clear paths to every free station. The planner returns null for all of them.
Every active, non-excluded, non-held-back station in that list then increments `disagreements` and
logs `autolayout.warnStagingPlannerTooStrict` (`:677-681`) — a false accusation, per destination, from
the one instrument that exists to find real mis-copies.

**Reachability.** Debug mode on, Return Home planned (`Layout.java:6577-6580`), and a locomotive
standing on a non-station Point — which is precisely where a hand-placed train sits; `SG-A2`'s own
war-story comment at `:936-939` says so in as many words. The inactive-origin divergence got its skip
on 2026-07-31 (`093b5c13`); the commit that created this one (`fd31d2b2`) extended the search and not
the instrument, with the exemption it needed sitting one line above the clause it copied.

**Severity.** B on the same reasoning `TV2-B1` recorded for the fifth divergence: debug-only channel,
but "a false accusation from the one instrument that exists to find real divergence, in a channel only
read when something else is already being chased" (`DR-B1`'s own test javadoc). Remedy is one line
beside the existing skip — `if (!e.getKey().isDestination()) continue;` — and it should land together
with `TV2-B1`'s `if (mustBackIn(loc, p)) continue;`, since the fix for either will be validated by the
same kind of fixture. Not merged into `TV2-B1` because that finding's trace, remedy and reachability
argument are specifically about the terminus rule and this one's are not.

---

## C findings

### RT3-C1 — `connected` carries a pre-stop reversal credit through a terminus stop

| | |
|---|---|
| **Status** | open — looser-proof direction only (never a false IMPOSSIBLE); overlaps `TV2-B2` (open) but is a distinct limb |

When `connected` expands a terminus that is an in-service destination — the two-move "stop there and
set off again" case added on 2026-09-01 — it queues the onward state with `now` carried unchanged
(`HomeStaging.java:1774-1798`):

```java
                // The train leaves such a stop facing OUT, so `now` is carried unchanged rather than
                // set - the arrival flip at a terminus is spent turning it round to leave (SV2-A1).
                if (!next.isTerminus() || (next.isDestination() && next.isActive()))
                {
                    queue.add(next);
                    turned.add(now);
                }
```

The comment defends not *setting* `now` there, and that half is right. But carrying `now == true`
through is its own overstatement: a train that turned at a reversing point and then backed into the
terminus has spent that reversal — the arrival flip turns it to face out, and it leaves the stop
forwards, exactly as the comment's first sentence says. The state after any terminus stop is
not-turned. The search agrees: each staging move out of a terminus seeds `turned = false`
(`:969-970`, the `SV2-A1` revert). So for a route origin → R (reversing) → T1 (terminus stop) → H
(terminus home, `mustReverse`), the proof answers connected while the search can never produce the
plan — nose-first into H is precisely what `SV2-A1` established must be refused.

The consequence is only ever in the safe direction — a provable IMPOSSIBLE is reported as
`NO_PLAN_FOUND` after the search burns its full budget, with no locomotive named — which is the same
cost `TV2-B2` records for the *seed* limb (`startsTurned = from.isReversing() || from.isTerminus()`,
`:1747`), still open. Filed as its own C because fixing the seed alone (or the `AutomationAPI.md`
sentence alone) leaves this limb behind: resetting `now` to false when queueing through a terminus
stop would make the proof exact on this dimension, and it cannot make it tighter than the search —
the search never travels through a terminus at all (`:1058`). If instead Adam prefers the proof
deliberately loose here, the comment should say the carry-through is part of that choice; today it
reads as an oversight beside a sentence that argues for the opposite.

---

## D — withdrawn, checked and clean, and things that look wrong but are not

### RT3-D1 — WITHDRAWN: "a no-way-out copy hard-blocks starting autonomy" (drafted at B)

Drafted from reading `409d4ce8`'s diff, where `checkBadCopies` emitted `COPY_NO_WAY_OUT` at
`Severity.ERROR` — whose enum doc says "Autonomy cannot be built at all until this is fixed"
(`AutonomyChecks.java:37-40`), which is false of a trapped copy — while the Start door refuses
outright on `errorCount() > 0` with no way past (the OB-057 gate, now asked through `hasErrors()`
since `87b6c10a`; `TrainControlUI.java:5183-5191`). Combined, any diagram with a trapped arrival
copy could never start autonomy, including one whose trap sits on an out-of-service or manual-only
berth.

**Wrong, and withdrawn before filing:** the current file already carries the downgrade, with a comment
naming the exact interaction (`AutonomyChecks.java:763-771` — "A WARNING, all three, and the reason is
what an error DOES: errorCount() > 0 refuses to start autonomy at all… a trapped arrival on a square
he has been running for months would have stopped his railway starting"). The combination was real for
the ninety minutes between `409d4ce8` and `06516f38` on the night of 2026-09-02, and the author found
and fixed it himself in `06516f38` — before this review, and before `87b6c10a` widened the gate. The
mistake was reading a commit's diff as the current state instead of opening the file — recorded here
because that is the calibration this section exists for. The remaining ERROR-severity findings
(blocking graph problems, `UNNAMED_STATION` by Adam's explicit ask, `DUPLICATE_SENSOR_PAGE`,
`DUPLICATE_LOCOMOTIVE`, `NO_STATIONS`) were each checked against the gate: all are genuine
cannot-run states with a remedy in the editor. The gate is not over-strict.

### RT3-D2 — the fifth audit divergence (`TV2-B1`/`SVN-C5`) verified still present and still open

At `cf048f9b`, `auditAgainstRuntime` still has no exemption for `mustBackIn`: `isPathClear` has no
terminus rule (`Layout.java:2312-2329`), `firstClearRoute` refuses an unturned arrival
(`HomeStaging.java:1051`), and none of the four exemptions catches it. Not re-filed; `TV2-B1` already
carries the full trace, reachability on all twelve berths, and the one-line remedy. RT3-B2 above is
its sibling, not its duplicate.

### RT3-D3 — `975f157d`'s own claims verified

The counting is byte-equivalent to the loop it replaced (old: `measured=false; break` on a
non-positive segment, refuse on `measured && trainLength > room`; new: `return null` on a non-positive
segment, refuse on `!= null && trainLength > room`); both unsoundnesses stay recorded at the call site
(`Layout.java:2369-2397`); the rule went into the search and deliberately not into `connected`
(verified: no reference in `connected`, `:1712-1803`); both sides ask one static pure function on the
same path object, so on any path both *consider*, they agree (RT3-B1 is about paths the planner never
considers). The null-guards cannot NPE: `room != null` implies `getTrainLength() != null` by the
function's first clause.

### RT3-D4 — the proof-looser invariant, checked dimension by dimension

Every refusal in `firstClearRoute` was compared against `connected`: occupancy (absent from the proof
— looser, by doctrine), blocked sensors and sensor-siblings (absent — looser), command conflicts
(absent — looser), room (absent — looser, `975f157d`'s explicit choice), origin rules (equivalent —
`plan()` tests `isActive`/`isDestination` before consulting the proof, `:443-445`), the seen-set
(point+turned only vs point+turned+commands — looser), terminus expansion (stop-and-go for in-service
destination termini vs never — looser), reversal seed (`isReversing || isTerminus` vs `isReversing` —
looser), arrival test (the proof checks arrival before its visited set, `:1768`, so no arrival can be
shadowed; the search checks arrival *after* its bookkeeping, which is RT3-B1's mechanism and makes the
search stricter than itself, not the proof tighter). **No dimension is tighter.** The invariant that
`D24-B1` and `SV2-A1` were both about holds at `cf048f9b`; RT3-C1 is a looseness beyond what the
comments record, not a tightness.

### RT3-D5 — `8d1c17ca` (two homes on one square at the loader) verified

The new square rule in `rebuildHomeStations` (`Layout.java:1155-1169`) mirrors `claimHome`'s
(`:1088-1091`) via the same `isSamePlaceAs`; the loser is dropped with its own warning and
`setHomeLoc(null)`, so it is not re-warned on every load and not written back out; which assignment
wins follows the points map's iteration order, exactly as the adjacent same-locomotive rule always
has; assignments still run before positional claims, and `claimHome` still refuses a square an
assignment took. No interaction defect found.

### RT3-D6 — `1cfdf370`'s builder `active` fix (D24-B5) verified

The extras loop now carries `active` on every copy of every square (`AutonomyBuilder.java:936-953`);
nothing pre-puts an `active` key into the emitted JSON so the `json.has(key)` guard cannot swallow it;
the consumer is `isPathClear`'s unfenced intermediate rule (`Layout.java:2268-2287`), which is what
the editor's cross promises. The badge-gate half (`shut` term in `worthABadge`,
`AutonomySession.java:4703-4711`) matches the fix's description; the drawing itself was not reviewed
(display, other reviewers' scope).

### RT3-D7 — `87b6c10a`'s `protectsAnOccupiedSquare` verified equivalent, and sound on builder graphs

The lifted rule (`Layout.java:6134-6155`) resolves names exactly as the deleted
`MarklinRoute.isOneOf` did (literal first, then `getAccessoryByName`), and gates on the same
per-point `getCurrentLocomotive()`. Sound on every builder-emitted graph because protecting signals
are emitted **on every copy** of a square (`AutonomyBuilder.java:873-897`) — the occupied copy always
carries the list — and `reserve()` sets the occupant too, so a claimed platform counts. On a
hand-written pair of blockless Points that are one physical platform, a train on the un-listed twin
is invisible to the guard — but that was equally true of the code it replaced, the model has no way
to know two blockless Points are one platform, and no shipped configuration has the shape (the
protecting-signal feature postdates the hand-written era). Not a finding. The new confirmation door
in `LayoutLabel` offers OK/cancel — a guard with a way past, per the standing rule.

### RT3-D8 — the selection tier's three mirrors re-verified in step

`pickPath`'s filter (`Layout.java:3781-3784`), `hasAutonomousDestination` (`:3524-3527`) and
`barredFromAutonomy` (`:4033-4058`) agree clause-for-clause today — active, not-reversing,
autoDestination, terminus-vs-reversible, exclusions, plus `reversesAlongTheWay` in the two that see
paths. `RTG-D5` re-confirmed at current line numbers. The fourth mirror (`SVN-C4`'s " -" suffix
predicate) remains as filed there; not re-filed.

### RT3-D9 — the copy checks close `RTG-A1`'s editor blindness at the right layer, without double-reporting

`RTG-A1`'s trap — a turning station copy emitted as a terminus destination with zero outgoing edges —
is now caught by `COPY_NO_WAY_OUT` reading the graph **as built** (`AutonomySession.java:1993-2062`),
which is the layer the square-level checks cannot see; the healthy-sibling gate (`:2046-2056`) is
what keeps a wholly-stuck square from being reported twice in different words, answering the
assignment's double-reporting question in the code's own text. `COPY_REACHES_NOTHING` walks through
same-square twins without counting them as success (`:2090-2185`), which is right. `pickPath` still
has no has-a-way-out clause — that is `FX2-4`'s closure (Adam fixed the layout, the editor warns),
a ruling, not a defect; noted so nobody re-derives it as a gap.

### RT3-D10 — the unfenced room rule is not a tier-doctrine leak

`isPathClear`'s room guard (`Layout.java:2362-2414`) refuses at execution for every tier, including a
hand dispatch. Checked against the doctrine and the precedents: it stands on the same footing as
`validateTrainLength` (`:2290`, also unfenced) — a physical-safety property of the train and the
track, not a preference about what autonomy chooses — and it is the behaviour Adam's quoted ruling
asked for ("such paths need to be disallowed"). `FX2-3` closed the counting questions accepted-as-is.
Clean.

### RT3-D11 — `trainLength` nullability chased to ground

`Locomotive.trainLength` is initialised to 0 in every constructor and no production caller passes
null to `setTrainLength` (`Layout.java:7563,7580`, `GraphLocAssign.java:215-218`,
`TrainControlUI.java:23113`), so `Point.validateTrainLength`'s unboxing (`Point.java:915`) cannot NPE
from a production door and the null-guards in `measuredRoomToReverseInto` are defensive. "EN57-947
has no train length set" means zero, not null. Clean.

### RT3-D12 — `RTG-C4`'s stale javadocs still stand, at new line numbers

`AutonomyBuilder.java:581-587` and `AutonomyChecks.java:785-787` still assert "HomeStaging.canRest
refuses a terminus to a locomotive that cannot reverse", a rule deleted from `canRest` on 2026-08-31
(`HomeStaging.java:1659-1677` says so itself). `RTG-C4` remains open and correct; not re-filed.

### RT3-D13 — `87b6c10a`'s already-running funnel guard checked for gaps

The check-then-mark pair (`TrainControlUI.java:16115-16126`) runs on the EDT for every mouse door, so
it is serialized where it matters; `routesExecuting` is populated by `routeStarted` before the thread
starts and cleared in the timed wrapper's exits. The route-trigger/autonomy door does not pass
through `executeRoute` — that boundary is the routes-vs-autonomy reviewers' ground and was not
re-derived here.

### RT3-D14 — the working tree again carries uncommitted edits to the live railway's configuration

`git status` shows `cs2_sample_layout/config/autonomy/configuration-Main.json`, `setup.json` and
`gleisbilder/1 - Main.cs2` modified. Unlike `RTG-A2`, the provenance is this time documented in the
day's own commits: `409d4ce8` and `469f69d6` describe Adam editing his diagram at 00:01–00:11 on
2026-09-02 (the LowerFront fix, MT-251–253) and `469f69d6` updated the frozen copy under
`test/operator_layout/` to match. Consistent with his own session; nothing here reads as test-JVM
damage. Not diffed further and not touched, per the hard rule. Flagged only so the observation is on
the record beside the lean.

### RT3-D15 — what this pass did not look at

`TileGraph.java` was read only at the interfaces the reducer and builder consume (`exits`, `landing`,
`getRoutes`, `Problem`) — no claim of coverage there. `AutonomyCompanionStore` likewise (store
accessors only). Not reviewed at all: `AutonomySession`'s UI surfaces beyond the gate expression and
the copy checks (badge painting, Clear All Home Locomotives wiring, the `SVN-A3` teardown ordering),
`AutonomyEditorPanel`, `AutonomyViewerPanel`, `GraphEdgeEdit`, timetable capture, the message
bundles, and every test file except the one `975f157d` added (read as evidence for RT3-B1, not
reviewed as a test). `Layout.java` regions not named in the scope list (CS2 sync, parseAuto's field
handling beyond homes/protecting signals, the simulation plumbing) were read only where a finding's
trace passed through them.

---

## For the orchestrator

1. RT3-B1 and RT3-B2 each need a failing test first; RT3-B2's fixture (a locomotive on a plain
   sensor, audit expected 0 after the exemption) is also the natural place to land `TV2-B1`'s
   still-missing exemption, and the two skips should go in together with `TV2-B1`'s remedy line.
2. RT3-B1's fix must not be validated with the shipped `shortBerth()` ring — that fixture measures
   every edge precisely so no alternate exists, and it will pass with or without the defect. A
   two-approach fixture (short measured approach, longer acceptable one sharing the final switch) is
   the discriminating shape, and the prediction to test first is that `firstClearRoute` returns null
   on it today while `getPossiblePaths` offers the longer path.
