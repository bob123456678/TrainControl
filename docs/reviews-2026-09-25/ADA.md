# ADA - Automation lane, validation round 1: Adam's answers of 2026-09-24 as carried out in `45cfa410..d05a6356`

**Status:** closed

**Prefix:** ADA

**Reviewed:** branch `autonomy-diagram-r0` at `d05a6356` (HEAD, 2026-09-25 00:57). The range is `45cfa410..d05a6356`: `7dc22256` (claims), `f17f5a5c` (fixes), `ac4c7567` (TDA-C10 re-read off the built railway), `e269aaec` (records), `d05a6356` (status lines). The lane is `src/org/traincontrol/automation` and `src/org/traincontrol/automationui`, and the claims that hold them.

**Method.** I read `docs/reviews/FANOUT.md` and `docs/reviews/README.md` first. Then I read every finding answered, and its disposition, from `git show d05a6356:docs/reviews-2026-09-24/<LANE>.md` (TDA-C8, C9, C10; TDA2-C1; TDA4-C2; TDU-B4, C6; TDU2-C3; TDD-C11; TDD5-C1), and OB-295 to OB-298 in `issues.md`. I read each commit of the range as a diff (`git show <c> -- <path>`), then read the code at HEAD around every changed method and its callers: `Edge.isMeasured`/`isPlaceACut`, `Layout.isPathClear`'s FR-001 block, `heldBackAlong`, `firstClearOrWhyNot`, `pathIsUnmeasured`/`tailHasProvablyPassed`/the release loop, `unmeasuredTrackThatCouldBeReleased`, `measuredRouteIn`, `theApproachItselfHoldsIt`, `whyTooLongForThisRoute`, `measuredRoomAtTheEndOf`, `walkOneTail`, `claimUpToWhereTheRailsPart`, `whyABerthCannotHoldIt`, `whyItWouldMeetItsOwnTail`, `piecesOf`, `unmeasuredPiecesOfTheWayRound`, the configuration reader, `HomeStaging.firstClearRoute`/`plannedOccupancy`/`auditAgainstRuntime`, `Point.heldBackBy`; `AutonomyBuilder` (node emission, barred copies, FR-001 emission, place marks), `GraphReducer.reduce`/`placesAlong`/`answeredAtZero`, `AutonomySession.rebuild`, `reversalsWithoutLength`, `stationsWithAHalfMeasuredApproach`, `runInFigures`, `refusingFigures`, `stretchesALengthRuleReads`, `needsALength`, `assignStretchLength`, `legsOnce`, `pieceCuts`; `AutonomyChecks.checkLengths`. I read the lane's claims in `7dc22256` and `ac4c7567` and asked of each whether its fixture reaches the answer's own case and whether its named mutation would turn it red. I grepped for sibling sites (every `getLength() <= 0` in the model, every `heldBackBy`, every "read as unmeasured" statement, every barred-side skip). I read the frozen railway's `test/layouts/live-snapshot/config/autonomy/setup.json` (no restrictions, no answered zeros, 158 lengths of which 140 are 1) and the tile types on its pages (21 route tiles on page 1). I scanned the three code commits for a member inserted under another member's javadoc. The GUI halves of OB-296, TDD5-C1, TDU2-C3 and TDD-C11 are another lane's; I read them only where they touch the running model (ADA-D8). **Nothing was executed**, and every finding that depends on behaviour carries a verification request. I did not open `cs2_sample_layout/`.

Grades: A: wrong behaviour on the layout, or data lost. B: incorrect results in specific configurations. C: cosmetic, a narrow case, or text. D: checked and clean. Each finding says what mitigates it.

---

### ADA-A1 - The route in (FR-087) now carries on over a leg answered 0, but the tail walk still stops at one: a platform admits a train whose tail lies on track nothing claims

| | |
|---|---|
| **Disposition** | Fixed - the narrow reading: the route in stops at a leg answered 0 again, and TDU-C6's 0 reaches the Atomic Routes gate and its escape only, as OB-274's ruling has every other length rule read it.  Claim 2c7a5b47 (`testTrackAnsweredZeroIsMeasuredTrack`, the [measured, answered 0, measured] shape), fix 1098c9b8; mutation V1 red.  The wider reading - the tail walks following - is put to Adam in open-questions.md under Length. |
| **Where** | `f17f5a5c`: `Layout.measuredRouteIn` (`Layout.java:10141-10156`, `if (!path.get(i).isMeasured()) break;`) against `Layout.walkOneTail` (`:7742`, `if (segment.getLength() <= 0) break;`) and `claimUpToWhereTheRailsPart` (`:9255`); the reader is `theApproachItselfHoldsIt` (`:10111`), asked by `whyTooLongForThisRoute` (`:10819`) and `HomeStaging` (`:1412`) |

