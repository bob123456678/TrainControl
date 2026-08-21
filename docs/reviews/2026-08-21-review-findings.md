# Two-review findings - 2026-08-21

**Prefix for citing this document: `TR`.**

**Version reviewed:** commit `1efa3b9a`, branch `autonomy-diagram-r0`, v3.0.0 Beta. **Scope:** two
passes, both read-only and neither compiling or running anything:

- **R** - regression review of the last 40 commits (`git diff HEAD~40..HEAD -- src test`), roughly
  7,300 added lines across 58 files, looking for defects the recent work introduced.
- **P** - independent whole-project review, deliberately not scoped to recent changes.

**Reviewed:** 2026-08-21. **Code WAS changed afterwards** - every finding marked Fixed was fixed in
`174178c5`, and the tests for them ran; the prose under each finding describes the defect as it stood
at `1efa3b9a`. Where a status has changed since, the status column says so and the prose does not.

Findings use the A/B/C/D convention in [README.md](README.md). Every one was re-verified against the
source before it was acted on; those that did not survive that verification are D findings rather than
deletions.

**Confidence** is the reviewer's own: CONFIRMED means traced end to end; PLAUSIBLE means the mechanism
is certain but the trigger could not be exercised from a reading.

A note on the banding: both reviewers ranked their findings and neither labelled them, so the letters
here are mine. A covers two things this convention does not separate - work destroyed silently, and
behaviour wrong in ordinary use - so A1-A11 are the first and A12-A23 the second. That is an ordering
within the letter, not a fifth severity.

---

## A - high

Wrong behaviour on the layout, or data silently lost.

### A1-A11, the ones that destroy work silently

