# Regression review: what a v2.8.1 user loses on the way to v3.0.0

**Status:** open

**Prefix for citing these findings elsewhere:** `R28`

**Reviewed:** the WORKING TREE of branch `autonomy-diagram-r0` at `828b1ff1` (`RAW_VERSION = "3.0.0"`), on
2026-09-01. Working tree and `HEAD` differ only in `cs2_sample_layout/config/autonomy/*.json`, which is
Adam's live setup and was read but never written.

**Baseline:** branch `master` at `5f0a75e3`, which is `RAW_VERSION = "2.8.1"` - the last shipped
version. Merge base with HEAD is `8cee6a3c`. Note that `master` carries **seven commits the 3.0.0 line
never had** (`git log --oneline 8cee6a3c..master`), so `git diff master...HEAD` (three dots) hides them;
every comparison below uses `git diff master HEAD` (two dots) or `git show master:<path>`.

**Nothing was compiled, executed, or written.** No `ant`, no `javac`, no `java`, no TestNG, no
`battery.sh`. Every command run for this pass was `git`, `grep`, `sed`, `ls` or `wc`. Where a claim
needs execution to be certain, the entry says so and the check is restated under "Open questions".

**The question this pass asked.** Not whether 3.0.0 is good, and not whether it differs from 2.8.1 -
most of the difference is the release. What a 2.8.1 user could do on 2026-08-17 and cannot do now, or
that now happens to their data without them being told.

---

## What this pass added that the earlier ones could not

[`RGN`](2026-08-31-regression-review.md) asked the same question against tag **`v2_7_4c`**, which is
948 commits older and, crucially, **predates the seven `master`-only commits**. So `RGN` could not
check the single most likely source of a regression against the *last shipped* version: a fix made on
the 2.8.1 release line and never forward-ported. That check is `R28-D1`, and it came back clean - all
seven commits' fixes are present at HEAD, several in improved form. That is worth as much as a finding
and is the reason it is written out in full rather than summarised.

`RGN-A1`, `RGN-A2`, `RGN-B1`, `RGN-B2`, `RGN-C1`, `RGN-C2`, `RGN-C3` and `RGN-C4` all apply to a 2.8.1
baseline as well as a 2.7.4c one; their status against this baseline is recorded in `R28-D10` and they
are **not** re-filed here. `RGN-A1`'s globals half has since been fixed - see `R28-D5`.

| | |
|---|---|
| **A1** | open - deleting a locomotive now silently deletes every route command that names it; 2.8.1 kept them |
| **B1** | open - a legacy `autonomy.json`'s per-edge accessory commands are dropped on import and cannot be authored anywhere in 3.0.0 |
| **B2** | open - "Export Current Graph" is unreachable in every configuration; its only replacement needs a command-line debug launch |
| **C1** | open - "Clear All Home Locomotives" is gone, and its two API methods are dead |
| **C2** | open - "Copy Outgoing Edge..." is gone with no equivalent |
| **C3** | open - `SHOW_HOME_LOCOMOTIVES` and `HIDE_REVERSING_EDGES_PREF` survive as dead constants; the home assignment is no longer drawn |
| **C4** | open - `route.ui.errorUnusableLocName`, added *for* 2.8.1, is an orphan key in all eight bundles |
| **C5** | open - the graph window's keyboard routes to s88 address and home locomotive have mouse-only successors |
| **D1-D12** | checks that came back clean |

---

## A - high

### A1 - deleting a locomotive now silently deletes every route command that names it

| | |
|---|---|
| **Disposition** | open |
| **Confidence** | confirmed by reading both revisions; the removal is deliberate, the silence is not |
| **Also** | DEFERRED - needs Adam, on the one question stated at the end |

At 2.8.1, `MarklinControlStation.deleteLoc` unlinked the locomotive from consists, rebuilt the id
cache, and stopped:

    git show master:src/org/traincontrol/marklin/MarklinControlStation.java  :2574-2592
        boolean res = this.locDB.delete(name);
        if (res)
        {
            ... unlinkLocomotive ...
            this.rebuildLocIdCache();
        }
        return res;

At HEAD it also walks every route:

    src/org/traincontrol/marklin/MarklinControlStation.java:2986-2996
        for (MarklinRoute r : this.getRoutes())
        {
            if (r.locomotiveDeleted(name))
            {
                this.logf("route.warnConditionNamesDeletedLocomotive", r.getName(), name);
            }
        }

and `Route.locomotiveDeleted` **removes the commands**:

    src/org/traincontrol/base/Route.java:190-199
        for (java.util.Iterator<RouteCommand> commands = this.route.iterator(); commands.hasNext();)
        {
            RouteCommand rc = commands.next();
            if (namesALocomotive(rc) && name.equals(rc.getName())) commands.remove();
        }

