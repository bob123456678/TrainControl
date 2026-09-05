# Acceptance by execution: the first run, the failure paths, and the thread nobody caught cheating

**Status:** open

**Prefix for citing these findings elsewhere:** `AC3` (confirmed unused - `grep -rn "AC3" docs/reviews`
returned nothing before this file existed).

**Reviewed:** branch `autonomy-diagram-r0`, on 2026-09-04 (file dated for the 2026-09-05 round), at
`357cdc40`.  The working tree was live while this ran: `cs2_sample_layout/config/autonomy/*` was
being rewritten by the running application, another reviewer (prefix `RG5`) was writing
`2026-09-05-fable-regression.md`, and the tree carried uncommitted additions to `LayoutGrid.java`,
`TrainControlUI.java`, `LayoutSandbox.java`, `testDiagramLooksRight.java` and
`testTheRebuildIsOnePass.java`.  Everything here was compiled and run against the tree as it stood,
those additions included.  `cs2_sample_layout/` was read by nothing in this pass and never written;
every probe ran in a fresh scratch working directory with `support.LayoutSandbox` opened over a
missing or fixture folder BEFORE any model was built, and `one.sh`'s live-layout fingerprint guard
stayed silent on every run.

**Method, stated up front.** Nine rounds have read this tree; the ground they held in common was
Adam's mature configuration and the autonomy feature.  So this pass ran what nobody had run: the
FIRST run (no LocDB, no UIState, no layout, no autonomy, virgin preferences - every salted pref key
is new in a fresh working directory), the second run over what the first one wrote, the documented
programmatic API surface against that empty world, the failure paths of the two state files
(corrupt at load, held open at save), and a violation-detecting `RepaintManager` under
production-shaped stimulation of the model-to-view callbacks.  Six temporary probe classes were
written to the session scratchpad (never into the repository), compiled against a scratch build of
the whole tree, run one JVM each under the same lock discipline as `one.sh`, and deleted.  Where a
finding depends on a mechanism, a control experiment pins the mechanism, not just the symptom.
Three existing test classes were run through `one.sh` - 15 tests, **Failures: 0, Skips: 0**.

---

## Verdict

**Ship it after one small fix - and the fix is four lines in a method whose own comment claims the
problem cannot happen.**  AC3-B1: when `LocDB.data` is corrupt, `restoreState`'s try-with-resources
leaks the `FileInputStream` (the `ObjectInputStream` CONSTRUCTOR throws, so the resource is never
assigned and nothing closes the inner stream), and the leaked Windows handle then makes the very
save that was built to heal this state - the keep-aside-and-write-fresh protection reviewed and
praised by three earlier rounds - fail its atomic move with "being used by another process".
Demonstrated by execution, and proven to be the handle by a control experiment: force finalization
and the same save succeeds.  The full UI self-heals by accident (GC closes the leaked handle during
the window build - measured, 3 of 3), so this bites the short-lived session: the programmatic API's
init-change-save shape fails 2 of 2, leaves the corrupt file in place, and the next run repeats it.
Everything else this pass touched held up under execution, and some of it impressively: a brand-new
user's first and second launches are clean end to end (demo layout extracted, empty databases
handled, exit save correct), the documented API surface degrades gracefully on an empty world in
23 of 25 probes, and not one Swing component was touched off the event thread across power,
feedback, locomotive, accessory, network-message, sync and rename stimulation - the marshaling
comments in `MarklinControlStation` all tell the truth (AC3-D1).

---

## Summary

| # | Severity | One line | Where |
|---|---|---|---|
| B1 | B | A corrupt `LocDB.data` leaks its file handle inside `restoreState`; the keep-aside protection then cannot replace the file, the session's database changes are lost with one log line, and short sessions repeat this forever | `MarklinControlStation.java:1688`, twin at `TrainControlUI.java:2565` |
| C1 | C | `writeAtomically` deletes its staging file when the WRITE fails but leaves it behind when the MOVE fails - the comment's "would otherwise accumulate" guard covers only half the exits | `Util.java:532-541` |
| C2 | C | `importRoutes` throws an undeclared `JSONException` straight through the programmatic API on malformed input; the one UI caller is fully guarded, an API caller gets an unchecked surprise | `MarklinControlStation.java:3552,3569` |
| D1 | D | What was executed and found sound - the first-run experience, the API-on-empty-world sweep, the EDT discipline - and what was not reached | - |

