# RLA4 - Autonomy lane, round 4: validating the dispositions of RLA3 and what round 3's fixes left

**Status:** open

**Prefix:** RLA4

**Reviewed:** branch `autonomy-diagram-r0` at `f7e94a50` (round 3's records commit). The working tree was clean (`git --no-optional-locks status --porcelain` printed nothing). Reviewed read-only on 2026-09-25.

## Method

I read the brief, `docs/reviews/README.md` for the shape, my lane's round-3 document `docs/reviews-2026-09-25/RLA3.md` whole, and `RLU3.md` and `RLD3.md` whole. From the earlier rounds I read `RLA.md`'s RLA-C1 and RLA-C2, `RLA2.md`'s RLA2-B1 to RLA2-B3 and RLA2-C4, `RLD.md`'s RLD-C3 and RLD-C4, and `RLD2.md`'s RLD2-C2 and RLD2-C3, because round 3 reopened the door they are about. I read the three round-3 commits in full with `git show`: the claims `cca48e96`, the fix `81a9fffa` and the records `f7e94a50`. For comparison I read `cc56a7a8`'s change to `AutonomyViewerPanel`, since round 3 restored most of what it removed.

Around each change I read the code at HEAD:

- **`AutonomyViewerPanel`:** `importConfiguration` whole, `activateTheConfigurationNamed`, `importLegacyGraph` whole, both `loadAfterImport`s, and `load`.
- **`AutonomySession`:** `LegacyImport`'s fields, `CARRIED_SETTINGS`, `findTheImportedFacings`, `importLegacy` (the seed, the placement branch, homes and carried settings), `captureFromLayout` whole, `POINT_OPERATIONAL_KEYS`, `setHome`/`writeHome` and `writePointProperty`.
- **`TrainControlUI`:** `isAutonomyBusy`, `prepareAutonomyReload`, the exit save's capture (:2525-2580), `captureRunningLayout` (:2990-3035) and `rebuildRunningLayoutFromSetup` (:6565-6720) - the three places that already guard a setup edit made during a run.
- **`Layout`:** `isRunning`, `explainCannotStart`, `whyItReachesNoStation`, `whyTheStartIsRefused`, `whyNoTrainIsStartedFrom`, `isABarredCopyOfAStation`, `turnsEveryTrainAt`, and the two `clearMultiUnitConflictsWith` calls a load and a Place make (:12710, :9617).
- **Elsewhere:** `AutonomyMenu`'s Configuration submenu and `guardWhileEditing`; `AutonomyEditorPanel.promptLocomotives` (the exclusion list's write); `Point.toJSON`; `MarklinControlStation.isAutonomyRunning` and the sync comment round 3 changed; `CS2File`'s `traktion` parse. For the answered-0 comments: `AutonomyCompanionStore.answerTileLengthZero` and `isTileLengthAnswered`, `GraphReducer.answeredAtZero`, `Edge.isMeasured`'s javadoc, and every hit of `git grep "every length rule"`.

Tests read:

- The five round-3 methods of `testTheImportDoorReadsAnOldFile` and `testWhyStuck` that `cca48e96` added or changed, with their fixtures and helpers (`standingIn`, `importFromTheMenu`, the `Answerer`, `placedIn`, `before`).
- `testADeclinedReloadLeavesHisConfigurationChosen` (how a test makes `isAutonomyBusy()` true) and `testASecondImportKeepsAHomeTheConfigurationHas` (how a test gives the file a home).
- The census allowances for `AutonomyViewerPanel`; `testMockCentralStation.fixtureFor`; the multi-unit in `test/lokomotive.cs2`.

Records read: behaviour.md :705, :1020-1026, :1957-1960, :2025-2050 and :2428-2450; open-questions.md :135 and :236-245; `Automation.md` :311; issues.md OB-303 and OB-304; tests.md MT-298; Readme.md's 3.0.0 lines on configurations and Why not Moving?.

Mechanical checks, all in memory with inline Python fed through a heredoc, nothing written to disk:

- The eight bundles at HEAD, read out of `git show`: key sets, duplicates, `{n}` placeholder sets against English, bytes above 127, empty values, and straight apostrophes in values that carry a placeholder. I decoded the three new keys and their four neighbours in every language.
- The orphaned-javadoc rule over `AutonomySession`, `AutonomyViewerPanel` and `Layout`, at `d779578b` and at HEAD.
- `docs/manual-tests/triage.db`, opened read-only (`mode=ro`), for the round-3 rows and the rows of RLA-C2, RLU-C4, RLA2-C4 and RLU2-C11.

