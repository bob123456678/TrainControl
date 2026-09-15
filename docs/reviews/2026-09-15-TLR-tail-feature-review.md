# The tail feature, reviewed

**Status:** open 2026-09-15 - round 1 fixed (A1, A2, B1, B2, C1-C5) in `fd3f251e`; validated (`2026-09-15-TLV-fix-validation.md`), round 2 in `8b8ec4ad` completed B1, B2, C3 and C5 and fixed TLR-B3; validated again (`2026-09-15-TLW-fix-validation-round-two.md`) and round 3 finished what that found

**Prefix:** TLR (checked free, with TLV and TLW for the validation rounds: `SELECT DISTINCT ref FROM finding` in `docs/manual-tests/triage.db`, and every declaration spelling in `docs/`, `test/` and `src/`)

**Covers** `63ac68f4`, `53a82f7c` and `3219f0ea` - FR-085 and WK7-B1: a standing train's arrival road saved, reloaded, carried across a rebuild and captured; the farthest-sensor question at the paste, the right-click Place and the locomotive dialog; the facing-menu section; click-to-pick in the autonomy editor; the tail walk following a given road along rails laid either way.  One Fable reviewer, read-only, told Adam's design (*"select the farthest sensor the tail of the train recently crossed"*, and click-to-select in the editor), the accepted WKW-B1, and the hands-on tests he has not run.

**On the pending tests.**  Several findings touch MT-435 - the hands-on test for this feature, written with it and not yet run - and A1 touches MT-431.  They are fixed rather than held: each fix makes those tests hold as written, rather than changing what they expect.  TLR-B3 is held because it would change blocking for trains with no road, which MT-431 and earlier validated tests describe.

---

## A - wrong behaviour on the layout

| id | status | where |
|---|---|---|
| TLR-A1 | Fixed | `Layout.walkStandingTrains` - a turned train's tail stops at the junction again |
| TLR-A2 | Fixed | `AutonomySession.captureFromLayout` - a split square's other copies put a departed train's tail back into the setup |

### TLR-A1 - a turned train's tail stops at the junction again

| | |
|---|---|
| **Disposition** | Fixed |

A facing change stands the same train on the other lane's copy of its square and carries its side and road across (`AutonomySession.moveOntoFacingCopy`, TDR-B2), so the road names the lane it drove in on while the walk runs down the other.  The "either way round" match added in `53a82f7c` tested the walked squares by copy NAME, so the platform's own edge, read from its far end, matched too - and being later in the road it won, sent the walk back the way it had come, and the tail stopped at BottomMainAPre.  Introduced by `53a82f7c`; the regression MT-335 was about.

**Confirmed by running.**  `regression.testAPassingTrainMayStandAcrossThePoints.testTheRoadIsFollowedAfterTheTrainIsTurned` drives three units to BottomMainA, turns the train through the real `moveOntoFacingCopy`, and asks B and C of the other train at Tunnel.  Red: *"after the train was turned at BottomMainA, the other train at Tunnel is offered BottomMainB"*.

**Fixed, round 1.**  The walk takes the road's edge ENDING at the square first, and only when there is none the edge starting there; both test the walked squares by place (`walkedAPlaceLike`).  The facing menu's tick compares roads by place too, so a turned train's recorded road is still ticked.

### TLR-A2 - a departed train's tail comes back from a split square's other copies

| | |
|---|---|
| **Disposition** | Fixed |

The build writes a square's side and road onto every copy, and a train leaving clears them only on the copy it stood on.  `captureFromLayout` merges a square's copies, so since `63ac68f4` made the two keys captured, the other copies put the departed train's tail back into the setup - and on some squares overwrote a newly arrived train's.  In-session rebuilds hid it; a restart applied it.

**Confirmed by running.**  `testALeavingTrainTakesItsTailOutOfTheSetup` captures a train at BottomMainA, rebuilds from the setup, takes the train off and captures again.  Red: *"the train left BottomMainA and the setup still records the side it came in by ... expected [null] but found [W]"*.

**Fixed, round 1.**  The capture reads `arrivedFrom` and `arrivedAlong` only from a copy carrying a locomotive.

---

## B - incorrect results or refusals in specific configurations

| id | status | where |
|---|---|---|
| TLR-B1 | Fixed | `TailCrossedPrompt` - not asked where one road back has a crossed sensor and the other none |
| TLR-B2 | Fixed | `TailCrossedPrompt` - a square's turning copy counted as a second road |
| TLR-B3 | Fixed | `Layout.walkStandingTrains` fork rule - the same name-counting, for trains with no road |

### TLR-B1 - one crossed road is enough to ask

| | |
|---|---|
| **Disposition** | Fixed |

A junction counted as a question only when sensors were crossed on TWO roads.  With A -> J at three units and C -> J at four, a five-unit train crosses A and not C: "A" claims A -> J while "Not known" stops at J, and nothing was asked or offered.

