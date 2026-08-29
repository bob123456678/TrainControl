# Test suite review — are the tests themselves able to fail?

**Status:** open

**Prefix:** `TST` — cite findings from here as `TST-A1`, `TST-B2`, and so on. Not taken elsewhere; the
prefixes already in use are CR, FB, FBR, FSR, FV, IAR, ISD, LD, RA, SV, WK.

**Reviewed:** branch `autonomy-diagram-r0`, HEAD `eac0e392`, on 2026-08-28. 124 test classes, 1,176
`@Test` methods, ~66,000 lines under `test/`. No source or test file was changed and nothing was run —
this is a reading pass, and every claim below was checked against the production file it is about.

**Scope.** Not the production code. The single question asked of every test was: *would this fail if the
thing it claims to protect broke?* Each finding therefore names a concrete mutation to the PRODUCTION
code that the test would still pass. A finding with no such mutation is not in here.

**Severity is by what the hollow test fails to protect**, per
[README.md](README.md) — a test that would not catch a defect on the running railway or a silent loss of
Adam's setup is A, whatever the test looks like.

**Method.** Every class was read in full, in six parallel passes, and every load-bearing claim was then
re-derived by hand against `src/`. Where a test scans production source, the anchor string was looked up
in the file it names: **no scan in the suite is stale at the anchor level** — every pattern matches today.
The scanning defects below are all of a different kind, and that distinction is worth keeping.

**The working tree moved during this review, and that matters for reading it.** Another session was
editing `src/` and `test/` while these passes ran — `Layout.java`, `TrainControlUI.java`, `LayoutGrid.java`,
`MarklinControlStation.java`, `AutonomyMenu.java`, the eight `messages*.properties`, `StartupSplash`,
`LoadingSpinner`, `TrainControlUI.form`, and four test classes all changed between 20:00 and 21:53 on
2026-08-28. Every finding that names one of those files was **re-derived against the tree as it stood at
21:55**, and all of them still hold; line numbers in this document are that later tree's. One finding —
TST-A3 — was written against a test method that has since been replaced, and is restated against its
replacement, which has the same gap. Findings in files that did not move are as read. If more time has
passed, re-check the line numbers before acting; the claims are anchored on strings, not offsets.

**55 findings: 15 A, 23 B, 17 C, and 27 D entries recording what was checked and holds.** The single
dominant shape, at eleven instances, is the one this repository has met repeatedly in production: the
rule is lifted out and tested, and nothing covers the call site. Three of the A findings are silent loss
of Adam's setup (TST-A1, TST-A13, TST-A15); the rest are guards on the running railway that a plausible
edit would remove without turning the suite red.

---

## A — High

| | Finding | Disposition |
|---|---|---|
| A1 | `testEveryKeyParseAutoReadsIsAlsoWritten` omits five keys `parseAuto` reads | open |
| A2 | `testEverySensorTheLegacyConfigUsesIsDerived` compares a set with itself | open |
| A3 | The tail-clearing gate is checked by ordering only, so its body can be emptied | open |
| A4 | The two-minute soak asserts a counter that simulate mode pins at zero | open |
| A5 | The "alert at most once" test reads a monotone latch, not a call count | open |
| A6 | The bulk-edit test passes when only one tile of the selection is edited | open |
| A7 | `distinctDestinations` has two call sites and no test anywhere | open |
| A8 | The route editor's four lock guards on row ACTIONS are uncovered | open |
| A9 | Three caption-undo tests drive an API the editor does not use | open |
| A10 | The locomotive-rename repair is tested by the test making the call itself | open |
| A11 | `DiagramMonitor.invalidate()` is called by no test; the test named for it reads a field initialiser | open |
| A12 | `testTheAuthoredFlagDoesNotLeakOntoThePlainCopy` asserts nothing when the loop is empty | open |
| A13 | `LayoutEditor.setupShift` — the coordinate mapping Adam reported — is executed by no test | open |
| A14 | `planBulkLine`'s four call sites are uncovered; the tests call the planner themselves | open |
| A15 | The capture-before-rename ORDER is asserted nowhere, and its javadoc points at a test that does not check it | open |

### TST-A1 — the export/import parity test's key list is incomplete, and says it is not

`test/core/testAutoLayout.java:1140-1194`, `testEveryKeyParseAutoReadsIsAlsoWritten`.

The javadoc criticises an earlier hand-listed version and claims "the source of truth is the READER" —
that every key `parseAuto` looks for on a point is in the array at 1174-1179. It lists thirteen.
`Layout.fromJSON` (`src/org/traincontrol/automation/Layout.java:6442-6830`) also reads **`terminus`,
`reversing`, `blockedBy`, `excludedLocs` and `loc`** off a point. All five are written today by
`Point.toJSON` (`src/org/traincontrol/automation/Point.java:941, 946, 1031, 1055, 1066`) and none is
checked.

**Mutation that survives:** delete `jsonObj.put("terminus", this.isTerminus);` at `Point.java:941` — or
the `reversing`, `blockedBy` or `excludedLocs` write beside it. Every terminus flag, reversing point,
arrival restriction and per-point exclusion is then silently dropped on export and does not come back on
import. This test, which exists to catch precisely that, stays green.

This is the highest-consequence finding in the review: it is silent loss of the setup, through the door
the test was built to guard.

### TST-A2 — the legacy-sensor gate is an identity comparison

`test/core/testAutonomyDiagramSampleLayout.java:634-682`, helper `derivableSensors()` at `:252-262`.

`derived` is built at `:637-641` by looping over `reducer.getPoints().values()` collecting `getS88()`.
`inScope` is set at `:647` to `derivableSensors()` — **the same loop over the same map**. After
`if (derived.contains(s88)) continue;` every surviving sensor is by construction absent from `inScope`
too, so it lands in `outOfScope` and `missing` is unconditionally empty. `assertTrue(missing.isEmpty())`
cannot fail.

The helper's own javadoc says it returns "every sensor that exists on a page autonomy is actually looking
at"; its body returns derived points. That doc-against-body split is the thing
[README.md](README.md)'s "verify the layer you are actually claiming about" is written for.

**Mutation that survives:** make `GraphReducer.reduce()` derive no Points for one page. Every legacy
sensor on that page moves from `missing` to `outOfScope`. In fact **no** reduction failure whatsoever can
fail this test — and the class javadoc calls this "the one direction of difference that is always a
defect."

Weaker sibling: `testEveryLegacyConnectionIsStillReachable:697-750` filters legacy edges through
`derivable`, built from the same reducer it then queries, so a page that fails to reduce takes its own
connections out of scope rather than failing.

### TST-A3 — the tail-clearing gate is proved present and ordered, not effective

`test/core/testTrainTailClearsEdges.java`. **This file was rewritten during the review** — see the note
under the header. Both versions have the same gap, so the finding is stated against the version now in
the working tree, `testTheClearAndTheUnlockAskTheSameQuestion:165-216`.

The current test is careful, and more so than the one it replaced. It proves all three anchors are
`>= 0` before ordering them; it asserts `asks < clears && asks < unlocks`, so the gate is in front; it
**counts** rather than positions, requiring exactly one `.add(path.get(waiting[0]))` and exactly two
occurrences of `tailHasProvablyPassed(` — the definition and one call — under a comment explaining that a
second write added in front of the gate would leave the positioned statement where it was; and it asserts
the looser companion rule `tailMayStillBeOn` has not come back. All anchors are live in
`src/org/traincontrol/automation/Layout.java` today (`:4856`, `:4877`, `:4889`).

Every one of those checks is about the gate's *presence, position and uniqueness*. None is about whether
it does anything.

**Mutation that survives:** at `Layout.java:4856-4860`, keep
`if (!tailHasProvablyPassed(travelledOnThisPath, waiting[1], loc.getTrainLength()))` exactly as written
and empty its body — remove the `continue;`. `asks`, `clears` and `unlocks` are unmoved and still
ordered; `adds` is still 1; `gates` is still 2; `tailMayStillBeOn` is still absent. The test is green and
the unlock is ungated: another train can be routed onto track this one is still standing on, which is
what the file exists to prevent.

The rule itself is well tested next door (`testTheTailHoldsTheEdgeUntilItHasPassed:48`,
`testAnUnmeasuredPathDoesNotHoldForEver:81`, `testUnmeasuredTrackIsNotProof:104`) and
`testTheClearingLoopAsksTheRule:139` checks the arguments the call passes. What no test does is *run*
the clearing loop and observe that an unproven edge is held. That is the difference between a scan and a
test, and this is the one place in the suite where the difference is a locked rail.

### TST-A4 — the soak test's central assertion is unfalsifiable in the mode it runs in

`test/core/testAutonomySimulationSanity.java:147-229`, `testSimulatedAutonomyRaisesNoWarning`;
assertions at `:182-184` and `:227-228`.

The fixture sets `"simulate": true` (`test/autonomy_sanity.json:143`).
`Layout.configureAndLockPath` returns at `Layout.java:2594-2598` —
`if (this.simulate || !PATH_INTEGRITY_VALIDATION) return true;` — **before** `validatePathActuation`.
`handleMisconfiguredPath` is therefore unreachable and `pathValidationFailureCount` is pinned at 0 for
the whole two minutes. The `PATH_VALIDATION_ALERT_THRESHOLD = Integer.MAX_VALUE` setup at `:79` is inert
for the same reason. The class javadoc acknowledges the bypass and then asserts its consequence as if it
were an outcome.

**Mutation that survives:** delete the entire guard at `Layout.java:2604-2609`, or make
`validatePathActuation` return `true` unconditionally, or set `PATH_INTEGRITY_VALIDATION = false`. The
two-minute run stays green.

**Second, on the same test:** `MIN_TOTAL_ACTUATIONS = 20` is asserted against
`acc.getNumActuations()` with **no baseline taken**. `MarklinControlStation.newAccessory` carries over
the actuation count from whatever is already at the address, and `setUpClass` claims MM2 addresses 1-7 —
exactly the addresses a real layout occupies, in a database `init()` restores from the operator's own
`LocDB.data`. **Mutation that survives:** remove the accessory-command loop from `configureEdge` so
autonomy never throws a switch; in simulate mode `configureAndLockPath` still returns true, trains still
run, and `totalActuations` is still >= 20 from the carried-over counts. Fix: record each accessory's
`getNumActuations()` in `setUpClass` and assert the delta.

What the test *does* cover genuinely is forward progress — active locomotives and >= 3 route completions
per locomotive. That half is real.

### TST-A5 — "at most one popup per Layout" is asserted against a latch that never clears

`test/core/testAutonomyPathValidation.java:304-361`, `testUiAlertFiresAtMostOncePerLayout`.

`hasShownPathValidationAlert()` returns `pathValidationAlertShown`, set once at `Layout.java:2803` and
never cleared. Asserting it is still true after three further failures (`:345`) cannot fail. Nothing
counts calls to `control.showAutonomyAlert`.

**Mutation that survives:** move `control.showAutonomyAlert(...)` (`Layout.java:2807-2812`) **out** of the
`if (alert)` block. The operator gets a modal dialog on every path-validation failure for the rest of the
session — the exact interruption the once-per-Layout rule exists to stop — and the latch is still true,
the count is still `THRESHOLD + 3`, and the test is green. Fix: count invocations through a stub `View`.

