# RSA11 - Release validation, round 28: Export at rest still writes the configuration as its file last had it - each train where it stood before the run, without a timetable captured this session or the Auto tab's settings; Start Timetable pressed in autonomy's coast-down forgets the stop being carried out; the Simulate warning still comes before each door's own refusals; two records out of step (TrainControl 3.0.0)

**Status:** open

**Prefix:** RSA11

**Reviewed:** branch `autonomy-diagram-r0` at `a5675105`, read and run from `git archive a5675105` unpacked into `validate30/rsa11/a56/` (reading, the baseline); `p1/`, a copy of it with two scratch probe classes added (`test/regression/rsa11Probes.java`, headless - RSA10's H1 and H2 renamed, on RSA9's Q1 railway - and `test/regression/rsa11Window.java`, one window per method: W1, W2, W3); `m1/`, a copy with two mutations (MU1, MU2); `cs2_sample_layout` excluded from the archive and an empty folder of that name made in each copy (still empty afterwards); `LocDB.data` and `UIState.data` beside each root are copies from TC30.  Round 28 is `78c3bf59..a5675105`: Adam's Works on MT-621, MT-623 and MT-625 (`1ec8d5ff`), the fixes for RSA10 (`d924b9cc`), their records (`b26cdbc9`) and the tracker (`a5675105`).  TC30's working tree and the main checkout's were never read, apart from copying the two data files the brief names and hashing the real ones; git was used only as `git -C tc-30 log / show / diff / archive`.  2026-09-30.

## Method

