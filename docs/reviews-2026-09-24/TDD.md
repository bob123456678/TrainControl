# TDD - Documents, the tracker, and the tests themselves (round 1)

**Status:** open

**Prefix:** TDD

**Reviewed:** branch `autonomy-diagram-r0` at `fc1ce476`, 2026-09-24. The range is `git log --since="2026-09-23 00:00"`, 218 commits, `12d74ad5` to `fc1ce476`. Nothing was run. No JVM, no test, no `one.sh`, `battery.sh` or `mutate.py`. `cs2_sample_layout/` was not opened.

**Method.** I read `docs/reviews/FANOUT.md` and `docs/reviews/README.md` first. Then I took the diffstat of `docs/` and `test/` over the whole range and split it at `7a961962`, the commit that closed the 2026-09-23 round. Commits before it went through four validation rounds and a mutation run. Commits after it had neither, so they got most of the time: OB-294 (`c3b8f1e7`, `121b1c3c`, `00a2fc6d`, `a5dfb64e`, `ea8e4ae4`, `c95e285b`, `f21fb8e4`), `fab81423` (MT-533, OB-235, OB-293, FR-102), OB-285 (`68759c09`), AUT2-C2 (`7665a96f`, `a6d3b476`), MT-477 (`712bff02`, `06d3c063`), MT-263 (`cbbca27d`), OB-230 (`7f453b75`, `8f55b863`), OB-288/MT-552 (`874e4b9f`, `615e6969`), OB-290 (`b461a0ae`), OB-291 (`e528ca2b`), REG2-C3/C7 and the battery fix `4c5c8702`.

For each commit I read `git show` and then the code as it stands at HEAD. That covers `Layout.isPathClear`, `walkOneTail`, `walkStandingTrains`, `whyItWouldMeetItsOwnTail` (both forms), `whyNoRouteFitsTo`, `getPossiblePaths`, HomeStaging's `firstClearRoute` and `passesTheTailsOfTrainsThatHaveNotMoved`, `GraphReducer.placesAlong`, and both manual send doors.

I read every test class those commits added or changed in `core/`, `regression/` and `ui/`. For each one I compared the javadoc MUTATION line against what its assertions can actually see.

On the documents side:
- `behaviour.md` and `open-questions.md` diffs over the range.
- The READMEs, `docs/UI-standards.md`, and the root `Readme.md` changelog.
- `tests.md` entries MT-492 to MT-572, and the `issues.md` Inbox and receipts written in the range.

Checks against real data:
- Counted the Inbox headings from the file.
- Read the `finding` table from `triage.db` read-only (`sqlite3` with `mode=ro`).
- Compared the live-snapshot README's Used-by list with the classes that open `live-snapshot`.
- Compared quoted labels with `messages.properties`.
- Resolved the git-history pointers left for the documents `827d3444` deleted.

Every finding that rests on behaviour I could not see by reading carries a verification request.

---

### TDD-A1 - A moving train's own tail claim can overwrite, and so hide, another train's tail on a shared switch square (OB-285, AUT2-C2)