---

## AC3-B1 - The unreadable-database protection is defeated by its own leaked file handle

| | |
|---|---|
| **Severity** | B - session data silently lost, in a specific configuration (a corrupt database file plus a save before the first GC finalization pass); not A because the pre-existing file is still kept aside intact, and the full UI was measured to self-heal |
| **Disposition** | fixed - the stream has its own resource at both sites, the model's and the window's twin.  Pinned by `testAThrowingWrapperDoesNotHoldTheFileOpen`, which reproduces the failure with the old shape and passes with the new one. |
| **Confidence** | The failure and its mechanism were both established by EXECUTION, including a control experiment isolating the handle as the cause.  The UI-session manifestation was measured (3 runs).  What was NOT executed: a real OneDrive/antivirus hold on the file (a same-process leaked handle stands in for it), and the Backup Data menu path (read only). |

### What was run

A probe wrote 33 bytes of garbage as `LocDB.data` in a fresh working directory, opened a
`LayoutSandbox`, called `init(null, true, false, false, true)`, added a locomotive, and called
`saveState(false)` - the exact shape of a programmatic session, and of the exit path's
`model.saveState(false)` (`TrainControlUI.java:16568`) and the Backup Data item's live save
(`TrainControlUI.java:19137`).

Observed, in order:
1. `restoreState` logs `StreamCorruptedException`, sets `databaseLoadFailed`, returns empty - as designed.
2. The keep-aside works: `tc_backup/unreadable2026-09-04_19-05-03LocDB.data` appears - as designed.
3. The save then fails: **"Could not save database. LocDB.data: The process cannot access the file
   because it is being used by another process."**  `LocDB.data` is still the 33-byte corrupt file,
   and `LocDB.data.part` (2,880 bytes - the good new database) is left beside it.

Nothing else held that file.  The holder is `restoreState` itself (`MarklinControlStation.java:1688`):

```java
// try-with-resources ensures the stream is closed (avoids a file-handle leak on every load)
try (ObjectInputStream obj_in = new CustomObjectInputStream(new FileInputStream(dataFile)))
```

`ObjectInputStream`'s constructor reads the stream header and THROWS on a corrupt file - so the
resource variable is never assigned, try-with-resources closes nothing, and the anonymous
`new FileInputStream(dataFile)` stays open.  The comment is true for every file that loads and for
every file that fails after construction (`ClassNotFoundException` from `readObject`); it is false
for precisely the case the surrounding protection exists for.  Java's `FileInputStream` opens with
read/write sharing but not delete sharing, so plain writes to the file would succeed - it is
exactly `writeAtomically`'s `Files.move(..., REPLACE_EXISTING)` (`Util.java:541`), the mechanism
added to make this save safe, that the leaked handle blocks.

### The control experiment

A leaked `FileInputStream` is closed by its finalizer, and by nothing an application does.  So the
mechanism was pinned by running: corrupt file, init, save - **fails**; then three
`System.gc()`/`runFinalization()` cycles; save again - **succeeds** (2,331 bytes written).  The
handle, and nothing else, was the blocker.

### Where it actually bites, measured rather than argued

- **Full UI session:** a probe built the entire `TrainControlUI` over the corrupt database and then
  ran the exit save.  3 of 3 runs succeeded - the window build generates enough garbage that a minor
  GC finalizes the leaked stream first.  So Adam's own usage pattern will essentially never see this.
- **Short-lived / programmatic session:** 2 of 2 runs failed.  `AutomationAPI.md`'s init-then-drive
  shape with a save is the reliable victim.  And because the save fails, the corrupt file is still
  there next run: the sequence repeats on EVERY subsequent short session, each one losing its
  changes with one log line and adding another `unreadable<timestamp>` copy to `tc_backup`, until a
  human deletes `LocDB.data` by hand - which nothing tells them to do.
- The GC dependence also means the UI result is timing, not design: nothing anywhere closes that
  handle on purpose.

### The twin, and why its test stays green

`TrainControlUI.restoreState` (`TrainControlUI.java:2565`) has the identical shape, the identical
comment, and writes through the same `writeAtomically`.  A probe with a corrupt `UIState.data` and
a full window build could NOT reproduce the failed save (1 of 1 succeeded) - the same GC accident,
made near-certain here because the UI state is only ever saved after the whole window exists.  So
the twin is filed as the same structural defect with no demonstrated consequence, and
`ui.testUiStateIsNotLostWhenUnreadable` (run in this pass: 1 test, 0 failures, 0 skips) is not
evidence against this finding - it exercises the keep-aside, with the window build between load and
save quietly disposing of the handle.  There is no corrupt-load-then-save test at all on the
`LocDB.data` side.

