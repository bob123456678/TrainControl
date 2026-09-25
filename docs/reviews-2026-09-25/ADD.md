# ADD - Documents, tracker and tests lane, round 1: Adam's answers of 2026-09-24 as carried out

**Status:** open

**Prefix:** ADD

**Reviewed:** branch `autonomy-diagram-r0` at `d05a6356` (HEAD, 2026-09-25). The range is `45cfa410..d05a6356`: `7dc22256` (the claims), `f17f5a5c` (the fixes), `ac4c7567` (TDA-C10 re-read off the built railway), `e269aaec` (behaviour.md, open-questions.md, the store, the tracker) and `d05a6356` (the status lines).

**Method.** I read `docs/reviews/FANOUT.md`, `docs/reviews/README.md` and `docs/manual-tests/README.md` first. Then I read every commit in the range as a diff with `git show`. The ten findings' full text and dispositions come from `git show d05a6356:docs/reviews-2026-09-24/<LANE>.md`.

I checked the records against their sources:
- The store: I queried `triage.db` read-only through `sqlite3` with `mode=ro`. That gave 4,333 rows for 3,976 findings, and all 258 rows of the round Closed. I compared the ten rows' dispositions with the documents.
- `tests.md`: I recounted dispositions per entry (454 fixed validated, 78 superseded, 49 fixed unvalidated, 2 needs test, 583 in all) and counted the ledger (51 rows).
- The claims about the frozen railway: I read `test/layouts/live-snapshot/config/autonomy/setup.json` and `configuration-Main.json` with inline `python -c` (barred arrivals, `blockedPoints`, tile lengths, maxima, point names).

For every behaviour.md and open-questions.md paragraph the records commit added, I read the code it describes. I also grepped `behaviour.md`, `Automation.md`, `Readme.md`, `tests.md`, `src/` and `test/` for text each change made false: test names that were renamed, the untick, "stretch", "random", "destination only" and answered zeros. For each new or edited claim I read the fixture. I asked whether it tests the answer's own case, whether its red on `7dc22256` was for the finding's reason, and whether its named MUTATION would really be caught. I read the new MT entries' steps against the menus and sentences they name: `LayoutRightclickAutonomyMenu`, `HomeLocomotiveMenu`, `TrainControlUI.whyAHandSendIsRefused` and `whyAutonomyWillNotStart`, and the English bundle.

**Nothing was executed.** No JVM, no test, no `mutate.py`. I made no git state change and created no file besides this one. I did not open `cs2_sample_layout/`. Every finding that depends on behaviour carries a verification request.

Grades are by consequence on the railway. A: wrong behaviour on the layout, or data lost. B: incorrect results in specific configurations. C: text, a narrow case, or a test that proves less than it says. D: checked and clean.

---

### ADD-B1 - Two of Adam's rulings on an answered 0 now stand side by side in behaviour.md; where the new one reaches a length rule (the route in), it admits a train whose tail the standing-tail walk does not claim

| | |
|---|---|
| **Disposition** | Fixed as ADA-A1, and behaviour.md states one rule now: section 5b's OB-274 paragraph names the one exception, and section 5d says the gate and the escape only (the round's records commit). |

**The two statements.**
- `behaviour.md:1011-1017` (OB-274, 2026-09-23), which `e269aaec` left in place: *"every length rule reads it exactly as it reads a piece nobody has measured - 5b is unchanged. Confirmed by Adam when asked the question directly: a deliberate 0 is not a measurement of nothing, and a stretch whose answers are all 0 is still not judged."*
- `AutonomyCompanionStore.java:1168-1176` says the same: *"every length rule asks `> 0`. What reads the difference is only `isTileLengthAnswered`"*.
- `behaviour.md:1642-1647` (new, TDU-C6): *"Track answered 0 on purpose is measured ... The route in (FR-087) carries on over it the same way."*