| # | Finding | Where | From | Conf. | Status |
|---|---|---|---|---|---|
| A1 | **Shift Up / Shift Left from the last row or column moves the whole diagram and tells the autonomy setup nothing.** `LayoutDiagram.shiftUp` normalises any start row past `sy - 2` to the first row, so hovering the bottom row shifts the entire page up and destroys row 0 — while `setupShift` is called with `from > to` and produces an empty map. Every station, name, length and pairing on the page stays on the square it used to be on. Nothing is dropped by the next reconcile, because every square still has a tile: the page's setup is simply attached to the wrong tiles. | `LayoutEditor.java:2805`, `:2889`; `LayoutDiagram.java:745`, `:775` | R1 | CONFIRMED | Fixed `174178c5` |
| A2 | **Cancel in the diagram editor does not take back the autonomy edits already written to disk.** `rememberAutonomy` → `saveQuietly()` writes `setup.json` on every dragged tile, group move and shift. `confirmExit` only re-reads the *pages* from disk. A cancelled drag leaves a station recorded on a square with no sensor; the next reconcile drops its name, length, restrictions and signal pairing. | `LayoutEditor.java:361`, `:3937` | R2 | CONFIRMED | Fixed `174178c5` |
| A3 | **A facing does not travel with the tile it is about.** Directions are keyed by square *and* route (`page:x,y#state,index`); `moveKeys` matches whole keys and so never matched one. Every facing stayed behind and was dropped by the next reconcile. | `AutonomyCompanionStore.java:1346` | *mine, during verification* | CONFIRMED | Fixed `174178c5` |
| A4 | **A tile dragged onto a square does not take that square's setup away.** `moveKeys`/`moveMembers` only overwrite a landing square when the source has something to overwrite it with. Plain track dragged over a station leaves the station's name, signals and restrictions on a square that now holds plain track; reconcile never tidies it up. The old `moveSelection` called `forgetCaptionsAt(landing)` unconditionally for exactly this reason. | `AutonomyCompanionStore.java:1518`, `:1575` | R9 | CONFIRMED | Fixed `174178c5` |
| A5 | **Discarding an edit keeps a signal pairing made since the load, and the next save writes it to disk.** `clear()` omits `stationSignals`; `clearShared()` has it. `readShared` only *puts* what the file holds, so an entry the file says nothing about is never overwritten. From then on a real signal is thrown to red on real hardware for a pairing the user cancelled. | `AutonomyCompanionStore.java:1862` | P4 | CONFIRMED | Fixed `174178c5` |
| A6 | **Renaming a configuration deletes its file before anything replaces it.** The new file is only written by the following save; `load()` rebuilds the list by scanning the folder. Anything that stops that save — a sync client on the folder, a full disk, the process dying — destroys the configuration permanently. `deleteConfiguration` got this right and says why; `renameConfiguration` was left behind. | `AutonomyCompanionStore.java:1010` | P7 | CONFIRMED | Fixed `174178c5` |
| A7 | **Two differently-named configurations can share one file.** Uniqueness is checked on the in-memory name; the filename is sanitised, and sanitising is many-to-one. Saving writes both to one file, one over the other, and the next load — which rebuilds from the folder — comes back with one of them gone. Deleting or renaming either takes the other's data. | `AutonomyCompanionStore.java:1891`, `:970` | P8 | CONFIRMED | Fixed `174178c5` |
| A8 | **An import that cannot be read empties the shared half on its way to failing.** `clearShared()` runs before `readShared(merged)`, and `merged` is assembled from someone else's file without type checking. The reader throws part way through; the panel reports "import unreadable", and the store stands blank, one save from being permanent. `load()` was hardened against exactly this and says so. | `AutonomyCompanionStore.java:929` | P9 | CONFIRMED | Fixed `174178c5` |
| A9 | **The track-diagram page index is truncated in place.** `gleisbild.cs2` names every page there is and was the one file in the project not written atomically. A failure part way through leaves a header naming no pages; every page file stays on disk unreferenced, the next start shows an empty layout, and the autonomy setup is reconciled away against it. | `LayoutDiagram.java:801` | P6 | CONFIRMED | Fixed `174178c5` |
| A10 | **`setup.json` gained an array shape with no version bump.** A station with two signals writes an array; the previous release reads that field with a string accessor and throws an unchecked exception — *after* `load()` has emptied the store, and past every `catch (IOException)` guarding `discardEdits`/`open`. The version gate exists for exactly this and did not fire. | `AutonomyCompanionStore.java:56`, `:2418` | R11 | CONFIRMED | Fixed `174178c5` |
| A11 | **Every command reports success on a failed network write, and one failure wedges the switching thread for the session.** `NetworkProxy.sendMessage` returns `false` on `IOException`; `exec()` is `void` and discards it, and is the boolean's only caller. Click a switch with power off: the GO datagram is lost, `go()` returns normally, and the worker parks in `waitForPowerState` — an untimed wait on a single-thread executor, so every later click on any tile silently does nothing. `stopAllLocs()` and `stop()` are the same shape and worse: the UI shows a stopped railway while the trains keep running. | `MarklinControlStation.java:2264`, `:2424`, `:2491`; `NetworkProxy.java:99`; `LayoutLabel.java:382` | P1 | CONFIRMED | **Open** - validated and traced 2026-08-21, needs a timeout value |

### A12-A23, the ones the user meets

