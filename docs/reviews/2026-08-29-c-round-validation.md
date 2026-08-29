# Validation of the C round - the fixes made on top of the VAL pass

**Status:** open - both B findings and VB-C1 are closed (see **Dispositions** at the end); the
remaining C items are not.

**Prefix for citing this document: `VB`.** Cite findings from here as `VB-B1`, `VB-C4` and so on. `VB`
is not declared by any other document in `docs/reviews/`; every two-letter prefix already taken is
listed in [2026-08-25-last-day.md](2026-08-25-last-day.md) and `VB` is not among them.

**Version reviewed:** the WORKING TREE, uncommitted, on top of `17ddd270` ("Five reviews, seven
fixers, a validator, and the three defects they found in each other"), branch `autonomy-diagram-r0`.
`git diff HEAD | md5sum` of the state reviewed: `a4fbbb3c4dd8e7fddad9f9dacf9a0f06`. **Reviewed:**
2026-08-29. Nothing in the tree changed while this pass ran; the newest source file is
`LayoutGrid.java` at 02:54, and every run below was made against that state.

**No source or test file was changed by this review.** The three mutations described below were made
on COPIES held outside the repository and compiled into a separate output directory that was placed
ahead of the real build on the classpath. `cs2_sample_layout/` was not written: `one.sh`'s live-layout
fingerprint passed on every run through it, and the folder's one modified file
(`config/autonomy/configuration-Main.json`) carries exactly the diff it had before this pass began.

This document is the round after [2026-08-29-round-validation.md](2026-08-29-round-validation.md)
(`VAL`), and cites its findings by that prefix. It also cites `UXR`
([2026-08-28-ux-consistency-review.md](2026-08-28-ux-consistency-review.md)), `TST`
([2026-08-28-test-suite-review.md](2026-08-28-test-suite-review.md)), `WK`
([2026-08-28-week-review.md](2026-08-28-week-review.md)) and `DOC`
([2026-08-28-documentation-review.md](2026-08-28-documentation-review.md)).

---

## Method, and what it could NOT settle

Three claims in the brief were settled by MUTATION - by breaking the production code the test is
supposed to be watching, on a copy, and confirming that the named test and only the named test goes
red. Two ratchets were settled by reimplementing their scan independently and comparing. Everything
else was settled by reading, and where reading was all that was possible this document says so.

**Classes run**, one JVM each, via `one.sh` against this working tree. Format is
`run/failures/skips`, and **skips were checked and are zero everywhere** - a class that skips reads as
green:

| Class | Unmutated | Mutated |
|---|---|---|
| `core.testTrainTailClearsEdges` | 6/0/0 | 6/**1**/0 - `testAnEdgeTheRuleRefusesToClearStaysHeldWhileARealPathRuns` |
| `core.testAutonomyDiagramStore` | 65/0/0 | 65/**5**/0, including `testAnUnreadableImportChangesNothing` |
| `ui.testDiagramExport` | 7/0/0 | 7/**1**/0 - `testAnAbsurdSizeIsCapped` |
| `regression.testJavadocsAreAttached` | 1/0/0 | - |
| `regression.testSwitchingToACentralStationLayout` | 9/0/0 | - |
| `regression.testEveryTestIsInTheBattery` | 4/0/0 | - |
| `regression.testEditorSurfaceRules` | 34/0/0 | - |

The battery was NOT run - the caller had just run it (121 green, 1 skip, OB-132).

**What could not be verified at all, and must be checked by hand.** None of the following is provable
from this chair, and three of them are the entire subject of the round:

- **OB-138.** That a double-click on a station caption now activates the mapped locomotive on a real
  running diagram. What is verified is that the branch is gone, that the code the double-click now
  falls through to is the same code a single click already ran, and that the right-click door to the
  editor is intact. Whether Adam sees the locomotive selected is a display question.
- **OB-139.** That the move cursor is actually absent over a hidden label, and returns when Text
  Labels is ticked back on. What is verified is that `dragCaption` - the only thing in `LayoutGrid`
  that sets `MOVE_CURSOR` - is no longer called in that state, and that `toggleText()` reaches
  `drawGrid()`, which constructs a fresh `LayoutGrid`.
- **OB-137.** That the route table no longer freezes. Swing single-thread violations are intermittent
  by construction; the absence of a freeze in one run proves nothing, and no automated test in this
  repository can see one. The same applies in reverse to VB-B1: the four sites named there are wrong
  by the rule, and I cannot show any of them misbehaving.
- **The `LoadingSpinner.getVisibleRect()` change (WK-C3).** Whether the hourglass now lands on the
  screenful the operator is scrolled to. Not exercised by any test that could tell.
- **The double-click race in VB-D3.** Reasoned from the event queue, not observed.

---

## A - high

**None.** No finding in this round rises to wrong behaviour on the layout or data silently lost. The
`Layout.executePath` change (VAL-C1, the removal of the third slot from `waitingToClear`) is the only
edit in the round that touches running-railway code, and it is verified clean under VB-D6.

---

## B - medium

| | Finding | Disposition |
|---|---|---|
| VB-B1 | `exportJSONActionPerformed` is not the only Swing dialog left off the event thread - three more in the same file, one of them live and reachable | open |
| VB-B2 | VAL-B5's sweep stopped at `TrainControlUI`; six destructive confirmations still pre-select the destructive answer | open |

### VB-B1. `exportJSONActionPerformed` is NOT the only remaining case

The brief states that `exportJSONActionPerformed` is the known remaining off-EDT dialog in
`TrainControlUI.java` and asks for that to be confirmed. It is not. Scanning every `new Thread(` body
in the file with `invokeLater`/`invokeAndWait` bodies stripped first - the same "prose read as code"
precaution `testNoFileChooserIsShownOffTheEventThread` takes, applied to the raw-thread question -
turns up four modal dialogs raised from a background thread, not one:

| Site | What it raises | Reachable from |
|---|---|---|
| `exportJSONActionPerformed` (20113) | modal `JOptionPane` holding an `AutoJSONExport` panel | Autonomy tab, Export |
| `exportRoutesMenuItemActionPerformed` (20166) | the same, for routes | Routes menu, Export |
| `exportLocsToCSVMenuItemActionPerformed` (21461) | the same, for the locomotive CSV | Locomotives menu, Export |
| `BulkEnableOrDisable` (17059) | modal `JOptionPane.showInputDialog` | Routes tab, Bulk Enable / Bulk Disable |

The last one is the worst of the four and is not an export dialog at all. `BulkEnableActionPerformed`
(18472) and `BulkDisableActionPerformed` (18464) each start a raw thread whose only statement is a
call to `BulkEnableOrDisable`, which starts a SECOND raw thread and shows a modal input dialog on it.
Two nested background threads and a modal dialog on the inner one, on a control an operator uses to
enable or disable every route on the railway at once. The same block then calls `this.repaintLayout()`
off the event thread (`refreshRouteList()` beside it is safe - it wraps its own body in
`invokeLater`).

Two more of the same shape sit outside `TrainControlUI` and outside the new guard's reach:
`AutoJSONExport.java:97-103` shows a modal `showSaveDialog` inside `new Thread(`, and
`LocomotiveFunctionAssign.java:522-557` shows a modal `showOpenDialog` inside `new Thread(`. The first
of those is the panel the three export handlers above put INSIDE their own off-thread dialog, so that
path is off the event thread twice over.

**Why the round's own regression test does not catch any of them.**
`testEditorSurfaceRules.testNoFileChooserIsShownOffTheEventThread` reads one file
(`src/org/traincontrol/gui/TrainControlUI.java`) and searches for one needle (`showOpenDialog(`). It
therefore cannot see `showSaveDialog`, cannot see any other file, and by construction cannot see a
`JOptionPane` at all - which is what all four of the sites above raise. The test is correct for what
it claims; the claim is narrower than the fault. This is the "fix one site, sweep the siblings"
pattern the folder's README names as the July cycle's most repeated mistake, arriving through the
guard rather than through the code.

Also noted and deliberately not counted here, because the brief scopes this to dialogs: six raw-thread
bodies in the same file call `setEnabled` on menu items and buttons directly (15637-15638, 17538-17539,
17573-17574, 17589-17590, 17629/17824, 21201). Those are Swing calls off the event thread too, but
they are property writes rather than window creation and they long predate this round.

### VB-B2. VAL-B5's sweep stopped at the file it started in

OB-134 records six destructive confirmations that pre-select Yes. Six were fixed, all six in
`TrainControlUI.java`, each with a `VAL-B5:` comment: delete route (15390), delete a page's key
mappings (15741), delete a locomotive from the database (16736), clear the whole timetable (17489),
discard unsaved autonomy JSON (19143), delete a track diagram page (20978). Every one of those is
right.

Six MORE sites of the same shape were left, one of them in the same file:

| Site | The question | What Enter does |
|---|---|---|
| `TrainControlUI.java:15697` | `page.ui.confirmReplaceMappings` | overwrites one page of key mappings with another's |
| `LayoutEditor.java:4214` | `layout.ui.confirmDeleteTrackDiagram` | deletes the whole track diagram page |
| `LayoutEditor.java:4675` | `autosetup.ui.confirmExitWithoutSaving` | discards unsaved autonomy edits |
| `LayoutEditor.java:4703` | `layout.ui.confirmExitWithoutSaving` | discards unsaved diagram edits |
| `LocomotiveFunctionAssign.java:465` | `loc.ui.confirmResetFunctionsToCentralStation` | throws away the local function assignment |
| `AutonomyEditorPanel.java:3341` | `autolayout.ui.confirmHomeIsAlreadySet` | moves another station's home locomotive |

`TrainControlUI.java:15697` is the sharpest of the six, because its twin two hundred lines below
(15741, "delete this page's key mappings") WAS fixed in this round with the comment "a page's entire
set of key mappings is destructive to lose to a stray Enter". Replacing that set is the same loss, and
the two sit in the same method family. The two `confirmExitWithoutSaving` sites are the same argument
as the fixed 19143 ("Yes here throws away unsaved autonomy JSON edits"), one window along.

Three sites are correctly left alone and are NOT part of this finding: 6973 (create an empty track
diagram - constructive), 15286 (exit while autonomy is running - the graceful stop has already been
commanded), and 18567 (execute a route - the thing the user pressed the button for).

**Bookkeeping.** OB-134 is still in `issues.md`'s Inbox with no receipt row, although the six it names
are fixed in the tree. So is OB-133, whose fix is verified clean under VB-D5.

---

## C - low

| | Finding | Disposition |
|---|---|---|
| VB-C1 | Three bugs fixed this round have no `MT-###` hands-on test, and they are the three no automated test can cover | open |
| VB-C2 | The javadoc ratchet's per-file breakdown cannot see a swap INSIDE a file | open |
| VB-C3 | The clamp test pins "bigger than a quarter", not "equal to the ceiling" | open |
| VB-C4 | `AutonomyMenu`'s UXR-C13 comment misdescribes the term it dropped, and contradicts the comment four lines above it | open |
| VB-C5 | `testFacingFollowsTheTrack`'s temp copy is never deleted | open |
| VB-C6 | `testRouteInventory` now asserts a floor against the operator's live railway | open |
| VB-C7 | `testEveryTestShapedMethodCarriesAnAnnotation` scans only one directory level | open |
| VB-C8 | Dead GraphStream property, and five unreferenced jars that `one.sh` still depends on | open |
| VB-C9 | `testImportRename` restores a displaced locomotive's name and address and nothing else | open |

### VB-C1. OB-137, OB-138 and OB-139 were fixed without the hands-on test the rules hand out immediately

`docs/manual-tests/README.md` states the rule plainly: "A **bug** becomes a finding in `docs/reviews/`
under that round's prefix, gets fixed, and gets an entry in `tests.md` with a new `MT-###` tag and the
disposition **needs test** - a bug fix needs a repeatable hands-on check that the regression stays
fixed". `issues.md` repeats it: "that tag is handed out immediately, not earned".

`tests.md` gained no new `MT-###` entry in this round, and `issues.md`'s "What has been picked up"
table gained no row. OB-136, OB-137, OB-138 and OB-139 all sit in the Inbox with their fixes already
in the tree. OB-135, filed the same day, has `MT-206` - so the practice is live and these four simply
missed it.

This matters more than a process slip usually would, because of WHICH three. OB-138 is a mouse
gesture on a running diagram, OB-139 is a mouse CURSOR, and OB-137 is an intermittent Swing threading
symptom. Not one of the three can be confirmed by anything in `test/`; the hands-on entry is the only
verification any of them will ever get, and it is the entry that was not written.

### VB-C2. The javadoc breakdown catches a cross-file swap and not a same-file one

`ORPHANS_BY_FILE` in `testJavadocsAreAttached` pins twenty-two lines of the form `path (count)`. I
reimplemented `orphansIn` and `collect` independently: the total is 96, the file set is exactly those
twenty-two, and every per-file count matches, summing to 96. The pin is correct today and the finding
below is about its resolution, not its accuracy.

The comment claims "a swap fails and names both files - the one that improved and the one that
regressed". That holds for a swap ACROSS files. It does not hold inside one: repair an orphan in
`TrainControlUI.java` and introduce a new one in `TrainControlUI.java`, and the line stays
`TrainControlUI.java (24)` while `ALLOWED` stays 96. That is not a corner case here - two files carry
24 and 18 of the 96 between them, and they are the two files this round edited most. The claim in the
comment should be narrowed to what it can do, or the pin taken to the member name.

The sibling ratchet has no such gap. I reimplemented `testNoTestOpensTheOperatorsRailway`'s model half
the same way: 56 classes, and the pinned `MODELS_WITHOUT_A_SANDBOX_NAMES` list is exactly those 56
names, no extras and none missing. Because it pins names with no counts, any swap changes the sorted
list and fails.

### VB-C3. The clamp test proves the ceiling is not a quarter, not that it is the ceiling

Verified by mutation, and the test does what it says: changing `DiagramExport.java:102` from
`if (tileSize > MAX_TILE_SIZE) tileSize = MAX_TILE_SIZE;` to `MAX_TILE_SIZE / 4` fails
`testAnAbsurdSizeIsCapped` and only that test, 7 run / 1 failure / 0 skips. `MAX_TILE_SIZE` is 200, so
`quarter` is 50 and all three renders collapse onto the same width, which is what the second assertion
catches.

What the test does NOT catch is any clamp between those two. `min(size, MAX_TILE_SIZE / 2)` gives
`absurd == ceiling` (both 100) and `ceiling > smaller` (100 > 50), and passes. The test's own comment
is honest about this - it says "the mutation the finding named" - but the method javadoc's first line
promises more than that: "brought back to the documented ceiling, not to some other size". The
docstring should say what the assertions say.

### VB-C4. UXR-C13's reasoning does not describe the term it removed

`AutonomyMenu.java:401-417` replaced `edit.setEnabled(chosen && !trainsMoving && edit.getItemCount() > 0)`
with `edit.setEnabled(ui.whyAutonomyEditorCannotOpen() == null && pagesAvailable)`. The comment
justifies this by saying the old expression "covers isLocalLayout/session==null (via chosen) and
isAutonomyBusy but not the fourth". It does not: `chosen` is
`session.getStore().getActiveConfiguration() != null` (line 362), which is a question about the ACTIVE
CONFIGURATION, not about the session or the layout being local. `whyAutonomyEditorCannotOpen` asks
neither.

The behaviour change is defensible and I am not asking for it back. `openLayoutEditor` says in its own
words (`TrainControlUI.java:4341-4343`) that "what can be OPENED is any session at all: a setup with
blocking errors will not load, and the editor is the only place those errors can be fixed" - so the
item is now live exactly when pressing it would work, which is the rule
`LayoutRightclickAutonomyMenu.addSetupMenu` already follows and the one this project has settled on.

What is wrong is the record. Four lines above the change, an untouched comment still reads "Editing
and page exclusions ask only that a configuration be CHOSEN" - true of `pages.setEnabled(chosen)`
below, no longer true of the item it names first. In a codebase whose README says "the comment IS the
safety mechanism", a justification that misstates the removed condition and a neighbouring comment
that now describes the opposite rule are worth one line each to fix.

### VB-C5. `testFacingFollowsTheTrack` leaves its temporary copy behind

TST-C17's fix builds a throwaway copy of `test_layout/config/autonomy` so `session.open` cannot write
to the checked-in fixture. The copy is correct; its cleanup is not. `temp.deleteOnExit()` is
registered on a DIRECTORY that by then contains `config/autonomy/<files>`, and `File.delete` - which is
what the exit hook calls - never removes a non-empty directory. Every battery run leaves one
`tc-facings*` tree in the system temp folder for good. Harmless, and one `deleteOnExit()` per created
file and per created directory, registered deepest-first, fixes it.

### VB-C6. A floor asserted against Adam's own railway

`testRouteInventory.testDerivedRoutes` gained `assertTrue(result.offered > 0, ...)`. The reasoning is
right - seven of eight methods in that class asserted nothing - but this particular class is on the
`MODELS_WITHOUT_A_SANDBOX` list, which is to say it deliberately reads whatever
`LAYOUT_OVERRIDE_PATH_PREF` names: on this machine, the real railway and its live derived
configuration. The assertion's own message says so out loud: "since this layout has always offered
routes".

So the battery now goes red if Adam excludes enough pages, or marks enough points inactive, or is
mid-way through a redraw. That is a test failing for a change to data rather than to code, on a
machine-specific fixture, and it is the same class of thing TST-B11 fixed in
`testTimetableOnDerivedGraph` by pointing it at `test_layout` instead. The other four `result.valid`
assertions added in the same commit are fine - they are about bundles under `tc_backup/` and
`test_layout/`, which are fixtures.

### VB-C7. The new annotation scan reads one level down

`testEveryTestShapedMethodCarriesAnAnnotation` iterates `new File("test").listFiles()` and then that
folder's `listFiles()` - two levels, no recursion. Its sibling in the same round,
`testNoTestOpensTheOperatorsRailway`, uses a recursive `filesUnder`. A test placed one directory
deeper would be invisible to the annotation check and visible to the sandbox check, which is exactly
the sort of disagreement between two scans of the same tree that goes unnoticed. The floor of 500 will
not catch it either: the current count is 1,199.

### VB-C8. GraphStream is gone except where it is not

`docs/reference/README.md` now correctly records that the three GraphStream jars are on disk but off
the classpath, and `nbproject/project.properties` confirms it. Two leftovers:

- `TrainControlUI.java:583` still executes `System.setProperty("org.graphstream.ui", "swing")`. It is
  the only mention of graphstream left in `src/` or `test/`, it compiles because it is a string, and
  it now configures a library that is not present.
- Five jars sit untracked in `resources/`: `flatlaf-3.5.4.jar` and `json-20251224.jar` (superseded by
  3.7.2 and 20260814, which is what the project and `build.xml` use) and the three `gs-*.jar`. The
  build does not reference any of them - `build.xml:281` names its jars rather than globbing, which is
  TD-7's fix and is right. But `one.sh` DOES list all three `gs-*.jar` on its classpath, so the test
  runner now depends on three untracked files that the reference README describes as removed. Delete
  the jars and the runner stops working; leave them and `git status` stays noisy forever. Worth
  deciding rather than leaving.

### VB-C9. A restored locomotive is a name and an address

TST-B20's fix in `testImportRename` captures the names of the locomotives it deletes and recreates
them in a `finally`. Better than the previous behaviour, which deleted them and stopped. But
`model.newMM2Locomotive(name, CS_DUPE_ADDRESS)` restores the name and the address and nothing else -
not the function mappings, the icon, the preferred speed, the accumulated runtime, or the structured
notes. The comment says "this recreates it exactly", and it does not.

This is currently harmless, and I checked rather than assumed: `saveState` has no caller anywhere
outside `TrainControlUI`, so a headless test JVM never writes the locomotive database back to disk.
The finding is that the comment claims a completeness the code does not have, in a place where a
future change - a test that does call `saveState`, or a runner that keeps one JVM across classes -
would turn it into silent data loss on the real database rather than an obvious absence.

---

## D - not defects, and checks that came back clean

**VB-D1. OB-137 and its sibling are both correct, and the split is clean.**
`importRoutesMenuItemActionPerformed` (20207) and `loadJSONButtonActionPerformed` (20054) both now
build and show the chooser in the handler body, which an `ActionListener` already runs on the event
thread, and both return early on cancel before disabling anything. Checked one layer down, because
"the work half must not touch Swing" is a claim about a call graph rather than about a lambda:
`model.importRoutes` reaches only `parseRoutesFromJson`, `deleteRoute` and `newRoute`, none of which
touch a Swing object, and its logging goes through `MarklinControlStation.logf` to
`TrainControlUI.log`, which wraps its whole body in `invokeLater` (6063). `this.syncWithCS2()` called
from the `BusyDialog` worker is off the event thread, so it takes the plain
`return this.model.syncWithCS2()` branch and never tries to open a second spinner. `prefs.put` is
`java.util.prefs`, not Swing. The `whenDone` half runs on the event thread by `BusyDialog`'s
contract, and because that post is in a `finally`, the menu item is re-enabled even if the import
throws - which the old code did not guarantee.

One thing worth writing down because it looks like a defect and is not: the load-JSON path now calls
`validateButtonActionPerformed(null)` from inside an `invokeLater`, where it used to be called from a
raw thread. That does not move slow work onto the event thread, because
`validateButtonActionPerformed` (19066) already wraps its ENTIRE body in `invokeLater` - it has always
run there, whoever called it.

**VB-D2. OB-138 leaves right-click and single-click untouched, and the editor is still reachable.**
The removed branch was `e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)`; both remaining
branches (`e.getButton() == MouseEvent.BUTTON3`, and the else) are byte-identical to `17ddd270`. The
right-click on the same caption calls `LayoutRightclickAutonomyMenu.showFor(ui, station, station, ...)`,
which passes the square as `here`, and `addSetupMenu` puts "Open Full Editor"
(`autosetup.ui.menuOpenFullEditor`, 612) at the foot of the Autonomy Setup submenu, live when
`whyAutonomyEditorCannotOpen()` returns null and greyed with the reason otherwise. One caveat on the
claim as written in the code comment: `addSetupMenu` returns early when `ui.isAutonomyBusy()` or when
`ui.buildAutonomyTileMenu(here)` comes back empty, so "the right-click menu still opens the full
editor" is true while autonomy is idle and there is a tile menu to hang it from, which is the same
window the removed branch itself refused to act in.

**VB-D3. The double-click firing twice is benign, by a margin I could not measure.**
A left double-click now delivers `mouseClicked` twice and both reach `jumpToLocomotive`. The second is
a no-op only because `ui.getActiveLoc()` has been updated by then - and `displayCurrentButtonLoc`
(8775) sets `this.activeLoc` inside an `invokeLater`, not synchronously. The `InvocationEvent` posted
during click one is dispatched on the next pump of the event queue, tens of milliseconds before the
second click's native event can arrive, so in practice the guard holds. If it ever did not, the
consequence is bounded and not dangerous: `jumpToLocomotive` cycles through a locomotive's mappings,
so a second call would land on that locomotive's second mapping page instead of its first. Recorded
rather than raised, because I cannot observe it and the failure is cosmetic.

**VB-D4. OB-139 is correct, and the toggle really does rebuild.**
`dragCaption` (128) is the only thing in `LayoutGrid` that sets a move cursor, and it sets it on both
the caption and the square beneath at line 134, at rest. Its single call site (1255) now requires
`!layout.getEditHideText()`, so with Text Labels off neither component is given the cursor and no
gesture is installed. `LayoutEditor.toggleText()` (4112) calls `refreshGrid()`, which posts
`drawGrid()`, which at 4257 constructs `new LayoutGrid(...)` - a whole new component tree, so ticking
the box back re-runs the guard with the flag false and reinstalls the drag. The one thing this does
not cover is `pauseRepaint`: `refreshGrid` returns silently when it is set, and a toggle during a bulk
operation would not rebuild. That is pre-existing and applies to every other setting on that panel.

**VB-D5. Both mutations the two rewritten tests were written for are live.**

- `testTrainTailClearsEdges`: `support.LayoutSandbox.open()` is the first statement of `@BeforeClass`
  and `init(null, ...)` the second, which is the right order. The new end-to-end test is not vacuous:
  removing the `continue;` from inside `if (!tailHasProvablyPassed(...))` in `Layout.executePath` -
  leaving the `if`, its condition and its position untouched, which is what defeats every source scan
  in that file - fails `testAnEdgeTheRuleRefusesToClearStaysHeldWhileARealPathRuns` and nothing else,
  6 run / 1 failure / 0 skips. Its control is real too: on the unmeasured path `getActiveAccs()` drops
  the accessory, so the harness can see a clear happening as well as not happening.
- `testAutonomyDiagramStore`: the import genuinely throws now - `assertTrue(threw, ...)` is asserted
  rather than assumed, and switching `readSquareMap`'s `object.getString(key)` to `optString` makes
  `testAnUnreadableImportChangesNothing` fail, along with four siblings that rest on the same strict
  accessor (`testACorruptConfigurationChangesNothing`,
  `testAFailedImportLeavesNoConfigurationBehind`,
  `testAFailedImportPutsBackTheConfigurationItWasReplacing`,
  `testALoadThatFailsOnATypeLeavesTheSetupAlone`). 65 run / 5 failures / 0 skips. OB-133 is genuinely
  closed.

**VB-D6. The `waitingToClear` slot removal is complete.** No reference to `waiting[2]` or
`travelledOnThisPath` survives anywhere in `Layout.java` - the only mention left is inside the
`tailHasProvablyPassed` javadoc explaining why the running total was wrong, which is the right place
for it. Every remaining index is `waiting[0]` or `waiting[1]`.

**VB-D7. The 446-line `HomeLocomotiveMenu` deletion is safe.** Grepped every removed member -
`addStationItem`, `addClearAllItem`, `editHomeLocomotive`, `confirmExclusion`, `apply`,
`refuseWhileBusy`, `shortName` - across `src/` and `test/`: zero references outside the file itself.
The one surviving method, `addReturnHomeItem`, has its one caller at
`LayoutRightclickAutonomyMenu.java:225`. The rules the deleted half enforced are tested where they now
live: `testHomeAssignmentRules` drives `AutonomyEditorPanel.homeChoices` and `homeBrokenBy` directly,
and the by-name `setSelectedItem` trap is guarded by the new
`testTheNameComparisonScanCanStillCatchAKnownBadLine`, which feeds the detector the exact line the
original defect looked like.

**VB-D8. The other three dead-code removals check out.**
`LocomotiveSelector.LocFilterBoxKeyTyped` - `getKeyCode()` is `VK_UNDEFINED` for every `KEY_TYPED`
event, so the removed Escape branch could not fire, and Escape is handled at
`LocFilterBoxKeyReleased:345`. `RightClickFunctionMenu`'s `mousePressed`/`mouseReleased` - the class is
never registered as a listener anywhere; its only use is `new RightClickFunctionMenu(...)` followed by
a direct `showPopup(evt)` at `TrainControlUI.java:17452`. `showTab(Icon)` - its only call site,
`LocomotiveSelector.java:394`, is commented out. `LayoutEditorRightclickMenu`'s `add(menuItem)` move
into the rotate block is behaviour-preserving: on every path that reaches it, `menuItem` is either the
freshly-built Rotate item (added, as before) or the Copy item already added twenty lines above (whose
re-add was a no-op because Copy was last).

**VB-D9. No assertion in the round was loosened.** I read every removed `assert` line in
`git diff HEAD -- test/`. Fourteen removals, and each one is replaced by something stricter: two
"something was thrown" checks became exact-message checks against
`autolayout.errorInvalidPointsSpecified`; `capped.getWidth() == atMax.getWidth()` became the
three-render shape check; `heading.getText() == session.describeTile(square)` - both sides of which
came from the method under test - became a comparison against a name read independently; the hourglass
pair became a pixel comparison; a `assertNotNull(testLayoutPickPath.class)` signpost that could not
fail became a comment; `assertEquals(shown, sortedCopyOfShown)` became three scrambled names added to
`layoutDB` by reflection and asserted in order. The only genuinely removed assertion,
`assertTrue(loc.getSpeed() == 0)` in `testAutonomyPathValidation`, came back on the next line with a
precondition (`loc.setSpeed(30)`) that turned it from tautology into a check. Nine new "floor"
assertions were added where a loop could previously run zero times.

**VB-D10. The two ratchets match reality exactly.** Reproduced independently, not read: 56 loose
classes and 56 pinned names, identical sets; 96 orphaned javadocs across 22 files, and every pinned
per-file count correct and summing to 96. Both `assertEquals` calls sort both sides first, so ordering
cannot produce a false failure. See VB-C2 for the one gap.

**VB-D11. The counts and keys in the round's documentation are right.** `test/README.md`'s new figures
(`core` 63, `ui` 16, `regression` 45, `support` 3) match the folders exactly. Every message key
introduced this round exists in `messages.properties` with the arity its call site uses -
`autolayout.ui.errorCannotStartWithErrors` takes one argument and is called through `I18n.f` with one;
`layout.ui.errorMaxSizeExceeded` likewise. `docs/reference/README.md`'s claim that the GraphStream jars
are off the classpath is confirmed by `nbproject/project.properties`.

**VB-D12. The live layout's diff is the application, not a test.** The uncommitted change to
`cs2_sample_layout/config/autonomy/configuration-Main.json` is `loc` and `facing` moving between
points - which `one.sh`'s own guard describes as what a running railway looks like - plus
`maxDelay: 5 -> 2` and the disappearance of `"simulate": true`. That last one is not a schema change:
`Layout.java:6224` only writes the key when simulation is on, so it is absent because simulation is
off. OB-136 already has this filed. Nothing I ran altered the file; every `one.sh` invocation returned
a clean fingerprint.

---

## Calibration note

The brief warned that a green battery has proved little in this codebase today. That held here in one
direction and not the other. Everything the brief asked me to check about the TESTS came back correct:
both ratchets are exact, both rewritten fixtures fail under the mutation they name, the clamp test
catches the clamp it says it catches, and nothing in a two-thousand-line test diff was weakened. The
two medium findings are both in the same shape and neither is about a test: a fix applied to the
handler that was reported and not to its twins (VB-B1), and a sweep that stopped at the file it
started in (VB-B2). Both were found by grepping for the sibling rather than by reading the fix, which
is the folder README's own advice and took one command each.

The thing I would flag hardest is not in either letter. Three of the four bugs this round closed can
only ever be validated by hand, and the entries that would have Adam validate them were not written
(VB-C1). A fix nobody will check is not obviously better than a fix nobody made.


---

## Dispositions

Written after acting on this report, on 2026-08-29.

### Closed

**VB-B1 - dialogs raised from background threads.** The finding named six sites and was right that the
guard could not see them; widening the scan found nine candidates, of which **six were real** and are
fixed:

| | |
|---|---|
| `TrainControlUI#BulkEnableOrDisable` | The worst of them - a modal input dialog at the bottom of a thread inside a thread, since both menu items wrapped the call in one of their own. The prompt is on the event thread and the two wrapper threads are gone; `repaintLayout` and `refreshRouteList` already marshalled themselves. |
| `AutoJSONExport#jsonSaveAs` | A modal chooser and an error dialog on a raw thread. Same shape as OB-137, one class over. |
| `LocomotiveFunctionAssign#resetButton` | The thread held nothing but a modal confirm - everything the answer led to was already being marshalled straight back, which is the tell. `syncWithCS2` is what runs off the event thread now. Its default answer was also `YES_NO_OPTS[0]`, and Yes there wipes every custom function and icon on the locomotive, so it moved to `[1]` under VB-B2's rule. |
| `LocomotiveFunctionAssign#useCustomFunctionIcon` | A modal chooser plus every `setEnabled`, the icon preview and a repaint, all on a raw thread. Nothing in the handler is slow; the thread is gone entirely. |
| `TrainControlUI#exportJSON`, `#exportRoutes`, `#exportLocsToCSV` | Worth naming precisely, because `showMessageDialog` reads like a toast: each **constructed a whole `AutoJSONExport` panel** - a JTextArea, a JButton, a GroupLayout - on the background thread and then blocked on it. Generation stays off the thread and now runs once rather than twice; the panel is built and shown on the event thread. |

Two were **not** defects and are worth recording as such, because both are ways the scan can be wrong
rather than the code:

- `LocomotiveStats#exportData` marshals with `invokeAndWait` and takes its chooser from an EDT helper.
  It was already correct, and my first draft flagged it because it knew only `invokeLater`.
- `FeedbackEvents#execCode` is sample code with a `main`, under `examples/`, not the application.

The guard is now `testNoDialogIsShownOffTheEventThread`: every source file, every blocking dialog form,
`examples/` skipped, both marshalling forms accepted. It examines **322 calls in 19 files** where the
old one read a handful in one, and both numbers are floored - a scan that stops finding calls has
quietly become a check that no dialogs exist. Two mutations were run to prove it discriminates: a raw
`new Thread(() -> showMessageDialog(...))` fails it, and the same thing with an `invokeLater` inside
passes. The first draft of that mutation did not compile, so the run reported nothing and I nearly read
silence as green.

**VB-B2 - destructive confirmations that pre-select Yes.** Four more moved to `YES_NO_OPTS[1]`:
deleting a track diagram, the two "exit without saving" confirmations in the editor, and the
paste-mappings dialog whose **twin twenty lines away had already been fixed** - which is the sharpest
form of the failure this project keeps hitting. The reset-functions dialog above makes five. The other
`YES_NO_OPTS[0]` sites are deliberately untouched: a Yes default is right where Yes is the safe answer,
and sweeping them all would be sweeping by pattern rather than by consequence.

**VB-C1 - four fixes Adam could not comment on.** MT-207 through MT-210 now exist for OB-136 to OB-139,
and OB-133 and OB-134 have ledger rows. He said it plainly - "I can't comment on OB's, only MT's" - and
three of these four are precisely the kind no automated test reaches: what a cursor does over a hidden
label, what a double-click does to a station, whether an import still feels like it freezes. The Inbox
is down to OB-130 and OB-132, which are the two that genuinely need him.

### Still open

VB-C2 through VB-C9 are untouched. VB-C6 (a floor asserted against Adam's own railway) and VB-C5 (a
leaked temporary directory) are the two I would take first.
