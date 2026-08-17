# Whole-project review, outside the autonomy-diagram feature

**Prefix for citing this document: `WP`.**

Reviewed at `bf11048` on branch `autonomy-diagram-r0`, 2026-08-17.

**All three A findings were fixed the same day**, each with a test written and seen to fail first; the
orphaned javadoc noted in `WP-C19` was reattached in passing, as its method was being deleted anyway.
Everything else below is open. Section status tables carry each finding's disposition.

## Scope and method

Four reviewers, deliberately pointed **away** from `automationui/` and the `Autonomy*` GUI classes.
That feature had eleven passes during the autonomy-diagram cycle and is audited separately in
[2026-08-17-disposition-audit.md](2026-08-17-disposition-audit.md) (`DA`); spending a fifth reviewer
there would have re-read the best-read code in the repository.

| Pass | Scope | Result |
|---|---|---|
| 1 | `automation/` - Layout, Point, Edge, TimetablePath, HomeStaging (~7,400 lines), read in full | 0 A, 1 B, 6 C, 9 D |
| 2 | `marklin/` - CAN protocol, control station, route/locomotive/accessory, `marklin/file/` | 0 A, 3 B, 6 C |
| 3 | `base/` - Locomotive, Accessory, Route, RouteCommand, NodeExpression, LayoutDiagram* | 2 A, 1 B, 7 C |
| 4 | `gui/` excluding `Autonomy*` - threading, save/restore, editors, keyboard, capture | 1 A, 4 B, 10 C |

The three A findings and the two most consequential B findings were re-verified by hand against the
source before being recorded here. Which findings were independently verified and which rest on the
reviewer's trace alone is marked per finding, because that is the calibration data a later reader
needs.

**None of these are regressions from this branch.** All three A findings predate the autonomy-diagram
work; two of them predate the July 2026 cycle. Defects introduced by this branch are `DA`'s subject,
not this document's.

---

## A - High

| | Finding | Status |
|---|---|---|
| A1 | Route conditions are not repaired when a locomotive is renamed | **Fixed 2026-08-17** |
| A2 | A case-only page rename deletes the page file | **Fixed 2026-08-17** |
| A3 | Route-editor capture collapses commands for different locomotives | **Fixed 2026-08-17** |

### A1. Route conditions are not repaired when a locomotive is renamed, so condition-gated routes silently stop firing

`base/Route.java:152` (`locomotiveRenamed`) iterates only `this.route`, the command list. A route's
condition tree is a separate field - `MarklinRoute.conditions`, a `NodeExpression` holding
`TYPE_AUTO_LOCOMOTIVE` commands that reference locomotives **by name** - and `MarklinRoute` does not
override `locomotiveRenamed`. Nothing repairs it.

Failure scenario: build an s88-triggered route with the condition "only fire when BR 218 is at s88
50", then rename BR 218. `MarklinControlStation.renameLoc` sweeps `locomotiveRenamed` over every
route - its own comment names routes as one of the two by-name stores needing repair - and the sweep
misses `getConditions()`. Afterwards `Route.evaluate` does `getLocByName(oldName)`, gets null, and
returns false forever. The route's monitor thread logs "condition failed" on every trigger and never
executes. A route setting switches and signals ahead of a moving train silently stops doing so.

Verified by hand: `locomotiveRenamed`'s body, and that `MarklinRoute` declares `conditions` and
`getConditions()` without overriding the repair. Route *renames* are unaffected -
`isConditionCommand()` excludes `TYPE_ROUTE`.

This is the by-name-versus-by-reference trap already recorded for this codebase. Routes were
correctly identified as a store needing rename repair; the repair was written for the command list
and the condition tree was not part of the question being asked.

### A2. `LayoutDiagram.saveChanges` deletes the page file on a case-only rename

`base/LayoutDiagram.java:438-447` writes `newFilePath`, then unconditionally deletes
`originalFilePath` when a filename was supplied and this is not a duplicate. There is no
`Files.isSameFile` check.