**The code now holds both, rule by rule.**
- `Edge.isMeasured` counts an answered 0 as measured. Four places ask it: the Atomic Routes gate (`Layout.java:9602`), the release escape (`:4965`), the route in (`measuredRouteIn`, `:10151`) and the own-tail piece count (`isMeasuredAt`). TDA-C10's figure asks it too (`isMeasuredOnTheRailway`).
- These still stop at `getLength() <= 0` and treat an answered 0 as unmeasured:
  - the standing-tail walk (`Layout.java:7742`, `if (segment.getLength() <= 0) break;`, before `covered.put`)
  - the room walk (`:10996`)
  - `claimUpToWhereTheRailsPart` (`:9255`)
  - the own-tail rule's "judged at all" test (`travelled > 0`)

**The consequence.**
- FR-087's `theApproachItselfHoldsIt` (`:10111-10123`) admits a train at a platform if it is no longer than `measuredRouteIn`.
- Its javadoc (`:10076-10082`) rests on two premises: *"the track under it is claimed.  An unmeasured leg ends the count, so a tail that would reach track nothing has measured is still refused"*.
- Since `f17f5a5c` the count carries on over a leg whose every place was answered 0, and adds the measured legs behind it. When that train stands, `walkOneTail` breaks at the same leg before claiming it. So the part of the train lying on the measured leg behind is claimed by nothing, and `isPathClear` can send a second train onto it.
- At `45cfa410` the route in stopped at that leg, and the same train was refused. This is a lifted rule losing its precondition. The claim `core.testAutoLayout.testTrackAnsweredZeroIsMeasuredTrack` pins the wider reading (`measuredRouteIn(...) == 3`).

**Whether the reading holds.**
- TDU-C6 asked only about the Atomic Routes refusal, and his answer names only it: *"so non-atomic should be allowed"*.
- The gate and the release escape had to move together, or the gate would let through a railway that the escape then releases under a train. That part of the reach is right.
- The route in is a wider reading. It makes a length rule more permissive, which is exactly what his 2026-09-23 confirmation said an answered 0 does not do.
- The own-tail count is text only and harmless.
- Nothing records that the reach went past the gate, or that "measured" now means two things.

**What mitigates it.**
- The frozen railway has no answered zeros: 158 tile lengths, none of them 0.
- An edge's places end with the square it arrives at, so a leg is all answered 0 only if its arrival sensor square was answered 0 too. His case, two switches back to back, does not do that by itself.
- FR-087 acts only at stations autonomy may choose, and only after the room rule has refused.

**Verification request.**
- Fixture: extend `testTrackAnsweredZeroIsMeasuredTrack`:
  - S, a station, then S→C measured 3;
  - C→X with every place answered 0, X's own square included, where X is not a station;
  - X→P measuring 2 including P, where P is a station, an auto destination, with a maximum of 5.
- A 5-unit train: `Layout.theApproachItselfHoldsIt(path, loc)` is true at HEAD and false at `45cfa410`.
- Stand it at P with that road, run `walkStandingTrains`, and read `placesCoveredByStandingTrains()`.
- **Proves it:** C:1 (on S→C) is not claimed, and `isPathClear` passes a second train over S→C.
- **Refutes it:** C:1 is claimed, or the train is refused.

**Adam's decision.** Either the route in stops at an answered 0 again (OB-274's rule for length rules, with TDU-C6 kept to the gate and the escape), or the tail walk walks on over an answered leg spending 0. Either way, behaviour.md 5b and the store's javadoc have to become one rule. B; A if the fixture shows it on a railway answered the way he answers.

---

### ADD-B2 - The right-click Place still turns a train whose heading faces a barred side, where the paste, the dialog and the editor's Place keep it; behaviour.md says it follows the same rule

| | |
|---|---|
| **Disposition** | Fixed as ADU-B1. |

**Two different candidate lists.**
- `placeSomewhereLegal` (`LayoutRightclickAutonomyMenu.java:1080`) chooses with `copyToPlaceOn` (`:1109`) from `placeableCopies()` (`:1002`). That list offers only copies that are destinations; the copies of a barred arrival side are "counted, not offered" (TWV-C6).
- The other three doors choose from `AutonomySession.departableFacingsFor` (`:1845`). Its javadoc says: *"keep its heading wherever a copy facing that way has a way out on the running layout, a copy trains may not arrive at included"* (OB-284).

