# Regression validation: round 1's REG fixes, seen by a user upgrading from v2.8.1

**Status:** open

**Open at close:** REG2-C3, REG2-C7 - carried in the finding store.

**Prefix:** `REG2`

**Reviewed:** branch `autonomy-diagram-r0` at `08a47bdd`, 2026-09-23.  Baseline: the REG lane at `281c79de` and the fixes since (`git log 281c79de..08a47bdd`, 33 commits); `master` at `5f0a75e3` (v2.8.1) for "what did 2.8.1 do".

**Method:** read-only.  `git show` of every commit the REG dispositions name (`e2223851`, `d64023cb`, `4fb36b4b`, `fd6341dd`) and of the isolation pair (`6ce9d625`, `ca7a423a`); `git diff 281c79de HEAD` over `src/`, `Readme.md`, `Automation.md`, `AutomationAPI.md` and the eight bundles; 2.8.1's `TrainControlUI`, `Layout`, `MarklinControlStation` and `Automation.md` read from `master`; the three claim classes read against their pre-fix commits.  Each kind of user traced through the start-up arm by hand.  Sweeps: every writer of `AUTO_LOAD_AUTONOMY`; every implementer/caller of `importRoutes`; every reader/writer of `LocDB.data`, `UIState.data`, the backup folder and every `Preferences` node; every reference to `hasRememberedBounds` at `281c79de` and HEAD; every doc sentence stating 2.8.1's switched-off-station rule.  Nothing compiled, run or written except this file; `cs2_sample_layout/` not opened.  Every finding here rests on reading; the ones execution would settle say how.

| | |
|---|---|
| **A** | none |
| **B** | none |
| **C1** | REG-C3's documentation half was never made: `Automation.md:28` is unchanged, while the disposition and the store say Fixed/Closed |
| **C2** | REG-C3's fix greys the diagram menu's Start on a Central Station layout with a tooltip telling the user to wait for their trains |
| **C3** | the auto-load box shows ticked and does nothing on the JSON arm for every user who never touched it - on a Central Station layout, for all of them, since there is no configuration arm there - and ticking it takes untick-then-tick (restates REG-B2's "For Adam" note; what is new is said) |
| **C4** | the v3.0.0 changelog's REG-B1 sentence tells the user to untick "Automatic Destination"; the control is "Can Be Chosen in Full Autonomy" |
| **C5** | `AutomationAPI.md:459` still promises that inactive points "can still be accessed" by hand - REG-B1's sibling, in the document JSON-path users read |
| **C6** | an import still logs "Route X is running..." for every route the file saved armed, just before the new notice says their firing is off |
| **C7** | the import reads each route's `auto` and discards it unreported, so the notice's remedy cannot restore a backup as it was, and "all at once with Bulk Enable" arms the routes that were off |
| **D** | REG2-D1 to REG2-D12 |

## A - high

None.

## B - medium

None.  Weighed: C2 (a false reason on a greyed control) and C7 (a route that was off in a backup armed by following the advice) - both are narrow populations with a visible way past, so C.

## C - low

### REG2-C1 - REG-C3's documentation half was never made: the user guide's prerequisite is unchanged, and the finding is closed

| | |
|---|---|
| **Disposition** | Fixed - 8370abb1 |
| **Where** | `Automation.md:28`; `docs/reviews-2026-09-23/REG.md:183` (the disposition); `docs/manual-tests/findings.tsv` (REG-C3 row, `Closed`) |

REG-C3's disposition reads "Fixed - 4fb36b4b (Automation.md's prerequisite) and e2223851 ...", and `4fb36b4b`'s message lists "REG-C3 (Automation.md's prerequisite)".  But `git show 4fb36b4b -- Automation.md` has five hunks - at lines 163, 187, 197, 211 and 254 (DCN's routing, lengths, captions and homes paragraphs) - and none at the prerequisite.  `git diff 281c79de HEAD -- Automation.md` is the same five.  HEAD still says:

    Automation.md:28
        **A track diagram.** Either downloaded from your Central Station or drawn in TrainControl's own editor.

