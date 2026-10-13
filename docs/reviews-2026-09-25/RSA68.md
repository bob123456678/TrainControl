# RSA68 - Validation of TrainControl 3.0.1, one wave over everything since RSA67 (`d2c4704b..6cdaf746` on `dev`, at `6cdaf746`): the per-signal lock closes RSA67-C1 with no new deadlock leg and the window-monitor leg is gone; FR-126's tools, the tab sort and OB-343's per-train record hold on every door - but OB-343's record now follows a fold that the old-file import then rolls back, so after a failed import into the configuration in use the next setup gesture's rebuild throws away the timetable a run captured, the Auto tab's settings and a card's pauses (B1, new, run at both commits); FR-125 keeps a user's installation and data safe on every path read, with gaps in what it says and in what pins it (C2-C4); OB-341's per-page rule agrees with the drawing apart from corners only a hand-edited file reaches - NOT CONVERGED

**Status:** closed

**Prefix:** RSA68

**Reviewed:** branch `dev` at `6cdaf746` (*FR-125 round 2: the update counts as done when the new version says it started; one at a time*): `git log d2c4704b..6cdaf746` (29 commits), `git diff d2c4704b 6cdaf746 -- src test` and `-- Readme.md Automation.md docs/reference`.  Read from `git archive 6cdaf746` (`cs2_sample_layout` excluded) unpacked into `validate30/rsa68/`, with an empty `cs2_sample_layout` folder for one.sh's guard; the archive carries no `LocDB.data` or `UIState.data`, and none was copied.  Two controls from `git archive d2c4704b` into `rsa68/w/pre/`, and one copy of the commit with a single constant changed, `rsa68/w/scale/` (C1).  2026-10-13 01:12 to 02:05.

## Method

**Read whole:** the brief; every hunk of the source diff (`Layout`'s signal lock, `refreshOneSignal`, `configureEdge`, `configureAndLockPath`; `TrainControlUI`'s 1,224 changed lines; `AutonomySession`, `AutonomyChecks`, `AutonomyCompanionStore`, `AutonomyEditorPanel`, `AutonomyViewerPanel`, `DiagramMonitor`, `DiagramMonitorDriver`, `LayoutEditor`, `LayoutGrid`, `LayoutPopupUI`, `MarklinControlStation`, `TrainControl.java`, `Util`, the 8 bundles); `SelfUpdate.java` and `JarUpdater.java` whole; the claim diff (RSA67-C1's four forced-race claims, the window-monitor claim, OB-341's 19, OB-343's and FR-126's 20, FR-125's 17, FR-127's 4, OB-342's 2, OB-344's 1); the documentation diff.  **At the commit, for the lock:** `getActiveAccSettings`, `exitGuardsLeftBy`, `pathHeldBy`, `onlyPassing`, `refreshProtectingSignal`, `refreshAllProtectingSignals`, `throwEntryGuard`; `Point.setLocomotive`/`reserve`/`assign`; `Edge`'s synchronized methods; `MarklinAccessory.setSwitched`/`parseMessage`/`updateTiles`; `LayoutLabel.updateImage`/`setImage`; `MarklinControlStation.exec`/`receiveMessage`/`log`, `TrainControlUI.log`/`repaintSwitch` - every monitor taken under a signal's lock, and every lock held while one is waited for.  **For FR-125:** `offerTheUpdate` through `closeForTheUpdate`, `backupData`, `WindowClosed`, `isAutonomyBusy`, `theAutoTabWaits`, `sendATrainByHand`, `Layout.executePath`'s count, `Util.writeAtomically`, `build.xml`'s fat jar.  **For OB-343:** `AutonomyViewerPanel.importLegacyGraph` and `load`, `captureRunningLayout`, `rebuildRunningLayoutFromSetup`, `capture`, `captureTheRailwaysSettings`, every `restoreSetup` caller.  Four read-only helpers (no JVM, no git, no writes) read OB-341, OB-343 with FR-126, FR-125, and FR-127/OB-342/OB-344 with the documentation and bundles side by side with me; each of their candidates below was re-read at the commit, and run where a run could settle it.  Each finding was searched for in the archive's `docs/manual-tests/findings.tsv` (5,619 lines): none is there.

**Runs.**  Every JVM through the archive's own `docs/tools/one.sh`, `TC_SCRATCH` a Windows-form folder per job under `rsa68/w/scratch`, started by `rsa68/w/runjob.sh` (RSA67's runner re-pointed: waits until no java.exe/javaw.exe naming testng, TestNG, batteryRun, anyReceivePort, one-files or battery-files, no javac.exe and no battery lock have been seen in two checks 20 s apart; Adam's own TrainControl is not waited for; never kills a JVM).  The other session's test JVM was running at my first queue (01:21) and was waited out.  This machine's test JVMs see 120 dpi on a 1920 by 1200 display (`ui.rsa68Dpi`).  No Central Station was reached; nothing hung, ran out of memory or tripped the layout guard.  (One slip of my own, before anything ran: I stopped my first queued runner's shell while it waited, and its one.sh child ran the batch anyway, to completion, at 01:26 - those results are the `b0` log.)

**Claim copies** (`rsa68/w/probes/`, copied into the archive's `test/`, unchanged but for one line making locomotives by name after `init`, since the archive has no locomotive database): `regression.rsa68GuardClaims` (= `testBothProtectingSignalsAreThrown`, three), `regression.rsa68HomeLabel` (= `testTheHomeLabelIsDrawnOnce`, four), `ui.rsa68Looks` (= `testDiagramLooksRight`, one).  **Scratch probes** (assert nothing; output in `rsa68/w/out/`): **`core.rsa68Race`** - four trains leaving four platforms over and over, two pairs sharing an exit guard (one pair's first edge commanding it GREEN itself), two threads refreshing protection as fast as they can, 25 s with Atomic Routes off and 25 s on; watches ThreadMXBean for a deadlock, every journey for an end, and every hand-over for the leaving train's guard RED.  **`ui.rsa68ImportRollback`** - B1, over the frozen railway (`live-snapshot`).  **`ui.rsa68Dpi`** - the display the test JVM sees.

