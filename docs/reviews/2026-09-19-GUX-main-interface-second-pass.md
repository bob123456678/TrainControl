# The main interface, second pass: what the per-file read did not see

**Status:** open

**Prefix:** GUX. Checked free before use: no document in `docs/reviews/` declares it, no file in the
folder carries it in its name, and `grep -rl GUX docs/` finds nothing.

**Covers:** `src/org/traincontrol/gui/` and the eight `src/org/traincontrol/resources/messages*.properties`
bundles as they stand at `7d826926` (HEAD of `autonomy-diagram-r0`, 2026-09-19). The pass began at
`9cdfb0c0`; at 11:36 a validation session committed `7d826926` under it, moving `LayoutLabel.java` by
seven lines below `:1034` (VB2-C4, the accessory timer clears its field only if it is still its own)
and `TrainControlUI.java` by six below `:19204` (VB2-B1, the exit asks each window once). Every line
cited below was re-pinned against `7d826926` before this was written; neither change touches a
finding. Excluded, because another reviewer has them:
`AutonomyEditorPanel`, `AutonomyViewerPanel` and `LayoutRightclickAutonomyMenu`. This is the second
pass over the ground `2026-09-19-UIX-main-interface-review.md` covered this morning and
`2026-09-19-SVB-validation-of-the-interface-fixes.md` validated; its findings are not repeated here, and
where a finding below extends one of theirs it says so. The delta `v2_7_4c..HEAD` (70 files in scope)
chose where to read; the code was read as it is now. **Read-only:** nothing was run, built or edited; no
test was executed; no git command that writes was used; nothing under `cs2_sample_layout/` was opened.

The brief for this pass was the places a per-file read is weakest, and the method followed it: every
`prefs.put*`, every file write, every `Timer`, every global listener, every `invokeAndWait` and
`Thread.sleep`, and every tooltip in the English bundle that names a gesture or a key was listed by a
grep over the scope and then traced to the code that answers for it. What that produced is one B and
six Cs; what it did not produce is in D, which is most of this document, because a second pass that
only reports what it found gives the next reader no way to tell "clean" from "not looked at".

---

## A - high

| id | status | where |
|---|---|---|
| - | - | none found |

Nothing reached A. The shapes looked for: a preference or a file written from a Cancel, a repaint or a
timer (D5, D6 - none); a window-monitor path taking the railway's monitor (UIX-D3 stands, and the one
new event-thread walk found, D10, takes no lock); a caption or tile registry that keeps a dead grid alive
for the session (D4 - the ordering is right on every path but the one in C4).

---

## B - medium

| id | status | where |
|---|---|---|
| GUX-B1 | open | `RouteEditorFrame.onSave` (`RouteEditorFrame.java:2742`, the edit branch at `:2798-2803`) against `MarklinControlStation.editRoute` (`:1957-1972`) and the three doors that change a route under an open editor: `TrainControlUI.deleteRoute` (`:19330`), `importRoutesMenuItemActionPerformed` (`:25374`), `enableOrDisableRoute` / `BulkEnableOrDisable` (`:21403`, `:21344`) |

### GUX-B1: the route editor does not know its route has changed underneath it, and its Save either fails with no way out or silently undoes the change

**Where.** The route editor is a non-modal frame that holds one route's name from the moment it opens:
`originalName` (`RouteEditorFrame.java:80`, set at `:212`) is what makes Save an EDIT rather than an add,
and its comment at `:269` says so. Save goes to `parent.getModel().editRoute(originalName, name, built,
s88, trigger, enabledBox.isSelected(), expression)` (`:2798-2803`); `editRoute` looks the name up
(`MarklinControlStation.java:1960`) and returns false when it is gone (`:1962-1966`), otherwise it
deletes and re-adds the route with everything the editor says, including the enabled flag.

Nothing on the main window consults the open editor before changing a route. `TrainControlUI.routeEditor`
is referenced only to open, refocus and capture (`:596-636`, `:4595-4632`, `:10741-10761`, `:21226-21249`,
`:23043-23064`) - the same shape UIX-B1 found on the exit, one door over. Three doors reach the route it
is editing:

1. **Routes tab > right-click > Delete** (`RightClickRouteMenu.java:153` -> `TrainControlUI.deleteRoute`,
   `:19330-19375`). The dialog asks about the route, not about the editor. After Yes the editor is still
   open on a name the database no longer has.
2. **File > Import routes** (`:25374-25440` -> `MarklinControlStation.importRoutes`), which deletes
   every route and adds the file's - the tooltip `ui.main.toolbar.tooltip.routeImport` says so, and it is
   right.
