# RSA71 - Validation of TrainControl 3.0.2, round 1, over everything since 3.0.1 (`a5e787aa..b2037955` on `dev`, 46 commits): OB-349 to OB-356, FR-129, FR-133 and the run refusal hold; the load doors, Unload and Export still reached a configuration while a route ran (C2, C3); a direction change made during a route was never followed (C8); five sentences of the changelog and keyboard list said more than the program does (C1, C4 to C6); two corners are left by Adam (C7, C9); one tracked baseline was not a leak (C10) - CONVERGED, every C settled

**Status:** closed

**Prefix:** RSA71

**Reviewed:** branch `dev` at `b2037955` (*Records: MT-756 (OB-355) and MT-757 (OB-356); both leave the Inbox; the changelog names them*): `git log a5e787aa..b2037955`, every production hunk of `HomeStaging`, `Layout`, `AutonomyCompanionStore`, `AutonomySession`, `TileGraph`, `TileAnnotation`, `TileOverlay`, `AutonomyEditorPanel`, `AutonomyViewerPanel`, `AutonomyMenu`, `LayoutEditor`, `LayoutGrid`, `LayoutLabel`, `LayoutRightclickAutonomyMenu`, `RightClickTimetableMenu`, `TrainControlUI`, the 8 bundles, `Readme.md`, `Automation.md`, `behaviour.md` and `build.xml`, against Adam's rulings in the brief.  Read-only, in a worktree detached at the commit.  2026-10-18.

## Method

**Read whole:** the brief and every production hunk above; for the run refusal, every door the brief lists and `aRunHoldsTheSetup`'s callers; for OB-351, `armMask`, `armAnswersFor`, `normalizeToArms`, `recordSquare` and every door that writes a direction.

**Probe:** `diag.testV302RouteDoors` (the live-snapshot sandbox; a probe route marked executing as `testNoSetupEditWhileARouteRuns` does; autonomy idle): the Return Home menu's two items, the configuration doors and Export while the route runs.  No Central Station was reached; Adam's own files and his running TrainControl were not touched.

**Counts:** 0 A, 0 B, 10 C, D below.

**This round has CONVERGED: no A and no B.**  Every C is settled: C1 to C6 and C8 fixed, C7 and C9 left on Adam's word, C10 not a defect.  Also seen and fixed, not graded: the PAUSED_AWAY sentence on the Return Home button's tooltip was one unwrapped line (`3ee312fe`).

---

### RSA71-C1 - The changelog said Run Trains to Their Homes is greyed while anything runs; it follows the Return Home button, which is greyed by autonomy alone

| | |
|---|---|
| **Disposition** | Fixed - `b8608078`: Run is available whenever the Return Home button is, and Place All is greyed while anything runs (as Automation.md already said).  The code is right: a door that drives trains keeps its own question. |
| **Grade** | C - wording. |
| **Names** | FR-129 |
| **Where** | `Readme.md:408`; `TrainControlUI.buildReturnHomeMenu` (`run.setEnabled(isReturnHomeOffered())`), `refreshReturnHomeButton`. |
| **Needs execution** | done - probe: with a route executing, *"[Run Trains to Their Homes] enabled=true"*, *"[Place All Trains at Their Homes (4)] enabled=false"*. |

---

### RSA71-C2 - The configuration load doors reached a load while a route ran, though the Autonomy menu greys Load: the viewer's enable button, the strip's Load this configuration, and the menu's list; Unload the same

