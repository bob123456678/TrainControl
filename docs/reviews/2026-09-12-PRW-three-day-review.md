# Three-day review: the autonomy diagram work of 2026-09-10 to 2026-09-12

**Status:** open
**Prefix:** PRW

**Reviewed:** branch `autonomy-diagram-r0` at commit `7f8630ba` (2026-09-12 03:47) PLUS the uncommitted
working tree as it stood on the afternoon of 2026-09-12 - 41 files modified, 12 test classes untracked,
1,102 lines added under `src/`. The working tree is the subject; the 43 commits since 2026-09-10 were
read where the working tree depends on them.

**Read-only.** No test was run, nothing was built, nothing under `cs2_sample_layout/` was touched beyond
reading `config/autonomy/configuration-Main.json` and `setup.json` for figures.

## Method, and what was actually read

The working-tree diff of `src/` in full (`git diff -- src`, 1,917 lines), then every changed method in
its file with its callers:

- `Layout.isPathClear` (the covered-track sweep and the two new refusals), `walkStandingTrains`,
  `tailLiesOn`, `whyABerthCannotHoldIt`, `whyTooLongForThisRoute`, `hasAWayThrough`,
  `isOfferableToOperator`, `barredFromAutonomy`, `turnsOnArrival`, `shouldReverseAt`, the arrival block
  of `executePathInternal` (7140-7230), `getNeighbors` and all eleven of its callers, `getPossiblePaths`,
  `entrySideOf`, the JSON reader for `places`.
- `Edge.setPlaces/getPlaceIds/getPlaceLengths/toJSON`.
- `GraphReducer.placesAlong`, `unmeasuredAfterTheLastSwitch`, `deriveLocks`, `locationsOf`, `lengthOf`,
  `sumLength`, `ReducedEdge.getPath`.
