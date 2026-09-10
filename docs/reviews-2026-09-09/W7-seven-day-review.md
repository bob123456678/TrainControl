# W7 - Seven-day review (2026-09-01 .. 2026-09-08)

**Status:** closed

*Every finding in this document was worked in the rounds of 2026-09-09 and 2026-09-10; a finding's state lives in `docs/manual-tests/triage.db` and its mirror `docs/manual-tests/findings.tsv`, which is where it has lived since the reviews folder was retired. Added 2026-09-10 (E8V-C4): `docs/reviews/README.md` says a document with no status line is open, and these seven were relying on that default.*

**Prefix for citing these findings elsewhere:** `W7`

## Scope and method

285 commits in the window. Read by theme, not commit by commit. Everything below was found by
reading; per the review constraints nothing was executed.

**Read deeply:**

1. **Reversal and direction** - the 09-06 arrivedFrom/facing cluster, the 09-07 "ignored, not
   queued" ruling (`bc6120f1`), the 09-08 wire-watch and narrowing revert (`e23f26d1`), OB-189
   (`8f909537`, `41913ecd`), the `eeee62c7`/`c9098a9b` paste sweep and revert, and the current
   state of `Layout.executePathInternal`, `reversedOnArrival`, and
   `TrainControlUI.reconcileFacingWhenIdle`.
2. **Length, tail and covered track** - MT-262 (`716cf3f5`, `306ccfd6`),
   `whyTooLongForTheBerth`/`measuredRoomAtTheBerth`, the HomeStaging seams (room at `to`,
   `canRest` capacity), and MT-309's two drawing commits (`6bb0a0fc`, `e2d6e4de`) with the
   covered-track plumbing in `TrainControlUI` and `LayoutLabel`.
3. **Editor surfaces and the capture/rebuild order** - MT-337 both halves (`1728986e`,
   `05c7f48e`), MT-246's nine announcing doors, `captureRunningLayout` and all five of its call
   sites, `rebuildRunningLayoutFromSetup`, `putTheTrainsBack` and its new regression test,
   MT-313 (`8f277e8e`).
4. **The 09-08 defect round** - MT-247 (`c22c9d90`) in full including the current
   `MarklinRoute.execRoute`, OB-187 (`003eea5d`), OB-188, MT-334, MT-337.

**Read moderately:** the look-and-feel/title-bar/scale sequence (`1d30eca6`, `c50b5f4c`,
`0f028080`) - commit messages plus spot checks; `testEditorSurfaceRules` for test-shape;
`behaviour.md` and `open-questions.md` against the week's rule changes.

**Skimmed by commit message only:** the review-catalogue migration to `triage.db`
(`af642884`..`22f3d302`), the message-bundle churn, `build.xml` battery wiring, the manual-tests
doc churn, and the big 09-02/09-03 review-fix rounds (those were themselves multi-validator
rounds and are the most-reviewed code of the week).

**Not read:** `LayoutGrid` beyond its MT-337 seam, `HomeStaging` beyond the length seams, the
harness/runner tooling, the splash/OB-170 sequence, `cs2_sample_layout` (constraint).

---

## A - high

### A1 - MT-337's rebuild tiebreak teleports trains at the one door that never captures

**Files:** `src/org/traincontrol/gui/TrainControlUI.java:5860-5930` (`putTheTrainsBack`),
`:4408-4512` (`buildAutonomyTileMenu` - no capture), `:5958-6058`
(`rebuildRunningLayoutFromSetup`); `src/org/traincontrol/gui/AutonomyEditorPanel.java:7161`
(`setupChanged`), `:7190` (`rebuildRunningLayoutSoon`).

Two fixes from this week, each correct alone, interact:

- **MT-246 (09-03)** made every setup gesture on the track-diagram right-click autonomy menu
  announce, so each one now ends in `rebuildRunningLayoutFromSetup` (via the borrowed
  `AutonomyEditorPanel`'s `setupChanged` -> `rebuildRunningLayoutSoon` ->
  `parentWindow().rebuildRunningLayoutFromSetup(true)`).
- **MT-337 (09-08)** flipped `putTheTrainsBack`'s tiebreak: where the rebuilt layout already
  places a train, the setup's answer now wins and the pre-rebuild running position is discarded
  ("WHERE THE REBUILD PUT IT, which is where the SETUP says it is").

MT-337's own justification is a *precondition*: "the setup is the newer of the two only because
`openLayoutEditor` captures the running layout before it constructs the editor"
(`TrainControlUI.java:6027-6035`). That order exists at every editor door - `openLayoutEditor`
(`:4939`), the page rename (`:24017`), the page delete (`:24173`), `resetAutonomySession`
(`:3021`), editor close (`:6147`). It does **not** exist at the diagram's right-click autonomy
menu: `buildAutonomyTileMenu` constructs the borrowed panel with no `captureRunningLayout`
anywhere on that path, and nothing captures when a run ends (`captureRunningLayout`'s own
javadoc: "Stopping autonomy does not capture; nothing else does either").

