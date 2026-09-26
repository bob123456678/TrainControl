# Release-readiness validation, round 5 - documentation and tests (TrainControl 3.0.0)

**Status:** open

**Prefix:** RLD5

**Reviewed:** branch `autonomy-diagram-r0` at `ebf4f919` (working tree clean by `git --no-optional-locks status --porcelain`), on 2026-09-26.  Two changes: round 4's claims `6f02ff2e`, fixes `d5b97ee8` and records `4ae6bb51`; and Adam's emergency-stop ruling of 2026-09-25, as a change in its own right - claims `19d82501`, fix `894c52c8`, test `92a0f20e`, tracker `ebf4f919`.

## Method

Read-only.  Nothing was compiled, run or started, and no JVM of any kind.  No git state was changed: only `git log`, `git show`, `git grep`, `git rev-parse` and `git --no-optional-locks status`.  Nothing under `cs2_sample_layout/` was read or written.  The only file written is this report.  Five mechanical checks were made in memory by inline `python -` heredocs that wrote nothing:

1. The eight bundles through `git show` at `d5b97ee8^`, `d5b97ee8`, `894c52c8^` and HEAD: key sets, duplicates, placeholder sets against English, bytes above 127, empty values, and straight apostrophes in values with a placeholder.  The ten keys the two changes added or changed, decoded in all eight languages.
2. His frozen routes file, `test/layouts/live-snapshot/config/gleisbilder/routes.json`: its ids, which routes carry a stop, and which fire another route.
3. `docs/manual-tests/triage.db`, opened read-only (`mode=ro` URI): row and ref counts, the 48 round-4 rows, the status notes of the eighteen rows round 4 touched, and MT-507's and MT-508's `test` and `verdict` rows.
4. tests.md's headings, dispositions and ledger; issues.md's Inbox by kind.
5. The live-snapshot README's "Used by" list against the test classes that name the scenario, by the census's rule (the quoted name) and by plain substring.

What was read:

- **The reviews.**  The brief; `docs/reviews/README.md`; my lane's `RLD4.md` whole, with every disposition; `RLA4.md` and `RLU4.md` whole; `RLD.md`'s RLD-C6.
- **The commits.**  All seven in full with `git show`, except the binary store.
- **The code round 4 changed, at HEAD.**  `AutonomyViewerPanel`: both `load`s, both `loadAfterImport`s, `importConfiguration`'s two refusals and its question, and `importLegacyGraph`'s capture.  `TrainControlUI`: `setupEditDeclinedDuringRun` and its javadoc, the exit save's fold, `isSetupNewerThanTheRunningLayout`, `captureRunningLayout`, `resetAutonomySession`, `rebuildRunningLayoutFromSetup` (where the flag is set and where it is cleared), `autonomyLoadedFromDiagram`, `layoutRefreshCompleteInternal`, `reloadActiveDiagramConfiguration`, the banner's load and `isAutonomyBusy`.  Also `AutonomyMenu`'s Configuration submenu and page toggle, `AutonomyEditorPanel.rebuildRunningLayoutSoon`, `MarklinControlStation.parseAuto`, and `Layout.whyItReachesNoStation`.
- **The code the stop ruling changed, at HEAD.**
  - `MarklinControlStation`: the start-up restore, the three `newRoute` overloads, `editRoute`, `deleteRoute`, `changeRouteId`, `execRoute(String)`, `chainDrives`' javadoc, the Central Station sync's route loop, `importRoutes`, `splitRoutesThatMixAStop` and `getRoutesSplitByLastImport`.
  - `MarklinRoute`: `executeAutoRoute`, both `execRoute` entries and the private one (its recursion guard, the accessory branch's `askable`, the stop branch and the route branch), `conflictingAccessoryAndReason`, `hasEmergencyStop` and `mixesAStop`.
  - Elsewhere: `Route.otherRouteRenamed`; `RouteCommand`'s factories; `CS2File`'s route-command factories; `RouteEditorFrame.everythingWrong`; `TrainControlUI`'s `executeRoute`, `deleteRoute`, `duplicateRoute`, `emergencyStopTriggered` and the Routes > Import message; `GraphLocAssign.menuLabelFor`.
- **Tests.**  `testTheImportDoorReadsAnOldFile`'s three new methods, the two it changed and their helpers.  `testWhyStuck`'s WS13 and its either-way claim.  `testWhereHisTrainsMayBeSent`'s owner census.  `testAStopRouteStandsAlone` whole.  `testARouteOverATrainAtItsDoors`: both methods and the fixture.  `testCommandTableMarks`' new method.  `testARouteDoesNotThrowSwitchesUnderATrain`'s change and `testOKFiresEveryCommandOfTheRoute`.  `testRoutes`' change.  `testEveryTestIsInTheBattery` and `testEveryScenarioIsUsedAndSaysSo`.  build.xml's test list, and the live-snapshot README.
- **Documents.**
  - behaviour.md: §7a's route paragraphs (:2150-2201), the import paragraph (:2431-2456), :705, :2036-2040 and :2504.
  - Elsewhere: `Automation.md` :311-316; AutomationAPI.md's Linked routes; open-questions.md :36-50, :235-248 and :420-432; issues.md's OB-303 and Inbox; tests.md's ledger, MT-507 and MT-508; Readme.md :374, :398-401 and :1070.

`docs/manual-tests/findings.tsv` was searched before each finding.  The prefix RLD5 is free.  The terms searched were: declined, putTheTrainsBack, before the run, recursion, chained route, Route command, stop route, stop-only, mixesAStop, MT-507, MT-508, ROUTE_STARTING_ID and same id.  Nothing below repeats a catalogued row beyond the ids it names.

Not reported, as the brief says: RLA3-B1 with RLU3-C4, OB-300 to OB-305, RLU2-C9 and RLD-C1.  Everything below is from reading.  Each finding that needs a run says so, with the fixture and what proves or refutes it.

**Counts:** 1 A, 1 B, 5 C, 8 D.  **This is not the signal to stop.**  The A and the B are both new, and both come from changes made since round 4 - neither is left over from an earlier round:

- **RLD5-A1** comes from the stop ruling's split (`894c52c8`).  A route fired by another route no longer reaches its stop.
- **RLD5-B1** comes from RLD4-C3's fix (`d5b97ee8`).  `load` now skips its capture while a declined edit waits, and does not carry the trains across the rebuild that follows.

Of the Cs:

- three are the stop change's own: the new test class is unregistered (C1), the stop route is tied to nothing (C2), and a stop's delay (C3);
- one is a record written in round 4 (C4);
- one is left over from the supersession of 2026-09-25, restated today (C5).

---

### RLD5-A1 - Once split, a route that another route fires no longer cuts the power: the stop is now in a third route, and a chain runs only two levels

| | |
|---|---|
| **Disposition** | Fixed as RLA5-A1. |
| **Grade** | A - a stop that no longer fires, with one log line to say so.  Reach: a route that fires, by a Route command, a route that mixed a stop - on any railway that has one when 3.0.0 first starts or imports its routes - and any chain built afterwards the way the editor's own message says.  **Not on Adam's railway as the frozen copy holds it**: none of its 85 routes has a Route command, and his split route is fired by its own sensor, which does reach the stop route. |
| **Names** | Adam's ruling of 2026-09-25 (`894c52c8`); SVN-A4 (*"never has its stop skipped"*); MT-507, MT-508 |
| **Where** | `MarklinRoute.java`: `:332-335` (`execRoute(auto)` is `execRoute(auto, 1, false)`), `:656-664` (a route refuses to run at `recursionLimit < 0`, logging `route.recursionLimitReached`), `:1043-1080` (a Route command runs `r.execRoute(auto, recursionLimit - 1, false)`), `:430` (*"One level, as a chained route runs one"*).  Every door starts at 1: `MarklinRoute.java:222` (the sensor) and `:366` (OK); `MarklinControlStation.java:3690` (the route list); `LayoutDiagramComponent.java:183` (the tile).  `MarklinControlStation.java:3438-3442` (`chainDrives`' javadoc says the same), `:4373-4431` (`splitRoutesThatMixAStop`); `messages.properties` `route.ui.frameStopStandsAlone`; behaviour.md `:2188-2190`, `:2192-2201`; Readme.md `:401` |
| **Needs execution** | yes |

**How deep a chain runs.**  A route fired from any door runs at limit 1.  A Route command in it runs the next route at limit 0.  A Route command in that route is refused, and the log says *"Route {0} not firing; recursion limit reached."*  So in a chain W -> X -> Y, W and X run and Y does not.

**Before the split:** W = [Route X] and X = [switch, stop].  X runs at level 0, and its own stop goes out.

**After it:** X = [switch, Route "X (Emergency Stop)"].  The stop route is the third level, so it is refused.  The switch is thrown and the power stays on - at the sensor door as well as the three a person uses, since `auto` changes who is asked and not how deep the chain runs.  No notice appears, because the stop branch that shows it never runs.

**New routes lead to the same place.**  The editor's message sends a route that should set something and then cut the power to *"Put the stop in a route of its own, and fire that route from this one with a Route command"*.  That is right for a route fired directly.  For a route that is itself fired by another, the stop never goes out.

**The records say the opposite:**

- behaviour.md :2189: a route carrying a stop *"never has its stop skipped (SVN-A4)"*;
- the new paragraph (:2195-2197): *"A route that should set something and then cut the power fires a stop-only route with a Route command; it counts as carrying the stop"*;
- the split's javadoc: *"So it does what it did - sets what it set, then cuts the power"*;
- the Readme: *"it keeps everything else"*.

None of them names the case where another route fires the route.

**No claim builds a chain.**  `core.testAStopRouteStandsAlone` asserts the shape of the split.  The two door classes (`ui.testARouteOverATrainAtItsDoors`, `regression.testARouteDoesNotThrowSwitchesUnderATrain`) fire the split route directly.

**Direction.**  Either let a Route command fire a stop-only route whatever the limit (it fires nothing, so it cannot recurse), or give the split's Route command one level more.  Add a claim that W firing a split X cuts the power, and say so in behaviour.md and the split's javadoc.

**Verification request.**  Use `core.testAStopRouteStandsAlone`'s fixture, a run with its own copy of the data.

1. Turn the power on: `model.go()`, then `waitForPowerState(true, ...)`.
2. Through the start-up's door (`newRoute(name, id, commands, 0, ...)`), add X = [accessory 39, stop] and W = [Route X].
3. Call `splitRoutesThatMixAStop`, then `model.execRoute("W")`, and wait a few seconds.

- **Proves:** the power is still on, and the log has `route.recursionLimitReached` for "X (Emergency Stop)".
- **Refutes:** the power goes off.
- **Control:** the same steps without the split - the power goes off.

### RLD5-B1 - RLD4-C3's fix at `load`: while a declined edit waits, several doors now rebuild the railway with every train where it stood before the run

Those doors are choosing a configuration, ticking a page in or out, and every import that reloads the running configuration.

| | |
|---|---|
| **Disposition** | Fixed as RLA5-B1. |
| **Grade** | B - DW-A1's consequence: the running railway puts a train on the square it left, and reads the square it stands on as free, so Start can route one train into another.  The reach is RLD4-C3's: only after a setup edit that a starting run declined, which is a race, and then at the gesture the declined edit's own log line points to.  Graded one step below its consequence, as RLD4-C3 was. |
| **Names** | RLD4-C3 (*"as the exit save and the editor doors do"*); WKW-B2, AMS-B1, ACC-B3, DW-A1, OB-183 |
| **Where** | **The change:** `AutonomyViewerPanel.java:776` (`captureRunningState && !ui.isSetupNewerThanTheRunningLayout() && ...`) and `:831` (`parseAuto(session().buildConfiguration())`: the railway is rebuilt from the stored placements).  **Callers that ask for a capture:** `AutonomyMenu.java:313` (choosing any configuration, the running one included); `AutonomyMenu.java:793` to `TrainControlUI.java:25015` (a page ticked in or out); `loadAfterImport` `:687-702`, from `:1151` and `:1428` (every import that reloads the running configuration).  **The doors it copied:** `TrainControlUI.java:6707-6725` (the editor doors' rebuild: `load(..., false, false)`, then `putTheTrainsBack`, then the flag cleared), `:3024-3029` (WKW-B2: *"where the trains stand is carried across rebuilds by `putTheTrainsBack`"*), `:589` (*"Cleared by a rebuild that replaces the running layout (AMS-B1)"*), `:2540` (the exit save).  **The records:** `messages.properties:396` (*"it will be picked up the next time the setup is loaded"*); behaviour.md `:2454-2456` |
| **Needs execution** | yes |

**What the exit save and the editor doors do.**  Neither folds the running layout while the flag is up, and neither loses a position:

- the exit save rebuilds nothing, and logs `placementsNotSaved`;
- the editor doors' rebuild carries each train across with `putTheTrainsBack`, then clears the flag, because the rebuilt railway now carries the edit (AMS-B1).

**What `load` does now.**  Only the first half.  With the flag up it skips its capture and rebuilds the running layout from the setup.  The setup's placements are those of the last fold, which was before the run.  So in the running railway:

- every train the run moved is put back on the square it left;
- the square it actually stands on reads as free;
- nothing is logged.

The flag also stays up afterwards, although the railway `load` built now carries the edit.  So the exit still saves no positions for the rest of the session.  That is AMS-B1's defect at this door; `load` never cleared the flag, before this round or since.

**Before `d5b97ee8`**, `load` captured with the flag up.  The edit was lost (RLD4-C3, graded C), and the trains stayed where the run had left them.  The fix exchanged that loss for a worse one.

**The doors:**

- choosing the running configuration again from the Autonomy menu - which is how the operator "loads the setup", the remedy the declined edit's log line gives;
- choosing another configuration - the one being left keeps its pre-run positions;
- a page ticked in or out of autonomy;
- every import that reloads the running configuration.  RLD4-C3's own door is among them, and there the file's *already standing* and *not placed* names are also counted against the stale placements.

**Same shape at an older door.**  Closing the track-diagram editor or re-downloading (`layoutRefreshCompleteInternal`, `TrainControlUI.java:23402`) goes through `resetAutonomySession`.  That clears the active name (`:3080`) before `load(wasRunning, false)`, so this door has rebuilt from stored placements while the flag is up ever since WKW-B2.  It is not catalogued.  So the defect is new at three doors and older at one.

**The records** all say these doors ask the same question - `load`'s new comment (*"the exit save and the editor doors ask the same"*), `captureRunningLayout`'s javadoc (*"Each asks `isSetupNewerThanTheRunningLayout` first"*), and behaviour.md :2454-2456, which names imports only.  None of them says that `load`'s answer, unlike the other doors', leaves the trains where they stood before the run.

**The claim.**  `testAnImportDoesNotFoldAnEditWaitingForItsRebuild` is a real claim for what it asserts.  By reading, V5 and V6 each fold the priority away and turn it red.  But it moves no train, so it cannot see this.

**Direction.**  Where `load` skips its capture for the flag, carry the trains across as `rebuildRunningLayoutFromSetup` does (`whereTheTrainsAre`, then `putTheTrainsBack`).  Clear the flag once the layout has been replaced.  Alternatively, send that case through `rebuildRunningLayoutFromSetup` when the configuration is the one running.  Add a claim with a moved train, and correct the three records.

**Verification request.**  Use `testAnOldFileIntoTheConfigurationInUseLeavesItsTrainsToTheRailway`'s fixture: the live-snapshot sandbox, the window, his configuration running, and nothing busy.

1. Move one standing train on the running railway only, to an empty station, as that claim does.
2. Write a priority with `setPointProperty` and do not rebuild.  Set `setupEditDeclinedDuringRun` to true by reflection, as `testAnImportDoesNotFoldAnEditWaitingForItsRebuild` does.
3. On the event thread, call `getAutonomyViewerPanel().load(inUse, true)`, which is what the menu's choice runs.

- **Proves:** in `ui.getModel().getAutoLayout()` the moved train stands on its old square, and the flag is still true.
- **Refutes:** the train stands where it was moved.
- **Control:** the same steps with the flag false - the train stays where it was moved.
- **Import:** the same steps with an import of MT-491 under a new name in place of step 3, since that reloads the running configuration.

### RLD5-C1 - The ruling's new test class is in neither build.xml nor the live-snapshot README: `regression.testEveryTestIsInTheBattery` is red at HEAD, and the scenario census cannot see the class using his frozen railway

| | |
|---|---|
| **Disposition** | Fixed as RLA5-C2. |
| **Grade** | C - bookkeeping: the release's final battery shows a red census, and `ant test` runs nothing of the class |
| **Names** | DD-A2, the registration census's own history |
| **Where** | build.xml (no `<test-one-class class="testAStopRouteStandsAlone"/>` among its 318 entries); `test/regression/testEveryTestIsInTheBattery.java` (a substring search of build.xml for every class that carries `@Test`; `DELIBERATELY_OUT` holds only `testAutoDetect`); `test/core/testAStopRouteStandsAlone.java:79-80` (reads `test/layouts/live-snapshot/config/gleisbilder/routes.json` by path), `:104` (`routes.length() + 1`); `test/layouts/live-snapshot/README.md` "Used by"; `test/regression/testEveryScenarioIsUsedAndSaysSo.java:213` (a user is a source containing the quoted name `"live-snapshot"`) |
| **Needs execution** | no - the census is a substring search and the line is absent.  The next run of `regression.testEveryTestIsInTheBattery` shows it: red, naming `testAStopRouteStandsAlone`. |

**build.xml.**  `894c52c8`'s message lists the neighbours it ran green, and the registration census is not among them.  `battery.sh` finds its classes from the tree (`find test -name "*.java"`), so the new class runs there, and so does the census that now fails.

**The Used-by list.**  By the census's own rule the list is complete: 78 names listed, 78 classes that quote the name.  But `testAStopRouteStandsAlone` reads his routes file by path.  It asserts on his route, and on the file holding exactly one route fewer than the model after the import.  A refreeze that carries the split route therefore turns it red, and the README - the list whoever refreezes will read - does not name it.

Adding it to the list as it stands would turn the census red in the other direction (*"listed and not a user"*), because a path is not the quoted name.

**Direction.**  Add the build.xml line.  Open the file through `support.Scenario.folderFor("live-snapshot")`, and list the class under Used by.

### RLD5-C2 - The stop route the split makes is tied to nothing: deleting it silently ends the split route's power cut, and on a railway whose routes all sit below 1000 its id is one a station route overwrites

It appears as a disarmed route the operator never made, and the delete confirmation, *"Delete route {0}?"*, names nothing that fires it.

| | |
|---|---|
| **Disposition** | Fixed in part - the stop route is numbered from 1000 (mutation W4 red); its deletion on its own is GSR-B4's, open. |
| **Grade** | C - a stop that no longer fires, but only after a deliberate delete of a route named "... (Emergency Stop)", or an id collision on a sync.  The split's log line and the import's message both say what it made. |
| **Names** | OB-155 (why the user door allocates from 1000); MKR-B1 |
| **Where** | `MarklinControlStation.java:4410` (`stopId = Collections.max(ids) + 1`), against `:2338` (the user door starts at `ROUTE_STARTING_ID`, 1000, `:237`) and `isLocalRouteId` (`:2221`); the sync's route loop `:1527-1541` (a station route carrying a local route's id deletes it); Readme.md `:1070` (*"will always overwrite local routes with the same ID"*); `TrainControlUI.deleteRoute` `:19716-19730` and `MarklinControlStation.deleteRoute` `:3747` (neither looks for a Route command that names the route); `MarklinRoute.hasEmergencyStop` `:434` (a fired route that is missing counts as no stop) |
| **Needs execution** | yes |

**Delete.**  Once the stop route is gone, X's Route command names nothing.  X throws its switches, logs *"route ... does not exist"* and cuts nothing.  `hasEmergencyStop` is false again, so X is asked about a switch under a train where before it was not.  Before the split, the stop could be removed only by editing X itself.  Deleting a route that another route fires has always been silent, but the split creates such a route on his railway without his hand.

**Rename is safe:** `editRoute`'s `otherRouteRenamed` rewrites the Route command.

**Id.**  On his railway the frozen file's ids run from 1 to 1001, so the stop route gets 1002, inside this program's own range.  On a railway whose routes are all below 1000, it gets max + 1.  A Central Station route may carry that number, and the sync then deletes the stop route and adds the station's in its place.  The user door allocates from 1000 for exactly this reason (OB-155).

**Direction.**  Allocate the stop route's id as `newRoute` does.  Make the delete confirmation name the routes that fire the route being deleted, or refuse to delete a stop route that another route fires.

**Verification request.**  Use `core.testAStopRouteStandsAlone`'s fixture.

1. Split "SA loaded" as `testALoadSplitsARouteThatMixesAStop` does.
2. `deleteRoute("SA loaded (Emergency Stop)")`.
3. Turn the power on and call `execRoute("SA loaded")`.

- **Proves:** the power is still on, and `hasEmergencyStop()` is false.
- **Refutes:** the delete is refused, or the power goes off.

### RLD5-C3 - A delay written on the stop no longer holds back the commands after it: the split moves it into the stop route, and the Route command left in its place waits only the default

| | |
|---|---|
| **Disposition** | Fixed as RLA5-C4. |
| **Grade** | C - a narrow case: a route with commands after its stop and a delay on the stop.  Adam's route has nothing after its stop. |
| **Names** | Adam's ruling of 2026-09-25 |
| **Where** | `MarklinControlStation.java:4392-4402` (the first stop moves *"with whatever delay it had"*; in its place goes `RouteCommand.RouteCommandRoute(stopName)`, with a delay of 0); `MarklinRoute.java:667-668` (a fired route runs on a thread of its own), `:1100-1128` (the pause after each command: `SLEEP_INTERVAL + max(delay, 150)`) |
| **Needs execution** | yes |

Take X = [stop (delay 2000), functions off].

- **Before the split**, X cut the power, waited two seconds, then sent the rest.
- **After it**, X fires the stop route, which starts its own thread and returns at once.  X then waits `SLEEP_INTERVAL` + 150 ms and sends the rest while the stop route is still inside its own pause.

The split's javadoc says the route *"does what it did"*.

**Direction.**  Put the stop's delay on the Route command that replaces it.

**Verification request.**

1. Build a mixed route [stop (delay 2000), accessory 40] through the start-up's door, and split it.
2. With the power on, fire it and record when the power goes off and when accessory 40 is sent.

- **Proves:** accessory 40 goes out roughly 150-300 ms after the power-off.
- **Refutes:** it goes out 2000 ms or more after the power-off.

### RLD5-C4 - open-questions.md's store paragraph lists additions only up to round 3 but gives round 4's total: its items add up to 4,618, not the 4,666 it states

| | |
|---|---|
| **Disposition** | Fixed in the records - open-questions.md's paragraph lists round 4's and round 5's additions. |
| **Grade** | C - records |
| **Names** | VD13-R1, VD14-R2, VD14-R5 (the paragraph's own history of arithmetic gone wrong) |
| **Where** | `docs/reference/open-questions.md:427-430` (`4ae6bb51` changed 4,618 to 4,666 and not the list before it); behaviour.md `:2504` (*"4,666 rows for 4,309 findings"* - right) |
| **Needs execution** | no |

The paragraph's items are 3,726 + 336 + 13 + 258 + 52 + 48 + 51 + 30 + 59 + 45, which is 4,618.  The store holds 4,666.  The difference is RLA4, RLU4 and RLD4, 16 rows each, which the list does not name.

`regression.testTheRecordsCountTheStore` compares the stated total with the store.  It does not check that the items add up to the total.

**Direction.**  Add *"and its fourth 48 (RLA4, RLU4 and RLD4)"*.

### RLD5-C5 - MT-508's superseding comment names a claim below the door, when MT-507's test is the door claim for MT-508's own route

The claim it names, `testOKFiresEveryCommandOfTheRoute`, fires a route the route list does not hold, through `execRoute` and `execRouteOverridingConflicts`.

| | |
|---|---|
| **Disposition** | Fixed in the records - a comment on MT-508 names the door claim for its route with the stop (ui.testARouteOverATrainAtItsDoors) beside the model claim for OK. |
| **Grade** | C - records: the outcome is claimed at the door, but by the test the comment does not name.  Left over from the supersession of 2026-09-25, restated today. |
| **Names** | RLD-C6 (the rule it quotes: *"the menu action or the method it runs, not a helper beneath it"*); MT-508 |
| **Where** | tests.md MT-508, comment of 2026-09-26; `test/regression/testARouteDoesNotThrowSwitchesUnderATrain.java:951-1010` (`route.conflictingAccessoryAndReason()`, `route.execRoute(false)` and `route.execRouteOverridingConflicts()`, on a `MarklinRoute` built for the probe and never added to the route list, `:1096`); `test/ui/testARouteOverATrainAtItsDoors.java:238-330` and `:507-512` (door 0 is `ui.executeRoute(ROUTE)`, the route list's own method); `TrainControlUI.java:20000-20060` |
| **Needs execution** | no |

MT-508's steps say: *"Fire the route from the route list and press OK"*.

- **With the stop, which is the entry's route.**  The route list asks nothing and runs the route guarded.  `testCancelAtEitherDoorRunsNothing`'s with-stop half drives exactly that at door 0, and asserts switch A left alone, switch B thrown and the power off.  The comment names the regression class's method instead, which calls the model beneath the door.
- **Without the stop** (*"asked, and OK throws both"*).  This is claimed only below the door: no test presses OK on the route list's question.

**Direction.**  Append a new comment naming `ui.testARouteOverATrainAtItsDoors.testCancelAtEitherDoorRunsNothing` for the with-stop route at the route list (the entries are append-only).

---

### RLD5-D1 - RLD4-C1 and RLD4-C2: an old file into the configuration in use is refused while autonomy is busy, before its question, with Delete's message; the in-use claim now reads what the file brings

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |
| **Where** | `AutonomyViewerPanel.java:1086-1091`; `testTheImportDoorReadsAnOldFile` - the busy claim and `testAnOldFileIntoTheConfigurationInUseLeavesItsTrainsToTheRailway` |

**The refusal** sits after the replace refusal, before the question, and before anything is written.  Its message, `autolayout.errorCannotEditWhileRunning`, is what RLU4-C1's direction asked for (*"with Delete's message"*).  Its claim sets `stagingFlowActive`, then asserts the refusal and that the configuration, the names and setup.json are unchanged.

**The in-use claim**:

1. takes a priority the file carries out of the configuration;
2. rebuilds without a capture, and asserts the priority is gone;
3. after the import, asserts it is back.

Under either R9 mutation - the capture block deleted, or `loadAfterImport(into)` - the reload's capture removes the priority, so the claim is red (by reading).  The claim also reads the new question, `confirmImportFillsGapsInUse`.

### RLD5-D2 - RLD4-C4 and RLU4-B1's claim: both refusal claims read the replace question

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |
| **Where** | `AutonomyViewerPanel.java:1074-1080`; `testAnImportIntoTheConfigurationInUseIsRefused`; `testABareConfigurationIntoTheConfigurationInUseIsRefused` |

**The bundle claim** asserts that `confirmImportOverwrites` is not among what was said.  Delete the refusal's `return`, and the busy check passes and the question is asked - red.

**The bare-shape claim** builds the configuration's own JSON, and asserts first that `detectImportFormat` reads it as `CONFIGURATION`.  It then asserts the refusal, no replace question, and that the configuration, the names and setup.json are unchanged.

### RLD5-D3 - RLD4-C5, RLD4-C6 and RLD4-C7: the user guide and behaviour.md give the four sentences the code chooses between; WS13 reaches the one where every train turns; the import-facing clause is there

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |
| **Where** | `Automation.md:311-316`; behaviour.md `:705`, `:2037-2040`; `Layout.java:5065-5094`; `testWhyStuck` `:704-723`, `:809-839` |

**The four sentences.**  `Automation.md`'s four bullets and behaviour.md :705 match `whyItReachesNoStation`'s returns:

- turn it round;
- where the other copy is barred: open that side in the autonomy editor and turn it round;
- where it is barred and every train turns: drive it off by hand;
- where the other way is refused: drive it off by hand;
- where neither way reaches a station: the either-way sentence, with *"unless every train turns round there"*.

**WS13** makes one copy a terminus and the other a reversing copy.  Its preconditions are `turnsEveryTrainAt`, `isABarredCopyOfAStation` and `canReachAnyDestination`.  It asserts the MustTurn sentence, with the menu absent from it.  Delete the `turnsEveryTrainAt` branch and the Barred sentence comes back - red.

**The either-way sentence's `{2}` is back**, so `testWhyStuck`'s third argument is read again.

**The import-facing clause.**  behaviour.md :2037-2040 now says a facing the square records stays, naming OB-304.

### RLD5-D4 - RLD4-C8 and RLD4-C9: the thirteen superseded rows carry status notes, RLD3-C3 and RLD3-C4 have their statuses, and the owner census reads three files and counts the tidy report

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |
| **Where** | `triage.db` `finding` rows; `test/ui/testWhereHisTrainsMayBeSent.java:201-245`; the three files it reads |

**The store** (read with `mode=ro`):

- Each of RLA2-B1, RLA2-B3, RLA2-C4, RLD2-C2, RLD2-C3, RLU2-C10, RLU3-C1, RLU3-C2, RLU3-D7, RLA3-D1, RLD3-D2, RLA-C2 and RLU-C4 has a round-4 status note saying what superseded it.
- RLU2-C13 is reopened as Open - deferred, OB-305's.
- RLD3-C3 is Open - deferred, with RLA3-C1 and RLA3-C2 under OB-303.
- RLD3-C4 is Closed.
- The bundle claim's javadoc says an old file is not refused.

**The census** now reads both menus and `AutonomyViewerPanel`, and counts `AutonomyReport.show(` as a message.  All 58 message calls in the three files are owned by `ui`, `edit` or `owner`, including the two whose owner is on the next line.  RLD4-C9's mutation - `this` as the right-click's report owner - is red by reading.

Two cosmetic leftovers:

- its precondition message still says *"the two menus"*;
- the literal `panel.contains("AutonomyReport.show(ui, session().save())")` below it is now redundant.

### RLD5-D5 - The eight bundles at HEAD: 1,848 keys each; round 4's two new keys, its three changed ones and the ruling's five say the same in every language

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |
| **Where** | `src/org/traincontrol/resources/messages*.properties` |

**Structure**, read in memory at `d5b97ee8^`, `d5b97ee8`, `894c52c8^` and HEAD:

- 1,841, then 1,843, 1,843 and 1,848 keys, in each of the eight files;
- no duplicates, and nothing missing or extra;
- every placeholder set equal to English's;
- no byte above 127, and no straight apostrophe in a value with a placeholder;
- the only empty values are Italian's and Polish's `stats.ui.valuePluralSuffix`.

**Decoded in all eight languages:**

- `infoLegacyNotPlacedInUse` - its `{2}` is *"Place Locomotive..."*, which is `GraphLocAssign.menuLabelFor`'s label for a square with no train;
- `confirmImportFillsGapsInUse`;
- `startReachesNoStationEitherWay` - `{2}` and the exception are back;
- `OtherWayBarred` - now says *"in the autonomy editor"*;
- `OtherWayBarredMustTurn`;
- `route.stopRouteSplitName`, `stopRouteSplit`, `errorStopAmongOtherCommands`, `frameStopStandsAlone` and `infoImportSplitStopRoutes`.

### RLD5-D6 - The stop ruling at the doors that make routes: none can now make a route that mixes a stop, the split keeps what it says, and his route's notice still shows

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |
| **Where** | `MarklinControlStation.java:485`, `:2077`, `:2332`, `:4337`, `:4373-4431`, `:1500-1560`; `RouteEditorFrame.java:2667-2685`; `CS2File.java` (its route-command factories); `MarklinRoute.java:422-461`, `:876`, `:933-947`; Readme.md `:401`; behaviour.md `:2192-2201` |

**No door can make a mixed route:**

- **Create.**  The user door `newRoute` and the editor's Save both refuse one.  `duplicateRoute` goes through the user door, and a split route is not mixed.
- **Edit.**  `editRoute` refuses before it deletes anything.  Bulk Enable and the auto-fire toggle reach it with the commands unchanged.
- **Import.**  Every route goes in, then the split runs, and the message names what it split.
- **Load.**  Every saved route is restored through the id door, then the split runs, before the sync.
- **Sync.**  The Central Station's parsers make accessory, speed, function and direction commands - never a stop.
- **Re-key.**  `changeRouteId` changes the id without touching the commands.

**The split keeps** the route's id, name, sensor, trigger, conditions, lock, armed state and autonomy activation; `editRoute` carries each of these itself.  On failure it leaves the route as it was: the stop route is deleted again if the edit fails, and `editRoute` cannot fail once it has deleted.

**His route**, in the frozen copy, is [accessory 39, stop] on s88 2012, with conditions, saved disarmed.  It splits to [accessory 39, Route "... (Emergency Stop)"], and the stop route gets id 1002.

**Fired by its sensor**, the split route runs at limit 1 and the stop route at limit 0, with `auto` passed on, so the notice shows.  The notice names the stop route - *"when the emergency stop route fires"*, as the ruling words it.  `hasEmergencyStop` counts the fired stop route, so the split route is not asked about, as it was not before, and the switch under a train is skipped, as before.

**A route that already fired a stop-only route and threw switches** is now not asked about either.  That is the ruling of 2026-09-01 applied to the new shape, and its held switch is skipped rather than thrown.

**The Readme and behaviour.md** say all of this, except RLD5-A1's chain.

### RLD5-D7 - MT-507 and MT-508 are superseded on Adam's answer, and the tracker, ledger and store agree

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |
| **Where** | tests.md MT-507, MT-508 and the ledger; `triage.db` `test`, `verdict` and `finding` tables; issues.md's Inbox |

**Adam's answer settles RLD-C6's question** (*"never asked, as now, or asked like any other"*).  A route with a stop fires, and it can no longer carry the switches the entries' route had.

**MT-507's named test** drives both doors - `ui.executeRoute`, and a click on his own route tile - with the split shape, and asserts every outcome the comment gives.

**The tracker:**

- tests.md holds 586 entries: 453 fixed validated, 123 superseded, 8 fixed unvalidated and 2 needs test;
- the ledger's 10 rows are exactly the 10 entries not closed, and it says *"576 of 586"*;
- the store's MT-507 and MT-508 rows read superseded, and their blocks carry the comment of 2026-09-26.

**The store** holds 4,666 rows for 4,309 refs.  RLA4, RLU4 and RLD4 have 16 rows each, and every one is Closed.  Its dispositions read as the documents' do, as far as I compared them (the first 90 characters).  **The Inbox** holds 92 entries: 60 OB and 32 FR.

### RLD5-D8 - The final battery must show today's claims that need a display or their own data passed, not skipped

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right, on the record |
| **Where** | the classes below; `testAStopRouteStandsAlone.java:52-57` (the whole class skips without its own copy of the data - which is how `ant test` and a NetBeans run meet it) |
| **Needs execution** | yes - the release's final battery |

**Verification request.**  From the release's final battery, read the per-method status of:

- `regression.testTheImportDoorReadsAnOldFile`: `testABareConfigurationIntoTheConfigurationInUseIsRefused`, `testAnOldFileIntoTheConfigurationInUseIsRefusedWhileAutonomyRuns`, `testAnImportDoesNotFoldAnEditWaitingForItsRebuild` and `testAnOldFileIntoTheConfigurationInUseLeavesItsTrainsToTheRailway` (each skips without a display);
- `ui.testCommandTableMarks.testAnEmergencyStopStandsAlone` and both methods of `ui.testARouteOverATrainAtItsDoors` (each skips without a display);
- `core.testAStopRouteStandsAlone`, all three methods;
- `regression.testARouteDoesNotThrowSwitchesUnderATrain.testOKFiresEveryCommandOfTheRoute`.

**Proves:** every one passed.  **Refutes:** any one skipped - then MT-507 and MT-508 go back on Adam's list, and the report says so.

`regression.testEveryTestIsInTheBattery` will be red until RLD5-C1 is fixed.