**The answer's reach.** Adam's words for TDU-C6 were *"0 lengths count as measures, so non-atomic should be allowed."* The work applied `Edge.isMeasured` in three places:

- the Atomic Routes gate;
- the release escape. This one is required: the gate and the escape must ask one question, or the gate lets through a railway the escape then releases under a train;
- the route in. This one is a wider reading, and nothing required it.

The rules that make the route in safe were not widened with it.

**What FR-087 rests on.** The allowance lets a train stand across the switch at a station autonomy may choose. It admits a train of up to `measuredRouteIn` there. Its own comment gives the safety argument: *"Since OB-207 a standing tail claims the places it lies on and `isPathClear` refuses any route over them, so nothing can be sent into the overhang."* Before `f17f5a5c`, the route in and the tail walk both stopped at a leg of length 0, so the train FR-087 admitted lay only on track the tail walk claims. Now:

- the route in steps over an answered-0 leg, adds 0, and keeps counting the measured legs behind it;
- the tail walk still breaks at that leg, before claiming it, and claims nothing behind it.

**Input that goes wrong.** A route `[E1, E2, E3]` to a platform P:

- E1 is measured at 3.
- E2 is a hop whose every place was answered 0. This is Adam's *"adjacent tracks"* exactly: a leg between two adjacent sensors, whose Mass Assign piece is just the far sensor's square, answered 0.
- E3 is measured at 2 and ends at P.

Now send a 4-unit train to P:

- The room rule (`measuredRoomAtTheEndOf`) counts E3, reaches E2's 0, and stops: the room is 2 (`:10996`). The train is refused, and FR-087 is asked.
- `measuredRouteIn` = 2 + 0 + 3 = 5, and 4 ≤ 5, so the train is admitted. At `f17f5a5c^` it was 2, and the train was refused.
- The train stands at P with 2 units in E3, and its last 2 units back in E1.
- `walkOneTail` spends E3, moves to E2's end, finds E2's length is 0, and breaks. E1's places are claimed by nobody, and so is any switch on E1 the tail lies across.

`isPathClear` protects a standing train's overhang only through those claims (`coveredTrack`, `coveredPlaces`, `anotherTailOn`). The arriving route's own locks are given up at `unlockPath` on arrival. So:

- a second train can be cleared onto E1, or through a switch on it;
- Return Home can plan that move, because its tail check is the same walk (`edgesATailWouldCover`);
- the diagram's grey, built from the same claims, shows nothing there.

**What mitigates it.**

- The frozen railway has no answered 0 anywhere: `tileLengths` holds no 0, and `setup.json` has no answered list.
- It needs an entirely answered-0 leg with measured legs on both sides, on the way into a station autonomy may choose, and a train longer than the measured track between the 0-leg and the platform.
- Parking berths are not affected: `theApproachItselfHoldsIt` answers false at a square autonomy may not choose, so FR-087 never admits a train there.
- The configuration is the one Adam named as his reason for answering 0, so it is a plausible one for his railway.

**The other readings.**

- **Narrow** (gate and escape only): the route in stays where the tail walk stops, and nothing here changes.
- **Wide** (every length rule): the tail walk and `claimUpToWhereTheRailsPart` would also continue over an `isMeasured()` leg of length 0, claiming its places and spending 0, which closes the gap. It would also make the room rule judge a last leg answered 0, which it now answers `null` ("cannot judge").
- **The work took the middle**, and the middle is the unsafe combination: the allowance was widened, and the claim that makes it safe was not.
- **Fix:** revert `measuredRouteIn` to stop at a 0-length leg, or carry the tail walk over an answered one. The second claims more, never less. Which reading Adam wants is his call.

**Verification request.** A `core.testAutoLayout` fixture, built from `testTrackAnsweredZeroIsMeasuredTrack`'s graph:

- Add a station `AZ_P` and an edge `AZ_B -> AZ_P` with places `["AZ:4","AZ:5"]`, spans `[1,1]`, length 2.
- Answer `ab`'s places, as that test does.
- Use a train of length 4 and the path `[ca, ab, bp]`.

Then check three things:

