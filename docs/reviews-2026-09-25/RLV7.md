# RLV7 - Validation, round 7: round 6's fixes, one validator over autonomy, the windows and bundles, and the documents and tests (TrainControl 3.0.0)

**Status:** open

**Prefix:** RLV7

**Reviewed:** branch `autonomy-diagram-r0` at `ab8aa1a9`, working tree clean by `git --no-optional-locks status --porcelain` (it printed nothing).  Scope `806e81eb..ab8aa1a9`: round 6's claims `528fd305`, fixes `04b1b204`, records `c868fd54` and store `ab8aa1a9`, reaching into round 5's `0f62a7ca` and older code only where `04b1b204` touches it.  Read-only, 2026-09-26.

## Method

Nothing was compiled or run and no JVM was started.  No git state changed: only `git log`, `git show`, `git diff` and `git diff --stat` between commits, `git grep`, `git rev-parse` and `git --no-optional-locks status`.  Nothing under `cs2_sample_layout/` was read or written.  This file is the only one written.

**Read whole:** the brief; `docs/reviews/README.md`; `docs/reviews-2026-09-25/RLV6.md`, every finding and disposition; `RLA5.md`, `RLU5.md` and `RLD5.md`, for what round 6 checked.

**The commits,** with `git show`: `04b1b204` in full (source, the two test hunks, behaviour.md and open-questions.md); `528fd305` in full (the five claims and the helpers `moveAStandingTrain`, `assertStandsWhereItWasMoved` and `answeringYes`); `c868fd54` by its stat and message (RLV6.md read at HEAD); `ab8aa1a9`'s findings.tsv, behaviour.md and open-questions.md hunks, and the store by its rows.

**The code at HEAD, around each change:**

- `AutonomyViewerPanel`: both `loadAfterImport`s, `loadActive`, both `load`s, `loadPrepared` and `revert` (:660-912); `importConfiguration`'s refusals and question (:1095-1146); `importLegacyGraph`'s capture, import and reload (:1247-1490); `save`.
- `TrainControlUI`:
  - the flag and its javadoc (:581-596); the exit save (:2500-2589);
  - `isSetupNewerThanTheRunningLayout`, `carryTheTrainsAcross`, `captureRunningLayout` and `resetAutonomySession` (:2965-3130);
  - `autonomyLoadedFromDiagram` and `prepareAutonomyReload` (:4063-4262);
  - `whereTheTrainsAre`, the pending-turn pair, `putTheTrainsBack` and `rebuildRunningLayoutFromSetup` (:6300-6780); `isLayoutEditorOpen` and `refuseWhileEditorOpen`;
  - `refreshAutonomyPrompt` and the banner's Load (:8674-8753); `unloadAutonomy` and `autonomySetupDeleted` (:8776-8825); `initializeTrackDiagram` and its three callers;
  - Edit Route and Add Route (:21631-21690, :23186-23235); `layoutEditingComplete`, `layoutRefreshComplete` and `layoutRefreshCompleteInternal` (:23309-23459); `isAutonomyBusy`; `reloadActiveDiagramConfiguration`; the page rename door (:25945-25985).
- `MarklinRoute`: `conflictingAccessoryAndReason`, `hasEmergencyStop`, `cutsThePowerAt`, `isOnlyStops`, `hasAStop`; the private `execRoute`'s guard (:711), `setExecuting` (:723), `askable` (:929) and Route branch (:1096-1130).
- Elsewhere:
  - `RouteEditorFrame`'s Save (:3290-3380); `AutonomyMenu` (:80-160, :294-320);
  - `AutonomyEditorPanel`'s per-square Remove, `bulkTools`, `placementChanged`, `setupChanged`, `rebuildRunningLayoutSoon` and `clearAllPlacements`, and what `menuOnly` gates; `LayoutRightclickAutonomyMenu.removeLocomotiveHere`;
  - `Layout`'s constructor, `isRunning`, `stopLocomotives`, `configureAndLockPath`'s reservation (:3784), `unlockPath` (:4097-4145), the early release (:8890-8930) and `moveLocomotive` (:9545-9696); `Point.reserve` and `toJSON`'s placement;
  - `MarklinControlStation`'s `hasAutoLayout`, `getAutoLayout`, `clearAutoLayout` and `parseAuto`; `AutonomySession.captureFromLayout` (:5337-5545), `placedLocomotives` and `importLegacy`'s standing-already seed; `AutonomyChecks.checkDuplicateLocomotives`; `AutonomyCompanionStore.save`;
  - `GraphReducer`'s point naming and `generatedName`; `AutonomyBuilder.uniqueNames` and `tilesByName`.

**Tests:** the five claims of `528fd305`; round 5's `testAReloadWhileAnEditWaitsKeepsWhereTheRunLeftTheTrains` and RLD4-C3's `testAnImportDoesNotFoldAnEditWaitingForItsRebuild`; `testADeclinedSetupEditSaysSoAndSurvivesTheExit`; `testNothingOnTheEventThreadTakesTheRailwaysMonitor`'s header and allowances; `testTheRoutesImportDoorAsksByName`'s changed wait; `testAStopRouteStandsAlone`'s class skip; build.xml's lines for the two claim classes.

**Documents:** behaviour.md :1968-1990, :2445-2462 and :2507-2510; open-questions.md :424-432; `messages.properties` :396, :400 and :1542.

**Mechanical checks, all in memory** (inline Python fed through a heredoc; nothing written):

- `docs/manual-tests/triage.db` opened `mode=ro&immutable=1`: row and ref counts, RLV6's twelve rows, GSR-B4 and the three deferrals, and a search of every row's title, disposition and evidence for the terms below;
- the live snapshot's `test/layouts/live-snapshot/config/autonomy/setup.json` and `configuration-Main.json`: its stations, whether each has a name of its own, and whether any two share one.

