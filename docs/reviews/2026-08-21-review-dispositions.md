# Two reviews, and what was done about them

Two Opus reviewers ran on 2026-08-21 against `1efa3b9a`: one over the last 40 commits looking for
regressions, one independent pass over the whole project. Everything below was re-verified against the
source before it was acted on. Two of the second reviewer's own candidates did not survive their
verification and were never reported (`CS2File.sanitizeFilename` does handle path separators;
`MarklinAccessory.parseMessage` applies state before `updateTiles`).

Findings are grouped by what happened to them, not by which reviewer found them.

---

## Fixed

### Data loss and silent corruption

**Shift Up / Shift Left from the last row or column moved the whole diagram and told the setup
nothing.** `LayoutDiagram.shiftUp` normalises any start row past `sy - 2` to the first row, so hovering
the bottom row — the natural gesture for "take this empty row away" — shifted the entire page up by one
and destroyed row 0, while the map handed to the autonomy setup came out empty. Every station, name,
length and pairing on the page stayed on the square it used to be on, and nothing was dropped by the
next reconcile either, because every square still had a tile. The whole page's setup was attached to
the wrong tiles, silently. Both gestures are now refused on the last row and column, where they never
had a meaning. `shiftDown`/`shiftRight` grow the page first and were never affected.

**Cancel in the diagram editor did not take back the autonomy edits it had already written to disk.**
Every gesture that moves track saves the setup as it goes — it has to, or a setup that lags the diagram
is one reconcile away from being deleted — but Cancel only re-read the *pages* from disk. A cancelled
drag left a station recorded on a square the track had been moved away from. The editor now takes a
whole-setup snapshot when it opens (`AutonomyCompanionStore.snapshotSetup`) and puts it back on Cancel.

**A facing did not travel with the tile it was about.** Directions are keyed by square *and* route —
`page:x,y#state,index` — and the mover matched whole keys, so it never matched one. Every facing stayed
behind and was dropped by the next reconcile. New `moveSuffixedKeys` handles the shape. This one was
mine, from the tile-move commit, and manual test 1 would have caught it.

**A tile dragged onto a square did not take that square's setup away.** The mover only overwrote a
landing square when the source had something to overwrite it with, so plain track dragged over a
station left the station's name, signals and restrictions attached to a square that now holds plain
track — and reconcile never tidied it up, because the square still had a tile. `moveTiles` now forgets
every landing square that is not itself a source, references included.

**Discarding an edit kept a signal pairing made since the load.** `clear()` was missing
`stationSignals`, while its sibling `clearShared()` had it — and the reader only puts what the file
holds, so an entry the file said nothing about was never overwritten. A pairing the user cancelled
survived the discard and the next save wrote it to disk, after which a real signal was thrown on real
hardware for it.

**Renaming a configuration destroyed it if the save that followed failed.** The old file was deleted at
edit time and the new one is only written by the next save; `load()` rebuilds the list by scanning the
folder, so anything that stopped that save destroyed the configuration outright. The file is now moved
and rewritten under the new name, with both halves rolled back on failure.

**Two differently-named configurations could share one file.** Uniqueness was checked on the name; the
filename is sanitised, and sanitising is many-to-one. Saving wrote both to one file, one over the
other, and the next load came back with one of them gone. Both doors — create and rename — now check
the resulting filename.

**An unreadable import emptied the setup on its way to failing.** The shared half was cleared before
the merged object was parsed, and the parse uses type-strict accessors. A bundle with the wrong type in
it wiped every name, station, length and pairing while reporting that the import had failed. The state
is now snapshotted and put back. `load()` was hardened against exactly this and says so; the import
path shares its reader and was not.

**The track-diagram page index was truncated in place.** `gleisbild.cs2` is the index of every page
there is and was the one file in the project not written atomically: a failure part way through left a
header naming no pages, every page file on disk unreferenced, and the next start reconciling the whole
autonomy setup away against an empty layout. It goes through `Util.writeAtomically` now, like its
neighbours.

**`setup.json` gained an array shape without a version bump.** A station with two signals is written as
an array, which the previous release reads with a string accessor — throwing an unchecked exception
*after* `load()` has emptied the store, leaving a blank setup one save from being permanent. The
version is now written per FILE rather than per build: 2 only where some station really does carry more
than one signal, so the great majority of setups stay readable by the previous release and the ones
that are not are refused cleanly.

### Wrong behaviour the user would meet

**Every mark in the command table was acted on twice.** `actOnRowMarks` registers a mouse listener that
dispatches on the value under the pointer, and it was called twice on the same table so the duplicate
column would get its renderer. One click on the trash deleted two commands — the second listener
re-read the same cell, found the row that had just shifted up into it, and deleted that as well — and
the up/down arrows did nothing at all, because the second listener moved the row back. The renderer is
now applied to extra columns without a second listener.