**Worked case: BottomMainA** (`5:20,12`; the frozen railway bars arrivals from the east there, so the copy facing west is barred).
- A train facing west, made active and placed there with right-click Place, meets `held = {E}`.
- `facingAfterAPaste` then returns the one heading a single copy has, E, and the train is turned.
- A paste, the Place Locomotive dialog or the editor's Place keeps it facing west and says autonomy will not start it there.
- TDU-B4's own verification request named BottomMainA. MT-581 uses BottomMainB, which has no barred side, so it cannot show this. Seven squares on the frozen railway have a barred side.

**The documents.** They say otherwise:
- `behaviour.md:694-697`: *"The diagram's right-click Place follows the same rule ... where the square can hold it"*.
- `behaviour.md:1989`: *"a placement keeps it where the train can leave that way (OB-284)"*.
- TDU-B4's disposition says "Fixed" without the exception.

**Consequence and mitigation.** The consequence is TDU-B4's own: the model's heading is the opposite of the train's, so it is sent the wrong way. It is now certain at such a square instead of a coin toss. Mitigating:
- the heading is drawn on the diagram, and the Facing item turns the train;
- it happens only when the train faces the barred way;
- the same case was turned before the fix too, since `placeableCopies` is unchanged. It is a site the fix did not reach, not a regression.

**Verification request.** In a sandbox of the frozen railway, turn 75 407 DB to face west on any station and make it active. Right-click BottomMainA and choose Place.
- **Proves it:** `facingOf("75 407 DB")` reads E. Then paste the same train at BottomMainA and read W (OB-284).
- **Refutes it:** the right-click Place reads W.

**Fix.** Either make the door keep the heading on the barred copy, as the other three do, or say in behaviour.md 3 that this door turns the train there.

---

### ADD-C1 - MT-580 expects Return Home's tooltip to be "the sentence Start's greyed item shows"; over MT-573's break they are two different sentences

| | |
|---|---|
| **Disposition** | Fixed as ADU-C5. |

**The entry.** `tests.md:28408` (MT-580), Expected: *"Return Home is greyed, and its tooltip is the sentence Start's greyed item shows - the setup cannot be used yet, and how many things have to be dealt with first."* Step 4 asks Adam to hover both items, which invites exactly that comparison.

**What the two items show.**
- The Return Home item's tooltip is `ui.whyAHandSendIsRefused()` (`HomeLocomotiveMenu.java:63-80`). Its javadoc (`TrainControlUI.java:23874` ff.) says it is *"never Start's, which say AUTONOMY cannot start"*.
- Start's greyed item shows `whyAutonomyWillNotStart()` (`LayoutRightclickAutonomyMenu.java:445`).
- MT-573's break (4 - Combined's Exclude Page unticked) raises `DUPLICATE_SENSOR_PAGE`, which is an ERROR (`AutonomyChecks.java:1116`). So Start reads *"Autonomy cannot start while the setup has N error(s)..."*, and Return Home reads *"This setup cannot be used yet: N things..."*.
- MT-550, which is validated, expects exactly that difference: *"Neither message says autonomy cannot start."*

**Consequence.** The part after the dash describes Return Home's sentence correctly. The first clause is false, so a literal run is marked *Does not work* and costs a round. `behaviour.md:43-45` (*"with the setup's sentence as its tooltip, as the right-click Start beside it is"*) reads the same way, but can be taken as "greyed with a reason". The claim is right: it compares the tooltip with `whyAHandSendIsRefused`. Nothing moves on the railway.

**Fix.** A comment on MT-580 saying the two sentences differ, and which one Return Home shows.

---

### ADD-C2 - MT-559 is validated and its Expected is now false at TopMainR1Inter and LowerFront; it was not moved back or commented