**Ran (at `6cdaf746`):** `regression.rsa68GuardClaims` **29 of 29** (RSA67-C1's four forced claims among them); `regression.testTheDiagramRefreshDoesNotWaitOnTheRailway` 5 of 9 with a display (`testADrivingThreadNeverWaitsForTheWindowsMonitor` green; the other four red only with *"no square on this snapshot can hold a train"* - no locomotive database); `core.testTheUpdaterSwapsTheJar` 14 of 14; `ui.testTheUpdateQuestion` 3 of 3; `core.testControlStationFaults` 22 of 22 with a display (21 + the popup skipped headless); `core.testAPausedTrainStaysPaused` 7 of 7; `core.testAutonomyDiagramMonitor` 68 of 68; `core.testAutonomyDiagramSession` 236 of 236; `core.testMessageBundles` 24 of 24; `ui.testTheActiveTickPausesATrain` 19 of 19; `regression.testControlNAsksTheMenusQuestion` 9 of 9; `regression.testTheVisibleElementsHeadingIsSpacedLikeTheOthers` 5 of 5; `regression.testAPendingTurnSurvivesTheRebuild` 13 + 1 skipped (*"no train stands at a captioned station"*); `regression.rsa68HomeLabel` **6 of 6** (the original skips all six for want of four locomotives); `ui.testTheEditorNamesItsShortcuts.testTheEditorTitlesNameTheLoadedConfiguration` 1 of 1; `ui.rsa68Looks`' three new FR-127/OB-344 claims **3 of 3** (the original skips the render claim for want of a locomotive); `core.rsa68Race`; `ui.rsa68ImportRollback`.  **At `d2c4704b` (controls):** `core.rsa68Race`, `ui.rsa68ImportRollback`.  **At `6cdaf746` with `UI_SCALE = "1.5"`:** `regression.testTheVisibleElementsHeadingIsSpacedLikeTheOthers` (C1).

**Not reached:** the real update - no updater or TrainControl was started against any jar, as the brief requires, so FR-125's process start, Windows command line, file locks, Defender and OneDrive holding files, and macOS/Linux are by reading; whether Adam's GitHub release assets carry `size` and `digest`; the natural, unforced occurrence of RSA67-C1's interleaving (the stress probe is blind to it - it saw nothing at `d2c4704b` either - so the forced claims are what closes it); the pop-up and drawn-area corners of OB-341 (C8, C9), read and not run; OB-342 on a real 150% display rather than the scale constant; the full battery (not mine to run); `testARetiredRailwaysJourneyEndsWithItsRailway` (seen once before) was not met; no mutation was run (C4's survivors are by reading).