Secondary on the same test: a hard `Thread.sleep(5000)` at `:355`, and `showUI = true` at `:45` (see
TST-B4).

### TST-A6 — the bulk-edit test is satisfied by editing one tile of the selection

`test/core/testAutonomyDiagramSession.java:107-119`, `testBulkEditingAppliesToEveryTileSelected`.

Fixture: `FEEDBACK(1,1) – STRAIGHT(2,1) – STRAIGHT(3,1) – FEEDBACK(4,1)`. Two tiles are selected,
`Direction.NONE` applied, and the assertion is that `edgesBetween(11,12)` and `(12,11)` are both 0.
Closing **either** straight severs the only run. Nothing asserts the store recorded a direction for both.

**Mutation that survives:** in `AutonomySession.setDirection(Set<TileKey>, Direction)`
(`src/org/traincontrol/automationui/AutonomySession.java:3036-3050`) replace the loop with
`TileKey tile = tiles.iterator().next();` and record only that one. Test green; on a real layout a
forty-tile bulk edit sets one tile, and the javadoc says this gesture is "the bulk of the work" of
setting a railway up.

### TST-A7 — `distinctDestinations` is called from two places and tested from none

`src/org/traincontrol/automationui/StationIndex.java:458-486`, called from
`src/org/traincontrol/gui/AutoLocomotiveStatus.java:973` and
`src/org/traincontrol/gui/LayoutRightclickAutonomyMenu.java:647`.

`grep -rn distinctDestinations test/` returns nothing. The test that names the rule —
`test/core/testAutonomyDiagramSession.java:3323-3335`,
`testAPathBackToWhereItStartedIsDroppedEvenForUnknownPoints` — asserts
`index.sameSquare("Ghost","Ghost")` and `index.squareOf("Ghost") == null`. Those are the helper, not the
rule.

**Mutation that survives:** delete `if (sameSquare(from, to)) continue;` at `StationIndex.java:474`. Both
menus then offer the train a journey to the platform it is standing on — the defect the javadoc
describes — and the suite is green. This is the repo's signature shape: the rule was lifted out, tested,
and the call site left uncovered.

### TST-A8 — the route editor's lock is tested on drawing and on editing, not on the row actions

`test/ui/testRouteEditorLocked.java` (whole class) and `test/ui/testCommandTableMarks.java:112-121`.

`RouteEditorFrame` carries four deliberate second guards — `moveRow:1902`, `deleteRow:1910`,
`duplicateRow:1925`, `addTo:1934`, each `if (locked) return;`, under a comment reading "One rule, two
guards: the drawing one is what the user sees, this one is what makes it true."
`testCommandTableMarks` is the only test in the suite that calls `clickCommandMarkForTest`, and it opens
`new RouteEditorFrame(null, null)` — **unlocked**. `testRouteEditorLocked` never calls it.

**Mutation that survives:** delete `if (locked) return;` from all four methods. `testRouteEditorLocked`
still passes (it checks painted mark strings and `isCellEditable`); `testCommandTableMarks` still passes
(unlocked frame). A keyboard press then mutates a Central Station route from an editor that says it is
read-only.

### TST-A9 — three caption-undo tests drive an API the editor does not call

`test/core/testAutonomyDiagramSession.java:3456-3480`, `:3488-3513`, `:3521-3535`.

All three drive `session.captionsOnPage(page)` / `session.restoreCaptionsOnPage(page, map)`, whose
javadoc claims to protect the track editor's Ctrl+Z ("without a snapshot of its own, Ctrl+Z brought a
deleted platform back with no name on it"). `LayoutEditor.java:325-354` uses
`autonomy.snapshotPage(...)` / `autonomy.restorePage(...)`, which route to
`AutonomyCompanionStore.snapshotPage`/`restorePage` and carry captions through `kept()`
(`AutonomyCompanionStore.java:4368`). The only callers of `captionsOnPage`/`restoreCaptionsOnPage` are
these tests and `AutonomySession` itself.

**Mutation that survives:** remove `"captions"` from `AutonomyCompanionStore.kept()`. Undo restores the
page with its captions stripped — the reported defect, back — and all three tests pass. Conversely,
deleting both `AutonomySession` methods would change nothing in the application.

### TST-A10 — the locomotive-rename repair is tested by the test supplying the call

`test/core/testAutonomyDiagramStore.java:2019-2050`, `:2055-2081`, `:2095-2126`.

Each test calls `AutonomyCompanionStore.repairLocomotiveInSetup(...)` /
`repairLocomotiveInPageSnapshot(...)` **by hand** between the rename and the restore. The production
obligation is `LayoutEditor.autonomyLocomotiveRenamed()` (`LayoutEditor.java:410-433`), which must do all
three: the Cancel snapshot at `:413`, the undo stack at `:425`, the redo stack at `:431`.

**Mutation that survives:** delete any one of those three. All three tests still pass, because the test
supplies the call the editor omitted. The redo-stack call at `:431` has no coverage at all.

### TST-A11 — `DiagramMonitor.invalidate()` is called by nothing in the suite

`test/core/testAutonomyDiagramMonitor.java:366-384`,
`testThePictureCanBeAskedForAgainAfterAViewIsRebuilt`.

The body constructs a `DiagramMonitor` with a `LayoutSource` returning null and asserts
`getPublished() != null` and `getPublished().isEmpty()`. Both read the field initialiser
`private volatile Map<TileKey, TileOverlay> published = Collections.emptyMap();`
(`DiagramMonitor.java:71`) on a freshly constructed object. The mechanism the javadoc is about is
`DiagramMonitor.invalidate()` (`:118`), whose only production caller is `DiagramMonitorDriver.java:307`.
No test in `test/` calls it.

**Mutation that survives:** make `invalidate()` a no-op. A rebuilt grid stays blank until the next train
moves.

**Same class, `testFiringOnlyMarksDirtyAndPublishesNothing` (`:286-321`, assertion at `:315`):** the null
`LayoutSource` short-circuits `compute()`, so `published.isEmpty()` holds no matter what firing does.
**Mutation that survives:** `public void markDirty() { dirty.set(true); refresh(); }` — computing on the
firing thread while it holds the layout's monitor, which is precisely the defect the test names. The
dirty-flag half of that test (`:319-320`) is sound.

### TST-A12 — a reversal test whose every assertion is inside a loop that can run zero times

`test/core/testAutonomyDiagramReversal.java:269-291`,
`testTheAuthoredFlagDoesNotLeakOntoThePlainCopy`. Every assertion sits inside
`for (JSONObject copy : pointsNamed(built, "Main4"))`. Its five siblings (`:97, :191, :235, :369, :397`)
each assert `copies.size()` first; this one does not.

**Mutation that survives:** change the copy-name separator in `AutonomyBuilder` from `" ("` to anything
else. `pointsNamed` (`:589`) matches `name.startsWith(base + " (")`, returns empty, zero iterations, test
green — while `terminus` leaks onto every plain copy and every passing train reverses.

### TST-A13 — the shift's coordinate mapping is built by the test, never by the editor

`test/regression/testDiagramShiftKeepsSetup.java` — all six `@Test` methods (`:40, :75, :103, :126,
:162, :230`) build the move map with the file's own `shift()` helper (`:298-316`), which hardcodes
`otherEnd = 22`. The only production producer of that map is
`LayoutEditor.setupShift` (`src/org/traincontrol/gui/LayoutEditor.java:3964-3987`), called from `:3775`,
`:3825`, `:3870` and `:3920`. Grepping `test/` for `setupShift` returns one hit — a comment at `:294` of
this same file.

**Mutation that survives:** transpose `LayoutEditor.java:3970` to
`int otherEnd = across ? layout.getSx() - 1 : layout.getSy() - 1;`. All six tests pass. On any
non-square page a real shift then stops mapping the rows or columns past the smaller bound, and every
setting on them is left behind — which is the class of fault Adam reported as "the coordinate mapping
there may be an issue". An off-by-one on `from` at any of the four call sites (`lastHoveredY + 1` →
`lastHoveredY`) survives equally.

### TST-A14 — the bulk-move planner is tested; the four calls that feed it are not

`test/regression/testLayoutEditorBulkEdits.java` — `apply()` at `:331` calls `store.moveTiles` directly,
so `LayoutEditor.applyBulkPlan` (`LayoutEditor.java:2148`) and the two `executeTool` calls (`:2000`,
`:2062`) never run. The same is true of
`testDeleteAndInsertKeepTheSetup.testThePlannerReportsBothHalvesOfAMove:116` and the three `planBulkLine`
tests in `testStationLabelsFollowMoves` (`:244, :268, :298`).

**Mutation that survives:** at `LayoutEditor.java:2000` swap the endpoints —
`planBulkLine(layout.getName(), true, destCol, startCol, sourceColumn.size(), occupied, isMove)` — or
pass `!isMove`. All ten tests here and the four elsewhere stay green while a real column move carries the
setup the wrong way, which is Adam's original unpaired-links report.

Also: `testLayoutEditorBulkEdits.java:39` says "see the note at the bottom of this file". There is no
note; the file ends at `:356` with helpers. The pointer to this gap is dangling.

### TST-A15 — the capture-before-rename order is claimed to be held elsewhere, and is not

`test/regression/testARunSurvivesAPageRename.java:46-50` states that the window doing capture *before*
rename "is held separately, by `testTheWindowAttachesItsRefreshCallback`". That class (116 lines, read in
full) checks `AutonomyRefreshCallback.attach`, the `attachAutonomyRefresh` count, and
`repairAutonomyLocomotive` → `updateVisiblePoints()`. Nothing about capture versus rename. The order
lives at `TrainControlUI.java:20586` (`captureRunningLayout();`) ahead of `renameOrDuplicate` at `:20599`.
Grepping `test/` for `captureRunningLayout` returns **zero hits**.

**Mutation that survives:** move `captureRunningLayout();` below the `renameOrDuplicate(...)` call.
`testTheOrderOfTheCaptureDecidesWhetherTheRunSurvives` still passes — it supplies both orders itself —
and DW-A1 returns: a rename after a run discards every placement the run produced, on every page, and
the rebuilt configuration can route a train into a block that is physically occupied.

This is the sharpest instance of the pattern in the review: a test that names the gap, delegates it, and
the delegate does not cover it. The two javadocs together read as coverage.

---

## B — Medium