| | |
|---|---|
| **Disposition** | Fixed - `6919ec4c`, `82897755`, `09bfe71a`: one question, `TrainControlUI.refusedWhileSomethingElseRuns` (a route, a hand send, a graceful stop or Return Home staging holds the setup and autonomy is not busy), asked first by every user door to a load (`AutonomyViewerPanel.loadByRequest`, the menu's list, the strip) and by `unloadAutonomy`.  While autonomy itself is busy the doors keep their offer to stop the trains (`prepareAutonomyReload`); the reloads the program makes at rest call `load` directly.  Claims in `regression.testNoSetupEditWhileARouteRuns`, red first. |
| **Grade** | C - a load during a route rebuilds the railway and switches the routes the configuration activates (`applyAutonomyRouteActivations`), the effect the route-activation door already refused; reachable only by a door the menu greys elsewhere. |
| **Names** | Adam, 2026-09-28: *"There should be no setup edit possible during a run"*; of a route, *"yes, refuse"* |
| **Where** | `AutonomyViewerPanel` enable button (`:361`), `TrainControlUI` strip (`~:10650`), `AutonomyMenu` configuration list, `unloadAutonomy`; `prepareAutonomyReload` (`:5088`) asked only `isAutonomyBusy`. |
| **Needs execution** | done - probe: *"LOAD during route: configuration=Main railway replaced=true routeStillExecuting=true"*; after the fix the claim asserts the refusal, the offer while autonomy is busy, and Unload at rest. |

---

### RSA71-C3 - Export asked whether autonomy is busy, where Import asks whether any run holds the setup

| | |
|---|---|
| **Disposition** | Fixed - `6919ec4c`: `exportConfiguration` refuses on `aRunHoldsTheSetup`, with `whyTheSetupIsHeld`, as Import does. |
| **Grade** | C - reachable only from a menu opened at rest and clicked after a route starts. |
| **Names** | MT-601 (Adam: *"Export should also only be allowed once we are stopped"*) |
| **Where** | `AutonomyViewerPanel.exportConfiguration` (`:1810`); Import (`:1105`). |
| **Needs execution** | done - claim in `regression.testNoSetupEditWhileARouteRuns`. |

---

### RSA71-C4 - The changelog said a clicked setup check finding is outlined on the Track Diagram; it is outlined in the autonomy editor's diagram

| | |
|---|---|
| **Disposition** | Fixed - `b8608078`. |
| **Grade** | C - wording. |
| **Names** | OB-352 |
| **Where** | `Readme.md:418`; `AutonomyEditorPanel.outlineTheNotice`. |
| **Needs execution** | read. |

---

### RSA71-C5 - The changelog said setup edits "now wait" during a route and "say what is running"; they refuse, with one sentence naming every kind of run

| | |
|---|---|
| **Disposition** | Fixed - `b8608078`: "are now refused with a message while a route is running", loading and exporting named. |
| **Grade** | C - wording. |
| **Where** | `Readme.md:417`; `ui.main.tooltip.placeAtHomesWhileRunning`. |
| **Needs execution** | read. |

---

### RSA71-C6 - The keyboard list said Alt with any letter selects a locomotive; Alt+G, P, V, O, S and U are other shortcuts

| | |
|---|---|
| **Disposition** | Fixed - `b8608078`: "Alt with a letter that has no Alt shortcut of its own does the same". |
| **Grade** | C - wording. |
| **Where** | `Readme.md:184`; `TrainControlUI.java:21462-21482`. |
| **Needs execution** | read. |

---

### RSA71-C7 - OB-353: on track joined to no station, a click says there are no directions to set while the right-click menu, the arm boxes and the bulk tools still set them

| | |
|---|---|
| **Disposition** | Ruled - left as it is (Adam, 2026-10-18: *"C7- leave it"*).  The menu writes are what OB-353's ruling allows (*"it can accept edits"*). |
| **Grade** | C - a message, at rest. |
| **Names** | OB-353, RSA63-C5 (out-of-service squares behave the same) |
| **Where** | `AutonomyEditorPanel.java:8552` (`infoNoStationHasNoDirection`); `AutonomySession.canCarryDirection` (`:8028`). |
| **Needs execution** | read. |

---

### RSA71-C8 - A locomotive's direction change made while a route held the setup was recorded and never followed, so the setup's facing stayed as it was after the route ended

| | |
|---|---|
| **Disposition** | Fixed - `a6f895fa`, on Adam's *"it should reverse, but be careful to avoid duplication in code mechanisms on how this is done"*: the one mechanism that follows a turn, `followTheTurn`, keeps a name it declined for a route alone in `turnsHeldByARoute` (a second change takes it out: two flips cancel), and asks again for each when the route finishes (`View.routeFinished`, from `MarklinRoute.execRoute`'s `finally`, posted to the event thread); a rename or delete follows the set.  Autonomy's own run, Return Home and a hand send keep the ruled outcome.  Claims in `regression.testAThrottleReversalIsFollowedFromItsEcho`, red first. |
| **Grade** | C - the decoder runs one way and the setup faces the other after a route that reversed a train, until the operator turns it on the diagram. |
| **Names** | the comment at `TrainControlUI.java:14862` ("recorded, then declined") |
| **Where** | `TrainControlUI.followDirectionChanges` (`:14902`), `followTheTurn` (`:14941`); `MarklinRoute.java:1099` (a route's own direction command). |
| **Needs execution** | done - the claims. |

---

### RSA71-C9 - The autonomy editor's own Place All does not ask again after its confirmation, where the Return Home and Track Diagram doors do

| | |
|---|---|
| **Disposition** | Ruled - left as it is (Adam, 2026-10-18: *"C9- leave it"*). |
| **Grade** | C - a route started while the confirmation is up. |
| **Names** | FR-129 |
| **Where** | `AutonomyEditorPanel.placeEveryTrainAtHome()` (`:304`, `() -> null`). |
| **Needs execution** | read. |

---

### RSA71-C10 - A tracked test baseline changed in a records commit (`2d92543e`: `minDelay` 1 to 0, `maxDelay` 5 to 1)

| | |
|---|---|
| **Disposition** | Not a defect - the blessed `test/baseline/configuration.json` has 0 and 1, and putting the file back to 1 and 5 turns `regression.testConfirmedGoodState` red; kept as committed. |
| **Grade** | C (none) |
| **Where** | `test/baseline/layout/config/autonomy/configuration-Main.json`. |
| **Needs execution** | done - `regression.testConfirmedGoodState`. |

---

### RSA71-D1 - OB-355 (PAUSED_AWAY), FR-129 (Return Home's menus), FR-133 (the Text Labels box)

| | |
|---|---|
| **Disposition** | Checked - clean (but C1, C9). |
| **Grade** | D |
| **Where** | PAUSED_AWAY only when nothing else is misplaced, carried as blocked, counted as home at the run's end, triaged from one snapshot on the worker; the Place item's state and its press ask one question, again after the confirmation; only a remembered mode 4 migrates; `captionsLabels` gone from source and bundles. |
| **Needs execution** | read. |

---

### RSA71-D2 - OB-349 to OB-354 and OB-356

| | |
|---|---|
| **Disposition** | Checked - clean (but C7). |
| **Grade** | D |
| **Where** | OB-349: exact names, every list cleaned, the dialog once; OB-350: the Place and the paste stand the train with no facing; OB-351: normalization only opens, is idempotent, and every direction door goes through `recordSquare`; OB-352: the square revealed; OB-353: an undirected stationless walk, arrows hidden on both diagrams; OB-354: the click area matches the icon; OB-356: the item disabled. |
| **Needs execution** | read. |

---

### RSA71-D3 - The run refusal, the monitor rule, the bundles, the docs and build.xml

| | |
|---|---|
| **Disposition** | Checked - clean (but C1 to C6). |
| **Grade** | D |
| **Where** | Every door the brief lists asks `aRunHoldsTheSetup`, which takes no Layout monitor on the event thread; 2055 keys in each of the 8 bundles, ASCII only, placeholders right; no dashes in the Readme or Automation.md lines; the keyboard claims match the code; the new classes in build.xml. |
| **Needs execution** | read. |