1. `Layout.whyTooLongForThisRoute(path, loc)` is null. With `ab`'s answers removed it is a refusal naming 2.
2. Stand the train at `AZ_P`, arrived along the path. `layout.placesCoveredByStandingTrains()` does not contain `"AZ:1"`.
3. A second train at `AZ_C` gets `isPathClear([ca], other, false) == true`.

- **Proves it:** 1 and 2 both hold.
- **Refutes it:** 1 refuses, or `"AZ:1"` is claimed.

---

### ADA-C1 - OB-297's pieces are not the pieces Mass Assign asks for: the note can still undercount, and on a measured railway it can count pieces Mass Assign never offers

| | |
|---|---|
| **Disposition** | Fixed - the build marks, place by place, what Mass Assign Lengths asks a length for (`toMeasure`, `lengthsAsked`), and the own-tail note counts those marks, once each: one list with the editor's.  The cut marks are gone.  Claims 2c7a5b47 (`testTheBuildMarksWhatMassAssignAsksFor` twice - the route tile and the station's share - and `testTheNoteCountsWhatMassAssignAsksFor`), fix 1098c9b8; mutations V5, V6 red. |
| **Where** | `f17f5a5c`: `Layout.piecesOf` / `isMeasuredAt` (`Layout.java:10585-10632`), `GraphReducer.answeredAtZero` (`GraphReducer.java:1622-1625`); against `AutonomySession.stretchesALengthRuleReads` (`:3389`), `needsALength` (`:3961`), `assignStretchLength` (`:3600`), `legsOnce` (`:3749`) |

The commit, the javadoc and `behaviour.md` all say the own-tail note now counts *"what Mass Assign Lengths asks for"*. The two differ in three ways.

1. **When a piece counts as measured.** `piecesOf` counts a piece as measured when ANY place on it has a length or was answered. Mass Assign's `needsALength` asks for a piece until it has a length or ALL its squares were answered. The same commit made every route tile's place "answered" (`answeredAtZero` now includes `takesNoLength`), and Mass Assign leaves route tiles out of pieces. So a piece with a route tile in it is never counted by the note, however unmeasured the rest of it is. This is TDA2-C1's undercount again, for any piece carrying one of the 21 route tiles on Adam's page 1. The same holds for a square added to an answered piece by a later diagram edit, a case `needsALength`'s own javadoc names.
2. **Where a piece starts.**
   - An edge's places leave out its start sensor, while Mass Assign's legs include both end sensors. A sensor square belongs to the first piece that reaches it (MAL-C2).
   - `assignStretchLength` gives a piece's units to station and turn-round squares first, then in leg order.
   - So a short piece measured below its square count - `[station, a]` given 1 - stores station 1, a 0.
   - The edge leaving that station has a piece `[a]` with nothing on it, and the note counts it, while `stretchesNeedingALength()` is empty.
   - The note then tells the operator to measure track the editor says is measured: an error with no remedy.
   - Squares before a switch that belong to another leg's piece shift the boundary the same way.
3. **The claims cannot see either.** `testTheBuildMarksWhereAStretchIsCut` compares the marks with `switchesALengthRuleReads() ∪ sharedSquaresALengthRuleReads()`, the same union `pieceCuts()` feeds the builder. `testTheNoteCountsPiecesCutAtTheSwitches` sets the marks by hand. Nothing compares the note's count with `stretchesNeedingALength()`.

**On the railway.**

- No train moves differently: the refusal and its figure are unchanged. Only the note is affected.
- Adam's measured railway has many zero squares inside measured pieces: 21 on one way round, per the comment in `whyItWouldMeetItsOwnTail`.
- So MT-571's step-2 refusal may now carry a note (*"N pieces ... have no length"*) that it did not carry at edge grain.
- `core.testATrainDoesNotRunIntoItsOwnTail` would not notice, because `gapNamedBy` matches with `lookingAt` and tolerates a trailing note.

**What mitigates it.** The note only follows an own-tail refusal, and the refusal is right either way.

**Verification request.**

- **(a) The route tile.** Use `testMassAssignLengths.openARouteTileInARun()` with the leg left unmeasured and something measured elsewhere. Rebuild, then call `Layout.piecesOf` on the running edge `1,1 -> 5,1`.
  - Proves it: the piece is flagged measured while `session.stretchesNeedingALength()` lists it.
- **(b) The station's share.** Build a station square, one plain square and a switch. Call `assignStretchLength(piece, 1)`, then rebuild.
  - Proves it: the edge leaving the station has a piece flagged unmeasured while `stretchesNeedingALength()` is empty.
