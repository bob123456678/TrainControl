# Independent full review - 2026-07-28

**Prefix for citing this document: `IR`.**

**Version reviewed:** commit `999718f`, branch `master`. **Scope:** an independent pass over the whole
of `v2_7_4c..HEAD` (90 commits, ~11,600 inserted source lines), read file by file in the final state
with the diff beside it, plus a skim of `v2_7_2..v2_7_4c` for the regression baseline. The existing
review documents were deliberately **not read until this pass was complete**, so the findings below
were reached without anchoring; the comparison section reconciles them afterwards.
**Reviewed:** 2026-07-28. **No code was changed as part of this review, and no tests were run** - the
author builds and tests in NetBeans. Claims were verified by reading the enforcing method, or by
scripts run against the real data (all eight bundles, all `I18n.f` call sites) rather than samples.

**A note on timing:** the author reports two further bugs identified and about to be committed.
This review is of `999718f`; the upcoming commit should be diffed against that.
**Validation round (2026-07-28, later the same day):** the two announced bugs landed as `bbaca6f`
and are test defects, overlapping nothing here; the fixes for IR-B1/C1/C2 are in the working tree
above it. Both are validated in their own section below, and statuses in the table reflect that
round. The validation also answered this document's assumptions 1-3 (see the final section) and
produced one new finding, `IR-C3`, filed from the fresh pass that followed.
**Third round (2026-07-28):** a pass over the code no earlier round or recorded review had read -
the route editor flows, the model-level route mutations, the live-view getters, network timeouts and
function bounds. Two findings (`IR-C4`, `IR-C5`) and one amendment (`IR-C3`); clean checks in
`IR-D3`.

Findings use the A/B/C/D convention in [README.md](README.md).

---

## Status