**Nothing written** outside `validate30/rsa68/` and this file, apart from what the runner makes (the isolated preference node and data folder per run, which one.sh removes, and the sandboxes' temporary layout copies).  Nothing of mine called `saveState`.  **The real files:** `C:/Users/adamo`'s `LocDB.data` (`08d1008506efd642`) and `UIState.data` (`7be43483e1a0fc84`), and the main checkout's (`43db294ceb2850ab`, `f4a113659eb7f523`, both last written 00:35:06, before this round began), unchanged byte for byte and time stamp from 01:12 to 02:02.  The main checkout touched only through `git -C <main checkout> archive / log / diff / show` and to hash and stat the two files; `tc-30`, the `w-*` folders and `bat93` neither read nor run.  **Running:** no JVM, runner, monitor or loop of mine is left (02:02: no java.exe, javaw.exe or javac.exe on the machine, no battery lock).  Nothing was refused by the permission system.

**Counts:** 0 A, 1 B, 10 C, 10 D.

**This round has NOT CONVERGED: one B.**  **What 3.0.1 got right** (D1-D10): the signal lock is taken last, under it only short monitors no holder of it waits on, so no new deadlock leg - the forced claims pass and 50 s of four trains against 40 million unforced refreshes found no deadlock and no RED at a hand-over (D1); `repaintSwitch` only posts, and nothing else under a signal's lock or an accessory's monitor waits for the window (D2); Exit's two halves are claimed (D3); the per-train record, the tools, the sort and the grey hold on every other door, off the railway's monitor, refused during a run (D4, D5); the per-page caption rule agrees with the drawing in every ordinary case and the registry's narrower prune cannot freeze a shown label (D6); the updater never leaves a partial jar under the old name, never overwrites an old jar or the user's data, starts nothing unasked and refuses a second (D7); the titles, OB-344 and OB-342 at 100% and 125% (D8); the documentation's other lines and the bundles (D9); every claim the changes touch is green but for want of the locomotive database (D10).  **What does not hold, B:** the old-file import folds the running railway into the configuration in use and now writes that fold into the window's record of how the railway was built; when the file then fails part way, the setup is rolled back to before the fold and the record is not, so the record claims an agreement the setup no longer has, and the next setup gesture's rebuild takes nothing from the railway and rebuilds it from the older setup: a timetable entry captured since the last fold, a moved Auto tab setting and a card's pause are lost (B1, run at both commits - kept at `d2c4704b`, lost at `6cdaf746`).  **What remains, all C:** OB-342's floor still cuts Cancel and the status line at 150% (C1); FR-125 says an update that cannot start is undone, and in three ways it keeps one that does not run (C2); the update's exit fires whenever its backup ends and asks less than the Auto tab does (C3); nothing pins FR-125's wiring end to end (C4); OB-341's check and drawing part in three hand-edited corners (C5), and its texts overstate or understate the per-page rule (C6); the Readme's 3.0.1 changelog has nothing since RSA67 (C7); and three older caption corners - pop-ups, captions outside the drawn area, and the covered-caption warning (C8-C10).

---

### RSA68-B1 - New in OB-343: the old-file import's fold into the configuration in use now updates the window's record of how the railway was built, and the import's rollback puts the setup back but not the record - so after an import that fails part way, the next setup gesture's rebuild finds the railway "unchanged since it was built", takes nothing from it, and rebuilds it from the older setup: a timetable entry captured by a run since the last fold, the Auto tab's settings and a card's pauses are lost

| | |
|---|---|
| **Disposition** | Fixed (`f80f76a1`): a rollback that puts the setup back puts the window's record of how the railway was built back with it (`TrainControlUI.noteSettingsAsBuilt` / `putSettingsAsBuiltBack`, at the old-file import's rollback and the editor's Cancel, Discard and undo); claims in `ui.testTheActiveTickPausesATrain`, red on the commit reviewed. |
| **Grade** | B - a timetable lost is graded B (Adam, 30 September), and a setting the operator moved on the Auto tab goes back without a word.  The trigger is narrow - an old-format autonomy file imported into the configuration that is loaded, that fails after its fold, followed by a setup gesture from the diagram before any whole fold (opening an editor, Export, a Load, the exit) - but it is at rest with nothing mid-edit, and it is exactly the question the brief asks: a fold that no rebuild follows, undone. |
| **Names** | OB-343 (`7ceed5fd`, `ef310c09`), RSA60-B1 (the record), RSA20-B1 and RSA21 (the fold takes only what the railway changed), RLA-C3 / RLU-C1 (the import's rollback), RSA22-C1 |
| **Where** | `AutonomyViewerPanel.importLegacyGraph` (`:1362`): the snapshot `was = session().snapshotSetup()` (`:1389`), then, into the configuration running, `session().captureFromLayout(..., into, ui.settingsAsBuiltFor(...))` (`:1451`) - the two-argument fold before OB-343; `AutonomySession.capture` now sets the record to the railway's globals in place (`:6904-6909`).  Then `importLegacy` (`:1463`) and `whatALegacyImportLeaves` (`:1467`, which reads `edges`, an edge's `commands` and the timetable strictly - `AutonomySession :2284`); a `RuntimeException` from either reaches `restoreSetup(was); rebuild()` (`:1624-1625`) - the setup as before the fold, the railway as it was, the record as after the fold.  The next `TrainControlUI.rebuildRunningLayoutFromSetup` (every setup door of the diagram's right-click menu, both bulk tools, a home, a caption): `captureTheRailwaysSettings` (`:8249` -> `AutonomySession :6990`) skips every key whose railway value equals the record - all of them - and `load` rebuilds the railway from the rolled-back setup. |
| **Inputs** | configuration A loaded and at rest; since its build the railway has changed alone - a run captured a timetable entry, an Auto tab setting moved, a card paused a train; Autonomy > Import of a file `detect` calls an old graph (`points` an array) with one bad value further in (here `"edges": {}`), named A at the prompt; then any setup gesture on the diagram. |
| **Needs execution** | done - **`ui.rsa68ImportRollback`** (`w/out/import.txt`), the frozen railway, configuration Main: the railway alone given one timetable entry and a minimum delay moved by one.  CONTROL, the rebuild alone: *"railway timetable 1 (was 1 before the rebuild), minDelay 2 (was 2)"* - kept.  CASE, the failed import first: *"after the failed import: dialogs [... JSONObject["edges"] is not a JSONArray ...]; setup timetable 1, record timetable 2, setup minDelay 2, record minDelay 1; railway timetable 2, railway minDelay 1"*, then *"after the rebuild: railway timetable 1 (was 2 before the rebuild), minDelay 2 (was 1)"* - the entry and the setting gone from the railway and the setup.  **The same probe at `d2c4704b`** (`w/out/import-pre.txt`): *"record timetable 1 ... after the rebuild: railway timetable 2 (was 2 before the rebuild), minDelay 1 (was 1); setup timetable 2"* - kept.  **New** with OB-343.  No claim folds and then rolls back.  **The same shape, by reading, graded C by the 1 October ruling** (at rest while the setup is mid-edit): an in-editor rebuild the build declines (a link left unpaired) now writes its partial fold into the record of the railway it leaves running; the editor's Cancel then restores the opening snapshot, and its rebuild no longer takes a card's pause or an Auto tab setting made while the editor was open.  **One way to settle it:** take the snapshot after the fold (the fold is the railway's, not the file's, and was never the import's to undo), or snapshot the record with the setup and put both back; or fold with no record here, as before. |

---

### RSA68-C1 - OB-342's floor is in raw pixels while the editors are scaled: at 150% the smallest size still cuts off Cancel and the autonomy editor's status line by 7 or 8 pixels, in both editors

| | |
|---|---|
| **Disposition** | Fixed (`bc1e1c5a`), on Adam's word of 2026-10-13 (asked which of RSA68's Cs to fix in 3.0.1, he chose every group): the floor is never below the window's own layout minimum plus its chrome, which follows the display scale; claims at 1.0, 1.25 and 1.5. |
| **Grade** | C - a control cut short at the window's smallest size on a display scaled to 150% (common on laptops), until the window is made a few pixels taller; Adam's own display, 125%, is clear. |
| **Names** | OB-342, OB-313 (scaling), `TrainControlUI.UI_SCALE` |
| **Where** | `LayoutEditor.EXTRA_MINIMUM_HEIGHT` (`:696`, 80) and `sizeForDiagram` (`:6080`): the floor is `630 + 80 + growth` pixels whatever the scale, and its javadoc says it was *"measured at 100% display scaling"*; `TrainControlUI` sets `flatlaf.uiScale` to dpi / 96 (`:868-871`), so fonts, insets and the column grow with the display. |
| **Needs execution** | done - this machine's test JVM sees 120 dpi (`ui.rsa68Dpi`: *"dpi 120, screen 1920x1200"*), so the claims' 5 of 5 above are at 125%.  The same class in a copy of the commit with `UI_SCALE = "1.5"` (`w/scale`, nothing else changed): *"at its smallest size - 700 by 710 pixels, at diagram size 30 - the autonomy editor cuts off controls ...: [JButton 'Cancel': 17 of 25 pixels high, 'This setup will run, with warn...': 17 of 24 pixels high]"* and the track editor *"[JButton 'Cancel': 17 of 25 pixels high]"*.  Older in kind (the floor was always raw pixels); OB-342 fixed 100% and 125%.  **One way to settle it:** scale `EXTRA_MINIMUM_HEIGHT` (or the whole floor) by the UI scale, clamped to the screen as now. |

---

### RSA68-C2 - FR-125: the Readme says an update whose new version cannot start is undone; the program undoes only a version that ends before `main`'s first line, and in three ways it can leave a version that does not run under the jar's name, one of them while telling the user the old one was put back

| | |
|---|---|
| **Disposition** | Fixed (`d15536fa`), on Adam's word of 2026-10-13 (asked which of RSA68's Cs to fix in 3.0.1, he chose every group): the started marker is written where the new version is up or the user leaves it; the note says "put back" only when it is true (`ui.update.notPutBack`); the tooltip matches what is offered; the Readme says which failures are undone. |
| **Grade** | C - each way is unlikely or not reachable today, the old jar is always kept beside it, and `update.log` says what happened; but the Readme promises more, and the brief asks whether any path leaves no working TrainControl. |
| **Names** | FR-125, the 12 October rulings (started = the marker; an update into a version without the marker code - not a finding) |
| **Where** | `Readme.md:362` (*"If the new version cannot start, the update is undone"*).  (1) The marker is `TrainControl.main`'s first act (`TrainControl.java:23`), so a new version that then fails in its own start-up - `init` throwing to `error.startupFailed` and `System.exit(1)`, a data file it cannot read - has "started" and is kept, with no note (`JarUpdater.update :504-511`).  (2) A release built for a newer Java than the user's makes `javaw`'s launcher show its error box before `main`: no marker, the process alive; past `START_CAP_MS` (`:112`, 10 minutes) the NO_WORD branch keeps it (`:513-517`), the updater's own window having closed at the launch (`:502`) - not reachable while `javac.target` is 1.8.  (3) `undo` (`:746`) moves the new jar aside, then the old back, and stops at the first failure: where the first move fails for its 20 x 250 ms (the just-ended JVM or antivirus holding the jar), it returns the backup, `rollBack` starts the old version from `<name>_v<old>_old.jar`, and the note it leaves makes the old version say *"v{1} was put back"* (`ui.update.rolledBack`) while the shortcut's name still holds the version that did not run.  And, wording: the Readme's *"not available while ..."* - the item is offered and refused when chosen; Help > Download Update's tooltip (`ui.main.tooltip.downloadUpdate`, set once at `:21077`) says *"or download it and update"* where, run from a folder of classes or an unwritable folder, the question offers only Download. |
| **Needs execution** | read (the three by `JarUpdater`'s own claims' shape: a launcher that writes the marker, one that ends without it, a mover that fails).  New.  **One way to settle it:** say in the Readme what "started" means and that the old jar can be renamed back; write the note only where the old jar is back under its name (otherwise a note that says where it is); and consider a shorter cap, or asking, for NO_WORD. |

---

### RSA68-C3 - FR-125: the update's exit is posted for whenever the backup ends, and it asks less than the Auto tab does: a hand send or Start counted from its click is not refused, it can fire inside a dialog the user is typing in, and its backup does not wait for a File > Backup still running

| | |
|---|---|
| **Disposition** | Fixed (`d15536fa`), on Adam's word of 2026-10-13 (asked which of RSA68's Cs to fix in 3.0.1, he chose every group): the refusal asks what the Auto tab asks; the exit waits for an open modal dialog; one backup at a time. |
| **Grade** | C - the first would leave a train under way as TrainControl exits (an A's consequence), but needs a click within the instant between a send's click and its thread counting itself, and then the exit's own trains question to miss it too: practically unreachable.  The other two are a surprise and an overlap, not a loss seen. |
| **Names** | FR-125, RSA27-C2 (`handSendsUnderWay`, counted from the click), `theAutoTabWaits` |
| **Where** | `installTheDownload` (`:28007`) posts `closeForTheUpdate` (`:28060`) when its backup returns; `closeForTheUpdate` (`:28077`) asks `whyAnUpdateMustWait(model, stagingFlowActive \|\| isAutonomyBusy())` - `Layout.isRunning`, not `theAutoTabWaits` (`:26109`: also a hand send under way from its click, and Graceful Stop offered); `WindowClosed` then saves and blocks the event thread in `startUpdater` up to 15 s (`:21641`) before `System.exit`.  `SelfUpdate.HandOver.isUnderWay` is asked by no door, so Start, a hand send, Execute Timetable and Return Home stay open through the download and backup (each is refused at the re-ask if it has counted itself by then - the safe ordinary case).  Posted, the exit runs in whatever modal loop is open when the backup ends - a rename or locomotive dialog is closed by `System.exit` with its input unapplied.  And `backupData` (`:24512`) is called from `installTheDownload` (`:28034`) whatever the File > Backup item's state, whose greying is the only guard against two backups: both save `LocDB.data`/`UIState.data` through the same `.part` staging file (`Util.writeAtomically`) and name their archive to the second. |
| **Needs execution** | read.  New.  **One way to settle it:** ask `theAutoTabWaits()` in `closeForTheUpdate`; refuse new runs and File > Backup while `updateHandOver.isUnderWay()`; and post the exit only when no modal dialog is showing (or ask once more before closing). |

---

### RSA68-C4 - FR-125's wiring is pinned by nothing end to end: the marker written first in `main`, the variable that tells the new version where to write it, the real starters, the hook in `WindowClosed`, `closeForTheUpdate` and the working directory handed on all survive being broken

| | |
|---|---|
| **Disposition** | Fixed (`d15536fa`), on Adam's word of 2026-10-13 (asked which of RSA68's Cs to fix in 3.0.1, he chose every group): `regression.testTheUpdateIsWiredEndToEnd` runs the real launcher and starter against plain child JVMs and pins the exit's order. |
| **Grade** | C - a gap in the claims, not in the product: the bodies read right today. |
| **Names** | FR-125; `core.testTheUpdaterSwapsTheJar`, `ui.testTheUpdateQuestion` |
| **Where** | the claims hand in launchers, movers and starters (only the cross-process lock is real); `grep -rn "reportStarted\|STARTED_ENVIRONMENT\|startDetached\|closeForTheUpdate\|updateHandOver\|installTheDownload\|workingDirectory" test` finds nothing.  Survivors, each with what it would do: `SelfUpdate.reportStarted()` dropped from `TrainControl.main` (`:23`), or the `put` of `STARTED_ENVIRONMENT` in `JarUpdater.startProcess` (`:833`) - every real update kept unconfirmed after 10 minutes, or rolled back if quit sooner; `writeMarker(request.running)` moved after `update` in `JarUpdater.main` (`:387`) - every update refused after 15 s; the `take()` in `WindowClosed` (`:21641`) moved above `everyOpenWindowMaySettle` - an editor's Cancel leaves an updater waiting that swaps at the next ordinary exit within 5 minutes; the catch's `return` dropped - TrainControl exits with no updater; `startUpdater` handing on the jar's folder for `user.dir` - the new version reads its data from the wrong folder; `javaOptions` keeping `-agentlib:jdwp`. |
| **Needs execution** | read.  New.  **One way to settle it:** claim `reportStarted` and `startProcess` with a real child JVM on a scratch jar in a temporary folder (the brief's own allowance), read the order of `WindowClosed`'s steps as RSA67-C3's claim reads the constructor's wiring, and assert `Request.workingDirectory`. |

---

### RSA68-C5 - New in OB-341: the setup check and the drawing part in three corners only a hand-edited or older setup reaches - a station that is not a Point drawn twice on a page with no ERROR, an unnamed station out of service given the ERROR though nothing is drawn, and a duplicate on another page filed and clicked to under the station's own page

| | |
|---|---|
| **Disposition** | Fixed (`179652cc`), on Adam's word of 2026-10-13 (asked which of RSA68's Cs to fix in 3.0.1, he chose every group): a station that is no Point is reported when drawn twice; the unnamed-and-switched-off rule is in `isCaptionDrawn`; the finding is filed on the page that draws the name twice. |
| **Grade** | C - no door of the editor makes any of the three states (the drop-down lists only built Points; doors and imports keep one caption per page); the rule's promise is *"an ERROR exactly where the diagram draws a name twice on a page"*. |
| **Names** | OB-341, OB-256, MT-337, GSE-C4 |
| **Where** | (a) `AutonomySession.stationsShownMoreThanOnce` (`:8000`) skips a station not among the reducer's Points (`:8013`), while the drawing (`TrainControlUI.autonomyCaptionAt` `:6230`, through `getCaptionTarget` and `isCaptionDrawn`) does not ask: a sensor with no track marked a station, or a station on a page left out whose captions sit on an included page, is drawn twice with no finding - `testStaleCaptionsAreNotCounted` asserts "no report" for exactly such a lone sensor.  (b) `autonomyCaptionAt` draws nothing for a station unnamed and out of service (`:6249-6255`), before `isCaptionDrawn`; the check does not apply that rule, so two captions beside it raise STATION_NAMED_TWICE beside its automatic-name ERROR - two rows for one sensor, which `checkStationLabels` avoids elsewhere.  (c) `AutonomyChecks.checkStationsNamedTwice` (`:698`) files the finding on the station's own square, and the editor counts and clicks findings by that square's page: two drawn captions on page B of a station on page A are counted on A, the click goes to A where the outlined squares are not, and B's count says nothing. |
| **Needs execution** | read.  New.  **One way to settle it:** count by the drawing's own answer (`autonomyCaptionAt`'s rule, the Point question dropped or asked of both), and file the finding on the first drawn duplicate square. |

