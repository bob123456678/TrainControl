# The four package sweeps, closed

`C1-C29` from [`2026-08-17-whole-project-review.md`](../reviews/2026-08-17-whole-project-review.md).
They were filed as four bundle rows - `automation/`, `marklin/`, `base/`, `gui/` - and never triaged,
so "four open items" was really twenty-nine - and the count was wrong three ways, which is recorded
here rather than quietly corrected (MON-C9).  The prose said twenty-nine three times, the adjudicating
commit's subject said twenty-eight, and the tables below hold thirty-two rows, because several filed
findings turned out to be bundles of their own.

**Worse, and the reason this matters: `WP-C19` filed SIX sub-defects and the sweep dispositioned four.**
Two had no row, no id and no disposition anywhere, inside a bundle the document declared closed.  Both
were adjudicated on 2026-09-08:

- **C19d, the case-sensitive command names.**  Fixed.  `Feedback` was matched through `toLowerCase()`
  and every other command with `equals`, so "feedback 12,1" parsed and "emergency stop" did not, in the
  same file typed by the same person.  All command names accept any case now; the exporter still writes
  the canonical spelling, so no existing route changes meaning.  `testACommandParsesWhateverCaseItIsTypedIn`
  pins it, including that Feedback stays forgiving - the direction this ruling did not go.
- **C19f, the orphaned javadoc.**  Already fixed, and recorded at the head of the source review: it was
  "reattached in passing, as its method was being deleted anyway".  Not a missing adjudication so much
  as one filed in the prose instead of the table.

**All twenty-nine have now been read against the code and
ruled on**, and every live one worth fixing is fixed.

**Method.** Each finding's stated mechanism was looked for in today's source. A finding is Cancelled
only when the mechanism is *demonstrably* gone - the guard is there, the call is the non-creating one,
the two statements are inside one monitor - and not merely when I could not find it.

**On red-before-green, corrected.** An earlier version of this line claimed every behavioural fix below
was proved by a test seen failing first. That was false for five of the fourteen, and a reviewer
checked it: **C6, C7, C19b, C22 and C24 shipped with no test at all**, as did the mid-run reversal
change. C7 is the one that matters - what the application believes a train's speed to be is what the
length and blocking rules run on, and a revert to integer division is invisible to the battery. The
others are a concurrency fix, a menu-state fix, a stat guard and a clipboard guard; defensible to ship
unpinned, but the sentence should have said so rather than claiming a discipline it did not follow.
Fixes made after that check carry their tests.

**What the sweep was worth.** Twelve were already gone, eleven of them fixed in the five weeks since by
people who did not know the finding existed. Fourteen are now fixed. Six are ruled and left, with
reasons, and one - C26 - turned out to be a feature nothing had ever tested. **And the largest was much
bigger than filed.**

## The one that mattered - C13

Filed as "evaluating a route condition registers a phantom accessory". True, and the same call sits in
the window's two keyboard paint paths, the second of them in a loop over all sixty-four keys - so
**opening a keyboard page registered sixty-four switches**, whether or not anything was wired to them.

Adam's real database currently holds **`Switch 1` through `Switch 2048` in DCC, contiguous** - the
entire DCC address space - plus 257 of the 320 MM2 addresses. Nobody makes 2048 switches by hand; that
is this defect, already realised.

The fix stops it recurring: a new `getAccessoryStateIfPresent` gives the same answer and creates
nothing, and all three read sites use it. Clicking a key still creates the switch it is about to
command - creation belongs on the path that commands, not on the one that draws.

**The rows already there stay.** Adam, 2026-09-07: *"there's no reason to delete an accessory, since we
just track their actuations. Them being in the database doesn't otherwise harm anything."* An accessory
record is a counter, not a claim that something is wired up, so a spurious one costs nothing. That also
retires the question of a delete API - there is nothing to delete.

The one consequence that survives is for tests: **every in-range address is taken**, so a test needing
an address nobody has registered must search for one and assert it found one, past the protocol range,
where nothing on a track diagram can be. `testAdvancedRoutes` does exactly that.

## Fixed (14)

