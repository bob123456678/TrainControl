# Seven days of commits, reviewed after the MT-333 ruling and today's fixes

**Status:** open 2026-09-14 - round 1 fixed (C1, C2, C3), validation running; WK7-B1 held for MT-432

**Prefix:** WK7 (checked free: `SELECT DISTINCT ref FROM finding` in `docs/manual-tests/triage.db`, and every declaration spelling in `docs/reviews/*.md`)

**Covers** `git log --since=2026-09-07`, about 215 commits from `ce7ba45e` to `cda45698`, weighted towards the newest: the MT-333 room rule (`d3271e48`, `e677cbab`), OB-223's Cancel (`244b07c2`), FR-080 and MT-429 in Why Not Moving (`89d3156d`, `a48cb5e6`), FR-084 (`b1cb1fe9`), the route condition editor (`a5a51ac3`, `e4651cec`), and the TDR/FTN rounds and MT sweep of 2026-09-13/14.  One Fable reviewer read them, read-only, told not to repeat the SEV, TDR and FTN reviews unless a fix recorded there was itself wrong, told the MT-333 ruling as Adam confirmed it so as not to report the intended behaviour, and told to mark any finding that touches a hands-on test Adam has not run.  Adam, 2026-09-14: *"iterate on any of its findings that don't conflict on a pending test"* - so a finding that does is recorded and held.

---

## A - wrong behaviour on the layout

| id | status | where |
|---|---|---|
| (none) | | |

---

## B - incorrect results or refusals in specific configurations

| id | status | where |
|---|---|---|
| WK7-B1 | Held for MT-432 | `Point.arrivedAlong`, `TrainControlUI.whereTheTrainsAre` / `putTheTrainsBack` - a driven train's route is lost at every rebuild |

### WK7-B1 - a driven train's tail stops following its route after any rebuild

| | |
|---|---|
| **Disposition** | Held |

`Point.arrivedAlong` lives in memory only.  `whereTheTrainsAre` records a standing train's square and arrival side, and `putTheTrainsBack` restores those two and not the route - so every rebuild of the running layout forgets it, and `walkStandingTrains` falls back to the fork rule for a train that has not moved.  Every setup gesture rebuilds (`rebuildRunningLayoutFromSetup`), and so does closing the autonomy editor.

**Scenario.**  75 407 DB, length 3, driven from Tunnel to BottomMainA: a second train at Tunnel is refused BottomMainB and C, as `regression.testAPassingTrainMayStandAcrossThePoints.testThreeUnitsAtBottomMainAClosesTunnelToBAndC` pins.  Set a home or a caption from the diagram's right-click menu, or open and close the autonomy editor: the train is put back with its side and no route, the walk stops at BottomMainAPre, and B and C are offered again over a tail still lying on the Tunnel run.

Introduced with the route-following tail in `d4f09f5d`, whose javadoc says a rebuild loses it; MT-333's note of 2026-09-14 listed the gestures and offered to carry the route through a rebuild.  **Held, not changed:** it touches MT-432, the protrusion test Adam has not run, whose steps and warning are written around this behaviour.

---

## C - documentation, tests and minor

| id | status | where |
|---|---|---|
| WK7-C1 | Fixed | `Layout.whyTooLongForThisRoute`, `measuredRoomAtTheEndOf`, `theApproachItselfHoldsIt` javadocs; `HomeStaging` - five comments still state "every square on the route" |
| WK7-C2 | Fixed | `LayoutEditor.discardAutonomyWork`; the bulk-clear warning - Cancel now brings cleared locomotives back and the warning says it does not |
| WK7-C3 | Fixed | `core.testATrainIsJudgedOnlyWhereItStops.testASquareWithNoSwitchBehindItIsNotJudged` - cannot fail for what it names |

### WK7-C1 - five comments state the old "every square on the route" reading

| | |
|---|---|
| **Disposition** | Fixed |

The javadoc of `Layout.whyTooLongForThisRoute` ("Every square on the route, not only the last one ... refused even where the train would not have stopped there") and its body comment ("AT EVERY SQUARE THE ROUTE RUNS THROUGH"); `measuredRoomAtTheEndOf`'s javadoc ("how whyTooLongForThisRoute asks the question at every square a train runs through"); `theApproachItselfHoldsIt`'s javadoc; `HomeStaging` ("AT EVERY SQUARE, NOT ONLY AT THE BERTH ... wherever that stretch is") and the leg-2 note below it.  Left by `e677cbab`, which changed only the loop comment.  The code does the opposite (`if (!comesToRest) continue;`), and the javadoc is what a reader meets first.

