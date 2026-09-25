# ADA2 - Automation lane, validation round 2: round 1's fixes and records, `d05a6356..117298de`

**Status:** open

Open: every finding below; none has a disposition yet.

**Prefix:** ADA2

**Reviewed:** branch `autonomy-diagram-r0` at `117298de` (HEAD, 2026-09-25 02:24). The range is `d05a6356..117298de`: `000c81c8` (the 2026-09-24 documents removed), `2c7a5b47` (claims), `1098c9b8` (fixes and the re-blessed configuration), `b6835920` (two pins), `117298de` (records). The lane is `src/org/traincontrol/automation` and `src/org/traincontrol/automationui`, and the claims that hold them.

**Method.** I read `docs/reviews/FANOUT.md` and `docs/reviews/README.md` first, then round 1's `docs/reviews-2026-09-25/ADA.md` with the disposition under every finding, and the dispositions in `ADU.md` and `ADD.md` that touch this lane (ADD-B1, ADD-C6, ADD-C9, ADU-C7). I read TDU-C6 as it stood at `d05a6356` (`git show d05a6356:docs/reviews-2026-09-24/TDU.md`) and OB-274 in behaviour.md 5b. I read each commit as a diff (`git show <c> -- <path>`) and then the code at HEAD around every changed method, and its callers:

- `Edge`'s marks and `isMeasured`, with its two callers;
- in `Layout`: `measuredRouteIn`, `theApproachItselfHoldsIt`, `whyTooLongForThisRoute`, `measuredRoomAtTheEndOf`, `pathIsUnmeasured`, `tailHasProvablyPassed` and its caller, `unmeasuredTrackThatCouldBeReleased`, `whyItWouldMeetItsOwnTail`, `askedFor`, `askedForOnTheWayRound`, the configuration reader, `isPathClear`'s FR-001 block, `heldBackAlong`, `blockingOccupantOf` and the inactive-intermediate refusal;
- in `HomeStaging`: the held-back checks and `plannedOccupancy`;
- in `AutonomySession`: `rebuild` and every read of the `reducer` field, `piecesToMeasure`, `stretchesALengthRuleReads`, `stretchesNeedingALength`, `squaresNeedingALength`, `needsALength`, `squareNeedsALength`, `runInFigures`, `refusingFigures`, `measuredWayIn`, and `copyFacing` and `departableFacingsFor` only as the Place fix calls them;
- `AutonomyBuilder`'s place marks, `GraphReducer.roomAfterTheLastSwitch`, and Mass Assign's walk in `AutonomyEditorPanel`, to compare its lists with the marks.

For each claim in the range I asked whether its fixture reaches the answer's own case and whether its named mutation turns it red. I grepped for sibling sites: every `isMeasured()`, every `isPlaceAnswered`, every `reduce()` and every `reducer =`, every "station being arrived at", and every "deadlock" in the range's source diff. I checked the re-blessed `test/baseline/configuration.json` with read-only Python fed on stdin (no file written): a census of the changed lines, whether each square carries one mark on every edge, whether any measured place is marked, and the tile type of each newly "answered" place in `test/baseline/layout`'s gleisbild. I checked the date of the frozen railway (`git log` on `test/layouts/live-snapshot/config/autonomy/setup.json`) against the commit that built OB-274. **Nothing was executed** (no JVM, no test, no build). Every finding that depends on behaviour carries a verification request. I did not open `cs2_sample_layout/`. The GUI halves of OB-296, TDU2-C3, TDD5-C1 and TDD-C11 are another lane's.

Grades: A: wrong behaviour on the layout, or data lost. B: incorrect results in specific configurations. C: cosmetic, a narrow case, or text. D: checked and clean. Each finding says what mitigates it.

---

### ADA2-C1 - The refusing figure now counts a way in with nothing measured on its last leg as 0, and that 0 is taken as the least: a platform with one unmeasured way in loses the figure its measured way in gives

| | |
|---|---|
| **Disposition** | Fixed - a way in with no length quotes no room, so it takes no part and hides nothing: the figure is taken over the ways {3} is taken over (`roomTheNoticeQuotes`), each the larger of the room and the route in, as the refusal quotes.  Claim d514de19 (`testTheRefusingFigureWalksTheBuiltRailwayAsTheRouteInDoes`, a way in with no length), fix 86b458c8; mutation W5 red. |
| **Where** | `1098c9b8`: `AutonomySession.measuredWayIn` (`AutonomySession.java:10129-10133`, `if (length <= 0) answer = 0;`), merged at `:10095` (`out.merge(square, way, Math::min)`) and read at `:9989-9995` (`refusedAbove != null && refusedAbove > 0`); against `Layout.measuredRoomAtTheEndOf` (`Layout.java:10952-10954`, `:10964`) |