---

### RSA68-C6 - New in OB-341: what the user reads about the per-page rule overstates or understates it - the ERROR says "Keep one" across pages, the takeover question says "off this page" when Yes leaves the station shown nowhere, and behaviour.md says an excluded page's caption counts for nothing

| | |
|---|---|
| **Disposition** | Fixed (`179652cc`), on Adam's word of 2026-10-13 (asked which of RSA68's Cs to fix in 3.0.1, he chose every group): the ERROR and the takeover question follow the per-page rule (`confirmTakeAnotherStationsOnlyName`), and behaviour.md on excluded pages. |
| **Grade** | C - wording (a C at most, by the 2 October ruling), though each says something the program does not do. |
| **Names** | OB-341, GSE-C5 (the takeover question), the 12 October ruling on unlabelled stations (not a finding in itself) |
| **Where** | `autosetup.ui.checkStationNamedTwice` (`messages.properties:1534`, all 8): *"{0} is shown in {2} places on the track diagram ... Keep one"* - with two on each of two pages it says four places and "keep one", which followed literally leaves one caption where one per page is allowed; with two on one page and one on another it says two places of three.  `autosetup.ui.confirmTakeAnotherStationsName` (`:1865`) after `mayTakeAnotherStationsOnlyName` (`AutonomyEditorPanel :3792`, filtered to this page at `:3802`): *"leaving {0} off this page?"* - where that was the station's only caption anywhere, Yes leaves it shown nowhere (a Must-be-fixed error at once); before OB-341 it said "off the diagram".  `behaviour.md:893`: a caption on a page left out or gone *"counts for nothing"* - it still counts against "shown nowhere" (`getLabelledStationTiles`, `AutonomySession :3273`, all pages), as Adam left it. |
| **Needs execution** | read.  New.  **One way to settle it:** "on one page" in the ERROR and its count per page; "off this page, and off the diagram" where no other caption remains; scope behaviour.md's sentence to the duplicate check. |

