# Independent acceptance: the parts nobody was looking at

**Status:** open

**Prefix for citing these findings elsewhere:** `AC2`

**Reviewed:** branch `autonomy-diagram-r0`, on 2026-09-04, at `83fb8456` (two commits past
`v3_0_0_rc13`).  The working tree was live while this ran: Adam was operating the railway
(`cs2_sample_layout/config/autonomy/*` modified in the tree throughout) and the axis-numbering fix
was being edited in `LayoutGrid.java`, `LayoutEditor.java` and `AutonomyEditorPanel.java` - so those
three files were deliberately NOT reviewed, and nothing here cites them.  `cs2_sample_layout/` was
read and never written.  One test class was run, through `one.sh`.

**Method, stated up front because it is the point of this document:** the four rounds that closed
today all chased the previous round's findings, and the well-trodden ground (the path-locking
failure handler, Return Home, the destination split, the legacy import, the axis numbers) is dense
with disposition comments.  So this pass chose its ground the other way: it ranked source files by
review-citation density and read the big ones with none - `MarklinLocomotive`, `Locomotive`,
`LayoutDiagramComponent`, `CS2Message`, `RemoteDeviceCollection` - plus the whole of
`MarklinControlStation`, `CS2File`, `MarklinRoute`, `Util`, `LayoutDiagram`, the UIState save and
restore, the timetable executor, and `LocomotiveFunctionAssign`.  Existing reviews were consulted
only to avoid re-filing; no finding below was chosen by following one.

---

## Verdict

**Not quite - one A-grade defect stands between rc13 and somebody who is not Adam, and it is
narrow.** AC2-A1: any edit to a route - including simply toggling its enabled checkbox in the
routes tab - silently removes that route from the autonomy configuration's "Activate routes"
selection, because `editRoute` and the sync-side route refresh both go through `deleteRoute`, which
strips the id from `activateRouteIDs`, and neither puts it back the way `changeRouteId` does.  The
exit-time capture then persists the mutilated list into the configuration, and at the next launch
`applyAutonomyRouteActivations` disables the route the user had switched on - an s88 automation
that quietly stops firing, with nothing but a log line, some sessions after the edit that caused
it.  That is exactly the "setting silently discarded" class this review was asked to rank third,
and it lands on precisely the users a release is for: anyone using the activate-routes feature who
ever edits a route.  Everything else found is C-grade or clean: the CS2 round-trip fidelity work
stops one file short of the index (AC2-C1), one unswept per-record guard in the accessory parser
(AC2-C2), and two small API-edge items (AC2-C3, AC2-C4).  The core command path - CAN encoding,
the locomotive fan-out, the sensor waits and their debounce, the two state files' load-failure
protection, the page writer's preservation discipline - held up under hostile reading (AC2-D1),
and `core.testRoutes` runs 22/22 with zero skips.  Fix A1 (the repair is the four lines
`changeRouteId` already contains, applied at two call sites), and this reviewer has no remaining
objection to shipping.

---

## Summary

| # | Severity | One line | Where |
|---|---|---|---|
| A1 | A | Editing, renaming, or enable/disabling a route drops it from the autonomy "Activate routes" selection; the exit capture persists the loss and the next launch disables the route | `MarklinControlStation.java:1762,3184`, `AutonomySession.java:3346`, `TrainControlUI.java:18587` |
| C1 | C | The index writer and the page parser silently delete what a genuine Central Station export carries that TrainControl does not model: `zuletztBenutzt`, top-level `page=N`, and the capitalised `Version` block | `LayoutDiagram.java:1122-1128`, `CS2File.java:528-552` |
| C2 | C | One malformed record in the station's `magnetartikel.cs2` aborts the entire sync - `parseMags` never received the per-record guard all three sibling parsers were given | `CS2File.java:697-731,573` |
| C3 | C | `exportLocsToCSV` dereferences the view without a null check, so the programmatic API (headless init) NPEs on it | `MarklinControlStation.java:3548` |
| C4 | C | `Util.getLatestReleaseInfo` leaks its reader and HTTP connection when the response cannot be read | `Util.java:655-664` |
| D1 | D | What was read and found sound, and the areas this pass did not reach | - |

