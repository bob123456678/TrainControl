# RLA5 - Autonomy lane, round 5: validating the dispositions of RLA4, what round 4's fixes left, and the emergency-stop ruling of 2026-09-25

**Status:** open

**Prefix:** RLA5

**Reviewed:** branch `autonomy-diagram-r0` at `ebf4f919`. The working tree was clean (`git --no-optional-locks status --porcelain` printed nothing). Reviewed read-only on 2026-09-26.

## Method

I read the brief, `docs/reviews/README.md` for the shape, my lane's round-4 document `docs/reviews-2026-09-25/RLA4.md` whole, and `RLU4.md` and `RLD4.md` whole, since round 4's fixes for RLU4-B1, RLU4-C3 and RLD4-C3 landed in this lane's files. I read with `git show` the three round-4 commits - the claims `6f02ff2e` and the fix `d5b97ee8` in full, and the records `4ae6bb51` by its stat, its message and the rows it wrote - and the four emergency-stop commits in full: claims `19d82501`, fix `894c52c8`, test `92a0f20e` and tracker `ebf4f919`.

Around each change I read the code at HEAD:

- **`AutonomyViewerPanel`:** `importConfiguration` whole, `importLegacyGraph`'s capture and reload, both `loadAfterImport`s, `loadActive`, both `load`s and `revert`.
- **`TrainControlUI`:** the flag `setupEditDeclinedDuringRun` and its javadoc, `isSetupNewerThanTheRunningLayout`, the exit save's fold (:2520-2570), `captureRunningLayout` and its javadoc, `resetAutonomySession`, `rebuildRunningLayoutFromSetup` (:6540-6730, including where the flag is set and the one place it is cleared), `prepareAutonomyReload`, `autonomyLoadedFromDiagram`'s head, `isAutonomyBusy`, `reloadActiveDiagramConfiguration`, `layoutRefreshCompleteInternal`'s reload (:23370-23410), `deleteRoute`, `duplicateRoute`, `writeRouteEnabledState`, `emergencyStopTriggered` and Routes > Import's message.
- **`AutonomyEditorPanel.rebuildRunningLayoutSoon`**, for how the flag is raised. **`AutonomyMenu`**'s Configuration submenu.
- **`MarklinRoute`:** `conflictingAccessoryAndReason`, `hasEmergencyStop`, `hasAStop`, `mixesAStop`, both public `execRoute` doors, `execRouteOverridingConflicts`, and the private `execRoute` whole (the recursion guard, the per-command loop, the stop branch, the chained-route branch, the pause after each command); the s88 monitor's trigger.
- **`MarklinControlStation`:** the start-up restore and its split (:440-500), the sync's route loop (:1490-1565), `editRoute`, the three `newRoute`s, `isLocalRouteId`, `execRoute(String)`, `deleteRoute`, `importRoutes`, `splitRoutesThatMixAStop` and `getRoutesSplitByLastImport`.
- **`RouteEditorFrame.everythingWrong`**; `CommandRow`'s STOP and ROUTE mapping; `RouteCommand`'s line form and parse for a Route command; `Route`'s mutators; `GraphLocAssign.menuLabelFor`; `Layout.whyItReachesNoStation`; `AutonomySession.captureFromLayout` (the key loop) and `Point.toJSON`'s priority.