3. **Routes tab > right-click > Enable/Disable Automatic Execution** (`RightClickRouteMenu.java:117-145`
   -> `enableOrDisableRoute`, `:21403-21432`) and **Bulk Enable/Disable** (`:21344`), both of which go
   through `writeRouteEnabledState` (`:21450`) and so through `editRoute`'s delete-and-re-add.

**What the user sees.** After door 1 or 2, Save shows `route.ui.errorEditRouteFailed` -
*"Route {0} could not be saved. It may have been changed or removed elsewhere - check the log"*
(`messages.properties:851`) - and returns (`:2801-2803`). The window stays open with everything typed in
it, and there is no way to keep it: `originalName` is final, so Save can never become an add, and the
only doors out are Escape and the X, both of which ask *"Close it and lose them?"* (`:895`). The
message names the problem and offers nothing; the log line it points at
(`route.warningRouteNotExistCalledFrom`) says the same thing again.

After door 3 the Save succeeds and is worse for it: `enabledBox` was set from the route when the window
opened (`:1120`), the menu changed the flag in the database, and Save writes the box back. Enable a route
from the Routes tab while its editor is open, press Save in the editor, and the route is disabled again -
silently, with the table showing whatever it showed last.

**The gesture.** Routes tab > right-click a route > Edit. Change a command. Go back to the Routes tab and
right-click the same route > Enable Automatic Execution (it is offered; the menu greys nothing for an
open editor). Return to the editor and press Save. Expected: the edit lands and the route stays enabled.
Observed: the route is disabled. For the loud half: instead of Enable, choose Delete and confirm, then
Save in the editor - the error, and the typing cannot be kept.

**Why B.** The silent half is an incorrect result in a specific configuration - a route's auto-fire
turned back off by a window that never showed the user that flag changing - and it is reached through
two items on the same menu as Edit. The loud half loses unsaved typing with a message that has no remedy,
which is the state UIX-B1 was raised for with a dialog in front of it. Narrow: it needs the editor open
while the route is touched elsewhere. Not A: nothing on disk is lost that the user did not delete, and
nothing is commanded.

**Siblings checked.** Change Route ID (`:20024`) edits by name and keeps the name, so the editor's Save
finds the new instance and preserves its id (`editRoute` reads `existing.getId()`, `:1982`) - clean.
Duplicate adds under another name - clean. A Central Station sync replaces only routes whose id the
station also holds, and those open locked with Save greyed (`:2209`) - clean. The layout editor's
route-tile assignment stores a name and is not a form over the route - not affected.

**How to prove it.** `test/core/testLayoutTiles.java` already builds `new RouteEditorFrame(ui, name,
route)` and reads it through `routeEditorHasUnsavedWork`; the same harness, two tests:

- *Enable, then Save.* Open the editor on a route that is disabled and has an s88; assert
  `!model.getRoute(name).isEnabled()` (precondition); `model.editRoute(name, name, commands, s88,
  trigger, true, conditions)` - what the menu does through `writeRouteEnabledState`; assert the route is
  now enabled (precondition); press Save (reach `onSave` by pressing the button the frame built at
  `:420`, or through a package-private `saveForTest()`); **assert `model.getRoute(name).isEnabled()`** -
  red today, false.
- *Delete, then Save.* Open the editor, `model.deleteRoute(name)`, assert `model.getRoute(name) == null`
  (precondition), press Save; **assert the route exists again with the editor's commands** - red today:
  `getRoute(name)` is still null and a dialog was shown (answer it through a test hook the way
  `discardAnswerForTest` answers the discard question).

**The fix.** Two halves, and the smaller one is enough for the loud case: when `editRoute` refuses
because the name is gone, fall through to `newRoute` - the editor has every field a new route needs, and
an edit of a route that no longer exists is an add of it. For the silent case, either refuse the three
doors while `routeEditor` holds that name (the shape CS3-B1 used for a locomotive a running route
drives, `refuseWhileARouteDrivesIt`), or have the editor re-read the flags it did not change before it
writes. Refusing is simpler and honest: *"That route is open in the editor."*

---

## C - low