### The fix shape (not applied - this review edits nothing)

Read the file into memory first and deserialize from a `ByteArrayInputStream` - the codebase
already does exactly this, for exactly this reason, in `decodeAutonomyJson`
(`TrainControlUI.java:1765`) - or declare the `FileInputStream` as its own resource in the
try-with-resources list.  Apply to both twins in the same commit, per this folder's own
grep-for-the-twins rule.  A red-first test is easy on the model side: corrupt file, init, save,
assert `LocDB.data` is no longer the corrupt bytes - it fails today without any GC games, because
the model path has no window build to hide behind.

---

## AC3-C1 - The staging file outlives a failed move

| | |
|---|---|
| **Severity** | C - litter beside the target, and only on a failure path |
| **Disposition** | fixed - the move is guarded and the staging file goes either way.  Asserted in the same test as B1, since the probe found them together. |
| **Confidence** | Observed by EXECUTION (the `LocDB.data.part` left behind in the B1 probe).  The claim about which exits are covered is from READING `Util.java:519-541`. |

`writeAtomically` deletes its `.part` staging file when the write into it fails, with a comment
explaining that a half-written file "would otherwise accumulate beside the target"
(`Util.java:532-539`).  The `Files.move` at line 541 sits outside that catch: when the MOVE fails -
which is the Windows failure mode, a target held open elsewhere - the staging file is left behind,
observed as `LocDB.data.part` in the B1 run.  It cannot accumulate past one copy (fixed name,
overwritten next attempt) and it holds the GOOD data, which is arguably the least bad file to leak.
Worth a `staging.delete()` on the move's failure path, or a deliberate comment saying the leftover
is intentional as a recovery artifact - either would do; the current state is neither.

---

## AC3-C2 - `importRoutes` throws undeclared unchecked JSON exceptions at the API surface

| | |
|---|---|
| **Severity** | C - the shipped UI cannot hit it; a programmatic caller gets an undocumented unchecked throw |
| **Disposition** | fixed - wrapped in an `IllegalArgumentException` carrying a translated message, rather than swallowed: returning an empty list would say "your file has no routes" about a file that is simply unreadable, and `importRoutes` would then delete every route the user has. |
| **Confidence** | EXECUTED: `importRoutes("not json at all")` and `importRoutes("[]")` both throw raw `org.json.JSONException` out of `parseRoutesFromJson` (`MarklinControlStation.java:3552`).  The UI caller census is from READING: the one caller (`TrainControlUI.java:22142`) wraps it in a catch and shows a proper dialog. |

The ordering inside `importRoutes` is right - it parses everything before deleting anything, so a
bad file cannot destroy the existing routes (verified by execution: the route list was intact after
both failures).  But the neighbouring surface tells a different story about intent: `exportRoutes`
declares `throws Exception`, `execRoute` on a missing name logs and returns, `parseAuto` on garbage
logs and degrades - and `importRoutes` declares nothing and throws `JSONException`.  In the
first-run API sweep of 25 probes this was the only surface that answered with an undeclared
unchecked exception rather than a logged refusal.  Either declare it or catch-and-log like its
siblings.

---

## AC3-D1 - What was executed and found sound, and what was not reached

| | |
|---|---|
| **Severity** | D |
| **Disposition** | record of coverage; nothing to fix |
| **Confidence** | Execution throughout unless marked otherwise. |

**The first run** (fresh working directory: no LocDB, no UIState, no layout, no autonomy, all
salted preference keys new; empty `LayoutSandbox`):
- Headless model init: missing `LocDB.data` correctly read as first launch, empty sandbox layout
  correctly reverts to the default source, failed station sync correctly reports "not connected".
  No crash anywhere.
- Full window build over that empty world: clean, no EDT exceptions (a default uncaught-exception
  handler was armed), the demo layout is extracted from `sample_layout.zip` and initialized, and
  `ui.saveState(false)` - the exit path - writes a valid `UIState.data`.  A brand-new user's first
  launch works.
