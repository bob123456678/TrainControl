# RSA9 - Release validation, rounds 24 and 25: a refused timetable entry leaves its train where it stands, so that train's next entry is retried for three minutes and then the whole run is stopped, "the track it needs never became free"; the editor's Cancel after a station name moved to another station rebinds the timetable to the other stations; Ctrl+Z puts the stored roads back by position into a rebuilt map, and swaps two trains' roads; a round 24 configuration's entries set aside come back out of order where a run was recorded twice (TrainControl 3.0.0)

**Status:** open

**Prefix:** RSA9

**Reviewed:** branch `autonomy-diagram-r0` at `809a03e7`, read and run from `git archive 809a03e7` unpacked into `validate30/rsa9b/a809/` (reading, the baseline), a copy of it, `validate30/rsa9b/p1/`, with two scratch probe classes added (`test/regression/rsa9Probes.java`, headless, and `test/regression/rsa9Window.java`, one window; neither in the battery), two mutation copies (`m1/`, `m2/`), and an archive of round 24 (`git archive 45b52363` into `a24/`) with one writer class (`test/regression/rsa9Round24Writer.java`) that saves a configuration as a round 24 build saves it, for round 25 to read; `cs2_sample_layout` excluded from every archive and an empty folder of that name made; `LocDB.data` and `UIState.data` beside each root are copies from TC30.  Rounds 24 and 25 are `72c38ad6..809a03e7`: the RSA8 fixes (`889904fa`), their records and tracker (`52658912`, `900e0629`, `45b52363`, with `e479be81` and `2d15b35b` before them), Adam's simpler timetable rule and the Pages dialog deleted (`53fbf926`), the tracker (`809a03e7`).  TC30's working tree and the main checkout's were never read, apart from copying the two data files the brief names and hashing the real ones; git was used only as `git -C tc-30 log / show / diff / archive`.  The earlier attempt's folder `validate30/rsa9/` was not read or used.  2026-09-30.

## Method

**Rounds 24 and 25, read whole:** the brief; `validate30/RSA8.md` and its dispositions at the commit (`docs/reviews-2026-09-25/RSA8.md`); both rounds' source diffs (round 24: `AutonomySession` - `Remembered`, `remembered`, `putBack`, `beforeTheFirstMove`, `snapshotPage`, `restorePage`, `restoreSetup`, `moveTiles`, `renamePage`, and the aside fixes; `AutonomyCompanionStore.repairLocomotiveInTimetable`; round 25: `Layout`, `TimetablePath`, `AutonomyCompanionStore`, `AutonomySession`, `AutonomyViewerPanel`, `TrainControlUI`, the eight bundles), the docs diff (`behaviour.md` 8, `open-questions.md`), the test diffs (round 24's thirteen claims, round 25's six and the fourteen it retired) and MT-613 to MT-620, and each changed method in its context.  **Every reader of a timetable entry:** `Layout` - `addTimetableEntry` (capture), the locomotive delete's sweep, `getTimetableStartingPoint`, `executeTimetableInternal` in both modes, `toJSON` with `timetableOnLoan`, the parse at the load, `loadReturnToHomeTimetable`; `AutoLocomotiveStatus`; `TrainControlUI` - Start Timetable and `aTrainNotAtItsStart`, Return Home's borrowed timetable, `repaintTimetable`, `timetableSignature`, `timetableStop`, `stationLabel`, delete, delay, restart, Clear, `RightClickTimetableMenu`; the fold (`capture`); the carry (`carryTheNamesAcross`, `carry`, `rename`, `legsOf`) over names on a page out; every store writer of a timetable (`renameStoredPoints` through `renamePage` and `moveTiles`, `repairLocomotiveInTimetable`, `repairOnDisk`, `createConfiguration`, `importConfiguration` and `importBundle`, `load` with `bringBackTheEntriesSetAside`).  **Every door that ticks a page:** `AutonomyMenu.pagesMenu`, `AutonomyEditorPanel.setPageExcluded`, the combine door, `excludeRepeatedSensorPages` (`AutonomyOverlayToggle.setPageExcluded` is display only).  **Round 24's undo and Cancel:** `LayoutEditor` - `snapshotLayout`, `captionSnapshot`, `undo`, `redo`, `restoreCaptions`, `takeTheUndoPoint`, `leaveFor`, `arriveAt`, `mayLeave`, `discardAutonomyWork`, `undoAutonomyEdits`; the store's `snapshotPage`, `restorePage`, `moveTiles`; every reader of what the carry remembers (`pointNamedNow`, `pointNamedByName`, `carry`, `rename`, `moveTiles`).  **What RSA8 did not reach**, autonomy first: the autonomy editor's Cancel restoring directions without the refusal (RSA7's note), by reading; Return Home only where it borrows the timetable.  **Not reached:** `HomeStaging` and the Return Home planner beyond the borrowing; `CS2File`; export; the store's reconciliation past `deletePage`; `AutonomyEditorPanel` beyond its page box and its name door; the track editor's undo and Cancel driven through `LayoutEditor` itself (each probe runs the statements the door runs, as the round's own claims do); RSA9-B1 on the frozen railway (measured on a three-page railway; the window run took a timetable whose trains each start where they stand); the mutations other than MK1 and MK2, and none of round 24's.  Every finding was searched for in `docs/manual-tests/findings.tsv` (4,980 rows) before it was written; none is catalogued (RSA9-B2 builds on RSA5-A1's rule and RSA8-B2's fix, RSA9-C1 on RSA4-C4's fallback).