| id | status | where |
|---|---|---|
| GUX-C1 | fixed 2026-09-19 - all four listeners read the modifiers, numbers off the key handler | `ui.main.tooltip.incrSpeed` / `decSpeed` (`messages.properties:1536-1537`) against `UpArrowLetterButtonPressed` / `DownArrowLetterButtonPressed` (`TrainControlUI.java:23482-23488`) |
| GUX-C2 | fixed 2026-09-19 - the tooltip now says sensors are captured and throttled | `route.ui.tooltipCaptureTarget` (`messages.properties:1975`, and the same sentence in the seven other bundles) against `TrainControlUI.feedbackChanged` (`:4593-4633`) |
| GUX-C3 | fixed 2026-09-19 - Cancel undoes the copy, as it already undoes the two slots | `LocomotiveFunctionAssign.copyCustomizationsActionPerformed` (`:624-636`) against the Cancel branch in `RightClickFunctionMenu` (`:288-306`) and its comment |
| GUX-C4 | fixed 2026-09-19 - the discard is in a finally, proved through a seam | `DiagramExport.render` (`:91-172`): the grid's `discard()` at `:168-171` is not in a `finally` |
| GUX-C5 | fixed 2026-09-19 - both doors ask isLocalRouteId; bulk syncs once and only if needed | `TrainControlUI.enableOrDisableRoute` (`:21420`) and `BulkEnableOrDisable` (`:21396`) sync with the Central Station for a route it has never heard of; `deleteRoute` (`:19353`) and the route editor stopped doing that under OB-155 |
| GUX-C6 | fixed 2026-09-19 - reattached to conditionCount | `RouteEditorFrame.java:1421-1423`: `conditionCount`'s javadoc sits two hundred lines from its method, directly above another doc comment - SVB-C4's shape, a fourth instance in the same file |

### GUX-C1: the speed buttons promise Control and Alt behaviour they do not have - UIX-C5's twin, one row up

UIX-C5 found the two direction buttons' tooltips promising a Control-click their listeners do not read.
The two buttons beside them say more: `ui.main.tooltip.incrSpeed` = *"Increase Speed (+Control to
fine-tune, +Alt for 2x increment)"* and `decSpeed` the same (`messages.properties:1536-1537`), set on
`UpArrow` and `DownArrow` at `TrainControlUI.java:14553` and `:14563`. Their listeners (`:14557`,
`:14567`) call `UpArrowLetterButtonPressed(evt)` / `DownArrowLetterButtonPressed(evt)`, which are
`incrementLocSpeed(SPEED_STEP)` and `decrementLocSpeed(SPEED_STEP)` and never look at `evt`
(`:23482-23488`). The behaviour described exists on the keyboard - `VK_UP` with Alt is `SPEED_STEP * 2`,
with Control is `1` (`:18870-18898`) - and nowhere on the button.

Pre-existing text (the same two lines are in the 2.7.4c bundle), so not a regression; in scope as text
that promises what the code does not do, and reported because UIX-C5 was fixed to its two lines and this
pass swept the four that share the toolbar. The four should go together, whichever way they go: have all
four listeners read `getModifiers()` the way the key handler does, or reword all four to name the keys.

**How to prove it.** Manual: hold Alt and click the up arrow twice; the speed rises by two steps, not
four. Or the same MT as UIX-C5's, extended to the speed buttons.

### GUX-C2: the capture-target tooltip says s88 feedback is not captured; it has been, on purpose, since 2026-08-22

`route.ui.tooltipCaptureTarget` (`messages.properties:1975`, on the combo at `RouteEditorFrame.java:307`)
reads: *"Where a thrown accessory is written. Conditions capture accessories only - s88 feedback is not
captured, because a layout with trains on it reports sensors constantly."*

`TrainControlUI.feedbackChanged` (`:4593-4633`) does exactly what the sentence says does not happen: when
the editor is open, capturing, and pointed at conditions, every feedback change reaching the view
(`MarklinFeedback.java:102`, `:151` -> `MarklinControlStation.java:2804`) becomes a
`RouteCommandFeedback` line and lands in the conditions through `appendCommand` (`:1246-1290`), joined
with AND to whatever is there. The throttle (`CAPTURE_COMMAND_THROTTLE`) suppresses only an identical
repeat inside the window; a train rolling over three sensors adds three rows. The tooltip's reason is
the reason the author gave for adding it: commit `1eb8b103` (2026-08-22, LT-A5, *"a sensor that could
not be captured"*) says *"throttled the same way the accessory half is, because a sensor a train is
standing on reports itself over and over."* The tooltip was written three days earlier (`fc529d42`,
2026-08-19) and never revisited. All eight bundles carry the translation of the wrong sentence
(`messages_da:1977`, `de:1975`, `es:1976`, `fr:1978`, `it:1978`, `nl:1978`, `pl:1976`).

**What it costs.** An operator who reads the tooltip, ticks Capture into conditions, and runs a train to
see which switch a route should wait for finds sensors in the list and, having been told they cannot be
there, does not know what put them there. The opposite of UIX-C5's shape - the code does more than the
text - and the same defect class.

