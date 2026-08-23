# General code review - the two days to 2026-08-22

**Status:** open

**Acted on the same evening, 2026-08-22.** Four findings answered; the tables below are updated in
place rather than left to mislead.

**GC-B2 and GC-B3 were both right and both mine, and they are the two worth reading.** Ctrl+G could
never fire in either mode - filed as fixed, and it would have come back as "does not work". And six
test classes were missing from `build.xml`, one of them added in the very commit that closed DD-A2 and
one of them the guard for the four defects around the editor switch. DD-A2 was closed by adding
thirty-four lines to a hand-kept list; this pass found the list had already fallen behind again. So the
fix this time is `testEveryTestIsInTheBattery`, which fails when it does.

**GC-A1 is half closed, and the half that is closed is not the half the finding proposed.** The
appearance symptom was fixed in `LayoutGrid` by asking whether a grid is inside an editor rather than
reading the shared flag. But the finding was right about the ordering: `repaintLayout` submits to a
single-thread `ExecutorService` and only calls `invokeLater` from inside it, so an extra `invokeLater`
cannot order against a task that has not been posted. **The comment claiming that ordering, and the
test pinning it, were both wrong and are corrected** - the test now pins the thing that actually holds.
What remains open is clickability, which still reads `layout.getEdit()` off the shared diagram.

**Prefix for citing this document: `GC`.** Severities per [README.md](README.md).

**Reviewed at `2fd94f86`** (`autonomy-diagram-r0` HEAD), covering the 102 commits in
`git log --since="2 days ago"` - from `2e29c698` to HEAD.

**Nothing was changed by this pass.** This document is the only file it wrote. No source, test or
resource file was touched, nothing was compiled, and the test battery was not run: another one was
running and holds UDP 15730.

**The working tree was already dirty when this pass began** - `TileAnnotation`, `AutonomyEditorPanel`
and `LayoutGrid` carry uncommitted work on MT-098/MT-099/MT-101, plus four files under
`cs2_sample_layout/config/`. Every finding below was checked against the committed HEAD, and none of
them touches a line that work changes. Said explicitly because a reader running `git diff` will see
those three files and wonder.

**Every finding was re-checked against the source by hand before it was written down.** Two candidates
did not survive that check and are in **D** with the reason, which is the part of this document worth
reading second.

**Method.** `git log`, `git log -p` and `git show` over the window; then a line-by-line reading of
`LayoutEditor.arriveAt` against the `LayoutEditor` constructor and against `render()`, since the brief
named that as the likely place for a third `OB-016`/`OB-017`-shaped defect; then `setAutonomyMode` in
both directions; then the EDT queue between `leaveFor`, `TrainControlUI.repaintLayout` and
`LayoutGridRenderer`; then the keyboard dispatcher; then the message bundles and `build.xml` by script.

---

## What this extends rather than repeats

Both existing reviews of this window were read first.

| Their finding | What this pass adds |
|---|---|
| **IR-C4** - `setAutonomyMode(null)` is not "nearly free", left unfixed because "the branch is dead today, nothing calls it" | **That is no longer true.** `IR` was written at `532fa00e`; `ccd553c5` landed after it and `arriveAt` now calls `setAutonomyMode(null)` on every switch out of autonomy mode. `OB-017` fixed two of IR-C4's four bullets (the palette, the heading). **GC-A2** is the third, now live. The fourth turned out not to be a defect at all - see **GC-D2**. IR-C4 should be reopened and re-severitied. |
| **OB-016** / **MT-106** - the viewer was drawn in edit mode while the editor was open | **GC-A1.** The fix is one extra `invokeLater` in each direction of `leaveFor`. It does not establish the ordering its comment claims, and it says nothing about the repaint that `arriveAt` itself provokes one line before it sets the flag. |
| **OB-019** / **MT-109** - Ctrl+G for the track lengths | **GC-B2.** The shortcut cannot fire. Two guards written a fortnight apart are mutually exclusive. |
| **DD-A2** - the matrix test was one of thirty-five classes `ant test` never ran, closed in `ae94421a` | **GC-B3.** Six more classes have accumulated since, five of them added *after* DD-A2 was closed and one *in the same commit that closed it*. The closure fixed the instances; the mechanism that produced them is a hand-kept list, and it has already failed again. |
| **DD-A1** - the store's eleven collections, fourteen sites | Not re-reported. The one thing worth adding is in **GC-D5**: the matrix test that guards it covers five of those fourteen sites, not fourteen. |

