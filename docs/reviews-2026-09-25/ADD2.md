# ADD2 - Documents, tracker and tests lane, validation round 2: round 1's fixes and records

**Status:** open

**Prefix:** ADD2

**Reviewed:** branch `autonomy-diagram-r0` at `117298de` (HEAD, 2026-09-25). The range is `d05a6356..117298de`: `000c81c8` (the 2026-09-24 round closed), `2c7a5b47` (round 1's claims), `1098c9b8` (the fixes and the re-bless), `b6835920` (two pins) and `117298de` (the records). The lane is `docs/`, `Readme.md`, `Automation.md`, `docs/manual-tests` and `test/`.

**Method.** I read `docs/reviews/FANOUT.md`, `docs/reviews/README.md` and the rules in `docs/manual-tests/README.md` first, then round 1's three reports as committed in `docs/reviews-2026-09-25/` (ADD.md in full, with every disposition; ADA.md and ADU.md in full, for the dispositions my lane's findings were "fixed as"). I read each commit of the range as a diff with `git show`, and the code at HEAD behind every document sentence the range added: `Layout.measuredRouteIn` (against `git show 45cfa410`), `measuredRoomAtTheEndOf`, `whyABerthCannotHoldIt`, `askedFor`/`askedForOnTheWayRound`, `Edge.isMeasured` and the new marks, `AutonomyBuilder`'s place loop, `AutonomySession.rebuild`, `piecesToMeasure`, `runInFigures`, `refusingFigures`/`measuredWayIn`, `LayoutRightclickAutonomyMenu.copyToPlaceOn`/`placeFacing` and the LD-C6 block, `HomeLocomotiveMenu`, `TrainControlUI.whyAutonomyWillNotStart`/`whyAHandSendIsRefused`, `Point.heldBackBy`, `Layout.blockingOccupantOf`, `HomeStaging`'s outcomes, and the English bundle's sentences each new tracker entry quotes. I read every claim and pin in the range and asked whether it tests its finding's own case and whether its mutation would be caught, using the round's own mutation specs (`mut_v1.json`, `mut_v1b.json` in the session scratchpad) to see which line each V-number changes. I checked the records against their sources: the store read-only through `sqlite3` with `mode=ro` (4,385 rows, 4,028 refs, the 52 new rows' statuses), `tests.md` dispositions and ledger recounted with `grep`, `issues.md`'s receipts, every test name the range's documents cite (grep for `void <name>(` under `test/`), and the re-blessed `test/baseline/configuration.json` (every changed line classified; the 8 squares marked `answered` looked up in `test/baseline/layout/.../1 - Main.cs2` and `2 - Bottom.cs2` with inline `python -c`). From the frozen railway (`test/layouts/live-snapshot/config/autonomy/`) I read `pointNames`, `barredArrivals`, `blockedPoints`, `tileLengths` and the point properties, and from `autonomy_legacy/autonomy.json` the old graph's edges around Tunnel and BottomMainA. I read the session's battery record `battery-0925a` (console and the one failing class's junit report). **Nothing was executed**: no JVM, no test, no `mutate.py`, no `triagedb.py`. No git state was changed, no file was written but this one, and `cs2_sample_layout/` was not opened. Every finding that depends on behaviour carries a verification request.

Grades are by consequence on the railway. A: wrong behaviour on the layout, or data lost. B: incorrect results in specific configurations. C: text, a narrow case, or a test or record that proves or says less than it claims. D: checked and clean.

---

### ADD2-C1 - MT-263 and MT-580's new comment tell Adam two different things about the same greyed Start tooltip over the same break, and MT-580 now sends him to run them together

| | |
|---|---|
| **Disposition** | Fixed - a comment on MT-263: over this break Start's tooltip and Start's message name the error count, as Adam saw on 2026-09-24, so its *one thing* expectation is superseded; MT-580 checks the same tooltip (the round's records commit). |

**The two entries.**
- MT-580's round-1 comment (`tests.md:28443`, ADU-C5): *"Over MT-573's break, Start's greyed item says autonomy cannot start while the setup has 1 error(s) ... It can be run in the same sitting as MT-263 and MT-573."*
- MT-263 (fixed unvalidated, on the ledger), its 2026-09-24 "To run now" over the same break - untick Exclude Page on 4 - Combined - `tests.md:225`: *"Expect: Start is greyed; the tooltip and the Start message both say one thing has to be dealt with first and point at the count along the top of the diagram."*

**Which is true.** `TrainControlUI.whyAutonomyWillNotStart(int, int, boolean)` (`:23850`) answers `errorCannotStartWithErrors` (*"Autonomy cannot start while the setup has {0} error(s)..."*) whenever there is an error finding, before it looks at blocking problems. The repeated sensor page is an ERROR finding (`AutonomyChecks.java:1116`, OB-150), so over this break Start's tooltip and Start's message name the error count. "One thing has to be dealt with first" (`errorCannotBuildDetailOne`) is the wording for a setup that will not build with no finding, which this break no longer produces. Adam's own MT-263 result says the same (`tests.md:229`: *"I get an error message saying errors must first be fixed"*), and nobody corrected MT-263's expectation after it.

**What the range did.** ADD-C1/ADU-C5 corrected MT-580 and did not reach its sibling, and ADD-C11's same-sitting note then paired the two. In one sitting Adam now reads that Start's tooltip says "one thing" (MT-263) and "1 error(s)" (MT-580); one of the two verdicts will be "does not work" against correct code, and a later round could "fix" Start's wording to match the wrong one.

**What mitigates it.** Nothing moves on the railway. MT-580's comment is right.

**Fix.** A dated comment on MT-263: over this break the tooltip and the Start message name the error count, since the repeated page became an error finding, and its step 2/3 expectation is superseded by what Adam saw on 2026-09-24. (The "1" in MT-580's comment is ADU-C5's count; the sentence's form is what step 4 checks.)

---

### ADD2-C2 - The one question put to Adam this round has no open row in the store, and its recommendation rests on the frozen copy, not his railway

| | |
|---|---|
| **Disposition** | Fixed - ADU-C7's row is *Open - Adam's decision* and carries the question; the recommendation says the frozen copy, and gives the narrow reading's cost (ADA2-C5); TDU-C6's row names ADA-A1 (the round's records commit).  His live setup was not read. |

**The question.** `open-questions.md:135-140` puts to Adam whether an answered 0 should count as measured beyond the Atomic Routes gate. It is the only **Open** item the round added.

**The store does not carry it.** The three rows it came from are all `Closed`: ADA-A1 ("Fixed - the narrow reading ... put to Adam"), ADD-B1 ("Fixed as ADA-A1") and ADU-C7 ("Not a defect under the narrow reading ... Whether to widen is the open question"). No row anywhere has an "Open - Adam's decision" status. FANOUT step 5 sets such a row to "Open - Adam's decision" - the decisions round did, which is why `d321346b` counted "34 closed, TDD5-C1 put to Adam". So:
- `open-questions.md:420` - *"Everything still open above is open in that store too, so it can be queried"* - is now false, and its SQL does not list this question;
- anything that builds the end-of-session "Needs your decision" list from the store will miss it.

**The recommendation's premise.** *"leave it at the gate - your own railway has no stretch answered 0 end to end"* (`:139-140`). What was read is the frozen copy (`test/layouts/live-snapshot`, config dated 2026-09-23 07:52): it has no answered 0 at all, 158 lengths and none 0. Answering 0 became possible that day (MT-476), and TDU-C6 was filed because Adam answers 0 on his railway ("for adjacent tracks"); ADA-A1 called his configuration "a plausible one for his railway". Nothing in the range read `cs2_sample_layout`, so the sentence states as a fact about his railway something read off a copy frozen before he could answer 0.

**A row that now misstates the reach.** TDU-C6's store row still reads *"`Edge.isMeasured` at the Atomic Routes gate, the release escape and the route in"* - the route in was put back (ADA-A1). With the document deleted, the row is what a query returns.

**What mitigates it.** The question is at the top of the Length section in prose, and the narrow reading is the safe one (ADD2-D1). Nothing on the railway.

**Fix.** An "Open - Adam's decision" row (or ADA-A1's status) for the reach; the recommendation to say "the frozen copy of your railway" or to be checked against his setup; a status note on TDU-C6's row naming ADA-A1.

**Verification request (for the main session, which may read his setup).** In `cs2_sample_layout/config/autonomy/setup.json`, find any edge of the built railway whose every square is in the answered list. **Proves the premise:** none. **Refutes it:** any, at a station autonomy may choose.

---

### ADD2-C3 - Two Inbox receipts the round left behind: OB-295 still says there is nothing to run by hand, and OB-297 cites a test that no longer exists

| | |
|---|---|
| **Disposition** | Fixed - OB-295 became MT-584; OB-297 names the tests that hold it and became MT-571, whose comment describes the note (the round's records commit). |

**OB-295** (`issues.md:1304`): *"The frozen copy of your railway has no such restriction, so there is nothing to run by hand."* State **fixed unvalidated**, Became **-**. The round wrote MT-584 for exactly this (ADD-C11), and the README's rule (`docs/manual-tests/README.md:130-136`) is that a receipt an `MT-###` tests names it in Became and leaves State empty - "a State typed beside a Became, or a 'fixed unvalidated' that nobody revisits after its test passes, is how 41 receipts came to say 'fixed unvalidated'". OB-296's row (`- | MT-581`) is the shape. So OB-295 will sit at fixed unvalidated after Adam validates MT-584, and its text contradicts the entry.

**OB-297** (`issues.md:1302`): *"the build marking the switches and crossings on each stretch: `core.testTheOwnTailArithmetic.testTheNoteCountsPiecesCutAtTheSwitches`"*. `2c7a5b47` renamed that test to `testTheNoteCountsWhatMassAssignAsksFor`, and `1098c9b8` removed the cut marks (the build now marks what Mass Assign asks for, `toMeasure`/`lengthsAsked`). A grep of `src/`, `test/` and `docs/` finds the old name only here. The receipt names no MT either, though MT-571 now carries the note's change.

**So ADD-C11's disposition is partly true**: MT-584 was written, but the receipt it was written for still says there is nothing to run.

**What mitigates it.** Records only.

**Fix.** OB-295 to `- | MT-584`; OB-297's test name and wording, and Became MT-571 if that entry is the one that shows it.

---

### ADD2-C4 - behaviour.md's new deadlock paragraph gives a remedy its own section rules out, and states a planner outcome nothing pins

| | |
|---|---|
| **Disposition** | Fixed in the record - behaviour.md says neither train is sent, by any door, until one is moved without TrainControl's routing or a restriction is cleared, and no longer names the planner's outcome; the isPathClear comment says the same (86b458c8, the round's records commit).  Which of the planner's refusals a deadlock gives is not a promise the document makes now, so nothing pins it. |

**The sentence** (`behaviour.md:74-78`, ADA-C3): *"neither moves until one is driven off by hand - Return Home answers NO_PLAN_FOUND there"*.

**"Driven off by hand".** In this document's vocabulary a hand-driven send is a TrainControl send, and a few paragraphs up (`:53-55`) the same section says *"autonomy, a hand-driven send and Return Home all obey it"*. `Layout.isPathClear` asks `heldBackAlong` for every tier, with only the watched square's own occupant exempt, so in the deadlock the hand send from each train is refused by the square the other holds back, exactly as autonomy is. The bundle uses the same phrase as a working remedy for a barred facing (*"drive it off by hand"*), which makes the misreading likely. What actually breaks it: drive one train away on the throttle and put it where it now is, take one train off the diagram, or clear one restriction for the move.

**"NO_PLAN_FOUND".** ADA-C3 was disposed of "in the record", with no claim. `HomeStaging` has `IMPOSSIBLE` ("cannot reach its home at all") as well as `NO_PLAN_FOUND`, and which one a deadlock produces depends on whether the reachability pass sees the held squares. Nothing tests the deadlock at all.

**And nothing user-facing mentions the cost.** The changelog's only line on the setting (`Readme.md:392`) is about clicking the square, and `Automation.md` never describes the restriction, not even in "why isn't it moving". An operator upgrading from 2.8.1 with a restriction on a square routes pass through will see those routes wait while the watched square is occupied. Whether that belongs in a non-technical changelog is Adam's call; it is recorded here because nothing else records it.

**What mitigates it.** It needs two restrictions set against each other; the frozen railway has none (`blockedPoints` is `{}`), and MT-443 said his setup had none on 2026-09-24.

**Verification request.** ADA-C3's fixture in `regression.testStationBlockedByAnotherPoint`: W1 and W2, one exit each, W1's through S2 and W2's through S1; S1 blockedBy W1, S2 blockedBy W2; a train on each W, each with a home past the other's held square.
- (a) The hand door's question, `isPathClear` on each exit as the right-click send asks it. **Proves the remedy false:** both refused.
- (b) `HomeStaging` for both trains. **Proves the sentence:** `NO_PLAN_FOUND`. **Refutes it:** `IMPOSSIBLE` or a plan.

---

### ADD2-C5 - ADD-C7's second half is dispositioned with a different mutation: the refusing figure's stop at a turn is still claimed by nothing

| | |
|---|---|
| **Disposition** | Fixed - the claim's turn case (d514de19) turns at a copy that is no station, and mutation W1 - the line ADD-C7 named, deleted - is red. |

**What ADD-C7 asked.** *"Delete the `reversing` line in `refusingFigures`. Proves it is held: some claim goes red. Refutes it: all stay green, and the turning half of TDA-C10 is unclaimed."* The line it meant is still there: `AutonomySession.java:10046`, `if (point.optBoolean("reversing", false)) setsOff.add(...)` - the clause that stops the figure's walk back at a copy trains turn at, as `measuredRouteIn` stops at an `isReversing()` end.

**What the disposition cites.** *"V9 turns the turn branch's claim red."* V9 (`mut_v1.json`) changes `:10085`, the notice's new filter `!turns.contains(edge.getString("start"))`, introduced by `1098c9b8` - not the `setsOff` line.

**Why the named line would survive.** Every fixture that reads a figure has its turning square a station as well: "Turns" in `testAutonomyDiagramSession` (station, `canReverse`), "Start" in `testTheRefusingFigureCountsBackOverASensor` (station, `canReverse`), and TopR1ParkShort in the pin (a station). A station's copies, turning twins included, are `station:true` (ADU-C1: the right-click Place's station-only list held the turning copy), so they are in `setsOff` by the station line and deleting the reversing line changes no figure a claim reads.

**Where it matters on his railway.** A copy that turns and is no station is exactly the build's barred turning copy (`station:false, reversing:true`, ADA-D3) - RampDown (barred S) and BottomMainPost (barred N) are both `canReverse` with a barred side. A train that comes in there, turns and goes on to a platform has a route in that stops at the turn; without the clause the figure walks back past it and gives a larger number, and "a train longer than {4} is refused" becomes false.

**What mitigates it.** The clause is right today; this is a claim gap. A notice, not a refusal.

**Verification request.** Mutation: delete `:10046`; run `core.testAutonomyDiagramSession` and `core.testAnAnsweredZeroIsNotMissing`. **Proves the gap:** both green. **Strengthening:** a fixture with a platform behind a switch whose way in starts at a barred turning copy of a `canReverse` square (station:false, reversing:true), measured behind it as well; the figure must be the leg from the turn only.

---

### ADD2-C6 - A round-1 claim was amended in the fix commit, so the red recorded for it was its fixture's

| | |
|---|---|
| **Disposition** | Fixed in the record - this round's one claim amendment was committed apart, before the fix (a377fc5c), and ADU2-C6's test, written after its fix, says so in its commit.  Round 1's is recorded here: `testTheRefusingFigureCountsBackOverASensor` gained its dead end in 1098c9b8, and mutation V7 is the evidence that fix is claimed. |

**The change.** `testTheRefusingFigureCountsBackOverASensor` (ADD-C6, ADA-C4) was committed red in `2c7a5b47`. `1098c9b8` - the fix - added to its fixture (`testAutonomyDiagramSession.java:3756-3758`): *"A DEAD END TRAINS TURN AT: otherwise a train that came in there can never leave towards the platform, and the build has no way in from it at all."*

**What that means for "red first".** Without that line there is no way in from Start at all, so the claim as committed was red on the unfixed code and would have stayed red on the fixed code: its red was the fixture's, not the finding's. By reading, the amended claim is red on `1098c9b8^` for the right reason (the old code read only a leg straight from a station or turning copy, and the leg into the platform starts at the sensor at 3,1), and V7 at HEAD shows it catches "the last leg only" - so the fix is claimed. What is wrong is the record: nothing says a claim was edited in the fix commit, which is the one edit the claims-first rule exists to make visible.

**Related, and disclosed:** several round-1 claims call signatures the fix introduced (`copyToPlaceOn` with five arguments, `setPiecesToMeasure`, `pieceToMeasure`), so their red on `2c7a5b47` was a missing method. The dispositions lean on the mutation run for these (V2 to V13), which is the right evidence; ADU-C1's says outright that V3 survived the first claim.

**What mitigates it.** The mutation evidence exists; nothing on the railway.

**Verification request.** Run the amended `testTheRefusingFigureCountsBackOverASensor` against `1098c9b8^`'s `AutonomySession.java`. **Proves the claim:** red at `getThird() == 5` with the notice present. **Refutes it:** red at the precondition, or green.

---

### ADD2-C7 - The refusing figure: "never less than {3}" is false for a half-measured way in, and a wholly unmeasured way in hides the platform's figure

| | |
|---|---|
| **Disposition** | Fixed as ADA2-C1 and ADA2-C2. |

**The sentence** (`behaviour.md:1522`, the round's records commit): *"Only for the notice's own ways in, over a switch or from a turn, so it is never less than {3}."*

**Why it is not.** `{3}` (`worst` in `runInFigures`) skips a way in whose room past the switch is unmeasured (`:9982`, `room <= 0`). `refusingFigures` does not: it takes every leg carrying `roomAtTheEnd`, and the builder writes that key for every leg crossing a switch, -1 included (`AutonomyBuilder.java:1336`). Take a platform (max 6) with two ways in over switches: A measures 3 past its switch; B has nothing measured past its switch and 2 before it, from a station. Then `{3}` = 3 and `{4}` = 2. The figure is true of the railway - the room rule bounds B by the leg's own length (`Layout.java:10952`) and FR-087's route in is 2, so a 3-unit train is refused coming in by B - but the notice reads "a train longer than 3 stands across that switch ... a train longer than 2 is refused instead", and the document's bound is false.

**Narrower than his words.** A way in over a switch with no length at all gives 0 (`measuredWayIn`, `:10132`), `Math::min` takes it (`:10095`), and `runInFigures` then drops the figure (`refusedAbove > 0`, `:9993`). So a platform with one measured way in that refuses and one wholly unmeasured way in gives no figure, though "where there is one" there is one. The unmeasured way refuses nothing (the room rule does not judge it), so it should simply not take part.

**What mitigates it.** A notice. The frozen railway gives the figures the documents name. Both cases need two ways in, one of them half or wholly unmeasured.

**Verification request.** A variant of `platformBehindASwitch` with a second station on the switch's branch.
- Branch measured 2 before the switch and nothing after, main way 3 past the switch, max 6. **Proves the bound false:** `getDetail() == 3` and `getThird() == 2`.
- Branch with nothing measured at all. **Proves the suppression:** no `checkRunInShorterThanThePlatformRefused` finding, where the main way alone gives one.

---

### ADD2-C8 - MT-583 asks Adam to check five figures read off the frozen railway; one is pinned, and the validated MT-564 still says the opposite about LowerFront

| | |
|---|---|
| **Disposition** | Fixed - all five figures pinned against the railway's own refusal on the frozen copy, over every route between the squares rather than one shuffled search (b152d025); mutations W3, W8, W9 red.  A comment on MT-564: LowerFront refuses a train of 5 from ParkingTrack12 (the round's records commit). |

**What MT-583 and behaviour.md assert** (`tests.md:28524`, `behaviour.md:1522-1524`): TopMainR1Inter 3, LowerFront 4 (from ParkingTrack12), and no figure at Tunnel, BottomMainA or BottomInnerOtherside because every way in measures at least the maximum (5, 5, 3 on the frozen railway).

**What holds them.** `testTheRefusingFigureIsTheRailwaysOwn` (`b6835920`) ties TopMainR1Inter's figure to `whyTooLongForThisRoute`. Nothing holds the other four: LowerFront's 4 and the three absences were measured once in the session (ADA-C4's disposition) and live only in prose. They are the part a later change to `refusingFigures` could move silently - C7's suppression, or C5's clause, would change exactly these.

**The contradiction.** MT-564 (fixed validated) records Adam's railway census: *"at each the railway admits the longer train standing across the switch, as the notice says. One exception ... TopMainR1Inter ... Nothing else found"* (`tests.md:27947`). LowerFront now refuses a 5 from ParkingTrack12 by the round's own measure. ADD-C2's disposition explains it (the census tried only a train one unit longer than the room), but that explanation is in ADD.md and the store, and the tracker entry Adam reads still says nothing else was found.

**The pin's path.** It finds its route with `layout.bfs(start, end, new ArrayList<>())`, and `bfs` shuffles its neighbours. It passes only if some start/end copy pair returns a route whose route in equals the figure; with two shortest routes of different route in, it would fail on some runs. The README asks randomised tests for fixed seeds and a floor.

**What mitigates it.** A notice; TopMainR1Inter, Adam's own case, is pinned.

**Fix.** Pin LowerFront (from ParkingTrack12) and the three absences on the live snapshot; a comment on MT-564 reconciling its "Nothing else found".

**Verification request.** Run `testTheRefusingFigureIsTheRailwaysOwn` twenty times. **Proves it stable:** green every time.

---

### ADD2-C9 - MT-581's round-1 comment adds a second outcome to a one-outcome entry

| | |
|---|---|
| **Disposition** | Fixed - MT-585 for BottomMainA, placed from BottomMainB after turning it west; MT-581's comment withdraws its extra step and points there (the round's records commit). |

`tests.md:28473`: *"Repeat steps 1 to 3 at BottomMainA with 75 407 DB facing west ... It stays facing west, on the copy trains may not arrive at, and Why not Moving? says it cannot be started there facing that way."*

MT-581's Expected is BottomMainB keeping both headings. BottomMainA keeping a barred heading is a different outcome that can be judged apart, from a different code path (the departable list and the barred move, not the heading rule over station copies). The README's rule (`docs/manual-tests/README.md:57-70`) is one outcome per entry - "a bundle has one disposition, so a verdict on any item is a verdict on all of them" - and "if it needs to be done differently, write a new entry and reference the old tag". ADU-B1's sketch asked for a comment *moving* the square; the comment adds it. The steps are also not quite runnable as written: step 1 has Adam note the heading, not set it, and 75 407 DB's home on the frozen railway is BottomMainA itself, where Place is not offered while it stands there.

**What mitigates it.** The check itself is right (ADD2-D2). Adam's consolidation question pulls the other way, and a same-sitting note on a new entry would answer both.

**Fix.** A new entry for BottomMainA (placed from another square, turned west first), same sitting as MT-581; a comment on MT-581 pointing to it.

---

### ADD2-C10 - Dispositions that read "Fixed" where part was left, and three status lines the store contradicts

| | |
|---|---|
| **Disposition** | Fixed - `heldBackBy`'s @return, `blockingOccupantOf`'s parameter and FR-087's javadoc (86b458c8); the menu's reads (ADU2-C5); the three round-1 documents' status lines are set at the round's close. |

- **ADD-C8** ("heldBackBy's parameters"): `Point.java:842` still says *"or null when the destination is free"*, the `@return` ADD-C8 quoted. The disposition's other claim - *"FR-087's javadoc now says the route in stops at a 0"* - is not what happened: the records commit touched no source, and `theApproachItselfHoldsIt`'s javadoc is unchanged (*"An unmeasured leg ends the count"*). It is true again because `measuredRouteIn` was put back, and it now says "unmeasured" of a leg `Edge.isMeasured` calls measured.
- **ADA-C6** (named `Layout.blockingOccupantOf`'s parameter): `Layout.java:5581` still reads *"@param destination the station being arrived at"*.
- **ADD-C10 / ADU-C3**: a healthy right-click asks the setup once now. Over a broken setup `LayoutRightclickAutonomyMenu.java:464` still asks `whyAHandSendIsRefused()` after Start's tooltip has asked `whyAutonomyWillNotStart()`, so `autonomyHasErrors` and `autonomyErrorCount` each walk the railway twice, and the LD-C6 comment's *"Asked ONCE each"* (`:413`) is still false there. The commit's "works the hand doors' sentence out from Start's answer" is true of the healthy case only.
- **Status lines.** `ADA.md:5` reads *"Open: ADA-A1, ADA-C1, ADA-C2, ADA-C3, ADA-C4, ADA-C5, ADA-C6"*; all seven are Closed in the store and in its own tables. ADD.md and ADU.md read `open` with no id named, and every row of both is Closed. FANOUT sets these at the round's close, but the Open list names ids that are not.

**What mitigates it.** Text, and the latency only over a broken setup.

---

### ADD2-C11 - Small text the round wrote or left

| | |
|---|---|
| **Disposition** | Fixed - Automation.md (ADA2-C7), the berth warning (ADU2-C3), and behaviour.md's *a train facing the barred way* (the round's records commit). |

- **`Automation.md:166`** now says the message *"says how many pieces - the same ones Mass Assign Lengths asks you for"*. The message says *"{0} stretch(es) of track on the way round have no length at all"* (`messages.properties:171`), and what it counts now includes single switches and crossings. A reader matching the guide to the sentence finds a different noun; either the guide says "stretches" or the sentence changes (UI lane).
- **`checkBerthTrackGivenNoLength`** (UI lane, all eight bundles): ADU-C8 qualified the end - *"every train with a length arriving that way is refused"* - and left the opening, *"{0} can take no train that comes in over the track behind it"*, which is the same over-statement.
- **`behaviour.md:702`**: *"turned a train round at every square with a barred side"* - only a train facing the barred way.

---

### ADD2-D1 - TDU-C6's narrow reading holds against his words and the ruling it rests on; ADD-B1's disposition is true

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **The code.** `Layout.measuredRouteIn` at HEAD is byte for byte `45cfa410`'s (`if (leg <= 0) break;`). `Edge.isMeasured` has two callers, the gate (`Layout.java:9604`) and the escape (`:4967`). The berth rule reads answered and unanswered squares alike (`:10285` only counts them for the message), and the own-tail note now takes Mass Assign's marks. So "and nothing else does" (`behaviour.md:1022`) and "here, and only here" (`:1653`) are true, and 5b and 5d state one rule. The javadocs in `Edge`, `GraphReducer`, `AutonomySession` (`:3610`, `:3664`, `:8636`) and `AutonomyEditorPanel` say the same.
- **Against his words.** TDU-C6 asked about one refusal and offered two remedies for the Atomic Routes gate; he answered with its consequence: *"so non-atomic should be allowed."* The narrow reading gives exactly that, and moves the escape with the gate so the gate cannot pass a railway the escape then releases under a train. Over an answered-0 edge the escape does not fire, so nothing is released early.
- **Against 2026-09-23.** OB-274's own words are *"same meaning to the model"*, and Adam confirmed a stretch answered 0 is still not judged. The narrow reading keeps that for every length rule. The middle reading of round 1 (route in only) was the unsafe one (ADA-A1); the wide one needs the tail walk and the tail question to walk over the 0 as well, which is a change to what a standing train blocks, and is rightly put to him (ADD2-C2 is only about how it is recorded).
- **The claim.** `testTrackAnsweredZeroIsMeasuredTrack` now has its own javadoc and MUTATION line above the other test's, and its route-in assertion uses the [measured 3, answered 0, measured 2] shape where the rule acts; V1 (count on past an answered 0) makes it 5 against 2.

---

### ADD2-D2 - OB-296 at the right-click door: ADD-B2's and ADD-C5's dispositions are true

| | |
|---|---|
| **Disposition** | Checked - clean. |

- `copyToPlaceOn` now takes the heading from `facingAfterAPaste(session.departableFacingsFor(...))` and the copy from `copyFacing`, the paste's two halves, and `placeFacing` moves with `moveLocomotive(locName, pointName, false, true)`. behaviour.md's Place paragraph (`:698-702`) and the barred-side paragraph (`:2003`, "a placement keeps it where the train can leave that way") are now true of all four doors.
- The claim runs on his railway at BottomMainA (precondition: the westbound copy is no station) and, since `b6835920`, at every station each way with a floor (`discriminating > 0`); `testTheRefusalsAreAskedAtTheDoors` lists the fourth door in both its guards.
- ADD-C5: the door's order is asserted (`keep = ` before `facingOf(`, the exact call `copyToPlaceOn(session, station, running, keep, usable)` after it, `placeFacing(` after that), so passing null or reading after the move goes red.
- MT-581's BottomMainA check expects what the code does; Why not Moving?'s sentence for it is `autolayout.why.startFacingBarred`. Its shape is ADD2-C9.

---

### ADD2-D3 - ADD-C1, ADD-C2, ADD-C3 and ADD-C4: the dispositions are true

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **ADD-C1.** MT-580's comment names both sentences correctly against `whyAutonomyWillNotStart` and `whyAHandSendIsRefused`. Its sibling is ADD2-C1.
- **ADD-C2.** MT-559 is back at fixed unvalidated with a dated comment giving the reason, as `docs/manual-tests/README.md:353-355` asks; the ledger lists it.
- **ADD-C3.** `Automation.md:166` no longer says a stretch measured only at its switch is missed; the count is Mass Assign's (the noun is ADD2-C11).
- **ADD-C4.** `testTheBuildKeepsTheFacingTheSetupRecords` has its AUT2-A1 javadoc back; `testARailwayCountsItsUnmeasuredDrivableTrack` has its own again; the new claim sits above it with its own. A scan of the range's `test/` hunks finds no other member inserted under a javadoc.

---

### ADD2-D4 - ADD-C6, ADD-C7's first half and ADD-C9: true in substance

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **ADD-C6.** `refusingFigures` walks back over the built railway through sensors nobody is started at, stops at a leg with no length and at a station or turning copy, and takes the least; a `...Pre` sensor no longer hides a figure. Claimed by `testTheRefusingFigureCountsBackOverASensor` (V7) and tied to the runtime by the pin (V15). Residue: ADD2-C5, C7 and C8.
- **ADD-C7, the planner half.** V14 deletes only `HomeStaging.java:1299`; with the runtime fixed, the claim's first assertion (`isPathClear` refuses the route through HS B) holds, so the planner's own assertion is the one reached. The reading is sound; I did not see the run.
- **ADD-C9.** `AutonomySession.rebuild` now reduces a fresh reducer and publishes it after (`:447-451`), the field is volatile, and there is no other `.reduce()` call in `src/`. `builder()` hands one reducer object to `AutonomyBuilder`, whose lists never change after publication, so the edges-then-locks read cannot mix two railways. The claim is a source-order guard (V10).

---

### ADD2-D5 - The records: counts, ledger and store

| | |
|---|---|
| **Disposition** | Checked - clean. |

- The store has 4,385 rows for 4,028 refs; behaviour.md and open-questions.md say so, and 3,726 + 336 + 13 + 258 + 52 = 4,385. The 52 new rows (ADA.md 15, ADD.md 19, ADU.md 18) are all Closed; `findings.tsv` gained 52 rows.
- `tests.md`: 453 fixed validated (421 + 32 with a trailing space), 78 superseded, 51 fixed unvalidated, 2 needs test - 584 entries, and "531 of 584" is right. The ledger has 53 rows, MT-559 and MT-584 among them.
- The 2026-09-24 round: `000c81c8` removed 15 documents and README says so without changing the earlier counts.
- The changes to `tests.md` are a disposition line, comments appended under existing entries, and one new entry at the bottom; no instruction was edited.

---

### ADD2-D6 - The re-blessed configuration is what the commit says, and it cleared a red the decisions round closed on

| | |
|---|---|
| **Disposition** | Checked - clean. |

- The `test/baseline/configuration.json` diff is exactly: 885 `"length": 0` lines gaining a comma, 885 `"toMeasure"` lines (640 `piece N`, 245 `square ...`), each on a place of length 0, 101 `"lengthsAsked": true` and 24 `"answered": true`. No length changed and no line was removed.
- The 24 `answered` lines fall on 8 squares, and every one is a route tile (`fahrstrasse` in the baseline's `1 - Main.cs2` and `2 - Bottom.cs2`), as f17f5a5c's TDU-C6 change made them.
- Those route-tile marks, like the `cut` marks, came from `f17f5a5c` and were never blessed then. The session's battery `battery-0925a`, started at `d05a6356`, has `regression.testConfirmedGoodState` failing at line 141 (now: `"cut": true`), so the decisions round was closed with its baseline red. `1098c9b8`'s re-bless is where that was settled, and its message names every kind of line it added.

---

### ADD2-D7 - OB-295's "sent to" and TDD5-C1's "follow" still hold; MT-584's steps match the editor

| | |
|---|---|
| **Disposition** | Checked - clean. |

- Neither reading was changed in the range; ADD-D2 and ADD-D3 of round 1 still stand. OB-295's cost is now written down (its wording is ADD2-C4).
- **MT-584.** The steps use the words of the validated MT-554 (Advanced Parameters, Unavailable While Occupied, tick, OK). TunnelLeftPark is a station, so it is in the list. BottomMainAPre is a sensor square, so it has Advanced Parameters. The Expected quotes `autolayout.errorDestinationBlockedByPoint` exactly (*"{0} is not available while {1} is occupied."*), with the held square as {0}. Step 5 has a control, and step 1 backs up the folder the edit writes. In the old graph the ways into BottomMainA are through BottomMainAPre or from TunnelLongPark, a berth trains must turn at (`mustReverse`), which by OB-229 a route may end at but not pass through.
- **The premise.** The disposition says the route from Tunnel was checked. The round's probe stood the sent train on the first station it found that reaches BottomMainA only through BottomMainAPre - not necessarily Tunnel.

**Verification request.** On the live snapshot, set blockedBy on BottomMainAPre's copies to TunnelLeftPark, stand a train on TunnelLeftPark and 75 407 DB on Tunnel's copy facing BottomMainA, then ask `explainDestinations(75 407 DB)` and the hand door's list.
- **Proves MT-584:** no copy of BottomMainA is offered, and the reason names BottomMainAPre and TunnelLeftPark.
- **Take the train off TunnelLeftPark:** BottomMainA is offered, or another reason is named.

---

### ADD2-D8 - The round's other claims test their findings' own cases

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **ADA-C1.** `testMassAssignLengths.testTheBuildMarksWhatMassAssignAsksFor` asserts the route tile unmarked and the station's share leaving nothing marked, from Mass Assign's own list as precondition. It was red on the unfixed code because no mark existed. `testTheNoteCountsWhatMassAssignAsksFor` catches once-per-place counting (V6). The live-snapshot twin compares the marks with `squaresNeedingALength()`, which is the editor's list by definition.
- **ADU-C3, C4, C6 and ADA-C2.**
  - `testTheMenuAsksTheSetupOnceWhereStartIsOffered` and `testTheSessionPublishesAReducerOnlyOnceReduced` are source guards on the exact lines (V11, V10).
  - The TDU2-C3 claim now waits for the triage and asserts Return Home offered first (V13), and it compares the wrapped tooltip with its tags stripped (V12).
  - Since `1098c9b8` that claim hands the item its sentence rather than building the menu, so the menu's `canStart ? null : ...` is held by the source guard alone. That is adequate for a one-line ternary.
- **ADU-C8.** Checked in English only, but all eight bundles carry the clause (read in the `1098c9b8` diff).
- **Bookkeeping.** No new test class. The classes that use the live snapshot are in its README's Used-by list. Every new claim has a javadoc with a MUTATION line.