| **Disposition** | Fixed - claim ce9dd7bd (red first, on a hand-built switch shared by two legs; the frozen railway's order was benign), fix e501b56d: isPathClear walks every train but the one routed, Return Home keeps each train's claims apart.  Mutations A1a, A1b red. |
|---|---|

**Graded by what it would cost if it is real: a train cleared over another train's tail.** Reachability is not proven. It needs the verification request below. If that shows no real arrangement puts two claims on one square with the unlucky order, this is a C (a comment claiming more than the map can hold).

OB-285 (`68759c09`) stopped the mover's own tail being taken as the answer. `Layout.isPathClear` (Layout.java ~2690) now does this:

```java
if (lyingAcross != null && lyingAcross.equals(loc)) lyingAcross = null;
...
if (lyingAcross == null) lyingAcross = anotherTailOn(e, loc, coveredPlaces);
```

That only works if another train's claim is still in the map to be found. The maps cannot hold two trains on one place.

`walkStandingTrains` fills `Map<Edge, Locomotive> covered` and `Map<String, Locomotive> places` for every standing train, the mover included, iterating `this.points.values()`. `this.points` is a `HashMap` (Layout.java:836). Every claim is a plain `put`, so the last train walked wins:
- `places.put(own, loc)` at Layout.java:7618
- `places.put(ids.get(at), loc)` at :7703
- `places.put(place, loc)` in `claimUpToWhereTheRailsPart` at :9210
- `covered.put(segment, loc)` at :7658

Here is how the case arises. Two trains' claims share a square. The mover's walk runs after the other train's. The mover's route runs over that square. Then:
- the direct lookup returns the mover and is set aside;
- `tailLiesOn(e, onShared, coveredPlaces)` finds none of the other train's places on `e`;
- `anotherTailOn` finds only the mover.

The route is cleared.

Shared squares are not exotic. The walk claims "up to and including the switch" where roads part (MT-477, `06d3c063`). It also claims a place "before its length is spent", so a tail that just reaches a switch claims it. So a mover whose tail just reaches switch S and a parked train lying back across S both claim S. A mover that then leaves over its own tail through S (turned on the throttle, by the Facing menu, or at a may-turn square) is exactly OB-285's case.

The same happens with OB-294's loop case: the own-tail rule passes a short train, and another train's claim on the square it comes back to is hidden.

The planner has the same hole. `passesTheTailsOfTrainsThatHaveNotMoved` reads `coveredAtStart` and `placesCoveredAtStart`, which are the same single-valued walk. `Layout.tailsOn` (javadoc at Layout.java:7884) promises "EVERY train, other than the one being routed, whose tail lies over metal this edge runs on", but it reads one owner per place. `testHomeStaging.testAnEdgeTwoTailsLieAcrossWaitsForBoth` puts its two tails on different places (`TT:k1`, `TT:k2`), so it never meets the shared-place case. behaviour.md 5c ("track two tails lay across stays shut until both trains have moved") is true only for tails on different squares.

**What mitigates it:**
- `testATurnedTrainIsNotSentIntoAnotherTail` asserts `parkedTailLiesOnTheWayOut()` as a precondition, so its own case cannot pass by this route. The turned train there is one unit long and does not reach column 7.
- The grey is drawn from the same map, but a grey square looks the same whoever owns it, so the operator still sees the square as taken.

**Verification request.**
- Fixture: extend `core.testATurnedTrainIsNotSentIntoAnotherTail`.
- Setup: lengthen the turned train at Tunnel (southbound), or clear the lengths on its way south, until `Layout.bodyOfATrainAt(atTunnel, turned, null)` (package-private, so by reflection) contains a place that the parked train's own body also contains. The column-7 switch square is the likely one.
- Assert: `isPathClear(Arrays.asList(wayOut), turned, false)` is false.
- Order: HashMap order decides who wins, so also run a hand-built fixture where the mover's Point name iterates after the other's. Two Points whose claims meet on one switch place, the mover turned and leaving over it.
- Proves the finding: `isPathClear` true with the parked train's claim masked.
- Refutes it: refused in both orders.

---

### TDD-B1 - The own-tail rule reads a loop with no lengths as zero long, so on a partly measured railway it refuses any return over a measured body (OB-294)

| **Disposition** | Fixed - as TDA-B1. |
|---|---|

`Layout.whyItWouldMeetItsOwnTail(path, loc, reach)` (Layout.java:10351) seeds each body place with `free = -reach`, where `reach` is the measured body in front of that place (:10387). The head's `travelled` then adds only measured spans. At a return:

```java
int wayRound = travelled - free;
if (wayRound > 0 && length > wayRound) return I18n.f("autolayout.errorWouldMeetItsOwnTail", ...);
```

Take a route that comes back to a body place over a loop with no measured squares. Then `travelled` is 0 and `wayRound == reach`. `reach` is always less than the train's length for a place on its body. So the train is refused whenever there is measured body in front of that place, however long the unmeasured loop really is. The sentence then tells the operator the route "comes back to track the train is still lying on after only {reach} units".

The javadoc (:10334) and the tracker both say otherwise:
- The javadoc: "a return with nothing measured on the way round is not judged".
- MT-564's deep-dive comment to Adam (tests.md:27897): "today's own-tail rule (OB-294): where nothing is measured, nothing is judged."

Both are true only when the body is unmeasured too. The javadoc's next sentence ("Unmeasured squares within a measured way round make it shorter than it is, which refuses rather than permits") covers this if the body counts as part of the way round, but nobody reading MT-564 would take it that way.

This is the same shape as the half-measured berth approach, which got a notice of its own (`checkHalfMeasuredApproach`). The own-tail rule has none. Adam's standing preference ("only apply if lengths are specified"; a check that over-refuses is worse than none) makes this his call.

**Cost:** routes refused on railways that measure berths before loops. On his fully measured layout, only squares answered 0 on a loop would do it.

**Verification request.**
- Fixture: a hand-built layout in `core.testATrainDoesNotRunIntoItsOwnTail`'s style. A station square measured 1, one measured body square of 1 behind it, a loop out and back onto that body square with every loop square at 0, and a train of length 3 that came in over the body square.
- Proves the finding: `whyItWouldMeetItsOwnTail` returns the own-tail sentence with gap 1.
- Refutes it: null.
- Adam's decision: whether a return over an unmeasured loop should be judged at all, or noticed as half-measured.

---

### TDD-C1 - MT-571 expects a refusal dialog that neither send door can show: LowerFront is not offered at all

| **Disposition** | Fixed - comment on MT-571 (LowerFront not offered; the sentence is in Why not Moving? and the Why window). |
|---|---|

MT-571 (tests.md:28072), step 2: "send it to LowerFront the way you did for OB-294". Expected: "refused. The message says EN57-203 (length 20) would run into its own tail on BottomMainAPre -> BottomCrossover...". Step 3 expects the same.

Both hand doors build their lists from `Layout.getPossiblePaths(locomotive, true)`:
- `LayoutRightclickAutonomyMenu.java:239`
- `AutoLocomotiveStatus.java:193` and `:378`

`getPossiblePaths` keeps only paths for which `isPathClear` is true (Layout.java:5879). `isPathClear` now refuses every route to LowerFront for a 20-unit train, so LowerFront is not on either list.

The new own-tail check at the doors (`LayoutRightclickAutonomyMenu.java` ~1375, `AutoLocomotiveStatus.java` ~1162) only fires for a menu built before the length changed. The door comment says as much ("the list this item was built from is a snapshot").

So Adam will not see the sentence. He will see LowerFront missing, and may reasonably mark the entry "Does not work". The sentence does appear in the locomotive list's why-window and in Why not Moving?, through `whyNoRouteFitsTo` and `firstClearOrWhyNot`.

Entries are append-only. The fix is a comment on MT-571:
- steps 2-3: LowerFront is not offered, and the reason is in the why-window;
- step 4: LowerFront is offered again.

---

### TDD-C2 - behaviour.md has no own-tail rule, and section 5c still says a train never blocks itself

| **Disposition** | Fixed - behaviour.md 5c. |
|---|---|

OB-294 is a new standing refusal in every tier, the planner included. Its javadoc cites "behaviour.md 5c" for the rule it qualifies. But `behaviour.md` has no entry for it: `git grep OB-294` finds no line in `docs/reference/`. 5c still reads "A train never blocks itself — pulling forward off its own tail is how it leaves" (behaviour.md:1123), qualified only by OB-285.

Missing from behaviour.md, and from `Automation.md`'s "Track lengths - what they are for":
- the rule itself: "the head may come back to a place only once the tail has left it";
- only measured track binds;
- a turn restarts the question;
- the sentence the operator gets.

The project rule is that behaviour.md holds the intended behaviour and comments cite it. At the moment the code is the only statement of the rule. The train-length maximum raised to 40 (`ea8e4ae4`) is not recorded there either (see TDD-C5).

---

### TDD-C3 - The own-tail refusal names the first return's gap, not the tightest, so its remedy can be false on a route that comes back twice

| **Disposition** | Fixed - as TDA-C1. |
|---|---|

`whyItWouldMeetItsOwnTail` returns at the first return where `length > wayRound` (Layout.java:10420). The sentence says "a train of {2} units or shorter is clear of it in time".

A later return on the same route can have a smaller `wayRound`. For example, the route crosses its own earlier track at a diamond after coming back over the body, where `wayRound` is that loop's length. A train of the named length then passes the first return and is refused at the second, with a different figure.

Returns that come earlier did not refuse the longer train, so they cannot refuse a shorter one. Only later returns matter. The refusal itself is right; only the remedy is overstated. On the frozen railway's BottomSecondary-to-LowerFront routes the return runs along the body from its front, so the first return is also the tightest. That is why `testTheRefusalNamesTheLongestTrainThatGoes` passes.

**Fix:** scan the whole route and report the smallest positive `wayRound`, refusing if the train is longer than that.

**Verification request.**
- Fixture: a hand-built route that returns over the train's body with a way round of 12, then crosses itself at a diamond with a loop of 6.
- Case: a train of length 20.
- Proves it: the sentence names 12, and a 12-unit train is then refused naming 6.

---

### TDD-C4 - OB-294's claims pin that the sentence agrees with itself, not the measured figure; "9 units" and "no train of 9 or less is refused anywhere" are unpinned

| **Disposition** | Fixed - the 9 pinned in c2251ace; mutation OT4 (body left one unit late) red.  The census sentence in MT-571 is qualified by the comment. |
|---|---|

`testTheRefusalNamesTheLongestTrainThatGoes` reads the gap back out of the refusal (`gapNamedBy`), then checks that a train of that length goes and one unit more does not. Any mutation that moves the model consistently keeps that relation and keeps 4-clear/20-refused. Examples:
- recording a route place's free point at its near end (`put(place, travelled)` before adding the span);
- taking a body place's far end (`-(reach + span)`).

Such a mutation survives every claim in the class. The class javadoc's MUTATION list does not claim these, so no line is false. But the only figure Adam can check with a tape - "Nine measured units" in MT-571 and in `121b1c3c`'s message - is asserted nowhere.

MT-571's "What this is" also says: "Measured on the frozen railway, no route between stations comes back to a train's own track in fewer than 9 units, so no train of 9 or less is refused by this anywhere." That is an absolute census claim with no test behind it. It also depends on where each train stands and the road it came along, which the sentence does not say.

**Verification request.**
- Add `assertEquals(gap, 9)` to `testTheRefusalNamesTheLongestTrainThatGoes`, and run the two mutations above against it.
- For the census: for every station and every arrival road on `live-snapshot`, and every route to every other station, record the smallest `wayRound` the rule computes.
- Refuted if the minimum is 9. Otherwise MT-571's sentence needs a comment.

---

### TDD-C5 - behaviour.md's Bulk Tools paragraph is stale since 2026-09-24: "a length is 1 to 20", three renamed items, and the train walk's new every-train mode

| **Disposition** | Fixed - behaviour.md's Bulk Tools paragraph. |
|---|---|

behaviour.md:1010-1019 is out of date on three counts.

1. **The range.** It says of Mass Assign Train Lengths "a length is 1 to 20". `ea8e4ae4` raised `ROUTE_TRAIN_LENGTH_MAX` to 40, and `AutonomyEditorPanel.acceptsATrainLength` follows it.
2. **The names.** It still names **Mass Assign Train Lengths**, **Mass Assign Max Train Lengths** and **Clear All Max Train Lengths**, also at :1530. `fab81423` renamed them:
   - Mass Assign Locomotive Train Lengths ({0} missing)...
   - Mass Assign Station Max Train Lengths...
   - Clear All Station Max Train Lengths ({0})
3. **What the walk does.** It says the walk "walks every train autonomy would run that has no length". Since MT-533 the item is never greyed, and with none missing it goes through every train with the length it has.

---

### TDD-C6 - MT-518 to MT-521, unrun, send Adam to menu items by labels that were renamed the same day

| **Disposition** | Fixed - comments on MT-518 to MT-521 giving the labels. |
|---|---|

These are `fixed unvalidated` and on the ledger:
- MT-518 (tests.md:26339) and MT-519 (:26363) say "Bulk Tools > Clear All Max Train Lengths".
- MT-520 (:26389) and MT-521 (:26415) say "Bulk Tools > Mass Assign Max Train Lengths...".

`fab81423` renamed both items: `autolayout.ui.menuClearAllMaxTrainLengths=Clear All Station Max Train Lengths ({0})` and `autosetup.ui.menuMassAssignMaxTrainLengths=Mass Assign Station Max Train Lengths...`. MT-533 got a comment naming its new label; these four did not.

Each needs a comment giving the label as the bundle has it. The entries themselves are append-only.

---

### TDD-C7 - OB-235's claim covers the Bulk Tools item only; the toolbar button, the disarm and the click guard that MT-568 checks are unclaimed

| **Disposition** | Fixed - pin testTheOneWayButtonIsGreyedAndPutDownOnAPageLeftOut in a961695e; mutations P1, P2 red. |
|---|---|

`fab81423` fixed OB-235 in three places in `AutonomyEditorPanel`:
- the Bulk Tools item greyed (:2261);
- the toolbar button greyed and disarmed when the page is left out (:9497);
- a click guard `isIgnored(tile)` (:7211).

`core.testMassAssignLengths.testOneWayRunIsGreyedOnAPageLeftOut` (:1478) reads only `bulkItemNamed(panel, "autosetup.ui.toolOneWay")`. Its MUTATION ("leave One-way run enabled on a page left out") is true for the menu item alone.

Taking out `oneWayButton.setEnabled(!ignored)`, or the disarm (`tool = Tool.NONE; oneWayFrom = null; oneWayButton.setSelected(false)`), or the click guard, survives. MT-568 step 2 (the button greyed) and step 3 (armed, then the page switched: "no longer pressed - nothing waits for a click") are exactly those parts, and it names this test as "What this is".

**Verification request:** mutate each of the three sites in turn. Only the first should turn the class red. The claims to add are `oneWayButton.isEnabled()` after `setPageExcluded`, and the tool state after arming then excluding.

---

### TDD-C8 - testAHandSendIsRefusedWhileTheSetupIsBroken's MUTATION line claims a mutation its claims cannot see

| **Disposition** | Fixed - as TDU-C3; the class javadoc says which claim sees which mutation. |
|---|---|

The javadoc (:21-23) says: "make the rule answer nothing for a broken setup and the first does [fail]".

The first claim calls only the static `whyAHandSendIsRefused(int, int)`, which always returns words. The decision "broken or not" is in the instance method: `if (!autonomyHasErrors()) return null;` (TrainControlUI.java:23819). The only check on it is the second claim's source-shape `body.contains("autonomyHasErrors()")`.

These mutations survive both claims:
- inverting the condition (`if (autonomyHasErrors()) return null;`);
- inserting `if (true) return null;` ahead of it.

In either case a broken setup lets a hand send through, which is the thing MT-263 is about.

**Verification request:** apply the inverted condition and run the class. Expected green, which proves the finding. A behavioural claim needs a window with `autonomyHasErrors()` true, or the method refactored to take the answer as a parameter.

---

### TDD-C9 - FR-102's claim reaches only the no-railway branch of whyWaitsOn, and nothing claims the outlines go after the click

| **Disposition** | Fixed - as TDU-C11. |
|---|---|

`AutonomyEditorPanel.whyWaitsOn` (:7136) has two branches:
- the running railway's trains (`runningLayout.get()`), whenever a railway is loaded, which is how Adam runs the editor;
- the setup's placements otherwise.

`regression.testTheEditorSaysWhatItsToolsDo.testWhyNotMovingOutlinesTheTrains` (:107) builds the panel with no running layout, so it reaches only the second branch. Its MUTATION ("outline nothing while the tool waits, or every station") is true there. A mutation inside the railway branch survives, for example `return true` for every point, or ignoring `squareOf`.

The `lastWhyTile != null` condition is also unclaimed. That condition is MT-570 step 2's "the outlines go".

The sibling claim `testTheGuardItemsSayWhatTheGuardsDo` asserts only that a tooltip is non-empty, so swapping the exit and entry texts would survive. That is minor.

---

### TDD-C10 - Stale comment, javadoc and message in the train-length walk after MT-533

| **Disposition** | Fixed - as TDU-C7. |
|---|---|

- **The inline comment.** `AutonomyEditorPanel.java:2328` still says "Greyed on the walk's own count, as the two above it". The comment directly under it says "NEVER GREYED", and `setEnabled(trainsToMeasure > 0)` is gone.
- **The javadoc.** `massAssignTrainLengths` (:10020) begins "Goes through every locomotive autonomy would run that has no train length" and calls itself "Mass Assign Max Train Lengths' walk, for trains". With none missing it now walks every train with the length it has.
- **The error message.** In that every-train walk, typing 0 shows `autosetup.ui.errorTrainLengthOutOfRange`: "0 means the train has no length, which is what it has now". That is untrue for a train that has one. This is the path MT-566 step 2 walks.

---

### TDD-C11 - autoLoadOffAfterLegacyImport's reason stopped being true when OB-254 removed autonomy.json; the untick now causes what it says it only reports

| **Disposition** | Fixed - Adam, 2026-09-24: *"Drop it now."*  The untick, its log line and the changelog clause are gone; Load Autonomy itself is not defunct - it resumes the active configuration at start.  Claims 7dc22256, fix f17f5a5c.  MT-582, superseding MT-503. |
|---|---|

The javadoc of `TrainControlUI.autoLoadOffAfterLegacyImport` (:2041-2049, REG2-C3, `ee407002` at 01:31) justifies the untick like this: "on a layout with only the old graph it loaded nothing until it had been set by hand ... So after an import the box says what the next start will do - load nothing".

At 03:57 `a4674c9b` (OB-254) removed the old graph from start-up entirely. Load Autonomy now resumes the active diagram configuration (TrainControlUI ~9666), and a legacy import makes one. So after an import, a ticked box would load the imported setup at the next start. The untick is what makes it load nothing.

The changelog says it plainly ("importing one unticks Load Autonomy, so tick it again to have the imported setup loaded at start"). MT-501, MT-502 and MT-503 each tell Adam to re-tick it.

Adam ruled for the untick ("Set the setting to unchecked when importing a legacy json file, each time"), but before OB-254 changed what the box does. **His decision:** keep the untick, or drop it now that nothing old is loaded at start. Either way the javadoc needs rewriting to say what the untick now does.

---

### TDD-C12 - OB-230's control carries a MUTATION line that was never run and may not hold

| **Disposition** | Not a defect - mutation H2 (any plan first) turns testAnEasyArrangementStillGetsTheShortestPlan red: the MUTATION line holds. |
|---|---|

`core.testReturnHomeFindsAPlanOnAFullRailway.testAnEasyArrangementStillGetsTheShortestPlan` says "MUTATION: search for any plan first, and this fails on the number of moves".

`7f453b75` calls this claim "the control, green today". Neither that commit nor `8f55b863` records the mutation being run. The weighted search (`ANY_PLAN_WEIGHT` 5) is not bound to return a longer plan on four trains one platform along. If it also finds six moves, the claim cannot tell the orders apart.

**Verification request:** swap the order in `HomeStaging.search` (`astar(..., ANY_PLAN_WEIGHT)` first) and run the class.
- Red on `getMoves().size()`: the line holds.
- Green: the line is false, and the claim needs an arrangement where the weighted search provably returns a longer plan.

---

### TDD-C13 - open-questions.md's store arithmetic does not add up

| **Disposition** | Fixed - open-questions.md names the 13 rows OB-247 added. |
|---|---|

open-questions.md:355-356 says: "3,726 rows then. The round of 2026-09-23 added 336 - five reviews and three rounds of validation - which makes 4,075 finding rows in the store now".

3,726 + 336 = 4,062. The store does hold 4,075 rows for 3,718 refs (read from `triage.db`), and behaviour.md:2307 is right. The other 13 rows are AR-17 to AR-23 and LR-1 to LR-6, added on 2026-09-24 for OB-247 (`0fbbb262`). They have no document, so the round did not add them. The sentence should say where the 13 came from.

---

### TDD-C14 - The OB-235 receipt gives the wrong filing date

| **Disposition** | Fixed - the receipt says 2026-09-21. |
|---|---|

`issues.md`:1268, the receipt row for OB-235, has Filed 2026-09-24. The entry is `### OB-235 - 2026-09-21` (issues.md:458), and the other rows in the table give the date the entry was filed (OB-230 2026-09-15, OB-254 2026-09-22).

---

### TDD-D1 - OB-294's arithmetic checked: ends of places, the standing square, turns, and the planner's use

| **Disposition** | Checked - clean. |
|---|---|

These held up on reading:
- **The far and near ends.** A route place is free once the tail passes the end the head left by: `put(place, travelled)` after the span is added. A body place is free once the tail passes its end nearer the head: `-reach`.
- **The standing square is not counted twice.** `GraphReducer.placesAlong` includes an edge's far endpoint and not its near one, so the first edge does not carry the start square. The walk spends the standing square first (OB-278) with `reach` 0.
- **Turns.** A turn on the way clears the map. A train leaving over its body (first place out already on it) ignores the body.
- **The planner.** HomeStaging calls the same static method with `bodyOfATrainAt(from, loc, cameAlong)`. "Prefix-closed, so pruning loses nothing" is true for this rule.
- **Where it is asked.** `isPathClear` asks it in every tier, and so do `whyNoRouteFitsTo` and both doors. Both doors ask it before the reversal question and before `executePath`.
- **No vacuous pass.** `debugPath` enumerates every route exhaustively, so the remedy claim cannot pass because a route went missing.

The planner's `seen` dominance can still drop a longer way round that has identical ironwork. That limitation is already written at the room-rule comment in `firstClearRoute`, and I did not raise it again.

---

### TDD-D2 - OB-294 test class: its MUTATION lines match what its claims can see

| **Disposition** | Checked - clean. |
|---|---|

- The class line (starting body left out; refuse at the gap; leaving over the tail judged as behind) matches the claims.
- `testATurnOnTheWayIsNotAReturnToTheTail` (no reset after a turn) matches.
- `testReturnHomePlansNoRouteIntoItsOwnTail` (the planner without the question) matches.

`00a2fc6d` and `c95e285b` record those runs as red. The 20-unit claim asserts that the refusal is the own-tail sentence and not another rule's, and that `isPathClear` agrees with `debugPath`. The 4-unit claim and the turned control each carry an "anyClear" control, so neither is vacuous.

---

### TDD-D3 - MT-477's stop-at-the-switch claim, and its behaviour.md entry

| **Disposition** | Checked - clean. |
|---|---|

`testWithNoAnswerTheTailStopsAtTheSwitch` fails on "take the first rail by the side". It asserts both halves: every shared square is covered, and no single-rail square is. `claimUpToWhereTheRailsPart` (a common suffix, up to and including the switch; not applied to an unmeasured rail or a single road) matches behaviour.md 5c's new bullet and MT-543.

---

### TDD-D4 - Counts quoted against the file and the store

| **Disposition** | Checked - clean. |
|---|---|

- **The Inbox.** `issues.md` has 81 headings between `## Inbox` and `## What has been picked up`: 49 OB and 32 FR. That matches open-questions.md.
- **The store.** `finding` has 4,075 rows and 3,718 distinct refs, matching behaviour.md:2307. No row has an empty status.
- **The prefix.** TDD appears in neither `findings.tsv` nor the store.

---

### TDD-D5 - live-snapshot README against the sources

| **Disposition** | Checked - clean. |
|---|---|

- The Used-by list equals the set of classes under `test/` that open `"live-snapshot"`: 70 each, no difference in either direction.
- The invariants it quotes (122 reduced edges, 87 Points, 125 edges) are the numbers `testTheFrozenRailwayIsStillTheRailway` pins.
- The single-switch README's five new users are right.

---

### TDD-D6 - Pointers left for the documents 827d3444 deleted

| **Disposition** | Checked - clean. |
|---|---|

Every git-history pointer resolves:
- `0a5dab2a:docs/reference/two-copies-evaluation.md` (behaviour.md and `testEverySquareBuildsToTheCopiesTheSetupImplies`)
- `2d910000:docs/reference/package-sweeps.md`
- `d8db4879:docs/reference/GraphEdgeEdit.java.txt`
- `e5f77c9c^:docs/plans/portmap-verification.py`

No tracked file outside the triage backups and old tracker comments names `docs/tools/parity/`, the plans, or the removed reference files as if they were still there. The old tracker comments are append-only and out of range.

---

### TDD-D7 - Labels the new MT entries quote

| **Disposition** | Checked - clean. |
|---|---|

These exist in `messages.properties` exactly as quoted in MT-543 to MT-570:
- Not known; Switch to Central Station Layout; Documentation; Debug / Echo Sent Commands
- "Autonomy needs a layout on this computer - download one from the Central Station..."
- Exclude Page; Advanced Parameters...; Unavailable While Occupied ({0})
- Yes - Trains Can Stop Here / No - Trains Can Only Pass Through / No - Nothing Can Pass
- Worth tidying ({0}); Configuration errors and warnings
- Maximum Train Length ({0}); Can Be Chosen in Full Autonomy; Enable Auto Execution
- "{0} Is Facing..."; "Stations the train cannot be sent to right now ({0})"
- the ParkingTrack12 closed sentence; Mass Assign Locomotive Train Lengths ({0} missing)...

MT-569 paraphrases its two tooltips closely, which is acceptable. Every bundle is ASCII-only, and the new keys are in all eight.

---

### TDD-D8 - The forty-unit maximum reaches every consumer

| **Disposition** | Checked - clean. |
|---|---|

`ROUTE_TRAIN_LENGTH_MAX` is read by:
- the locomotive menu's list (TrainControlUI ~27564);
- `GraphLocAssign`'s list, now built from the constant;
- the walk's `acceptsATrainLength`;
- the walk's out-of-range sentence.

`Layout.fromJSON` and the legacy import check only `>= 0`. `testTheEditViewOffersEveryLengthTheMenuDoes` pins 40 and fails on a hand-written list.

---

### TDD-D9 - OB-291: a remembered maximum is ignored everywhere it could act

| **Disposition** | Checked - clean. |
|---|---|

- `Point.validateTrainLength` returns true for a non-destination. It is the only runtime reader apart from a message.
- `tilesWithAMaxTrainLength`, `modelsAnyLength`, `stationsWithAHalfMeasuredApproach` and `runInsShorterThanTheBerth` all check `store.isStation`.

That matches behaviour.md and MT-558.

---

### TDD-D10 - OB-230's budget shares, and the lowered precondition in testReturnHomeKeepsClearOfTheTailsItLeaves

| **Disposition** | Checked - clean. |
|---|---|

`HomeStaging.search` gives:
- thirds when the greedy pass moved something (shortest from the greedy arrangement, shortest from the start, then weighted any-plan);
- halves otherwise.

That matches behaviour.md and the `SEARCH_BUDGET_MS` javadoc. The precondition `now.get() > 5000` (was 7500) still proves the first share was spent. The claim still pins the retry, because HomeStaging's comment records that the weighted search finds nothing on that railway.

---

### TDD-D11 - Receipts name the right tests

| **Disposition** | Checked - clean. |
|---|---|

The issues.md receipts for 2026-09-24 each name the MT that checks their behaviour:
- OB-285 → MT-495; OB-230 → MT-492; OB-284 → MT-498
- OB-288 → MT-552 (superseded by MT-564, and says so)
- OB-289 → MT-553; OB-290 → MT-549; OB-291 → MT-558; OB-292 → MT-563
- FR-100 → MT-565; FR-101 → MT-562
- OB-235 → MT-568; OB-293 → MT-569; FR-102 → MT-570
- OB-294 → MT-571 and MT-572

OB-283 and FR-093 are marked declined with the reason. OB-286, OB-287 and FR-098 are still in the Inbox, with reasons.

---

### TDD-D12 - The checker-oracle change in 4c5c8702 is sound

| **Disposition** | Checked - clean. |
|---|---|

`testTheCheckerAgreesWithTheBuild` now:
- asserts that every closed station is unreached on the built graph;
- compares the `STATION_CLOSED` list exactly;
- removes the closed stations before the reachability comparison, so a closed station also reported unreachable still fails it.

The oracle's `closedAmong` reads `session.shutTiles()`, the same source the checker reads. It proves the sentence is emitted once each, not which squares are closed, and that matches its message. `testPathTypeRedrawsTheTestInTheEditor` zeroing the borrowed train's length and restoring it in `finally` is right.
