# Validation of the WK7 fix round of 2026-09-14

**Status:** closed 2026-09-14 - round 2 fixed (WKV-B1, WKV-B2, and what WK7-C1 and WK7-C2 had left) and validated (`2026-09-14-WKW-fix-validation-round-two.md`); WKV-C1 waits for Adam

**Prefix:** WKV (checked free: `SELECT DISTINCT ref FROM finding` in `docs/manual-tests/triage.db`, and every declaration spelling in `docs/reviews/*.md`)

**Validated:** round 1 of `docs/reviews/2026-09-14-WK7-seven-day-review.md`, commit `eccb7b3a`, by one Opus validator, read-only, told the MT-333 ruling as Adam confirmed it and the hands-on tests he has not run.  Adam, 2026-09-14: *"iterate up to 2x with an opus validator"*.

---

## The WK7 findings

| id | verdict | what was left |
|---|---|---|
| WK7-B1 | Held correctly | The reason overstated MT-432: its steps drive and look, and do not rebuild; its warning lists opening the editor but not a home or caption set from the diagram's right-click menu.  Still held - carrying the route through a rebuild changes what MT-432 checks. |
| WK7-C1 | Fix incomplete | Test comments still gave the old "every square" reading: `testTheWashIsNoLongerThanTheTrain`, `testHomeStaging`'s leg-2 note, and `testTheLengthGuardsOnTheRealLayout`'s RampDown claim - its summary, reasoning, MUTATION paragraph, a comment and three assertion messages. **Fixed, round 2.** |
| WK7-C2 | Fix incomplete | `testSwitchingToACentralStationLayout` still named the old class; the renamed class's javadoc spoke in the present tense of Cancel not undoing; `issues.md` OB-194 and OB-223 said Cancel does not put placements back. **Fixed, round 2**, the tracker by dated notes rather than by rewriting what was true when it was written. |
| WK7-C3 | Confirmed fixed | |

---

## A - wrong behaviour on the layout

| id | status | where |
|---|---|---|
| (none) | | |

---

## B - incorrect results or refusals in specific configurations

| id | status | where |
|---|---|---|
| WKV-B1 | Fixed | `AutonomyEditorPanel.clearLocomotivesWarning` - the track diagram's own menu promised a Cancel it does not have |
| WKV-B2 | Fixed | `LayoutEditor.maySettleBeforeExit` - Discard when closing the application did not put back the locomotives a bulk clear took |

### WKV-B1 - the track diagram's Clear All Locomotives says Cancel puts them back

| | |
|---|---|
| **Disposition** | Fixed |

Round 1 changed the bulk-clear warning to *"Cancel puts them back; Save keeps the change"*.  The same item is on the track diagram's own right-click menu - the panel served with no page - where there is no Cancel: the panel saves the setup on every change, and `TrainControlUI.buildAutonomyTileMenu` offers no menu at all while an editor is open, so no editor's Cancel can reach it.  Before round 1 that door said Cancel would not bring them back, which happened to be true there.  Introduced by `eccb7b3a`.

**Confirmed by running.**  `regression.testTheBulkClearSaysWhatCancelDoes.testTheDiagramsOwnMenuDoesNotPromiseACancel` builds the real diagram menu through `buildAutonomyTileMenu` and read red: *"This will take every locomotive off the setup (4): EN57-203, 75 407 DB, 2-8-4 3505 SP, EN57-947.  Their home assignments are kept.  Cancel puts them back; Save keeps the change."*

**Fixed, round 2.**  With no page the warning uses `autolayout.ui.confirmClearLocomotivesAtOnce`, which says the clear is saved at once, in all eight languages; the editor's door keeps its own sentence.

### WKV-B2 - Discard on the way out keeps a bulk clear

| | |
|---|---|
| **Disposition** | Fixed |

Closing TrainControl with the autonomy editor open asks Save / Discard / Cancel.  Discard restores the setup as the editor opened it, inside `settleUnsavedWork` - but the running layout was rebuilt after the clear and still has no trains on those squares, and the save on the way out (`TrainControlUI.saveState` → `captureFromLayout`) folds the running layout back over the setup and writes it.  The editor's Cancel does not have this because `autonomyEditorClosed` rebuilds from the restored setup before it captures.  From OB-223 (`244b07c2`), which made the exit's Discard restore a snapshot the capture then overwrites.

**Confirmed by running.**  `regression.testCancelUndoesAutonomyEdits.testDiscardOnTheWayOutPutsBackTheLocomotivesABulkClearTook` clears through the real Bulk Tools door, answers Discard in the real `maySettleBeforeExit`, then runs the exit's fold (`captureRunningLayout`: the same `captureFromLayout` and `saveWithoutReconciling` under the same conditions, without writing the operator's `UIState.data`, since the exit itself ends in `System.exit`).  Red: *"Maps do not have the same size: 0 != 4"*.

**The validator's "point settings" half is not a defect.**  `testDiscardOnTheWayOutPutsTheArrowsBack`, the same exit with an arrow changed, was green before the fix: the fold does not carry arrows.  Kept as the control.

**Fixed, round 2.**  After a Discard in autonomy mode `maySettleBeforeExit` asks for the same rebuild Cancel gets, so the capture folds a railway built from the restored setup.  Placed there because the exit's save runs before `completeExitDiscard`; the setup is already restored at that point, so an exit refused afterwards leaves the setup and the railway agreeing, as after a Cancel.  `regression.testEditorSurfaceRules.testLeavingCompletesBothHalvesOfADiscard` still holds: the settle is asked, and the track-mode undo still waits for the exit to be certain.

---

## C - documentation, tests and minor

| id | status | where |
|---|---|---|
| WKV-C1 | Held for Adam | MT-415 and MT-430, both passed, expect the bulk-clear warning to say Cancel will not bring the locomotives back |

### WKV-C1 - two passed hands-on tests describe the old sentence

| | |
|---|---|
| **Disposition** | Held |

MT-415 and MT-430 were passed by Adam against *"Cancel will not bring them back"*.  Since OB-223 Cancel does, the warning says so (WK7-C2), and on the diagram's own menu it says the clear is saved at once (WKV-B1).  **Not changed:** these are Adam's passed results.  OB-223, which he asked for, settled that Cancel undoes the setup; what is held is only the text of two tests he passed.  **Corrected after the second validation (WKW):** what Cancel does now is undo a clear, not a placement - a locomotive placed in the editor stays placed after Cancel, because the rebuild that follows a placement names the train and Cancel's does not - so MT-430's *"Placing ... is still not undone"* is still true, and only *"clearing"* in it is stale.

---

## D - looked wrong and is not

- **WKV-D1** - the arrow half of WKV-B2, above: Discard on the way out already kept arrows put back, run.  **Corrected after the second validation (WKW-C1):** arrows are the one setting the exit's fold never carries, so this answered nothing about the rest - the fold writes placements, `active`, `maxTrainLength`, `speedMultiplier`, `priority`, `home` and `excludedLocs`, and before round 2 a discarded home was kept too.  Round 2's rebuild covers them; `testDiscardOnTheWayOutPutsTheHomeBack` pins one.