- `AutonomyBuilder` 930-1010 (how a may-turn square's copies are emitted and flagged) and the `places`
  emission.
- `AutonomySession.facingByPath`, `walkTo`, `facingOf` (both overloads), `facingAfterAPaste`,
  `facingOnTheRailway`, `moveOntoFacingCopy`, `faceTheWayItCameIn`, `reversalsWithoutLength`,
  `stationsAutonomyWillNotChoose`, `routesCoveredByStandingTrains`, `tilesBlockedByStandingTrains`,
  `walkBackFrom`, `captureFromLayout`'s facing write, `canDepartFrom`.
- `StationIndex.distinctDestinations`, `facingsAt`.
- `HomeStaging.firstClearRoute` (940-1060), `passesTheTailsOfTrainsThatHaveNotMoved`, the reachability
  search (1740-1800), the constructor's `coveredAtStart`.
- `ManualReversalPrompt` whole.
- GUI: the three paste doors (`TrainControlUI.rememberPlacement` and the `facingAtTheLanding` read at
  6755-6800, `AutonomyEditorPanel` 4520-4580, `GraphLocAssign.commitAndRecord` and
  `headingOfTheArrivingTrain`), `TrainControlUI.repaintLoc`'s function-button block and `ProcessFunction`
  / `fireF`, `reconcileFacingWhenIdle`, `workOutCoveredTrack`, `isTrackBlocked`, `coveredRoutesAt`,
  `captureRunningLayout` and its six callers, `LayoutLabel.paintComponent` and `theRoadToFade`,
  `TileOverlay.paint/paintRun/colourOf`, `TileAnnotation.paint`, `LayoutRightclickAutonomyMenu`'s
  `gatherPathOptions`, `theListWasCutShort` and the draw loop, its placement door,
  `AutoLocomotiveStatus.notChosenByAutonomy`, `whatTheOperatorMayChoose`, the double-click handler,
  `LayoutEditor.setAutonomyMode`'s posted block.
- `Locomotive.drivableFunctionCount/getF/validF`, `MarklinLocomotive.drivableFunctionCount/getF/setF
  (930)/functionsOff`, `Point.setLocomotive`, `Point.isSamePlaceAs`.
- `docs/manual-tests/tests.md` MT-364 to MT-377, the working-tree additions to `issues.md` (OB-207 to
  OB-212, FR-074), the headers of `testTheArrivalHonoursTheAnswer`, `testAPasteDoesNotTurnTheTrainRound`
  and `testABerthAndAPlatformJudgeAnOverhangDifferently`.

Figures from the real railway: `setup.json` measures THREE tiles (`5:19,12`=1, `5:13,12`=1,
`5:10,10`=2); `configuration-Main.json` has 20 squares with `autoDestination: false`, 12 of them
`parking`, and none of the 71 point squares carries a `length` of its own.

Every finding below names the scenario and says why a caller reaches it. Two were checked against
Adam's own triage notes in `tests.md`, which record the offered path and the outcome on his railway.

---

## A - wrong behaviour on the layout

| id | status | where |
|---|---|---|
| PRW-A1 | fixed | `Layout.turnsOnArrival` / `executePathInternal` arrival block |

### PRW-A1 - "keep direction" at a may-turn square leaves the train on the copy that says it turned

**Where.** `src/org/traincontrol/automation/Layout.java` `turnsOnArrival` (about 5722-5739) and the
arrival block that consumes it (about 7193-7228); `StationIndex.distinctDestinations` (468-500) for the
mechanism that offers the path.

**The fifth site.** The task asked for a fifth instance of the may-turn/terminus confusion. This is it,
and it was created by the fix to the third (MT-368).

**What happens.** A hand-driven send to a station trains MAY turn round at is offered ONE row per square
(`distinctDestinations` keeps the first path per square, in `HashMap` order of `Layout.points`), and on
Adam's railway that row's path ends at the TURNING copy, `BottomMainB (eastbound, reverse)`. That is not
a guess: his MT-368 triage - *"I said yes, but it still got reversed ... 'no' also reverses it"* - is
the old `isTerminus()` limb firing, which it can only do at that copy.

The working tree's `turnsOnArrival` now honours the answer, so with "keep direction" the locomotive is
NOT sent a direction change. But nothing else changes: the train is still stood on
`BottomMainB (eastbound, reverse)`, whose side is W and whose only outgoing edges lead back the way it
came. The physical train faces E.

Trace of what follows, all in the working tree:

- 7220: `reversedOnArrival.remove(loc.getName())` - the "arrival that did not turn" branch, whose comment
  says *"this arrival has placed it on the copy its journey ended at and the graph is already right
  about it."* That sentence is now false for exactly this case.
- `TrainControlUI.reconcileFacingWhenIdle` (6907) only acts on `reversedOnArrival`, so nothing re-stands
  the train.
- The next `captureFromLayout` (`AutonomySession` 3902, line 82-86) writes the copy's side - W - into
  the setup as the train's facing.
- The next dispatch, by hand or by autonomy, is a path from the reverse copy: it runs west. The
  locomotive's own direction is unchanged, so it drives EAST, off the route it holds and onto track
  nobody has locked. `ReversalPolicy`'s javadoc describes precisely this hazard for "keep direction" at
  a compulsory turn; it now exists at a may-turn square for the answer the operator was promised.

**The mirror case is already repaired, which is what makes this one visible.** A train that arrives at
the PLAIN copy and answers "turn" goes through `reversedOnArrival.put` -> `faceTheWayItCameIn` ->
`moveOntoFacingCopy`, which re-stands it on the sibling copy that faces the way it came in. The
"turning copy + keep" case has no equivalent. `core.testTheArrivalHonoursTheAnswer` asserts only the
boolean from `turnsOnArrival`, so it is green over this.

**Reachable by.** MT-368's own steps 1-2, run against the working tree: send a reversible train by hand
to BottomMainB, answer "keep direction", then send it anywhere. Also every non-reversible locomotive the
MT-367 fix newly admits to a may-turn square (`hasAWayThrough`), because the path it is admitted on is
the same reverse-copy path - and for that train "keep direction" is the only sensible answer.

**Direction of a fix, for whoever takes it.** The copy has to follow the answer, not only the decoder.
Either re-stand a non-turned arrival on the sibling copy whose side matches the way it is actually
pointing (the same `moveOntoFacingCopy` the turned case uses, with the entry side reversed), or decide
the destination COPY at departure from the prompt's answer - `forJourney` already has the answer before
`executePath` is called - and, for a train that cannot reverse, prefer the plain copy in
`distinctDestinations` rather than whichever `HashMap` hands over first.

---

## B - incorrect results or refusals in specific configurations

| id | status | where |
|---|---|---|
| PRW-B1 | fixed | `Layout.whyABerthCannotHoldIt` |
| PRW-B2 | fixed | `HomeStaging.firstClearRoute` / `passesTheTailsOfTrainsThatHaveNotMoved` |
| PRW-B3 | fixed | `AutonomySession.facingOf(String, Layout)` and its two callers |
| PRW-B4 | fixed | `AutonomySession.walkBackFrom` against `Layout.walkStandingTrains` |

### PRW-B1 - an unmeasured approach makes a parking berth refuse every train that has a length

**Where.** `src/org/traincontrol/automation/Layout.java` `whyABerthCannotHoldIt` (about 7857-7907),
called from `isPathClear` at 2667 in every tier.

**What happens.** The claim loop spends the train's length over the approach's places from the berth
back: `claimed.add(...); left -= max(0, span); if (left <= 0) break;`. On an approach whose places all
measure 0, `left` never moves and EVERY place is claimed, back to the approach's start. Any symmetric
lock partner then matches and the send is refused with
`autolayout.errorBerthWouldFoulAnotherRoad`.

**Why it is the wrong answer and not merely a strict one.** The method's own contract says its claim
is *"the same arithmetic the tail walk uses"* so that what it refuses and what `walkStandingTrains`
would block *"are the same stretch of railway"*. They are not here: `walkStandingTrains` has THE
MEASUREMENT RULE - `if (segment.getLength() <= 0) break;` - and claims nothing on an unmeasured segment,
on Adam's ruling *"if no length specified, just stop there."* And `whyTooLongForThisRoute` declines to
judge the same approach (`room == null` -> continue). So the berth is refused for fouling a road that,
once the train stood there, the standing rule would not block - and the message names that road.

**Reachable by.** This is the operator's configuration today. Three tiles are measured on the whole
railway; 12 parking berths and 8 other manual-only squares are not, and the parking berths sit on
ladders where every approach shares switch tiles with its neighbours. Any locomotive with a train
length set (EN57-203, 75 407 DB and 2-8-4 3505 SP have had one this week) is refused every one of
those berths by hand, and - through PRW-B2 - has its Return Home plan refused at the first move into
one. Before this change those sends were accepted, because the room rule alone was asked and it
declines to judge.

**Remedy shape.** Say nothing when the approach has no measured place at all (return null when the
sum of `spans` is 0), which is what the rule's two neighbours already do; a partly measured approach
can keep the refusing-side claim it has.

### PRW-B2 - the staging planner and the runtime now disagree in both directions

**Where.** `src/org/traincontrol/automation/HomeStaging.java`: `coveredAtStart` (125), the room test
at 1038-1041, `passesTheTailsOfTrainsThatHaveNotMoved` (1204-1250).

**What happens.** Three rules changed in `Layout` today and none reached the planner:

1. The shared-metal sweep narrowed to PLACES (`tailLiesOn`). The planner still refuses on whole covered
   edges (`coveredAtStart` is `edgesCoveredByStandingTrains()`, and 1224-1235 is the old whole-edge
   sweep). So the planner declares unreachable what the runtime now allows - OB-207's own scenario,
   `Tunnel -> BottomMainA` past EN57-203's one-unit tail, is still IMPOSSIBLE to a return-home plan.
   The planner's own comment names the rule: *"a proof may be looser than the search it guards, never
   tighter."*
2. The platform relaxation in `whyTooLongForThisRoute` (a train as long as its whole approach may stand
   across the points at an auto-destination). The planner asks `measuredRoomAtTheEndOf` directly at
   1038 and refuses the same platform. Tighter again.
3. `whyABerthCannotHoldIt`. Its javadoc says *"Pure, like whyTooLongForThisRoute beside it, so the
   staging planner can ask the same question rather than carry a copy."* The planner does not ask it -
   `grep` finds no caller outside `isPathClear`. So a plan can send a long train into a berth the
   runtime then refuses at the first move, which is the failure the planner's own OB-184 comment was
   written to prevent.

**Reachable by.** Any Return Home with a train that has a length, on a railway with a parked train
(1) or with measured platforms (2, 3). MT-370 is in the same round.

### PRW-B3 - `facingOf(loc, running)` reads a stale record and can flip a train the ruling says must not flip

**Where.** `src/org/traincontrol/automationui/AutonomySession.java` `facingOf(String, Layout)`
(5861-5887); callers `GraphLocAssign.commitAndRecord` (225) and `headingOfTheArrivingTrain` (486), and
the no-path arm of `facingByPath` (5743, new today).

**What happens.** The CONF-B1 overload asks the running layout WHERE the train is and then returns
`getFacing(where)` - the SETUP's stored facing for that square. `behaviour.md` 6a, quoted at
`reconcileFacingWhenIdle`, says that record is stale at exactly this moment: a run moves trains and
nothing writes where they ended up back to the setup until a capture. The square's stored facing is
whatever was last written there - a previous occupant's hand placement, or a previous occupant's turn
written by `faceTheWayItCameIn`.

Concrete: Y is sent to BottomMainB and turns, so `faceTheWayItCameIn` writes B = W. Y leaves. X arrives
at B on the plain copy, facing E, with no capture in between (none of `captureRunningLayout`'s six
callers is on this path). The operator assigns X to C in the graph window: `heading` = `getFacing(B)` =
W; `facingAfterAPaste(held, W, ...)` keeps W where C can hold it; X is recorded facing W and is pointing
E. That is the flip today's ruling on MT-377 forbids - *"as long as the direction isnt flipped"* - and
the new deterministic no-path arm of `facingByPath` inherits it by the same call.

`facingOnTheRailway(square, running)` (5993), fifty lines below, reads the copy the train is standing on
and is the source both callers mean.

**Not reachable through the diagram paste's main arm**, which walks the graph (`walkTo`) and answers
from a copy; only its fallback and the graph-window door read this.

### PRW-B4 - the drawn tail and the guard's tail are offset by the standing square's own length

**Where.** `src/org/traincontrol/automationui/AutonomySession.java` `walkBackFrom` (5395-5468) against
`src/org/traincontrol/automation/Layout.java` `walkStandingTrains` places loop (6114-6140). Since
commit `3a261a3a` the grey is `tilesCoveredByStandingTrains`, so this walk is now both the orange line
and the grey.

**What happens.** The two walks charge different squares for the same train:

- `walkStandingTrains` spends the standing square FIRST (the arriving edge's places end with it;
  `fromTheEnd` claims `ids[size-1]` and subtracts its span), then the path tiles back.
- `walkBackFrom` starts at the standing square, never subtracts its length, draws the path tiles back
  subtracting each, and then subtracts the FAR endpoint's length (5462). The comment there justifies it
  by *"GraphReducer builds an edge's length the same way - the path plus the square it arrives at"* -
  but the square the arriving edge arrives at is the STANDING square, not the far one. The analogy is
  inverted.

So the picture lies `len(standing) - len(far end)` further back than the guard's claim. On the figures
MT-371 step 4 asks Adam to set - TunnelLongPark (10,9) = 2, 10,10 = 2 - a two-unit train claims `{10,9}`
in the guard and is DRAWN lying across 10,10. He will see a train that fits its berth drawn spilling
out of it, on the same screen the step says should read "accepted".

**Severity.** Nothing physical follows; the guard is the one that refuses and it is right. It is B
rather than C because the picture IS the affordance for the guard (`guard-and-affordance-same-question`),
and this project has treated picture/guard disagreement as a defect every time it has met one (MT-278,
MT-309, OB-208, MT-373).

---

## C - narrow, cosmetic, or a remedy that is not shown

| id | status | where |
|---|---|---|
| PRW-C1 | fixed | `LayoutLabel.paintComponent` -> `TileAnnotation.paint` / `TileOverlay.paintRun` |
| PRW-C2 | fixed | the two hand-driven send doors |
| PRW-C3 | fixed | `Layout.walkStandingTrains`, turned-train hop |

### PRW-C1 - on a double curve the "already faded" flag is tile-wide while the fade is per road

`LayoutLabel.paintComponent` passes `refused` to both `annotation.paint` and `overlay.paint`. On a
double curve `theRoadToFade` fades only the covered road, but `refused` is still true for the square, so
the annotation's `DIM` wash under the arrows of the OTHER road and the overlay's `LOCKED_WASH` under a
route holding the other road are both skipped. The held road still gets its grey line
(`colourOf(LOCKED)`), so it is legible; it just loses the wash MT-373 says it should have. Cosmetic,
confined to double curves carrying a tail on one road and a lock or arrows on the other.

### PRW-C2 - a berth refusal reaches the operator as "check log"

`AutoLocomotiveStatus` 1110 and `LayoutRightclickAutonomyMenu` 1295 pre-check the chosen path with
`whyTooLongForThisRoute` so the refusal is a sentence. Neither asks `whyABerthCannotHoldIt`, so a send
refused by the new rule goes into `executePath`, fails in `isPathClear`, and the door shows
`autolayout.ui.autoFailedCheckLog`. The sentence (`errorBerthWouldFoulAnotherRoad`) exists and is in
the log. `error-must-have-a-remedy` applies: the remedy is in the message and the door does not show it.
(The why-not window at 4845 is fine - it reads `lastError`.)

### PRW-C3 - the places walk over-claims for a turned train

In `walkStandingTrains`, when the first hop is an OUTGOING edge (a turned train, `fromTheEnd` false)
the places are spent from the first path tile and the standing square is never charged, so the claim is
one square's length too long; on a further hop back through an INCOMING edge the junction square is
charged again. Both errors are on the refusing side and need a turned train longer than its first edge.
Recorded so nobody reads the "same arithmetic" comment as exact.

---

## D - not defects: clean checks and things that look wrong and are not

| id | status | subject |
|---|---|---|
| PRW-D1 | clean | `Layout.getNeighbors` shuffle, all eleven callers |
| PRW-D2 | clean | consist function buttons |
| PRW-D3 | clean | the ellipsis rule |
| PRW-D4 | clean | the other `isTerminus()` sites |
| PRW-D5 | clean | MT-364: `unmeasuredAfterTheLastSwitch` and `reversalsWithoutLength` |
| PRW-D6 | clean | `places` end to end |
| PRW-D7 | clean | the platform relaxation's safety claim |
| PRW-D8 | clean | the editor's one-argument `facingOf` |
| PRW-D9 | clean | smaller working-tree changes |
| PRW-D10 | not new | `whatTheOperatorMayChoose` on the event thread |

**PRW-D1.** Inside `Layout.java`: 2081 and 2096 are universally quantified over the list, 2959 asks
`isEmpty`, 3824 is `bfs` picking a journey (the shuffle's purpose), 8448 is an existence search. Outside:
`HomeStaging` 955 builds a plan (a journey), 1752 is an existence search; `AutonomySession` 5541 and 5620
ask `isEmpty` (and the `departable` list it feeds is ordered by `copies.entrySet()`, not by the
neighbours); 5725 is today's `walkTo`, which runs the whole breadth-first walk and decides on
DISTANCES, so the shuffle cannot reach the answer; `LayoutRightclickAutonomyMenu` 1025 and
`TrainControlUI` 6737 ask `isEmpty`. The sweep holds.

**PRW-D2.** `repaintLoc` enables buttons `[getNumF, drivable)` with text and no icon; the selected-state
loop at 12152 already covered `[getNumF, NUM_FN)` and `MarklinLocomotive.getF` (1380) answers those from
the members, so the extra buttons are not stale. `ProcessFunction` -> `fireF` -> `setF`, and
`MarklinLocomotive.setF` (930) accepts `< drivableFunctionCount()`. The F20+ tab follows `drivable`.
Guard and affordance ask the same question.

**PRW-D3.** `theListWasCutShort(paths.size() + other, shown, other)` reduces to
`paths.size() > shown`, evaluated when `++shown >= min(MAX_PATHS, paths.size())`: it fires exactly when
`MAX_PATHS` rows are up and more ordinary destinations remain, and never when everything fitted. The
`break` after adding the item is right.

**PRW-D4.** Looked for the fifth site at every remaining `isTerminus()` call. `Layout` 2336 (a terminus
may only end a path) is correct for a turning copy - passing through it mid-path would be a reversal
without a stop. 3940 and 4201 exclude the turning copy from autonomy's candidates for a non-reversible
train, and the plain copy remains, which is the intended split. `barredFromAutonomy` 4611 is per copy
and its statement is true per copy. `HomeStaging` 1081 and 1790 do not expand a turning copy, correctly.
`AutoLocomotiveStatus` 162's dash is per path end and `whatTheOperatorMayChoose` filters after it. The
fifth site is PRW-A1, at the arrival.

**PRW-D5.** `unmeasuredAfterTheLastSwitch` now answers empty as soon as the stretch has ANY measured
square, which is what `roomAfterTheLastSwitch` already binds on (*"only indeterminate if the entire
logical segment has length 0"*); `reversalsWithoutLength` keeps the reversal square's own entry only
where no edge arrives. The two now say the same thing.

**PRW-D6.** `placesAlong` lists the path steps (endpoints excluded by `getPath`'s contract) plus the far
endpoint, each with `lengthOf`, so the lengths sum to `sumLength(path) + lengthOf(end)` = `getLength()`
by construction. `deriveLocks` intersects path steps only, so `tailLiesOn` never widens the shared-metal
relation - it can only narrow within it. `Edge.setPlaces` refuses mismatched lists and the JSON reader
skips entries without `at`, keeping ids and spans in step. A file without `places` keeps the whole-edge
answer at every reader.

**PRW-D7.** The relaxation in `whyTooLongForThisRoute` only ever `continue`s past a refusal, and its
safety rests on the tail walk claiming the switch after the train arrives. That holds: the arrival writes
`arrivedFrom` from `entrySideOf` (7174) before any reversal, so the first hop of `walkStandingTrains` is
chosen by side rather than by the fork rule, and the switch's place is claimed as long as the approach is
measured - which the relaxation requires (`getLength() > 0`).

**PRW-D8.** `AutonomyEditorPanel` 4568 reads the one-argument `facingOf`, which is the setup only. It is
fresh there because the editor is opened after `captureRunningLayout()` (`TrainControlUI` 5146), so this
door is not an instance of PRW-B3.

**PRW-D9.** The blocker list's sort by `describeTile` and the 120-grey foreground; the posted
`applyRememberedCaptionMode` before `refreshGrid`; `debugArea.setEditable(false)`; `TRAIN_MARK` matched
to `POINT_INACTIVE`; `GraphReducer.Place`; the message-bundle key for the berth refusal in all eight
languages. Read, nothing to report.

**PRW-D10.** `whatTheOperatorMayChoose` calls the `synchronized` `isOfferableToOperator`, and on the
fallback path that is the event thread taking the railway's monitor. The comment says that path was
already taking it through `getPossiblePaths`, and the regression class carries the allowance. Not new,
not worsened; noted because it is the AB-BA shape OB-192 was about.

---

## What this pass did NOT cover

- **The bodies of the 43 commits** beyond what the working tree depends on. The commit list was read;
  the diffs were consulted for `28b20a65` (OB-204/205/206), `3a261a3a`/`1e04b0c6` (the grey and the
  double curve) and `d0aaa423` (arrival sides) only as far as the working tree's methods reach into
  them. The reversal-bounds work (`581c0668`, `30ac3597`), the menu-bar scale commits, the CAN reopen,
  the page-rename fixes and the language-fit test were not reviewed.
- **Tests.** None run, by instruction. The twelve new classes were opened only for their headers, to
  learn what they pin; whether they are red or green, or whether any skips everything, is unknown here.
- **`AutonomyBuilder`** beyond the copy emission and the `places` block; **`GraphReducer`** beyond the
  five methods named above; **`LayoutEditor`** beyond the posted block; **`AutonomyCompanionStore`**.
- **The room rules' arithmetic** (`measuredRoomAtTheEndOf`, `roomAfterASwitchOnTheWay`) - read for
  their contracts, not re-derived.
- **OB-209** (the timetable capture timeout) - not looked at.
- **The Central Station files** under `cs2_sample_layout/config/` other than the two autonomy JSON
  files, and nothing in them was interpreted beyond counting.
- **Concurrency** of the new code beyond PRW-D10: `whyABerthCannotHoldIt` and the places walk run under
  the same monitor their neighbours do, and no new field is read from another thread; not traced
  further.
