# RLV6 - Validation, round 6: round 5's fixes and the emergency-stop change, one validator over autonomy, the windows and bundles, and the documents and tests (TrainControl 3.0.0)

**Status:** open

**Prefix:** RLV6

**Reviewed:** branch `autonomy-diagram-r0` at `806e81eb`, working tree clean by `git --no-optional-locks status --porcelain`.  Scope `4ae6bb51..806e81eb`: the emergency-stop change (claims `19d82501`, fix `894c52c8`, test `92a0f20e`, tracker `ebf4f919`) and round 5 (claims `ec947f34`, fixes `0f62a7ca`, records `fb7f9dc3` and `806e81eb`), reaching into round 4's `d5b97ee8` only where `0f62a7ca` touches it.  Read-only, 2026-09-26.

## Method

Nothing was compiled or run and no JVM was started.  No git state changed: only `git log`, `git show`, `git grep`, `git ls-tree`, `git diff --stat` between commits and `git --no-optional-locks status`.  Nothing under `cs2_sample_layout/` was read or written.  This file is the only one written.

**Read whole:** the brief; `docs/reviews/README.md` (the rules and the document shape); `docs/reviews-2026-09-25/RLA5.md`, `RLU5.md` and `RLD5.md`, every finding and disposition.

**The commits,** with `git show`:

- `0f62a7ca` in full: source, behaviour.md, and the eight bundles, decoded;
- `ec947f34` in full: every test hunk, build.xml and the live-snapshot README;
- `894c52c8`: its `gui/`, `model/`, Readme, behaviour.md and test hunks (its `marklin/` half read at HEAD);
- `92a0f20e` and `19d82501`'s UI-test hunks;
- `806e81eb`'s document hunks, and `fb7f9dc3` by its message and stat.

**The code at HEAD, around each change:**

- `MarklinRoute`: :300-560 (both public `execRoute` doors, `conflictingAccessoryAndReason`, `hasEmergencyStop`, `cutsThePowerAt`, `isOnlyStops`, `hasAStop`, `mixesAStop`) and the private `execRoute` whole (:680-1180).
- `MarklinControlStation`:
  - the start-up restore and split (:455-500);
  - the sync's route loop (:1495-1566);
  - `editRoute`, `isLocalRouteId` and the three `newRoute`s (:2054-2360);
  - `chainDrives` (:3420-3470);
  - `importRoutes` and `splitRoutesThatMixAStop` (:4200-4464).
- `AutonomyViewerPanel`: both `loadAfterImport`s, `loadActive` and both `load`s (:660-860); `importConfiguration`'s refusals (:1060-1120).
- `TrainControlUI`:
  - the flag (:596), the exit save (:2515-2575), `isSetupNewerThanTheRunningLayout`, `captureRunningLayout` and `resetAutonomySession` (:2965-3086);
  - `autonomyLoadedFromDiagram` (:4027-4143) and `prepareAutonomyReload` (:4184-4218);
  - both `rebuildRunningLayoutFromSetup`s (:6236-6264, :6553-6735), `wrappedToFit` and `autonomyEditorClosed`'s head;
  - `unloadAutonomy`; `layoutEditingComplete`, `layoutRefreshComplete` and `layoutRefreshCompleteInternal` (:23268-23415);
  - `isAutonomyBusy` and `reloadActiveDiagramConfiguration`.
- Elsewhere:
  - `Layout.isRunning`, `stopLocomotives` and the dispatch's arrival wait (:8470-8500, :8695-8715); `Locomotive.waitForOccupiedFeedback` (:745-855);
  - `AutonomyMenu` (:80-180, :270-340, :640-665); `LayoutRightclickAutonomyMenu`'s Place items (:670-800);
  - `RouteEditorFrame`'s stop gate (:2667-2690) and Save's tail (:3350-3380); `LayoutEditor`'s page switch (:6030-6115);
  - `CommandRow.hasDelay` and `of` (:300-490); `RouteCommand.toLine`; `Route.otherRouteRenamed`.

**Tests:**

- `core.testAStopRouteStandsAlone` whole; `ui.testARouteOverATrainAtItsDoors` (its fixture and the new chain method); `ui.testCommandTableMarks.testAnEmergencyStopStandsAlone`;
- `regression.testTheImportDoorReadsAnOldFile`: round 5's three hunks and RLD4-C3's claim; `regression.testTheRoutesImportDoorAsksByName`'s new method;
- `core.testRoutes`' generator; `regression.testEveryScenarioIsUsedAndSaysSo`'s user rule; `regression.testEditorSurfaceRules.testARebuildLeavesTheTrainsWhereTheyAre`.