- **(c) The frozen railway.** Take `testATrainDoesNotRunIntoItsOwnTail.standItAsArrived(20)` and check each `routesToLowerFront()` reason for `errorOwnTailPartlyUnmeasured`'s text.
  - Proves it: the note is present.
  - Refutes it: absent.

---

### ADA-C2 - OB-298 fixed the shape its claim builds, not the incident's: `AutonomySession.rebuild` publishes the reducer before its first `reduce()`, and a reader in that window now walks an empty list without a sound

| | |
|---|---|
| **Disposition** | Fixed - the session hands out a reducer only once reduced, and the field is volatile: whichever thread rebuilds, a reader holds the old railway or the whole new one, which answers the filing's question by construction.  Claim 2c7a5b47, fix 1098c9b8; mutation V10 red. |
| **Where** | `f17f5a5c`: `GraphReducer.reduce` (`GraphReducer.java:386-408`); `AutonomySession.rebuild` (`AutonomySession.java:442-443`: `reducer = new GraphReducer(...); reducer.reduce();`); claim `testAutonomyDiagramReducer.testARebuildDoesNotChangeWhatAReaderIsWalking` |

**Production never calls `reduce()` twice on one reducer.** The only production call is `AutonomySession.java:443`, on a reducer assigned to the (non-volatile) field one line earlier. The claim calls `reduce()` a second time on a reducer whose lists a reader already holds. That is a shape the application does not produce.

**What the incident must have been.** Filed as OB-298: a build's `splitSides` iterated `getEdges()` while "the other rebuild cleared and refilled that list in place". Since production never re-reduces a reducer, the reader must have fetched the NEW reducer between those two lines and walked a list `reduce()` was filling.

**What the fix does to that case.** `getEdges()` wraps the published object's initial empty list, which is never filled: the swap replaces it. So:

- the reader completes on an empty railway, with no exception;
- a build from that reads no edges, or mixes the old-empty edges with points swapped in later;
- the cross-list swap is not atomic either. `locks` goes in before `edges`, and `ReducedEdge` has identity equality, so a reader mixing the two finds no locks.

**The filing's own question is unanswered.** It asked whether any door builds off the event thread: the start-up resume, `rebuildRunningLayoutFromSetup`. The receipt calls the item "done now".

**Mitigation.** The filing says every door read so far builds and edits on the event thread. It was seen only in a test that edited off it.

**Fix.** Publish after reducing: `GraphReducer fresh = new GraphReducer(...); fresh.reduce(); reducer = fresh;`. And answer the filing's question.

**Verification request.**

- Fixture: a test that runs `session.rebuild()` on one thread while another loops `session.getReducer().getEdges().size()` on a railway with edges.
- Proves it: the reader ever sees 0.
- The line order at `:442-443` shows it by reading.

---

### ADA-C3 - OB-295 makes a restricted through-square a roadblock for as long as the watched train stands, and two restrictions can now deadlock; the comment that said why the standing half was destination-only was removed, not answered

