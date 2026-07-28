# Return-to-home (staging) feature review - 2026-07-27

**Prefix for citing this document: `HS`.**

**Version reviewed:** commit `829d25d`, branch `master` - the five "New come home features" commits
(`8a1b77e`..`829d25d`, 2026-07-27 21:52-23:19), which add `HomeStaging`, the `Layout` home-claim and
sequential-timetable machinery, the Return Home button and menu items, and two test classes.
**Reviewed:** 2026-07-27. **No code was changed as part of this review, and no tests were run** - the
author builds and tests in NetBeans. Every claim below was made by reading the enforcing method, per
[README.md](README.md).

**This review stands outside the July 2026 cycle** indexed by
[2026-07-cycle-summary.md](2026-07-cycle-summary.md) - it reviews new feature work, not that cycle's
fixes - but it cites the cycle's documents by their prefixes and was asked for with three focuses:
logic errors, parity between the planner and the `Layout` runtime rules (inactive points named
explicitly), and the correctness of UI enablement.

**Method note.** The planner re-implements the runtime's path rules on a shadow state, so the whole
review question is parity - and this codebase's history says the way to answer it is to put the
planner's predicate and the runtime's (`isPathClear`, `pickPath`, `getPossiblePaths`) side by side,
rule by rule, in the source. That was done for every rule; the results are `HS-B4` (one real gap),
`HS-D1` (everything that matched), and one trap avoided: the inactive-point rule is gated on
`isAutoRunning()`, which the July cycle twice warned reads like `isRunning()` and is not - both were
opened, and the planner's assumption is right (see D1).

Findings use the A/B/C/D convention in [README.md](README.md).

---

## Status

| # | Finding | Severity | Status |
|---|---------|----------|--------|
| HS-B1 | `requestReturnToHome` ignores what `loadReturnToHomeTimetable` returns, and plans twice; when the second plan fails, the user's *original* timetable executes, unrequested | B | **Fixed** |
| HS-B2 | With timetable capture toggled on, a staging run appends its own moves as captured entries, then "executes" them, fails, and reports a successful run as stopped - with an emergency stop | B | **Fixed** |
| HS-B3 | Provably impossible configurations are reported as `NO_PLAN_FOUND` ("may still be possible"), after burning the full search - an inactive home station is the flagship case | B | **Fixed** |
| HS-B4 | The planner never blocks the sensor of a moved train's destination, so a later move through a point sharing that s88 plans clean and is refused at execution - aborting the run | B | **Fixed** |
| HS-C1 | The two right-click "Return Locomotives Home" items are not greyed while autonomy runs; the button is. Clicking produces an error dialog instead | C | **Fixed** |
| HS-C2 | The Return Home button goes stale on arrivals: the refresh is wired into `repaintAutoLocList`, but the arrival callback calls `repaintAutoLocListLite` directly, bypassing it | C | **Fixed** |
| HS-C3 | The Execute Timetable handler mutates Swing state from its worker thread; the new lines extend a pre-existing pattern the staging flow itself avoids | C | **Fixed** |
| HS-C4 | Three new javadoc blocks are orphaned - stacked above another javadoc, so tools attach them to nothing | C | **Fixed** |
| HS-C5 | The design's post-run arrival verification is absent: nothing checks that every homed locomotive actually arrived | C | **Fixed** |
| HS-D1 | Parity checks that came back clean, and design-contract items verified present | - | Recorded |