Adam's answer to TDA-C10 was *"Add the refusing figure where there is one."* The walk back stops at a leg with no length (ADA-A1) and returns 0 for it. That 0 is right for a leg **behind** the leg into the platform: the route in counts the legs after the gap, and the gap adds nothing.

It is wrong for the leg **into** the platform:

- The runtime does not judge that way at all. `measuredRoomAtTheEndOf` answers null there: for a last leg over a switch with room -1 and length 0 (`bound > 0 ? bound : null`), and for a switchless last leg of length 0 (`room > 0 ? room : null`). With a null room, `whyTooLongForThisRoute` refuses no train on that way, so that way has no refusing figure.
- The walk returns 0 for it. The square's minimum becomes 0, and `refusedAbove > 0` then drops the figure for the whole platform, including the one a measured way in gives.
- At `d05a6356` the same leg was skipped (`if (!isMeasuredOnTheRailway(edge)) continue;`). So this is a regression: a figure that was given before `1098c9b8` is not given now.

**Input that goes wrong.**

- Platform P: autonomy may choose it, and it is set to take a train of 6.
- Way in 1: from station S1, over a switch, with 2 measured before the switch and 2 after it. The room is 2 and the route in is 4.
- Way in 2: from a station S2 east of P where trains may turn, with nothing measured.
- The notice is due, with {3} = 2.
- At HEAD it has no {4}. At `d05a6356` it said 4.
- On the railway: from S1 a train of 5 is refused, quoting 4; from S2 no train is refused.

**What mitigates it.**

- It is a notice, and no train moves differently.
- MT-583's comment says the walk leaves the frozen railway's figures unchanged (TopMainR1Inter 3, LowerFront 4), and the pin asks for TopMainR1Inter's figure.
- It needs a platform with one way in that has nothing measured on its last leg. That is plausible on a railway measured in parts, and his live railway may differ from the frozen copy.

**Fix.** Leave a leg into the platform with no length out of the minimum, as `isMeasuredOnTheRailway` did, and keep the 0 for a leg behind it.

**Verification request.** `core.testAutonomyDiagramSession`, fixture `platformWithTwoApproaches()`:

- Make 1,1 a station ("Behind").
- Make 5,1 a station ("Platform") with `maxTrainLength` 6.
- Make 7,1 a station with `canReverse` TRUE. Without it, its leg is filtered out as neither over a switch nor from a turn.
- Give 2,1 and 4,1 a length of 2 each, leave 6,1 unmeasured, and rebuild.
- **Proves it:** `findingFor(RUN_IN_AT_A_PLATFORM_REFUSED)` is null and `findingFor(RUN_IN_AT_A_PLATFORM)` is present.
- **Refutes it:** the refused sentence is present with `getThird() == 4`.
- **Cross-check:** `Layout.whyTooLongForThisRoute` refuses a train of 5 from Behind quoting 4, and refuses nothing from 7,1's turning copy.

---

### ADA2-C2 - "Never less than {3}" is not true: a way in whose track past the switch is unmeasured gives a {4} but no {3}

| | |
|---|---|
| **Disposition** | Fixed - the figure takes only the ways in {3} takes, so it is never under {3}; a way in with nothing measured past its switch is the half-measured notice's.  Claim d514de19 (never under the room), fix 86b458c8; mutation W6 red.  {4} equal to {3} is kept, by design: that is TopMainR1Inter, the platform TDA-C10 was asked about, where the shortest way in holds nothing behind its switch and *is refused instead* is what happens.  A first version of the fix dropped it and the railway's pin went red before the commit, so it was put back. |
| **Where** | `behaviour.md:1522` (*"Only for the notice's own ways in, over a switch or from a turn, so it is never less than {3}"*); `refusingFigures`' javadoc (`AutonomySession.java:10013-10015`); `runInFigures` `:9982` (`(room <= 0 && !theBerthRulesFigure) ... continue`) against `refusingFigures` `:10085` (every leg with `roomAtTheEnd`) |

The two halves of the notice take different sets of ways in:

- `runInFigures` leaves out an arriving leg with nothing measured past its switch (room -1).
- `refusingFigures` takes the same leg, because it has `roomAtTheEnd`, and counts the leg's whole length. Where the track before the switch is measured, that length is positive.
- The runtime refuses on it, so the figure is true. `measuredRoomAtTheEndOf` takes the leg's length as the bound (`Layout.java:10952`), the route in is the same, and the refusal quotes it.
- But the figure can be below {3}, which comes from another way in.

**Input.** Take `platformWithTwoApproaches()` with the platform set to 6:

- West: 2 measured before the switch and nothing after it. The leg's length is 2 and its room is -1.
- East: 3 measured, from a station trains turn at. Its room is 3.
- The notice reads *"only 3 of track is measured between it and the switch ... a train longer than 3 stands across that switch ... Coming in the shortest way, over 2 of measured track, a train longer than 2 is refused instead."*

This is the shape ADA-C4 item 3 named, through a second door. `1098c9b8`'s filter closed the adjacent-station door only. The records then wrote the property as settled: "so it is never less than {3}" in behaviour.md, and "quoting it beside {3} said a train is refused above less than the room it stands in" in the javadoc.

Related, and older than the range: where nothing before the switch is measured and the station is straight behind, {4} equals {3}. The sentence then says a train longer than 2 both stands across the switch and is refused.

**What mitigates it.** It is notice text, and each figure is the railway's own for its way in.

**Verification request.** The fixture above:

- Give 2,1 a length of 2, leave 4,1 unmeasured, give 6,1 a length of 3, and make 7,1 a station with `canReverse`.
- **Proves it:** the refused finding has `getDetail() == 3` and `getThird() == 2`.
- **Cross-check:** `whyTooLongForThisRoute` refuses a train of 3 from Behind quoting 2.

---

### ADA2-C3 - The walk back stores a leg that a loop cut short as having no way in, and it counts ways in the railway never runs