| | What it was |
|---|---|
| **C3** | `deletePoint` and `deleteEdge` now refuse while the railway is running or being planned - the guard `renamePoint` already carried. The menu greying is not this guard: items grey when the popup opens and fire when it is clicked. |
| **C5** | `parseReleaseVersion` finds the version by its digits. `split("v")[1]` threw on a name with no "v" and, worse, returned "iew " for "TrainControl Preview v3.0.0" - which compares as older, so a real release would be hidden with nothing logged. |
| **C6** | `Point`'s id allocator is atomic. |
| **C7** | A received speed is rounded, not truncated. |
| **C13** | Above. |
| **C16** | A group closes its own bracket instead of leaving it to the last child, so an empty one no longer opens a bracket it never closes. |
| **C18** | The comment claiming the four shift methods are unused is corrected; all four are on the editor's right-click menu. A comment saying a method has no callers reads as permission to change it freely. |
| **C19a** | `urls != null && urls instanceof Map` - one thing said twice, neither clause saying it. |
| **C19b** | A "ran today" record is no longer written for a locomotive commanded with the track power off. Its sibling branch already asked; this one did not. |
| **C19c** | `convertSecondsToHMmSs` takes milliseconds. Documented rather than renamed - the method is public, and a silent rename is a worse trap than a name carrying a correction. |
| **C22** | Clearing a page only releases the active locomotive if it was on that page. |
| **C23** | A throwing sync no longer leaves Sync and the functions menu greyed for the session. Reported as the failure code the method already knows how to explain. |
| **C24** | Cancelling a label edit no longer resets the clipboard and disarms the active tool. The re-add stays unconditional - skipping it too would risk a blank square to fix a clipboard. |
| **C26** | Not the reported defect - the clipboard write is deliberate - but the drag it belongs to could be killed by a clipboard another application was holding. See below. |

## Cancelled - the mechanism is gone (12)

**C2** (check and insert share one monitor, and the count includes `takingPath`), **C8**, **C9**,
**C11**, **C12** - all fixed since the sweep, three carrying comments naming the exact fault - **C14**
(the interrupt is re-asserted once on the way out, not inside the loop), **C17** (every method of
`RemoteDeviceCollection` is synchronised and every getter returns a copy; the class comment says so),
**C20**, **C21**, **C25** (no unresolvable-ID rebind; `RouteEditorFrame` checks the route list before
binding), **C28**, **C29**.

**C28 needs a correction to what I said in the first pass.** I called it a confirmed crash on the
strength of reading the dialog. It was found independently as **UC-B1** and fixed at the model layer:
all three writers of `numF` - both constructors and `setAddress` - clamp the stored function numbers,
with tests. The dialog needs no guard of its own, and adding one would be a second rule to drift from
the first.

## Live, and deliberately left (6)

| | Ruling |
|---|---|
| **C1** | `runLocomotive` has no pacing floor. Needs `minDelay = maxDelay = 0` *and* a locomotive with no available path, and the symptom is heat rather than a wrong railway. |
| **C4** | `HomeStaging.blockedSensors(Map state)` ignores its parameter. The behaviour is **correct** - unknown occupancy is a start-state fact and no move of ours clears it - so the defect is a signature promising something the body does not do. |
| **C10** | The CS2 flat-file importer's three-way pause placement. Real per the CS3 importer's own comment, but it needs a three-way turnout imported from a CS2 flat file, and there is none to test against. |
| **C15** | The feedback waits retry by recursion. It needs a sensor flapping within every `minDuration` window for long enough to exhaust a stack - a broken sensor, which would be reported as one. |
| **C19e** | `LayoutDiagramComponent` rotates about `img.getWidth(null)/2` on an image that may have come from `getScaledInstance`, whose width can read -1 until it loads. Real, and **not something to change without looking at the result** - a wrong fix here is a visibly broken diagram, which is worse than an intermittent one. |
| **C27** | `saveState` iterates `locMapping` off the EDT. Compensated by the handler's `RuntimeException` catch, which the finding itself concluded; the item is reported as unsaved and the backup completes. |

## C26 - not a defect, and now covered

Adam, 2026-09-07: *"c26 is deliberate, that is the dragging functionality you implemented on the
keyboard. There should be tests to ensure no regression there."*

There were none - copy, cut, move and swap across fifty pages, and not one line covered, which is how a
reviewer came to read a deliberate feature as a side effect. `ui.testTheKeyboardDrag` now covers all
four gestures, and `testEditorSurfaceRules` pins the clipboard write itself so the next reviewer sees
why it is there.

**Writing them found a real one.** `setContents` throws `IllegalStateException` whenever another
application is holding the Windows clipboard - ordinary and transient - and the write runs *after* a
cut has emptied the source key and *before* the paste can put the locomotive down. Uncaught, a moment
of bad luck deleted a mapping. Four of the five new tests failed on it the first time they ran, and not
one of them is about the clipboard. The write is now guarded: the name on the clipboard is a
convenience, the drag is the feature.

## On the structural items

`DD-C1` (decompose `TrainControlUI`) and `DD-A1` (the store's eleven collections declared in six
places) are **not started**, deliberately. Both are multi-session refactors of code every feature
touches, and starting one at the end of a long session - after a day in which two of my own "fixes"
were worse than the defect and one duplicated a rule that already existed - is how a working railway
gets broken for a week. They are real, they are not urgent, and they want a session of their own with
nothing else in it.

*Written 2026-09-07.*