Nothing below duplicates an open entry in [`../manual-tests/tests.md`](../manual-tests/tests.md).
Where a finding is about a fix that is sitting there as `fixed unvalidated`, the `MT-###` is cited.

---

## A - high

| # | Finding | Status |
|---|---------|--------|
| GC-A1 | `arriveAt` sets the shared edit flag in front of every repaint it causes, so the main window's diagram is rebuilt in edit mode after a switch. OB-016 again, by the route its fix does not cover | Half closed - the LAYOUT half is fixed in LayoutGrid and the false comment and test are corrected; CLICKABILITY still reads the shared flag |
| GC-A2 | Leaving autonomy mode never undoes the findings-panel swap. The list stays across the bottom of the track editor, and coming back mounts nothing - the new session's findings are invisible | Closed `3aa24314`+ - the teardown unmounts it, and the contract is pinned by test |

### GC-A1 - the edit flag is set in front of the repaint, not behind it

**Where.** `src/org/traincontrol/gui/LayoutEditor.java:4239` and `:4287`;
`src/org/traincontrol/gui/TrainControlUI.java:2728`, `:16052`, `:19933`, `:19943`, `:366`.

`OB-016` (`243cdba9`) diagnosed this exactly right and fixed it in the wrong place. Its remedy is one
extra `SwingUtilities.invokeLater` in each branch of `leaveFor` (`LayoutEditor.java:4190` and `:4201`),
with a comment saying the switch now lands "BEHIND the repaint the teardown has already queued". Three
things are wrong with that.

**1. The teardown's repaint is not on the EDT queue when `arriveAt` is posted.** `repaintLayout` does
not `invokeLater`; it does

```java
this.LayoutGridRenderer.submit(() -> javax.swing.SwingUtilities.invokeLater(() -> { ... }));
```

(`TrainControlUI.java:19943`), and `LayoutGridRenderer` is `Executors.newFixedThreadPool(1)`
(`:366`). `submit` returns immediately; the `invokeLater` that actually queues the EDT work is issued
later, by the worker thread. `leaveFor`'s `invokeLater(() -> arriveAt(...))` is issued **directly on
the EDT, in the same event**, so it is normally queued *first*. The extra hop makes the race narrower
in one direction and wider in the other, and it is still a race - which is why `MT-106` ends with
"worth doing more than one pass on".

**2. `arriveAt` causes a repaint of its own, and that one cannot possibly win.** Line 4239:

```java
parent.selectLayoutPage(page);
```

`selectLayoutPage` is `this.LayoutList.setSelectedItem(page)` (`TrainControlUI.java:2728`), which
fires `LayoutListActionPerformed` -> `repaintLayoutFromCache()` (`:16052`). Forty-eight lines later, at
`:4287`, still inside the same EDT event, `arriveAt` runs `layout.setEdit()`. A task posted to the EDT
cannot run before the event that posted it has finished, so **that repaint always sees
`layout.getEdit() == true`.** `LayoutGrid` reads the flag off the shared `LayoutDiagram` when it builds
each cell - `new LayoutLabel(c, master, size, ui, layout.getEdit())`, `LayoutGrid.java:223` - and
`master` in the viewer is `KeyboardTab`, not a `LayoutEditor`. That is the greying, the grid lines, the
dead clicks and the `ClassCastException` `setEdit(boolean)`'s own javadoc describes.

Worth noting because it is easy to talk yourself out of: `JComboBox.setSelectedItem` calls
`fireActionEvent()` **unconditionally**, outside the "did it change?" branch, so this fires on a mode
switch that stays on the same page as well as on a page change.

**3. The only thing that saves it is a cache that the teardown has just emptied.**
`repaintLayoutFromCache` passes `useCache = true`, so a page already in `layoutCache` is re-hung
without building a grid. But `repaintLayout(false, false)` - which both teardowns call, via
`autonomyEditorClosed` and `layoutRefreshComplete` - does `this.layoutCache = new HashMap<>()`
(`TrainControlUI.java:20029`) and re-caches only the page being left. So on a **page** switch the
arriving page is a cache miss and is rebuilt in edit mode deterministically; on a **mode** switch the
outcome depends on which of the two repaints runs first, which is point 1.