**A signal row's setting could be flipped by one stray click.** An accessory row upgraded to a Signal
row for display kept the word "turn", which is not in the signal dropdown, so the combo fell back to
its first entry — green. Clicking that cell and clicking away committed it: a route that put a signal to
danger quietly became one that cleared it. The setting is now translated with the kind.

**A condition mixing AND and OR was flattened.** `ConditionOutline.write` recursed into a child at the
parent's depth whatever word the child used, so `A and (B or C)` came out as one level reading
`A and B or C` — two different joining words at one depth, which this class's own rule refuses. Such a
route opened flagged red and could not be saved until the user restructured a condition they never
wrote, and the reading the Test button evaluates said `(A and B) or C` instead. Cross-operator children
now go one level deeper.

**Backspace over the diagram removed a locomotive instead of cycling tabs.** The diagram gesture is
asked first whenever the pointer rests on a square, and it claimed Backspace. Pressing it to leave the
Track Diagram tab took the locomotive off the square under the pointer and saved it. Delete only, now.

**Ctrl+X on a selection left nothing to paste.** `delete()` resets the clipboard, which wiped the copy
`cutSelection` had taken one line earlier. The clipboard is now held across the delete.

**Capturing into a route containing a three-way row threw, invisibly.** The settle step calls
`toCommand()` on every row and a three-way row refuses by design; the throw was swallowed by the
capture's blanket catch, so the table stopped updating while the capture went on filling it. Everything
captured was still saved. Three-way rows are skipped and the settle has its own catch.

**An existing automatic route opened with its sensor and trigger boxes dead.** `setSelected` fires no
ActionEvent, so the listener that greys them never ran on the way in. `load()` calls it now.

**A locked route's title was overwritten immediately after construction**, removing the one sentence
explaining why every control was grey.

**Exporting a diagram as a picture permanently stopped tile updates for that page.** The offscreen grid
is built with a null master, its labels are registered with the model like any others, and
`isParentVisible()` dereferenced the null on the message thread — inside the loop that updates every
tile, with the exception swallowed by the executor. Every tile after it was never refreshed again, and
the bad label could never be pruned because pruning is the call that threw. Both dereferences are
guarded; a parentless label now reports itself invisible, which is what gets it removed.

**A page switch while tiles were still decoding could leave that page permanently blank.** `discard()`
stopped the timer that reveals the container without revealing it, and the container was already in the
page cache. It reveals first now.

**The hovered diagram square was not cleared when the grid was replaced.** Resting the pointer on a
platform, switching pages by hotkey and pressing Ctrl+X cut the locomotive from the page you were no
longer looking at, and saved it.

**A saved state with more pages than the preference grew the page count without growing the tab
strip**, so navigating to one of the extra pages threw out of the tab pane. Both grow together now, and
the preference is written.

**A popup diagram window did not stop the grid it was replacing**, so the outgoing grid's timers went
on dropping a spinner into the page the new one had just drawn.

### Concurrency and reliability

**The feedback branch of `receiveMessage` was the last check-then-act.** `hasId` then `getById` are two
separate acquisitions, and `syncLayouts` prunes feedbacks on another thread — exactly the ones this
branch auto-creates. The resulting NPE was swallowed by the executor's Future and the sensor event was
dropped. Feedback state is a level and the driving threads wait on it without a timeout, so a train
whose arrival was dropped never slows and never stops. Resolved into a local, like its two siblings.

**A short datagram was parsed with the tail of the previous one.** One buffer is reused for every
receive and the message was read out of the buffer rather than the packet, with the received length
never consulted — so a stale locomotive or accessory command could be re-applied under an unrelated
command byte, past the duplicate-suppression window. Only a datagram of the right length is parsed now.

**`Layout.layoutVersion` was not volatile.** It is the fence that stops a train when a configuration is
reloaded, written by the loading thread and read by every driving thread at six points in its loop.
Every other cross-thread field in that class was already volatile; this one was missed.

**`updatePowerState` touched Swing from the message thread** — the one `View` callback in that class
that did not marshal.

**`AutoLocomotiveStatus` could NPE on the EDT** reading milestones for a locomotive whose driver had
cleared them, leaving every panel after it in the loop stale.

**`NetworkProxy`'s failure path could NPE during startup**, because the model is set after the control
station's constructor has already transmitted. That exception walked out of `main`.

---

## Not done, and why

### Needs your judgement or the hardware

**Commands report success on a failed network write, and one failure can wedge the switching thread.**
`NetworkProxy.sendMessage` returns a boolean that `exec()` discards, and `exec()` is its only caller.
Click a switch with power off: the GO datagram is lost, `go()` returns normally, and the worker parks
in `waitForPowerState` — an untimed wait on a single-thread executor, so every later click on any tile
silently does nothing for the rest of the session. `stopAllLocs()` and `stop()` are the same shape with
a worse consequence: the UI shows a stopped railway while the trains keep running.

This is the most serious thing either reviewer found, and it is not a change to make blind. Making
`exec` return its boolean touches every command path; giving `waitForPowerState` a bounded wait changes
what the application does when the Central Station is slow rather than absent, and only you can judge
that timeout against your hardware. **Recommendation:** bounded wait first (smallest, safest), then the
boolean.