---

## AC2-A1 - A route edit silently discards the autonomy route-activation selection, permanently

| | |
|---|---|
| **Severity** | A - a user setting is silently destroyed in the stored configuration, and the visible consequence (an s88 route that no longer fires) surfaces sessions later, disconnected from its cause |
| **Disposition** | open |
| **Confidence** | Every layer traced by reading, each quoted below; the globals round trip confirmed through all four hops (toJSON -> capture -> builder -> fromJSON).  NOT verified by execution: no new test could be compiled in this environment, and none of the 22 existing `core.testRoutes` tests pins this (they pin the adjacent immutable-list defect only).  The one link I did not step through is the UI list repaint after an edit; I claim only what the model does. |

### The defect

`deleteRoute` removes the deleted route's id from the running autonomy layout's activation list
(`MarklinControlStation.java:3184-3187`):

```java
if (this.getAutoLayout().getActivateRouteIDs().contains((Integer) r.getId()))
{
    this.getAutoLayout().getActivateRouteIDs().remove((Integer) r.getId());
}
```

That is right for a route that is genuinely going away.  But two flows delete a route **and
immediately re-create it under the same id**, and neither restores the id:

1. **`editRoute`** (`MarklinControlStation.java:1757-1768`) - the only door every route edit goes
   through: the route editor's Save (`RouteEditorFrame.java:2572`), a rename, and - via
   `writeRouteEnabledState` (`TrainControlUI.java:18585-18589`) - the routes tab's enable/disable
   toggle and the bulk enable/disable:

   ```java
   Integer id = existing.getId();
   // Disable the route so that the s88 condition stops firing
   existing.disable();
   this.deleteRoute(name);
   if (!this.newRoute(trimmedNewName, id, route, s88, s88Trigger, routeEnabled, conditions))
   ```

2. **`syncWithCS2`** (`MarklinControlStation.java:1268-1298`) - a route that changed on the
   Central Station is deleted and re-added with the same id ("route.deletingDuplicateId" then
   `newRoute(r)`), so editing a listed route on the station itself takes the same path.

The proof that this is an omission and not a policy is the third sibling: **`changeRouteId`**
performs exactly this repair (`MarklinControlStation.java:3231-3239`):

```java
if (this.getAutoLayout().getActivateRouteIDs().contains(oldId))
{
    this.getAutoLayout().getActivateRouteIDs().remove(oldId);
    this.getAutoLayout().getActivateRouteIDs().add(newId);
}
```

This is the July cycle's most-repeated defect shape, named in this folder's own README: "when you
fix a call site, grep for its twins before closing the finding."

### Why the loss is permanent, not just for the session

`Layout.toJSON` writes the list unconditionally (`Layout.java:7245-7246`):

```java
jsonObj.put("activateRoutes", this.isActivateRoutes());
jsonObj.put("activateRouteIDs", new JSONArray(this.activateRouteIDs));
```

On a clean exit, `TrainControlUI.saveState` lifts the running layout back into the active diagram
configuration (`TrainControlUI.java:2292-2318`, "the save on the way out"), and
`captureFromLayout` replaces the configuration's globals wholesale with what `toJSON` said
(`AutonomySession.java:3346-3353`):

```java
for (String key : root.keySet())
{
    if (!"points".equals(key) && !"edges".equals(key)) globals.put(key, root.get(key));
}
configuration.put("globals", globals);
```

The legacy path persists it too: with no diagram configuration and autosave on, `toJSON` is
written to `autonomy.json` (`TrainControlUI.java:2350-2360`).

