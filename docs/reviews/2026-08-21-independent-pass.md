# Independent pass - 2026-08-21

**Prefix for citing this document: `IP`.**

**Version reviewed:** commit `7f482897`, branch `autonomy-diagram-r0`, v3.0.0 Beta.

**Scope:** the project as it stands, deliberately NOT the recent changes - `TR` covers those. Areas the
July and August cycles worked over heavily (the autonomy store's square-keyed structures, the route
editor's tables, the diagram editor's selection, the CAN message handlers) were read only far enough to
avoid re-raising what is already recorded. Weight went to the corners those passes left alone: the
CS2/CS3 file parsers, locomotive functions and consists, the timetable and return-home planning,
`GraphReducer` and the arrival-side split, accessory address and protocol handling, and the save and
restore of UI state.

**Reviewed:** 2026-08-21. Read-only. Nothing was compiled and nothing was run - no `javac`, no TestNG,
no application. Every claim below is from reading source, and each finding says what was read.

**Method, honestly.** Most of the file was read by me directly. Two areas were read by delegated
readers working from the same rules - the UI state save/restore path, and `GraphReducer` / `TileGraph` /
`StationIndex` / `TilePorts`. **Every finding either of them returned was re-verified by me against the
source before it was written down here**, including the sibling code each rests on; the line numbers and
quotes below are ones I opened myself. Two of their reports (`IP-B2`, `IP-C2`) survived that check
intact, two (`IP-A1`, `IP-B3`) survived with the severity re-argued, and their negative results are
recorded under D so a later reader knows what was looked at.

**What I did NOT cover.** No dynamic behaviour of any kind. `TrainControlUI` (20k lines) was read only
where a trail led into it - `saveState`/`restoreState`/`setViewListener`, the layout page menus, the
label click paths - so the great majority of it is unexamined here. `AutonomySession` (3.6k lines) and
`AutonomyCompanionStore` (2.7k lines) were read only around the reconcile and shared-field paths.
`RouteEditorFrame`, `LayoutEditor`, `NodeExpression`'s text parser, `DiagramMonitor`, `TileAnnotation`,
`TileOverlay` and the whole `test/` tree were not reviewed. No message bundle was audited (`TR-D7` did
that). No I18n key was checked to exist.

**Confidence** is mine: CONFIRMED means traced end to end in the source; PLAUSIBLE means the mechanism
is certain but the trigger could not be exercised from a reading.

**On the size of this pass.** Nine findings, one of them A. That is a low yield for the ground covered,
and it should be read as such: the two prior cycles took most of what was there, and several of the
things below are twins of fixes those cycles already made in one place and not the other. The single
most useful sentence in this document is probably in D3 rather than in any A or B.

---

## Status

| # | Finding | Severity | Conf. | Status |
|---|---|---|---|---|
| A1 | An unreadable `UIState.data` is reported as "no data file found" and then overwritten on exit, with no copy kept - the identical hazard on `LocDB.data` is guarded and documented | A | CONFIRMED | Fixed 2026-08-21 |
| B1 | The CS2 route importer reads `stellung 2` as both drives thrown - a three-way position that does not exist - and sets the base address to the opposite of what the CS3 importer sets it to | B | CONFIRMED (defect), PLAUSIBLE (trigger) | Open |
| B2 | A link switched off with "Use link" still blocks the build, and the undirected path search walks through one that `exits()` refuses | B | CONFIRMED | Open |
| B3 | Page names, the active page and the active button are dropped - then erased on exit - whenever the page-count preference is at least the size of the saved state | B | CONFIRMED | Fixed 2026-08-21 |
| B4 | `parseLayout` has no per-record guard, unlike its two siblings in the same file, and the call site's response to any failure is to clear the user's local-layout folder | B | CONFIRMED (mechanism), PLAUSIBLE (trigger) | Fixed for the page |
| C1 | An `Error` escaping `executePath` on a timetable thread leaves the run permanently "running"; the autonomy dispatcher catches `Throwable` for exactly this reason | C | PLAUSIBLE | Fixed 2026-08-21 |
| C2 | `GraphReducer` reports one self-loop or one parallel route several times; `TileGraph.validatePortals` de-duplicates and says why | C | CONFIRMED | Fixed 2026-08-21 |
| C3 | `LayoutDiagram.saveChanges` writes a renamed page under the raw name, while `CS2File` reads it back through `sanitizeFilename` | C | CONFIRMED (mismatch), PLAUSIBLE (trigger) | Fixed 2026-08-21 |
| C4 | `parseFile` collapses `", "` inside array values, so a multi-unit member whose name contains a comma-and-space is dropped from its consist | C | CONFIRMED (mechanism), no real fixture triggers it | Open |
| D1 | `Util.writeAtomically` and its six call sites | - | - | Clean |
| D2 | `KNOWN_SHARED` against the keys `sharedFields()` actually writes | - | - | Clean |
| D3 | `RemoteDeviceCollection.add` evicting a duplicated locomotive | - | - | Not a defect |
| D4 | Lock-order inversion between the Layout monitor and the `activeLocomotives` monitor | - | - | Not a defect |
| D5 | The arrival-side split, lock-edge symmetry, grid-edge bounds and key collisions in the reducer | - | - | Clean |
| D6 | `MarklinSimpleComponent` round trip and `serialVersionUID` | - | - | Clean |
| D7 | `validatePathActuation` returning `true` on interrupt | - | - | Not a defect (unreachable) |
| D8 | `CS2File`'s `magList` / `locList` caches going stale across syncs | - | - | Not a defect |
| D9 | The comment at `Layout.java:5077` naming the wrong field as the capture guard | - | - | Not a defect (comment only) |

