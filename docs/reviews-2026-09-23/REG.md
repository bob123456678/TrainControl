# Regression review: what a v2.8.1 user loses on upgrading to this build - third pass

**Status:** open

**Prefix:** `REG`

**Reviewed:** branch `autonomy-diagram-r0` at `281c79de`, 2026-09-23.  Baseline: branch `master` at `5f0a75e3` (`RAW_VERSION = "2.8.1"`), compared with two-dot diffs (`git diff master HEAD`, `git show master:<path>`) throughout.  Four earlier rounds used this baseline - `R28` and `RG3` at `cf048f9b`, `RG4` at `2cef4211`, `RG5` at `357cdc40` - and 540 commits have landed since the last of them; this pass concentrates on those.

**Method:** read-only throughout - `git diff master HEAD`, `git show master:`, `git log -S`, `grep`, and short read-only `python` over git objects and over the frozen data in `test/operator_layout/` (never `cs2_sample_layout/`).  Nothing was compiled, run or written except this file.  The earlier regression rounds against this baseline (`R28`, `RG3` at `cf048f9b`; `RG4`, `RG5` at `2cef4211` / `357cdc40`) were read from `4020a899^` and their closed findings were not re-raised; the ones marked fixed were re-checked (`REG-D7`).  Sweeps re-run at both revisions and subtracted: message-bundle keys (at three revisions), preference reads and their defaults, `KeyEvent`/accelerator/`KeyStroke` sets, main-form components, the I18n keys each 2.8.1 menu file used, serialised fields/UIDs/enums, route and autonomy JSON keys, `Layout` setting defaults, library class-file versions.  Then read in depth: the legacy import (`AutonomySession.importLegacy`, `whatALegacyImportLeaves`), `isPathClear`'s tier rules, the start-up auto-load branch and the JSON fallback, route import/export and `applyAutonomyRouteActivations`, the atomic-routes override, `MarklinControlStation`'s code-only diff (sync, packet handling, restore), `MarklinRoute`'s execution diff, `NetworkProxy`/`CSDetect`, `CS2File`'s diff since `RG5`, and the v3.0.0 changelog line by line against the code it describes.  Adam's 2.8.1-era `autonomy.json` was measured for B1.  Every finding here rests on reading; each says what execution would settle.