| # | Finding | Where | From | Conf. | Status |
|---|---|---|---|---|---|
| A12 | **Every mark in the command table is acted on twice.** `actOnRowMarks` registers a mouse listener that dispatches on the cell's value, ignoring the column, and it is called twice on the same table. One click on the trash deletes two commands; the up/down arrows do nothing at all, because the second listener moves the row back; duplicate makes two copies. | `RouteEditorFrame.java:2555`, `:2558` | R3 | CONFIRMED | Fixed `174178c5` |
| A13 | **A signal row's Setting cell can be flipped by one stray click.** An accessory row upgraded to a Signal row for display keeps the word "turn", which the signal dropdown does not contain, so the combo falls back to its first entry — green. Single click into the cell, click away, and a route that put a signal to danger is now one that clears it. | `RouteEditorFrame.java:692`, `:637`, `:2606` | R5 | CONFIRMED | Fixed `174178c5` |
| A14 | **A condition mixing AND and OR without brackets is flattened.** `write()` recurses into a child at the parent's depth whatever word the child uses, so `A and (B or C)` becomes one level reading `A and B or C` — two words at one depth, which this class's own rule refuses. The route opens flagged red and cannot be saved until the user restructures a condition they never wrote, and the reading the Test button evaluates says `(A and B) or C`. | `ConditionOutline.java:392` | P (via R10) | CONFIRMED | Fixed `174178c5` |
| A15 | **Exporting a diagram as a picture permanently stops tile updates for that page.** The offscreen grid is built with a null master; its labels are registered with the model like any others, and `isParentVisible()` dereferences the null on the message thread — inside the loop that updates every tile, with the exception swallowed by the executor's Future. Every label after it is never refreshed again, and the bad label can never be pruned because pruning is the call that throws. | `DiagramExport.java:115`; `LayoutGrid.java:166`, `:482`; `LayoutLabel.java:430`, `:680` | P3 | CONFIRMED | Fixed `174178c5` |
| A16 | **The feedback branch of `receiveMessage` is a check-then-act whose NPE is swallowed.** `hasId` then `getById` are two separate acquisitions, and `syncLayouts` prunes feedbacks on another thread — exactly the ones this branch auto-creates for an unknown s88. The event is dropped in silence. Feedback state is a *level* and `waitForOccupiedFeedback` is an untimed wait on it, so a train whose arrival was dropped never slows and never stops. The locomotive and accessory branches were both hardened against this and carry comments saying so. | `MarklinControlStation.java:2088` | P2 | CONFIRMED (race), PLAUSIBLE (trigger) | Fixed `174178c5` |
| A17 | **Backspace over the diagram removes a locomotive instead of cycling tabs.** `locomotiveGestureOnDiagram` claims bare Backspace and is consulted before the tab cycler. With the pointer resting on a station square, pressing Backspace to leave the Track Diagram tab clears the square and saves it. On an empty square it still returns `true`, so Backspace simply stops navigating whenever the mouse is over the diagram. | `TrainControlUI.java:3056`, `:12033`, `:12418` | R4 | CONFIRMED | Fixed `174178c5` |
| A18 | **Ctrl+X on a selection deletes the tiles and empties the clipboard.** `cutSelection` copies then deletes; `delete()` ends with `resetClipboard()`, which nulls the group clipboard the copy just filled. Ctrl+V then does nothing — the outcome the method's own javadoc says it was written to avoid. | `LayoutEditor.java:2301`, `:2501` | R6 | CONFIRMED | Fixed `174178c5` |
| A19 | **`Layout.layoutVersion` is not volatile.** It is the fence that stops a train when a configuration is reloaded — written once by the loading thread, read by every driving thread at six points in its loop and by both timetable waits. A locomotive can keep reading its cached value and drive a whole path against a retired graph. Every other cross-thread field in the class is already volatile. | `Layout.java:474`, `:558`, `:1049` | P12 | PLAUSIBLE | Fixed `174178c5` |
| A20 | **The UDP reader ignores the received datagram length and reuses one buffer.** The message is read out of the buffer rather than the packet, so a short datagram is parsed as its own header plus the tail of the previous payload — a stale locomotive or accessory command re-applied under an unrelated command byte, past the duplicate-suppression window, onto a physical railway. | `NetworkProxy.java:150`; `CS2Message.java:102` | P10 | CONFIRMED (defect), PLAUSIBLE (trigger) | Fixed `174178c5` |
| A21 | **Every wait on a level is untimed.** `waitForPowerState`, `waitForOccupiedFeedback`, `waitForClearFeedback`, `waitForAccessoryState` and `waitForS88Reached` all wait without a deadline on a state one dropped UDP datagram can leave un-set. UDP has no retransmission. `validatePathActuation` already does this properly and is the model. | `Locomotive.java:699`; `MarklinControlStation.java:645` and others | P1 (related) | CONFIRMED | **Withdrawn - see D9.**  Raised as A, downgraded 2026-08-21 |
| A22 | **The Layout monitor is held across per-command sleeps, and a running train needs it before every sensor wait.** `configureAndLockPath` holds `synchronized (this)` across a lock loop containing ~150ms per edge and per accessory; `updatePendingS88` is `synchronized` on the same Layout and is called immediately before every sensor wait. A second locomotive can block there while its train crosses and clears the sensor, then wait for a trigger that will never come again — not slowing, not stopping, path still locked. | `Layout.java:2233`, `:2005`, `:3780`, `:4038` | P5 | PLAUSIBLE | Fixed 2026-08-21 |
| A23 | **Main-window diagram tiles are registered with the model forever.** Removal is opportunistic and keyed on `isParentVisible()`; for a main-window tile the parent is a tab visible for the life of the application, so the predicate can never be false. Every `repaintLayout` with `useCache = false` adds a generation. Each accessory ends up walking hundreds of dead labels per CS echo, decoding icons and posting EDT repaints for them; heap grows without bound. `DiagramTileRegistry.register` documents and fixes this bug for its own map only. | `LayoutGrid.java:482`; `MarklinAccessory.java:128`, `MarklinFeedback.java:64`, `MarklinRoute.java:291` | P11 | CONFIRMED | Fixed 2026-08-21 |