which is REG-C3's quoted sentence word for word.  `Readme.md:128` ("or via a JSON configuration file") is also unchanged.  So the half of REG-C3 that tells a Central Station user autonomy needs a copy on this computer does not exist anywhere except by implication in `Readme.md:394` (the Download item), and the store calls the finding closed.

The code half (`canStartAutonomy`) is done - see REG2-D4 - so this is only the sentence.  **Verification request:** none; `git diff 281c79de HEAD -- Automation.md | grep -n "^@@"` shows it.  **Suggested fix:** REG-C3's own wording - "Either drawn in TrainControl's own editor, or downloaded from your Central Station onto this computer (Layouts -> Download Central Station Layout Files); autonomy does not run on a diagram read live from the Central Station" - and reopen the store row until it lands.

### REG2-C2 - on a Central Station layout the greyed Start item now tells the user to wait for their trains

| | |
|---|---|
| **Disposition** | Fixed - 8370abb1 (claim 8346be65, red first) |
| **Where** | `TrainControlUI.java:24523-24527` (`canStartAutonomy`), `:24574-24603` (`whyAutonomyWillNotStart`), `LayoutRightclickAutonomyMenu.java:423-444` |

REG-C3's fix added `!isRemoteLayout()` to `canStartAutonomy`, so the diagram right-click menu's Start is greyed over a Central Station layout (reachable there once a JSON graph is loaded: `InnerLayoutPanelMouseClicked` -> `showFor`, and the Start branch needs only `hasAutoLayout()`).  A greyed Start gets a tooltip, and the tooltip comes from `whyAutonomyWillNotStart()`, which knows three reasons and not the new fourth:

    LayoutRightclickAutonomyMenu.java:423-444
        boolean canStart = ui.canStartAutonomy();
        menuItem.setEnabled(canStart);
        if (!canStart) { ... menuItem.setToolTipText(AutonomyEditorPanel.wrapped(ui.whyAutonomyWillNotStart())); }

    TrainControlUI.java:24594-24602
        if (errors > 0) return ... errorCannotStartWithErrors
        if (blocking > 1) return ... errorCannotBuildDetail
        if (blocking == 1 || broken) return ... errorCannotBuildDetailOne
        return I18n.t("autolayout.errorUnableToStartAutonomyWaitForTrains");

On a Central Station layout `getAutonomySession()` is null (`getLocalLayoutPath()` is null for the stored empty path, `:2918-2920`), so errors = 0, blocking = 0, `autonomyHasErrors()` = false, and the tooltip reads *"Unable to start autonomy. Wait for all trains to reach their stations."*  Nothing is moving and nothing will make it true.  Before the fix the item was enabled and a press showed `autosetup.ui.menuNoSetupPossible` ("Autonomy needs a layout stored on this computer") - the true reason.  The fix made the affordance agree with the guard about *whether*, and disagree about *why*; the menu's own comment at `:426-440` (V32-C1) and `requestStartAutonomy`'s (UXR-C5, V31-C1) record that this exact sentence has been filed as "told to wait for trains that are not running" three times.