`docs/manual-tests/findings.tsv` and the store were searched before each finding.  The terms were: reserv, mid-run, mid-path, locked path, between sensors, reload while running, abandon the current run, route editor, saving a route, `layoutEditingComplete`, `prepareAutonomyReload`, two places, `placementsJustEdited`, CS3-C4 and DW-A1.  Only CS3-C4 and DW-A1 matched, and they are named below as parents; none of the findings below has a catalogued row of its own.  The prefix RLV7 is free.  Not reported, as the brief says: RLA3-B1 with RLU3-C4, OB-300 to OB-305, RLU2-C9, RLD-C1 and GSR-B4.

**Everything below is from reading.**  Each finding that needs a run says so, with the fixture and what proves or refutes it.

**Counts:** 1 A, 1 B, 5 C, 8 D.

**Nothing new above C.**  Every round-6 disposition does what it says at the doors it names (RLV7-D1 to RLV7-D4), and the past day's changes broke nothing above C.  The five Cs are new: four in `04b1b204` (three of them partials of RLV6-B1 or RLV6-C1 in narrow sub-cases) and one in `528fd305`.  By the brief's rule, this is the signal to stop for the past day's work.

**The A and the B are not new, and both need a run before anything is done.**  Both are older than the release review, and no round has named either.  Both sit at doors round 6 rewrote, and RLV6-B1 reasoned each away:

- **RLV7-A1** is at door (a): RLV6-B1 said *"the flag alone decides"*, and saving a route while autonomy runs loses every position without the flag.
- **RLV7-B1** is at door (c): RLV6-B1's control expected *"the flag false gives a new Layout and not busy"*, and with a train under way the ordinary reload is refused instead.

They are for Adam to rule on as older defects, once a run confirms them.

---

### RLV7-A1 - Saving a route while autonomy runs forgets where every train stands: the reload the Save sets off asks to abandon the run, Yes rebuilds the railway from the setup as it was before the run, and No leaves the run going with no configuration loaded to fold it into

| | |
|---|---|
| **Disposition** | Fixed - the route editor's Save no longer completes a diagram edit: a route is no page of the diagram, and its tiles follow it by id (rebindRouteTiles), so nothing of autonomy is reset and nothing is asked.  claims 631ebd2b (red first), fix 1ed10c7f; mutation X1 red. |
| **Grade** | A.  DW-A1's consequence (graded A): the running layout, and from the next fold the configuration, stand each train the run moved on the square it left.  Occupancy is placements, so Start can route another train into the block a train really occupies.  Reached by an ordinary gesture with no race: saving any route in the route editor while autonomy runs, whichever way the question is answered.  **Not new in the past day, and no earlier round's remainder:** older than the release review.  The reload after an edit dates from `b7b2ce2e` (2026-08-17), and `captureRunningLayout`'s running guard from `1a609444` (2026-08-24, DW-A1's fix).  RLV6-B1 (round 6) reasoned it away at this door, and `04b1b204` keys its carry on the flag alone. |
| **Names** | RLV6-B1 (door (a), and its premise); DW-A1, WKW-B2, OB-183, OB-047; Adam's ruling of 2026-09-04 (the editor doors do not stop the trains) |
| **Where** | **The Save:** `RouteEditorFrame.java:3378` (`parent.layoutEditingComplete()`, unconditional).  **The route editor opens during a run:** `TrainControlUI.java:21631-21690` and `:23186-23235` (Edit Route and Add Route ask only `refuseWhileEditorOpen`, which is the track-diagram editor: `:6252`, `:6796-6808`).  **The reset:** `:23436-23447` (`layoutRefreshCompleteInternal`: `resetAutonomySession()`, then `load(wasRunning, false)`); `:3099-3124` (`captureRunningLayout()`, then the diagram's monitor stopped `:3110`, the Auto tab greyed `:3118`, `activeDiagramConfiguration = null` `:3124`); `:3049-3057` (`captureRunningLayout` returns while `getAutoLayout().isRunning()`); `Layout.java:1795-1799` (`isRunning`: `running`, or any active train or thread).  **The question:** `TrainControlUI.java:4228-4256` (`confirmReloadJsonStopsRunningLocomotives`, default No).  **The load:** `AutonomyViewerPanel.java:775` (the carry asks the flag only), `:814` (`loadPrepared`'s capture needs an active name).  **The folds that could save it later:** `TrainControlUI.java:2534` (the exit) and `:3051` (`captureRunningLayout`), both needing an active name |
| **Needs execution** | yes - see the request |

**What happens.**  Nothing refuses the route editor while autonomy runs: only the track-diagram editor is refused (OB-047), and nothing refuses routes.  Its Save runs `layoutEditingComplete`, and `layoutRefreshCompleteInternal` then does three things.

1. `resetAutonomySession` calls `captureRunningLayout`, which returns because the layout is running.  Nothing the run did is folded.
2. The session and the active name are dropped, the diagram's monitor is stopped and the Auto tab greyed.
3. `load(wasRunning, false)` reaches `prepareAutonomyReload`, which finds autonomy busy and asks *"Locomotives are still running.  Reloading will stop them and abandon the current run.  Continue?"*.

Then, either way:

- **Yes.**  Every active train is set to speed 0, and `loadPrepared(wasRunning, false, true)` runs.  Its capture needs an active name, which step 2 removed, so the railway is rebuilt from the setup.  The setup holds the placements of the last fold, which was before the run.  Every train the run moved is stood back on its pre-run square, and the next fold writes that.
- **No.**  `load` returns.  The run goes on over a layout with no configuration name attached: the Auto tab is greyed and the diagram's monitor is stopped.  No door can fold it now, because the exit, `captureRunningLayout` and `loadPrepared` all need the active name.  So the next load, or the next start, rebuilds from the pre-run placements.  The loss is only deferred.

