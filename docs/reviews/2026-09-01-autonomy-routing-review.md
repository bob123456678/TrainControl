# Autonomy algorithm and routing review — v3.0.0 pre-release

**Status:** open
**Prefix:** `RTG` — cite findings from this document as RTG-A1, RTG-B1, and so on.

Reviewed: branch `autonomy-diagram-r0` at `828b1ff1` ("The cross takes the colour and the weight it
should"), which is one commit past the `e9435bfc` the briefing named — the uncommitted edits to
`TileAnnotation`, `LocomotivePlaceholder` and `testAutonomyDiagramMonitor` it listed were committed as
`828b1ff1` before this pass started. The working tree additionally carries uncommitted modifications to
`cs2_sample_layout/config/autonomy/setup.json` and `configuration-Main.json` (see RTG-A2). Reviewed
2026-09-01, **by reading only** — no test, build, or JVM was run by this pass, before or after the
orchestrator's mid-round stop order.

Scope, per assignment: `automation/Layout.java` (isPathClear, pickPath, hasAutonomousDestination,
barredFromAutonomy, executePath, runLocomotives, the length/reversal guard, claimHome,
setHomeLocomotive, the loader), `automation/HomeStaging.java` (plan, astar, firstClearRoute, connected,
canRest*, canGetHome, mustBackIn, triage, auditAgainstRuntime), `Point.java`, `Edge.java`, and the
`automationui` classes as they affect routing. Line numbers are from the working tree as reviewed.

---

## The known open failure, diagnosed

`test/core/testTheParkingBerthsGetTheirTrainsBack` (deliberately excluded from the battery;
`test/regression/testEveryTestIsInTheBattery.java:44-49`) fails with outcome **IMPOSSIBLE**. The
assignment asked precisely which function is responsible. The answer is that **two independent
stranding mechanisms each manufacture an arrangement for which IMPOSSIBLE is the planner's *correct*
answer** — the backing-in machinery the failure message blames is not what fires, and
`HomeStaging.plan()` is the messenger, not the culprit.

The function that *declares* impossibility is the origin-flags clause of `plan()`'s unreachable scan,
`HomeStaging.java:443-445`:

```java
if (!locationOf(this.start, l).isActive()
    || !locationOf(this.start, l).isDestination()
    || !canGetHome(l, locationOf(this.start, l), home)) unreachable.add(l);
```

Any homed locomotive standing on an inactive point, or on a non-station point, is proved stuck by the
first two clauses **before the backing-in question (`canGetHome`/`connected`/`mustBackIn`) is ever
asked**. Both stranding mechanisms below land a train exactly there, and both proofs are sound:
staging executes with `running` set, and `Layout.isPathClear:2192` ("Inactive points not allowed in
auto running") refuses any edge whose start is inactive — mirrored by
`HomeStaging.firstClearRoute:928` (`if (!from.isActive()) return null;`) and `:940`
(`!from.isDestination()`). A train standing on such a point genuinely cannot be given a staging move.

**Mechanism 1 — the graph trap (the app defect; RTG-A1).** Already measured by whoever wrote the
exclusion entry: *"BottomMainB (eastbound, reverse) is a destination with ZERO outgoing edges on the
operator's derived graph, so a train that reverses there can never leave"*
(`testEveryTestIsInTheBattery.java:45-47`), with Adam's ruling quoted: *"it should be easily possible
to get back."* I verified the mechanism in the builder — see RTG-A1 for the function chain. Full
autonomy will choose that trap for a reversible locomotive (the test's "PB test loc 3"), and the
hand-dispatch door can put any train there.

**Mechanism 2 — the fixture's blind hand-dispatch (RTG-B1).** The currently observed failure
message begins "no way home from PB test loc 0 at **ParkingTrack6**". ParkingTrack6
(`2 - Bottom:17,6`) is **`active: false`** in the active configuration
(`cs2_sample_layout/config/autonomy/configuration-Main.json`, entry `"2 - Bottom:17,6"` —
`parking, mustReverse, active: false`; so are all nine Bottom-page ParkingTracks). The test's
hand-start (`testTheParkingBerthsGetTheirTrainsBack.java:181-197`) executes
`layout.getPossiblePaths(parked, true).get(0)` blindly. `getPossiblePaths` (`Layout.java:4314`)
filters on occupancy and station-ness only — **not on `isActive`** — and at rest `isPathClear`'s
inactive-endpoint rules (`:2192`, `:2269`) are fenced behind `isAutoRunning()`, which is false during
the hand-start. That is deliberate manual-tier doctrine (`Layout.java:2236-2242`: a manual route "may
still FINISH on one, which is how a route to a parked-up berth is picked"). `executePath` is
synchronous, so loc 0 was standing on the out-of-service ParkingTrack6 before `runLocomotives()` even
ran; `runLocomotives` then skips it ("avoid starting inactive locomotives", `Layout.java:1671-1675`),
nothing ever moves it again, and `plan()` proves the truth.

**What it is NOT.** It is not `mustBackIn`, `connected`'s two-state reversal search, or
`firstClearRoute`'s reversal sequencing — those are correct by reading (and the test's own experiment,
"disabling mustBackIn gives the same answer", agrees). It is also not a false proof: I checked the
IMPOSSIBLE against the layer that enforces it (`isPathClear:2192` under staging's `running = true`)
and the planner and runtime agree.

**What a fix needs, in two places.** (1) The graph defect of RTG-A1, which Adam has already ruled on.
(2) The fixture must stop dispatching to the first path regardless of destination — filter the options
to `end.isActive()` (ideally `isChoosableByAutonomy`), or the test stays red for the ParkingTrack6
reason even after the builder is fixed. Note the assert's static text ("…so each of them has to back
in past a reversing point") mis-attributes the failure; the test's javadoc eliminations date from the
earlier NO_PLAN_FOUND shape of this failure and describe a different arrangement of the same fixture.

---

## A findings

### RTG-A1 — Full autonomy can strand a train on a turning destination with no way out

**CLOSED by `FX2-4`.** Adam fixed the layout - "it should now be possible to leave in both directions" - and then converted the four parking berths to `autoDestination: false`. The editor also warns about the shape now (2026-09-02): a station copy that can be left and still reaches no other station.

| | |
|---|---|
| **Status** | open — defect confirmed; choice of fix DEFERRED — needs Adam (he has ruled the outcome: "it should be easily possible to get back") |

The builder emits, for a may-turn station square, a turning copy that is a **terminus destination**
(`AutonomyBuilder.java:826` — `boolean stops = point.isStation() && arrivalAllowed(node);` and
`:963-970` — `json.put(stops ? "terminus" : "reversing", true)`). Its outgoing edges, however, come
only from the edge loop at `AutonomyBuilder.java:1010-1034`, gated by `Node.leavesBy`
(`:114-126` — `if (reverse) return arrival == exitSide;`): a turning copy leaves only by the side it
arrived by, and only where a **traced** reduced edge exits that side. On a one-way stretch — the main
line's direction arrows in `setup.json` `tileDirections` — no reduced edge runs back the way the train
came, so the copy is emitted as a destination with **zero outgoing edges**. On Adam's own derived
graph this is "BottomMainB (eastbound, reverse)" (measured; recorded in
`testEveryTestIsInTheBattery.java:45-49`).

Nothing at the choosing tier refuses it: `pickPath`'s destination filter (`Layout.java:3731-3734`)
checks active/destination/not-reversing/autoDestination/terminus-vs-reversible/exclusions — there is
no "has a way out" clause — and the trap is a terminus, not a reversing point, so
`reversesAlongTheWay` (`:3746`) does not fire either. A **reversible** locomotive is therefore sent
there by full autonomy of its own accord; any locomotive can be sent there by the right-click door
(`getPossiblePaths` has no such clause at `:4314`). Once there, the train is stranded absolutely:
`getPossiblePaths` from a point with no outgoing edges is empty, so even manual rescue by route is
impossible — only lift-and-replace recovers — and every Return Home for the rest of the session
answers IMPOSSIBLE (correctly: `connected` from a point with no exits is false, RTG-B1's scan names
the train).

The editor's checks cannot see it, which is why it survived to be measured on the real railway:

- the trapped-arrival scan **exempts turn-around tiles by definition**
  (`AutonomySession.java:3142` — `if (isTurnAround(tile)) continue;` — "nobody has said trains may
  turn round there" is part of what "trapped" means there);
- `TERMINUS_STRANDED` / `STATION_REACHES_NOTHING` ask reachability **of the square**, not of the copy
  (`AutonomyChecks.java:1061-1098` — `reducer.reachableTiles(station.getTile(), …)`): the BottomMainB
  square reaches plenty eastward through its plain copy, so no finding fires while its turning copy
  is a dead end.

Candidate fixes, in descending order of how much they honour the ruling: emit the turning copy's
return edges (the may-turn marking is the operator saying this track is physically traversable both
ways for a shunt, so the reversal move may run against the arrows); or stop emitting a turning copy as
a *destination* when it has no exit (`stops = false`, plus a per-copy editor finding); or, as a
defensive backstop either way, a "destination must have an outgoing edge" clause in
`barredFromAutonomy` (which `pickPath` and `hasAutonomousDestination` both mirror — one list,
`Layout.java:3981-4009`). Which of these Adam wants is his call; the first matches his quoted ruling.

### RTG-A2 — Uncommitted working-tree edits to the real railway's autonomy configuration

**CLOSED by `FX2-1`.** "Just restore the previously committed version", then "you can restore and then patch that tile". Done.

| | |
|---|---|
| **Status** | DEFERRED — needs Adam. One-sentence question: are the working-tree changes to `cs2_sample_layout/config/autonomy/*` (placements moved, "75 407 DB" placement and two `excludedLocs` lists removed, `atomicRoutes` false, `pathPreference` RANDOM_ANY_STATION, timetable naming "MT-x233 Test Loc") your own hands-on session, or damage? |

`git status` shows `cs2_sample_layout/config/autonomy/setup.json` and `configuration-Main.json`
modified. The briefing's list of expected uncommitted changes does not include them, the folder is
"not recoverable", and this round's coordinator has just killed concurrently running batteries — so
the possibility that a test JVM wrote here must be ruled out loudly rather than assumed away. The diff
includes **silent data loss if unintended**: the `excludedLocs` lists on `1 - Main:14,3` and
`1 - Main:13,9` are gone, the `75 407 DB` placement at `2 - Bottom:13,14` is gone, `atomicRoutes`
flipped true→false (the setting Adam's configuration is documented to use), and the stored timetable
now names "MT-x233 Test Loc".

Stated lean, so this is calibratable: the placements match the five-train arrangement Adam himself ran
by hand for MT-245/MT-246 (EN57-947 homed at BottomMainC facing W, EN57-203 at TunnelCenterPark, the
tunnel berths re-activated, `20c30781`/`e0b706bf` record him doing exactly this on 2026-08-31), and
"MT-233 Test Loc 2" is plausibly a locomotive he created running manual test MT-233. So this is
*probably* his own session state. It is filed at A because if that guess is wrong, this is the exact
data-loss the hard rules exist to prevent, and nothing else in this round will ask. If Adam confirms
the edits are his, this finding closes as not-a-defect (record the transition here, per the README).

---

## B findings

### RTG-B1 — The excluded parking-berth test strands its own trains on out-of-service track, then blames backing-in

| | |
|---|---|
| **Status** | open (test-fixture defect; the planner's answer is correct) |

Full mechanism in "The known open failure" above. Evidence chain: observed arrangement "PB test loc 0
at ParkingTrack6" (task-supplied current failure text); `"2 - Bottom:17,6"` carries `"active": false`
in `configuration-Main.json`; `getPossiblePaths` applies no `isActive` filter (`Layout.java:4314`)
and `isPathClear`'s endpoint inactive rules are `isAutoRunning()`-fenced (`:2192`, `:2269`), false
during the hand-start; `executePath` is synchronous, so the train reached ParkingTrack6 before
autonomy started; autonomy can neither start it there (`Layout.java:1671-1675`) nor route into or out
of an inactive point; `HomeStaging.plan()`'s `!isActive()` clause (`HomeStaging.java:443`) then
correctly proves IMPOSSIBLE. The assert message's claim about backing in past a reversing point is a
static suffix, not a diagnosis.

Fix (test-only): pick the first option whose destination is active — better, one
`isChoosableByAutonomy` accepts — or lift-and-replace the berth trains after the run phase. Keep the
hand-start itself; Adam's "the parked trains need to be manually started the first time" stands. Note
for the re-run: with RTG-A1 unfixed, a train can also strand at "BottomMainB (eastbound, reverse)",
so expect the test to stay red until both are addressed. Whether the *application's* manual tier
should keep offering out-of-service berths is settled doctrine (`Layout.java:2236-2242`), not re-filed
here; what the operator experience lacks when that door is used is RTG-C1.

### RTG-B2 — The backing-over-the-switch guard sums the whole path, but a train that reverses mid-path can only stand on the post-reversal suffix

**CLOSED by `FX2-3`.** Accepted as-is.

| | |
|---|---|
| **Status** | open; the residual berth-vs-switch question inside it is DEFERRED — needs Adam (one sentence below) |

The guard added by `17cad1fe` (`Layout.java:2330-2364`) applies when a path ends at a terminus or
reversing point and refuses the path when `loc.getTrainLength() > room`, where `room` is the sum of
**every** segment on the path (`:2341-2350`). For a forward arrival that matches Adam's quoted rule
("Do you sum the track segments leading up to it? if they are long enough, then we are good"). But
the route this guard was built for — a train that passes a reversing point and *backs* into the berth
(`executePathInternal` reverses at every intermediate reversing point, `Layout.java:5296-5308`) —
occupies only the track between the reversal and the berth. Pre-reversal segments cannot hold any
part of the backing train.

Concrete counterexample: origin →(10)→ R(reversing) →(1, over the switch)→ berth(2). `room` = 13, an
8-unit train is allowed; physically the backing move from R has 3 units of room and the train cannot
complete it without standing far across the switch — the exact case Adam asked to be guarded
("if segments < train length, then we can't reverse over the switch"). After arrival the whole path
is unlocked (`unlockPath` at `Layout.java:5591`) and the model records the train only at the berth
Point, so the fouled switch reads as free track to every later route: wrong behaviour on the layout
the moment it fires with real stock.

Reachability today: the guard requires `trainLength > 0` and **every** segment measured; Adam's
current data measures six tiles, so the guard is presently inert on his railway — but the editor is
now actively prompting him to fill in exactly these lengths (`reversalsWithoutLength`,
`AutonomySession.java:1921-1943`), so this arms itself as he complies. Fix shape: when the path
reverses along the way, sum only the segments after the last reversing end; keep the whole-path sum
for reversal-free terminus arrivals. The mutation test added in `17cad1fe` pins the current whole-path
arithmetic and must move with the fix.

Deferred question for Adam, one sentence: when the run-in *is* long enough, is a train longer than
berth-plus-switch still allowed to come to rest across the switch behind its berth (the "sum the
segments" reading), or should the berth-and-switch lengths alone bound it (the original question's
reading)?

---

## C findings

### RTG-C1 — IMPOSSIBLE names locomotives but never says why each one is stuck

| | |
|---|---|
| **Status** | open |

`plan()` knows exactly which proof condemned each blocked locomotive — inactive origin, non-station
origin, unrestable home, no turning route (`HomeStaging.java:443-445`), conflicting homes on one
section (`:454-494`), or a mutual hold (`:539-576`) — and `Plan.getBlocked()` carries only the
locomotives (`:296-330`). The operator, and this round's own test author, are left to guess: the
parking-berth test's failure text blames backing-in for a train actually stuck on an out-of-service
point, which is precisely the mis-diagnosis a reason string would have prevented. The remedy exists in
every case (drive the train off by hand, reactivate the point, move the home); it is only
undiscoverable. Suggest `getBlocked()` gaining a per-locomotive reason (message key), shown wherever
the IMPOSSIBLE is surfaced.

### RTG-C2 — `blockedSensors(Map state)` ignores its parameter

| | |
|---|---|
| **Status** | open |

`HomeStaging.blockedSensors` (`HomeStaging.java:1218-1235`) takes the hypothetical `state` and reads
only `this.start` and `this.sensorsSet`. The *behaviour* is correct — an unexplained sensor is one no
known train accounted for **at snapshot time**, and that classification must not drift as planned
moves rearrange the fleet — but the signature says otherwise, and both `search()` and `astar()`
recompute it per state (`:752`, `:833`) for a state-independent answer. Drop the parameter, compute
once in the constructor, and the next reader cannot build on the wrong assumption. No behavioural
change.

### RTG-C3 — The impossibility proof seeds the reversal state stricter than the search it speaks for

| | |
|---|---|
| **Status** | open — narrow; unreachable on builder-derived graphs, reachable on legacy hand-written ones |

`firstClearRoute` seeds its search with the train already turned when it stands on a reversing point
(`HomeStaging.java:948-949`, "A train already standing on a reversing point sets off turned" — which
matches the runtime, since arrival there switched its direction, `Layout.java:5575-5582`).
`connected`, the proof `canGetHome` rests on, seeds `false` unconditionally (`:1682-1684`). So for a
non-reversible locomotive standing on a reversing point whose terminus home is reachable without
passing a *second* reversing point, `plan()` answers IMPOSSIBLE — a proof — while its own search
would have found and the runtime would have driven the move. On a builder-derived graph this cannot
fire: a turning copy is never emitted as a station (`AutonomyBuilder.java:970` puts `reversing` only
on non-station copies), so such an origin already fails the `!isDestination()` clause first. A legacy
hand-written configuration (still loaded by the same `fromJSON`; `importLegacy` exists) may carry
reversing *stations* — the old model's berths — and there the false proof is reachable, if contrived.
Fix is one line: seed `connected` with `from.isReversing()`.

### RTG-C4 — Stale design-record comments contradict the terminus-tier rulings of 2026-08-31/09-01

| | |
|---|---|
| **Status** | open |

Comments this project treats as the design record now assert rules that moved:

- `HomeStaging.connected` javadoc (`HomeStaging.java:1655-1660`): "The runtime already insists —
  `Layout.isPathClear` refuses a terminus to a locomotive that cannot reverse unless the path passes
  a reversing point." That rule was removed from `isPathClear` on 2026-09-01
  (`Layout.java:2280-2297`, "NO TERMINUS RULE HERE") and lives at the choosing tier only.
- `AutonomyBuilder.homeCopy` javadoc (`AutonomyBuilder.java:581-582`) and
  `AutonomyChecks.checkHomesThatNeedReversing` javadoc (`AutonomyChecks.java:714-717`): both say
  "HomeStaging.canRest refuses a terminus to a locomotive that cannot reverse"; `canRest` has not
  done so since 2026-08-31 (`HomeStaging.java:1621-1636`, "NOT THE TERMINUS RULE") — the rule is now
  the route-shaped `mustBackIn`/`connected` pair. The HOME_NEEDS_REVERSIBLE warning itself remains
  useful, but its premise sentence is now false: a non-reversible locomotive *can* live on an
  every-copy-turns square if a turning route reaches it.

Not fixed by this pass (rule 3/4 of the briefing); filed so the next reader is not sent reasoning
from a rule that no longer exists. Adam's own quoted rulings inside those comments must be preserved
verbatim when the surrounding claims are corrected.

---

## D — checked and clean, and things that look wrong but are not

| # | What was checked | Result |
|---|---|---|
| RTG-D1 | The IMPOSSIBLE for a train on an inactive/non-station origin, suspected as a false proof | Not a defect: staging executes with `running` set and `isPathClear:2192`/`:2201` refuse the first edge, so planner and runtime agree; the planner's origin rules (`firstClearRoute:928,940`) are faithful copies |
| RTG-D2 | `firstClearRoute` ignoring lock edges | Deliberate and sound — staging runs one train at a time (`timetableSequential`, `Layout.java:6528-6538`), so the runtime's lock question always answers no; documented at `HomeStaging.java:966-984` and audited by `auditAgainstRuntime` |
| RTG-D3 | The reversal-as-state search (`Candidate.turned`, seen-set keyed by point+turned+commands) in `firstClearRoute`, and the mirrored two-state BFS in `connected` | Correct by reading: turned is monotonic, terminus is arrived-at but never expanded, arrival is tested before the visited set, straight and turned arrivals are distinct states; `mustBackIn` is asked per copy of the home square (`canGetHome`, `HomeStaging.java:1556-1578`) |
| RTG-D4 | `pickPath` shuffle / stable sort / band gate (an area with July-cycle history) | No defect found; the RANDOM_ANY_STATION comparator and the band close-out at `Layout.java:3706-3710` read correctly |
| RTG-D5 | `hasAutonomousDestination` mirroring `pickPath` | In step clause-for-clause today, including the terminus clause (`Layout.java:3474-3477` vs `:3731-3734`) and `reversesAlongTheWay` |
| RTG-D6 | Four-flag interactions at the model door | `Point.setTerminus`/`setReversing` refuse the illegal pair in both orders (`Point.java:333-375`); the builder derives both flags exclusively and strips authored copies (`DERIVED`, `AutonomyBuilder.java:197,934`); `stops` decided once so a barred turning copy degrades to a plain reversing point rather than an unloadable terminus-non-station (`:818-828`, `:966-970`) |
| RTG-D7 | Multi-Point-per-square occupancy: `setLocomotive` sweep vs `reserve`, `clearBlockExcept` in `moveLocomotive`, `Edge.isOccupied(wholeBlock)`, `canEnter`'s sensor-sibling rule | One-place invariant holds through every placement door; the planner is the stricter half by design and stations always carry an s88 (`Point` constructor throws), so the sensor rule covers the block pairs on emitted graphs — the "agree by coincidence" note at `HomeStaging.java:1141-1149` remains accurate |
| RTG-D8 | `astar` re-scoring (stale queue entries), launch-pad exemption, block-aware `atHome`/`misplaced`, POSITION_AMBIGUOUS screen | Correct by reading; the `Scored`-carries-its-own-score device does what its comment claims |
| RTG-D9 | Reversal execution: intermediate reversing points and terminus arrival | `executePathInternal:5296-5308` and `:5575-5582` reverse where the graph says; a non-reversible train that backed in departs forwards, which is the point of the 2026-08-31 ruling |
| RTG-D10 | `claimHome` / `setHomeLocomotive` / `rebuildHomeStations` after the "home is the square" ruling | Square-level checks present on both doors (`isSamePlaceAs` at `Layout.java:1088-1091` and `:1229-1235`); assignments win over positional claims; loader resolves names once and reports dangles |

`AutonomyCompanionStore` was read only at the interfaces the builder and session consume (lengths,
blocking points, barred arrivals, captions); no finding, and no claim of full coverage there.

---

## Open questions for the orchestrator (to run serially, not by this pass)

1. **Run the excluded test once with the blocked list made visible** — print `plan.getBlocked()` and,
   for each blocked locomotive, its Point and that Point's isActive/isDestination/outgoing-edge count.
   Prediction being tested: every blocked locomotive stands on an `active:false` Bottom-page
   ParkingTrack or on "BottomMainB (eastbound, reverse)"; none is blocked at the
   `connected`/`mustBackIn` tier. If a train shows blocked on an *active station with exits*, there is
   a third mechanism this review did not find, and the backing-in route search becomes suspect again.
2. **Ask Adam the RTG-A2 question before anything else writes to or reads state from that folder.**
3. After RTG-A1's builder fix and RTG-B1's fixture fix land, re-run the test; if green, restore it to
   the battery and update `DELIBERATELY_OUT` (its own count assertion pins the list at 2).