| | |
|---|---|
| **Disposition** | Fixed - MT-559 moved back to fixed unvalidated with a comment, as the README asks (the round's records commit).  LowerFront's 4 was measured with the railway's own refusal: a train of 5 from ParkingTrack12 is refused and one of 4 admitted - the MT-564 deep dive tried only a train one unit longer than the room. |

**The entry.** MT-559 (`tests.md:27749`) is **fixed validated**. Expected: *"BottomMainA, Tunnel, BottomInnerOtherside, LowerFront and TopMainR1Inter each say ... may block other parts of the layout until it leaves - none of them says* is refused*."*

**What changed.** Since `f17f5a5c` and `ac4c7567`, TopMainR1Inter's and LowerFront's notices use `checkRunInShorterThanThePlatformRefused`, which adds *"...a train longer than {4} is refused instead."* MT-583 checks the new sentence, but MT-559 still reads as finished.

**The rule.** `docs/manual-tests/README.md:353-355`: *"When a fix lands that changes behaviour a validated test covered, move that test back to fixed unvalidated and say why in its Comments."* `e269aaec` commented MT-491, MT-501, MT-502 and MT-573, and not MT-559.

**The record contradicts itself about LowerFront.** MT-564's validated deep-dive comment (`tests.md:27941`) reports, on the same frozen railway (its config has not changed since 2026-09-23): *"One exception ... TopMainR1Inter ... Nothing else found."* MT-583, behaviour.md and TDA-C10's disposition now say LowerFront 4 is *"the railway's own refusal"*. Both may be true: the deep dive may have tried only a train one unit longer than the run-in. The record does not say.

**Verification request.** On the frozen sandbox, send a 5-unit train (LowerFront's maximum is 5) to LowerFront from the station copy the figure is read from.
- **Proves MT-583:** it is refused.
- **Refutes it:** it is admitted.

**Fix.** Move MT-559 back to fixed unvalidated, or supersede it by MT-583, with the comment.

---

### ADD-C3 - The user guide still says the own-tail note counts stretches from sensor to sensor, and misses one measured only at its switch

| | |
|---|---|
| **Disposition** | Fixed - Automation.md says the note counts the pieces Mass Assign Lengths asks for (the round's records commit). |

`Automation.md:166`: *"where part of it has no length the message usually says how many stretches - measure them ... (A stretch counted is the track from one sensor to the next, so one whose only length is on its switch is missed.)"*

OB-297 (`f17f5a5c`) made the note count pieces cut at switches and crossings. A leg measured only at its switch is no longer missed, and an answered 0 no longer counts. `behaviour.md:1186-1190` was updated; the guide was not touched in the range. This is the reader-facing sentence Adam's users see.

---

### ADD-C4 - Two new tests were inserted between an existing test's javadoc and its method

| | |
|---|---|
| **Disposition** | Fixed as ADU-C9 and ADA-C5. |

`7dc22256` anchored both insertions at the declaration, not above the javadoc:
- **`testATrainIsPutOnlyWhereItCanStart.java:88-92`.** `testTheBuildKeepsTheFacingTheSetupRecords`'s javadoc (*"A facing only a copy trains may not arrive at holds still stands the train on a copy facing that way (AUT2-A1)"*) is now an orphan above the new test's javadoc. The old test has none.
- **`testAutoLayout.java:636-690`.** `testTrackAnsweredZeroIsMeasuredTrack` has no javadoc of its own and has inherited `testARailwayCountsItsUnmeasuredDrivableTrack`'s. That javadoc carries VD16-T1 and eight MUTATION lines about "the first claim ... the eighth". The test it describes (`:738`) now has none.
  - Anyone deriving mutation specs from javadocs will now aim that test's catalogue at the wrong method.
  - The new test states its own mutation nowhere.

A sweep of every `*.java` hunk in the range for an added member right after an existing `*/` finds only these two. Nothing on the railway.

---

### ADD-C5 - The OB-296 claim holds the door by two substrings; ignoring the heading at the door survives it

| | |
|---|---|
| **Disposition** | Fixed - the claim asks the rule on his railway and checks the door reads the heading, hands it to the rule and only then moves (2c7a5b47). |

**The claim.** `testTheRightClickPlaceKeepsTheTrainsHeading` tests `copyToPlaceOn` well: the heading kept, the same answer 8 times, and one copy against the heading. For the door itself it only asserts that `placeSomewhereLegal`'s source has no `Random` and contains both `copyToPlaceOn(` and `facingOf(`.

**What survives it.**
- Its MUTATION line says *"ignore the heading, and this fails"*. At the door, `copyToPlaceOn(usable, facings, null)` keeps both substrings, because `facingOf(` stays on the `keep` line, so the claim stays green.
- The javadoc promises *"with the heading read before the move"*. Nothing checks the order: reading `facingOf` after `placeFacing` would pass.

The code is right today. Guard-knows-only-what-it-lists.

**Fix.** Assert that the `keep` variable is the third argument, and that `facingOf(` comes before `placeFacing(`. Or drive the door on the sandbox, as MT-581 does by hand.

---

### ADD-C6 - TDA-C10's figure is read off direct legs only, which is narrower than "where there is one", and MT-583 explains the missing figures by a property the code does not check

| | |
|---|---|
| **Disposition** | Fixed as ADA-C4 - the figure counts back over sensors nobody is started at.  MT-583's reason was measured, not assumed; its comment records the figures. |

**What the code reads.** `AutonomySession.refusingFigures` (`ac4c7567`) takes only built edges that run straight from a station or turning copy into the platform's stop copy. Its javadoc says a figure a longer way in might lower *"is left unsaid rather than guessed"*.

**What that leaves out.** On the frozen railway, eight sensors are named `...Pre` and none of them is a station: BottomMainAPre, TunnelPre, LowerFrontPre, TopMainR1Pre, and others. So on any side where a Pre sensor stands between the station behind and the platform, no figure can be given. That holds even when the whole route in from that station measures less than the maximum and the railway refuses (FR-087 counts the route in across non-station sensors). His words were *"where there is one"*; the implementation gives it where it can be read off a single leg.

**MT-583's reason.** `tests.md:28487` says Tunnel, BottomMainA and BottomInnerOtherside give no figure because *"every way into them the railway runs measures at least their maximum"*. `ac4c7567` records that measurement for Tunnel only (*"no route the railway runs into Tunnel measures under 6"*). For the other two, the absence may just be the missing direct leg.

**Mitigation.** It is text. Every refusal still has its own sentence at the send doors and in Why not Moving?. Adam's railway case, TopMainR1Inter, is covered.

**Verification request.** On the frozen sandbox, for every platform that has a run-in notice, take the smallest `Layout.measuredRouteIn` over the routes the railway runs into it from any station or turn, and compare it with the figure the notice gives.
- **Proves narrowness:** some platform's smallest route in is under its maximum and no figure is given. BottomMainA from Tunnel via BottomMainAPre is the first to try.
- **Refutes it, and makes MT-583's sentence true:** no such platform exists.

---

### ADD-C7 - Three claims were not seen red for their own reason

| | |
|---|---|
| **Disposition** | Checked by mutation - V14 (the planner's line alone, the runtime fixed) turns `testAHomeBeyondAHeldBackSquareIsNotStagedThroughIt` red at the planner's assertion, as D1 did in the decisions round; V9 turns the turn branch's claim red. |

- **`core.testHomeStaging.testAHomeBeyondAHeldBackSquareIsNotStagedThroughIt` (OB-295, the planner).**
  - Its first assertion is a precondition that `layout.isPathClear(...)` refuses the route through HS B. That is the runtime half of the same fix.
  - So on `7dc22256` it went red at the precondition, for the runtime's reason, and the planner assertion was never reached.
  - `HomeStaging` does not call `isPathClear` (`HomeStaging.java:38`), so the planner claim is a real, separate one. Its failure on unfixed planner code has simply not been seen.
- **`core.testMassAssignLengths.testNothingSpentBeforeTheSwitchIsAWarningOfItsOwn` (TDA4-C2, the switch sibling)** and **`afterATurn.getThird() == 4` in `testAutonomyDiagramSession` (TDA-C10, the turn sibling).** Both claims were written in the fix commit `f17f5a5c`, not the claims commit. The turn sibling's fixture makes "Turns" a station as well as a turn-round square. Whether it depends on `refusingFigures`' `reversing` clause at all is unknown, so a mutation dropping that clause may survive every claim.

**Verification request.**
- At HEAD, revert only `HomeStaging.java:1299`. **Proves the claim:** red at `assertNotEquals(... READY ...)`, not at the precondition.
- Delete the `reversing` line in `refusingFigures`. **Proves it is held:** some claim goes red. **Refutes it:** all stay green, and the turning half of TDA-C10 is unclaimed.

---

### ADD-C8 - Text the change left behind in comments and test javadocs

| | |
|---|---|
| **Disposition** | Fixed - 1098c9b8 (the MUTATION line, `heldBackBy`'s parameters, the refusing figure's) and the round's records commit (FR-087's javadoc now says the route in stops at a 0). |

- **`testTheTailIsPickedOnTheDiagram.java:1076`** (`testARenameInTheWaitKeepsTheAnswer`): *"MUTATION: ask for the configuration's name alone, and this fails."* Since TDD5-C1, `sameSetup` asks only the session, and there is no configuration term left to mutate.
- **`testAutonomyDiagramSession.java:3639`:** *"leave the station behind out of the walk"*. `ac4c7567` replaced the walk (`shortestMeasuredWayIn`) with a read of direct legs.
- **`Point.java:835-843`:** `heldBackBy`'s `@param destination the station being arrived at` and `@return ... null when the destination is free`. Since OB-295 the rule is asked of every square a route arrives at, stations or not. The first sentence was updated and the parameter text was not.
- **`Layout.java:10076-10082`:** FR-087's *"An unmeasured leg ends the count"* is no longer true of an answered 0. That one is ADD-B1.

Nothing on the railway.

---

### ADD-C9 - OB-298 hands the lists over one field at a time; the builder reads edges and then locks, so a rebuild between the two now drops lock edges silently where it used to throw

| | |
|---|---|
| **Disposition** | Fixed as ADA-C2. |

**How the hand-over works.** `GraphReducer.reduce` (`:394` ff.) builds a fresh reducer and assigns seven `volatile` fields one after another, edges last.

**The reader.** `AutonomyBuilder` copies `reducer.getEdges()` (`:1222`). Later, per edge, it asks `reducer.getLocks().get(edge)` (`:1354`). `ReducedEdge` has no `equals` or `hashCode`, so it is looked up by identity. If a rebuild on another thread lands between those two lines, every lookup misses and the build emits edges with no lock edges. That silently loses the lock half of "unavailable while occupied" and every derived lock. Before the change the same overlap threw `ConcurrentModificationException`, which was loud.

**The claim.** `testARebuildDoesNotChangeWhatAReaderIsWalking` is single-threaded and checks one list at a time. The receipt says *"a reader walking them finishes on the railway it began on"*, which is true of one list, not of a reader that walks two.

**Mitigation.** The overlap was seen once, in a test that edited off the event thread. Whether production can overlap a rebuild with a build is not established.

**Verification request.** Two threads on one session:
- one loops `session.rebuild()`;
- the other loops the builder's `build`, checking that every edge with locks at a quiet point still has them.

**Proves it:** a build with an edge missing its `lockEdges`. **Refutes it:** none in, say, 10,000 overlaps, or a proof that both run only on the event thread.

---

### ADD-C10 - The right-click menu now asks the whole setup check again on every right-click

| | |
|---|---|
| **Disposition** | Fixed as ADU-C3. |

**What changed.** `HomeLocomotiveMenu.addReturnHomeItem` (TDU2-C3) now calls `ui.whyAHandSendIsRefused()`. That is `autonomyHasErrors()` → `hasErrors()` → `errorCount()` → `AutonomySession.check()`, and over a broken setup it adds `autonomyErrorCount()`, which is `check()` again. It runs every time a station is right-clicked, right after Start's `canStartAutonomy()` has asked the same.

**The comment it makes false.** `LayoutRightclickAutonomyMenu.java:415-423` (LD-C6) says `check()` *"is not cached ... four full walks of the railway on the event thread, every time somebody right-clicks a station"*, and that each question is *"Asked ONCE each"*. That is no longer true. This is for the UI lane to weigh.

**Verification request.** Time a right-click on BottomMainB on the frozen railway at `45cfa410` and at HEAD. **Proves a cost:** a measurable increase on the event thread.

---

### ADD-C11 - Adam's consolidation question was answered by growth, and the one change he can see on his railway got no entry

| | |
|---|---|
| **Disposition** | Fixed - same-sitting notes on MT-580 and MT-582, and MT-584 for OB-295 on his railway (BottomMainAPre held back by TunnelLeftPark; checked on the frozen railway that the route from Tunnel into BottomMainA runs through it and the rule names it).  OB-298 has nothing to run by hand; its receipt says so. |

**The question and the answer.** He asked *"Are there any MT's that we can consolidate or clean up to avoid getting things out of hand?"*. The ledger went from 48 to 51 open entries. MT-580 to MT-583 were appended, and the only supersession, MT-503, was forced by the change itself.

**Cheap consolidations left undone.** `tests.md` rule 4 keeps one outcome per entry, so these are "same sitting" notes, not merges:
- **MT-582** checks the absence of a removed behaviour on the same import that MT-491, MT-501 and MT-502 already run (the same file, a new configuration, the same deletion). It says nothing about running them together.
- **MT-580**'s step 4 hovers Start, which is MT-263's one remaining gesture (its step 2). It says "same sitting as MT-573" but not MT-263.

**OB-295 got no entry.** The issues.md receipt says *"The frozen copy of your railway has no such restriction, so there is nothing to run by hand"*. That is true of the fixture (`blockedPoints` is `{}`), not of the feature. MT-554 and MT-555 show how he sets a restriction on TunnelCenterPark in two clicks, and OB-295 is the one change in the round that changes where trains go.

**Receipts with no way to validate.** OB-295, OB-297 and OB-298 are receipted "fixed unvalidated" with nothing that can move them to validated. The README (`docs/manual-tests/README.md:134-135`) names that as how 41 receipts went stale.

---

### ADD-D1 - The records: ten dispositions, the store, the ledger and the counts

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **The store.** The store's rows for the ten findings (TDA-C8, C9, C10, TDA2-C1, TDA4-C2, TDD-C11, TDD5-C1, TDU-B4, TDU-C6 and TDU2-C3) carry the same disposition as the documents at `d05a6356`, all Closed.
- **The round.** All 258 rows from TDA\*.md, TDU\*.md and TDD\*.md are Closed. All 15 documents read `**Status:** closed`.
- **The finding count.** 4,333 rows for 3,976 findings, as behaviour.md:2412 and open-questions.md:407 say. No rows were added, so neither figure needed to move.
- **The ledger.** `tests.md` says "532 of 583 ... 454 fixed validated and 78 superseded", which is true. The ledger lists 51 entries (49 fixed unvalidated, 2 needs test), and MT-580 to MT-583 are among them.
- **open-questions.md.** Each "Open" block that `e269aaec` emptied had exactly the questions answered, and each "Decided" bullet quotes his words as given.

---

### ADD-D2 - TDD5-C1: "follow the train" read as he meant it

| | |
|---|---|
| **Disposition** | Checked - clean. |

**The code.** `TailCrossedPrompt.sameSetup` now asks only whether the session is the same. `whereTheAnswerGoes` still requires the running copy to hold the same train, from the same side, with the road it had. The paste, the right-click Place and the dialog each write into the door's session, which is the one now active, and onto the running copy. So the answer goes where the train physically stands, and nowhere once the session is replaced.

**The other reading.** "Follow" could mean following a train that moved, but the answer is about the track behind one square, so it cannot follow a moved train. The reading holds.

**The claim and the documents.**
- `testAnAnswerFollowsTheTrainIntoAnotherConfiguration` loads a copy of the configuration that holds the same placement. It asserts both the session and the running copy's road. Its MUTATION (put the configuration term back) would go red.
- The behaviour.md 5c paragraph matches the code.
- The log line's "or the setup was reloaded" is still true of the cases that drop the answer.

---

### ADD-D3 - OB-295 / TDA-C9: "sent to THIS square" read as every square a route arrives at

| | |
|---|---|
| **Disposition** | Checked - clean. |

**Why the reading holds.** A square trains only pass is never a destination, so for TDA-C9's square "sent to" can only mean routed through it.

**The code.** Runtime, explanation and planner all ask the one expression, `Point.heldBackBy`, via `heldBackAlong` in `isPathClear` and `firstClearOrWhyNot`, and at `HomeStaging.java:1299`. The leaving train stays exempt.

**The reach.** It is wider for a station a route passes through: that station is now shut to through routes while a train stands on the watched square. behaviour.md:65-77 states that cost, and the lock half already did the same. The frozen railway has no restrictions (`blockedPoints` is `{}`), so nothing changes on it.

**The claim.** `testATrainStandingOnTheWatchedPointClosesASquareTrainsPass` has a control, the claim and the reopening. It stands the train on BK YARD TWIN, which is in the watched square's block; that is intended, because `onTheLiveBlock` asks the block. The reason check's `contains("BK YARD")` cannot tell BK YARD from its twin, which is harmless.

---

### ADD-D4 - TDD-C11, TDU2-C3, TDA-C8, TDA4-C2, OB-297 and the reading of each

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **TDD-C11.** The untick, its log key (removed from all 8 bundles) and the changelog clause are gone. The setting stays, and the answer to his parenthetical (*"isn't the setting defunct?"*) is recorded in open-questions.md and MT-582.
  - The notes on MT-491, MT-501 and MT-502 are true: each had the tick-again text.
  - Stale references to the old test's name survive only in the superseded MT-503 and MT-560, which are append-only.
- **TDU2-C3.** The item is greyed with the hand-send sentence. `refreshReturnHomeButton` and the Execute Timetable button are unchanged, so both stay live and explain. The claim compares the tooltip with `whyAHandSendIsRefused`.
- **TDA-C8.**
  - The reversal notice no longer skips a barred side, and the berth checks still do.
  - The claim's fixture measures the east side (6,1) and has no switch there, so the platform can be listed only through the barred west side.
  - The frozen railway's RampDown and BottomMainPost are measured both sides (MT-552), so no new notice is expected there.
- **TDA4-C2.** The sentence is its own key and ASCII in all 8 bundles, and the WARNING grade is asserted. The run-in notice no longer says 0.
- **OB-297.** The build marks switches and crossings; `Edge` writes and reads `cut` in JSON. `testTheNoteCountsPiecesCutAtTheSwitches` is red under both named mutations: its count goes to 1 against 2, and to 2 against 1. `testTheBuildMarksWhereAStretchIsCut` computes its expected set from the same two session methods the build uses, so it proves the marks arrive, not that they are the right squares.

---

### ADD-D5 - Test bookkeeping for the renamed class

| | |
|---|---|
| **Disposition** | Checked - clean. |

**The class.** `testALegacyImportUnticksLoadAutonomy` became `testALegacyImportLeavesLoadAutonomyAlone`. It opens no sandbox and no window.

**The registers:**
- `build.xml` names it;
- the window census moved from 62 to 61, with the reason given;
- `test/layouts/single-switch/README.md` no longer lists it as a user.

No other `src/`, `test/` or `build.xml` reference to the old name remains, besides the census comment's history.

---

### ADD-D6 - MT-581, MT-582 and MT-583's steps against the app

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **MT-581.** BottomMainB (`5:20,13`) has no barred side, and its placeable copies face both ways: the plain eastbound copy and the westbound turning copy, per `AutonomySession`'s note that the plain westbound copy is no station. The Place item appears only for an active train on a destination not already holding it, which is what the steps set up.
- **MT-582.** `docs/manual-tests/files/MT-491-autonomy-2.7.4c.json` exists.
- **MT-583.** It quotes the English `checkRunInShorterThanThePlatformRefused` exactly. It says its figures are the frozen copy's and may differ on his railway.