**Confirmed by running.**  `core.testTheTailCrossedQuestion.testOneCrossedRoadIsEnoughToAsk`.  Red: *"the tail crossed TQ_A on one road back from the junction and no sensor on the other ... the question is not put"*.

**Fixed, round 1.**  A junction is a question when it has two roads back and a crossed sensor on at least one.

### TLR-B2 - two copies of one square are one road

| | |
|---|---|
| **Disposition** | Fixed |

A square trains may turn at is a lane copy and a turning copy - the same metal under two names - and counted by name it was a second road back: the question was put on plain track and the list offered the same sensor twice.

**Confirmed by running.**  `testTwoCopiesOfOneSquareAreOneRoad`.  Red: *"the list offered [TQ_J, TQ_A, TQ_A (reverse)] expected [2] but found [3]"*.

**Fixed, round 1.**  Roads, walked squares and first hops are keyed by place - the block a split square's copies share.

### TLR-B3 - the fork rule counts a square's copies as separate roads too

| | |
|---|---|
| **Disposition** | Fixed |

`walkStandingTrains`' fork rule counts distinct neighbours by name, so for a train with no road a turning copy next to the junction reads as a second road back and the tail stops early.  Older than these commits.  **Held:** counting by place would block more track behind trains with no road - a change to what MT-431 and validated tail tests describe, and a rule Adam set (*"end locking at the switch and call it a day"*).  For his call.

**Not held after all - fixed in round 2 (`8b8ec4ad`).**  The validation showed the reason was wrong: his rule stops the tail where track SPLITS, and two copies of one square are not a split; and with TLR-B2 fixed and this held, such trains could never be given a road (TLV-B2).  Counted by place; `core.testTheTailCrossedQuestion.testATrainWithNoRoadFollowsOneRoadUnderTwoNames`, seen red first.

---

## C - documentation, tests and minor

| id | status | where |
|---|---|---|
| TLR-C1 | Fixed | stale comments: `Point.getArrivedAlong`, `AutonomySession.captureFromLayout`, `Layout.walkStandingTrains` |
| TLR-C2 | Fixed | `testTheTailCanBeGivenInTheEditor` said Escape and pressed the right-click cancel |
| TLR-C3 | Fixed | `TailCrossedPrompt.label` - the "via" could name the junction itself |
| TLR-C4 | Fixed | `GraphLocAssign.commitAndRecord` - the tail dialog had no parent |
| TLR-C5 | Fixed | the three doors kept an old road when the answer was Not Known |

**TLR-C1.**  Three comments still said the road is kept in memory only, or is not captured.  Corrected.

**TLR-C2.**  `testAClickElsewhereKeepsThePickArmed` claimed Escape lets go but invoked `cancelPendingGesture`; Escape is `putToolsDown` -> `clearGesture`.  The claim now presses `putToolsDown`; with the tail lines taken out of `clearGesture` it goes red.  The class still skips if the snapshot yields no qualifying train - recorded, and its preconditions name the case.

**TLR-C3.**  The label that tells two roads to one sensor apart took `road.get(1).getStart()`, which is the junction itself when a rail is laid away from the train.  It now takes the far end of the road's first rail.

**TLR-C4.**  The locomotive dialog's door passed no parent, so the question could open behind the window.  It passes the dialog's panel.

**TLR-C5.**  The doors wrote a road only when one was chosen, and pasting the same train back on its square is not a change of occupant, so the old road stayed and the next rebuild followed it.  **Confirmed by running:** `testNotKnownOnAPasteForgetsAnOldRoad`, red: *"the setup still holds the road from the paste before"*.  **Fixed:** all three doors write the road either way.

---

## D - looked wrong and is not

- **TLR-D1** - `Layout.getEdge(String, String)` returns null for unknown names, so `roadNamed`'s catch is dead but harmless; a road is dropped whole on any unresolved pair, as documented.
- **TLR-D2** - the paste door asks the tail question after the move while the side question is asked before: correct, a dismissed tail question is Not Known and does not refuse the placement.
- **TLR-D3** - `wouldAsk` true implies a non-empty list.

---

## Checked and found sound by the reviewer

Trains with no road are unchanged by the walk edit.  All three placement doors write both stores after the move, and no other door writes a placement's side.  `moveOntoFacingCopy` and `standOnTheCopyItDidNotTurnOn` carry the road.  `setupEditDeclinedDuringRun` skips the capture whole.  The side radio clears the road only on a changed side, in both stores.  The editor gesture: right-click cancels, `anythingIsArmed` includes it, reload clears it, a miss stays armed, an ambiguous square goes to the list, a rebuild while armed survives.  The dialog is event-thread safe and unreachable during a run.  `putTheRoadBack` tolerates the older two-element arrays.  The eight bundles, the tracker entries, and the one-graph-per-claim rule in `testTheTailCrossedQuestion`.
