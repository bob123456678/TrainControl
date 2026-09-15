# The 2026-09-15 feedback fixes, reviewed

**Status:** open 2026-09-15 - round 1 fixed B1, B2, B3, C2, C6 and C7 in `ddaa1b9b`, restored C4's seven claims and strengthened the C5 claim in `3369b650`; C2's fix was replaced in round 2 (MFV-B1, `ab217322`); C1 and C3 held; C8 noted on OB-229 in `32b415a2`

**Prefix:** MFR (checked free, with MFV, MFW and MFX for the validation rounds: `SELECT DISTINCT ref FROM finding` in `docs/manual-tests/triage.db`, every declaration spelling in `docs/reviews/`, and a grep of `src/`, `test/` and `docs/`)

**Covers** `9cc6a874`, `c0fe696e`, `7a484ea9`, `6c55bae6` and `c8a55f01`, with the tracker commits `ee6c91df` and `fc6042d0` - Adam's MT feedback of 2026-09-15: FR-087 (a station autonomy may choose is bounded by the measured route in), OB-226, OB-227 and FR-088 (the tail question's reach, roads and default), FR-086 (Control+N), OB-225 (Why Not Moving? follows Path Type) and OB-228 (Return Home models the tails of the trains it moves).  One reviewer (Fable), read-only; every fix below was run.

**On the pending tests.**  B1 touches MT-439, B2 MT-440, B3 MT-436 and C6 MT-437 - all written with these commits and not yet run.  They are fixed rather than held: each fix makes the test hold as written.

---

## A - wrong behaviour on the layout

None.  The reviewer checked that every door that admits a train under FR-087 records the road it arrived by, so the tail walk covers the track the train was counted against.

---

## B - incorrect results or refusals in specific configurations

| id | status | where |
|---|---|---|
| MFR-B1 | Fixed | `Layout.firstClearOrWhyNot` - Why on Manual refused a station beyond a headshunt the menu offers |
| MFR-B2 | Fixed | `HomeStaging.astar` - two orders reaching the same squares with different tails were one arrangement |
| MFR-B3 | Fixed | `AutonomyEditorPanel.showStationNameFor` - Control+N offered a name on an ignored square |

### MFR-B1 - Why Not Moving? on Manual refused a station beyond a headshunt

| | |
|---|---|
| **Disposition** | Fixed |

By hand the standing bars were skipped, but `firstClearOrWhyNot` still threw away every route that passes a reversing square.  `reversesAlongTheWay` is autonomy's rule - *"NOT asked by isPathClear, so a hand-driven move ... can still use a headshunt"* - and `getPossiblePaths`, the right-click menu, asks only `isPathClear`.  So on Manual a station reached only through a headshunt read as "cannot be sent" while the menu offered it, against behaviour.md section 7 and MT-439 step 2.

**Confirmed by running.**  `core.testWhyStuck.testByHandAStationBeyondAHeadshuntIsSomewhereToGo`: A -> R -> B, R reversing; the menu offers B, autonomy refuses it.  Red: *"the right-click menu offers WSH_B and Why Not Moving? on Manual says it cannot be sent there: 'Every route there runs through a reversing point.'"*

**Fixed, round 1.**  `firstClearOrWhyNot` takes the tier and asks `reversesAlongTheWay` only for autonomy.

### MFR-B2 - Return Home merged arrangements that differ only in their tails

| | |
|---|---|
| **Disposition** | Fixed |

A* keyed an arrangement by where the trains stand, and set `movedAlong` from the one chain of moves `cameFrom` kept.  Once tails are modelled (OB-228), two orders can reach the same squares with different tails, and the rediscovery with the useful tails was dropped as no cheaper - so the search could answer NO_PLAN_FOUND where a plan exists.

**Confirmed by running.**  `core.testReturnHomeKeepsClearOfTheTailsItLeaves.testAnOrderThatOnlyWorksOneWayRoundIsFound`: X has a short road home past SY, whose tail lies across Z's road, and a long one it takes while Y still stands at SY; the only order that brings all three home is X, Y, Z.  Red: *"the search answered NO_PLAN_FOUND ... Moves: []"*.  The red alone does not say why; the fix below turning it green is what ties it to the merge.