---

## A - high

Wrong behaviour on the layout, or data silently lost.

### A1 - An unreadable `UIState.data` is reported as a first launch and then overwritten on exit

**Where:** `src/org/traincontrol/gui/TrainControlUI.java:1667-1701` (restore), `:1390-1399` (save),
`:12510-12511` (window close). Compare `src/org/traincontrol/marklin/MarklinControlStation.java:1599-1626`
and `:1455-1487`.

**What is wrong.** `TrainControlUI.restoreState()` cannot tell "there is no file" from "the file is
there and would not read", logs a message asserting the first for both, and the save on the way out then
writes the resulting empty state over the file with no copy kept.

**The failure.** `restoreState()` catches `IOException` and logs `ui.infoUiInitializingDefaultData`,
which `messages.properties:64` spells "No data file found, UI initializing with default data." Any read
failure lands there: a handle held for a moment by a sync client or an antivirus scanner, a permissions
change, a file truncated by an earlier hard kill. `instance` stays empty, `setViewListener` proceeds with
it, `:3971-3977` maps the first locomotive onto Q, and `WindowClosed` calls `saveState(false)`, which
serialises `locMapping` - now one locomotive on one key - straight over `UIState.data`. Every
locomotive-to-key mapping on every page, every page name, and the saved active page and button are gone,
permanently. `Util.writeAtomically` is no help and the author already says why, at
`MarklinControlStation.java:228`: *a complete successful write of nothing is not a partial write.*

**Why this is a defect rather than a decision.** The identical hazard on the locomotive database is
handled, explicitly and with the reasoning written down. `MarklinControlStation.restoreState` clears and
then sets `databaseLoadFailed` from `new File(dataFile).exists()` inside its `IOException` catch
(`:1605`, `:1621-1626`), and `saveState` copies the unreadable file aside as
`tc_backup/unreadable<timestamp>LocDB.data` before replacing it (`:1462-1487`), logging
`log.databaseUnreadableKept`. The comment there describes this exact sequence - "One transient read
failure at startup plus a normal exit used to destroy every locomotive customization the user had ever
made". `UIState.data` is written by the same session, in the same working directory, on the same window
close, and got neither the distinction nor the copy.

**Mitigation, stated fairly.** Unlike the locomotive database - whose loss is masked because the next
Central Station sync repopulates the list - an empty page of buttons is visible at startup, so a user
who notices before closing the window can avoid the overwrite. Against that: the log tells them it was a
first launch, there is no dialog, and there is no kept copy if they close the window to go and
investigate. The `ClassNotFoundException` branch (`:1693-1698`, `ui.errorBadUiDataFile`) names the cause
honestly but leads to the same overwrite.

**What I read to verify.** All uses of `DATA_FILE_NAME` in `TrainControlUI` (`:148`, `:1383-1384`,
`:1672`) - `saveState` is the only writer and `restoreState` the only reader; the whole of
`saveState(boolean)` at `:1351-1412`; the `WindowClosed` handler; `Util.writeAtomically`
(`Util.java:231-254`); `MarklinControlStation.restoreState(String)` and `saveState(boolean)` in full;
and `messages.properties:64-65` and `:1138` for the three strings.

**Confidence:** CONFIRMED.

---

## B - medium

Incorrect results, or crashes in specific configurations.

### B1 - The CS2 route importer drives a three-way into a position it cannot hold

**Where:** `src/org/traincontrol/marklin/file/CS2File.java:851-872`, with its comment block at `:874-883`.

```java
if (setting >= 2)
{
    pauseAfter = RouteCommand.RouteCommandAccessory(id + 1, accType, setting == 2);
    r.addItem(pauseAfter);
}

RouteCommand primary =
    RouteCommand.RouteCommandAccessory(id, accType, setting != 1 && setting != 3);
```

**What is wrong.** For `stellung 2`, `setting != 1 && setting != 3` is true and `setting == 2` is true,
so the route is imported as *both* drives thrown - which is not one of a three-way's three positions -
and the diverging command is emitted *before* the other drive has been released.

**The failure.** `true` means thrown: `RouteCommandAccessory`'s third argument becomes `KEY_SETTING`
(`RouteCommand.java:86-95`), `MarklinRoute.execRoute` passes it to `setAccessoryState`
(`MarklinRoute.java:371`), and that calls `turn()` (`MarklinControlStation.java:2979-2986`). So running
an imported CS2 route whose item carries `stellung=2` on a three-way turnout sends address N+1 to thrown
and then address N to thrown. Three places in this codebase state that this combination must not
happen:

- `LayoutDiagramComponent.getPrimaryDriveState()` (`:271-274`) computes `state == 0` for a three-way,
  and its javadoc at `:260-270` says in as many words that the formula this parser still uses was the
  bug: *"Seeding from state != 1 made state 2 both drives thrown, which is none of the three, so the
  turnout opened in a position it cannot physically be in."*
- `LayoutDiagramComponent.execSwitching()` (`:147-167`) cycles a three-way through (S,S) -> (T,S) ->
  (S,T) -> (S,S) and never through (T,T), releasing before throwing in every branch.
