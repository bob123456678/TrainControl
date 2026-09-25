# Release-readiness review, TrainControl 3.0.0 - documentation and tests

**Status:** open
**Prefix:** RLD

Reviewed on 2026-09-25, branch `autonomy-diagram-r0`, from `de75f3d1` (the last closed round) to `146b25eb`. HEAD moved during the review: it was `1c6978c6` when the review started, and `146b25eb` (MT-575, MT-576 and MT-586 superseded) landed while it ran. That commit was read too.

**Method.** Read-only. Nothing was run, compiled or started. No git state was changed. Nothing under `cs2_sample_layout/` was read, and that includes the part of `ec865d82` that touches it. What was read:

- `git log`/`git diff de75f3d1..HEAD` for every file in this lane: `Automation.md`, `Readme.md`, `build.xml`, `docs/reference/behaviour.md`, `docs/manual-tests/README.md`, `issues.md`, the `tests.md` diff (all 99 hunks), `test/layouts/live-snapshot/README.md`, and the eight message bundles.
- The source changes those documents describe, read with the tests: `Layout.java` (the answered-0 length, OB-299, BPV-A1), `AutonomyViewerPanel.java` (the legacy import and `loadAfterImport`), `TrainControlUI.java` (Routes > Import, the three locomotive edit doors, the census guard) and `TailCrossedPrompt.java` (MT-575).
- For each of the 47 manual tests superseded or retired in the range: the entry's Steps, Expected and every Comment, printed from `tests.md`, then the test method its comment names, read in full, and checked against the rule this session added to `docs/manual-tests/README.md` (same door, same inputs, every expected outcome).
  - New classes read: `regression.testTheImportDoorReadsAnOldFile`, `testTheRoutesImportDoorAsksByName`, `testASetupMovesToAOnePageLayout`, `ui.testWhereHisTrainsMayBeSent`, `testThePlaceDoorsKeepTheHeading` and `testARouteOverATrainAtItsDoors`.
  - Additions read: `testAHandSendIsRefusedWhileTheSetupIsBroken`, `testMassAssignLengths`, `testATrainDoesNotRunIntoItsOwnTail`, `testAutonomyDiagramSession`, `testAnAnsweredZeroIsNotMissing`, `testRoutePicking`, `testMessageBundles`, `testALocomotiveDoesNotEvictItself`, `testAPlacedTrainRecordsWhereItCameFrom`, `testApplyIsGreyedWithNothingToApply`, `testARouteDoesNotThrowSwitchesUnderATrain`, `testTheTailIsPickedOnTheDiagram` and `testTheOldAutonomyTabIsGone`.
- The Readme's v2.8.2 entry, compared with `master` using `git show master:Readme.md`, and master's 2.8.2 commits using `git log HEAD..master` and `git show`.
- Bundle keys checked two ways: a `grep`/`comm` pipe of every key-shaped string literal in `src/` against `messages.properties`, and the `Grep` tool across all eight bundles.
- `findings.tsv` searched before each finding was raised. The prefix `RLD` was free.

**Two stray files, both removed.** An intermediate key list was written to `%TEMP%` and deleted within the minute. A `grep` given a `\u` pattern crashed and left `grep.exe.stackdump` (untracked) in the repository root at 13:53. It was deleted, and `git status` is clean again.

The D findings are checks that came back clean, and are open only until the triage sets them.  Every finding marked **needs execution** carries a verification request: the fixture, the result that proves it, and the result that refutes it.

---

### RLD-B1 - 3.0.0 still saves over an unreadable locomotive database when its safety copy fails; 2.8.2 does not (BPV-C7 was never brought across)

| | |
|---|---|
| **Disposition** | Fixed - master's BPV-C7 brought to 3.0: the unreadable mark is cleared only once the copy exists, and until then the save leaves the file as it is.  Claims 66319d3c (red first, both halves), fix e7a2f1fa; mutations R3 red, R4 red. |

**Where.** `MarklinControlStation.java:1797-1820` and `TrainControlUI.java:2428-2430`. Master fixed this in `86b8b73a`, with the claims in `103375b1`.

**What happens on 3.0.0.** TrainControl sometimes finds `LocDB.data` (the locomotive list) or `UIState.data` (the keyboard pages) unreadable at start. On the next save it first copies the unreadable file into `tc_backup`. The problem is the order:

