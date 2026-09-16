# The autonomy setup and session, reviewed whole

**Status:** open 2026-09-15 - round 1 fixed A1, B1, B2, C1 and C2 (claims `8818d8cd`, fix `64169b0b`); C3 answered by those claims; B1's missing control added in round 2 as AMV-C7 (`c02f7000`)

**Prefix:** AMS (checked free, with AMG, AMR, AMH and AMV: `SELECT DISTINCT ref FROM finding` in `docs/manual-tests/triage.db`, every declaration spelling in `docs/reviews/`, and a grep of `src/`, `test/` and `docs/`)

**Covers** the whole of `AutonomyCompanionStore`, `AutonomySession`, `TileAnnotation`, `TileOverlay` and `AutonomyRefreshCallback`, followed into `TrainControlUI` (capture, rebuild, put-back, exit), `AutonomyEditorPanel`, `LayoutEditor`, `LayoutRightclickAutonomyMenu` and `AutonomyViewerPanel.load` - one of four Fable reviewers Adam asked to look at the full autonomy model rather than the latest fix (AMG, AMS, AMR, AMH).  Read-only; every fix below was run.

**On the pending tests.**  B1 touches MT-267 and MT-326, and adds MT-446.  B2 adds MT-443.

---

## A - wrong behaviour on the layout

| id | status | where |
|---|---|---|
| AMS-A1 | Fixed | `AutonomySession.captureFromLayout` - pages in play counted a stand-in for a page that would not read |

### AMS-A1 - a capture pruned every setting on a page whose file would not read

| | |
|---|---|
| **Disposition** | Fixed |

`captureFromLayout` prunes each configuration entry whose square is not in the graph, on the pages in play - every page not excluded.  A blank stand-in for a page whose file would not read (NSV-B3) carries the real page's name and one text tile, so every placement, home, priority and exclusion on that page was pruned, and the capture's callers wrote it: opening and closing the autonomy editor, the exit save, every configuration load.  The MT-135 loss FV3-A1 closed for `save()` and `pagesSafeToJudge`, by the one loop of four that did not skip the stand-in.

**Confirmed by running.**  `core.testAutonomyDiagramSession.testACaptureDoesNotJudgeAPageThatWouldNotRead`.  Red: *"a capture removed the priority on a page whose file would not read ... expected [7] but found [null]"*.  Its control - a setting on a square the loaded page does not have is pruned - holds before and after.

**Fixed, round 1.**  The loop skips `page.isUnreadable()`, as the other three do.  No MT: a page that will not read is a OneDrive placeholder, not something to stage by hand.

---

## B - incorrect results or refusals in specific configurations

| id | status | where |
|---|---|---|
| AMS-B1 | Fixed | `TrainControlUI.setupEditDeclinedDuringRun` - never cleared |
| AMS-B2 | Fixed | `AutonomySession.setStation(tile, false)` - kept "Unavailable While Occupied" |

### AMS-B1 - the declined-edit guard stayed up for the rest of the session

| | |
|---|---|
| **Disposition** | Fixed |

An edit declined as a run starts (MT-267) sets a flag that stops every fold of the running layout back into the setup (ACC-B3, WKW-B2).  Its javadoc said *"never cleared"*, on the worry that telling whether a later rebuild covered the same point is bookkeeping that goes wrong quietly.  But a rebuild covers every point: it builds the whole railway from the setup, edit included, and puts the trains back where they stand.  After the run the guard went on refusing at every door, so the editor opened on pre-run placements, homes were set at pre-run squares, the exit saved no positions, and the next start put every train back where it stood before the run - OB-183's consequence, kept alive by the guard.

**Confirmed by running.**  `regression.testCancelUndoesAutonomyEdits.testTheDeclinedEditGuardEndsWhenARebuildCarriesTheEdit`.  Red: *"the running layout was rebuilt from the setup - the declined edit with it - and the guard is still up"*.

**Fixed, round 1.**  `rebuildRunningLayoutFromSetup` clears the flag once the load has REPLACED the running layout with a valid one.  A load that declines - a confirmation refused, a setup that will not build - leaves it up.  The field's javadoc and the capture's comment say so.  MT-446.

### AMS-B2 - a demoted station kept its restriction, with no menu to clear it

| | |
|---|---|
| **Disposition** | Fixed |

`setStation(tile, false)` swept the caption, the barred arrivals and the protecting signal, not the FR-001 restriction.  The build emits its lock edges for any square carrying one, so every route into the demoted square still locked every approach to the watched square; the menu item that clears it is offered on stations only.  Re-promoting was the only way past.

**Confirmed by running.**  `regression.testStationBlockedByAnotherPoint.testDemotingTheStationTakesTheRestrictionWithIt`.  Red: *"the square is no longer a station and still carries 'unavailable while the yard is occupied'"*.

**Fixed, round 1.**  Demotion clears it with the other three.  MT-443.

---

## C - documentation, tests, minor

| id | status | where |
|---|---|---|
| AMS-C1 | Fixed | `forgetPlacementsElsewhere` - left `arrivedFrom` and `arrivedAlong` on the square a train was moved off |
| AMS-C2 | Fixed | `putTheTrainsBack` - discarded `moveLocomotive`'s answer |
| AMS-C3 | Answered | no test for A1, B1 or B2 |

### AMS-C1 - the third placement door did not clear the tail

`placeLocomotive(tile, null)` and `clearEveryPlacement` clear `loc`, the facing, `arrivedFrom` and `arrivedAlong` (IND9-A1, WK7-B1); placing the same train elsewhere cleared the first two, and rebuilt the graph once per square swept.  Traced harmless today by the reviewer, a trap for the next reader.  **Confirmed by running:** `core.testAutonomyDiagramSession.testMovingATrainInTheSetupTakesItsTailOffTheSquareItLeft`, red *"expected [null] but found [WEST]"*.  **Fixed:** both keys cleared, one rebuild.

### AMS-C2 - a train the rebuilt railway refused left a tail on the square

`moveLocomotive` refuses a square that is not a destination; `putTheTrainsBack` then wrote the arrival side and road onto it regardless.  **Confirmed by running:** `regression.testAnEditedPlacementSurvivesTheRebuild.testATrainThatCannotBePutBackLeavesNoTailBehind`, red *"expected [null] but found [north]"*.  **Fixed:** the side and road go back only where the train did.  The reviewer's "and nothing says so" is not a defect: `moveLocomotive` logs its refusal to the model's log, which is the log the application hands in - the claim's second assertion was withdrawn before the fix, and says why.

### AMS-C3 - the tests

**Answered:** the five claims above.

---

## D - looked wrong and is not

| id | what |
|---|---|
| AMS-D1 | Cancel does not undo a train moved in the autonomy editor: the railway wins and the close capture writes it back.  Deliberate - `LayoutEditor.discardAutonomyWork`, WKW-B1 accepted |
| AMS-D2 | `captureFromLayout` replaces `globals` wholesale; the only key set outside `Layout.toJSON`, `pathPreference`, is emitted by it, so the round trip holds |
| AMS-D3 | `putTheTrainsBack` of a non-reversible train backed into a terminus succeeds: `moveLocomotive`'s terminus refusal is commented out (MT-245) |

---

## What the passes missed

- **A guard with no way past, justified by a cost it did not have.**  "Never cleared" was defended as avoiding per-point bookkeeping - and the event that makes it safe to clear, a full rebuild, needs none.  Adam's standing rule is that he would rather have no check than an over-strict one.
