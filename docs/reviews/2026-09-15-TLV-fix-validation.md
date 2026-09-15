# Validation of the tail feature's round 1, 2026-09-15

**Status:** open 2026-09-15 - round 2 fixed in `8b8ec4ad` (TLV-A1, B1, B2, B3, C1-C3; TLR-B3 no longer held); the battery's one failure on round 1 explained and its claim corrected; validated (`2026-09-15-TLW-fix-validation-round-two.md`), round 3 completed TLV-A1 and narrowed TLR-B3 to square and lane

**Prefix:** TLV (checked free with TLR and TLW before the review began)

**Validated:** round 1 of `docs/reviews/2026-09-15-TLR-tail-feature-review.md`, commit `fd3f251e`, by one Opus validator, read-only, told Adam's design for the tail question and the hands-on tests he has not run.

---

## The TLR findings

| id | verdict | what was left |
|---|---|---|
| TLR-A1 | Confirmed fixed | Either half of the fix (pass order, by-place test) alone turns its claim green; neither is pinned on its own.  Recorded. |
| TLR-A2 | Confirmed fixed | Every door writes the side and road on the copy carrying the train.  **But see the battery note below.** |
| TLR-B1 | Fix incomplete | The same rule applied at the platform, where the walk could not honour the answer (TLV-B1); "two roads" wording left behind (TLV-C1).  **Fixed, round 2.** |
| TLR-B2 | Fix incomplete | Right for the list; with TLR-B3 held it left such trains no way to be given a road (TLV-B2).  **Fixed, round 2**, by fixing TLR-B3. |
| TLR-B3 | Held, reason inaccurate | Adam's rule stops the tail where track SPLITS; two copies of one square are not a split.  **Fixed, round 2.** |
| TLR-C1, C2 | Confirmed fixed | |
| TLR-C3 | Confirmed fixed | Not for roads that part and meet again (TLV-B3).  **Fixed, round 2.** |
| TLR-C4 | Confirmed fixed, caveat | TLV-C3.  **Fixed, round 2.** |
| TLR-C5 | Fix wrong | TLV-A1.  **Fixed, round 2.** |
| TLR-D1..D3 | Agreed | |

**The battery on round 1 (`5f900fe1`)** - 252 of 253 classes green.  `regression.testOneChangeSticks.testTheArrivalSideSurvivesTheRoundTrip` set an arrival side on a station with no train on it and expected it to survive a capture; TLR-A2 now drops a side read from a copy with no train, the way a departed train's tail is dropped (RGD-B1).  No door writes a side without a train - the arrival writes it for the train that arrived, the three doors for the train just placed, and the menu offers it only where a train stands - so the claim now stands a train on the station first and asks the same question.  *This changes a test's fixture to fit a fix, and is listed for Adam as such.*

---

## A - wrong behaviour on the layout

| id | status | where |
|---|---|---|
| TLV-A1 | Fixed | the three placement doors - a door that asks nothing erased a driven train's road |

### TLV-A1 - re-placing a driven train, or OK in its dialog, erased its road

| | |
|---|---|
| **Disposition** | Fixed |

TLR-C5 made every door write the road "either way", and `askAfterPlacement` returned no road both for Not Known and for no question - so pasting a train back onto the square it stands on, pressing OK in its locomotive dialog, or closing the question, wrote "no road" over the one autonomy drove it in on, and B and C opened again behind 75 407 DB.

**Confirmed by running.**  `regression.testTheTailCanBeGivenInTheEditor.testAPasteThatAsksNothingKeepsTheRoad` (the real Control+V, same square, nothing asked) and `testDismissingTheQuestionKeepsTheRoad`.  Red: *"nothing was asked, and the setup lost its road"*, *"the question was closed without an answer, and the setup lost the road the train had"*.