**Fixed, round 1.**  Each arrangement keeps the routes its moved trains came by (`routesOf`), and its key is the squares plus the edges each moved train's tail covers (`tailKey`, walked by `Layout.edgesATailWouldCover`), so two orders are one arrangement only where their tails lie on the same track.

### MFR-B3 - Control+N offered the station chooser on an ignored square

| | |
|---|---|
| **Disposition** | Fixed |

The key's javadoc said it asked what the menu asks.  It did not ask `isIgnored`, so on a page left out of autonomy it opened the chooser and wrote a caption where the menu offers only the bulk tools; nor did it abandon a gesture in progress as the menu does.

**Confirmed by running.**  `regression.testControlNAsksTheMenusQuestion` puts the menu and the key's predicate side by side on plain track and on the same track with its page excluded.  Red, and weaker than the others: the panel had no predicate for the key to share, so the claim failed on its absence (*"AutonomyEditorPanel has no offersAStationName"*) rather than on a wrong answer - a key that opens a modal dialog cannot be asked directly.

**Fixed, round 1.**  `offersAStationName` is the menu's own question, public for that test; `showStationNameFor` cancels a pending gesture, acts only where the predicate says so, and says `infoTileIgnored` on an ignored square.

---

## C - documentation, tests, minor

| id | status | where |
|---|---|---|
| MFR-C1 | Held | `TailCrossedPrompt.preselectedIndex` - the default at a fork whose other road is unmeasured |
| MFR-C2 | Fixed | `HomeStaging.search` - a failed search could take twice the budget (round 2 shares the budget out: MFV-B1) |
| MFR-C3 | Held | `HomeStaging` - a train moved away and back is judged by two tails |
| MFR-C4 | Fixed | `core.testTheTailCrossedQuestion` - seven claims deleted, not moved |
| MFR-C5 | Not a defect | `testWhyNotMovingFollowsPathType` asserted only absences on Manual - claim strengthened |
| MFR-C6 | Fixed | `Layout.whyTooLongForThisRoute` - the refusal quoted the room past the switch, not the route in |
| MFR-C7 | Fixed | stale comments: "the approach's own length", "while some of the train is still left beyond it" |
| MFR-C8 | Noted | OB-229 - the audit's third disagreement is a copy |

### MFR-C1 - the default at a fork whose other road is unmeasured

| | |
|---|---|
| **Disposition** | Held - Adam's ruling |

Where the second road back from a junction contributes no sensor because it is unmeasured, the one sensor on the measured road is the only one nearest the back, and the list starts on it.  Held: Adam asked for *"the closest sensor to the back"* to be the default *"so the user can just click OK if appropriate"*, and this is that sensor; the list still offers Not Known, and a default is a suggestion he confirms.  If it misleads on his railway, MT-438 step 4 is where it will show.

### MFR-C2 - a failed Return Home search could take twice as long

| | |
|---|---|
| **Disposition** | Fixed |

The retry from the start (OB-228) ran with a fresh `SEARCH_BUDGET_MS`.  **Fixed, round 1:** one deadline, set once per `search`, read by every A* it runs.  **Replaced in round 2** (MFV-B1, `ab217322`): one deadline could leave the retry from the start no time at all, so the budget is shared out - half to A* from the greedy arrangement, the rest to the retry.  Not claimed by a test in round 1; round 3 claimed the budget on a stepped clock (MFW-C3, `testTheRetryFromTheStartHasTimeLeft`).

### MFR-C3 - a train moved and later back on its start square is judged by two tails

| | |
|---|---|
| **Disposition** | Held |

Over-refusal only - both its old tail and its route's are applied - and it needs a plan that moves a train away and back, which no fixture here could be made to produce first.  Held until one does.

### MFR-C4 - seven tail-question claims were deleted, not moved

| | |
|---|---|
| **Disposition** | Fixed |