**What round 6 changed here.**  With the flag up, Yes now reaches `carryTheTrainsAcross`, which keeps every standing train (a train stopped mid-path is RLV7-C1).  So the rare case is now right, and the ordinary case is as it was.  RLV6-B1's door (a) says *"With the flag down, `captureRunningLayout` folds first and nothing is lost, so the flag alone decides."*  During a run the flag does not decide: `captureRunningLayout` also returns while the layout is running.

This door is also meant to leave the trains alone: Adam ruled on 2026-09-04 that the editor doors do not stop them.  During a run, this one asks to.

Door (a)'s claim runs idle, so no claim reaches this.

**Direction.**

- A route edit changes no page, so a route editor Save need not reset autonomy.  Either skip the reset and reload for it, or, while `isAutonomyBusy()`, keep the session and the name and reload when the run ends.
- Wherever a fold is skipped - because an edit waits, or because the layout is running - have the reload carry the trains, keyed on "the fold was skipped" rather than on the flag.
- Never null the active name without a fold or a carry.

**Verification request.**  Use door (a)'s claim's fixture: the live-snapshot sandbox, the window, Main running and idle.  Move one standing train with `moveAStandingTrain`, then set the running `Layout`'s `running` field true by reflection, so `isRunning()` answers true (after the move, since `moveLocomotive` refuses while running).  Leave the flag false.

- **Yes.**  Call `ui.layoutEditingComplete(done)` with `answeringYes`'s thread answering, and wait for `done`.
  - **Proves:** `getAutoLayout()` stands the train on its old square.
  - **Refutes:** it stands on the moved-to square.
  - **Control:** `running` false gives the moved-to square (the reset's fold).
- **No.**  The same, answering the second option.
  - **Proves:** `getActiveDiagramConfiguration()` is null while `getAutoLayout().isRunning()`; then, with `running` set false, `load(Main, true)` stands the train on its old square.
  - **Refutes:** the name is kept, or the train stands on the moved-to square.

---

### RLV7-B1 - A reload confirmed while a run is going folds each running train onto every point of its locked path: the configuration running is then refused as a locomotive "in two places" and autonomy stays busy, and a configuration chosen instead loads with the duplicates saved into the one left

| | |
|---|---|
| **Disposition** | Fixed - a load folds no layout that holds a path: while the running layout isRunning() after the reload's Yes, the load carries the trains across instead (the configuration running, and the one running first when another is chosen), and loadPrepared's fold asks isRunning() as the exit save and captureRunningLayout do.  claims 631ebd2b (red first), fix 1ed10c7f; mutations X2 red, X3 SURVIVED.  X3 is the fold's own guard, which load's carry leaves no way to reach while a path is held: kept as the siblings' rule, not proved. |
| **Grade** | B.  A reload the operator has confirmed is refused with *"This setup cannot be used yet"*.  The old layout stays, with its trains stopped and reading busy: Start and Graceful Stop stay dark until each train's awaited sensor fires, or a restart, and the restart resumes the last saved placements.  That is RLV6-B1's door (c) consequence, reached here with the flag down and no race.  Choosing another configuration instead writes the duplicates into the one left, which then refuses to load until each is cleared by hand.  Not A: no train moves, and the check's message names the square and the remedy.  **Not new in the past day:** older than the release review (`loadPrepared`'s capture is `load`'s, moved by `04b1b204` unchanged).  No round has named it.  Round 4 (RLU4-C1, RLA4-C1) and round 6 reasoned about this gesture as though the fold recorded where each train stopped; RLV6-B1's request (c) expected *"Control: the flag false gives a new Layout and not busy"*. |
| **Names** | RLV6-B1 (door (c), and its control); RLU4-C1, RLA4-C1; OB-143 |
| **Where** | **The fold:** `AutonomyViewerPanel.java:814-827` (it asks the flag, the active name and `isValid()`, not `isRunning()`), `:836-859` (a blocking problem: the dialog, then `revert`), `:878` (`save()` after a load that built); `:1120-1123` (RLU4-C1's reasoning: *"the reload stopped the trains and captured where they stopped"*).  **The folds that do ask:** `TrainControlUI.java:2537` (the exit) and `:3054` (`captureRunningLayout`).  **Yes releases nothing:** `TrainControlUI.java:4251-4256`; `Layout.java:1842-1845` (`stopLocomotives` clears `running` only); `:8486` (*"parked forever in waitForOccupiedFeedback"*).  **A locked path holds every point:** `Layout.java:3784` (`configureAndLockPath`: `e.getEnd().reserve(loc)` for every edge); `:4127-4141` (`unlockPath` empties them, the start included, only when the path ends); `Point.java:609-616` (`reserve`: *"only it should be able to say a locomotive is in more than one place at once"*).  **Folded as placements:** `Point.java:1280-1289` (`toJSON` writes `loc` for any occupant); `AutonomySession.java:5401-5410` (the capture writes it onto each point's square); `:2433-2456` (`placedLocomotives`: every square with a train); `AutonomyChecks.java:1260-1296` (`checkDuplicateLocomotive`, `Severity.ERROR`); `AutonomyCompanionStore.java:988-1007` (a save writes every configuration) |
| **Needs execution** | yes - see the request |

**Why every running train is in two places.**

1. A path is locked by reserving every point on it for the train.  The reservations go only when the path ends: in atomic mode the start as well, and in the other mode the points ahead.
2. Yes at `prepareAutonomyReload` clears `running` and sets each active train's speed to 0, but releases nothing.  A train stopped between sensors never reaches the sensor its thread waits for, so its path stays locked.
3. With the flag down, `loadPrepared` then folds the running layout.  `toJSON` writes the train on every point it holds - its start, the points between and its destination - and the capture writes each onto its square.

So every train under way stands in the configuration on two or more squares.

**What follows.**

- **The configuration running**, from the Autonomy menu's radio, or the reload after an import under another name.  The rebuild finds a `checkDuplicateLocomotive` error for each such train, and the dialog says the setup cannot be used yet.  `revert` leaves the old layout: stopped, and reading busy (RLV6-B1's analysis of that state).  The duplicates stay in the store in memory, for the next save to write.
- **Another configuration.**  The capture writes the duplicates into the configuration left, and the other builds.  `loadPrepared`'s `save()` then writes every configuration, the left one included.  Choosing it again is refused.