**How to prove it.** `test/ui/testRouteCapture.java` already exercises capture; a test that sets
`setCapturingIntoConditions(true)` (`:1386`), calls `ui.feedbackChanged("1", true)` and asserts
`conditionCount()` (`:1619`) grew by one is green today, which is the point: it proves the sentence wrong. The
fix is to the sentence, in eight bundles: *"Conditions capture accessories and sensors; commands
capture accessories only."*

### GUX-C3: Copy Customizations writes to the locomotive before OK, and the dialog's own Cancel comment says nothing does

`LocomotiveFunctionAssign` is the panel `RightClickFunctionMenu` wraps in an OK/Cancel option dialog
(`RightClickFunctionMenu.java:275-306`). The comment on its Cancel branch (`:293-302`, SVN-B14) says:
*"Everything else in this dialog waits for OK, so Cancel used to discard the icon and the trigger and
keep the slot move"* - and restores the two autonomy slots on that basis. The panel's own delete-icon
handler says the same rule in its own words: *"This allows us to delay the change until the OK button is
pressed"* (`:618`).

`copyCustomizationsActionPerformed` (`:624-636`) does not wait. It calls
`this.loc.setFunctionTypes(...)` and `this.loc.setCustomFunctions(true)` on the click, repaints the
locomotive, and `doApply` (`:269`) on OK has nothing left to do for it. Cancel puts back the two slots
and nothing else, so the function types and icons copied from the other locomotive stay copied.

**The gesture.** Locomotive tab > right-click a function button > Edit. Press *Copy customizations from
X* (offered when a copy target was set by right-click > Copy on another locomotive, `TrainControlUI
.java:10295-10304`). Press Cancel. The locomotive now has X's function types and icons.

**Why C.** The code is 2.7.x (identical in `v2_7_4c:LocomotiveFunctionAssign.java:549-561`); what is
new is the comment that claims the opposite, written in this delta while fixing the slot ticks beside
it. It is the SOP's authoritative-comment rule: a reader who trusts *"everything else waits for OK"*
will not look at this handler. Either the copy should be deferred like the icon (`customIconPath`'s
shape - remember the source, apply in `doApply`), or the SVN-B14 comment should name the exception.

**How to prove it.** Build the panel on a locomotive with default functions, set a copy target with
custom ones, press `copyCustomizations` (`:378`), then run the Cancel branch's restore; **assert
`loc.hasCustomFunctions()` is false** - red today.

### GUX-C4: the export's grid is retired on the success path only

`DiagramExport.render` (`:91-172`) builds a `LayoutGrid` into a panel nobody will show (`:109-115`),
waits for its tiles (`awaitTiles`, `:118`, which can throw `InterruptedException`), paints it under a
second `invokeAndWait` (`:122-157`, which throws `InvocationTargetException` if anything in the paint
throws), and only then retires it: `grid[0].discard()` at `:168-171`. The comment above the discard
(`:159-166`, NR-3) records what happens without it: *"every caption it registered stayed in the window's
label table for the session, keeping the whole grid and its tiles reachable through them. One page
retained per export."*

There is no `try`/`finally`. An export that is interrupted, or whose paint throws, reaches the caller's
`failed[0] = e` (`TrainControlUI.java:11967-11971`) with the grid still registered: its captions are in
`layoutStations` under an owner nothing will ever match (`addLayoutStation` prunes only labels of the
same owner, `:1647-1655`, and the owner is a `JPanel` local to this call), its container is never in
the page cache, and `forgetLayoutStations` is called from nowhere else. The NR-3 leak is back for that
one export.

**Why C.** The failure path is narrow - nothing in the paint is known to throw, and an interrupt needs
somebody to interrupt the `BusyDialog` worker - and the cost is one page's labels for the session. It is
the trap the SOP says to fix "for the next caller": the discard is one `finally` away from covering every
path, and the comment beside it already argues for that.

**How to prove it.** Not deterministically without a hook; by reading, the three statements between
the build and the discard can each throw. The fix is mechanical: wrap `:118-157` in `try { ... }
finally { invokeAndWait(discard) }`.

### GUX-C5: Enable/Disable Automatic Execution syncs with the Central Station for a route the station has never heard of

OB-155 (recorded at `TrainControlUI.java:19351-19364` and `RouteEditorFrame.java:2812-2851`) took the
post-edit sync off Delete and off the editor's Save for local routes: *"the route page should not have to
sync with the cs2 after edits/deletions for routes >= ID 1000"* - because the round trip fetches the
whole database behind a modal spinner and, with the station off, waits twice the connect timeout to
learn nothing. `deleteRoute` now asks `isLocalRouteId` first (`:19353`).

