# The eighth review: what a reader with no context found

The first seven reviews were all primed. They were told what the branch was for, what the previous rounds had found, and which files had changed - which is efficient, and which means seven readers in a row looked hardest at the same places. This one was given none of that: a Java application that drives a model railway, form your own view, treat the fact that it moves physical objects seriously. Nothing about the branch, nothing about the earlier findings, no list of changed files.

It went where the others had not, because it had no reason to think anywhere was settled: the CAN protocol code, the control station, the file importers, the automation core. Fourteen findings. Every one below was confirmed by reading the code before anything was changed, and none of them turned out to be wrong.

The pattern in what it found is worth stating on its own. **Almost none of it is new.** Two findings are on this branch; the rest have been in TrainControl for a long time, in the layers nobody had reason to reread. The primed reviews were not careless - they were pointed, and being pointed is exactly what stops you looking at the ping handler.

---

## The four that stop a railway

### One lost packet ended the keepalive for the session

`sendPing(false)` did nothing while a ping was already in flight, and the only thing that cleared "in flight" was a response. UDP does not promise a response. So a station reboot, a moment of wireless, one dropped packet - and no ping was ever sent again for the rest of the session.

What follows from that is worse than the silence. The age of that one unanswered ping grew without bound, so the status line read **"Lost network connection" permanently**, including long after the network came back. And the five-second latency check reads the same number: with autonomy running and a latency limit set, **the power was cut every five seconds** - including five seconds after the operator switched it back on. Only restarting recovered.

Fixed by retrying an unanswered ping. The second half matters as much: measuring latency against the *first* ping of an outage would report the whole outage as the round trip of the packet that ended it, and that figure is what the cutoff reads - so recovering from an outage would itself have cut the power. A separate clock keeps the warning honest across retries.

### A frame that said nothing read as an order to cut the power

`getSubCommand()` guarded on `data.length < 5`. The parsing constructor always allocates eight bytes and fills only as many as the frame declared, so `data.length` is eight for every message that has ever arrived off the network and the guard could not fire. A system frame with four payload bytes therefore returned the untouched fifth byte - zero - and **zero is `CMD_SYSSUB_STOP`**.

The system branch deliberately does not check the response bit, so it can see stops from other controllers. Any short system frame on the bus therefore read as one: power state off, every locomotive told the power had gone (which corrupts the running-time accounting), the indicator dark, everything waiting on the power state released. On a layout still visibly running.

The comment above the guard states the correct intent - "five bytes are needed, not four". The code tested the wrong variable. It now tests `length`.

The test that matters here is the second one: **a real stop is still a stop.** A fix to a false positive that reaches far enough to suppress the true positives is the more dangerous bug of the two, and a stop from another controller is how somebody standing at the layout hits emergency stop.

### Clearing a point's priority killed that train for the session

The priority dialog sends `null` when the box is emptied - the obvious way to say "no priority" - and `null` was stored in an `Integer` field that two things unbox: `getPriority()`, which returns `int`, and `toJSON`'s `!= 0`.

The first throws inside the comparator that chooses where a train goes next. That call sits **outside** the try that guards path execution, so the locomotive's dispatch thread died and that train silently stopped being sent anywhere for the rest of the session. The second threw on every attempt to save the layout.

Zero already meant "no priority". `setMaxTrainLength` had been hardened against exactly this pattern; `setPriority` never was.

### "Face the other way" turned the wrong train

*This one is on this branch.* The facing submenu appears for the locomotive standing on the square - it is gated on that and its whole purpose is turning it round. It moved the locomotive selected on the keyboard instead: with some other train active, "face east" picked **that** train up and put it down on this platform, in the running layout and in the saved configuration both, while the train being pointed at did not move. With nothing selected it threw and the menu item did nothing at all.

The method it shares with "Place this locomotive here" is correct for that caller and wrong for this one. It takes the locomotive now instead of looking it up.

---

## The one that destroys data

### A database that would not load was saved over the top of

`restoreState` reports an unreadable file the same way it reports a first launch: an empty list and a log line saying "initializing with defaults". So a transiently locked file on Windows, a permissions change, a truncation - and the application runs with an **empty locomotive database**. Closing the window then calls `saveState`, which writes that emptiness over the real file.

The atomic-write staging does not help. It protects against dying part way through a write; this is a complete, successful write of nothing.

One transient read failure at startup plus a normal exit destroyed every locomotive customization the user had ever made, with no undo and only manual backups. A file that is missing is still a first launch and still saves normally; a file that is **there** and will not read now gets copied aside, loudly, before anything goes on top of it.

---

## The rest, in the order they were fixed

