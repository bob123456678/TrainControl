# Independent review of the last seven days

**Status:** open

**Prefix:** `ISD` — cite findings from here as `ISD-A1`, `ISD-B2`, and so on.

**What was reviewed, and when.** `f3eb2168..ac61dcec` — roughly 400 commits and 106k inserted lines —
read on 2026-08-24 as the code stands, not as the commits describe it. The reviewer was given no list of
claims to check and no prior findings: the brief was to look at the code fresh and say what is wrong
with it, in the order that matters for a railway that drives real trains.

**Method and its limits.** Read-only; nothing was built or run, and the reviewer says so where a finding
rests on reading alone. Several findings below are marked with the confidence given. Where this document
says *confirmed*, the reviewer traced the failing path end to end; where it says *likely*, they did not.
One finding was **withdrawn on verification** — see ISD-D1, which is the most useful entry here for
judging how much to trust the rest.

**Areas reported clean** are in ISD-D2. That list is part of the review, not padding: knowing which
parts were looked at and found sound is what makes the rest actionable.

---

## A — high. Wrong behaviour on the layout, or data silently lost.

| | Finding | Disposition |
|---|---|---|
| **ISD-A1** | One locomotive can be dispatched onto two paths at once | **Fixed** in `38ccbfc8` |
| **ISD-A2** | Switching layout source rips the diagram out from under moving trains | **Fixed** in `38ccbfc8` (= `FV-A2`) |
| **ISD-A3** | Execute Timetable and Return Home start trains under an open editor | **Fixed** in `38ccbfc8` |
| **ISD-A4** | A timetable leg that fails reports the run as completed | **Open** — `OB-072` |
| **ISD-A5** | The return-home planner does not know about `blockedBy` | **Open** — `OB-073` |

### ISD-A1 — the dispatch busy check has a seconds-wide hole

The only per-locomotive busy check is `activeLocomotives.containsKey(loc)`, and a locomotive joins that
map only **after** `configureAndLockPath` returns. That call throws every turnout and signal on the path
with a wait between each and then validates the actuation — seconds. For all of it the check answers
"not busy", so a second dispatch of the same locomotive passes every test and starts too.

Both threads then drive one physical train, and each one's completion unlocks points the other still
relies on. Two gestures a couple of seconds apart reach it: double-click a route in the Auto tab, then
dispatch another from the diagram's right-click menu — whose items do not re-check when clicked.

`takingPath` has been maintained since the lock-symmetry work and was only ever *counted*, for the
maximum-trains cap. Nothing ever asked whether a given locomotive was in it.

**Disposition: fixed.** The dispatch guard now refuses a locomotive that is already in `takingPath`.
This closes the seconds-wide window; a sub-millisecond race between the check and the claim remains
theoretically open and is not reachable by two human gestures.

### ISD-A2

See `FV-A2`. Reported independently by both passes, which is worth noting: the two reviewers had
different briefs and found the same unguarded door.

### ISD-A3 — the other two doors that start trains

`startAutonomyActionPerformed` refuses while an editor is open, quoting MT-135. Its two siblings do not:
`executeTimetableActionPerformed` and `requestReturnToHome` check power and `isAutonomyBusy`, and
`isAutonomyBusy` knows nothing about an editor. Both drive every train on the layout.

Open the editor, click the Auto tab, press Execute Timetable: trains run over a diagram being edited —
the exact MT-135 failure, one button to the left of the fixed door.

**Disposition: fixed.** Both refuse while an editor is open, before disabling their buttons so a refusal
cannot leave a control dead.

### ISD-A4 — a failed leg reports success