**Documents:** behaviour.md :2189-2205, :2448-2462 and :2506-2510; open-questions.md :420-432; tests.md MT-508's comments; Readme.md :398-401 and :758; Automation.md :283.

**Mechanical checks, all in memory** (inline Python fed through a heredoc, reading `git show` output or opening the store read-only; nothing written):

- the eight bundles at `ebf4f919` and HEAD: key sets, duplicates, placeholder sets against English, bytes above 127, empty values, and straight apostrophes in values with a placeholder; and twelve keys decoded in all eight languages;
- his frozen `test/layouts/live-snapshot/config/gleisbilder/routes.json`: its ids, which routes carry a stop, and which carry a Route command;
- `docs/manual-tests/triage.db` (`mode=ro&immutable=1`): row and ref counts, the fifty round-5 rows and their statuses, and MT-508's block in the `test` table.

`docs/manual-tests/findings.tsv` was searched before each finding.  The terms were `layoutRefreshComplete`, `layoutEditingComplete`, `resetAutonomySession`, `23402`, "diagram edit", "another configuration", `seen`, WKW-B2, AMS-B1, ACC-B3, RLD4-C3, GSR-B4, DW-A1, DIR-A3 and SVN-A4.  None of the findings below has a catalogued row of its own; the ids each names are its parents.  The prefix RLV6 is free.  Not reported, as the brief says: RLA3-B1 with RLU3-C4, OB-300 to OB-305, RLU2-C9, RLD-C1 and GSR-B4 (which RLA5-C3, RLU5-C7 and RLD5-C2's second half defer to).

**Everything below is from reading.**  Each finding that needs a run says so, with the fixture and what proves or refutes it.

**Counts:** 0 A, 1 B, 3 C, 8 D.

**Nothing new above C.**

- **RLV6-B1 is a remainder.**  It is of round 4's `d5b97ee8` at two of its doors, and of WKW-B2's fix (2026-09-14) at the third.  Round 5 named all three doors, and its fix reached only the idle reload of the configuration running, so the dispositions "Fixed as RLA5-B1" on RLU5-B2 and RLD5-B1 are partial.
- **The one new behaviour inside it** is autonomy left reading busy after a reload confirmed during a run.  On its own that would grade C: a window stuck, no railway harmed, reached only after the race.
- **The three Cs are new in the past day.**

By the brief's rule this is the signal to stop, once RLV6-B1 is fixed or deferred on Adam's word.

---

### RLV6-B1 - RLA5-B1's fix reaches one of the four doors round 5 named; three still put every moved train back on its pre-run square and leave the flag up

The three doors are: the reload after a route or track-diagram edit; loading another configuration; and a reload confirmed while a run is going.  At the last, autonomy is now also left reading busy.

| | |
|---|---|
| **Disposition** | Fixed - while a declined edit waits, every load carries each train across and lowers the flag once a layout carries the edit: the configuration running and the one a reset has forgotten the name of (door a), another configuration after carrying the one running (door b), and after the reload's question rather than through the rebuild that refuses while busy (door c).  claims 528fd305 (red first), fix 04b1b204; mutations X1 red, X2 red, X3 red, X6 red. |
| **Grade** | B.  The consequence is DW-A1's (graded A): the running layout, and after the next fold the configuration, stands a train on a square it has left.  Occupancy is placements, so Start can route another train into the block it really occupies.  The reach is only after the one-event race that raises the flag, so one grade down, as RLA5-B1 and RLD4-C3 were.  **A remainder, not new:** door (a) since WKW-B2's fix (2026-09-14); doors (b) and (c) since round 4's `d5b97ee8` (RLD4-C3's fix).  Round 5 named all three: RLA5-B1's text and RLD5-B1 name (a); RLU5-B2 names (a), (b) and (c), whose first door is *"choosing any configuration ... (the one running included)"*; RLD5-B1 also names (b).  New in `0f62a7ca` at door (c) only: autonomy left busy. |
| **Names** | RLA5-B1, RLU5-B2, RLD5-B1 (partial dispositions); RLD4-C3, WKW-B2, AMS-B1, ACC-B3, OB-183, DW-A1, OB-143 |
| **Where** | **The fix:** `AutonomyViewerPanel.java:774-781` (the branch asks for the configuration running *by its active name*), with `:790-803` (the capture, skipped while the flag is up).  **The rebuild:** `TrainControlUI.java:6623` (its body is skipped while `isAutonomyBusy()`) and `:6721-6725` (the only line that lowers the flag).  **Door (a):** `:23392-23403`, where `resetAutonomySession` runs `captureRunningLayout`, which returns at `:3029` with the flag up, then nulls the active name at `:3080`, then `load(wasRunning, false)`.  It is reached from `layoutEditingComplete` (`:23291`), which `RouteEditorFrame.java:3378` (Save), `LayoutEditor.java:6113`, `:6419` and `:7396` (the track mode) and four page operations (`TrainControlUI.java:25677`, `:25935`, `:26097`, `:26213`) call.  **Door (b):** `AutonomyMenu.java:310-316`.  **Door (c):** `TrainControlUI.java:4184-4218` (`prepareAutonomyReload`: the question, speed 0 on every active train, `stopLocomotives`); `Layout.java:1795-1799` (`isRunning`: any active train or locomotive thread); `Locomotive.java:787-825` (the arrival wait ends only on its sensor); `Layout.java:8483-8487` (*"parked forever in waitForOccupiedFeedback ... activeLocomotives never emptied, so isRunning() stayed true ... blocked until the graph was reloaded"*); `TrainControlUI.java:2534-2538` (the exit captures nothing while the layout reads running).  **The records:** `captureRunningLayout`'s javadoc, `:3025-3028` |
| **Needs execution** | yes - see the request |