**Fixed, round 1.**  The five comments say where the train comes to rest - the destination and a square it turns at - and that a standing train's blocking is a different mechanism; the planner's note says it asks exactly where the runtime asks.  Comments only.

### WK7-C2 - Cancel now brings bulk-cleared locomotives back, and the warning says it does not

| | |
|---|---|
| **Disposition** | Fixed |

`discardAutonomyWork` restores the snapshot taken when the editor opened, placements included; `autonomyEditorClosed` rebuilds the running layout from it, and `putTheTrainsBack` only moves trains that were standing, which the cleared ones are not - so they come back where the setup puts them, and `captureRunningLayout` writes that.  Before OB-223 the discard re-read a file the per-gesture save had already written without them.  The OB-194 warning - *"Cancel will not bring them back"* - and OB-223's own javadoc now say the reverse of what happens; `regression.testTheBulkClearWarnsThatCancelWillNotUndoIt` pins only the wording.  In Adam's favour, but the sentence he asked for is false.

**Fixed, round 1, after confirming it by running.**  `regression.testCancelUndoesAutonomyEdits.testCancelPutsBackTheLocomotivesABulkClearTook` clears every locomotive through the real Bulk Tools door, presses the real Cancel, and asserts the placements are back in the setup and on the running railway - green before any wording changed, which is the reviewer's reading confirmed.  Mutation: make the discard re-read the file instead of restoring the snapshot, and it goes red.  The warning now says Cancel puts them back and Save keeps the change, in eight languages; the javadocs in `AutonomyEditorPanel` and `LayoutEditor` say why it once said the opposite; `testTheBulkClearWarnsThatCancelWillNotUndoIt` is renamed `testTheBulkClearSaysWhatCancelDoes`.

### WK7-C3 - a claim in `testATrainIsJudgedOnlyWhereItStops` can no longer fail for what it names

| | |
|---|---|
| **Disposition** | Fixed |

`testASquareWithNoSwitchBehindItIsNotJudged` pins "the condition that separates the rule from the artefact" - a passed square judged only where a switch bounds it.  That condition and `roomAfterASwitchOnTheWay` were removed, so no passed square is judged at all and the claim is met by any implementation of the rule, including one where judging switch-bounded passed squares came back.  The class javadoc still describes the condition as live.

**Fixed, round 1.**  The claim is removed, and the class javadoc says the condition and its claim went with the pass-through check.

---

## D - looked wrong and is not

- **WK7-D1** - the direction follower writes the setup with the editor open and has no editor-open guard, so a Cancel reverts a facing the railway reported.  Not new with OB-223: nothing saves on that path, so the old re-read discarded it the same way, and the running layout is re-captured at the next open (DIR-B3).
- **WK7-D2** - `a48cb5e6` touched seven of the eight bundles: German already read `(Seite {1})`.  All eight carry the same keys, ASCII only, and no lone apostrophe in a pattern with arguments.
- **WK7-D3** - `HomeStaging`'s mid-route turn asks neither the platform relaxation nor the berth rule; the runtime applies both at the destination only, so the two agree.
- **WK7-D4** - `ConditionOutline.whatIsWrong` flags a joining word deeper than either neighbour; no outline that `write`/`read` produce has one, checked against `(A or B) and C`, `A and (B or C)` and `3 or ((1 or 2) and 4)`.

---

## Checked and found sound by the reviewer

MT-333's mechanism (2) - the destination and a turning square only, the 2026-09-12 relaxation at the destination only, the censuses discriminating - and mechanism (1) untouched (`whyABerthCannotHoldIt`, `walkStandingTrains`, `spendableAllowance`); OB-223's snapshot timing, Save path, page and mode switching, exit path and rename repair, with `hasUnsavedAutonomyWork` and `discardAutonomyWork` the only two doors; FR-080/MT-429's `composeWhy` (key filled before lookup, grouping as `whyNotReport`, one lock, page suffix, a discriminating order claim); FR-084's retest guard and the clearing of the last test; the route condition editor (first-line indent, kind re-choose against `asShown`, Switch/Signal translation, markup escaping, per-row renderer reset, the tooltip path); the consist functions and the Return Home spinner; no new monitor taken on the wrong thread.
