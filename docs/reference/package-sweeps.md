# The four package sweeps, adjudicated

`C1-C29` from [`2026-08-17-whole-project-review.md`](../reviews/2026-08-17-whole-project-review.md).
They were filed as four bundle rows - `automation/`, `marklin/`, `base/`, `gui/` - and never triaged,
so "four open items" was really twenty-nine. Every one has now been read against the code as it
stands.

**Method.** Each finding's stated mechanism was looked for in today's source. A finding is Cancelled
only when the mechanism is *demonstrably* gone - the guard is there, the call is the non-creating one,
the two statements are inside one monitor - and not merely when I could not find it. Where the fix
carries a comment naming the same defect the sweep found, that is said so.

**What the sweep was worth.** Twelve of twenty-nine are closed, and eleven of those twelve closed
because someone fixed them in the five weeks since - mostly without knowing the finding existed. Six
are live and cheap. Seven still need a read. That ratio is the argument for triaging a bundle row
rather than carrying it: a third of it was already done.

## Closed - the mechanism is gone (12)

| | Why it is closed |
|---|---|
| **C2** | `configureAndLockPath` now calls `isPathClear` and `takingPath.put` inside one `synchronized (this)`, and `trainsUnderway()` counts `takingPath` as well as `activeLocomotives`. The check-then-insert window the finding described is closed. |
| **C6** | Fixed 2026-09-07 - `AtomicInteger`. |
| **C7** | Fixed 2026-09-07 - `Math.round(speed / 10.0)`. |
| **C8** | `CS2Message.getSubCommand` now tests `length`, with a comment naming the exact fault: `data.length` was always eight, so the guard could not fire, and a short system frame read as `CMD_SYSSUB_STOP`. |
| **C9** | The MFX branch of the UID fallback has the base correction, commented as "the same correction the DCC branch below has carried all along". |
| **C11** | `exportLocsToCSV` guards the null view, commented for the headless case. |
| **C12** | The headless IP prompt is deliberately not try-with-resources, with a comment naming the `NoSuchElementException` on retry. |
| **C14** | The interrupt is captured and re-asserted once on the way out, not re-asserted inside the loop. The spin shape is gone. |
| **C20** | No `Pattern` or regex construction in `LocomotiveStats` today. |
| **C21** | The mapping rework removed the per-page clear. |
| **C22** | Fixed 2026-09-07 - the active locomotive is only let go if it was on the cleared page. |
| **C29** | No matching `getSelectedItem()` in `RouteEditor`. |

## Live, and worth fixing (6)

Ranked by what a user would actually meet.

| | What happens | Cost |
|---|---|---|
| **C28** | `GraphLocAssign.updateValues` calls `arrivalFunc.setSelectedIndex(loc.getArrivalFunc() + 1)` against a combo built from `getNumF()`. A stored function number at or above the locomotive's current function count throws `IllegalArgumentException` and the assignment dialog fails to open. **Confirmed, not suspected** - and the `trainLength` combo four lines below already has the clamp these two lack, which is this codebase's most reliable tell. | Two lines |
| **C13** | `Route.evaluate` still calls `getAccessoryState`, whose miss branch calls `newSwitch`. Evaluating a route condition against an address that is not in the database **creates** that accessory, and it persists. `NodeExpression` was fixed for the same fault and its comment says "this display path" - the evaluate path was not swept. | Small |
| **C3** | `deletePoint` and `deleteEdge` still lack the `isRunning() || isStagingInProgress()` guard. `renamePoint` has it, and carries a comment explaining precisely why a second caller would inherit graph corruption. One site fixed, two siblings missed. | Small |
| **C23** | The off-EDT half is fixed - both `setEnabled` calls are inside `singlePass` now, commented. The **no-`finally`** half is not: if `syncWithCS2` throws, the worker thread dies and Sync and the functions menu stay greyed for the rest of the session, with nothing on screen to say why. | Small |
| **C5** | `Util.parseReleaseVersion` does `split("v")[1]`. The caller catches `Exception`, so a release named without a "v" degrades to "could not fetch update info" rather than crashing - but it degrades silently and permanently, and the trigger is a naming choice, not a fault. | One line |
| **C17** | `RemoteDeviceCollection`'s two `HashMap`s are plain and read cross-thread by the automation wait loops. Self-healing on the next notify, which is why it graded C. | Small, if done at all |

## Live, and deliberately left (3)

| | Ruling |
|---|---|
| **C1** | `runLocomotive` has no pacing floor. Real, but it needs `minDelay = maxDelay = 0` *and* a locomotive with no available path, and the symptom is heat rather than a wrong railway. Worth doing inside a session that is already in that loop; not worth opening one. |
| **C4** | `HomeStaging.blockedSensors(Map state)` ignores its parameter - the body reads `this.start`. The behaviour is **correct**: unknown occupancy is a start-state fact and no move of ours clears it. The defect is the signature promising something the body does not do, which will mislead the next reader. Cosmetic until it isn't. |
| **C15** | The feedback waits retry by recursion. It needs a sensor flapping within every `minDuration` window for long enough to exhaust a stack, which is a broken sensor and would be reported as one. |
| **C10** | The CS2 flat-file importer's three-way pause placement. Real per the CS3 importer's own comment, but it only bites layouts with three-way turnouts imported from a CS2 flat file, and Adam's does not have one to test against. |

## Not yet read (7)

`C16` (NodeGroup rendering for hand-written JSON), `C18` (a stale comment about the shift methods),
`C19` (six assorted small ones - an always-true `instanceof`, a runtime date stamped with power off, a
mis-named `Conversion` method, case-sensitive prefixes, `getWidth(null)` on an async image, an orphaned
javadoc), `C24` (cancelling a text edit re-adds the component and resets the clipboard), `C25` (a route
tile with an unresolvable ID silently rebinding to the first route), `C26` (drag writing to the system
clipboard; Delete destroying a pending copy target), `C27` (`saveState` iterating `locMapping` off the
EDT - compensated, per the finding).

These are the ones whose mechanism I could not locate quickly enough to be sure whether "not found"
meant fixed or meant renamed. **Saying they are stale without that check is the failure mode this
document exists to avoid**, so they stay open and unadjudicated rather than closed on a guess.

## What is yours to rule on

1. **The six live ones - do them, or leave them?** All six are under an hour together. C28 is a
   crash the user meets by opening a dialog; C13 writes junk into the accessory database. I would do
   those two whatever you decide about the rest.
2. **The seven unread ones - worth a pass?** Half a session, and on this sample it will close more
   than it opens.

## On the structural items

`DD-C1` (decompose `TrainControlUI`) and `DD-A1` (the store's eleven collections declared in six
places) are **not started**, deliberately. Both are multi-session refactors of code every feature
touches, and starting one at the end of a long session - after a day in which two of my own "fixes"
were worse than the defect and one duplicated a rule that already existed - is how a working railway
gets broken for a week. They are real, they are not urgent, and they want a session of their own with
nothing else in it.

*Written 2026-09-07.*