**Runs.**  Every JVM through the archive's own `docs/tools/one.sh`, `TC_SCRATCH` a Windows-form folder per job under `rsa9b/scratch`, started by `rsa9b/runjob.sh` (from `queue1.sh`, `queue2.sh`, `queue3.sh`) only once no `java.exe`, `javaw.exe` or `javac.exe` and no battery lock had been seen for two checks 20 s apart, each deciding from its own log.  The coordinator's battery held the machine until about 18:48, TrainControl run from NetBeans in the main checkout was open from 18:40 to 18:48, and another run held it from about 18:54 to 19:09; my jobs ran 18:49-18:54 and 19:09-19:11, one at a time.

- **Probes** (p1).  They assert nothing; each writes what it saw, preconditions first, to `p1/rsa9-out/` (the first run's copy in `rsa9b/out/run1/`).  `regression.rsa9Probes` (6 run, 0 failures, twice): **Q1**, a timetable run in simulation with a page out, and its control; **Q2**, a configuration as round 24 wrote it read by round 25, with a run recorded twice and a control; **Q2c**, the same from the file round 24's own build wrote (`a24`, `rsa9Round24Writer`, 1 run, 0 failures); **Q3**, Ctrl+Z as the editor makes it with two trains' roads, two cases and a control; **Q4**, the timetable with a page out through eleven doors as each runs its statements; **Q5**, the editor's Cancel after a station name moved to another station, with two controls.  In the first run every fixture started a leg at the west end of a row, which builds no departure, and Q1-Q5 found no path; the fixtures were moved to interior stations and run again (Q1-Q5 below are the second run).  `regression.rsa9Window` (1 run, 0 failures): **W1**, the Autonomy menu's "Pages with Autonomy Enabled" item for `2 - Bottom` clicked out and back in on a sandbox copy of the frozen railway, opened as the round's window claim opens it, with an entry on the page added; **W2**, between the two clicks, Start Timetable pressed through its own button with the page out and the railway in simulation.
- **Baseline** (a809): round 25's six claims and round 24's B2/B3 claims, `core.testAutonomyDiagramSession.testAnEntryOfAPageThatDidNotLoadIsKept` and `regression.testJavadocsAreAttached.testNoNewOrphanedJavadocs` (13 run, 0 failures); `regression.testNoSetupEditDuringARun.testAPageTickedBackInFromTheAutonomyMenuKeepsItsEntries`, the window claim on the menu (1 run, 0 failures).
- **Mutations**, two of the eight the commit record names, re-run: **MK1** (`m1`, the load drops an entry the railway cannot build, as before round 25) - 3 of 3 claims red, the window claim on the Autonomy menu among them; **MK2** (`m2`, the run does not refuse such an entry) - `testAnEntryTheRailwayCannotRunIsRefusedAndTheRunGoesOn` red ("a timetable of entries the railway cannot run did not end").

**Nothing written** outside `validate30/rsa9b/` and this file, apart from what the runner and the tests' own sandbox make and remove (each run's data copies and preference node under `one.sh`, the frozen railway's sandbox copy under `LayoutSandbox`); the probes' setup folders are under `rsa9b/scratch/probe-tmp`.  Each run read copies of the data files; the working copies' `LocDB.data` and `UIState.data` in `a809`, `p1`, `m1`, `m2` and `a24` hash the same as the TC30 copies they came from (`7a0737ae...`, `a64608f1...`), and nothing called `saveState`; Q4's locomotive rename was made in memory and renamed back.  **The real files:** `C:/Users/adamo`'s `LocDB.data` and `UIState.data`, and the main checkout's `UIState.data`, are unchanged byte for byte from 17:36 (`6ef56939...`, `9e32894e...`, `b80baf8d...`); **the main checkout's `LocDB.data` changed** (`481311d4...` -> `b005ee6e...`), written at 18:48:48 together with its `UIState.data` (rewritten identical) - 25 s before my first JVM started (18:49:13), as TrainControl run from NetBeans in the main checkout (pid 22940, `TrainControl 0 1 1`, open since 18:40) closed; every JVM of mine read copies under its own data folder.  The two nodes under `HKCU\Software\JavaSoft\Prefs\traincontrol-test-runs` (`battery-680`, `one-620`) are the ones RSA and RSA2 found - not mine, not touched.  No git state changed; no commit.  Nothing of mine is running: the queues, the job runners and every waiter and monitor have ended, no `java.exe`, `javaw.exe` or `javac.exe` of mine is left, and the battery lock is gone.  No job hung, ran out of memory or was ended by the runner.  **Still running, not mine and not stopped:** the earlier, stopped attempt's mutation queue - `validate30/rsa9/queue2.sh` (pid 7425) and its `runjob.sh` (pid 41736, waiting at 19:16 to run `mutMB5f` in `validate30/rsa9/m3`, with `mutMB6a` and `mutMB2m` after it); it ran `mutMB2u` at about 19:10.  It waits for an idle machine as mine did; stopping it is the coordinator's call.

**Counts:** 0 A, 2 B, 4 C, 6 D.

**The review has not converged: two B.**  **Adam's question - is the timetable behaviour reliable now? - for keeping the entries, yes; for running them, not yet.**  Round 25's rule holds at every door that ticks a page, builds, folds, clears, moves, undoes, cancels or renames, and across a restart and a page whose file did not load: every entry kept once each, in its order, listed on the Auto tab and run again when the page is back (RSA9-D1: Q4's eleven doors, W1 through the Autonomy menu's own item).  A run refuses an entry through a page out with its reason and goes on (D2: W2 through Start Timetable's own button).  **But a refused entry leaves its train where it stands, and when that train's next entry starts where the refused one would have taken it, the run stalls on it for three minutes, stops every train and says the track never became free** (RSA9-B1, older - every build since page exclusion ran without those entries; round 25 now promises "the run goes on").  **And the editor's Cancel after a station name moved to another station rebinds the timetable's legs to the other stations** (B2, older: the carry's square rule of round 21; round 24 put the carry's memory back at a Cancel only where a move was made).  **Round 24's undo** puts the railway's squares and the carry's memory back right (D3), but puts the stored legs back by position into points maps `restorePage` has just rebuilt, so two trains' roads can swap (C1, new in round 24).  **The fold of a round 24 configuration** puts entries set aside back after the first copy of the run they followed, so a run recorded twice comes back out of order (C2).  Two smaller: the refusal names a station on a page in play and asks whether its page is left out (C3); Clear's comment still describes the aside (C4).

---

### RSA9-B1 - A refused timetable entry leaves its train where it stands: where that train's next entry starts where the refused one would have taken it, the run retries it for three minutes, then stops every train and ends, saying the track it needs never became free - the rest do not run

| | |
|---|---|
| **Disposition** | Fixed on Adam's ruling (2026-09-30: "stop the timetable run upon encountering an invalid path, and let the user know") - the run stops at an entry the railway cannot run, gracefully, trains already under way finishing their journeys; the entry is left unrun, and Start Timetable's dialog says which and why (Layout.whyTheTimetableStopped, autolayout.ui.errorTimetableStoppedBecause).  claims and fix 95555f5f; mutations MS1 red, MS2 red, MS3 red.  The window claim presses Start Timetable on the frozen railway with the page ticked out from the Autonomy menu and reads the dialog.  behaviour.md 8 and open-questions.md say so; MT-620, which expected the run to go on, is superseded by MT-624. |
| **Grade** | B.  **How:** round 25 refuses an entry the railway cannot build when the run reaches it, stamps it and goes on (`Layout.executeTimetableInternal`, the `!ttp.isRunnable()` branch).  The train did not move.  Its next entry was recorded from where the refused one ended; `executePathInternal` refuses it - *"does not currently occupy the start of the path"* - and the entry's thread retries, which a parallel run does for `TIMETABLE_STUCK_MS` (180 s) while the loop waits for it to start, holding every later entry; then *"Timetable entry ... has been unable to run for some time, so the timetable has been stopped.  The track it needs never became free"*, `stopLocomotives`, and Start Timetable's dialog *"that move could not be made ... You can move whatever is in the way"*.  Start Timetable's check asks each train only its first entry the railway can run (`aTrainNotAtItsStart`), so it passes.  The common shape is a train that runs from the page in play onto the page left out and back to another station of it: its link must be switched off to tick the page out (MT-619 step 1), so both legs across it are refused, and the train's next entry starts at the station it would have come back to.  behaviour.md 8 says *"it is refused ... and the run goes on"*; MT-620 expects *"The other entries run"*.  **Reach:** a timetable with a page left out in which a train's entries through that page do not bring it back to the station it left.  **Older:** every build since pages could be left out ran without those entries (dropped at the load, or set aside in rounds 23-24) and stalled the same way; round 25 is the first to promise the run goes on.  Not the Return Home planner's runs, whose entries are always built. |
| **Names** | MT-620, MT-619, behaviour.md 8, SG-A5 (the skip this refusal copies) |
| **Where** | `Layout.java:6923-6945` (the refusal), `:6969-7055` (the entry's retries; `stuck` at 7029, the message at 7042), `:9212-9216` (`errorLocomotiveNotAtPathStart`), `:748` (`TIMETABLE_STUCK_MS`); `TrainControlUI.java:30014-30033` (`aTrainNotAtItsStart`), `:27701`, `:27777-27784` (the dialog); `messages.properties:230`, `:1800`; `docs/reference/behaviour.md` 8 |
| **Needs execution** | done - **Q1** (`rsa9Q1TheRunWithAPageOut`; main: Alpha, Beta, Gamma, Delta, Kappa, a link to second; second: Epsilon, Zeta, Eta; third: Theta, Iota, Lambda; `RSA9 mover`: Beta -> Gamma, Gamma -> Epsilon across the link, Epsilon -> Delta back, Delta -> Gamma; `RSA9 third`: Iota -> Lambda; the link switched off and second ticked out as MT-619 says; `TIMETABLE_STUCK_MS` 15 s for the probe): the railway lists `R, U, U, R, R`; Start Timetable's check **null**; the run: Beta -> Gamma runs, both crossings refused with the reason, then *"RSA9 mover does not currently occupy the start of the path ... Retrying"* for 15 s, *"... The track it needs never became free"*; **executeTimetable returned false after 15,620 ms; `RSA9 third` never ran** (still on Iota; unfinished index 3).  **Control**, the same with the mover's last entry starting where it stands (Gamma -> Delta): every entry run or refused, returned **true** after 1,282 ms, `RSA9 third` on Lambda. |

**Direction:** Adam's call between (a) refusing, with the reason, each later entry of a train whose refused entry would have moved it until one starts where it stands, so the rest of the timetable runs; (b) Start Timetable following each train through its entries and saying which will not start; or (c) keeping this, and saying so in behaviour.md 8 and MT-620 - with a message that names the train's place rather than the track.  A claim that runs a timetable through a page out and back to another station.

---

### RSA9-B2 - The editor's Cancel after a station's name moved to another station - two names swapped, or a freed name reused - rebinds the timetable's legs to the other stations: Cancel puts the carry's memory back only where a move was made, so its rebuild carries every leg by the names' new squares

| | |
|---|---|
| **Disposition** | Fixed - what the carry of names remembers is taken as the editor opens (beginEditSession -> asTheEditBegan), not only before the first move, and a Cancel puts it back.  claims and fix 95555f5f; mutation MB2 red.  The claim swaps two names by three renames and cancels. |
| **Grade** | B, as RSA8-B2 was for the same rebinding through a move.  **How:** since RSA5-A1 the carry keeps a name only *"still built, on the square it named"* in the builds this session has seen (`carry`); a name the build now gives another square is carried to the name its old square has now.  Inside an editor session, a name moved to another station - Beta named "Tmp", Gamma named "Beta", the first named "Gamma" - leaves the memory with each name on the other's square, and the legs, rightly, follow the squares.  Cancel (`mayLeave` -> `discardAutonomyWork` -> `restoreSetup`) puts back the setup and the snapshot's legs, which name the stations as they were - and puts back the memory only `if (beforeTheFirstMove != null)`, which only `moveTiles` sets (RSA8-B2's fix).  So its rebuild finds "Beta (eastbound)" built on Beta's square but remembered on Gamma's, and carries it to Gamma's name: every leg is moved to the other station, and the restored legs - saved by `restoreSetup` and folded on the editor's close - are written that way.  The entries are kept (round 25) and refused at every run.  **Reach:** in one autonomy editor session, a station given a name another station had in it - a swap, or a name freed and reused - and the session cancelled; a page's and a track move's Cancel are right.  **Older:** the square rule of round 21 (RSA5-A1) and every Cancel before round 24; round 24 restored the memory at a Cancel after a move and not after a rename. |
| **Names** | RSA8-B2, RSA5-A1, RSA3-B1, RSA4-D6 |
| **Where** | `AutonomySession.java:3316-3331` (`restoreSetup`: the memory put back only after a move), `:3468` (`beforeTheFirstMove` set in `moveTiles` alone), `:1005-1104` (`carry`; the rule at 1021), `:805-847` (`carryTheNamesAcross`, the memory written after each build); `LayoutEditor.java:5829`, `:6001`, `:614-629` (`discardAutonomyWork`) |
| **Needs execution** | done - **Q5** (`rsa9Q5ACancelAfterANameMovedToAnotherStation`; Alpha, Beta, Gamma, Delta; legs Beta -> Gamma, Gamma -> Delta; the snapshot as the editor opens): the names of Beta and Gamma swapped by three renames - the legs follow their squares; Cancel: **`Gamma (eastbound) -> Beta (eastbound)`, `Beta (eastbound) -> Delta`**, the railway built from them `U, U`, and the next session reads the same.  **Control 1**, Cancel with no rename: as before, `R, R`.  **Control 2**, a move first, then the swap and Cancel: the next session reads them as before. |

**Direction:** put the carry's memory back at every Cancel - take it when the editor's undo point is taken (`takeTheUndoPoint`), as `snapshotPage` now takes it for an undo - not only before the first move; and a claim that swaps two names and cancels.

---

### RSA9-C1 - Ctrl+Z puts every configuration's stored legs back by position into points maps that `restorePage` has just rebuilt in another order: two trains' roads on different pages whose squares share a bucket are swapped

| | |
|---|---|
| **Disposition** | Fixed - the undo point holds each stored leg by where it is (keyedLegsOf: a road by its square and step, the timetable by entry and leg where it has as many legs), not by position.  claims and fix 95555f5f; mutation MC1 red.  The claim uses RSA9's bucket-sharing page name. |
| **Grade** | C, as RSA4-C4 was for a road lost to the same fallback.  **How:** round 24's undo point records every configuration's legs as a list of names (`remembered` over `legsOf`: the timetable's legs, then the road of each standing train in the order of `points.keySet()`), and `restorePage` puts them back by position where the count matches (`putBack(was, true)`).  But `store.restorePage` first replaces each configuration's `points` with a new `JSONObject` - the other pages' keys first, then the page's - and a `JSONObject` is a `HashMap`, so two keys in one bucket now iterate the other way round.  The roads are then written into each other.  The next load lays each train's tail along the other train's road; where no edge of it reaches the train, the walk falls back to the fork rule (`cameFromAlong` finds nothing), as with a road dropped (RSA4-C4); the stored roads stay swapped until the trains move.  The positional put-back is not needed for the page's own roads, which the snapshot holds by key.  **Reach:** a Ctrl+Z in the track editor - after any edit, or none - with two standing trains whose roads are recorded, on the edited page and another, whose square keys share a bucket of the points map, the edited page's key inserted first.  **New in round 24** (`889904fa`). |
| **Names** | RSA8-B2, RSA8-B3, RSA4-C4, WK7-B1 |
| **Where** | `AutonomySession.java:196-229` (`remembered`), `:235-270` (`putBack`: by position at 259-268), `:879-921` (`legsOf`: roads in `keySet()` order), `:3352-3365` (`restorePage`); `AutonomyCompanionStore.java:3326-3365` (`restorePage`: `rebuilt` at 3344, other pages' keys first) |
| **Needs execution** | done - **Q3** (`rsa9Q3TheUndoPutsTheRoadsBackByPosition`; `RSA9 mover` on main 5,1 along Beta -> Gamma, `RSA9 stayer` on 5,1 of a page named `second124`, whose key shares main 5,1's bucket, along Zeta -> Eta): keys `[main:5,1, second124:5,1]`; the row moved two squares and undone as the editor does it: keys `[second124:5,1, main:5,1]`, **main 5,1's road `Zeta -> Eta`, second124 5,1's `Beta -> Gamma`**; the load: each train *"arrived along"* the other's road.  **B**, the undo point put straight back with no edit: the same.  **Control**, the page named `second` (another bucket): both roads as before. |

**Direction:** put the stored legs back by key - each configuration's timetable by position, each road by its square - or leave the roads of squares on other pages alone; and a claim with two roads.

---

### RSA9-C2 - A configuration written by a round 24 build with a page out: its entries set aside come back after the first copy of the run each followed, so a timetable with a run recorded twice is read back out of order

| | |
|---|---|
| **Disposition** | Not a defect - the list rounds 23 and 24 wrote kept no position, so a run recorded twice cannot say which copy an entry followed; the read puts it after the first, as those builds themselves did, and it is a one-time read of files from those two builds. |
| **Grade** | C (two development builds of 30 September wrote such files; B for any configuration of Adam's they saved with a page out and a repeated run).  **How:** round 24 set aside each entry of a page out with a copy of the last entry kept before it (`asideItem(entry, previous)`), one for one.  Round 25 folds them back at the read (`bringBackTheEntriesSetAside`), each after *the first* entry of the timetable that sends the same train the same way (`sameRun`), skipping only those already brought back.  With a shuttle recorded twice, each second entry goes back beside the first copy of its run.  A train then has two runs the same way in a row, and the second cannot start where it stands - RSA9-B1's stall.  The same rule was round 24's own `putBackAfter`.  Round 23 files lose such entries anyway (RSA8-B5).  An exported configuration of rounds 23-24 brought in by Import (`importConfiguration`, `importBundle`) keeps its list until the next read of the folder, and is folded then.  **Reach:** a configuration saved by a round 23 or 24 build with a page out and a run recorded more than once.  **New in round 25** (the fold; the order rule round 24's). |
| **Names** | RSA8-B5, RSA4-B2, RSA9-B1 |
| **Where** | `AutonomyCompanionStore.java:6329-6387` (`bringBackTheEntriesSetAside`), `:6390-6412` (`sameRun`), `:938` (only `load` calls it), `:2576-2590` |
| **Needs execution** | done - **Q2c** (`rsa9Q2cTheRound24FileRead`, the file round 24's build wrote: `rsa9Round24Writer` in `a24`): recorded `mover B->G, stayer Z->E, mover G->B, stayer E->Z` twice; round 24 wrote 4 kept and 4 aside; **read by round 25: `mover B->G, stayer Z->E, stayer Z->E, mover G->B, stayer E->Z, stayer E->Z, mover B->G, mover G->B`**, the list gone.  **Q2** the same as written by hand.  **Control**, each shuttle once: read in the order recorded. |

**Direction:** match each item's `after` count for count - the n-th copy of a run to the n-th - or keep the item's position among the entries kept; and fold at Import too.

---

### RSA9-C3 - The refusal names the first point of the path the railway does not have - after a link is switched off, a station of the page in play - and asks whether its page is left out

| | |
|---|---|
| **Disposition** | Fixed - the refusal's words no longer assume a page left out: "which autonomy does not have now - a page left out, or a link switched off?" (autolayout.errorPointNotOnTheRailway, eight languages).  Wording alone; no claim. |
| **Grade** | C.  **How:** `TimetablePath.fromJSON` keeps the first point of the path the railway lacks and the run says *"Disallowed because the route passes through {0}, which is not part of autonomy - is its page left out?"*.  A page is ticked out after its link is switched off (MT-619 step 1), and switching the link off changes the copies of the station beside it, on the page in play: its copy facing the link goes.  A route across the link is then refused naming that station - on the page that is in autonomy.  **Reach:** every entry crossing a link switched off.  **New in round 25.** |
| **Names** | MT-620 |
| **Where** | `TimetablePath.java:296-305`; `messages.properties:137` (and the seven translations) |
| **Needs execution** | done - Q1's log: *"Timetable route RSA9 mover from Gamma (eastbound) to Epsilon (eastbound) was not run.  Disallowed because the route passes through **Kappa (eastbound)**, which is not part of autonomy - is its page left out?"* - Kappa is on main, in play. |

**Direction:** name a point of a page out where the path has one, or say the route cannot be built without asking about its page.

---

### RSA9-C4 - Clear's comment still says it clears the entries set aside for pages out of autonomy

| | |
|---|---|
| **Disposition** | Fixed - Clear's comment says what Clear does now.  A comment; no claim. |
| **Grade** | C.  `TrainControlUI.clearTimetable`'s comment above `clearTheTimetable` reads *"with the entries it has set aside for pages out of autonomy (RSA5-C1): kept for a page's return"* - round 25 took the aside out; the method it calls says so.  **New in round 25.** |
| **Names** | RSA5-C1 |
| **Where** | `TrainControlUI.java:22990-22992` |
| **Needs execution** | by reading (`grep` of every source file for the aside) |

---

### RSA9-D1 - Round 25's keep: every entry kept, in order, at every door that ticks a page, builds, folds, clears, moves, undoes, cancels or renames, across a restart and a page not loaded - and through the Autonomy menu's own item

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RSA8-B1 to B6, RSA8-C2, RSA4-B2, MT-619 |
| **Where** | `TimetablePath.java:213-305`; `Layout.java:13950-13985`, `:12712-12720`; `AutonomySession.java:6099-6385` (`capture`), `:805-1104`; `AutonomyCompanionStore.java:2862-2930`, `:3223-3240`; `AutonomyMenu.java:741-816` |
| **Needs execution** | done - **Q4**, eleven doors, timetable `M1, S1, M2, S2, S1 again`: the menu's tick out and in; the editor's box and its close; a restart with the page out; a locomotive renamed while out; the page's file not loaded at a start; a move on the page out saved, undone and cancelled (with a name made from a square, which the store renames); Clear; an entry deleted on the Auto tab (stays deleted); the page out renamed - every entry kept once, in order, names carried where they should be, all runnable once the page is back.  **W1**, the frozen railway, the menu's own item: out - stored 2 as before, running `R, U`, the Auto tab listing both by name; in - running `R, R`. |

---

### RSA9-D2 - The run with a page out: refused with the reason and the run goes on where each train's next entry starts where it stands; Start Timetable's check; the readers of an unbuilt entry

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RSA9-B1 (a train whose refused entry would have moved it) and C3 (the reason's wording). |
| **Grade** | D |
| **Names** | MT-620 |
| **Where** | `Layout.java:6727-6740`, `:6923-6945`; `TrainControlUI.java:30014-30060`, `:24888`, `:25040-25050` |
| **Needs execution** | done - **W2**, Start Timetable pressed through its own button with `2 - Bottom` out, in simulation: the check null, the entry on the page refused with its reason, `2-8-4 3505 SP` run TopMainR2Inter -> RampDown, *"Timetable execution finished"*, no dialog.  **Q1's control.**  By reading: sequential runs (Return Home's) hold only built entries; a refused last entry ends the run as an entry's own thread does; Return Home's borrowed timetable and the fold during it carry unbuilt entries; the locomotive delete sweeps them; capture, delete, delay, restart and the table read them safely. |

---

### RSA9-D3 - Round 24: a move, Ctrl+Z and Cancel move and put back what the carry remembers and the railway's squares

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RSA9-C1 (the roads put back by position) and RSA9-B2 (a Cancel after renames). |
| **Grade** | D |
| **Names** | RSA8-B2, RSA8-B3 |
| **Where** | `AutonomySession.java:166-270`, `:3316-3365`, `:3445-3484`; `LayoutEditor.java:385-432`, `:5416-5600`, `:6030-6290` |
| **Needs execution** | done - the round's five claims green (baseline); Q4's moves on a page out saved, undone and cancelled; Q3's control.  By reading: one editor window edits one page at a time and a page or mode switch resets the session or clears the undo history, so no undo point outlives its session; redo takes its undo point at the undo. |

---

### RSA9-D4 - The rounds' claims, and two of round 25's mutations re-run

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | - |
| **Where** | `test/core/testLayoutTimetable.java`, `test/core/testAutonomyDiagramSession.java`, `test/regression/testAnEditedPlacementSurvivesTheRebuild.java`, `test/regression/testNoSetupEditDuringARun.java` |
| **Needs execution** | done - baseline 13 and 1 run, 0 failures; **MK1** 3 of 3 red (the window claim on the Autonomy menu among them, failing on the entry lost); **MK2** 1 of 1 red.  The window claim drives the menu's own item; the Start Timetable claim drives the extracted rule, and the button reads it (W2). |

---

### RSA9-D5 - The Pages dialog deleted

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RSA8-C1, RSA7-B2, MT-608, MT-609 |
| **Where** | `AutonomyViewerPanel.java:349-356`, `:425-428`; `AutonomyMenu.java:478-481` |
| **Needs execution** | by reading - nothing calls `choosePages` or `applyPages` (the build compiles), the prompt's text is still the menu's tooltip, and behaviour.md and MT-613/614 name the menu's item |

---

### RSA9-D6 - What RSA8 did not reach, by reading: the autonomy editor's Cancel (RSA7's note), and the other readers of a round 23-24 configuration

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RSA9-C2's note on Import. |
| **Grade** | D |
| **Names** | RSA7 (method), RSA8 (method) |
| **Where** | `LayoutEditor.java:614-640`; `AutonomyCompanionStore.java:1419-1430`, `:2103-2140`; `AutonomySession.java:3296-3306` |
| **Needs execution** | by reading - the autonomy editor's Cancel puts back the directions and the placements together, so a train placed inside the session on a copy the session made goes back where the setup had it, with nothing running while the editor is open; the on-disk locomotive and page repairs open a store, whose read folds the list first; the pre-edit note restored at a start is folded at the next read; Import folds at the next read (RSA9-C2). |