| | |
|---|---|
| **Disposition** | Fixed - the route in is worked out for every leg together and lowered until nothing changes, so a loop is walked whole and nothing cut short is remembered; never through or into a switched-off point, and never from or through another copy of the platform.  A train standing on a switched-off station is still a way in - the railway sends one out by hand, and ParkingTrack12 into LowerFront is refused above 4 - which the first fix dropped: the pin b152d025 was red against 86b458c8 until 12b46942.  Claims d514de19 (the loop, the switched-off point, the platform's own copy) and 8548dbf6 (legs in any order); mutations W2, W3, W4, W7 red. |
| **Where** | `AutonomySession.measuredWayIn` (`:10117-10154`: `if (!reached.contains(from) \|\| !walking.add(leg)) return Integer.MAX_VALUE;` and then `known.put(leg, answer)` for every leg on the way back); `refusingFigures` `:10065-10074` (`reached`) and `:10090` (the same-square test, on the first leg only); against `Layout.isPathClear` `:2561-2569` (a route through a point that is switched off is refused) |

**The stored answer.**

- A leg first reached while the walk is inside a loop of sensors with no station or turn on it is worked out with the loop cut: the leg the walk started from answers `MAX_VALUE`.
- That answer is stored in `known`.
- A later platform whose way in runs back through the same leg reads the stored `MAX_VALUE`. Its figure is then missing, or it is taken from another way in and is too high. A figure that is too high is false: a shorter train coming round the loop is refused below it.

**Input.**

- Built points: S (a station), A, B and C (sensors), P1 and P2 (stations).
- Edges in this order, each of length 1: S→A, A→B, B→C, C→A, then B→P1 and C→P2, each with `roomAtTheEnd` 1.
- B→P1 is walked first:
  - B→C calls A→B, which is still on the walk, so B→C is stored as `MAX_VALUE`.
  - So is C→A.
  - P1 gets 3.
- C→P2 then reads B→C's stored value and gets `MAX_VALUE`, where its true route in is 4 (S→A→B→C→P2).

**Ways in the railway never runs.** The walk ignores whether a point is switched on:

- A way in through a switched-off sensor is counted, though `isPathClear` refuses any route through one (*"Inactive really means nothing can pass"*).
- A way in that starts at another copy of the platform itself, round a loop, is counted too, because only the first leg is checked for the same square. A route from a platform to itself is no journey.

Either can give a figure lower than any refusal the railway makes.

**What mitigates it.** It is a notice. It needs a loop with no station on it, a switched-off point on the way in, or a loop back into the same platform. MT-583's comment says the frozen railway's figures are unchanged.

**Verification request.**

- Call `refusingFigures` by reflection with the JSON above. Give it a `named` map that sends each point to a TileKey of its own.
- **Proves it:** P2 is absent from the result. With a second way in S2→P2 (length 9, `roomAtTheEnd` 1) added, P2 maps to 9.
- **Refutes it:** P2 maps to 4.

---

### ADA2-C4 - Two dispositions say code text was changed that was not: the isPathClear comment still says "nothing else is held up by it", and `blockingOccupantOf` still takes "the station being arrived at"

| | |
|---|---|
| **Disposition** | Fixed - the isPathClear comment names the deadlock OB-295's standing half takes on; `blockingOccupantOf`'s parameter and `heldBackBy`'s @return say every square a route arrives at (86b458c8).  Text. |
| **Where** | `Layout.java:2879-2887`; `Layout.java:5581`; the dispositions of ADA-C3 and ADA-C6. `git diff d05a6356 117298de -- src/` contains neither "deadlock" nor "held up". |

**ADA-C3's disposition** reads: *"behaviour.md section 1, the isPathClear comment and open-questions.md name the cost and the deadlock"*.

- behaviour.md (`:74-78`) and open-questions do name them.
- The comment does not, and no commit in the range touches the FR-001 block.
- It still ends *"The cost is the one the setting names: while that train stands there, the square is shut to routes through it as well as to arrivals - somebody named both squares, and nothing else is held up by it."*
- Five lines above that, it quotes `Edge.isLockHeld`'s reason the lock half counts only routes: a parked train *"a permanent roadblock, and two could deadlock"*. That is the hazard OB-295's standing half now accepts, and the comment gives it as the reason for the other half without saying it now applies to this one.

**ADA-C6's disposition** reads: "the comments and parameter text in 1098c9b8".

- `Point.heldBackBy`'s two `@param destination` lines were fixed.
- `Layout.blockingOccupantOf`, the runtime's name for the same rule, still reads `@param destination the station being arrived at`. It was listed in ADA-C6 by name, and `heldBackAlong` calls it for every square a route arrives at.
- `Point.heldBackBy`'s `@return` still says "null when the destination is free".

**What mitigates it.** Text only. The behaviour is recorded correctly in behaviour.md.

---

### ADA2-C5 - The open question on an answered 0 rests on a copy of his railway frozen before a 0 could be answered, and leaves out what the narrow reading costs

| | |
|---|---|
| **Disposition** | Fixed in the record - the open question says the frozen copy predates answering 0 and cannot say whether his railway has such a stretch, and gives what the narrow reading costs: a train the whole measured run would hold is refused where a 0 lies between measured stretches, with a typed length for nothing the only way past (the round's records commit).  His live setup was not read: it is his, uncommitted, and this work does not open it. |
| **Where** | `open-questions.md:135-140` (*"Recommended: leave it at the gate - your own railway has no stretch answered 0 end to end, and a rule that admits more should wait for a case that needs it"*); `test/layouts/live-snapshot/config/autonomy/setup.json` (refrozen in `a9590790` from `2958fcf3`, his layout as of 2026-09-23 04:07, with `e36df979`'s barred side at 07:52); OB-274 built in `65e7fc73`, 2026-09-23 05:22 |

**The premise.**

- ADA-A1's mitigation said *the frozen railway* has no answered 0. The card turned that into *your own railway*.
- The frozen copy is his layout as it stood before a length could be answered 0 at all: an answered 0 is stored as a 0 in `tileLengths`, and until `65e7fc73` a 0 removed the length. So the copy cannot contain one.
- Whether his railway has one now is not known. He asked for the 0 for exactly this case (*"for adjacent tracks"*), and Mass Assign has offered it since the same morning.

**What the card leaves out.** It gives the wider reading's cost ("changes what a standing train blocks") but not the narrow reading's.

- A leg answered 0 between measured legs on the way into a platform autonomy may choose ends the route in there. This is ADA-A1's shape: a hop between adjacent sensors.
- So a train that the whole measured run would hold is refused. The refusal says the track *"measures"* only what lies after the 0 (`errorTrainTooLongForBerth`).
- Its only remedy is to type a length for track he has answered is nothing: the 1 he used as a placeholder before OB-274.
- The wider reading admits that train and claims its tail over the 0 and behind it.
- So "a case that needs it" may already exist on his railway.
- None of this is new. It is OB-274's "same meaning to the model", and it was so before TDU-C6. But it is the half of the trade he is being asked to weigh.

**What mitigates it.** It is text. The narrow reading is safe (ADA2-D1), and the decision is his either way.

**Verification request.** Count the 0 entries in `tileLengths` in his live `config/autonomy/setup.json`, by Adam or with his permission; the frozen copy cannot answer it.

- **Refutes the premise:** any 0 on a leg on the way into a station autonomy may choose.
- **Confirms it:** none.

---

### ADA2-C6 - "A reader now holds the old railway or the whole new one" is true of the builder, not of the session's own readers; the filing's question is still open

| | |
|---|---|
| **Disposition** | Fixed - the comment says what holds: a build takes the field once; the session's own methods rely on every rebuild running on the event thread, as every caller does - the loads and the revert, Delete Everything, and the editor's edits (86b458c8).  That reading is the answer to the filing's question, and OB-298's receipt already says there is nothing to run by hand. |
| **Where** | `AutonomySession.java:443-451` (the comment); ADA-C2's disposition (*"whichever thread rebuilds, a reader holds the old railway or the whole new one, which answers the filing's question by construction"*); the field read afresh at `:9906` and `:9918` (`runInFigures`, per station), `:5974-5978` (`everyArrivalMustTurn(graph, reducer, tile)` inside a loop over `reducer.getPoints()`), `:6038` (`AutonomyChecks.run(graph, reducer, ...)`); `graph` (`:42`) is not volatile and is assigned first, at `:437` |

**What the fix does.**

- The builder takes the reducer once (`:4439`), and no published reducer is ever reduced again: `reduce()` has one caller, on the fresh instance.
- So a build is whole. That closes OB-298's incident and ADD-C9 (ADA2-D4).

**What the comment claims beyond that.**

- The session's own methods read the volatile field again at every use. A rebuild on another thread between two reads gives one method two railways.
- `graph` is published before the reducer, so a check can pair the new graph with the old reducer.
- "Whichever thread rebuilds" therefore does not hold. What holds is that every door rebuilds on the event thread, which was the filing's premise.
- The disposition says that premise is no longer needed, and the filing's question - does any door, the start-up resume or `rebuildRunningLayoutFromSetup`, rebuild off the event thread? - is still unanswered.

**What mitigates it.** No door is known to rebuild off the event thread. The finding is about a comment and a disposition.

---

### ADA2-C7 - The own-tail note counts each switch and crossing on the way round, where Mass Assign asks one length for all of a page's; the user guide calls them all pieces

| | |
|---|---|
| **Disposition** | Fixed - Mass Assign's answers are the keys: a piece, a page's switches, a page's crossings, one length each (86b458c8); the note says so in Mass Assign's own name, in eight languages, and the user guide uses the same words (the round's records commit).  Claim d514de19 (`testTheSwitchesOfAPageAreOneThingToMeasure`); mutations W10, W16 red.  The baseline was re-blessed: only the marks moved, 245 out and 245 in. |
| **Where** | `AutonomySession.piecesToMeasure` (`:4470-4473`, one key `"square <tile>"` per switch and per shared square); `autolayout.errorOwnTailPartlyUnmeasured` (*"{0} stretch(es) of track on the way round have no length at all"*); `Automation.md:166` (*"how many pieces - the same ones Mass Assign Lengths asks you for"*); `autosetup.ui.promptMassAssignSwitches` (*"Enter the length of one turnout; each of them is given it"*) |

- Take a way round with one piece and four switches that have no length.
- The note says *"5 stretch(es) of track ... have no length at all"*.
- Mass Assign asks two questions: the piece, then *"Switches on this page without a length: N"*.
- The count is right as a count of things with no length, and MT-571's new comment says "pieces, switches and crossings". The user guide and the sentence use nouns the editor does not.

**What mitigates it.** Wording only.

---

### ADA2-D1 - TDU-C6's narrow reading is carried out in both places it names, and the reading holds against his words and the ruling of 2026-09-23

| | |
|---|---|
| **Disposition** | Checked - clean. |

**Checked in the code.**

- `Edge.isMeasured` has two callers: the gate (`Layout.java:9604`) and the escape (`:4967`).
- Every other length rule asks `getLength() <= 0`:
  - `measuredRouteIn` (`:10157`);
  - `measuredRoomAtTheEndOf` (`:10964`);
  - `walkOneTail`;
  - `claimUpToWhereTheRailsPart`.
- The berth rule spends 0 over an answered square.
- The only runtime reader of an answer is the count in the berth refusal (`:10285`).
- The gate and the escape ask one question. A path with an answered-0 edge never escapes: its edges are held until measured track after them covers the train, or to the end of the route - the safe direction.
- The gate is the door he uses: his configuration runs with Atomic Routes off (`Layout.java:8766-8768`).

**The reading.**

- His words answer a finding about the gate's remedy, and *"so non-atomic should be allowed"* names the consequence he wanted.
- OB-274's *"same meaning to the model"*, and his confirmation that *"a stretch whose answers are all 0 is still not judged"*, stand everywhere else.
- Of the three readings, the narrow one is the only one that neither overturns the ruling of 2026-09-23 without being asked nor opens ADA-A1's gap.

**The other readings.**

- **The wide reading:**
  - the room rule would judge a last leg answered 0, refusing every train with a length unless the route in holds it;
  - the route in and both tail walks would carry on over a 0-leg, claiming its places;
  - the result admits more at platforms and blocks more behind standing trains.
- **The middle reading** (the route in alone) was unsafe and has been reverted.
- behaviour.md 5b (`:1019-1021`) and 5d state one rule. The open question and what it leaves out: ADA2-C5.

---

### ADA2-D2 - ADA-A1's fix puts the route in back where the tail walk stops, and its claim tests that shape

| | |
|---|---|
| **Disposition** | Checked - clean. |

**Checked:**

- `measuredRouteIn` (`:10151-10160`) stops at `leg <= 0`, as `walkOneTail`, `claimUpToWhereTheRailsPart` and `measuredRoomAtTheEndOf` do. The reversal stop is unchanged.
- Its readers follow it:
  - `theApproachItselfHoldsIt`, and through it FR-087 in `whyTooLongForThisRoute` and `HomeStaging`;
  - the refusal's `refusedAt`.
- The claim `testTrackAnsweredZeroIsMeasuredTrack` asserts `measuredRouteIn([3, answered 0, 2]) == 2`:
  - putting `isMeasured` back gives 5, so the mutation is caught;
  - the assertion is made with the leg answered, which is the answer's own case.
- `measuredRouteIn`'s javadoc sentence "the room rule and this allowance stop at the same square" is true again.

---

### ADA2-D3 - OB-297's marks are Mass Assign's list, they survive the configuration round trip, and the re-blessed configuration changed only by added fields

| | |
|---|---|
| **Disposition** | Checked - clean. |

**The marks.**

- `piecesToMeasure` is two things:
  - `stretchesNeedingALength`, which is `needsALength` (nothing measured, and not every square answered), keyed per piece;
  - every switch and shared square for which `squareNeedsALength` holds.
- Mass Assign's three steps walk the same predicates, page by page (`AutonomyEditorPanel:9881-9883`).
- A route tile is in no piece, so it is not marked. A piece with its units on a station's square is measured, so it is not marked either. Both halves of ADA-C1 are closed.

**The round trip.**

- `AutonomyBuilder` writes the marks (`:1316`, `:1324`).
- The reader takes them inside the all-or-none block, and only with `lengthsAsked` (`Layout.java:12872-12881`).
- `Edge.toJSON` writes them back (`:865-879`).
- `Edge` has no copy constructor, and the setter has one caller.
- The running layout is always parsed from `buildConfiguration()` (`AutonomyViewerPanel:815`), so the fallback of one key per edge is reached only by a hand-written or legacy configuration.

**The count.** `askedForOnTheWayRound` counts distinct keys strictly between the leaving place and the return. The indices come from `startsAt` plus `leftAt`, the same bounds the per-edge code used before.

**The re-blessed configuration.**

- The diff adds 885 `toMeasure`, 101 `lengthsAsked` and 24 `answered` lines.
- The 885 `"length": 0` lines changed only by gaining a comma.
- Every edge with places carries `lengthsAsked`.
- Each square carries one mark on every edge it lies on, and no measured place is marked.
- The 24 `answered` places are eight squares, and every one is a `fahrstrasse` tile in `test/baseline/layout`'s gleisbild.

**The claims.** Checked: `testMassAssignLengths.testTheBuildMarksWhatMassAssignAsksFor` (the route tile, and the station's share), its frozen-railway pin, and `testTheNoteCountsWhatMassAssignAsksFor`.

**Left unguarded.** The pin compares the marks with `squaresNeedingALength`, not with the three lists Mass Assign walks. A fourth step added to Mass Assign would not be seen.

---

### ADA2-D4 - OB-298: a reducer is published only once reduced, and a build holds one

| | |
|---|---|
| **Disposition** | Checked - clean. |

**Checked:**

- `rebuild` reduces a fresh instance and only then assigns it (`:447-451`). That is the field's only assignment, and `reduce()` has no other caller.
- The field is volatile.
- `builder()` takes the reducer once (`:4439`). Nothing reduces a published reducer again, so a build reads one railway's edges and locks together. That closes ADD-C9.
- The claim is a source-shape pin of that one site, and its mutation is caught.

**Its limits:** ADA2-C6.

---

### ADA2-D5 - OB-295's reading still holds, and nothing in the range changed its code

| | |
|---|---|
| **Disposition** | Checked - clean. |

**Checked:**

- The range changed only `Point.heldBackBy`'s parameter text.
- Round 1's check still stands: the rule is asked of every square a route arrives at, at runtime, in Why not Moving? and by the planner.
- The deadlock is now named in behaviour.md (`:74-78`) and open-questions.md. The code comment is ADA2-C4.
- MT-584 names a square trains only pass on his railway (BottomMainAPre, held back by TunnelLeftPark).

**Older than the range, noted.** The planner's `plannedOccupancy` also consults points that share a sensor (`HomeStaging.java:2395-2410`). Since OB-295 it does so for every square a plan passes, not only its destination. It fails safe: NO_PLAN_FOUND, never a wrong movement. It matters only while a restriction is set, as during MT-584.

---

### ADA2-D6 - TDA-C8 and TDA4-C2: only their comments and wording changed in the range, and the comments are now true

| | |
|---|---|
| **Disposition** | Checked - clean. |

**Checked:**

- `reversalsWithoutLength` still asks about a barred side.
- The two berth checks still skip one, and now give the right reason: a train may come in that way and turn, but none stops there.
- ADU-C8's wording ("every train with a length") is in all eight bundles, as `\uXXXX` escapes.

---

### ADA2-D7 - ADA-C5, and ADA-C6 apart from ADA2-C4, are done

| | |
|---|---|
| **Disposition** | Checked - clean. |

**Checked:**

- The TDU-C6 claim sits above `testARailwayCountsItsUnmeasuredDrivableTrack`'s javadoc, with a javadoc of its own.
- These now say "every length rule but the Atomic Routes gate and its escape":
  - `GraphReducer.java:290`;
  - `AutonomySession`'s assign-stretch, assign-switch and Segment Length comments;
  - `AutonomyEditorPanel:7078`.
- `tailHasProvablyPassed`'s `@param` and `unmeasuredTrackThatCouldBeReleased`'s first paragraph say "measured".
- `runInFigures`' answered-0 sentence names the berth's own warning.
- "From the station or turn nearest behind" (open-questions.md, `runInFigures`) now describes the walk back.

---

### ADA2-D8 - Where the walk back gives a figure, it matches the route in, and its claims are red for the finding's own reason

| | |
|---|---|
| **Disposition** | Checked - clean. |

**The walk matches the route in:**

- A leg behind the platform leg with no length adds nothing, and nothing beyond it counts.
- A station or turning copy is counted and ends the walk.
  - `measuredRouteIn` does not stop at a station it passes, but a train can be started there, so the least way in is from that station.
- A copy a train may not arrive at is not a station, so it is not a start.
- `reached` leaves out copies no train can get to: the Tunnel case.
- The filter keeps a switchless leg only where it starts at a turn, which closes ADA-C4 item 3's adjacent-station door.

**The claims.**

- `testTheRefusingFigureCountsBackOverASensor` is red at `d05a6356`, which gave no figure with a sensor behind the platform. Its 5 (2, then 1, the switch and 2) is `whyTooLongForThisRoute`'s `refusedAt` from Start.
- `testTheRefusingFigureIsTheRailwaysOwn` ties TopMainR1Inter's notice to the refusal on one route from TopR1ParkShort.
  - It does not prove the figure is the least over every way in.
  - It takes one `bfs` route per pair of copies. Where two routes join a pair, the shuffle could return the other one; not checked.

**Not clean:** ADA2-C1, C2, C3.
