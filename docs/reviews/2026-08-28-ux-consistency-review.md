# UI and UX consistency review — 2026-08-28

**Status:** open

**Prefix:** `UXR`. Cite findings from here as `UXR-A1`, `UXR-B3` and so on. Every other two- and
three-letter prefix in this folder (`CR`, `FB`, `FBR`, `FSR`, `FV`, `IAR`, `ISD`, `LD`, `RA`, `SV`) was
taken.

**What was reviewed:** branch `autonomy-diagram-r0` at `eac0e392` ("OB-129, FR-040, FR-041, menu
grouping, and a field the designer kept deleting"), on 2026-08-28. Read-only: nothing was built, run,
or changed. Scope was what the operator sees and can do — enable/disable rules against the methods they
gate, components written by more than one place, dialogs that name a cause, the eight message bundles,
menu grouping, modality, and keyboard/focus.

**Method.** For each control: read the predicate that sets its enabled/visible state, then open the
method its listener actually calls and read its first check. Report only where the two differ, and only
after arguing how a real user reaches that state. Three claims were withdrawn during the pass on
exactly that test; all three are recorded under D with what was wrong about them.

---

## Summary

**Twenty-three findings, and the sharpest one is a collision between two fixes that were each correct
on their own.** `guardLayoutMenu` (OB-033) sets *every* child of the Layout menu from one flag when the
menu opens. `repaintPathLabel` (OB-098, OB-100) greys the two Central Station items when no station is
answering. The first runs later than the second, always, because it runs at the moment the menu is
opened — so the connection guard is undone every time the operator looks at the menu, and "Switch to
Central Station Layout" is live with no Central Station. Its handler has no connection check of its own;
the greying *was* the check, and pressing it erases the pointer to Adam's own layout folder.

Findings B1 and B2 are the other end of the same method: the listener that calls `guardLayoutMenu` is
registered inside `mountAutonomyControls`, **after** that method's early return, so on a Central Station
layout the guard never runs at all; and Manage Pages is written by two rules with different predicates,
so the menu and the button four inches from it disagree during every diagram rebuild.

**The recurring shape underneath nine of the B items is one sentence:** the control that offers an
action does not ask the predicate the action asks. It appears as a route item that can never succeed
(B6), a Start item offered while trains are still coasting to a stop (B7), four management items greyed
because they were told to ask "what is running" while the method they call asks "what is selected"
(B8), and three surfaces that let you press something during a run and then answer with a dialog (B3,
B9, B12). That is the same rule Adam wrote down for OB-057 and OB-090, and it has now been broken at
eleven controls that were never swept when it was written.

The wording half is dominated by one fact: **308 `autosetup.*` keys — 89% of that namespace — are
byte-identical English in all seven translations**, including irreversible delete confirmations.

| | Count |
|---|---|
| A — high | 1 |
| B — medium | 12 |
| C — low | 21 |
| D — not defects | 12 |

---

## A — high

Wrong behaviour on the layout, or data silently lost.

| | Finding | Disposition |
|---|---|---|
| A1 | The Layout menu re-enables every item when it opens, undoing "not without a Central Station" | open |

### A1 — `guardLayoutMenu` turns the Central Station items back on every time the menu is opened

`src/org/traincontrol/gui/TrainControlUI.java:2754-2817`

```java
for (int i = 0; i < layoutMenu.getMenuComponentCount(); i++)
{
    java.awt.Component child = layoutMenu.getMenuComponent(i);

    if (child == goToEditorItem) continue;
    if (child == localHeading || child == centralStationHeading) continue;

    child.setEnabled(!busy);          // line 2770
}
```

`switchCSLayoutMenuItem`, `downloadCSLayoutMenuItem` and `openCS3AppMenuItem` are all direct children of
`layoutMenu` (added at 14375, 14384, 14392). With no editor open — the normal state — this loop sets all
three enabled. The `if (!busy)` block underneath (2788-2805) re-decides only `modifyLocalLayoutMenu`,
`editPageMenu`, `popUpAllMenuItem` and `exportDiagramItem`. Nothing puts the three back.

Their real rule lives in `repaintPathLabel`, 23758-23786:

```java
final boolean connected = isCentralStationConnected();
...
this.switchCSLayoutMenuItem.setEnabled(connected);
this.downloadCSLayoutMenuItem.setEnabled(false);
```

and `openCS3AppMenuItem.setEnabled(false)` at 6356 when `!model.isCS3()`.

`repaintPathLabel` runs from `repaintLayout`. `guardLayoutMenu` runs from the menu's `menuSelected`,
which fires *as the menu opens* — so it is unconditionally the later writer at the only moment that
matters. **Every opening of the Layout menu makes all three items live.**

**Why this is A and not B.** `switchCSLayoutMenuItemActionPerformed` (17713-17769) has no connection
check at all — `refuseWhileAutonomyRunning`, `refuseWhileEditorOpen`, and then it proceeds to:

1. `prefs.put(LAYOUT_OVERRIDE_PATH_PREF, "")` — 17739, **erasing the pointer to the operator's own
   layout folder**;
2. `this.model.clearLayouts()` — 17747;
3. sync from a Central Station that is not there.

`repaintPathLabel`'s own comment grades the outcome, 23742-23745: *"Both items fetch over the network
the moment they are pressed. Offered with no station answering, the best case is a wait and an error;
the worse case is Switch, which replaces the layout in use with one it cannot then read - which is how
OB-103 gets to say 'no layout loaded' with nothing on screen to explain it."*

**How a user reaches it.** `isCentralStationConnected()` is `getNetworkCommState() && !isSimulation()`
(1841-1844), so it is false in simulate/debug mode as well as when the CS2 is off or off the network.
Open the Layout menu in either state and Switch is live. This is OB-098 verbatim — *"switch to central
station layout is NOT greyed out in debug/simulate mode"* — live again. Worse, after step 1 the path
preference is empty, and OB-127 established that an empty path resolves to the working directory, so the
sync can return a *different* set of pages rather than none.

**Not introduced today.** The loop is from `2a59d59b` (OB-029/OB-033); the connection greying is newer.
They have been disagreeing since OB-098 landed.

**Two smaller instances of the same loop**, recorded here rather than separately:

- `downloadCSLayoutMenuItem` live on a local layout passes the loop, then
  `downloadCSLayoutMenuItemActionPerformed` (20901-20964) falls to its `else` and says *"No Central
  Station layout is currently available."* The predicate that produced that is `!isLocalLayout() &&
  !getLayoutList().isEmpty()` — two causes, one of which is "you are on a local layout", which is not
  what the sentence says.
- `openCS3AppMenuItem` live on a CS2 opens `model.getCS3AppUrl()` in a browser.

**Suggested shape of a fix** (not applied): `guardLayoutMenu` should ask each item's own rule rather
than one flag — extract `repaintPathLabel`'s decision as `applyCentralStationMenuAvailability()` and
call it inside the `if (!busy)` block beside the four rules already there. That is the move
`applyLayoutEditingAvailability` already made for Manage Pages.

---

## B — medium

An item that is live and fails, or greyed and should not be; incorrect results in specific
configurations.

| | Finding | Disposition |
|---|---|---|
| B1 | On a Central Station layout the Layout menu guard is never registered at all | open |
| B2 | Manage Pages has two writers with different predicates; the menu and the button beside it disagree | open |
| B3 | "Activate Routes" and the autonomy route list are live during a run and answer with a dialog | open |
| B4 | The function-icon dialog focuses its field after the dialog has closed | open |
| B5 | Three destructive confirmations pre-select Yes while their own comment says No | open |
| B6 | "Enable Automatic Execution" is offered on every route and always fails on one with no s88 | open |
| B7 | `isAutoRunning` vs `isRunning`: Start is offered and Stop vanishes while trains are still moving | open |
| B8 | Export, Rename, Delete and Duplicate are greyed on "what is running" but act on "what is selected" | open |
| B9 | The Pages checkboxes and Delete Setup are live during a run and refuse with a dialog | open |
| B10 | Paste Mappings is live on the page it was copied from and does nothing at all | open |
| B11 | Ctrl+C over an empty square arms Fill and Paste; Paste then shows an empty dialog | open |
| B12 | Every item on the timetable right-click menu is live during a run and all four refuse | open |

### B1 — the Layout menu's guard is wired up inside a method that returns early

`TrainControlUI.java:3196-3261`

```java
public void mountAutonomyControls()
{
    AutonomySession session = getAutonomySession();

    if (session == null)
    {
        ... put the JSON window back ...
        return;                                  // 3220
    }
    ...
    mountLayoutHeadings();                       // 3241

    if (layoutMenu != null && layoutMenu.getMenuListeners().length == 0)   // 3245
    {
        layoutMenu.addMenuListener(... guardLayoutMenu() ...);
    }
```

`getAutonomySession()` (2522-2533) returns null when there is no local layout path, or when the config
folder is not usable. A Central Station layout has no local path. So on a session that starts on a CS
layout, `guardLayoutMenu` is **never called**, and with it:

- **`popUpAllMenuItem` is decided by nobody.** `repaintPathLabel` deliberately stopped setting it (its
  comment at 23781-23785 hands ownership to `guardLayoutMenu`), so it keeps the GUI-builder default,
  enabled. `popUpAllMenuItemActionPerformed` (21168-21175) loops over `LayoutList`; with nothing loaded
  the body never runs and **the item does nothing at all, silently**. That is the state OB-128 greyed it
  for.
- **`exportDiagramItem` is not decided either** — more forgiving: `exportDiagram` (8076-8085) opens with
  a `pages.isEmpty()` check and says *"No track diagram is loaded."* A live item that fails politely.
- **The FR-040 data-source label never appears.** `refreshDataSourceLabel` is called only from
  `guardLayoutMenu` (2777), so the item keeps its form text, `ui.main.toolbar.showDataSource` = "Show
  Current Data Source" — the pre-FR-040 wording the commit message says was replaced.
- **The two section headings are never mounted**, so the same menu has headings in one session and not
  in another, decided by something (an autonomy setup existing) that has nothing to do with it.

The editor-busy half is not affected in practice: `layoutCanBeEdited()` is false on a CS layout, so no
editor can be open over one. That is why this is B and not A.

### B2 — Manage Pages is written by two rules that ask different questions

| Where | Predicate |
|---|---|
| `applyLayoutEditingAvailability`, 3971 | `noEditorOpen && layoutCanBeEdited()` |
| `guardLayoutMenu`, 2792 | `layoutCanBeEdited()` only |

`guardLayoutMenu` runs on menu open and is the later writer. It never consults `noEditorOpen`.

`noEditorOpen` is false in a state where `isLayoutEditorOpen()` is also false: `layoutEditingComplete`
(18414-18447) calls `setEditLayoutEnabled(false)`, then runs `model.refreshLayouts()` on a background
thread — emptying and repopulating the layout database — and only gives the flag back at 18493. That
window is seconds on a large diagram, and it is entered after **saving a route** as well as after a
diagram edit, so the editor window is usually already gone.

**How a user reaches it.** Save a route, then open the Layout menu while the diagram rebuilds. Manage
Pages is live; the Edit Layout button and the autonomy settings' Edit button are grey, because those are
set only by `applyLayoutEditingAvailability`. Two controls offering the same permission, visibly
disagreeing.

### B3 — the two autonomy-settings controls refuse with a dialog instead of greying

```java
// 21187-21204
private void toggleSpecifiedRoutesMouseReleased(...)
{
    if (this.isAutonomyBusy())
    {
        JOptionPane.showMessageDialog(this, I18n.t("autolayout.ui.errorWaitForActiveLocomotivesToStop"));
    }
    ...
}
// 21206-21234 — autoRouteListMouseReleased, identical guard
```

Neither is ever greyed. `toggleSpecifiedRoutes` appears in **no** `setEnabled` call anywhere in the
file; `autoRouteList` is set only from `isActivateRoutes()` (18828, 21202), which is about whether route
activation is switched on, not whether the railway is busy.

This is the case OB-101 fixed for `timetableCapture` one control over, and the comment that landed with
it (19189-19199) states the rule these two break: *"refusing is not the same as not offering. A live
button that answers with a dialog reads as a control you may use and happen to have used wrongly; a
greyed one says the railway is busy."*

**How a user reaches it.** Start autonomy, or press Return Home, then open Autonomy Settings and click
Activate Routes or a route. The checkbox visibly flips, a dialog appears, and 21200 flips it back.

### B4 — the function-icon dialog's `focusFno()` runs after the dialog has closed

`TrainControlUI.java:21405-21442`

```java
JDialog dialog = new JDialog(sourceWindow, ..., Dialog.ModalityType.APPLICATION_MODAL);
...
dialog.setVisible(true);      // 21439 — blocks until the dialog is dismissed
edit.focusFno();              // 21441
```

`setVisible(true)` on an `APPLICATION_MODAL` dialog does not return until the dialog is hidden, so
`focusFno()` — `this.fNo.requestFocus()`, `LocomotiveFunctionAssign.java:238-241` — runs on a component
whose window has gone. The function-number field never gets focus and the operator must click into it
every time. This is the only site; `focusFno`/`focusImages` are called nowhere else.

### B5 — three confirmations pre-select the destructive answer while claiming the safe one

`YES_NO_OPTS` is `{ I18n.t("ui.yes"), I18n.t("ui.no") }` — `TrainControlUI.java:568-571`. Index 0 is
**Yes**. Three sites pass `YES_NO_OPTS[0]` as `initialValue` with `// default selection = "No"`:

| Line | Dialog |
|---|---|
| 17333 | `syncFullLocStateMenuItem` — queries the Central Station, turning every function off first |
| 17877 | `showCurrentLayoutFolderMenuItem` — offer to open the layout folder (harmless) |
| **21303** | `deleteTimetableEntry` — *"Remove entry {0} ({1})? Continue?"* |

The convention is understood elsewhere: `prepareAutonomyReload` (3434), line 18872 and
`AutonomyMenu.deleteEverything` (637) all pass `YES_NO_OPTS[1]` with *"default to leaving the running
layout alone"* / *"defaulting to No matters more here than anywhere"*. So the intent at these three was
the safe answer and the index is its opposite. Enter on the delete-timetable-entry confirmation deletes
the entry — one keypress rather than a silent loss, which is why this is B and not A. Escape and
window-close are handled correctly everywhere (`!= YES_OPTION`).

### B6 — "Enable Automatic Execution" is offered on every route and can never work without an s88

`RightClickRouteMenu.java:86-101` — the item is always added, always enabled, and picks its label from
`route.isEnabled()`:

```java
if (!route.isEnabled()) { ... "menuEnableAutoExecution" ... ui.enableOrDisableRoute(routeName, true); }
else                    { ... "menuDisableAutoExecution" ... ui.enableOrDisableRoute(routeName, false); }
```

`TrainControlUI.enableOrDisableRoute` (16948) asks a different field entirely:

```java
if (r.hasS88()) { ... editRoute ... }
else            { ... showMessageDialog(I18n.f("route.ui.errorS88RequiredForAutoFire", r.getName())) ... }
```

`MarklinRoute.hasS88()` is `this.s88 > 0` (997); `isEnabled()` is `this.enabled` (1028). Unrelated
fields.

**How a user reaches it.** Right-click any route with no triggering feedback sensor — which is every
route built by hand for manual firing. The item says "Enable Automatic Execution" and the press produces
an error dialog. There is no state in which it can succeed for such a route, and it is offered on all of
them.

**And the bulk path asks a third rule.** `BulkEnableOrDisable` (16929) tests `r.hasS88() ||
r.isEnabled()` and skips silently. So a route with `enabled == true && s88 == 0` can be disabled from
Bulk Disable and cannot be disabled from its own right-click menu, which shows "Disable Automatic
Execution" and errors.

### B7 — `isAutoRunning` vs `isRunning`: Start is offered and Stop vanishes while trains are still moving

`LayoutRightclickAutonomyMenu.java:161` branches on `isAutoRunning()`. The two near-twins, both in
`src/org/traincontrol/automation/Layout.java`:

```java
public boolean isRunning()      // 1391
{
    return this.running || !this.getActiveLocomotives().isEmpty() || this.locomotiveThreads.get() > 0;
}

public boolean isAutoRunning()  // 1401
{
    return this.running;
}

public void stopLocomotives()   // 1408
{
    this.running = false;
}
```

`stopLocomotives()` clears `running` **immediately**; the trains keep going until they reach their next
station. For that whole coast-down window `isAutoRunning()` is false while `isRunning()` — and therefore
`isAutonomyBusy()` (19346) — is true. Three consequences:

- **"Stop Autonomy Gracefully" disappears** from the diagram menu (it is in the `else`, lines 508-521)
  while trains are still moving.
- **"Start Autonomy" is shown, enabled.** `menuItem.setEnabled(ui.canStartAutonomy())` (189), and
  `canStartAutonomy()` = `startAutonomy.isEnabled() && autonomyErrorCount() == 0`, where
  `gracefulStopActionPerformed` (19031) sets `startAutonomy.setEnabled(true)` synchronously.
- **Pressing it is refused** — `startAutonomyActionPerformed` reaches `isValid() && !isAutonomyBusy()`
  (19653), fails, and falls to the `isRunning()` arm at 19677 with *"wait for active locomotives to
  stop"*.

That this is an oversight rather than intent is visible in the same file: every *inner* item of that
branch asks `isAutonomyBusy()` — 358 (place), 402 (remove), 452 (edit loc), 576 (setup submenu) — and so
does `HomeLocomotiveMenu.addReturnHomeItem:57`. Only the branch that chooses between Start and Stop asks
`isAutoRunning()`.

**Same root, second surface.** `AutonomyOverlayToggle.java:234` picks its button as *stop if enabled,
else start if enabled*. Because `gracefulStopActionPerformed` disables `gracefulStop` and enables
`startAutonomy` in consecutive lines, the strip drawn over the diagram flips from "Graceful Stop" to
"Start Autonomous Operation" while trains are moving — contradicting its own doc at 217-220 ("Stop wins
when both are"). The strip is faithfully mirroring the buttons, so the repair belongs upstream in
`gracefulStopActionPerformed`.

**How a user reaches it.** Press Graceful Stop, then right-click a station or look at the strip while
the trains finish their paths — which is exactly when an operator is watching the diagram.

### B8 — four management items are greyed on "what is running" but act on "what is selected"

`AutonomyMenu.java`:

| Item | Line | Enable predicate | Method called | What the method acts on |
|---|---|---|---|---|
| Export Configuration | 323 | `running != null` | `AutonomyViewerPanel.exportConfiguration()`:1195 | `selected()` |
| Duplicate / Rename / Delete | 575-582 | `loaded` = `running != null` | `.duplicate()`:1223, `.rename()`:1256, `.delete()`:1290 | `selected()` |

`running` is `ui.getActiveDiagramConfiguration()` (3404) — what the *running graph* was built from.
`AutonomyViewerPanel.selected()` (653) is:

```java
Object chosen = configurations.getSelectedItem();
return chosen == null ? session().getStore().getActiveConfiguration() : String.valueOf(chosen);
```

— the dropdown, falling back to the **store's** active configuration. Two different facts.
`AutonomyMenu` already knows they differ: line 350 computes `boolean chosen =
session.getStore().getActiveConfiguration() != null` as a separate, weaker question for exactly the
"setup that will not load" case, and uses it for the Edit and Pages submenus.

**How a user reaches it, in one click.** Autonomy → Manage → **Unload Autonomy** (604). `unloadAutonomy`
(5747-5761) calls `resetAutonomySession`, which sets `activeDiagramConfiguration = null` (2692) and
touches nothing in the store. Reopen the menu: the configuration is still listed, still the store's
active one, `selected()` returns it — and Rename, Delete, Duplicate and Export are all greyed with
*"needs loaded"*, when all four would have worked. The same state arrives on its own for a setup with
blocking errors, which never loads.

Export is the sharp end. Lines 298-300 un-grey Import with the reasoning *"the moment it is most needed
is when the current setup will not load"* — and then grey the Export you would use to get that broken
configuration off the machine. The comment above `exportItem.setEnabled(running != null)` states *"it
writes out the configuration that is RUNNING"*, which the method it gates does not do.

### B9 — the Pages checkboxes and Delete Setup are live during a run

`AutonomyMenu.java:408` — `pages.setEnabled(chosen)`. The Edit submenu directly above (394) is `chosen
&& !trainsMoving`, on the reasoning at 384-392 that *"Only this item. Everything else on this menu
chooses a setup or does housekeeping on the file."* That reasoning does not fit Pages: ticking a page
runs `setPageExcluded` + `save()` + `reloadActiveDiagramConfiguration()`, which rebuilds the running
railway. Which is why its own handler refuses at 690 and then has to undo the tick:

```java
if (ui.isAutonomyBusy())
{
    box.setSelected(!box.isSelected());
    JOptionPane.showMessageDialog(ui, I18n.t("autolayout.errorCannotEditWhileRunning"));
    return;
}
```

With trains running, Autonomy → Pages is live and every checkbox flickers on and answers with a dialog.
**Delete Setup** (589) is the same: always enabled, `deleteEverything` refuses at 627.

### B10 — Paste Mappings is live on the page it was copied from and does nothing

`RightClickPageMenu.java:60-72` attaches the listener when `ui.pageCopied()` and disables the item
otherwise. `TrainControlUI.pasteCopiedPage()` (15533) asks a second question the menu cannot see:

```java
if (pageCopied() && this.locMappingNumber != this.pageToCopy)
```

`pageCopied()` (15570) is only `pageToCopy != null`.

**How a user reaches it.** Right-click → Copy Mappings, right-click again without changing page → Paste
Mappings is live, and pressing it does nothing at all: no dialog, no log line, no clue. This is the
shape `AutonomyMenu.java:202-212` records as DW-C2 (*"a live item whose press did nothing whatever"*),
at a menu that was not swept when that was fixed.

### B11 — Ctrl+C over an empty square arms Fill and Paste, and Paste then shows an empty dialog

`LayoutEditorRightclickMenu.java` gates both on `edit.hasToolFlag()`:

- 131 `fillSelected.setEnabled(anyPicked && edit.hasToolFlag());`
- 65 `pasteMenuItem.setEnabled(edit.hasToolFlag() || edit.hasGroupClipboard());`

`LayoutEditor.hasToolFlag()` (693) is `toolFlag != null`. The methods ask for more:

- `fillSelection()` (2887) refuses on `!hasToolFlag() || lastComponent == null` with *"nothing to fill
  with"*.
- The paste path with no group clipboard goes to `executeTool(label, null)` (1926), which calls
  `snapshotLayout()` first and then `execCopy` → `new LayoutDiagramComponent(lastComponent)` (2216).
  The copy constructor (`base/LayoutDiagramComponent.java:105-107`) dereferences `original.type` on its
  first line, and `execCopy`'s catch is `catch (IOException ex)` only.

**How `toolFlag != null && lastComponent == null` arises.** `LayoutEditor.java:5906`, the Ctrl+C
handler, calls `initCopy(getLastHoveredLabel(), null, false)` with **no null-component check** — unlike
the two mouse paths (1062, 1907) and unlike this menu's own Cut and Copy items, which sit inside `if
(component != null)` (LayoutEditorRightclickMenu.java:209). `initCopy` (2337) then sets `lastComponent =
layout.getComponent(lastX, lastY)` — null over an empty square — and `toolFlag = tool.COPY` regardless.

So: hover an empty square, press Ctrl+C, then right-click. Fill is live and refuses with a dialog;
Paste is live and pushes an undo state before failing.

**Corrected from the first draft.** This was written up as an uncaught NullPointerException escaping the
ActionListener. It does not: the listener wraps the call in `catch (Exception e)` →
`showMessageDialog(this, e.getMessage())`, and `NullPointerException.getMessage()` is null on Java 8. The
real outcome is **a message dialog with no message in it**, plus an undo state pushed for an operation
that never happened. Still a defect, and one degree less bad than claimed.

### B12 — every item on the timetable right-click menu is live during a run, and all four refuse

`RightClickTimetableMenu.java` — four items, none with a predicate. Each handler opens with the same
`isAutonomyBusy()` check and the same dialog: `updateTimetableDelay` (21321), `deleteTimetableEntry`
(21277), `restartTimetable` (21359), `clearTimetable` (17282). So right-clicking the timetable during a
run — the moment you are most likely to be looking at it — offers four things and refuses all four
identically. Same class as B3 and B9; listed separately because it is a whole menu rather than a
control.

---

## C — low

Cosmetic, wording, ordering, dead code, narrow edge cases.

| | Finding | Disposition |
|---|---|---|
| C1 | One sentence serves as a dialog body and three different menu items, and on the Layout menu it names the wrong subject | open |
| C2 | The Import menu item takes its label from a key called `tooltip.import` | open |
| C3 | The data-source item: stale tooltip, dead string, and a 17th site asking a centralised question | open |
| C4 | Return Home is greyed at five sites without the tooltip `disableReturnHome` exists to keep true | open |
| C5 | Three near-identical "wait for the locomotives" sentences | open |
| C6 | `autosetup.*` is 89% untranslated — 308 keys, including irreversible delete confirmations | open |
| C7 | German `layout.ui.errorInvalidPathOrCorruptData` is truncated and loses the actionable half | open |
| C8 | point / sensor / node / graph / track diagram — one thing, five names, split by namespace | open |
| C9 | Literal `...` and `…` in the same dropdown | open |
| C10 | Two menus called "Layouts"; "Layout" means both the folder and one page inside one menu | open |
| C11 | The pop-out and the picture export sit under the "Local Layout" heading although they work on CS layouts | open |
| C12 | "Increase Size" has no predicate while its stated mirror "Decrease Size" does — and the greyed one says no reason | open |
| C13 | The Autonomy menu's Edit submenu does not ask the guard's fourth refusal, though its sibling does | open |
| C14 | "Deselect All" and "Clear Selection" are the same action under two labels in one popup | open |
| C15 | "Map Unassigned Locomotives" is always enabled and can silently do nothing | open |
| C16 | Delete Page shows its positive tooltip while disabled | open |
| C17 | `AutonomyMenu.refreshEnabled` asks `getLayoutList().isEmpty()` live | open |
| C18 | Latent double-add in the layout editor's right-click menu | open |
| C19 | `HomeLocomotiveMenu` is two-thirds dead and its javadoc claims three callers | open |
| C20 | Three jumps to the Auto tab lack the `isEnabledAt` guard `showLayoutTab` was given | open |
| C21 | Assorted dead code: `showTab(Icon)`, `LocFilterBoxKeyTyped`'s Escape branch, `RightClickFunctionMenu`'s mouse handlers | open |

### C1 — `autosetup.ui.menuEditorOpen` is asked to be four things

```
autosetup.ui.menuEditorOpen=Close the editor first - autonomy cannot be changed from here while it is open.
```

| Where | Role |
|---|---|
| `TrainControlUI.java:4975`, `:4980` | the body of the refusal dialog — correct |
| `AutonomyMenu.java:162` | a **clickable** menu item that brings the editor forward |
| `LayoutRightclickAutonomyMenu.java:144` | a **disabled** label |
| `TrainControlUI.java:2809` (`goToEditorItem`) | a **clickable** item on the **Layout** menu that brings the editor forward |

A full sentence is not a menu label, and on the two clickable ones it instructs the user to close the
editor while the action shows it. On the Layout menu it also says *"autonomy cannot be changed"* — the
Layout menu is not about autonomy; the items it is explaining are Open Layout, Manage Pages and the
pop-out. The message names the wrong subject at the one place a person meets it there.

### C2 — the Import item's label comes from a tooltip key

```java
importRoutesMenuItem.setText(bundle.getString("ui.main.toolbar.tooltip.import"));
importRoutesMenuItem.setToolTipText(bundle.getString("ui.main.toolbar.tooltip.routeImport"));
```

with `ui.main.toolbar.tooltip.import=Import` and the sibling `ui.main.toolbar.export=Export` correctly
named. The rendered text is right today; the trap is for whoever next translates a key whose name says
it is a tooltip and whose value is a two-word button label.

### C3 — the data-source item after FR-040

At `TrainControlUI.java:2923-2943`:

- The tooltip is still `ui.main.toolbar.tooltip.showDataSource` = *"Shows where the layout is being
  loaded from."* The label now **is** that answer.
- `ui.main.toolbar.showDataSource` = "Show Current Data Source" is unreachable on a local layout —
  `refreshDataSourceLabel` overwrites it before the popup paints. (On a CS layout it is what you see —
  UXR-B1.)
- `refreshDataSourceLabel` asks `this.model.getLayoutList().isEmpty()` directly, while `guardLayoutMenu`
  two lines later asks `isLayoutLoaded()`. The javadoc on `layoutLoaded` (4006-4020) is explicit that
  asking `getLayoutList().isEmpty()` on demand **is** the defect — *"asked at sixteen places, each at
  whatever moment its own code ran"*. This is a seventeenth, and it can disagree with the item beside it
  during a `refreshLayouts` rebuild: "Data Source: None" over a live Pop-out item, or the reverse. (See
  also C17, an eighteenth.)

Also in this family: `layout.ui.errorNoCentralStationLayoutAvailable` is shown for two different causes —
see the note under UXR-A1.

### C4 — Return Home is greyed without saying why, at five sites

`disableReturnHome` (19398-19410) exists to stop this: *"A dead control with a stale reason is worse
than one with none: it answers a question the user did not ask and contradicts what they can see."*
Five places set the button directly and leave the tooltip alone:

| Line | Context |
|---|---|
| 3531 | `setAutonomyDependentTabs(false)` |
| 11168 | window construction |
| 18197 | timetable execution starting |
| 18950 | JSON validation failed |
| **19186** | `requestReturnToHome` — the staging run itself |
| 19375 | inside `refreshReturnHomeButton`, the `layout == null` branch |

19186 is the sharp one: the tooltip reads `ui.main.tooltip.returnHome` = *"Send every locomotive back to
the station it started on."* for the whole length of a staging run — the exact stale explanation the
comment eight lines below (19381-19383) says was fixed.

### C5 — three sentences for one state

```
autolayout.ui.errorWaitForActiveLocomotivesToStop=Please wait for all active locomotives to stop.
autolayout.ui.infoWaitForActiveLocomotivesToStop=Please wait for active locomotives to stop.
autolayout.errorUnableToStartAutonomyWaitForTrains=Unable to start autonomy. Wait for all trains to reach their stations.
```

The first two differ by one word and are both live (the first at eight call sites, the second at 19681).
The third also swaps "locomotives" for "trains". A fourth phrasing,
`autolayout.ui.errorReturnHomeTrainsMoving`, is deliberate — `describeStagingOutcome` exists so the
staging surfaces cannot describe one state two ways, and it names the Graceful Stop button by pulling in
its own label. That one is right; the three above are the drift it was built to prevent, one door along.

Related: `requestStartAutonomy` (19095-19119) throws
`autolayout.errorUnableToStartAutonomyWaitForTrains` whenever `startAutonomy.isEnabled()` is false —
which is also false with no configuration loaded, with an invalid setup, and during a timetable run. The
menu that calls it already splits the two reasons for its **tooltip**
(`LayoutRightclickAutonomyMenu.java:191-202`); the exception it shows on click does not.

### C6 — `autosetup.*` shipped in English in all seven translations

Measured across all eight bundles: **395 of 1857 keys are byte-identical in all seven translations, and
308 of those are `autosetup.*`** — 89% of that namespace. Not scattered rot; one feature (the
diagram-autonomy editor) copied verbatim into every file. A German operator opening the Autonomy Editor
gets an English window, including:

```
autosetup.ui.confirmDeleteConfiguration=Delete the configuration {0}?  This cannot be undone.
autosetup.ui.confirmDeleteSetup=Delete this layout's whole autonomy setup?\n\nThis removes {0} configuration(s) ... This cannot be undone.
```

— irreversible confirmations in a language the operator may not read.

Two sharper sub-cases:

- Shared vocabulary that **is** translated elsewhere is left English here. `ui.cancel` is
  "Abbrechen" / "Annuler" / "Anuluj", but `route.ui.frameCancel` and `autosetup.ui.oneWayCancel` are
  "Cancel" in all seven — so one dialog can show "Annuler" and "Cancel" as two different buttons. Same
  for `route.ui.frameSave`, `frameAdd`, `frameRemove`, `frameName`, `frameColKind/Setting/Condition`.
- The same English phrase has two fates: `autosetup.ui.menuAutonomySetup` = "Autonomy Setup" is
  translated everywhere ("Autonomie einrichten"); `layout.ui.sidebarAutonomy` = "Autonomy Setup" is
  English in all seven.

Untranslated outside `autosetup` and worth attention because they are first-run or safety-adjacent:
`error.alreadyRunning`, `error.startupFailed`, `layout.warnPowerNotConfirmed`,
`layout.warnDiagramKeyWhileBusy`, `layout.warnNoAutonomyPointHere`, `route.ui.confirmDiscardChanges`,
`ui.warnBackupIncomplete`, `loc.ui.errorLocomotiveNameUnusable`.

Cosmetic within it: `loc.forwardShort`/`loc.backwardShort` are left `f`/`b` in German (should be
`v`/`r`); every other language localised them, and Danish `f`/`b` happens to work for *frem*/*bak*.

### C7 — the German invalid-path message drops its actionable half

```
en: Invalid path or corrupt data. Ensure this folder is the parent of the CS2's "config" layout folder hierarchy.
fr: Chemin non valide ou données corrompues. Vérifiez que ce dossier est bien le parent de l'arborescence du dossier « config » de la CS2.
de: Ungültiger Pfad
```

Every other language carries both sentences. The German user is told the path is wrong, loses the only
sentence saying which folder to pick, and loses "or corrupt data" — so the message names one of two
causes.

### C8 — point / sensor / node / graph / track diagram

The split is exact and namespace-aligned; two generations of vocabulary are live at once:

| concept | `autolayout.*` (older) | `autosetup.*` (newer) |
|---|---|---|
| the picture | **graph** (19 keys) | **track diagram** (15 keys) |
| the place | **point** (47 keys) | **sensor** (22 keys) |

So `autolayout.ui.menuRemoveLocomotiveFromNode` = *"Remove Locomotive {0} from **Point**"* and
`autosetup.ui.describeSensor` = *"the **sensor** at {0} (s88 {1})"* name the same s88-backed square, and
`layout.ui.autonomyStationPrefix` = *"Point:"* labels it a third way on the main diagram. The key name
says *Node*, the value says *Point*, and "node" appears in **no** value anywhere. Two keys hedge openly:
`autolayout.ui.promptChooseConnectionTarget` = *"Choose the name of the station/point..."* and
`autolayout.errorOnlyDestinationPointsCanBeTerminus` = *"Only destination points (stations)..."*.

Cosmetic today. It stops being cosmetic the moment `autosetup.*` (C6) is translated, because the drift
gets baked into seven languages. Settle the glossary first.

Adjacent: `s88` appears as `S88` in 35 keys and `s88` in 5, and `layout.s88Feedback` = "S88 Feedback"
makes "feedback" a further name for the same concept in 7 values.

### C9 — two ellipsis glyphs in one dropdown

`AutonomyMenu.java` renders these together:

```
Export…                 autosetup.ui.btnExportConfiguration       …
Import…                 autosetup.ui.btnImportConfiguration       …
Manage Configurations…  autosetup.ui.btnManage                    …
Add a Configuration...  autosetup.ui.menuInitialize               literal
Duplicate...            autosetup.ui.menuNewConfiguration         literal
Rename...               autosetup.ui.menuRenameConfiguration      literal
Autonomy Settings...    autosetup.ui.menuGlobalSettings           literal
```

68 keys use the literal three dots and 9 use `…`. Visible as differing glyph widths in one list. Separate
from the *presence* of the ellipsis, which
[2026-08-19-ui-consistency-proposal.md](2026-08-19-ui-consistency-proposal.md) §1 covers and which Adam
has not yet picked from.

### C10 — "Layouts" names two menus, and "Layout" names two things

`ui.main.toolbar.layouts=Layouts` is the text of the top-level `layoutMenu` (14267) **and** of
`layoutMenuItem`, a submenu of Preferences — two entries with the same name in one menu bar, one a menu
of actions and one a group of preferences.

Inside the Layout menu, "Layout" means the folder in *"Open Layout..."* and *"Create New Layout"*, and
one page in *"Edit Layout Page"*, *"Pop-up all Layout Pages"* and the menu's own plural title
(`getLayoutList()` returns page names). The new "Local Layout" heading is the folder sense; the menu
title above it is the page sense.

### C11 — the pop-out and the picture export sit under the heading that contradicts them

New in `eac0e392`. `mountLayoutHeadings` (2856-2870) inserts "Local Layout" before
`chooseLocalDataFolderMenuItem` and "Central Station Layout" before `switchCSLayoutMenuItem`; everything
between reads as belonging to the first. `popUpAllMenuItem` and the picture export sit in that span —
and `guardLayoutMenu`'s own comment five lines away (2796-2798) says why they should not: *"Pop-out
windows and the picture export need PAGES, not a local folder - a Central Station diagram pops out and
exports perfectly well."*

### C12 — "Increase Size" has no predicate, and the greyed "Decrease Size" says nothing

`LayoutEditorRightclickMenu.java:416-454`. The comment at 400-403 says the two are exact mirrors and
that being exact mirrors is the point.

- Decrease (453): `menuItem.setEnabled(edit.getMarklinLayout().edgesAreEmpty());` — matches
  `shrinkEdges()`'s own first check (`LayoutEditor.java:4048`) exactly. Correct — but it gets no
  tooltip, so when it is grey it is dead with no reason, against the pattern used everywhere else in
  this codebase.
- Increase: **no predicate at all.** `growEdges()` (4009) opens with `if (layout.getSx() >= MAX_SIZE ||
  layout.getSy() >= MAX_SIZE)` → dialog. `MAX_SIZE` is 60.

The item's label prints the current size, so at the ceiling a user reads "Increase Size (60 x 30)" on a
live item and is refused. The four shift items (459-469) share the same guard at 4078 and are likewise
ungated. C rather than B because it needs a 60-square diagram.

### C13 — the Autonomy menu's Edit submenu does not ask the guard's fourth refusal

`AutonomyMenu.java:394` — `chosen && !trainsMoving && itemCount > 0`. The method it calls,
`openAutonomyEditorOnPage` → `openLayoutEditor` (4188), has four refusals: `!isLocalLayout()`, `session
== null`, `isAutonomyBusy()`, and `!this.editLayoutButton.isEnabled()` (4249). The menu covers the first
three and not the fourth. `editLayoutButton.isEnabled()` is `noEditorOpen && layoutCanBeEdited()`
(3964), whereas `AutonomyMenu.guardWhileEditing()` asks `ui.isLayoutEditorOpen()` = `openEditor != null
&& openEditor.isDisplayable()` (5025). They come apart in the same `layoutEditingComplete` window as
UXR-B2, so pressing an Edit page there produces *"an editor is already open"* with no editor open.

Cited because the sibling surface got it right: `LayoutRightclickAutonomyMenu.addSetupMenu():603` asks
`ui.whyAutonomyEditorCannotOpen()`, which is written (4158) to be `openLayoutEditor`'s four refusals in
its order, and is test-pinned. The menu-bar copy never adopted it.

Minor, same item: the third term `getItemCount() > 0` (all pages excluded) has no arm in the tooltip at
395-398, so the submenu goes dead showing the positive hint.

### C14 — one action, two labels, one popup

`LayoutEditorRightclickMenu.java:96-100` ("Deselect All") and 147-151 ("Clear Selection") both call
`edit.clearSelection()`, both tooltip "Escape", both enable on a non-empty selection. Two labels for one
action, separated by a divider that implies they differ.

### C15 — "Map Unassigned Locomotives" is always enabled and can do nothing

`RightClickPageMenu.java:77`. `mapUnassignedLocomotives()` (7463) loops and silently returns when every
key is filled or nothing is unmapped. Same class as B10, lower stakes — nothing is lost, and the common
case does something.

### C16 — Delete Page shows its positive tooltip while disabled

`RightClickPageMenu.java:137` sets the positive tooltip unconditionally, including when the item is
disabled — while the comment at 121-123 claims the opposite (*"greyed with an explanation says what to
do about it"*). `deleteCurrentLocMappingPage` (1397) knows which of the two reasons it is; the item
shows neither. Add Page directly above does it correctly.

### C17 — `AutonomyMenu.refreshEnabled` asks the question that was centralised

`AutonomyMenu.java:79` asks `!ui.getModel().getLayoutList().isEmpty()` live, rather than
`ui.isLayoutLoaded()`. `refreshEnabled()` is called only from the constructor and `autonomyMenuActed()`,
so this menu's enabled state is a snapshot taken at a different instant from every other consumer. Same
family as C3; see the javadoc at 4006-4020 for why that question has one owner.

### C18 — latent double-add in the layout editor's right-click menu

`LayoutEditorRightclickMenu.java:246-266`: `menuItem` is only reassigned to the Rotate item inside `if
(!component.isText() && component.getNumOrientations() > 1)`, but `add(menuItem)` at 266 is outside it.
For a text tile or a symmetric one, the previously-added **Copy** item is passed to `add()` a second
time, and `Container.add` removes it from its position and appends. A no-op today only because Copy is
already last; insert anything between 243 and 266 and the menu silently reorders.

### C19 — `HomeLocomotiveMenu` is two-thirds dead and its javadoc claims three callers

The class comment says *"Three menus reach this"*. `addStationItem` (92), `addClearAllItem` (151),
`editHomeLocomotive` (239), `confirmExclusion` (406), `shortName` and `NONE` have **no callers anywhere
in `src/`**; only `addReturnHomeItem` survives, with one caller. The home-locomotive editor lives in
`AutonomyEditorPanel` now. Everything in the dead half — including the by-name `setSelectedItem` fix at
285 and the `whyNotAHome` split at 358 — is unexercised.

### C20 — three jumps to the Auto tab do not ask whether it is enabled

`showLayoutTab` was given the guard (15190-15210, OB-128): *"`setEnabledAt` stops the USER picking a tab
and does nothing about the program picking it, and nine methods picked it outright by index."* The three
siblings for tab 2 were not swept: `showAutonomyRunTab` (5558), `showAutonomySettingsTab` (5575),
`jumpToAutonomyLocTab` (18760). All three do a bare `KeyboardTab.setSelectedIndex(2)` with neither an
`isEnabledAt(2)` check nor the `getTabCount() <= 2` bound `setAutoTabEnabled` (3507-3515) takes for the
same tab. **Unreachable today** — each caller only exists with a configuration loaded, and
`AutonomyMenu`'s Settings item is gated on `running != null` (336, 367), which matches
`refreshAutonomyTabState`'s answer. A trap for the next caller, not a changelog entry.

### C21 — dead code

- `showTab(Icon)` (`TrainControlUI.java:15216-15226`) — its only call is commented out
  (`LocomotiveSelector.java:398`). It also lacks C20's guard, so reviving it revives OB-128 with it.
- `LocomotiveSelector.LocFilterBoxKeyTyped` (360-369) tests `getKeyCode() == VK_ESCAPE`, which is
  `VK_UNDEFINED` for every `KEY_TYPED` event. **No behaviour is lost** — the same window handles Escape
  in `LocFilterBoxKeyReleased` (345), `formKeyPressed` (320) and `MainLocListKeyPressed` (331).
- `RightClickFunctionMenu`'s `mousePressed`/`mouseReleased` (27-37) are never reached: the class is
  never registered as a listener, only constructed and told `showPopup(evt)`. Its `openEditDialog:102`
  `if (!b.isEnabled()) return;` is also unreachable — the call site `EditFunction` (17262) already
  tests it, and disabled Swing components do not dispatch mouse events.

---

## D — not defects

Things that look wrong and are not, claims withdrawn during the pass, and checks that came back clean.

| | Item |
|---|---|
| D1 | The Start button staying live and explaining at press time is deliberate |
| D2 | **Withdrawn:** `refuseWhileAutonomyRunning` vs `isAutonomyBusy` — they agree |
| D3 | **Withdrawn:** `switchLocMapping`'s single-page branch — unreachable, `MIN_LOC_MAPPINGS` is 2 |
| D4 | **Corrected:** UXR-B11's failure is a blank dialog, not an uncaught exception |
| D5 | The route editor's add and edit paths both guard, and both refuse a second window |
| D6 | Bundle key sets, duplicates, ASCII escapes and placeholder parity are all clean |
| D7 | `editPageMenu`'s two writers agree |
| D8 | Deleting a diagram page is fully guarded |
| D9 | `buildDiagramExportMenu` cannot duplicate its item |
| D10 | Return Home does not ask the power state — but neither surface does, so nothing disagrees |
| D11 | `AutonomyMenu.rebuild`'s remote branch escapes `guardWhileEditing` — unreachable |
| D12 | "Central Station" untranslated is deliberate; German translating it *sometimes* is not (see below) |

**D1.** `canStartAutonomy`'s javadoc (19047-19071) says outright that *"the button is deliberately left
enabled and explains at press time"*, and `gracefulStopActionPerformed` re-enables Start immediately
(19031). Pressing it while the trains wind down produces `infoWaitForActiveLocomotivesToStop` — UXR-B7's
shape. It is not raised **as a button defect** because it is a stated decision with its reasoning
written down, and because `startAutonomyActionPerformed` greys the button before dispatching
(19586-19592) for the double-press hazard that mattered. B7 is raised because the *diagram menu* and the
*overlay strip* draw a conclusion from it that the decision never licensed: they remove the Stop item
and present Start as the thing to do while trains are moving.

**D2 — withdrawn.** Raised, then withdrawn. `refuseWhileAutonomyRunning` (4937-4944) asks
`model.isAutonomyRunning()` while the class's own combined predicate is `isAutonomyBusy()`
(`stagingFlowActive || isRunning()`), whose javadoc says every surface must ask *it* rather than rebuild
the disjunction. That looked like a staging-window hole in Switch-to-CS, Choose-Folder, Add-Locomotive
and Sync. Opening `MarklinControlStation.isAutonomyRunning` (2833-2842) settles it: it is
`hasAutoLayout() && (isRunning() || isStagingInProgress())`, and `requestReturnToHome` sets
`stagingFlowActive` and `layout.setStagingInProgress(true)` in consecutive statements (19181, 19185). The
two agree at every instant. This is the README's near-twin trap, and it went the other way. **Note that
UXR-B7 is the same trap where the answer came out the other side** — `isAutoRunning` really is the wrong
one there. Reading the name was not sufficient in either case.

**D3 — withdrawn.** `switchLocMapping` (5944-5995) sets `PrevLocMapping`/`NextLocMapping` only inside
`if (numLocMappings > 1)`, which looked like buttons left stale when the count drops to one.
`MIN_LOC_MAPPINGS` is 2 (350), `canDeleteCurrentPage` refuses below it (1314), and 611 floors the
restored preference at it. The count can never be 1, so the `else` at 5987-5992 is unreachable. (The
keyboard's equivalent, 6145-6161, sets both unconditionally — the two surfaces look inconsistent and
only one can be exercised.)

**D4 — corrected, not withdrawn.** See the note at the end of UXR-B11.

**D5.** `editRoute` (16776) and `AddRouteButtonActionPerformed` (18317) both call `refuseWhileEditorOpen`
before opening, and both re-check `routeEditor != null && isVisible()` **on the EDT** immediately before
constructing. The add path was the obvious sibling to have been missed; it was not.

**D6.** All eight bundles hold exactly 1857 keys with identical key sets — no missing keys (so no silent
English fallback), no dead keys, no duplicates within a file. All eight are pure ASCII with `\uXXXX`
escapes. Placeholder parity is perfect: zero mismatches of `{n}` count or index across 1857 keys × 7
languages, no `%s`/`%d`, and no straight apostrophes — which matters, since `I18n.f` uses
`MessageFormat` and a stray `'` silently swallows a `{0}`. `test/core/testMessageBundles.java` enforces
the first four. **It does not enforce placeholder parity** — currently correct by luck, and the one gap
worth closing.

**D7.** `editPageMenu` is set by `guardLayoutMenu` (2794) and `mountEditPageMenu` (2967) with
`layoutCanBeEdited() && isLayoutLoaded()` and `!pages.isEmpty() && isLocalLayout()`. Same question; two
writers, no disagreement.

**D8.** `deleteLayoutMenuItemActionPerformed` (20602-20640) checks `refuseWhileAutonomyRunning`,
`refuseWhileEditorOpen`, `isLocalLayout()` and "more than one page left", each with its own message.

**D9.** `buildDiagramExportMenu` (8012-8043) has no re-entry guard, unlike `addCombinePagesItem` and
`mountEditPageMenu` beside it — but its single call site is line 860, in the constructor.

**D10.** `HomeLocomotiveMenu.addReturnHomeItem:57` asks `isAutonomyBusy()` then `triageReturnToHome()`;
`requestReturnToHome` (19130) additionally refuses on `refuseWhileEditorOpen` and `!getPowerState()`.
The editor arm is covered by the caller (LayoutRightclickAutonomyMenu returns early at 142). The
**power** arm is asked by neither the menu nor `refreshReturnHomeButton` (19366) — so both surfaces are
incomplete in the same way and nothing on screen contradicts anything else. Out of scope for a
consistency pass; worth a line in whatever pass owns the staging guards.

**D11.** `AutonomyMenu.rebuild()` returns at 221 and 228 *before* `guardWhileEditing()` at 452, so the
class's stated rule ("everything greyed while an editor has the diagram") does not hold on that branch —
and the download it offers, `downloadCSLayoutMenuItemActionPerformed` (20901), has no
`refuseWhileEditorOpen`. Unreachable: the branch needs `isRemoteLayout()` and an open editor needs
`isLocalLayout()`.

**D12.** The brief is right that "Central Station" is a Marklin product name and deliberately
untranslated. What is **not** deliberate is German translating it in 46 keys ("Zentrale") and keeping it
in 18, including siblings about the same device:

```
layout.ui.errorNoCentralStationLayoutAvailable [de] = Kein Zentrale-Layout ist derzeit verfügbar.
layout.ui.errorNoLayoutFromCentralStation      [de] = Es konnte kein Gleisbild von der Central Station gelesen werden.
route.invalidCs3Route     [de] = Central Station-Route...
route.unparseableCs2Route [de] = Fahrstraße ... der Zentrale
```

Danish and Dutch use "Central Station" uniformly; es/fr/it/pl are consistent apart from the same
`unparseableCs2Route` outlier. Reads as two different devices. Listed here rather than under C only
because the brief pre-declared the topic settled — the finding is the *inconsistency*, not the choice.

---

## What this pass did not cover

Stated so the next reviewer knows where the hole is rather than assuming it was swept.

- **`RouteEditorFrame.java` (3741 lines) and `AutonomyEditorPanel.java` (6528 lines)** were read only
  where a finding led into them. Their own controls were not enumerated against the methods they gate.
  Between them they hold 22 `setEnabled` calls and both are editor surfaces with the modality and
  keyboard questions this brief asks about — the obvious next pass. `LayoutEditor.java` was covered only
  through the two right-click menus that drive it.
- **Colours, spacing and layout** were not looked at; this was scoped to what the interface *says* and
  *offers*.
- **Nothing was run.** Every finding here is from reading, and this project's own calibration data says
  reading passes miss what execution finds — `2026-08-25-last-day.md` records that all nine defects that
  round were found by running something. Four findings here would be settled in five minutes at the
  keyboard, and are the ones to check first:
  - **B1** — start a session on a Central Station layout, open the Layout menu.
  - **B4** — right-click one function button.
  - **B6** — right-click a route that has no s88.
  - **B7** — press Graceful Stop, then right-click a station while the trains are still moving.