| | |
|---|---|
| **Disposition** | Fixed in the record - behaviour.md section 1, the isPathClear comment and open-questions.md name the cost and the deadlock of two restrictions set against each other (the round's records commit).  It is Adam's ruling's consequence; the frozen railway has no restriction. |
| **Where** | `f17f5a5c`: `Layout.isPathClear` FR-001 block (`Layout.java:2874-2944`), `HomeStaging.firstClearRoute` (`:1299`); the removed text said the standing half was *"asked only of a path DESTINATION ... so neither hazard applies"*, the hazards being `Edge.isLockHeld`'s *"a locomotive beside a junction a permanent roadblock, and two could deadlock"* |

**The reading holds.** Adam's words name both halves for THIS square, including one trains only pass (ADA-D1).

**What it costs, which nothing says.**

- While a train is parked on the watched square W, every route crossing the held square S waits. On a through line that is every route through it, for as long as the train stands - possibly all evening.
- Take two restrictions, S1 held by W1 and S2 held by W2, where each parked train's only way out passes the other's held square. The exemption lets each train leave its own watched square, but not pass the other's. Neither train can move.
- Before `f17f5a5c` both went, because the lock half asks only about route-held approaches.
- `behaviour.md` names the roadblock as the setting's cost ("shut to routes through it too"). Neither it nor the new code comment mentions the deadlock. The planner's NO_PLAN_FOUND would be the only sign.

**What mitigates it.**

- The frozen railway has no restriction.
- MT-443's comment says Adam's setup had none as of 2026-09-24.
- The deadlock needs two restrictions arranged against each other.

**Verification request.** A `testStationBlockedByAnotherPoint` fixture with W1 and W2, each with one exit, where W1's exit runs through S2 and W2's through S1. Put S1 blockedBy W1 and S2 blockedBy W2, and a train on each W.

- Proves it: `isPathClear` refuses both exits at HEAD, and at `f17f5a5c^` allows both.

---

### ADA-C4 - TDA-C10's figure is given only for a single leg from a station or turning copy; its javadoc and MT-583 say more than that, and nothing ties the figure to the runtime's refusal

| | |
|---|---|
| **Disposition** | Fixed - the figure walks back over the built railway to a copy a train is started or turns at, stopping at a leg with no length, for the notice's own ways in only (never under {3}); the javadoc says so.  MT-583's reason was measured on the frozen railway with the railway's own route search (TopMainR1Inter 3, LowerFront 4 matching `whyTooLongForThisRoute`; Tunnel 6, BottomMainA 6, BottomInnerOtherside 4, none under the maximum).  Claims 2c7a5b47 (`testTheRefusingFigureCountsBackOverASensor`) and b6835920 (`testTheRefusingFigureIsTheRailwaysOwn`, tying the notice to the refusal), fix 1098c9b8; mutations V7, V8, V9, V15 red. |
| **Where** | `ac4c7567`: `AutonomySession.refusingFigures` (`:9994`) and its javadoc (`:9978-9993`); `runInFigures` (`:9871`); MT-583; `open-questions.md:145`; the `f17f5a5c` message |

Where a figure is given, it is the runtime's bound for a train setting off from that copy (ADA-D7). Four things around it are not right.

1. **"A way in from further back measures at least as much" is false where a leg of it is unmeasured.** `measuredRouteIn` stops at that leg.
   - Take S2 → (unmeasured) → X → (2, over a switch) → P, and S1 → P measuring 4. The notice says 4, and a 3-unit train from S2 is refused.
   - The javadoc's next clause ("a figure it might lower is left unsaid") contradicts its first.
   - `open-questions.md` and the fix commit say "from the station or turn nearest behind", which suggests walking back. The code takes one leg.
2. **MT-583 says of Tunnel, BottomMainA and BottomInnerOtherside "every way into them the railway runs measures at least their maximum".** The code only knows it gives no single-leg figure under the maximum. `ac4c7567`'s message says Tunnel was measured. It does not say the other two were checked over longer ways in.
3. **{4} is not bounded by {3}.** `refusingFigures` takes switchless legs from an adjacent station, which `runInFigures`' `worst` skips (`room == Integer.MIN_VALUE`). The sentence can then read "a train longer than 3 stands across that switch ... over 2 of measured track, a train longer than 2 is refused instead".
4. **The rule is stated twice.** `refusingFigures` re-derives `measuredRouteIn` in JSON terms, and the two already differ on an answered-0 leg: the route in continues over it, while a single answered-0 leg gives 0 and is suppressed. `testThePlatformNoticeNamesTheFigureATrainIsRefusedAbove` asserts the notice's number only. No claim asks `whyTooLongForThisRoute` to refuse at {4}+1 and admit {4} from that copy.

**What mitigates it.** It is a notice. Every figure it gives is true. Items 1 and 3 need partly measured or adjacent-station geometry.

**Verification request.**

- **The figure.** On the frozen railway, for BottomMainA and BottomInnerOtherside, find over every route from every station copy the smallest length `whyTooLongForThisRoute` refuses with `errorTrainTooLongForBerth`.
  - Proves MT-583's sentence: all at or above the stated maximum.
  - Refutes it: any below.
- **The {4} ≤ {3} case.** Take `platformBehindASwitch` with a station one plain square east of the platform and no switch between. Set that square to 1 and give the platform max 6.
  - Proves it: the finding's `getThird()` is below `getDetail()`.

---

### ADA-C5 - The TDU-C6 claim was inserted under another test's javadoc, and its route-in assertion pins a path the rule never asks the route in about

| | |
|---|---|
| **Disposition** | Fixed - the claim has its own javadoc above the other's (2c7a5b47), and its route-in assertion pins the shape the rule acts on: a leg answered 0 between measured legs. |
| **Where** | `7dc22256`: `test/core/testAutoLayout.java:689-735` (`testTrackAnsweredZeroIsMeasuredTrack`), `:737-738` (`testARailwayCountsItsUnmeasuredDrivableTrack`) |

1. **The javadoc is orphaned.** The new method sits between `testARailwayCountsItsUnmeasuredDrivableTrack`'s javadoc and its `@Test`. So that long javadoc - eight claims, VD16-T1, and a MUTATION record of which mutation turned which claim red - now documents the TDU-C6 claim. The method it describes has none, and the new claim has no javadoc or MUTATION line of its own. It is the only such insertion in the three code commits (scanned).
2. **The route-in assertion pins a path that never reaches the route in.** It checks `measuredRouteIn([ca, ab]) == 3`, where the answered-0 leg is the LAST leg. For that path, `measuredRoomAtTheEndOf` answers `null` on the first leg it reads, so `whyTooLongForThisRoute` never consults the route in. The assertion pins a number no rule reads for that path. The shape where the change acts - a 0-leg between measured legs - is unclaimed, and that is ADA-A1's shape.

**Mitigation:** test documentation and coverage only.

---

### ADA-C6 - Comments and documents the range made false, and one rule now stated both ways in behaviour.md

| | |
|---|---|
| **Disposition** | Fixed - the comments and parameter text in 1098c9b8 and the documents in the round's records commit. |
| **Where** | as listed |

**An answered 0 "read as unmeasured by every rule"** - false since `f17f5a5c` for the gate, the escape, the route in and the own-tail note:

- `GraphReducer.java:290` (`Authored.isTileLengthAnswered`);
- `AutonomySession.java:3605` and `:3658` (assign stretch and switch lengths);
- `AutonomySession.java:8617`;
- `AutonomyEditorPanel.java:7078` (GUI lane);
- **`behaviour.md:1011-1016`**: *"every length rule reads it exactly as it reads a piece nobody has measured ... Confirmed by Adam ... a stretch whose answers are all 0 is still not judged"*, and `:1023`. `e269aaec` added the opposite at `:1642` ("Track answered 0 on purpose is measured") and left these standing. The document now states the rule both ways, and neither passage says which rules still read the 0 as nothing: the room rule, the tail walk, and the own-tail judging's "travelled > 0".

**`behaviour.md:1646`** says *"a train with a length holds such track until its route ends"*. That is true only where nothing measured follows the 0-leg on the route. Otherwise it is released once the head is a train's length past it, which is safe but not what the sentence says.

**The escape's documentation:**

- `Layout.tailHasProvablyPassed`'s `@param pathIsUnmeasured` (`:4920`) still says "NO edge anywhere on this path has a length".
- `unmeasuredTrackThatCouldBeReleased`'s first paragraph still says the escape "means NO EDGE ANYWHERE ON THE PATH has a length". The paragraph appended below it corrects this, so the rule is stated twice in one javadoc.

**`Layout.measuredRouteIn`'s javadoc** says the route in stops where `measuredRoomAtTheEndOf` does, "so the room rule and this allowance stop at the same square". For an answered-0 leg they no longer do (ADA-A1).

**`AutonomySession.java:9912-9916`** (`runInFigures`) says answered squares "are named by nothing else, and this one says 0". Since TDA4-C2 this notice says nothing, and `BERTH_TRACK_GIVEN_NO_LENGTH` names them.

**`AutonomySession.java:9761-9763` and `:9899-9901`** justify the two berth checks' barred-side skip as "an approach no train uses". Since TDA-C8's reading, trains may use it to turn. The right reason is the one `reversalsWithoutLength` now gives: nothing stops there.

**`Point.heldBackBy`'s `@param destination`** (`Point.java:838`, `:864`: "the station being arrived at") and **`Layout.blockingOccupantOf`'s parameter name** now describe a function asked of every square on the route.

**The TDA-C10 wording** "from the station or turn nearest behind" - `open-questions.md:145` and the `f17f5a5c` message - is the pre-`ac4c7567` rule (ADA-C4 item 1).

**Mitigation:** text only.

---

### ADA-D1 - OB-295 is carried out as worded in every tier, and the reading of "sent to" holds

| | |
|---|---|
| **Disposition** | Checked - clean. |

**Checked:**

- `heldBackAlong` asks `Point.heldBackBy` of every edge's end, and the start is not an end. It serves `isPathClear` (every tier: autonomy, the hand doors, the timetable, Return Home's run) and `firstClearOrWhyNot`, the Why not Moving? text. The text is `errorDestinationBlockedByPoint` ("{0} is not available while {1} is occupied."), true of a passed square.
- The planner asks the same rule against `plannedOccupancy`, on every `next` of its search, beside `canRest` for the destination.
- The occupant exemption survives, so a train on the watched square may leave through the held one.
- The build emits `blockedBy` on every copy, pass-through ones included, so every copy is asked.
- `auditAgainstRuntime` compares against `getPossiblePaths`, which asks `isPathClear`, so planner/runtime drift on intermediate squares would show there.
- The claims are both red for the finding's reason:
  - `testATrainStandingOnTheWatchedPointClosesASquareTrainsPass` uses a non-station through-square, the occupant on the block twin, a control, and the why text.
  - `testAHomeBeyondAHeldBackSquareIsNotStagedThroughIt` asserts the runtime precondition and the planner, with a control.