| # | Finding | Severity | Status |
|---|---------|----------|--------|
| IR-B1 | The two run-starting entrances missed by the `WR-B2`/`WR-B3` sweep: autonomy reload and Start Autonomy gate on `isRunning()` alone, and a reload landing in the staging planning window permanently wedges the staging worker - `isAutonomyBusy` then reads true for the rest of the session | B | **Partly fixed 2026-07-28** (working tree) - both guard arms closed and validated; a *confirmed* reload during planning still wedges, because the Layout-side chain is untouched. See the validation section |
| IR-C1 | The timetable-editing surfaces (clear, restart, per-entry delay, capture toggle) also read the planning window as idle; edits made there are silently undone by the borrow-restore | C | **Fixed 2026-07-28** (working tree). Fix validated - all four surfaces plus entry-deletion, the route-activation toggle and its list |
| IR-C2 | `HomeStaging.astar` orders its priority queue on a mutable score map; re-scoring a state already enqueued breaks the heap invariant retroactively. Correctness survives (closed set), but the search burns budget out of order - worsening the open `HS-B3` NO_PLAN_FOUND ambiguity | C | **Fixed 2026-07-28** (working tree). Fix validated |
| IR-C3 | The graph-mutation surfaces (both graph key handlers, the graph general menu, the diagram autonomy menu's remove/edit items) still gate on `isRunning()` alone, so the planning window permits placements, cuts, point deletion and locomotive removal under the planner | C | **Open** - filed by the validation round's fresh pass |
| IR-C4 | `RouteEditor` ignores the boolean `editRoute` now returns: a model-refused edit closes the dialog as if saved, with the refusal only in the log - the `PV-C7` shape, reachable today only through a race with a concurrent Central Station sync | C | **Open** - filed by the third-round pass |
| IR-C5 | `Layout.getTimetable` hands the EDT the live list, and the graph callback repaints the timetable on every path start/end - so a capture-on autonomy run has locomotive threads appending to a `LinkedList` the EDT is iterating. The missed twin of `4f9eb09`, which gave `getHomeStations` a snapshot for exactly this reader/writer pair | C | **Open** - filed by the third-round pass |
| IR-D1 | Clean checks: bundle audits re-run from scratch, the fence coverage IR-B1's trace rests on, the `-1` sentinel's call sites, and six other suspicions that died on reading | - | Recorded |
| IR-D2 | Validation-round clean checks: the `bbaca6f` test fixes, the closed hazard families re-swept, source-encoding suspicion resolved, and two narrow observations recorded | - | Recorded |
| IR-D3 | Third-round clean checks over the previously unread code: network timeouts, function bounds, backup fallback, blank-name traps, and where the live-view pattern does *not* bite | - | Recorded |

No A findings. Nothing in the range was found to have regressed against `v2_7_2` into wrong
train movement or data loss; the one B is a session-wedging interleaving in the new feature.

---

## B. Medium

### IR-B1. Reload during staging planning wedges the worker for the session

[TrainControlUI.java:12779](../../src/org/traincontrol/gui/TrainControlUI.java) (the reload guard:
`hasAutoLayout() && isRunning()` - nothing else), [TrainControlUI.java:13354](../../src/org/traincontrol/gui/TrainControlUI.java)
(Start Autonomy: `isValid() && !isRunning()`), against
[TrainControlUI.java:13158](../../src/org/traincontrol/gui/TrainControlUI.java) (`isAutonomyBusy`,
which exists precisely because `isRunning()` reads false for the whole planning phase, and which
`WR-B3`'s fix wired into "all seven surfaces" - none of them these two).

`178aa4c` established that the staging flow's planning phase is invisible to `isRunning()` and closed
that window for every surface that could start a second **return-home** or edit the claim map. The
two entrances that start or retire a **run** were not in the sweep:

- **Start Autonomy** is enabled throughout planning (the flow disables it only after the plan
  succeeds), and its guard passes. The staging worker's own `isRunning()` re-check inside
  `loadReturnToHomeTimetable` catches this in all but a milliseconds-wide race, so this arm is the
  minor one.
- **The autonomy reload** (`validateButtonActionPerformed`) is the major one. During planning,
  `isRunning()` is false, so the reload's stop-confirmation block is skipped entirely and the Layout
  is swapped. The staging worker holds the old one, and what follows was traced in the enforcing
  methods, not assumed:

1. `loadReturnToHomeTimetable` runs on the retired Layout. Its `isRunning()` re-check passes (nothing
   was ever dispatched on it), so the staged plan loads and `executeTimetable` begins.
2. Entry 1's `executePath` runs `configureAndLockPath` **unfenced** - every accessory on the first
   move's path is commanded on the real hardware, and the departure-function callback fires. No train
   moves: every speed write in `executePathInternal` sits under `isCurrentLayout()` (verified per
   site - see D1), so the fence-abort at
   [Layout.java:3117](../../src/org/traincontrol/automation/Layout.java) is reached with the
   locomotive still standing.
3. That abort `return true`s **before** the completion block that removes the locomotive from
   `activeLocomotives` ([Layout.java:2897](../../src/org/traincontrol/automation/Layout.java) puts it,
   line 3181 would remove it) - the deliberate `HS-B5` strand, on the retired Layout.
4. With two or more moves in the plan, the dispatch loop's sequential branch
   ([Layout.java:2595](../../src/org/traincontrol/automation/Layout.java)) now waits for that stranded
   entry to leave `activeLocomotives`. It never will: `running` is true on the retired Layout,
   nothing reachable from the UI ever calls `stopLocomotives()` on it (every button resolves
   `getAutoLayout()` to the new one), and the loop's own completion wait - which does check
   `isCurrentLayout()` - is never reached because the dispatch `for` loop never ends.
5. The worker is wedged, so its `finally` never runs: `stagingFlowActive` stays true
   ([TrainControlUI.java:13036](../../src/org/traincontrol/gui/TrainControlUI.java)), and from then on
   every `isAutonomyBusy` surface - Return Home, both menus, all home-assignment edits - reports
   "trains are moving" for the rest of the session, and Execute Timetable (disabled at the commit
   point, re-enabled only in that `finally`) is dead. Restart to recover.

A single-move plan recovers: its entry thread is the last, calls `stopLocomotives()` on the retired
Layout, the completion wait exits on the fence, and the `finally` restores everything - leaving only
the spuriously thrown switches. No data is lost in either shape: the borrow-restore targets the
retired Layout, and the new Layout's timetable came from the file.

Reachable without contrivance: the planning phase has no progress indicator and no cancel (the
recorded `HS` unknown), A* cost is the open half of `HS-B3`, and "reload the file" is a natural
response to an apparently idle UI. B rather than A on the July convention - narrow trigger, primary
operations dead with no stated way back, no data loss - the same reasoning as `WR-B1` and `WR-B2`.

**Fix shape:** the reload guard should ask `isAutonomyBusy()` (and, when busy-but-not-running,
either refuse or treat it as the running case - confirm, then wait for the worker before swapping);
Start Autonomy's guard gets the same one-word change. This is the family's next instance after the
eight in the cycle summary's table and `WR-B3`; the predicate exists and is documented as the thing
every surface must ask.

---

## C. Low

### IR-C1. Timetable edits during planning are silently undone

[TrainControlUI.java:11633](../../src/org/traincontrol/gui/TrainControlUI.java) (`clearTimetable`),
line 14607 (`restartTimetable`), line 14569 (`updateTimetableDelay`), line 12080 (the capture
toggle) - all gated on `isRunning()` alone - against
[TrainControlUI.java:13023](../../src/org/traincontrol/gui/TrainControlUI.java) (the borrow:
`new ArrayList<>(layout.getTimetable())` at the commit point, restored in the `finally`).

The same window as IR-B1, milder consequence: during planning these surfaces answer "not running"
and permit the edit. A clear or restart landing there is overwritten by `setTimetable(staged)` and
then reverted to the **pre-edit borrow** when the run ends - the operator watched their clear
happen and finds the timetable back afterwards. A per-entry delay edit mutates a `TimetablePath` the
borrow holds by reference, so it survives, by luck rather than design. One predicate swap
(`isAutonomyBusy`) in four guards closes all of it; worth doing together with IR-B1.

### IR-C2. The A* queue is ordered on a map the search mutates

[HomeStaging.java:458](../../src/org/traincontrol/automation/HomeStaging.java) (the comparator:
`score.get(a) - score.get(b)` over `PriorityQueue<String>`), lines 499-507 (the relaxation: a
cheaper route to a known state overwrites `score` and re-adds the key).

`java.util.PriorityQueue` compares on demand. When the relaxation re-scores a key that is already
**in** the queue, the old entry's effective priority changes in place, which breaks the heap
invariant retroactively - polls can then return non-minimal states. Nothing is lost (elements only
leave via `poll`, and the closed set makes revisits harmless), so plans stay valid and termination
holds; what suffers is the order the `SEARCH_LIMIT` budget is spent in. That matters only because
NO_PLAN_FOUND is a budget statement, and the open half of `HS-B3` is exactly about trusting that
statement - a corrupted exploration order makes "no plan within the limit" mean slightly less.

Latent-trap C, not a behaviour bug. The standard shape fixes it in a few lines: enqueue immutable
`(key, f)` pairs and skip stale entries on poll, which is also what makes the queue's ordering
self-describing.

### IR-C3. The graph-mutation surfaces still read the planning window as idle

*Filed by the validation round's fresh pass, after the IR-B1/IR-C1 sweep landed.*

[GraphViewer.java:267](../../src/org/traincontrol/gui/GraphViewer.java), line 327 and line 527 (the
key handlers' `isRunning()` gates - placement, cut/paste, point deletion),
[GraphRightClickGeneralMenu.java:114](../../src/org/traincontrol/gui/GraphRightClickGeneralMenu.java)
(the menu block offering clear-locomotives),
[LayoutRightclickAutonomyMenu.java:137](../../src/org/traincontrol/gui/LayoutRightclickAutonomyMenu.java),
lines 156 and 174 (remove/edit locomotive), against
[Layout.java:1111](../../src/org/traincontrol/automation/Layout.java) (`getNeighbors` - an
**unsynchronized** iteration over the live adjacency list, which the planner calls from its worker).

The working-tree sweep moved the run-starting and timetable-editing surfaces to `isAutonomyBusy()`;
the surfaces that mutate the *graph* were not in it. During the planning window they all pass their
`isRunning()` gates, so the operator can place, cut, delete a point, or clear the run list while the
planner is deriving moves from a snapshot that no longer matches - and while `firstClearRoute` and
`connected` walk the live adjacency through the unsynchronized `getNeighbors`, so a structural edit
can also land a `ConcurrentModificationException` in the worker mid-iteration.

The consequences are bounded, which is why this is C and not B: the planner snapshots occupancy once
at entry, the runtime re-validates every move before driving it (a mismatch retries, abandons, and
reports "stopped early" - degraded, not wrong movement), and a worker exception unwinds through the
`finally` (timetable restored, flags cleared, buttons back) - though silently, with no dialog.
Historically these gates were sufficient because graph edits were refused while anything ran; the
planning window is the new state they cannot see. The fix is the same one-word predicate swap the
other two sweeps made, at six sites.

*Amended by the third-round pass:* the worker-side exposure is wider than `getNeighbors`.
`Layout.getPoints()` ([Layout.java](../../src/org/traincontrol/automation/Layout.java)) returns the
**live** `points.values()` view, which `HomeStaging.snapshot` iterates on the worker - so a point
created or deleted in this window can also fault or tear the snapshot itself, not only the route
searches. Both reads close with the same guard fix; no separate finding.

### IR-C4. The route editor reports success on a refused edit

[RouteEditor.java:1876](../../src/org/traincontrol/gui/RouteEditor.java) (the call - return value
discarded), [MarklinControlStation.java:1420](../../src/org/traincontrol/marklin/MarklinControlStation.java)
(`editRoute`, which now returns false on a missing route, a rename collision, or a failed re-add,
each with only a log line).

The cycle's `WR`-round work gave `editRoute` a boolean precisely so the model would not depend on
one dialog to protect its data. The dialog, in turn, still does not read it: a refused edit falls
through to `layoutEditingComplete` and the editor closes as if saved. The editor's own pre-checks
cover every deterministic refusal (blank names, rename collisions), so the false return is reachable
today only when a concurrent Central Station sync deletes or re-adds the route between the
pre-check and the call - narrow, which is why this is C. But it is the exact `PV-C7` shape: the
model now refuses correctly, and the user is not told. One `if` and one dialog.

### IR-C5. The timetable is handed to the EDT as a live view

[Layout.java:276](../../src/org/traincontrol/automation/Layout.java) (`getTimetable` - returns
`this.timetable`, a plain `LinkedList`),
[TrainControlUI.java:15382](../../src/org/traincontrol/gui/TrainControlUI.java) (`repaintTimetable`:
`timeTable.hashCode()` - which iterates every element - then a for-each over the same live list),
[TrainControlUI.java:15233](../../src/org/traincontrol/gui/TrainControlUI.java) (the graph callback,
which calls `repaintTimetable()` at the **beginning and end of every path**),
[Layout.java:243](../../src/org/traincontrol/automation/Layout.java) (`addTimetableEntry` - appends
from locomotive threads, under the Layout monitor the EDT does not hold).

With timetable capture on during an autonomy run - a supported workflow; capture exists to record
runs - every path start appends a captured entry from a locomotive thread while every other path's
start or end has just queued a repaint that iterates the same `LinkedList` on the EDT. Both
`AbstractList.hashCode` and the for-each use iterators, so the race lands as a
`ConcurrentModificationException` inside the `invokeLater` - Swing prints it to stderr and the
repaint dies, possibly after `setRowCount(0)`, leaving the timetable table blank until the next
callback repaints it. Transient and self-healing, no data damage: C.

The reason it is worth filing at all is the pattern: `4f9eb09` fixed exactly this - "hand out the
home map as a snapshot rather than a live view", same class, same reader (EDT), same writers
(locomotive threads) - and the timetable field a few lines above `homeStations` kept the live-view
getter. `getPoints()` (see the IR-C3 amendment) is the third of the family. The fix is the one
`getHomeStations` already demonstrates: copy under the monitor, or copy at the repaint's entry.

---

## IR-D1. Clean checks and suspicions that died correctly

- **Bundle audit, re-run from scratch at HEAD** (not a re-read of `WR-D1`'s): all eight bundles are
  byte-level ASCII-pure; key parity against the base bundle is exact, including the five new
  languages; and for all 484 keys fetched via `I18n.f` anywhere in `src/`, the placeholder set that
  *survives MessageFormat quote processing* matches the base bundle in every locale - zero
  mismatches. The French/Italian typographic-apostrophe convention holds throughout.
- **The fence coverage IR-B1 rests on:** every `loc.setSpeed` in `executePathInternal`'s milestone
  loop is inside an `isCurrentLayout()` guard - the multiplier adjust, the pre-arrival reduction,
  and the reversing branch - so a stale run commands accessories and functions but never movement.
  Read per site, not pattern-matched.
- **The `-1` sentinel** (`getUnfinishedTimetablePathIndex`): all four call sites are safe. Two clamp
  with `Math.max(0, ...)`; `timetableHasUnfinishedPaths` tests `>= 0`; and `restartTimetable`'s
  `== 0` comparison ([TrainControlUI.java:14618](../../src/org/traincontrol/gui/TrainControlUI.java))
  looks like the missed caller but is not - its third conjunct (`getExecutionTime() == 0` on entry 0)
  disambiguates the collapsed meanings the old 0 had, under both conventions.
- **`validatePathActuation`'s interrupt branch** returns "validated" on `InterruptedException`, which
  would let a locomotive depart unvalidated - but no code in `src/` calls `.interrupt()` on another
  thread (grepped), so the branch is unreachable today. It becomes real the day someone interrupts
  locomotive threads; the comment at the site already explains the intent.
- **`RemoteDeviceCollection.add`'s one-to-one enforcement vs duplicated-address locomotives:** the
  suspicion was that `names.values().removeIf(id-match)` could evict one of two locomotives sharing
  an address. It cannot: `locDB` ids are name-qualified (`MarklinLocomotive.getUID()` is
  `name + '_' + uid`), so duplicates never collide on the id, and the removal runs on a values view
  - a linear iterator walk, no hashing of drifted keys, so the July `removeIf` trap does not apply.
- **`Edge.isOccupied`'s rewrite** is behaviour-preserving: `Point.isOccupied()` is exactly
  `currentLoc != null`, so folding the two reads into one null-checked read changes the race, not
  the semantics.
- **`handleMisconfiguredPath` releases exactly what was locked:** `edgesLocked` is incremented before
  the configure attempt, so the sublist includes the edge that failed - which was `setOccupied`
  first. The theoretical self-eviction on a loop path (releasing the end point that is also the
  start) is unreachable: `bfs` marks visited on dequeue, so no returned path ends where it began.
- **The identity-hash change, swept for distinct-object equality reliance:** every
  `contains`/`remove`/map-key use of locomotives found operates on live objects from the same
  database; the sync and rename flows compare by `getIntUID`/`getName` explicitly. Nothing found
  relying on the old value equality across separately constructed objects.
- **The tail commits after the last recorded review round** (`65f8aff`, `3c7ff3e`, `4c5ea87`,
  `2849704`, `2e82f3a` and its revert `df74d4b`) were read in the final state: the warn-not-refuse
  home dialog, the menu cosmetic pass (point-name header, message dedup), the key-handler fixes
  (paste now honours the cut clipboard without demanding an active locomotive; Ctrl+X null-guards a
  hovered name whose point was deleted), and the clipboard-clearing revert, which restores the old
  clear-on-delete behaviour deliberately. Clean.
- **The capture-gap convention change** in `addTimetableEntry` (gap stored on the later entry) was
  checked against the dispatch loop's read (`ttp.getSecondsToNext()` before dispatching `ttp`):
  capture now matches replay, and previously saved timetables keep their stored values with
  unchanged replay semantics.

---

## Validation of the fixes (2026-07-28, working tree above `bbaca6f`)

Validated by the reviewer who filed the findings, reading each fix in the enforcing method. The
author's direction for IR-B1 was recorded first: *"Reload should still work, but cleanly."*

- **`IR-C2` - correct.** The queue now holds immutable `Scored` (key, f-score) pairs, compared with
  `Integer.compare`; the poll skips a stale entry when its carried score no longer matches the map
  (the cheaper duplicate is still queued and comes up in its own place), and the closed-set check
  precedes it, so the equal-score case is also covered. No unboxing hazard: every queued key was
  scored before `add`. The comment records the defect faithfully.
- **`IR-C1` - correct, and wider than filed.** All four named surfaces switched to
  `isAutonomyBusy()`, plus three the finding had not listed: timetable entry deletion, the
  route-activation toggle and its list. Re-grepped: no timetable- or run-adjacent surface in
  `TrainControlUI` still asks `isRunning()` as a *guard* (the remaining hits are the autosave skip,
  the exit flow, the settings helper and button-visibility branches - each read, none a mutation
  gate for this window; but see IR-C3 for the graph-mutation surfaces outside this class).
- **`IR-B1` - partly fixed.** Both guard arms are closed exactly as prescribed: Start Autonomy asks
  `isAutonomyBusy()`, and the reload's confirmation block now fires during planning too - still a
  confirmation rather than a refusal, which is right twice over (the stuck-layout recovery argument,
  and the author's direction). `isAutonomyBusy` itself also improved in passing: it now asks
  `hasAutoLayout()` rather than `getAutoLayout()`, so the question can no longer instantiate a
  Layout to answer itself - the `CR-C12`/`FCR-C1` family's trap, avoided.

  **What remains open:** the *confirmed* path. The confirmation's `stopLocomotives()` runs on the
  still-current Layout **before** the staging worker sets `running` - so it clears nothing - and the
  Layout-side chain in the original trace is untouched. Re-traced against the working tree: worker
  finishes planning on the retired Layout, its `isRunning()` re-check passes, the plan loads and
  executes, entry 1's accessories are commanded unfenced, the fence-abort strands the entry, and
  with two or more moves the sequential wait at
  [Layout.java:2595](../../src/org/traincontrol/automation/Layout.java) spins forever - nothing
  reachable from the UI calls `stopLocomotives()` on a retired Layout (Graceful Stop resolves
  `getAutoLayout()` to the new one). The `finally` never runs and `stagingFlowActive` never clears.

  **Fix shape for "works, but cleanly":** put the version fence where the wedge lives - the dispatch
  loop's wait loops (`while (this.running)` becomes `while (this.running && this.isCurrentLayout())`,
  matching the completion wait one screen below) - and, to also stop the spurious hardware commands
  and departure function, check `isCurrentLayout()` at `executePathInternal`'s entry beside
  `isValid()`. With those two, a confirmed reload lets the worker unwind through its `finally`:
  timetable restored, flags cleared, buttons back, nothing commanded from the retired graph.

## IR-D2. Validation-round clean checks

- **The `bbaca6f` test fixes are sound.** The `testReturnHomeOnRealLayout` precondition is the
  README's own pattern - a root change (assignable homes) invalidating an old test's premise, the
  assertion inverted into the property that replaced it (the loaded graph must be *plannable*, not
  already home). The `testRoutes` id-collision fix is a real generator defect: ids drawn from
  `nextInt(1000)` without joining the in-use set, so a collision silently evicted the earlier route
  from the id-keyed database - the accumulating `currentIds.add` closes it, and the comment is right
  that names never had the problem.
- **The two announced bugs overlap nothing filed here** - both are test-side, per the author and
  confirmed by the diff.
- **Closed hazard families, re-swept at the working tree:** zero `submit(new Thread`/
  `invokeLater(new Thread` sites remain (`PV-C1`/`FP-C3` family), and zero creating
  `getAccessoryByAddress` call sites outside its own definition (`FCR-C2` family).
- **Source-encoding suspicion, resolved by reading the build:** the new comment's typographic
  apostrophe (and the pre-existing ones, plus the arrow-key literals in `TrainControlUI`) are safe -
  `nbproject/project.properties` sets `source.encoding=UTF-8`, so javac reads what was written. The
  ASCII-escape discipline is a bundle rule, not a source rule; `HomeLocomotiveMenu`'s `’`
  escapes are the stricter style, inconsistently applied. Style note only.
- **Two narrow observations, recorded not filed** (both fail the "does happen" bar): a full
  autonomy-list repaint landing in the milliseconds between the staging worker enabling Graceful
  Stop and `running` turning true would re-disable that button for the whole run (the visibility
  branch at [TrainControlUI.java:15311](../../src/org/traincontrol/gui/TrainControlUI.java) asks
  `isRunning()`), and an application exit landing in the same commit-to-run gap would autosave the
  staged plan as the operator's timetable - both windows are milliseconds wide with no natural
  trigger inside them.

## IR-D3. Third-round clean checks (the code no earlier pass had read)

A pass over the regions neither this document's first round nor the recorded cycle had deep-dived,
looking for pre-existing defects. What was read, and what came of it:

- **`CS2File.fetchURL` has connect and read timeouts** (added this cycle), so a hung Central Station
  cannot strand the new background `refreshLayouts` workers. `Util.downloadFile` has its own pair.
- **`Util.getBackupPath` falls back to the working directory** exactly as the backup dialog's fix
  assumes - the dialog derives its path from an actual result, so the two cannot disagree.
- **Function bounds are symmetric:** `_setF` checks both ends of the range, as `getF`'s fix did;
  `MarklinLocomotive.setF` fans out to linked locomotives under its own monitor, consistent with
  `unlinkLocomotive`'s locking.
- **`MarklinRoute.executeAutoRoute`'s ignored return value is correct behaviour:** false means "the
  parked monitor is still alive and will pick up the re-enable" - the one caller
  (`applyAutonomyRouteActivations`) has nothing to do with it.
- **The blank-name trap in `newRoute`:** both model-level overloads `trim()` and then accept an
  empty name; the only UI entrance (`RouteEditor.RouteCallback`, line 1818) rejects blank first, so
  the path is unreachable today. Recorded as a trap for the next caller, the `CR-B3`/`C7`/`C15`
  class - a guard in the model would cost two lines.
- **Where the live-view pattern does not bite:** `getEdges`-driven graph rendering happens at build
  time on the EDT; `AutoLocomotiveStatus` reads points only behind a non-empty path list; the
  `linkedLocomotives` map is only handed out for iteration under the owning locomotive's monitor or
  from the EDT after the staged-swap fix. The two that do bite are filed (IR-C3 amendment, IR-C5).
- **Cosmetic, recorded only:** `PositionAwareJFrame` and a handful of bootstrap paths use
  `printStackTrace` rather than the model log - console-only diagnostics, all in
  startup/preferences code that predates the cycle.

---

## Comparison against the existing review record

The comparison was done after the pass, per the independence requirement. The short version: the
existing record (`CR`/`PC`/`IND`/`INT`/`FCR`/`RR`/`FP`/`PV`/`HS`/`WR`) already contains nearly
everything this pass turned up en route - the stuck-button classes, the capture self-append, the
reload fence and its completion-wait interaction, the rename-proposal family, the bundle disciplines
- all filed, fixed, and validated there. The deferred items (`HS-B3`'s exhaustion-vs-limit half,
`HS-B4`'s mixed-hardware conservatism, `FP-B3`, `FP-C6`) were confirmed unchanged at HEAD and are
not re-litigated here.

What this pass adds is three open findings, and all three are **new errors made as a result of the
cycle's own changes** - which was the question this review was asked to answer:

1. **IR-B1 is the signature error's next instance** - the ninth by the cycle summary's own table plus
   `WR-B3`. `178aa4c` invented `stagingFlowActive` because `isRunning()` cannot see the planning
   phase, wired it into the surfaces that start a return-home; `WR-B3`'s fix extended it to the
   surfaces that edit homes, and renamed the predicate `isAutonomyBusy` so no surface would rebuild
   half the disjunction. The two entrances that start or retire a *run* - reload and Start Autonomy -
   still ask `isRunning()` alone. The pattern's purest statement remains true: each sweep fixed the
   surfaces it looked at.
2. **IR-C1 is the same window's third face** (after the run-buttons and the home-edits): the
   timetable-editing surfaces. Its consequence exists only because the staging feature introduced
   the borrow-restore.
3. **IR-C2 is new code's own defect** - introduced with `HomeStaging`, latent because plan validity
   does not depend on exploration order.

New errors that the record already caught and closed - listed so the tally is in one place: `WR-C5`
(introduced by the `WR-C2` fix, one commit after a tree-wide sweep for exactly that defect class),
`WR-B3` (introduced by `d1f7008` one commit after the flag it needed was created), the
two-commit non-compiling tree (`178aa4c`/`d1f7008`, caught in `WR`'s fourth round), `HS-B5`/`HS-B6`
(introduced with the staging feature, caught by `HS`), and `RR`'s five C findings (all introduced by
the July cycle's own fixes). Verified present-and-fixed at HEAD by spot-check, not re-validated.

---

## Assumptions that needed the author's confirmation

Answered by the author on 2026-07-28; recorded here with the answers rather than rewritten, per the
one-status rule.

1. **Path integrity validation assumes the Central Station echoes every accessory command.**
   *Resolved: confirmed working on real hardware.* The design (default-on, `Accessory.isConfirmedAt`
   requiring an echo ever seen) rests on "setSwitched always transmits, so an echo is always
   expected"; the author reports it exercised and working.
2. **Is the reload's exemption from the busy-check a decision or an oversight?** *Resolved: the
   direction is "reload should still work, but cleanly."* That confirms the fix shape in the
   validation section - confirmation rather than refusal at the guard (done), plus a
   reload-tolerant worker via the version fence in the dispatch loop (the open half of IR-B1).
3. **`getAccessoryByName`'s prefix-swap fallback assumes accessory names are always canonical**
   ("Switch N"/"Signal N" plus protocol suffix). Not yet contradicted; no non-canonical naming path
   was found in this pass, but the importers are large. Stays an assumption.
4. **The two bugs the author identified** landed as `bbaca6f` and are test defects overlapping
   nothing here - confirmed by the author and by the diff (see IR-D2).

## Addressed 2026-07-28

All three findings fixed.  Each was re-read in its enforcing method first, and each held exactly as
described - including the trace in `IR-B1`, whose two guards were confirmed line by line before
anything was changed.

- **IR-B1** - both entrances now ask `isAutonomyBusy()`.  The reload guard deliberately stays a
  *confirmation* rather than becoming a refusal: `isRunning()` is also true when an
  `activeLocomotives` entry has been stranded, and reloading is the only in-session way out of that
  state, so refusing here would put the recovery path out of reach.  The comment above that guard
  already said so, and the fix would have broken it had the finding been applied literally.  With the
  swap, a reload during planning reaches `stopLocomotives()` before the swap, so the worker finds
  `running` false, drains its entries without dispatching, and unwinds through its `finally` -
  releasing `stagingFlowActive` instead of wedging on it.
- **IR-C1** - the four timetable surfaces swapped.
- **Six further instances of the same shape, not named in the report.**  The finding treated
  `isRunning()`-versus-`isAutonomyBusy()` as a property of six specific guards; it is a property of
  every guard whose body is "refuse with `errorWaitForActiveLocomotivesToStop`", because that message
  *is* the statement `isAutonomyBusy()` makes.  Reading the file for that shape rather than for the
  cited line numbers found `deleteTimetableEntry`, `executeTimetableActionPerformed`,
  `toggleSpecifiedRoutesMouseReleased` and `autoRouteListMouseReleased` as well - ten sites in total,
  all now on the one predicate.
- **IR-C2** - the queue holds `Scored(key, f)` entries and discards a stale one on poll, so re-scoring
  can no longer reorder what is already queued.  The comparator also moved from `a - b` to
  `Integer.compare`.

**A defect in the predicate itself, found while wiring it into more places.**  `isAutonomyBusy` read
`this.model.getAutoLayout()`, which *builds* a Layout when none exists - so asking whether autonomy
was busy on a graphless session made `hasAutoLayout()` start answering true.  Harmless where it was
called from before, since every one of those sites already had a graph, but the `IR-B1` fix puts it
behind `hasAutoLayout()` in the reload guard, where the question is asked precisely when there may be
no graph.  It now short-circuits on `stagingFlowActive` and otherwise asks `hasAutoLayout()` first.

**Not tested.**  Nothing here is reachable from the suite: the ten guards are UI event handlers, and
`IR-C2` changes the order a search explores in rather than what it returns.  The existing staging
tests still pin what the search produces, which is what `IR-C2` explicitly says is unaffected.

## Addressed 2026-07-28 (second round)

The `IR-B1` remainder and all three new findings.  The validation round was right that my first
`IR-B1` fix was incomplete, and right about why: I had reasoned that the confirmation's
`stopLocomotives()` would leave the worker with `running` false, without checking that
`executeTimetableInternal` sets `running = true` itself a moment later
([Layout.java:2538](../../src/org/traincontrol/automation/Layout.java)).  It clears nothing.  I
should have read the flag through to its next write rather than stopping at the call.

- **`IR-B1`, the remaining path** - fixed as prescribed, at the two places the wedge actually lives.
  The dispatch loop's wait is now `while (this.running && this.isCurrentLayout())`, matching the
  completion wait a screen below, so a retired Layout can no longer spin there forever; and
  `executePathInternal` refuses at entry when `isCurrentLayout()` is false, so a retired graph
  commands no accessories and fires no departure function - the speed writes were already fenced, but
  `configureAndLockPath` ran before any of them.
- **`IR-C3`** - all six graph-mutation gates on `isAutonomyBusy()`: the hover, click and key handlers
  in `GraphViewer`, the general menu's clear-locomotives block, and the track diagram's place, remove
  and edit items.
- **`IR-C4`** - the editor reads the boolean and reports a refusal instead of closing as though it had
  saved.  A new `route.ui.errorEditRouteFailed` in all eight bundles.
- **`IR-C5`** - the repaint copies the timetable at its entry rather than iterating the live list.

  **Not** in the getter, which is where the finding's first option pointed and where
  `getHomeStations` sets the precedent.  `deleteTimetableEntry`
  ([TrainControlUI.java](../../src/org/traincontrol/gui/TrainControlUI.java)) calls
  `getTimetable().remove(index)` - it mutates the list the getter hands back.  A snapshot there would
  have made entry deletion apply to a throwaway copy and silently stop working, with nothing in the
  suite covering it.  The reader-side copy closes the race with no such risk.  Worth recording as the
  reason the two live-view getters are not the same problem after all.

**A defect I introduced while fixing `IR-C4`, and the check that now catches it.**  The Danish value
for the new key went in carrying a literal `\xe6` - not a properties escape at all; Java's loader
reads `\x` as a bare `x`, so the word would have displayed as `vxe6ndret`.  Every existing bundle
check passed it, because `\xe6` is spelled in ASCII and the ASCII rule was the only thing looking.
`validate_all.py` now walks every escape in all eight bundles and accepts only `\uXXXX` with four hex
digits, `\n`, `\t`, `\r`, `\\` and an escaped space - verified against a probe carrying that exact
fault plus a malformed `\u12`, both flagged, with valid escapes left alone.

**Test coverage added where the fix is reachable from the suite.**

- `IR-B1` - two tests in `testHomeStaging`.  Parsing a second graph retires the first, because the
  version counter is static, which is exactly what a reload does.
  `testARetiredLayoutRefusesToRunAPath` asserts the retired Layout returns false and strands nothing
  in `activeLocomotives` - before the fix it locked the path, commanded its accessories, aborted at
  the fence and returned *true* with the entry left behind.
  `testARetiredLayoutStopsExecutingItsTimetable` builds a real staged plan of three moves, retires the
  Layout, then executes it: the `timeOut` is the assertion, because without the fence that call never
  returns.
- `IR-C2` - the swap test now asserts the plan is *exactly* three moves rather than at least three.
  A swap cannot be done in two, and A* with an admissible heuristic returns the shortest plan, so a
  queue handing back non-minimal states shows up as a longer plan.  The fixture already forces A*:
  neither locomotive can go home greedily, because each home holds the other.
- `IR-C4` - two tests in `testRoutes`, where `editRoute` had no coverage at all.  One pins both
  refusals *and* that neither route is lost - the delete-then-re-add ordering is the whole reason that
  check moved into the model.  The other pins that an unobstructed edit still succeeds, so the first
  is not passing merely because everything is refused.

**Still not tested**, and not reachable from the suite: the ten-plus predicate swaps are UI event
handlers, and `IR-C5` needs timetable capture on during a live run to produce the interleaving.  Both
were verified by reading the enforcing method only.
