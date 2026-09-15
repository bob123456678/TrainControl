# Second validation of the WK7 fix rounds, 2026-09-14

**Status:** closed 2026-09-14 - WKW-B2, WKW-C1, WKW-C2 and the remaining text fixed; WKW-B1 held for Adam

**What happened to each finding is in the status column and in `docs/manual-tests/findings.tsv`.**  The repairs were made after this document was written and were not validated by a further round - Adam capped the validation at two (*"iterate up to 2x with an opus validator"*) - so the code they touch is held by its own claims, seen red first, and by the battery.

**Prefix:** WKW (checked free: `SELECT DISTINCT ref FROM finding` in `docs/manual-tests/triage.db`, and every declaration spelling in `docs/`, `test/` and `src/`)

**Validated:** round 2, commit `3fb00394`, against `2026-09-14-WK7-seven-day-review.md` and `2026-09-14-WKV-fix-validation.md`, by one Opus validator, read-only, told the MT-333 ruling as Adam confirmed it and the hands-on tests he has not run.

---

## The round-2 items

| item | verdict | what was left |
|---|---|---|
| WKV-B1 | Confirmed fixed | `page == null` is only the diagram's tile menus (`TrainControlUI.java`, the one `new AutonomyEditorPanel(session, null, ...)`); that menu is refused while an editor is open and saves every change.  Two ways the claim could pass on a different string are noted: it does not check the warning names a locomotive, and the tooltip half matches any tooltip holding the count. |
| WKV-B2 | Confirmed fixed | A moved train, a pending turn, a direction learned by the follower and a rename all survive the exit rebuild.  A refused exit and a run in progress cannot coincide with an open editor.  The `captureRunningLayout` stand-in for `saveState(false)` makes the same calls. |
| WK7-C1 | Fix incomplete | `testTheLengthGuardsOnTheRealLayout`'s note on start squares still gave the old reading; `testWhyRampDownIsRefused` was still named in that class, in `testATrainIsJudgedOnlyWhereItStops` and in `testALongTrainIsOfferedNothingOnAOneUnitRailway`; `build.xml` still described the room rule as asked at every square; the MUTATION paragraph named "the first assertion", which is a precondition.  **Fixed, round 3.** |
| WK7-C2 | Confirmed fixed | The text holds; the warning's promise has one exception, WKW-B1. |
| WK7-B1 | Held correctly | The revised reason said MT-432 names opening the editor; it names closing it.  **Corrected.** |
| WKV-C1 | Held correctly, reason inaccurate | Cancel undoes a clear, not a placement, so MT-430's *"Placing ... is still not undone"* still holds, and OB-223 already settled the design - what is held is the text of passed tests.  **Corrected.** |

---

## A - wrong behaviour on the layout

| id | status | where |
|---|---|---|
| (none) | | |

---

## B - incorrect results or refusals in specific configurations

| id | status | where |
|---|---|---|
| WKW-B1 | Held for Adam | `TrainControlUI.putTheTrainsBack` - "Cancel puts them back" is false when a cleared square has been given to another train |
| WKW-B2 | Fixed | `TrainControlUI.captureRunningLayout` - opening the editor removed a setup edit declined during a run |

### WKW-B1 - a cleared locomotive is lost when its square went to another train before Cancel

| | |
|---|---|
| **Disposition** | Held |

A stands on S.  Clear All Locomotives; place B on S in the editor, whose rebuild names B so it stands on S on the railway; press Cancel (or close the application and Discard).  The restored setup has A on S and the rebuild regenerates it - then `putTheTrainsBack` puts B back on S, because B was standing there and is not named, and `moveLocomotive` clears the square, taking A with it.  The capture writes S = B.  A is gone from the setup after the warning promised it back.  A single-square Remove followed by the same placement does the same.

**Held, not changed.**  Fixing it means Cancel undoing a placement wherever it collides with one the restored setup puts back, and MT-430, which Adam passed, says *"Placing ... is still not undone by Cancel"*.  Which answer wins on that square - the editor's placement or the setup as it opened - is his call.

### WKW-B2 - opening the editor removes a setup edit declined during a run

| | |
|---|---|
| **Disposition** | Fixed |

An edit made as a run starts reaches the file and not the running layout; the message promises it is picked up at the next load, and since ACC-B3 the exit save skips its fold while `setupEditDeclinedDuringRun` holds.  `captureRunningLayout` is the same fold at five other doors - opening an editor, closing one, re-downloading the diagram, renaming and deleting a page - and did not ask.  So stop the run and open the autonomy editor: the layout built before the edit is folded over the configuration and the edit is removed, and the exit then logs that it skipped its capture to protect an edit that is already gone.  Rare - it needs the edit and Start in the same event - and older than round 2.

**Confirmed by running.**  `regression.testCancelUndoesAutonomyEdits.testADeclinedEditSurvivesTheNextCapture` leaves the state that race leaves - a home written to the setup and the file without a rebuild, and the flag set - and runs the fold.  Red: *"the fold removed the home that was declined during the run ... expected [EN57-203] but found [null]"*.

**Fixed, round 3.**  `captureRunningLayout` returns while the flag holds, the trade ACC-B3 made at exit: for the rest of the session no door folds the running layout back, and train positions are carried across rebuilds by `putTheTrainsBack` rather than written.  MT-326, next to this, stops and quits without opening the editor, so its steps are unchanged.

---

## C - documentation, tests and minor

| id | status | where |
|---|---|---|
| WKW-C1 | Fixed | WKV-B2 and WKV-D1 understated what the old exit Discard kept, and the arrow control could not catch it |
| WKW-C2 | Fixed | MT-347, passed, still expects the destination refused for a square the route only passes |

### WKW-C1 - the exit's fold carries homes, and nothing pinned one

| | |
|---|---|
| **Disposition** | Fixed |

`captureFromLayout` folds placements, `active`, `maxTrainLength`, `speedMultiplier`, `priority`, `home` and `excludedLocs`, so before round 2 a home discarded on the way out was written back too.  The arrow control tests a tile direction, the one setting the fold never carries, and WKV-D1 recorded the whole point as not a defect.

**Fixed, round 3.**  `testDiscardOnTheWayOutPutsTheHomeBack` sets a home, rebuilds as the editor's home door does, answers Discard in the real `maySettleBeforeExit` and runs the fold.  Green on round 2's code; with the exit rebuild removed from `maySettleBeforeExit` it and the placement claim go red.  WKV-D1 and WKV-C1 are corrected in their document.

### WKW-C2 - MT-347 describes the old rule

| | |
|---|---|
| **Disposition** | Fixed |

Adam passed MT-347 on 2026-09-12: *"The destination is not offered, and the notice names the square just past the switch."*  Since MT-333 that destination is offered.  **Fixed** as a dated note on MT-347, beside MT-415 and MT-430 - not a change to his result.

---

## D - looked wrong and is not

- **WKW-D1** - `testEditorSurfaceRules.testLeavingCompletesBothHalvesOfADiscard`'s MUTATION note says calling `completeExitDiscard` from `maySettleBeforeExit` fails it, and a duplicate call would not.  Older than these rounds; a moved call does fail it, which is the fault the claim was written for.