**Verification request (needs execution, briefly).**  In `testSwitchingToACentralStationLayout.testStartIsNotOfferedOverACentralStationLayout`, after its `assertFalse(ui.canStartAutonomy())`, add `assertEquals(ui.whyAutonomyWillNotStart(), I18n.t("autosetup.ui.menuNoSetupPossible"))`.  **Proves:** it returns the wait-for-trains sentence.  **Refutes:** it returns the layout sentence (which would mean the session is not null in that fixture, and the menu's path differs).  **Suggested fix:** `whyAutonomyWillNotStart()` answers `autosetup.ui.menuNoSetupPossible` first when `isRemoteLayout()`, in the same order as `refuseAutonomyStartWhileBroken`; the scripting API's exception at `:24664` then says it too.

### REG2-C3 - the auto-load box shows ticked and does nothing on the JSON arm for every user who never touched it; ticking it takes two clicks

| | |
|---|---|
| **Disposition** | Partly fixed - the changelog line (8370abb1).  Open - Adam's decision: whether an untouched Load Autonomy box should show unticked where the old JSON graph is the only thing it could load |
| **Where** | `TrainControlUI.java:1251` (the displayed default), `:9695-9716` (the arm), `:9723-9750` (`resumesFromJsonAtStart`), `Readme.md:374` |

This restates the note in REG-B2's disposition ("For Adam: an untouched box shows ticked and does not load an old JSON graph"), which now sits on a row the store marks Closed.  What is new:

1. **For a Central Station user it is not "does not load an old JSON graph" but "does nothing at all".**  The box is displayed from `prefs.getBoolean(AUTO_LOAD_AUTONOMY, true)` (`:1251`).  On a Central Station layout `getAutonomySession()` is null, so the configuration arm can never be taken, and with no stored key `resumesFromJsonAtStart()` is false - so the box reads ticked, its tooltip says it "attempts to parse the autonomy configuration at startup", and it never does anything.  At 2.8.1 the same user saw it unticked.
2. **The way to 2.8.1's behaviour is untick, then tick.**  2.8.1's user who wanted their JSON graph at start ticked the box once.  At HEAD it is already ticked; clicking it writes `false`, clicking again writes `true` (`:26326-26328`), and only then does the JSON arm load.  The javadoc at `:9736-9737` says "ticking it (or unticking and ticking) is what stores the choice" - but a box shown ticked cannot be ticked.  Nothing a user reads says so.
3. **The changelog line is unconditional.**  `Readme.md:374`: "the one you were last using is loaded when TrainControl starts".  For a user whose stored key is `false` (ticked, then unticked, at 2.8.1) the outer `isSelected()` is false and no configuration is loaded.

**Is anyone worse off than at 2.8.1?**  Behaviourally no - REG2-D1 traces every stored state and each loads exactly what 2.8.1 loaded, except "ticked with no `autonomy.json`", which is now silent where 2.8.1 opened the Blank/Sample chooser.  In what the box *shows*, yes: every user with no stored key on the JSON arm, and discovering how to get the old behaviour is harder.

**Verification request.**  Reading settles it; to see it, start against a Central Station layout with the `AutoLoadAutonomy<hash>` value removed and an `autonomy.json` present: Startup -> Load Autonomy is ticked and no graph is loaded.  **Suggested fix (Adam's call):** display the box from the key when there is one and, when there is none, from whether this layout can take the configuration arm (`!isRemoteLayout()`), refreshing it on a layout switch; or keep the display and say in the tooltip that an `autonomy.json` is loaded only once the box has been set by hand.  And "... is loaded when TrainControl starts, unless Startup -> Load Autonomy is unticked" in the changelog.

### REG2-C4 - the changelog names a control that does not exist: "untick Automatic Destination"

| | |
|---|---|
| **Disposition** | Fixed - 8370abb1 |
| **Where** | `Readme.md:380` (added in `4fb36b4b`); `messages.properties:1664`; `AutonomyEditorPanel.java:1494` |

    Readme.md:380
        - A switched-off station can no longer be sent to by hand, nor driven through: switched off now means nothing uses
          it.  To keep a parking track reachable by hand but out of autonomy's choices, leave it switched on and untick
          Automatic Destination.

There is no control of that name.  The toggle is `autosetup.ui.menuAutoDestination` = **"Can Be Chosen in Full Autonomy"** (the autonomy editor's station menu, `AutonomyEditorPanel.java:1494`), which is what `Automation.md:178` and `behaviour.md:36/458/900` call it.  The wording is REG-B1's own suggested fix ("untick 'automatic destination'"), copied without checking the label; this is the one sentence a 2.8.1 user with switched-off parking tracks follows to get their old behaviour back.

Smaller: "nor driven through" reads as new, but passage was already refused at 2.8.1 (`master:Layout.java:1416-1435`, the unfenced intermediate rule).  Harmless.  **Verification request:** none.  **Suggested fix:** "leave it in service and untick Can Be Chosen in Full Autonomy on the station's menu in the autonomy editor".

### REG2-C5 - `AutomationAPI.md` still promises that inactive points can be reached by hand

| | |
|---|---|
| **Disposition** | Fixed - 8370abb1, and AutomationAPI.md's two siblings in 4132d260 |
| **Where** | `AutomationAPI.md:459` (and `:484`) |

REG-B1's fix corrected the changelog and two comments.  The document a JSON-path user reads for the `active` key - and REG-B1 names that user ("a 2.8.1 user still on their old file meets the new refusal on the same click that worked yesterday") - still says:

    AutomationAPI.md:459
        You can optionally mark any point as inactive (`"active" : false`).  Automatically chosen paths will never include
        inactive points.  However, they can still be accessed in semi-autonomous (point-to-point) operation.

This was 2.8.1's `Automation.md:434`, true then for destinations.  At HEAD `isPathClear` refuses an inactive destination at every door (`Layout.java:2603-2611`).  `:484` ("inactive points can be traversed in semi-autonomous mode") was already false at 2.8.1 - the intermediate rule was unfenced there too - so it is older than this release, but it is the same paragraph's promise.  `4fb36b4b` edited this file in three places and not these.  Found by sweeping for REG-B1's sentence in every doc (Method).  **Verification request:** none.  **Suggested fix:** "Inactive points are used by nothing - not autonomy, and not a route you pick yourself - except that a train already standing on one can be driven off it by hand."

### REG2-C6 - an import still announces every route the file saved armed as "running", just before the notice says they are off

| | |
|---|---|
| **Disposition** | Fixed - 8370abb1 (claim 8346be65, red first): an imported route is built unarmed |
| **Where** | `MarklinControlStation.java:4140-4147` (`parseRoutesFromJson`), `MarklinRoute.java:119-145` (the constructor), `:162-185` (`executeAutoRoute`) |

The lead the task named, judged: yes, a user meets it, and it is cosmetic.  `MarklinRoute.fromJSON` passes the file's `auto` to the full constructor, which ends in `executeAutoRoute()`; for an armed route with an s88 that starts a monitor thread whose first statement - before it looks at `enabled` - is

    MarklinRoute.java:182-185
        this.network.logf("route.running", this.getName());

`parseRoutesFromJson` disables the route on the next line, but the thread has started and logs anyway.  So restoring a backup of a working railway writes one *"Route X is running..."* per armed s88 route (asynchronously, usually before "Deleting existing routes..."), and then REG-B3's new notice: *"N routes imported.  Their automatic firing is off ..."*.  In five of the seven translations `route.running` reads "is being executed" (es `se está ejecutando`, fr `est en cours d'exécution`, it `è in esecuzione`, nl `wordt uitgevoerd`, pl `jest wykonywana`; da and de say "is running") - a stronger false statement than the English.  Not new with the fix (it has logged since `S14-A1`'s disarm), but the fix put a contradicting sentence beside it.  No behaviour follows: the parked thread exits on the sensor's next pulse without firing (`if (!this.enabled) return;`), and a later enable replaces the route object (`writeRouteEnabledState`) or finds the parked monitor alive (`executeAutoRoute`'s guard), so nothing double-fires.

**Verification request (needs execution).**  In `testAnImportSaysItsRoutesAreOff`, the tap already sees it: after the import, wait up to a second for `logged` to contain `I18n.f("route.running", "REG-B3 probe 0")` (the route saved `auto: true`, s88 8870).  **Proves:** present.  **Refutes:** absent.  **Suggested fix:** build the imported route disarmed rather than disarm it after - a `fromJSON` overload (or a `parseRoutesFromJson`-local `put("auto", false)` on a copy of the object) - which removes the line and the parked thread, and makes the comment's "no window to reason about" literally true.

### REG2-C7 - the file's per-route arming is read and thrown away unreported, so the notice's remedy cannot restore a backup as it was

| | |
|---|---|
| **Disposition** | Open - Adam's decision: whether an import should restore each route's saved firing, or at least say which were saved armed |
| **Where** | `MarklinControlStation.java:4144-4147`, `:4192-4196`; `TrainControlUI.java:26101-26106`; `Readme.md:404` |

REG-B3's fix says the routes are off and how to turn them on: `"{2}" on the right-click menu of a route, or "{1}" for several at once` (Enable Auto Execution / Bulk Enable), and the changelog: "Turn on the ones you want from their right-click menu, or all at once with Bulk Enable."  But *which* ones were on is exactly what the import discarded: `auto` is read (`MarklinRoute.java:1321`) and overridden, and neither the log nor the dialog names the routes that were saved armed.  A 2.8.1 backup where some s88 routes were deliberately off - a power-cut route armed only for certain sessions, say - cannot be put back as it was without opening the JSON; and following "all at once with Bulk Enable" (whose prompt defaults to `*`) arms the ones that were off, which is the one outcome a restore should never produce.  At 2.8.1 the import restored each route's state exactly.

Adam's rule (arrive disarmed) is not in question.  **Verification request:** none; reading.  **Suggested fix (Adam's call):** in `importRoutes`, log one line per route the file had armed ("Route X was saved armed; it arrives off") and give the count in the notice ("5 of the 12 were saved armed ..."), so the user can re-arm exactly those; and drop "all at once" from the changelog sentence.

## D - not defects (checks that came back clean)

### REG2-D1 - REG-B2 verified: every kind of user traced through the start-up arm

`AUTO_LOAD_AUTONOMY` is written only by `AutoLoadAutonomyMenuItemActionPerformed` at both revisions (`master:TrainControlUI.java:13958`, HEAD `:26327`; no `doClick`, no `prefs.clear/removeNode/importPreferences` in `src/`), so "an absent key is 2.8.1's unticked box" holds.  The key name and folder hash are unchanged.  The JSON text is read at `:9395-9396` in the same `setViewListener`, before the `invokeLater` that asks `resumesFromJsonAtStart`.

| user (stored key) | layout | HEAD at start | 2.8.1 at start |
|---|---|---|---|
| ticked (`true`), has `autonomy.json` | local, no config / CS | JSON graph loaded, route activations applied | same |
| ticked (`true`), no `autonomy.json` | any | nothing | Blank/Sample chooser, then a validation error |
| ticked (`true`) | local with a configuration | configuration loaded | (no configurations) |
| never touched (absent) | local, no config / CS | nothing | nothing |
| never touched (absent) | local with a configuration | configuration loaded | - |
| unticked (`false`) | any | nothing | nothing |
| new user (absent) | any | as "never touched" | - |

Both halves of REG-B2 are gone for the user it was about: no chooser (key absent -> false; ticked + empty -> false), and a 2.8.1 file's `activateRoutes` is applied at start only for somebody who ticked the box, as 2.8.1 did.  The claim: `testAStartLoadsOnlyAGraphSomebodyAskedFor` - at `d64023cb` `resumesFromJsonAtStart` returned `true`, so `testAnUntouchedBoxDoesNotLoadAnOldGraph` and `testATickedBoxWithNothingToLoadAsksNothing` fail on their `assertFalse` for the stated reasons; `testATickedBoxStillLoadsItsGraph` is the control (passes both sides); `testTheStartUpArmAsksIt` pins the call and "one road".  The display question is REG2-C3.  Considered and not filed: a Central Station user who ticked the box still has the file's route activations applied at start though Start is refused there - 2.8.1 parity, and the manual sends the JSON graph still offers on that layout use the same routes.

### REG2-D2 - REG-B3 verified: the count, every implementer and caller, and the eight bundles

`ViewListener.importRoutes` is implemented only by `MarklinControlStation` (no other `implements ViewListener`, no `Proxy` in `src/` or `test/`); callers are the menu door (`TrainControlUI.java:26067`, uses the count) and three tests (`testAnImportSaysItsRoutesAreOff:123`, `testInvalidInput:529`, `testRoutes:1501`), which compile against `int` unchanged.  `AutomationAPI.md` and the programmatic example do not use it; only a program *compiled* against the 2.8.1 jar would meet the descriptor change, and it recompiles as is.  `added` counts `newRoute` successes, so refused duplicates are excluded.  The key is in all eight bundles with `{0}`, `{1}`, `{2}` each, ASCII-escaped, no ASCII apostrophe for `MessageFormat` to eat (fr/it use `’`); `{1}`/`{2}` are the translated `ui.main.bulkEnable` and `route.ui.menuEnableAutoExecution`, both real controls (the Routes tab button; `RightClickRouteMenu.java:120-126`, offered when the route has an s88).  The log line and the dialog use the same key and arguments.  The claim fails at `d64023cb` on `assertTrue(logged.contains(notice))` - the key and the count existed there, the `logf` did not - which is the right reason; `assertEquals(added, 2)` is its precondition.  Parse-then-delete order and the one door (`parseRoutesFromJson` has no other caller) are unchanged.

### REG2-D3 - REG-B1's partial fix: what it says is done is done

`Layout.java:2548-2555` now states the 2026-09-06 rule ("It may no longer FINISH on one"); `AutonomySession.java:619-631` now counts eighteen stations, says the destination rule is unfenced, and states the open question as Adam's.  The v3.0.0 line exists (`Readme.md:380`, wording in REG2-C4).  The start stays exempt at HEAD (`Layout.java:2594-2601`, and no start test in `isPathClear` outside `isAutoRunning`), so the v2.8.0 sentence's "still drive one away from it" is still true; leaving the historical line as history is reasonable now v3.0.0 supersedes it.  Store row is `Open - Adam's decision`, matching.

### REG2-D4 - REG-C3's code half verified

`canStartAutonomy` now asks `!isRemoteLayout()` before `autonomyHasErrors()`, the same two questions `refuseAutonomyStartWhileBroken` asks in the same order (`:5931`, `:5952`); the only other term, the button's enabled state, is the documented deliberate difference.  Its only caller is `LayoutRightclickAutonomyMenu.java:423`; the strip (`AutonomyOverlayToggle`) does not call it and is not mounted on a Central Station layout.  The claim fails at `d64023cb` for the right reason: with the layout path emptied the session is null, `autonomyHasErrors()` is false, and the old predicate returned `true` with the button enabled.  What the fix left wrong is REG2-C2; what was not done is REG2-C1.

### REG2-D5 - REG-C1 and REG-C2 verified against the code

REG-C1: `Readme.md:381` matches `keepAtomicRoutesOnWhileTheRailwayCouldReleaseTrack`'s two questions (`Layout.unmeasuredTrackThatCouldBeReleased`, `trainsWithNoLength` over `getLocomotivesToRun`).  REG-C2: `RouteListMouseClicked` runs on the play-button region and confirms elsewhere (`:23655-23690`), non-left clicks return to the menu handler; `findRoute` ends in `showRouteInTheList` for an exact or single match and offers a choice for several (`:29778-29815`).  Both sentences are now true.

### REG2-D6 - the rest of the changelog lines `4fb36b4b` added

The import sentence (`Readme.md:404`) is true (Bulk Enable prompts with `*`, i.e. every route with an s88 - the consequence is REG2-C7).  The two guard-signal lines match `autosetup.ui.menuPairSignal` / `menuPairEntrySignal` ("Exit Guard Signal...", "Entry Guard Signal...").  "Bulk Tools" is `autosetup.ui.menuBulkTools`.  "Maximum Train Length" is `autolayout.ui.menuMaxTrainLength`.

### REG2-D7 - today's other changes owe no changelog line to a 2.8.1 user

The 33 commits' behaviour changes (GUI-A1, TDY-B1/AUT-B2, AUT-B1, TDY-B2, GUI-B1/B2/B3, AUT-C1/C3/C4, TDY-C1-C4, GUI-C2/C5/C7/C8) all correct features that are new in v3.0.0 - copies, homes held to a facing, tails, the route highlight, the Segment Length prompt - so a 2.8.1 user never met the broken version; the v3.0.0 section describes the features.  The one serialised-format change, `copyArrival` -> `copyFacing` on `Point.toJSON` (`ac5fe2f2`), renames a key introduced the same day (`74e2d8a1`, OB-282) and never shipped; a 2.8.1 `autonomy.json` has neither.  `LocDB.data`'s classes are untouched in the range.

### REG2-D8 - the test-run isolation leaves the application's files and node exactly where they were

With `traincontrol.dataDir` unset (or blank), `Util.dataPath(name)` returns `name` - the bare relative name every site used before; with `traincontrol.preferences` unset, `Util.preferencesFor(c)` returns `Preferences.userNodeForPackage(c)`.  Every site was converted as a pure wrap: `UIState.data` read (`:2833`), write (`:2482`), unreadable-kept-aside (`:2458`), load-failed checks (`:2858`, `:2868`), first-launch (`:4963-4964`), backup archive (`:22518-22520`); `LocDB.data` restore (`MarklinControlStation.java:446`), write (`:1827`), kept-aside (`:1803`); `getBackupPath`'s folder.  `TrainControlUI.prefs` is `/org/traincontrol/gui` at both revisions (`master:TrainControlUI.java:374`), and `AutonomyEditorPanel.VIEW_PREFS` resolves to the same package node; no other `Preferences` node exists in `src/`.  The two properties are set only by `docs/tools/one.sh:432` and `battery.sh:540` (`nbproject` has no such `jvmargs`).  So a 2.8.1 user's `LocDB.data`, `UIState.data` and preferences are found where 2.8.1 left them.  (`autonomy.json`, the layout folders and `tc_loc_icons` are not routed through `dataPath`; that concerns what a test run shares, not what the application reads - see "not covered".)

### REG2-D9 - `hasRememberedBounds` was dead

At `281c79de` the only occurrence in the tree was its declaration (`PositionAwareJFrame.java:212`); it was `protected`, no subclass declared or called it, no test named it in a reflective string, and it was not in `master` (added since v2.8.1, per `git log -S`).  Removing it changes nothing.  Cosmetic only: `fd6341dd` left the next method's `/**` at column 0 (`PositionAwareJFrame.java:198`); the javadoc is still attached.

### REG2-D10 - the notice's placement in the door

The dialog is raised in `BusyDialog.run`'s finishing half, on the event thread, after the list refresh and only when nothing failed; the failure path is unchanged.  (A sync failure after a successful import still reads as "failed to import" - pre-existing, and the log carries the notice.)

### REG2-D11 - Escape in the editor, as the changelog now documents it

"Escape (let go of whatever is held; with nothing held, close the editor)" is FR-065, Adam's ruling ("same as closing via button, with warning shown as needed", `LayoutEditor.java:7390-7403`); the unsaved-changes warning is the close button's.  A 2.8.1 user's Escape-to-reset habit is covered by the first clause.

### REG2-D12 - no sibling import door

`parseRoutesFromJson` has one caller; the backup archive writes `routes.json` through the export door and restoring it goes back through Import Routes, so the notice covers the restore path too.

## What this pass did not cover

- **Execution.**  Nothing run.  REG2-C2 and REG2-C6 have runnable probes; the rest are settled by reading.
- **What a test run still shares.**  `dataPath` covers `LocDB.data`, `UIState.data` and backups only.  The working directory's `autonomy.json` (written by `saveState` when no configuration is active and the text is non-empty) and the Java Preferences node for classes run outside `one.sh`/`battery.sh` (NetBeans' own test runner sets neither property) are still shared.  Out of the REG lane; noted for whoever owns the harness.
- **REG-B1's open decision** (translate `active: false` on an imported station) was not re-argued.
- **The parity-harness and start-up timing requests** in REG's own "not covered" remain unexecuted.