- `ThreeWaySwitch.expand()` (`:104-131`) writes the same three shapes and explains the ordering:
  *"the motor that must end up straight is settled FIRST ... and only then does the one that chooses the
  branch move."* `Layout.configureEdge` (`:1939-1962`) sorts released-before-thrown for the same reason.

The same expression also disagrees with the *CS3* importer twenty pages away in this file. For a
three-state accessory at `stellung 2`, `parseRoutesCS3` (`:1335-1357`) emits `(address, false)` then
`(address + 1, true)` - base released, second drive thrown. The CS2 branch emits `(id + 1, true)` then
`(id, true)`. The two importers therefore command the base address to opposite states for the same
route. They agree on `stellung` 0, 1 and 3; only 2 diverges. `magnetartikel.cs2` in the test fixtures
carries `.typ=dreiwegweiche` with `.stellung=2` (`test/magnetartikel.cs2:34-41`), so the encoding is
real; the three-aspect form signals at ids 107 and 109 (`:859-865`) are the other accessory kind that
uses it.

**Trigger.** Neither shipped route file exercises it. `test/fahrstrassen.cs2` and
`Oles kreds/config/fahrstrassen.cs2` between them carry 698 `stellung=1` items and 18 `stellung=3`, and
no `stellung=0` or `stellung=2` at all. So the defect is certain and a user file that reaches it has not
been seen. Per README that keeps it a B rather than an A, and would keep it out of a changelog entry
until a real file is found.

**What I read to verify.** `parseRoutes` in full (`:717-912`) including the comment block that documents
the intended mapping; `parseRoutesCS3`'s `mag` branch (`:1257-1387`); `RouteCommand.RouteCommandAccessory`
and `getSetting` (`:86-95`, `:310-313`); `MarklinRoute.execRoute` (`:333-557`);
`MarklinControlStation.setAccessoryState` (`:2958-2987`); `LayoutDiagramComponent.execSwitching` and
`getPrimaryDriveState`/`getSecondaryDriveState`; `ThreeWaySwitch` in full; and the two route fixtures and
`test/magnetartikel.cs2`.

**Confidence:** CONFIRMED (defect), PLAUSIBLE (trigger).

### B2 - A link switched off still blocks the build

**Where:** `src/org/traincontrol/automationui/TileGraph.java:684-715` (the pairing loop) and `:1178-1183`
(the undirected walk). Compare `:574` and `:727`, which do consult the same set.

**What is wrong.** `disabledPortals` is honoured by `exits()` and by the never-paired warning loop, and
ignored by `validatePortals()`'s pairing loop - every problem of which is `blocking = true` - and by
`continuations()`.

**The failure.** The field's own javadoc (`:313-319`) states the use case: *"A diagram can carry a link
that belongs to the drawing rather than to the railway autonomy runs - a jump to a page that is only
ever driven by hand, say - and refusing to build until it is paired would be autonomy insisting on
something the user has already decided against."* Pair link A on page *main* to link B on page *hidden*,
then exclude *hidden* from autonomy. `tiles` no longer contains B, `pages` still does, so
`validatePortals` raises `ERROR_PORTAL_EXCLUDED` with `blocking = true` (`:692-694`). The remedy the UI
offers is the "Use link" toggle (`AutonomyEditorPanel.java:1152-1155`), which calls
`AutonomySession.setPortalDisabled` -> `AutonomyCompanionStore.setPortalDisabled`
(`AutonomyCompanionStore.java:676-686`) and **only adds to the disabled set - it never unpairs**.
`applyTo` (`:1999-2014`) replays the pairing first and the disable second, so the tile reaches the graph
paired *and* disabled: `exits()` declines to offer a way through it (`:574`), it contributes nothing to
the derived graph, and the blocking error stands. `hasBlockingProblems()` (`TileGraph.java:813-820`,
`AutonomySession.java:2617-2620`) is what refuses the load - `TrainControlUI.java:3416` replaces the
"load this setup" button with "fix setup", and `AutonomyMenu.java:292` and
`AutonomyViewerPanel.java:436`, `:763` gate on the same call. So autonomy will not run, and the only way
out is to know that Unpair has to be used as well as the toggle. `AutonomyChecks.run` (`:301-307`) copies
the problem through verbatim; nothing downstream filters by disabled state. The redrawn-far-end case
(`:704-709`) reaches the same dead end.

The second half is smaller and in the same shape. `continuations()` (`:1178-1183`) adds a portal's
partner with no `disabledPortals` test, so `findUndirectedPath` (`:1049`) walks through a link that
`exits()` refuses to walk through. Its only caller is `AutonomySession.setOneWayRun` (`:2983-2990`),
which hands the path to `applyOneWay` - so a one-way run drawn between two pages joined only by a
switched-off link reports success and writes directions onto tiles that no derived edge ever crosses.

Supporting: `TileGraph.isPortalDisabled` (`:330`) is public and has no caller anywhere in `src` - the
set is consulted only from inside the class, and only at two of the three places it matters.

**What I read to verify.** `TileGraph.java` `exits()` around `:565-585`, `validatePortals()` in full
(`:675-762`), `continuations()`/`neighbour()` (`:1160-1198`), `hasBlockingProblems` and `getProblems`;
`AutonomyCompanionStore.setPortalDisabled`, `pairPortals` and `applyTo`; the menu construction at
`AutonomyEditorPanel.java:1140-1175`; `AutonomyChecks.run` (`:289-325`); the four `hasBlockingProblems`
call sites, and `TrainControlUI.java:3400-3425`.