- **The reading.** TDA-C9 asked about a square trains only pass. Adam's answer names both halves for THIS square, and the lock half already shut routes through it. "Sent to" therefore has to include passing, or the standing half means nothing there. The destination-only reading would have made TDA-C9 a wording fix. The work's reading also reaches stations passed through, for the same reason the lock half always did, and `behaviour.md` says so.

**Note.** `testHomeStaging.applyPlan` - named in `Point.heldBackBy`'s javadoc as the third expression of the rule - still checks each move's destination only. It never checked routes, and the new planner claim covers the intermediate case.

**Cost:** ADA-C3.

---

### ADA-D2 - TDU-C6 at the gate and the escape: one question, asked the same way, in the safe direction

| | |
|---|---|
| **Disposition** | Checked - clean. |

**Checked:**

- `unmeasuredTrackThatCouldBeReleased` and `pathIsUnmeasured` both ask `Edge.isMeasured`. It is true for a length, or for places all answered; with no places, only a length counts.
- So the gate cannot admit a railway the escape then releases under a train.
- A path whose only "measured" edges are answered 0 no longer escapes. It holds every edge until the route ends: nothing accumulates, so `behind >= length` never holds.
- Releasing an answered-0 edge once the head is a train's length past its END is safe whatever its physical length. Edges behind it under-count, and are held longer.
- `answeredAtZero` marking route tiles is needed for a leg answered 0 around a route tile to read as measured (`testARouteTileReachesTheRailwayAsAnswered`, mutation named and reachable). It also fixes the berth refusal's "N squares still have no length" (`Layout.java:10279`), which counted route tiles. That now agrees with OB-273.
- The claim's gate and escape assertions are red for the finding's reason.

