# Seven-day review: the working tree of 2026-09-13 over the week's commits

**Status:** open
**Prefix:** SEV

**Reviewed:** branch `autonomy-diagram-r0` at commit `7f8630ba` (2026-09-12 03:47) PLUS the uncommitted
working tree as it stood on the afternoon of 2026-09-13 - 47 tracked files modified and 18 untracked
(all under `test/` and `docs/reviews/`), 2,300 lines added under `src/`. The working tree is the
subject. The 186 commits since 2026-09-07 were listed and read by subject; their bodies were opened
only where a working-tree method reaches into them.

**Read-only.** No test was run, nothing was built, nothing was written except this file. Under
`cs2_sample_layout/` only `config/autonomy/setup.json` was read, to count measured tiles (it stores
them under `tileLengths`; the count agrees with the working tree's own comments - three).

## Method, and what was actually read

**Triage.** A week of 186 commits plus a 65-file working tree cannot be read line by line, and the
brief said so. The choice made: the working tree's `src/` diff was read in FULL (`git diff -- src`,
about 2,000 lines across 18 files), because it holds today's changes and every fix from the PFX/PRV
round, and because the two previous reviews already covered the three days of commits before it. The
week's commits were then read as a LIST, and the diffs consulted only where a changed method in the
working tree calls into them. Tests were opened for headers and for the specific claims a finding
rests on, never run.

Then, for every changed method, the callers and the methods it delegates to - in their current form
in the file, not in the diff - because the previous round's most useful finding (`whyTooLongForThisRoute`
is not `measuredRoomAtTheEndOf` plus a relaxation) came from exactly that habit.

Read in `src/org/traincontrol/automation/`:

- `Layout`: `walkStandingTrains` whole (6094-6285), `tailLiesOn`, `isPathClear`'s covered-track sweep
  and the berth refusal (2518-2680), `whyABerthCannotHoldIt` whole (8033-8110),
  `whyTooLongForThisRoute` whole with the relaxation (8138-8265), `measuredRoomAtTheEndOf` (8363-8440),
  `turnsOnArrival` (5812-5842), `shouldReverseAt` whole (5878-5975), `hasAWayThrough` (4646-4660),
  `isOfferableToOperator` (4560-4632), `barredFromAutonomy` (4680-4705), `mayReverseAt` (6340-6358),
  `standOnTheCopyItDidNotTurnOn` whole (3242-3282), the arrival block of `executePathInternal`
  (7305-7440) including where the re-stand sits relative to `unlockPath`, `CB_ROUTE_END` and the
  layout callbacks, the start-Point refusal at 6717-6722, the two path-end filters at 4011 and 4272,
  `executeTimetableInternal`'s retry loop (5405-5490), `loadReturnToHomeTimetable`, the `places` JSON
  reader, and `applyDefaultLocCallbacks`' route-end hook (9190).
- `HomeStaging`: the constructor's two snapshots (118-145), `snapshot`'s station set (156-162),
  `astar` and its move generation (730-835), `firstClearRoute` whole (918-1135) including the room and
  berth tests at the destination, `canRest` both overloads (1633, 1739), the reachability search
  (1845-1860), `Candidate`.
- `Edge.setPlaces/getPlaceIds/getPlaceLengths/toJSON`; `Point.isSamePlaceAs`, `getBlock`,
  `heldBackBy`'s javadoc change.

In `automationui/`: `AutonomySession.homeEveryPlacedTrain`, `setHome`, `homesElsewhere`,
`clearEveryHome`, `setPointProperty`, `tilesWithALocomotive`, `placedLocomotives`,
`getLocomotiveNameAt`, `facingByPath` whole, `walkTo` whole, both `facingOf` overloads,
`facingsFor`, `sameSquare`, `routesCoveredByStandingTrains`, `tilesBlockedByStandingTrains`,
`walkBackFrom` whole, `flipFacing`'s candidate collection, the reversal-notice change at 2678-2730,
`getCaptionTarget`/`setCaption`; `GraphReducer.unmeasuredAfterTheLastSwitch`, `placesAlong`, `Place`;
`AutonomyBuilder`'s `places` emission; `TileAnnotation.paint` all three overloads;
`TileOverlay.paint` all three and `paintRun`; `AutonomyCompanionStore.getCaptionTarget`, `isStation`,
`getTileLength`.