**Confidence:** CONFIRMED.

### B3 - Page names and the restored position are dropped, then erased, when the preference outruns the file

**Where:** `src/org/traincontrol/gui/TrainControlUI.java:3939-3941`, with the preference declared at
`:182-183`.

```java
// Restore page names, active button, and page, which are stored at the end
if (saveStates.size() > this.numLocMappings)
{
    this.pageNames = saveStates.get(saveStates.size() - 1);
```

**What is wrong.** The page-names map is *always* the last element of the saved list, whatever the page
count is (`saveState`, `:1375-1379`), but its restore is gated on a comparison that only holds when the
count in the preference and the count in the file agree exactly.

**The failure.** The grow loop immediately above (`:3894-3915`) handles `numLocMappings` being too
*small* - that is `TR-B5`, and it is fixed. Nothing handles it being too *large*. In that case `:3939`
is false, `this.pageNames` stays the empty `HashMap` from `:522`, and the saved active page and active
button (`SAVE_KEY_ACTIVE_MAPPING_NUMBER`, `SAVE_KEY_ACTIVE_BUTTON`) are skipped with it. Then
`saveState` on the way out serialises the now-empty map at `:1379` and the names are gone from the file
as well. No error and no dialog; the button mappings survive, so it reads as "my page names vanished".

Two ways the counts diverge, both without a second machine:

- `addLocMappingPage` writes the preference the instant the page is added (`:1061-1065`), while
  `UIState.data` is written only on a clean exit (`:12510-12511`). Add a page, then have the JVM die or
  the machine shut down before the window closes: the preference says 11, the file holds 10 pages plus
  names = 11 entries, and `11 > 11` is false.
- `LOC_MAPPING_PAGES_PREF` is declared *without* the folder hash (`:183`), unlike every other preference
  that describes a particular working directory - `IP_PREF` (`:178`), `LAYOUT_OVERRIDE_PATH_PREF`
  (`:179`), `ONTOP_SETTING_PREF` (`:191`), `AUTO_POWER_ON` (`:205`), `AUTO_LOAD_AUTONOMY` (`:207`),
  `ENHANCED_PATH_VALIDATION` (`:208`). `prefs` is `Preferences.userNodeForPackage` (`:427`), so the count
  is shared across every folder the user runs TrainControl from, while the thing it counts lives in each
  folder's own `UIState.data`. Adding a page in one folder silently disarms the restore in the other.
  This repository already contains `tc_backup` and `z_backup` folders and several `LocDB_N.data` copies,
  so two working folders is not a hypothetical.

Restoring an older `backup<ts>UIState.data` over `UIState.data` from the backup folder reaches the same
state, and that is a recovery path a user in trouble would actually take.

The mapping loop at `:3917` also runs one iteration too far in this case and reads the page-names map as
if it were a page - harmless, because its keys are page numbers and the sentinels `-1`/`-2` while
`buttonMapping` is keyed by `VK_A`..`VK_Z`, so every lookup returns null.

This is a different line and a different direction from `TR-B5`, which is the saved state being *larger*
than the preference and whose symptom is a missing tab.

**What I read to verify.** Every reference to `numLocMappings`, `pageNames`, `LOC_MAPPING_PAGES_PREF` and
`locMapping` in the file; the constructor initialisation at `:522-532` and `:887-889`;
`addLocMappingPage` (`:1059-1074`) and `deleteCurrentLocMappingPage` (`:1082-1130`); `saveState`'s
serialisation order at `:1355-1379`; the whole of `setViewListener` down to `:3986`; and the preference
block at `:174-210`.

**Confidence:** CONFIRMED.

### B4 - One bad element in one page aborts the whole diagram import, and the answer is to forget the user's layout folder

**Where:** `src/org/traincontrol/marklin/file/CS2File.java:2233-2418` (no per-record guard),
`src/org/traincontrol/marklin/MarklinControlStation.java:441-454` (the response).

**What is wrong.** `parseLayout` parses every element of every page with nothing catching a per-element
failure, while the two sibling parsers in the same file were both given per-record guards with comments
explaining why - and the call site's reaction to any failure at all is to clear
`LAYOUT_OVERRIDE_PATH_PREF`.

**The failure.** Four expressions in `parseLayout` throw on a malformed field and none is guarded:
`Integer.valueOf(m.get("id").replace("0x", ""), 16)` at `:2241` and `:2288`,
`Integer.valueOf(m.get("drehung"))` at `:2343`, `Integer.valueOf(m.get("zustand"))` at `:2348`. A missing
or unreadable *page file* does the same at `:2221`, where `fetchURL` throws `FileNotFoundException` for a
page the index names but the folder does not hold. Any of these aborts the parse of every page, not just
the offending element, and propagates to `syncLayouts` and then to
`syncLayoutsFromConfiguredSource`, whose catch (`:446-454`) logs, then does
`TrainControlUI.getPrefs().put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "")` and re-syncs from the
Central Station. The user's configured local layout folder is forgotten permanently; `isLocalLayout()`
(`:19257-19260`) now returns false, so `repaintPathLabel` disables `modifyLocalLayoutMenu` and
`switchCSLayoutMenuItem` (`:19341-19356`) and the diagram editor is simply gone from the menus until they
find the folder again. The diagrams meanwhile come from the Central Station, under whatever page names
*it* uses - and `AutonomySession.save()` reconciles the autonomy setup against the pages the session
holds (`:3547-3576`), with `AutonomyCompanionStore.reconcile` (`:1879-1942`) dropping every tile property
and unreferenced name whose key is not in the set it is given. There is no floor on how much a reconcile
may drop.