**Concrete failure:** run autonomy (or a manual send, or Return Home) so a train moves from A to
B; stop; right-click any square on the track diagram viewer and set a home locomotive, name a
station, or make any other setup edit. The rebuild fires, regenerates the running layout from a
setup whose placement for that train is still A, and `putTheTrainsBack` now *leaves it at A*
because the rebuild "has an opinion". The model, the diagram, and occupancy (which is
`currentLoc`, not s88 - the DW-A1 comment at `TrainControlUI.java:2963-2975` spells out that
"Start can then route one into a block that is physically occupied") all now say the train is at
A while it physically stands at B. Its `arrivedFrom` is also lost (`now != back`), so its tail
stops blocking. The next exit capture writes the wrong position to disk and makes it permanent.

Before MT-337 this door was correct (running answer won) and the editor doors were wrong (edits
undone); now the editor doors are correct and this door is wrong. Note the same week's REG7-A2
fix chose the *opposite* tiebreak for facing - "the setup now answers only when the railway has
no opinion" (`open-questions.md:63-70`) - each ruling right for its own door's capture order,
which is exactly how this will keep flip-flopping until the diagram-menu door either captures
first (as every editor door does) or the precondition is enforced rather than assumed.

### A2 - OB-189 fixed two of the three doors: a timetable or Return Home run ends with nobody told

**Files:** `src/org/traincontrol/automation/Layout.java:5081` (timetable `executePath`),
`:6764-6790` (the arrival toggle into `reversedOnArrival` - "on the shared arrival path"),
`:3053` (`takeReversalsOnArrival`, the only drain);
`src/org/traincontrol/gui/TrainControlUI.java:6524` (`reconcileFacingWhenIdle`, reached only
from `updateVisiblePoints` at `:26103`), `:21050-21108` and `:22470-22560` (both timetable
completion handlers - neither refreshes).

OB-189's diagnosis (09-08): a destination reversal is recorded by toggling
`Layout.reversedOnArrival`, and the window writes it to the setup in `reconcileFacingWhenIdle`,
which is *only reached from a diagram refresh*; autonomy refreshes constantly, hand-driven
journeys refreshed never. The fix added `updateVisiblePoints()` after the journey returns at the
two right-click doors (`AutoLocomotiveStatus.java:1078`,
`LayoutRightclickAutonomyMenu.java:1101`).