**Round 28, read whole:** the brief; `validate30/RSA10.md` and its dispositions at the commit (`docs/reviews-2026-09-25/RSA10.md`); the round's source diff (`AutonomyViewerPanel.initialize`, `duplicate`, `rename`; `TrainControlUI.captureRunningLayout` made package-visible, `refusedToSendATrain`, `declinedToRunUnsimulated`, Start Timetable's and Start Autonomy's handlers, `aTrainNotAtItsStart`), the test diff (five claims), the docs diff (behaviour.md 8, open-questions.md, MT-626 to MT-630, findings.tsv) and each changed method in its context.  **Every door that writes a configuration or the setup, and every reader of the configuration the next start resumes:** the Autonomy menu (`AutonomyMenu.buildItems`, `manageMenu`, `pagesMenu`, `deleteEverything`), `AutonomyViewerPanel` (`selected`, `refresh`, `load`, `loadPrepared`, `revert`, `loadActive`, `loadAfterImport`, `initialize`, `importConfiguration`, `importLegacyGraph`, `activateTheConfigurationNamed`, `exportConfiguration`, `duplicate`, `rename`, `delete`, `save`), the store's `createConfiguration`, `importConfiguration`, `forgetConfiguration`, `renameConfiguration`, `deleteConfiguration`, `setActiveConfiguration`, `exportBundle`, `AutonomySession.initialize` and `capture`; every writer of `setActiveConfiguration`; the window's folds (`captureRunningLayout`, the exit's in `saveState`, `keepWhereTheTrainsStand`, `writeTheTurnsOwed`) and the flag they skip on (`setupEditDeclinedDuringRun`, set only in `rebuildRunningLayoutFromSetup`); the Auto tab's setting handlers and `isAutoLayoutRunning`; `applyTrainLength`, `GraphLocAssign.commitAndRecord` and its two callers; the Layout menu's handlers that rename, delete, duplicate, switch or download a layout.  **Every reader of the Graceful Stop flag:** its two setters and three clears, Start Timetable's dialog, Return Home's dialog, `isGracefulStopPending` and the strip (`AutonomyOverlayToggle.runButtonFor`, `syncRun` and what calls it); which buttons are enabled during each kind of run (`executeTimetable`, `startAutonomy`, `gracefulStop` - every `setEnabled`), `requestStartAutonomy` and the station right-click.  **Every door that sends trains, and the order of its questions:** Start Autonomy (button, strip, right-click), Start Timetable, Return Home (button, both right-clicks), the two hand sends (`sendATrainByHand` from the Auto tab and the diagram), the gate (`whyNoTrainMayBeSent`, `refusedToSendATrain`), `prepareAutonomyReload`.  **The timetable's doors:** `executeTimetableInternal` (the stop branch and the waits before it), `getUnfinishedTimetablePathIndex`, `resetTimetable`, `restartTimetable`, `deleteTimetableEntry`, `updateTimetableDelay`, `clearTimetable`, `setTimetable`, `loadReturnToHomeTimetable` and Return Home's hand-back, `TimetablePath`'s serialisation, `reloadActiveDiagramConfiguration`.  **What RSA10 did not reach:** Rename and Add Configuration during a run, and Return Home with Simulate unticked - by running (W1, W2); the track editor's Ctrl+Z through `LayoutEditor` - by reading (`undo`, `redo`, `restoreCaptions`); `HomeStaging` - its door and the installation and hand-back of its plan only.  **Not reached:** `HomeStaging`'s search (W1's Return Home found no plan on the frozen railway with four pages out, which I did not look into); `CS2File`; the store's reconciliation past what `save` asks; `AutonomyEditorPanel` beyond its run guard (`refusedWhileRunning`, `item`) and the locomotive dialog's door; the hand sends with Simulate unticked by running (by reading and MU2 only); the round's own mutations under their names (MA1d/r/i/f/a, MG1, MG2, MC2, MW4) - my W1 to W3 drive the doors the round's claims call directly, and MU1/MU2 test what its claims leave unpinned.  Every finding was searched for in `docs/manual-tests/findings.tsv` (5,046 rows, 4,689 refs, at the commit) before it was written; none is catalogued (B1 is RSA10-A1's at-rest half at the sibling door the round did not sweep; C1 is RSA10-B1's fix placed before its door's own refusal; C2 is the half of RSA10-C1 its direction asked for and the round did not build).

**Runs.**  Every JVM through the archive's own `docs/tools/one.sh`, `TC_SCRATCH` a Windows-form folder per job under `rsa11/scratch`, started by `rsa11/runjob.sh` (RSA10's, from `queue1.sh` and `queue2.sh`) only once no `java.exe`, `javaw.exe` or `javac.exe` and no battery lock had been seen for two checks 20 s apart, each deciding from its own log; every job ran at its first attempt.  The coordinator's battery (lock pid 21560) held the machine from before my start (21:56) until about 23:09; Adam's TrainControl from the main checkout's build (pid 9712) was open from 22:37 until before 23:09; my JVMs ran 23:09:25-23:14:57, one at a time.  No job hung, ran out of memory, failed to compile or tripped the layout guard.