**Fixed, round 2.**  `askAfterPlacement` answers a `TailCrossedPrompt.Answer`, which tells an answer (a sensor, or Not Known) from no question or a closed dialog, and the doors rewrite the road only when it was answered or the side it follows from changed.  `testNotKnownOnAPasteForgetsAnOldRoad` is rebuilt on the same-square paste, where it reaches a state an operator can (TLV-C2).

---

## B - incorrect results or refusals in specific configurations

| id | status | where |
|---|---|---|
| TLV-B1 | Fixed | `Layout.walkStandingTrains` first hop - a fork right behind the platform was asked about and the answer ignored |
| TLV-B2 | Fixed | the fork rule counted a square's copies as two roads, so such trains could never be given one (TLR-B3) |
| TLV-B3 | Fixed | `TailCrossedPrompt.label` - roads that part and meet again read the same |

### TLV-B1 - the walk's first hop ignored the road

| | |
|---|---|
| **Disposition** | Fixed |

The first hop picks by arrival side alone and takes the first rail it meets.  With two rails coming in from the west right behind the platform, the question was put, "L" answered, and the walk covered K -> S and left L -> S - where the train stands - open.  Since `53a82f7c`; TLR-B1 widened it.

**Confirmed by running.**  `core.testTheTailCrossedQuestion.testTheAnswerPicksTheRailRightBehindThePlatform`.  Red: *"TQ_L was answered and the rail from it, right behind the platform, is not covered"*.

**Fixed, round 2.**  The first hop follows the road where it names one of the rails on the arrival side (`roadBackAtTheFirstHop`); the road match is one method, `cameFromAlong`, shared with every later hop.

### TLV-B2 - one square under two names stopped the tail (TLR-B3)

| | |
|---|---|
| **Disposition** | Fixed |

The fork rule counted a junction's neighbours by name, so a lane copy and a turning copy of one square read as two roads and the tail of a train with no road stopped at the junction - and with the question rightly not put there (TLR-B2), nothing could extend it.  Adam's rule is *"end locking at the switch"* - where track splits - and two copies of one square are not a split.

**Confirmed by running.**  `testATrainWithNoRoadFollowsOneRoadUnderTwoNames`.  Red: *"the tail stopped at the junction. Covered: [TQ_J -> TQ_S]"*.

**Fixed, round 2.**  Neighbours and walked squares are counted by place (`placeKey`, `walkedAPlaceLike`).  **This blocks more track behind some trains with no road** - correctly, the tail of a train reaching past such a square lies on it - and the battery is the check that nothing else moved.

### TLV-B3 - roads that part and meet again read the same

| | |
|---|---|
| **Disposition** | Fixed |

"via" named the next sensor towards the train, and two roads that part and meet again before it read "A (via N)" both.

**Confirmed by running.**  `testRoadsThatMeetAgainAreToldApart`.  Red: *"two different roads read the same in the list ... [... TQ_A (via TQ_N), ... TQ_A (via TQ_N)]"*.

**Fixed, round 2.**  Where one sensor does not tell two choices apart, the label names every sensor on the way.

---

## C - documentation, tests and minor

| id | status | where |
|---|---|---|
| TLV-C1 | Fixed | "two roads" still in `TailCrossedPrompt`, `AutonomyEditorPanel`, behaviour.md 5c and MT-435 |
| TLV-C2 | Fixed | the TLR-C5 claim reached an unreachable state; its second assertion could not fail |
| TLV-C3 | Fixed | the locomotive dialog's question was owned by the dialog that had just closed |

**TLV-C1.**  Reworded everywhere to *a junction with two roads back and a sensor crossed on at least one*; behaviour.md 5c and MT-435 also say that closing the question, or re-placing a train when nothing is asked, keeps its road.

**TLV-C2.**  Rebuilt on the same-square paste (TLV-A1), with both stores checked.

**TLV-C3.**  The question is owned by the main window (`edit.parent`), as at the other two doors.

---

## D - looked wrong and is not

- **TLV-D1** - TLR-A2's RGD-B1 removal is now redundant (the operational-key loop replaces or removes both keys) and harmless; left.