The third door with the same shape is the timetable - which **Return Home loads**
(`Layout.java:5807`). Its legs run through the same shared arrival path, so every terminus
arrival (behaviour.md section 6: Return Home backs trains into their home berths and "never asks
the operator anything") toggles `reversedOnArrival`. `reconcileFacingWhenIdle` correctly refuses
for the whole run (`isRunning()` is true end to end), so the flips accumulate to drain at the
first idle refresh - and no refresh comes: both completion handlers restore buttons, repaint the
timetable, and call `refreshReturnHomeButton`, but neither calls `updateVisiblePoints`.

**Concrete failure:** press Return Home; trains back into their termini and physically reverse;
the run ends; the diagram goes on showing every returned train facing the way it set off - the
exact symptom Adam reported as OB-189, on the tier he was driving when he reported MT-309. Worse
than cosmetic: dispatch a train *before* anything else happens to repaint (or exit the
application, whose capture writes the un-reconciled facing to disk), and paths are offered for
the wrong heading. The pending state self-heals on the next unrelated refresh, which is what
will make this intermittent and hard to re-report.

behaviour.md section 3 (lines 185-194) documents the levelling rule but assumes the refresh
fires; OB-190 (`issues.md`) asks Adam to confirm the fix on the manual doors only.

---

## B - medium

### B1 - behaviour.md section 5c still describes the drawing MT-309 replaced

**File:** `docs/reference/behaviour.md:365-377`.

Three statements moved on 09-08 and the document did not:

- *"Covered squares are **greyed on the diagram** until the train moves"* - since `e2d6e4de` a
  train is an opaque orange line along the road it lies on (`LayoutLabel.TRAIN_MARK`); nothing
  is greyed.
- *"**The wash survives a highlight.** ... the square goes back to being greyed ... asked again
  rather than remembered"* - the wash and its restore branch are gone; the mark is painted over
  whatever icon is present (`LayoutLabel.paintComponent`), so the sentence describes a mechanism
  that no longer exists.
- *"**What is drawn is what is refused.**"* - inverted by `6bb0a0fc`. The railway still blocks
  per edge; the drawing now walks the train's own length back from where it stands and draws a
  **subset** of what is refused (`AutonomySession.tilesCoveredByStandingTrains`: "the indicator
  saying WHERE THE TRAIN IS rather than what is unavailable"). An operator can now be refused a
  route over track showing no mark at all - the "no notice that can help state/debug this" shape
  Adam complained about in MT-262 - and the one document that should say so says the opposite.

Given Adam's acceptance criterion ("the behavior needs to be documented"), this is the week's
clearest documentation drift: both MT-309 commits edited behaviour-adjacent code and tests but
not `behaviour.md`, whose own preamble promises it states known limits.

### B2 - MT-247 changed what an s88-fired route does, and no reference document knows it

**Files:** `src/org/traincontrol/marklin/MarklinRoute.java:505-560, 634-820`;
`docs/reference/behaviour.md` (no route-execution section - see its section list);
`docs/manual-tests/findings.tsv:1233` (RGN-B2, Closed).

`c22c9d90` (09-08) replaced the whole-group refusal at the unattended door - one held turnout
dropped every accessory in the route - with a per-command skip: only the held accessory is left
alone, everything else including the route's other turnouts fires. That is Adam's ruling and the
code and all eight bundles now agree with each other. But:

- `behaviour.md` has **no section on route execution or accessory conflicts at all** - not the
  two-door doctrine (human door cancels whole / s88 door skips per command), not the
  emergency-stop carve-out (a stop-carrying route is never asked, at either door), not this
  week's change to it. The whole doctrine lives in `execRoute`'s comments. A rule this
  behavioural, changed this week, has no written statement outside the code.
- The comment's own open question - *"Whether an s88-fired route SHOULD go on driving trains
  over ironwork it did not set is Adam's question ... put to him as part of RGN-B2"* - points at
  a finding the store marks **Closed** ("behaviour ruled unchanged by Adam, 2026-09-03"). That
  ruling predates the 09-08 change. The question is now recorded nowhere that
  `open-questions.md` ("records what is still open") or the triage store would surface.

**Concrete failure:** not a runtime defect - an acceptance one. The next reviewer or Adam
himself reading `behaviour.md` to verify what a sensor-fired route does over a partly-occupied
path finds nothing, and the one pointer to the open half of the ruling leads to a closed row.

---

## C - low

### C1 - the MT-337 test's "OB-183 control" models a case real runs rarely produce, so the suite cannot see A1

**File:** `test/regression/testAnEditedPlacementSurvivesTheRebuild.java:143-190`.

The control test (`testATrainTheRebuildDroppedIsStillPutBack`) builds "the OB-183 case" as *the
rebuild placed nothing* - a setup with no opinion. But OB-183 as it happens on the railway is a
setup with a **stale** opinion: a run moved the train after the last capture, so the rebuild
places it - at the wrong square. In that shape the fixed code takes the MT-337 branch, and both
tests pass while the train teleports (A1). The pair proves "an edited placement survives" and
"an unknown train is restored", and between them leaves the actually-common third case - known
to the setup, moved since - asserted by nothing. A third test handing `putTheTrainsBack` a
rebuild whose placement *disagrees* with `standing` because of a move (not an edit) would have
to first decide A1's question - which is the point: the code cannot currently distinguish "the
setup is newer because an editor wrote it" from "the setup is older because a run outran it",
and neither can any test.

### C2 - the OB-189 fix is the same seventeen lines pasted at two doors, and the third sibling is the one that was missed

**Files:** `src/org/traincontrol/gui/AutoLocomotiveStatus.java:1061-1078`,
`src/org/traincontrol/gui/LayoutRightclickAutonomyMenu.java:1084-1101`.

The comment block and the `updateVisiblePoints` call are duplicated verbatim at both manual
doors. The week's own doctrine (`51a6a971`: "Both assignment doors commit the same way, through
one place") argues for the journey-end refresh living where the journey ends - one wrapper
around `executePath`'s return, or in `Layout` via the existing `CB_ROUTE_END` callback - rather
than at each caller. The cost of the per-caller shape is already visible: the timetable caller
never got it (A2), exactly the "fix one site, sweep the siblings" failure the sweep was for.

---

## D - minor

### D1 - testEditorSurfaceRules' restated wash guard still titles itself by the removed mechanism

**File:** `test/regression/testEditorSurfaceRules.java:1392` and `:1424`
(`testTheCoveredWashIsDrawnWhereItShouldBe`).

The javadoc heading and the method name still say "the covered-track **wash** survives a
highlight"; the assertions were correctly restated for MT-309 (the mark is painted, no
`addCoveredOverlay`, viewer-only), so this is naming only - but this file is the one the next
drawing change will be greppd for, and "wash" is now a word with no referent in `LayoutLabel`.

---

## What this pass could not check

- **Anything by running.** All findings are from reading; A1 and A2 in particular deserve a
  driven reproduction (A1: run, stop, right-click-edit, watch the placement; A2: Return Home to
  a reversing home berth, watch the facing after the run) before they are acted on.
- **What anything draws.** MT-309's orange line, the LAF/title-bar/scale outcome, OB-187's
  one-moment ungreying - all pixel questions this pass took on the commits' word.
- **The triage store migration.** `triage.db` is binary; I verified `findings.tsv` renders and
  is header-consistent, not that the migration lost nothing.
- **The 09-02/09-03 review-fix rounds in detail** - trusted as the most multiply-reviewed code
  of the week; a defect surviving four validator rounds would also survive my re-read.
- **Translations** beyond spot-checking MT-247's eight bundles for the changed conflict strings
  (present, ASCII-safe).
- **The harness and battery tooling**, and `cs2_sample_layout` (constraint).