Tests read: the round-4 methods `6f02ff2e` added or changed in `testTheImportDoorReadsAnOldFile` (the bare-configuration refusal, the refusal while autonomy runs, the declined-edit import, the in-use claim's priority and question, and the helpers `priorityTheFileCarries`, `aStationTheFileGivesNoPriority` and `priorityIn`), `testWhyStuck`'s WS13 claim and the either-way change, and the message-owner census; `core.testAStopRouteStandsAlone` whole; the changes to `ui.testARouteOverATrainAtItsDoors`, `ui.testCommandTableMarks`, `regression.testARouteDoesNotThrowSwitchesUnderATrain` and `core.testRoutes`; `testCancelUndoesAutonomyEdits`' three declined-edit claims; `regression.testEveryTestIsInTheBattery` and `regression.testEveryScenarioIsUsedAndSaysSo`; `build.xml`'s test list and `docs/tools/battery.sh`'s class loop.

Records read: behaviour.md :705, :2036-2040, :2186-2201 and :2431-2456; `Automation.md` :283 and :309-316; `AutomationAPI.md` :522-530; open-questions.md :39-43 and :236-248; issues.md OB-303 and the GS list (:690-720); tests.md MT-507 and MT-508 with the ledger; Readme.md :114 and :398-402.

Mechanical checks, all in memory - inline Python fed through a heredoc, reading `git show` output, nothing written to disk:

- The eight bundles at `f7e94a50`, `4ae6bb51` and HEAD: key sets, duplicates, `{n}` placeholder sets against English, bytes above 127, empty values, and straight apostrophes in values that carry a placeholder. I read the two keys round 4 added, the three it changed and the five the emergency-stop fix added, in all eight languages.
- The orphaned-javadoc rule of `testJavadocsAreAttached.orphansIn`, over every `src/` file at the same three revisions.
- His frozen routes, `test/layouts/live-snapshot/config/gleisbilder/routes.json` (the fixture copy, not `cs2_sample_layout/`), and `test/TC_routes.json`: every route with a stop, every Route command, and the ids.
- `docs/manual-tests/triage.db`, opened read-only (`mode=ro&immutable=1`), for the status notes round 4 says it wrote and the round-4 rows of this lane.

Every finding below was searched for in `docs/manual-tests/findings.tsv` first (recursion, chain, the declined-edit flag, `putTheTrainsBack`, `layoutRefreshComplete`, deleting a chained route, the route id floor). Only RLA5-C3 has a catalogued parent, GSR-B4, and it says so. The prefix RLA5 is free in the store. As the brief says, I do not report RLA3-B1 with RLU3-C4, OB-300 to OB-305, RLU2-C9 or RLD-C1.

Nothing was compiled or run. No JVM was started, no git state changed, and nothing under `cs2_sample_layout/` was read or written. This file is the only one written.

**Everything below is from reading.** Each finding that needs a run says so, and says what proves or refutes it.

Counts: 1 A, 1 B, 6 C, 9 D.

**This is not the steady state.** Every one of RLA4's nine dispositions holds (RLA5-D1 to RLA5-D5). But both findings above C are new, and each comes from a change made since round 4:

- **RLA5-A1** is from the emergency-stop split (`894c52c8`). A route that fires a split route through a Route command no longer cuts the power.
- **RLA5-B1** is from round 4's fix for RLD4-C3 (`d5b97ee8`). `load` now skips its capture while a declined edit waits, and puts no train back.

Of the six Cs, five are new: one is from round 4's refusal message and four are from the split and its claims. RLA5-C3 is a remainder of GSR-B4, which the split gives a stop to lose.

---

### RLA5-A1 - A route that fires, through a Route command, a route the split has made no longer cuts the power: the stop now sits one chained route deeper, and a chained route may fire no further

| | |
|---|---|
| **Disposition** | Fixed - a route that is only a stop runs at any depth of a chain, since it fires nothing further, and hasEmergencyStop follows the chain as far as firing it reaches.  claims ec947f34 (red first), fix 0f62a7ca; mutations W1 red, W2 red. |
| **Grade** | A |
| **Names** | Adam's ruling of 2026-09-25 (MT-507, MT-508: *"routes with an emergency stop should still fire"*); SVN-A4 and behaviour.md's *"never has its stop skipped"*; DIR-A3; `894c52c8` |
| **Where** | `MarklinControlStation.splitRoutesThatMixAStop` :4373-4428 (:4400-4401: the stop moves out, a Route command takes its place); `MarklinRoute.execRoute(boolean)` :332-335 (every door starts at limit 1); `MarklinRoute.execRoute(boolean, int, boolean)` :656-665 (`if (recursionLimit < 0) { logf("route.recursionLimitReached"); return; }`) and :1080 (`r.execRoute(auto, recursionLimit - 1, false)`); `hasEmergencyStop` :434-449 (*"One level, as a chained route runs one"*); `messages.properties:864` (`route.ui.frameStopStandsAlone`); behaviour.md :2188-2201; Readme.md :400-401 |
| **Needs execution** | yes - see the request |

Every door that fires a route starts it at a recursion limit of 1: the route list, the tile, the sensor, the override and the API. A route fired by a Route command runs at the limit less one, and a route whose limit is below 0 is refused with a log line (*"Route {0} not firing; recursion limit reached."*). So a route may fire one other route, and that one may fire none.

The split moves a route's stop one route deeper. Take a route A whose Route command fires X, where X is `[switch 39, stop]`:

- **Before the split:** A runs at limit 1 and X at 0. X's stop is X's own command, so it goes out and the power is cut.
- **After the split:** X is `[switch 39, Route -> "X (Emergency Stop)"]`. A runs at limit 1 and X at 0, and X's Route command calls the stop route at -1, which is refused. The power stays on, and no notice is shown. The log line is the only trace.

`hasEmergencyStop` says in its own javadoc that a chain runs one level, and counts a stop one level down for that reason. Nothing considered that the split adds a level to every chain that already reached X.

Nothing else about A changes. A's own `hasEmergencyStop` was false before (X's stop was not A's command) and is false now (X has no stop of its own), so the questions are as they were. What is lost is the power cut.

**Reach.**

- **On his railway as frozen, none.** The snapshot's `routes.json` holds 85 routes and not one Route command. His one mixed route, *Auto Emergency Stop Bottom Secondary*, is fired by its sensor, 2012, directly. Its stop then runs at depth 1, within the limit, and `ui.testARouteOverATrainAtItsDoors` shows it going out.
- **On any database with a route that fires a mixed route.** The editor allowed building one until this commit, and the split runs on every start.
- **And going forward, by the program's own advice.** The editor's refusal tells the user to *"Put the stop in a route of its own, and fire that route from this one with a Route command"*. Fired from any other route, the result is exactly this shape.

behaviour.md :2188 says *"An emergency stop is obeyed whatever else is true ... never has its stop skipped"*, and the Readme says *"its stop is always sent"*. Graded A, because a stop that does not go out is the consequence the ruling, SVN-A4 and DIR-A3 exist to prevent, and wherever the shape exists it happens on every firing.

**Direction.** Either of these:

- let a stop-only route run whatever the depth - the limit guards against loops, and a stop-only route fires nothing further;
- or have the split write the stop so that it runs at X's own depth.

Either way, add a claim with A firing a split X.

**Verification request.** Use `core.testAStopRouteStandsAlone`'s fixture, the run's own data copy.

- **Set-up:**
  - make feedback 48451;
  - restore X mixed, through `newRoute("X", id, [accessory 39, stop], 48451, ...)`;
  - make A through the user door, with `[Route X]`;
  - call `splitRoutesThatMixAStop()`, then `go()`, and assert as a precondition that the power is on;
  - `execRoute("A")`, then poll `getPowerState()` for three seconds.
- **Proves:** the power is still on, and the log has `route.recursionLimitReached` naming *X (Emergency Stop)*.
- **Refutes:** the power is off.
- **Control:** `execRoute("X")` on its own cuts the power.

---

### RLA5-B1 - RLD4-C3's fix makes `load` skip its capture while a declined edit waits, and `load` puts no train back: choosing the configuration running, ticking a page out, or any import then rebuilds the railway with each train where it stood before the run - silently, and again at every reload after, because `load` never lowers the flag

| | |
|---|---|
| **Disposition** | Fixed - a reload of the configuration running while a declined edit waits rebuilds as the editor doors do, carrying every train across, and lowers the flag.  claims ec947f34 (red first), fix 0f62a7ca; mutation W3 red. |
| **Grade** | B - DW-A1's consequence (a train modelled on a square it has left, so Start may route another into its block), reached only after the one-event race that raises the flag; RLD4-C3 was graded a step down for the same race |
| **Names** | RLD4-C3 (*"the capture before an import and `load`'s capture ask whether a declined edit waits for its rebuild"*, `d5b97ee8`); OB-183 (Adam, "option 1": where a train IS is a fact), OB-144, DW-A1, AMS-B1, ACC-B3, WKW-B2 |
| **Where** | `AutonomyViewerPanel.load` :776 (the capture skipped while `isSetupNewerThanTheRunningLayout`), :796-831 (rebuilt from the setup, nothing put back, then `save()`); `loadAfterImport` :687-705; `importLegacyGraph` :1286 and :1428; `TrainControlUI` :596 (the flag), :6682-6724 (`rebuildRunningLayoutFromSetup`: `whereTheTrainsAre` / `putTheTrainsBack`, and the only line that lowers the flag), :2540 (the exit save says `placementsNotSaved`), :25005-25016 (`reloadActiveDiagramConfiguration`), :23402 with :3055-3080 (the reload after a diagram edit - the same shape, older than this round); `AutonomyMenu.java:313` (the radio); `testAnImportDoesNotFoldAnEditWaitingForItsRebuild` (no train moves) |
| **Needs execution** | yes - see the request |

`setupEditDeclinedDuringRun` marks a setup that is newer than the running layout: an edit whose coalesced rebuild found a run had started. While it is up, every fold of the running layout into the setup is skipped. The design pairs each skip with one of two things:

- **A rebuild that carries the trains across.** `rebuildRunningLayoutFromSetup` takes `whereTheTrainsAre` before and calls `putTheTrainsBack` after. `captureRunningLayout`'s comment says the same: *"where the trains stand is carried across rebuilds by `putTheTrainsBack` rather than written"*.
- **A loss that is said.** The exit save logs `placementsNotSaved`.

Before `d5b97ee8`, `load` folded regardless. That kept every train's position and lost the edit (RLD4-C3). Now it skips the fold, rebuilds from the configuration - which holds the positions from before the run, since nothing folds when a run ends - and has no put-back. It keeps the edit and loses every position the run changed, and it says nothing.

`rebuildRunningLayoutFromSetup`'s own javadoc names this outcome. Deferring the rebuild past a run *"is the obvious fix and is worse than the defect ... put each one back where it started. That is `OB-144`"*.

**And `load` never lowers the flag.** Only `rebuildRunningLayoutFromSetup` does (AMS-B1). A `load` that has just built the railway from the setup, edit included, leaves it up. So until the next setup gesture rebuilds, every later reload of the running configuration does this again, and the exit save keeps skipping.

**The doors that reach it**, all with autonomy idle:

- **The Autonomy menu's radio for the configuration running**, which calls `load(name, true)`.
- **A page ticked out of autonomy** (`reloadActiveDiagramConfiguration`).
- **Every import:**
  - into another name, the configuration running is reloaded with a capture requested (`configurationToLoadAfterImport`);
  - an old file into the configuration in use gets `captured = false`, so `loadAfterImport(into, true)`.

The reload after a track-diagram edit (:23402) has had the same shape since WKW-B2, and is older than this round. `captureRunningLayout` skips there, and `resetAutonomySession` nulls `activeDiagramConfiguration`, so `load` captures nothing either. It is not catalogued; a fix should cover it too.

**On the railway.** The running layout, and after `save()` the file, stand each train the run moved back on its pre-run square. Occupancy is placements, and `isPathClear` never asks the s88. So the squares the trains really stand on read free, and the next Start may route a train into one (DW-A1).

**Reach.** The flag goes up only when an edit's rebuild, posted one event later by `rebuildRunningLayoutSoon`, meets a Start or a staging run. Then a run has to move trains, and one of the doors above has to come before any setup gesture.

**Is it new?** Yes, at `load`'s doors: it came with `d5b97ee8`. RLD4-C3's verification request and its claim move no train, so neither could see it.

**Direction.** Two changes, and the same for the reload at :23402:

- in `load`, when the flag is up and the configuration is the one running, carry the placements across as `rebuildRunningLayoutFromSetup` does (`whereTheTrainsAre` before, `putTheTrainsBack` after);
- lower the flag once a load has replaced the layout with a valid one, as AMS-B1 does.

**Verification request.** Use `testAnImportDoesNotFoldAnEditWaitingForItsRebuild`'s fixture: the live-snapshot sandbox, the window, his configuration running, autonomy idle.

- **Set-up:** move one standing train on the running railway to an empty station, as the in-use claim does, without writing it to the configuration. Assert as a precondition that the configuration still has it at its old square. Set `setupEditDeclinedDuringRun` true by reflection.
- **Then, one at a time:**
  - (a) `getAutonomyViewerPanel().load(inUse, true)` on the event thread, which is the radio's call;
  - (b) import MT-491 from the menu under a new name.
- **Proves:** after each, `ui.getModel().getAutoLayout()` stands the train on its old square, and the flag is still true.
- **Refutes:** the train is on the square it was moved to.
- **Control:** with the flag false, it is on the moved-to square.

---

### RLA5-C1 - RLA4-C1's refusal says "Cannot edit auto layout while running.": it names neither the import nor a remedy, in all eight languages

| | |
|---|---|
| **Disposition** | Fixed - the refusal names the import and what to do instead, in eight languages.  claims ec947f34 (red first), fix 0f62a7ca; mutation W8 red. |
| **Grade** | C - cosmetic: nothing moves, and the operator is told no without being told what would work |
| **Names** | RLA4-C1, RLU4-C1, RLD4-C1 (direction: *"naming the remedy (stop autonomy first, or import under another name)"*) |
| **Where** | `AutonomyViewerPanel.java:1086-1091`; `autolayout.errorCannotEditWhileRunning` (`messages.properties:252` and the seven translations); compare `autosetup.ui.errorImportIntoConfigurationInUse` :1532 |
| **Needs execution** | no |

The refusal is right, and it sits where it should: after the file is read, before the question, and owned by the main window. Its words are Delete's - *"Cannot edit auto layout while running."* - in the old "auto layout" term, and the operator has just chosen Import, not an edit.

Two remedies work, and the message gives neither: stop autonomy and import again, or import under another name now. An import under another name is allowed while autonomy runs. The in-use refusal beside it, `errorImportIntoConfigurationInUse`, does name its remedy.

RLA4-C1's direction offered Delete's key, and RLD4-C1's direction asked for the remedy. The fix took the first.

**Direction:** a key of its own - for example, "{0} is the configuration in use and autonomy is running, so nothing was imported.  Stop autonomy and import it again, or import the file under another name." - in eight languages.

---

### RLA5-C2 - `core.testAStopRouteStandsAlone` is not in `build.xml`: `ant test` does not run it, and `regression.testEveryTestIsInTheBattery` is red from `19d82501` on

| | |
|---|---|
| **Disposition** | Fixed - in build.xml, and in the live snapshot's Used by, which the class now reaches through Scenario.folderFor (ec947f34). |
| **Grade** | C - bookkeeping: the release's battery has a red that is not a defect in the program, and `ant test` leaves out the split's claims |
| **Names** | DD-A2 (why the guard exists); `19d82501`, `894c52c8` (*"green with the route classes, the bundles, the javadoc ratchet and the event-thread census"* - not this guard) |
| **Where** | `test/core/testAStopRouteStandsAlone.java`; `build.xml` (no `<test-one-class class="testAStopRouteStandsAlone"/>` - `git grep` finds the name only in behaviour.md and two test javadocs); `test/regression/testEveryTestIsInTheBattery.java` (`testTheBatteryRunsEveryTestClass` lists every class under `test/` that carries `@Test` and is not in `build.xml`); `docs/tools/battery.sh:606` (finds classes by file, so it runs the class and the guard both); `test/layouts/live-snapshot/README.md` "Used by" |
| **Needs execution** | yes - see the request |

The class carries `@Test` and has no `build.xml` line. So:

- **The battery's guard fails naming it.** `testEveryTestIsInTheBattery` lists every `@Test` class under `test/` that `build.xml` does not name.
- **`ant test`, and a NetBeans test run, never run it.** It would skip there anyway without the data-folder property. `battery.sh` does run it, since it finds classes by file.

Separately, the class reads the live-snapshot's `routes.json` by path. `testEveryScenarioIsUsedAndSaysSo` counts only a quoted `"live-snapshot"` as a user, so the scenario's "Used by" list does not name the class. That matters at the next refreeze. Once 3.0.0 has run the split on his railway, the refrozen `routes.json` will hold his route already split: the import will split nothing, and the claim will fail on `getRoutesSplitByLastImport`. Nothing warns whoever refreezes it.

**Direction:** the `build.xml` line; and name the class in the README's "Used by", or read the file through `support.Scenario` so the guard sees it.

**Verification request.** Run `regression.testEveryTestIsInTheBattery`.

- **Proves:** `testTheBatteryRunsEveryTestClass` is red, naming `testAStopRouteStandsAlone`.
- **Refutes:** it is green.

---

### RLA5-C3 - The split makes a mixed route's power cut a second route that can be deleted on its own; the route that fires it then names nothing and cuts nothing - GSR-B4's defect, now on a stop

| | |
|---|---|
| **Disposition** | Follow-up - GSR-B4 (open): a route another route fires can be deleted on its own, and the split's stop route is one such; the general fix is GSR-B4's. |
| **Grade** | C - a remainder of GSR-B4 (B, open), not new; the split raises what GSR-B4 can cost |
| **Names** | GSR-B4 (*"Deleting a route leaves every other route's chain command naming it"*, open, issues.md :707); `894c52c8` |
| **Where** | `TrainControlUI.deleteRoute` :19716-19760 (asks "delete route {0}?" and nothing else); `MarklinControlStation.deleteRoute` :3747-3771; `MarklinRoute` :1089-1094 (`route.warningRouteNotExistCalledFrom`); `hasEmergencyStop` :434-449 |
| **Needs execution** | no |

Before the split, his stop was a command inside *Auto Emergency Stop Bottom Secondary*. Now it is a separate route, *Auto Emergency Stop Bottom Secondary (Emergency Stop)*. The route list shows that route unarmed and without a sensor, which is what a stray route looks like. The rest of his list already has six stop-only routes armed on sensors.

Delete asks only whether to delete it. Afterwards the parent's Route command names a route that does not exist, so its run logs a warning and cuts no power. `hasEmergencyStop` finds no stop route, so the parent becomes a route that is asked about again.

This is GSR-B4, which is known and open. What changes is that the program made the dependency, not the operator.

**Direction:** with GSR-B4 - at least, have Delete say which routes fire the one being deleted.

---

### RLA5-C4 - The stop's delay moves to where it holds back nothing: a command after a delayed stop now follows it by the 150 ms floor, and the split's javadoc says the route "does what it did"

| | |
|---|---|
| **Disposition** | Fixed - the stop's wait moves to the Route command in its place, so the commands after it still wait.  claims ec947f34 (red first), fix 0f62a7ca; mutation W5 red. |
| **Grade** | C - a timing change the javadoc denies; nothing on his railway |
| **Names** | `894c52c8` (*"The first stop moves, with whatever delay it had ... So it does what it did"*) |
| **Where** | `MarklinControlStation.splitRoutesThatMixAStop` :4353-4372 (javadoc), :4395-4403; `MarklinRoute` :1118-1128 (each command is followed by `SLEEP_INTERVAL + max(delay, DEFAULT_SLEEP_MS)`) |
| **Needs execution** | no |

A command's delay is the pause after it; the Readme calls it "how long to wait afterwards". The split moves the stop, with its delay, into a route of one command, where the pause after the last command holds back nothing. The Route command left in the parent has a delay of 0.

So in `[switch, stop (delay 3000), switch 2]`, switch 2 used to go out three seconds after the stop. Now it goes out about 200 ms after the Route command, while the stop runs on the stop route's own thread.

His route is `[switch 39, stop]`, with no delay and nothing after the stop, so nothing changes for him.

**Direction:** give the Route command the stop's delay; or say in the javadoc that a delay on a stop is dropped.

---

### RLA5-C5 - The split numbers the stop route one above the highest id, where the user's door starts at 1000 (OB-155): on a database with no route at or above 1000 the stop route takes an id in the station's range, and a sync that meets a station route of that id deletes it

| | |
|---|---|
| **Disposition** | Fixed - the stop route is numbered from 1000, as a route made in the editor is.  claims ec947f34 (red first), fix 0f62a7ca; mutation W4 red. |
| **Grade** | C - two doors that make a route disagree on its id; the harm needs a station route with that exact id |
| **Names** | OB-155 (`isLocalRouteId`: *"It says what an id could collide with"*); `894c52c8` |
| **Where** | `MarklinControlStation.java:4410` (`Collections.max(ids) + 1`) against :2338-2343 (`ROUTE_STARTING_ID`, then max + 1); the sync's route loop :1526-1544 (a local route whose id the station also holds, differing, is deleted and the station's added); `isLocalRouteId` :2221 |
| **Needs execution** | no |