**Severity A** rather than B: this is wrong behaviour on the layout surface the operator drives trains
from, it persists until the editor is closed, and clicking an accessory in that state does nothing.

**The fix I would make, and it is not another `invokeLater`.** This is the third round on one ordering
question, and the reason is that "am I in edit mode?" is model state on a `LayoutDiagram` that two
views share. `LayoutGrid` already takes a `popup` flag and already knows its `master`; give it the edit
flag explicitly - the editor passes true, `repaintLayout` passes false - and the ordering stops
mattering at all. The smaller stopgap, if that is too much for one commit, is to guard the viewer
rebuild with the `isLayoutEditorOpen()` predicate that already exists at `TrainControlUI.java:3378`
and is used by two menus and nothing else.

**Test.** `testEditorSwitchClearsPageState.testTheSwitchLandsBehindTheQueuedRepaint` counts two
occurrences of `invokeLater(() -> arriveAt` in `leaveFor`. That is a shape, not the property its name
claims, and it passes today while the property does not hold. `MT-106` is the hands-on test and it
should be extended to say *switch PAGE as well as mode* - Adam's failing run of it was a mode switch.

### GC-A2 - leaving autonomy mode puts the palette back and leaves the findings list where it was

**Where.** `LayoutEditor.java:1173`, `:1201-1226`, `:1428-1461`.

This is `OB-017`'s twin, in the same method, six lines below the branch `OB-017` fixed.

Entering autonomy mode takes the diagram's scroll pane out of the form's `GroupLayout` and puts a
stack in its place - the scroll pane in the middle, the findings list and its count across the bottom:

```java
if (autonomyFindings == null && autonomyPanel.getFindingsPanel() != null)
{
    javax.swing.JPanel stack = new javax.swing.JPanel(new BorderLayout());
    ...
    ((javax.swing.GroupLayout) formPane.getLayout()).replace(this.jScrollPane1, stack);
    stack.add(this.jScrollPane1, BorderLayout.CENTER);
    ...
    autonomyFindings = stack;
}
```

The `session == null` branch above undoes three things - the panel, the banner, the visibility column -
and does not undo this one. `autonomyFindings` is assigned in exactly one place and read in exactly
one place; nothing ever clears it. Two consequences, and the second is worse:

- **Going back to the track editor**, the findings list and the "so many things to fix" count stay
  across the bottom of the window, under a track diagram they are no longer about. Roughly 190px of a
  window that `OB-003` was filed to make big enough.
- **Coming back to autonomy mode**, `autonomyFindings == null` is false, so the whole block is skipped.
  `AutonomyEditorPanel` builds `findingsPanel = buildFindings()` in its constructor
  (`AutonomyEditorPanel.java:319`) and deliberately does **not** add it to itself - "the findings are
  built here but mounted by the WINDOW" - so the new session's findings panel is now an orphan with no
  parent, and the list on screen is the previous panel's, frozen. Its `ListSelectionListener` is the
  old panel's too, and it holds the old panel's tile list, so clicking a row reveals or jumps to a
  square taken from a setup that is no longer being edited.

The findings list is how the user learns their setup will not run. Showing a stale one and hiding the
live one is the failure mode where "I fixed it and it still says it is broken" comes from - which is
the same complaint `autonomyEditorClosed`'s `refreshAutonomyPrompt` comment was written about.