| | Finding | Disposition |
|---|---|---|
| B1 | `ant test` has neither the skip gate nor the live-layout fingerprint that `battery.sh` has | open |
| B2 | `testNothingWroteToTheGoldenLayout` compares before its own siblings run | open |
| B3 | Nothing enforces the `LayoutSandbox` rule; three classes reach the live layout without it | open |
| B4 | Five test methods in `testParseCS2Routes` carry no `@Test` and have never run | open |
| B5 | `testRouteInventory` contributes eight green results and almost no assertions | open |
| B6 | `testNoSelfRecursiveWrappers` only detects the `this.`-qualified spelling | open |
| B7 | The exit-discard ordering assertion passes if the settle call is deleted | open |
| B8 | `testThePickedUpLabelIsAlwaysPutDown` matches the wrong branch and is now vacuous | open |
| B9 | `testTheActivePageDrawsTheSamePictureAsChoosingIt` renders the same call twice | open |
| B10 | `testBusyDialogInteraction` never asserts a dialog is shown or disposed | open |
| B11 | `testTimetableOnDerivedGraph` derives from the machine's preference, and its config name is dead | open |
| B12 | `testReturnHomeOnRealLayout` has an unseeded `Random` and no floor on meaningful rounds | open |
| B13 | `testAPermanentlyUnexecutableEntryEndsTheRun` races its own precondition | open |
| B14 | `testAnAddressWithOneLocomotiveIsNotReportedFree`'s closing assertion is a tautology | open |
| B15 | `testRunningAgainOverASettledSetupChangesNothing` states an invariant already violated | open |
| B16 | `testMessageBundles`' source scans have no "did I scan anything" control | open |
| B17 | `testAnUnreadableImportChangesNothing` never asserts the import was refused | open |
| B18 | Three sample-layout tests assert an empty list with nothing proving the list can fill | open |
| B19 | `testStationLabelPrefill` tests `nearestOf` hard and `nearestStation` not at all | open |
| B20 | Process-global and fixture state left mutated for later classes | open |
| B21 | `testErrorsStopTheSetupRunning` asserts a method with no production callers | open |
| B22 | The address-0 rule is pinned; the dialog that re-implements it is untested | open |
| B23 | `testTheWindowAttachesItsRefreshCallback` strips `//` only, and says so | open |

### TST-B1 — the documented entry point is a weaker gate than the undocumented one

`test/README.md` says "`ant test` runs everything except `testAutoDetect`". `build.xml:106-260` does
exactly that, one class per JVM, and `nbproject/build-impl.xml:1645` fails the build on
`tests.failed`. Two things `tools/battery.sh` does that `ant test` does not:

1. **A skipped class reads as green.** `battery.sh:150-180` classifies "Total tests run: 0" and
   "Skips: N" separately from pass, under a comment recording that
   `core.testAutonomyDiagramSampleLayout` sat at "13 tests, 0 passed, 13 skipped" for two days and was
   counted green every time. `ant test` has no equivalent. Eighteen classes in the suite throw
   `SkipException` or skip headless, and `testAutonomyDiagramSampleLayout:79-193` rethrows from
   `@BeforeClass`, which TestNG renders as SKIPPED, not FAILED.
2. **The live railway is not fingerprinted.** `battery.sh:66-86, 183` hashes `cs2_sample_layout` around
   the whole run and shouts if it changed. `ant test` does not, and no test class can do it (TST-B2).

`build.xml` also does not pass `-Dtraincontrol.anyReceivePort=true`
(`src/org/traincontrol/marklin/udp/NetworkProxy.java:46`), which `battery.sh:46` sets and documents: a
bind failure "comes out of `@BeforeClass` as *Total tests run: 16, Failures: 0, Skips: 16* — zero
failures, having tested nothing." So `ant test` run while TrainControl is open, or with an orphaned test
JVM alive, can lose whole classes to a bind failure.

**Mutation that survives:** break anything in a class that skips — nothing about `ant test` reports it.

**Unverified, and it decides how bad this is:** whether TestNG's ant task sets `failureProperty` on a
configuration failure. `battery.sh:126-127` and `:146` handle "Configuration Failures: N" as a *separate*
line from the summary and treat it as a failure explicitly, which reads as somebody having found that the
summary alone did not say so. I could not run anything to settle it. If configuration failures do set the
property, item 1 is about deliberate `SkipException`s only and this is a C; if they do not, a class whose
`@BeforeClass` throws is invisible to `ant test` and this is a B or worse. Settling it is one run.

Fix is small: add `<sysproperty>` for the port flag to the macro, and either point `test/README.md` at
`battery.sh` as the real gate or port its three classification branches into a `-post-test-run` check.

### TST-B2 — the golden-layout write check runs before the tests that could write

`test/regression/testTheGoldenLayoutHoldsTogether.java:71-152`.

`before = fingerprint(GOLDEN)` is taken in `@BeforeClass:79`; `after = fingerprint(GOLDEN)` is taken
**inside `testNothingWroteToTheGoldenLayout`**, the first of five `@Test` methods (`:128`). The other
four (`:164, :207, :235, :262, :331`) run afterwards and are never compared. `tearDownClass:98-102` only
stops the model.

The javadoc is admirably honest about the cross-JVM limit ("Read the scope before relying on it") but
does not mention this one: the class cannot see its **own siblings'** writes.

**Mutation that survives:** have any later `@Test` in this class call `store.save()`. Adam's
unrecoverable railway is rewritten and the class is green. Fix: move the comparison into an
`@AfterClass`.

### TST-B3 — the `LayoutSandbox` rule is a convention with no test behind it

`test/support/LayoutSandbox.java` exists because a battery left `cs2_sample_layout` modified on every
run (OB-111): the window follows `TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF`, which on Adam's machine
names his railway. Eight classes use it — `testSwitchingToACentralStationLayout`,
`testBusyDialogInteraction`, `testDiagramExport`, `testDiagramLooksRight`, `testLocIconCrop`,
`testLocMappingPages`, `testRenderingCost`, `testUiStateIsNotLostWhenUnreadable`. Nothing checks that a
ninth does.

Three classes already reach past it:
- `test/ui/testRouteCapture.java:41` — `init(null, true, false, false, true)`, no sandbox.
- `test/core/testAutonomyPathValidation.java:45` and `test/core/testLayoutTiles.java:62` —
  `init(null, true, **true**, false, true)`, i.e. `showUI = true`, which constructs and initialises the
  real `TrainControlUI`. No sandbox.

Worse than reading: `MarklinControlStation.java:465`, on a parse failure, does
`TrainControlUI.getPrefs().put(LAYOUT_OVERRIDE_PATH_PREF, "")` — a transient read error inside any of
these clears the operator's configured layout path.

**Mutation that survives:** add a new UI test class that constructs `TrainControlUI` without the sandbox.
OB-111 is back and the suite is silent. `testEveryWindowWearsTheIcon` is the model for the check that is
missing here — a source scan asserting that every test class naming `new TrainControlUI(` or
`init(..., true, ...)` also names `LayoutSandbox`.

### TST-B4 — five test methods have never run, and two of them are wrong

`test/core/testParseCS2Routes.java` — `testDCCRouteNoMags:233`, `testMM2RouteMags:243`, `testDCCAcc:253`,
`testMM2Acc:260`, `testMM2Acc1:267` carry no `@Test`. There is no `testng.xml`, so discovery is by
annotation. These five are the only unannotated `public void test*` methods in the whole suite — I swept
all 124 classes.

Two are also incorrect: `testMM2Acc`/`testMM2Acc1` assert that MM2-named addresses 118 and 117 are
`DCC`, and `testMM2RouteMags` asserts every command of `"D1 dcc tst"` is MM2 — which contradicts the
annotated `testDCCRoute:228` beside it, asserting address 121 is DCC. They would fail if enabled, so
enabling them is not a one-line change; read them first.

`testEveryTestIsInTheBattery` cannot see this: it checks the class is named in `build.xml`, which it is.

**Mutation that survives:** invert accessory protocol detection for addresses 117/118/121 in
`CS2File.parseMags`.

Related, in the same file: `testDCCRoute:216-231` filters `for (RouteCommand rc : r.getRoute())` on two
addresses and asserts only inside. Zero matches, zero assertions. **Mutation:** make `parseRoutes` drop
accessory commands at address 121 — the loop never sees them. Its sibling
`testParseCS2Layout.testDCCAcc` does this correctly, with a `valid` counter.

### TST-B5 — a report class registered as eight tests

`test/core/testRouteInventory.java`. Seven of its eight `@Test` methods contain **no assertion at all**:
`testDerivedRoutes:61`, `testStuckBundleRoutes:67`, `testLaterBundleRoutes:77`,
`testLatestBundleRoutes:87`, `testHandAuthoredRoutes:97`, `testWhatTheUiWouldShow:353`,
`testWhatTheLockEdgeActuallyShares:449`. Four begin `if (!bundle.isFile()) return;` against
CWD-relative paths, so on any machine without `tc_backup/Autonomy 1b.json` they pass having done
nothing. `testWhyBottomMainAOffersNothing:263` asserts only `layout.isValid()`. Line `:460` is dead:
`String name = session.getStationIndex() == null ? "" : "";`.

The class javadoc says so — "A REPORT, not an assertion" — and
[README.md](README.md) §"Three kinds of document" says a generated report "does not belong in this
folder … leave the harness to write it to a temporary directory, which is what `testRouteInventory` now
does." The output moved; the class did not. It is still in `build.xml` and still contributes eight
greens.

**Mutation that survives:** make `Layout.getPossiblePaths` return an empty list unconditionally — all
seven pass.

It also calls `model.parseAuto(...)` and `layout.moveLocomotive(...)` on the shared model, replacing the
live autonomy graph for anything after it in the JVM.

### TST-B6 — the StackOverflow guard sees only one spelling of the bug

`test/regression/testNoSelfRecursiveWrappers.java:67`. The detector is
`withoutComments(line).contains("this." + name + "(")`. The historical bug was
`this.syncWithCS2()` inside `syncWithCS2`, so the test catches the case it was written for, and it has a
proper `assertTrue(checked > 0)` floor at `:75`.

**Mutation that survives:** inside the wrapper at `TrainControlUI.java:7887`, change
`return this.model.syncWithCS2();` to `return syncWithCS2();`. Unqualified self-call, infinite recursion,
every Central Station sync a StackOverflowError — and the test is green. The same for a call inside a
lambda (`() -> syncWithCS2()`).

The house style is `this.`-qualified throughout, which is why nothing has tripped it; but the bug this
guards was introduced by a mechanical rewrite, and a mechanical rewrite is exactly what produces the
unqualified form. Widening the match to `(^|[^.\w])name\s*\(` inside the body would cover both.

### TST-B7 — the exit-discard ordering check passes when the refusal is removed

`test/regression/testEditorSurfaceRules.java:1295`.

```java
assertTrue(exit.indexOf("completeExitDiscard()") > exit.indexOf("maySettleBeforeExit()"), ...)
```

`exit` is the body of `TrainControlUI.WindowClosed`. Neither term's presence is asserted. Both are live
today (`TrainControlUI.java:15121` and `:15192`).

**Mutation that survives:** delete the `!openEditor.maySettleBeforeExit()` call at `:15121`. Application
exit stops asking about unsaved work at all — worse than the fault the test was written for — and
`indexOf` returns `-1`, so a present `completeExitDiscard()` is `>= 0 > -1` and the assertion passes. The
earlier assertion at `:1259` only checks that `maySettleBeforeExit`'s *own body* calls
`settleUnsavedWork`, not that anything calls `maySettleBeforeExit`.

The rest of this class handles the `-1` hazard correctly — `testManageSitsUnderTheConfiguration:2264-2278`
asserts all three positions are `>= 0` before ordering them, and `testThePageKeysUseTheSwitch...:1717`
proves `stepPage(1)` is present first. One line to bring this one into line with its siblings.

### TST-B8 — a drag test that matches the wrong branch, and says the opposite in its javadoc

`test/ui/testStationLabelDrag.java:260-279`, `testThePickedUpLabelIsAlwaysPutDown`.