The dispatcher's `catch (Throwable)` stops all trains but never sets `abandoned`, so `return
!abandoned.get()` answers true and the plain-timetable flow shows no "stopped at entry N" dialog —
reproducing the symptom the code's own comment says was fixed. The staging flow is accidentally immune
via an unrelated cross-check. Every train stops and the application says it went fine.

**Disposition: open.** Filed as `OB-072`.

### ISD-A5 — staging cannot see the FR-001 restriction

The FR-001 refusal lives in `isPathClear` behind `isAutoRunning()`, and staging executes with running
true — but `HomeStaging`'s `canEnter`, `canRest` and impossibility scan never read `getBlockedBy()`, and
the runtime audit compares against `getPossiblePaths` at rest, where the clause is skipped.

If a home station is watched by a square that is another locomotive's home, the plan reports READY,
execution refuses that leg, and the run gives up after the retry limit and stops everything — the fleet
left half-staged.

It fails safe: no train moves wrongly. But partial execution is precisely what staging was built to
avoid, and the planner should refuse up front.

**Disposition: open.** Filed as `OB-073`.

---

## B — medium. Incorrect results, or crashes in specific configurations.

| | Finding | Disposition |
|---|---|---|
| **ISD-B1** | A page that fails to load has its whole setup pruned | **Open** — `OB-068` |
| **ISD-B2** | The timetable is an unrepaired holder of locomotive and station names | **Open** — `OB-069` |
| **ISD-B3** | Closing the app never asks the open editor about unsaved work | **Open** — `OB-070` |
| **ISD-B4** | A Central Station rename proposal bypasses the unusable-name guard | **Open** — `OB-074` |
| **ISD-B5** | Combining linked pages fabricates a setup, on a worker thread | **Fixed** in `38ccbfc8` (= `FV-B1`) |
| **ISD-B6** | `toStored` splits on the first colon; everything else uses the last | **Open** — `OB-071` |
| **ISD-B7** | A retired page id can be reissued | **Open** — recorded in MT-142 |
| **ISD-B8** | Legacy import writes homes without the one-home sweep | **Open** — `OB-075` |
| **ISD-B9** | The editor's Cancel reverts edits made from the main window | **Open** — `OB-076` |

### ISD-B1 — the highest-value item still open

`CS2File.parseLayout` deliberately skips a page that will not parse or whose file is missing — the
everyday OneDrive case, an unhydrated placeholder or a sync lock — and `readShared` is relaxed about it
too: "Absent is fine - the page may simply not be loaded."

But `AutonomySession.save()` reconciles against the pages that *did* load, and its only guard is
`isPageNumberingSuspect`. Every name, station, direction, length, signal pairing and caption on the page
that did not load is dropped as deleted track and written. Then the next page operation calls
`writeLayoutIndex` with the in-memory list, which drops the unloaded page from `gleisbild.cs2` and
retires its id — the page vanishes from the layout with its file orphaned on disk.

Three of the four doors that trigger that save discard the reconciliation report, so it is silent.

**The fix shape already exists in this codebase:** treat "an id in `pageNamesWhenWritten` with no loaded
page" exactly as suspect numbering is treated — save, but do not prune.

**Disposition: open.** Filed as `OB-068`. Recommended first.

### ISD-B2 — the fourth holder

The rename repair's own documentation enumerates "three things in a configuration hold a locomotive by
NAME". There is a fourth: the captured timetable rides in `globals`, and every entry names its
locomotive and names Points by name. On the next load `TimetablePath.fromJSON` throws on the
unresolvable name and the loader's all-or-nothing loop drops the **entire** timetable with a log
warning; `captureFromLayout` then writes the empty timetable back, permanently.

Station renames are worse: `setPointName`'s comment says "Nothing else has to happen", which is untrue
of the timetable. Only the active running configuration self-heals by capture.

**Disposition: open.** Filed as `OB-069`. Two fixes wanted: add the timetable to the repair, and make
the loader drop a bad *entry* rather than the whole list.

### ISD-B3, ISD-B4, ISD-B6, ISD-B8, ISD-B9

Filed as `OB-070`, `OB-074`, `OB-071`, `OB-075` and `OB-076` respectively, each with the failing
sequence. ISD-B6 is the same family as OB-067 and, like it, is dissolved for good by FR-013 rather than
patched.

### ISD-B7

Already recorded in MT-142's comments, where the weaker property that actually holds is stated and the
safety is pinned to `deletePage` having forgotten the settings first. The review adds one route not
considered there: an older `setup.json` returning via a OneDrive sync conflict.

---

## C — low. Cosmetic, dead code, or narrow edge cases.

| | Finding | Disposition |
|---|---|---|
| **ISD-C1** | Startup hangs for ever if the window fails to build | **Open** — `OB-077` |
| **ISD-C2** | A modal refusal dialog is raised from worker threads | **Open** — `OB-078` |
| **ISD-C3** | The event thread can block on the Layout monitor for seconds | **Open** — `OB-079` |
| **ISD-C4** | `Layout.isValid` is non-volatile; `layoutVersion` is a non-atomic RMW | **Open** |

ISD-C1 is an error path only, but its symptom is the worst kind — no window and no message. ISD-C3 is
the residue of work that moved `getPossiblePaths` off the EDT: three callers were left behind, and
hovering a locomotive panel during a manual dispatch freezes the window for the configuration phase.

---

## D — not defects.

| | Finding | Disposition |
|---|---|---|
| **ISD-D1** | *Withdrawn:* trains keep running when the app is closed with autosave unticked | **Withdrawn** — originally raised at B |
| **ISD-D2** | Areas swept and found sound | Closed |
| **ISD-D3** | Four comments that state the opposite of what the code does | **Open** — `OB-080` |

### ISD-D1 — withdrawn, and why it was wrong

Raised at B severity as part of ISD-B3: the graceful stop and the "exit with trains at speed?"
confirmation both sit inside `if (this.autosave.isSelected() && ...)`, so with autosave unticked,
closing the window while trains ran would neither stop them nor ask.

**The condition is unreachable.** Autosave is forced on and its checkbox hidden in the constructor —
`setSelected(true)` followed by `setVisible(false)` — so `isSelected()` is always true. Adam, on being
shown the finding: *"layout autosave should be default and forced these days. I believe we have hidden
the checkbox to turn it off."*

Recorded rather than deleted, per this folder's rule, because it is calibration data: the reviewer read
the nesting correctly and the reachability not at all, and a reader deciding how much weight to give
ISD-A1 or ISD-B1 should know that.

The nesting was still corrected — a guard that stops a railway should not be one hidden checkbox away
from not running — but it fixed no live defect, and the commit message that first described it
overstated the consequence. That overstatement is corrected in the code comment at the site.

### ISD-D2 — swept and found sound

- **Path lock/unlock symmetry**: the `f2818206` fix is complete — partial-lock release counts are right,
  all three failure exits drop the `takingPath` claim, and release is guarded against secondary failure.
- **Occupancy check-and-set** is atomic under one monitor, and block-aware occupancy is applied at all
  four sites. ISD-A1 was the only double-booking route found.
- **Reversing**: the terminus-only rule is consistently tiered across `pickPath`,
  `hasAutonomousDestination` and `isPathClear`, and matches the recorded rulings.
- **The `homeLoc`/`blockedBy` object migration**: renames mutate in place, deletes sweep everything, and
  the name↔object boundary is exactly `toJSON` and `parseAuto`.
- **Store rename/move/undo plumbing** — `renamePage`, `deletePage`, `moveTiles`,
  `snapshotPage`/`restorePage`, `repairOnDisk`'s double load, `renameConfiguration`'s move-then-rewrite,
  and the atomic writes throughout — correct as read, including the deep-copy and ordering traps the
  comments document.
- **Locomotive rename/delete propagation** reaches every holder found, except the timetable (ISD-B2).
- **The layout engine's concurrency**, the DiagramMonitor pipeline, the `syncWithCS2` split and the
  station-label plumbing are sound.

### ISD-D3

Filed as `OB-080`. Includes `Point.java` still documenting `homeLoc` as held by name above a field that
is now a `Locomotive`, and `Edge.isLockHeld`'s premise that "locks are symmetric" — which `Layout`
contradicts, counting 104 of 118 shipped lock relations as asymmetric. The safety argument survives by
another route; the stated premise should not be reasoned from.