**What the fix does.**  `load` now sends one case to `rebuildRunningLayoutFromSetup(false, null)`: a capture was asked for, the flag is up, and the name is the active one.  That rebuild records `whereTheTrainsAre`, loads without a capture, puts the trains back, and lowers the flag.  Every other `load` with the flag up still does what round 4 left it doing: skip the fold, rebuild from the configuration's pre-run placements, and leave the flag up.  Three doors reach that.

**(a) The reload after a route or track-diagram edit.**  `layoutEditingComplete` runs after the route editor's Save, after the track-diagram editor in track mode, and after the page operations.  `layoutRefreshCompleteInternal` then:

1. calls `resetAutonomySession`, whose `captureRunningLayout` returns because the flag is up;
2. has the active name nulled by that reset;
3. calls `load(wasRunning, false)`.

That `load` asks for a capture.  But the name no longer equals the active one (null), so the new branch is skipped.  The capture needs an active name too, so it is skipped as well.  The railway is rebuilt from the setup, with every train the run moved back where it started, and the flag stays up.

With the flag down, `captureRunningLayout` folds first and nothing is lost, so the flag alone decides.  Saving any route in the route editor is far commoner than the Autonomy menu's radio.

**(b) Another configuration.**  Choosing it from the radio skips the branch, because the name is not the active one, and skips the capture, because of the flag.  Two things follow:

- **The configuration left keeps its pre-run placements, silently.**  The exit's identical loss logs `placementsNotSaved`; this one logs nothing.  Choosing it again later (no run of the other in between) builds it with the trains on squares they left.
- **The flag stays up over a configuration whose running layout was just built from its own setup.**  Its exit save and `captureRunningLayout` then skip until some setup gesture's rebuild lowers the flag.  That is AMS-B1's remainder; a load after Unload does the same.

Round 5 asked for this door: RLU5-B2's direction was *"choosing another configuration, fold the placements only, or refuse with a message until the edit is carried"*.

**(c) The configuration running, reloaded while the run goes on.**  This is the state the flag is raised in.  The door is the radio, or the reload after an import under another name.

1. `prepareAutonomyReload` asks *"Locomotives are still running.  Reloading will stop them and abandon the current run.  Continue?"*.
2. Yes clears `running` and sets speed 0 on every active train.
3. The branch hands the reload to `rebuildRunningLayoutFromSetup`, whose whole body is guarded by `!isAutonomyBusy()`.  `isRunning()` is still true while any train is active or any locomotive thread lives, so nothing is rebuilt.  `sayIfDeclined` is false, so nothing is said.

**The part `0f62a7ca` adds.**  A train stopped between sensors never reaches the one its thread waits for.  The wait ends only on the feedback: Layout :8483-8487 records this as *"blocked until the graph was reloaded"*.  Before `0f62a7ca` the load replaced the Layout, and `isAutonomyBusy` asked the new one.  Now nothing replaces it, so:

- Start and Graceful Stop stay dark (OB-143's coast-down, for good);
- setup editing is refused;
- choosing the configuration again repeats all of this.

**The ways out** are door (b) or a restart.  A restart's exit captures nothing while the layout reads running, and both rebuild the configuration from its pre-run placements.  His snapshot has one configuration, Main, so for him it is a restart.

**The records.**  `captureRunningLayout`'s javadoc still says *"where the trains stand is carried across rebuilds by `putTheTrainsBack` rather than written ... The rebuild that replaces the running layout clears the flag"*.  That is true of `rebuildRunningLayoutFromSetup` only.  RLU5-B2 and RLD5-B1 asked for it to be corrected.  behaviour.md :2459-2461 speaks of imports only, and is right for them.

**Direction.**

- In `load`, with the flag up, record `whereTheTrainsAre` before anything is replaced.
  - For the configuration running, put the trains back after.  This includes where `layoutRefreshCompleteInternal`'s reset has just forgotten the name; it holds `wasRunning`.
  - For another configuration, fold the placements alone into the one left.
- After a Yes at `prepareAutonomyReload`, go ahead as the flag-down load does, rather than through the rebuild's busy guard.
- Lower the flag whenever a load replaces the running layout with a valid one (AMS-B1's rule, at `load`).
- Correct `captureRunningLayout`'s javadoc, and add a claim with a moved train at each door.

**Verification request.**  Use `testAReloadWhileAnEditWaitsKeepsWhereTheRunLeftTheTrains`' fixture: the live-snapshot sandbox, the window, Main running, idle.  Move one standing train on the running railway only, to an empty station, as that claim does, and set `setupEditDeclinedDuringRun` true by reflection.

- **(a)** Call `ui.layoutEditingComplete(done)` and wait for `done`; the route editor's Save runs `layoutEditingComplete()`.
  - **Proves:** `getAutoLayout()` stands the train on its old square, and the flag is still true.
  - **Refutes:** it stands on the moved-to square.
  - **Control:** the flag false gives the moved-to square.
- **(b)** First, with the flag false, import MT-491 under a new name so a second configuration exists.  Then move the train and raise the flag, and call `getAutonomyViewerPanel().load(<the new name>, true)` on the event thread.
  - **Proves:** Main's configuration in the store still places the train on its old square, and the flag is still true after the other is loaded.
  - **Control:** the flag false, and Main places it on the moved-to square.
- **(c)** Use the sandbox's model with no sensor echo, so nothing sets a feedback.  Dispatch one train so that its thread waits on its first sensor, and assert `isAutonomyBusy()`.  Raise the flag, answer Yes to `confirmReloadJsonStopsRunningLocomotives`, and call `load(Main, true)`.
  - **Proves:** `getAutoLayout()` is the same object afterwards, and `isAutonomyBusy()` is still true thirty seconds later.
  - **Refutes:** a new Layout, or not busy.
  - **Control:** the flag false gives a new Layout and not busy.
  - **The no-op half alone:** it can be shown with `stagingFlowActive` true in place of the dispatch, as `testAnOldFileIntoTheConfigurationInUseIsRefusedWhileAutonomyRuns` makes autonomy busy.

---

### RLV6-C1 - With the flag up, the Autonomy menu's, a page tick's and an import's reload of the configuration running is the editor doors' quiet one: hand-driven trains are not stopped, and a setup that will not build is reported only in the log, as a failed start-up resume

| | |
|---|---|
| **Disposition** | Fixed - the carried load is the one asked for, interactive or not, so a train driven by hand is stopped as the same reload stops it with nothing waiting.  claims 528fd305 (red first), fix 04b1b204; mutation X4 red. |
| **Grade** | C.  One gesture behaves two ways on a flag nobody can see, and nothing on the railway is modelled wrong.  New in `0f62a7ca`. |
| **Names** | RLA5-B1 (its fix); VD11-A2 (the interactive load's stop); Adam's ruling of 2026-09-04 (the editor doors do not stop trains) |
| **Where** | `AutonomyViewerPanel.java:774-781`; `TrainControlUI.java:6707` (the rebuild's `load(activeDiagramConfiguration, false, false)`); `AutonomyViewerPanel.java:817-830` (interactive: the dialog; otherwise `infoResumeFailed`), `:858` (`autonomyLoadedFromDiagram(name, !interactive)`); `TrainControlUI.java:4063-4071` (*"Choosing a configuration is that"*: `if (!resumed) AltEmergencyStopActionPerformed(null)`, which is `stopAllLocs`); `messages.properties:1542` (*"... could not be loaded at startup ..."*); `TrainControlUI.java:25005-25016` (`reloadActiveDiagramConfiguration` and its comment); `AutonomyMenu.java:313` |
| **Needs execution** | yes, for the stop - see the request |

The branch does not load the configuration the way the operator asked.  It hands it to `rebuildRunningLayoutFromSetup`, whose own load is non-interactive.  With the flag down, the same three gestures load interactively, and two things differ.

- **The stop.**  An interactive load stops every locomotive.  VD11-A2's rule: the boundary is *"whether the OPERATOR asked for a different railway.  Choosing a configuration is that"*.  With the flag up, the radio, a page tick and the reload after an import stop nothing, so a train being driven by hand rolls on across the rebuild.  The editor doors skip the stop on Adam's ruling of 2026-09-04.  These doors are not editor doors, and with the flag down they do stop.
- **A setup that will not build.**  Interactively, the dialog *"This setup cannot be used yet ..."* appears.  Now the log alone says `infoResumeFailed`, *"could not be loaded at startup"*, about a load nobody started at start-up.
  - At a page tick this is the case `reloadActiveDiagramConfiguration`'s comment exists for: a refused rebuild *"leaves the OLD layout running - page included - while the menu and the strip both say the page is out ... refusing it silently reintroduces it in the one case that matters"*.

The claim loads with `interactive` false, so it cannot see either.

**Direction:** keep the operator's `interactive` in the branch.  For example, carry the trains round the load that would otherwise run - `whereTheTrainsAre`, `load(name, interactive, false)`, `putTheTrainsBack`, lower the flag - rather than delegating.  The same change serves RLV6-B1's door (c).

**Verification request.**  Use RLV6-B1's fixture, with no autonomy running.  Set one locomotive's speed to 40 by hand, raise the flag, call `getAutonomyViewerPanel().load(Main, true)` on the event thread, and wait a second.

- **Proves:** its speed is still 40.
- **Refutes:** 0.
- **Control:** the flag false gives 0.

---

### RLV6-C2 - `hasEmergencyStop` shares one visited set across every branch it walks, so a route that reaches a split route by two paths, the longer first, is counted as not cutting the power, is asked about, and Cancel calls off the power cut

| | |
|---|---|
| **Disposition** | Fixed - the walk remembers the path, not every route asked.  claims 528fd305 (red first), fix 04b1b204; mutation X5 red. |
| **Grade** | C.  The consequence is DIR-A3's: Cancel discards a power cut the question never mentioned.  But it needs a shape nothing makes and no railway is known to have: a route that fires the same split route directly and through another route, the other route listed first, with a switch of its own under a train.  His frozen routes have no Route command at all.  New in `0f62a7ca`, a partial of RLA5-A1's second half.  Before the split, the one-level check answered true for this shape, because the stop was the fired route's own. |
| **Names** | RLA5-A1, RLU5-A1, RLD5-A1 (their `hasEmergencyStop` half); DIR-A3; Adam's ruling of 2026-09-01 |
| **Where** | `MarklinRoute.java:451-469` (`cutsThePowerAt`; `if (!seen.add(this)) return false;` at `:453`); `:416` and `:919` (the two questions it takes away); `:701` and `:1123` (what firing does) |
| **Needs execution** | yes - see the request |

Take four routes:

- XS = [stop];
- X = [switch, Route XS], a split route;
- B = [Route X];
- A = [switch under a train, Route B, Route X].

**The walk.**  A runs at 1; B runs at 0; X at -1 answers false, since it is not stop-only, and is marked seen.  Back in A, the direct Route X meets X already seen, and answers false.  So `hasEmergencyStop()` is false.

**The firing.**  Firing A runs X at 0, and X fires XS at -1, which a stop-only route may.  The power goes off.

So the route list and the tile ask about A's switch, and Cancel returns before anything runs, stop included.  Swap A's two Route commands and the walk answers true.

**The set is not needed for the walk to end.**  Every step lowers the limit.  A route below 0 that is not stop-only answers false at once, and a stop-only route fires nothing.

**Direction:** drop the set, or key it on the route and the limit; add a claim with both orders.

**Verification request.**  Use `ui.testARouteOverATrainAtItsDoors`' fixture (the echo on).  Add XS, X, B and A as above, each through `newRoute(MarklinRoute)`.

- **Proves:** `A.hasEmergencyStop()` is false, while `A.execRoute(false)` with the power on turns it off.
- **Refutes:** true.
- **Control:** A' = [Route X, Route B] answers true.

---

### RLV6-C3 - Two sentences the round wrote now read wrongly: open-questions.md's list of store additions has lost a conjunction, and behaviour.md's "Until then" now follows RLA5-B1's sentence

| | |
|---|---|
| **Disposition** | Fixed in the records - the comma, and "Until then" named for what it follows (04b1b204). |
| **Grade** | C.  Cosmetic records.  New in `806e81eb` (open-questions.md) and `0f62a7ca` (behaviour.md). |
| **Names** | RLD5-C4 (its fix); RLA5-B1; RLA2-B1 |
| **Where** | `docs/reference/open-questions.md:429`; `docs/reference/behaviour.md:2459-2462` |
| **Needs execution** | no |

**open-questions.md :429.**  It now reads *"the release review's second round 59 (RLA2, RLU2 and RLD2) its third 45 (RLA3, RLU3 and RLD3), its fourth 48 ..."*.  The "and" before "its third" went when the fourth and fifth were added.  The items still sum to the 4,716 stated.

**behaviour.md :2461.**  It reads *"... and the edit is carried from then on (RLA5-B1).  Until then a layout that already had configurations took the file's placements, homes and facings into the one in use, and the name asked for was thrown away."*  "Until then" means before the import took these rules.  At `f7e94a50` it followed the sentence about the old file placing none of its trains.  Round 4's and round 5's sentences were inserted before it, and now it reads as "until the edit is carried".

**Direction:** restore the "and"; begin the behaviour.md sentence "Before 2026-09-25," or move it up.

---

### RLV6-D1 - RLA5-A1, RLU5-A1 and RLD5-A1: a stop-only route runs at any depth, a route that fires a split route counts as cutting the power, and the claim builds the chain

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLA5-A1, RLU5-A1, RLD5-A1; the ruling of 2026-09-25 |
| **Where** | `MarklinRoute.java:435-488`, `:696-708`, `:976-999`, `:1123`; `ui.testARouteOverATrainAtItsDoors.testAStopReachedThroughAChainStillCutsThePower`; `MarklinControlStation.java:3438-3442` |
| **Needs execution** | no |

**The runtime.**  `execRoute` refuses below 0 only a route that is not stop-only.  `isOnlyStops` is true only for a non-empty route of stops, which fires nothing, so nothing can loop.  `auto` is passed down the chain, so the notice still shows at the sensor door.

**`hasEmergencyStop`** walks with `execRoute`'s own limits.  The one exception is RLV6-C2.

**The claim** builds top, then split, then stop.  It asserts the power on, fires the top through `execRoute(false)`, waits for the power off, and asserts `hasEmergencyStop()` on the top.

- W1, holding a stop-only route to the limit, leaves the power on: red.
- W2, counting one level, makes `hasEmergencyStop` false: red.
- Only the chain can turn the power off, so the fixture does not supply the answer.

**Side effects checked:**

- A stop-only route three routes down, refused before, now fires.  That is the ruling's *"routes with an emergency stop should still fire"*, and `hasEmergencyStop` agrees, so nothing is asked.
- V → W → split X still stops at X, as X did before the split, and V is still asked, as before.
- `chainDrives`' "ONE HOP" stays true for its question: a stop-only route drives no locomotive.

### RLV6-D2 - RLU5-B1, RLA5-C5 and RLD5-C2's id half: the stop route is numbered from 1000, and the claim imports his route alone at id 7

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLU5-B1, RLA5-C5, RLD5-C2; OB-155 |
| **Where** | `MarklinControlStation.java:4420`, `:2338-2343`, `:2221-2224`, `:1528-1543`; `core.testAStopRouteStandsAlone.testTheStopRouteIsNumberedAsAUsersRouteIs` |
| **Needs execution** | no |

**The numbering.**  `Math.max(ROUTE_STARTING_ID, max + 1)` gives the user door's range: 1000 where free, otherwise the next up.  The database always holds the route being split, so the maximum exists.  The sync replaces by id only the station's routes, whose ids are below 1000.  His database runs to 1001, so his stop route is 1002, as before.

**The claim.**  It imports his route alone at id 7, and asserts at least 1000; without the fix it is 8.  The import deletes every route first, so nothing else can raise the maximum.

**The delete half** of RLD5-C2 is GSR-B4's.  It is Open - deferred in the store, as RLA5-C3 and RLU5-C7 are.

### RLV6-D3 - RLA5-C4 and RLD5-C3: the stop's wait rides on the Route command in its place, and the route runs as it did

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLA5-C4, RLD5-C3; X8-C3 |
| **Where** | `MarklinControlStation.java:4398-4410`; `MarklinRoute.java:1161-1171`; `CommandRow.java:324-329`, `:402`, `:410`; `core.testAStopRouteStandsAlone.testALoadSplitsARouteThatMixesAStop` |
| **Needs execution** | no |

**The split.**  The stop route gets a fresh stop, and the Route command gets the stop's delay.

**The runtime.**  The pause after every command, a Route command included, is `SLEEP_INTERVAL + max(delay, 150)`.  So the command after it still waits the stop's wait, while the stop route's own thread cuts the power a thread-start after the Route command goes.

**The claim** asserts 700 on the Route command, so W5 is red.

**The editor.**  X8-C3's `hasDelay` gives a ROUTE row no delay, so the wait is not shown there and a Save from the editor drops it.  It did the same for a STOP row, which `hasDelay` also excludes, so nothing is lost that was kept before.  His route has no delay.

### RLV6-D4 - The messages round 5 changed say what each finding asked, in eight languages, and each claim reads the whole sentence

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLA5-C1, RLU5-C2, RLU5-C1, RLA5-C6, RLU5-C3, RLU5-C4 |
| **Where** | `AutonomyViewerPanel.java:1100-1107`, `:1369-1376`; `RouteEditorFrame.java:2684-2688`; `TrainControlUI.java:25228-25229`, `:6782-6809`; `LayoutRightclickAutonomyMenu.java:694-747`; `src/org/traincontrol/resources/messages*.properties` |
| **Needs execution** | no |

- **RLA5-C1 and RLU5-C2.**  The new key `autosetup.ui.errorImportIntoConfigurationInUseWhileRunning` names the configuration, says nothing was imported, and gives both ways on.  It is decoded in all eight, and the claim asserts it with the name.
- **RLU5-C1.**  `{2}` is now `layout.ui.menuPlaceLocomotive` with an ellipsis.  That is the item offered on a destination for the locomotive chosen in the main window while autonomy is idle, and the sentence now says to choose it there first.  The claim reads the sentence from `{0}` to its end.
- **RLA5-C6 and RLU5-C3.**  The refusal takes `route.kind.STOP` and `route.kind.ROUTE`, and all eight carry `{0}` and `{1}`.  `testCommandTableMarks` compares the editor's list with the exact formatted sentence, so W10 is red.  The other stop keys still say "emergency stop": they are the log's, the import's and the stop route's own name, all outside the editor, and the disposition claims only the editor's.
- **RLU5-C4.**  `wrappedToFit` (90 columns) is put round the split paragraph.  The claim allows 100 a line; the English opening alone is 146.
- **The bundles.**  There are 1,848 keys each at `ebf4f919` and 1,849 at HEAD (the one new key), and the same set in all eight.  There are no duplicates, every placeholder set equals English's, and no byte is above 127.  No straight apostrophe is in a value with a placeholder.  The only empty values are Italian's and Polish's `stats.ui.valuePluralSuffix`.

### RLV6-D5 - Bookkeeping and the claims: RLA5-C2, RLU5-C6, RLD5-C1 and RLU5-C5, and each round-5 claim red at `ebf4f919`

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLA5-C2, RLU5-C6, RLD5-C1, RLU5-C5 |
| **Where** | `build.xml` (the `testAStopRouteStandsAlone` line); `test/layouts/live-snapshot/README.md`; `test/regression/testEveryScenarioIsUsedAndSaysSo.java:195-213`, `:223-240`; `test/core/testAStopRouteStandsAlone.java:121-203` |
| **Needs execution** | no |

**The bookkeeping.**

- build.xml has the class.
- The class reads his routes through `support.Scenario.folderFor("live-snapshot")`.  That quoted name is what the scenario census counts as a user.
- The README lists `core.testAStopRouteStandsAlone` in the backticked form the census reads.

**The load claim (RLU5-C5)** gives its route conditions, a trigger other than the default, a lock and a delayed stop.  It asserts the id 84951, the trigger, the conditions as JSON, the lock and the wait.

- W6 (null conditions) is red by a NullPointerException on the conditions line rather than by the assertion message: still red.
- W7 (delete, then the user door) changes the id and loses the lock.
- Every value asserted is set before the split and read off the route the split leaves, so the fixture does not supply the answer.

**Each round-5 claim is red at `ebf4f919` by reading:** the chain (refused at -1), the reload (the pre-run square), the id (8), the wait (0), and the four messages (keys absent or different).

### RLV6-D6 - RLA5-B1's fix at the door it covers: the configuration running, reloaded idle with the flag up, keeps every train where the run left it and lowers the flag, and the claim moves a train

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLA5-B1; RLD4-C3 |
| **Where** | `AutonomyViewerPanel.java:774-781`; `TrainControlUI.java:6682-6725`; `testTheImportDoorReadsAnOldFile.testAReloadWhileAnEditWaitsKeepsWhereTheRunLeftTheTrains` and `testAnImportDoesNotFoldAnEditWaitingForItsRebuild` |
| **Needs execution** | no |

**The rebuild** records the trains, loads with no capture, puts them back, and lowers the flag once the layout is replaced and valid.  Because it loads with no capture, it cannot recurse into the branch.  Idle, neither `prepareAutonomyReload` asks anything, so no question is added.

**The claim** moves a standing train on the running layout only, raises the flag and reloads.  It asserts the moved-to square and the flag down, so W3 is red.

**RLD4-C3's claim** raises the flag again before its second import, and still reads its priority.

The doors this fix does not reach are RLV6-B1; what its reload loses is RLV6-C1.

### RLV6-D7 - The records round 5 wrote: the counts match the store, MT-508's comment is in both tests.md and the store, and the three deferrals are open

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLD5-C4, RLD5-C5; GSR-B4 |
| **Where** | `docs/manual-tests/triage.db` (read-only); `docs/manual-tests/findings.tsv`; behaviour.md :2509; open-questions.md :428-430; tests.md MT-508 |
| **Needs execution** | no |

**The counts.**  The store holds 4,716 rows for 4,359 refs, as behaviour.md and open-questions.md say.  The paragraph's twelve items sum to 4,716; its wording is RLV6-C3.

**The fifty rows** of RLA5, RLU5 and RLD5 are all in the store:

- 47 are Closed;
- RLA5-C3, RLU5-C7 and RLD5-C2 are Open - deferred, each naming GSR-B4;
- each row reads as its document's disposition does, and findings.tsv renders the same fifty.

**MT-508's comment** of 2026-09-26 is appended below the earlier one, with the steps untouched, and it is in the store's block for MT-508.  It names `ui.testARouteOverATrainAtItsDoors.testCancelAtEitherDoorRunsNothing`, which fires the MT-507 route - the same shape - from the route list and its tile.

### RLV6-D8 - The emergency-stop change as a change of its own: no door the program offers makes a mixed route, and the split keeps his route whole

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | Adam's rulings of 2026-09-25; RLA5-D6, RLU5-D6, RLD5-D6 |
| **Where** | `RouteEditorFrame.java:2667-2688`; `MarklinControlStation.java:485`, `:2077`, `:2127`, `:2332`, `:4337`, `:4374-4441`; `TrainControlUI.java:21710`; `test/layouts/live-snapshot/config/gleisbilder/routes.json` |
| **Needs execution** | no |

**The doors.**

- The editor's gate refuses a stop among other commands, and the model's two user doors refuse one before anything is deleted.
- Duplicate goes through the user door.
- The import and the start-up restore use the unguarded doors, and the split follows.
- `CS2File` builds no stop, and the sync adds none.
- `0f62a7ca` changed only the id, the wait and the depth rule on this path.

**The split** adds the stop route first, at a free id and name.  Then it re-adds the route under its own id through `editRoute`, keeping the lock, the autonomy activation, the conditions, the trigger and whether it is armed.  If the edit fails, the stop route is deleted again.

**His frozen routes:**

- 85 routes, ids 1 to 1001, two of them at 1000 or more;
- nine carry a stop, and one mixes: [accessory, stop] on s88 2012, beside a stop-only route on the same sensor;
- none has a Route command.

So at his first 3.0.0 start, the split makes *Auto Emergency Stop Bottom Secondary (Emergency Stop)* at id 1002.  Fired by its sensor, it runs at depth 1, and the notice names it.