`down = grid.indexOf("hideCaptionGhost()", released)` resolves to `LayoutGrid.java:257` — the
`hideCaptionGhost()` inside the **wrong-button early-return block** — and
`leaves = grid.indexOf("return;", released)` resolves to `:260`, the `return;` in that same block.
`257 < 260`, always.

**Mutation that survives:** delete `editor.hideCaptionGhost();` at `LayoutGrid.java:271` — the
unconditional put-down on the left-button path — or move it below
`if (!moved || editor.getAutonomyPanel() == null) return;` at `:274`. A click that was not a drag then
leaves a floating copy of the caption stuck over the diagram. `down` is still 257 and the test is green.

The javadoc at `:258` states "MUTATION: moving `hideCaptionGhost` below the early return fails this." It
does not. Anchoring `down` on the second occurrence, or on `boolean moved = dragging[0];`, fixes it.

Related, same file, `:307`: `assertTrue(pressed.contains("dragging[0] = false"))`. That string occurs
twice in `mousePressed` — `LayoutGrid.java:205` (the wrong-button branch the message is about) and `:211`
(the ordinary path). **Mutation that survives:** delete line 205; a right-press part way through a
left-drag clears `began` but leaves `dragging` true, and the substring is still found at 211.

### TST-B9 — the active-page export test renders the same call twice

`test/ui/testDiagramExport.java:207-231`,
`testTheActivePageDrawsTheSamePictureAsChoosingIt`. Lines 212-213:

```java
BufferedImage byName  = DiagramExport.render(page, 60, ui);
BufferedImage asActive = DiagramExport.render(page, 60, ui);
```

Byte-identical arguments. Nothing reaches the active-page path. What the test measures is that `render`
is deterministic.

**Mutation that survives:** at `TrainControlUI.java:8044`, change
`active.addActionListener(event -> exportDiagram(activeLayoutPage()))` to export a different page, or
make `activeLayoutPage()` answer wrongly. "Export active diagram" silently exports the wrong page.

### TST-B10 — the leaked-dialog class never mentions a dialog

`test/ui/testBusyDialogInteraction.java`. The class exists because "a leaked modal dialog is a frozen
program", and there is no reference to the dialog anywhere in the file: no visibility check, no dispose
check. `testWorkThatThrowsStillDismissesTheDialog:161-179` asserts only that the *done callback* ran.

**Mutation that survives:** strip the dialog out of `BusyDialog.run` entirely — spawn the work thread,
post `done` with `invokeLater`, never construct or show a window. All six tests pass. Narrower version:
keep the dialog, run `done` in the `finally`, never call `dispose()` — the named test passes with the
program frozen.

Same file, `:256` and `:355`: `assertFalse(model.getLocList().isEmpty(), "the sync brought in no
locomotives")` cannot fail. `model` is the class-level station from `init(null, true, false, false,
true)`, which loads the operator's real database; the list is already full before the sync.
**Mutation that survives:** make `syncWithCS2()` return 0 immediately without contacting the station or
replacing any database. Fix: assert on a locomotive name that exists only in `test/lokomotive.cs2`, or
take a count before and after.

### TST-B11 — the derived-graph timetable test reads the build machine's preference

`test/core/testTimetableOnDerivedGraph.java:348-400`, `derivedLayout()`.

It constructs `new AutonomySession(new File("test_layout"))` but takes the diagram pages from
`model.getLayoutList()` at `:361`, which comes from `MarklinControlStation.syncLayoutsFromConfiguredSource`
reading `TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF` out of java `Preferences`
(`MarklinControlStation.java:435`). The pages are therefore whatever the machine's preference names,
paired with `test_layout`'s autonomy store. `testTracedPathIsContinuous:55-67` does this correctly, by
parsing `test_layout` through `CS2File` explicitly.

`CONFIGURATION = "Autonomy 1"` (`:83`) does not exist in the fixture:
`test_layout/config/autonomy/` holds only `configuration-Main.json` and `setup.json` says
`"activeConfiguration": "Main"`. So the `contains(...)` guard at `:367` is always false and the class
falls through to "whichever happened to be active" — which the comment at `:365-366` says must not
happen.

**Mutation that survives:** make `AutonomyCompanionStore.setActiveConfiguration(String)` a no-op; the
branch calling it is never taken.

Three `SkipException`s can also retire the class's only `@Test`: no `test_layout` (`:352`), no active
configuration (`:374`), and `captured.isEmpty()` after a 12-second run (`:212-217`). The third is the
routine one on a loaded machine — and `testTimetableCaptureThroughARealRun` deliberately refuses to skip
for exactly that case.

### TST-B12 — the return-home property test has no seed and no floor

`test/core/testReturnHomeOnRealLayout.java:223-291`,
`testEveryoneCanGetHomeAfterAutonomyIsStoppedAtRandom`.

`RANDOM = new Random()` at `:64` — unseeded, and no seed in any failure message; the class comment at
`:158-160` concedes the failure is unreproducible. This is the doctrine
[README.md](README.md) §Testing states outright, and `testTimetableOnDerivedGraph` follows it correctly
(`SEED`, `andTheSeed()`).

The pre-round block (`:236-246`) is skipped entirely when `loaded == ALREADY_HOME`, and each of the three
rounds `continue`s at `:263-267` when the arrangement comes back already home. Nothing counts how many
rounds actually reached `planReturnToHome()` / `executeTimetable()`.

**Mutation that survives:** make `Layout.planReturnToHome()` always return a plan with
`isPossible() == false`. If the operator's file loads at home and the three random stops finish at home,
every assertion is bypassed and the run is green.

### TST-B13 — the give-up bound test races the flag it waits on

`test/core/testLayoutTimetable.java:285-330`,
`testAPermanentlyUnexecutableEntryEndsTheRun`. `runner.start()` at `:310` is followed immediately by
`while (layout.isAutoRunning() && ...)`. `Layout.executeTimetableInternal:4110` sets `running = true`
only after the worker is scheduled, so the main thread's first read most often sees `false`, the wait
loop exits at once, and both closing assertions pass without the retry loop having run. There is no
precondition assert that the run started — unlike its sibling `testAGracefulStopEndsTheRetryLoop:338-381`,
which sleeps 3s and asserts `isAutoRunning()` first.

**Mutation that survives:** delete the `TIMETABLE_STUCK_MS` give-up from the retry loop (T3 restored).
`assertFalse(layout.isAutoRunning())` still reads false on the pre-start race, and
`assertFalse(...isExecuted())` is true either way.

### TST-B14 — the closing assertion is the tautology and the "precondition" is the check

`test/core/testLocomotive.java:933-961`,
`testAnAddressWithOneLocomotiveIsNotReportedFree`. `single` is obtained by iterating `all.entrySet()`
(`:944-952`); line `:960` then asserts `all.containsKey(single)`. That cannot fail. The only substantive
line is `:957`, labelled "precondition". The named subject — the duplicates dialog answering "address is
free" from `getDuplicateLocAddresses` — is never exercised; no production predicate is called.

**Mutation that survives:** revert the dialog to consulting `getDuplicateLocAddresses` instead of
`getLocAddresses`. The defect returns and the test is green.

### TST-B15 — a test whose comment says a second call site must not exist, beside two call sites

`test/core/testAutonomyDiagramSession.java:2040-2073`,
`testRunningAgainOverASettledSetupChangesNothing`. Its javadoc: *"What protects that choice is the
caller: this runs only when the first configuration on a layout is created … If a second call site ever
appears, this is the test whose comment explains why it must not."*

`excludeRepeatedSensorPages()` is now called from `AutonomyViewerPanel.java:885` (configuration creation,
the sanctioned one) **and** `:1137`, on legacy import into an existing setup.

**Mutation that survives:** none is needed — the stated invariant is already broken in shipped code and
nothing detects it. A page the user deliberately switched back on is re-excluded by the next legacy
import, and no test covers that. Either the comment is wrong or `:1137` is; the test cannot tell you
which, and that is the finding.

### TST-B16 — the message-bundle source scans can examine zero files and pass

`test/core/testMessageBundles.java:428` and `:513`. `javaSources(new File("src"))` is CWD-relative and
returns an empty list when `listFiles()` is null (`:480`).
`testEveryFormattedMessageHasAPlaceholder` and `testConfirmationsUseTranslatedButtons` then pass having
examined nothing — indistinguishable from "no offenders". The class asserts `bundles().size() >= 2` for
exactly this reason and does not do the same for sources.

Both patterns are live today: `I18n.f("…",` occurs 382 times under `src/`, and `showConfirmDialog(` in
three files.

**Mutation that survives:** run from any directory but the project root. `testJavadocsAreAttached:51` and
`testNoSelfRecursiveWrappers:43` both guard this and are the model to copy.

### TST-B17 — an "unreadable import changes nothing" test that never checks the import failed

`test/core/testAutonomyDiagramStore.java:445-474`,
`testAnUnreadableImportChangesNothing`. `try { store.importBundle("Theirs", bundle); } catch
(RuntimeException expected) { }` with no `fail()` after the call. Its two assertions are that
`getPointName(station)` is still `"Bottom Main"` and `isStation(station)` — and importing fills gaps and
never overwrites (`testImportingFillsGapsAndOverwritesNothing:1336`), so both hold whether the import
threw or succeeded.

**Mutation that survives:** change `readShared` to use `opt*` accessors instead of the type-strict ones,
so a malformed bundle imports silently. Nothing throws, the rollback this test exists for is dead, and
the test passes.

The three sibling catch-without-`fail()` blocks at `:1763`, `:1943`, `:1988` **do** have teeth — a lenient
import would trip their assertions. This one does not.

### TST-B18 — three sample-layout tests assert an empty violation list with no population floor

`test/core/testAutonomyDiagramSampleLayout.java:425-436`
(`testATrainCannotLeaveBySideItArrivedAt`), `:532-549`
(`testATrainReachingBottomMainAFromTheTunnelCannotGoBack`), `:769-910`
(`testNoTwoRoutesCanOccupyTheSameTrackUnlocked`). All three build a violation list and assert it is
empty. The counts are printed and never asserted.

**Mutations that survive:** `AutonomyBuilder.edgesByName()` returns an empty map — `turns` and
`offending` are both empty, tests 1 and 2 pass. `ReducedEdge.getPath()` returns an empty list —
`sharesATile` is false for every pair, so `derivedGaps` is empty **and** `exactExtent` returns null for
every legacy edge, making the hand-built half 100% "ambiguous" and silently skipped. The class calls that
third one "the most dangerous defect in the project".

`testStationsCanStillReachOneAnother:1347` is the one test in the class that does assert its own
population is non-empty first. It is the pattern the other three want.

Same shape, `test/regression/testTheCheckerAgreesWithTheBuild.java:217-239`
(`testTheStationsNothingReachesAreTheOnesTheBuildCannotRouteTo`): compares `reported(...)` with
`unreached`, and if both are empty it passes. Its two siblings each prove non-emptiness first — `:195`
(`assertFalse(reported.isEmpty(), "…ten of them…")`) and `:293`
(`assertNotNull(exempt, …)`). This one does not, so **mutation that survives:** make
`AutonomyChecks.checkStations` never report `STATION_UNREACHABLE`, on the assumption the frozen fixture
has none. I could not run to establish whether it currently does; the floor is worth adding either way,
since if the fixture has none the test is asserting nothing at all today.