In `gui/`: `AutonomyEditorPanel.bulkTools`, `homeEveryPlacedTrain`, `clearAllHomes`,
`addCaptionItems`, `promptStationLabel`, `promptBlockingPoints` (the filter, the sort, the grey),
`applyRememberedCaptionMode`, `locomotiveAt`, `placementChanged`'s comment, `clearLocomotivesWarning`;
`TrainControlUI`'s paste and cut branches (6690-6880) and `rememberPlacement` (7551-7640), the
`cutFacing` field and its clearing on locomotive deletion, `attachAutonomyRefresh`,
`autonomyCaptionAt`, the function-button block (12115-12170), the Auto-tab `loaded` change, the
eleven `log` -> `logf` substitutions; `LayoutLabel.paintComponent`'s wash block and `TRAIN_MARK`;
`LayoutEditor.setAutonomyMode`'s posted block; `LayoutRightclickAutonomyMenu.PathOptions`,
`theListWasCutShort`, `gatherPathOptions`, the draw loop, the berth pre-check;
`AutoLocomotiveStatus.whatTheOperatorMayChoose` and the berth pre-check; `ManualReversalPrompt.forJourney`
whole; `Locomotive.drivableFunctionCount`, `MarklinLocomotive`'s override.

Documents: `docs/reviews/README.md`, `2026-09-12-PRW-three-day-review.md`, `2026-09-12-PFX-fix-round.md`,
the head of `2026-09-12-PRV-fix-validation.md`, the working-tree diffs of `behaviour.md`, `issues.md`
(OB-207 to OB-213, FR-074, FR-075), `Automation.md`'s new "Track lengths" section, and the MT-367,
MT-368 and MT-371 entries in `tests.md`. The eight message bundles were checked for the 22 new keys
(all present in all eight). Test headers and specific claims: `testAStationsSizeIsAnAllowance`,
`testABerthAndAPlatformJudgeAnOverhangDifferently`, `testTheArrivalHonoursTheAnswer` (method list),
`testAPasteDoesNotTurnTheTrainRound` (header only).