**Note.** Its route-in assertion: ADA-C5. The route in's reach: ADA-A1.

---

### ADA-D3 - TDA-C8: the notice asks about a barred side again; the build was not changed, and the reading holds

| | |
|---|---|
| **Disposition** | Checked - clean. |

**Checked:**

- `reversalsWithoutLength` no longer skips a barred side.
- The two berth checks still do, which is right: nothing stops there. Their comments are ADA-C6.
- `AutonomyBuilder.nodesFor` still emits a turning copy for every side of a square trains may turn at. A barred one goes out `station:false`, `reversing:true`.
- The claim is red for the reason: the precondition shows the entry is due to the west side before the bar.

**The reading.** *"Arrivals THAT STOP THERE should only be allowed from the configured side(s). Turning shouldn't need to factor this in"* - a turning arrival is not an arrival that stops there, so turning ignores the bar. That is what the work built.

**The other reading.** "Turning stops the train, so the former governs it too" would have left the notice alone and removed the barred side's turning copy from the build: a change to where trains go on RampDown and BottomMainPost. `behaviour.md` and `open-questions.md` record the reading taken, so Adam can reverse it.

**On the frozen railway** both approaches are measured, so no new notice shows.

---

### ADA-D4 - TDA4-C2: its own sentence, as a warning, only where every square before the stop was answered

| | |
|---|---|
| **Disposition** | Checked - clean. |

**Checked:**

- `runInFigures` sends a parking berth to `givenNoRoom` only where both hold:
  - the berth rule's figure governs (`before[2] == 1`, something on the leg measured);
  - nothing before the stop is measured and no square there is unanswered (`before[0] == 0`, `before[1] == 0`; the unanswered case still `continue`s to the half-measured warning).