No A findings, though `HS-B1` is A-shaped by the letter of the definition ("wrong behaviour on the
layout") and is rated B on the same reasoning as `FP-B1`: a narrow trigger, and every move the wrong
timetable makes is still a validated, user-authored path.

---

## B. Medium

### HS-B1. The load's answer is thrown away, and the fallback is the wrong timetable

[TrainControlUI.java:13007](../../src/org/traincontrol/gui/TrainControlUI.java) (the call:
`layout.loadReturnToHomeTimetable();` - return value discarded),
[Layout.java:3380](../../src/org/traincontrol/automation/Layout.java) (`loadReturnToHomeTimetable`,
which plans **again** and returns without loading when the second plan fails),
[Layout.java:956](../../src/org/traincontrol/automation/Layout.java) (`getNeighbors` - the shuffle).

The flow plans twice: `requestReturnToHome` calls `planReturnToHome()` and checks `isPossible()`, then
calls `loadReturnToHomeTimetable()` - which *internally plans again* and only replaces the timetable
if its own plan is possible. The UI discards that second answer and proceeds to
`layout.executeTimetable()` unconditionally.

So whenever plan #1 succeeds and plan #2 does not, the timetable was never replaced - and
`executeTimetable()` runs whatever is loaded: **the user's original timetable**, at normal
(non-sequential) semantics, when what they pressed was "Return Home". Depending on where the
locomotives stand, that is either unrequested train movement or an entry retrying forever (normal mode
never gives up), with autonomy stuck running either way.

The two plans can genuinely disagree, three ways - traced, not assumed:

1. **The planner is nondeterministic.** `firstClearRoute` and `connected` traverse via
   `this.layout.getNeighbors(...)`, which ends in `Collections.shuffle(neighbors)` - the July cycle's
   most famous trap (`CR`'s "bfs appeared deterministic - the shuffle is in getNeighbors"). Two
   consecutive plans explore in different orders; near `SEARCH_LIMIT` or `ROUTE_SEARCH_LIMIT`, one can
   finish inside the budget and the other not.
2. **State can change between the plans.** Both run at rest, but the window is real: a manual
   locomotive drag, a feedback flicker (the snapshot reads live sensors), or an individual path
   double-clicked from the locomotive panel.
3. **`loadReturnToHomeTimetable` re-checks `isRunning()`** and refuses with a *fabricated*
   `NO_PLAN_FOUND` plan - so anything that started a locomotive between the UI's own `isRunning` check
   and the load takes this path deterministically. (That fabricated outcome is also the wrong message:
   the dialog for `NO_PLAN_FOUND` advises "try moving one locomotive out of the way", which is
   unrelated advice when the actual problem is that something is running.)

**Fix shape:** use the returned plan - `if (!loaded.isPossible()) { show describeStagingPlan(loaded);
return; }` - and plan once, not twice: either pass the already-computed plan into the load, or drop
the UI's separate `planReturnToHome()` call and let the load's plan drive both the possibility check
and the dialog. Planning once also halves the cost of `HS-B3`'s worst case.

### HS-B2. Capture on: the staging run records itself, replays the recording, and calls success a failure

[Layout.java:3410](../../src/org/traincontrol/automation/Layout.java) (`loadReturnToHomeTimetable`
restores the capture flag after loading, before the run),
[Layout.java:2685](../../src/org/traincontrol/automation/Layout.java) (`executePathInternal` calls
`addTimetableEntry` on every path it starts),
[Layout.java:234](../../src/org/traincontrol/automation/Layout.java) (`addTimetableEntry` appends
whenever `timetableCapture` is on),
[Layout.java:2340](../../src/org/traincontrol/automation/Layout.java) (the dispatch loop bound,
`i < this.timetable.size()`, re-evaluated every iteration).

The design memo for this feature listed this exact hazard: *"Force `timetableCapture` off for the
duration, or staging moves get appended as if captured."* The implementation forces it off **for the
load only** - `loadReturnToHomeTimetable` saves the flag, loads, and restores it before returning -
and `testTimetableCaptureSurvivesLoadingAPlan` pins that narrowed behaviour as if it were the goal.

With the toggle on, the run then does this, step by step in the enforcing methods:

1. Every staged move that starts passes through `executePathInternal` -> `addTimetableEntry` -> a copy
   is **appended to the very timetable being executed**.
2. The dispatch loop's bound is `this.timetable.size()`, re-read each iteration, so after the last
   staged entry it walks into the appended copies.
3. Each copy fails `executePath` validation (the locomotive is now at the path's *end*, not its
   start), retries three times at 2 s each in sequential mode, then sets `abandoned`,
   **calls `stopLocomotives()`**, and the run returns false.
4. The user - whose trains are all home, the run having actually succeeded - gets the
   "return home was stopped: a train's path stayed blocked" dialog and an emergency stop.

The borrowed-timetable restore in the UI's `finally` does clean up the appended entries afterwards, so
the damage is the false failure, the stop, and the wasted retry minutes - not permanent pollution.

Also worth noting: with capture appending mid-run, the "last entry" test at
[Layout.java:2453](../../src/org/traincontrol/automation/Layout.java) (`index == size()-1`) misses,
because the size grew - the run then terminates through the abandonment path rather than the normal
one, which is what makes the false failure unconditional rather than timing-dependent.

**Fix shape:** make the exclusion match the design's word "duration". The cleanest point is
`addTimetableEntry`: skip the append when `this.timetableSequential` is set - one condition, no
flag-juggling across the UI, and the capture toggle keeps its state for afterwards. The test should
then assert that a staging run *with capture on* appends nothing.

### HS-B3. "No plan found" where "impossible" is provable - after paying for the full search

[HomeStaging.java:247](../../src/org/traincontrol/automation/HomeStaging.java) (the unreachability
check: graph connectivity only),
[HomeStaging.java:725](../../src/org/traincontrol/automation/HomeStaging.java) (`canRest` - never
consulted about the home itself before searching),
[HomeStaging.java:440](../../src/org/traincontrol/automation/HomeStaging.java) (`astar`'s single
`return null` for two different terminations).

The class's own documentation makes the three-outcome contract explicit - IMPOSSIBLE is a proof,
NO_PLAN_FOUND means the search gave up, and conflating them "would be a statement this class cannot
support." Two paths conflate them anyway, in the other direction - provable impossibility reported as
a mere give-up:

1. **A home that fails `canRest` for its own locomotive.** The pre-search impossibility check tests
   only `connected(from, home)` - raw graph connectivity, blind to everything else. But a locomotive
   whose home is **inactive** (`stations` excludes it, `canRest` refuses it) can *never* be planned
   home: the greedy pass finds no route to it, and A* cannot even generate a move ending there, so the
   goal is unreachable by construction. The search exhausts - up to `SEARCH_LIMIT` = 200,000
   configurations, each expansion running route searches per locomotive per station - and the user is
   told "No way to arrange this was found. It may still be possible - try moving one locomotive out of
   the way by hand", which is wrong twice: it is not possible, and no amount of hand-moving will help.
   The same holds for a home that now excludes its locomotive, fails the train-length check, or is a
   terminus whose locomotive is not reversible. All four are one cheap check per homed locomotive,
   before any search, with the locomotive and the *reason* nameable in the dialog - "home station X is
   inactive" is actionable in a way neither existing message is.
2. **A\* exhaustion versus the limit.** `astar` returns null both when `examined` hits `SEARCH_LIMIT`
   (gave up - `NO_PLAN_FOUND` is exactly right) and when `open` empties first (the whole reachable
   configuration space was examined - under the model's rules that is a *completeness proof*, and the
   honest answer is IMPOSSIBLE). One boolean distinguishes them.

Compounding both: the search runs with the Return Home and Execute Timetable buttons already disabled
and no way to cancel, so the worst case is a long, silent, unabortable wait that ends in the wrong
message. (The planning cost itself is a recorded unknown - see the note under D1.)

**Fix shape:** before the connectivity loop, test `canRest(l, home)` for every homed, misplaced
locomotive and return IMPOSSIBLE naming the failures and reasons; in `astar`, return a distinguishable
"exhausted" result (or have `search()` check whether the limit was hit) and map exhaustion to
IMPOSSIBLE. Both fixes also spare the search burn in exactly the cases that burn longest.

### HS-B4. A moved train's new sensor is invisible to the rest of the plan

[HomeStaging.java:700](../../src/org/traincontrol/automation/HomeStaging.java) (`blockedSensors` -
iterates only `this.sensorsSet`, the sensors occupied *at snapshot*),
[Layout.java:1093](../../src/org/traincontrol/automation/Layout.java) (`isPathClear` reading live
feedback for every edge end at execution time).

The planner's sensor model handles departures correctly: a snapshot-occupied sensor is released once
every point reporting it is vacated. Arrivals are the gap. When a planned move relocates a train to a
station whose sensor was **clear at snapshot**, that sensor never enters `blockedSensors` - the set is
built only over `sensorsSet` - so a *later* move routed through any point sharing that s88 address is
planned as clear. (The occupied *point* itself is still blocked, by the occupancy check; only the
shared-address neighbours slip through.)

At execution, `isPathClear` reads the real sensor. On hardware where a stationary train holds its
occupancy detector, the arrived train is now reading occupied - the later move is refused, retries
three times, and the run is **abandoned** with the "path stayed blocked" dialog: a plan the planner
was sure of, killed by a rule it modelled for the snapshot but not for its own futures. Shared
addresses are not exotic here - the snapshot code's own comment describes a bypass and a platform
routinely reporting the same sensor, and nothing enforces s88 uniqueness across points (the reason
occupancy is keyed by address in the first place).

*The honest caveat:* whether a resting train holds its sensor is hardware-dependent - pulsed contact
tracks clear behind the train (simulation models this), occupancy detectors do not. On pulsed
hardware this gap never fires. That makes it a B rather than an A: real abort, specific
configuration.

**Fix shape:** the snapshot already contains the answer to "does this layout's hardware hold
sensors": if occupied stations' sensors read occupied at snapshot, model arrivals the same way -
add the destination's s88 to the blocked set (keyed with its own explained/held bookkeeping) when the
snapshot showed resting trains holding theirs. On pulsed hardware the snapshot shows the opposite and
the model stays as it is. Failing that, a conservative always-block matches `isPathClear`'s worst
case at the cost of refusing some bypass routes the runtime would allow.

---

## C. Low

### HS-C1. The menus offer what the button refuses

[GraphRightClickGeneralMenu.java:107](../../src/org/traincontrol/gui/GraphRightClickGeneralMenu.java),
[LayoutRightclickAutonomyMenu.java:44](../../src/org/traincontrol/gui/LayoutRightclickAutonomyMenu.java)
(enablement: `triageReturnToHome() == null` only),
[TrainControlUI.java:13087](../../src/org/traincontrol/gui/TrainControlUI.java)
(`refreshReturnHomeButton`: disabled whenever `layout.isRunning()`).

The button greys while anything runs; the two right-click menu items consult only the triage, which
knows nothing about running state - locomotives mid-path are still "away from home", so the item shows
enabled during an autonomy run. Clicking it is safe (`requestReturnToHome` opens with an `isRunning`
guard and explains), but the enablement question the user asked - are things greyed out correctly? -
has three answers here and should have one. Both menus also compute the triage against positions that
are changing under them while trains run, which is harmless (any answer leads to the same guard) but
meaningless. One `isRunning()` short-circuit in each menu, with the existing "wait for active
locomotives" text as the tooltip, aligns all three surfaces. The same block is duplicated verbatim in
both menus - a twin to keep in sync, or extract.

### HS-C2. The button's refresh misses the event its comment promises

[TrainControlUI.java:15258](../../src/org/traincontrol/gui/TrainControlUI.java) (the refresh, wired
into `repaintAutoLocList`, commented "the button stops being stale the moment the last locomotive
arrives"), [TrainControlUI.java:15087](../../src/org/traincontrol/gui/TrainControlUI.java) (the
arrival path: the `GraphCallback` - "fires at the beginning and end of each path" - calls
`repaintAutoLocListLite()` **directly**).

Arrivals do not pass through `repaintAutoLocList`; they enter at `repaintAutoLocListLite`, one level
below the refresh. So the exact event the comment names as the reason for the placement is the one
that bypasses it. Concretely: with every locomotive home (button greyed, "already home" tooltip),
double-click a path in the locomotive panel to move one away - on arrival the button should light and
stays grey, until any unrelated repaint happens by. The staging and Execute Timetable flows are
unaffected (both refresh explicitly), and the right-click menus re-triage on open, which is why this
is C. Fix: put the refresh at the top of `repaintAutoLocListLite` (both public entrances funnel
there), or have the callback call `repaintAutoLocList` - either way marshalled to the EDT, since the
callback fires on locomotive threads (see HS-C3).

### HS-C3. Swing state mutated off the EDT in the Execute Timetable handler

[TrainControlUI.java:12183](../../src/org/traincontrol/gui/TrainControlUI.java): the worker thread
that runs `executeTimetable()` calls `returnHomeButton.setEnabled(false)`,
`startAutonomy.setEnabled(...)`, `refreshReturnHomeButton()` (two Swing mutations plus a tooltip) and
`gracefulStop.setEnabled(false)` directly. The surrounding lines predate these commits - the pattern
is inherited - but four of those calls are new in this diff, and the same feature's own
`requestReturnToHome` wraps every Swing touch in `invokeLater`, so the codebase now demonstrates both
conventions side by side in the two handlers that do the same job. Same class of defect as the
`LayoutLabel` highlight fix this month ("mutating a Swing component off the EDT - the one place in
this class that did not marshal its work"). `setEnabled` off-EDT rarely misdraws, which is why this is
C and not B.

### HS-C4. Three orphaned javadoc blocks

Three of the feature's javadoc comments are stacked immediately above *another* javadoc, so javadoc
tooling attaches them to nothing and the next reader finds the wrong description on the member:

- [Layout.java:3274](../../src/org/traincontrol/automation/Layout.java) - `planReturnToHome`'s
  description ("Works out whether every locomotive can be sent back...") sits above
  `logStagingAudit`'s own javadoc.
- [Layout.java:3334](../../src/org/traincontrol/automation/Layout.java) - `triageReturnToHome`'s
  description ("Whether there is anything to send home at all...") sits above `isFeedbackOccupied`'s.
- [TrainControlUI.java:13070](../../src/org/traincontrol/gui/TrainControlUI.java) -
  `describeStagingOutcome`'s description ("Shared by the cheap triage and the full plan...") sits
  above `refreshReturnHomeButton`'s.

Same cosmetic family as `PV-C5`/`PV-C6`, different mechanism (blocks written above the wrong
neighbour, not a reformat). Move each block down to its method.

### HS-C5. The promised post-run check does not exist

The design memo closes its execution section with: *"Also add a post-run check that every homed
locomotive arrived; `executeTimetable` never verifies."* Nothing in the shipped flow performs it -
`requestReturnToHome` trusts `executeTimetable`'s boolean, which reports only abandonment. The chain
that makes the check redundant (every move either completed or abandoned, and `executePath` completes
only on arrival) holds *today*, but it is exactly the kind of multi-link invariant this month's
reviews watched decay; the check is one `triageReturnToHome()` call after the run - `ALREADY_HOME`
confirms, anything else (excepting a user-initiated graceful stop) warrants the stopped dialog. Cheap
insurance, and it would have caught `HS-B1` and `HS-B2` at runtime.

---

## HS-D1. Parity checks that came back clean, and contract items verified

**The rule-by-rule parity table, planner vs runtime - each read in both sources:**

- **Inactive points** - the focus question, and the subtlest match. The runtime's rule
  ([Layout.java:1076](../../src/org/traincontrol/automation/Layout.java), `:1125`) applies **only when
  `isAutoRunning()`** - which was opened rather than trusted, this being the codebase where
  `isAutoRunning`/`isRunning` name-confusion produced two review errors: `isAutoRunning()` returns
  `this.running` alone, and `executeTimetable` sets `this.running = true`. So during staging
  execution the rule is live, and the planner enforcing it unconditionally (`canEnter`, `canRest`,
  and `stations` all test `isActive`) matches the state its plans actually run in. The audit's
  decision to skip inactive endpoints when comparing against the at-rest runtime is likewise correct.
  The one inactive-point defect is not parity but reporting - `HS-B3`'s inactive *home*.
- **Excluded locomotives** - runtime refuses excluded non-destination intermediates
  (`isPathClear:1058`) and excluded destinations only at selection (`pickPath:2037`); the planner's
  `canEnter` (destinations traversable regardless) and `canRest` (excluded destinations refused)
  split identically.
- **Terminus** - runtime: terminus legal as path start and end, never mid-path (`:1067`), reversible
  locomotives only at the end (`:1136`). Planner: never expands a terminus (so it appears only as
  origin or final end), `canRest` demands reversibility. Match.
- **Train length** - endpoint-only in both (`:1115` / `canRest`).
- **Lock edges** - `Edge.isOccupied` is "end point holds someone else, or the locked flag"; the
  planner checks the endpoint and skips the flag, correctly, since in every planned state nothing is
  mid-path and no lock is held.
- **Accessory-command conflicts** - `withCommandsOf` mirrors `isPathClear`'s
  one-setting-per-accessory rule, and the route search's dominance pruning (`alreadyReached`) keeps
  it sound when the same point is reachable under different settings.
- **Sensors at rest** - read from live feedback at snapshot, never inferred from occupancy; released
  only when every reporting point is vacated; unexplained sensors block forever. Departures correct;
  arrivals are `HS-B4`.
- **maxActiveTrains / edge locks / activeLocomotives** - absent from the model by the sequential
  argument, which holds: the sequential wait ensures nothing is active when the next move validates.

**Execution machinery:**

- The sequential wait is race-free where it matters: `activeLocomotives.put` precedes
  `setExecutionTime` inside one `synchronized (activeLocomotives)` block
  ([Layout.java:2663](../../src/org/traincontrol/automation/Layout.java)-2676), so "started but not
  yet active" is not observable in any ordering the JMM makes plausible - and even the theoretical
  reordering is contained by `configureAndLockPath` re-validation.
- The empty-timetable early return prevents a permanently-stuck `running` flag; the completion wait
  correctly outlives the last dispatch via the `activeLocomotives` drain; the `startIndex` clamp
  handles the reset window.
- Staging retries are bounded in sequential mode only, with their own pacing constant (the delay
  settings may be zero); normal timetable behaviour is untouched - the design's item 3, verified.
- `secondsToNext` is 0 on every staged entry - design item 4, verified.
- The timetable is borrowed and restored in a `finally` that covers impossibility, completion,
  abandonment, graceful stop and exception alike, and `setTimetable` clears the sequential flag on
  every path out - an improvement on the design's confirm-and-restore, with the crash case reasoned
  (the timetable reaches disk only on save). `testTheTimetableIsBorrowedAndGivenBackUnchanged` and
  `testGetTimetableIsLiveSoCallersMustCopyToRestoreIt` pin it.

**Home-claim semantics (design contract):**

- Claims are injective, first-claim-wins (`claimHome` tests both directions); captured in `fromJSON`
  at placement, exactly where the design specified; a hand-placed locomotive claims only an unclaimed
  station; moving a homed locomotive never re-homes it; `locDeleted` releases the claim (the
  `IND-M4`/`INT-B1` defect class, pre-empted). All four are pinned by tests.
- One deviation, noted not filed: any hand-*move* of a homeless locomotive claims its target
  (`moveLocomotive` -> `claimHome`), slightly broader than the design's "placed after load claim
  their initial point". Documented in the code, arguably friendlier; a free agent can acquire a home
  by being repositioned.
- A homed locomotive *removed from the graph* keeps its claim and silently drops out of the goal test
  (triage counts placed locomotives only, so "already home" can be reported while a homed locomotive
  sits in the roundhouse). Defensible - it cannot be driven home, and its claim should survive for
  when it returns - recorded so the choice is visible.

**UI and strings:**

- The `*`/`+` legend change in `AutoLocomotiveStatus` is matched by the updated
  `tooltip.currentLocation` text - home and timetable-start are now distinct symbols with a correct
  legend.
- All 19 new message keys exist in all eight bundles - full parity re-audited at 1,215 keys per
  bundle, zero duplicates, ASCII-pure - with placeholder counts matching every call site.
- Both test classes are registered in `build.xml`.

**Recorded unknowns, per the `FP` convention:** the planning cost on a large, tightly packed layout is
unmeasured (`SEARCH_LIMIT` × per-expansion route searches is a large product, on a background thread,
with no cancel - see `HS-B3`); `triageReturnToHome` now runs a full snapshot on every
`repaintAutoLocList` and every menu open, individually cheap but newly on hot paths; and the design's
open question - whether a mid-run graph reload aborts the staging run - remains open (the
`isCurrentLayout` fence abandons the in-flight path, but what the dispatch loop does with the
remaining entries of a stale plan was not traced to the end here).

---

## Addressed 2026-07-27

All nine findings fixed by the author of the code under review, after reading each one against the
enforcing method rather than taking the write-up on trust.  Every claim checked out.

- **HS-B1** - the UI now plans **once**: `loadReturnToHomeTimetable()` is called first and its returned
  plan drives both the possibility check and the dialog, so the answer that decides whether the
  timetable was replaced is the answer that decides whether to run.  The running-refusal no longer
  masquerades as `NO_PLAN_FOUND` - a `LOCOMOTIVES_RUNNING` outcome carries the existing
  "wait for active locomotives" text.
- **HS-B2** - the exclusion moved to `addTimetableEntry`, which now skips the append while
  `timetableSequential` is set: the whole duration, as the design said, not just the load.  The
  flag-juggling around the load is gone.  Pinned by a new test.
- **HS-B3** (first half) - a home its own locomotive can never rest at (inactive, excluding it, too
  short, or a terminus it cannot reverse from) is now reported IMPOSSIBLE before any search.  The A*
  exhaustion-versus-limit distinction is **not** done - see below.
- **HS-B4** - the snapshot now measures whether this layout's hardware holds a sensor under a resting
  train, and models arrivals the same way when it does.  On pulsed hardware the measurement comes back
  false and the model is unchanged.
- **HS-C1** - both menus short-circuit on `isRunning()` and show the same text the button does.
- **HS-C2** - the refresh moved into `repaintAutoLocListLite`, where arrivals actually enter.
- **HS-C3** - the new Swing touches in the Execute Timetable handler are marshalled.
- **HS-C4** - all three javadoc blocks reattached to their methods.
- **HS-C5** - the run now asks `triageReturnToHome()` afterwards and reports a run that ended short,
  suppressed when the operator asked for the stop (a new `gracefulStopRequested` flag).

**Not addressed, and why:**

- **HS-B3, second half.** `astar` still returns one `null` for both "budget exhausted" and "search
  space exhausted", so a completeness proof is still reported as a give-up.  The fix is a boolean, but
  it changes what the operator is told in a case nobody has yet hit on a real layout; left for a
  measurement of planning cost to be taken first, alongside the recorded unknown under D1.
- **The `finally`-block coverage gap.** `testTheTimetableIsBorrowedAndGivenBackUnchanged` proves the
  mechanism round-trips; nothing proves the UI invokes it on every exit path, because that needs a live
  Swing window.  Closing it means moving the borrow into `Layout` - a refactor, deliberately not made
  while fixing review findings.