**Fix.** Mirror what `OB-017` did: in the `session == null` branch, `replace(autonomyFindings,
this.jScrollPane1)` under the same `instanceof GroupLayout` guard the other three use, pull
`jScrollPane1` back out of the stack first, and set `autonomyFindings = null`. `buildPalette()` is the
model - it rebuilds rather than hides, for the same reason (the container's layout manager changes).

**Test.** `testEditorSwitchClearsPageState` already reads `setAutonomyMode`'s body for
`buildPalette()`. One more assertion in the same style - that the body names `autonomyFindings` in the
teardown branch - costs three lines and is verifiable by mutation the way the palette one was.
`MT-107` is the hands-on twin and this deserves a sibling entry beside it.

---

## B - medium

| # | Finding | Status |
|---|---------|--------|
| GC-B1 | The two visibility checkboxes are not re-read from the arriving page, so after a switch they lie - and the first click on one does the opposite of what it says | Open |
| GC-B2 | Ctrl+G, the whole of OB-019's first part, cannot fire in either mode | Closed - Ctrl+G handled above the autonomy-mode guard, since it is FOR autonomy mode |
| GC-B3 | Six test classes are not in `build.xml`, including every regression test for OB-003, OB-005, OB-016 and OB-017 | Closed - six added, and `testEveryTestIsInTheBattery` now fails when the list falls behind |

### GC-B1 - `arriveAt` re-points the window at a new page and leaves the checkboxes describing the old one

**Where.** `LayoutEditor.java:466` (constructor), `:4216-4347` (`arriveAt`), `:3569-3584`
(`toggleAddresses`), `:3496-3511` (`toggleText`), `:1403-1417`.

The constructor mirrors the page's own preference into the control:

```java
this.showAddressCheckbox.setSelected(l.getShowAddress());
```

`showAddress` and `editHideText` are fields on `LayoutDiagram` (`LayoutDiagram.java:76-77`), so they
are **per page**. `arriveAt` assigns `this.layout = arriving` at `:4276` and never re-reads either
one. Neither checkbox is touched anywhere between `:4216` and `:4347`.

The arriving page's real values are not "whatever the user last chose in the editor", either. A track
switch goes through `layoutEditingComplete` -> `model.refreshLayouts()`, which clears and repopulates
the layout database, so every `LayoutDiagram` is a **new object** with both flags at their defaults;
`repaintLayout` then sets `showAddress` on the *selected* page from the main window's own menu
preference (`TrainControlUI.java:19999`). Whatever the editor's checkbox says after a switch, it is
not a fact about the page on screen.

**What the user sees.** Tick **Addresses** on page A. Switch to page B: no addresses are drawn, and
the box is still ticked. Click it to turn them off - Swing unticks it, the handler runs
`toggleAddresses()`, which computes `!layout.getShowAddress()` = `!false` = **true**, draws the
addresses, and re-ticks the box. The control has done the opposite of what it said and snapped back.
Identically for **Text Labels** through `toggleText()`.

**And it defeats a rule that exists for legibility.** `setAutonomyMode` at `:1403`:

```java
if (autonomyPanel.getShowLengths().isSelected() && this.showAddressCheckbox.isSelected())
{
    this.showAddressCheckbox.setSelected(false);
    toggleAddresses();
}
```

That pairing is only correct while the box and the diagram agree. Enter autonomy mode with lengths
remembered on and a stale-ticked Addresses box, and it runs `toggleAddresses()` against
`showAddress == false`, turning addresses **on** - two numbers on every square, which is the exact
thing the comment three lines above says cannot be read. The listener at `:1409` has the same shape.

**Fix.** Two lines in `arriveAt`, beside the selection clears, with the reason: the checkboxes describe
the page, and the page has changed.

### GC-B2 - Ctrl+G is unreachable

**Where.** `LayoutEditor.java:499-504` and `:4953` against `:5034-5037`.

`OB-019` added a shortcut for the track lengths. The handler is a branch of `formKeyPressed`:

```java
else if (evt.isControlDown() && evt.getKeyCode() == KeyEvent.VK_G)
{
    this.toggleTrackLengths();
}
```

`formKeyPressed` opens at `:4953` with `if (isAutonomyMode()) return;`, and `toggleTrackLengths` opens
at `:501` with `if (autonomyPanel == null) return;`. `isAutonomyMode()` is
`autonomyPanel != null && autonomyPanel.isVisible()`. The two guards are complements: in autonomy mode
the dispatcher returns before reaching the branch, and in track mode the branch is reached and the
method returns immediately. **There is no state in which pressing Ctrl+G does anything.**

The commit message reasons carefully about both modes - "Silent in the track editor, where there are
no lengths" - and the mode it describes as silent is the only one the key can reach. That is the
`isAutonomyMode()` early return doing exactly what it was written to do (`"Every shortcut below places,
cuts, rotates or retextures a tile"`) to the one shortcut below it that does not.

`MT-109` is filed as `fixed unvalidated` and will come back as "does not work" the moment somebody
presses the key.

**Fix.** Move the `VK_G` test above the `isAutonomyMode()` guard, which is where it belongs anyway -
it is the only shortcut in the method that is *about* autonomy mode. `toggleTrackLengths`'s own null
check then carries the "silent in the track editor" behaviour on its own, which is what it was written
for.

The two other parts of `OB-019` - `showLengths.setFocusable(false)` and the length font size - are
both correct and are unaffected. See **GC-C1** for the sibling the focus fix did not sweep.

### GC-B3 - six test classes are not in the battery, and the list that decides is hand-kept

**Where.** `build.xml:118` onwards; `test/regression/`.

`build.xml` lists 75 classes. `test/` holds 84 `.java` files, of which two (`test/support/CS3TestServer`
and `test/support/TestStationAddress`) are helpers with no `@Test` and one (`testAutoDetect`) is
excluded on purpose and documented as such. That leaves **six real test classes that `ant test` does
not run**:

| Class | Added by | What it guards |
|---|---|---|
| `testEditorSwitchClearsPageState` | `ccd553c5` | OB-003, OB-005, OB-016, OB-017 - the whole per-page-state contract |
| `testDataSafetyRoundTrips` | `e4ac9442` | MT-074, MT-075 - export/import leaves the source files byte-identical; the page `.bak` |
| `testCancelRestoresPlacements` | `0651d57c` | Cancel putting placements back |
| `testTrainMarkIsNotBlank` | `0651d57c` | FR-005 - `isBlank()` not counting a train as content |
| `testAutonomyLabelShowsLocomotiveName` | `cd27e285` | MT-093 - the label drawing its JSON |
| `testLocomotiveAddressRules` | `34ae94ad` | MT-090 - Add Locomotive refusing address 0 |

`build.xml` was last touched by `34ae94ad`. `testLocomotiveAddressRules` was created **in that same
commit** and not listed; the other five were added after it. `MT-091` records Adam confirming
`ant test` "works" against `fc672631` - which is true, and it was already missing four of these at
that point.

This is `DD-A2` recurring within hours of being closed, and the recurrence is the finding rather than
the six lines. `build.xml:102` already says "Adding a test class means adding a line here. That is the
price of the per-class isolation" - a comment in the right place, read by nobody adding a class, six
times running.

**Fix.** Add the six lines, and then close the mechanism: a test that reads `build.xml`, globs
`test/**/test*.java`, and fails naming any class that carries `@Test` and is not listed (with
`testAutoDetect` as the one declared exception, exactly the way
`testAutonomyStoreSettingsMatrix.NOT_KEYED_BY_SQUARE` declares its exceptions). That test is about
thirty lines, has no dependencies, and is the only version of this fix that survives the next person.
It has to be listed in `build.xml` itself, which is a pleasing amount of the joke.

---

## C - low

| # | Finding | Status |
|---|---------|--------|
| GC-C1 | The `unfocusable` sweep cannot reach the findings list, which is the one focusable control left in the editor | Open |
| GC-C2 | `showDiagramSize()` is not re-run on a switch, so the Diagram Size tooltip names the page you left | Open |
| GC-C3 | `arriveAt`'s modal pumps events while the main window's Edit button is enabled, so a second editor can be opened onto the same diagram | Open |
| GC-C4 | `sizeForDiagram` writes its own result back to preferences, so "fitted" and "chosen" become indistinguishable after the first visit | Open |
| GC-C5 | `testEditorSwitchClearsPageState.methodSource("arriveAt")` finds a call, not the declaration, and reads the right body only by luck | Open |

### GC-C1 - the sweep that makes the editor's shortcuts work misses the findings list

`AutonomyEditorPanel.java:326-329`:

```java
// Nothing in this column is worked by keyboard, and a control that takes focus swallows the
// key presses the window around it uses.  button() already does this one control at a time;
// the sweep covers the lists and anything added later that forgets.
AutonomyViewerPanel.unfocusable(this);
```

`unfocusable` recurses over `getComponents()` (`AutonomyViewerPanel.java:186-198`), so it reaches only
what is already a descendant of the panel. `findingsPanel` is built ten lines earlier at `:319` and is
deliberately never added to the panel - the window mounts it. So the one `JList` in the editor that a
user is *meant* to click is the one control the sweep does not reach, and neither it nor its scroll
pane is set unfocusable anywhere else.

Latent today rather than live: `formKeyPressed` returns immediately in autonomy mode, so there is no
shortcut to steal while the list is on screen legitimately. It stops being latent the moment **GC-A2**
is true - the list stays mounted in track mode, where every shortcut is live - and it would bite
directly if any of the diagram shortcuts were ever allowed in autonomy mode.

The comment claiming the sweep "covers the lists" is worth correcting either way; it is the kind of
sentence a later reader will trust.

### GC-C2 - the Diagram Size tooltip is about the previous page

`showDiagramSize()` (`:3389`) writes `layout.getSx() x layout.getSy()` into the heading's tooltip. It
is called from the constructor (`:480`), `growEdges` (`:3417`) and `shrinkEdges` (`:3456`) - and its
own javadoc says why: "Re-read after every grow and shrink, because a number written once at startup is
wrong from the first press." A page switch is a third way for the number to change, and `arriveAt` does
not call it. One line.

### GC-C3 - the "cannot edit while running" dialog opens a door to two editors

`arriveAt:4227-4235` shows a modal when the setup has become unavailable between the click and the
switch. `parent.setEditLayoutEnabled(false)` is not until `:4314`. Both teardowns re-enable that button
on the way out - `layoutRefreshComplete` and `autonomyEditorClosed` both do, because for every caller
they ever had an editor really had closed - so between the teardown and line 4314 the main window's
**Edit Layout** button is live. A modal pumps the event queue, so the user can press it.

`openLayoutEditor` guards against exactly this and says why: "Two editors share one `LayoutDiagram` and
one edit flag: closing either one sets edit false under the other, whose tiles then stop routing clicks
to it, and re-enables the button so a third can be opened on top." The guard is
`if (!this.editLayoutButton.isEnabled())`, and here it is enabled.

Narrow - it needs the setup to become unavailable mid-switch - but the fix is to move
`setEditLayoutEnabled(false)` to the **first** line of `arriveAt`, before anything that can show a
dialog, rather than the last. There is no state in which `arriveAt` running should leave that button
pressable.

### GC-C4 - the fit becomes a decision the first time it is applied

`arriveAt:4305-4308` and `render:3923-3944` both do `sizeForDiagram()` when there are no remembered
bounds, and then `saveWindowBounds()`. `sizeForDiagram` also calls `pack()`, which fires
`componentResized`, which `PositionAwareJFrame` has wired to `saveWindowBounds` as well. So the fitted
size is written into preferences immediately, and from then on `hasRememberedBounds()` is true for that
page and that tile size, forever.

`OB-003`'s own comment draws the distinction - "A remembered size is a decision; the diagram fit is
what to do in the absence of one" - and the code erases it on first use. In practice a page is fitted
exactly once, ever, so growing a diagram afterwards leaves the window at the old fit. (With "remember
window location" switched off, `saveWindowBounds` and `hasRememberedBounds` both short-circuit and the
fit runs every time, which is the behaviour the comment describes.)

Low because the outcome is defensible - it is what "remember where I left it" means - but it is not
what the comment says, and `MT-096` will be re-run against the comment.

### GC-C5 - the source-reading helper finds a call, not a method

`test/regression/testEditorSwitchClearsPageState.java`, `methodSource`:

```java
int at = all.indexOf(" " + name + "(");
```

For `"arriveAt"` the first match in `LayoutEditor.java` is the **call** at `:4190`
(`-> arriveAt(page, autonomy)`), not the declaration at `:4216`. It then takes `indexOf('{', at)` and
counts braces - and gets the right body only because there happens to be no `{` in the twenty-six
lines between the call and the declaration. A lambda added to `leaveFor` after that call, or a comment
containing a brace, would silently point the whole class at a different method, and every assertion in
it would go from "arriveAt clears this" to "some other method mentions this" without failing.

The class already closed the sibling hole (`testTheNamedFieldsExist`, and stripping comments after its
first false positive), so this is the same lesson one layer down. Anchoring on
`"private void " + name + "("` fixes it and would have failed loudly rather than passing by luck.

---

## D - considered and rejected

| # | | |
|---|---|---|
| GC-D1 | The caption undo stacks are not cleared by `arriveAt` - and self-heal | Recorded |
| GC-D2 | **Correction to IR-C4:** the lengths-toggle listener does not leak across a mode switch | Recorded |
| GC-D3 | `groupClipboard` surviving a page switch is a feature, not stale state | Recorded |
| GC-D4 | `mountSidebar`'s early return cannot leave a stale strip in practice | Recorded |
| GC-D5 | The store's settings matrix covers five of DD-A1's fourteen sites - a gap, not a defect | Recorded |
| GC-D6 | Checked clean: bundles, menu rebuilds, `refreshGrid`'s lock, the window listener | Recorded |

### GC-D1 - `previousCaptions` looked like the fifth field `arriveAt` forgot, and is not

`arriveAt:4273-4274` clears `previousLayoutComponents` and `previousLayoutComponentsRedo` and does not
clear `previousCaptions` or `previousCaptionsRedo`, whose own javadoc (`:306-318`) states the invariant
"pushed together, popped together, and the same size at every moment". That reads like a data-loss bug
of exactly the shape `OB-005` was about: pop a caption snapshot belonging to page A while restoring
page B's components, and `restoreCaptions` writes page A's names onto page B.

It cannot happen. `snapshotLayout` (`:3729`) ends with

```java
while (this.previousCaptions.size() > this.previousLayoutComponents.size())
{
    this.previousCaptions.removeLast();
}
```

which trims from the **tail**, and both stacks push at the head. After a switch the components stack is
empty and the captions stack holds N stale entries; the first edit on the new page pushes one to each
and the loop drops all N old ones, oldest first, leaving the new page's snapshot at the head. Before
that first edit, `canUndo()` is false and `undo()` cannot run at all, and `redo()` is gated on
`previousLayoutComponentsRedo`, which *is* cleared. `redo()` carries the same trim at `:3820`.

So the stacks are momentarily unequal and never wrong. Recorded rather than dropped because the
invariant in the javadoc is genuinely violated between the switch and the next edit, and the next
person to read that comment and this method will reach for exactly this finding. If anything is done
here it is to clear the two stacks in `arriveAt` for honesty, not for correctness.

### GC-D2 - the fourth bullet of IR-C4 is not a defect

IR-C4 lists "a listener added to the lengths toggle" among the things entering autonomy mode does and
leaving does not undo (`LayoutEditor.java:1409`). Checked: `showLengths` is
`private final JCheckBox` on `AutonomyEditorPanel` (`:221`), an instance field, and leaving the mode
sets `autonomyPanel = null` so the next entry builds a new panel with a new checkbox. The listener dies
with the panel it belonged to; nothing accumulates. The other three bullets were real - two fixed by
`OB-017`, one open as **GC-A2**.

### GC-D3 - the group clipboard is meant to cross pages

`groupClipboard` is a list of `CarriedTile`, each holding a `dx`, a `dy` and a component copy
(`:280-297`) - offsets from the top left of what was copied, with no page name anywhere in it.
`arriveAt` clears the three selections and `toolFlag` and leaves this alone, and that is right: copy a
group on page A, switch to page B, paste. The commit that added `cutSelection` says the missing verb
mattered precisely because "moving several squares to another page" was three steps. Not a finding.

`lastComponent` is likewise left uncleared, and is unreachable without `toolFlag`, which is cleared -
every read of it is behind `hasToolFlag()` or an explicit null test (`:729`, `:2000`, `:2672`).

### GC-D4 - `mountSidebar` returning early

`mountSidebar:4441-4449` returns before building anything when there is one page and no setup to
switch to, which would leave the previous wrapper and the previous `sidebar` field in place. To reach
`arriveAt` at all the user has to have clicked a page (needs more than one) or a mode tab (needs the
mode control to exist), so the early return is only reachable if the configuration is unloaded between
the click and the arrival, on a one-page railway. Even then the surviving strip is functional and
`syncSidebar()` at `:4335` puts its selection right, because it operates on the same objects. Left.

Note for anyone changing that method: `isAutonomyMode()` is always **false** when `arriveAt` calls it,
because `setAutonomyMode(null)` runs at `:4282` and `setAutonomyMode(session)` not until `:4295`. The
`|| isAutonomyMode()` half of `offersModes` is therefore dead on this path. Harmless today.

### GC-D5 - the settings matrix is complete down one axis and not the other

`testAutonomyStoreSettingsMatrix` is a good test and its reflective guard
(`testEveryCollectionInTheStoreIsAccountedFor`) is the right answer to `DD-A1`'s failure mode. Worth
recording what it does not cover, because the file's own header invites the reader to think it covers
everything: it asserts five operations - `moveTiles`, build-over, `snapshotPage`/`restorePage`,
`renamePage`, save/load. `DD-A1` enumerates fourteen per-collection sites. `forgetSquares`, `clear`,
`clearShared`, `reconcile` and `applyTo` have no row. `applyTo` is the one that matters most - a
collection it does not know about never reaches the running railway at all - and `reconcile` is the one
that deletes settings for empty squares.

Not raised as a defect because no collection is actually missing from any of them today; raised because
the guard's failure message tells the next author "add each to SETTINGS - so that moving, building
over, restoring, renaming and saving are all checked against it", and that list is five of fourteen.
This belongs to `DD-A1`'s work rather than to this pass.

### GC-D6 - checked and sound

Recorded so the next reviewer does not repeat the trip.

- **The message bundles are clean.** All eight files, 1767 keys each, identical key sets, and zero
  bytes above 0x7F in any of them. Every literal key passed to `I18n.t`/`I18n.f`/`bundle.getString`
  across `src/` resolves; the seven apparent misses are all prefixes concatenated with an enum name
  (`"autosetup.ui.side" + side.name()`) or examples in `I18n`'s own javadoc.
- **`mountEditPageMenu` and `mountCombinePagesItem` remove before re-adding** (`TrainControlUI.java:1939`,
  `:1969`), so the repeated mounting that used to duplicate the Combine item cannot duplicate either.
  `OB-021`'s index search reads the current menu rather than a constant, as its comment claims.
- **`tidySeparators` is convergent and its bounds are right** - it removes a separator only when the
  previous component was one (`previousWasSeparator` starts true, so a leading separator goes), and
  trims trailing ones in a second loop. It does not renumber under itself: the index is not advanced
  on removal.
- **`refreshGrid`'s coalescing is sound.** The recursive `refreshGrid()` from inside the `finally`
  happens while the thread already holds `lock`, which is a `ReentrantLock`, and `isRunning` is set
  false before the re-entry sets it true again. No lost rerun and no deadlock.
- **`arriveAt` correctly does not repeat the parts of `render()` that are about being a window** - no
  second `WindowAdapter`, no `setVisible` on a visible frame, no `setAlwaysOnTop`. Its comment at
  `:4284` says so and it is accurate. The window listener is added once, inside `render()`'s queued
  body.
- **`mountSidebar` re-wraps rather than nests.** It builds a fresh wrapper each time and puts the
  remembered `formPane` in it, so repeated calls cannot bury the form one panel deeper each switch.
- **`confirmExitWithoutAsking` is correct on both of its paths** - it clears the edit flag on the
  diagram it is actually holding, hands the button back, and does not re-run a teardown that
  `leaveFor` has already run.

---

## Ranking

By expected value: pain removed divided by risk taken. This is the order I would do them in, and it is
not the order they are numbered in.

| Rank | Finding | Why here |
|---|---|---|
| 1 | **GC-B3** | Six lines in `build.xml`, zero risk, and until it is done every other fix on this list ships without its guard being in the gate. The meta-test can follow in the same commit or the next one. |
| 2 | **GC-A2** | A user-visible A, contained to one method, and `OB-017` six lines above is the template for the fix. ~15 lines. |
| 3 | **GC-B2** | One branch moved above one guard. A feature that currently does nothing starts working. |
| 4 | **GC-B1** | Two lines in `arriveAt`, and it also closes the lengths/addresses exclusion inverting itself. |
| 5 | **GC-C2**, **GC-C3** | One line each, both in `arriveAt`, both natural companions to GC-B1's commit. |
| 6 | **GC-A1** | The largest win and the largest blast radius. Passing the edit flag into `LayoutGrid` explicitly touches both call sites and the viewer's own rebuild, and it wants its own commit and its own hands-on pass. Do not attempt it in the same change as anything else here. |
| 7 | **GC-C5** | Ten minutes, and it is the test the other fixes will be leaning on. Worth doing before GC-A1, not after. |
| 8 | **GC-C1**, **GC-C4** | Cleanups. GC-C1 stops being latent once GC-A2 lands, so it can ride along with it. |

**I would do GC-B3 first.** Not because it is the worst thing here - GC-A1 is - but because it is the
only one whose absence makes the others harder to land safely: five of the six missing classes pin the
exact behaviour the rest of this list changes, and right now a green `ant test` says nothing about any
of them. It is also the second time in one day that this has been the top of a ranking, which is the
argument for spending the extra thirty lines on the test rather than the six on the list.

Among the things a user would notice, **GC-A2** first: it is the same shape as two defects already
fixed this window, the fix is a copy of one of them, and it is the one where the application actively
misleads - a findings list that belongs to a session nobody is editing any more.