## B - medium

Incorrect results, or crashes in specific configurations.

| # | Finding | Where | From | Conf. | Status |
|---|---|---|---|---|---|
| B1 | **Capturing into a route that contains a three-way row throws, and the captured rows land invisibly.** `settleCapturedRows` calls `toCommand()` on every row; a three-way row refuses by design. The throw is swallowed by the capture's blanket catch, so `fireTableDataChanged()` never runs — the table stops changing while the capture goes on filling it, de-duplication never runs, and everything captured is still saved. | `RouteEditorFrame.java:1032`, `:995` | R8 | CONFIRMED | Fixed `174178c5` |
| B2 | **A page switch while tiles are still decoding can leave that page permanently blank.** `showWhenTilesAreReady` hides the container and relies on `reveal` to show it; `discard()` stops both timers and short-circuits `reveal`, and nothing else ever sets the container visible — but `repaintLayout` has already put that container in the page cache. | `LayoutGrid.java:79`, `:536`; `TrainControlUI.java:19413` | R7 | CONFIRMED | Fixed `174178c5` |
| B3 | **An existing automatic route opens with its S88 field and Trigger dropdown greyed out and unreachable.** `showSensorIfAutomatic()` runs once during `build()` (box unticked) and thereafter only from the box's ActionListener; `load()` uses `setSelected`, which fires no ActionEvent. The trigger sensor cannot be changed without unticking and re-ticking Automatic. | `RouteEditorFrame.java:417`, `:824` | R12 | CONFIRMED | Fixed `174178c5` |
| B4 | **The hovered diagram square is not cleared when the grid is torn down.** `mouseExited` is the only writer that clears it, and `repaintLayout` replaces the grid under a still pointer. Rest the pointer on a platform, switch pages by hotkey, press Ctrl+X: the locomotive is cut from the page you are no longer looking at, and saved. | `LayoutLabel.java:194`; `TrainControlUI.java:3020` | R14 | CONFIRMED (except the AWT-exit step, PLAUSIBLE) | Fixed `174178c5` |
| B5 | **A saved state with more pages than the preference grows the page count without growing the tab strip.** The tabs are built in the constructor; `setViewListener` runs later and can increment the count without adding a tab or writing the preference. `switchLocMapping` then indexes out of the tab pane on the EDT, and the preference is never corrected, so it recurs every start. | `TrainControlUI.java:3889`, `:887`, `:3644` | R15 | PLAUSIBLE | Fixed `174178c5` |
| B6 | **A locked route's explanation is overwritten immediately after construction.** `becomeReadOnly()` sets a title saying why every control is grey; `editRoute` then sets the ordinary edit-route title over it. The old editor's Save-button tooltip that used to carry the same sentence is gone too. | `TrainControlUI.java:13924`; `RouteEditorFrame.java:1626` | R13 | CONFIRMED | Fixed `174178c5` |
| B7 | **"Show Station Here" tooltip says the action is refused, on an item that performs it.** `setEnabled(mine)` was removed in favour of a confirm dialog; the tooltip telling the user to go and clear the text elsewhere stayed. It sends them into the dead end the change removed. | `AutonomyEditorPanel.java:1415`; `messages.properties:1643` | R16 | CONFIRMED | Fixed `174178c5` |
| B8 | **The save-immediately mechanism reached the move paths and not the forget paths beside them.** The three `forgetCaptionsAt` call sites (paste, fill, delete) leave the change in memory only; it reaches disk solely through `resetAutonomySession`, which is gated on a configuration being loaded and the layout not running. | `LayoutEditor.java:2070`, `:2359`, `:2497` | R17 | PLAUSIBLE | Fixed `174178c5` |
| B9 | **`updatePowerState` touches Swing straight from the message thread** — the one `View` callback in that class that does not marshal. Intermittently stuck Go/Stop button states. | `TrainControlUI.java:4927` | P (minor) | CONFIRMED | Fixed `174178c5` |
| B10 | **`AutoLocomotiveStatus` can NPE on the EDT** reading the last milestone for a locomotive whose driver has already cleared them (the two maps are cleared in sequence, unsynchronized). The exception lands inside the unguarded loop that refreshes every panel, so every panel after it is left stale. | `AutoLocomotiveStatus.java:275`; `TrainControlUI.java:18450` | P (minor) | CONFIRMED | Fixed `174178c5` |
| B11 | **A popup diagram window never discards the grid it replaces**, so the outgoing grid's failsafe (8s) and grace (120ms) timers go on mutating the panel the replacement now owns — the glitch `discard()` was added to remove, on the one path that did not call it. | `LayoutPopupUI.java:41` | P (minor) | CONFIRMED | Fixed `174178c5` |
| B12 | **`NetworkProxy.model` is assigned after the control station's constructor has already transmitted.** A send failure in that window NPEs inside the catch block, out of the constructor and out of `main`, which prints `Error occurred: null` and exits. | `NetworkProxy.java:119`; `MarklinControlStation.java:335`, `:3594` | P1 (related) | CONFIRMED (window), PLAUSIBLE (trigger) | Fixed `174178c5` |
| B13 | **Executors, socket and timers are never shut down** — three non-daemon single-thread pools, a `DatagramSocket` nothing closes, a non-daemon reader thread whose only exit is that socket closing, and a discarded `Timer`. The GUI masks it with `System.exit(0)`; the documented programmatic entry point hangs on return, and a second `init()` in one JVM leaks the port. | `MarklinControlStation.java:243`; `TrainControlUI.java:4158` | P (minor) | CONFIRMED | Fixed 2026-08-21 |