The user's door starts a new route at 1000 and uses max + 1 only when 1000 is taken. The split uses max + 1 always.

- **On his database** the highest id is 1001, so the stop route gets 1002. Nothing changes for him.
- **On a database whose routes are all below 1000** - Central Station routes and routes made here before 2025-02-01 - the stop route lands in the ids the station hands out. If the station holds, or later makes, a route of that id, the next sync deletes the stop route and puts the station's route in its place. The parent's Route command then names nothing (RLA5-C3's consequence), or, if names collide, something else.

**Direction:** allocate as the user's door does.

---

### RLA5-C6 - The new messages speak of "an emergency stop" and "a Route command"; the editor's own rows are "Stop Everything" and "Trigger Another Route", in every language

| | |
|---|---|
| **Disposition** | Fixed - the editor's refusal names its rows as the editor labels them (route.kind.STOP, route.kind.ROUTE).  claims ec947f34 (red first), fix 0f62a7ca; mutation W10 red. |
| **Grade** | C - cosmetic: the remedy is right, in words the editor does not show |
| **Names** | `894c52c8` |
| **Where** | `messages.properties` :864 (`route.ui.frameStopStandsAlone`), :863, :865, against :770 (`route.kind.STOP=Stop Everything`) and :774 (`route.kind.ROUTE=Trigger Another Route`); the seven translations, which follow English on both sides |
| **Needs execution** | no |

The editor is built from dropdowns. A user who is told to *"fire that route from this one with a Route command"* looks for that name in the dropdown, and finds "Trigger Another Route". "Emergency stop" appears nowhere in the English bundle outside these five keys, apart from a keyboard tooltip. The same mismatch is in all eight languages - German, for instance, says "Routenbefehl" against its "Andere Fahrstraße auslösen".

**Direction:** quote `route.kind.STOP` and `route.kind.ROUTE` in the refusal, as other messages quote a menu's label.

---

### RLA5-D1 - RLA4-C1 and RLA4-C2: the refusal and the priority claim do what their dispositions say

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLA4-C1, RLA4-C2, RLU4-C1, RLU4-C2, RLD4-C1, RLD4-C2 |
| **Where** | `AutonomyViewerPanel.java:1086-1091`, :1286, :1428; `testTheImportDoorReadsAnOldFile` (`testAnOldFileIntoTheConfigurationInUseIsRefusedWhileAutonomyRuns`, the in-use claim's new assertions); `AutonomySession.captureFromLayout` :5495-5499; `Point.toJSON` :1259-1262 |

- **RLA4-C1.** An old file into the configuration in use is refused while `isAutonomyBusy()`, after the file is read and before the question, so nothing is written.
  - The claim sets `stagingFlowActive`. It asserts the message, the configuration, the names and `setup.json` on disk. With the refusal gone, the stop question would be answered and the import would go through, so the claim is red.
  - behaviour.md :2453 and the menu comment say so.
  - Its message is RLA5-C1.
- **RLA4-C2.** Before the import, the in-use claim takes out of the configuration a priority the file carries, and rebuilds without a capture. As a precondition it asserts the priority is gone. The capture writes `priority` only where it is not 0 (`Point.toJSON`) and removes a key the layout does not write. So a capture on the reload - the capture block deleted, or `loadAfterImport(into)` - would remove the priority the import brought, and the claim's last assertion but one is red. Both of RLA4-C2's mutations are caught, and the fixture does not supply the answer.

---

### RLA5-D2 - RLA4-C4: WS13 pins the branch GUI4-C3 is about

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLA4-C4, RLU4-C6, RLU4-C7, RLD4-C6, GUI4-C3 |
| **Where** | `Layout.whyItReachesNoStation` :5070-5095; `testWhyStuck.testOpeningASideIsNotOfferedWhereEveryTrainTurns` |

The branch now asks three things in order:

- not a barred copy of a station gives the "refused" sentence;
- a barred copy where every train turns gives `...OtherWayBarredMustTurn`;
- otherwise, "open that side ... in the autonomy editor".

WS13 makes the station copy a terminus and the barred copy a reversing point. It asserts as preconditions `turnsEveryTrainAt`, the barred copy, and that the copy reaches a station. It then asserts the MustTurn sentence exactly, and that the arrivals menu is not in it. Drop the middle branch and the Barred sentence comes back, and `assertEquals` fails.

---

### RLA5-D3 - RLA4-C3, RLA4-C5, RLA4-C7, RLA4-C8 and RLA4-C9: the records say what the code does

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLA4-C3, RLA4-C5, RLA4-C7, RLA4-C8, RLA4-C9 |
| **Where** | `Automation.md` :309-316; behaviour.md :705, :2038-2040; issues.md OB-303; open-questions.md :238-247; `AutonomyCompanionStore.java:1167-1178`; `AutonomySession.java:746` |

- **RLA4-C3.** `Automation.md`'s four bullets and behaviour.md :705 map one to one onto the four sentences `whyItReachesNoStation` chooses between:
  - turn it round;
  - open that side and turn it round, unless every train turns there;
  - drive it off by hand;
  - switch on, tick, and open the side unless every train turns round there.
- **RLA4-C5.** behaviour.md :2038-2040 says a facing the square records stays, which is `AutonomySession.java:746`.
- **RLA4-C7.** OB-303 says the mock station serves OBB 1043 with two members, and that the claims need a set-up.
- **RLA4-C8.** open-questions.md lists a home taken off and an emptied exclusion list, in the question and in option (a).
- **RLA4-C9.** The javadoc says what reads an answered 0 now, and dates the superseded answer. `git grep "every length rule"` finds no comment still stating the old rule.

---

### RLA5-D4 - RLA4-C6: the status notes are in the store

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLA4-C6, RLA3-C6, RLU4-C8, RLD4-C8 |
| **Where** | `docs/manual-tests/triage.db`, table `finding`, column `status_note` (read with `mode=ro&immutable=1`) |

RLA-C2's note now ends: *"Superseded - round 2 refused the name (RLA2-B1); round 3 took an old file back in without its trains, capturing first (RLD3-C1); round 4 refuses it while autonomy runs (RLU4-C1)."* RLU-C4's ends: *"round 4 names the closed side again, unless every train turns round there (RLU4-C4)."* The eleven other rows RLU4-C8 named carry a dated round-4 line too. The store holds 4,666 rows for 4,309 refs, as `4ae6bb51` says.

---

### RLA5-D5 - Round 4's other changes in this lane's files: the refusal covers both kinds of file that replace a configuration, the question and the message say what Yes does, and `captureRunningLayout`'s census of folds is complete

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLU4-B1, RLU4-C3, RLD4-C3 (its records half) |
| **Where** | `AutonomyViewerPanel.java:1055-1059`, :1072-1103, :1353-1357; `TrainControlUI.java:2966-2986`; `git grep "captureFromLayout("` in `src/` |

- **RLU4-B1.** `format != LEGACY_GRAPH` refuses a bundle and the bare shape both. `UNKNOWN` has already returned at :1057, so nothing else reaches the check.
- **RLU4-C3.** The in-use question is `confirmImportFillsGapsInUse`. `infoLegacyNotPlacedInUse` now names the right-click's `labelPlaceLocomotiveAt`, which is the label `GraphLocAssign.menuLabelFor` gives an empty station's item. `{2}` is in all eight languages.
- **The fold census.** `captureFromLayout` has four production callers - `load`, the import, the exit save and `captureRunningLayout` - and the javadoc names all four. Whether skipping one is safe is RLA5-B1.

---

### RLA5-D6 - The stop rule holds at every door that creates, edits, duplicates, imports, loads or syncs a route; the split keeps everything the route was, cannot fail part way, and the notice still shows

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | Adam's ruling of 2026-09-25; `894c52c8`, `19d82501` |
| **Where** | `MarklinControlStation` :476-485, :1495-1565, :2065-2140, :2248-2345, :4278-4428; `RouteEditorFrame.java:2667-2684`; `TrainControlUI` :21691-21730, :21900-21904, :25213-25230; `MarklinRoute` :933-947, :1080; `CS2File` (no stop is built) |

**Every door.**

| Door | What it does with a stop |
|---|---|
| Create and edit from the editor | The editor's gate reads every row, and the model's user doors (`newRoute` from input, `editRoute`) refuse before anything is deleted. |
| Duplicate, and arm or disarm | They go through those two doors. |
| Import, and the start-up restore | They go through the unguarded "from file" and "from database" `newRoute`s, and the split follows, after every route is in and armed. |
| The Central Station sync | Brings no stop: `CS2File` never builds one. |
| Change Route ID | Changes the id only. |

A programmatic caller of the two unguarded overloads can still add a mixed route. It runs as mixed routes always ran, stop included, and is split at the next start.

**What the split keeps.** The parent keeps:

- its name and id - `editRoute` re-adds under the same id;
- its sensor, trigger and conditions;
- whether it is armed - `r.isEnabled()` is read before `editRoute` disables the route, and the constructor re-arms it;
- its autonomy activation and its lock.

Neither step of the split can fail: `deleteRoute` refuses nothing, and the id and name it frees are the ones re-added.

**The notice.** Fired by its sensor, the parent passes `auto` down the chain, and the stop route shows *"The power has been automatically turned off because route {0} was triggered"*. The notice now names *X (Emergency Stop)*, which carries X's name. His route's stop fires at the same moment as before: about 200 ms after switch 39, on the stop route's own thread.

---

### RLA5-D7 - No question appears where none did; that the switch half of a split route is never asked is disclosed to Adam, with the way back

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | MT-507, MT-508, the ruling of 2026-09-01, MT-247 |
| **Where** | `MarklinRoute` :416, :434-449, :876; tests.md MT-507 and MT-508 (their 2026-09-26 comments) |

`hasEmergencyStop` only adds a way to answer true, so no route is asked about that was not before.

The split parent - switches, then a stop route - is not asked at a human door, and a switch under a train is skipped there, as the mixed route's was. Adam's words (*"if a route has emergency stop, it cannot have any other types of commands"*) leave that reading open. MT-507's and MT-508's comments say what happens, and offer to put the entries back on his list if he wants the switch half asked like any other route. That is the right place for the choice.

---

### RLA5-D8 - The eight bundles: 1,841 keys at `f7e94a50`, 1,843 after round 4, 1,848 at HEAD, and every check clean at all three

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Where** | `src/org/traincontrol/resources/messages*.properties` at `f7e94a50`, `4ae6bb51`, HEAD |

At each revision, read in memory:

- all eight languages hold the same keys, with no duplicates;
- every key's placeholders equal English's;
- no byte is above 127;
- no straight apostrophe is in a value that carries a placeholder;
- the only empty values are Italian's and Polish's `stats.ui.valuePluralSuffix`.

Round 4 added `confirmImportFillsGapsInUse` and `startReachesNoStationOtherWayBarredMustTurn`, and gave `startReachesNoStationEitherWay` and `infoLegacyNotPlacedInUse` a `{2}` in all eight. The split added five `route.` keys in all eight. Each translation uses the word its own bundle already uses for a route: itinerario, itinéraire, trasa, rute.

---

### RLA5-D9 - The length, tail and berth rules, Return Home and multi-units are untouched; the javadoc ratchet holds at 86

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLA-D1, RLA4-D7, BPV-A1 |
| **Where** | `git diff --stat f7e94a50 HEAD -- src/` |

Since `f7e94a50`, `src/org/traincontrol/automation/` changed only in `Layout.whyItReachesNoStation`, and `automationui/` only in one javadoc. `marklin/` changed only in routes. So the runtime and the planner still ask the same methods, and the multi-unit sweep is as RLA4-D4 read it.

The orphaned-javadoc rule, run in memory, gives 86 at `f7e94a50`, `4ae6bb51` and HEAD, with the same per-file counts for every file these commits changed. The new members' javadocs each sit on their member.
