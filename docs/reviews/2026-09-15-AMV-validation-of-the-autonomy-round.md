# The autonomy round, validated

**Status:** open 2026-09-15 - round 2 fixed B1, C1, C2, C4, C5, C6 and C7 (claims and fix `c02f7000`); C3 answered in the PTR document

**Prefix:** AMV (checked free, with AMG, AMS, AMR and AMH: `SELECT DISTINCT ref FROM finding` in `docs/manual-tests/triage.db`, every declaration spelling in `docs/reviews/`, and a grep of `src/`, `test/` and `docs/`)

**Covers** one Opus validation of two rounds at once: PTR round 1 (`19a680f3`, `b4776f6b`, `0df8aaa3`, `a14883e4`) and AM round 1 (`8818d8cd`, `64169b0b`, `864471df`, `a866da72`) - the four Fable reviews of the whole autonomy model.  Read-only, nothing run.

**What it confirmed.**  13 of the 15 items put to it: PTR-B1 (the fallback is reached only when nothing was found, and cannot give the terminus sentence for another reason), PTR-C1, PTR-C3, PTR-C4, AMS-A1, AMS-B1, AMS-B2, AMS-C1, AMS-C2, AMG-B1, AMG-C1, AMG-C2, AMR-B2, AMR-C1 and AMH-B1's mechanics.  MT-442 it held in code and could not settle for the railway; a probe on the frozen snapshot has since answered it - from both copies of BottomInner a train can stand on, every one of the five squares says *"Contains an intermediate terminus station"*, which is what the test expects.

**On the pending tests.**  B1 adds MT-449 and bears on MT-445.  C4 corrects MT-443.  C5 corrects MT-444.

---

## A - wrong behaviour on the layout

None.

---

## B - incorrect results or refusals in specific configurations

| id | status | where |
|---|---|---|
| AMV-B1 | Fixed | `HomeStaging` - a train that cannot reverse could be turned mid-move and sent on |

### AMV-B1 - the ruling governed where a move ended, not what its route did on the way

| | |
|---|---|
| **Disposition** | Fixed, to Adam's ruling |

AMH-B1 restricted where a plan may REST a train that cannot reverse.  A route may also turn one at a reversing point in the middle of a move - which is how such a train backs into a berth (MT-245, and the 2026-08-31 ruling an existing claim pins) - and then carry on, arriving somewhere else the wrong way round.  So behaviour.md's new sentence and MT-445 said more than the code did.  Adam, asked whether his ruling covers a turn mid-move: *"It should be the first option, but the locking mechanism will refuse it.  That's why we started the 2 step process for parking, which is OK in my opinion."*

**Confirmed by running.**  `core.testHomeStaging.testATrainThatCannotReverseIsNotTurnedMidMoveAndSentOn` - X and H either side of a reversing point, with a spare only the train that cannot reverse may use.  Red: *"turned on the way and sent on: HS bravo -> HS Y"*.  Its control is the same railway with a train that can reverse.

**Fixed, round 2.**  A route that turns a train that cannot reverse is allowed only where the move ends at its home, or at a square it comes to rest facing out of - a berth or a terminus, which is the backing-in case the earlier rulings protect.  MT-449.

---

## C - documentation, tests, minor

| id | status | where |
|---|---|---|
| AMV-C1 | Fixed | `Layout.bfs` - "every caller in the application passes excludePaths" |
| AMV-C2 | Fixed | the census's `ON_THE_WAY` javadoc still said the list is empty |
| AMV-C3 | Answered | PTR-C2's write-up credited the wrong assertion |
| AMV-C4 | Fixed | MT-443's second expectation could not fail before the fix |
| AMV-C5 | Fixed | on Manual, being too short replaced the menu's own reason |
| AMV-C6 | Fixed | behaviour.md said a switched-off station's reason comes from the route check |
| AMV-C7 | Fixed | two second halves unpinned |

### AMV-C1 - the comment about `bfs`'s callers

PTR-B1's fallback passes null, so "every caller in the application passes excludePaths" stopped being true the moment it was written.  **Fixed.**

### AMV-C2 - the census javadoc

It still described `ON_THE_WAY` as empty, with any square appearing meaning the pass-through check was back; it has carried one entry since OB-229.  **Fixed.**

### AMV-C3 - PTR-C2's write-up

The band floor and the subset check already failed if the listed square stopped refusing, so the new line can never be the first to fail.  **Answered:** the PTR document now says so.

### AMV-C4 - MT-443's second expectation

The FR-001 standing-train rule is asked of a path's DESTINATION, and its lock edges are about a route already running - so a train routed THROUGH the demoted square was never held up by a train merely standing on the watched one.  **Fixed:** the step now sends a train TO the square.

### AMV-C5 - which reason the operator sees

A physical refusal outranks a preference (MT-262), which replaced the menu's own reason where the station was also too short.  Adam: **"The menu's reason."**  **Fixed,** with a claim: `core.testWhyStuck.testByHandTheMenusReasonOutranksBeingTooShort`.

### AMV-C6 - behaviour.md on a switched-off station

**Fixed:** the reason is the menu's, which is asked first.

### AMV-C7 - the unpinned halves

The AMS-B1 claim had no control for a declined rebuild, and nothing pinned AMH-B1's no-home half.  **Fixed:** `regression.testCancelUndoesAutonomyEdits.testADeclinedRebuildLeavesTheDeclinedEditGuardUp` and `core.testHomeStaging.testATrainWithNoHomeThatCannotReverseIsNeverTurnedAside`.

---

## D - looked wrong and is not

| id | what |
|---|---|
| AMV-D1 | `whyNoRouteFitsTo` needs no terminus fallback: it only swaps a standing or menu bar for a length refusal, and where every route passes a terminus the bar that remains is the tier's real answer |
| AMV-D2 | A PLACEMENT edit declined during a run is still overwritten by the clearing rebuild - `putTheTrainsBack` keeps the railway's position for a train the gesture did not name, which is Adam's OB-183 ruling |

---

## What the passes missed

- **A rule fixed at the end of a move, and stated as a rule about the whole move.**  The fix, the document and the manual test all said "turned on the way only to go home"; only the test's oracle was asked whether the code said it too, and it was written to match the fix rather than the sentence.