### TST-B19 — `nearestOf` is tested exhaustively, `nearestStation` not at all

`test/ui/testStationLabelPrefill.java`. `testTheNearestStationIsTheOneOffered` and
`testAStationOnAnotherPageIsNotNear` drive `AutonomyEditorPanel.nearestOf`
(`AutonomyEditorPanel.java:2169`) hard, including the diagonal case added after a mutation run. The
caller `nearestStation` (`:2138-2154`) — which turns the graph into the candidate list — appears in the
suite only as the string `"showing = nearestStation(tile);"` in a source scan at `:130`.

**Mutation that survives:** drop `point.isStation()` from the filter at `AutonomyEditorPanel.java:2147`.
Every reduced point becomes a label candidate, so "Show station label here" offers a plain track square.
All three tests pass, source scan included.

### TST-B20 — state left mutated for whatever runs next

Not hollow assertions, but they make other tests pass or fail for reasons unrelated to the code.

- **`test/core/testAutoLayout.java:1140-1194`** — the `finally` (`:1187-1193`) restores `block`,
  `protectingSignal`, `home` and `active`. It does **not** restore `maxTrainLength(7)`, `priority(3)`,
  `speedMultiplier(0.75)` or `autoDestination(false)`, all set at `:1158-1161` on
  `layout.getPoints().iterator().next()` — a point of the model-wide sample layout every other test in
  this 1,288-line class shares. It is left non-dispatchable and speed-scaled.
- **`test/core/testLocomotive.java:41` and `:359`** set `DEBUG_SIMULATE_PACKETS = true` and
  `tearDownClass:723-740` restores nothing. `testRouteReachesTheRails` and `testControlStationFaults`
  both save and restore this flag deliberately. Leaked true, `testAutoLayoutRace:431`
  (`testWaitingForPowerGivesUp`) can have its GO echoed back and `waitForPowerState(true, 400)` return
  true — a false failure in another class.
- **`test/core/testAutonomyDiagramSampleLayout.java:1863-1884`** calls `graph.pairPortals(...)` and
  `graph.disablePortal(here)` on the `@BeforeClass`-built `graph` and never restores it.
  `testTheDiagramIsNotRefused:616` and `sharesATile:918` both read that object, and TestNG does not
  guarantee method order.
- **`test/core/testLayoutRenameKeys.java:80`** installs a hand-built `Layout` into the shared model by
  reflection and never restores it.
- **`test/core/testParseCS3Routes.java:119`** calls `rc.removeAll(newRoute.getRoute())` on
  `otherRoute.getRoute()`, which `Route.java:93` returns live — so the diagnostic empties the route it is
  diagnosing, for every later assertion in the class. Failure path only.
- **Locomotives left in, and real ones deleted, in the restored DB image:** `testLocDB.java:109` creates
  `"New locomotive test 2"` at MFX 20 and never deletes it (empty `tearDownClass` at `:234`);
  `testLocomotive.java:723` deletes `Test loc child 1` and `2` but not `child 3` (created `:720`), and
  deletes `"Test loc 4a"`, which is never created; `testImportRename.java:224-232` deletes **every**
  locomotive in the restored database sharing MM2 address 60 with its probe — the operator's own records,
  by name. All in-memory only today (`MarklinControlStation.saveState` has no caller outside
  `TrainControlUI`), so nothing reaches disk; the damage is to later classes in the same JVM.

### TST-B21 — the rule the affordance was supposed to ask has no caller

`test/regression/testErrorsStopTheSetupRunning.java:89` —
`assertTrue(session.hasErrors(), "…so every affordance that asks this goes on offering to start…")`.
`AutonomySession.hasErrors()` (`src/org/traincontrol/automationui/AutonomySession.java:2991`) has **zero
callers in `src/`**; grep returns only the declaration. The affordances now ask
`autonomyErrorCount()` (`AutonomyOverlayToggle.java:262`, `TrainControlUI.java:19070`).

The class's own javadoc names this exact shape — DD-A6, "a rule with no caller passes every test written
about the rule" — and then does it.

**Mutation that survives:** revert both affordances to `session.hasBlockingProblems()`. All three tests
pass and OB-090 is back: a live Start button over a setup that refuses every press. This is the
guard-and-affordance rule (OB-057/OB-090) — the button that offers an action must ask the guard's own
predicate — and the test asserts the predicate nobody asks.

### TST-B22 — the address-0 rule is pinned and the dialog that re-implemented it is not

`test/regression/testLocomotiveAddressRules.java` (whole file). The rule's only call site is
`src/org/traincontrol/gui/AddLocomotive.java:311`, and no test file anywhere in `test/` mentions
`AddLocomotive`.

**Mutation that survives:** replace `AddLocomotive.java:311` with the three upper-bound-only `if` blocks
and the `abs()` it used to have. All four tests pass and address 0 is accepted again — the original
defect the class javadoc describes, exactly as it was.

### TST-B23 — a comment-stripper that handles only half the comment syntax, under a comment saying so

`test/regression/testTheWindowAttachesItsRefreshCallback.java:34-46`. `withoutComments` strips `//` lines
only; block comments and javadoc pass through into the count at `:78`. Today
`attachAutonomyRefresh(` occurs exactly three times in `TrainControlUI.java` (`:3301, :3338, :18941`),
all code.

**Mutation that survives:** delete the call at `TrainControlUI.java:18941` and add one javadoc line
mentioning `attachAutonomyRefresh(`. The count is 3 again, `attached >= 3` passes, and the second
`parseAuto` leaves a `Layout` with no callback — the timetable and locomotive-status panels stop being
repainted, which is the d8db4879 regression, reported twice.

Listed as a finding rather than a note because it is *self-declared*: the comment at `:72-77` sets it out
in full and leaves it. It is a two-line fix (strip `/* */` as well), and it is the same weakness as
TST-C10.

---

## C — Low

| | Finding | Disposition |
|---|---|---|
| C1 | `testEveryWindowWearsTheIcon` has no floor on windows examined | open |
| C2 | `testEveryTestIsInTheBattery` cannot see a test method, only a test class | open |
| C3 | `testTheReversingRuleIsTestedWhereItLives` asserts `assertNotNull(SomeClass.class)` | open |
| C4 | `testEveryRuleIsCoveredSomewhere` is a coverage index, not coverage | open |
| C5 | `testRouteCommandParity`'s corpus omits four of eleven kinds; `TYPE_ROUTE` is unreachable in `testRoutes` | open |
| C6 | Bare `catch (Exception)` accepts any throwable as "rejected" | open |
| C7 | Ordering scans whose left term is not proved present | open |
| C8 | Loop-only assertions with no floor, each currently covered by a sibling | open |
| C9 | Self-referential oracles: expected value computed by the method under test | open |
| C10 | Method-body scans that do not strip comments | open |
| C11 | `testAutonomyGroundTruth` and other classes silently skip on a working-directory change | open |
| C12 | `testTheDerivedGraphCanBeExportedForInspection` has no assertions and writes into the repo root | open |
| C13 | Fixture constants asserted as results | open |
| C14 | Fixed ports and machine-dependent skips | open |
| C15 | Two `testLocomotiveIdentityPropagates` sweeps with narrow patterns and no floor | open |
| C16 | `testTheEditorsArrivalsAreNotCoupledToTheViewersSetting` checks for a method NAME | open |
| C17 | `testFacingFollowsTheTrack` opens a live session on the tracked fixture | open |

### TST-C1 — the window-icon test would pass on an empty source tree

`test/regression/testEveryWindowWearsTheIcon.java`. All three `@Test` methods build a `naked`/`spelling`
list and assert it is empty. `javaUnder(new File("src"))` returns an empty list when `listFiles()` is
null (`:236`), and nothing asserts how many windows were examined. Its two siblings guard this —
`testNoSelfRecursiveWrappers:75` (`assertTrue(checked > 0)`) and `testEveryTestIsInTheBattery:50`
(`build.exists()`).

Every anchor is live today: 10 window classes, 2 `new JDialog(` sites,
`applyWindowIcon` at `TrainControlUI.java:4508`, `locicon.png` in five hand-written files.

**Mutations that survive:** run from another directory; or introduce a new window base class
(`extends TrainControlDialog`) that `WINDOWS` does not list, so the next window is never asked. The
class's stated purpose — "a window added next month is required to ask" — is exactly what the missing
floor gives up. Cosmetic consequence, hence C.

### TST-C2 — the battery check is per class, not per test

`test/regression/testEveryTestIsInTheBattery.java`. I verified the list is currently **complete**: every
class on disk carrying `@Test` is in `build.xml` except `testAutoDetect`, and no `build.xml` entry names
a missing file. The mechanism is sound, including `withoutXmlComments` and the one-entry exclusion cap.

Two things it cannot see, both demonstrated in this review: a **method** losing its `@Test` (TST-B4), and
a class that runs but skips everything (TST-B1). The macro matches `**/<class>.java`, so a `build.xml`
entry naming a class that no longer exists would run zero tests and pass; nothing asserts the reverse
direction. A per-method count pinned per class — or simply asserting `Skips: 0` in the harness — closes
both.

### TST-C3 — a signpost test with no assertion in it

`test/core/testNonReversibleTrains.java:90-97`,
`testTheReversingRuleIsTestedWhereItLives`. The body is
`assertNotNull(testLayoutPickPath.class, …)`, a compile-time-guaranteed non-null. It keeps the class
*name* from rotting, which is what the comment says, but it is registered and counted as a test.

**Mutation that survives:** delete `testFullAutonomyDoesNotDriveThroughAReversingPoint` from
`test/core/testLayoutPickPath.java:467-491` and remove the reversing-point refusal from
`Layout.pickPath`. This test still passes. (The pointed-at test does exist today and is strong.) A
comment would say the same thing without occupying a slot in the count.

### TST-C4 — a hand-maintained claim that another file covers something

`test/core/testRoutePicking.java:64-65, 177-187`, `testEveryRuleIsCoveredSomewhere`. `COVERED_ELSEWHERE`
asserts that a rule is exercised in another file. It holds today —
`testAutoLayout.java:722-743` genuinely discriminates `LEAST_RECENTLY_VISITED` in both directions — but
nothing links the two.

**Mutation that survives:** make `Layout.costOf`'s `LEAST_RECENTLY_VISITED` branch return a constant and
delete `testAutoLayout.java:722-743`. This test still passes, still claiming every rule is covered. The
enum-shape half (a new constant fails it) is real and worth keeping.

### TST-C5 — parity corpora that do not cover what their javadoc claims

- `test/core/testRouteCommandParity.java:36-50`. The javadoc: "Every constructor `RouteCommand` offers is
  exercised, so a kind added later without a matching parse is caught here." The corpus omits
  `RouteCommandRoute`, `RouteCommandAutoLocomotive`, `RouteCommandAutonomyLightsOn` and
  `RouteCommandLightsOn` (`src/org/traincontrol/base/RouteCommand.java:118, 183, 204, 211`).
- `test/core/testRoutes.java:93-96` — the `types` array has **nine** elements and the index is
  `random.nextInt(8)`. `TYPE_ROUTE` sits at index 8 and is unreachable, so the `case TYPE_ROUTE` block at
  `:113-117` is dead: no generated route in `testJSONExportImport`, `testAddRemoveRoute` or
  `testJSONImport` ever carries a route-triggering command.