| What | Where it was | Now |
| --- | --- | --- |
| A saved return-home plan came back as an ordinary timetable - the sequential flag was written into the plan and not into the file, and `setTimetable` clears it. Entries then dispatch as soon as the previous one *starts* rather than arrives, which is the contention the flag exists to prevent | `Layout.toJSON`/`fromJSON` | Persisted, both directions tested |
| A path that failed part way left the sensor it was heading for pending for ever, so a route condition asking "has it reached that sensor" parked its thread until that locomotive was dispatched again - after a timetable failure, never | `Layout.executePath` catch | Cleared with the rest of the bookkeeping |
| Renaming the running configuration stranded it. The window remembers it by name and the menu only offers Rename while one is loaded, so the ordinary case left that name pointing at nothing: the exit-time capture found no such configuration and returned silently, dropping every placement and home set since the load | `AutonomyViewerPanel.rename` | A hook, like the one deleting already had |
| Right-clicking a sensor faked a train. The handler acted on any button, harmless while right-clicking did nothing - and not harmless once right-clicking a station became the way to work with it. With the overlay off, or the page left out, the menu does not open and the gesture fell through to flipping the sensor | `LayoutLabel` | Right-click that opens nothing does nothing |
| A half-finished layout download was committed as the whole truth. All three writers open the destination directly, which truncates it at once, so a transfer stopped by the read timeout committed a partial file - and the next sync reads it as the authoritative diagram | `CS2File.downloadCS2Layout` | Staged and moved into place |
| An MFX locomotive whose record has no address imported with an address past the highest MFX address there is. The DCC branch has always subtracted its base back off the UID; the MFX branch never did | `CS2File.parseLocomotives` | Corrected, and tested |
| Pressing Start twice started twice. The button stayed live until a worker several checks later disabled it, and there are three ways to press it | `startAutonomyActionPerformed` | Greyed on the event thread at the press |
| Its confirmation dialog was built and shown off the event thread, alone among the dialogs in that method | same | On the event thread |
| Leaving a page out mid-run desynced the interface from the railway: strip red, captions gone, right-clicks dead - while the layout carried on routing trains to those stations | `AutonomyMenu.pagesMenu` | Refused while running, reloaded when not |
| The CS3 duplicate filter had no time bound, so the same accessory told to go to the same place twice had the second command swallowed whole | `receiveMessage` | Bounded to 250 ms |
| A stop discarded because comms are off was completely silent - and that state is what a failed startup sync leaves you in, with the power possibly still on from before | `exec` | Said out loud |
| The headless IP prompt closed `System.in`, so mistyping the address once threw out of the retry instead of prompting again | `init` | Not closed |
| The station-label map was read from worker threads and was a plain `HashMap` | `TrainControlUI` | Concurrent |
| A failed autonomy save closed the editor as though it had succeeded | `LayoutEditor` | Stays open |
| The path list counted the menu's own furniture, cutting off about four paths early | `LayoutRightclickAutonomyMenu` | Counts paths |
| A dead method that never worked, and a comment for a field that moved to `Layout` | `Layout`, `Point` | Gone |

---

## Deliberately deferred

Two, both with reasons rather than shrugs.

**The `maxActiveTrains` cap can be exceeded.** The cap is checked inside the layout monitor, but the locomotive is not registered in `activeLocomotives` until later, under a different monitor - and between the two sits a validation wait of several seconds that deliberately holds no lock. So with the cap at one, train A can be in that wait while train B checks the cap, sees zero, and locks a disjoint path. Both run.

Track locking still holds throughout: the edges are exclusively locked either way, so this is not a collision risk. It exceeds a *preference* - usually set for booster capacity or operator comfort - by however many disjoint paths fit in the window. Closing it means reordering the locking protocol under a running autonomous layout, which is not a change to make in a round nobody is watching. Written up here so it is a decision rather than an omission.

**The locomotive wait loops busy-spin if interrupted.** Each `catch (InterruptedException)` re-sets the interrupt flag inside a `while` that immediately calls `wait()` again, so `wait()` throws at once and the loop spins at full CPU until its condition happens to be satisfied. Nothing in the codebase interrupts these threads - the code says so where it explains why it cannot - so this is latent.

The correct fix is small and I know what it is: remember the interrupt in a local and re-assert it once on the way out, so the wait is genuinely a wait and the caller can still see the interruption. It touches six blocking waits in the locomotive control path, and there is no way to test an interruption end-to-end here. That is a change to make with Adam at the layout, not without him.

---

## What this round did not change

Both of these are worth saying, because "the reviewer was wrong" is a finding too and there were none of those. Every claim it made held up. What it *could* not check is the same thing none of the earlier rounds could: **whether the hardware obeys.** These tests prove commands are formed and dispatched correctly. A Central Station is the only thing that proves the rest.