`namesALocomotive` is `isLocomotiveSpeed() || isFunction() || isAutoLocomotive() ||
isLocomotiveDirection()` (`Route.java:243-247`), so every speed, direction and function command for
that locomotive is gone out of every route in the database.

**Nothing says so.** The return value - and therefore the one log line - is about **conditions**, not
commands: `locomotiveDeleted` returns true only if a CONDITION still names the locomotive
(`Route.java:201-208`), and the conditions are deliberately left alone. The command removal produces no
log line at all. The confirmation dialog is unchanged between the two revisions:

    src/org/traincontrol/resources/messages.properties:91          (HEAD)
    git show master:src/org/traincontrol/resources/messages.properties:88   (2.8.1)
        ui.confirmDeleteFromDatabase=Are you sure you want to delete {0} from the database?

**It is permanent.** Routes and their command lists are persisted: `saveState` walks
`this.routeDB.getItems()` (`MarklinControlStation.java:1513`) and the restore rebuilds each with
`newRoute(c.getName(), c.getAddress(), c.getRoute(), ...)` (`:361`). So the next save writes the gutted
routes over the good ones.

**Why this is a loss and not just a difference.** The javadoc's argument (`Route.java:163-170`) is that
"a command for a locomotive that cannot be resolved does nothing when the route fires", so leaving it
"keeps the route looking complete while it is not". That is true *while the locomotive is absent*, and
it stops being true the moment a locomotive of that name exists again. At 2.8.1 the delete-then-re-add
sequence - deleting one by mistake, deleting one to rebuild it, taking one out of the database and
putting it back - restored every route that mentioned it. At 3.0.0 the routes are already edited and
there is nothing to restore. The javadoc considers the inert case and not the re-created one.

It is mitigated by `changeLocAddress(String, int, decoderType)`
(`MarklinControlStation.java:2841`), which changes address AND decoder type in place - so the one
workflow that *forced* delete-and-re-add at some earlier version does not force it now. That is why
this is A rather than A-with-a-changelog-entry-demanded: the trigger is a user action they confirmed.
What they did not confirm, and are not told about, is the edit to their routes.

**Severity.** A. Authored data is removed silently, from a store that is then written to disk, on a
path that is one menu item away.

**DEFERRED - needs Adam:** should the delete confirmation say how many route commands will be removed
(and from which routes), or should the removal be dropped in favour of the 2.8.1 behaviour?

**How to confirm.** No execution needed for the code path; to see it, add a locomotive `X`, put
`locspeed,X,40` in a route, delete `X` from the database, and print `getRoute(...).toCSV()`. Expect the
line to be gone, with nothing in the log about it.

---

## B - medium

### B1 - a legacy `autonomy.json`'s per-edge accessory commands are dropped on import, and cannot be authored anywhere in 3.0.0

| | |
|---|---|
| **Disposition** | open |
| **Confidence** | confirmed by reading, and measured against the graph in the repository |

At 2.8.1 an edge could carry an arbitrary list of accessory commands, authored by hand:

    git show master:src/org/traincontrol/gui/GraphEdgeEdit.java:288
        I18n.t("autolayout.ui.signalSwitchCommands")   // free-text configCommands JTextArea
    git show master:src/org/traincontrol/gui/GraphEdgeEdit.java:340
        I18n.t("autolayout.ui.captureCommands")        // capture them from the diagram and keyboard

and `Edge.toJSON` wrote them:

    git show master:src/org/traincontrol/automation/Edge.java:429-444
        for (Entry<String, Accessory.accessorySetting> acc : this.configCommands.entrySet())
        ...
        if (!commandList.isEmpty()) { jsonObj.put("commands", new JSONArray(commandList)); }