## C - low

Cosmetic, dead code, or narrow edge cases.  An open C is still a C: "Declined" below means judged not
worth fixing, not that it stopped being a finding.

| # | Finding | Where | From | Conf. | Status |
|---|---|---|---|---|---|
| C1 | **Two ordinary adjacent turnouts with a settle delay open as one three-way row.** `Switch 5 straight,300` then `Switch 6 turn` — a common crossover — matches the RIGHT shape exactly. The round trip is byte-exact so nothing is lost, but deleting that row removes both commands and changing it to "left" writes a different piece of railway. | `RouteEditorFrame.java:836`; `ThreeWaySwitch.read` | R (minor) | CONFIRMED | **Declined** — documented trade-off of reading points back out of commands |
| C2 | **The digits-only address editor covers Accessory and Feedback but not Signal or Three-way**, so those accept "twelve" and complain only at Save. | `RouteEditorFrame.java:2627` | R (minor) | CONFIRMED | **Declined** — validation catches it |
| C3 | **`AutonomyBanner.hold()` stores the rendered placeholder into `saying`**, so `isSaying()` returns true forever after the first message. | `AutonomyBanner.java:344` | R (minor) | CONFIRMED | **Declined** — no caller anywhere in `src` or `test` |
| C4 | **`snapshotPage` puts live per-point `JSONObject` references into the undo snapshot**, and `setPointProperty` mutates them in place. Reachable only by editing an autonomy property between a diagram snapshot and its undo; the two modes are otherwise exclusive. | `AutonomyCompanionStore.java:1385` | R (minor) | CONFIRMED | **Declined** — `snapshotSetup`, added in this batch, deep-copies and is the model if it ever needs fixing |
| C5 | **The page cache branch replaces a panel's contents without discarding the outgoing grid**, so its 120ms grace timer can still drop a spinner into the panel. | `TrainControlUI.java:19391` | R7 (related) | CONFIRMED | **Declined** — same shape as B2, no observed symptom on that path |

