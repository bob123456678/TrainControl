# RLV13 - Validation, round 14: the fixes for RLV12 - one gate for every door that sends a train, the trains moved on along a chain and kept apart by square, no setup edit from a tail answer or a direction follow during a run, and the message pass (TrainControl 3.0.0)

**Status:** open

**Prefix:** RLV13

**Reviewed:** branch `autonomy-diagram-r0` at `869d40f7`, read and run from `git archive 869d40f7` unpacked into `validate30/rlv13/` (`src869` for reading and the baseline; `p13` for the probes; `mRem`, `mOK` for mutations), `cs2_sample_layout` excluded and an empty folder of that name made for the runner.  TC30's working tree and the main checkout's were never read; git was used only as `git -C tc-30 log / show / diff / archive`.  Scope `98407a74..869d40f7`: the claims `cd19e9ca`, the fixes and the gate `fa641a6e`, the message pass `2d8726a6`, the records `98b8b27a` and the store `869d40f7`.  (`b513f86a`, committed at 02:09 after the scope's end, touches RLV13-C1 and was not validated.)  2026-09-29.

## Method

**Read whole:** the brief; `docs/reviews/README.md`; RLV12.md as written (`validate30/RLV12.md`) and as dispositioned (`docs/reviews-2026-09-25/RLV12.md` at `869d40f7`, diffed against it - only the ten disposition cells differ); every commit message in scope; the source diffs of `fa641a6e` (TrainControlUI, AutoLocomotiveStatus, LayoutRightclickAutonomyMenu, Layout, AutonomySession, TailCrossedPrompt, MarklinControlStation, LayoutLabel, AutonomyViewerPanel, AutonomyOverlayToggle, the new key in eight bundles), `docs/tools/one.sh` and `battery.sh`; the claims of `cd19e9ca` and `fa641a6e` (`testNoSetupEditDuringARun` whole, `testATrainIsDispatchedOnce`'s five new claims, `testTheArrivalHonoursTheAnswer`'s and `testAutonomyDiagramSession`'s, and the four source checks changed); the 33 keys `2d8726a6` rewrites and the two `fa641a6e` adds, old and new, in all eight bundles, decoded side by side by a script (`rlv13/msg/`) that also compared placeholders, quoted labels, apostrophes and ASCII, and looked every quoted phrase up in its own bundle; the records' hunks (behaviour.md, open-questions.md, findings.tsv) and a copy of `triage.db`, queried; the round's mutation spec and log (`mut_r14.json`, `mut_r14.log`: 20 of 20 red), its green, message and OOM logs; and, after it finished, the coordinator's battery (`battery-r14.log` and its per-class output).

**The code at 869d40f7, around each change:** every caller of `executePath`, `runLocomotives`, `executeTimetable`, `requestStartAutonomy`, `requestReturnToHome`, `restartTimetable`, `startAutonomyActionPerformed`, `executeTimetableActionPerformed`, the Start strip's mirror and `HomeLocomotiveMenu`; the window's key listeners and every `doClick`; `whyNoTrainMayBeSent`, `refusedToSendATrain`, `sendATrainByHand`, `whyThisPathCannotBeTaken`, `whyAutonomyStartIsRefused`, `whyAHandSendIsRefused`, `refuseWhileEditorOpen` and its callers, `isLayoutEditorOpen`, `openLayoutEditor` and its posted build, `whyAutonomyEditorCannotOpen`, `keepAtomicRoutesOnWhileTheRailwayCouldReleaseTrack` and its callers, `ManualReversalPrompt.forJourney`; `Layout.getLastPointsReached`, `keptApart`, `movedOn`, `placeOf`, `stationsAheadItHolds`, `lastKnownPoint`, `isRunning`, `isAlreadyUnderway`, `executePath`'s count, the arrival's turn; `Point.isSamePlaceAs` / `setBlock`; `AutonomySession.moveOntoFacingCopy` and its three callers, `faceTheWayItCameIn`, `flipFacing`, `save`; `followDirectionChanges`, `followTheTurn`, `reconcileFacingWhenIdle`; `TailCrossedPrompt.whereTheAnswerGoes`, `noteADroppedAnswer`, `runningNow` and the three doors that ask them (`LayoutRightclickAutonomyMenu.placeFacing`, `GraphLocAssign.commitAndRecord`, the diagram's Ctrl+V); the diagram menu's Edit Locomotive and its `refusedWhileRunning`; the editor panel's factories and `tileClicked`; every `hasAutoLayout()` in `src`; `AutonomyViewerPanel`'s import question; `AutonomyCompanionStore.isPageNumberingSuspect`; `whyNonAtomicRoutesAreRefused`.

**Runs.**  Every JVM through the archive's own `docs/tools/one.sh`, `TC_SCRATCH` a Windows-form folder per job under `rlv13/scratch`, from a queue (`rlv13/tools/queue.sh`) that started each job only when no `java.exe`, `javaw.exe` or `javac.exe` had been seen for two checks 20 s apart and no battery lock file existed, and would have retried a job the runner refused, deciding from that attempt's own log (none was refused).  The coordinator's battery held the machine until 02:07; everything below ran 02:08-02:19, one job after another.

- **Baseline** (`src869`): thirteen targets, 99 tests (RLV13-D9).
- **Probes** (scratch test code in `p13` only, `regression.rlv13Probes`, each writing what it saw to `rlv13/out`): on the live-snapshot sandbox with the window, `rlv13ProbeASendQueuedAheadOfTheEditorsBuild` (RLV13-C2) and `rlv13ProbeATurnMadeAtRestFollowedAfterASendIsLost` (RLV13-D4).
- **Mutations** (each a fresh archive with only its change): `mRem` - Start's refusal over a Central Station layout taken out of the gate, run against the six classes that touch Start, the gate or such a layout; `mOK` - the diagram's Edit Locomotive's second refusal taken out, run against its claim (RLV13-C9).  The round's 20 were not repeated; `mut_r14.log` has each red where the dispositions say.
- **The runner:** `one.sh` given a real and a misspelled method, then two real ones (RLV13-D7).
- **The battery's two reds** re-run on the archive, whole class and the first by name (RLV13-C1).

**Nothing written** outside `validate30/rlv13/` and this file.  The runner itself takes the shared lock under `%TEMP%` and a preference node of its own, and removes both; every `state-one-*` folder of my runs is gone.  Two nodes under `HKCU\Software\JavaSoft\Prefs\traincontrol-test-runs` exist (`battery-680`, `one-620`) - the same two RLV12 found before its runs; not mine, not touched.  No git state changed; no commit.  No tree has anything in `cs2_sample_layout`.  `LocDB.data` and `UIState.data` in each tree are copies from TC30.  The real `LocDB.data` and `UIState.data` - the main checkout's and `C:/Users/adamo`'s - were copied and hashed before the first JVM and compared byte for byte after the last: unchanged (`3f54de3a...`, `3604bf5f...`, `6ef56939...`, `9e32894e...`).  Nothing of mine is running: the queues ended, two `tail` processes my stopped monitors left were ended by process id (their command lines named `rlv13/logs/queue.log`), and no `java.exe`, `javaw.exe` or `javac.exe` is left.

**Counts:** 0 A, 0 B, 9 C, 10 D.

**The single gate is true at every door, as a question** (RLV13-D1): Start - its button, the strip's mirror, the diagram's item and the API - Execute Timetable, Return Home and both hand doors ask it first, nothing in `src` dispatches past it, and nothing is newly refused but an editor at the diagram's destinations.  **It is not yet true in time** (RLV13-C2): the gate asks whether the editor's window exists, and the editor is built in a task posted after its own question about trains, which does not ask again - so a send queued ahead of that task runs with the editor open, and a square clicked in it edits the setup under the run, RLV12-B1's outcome by a narrower door.  **Round 14's other fixes are right** (RLV13-D2 to D6): the chain push, the squares, the Edit Locomotive refusal, the primitive's decline, the pairs changed and the retired arrival hold under every arrangement read or measured; the one gap is the tail answer's own predicate, which does not see Return Home's planning (RLV13-C3).  **The message pass** keeps what 30 of its 33 keys need and every placeholder, but counts in words where it did not (RLV13-C4), keeps two labels that are not on screen (RLV13-C5) and thins two reasons (RLV13-C6).  **The acceptance battery on the commit is red** in two older source checks the round moved code out from under (RLV13-C1; `b513f86a`, after the scope, addresses both).  **New in round 14:** C1, C3, C4, C6, C8, C9 (its second half).  **Older:** C2 (exposed by the gate), C5 (left by the pass), C7, C9 (its first half, moved by the round).

---

### RLV13-C1 - The round leaves the battery red: two older claims pin text the round took away, one the arrival's turn and one the hand doors' prompt

| | |
|---|---|
| **Disposition** | Fixed in b513f86a - testATrainCoversTheTrackBehindIt pins the turn by the statement that turns now, after arrived.setArrivedFrom; testNonReversibleTrains looks for the reversal question in sendATrainByHand and checks that neither hand-door file dispatches itself.  Both green, and the battery's two reds with them. |
| **Grade** | C.  Nothing the railway does is wrong - both rules hold in the code - but the acceptance battery on the round's commit is red in two classes, and each red is a guard that no longer reads the code it guards.  `core.testATrainCoversTheTrackBehindIt.testTheArrivalRecordsWhichWayTheTrainCameIn` pins that the arrival records its side before it turns the train by finding the literal `loc.delay(this.getMinDelay(), this.getMaxDelay()).switchDirection()`; RLV12-C5's fix split that statement round the new retire check, so the index is -1 and the assertion *"the arrival side is recorded AFTER the train is turned round"* fires about an order that is still right.  `core.testNonReversibleTrains.testEveryManualDoorHandsOverAPrompt` is a census of the two hand doors' files for `ManualReversalPrompt.forJourney(`; both doors now send through `TrainControlUI.sendATrainByHand`, which asks it, so the census fails on the first file it reads (the diagram's) and would fail on the second.  The round's green runs named the classes it touched and neither of these.  **New in round 14** (`fa641a6e`). |
| **Names** | fa641a6e, RLV12-B1, RLV12-C5, OB-182, DIR-A2 |
| **Where** | `test/core/testATrainCoversTheTrackBehindIt.java:386-392`; `Layout.java:9444-9450`; `test/core/testNonReversibleTrains.java:409-438`; `TrainControlUI.java:6574-6575` |
| **Needs execution** | done - both classes re-run on the archive: `testATrainCoversTheTrackBehindIt` 19 run, 1 failure; `testNonReversibleTrains` 18 run, 1 failure; each failing method by name, 1 of 1 red, with the messages quoted; the coordinator's battery on the main checkout the same (316 classes green, these 2 with failures) |

**Direction:** pin the turn by the statement that turns now (`loc.switchDirection().delay(1000)`), still after `arrived.setArrivedFrom(`; point the census at `sendATrainByHand` in `TrainControlUI.java` and keep the hand-door files' check that they send through it (which `testAHandSendIsRefusedWhileTheSetupIsBroken` already makes).  `b513f86a`, committed after this scope, changes both checks that way; not validated here.

---

### RLV13-C2 - An editor asked for at rest is built in a task that does not ask again: a send queued ahead of that task runs with the editor open, and a square clicked in it edits the setup under the run

| | |
|---|---|
| **Disposition** | Fixed - an editor on its way counts: the gate asks anEditorIsOpenOrOnItsWay (editorOnItsWay, set when the editor is asked for and cleared once render() has shown it, or the build refused or failed); the posted build asks isAutonomyBusy again, refuses, says so and gives the Edit button back; and the run doors ask refusedForAnEditorOnItsWay at their last event-thread moment before the dispatch - Start's and Return Home's workers beside the atomic-routes gate, Execute Timetable's posted body before it.  claims 2dd667be (red first), fix e19d2b63 (the Auto tab's send, Start, and the build over a begun run, each seen running under the editor).  Those claims queued the send and Start AHEAD of the editor's request, so the build's second look was what refused and three mutations survived them (the gate's "on its way" by behaviour, the editor question's flag, Start's own look); claims ee02bc60 reach each one - the gate asked with the editor on its way, and Start's worker held on an armed route's question while the editor is built - and the two queued claims guard the pairs: mutations ME1 red, MEh red, ME2 red, ME3 red, ME13 red, ME23 red.  Return Home's and Execute Timetable's hops are the same helper at the same moment as Start's, by reading: neither has a fixture that reaches its hop without refusing first. |
| **Grade** | C.  The one gate asks whether an editor's window exists (`isLayoutEditorOpen`: `openEditor` displayable); `openLayoutEditor` asks whether trains run, disables the Edit button, and then builds the editor in a posted task that asks nothing.  Between the two, neither side can see the other: a send that runs after the editor was asked for and before it was built passes the gate, and the editor then opens over the run.  Measured with the real handlers: the event thread held for 1.5 s - a slow refresh - with an Auto tab double-click and Open Full Editor queued behind it, in that order.  The double-click's handler posts the send; the editor's request runs next, finds nothing running and posts the build; the send passes the gate and dispatches; the build opens the editor.  Then a square clicked in it wrote the setup and raised the waiting-edit flag - RLV12-B1's outcome, and with the flag up, RLV11-B2's exit.  Start is exposed the same way, by reading: with Start and then Edit queued, Start's click passes the gate and starts its worker, the editor's request finds nothing running yet, and the worker's `invokeAndWait` for the atomic-routes gate queues behind the build - `runLocomotives` follows with the editor open.  It needs two clicks inside one busy spell of the event thread, which is why it is a C.  **Older** (the posted build and the busy question are OB-047's; the doors' editor question was always the window's), **exposed by round 14**, whose one gate is where the question now lives and whose `followTheTurn` fixed this very shape - decide, post, act without asking again - for the direction follow. |
| **Names** | RLV12-B1, RLV12-C4, RLV11-B2, OB-047, MT-135, OB-058 |
| **Where** | `TrainControlUI.java:6482-6484` (the gate's editor question), `:7142-7155` (`isLayoutEditorOpen`), `:5305-5309` (`openLayoutEditor`'s busy question), `:5342` (the button disabled), `:5390-5437` (the posted build, no question); `:27612-27627` (Start's worker: `invokeAndWait`, then `runLocomotives`) |
| **Needs execution** | done - probe `rlv13ProbeASendQueuedAheadOfTheEditorsBuild` |

| | editor | busy | 2-8-4 3505 SP | `1 - Main:0,2` | waiting-edit flag | asked |
|---|---|---|---|---|---|---|
| at rest | closed | false | standing | BOTH | false | - |
| after the two queued clicks | **open** (autonomy editor) | **true** | **under way, holding 6 points** | BOTH | false | nothing |
| after a square clicked in the editor | open | true | under way | **TOWARD_A** | **true** | nothing; log: *"A setup edit could not be applied to the running railway, because autonomy started between the edit and the moment it was to be applied ..."* |

**Direction:** ask again in the posted build - refuse, and give the button back, where autonomy has become busy since it was asked for, as `followTheTurn` now does; and let the gate see an editor on its way (`!editLayoutButton.isEnabled()` beside `isLayoutEditorOpen()`, as `whyAutonomyEditorCannotOpen` already reads it).  Claim: the probe, asserting no dispatch or no editor.

---

### RLV13-C3 - A tail answered while Return Home is planning is still written: `whereTheAnswerGoes` asks `Layout.isRunning`, where every other setup-edit refusal asks whether autonomy is busy

| | |
|---|---|
| **Disposition** | Fixed - whereTheAnswerGoes, and the log line with it, drop an answer while a staging flow owns the railway as well as while it runs (Layout.isStagingInProgress, set with the window's flag), so Return Home's planning counts as the window's isAutonomyBusy does.  claims 2dd667be (red first), fix e19d2b63; mutation MT3 red. |
| **Grade** | C.  The ruling's refusals all ask `isAutonomyBusy()` - the factories, the diagram's Edit Locomotive, `followTheTurn`, the editor's opening - which is `stagingFlowActive || isRunning()`, because Return Home spends its planning with nothing running (the method's own javadoc: *"isRunning reads false while the flow is very much under way"*).  The new drop asks the railway's `isRunning()` only.  So a tail question left waiting on the diagram (non-modal, up to 30 minutes), answered after Return Home was pressed and before its plan dispatches the first train - seconds on a full railway - writes the road into the setup and onto the running Point during the run Return Home has begun, and saves; a standing train's tail is what `edgesCoveredByStandingTrains` blocks for every path the plan tries.  The window is the planning time; the operator has to answer the question in it.  **New in round 14** (the check is new; the question in the other predicate). |
| **Names** | RLV12-C3, RLV12-C4, FR-077, OB-192 |
| **Where** | `TailCrossedPrompt.java:605` (`running.isRunning()`); `TrainControlUI.java:24800-24810` (`isAutonomyBusy`: `stagingFlowActive`), `:24607-24611` (Return Home raises `stagingFlowActive` and `setStagingInProgress(true)` before planning on its worker); `Layout.isStagingInProgress()` |
| **Needs execution** | no - by reading |

**Direction:** ask `running.isRunning() || running.isStagingInProgress()` - the railway's own record that a staging flow owns it, set with the window's flag - so the model-side question matches the window's; the log line's second question with it.

---

### RLV13-C4 - Two rewritten refusals now count in words, and say "1 locomotives have no train length" in every language

| | |
|---|---|
| **Disposition** | Fixed - both refusals of non-atomic routes put the count after the subject again, "{0} in all ({1})" and its equivalents, in all eight languages, so one train reads right and Polish needs no plural form.  claims 2dd667be (red first), fix e19d2b63; mutation MM4 red. |
| **Grade** | C.  `errorNonAtomicNeedsLengths` and `errorNonAtomicNeedsTrainLengths` - the checkbox's refusal of non-atomic routes - were worded around the count (*"... has no length, {0} in all ({1})"*), which reads right for any number.  The rewrite puts the count before a plural noun and verb: *"{0} pieces of track autonomy runs over have no length ({1})"*, *"{0} locomotives have no train length ({1}) ... Set their lengths first"* - and the same in all seven translations (*{0} Lokomotiven haben*, *{0} locomotives n'ont pas*, *{0} lokomotiver har*, ...), so one train without a length - the likely case, a locomotive just added - reads *"1 locomotives have no train length"*.  Polish is wrong for 1 to 4 (*{0} lokomotyw nie ma*: genitive plural, which Polish takes only from 5).  `whyNonAtomicRoutesAreRefused` passes `trains.size()` and `track.size()`, 1 included.  **New in round 14** (`2d8726a6`). |
| **Names** | 2d8726a6, VD14-C1 |
| **Where** | `messages*.properties` `autolayout.errorNonAtomicNeedsLengths`, `autolayout.errorNonAtomicNeedsTrainLengths`; `TrainControlUI.java:6204-6220` |
| **Needs execution** | no - by reading |

**Direction:** keep the new sentence's shape and move the count out of the subject, as before (*"... cannot be switched off: track autonomy runs over has no length - {0} in all ({1}) - and ..."*), or a `{0,choice,...}` pattern in the languages that can use one.

---

### RLV13-C5 - Two rewritten setup findings quote on-screen labels that are not on screen, and the pass kept them

| | |
|---|---|
| **Disposition** | Fixed - checkMayTurnOnDeadEnd quotes the menu item's own words in French and Italian, and checkTerminusTwoWaysIn names "Trains May Change Direction Here" (autosetup.ui.menuTurnMay) in every language; a claim checks all three findings that quote the setting against the item.  claims 2dd667be (red first), fix e19d2b63; mutation MM5 red. |
| **Grade** | C.  The commit says every on-screen label was kept, and it was - but two were not labels.  `checkMayTurnOnDeadEnd` quotes the square's setting as the menu names it; in French it quotes *"les trains peuvent changer de direction ici"* where the menu item (`autosetup.ui.menuTurnMay`) and `checkArrivalTrapped` beside it say *"Les trains peuvent changer de sens ici"*, and in Italian *"i treni possono cambiare direzione qui"* where the item says *"I treni possono invertire il senso qui"* - a French or Italian user looks for an item that does not exist.  And `checkTerminusTwoWaysIn` ends *"Or set the square to may turn round"* - unquoted in English, and quoted as a label in seven translations (*„darf wenden"*, *«peut rebrousser»*, *„mag keren"*, *„może zmieniać kierunek"* ...) that no menu item carries; the item is *Trains May Change Direction Here* in every language.  **Older** (both wordings predate the pass), **left by round 14's pass**, which rewrote both keys around them. |
| **Names** | 2d8726a6 |
| **Where** | `messages_fr.properties`, `messages_it.properties` `autosetup.ui.checkMayTurnOnDeadEnd`; `messages_{da,de,es,fr,it,nl,pl}.properties` `autosetup.ui.checkTerminusTwoWaysIn`; `autosetup.ui.menuTurnMay` in each |
| **Needs execution** | no - every quoted phrase of the 35 keys looked up in its own bundle by a script; these are the ones with no match |

**Direction:** quote `menuTurnMay`'s own words in French and Italian; in `checkTerminusTwoWaysIn` name the item in every language as `checkMayTurnOnDeadEnd` does ("*Trains May Change Direction Here*").

---

### RLV13-C6 - The pass dropped the reason behind a remedy in one setup finding and the reassurance behind Save in another

| | |
|---|---|
| **Disposition** | Fixed - checkHalfMeasuredApproach says again when it applies (while part of its approach is measured and part is not), and warnPageRenumbered that a save tidies nothing while it stands, in all eight languages.  claims 2dd667be (red first), fix e19d2b63; mutation MM6 red. |
| **Grade** | C.  Adam's lighter touch was to keep a short reason where it helps the user decide.  `checkHalfMeasuredApproach` lost its condition - *"while part of its approach is measured and part is not"* - and kept its two remedies, "measure them, or clear the lengths on that approach": without the condition, clearing lengths reads as a way to make a warning go away rather than as the other way to make the approach consistent.  `warnPageRenumbered` lost *"not even by a save"*: the old notice told the user that pressing Save, which it then asks for, deletes nothing while the numbering is suspect; the new *"nothing is tidied away until this is settled. Save the setup to record the new numbering"* can be read as Save settling it and tidying.  (The code is as the old words said: `save()` prunes only while `isPageNumberingSuspect()` is false, and the conflicts are cleared by the next load.)  The translations follow the English in both.  **New in round 14** (`2d8726a6`). |
| **Names** | 2d8726a6, DR-B10 |
| **Where** | `messages*.properties` `autosetup.ui.checkHalfMeasuredApproach`, `autosetup.ui.warnPageRenumbered`; `AutonomySession.java:9530-9540`, `AutonomyCompanionStore.java:812-815` |
| **Needs execution** | no - by reading |

**Direction:** put back the half-clause in each (*"... may refuse trains that would fit while part of its approach is measured and part is not: ..."*; *"nothing is tidied away until this is settled, a save included"*).

---

### RLV13-C7 - One check-then-build pair is left off the event thread: the graceful stop's wait for the trains to stop, which the source claim does not know

| | |
|---|---|
| **Disposition** | Fixed - Graceful Stop's worker reads the railway once (getAutoLayoutIfLoaded), stops that one, and waits while it runs and is still the model's; the method is in the ask-once source claim.  claims 2dd667be (red first), fix e19d2b63; mutation MG7 red. |
| **Grade** | C.  RLV11-C5's family: `hasAutoLayout()` then `getAutoLayout()` is two takes of the model's lock, and Unload, on the event thread, can clear the model between them; the second then builds an empty railway that `hasAutoLayout` answers yes about.  Graceful Stop's worker polls `while (this.model.hasAutoLayout() && this.model.getAutoLayout().isRunning())` for the whole coast-down - Unload is allowed then, with its confirmation - and its first line builds a railway if there is none.  The claim for RLV12-C5 reads the model's file and three named doors, so it cannot see this one.  The window is one iteration's two lock takes, every `REPAINT_ROUTE_INTERVAL`.  **Older**; not in RLV12-C5's list, and the disposition's sweep ("the model's, the protection question's and the path gather's pairs") stopped short of it. |
| **Names** | RLV12-C5, RLV11-C5, UXR-B7 |
| **Where** | `TrainControlUI.java:27693` (`getAutoLayout().stopLocomotives()`), `:27710` (the pair); `testAutonomyDiagramSession.testTheModelAndTheDoorsOffTheEventThreadAskForTheRailwayOnce` (three doors named) |
| **Needs execution** | no - by reading; every other `hasAutoLayout()` pair in `src` was traced to the event thread |

**Direction:** read the railway once before the loop (`getAutoLayoutIfLoaded()`) and wait on that one, which is the railway the stop was asked of; add the door to the claim's list.

---

### RLV13-C8 - Comments the round left saying what is not so: Return Home "asked from a worker", six callers of the atomic-routes gate, and a renamed method named three times

| | |
|---|---|
| **Disposition** | Fixed in e19d2b63 - refusedToSendATrain's javadoc says every door asks it on the event thread and why the posted branch stays; the atomic-routes gate's javadoc counts five callers and four dispatch doors, and the test's javadoc four; the three comments name whyAutonomyStartIsRefused, and the Central Station test's javadoc too. |
| **Grade** | C.  `refusedToSendATrain`'s javadoc gives its reason for the off-thread branch as *"since Return Home is asked from a worker at the end of a staging run"*; `requestReturnToHome` has two callers, its button and the diagram's item, both on the event thread, and no door asks the gate off it - so the branch is dead and the reason is not true.  `keepAtomicRoutesOnWhileTheRailwayCouldReleaseTrack`'s javadoc - *"SIX CALLERS, AND THE COUNT HERE HAS BEEN WRONG TWICE"* - still names *"the two hand dispatches in AutoLocomotiveStatus and LayoutRightclickAutonomyMenu"* and says the test *"holds the five"*: there are five callers now and the test holds four, as does its own javadoc's *"any one of the five"*.  And three comments still name `refuseAutonomyStartWhileBroken`, renamed `whyAutonomyStartIsRefused` in `fa641a6e`, which swept the name from two other files.  **New in round 14.** |
| **Names** | fa641a6e, VD17-B1, VD17-T7 |
| **Where** | `TrainControlUI.java:6496-6497`, `:6321-6331`, `:24321`, `:24380`, `:24486`; `testNonAtomicRoutesNeedTheirLengths.java:500-501` |
| **Needs execution** | no - by reading |

**Direction:** drop the branch or say it is for a future caller; correct the count to five (four dispatch doors, one door each for both hand doors) in the three places; rename the three references.

---

### RLV13-C9 - What round 14's claims do not reach: Start's refusal over a Central Station layout, now one arm of the gate, and Edit Locomotive's second refusal

| | |
|---|---|
| **Disposition** | Fixed - claims: Start over a Central Station layout is refused by the gate in its own words (the existing test asks whyNoTrainMayBeSent(true)), and the diagram's Edit Locomotive refuses at OK when a run begins while its dialog is open (the answering thread begins it) - mutations mRem red, mOK red.  2dd667be. |
| **Grade** | C.  Claims that pass with the rule undone.  The gate's `true` flag exists for Start's own half, and the only thing that half asks that the hand doors' does not is `isRemoteLayout()` - OB-104's refusal to run autonomy over a layout that lives on the Central Station, where a loaded autonomy JSON leaves the Start button live (REG-C3).  Taken out, every class that touches Start, the gate or such a layout stays green: the source claim checks that the gate names `whyAutonomyStartIsRefused()`, not what that asks, and `testStartIsNotOfferedOverACentralStationLayout` asks the menu's affordance and the tooltip's sentence, not the press.  Without the arm, Start pressed there is refused only by the gate's other questions - an open editor, setup errors, the power - and otherwise goes on to start the railway (by reading).  And the diagram's Edit Locomotive asks twice, at the click and at OK; its claim starts the run before the click, so OK is never reached with a run begun, and the second refusal can go with nothing red.  **Older** (the remote arm was as unpinned in `refuseAutonomyStartWhileBroken`; the round moved it into the gate) and **new in round 14** (the second refusal). |
| **Names** | OB-104, REG-C3, REG2-C2, RLV12-C3 |
| **Where** | `TrainControlUI.java:6130` (the arm), `:6486` (the flag's use); `LayoutRightclickAutonomyMenu.java:841` (the second refusal); `testTheRefusalsAreAskedAtTheDoors.testTheOneGateAsksTheEditorTheSetupAndThePower`, `testSwitchingToACentralStationLayout.testStartIsNotOfferedOverACentralStationLayout`, `testNoSetupEditDuringARun.testTheDiagramsEditLocomotiveRefusesOnceTrainsRun` |
| **Needs execution** | done - mutations `mRem`, `mOK` |

| mutation | run | result |
|---|---|---|
| `mRem` - `if (isRemoteLayout()) return ...menuNoSetupPossible` taken out of `whyAutonomyStartIsRefused` | `testSwitchingToACentralStationLayout`, `testErrorsStopTheSetupRunning`, `testTheRefusalsAreAskedAtTheDoors`, `testAHandSendIsRefusedWhileTheSetupIsBroken`, `testTheRefusalToStartSaysWhichThing`, `testTheOldAutonomyTabIsGone` | **green** - 13/13, 8/8, 6/6, 7/7, 2/2, 6/6 |
| `mOK` - the refusal at OK taken out of the diagram's Edit Locomotive, the one at the click kept | `testTheDiagramsEditLocomotiveRefusesOnceTrainsRun` | **green** - 1/1 |

**Direction:** press Start over a Central Station layout with the button live - the setup `testStartIsNotOfferedOverACentralStationLayout` already makes - and assert the refusal's words and no run; for OK, begin the run while the dialog is open, as the tail claim does for its question.

---

### RLV13-D1 - The one gate: every door that sends a train asks it first, nothing sends past it, and nothing is newly refused but an open editor

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RLV13-C2 (an editor still being built), RLV13-C8 (its javadoc) and RLV13-C9 (the remote arm unpinned). |
| **Grade** | D |
| **Names** | RLV12-B1, OB-047, MT-135, MT-263, TDU-B1, SPEC-C4, GS-B1, RLU-B2, OB-104 |
| **Where** | `TrainControlUI.java:6482-6512` (`whyNoTrainMayBeSent`, `refusedToSendATrain`), `:6531-6587` (`whyThisPathCannotBeTaken`, `sendATrainByHand`), `:24507-24515` (`requestStartAutonomy`), `:24553-24558` (Return Home), `:27319` (Execute Timetable), `:27515` (Start); `AutoLocomotiveStatus.java:1057-1090`; `LayoutRightclickAutonomyMenu.java:478`, `:1417-1430`; `HomeLocomotiveMenu.java:71`; `AutonomyOverlayToggle.java:162-173` |
| **Needs execution** | done - the round's claims green on the archive; the rest by reading |

**Every door found.**  `executePath` has one caller outside the model, `sendATrainByHand`; `runLocomotives` one, Start's worker; `executeTimetable` two, Execute Timetable and Return Home.  Start is reached from its button, the diagram strip's mirror (`doClick` on that button) and the diagram's Start item through `requestStartAutonomy`; Return Home from its button and the diagram's item (`HomeLocomotiveMenu`); the hand sends from the Auto tab's double-click and the diagram's destinations.  No keyboard binding or accelerator reaches any of them (the window's key listeners drive locomotives, not autonomy), `restartTimetable` only resets the list, and there is no network or scripting entry in `src` - `Layout.runLocomotive(s)` is the programmatic API the examples use, with no window, by design.  Routes (the Central Station's, and s88-triggered ones) are a separate system the gate was never about.  Each door asks the gate as its first statement - Start and Execute Timetable before greying their buttons, Return Home before `stagingFlowActive`, `sendATrainByHand` before the path's rules, the reversal question, the atomic-routes gate and the dispatch (`testAHandSendIsRefusedWhileTheSetupIsBroken.door` pins that order).

**Nothing newly refused but the editor at the diagram's destinations**, which is the intended change: the gate asks what each door asked for itself (the editor, the setup in the same words - Start's for Start through the `true` flag, the hand doors' for the rest - and the power), and a hand send while another train runs is not refused, since the editor cannot be opened while one does.  The orders moved: Start and Execute Timetable now ask the power before greying (a refused press changes nothing, which is the rule's point); Return Home asks the power before whether trains are busy - only reachable with its button and item live while trains run, and both are greyed then.  **Owners and threads:** every door's refusal is shown over the main window on the event thread (both hand doors are on it - the Auto tab's in its `invokeLater`, the diagram's in its item's action); the reversal question keeps the owner each door gave it.  The `invokeLater` branch of `refusedToSendATrain` has no caller: nothing asks the gate off the event thread (RLV13-C8).  The API's `requestStartAutonomy` still returns quietly on a refusal the gate shows as a dialog, as it did for the editor and the setup before the round.

---

### RLV13-D2 - `keptApart`, `movedOn` and `placeOf`: the chain along any number of trains, squares by block, cycles, a train still locking its path, and what the carry and the fold do with the answer

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLV12-C1, RLV12-C2, RLV12-C7, RLV11-C1, RLV11-C2, RLV9-C4, MT-165 |
| **Where** | `Layout.java:1604-1631` (`getLastPointsReached`), `:1653-1705` (`keptApart`), `:1719-1757` (`movedOn`), `:1766-1769` (`placeOf`), `:1779-1807` (`stationsAheadItHolds`); `Point.java:943-950` (`isSamePlaceAs`) |
| **Needs execution** | done - the four claims green on the archive (`core.testATrainIsDispatchedOnce`); MC1, MC2, MO red in the round's log; the rest by reading |

`movedOn` changes nothing unless it succeeds: the only writes are its own last four lines, reached after a free station or a successful recursive push, so a failed branch leaves `out` and `keptThere` as they were and the next branch is tried against the same state.  The walk is a depth-first search over "this train holds a station that train is kept at", and the shared `moving` set does not lose an answer a fresh search would find - a train it marks failed could only succeed through a train already on the walk, which could then reach the same free station itself.  Each step is to a station further ahead on the train's own path, and `moving` stops a cycle.  A station a train holds records that train, and the lock keeps two trains off one block (`locomotiveInBlock`), so a train kept where its point records it - one still locking its path, kept at its start - is never pushed; `takingPath` gives `stationsAheadItHolds` the path reserved so far.  `placeOf` is the builder's block (`Point.setBlock` is set only from the `block` the build writes for a split square), the same test `isSamePlaceAs` makes.  The carry (`whereTheTrainsAre`) and both folds (`toJSON(Map)`) read the one map, so with no two trains on one square neither the carry's `clearBlockExcept` nor the fold's per-square merge has a second train to lose.  Where `movedOn` fails - a train holding no station ahead that is free or can be freed - the train stays at its point, as the javadoc says; that needs a cycle of trains each last seen where the next is heading, which no arrangement measured so far produces.

---

### RLV13-D3 - RLV12-C3: the diagram's Edit Locomotive refuses, and a tail answered once trains run is dropped at all three doors that ask it

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RLV13-C3 (Return Home's planning) and RLV13-C9 (the second refusal unreached). |
| **Grade** | D |
| **Names** | RLV12-C3, W21-B3, REG8-B2, TDU2-A1 |
| **Where** | `LayoutRightclickAutonomyMenu.java:203-210`, `:812-856`; `TailCrossedPrompt.java:600-610`, `:688-708`; the three askers: `LayoutRightclickAutonomyMenu.java:1340-1368` (Place), `GraphLocAssign.java:317-345` (Edit Locomotive), `TrainControlUI.java:8654-8683` (the diagram's Ctrl+V) |
| **Needs execution** | done - the claims green on the archive; MC3a-c red in the round's log |

Every door that asks the tail question takes where to write from `whereTheAnswerGoes`, so one check covers the three; a dropped answer writes neither the setup's road nor the running Point's.  What was written at rest before the question - the placement, facing and side - is still saved afterwards, during the run if one began in the wait, which the disposition states; nothing a run reads is written by that save.  The log's reason is asked again when it is written, a moment after the drop was decided, so a run that ended in between logs the square-changed sentence instead - harmless.

---

### RLV13-D4 - RLV12-C4: `moveOntoFacingCopy` declines a train under way for all three of its callers, and the posted follow's new question ends a turn as the ruling ends one

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLV12-C4, RLV11-C3, DIR-B3, DIR-B4, MON-C15 |
| **Where** | `AutonomySession.java:2067-2115` (the decline at `:2109`), callers `:2231` (`faceTheWayItCameIn`, which keeps its own check at `:2205`), `:2381` (`flipFacing`), `:8031` (the facing menu); `TrainControlUI.java:13130-13143` (the decision on the Central Station's thread), `:13164`, `:13180-13205` (`followTheTurn`), `:7966-8004` (the idle levelling) |
| **Needs execution** | done - the claims green on the archive; MC4a, MC4b, MC7c red in the round's log; probe `rlv13ProbeATurnMadeAtRestFollowedAfterASendIsLost` |

The decline is after the train is found on the square and before anything is cleared, so a declined move changes nothing on the railway; `running` is null-checked at the top, so a setup-only caller never reaches it.  `flipFacing` still writes the setup's facing before the move, but its one caller, `followTheTurn`, now returns before it while autonomy is busy; the facing menu is a factory item and refuses during a run.

**What the posted follow's question does to a turn made at the moment a run begins**, measured: the Central Station's thread decides with `isRunning()` and records the new direction; where a send runs before the posted work, the work declines on `isAutonomyBusy()`, and the idle levelling then takes the new direction as the baseline - so the turn is never written, exactly as a turn made during a run is not (Adam's ruling: *"if a manual command is sent, ignore it"*; the levelling is its own mechanism for that).  The two predicates differ over Return Home's planning, where the thread reads nothing running and the work reads busy: a turn there, followed before round 14, is now ignored as a run's.  Consistent with the ruling and with `isAutonomyBusy` everywhere else; noted, not graded.

| | EN57-947's direction | setup facing | on the railway |
|---|---|---|---|
| at rest (BottomInner, `1 - Main:13,9`) | forward | W | W |
| turned; the echo decided with nothing running; a send queued ahead of the posted follow | backward | W | W |
| the send's run finished, the refresh levelled, the same echo again | backward | W | W |
| control: turned back, nothing queued - followed, and logged *"EN57-947 changed direction, so its facing ... was updated to match"* | forward | S | S |

The last row is the ruling's cost, as for any turn ignored during a run: the next turn is followed from the levelled baseline, so the setup ends opposite the locomotive until it is set from the right-click menu.

---

### RLV13-D5 - RLV12-C5: the changed pairs, and the arrival's turn on a retired railway

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RLV13-C7. |
| **Grade** | D |
| **Names** | RLV12-C5, RLV11-C5, CS3-C4 |
| **Where** | `MarklinControlStation.java:3802-3810`, `:3862-3871`, `:3911-3919`; `TrainControlUI.java:24800-24810`; `LayoutRightclickAutonomyMenu.java:239-246`; `LayoutLabel.java:2009-2037`; `TailCrossedPrompt.java:636-640`; `Layout.java:9436-9470` |
| **Needs execution** | done - `testTheModelAndTheDoorsOffTheEventThreadAskForTheRailwayOnce` and `testATrainArrivingOnARailwayRetiredOnTheWayIsNotTurned` green on the archive; MC5m/b/g/l/t red in the round's log |

Each changed site reads the railway once and treats none as nothing to do, as its `hasAutoLayout()` false did.  The arrival asks `isCurrentLayout()` after the pause in which Unload can come and before the turn, and records no owed turn when it declines, so the drain has nothing to write for a train that did not turn; the arrival's log line ("reached a terminus ...") is still written first.

---

### RLV13-D6 - RLV12-C6: the stations' maxima sentence, in eight languages

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLV12-C6, RLA3-B1, RLU2-C11 |
| **Where** | `AutonomyViewerPanel.java:1219-1240`; `messages*.properties` `autosetup.ui.importKeepsTheStationMaximums` |
| **Needs execution** | done - the two import claims green in the coordinator's battery; MC6 red in the round's log |

Appended to both old-file questions and to no bundle's, after the default rule it is the exception to, with the configuration's name as `{0}` in every language; ASCII with escapes; read with `I18n.f` and no apostrophe in it.  "Once {0} has been loaded" is true for the configuration in use (the import folds first) and for any other that a fold has written since - every exit, load, Unload and editor open; a configuration loaded once and left by a crash before any fold would take the file's maxima, the one case the sentence does not fit.

---

### RLV13-D7 - The runners: a method list with a name that did not run, a JVM that runs out of memory, a hung class counted once

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLV12-C8, V33-B1, V34-C3 |
| **Where** | `docs/tools/one.sh:404-408`, `:490-491`, `:536-541`, `:560-562`, `:600-625`; `docs/tools/battery.sh:512-516`, `:755-757` |
| **Needs execution** | done - `one.sh "t1:core.testATrainIsDispatchedOnce.testAChainOfTrainsUnderWayIsMovedOnAlongIt,core.testATrainIsDispatchedOnce.noSuchMethodAtAll"`: *Total tests run: 1* and *DID NOT RUN EVERY METHOD ASKED FOR - no result for: core.testATrainIsDispatchedOnce.noSuchMethodAtAll*, counted, exit 1; two real names in one list: 2 run, clean |

The results file is removed before each JVM, so a class that writes none cannot be read from the last run's; each name is looked for as its own `signature="name(` beside `instance:pkg.Class@`, so a prefix of another method's name does not match and a method of another class does not either; a name that ran and failed, or was skipped, is found and reported by the checks after it.  `-XX:+ExitOnOutOfMemoryError` is honoured by the JDK 8u361 both runners name, and the JVM's own "Terminating due to java.lang.OutOfMemoryError" goes to the captured output, not through the `System.err` the application swallows (the coordinator's `r14-oom.log`, at `-Xmx8m`, shows the branch reached).  The hang's `continue` counts a hung class once; a class that hung after printing failing results is reported as hung without the failure lines, and still counts.

---

### RLV13-D8 - The message pass, apart from RLV13-C4, C5 and C6

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RLV13-C4, RLV13-C5 and RLV13-C6. |
| **Grade** | D |
| **Names** | 2d8726a6, RLA3-B1 |
| **Where** | `src/org/traincontrol/resources/messages*.properties`, the 33 keys `2d8726a6` changes and the two `fa641a6e` adds |
| **Needs execution** | done - each key decoded old and new in eight bundles and compared by a script; `core.testMessageBundles` and `regression.testEveryMessageKeyIsAskedFor` green on the archive |

The same 33 keys change in every bundle and no other; every placeholder of the old value is in the new one and in every translation; every bundle stays ASCII with `\uXXXX` escapes and has no straight apostrophe (French, Italian and Dutch keep `’`); every quoted label the old text carried is carried unchanged.  Read side by side, 30 of the 33 keep what a user needs to decide or recover: the remedies, the menu names, the numbers and the "nothing is lost" and "autonomy will retry" reassurances stay; what went is restatement ("A locomotive can only be in one place", "It is a way of drawing ..."), and the translations say what the English says.  Corrections the pass made on the way: the French scissors-crossing message's Italian word; Spanish `errorPathMisconfiguredDialog` from *tú* to the bundle's *usted* (`errorLocomotivePositionAmbiguous` beside it keeps *tú*, as before); Italian `errorHandDispatchFailed` and `warnPageRenumbered` from *Lei* to the bundle's *tu*; Italian `checkCopyReachesNothing`'s missing apostrophe (*nessun’altra*).  Of the in-scope messages left over 150 characters, the longest read as the commit says - what happened and what to do - with small repetition left in a few (`hintFacingOnlyOne` says "only one way" twice); not graded.

---

### RLV13-D9 - Baseline

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | cd19e9ca, fa641a6e, 2d8726a6 |
| **Where** | the method |
| **Needs execution** | done |

Thirteen targets on the archive, one JVM each, 99 tests, no failures, no skips, none HUNG: `core.testATrainIsDispatchedOnce` 15, `core.testTheArrivalHonoursTheAnswer` 12, `core.testAnAnsweredZeroIsNotMissing` 6, `core.testMessageBundles` 16, `regression.testTheRefusalsAreAskedAtTheDoors` 6, `regression.testErrorsStopTheSetupRunning` 8, `ui.testNonAtomicRoutesNeedTheirLengths` 10, `regression.testNothingOnTheEventThreadTakesTheRailwaysMonitor` 5, `regression.testTheRecordsCountTheStore` 3, `regression.testEveryMessageKeyIsAskedFor` 2, `regression.testAHandSendIsRefusedWhileTheSetupIsBroken` 7, `regression.testNoSetupEditDuringARun` 8, and `core.testAutonomyDiagramSession.testTheModelAndTheDoorsOffTheEventThreadAskForTheRailwayOnce` 1.  The coordinator's battery on the main checkout (01:06-02:07): 316 classes green - `testTheImportDoorReadsAnOldFile` 39/39 and `testSwitchingToACentralStationLayout` 13/13 among them - and 2 with failures, which are RLV13-C1.

---

### RLV13-D10 - The records

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RLV13-C2 and RLV13-C3 (behaviour.md's new sentence says an edit waits only where a run begins between an edit and its rebuild), RLV13-C1 (the battery) and RLV13-C8. |
| **Grade** | D |
| **Names** | RLV12, RLV12-C9, RLA3-B1 |
| **Where** | `docs/reviews-2026-09-25/RLV12.md`; `findings.tsv` (19 RLV12 rows); `triage.db`; `behaviour.md:2475-2483`, `:2497-2503`, `:2541`; `open-questions.md:429-431` |
| **Needs execution** | done - queried a copy of the store |

RLV12.md as dispositioned differs from the report as written in the ten disposition cells only, and each describes what `fa641a6e`, or the store commit for C9, does, naming its mutation where one was run.  The store has 4,832 rows for 4,475 findings, as behaviour.md and open-questions.md now say; the 19 RLV12 rows are Closed (1 B, 9 C, 9 D); RLA3-B1, RLU3-C4, RLA2-C4 and RLU2-C11 are Closed with Adam's decision and `fd31f56f`, and findings.tsv carries them so.  The notes date the round 2026-09-25, the folder's date, as every row catalogued from that folder does.
