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

Findings use the A/B/C/D convention in [README.md](README.md).

---

## Status

| # | Finding | Severity | Status |
|---|---------|----------|--------|
| IR-B1 | The two run-starting entrances missed by the `WR-B2`/`WR-B3` sweep: autonomy reload and Start Autonomy gate on `isRunning()` alone, and a reload landing in the staging planning window permanently wedges the staging worker - `isAutonomyBusy` then reads true for the rest of the session | B | **Open** |
| IR-C1 | The timetable-editing surfaces (clear, restart, per-entry delay, capture toggle) also read the planning window as idle; edits made there are silently undone by the borrow-restore | C | **Open** |
| IR-C2 | `HomeStaging.astar` orders its priority queue on a mutable score map; re-scoring a state already enqueued breaks the heap invariant retroactively. Correctness survives (closed set), but the search burns budget out of order - worsening the open `HS-B3` NO_PLAN_FOUND ambiguity | C | **Open** |
| IR-D1 | Clean checks: bundle audits re-run from scratch, the fence coverage IR-B1's trace rests on, the `-1` sentinel's call sites, and six other suspicions that died on reading | - | Recorded |

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

## Assumptions that need the author's confirmation

1. **Path integrity validation assumes the Central Station echoes every accessory command.** The
   design (default-on, `Accessory.isConfirmedAt` requiring an echo ever seen) rests on "setSwitched
   always transmits, so an echo is always expected". If any real configuration fails to echo -
   protocol quirk, CS firmware difference - every autonomous departure fails validation, each
   locomotive is stopped at its start, and the one-time alert fires. Has this run against real CS2
   *and* CS3 hardware, not only simulation?
2. **Is the reload's exemption from the busy-check a decision or an oversight?** The reload
   deliberately warns-not-refuses on `isRunning()` so a stuck layout stays recoverable; IR-B1 assumes
   the *planning-window* pass-through was not similarly deliberate. If it was, the finding's fix
   shape changes (the worker must be made reload-tolerant instead).
3. **`getAccessoryByName`'s prefix-swap fallback assumes accessory names are always canonical**
   ("Switch N"/"Signal N" plus protocol suffix). If any import path can produce a differently named
   accessory, the fallback's `startsWith` could rewrite a name it should not. No such path was found
   in this pass, but the importers are large.
4. **The two bugs the author has just identified** are not yet visible to this review. If either is
   IR-B1/IR-C1/IR-C2, the entries above should be updated with the fix commit rather than renumbered.

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