- **The second run** over what the first wrote: "UI state loaded from file", clean again.  One
  oddity observed and judged sound: when the layout preference is empty but `sample_layout/`
  already exists on disk, the re-extraction logs a `FileAlreadyExistsException` per file and then
  proceeds to initialize the layout correctly - resilient in behaviour, noisy in the log.  Reaching
  that state at all took this probe's sandbox restore; the nearest real door is the
  revert-to-default-source path clearing the preference (`MarklinControlStation.java:465`), which
  is deliberate and logged.

**The programmatic API against an empty world** - the brief's "what does this method need that a
headless session does not have" question, asked 25 times by execution: `getLocList`,
`getRouteList`, `getLayoutList`, `hasAutoLayout`, `parseAuto` (garbage and `{}`),
`newMM2Locomotive` plus speed/functions/direction/instant stop, `lightsOn`, `allFunctionsOff`,
`getAccessoryState` (auto-creating), `setAccessoryState`, `newSignal`+`green`,
`newSwitch` DCC+`straight`, `getFeedbackState` on an unknown sensor, `isFeedbackSet`, `execRoute`
on a missing route (logs and returns - the guard added for exactly this shape works),
`exportRoutes` on an empty DB, `exportLocsToCSV` headless (AC2-C3's fix verified working by
execution: returns a 140-byte CSV, no NPE), `renameLoc`, `deleteLoc`, `saveState(true)` (backup
lands in `tc_backup/`), `stopAllLocs`, `stop`, `go`.  23 of 25 graceful; the two exceptions are
AC3-C2.  Also checked: a failed `parseAuto` leaves `hasAutoLayout()` true with an INVALID layout -
the exit-time capture is gated on `isValid()` (`TrainControlUI.java:2292-2296`, by reading), so a
bad JSON load cannot commit an empty layout over a stored configuration.

**Concurrency, instrumented rather than reasoned about:** a `RepaintManager` subclass recording
every off-EDT `addDirtyRegion`/`addInvalidComponent` with its stack, over the real fixture layout
(`test/test_layout` via sandbox) and the real window, stimulated from a non-EDT thread the way
production does - `go`/`stop`, `setFeedbackState`, locomotive speed/function/direction via the
API path, `setAccessoryState`, three fabricated CAN response messages through `receiveMessage`
(accessory, locomotive velocity, system go - the network reader's path), `syncWithCS2` from a
worker (the BusyDialog path), and `renameLoc`/`deleteLoc` from a worker.  **Zero violations.**
The marshaling comments on `repaintLoc`, `repaintSwitch`, `updatePowerState` and `feedbackChanged`
all hold under execution.  (The instrument recorded 319 violations during the probe's own window
construction - that is the TEST-HARNESS pattern of calling `setViewListener` directly from the
main thread, which `test/ui` classes share; production calls it through `invokeLater`
(`MarklinControlStation.java:3952-4085`), so those are the probe's, not the application's.)

**Test runs** (all through `one.sh`, one JVM each): `core.testAtomicWrite` 6,
`core.testLoadData` 8, `ui.testUiStateIsNotLostWhenUnreadable` 1 - 15 tests, 0 failures, 0 skips,
live-layout guard silent.

**Not reached, so the next pass can choose differently:** the route editor and the autonomy
diagram editing surface (`LayoutGrid`, `LayoutEditor`, `AutonomyEditorPanel` - all carrying
uncommitted edits during this pass, deliberately untouched); the timetable; the keyboard tab's
interactions (its construction with zero locomotives is covered by the first-run window build, its
behaviour is not); `LocIconCropDialog`; `NetworkProxy`'s reader internals and `CSDetect`; disk-full
(no safe way to fake it here - the held-open-file case stands in for external interference);
everything requiring a real Central Station or CS3; and the 27,000 lines of `TrainControlUI`
outside construction, save/restore, and the import handler read for C2.

---

## Footnote: what this pass left behind

Nothing in the repository except this file.  All six probe classes, their compiled output, the
scratch build of the tree, and the fresh working directories were deleted from the session
scratchpad after the runs.  The user-wide battery lock was taken before the probe runs; the pid written
into it belonged to a shell that exited between tool calls, so by the end of the pass the lock had
been (correctly) cleared as stale rather than released by its taker - the JVM-probe guard, which
matches on the testng classpath every probe carried, covered the runs throughout.  `one.sh` managed
its own lock for the test runs.  No preferences outside fresh-directory-salted
keys were written, and `LayoutSandbox` restored the layout preference in a `finally` on every
probe, all of which exited normally.
