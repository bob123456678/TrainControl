# Autonomous work log

The running record of a long unattended session on `autonomy-diagram-r0`. Updated as work lands, so
that what was done, what was decided, and what is waiting on Adam can be read without reading the diff.

**How to read this.** Each item carries its own status. Anything marked **NEEDS ADAM** is a question or
a judgement I would not make alone; everything else is either done, in progress, or explicitly deferred
with a reason. Commit hashes are given so any single piece can be reverted on its own.

---

## Orientation, before starting

The branch had moved: four commits landed while I was away, three of them Adam's own review rounds
(`0e3e280`, `2a3f0a8`, `7ab50e2`) and two fixes (`4753f57` GR-B1, `f956946` the branch review's four).
Read before starting, so this session builds on them rather than beside them.

**Review documents audited for outstanding items.** All findings on the v3.0.0 branch review are
dispositioned. Six rows in the post-fix review still read `Open` though the work landed in `0f743bf`;
corrected. What genuinely remains:

| Where | Item | Why it is still open |
|---|---|---|
| post-fix review | B1 | Claim withdrawn, fix stands. Not work - a correction already recorded |
| post-fix review | B8 | Route-preference enumeration cost. Wants measuring before deciding |
| backport review | C1 | Case-only rename crash window. Deliberate: a recovery scan costs more than the risk |
| backport review | C3 | 0-speed locomotive retries in an unbounded paced loop |
| whole-project review | C1-C29 | Low tier, `automation/` `marklin/` `base/` `gui/`. Needs a verification sweep first - four of nine audit rows turned out already fixed, so this table is probably part stale |

---

## The plan, in the order it will be done

1. Full test battery; fix what it finds
2. The timetable test, to Adam's recipe
3. Features and remaining bugs, one commit each, tests red first where possible
   - `syncWithCS2` off the EDT, with a mock CS2 to read from
   - Route editor: ovals for commands and conditions, boolean expression editing
   - Multi-select in the diagram editor, deprecating the odd paste variants
4. Full battery again
5. Independent, regression, focused and behavioural reviewers (Opus); iterate
6. Fable regression and independent reviewer; iterate
7. UI consistency pass - proposal only, no changes
8. Feature completeness and 3.1.0 ideas, for review
9. Test profiling; a lite battery of everything under 10s
10. Readme pass: drop 3.0.0 bugfixes that only ever existed on 3.0.0
11. A new Automation.md as a user guide, with a track-diagram export to illustrate it

---

## Progress

### 1. Full test battery
Done, and repeatedly since. The harness lives outside the repo and grew three fixes of its own during
this run: it now reaps orphaned JVMs before starting (one hung test holds the CS2 UDP port, and every
model-based class after it then reports "Address already in use" out of `@BeforeClass`, which TestNG
renders as a clean skip); it retries a class with a display when the headless run reports skips or
configuration failures, because a class that never ran is not a class that passed; and it snapshots
`LocDB.data` before the battery and restores it after, because two classes bring up the real interface
and its window-close handler saves state.

### 2. The timetable test, to Adam's recipe
Done - `testTimetableOnDerivedGraph`. Capture a configuration, run, send everyone back to their
starting positions, replay, require every entry to complete.

Two of the failures it produced were the test's fault rather than the product's, and both are worth
recording. A configuration carries its own saved timetable in its globals, and capture APPENDS - so
the snapshot was four entries of somebody else's run followed by this one's. And every locomotive has
to be restored TWICE, because placing one onto a square clears the other copies of that square, so a
single pass sweeps trains that were already back.

Its headline assertion was later found to be vacuous - see the reviews below.

### 3. Features and remaining bugs

| What | Commit | Notes |
| --- | --- | --- |
| `syncWithCS2` off the event thread, behind a modal spinner | `43dc1a6` | One wrapper rather than sixteen edits. See the review findings - the wrapper shipped calling itself |
| A mock Central Station to sync against | `157cd6a` | `testMockCentralStation`; a local HTTP server serving the sample `.cs2` files |
| The new route editor | several | Two dropdown tables, capture, boolean chaining shown as rows |
| Multi-select geometry | `TileSelection` | The arithmetic, tested apart from the editor. The editor wiring is still outstanding |

### 4. Full battery again
Run after each group. Clean at the point of writing except where noted below.

### 5-6. Reviewers

Two Opus reviewers ran over the whole session - one independent, one focused on the sync and the route
editor. They agreed on the most serious finding, independently, and between them found nine more that
were real. **Everything they raised was verified before being acted on, and the four cases where I
disagreed are recorded as such rather than quietly dropped.**

#### Fixed

| Finding | What it was | Fixed in |
| --- | --- | --- |
| **A1 / 1** | `TrainControlUI.syncWithCS2` called ITSELF rather than the model, in both branches. Every Central Station sync was a `StackOverflowError`. Mine: the bulk edit that routed sixteen call sites through the new wrapper also rewrote the two calls inside it | `bff832e`, guarded by `testNoSelfRecursiveWrappers` |
| **A2 / II.2** | `CommandRow` carried no protocol, and the editor hardcoded MM2. A DCC accessory saved as MM2 addresses a *different physical decoder* - `MarklinAccessory` puts the two protocols at different UIDs | `bff832e` |
| **A3 / II.1** | `CommandRow` carried no delay either, on four command kinds. A layout timed so a point motor settles lost all of it on one Save | `bff832e` |
| **C1 / II.3** | Commands the editor cannot show were held by their ORIGINAL index while the editable rows moved underneath. Deleting a row moved a sub-route call to the wrong side of a turnout, silently, having never shown it | `a24361d` - one list, one order, kept commands shown greyed |
| **B4 / II.6** | `ConditionRows` refused left-nested chains, which is what `NodeExpression.fromList` builds and therefore what every Central Station import with three or more conditions is. Two conditions worked; three greyed the editor out | `1d9020e` |
| **B3** | A checked-in sample configuration was polluted with four captured timetable entries from a live run, in a commit about something else | `6efc45a` |
| **B1** | `testTimetableOnDerivedGraph`'s headline assertion compared an object with itself and could not fail | `0cacfc6` - it now asks the layout where the trains actually are |
| **B2** | `testBusyDialogInteraction` re-implemented the wrapper instead of calling it, which is why A1 shipped | `e47e8a6` - two new tests drive the real method |
| **I.2** | Two concurrent syncs became possible when the work moved off the event thread; the event thread used to BE the swap | `e47e8a6` - a second caller is turned away and told so |
| **I.3** | `layoutDB` sat empty for the whole of an HTTP fetch, not "a paint" | `e47e8a6` - parse first, then swap |
| **C6** | `autonomyFutures` was dead; a rejected `submit` re-latched the refresh flag | `e47e8a6` |
| **C3** | `onSave` discarded `newRoute`'s answer and skipped the sync every other route path performs | `e47e8a6` |
| **C7** | Two javadoc comments orphaned - inserted above the wrong methods | `e47e8a6` |
| **C4** | "the pause between attempts may be zero" - it may not; `pacedWait` sleeps `COMPLETION_POLL`. And `executeTimetable`'s `@return` still said abandonment was sequential-only | `0cacfc6` |
| **C2** | Unseeded `Random` in a test that drives trains for minutes | `0cacfc6` - seeded, and the seed is in every failure message |
| (mine) | The panels latched "No available paths" at the end of a run: `findPaths` answers null for "did not search", and the repaint stamped it as an answer | `e47e8a6` |

#### Judged, and not changed

| Finding | Why not |
| --- | --- |
| **C5 / I.5** - `TEST_CS2_ADDRESS` is `public static volatile` and read by production code | Kept. It mirrors the existing `DEBUG_SIMULATE_PACKETS` convention, is null in every normal run, and both users restore it in a `finally`. The reviewer's own verdict was "acceptable"; the tightening suggested (an instance field through a constructor) would mean threading a test seam through `MarklinControlStation`'s construction, which is a larger change than the risk. **Flagged for Adam** rather than decided quietly |
| **C2 (route editor)** - `appendCommand`'s dead branch computing a colliding key | Removed as a side effect of the one-list rewrite, so there is nothing left to guard |
| **I.4** - `BusyDialog.run` is non-blocking when called off the event thread, so the same method has two contracts | Real, and deliberate: the alternative is to block a background thread on a dialog it cannot see. Documented rather than changed |
| **II.7** - a captured signal renders as `ACCESSORY / 4 / straight` rather than green/red | Correct as it stands. `KEY_ACCESSORY_TYPE` is never populated from route storage, so nothing is lost. The vocabulary could be friendlier; **noted for the UI consistency pass** rather than changed mid-fix |

### 6. Fable reviewers

Two more, one regression and one independent. They found nine real things between them, and agreed
with each other on the most serious. Every one was verified before being acted on.

| Finding | What it was | Fixed in |
| --- | --- | --- |
| **Both, independently** | The multi-select key bindings never fired. WHEN_IN_FOCUSED_WINDOW is dead in a window whose FRAME holds focus, and the old key handler went on deleting whatever the mouse was over - so Delete with ten squares picked erased one unrelated tile | `902c647` |
| **Independent, #1** | Six messages used `%s`. MessageFormat leaves it in the text and discards the argument, so the route editor's "kept as they are: %s" printed the placeholder where the condition should be - in the same commit that claimed to fix conditions being invisible | `902c647`, with two new bundle checks |
| **Regression, #1** | `fitsAfterMove` was handed PIXELS against a column number, so a group dragged past the edge was never refused - and since the move clears before it writes, the group was already deleted when it threw | `902c647` |
| **Regression, #4** | `groupClipboard` was never cleared, so one group copy hijacked every later paste for the session | `902c647` |
| **Regression, #5** | Capture in the new route editor did nothing unless the OLD editor was open - the outer gate was never extended | `902c647` |
| **Regression, #3** | "+" moved every tile down one row, and every autonomy annotation is keyed by square, so stations, signals, arrivals and captions would all have been left one row out | `902c647` - grows at the far edges only; the top row is **flagged for Adam** |
| **Regression, #6** | A refused sync returned -1, which callers read as "failed" - the Sync menu announced a failure that had not happened, and the switch-layout path cleared the layouts then did not sync | `902c647` |
| **Regression, #7** | The condition editor offered kinds `Route.evaluate` cannot evaluate, giving a permanently false condition and a route that silently stopped firing | `902c647` |
| **Independent, #4** | Add and Remove stayed enabled on a bracketed condition whose contents Save then discarded | `902c647` |
| **Independent, #5, #6** | Two test defects: a class javadoc describing a comparison replaced two commits earlier, and a "MatchTheFile" test that matched nothing against the file | `902c647` |

**And one the reviewers did not find, which its own test did.** The diagram export produced a blank
image. Three causes, all uncovered by chasing one assertion: `printAll` and `paintAll` both begin with
an `isShowing()` check and do nothing offscreen; tile images decode on a worker and apply on the event
thread, so a render holding that thread paints before any icon arrives; and tile icons exist as files
only at 30 and 60 pixels, so asking for 40 failed every icon. The test was written to fail on a
single-colour image, and it did, on the first run.

### 7-11. The rest of the work order

| Item | Where |
| --- | --- |
| UI consistency proposal | `docs/reviews/2026-08-19-ui-consistency-proposal.md` - seven items, each with a cost and a recommendation, nothing changed |
| Feature completeness and 3.1.0 | `docs/reviews/2026-08-19-completeness-and-3.1.0.md` - and three questions for Adam at the end |
| Test profiling and a lite battery | `docs/reviews/2026-08-19-test-suite-timings.md`; `runlite.sh` in the scratchpad. Ten classes are 606 seconds; the other fifty are under ten each |
| Readme | `86167bf` - nine 3.0.0-only bugfixes removed, this session's features added |
| `Automation.md` | Rewritten as a user guide, diagram-first, with four worked examples and a troubleshooting section. The old JSON and API material is preserved in `AutomationAPI.md`. Eight screenshots are placeholders; the guide lists what each should show |
| Diagram export | `DiagramExport`, on the Layout menu. Writes a whole page to a PNG at any size |

### Outstanding

Not yet done, in the order they will be:

1. Multi-select editor wiring - shift-click, Escape, group drag, paint/erase/rotate, copy and paste,
   the row and column controls, and the deprecations Adam listed
2. Fable reviewers
3. The UI consistency proposal
4. Feature completeness and 3.1.0 ideas
5. Test profiling and a lite battery
6. Readme: drop the 3.0.0-only bugfixes
7. `Automation.md`, with a full-size track-diagram export in the Layout menu

Also carried, from earlier findings not yet acted on:

- T3's give-up is invisible: the return value is discarded, "path finished" is logged after the
  failure, and nothing says that Execute can be pressed again. Three minutes also measures the wrong
  thing - no-progress detection would be better than a fixed bound
- "Across the Fewest/Most Sensors" counts derived-graph Points rather than sensors
- Least-recently-visited is the path preference an operator would reach for first, and is missing
- Capture for conditions - explicit destination, accessories yes, s88 no by default
- `testRouteCommandParity` builds a full `MarklinControlStation` for a field no test reads, which binds
  the CS2 port and loads the operator's locomotive database for nothing
- `testRouteCapture` claims addresses 71-73 without clearing them first
