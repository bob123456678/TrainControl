# RLU5 - validation, round 5: the windows (every screen, dialog and menu) and the message bundles (TrainControl 3.0.0 release readiness)

**Status:** open

**Prefix:** RLU5

**Reviewed:** branch `autonomy-diagram-r0` at `ebf4f919` (working tree clean by `git --no-optional-locks status --short`): round 4's claims `6f02ff2e`, fixes `d5b97ee8` and records `4ae6bb51`, and Adam's emergency-stop ruling of 2026-09-25 as a change in its own right - claims `19d82501`, fix `894c52c8`, test `92a0f20e`, tracker `ebf4f919`.  On 2026-09-26.

## Method

Read-only: nothing compiled or run, no JVM, no git state changed (only `git log`, `git show`, `git grep`, `git ls-tree`, `git diff --stat` between commits and `git --no-optional-locks status`), nothing under `cs2_sample_layout/` read or written, and this file is the only one written (its folder already held the other lanes' files, which I did not open).  I read the brief, `docs/reviews/README.md`, my lane's round-4 document `docs/reviews-2026-09-25/RLU4.md` whole, and `RLA4.md` and `RLD4.md` whole.  I read the seven commits with `git show`: `d5b97ee8` in full for `gui/`, `automation/Layout.java`, `automationui/AutonomyCompanionStore.java`, the eight bundles and every document it changed; `6f02ff2e` in full (the four new or changed import claims and their helpers, `testWhyStuck`'s WS13 and the either-way assertions, the owner census in `testWhereHisTrainsMayBeSent`); `4ae6bb51`'s reference-document lines; `19d82501`, `894c52c8`, `92a0f20e` and `ebf4f919` in full.  Around each change I read at HEAD: `AutonomyViewerPanel.importConfiguration`, `importLegacyGraph`'s capture, both `loadAfterImport`s and `load` whole; `TrainControlUI.isSetupNewerThanTheRunningLayout`, the flag's field and every use of it (the exit save, `captureRunningLayout`, `rebuildRunningLayoutFromSetup`), `isAutonomyBusy`, every caller of the panel's `load`, the Routes > Import door whole, `deleteRoute`, `writeRouteEnabledState` and `emergencyStopTriggered`; `Layout.whyItReachesNoStation`, `getLocomotivesToRun` and where the parse fills it; `AutonomySession.importLegacy`'s standing-already seed and not-placed branch; `LayoutRightclickAutonomyMenu`'s two Place items and their gates, and `GraphLocAssign`'s locomotive list; `MarklinRoute.execRoute` whole (the recursion limit, the chained route, the stop, the popup), `hasEmergencyStop`, `hasAStop`, `mixesAStop` and `conflictingAccessoryAndReason`; `MarklinControlStation`'s start-up restore, both `newRoute` overloads that take a list, `newRoute(MarklinRoute)`, `editRoute`, `deleteRoute`, `changeRouteId`, `rebindRouteTiles`, `isLocalRouteId`, the Central Station sync's route loop, `parseRoutesFromJson`, `importRoutes`, `splitRoutesThatMixAStop` and `getRoutesSplitByLastImport`; `RouteEditorFrame.everythingWrong`, its Save paths and `Entry`; `CommandRow.of` and the kind labels; `RouteCommand`'s route line and parser; `CS2File`'s route commands; `Route.otherRouteRenamed`.  Tests read: `core.testAStopRouteStandsAlone` whole, the two changed UI classes' diffs, `92a0f20e`'s diff, `regression.testEveryTestIsInTheBattery`, `regression.testEveryScenarioIsUsedAndSaysSo`'s user rule, and `testJavadocsAreAttached`'s rule and pins.  Documents: behaviour.md :705, :2036-2040, :2186-2201 and :2431-2445; Automation.md :311-316; Readme.md :374, :400-401 and :407; open-questions.md :42 and :238-247; issues.md OB-303; tests.md MT-507 and MT-508.  **Mechanical checks, all in memory** (inline Python fed through a heredoc, reading `git show` output or the files; nothing written): the eight bundles at `4ae6bb51` and HEAD (key sets, duplicates, placeholder sets against English, bytes above 127, empty values, straight apostrophes in values with a placeholder), and the round's seven changed or new import and Why-not-Moving keys and the five new route keys decoded and read in all eight languages; the orphaned-javadoc rule over every `src/` file at HEAD; the live snapshot's `config/gleisbilder/routes.json` (every route's ids and command kinds); `build.xml`'s `test-one-class` lines against the test classes that carry `@Test`; `docs/manual-tests/triage.db` opened read-only (`mode=ro&immutable=1`; its header says rollback journal) for the rows RLU4-C8 named, every RLU4 row, and the grades of the findings cited below.  `docs/manual-tests/findings.tsv` was searched before each finding (the prefix RLU5 is free; the recursion limit, a stop route, `ROUTE_STARTING_ID`, `putTheTrainsBack`, Place Locomotive, `errorCannotEditWhileRunning`, the editor's kind labels, `wrappedToFit`): nothing below repeats a catalogued row beyond the ids it names.  Not reported, as the brief says: RLA3-B1 with RLU3-C4, OB-300 to OB-305, RLU2-C9 and RLD-C1.  **Everything below is from reading**; each finding that needs a run says so, with the fixture and what proves or refutes it.

One A, two B, seven C, eight D.  **This is not the signal to stop: all three findings above C are new**, none a remainder of an earlier round's finding.  RLU5-A1 and RLU5-B1 come from the emergency-stop change (`894c52c8`): a split route that another route fires no longer cuts the power, and the split's stop route takes an id the Central Station's sync can take back.  RLU5-B2 is a regression of round 4's own RLD4-C3 fix (`d5b97ee8`), carrying an AMS-B1 remainder with it.  Of the Cs, RLU5-C1 says RLU4-C3's disposition is partial, RLU5-C2 is new from round 4's RLU4-C1 fix, and RLU5-C3 to RLU5-C7 are new from the emergency-stop change.  Every RLU4 finding's disposition is otherwise true (RLU5-D1 to RLU5-D4).

---

### RLU5-A1 - A route that another route fires no longer cuts the power once it is split: the split puts its stop one Route command deeper than a fired route may reach, and the route editor's advice builds the same

| | |
|---|---|
| **Disposition** | Fixed as RLA5-A1. |
| **Grade** | A - a stop that does not fire, SVN-A4's consequence (graded A), and behaviour.md's rule is false for it: "A route carrying a stop is never refused at a human door and never has its stop skipped".  Reach: a route that another route fires with a Route command, and that mixed a stop with other commands - split at the first start of 3.0.0 or at an import - or one built as the editor now advises while another route fires it.  Not on Adam's railway as frozen: the live snapshot's 85 routes hold no Route command at all (only switch and stop commands).  New, in `894c52c8`.  Needs execution. |
| **Names** | Adam's ruling of 2026-09-25 (claims `19d82501`, fix `894c52c8`); SVN-A4 |
| **Where** | `MarklinRoute.java:334` (every door fires a route with `recursionLimit` 1 - the sensor monitor, the tile, the route list, `MarklinControlStation.execRoute(name)` and so a locomotive's callback), `:1080` (a Route command fires its route with `recursionLimit - 1`), `:658` (below 0 the route logs `route.recursionLimitReached` and returns before anything is sent), `:430` ("One level, as a chained route runs one"); `MarklinControlStation.java:4393-4403` (the stop moves to a new route and a Route command stands in its place); `RouteEditorFrame.java:2684` with `route.ui.frameStopStandsAlone`; behaviour.md :2188-2190 and :2192-2200; Readme.md:401 |
| **Needs execution** | yes |

A route fired from any door runs at recursion limit 1.  A Route command in it fires the named route at 0, which runs its commands; a Route command in that one would fire at -1, which is refused with the log line "Route {0} not firing; recursion limit reached." and nothing else.  Before the split, a route Y of a switch and a stop, fired by another route Z's Route command, ran at 0 and cut the power.  After it, Y is the switch and a Route command firing "Y (Emergency Stop)": fired by Z it runs at 0, throws the switch, and fires its stop route at -1 - which is refused.  The power stays on, no notice is shown, and only the log says so.  Fired by its own sensor or by hand, Y still cuts the power (1, then 0), and that is all the claims try.

The split's javadoc says "So it does what it did - sets what it set, then cuts the power", which is true one door deep; `hasEmergencyStop`'s comment knows the depth ("One level, as a chained route runs one"), and the split spends that one level.  The editor's refusal tells the operator to build the same by hand - "Put the stop in a route of its own, and fire that route from this one with a Route command" - which, for a route another route fires, gives a power cut that never happens.

**Direction:** a route that is only an emergency stop runs at any depth - behaviour.md's "obeyed whatever else is true" - for example by not refusing it at the recursion limit when a Route command fires it; and a claim that a split route fired by another route cuts the power.

**Verification request.**  `core.testAStopRouteStandsAlone`'s fixture (the model on its own copy of the data).  Restore a route Y of switch 39 and an emergency stop through the door the start-up uses (`newRoute(name, id, commands, 0, trigger, false, null)`), make Z of one Route command firing Y with `newRoute(name, commands, ...)`, call `splitRoutesThatMixAStop()`, turn the power on as `ui.testARouteOverATrainAtItsDoors` does, `execRoute(Z)`, and wait three seconds for both route threads.  **Proves:** the power is still on, and the log has `route.recursionLimitReached` for Y's stop route.  **Refutes:** the power is off.  **Controls:** `execRoute(Y)` cuts it; at `4ae6bb51`, Z over the unsplit Y cuts it.

---

### RLU5-B1 - The split's stop route takes the next id up, not one from 1000 as a route made at any other door does: where no route has an id of 1000 or more, the Central Station's sync can delete it, and the route it came from stops cutting the power

| | |
|---|---|
| **Disposition** | Fixed - the stop route is numbered from 1000, as a route made in the editor is.  claims ec947f34 (red first), fix 0f62a7ca; mutation W4 red. |
| **Grade** | B - RLU5-A1's consequence (the stop route gone; the route it came from sends no stop, and since `hasEmergencyStop` finds no stop route it is asked about at the human doors again), through two conditions: no route of id 1000 or more - any setup whose routes were all made before 2025-02-01 or synced from the station, or an import of such an export - and the station holding, now or later, a route of the id the split took.  One grade down for the second, as RLD4-C3 was taken down for its precondition.  Not on Adam's railway as frozen: the snapshot's ids run to 1001, so its stop route would be 1002.  New, in `894c52c8`.  Needs execution. |
| **Names** | Adam's ruling of 2026-09-25 (fix `894c52c8`); OB-155 (the 1000 floor); RLU5-A1 |
| **Where** | `MarklinControlStation.java:4410` (`Collections.max(this.routeDB.getItemIds()) + 1`) against `:2338-2342` (a route made from input: `ROUTE_STARTING_ID` where free, then the next up) and `:2223` (`isLocalRouteId`: an id from 1000 is one "The Central Station has never been told about"); `:1527-1543` (the sync deletes a route whose id a station route carries with other commands, and adds the station's); `TrainControlUI.java:25167-25178` (Routes > Import runs `syncWithCS2()` straight after the import that split); `MarklinRoute.java:442` (`hasEmergencyStop` finds the stop route by name) |
| **Needs execution** | yes |

Every other door that makes a route starts at 1000 so that no id it hands out can meet one the station hands out; the split takes the highest id held plus one.  Where every route's id is below 1000, the stop route's is too.  At the next sync - at every start, and straight after Routes > Import - a station route of that id with other commands deletes it ("route.deletingDuplicateId") and takes its place.  The route it was split from then fires a route of that name that does not exist: the log says "route {0} does not exist", no stop is sent, and the route is asked about at the route list and its tile as a route without a stop.  The import's own message has just named it among the routes split.

**Direction:** give the stop route its id as a route made from input gets one - from `ROUTE_STARTING_ID`.

**Verification request.**  `core.testAStopRouteStandsAlone`'s fixture with every route of id 1000 or more taken out of the sandbox's copy, and a route of a switch and a stop restored at id 21 through the start-up's door.  Call `splitRoutesThatMixAStop()`.  **Proves:** the stop route's id is below 1000, where `newRoute(name, commands, ...)` on the same database gives 1000.  Then serve a station route of that id (`testMockCentralStation.fixtureFor`) and sync.  **Proves:** the stop route is gone, and the split route's `hasEmergencyStop()` is false.  **Refutes:** the id is 1000 or more, or the stop route survives the sync.

---

### RLU5-B2 - RLD4-C3's fix makes `load` skip its fold while a declined setup edit waits, without carrying the trains across as the rebuild door does or lowering the flag after: the Autonomy menu's choose, a page ticked out, a track-diagram edit and the reload after any import now put every train a run moved back where it stood before the run

| | |
|---|---|
| **Disposition** | Fixed as RLA5-B1. |
| **Grade** | B - DW-A1's consequence (graded A): the running layout holds trains on squares they are not on, occupancy is placements, and Start can route a train into a block that is physically occupied.  Reach: only after a setup edit a run declined, before any setup gesture has rebuilt - RLD4-C3's own reach, which that lane took one grade down; so B.  A regression of round 4's fix (`d5b97ee8`) - before it, `load` folded, and the edit was lost instead (RLD4-C3, a C) - and the flag not lowered by `load` is AMS-B1's remainder at a door round 4 now makes honour the flag.  Needs execution. |
| **Names** | RLD4-C3 (its fix); AMS-B1, ACC-B3, WKW-B2 (the trade the flag makes); DW-A1 (the consequence); RLA4-C1's second direction ("reload with the placements carried the way `rebuildRunningLayoutFromSetup` carries them") |
| **Where** | `AutonomyViewerPanel.java:776` (`load`'s capture now asks `!ui.isSetupNewerThanTheRunningLayout()`), `:831` (`parseAuto(session().buildConfiguration())`: the configuration's placements), `:1286` and `:1428` (the import's capture skipped, so its reload is asked to capture - and skips); `TrainControlUI.java:596` (the field's javadoc: "Cleared by a rebuild that replaces the running layout (AMS-B1)"), `:6618-6621` (where it is set), `:6707-6725` (`rebuildRunningLayoutFromSetup`: `load(..., false, false)`, then `putTheTrainsBack`, then the only line that lowers it), `:3016-3029` (`captureRunningLayout`: "where the trains stand is carried across rebuilds by `putTheTrainsBack` rather than written ... The rebuild that replaces the running layout clears the flag"); the callers of `load` that capture: `AutonomyMenu.java:313`, `TrainControlUI.java:23402` and `:25015`, and both `loadAfterImport`s; behaviour.md :2443-2444 |
| **Needs execution** | yes |

The flag says the setup on disk is newer than the running layout: a setup edit reached the file and not the running layout, which was built before it.  The trade ACC-B3, WKW-B2 and AMS-B1 settled has three parts: no fold of the running layout until a rebuild carries the edit; the trains carried across that rebuild by `whereTheTrainsAre` and `putTheTrainsBack`; and the flag lowered once the rebuild has replaced the running layout.  Round 4 gave `load` the first part only.  With the flag up and the run over, `load` no longer folds; it builds from the configuration, whose placements nothing has captured since before the run; the trains the run moved are stood back where they started on the running layout itself; and the flag stays up, since only `rebuildRunningLayoutFromSetup` lowers it - so the exit save, the editor doors and every later `load` skip their folds until a setup gesture's rebuild comes, or for the rest of the session.

The doors: choosing any configuration from the Autonomy menu (the one running included - its radio item still fires), ticking a page out of autonomy, the reset after a track-diagram edit, and the reload of the running configuration after any import, bundle or old file, under any name.  The claim for RLD4-C3 (`testAnImportDoesNotFoldAnEditWaitingForItsRebuild`) moves no train, so it cannot see this.

**Direction:** while the flag is up, reload the running configuration as `rebuildRunningLayoutFromSetup` does - the trains carried across, and the flag lowered once the running layout is replaced; choosing another configuration, fold the placements only, or refuse with a message until the edit is carried.  behaviour.md and `captureRunningLayout`'s javadoc to say which.

**Verification request.**  The live-snapshot sandbox, the window open, Main running, nothing busy.  Move one standing train on the running railway only, to an empty station, as `testAnOldFileIntoTheConfigurationInUseLeavesItsTrainsToTheRailway` does; set `setupEditDeclinedDuringRun` true by reflection; call `getAutonomyViewerPanel().load(inUse, true)` on the event thread (the menu's choose).  **Proves:** the train stands on its old square in the running layout and in Main, and the flag is still true.  **Refutes:** it stands on its new square.  **Control:** the same with the flag false - its new square.

---

### RLU5-C1 - RLU4-C3's remedy names an item that does not offer the trains it names: "Place Locomotive..." lists only trains already standing, and every train in the not-placed line is one the configuration in use does not have standing

| | |
|---|---|
| **Disposition** | Fixed - the not-placed line names the door that places a train the diagram does not have: choose it in the main window, then "Place <train>" on its station.  claims ec947f34 (red first), fix 0f62a7ca; mutation W9 red. |
| **Grade** | C - RLU4-C3's own grade: a message whose remedy does not work.  Nothing moves.  RLU4-C3's disposition ("the message after says how to put one down") is partial.  No execution needed; the request confirms it. |
| **Names** | RLU4-C3 (fix `d5b97ee8`) |
| **Where** | `AutonomyViewerPanel.java:1355-1356` (`{2}` is `autolayout.ui.labelPlaceLocomotiveAt`); `AutonomySession.java:1140-1147` (a train goes to `notPlacedInUse` only when the configuration does not have it standing); `LayoutRightclickAutonomyMenu.java:788-791` (the item `GraphLocAssign.menuLabelFor` names "Place Locomotive..." on an empty station) and `GraphLocAssign.java:117-121` (opened with `newOnly` false, it lists `getLocomotivesToRun()`); `Layout.java:13227-13247` and `:9563-9566` (that set is the trains placed); `LayoutRightclickAutonomyMenu.java:694` and `:733` ("Place {0}", for the locomotive chosen in the main window - the door that puts down a train the diagram does not have); `infoLegacyNotPlacedInUse` in all eight bundles; `testTheImportDoorReadsAnOldFile.java:810` (the claim reads the line only up to `{0}`) |
| **Needs execution** | no |

The line now ends "To put one where it stands, right-click its station on the track diagram and choose "Place Locomotive..."".  That item opens the placement dialog with the trains the running layout already has standing - it moves one of them.  The trains the line names are, by the branch that names them, the ones the configuration in use does not have standing, so none of them is in that list (in memory, the MT-491 file's ET22-245 into Main).  The door that works is the other Place item: choose the train in the main window, then right-click the station and choose "Place <train>".  No claim reads the sentence past its list of trains.

**Direction:** name that door - choose the locomotive in the main window, then "Place {0}" on its station's right-click menu - in eight languages, and read the sentence to its end in the claim.

**Verification request (to confirm).**  The in-use claim's fixture after its import: on an empty station, build the diagram's right-click autonomy menu, press the item labelled `autolayout.ui.labelPlaceLocomotiveAt`, and read the dialog's locomotive list.  **Proves:** ET22-245 is not in it.  **Refutes:** it is.

---

### RLU5-C2 - The refusal round 4 added for an old file into the configuration in use while autonomy runs says "Cannot edit auto layout while running.": it names neither the import nor a way on, after the file and the name have been chosen

| | |
|---|---|
| **Disposition** | Fixed as RLA5-C1. |
| **Grade** | C - a refusal without a remedy; nothing moves.  New in `d5b97ee8`, which took my lane's own direction (RLU4-C1: "with Delete's message"); RLD4-C1's direction had asked for the remedy to be named - stop autonomy first, or import under another name.  No execution needed. |
| **Names** | RLU4-C1, RLA4-C1, RLD4-C1 (the fix) |
| **Where** | `AutonomyViewerPanel.java:1086-1091`; `autolayout.errorCannotEditWhileRunning` in all eight bundles (`messages.properties:252`, and e.g. German "Autolayout kann während des Betriebs nicht bearbeitet werden."); the refusal above it, `autosetup.ui.errorImportIntoConfigurationInUse` (`:1074-1080`), which says what to do |
| **Needs execution** | no |

The refusal beside it, for a file that would replace the configuration, ends "Import the file under another name, and choose it under {1} when you want to run it."  This one, from the same Import, calls the import an edit, the feature "auto layout" - a name no menu uses now - and stops.  Two ways on exist: stop autonomy, or let the trains finish, and import again; or import under another name.

**Direction:** a key of its own - "{0} is the configuration in use and autonomy is running, so nothing was imported.  Stop autonomy and import it again, or import the file under another name." - in eight languages; the claim asserts the key and moves with it.

---

### RLU5-C3 - The route editor's refusal speaks of "an emergency stop" and "a Route command"; its own rows call them "Stop Everything" and "Trigger Another Route", in all eight languages

| | |
|---|---|
| **Disposition** | Fixed as RLA5-C6. |
| **Grade** | C - cosmetic: the remedy works once the operator maps the words.  New in `894c52c8`.  No execution needed. |
| **Names** | Adam's ruling of 2026-09-25 (fix `894c52c8`) |
| **Where** | `RouteEditorFrame.java:2667-2684`; `route.ui.frameStopStandsAlone` (`messages.properties:864` and the seven translations); `route.kind.STOP` and `route.kind.ROUTE` (`:770`, `:774`), which the editor shows for each row (`CommandRow.java:153`) |
| **Needs execution** | no |

The editor's kind column and its drop-down say "Stop Everything" and "Trigger Another Route"; nowhere does the editor say "emergency stop" or "Route command".  Every translation carries the same gap - German "Notstopp" and "Routenbefehl" against "Alles anhalten" and "Andere Fahrstraße auslösen", French "arrêt d’urgence" and "commande d’itinéraire" against "Tout arrêter" and "Déclencher un autre itinéraire".

**Direction:** the sentence takes the two kind labels as its arguments.

---

### RLU5-C4 - Routes > Import's new line naming the routes it split is not wrapped, where RLU-C3 wrapped the question beside it for the same reason

| | |
|---|---|
| **Disposition** | Fixed - the paragraph naming the routes an import split is wrapped to the screen.  claims ec947f34 (red first), fix 0f62a7ca; mutation W11 red. |
| **Grade** | C - RLU-C3's grade: a dialog wider than the screen once the list is long.  New in `894c52c8`.  No execution needed. |
| **Names** | RLU-C3 |
| **Where** | `TrainControlUI.java:25217-25229` (the names joined on one line); `:25152-25153` (the question, through `wrappedToFit`); `route.ui.infoImportSplitStopRoutes`: 146 characters before the names in English, 200 in German, 213 in French |
| **Needs execution** | no |

A dialog does not wrap a line, and this one puts every split route's name at the end of a sentence already wider than the notice above it.  RLU-C3 found the same shape in the same door's question and wrapped it; the line added this round went in unwrapped.

**Direction:** `wrappedToFit` round the whole message, as the question has it.

---

### RLU5-C5 - The split's claim reads the name, the sensor and the armed state: the conditions, the id a route tile is bound by, the lock and the trigger, which the commit and behaviour.md say are kept, can go with every test green

| | |
|---|---|
| **Disposition** | Fixed - the load claim reads the id, trigger, conditions and lock, and the stop's wait.  Claims ec947f34; mutations W6 red, W7 red. |
| **Grade** | C - a claim that would not catch its own regression.  His own route has conditions (switch 12 off and sensor 1009), so a lost condition fires its power cut on every pass of sensor 2012.  Needs execution. |
| **Names** | Adam's ruling of 2026-09-25 (claims `19d82501`) |
| **Where** | `test/core/testAStopRouteStandsAlone.java:223-251` (`assertTheSplit`), `:77` (the import of his routes file, whose mixed route carries conditions), `:120` (the load, built with no conditions); `MarklinControlStation.java:4418` (`editRoute` with the route's sensor, trigger, armed state and conditions, which keeps its id, lock and autonomy selection); behaviour.md :2198-2199 |
| **Needs execution** | yes |

The code keeps all of them today: `editRoute` re-adds the route under its own id, puts its lock and autonomy selection back, and is handed the conditions and trigger.  The claim asserts none of those, so a split that built the route afresh - a new id, which unbinds its tile; no lock; no conditions - passes.

**Direction:** assert, in the import claim, that his route keeps its conditions, id, lock and trigger.

**Verification request.**  Two mutations, one at a time, in `splitRoutesThatMixAStop`: pass `null` for the conditions; and replace the `editRoute` call with `deleteRoute(name)` and `newRoute(name, rest, ...)`.  Run `core.testAStopRouteStandsAlone`.  **Proves:** green under each.  **Refutes:** red.

---

### RLU5-C6 - `core.testAStopRouteStandsAlone` is not in `build.xml`: `ant test` does not run the split's claims, and `regression.testEveryTestIsInTheBattery` is red at HEAD

| | |
|---|---|
| **Disposition** | Fixed as RLA5-C2. |
| **Grade** | C - a test bookkeeping gap: `battery.sh` finds classes on disk and runs the new one, but the NetBeans and `ant test` run Adam uses does not, and the guard for exactly this fails in the next full battery.  New in `19d82501`.  Needs execution. |
| **Names** | Adam's ruling of 2026-09-25 (claims `19d82501`); DD-A2 (the guard's reason) |
| **Where** | `build.xml` (no `<test-one-class class="testAStopRouteStandsAlone"/>`; its precedent, `testAnImportSaysItsRoutesAreOff`, is at `:485`); `test/regression/testEveryTestIsInTheBattery.java:80-95`; `docs/tools/battery.sh:606` (every `test/*.java`) |
| **Needs execution** | yes |

The class carries `@Test` and is in no `test-one-class` line, and `DELIBERATELY_OUT` holds only `testAutoDetect`.  The fix commit lists what it ran green - "the route classes, the bundles, the javadoc ratchet and the event-thread census" - and the membership guard is not among them.  (Its data-directory skip means `ant test` would skip it anyway; the registration is what keeps the guard green and the skip visible, as for `testAnImportSaysItsRoutesAreOff`.)

**Direction:** the `test-one-class` line.

**Verification request.**  Run `regression.testEveryTestIsInTheBattery`.  **Proves:** red, naming `testAStopRouteStandsAlone`.  **Refutes:** green.

---

### RLU5-C7 - Deleting a stop route the split made says nothing of the route that fires it: that route's power cut goes, and it is asked about again at the human doors

| | |
|---|---|
| **Disposition** | Follow-up - GSR-B4 (open): a route another route fires can be deleted on its own, and the split's stop route is one such; the general fix is GSR-B4's. |
| **Grade** | C - the consequence is a stop that does not fire, but only through a delete the operator confirms, of a route whose name says "(Emergency Stop)".  The exposure is new: the split makes a disarmed, sensorless route the operator never made, which reads as debris in the route list.  No execution needed. |
| **Names** | Adam's ruling of 2026-09-25 (fix `894c52c8`); RLU5-A1 |
| **Where** | `TrainControlUI.java:19716-19745` (Delete asks "Delete route {0}?" and nothing more); `MarklinControlStation.java:3747` (`deleteRoute` touches no other route's commands); `MarklinRoute.java:1093` (a Route command whose route is gone logs `route.warningRouteNotExistCalledFrom` and sends nothing), `:442` (`hasEmergencyStop` finds the stop route by name) |
| **Needs execution** | no |

A rename is carried into the route that fires it (`otherRouteRenamed`); a delete is not, and not said.  Once the stop route is gone, the route it was split from throws its switch and sends no stop, and since `hasEmergencyStop` no longer finds a stop it is asked about at the route list and its tile - the question Adam's ruling of 2026-09-01 took away from a route with a stop.

**Direction:** the delete confirmation names the routes that fire the route being deleted, as deleting a locomotive names the routes that command it.

---

### RLU5-D1 - RLU4-B1: every file that would replace the configuration in use is refused, after it is read and before anything is asked or written, and the claim imports the bare shape

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |
| **Where** | `AutonomyViewerPanel.java:1072-1080`; `AutonomySession.ImportFormat`; `testTheImportDoorReadsAnOldFile.testABareConfigurationIntoTheConfigurationInUseIsRefused` |

The refusal is now `format != LEGACY_GRAPH`, and `UNKNOWN` has already returned, so a bundle and a bare configuration are refused alike - before the question (`:1095`) and before anything is created, on the main window.  The claim writes the configuration in use in its own on-disk shape with one priority added, asserts the shape is read as `CONFIGURATION`, and fails on the missing message and on the replace question if either comes back.  The Import menu has one action for both of its entries, and the viewer panel's button is the same method.

### RLU5-D2 - RLU4-C1 and RLU4-C2: the busy refusal comes before the question and writes nothing, and the in-use claim now reads a setting the file brings

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |
| **Where** | `AutonomyViewerPanel.java:1086-1091`, `:1286`, `:1428`; `testAnOldFileIntoTheConfigurationInUseIsRefusedWhileAutonomyRuns`; `testAnOldFileIntoTheConfigurationInUseLeavesItsTrainsToTheRailway` |

`stagingFlowActive` makes `isAutonomyBusy()` true, and the claim asserts the refusal and that the configuration, the names and setup.json are unchanged.  The in-use claim takes out of Main a priority the MT-491 file carries, rebuilds without a capture, imports, and asserts the priority is back.  Under RLU4-C2's two mutations - the capture block deleted, or `loadAfterImport(into)` - the reload captures a running layout that has no priority there and removes it, so both are red by reading, as the records' V3 says.  It also asserts the new question.  What it words is RLU5-C2.

### RLU5-D3 - RLU4-C4, RLU4-C6 and RLU4-C7 in the program: the either-way sentence names the closed side with GUI4-C3's exception, the barred sentence says where the menu is, the new sentence names its reason, in all eight languages, and WS13 is red without the guard

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |
| **Where** | `Layout.java:5052-5095`; the four `autolayout.why.startReachesNoStation*` keys in eight bundles; `testWhyStuck` :708-723 and `testOpeningASideIsNotOfferedWhereEveryTrainTurns` |

The loop keeps the first refused copy; one not barred gives the refused sentence, one barred where every train turns gives the new `...OtherWayBarredMustTurn`, and the rest the barred sentence with "in the autonomy editor".  Decoded in all eight, each says what English says, the exception included ("außer dort wendet jeder Zug", "sauf si tout train y fait demi-tour", "chyba że każdy pociąg tam zawraca").  The either-way claim asserts the arrivals menu in the sentence and the exception in the English bundle; WS13 marks both platform copies as turning ones, asserts the preconditions, and asserts the new sentence and no arrivals menu, so dropping the guard is red.  RLU4-C7's third bullet - the train's own-way remedy - is not carried, and its disposition does not claim it.

### RLU5-D4 - RLU4-C5 and RLU4-C8: the user guide and behaviour.md give the four cases, and the store carries every status note the disposition names

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |
| **Where** | `Automation.md:311-316`; behaviour.md :705; `docs/manual-tests/triage.db`, table `finding` |

Automation.md now lists the four cases the sentences give, each with its remedy, and behaviour.md :705 the same with its tests.  Read with `mode=ro`: RLA2-B1, RLA2-B3, RLA2-C4, RLD2-C2, RLD2-C3, RLU2-C10, RLU3-C1, RLU3-C2, RLU3-D7, RLA3-D1, RLD3-D2, RLA-C2 and RLU-C4 each carry a round-4 status note naming what superseded them; RLU2-C13 is "Open - deferred" as OB-305's; RLD3-C4 is Closed by RLD4-C9's pin; RLD3-C3 has its twins' status.  The store holds 4,666 rows for 4,309 refs, as behaviour.md and open-questions.md now say, and every RLU4 row matches its disposition.

### RLU5-D5 - The eight bundles at HEAD hold 1,848 keys each and agree; the javadoc ratchet still counts 86

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |
| **Where** | `src/org/traincontrol/resources/messages*.properties` at `4ae6bb51` and HEAD; `testJavadocsAreAttached.ALLOWED` and `ORPHANS_BY_FILE` |

Read in memory: 1,843 keys in each at `4ae6bb51` (round 4's two new ones) and 1,848 at HEAD (the five route keys), the same set in all eight, no duplicates, every placeholder set equal to English's, no byte above 127, no straight apostrophe in a value with a placeholder, and the only empty values Italian's and Polish's plural suffix.  The round-4 keys and the five route keys, decoded, say the same in every language (their wording is RLU5-C3).  The orphan rule over every `src/` file at HEAD counts 86, with `MarklinControlStation.java` 1, `RouteEditorFrame.java` 3, `TrainControlUI.java` 23 and `AutonomyViewerPanel.java` 2 as pinned; the new javadocs each sit on a member.

### RLU5-D6 - The emergency-stop rule at every door that makes, changes, loads or syncs a route: no door the program offers leaves a stop among other commands, a split keeps what the route was, and the notice and the no-question rule hold one route deep

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |
| **Where** | `RouteEditorFrame.java:2667-2684`; `MarklinControlStation.java:2077`, `:2332`, `:485`, `:4337`, `:4393-4430`, `:2092-2155`; `MarklinRoute.java:416`, `:434-448`, `:933-946`; `CS2File`'s route commands; `TrainControlUI.java:21710`, `:21902`, `:29184` |

The editor refuses a stop among other rows; `newRoute` from input and `editRoute` refuse it before anything is deleted, so Duplicate (`newRoute`) and Enable, Disable and Bulk (`editRoute`) go through the rule; the start-up restore and Routes > Import split every mixed route after all routes are in - the two `newRoute` overloads they use, which take an id or a built route, do not refuse, and in the program only they, the station's sync, `editRoute` after its own check, and the split reach them; the Central Station's routes carry only switch, speed, direction and function commands.  `editRoute` keeps the id (and so the tile), the lock, the autonomy selection, the conditions, the trigger and the armed state; an armed route's old monitor exits on its next pulse.  A split route fired by its sensor passes `auto` to its stop route, which shows `route.ui.infoPowerAutomaticallyTurnedOffBecauseRouteTriggered` naming the stop route - whose name carries the parent's.  `hasEmergencyStop` counts a route that fires a stop-only route, so the split route is not asked about, as before; a route that fires the split route was asked about before and still is.  The start-up split shows no window (OB-170's rule) and the log names both halves.  Beyond one level, and with the stop route gone, it does not hold: RLU5-A1, RLU5-B1 and RLU5-C7.

### RLU5-D7 - Every message the round added belongs to a window that is showing, and the owner census reads the three files it names

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |
| **Where** | `test/ui/testWhereHisTrainsMayBeSent.java:203-240`; `LayoutRightclickAutonomyMenu.java`, `LayoutEditorRightclickMenu.java`, `AutonomyViewerPanel.java`; `TrainControlUI.java:25223` |

Tallied in memory, the owners of every `JOptionPane.show` and `AutonomyReport.show(` call: the diagram's menu 13, all `ui`; the editor's menu 13 `edit` and 1 `owner`; the viewer panel 31, all `ui`.  The census now counts the tidy report and reads the panel, so `this` back as any owner is red (RLD4-C9).  Round 4's two new dialogs are owned by `ui`; Routes > Import's message by the main window; the editor's refusal by the editor.

### RLU5-D8 - MT-507 and MT-508 are superseded on Adam's answer by tests that exist and assert what their comments say; the tail question's list (MT-575) is untouched since round 3

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D - checked and found right |
| **Where** | `docs/manual-tests/tests.md` MT-507, MT-508 and the ledger; `ui.testARouteOverATrainAtItsDoors.testCancelAtEitherDoorRunsNothing`; `regression.testARouteDoesNotThrowSwitchesUnderATrain.testOKFiresEveryCommandOfTheRoute`; `TailCrossedPrompt.java` |

Each entry's disposition changed and a dated comment was appended, its steps untouched; each comment quotes the answer, names a method that exists and is in `build.xml`, and says what it asserts, which is what the test does: the stop as a Route command firing a stop-only route, no question with it, the held switch left alone, the other thrown, the power off, and the question and OK or Cancel without it.  The ledger's 12 to 10 matches.  `git diff f7e94a50 HEAD` touches neither `TailCrossedPrompt.java` nor `TrainControlUI.openLayoutEditorWindow` (its two hunks in `TrainControlUI` are at :2962 and :25213), so MT-575's list opens where rounds 1 to 4 left it.

---

Counts: 1 A, 2 B, 7 C, 8 D.