| | |
|---|---|
| **A** | none |
| **B1** | a switched-off station can no longer be sent to by hand; the v2.8.0 changelog line promising it is still shipped, the v3.0.0 one is silent, and the import carries `active: false` onto 18 of Adam's stations |
| **B2** | the auto-load default flip reaches the JSON fallback: a Blank/Sample chooser at every start for users with no autonomy, and a 2.8.1 file's route switch-offs applied at start-up |
| **B3** | Import Routes switches every imported route off, with no notice and no changelog line |
| **C1** | Atomic Routes forced on after every load while anything is unmeasured - which after an import is everything - and the changelog does not say so |
| **C2** | the Routes changelog block puts the play button on the wrong window and says Find Route runs a route |
| **C3** | autonomy on a diagram read from the Central Station is gone (`RGN-A2`'s deliberate half), and `Automation.md`'s prerequisite still reads as if it works |
| **D** | ten checks that came back clean, REG-D1 to REG-D10 |

## A - high

None.  Stated rather than omitted.  Two candidates were weighed and placed at B: the route switch-offs in `REG-B2` (wrong behaviour on the layout, persisted - but only for a user whose 2.8.1 file carries `activateRoutes: true`, and only what 2.8.1 itself did whenever that user loaded the graph), and `REG-B3` (every s88 route off after a restore - but visible in the route list, deliberate, and pinned by tests).  For the individual user either is closer to an A; by population they are B.

## B - medium

### REG-B1 - a 2.8.1 user's switched-off parking stations can no longer be reached by hand, and the only changelog line about it still promises they can

| | |
|---|---|
| **Disposition** | Partly fixed - the changelog says it (4fb36b4b) and the two comments are corrected (fd6341dd).  Open - Adam's decision: whether the legacy import should translate `active: false` on a station into "on, not an automatic destination" |
| **Where** | `Layout.java:2569-2598` (the rule and its comment), `Readme.md:484` (the promise), `AutonomySession.java:599-622` (the import), `Layout.java:2536-2542` (a comment still stating the 2.8.1 rule) |

**What changed.**  At 2.8.1 an inactive point refused a *destination* only while autonomy was running:

    git show master:src/org/traincontrol/automation/Layout.java:1449-1458
        if (!path.get(path.size() - 1).getEnd().isActive() && this.isAutoRunning())
        { ... I18n.f("autolayout.errorInactiveStationInAutoRun", ...) ... return false; }

and the comment above the intermediate rule said why the endpoints were fenced: a manual route "may still FINISH on one, which is how a route to a parked-up berth is picked".  The v2.8.0 changelog, which a 2.8.1 user has read, made it a promise:

    Readme.md:484   (v2.8.0 section, still shipped at HEAD)
        ... you can still send a train to a switched-off station, and still drive one away from it,
        so a deactivated parking track stays reachable by hand

At HEAD the destination rule is unfenced, on Adam's ruling of 2026-09-06 ("Inactive really means nothing can pass", `behaviour.md` section 2):

    src/org/traincontrol/automation/Layout.java:2589-2598
        if (!path.get(path.size() - 1).getEnd().isActive())
        { ... I18n.f("autolayout.errorInactiveStation", ...) ... return false; }

`isPathClear` is the tier every door passes through, so every hand-driven send refuses it.  **It reaches users who have not imported anything, too**: `Layout` is the same class whether it was built from a diagram configuration or from `autonomy.json` by the JSON path (which a Central Station layout, or a local one with no configuration yet, still uses - see `REG-B2`), so a 2.8.1 user still on their old file meets the new refusal on the same click that worked yesterday.

**Why this is a regression finding and not a re-argument of the ruling.**  The ruling stands; the rule is Adam's.  What is wrong is what an upgrading user is told:

1. **The v3.0.0 changelog says nothing.**  `grep -n "inactive\|switched off\|switched-off" Readme.md` finds no v3.0.0 line about the destination rule (lines 372, 375, 384 and 418 are about other things).
2. **The v2.8.0 line above is still in the Readme and is now false** - it describes the exact behaviour this release removes.  Same class as `RG3-B1` (a changelog sentence telling the user a capability is still there), which was a B.
3. **The flag's meaning changed under data the import carries verbatim.**  `active` is in `CARRIED_SETTINGS` (`AutonomySession.java:621-622`) and copied as given.  Measured on Adam's own 2.8.1-era file, `test/operator_layout/config/autonomy_legacy/autonomy.json`: **24 points are `active: false`, 18 of them stations** - `ParkingTrack4` to `ParkingTrack12`, `TopMainR0Park`, `TopR1ParkLong`, `TopR1ParkShort`, `TunnelCenterPark`, `TunnelLeftPark`, `TunnelLongPark`, `TunnelRightPark`, `LowerFront`, `LowerUp`.  Sixteen of those are reversing stations, which the import already marks `parking` (not an automatic destination, `AutonomySession.java:876-879`, the `setPointFlag(tile, AutonomyBuilder.PARKING, true)` at `:878`) - so for them `active: false` was the 2.8.1 way of saying "autonomy stays out, I drive in by hand", and after the import it says "nobody drives in".  The import dialog (`whatALegacyImportLeaves`) counts what was left behind and says nothing about this.
4. **The import's own javadoc argues the opposite**: `AutonomySession.java:612-618` says carrying `active` unfiltered "is a RESTORATION rather than a regression: at v2.8.1 ... those points blocked paths then too".  True of intermediates, which 2.8.1 already refused; false of the eighteen destinations, which 2.8.1 allowed by hand.  It predates the 2026-09-06 ruling and was not revisited by it.
5. **`Layout.java:2536-2542` still states the 2.8.1 rule** ("may still FINISH on one, which is how a route to a parked-up berth is picked") thirty lines above the code that now refuses exactly that.

Adam's current configuration (`test/operator_layout/config/autonomy/configuration-Main.json`) has one inactive square, so he has evidently re-authored past this; a user importing for the first time has not.

**Is there a way past?**  Yes - switching the station back on (`autosetup.ui.testSquareIsOutOfService` names where).  For the sixteen reversing stations that is safe, because `parking` already keeps autonomy out; for `LowerFront` and `LowerUp` it also makes them automatic destinations unless the user unticks that too.  So the remedy exists but is not the obvious one, and nothing points at it on upgrade.

**Verification request.**  Reading settles the rule; the import's effect needs one run.  Model on `test/core/testALegacyImportMatchesTheFileItCameFrom.java` (it already loads the frozen legacy file at `:90`): import `test/operator_layout/config/autonomy_legacy/autonomy.json` onto `test/operator_layout/`, build the layout, place a locomotive on a square that reaches `ParkingTrack4`'s tile, and ask `Layout.isPathClear` (with `isAutoRunning()` false) for a path ending there.  **Proves:** refused with `autolayout.errorInactiveStation`.  **Refutes:** allowed - in which case something between the import and the build translates `active` and point 3 is withdrawn.  The changelog half needs no execution.

**Suggested fix.**  One sentence in the v3.0.0 changelog saying a switched-off station can no longer be sent to by hand (and how to get the old effect: leave it on, untick "automatic destination"); correct the two comments.  Whether the import should translate `active: false` on a station into "on, not an automatic destination" rather than carry it is Adam's decision - it is the choice between preserving the flag and preserving what the flag did.

### REG-B2 - the auto-load default now reaches the old JSON path on every start, which `REL-B3`'s closure did not consider: a chooser dialog for users with no autonomy, and a 2.8.1 file's route switch-offs applied before anyone asked

| | |
|---|---|
| **Disposition** | Fixed - e2223851 (claims d64023cb, red first): the start-up JSON arm keeps 2.8.1's terms - a box somebody ticked, and a graph to load.  For Adam: an untouched box shows ticked and does not load an old JSON graph |
| **Where** | `TrainControlUI.java:1249` (the default), `:9662-9679` (the start-up branch), `:24288-24292` (the empty-JSON dialog), `MarklinControlStation.java:1059-1140` (`parseAuto` -> `applyAutonomyRouteActivations`) |

`AUTO_LOAD_AUTONOMY` went from `false` (`master:TrainControlUI.java:704`) to `true` (`:1249`).  `REL-B3` filed that and was closed by `9d16eeae` ("the auto-load default keeps its place, and says what it cost"); both it and the comment at `:1227-1247` reason about the **diagram configuration** path.  The start-up branch has a second arm, and a 2.8.1 user who never opened Startup Options (no stored key, so the new default applies) is exactly the user who lands in it:

    src/org/traincontrol/gui/TrainControlUI.java:9663-9678
        if (session != null && this.getAutonomyViewerPanel() != null
            && session.getStore().getActiveConfiguration() != null)
        {
            this.getAutonomyViewerPanel().loadActive();
        }
        else
        {
            this.validateButtonActionPerformed(new CustomActionEvent(...));
        }

`session` is null for every Central Station layout (`getAutonomySession`, `:2912-2921`), and `getActiveConfiguration()` is null for every local layout that has not yet been set up or imported - which is every upgrading user's first start.  `validateButtonActionPerformed` then does what 2.8.1 did only for users who had ticked the box:

1. **No `autonomy.json` (a user who never used autonomy - most users).**  `:9362` leaves the text area empty, and

        src/org/traincontrol/gui/TrainControlUI.java:24288-24292
            // Offer to load a blank graph if there is no JSON
            if (this.autonomyJSON.getText().trim().equals(""))
            {
                this.loadDefaultBlankGraphActionPerformed(null);
            }

   opens the modal "Blank graph / Sample graph / Cancel" chooser (`:25698-25712`, `autolayout.ui.confirmCreateNewGraphOverwritesExisting`) during start-up.  Cancel leaves the text empty, `parseAuto("")` builds nothing, and `:24302-24321` follows with `autolayout.ui.errorJsonValidationFailedCheckLog`.  Nothing is written for a cancelled start (`saveState` writes `autonomy.json` only when the text is non-empty, `:2630`), so **it happens again on every start** until the user picks Blank (which writes a blank `autonomy.json` on exit) or finds Startup Options and unticks the box - and a Central Station user cannot make the configuration that would take them down the other arm.  The comment defending the default says "A layout with no setup is unaffected" (`:1233`); by this reading it is the case most affected.  It is also a second window during start-up, which is what `OB-170` worked to avoid.

2. **A 2.8.1 `autonomy.json` with route activations.**  `parseAuto` ends in `applyAutonomyRouteActivations` (`MarklinControlStation.java:1099`, body `:1106-1140`), which disables every s88 route not in `activateRouteIDs` and enables and arms the ones that are.  `disable()` only clears a flag (`MarklinRoute.java:1213-1216`), and that flag is saved to `LocDB.data` on exit.  The legacy **import** refuses to carry these two keys precisely because of this (`AutonomySession.java:948-971`: on Adam's file, `activateRoutes: true` with an empty list "would disable every route he has") - but the start-up fallback parses the raw file and applies them anyway, **before** the user has reached the import.  On Adam's own legacy file that is every s88-triggered route switched off at first start, logged one line each (`route.autolayoutDisabledRoute`) and nowhere else.  At 2.8.1 this happened only when the user loaded the graph, which with the box unticked was a deliberate act.

**Why nothing compensates.**  No guard on the start-up arm asks whether there is any JSON, or whether this user ever loaded autonomy; `grep -rn "AutoLoadAutonomy\|AUTO_LOAD_AUTONOMY\|BlankGraph" test/` returns nothing, so no test starts the window with the key absent.  The changelog's only sentence on this is about configurations ("the one you were last using is loaded when TrainControl starts", `Readme.md:366`).

**Verification request (needs execution; a person can do it faster than a test).**  On a Windows profile that has never run TrainControl - or after exporting and then removing the `AutoLoadAutonomy<hash>` value from the `org/traincontrol/gui` preference node - with no `autonomy.json` in the working directory, start the jar against a Central Station layout (Layout -> switch to Central Station).  **Proves (half 1):** the "Blank graph / Sample graph" chooser appears during start-up, and Cancel is followed by the JSON-validation error; it recurs on the next start.  **Refutes:** the window opens with no dialog.  For half 2, as a test: model on `test/regression/testSwitchingToACentralStationLayout.java`'s `LayoutSandbox` + `new TrainControlUI()` pattern, put a copy of `test/operator_layout/config/autonomy_legacy/autonomy.json` in the text area, create two s88 routes enabled, and call `validateButtonActionPerformed` as the start-up arm does.  **Proves:** both routes read `isEnabled() == false` afterwards.  **Refutes:** they stay enabled.

**Suggested fix.**  The start-up arm should not fall back to the JSON path when there is no JSON, and arguably should not fall back at all for a user who never ticked the box at 2.8.1 - which the code can tell, because 2.8.1 never stored the key for such a user.  Whether the fallback should apply route activations on an unattended start is Adam's call; the import's answer ("must not switch the railway's routes off") suggests not.

### REG-B3 - Import Routes now switches every imported route off, and nothing says so - not the import, not the changelog

| | |
|---|---|
| **Disposition** | Fixed - e2223851 (claim d64023cb, red first): the import logs and shows how many routes arrived and that their firing is off; changelog 4fb36b4b |
| **Where** | `MarklinControlStation.java:4083-4151` (`parseRoutesFromJson`), `:4159-4186` (`importRoutes`), `TrainControlUI.java:25966-26033` (the menu door) |

At 2.8.1 a route file restored each route's automatic firing as it was saved: `MarklinRoute.fromJSON` reads `auto` (`git show master:src/org/traincontrol/marklin/MarklinRoute.java:779`, `boolean enabled = jsonObject.getBoolean("auto")`) and the constructor arms the s88 monitor.  The v2.0.0 changelog introduced the pair of buttons as "useful for backups" (`Readme.md:1225`), and that is what 2.8.1 users use them for.

At HEAD every route is disarmed on the line after it is built, whatever the file says - Adam's ruling of 2026-09-10 (`S14-A1`, "they should not be armed.  The user can choose to do this when they are ready"):

    src/org/traincontrol/marklin/MarklinControlStation.java:4144-4148
        MarklinRoute route = MarklinRoute.fromJSON(dataArray.getJSONObject(i), this);
        route.disable();
        routes.add(route);

and `importRoutes`' own comment says so: "A file's `auto` flag is therefore read and then overridden".  The disabled flag is what `LocDB.data` saves on exit, so it persists.

**The rule is Adam's and is pinned** (`test/core/testRoutes.java` `testARouteReadFromAFileArrivesDisabled`, `testAnExportedRouteComesBackDisarmed`).  What is wrong is that the user is never told:

- **No notice at the door.**  The menu handler's finishing half (`TrainControlUI.java:26011-26032`) shows a dialog only on failure; on success it refreshes the list and stops.  The log says `route.deletingExisting` and one `route.adding` per route; no key in `messages.properties` mentions disarming (`grep -in "disarm\|not armed" messages.properties` is empty).
- **No changelog line.**  The v3.0.0 section's Routes block (`Readme.md:385-392`) says nothing about imports.

**Failure scenario.**  A 2.8.1 user moves to a new computer or recovers from a lost `LocDB.data` with Routes -> Import.  Every s88-triggered route - block signals, a trap point, a power-cut safety route - arrives switched off.  The first sign is a train running through a sensor that used to set a road or cut the power.  The remedy exists (turn each back on, or the bulk enable), but only for somebody who knows to look.

**Verification request.**  None needed for the behaviour (the two tests above pin it); the silence is read off the handler and the bundle.  If the coordinator wants it seen: import `test/operator_layout`'s routes export (or any file with `"auto": true`) through the menu and look for any dialog or log line mentioning that the routes are off.

**Suggested fix.**  One line in the success path - "N routes imported; their automatic firing is off until you turn it on" - and one changelog sentence.  Both are about telling, not about the rule.

## C - low

### REG-C1 - a 2.8.1 railway that ran with Atomic Routes off now runs with them on after every load, and the changelog does not say so

| | |
|---|---|
| **Disposition** | Fixed - 4fb36b4b: a changelog sentence |
| **Where** | `TrainControlUI.java:6127-6166` (`keepAtomicRoutesOnWhileTheRailwayCouldReleaseTrack`), its call sites `:23100`, `:24297`, `:24618`, `:25561`, `AutonomyViewerPanel.java:822`, `AutoLocomotiveStatus.java:1159`, `LayoutRightclickAutonomyMenu.java:1376`; `behaviour.md` 5d |

Adam's ruling (2026-09-21, "just force the checkbox checked as well"; `behaviour.md` 5d) is that the railway may not run non-atomic while any track autonomy runs over is unmeasured or any train has no length.  Every load and every dispatch door forces `atomicRoutes` back to true and writes `autolayout.warnAtomicRoutesKeptOn` / `...KeptOnTrains` to the log.

For an upgrading user this is not an edge case.  A train length of 0 is what `Locomotive.trainLength` holds until somebody sets it, and **the legacy import does not bring edge lengths across** (reported by `whatALegacyImportLeaves`, `autosetup.ui.leftEdgeLengths`) - so every 2.8.1 railway that ran with `"atomicRoutes": false` runs atomic after the import until every piece of track has been measured again square by square.  2.8.1 allowed the setting with a documented caveat ("length values should be set for all edges and trains", `Readme.md:1338`) and did not enforce it.

Why only a C: the checkbox refuses with a message that says exactly what to measure (`autolayout.errorNonAtomicNeedsLengths`, `...NeedsTrainLengths`), so the user who looks is told, and the consequence is fewer trains moving at once rather than anything unsafe.  What is missing is one changelog sentence; the v3.0.0 section's only mention of atomic routes (`Readme.md:414`) is the tail-release bug fix, which is the reason for the rule but does not state it.  Reading only; no execution needed.

### REG-C2 - two sentences in the changelog's Routes block describe controls that are not what or where they say

| | |
|---|---|
| **Disposition** | Fixed - 4fb36b4b |
| **Where** | `Readme.md:390`, `:391`; `TrainControlUI.java:23563-23627` (`RouteListMouseClicked`), `:29674-29845` (`mountFindRoute`, `findRoute` at `:29703`, `showRouteInTheList` at `:29821`) |

Both were written in the changelog rewrite `a3128cb7`, and both are the sentences a 2.8.1 user reads to learn how clicking a route changed - which matters, because one of the two changes removes a confirmation they are used to.

    Readme.md:390
        - Every route tile on the track diagram now has a play button that runs the route straight away,
          while a click elsewhere on the tile still asks first and a right-click still opens the menu.

The play button is on the cells of the **Routes tab** list (`RouteListMouseClicked`, `isOverTheRoutePlayButton`, the renderer at `:29889`), not on the track diagram: `grep -n -i "play" LayoutLabel.java LayoutGrid.java` finds nothing about one.  At 2.8.1 every left click on a route cell asked first (`git show master:src/org/traincontrol/gui/TrainControlUI.java:12442-12476`); at HEAD a click on the right-hand part of the same cell runs the route with no question.  That is the intended feature (FR-043, MT-217) - but the sentence that should warn a 2.8.1 user sends them to the wrong window to find it.

    Readme.md:391
        - Find Route on the Routes menu takes a name or part of one: an exact name runs outright, and
          a fragment matching several offers the choice.

`findRoute` ends in `showRouteInTheList(found)`, which selects the Routes tab, scrolls to the cell and washes it yellow - it never executes anything.  That is what `MT-218` (FR-044) asked for ("jumps to the route page and highlights the matched cell") and what Adam validated.  A user reading "runs outright" will expect a menu item that throws switches.

Reading only.  **Fix:** "Every route in the Routes tab now has a play button ..." and "an exact name goes straight to it".

### REG-C3 - autonomy on a diagram read from the Central Station is gone, and the user guide's prerequisite still reads as if it works

| | |
|---|---|
| **Disposition** | Fixed - 4fb36b4b (Automation.md's prerequisite) and e2223851 (claim d64023cb, red first: the right-click Start is not offered over a Central Station layout) |
| **Where** | `TrainControlUI.java:4276-4324` (`refreshAutonomyTabState`), `Automation.md:28`, `Readme.md:128` |

At 2.8.1 the autonomy graph was independent of where the track diagram came from, so a user whose diagram was read straight from the Central Station (the default for a CS3 Track Board, `Readme.md:89`) could run autonomy from `autonomy.json`.  At HEAD the Auto tab is greyed unless the layout is local: `setAutoTabEnabled(valid && loaded && isLocalLayout())` (`:4323`), deliberately, since `OB-104`.  `RGN-A2` recorded this half as "deliberate ... still a 2.7.4c capability that is gone, and it has no changelog line" and was closed on the other half.

Since then the changelog gained the Download item ("The autonomy menu's 'Autonomy needs a layout on this computer' is now something you can press: it downloads one", `Readme.md:382`), which is a way past and names the requirement in passing.  What still misleads is the user guide:

    Automation.md:28
        **A track diagram.** Either downloaded from your Central Station or drawn in TrainControl's
        own editor.

"Downloaded from your Central Station" is also how a CS-read diagram arrives, and `Readme.md:128` still offers "a JSON configuration file" as a way to set autonomy up, which on a Central Station layout loads (the JSON window is put back, `:3945-3968`) and then cannot be run: the Start handler (`startAutonomyActionPerformed`, `:25526`, which the diagram menu's `requestStartAutonomy` also reaches) calls `refuseAutonomyStartWhileBroken`, whose first test is `isRemoteLayout()` (`:5920-5927`, message `autosetup.ui.menuNoSetupPossible`).  (Aside, outside this pass's question: the diagram's right-click Start is offered *enabled* in that state, because `canStartAutonomy` (`:24455`) asks the button and `hasErrors()` but not `isRemoteLayout()` - the menu offers what the handler then refuses, which is the guard-and-affordance shape Adam has ruled on.)  **Fix:** say "a copy on this computer (Layouts -> Download Central Station Layout Files)" in `Automation.md`'s prerequisite.  Reading only.

## D - not defects (checks that came back clean)

### REG-D1 - `LocDB.data` still round-trips with 2.8.1 in both directions

The list written is still `List<MarklinSimpleComponent>`; its only change is a defensive copy of a route's command list (`MarklinSimpleComponent.java:90-94`, the copy at `:94`).  Every explicit `serialVersionUID` is identical at both revisions (`MarklinSimpleComponent`, `RouteCommand`, `NodeExpression`, `NodeAnd`, `NodeOr`, `NodeGroup`, `NodeRouteCommand`).  The field declarations of `RouteCommand`, the five `Node*` classes, `Accessory` and `Route` are identical (`diff` of every `private|protected|public ... ;` line), and so are the enums a stream names by constant: `RouteCommand.commandType`, `Route.s88Triggers`, `Accessory.accessoryDecoderType`, `Locomotive.decoderType`, `MarklinSimpleComponent.Type`.  `CustomObjectInputStream`'s two class remappings are unchanged, and so are the restore loop (`MarklinControlStation.java:446-480`) and `newLocomotive(MarklinSimpleComponent)` (`:3137-3163`).  The new behaviour around it - keeping an unreadable file aside before the exit save - only adds.

### REG-D2 - route JSON: the same keys written and read at both revisions

`MarklinRoute.toJSON` writes `auto, commands, conditions, id, name, s88, triggerType` and `fromJSON` reads the same set with the same accessors (`getBoolean("auto")`, `optInt("s88")`, ...); `RouteCommand.toJSON` writes `type`, `state` and the config map at both.  A 2.8.1 export imports at HEAD and vice versa.  The three differences are all on the reading side and all more tolerant or deliberate: route speeds clamped at the factory (`S14-B2`), command names matched case-insensitively (`WP-C19d`), and the disarm that is `REG-B3`.

### REG-D3 - `autonomy.json` as HEAD writes it is a superset of what 2.8.1 wrote

For a user still on the JSON path, `Layout.toJSON` writes all sixteen 2.8.1 top-level keys plus `pathPreference` and `timetableSequential`; `Point.toJSON` writes all nineteen 2.8.1 keys plus nine new ones; `Edge.toJSON` all seven plus five.  Nothing 2.8.1 wrote is dropped.  The default values of every setting field in `Layout` (`minDelay` ... `activateRoutes`) are unchanged.

### REG-D4 - the bundle sweep, at three revisions, adds nothing since `RG3`

Run the way `RG3-D2` ran it (key present, referenced as a literal by any `.java` or `.form`), at `master`, `cf048f9b` and HEAD: 1,237 / 1,874 / 1,820 keys, 9 / 278 / 43 orphans.  213 keys live at 2.8.1 are dead or gone at HEAD, of which **six are new since `cf048f9b`**, and each is accounted for: `loc.ui.menuCopyToNextPage` / `...PreviousPage` were already commented out at 2.8.1 (`master:RightClickMenuListener.java:120-133`, the sweep reads the comment - a false positive); `ui.main.toolbar.functions` is the menu Adam renamed Utilities (`TrainControlUI.java:911-924`); and `autolayout.errorInactiveStationInAutoRun`, `errorLockEdgeNotInGraph`, `errorLocomotiveNotInDatabase` are three refusals that were renamed (`REG-B1`) or relaxed into "drop the bad entry and say so" (`397caacb`, `1f8f11a7`).  Two keys came back since `cf048f9b` (`autolayout.ui.menuClearLocomotives`, `confirmClearLocomotives` - `RG3-C1`'s fix).

### REG-D5 - preferences: one default changed, nothing renamed

Every `prefs.get*` read at both revisions, keys resolved through their constants: 34 at 2.8.1, 36 at HEAD.  Read at 2.8.1 and not at HEAD: `AutoSave` (that is `RGN-C1`, still open and not re-filed) and the five display preferences of the deleted graph window already named by `R28-C3` / `RG3-D5` (their constants are now gone from the tree).  Default changed: `AUTO_LOAD_AUTONOMY` only - `REG-B2`.  `Conversion.getFolderHash` and `PositionAwareJFrame`'s window-geometry keys are unchanged.

### REG-D6 - keyboard and menus

Menu accelerators and `KeyStroke`s are identical at both revisions apart from two new Escape bindings.  In `TrainControlUI` the `KeyEvent.VK_*` set only grew.  `LayoutEditor` lost exactly Shift+R and Shift+C (`RG3-D10`, deliberate, changelog line at `Readme.md:396`), and the Readme keyboard diff matches the code.  In the main form the only component gone is `reopenGraphButton`.  "Edit Current Page" left Manage Pages for the new Edit Layout Page menu (`cdea0f81`), where it is the first entry.  Every locomotive-menu key 2.8.1's `RightClickMenuListener` / `RightClickSelectorMenu` used is still referenced from `LocomotiveMenuItems` (`RG3-D3` still holds).

### REG-D7 - earlier regression findings marked fixed, re-checked at HEAD

`RG3-B1` (the "older editor is still there" sentence is gone - `Readme.md:389` now ends "Routes that came from the Central Station open read-only"); `RG3-C2` (`Readme.md:242` documents Control+S in the autonomy editor, and `VK_S` is still handled); `RG3-C3` (the legacy editor item is removed by `removeSupersededPageItems`, called from `display()` at `:9778` on every path); `RG3-C5` and `REL-C12` (Readme and both bundle tooltips say "a column on the right and a row at the bottom", `messages.properties:1818-1819`); `RG3-C6`, `R28-C4`, `RG3-B1`'s two keys (none of `autosetup.ui.layer*`, `route.ui.errorUnusableLocName`, `route.ui.menuNewEditor`, `route.ui.tooltipNewEditor` is in the bundle); `R28-C3` (none of the five dead preference constants is left in `src/`).  All hold.

### REG-D8 - the bundled libraries still run on Java 8

The Readme still says "Install Java 8".  Every class in `resources/json-20260814.jar` and `resources/flatlaf-3.7.2.jar` outside `META-INF/versions/` is class-file major 52 (Java 8), read with `zipfile`; `javac.source`/`javac.target` are 1.8 at both revisions.

### REG-D9 - Central Station traffic: the changes found are fixes

Incoming duplicate suppression is now a 250 ms window (`DUPLICATE_WINDOW_NS`, `MarklinControlStation.java:371`) where 2.8.1 suppressed a repeat for ever - that is the changelog's "the same accessory commanded twice ... had the second command ignored".  A datagram is parsed only when it fills the 13-byte buffer (`NetworkProxy.java:303`), which drops only short frames (a longer one is truncated to 13 by `receive`, exactly as before).  A feedback module is created only from a `CMD_ACC_SENSOR` frame (`S14-C7`).  Route timing is unchanged in effect: both revisions sleep `SLEEP_INTERVAL + max(delay, 150)`; HEAD just logs the floored figure.  The route-conflict guard is silent unless autonomy is running (`MarklinRoute.heldReason`, `:484`), so manual route use is unchanged, as the changelog says.  The CS2 route parser's `S88Flag` change reproduces 2.8.1's result by design (`X8-B3`).  Reading only.

### REG-D10 - smaller changes that looked like losses and are not

`AddLocomotive` now refuses address 0 and a name containing a comma: the first is `MarklinLocomotive.validateNewAddress`, identical at both revisions and already enforced by 2.8.1's own edit door; the second is 2.8.1's own changelog rule applied at a door it missed.  The train-length dropdown offers 0-20, as 2.8.1's `GraphLocAssign` did (`master:GraphLocAssign.java:331`).  A placeholder for an unreadable page cannot be saved over the real file (`LayoutDiagram.saveChanges` refuses, `:737`).  The backup is now one archive rather than a folder of copies, and says so in the changelog; it includes `autonomy.json` and a `routes.json` written through the Export Routes door.

## What this pass did not cover

- **Autonomy runtime parity since `RG5`.**  `RG4`/`RG5` measured it with the parity harness at `357cdc40`; 540 commits have landed since, with `Layout.java` alone +4,338 lines and `HomeStaging.java` +1,419.  Several of them are rulings that change what a 2.8.1 railway does (the tail and room rules of `behaviour.md` 5a-5e, the permanent-turnout rule of 2026-09-22, the non-reversible-at-a-turning-copy rule of MT-367).  Whether each is in the changelog was not audited one by one - `REG-B1` and `REG-C1` are the two found by following the 2.8.1 data through.  **Verification request:** re-run the parity harness (`RG4`'s method) at HEAD against Adam's 2.8.1 file and diff the journey sets against the `357cdc40` run; a journey 2.8.1 made that HEAD refuses, with no changelog line, is a finding.
- **Performance.**  Nothing was timed.  The one change a 2.8.1 user meets on every start is that `setViewListener` now opens the autonomy session (`getAutonomySession` -> `session.open(pages)`, page reduction and the caption migration) on the event thread, and with the new auto-load default also builds a configuration.  **Verification request:** start the 2.8.1 jar (`master`) and the HEAD jar against the same copy of `test/operator_layout/`, with and without a configuration, and compare the time from launch to an interactive window (the log's `ui.initializing` line to the first keyboard response).  A difference a person would notice (more than a second or two) is a finding.
- **`LayoutGrid` / `LayoutLabel` drawing** beyond the spinner timers, and **`CS2File`'s writer** beyond the diff since `RG5` - `RG4-D3`'s downgrade matrix was taken on trust for `.cs2` pages and the page index.
- **The route editor's handling of a 2.8.1 route on save** - `GSR-B5` (a dropdown that cannot show the stored value rewrites it) is open and is the one place a 2.8.1 route could change without being touched; not re-derived here.
- **Nothing was executed**, so B2's dialog in particular is a claim about what the code says; it is the finding most worth the thirty seconds its verification takes.