- The run-in notice then skips that approach only.
- `checkLengths` raises it as `WARNING`, which does not block Start: only `ERROR` counts in `autonomyHasErrors`.
- The sentence exists in all eight bundles, ASCII, with `{0}` and no figure.
- The sentence is true of the runtime. `whyABerthCannotHoldIt` judges once a span on the approach is positive, spends 0 over answered squares, and claims up to the crossing.
- The wording answers "the effective specified length of the track is 0" ("the length given for that track adds up to 0").
- The renamed claim asserts key, square and severity, and its mutation ("leave it to the run-in notice, or grade it a notice") is caught.

---

### ADA-D5 - OB-297: the way-round arithmetic and the marks' round trip

| | |
|---|---|
| **Disposition** | Checked - clean. |

**Checked by hand:**

- `unmeasuredPiecesOfTheWayRound` counts the right pieces:
  - in the edge left, the pieces ending after `leftAt`;
  - every piece of the edges between;
  - in the return edge, the pieces starting before `backAt`;
  - body places (`leftIn = -1`) from edge 0;
  - leaving and return in one edge, the pieces between.
- Checked against `testTheNoteCountsPiecesCutAtTheSwitches`' figures (2, then 1), and TDA2-C1's fixture 1 (1).
- `leftAt` skips repeats of the same square, as `edgeWhenLeft` does.
- **The round trip of the marks:**
  - `AutonomyBuilder` writes `"cut"` where `placesAlong` now gives each place its tile;
  - the configuration reader reads `"cut"` inside the same all-or-none block as `"answered"`;
  - `Edge.toJSON` writes it back.
- There is no `Edge` copy constructor or clone for the new field to miss: `setAnsweredPlaces`/`setCutPlaces` have one caller each.
- A configuration without marks gives one piece per edge, which is the old edge grain.

**Not checked:** whether those pieces are Mass Assign's (ADA-C1).

---

### ADA-D6 - OB-298 as far as one reducer is concerned

| | |
|---|---|
| **Disposition** | Checked - clean. |

**Checked:**

- All seven fields `reduce()` used to clear are swapped.
- The methods it calls are private, and nothing extends `GraphReducer`, so the fresh instance loses no override.
- No caller keeps a `getPoints()`/`getEdges()`/`getLocks()` view in a field. A cached view would now go stale where it used to follow the rebuild; none exists.
- The claim's mutation - clear and refill in place - does throw on the claim's iterators.

**The window this does not cover:** ADA-C2.

---

### ADA-D7 - TDA-C10: where a figure is given, it is the railway's own bound for a train setting off from that copy

| | |
|---|---|
| **Disposition** | Checked - clean. |

**For a single leg from a station copy S:**

- `measuredRouteIn([S->P])` is the leg's length when measured.
- `whyTooLongForThisRoute` admits a train longer than the room only up to that (FR-087), and quotes `max(room, routeIn)`.
- So "a train longer than {4} is refused" is exact.

**For a turning copy R:** the route in stops at R's leg, because `isReversing()` breaks the walk back, so the leg from R is the bound.

**Copies no train is started at are left out:**

- barred copies are `station:false`, and not `reversing` unless they turn;
- the platform's own other copies are excluded.

**`ac4c7567`'s claim** is red for the Tunnel case, with a control.

**Everything else:**

- The figure is given only at a station autonomy may choose, below its maximum, and only where the run-in notice is already due.
- `{4}` is `getThird()`, passed by both panels.
- The eight bundles have `{2}`, `{3}` and `{4}`, all ASCII.

**What is not right around it:** ADA-C4.

---

### ADA-D8 - The answers outside this lane, read where they touch the running model

| | |
|---|---|
| **Disposition** | Checked - clean. |

**TDD5-C1 ("Follow the train").**

- `sameSetup` is now the session alone. `whereTheAnswerGoes` still writes only to the running copy of that name, and only where it holds the same train, from the same side, with the same road. So after a configuration load the road goes onto the running railway and into the active configuration, and a replaced session still drops it.
- **The reading holds.** The train on the rails is the one the answer describes.
- **The other reading** would also have written it into the configuration the question was asked in. As built, that configuration keeps the placement without the road, so the train's tail stops at the switch if the operator switches back. `behaviour.md` 5c is silent on that configuration.

**OB-296.** `copyToPlaceOn` is deterministic: `facingAfterAPaste` over the usable copies' headings, then the first copy the build made. The heading is read from the running railway before the move. I did not verify the GUI doors themselves.

**TDU2-C3 and TDD-C11** do not touch this lane's code.