**They are not imported.** `AutonomySession.importLegacy` reads the points array and the top-level
settings, and explicitly skips the edges:

    src/org/traincontrol/automationui/AutonomySession.java:553
        org.json.JSONArray points = legacy.optJSONArray("points");
    src/org/traincontrol/automationui/AutonomySession.java:822-824
        for (String key : legacy.keySet())
        {
            if ("points".equals(key) || "edges".equals(key)) continue;

The long comment above that loop says why the *lengths* are left behind and calls it "left for a
decision rather than migrated wrongly". It does not mention the commands. `RGN-A1` named the lengths
and the timetable; it did not name these.

**They cannot be re-authored.** `grep -rn "addConfigCommand" src/` at HEAD returns exactly four hits
and none of them is a user interface: `automation/Edge.java:79` (the API),
`automation/Layout.java:2692` (`copyEdge`, internal), `automation/Layout.java:7677` (the legacy JSON
reader), and `examples/FullAutonomyExample.java`. On the diagram path the command set for a connection
is derived from tile geometry by `GraphReducer.collectCommands` (`GraphReducer.java:871`, `:975`) and
emitted by the builder at `AutonomyBuilder.java:1046-1063`. There is a pairing gesture for signals
(`autosetup.ui.menuPairSignal`), which covers one case; there is nothing for an uncoupler, a level
crossing, a second signal, or any accessory the geometry does not imply.

**It is real data.** In the graph shipped in this repository,
`test/test_layout_snapshot/config/autonomy_legacy/autonomy.json` - a copy of Adam's own legacy setup -
**69 edges carry a `commands` block**, and the accessories named include **fifteen distinct signals**
(`Signal 37/38/39/40`, `61-64`, `81`, `86`, `87`, `94`, `107`, `108`, `116`) as well as the switches.
The switches are what the geometry derivation reproduces; the signals are the part that depended on a
human having said so.

**Not the lock edges.** Those *do* survive: they are emitted by `AutonomyBuilder.java:1138`
(`json.put("lockedges", lockEdges)`) from the `menuBlockedByPoints` authoring gesture, and read back at
`Layout.java:7728`. The finding is about `commands` only.

**Severity.** B rather than A because the legacy `autonomy.json` is not deleted by the import, so the
data still exists in the file - it is unreachable rather than destroyed - and because the largest part
of it (the switches) is re-derived correctly. What is lost is the operator's own signal and accessory
authoring, silently, with no way to put it back.

**How to confirm.** Read-only: `grep -c '"commands"' test/test_layout_snapshot/config/autonomy_legacy/autonomy.json`
(69) against `grep -rn "addConfigCommand" src/` (no GUI caller). To measure the gap on Adam's real
graph, build a configuration from the diagram and diff the emitted `commands` per edge against the
legacy file's, keyed by `start`/`end`.

### B2 - "Export Current Graph" is unreachable in every configuration, and its replacement needs a command-line debug launch

| | |
|---|---|
| **Disposition** | open |
| **Confidence** | confirmed by reading; the composition of three predicates, none of which needs execution to read |

At 2.8.1 the Auto tab's configuration page carried five buttons - Validate, Load Blank Graph, Load
JSON, **Export Current Graph**, JSON documentation - and they were always there. At HEAD they are
hidden or unreachable in both possible configurations:

- **Local layout.** `getAutonomySession()` is non-null for any local layout folder
  (`AutonomyCompanionStore.isUsable()` is `layoutFolder != null && layoutFolder.isDirectory()`), so
  `mountAutonomyControls` takes the second branch:

      src/org/traincontrol/gui/TrainControlUI.java:3420-3431
          int configTab = locCommandPanels.indexOfComponent(this.autonomyPanel);
          if (configTab >= 0) locCommandPanels.remove(configTab);
          ...
          this.exportJSON.setVisible(false);

- **Central Station layout.** `session == null`, so the tab and the five buttons come back
  (`:3386-3396`) - but they live inside `locCommandPanels`, which is inside `autoPanel`, which is
  `KeyboardTab` index 2 (`KeyboardTab.addTab("Auto", autoPanel)`, `:12376`;
  `locCommandPanels.addTab(... autonomyPanel)`, `:11790`). And:

      src/org/traincontrol/gui/TrainControlUI.java:3677
          setAutoTabEnabled(valid && loaded && isLocalLayout());

  `isLocalLayout()` is false, so index 2 is greyed and stepped off (`:3684-3687`). This is the same
  composition as `RGN-A2`; what is new here is that it takes the *export* with it, in the one
  configuration where `mountAutonomyControls` still mounts it.

**The replacement is debug-only.** `AutonomyMenu` offers `autosetup.ui.menuExportRawGraph` = *"Export
Raw Graph as JSON (Advanced Users)"*, which calls `AutonomyViewerPanel.inspect()` - and that is the
same artefact, `builder(globals()).withCoordinatesFromTiles(pageOrder).build()`
(`AutonomySession.java:2476-2486`). But it is gated:

    src/org/traincontrol/gui/AutonomyMenu.java:445-451
        boolean inspectable = ui.getModel() != null && ui.getModel().isDebug()
            && !session.getStore().getConfigurationNames().isEmpty()
            && session.getStore().getActiveConfiguration() != null
            && session.getReducer() != null
            && !session.hasBlockingProblems();

and debug is not a menu item. `grep -rn "setDebug" src/` returns nothing; the flag is set once, from
the constructor, out of `main`:

    src/TrainControl.java:23
        boolean debug = (args.length >= 2);

So it requires launching the jar from a command line with at least two arguments. Somebody running the
shipped application normally can never have it on. (That is true at 2.8.1 too - what changed is that
the export now depends on it.)

**And the replacement is weaker where it is available.** 2.8.1's `exportJSONActionPerformed` showed an
`AutoJSONExport` panel with a `JFileChooser` Save As, remembered `LAST_USED_FOLDER`, and put the JSON on
the clipboard. `inspect()` writes a fixed name in the working directory and overwrites without asking:

    src/org/traincontrol/gui/AutonomyViewerPanel.java:1363-1367
        java.io.File out = new java.io.File("autonomy-derived.json").getAbsoluteFile();
        java.nio.file.Files.write(out.toPath(), ...);

`autosetup.ui.btnExportConfiguration` ("Export...") exists and is not gated, but it exports the
companion-store *configuration*, not the built graph - a good backup, and not the thing you can
hand-edit or feed back to a 2.8.1 installation.

**Severity.** B. It is not wrong behaviour on the layout, and there is a nearby export for the backup
case; what is gone is the only door to the graph in the format 2.8.1 reads, which is also the only
downgrade path.

**How to confirm.** Read-only, above. To see it: open a local layout, look at the Auto tab (no
configuration page); switch to a Central Station layout, look at the tab bar (Auto greyed).

---

## C - low

### C1 - "Clear All Home Locomotives" is gone, and its two API methods are dead

| | |
|---|---|
| **Disposition** | open |
| **Confidence** | confirmed by reading |

    git show master:src/org/traincontrol/gui/HomeLocomotiveMenu.java:150,157
        static void addClearAllItem(JComponent menu, TrainControlUI ui, Component dialogParent, ...)
        JMenuItem menuItem = new JMenuItem(I18n.t("autolayout.ui.menuClearAllHomeLocomotives"));

wired from `GraphRightClickGeneralMenu.java:160`. At HEAD `addClearAllItem` no longer exists in
`HomeLocomotiveMenu.java` (its own header says "Only `addReturnHomeItem` survives"), both bundle keys
(`autolayout.ui.menuClearAllHomeLocomotives`, `autolayout.ui.confirmClearAllHomeLocomotives`) are
referenced by no `.java`, and the backing API has no callers at all:

    grep -rn "clearHomeLocomotives\|hasHomeLocomotives" src/
        src/org/traincontrol/automation/Layout.java:1245    synchronized public void clearHomeLocomotives()
        src/org/traincontrol/automation/Layout.java:1259    synchronized public boolean hasHomeLocomotives()

Only the per-station `autosetup.ui.menuHomeNone` remains, so clearing N home assignments is N
right-clicks. On Adam's graph that is up to 62.

### C2 - "Copy Outgoing Edge..." is gone with no equivalent

| | |
|---|---|
| **Disposition** | open |
| **Confidence** | confirmed by reading |

    git show master:src/org/traincontrol/gui/GraphRightClickPointMenu.java:730
        I18n.t("autolayout.ui.menuCopyOutgoingEdge")

It duplicated one connection's whole configuration - commands, lock edges, length - onto another pair
of points. The key is unused in any `.java` at HEAD, and the `autosetup.ui.menuConnections` submenu in
`AutonomyEditorPanel` offers `menuUseLink`, `menuPairLink`, `menuGoToLinkPartner`, `menuBranch`,
`menuAllBranches`, `menuTurnMay/Must/Never`, `menuRouteToward/Both/None` - nothing that copies one
connection's settings to another. Much of what it copied is now derived rather than authored, which is
why this is C and not B; the parts that are still authored (lengths, blocked-by-points) have to be set
one at a time.

### C3 - two preferences survive as dead constants, and the home assignment is no longer drawn

| | |
|---|---|
| **Disposition** | open |
| **Confidence** | confirmed by reading |

    src/org/traincontrol/gui/TrainControlUI.java:246
        public static final String HIDE_REVERSING_EDGES_PREF = "HideReversingEdges";
    src/org/traincontrol/gui/TrainControlUI.java:248
        public static final String SHOW_HOME_LOCOMOTIVES = "ShowHomeLocomotives";

Both are the only occurrence of their name in the tree. At 2.8.1 both were read and written:
`master:TrainControlUI.java:15071` (`prefs.getBoolean(HIDE_REVERSING_EDGES_PREF, false)`) and
`master:TrainControlUI.java:15136`:

    if (p.getHomeLoc() != null && prefs.getBoolean(SHOW_HOME_LOCOMOTIVES, true))

with toggles at `master:GraphRightClickGeneralMenu.java:218` and `:287`. Neither the preference nor
its menu item exists at HEAD, and the stored values sit unread.

`HideReversingEdges` is graph-window declutter and goes with the window. `ShowHomeLocomotives` is
different in kind: the *information* it toggled has no home either. `grep -rn "getHomeLoc"
src/org/traincontrol/gui src/org/traincontrol/automationui` finds one live use,
`AutoLocomotiveStatus.java:240`, which is a per-locomotive badge in the run list, not a mark on the
track. The diagram outlines the *station* a locomotive is assigned to; it does not name the locomotive
on the station the way the graph did. Filed as C because the assignment is still visible somewhere and
still settable; it is listed so that "the pref is dead" and "the display is gone" are not mistaken for
two separate small things.

`RGN-D8` listed `HIDE_INACTIVE_PREF`, `HIDE_REVERSING_PREF` and `SHOW_STATION_LENGTH` as removed with
the graph window. These two are the same shape and were not on that list.

### C4 - `route.ui.errorUnusableLocName`, added *for* 2.8.1, is an orphan key in all eight bundles

| | |
|---|---|
| **Disposition** | open |
| **Confidence** | confirmed by reading |

Commit `bdfac1cc` ("Settle the unusable-name rule on RouteCommand") added this key to all eight bundles
and used it from `RouteEditor.refuseUnusableName`. At HEAD the key is still in all eight
(`messages.properties:987` and its seven siblings) and is referenced by no `.java` - `RouteEditorFrame`
uses `route.ui.frameNameNotUsable` instead (`RouteEditorFrame.java:2234`). The rule itself survives
(`RouteCommand.isNameUsable` is asked at `RouteEditorFrame.java:2227`, `TrainControlUI.java:16516` and
`:22202`, the last of which is a *fourth* door 2.8.1 did not have), so this is dead weight rather than
lost behaviour. It is the only bundle key in this class and is easy to miss because it looks live.

### C5 - the graph window's keyboard routes to s88 address and home locomotive have mouse-only successors

| | |
|---|---|
| **Disposition** | open |
| **Confidence** | confirmed by reading |

    git show master:src/org/traincontrol/gui/GraphViewer.java:608   Ctrl+S -> set the hovered point's s88
    git show master:src/org/traincontrol/gui/GraphViewer.java:613   Ctrl+H -> set the hovered point's home locomotive

Both capabilities exist at HEAD - s88 comes from the feedback tile and is set with Ctrl+A in the layout
editor (`LayoutEditorRightclickMenu.java:294`, `layout.ui.addressLabelFeedbackAddress`), home via
`autosetup.ui.menuHomeFor` (`AutonomyEditorPanel.java:1045`) - but only by mouse.
`grep -n "KeyEvent.VK_" src/org/traincontrol/gui/AutonomyEditorPanel.java` yields two `VK_ESCAPE`
bindings and nothing else; `LayoutLabel.java`, `LayoutGrid.java` and `LayoutPopupUI.java` contain no
`KeyEvent.VK_` at all. This is the same class as the already-known Ctrl+E / Ctrl+U removal and is filed
in one entry rather than four. Cut-and-paste of a locomotive is **not** in this list - see `R28-D2`.

---

## D - not defects

### D1 - every one of the seven `master`-only (v2.8.1) commits is forward-ported to HEAD

This is the check that only a 2.8.1 baseline can make, and it is clean. `git log --oneline
8cee6a3c..master` is `4adc7afb`, `a673bc7d`, `ac66dc7b`, `bdfac1cc`, `68d78e45`, `2b0eef79`,
`5f0a75e3`. Their combined source diff (`git diff 8cee6a3c..master -- src`, 1,302 lines) contains
sixteen behavioural fixes. Each was located at HEAD:

| 2.8.1 fix | at HEAD |
|---|---|
| `Layout` refuses a 0-speed locomotive at Start rather than invalidating the layout | `Layout.java:1685` |
| `Layout.executePath` refuses `speed < 1 \|\| speed > 100` | `Layout.java:3533`, `:5054` |
| `Layout.fireCallback` guards every callback with `catch (Throwable)` | `Layout.java:5904`; the only bare `callback.apply` left is the one *inside* it, `:5911` |
| every fire site moved behind it | `:2812`, `:5177`, `:5548`, `:5558`, `:5602`, `:5609`, `:5808`, `:5900` |
| `Feedback.readyForUpdate` guards a backward wall clock (`since >= 0 &&`) | `git diff master HEAD -- src/org/traincontrol/base/Feedback.java` is **empty** |
| `LayoutDiagram` case-only page rename via `Files.isSameFile` + staging | `LayoutDiagram.java:501` |
| `Locomotive.setPowerState` starts the runtime clock only on a real transition | `Locomotive.java:407`, `:423` |
| `RemoteDeviceCollection` synchronized on every public method | 9 `synchronized public` at both revisions; diff is comment-only |
| `RouteCommand.isNameUsable` | `RouteCommand.java:596`, asked at four doors (2.8.1 had three) |
| `AddLocomotive` asks `getLocAddresses`, not the duplicate list | `AddLocomotive.java:456` |
| `AutoLocomotiveStatus.findPaths` off the EDT, with the `haveFound` flag | `AutoLocomotiveStatus.java:159`, `:214` |
| `timetableStart()` at all three legend sites | `:362`, `:389`, `:407` |
| the `getCellBounds` double-click guard | `:992` |
| the `index >= paths.size()` / captured-path dispatch guard | `:1020-1022` |
| `LayoutEditor` click-vs-drag and null-target guards | `LayoutEditor.java:1352-1391` |
| `filterConfigCommands` per-locomotive dedup key | moved whole to `base/RouteCapture.java:43`, `:101` |
| the single-route-editor check moved onto the EDT | `TrainControlUI.java` route editor path |
| the null-search-string guard | `TrainControlUI.java:17673`, `if (searchString == null \|\| "".equals(searchString)) return;` |
| `MarklinControlStation` disables the rejected duplicate route | `:1892`, `r.disable();` |
| `locIdCache == null -> rebuildLocIdCache()` | `:2277` and `:2451` (2.8.1 had one site; HEAD has two) |
| `MarklinRoute.locomotiveRenamed` repairs the condition tree | generalised - see `R28-D7` |
| `ViewListener.getLocAddresses` | `ViewListener.java:81` |
| `loc.ui.errorLocomotiveNameUnusable` | present and used, `TrainControlUI.java:16520`, `:22205` |

The only casualty is the bundle key `route.ui.errorUnusableLocName` - `R28-C4`, and the rule it
labelled is enforced by a different key.

### D2 - locomotive cut-and-paste on the diagram survives

The deleted `GraphViewer` bindings (Ctrl+V paste, Ctrl+X cut, Delete) have a live successor in
`TrainControlUI`: the `cutLocomotive` field and its handling are at `TrainControlUI.java:5576`, `:5609`,
`:5614`, `:5663`, and `deleteLoc` clears it at `:17346` with a comment naming the trap. The set of
`keyCode == KeyEvent.VK_*` comparisons in `TrainControlUI.java` is identical between the two revisions.
This is called out because it is the obvious wrong conclusion from "the graph window's key handling was
deleted".

### D3 - preference keys and `UIState.data`

Both revisions use the same node, `Preferences.userNodeForPackage(TrainControlUI.class)`
(`master:TrainControlUI.java:374`, HEAD `:511`). **No key present at 2.8.1 was renamed**, including the
`Conversion.getFolderHash(10)` suffixes, so nothing is orphaned by a spelling change. No surviving key
is read-but-never-written. `LAYOUT_TITLES_PREF` save/restore is byte-identical.
`PositionAwareJFrame.getWindowName()` is unchanged (`master:191`, HEAD `:239`), so surviving windows
keep their saved geometry; `GraphViewer_*` and `RouteEditor_*` entries are orphaned because those
windows are gone.

`UIState.data` round-trips both ways: same `List<Map<Integer,String>>` shape, same
`SAVE_KEY_ACTIVE_MAPPING_NUMBER = -1` / `SAVE_KEY_ACTIVE_BUTTON = -2` (`master:356-357`, HEAD
`:493-494`). HEAD's restore is strictly *more* permissive - `!saveStates.isEmpty()`
(`TrainControlUI.java:6594`) where 2.8.1 compared against a fixed page count - and `:6537` grows the
page count to match a larger file rather than truncating. `RGN-C4` (a 3.0.0 file with a non-default
page count read back by 2.7.4c) is unchanged and still applies in the downgrade direction only.

### D4 - the message-bundle sweep

Nineteen keys are present at 2.8.1 and absent at HEAD (1,237 keys against 1,865). Every one is graph
window UI: `app.ui.autonomyGraphTitle`, `app.ui.autonomyGraphTitleLoc`,
`autolayout.ui.confirmClearLocomotives`, `confirmDeleteEdge`, `confirmDeletePoint`,
`confirmDeletePointOccupied`, `errorLoadingGraphUi`, `infoAllLocomotivesPlaced`,
`infoNoOtherPointsToConnect`, `infoPointHasNoCoordinateInfo`, `layoutGraph`, `menuAddLocomotiveAtNode`,
`menuClearLocomotives`, `menuRemoveLocomotiveFromGraph`, `tooltip.reopenGraph`, `ui.main.graphUIOptions`,
`ui.main.reopenGraph`, `ui.main.tooltip.hideInactivePoints`, `ui.main.tooltip.hideReversingStations`.
As `RGN-D7` says, this sweep is a weak proxy: it finds a removed feature only if that feature had a
string nothing else reuses. `R28-C1`, `C2` and `C3` are all cases it misses, because their keys are
still *in* the bundle - they are just referenced by nothing. The stronger sweep is "key present in the
bundle, referenced by no `.java`", and it is what produced three of the five C findings.

### D5 - the legacy `autonomy.json` settings DO come across now, and the format round-trips

`RGN-A1`'s largest half has been fixed. `importLegacy` now copies every top-level key that is not
`points` or `edges` into the configuration's globals, and the comment above it cites `RGN-A1` by name
(`AutonomySession.java`, the block beginning "AND THE SETTINGS ABOVE THE POINTS (RGN-A1)"). Route
activations are deliberately excluded, with a reason given. The edge lengths are still not carried, and
`RGN-A1` stays open for that; the edge *commands* are `R28-B1`.

Separately, the JSON format itself has not lost anything. Keys written by `Layout.toJSON` at 2.8.1:
`activateRouteIDs`, `activateRoutes`, `atomicRoutes`, `defaultLocSpeed`, `edges`, `maxActiveTrains`,
`maxDelay`, `maxLatency`, `maxLocInactiveSeconds`, `minDelay`, `points`, `preArrivalSpeedReduction`,
`simulate`, `timetable`, `turnOffFunctionsOnArrival`, `turnOnFunctionsOnDeparture`. HEAD writes all
sixteen plus `pathPreference` and `timetableSequential`, and reads both back
(`Layout.java:7791`, `o.has("pathPreference")`). No key was dropped from either side.

### D6 - `sanitizeFilename` is character-identical

`CS2File.sanitizeFilename` now delegates to `Util.sanitizeFilename` (`CS2File.java:278-281`), whose body
is the same expression 2.8.1 had:
`name.replaceAll("[\\\\/:*?\"<>|\\x00-\\x1F]", "_")` (`Util.java:495` vs
`master:CS2File.java:281`). A page written by 2.8.1 is looked up under the same name at HEAD.

### D7 - the rename-into-conditions repair was generalised, not lost

2.8.1 fixed it with a `MarklinRoute.locomotiveRenamed` override. At HEAD there is no override, which
looks like a lost fix and is not: `Route.locomotiveRenamed` now iterates `namesLocomotives()`
(`Route.java:156`), which walks the commands **and** `NodeExpression.toList(this.getConditions())`
(`Route.java:223-241`) and returns the live objects. Strictly better - it is in the base class, so a
`Route` that is not a `MarklinRoute` gets it too. `MarklinControlStation.renameLoc` also documents why
the third repair site (the running layout's home assignments) was dropped: a `Point` holds the
`Locomotive` rather than its name now, so a rename changes the object (`MarklinControlStation.java:3059-3066`).

### D8 - `Locomotive`'s wait loops are strictly better

At 2.8.1 an `InterruptedException` inside `waitForOccupiedFeedback`, `waitForClearFeedback`,
`waitForAccessoryState`, `waitForSpeedAtOrAbove` and `waitForSpeedBelow` re-set the interrupt flag
*inside* the loop, so the next `wait()` threw immediately and the thread busy-spun until the condition
came true. HEAD sets a local and re-asserts the flag on the way out
(`Locomotive.java:602`, `:653`, `:688`, `:725`, `:872`), so the thread actually waits and the caller
still sees the interrupt. Neither revision exits the loop early, so no caller's contract changed.

### D9 - four other small paths, all improvements

`CS2Message.getSubCommand` guards on `this.length` rather than `this.data.length`
(`CS2Message.java:443`); the comment records that the old guard could not fire and a short system frame
therefore read as `CMD_SYSSUB_STOP`. `TrainControl.main` now names the "already running" case in plain
English (`TrainControl.java`, the `isPortInUse` branch in `main`). `AutoJSONExport`'s file chooser moved onto the EDT with only the
write off it. `NetworkProxy` names and daemonises its reader thread, retries the web check
(`CSDetect.checkWebServer`), and null-guards `this.model` on the send path.

### D10 - `RGN`'s findings against this baseline

Every one of them was reached from `v2_7_4c`; here is whether it is also true of 2.8.1, checked rather
than assumed. None is re-filed.

| `RGN` | against v2.8.1 | evidence |
|---|---|---|
| A1 (legacy import drops everything but points) | **half fixed**, still open for edge lengths | `R28-D5` |
| A2 (Auto tab disabled for legacy users) | **unchanged and still true** | `TrainControlUI.java:3658`, `:3677` read verbatim; the comment at `:3676` still asserts the opposite of the first clause |
| B1 (`Point:` captions erased from `.cs2` pages) | true - 2.8.1 is where those labels come from (`LayoutGrid.LAYOUT_STATION_PREFIX`) | `AutonomySession.open()` still calls `migrateStationLabels()` unconditionally |
| B2 (s88-fired route drops accessories, does not stop) | **true and newer than RGN thought** - the whole conflict concept is absent at 2.8.1: `git show master:.../MarklinRoute.java \| grep skipAccessories` returns nothing | `MarklinRoute.java:625`-equivalent, `boolean skipAccessories = auto && conflict != null` |
| B3 (v2.7.4 changelog section rewritten) | not applicable to a 2.8.1 reader | - |
| C1 (autosave forced on) | true | `TrainControlUI.java:870-871` |
| C2 (CS2 route delay `sekunde=2.3`) | true - `master:CS2File.java` still truncates then scales | |
| C3 (bracketed locomotive name refused) | true, and now enforced at a **fourth** door 2.8.1 did not have (`TrainControlUI.java:22202`, the Central-Station rename proposal) - which is a fix, not a widening | |
| C4 (`UIState.data` downgrade) | downgrade-only, unchanged | `R28-D3` |

### D11 - the two new refusals in `isPathClear` are deliberate

`isPathClear` gained a train-too-long-to-reverse test (`Layout.java`, the block quoting Adam: *"Do you
sum the track segments leading up to it? ... if segments < train length, then we can't reverse over the
switch"*) and a destination-blocked-by-another-point test (FR-001), and **lost** the
`isTerminus() && !loc.isReversible()` refusal. All three are the terminus-tier ruling recorded in commit
`280ff08b` and in the briefing's own doctrine note; the removal makes 3.0.0 *less* restrictive on the
manual path, which is the intent. The length test is skipped entirely when any segment on the path is
unmeasured (`measured = false`), so it cannot refuse a train on the strength of a zero it invented.
Read and cleared, not filed.

### D12 - route import/export, sort order, and ids

`exportRoutes`, `parseRoutesFromJson`, `importRoutes`, `RouteCommand.toJSON`/`fromJSON`,
`refreshRouteList`, `ROUTE_STARTING_ID` and the id allocation in `newRoute` are untouched between the
two revisions (this repeats `RGN-D4`/`D6` at the nearer baseline, where it also holds). The one new
gate, `isLocalRouteId` (`MarklinControlStation.java:1845`), decides whether to skip a Central Station
sync after an edit and never changes an id.

---

## Open questions - things I could not settle by reading

The rule for this round was no execution, so these are stated rather than answered.

1. **`R28-A1`'s reach.** Does any path other than the two UI delete doors reach
   `MarklinControlStation.deleteLoc`? I found `LocomotiveMenuItems.java:110`,
   `TrainControlUI.java:15408`, `:17363` and `:22217` (the Central-Station rename proposal, where the
   locomotive being deleted is immediately replaced by another of the same name - so at 2.8.1 the route
   commands would have survived and re-bound, and at HEAD they are gone). A Central Station **sync**
   does not delete: `locDB.delete` at `MarklinControlStation.java:1364`, `:2866` and `:3047` are the
   re-key paths, which deliberately do not go through `deleteLoc`. Worth one grep by somebody who can
   run the suite: does any test cover a route surviving a locomotive delete?

2. **`R28-B1` measured.** Build a configuration from Adam's own diagram and diff the emitted per-edge
   `commands` against `cs2_sample_layout/config/autonomy_legacy/autonomy.json`, keyed by
   `start`/`end`. My claim is that the fifteen signals do not come back; the switches should. This is
   the single most valuable thing somebody with a machine could do with this report.

3. **`R28-B2`.** Confirm by eye that the Auto tab is greyed on a Central Station layout and that the
   configuration page is absent on a local one. Both follow from reading three predicates; neither has
   been seen.

4. **`R28-C3`.** Is the home *locomotive* named anywhere on the track diagram at HEAD, as opposed to the
   home *station* being outlined? I found no drawing code that reads `getHomeLoc`, but the diagram
   drawing at HEAD is large and I read it by search rather than in full.

## What this pass did not look at

- **Autonomy runtime parity.** Whether a train picks the same route and holds the same locks as it did
  at 2.8.1. `RGN` left this gap for the same reason (it needs a jar and a layout) and so do I.
  Everything above about autonomy is about reaching it or configuring it, not about what it does.
- **`LayoutGrid` / `LayoutLabel` drawing**, 2,144 changed lines between them, read only far enough to
  answer `R28-C3`. The layout editor had its own round (`LE`) and I did not re-derive it.
- **`HomeStaging`** (906 changed lines) and the timetable executor. Both existed at 2.8.1 and both
  changed heavily; I checked only that the settings that drive them round-trip (`R28-D5`).
- **The eight message bundles' key-set agreement.** I diffed the English bundle against 2.8.1; I did
  not check that all eight still hold identical key sets at HEAD.
- **`MarklinControlStation.syncWithCS2`** and the backup archive, beyond the format questions in
  `R28-D3` and `R28-D6`.
- **Nothing was executed**, so every "confirmed by reading" is a claim about what the code says.