Failure scenario, on Windows or macOS: rename a page from "Main" to "MAIN". The GUI guard at
`gui/TrainControlUI.java:15505` is `List.contains` - case-sensitive string equality - so "MAIN" is
not recognised as colliding with "Main" and the rename proceeds. `Files.newBufferedWriter("MAIN.cs2")`
opens the existing `Main.cs2` on a case-insensitive filesystem and writes it; `Files.delete("Main.cs2")`
then deletes that same file. Renaming is only offered for local layouts, so there is no Central
Station copy to fall back on. The page is gone and the rewritten index names a file that does not
exist.

Verified by hand: both the delete with no same-file check and the case-sensitive guard that lets the
rename through. The exact-duplicate and trailing-space vectors are genuinely blocked - `contains`
catches the first and the dialog trims - so the case-change vector is the only one open, and it
loses data with no message.

### A3. Route-editor command capture collapses commands for different locomotives

`gui/RouteEditor.java:252` takes the dedup key as everything before the **first** comma. That is
correct for accessory lines, which are `name,setting`. Locomotive lines are `prefix,name,value`
(`RouteCommand.toLine`), so `locspeed,Loc A,50` and `locspeed,Loc B,40` both key to `locspeed` and
the `LinkedHashMap` keeps only the last.

Failure scenario: a route drives two locomotives. The user turns on "Capture commands" and clicks one
turnout. `appendCommand` re-filters the entire text area, and Loc A's speed line disappears from the
middle of the text. Saving persists the truncated route.

Verified by hand. Worth noting for whoever fixes it: the comment immediately above this code records
a recent correction to this same function for three-way pair ordering. Someone was working here and
the locomotive case was not part of what they were checking - which is the argument for fixing the
key rather than adding a second special case beside it.

---

## B - Medium

| | Finding | Status |
|---|---|---|
| B1 | Dispatch at speed 0 blocks an automation thread forever, or invalidates the whole layout | Open |
| B2 | NPE on a null `locIdCache` silently drops every locomotive state update | Open |
| B3 | A duplicate route in an imported JSON leaks a live, invisible s88 monitor | Open |
| B4 | The four device databases are bare `HashMap`s crossed by three thread families | Open |
| B5 | A redundant power-on event discards accumulated running time | Open |
| B6 | Cancelling the bulk enable/disable dialog throws and skips the refresh | Open |
| B7 | "Address is free" is answered from the duplicate list, so one existing user reads as free | Open |
| B8 | Every editor click runs the tool - palette clicks throw, grid clicks forge undo entries | Open |
| B9 | `RouteEditor` is realised on a raw thread, off the EDT | Open |

### B1. Dispatch at speed 0

`automation/Layout.java:3135` (`executePathInternal`) validates path, locomotive and occupancy but
never validates `speed >= 1`, and two UI dispatch sites pass `loc.getPreferredSpeed()` - which is 0
for a locomotive placed on a node without the speed dialog (`GraphRightClickPointMenu` prompts for
nothing, and the `fromJSON` default only applies to locomotives already in the file).

Semi-autonomous dispatch then locks the path, fires the departure functions, commands speed 0, and
blocks forever in `waitForOccupiedFeedback` on a sensor a stationary train will never reach.
`activeLocomotives` stays non-empty, so `isRunning()` is stuck true and autonomy start, the
simulation toggle and locomotive edits are all blocked until the graph is reloaded; the thread leaks.
Through full autonomy start the same locomotive instead throws, and the catch **invalidates the
entire layout** and stops every locomotive - one 0-speed locomotive turns Start into "configuration
invalid, must reload".

Traced end-to-end for both consequences. The train never moves, so there is no physical hazard - that
is the whole reason this is B and not A.

### B2. NPE on a null `locIdCache`