**Every wait on a level is untimed.** `waitForPowerState`, `waitForOccupiedFeedback`,
`waitForClearFeedback`, `waitForAccessoryState` and `waitForS88Reached` all wait without a deadline on
a state a single dropped UDP datagram can leave un-set. `validatePathActuation` already does this
properly — deadline, bounded wait, act on the timeout — and is the model the others should follow. Same
reason for leaving it: the timeouts are a railway judgement.

**The Layout monitor is held across per-command sleeps while a running train needs it.**
`configureAndLockPath` holds `synchronized (this)` across a lock loop containing a 150 ms sleep per
edge and per accessory — seconds, for a long path — and `updatePendingS88` is `synchronized` on the same
Layout and is called immediately before every sensor wait. A second locomotive can therefore block
there while its train crosses and clears the sensor, then wait for a trigger that will never come
again. The fix is to give `locomotivePendingS88` its own monitor; it is already a ConcurrentHashMap and
the Layout monitor buys nothing. Left alone because it is a concurrency change to the driving path and
wants a running railway to trust it.

**Main-window diagram tiles are registered with the model forever.** Removal is opportunistic and keyed
on `isParentVisible()`, and for a main-window tile the parent is a tab that is visible for the life of
the application — so nothing is ever removed, and every rebuild adds a generation. Each accessory ends
up walking hundreds of dead labels per CS echo, decoding icons and posting repaints for them.
`DiagramTileRegistry.register` documents this exact bug and fixes it for its own map by judging
`isDisplayable()`; the same idea has to be carried to the three model-side sets. Not a one-liner —
visibility is the wrong discriminator, because cached pages are legitimately detached but alive — so it
wants a deliberate pass rather than being tacked onto this batch.

**Executors and sockets are never shut down.** Three single-thread pools with non-daemon threads, a
`DatagramSocket` nothing closes, a non-daemon reader thread, and a discarded `Timer` in the UI. The GUI
masks all of it with `System.exit(0)`; the programmatic entry point in `examples/` hangs on return, and
a second `init()` in one JVM leaks the port. Worth a tidy-up, no user-visible symptom today.

### Deliberate, or not worth it

**A three-way point and an ordinary crossover can look identical.** Two adjacent turnouts with a settle
delay — `Switch 5 straight,300` then `Switch 6 turn` — match the right-hand three-way shape exactly and
open as one three-way row. The round trip is byte-exact, so nothing is lost, but deleting that row
removes both commands. This is the documented trade-off of reading points back out of the commands
rather than storing them; flagged so you know the false-positive shape is a common one.

**`AutonomyBanner.hold()` marks itself as saying something forever.** Real, but the method has no
caller anywhere in `src` or `test`, so nothing can reach it.

**`snapshotPage` puts live JSONObject references into the undo snapshot.** Reachable only by editing an
autonomy property between a diagram snapshot and its undo, and the two modes are otherwise exclusive.
`snapshotSetup`, added in this batch, deep-copies for exactly this reason and is the model to follow if
it ever needs fixing.

**The digits-only address editor covers Accessory and Feedback but not Signal or Three-way**, so those
accept "twelve" and complain at Save rather than at the keystroke. Cosmetic; validation catches it.

---

## Tests

Added, all seen failing first by mutating the fix away:

- a facing travels with its tile (`testAutonomyTileMove`)
- a square landed on lets go of what it knew, and a square that is both landed on and moving does not
- discarding an edit forgets a signal paired since the load (`testAutonomyDiagramStore`)
- a rename that is never saved still leaves the configuration on disk
- two names cannot share one file
- an unreadable import changes nothing
- a tree that mixes AND and OR comes back nested, both ways round (`testConditionOutline`)

Whole battery green: 68 classes, no failures. `testAutoDetect` is untried as always — it needs a
Central Station on the network.

## Still needs you, at the layout

The manual list in `2026-08-20-tests-to-run.md` now has three more (23–25, the multi-signal work).
On top of those, this batch wants:

**26. Shift Up with the pointer on the bottom row, and Shift Left on the last column.** Both should now
do nothing at all. Then the same one row up and one column in, which should shift normally — and check
the autonomy editor afterwards to see the stations went with the track.

**27. Drag a tile onto a station square.** The station has to be gone from the autonomy editor
afterwards, not left on a square holding plain track.

**28. Cancel the diagram editor after moving a set-up station.** Both the diagram and the autonomy
setup have to be back where they started. This is the one that used to lose the station quietly.

**29. The command table's marks.** Delete removes exactly one row; the arrows move a row and leave it
moved; duplicate makes one copy.

**30. A route holding a signal command.** Open it, click the Setting cell, click away without choosing
anything, and save. The signal must still be at danger.

**31. Export a diagram as a picture, then throw a switch on that page.** The tile has to keep updating.