The asymmetry is what makes this a finding. `parseRoutes` catches `NumberFormatException |
ArrayIndexOutOfBoundsException` per route at `:892-907`, saying *"Letting this propagate would reach
syncWithCS2's outer catch and abandon the entire import - every route and locomotive - because one record
was malformed."* `parseLocomotives` does the same at `:1815-1826`. `parseLayout`, in the same class, was
left with none.

**Trigger.** Weaker than the mechanism, and I will not claim more. Both `gleisbild.cs2` and the page
files are written atomically now (`LayoutDiagram.java:801-848`, `:437-467`, `CS2File.java:1899-1925`), and
the download only sets the preference after it has completed (`TrainControlUI.java:17599-17614`), so
TrainControl does not itself produce a half-written folder. What remains is a hand-assembled folder -
which the application documents and supports, via `layout.configFolderStructureHint` - a folder on a
network or sync-backed drive with a file momentarily unavailable, or a page renamed on the Central
Station between the download and the read. I could not construct a shipped file that triggers it.

**What I read to verify.** `parseLayout` in full including both element passes; `parseRoutes` and
`parseLocomotives` and their catch blocks; `syncLayouts` and `syncLayoutsFromConfiguredSource`
(`MarklinControlStation.java:397-530`); `refreshLayouts`; all eleven references to
`LAYOUT_OVERRIDE_PATH_PREF`; `isLocalLayout`/`getLayoutPath`/`repaintPathLabel`;
`AutonomySession.save`/`saveWithoutReconciling`; `AutonomyCompanionStore.reconcile`; and
`Util.writeAtomically`.

**Confidence:** CONFIRMED (mechanism), PLAUSIBLE (trigger).

---

## C - low

Cosmetic, dead code, or narrow edge cases. An open C is still a C.

### C1 - An `Error` on a timetable thread leaves the run "running" for ever

**Where:** `src/org/traincontrol/automation/Layout.java:3617-3728`. Compare `:2865-2872`.

**What is wrong.** The per-entry timetable thread wraps its dispatch in `catch (Exception e)`, while the
autonomy dispatcher two thousand lines away wraps the identical call in `catch (Throwable e)` with a
comment saying why.

**The failure.** `executePath`'s own handler is `catch (RuntimeException e)` (`:3838`) - it removes the
locomotive from `activeLocomotives`, clears its milestones and its pending s88, and stops it. An `Error`
skips all of that. On the timetable path it then skips the thread's `catch (Exception e)` at `:3699` and
also the `if (index == this.timetable.size() - 1)` block at `:3711-3728`, which is the *only* place the
last entry's thread clears `running`. `isRunning()` is `running || !activeLocomotives.isEmpty()`, so both
halves stay true, and the completion wait at `:3749-3760` - `while (this.isRunning() &&
this.isCurrentLayout())` - never returns. `executeTimetable` never returns to its caller, so the Start
and Graceful Stop buttons never come back, and nothing short of a graph reload recovers. On the autonomy
path the same escape is caught: `runLocomotive` at `:2869` uses `catch (Throwable e)` and the comment
above it explains that an exception escaping there "killed this locomotive's thread with its track still
held".

**Trigger.** An `Error`, which means `OutOfMemoryError` or `StackOverflowError` in practice. The heap
leak that would have supplied the first is `TR-A23` and is fixed. `Locomotive.waitForOccupiedFeedback`
and `waitForClearFeedback` recurse rather than loop when the sensor fails its `minDuration` re-check
(`Locomotive.java:801-813`, `:869-878`), so a sensor that flickers for long enough would supply the
second - but that is thousands of consecutive flickers and I cannot claim it happens. Reported because
the asymmetry is a trap for the next author, not because I have seen it fire.

**What I read to verify.** `executeTimetableInternal` from `:3569` to `:3762`; `executePath` and
`executePathInternal`; `runLocomotive`; `isRunning`, `stopLocomotives`, `runLocomotives`; and the
recursion in both `Locomotive` waits.

**Confidence:** PLAUSIBLE.

### C2 - One self-loop is reported as several

**Where:** `src/org/traincontrol/automationui/GraphReducer.java:302` (the list), `:810-812` and
`:825-833` (the additions). Compare `TileGraph.java:746-761`.

**What is wrong.** `problems` is a plain `ArrayList` and `walkEdges` appends to it once per *branch*
rather than once per fact.

**The failure.** `walkEdges` (`:748-768`) launches one walk per side of every Point and per exit of every
side. A plain circle of track carrying exactly one sensor A is walked out east - returning to A by west -
and out west, returning by east: two `WARN_SELF_LOOP` problems, same tile, same key, for one balloon
loop. For `WARN_PARALLEL_ROUTE`, N reconverging routes between one ordered pair of Points produce N-1
problems all carrying `start`. `AutonomyChecks.run` (`:309-314`) copies each `Problem` to a `Finding`
one-for-one, so the editor's list shows the same square twice with the same sentence, which reads as two
things to fix when fixing either makes both disappear.