**Mutation that survives:** drop the NAME field from `RouteCommand`'s JSON serialisation for
`TYPE_ROUTE`. The only remaining coverage is `test/ui/testRouteCapture.java`. One-character fix:
`nextInt(types.length)`.

### TST-C6 — "it threw, therefore it was rejected"

`test/core/testLayoutBfs.java:290-306` (`testPointFromAnotherLayoutIsRejected`) and
`test/core/testLayoutBfsEquivalence.java:377-411` (`testBothImplementationsRejectAForeignPoint`). Both
accept any throwable; the first additionally requires only a non-null message, which modern
helpful-NullPointerException messages satisfy.

**Mutation that survives:** delete the
`if (start == null || end == null) throw new Exception(I18n.f("autolayout.errorInvalidPointsSpecified"))`
guard from `Layout.bfs`, so a foreign point NPEs downstream instead. Both tests pass; the operator-facing
message is gone.

### TST-C7 — ordering scans whose left term could be absent

Beyond TST-B7. `test/regression/testEditorSurfaceRules.java:2009`:
`assertTrue(body.indexOf("getBlockingPoints(station)") < body.indexOf("getNamedTiles()"))`. Both are live
(`AutonomyEditorPanel.java:3040` and `:3044`), and the direction that matters — moving the read below the
loop — does fail. But renaming or reparameterising `getBlockingPoints(station)` makes it `-1` and the
assertion passes vacuously.

I re-derived every other `indexOf` ordering in the suite. **Safe** (left operand proved positive by an
earlier `contains`/`assertTrue`, so an absent right operand gives `positive < -1` and fails):
`testEditorSurfaceRules.java:1728`, `testLocIconCrop.java:149`, `testLocMappingPages.java:149`,
`testStationLabelPrefill.java:131`, `testEditorSurfaceRules.java:1295`'s *left* term. **Broken:**
`testStationLabelDrag.java:275` (TST-B8) and `testEditorSurfaceRules.java:1295` (TST-B7).

### TST-C8 — loop-only assertions, each currently rescued by a sibling

Recorded because the rescue is incidental and the sibling can go.

- `test/core/testAutonomyDiagramPorts.java:252-266`, `testTrailingOnlyRestrictionRotates` — **the one not
  covered elsewhere.** Mutation: `TilePorts.ports(CUSTOM_PERM_LEFT, o, 0)` returns empty for `o > 0`. All
  four orientations run zero assertions and pass; the rotation of the trailing-only restriction — a
  defective switch drawn sideways — is asserted nowhere else. Its sibling
  `testDefectiveSwitchesAreTrailingOnly:230` has the `assertFalse(routes.isEmpty())` guard this one lacks.
- `testAutonomyDiagramPorts.java:200-210`, `testAutonomyDiagramTiles.java:165-175`,
  `testAutonomyDiagramMonitor.java:329-360`, `testTracedPathIsContinuous.java:80-117`,
  `testHomeStaging.java:208-216`, `testAdvancedRoutes.java:552`, `testRoutes.java:821`,
  `testTileSelection.java:61`, `testAutonomyDiagramSession.java:332-341`,
  `testAutonomyDiagramSession.java:3158-3192`, `testImportRename.java:456`,
  `testParseCS3Loks.java:219`, `testAutonomyDiagramSampleLayout.java:1093-1121` and `:1169-1174` — each
  can run zero iterations or degenerate to an empty-vs-empty comparison, and each has a named sibling
  that would catch the same mutation. `testImportRename.java:456`
  (`testUnknownLocomotiveIsNotProposed`) is the weakest of them: it creates no unknown locomotive at all,
  so **mutation:** `getLocomotivesToRenameFromImport()` returns `Collections.emptyList()`.
- `test/core/testParseCS3Loks.java:112`, `testParseCS3Loks` — calls four parse methods and asserts
  nothing. **Mutation:** `parseLocomotivesCS3` returns an empty list.

### TST-C9 — expected values produced by the code under test

- `test/ui/testDiagramLooksRight.java:1809` —
  `assertEquals(heading.getText(), session.describeTile(square))`. Both sides come from
  `AutonomySession.describeTile` (`AutonomySession.java:2108`). **Mutation:** delete the
  `store.getPointName(tile)` branch so it always returns `"12,7"` — the autonomy menu heads every square
  with coordinates instead of its name, which is the OB-112 symptom the test is named for, and both sides
  move together. The test already has an independent oracle in hand at `:1791`
  (`session.getStore().getPointName(tile)`) and does not use it.
- `test/ui/testDiagramExport.java:128-136`, `testAnAbsurdSizeIsCapped` — both sides read
  `DiagramExport.MAX_TILE_SIZE`. **Mutation:** change the clamp at `DiagramExport.java:102` to
  `Math.min(tileSize, MAX_TILE_SIZE / 4)`; `render(100000)` and `render(MAX_TILE_SIZE)` still match. It
  proves clamping happens, not that it clamps to the documented ceiling.
- `test/ui/testDiagramLooksRight.java:800` — the closing assertion of
  `testStationLabelsFollowTheColourPreference` compares against the value the `finally` just wrote.
  Self-documented at `:798-799` as a guard on the *restore*; flagged only so a later reader does not
  count it as a fifth assertion about `restingFill`.

### TST-C10 — body scans that read prose as code