`marklin/MarklinControlStation.java:1931` reads `locIdCache` with the field still null. The cache is
built only at the end of a *successful* `syncWithCS2()`, by the 3-arg `newLocomotive`, or by
delete/rename/re-address; the restore path `newLocomotive(MarklinSimpleComponent)` does not build it.

Failure scenario: LocDB.data restores locomotives, then `syncWithCS2()` fails - CS web server down or
firewalled - while the CS is still reachable over UDP, because `NetworkProxy.setModel` starts the
reader thread unconditionally. Someone drives a locomotive from the CS screen; the response reaches
`locMessageProcessor`, which NPEs on the null cache. The exception is captured in the executor's
discarded `Future`, so **every** locomotive update is dropped in silence and the UI shows stale
speeds and directions until a later sync succeeds. `exec()` null-checks the same cache for the
simulate path, so the nullability is already known here.

### B3. Duplicate route in an imported JSON leaks a live monitor

`marklin/MarklinRoute.java:135` - the complete constructor calls `executeAutoRoute()`, starting a
monitor thread as soon as `enabled && hasS88()`, before the object is accepted into any database, and
`fromJSON` uses this constructor with `auto` taken from the file.

Failure scenario: the user imports a hand-edited routes JSON in which two entries share a name or ID
and have `"auto": true` plus an s88. Both objects start monitors during the parse loop; `newRoute`
refuses the second and logs "notAdded" - but its monitor thread stays alive with `enabled == true`,
watching the sensor forever. Every trigger fires the phantom's commands - turnouts, locomotive speeds
- in addition to what the user believes exists, with no UI handle to disable it short of a restart.

The CS2 and CS3 importers are safe: they use the simple constructor with `enabled` false, and the
restore path checks name and ID before constructing.

### B4. The device databases are unsynchronised across three thread families

`base/RemoteDeviceCollection.java:17-20` backs all four device databases with plain `HashMap`s, and
they are crossed by the CAN executors, the EDT, and background sync threads with no common lock.
Concretely: `feedbackDB.add` from the feedback executor on every unseen contact racing
`feedbackDB.delete`/`add` in `syncLayouts` (a background thread holding only `layoutRefreshLock`);
`locDB` mutation from the EDT in `deleteLoc`/`renameLoc`, neither synchronised, racing `locDB.getById`
on the `locMessageProcessor` thread; and `saveState` iterating all four while the executors insert.

The sharp end: if the resulting `ConcurrentModificationException` fires inside `saveState`'s
iteration during autosave or exit, it escapes the method's try - which covers only the file write -
and the database is **not written**, losing customisations silently.

Recorded as a genuine gap rather than a style preference because the author clearly considered this
class of problem elsewhere: `locIdCache` is volatile with a synchronised rebuild, and the tile sets
are `ConcurrentHashMap`-backed. The bare backing maps look like the case that was missed, not the case
that was decided.

### B5. A redundant power-on discards accumulated running time

`base/Locomotive.java:387-391` - in `notifyOfPowerStateChange(true)`, when `speed > 0` the method
resets `lastStartTime` without checking whether `powerState` was already true. Runtime is credited
only at the next stop or power-off, so the interval from the real start to the redundant GO is never
recorded. `MarklinControlStation.receiveMessage` calls this for every locomotive on **every** GO
message, transitioning or not - so pressing Go on the CS while a locomotive has been running thirty
minutes drops those thirty minutes from the persisted statistics. The method already tracks
`powerState`; an early-out inside the lock closes it.

### B6. Bulk enable/disable NPEs on Cancel

`gui/TrainControlUI.java:12664-12681` - cancelling the search-string dialog returns null,
`!"".equals(null)` passes, and `r.getName().contains(null)` throws. The worker thread dies on a
silent stack trace and the sync and refresh are skipped.

### B7. "Address is free" is answered from the wrong list

`gui/AddLocomotive.java:464` checks `getDuplicateLocAddresses()`, which contains only addresses used
by **two or more** locomotives. An address used by exactly one existing locomotive is therefore
reported free, and the user assigns a conflicting decoder address believing it vacant.