The stored configuration round-trips through the builder - `AutonomySession.globals()` copies
every stored key (`AutonomySession.java:3451-3454`), `AutonomyBuilder.build()` emits them at the
JSON root (`AutonomyBuilder.java:777-780`), and `Layout.fromJSON` reads `activateRouteIDs` back
(`Layout.java:8304-8328`).  So the mutilated list becomes the configuration's truth.

### What the user then sees

At the next launch (or the next checkbox touch), `applyAutonomyRouteActivations`
(`MarklinControlStation.java:866-897`) walks the live route database: a route whose id is not in
the list is **disabled** - `r.disable()`, one log line.  The import code one package over states
the stakes in its own words (`AutonomySession.java:865-873`): "every route whose id is not in
activateRouteIDs is disabled, and every route that is gets enabled and executed - accessories
thrown on the real railway."  A route the user had firing on an s88 sensor - which on this railway
can be a protection route - stops firing, with no dialog, no dirty marker, and a cause several
sessions in the past.

### Why four rounds missed it

The loss is timing-dependent.  Any `parseAuto` rebuild between the route edit and the exit (a
diagram edit applied, a locomotive placed, a configuration loaded) rebuilds the live Layout from
the still-intact stored globals, restoring the id and hiding the defect for that session.  Only
the plain sequence - edit a route, keep running, exit - persists the loss.  A reviewer exercising
the route editor *and* the diagram in one session would never see it.

### Reproduction (for MT entry, no code needed)

1. Autonomy loaded; "Activate routes" on; route R selected in the list; note R enabled.
2. Routes tab: toggle R disabled, then enabled again (or open R in the editor and Save unchanged).
3. Exit normally.  Relaunch.
4. R is deselected in the activation list, and the log shows `route.autolayoutDisabledRoute` for
   R at config apply.  Its s88 trigger no longer fires.

### The fix shape (not applied - this review edits nothing)

In `editRoute`, capture `getActivateRouteIDs().contains(id)` before `deleteRoute` and re-add the
id after `newRoute` succeeds; same two lines at the `syncWithCS2` delete-then-readd.  That is the
`changeRouteId` repair applied to its two twins.  A test can pin it by asserting membership
survives `editRoute` - and per this project's rule, it should be seen red first against the
current tree.

---

## AC2-C1 - The round-trip preserves everything except what the INDEX and top-level bare keys carry

| | |
|---|---|
| **Severity** | C - silent modification of user files, with no functional consequence demonstrated |
| **Disposition** | open |
| **Confidence** | Verified against the shipped `sample_layout` (a genuine CS2-format export) and against Adam's own `cs2_sample_layout` (read-only).  What the Central Station itself does with the rewritten files was NOT tested - no CS2 was harmed or consulted. |

The page exporter went to great lengths to preserve what TrainControl does not model - unmodelled
element keys, unmodelled blocks, the file's own type words - under the principle written at
`LayoutDiagramComponent.java:741`: "What TrainControl does not understand it is not entitled to
throw away."  Three things still fall through, all present in the genuine CS2 export this
repository ships (`sample_layout/config/`):