**What was looked for**, in the order the brief listed: a seventh site of the may-turn/terminus
confusion; rule pairs that must agree; state read at the wrong moment; the drawing against the guard
after today's allowance ruling; today's new code. Findings that the PRW/PFX/PRV round already
recorded are not repeated. One PFX fix is contradicted below (SEV-B1 is a consequence of today's
allowance change on yesterday's berth rule) and one today's fix has a consequence in the planner
(SEV-B2); both say so.

---

## A - wrong behaviour on the layout

| id | status | where |
|---|---|---|
| (none) | | |

Nothing in this pass reaches A. SEV-B3 is collision-shaped but is not reachable on the railway as it
is measured today, and says so; SEV-B1 and SEV-B2 are stranding and refusal, with the train safe.

---

## B - incorrect results or refusals in specific configurations

| id | status | where |
|---|---|---|
| SEV-B1 | fixed | `Layout.whyABerthCannotHoldIt` 8084-8086 against `walkStandingTrains` 6263 |
| SEV-B2 | fixed | `Layout.turnsOnArrival` 5831 against `HomeStaging` 162/807/1128 and `executePathInternal` 6721 |
| SEV-B3 | fixed | `Layout.walkStandingTrains` 6263 against 6271 |

### SEV-B1 - the berth rule still spends the berth's own measurement; the guard no longer does

**Where.** `src/org/traincontrol/automation/Layout.java`: the claim loop of `whyABerthCannotHoldIt`
at 8082-8088 (`claimed.add(ids.get(n)); left -= Math.max(0, spans.get(n));` from `n = ids.size()-1`,
which is the berth square itself) against the allowance at 6263 in `walkStandingTrains`
(`onTheAllowance = fromTheEnd && step == 0 && here == standingHere` - the standing square is claimed
and NOT spent).

**The pair that must agree.** The berth rule's own contract (8050-8053): *"Which places the train
would claim is the same arithmetic the tail walk uses ... so what this refuses and what
`walkStandingTrains` would then block are the same stretch of railway, rather than two answers that
have to agree."* Today's allowance change altered the tail walk's arithmetic at exactly one place -
the square the train stands on - and the berth rule was not changed with it. The two now spend
different squares for the same train, and the berth rule is the looser one.

**What happens.** A train is offered a parking berth, the berth rule accepts it because the berth's
own measurement absorbs part of the train, the train parks, and the guard then claims the train's
whole length behind the berth - over the switch the berth rule exists to keep clear.

Concrete, on the project's own fixture. `core.testABerthAndAPlatformJudgeAnOverhangDifferently`
measures `TunnelLongPark` (10,9) at 2 and leaves 10,10 unmeasured (the live snapshot carries no
lengths, OB-196). A two-unit train:

- Berth rule: claim 10,9, `left = 2 - 2 = 0`, break. Claimed `{10,9}`. Nothing shared. **Accepted** -
  and `testTheBerthRuleNamesTheRoadItWouldFoul` asserts exactly this at length 2, quoting the rule's
  javadoc: *"a two-unit train fits inside the berth and lies over nothing at all."*
- Guard, once it is standing there: claim 10,9 without spending (allowance), `left = 2`; claim 10,10,
  span 0, `left = 2`; claim 10,8 - the switch - span 0, `left = 2`; and on to every place of the
  approach, because none of them is measured. **10,8 is claimed**, and `isPathClear`'s shared-metal
  sweep now refuses `BottomMainAPre -> RampDown` and `-> BottomCrossover` through `tailLiesOn`. Those
  are, in the berth rule's own words, *"the only two roads to the lower level"*; the rule's javadoc
  measures the cost at 90 ordered pairs of stations.

Yesterday the guard spent the berth's 2 as well (`left = 0` after 10,9, break) and the two agreed.
Today's change is what opened the gap, and `testAParkingBerthStillTakesATrainThatFits` is green over
it because it asks only the two pure rules and never the walk.

With the figures MT-371 step 4 tells Adam to set - 10,9 = 2 AND 10,10 = 2 - the two-unit case agrees
again (the guard spends 10,10 and stops), and the disagreement moves to three units: berth rule claims
10,9 (`left 1`) and 10,10 (`left -1`), accepts; guard claims 10,9 (`3`), 10,10 (`1`), 10,8 (`1`) -
the switch again. MT-371 step 4 tells him a three-unit train there is *"refused ... a parking berth
may not block a road"*. It will be accepted.

**Reachable by.** Every door asks the same static method - both hand-driven doors pre-check it
(PRW-C2's fix), `isPathClear` refuses on it, and `HomeStaging.firstClearRoute` asks it at the
destination (PRW-B2 leg 3's fix) - so all of them accept together. Any train with a length, at any
berth whose approach is partly measured. On Adam's railway the moment he measures a berth square, which
is what both MT-371 step 4 and the new `Automation.md` section (*"Measure the squares a train comes to
rest on"*) tell him to do.

**Not a collision.** The guard is the one that refuses and it is right; what is lost is the lower level
for as long as the train is parked, which is the stranding cost his ruling assigned to berths
specifically because *"a parked train pays it all evening."* B for that reason.

**Remedy shape.** Skip the span of the last place (`n == ids.size()-1`) in the berth rule's claim, so
its arithmetic follows the guard's allowance - one line, mirroring `onTheAllowance`. Then the fixture's
length-2 claim will go red for the right reason (an unmeasured 10,10 costs the tail nothing, so a
two-unit train at an unmeasured approach does reach the switch), and measuring 10,10 - which MT-371
already asks for - makes it green again. The javadoc sentence about the two-unit train, and the
`Automation.md` example of a berth that takes a train that "fits end to end", both need the same
correction.

### SEV-B2 - the planner still models a non-reversible train as turned at a may-turn square; the runtime no longer turns it

**Where.** `src/org/traincontrol/automation/Layout.java` `turnsOnArrival` 5831 (the clause added today:
`!loc.isReversible() && hasAWayThrough(arrived)` returns false BEFORE the `ALWAYS_REVERSE` test) and
the re-stand it triggers at 7416; against `src/org/traincontrol/automation/HomeStaging.java` 162 (the
station set is every active destination Point - every COPY), 807 (any of them may be an intermediate
rest), 1739 (`canRest` carries no terminus rule for a non-reversible train, by Adam's ruling of
2026-08-31), 1128 (a `terminus:true` copy ends a leg and the next leg starts from it); and
`executePathInternal` 6717-6722, which refuses a leg whose start Point does not hold the train.

**This is the seventh site.** `AutonomyBuilder` emits the turning copy of a may-turn square with
`terminus: true`. The planner never reads that flag as "this train turns here" - its `turned` flag reads
`isReversing()` only - but it does not need to: it stands the train on that Point and searches the
next leg from that Point's OUTGOING EDGES (`getNeighbors(current.at)` at 963), and the turning copy's
outgoing edges are the edges for a train pointing the other way. So the plan says, in the only
vocabulary the graph has, "after this leg the train is turned". Until today the runtime agreed:
`executePath` under `ALWAYS_REVERSE` turned every train at a `terminus:true` copy, so the copy was
right for the wrong reason - which is the same sentence PFX-B3 wrote about the hand-driven case.

Today's clause makes the runtime disagree for a train that cannot reverse: it is not turned, and
`standOnTheCopyItDidNotTurnOn` moves it onto the plain sibling. The next leg of the plan starts from
the turning copy, `start.getCurrentLocomotive()` is null there, the leg is refused with
`errorLocomotiveNotAtPathStart`, and `executeTimetableInternal` retries it until
`STAGING_MAX_ATTEMPTS`, logs `errorReturnToHomeEntryStuck`, calls `stopLocomotives()` and abandons
the plan (5445-5480). The fleet is left half-staged, which is OB-073's shape and the case the
planner's own comment at 1053 says it must not produce.

**Trace.** EN57-947 (cannot reverse) needs to be moved out of the way before another train can reach
its home. `astar` at 807 tries every station as a rest; `BottomMainB (eastbound, reverse)` is a
destination, active, not excluded, and `canRest` accepts it (the terminus rule was removed there
deliberately so that berths could be homes). `firstClearRoute` reaches it - a `terminus:true` copy may
be arrived at - and the plan records Move 1: EN57-947 to that copy. Move 2, later, is EN57-947 from
that copy onward, along its westbound edges. Runtime, Move 1: `turnsOnArrival(arrived, EN57-947,
ALWAYS_REVERSE)` - non-reversible, `hasAWayThrough` finds the plain `(eastbound)` copy - returns
false; the train is re-stood on `BottomMainB (eastbound)`. Move 2: refused at 6721. Abandoned.

The same pair of legs in a RECORDED timetable - a non-reversible train captured arriving at a
may-turn square's turning copy and departing from it, which is what every such arrival recorded before
today looks like - replays the same way, through the same loop.

**Reachable by.** A Return Home whose plan rests a non-reversible train at a may-turn square on the
way (the common one-move-per-train case does not reach it; a shunt does), and any timetable holding
such a leg pair. Adam's railway has two non-reversible locomotives with lengths set this week and one
may-turn station with plain copies on both sides.

**Why this is not already caught.** `testTheArrivalHonoursTheAnswer.testATrainThatCannotReverseIsNotTurnedWhereItNeedNot`
drives one path and asserts the copy; nothing under `test/` drives a two-leg plan for a non-reversible
train through a may-turn square (`grep` for `HomeStaging`/`ReturnToHome` in the three arrival test
classes finds nothing).

**Remedy shape.** Either side can move, and the one that should is the planner: it already asks the
runtime's own statics at four places (PFX's "the pattern `Point.heldBackBy` sets"), and the question
here has a runtime owner too - whether THIS train, arriving at THIS copy, ends up on it. A small rule
in `HomeStaging` that, for a non-reversible train, never rests it on a turning copy that has a plain
sibling reachable by the same approach (the predicate `standOnTheCopyItDidNotTurnOn` uses) keeps the
plan describing what the railway will do. Going the other way - re-turning the train under
`ALWAYS_REVERSE` - reintroduces MT-368's complaint for Return Home.

### SEV-B3 - the allowance is applied to the places budget and not to the edge budget

**Where.** `src/org/traincontrol/automation/Layout.java` `walkStandingTrains`: 6263-6265 skips the
standing square's span when spending `left`; 6271 `remaining -= segment.getLength()` still spends the
whole segment, whose length includes that square (`Edge` javadoc: *"the arriving square counts and the
departing one does not"*). `remaining` is what decides whether the walk takes a second hop, and it seeds
`left` on that hop.

**What happens.** For a train at a square measured `a`, with an approach whose other places sum to
`p1`, and a length `L` with `p1 < L <= p1 + a`: the places loop ends the first hop with `left = L - p1
> 0` - the ruling says that much of the body lies further back - but `remaining = L - (p1 + a) <= 0`
ends the walk. The tail's last `min(L - p1, a)` units are never claimed, and the edge they lie on is
never covered. That is the permitting side: a route sharing metal with that edge is cleared over a
train lying on it.

Concrete: platform measured 10, approach `[t(2), P(10)]` length 12 from Point Q, a measured plain edge
`[u(3), Q(1)]` behind Q. A six-unit train at P should, under the ruling, lie over `t`, `Q`, `u` (2 + 1
+ 3 = 6). The walk claims `t`, sets `left = 4`, and stops because `remaining = 6 - 12 < 0`. `Q` and `u`
stay clear.

**Reachable by.** Only with the edge behind the approach MEASURED (the measurement rule at 6217 stops
an unmeasured second hop anyway) and joined by a single neighbour. Adam's railway has three measured
tiles and no measured second edge, and `Automation.md`'s new guidance asks him to measure the run back
to the switch, which on his approaches lies inside the first edge. So: not reachable today, reachable
the day a second edge is measured, and the test that pins the ruling (`testAStationsSizeIsAnAllowance`)
is a single-edge fixture and cannot see it. Recorded as B rather than C because the error is on the
permitting side of the one guard that stands between two trains.

**Remedy shape.** Spend `remaining` by the same rule as `left` - subtract the segment's length less
the standing square's span on the first hop - so the two budgets describe one train. The room rules
(`measuredRoomAtTheEndOf`, and the relaxation's bound at 8252, which admits a train as long as the
whole edge INCLUDING the station square) still count the station square as room; that is the ruling's
"capacity" reading and is deliberate, but it means a train the relaxation admits at its bound lies, by
the walk's reading, exactly `a` units past the approach. Worth a sentence at 8252.

---

## C - narrow, cosmetic, or a trap for the next caller

| id | status | where |
|---|---|---|
| SEV-C1 | fixed | `Layout.hasAWayThrough` 4646 / `turnsOnArrival` 5831 |
| SEV-C2 | fixed | `AutonomySession.homeEveryPlacedTrain` 6200-6220 |
| SEV-C3 | fixed | `AutonomyEditorPanel.bulkTools` 2018, `homeEveryPlacedTrain` 2107 against `AutonomySession` 1789 |
| SEV-C4 | answered | `AutonomySession.homeEveryPlacedTrain` reads the setup's placements |

### SEV-C1 - `hasAWayThrough` answers about every copy except the one it is asked about

`hasAWayThrough(square)` skips `copy == square` (4652) and returns true only when some OTHER copy is
plain. Its javadoc says *"@param square any copy of the square"*. `isOfferableToOperator` only calls it
with a turning copy, where that is right. `turnsOnArrival` (5831) calls it with whatever `arrived` is -
and on a may-turn square with ONE arrival side (one plain copy, one turning copy) the plain copy asks
and is told there is no way through, because its only sibling turns.

Traced to the end for a non-reversible train arriving on that plain copy: the clause does not fire,
`KEEP_DIRECTION.asksAbout` is false, `arrived.isTerminus()` is false, and `shouldReverseAt(plain,
plain, loc, KEEP_DIRECTION)` returns false by either of its last two lines (`mayReverseAt` false when
the sibling carries `terminus`, `shouldReverse` false when it carries `reversing`). So the answer is
right by accident. On Adam's railway every may-turn square has plain copies on both sides. A trap for
the next caller, of the kind the protocol says to record; the fix is `copy == square` -> test the
square itself first.

### SEV-C2 - the bulk home door re-derives the station index once per train, and says it does not

`homeEveryPlacedTrain` (6200) calls `setHome` per train, and `setHome` (6128) calls `setPointProperty`
once per home swept and once for the write - each of which ends in `deriveStationIndex()` (6316-6325,
*"a full builder construction, on the event thread"*). Then the bulk door derives once more. The
javadoc's claim - *"the loop calls it and only the re-derive is lifted out"* - is not what the code
does; `clearEveryHome` beside it uses `writePointProperty` precisely to avoid this. Cost, not
correctness: N+1 to 2N+1 builder constructions on the EDT for N placed trains. The comment is the
defect as much as the loop.

### SEV-C3 - the confirmation counts trains on excluded pages; the door skips them

The menu label, the tooltip, the guard and the confirmation all count `tilesWithALocomotive()` (2018,
2107, 2113-2119), which walks every configuration point. The door iterates `placedLocomotives()`
(6208), which skips excluded pages (1789). With a train placed on an excluded page the dialog says N,
`assigned` is N-1, and `infoTrainsHomed` reports the smaller number. Narrow; the two counts want one
source. (`clearAllPlacements` has the same pair and was not checked for the same drift.)

### SEV-C4 - "where it stands" is the setup's answer, which is fresh only as of the capture that opened the editor

`placedLocomotives()` reads `loc` from the configuration's points (1774-1797), and the session's own
comments (`flipFacing`, 1623) say that record *"names the square a train SET OFF FROM until the next
captureFromLayout writes the arrival back."* The editor is opened after `captureRunningLayout()`
(`TrainControlUI` 5135), so at the moment it opens the two agree. Whether a train can move while the
editor stays open - a hand dispatch from the main window's diagram, or autonomy already running when
the editor was opened - and whether the editor's marks would then show the railway while this door
reads the file, was NOT traced to a conclusion here; the editor's own `locomotiveAt` (4596) reads the
same setup property, so at least the editor's list and this door agree with each other. Recorded so
the next reader knows it was looked at and not settled.

---

## D - not defects: clean checks and things that look wrong and are not

| id | status | subject |
|---|---|---|
| SEV-D1 | clean | the re-stand's position among the arrival's callbacks |
| SEV-D2 | clean | the cut clipboard's heading |
| SEV-D3 | clean | `ManualReversalPrompt.forJourney`'s new clause; MT-368 point 2 known |
| SEV-D4 | clean | the caption menu's "shows itself" test |
| SEV-D5 | clean | the three washes after PRW-C1 |
| SEV-D6 | clean | guard/affordance pairs at the hand doors and the planner |
| SEV-D7 | clean | the picture against the guard after the ruling |
| SEV-D8 | clean | the blocker-list filter, sort and grey |
| SEV-D9 | clean | the PFX/PRV fixes re-read |
| SEV-D10 | clean | smaller working-tree changes |
| SEV-D11 | not new | the two `HomeStaging` snapshots |

**SEV-D1.** `CB_ROUTE_END` fires before the re-stand (7398) and only toggles arrival functions (9190);
the layout callbacks that reach `attachAutonomyRefresh` -> `updateVisiblePoints` fire after it, inside
the same monitor (7418-7434). So nothing that reads the train's Point runs between the arrival and the
re-stand. The tail is read from `arrived` before `setLocomotive` sweeps it (3271-3277).

**SEV-D2.** `cutFacing` is read at 6856 before `moveLocomotive(null, ...)` at 6868, from the running
layout through the PFX-B4 `facingOf`, which answers from the copy; it is cleared on paste (6837) and
when the locomotive is deleted (20596); the override at 6765-6770 fires only for the clipboard's own
train and only where `facingsFor(aimed)` holds that side, and `point` was resolved before it from the
walked answer - which is right, because `rememberPlacement` writes the facing through
`facingAfterAPaste(facingsFor(tile), facingAtTheLanding, point.getName())`, not from the copy.

**SEV-D3.** The non-reversible clause at 145 sits after the terminus-not-asked return (98-100) and the
destination test (126), and before `ask` - so no dialog is shown for a train it applies to, and a
compulsory terminus never reaches it. MT-368 point 2 (the in-progress badge reads the reserved turning
copy) is diagnosed in `tests.md` and deliberately not repaired; not re-reported.

**SEV-D4.** `showsItself` needs an explicit `captions` entry `tile -> tile` (2446; `getCaptionTarget` is
a map lookup, 5353). Checked whether a station square with NO entry would draw its name anyway and so
make the enabled item silent again: `autonomyCaptionAt` returns null for a square with no entry
(5272-5322), so nothing is drawn there and `applyCaption(tile, tile)` is a visible change. The fix
covers the state Adam reported.

**SEV-D5.** `LayoutLabel` 1461 hands the annotation and the overlay the SHAPE already faded (the whole
tile, or one road of a double curve); `TileAnnotation.paint(Shape)` and `TileOverlay.paintRun` subtract
it with `Area` and clip, restoring the clip after. The two boolean overloads reduce to the whole-tile
rectangle. `theRoadToFade()` is evaluated up to three times per paint; harmless.

**SEV-D6.** Both hand doors, `isPathClear` and `HomeStaging.firstClearRoute` ask the same two statics
(`whyTooLongForThisRoute`, `whyABerthCannotHoldIt`); the right-click menu and the Locomotive tab both
ask `isOfferableToOperator`; the bulk-home item greys on the same count its guard checks. The one
disagreement found is not between doors but between a rule and the walk (SEV-B1).

**SEV-D7.** `walkBackFrom` never spends the standing square and is fed from
`edgesCoveredByStandingTrains`, so it walks exactly the edges the guard covered; within them it spends
each square's length. After the ruling the guard's place claim on the first hop matches it. The
PRW-B4 offset is settled the way the walk's comment records; and the picture inherits SEV-B3's
edge-extent stop, so the two still agree with each other there.

**SEV-D8.** `already` is read before the loop (3973) and a stored non-station entry is kept in the list
(4021), so the FBR-A2 deletion cannot recur; the sort is on `describeTile`, the grey is a foreground
colour with the box still clickable, and `stationsAutonomyWillNotChoose` is read once outside the loop.

**SEV-D9.** PFX-B2 (`anyMeasured`), PFX-B4 (the copy's facing, falling back to the square), PRV-B1
(`walkTo` records a turning copy and does not expand it), PRV-B2 (the re-stand after `unlockPath`),
PRW-B2 legs 1 and 3 in the planner, PRW-C1, PRW-C2 - all read in their current form; nothing to add
beyond SEV-B1's consequence for the berth rule and SEV-B2's for the planner.

**SEV-D10.** `Edge.setPlaces` refuses mismatched lists and the JSON reader skips an entry without `at`
from both lists together; `GraphReducer.unmeasuredAfterTheLastSwitch` answers empty as soon as the
stretch is measured at all, matching `roomAfterTheLastSwitch`; the reversal notice adds the end tile
only where no edge arrives; the Auto tab's third `loaded` clause is gone on Adam's ruling and the
comment says why; `debugArea.setEditable(false)`; `TRAIN_MARK` = `POINT_INACTIVE`; the function buttons
past `getNumF()` follow `drivableFunctionCount()` with no text and no icon, and the F20 tab follows
the same number; `theListWasCutShort` is unchanged from PRW-D3; the 22 new bundle keys exist in all
eight languages. Read, nothing to report.

**SEV-D11.** `HomeStaging`'s constructor takes `edgesCoveredByStandingTrains()` and
`placesCoveredByStandingTrains()` in two separate `synchronized` calls (128-140), so they are two
snapshots of a railway that could move between them. It cannot here: `loadReturnToHomeTimetable`
refuses while anything is running, and the planner is built at rest. Noted for whoever moves it.

---

## What this pass did NOT cover

- **The bodies of the 186 commits.** The list was read; diffs were opened only for what a working-tree
  method reaches. The 2026-09-07 to 2026-09-09 work (the arrival-side vocabulary, the menu-bar scale,
  the triage store, the page-rename fixes, the CAN reopen, the covered-track grey's first three
  iterations) was not reviewed here beyond its current form where a changed method touches it.
- **Tests.** None run. Whether any of the 18 new classes skips everything is unknown here. Two were
  read far enough to say what they cannot see (SEV-B1, SEV-B3).
- **`AutonomyBuilder`** beyond the `places` emission; **`GraphReducer`** beyond the three methods named;
  **`StationIndex`**; **`GraphLocAssign`** (PRW covered its door and it did not change this week);
  **`AutonomyCompanionStore`** beyond the four accessors named; **`LayoutGrid`**.
- **The room rules' arithmetic** - read for contracts, not re-derived, same as the last two passes.
- **MT-368 point 2** (reserving the turning copy at dispatch) - known, deliberately unrepaired, not
  examined for what a repair would touch.
- **Concurrency** beyond SEV-D1 and SEV-D11. `hasAWayThrough` walks every Point under the Layout's
  monitor from `isOfferableToOperator`, which the Locomotive tab's fallback path calls on the event
  thread (PRW-D10's allowance); not re-measured.
- **The message bundles' translations** and whether the eight files are still ASCII-only; the keys
  were counted, the text was not read.
- **`test/baseline/configuration.json`** (+4,344 lines) - regenerated with `places`; not compared.
- **SEV-C4's open question** - whether a train can move while the editor is open and what the editor
  would show if it did.