---

### RSA68-C7 - The Readme's 3.0.1 changelog has no line for anything built since RSA67: one-click updates, Activate and Deactivate All Trains and the sorted cards, the configuration in the titles, the duplicate-caption error, and the pause kept

| | |
|---|---|
| **Disposition** | Fixed (`577fc67f`): the 3.0.1 changelog has a line for each. |
| **Grade** | C - an omission, not a wrong statement; it may be meant for release time, in which case say so and close.  DCN-C11 (the same for 3.0.0) was a C and fixed. |
| **Names** | FR-125, FR-126, FR-127, OB-341, OB-342, OB-343, OB-344, RSA67-C1; DCN-C11 |
| **Where** | `Readme.md:402-409`: the v3.0.1 entries cover OB-340, OB-338/339 and OB-337; the docs diff since `d2c4704b` has no changelog hunk (the new "Updating TrainControl" section is at `:352-362`). |
| **Needs execution** | read. |

---

### RSA68-C8 - Older: a pop-up diagram window keeps a caption that was moved or taken off, and goes on writing the station into it, until it changes page or is opened again

| | |
|---|---|
| **Disposition** | Fixed (`179652cc`), on Adam's word of 2026-10-13 (asked which of RSA68's Cs to fix in 3.0.1, he chose every group): a pop-up whose captions changed is redrawn when the setup changes or an editor closes. |
| **Grade** | C - a stale picture in a secondary window; the main window is right.  It matters now because Stop Showing is the remedy the new ERROR asks for. |
| **Names** | OB-341 (the registry by page), RSA40-C1 |
| **Where** | `TrainControlUI.autonomySetupChanged` (`:4323`) and `autonomyEditorClosed` refresh the main window's diagram only; `updatePopups(true)` is reached from the track editor's refresh (`layoutRefreshCompleteInternal`, `:25627`) and the Show Addresses toggle alone.  A pop-up's caption labels are registered under its own owner, so the main window's rebuild never prunes them, and `updateStationLabels` keeps writing the station's live text into the one on the old square. |
| **Needs execution** | read, not run (medium confidence).  Older.  **One way to settle it:** `updatePopups(true)` from `autonomySetupChanged` when captions changed (as `captionsAreAsBuilt` tells for the main window). |