`test/ui/testDiagramLooksRight.java:1060-1080`, `test/ui/testStationLabelDrag.java:299-322, 348-356`,
`test/ui/testLocIconCrop.java:124-156`. None strips comments.
`test/ui/testLocMappingPages.java:292` has a `withoutComments` helper written for exactly this hazard
("a check that did not strip comments would pass on the strength of the prose describing the code after
the code had gone"), and `testEditorSurfaceRules:229-238` records the same fix costing a green run.
Nothing is vacuous today — I checked each body — but `LayoutLabel.liftAboveLabels:1079-1136` is 40 lines
of comment to 8 of code.

**Mutation that survives:** delete `if (lift) keepCaptionsInFront(parent);` at `LayoutLabel.java:1119`
and leave a comment line reading `// keepCaptionsInFront(parent) is handled by the caller now`.

Same class, `test/regression/testEditorSurfaceRules.java:99-123`: the `toggle` assertion is nested inside
`for (int j …) if (lines.get(j).contains("private javax.swing.JCheckBoxMenuItem toggle(String text,
String tooltipKey"))`. If that signature is reformatted the inner loop never runs and no assertion is
made about `toggle` at all. Both overloads are live today
(`AutonomyEditorPanel.java:1742` delegates to `:1775`).

### TST-C11 — classes that go quiet when the working directory moves

`test/core/testAutonomyGroundTruth.java:57-71` resolves `new File("test/autonomy.json")` relative to the
CWD and throws `SkipException` if absent, so the whole class — 1,399 pinned station pairs, the strongest
pinning mechanism in the suite — reports no failures when run from anywhere but the project root.
`testAutonomySimulationSanity:118` resolves its fixture as a classpath resource and is the model.
(Its `testTheStationPathsHaveNotChanged:85` writes the pinned file when absent, but calls `fail()`
immediately after, so that half is deliberate and correct.)

Same shape, less consequence: `testConfirmedGoodState`, `testTheGoldenLayoutHoldsTogether`,
`testTimetableOnDerivedGraph`, `testReturnHomeOnRealLayout`. Under TST-B1 all of these read as green.

### TST-C12 — a test with no assertions that writes two files into the repository root

`test/core/testAutonomyDiagramSampleLayout.java:1138-1163`,
`testTheDerivedGraphCanBeExportedForInspection`. Two `write()` calls, zero assertions; `write()` at
`:1156` does `new File(name)`, i.e. the process CWD. `autonomy-derived.json` (30,938 bytes) and
`autonomy-derived-open.json` (174,171 bytes) are sitting in the project root, last written 2026-08-28
21:24.

[README.md](README.md) §"Three kinds of document" is explicit that a generated dump belongs in a
temporary directory. **Mutation that survives:** make `withCoordinatesFromTiles(pageOrder)` return `(0,0)`
for every tile, or ignore `pageOrder`.

### TST-C13 — fixture constants asserted as results

- `test/core/testAutonomyPathValidation.java:264` — `assertTrue(loc.getSpeed() == 0)` on a `dummyLoc()`
  that was never given a speed. **Mutation:** delete `loc.setSpeed(0)` at `Layout.java:2763`. A train
  left running onto an unset path is not detected.
- `test/core/testAutonomyDiagramReducer.java:236` — `assertEquals(between.get(0).getLength(), 0)` with no
  authored lengths; 0 by construction. Covered by `testLengthIsTheSumOfTheTilesCovered`.
- `test/ui/testUiStateIsNotLostWhenUnreadable.java:165` — `assertTrue(copy.length() > 0)`, subsumed two
  lines below.
- `test/core/testParseCS2Layout.java:120`,
  `testTheLayoutListIsStillSortedLexicographically` — compares `model.getLayoutList()` with a sorted copy
  of itself, trivially true at 0 or 1 pages, and the list comes from
  `LAYOUT_OVERRIDE_PATH_PREF` rather than the fixture. **Mutation:** `getLayoutList()` returns an empty
  list.
- `test/core/testAccessory.java:397`,
  `testEchoForADeletedAccessoryIsIgnored` — self-documented at `:391-395`: the exception is captured in a
  `Future` nobody reads, so "the pre-fix code would NOT have failed the middle assertion here." The
  honesty is right; it should not be counted as coverage of that guard.
- `test/core/testAutonomyDiagramSession.java:489-525`,
  `testRenamingAStationTouchesNoPage` — `before` is snapshotted at `:516`, after the migration has
  already rewritten `pageFile`, and the rename changes no component, so an identical rewrite is
  invisible. **Mutation:** have `setPointName` call the page-writing routine for every page carrying that
  station.

### TST-C14 — fixed ports, headless skips, and machine-dependent gates

- Fixed ports: `testParseWebServer.java:87` and `testImportRename.java:89` both bind **8080** via
  `CS3TestServer`; `testUdpMessagesReachTheWire.java:38` binds **15731**. A collision becomes a setup
  failure, which TestNG renders as a skip (see TST-B1).
- Whole-class headless skips: `testBusyDialogInteraction`, `testDiagramExport`, `testDiagramLooksRight`.
  Per-method: `testCommandTableMarks`, `testRouteEditorValidation`, `testRouteEditorShading`,
  `testRouteEditorLocked`, `testUiStateIsNotLostWhenUnreadable`. Partial: `testRouteCapture` (4 of 6),
  `testRenderingCost` (2 of 8).
- Would **error** headless rather than skip, which is the better outcome — noting it so nobody "fixes" it
  into a skip: `testLocMappingPages`, `testLocIconCrop`, `testTheWaitMarkIsAnHourglass`'s
  `testTheTimerActuallyRuns` and `testABlockedEventThreadDoesNotFreezeTheSand`,
  `testAutonomyPathValidation`, `testLayoutTiles`.
- `test/ui/testDiagramLooksRight.java:920`,
  `testTheFourFacingArrowsAreOneMatchedSet` skips unless `StationCaption.LABEL_FONT` resolved to
  `Segoe UI Symbol` — correct by design, but the OB-116 regression is unguarded on any non-Windows
  machine.
- `test/core/testMockCentralStation.java:403` skips when the machine rejects `192.0.2.1` quickly. Honest,
  but on a network with a rejecting default route it always skips.
- `test/core/testAutoDetect.java` — hardcoded `targetIP = "192.168.50.25"` and a real LAN probe; its
  `setUpClass:97-131` strips `final` via `Field.class.getDeclaredField("modifiers")`, which throws on
  Java 12+, and `PING_RETRY` is a compile-time constant so the reflection at `:74` changes nothing even
  when it works. Correctly excluded from the battery; recorded so nobody counts it as coverage.
- `test/ui/testRenderingCost.java:250-309`, `testDecodingTheTileImages` — the decode loop at `:286-294`
  catches `Exception` and continues; `decoded` and `distinct.size()` are printed, never asserted. The
  only assertion is `ms < 30000`. **Mutation:** make `LayoutDiagramComponent.getImage` throw for every
  tile — `decoded` is 0, `ms` is ~0, test green, printing `coldDecodes=0`. This is the "0.00ms looks like
  good news" failure the class's own `@BeforeClass` comment says it was rewritten to avoid; the guard was
  added for the reducer and not for the decode path.

### TST-C15 — two identity sweeps whose patterns are narrower than the rule

`test/regression/testLocomotiveIdentityPropagates.java`.

- `testEveryLocomotiveHolderIsNamedInTheSweep:338-372`. `holdersIn` matches only
  `private final Map<Locomotive,…>` / `Set<Locomotive>` / `List<Locomotive>`, and nothing asserts the
  returned list is non-empty. All seven current holders in `Layout.java` (`:390, 391, 410, 411, 424, 425,
  452`) match today. **Mutation that survives:** add a holder as
  `private Map<Locomotive, Long> lastSeenAt = new LinkedHashMap<>();` — no `final` — and leave it out of
  `locDeleted`. Invisible to the regex; a deleted locomotive stays held. A
  `ConcurrentHashMap<Locomotive, …>` declaration is invisible for the same reason.
- `testNoHomeOrPlacementIsComparedWithAName:389-464` is line-scoped, with no positive control and no
  floor. **Mutation that survives:** in any GUI class, write `Object home = point.getHomeLoc();` on one
  line and `if (name.equals(home))` on the next. It compiles, is silently always false — the defect class
  the javadoc names — and the scan never sees it, because it requires both tokens on the same line.
  Renaming the three accessors reduces the guard to zero comparisons, silently.

### TST-C16 — an absence check on a method name rather than on the coupling

`test/regression/testDiagramDrawingSettings.java:229-243`,
`testTheEditorsArrivalsAreNotCoupledToTheViewersSetting`.

**Mutation that survives:** have `AutonomyEditorPanel` read the preference directly —
`TrainControlUI.getPrefs().getBoolean(TrainControlUI.DIAGRAM_RESTRICTION_ARROWS, true)` — instead of
calling `diagramShowsRestrictionArrows()`. `assertFalse(panel.contains("diagramShowsRestrictionArrows"))`
passes, and the editor's chevrons are coupled to the viewer's setting exactly as Adam asked they not be.
Asserting the absence of `DIAGRAM_RESTRICTION_ARROWS` in that file as well closes it.

### TST-C17 — a regression test that opens a live session on the tracked fixture

`test/regression/testFacingFollowsTheTrack.java:57, 68` — `new AutonomySession(new File("test_layout"))`
then `session.open(pages)`. `AutonomySession.open` runs `migrateStationLabels`, which calls
`store.save()` and `page.saveChanges(null, false)` (`AutonomySession.java:1626-1707`). Its siblings
`testDiscardedEditsDoNotDeleteSetup` and `testErrorsStopTheSetupRunning` copy the fixture first,
"because both tests write"; this one does not. Those two copy only `config/autonomy` and hand the session
`LayoutDiagram` objects whose URLs still point at the real `test_layout/config/gleisbilder`, so
`saveChanges` there writes the fixture too.

Not live today — there are no `Point:` labels in `test_layout/config/gleisbilder/*.cs2`, so the migration
does not fire. It fires the day a `Point:<known station>` label is added to the fixture, and nothing
watches `test_layout`: both `testTheGoldenLayoutHoldsTogether` and `tools/battery.sh` fingerprint
`cs2_sample_layout` only. Hygiene, not a surviving mutation, but the fixture is tracked and a fixture
that moves is a suite measuring the fixture.

**Also minor, recorded rather than ranked:**
`testARouteDoesNotThrowSwitchesUnderATrain.testASignalMayBeSetREDOverAnOccupiedPlatform` skips its reason
assertion with a `println` when `signalWasActive.get(0)` is true — a documented race tolerance, and no
single production mutation both survives it and escapes the class's other assertions.
`testEditorSwitchClearsPageState.methodSource` resolves `arriveAt` via `indexOf(" arriveAt(")`, whose
first hit is the *call* at `LayoutEditor.java:4928`, not the declaration at `:4963`; it lands on the right
body only because there is no `{` between them, so a braced lambda there makes it fail loudly — fragile
rather than hollow. `testStationLabelsFollowMoves.testTheDiagramStillFindsTheNameAfterTheNudge:369-371`
accepts the caption at either square. `testSwitchingToACentralStationLayout.earliest():405-417` is dead
code.

---

## D — Checked and sound

These were read and found to hold. Recorded because what a review actually covered is the useful half.

**D1 — `test/core/testHomeStaging.java` (3,300 lines, 65 tests) is the strongest file in the suite.**
Nearly every test carries a "the fixture did not take" precondition, a same-graph control, and a
`MUTATION-CHECKED` note naming the production line and the resulting failure count. Plans are verified by
*replaying* them through `applyPlan`, which asks the production rule `Point.heldBackBy` rather than
restating it. The parity-audit family is correctly balanced — four "audit is silent" tests plus
`testTheParityAuditStillReportsADivergenceOnAWatchedStation` asserting it still returns 1, so "make
`auditAgainstRuntime` return 0" is not a surviving mutation.
`testAnUnchangedLayoutSerialisesTheSameWayTwice` documents its own near-miss (HashSet → LinkedHashSet).

**D2 — the BFS property tests follow the stated doctrine exactly.**
`testLayoutBfs.java` and `testLayoutBfsEquivalence.java`: fixed seed sequences (0..149 / 0..119) printed
in every context string, an independently written `referenceDistance` oracle, and real floors
(`reachable > 300`, `exhausted > 50`, `alternativesFound > 0`, `routesFound > 300`). The differential ran
a same-vs-same control and **recorded the result in the file** (`:293-300`), which is why repeated
exclusion is deliberately excluded from the comparison. The nondeterminism itself is pinned.

**D3 — `test/core/testLayoutPickPath.java`.** Every negative assertion has a same-graph positive control
(`testAPathThroughAReversingStationIsNotChosen`, `testYieldingIgnoresALocomotiveAutonomyWillNeverDispatch`,
`testATerminusMayNotBeDrivenThrough`, both `testAnInactive…` pairs). The autonomy/manual/staging tiering
is asserted from both sides, and the repetition counts (20/30/60) are justified against the shuffle.

**D4 — `test/regression/testTheCheckerAgreesWithTheBuild.java`** (apart from the missing floor in
TST-B18). Floors everywhere (`edges > 50`, `checked > 20`, `split > 30`, `stations > 10`), an oracle that
is genuinely independent (a plain BFS over the built edges, deliberately ignorant of arrival sides and
turn sets), and — unusually — **its blind spots written down with the run that found them**, twice. It is
the best-calibrated document in the folder.

**D5 — `test/regression/testEditorSurfaceRules.java`** (2,289 lines) apart from TST-B7 and TST-C10. It
strips comments before matching, records why (`:229-238` — a `finally` matched inside the comment
explaining the `finally`), floors its counting loops (`assertEquals(asks, 2)`, `raises.size() >= 2` under
the comment "The loop above asserts nothing if it runs over nothing"), proves each `bodyOf` result
non-empty before asserting absence, and anchors on the last parameter rather than the first line where an
overload would shadow. `testManageSitsUnderTheConfiguration` asserts all three positions are `>= 0`
before ordering them — the pattern TST-B7 and TST-B8 want.

**D6 — `test/regression/testEveryTestIsInTheBattery.java`.** Verified independently: all 123
`test-one-class` entries name a real file, and every class on disk carrying `@Test` is in the list except
`testAutoDetect`. `withoutXmlComments` is correct and its reason is recorded with the mutation run that
justified it. The exclusion-list cap and the "excused class exists" check are both real. Limits at
TST-C2.

**D7 — `test/regression/testJavadocsAreAttached.java`.** A ratchet with `assertEquals(found, ALLOWED)` as
well as `<=`, so an improvement must be banked and a regression fails with the file named. Guards its own
working directory. Sound.

**D8 — `test/core/testDiagramResize.java`.** Grow/shrink mirroring including the tile's own stored `y`,
the refusal on both the right column and the bottom row, the top row explicitly *not* an edge, the
one-column and one-row cases, and the empty-page bounds regression with a one-row control beside it. I
checked the layer: the refusal really is inside `LayoutDiagram.trimEdges` (`:557`), not only in the
caller, and `LayoutEditorRightclickMenu:453` asks the same predicate that guards the action — guard and
affordance agreeing, which is the rule this repo learned the hard way.

**D9 — `test/core/testAutonomyDiagramStore.java`** (2,694 lines) is the strongest of the autonomy files.
Page identity is pinned from both sides (rename vs renumber, with the untouched page asserted as hard as
the changed one, and the stored key `"2:3,3"` checked on disk); failure atomicity proves the throw *and*
that the store is neither emptied nor half-refilled; reconciliation asserts kept-vs-dropped in both
directions with the CR-C3 control a reviewer's mutation exposed; locomotive repair covers all three
name-holders plus the timetable, with a real prefix control.
`testTwoRoutesAcrossOneSquareKeepTheirOwnDirections` is exemplary — asymmetric mirrored `RouteId`s plus a
never-set third route as the "not answering too broadly" control.

**D10 — the reducer's barred-arrival trio** (`testAutonomyDiagramReducer.java:62, 130, 1049`) is the
best work in the diagram suite: each carries an explicit control run before the barred run, each asserts
that `findPath` and `reachableTiles` *agree*, and each states its surviving mutation. `arrivalSideAt`
throws if the fixture did not build the edge, so a broken fixture errors rather than passes.

**D11 — `testAutonomyDiagramPorts.java` (all 17).** Pure data pinning of `TilePorts` with real controls:
`testTheWholePortTableIsWhatTheMapSaysItIs:558` asserts `table.size() == componentType.values().length`
first, so a new tile type cannot slip in unstated, and `testEveryComponentTypeIsClassified:38` is the
same fence from the other side. Apart from TST-C8.

**D12 — `testAutonomyDiagramTiles.java` (17 of 19).**
`testATrainCanWalkThroughAPairedPortalBothWays:385` walks approach → portal → jump → partner → track
beyond in both directions and is explicitly the version that would have caught the earlier hollow portal
test. `testAWalkCannotChangeTracksAtACrossing:255` carries positive controls before its two negatives.

**D13 — `testAutonomyDiagramMonitor.java` (14 of 18).** The two real-track tests (`:422`, `:554`) run the
real `GraphReducer` and stub only the three `Layout` methods `compute` reads. The pixel tests isolate by
*difference* against a control rendering rather than betting on absolute pixels.
`testASquareWithARunningTrainComesToTheFront:1079` asserts both the lift and the release, with
preconditions establishing the starting z-order.

**D14 — `test/ui/testTheWaitMarkIsAnHourglass.java`** answers the "drives internal state by hand"
question head-on: `testTheTimerActuallyRuns:432` and
`testABlockedEventThreadDoesNotFreezeTheSand:552` let the real `javax.swing.Timer` fire instead of
calling `advanceOneFrame`, and the class says so. Sand conservation, direction, in-bounds at 21 frames,
glass ceiling and centring are all real pixel measurements.

**D15 — `test/ui/testRouteEditorLocked.java`** (apart from TST-A8) is the strongest UI class: every
"is absent" sweep has a paired "is present on an ordinary route" control down the same code path, the
loop-vacuity guards are real (`cells > 0`, `fields.size() > 0`, `tables.size() >= 2`), and it records a
per-mutant verification run.

**D16 — `test/ui/testLocMappingPages.java`** is the model the rest of `ui/` should follow: the predicate,
the real menu affordance (greyed *and* tooltip naming the limit), the public entry point's own guard, and
a comment-stripping scanner.

**D17 — `test/ui/testDiagramLooksRight.java`'s core** (apart from TST-C9):
`testACaptionNeverMovesAnythingElse:1264` waits for tile decode, changes text *and* font and asserts zero
movement of >10 components; `testARebuiltDiagramIsNotTakenOffTheScreen:1867` holds a decode open by hand
and includes a fresh-panel control that defeats "never hide anything";
`testEveryPixelOfARunLandsOnTrack:201` is a difference-of-two-renders invariant with a per-square minimum
that defeats edge bleed. `testDiagramExport`'s leak trio (`testANewGridRetiresTheOneItReplaces`,
`testAReplacedGridIsNotRetained`, `testADozenEditorCyclesRetainNothing`) are bounded GC loops with a
live-reference control.

