# TWV - validating the fixes for T10 and W21

**Status:** open

**Prefix for citing these findings elsewhere:** TWV

Checked free before use: `grep -rn TWV docs/ src/ test/` returns nothing, and
`grep -c TWV docs/manual-tests/findings.tsv` is `0`.

**Commit validated:** `abbed984`, branch `autonomy-diagram-r0`, on 2026-09-11. The reviews validated are
[`T10-ten-day-review.md`](T10-ten-day-review.md) (0 A, 3 B, 7 C) and
[`W21-three-week-review.md`](W21-three-week-review.md) (0 A, 3 B, 9 C).

---

## Scope and method

The brief was to attack the repairs rather than the findings: is each fix right, is it complete, and is
it worse than the defect. Every A/B verdict below rests on something that was run.

**What was executed.**

- `regression.testTheArrowsKeepTheirAim` against the working tree - 7 of 7 clean - and then against
  `abbed984^`'s `LayoutPageEdit.java` and `LayoutDiagram.java` restored into the tree, which is the
  only honest way to ask "would this test have caught the defect it was written for". Two of the three
  new tests failed; the third passed. See `TWV-B1`.
- A throwaway TestNG class in `test/scratchTWV/` drove all three real gestures against a
  `live-snapshot` sandbox and printed the `.artikel` of the first `pfeil` tile in **every** page file,
  before and after, plus the index. That is the measurement behind `T10-B1`, `T10-B2` and `T10-B3`
  being confirmed, and it is also what shows the copy is corrected while the test that claims to
  check it looks at a different file. The class is deleted.
- `core.testNetworkProxy` against the tree (clean), and again with `listen()` cut out of
  `sendMessage`'s reopen - the new assertion failed, with its own message. `W21-B2`'s test does catch
  its own defect.
- `core.testParseCS2Layout` (27, clean), `regression.testPageIdsAreDurable` (19, clean),
  `regression.testSwitchingToACentralStationLayout` - **1 failure, on the tree as committed**. See
  `TWV-B2`.

**What was read rather than run:** the `settleAbsentPages` interaction with `deliberatelyRemoved`
(`TWV-B3`), the `moveLocomotive` caller sweep (`TWV-B4`, `TWV-B5`), the `Combine` trace that settles
`T10-C2`, and the lock-ordering argument for `MarklinFeedback.setState` (`TWV-D1`). Each says below
what it would take to execute.

**The tree was left as found.** Every file mutated was copied to the scratch directory first and
restored from that copy; `git status --porcelain` shows only the three `cs2_sample_layout` lines that
were there at the start, and `git diff --stat -- src test docs build.xml` is empty. Nothing under
`cs2_sample_layout/` was written, moved or staged. No process was killed. The battery was not run.

---

## The verdict table

### T10

| id | what | verdict |
|---|---|---|
| T10-B1 | Add Page leaves the page it was invoked from stale on disk | **FIX CONFIRMED** |
| T10-B2 | Duplicate leaves the COPY stale | **FIX CONFIRMED** (its test does not test it - `TWV-B1`) |
| T10-B3 | Rename leaves the renamed page's own arrows stale | **FIX CONFIRMED** |
| T10-C1 | two javadocs say the old order is read from the index | NOT FIXED - still open (real) |
| T10-C2 | the "known gap" for a combined page's arrows does not exist | NOT FIXED - still open; **the claim is confirmed**, see below |
| T10-C3 | FR-018's absent-page question became unreachable | **FIX INCOMPLETE**, and partly **FIX WRONG** - `TWV-B3`, `TWV-C4` |
| T10-C4 | "could not be read and was skipped" is no longer what happens | NOT FIXED - still open (real) |
| T10-C5 | `NO_PAGE`'s javadoc claims a reference comparison | NOT FIXED - still open (real) |
| T10-C6 | a capitalised `seite` block gives CS2File no pages | **FIX CONFIRMED**, two caveats - `TWV-C1`, `TWV-C2` |
| T10-C7 | "deleting a selection is one call per square" | NOT FIXED - still open (real) |
| T10-D1..D9 | clean checks | NOT VERIFIED (not defects; not re-derived) |
| T10-D10 | withdrawn: the combined page's arrows as a defect | **agreed** - independently re-traced, see `T10-C2` below |

### W21