---

### RSA68-C9 - Older, AD-C6's fix incomplete: the autonomy editor can put a caption on a square outside the area the running diagram draws, so the station is named nowhere on that page there and nothing reports it

| | |
|---|---|
| **Disposition** | Fixed (`179652cc`), on Adam's word of 2026-10-13 (asked which of RSA68's Cs to fix in 3.0.1, he chose every group): no caption is placed, offered or dropped outside the area the running diagram draws (`LayoutDiagram.isDrawnWhenRunning`).  A caption already outside it is not reported (that would change the unlabelled check; not asked). |
| **Grade** | C - a corner of placement; the editor shows the caption, the running diagram does not, and the unlabelled check is satisfied by its existence. |
| **Names** | AD-C6 (Closed, Fixed: *"A caption could be filed outside the area the diagram draws"*) |
| **Where** | `LayoutDiagram.checkBounds` (`:190`): in edit mode the bounds are the whole page; out of it (`IGNORE_PADDING`) only the squares holding a component - and a caption is not a component.  `LayoutEditor.render` sets edit mode on the page object the session shares, so `AutonomySession.placeCaption`'s bounds guard (`:3911`, AD-C6's) reads the whole page when its only caller, the editor's `placeLabelFor`, asks; and Show a Station Name Here on a blank margin square, and the drag (`refuseCaptionDrop`), have no rule about the drawn area at all.  `testACaptionIsNeverPlacedOutsideTheDrawnArea` builds its page with edit mode off, so it passes either way. |
| **Needs execution** | read, not run (medium-high confidence).  Older.  **One way to settle it:** compute the drawn area from the components alone (as the running grid does) for the guard, and refuse a caption square outside it at every door. |

---

### RSA68-C10 - Older: the covered-caption warning and behaviour.md say the user's own text is drawn and the caption hidden; the grid draws the caption and hides the text