**D18 — the protocol and file layer.** `testLoadData` (seven real old-build fixtures with per-kind
counts, plus `testEveryRouteInEveryFixtureStillHasItsCommands` asserting the loop counter against the
manifest); `testAtomicWrite` (completed write lands — the control — failed write preserves prior
contents, no staging file left, new-file case, backup archive contents); `testInvalidInput` (~40
`Layout.fromJSON` rejection paths, each group carrying an accept-case control, plus the "no half-created
accessory" check with its own emptied-address precondition); `testMockCentralStation` (HTTP-fetch parity
with disk plus an "addresses are not all identical" control, and 404/garbled/refused/black-hole fault
injection); `testControlStationFaults` (short system frame not read as STOP, *with* the real-stop control
beside it); `testCS2Message`; `testCS3NotFoundDetection` (both branches, including the pre-2.6.0
HTTP-200-with-error-body one); `testUdpMessagesReachTheWire` (actual bytes off a socket, before and after
a forced reopen); `testCentralStationDetection` (whose two source-scan patterns I verified live at
`CSDetect.java:206` and `:231`).

**D19 — `test/core/testTimetableCaptureThroughARealRun.java`** drives the real
`parseAuto` → `AutonomyRefreshCallback.attach` → `runLocomotives` path with a capture-off differential
control and a `moved` precondition rather than a skip — and its OB-114 note explains why it refuses to
skip where `testTimetableOnDerivedGraph` does.

**D20 — the editor round trips.** `testCommandRow`, `testConditionOutline`, `testConditionRows`,
`testThreeWaySwitch`, `testTileSelection`, `testRouteRoundTrip`, `testRouteReachesTheRails`,
`testMultiUnitMembership`, `testLayoutRenameKeys`, `testFeedback` (the clock-went-backwards gate with the
forward-moving control that stops "return true always" passing) — refusal cases each backed by their
positive control.

**D21 — no stale source scans.** Every regex and substring anchor in the suite matches real code in the
file it names at HEAD `eac0e392`. I checked all 31 `bodyOf(...)` method signatures, the `AutonomyChecks`,
`AutonomyEditorPanel`, `LayoutGrid`, `LayoutEditor`, `LayoutLabel`, `TrainControlUI`, `CSDetect`,
`DiagramExport` and `messages.properties` anchors individually. The scanning defects in this review are
all of a different kind — matching the wrong occurrence (TST-B8), matching only presence and order
(TST-A3, TST-B7), or having no floor on what was scanned (TST-B16, TST-C1).

**D22 — no disabled tests, no swallowed assertions in `expectedExceptions`, no test naming
`cs2_sample_layout` as a write target.** Zero `@Test(enabled = false)`, zero
`expectedExceptions = Exception.class`, and the only references to the live railway folder are the
read-and-fingerprint one (TST-B2) and prose.

**D23 — `test/regression/testAutonomyStoreSettingsMatrix.java` and its neighbours.** The matrix (12
settings × 8 columns) carries a reflection guard on unclassified fields, an asymmetric `RouteId(1,3)`,
and id-vs-name keying separated across four columns with measured mutations — it is the class written
for this project's commonest bug shape and it does the job.
`testStoreCollectionsAreHandledEverywhere`: all 15 site names verified to resolve to real declarations in
`AutonomyCompanionStore.java`, comment-stripped. Sound with them:
`testAutonomyTileMove`, `testCancelRestoresPlacements` (including the LD-4 note-shape cases and the
`deleteEverything` control), `testDeleteAndInsertKeepTheSetup`, `testDiscardedEditsDoNotDeleteSetup`,
`testDataSafetyRoundTrips`, `testLayoutFolderRobustness`, and the store half of
`testStationLabelsFollowMoves`.

**D24 — `test/regression/testPageIdsAreDurable.java` (1,426 lines, 14 methods).** Every method carries a
"the fixture did not take" precondition, the two-absent-page fixture defeats the empty-the-hold mutant,
and `countIn` asserts deltas rather than absolutes. This is the reference implementation of the
precondition rule in [README.md](README.md) §Testing.

**D25 — the running-railway regressions.** `testBothProtectingSignalsAreThrown`,
`testARouteDoesNotThrowSwitchesUnderATrain`, `testStationBlockedByAnotherPoint`, `testLayoutReloadFence`
(with its no-version-bump control), `testStuckTrainAdvisory`, `testAutoLayoutRace` (the
`activeLocomotives` CME race, with an explicit anti-vacuity guard on both thread counters),
`testTimetableCapture` (capture-off control plus the parseAuto-survival case), and
`testTriggerWaitsSayNothing` — for which the advisory path was traced end to end
(`MarklinLocomotive.waitedTooLongFor` → `network.logf("autolayout.warnLocomotiveWaitingLong", …)`) and
both the wording check and the sensor-name fallback confirmed to catch the "advisory moved into the
shared two-argument door" mutation.

**D26 — the regression suite's source scans, all anchors verified as code today.**
`testHomeAssignmentRules` (7 of 7, all at real call sites); `testEditorSwitchClearsPageState` (all five
clears plus `takeTheUndoPoint(` inside the real `arriveAt` body, and `MUST_BE_UNMOUNTED` equal to the
four fields actually assigned); `testDiagramDrawingSettings` (the defaults count is exactly 2, and every
`showStaticAutonomyLayer` guard is present and in order); `testSwitchingToACentralStationLayout`
(`settleAbsentPages` at `:1875` precedes `final java.util.List<String> absent` at `:1896` and contains
`!isLocalLayout()`; `layoutLoaded` counts 1/2; `showLayoutTab()` 10 >= 9; the splash is gated on `showUI`
in real code and closed twice, with 382 characters inside the `finally`);
`testLocomotiveIdentityPropagates`' `model.renameLoc(` count of 2 and `repairLocomotiveOnDisk`.

**D27 — small and well-controlled.** `testTrainMarkIsNotBlank` (the control asserts blankness first),
`testBackupArchiveNamesTheLayout`, `testRouteEditorRoundTripCases` (order read off the *parsed* commands,
TD-6 fixed), `testAutonomyLabelShowsLocomotiveName`, `testBarredArrivalIsNotADestination`,
`testRenameRoundTripThroughTheUIPath` (drives `LayoutPageEdit.renameOrDuplicate`, the real menu call, and
asserts on `errorCount` deltas), `testAMovedTileCarriesItsSetup`, `testFacingFollowsTheTrack` (a
hand-derived table rather than the code's own oracle — see TST-C17 for its fixture hygiene),
`testConfirmedGoodState` (`test/baseline/` is present, so it does not skip).

---

## Patterns worth carrying forward

1. **The extracted rule is tested; the call site is not.** TST-A3 (call-site half), TST-A7, TST-A8,
   TST-A9, TST-A10, TST-A13, TST-A14, TST-A15, TST-B19, TST-B21, TST-B22. **Eleven instances in one
   pass**, and they include the three highest-consequence findings here. It is the dominant defect class
   in this suite, as it has been in the production code. Three sub-shapes are worth naming separately,
   because they read as coverage from inside the file:
   - the test supplies the call the caller was supposed to make (TST-A10, TST-A13, TST-A14);
   - the test asserts a rule with **no production caller at all** (TST-B21 — and `AutonomySession`'s
     caption API in TST-A9 is the same thing from the other side);
   - the test names the gap and delegates it to a sibling that does not cover it (TST-A15, and TST-C4).

   A cheap sweep that would have found most of them: for every public method a test calls directly,
   `grep src/` for its callers. Zero callers, or callers with no test, is the finding.

2. **A floor is the cheapest thing in a test and the most often missing.** TST-A12, TST-B12, TST-B16,
   TST-B18, TST-C1, TST-C8. The suite already knows this — `testEditorSurfaceRules:2076` writes "The loop
   above asserts nothing if it runs over nothing" and adds one, `testLayoutBfs` floors every generator,
   `testTheCheckerAgreesWithTheBuild` floors four separate populations. The rule is not being applied
   uniformly, and the places it is missing are not the places anybody looked at last.

3. **A test in the mode that disables the thing it asserts.** TST-A4 is the purest form: the fixture turns
   on simulate, simulate short-circuits the validation, and the test asserts the validation never fired.
   Worth asking of any test whose fixture sets a global mode.

4. **The javadoc is the specification and it is checkable.** TST-A1, TST-A2, TST-A15, TST-B8, TST-B15,
   TST-B21 and TST-C5 were each found by taking a test's own claim literally and checking it — "every key
   the reader looks for is listed here", "MUTATION: moving X below the early return fails this", "held
   separately by that test", "if a second call site ever appears". Every one of those seven claims was
   false, and two of the classes describe the exact defect they then contain (TST-B21's DD-A6 note,
   TST-B23's own comment). The prose in this suite is unusually good, which is precisely why it is worth
   checking rather than trusting: it is detailed enough to be wrong.

5. **`ant test` is not the gate the README says it is.** TST-B1 and TST-C2. Everything the July and August
   cycles learned about skips-reading-as-green lives in `tools/battery.sh`, which `build.xml` does not
   call and `test/README.md` does not mention.