- **Baseline** (a56): the round's five claims, with `testStartTimetableAsksOnlyEntriesTheRailwayCanRun` - `testStartTimetableAsksNothingPastTheEntryTheRunStopsAt` (headless), `testTheConfigurationDoorsWaitForAutonomyToStop`, `testANewConfigurationAfterARunCopiesWhereTheTrainsAre`, `testStartAutonomyForgetsAnEarlierGracefulStop`, `testATimetableStoppedAtAnEntryItCannotRunSaysWhy`, `testStartingUnsimulatedInASimulationWarnsFirst` (windows): 7 run, 0 failures.
- **Probes** (p1).  They assert nothing; each writes what it saw, preconditions first, to `rsa11-out/`.  `regression.rsa11Probes` (2 run, 0 failures): **H1**, RSA9-B1's railway run twice; **H2**, Start Timetable's check against the stop, the cases RSA10 refused and a control.  `regression.rsa11Window` (1 run each, 0 failures), each on a sandbox copy of the frozen railway opened as the round's window claims open it, in simulation: **W1**, Adam's question - 1 - Main 15,5's link off and an entry for `EN57-947` on 2 - Bottom stored, as round 27's claim sets it; autonomy run A through Start's button with Graceful Stop and then Execute Timetable pressed in the coast-down; autonomy run B with Graceful Stop alone; 2 - Bottom ticked out through the Autonomy menu's own item; a timetable of `75 407 DB` along a path of its own, the entry through 2 - Bottom 3 s later, and `2-8-4 3505 SP`'s entry from BottomMainB with the train moved to BottomMainC; Start Timetable's button pressed three times, with Duplicate, Rename and Add a Configuration fired from Autonomy > Manage Configurations during the first run; Return Home; 2 - Bottom ticked back in from the menu and Start Timetable pressed; Restart from the timetable's right-click; Duplicate at rest from the menu, the exit's fold, a session opened over the folder as the next start opens one, and the copy chosen from Autonomy > Configuration.  **W2**, Simulate unticked: Start Timetable with no entries, Return Home and Start Timetable with autonomy busy (Return Home's own flag, as the round's claim fakes a run), Start Timetable with the power off - the warning answered Yes, so each door goes on to its own answer.  **W3**, at rest: a train moved on the railway (as the round's claim moves one), an entry added to the railway's timetable (as capture adds one), the maximum delay changed on the railway (as the Auto tab's slider writes it); Export from Autonomy > Configuration (Main) > Export, its file chooser answered with a file under `rsa11-out/`; then the exit's fold as the control.
- **Mutations** (m1), run against the round's Simulate claim: **MU1** the warning asked before the gate's refusals, **MU2** a hand send asking the gate without the warning, together - **green** (1 run, 0 failures): the claim pins neither the order the disposition states nor the hand sends (RSA11-C2).

**Nothing written** outside `validate30/rsa11/` and this file, apart from what the runner and the tests' own sandbox make and remove (each run's data copies and preference node under `one.sh`, the frozen railway's sandbox copy under `LayoutSandbox`); the probes' setup folders are under `rsa11/scratch/probe-tmp`.  Each run read copies of the data files; the working copies' `LocDB.data` and `UIState.data` in every copy hash the same as the TC30 copies they came from (`7a0737ae...`, `a64608f1...`), and nothing called `saveState` (W1 and W3 called `captureRunningLayout`, which writes the sandbox's autonomy files; W3's export went to `p1/rsa11-out/export-W3.json`).  **The real files:** `C:/Users/adamo`'s `LocDB.data` and `UIState.data`, and the main checkout's `UIState.data`, are unchanged byte for byte from 21:56 (`6ef56939...`, `9e32894e...`, `b80baf8d...`); **the main checkout's `LocDB.data` changed** (`5ec6fae9...` -> `3518bb3d...`), written at 22:53:52 together with its `UIState.data` (rewritten identical) while Adam's TrainControl from the main checkout (pid 9712) was open - 16 minutes before my first JVM (23:09:25) - and unchanged since; every JVM of mine read copies under its own data folder.  No git state changed; no commit.  Nothing of mine is running: the queues, the job runners and the monitors have ended, no `java.exe`, `javaw.exe` or `javac.exe` of mine is running, and the battery lock is gone (a `java.exe` started at 23:17:45, pid 24552, `-Xmx768m`, after my last job ended at 23:14:57, is not mine).

**Counts:** 0 A, 1 B, 3 C, 7 D.

**The review has not converged: one B.**  **Adam's question - is the timetable behaviour reliable now? - yes, at every door I drove** (RSA11-D4): with a real Graceful Stop of an earlier autonomy run and 2 - Bottom ticked out through the Autonomy menu's own item, Start Timetable's button ran the first entry, stopped at the entry through the page left out and said which and why, three times, never asking where a train past the stop stands; Return Home handed the timetable back with its progress; ticking the page back in kept where the run had stopped; Restart, the fold, the next start and a copy all kept the three entries.  Round 28's four fixes are right where they act (D1 to D3, D5).  **What remains:** Export, the sibling of the door round 28 mended for RSA10-A1, still writes the configuration as its file last had it - after a run each train where it stood before it, and without a timetable captured this session or the Auto tab's settings - though its own refusal tells the operator to stop autonomy and export again (B1, older); Start Timetable pressed in autonomy's coast-down forgets the stop being carried out and the strip's greyed Graceful Stop goes (C1, new); the Simulate warning is still asked before each door's own refusals, which behaviour.md says it follows (C2); two records out of step (C3).

---

### RSA11-B1 - Export at rest writes the configuration as its file last had it: after a run each train where it stood before the run, without a timetable entry captured this session, and with the Auto tab's settings as they were at the load - though its refusal says "Stop autonomy and export again", and round 28 made Duplicate fold first for exactly this

| | |
|---|---|
| **Disposition** | Fixed - Export folds the running railway into the configuration before it writes it (AutonomyViewerPanel.exportConfiguration, captureRunningLayout), as New Configuration does since RSA10-A1: the file has the trains where the run left them, and the settings and timetable as they now run.  claims and fix 83ba2fca; mutation MX1 red.  A window claim moves a train and changes the pace on the railway alone, exports through the door's own chooser, and reads the file. |
| **Grade** | B (Adam, 2026-09-30: a timetable entry lost is graded B).  **How:** what a run, the Auto tab and timetable capture change lives in the running layout until a fold writes it into the configuration - the exit, a load, the editor doors, an import into the one running, and since round 28 Duplicate (*"its file holds where they stood before the last run, and the copy took that"*, `AutonomyViewerPanel.duplicate`).  Stopping does not fold (`captureRunningLayout`'s own note).  `exportConfiguration` writes `exportBundle(selected())`, a copy of the configuration's JSON as the store holds it, with no fold.  So an export made after a run - the only kind there is since round 27 refuses one during it, saying *"Stop autonomy and export again"* - records each train where it stood before the run, leaves out every entry the timetable captured this session (`addTimetableEntry` appends to the railway alone), and carries the pace and other Auto tab settings as they were at the last load.  Brought back by Import - as a backup, or on another machine - the captured entries are gone, and a configuration chosen from it stands its trains where they were before the run.  Nothing says so.  **Reach:** Export after any run, capture or Auto tab change since the last load, every session.  **Older** (Export has never folded); exposed by round 28, whose fix for RSA10-A1 folds first at the sibling door. |
| **Names** | RSA10-A1, MT-601, MT-625, MT-627, RSA4-C5, OB-183, behaviour.md 8 |
| **Where** | `AutonomyViewerPanel.java:1539-1574` (`exportConfiguration`: `exportBundle(selected())`, no fold), against `:1595-1597` (Duplicate's fold, round 28); `AutonomyCompanionStore.java:2070-2083` (`exportBundle`, the stored JSON); `TrainControlUI.java:3185-3244` (`captureRunningLayout`), `:27597-27640` (the Auto tab's handlers write the railway alone); `Layout.java:925-936` (`addTimetableEntry`); `messages.properties:1544` (*"Stop autonomy and export again"*) |
| **Needs execution** | done - **W3** (`rsa11W3ExportAtRest`): at rest, `EN57-203` moved on the railway to 1 - Main:20,14 (the setup recording it on 4,5), the railway's timetable 1 -> 2 entries (the setup's 1), the maximum delay 2 -> 7 (the setup's 2); Export through Autonomy > Configuration (Main) > Export, its chooser answered: **the file has `EN57-203` on 1 - Main:4,5, a timetable of 1 entry, a maximum delay of 2**.  Control, the exit's fold straight after: the setup then records 20,14, 2 entries, 7. |

**Direction:** fold the running layout into the configuration before Export writes it, as Duplicate now does at rest (`captureRunningLayout`, which keeps its own rule about a declined setup edit); a claim that exports after a train has moved, an entry has been captured and a setting changed, and reads the file.

---

### RSA11-C1 - Start Timetable's press clears the Graceful Stop flag before its own busy refusal: pressed in autonomy's coast-down - Execute Timetable stays enabled while autonomy runs - it is refused, and the stop being carried out is forgotten, so the diagram strip's greyed Graceful Stop goes

| | |
|---|---|
| **Disposition** | Fixed - Start Timetable lowers the Graceful Stop flag once its busy refusal has passed, in the posted task, as Return Home lowers its own: a press while the trains finish a Graceful Stop is refused and the stop stays pending.  claims and fix 83ba2fca; mutations MG3 red, MG4 red. |
| **Grade** | C (what the strip shows for a few seconds; nothing moves differently).  **How:** round 28 clears `gracefulStopRequested` at Start Timetable's press once the gate has passed (RSA10-B1), and the busy refusal comes after it, in the posted task.  Start Autonomy never greys Execute Timetable, so during autonomy's coast-down after Graceful Stop the button is live; a press is refused (*"Please wait for all active locomotives to stop."*), but the flag is already down, so `isGracefulStopPending` reads false while the trains are still finishing and the strip, which shows a greyed Graceful Stop for exactly that window (OB-143, Adam: *"make it get greyed out and then replaced"*), decides HIDDEN at its next sync.  Return Home, which RSA10-B1's direction and the round's own comment cite as the model, clears after its busy refusal.  Start Autonomy's clear also precedes its worker's refusals, but its button stays greyed until the coast-down ends, the strip mirrors that, and the station right-click asks `isAutonomyBusy`, so no press reaches it then.  **Reach:** Execute Timetable pressed after Graceful Stop while autonomy's trains are still finishing.  **New in round 28.** |
| **Names** | RSA10-B1, OB-143, UXR-B7 |
| **Where** | `TrainControlUI.java:27672-27687` (the gate, the clear at 27676, the posted busy refusal at 27682), against `:24896-24935` (Return Home: refusal, then the clear); `:25178-25181` (`isGracefulStopPending`); `:28058-28111` (Graceful Stop: Start comes back only when the trains have stopped); `AutonomyOverlayToggle.java:268-281` (`runButtonFor`), `:317-320` |
| **Needs execution** | done - **W1**, autonomy run A: Graceful Stop pressed with a train under way - *flag true, busy true, pending true, runButtonFor STOP_PENDING, the strip mirrors Graceful Stop*; Execute Timetable (enabled) pressed: refused with *"Please wait for all active locomotives to stop."*, and **flag false, busy true, pending false, runButtonFor HIDDEN, the strip mirrors nothing** - so 1.5 s later and until the coast-down ended 4 s on.  Run B, Graceful Stop alone: pending and STOP_PENDING throughout, flag still true afterwards. |

**Direction:** clear the flag in the posted task once its busy refusal has passed (as Return Home clears after its own).

---

### RSA11-C2 - The Simulate warning is still asked before each door's own refusals - a railway already busy, an empty timetable, a train not at its start, no locomotives, no plan home, a path the train cannot take - while behaviour.md says it is asked once they have passed; the round's claim pins neither that nor the hand sends

| | |
|---|---|
| **Disposition** | Fixed in the records and the claims - behaviour.md 8 says the warning is asked once the send gate's own refusals have passed, before each door's own, as built; the warning's window claim now drives a hand send (asked) and a press the gate refuses, the power off (not asked).  claims and fix 83ba2fca; mutations MU1 red, MU2 red. |
| **Grade** | C (a simulation only).  **How:** round 28 moved `declinedToRunUnsimulated` into `refusedToSendATrain`, after the gate's own three refusals (the editor, the setup, the power) - so a press the gate refuses no longer asks, and Return Home and the hand sends ask now.  Each door's own refusals still come after it: Start Timetable's busy, empty-timetable and train-not-at-its-start refusals; Start Autonomy's no-locomotives, busy and invalid-layout ones (on its worker); Return Home's busy refusal and an impossible plan; a hand send's `whyThisPathCannotBeTaken`.  So a press that will be refused first asks *"Start anyway?"* - RSA10-C1's second half, whose direction was *"so every door that sends a train asks it once and a refused press does not"*.  behaviour.md 8 states the rule as built otherwise: *"every door that sends trains ... asks, once its own refusals have passed"*.  The one claim presses Start, Start Timetable and Return Home with the gate passing and answers No; no claim has the gate refusing, or a hand send. |
| **Names** | RSA10-C1, MT-621, MT-622, MT-630, behaviour.md 8 |
| **Where** | `TrainControlUI.java:6580-6599` (`refusedToSendATrain`); `:27672` then `:27682`, `:27689`, `:27749` (Start Timetable); `:27874` then `:27960`, `:27969` (Start); `:24892` then `:24896`, `:24993` (Return Home); `:6672` then `:6674` (`sendATrainByHand`); `docs/reference/behaviour.md:2515-2519`; `test/regression/testNoSetupEditDuringARun.java:2612-2727` |
| **Needs execution** | done - **W2** (Simulate unticked, the warning answered Yes): **(a)** Start Timetable with no entries: the warning, then *"There are no timetable entries..."*; **(b)** Return Home with autonomy busy: the warning, then *"Trains are still moving..."*; **(c)** Start Timetable with autonomy busy: the warning, then *"Please wait for all active locomotives to stop."*; **(d)** the power off: the gate's refusal alone, no warning - the half round 28 built.  **MU1 + MU2** (the warning before the gate's refusals; no warning at a hand send): the round's claim green. |

**Direction:** ask it in each door after that door's own refusals - or say in behaviour.md that it follows the gate's only; a claim with a press the gate refuses, and one with a hand send.

---

### RSA11-C3 - Two records out of step: behaviour.md 8's colon now introduces the import's reasons after round 28's New Configuration sentence, and MT-626 sends the operator to an "Autonomy > Add a Configuration..." item the menu does not have while a configuration is loaded

| | |
|---|---|
| **Disposition** | Fixed in the records and the tracker - behaviour.md 8's import paragraph gives the import its own colon again, with New Configuration, Rename, Add and Export after it; MT-626 has a comment that Add a Configuration is under Manage Configurations while a configuration is loaded (entries are append-only).  83ba2fca. |
| **Grade** | C.  **How:** behaviour.md 8 read *"...and every export, before it asks where (... MT-601 ...):"* followed by why imports are refused (*"into the configuration in use the reload's capture took back what the import had just brought..."*); round 28 inserted the New Configuration, Rename and Add sentences and *"At rest, New Configuration folds ... (RSA10-A1, as RLD-C3 for an import):"* before that colon, so the import's reasons now read as New Configuration's.  MT-626's step 1 ends *"then **Autonomy > Add a Configuration...**"*: with any configuration, which a running autonomy has, Add a Configuration is inside Manage Configurations (`manageMenu`), where W1 fired it; it stands on the top level only when there are none (MT entries are append-only, so a comment, not an edit). |
| **Names** | RSA10-A1, MT-626 |
| **Where** | `docs/reference/behaviour.md:2549-2554`; `docs/manual-tests/tests.md:30317-30335` (MT-626); `AutonomyMenu.java:300-314`, `:598` |
| **Needs execution** | by reading; W1 found the item under Autonomy > Manage Configurations |

---

### RSA11-D1 - Round 28's configuration doors: Duplicate, Rename and Add refused during a real run through the Autonomy menu; Duplicate at rest folds first, the configuration running stays the one the next start resumes, and the copy chosen keeps the train where it is

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RSA11-B1 (Export, the sibling not swept) and RSA11-C3 (MT-626's path). |
| **Grade** | D |
| **Names** | RSA10-A1, MT-626, MT-627, RLD-C3 |
| **Where** | `AutonomyViewerPanel.java:946-1023`, `:1576-1665`; `TrainControlUI.java:3185-3244`; `AutonomyCompanionStore.java:1967-1970`, `:2535-2553`, `:2611-2736` |
| **Needs execution** | done - **W1**, during the first timetable run (busy true), Duplicate, Rename and Add a Configuration fired from Autonomy > Manage Configurations: each said *"Cannot edit auto layout while running."* and nothing else; store active Main -> Main, names [Main] -> [Main].  At rest after the runs, Duplicate from the menu: store active Main, running Main, `75 407 DB` on 1 - Main:21,6 on the railway, in Main and in the copy, the copy's timetable the three entries; the exit's fold and a session over the folder: **resumes Main, `75 407 DB` on 21,6**; the copy chosen from Autonomy > Configuration: the train still on 21,6, the three entries.  The round's two claims green.  By reading: every writer of the active configuration now leaves the one running chosen while one runs (the copy is chosen only where none does; Add's own load or its revert decides; Rename follows the running one; Delete of the running one unloads it); the fold is skipped where a declined setup edit waits, as the exit's is, and the copy then takes the file's record - consistent with what the exit keeps in that state. |

---

### RSA11-D2 - The Graceful Stop of an earlier run no longer silences the stop: after a real Graceful Stop of an autonomy run, a timetable that stops at an entry through a page left out says which and why

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RSA11-C1 (Start Timetable's clear before its busy refusal). |
| **Grade** | D |
| **Names** | RSA10-B1, MT-628, MT-624 |
| **Where** | `TrainControlUI.java:442`, `:4421`, `:24935`, `:25065`, `:25178-25181`, `:27676`, `:27827`, `:27877`, `:28060` |
| **Needs execution** | done - **W1**: autonomy run B, Graceful Stop through its button; after the coast-down the flag still `true`; 2 - Bottom ticked out; Start Timetable's button: flag `false` after the press, and the run stopped at entry 2 with *"The timetable stopped at entry 2.  Disallowed because the route passes through ParkingTrack11, which autonomy does not have now - a page left out, or a link switched off?"*.  The round's two claims green.  By reading: the flag's three readers - Start Timetable's dialog, Return Home's dialog, the strip - and its setters; a reload's Yes still silences the run it stops; Start Autonomy's early clear is unreachable during a coast-down (C1). |

---

### RSA11-D3 - Start Timetable's check asks nothing from the first entry the run will stop at; where that is the first entry, the run starts and stops there at once, saying why

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RSA10-B2, RSA9-B1, MT-629 |
| **Where** | `TrainControlUI.java:30071-30095`; `Layout.java:6835-7212` (the stop branch 6938-6957) |
| **Needs execution** | done - **H1** (RSA9-B1's railway): check `null`; run 1 stopped at entry 2 after 511 ms with the mover's journey finished; **check `null` again**, run 2 stopped at entry 2 after 7 ms (RSA10 read *"must be moved to Delta"* here).  **H2**: A, null (RSA10: *"must be moved to Iota"*), run stopped at entry 2; C, null (RSA10: *"must be moved to Delta"*), run stopped at entry 1 after 0 ms; the control unchanged (the third's entry before the stop asked, and run).  **W1** runs 2 and 3: check `null` with `2-8-4 3505 SP` away from its start past the stop; each press stopped at entry 2 and said so.  By reading: the check breaks at the predicate the run stops on (`isRunnable`), from the same clamped resume index. |

---

### RSA11-D4 - Adam's question: the timetable at every door, with a page ticked out through the Autonomy menu's own item

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RSA11-B1 (an export leaves out a timetable captured this session). |
| **Grade** | D |
| **Names** | MT-624, MT-628, MT-629, RSA9-B1, RSA10-B1, RSA10-B2 |
| **Where** | `TrainControlUI.java:27660-27854`, `:24887-25128`, `:28215-28255`, `:25822-25834`; `AutonomyMenu.java:741-823`; `Layout.java:6689-6718`, `:6835-7212`, `:12286-12297`, `:12418-12472`; `TimetablePath.java:228-313` |
| **Needs execution** | done - **W1**, in order: 2 - Bottom ticked out from the menu (the entry kept, unrunnable); **Start Timetable** x2: entry 1 run, the stop at entry 2 with its reason, unfinished index 1, the third entry untouched; **Return Home** (*"No way to arrange this was found..."*): the timetable handed back whole, its stamp and index kept, not sequential; **Start Timetable** after it: the same stop and dialog; **2 - Bottom ticked back in** from the menu: all three runnable, **unfinished index still 1**, and Start Timetable then asks `EN57-947` to stand at ParkingTrack11 - its entry's start, where the fixture never placed it, which is right; **Restart** from the right-click, Yes: index 0, the check asks the first entry's train; **Duplicate**, the exit's fold, the next start and the copy chosen: three entries each (D1).  By reading: delete, delay and Clear are refused while busy; the fold writes each entry's stamp (`executionTime`), so a tick back in keeps where a run stopped; Return Home's borrowed list is the same entries. |

---

### RSA11-D5 - The Simulate warning in the one gate: a press the gate refuses no longer asks, and Return Home and the hand sends do

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RSA11-C2 (each door's own refusals still after it). |
| **Grade** | D |
| **Names** | RSA10-C1, MT-630 |
| **Where** | `TrainControlUI.java:6529-6599`, `:6667-6700`, `:24887-24892`, `:27672`, `:27874` |
| **Needs execution** | done - **W2** (d): power off, the gate's refusal only; (b) Return Home asks.  The claim green (it presses Return Home now).  By reading: every caller of `refusedToSendATrain` is on the event thread, so none skips the warning by the `isEventDispatchThread` clause; declined, each door returns before it greys anything or sets Return Home's flags; unattended runs are not asked. |

---

### RSA11-D6 - The round's claims, dispositions and records

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RSA11-C2 (the Simulate claim's gaps) and RSA11-C3. |
| **Grade** | D |
| **Names** | RSA10-A1, RSA10-B1, RSA10-B2, RSA10-C1, MT-626 to MT-630 |
| **Where** | `test/regression/testNoSetupEditDuringARun.java:2601-2727`, `:2830-2950`, `:3121-3473`; `test/regression/testAnEditedPlacementSurvivesTheRebuild.java:1455-1522` (`testStartTimetableAsksNothingPastTheEntryTheRunStopsAt`); `docs/reviews-2026-09-25/RSA10.md`; `docs/reference/behaviour.md:2504-2519`, `:2549-2554`; `docs/manual-tests/tests.md` MT-626 to MT-630; `findings.tsv` (RSA10 catalogued) |
| **Needs execution** | done - the seven claims green at the commit.  By reading: each disposition names claims that exist and test its door - the configuration doors' claim calls the panel's methods the menu items call, with a run faked by Return Home's flag (W1 drives the menu during a real run); the Duplicate claim moves a train on the railway alone (W1 after real runs); the flag claims set it by reflection (W1 presses Graceful Stop); the check's claim asks the method, not the button (W1, H1 press again).  behaviour.md 8's new sentences on the stop's dialog and the check match the code; RSA10's ten rows are in the catalogue. |

---

### RSA11-D7 - The track editor's Ctrl+Z puts the setup back through the same door the claim drives

| | |
|---|---|
| **Disposition** | Checked - clean, by reading. |
| **Grade** | D |
| **Names** | RSA9-C1, RSA10-D3, LE-A3 |
| **Where** | `LayoutEditor.java:5454-5592` (`undo`, `redo`: the diagram first, then `restoreCaptions`), `:408` (`autonomy.restorePage`); `AutonomySession.restorePage`, `putBack` |
| **Needs execution** | by reading - not run through `LayoutEditor`: both undo and redo restore the page's tiles and then the setup through `restoreCaptions` -> `AutonomySession.restorePage`, the method RSA9-C1's claim and RSA10-D3's mutation exercise; the caption stack is trimmed to the tile stack from its oldest end, so a snapshot is never paired with another step's. |