| | |
|---|---|
| **Disposition** | Fixed (`179652cc`), on Adam's word of 2026-10-13 (asked which of RSA68's Cs to fix in 3.0.1, he chose every group): the warning and the docs say what is drawn - the caption over the text (MT-017's question already said so). |
| **Grade** | C - a wrong statement in a warning and in the written rule; nothing is lost. |
| **Names** | OB-341 (its check counts such a caption as drawn - agreeing with the grid, not with the warning) |
| **Where** | `autosetup.ui.checkCaptionCovered` (`messages.properties:1501`, *"... your own text, which is shown instead"*) and `AutonomyChecks.checkCoveredCaptions`' javadoc (`:1296-1306`); `behaviour.md:895` (*"Text you have written on a square wins"*).  `LayoutGrid` (`:1382` on): `captioned` comes from `autonomyCaptionAt` with no question about the square's own label, every `captioned != null` branch sets the caption's text, and the user's own (`own`) is written only in the final `else` (`:1715-1727`). |
| **Needs execution** | read (80-90% confidence).  Older.  **One way to settle it:** decide which wins (Adam's), then make the grid, the warning and behaviour.md say the same. |

---

### RSA68-D1 - RSA67-C1's per-signal lock: every command that turns a held guard RED or GREEN on a path's account - the refresh, the edge's own commands, the exit guards - is one step against the others; the lock is taken last, under it only short monitors that no holder of it waits on, so no new deadlock leg; the forced interleaving passes and a load test finds no deadlock

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RSA67-C1, OB-340, AU-A2 (a route's check-then-command, accepted), 7c's stated exception (`throwEntryGuard`) |
| **Where** | `Layout.signalLocks`/`signalLock` (`:11960-11990`, keyed by the accessory, whose `hashCode` is its UID and address - stable); `refreshOneSignal` (`:11999`) holds it from the walk of the Points to `setState`; `configureEdge` (`:3914`) and the exit guards (`:4859`) hold it once the claim (`takingPath.put`) and `e.setOccupied()` make the hold readable.  Under it: `getActiveAccSettings` (ConcurrentHashMaps, `Edge.isLockHeld`/`getConfigCommands`), `onlyPassing`/`pathHeldBy` (no monitor), `getAccessoryByName` (the database), `MarklinAccessory.setSwitched` (its own monitor, `accessoryMonitor` briefly, `updateTiles` -> `setImage` posted, `repaintSwitch` posted, `exec` - whose echo and log are asynchronous).  Held while it is waited for: the railway's monitor (dispatch) and `activeLocomotives` (the early release) - neither taken under it.  `throwEntryGuard` (`:11824`) still commands RED unlocked - 7c's stated exception; routes and the keyboard keep their accepted check-then-command. |
| **Needs execution** | done - `regression.rsa68GuardClaims` 29 of 29 (the four forced claims with Atomic Routes off and on, the edge commanding the guard itself); **`core.rsa68Race`** (`w/out/race.txt`): *"atomic=false 25 s: journeys 64 ... exit guard RED at hand-over 0 ... refreshes 18768476, deadlock none"*, *"atomic=true 25 s: journeys 64, refused 2 ... RED at hand-over 0 ... refreshes 21643014, deadlock none"*.  The same probe at `d2c4704b` (`race-pre.txt`) also saw no RED - the unforced window is microseconds against a 150 ms configure sleep - so it speaks for liveness, and the forced claims for the race. |

---

### RSA68-D2 - The window-monitor leg is gone, and no other: `repaintSwitch` only posts, and nothing a driving thread does under the railway's monitor, a signal's lock or an accessory's monitor waits for the window

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | `fd706247`, OB-192 |
| **Where** | `TrainControlUI.repaintSwitch` (`:13054`) unsynchronized, its body `invokeLater`; `TrainControlUI.log` posts; `LayoutLabel.updateImage`/`setImage` post their Swing work and submit decodes; `MarklinControlStation.receiveMessage` hands every message to executors; the sent-message observer is set only by tests. |
| **Needs execution** | done - `testADrivingThreadNeverWaitsForTheWindowsMonitor` green with a display (the class's four other reds are want of a locomotive database, D10). |

---

### RSA68-D3 - RSA67-C3: Exit's two halves are claimed - the saved address forgotten in the preferences given, then the exit - on the run's isolated node only, and the constructor's wiring and the real body read

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Where** | `MarklinControlStation.exitUnconnected(Preferences, Runnable)` (`:1626`); `testExitForgetsTheSavedAddressAndEnds`, `testExitUnconnectedForgetsTheRealAddressAndEndsWithCodeOne`. |
| **Needs execution** | done - `core.testControlStationFaults` 22 of 22 with a display. |

---

### RSA68-D4 - OB-343 on every other path: a door writes as built only the trains it changed, read from each train after the change; the whole and partial folds bring the record up to date; nothing is undone by a later rebuild on any traced order of card, tick, tools, editor opening, load, reset and exit; the grey is published paused-first; all on the event thread, none taking the railway's monitor

| | |
|---|---|
| **Disposition** | Checked - clean (but B1). |
| **Grade** | D |
| **Names** | OB-343, RSA60-B1, RSA60-C5, RSA21-C1 |
| **Where** | `notePausesAsBuilt(changed)` and `setLocomotivesActive` (`TrainControlUI :4652-4736`); `AutonomySession.capture` (`:6895-6911`), `captureTheRailwaysSettings` (`:6987-7016`, taken keys follow, keys the railway stopped writing dropped, the timetable skipped while pages are stale - consistently); `copyOfSetting`; `rebuildRunningLayoutFromSetup` (fold, then load, then `noteTheRailwaysSettingsAsBuilt`); `DiagramMonitor.setTrainsOnNoPoint` (the paused set first, both volatile; one writer, so a frame sees at worst the old trains with the newer set); `DiagramMonitorDriver.bind` passes the paused set.  Traced: card pause then tick; card going then tick; card then Activate; card then Deactivate; a card on a train off the layout then Deactivate; editor opening's fold then card.  Not a finding, for the record: the carry load (`carryTheTrainsAcross`) does no settings fold, but is reached only after a declined rebuild, when no run can capture anything - unchanged by these commits. |
| **Needs execution** | done - `core.testAPausedTrainStaysPaused` 7 of 7, `ui.testTheActiveTickPausesATrain` 19 of 19 (end to end through the window: cards, menus, both tools, the editor's fold, the grey), `core.testAutonomyDiagramMonitor` 68 of 68.  Claim gaps, minor: swapping the two writes in `setTrainsOnNoPoint` (without emptying) survives; dropping the whole fold's stale-key clearing survives. |

---

### RSA68-D5 - FR-126 as ruled: Activate sets every paused train going, placed or not, without asking; Deactivate pauses only the trains standing on the layout; both refused while anything runs; the cards sorted when the tab is opened (active, paused, inactive stations, by name), never on a pause click; the tools read each train's own flag; nothing on the event thread takes the railway's monitor

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | FR-126, OB-343 |
| **Where** | `AutonomyEditorPanel.bulkTools`' two items (`item` -> `refusedWhileRunning` -> `isAutonomyBusy`), `pausedTrains`, `goingTrainsStanding`, `isPaused`, `setEveryTrainActive` (the list's names the model lacks written to the setup alone); `TrainControlUI.autoTabOrder` (`getLocomotiveLocation` and `Point.isActive`, both unsynchronized), `resortAutoLocList` (moves the existing cards only), `sortTheCommandsTabIfShown` from the tab selections alone; the card's pause reaches only `trainsPausedChanged`.  Not a finding: a card opened mid-run groups a moving train by any Point it reserves, as the old comparator did. |
| **Needs execution** | done - as D4. |

---

### RSA68-D6 - OB-341's per-page rule in every ordinary case: the drawing and the check ask one rule; a self-caption gives way only to one on its page; excluded and gone pages draw nothing; the registry's prune is only narrowed, so a shown label cannot freeze blank; every editor door, the import and the migration work per page; nothing of it reachable during a run

| | |
|---|---|
| **Disposition** | Checked - clean (but C5, C6, and the older C8-C10). |
| **Grade** | D |
| **Names** | OB-341, MT-337, OB-256, RSA40-C1, MT-670 |
| **Where** | `AutonomySession.isCaptionDrawn` (`:3343`), `drawnCaptionsFor`, `setCaption`/`clearCaptions` (this page only), `isCaptionedOnPage`, the old-file import and `migrateStationLabels`; `AutonomyCompanionStore.importBundle` (by station and page, the file's own repeats included, in square order); `TrainControlUI.addLayoutStation` (`LAYOUT_STATION_PAGE` adds an AND to the eviction; the labels it now keeps are retired by `discard`, the cache resets and `forgetTheCachedPagesButTheShownOne`); `updateStationLabels` and `autonomyPointsForTile` key by the station's square, so every page's label is found; the menus' Stop Showing on switch, signal and excluded squares; every caption door through `item` -> `refusedWhileRunning`; the findings cache keyed on the setup snapshot. |
| **Needs execution** | done - `core.testAutonomyDiagramSession` 236 of 236, `regression.testControlNAsksTheMenusQuestion` 9 of 9, `regression.testAPendingTurnSurvivesTheRebuild` 13 + 1 skipped, `regression.rsa68HomeLabel` 6 of 6 (`testTheSetupCheckCountsWhatTheDiagramDraws` - the one end-to-end comparison of check and grid, one page - among them). |

---

### RSA68-D7 - FR-125, a user's installation and data, Windows first: the old jar is touched only after TrainControl's process has ended, is never overwritten and never left partial under its name; the data is saved by the normal exit and the backup taken before anything is replaced; nothing is started unasked or twice; a refusal, a failure or a cancel leaves everything as it was

| | |
|---|---|
| **Disposition** | Checked - clean (but C2-C4). |
| **Grade** | D |
| **Names** | FR-125, the 12 October rulings |
| **Where** | **The exit lock:** `SelfUpdate.holdTheExitLock` (`tryLock` beside the jar, held in static fields until the JVM ends; let go only on `startUpdater`'s failure), `JarUpdater.waitForLock` (null, IOException or overlap = held; LockFileEx on Windows, fcntl elsewhere); no shutdown hook anywhere, and the lock file is opened by nothing else in TrainControl (so no POSIX early release).  **Not unasked:** `HandOver.exitWith` arms the update only for the exit call and disarms it in a finally; `WindowClosed` takes it only after the editors' question, the trains question and both saves; stale running and started markers deleted first; the note read once and deleted; one start per session.  **The swap:** both renames in one folder, no REPLACE_EXISTING, `backupName` never an existing file; a first-rename failure (the jar still held) puts nothing out of place and starts the old version.  **The download:** to `.partial` and renamed when whole; size and `sha256` where the release gives them, and a Main-Class always; a release jar is the fat jar (`build.xml`).  **The process:** same `java.home` (`javaw.exe` on Windows), options less debuggers and agents, `-jar` the absolute path, `main`'s arguments, the old `user.dir` - where the data is read from; handles not inherited; a path outside the code page cannot reach the updater, which then never answers, and TrainControl stays open.  **The question:** Download, Download & Update only from a jar in a writable folder, Download the default, the close box Cancel.  **Not a finding, for the record:** `startUpdater` lets go of the lock without waiting for the updater it has just stopped (`updater.stop()`, `:519`, a `destroy` with no `waitFor`) - with Windows' `TerminateProcess` and a JVM's prompt halt on SIGTERM the window is theoretical; a throw between the updater's answer and `System.exit` would leave an updater waiting 5 minutes on an open TrainControl - no throw there is realistic. |
| **Needs execution** | done - `core.testTheUpdaterSwapsTheJar` 14 of 14, `ui.testTheUpdateQuestion` 3 of 3; the rest read (the real update was not run, as the brief requires). |

---

### RSA68-D8 - FR-127, OB-344 and OB-342 at 100% and 125%: each title follows every load, switch, rename, reset and page change, set on the event thread; an old `Point:` label is drawn as written and nothing reads it as a station; every control shows at the smallest size on this machine's 125%

| | |
|---|---|
| **Disposition** | Checked - clean (but C1). |
| **Grade** | D |
| **Where** | `activeDiagramConfiguration` written in three places, each followed by `refreshConfigurationTitles` (`resetAutonomySession`, `autonomyLoadedFromDiagram`, `autonomySetupRenamed`); the store's chosen configuration moves only by doors greyed while an editor is open; `LayoutEditor.titleFor`/`refreshTitle`, `LayoutPopupUI.titleWith`, the renders' titles; the pre-release title rule untouched.  `LayoutGrid :1715-1727`; the prefix used only by the migration and Combine Linked Pages.  The status line's tooltip set beside both `banner.setText`s. |
| **Needs execution** | done - `ui.rsa68Looks` 3 of 3 (the render claim with a locomotive made), the editors' title claim 1 of 1, `regression.testTheVisibleElementsHeadingIsSpacedLikeTheOthers` 5 of 5 at 120 dpi. |

---

### RSA68-D9 - The documentation and bundles, apart from C2, C6, C7 and C10: Automation.md's caption rows, Bulk Tools rows, sort paragraph, title line and Must-be-fixed bullet, behaviour.md's caption section and 7c's lock sentence, and the Readme's Updating section say what the program does; all 33 new or changed keys are in all 8 bundles with the same placeholders, ASCII-only, and no apostrophe for `MessageFormat` to eat

| | |
|---|---|
| **Disposition** | Checked - clean (but C2, C6, C7, C10). |
| **Grade** | D |
| **Where** | docs diff; the 8 bundles; each bundle's quoted "Stop Showing" matches its own menu item.  Wording is Adam's to judge. |
| **Needs execution** | read; `core.testMessageBundles` 24 of 24. |

---

### RSA68-D10 - No regression where these changes touch: every class run is green but for want of the locomotive database, and each such red or skip passes with locomotives made in memory where a copy was made

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Where** | the classes in Method's **Ran**.  The want-of-database reds and skips: `testTheDiagramRefreshDoesNotWaitOnTheRailway`'s four (*"no square on this snapshot can hold a train"*), `testAPendingTurnSurvivesTheRebuild`'s one (*"no train stands at a captioned station"*), `testTheHomeLabelIsDrawnOnce`'s six and the FR-127 render claim (both green in their copies). |
| **Needs execution** | done. |