### B8. Every editor click runs the tool

`gui/LayoutEditor.java:405-417` - `beginDrag` always creates `dragWindow` on press and `endDrag`
always executes on release, with no drag threshold. A plain click on a **palette** tile has
`lastHoveredX/Y` of -1, so `execCopy(null, ...)` builds a component at (-1,-1) and
`addComponent` throws `IndexOutOfBoundsException` - uncaught, on the EDT, on every palette click;
only `IOException` is caught. The feature appears to work because `receiveClickEvent` re-arms the
tool afterwards. A plain click on an **occupied grid** tile cuts and immediately re-drops the tile
onto itself, which is harmless to the diagram but calls `snapshotLayout()` first - so merely clicking
pushes undo entries, clears redo, and makes the "exit without saving?" prompt appear when the user
changed nothing.

### B9. `RouteEditor` realised off the EDT

`gui/TrainControlUI.java:12577` and `:13771` construct, pack and `setVisible(true)` the `RouteEditor`
JFrame on a raw `new Thread(...)`, and its `routeContents` Document is then written from the EDT by
the capture path. Failure is probabilistic - intermittent paint corruption, or a rare deadlock when a
capture event races window realisation - which is why it is B. It stands out in a codebase that
otherwise marshals rigorously.

---

## C - Low

| | Finding | Status |
|---|---|---|
| C1-C6 | `automation/` - pacing, ceiling race, missing guards, dead parameter | Open |
| C7-C12 | `marklin/` - speed truncation, dead guard, MFX fallback, three-way pause, headless paths | Open |
| C13-C19 | `base/` - phantom accessories, interrupt spin, recursion, stale comments | Open |
| C20-C29 | `gui/` - regex filter, ghost mappings, off-EDT enable, silent rebind | Open |

