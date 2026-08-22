# Independent review of the week to 2026-08-22

**Status:** open

One Opus reviewer, given the seven days of work on `autonomy-diagram-r0` and told to look somewhere the
previous reviewers had not. It found eleven things worth acting on and recorded nineteen clean checks.

Prefix: **IR**. Severities per [README.md](README.md).

Reviewed at `532fa00e`. Everything below is fixed and committed unless its disposition says otherwise.

---

## Fixed

| # | Sev | What was wrong | Disposition |
|---|-----|----------------|-------------|
| IR-A1 | A | The `UIState.data` test held the only copy of the operator's file in memory | Fixed - the copy is on disk now |
| IR-B1 | B | Paste, fill and delete told the setup once per SQUARE: two graph rebuilds and a whole-setup disk write each | Fixed - once per gesture |
| IR-B2 | B | A bulk column move announced every landing square before `applyBulkPlan` did, defeating the label-sparing rule | Fixed - `execCopy` can keep quiet, like `delete` |
| IR-B3 | B | The stuck-train advisory restarted its clock at a sensor flicker | Fixed - the origin travels |
| IR-B4 | B | The advisory logged while holding the static lock every s88 event needs | Fixed - said off the lock |
| IR-C1 | C | `moveTiles` and `restorePage` rebuilt the graph twice | Fixed |
| IR-C2 | C | A page was matched by string prefix, so `Yard` swallowed `Yard: Upper` | Fixed - both helpers parse |
| IR-C3 | C | Undo and redo restored the setup before the diagram had moved | Fixed - the documented order |
| IR-C5 | C | An exception in `render()`'s queued body left the Edit button disabled for the session | Fixed |
| IR-C6 | C | A regression test could pass with its thread dead | Fixed - the precondition is asserted |
| IR-C7 | C | Nothing asserted the advisory's elapsed figure, so IR-B3 passed three tests | Fixed - and the first fix for it was vacuous too |

### IR-A1 - the only copy was in RAM

`testUiStateIsNotLostWhenUnreadable` overwrites the operator's real `UIState.data`, builds a whole
`TrainControlUI`, and restores from a `byte[]` held in a static field. Between those points there was no
copy on disk. Anything that ends the JVM in between - a hang in `invokeAndWait`, an out-of-memory, the
operator stopping the run, or **the battery runner's own orphan reaper, which `taskkill /F`s leftover
test JVMs before every run** - takes the only copy with it.

This file had already destroyed that file once, by a different route, the day it was written. The class
header even promised a scratch file that did not exist.

It now writes `UIState.data.reviewbak` before touching anything, names that path in the failure message,
and deletes it only after the restore has been verified byte for byte.

### IR-B1 and IR-B2 - the same sweep, twice more

Both come from FR-B1, last round's fix for "paste and fill leave the setup on squares they build over".
That fix was right and was applied one square at a time, inside the loop.

**IR-B1, the cost.** Each call ran `moveTiles`, which calls `touched()`, which rebuilds the whole graph -
a fresh `TileGraph` over every page, a `GraphReducer.reduce`, an `AutonomyBuilder` naming pass - and then
rebuilt it again (IR-C1). Then it wrote the entire setup to disk, atomically, every file of it. Select
all, copy, paste is three clicks, and `copySelection` takes the bounding box **including blank squares**:
on a 60x30 page that is 1,800 iterations, ~3,600 graph rebuilds and 1,800 whole-setup disk writes, on
the event thread, with repainting suppressed so nothing on screen moves while it happens. The layout
folder is under OneDrive here, so each write may also wake a sync client.

`delete()` was worse in one respect: `forgetCaptionsAt` returns whether it changed anything and the
caller ignored it, so deleting a square with no caption still wrote the whole setup.

All three now collect and tell once - the shape `applyBulkPlan`, `moveSelection` and the four shifts
already used.

**IR-B2, the correctness half, and this is FR-A1's shape a third time.** The bulk loops call
`delete(l, false)` so the clearing pass stays quiet and `applyBulkPlan` can speak once, with the moves
and the built-over squares together. But the next loop called `execCopy(destLabel, false)` - and FR-B1
had added an unconditional forget to that branch for the palette case. So every destination square was
announced twice: loudly first, with **no moves map**, then correctly.

A landing square announced with no moves map cannot be told apart from one built over by something
unrelated, so FR-C3's rule - keep a station's label when the station itself is what lands on it - could
not apply, and the label was dropped a moment before `applyBulkPlan` would have carried it. Move column
2 onto column 7 with a station at (2,5) whose name is drawn at (7,5), and the name goes. **That is the
exact symptom FR-C3 was written to cure, restored for the bulk path by the fix for FR-B1.**

`execCopy` now takes the same `tellAutonomy` flag `delete` was given, and the bulk loops pass false.
When `delete` got that flag, its sibling in the same two loops did not - which is the whole lesson.

### IR-B3 and IR-B4 - the advisory