The claim script for OB-226, OB-227 and FR-088 rewrote `core.testTheTailCrossedQuestion` and dropped `testOneCrossedRoadIsEnoughToAsk`, `testTwoCopiesOfOneSquareAreOneRoad`, `testTheAnswerPicksTheRailRightBehindThePlatform`, `testATrainWithNoRoadFollowsOneRoadUnderTwoNames`, `testRoadsThatMeetAgainAreToldApart`, `testTwoEndsOfOneSquareAreTwoRoads` and `testTheRecordedRoadIsTheOneOfferedFirst` - the evidence for TLR-B1/B2, TLV-B1/B2/B3 and TLW-B1/C4, whose rules are all still in the code.  **Fixed, round 1:** restored from `9e626e48` on their own sensors; `testOneCrossedRoadIsEnoughToAsk` measures C -> J five, since OB-226 offers a sensor exactly the train's length back.  All thirteen claims pass with the seven restored (they were never red - the rules held; only their tests were gone).

### MFR-C5 - the Why claim asserted only absences on Manual

| | |
|---|---|
| **Disposition** | Not a defect - claim strengthened |

**Fixed, round 1:** on Manual the answer must name more stations it can go to than on Auto - an empty or failed answer names none.  Passes on the real editor before the round's fixes, as it should - C5 is a stronger claim, not a defect.

### MFR-C6 - the FR-087 refusal quoted the room past the switch

| | |
|---|---|
| **Disposition** | Fixed |

At BottomMainA with four units the refusal read "measures 1" - the room past the switch - where the route in, which had just held three, measures three.  **Confirmed by running:** `testTheRouteInHoldsAThreeUnitTrainAndNotAFourUnitOne` now asks the number.  Red: *"overhang probe standing (length 4) is longer than the track leading into BottomMainA, which measures 1"*.  **Fixed, round 1:** `Layout.measuredRouteIn` is the one count; the allowance compares against it and the refusal at a station autonomy may choose quotes the larger of the two bounds.

### MFR-C7 - stale comments

| | |
|---|---|
| **Disposition** | Fixed |

`theApproachItselfHoldsIt`'s javadoc, the block in `whyTooLongForThisRoute`, the planner's comment in `HomeStaging`, and `TailCrossedPrompt`'s class javadoc.

### MFR-C8 - OB-229's third disagreement

| | |
|---|---|
| **Disposition** | Noted on OB-229 |

The audit counted three disagreements and printed two: `logStagingAudit` prints `placeNameOf`, which names a square and not its copy, so the third is almost certainly RampDown's other copy.  Recorded on OB-229 in `32b415a2`; the issue stays open.

---

## D - looked wrong and is not

| id | what |
|---|---|
| MFR-D1 | A square with no incoming rail on the recorded side is never asked the tail question - a train there was pushed in backwards, the road OB-227 excludes |
| MFR-D2 | Control+N is also the main window's notes key - separate windows, a frame-scoped post-processor, no collision |
| MFR-D3 | `putIfAbsent(roadKeyOf(...))` merges a lane copy and its turning copy - one piece of metal, read by place |
| MFR-D4 | `beyond < 0` at the first hop counts the standing square's own measurement - pre-existing, and the list is a suggestion |
| MFR-D5 | The three fixture moves from stations to parking berths follow FR-087 and hide no defect; station-side refusal is still held by `testTheRouteInHoldsAThreeUnitTrainAndNotAFourUnitOne` and `admittedWithUnmeasuredApproach` |

---

## What the passes missed

- **C4 was made by the fix, not found by the tests.**  A claim script that rewrote a test class replaced seven passing claims with three new ones and every run stayed green, because a deleted test cannot fail.  Only reading the diff against the class's history found it.
- **The battery found what the review of FR-087 did not look for.**  Three test fixtures - two berth classes and the real-layout boundary search - encoded the pre-FR-087 station rule with the default *Can Be Chosen In Full Autonomy* on.  Each was found by running, one of them only by the full battery.
- **B2 needed tails and orderings together.**  Each rule was tested on its own (OB-228's claim had one ordering that works); the merge only shows with three trains where the order changes which road a train takes.
