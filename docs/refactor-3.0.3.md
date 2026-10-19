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
| 1 | Layout files and pages (duplicateOrRenameCurrentLayout, combineLinkedPages, writeIndexAndKeepLinksAimed, initializeEmptyLayout, unzipFile, downloadCentralStationLayout, createNewLayout, copy, paste and clear page) → `LayoutFileActions` | ~1,280 | 4/4/0 |
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
  - `TrainFit`: whyTooLongForThisRoute and measuredRoomAtTheEndOf; ~12,000-13,100.

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

- **docs/reviews-2026-09-25/ (97 files, 3.7 MB): delete it.** The folder's own README says each round's documents are deleted once catalogued. findings.tsv already carries 743 RSA rows (13 for RSA71), and the citation guard resolves against findings.tsv, not the documents. Before deleting, run `catalog-findings.py --check`. Then update the counts that testTheRecordsCountTheStore pins in docs/reviews/README.md.
- **tests.md (1.86 MB) and issues.md** are renders of triage.db. Make it a standing agent rule never to Read them and to use the triagedb CLI instead. An option for later: render released MTs into `tests-archive-<version>.md`. That needs changes to triagedb's render and to testTheSuppliedManualTestFilesAreWhatTheySay.
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
| 2. Comment trim | Comment-only, bytecode-verified, one file per worker: TrainControlUI, Layout, AutonomySession, AutonomyEditorPanel, then the next six. Expect a 30-40% cut of comment lines in the giants. | Sonnet ×4 in parallel; Haiku integrator for ratchets and orphan counts | 4-5 sessions, 1 battery |
| 3. Low-risk extractions | TrainControlUI Tier 1 (five clusters, one commit each, **one worker in sequence**); AutonomySession `SessionImport` and `SessionRuns`; the testAutonomyDiagramSession split | Sonnet ×3, one per file group | 4-5 sessions, 2 batteries |
| 4. Medium | TrainControlUI Tier 2; AutonomyEditorPanel menus, prompts and mass-assign; LayoutEditor selection and undo; Layout explanations, standing trains and fit (Opus) | Sonnet ×3 and Opus ×1 | 6-8 sessions, 2 batteries |
| 5. Core (or 3.1.0) | TrainControlUI Tier 3; the `SettledQuestion` base class | Opus | 4-6 sessions, 2 batteries plus a fan-out round |

That is about 20-28 worker sessions and 9 batteries. **Main risks:**
- feature work on TrainControlUI during Phases 3-5 would conflict: 3.0.3 is refactor-only and that work is paused (Adam's answer to question 6);
- negative source assertions going vacuous (Phase 0 addresses this);
- concurrency changes in Layout;
- swallowed reflection failures;
- line numbers in findings.tsv `file:line` evidence going stale (historical only, acceptable).

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