**B3.** A sensor that makes and lets go restarts the wait, correctly. The restart carried a *remainder*
of the quota rather than the origin, so the clock began again with it: a train dispatched at 0:00 whose
sensor flickered at 4:30 was announced as stuck "after 0 minutes". The origin now travels through a
private overload.

**B4.** `waitedTooLongFor` is called from inside `synchronized(monitor)`, and that monitor is **static** -
one for every locomotive in the application. `Feedback._setState` must acquire it, and s88 events arrive
on a single thread. The only override went straight to a logging handler writing to `System.out`, which
blocks whenever its consumer does. Run from the IDE with output to the editor window, and a slow console
write stops arrival detection **for every train under way**. The base class's javadoc says the hook must
not block; the only implementation of it did. It now hands the message to a single daemon thread.

### IR-C7 - and the first fix for it was vacuous

Nothing asserted the elapsed figure, which is how IR-B3 survived three tests. The test written for it
asserted on the *sentence*, and the sentence rounds to whole minutes - so in a test that runs for two
seconds, every number rounds to zero and a build with the defect deliberately restored **passed**.

It now watches the number the hook is handed, through a test-local subclass overriding the protected
method, and fails against the mutant. Worth recording as its own item: a test written for a defect, run
once against the fixed code, and never checked against the broken one, is a test that proves the code
compiles.

---

## Not fixed, and why

### IR-C4 - `setAutonomyMode(null)` is not "nearly free", and my note in the last round said it was

`2026-08-22-adam-round.md` says of switching mode in place: "the mode half is nearly free… close to
wired". That was wrong, and the reviewer checked it properly.

Entering the mode does `newComponents.removeAll()`, which destroys the palette built in the constructor.
Leaving it only removes the autonomy panel - so the palette column would come back **empty**. Three
other entry-side changes are also not undone: the scroll-pane-to-stack `GroupLayout.replace` (the
findings list would stay across the bottom), the heading text, and a listener added to the lengths
toggle.

Left as it is, deliberately: the branch is dead today, nothing calls it, and wiring it is the AR-16 work
rather than a fix to be slipped in beside eleven others. **Anyone doing AR-16 must treat it as building
the teardown, not as connecting an existing one.**

### IR-C9 - three narrow ones in the locomotive base class

`blockUntilMotion` spins at full CPU for its whole 30-second window once interrupted, and reads a
non-volatile `speed` outside the monitor. `waitForAccessoryState` waits unboundedly for an
*acknowledgement*, against the rule stated for `waitForPowerState` - and its predicate reaches
`getAccessoryState`, which **creates a switch in the accessory database** when the address is unknown.
`_setSpeed` in the 14-argument constructor runs before `historicalOperatingTime` is assigned and would
throw for a non-zero speed, unreachable only because the one subclass hard-codes zero.

None is reachable from the application: all three are public API for user scripts, and nothing in `src/`
calls them. Recorded rather than fixed because each changes the behaviour of an API somebody's own
scripts may depend on, and that is Adam's call rather than mine.

---

## Checked and sound

Recorded so the next reviewer does not repeat the trip. The reviewer enumerated all 23 diagram-mutating
call sites in `LayoutEditor` and checked each against the store.

- **The four shift operations are correct**, including the ranges - verified against `LayoutDiagram`'s
  own bounds arithmetic - and the ordering, which computes the map before the diagram moves and tells
  the store after.
- **`growEdges`/`shrinkEdges` correctly tell the store nothing**: they add and remove at the right and
  bottom, so no square changes coordinates.
- **`clear()` and `delete()` correctly forget less than paste and fill do.** Both leave squares EMPTY,
  which is the one case reconcile can find on its own. Paste and fill leave them *occupied by something
  else*, which it cannot - which is why FR-B1 swept those three and not these two.
- **`moveSelection` is correct**, including blank selected squares, so a caption on blank space travels.
- **`rotate`, `rotateSelection`, `editText` and `editAddress` correctly do not touch the store.**
- **The whole EDT queue for opening the editor was traced** - `render()` queues, `setAutonomyMode` runs
  synchronously before the queued body, and `reveal` lands on the final grid rather than the first.
- **Nothing in the editor touches Swing off the EDT or the model on it.**
- **The content-pane wrap is complete**, `PositionAwareJFrame` included, and all three `GroupLayout`
  casts are now guarded (AR-10).
- **Every per-grid-rebuild registration is pruned**, and the prune actually fires because `LayoutGrid`
  removes the old labels before the new ones register.
- **The `editLayoutButton` protocol holds on all five exit paths**, and a Save does not roll back the
  setup it has just written.
- **Dummy Loc contamination: clean.** `MarklinRoute` reaches only the two-argument wait, which makes the
  advisory structurally unreachable. Both callers of the three-argument form are in the dispatch loop.
- **No lost wakeups on any of the four waits**, and no lock-order inversion.
- **A protecting signal whose tile has been deleted cannot reach the railway** - `reconcile` does not
  clean those values, but `protectingSignalNames` drops them one layer up.