Every finding below was searched for in `docs/manual-tests/findings.tsv` first (an import while autonomy runs, the capture before an import, the either-way remedy in the user guide, the served multi-unit, the question's list of settings, the answered-0 javadoc). None is there. The prefix RLA4 is free. As the brief says, I do not report RLA3-B1 with RLU3-C4, OB-300 to OB-305, RLU2-C9 or RLD-C1. RLA4-C7 and RLA4-C8 are about how two of those were recorded - the premise of a follow-up and the list in a question - not about the defects.

Nothing was compiled or run. No JVM was started, no git state changed, and nothing under `cs2_sample_layout/` was read or written. This file is the only one written.

**Everything below is from reading.** Each finding that needs a run says so, and says what proves or refutes it.

Counts: no A, no B, 9 C, 7 D. **Nothing new above C** - by the brief's measure, the signal to stop. Two of the Cs are round 3's own door: it restored RLA-C2's capture for an old file into the configuration in use, but not for an import made while autonomy runs (RLA4-C1), and its claim cannot tell that capture from the reload's (RLA4-C2). The other seven are a remedy the user guide still gives, an unpinned guard, and five records.

---

### RLA4-C1 - Into the configuration in use while autonomy runs, an old file's homes and settings are written and then folded away - by the reload's capture if the operator lets it stop the trains, by the next fold if he does not; behaviour.md says the reload never captures

| | |
|---|---|
| **Disposition** | Fixed - an old file into the configuration in use is refused while autonomy runs, as Delete is.  claims 6f02ff2e (red first), fix d5b97ee8; mutation V2 red. |
| **Grade** | C |
| **Names** | RLD3-C1 (*"the running layout is captured into it first, the reload does not capture"*); RLA-C2, whose defect this is again for one case; ACC-B3, WKW-B2 and AMS-B1, the guard the setup's other doors use for an edit made during a run |
| **Where** | `AutonomyViewerPanel.importLegacyGraph` :1254-1280 (the capture only `if (intoTheOneRunning && !ui.isAutonomyBusy() && ...)`), :1406 (`loadAfterImport(into, !captured)`); `load` :763 (`prepareAutonomyReload`) and :773-786 (the capture); `AutonomySession.captureFromLayout` :5495-5499 (every `POINT_OPERATIONAL_KEYS` key replaced, or removed where the running layout has none); `TrainControlUI` :4170-4204, :2534-2556 (the exit save's fold), :3015 and :6603-6607 (`setupEditDeclinedDuringRun`); behaviour.md :2433-2438; `AutonomyMenu.java:332-333` |
| **Needs execution** | yes - see the request |

Round 3 takes an old file into the configuration in use again. It captures the running layout into it first, imports, and reloads without a capture, so the reload does not write the railway back over what the import brought. That is RLA-C2's round-1 fix, restored. But the capture has a condition that behaviour.md, the commit and the menu comment leave out: `!ui.isAutonomyBusy()`. That is false whenever autonomy is started, a train is moving, or Return Home is staging (`Layout.isRunning` reads the running flag as well as the moving trains). In that state `captured` stays false and the reload takes RLA-C2's old road.

- **Yes to "reloading stops running locomotives".** `load` stops the trains and then captures the running layout into the configuration in use. For every point, the capture replaces `home`, `active`, `maxTrainLength`, `speedMultiplier`, `priority` and `excludedLocs` with the running layout's value, or removes the key where the layout has none. The running layout was built before the import. So every home the file gave and every setting it carried into a gap goes again: a home is on a point only where the layout has one, and `active`, `priority` and `speedMultiplier` only where they are off their defaults. What survives is what the capture has no key for: Can Be Chosen (the file's switched-off stations) and the shared half. The import's message has already been shown, counting the settings it carried.
- **No.** `load` returns at `prepareAutonomyReload`. The import is saved, and the running layout does not have it. This is exactly the state the setup's other doors mark with `setupEditDeclinedDuringRun` - *"A setup edit made as a run starts is written to the file and not to the running layout"* - so that the exit save and the editor doors do not fold the older layout back over it (ACC-B3, WKW-B2). This door does not set the flag. After the run, the exit save (:2540) or opening an editor (:3015) folds the layout back over the import and removes the same homes and settings.

The door's own comment gives the reason for the condition: *"Not while trains are moving: the reload stops them and captures where they stopped, as it always has."* That is right for the placements, which are the railway's to say. It is not right for homes and settings, which is RLA-C2's distinction. behaviour.md :2436-2437 states the rule without the exception: *"what the running layout knows goes into the configuration first, the reload after the import does not capture again"*. So does the RLD3-C1 disposition.

**On the railway.** Nothing moves that was not told to. No train is placed, whatever the state (`placeTrains` is false whenever the name is the running one), and what is undone leaves the configuration as it was before the import. What the operator meets is RLA-C2's: a Yes, a message counting what was carried, and after the reload or the next exit the homes and settings are not there. Import is not greyed while autonomy runs (only while the layout editor is open), and "busy" includes autonomy started with every train standing. Graded C, as RLA-C2 was.

**Direction:** either of these.

- Refuse an old file into the configuration in use while `isAutonomyBusy()`, as the setup's edit doors refuse (`autolayout.errorCannotEditWhileRunning`).
- Or, on that branch, reload with the placements carried the way `rebuildRunningLayoutFromSetup` carries them (`whereTheTrainsAre` / `putTheTrainsBack`) instead of a full capture, and set `setupEditDeclinedDuringRun` when the stop is declined.

Either way, behaviour.md :2436 and the menu comment should say what happens while autonomy runs.

**Verification request.** Use `testADeclinedReloadLeavesHisConfigurationChosen`'s fixture: the live-snapshot sandbox with `stagingFlowActive` set true by reflection, so `isAutonomyBusy()` is true. Use the file `testASecondImportKeepsAHomeTheConfigurationHas` builds: his MT-298 file, with a home at BottomMainB for the train it places at Tunnel. As a precondition, assert that the configuration in use gives that train no home. Import the file from the menu under the name of the configuration in use, and answer Yes to the fill-gaps question.

- **(a)** Answer Yes to `autolayout.ui.confirmReloadJsonStopsRunningLocomotives`.
  - **Proves:** after the reload, the configuration in use has no home for that train.
  - **Refutes:** it has one at BottomMainB.
- **(b)** Answer No, then read the configuration and `setupEditDeclinedDuringRun` by reflection.
  - **Proves:** the home is in the configuration and the flag is false, so the exit save will fold it away once the run ends.
  - **Refutes:** the flag is true.
- **Control:** the same with `stagingFlowActive` false. The home is there after the reload (the capture-first path).

---

### RLA4-C2 - RLD3-C1's claim cannot tell the capture before the import from the reload's capture after it: the moved train is kept either way, and nothing reads what the import brought

| | |
|---|---|
| **Disposition** | Fixed - the in-use claim takes a priority the file carries out of the configuration, and asserts the import brings it back past the reload.  claims 6f02ff2e (red first), fix d5b97ee8; mutation V3 red. |
| **Grade** | C |
| **Names** | RLD3-C1 (*"claims cca48e96 (red first) ... mutations U2 red, U3 red, U4 red"*); RLD2-C3, whose strengthening this is; RLA-C2, the property the capture exists for |
| **Where** | `testTheImportDoorReadsAnOldFile.testAnOldFileIntoTheConfigurationInUseLeavesItsTrainsToTheRailway` (:702-800, MUTATION line :696-697); `AutonomyViewerPanel.java:1264-1278`, :1406 |
| **Needs execution** | yes - see the request |

The claim moves a standing train on the running railway only. It imports MT-491 under the running configuration's name and asserts four things:

- nothing placed;
- the same trains standing;
- the moved train at its new square in the configuration;
- the not-placed line.

Its MUTATION line says that leaving out *"the capture that keeps a moved train where it stands"* fails it. That holds for RLD2-C3's mutation: delete the `captureFromLayout` line and leave `captured = true`, so neither capture runs. It does not hold for the regression RLA-C2 is about. Delete the capture block, so that `captured` stays false: the reload's own capture then records the moved train at its new square, and every assertion passes. That is round 2's door, and round 1's before `2fa033f3`.

So what the capture before the import protects - the homes and settings the import writes surviving the reload - has no claim at the door that has it again. The MT-298 claim beside it cannot see it either: a hand-set maximum is in the running layout, so either capture writes it back.

**Verification request.**

- **Mutation.** Delete the `if` block at :1264-1278 (the capture and `captured = true`), leaving `boolean captured = false;` and `loadAfterImport(into, !captured)`, and run the claim.
  - **Proves:** green.
  - **Refutes:** red.
- **The claim strengthened.** Import the file with a home that RLA4-C1's request uses, having asserted first that the configuration in use gives that train no home. Assert that the home is in the configuration after the reload. It should be red under the mutation and green without it.

---

### RLA4-C3 - RLU3-C1 is fixed in the sentence and not in the user guide: Automation.md and behaviour.md still send the operator to open the side under Trains May Arrive..., and neither describes the two new other-way sentences

| | |
|---|---|
| **Disposition** | Fixed in the records - Automation.md and behaviour.md give the four cases the sentences now give (d5b97ee8). |
| **Grade** | C |
| **Names** | RLU3-C1 (*"Fixed - the either-way sentence no longer names a side to open"*; its Where names `Automation.md:311` and `behaviour.md:705`); RLU3-C2; RLD3-D6 (*"Automation.md:311 and behaviour.md:705 give the two remedies"*), true at round 2 and not since |
| **Where** | `Automation.md:311`; `docs/reference/behaviour.md:705`; `Layout.whyItReachesNoStation` :5070-5083 |
| **Needs execution** | no |

`81a9fffa` took *"open the side a train would arrive on under Trains May Arrive..."* out of the either-way sentence in all eight languages, because at a station every train turns round at, opening that side is the terminus error and the setup refuses to load (GUI4-C3). It changed neither document.

- **The user guide**, which his users read: *"otherwise drive it off by hand, or let autonomy choose a station it can reach - switch that station on, tick Can Be Chosen in Full Autonomy, and open the side a train would arrive on under Trains May Arrive..."*.
- **behaviour.md :705** says the same, citing RLU-C4 and RLU2-C10.

Both still describe the fall-through as the only other case. Since RLU3-C2, a train whose other way reaches a station hears one of two new sentences instead:

- "open that side ... and turn it round", where the other copy is barred and not every train turns there;
- "drive it off by hand", where autonomy starts no train facing the other way.

Neither document describes either.

**On the railway.** Nothing moves. Followed at a terminus reached from two sides, the guide's remedy makes autonomy refuse the whole setup until the side is closed again, which is RLU3-C1's consequence reached through the guide instead of the dialog.

**Direction:** say what the four sentences say:

- turn it round where the other way may be started from;
- where it is barred and not every train turns there, open that side and turn it round;
- where autonomy starts no train facing the other way, drive it off by hand;
- where neither way reaches a station, drive it off, or switch a reachable station on and tick Can Be Chosen.

---

### RLA4-C4 - The other-way-barred sentence's GUI4-C3 guard has no pin: drop `!turnsEveryTrainAt(refused)` and every test stays green, and the sentence tells him to open a terminus's closed side

| | |
|---|---|
| **Disposition** | Fixed - WS13 builds a square whose every copy turns a train, one of them barred.  claims 6f02ff2e (red first), fix d5b97ee8; mutation V9 red. |
| **Grade** | C |
| **Names** | RLU3-C2 (*"barred with 'open that side and turn it round' where not every train turns there (GUI4-C3) ... mutation U5 red"*); RLU3-C1, the harm the guard exists to prevent |
| **Where** | `Layout.java:5074` (`isABarredCopyOfAStation(refused) && !turnsEveryTrainAt(refused)`); `test/core/testWhyStuck.java` :742-745 and :780-785, and the builder `platformWithASiding` :791-829; `test/core/testATrainIsPutOnlyWhereItCanStart.java:829` (GUI4-C3 at the train's own copy, a different branch) |
| **Needs execution** | yes - see the request |

The new branch picks "open that side under Trains May Arrive... and turn it round" only where the other copy is barred and not every train turns at the square. That second clause is RLU3-C1's lesson. Opened at a square every train turns round at, a side makes a terminus reached from two sides, and the setup refuses to load.

No test builds that case for this branch:

- the two claims use `platformWithASiding`, whose copies are neither terminus nor reversing;
- the GUI4-C3 pin in `testATrainIsPutOnlyWhereItCanStart` asserts `startFacingBarredMustTurn`, which is `whyNoTrainIsStartedFrom` on the train's own copy.

So removing the clause leaves every test green. With it removed, a train at such a square would be told to open the side, which is the remedy RLU3-C1 took out of the other sentence.

**Verification request.** Build `platformWithASiding("WS13", true, false)` and mark both platform copies terminus (`setTerminus(true)`). Assert as preconditions that `turnsEveryTrainAt` holds for the westbound copy, that it is a barred copy of a station, and that it reaches WS13 Far. Stand a train on the eastbound copy.

- **Expected with the guard:** `autolayout.why.startReachesNoStationOtherWayRefused`.
- **Mutation:** drop `&& !turnsEveryTrainAt(refused)` at :5074, and run `core.testWhyStuck`.
  - **Proves:** green.
  - **Refutes:** red.

If the builder cannot make that copy reach WS13 Far once it is a terminus, the branch is unreachable, and this becomes a D.

---

### RLA4-C5 - behaviour.md's import-facing paragraph still says a facing is guessed wherever the edges cannot say; round 3 corrected the other of the two sentences RLD3-C5 named

| | |
|---|---|
| **Disposition** | Fixed in the records - behaviour.md's import-facing paragraph says a recorded facing stays (d5b97ee8). |
| **Grade** | C |
| **Names** | RLD3-C5 (*"Fixed in the records (81a9fffa)"*; its direction: *"A clause in each behaviour.md sentence"*); RLA3-C4 |
| **Where** | `docs/reference/behaviour.md:2038-2039`; :2445-2446 (the sentence that was fixed); `AutonomySession.java:746` |
| **Needs execution** | no |

behaviour.md :2446 now says *"where the file cannot say, the last occupant's stays"*. That is the RLA3-C4 half, and it is right. But :2038-2039, the import-facing paragraph in the barred-side section, still reads: *"an import of an old autonomy.json takes each train's facing from the side its point's one-way edges leave by, guessing one trains may arrive in only where they cannot say (REG3-C1, REG4-A1, REG4-C1)"*.

Where the edges cannot say and the square records a facing, the code keeps the recorded one (`if (ran == null && getFacing(tile) != null) continue;`). No guess is made, and none is preferred for a way trains may arrive. OB-304 carries the code's half. The paragraph is the one place a reader of that section looks.

**Direction:** "... guessing one trains may arrive in where they cannot say and the square records none; a facing the square records stays (OB-304)".

---

### RLA4-C6 - RLA3-C6's store half: RLA-C2's and RLU-C4's rows still read as round 1 left them

| | |
|---|---|
| **Disposition** | Fixed in the records - RLA-C2's and RLU-C4's rows carry status notes naming what superseded them. |
| **Grade** | C |
| **Names** | RLA3-C6 (*"RLA-C2's and RLU-C4's rows say what superseded them"*; the finding named *"its triage.db status note"*) |
| **Where** | `docs/manual-tests/triage.db`, table `finding`, refs RLA-C2 and RLU-C4; `docs/manual-tests/findings.tsv` :2378 and :2483; compare RLA2-C4 and RLU2-C11 in the same table |
| **Needs execution** | no |

`f7e94a50` appended a dated "Superseded 2026-09-25: ..." sentence to both dispositions in the documents (`RLA.md`, `RLU.md`). The rows did not change. Read with `mode=ro`:

- **RLA-C2:** status `Closed`, and a status note of *"3.0.0 release review, round 1 (2026-09-25): Fixed - into the configuration running, by its name, the running layout is captured first and the reload after the impor"*.
- **RLU-C4:** the round-1 note ending *"not one where t"*.
- **The `disposition` field** holds the first 120 characters, so the appended sentence can never show there.

Round 2's rows that RLA3-B1 superseded were handled: RLA2-C4 and RLU2-C11 read *"superseded by RLA3-B1"*. The documents are deleted when the round closes. After that, a query of RLA-C2 is told again that placements the import brought stay, which round 3's door no longer does, and RLU-C4 is told the "trains turn round" clause is there.

**Direction:** the same status note RLA2-C4 got, naming RLA2-B1 and RLD3-C1 for RLA-C2, and RLA2-C7 and RLU3-C1 for RLU-C4.

---

### RLA4-C7 - RLA3-C2's follow-up waits on "the mock station serves no multi-unit"; the file it serves has one, as both round-3 reviewers said

| | |
|---|---|
| **Disposition** | Fixed in the records - OB-303 says the mock station serves a multi-unit, and what its claims need (d5b97ee8). |
| **Grade** | C |
| **Names** | RLA3-C2 (*"Follow-up - filed as OB-303"*), RLD3-C3; OB-303 (2) and its direction |
| **Where** | `docs/manual-tests/issues.md:1332` (*"the members half and the not-while-running guard can be deleted with every test green - the mock station serves no multi-unit"*; direction *"serve a multi-unit from the mock station for the claims"*); `test/core/testMockCentralStation.java:164` (`fixtureFor` serves `test/lokomotive.cs2`); `test/lokomotive.cs2` :1500-1553 (OBB 1043, uid 0x2c01, `.traktion` 1043 006-4 ÖBB and 1043 001-5 ÖBB); `CS2File.java:1696-1700` (`traktion` makes a `MULTI_UNIT`) |
| **Needs execution** | no |

OB-303 records the gap in RLA3-C2 correctly, but gives the wrong cost: it says the claims wait because the mock station serves no multi-unit. The mock station serves `test/lokomotive.cs2` for every `lokomotive.cs2` request. That file holds OBB 1043, which `CS2File` parses as a `MULTI_UNIT` with two members.

RLA3-C2 said so (*"The fixture for both exists: `test/lokomotive.cs2` serves a traktion (line 1548)"*), and so did RLD3-C3, which named the locomotives. What the claim needs is the database's copy of OBB 1043 holding one member before the sync, with the other member standing. That is a set-up, not a new fixture.

The deferral may still be sound on its other grounds (the reach, and the next load or Place clears it). But Adam is told that the members half and the running guard wait on test infrastructure that already exists. Those are the half of RLA2-B2 that can evict a train mid-run.

**Direction:** correct OB-303 (2), and say what its claims need: the served multi-unit, and the database's copy of it given one member.

---

### RLA4-C8 - The question put to Adam for RLA3-B1 lists five settings its rule reaches; it reaches two more - a home taken off, and an exclusion list emptied

| | |
|---|---|
| **Disposition** | Fixed in the records - open-questions.md's question lists a home taken off and an emptied exclusion list (d5b97ee8). |
| **Grade** | C |
| **Names** | RLA3-B1 (*"Put to him in the report and open-questions.md"*) - not the defect, which is known, but the list the question gives him |
| **Where** | `docs/reference/open-questions.md:238-245`; `AutonomySession.importLegacy` :1171-1180 (a home is written where the square has none and the configuration homes the train nowhere), :1187-1191 (`excludedLocs` is a carried setting); `AutonomySession.setHome` :8154-8161 (*"null to clear this square's home"*) and `writePointProperty` :8628 (null removes the key); `AutonomyEditorPanel.java:5655-5656` (an emptied exclusion list is written as `null`) |
| **Needs execution** | no - the RLA3-B1 request's fixture shows both, if wanted |

The question says *"Every editor door stores a default as nothing - a maximum of 0, Can Be Chosen ticked, a square switched back on, priority 0, speed 100%"*. The same gap test reaches two more things.

- **A home taken off.** Clearing a home removes the key. On a second import of the same old file, a train the configuration now homes nowhere gets the file's home back: `homedInConfiguration` does not hold it, and the square has no `home`. Return Home then sends it there.
- **An exclusion list emptied.** It is written as nothing, and `excludedLocs` is one of `CARRIED_SETTINGS`, so the file's exclusions return and the station refuses those locomotives again.

The capture does not protect either: `Point.toJSON` writes `home` only where there is one, and exclusions only where there are some.

Neither is on the list Adam is choosing over. His recommendation, option (a), is to *"say in its question that settings at their default take the file's"*, and a home is not what he would read as a setting. The import's own message keeps homes apart from settings.

**Direction:** add "a home taken off, an exclusion list emptied" to the question's list, and to the wording option (a) would put in the import's question.

---

### RLA4-C9 - A seventh comment on the answered-0 rule, which RLA3-C6's sweep missed, states the rule the decision replaced: nothing that reads a length can tell an answered 0 from unmeasured track

| | |
|---|---|
| **Disposition** | Fixed - the javadoc says what reads an answered 0 now, and marks the 2026-09-23 answer superseded (d5b97ee8). |
| **Grade** | C |
| **Names** | RLA3-C6 (*"the six comments ... name the own-tail exception"*) and RLA2-C8, which swept the comments on this rule; ADU-C7 and ADA-A1, the decision of 2026-09-25 |
| **Where** | `AutonomyCompanionStore.java:1167-1176` (the javadoc of the method that records an answered 0; `git blame`: 2026-09-23, before the decision); `GraphReducer.answeredAtZero` :1628-1630 and the `Place`s it builds (:1613, :1617); `Edge.isMeasured`'s javadoc (:492-505); open-questions.md :135 |
| **Needs execution** | no |

The javadoc says: *"nothing that reads a length can tell it from a square nobody measured: `getTileLength` answers 0 for both, and every length rule asks `> 0`. What reads the difference is only `isTileLengthAnswered`, which the Mass Assign Lengths walk and the Unmeasured Track display ask ... Adam, asked directly: a deliberate 0 is not a measurement of nothing, and a stretch whose answers are all 0 is still not judged."*

That was the rule on 2026-09-23. Since the decision of 2026-09-25 (*"Yes, count answered 0 as 0"*), the reducer asks `isTileLengthAnswered` for every place it builds (`answeredAtZero`). `Edge.isMeasured` reads it, and so do the route in, the room walk, the tail claim, the berth rule, the tail question and the Atomic Routes gate. Answered 0 throughout, a stretch is judged as measured track of no length.

The six comments RLA3-C6 fixed each said the new rule without its exception. This one says the old rule, at the method that records the answer. That is where someone deciding whether an answered 0 can change a train's route would look first. Nothing on the railway changes; it is a record that contradicts the rule the length code enforces.

**Direction:** say what the answer is now read by - `Edge.isMeasured`, through the reducer's places, by every length rule but the own-tail one (OB-300) - and mark the 2026-09-23 quote as superseded on 2026-09-25.

---

### RLA4-D1 - RLD3-C1 with autonomy idle: an old file into the configuration in use is captured into first, places no train, names them, and reloads without a capture; twins of the name still meet "name in use"

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLD3-C1, RLD2-C3, RLA2-B3, OB-183; RLA3-D1's twins |
| **Where** | `AutonomyViewerPanel.importLegacyGraph` :1260-1291, :1330-1335, :1406; `loadAfterImport` :687-700; `AutonomySession.importLegacy` :987-1010, :1140-1168 |

The capture into the configuration in use runs before `importLegacy`, so the seed reads the running layout's placements and homes:

- A file train that the railway has standing goes to `alreadyPlaced`.
- One it does not goes to `notPlacedInUse`, before the duplicate check and before any `loc` is written.
- Nothing is put in `facingToFind`, and `placed` stays 0.

The running configuration is chosen again before the save (:1289-1291). `loadAfterImport(into, false)` reloads it through `load(..., false)`, and with nothing busy `prepareAutonomyReload` asks nothing. A train that a run moved therefore keeps its new square, and its facing is captured from the copy it stands on.

The new line (`infoLegacyNotPlacedInUse`, `{0}` and `{1}` in all eight bundles) is added only when there is a name to give. A name differing from the running one only in case still reaches `createConfiguration` and is refused as a name in use.

The capture is `load`'s own capture moved earlier. It asks what `load` asks, and the census allowance says the same. It does not ask `setupEditDeclinedDuringRun`, and neither does `load`'s, so that exposure is not new.

RLA3-D3's facing claim and RLA3-D4's homes claim import into configurations not in use, and round 3 does not touch them.

---

### RLA4-D2 - RLD3-C2: a bundle under the name in use is refused at the one door, after it is read and before the question, and the pin fails on the message if the refusal moves

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLD3-C2, RLA2-B1 |
| **Where** | `AutonomyViewerPanel.importConfiguration` :1060-1073; `testAnImportIntoTheConfigurationInUseIsRefused` :617-686 |

The refusal now needs `format == BUNDLE`, so it follows the read and the shape test. It still comes before the fill-gaps or replace question and before anything is created. All three menu entries reach it.

The pin writes `exportBundle(inUse)` to a temp file, as Export does, and imports it under the running name. It asserts the configuration, the names, `setup.json` on disk and the message.

A bundle of his own unchanged configuration may leave the first three equal even without the refusal. But the message assertion fails as soon as the refusal is gone or moved into `importLegacyGraph`, which is RLD3-C2's mutation.

---

### RLA4-D3 - RLU3-C2's split asks the start's own rule of the other copy, and the eight bundles agree at 1,841 keys

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLU3-C2, RLU3-C1, RLA3-D5 |
| **Where** | `Layout.whyItReachesNoStation` :5052-5084; `testWhyStuck` :706, :710-714, :742-745, :780-785; the eight `messages*.properties` |

The loop returns "turn it round" at the first other copy that reaches a station and passes `whyNoTrainIsStartedFrom`. It keeps the first refused one only to choose between the two new sentences, so a startable copy later in the order still wins. On a built railway a copy is switched off only with its whole square, and `explainCannotStart` asks the train's own copy first. So the "refused" sentence in practice means a barred copy at a square every train turns at, or a copy that is no station, and "drive it off by hand" is the right and only remedy for both.

Read in memory at HEAD, the eight bundles hold 1,841 keys each (1,838 plus the three new ones):

- the same set in all eight, with no duplicates;
- every key's placeholders equal to English's, and no byte above 127;
- no straight apostrophe in a value that carries a placeholder (`infoLegacyNotPlacedInUse` uses U+2019);
- the only empty values are Italian's and Polish's plural suffix.

The either-way sentence has `{0}` and `{1}` only, in all eight. `testWhyStuck` :706 still passes a third argument, which `MessageFormat` ignores, so its expected string is the production one.

---

### RLA4-D4 - RLA3-C1 to RLA3-C3's records: the sync comment and behaviour.md's "swept by the next load or Place" are true

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLA3-C1, RLA3-C2, RLA3-C3, RLD3-C3, RLA3-D2 |
| **Where** | `MarklinControlStation.java:1713-1716`; behaviour.md :1960; `Layout.java:9617` (Place), :12710 (`fromJSON`); issues.md OB-303 |

A load's `fromJSON` calls `clearMultiUnitConflictsWith` before it puts each train down. A Place asks it too, before placing. So a member that joined a standing multi-unit during a run does not stand beside it after the next load, or after a Place of either train, as the comment and behaviour.md now say.

The sync's code is unchanged since RLA3-D2 (only the comment moved), and OB-303 carries all three findings with their directions. Its one wrong premise is RLA4-C7.

---

### RLA4-D5 - RLA3-C4, RLA3-C5 and RLA3-C6: the sentence, the follow-up, six comments and the open question say what the code does

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLA3-C4, RLA3-C5, RLA3-C6 |
| **Where** | behaviour.md :2445-2446, :1022-1023; `Layout.java:367`; `AutonomySession.java` :3705, :3759, :8738; `GraphReducer.java:290`; `AutonomyEditorPanel.java:7078`; open-questions.md :135; issues.md OB-304 |

- **RLA3-C4:** behaviour.md :2446 gives the exception. The sentence in the other section is RLA4-C5.
- **RLA3-C5:** OB-304 names the three silent skips, the uncounted facing and the arrival preference, with directions.
- **RLA3-C6:** each of the six comments and open-questions.md's "Decided" line now say "every length rule but the own-tail one (OB-300)". Of the other hits for `git grep "every length rule"` in `src/`, `Layout.java:10559` is about unmeasured track and is right. `AutonomyCompanionStore.java:1173` is not: it states the rule the decision replaced (RLA4-C9). The rows are RLA4-C6.

---

### RLA4-D6 - RLD3-C1's MT-298 half: the claim chooses the imported configuration through `load`, and fails on a refusal or on an overwrite

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLD3-C1 (MT-298), RLD2-C7 |
| **Where** | `testASecondImportIntoTheConfigurationInUseKeepsAHandMadeChange` :811-889; tests.md MT-298's comment of 2026-09-25 |

The claim imports MT-298 into "MT-298 run" and chooses it with `load("MT-298 run", false)`: the menu's own call, which captures his configuration and makes the imported one run. It asserts that as a precondition. It then changes a named station's maximum by hand, reloads without a capture as the editor does, and imports again under that name.

- A refusal gives no import message, so `placedIn` is null and the claim is red.
- A gap-fill turned into an overwrite writes the file's maximum over the hand one, and the claim is red.

tests.md's comment names it and is appended, not edited.

---

### RLA4-D7 - The length, tail and berth rules and the Return Home planner are untouched this round, and the javadoc ratchet's counts for the changed autonomy files hold

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLA-D1, RLA3-D7 |
| **Where** | `git diff --stat d779578b HEAD -- src/ test/` |

Round 3 changed these source files:

- `Layout`: one comment, and `whyItReachesNoStation`.
- `AutonomySession`: three comments, and `importLegacy`'s new flag with its field.
- `GraphReducer` and `AutonomyEditorPanel`: one comment each.
- `AutonomyMenu`, `MarklinControlStation` and `MarklinLocomotive`: comments.
- `AutonomyViewerPanel`: the import.
- The eight bundles.

Nothing in `walkOneTail`, `measuredRouteIn`, `measuredRoomAtTheEndOf`, `whyABerthCannotHoldIt`, `whyItWouldMeetItsOwnTail`, `HomeStaging` or `AutonomyBuilder` changed. So the runtime and the planner still ask the same methods, and RLA-D1 holds.

The orphaned-javadoc rule, run in memory, gives 9 for `AutonomySession`, 2 for `AutonomyViewerPanel` and 2 for `Layout`, at `d779578b` and at HEAD. Those are the entries `testJavadocsAreAttached` pins, and the two new overloads' javadocs each sit on their method.
