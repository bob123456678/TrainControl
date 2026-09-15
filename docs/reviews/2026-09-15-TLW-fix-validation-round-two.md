# Second validation of the tail feature's fix rounds, 2026-09-15

**Status:** closed 2026-09-15 - TLW-A1, B1, C2, C3 and C4 fixed in round 3; TLW-C1 recorded

**What happened to each finding is in the status column and in `docs/manual-tests/findings.tsv`.**  The repairs were made after this document was written and were not validated by a further round - Adam capped the validation at two (*"iterate up to 2x with an opus validator"*) - so the code they touch is held by its own claims, seen red first, and by the battery.

**Prefix:** TLW (checked free with TLR and TLV before the review began)

**Validated:** round 2, commit `8b8ec4ad`, against `2026-09-15-TLR-tail-feature-review.md` and `2026-09-15-TLV-fix-validation.md`, by one Opus validator, read-only, told Adam's design and the hands-on tests he has not run.

---

## The round-2 items

| item | verdict | what was left |
|---|---|---|
| TLV-A1 | Fix incomplete | The keep-or-replace decision read the setup, which a run does not tell (TLW-A1).  **Fixed, round 3.** |
| TLV-B1 | Confirmed fixed | |
| TLR-B3 / TLV-B2 | Confirmed fixed, with limits | Wrong at one topology (TLW-B1, **fixed**) and reaches further than "some trains" (TLW-C1, recorded). |
| TLV-B3 | Confirmed fixed | |
| TLV-C1 | Fix incomplete | Test messages and two javadocs still said "two roads" or described the old return (TLW-C2).  **Fixed, round 3.** |
| TLV-C2, C3 | Confirmed fixed | |
| New claims | none vacuous | gaps listed in TLW-C3, **closed in round 3** |
| Battery note (`testOneChangeSticks`) | Held correctly | no door writes a side on an empty square |

**The battery on round 2 (`83e3e27a`)** - 252 of 253 classes green.  `ui.testDiagramLooksRight.testTheTrainIsDrawnOverTheStationCaption` failed with *"No such child: 1"* - a component looked up before it was there - and passed 21 of 21 when the class was run again on the same commit.  Nothing in these rounds touches the diagram's drawing.  Recorded as intermittent, not fixed.

---

## A - wrong behaviour on the layout

| id | status | where |
|---|---|---|
| TLW-A1 | Fixed | the three placement doors - keep-or-replace read the setup, which a run does not tell |

### TLW-A1 - a door erased the road a train had just driven in on

| | |
|---|---|
| **Disposition** | Fixed |

A run writes its arrival - side and road - on the running layout only; the setup hears of it at the next capture.  The doors decided whether to keep the road from `session.getArrivedFrom`, so right after a run a paste back where the train stands, or OK in its locomotive dialog, saw no side in the setup, took the side as changed, and wrote "no road" over the one the train drove in on - B and C open again behind 75 407 DB.  And where a paste moved the train onto another copy of its square, the keep branch left the new copy with a side and no road.

**Confirmed by running.**  `regression.testTheTailCanBeGivenInTheEditor.testAPasteAfterARunKeepsTheRoadTheRunLeft` leaves a run's state - the road on the running Point, a setup that has not been told - and pastes the train back in place.  Red: *"the running railway lost the road the run left - the door read the setup, which had not been told of the run"*.

**Fixed, round 3.**  Each door reads the road, side and square the train had on the running layout before it moves it, and records - in both stores, so they agree - the answer, or with nothing answered that road where the train is still on the same square with the same side, and no road where it is not (`TailCrossedPrompt.Answer.roadToRecord`).

---

## B - incorrect results or refusals in specific configurations

| id | status | where |
|---|---|---|
| TLW-B1 | Fixed | counting by square merged a real split whose branches reach a square's two ends |

### TLW-B1 - a square's two ends are two roads

| | |
|---|---|
| **Disposition** | Fixed |

The builder splits a sensor square by arrival side, and a turning copy runs on the lane of the copy it turns.  Round 2 counted all a square's copies as one road - right for a lane and its turning copy, wrong for the square's two ends, which a balloon reaches from one junction by different track.  A train with no road then ran on past the junction down whichever rail sorted first, and the question was not put.

**Confirmed by running.**  `core.testTheTailCrossedQuestion.testTwoEndsOfOneSquareAreTwoRoads`.  Red: *"a train with no road ran on past a junction whose two rails reach opposite ends of one square ... Covered: [TQ_J -> TQ_S, TQ_A (eastbound) -> TQ_J]"*.

**Fixed, round 3.**  Roads and forks are counted by square AND lane - the name with its turning mark removed, where the builder writes the lane (`Layout.placeKey`, `TailCrossedPrompt.roadKeyOf`); walked squares are still counted by square.

---

## C - documentation, tests and minor

| id | status | where |
|---|---|---|
| TLW-C1 | Recorded | TLR-B3's fix reaches every no-road train at a split two-way platform, not only turning copies at a junction |
| TLW-C2 | Fixed | "two roads" in test messages; `askAfterPlacement`'s return and `ask`'s dismissal javadoc |
| TLW-C3 | Fixed | the A1 claims reached no post-run state; checked one store; the seam read an unoffered sensor as a dismissal |
| TLW-C4 | Fixed | the list did not start on the recorded road, so OK with nothing chosen erased it |

**TLW-C1 - recorded.**  Before round 2 the tail of a train with no road standing at a split two-way platform stopped at the first sensor behind it, because the platform's lane and turning copies read as two neighbours.  It now carries on to the next real split, as Adam's rule says.  This blocks more track behind such trains than before; the battery covers the built railway, and no claim pins this exact case on it.  It could show in MT-431 step 2 if another hand-placed train with a length stands at a split station on those roads.

**TLW-C2.**  Corrected.

**TLW-C3.**  The post-run claim is TLW-A1's; the nothing-asked claim checks the same road on the railway; the dismissal claim asserts the question is put and checks both stores; the test seam refuses a sensor that is not offered rather than reading it as a dismissal.

**TLW-C4.**  The list starts on the road the train has (`TailCrossedPrompt.preselectedIndex`).  **Confirmed by running:** `testTheRecordedRoadIsTheOneOfferedFirst`, red: *"expected [2] but found [-1]"*.

---

## D - looked wrong and is not

- **TLW-D1** - with no road the fork rule could step onto a turning copy that leads nowhere; name order prevents it (")" sorts before ","), which rests on naming only.
- **TLW-D2** - in the editor's narrowed pick list, Not Known and closing both keep the road; predates round 2 and errs towards keeping.