1. **`zuletztBenutzt` in the index.** `sample_layout/config/gleisbild.cs2` carries
   `zuletztBenutzt\n .name=Page 1` (the CS2's last-used-page memory).  `writeLayoutIndex`
   regenerates the index from a hardcoded header (`LayoutDiagram.java:1122-1128`: `[gleisbild]`,
   `version`, `.major=1`, `groesse`) plus `seite` entries - unlike `exportToCS2TextFormat`, which
   was explicitly fixed to preserve unmodelled blocks (`LayoutDiagram.java:287-310`).  The first
   page add, rename, delete or duplicate deletes the block.
2. **Top-level bare keys in page files.** `sample_layout/config/gleisbilder/Page 1.cs2` opens
   with `page=1`.  `parseFileContents` (`CS2File.java:528-552`) recognises only `^[a-z]+$` block
   names and ` .key=value` lines; a bare `page=1` matches neither, enters neither the model nor
   the unmodelled store, and is deleted by the first save - which "naming a station" triggers
   unasked.
3. **`Version` with a capital V** (as the sample index spells it) fails `^[a-z]+$` too, so the
   block is dropped and the exporter's lowercase fallback replaces it.

Adam's own files are already TrainControl-normalised (lowercase `version`, no `zuletztBenutzt`,
no `page=`), which is why his railway never shows this.  A new user who downloads their CS2
layout (`downloadCS2Layout` copies the station's bytes verbatim) and then names one station gets
files the station never wrote.  Whether a CS2 re-reading them cares is unknown; the cost if it
does is a diagram that will not load back onto the station.  Worth one manual test by somebody
with a real CS2 before GA - not worth blocking rc13.

Also noted while tracing: `readLayoutIndexIds` (`LayoutDiagram.java:890-908`) treats
`zuletztBenutzt`'s ` .name=` line as a page at position 0; benign today only because the same name
always reappears as a real `seite` and the map overwrites, but it is the kind of luck that
expires.

---

## AC2-C2 - One malformed accessory record aborts the entire Central Station sync

| | |
|---|---|
| **Severity** | C - structurally real; no genuine station file demonstrated to trigger it |
| **Disposition** | open |
| **Confidence** | Code path read; the "does this happen" half checked only against the two `magnetartikel.cs2` files in this repository, both clean. |

`parseMags` (`CS2File.java:697-731`) guards missing `id`/`typ` keys but calls
`Integer.parseInt(m.get("id"))` unguarded.  Its three sibling parsers were each given per-record
guards with comments explaining the blast radius - `parseRoutes` (`CS2File.java:915-921`:
"Letting this propagate would reach syncWithCS2's outer catch and abandon the entire import"),
`parseLocomotives` (`CS2File.java:1838-1848`), `parseLocomotivesCS3` (`CS2File.java:1684-1694`).
`parseMags` was not swept.  The exposed path is `parseRoutes()` -> `getMagList(false)`
(`CS2File.java:571-576`), which fetches from the station itself; a non-numeric `id` there
propagates to `syncWithCS2`'s outer catch, the whole sync returns -1, and at startup that reads as
"not connected" (`MarklinControlStation.java:380`).  The local-folder door is already covered by
`syncLayouts`' catch.  A CS2 writes numeric ids, so per this folder's own discipline
("distinguish could happen from does happen") this is a guard worth adding, not a changelog entry.

---

## AC2-C3 - `exportLocsToCSV` NPEs when no UI is attached

| | |
|---|---|
| **Severity** | C - unreachable from the shipped UI; reachable through the programmatic API |
| **Disposition** | open |
| **Confidence** | Caller census done (the only in-tree caller is the UI menu at `TrainControlUI.java:23403`); not executed. |

`MarklinControlStation.exportLocsToCSV` (`MarklinControlStation.java:3548`) calls
`this.view.getAllLocButtonMappings(l)` with no null check, and `view` is null whenever the model
was built with `showUI` false (`MarklinControlStation.java:3865`) - which is exactly how the
documented programmatic API (`AutomationAPI.md`, the `examples` package) initialises.  Every
neighbouring method in the class guards `this.view != null`.  An API user asking for the CSV gets
a bare NPE instead of a CSV without button-mapping data.

---

## AC2-C4 - Release-check helper leaks its connection on a bad response

| | |
|---|---|
| **Severity** | C - one leaked handle per failed update check |
| **Disposition** | open |
| **Confidence** | Read only. |

`Util.getLatestReleaseInfo` (`Util.java:655-664`) reads its `BufferedReader` with a plain
`in.close()` at the end - not try-with-resources - so an `IOException` mid-read, or a
`JSONException` from a garbage response, leaks the reader and its HTTPS connection.  Every other
fetch in the project was converted (`CS2File.parseFile`, `parseJSONArray`, `ping`, `isCS3` all
carry comments about exactly this).  One call per session at most; cosmetic.

---

## AC2-D1 - What was checked and found sound, and what was not reached

| | |
|---|---|
| **Severity** | D |
| **Disposition** | record of coverage; nothing to fix |
| **Confidence** | Reading throughout, except where a run is named. |

Checked and found sound (each read in full, adversarially, in this pass):

- **`CS2Message`** - bit packing both directions, sign-extension masks, the `getSubCommand` short-frame
  guard, hash normalisation, the dedup `equals`.  No defect found.
- **`RemoteDeviceCollection`** - the two-map invariant under `add` (both stranding cases handled),
  leaf locking as claimed.  No defect found.
- **`MarklinLocomotive`** - consist fan-out ordering and clamping, the identity hash contract,
  `setLinkedLocomotives`' staged swap, `addressFromUID` base ordering, `setAddress`'s
  arrival/departure clamp.  No defect found.
- **`Locomotive` (base)** - the sensor waits: interrupt-flag deferral, the debounce restart carrying
  the advisory origin, the advisory's non-blocking hand-off; runtime accounting under `speedMonitor`
  including the power-state interplay.  No defect found.
- **`MarklinControlStation`** - constructor restore order, `syncWithCS2`'s reconciliation (rename
  ordering, duplicate refusals, deferral while running), `saveState`/`restoreState`'s
  unreadable-file-kept-aside protection (and its UIState twin at `TrainControlUI.java:2200-2225`),
  `receiveMessage`'s executor branches, ping retry/outage clocks, `deleteLoc`'s consist and route
  repairs.  The one defect found became AC2-A1.