`enableOrDisableRoute` (`:21403-21432`) did not get the sweep: after `writeRouteEnabledState` it calls
`this.syncWithCS2()` unconditionally (`:21420`), and `BulkEnableOrDisable` does the same once per batch
(`:21396`). Both write through `editRoute`, which is a delete-and-re-add in the local database and tells
the station nothing (the editor's own comment at `:2812-2822` says so), so the sync cannot bring back
anything about the route that was toggled. It CAN bring back the OB-155 hazard the editor's comment
names - *"a local route sharing an id with one on the station is deleted and replaced by the station's
version"* - though `isLocalRouteId` makes that collision impossible for ids the application allocates.

**The gesture.** With the Central Station switched off and a layout whose routes are all local, right-click
a route > Disable Automatic Execution. Expected: the flag changes. Observed: the flag changes, and then a
spinner for the length of two connect timeouts.

**Why C.** A cost, not a wrong result; the sync is idempotent and the flag is already written before it
runs. Reported because it is the fix-one-site-sweep-the-siblings rule the SOP names, applied to a
commit that cited it.

**How to prove it.** Read the two lines against `deleteRoute`'s guard; the same `if (!wasLocal)` with
`wasLocal` read before the write.

### GUX-C6: a fourth orphaned javadoc in the route editor, in the file SVB-C4 swept

`RouteEditorFrame.java:1421-1423` is a doc comment - *"How many conditions the list holds, so a test can
see that a capture arrived."* - followed immediately by another doc comment (*"Sets a cell of the command
table..."*, `:1424`). The method it describes, `conditionCount()`, is at `:1619`. Javadoc attaches a doc
comment only to the declaration that follows it, so this one documents nothing, and the paragraph a
reader finds above the cell-setting helper is about a different method. `git log -L` puts it at
`2790d8bb` (2026-09-06, OB-174 to OB-178): a helper inserted at the declaration, above the javadoc of
its neighbour - the memory rule SVB-C4 named, and SVB-C4 listed three instances the same day without
this one, which sits in the same file as its first.

**How to prove it.** `regression.testJavadocsAreAttached` exists (it reads `RouteEditorFrame`); if it
cannot see two consecutive `/** */` blocks it is worth teaching it to, since that is the whole of the
shape. Fix: move the three lines to `:1618`, above `conditionCount()`.

---

## D - not defects, and checks that came back clean

| id | status | where |
|---|---|---|
| GUX-D1 | clean | every `prefs.put*` in scope: written from a menu item, a chooser's approval, a page add/delete, the sidebar's mode choice, or `openLayoutEditor` with `remember` - never from a Cancel, a repaint or a timer |
| GUX-D2 | clean | every file write in scope, and every save of the autonomy setup made from the interface |
| GUX-D3 | clean | every `javax.swing.Timer` and `java.util.Timer` in scope: one-shot, stopped on discard, or started exactly once |
| GUX-D4 | clean | the three registries a rebuilt grid can leak through, and the order the rebuild does things in |
| GUX-D5 | clean | every `invokeAndWait` in scope is guarded against the event thread; every `Thread.sleep` is on a worker |
| GUX-D6 | clean | `BusyDialog`: the fast-worker race, the `Closer` race, and the off-thread bounce |
| GUX-D7 | clean | `RouteEditorFrame.stateSignature` covers every input the window has - SVB's open question |
| GUX-D8 | clean | Cancel puts back: the layout editor (in-memory snapshot, not a re-read), the function dialog's two slots, the crop dialog, the assignment dialog |
| GUX-D9 | clean | fourteen tooltips that name a key or a gesture, traced to the code that answers for them |
| GUX-D10 | not measured | `refreshAutonomyFindings` runs `session.check()` on the event thread after every page switch |
| GUX-D11 | not a finding | `diagramShowsRestrictionArrows()` has no caller in `gui/` |
| GUX-D12 | not a finding | a route tile's right-click opens the editor only with the power off or the network down |
| GUX-D13 | not a finding | `ui.main.tooltip.returnHome` says "the station it started on"; homes can be assigned |
| GUX-D14 | clean | `AutonomyMenu` greying, the overlay strip's copied Start button, the banner's linger timer |

**GUX-D1.** Fifty-one writes. `TrainControlUI`: the twelve toolbar tick boxes and radio buttons
(`:23533-23563`, `:25168-25660`, `:26485-26595`, `:26868`), the page count on add and delete
(`:1753`, `:1804`, and the grow-on-load at `:8881`), the layout path from the three doors that change it
(`:10127` after the initialise-a-blank-layout question, `:22258-22331` open/switch/clear, `:26522` after
Download), `LAST_USED_FOLDER` / `LAST_USED_ICON_FOLDER` after a chooser's Approve (`:25223`, `:25409`,
`:27675`, and `LocomotiveStats.java:445`, `LocomotiveFunctionAssign.java:605`, `AutoJSONExport.java` in
its `whenDone`), `LAST_EDITOR_AUTONOMY_PREF` from `rememberEditorChoice` (the sidebar's choice,
`:5008`) and from `openLayoutEditor` only when `remember && autonomy != null` (`:5163`, with the comment
saying why a fallback is not a choice), `CROP_LOC_ICON_PREF` at chooser Approve, before the crop dialog
(`:27615`) - so a Cancel in the crop keeps the tick, which is what remembering a tick means -
`LAYOUT_TITLES_PREF` from `saveLayoutTitles` inside `saveState` (`:21650-21694`), and the legacy-key
removals (`:11579-11612`). `LayoutEditor.java:4242` is the grid checkbox. `PositionAwareJFrame:109-113`
writes on move, resize, state change and close, gated on `REMEMBER_WINDOW_LOCATION` and `isVisible()`.
No write is reached from a repaint, a timer or a Cancel.

