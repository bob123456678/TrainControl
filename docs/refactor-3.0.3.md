# FR-136: making the code cheaper for agents (the 3.0.3 plan)

Measured on dev at b7eee1f9, 2026-10-18.  **Approved by Adam on 2026-10-18** (*"I approve the plan and the change to the comment rule"*), with the rulings below; where the survey's text and a ruling differ, the ruling wins.  FR-136 in the Inbox is the request: *"refactor code to make agents work faster- split large files, consolidate documentation, use subclassing as needed, clear dead code.  validate behavior is unchanged.  v3.0.3 change."*

## Adam's rulings (2026-10-18)

- **Done already:** `openLegacyTrackDiagramEditor` and `editCurrentPageActionPerformed` are removed from the form and the code (Adam's `a4dac3dd`; OB-359).
- **Keep every public method on the API classes, used or not** (Layout, Edge, Locomotive, MarklinControlStation; the list in §3): the API is not frozen, but *"these seem useful to an API user, and we should add examples later"*.  The examples are FR-138.
- **Keep anything Adam wrote, and ask before removing it:** a method that dates from before 2.7.0 (tag `v2_7_0`; `git log --follow -S'<name>' --reverse` or `git blame` gives its first commit) is listed for his approval, never removed by a worker.
- **Keep `Conversion.convertSecondsToDate` and `ImageUtil.generateImageWithRect`.**
- **The comment rule in §4 is approved**, amending the 2026-09-07 ruling (recorded in `docs/reviews/README.md`).
- Answers to the questions: see §7.

## Second pass, before Phase 0 (Adam, 2026-10-18, after 3.0.2 shipped)

**Measure, don't trust the figures below.**  3.0.2's last day moved the giants by up to 180 lines (TrainControlUI
35,157, Layout 15,604, AutonomySession 14,703, AutonomyEditorPanel 12,504, LayoutEditor 8,173).  Line numbers in this
plan are stale: workers find code by method name and the audit (§2), re-run at the start of every phase.

**New code since the survey.**  OB-361 already moved run-finding into `TileGraph` (so §2's `SessionRuns` is smaller);
`RouteRunningMarks` (FR-137) is a small package-private helper of the shape this plan wants, a model to copy;
`AutonomyEditorPanel.copyNoticeToClipboard` and the route editor's conditions warning are new.

**Dead code, ruled.**  Removed: `withNewFirst`, `routeNamed`, `showTextMenu` (`c1b9ca32`), `CustomActionEvent`
(`b4458803`; it marked a validation the program started itself, unused since OB-254).  Kept by Adam:
`nextLocMapping`, `prevLocMapping` (*"keep prevlocmapping and nextlocmapping"*).

**Phase 0 also covers (lessons of the 3.0.2 run):**
- Tests that COPY a rule of the program instead of asking it.  Two tooltip claims held their own 320-pixel copy of the
  editor's one-line rule and went red, looking like a regression, when OB-358 changed the rule.  Find such copies (a
  constant or formula restated in a test) and make the test ask the program; the audit lists private members a test
  reaches by name (`fitsOnOneLine` is one now).
- The intermittent claim: `regression.testAThrottleReversalIsFollowedFromItsEcho` failed once under a battery's load
  (the facing E, not W) and passed five times alone.  Every phase is accepted on a battery, so it is made steady first.

**One test runs at a time, across every worktree.**  The runner's lock is global and a battery holds it for 2-3
hours, so "in parallel" below means editing in parallel; test runs queue.  Batteries are scheduled when no worker needs
the lock.  Every worker queues each run as ONE background command with its own retry loop (never short polling), and
never uses `git stash` (shared by every worktree, and holding Adam's own entries): `git diff > file` / `git apply`.

**Freeze and Inbox.**  The TrainControlUI feature freeze starts now (FR-137, FR-140 and the route refusal's naming
landed in 3.0.2).  3.0.3's Inbox: FR-136 (this plan), FR-138 (API examples, last, once the API classes have settled)
and FR-130 (the yardstick below).  Archiving released MTs (Phase 1) keeps MT-771, which is still to be run.

**Review indexes once the documents are deleted (Adam: "Agree on the indexes").**  The finding store is the index:
every finding is a row of `triage.db` (and `findings.tsv`) with its ref and document.  A round's next number comes
from the store - `python docs/tools/catalog-findings.py --next RSA` prints it and refuses a prefix or number already
used - and never from the folder.  A round's record is written to a working folder, catalogued with `--add`, then
deleted.  The tool's full rebuild, which re-reads whatever documents exist, refuses to run once the documents are gone,
so the deleted rounds' rows cannot be lost.  Built in Phase 1, with the folder's deletion; `docs/reviews/README.md` says
so.

**The yardstick: FR-130, timed before and after (Adam: "add a trivial implementation task to the plan, and measure its
time taken before 3.0.3 ... FR-130, ... which we would then release concurrently").**
- Before (now): one Sonnet worker implements FR-130 from a fixed brief (`scratchpad/fr130_brief.md`, kept for the
  repeat) on the code as it is.  Recorded: wall time, tool calls, tokens read, and the time spent waiting for the test
  lock, separately (it depends on what else runs).  Its result merges and ships in 3.0.3.
- After Phase 5 (or whichever phase ends the work): the same model and brief implement FR-130 again, on the refactored
  code, in a throwaway branch that is never merged.  Same four figures; the difference is the plan's result.
- **Before, measured 2026-10-18** (dev `acaaf3fc`, Sonnet, commit `9e55fb8f`, merged as `ec7925d4`): 372 s of agent time
  (about 7 minutes from launch to report), 32 tool calls, 107,349 tokens, 30 s waiting for the test lock.  Two files
  changed (`TileOverlay`, `ui.testDiagramLooksRight`); no glow needed.
- **And the quality of the result, not only its cost (Adam, 2026-10-18: "let's also compare the quality of the result in
  addition to the efficiency (cost and time) of the build").**  Each build is judged the same way:
  - Regressions: every test class that reads the touched source files is run on the build, not only the classes the
    worker ran; the reds it caused are counted.
  - A blind review: one Opus reviewer grades both diffs against the brief, labelled X and Y in a random order, with the
    validator's A/B/C grades (wrong behaviour, missing or weak claim, comment or style), and states which it would ship.
  - Its claims: whether each test it added turns red when its change is reverted, and whether it pins anything the brief
    did not ask for.
  - The fit: lines changed, files touched, and whether the comments follow the comment rule.
  - The after build also passes the before build's tests, and Adam's MT-772 steps are walked on it.
- **Before, quality so far:** one regression, found after the merge. `core.testAutonomyDiagramMonitor`'s
  `testTheIconRunsAlongEveryCurveAndDoubleCurveOnItsOwnRoad` (MT-642) went red. The icon, drawn as one upright picture
  and then turned, came out 86% like the drawing it is held to, against a 98% bar. The worker ran only
  `ui.testDiagramLooksRight`; the Monitor class also reads `TileOverlay`.
- **After, measured 2026-10-19** (dev `bcb78603` with FR-130 taken out, Sonnet, the same brief and prompt; branch
  `fr130after2`, commit `6354e3ee`, never merged): 311 s of agent time, 39 tool calls, 116,364 tokens, no wait for the
  test lock.  Two files changed (`TileOverlay`, `core.testAutonomyDiagramMonitor`); no glow.
  - **A first after-run does not count.** It ran on a branch where FR-130 had been reverted, and the revert commits'
    messages quote the original title, which names the design ("halo and all as one picture"); its commit message
    repeated that title word for word.  The counted run used a separate clone whose history is one squashed commit on
    `acaaf3fc`, with no remote, and with MT-772 and FR-130's receipt removed from its copy of the tracker.
  - **Cost, before and after:** agent time 372 s and 311 s (17% less); tool calls 32 and 39; tokens 107,349 and 116,364
    (8% more).  One run each, so within the noise.  FR-130 changes `TileOverlay` only, which no phase split or trimmed,
    so this task barely touches what the refactor changed; it cannot show the refactor's main gain.
  - **Quality, before and after:**
    - Regressions: the before build broke the curve claim (MT-642); the after build broke nothing (all 12 test classes
      that read `TileOverlay` green: 220 tests).
    - Each build's claim fails without its change.  The after build also passes the before build's claim.
    - A blind Opus review (X and Y in a random order) found no A in either, three B and two C in the before build, two
      B and two C in the after build, and would ship the after build: one scale-and-rotate draw as before, the halo
      made hollow under the icon once in its cache, and nothing new allocated per repaint.  Its doubt that the before
      build's claim could fail on unchanged code was checked and is wrong: that claim fails without FR-130.
    - The after build leaves the halo opaque around the icon and draws only the icon at 90%; the before build drew the
      two as one picture at 90%.  Neither added a glow.

## Summary

Agents spend most of their cost reading four Java files: TrainControlUI (35,278 lines, 1.76 MB), Layout (15,597), AutonomySession (14,524) and AutonomyEditorPanel (12,455). Since 2026-09-01 each of them was touched by 11 to 18% of all 1,824 commits. A typical change is small (V302's Unload refusal added 3 source lines and 49 test lines), but to make it an agent has to read thousands of lines around it. About half of every giant is comment, and most of that comment is history: dated rulings, "it used to", and review-round narrative. Those notes already have a home in the finding store, which `testEveryCitationResolves` resolves against. The splits can be done, but the tests depend on the file layout in three ways. 78 test files read source files as text (42 of them read TrainControlUI). About 217 TrainControlUI members are named by string in 88 test files. Several bookkeeping guards are keyed by file name. So the plan starts by making the tests survive a split (Phase 0). Next come two near-zero-risk wins: deleting dead weight, and trimming comments in commits proven by bytecode comparison to change no code. Only after that does it extract method clusters by delegation, in releasable steps, lowest test exposure first. Each step gets its own battery. Layout's public API and every `synchronized` declaration stay in Layout.java.

## 1. Measurements

**Largest source files** (lines / estimated methods / comment lines per code line / finding citations in comments):

| File | Lines | Methods | Comment:code | Citations | Notes |
|---|---|---|---|---|---|
| gui/TrainControlUI.java | 35,278 | ~925 | 0.55 (0.84 hand-written) | 729 | 6,710 generated lines: initComponents 15722-21488, variables 34379-34800, 125 GEN-FIRST handlers. ~200 hand-written fields. |
| automation/Layout.java | 15,597 | ~335 | **1.16** | 360 | Public Java API (AutomationAPI.md). The monitor guard reads this file by name. |
| automationui/AutonomySession.java | 14,524 | ~377 | 1.05 | 356 | Lowest test exposure of the giants (see §2). |
| gui/AutonomyEditorPanel.java | 12,455 | ~301 | 1.02 | 410 | Hand-written JPanel, no .form. |
| gui/LayoutEditor.java | 8,132 | ~186 | 0.89 | 198 | .form-backed; generated region is small (7504-7692 plus variables). |
| automationui/AutonomyCompanionStore.java | 6,700 | ~205 | 0.88 | 76 | Already uses subclassing (the `Kept` hierarchy). |
| marklin/MarklinControlStation.java | 5,565 | ~153 | 0.74 | 53 | Public API. |
| gui/RouteEditorFrame.java | 4,948 | ~150 | 0.78 | 42 | |
| test/core/testAutonomyDiagramSession.java | 14,313 | ~323 tests and helpers | 0.49 | 282 | The largest test file. |

Across all of `src/`, 67,229 of 176,931 lines are comment (38%). In the four giants there are 132 comment blocks of 30 lines or more (5,044 lines). The longest run to 46-59 lines; TrainControlUI 8324-8379 is a typical one, an OB-144 → OB-183 → MT-337 history told in prose. In TrainControlUI, 669 comment lines use history wording and 178 carry dates.

**Largest docs:** docs/manual-tests/tests.md 1.86 MB (757 MT entries, rendered from triage.db); docs/reviews-2026-09-25/ 97 files, 3.68 MB (71 RSA, 8 RLV, 5 each of RLA, RLD and RLU, plus BCR, BPV and E2E); docs/reference/behaviour.md 267 KB (3,159 lines; §5 Length alone is lines 990-2108); docs/manual-tests/issues.md 184 KB; Automation.md 73 KB; Readme.md 63 KB; open-questions.md 47 KB; AutomationAPI.md 44 KB. Churn since 2026-09-01: tests.md 344 commits, behaviour.md 332, TrainControlUI 328, issues.md 267, AutonomySession 242, open-questions.md 231, Layout 218.

**What a common change costs today.** Take a refusal or door change like V302. The agent greps TrainControlUI, then reads the gate (`whyNoTrainMayBeSent`, ~7000), the doors that ask it (scattered across 7000-7300 and 26600-26900), and the neighbours of each door, each with a 20-50 line comment of history. Then it reads one or two 5k-line regression tests and the bookkeeping guards it may have to update. That is easily 3-6k lines (150-300 KB) to make a 3-line edit. Methods with one name are spread across the file: `refreshWhatWaitsForTheTrains` and `applyLayoutEditingAvailability` are defined or called in more than 10 places, thousands of lines apart.

## 2. File splits

### The constraints

- **Source-scanning tests.** 78 test files read source as text. `"src/org/traincontrol/gui/TrainControlUI.java"` appears 102 times, in 39 files. They typically call `indexOf("private void checkAutoLayoutLatency(")` and take the body up to `"\n    }"`, so they depend on file, visibility, signature text and indentation. **36 files make negative source assertions** (`assertFalse(window.contains("void autoLoadOffAfterLegacyImport("))`). These go silently vacuous if the code moves to another file.
- **Reflection by name.** There are 754 `getDeclared*` calls in 149 test files, plus helpers that take a name parameter (for example testNoSetupEditDuringARun:5549 and testControlStationFaults:1519). The most reached names are setupEditDeclinedDuringRun (20), stagingFlowActive (15), openEditor (11), captureRunningLayout (7) and autonomySession (10). `getDeclaredField` does not search superclasses, so a superclass split breaks it, and loudly. That is better than the 237 catch sites in tests, some of which swallow a missing member. For example, testALocalLayoutNeverWritesAutonomyJson catches `NoSuchFieldException` for `autonomyJSON`, so a moved member there passes silently.
- **Guards keyed by file.**
  - testNothingOnTheEventThreadTakesTheRailwaysMonitor reads `synchronized` declarations from **Layout.java only** (its `LAYOUT` constant). It scans only `gui/` and `automationui/` (`SURFACES`), and its `ALLOWED` is keyed `File.java#member`; 21 of those keys are TrainControlUI's.
  - testJavadocsAreAttached pins orphans per file (`ORPHANS_BY_FILE`: TrainControlUI 23, AutonomyEditorPanel 18, AutonomySession 9; 86 in total).
  - testNoSelfRecursiveWrappers checks only TrainControlUI. It exists because a mechanical delegation rewrite once made a wrapper call itself, which is exactly the risk extraction creates.
- **.form classes.** initComponents, the variables block and the guarded handler signatures must stay in TrainControlUI and LayoutEditor. Handler **bodies** are ordinary code and can shrink to one-line delegations. Removing a generated component (see §3) needs the GUI Builder (Adam), not a hand edit.
- **Public API.** AutomationAPI.md documents `Layout`, `MarklinControlStation`, points, edges and locomotives as a Java API. Their public signatures must not move or change.

### The method

Extract by **delegation into package-private collaborators in the same package**. Fields stay on the owner class, so field reflection keeps working. A cluster of methods moves to `final class X { private final TrainControlUI ui; ... }`. Where a reflected or source-scanned method moves, the test is updated in the same commit. Do not use a superclass chain for TrainControlUI: it breaks `getDeclaredField` and hides where state lives. Use subclassing only where several copies already share a shape:
- the four "ask, settle, work out, await" asynchronous answer machines: covered track (TrainControlUI ~9300-9950), caption visibility (~13600), Return Home triage (~27100-27300) and the editor's Why (AutonomyEditorPanel ~9000-9500). These could become one `abstract class SettledQuestion<A>` with four subclasses.
- the Icon classes (TurningArc, RevertMark, WrenchMark, CropMark: ~240 lines).

### TrainControlUI clusters, by payoff against risk

Line counts include javadoc. "Refl" is the number of test files naming the cluster's members as strings; "scan" is the number of test files quoting its signatures; "mon" is the number of monitor-guard keys.

| Tier | Cluster → new class (gui/) | Lines | Refl/scan/mon |
|---|---|---|---|
| 1 | Loc icons and crop (setLocIcon, cropLocIcon, recropLocIcon, rememberCropSource, cropTheCentralStationPicture, deleteLocIconFile, the three Mark icons) → `LocIconActions` | ~1,620 | 1/1/0 |
| 1 | Update check and install (offerTheUpdate, askAboutTheUpdate, downloadTheUpdate, installTheDownload, whenNoModalDialog, closeForTheUpdate) → `UpdateFlow` | ~570 | 1/1/0 |
| 1 | Backup (prefixForBackup, backupData, writeTheBackup, BackupOutcome) → `BackupAction` | ~290 | 0/1/0 |
| 1 | Route list (executeRoute, runAndTimeTheRoute, refreshRouteList, editRoute, duplicateRoute, BulkEnableOrDisable, the play button, findRoute and routeNamedIn, CustomTableRenderer) → `RouteListPanel` | ~2,015 | 1/4/0 |
| 1 | Layout files and pages (duplicateOrRenameCurrentLayout, combineLinkedPages, writeIndexAndKeepLinksAimed, initializeEmptyLayout, unzipFile, downloadCentralStationLayout, createNewLayout; the copy, paste and clear "page" actions are locomotive mapping pages, not layout pages, and stay out of this cluster) → `LayoutFileActions` | ~1,280 | 4/4/0 |
| 2 | Timetable (repaintTimetable, timetableStop, delete, update and restart entries, clearTimetable) → `TimetableTab` | ~1,330 | 4/1/**3** |
| 2 | Autonomy settings tab (loadAutoLayoutSettings plus the 12 `*MouseReleased` bodies) → `AutonomySettingsTab` | ~840 | 0/1/0 |
| 2 | Locomotive operations (changeLocAddress, changeLocNotes, findSimilarLocs, deleteLoc, quickLocSearch) → `LocomotiveActions` | ~2,160 | 0/3/1 |
| 2 | Covered track and caption visibility (the settle/await machines) → `CoveredTrackView`, `CaptionVisibility` | ~870 | 2/3/1 |
| 3 | Return Home (refreshReturnHomeButton, triage, buildReturnHomeMenu, requestReturnToHome, describeStagingOutcome; the `stagingFlowActive` field stays) → `ReturnHomeController` | ~1,690 | **12**/2/3 |
| 3 | Setup ↔ railway sync (captureRunningLayout, rebuildRunningLayoutFromSetup, putTheTrainsBack, pending turns, settings as built) → `RunningLayoutSync` | ~1,740 | 6/4/**5** |
| 3 | Refusal gates (whyNoTrainMayBeSent, the refuseWhile* methods, whyAHandSendIsRefused, whyTheSetupIsHeld) → `RunGates`, mostly static | ~660 | 6/**7**/0 |

Tiers 1 and 2 move about 11k lines out. With Tier 3 (about 4k more), TrainControlUI becomes a shell of about 7k generated lines plus 8-10k hand-written ones.

### The other giants

- **AutonomySession → automationui.** This is the best logic giant to split first: only 25 names are reflected, in 27 files, and 5 scanning tests. Its callers are compile-checked, so delegation is safe.
  - `SessionImport`: LegacyImport, importLegacy, importBundle, detectImportFormat, findTheImportedFacings, carryTheOldDirections; ~1,400-2,950; ~1,500 lines.
  - `SessionLengths`: Stretch, stretchesNeedingALength, assign*Length, measuredIn, runInFigures and refusingFigures; ~4,965-5,600 and 13,700-14,100.
  - `SessionFindings`: check, checkAfresh, Same, GuardNotices and the destinationCopies* checks; ~8,000-9,100. Or fold these into the existing AutonomyChecks.
  - `SessionCoverage`: the *CoveredByStandingTrains and *BlockedByStandingTrains methods and walkBackFrom; ~10,000-10,600. Three monitor keys move with it.
  - `SessionPlacements`: placeable and departable facings, flipFacing, facingByPath, setHome, placeEveryTrainAtHome.
  - `SessionRuns`: Run, runs, runLeaders, setRunDirection, flowMarks and staticAnnotations; ~12,600-13,300.
- **Layout.** Keep **every public and every `synchronized` signature in Layout.java** as a one-line delegator, so the API and the monitor guard both stay intact. Move only the bodies of unsynchronized clusters into package-private `automation` helpers:
  - `LayoutExplanations`: explain*, why*, Destinations; ~6,000-7,100.
  - `StandingTrains`: the walk* and tail* methods and placesCoveredByStandingTrains; ~8,000-9,100.
  - `TrainFit`: whyTooLongForThisRoute and measuredRoomAtTheEndOf, and (taken in Phase 4, both already static) whyABerthCannotHoldIt and the 3-argument whyItWouldMeetItsOwnTail; ~12,000-13,100.

  Helpers must not take locks of their own; a call back into Layout while its monitor is held is re-entrant and safe. This needs Opus.
- **AutonomyEditorPanel → gui.**
  - `EditorTileMenus`: buildTileMenu, buildTextMenu, buildFacingMenu, buildArrivedFromMenu; ~1,000-4,600.
  - `EditorPrompts`: promptNumber, promptLocomotives, promptLengthFor, promptLinkName, promptBlockingPoints; ~5,000-7,200.
  - `EditorSignals`: the guard and signal pairing; ~7,000-7,600.
  - `EditorWhy`: composeWhy is a monitor key; ~9,000-9,900.
  - `EditorMassAssign`: ~11,000-11,600.
- **LayoutEditor.** Selection, clipboard, move and rotate (~2,400-3,600), shift, grow and shrink (~5,000-5,500) and undo/redo (~5,500-6,000) become helpers. The form's code stays where it is.
- **Tests.** Split testAutonomyDiagramSession (14k lines) by topic. This touches only tests, but needs the build.xml, battery membership and "Used by" README updates.

### Finding every reflection and source reference before a split

Commit an audit script under docs/tools. A prototype is in the scratchpad (reflaudit.py, clusters.py). For each member of the class being split, it lists:
1. every string literal in test/ equal to the member's name;
2. every `getDeclared*`, `getMethod` or `getField` call and every helper that takes a name parameter;
3. every quoted signature fragment (`"name("`) in a test that reads `src/`;
4. every `catch (NoSuch*Exception)` near the name.

Run it before and after each step. Any name whose declaring file changed must have its test edited in the same commit.

**Scans still read by class after Phase 0** (they fail by name when code moves, or they would miss moved code, so they are rebuilt before the class they read is split):
- `regression.testEditorSurfaceRules`: `testEveryDoorThatWritesTheSetupAnnouncesItWiderSweep` and its `writers()` read the AutonomyEditorPanel and AutonomySession class declarations.  Writes moved into a new class would not be scanned; only the `checked >= 7` floor would notice a wholesale move.  Rebuild it before any AutonomyEditorPanel or AutonomySession extraction.
- File-keyed lists that go red by name, not silent: `filesWriting`, the sendable-destination excuse list and the AutonomySession save skip (testEditorSurfaceRules), and testMessageBundles' `I18n.java` skips.  Each is updated in the commit that moves the file's code.

## 3. Dead code

**Before anything is removed:** a candidate first written before 2.7.0 (Adam's code) is listed for his approval and kept until he gives it; public methods on the API classes are kept (rulings above).

**The method.** Count identifiers in comment-stripped source with token counters, so the whole tree takes seconds. Then:
- a private member referenced only by its own declaration is a candidate;
- a non-private member referenced nowhere in src/ is a candidate, but only if it is not on an API class and not an interface implementation without `@Override`;
- a candidate whose name appears as a test string literal is kept until that test is examined;
- removing code that uses a message key can make the key dead and turn testEveryMessageKeyIsAskedFor (EXPECTED=0) red. Check that each key has another user first.

**Unreachable branches.** Look for generated components that are hidden or removed at start-up, `catch (NoSuch*Exception)` in tests, and dated "until"/"legacy" guards.

**Bundle keys.** These are already at zero dead, but keys asked for **only by test literals** are a second class worth listing.

**Found:**

| Candidate | Where | Confidence |
|---|---|---|
| `withNewFirst` (private, unused). Its key route.ui.frameNewRoute is still used by RouteEditorFrame:239, so it is safe. | TrainControlUI:14705 | High |
| `nextLocMapping`, `prevLocMapping` (private, unused) | TrainControlUI:15474, 15479 | High |
| `routeNamed` (private, unused; `routeNamedIn` is the live one) | TrainControlUI:33935 | High |
| `showTextMenu` (private, unused) | AutonomyEditorPanel:3410 | High |
| `CustomActionEvent` (21 lines, referenced nowhere since 2025-03) | gui/CustomActionEvent.java | High |
| ~~`openLegacyTrackDiagramEditor` and `editCurrentPageActionPerformed`~~ | TrainControlUI and .form | **Done** by Adam in the GUI Builder (`a4dac3dd`, OB-359).  Left behind: the now unused `DIAGRAM_EDITOR_EXECUTABLE` and `DIAGRAM_EDITOR_EXECUTABLE_ZIP` constants and the bundle keys only the handler used. |
| Test branch catching `NoSuchFieldException` for `autonomyJSON` (the tab was deleted in OB-254) | testALocalLayoutNeverWritesAutonomyJson:125-136 | High |
| Public methods unused anywhere on API classes: Layout.hasOnlyReversingIncoming, hasOnlyInactiveIncoming, hasOnlyInactiveNeighbors, hasOnlyReversingNeighbors, getPointById, getEdgeById, getPathLength, waitForS88Reached, getPointsInActivePath; Edge.clearConfigCommand, clearLockEdges; Locomotive.goForward, goBackward, getNumPaths, getTypes; MarklinControlStation.getLastLatency, getMarklinLocomotives | Layout, Edge, Locomotive, MarklinControlStation | **Kept** (Adam's ruling): useful to an API user; examples to come (FR-138). |
| Unused gui/util methods: LayoutEditor.editTextWithDropdown and addBoxHighlighted, LayoutLabel.paintCoach and getAutonomyOverlay, AutonomyEditorPanel.isShowingLengths, AutonomySession.getLeader, TileAnnotation.getTraces, LocIconCropDialog.getZoomFraction | various | Medium. The scan's false positives were listener methods without `@Override`, so check each by hand; any dating from before 2.7.0 goes to Adam first.  **Kept by ruling:** Conversion.convertSecondsToDate, ImageUtil.generateImageWithRect. |

The scan found 197 candidates in all; most of the others are listener false positives. The `examples/` classes are API samples and stay.

## 4. Documentation consolidation

- **docs/reviews-2026-09-25/ (97 files, 3.7 MB): delete it.** The folder's own README says each round's documents are deleted once catalogued. findings.tsv already carries 743 RSA rows (13 for RSA71), and the citation guard resolves against findings.tsv, not the documents. Before deleting, run `catalog-findings.py --check`. Then update the counts that testTheRecordsCountTheStore pins in docs/reviews/README.md.  **Done** (p1a ce8a0b70 and ef414596; 101 files, all in the store).
- **tests.md (1.86 MB) and issues.md** are renders of triage.db. Make it a standing agent rule never to Read them and to use the triagedb CLI instead. An option for later: render released MTs into `tests-archive-<version>.md`. That needs changes to triagedb's render and to testTheSuppliedManualTestFilesAreWhatTheySay.  **Done in Phase 1** (2026-10-19, `2ff1eecd`): closed entries live in `tests-archive-<release>.md`, placed by `triagedb.home_for`; tests.md went from 1.86 MB to 56 KB.  Each release adds its row to `triagedb.ARCHIVES` (docs/RELEASE.md, step 13).
- **behaviour.md** stays one file, because 98 citations in src/ and test/ name its sections. Trim the argument history inside the rules; keep the rule and its quote from Adam. Add a section index with line ranges at the top. §5 (1,118 lines) is the first target.
- **Comment prose vs the store.** Comments currently tell the review history. The finding store already holds that history (`what`, evidence and commit, for each ref). The citation guard needs only the **id** to stay resolvable, so trimming prose and keeping the id keeps the guard green. Dropping a dead id makes the ratchet fail (DEAD_CITATIONS=44) and ask for the number to be lowered, which is intended.
- **An agent code map.** A generated `docs/CODEMAP.md` (from a script like outline.py) giving each concern's file and entry methods, and the generated-code ranges to skip. Regenerated per release so it cannot go stale.

**The comment rule** (approved 2026-10-18; amends the 2026-09-07 "self-contained" ruling rather than reversing it):
- A comment states the **current** rule and why the code meets it. It must make sense on its own, ideally in 8 lines or fewer.
- History belongs in the finding row its id resolves to, or in git: what it used to do, which round found it, who said "option 1" on which date.
- Keep one id per claim, and quote Adam only where his wording is the rule.
- A block over 15 lines must either be a javadoc contract or move to behaviour.md.
- New code follows the rule at once. Existing code is trimmed in comment-only commits.

## 5. Proving behaviour is unchanged

1. **Baseline.** A full battery on the step's starting HEAD, with per-class results recorded. The 3.0.2 release battery can serve for the first step.
2. **Comment-only commits.** Strip comments and compare the token streams (a script), and compare `javap -c -p` output with line-number tables removed. Identical bytecode means no behaviour change by construction. Run only the bookkeeping classes (javadoc, citation, records count, message key) per commit, and a battery per phase. This depends on no test asserting comment text; the battery is the check on that assumption.
3. **Extraction commits.**
   - `javap -p` member diff of each touched class: only the planned moves.
   - `javap -public` diff of Layout, MarklinControlStation, Locomotive, Point, Edge, model/View and every public gui class: must be empty.
   - `git diff -- '*.properties'`: must be empty.
   - The reflection and source audit (§2).
   - grep battery logs for `NoSuch(Field|Method)Exception`.
   - `one.sh` on the touched tests, then a battery at the end of each tier.
4. **Bookkeeping to update in the same commit.**
   - Monitor `ALLOWED` keys (`File#member`).
   - Keep helpers inside `SURFACES` (gui and automationui), or extend the list.
   - Keep `synchronized` declarations in Layout.java, or make `LAYOUT` a list.
   - Javadoc `ORPHANS_BY_FILE` and the 86 total.
   - Extend testNoSelfRecursiveWrappers to the new delegators.
   - The window census (testEveryWindowWearsTheIcon and testEveryWindowWearsTheApplicationsLook) if a helper creates a window.
   - build.xml, battery membership and "Used by" lists for test splits.
   - DEAD_CITATIONS when trims drop dead ids.
   - Re-run catalog-findings.py so `cited_by` follows the moved code. No test guards this.
5. **Mutation spot checks.** For each moved cluster, apply two or three of the existing MUTATION notes (there are 1,506) at the new location and confirm they go red. Mutate methods, not classes; make sure mutate_wt's output folder exists.
6. **Review.** An Opus fan-out round (FANOUT.md) over each tier's diff.
7. **Manual tests (Adam's answer to question 7).** Each phase that moves code ends with MTs for the features it moved, one MT per feature, for Adam to run: icon crop, update check, backup, route run and find, layout duplicate and combine, timetable, Return Home, and whatever else the phase touched.

## 6. Phased plan for 3.0.3

Each phase can be released on its own and ends with a battery. Workers use separate worktrees. Shared bookkeeping files (the monitor guard, the javadoc guard, the citation ratchet, build.xml) are edited by one integrator, never by two workers at once.

| Phase | Work | Workers (parallel where files differ) | Effort |
|---|---|---|---|
| 0. Split-proof tests | `test/support/SourceOf` (finds a declaration anywhere in src/ and asserts it is unique; `SourceOf.all()` for negative assertions). Convert the scanning tests. Commit the audit, javap-snapshot and comment-strip-compare scripts.  Point tests that copy a rule of the program at the program; make the throttle claim steady. | Opus ×1 for the helper and scripts first; then Sonnet ×3 (core/, regression/, ui/); Opus reviews the negative-assertion conversions. | 3-4 sessions, 1 battery |
| 1. Dead weight | Delete docs/reviews-2026-09-25 (and each later round's documents once catalogued), with `catalog-findings.py --next` and the full-rebuild guard first; the dead test branch.  (The dead methods and `CustomActionEvent` are already gone; the legacy editor's constants and keys went with OB-359.)  Fix README counts.  Archive released MTs out of tests.md (triagedb's render and testTheSuppliedManualTestFilesAreWhatTheySay change with it). | Haiku ×1, Sonnet ×1 for the MT archive, then an integrator | 1 session, 1 battery |
| 1a. A faster battery | Adam (2026-10-19): "a phase of making the battery run faster".  See **1a** below the table. | Opus ×1 for the lanes; Sonnet ×2 for the slowest classes | 2-3 sessions, 2 batteries (before and after, compared claim by claim) |
| 1b. Hollow tests | Adam (2026-10-19): "a sweep for tests that look like they are testing something but aren't".  See **1b** below the table. | Opus ×1 for the scan script; Sonnet ×3 (core/, regression/, ui/); Opus reviews every claim changed | 3-4 sessions, 1 battery |
| 1c. The tracker becomes a database | Adam (2026-10-19): "why aren't we storing all tests in a database, why have md files at all?  I made this ask before."  He chose "DB only, plus a text dump", now.  See **1c** below the table. | Opus ×1; then an integrator cuts over with the triage app closed | 2-3 sessions, the bookkeeping classes |
| 2. Comment trim | Comment-only, bytecode-verified, one file per worker: TrainControlUI, Layout, AutonomySession, AutonomyEditorPanel, then the next six. Expect a 30-40% cut of comment lines in the giants. | Sonnet ×4 in parallel; Haiku integrator for ratchets and orphan counts | 4-5 sessions, 1 battery |
| 3. Low-risk extractions | TrainControlUI Tier 1 (five clusters, one commit each, **one worker in sequence**); AutonomySession `SessionImport` and `SessionRuns`; the testAutonomyDiagramSession split | Sonnet ×3, one per file group | 4-5 sessions, 2 batteries |
| 4. Medium | TrainControlUI Tier 2; AutonomyEditorPanel menus, prompts and mass-assign; LayoutEditor selection and undo; Layout explanations, standing trains and fit (Opus) | Sonnet ×3 and Opus ×1 | 6-8 sessions, 2 batteries |
| 5. Core | TrainControlUI Tier 3; the `SettledQuestion` base class.  Deferred on 2026-10-19 until Phase 4 was validated; back in 3.0.3 on Adam's word of 2026-10-24: "We can now include the rest of the plan for 3.0.3, as long as the current state is already committed." | Opus | 4-6 sessions, 2 batteries plus a fan-out round |
| 6. Coverage of what the user does and what comes of it (beside Phases 2-5) | Adam (2026-10-19): "increase test coverage (meaningfully, through actions the user would take)"; "Sooner sounds better.  Don't limit this to UI interactions, it can also be state outcomes."  See **6** below the table. | Opus ×1 for the inventory; Sonnet ×4 in parallel, one area each; an integrator folds | 4-6 sessions, 1 battery |

That is about 32-43 worker sessions and 13 batteries.  Phases 1a and 1b come before the comment trim and the
extractions on purpose: every later phase ends with one or two batteries, and every extraction leans on the tests
being real.

**1a. A faster battery.**  Battery 101 (2026-10-18, 339 classes) took 90 minutes from start to finish:
- 74 minutes inside the tests, and about 16 starting a JVM for each class.
- 145 classes take under 5 s, 158 take 5 to 30 s, and 36 take longer.  The ten slowest take 26 minutes, a third of
  the test time: testNoSetupEditDuringARun (317 s), testTheImportDoorReadsAnOldFile (302 s), testTheActiveTickPausesATrain
  (199 s), testAutonomySimulationSanity (155 s), testTheAutoTabWaitsForTheTrains (124 s), testReturnHomeOnRealLayout
  (108 s), testNoSetupEditWhileARouteRuns (107 s), testACompulsoryTurnIsNotAQuestion (77 s), testATrainIsDispatchedOnce
  (75 s) and testTrainsComeHomeToTheirPlatforms (69 s).
- One class runs at a time, under one lock shared by every worktree, so a battery also stops every worker's runs.

The work:
- **Lanes.**  Run the classes in several JVMs at once, each with its own data folder (`traincontrol.dataDir`), its own
  ports for the mock Central Station, and its own preferences node.  Classes that need the keyboard focus or a window
  in front stay in one serial lane.  The lock becomes a count of lanes, so a worker's single run can still get in.
- **The slowest classes.**  Find where their time goes.  Replace fixed sleeps with waits on the condition that ends
  them; where a test waits out a program timer, it asks the timer (Phase 0 already points many at the program's
  constants).  A test-only speed setting inside the program is a production change, so it goes to Adam first.
- **The proof.**  The old battery and the new one run on the same commit.  Their results are compared claim by claim,
  not class by class: the same claims ran, and the same ones passed.  A sample of mutations still turns red.  The goal is
  measured, not promised.

**1b. Hollow tests: claims that look like they test something but do not.**  Phase 0 found several, and the Phase 0
review found more:
- a needle that matched only in a method about 3,700 lines away;
- a `matches(".*x.*")` that could never match across line breaks;
- a claim skipped by an `if (!found.isEmpty())` guard after its method was deleted;
- colour matchers asserting zero pixels of a colour, which a changed colour would satisfy;
- a pixel check that asserted the test's own input back.

The sweep looks for these shapes:
- needles that never occur where they are looked for;
- claims behind a guard that can skip them without failing;
- loops over a list that can be empty, with no floor;
- zero or absence claims with no positive control beside them;
- asserts inside a `catch`, or a `catch` that swallows the assertion error;
- the 43 `NoSuch*` catches the reflection audit listed;
- waits that time out and then assert nothing, and Swing asserts queued with `invokeLater` after the method has
  returned;
- methods with no assert at all;
- skips the battery counts as green (battery 101 had two classes that "tested nothing").

`docs/tools/hollow-scan.py` flags candidates (`--help` explains each shape, `--json` for tools), and flags each of the five cases above when it is put back on a copy.  Workers then check each claim the way Phase 0 did:
1. mutate the production code the claim is about;
2. see the claim go red;
3. revert.

A claim that stays green is rewritten to ask the program (it says what it said before, and now it is true), or deleted
if nothing in it can be made real.  Each one is listed for Adam.  Claims over the four giants come first, because the
extractions lean on them.  An Opus reviewer checks every claim that changed, as in Phase 0.  The scan script stays,
and a new hollow shape found later is added to it. **Main risks:**
- feature work on TrainControlUI during Phases 3-5 would conflict: 3.0.3 is refactor-only and that work is paused (Adam's answer to question 6);
- negative source assertions going vacuous (Phase 0 addresses this);
- concurrency changes in Layout;
- swallowed reflection failures;
- line numbers in findings.tsv `file:line` evidence going stale (historical only, acceptable).

**1c. The tracker becomes a database.**  Adam asked for this on 2026-08-25 (*"for the triage app, lets convert it to
use a database and then add apis to check, update, and file dispositions/cases"*), and it was only half done.
`triagedb.py` put a database and an API in front of the markdown, but the markdown stayed the record:
- the triage app builds a throwaway store from `tests.md` and `issues.md` every time it loads, and writes Adam's
  comments into the markdown;
- `triage.db` is refreshed from the markdown by `sync`, and every write renders the store back over it.

A stale copy erased nine of Adam's comments that way on 2026-09-15, which is why every tracker script must sync first.
On 2026-10-19 Adam asked *"why aren't we storing all tests in a database, why have md files at all?"* and chose a
database with a text dump.

The work:
- **One record.**  `triage.db` holds every test, verdict, comment, issue and finding, and is the only thing anything
  reads.  The triage app and every script read and write it through the `triagedb` API.  Nothing is parsed from
  markdown again, so there is no `sync` and no stale copy.
- **A text dump for git, never read back.**  Each write also writes a plain-text export, deterministic and sorted, in
  which a new comment is a small added hunk.  Git keeps the history readable and a merge stays possible, which a binary
  `triage.db` cannot give.  Nothing reads the dump back, except the Java tests, which cannot open SQLite without a
  driver.  `findings.tsv` folds into the same scheme or stays as it is.
- **The markdown goes.**  `tests.md`, `issues.md` and the four `tests-archive-*.md` (Phase 1's archive, made obsolete
  by this) are deleted, with the archive code and RELEASE.md's step 13.  Their history stays in git.  An `MT-###` or
  `OB-###` cited anywhere resolves through the CLI and the app (`triagedb show MT-642`).
- **The migration is a function.**  It imports the markdown into the database once and is re-runnable.  The
  integrator runs it on the live files at cut-over, with the triage app closed, because Adam may add comments in the
  meantime.  The proof: every entry, every comment and every `**Adam,` line is in the database and the dump, counted
  before and after; and `show` gives back each entry's text exactly as written.
- **What changes with it:** the triage app's reading and writing; every `triagedb` writer; the Java tests that read
  the tracker files (`testTheSuppliedManualTestFilesAreWhatTheySay`, the citation and records guards);
  `docs/manual-tests/README.md`; RELEASE.md; FANOUT.md's records step; and the agent notes in the house rules and
  memory.

**Done (2026-10-19, branch p1db):** `triage.db` is the record and `dump/` its text, one file per table; the markdown
and the archive code are gone; `triagedb.py migrate` imported them, and `docs/tools/prove-tracker-migration.py` found
nothing lost (772 entries, 1,957 comments, 861 verdicts, 889 `**Adam,` lines, 30 Inbox items, 491 receipts).

**6. Coverage of what the user does and what comes of it.**  Adam: "This can be done in parallel, report, and fold
later"; then "Sooner sounds better.  Don't limit this to UI interactions, it can also be state outcomes."
- **When.**  The inventory is built during Phase 1b, which sets the rule each new claim must meet.  The tests are then
  written beside Phases 2-5, as a net under the extractions, not after them.  The areas the extractions reach first come
  first: TrainControlUI's Tier 1 clusters before Phase 3 starts on them.  The new tests drive the program from outside
  and name no member, so a split does not move them.  Their runs share the test lock with the phases' batteries, which
  is one more reason for Phase 1a first.
- **The inventory.**  Two lists, one row per entry, each recording which tests exercise it and how:
  - **What the user does.**
    - every menu item, button and tab;
    - every keyboard shortcut;
    - every gesture on the diagram (click, drag, right-click, paste);
    - every dialog's answers;
    - every editor's tools.

    A test counts when it drives the real component (a click, a key event, a menu item's action), not when it calls
    the handler by reflection.  The reflection audit lists the handlers tests reach by name; that is where most of
    the gap shows.
  - **What happens to the state, whoever starts it.**  Not only what a click starts: a route that runs, an autonomy run,
    a timetable, a return home, a feedback or an echo arriving from the railway, an import, a save and a reload, a
    restart, a power cut, a layout switch.  For each, the state that should result:
    - where each train stands and which way it faces;
    - which track is claimed;
    - the switch and signal positions;
    - the setup and the locomotive database as saved;
    - the statistics.

    A test counts when it starts the event as the program would meet it (through the API, the mock Central Station's
    packets, or the file on disk) and checks the resulting state.

  A JaCoCo run of the battery then shows the code both lists reach that no test does.  JaCoCo is a test-only jar, added
  with Adam's OK.  The inventory is the phase's first report, ranked by how often the action or event happens and by how
  much code it reaches untested.
- **Meaningful means a real outcome.**  Each new test acts as the user, or as the railway, would, and asserts what
  results:
  - the window's state;
  - the command that reaches the railway (through the mock Central Station and `WireRecorder`);
  - the model's state after it settles;
  - the file that is saved and what reads back from it;
  - the sentence shown.

  "Did not throw" is not an outcome.  Every new claim goes red under a mutation of the code it is about (the 1b rule),
  runs on the sandboxed railway, and carries the usual bookkeeping (census, build.xml, Used by).
- **In parallel, then reported.**  Workers take one area each and work in parallel:
  - the main window and locomotives;
  - the layout editor and diagram;
  - the autonomy editor and its runs;
  - routes, timetable and the railway's own events.

  Each reports what it covered and every defect it found.
- **Folded later.**  A defect a new test exposes is filed (OB, through triagedb) with the test attached, not fixed in the
  phase: the release stays refactor-only, and a red test does not join the battery.  The test is folded in with its
  fix in a later release.  The green tests are folded into the battery by one integrator, who owns the shared
  bookkeeping files, once per phase they run beside.  The final report gives the inventory's coverage before and after.

## 7. Questions, with Adam's answers (2026-10-18)

1. Is the public Java API frozen?  **"Not frozen, but keep the methods as noted above"**: every public method on the API classes stays; FR-138 adds example code.
2. The comment rule in §4, and comment-only trims of existing code?  **"Yes"**.
3. Delete docs/reviews-2026-09-25 now, and each round's documents once catalogued?  **"Delete"**.
4. Convert the ~78 source-reading tests to a shared `SourceOf` helper?  **"Yes"**.
5. The two superseded generated menu items?  **"Done"** (`a4dac3dd`).
6. 3.0.3 refactor-only, with feature work on TrainControlUI paused during Phases 3-5?  **"Yes"**.
7. An MT smoke checklist, or battery plus mutations?  **"make MTs for validation"**: see §5, item 7.
8. behaviour.md one file, and released MTs archived out of tests.md?  **"yes to both"**.
9. New helper classes in gui/ and automationui/, or a subpackage?  **"keep them in the current packages"**: the monitor guard's `SURFACES` stays as it is.

## 8. Next steps, after 3.0.3

Adam (2026-10-24): "We can now include the rest of the plan for 3.0.3".  Phase 5, FR-138 and the harsher comment
pass below are back in 3.0.3; the four questions were taken at their conservative answers (see the log for Adam).

Adam (2026-10-20): "Defer remaining optimization/comment trimming items to next steps under the plan."  3.0.3 stops
at Phase 4, Phase 6, the comment trims and the Layout pilot; what follows is the queue for the next release.

**Also in 3.0.3, on Adam's word of 2026-10-24** ("Make the doctrine.  We should also study builder behavior to identify
redundant checks they make or questions they ask.  For DD-B9 and DD-B6, can we fix them? OK the retire the
open-questions file after that.  For behaviour.md, add them to the plan but don't work it yet, since there may be new
learnings.  Also, we should use US English.")
- **A builder doctrine** in `CLAUDE.md`: how to build here (one rule written once, tests through what the user does,
  lean briefs and runs), drawn from the lessons so far and from a study of the builders' own transcripts (redundant
  checks, questions the repository already answers).
- **DD-B9** (the editor's reachability walk re-applies the builder's four authored sets by hand) and **DD-B6** (the
  control-station start-up copied into about 90 test files), fixed; DD-B6 after Phase 5 has merged, since both touch
  the tests that name TrainControlUI's members.
- **Retire `docs/reference/open-questions.md`** once those two are done: its Part 1 rulings checked against
  behaviour.md, the Inbox count it carries dropped from `triagedb.py` and `testTheRecordsCountTheStore`, its two links
  fixed.
- **US English** everywhere: the English bundle, Readme.md, Automation.md and UI-standards.md now; comments and
  AutomationAPI.md after the workers on those files merge; behaviour.md with its compaction below.
- **Duplicated code** (Adam, 2026-10-24: "Do the split: same-rule copies and both bugs in 3.0.3.  File the rest as part
  of a plan to review for a future release.").  `docs/tools/dupscan.py` finds runs of 60 tokens or more that are the
  same up to names and literals (268 clusters on `c4509be7`, about 7% of `src/`); 116 were read and sorted into the
  same rule written twice (A, 31), repetition a helper would shorten (B, 25) and alike in shape only (C, 60).  Cluster
  numbers are the scan's ranks on that commit.  In 3.0.3, refactor-only, one place per rule:
  - OB-371 (cluster 114): the name prompt and the whole-length prompt become one, with MT-474's focus fix.
  - OB-372 (cluster 50): which copy of a square holds the train, written three times, one with OB-314's adjustment.
  - Autonomy: a square's name in a finding (4), dropping pointers to squares that are gone (15), the trains' squares
    (29), the square beside a side (102), a direction along a run's own tiles (120), the editor's arm mask (262).
  - Editors: the digits-only field (14), the tile menu's early returns (63), the findings row color (101), sorting
    findings into sections (248; the viewer, built but never shown, adds the graph's problems twice), the gray wash
    (113).
  - Elsewhere: shifting tiles in the layout editor (7), squares whose roads changed (27), building an export off the
    event thread (57), recording a placement in both stores (96), keeping an unreadable file aside (99), a string or
    an array field (35), the arrival suffix (81), a station choosable by any copy (138), `fileOf` (152), starting a
    detached process (192).
  - OB-373 (found merging cluster 96): the track diagram's Place saves a placement with no facing.
  - The six held back, on Adam's word of 2026-10-24 ("Do them all, I agree with the sentences."), pre-2.7.0 code
    included: the refusal while a run holds the setup, at 36 sites with 7 messages, in `RunGates` with two sentences
    ("Edits are not allowed while autonomy or routes are running." for an edit, "Wait for the active locomotives to
    stop." for a door that must wait) (45); the coalesced worker of CoveredTrackView, CaptionVisibility and
    ReturnHomeController (61, 80); a red switch key's look (43); the CS2 function list parse (38); the ordered JSON
    object (18); the image chooser's types (224).

**For the next release, on Adam's word of 2026-10-24** ("Move JaCoCo to the next release, along with the
behavior.md split, and tooling."): the behaviour.md split below, the JaCoCo rerun under Measurement, and the builder
tooling the study suggested (`newtest.py` for a new test class's bookkeeping, `redgreen.sh` for a claim's red and
green in one run, `env.sh` for a worker's environment).

**Next release: a comment pass on the tests** (Adam, 2026-10-24: "we can add that to the plan for the next release").
Comments are 33% of `test/`'s characters (about 1M of its 3M estimated tokens), and `testCommentsStayLean` counts
history and long blocks in `src/` only.
- Comment-only passes with `docs/tools/lean.py`, the 23 test files over 2,000 lines first (82,000 lines, 17,000 of them
  comments), Sonnet per file as for `src/`.
- Kept, as CLAUDE.md says for tests: what each claim asserts and why it matters, the one `MUTATION:` note per claim,
  and why a non-obvious wait or fixture was chosen.  Assertion messages are never edited.  Cut: history, review
  rounds, restated code, and narratives of how a bug was found.
- Proof: `comment-strip-compare.py` must compare the test classes' bytecode too (extend it if it compiles `src/` only),
  and every class whose comments changed runs, since a few tests read other files' source.
- Then `testCommentsStayLean` counts history and long blocks in `test/` as well, so the cut stays made.
- Expected: a 30-40% cut of test comment characters, about 300-400k tokens, roughly a tenth of what reading the tests
  costs.

**Next release: the gui package split** (Adam, 2026-10-24: "add the package split to the next release plan, with the
first step being reviewing the split for my approval").  `org.traincontrol.gui` holds 79 classes, 27 of them
package-private, with about 450 package-private members used across its files (TrainControlUI 143,
AutonomyEditorPanel 99, LayoutEditor 47): a member used across a new package line has to become public.
1. **For Adam's approval first:** a read-only script proposes groups (leaf widgets and dialogs; locomotive dialogs and
   icons; updates and backup; the autonomy editor; the layout editor) and counts, per group, the members that would
   have to become public.  Nothing moves before Adam picks the groups and the threshold.
2. The approved groups move by script (`git mv`, the package and import lines in `src/` and `test/`, the `.form`
   files with their classes, the `Class.forName("org.traincontrol.gui...")` strings in tests and the paths in docs),
   proved by `javac` and by `javap` differing only in package names.  TrainControlUI and AutonomyEditorPanel stay in
   `gui`: Adam's two preference nodes are keyed by their package.
3. At the start of the release, before other branches are open, so the move does not collide with them.

**Next release: duplicated code to review** (from the 3.0.3 scan; rerun `dupscan.py` first, as the numbers move):
- A ratchet test (Adam, 2026-10-24: "add the ratchet to the plan"): a `regression` class, in the shape of
  `testCommentsStayLean`, that counts copy groups of 60 tokens or more in `src/` and fails when a new one appears or a
  group grows, so every builder sees a new copy, not only the merge check.  Port `dupscan.py`'s tokenizer to Java (it
  is about 60 lines) rather than call Python from a test.  Since 3.0.3 the orchestrator runs `docs/tools/dupcheck.py`
  on each branch before merging it: PARTIAL (some copies of a group changed, not the others) and NEW (a new copy).
- Same rule, in code from before v2.7.0, so each needs Adam's word: the "a run holds the setup" refusal at 11 sites,
  which belongs in `RunGates` (45); the ordered JSON object built by reflection in 7 places (18); the CS2 function
  list parse (38); the switch key's colors (43); the image chooser (224).
- The coalesced worker of CoveredTrackView, CaptionVisibility and ReturnHomeController (61, 80): the `SettledQuestion`
  worker kept it per class (Return Home's re-ask writes the subject again, and caption visibility settles unlocked);
  the scan reads it as one `CoalescedAsk` of about 70 lines.  Decide with both readings.
- Repetition a helper would shorten: the settings tab's 12 slider handlers (1), walking the built JSON (3, 47), the
  bulk-clear items and actions (6, 20, 28), the builder's emitted names (13), the icon marks (26, 37), a station's
  list replaced (46), a selection highlighted (51), reduced squares matching a test (59), a Ctrl key on the hovered
  square (36), a text place (70), a heading label (79), one side of N asked (156, 171), a bold menu heading (255).
  Before v2.7.0: point keys typed and checked (8), the popup listeners (30), the right-click items (69), the text
  canvas (71), the system command bytes (74), the focus-timer field (131), the tile refresh registry (209).
- The 152 clusters below rank 80 with all their copies in one file were not read.
- No claim covers the stale-answer discard in CaptionVisibility and ReturnHomeController: removing it from both
  survived (the coalesced-worker merge's mutation M4, 2026-10-24).  A claim that an answer for a subject that has
  since moved is not painted.
- Left by `open-questions.md` (retired in 3.0.3): Adam asked to keep an eye on route choice not being reproducible
  between runs (behaviour.md §5a states the fact; nothing watches it); its §2b's "one fixture with a curve and a
  switch" and "the two menus NAME a square alike" were not confirmed done.  DD-A1, DD-C6, DD-C7 and DD-C8 stay in
  OB-331 for 3.1.0.

**Next release: behaviour.md** (239 KB, 2,884 lines; about 60k tokens to read whole).  Split by functional area into
one file per area with `behaviour.md` (to be `behavior.md`) as a short index (area, section, main classes, main tests);
compact each to the rule, its why and its id; update the ~90 citations in code and tests in the same commit; prove
with a script that every OB/MT/FR id and every quote of Adam survives.  Held until the current work's learnings are in.

**Code**
- **Phase 5** (TrainControlUI Tier 3, the `SettledQuestion` base class), deferred on Adam's word of 2026-10-19.
- **FR-138**, the API examples.

**Comments, to the strict rule** (CLAUDE.md, "Comments stay lean")
- Done in 3.0.3, all comment-only with identical bytecode:
  - the pilot on Layout.java (`88856f02`..`d5bd4235`, Opus): 11,876 lines to 9,897, comment lines cut 44%, its 434
    review ids gone;
  - AutonomySession, AutonomyEditorPanel, LayoutEditor and TrainControlUI (`f671a519`..`043ef48b`, one Sonnet worker,
    on Adam's word of 2026-10-21): comment lines cut 28%, 19%, 13% and 10%; LayoutEditor's 100 review ids gone.  The
    cuts are smaller than the pilot's: Sonnet kept more of the why, and about 12,800 of TrainControlUI's lines are
    Adam's own, which the rule leaves alone.
- Next: a harsher pass, Opus as in the pilot, on the blocks of 10 lines or more that are not Adam's and still stand:
  AutonomySession 62, TrainControlUI 38, AutonomyEditorPanel 35, LayoutEditor 25.
- Questions for Adam before it:
  - Lock-order and thread notes were allowed about 5 lines in both passes; confirm, or hold them to 3.
  - Seven of Adam's commits carry no Claude trailer but read as Claude-written (`902c647c`, `47147436`, `28ef127a`
    and others).  The rule leaves their comments alone; Adam can name the ones that are really Claude's.
  - The 67 review ids left are on Adam's own lines (ManualReversalPrompt 10, AutonomySession 5, TrainControlUI 2 and
    test files) and stay pinned unless he says they may go.
  - Commit the guarded edit helper both passes used (`lean.py`: edits only comment lines, refuses code and Adam-owned
    lines) to `docs/tools/`, so every worker uses it.
- Every pass that drops a dead id lowers `DEAD_CITATIONS` and takes the id off the roll (`triagedb.load_dead`, then
  `render_findings`).  `testEveryCitationResolves`'s `cited.size() > 300` (ids in code and strings as well as
  comments) still holds with LayoutEditor cleared; lower it if a later pass takes the count under.
- Found by the test pass (issue 28): `HomeStaging.canRest`'s FR-001 check never decides anything, because the
  planner's path search already refuses a held-back square.  Removed on Adam's word of 2026-10-24 ("canRest's
  redundant check can go").

**Ratchets as they stand at 3.0.3** (each may only go down)
- `testCommentsStayLean`: history 319, long blocks 54, review ids 67.
- `testJavadocsAreAttached`: 16.
- `testEveryCitationResolves`: 20 dead citations.

**Tests**
- The test pass of issues 25 and 27-30 is done (`d9021d99`, `docs/refactor-3.0.3-for-adam.md`).  Issue 30's three
  classes read the operator's own state for the main folder (his saved keys, his always-on-top setting, his
  remembered window size); a class that copies his settings sets what it needs, never toggles it.
- Issue 24 (the main window not put back on top, once under lanes in a worktree) is settled: it is issue 30's
  always-on-top claim reading Adam's own setting, which he changed during the run, fixed in `d9021d99`.

**Measurement (next release)**
- A JaCoCo rerun (stopped on Adam's word of 2026-10-20), outside the repository as before; the 2026-10-19 run gave
  81.5% of lines.