- Both saves clear their "unreadable" mark *before* the copy is attempted.
- If the copy then throws (the `tc_backup` folder cannot be made, the disk is full, OneDrive holds the file), the failure is logged and the save carries on.
- The save then writes the session's empty database over the only copy of the file.

This is the shape master's BPV-C7 fixed for 2.8.2. On master the mark is cleared only once `Files.copy` has returned, and until then the save leaves the file alone. The master commit says so itself: *"3.0 has the same shape (df5d291b), so this goes beyond the port"*.

**What a user sees.** The copy has to fail first, so this is rare. When it does, a 2.8.2 user who upgrades to 3.0.0 loses the protection 2.8.2's release notes promise. Master's line reads *"...and the file is not saved over until that copy has been made"*. The locomotive customizations, or the keyboard pages, are then written over.

**Why B and not A.**
- It needs two faults together: an unreadable file at start, and a failed copy.
- The copy failure is logged, so the loss is not silent.
- Master graded the same shape C.

The consequence is still data loss, and it is the one line of 2.8.2's changelog that 3.0.0 does not honour. This overlaps the Regression lane. It is raised here because the carried Readme entry (RLD-C1) is where the gap hides: the branch copy lacks exactly that clause.

**The other master-only commits since the backport were checked**, by provenance in their messages and by grepping the branch:
- BPV-C9 (gateway ping), BPV-C12 (skipped timetable entry) and BPV-C4 (Start's question on the event thread) came *from* 3.0 and are present.
- BPV-C13 (a failed trip stops autonomy) is covered on 3.0 by its own graceful stop: RC-A11 in `Layout.executePath`.
- Only BPV-C7 is missing.

**Needs execution.** Port master's two claims to the branch and run them red:
- `testControlStationFaults.testAnUnreadableDatabaseIsNotSavedOverWhileItsCopyCannotBeKept`
- `testMainWindowFaults.testAnUnreadableUiStateIsNotSavedOverWhileItsCopyCannotBeKept`

The fixture is a run-copy data folder whose `LocDB.data` is garbage bytes, with the backup path made uncreatable (for example a plain *file* where the `tc_backup` folder would go), then a save.

- **Proves it:** `LocDB.data` no longer holds the garbage bytes after the save.
- **Refutes it:** the bytes are unchanged, and the log says the database could not be saved.

### RLD-C1 - The Readme's v2.8.2 entry is master's first draft, not the entry 2.8.2 shipped with

| | |
|---|---|
| **Disposition** | Open - the entry is replaced by 2.8.2's final one when master's release validation ends, each line checked against 3.0. |

`001ba7b4` ("carry over the v2.8.2 changelog entry from master, word for word") copied the entry at 11:24. Master then rewrote it twice: `5ecbf918` at 12:35 and `561c8ac3` at 13:25.

`diff` of the two v2.8.2 sections (`Readme.md:461-476` against `git show master:Readme.md`) shows five differences:

- **The rename line** lacks *"or one of the locomotives in a multi-unit"*. That is BPV-A1, which this branch also fixed (`6033fb45`).
- **The Return Home line** is the old wording. It lacks *"Now a train with no speed is skipped and the others still go home, and a train that is not at a station is named before anything moves"*.
- **The failed-trip line is missing:** *"if a train's trip failed part way ... Now autonomy stops itself..."*.
- **The tc_backup line** lacks *"and the file is not saved over until that copy has been made"*. See RLD-B1: this clause is true of 2.8.2 and not of 3.0.0.
- **The gateway-ping line is missing:** *"finding the Central Station automatically could say it was not possible after a single lost reply"*.

Cosmetic in itself. It still matters for two reasons:
- Someone comparing the two Readmes to see what 3.0.0 carries forward is reading a list that is not 2.8.2's.
- The one clause that differs in substance is the one 3.0.0 lacks.

When it is re-copied, re-check each line against 3.0 behaviour. The Return Home line in particular describes master's fix; 3.0's own entries at `Readme.md:437-441` should say the same thing.

### RLD-C2 - The 3.0.0 changelog still says a route fired by its sensor sets none of its switches; since MT-247 it skips only the one under the train

| | |
|---|---|
| **Disposition** | Fixed - the line says a sensor route skips only the switch under a train, and that a route with an emergency stop is never refused (0167541e). |

`Readme.md:400`: *"a route fired by a sensor sets none of its switches and signals instead, because there is nobody there to ask - the rest of it, such as speeds and functions, still runs."*

RGN-B2 wrote that sentence on 2026-09-03 (`5d873aca`), and it was true then. Three days later MT-247 (`c22c9d90`, Adam 2026-09-06: *"don't run the conflicting switch commands, but do run the power off and others"*) changed the rule to one accessory at a time. `behaviour.md` §7a has said so since: *"Only the switch under the train is refused. Everything else in the route runs"*.

This session's `ui.testARouteOverATrainAtItsDoors.testFiredByItsSensorItSkipsOnlyTheSwitchUnderTheTrain` asserts exactly that: switch B is thrown, switch A is not. The release notes contradict both the rule and the test.

- **Consequence:** none on the railway. The behaviour is the safer and more useful one. A reader of the notes would be surprised to see a sensor route's other switches thrown.
- **Also worth a clause in the same bullet:** a route that carries an emergency stop is never asked about at either door (Adam, 2026-09-01). That is what made MT-507 and MT-508 impossible to run as written.

### RLD-C3 - behaviour.md says an imported old autonomy.json is "made the one in use"; the door keeps the running configuration in use, and its test asserts that

| | |
|---|---|
| **Disposition** | Fixed as RLA-C1; and the window it names is shut: the running configuration is chosen again before the save, so a declined reload leaves it the one the next start resumes.  Claims 01cf0d72 (red first, each for its own reason), fix 2fa033f3; mutation R8 red. |

`behaviour.md:2423`: *"An old autonomy.json goes into the configuration named at the Import prompt ... created where there is none of that name, and made the one in use; the configuration in use before is left as it was."* The comments at `AutonomyViewerPanel.java:1116` and `:1164` say the same.

What the door actually does, from `importLegacyGraph`:
1. `activateTheConfigurationNamed` points the store at the named configuration, only long enough for `importLegacy` to write into it.
2. `save()` runs.
3. `loadAfterImport` asks `AutonomySession.configurationToLoadAfterImport(running, imported)`. On a layout where a configuration is running, the running one wins (*"Importing a configuration is not a request to switch to it"*). The store is pointed back at it and it is reloaded.

So with Load Autonomy on, which is the ordinary case, the imported configuration is **not** the one in use afterwards. `regression.testTheImportDoorReadsAnOldFile` asserts exactly that (*"the import switched away from ..."*). The MT-491/501/502/582 comments tell Adam *"your configuration in use is left as it was"*, and the commit message for Adam's "(a)" (`fd3237ea`) says the same. So it is `behaviour.md` that is out of step. Whichever reading Adam meant, the document that states intent should say it. If he meant "switch to it", the door is wrong instead.

What a user meets, either way:
- Autonomy > Configurations > Import of an old file says *"placed 4 locomotives"*, and nothing on the diagram changes until the new configuration is chosen and loaded.
- `loadAfterImport`'s own javadoc names that as the thing that makes an import "look to have failed".

**One more window, which needs execution.** The `save()` in step 2 writes `activeConfiguration` = the *imported* name into `setup.json` (`AutonomyCompanionStore.java:997`). Only a *successful* reload of the running configuration saves it back. The Import item is not greyed while autonomy runs, so there are two ways the reload can end early:
- `prepareAutonomyReload` asks *"reloading stops running locomotives"* and the operator answers No (the default).
- The rebuild is refused after the import has merged its track half.

Either way, `setup.json` is left naming the imported configuration. If nothing saves the store before exit, the next start loads the imported configuration in place of his own.

**Verification request.**
- **Fixture:** `testTheImportDoorReadsAnOldFile`'s. Before pressing Import, dispatch one train by hand so that `isAutonomyBusy()` is true, and have the answerer press No on the reload question.
- **Then:** read `config/autonomy/setup.json` in the sandbox.
- **Proves it:** `activeConfiguration` is "MT-491 import".
- **Refutes it:** it still names the configuration that was running.

### RLD-C4 - Importing an old autonomy.json under a name already taken asks "Replace it with the imported one?", then merges instead

| | |
|---|---|
| **Disposition** | Fixed as RLA-B2. |

`AutonomyViewerPanel.java:1016` asks `autosetup.ui.confirmImportOverwrites` (*"A configuration named {0} already exists. Replace it with the imported one?"*) before it knows what kind of file it is.

For a bundle, Yes replaces. For an old autonomy.json, this session's change routes Yes into `activateTheConfigurationNamed` → `importLegacy`, which *gap-fills* the existing configuration: `if (!point.has(key) || extras.has(key)) continue;`, "so re-running cannot undo an edit". That is the behaviour MT-298 wants.

`testTheImportDoorReadsAnOldFile.testASecondImportFromTheMenuKeepsAHandMadeChange` answers the question Yes and asserts that nothing was replaced. The test encodes the contradiction.

Before this session the question was asked too, and the legacy path then wrote into whichever configuration was running. It is only now that Yes reaches the configuration named, so the wording matters now.

- **What a user meets:** someone who wants a clean re-import is promised a replacement and gets their old values kept.
- **Fix, either way:**
  - Word the question for the legacy case ("... Fill in what it does not already have?").
  - Or, if Adam wants Yes to replace, clear the configuration first. That would also change MT-298's rule.

### RLD-C5 - The MT-582 claim can pass with its defect back: it never ticks Load Autonomy first, and it does not read the log

| | |
|---|---|
| **Disposition** | Fixed - the claim ticks Load Autonomy first and asserts the log says nothing about it (26b18432); mutation R11 red (the untick put back). |

MT-582's step 1 is *"Make sure Preferences > Startup > Load Autonomy is ticked"*, and its Expected is *"Load Autonomy is still ticked, and the log says nothing about it"*.

`testTheImportDoorReadsAnOldFile.java:90`/`:166` does less than that:
- It reads the preference as it finds it (`get(AUTO_LOAD_AUTONOMY, "unset")`) and asserts that it reads the same afterwards.
- `AUTO_LOAD_AUTONOMY` includes the folder hash, so it is **Adam's own preference** for this project folder.
- The defect it guards against was `prefs.putBoolean(AUTO_LOAD_AUTONOMY, false)` (`ee407002`, removed in `f17f5a5c`).

So on a machine where Load Autonomy is unticked, the old untick leaves "false" as "false" and the claim is green. Its javadoc says *"MUTATION: ... untick Load Autonomy ... and this fails"*, and that holds only on some machines. The log half of the Expected is not asserted either.

- **Mitigation:** `regression.testALegacyImportLeavesLoadAutonomyAlone` reads every legacy import door and the removed method, so a return of the untick through the source is still caught. This is a C for that reason.
- **Fix:** tick it (`putBoolean(..., true)`) before the import, keeping the put-back in `finally`, and assert the log has no line about Load Autonomy.

**Needs execution:** put `ee407002`'s untick back with the preference set to false first.
- **Proves it:** green.
- **Refutes it:** red.

### RLD-C6 - Four supersessions fall short of the rule this session wrote for them

| | |
|---|---|
| **Disposition** | Fixed in the tracker - MT-505, MT-571, MT-507 and MT-508 are back on his list as fixed unvalidated, each with a comment saying why; MT-507/MT-508 ask him which he wants for a route with an emergency stop.  The final battery's result is recorded in the report. |

`docs/manual-tests/README.md` now says an automated test may supersede an MT only when the test:
- drives the same door ("the menu action or the method it runs, not a helper beneath it"),
- uses the same inputs,
- asserts every outcome the Expected names as the Comments amend it,
- and is green in a full battery.

*"Where any of that is in doubt ... the entry stays Adam's."*

Most of the 47 meet that bar: see RLD-D4 to RLD-D6. These do not, or not wholly:

- **MT-505 (a guard signal no way in passes is noticed).**
  - `core.testAutonomyDiagramSession.testAGuardOffTheWayInIsNoticed` runs on a synthetic two-line page, not his railway.
  - It asserts that the finding's *key* exists and is graded a notice. It does not assert the sentence the Expected quotes (*"the signal guards the station but no way into it passes it, and to check it is the signal you meant"*), and it never reads the editor's *Configuration errors and warnings* list, which is where step 2 looks.
- **MT-571 (a train is not sent round a loop into its own tail).**
  - The send doors are not driven. `testATrainDoesNotRunIntoItsOwnTail.java:182` `offersLowerFront()` rebuilds their filter from model calls: `getPossiblePaths(train, true)` then `isOfferableToOperator`.
  - Why not Moving? is read through `explainDestinations`, the locomotive list's call. The editor's Why not Moving? uses `explainCannotStart` and `explainDestinationsGrouped` (`AutonomyEditorPanel.java:7969-7980`).
  - Compare MT-495, MT-517 and MT-584, which build the real right-click menu (`gatherPathOptions` plus the menu's own constructor) and click the real Why button.
- **MT-507 and MT-508 (Cancel and OK on a route over a train).**
  - Both comments say *"The steps could not pass as written"*: a route with an emergency stop is never asked about.
  - The tests then assert the opposite of the Expected for that route (no question, switch A left alone), plus the Expected for a route *without* the stop.
  - That is a reasonable reading of the ruling of 2026-09-01. It is still a decision made in the superseding comment itself, which is the "in doubt" case the rule reserves for Adam.
  - MT-247's history adds to the doubt: Adam ran exactly this route shape on 2026-09-06 and answered *"OK should fire everything"*.
  - The honest state is "superseded, pending Adam's confirmation of the stop carve-out". Better still, a new MT without the stop: the entries are append-only.
- **All of them:** the rule's last condition, "green in a full battery", is not recorded in any of the superseding comments.

**Verification request.** Give the battery run that followed each superseding commit. Each class it names should be reported **green**, not **skipped**. Note that `testMassAssignLengths.testTheMaximumWalkOnHisRailway` skips itself where the desktop does not give the prompt the keyboard (`testMassAssignLengths.java:1675`), and that the battery counts a skip as "tested nothing". A skip there means MT-520 was not answered.

### RLD-C7 - Five superseding comments name the class but not the method

| | |
|---|---|
| **Disposition** | Fixed in the tracker - MT-491, MT-501, MT-502 and MT-582 have a comment naming the method; MT-571's return names it. |

The README rule says: *"The Comments name the test, class and method, and what it asserts"*. These comments name only the class:
- **MT-491, MT-501, MT-502 and MT-582** name `regression.testTheImportDoorReadsAnOldFile`. The method is `testAnOldFileFromTheMenuGoesIntoANewConfiguration`.
- **MT-571** names `core.testATrainDoesNotRunIntoItsOwnTail`, a class of several methods. Its new assertions are in the method that holds the 20/9/10 figures.

Bookkeeping: Adam reopening one of these cannot go straight to what answers it. Fix with a dated Comment through `triagedb`, since the entries are append-only.

### RLD-C8 - The OB-299 sentences use a different word for autonomy than the Why sentences beside them, in four languages

| | |
|---|---|
| **Disposition** | Fixed - Danish, Spanish, Italian and Dutch use their neighbours' word (3af66907). |

`autolayout.why.startReachesNoStation` and `...EitherWay` sit in the same Why not Moving? list as `startFacingBarred` and `startNotStation`. Four languages name autonomy differently in the new lines:

| Language | New lines | Neighbour lines |
|---|---|---|
| Danish | *automatikken* | *autonomien* |
| Spanish | *la autonomía* | *la automatización* |
| Italian | *l'autonomia* | *l'automazione* |
| Dutch | *de automaat* | *de automatisering* |

German (*Automatik*), French (*automatisme*) and Polish (*automatyka*) agree with their neighbours.

Spanish also writes *"Déle la vuelta"* (`messages_es.properties:1791`). Its neighbours write *"dele la vuelta"*, which is the current spelling.

Cosmetic. A German-reading operator will not see it, and MT-291 (reading in a language) is where it would surface. Every placeholder and quote is correct in all eight languages.

### RLD-C9 - The user guide's new "why isn't it moving" entry gives "turn the train round" even where turning cannot help

| | |
|---|---|
| **Disposition** | Fixed - the user guide gives both remedies (3af66907). |

`Automation.md:311`: *"Why not Moving? says so: turn the train round, or drive it off by hand."*

`Layout.whyItReachesNoStation` has two answers:
- **`startReachesNoStation`**, where another copy of the square reaches a station: turn it round.
- **`startReachesNoStationEitherWay`**, where none does: *"Drive it off by hand, or tick 'Can Be Chosen in Full Autonomy' on a station that can be reached from there"*.

The guide gives only the first remedy. `behaviour.md` gives both. One clause fixes it.

### RLD-C10 - "Balanced priority counts an answered 0 as 0" is not quite what it does, and ratioOf's javadoc now overstates

| | |
|---|---|
| **Disposition** | Fixed as RLA-C7. |

`behaviour.md:1034` says Over the shortest track, Over the longest track *and balanced priority* count a section answered 0 as 0.

`Layout.ratioOf` divides by `Math.max(1, lengthOf(path))`. So a route whose every section is answered 0 counts 1 under balanced priority, and 0 under Over the shortest track. Its javadoc (`Layout.java:287`) still says *"Distance is measured exactly as SHORTEST_LENGTH measures it, floor included, so the two rules cannot disagree about which of two routes is longer"*. For a 0-length route against a 1-length one, they now do disagree: shortest prefers the 0, balanced ties them.

The clamp itself is right, because it prevents a division by zero. Only the two sentences are wrong. It is reachable only between stations joined by sensors side by side with everything between them answered 0. `testRoutePicking.testAnAnsweredZeroCountsNothingInTheLength` does not exercise balanced priority, although the commit message says *"Balanced priority divides by the same length, so it follows"*.

### RLD-C11 - Routes > Import's question now lists every saved-armed route on one unwrapped line

| | |
|---|---|
| **Disposition** | Fixed as RLA-C8. |

`TrainControlUI.java:25080` now fills `route.ui.confirmRearmImported` with `String.join(", ", savedArmed)`. That is correct, and the MT-496 automation found it. But `JOptionPane` does not wrap a `String` message. A routes file with many routes saved armed gives a question dialog as wide as the list.

The test data arms one route, so the test cannot see width. How many routes Adam keeps armed was not checked, since it is in his live data. The same sentence is used in all eight languages, so a wrap or a count-plus-first-few would cover them all.

**Needs execution.**
- **Fixture:** `testTheRoutesImportDoorAsksByName`'s, with 30 routes armed before the export.
- **Proves it:** the question dialog's width is greater than the screen's.
- **Refutes it:** the dialog fits.

---

### RLD-D1 - build.xml, the window census and the live-snapshot README list what they should

| | |
|---|---|
| **Disposition** | Checked - clean. |

**build.xml.** All six classes added in the range (`git diff --diff-filter=A`) are registered as `<test-one-class>`: the three `ui.` classes beside `testACutTrainArrivesTheWayItWouldDrive`, and the three `regression.` classes beside `testAHandSendIsRefusedWhileTheSetupIsBroken`.

**The window census.** `testNoTestOpensTheOperatorsRailway` counts classes that write `new TrainControlUI(`. It went from 61 to 64, for exactly the three regression classes that do, and each has a dated comment line. The three `ui.` classes build their window through `init(null, true, true, ...)`, which that pattern does not match. They are held instead by the model half of the same check (`= init(null` after a sandbox), and all three open `LayoutSandbox.open(Scenario.folderFor("live-snapshot"))` before `init`.

**The live-snapshot README.** "Used by" gained the five classes that call `folderFor("live-snapshot")`. `testTheRoutesImportDoorAsksByName` uses the default `test_layout` sandbox and is correctly absent.

### RLD-D2 - The tests.md ledger agrees with its entries

| | |
|---|---|
| **Disposition** | Checked - clean. |

At `146b25eb` there are 586 `### MT-` headings:
- 453 **fixed validated** (421, plus 32 with a trailing space),
- 125 **superseded**,
- 6 **fixed unvalidated**,
- 2 **needs test**.

The open table lists exactly those 8, and the footer reads *"578 of 586 ... 453 fixed validated and 125 superseded"*. At `1c6978c6` it was 575/122 with 11 open, which was also consistent.

### RLD-D3 - MT-468's mechanical half holds: every key a screen asks for is in the bundles

| | |
|---|---|
| **Disposition** | Checked - clean. |

Checked independently of the new test, from outside the test's own regexes. Every key-shaped string literal in `src/` (17 prefixes) was compared against `messages.properties`. The only absentees are:
- the five prefixes built with a suffix, which the test excludes;
- the two example keys in `I18n`'s javadoc.

The suffix families (`autolayout.ui.pathPreference*`, `...tooltip.pathPreference*`, `autosetup.ui.side*`, `autosetup.ui.facing*`, `route.kind.*`) have the same count in all eight bundles: 10, 10, 4, 7 and 13. Keys held in constants, such as `IMPORTED_ROUTES_NOTICE`, are literals in `src/`, so this pass caught them.

### RLD-D4 - The import-door and Routes-import tests do not have their answers supplied by the fixture

| | |
|---|---|
| **Disposition** | Checked - clean. |

**Old autonomy.json import (MT-298, MT-491, MT-501, MT-502).** The imported configuration starts empty: `createConfiguration(name, null)` is `new JSONObject()`, and `maxTrainLength`, `active` and `AUTO_DESTINATION` are per-configuration. So the facts these tests assert come from his 2.7.4c file, not from the snapshot:
- ParkingTrack7's *"not chosen, not switched off"* (MT-502);
- the station maximum the second import must keep (MT-298).

The log assertions (MT-491, MT-501) read the same logger that carries the 176-pieces line, which the test asserts is present. So the "no such line" checks are not vacuous.

**Routes import (MT-496, MT-497).** The Yes test alone would pass with the old count, because one route is armed and saved armed. `testASavedArmedRouteWithNoSensorIsNotCountedAsArmed` is the claim that separates the two, and it does.

### RLD-D5 - The broken-setup, place-door, where-sent and MT-586 window tests answer their MTs at the MTs' own doors

| | |
|---|---|
| **Disposition** | Checked - clean. |

**Broken setup: MT-263, MT-573, MT-580.**
- The test checks its preconditions: 4 - Combined is off on the copy, switching it on is an error, and Return Home is offered before the break.
- Then it reads Start and Return Home off the diagram's own right-click menu, presses the three buttons, and asserts each sentence, that each button stays live, and that no train moved.

**Place doors: MT-498, MT-581, MT-585, MT-575.**
- Control+X/V go through the diagram's key door, and Place through the right-click and the editor.
- Where the train faces is read from the *75 407 DB Is Facing...* menu.
- MT-575 asserts that the list's owner is the editor window.

**Where trains may be sent: MT-495, MT-517, MT-584, MT-570, MT-586.**
- The real right-click menu is built through `gatherPathOptions` and its own constructor.
- Why not Moving? is pressed and clicked in the editor.
- MT-495 has a one-unit control, and MT-586 drives the send in simulation.

**One-page layout: MT-512 to MT-514.** The test runs on a sandbox copy of the shipped one-page layout, so the shipped folder is never written. It clears the once-only memory before the editor openings.

**Length tools: MT-518 to MT-521, MT-530, MT-568.** Driven from the Bulk Tools items with their confirmations answered.

The MT-507 "without the stop" half drives both real doors: the route list and a route tile.

### RLD-D6 - BPV-A1: the documents say what the code does

| | |
|---|---|
| **Disposition** | Checked - clean. |

**The three callers.** The three edit doors that call `sanitizeMultiUnits` each pass the edited locomotive:
- `TrainControlUI.java:20493`, in `changeLocAddress` (rename and re-address);
- `:20900`, in `changeLinkedLocomotives` (re-link);
- `:26387`, in `checkForRenameMenuItemActionPerformed` (Check for Renames).

Placing (`moveLocomotive`) and loading (`fromJSON`) call the full `clearMultiUnitConflictsWith`.

**behaviour.md's claim that the doors refuse "while autonomy runs - its coast-down and Return Home's planning included"** is true:
- All three doors refuse on `isAutonomyRunning()`.
- That is `isRunning() || isStagingInProgress()`.
- `isRunning()` includes any active locomotive.

The loader half has its claim (`testALoadedLayoutDoesNotStandAHeadAndItsMemberBoth`). The rename claim calls `sanitizeMultiUnits` with the renamed locomotive, which is what all three doors do.

### RLD-D7 - MT-575's fix cannot strand a question asked from the main window

| | |
|---|---|
| **Disposition** | Checked - clean. |

The worry was that with an editor open, a tail question raised from the main window's diagram would now open from the editor, somewhere other than where the operator is working.

It cannot happen. The main window's diagram keys refuse while an editor is open (`TrainControlUI.java:6982`, OB-076), and so do its right-click menu (`:4620`) and the destination list (`LayoutRightclickAutonomyMenu.java:222`, `:371`). Every door that can reach `TailCrossedPrompt` with an editor open is therefore the editor's own. `behaviour.md`'s new clause (*"the list opens from the editor, in front of it"*) matches.

### RLD-D8 - The answered-0 route length is guarded against division by zero, and the 3.0.0 line about it is still true

| | |
|---|---|
| **Disposition** | Checked - clean. |

`lengthOf` can now return 0. Its only divisor, `ratioOf`, clamps with `Math.max(1, ...)`. The clamp's documentation is RLD-C10.

`Readme.md:378` (*"Track with no recorded length now counts as one sensor's worth"*) is still accurate: an answered 0 is a recorded length. The user guide's two lines (`Automation.md:168`, `:226`) and the tooltips for Over the shortest track and Over the longest track agree with the code.