**GUX-D2.** Files: `AutoJSONExport:125` and `LocomotiveStats:444` after a chooser, on a worker, with the
chooser itself on the event thread (VB-B1, OB-137); `DiagramExport:271` behind `BusyDialog.run`;
`TrainControlUI:2463` copies an unreadable state file aside once before overwriting it (`uiStateLoadFailed`,
cleared so it happens once); the backup at `:21500-21600` and the update download at `:26443`; the crop
at `:27833` through `writeAtomically` with `ImageIO.write`'s boolean checked; `AutonomyMenu:577` deletes
a configuration through `actions.delete()`, from a menu item that says Delete. Setup saves from the
interface: `GraphLocAssign.commitAndRecord` (`:302`, after the placement and with the VAL9-B1 comment
saying why), `LayoutEditor.rememberAutonomy` per gesture (`:433`, `:651`, MT-406), the tile menus and the
paste door (`TrainControlUI:4711`, `:7950`, both logged-not-shown per DR-B10), the combine door
(`:25914`, ACC-C6's sweep), and the autonomy menu's own Save (`AutonomyMenu:753`). Each is a door whose
name says it writes.

**GUX-D3.** `DiagramMonitorDriver` (`:159`): a daemon `java.util.Timer`, `start()` idempotent on the
field, `stop()` cancels and nulls, and a tick already running when `stop()` lands is dropped on the event
thread by the `timer == null` re-check and the `generation` counter (`:250-275`). `LoadingSpinner`
(`:120`): started in `addNotify`, stopped in `removeNotify`. `LocButtonTransferHandler.PageSwitcher`
(`:288`): one-shot, restarted per page, stopped on exit and drop. `AutonomyBanner.clear` (`:229`): one-shot,
restarted by `show`, stopped by `showUntilChanged` and `offer` - and the main window uses only `offer`
(`TrainControlUI:8290-8341`), so a held message cannot be cleared by an earlier `show`. The ping timer
(`TrainControlUI:9232`) is created inside `setViewListener`, whose only caller is
`MarklinControlStation.java:4412`, once. The Return Home spinner (`:24644`) is stopped by
`showReturnHomeWorking(false)` on the plan-known line, the impossible-plan line and in the worker's
`finally` (`:24149`, `:24206`). `LayoutGrid.failsafe` / `grace` (`:2001`, `:2007`) are stopped in
`discard()` (`:743-744`) and the reveal checks `discarded` (`:1979`). The route play button's `rest`
(`:28917`) and the three 50 ms focus timers are one-shot. `LayoutLabel`'s two are UIX-B2's and SVB-B1's,
now clearing their fields (`:1059` - only when the field still holds the timer that fired, since
`7d826926`; `:1228`, `:1234-1242`).

**GUX-D4.** Three registries hold labels a grid built, and each was checked for the case a rebuild leaves
a dead label behind. `DiagramTileRegistry.register` (`:74-82`) drops the arriving label's predecessors
when they share a window and are no longer displayable, and `publish` prunes on `isParentVisible()`,
which is the WINDOW's visibility (`LayoutLabel:710-722`) - so a cached page's detached labels survive
(their window is up) and a closed popup's do not (`LayoutPopupUI:104-111` hides on close). The caption
registry `layoutStations`: `forgetLayoutStations` (`TrainControlUI:1456-1481`) is called from
`LayoutGrid.discard()` (`:729`) and stands down when the container is cached; the rebuild at
`:30357-30392` discards the old grid BEFORE resetting the cache, so a cached page keeps its labels, and
then `forgetCachedPageLabels()` (`:1412-1427`) hands every cached page's labels back with a null
container before `layoutCache = new HashMap<>()` (`:30390`) - the order the comment at `:30378-30389`
says it had to be. The accessory and feedback tile sets prune on `isParentVisible()` as before. The export grid is
discarded at `DiagramExport:168-171` (NR-3) - on the success path; C4 is the other path. `LayoutPopupUI`
now discards the grid it replaces (`:grid.discard()` in the delta), which the two other diagram windows
already did.

**GUX-D5.** Nine `invokeAndWait` sites, every one behind `isEventDispatchThread()`:
`ArrivalSidePrompt:326-327`, `FacingPrompt:200-201`, `ManualReversalPrompt:407-408`,
`TailCrossedPrompt:382-383`, `TrainControlUI:2097-2106` (`askOnEventThread`), `:2162-2171`,
`:19554-19556`, `:24890-24897`, and `DiagramExport:94-100`, which refuses the event thread outright
because it must wait for tiles the event thread applies. `LocomotiveStats:453` and `:483` run inside the
export's own `new Thread`. Every `Thread.sleep` is on a worker: the tile's switching pool
(`LayoutLabel:586`), the keyboard's power-on thread (`TrainControlUI:22970`), the graceful-stop coast-down
(`:23906`) and the renderer's refresh (`:28534`); `CAN_MONITOR_DELAY` at `:9139` is inside the monitor
thread.

**GUX-D6.** `BusyDialog.run` (`:178-221`) bounces itself to the event thread rather than refusing, and
the comment says what the race would be. A worker that finishes before `setVisible(true)` runs has its
`dispose` queued; the modal's nested loop drains the queue, so the dialog closes rather than hangs.
`Closer` (`:116-176`) takes `closed` and `dialog` under one monitor on both sides, so a close that lands
before the open shows nothing.

**GUX-D7.** SVB left open whether `stateSignature` (`RouteEditorFrame:527-570`) covers every input. The
window's inputs are `nameField`, `s88Field`, `triggerBox`, `enabledBox`, the command rows and the
condition rows - and those are the six the signature reads. `captureBox` and `captureTarget` (`:153`,
`:167`) are the only other controls, and neither is saved; `proposeName` re-takes the signature after
offering a name (`:282`). Closed.

**GUX-D8.** The layout editor's Cancel restores the setup from `autonomyAsOpened`, a snapshot taken at
open and re-taken at every page or mode switch (`LayoutEditor:535-541`, `:615-624`, `:632-646`), not a
re-read of a file the per-gesture save has already written (MT-406's lesson, stated at `:582-596`).
`RightClickFunctionMenu` restores the two slots (`:303-304`; C3 is what it does not restore).
`LocIconCropDialog` answers null for Cancel, Escape and the close box (`:233-253`) and
`TrainControlUI:27638-27660` assigns and deletes nothing on it (LD-6). `GraphLocAssign` writes nothing
until `commitChanges` (`:783-796`); its slider listener only updates a label (`:924-926`).

**GUX-D9.** Traced and right: `ui.main.tooltip.spaceBar` / `enterButton` / `shiftButton` against
`VK_SPACE` (`:18922`), `VK_ENTER` (`:19157`), `VK_SHIFT` (`:19153`); `page.ui.tooltipDeletePage` (*"only
an empty page"*) against `canDeleteCurrentPage` (`:1772-1779`); `ui.main.toolbar.tooltip.routeImport`
(*"replace all"*) against `importRoutes` (parse, delete all, add); `turnOnLights` (*"all mapped
locomotives"*) against the walk of `locMapping` in its handler (`:21864-21880`);
`layout.ui.tooltipGrid`'s Control+K against `LayoutEditor:7218`; `layout.ui.tooltipSelection`'s
shift-click and shift-drag against `:1234` and `:2166`; `route.ui.tooltipCapture` (accessories thrown
on the layout - `repaintSwitch`'s branch at `:10741-10761`); `tooltipHighlightOnDiagram`'s five seconds
against `HIGHLIGHT_HOLD_MS`; `autosetup.ui.tooltipPageLeftOut` and `tooltipFindings` against the two
mouse listeners in `AutonomyOverlayToggle:118-145`; `ui.main.tooltip.keyboardPlus` / `keyboardMinus`
(*"Control + to cycle faster"*) against `switchKeyboard(keyboardNumber + 4)` under Control at
`:18991-19005`; `autolayout.ui.doubleClickExecute` against the click-count and cell-bounds check at
`AutoLocomotiveStatus:1053-1063`; `HomeLocomotiveMenu`'s greying against `isAutonomyBusy` and
`isReturnHomeOffered`, with the reason in the tooltip. The two that were wrong are C1 and C2.

**GUX-D10.** `refreshAutonomyFindings` (`TrainControlUI:8010-8095`) runs after every page switch of the
main diagram (`:30412`) and after every autonomy action, on the event thread, and its comment calls it
*"cheap enough"*. `AutonomySession.check()` (`:5134`) walks every reduced point and, for each reversing
one, a reachability set (`AutonomyChecks:580-631`). On the reduced graph of a real layout that is
hundreds of small walks; I could not size it without running it and I am recording it rather than
claiming it. It takes no lock, so it is a stall at worst, never a deadlock.

**GUX-D11.** Every reader of `DIAGRAM_RESTRICTION_ARROWS` in `gui/` is a writer or the menu's own
initial state, and `diagramShowsRestrictionArrows()` has no caller in the package - which looked, for
one grep, like a tick box that does nothing. It is read at `AutonomySession.java:7711`, inside the
static-layer refresh, and `regression.testDiagramDrawingSettings` pins that the editor asks the accessor
rather than the preference. Recorded so the next grep does not raise it.

**GUX-D12.** `LayoutLabel:362`: a right-click on a route tile opens the editor only when the power is off
or the network is down; otherwise it falls through to the click handler. Odd to read; identical in
`v2_7_4c:LayoutLabel.java:163`, so it is a 2.7.x rule and not this delta's. Not raised.

**GUX-D13.** `ui.main.tooltip.returnHome` = *"Send every locomotive back to the station it started on."*
Homes have been assignable since `d1f7008a` (2026-07-28) and behaviour.md section 6 says so; but a
locomotive with no assignment still gets a positional home where it first appeared
(`Layout.claimHome`, `:1096-1140`), so the sentence is true for every train nobody has reassigned.
Half-right, and the half it misses is the one an operator who set homes already knows. Not raised.

**GUX-D14.** `AutonomyMenu` rebuilds every item on `menuSelected` (`:54`, `:180`), so the greying the
behaviour document warns about (*"greyed when the popup opens"*) is at least fresh at open, and each
action re-asks `ui.autonomyMenuActed()` -> `autonomySetupChanged()`. `AutonomyOverlayToggle.run` is a
copy of the main window's button that presses that button (`:155-166`), chosen by `syncRun` from the
two real buttons' `enabled` property changes (`TrainControlUI:4147-4150`), bounced to the event thread
(`:304-308`). The banner is D3.

---

## What this pass did not cover

- **Nothing was executed.** Every red-today claim is from reading; the gestures are described, not
  performed.
- `LayoutEditor`'s editing logic beyond its close, cancel and snapshot paths (`:520-665`,
  `:6538-6558`) - the same limit the first pass declared.
- `RouteEditorFrame`'s condition outline, its capture settling (`settleCapturedRows`) and its validation
  beyond `onSave`'s edit/add branch and `stateSignature`.
- `LocIconCropDialog` beyond its exits; `TailCrossedPrompt`, `ArrivalSidePrompt`, `FacingPrompt` and
  `ManualReversalPrompt` beyond their thread guards - UIX-D6 and D7 stand.
- `StationCaption`'s painting, `AxisRuler`, `RowIcons`, `LocomotivePlaceholder`, `HandScrollListener`.
- `AutoLocomotiveStatus` beyond its click handlers; the `findPaths` worker and its ordering against
  `repaintAutoLocListLite` were not re-read.
- The `GEN-BEGIN` blocks, and the seven non-English bundles for meaning (C2 was checked for presence of
  the same sentence, not for the quality of its translation).
- `PositionAwareJFrame` with a maximised window and with an iconified one (Windows reports an iconified
  frame's position off-screen; whether `componentMoved` fires for it and writes -32000 was not
  confirmed). Both predate the delta.
- D10 was not measured.

## Seen outside my scope

- `MarklinControlStation.importRoutes` (`:3977-4005`): the comment *"If all read successfully, remove
  existing routes and update route DB"* (`:3991`) sits after the delete loop; the order is parse, delete, add,
  which is right - a parse failure throws before anything is deleted - but the comment describes the
  add loop as if it were the guard.
- `AutonomyViewerPanel.java:1432` (UIX's note) still stands for the autonomy-editor reviewer.