- **`MarklinRoute`** - monitor thread lifecycle (the disable/enable double-monitor guard), the
  per-command conflict re-ask, emergency-stop precedence, the recursion limit and `auto`
  propagation into chained routes.  No new defect; the area is dense with recent dispositions and
  they match the code.
- **`CS2File`** - both route importers' pause placement, the sparse-function collection, the
  multi-unit name splitting, per-page and per-record failure containment, `copyAtomically`, the
  CS3 backup additions.  Nothing new beyond AC2-C1/C2.
- **`Util`** - `writeAtomically`, the backup walkers' loop guards, `sanitizeFilename`,
  `isLocIconFile`'s canonical-parent check.  Nothing beyond AC2-C4.
- **`LayoutDiagram`** - page-id identity rules (floor, reissue, keepAbsent), the
  unreadable-index refusal and its charset fallback, `saveChanges`' case-insensitive rename
  handling and first-touch `.bak`.  Nothing beyond AC2-C1.
- **`LayoutDiagramComponent`** - export round-trip of typ words and semaphore rotation inversion,
  unmodelled key preservation, the copy constructor carrying both.  No defect found.
- **Timetable executor** (`Layout.executeTimetableInternal`) - dispatch gating, both stuck bounds,
  the Throwable containment, the completion wait's fence condition.  No defect found.
- **`LocomotiveFunctionAssign`** - icon/trigger state tracking across function switches, the
  reset confirmation default.  No defect found.
- **Run:** `core.testRoutes` via `one.sh`: 22 run, 0 failures, 0 skips.

Not reached, stated so the next pass can choose differently: `LayoutGrid`, `LayoutEditor`,
`AutonomyEditorPanel` (excluded - being edited during this review), the bulk of `AutonomySession`
and `AutonomyCompanionStore` (only the capture and globals paths were traced), `HomeStaging`,
`TileGraph`/`GraphReducer`/`TilePorts`, `AutoLocomotiveStatus`, the keyboard tab and s88 monitor
UI, `LocIconCropDialog`, `DiagramExport`, multi-window behaviour (`PositionAwareJFrame`,
`LayoutPopupUI`), `NetworkProxy`/`CSDetect`, and the 26,971 lines of `TrainControlUI` outside the
save/restore, capture, and route-toggle regions cited above.