The sibling states the rule and implements it. `TileGraph.validatePortals` (`:746-761`) filters its
`found` list against `problems` on tile-and-key before adding, and its comment is exactly the argument
above: *"the same tile and the same message twice is one problem reported twice - which reads as two
things to fix and cannot be, since fixing it makes both disappear."*

**What I read to verify.** `GraphReducer.walkEdges`, `walk` and `continueWalk` in full;
`TileGraph.validatePortals`; `AutonomyChecks.run` (`:289-325`). I did not run the UI, so I cannot say
what the list looks like on screen - only that two identical `Finding`s reach it.

**Confidence:** CONFIRMED.

### C3 - A renamed page is written under a name the reader will not look for

**Where:** `src/org/traincontrol/base/LayoutDiagram.java:433-435`. Compare
`src/org/traincontrol/marklin/file/CS2File.java:252-257` and `:259-283`.

**What is wrong.** `saveChanges` builds the destination with `originalFilePath.resolveSibling(filename.trim()
+ ".cs2")` - the raw name - while `CS2File.getLayoutURL` locates a local page with
`sanitizeFilename(layoutName)`, and `writeLayoutIndex` records the raw name in `gleisbild.cs2`.

**The failure.** `sanitizeFilename`'s javadoc names this exact hazard: *"Applied on BOTH sides on
purpose. The local read locates a page by the name in the index, so sanitizing only the write would
produce a file the reader then could not find."* Both sides were done - for `downloadCS2Layout`
(`CS2File.java:1877`) and for the read (`:255`). `saveChanges` is a third writer, and it was not.
Rename a page through `duplicateOrRenameCurrentLayout` (`TrainControlUI.java:17295-17372`) to a name
holding any of `\ / : * ? " < > |` and the file lands somewhere `getLayoutURL` will not look. On Windows
most of those characters make the write throw, which fails safely - the index is only written *after*
`saveChanges` returns (`:17347-17353`), so nothing is committed. The two that do not throw are `/` and
`\`, which `Path.resolveSibling` reads as directory separators: `Yard 1/2` becomes
`gleisbilder/Yard 1/2.cs2`, which fails cleanly if `Yard 1` does not exist and succeeds - into the wrong
place, with the index naming a file that will be looked for as `Yard 1_2.cs2` - if it does. From there,
`IP-B4`: the whole layout import fails and the local-layout folder is forgotten.

Nothing validates the entered name against `sanitizeFilename` before offering it, and the pre-check at
`:17318` only tests for a name that already exists.

**Trigger.** A page name containing a slash. Natural enough ("Up/Down", "N/S Yard") to be worth guarding;
not something I can show has happened.

**What I read to verify.** `saveChanges` in full (`:424-498`); `getFilePath`; `writeLayoutIndex`
(`:801-848`); `CS2File.getLayoutURL` and `sanitizeFilename` with its javadoc; `downloadCS2Layout`; and
all four call sites of `saveChanges` and three of `writeLayoutIndex`.

**Confidence:** CONFIRMED (the mismatch), PLAUSIBLE (the trigger).

### C4 - A multi-unit member whose name contains a comma-and-space is dropped from its consist

**Where:** `src/org/traincontrol/marklin/file/CS2File.java:498`, read back at `:1729`.

```java
arrayString = arrayString.replace("}{", "|");
arrayString = arrayString.replace(", ", ",");
```

**What is wrong.** The array block is flattened by calling `HashMap.toString()` and then rewriting its
`", "` entry separator as `","`. That rewrite cannot tell the separator from a `", "` inside a value,
and exactly one array key carries free text: `lokname`, the name of a multi-unit member.

**The failure.** `parseLocomotives` recovers the member names at `:1729` by splitting on `",lok="` and
stripping `lokname=`. A member called `BR 50, Ep. III` is stored as `BR 50,Ep. III` - the space after the
comma gone - so `network.getLocByName` finds nothing at `MarklinLocomotive.setLinkedLocomotives:1132`,
`canBeLinkedTo` logs `loc.errorLinkInvalidObject`, and the member is silently missing from its consist.
Commanding the head then moves one engine of two.

This is the same defect the line above it was fixed for. The comment at `:474-479` explains the `=` half:
*"Split without a limit, a member named 'BR 50 = Ep.III' was stored as 'BR 50 ', matched nothing, and was
dropped from its consist with only a log line to say so."* The `", "` half is the twin, four lines
further down.

**Trigger, checked against the real data.** None of the 244 `.name=` entries across `test/lokomotive.cs2`
and `test/lokomotive_cs3.cs2` contains a comma followed by a space. So this is a latent trap, not
something a shipped fixture reaches - which is why it is a C and not a B, and why it should not have a
changelog entry unless a real file turns up.

**Also worth knowing while you are in there.** The `",lok="` split at `:1729` only works because
`HashMap` happens to iterate `lokname` before `lok` (their spread hashes land in buckets 1 and 9), which
is why the example in that very comment reads `{lokname=...,lok=0x4023|...}`. It is correct today and
correct for every JDK 8 build, but it is an ordering nobody wrote down.

**What I read to verify.** `parseFileContents` in full (`:450-546`); the `traktion` blocks in
`test/lokomotive.cs2:1544-1553`, which confirmed that each member is preceded by its own ` .traktion`
line and so is flushed separately - my first reading of this had the members overwriting each other in
one map, and the fixture disproved it; `parseLocomotives`'s multi-unit branch (`:1721-1746`);
`parseLocomotivesCS3`'s equivalent (`:1536-1595`); `MarklinLocomotive.setLinkedLocomotives` and
`canBeLinkedTo`; and every `.name=` line in both locomotive fixtures.

**Confidence:** CONFIRMED (mechanism), and explicitly NOT triggered by any real file in the repository.

---

## D - not defects

Claims that turned out to be wrong, and checks that came back clean. Recorded rather than deleted, so
the same ground is not walked twice and so a later reader can judge how much of the rest to trust.

| # | Claim or check | Why it is a D |
|---|---|---|
| D1 | `Util.writeAtomically` and its call sites destroy work on a failed write | Clean. `Util.java:231-254` stages through a sibling, deletes the staging file on any `IOException` or `RuntimeException`, and only then `Files.move`s with `REPLACE_EXISTING`; the target is untouched on failure. All six callers checked - `TrainControlUI:1392` and `:1517`, `MarklinControlStation:1502`, `LayoutDiagram:464` and `:847`, `CS2File:1903` - and each closes its writer inside the body, so nothing truncated is ever moved into place. It does not `fsync`, which is a real but different question and out of scope for a desktop application. |
| D2 | `KNOWN_SHARED` omits a field `save()` writes, which the comment says is "quietly destructive" | Clean, and the invariant is worth stating because the class asks for it. `sharedFields()` (`AutonomyCompanionStore.java:773-795`) writes `version, pages, pointNames, stations, tileLengths, tileDirections, barredArrivals, stationSignals, portals, captions, linkNames, excludedPages, disabledLinks`; `KNOWN_SHARED` (`:2057-2060`) holds all thirteen plus `activeConfiguration`, which is written at `:518` on the same root. Nothing is missing in either direction. |
| D3 | `RemoteDeviceCollection.add` evicts a duplicated locomotive, because `names.values().removeIf(mapped.equals(id))` deletes every name pointing at that id, and two locomotives may legitimately drive one decoder | **Wrong, and it is worth saying why.** This is the hazard the July cycle surfaced ("MFX addresses are unique" was plausible and wrong), and I re-raised it from reading `RemoteDeviceCollection` alone. The locomotive database is not keyed on the UID: `MarklinLocomotive.getUID()` (`:395-402`) returns `getName() + '_' + UID` as a *String*, precisely so "the same mm2 address can be re-used", and `locDB.add` is called with that everywhere (`MarklinControlStation:2610`, `:2629`, `:2655`, `:2765`, `:2924`). Two duplicated locomotives therefore have different ids and neither evicts the other. Verifying the layer that enforces the rule, rather than the one that looks like it should. |
| D4 | The Layout monitor and the `activeLocomotives` monitor are taken in both orders, so two driving threads can deadlock | Wrong. `executePathInternal:4298` does take `activeLocomotives` and then, through `unlockPath` (`:2539`, `synchronized`), the Layout monitor. But nothing goes the other way: every `synchronized` method on `Layout` that touches `activeLocomotives` - `getActiveAccs` (`:694`), `locDeleted` (`:718`), `addTimetableEntry` (`:606`) - touches it as a `ConcurrentHashMap` without taking its monitor, and `configureAndLockPath`'s `synchronized (this)` block (`:2259-2297`) calls nothing that does. All ten `synchronized (this.activeLocomotives)` sites checked. |
| D5 | The arrival-side split, lock-edge generation, grid-edge bounds and square-keyed maps in the reducer carry defects | Clean, on a dedicated pass over `GraphReducer`, `TileGraph`, `StationIndex` and `TilePorts`. Per-copy data is applied to *all* copies where it must be (s88, block, protecting signals, destination flag, extras, edge length, lock edges) and pinned to one only where that is the design (`loc`, `home`, each with its own selector; `terminus`/`reversing` are per-copy deliberately). `deriveLocks` emits both directions and its `a == b` guard is reference identity, which catches an edge whose path crosses one tile twice. Out-of-grid neighbours mint a `TileKey` that misses `tiles` and returns null, which is the same answer as a blank square. `TileKey.toString()` round-trips through `parseTileKey` because that uses `lastIndexOf` for both separators, so a page name containing `:` or `,` still parses. There is no `try`/`catch` anywhere in those four files, so nothing is swallowed. |
| D6 | `MarklinSimpleComponent` has drifted, so a saved database silently loses fields | Clean. `serialVersionUID` is pinned (`:56`); every later-added field is null-guarded on read (`getFunctionTriggerTypes:198`, `getHistoricalOperatingTime:275`, `getTrainLength:282`, `getAccessoryDecoderType:314`); and `newLocomotive(MarklinSimpleComponent)` (`MarklinControlStation:2639-2665`) applies every field the constructors capture. |
| D7 | `validatePathActuation` returns `true` when interrupted, so a locomotive departs onto a path whose accessories were never confirmed | Structurally real, unreachable. `Layout.java:2386-2392` does return `true` on `InterruptedException`, and `configureAndLockPath` then reports success. But nothing interrupts a locomotive thread: the only `interrupt`-family calls in `src` are the three `shutdownNow()` on the CAN message executors (`MarklinControlStation:3215-3217`) and one in `CSDetect`, and the driving threads are plain `new Thread(...)` that nothing holds a reference to. Worth fixing as a trap for the next caller; not worth a severity. |
| D8 | `CS2File`'s `magList` and `locList` caches go stale, so a re-sync imports routes against an accessory's old address or protocol | Wrong. `syncWithCS2` builds a fresh `CS2File` on its first line (`MarklinControlStation:1132`), so both caches live exactly one sync. `refreshLayouts` reuses the existing parser but only calls `parseLayout` and `getMagList`, neither of which touches either cache. |
| D9 | The comment at `Layout.java:5077-5079` is wrong about which flag keeps staging moves out of the captured timetable | The comment is wrong and the code is right, which makes this a note rather than a defect. It says `addTimetableEntry` "excludes them for as long as `timetableSequential` is set"; the guard at `:612` is on `timetableExecuting`, which `executeTimetable` sets and clears in a `finally` (`:3511-3524`) around the whole run including the completion wait. The behaviour is what the comment claims; the field named is not the one doing it. |

---

## Dispositions, 2026-08-21

Every finding above was re-verified against the source by me before anything was changed.  Nothing was
withdrawn on that check.  What follows is what was done, in the same order.

| # | Disposition |
|---|---|
| A1 | **Fixed.**  `restoreState` now tells "no file" from "would not read" - it sets `uiStateLoadFailed` when the file exists - and `saveState` copies an unreadable file aside as `unreadable<timestamp>UIState.data` before replacing it, logging where it went.  Straight from the locomotive database, which is the sibling that already had it. |
| B1 | **Open.**  Confirmed as a mismatch between the two importers; NOT fixed here.  Which way `stellung` maps and which drive is the base address are facts about the ironwork, and the wrong choice throws a real three-way into a position it cannot hold.  That wants the railway, not a reading. |
| B2 | **Open.**  Confirmed.  Fixing it changes what BLOCKS an autonomy build, which is a decision about what the setup insists on rather than a defect in how it counts - and a build that stops blocking is a build that starts running on a graph somebody may not have meant.  Worth doing, worth doing deliberately. |
| B3 | **Fixed.**  The page names are the last entry of the saved list whatever the page count is, so the restore is now gated on the list being non-empty rather than on the preference agreeing with the file.  `LOC_MAPPING_PAGES_PREF` also gained the folder hash every other per-folder preference carries; it was added this cycle and unreleased, so nothing had to be migrated.  The page loop stops at the pages instead of reading the names map as one. |
| B4 | **Fixed for the page, open for the element.**  The page loop now guards each page: one that will not parse is logged and skipped, the rest of the layout loads, and the folder preference survives.  `pageIndex` is advanced before the guard so a skipped page does not renumber the others - the autonomy setup is keyed by page id.  A per-ELEMENT guard, which would save the rest of a page rather than the rest of the layout, is not done. |
| C1 | **Fixed.**  The timetable thread catches `Throwable`, as the autonomy dispatcher already did for the same call and for the reason written above it. |
| C2 | **Fixed.**  `noteOnce` filters on tile and key before recording, which is `TileGraph.validatePortals`\'s rule and its comment, applied where the same reasoning holds. |
| C3 | **Fixed.**  `sanitizeFilename` moved to `Util` so that all three writers can reach it - `LayoutDiagram` is in `base` and must not reach into the Marklin package - and `CS2File` delegates, so every existing caller is untouched.  `saveChanges` applies it to the name it writes and to its staging path. |
| C4 | **Open.**  Confirmed by reading.  The fix is in `parseFile`\'s value splitting, which every CS2 and CS3 file in the application goes through, and the failing input is a locomotive name containing a comma followed by a space.  Narrow enough to be worth a test written first, and that test wants a real file to be written against. |

**Tests.**  A1, B3, B4, C1, C2 and C3 are all changes to failure paths that need a real file, a real
window or a real Error to exercise, and none has an automated test.  That is a gap and is recorded as
one rather than papered over; the hands-on tests below are what stands in for them today.

**At the layout, for these:**

**38. Make `UIState.data` unreadable** - copy any other file over it - then start TrainControl, close it,
and look in `tc_backup`.  There should be a copy named `unreadable<timestamp>UIState.data`, and the log
should say where it went rather than "no data file found".

**39. Rename a track diagram page to something with a slash in it**, "Up/Down".  Close TrainControl and
reopen it: the page must still be there.

**40. Put a page in `gleisbild.cs2` that the folder does not hold**, then open the layout.  Every other
page has to load, the missing one has to be named in the log, and the Layouts menu must still be
pointing at your folder afterwards.

---

## Counts

| | A | B | C | D | Total |
|---|---|---|---|---|---|
| Open | 1 | 4 | 4 | - | 9 |
| Not defects / clean | - | - | - | 9 | 9 |
| **Total** | **1** | **4** | **4** | **9** | **18** |

Of the nine open findings, four (`IP-B1`, `IP-C2`, `IP-C3`, `IP-C4`) are cases where a rule was written
down in one place and not applied in its twin, and in three of those the twin's own comment states the
rule being broken. `IP-A1` is the same shape at a larger scale. That is the pattern of this pass, and it
is the same pattern the July cycle's most-repeated mistake describes - which suggests the highest-yield
thing a future pass can do here is take each defended invariant in a comment and grep for every place it
should hold.