**The siblings.**  The exit and `captureRunningLayout` refuse to fold a running layout; `loadPrepared` is the one fold that does not ask.  Round 6's flag-up path at the same door does not fold - it carries (RLV7-C1) - so, ironically, it builds.

**Direction:** fold no layout that holds a locked path.  Ask `isRunning()` at `loadPrepared`'s capture, as the other two folds do, and carry the trains there by RLV7-C1's rule.

**Verification request.**  Use RLV6-B1's request (c) fixture: the live-snapshot sandbox with the model's sensor echo off, and Main running.  Dispatch one standing train on a path of one or more edges, so its thread waits on its first sensor, and assert `isAutonomyBusy()`.  Leave the flag false, and call `load(Main, true)` on the event thread with `answeringYes`.

- **Proves:** `getAutoLayout()` is the same object and `isAutonomyBusy()` is still true thirty seconds later.  The answers heard include `errorCannotBuildDetail` (or its One form), and Main in the store places the train on more than one square.
- **Refutes:** a new Layout.
- **The other door:** first import MT-491 as "MT-491 other", as door (b)'s claim does; then `load("MT-491 other", true)` with Yes.
  - **Proves:** the sandbox's `configuration-Main.json` places the train on two or more squares, and `load(Main, true)` afterwards is refused.

---

### RLV7-C1 - `carryTheTrainsAcross` reads a layout whose running trains hold locked paths: a train stopped mid-path is carried to whichever of its points the map yields last - its destination, or, where that point is no station, its pre-run square - and the door-(c) claim makes autonomy busy with no path at all

| | |
|---|---|
| **Disposition** | Fixed - Layout.getLastPointsReached: a train under way is read at the last station on its path whose sensor it tripped, or where it set off; TrainControlUI.whereTheTrainsAre reads that, and a train a released path no longer records is kept at it unless another train stands there.  Said in behaviour.md.  claims 631ebd2b (red first), fix 1ed10c7f, and 46474dca (the rule on a hand-built track); mutations X4 red, X5 red. |
| **Grade** | C.  The consequence can be DW-A1's: a train modelled on its destination while it stands in the block it set off from, which Start can then route another train into.  But it needs both the one-event race that raises the flag and a reload confirmed while trains are under way - two preconditions, two grades down, as RLV6 took one precondition one grade.  The operator has been told the run is abandoned, and no rule can know where a train stopped between sensors is.  What is wrong is that no rule is chosen.  **New in `04b1b204`**: at door (c), and at door (a) when a route is saved during a run with the flag up (RLV7-A1's Yes). |
| **Names** | RLV6-B1 (door (c), and its "Fixed"); OB-183; RLV7-A1, RLV7-B1 |
| **Where** | `TrainControlUI.java:2984-2986` (the javadoc: *"this runs whatever isAutonomyBusy says ... a train stopped between sensors keeps the old layout reading busy"*); `:2993` (`whereTheTrainsAre()`); `:6355-6372` (one entry per locomotive, over `layout.getPoints()` - `points` is a `HashMap`, so the last reserved point in its order wins); `:6494-6525` (`putTheTrainsBack`: `moveLocomotive` refuses a point that is no station, `Layout.java:9605-9612`, and the train stays where the setup put it); `:6667` (the rebuild this was modelled on never runs while busy, so `whereTheTrainsAre` had never met a locked path); `testTheImportDoorReadsAnOldFile.java:1514` (`stagingFlowActive`: busy, with nothing dispatched) |
| **Needs execution** | yes - see the request |

**Where the rule came from.**  The carry was made from `rebuildRunningLayoutFromSetup`, which runs only at rest.  There each train stands on one point, and `whereTheTrainsAre`'s map, locomotive to point, is exact.  The carry's own javadoc says it runs while autonomy is busy.

**What it meets there.**  On a layout with a locked path, the train is the occupant of every point on it (RLV7-B1), and the map keeps whichever the `HashMap` yields last:

- **its destination:** the new layout stands the train there, and the start - which it has barely left, or not left - reads free;
- **a point between that is no station:** `moveLocomotive` refuses it, and the train stays where the setup put it - with the flag up, the square it stood on before the run;
- **its start:** right, by luck.

**The claim.**  Door (c)'s claim makes autonomy busy with `stagingFlowActive`, dispatches nothing, and moves a standing train.  So it shows the reload is made, and cannot see this.

**Direction.**

- Choose a rule for a train holding more than one point, and apply it in `whereTheTrainsAre`.  Where it set off is the last place it was known to stand; the other honest answer is to take it off the railway and say so.
- Use the same rule for RLV7-B1's fold, say it in behaviour.md, and add a claim with a dispatched train.

**Verification request.**  Use RLV7-B1's dispatch fixture, with the flag raised by reflection.  Answer Yes to `load(Main, true)`.

- **Proves:** a new Layout, with the dispatched train on its destination or on its pre-run square, not on its start.
- **Refutes:** it stands on its start, by a stated rule.

---

### RLV7-C2 - The carry reads `running == null` as "the reset after an edit forgot the name", which is one of four doors that forget it: after a switch of layout folder it carries the previous railway's trains, by Point name, into the configuration loaded; after Unload or Delete it builds the empty Layout CS3-C4 warns of