| id | what | verdict |
|---|---|---|
| W21-B1 | `MarklinFeedback.setState` tells nobody | **FIX CONFIRMED** (no test - `TWV-C5`) |
| W21-B2 | the CAN reopen restores only transmission | **FIX CONFIRMED** (mutation-checked) |
| W21-B3 | "Place locomotive" saves a placement the railway refused | **FIX INCOMPLETE** - two unswept siblings, `TWV-B4`, `TWV-B5` |
| W21-C1 | `(char) -1` in a message dialog | NOT FIXED - still open (real) |
| W21-C2 | the gateway ping is not retried | NOT FIXED - still open (real) |
| W21-C3 | `baseNameOf` never got `describe`'s fallback | NOT FIXED - still open (real) |
| W21-C4 | `invalidate()` writes `published` with no lock | NOT FIXED - still open (real) |
| W21-C5 | a chevron for an IDLE segment with no line | NOT FIXED - still open (real, unreachable today) |
| W21-C6 | `NetworkProxy.reader`'s comment describes nothing | **FIX CONFIRMED** |
| W21-C7 | the callback guard's first assertion reads raw source | NOT FIXED - still open (real) |
| W21-C8 | localising the trigger combo moves a Momentary selection | NOT FIXED - still open (real) |
| W21-C9 | `GraphLocAssign` vs `behaviour.md` on the arrival side | NOT FIXED - still open (Adam's to settle) |
| W21-D1..D5, D7, D8 | clean checks and coverage notes | NOT VERIFIED (not defects; not re-derived) |
| W21-D6 | not settled: `removeLocomotiveHere` losing its `session.save()` | **settled - it is a second defect**, `TWV-B5` |

**Counts.** 6 FIX CONFIRMED, 2 FIX INCOMPLETE (one of them partly FIX WRONG), 0 FIX WRONG outright,
13 NOT FIXED - still open, 2 settled by this pass, the rest NOT VERIFIED.

---

## Reasoning, longest where I disagree

### The page gestures - `T10-B1`, `T10-B2`, `T10-B3`: the code is right

The new order is settle the list, build `renamed`, `repointLinksForNewOrder`, save every changed page
except the subject, save the subject to its own file when the gesture is not a rename, blank, write
the page, do the autonomy work, write the index. I drove all three gestures against a `live-snapshot`
sandbox and read the files:

```
DUP  1 - Main              2 -> 3     (source, corrected)
DUP  1 - Main and neighbours    3     (the COPY - T10-B2's subject, correct)
DUP  4 - Combined          1 -> 2
ADD  1 - Main              2 -> 3     (the page the operator had open - T10-B1)
ADD  1 - Main and neighbours   (blank, 34 bytes)
REN  9 - Main              2 -> 1     (the renamed page's own file - T10-B3)
REN  2 - Bottom            0 -> 4     REN 3 - Top Parking  0 -> 4     REN 4 - Combined  1 -> 0
REN  1 - Main              deleted;  index keeps `.id=5` under the new name
```

Every one of those is the right answer for the new alphabet, and the rename keeps the page's id. The
things the brief asked me to check hard:

- **The id floor and `keepAbsent`.** Both are still evaluated at the index write, which is still the
  last statement of the method and still after the `session.saveWithoutReconciling()`. Nothing moved
  relative to them.
- **The autonomy session work between the two points.** It is now downstream of the page saves rather
  than upstream. Nothing in `renamePage`, `markPagesStale` or `saveWithoutReconciling` reads a page
  file, and nothing in `saveChanges` touches the store, so the two are independent. The rename probe
  confirms it: the index came out with `.id=5` under `9 - Main`.
- **Saving a page to its own path before the gesture.** `saveChanges` refuses an unreadable stand-in
  by throwing, at the one method that writes a page - so the new `page.saveChanges(null, false)`
  cannot write a blank over a file somebody is recovering. It throws, is caught, and is logged.
- **The duplicate page-name case.** `loadedPages` walks `getLayoutList()`, which is `layoutDB`'s key
  set, so two pages sharing a name yield one object. `pages.contains(page)` is identity
  (`LayoutDiagram` overrides neither `equals` nor `hashCode`), so for the aliased-away twin the
  subject would not be saved - the same `before`/`after` degeneracy `T10-D7` already describes, not
  made worse here.
- **`TrainControlUI`'s Delete and Combine.** Both still call `LayoutDiagram.writeIndexAndKeepLinksAimed`
  and both pass `renamed = null`. Removing the "a page being renamed is not saved here" filter from
  that method is therefore behaviour-neutral for them: the filter only ever did anything for a
  non-empty `renamed` map, and only `LayoutPageEdit` passed one. The filter's job now lives at the one
  call site that needs it, as `if (moved == page) continue;`.

What is wrong is one of the three tests - `TWV-B1` - and where one of them opens its sandbox -
`TWV-B2`.

### `T10-C2` - settled: **Combine really is fine, and the sentence you wrote is wrong**

You asked me to settle this. I agree with the reviewer, and here is the whole chain:

1. `combineLinkedPages:24947` writes the copy from the source page **as it stands**, before any re-aim.
   So far this is `T10-B2`'s shape exactly.
2. `:24955` `writeIndexAndKeepLinksAimed(layoutList, null, keepAbsent)` re-aims every loaded page and
   saves the ones that changed. The copy is not loaded, so it is not corrected. Still `T10-B2`'s shape.
   **This step is executed** - it is the `DUP 4 - Combined 1 -> 2` line above, the same method.
3. `:24991` `refreshLayouts()` re-reads every page from disk. The sources on disk are now corrected.
4. `:24993` `fillCombinedPage` calls `made.clear()` and then refills the page square by square from
   `this.model.getLayout(each)` - the just-re-read, corrected sources - through
   `new LayoutDiagramComponent(tile)`, whose copy constructor passes `original.rawAddress` to the
   delegated constructor (`LayoutDiagramComponent.java:107`). So the arrow numbers that reach the
   combined page are the corrected ones.
5. `fillCombinedPage` ends `made.saveChanges(null, false)`.

The stale bytes written at step 1 never survive step 4. So `fillCombinedPage`'s heading - **"THE
COPIED ARROWS ARE NOT RE-AIMED, AND THAT IS A KNOWN GAP"** - and `behaviour.md:1172-1174`'s *"The one
thing not covered is the combined page's own copied arrows"* are both false, and they are false in the
place a reader checks. Both are still in the tree at `abbed984`.

I did not drive Combine end to end: it needs a window and a right-click. What I executed is step 2,
which is the load-bearing half; steps 3-5 are code order and one constructor. **If you want this
closed by execution rather than by reading, it is an MT-style hands-on test**: a layout with an arrow
on a page that Combine will move, combine, then read the combined page's `.artikel` in the file.

The behaviour document should lose that sentence. Nothing replaces it: after `abbed984` there is no
gesture that leaves an arrow stale on disk.

### `T10-C3` - the fix restores the dialog and breaks two things doing it

The new loop in `settleAbsentPages` is right about the population: a stand-in *is* the absence FR-018
is about. Two problems, filed as `TWV-B3` and `TWV-C4`.

### `T10-C6` - both halves landed

`parseFile`'s block arm is `^[a-zA-Z]+$` and `_type` is stored lower-cased; all twelve
`"xxx".equals(m.get("_type"))` readers compare against lower-case literals, and the one non-lowercase
value - `"_bareKeys"` at `CS2File:637` - is put directly and never goes through the lowering. The
guard now asks `actuallyRead == 0 && (couldNotBeRead > 0 || namedByTheIndex > 0)`, which cannot fire
on an empty folder (no index, `namedByTheIndex` 0, nothing threw) or on a Central Station layout whose
pages read (`actuallyRead > 0`).

One thing the widening actually improves, which is worth knowing because it is the only live
behavioural change I found: `sample_layout/config/gleisbild.cs2` has a `zuletztBenutzt` block carrying
`.name=Page 1`. Under `^[a-z]+$` that line was not a block, so its `.name` was attached to the
preceding `groesse` block. Now it gets a block of its own. `parseLayoutIndex` still finds the same
three `seite` blocks either way, so no page list changes - but the stray key stops landing on a
stranger.

Two caveats are filed: `TWV-C1` (the lowering leaks into the write path) and `TWV-C2` (the new read
of `fileParser` does not follow the RC-B9 rule stated eight lines above it).

### `W21-B1` - safe, and I went looking for the ways it would not be

This was named as the most likely to be worse than the defect. It is not, and here is each hazard and
why:

- **Deadlock against `parseMessage`'s monitor.** Both now take the same monitor - the
  `MarklinFeedback` instance - so no new lock *order* exists. The nested order is
  `MarklinFeedback` -> `Locomotive.monitor` (`Feedback._setState`), in both methods. I looked for the
  reverse: the only holders of `Locomotive.monitor` are `waitForOccupiedFeedback` and
  `waitForClearFeedback`, which while holding it call `isFeedbackSet` / `getFeedbackState` ->
  `MarklinFeedback.isSet()`, which is a plain unsynchronised field read on the base class. Nothing
  acquires a feedback's monitor under `Locomotive.monitor`.
- **The EDT now waits for a monitor the network thread holds.** That is new - a diagram s88 click goes
  `LayoutDiagramComponent` -> `setState`, and `setState` is now synchronized. It is only a deadlock if
  the network thread, holding that monitor, blocks on the EDT. It does not: `updateTiles` ->
  `LayoutLabel.updateImage` -> `setImage` marshals with `invokeLater`, `MarklinControlStation.log`
  wraps the whole body in `invokeLater`, and `TrainControlUI.feedbackChanged` returns before any Swing
  call and otherwise `invokeLater`s. There is no `invokeAndWait` anywhere under that monitor.
- **The start-up restore flood.** `this.view = view` is assigned at `MarklinControlStation:427` and
  the restore loop runs at `:437`, so the view *is* live and every restored module now announces.
  `MarklinControlStation.feedbackChanged` null-guards, and `TrainControlUI.feedbackChanged` returns on
  its first line unless the route editor is visible and capturing into conditions, which it cannot be
  during the constructor. Nothing is appended and nothing is logged; the flood is 200-odd early
  returns.
- **A capture recording what it should not.** There is one real new case, and it is a judgement call
  rather than a defect: with the route editor capturing into conditions *while autonomy simulates*,
  `Layout.simAnnounce` / `simClearBehind` now append an `s88` condition row per simulated sensor.
  `CAPTURE_COMMAND_THROTTLE` only de-duplicates an identical consecutive command, so a simulated run
  would fill the conditions list. Both doors are operator-driven and simultaneous, so I am not filing
  it - but it is undocumented behaviour, and it is the thing to say in `behaviour.md` if you keep it
  (`TWV-C5`).
- **Double announcement.** `grep` for `feedbackChanged` across `src/`: four sites, one producer
  (`MarklinFeedback`, now two doors), one relay, one consumer. `setFeedbackState` does not announce
  separately, so nothing fires twice.

### `W21-B2` - right, and its test proves it

`listen()` is `private synchronized`; `sendMessage(byte[])` is `synchronized` on the same object, so
the nested call is re-entrant. A second listener needs `reader.isAlive()` to be false while the old
thread is still reading, and the only way out of `ReadMessages.run` is the loop test on a closed
socket - which the reopen has not yet replaced at that point - so I could not construct one.
`setModel` remains the only other caller and still assigns `this.model` before calling it.

I cut `listen()` out of the reopen and ran the class: `testSendReopensAClosedSocket` failed with its
own message, `expected [1] but found [0]`. Restored and re-run clean.

Two things the finding named are still open, and one of them the fix makes bigger - `TWV-C3`.

### `W21-B3` - right at the site, and the sweep stopped one door short

`placeFacing` now honours the refusal, and the early return skips only `repaintAutoLocList` and
`updateVisiblePoints`, which have nothing to repaint when nothing moved. `session.save()` is inside
the `facing != null` arm, so nothing is written. The javadoc's "two callers" no longer exist -
`placeSomewhereLegal` is the only one (`TWV-C6`) - so the early return cannot refuse a facing-only
turn, which is the way this fix could have been worse than the defect.

`grep` for `moveLocomotive(` over `src/` gives six production call sites. Three are fine, three are
not the same, and two are defects:

| site | result used? | what is written afterwards | verdict |
|---|---|---|---|
| `LayoutRightclickAutonomyMenu:1105` | yes, now | - | fixed |
| `GraphLocAssign:335` (`commitChanges`) | no | `commitAndRecord` reads `point.getCurrentLocomotive()` **after** the move, so the setup records what the railway actually holds | not the same defect (`TWV-D6`) |
| `TrainControlUI:6067` | no | a rebuild re-applying known placements | not a door |
| `TrainControlUI:6703` (Ctrl+V paste) | **no** | `this.cutLocomotive = null` and then `rememberPlacement`, which **saves** | **`TWV-B4`** |
| `TrainControlUI:6718` (Ctrl+X / Delete) | no | removal branch; `rememberPlacement` records the now-empty point | fine |
| `LayoutRightclickAutonomyMenu:1290` (`removeLocomotiveHere`) | no | in-memory setup only, **never saved** | **`TWV-B5`** |

---

## TWV findings

| id | what | disposition |
|---|---|---|
| B1 | `testDuplicatingAPageCorrectsTheCopy` asserts on the source page and passes against the pre-fix code | open |
| B2 | the new gesture test opens its `LayoutSandbox` outside the try, and the battery is red for it | open |
| B3 | `settleAbsentPages`' stand-in loop ignores `deliberatelyRemoved`, so deleting an unreadable page can put it back in the index | open |
| B4 | the `moveLocomotive` sweep missed the Ctrl+V paste door, which discards the refusal, clears the clipboard and saves | open |
| B5 | `removeLocomotiveHere` never saves, so a removal made from the diagram is lost at the next launch (settles `W21-D6`) | open |
| C1 | lower-casing `_type` reaches `exportToCS2TextFormat`, so saving a page rewrites its block names | open |
| C2 | `namedByTheIndex` is read off the `fileParser` field long after the parse, against the RC-B9 rule eight lines above | open |
| C3 | `stopListening` is still unsynchronised, and the reopen now leaves a listener THREAD as well as a socket | open |
| C4 | "They were deleted" no longer retires a stand-in's id, because a stand-in is in `layoutList` | open |
| C5 | four of the six fixes ship with no test, and `behaviour.md` is untouched by the whole commit | open |
| C6 | the "Place X here" item is still offered for the copies the fix now refuses, and `placeFacing`'s javadoc names a caller that is gone | open |
| C7 | Add and Duplicate now rewrite the operator's current page file unconditionally | open |
| C8 | both review documents' tables say `open` for the findings this same commit fixed | open |
| D1 | `setState` being `synchronized` - no deadlock, no lock inversion - clean | closed |
| D2 | the start-up restore does not flood anything - clean | closed |
| D3 | a second CAN listener cannot be started - clean | closed |
| D4 | the id floor, `keepAbsent`, and the autonomy work between the two points - clean | closed |
| D5 | `writeIndexAndKeepLinksAimed`'s lost rename filter does not affect Delete or Combine - clean | closed |
| D6 | `GraphLocAssign` discards the same result and is NOT the same defect - clean | closed |
| D7 | the Add and Rename tests each fail for their own defect, measured - clean | closed |
| D8 | `testNetworkProxy`'s new assertion fails for its own defect, measured - clean | closed |

---

### TWV-B1 - the test named for `T10-B2` does not test `T10-B2`

`test/regression/testTheArrowsKeepTheirAim.java:155` (`testDuplicatingAPageCorrectsTheCopy`) and
`:283` (`String page = "rename".equals(gesture) ? made : subject;`).

`T10-B2` is about **the copy**: the source page was already corrected before this commit, and the
finding's own measurement says so - `ON DISK 1 - Main -> 3` for the source and `ON DISK 1 - Main and
neighbours -> 2` for the copy. The helper resolves the file to read as `made` only for a rename, so
for the duplicate gesture it reads `1 - Main` - the source. The copy's file is never opened by any
test in the class.

**How I know.** Executed, both ways. Against the tree: 7 of 7 clean. Against `abbed984^`'s
`LayoutPageEdit.java` and `LayoutDiagram.java` copied back into the tree:

```
Total tests run: 7, Failures: 2, Skips: 0
FAIL testARenamedPageKeepsItsOwnArrowsOnDisk   ... expected [1] but found [2]
FAIL testAddingAPageCorrectsTheFileItWasInvokedFrom ... expected [3] but found [2]
PASS testDuplicatingAPageCorrectsTheCopy
```

So the answer to "does each new test catch its own defect" is **yes for `T10-B1`, yes for `T10-B3`,
no for `T10-B2`**. The mutation the javadoc proposes ("move the re-aim back below
`page.saveChanges(newLayoutName, duplicate)`") would fail it, because the separate
`page.saveChanges(null, false)` would then carry stale tiles - but that is a mutation of the FIX, not
the defect the finding reported, and the SOP's rule is the defect.

The fix itself is fine: my probe read the copy's file and found `firstArrow=3`, which is correct.

**What I would change.** `String page = "duplicate".equals(gesture) ? made : "rename".equals(gesture)
? made : subject;` - or more plainly, assert on both files for the duplicate, since the finding is
about the two disagreeing. The expected value is 3 for both.

### TWV-B2 - the new test opens its sandbox outside the try, and the battery is red

`test/regression/testTheArrowsKeepTheirAim.java:270-275`, against
`test/regression/testSwitchingToACentralStationLayout.testEverySandboxIsOpenedInsideATry`.

```java
support.LayoutSandbox sandbox = support.LayoutSandbox.open(
    support.Scenario.folderFor("live-snapshot"));

try
{
    ... MarklinControlStation.init(...) ...
```

Anything that throws between the `open` and the `try` - and `init` binding its port is the named
example - leaves the machine-global layout preference pointing at a folder under `%TEMP%`. That is the
railway TrainControl opens the next time it starts. A guard for exactly this shipped in `6fe9ec38`,
the commit immediately before this one, whose own subject line is *"a killed test JVM left the
operator's layout preference pointing at a fixture"*.

**How I know.** Executed, on the tree as committed, with nothing of mine present:

```
--- regression.testSwitchingToACentralStationLayout
Total tests run: 12, Failures: 1, Skips: 0
1 sandbox(es) are opened outside a try ... but found
    [[testTheArrowsKeepTheirAim.java, in private static void assertArrowOnDiskAfterGesture(...)]]
```

**This means the battery does not come back clean at `abbed984`.** I have not run the battery - that
is yours - but this class is in it and this failure is deterministic.

**What I would change.** Move the `open` inside the `try`, which is what every other sandbox user in
the suite does and what the guard's message asks for. One line.

### TWV-B3 - the stand-in loop does not honour `deliberatelyRemoved`, so a deleted page can come back

`src/org/traincontrol/gui/TrainControlUI.java:2154-2172`, against `:25343` and
`src/org/traincontrol/base/LayoutDiagram.java:1725-1745`.

`pagesTheIndexWouldDrop` is handed `renamedFromTo` and `deliberatelyRemoved` precisely so the operator
is not asked about the thing they just did - the delete call site says so in as many words: *"The page
being deleted is named as a deliberate removal, which is what stops the question being asked about the
very thing the operator just did."* The new loop is underneath that call and filters on neither:

```java
for (String name : this.model.getLayoutList())
{
    LayoutDiagram page = this.model.getLayout(name);

    if (page != null && page.isUnreadable() && !absent.contains(name)) absent.add(name);
}
```

So deleting a page that is a stand-in - which is a plausible thing to do to a page that will not load -
puts that page's name straight back into `absent`. Two consequences:

1. The operator is asked whether the page they just chose to delete has been deleted. That is the
   dialog the call site exists to prevent.
2. If they answer **Keep them**, `keepAbsent` carries the name to `writeLayoutIndex`, where the
   held-back loop's gate is `if (id == null || layoutList.contains(held)) continue;` - and by then
   `layoutList.remove(going)` has run, so the gate passes and the deleted page is written back into
   the index with its old id. Its file has been deleted and its setup forgotten, so the next load
   produces a blank stand-in for a page the operator deleted, for ever.

**How I know.** Read - the loop has no filter, `deliberatelyRemoved` is a parameter of the method the
loop sits in, and `writeLayoutIndex`'s gate is a single line. I did not execute it because the path
goes through `JOptionPane.showOptionDialog`, and driving a modal dialog from a test is the thing that
stranded a JVM here yesterday. **To execute it:** it needs a hands-on test - `MT`-style - with an
unreadable page and a delete of that page.

**What I would change.** Apply the same two exclusions to the new loop that `pagesTheIndexWouldDrop`
already applies: skip a name in `deliberatelyRemoved`, and skip a name that is a key of
`renamedFromTo`.

### TWV-B4 - the Ctrl+V paste door discards the same refusal, clears the clipboard, and saves

`src/org/traincontrol/gui/TrainControlUI.java:6703`, `:6705`, `:6727`.

```java
this.model.getAutoLayout().moveLocomotive(placing.getName(), point.getName(), false);

this.cutLocomotive = null;
...
rememberPlacement(point, aimed);
```

`isAutonomyBusy()` at `:6551` closes the "autonomy started in between" door for this path, which the
right-click menu could not. What it does not close is `!target.isDestination()`, and that one is
reachable: `getAutonomyPointForTile` goes to `StationIndex.speakerAt`, whose last line is
`return all.isEmpty() ? null : all.get(0);` - it hands back a non-destination copy when no copy is a
destination. The paste door's own guard asks `canDepartFrom`, which is a different question and is
true for an ordinary sensor on plain track.

So: point at a plain-track sensor square, press Control+V. `moveLocomotive` logs
`autolayout.errorPointIsNotStation` and returns false; the clipboard is cleared anyway; and
`rememberPlacement` saves the setup. If the train got there by Control+X - which removed it with
`purge = true` - it is now on no square and on no clipboard.

`rememberPlacement` reads `point.getCurrentLocomotive()` rather than `placing`, so the *placement* it
records matches the railway - that is why this is B and not A. But if the square was already occupied,
the refused paste still writes `setArrivedFrom(tile, tailAtTheLanding)` and
`setFacing(..., facingAtTheLanding, ...)` - a tail and a heading computed for the train that was not
placed, applied to the train that was already standing there, and saved.

**How I know.** Read, all four links: `moveLocomotive`'s `isDestination` arm at `Layout.java:7071`;
`speakerAt`'s fall-through at `StationIndex.java:444`; the unconditional `cutLocomotive = null`; and
`rememberPlacement`'s `session.save()` at `:7478`. **To execute it:** a hands-on test - cut a train,
point at a non-station sensor, paste, and look at the Autonomy tab.

**What I would change.** The same line the menu got:
`if (!this.model.getAutoLayout().moveLocomotive(placing.getName(), point.getName(), false)) return true;`
- before `cutLocomotive` is cleared, so the clipboard survives and the paste can be aimed again. The
return is `true` because the key was handled.

### TWV-B5 - `removeLocomotiveHere` never saves, which settles `W21-D6`

`src/org/traincontrol/gui/LayoutRightclickAutonomyMenu.java:1281-1304`.

It calls `moveLocomotive(null, name, true)`, then `session.placeLocomotive(station, null)`, then two
repaints. There is no `session.save()`. `W21-D6` could not establish whether something downstream
saves; it does not. The `AutonomyEditorPanel` refresh callback at `TrainControlUI:4545` is the
callback of the *tile autonomy panel*, fired when that panel reports an edit - `repaintAutoLocList`
and `updateVisiblePoints` do not reach it. `GraphLocAssign:194` states the rule this breaks: *"Every
other door that writes the setup saves it: the paste door, the facing menu, placeFacing beside this
one on the same menu."*

So a train removed from a square using the track diagram's right-click menu is back on that square at
the next launch, unless some unrelated door happens to save the session first - which makes it
intermittent, which is worse than consistently broken.

The discarded `moveLocomotive` result matters here for the same reason it did in `W21-B3`: if the
refusal fires, the in-memory setup says the square is empty while the railway still has the train, and
the next save from any other door commits that.

**How I know.** Read - the method in full, the callback it does not reach, and `grep` for
`session.save` in the class (one hit, in `placeFacing`).

**What I would change.** `if (!moveLocomotive(...)) return;` and then the same guarded
`session.save()` `placeFacing` has, twenty lines above it.

---

### TWV-C1 - lower-casing `_type` reaches the write path

`src/org/traincontrol/marklin/file/CS2File.java:568` against
`src/org/traincontrol/base/LayoutDiagram.java:317`.

`item.put("_type", s.toLowerCase())` is read back by `exportToCS2TextFormat`, which writes it out as
the block name for every unmodelled block:

```java
builder.append(block.get("_type")).append("\n");
```

That machinery exists to put back what the file said - `X8-A1`, `X8V-C6`, and the rule *"what
TrainControl does not understand it is not entitled to throw away"*. Case is part of what it said. So
a page whose file spells a block `Version` is now silently rewritten as `version` on the first save.

Latent rather than live: I scanned every `.cs2` in the tree outside `cs2_sample_layout`, and the only
capitalised top-level block names are `Version` and `zuletztBenutzt`, both in `gleisbild.cs2` index
files, which `LayoutDiagram` writes through `writeLayoutIndex` rather than through this exporter. No
page file has one. Filed at C for that reason - and the reachability argument is the same one `T10-C6`
itself is filed on.

**What I would change.** Keep the original spelling for the export and lower-case only the comparison,
or store both (`_type` lowered for readers, `_typeAsWritten` for the exporter).

### TWV-C2 - the new index count is read off a field the method was told not to re-ask

`src/org/traincontrol/marklin/MarklinControlStation.java:832`, against `:791-797` in the same method.

Eight lines above the new read, and about this exact hazard:

> **ASKED ONCE, HERE, AND CARRIED** (RC-B9). The pruning decision a hundred and sixty lines below used
> to ask the parser again, and `fileParser` is a field that `syncWithCS2` reassigns OUTSIDE the lock
> that serialises this method [...] A local removes the question rather than answering it.

`final int namedByTheIndex = fileParser.getPagesTheIndexNamed();` is a second question to the same
field, asked after `parseLayout` and after the `actuallyRead` loop. The window is smaller than the one
RC-B9 closed and the consequence is milder - a spurious throw, which reverts to the Central Station,
or a missed one - but the rule was written down immediately above the new line and the new line does
not follow it.

**How I know.** Read. Not executed: reproducing it needs a concurrent `syncWithCS2` timed into a
few-instruction window.

**What I would change.** Read it beside `couldNotBeRead`, off the same `fileParser` reference, in the
statement after `parseLayout`.

### TWV-C3 - the reopen now leaves a listener thread in the race `stopListening` already had

`src/org/traincontrol/marklin/udp/NetworkProxy.java:112-115` and `:205-213`.

`W21-B2` named this and it was not fixed: *"`stopListening` reads and closes `socket` with no lock
while `sendMessage` is `synchronized` and is the writer, so a send racing a stop can re-bind 15730
after the close check."* The fix makes the loser of that race worse. Before, the race left an open
socket; now `listen()` runs too, so it leaves an open socket **and** a started daemon thread. The only
production caller of `stopListening` is `MarklinControlStation.shutdown()`, and its documented purpose
is to give port 15730 back so a second `init()` in the same JVM can bind it - which is the test
harness. A leaked daemon reader is exactly "a thread that outlived the program".

There is a second, narrower piece in the same fix: `listen()` lifts the listener start out of
`setModel`, where `this.model = model` had just run, and calls it from `sendMessage`, where `model`
may be null. The catch block directly below says so: *"The model is set AFTER this class is
constructed, and the constructor of the control station transmits - a ping and a power command -
before it gets there."* `ReadMessages.run()` opens with `model.initMessageBuffer()`. It needs
`socket.isClosed()` to be true in that window, which it is not on a normal construction, so this is a
trap rather than a defect - but it is the precondition the original call site had and the new one
does not.

**How I know.** Read, both. Not executed: both need a timed race.

**What I would change.** `synchronized public void stopListening()` - the reason the class wants it is
already written in that method's own javadoc - and a `model != null` guard at the top of `listen()`.

### TWV-C4 - "They were deleted" no longer retires the id

`src/org/traincontrol/gui/TrainControlUI.java:2246` (`return Collections.emptyList();`) against
`src/org/traincontrol/base/LayoutDiagram.java:1744`.

FR-018's two answers work by controlling `keepAbsent`: KEEP returns the names, so `writeLayoutIndex`
re-emits them with their old ids; GONE returns nothing, so the index drops them and the ids retire.
That only works for a page that is **not in `layoutList`**. A stand-in is in `layoutList` - that is the
whole of `NSV-B3` - so `writeLayoutIndex` writes it from the main loop regardless of the answer, and
the held-back loop skips it by its own `layoutList.contains(held)` gate.

So after answering "They were deleted": the setup is pruned, and the page stays in the index with its
id and comes back as a stand-in at the next load, and the dialog fires again at the next page edit.
The prune - the half the fix was for - happens. The retire does not. And the combination is the worse
one: the settings are gone while the page is still listed, so if the file *does* hydrate later, the
page returns empty.

I am not sure what the right answer is, which is why this is C and not B: a stand-in genuinely is
still in the list, and dropping it from the index would re-aim every arrow after it, which is what
`NSV-B3` exists to prevent. **This is a decision, not a repair** - either the dialog stops offering
"deleted" for a stand-in and offers "forget its settings" instead, or `writeLayoutIndex` learns to
drop a name the caller has explicitly retired even though it is in the list.

**How I know.** Read - the three lines are `settleAbsentPages`'s return, the main loop, and the
held-back loop's gate.

### TWV-C5 - four of six fixes have no test, and `behaviour.md` is untouched

`docs/reference/behaviour.md` has no change in `abbed984`, and `test/` has two: the
`testNetworkProxy` assertion and the three gesture tests.

So `W21-B1` (both doors announce), `W21-B3` (the railway's refusal decides), `T10-C3` (the absent-page
dialog fires for a stand-in) and `T10-C6` (a capitalised block, and the guard asking against the
index) all ship untested. `T10-C6` is the one that is cheap and should not have: `testParseCS2Layout`
already builds temp layouts and writes indexes in six places, and the test is "write the index with
`Seite`, parse, assert two pages" plus "assert `syncLayouts` throws". `W21-B1` is a unit test
(`MarklinControlStation.init(null, true, false, false, true)`, a recording `View` by reflection, create
the module, `setState`, assert one call) - `W21`'s own deleted probe is the shape, and it is quoted in
the review. The other two need the layout.

And the behaviour document says none of it: not that both feedback doors announce, not that a
placement the railway refuses is not recorded, not what the operator is asked about a page whose file
will not read - which `T10-C3` already pointed out is documented nowhere. It still says the opposite of
the truth about Combine (`T10-C2`).

Adam's bar for v3.0.0 is *"the behavior needs to be documented and tested"*. This commit meets neither
half for four of its six B/C repairs.

### TWV-C6 - the affordance still offers what the guard now refuses

`src/org/traincontrol/gui/LayoutRightclickAutonomyMenu.java:701-715` and `:1046-1049`, against `:1105`.

`placeableCopies` ends:

```java
if (!out.isEmpty()) return out;

return shut.isEmpty() ? stranded : shut;
```

`shut` is the copies for which `copy.isDestination()` is explicitly false - and those are exactly what
`moveLocomotive` now refuses. The item's enablement is `!usable.isEmpty()`, so when the fallback is
`shut` the item is enabled, the operator clicks it, and **nothing happens** except a line in the log.
Before the fix the train was placed (wrongly, and saved - that is `W21-B3`); after it, the menu
promises an action it will decline.

The square has to be one whose destination copies are all stranded or edgeless while a non-destination
copy is not, so it is narrow. It is still the `OB-057`/`OB-090` shape: the button that offers an action
must ask the guard's own predicate.

Two smaller things in the same place: `placeFacing`'s javadoc says *"it has two callers that mean
different trains"* and names Turning as the second - `grep` finds one caller. And `placeSomewhereLegal`
returns silently on an empty list, which is now the only other way out.

**How I know.** Read. `grep -n "placeFacing" LayoutRightclickAutonomyMenu.java` gives the declaration
and one call.

**What I would change.** Either drop the `shut` fallback from `placeableCopies` and let the item grey
with its existing "no way out" tooltip, or have the item ask `isDestination` about the copy it will
actually use. The second is the real fix; the comment at `:1046` explains why `shut` is offered at
all, and that reason no longer survives the refusal downstream.

### TWV-C7 - Add and Duplicate now rewrite the current page whether or not anything changed

`src/org/traincontrol/automationui/LayoutPageEdit.java:216-228`.

```java
if (!rename && pages.contains(page))
{
    try
    {
        page.saveChanges(null, false);
```

Unconditional. The loop above it saves only the pages `repointLinksForNewOrder` reported as changed;
this one asks about the gesture. For a page carrying no arrows - which is most pages - Add Page and
Duplicate Page now rewrite the operator's current page file through `exportToCS2TextFormat` for no
reason, and create a `.bak` beside it the first time.

The round trip is well covered (`X8-A1`, `X8-B2`, `X8V-C6`, and `testParseCS2Layout` is 27 clean), and
my probe shows `1 - Main` at 14,455 bytes before and after, so I am not claiming loss. It is
unnecessary writing on a file that lives in OneDrive, and it is one condition away from not being:
`if (!rename && changed.contains(page))`, holding the result of the re-aim rather than iterating it.

### TWV-C8 - both reviews still say `open` for what this commit fixed

`docs/reviews-2026-09-11/T10-ten-day-review.md` B table and C table,
`docs/reviews-2026-09-11/W21-three-week-review.md` B table.

Both documents were added by `abbed984`, the same commit that fixed `T10-B1/B2/B3`, `T10-C3`,
`T10-C6`, `W21-B1/B2/B3` and `W21-C6` - and every one of those rows still reads `open`. The SOP's
rule is *"One status, one location... A finding's disposition belongs in exactly one place - the
status table at the head of its section"*, and its recorded cost is a reviewer misreading his own
table as showing a dozen open items that were already fixed. Eight rows, in the two documents a reader
will open first.

---

### TWV-D - checks that came back clean

**D1 - `setState` being `synchronized`.** No new lock order (`MarklinFeedback` -> `Locomotive.monitor`
in both doors), no reverse acquisition (`Locomotive.monitor`'s two holders reach only the unsynchronised
`isSet()`), and nothing under the monitor blocks on the EDT (`updateImage`, `log` and
`feedbackChanged` all `invokeLater` or return first). Read, all five methods.

**D2 - the start-up restore.** `this.view` is assigned at `:427` and the restore runs at `:437`, so it
does announce - and `TrainControlUI.feedbackChanged` returns on its first line with no route editor.
No flood, no NPE. Read.

**D3 - a second CAN listener.** `listen()` is `synchronized` and guards on `reader.isAlive()`;
`ReadMessages.run` exits only on a socket the reopen has not yet replaced. I could not construct a
two-listener interleaving, and the test asserts the count is exactly 1.

**D4 - the id floor, `keepAbsent`, and the autonomy work.** All three still sit where they did relative
to the index write, which is still the last statement. The rename probe confirms the id survives
(`.id=5` under `9 - Main`).

**D5 - the lost rename filter in `writeIndexAndKeepLinksAimed`.** Both remaining callers
(`TrainControlUI` Delete at `:24955`, Combine at `:25412`) pass `renamed = null`, so the filter could
never have fired for them. Behaviour-neutral.

**D6 - `GraphLocAssign` discards the same result.** It is not `W21-B3`'s defect: `commitAndRecord`
reads `point.getCurrentLocomotive()` *after* `commitChanges`, and guards the facing write on it, so
what reaches the setup is what the railway holds. The comment at `:175` (*"an assignment that did not
take should not write a facing"*) shows the question was asked. Worth a line saying the result is
deliberately unread; not worth a change.

**D7 and D8 - the mutations.** Measured, both restored: the Add and Rename gesture tests each fail
against `abbed984^` with their own message; `testNetworkProxy.testSendReopensAClosedSocket` fails with
its own message when `listen()` is cut out of the reopen. The Duplicate test does not - `TWV-B1`.

---

## What I could not execute

- **`T10-C2` end to end.** Driving Combine needs a window and a right-click. Steps 3-5 of the chain
  are read. Worth an `MT-###`.
- **`TWV-B3`, `TWV-B4`.** Both go through a modal dialog or a diagram gesture. Hands-on tests.
- **`TWV-C2`, `TWV-C3`.** Both are races with windows of a few instructions.
- **The battery.** Not run, by instruction. But `regression.testSwitchingToACentralStationLayout` is
  in it and fails deterministically on the tree as committed - `TWV-B2`.