---

## D - not defects

Claims that turned out to be wrong, and checks that came back clean.  Recorded rather than deleted: a
rejected finding is worth as much as a confirmed one, because it stops the same claim being re-raised
and it says how much to trust the rest of the pass.

| # | Claim or check | From | Why it is a D |
|---|---|---|---|
| D1 | `CS2File.sanitizeFilename` does not replace `/` and `\`, so a configuration name holding a path separator escapes the folder | P | Wrong. The character class covers both. The reviewer rejected it before reporting; re-checked here literally. |
| D2 | `MarklinAccessory.parseMessage` applies state after `updateTiles`, so a throw in the tile loop loses the state | P | Wrong. The order is the other way round - state is applied first. Rejected by the reviewer before reporting. |
| D3 | The multi-signal widening leaves callers assuming a single signal | R | Traced end to end and clean: the store's list accessors, `rekeyListValues`/`moveListValues` (both `setValue` a fresh list, so page snapshots are not aliased), `readStringListMap`, `protectingSignalNames`, `signalsThatAreGone`, `AutonomyBuilder:801`, `Point.toJSON:786`, `Layout.refreshOneSignal`.  Singular `getProtectingSignal()` is referenced only from tests.  A10 is the only defect in that work. |
| D4 | The signal picker dialog nests modals without bound | R | Clean.  The click - add - reopen path fully unwinds before the next click, and all four option labels are distinct in every locale. |
| D5 | `RouteCapture` and `ThreeWaySwitch` drifted from the editor they were lifted out of | R | Clean.  The filter is a faithful lift; expand and read are exact inverses for all three positions. |
| D6 | The new route editor drops a field the deleted one wrote | R | Clean.  Name, s88, trigger, enabled, commands and conditions all reach the same `editRoute`/`newRoute` signatures, and `editRoute` preserves the route id. |
| D7 | The eight message bundles have drifted apart | R | Clean.  All pure ASCII, identical key sets, and every key referenced from code resolves - the seven apparent misses are dynamic `prefix + enum.name()` lookups whose members are all present. |
| D8 | `TileAnnotation`, `RowIcons`, `LayoutRightclickAutonomyMenu` and the `TEST_CS2_ADDRESS` change carry defects | R | Clean.  Nothing found. |
| D9 | **Withdrawn from A21.**  The sensor waits are untimed and one dropped datagram strands a driving thread | P | Raised as A, withdrawn 2026-08-21 after validation.  A deadline here has nothing safe to do - the train is between sensors, and proceeding blind or stopping on a guess are both worse than waiting - and the level is not a one-shot event: `FEEDBACK_DURATION_THRESHOLD` is documented as exceeding the CS2 POLLING interval, so the station re-reports and a lost datagram heals on the next poll.  `waitForAccessoryState` has no production caller at all.  The one member of the group that is a real defect is `waitForPowerState`, which waits on an acknowledgement rather than on a train - that is A11 and stays open. |

---

## Counts

| | A | B | C | D | Total |
|---|---|---|---|---|---|
| Fixed in `174178c5` | 19 | 12 | 0 | - | 31 |
| Fixed 2026-08-21, second round | 2 | 1 | 0 | - | 3 |
| Open | 1 | 0 | 0 | - | 1 |
| Declined | 0 | 0 | 5 | - | 5 |
| Withdrawn to D | 1 | 0 | 0 | - | 1 |
| Not defects | - | - | - | 9 | 9 |
| **Total** | **23** | **13** | **5** | **9** | **50** |

Of the 40 defects that stand, one (A3) was found by me while verifying the reports rather than by
either reviewer, and one (A21) was withdrawn on validation and is now D9.

**One open finding: A11.**  It is validated and traced; what it needs is a timeout value, which is a
judgement about the railway.