| | |
|---|---|
| **Disposition** | Fixed - the name a reset forgets is remembered (forgottenByTheReset), and a load carries only the configuration running or that one; Unload, a deleted setup and a switch of railway forget the railway (the flag and the name); the carry, the rebuild, the diagram monitor and the autonomy panel's list ask getAutoLayoutIfLoaded rather than building a Layout.  The list was what built one after Unload - and at every start with nothing loaded - found by a stack taken where the model builds one (a1241d73, which corrects 1ed10c7f's message naming the monitor).  claims 631ebd2b (red first), fix 1ed10c7f, a1241d73; mutations X6 red, X7 red, X8 red, X9 red, X10 red, X12 SURVIVED.  X12 - reading nothing running as the one loaded - is masked by the three doors now forgetting the railway, which leaves no state where it matters. |
| **Grade** | C.  Both halves need the flag up at a layout switch or an Unload: the one-event race, with nothing loaded or rebuilt since.  The switch half moves a placement only where a station of the new railway has a name the old one used.  There it displaces the train the new configuration had placed, since `moveLocomotive` *"DISPLACES whoever was there"*.  Between two copies of the same railway, carrying is what OB-183 would want.  The Unload half leaves an empty Layout only when the load then fails, and nothing found reads that Layout harmfully.  **New in `04b1b204`.** |
| **Names** | RLV6-B1 (door (a)); CS3-C4; OB-183 |
| **Where** | **The test:** `AutonomyViewerPanel.java:777-787` (`running == null \|\| running.equals(name)`).  **The four doors that null the name** (`TrainControlUI.java:3124`): `:23438` (door (a), which reloads the same name); `:10740` (`initializeTrackDiagram`, from a folder switch or a new local layout - it leaves the old Layout in the model, since only `:8783` and `:8820` clear it); `:8785` (`unloadAutonomy`, after `clearAutoLayout`); `:8822` (`autonomySetupDeleted`, the same).  **The load that follows a switch:** `:8716-8752` (the banner offers Load for the new layout's configuration).  **The empty Layout:** `:2997` (`this.model.getAutoLayout()`, which builds a Layout when there is none - `MarklinControlStation.java:1009-1029` - where `TrainControlUI.java:24390-24391` says *"hasAutoLayout, not getAutoLayout ... must not bring a Layout into being to answer it"*); `:3007-3008` (`carried` false when the load fails, so that Layout stays).  **The displacement:** `Layout.java:9645` (`clearBlockExcept`) |
| **Needs execution** | yes - see the request |

**The inference.**  At door (a), a null active name means the reset has just forgotten the name of the configuration it is reloading, and carrying is right.  Three other doors also leave the name null, and a load after any of them now carries too.

**After a folder switch.**  `initializeTrackDiagram` resets the session and leaves the previous railway's Layout in the model.  The banner then offers Load for the new folder's configuration.  With the flag up, that load records where the previous railway's trains stood, by Point name, and puts each on a point of the same name in the new one, displacing whoever the new configuration had there.  The flag is lowered, and the next fold writes the result.

**After Unload or Delete.**  The model has no Layout.  `whereTheTrainsAre` and `takeThePendingTurns` ask `hasAutoLayout()` first; `runningBefore` does not, and builds an empty one.  If the load then fails, that empty, valid Layout stays, and `hasAutoLayout()` answers yes about nothing - the state CS3-C4's comment was written to prevent.

**Direction.**

- Carry only where the name forgotten is the one being loaded: pass `wasRunning` from `layoutRefreshCompleteInternal`, or remember it at the reset.
- Use `hasAutoLayout() ? getAutoLayout() : null` for `runningBefore`.
- Lower the flag at a folder switch and at an Unload, since the edit it guards belongs to a setup that is no longer loaded - or keep it, and say why.

**Verification request.**

- **Unload.**  Live-snapshot sandbox, window, Main running and idle.  Raise the flag; call `ui.unloadAutonomy()`; make Main unbuildable in the store (a second placement of a standing train through `setPointProperty`); call `load(Main, true)`, answering OK.
  - **Proves:** `getModel().hasAutoLayout()` is true, with `getAutoLayout().getPoints()` empty, and `getActiveDiagramConfiguration()` is null.
  - **Control:** the flag false gives `hasAutoLayout()` false.
- **Folder switch.**  Use two sandbox folders, the second a copy of the first with one train placed on a different station.  Raise the flag in the first, switch to the second through the window, and press the banner's Load.
  - **Proves:** the second's running layout has the first folder's train positions, with its own train displaced.
  - **Control:** the flag false gives the second's own placements.

---

### RLV7-C3 - Door (a)'s carry goes by Point name, and a page rename renames every point on the page that has no name of its own: with the flag up, a train standing on such a station goes back to its pre-run square - DW-A1's shape, which the capture before a rename covers only with the flag down

| | |
|---|---|
| **Disposition** | Open - deferred as OB-306.  A page rename's carry goes by Point name, and only a station with no name of its own is renamed with its page; every one of the 33 stations on his railway has a name of its own, and it also needs the one-event race that raises the flag. |
| **Grade** | C.  DW-A1's consequence (A), after the one-event race.  It also needs a train standing on a station with no name of its own, on the page renamed - or two stations of one name on different pages, whose " (2)" can change hands when the page order does.  **Not on his railway as frozen:** every one of the live snapshot's 33 stations has a name of its own, and no two share one.  **New in `04b1b204`**, a partial of RLV6-B1 at door (a): before it, with the flag up, this door lost every moved train; now it loses only these. |
| **Names** | RLV6-B1 (door (a)); DW-A1; OB-183 |
| **Where** | `TrainControlUI.java:25961` (`captureRunningLayout()` before `renameOrDuplicate`, which returns with the flag up at `:3073`); `:25974-25983` (the rename, then `layoutEditingComplete`); `:6316-6318` (`whereTheTrainsAre`: *"By POINT NAME rather than by square"*); `:6494-6496` (`built.getPoint(name)` is null, so the train is left where the setup puts it); `GraphReducer.java:940-944` and `:962-965` (a point with no authored name is called `page + " " + x + "," + y`); `AutonomyBuilder.java:1584-1630` (`uniqueNames` suffixes a repeated name in the order of `page:x,y`) |
| **Needs execution** | yes - see the request |

**Why the flag decides here.**  DW-A1's fix folds the running layout by square before the rename, so the store is re-keyed with every train where it stands.  With the flag up that fold returns, and the carry after the reload looks each train's point up by its old name.  A point with no name of its own is called after its page, so after a rename it is not found.  The train then stands where the setup - re-keyed, but from before the run - puts it.

**Direction:** at a rename, carry by square and copy, as the capture does, re-keyed through the rename - or rename the recorded names along with the page.

**Verification request.**  Use the live-snapshot sandbox.  Remove one station's entry from the sandbox setup's `pointNames`, open the window, and move a standing train onto that station on the running layout only.  Raise the flag, and rename that station's page through the window's own rename method.

- **Proves:** `getAutoLayout()` stands the train on its pre-run square.
- **Refutes:** it stands on the renamed station.
- **Control:** the flag false gives the renamed station.

---

### RLV7-C4 - Choosing another configuration while an edit waits, where the configuration running will not build with that edit: the log says it "could not be loaded at startup", its run's positions are dropped with nothing said, and the flag stays up over the configuration chosen

| | |
|---|---|
| **Disposition** | Fixed - choosing another configuration while an edit waits, where the one running cannot be used with that edit, is refused with autosetup.ui.errorCannotKeepTrainsBeforeChoosing (eight languages) and the count of what stops it; the one running stays loaded - with its errors, where it builds with them (SVN-B10) - the edit is kept, the flag stays up, and the internal load no longer logs "could not be loaded at startup".  Claim 247a5fd1 (red first), fix 60411a00; mutations X13 red, X14 SURVIVED.  X14 - the internal load logging a start-up's failure again - is not reached: the claim's edit is an error, and a setup with errors loads (SVN-B10), so that load does not fail; it fails only on a blocking problem, which a setup edit makes only through the graph. |
| **Grade** | C.  Three things RLV6 found are all back, in one sub-case: RLV6-B1 (b)'s *"the configuration left keeps its pre-run placements, silently"* and *"the flag stays up over a configuration whose running layout was just built from its own setup"*, and RLV6-C1's misnamed log line.  The sub-case is a declined edit that has left the configuration running unable to build.  It needs the race, an edit that breaks the build, and then another configuration chosen; his snapshot has one configuration.  **New in `04b1b204`**: the dispositions "Fixed" on RLV6-B1 and RLV6-C1 are partial here. |
| **Names** | RLV6-B1 (b), RLV6-C1; AMS-B1 |
| **Where** | `AutonomyViewerPanel.java:789-794` (`carryTheTrainsAcross(() -> loadPrepared(running, false, false))`, then `loadPrepared(name, interactive, captureRunningState)` whatever it returned); `:836-856` (not interactive: `autosetup.ui.infoResumeFailed`, *"could not be loaded at startup"*, `messages.properties:1542`, then `revert`); `:814` (the capture is skipped, since the flag is still up); `TrainControlUI.java:3007-3010` (`carried` false, so the flag stays); `:2540-2542` (the exit's `placementsNotSaved`); `:4186` (`infoLoadedConfiguration`) |
| **Needs execution** | yes - see the request |

**What happens.**  The carry of the configuration running is an internal step, so it is not interactive.  When the configuration will not build, four things follow.

1. The log says it could not be loaded at startup, about a load nobody started at start-up.
2. The old layout stays, `carried` is false, and the flag stays up.
3. The load of the configuration chosen skips its capture because of the flag.  So the positions the run gave the configuration left are never folded, and nothing says so.  The disposition's *"which is then folded as any configuration left is"* does not hold here.
4. The flag stays up over the configuration chosen.  Its exit saves no positions, and `captureRunningLayout` folds nothing, until some rebuild or carried load lowers it (AMS-B1's remainder).

Even when it builds, the log reads *"Loaded configuration Main"* just before the configuration chosen.

**Direction:** when the carry returns false, either fold the placements alone into the configuration left (RLV6-B1's first direction), or refuse the choice with a message until the edit is carried (RLU5-B2's).  Say an internal failure in words about the edit, not a start-up.

**Verification request.**  Use door (b)'s claim's fixture.  Before raising the flag, write into Main in the store something that stops it building (a second placement of a standing train, through `setPointProperty`), without rebuilding.  Move a train, raise the flag, and call `load("MT-491 other", true)`.

- **Proves:** the log has `infoResumeFailed` naming Main; Main in the store keeps the moved train on its old square; and the flag is still true once "MT-491 other" is running.
- **Refutes:** the flag is false, or Main has the train where it was moved.

---

### RLV7-C5 - None of round 6's claims has an edit waiting, and none asserts that the configuration still has the moved train on its old square: door (b)'s claim passes a fold of the stale layout, which is RLD4-C3's regression

| | |
|---|---|
| **Disposition** | Fixed - every claim with the flag up writes an edit to the file first (a declined edit is on the file), checks the configuration still has the moved train where it set off, and checks the edit after the load; door (a)'s claim went red on the helper that wrote to memory only, which a1241d73 corrects.  claims 631ebd2b (red first), fix 1ed10c7f, a1241d73; mutation X11 red. |
| **Grade** | C: claims that would not catch a regression of their own disposition's words.  Door (b)'s disposition is "the one running first, *with the edit* and where the trains stand".  Its claim reads where the train stands and the flag, and nothing is waiting to be lost.  RLD4-C3's claim, the only one with an edit, never reaches door (b): its second import reloads the configuration running.  **New in `528fd305`.** |
| **Names** | RLV6-B1; RLD4-C3; RLA5-B1 (its request's precondition) |
| **Where** | `testTheImportDoorReadsAnOldFile.java:1349`, `:1432`, `:1516`, `:1589` (the flag raised by reflection with no `setPointProperty` before it, unlike RLD4-C3's claim at `:1157` and `:1173`); `:1616-1645` (`moveAStandingTrain` moves on the running layout and asserts nothing about the configuration); `:1438-1448` (door (b)'s assertions); `AutonomyViewerPanel.java:789-791` |
| **Needs execution** | yes, for the mutation |

**The fold it would pass.**  Suppose door (b) were fixed by lowering the flag and falling through to the ordinary fold.  Main would then have the moved train where the run left it, and the flag would be down, so door (b)'s claim is green.  But that fold writes the layout built before the edit back over the edit - which is what RLD4-C3 was fixed for.  At doors (a) and (c), a fold at the reset or at `loadPrepared` is caught only where it also leaves the flag up.

**The missing precondition.**  RLA5-B1's request asked to *"Assert as a precondition that the configuration still has it at its old square"*.  None of the four claims does.  If `moveLocomotive`'s callbacks ever wrote the placement into the setup, the three claims that read the running layout would pass on a plain rebuild from the setup.  They were red at `806e81eb` (RLV7-D1), so they are real claims today.

**Direction:** in each claim, write a priority with `setPointProperty` before raising the flag, as RLD4-C3's claim does, and assert it after the load; and assert the configuration's old square before the load.

**Verification request.**  Mutation: at `AutonomyViewerPanel.java:791`, lower the flag and fall through instead of carrying.

- Run door (b)'s claim.  **Proves:** green.
- Then add a priority to Main through `setPointProperty` before the flag is raised.  **Proves:** under the mutation the priority is gone after the load; without it, the priority stays.

---

### RLV7-D1 - RLV6-B1 at the doors it names, at rest: every load while an edit waits carries each train across, keeps the operator's `interactive`, asks once, and lowers the flag only on a replaced valid layout; each door's claim is red at `806e81eb` by reading

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLV6-B1; RLA5-B1, RLD5-B1, RLU5-B2 |
| **Where** | `AutonomyViewerPanel.java:759-795`; `TrainControlUI.java:2991-3018` against `:6726-6777`; `testTheImportDoorReadsAnOldFile.java:1321`, `:1391`, `:1480` |
| **Needs execution** | no |

**The carry is the rebuild's.**

- `whereTheTrainsAre` and `takeThePendingTurns` come before the load, and `putTheTrainsBack(standing, null)` after.
- The turns are put back in a `finally`.
- The flag is lowered by the same test: a different, valid layout.

**Each door.**

- **Door (a):** the name is null and the load is `wasRunning`.
- **The configuration running:** carried directly.
- **Door (b):** the one running is carried first, not interactively; then the one chosen, whose capture is now allowed.
- **Door (c):** after the one question.  `loadPrepared` asks nothing, and has no busy guard.
- **No recursion:** `loadPrepared` never calls `load`, and the rebuild's own load passes `captureRunningState` false.

**The claims, at `806e81eb`, by reading.**  Each named mutation undoes its fix.

- **(a):** the name is not the active one, so the branch is skipped; the build from the setup gives the old square.  Red.
- **(b):** the branch is skipped and the capture is too, so Main keeps the old square.  Red.
- **(c):** the rebuild refuses while `stagingFlowActive`, so the Layout is the same.  Red.

**The records.**  `captureRunningLayout`'s javadoc (`TrainControlUI.java:3068-3072`) and behaviour.md :2459-2462 say what the code does.

**RLD5-B1's aside, checked with it:** with the flag up, the import's lists are counted against the pre-run placements.  A run moves trains without adding or removing any, and the diagram's Place writes the setup as well as the running layout.  So the set of trains standing is the same, and every train is classed as it would be.  Only which of the file's squares are passed over silently, by occupancy, differs, as it always has.  Not a finding.

### RLV7-D2 - RLV6-C1: a reload the operator asks for while an edit waits stops a train driven by hand, and shows the dialog when the setup will not build; the editor door stays quiet

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLV6-C1; VD11-A2; Adam's ruling of 2026-09-04 |
| **Where** | `AutonomyViewerPanel.java:783`, `:841-854`, `:882`; `TrainControlUI.java:4112-4115`; `AutonomyMenu.java:313`; `TrainControlUI.java:25059`, `:23446`; `testTheImportDoorReadsAnOldFile.java:1556` |
| **Needs execution** | no |

**The doors.**

- The radio and a page tick pass `interactive` true.  So `autonomyLoadedFromDiagram(name, false)` stops every locomotive, and a refused build shows `errorCannotBuildDetail`.
- The editor door passes false, which is Adam's ruling of 2026-09-04.

**The claim.**  It sets a speed of 40 by hand, with autonomy idle so that nothing else stops the train, and reads 0 a second later.  At `806e81eb` the quiet rebuild left it at 40.

### RLV7-D3 - RLV6-C2: `cutsThePowerAt` keeps the path, ends by its limit, and answers what firing does; the claim is red under the old set

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLV6-C2; RLA5-A1; DIR-A3 |
| **Where** | `MarklinRoute.java:435-481`, `:711`, `:723`, `:1096-1130`; `testAStopRouteStandsAlone.java:256` |
| **Needs execution** | no |

**The walk ends.**

- Every step lowers the limit.
- Below 0, a route that is not stop-only answers false at once, and a stop-only route answers true at once.
- So it goes at most three levels, and dropping the shared set cannot run away; a loop ends on the path set.

**It answers what firing does.**

- The runtime fires each Route command on its own, refuses below 0 before `setExecuting`, and skips a route that fires itself.
- So a route reached by two paths runs its stop from the shorter, as the walk now says.

**No new question.**  The change only adds true answers, so it takes questions away and never adds one.

**The claim** builds [stop], [Route stop], [Route split] and [Route longer, Route split].  Under the shared set, the direct Route command to split meets the split already seen, and answers false.  Red.

**No sibling walk shares a set:** `git grep` of both `marklin` classes and `gui/`.

### RLV7-D4 - RLV6-C3 and the round's records: the comma, the sentence named for what it follows, the counts, and the twelve rows

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLV6-C3; RLD5-C4 |
| **Where** | open-questions.md :426-430; behaviour.md :2459-2462 and :2510; findings.tsv (twelve RLV6 rows); `docs/manual-tests/triage.db` |
| **Needs execution** | no |

**The sentences.**  The comma is restored, and *"Before the import went into the configuration named"* says what "Until then" meant.

**The store,** read with `mode=ro`:

- 4,728 rows for 4,371 refs, as behaviour.md and open-questions.md say.  The additions listed sum to it: 4,716 + 12.
- RLV6's twelve rows are Closed, with dispositions as RLV6.md has them.
- RLA5-C3, RLU5-C7 and RLD5-C2 are Open - deferred, and GSR-B4 is Open.
- findings.tsv renders the same twelve rows.

### RLV7-D5 - The two other test changes: the census allowance follows the capture into `loadPrepared`, and the routes-import wait tolerates `editRoute`'s delete-and-re-add without hiding a failure

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | OB-192 (the census's reason) |
| **Where** | `testNothingOnTheEventThreadTakesTheRailwaysMonitor.java:242-246`, `:266`; `testTheRoutesImportDoorAsksByName.java:422-425`, `:431` |
| **Needs execution** | no |

**The census.**  `load` itself now reaches no `synchronized` `Layout` member.  `carryTheTrainsAcross` reads `getPoints` (unsynchronized) and calls `putTheTrainsBack`, which is already allowed (`:266`).  The `toJSON` read moved with the capture into `loadPrepared`.

**The wait.**  `editRoute` deletes the route and adds it again, so `getRoute` answers null for a moment.  The loop now waits through that, and a route that is never armed again still fails at the precondition a few lines on (*"no route has automatic firing on to export"*).  The moment itself is `editRoute`'s, and older than this scope.

### RLV7-D6 - Round 6 touched no bundle, no route door and no split: no message is missing or different in any language, no stop stops firing, and no question appears

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | Adam's rulings of 2026-09-25; RLV6-D2, RLV6-D3, RLV6-D4, RLV6-D8 |
| **Where** | `git diff --stat 806e81eb..ab8aa1a9 -- src/` (three files: `AutonomyViewerPanel`, `TrainControlUI`, `MarklinRoute`); `MarklinRoute.java:454-481` |
| **Needs execution** | no |

**The bundles.**  No properties file changed, and the carry adds no message.

**The routes.**  `MarklinControlStation`, `RouteEditorFrame` and `CommandRow` are as round 6 read them.  So the split, the editor's refusal and the doors that make routes stand as RLV6-D2, D3, D4 and D8 found them.  In `MarklinRoute` only the question changed, and only toward "cuts the power" (RLV7-D3); `execRoute` is untouched.

**The stops and questions.**  The interactive loads the carry makes stop the trains exactly where the same loads with nothing waiting do.  `prepareAutonomyReload` is asked once per load.

### RLV7-D7 - The carry names no placement the declined gesture edited, and that is right: considered, and not a defect

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | D2-A1, W7-A1, OB-183, OB-047 |
| **Where** | `AutonomyEditorPanel.java:9456-9460` (the names are drained before the rebuild, which may then be declined); `:10680-10725` (Clear Every Locomotive, in Bulk Tools, which `menuOnly` does not remove - so it is on the diagram's autonomy menu too, `TrainControlUI.java:4732`); `:1364-1374` (the per-square Remove, which is the editor's only); `LayoutRightclickAutonomyMenu.java:1567-1600` (the diagram's Remove takes the train off the running layout first); `TrainControlUI.java:3005`; behaviour.md :1985-1990 |
| **Needs execution** | no |

**Which edit can meet the race.**  Only Clear Every Locomotive, from the diagram's Bulk Tools, can be declined:

- the editor's doors cannot, because no run starts while the editor is open (OB-047);
- the diagram's Remove writes the running layout itself.

**What happens to it.**  Declined, its names are dropped, and the next carry puts the trains back - as the next rebuild has since D2-A1.

**Why that is right.**  The run that declined the Clear started on the running layout, which still had those trains, and drove them.  Where they stand now is newer than the Clear.  behaviour.md's exception - *"for those trains the setup IS the newer answer"* - assumes no run between.

### RLV7-D8 - The final battery must show round 6's five claims passed, not skipped

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLD5-D8 (the same request for round 5's claims) |
| **Where** | `testTheImportDoorReadsAnOldFile.java` (the four window claims skip without a display); `testAStopRouteStandsAlone.java:52-57` (the class skips without its own copy of the data, which is how `ant test` and a NetBeans run meet it); build.xml :486 and :542 |
| **Needs execution** | yes - the release's final battery |

**Verification request.**  From the release's final battery, read the per-method status of:

- `regression.testTheImportDoorReadsAnOldFile`: `testAnEditCompletedWhileAnEditWaitsKeepsWhereTheRunLeftTheTrains`, `testChoosingAnotherConfigurationWhileAnEditWaitsKeepsWhereTheRunLeftTheTrains`, `testAReloadConfirmedWhileBusyAndAnEditWaitsIsMade` and `testAReloadAskedForWhileAnEditWaitsStopsTheTrainsAsAnyOtherDoes`;
- `core.testAStopRouteStandsAlone.testAStopReachedByTwoPathsIsCounted`.

**Proves:** every one passed.  **Refutes:** any one skipped.
