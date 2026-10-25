# The tail and Atomic Routes

Part of the behavior reference: [`../behaviour.md`](../behaviour.md) is the index.  Sections keep the numbers and headings they had in the single file, so a citation such as "behaviour.md §5c" still resolves.

### 5c. The tail: track a standing train is lying across

> *"Those edges it reaches need to be considered blocked ... bottommainc should currently be blocked
> since a train of length 4 is standing at bottommainb, which has a length of 1 leading up to its
> switch."* - Adam

#### The walk

- **Edges, not points**: *"because the points are technically unoccupied. But the blocked edges
  should prevent routing to the covered points."* A station reachable only across covered track becomes unreachable as
  a consequence, not by a second rule that could disagree.
- The walk starts at the side recorded in `arrivedFrom` and follows track **regardless of direction**: a tail fouls the
  rail whichever way traffic runs.
- **It stops at a fork.** *"If entering a switch from the fork direction where it splits, just end
  locking at the switch and call it a day."* One way back means the tail certainly lies there; several means the graph
  cannot say which.
  - **Unless the train was driven there** (MT-333/MT-335; Adam: *"Follow its last route"*). A train that arrived by a
    run remembers the route (`Point.arrivedAlong`), and past a junction the walk takes the road that route came in on.
    `core.testATailFollowsTheRouteItCameIn`, with a placed train with no road as its control.
  - **The road is kept** (Adam: *"Then state will always be fully consistent."*): saved with the point (`arrivedAlong`,
    as start/end name pairs), read back on load, carried across every rebuild and captured into the setup with the
    placement and the side. A road naming a rail the layout no longer has is dropped whole, and the fork rule applies
    again. `core.testATailRouteIsKept`, `regression.testAPassingTrainMayStandAcrossThePoints`.
  - **A train placed by hand is asked** (Adam: *"pick from a list and select the farthest sensor
    the tail of the train recently crossed.  Also, ideally in the autonomy editor, it should allow the user to
    click to select as well."*). The whole train lies between the farthest sensor its tail has crossed and the square it
    stands on, so that sensor names the road.
    - **When it is asked.** When a train is pasted, placed from the right-click menu, or set in the locomotive dialog,
      only where the tail lies past a junction with two roads back that put it on different track. Two roads are two
      pieces of METAL, compared by the places the rail runs over (OB-276: RampDown's southbound lane and its northbound
      turning copy both leave south by one rail, so they are one road; a balloon's two ends are two).
    - **A tail that has passed the switch the roads part at is asked about whether or not it has reached a sensor**
      (MT-477; Adam: *"when set to lenth 3, the tail always follows switch 51 turned, rather than facing
      straight toward rampdown ... technically that length should qualify for the prompt"*): the last square two rails
      share holds the points, and a tail reaching into it lies on one leg or the other. Each road it can lie on is
      offered, by its sensor where the tail reached it and as **towards X (not reached)** where it did not. A tail that
      ends before the points covers the same squares on every road, and is not asked.
      `core.testATailPastASwitchIsAskedAbout`.
    - **Not asked, or closed without an answer: the road the train had is kept** where it stays on the same square with
      the same side (the question's **Cancel**), and no answer forgets it (Adam: *"drop
      not known, keep cancel"*).
    - **The list** starts on the road the train has; with none, on the one sensor nearest the back of the train where
      exactly one qualifies, and on nothing otherwise (FR-088; Adam: *"so the user can just click OK if appropriate"*). A
      way towards a sensor the tail has not reached is never where it starts. It offers each sensor, nearest first, with
      **OK** and **Cancel**; Cancel, or OK with none chosen, keeps the fork rule. A sensor exactly the train's length
      back is offered: the tail has reached it (OB-226). Only roads a train can drive in on are offered (OB-227; Adam:
      *"that isn't a realistic path"*).
    - **Where it is put.** The same list is in the right-click menu under **Farthest sensor the tail crossed**, and in
      the autonomy editor **Pick on the diagram...** outlines the sensors to click. **On the main window the question
      itself is put on the diagram** (FR-100; Adam: *"highlight possible squares on the diagram
      and ask the user to click one.  only show the list if there are options on another page"*): the sensors are lit,
      a click on one answers, with **Cancel** in a small window. The list is used where a choice is on another page or
      not drawn, and where an editor window is open (its squares do not take the click); it then opens from the editor,
      in front of it (MT-575), while the editor is showing, and from the main window while the editor is minimized. The
      rest of a double-click on the sensor that answered is the question's, and the paste reads everything about its
      landing before the question waits, because the window stays live meanwhile.
    - **An answer is written only where the placement still stands when it comes back**: the square's copy on the
      railway then running still holds the train with the side and road it was put down with (a railway rebuilt in the
      wait counts, its new copy being the one asked), and the setup is still the window's. **So the answer follows the
      train** (Adam: *"Follow the
      train."*): another configuration loaded in the wait, whose copy holds the train as it was put, gets it; closing
      the track-diagram editor, which replaces the session, drops it, and a door whose setup was let go saves nothing. An
      editor opened in the wait drops it too, since its Cancel would take the answer back out, as every main-window door
      that writes the setup refuses while an editor is open. A dropped answer (Not known included, a Cancel not) is
      logged, naming the train and the square; the facing was written before the question, so only the road waits.
      `regression.testTheTailIsPickedOnTheDiagram`.
    - **The offered sensors are a suggestion**, worked out from the measured lengths of the roads back; what blocks
      track is still this walk, reading the road chosen. `core.testTheTailCrossedQuestion`,
      `regression.testTheTailCanBeGivenInTheEditor`.
- **It stops at unmeasured track.** Only positive lengths are determinate.
- **The square the train is standing on is track, and it is spent first** (OB-278; Adam: *"the 2 length tile with
  the s88 consumes 2 units of the train"*, and asked where, *"Everywhere"*). A train no longer than its square blocks
  nothing behind it; a longer one spends the square and lies back over the rest. Every walk of where a body lies does
  this alike (the tail walk, the berth rule, the orange line) and so does the room rule. **The allowance is the
  station's SIZE**, the maximum train length typed on it, which is what `whyTooLongForThisRoute` asks first. Adam: *"if
  the segment length is shorter,
  more should be blocked. The station size is an allowance, not a length"*. On his railway each berth's length is written
  on its sensor square, so reading the allowance as the square's own length would put every standing train's tail over
  the switch behind. `core.testAStationsSizeIsAnAllowance`.
- **A train part-way along a run is walked the same way from its last MILESTONE** (OB-244, MT-438): a milestone is
  ordinary block and the body lies over it. `core.testARunningTrainHasOneTail.testAMidRunMilestoneIsNotAnAllowance`.
- **The first hop takes the copy of the rail the train ARRIVED along.** A rail is two edges, one per direction, and an
  edge's places are the path plus the square it arrives at, so only the arriving copy carries the train's own square.
  Where there is none (a square a train has been turned on) the other is used.
- **With no road to follow, a fork right behind the platform stops the tail at the switch** (MT-477; Adam: *"stop
  at the switch"*): where the rails arriving by the recorded side part before the train's length is spent (at
  BottomSecondary, switch 51) and the train was placed with no answer, the squares they share are claimed up to and
  including the switch, and nothing past it.
  `core.testATailPastASwitchIsAskedAbout.testWithNoAnswerTheTailStopsAtTheSwitch`.

#### Whose tail blocks whom

- A train never blocks itself: pulling forward off its own tail is how it leaves. **But its own tail is not an answer
  for anybody else's** (OB-285; Adam: *"Add the refusal"*). A train turned where it stands (on the throttle, with the
  Facing menu, or at a square trains may turn at) finds its own tail on its way out, and the other trains are asked
  about there too, by the railway and by Return Home. A train turned at Tunnel must not be cleared south through the
  points at column 7 with a train in TunnelRightPark lying across them. `core.testATurnedTrainIsNotSentIntoAnotherTail`.
- **Except round a loop into its own tail** (OB-294; Adam: *"make sure the model factors in whether the
  train will clear the area before it crosses over"*, and *"the check should pass if the train would be gone (i.e. if
  that one was only length 4, for example)"*). **The head may come back to a place only once the tail has left it.** The
  body as it stands is the one the walk claims; the route's own places are timed as the head runs them. The figure for a
  return is the longest train clear of the place in time: for a place the body lies on, the measured track the head runs
  before it comes back plus the body in front of that place; for a place the route ran over, the measured track run since
  the head left it. A longer train is refused, and the refusal names the tightest figure on the route. **Only measured
  track binds**: a return is judged only where the way round is measured since the head left it (some length run on it,
  or every place on it answered 0, OB-300); a way round nobody measured is not judged. Where some of it has no length the
  refusal counts what Mass Assign Lengths would ask a length for on the way round. The build marks, place by place, the
  piece, switch or shared square the editor still asks for, keyed by the answer it is asked in (its piece, or all of its
  page's switches, or all of its page's crossings; OB-297; Adam: *"Locations of switches are known."*), and the note
  counts those answers once each, so it asks for exactly what the editor offers. A configuration built before the marks
  counts whole legs. **After a turn the body is ahead of the train** and moves with it, so the question starts again.
  Asked by every tier, Why not Moving? and Return Home's planner. A hand door does not offer a destination the rule
  refuses on every route; the sentence shows where a menu built before the length changed is used, and in Why not
  Moving?. On his railway the way round from BottomSecondary by the tunnel back to row 11 measures nine units, so a
  four-unit train goes and a twenty-unit one does not.
  `core.testATrainDoesNotRunIntoItsOwnTail`, `core.testTheOwnTailArithmetic`.
- **Every other train's tail is asked about, not only the one the record kept.** The claims record one train per place,
  and two tails fouling one switch from its two legs both claim its square, so a train's route is judged against every
  OTHER train, walked without it, and Return Home keeps each train's claims apart. `core.testATailIsNotHiddenByAnother`.
- **One tail per train, and a running train's starts at its head** (OB-243; Adam: *"the
  tail is certain at departure and shouldn't change.  You also know which way the train went ... Just
  unlock the rest of the diagram once the tail by length is far enough away"*). A locked path makes a running locomotive
  the occupant of several Points at once; the walk is made once per locomotive, so no tail is drawn at a destination not
  yet reached or a square long left (OB-242). A running train is anchored at its last reported milestone and spends its
  length back along the road it has driven; a standing train where it stands. `core.testARunningTrainHasOneTail`.

#### Shared metal

- **And the track it shares metal with is closed too, which is the anti-collision rule at a switch.**
  Adam: EN57-203 *"is allowed to traverse a blocked/shaded switch (60) to get from
  TunnelLeftPark to BottomMainC, even though it should not be possible."* Coverage is recorded per EDGE, and a train
  lying across one pair of a switch's arms fouls the other pair, a different Edge. So a path is refused over any edge
  that shares metal with a covered one: the lock-edge relation `GraphReducer` derives from shared tiles. It is told
  apart from an FR-001 occupancy restriction, which uses the same list, by **symmetry**: sharing a tile is mutual,
  a restriction one-directional. Known limit: two stations each holding the other back are symmetric by coincidence,
  and this over-refuses there. `core.testACoveredSwitchClosesTheOtherRoad`, on `test/layouts/single-switch`.
- **The copies of one square are one piece of metal, except where the square is two.** The build groups a square's
  copies under a `block`, so a train on the northbound copy makes the southbound copy read occupied. The TILE says which
  copies those are, not the s88, since different places share a sensor on a real layout. On three types (OB-238) Adam:
  *"it is two pieces of metal. imagine two parallel tracks simple
  appearing on one tile for visual convenience. two distinct, not connected paths."* DOUBLE_CURVE,
  FEEDBACK_DOUBLE_CURVE and OVERPASS are grouped per ROAD instead, and a train on one road leaves the other free.
  `core.testTheDiagramDrawsWhatTrainsMayDo.testEachArcOfADoubleCurveIsItsOwnPieceOfMetal`.
- **But only over the part of it the train is actually lying on** (OB-207; Adam: *"75 407
  DB cannot go from Tunnel to BottomMainA even though it should be able to"*, at a train length of one). A one-unit train
  parked at the end of a twelve-tile run must not foul every road sharing any tile of it. The build
  writes each edge's **places** (the location identifiers the lock relation is derived from, in order, each with what it
  measures); the tail walk spends the train's length across them, and the shared-metal rule asks whether the tail lies
  on metal *this* edge runs over.
  - The **direct** case stays whole-edge on purpose: a path uses all of its own edges, so a tail anywhere on one is in
    the way.
  - An edge with no places (a hand-written configuration, or one written before 3.0.0) keeps the whole-edge answer.
  - `core.testAShortTrainDoesNotBlockTheWholeRun`, with a control that a train long enough to reach the shared metal
    still blocks it.

#### What the diagram draws

- **Two marks, and they say different things** (Adam: *"'train is here' should also mean
  'track is blocked' - that is the whole point. it's the same as greying out edges, just in a
  different way."*). **Orange** is where a train IS; **gray and not orange** is track that train's presence has made
  unusable; neither is free.
- **A run is drawn in the stations' blue ahead of the train and dark gray for the track it has driven and still
  holds, with white arrowheads edged in dark gray** (FR-106: Adam's palette, kept in one place, `DiagramColours`).
  Track a non-atomic run gives back behind the train is drawn as nothing; under atomic routes it stays until the run
  ends.
- **The strip above the track diagram says "Setup changes not applied yet"** while the setup has changes the railway
  has not taken (an edit made as a run started, or one that leaves the setup unable to build while the last railway that
  built stays loaded); its tooltip says they apply at the next load, once the setup has no errors (Adam: "Add
  the setup notice, but make it brief").
- A train is drawn as an **orange line along the track it is standing on**, **on the track diagram viewer only**, and
  while it runs along the track its length still covers, over its route (FR-106). An editor is where the railway is
  arranged, and what happens to be standing on it while you arrange it is a fact about right now rather than about the
  drawing.
- Blocked track is drawn by **fading the square itself to 40%**, **whether or not anything is running**, so a blocked
  curve is still legibly a curve. Adam: *"I want the shading to instead be the same tile with more transparency exactly like
  what happens when edges are locked in autonomy mode."* The fade is a composite set before `super.paintComponent`,
  because nothing drawn afterwards can make what is underneath transparent; everything autonomy draws on top, the train
  line included, is at full strength. `LayoutLabel.BLOCKED_ALPHA` is the number.

  **And it is applied once.** A square blocked by a standing train and locked by a running train's route is drawn at the
  same 40% (OB-212; Adam: *"keep one level of
  opacity, dont stack."*). The route's and the annotation's washes are told the region already faded (MT-375,
  `ui.testTheWashDoesNotStack`).

  **It is drawn at idle too.** `Layout.isPathClear` sweeps the covered edges in every tier at every time, because a tail
  lying across the rail is physical, so at idle a hand send across a parked train's tail is refused
  (`errorTrackCoveredByStandingTrain`) and the diagram must not paint that track as free: the guard and the affordance
  ask one question (OB-057/OB-090). Adam: *"yes, this greyout should appear at idle and
  be regenerated if a placement or train/track length is changed."* `ui.testTheGreyAppearsAtIdleToo`;
  `ui.testBlockedTrackIsGreyWhileAutonomyRuns` is the running case.

- **And it is regenerated by every operator gesture that changes it with no train moving** (Adam: *"make sure the shading and orange repaints on
  any autonomy or train/track length edit."*). A station flag, an arrival side, a one-way direction and a reversal
  marking all change which edges exist, and so what a standing train covers. **One door carries the general case**:
  `rebuildRunningLayoutFromSetup` ends in `blockedTrackChanged()`, and every setup change goes through it; the refresh
  diffs the two sets and repaints only the squares whose mark changed. A **placement** also reaches it through
  `updateVisiblePoints` at each placement door (Control+X / Control+V / Delete, the right-click Place and Remove items,
  `GraphLocAssign`); a **tile length** through the setup rebuild it needs anyway (an edge's length is baked in by
  `GraphReducer`); a **train length** through `TrainControlUI.blockedTrackChanged`, since
  `Layout.edgesCoveredByStandingTrains` reads `getTrainLength()` every time it is asked.
- **The line follows the road, not the square**, which is why a wash was the wrong way to say *"a train is here"*. Adam:
  *"instead of shading the entire tiles, we need to draw a line (let's say in orange) to show that
  the train is there. graying makes it look confusing on double curve tiles."* A double-curve tile carries two roads; a
  square gray and not orange claims no train on either.
- **The line is drawn on every square the train lies over, the sensor squares included**: the square a train stands on
  is always a sensor. Adam: *"when we draw orange lines, they don't overlap with sensors"* (OB-277). Each sensor the body
  reaches is drawn along the road facing the track just walked, which names one arc even on a double curve.
  `regression.testTheWashIsNoLongerThanTheTrain.testTheSquareTheTrainStandsOnIsOrange` and
  `testASensorTheTailLiesAcrossIsOrange`, the second with the train one unit too short as its control.
  **A route tile is drawn too** (OB-279): it carries what the track beside it carries, its road named by the sides it
  joins. `core.testRouteTilePlacement.testARouteTilesRoadCanBeDrawn`.
- **The line is as long as the train**: square by square from where the train stands, each paying its own length
  (MT-309).
- **The marks survive a highlight.** Neither is baked into the image; both are applied on every paint, and when an
  accessory's flash ends the square is asked again, because the train may have moved meanwhile.
- **Both are refreshed together, and only where they changed.** The whole diagram is never rebuilt for either (MT-334:
  the page flickering).
- **And never on the event thread** (OB-192). Both answers come from `Layout.edgesCoveredByStandingTrains`,
  `synchronized` on the `Layout`, the monitor a dispatch holds for the whole of `configureAndLockPath`, a sleep per
  accessory (and `repaintSwitch`, which a driving thread inside that monitor calls, must not be `synchronized` on the
  window). Adam: *"starting autonomous operation ... makes the UI
  unresponsive. Trains still run, but nothing is repainted, and controls are stuck."* So the pass runs on a worker and
  the marks land a beat later.
- **WHAT IS DRAWN IS WHAT IS REFUSED, once both marks are counted.** The gray is the squares standing trains CLAIM,
  `Layout.placesCoveredByStandingTrains`, which is what `isPathClear`'s shared-metal sweep refuses. Adam (OB-208):
  *"orange shows where the train is, gray shows what's blocked."* And (OB-280): *"grey what routing actually refuses.  if
  the train doesn't protrude past the switch, there should be
  nothing else to gray."* A path crossing only the far end of a covered edge is NOT refused (OB-207), so the gray is the
  claimed squares, not every tile of every covered edge: the whole square where the place is the square (a train on one
  leg of a turnout blocks the other), one road where the place names a road (a double curve or an overpass), and the
  fade follows the gray's roads. A train that fits on its berth grays that square and nothing behind it.
  `AutonomySession.routesBlockedByStandingTrains`, `ui.testTheGreyAppearsAtIdleToo.testTheGreyIsWhatTheRailwayClaims`.

  **What is still not drawn**, and is explained in the "why not moving" view instead:

  - a covered edge's squares the train does not lie on, which routing does not refuse (OB-280);
  - a path refused because an edge it uses **shares metal** with a covered one: the square is grayed, but the *path* is
    not marked as refused.

  **And the answer has to be readable** (OB-191). The "why not moving" answer is shown in the strip across the top of the
  editor, which has a height floor (so messages coming and going do not move the diagram) and a height CAP; an answer
  longer than the cap is given the whole strip, so its scrollbar appears. A short message is still centered (OB-151);
  both rules are one statement in `AutonomyBanner.CentredButNeverTaller`.

  **And it is worked out on a worker, not on the click.** `Layout.explainDestinations` takes the railway's monitor and
  searches for every station, so the click captures the layout and the station index, says *"Working out the
  reasons..."*, and paints the report when it lands. Of two clicks in a row the later one wins. The editor cannot be open
  while autonomy runs (OB-047), so this is not a live freeze; it is on a worker anyway. Adam: *"I would rather take it off
  EDT. It's not critical now, but we want to
  avoid these pitfalls."*

**Known limits, deliberately.** A tail that really reaches past a fork, or across unmeasured track, is not blocked; nor
is the tail of a train whose recorded arrival side names a side no track now leaves its square by (a value left stale by
a diagram edit: the walk claims nothing, `Layout.walkOneTail`, OB-264). All three under-claim: blocking on a guess is
still a refusal, and stops trains that could have run.

#### Two rulings about the room sum

1. **A positive length counts, and that is deliberate.** An edge's length is the sum of `max(0, tileLength)` over its
   tiles, so one measured tile out of five reads as a measured segment, which under-counts and can refuse a train that
   fits. Asked whether an edge should count as measured only when every tile is, Adam: *"no, any nonzero length on a
   logical edge between 2 stations will count."* It errs towards refusing; the remedy is to measure the rest.

2. **A REVERSAL SPLITS THE RUN IN, AND THE TRAIN HAS TO FIT IN BOTH HALVES.**

   Asked whether *"the track segments leading up to it"* means up to the reversal or up to the berth, Adam:
   *"it can be either the reversal or the berth, depending on where switches are.  both need to be long
   enough."*  With the figures:

   > four-unit train in a three-unit berth with a two-unit approach: refused both because of the berth
   > (3<4) and the track length that potentially couldn't fit the train while reversing.  if the train
   > isn't reversing, then it should be accepted as long as the berth is long enough.

   > 8 unit train on a 9-unit runin would just be refused because 3<8.  if the max train length at the
   > berth was set to 8, we would be OK.  in short, your tests should consider both.

   | Where the train stands | The stretch it has to fit in |
   | --- | --- |
   | the berth, after turning | back from the berth to the reversal, or to the last switch, whichever is met first |
   | the reversing point, while it changes direction | back from the reversal to the last switch, or to the start of the route |

   The first is the walk's own stop (`Layout.measuredRoomAtTheEndOf`); the second is a square the train **comes to rest
   on**, so `whyTooLongForThisRoute` judges it as a berth. Where nothing on the route turns the train the sum is the
   whole run in: *"if the train isn't
   reversing, then it should be accepted as long as the berth is long enough."* `core.testNonReversibleTrains` holds
   both, each isolated by making the other stretch long enough, and reads the refusal's sentence. A shorter stretch with
   nothing measured in it is no information, not no room; `autosetup.ui.checkRunInShorterThanTheBerth` is the notice
   that asks for the berth to be measured.

#### The platform notice

**A STATION TOLD IT HOLDS MORE TRAIN THAN ITS TRACK MEASURES GETS A NOTICE.** Adam:
*"let's add an autonomy editor notice that alerts the user if a run-in is shorter than the berth length,
that way they can decide if it makes sense or not.  for example, a station of length 4 may have two
segments of tracks on either side of a switch, of length 2.  that is acceptable and its limitations are
understood."* `maxTrainLength` is what somebody typed and the measured room is what the track says; where the room is
smaller the typed maximum never binds.

- It fires per STATION, on the SMALLEST room any of its approaches has, with both numbers, the maximum first.
- The room is `ReducedEdge.getRoomAtTheEnd()`, what `Layout.measuredRoomAtTheEndOf` counts. **A notice quoting a number
  the refusal would not quote sends the reader to measure the wrong stretch.** So for a parking berth the number is the
  berth rule's where that stops first (at a crossing between the berth and its switch, or on a leg with no switch), and
  only where the rule refuses: something on the leg is measured, and another road runs over the square it stops at.
  With nothing measured before that switch or crossing the notice says nothing: where those squares were answered 0 the
  berth takes no train that way, a **warning of its own**, *"the
  length given for that track adds up to 0"* (Adam: *"Give it its own sentence as a warning,
  make it sound intuitive"*); where they were not, the half-measured warning names them.
- **At a platform, the figure a train is refused above, where there is one** (Adam: *"Add the
  refusing figure where there is one."*). A train longer than the room stands across the switch only as far as the
  measured route in holds it (FR-087), so the sentence's third number, given where it is under the stated maximum, is
  read off the railway the setup builds: back from the platform over every leg, through sensors nobody is started at, to
  a copy a train is started at or turns at; stopped at a leg nobody measured and on past one answered 0 throughout;
  never through or into a point switched off (though out of a station switched off round a standing train), never from
  or through another copy of the platform's own square; every leg together, so a loop is walked whole. Each way in gives
  the larger of its room and its route in, and the figure is the least. It is taken only over the ways in {3} is taken
  over (past a switch with something measured, or with a length from a turn), so it is never less than {3}, and a way in
  with no length refuses nothing and hides nothing. Where it equals {3} it is still given. On the frozen railway: TopMainR1Inter 3
  (TopR1ParkShort's four-unit train, MT-564), and LowerFront 4, from ParkingTrack12; none at Tunnel, BottomMainA or
  BottomInnerOtherside. `core.testAnAnsweredZeroIsNotMissing.testTheRefusingFigureIsTheRailwaysOwn`.
- An arriving edge crossing no switch is skipped unless trains turn round where it starts (the guard then walks on back)
  or, for a parking berth, a crossing on it ends the berth rule's room.
- Silent where either side is missing: no typed maximum is `checkNoMaxTrainLength`'s sentence, an unmeasured stretch is
  `checkReversalNeedsLength`'s, and a railway measuring no track has decided not to model lengths.
- A **NOTICE**, not a warning, by his ruling: his own example is a railway with nothing wrong with it.

`core.testThePlatformNoticeKnowsWhatATrainIsRefusedAt.testTheEditorSaysWhenAPlatformHoldsLessTrackThanItClaims`, on his
own 2 + 2 example and on the reversal branch.

#### The census

**Measured on the operator's own railway.** The room rule runs at every destination and in every tier (MT-262), so
whatever either ruling gets wrong, it gets wrong everywhere. `core.testTheRoomRuleCensusOnTheRealLayout`, in the battery,
searches a path for every ordered pair of station Points and asks each of six trains of its own, one to six units long,
whether the widened rule refuses it and the fence it replaced (`isTerminus() || isReversing()`) would not have. The six
lengths are the class's own (Adam: *"generate trains programmatically in the tests, and
give them semantic names"*).

| | |
|---|---|
| ordered station pairs | 1332 (37 destination Points, each against the other 36) |
| of those, routable | 956 |
| train lengths asked, the census's own | 1, 2, 3, 4, 5, 6 |
| journeys the widening NEWLY refuses | **505** |
| every one of them arriving at | BottomMainA (eastbound), BottomMainB (eastbound), BottomMainPost (northbound) - each measured at **ONE** unit of room |

The census runs on the railway frozen from Adam's measured layout (`test/layouts/live-snapshot`) with his own lengths and
station sizes taken off, so it is the three-tile experiment. His one-way marks, barred sides and the compulsory turn at
BottomMainC leave 37 destination Points; BottomMainC is a terminus, where a refusal is not new. The census walks the
railway's own route search, which does not extend a route through a terminus that is not its end, nor double back
through another copy of its own start or end (section 7; Adam: *"We
need to refuse both. A copy makes a cycle."*). No route turns at a measured square under these three tiles, so the turn
bound is held by the band, `core.testNonReversibleTrains` and `core.testATrainIsJudgedOnlyWhereItStops`.
`core.testWhichSquaresTheRoomRuleClosesOff`, on the same three tiles, finds no square that offers a longer train nothing.

The berths refused are the ones Adam named, with his number: *"bottommainb, which has a length of 1 leading up to its
switch"*, and MT-262's own report of `BottomMainA (eastbound)` offered *"with ONE measured unit of room behind it"*. A
layout with lengths over more of its track would be a different answer, found by running the census again.

### 5d. Atomic Routes, and the track a train is handed back

**A path is held whole until the run ends - unless Atomic Routes is off, and then each edge is given
back as the tail clears it.** Adam runs with `"atomicRoutes": false`, so this is his railway's ordinary behavior.

Giving an edge back early lets a second train follow a first down the same road. The question is only *has the tail
passed this edge*, and `Layout.tailHasProvablyPassed` answers it from how far the head has traveled since the end of
the edge and how long the train is.

**How far the head has traveled is counted to where the sensor that last answered saw it**: the edge whose sensor
answered, up to that sensor's own square. A sensor answers when the head ENTERS its square, so the square counts behind
the head only when the next sensor answers: the safe reading for a release (the walk that claims a running train's body
takes the generous one). A Point with no sensor is passed without waiting, so the track to it counts only once the next
sensor answers, and so does the track to a sensor that already read occupied when its wait began (one two Points in a
row share, or one another train stands on). `testASensorTwoPointsShareIsNotProofTheHeadReachedTheSecond`,
`core.testTrainTailClearsEdges.testAnEdgeIsHandedBackOnceTheTailHasPassedIt`,
`testASensorsOwnSquareIsNotCountedBehindTheHeadYet`, `testAnEdgeIsNotHandedBackBeforeTheTailHasPassedIt`.

**It is wrongly true in exactly two states, and both of them are ordinary.**

- **A path with no measured edge anywhere on it**: "how far has the head gone" is "no idea", read as clear.
- **A train whose length is 0**, what `Locomotive.trainLength` holds until somebody sets it: every edge is handed back
  under the train. **Mass Assign Train Lengths** on Bulk Tools (§5b, FR-094) asks every train autonomy would run that has
  none.

In either state the next dispatch could be routed onto occupied track. **So the railway is not allowed to run
non-atomic while either state holds**, asked at every door that could start a train or load a setting:

- the **Atomic Routes checkbox** refuses the gesture and says what is unmeasured;
- the **load door** (the editor's apply, OB-254) forces the setting back ON and logs the reason, because a file has
  nobody at it and refusing the load would make a configuration unopenable;
- the **five dispatch doors** (Start, Execute Timetable, Return Home, and the two hand dispatches) do the same, because
  a TRAIN's length is written on the live layout long after any file was parsed.

**What counts is whole paths and trains, not squares.** An unmeasured switch square does not count (one measured square
gives the edge a length), nor does a rail with measured track between it and every destination, because a path ends at
a destination; that is enforced where paths are BUILT, in `Layout.bfs` and `HomeStaging.firstClearRoute`. A rail is
counted once, not once per direction.

**Track answered 0 on purpose is measured** (Adam: *"0 lengths count
as measures, so non-atomic should be allowed"*). The checkbox, the load door, the dispatch doors and the escape all ask
`Edge.isMeasured` (a length, or every square answered 0; a route tile counts as answered). Such a path gets no escape,
so its edges are held until the measured track after them covers the train, or to the end of the route: the safe
direction. **Every other length rule asks the same question** (§5b): the gate and the escape have to ask one question, and so
do the route in and the tail walk, or a platform admits a train whose tail lies past the 0 on track nothing claims.

So the Unmeasured Track display in the editor is **not** the list to work from (it is about how far a tail reaches): the
refusal's own list is, in alphabetical order because the message shows only the first three. `MT-470` is the hands-on
test.

**A sensor is held for the train whose route runs to it until the rail to it is given back** (Adam: *"it
should be allowed once unlocked"*). The first time a sensor reads occupied ends every journey waiting on it, so no route
may end at, or pass, a sensor another train's journey is waiting on, nor one whose rail that journey has not yet given
back. A feedback double curve's two arcs are two pieces of metal with one contact (OB-238), and a gap between two axles
reads clear. A train still claiming its route holds its sensors as one under way does, and the claims are read before
the journeys, since the hand-over writes the journey and then takes the claim away. `core.testATrainIsDispatchedOnce`.