**`automation/`.** **C1** - `runLocomotive`'s loop has no pacing floor, so with `minDelay = maxDelay
= 0` (explicitly supported) a locomotive with no available path spins hot: `pickPath` walks every
point pair running BFS, then delays 0 ms, repeat. `pacedWait` was added to fix exactly this for the
timetable loops and was not applied here. **C2** - the `maxActiveTrains` ceiling is checked inside
`isPathClear` under the Layout monitor, but the `activeLocomotives` insertion it counts happens later
and outside that monitor, so two threads can both see `size == max-1` and both depart; one extra
train, each on an individually validated locked path. **C3** - `deletePoint` and `deleteEdge` lack the
`isRunning() || isStagingInProgress()` guard `renamePoint` has; the current caller is gated at
popup-open but the action fires at click time, so starting autonomy from another window while the
popup is open runs the unguarded method mid-run. **C4** - `HomeStaging.blockedSensors(Map state)`
ignores its parameter entirely; the behaviour is correct but the signature promises per-state
evaluation. **C5** - `Util.parseReleaseVersion` does `split("v")[1]`, throwing on a release name with
no "v"; the caller's catch was not traced. **C6** - `Point`'s static `++id` is unsynchronised,
unreachable today because every creation path is single-threaded.

**`marklin/`.** **C7** - received speed is integer-divided by 10, so a fine-step speed set by another
controller displays truncated and `syncFromState` re-sends it slightly slower. **C8** -
`CS2Message.getSubCommand`'s length guard tests `data.length` (always 8 for a parsed message) instead
of `length`; a DLC-4 system frame would read a padding byte and be handled as `CMD_SYSSUB_STOP`,
flipping the model's power state off. No evidence the CS ever emits such a frame - a latent guard
defect, not observed behaviour. **C9** - the missing-`adresse` UID fallback subtracts the base for DCC
and multi-units but not for MFX; wrong code with an unproven trigger. **C10** - the CS2 flat-file
route importer attaches the operator's pause to the first command of a three-way pair, so the tuned
pause lands *between* the two coil drives and the pair-to-next gap falls back to 200 ms, under the
350 ms margin used elsewhere. This is the placement the CS3 importer's own comment calls out as the
defect it was fixed for, and the class comment's claim that the CS2 importer cannot recognise a
three-way is too strong - `stellung >= 2` identifies the pair. **C11** - `exportLocsToCSV` NPEs with
no view, reachable only through the headless API. **C12** - the headless IP prompt crashes on retry
because try-with-resources closed `System.in`.

**`base/`.** **C13** - `Route.evaluate`'s accessory conditions call `getAccessoryState`, which creates
a switch when the address is unknown, so evaluating a condition registers a phantom accessory that
then persists; `toCSV` deliberately uses the non-creating lookup for exactly this reason and the
evaluate path was missed. **C14** - every wait loop re-asserts the interrupt and loops, which becomes
a 100% CPU spin if anything ever interrupts another thread; nothing does today. **C15** - the feedback
waits retry by recursion, so a sensor flapping within every `minDuration` window grows the stack.
**C16** - `NodeGroup` text rendering breaks for shapes only hand-written JSON can build. **C17** -
`RemoteDeviceCollection` read cross-thread by automation wait loops; self-heals on the next notify,
which is why this is C and B4 is B. **C18** - a stale comment claims the shift methods are unused;
all four are wired to the editor right-click menu. **C19** - assorted: an always-true `instanceof`;
`_setSpeed` stamping a "ran today" date with track power off; `Conversion.convertSecondsToHMmSs`
taking milliseconds despite its name; case-sensitive `Route`/`Emergency Stop` prefixes where
`Feedback` is insensitive; `getImage` calling `getWidth(null)` on an async-scaled image; an orphaned
javadoc above the wrong method.

**`gui/`.** **C20** - `LocomotiveStats`' filter builds a regex from user text, so typing `(` - and
locomotive names shown include "(Multi-unit)" - throws per keystroke and the filter silently fails.
**C21** - `deleteLoc` clears mappings only on the current page, leaving ghost buttons elsewhere for
the session. **C22** - `doClearCurrentPage` nulls `activeLoc` unconditionally, killing keyboard control
of a locomotive that was not on the cleared page. **C23** - `doSync` re-enables menu items off the EDT
and never re-enables them if the sync throws, having no finally. **C24** - cancelling a text edit
still re-adds the component and resets the clipboard, disarming an active tool. **C25** - a route tile
whose stored route ID no longer resolves silently rebinds to the first route in the database on OK.
**C26** - dragging a locomotive button writes its name to the system clipboard as a side effect, and
plain Delete destroys a pending copy target. **C27** - `saveState` from the backup worker iterates
`locMapping` while the EDT mutates it; compensated, because the handler catches `RuntimeException`
and reports the item as unsaved, so the backup fails loudly. **C28** - `GraphLocAssign` throws when a
stored function number exceeds the locomotive's current `getNumF()`; suspected, not verified against
the model's clamping. **C29** - `RouteEditor`'s locomotive combo NPEs if the selected locomotive is
deleted while the editor sits open.

---

## D - Not defects, and checks that came back clean

**`automation/`, verified clean.** `TimetablePath.secondsToNext` is milliseconds despite its name -
every producer and consumer was checked and they agree, so only the name lies. `Layout.bfs` marks
`visited` on dequeue, which looks like the classic defect and is documented as deliberate because the
`excludePaths` enumeration depends on reaching a point by several routes. `unlockPath` in atomic mode
cannot free track under another active path: locking edge Y also locks its lock edge X, so no path
containing X could have started. `HomeStaging.firstClearRoute` not applying
`passesThroughReversingStation` matches the runtime and is deliberate - yard staging can *require*
passing over one berth to reach another. `validatePathActuation`'s contract holds: every CS echo,
including a no-movement echo, sets `actuationConfirmed` before `notifyAll`. Double-pressing Start
cannot spawn two driver threads. Manual actuation under a locked route is not silently allowed -
`LayoutLabel` requires operator confirmation. `configureAndLockPath` holding the monitor through
per-accessory 150 ms sleeps is a recorded tradeoff, not a defect, though it will read as a UI hang
under a many-accessory path.

**`marklin/`, verified clean.** Every CAN encoding checks out - UID bases, speed scaling, direction
bytes, function encoding, big-endian packing, and the symmetric accessory setting between
`setSwitched` and `parseMessage`. `CS2Message` parsing is correct on sign-extension, the length-nibble
clamp, the raw-buffer copy that prevents aliasing of the reader's reused buffer, and hash
normalisation. Duplicate-packet suppression only drops consecutive identical responses, and every
scenario constructed where a legitimate repeat is dropped leaves state already identical. No consist
deadlock is possible: `canBeLinkedTo` forbids linking a locomotive that has members, so lock order is
strictly head-to-member. `saveState` stages through `Util.writeAtomically`. The sample layout's
quirks - first page without `.id`, an element without `.id`, `artikel=-1`, the absent
`magnetartikel.cs2` - are all handled.

**`base/`, verified clean.** wait/notify pairing is complete throughout: every waiter's condition
writer notifies the monitor the waiter holds. The lock-ordering claim in `notifyOfPowerStateChange`
holds - no path takes `speedMonitor` then a locomotive lock. `fromTextRepresentation` can throw
`EmptyStackException` on unbalanced input, but both call sites sit inside `catch (Exception)` blocks
that show the invalid-expression dialog. `RouteCommand`'s JSON round trip drops only a key that is
never serialised through that path. `writeLayoutIndex`'s numbering is self-consistent with
`parseLayoutIndex`'s fallback. Right-nested operator precedence in the condition parser is deliberate
and pinned by `normalize`'s contract.

**`gui/`, verified clean.** All three exit-time save paths go through `Util.writeAtomically`, and no
save-clobber race exists between exit and backup. Keyboard mapping persistence under AZERTY and
QWERTZ is symmetric, because `applyKeyboardType`'s queued body runs before `setViewListener`'s.
Model-callback to UI paths all marshal to the EDT or use concurrent structures, and the repaintLayout
path guards against the mid-rebuild empty layout database. `LayoutEditor.refreshGrid`'s re-entrancy
handling is correct. GraphViewer's mutations are gated on `isAutonomyBusy`.

**No suspected compile errors** were found in any file read across the four passes.

---

## What the passes missed

**`gui/` is half-read.** Roughly 10,000 of `TrainControlUI.java`'s 18,000 lines were not read: the
timetable UI, the CS3 web integration, the preferences dialog handlers, function-tab painting, and
most `*ActionPerformed` bodies between roughly lines 5000-10700 and 14200-18000. Several smaller GUI
classes were grep-skimmed for threading and parse hazards only, not read line by line. Given that the
one A finding and four of the nine B findings came from the half that *was* read, the unread half is
the largest remaining gap in this repository.

**The lead reviewer's sub-passes did not report back to it.** Passes 2, 3 and 4 returned their results
independently rather than through the reviewer that spawned them, which is why the consolidated
lettering here was done by hand rather than by that reviewer. Nothing was lost, but the lead's own
summary claimed those areas were uncovered when they were not - a reminder that a fan-out's
consolidation step is itself a place findings can vanish.

**Protocol conformance cannot be settled from source.** Two findings (`WP-C8`'s DLC-4 system frame,
`WP-C10`'s `sekunde` semantics) turn on what real hardware emits, and were assigned severity on the
assumption that the code's own invariants are the best available evidence. A capture from a live CS2
or CS3 would settle both.

**No dynamic verification.** Nothing here was compiled or run - per the standing constraint that
builds and tests happen in NetBeans. Every claim rests on reading, and the three A findings were
re-read by hand for that reason.

**`base/` and `marklin/` were read for their own correctness, not for their interaction.** The consist
`setSpeed` fan-out's lock ordering across linked locomotives sits exactly on that boundary and was
explicitly left by both passes.
