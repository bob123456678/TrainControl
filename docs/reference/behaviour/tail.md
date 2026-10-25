# The tail and Atomic Routes

Part of the behavior reference: [`../behaviour.md`](../behaviour.md) is the index.  Sections keep the numbers and headings they had in the single file, so a citation such as "behaviour.md §5c" still resolves.

### 5c. The tail: track a standing train is lying across

> *"Those edges it reaches need to be considered blocked ... bottommainc should currently be blocked
> since a train of length 4 is standing at bottommainb, which has a length of 1 leading up to its
> switch."* - Adam

- **Edges, not points** - *"because the points are technically unoccupied. But the blocked edges
  should prevent routing to the covered points."* A station reachable only across covered track
  becomes unreachable as a consequence, rather than by a second rule that could disagree.
- The walk starts at the side recorded in `arrivedFrom` and follows track **regardless of direction**
  - a tail fouls the rail whichever way traffic runs.
- **It stops at a fork.** *"If entering a switch from the fork direction where it splits, just end
  locking at the switch and call it a day."* One way back means the tail certainly lies there;
  several means the graph cannot say which.
  - **Unless the train was driven there** (MT-333/MT-335; Adam: *"Follow its last route"*).
    A train that arrived by a run remembers the route (`Point.arrivedAlong`), and past a junction the
    walk takes the road that route came in on. `core.testATailFollowsTheRouteItCameIn` drives a real run
    through a junction and has a placed train with no road as its control.
  - **The road is kept** (WK7-B1; Adam: *"Then state will always be fully consistent."*). It is
    saved with the point (`arrivedAlong`, as start/end name pairs), read back on load, carried across every
    rebuild with the train, and captured into the setup with the placement and the side - so a restart,
    closing the autonomy editor or any setup gesture does not drop it. A road naming a rail the layout no
    longer has is dropped whole, and the fork rule applies again. `core.testATailRouteIsKept`,
    `regression.testAPassingTrainMayStandAcrossThePoints`.
  - **A train placed by hand is asked** (Adam: *"pick from a list and select the farthest sensor
    the tail of the train recently crossed.  Also, ideally in the autonomy editor, it should allow the user to
    click to select as well."*). The whole train lies between the farthest sensor its tail has crossed and the
    square it stands on, so that sensor names the road.
    - **When it is asked.** When a train is pasted, placed from the right-click menu, or set in the locomotive
      dialog, only where the tail lies past a junction with two roads back and the roads put it on different
      track. Two roads are two pieces of METAL, compared by the places the rail runs over (OB-276: RampDown's
      southbound lane and its northbound turning copy both leave south by one rail, so they are one road; a
      balloon's two ends are two). Elsewhere every answer describes the same track.
    - **A tail that has passed the switch the roads part at is asked about whether or not it has reached a
      sensor** (MT-477; Adam: *"when set to lenth 3, the tail always follows switch 51 turned, rather than facing
      straight toward rampdown ... technically that length should qualify for the prompt"*): the last square two
      rails share holds the points, and a tail reaching into it lies on one leg or the other. Each road it can lie
      on is offered - by its sensor where the tail reached it, and as **towards X (not reached)** where it did not.
      At BottomSecondary three units are offered towards RampDown and towards BottomCrossover, and four units
      RampDown and towards BottomCrossover. A tail that ends before the points covers the same squares on every
      road, and is not asked. `core.testATailPastASwitchIsAskedAbout`.
    - **Not asked, or closed without an answer: the road the train had is kept** where it stays on the same
      square with the same side - the question's **Cancel** - and there is no answer that forgets it (Adam: *"drop
      not known, keep cancel"*).
    - **The list** starts on the road the train has; with none, on the one sensor nearest the back of the train
      where exactly one qualifies - no other offered sensor lies further back on the same road - and on nothing
      otherwise (FR-088; Adam: *"so the user can just click OK if appropriate"*). A way towards a sensor the tail
      has not reached is no sensor crossed, and is never where the list starts. It offers each such sensor, nearest
      first, with **OK** and **Cancel**; Cancel, or OK with none chosen, keeps the fork rule. A sensor exactly the
      train's length back is offered: the tail has reached it (OB-226). Only roads a train can drive in on are
      offered - the walk back takes rails that run towards the train - so a road it could only have reversed along
      is not one (OB-227; Adam: *"that isn't a realistic path"*).
    - **Where it is put.** The same list is in the right-click menu under **Farthest sensor the tail crossed**,
      and in the autonomy editor **Pick on the diagram...** outlines the sensors to click instead. **On the main
      window the question itself is put on the diagram** (FR-100; Adam: *"highlight possible squares on the diagram
      and ask the user to click one.  only show the list if there are options on another page"*): the sensors are
      lit and a click on one answers it, with **Cancel** in a small window. The list is asked where a choice is on
      another page or not drawn, and where an editor window is open, whose squares do not take the click (TDU-C1) -
      and then the list opens from the editor, in front of it (MT-575), while the editor is showing; a minimised
      one hides what it owns, so then the list opens from the main window (RLA-C4). The rest of a double-click on
      the sensor that answered is the question's, not the sensor's (TDU-B3), and the paste reads everything about
      its landing before the question waits, because the window stays live while it does (TDU-B2).
    - **An answer is written only where the placement still stands when it comes back** (TDU2-A1, TDU3-B1): the
      square's copy on the railway running then still holds the train, with the side it was put down with and the
      road it had - a railway rebuilt in the wait counts, its new copy being the one asked - and the setup is
      still the window's: the same session. **So the answer follows the train** (TDD5-C1; Adam: *"Follow the
      train."*): another configuration loaded in the wait, whose copy holds the train as it was put, gets it, on
      the railway running and in the configuration now active; closing the track-diagram editor, which replaces
      the session, drops it, and a door whose setup was let go in the wait saves nothing (TDU4-C2). An editor
      opened in the wait drops it too, and nothing is written into the setup: the editor holds the setup as it was
      when it opened, and its Cancel would take the answer back out - as every main-window door that writes the
      setup refuses while an editor is open (RLU-C9). A dropped answer - Not known included, a Cancel not - is
      logged, naming the train and the square; the facing was written before the question was asked, so only the
      road waits for it. `regression.testTheTailIsPickedOnTheDiagram`.
    - **The offered sensors are a suggestion.** They are worked out from the measured lengths of the roads back;
      what blocks track is still this walk, reading the road chosen. A road given this way runs along rails laid
      towards the train, as a run's road does. `core.testTheTailCrossedQuestion`,
      `regression.testTheTailCanBeGivenInTheEditor`.
- **It stops at unmeasured track.** Only positive lengths are determinate.
- **The square the train is standing on is track, and it is spent first** (OB-278; Adam: *"the 2 length tile with
  the s88 consumes 2 units of the train"*, and asked where, *"Everywhere"*).
  A train no longer than the square it stands on lies on that square and blocks nothing behind it;
  a longer one spends the square and lies back over the rest. Every walk that works out where a body
  lies does this alike - the tail walk here (places budget and hop budget), the berth rule, and the
  orange line (below) - and so does the room rule, which always counted the square.
  **The allowance is the station's SIZE** - the maximum train length typed on it, which says how much
  train it may hold and is what `whyTooLongForThisRoute` asks first. Adam: *"if the segment length is shorter,
  more should be blocked. The station size is an allowance, not a length"*. On his measured railway each berth's
  length is written on its sensor square, so reading the allowance as the square's own length would leave that
  square unspent and put every standing train's tail over the switch behind. `core.testAStationsSizeIsAnAllowance`.
- **A train part-way along a run is walked the same way from its last MILESTONE** (OB-244, MT-438):
  a milestone is ordinary block and the body lies over it.
  `core.testARunningTrainHasOneTail.testAMidRunMilestoneIsNotAnAllowance`.
- **The first hop takes the copy of the rail the train ARRIVED along** (SVZ-B1). A piece of rail is two
  edges, one per direction, and at a berth both can report the same way in. An edge's places are the
  path plus the square it arrives at, so only the arriving copy carries the square the train is on - taking
  the other would leave that square claimed by nobody. Where there is no arriving copy - a square a train has
  been turned on - the other is used.
- **With no road to follow, a fork right behind the platform stops the tail at the switch** (MT-477; Adam: *"stop
  at the switch"*), as every other fork does. Where the rails arriving by the recorded side part before the
  train's length is spent - at BottomSecondary, the straight rail from RampDown and the turned one from
  BottomCrossover, at switch 51 - and the train was placed with no answer, the squares they share are claimed up
  to and including the switch, and nothing past it.
  `core.testATailPastASwitchIsAskedAbout.testWithNoAnswerTheTailStopsAtTheSwitch`.
- A train never blocks itself - pulling forward off its own tail is how it leaves. **But its own tail is not an
  answer for anybody else's** (OB-285; Adam: *"Add the refusal"*). A train turned where it stands - reversed on
  the throttle, turned with the Facing menu or at a square trains may turn at - finds its own tail on its way out;
  the other trains are asked about there too, by the railway and by Return Home alike. On his railway a train
  turned at Tunnel must not be cleared south through the points at column 7 with a train in TunnelRightPark lying
  across them. `core.testATurnedTrainIsNotSentIntoAnotherTail`.
- **Except round a loop into its own tail** (OB-294; Adam: *"make sure the model factors in whether the
  train will clear the area before it crosses over"*, and *"the check should pass if the train would be gone (i.e. if
  that one was only length 4, for example)"*). **The head may come back to a place only once the tail has left it.**
  The body as it stands is the one the walk above claims; the route's own places are timed as the head runs them. The
  figure for a return is the longest train clear of the place in time: for a place the body lies on, the measured
  track the head runs before it comes back there plus the body in front of that place; for a place the route ran
  over, the measured track run since the head left it. A longer train is refused, and the refusal names the tightest
  such figure on the route - the longest train that goes (TDA-C1). **Only measured track binds**: a return is judged
  only where the way round - the route itself, not the body in front of the place - is measured since the head left
  it: some length run on it, or every place on it answered 0 (OB-300 - a loop answered 0 throughout is track of no
  length, and refused to any train lying on it); a way round nobody measured is not judged. Where some of it has no
  length the refusal says how many things Mass Assign Lengths would ask a length for on the way round, which is the
  other way past (TDA-B1). The build marks, place by place, the piece, switch or shared square the editor still asks
  for, keyed by the answer it is asked in - its piece, or all of its page's switches, or all of its page's
  crossings, one length each (OB-297; Adam: *"Locations of switches are known."*) - and the note counts those
  answers, each once, so it asks for exactly what the editor offers: a route tile's piece is asked for until its
  other squares are measured, and a piece whose units sit on a station's square is not (ADA-C1). A configuration
  built before the marks counts whole legs.
  **After a turn the body is ahead of the train** and moves with it - a turn on the way, or a train leaving over its
  own tail - so the question starts again. Asked by every tier, Why not Moving? and Return Home's planner. A hand door
  does not offer a destination the rule refuses on every route; the sentence shows where a menu built before the
  length changed is used, and in Why not Moving? (TDD-C1). On his railway the way round from BottomSecondary by the
  tunnel back to row 11 measures nine units, so a four-unit train goes and a twenty-unit one does not.
  `core.testATrainDoesNotRunIntoItsOwnTail`, `core.testTheOwnTailArithmetic`.
- **Every other train's tail is asked about, not only the one the record kept** (TDD-A1). The claims record one train
  per place - the last walked, in an order nobody chose - and two tails fouling one switch from its two legs both claim
  its square. A train's route is judged against every OTHER train, walked without it, and Return Home keeps each
  train's claims apart. `core.testATailIsNotHiddenByAnother`.
- **One tail per train, and a running train's starts at its head** (OB-243; Adam: *"the
  tail is certain at departure and shouldn't change.  You also know which way the train went ... Just
  unlock the rest of the diagram once the tail by length is far enough away"*). A locked path reserves
  every Point on it, so during a run a locomotive is the occupant of several Points at once; the walk is made
  once per locomotive, so no tail is drawn at a destination the train has not reached or at a square it has long
  left (OB-242). A running train is anchored at its last reported milestone and spends its length back along the
  road it has already driven; a standing train is anchored where it stands. `core.testARunningTrainHasOneTail`.
- **And the track it shares metal with is closed too, which is the anti-collision rule at a switch.**
  Adam: EN57-203 *"is allowed to traverse a blocked/shaded switch (60) to get from
  TunnelLeftPark to BottomMainC, even though it should not be possible."* Coverage is recorded per
  EDGE; a train lying across one pair of a switch's arms fouls the other pair, and the other pair is a
  different Edge that is not in the covered set. So a path is refused over any edge that shares
  metal with a covered one - the lock-edge relation `GraphReducer` derives from shared tiles. Told
  apart from an FR-001 occupancy restriction, which uses the same list, by **symmetry**: sharing a
  tile is mutual and the reducer records both directions, while a restriction is one-directional.
  Known limit: two stations each holding the other back are symmetric by coincidence, and this
  over-refuses there. `core.testACoveredSwitchClosesTheOtherRoad` goes red when the rule is removed - it needs a
  turnout to express, so it runs on `test/layouts/single-switch` (AUT9-B2).
- **The copies of one square are one piece of metal, except where the square is two.** Occupancy is
  recorded per Point and a square is emitted as one Point per side a train can arrive by, so the build
  groups those copies under a `block`: a train on the northbound copy makes the southbound copy read
  occupied, which is right, because they are the same rail. The TILE says which copies those are, not
  the s88 - genuinely different places share a sensor on a real layout. On the three types where that is wrong
  (OB-238) Adam: *"it is two pieces of metal. imagine two parallel tracks simple
  appearing on one tile for visual convenience. two distinct, not connected paths."* A double curve is
  two arcs in opposite corners that never touch and an overpass is two tracks at different heights, so
  DOUBLE_CURVE, FEEDBACK_DOUBLE_CURVE and OVERPASS are grouped per ROAD instead (AUR-B1), and a train on one road
  leaves the other free. `core.testTheDiagramDrawsWhatTrainsMayDo.testEachArcOfADoubleCurveIsItsOwnPieceOfMetal`.
- **But only over the part of it the train is actually lying on** (OB-207; Adam: *"75 407
  DB cannot go from Tunnel to BottomMainA even though it should be able to"*, at a train length of
  one). A one-unit train parked at the end of a twelve-tile run must not foul every road sharing any tile of it.
  The build writes each edge's **places** - the location identifiers the lock relation is itself derived from, in
  order, each with what it measures - the tail walk spends the train's length across them, and the shared-metal
  rule asks whether the tail lies on metal *this* edge runs over. Adam proposed it as extra nodes at the switches;
  the places are the same information without inventing a Point that has no sensor.
  - The **direct** case stays whole-edge on purpose: a path uses all of its own edges, so a tail
    anywhere on one is in the way. Only a **shared** edge can be touched at one end and no further.
  - An edge with no places - a hand-written configuration, or one written before 3.0.0 - keeps the
    whole-edge answer.
  - `core.testAShortTrainDoesNotBlockTheWholeRun` is the test, with a control that a train long
    enough to reach the shared metal still blocks it.
- **Two marks, and they say different things** (Adam: *"'train is here' should also mean
  'track is blocked' - that is the whole point. it's the same as greying out edges, just in a
  different way."*). A square with **orange** on it is where a train IS. A square that is **grey and
  not orange** is track that train's presence has made unusable. A square with neither is free.
- **A run is drawn in the stations' blue ahead of the train and dark grey for the track it has driven and still
  holds, with white arrowheads edged in dark grey** (FR-106: Adam's palette, kept in one place, `DiagramColours`).
  Track a non-atomic run gives back behind the train, as its tail clears it, is drawn as nothing - its line and the
  pale wash of what it held clear both go (RSA25-C2); under atomic routes they stay until the run ends.
- **The strip above the track diagram says "Setup changes not applied yet"** while the setup has changes the railway
  has not taken - an edit made as a run started, or one that leaves the setup unable to build while the last railway
  that built stays loaded; its tooltip says they apply at the next load, once the setup has no errors (Adam: "Add
  the setup notice, but make it brief").
- A train is drawn as an **orange line along the track it is standing on**, **on the track diagram
  viewer only** - and while it runs, along the track its length still covers, drawn over its route so the tail shows
  the whole way (FR-106). An editor is where the railway is arranged, and what happens to be
  standing on it while you arrange it is a fact about right now rather than about the drawing - the
  same reasoning that makes station names the default caption there.
- Blocked track is drawn by **fading the square itself to 40%**, **whether or not anything is
  running** - the tile's own art, drawn faintly, so a blocked curve is still legibly a curve. Adam:
  *"I want the shading to instead be the same tile with more transparency exactly like
  what happens when edges are locked in autonomy mode."*

  The fade is applied **to the icon as it is painted** - a composite set before `super.paintComponent`
  - because nothing drawn afterwards can make what is underneath transparent. Everything autonomy
  draws on top, the train line included, is painted at full strength, so a square that is both still
  reads as both. `LayoutLabel.BLOCKED_ALPHA` is the number.

  **And it is applied once.** A square blocked by a standing train and also locked by a route a running
  train holds is drawn at the same 40%, not faded twice (OB-212; Adam: *"keep one level of
  opacity, dont stack."*). Two reasons to refuse a square are still one refused square. The route's and the
  annotation's washes are told the region the tile has already faded and do not lay a second one over it
  (MT-375, `ui.testTheWashDoesNotStack`).

  **It is drawn at idle too** (W7B-B1). The refusal is not fenced to a running railway: `Layout.isPathClear` sweeps
  the covered edges in every tier at every time, because a tail lying across the rail is physical rather than a
  preference. So at idle a right-click manual send across a parked train's tail is refused -
  `errorTrackCoveredByStandingTrain` - and the diagram must not paint that track as free: the guard and the
  affordance must ask one question (OB-057/OB-090). Adam: *"yes, this greyout should appear at idle and
  be regenerated if a placement or train/track length is changed."*
  `ui.testTheGreyAppearsAtIdleToo` is the class; `ui.testBlockedTrackIsGreyWhileAutonomyRuns` is the running case.

- **And it is regenerated by every operator gesture that changes it with no train moving.** Adam
  asked first for three - a locomotive placed or removed, a train's length changed, a tile's length
  changed - and then for all of them: *"make sure the shading and orange repaints on
  any autonomy or train/track length edit."* A station flag, an arrival side, a one-way direction and
  a reversal marking all change which edges exist, and therefore what a standing train covers.

  **One door carries the general case**: `rebuildRunningLayoutFromSetup` ends in
  `blockedTrackChanged()`, and every setup change goes through it. The refresh diffs the two sets and
  repaints only the squares whose mark changed, so a rebuild that alters nothing costs a comparison.

  The three original gestures still reach it by their own routes:

  - a **placement** through `updateVisiblePoints`, at each of the diagram's placement doors - the
    keyboard's Control+X / Control+V / Delete, the right-click Place and Remove items, and
    `GraphLocAssign`;
  - a **tile length** through the setup rebuild it needs anyway, because an edge's length is baked
    into the built `Layout` by `GraphReducer` and cannot change without one - which is the general
    door above, reached first by this one;
  - a **train length** through `TrainControlUI.blockedTrackChanged`.
    `Layout.edgesCoveredByStandingTrains` reads `getTrainLength()` off the locomotive
    every time it is asked, so a length typed into the dialog changes what the railway refuses
    immediately, with no rebuild in between and therefore nothing else to tell the drawing.
- **The line follows the road, not the square.** It runs along the route the train occupies through
  each tile, which is why a wash was the wrong way to say *"a train is here"*. Adam:
  *"instead of shading the entire tiles, we need to draw a line (let's say in orange) to show that
  the train is there. graying makes it look confusing on double curve tiles."* A double-curve tile
  carries two roads, and a train on one of them would be indistinguishable from a train on the other. A square
  that is grey and not orange claims no train on either road, only that the track is unavailable.
- **The line is drawn on every square the train lies over, the sensor squares included.** The square a
  train stands on is always a sensor, so a line missing there is missing from the one square that matters
  most. Adam: *"when we draw orange lines, they don't overlap with sensors"* - on every sensor,
  occupied or not; the orange shows where the train is (OB-277). The
  standing square is drawn and, since OB-278, spent first like every square behind it, and each sensor the body
  reaches is drawn along the road facing the track just walked, which names one arc even on a double
  curve. `regression.testTheWashIsNoLongerThanTheTrain.testTheSquareTheTrainStandsOnIsOrange` and
  `testASensorTheTailLiesAcrossIsOrange`, the second with the train one unit too short as its control.
  **A route tile is drawn too** (OB-279): it carries whatever the track beside it carries, and its road is
  named by the sides it joins rather than by the port map.
  `core.testRouteTilePlacement.testARouteTilesRoadCanBeDrawn`.
- **The line is as long as the train.** It walks square by square from where the train stands, each square
  paying its own length, and stops when the train is used up - drawing the whole edge instead washed
  seventeen squares for a train of length one (MT-309). The grey is what routing refuses (below).
- **The marks survive a highlight.** An accessory change flashes the square it commands; when the
  flash ends the square is asked again rather than remembered, because the train may have moved while
  the highlight was showing. The train line is painted over the tile's icon; the refusal is the icon itself, drawn faintly. Neither is baked into the image - both are applied on every paint, which is what keeps an accessory highlight from taking either away with it.
- **Both are refreshed together, and only where they changed.** One pass recomputes both answers and
  repaints exactly the squares whose line or wash differs from what it was. The whole diagram is
  never rebuilt for either: that is MT-334, and what it looks like is the page flickering.
- **And never on the event thread** (OB-192). Both answers come from
  `Layout.edgesCoveredByStandingTrains`, which is `synchronized` on the `Layout` - the same monitor a
  dispatch holds for the whole of `configureAndLockPath`, a sleep per accessory of the path. Asked
  from the event thread that freezes the window for the whole dispatch (and `repaintSwitch`, which a driving
  thread inside that monitor calls for every accessory it commands, must not be `synchronized` on the window -
  RSA67-C1). Adam: *"starting autonomous operation ... makes the UI
  unresponsive. Trains still run, but nothing is repainted, and controls are stuck."* So the pass runs
  on a worker and the marks land a beat after they are asked for. Nothing about **what** is drawn
  changes; only where the work happens.
- **WHAT IS DRAWN IS WHAT IS REFUSED, once both marks are counted.** The grey is the squares
  standing trains CLAIM - `Layout.placesCoveredByStandingTrains`, which is what `isPathClear`'s
  shared-metal sweep refuses a path over - so blocked track is visible as blocked track, and track a
  train does not lie on is not grey. A square the railway would not let a train onto must not look like free track;
  two marks answer both questions (where the train is, what is blocked), where narrowing one mark to answer the
  second could only lose the first. Adam (OB-208): *"orange shows where the train is, gray shows what's blocked."*
  And (OB-280): *"grey what routing actually refuses.  if the train doesn't protrude past the switch, there should be
  nothing else to gray."* A path driving along a covered edge ends at or runs through the square the train
  stands on, which is claimed; a path crossing only the far end of that edge is NOT refused (OB-207) - so the grey
  is the squares the railway claims under standing trains, not every tile of every covered edge: the whole square
  where the place is the square (a train on one leg of a turnout blocks the other), one road where the place
  names a road (a double curve or an overpass), and the fade follows the grey's roads rather than the orange's.
  With OB-278, a train that fits on its berth greys that square and nothing behind it.
  `AutonomySession.routesBlockedByStandingTrains`, `ui.testTheGreyAppearsAtIdleToo.testTheGreyIsWhatTheRailwayClaims`.

  **What is still not drawn**, and is explained in the "why not moving" view rather than on the
  diagram:

  - a covered edge's squares the train does not lie on, which routing does not refuse (OB-280);
  - a path refused because an edge it uses **shares metal** with a covered one - the lock-edge
    relation `GraphReducer` derives from shared tiles - is refused over a square that is itself
    greyed, but the *path* is not marked as refused anywhere on the diagram.

  **And the answer has to be readable, which is a rule of its own** (OB-191). The "why not moving"
  answer is a train, a dozen stations and the reason each one was refused, and it is shown in the
  strip across the top of the editor. That strip has a height floor so that messages coming and going
  do not move the diagram, and a height CAP so that a long answer does not eat the page - and an
  answer longer than the cap must be given the whole strip, because a viewport smaller than its
  contents is the only thing that makes the strip's scrollbar appear. Laid out at its preferred height
  instead, it is shrunk toward its minimum and the strip stands open at full height with nothing in it. A short
  message is still centred in the strip (OB-151); the two rules are one
  statement in `AutonomyBanner.CentredButNeverTaller`, because settings that have to be read together
  are settings that come apart.

  **And it is worked out on a worker, not on the click** (D3-C1). The answer comes from
  `Layout.explainDestinations`, which takes the railway's monitor and walks the graph, and then a
  reduced-path search for every station the railway offers - so asking for it on the event thread would break
  the rule above in the one tool a stuck railway sends people to. The click captures the
  layout and the station index, says *"Working out the reasons..."*, and the report and its lines are
  painted when they land. Two clicks in a row are two searches and the later one wins: an answer to a
  question the user has moved on from is discarded rather than painted over the one they are waiting
  for. The editor cannot be open while autonomy is running (OB-047), so this is not a live freeze; it is on a
  worker anyway. Adam: *"I would rather take it off EDT. It's not critical now, but we want to
  avoid these pitfalls."*

**Known limits, deliberately.** A tail that really does reach past a fork, or across unmeasured
track, is not blocked. Nor is the tail of a train whose recorded arrival side names a side no track now
leaves its square by - a value left stale by an edit to the diagram: the walk stops before its first
hop and claims nothing, even where the geometry leaves only one way back (`Layout.walkOneTail`, OB-264). All
three under-claim. Blocking on a guess is still a refusal, and it stops trains
that could have run.

**Two rulings about the room sum** (MON-C13).

1. **A positive length counts, and that is deliberate.** On a diagram-built graph an edge's length is
   the sum of `max(0, tileLength)` over the tiles it spans, so one measured tile out of five gives a
   positive number and the sum reads it as a measured segment - which under-counts and can refuse a
   train that fits. Asked whether an edge should count as measured only when every tile in it is,
   Adam: *"no, any nonzero length on a logical edge between 2 stations will count."*

   So this is the rule rather than a limitation. It errs towards refusing, which is the safe direction,
   and the remedy in the operator's hands is to measure the rest of the tiles.

2. **A REVERSAL SPLITS THE RUN IN, AND THE TRAIN HAS TO FIT IN BOTH HALVES.**

   Asked whether *"the track segments leading up to it"* means up to the reversal or up to the berth, Adam:
   *"it can be either the reversal or the berth, depending on where switches are.  both need to be long
   enough."*  With the figures:

   > four-unit train in a three-unit berth with a two-unit approach: refused both because of the berth
   > (3<4) and the track length that potentially couldn't fit the train while reversing.  if the train
   > isn't reversing, then it should be accepted as long as the berth is long enough.

   > 8 unit train on a 9-unit runin would just be refused because 3<8.  if the max train length at the
   > berth was set to 8, we would be OK.  in short, your tests should consider both.

   So there are **two stretches and two comparisons**, and a train has to fit in each:

   | Where the train stands | The stretch it has to fit in |
   | --- | --- |
   | the berth, after turning | back from the berth to the reversal, or to the last switch, whichever is met first |
   | the reversing point, while it changes direction | back from the reversal to the last switch, or to the start of the route |

   The first is the walk's own stop (`Layout.measuredRoomAtTheEndOf`); the second is a square the train
   **comes to rest on**, so `whyTooLongForThisRoute` judges it the way it judges a berth rather than the
   way it judges a square the train rolls over. Where nothing on the route turns the train neither
   stretch exists and the sum is the whole run in, which is his other sentence - *"if the train isn't
   reversing, then it should be accepted as long as the berth is long enough."*

   `core.testNonReversibleTrains` holds both, each isolated by making the other stretch long enough that
   it cannot be the one objecting, and reading the refusal's sentence rather than its yes-or-no.

   **His 3 is neither of the numbers the rule produces on that fixture** - the berth beyond the turn is
   4 and the approach behind it 5 - and both refuse an eight-unit train, so the verdict is his and the
   arithmetic is the rule's.

   A shorter stretch with nothing measured in it is no information rather than no room (bullet 1's doctrine). A
   long measured approach with an unmeasured berth beyond the turn is therefore not refused for room;
   `autosetup.ui.checkRunInShorterThanTheBerth` is the notice that asks for the berth to be measured.

**A STATION TOLD IT HOLDS MORE TRAIN THAN ITS TRACK MEASURES GETS A NOTICE.** Adam:
*"let's add an autonomy editor notice that alerts the user if a run-in is shorter than the berth length,
that way they can decide if it makes sense or not.  for example, a station of length 4 may have two
segments of tracks on either side of a switch, of length 2.  that is acceptable and its limitations are
understood."*

Two numbers can refuse a train a platform and different hands set them: `maxTrainLength` is what somebody
typed, and the measured room is what the track says. Where the measured room is smaller the typed maximum
never binds, and a train inside it is refused by a rule quoting a number nobody typed.

- It fires per STATION, on the SMALLEST room any of its approaches has, and the sentence carries both
  numbers - the maximum first, the room second.
- The room is `ReducedEdge.getRoomAtTheEnd()`: the track from the last switch on the arriving edge to the
  platform, which is what `Layout.measuredRoomAtTheEndOf` counts. **A notice quoting a number the refusal
  would not quote sends the reader to measure the wrong stretch.**  So for a parking berth the number is the
  berth rule's where that stops first - at a crossing between the berth and its switch, or on a leg with no
  switch (TDA2-C6, TDA3-C2) - and only where the rule refuses: something on the leg is measured, which is when it
  judges at all, and another road runs over the square it stops at, which a switch's other leg into the same berth
  does not (TDA5-C1).  There, with nothing measured before that switch or crossing, the notice says nothing: where
  those squares were answered 0 the berth takes no train that way, and that is a **warning of its own** - *"the
  length given for that track adds up to 0"* (TDA4-C2; Adam: *"Give it its own sentence as a warning,
  make it sound intuitive"*) - and where they were not, the half-measured warning names them (TDA3-C1).
- **At a platform, the figure a train is refused above, where there is one** (TDA-C10; Adam: *"Add the
  refusing figure where there is one."*).  A train longer than the room stands across the switch only as far as the
  measured route in holds it (FR-087), so one setting off from the station or turn right behind, and longer than the
  track from there, is refused.  That figure is the sentence's third number, given where it is under the stated
  maximum, and read off the railway the setup builds: back from the platform over every leg, through sensors nobody is
  started at, to a copy a train is started at or turns at - a station square can have a copy heading the platform's way
  that trains may not arrive at - and stopped at a leg nobody measured, as the route in is, and on past one answered
  0 throughout; never through or into a
  point that is switched off, though out of a station switched off round a train standing there, and never from or
  through another copy of the platform's own square; worked out for every leg together, so a loop is walked whole
  (ADD-C6, ADA2-C3).  Each way in gives the larger of its room and its route in, as the refusal quotes, and the figure
  is the least.  Only over the ways in {3} is taken over - past a switch with something measured, or with a length from
  a turn - so it is never less than {3}, and a way in with no length refuses nothing and hides nothing (ADA2-C1,
  ADA2-C2).  Where it equals {3} it is still given: the shortest way in holds nothing behind its switch, and a train
  longer than {3} is refused there rather than standing across it.  On the frozen railway: TopMainR1Inter 3, equal to
  its room (TopR1ParkShort's four-unit train, MT-564), and LowerFront 4, from ParkingTrack12 - switched off, and a
  train standing there is sent by hand - each the railway's own refusal; none at Tunnel, BottomMainA or
  BottomInnerOtherside, where every way in the railway runs measures at least the maximum.
  `core.testAnAnsweredZeroIsNotMissing.testTheRefusingFigureIsTheRailwaysOwn`.
- An arriving edge crossing no switch is skipped unless trains turn round where it starts - there the
  guard walks on back through earlier edges, so that edge bounds nothing - or, for a parking berth, a crossing on
  it ends the berth rule's room, as above.
- Silent where either side is missing: no typed maximum is `checkNoMaxTrainLength`'s sentence, an
  unmeasured stretch is `checkReversalNeedsLength`'s, and a railway measuring no track at all has decided
  not to model lengths.
- A **NOTICE**, not a warning, and that is his ruling rather than a grading: his own example is a railway
  with nothing wrong with it, and a setup where that is understood should not carry a warning for ever.

`core.testThePlatformNoticeKnowsWhatATrainIsRefusedAt.testTheEditorSaysWhenAPlatformHoldsLessTrackThanItClaims` holds it, on
his own 2 + 2 example and on the reversal branch.

**Measured on the operator's own railway**, because "everywhere" is not a number. The room rule runs at every
destination and in every tier (MT-262, D2-C2), so whatever either ruling above gets wrong, it gets wrong everywhere.
The census is `core.testTheRoomRuleCensusOnTheRealLayout`, which runs in the battery: for every ordered pair of
station Points it searches a path, then asks each of six trains of its own, one to six units long, in
turn whether the widened rule refuses it and the fence it replaced - `isTerminus() || isReversing()` -
would not have. The six lengths are the class's own (Adam: *"generate trains programmatically in the tests, and
give them semantic names"*), so measuring or buying one of his trains does not move the population the census is
over.

| | |
|---|---|
| ordered station pairs | 1332 (37 destination Points, each against the other 36) |
| of those, routable | 956 |
| train lengths asked, the census's own | 1, 2, 3, 4, 5, 6 |
| journeys the widening NEWLY refuses | **505** |
| every one of them arriving at | BottomMainA (eastbound), BottomMainB (eastbound), BottomMainPost (northbound) - each measured at **ONE** unit of room |

The census runs on the railway frozen from Adam's measured layout (`test/layouts/live-snapshot`). His own lengths and
station sizes are taken off in the census's copy first, so it is the three-tile experiment. His one-way marks, barred
sides and the compulsory turn at BottomMainC leave 37 destination Points; BottomMainC is off the list of berths because
it is a terminus - one copy, every train turned - and a refusal at a terminus is not new. The census walks the
railway's own route search, which does not extend a route through a terminus that is not its end (section 7), and
does not walk a route that doubles back through another copy of its own start or end (AMR-B1, section 7; Adam: *"We
need to refuse both. A copy makes a cycle."*). No route turns at a measured square under these three tiles, so the
census does not reach the turn bound; the band in the census, `core.testNonReversibleTrains` and
`core.testATrainIsJudgedOnlyWhereItStops` hold it. `core.testWhichSquaresTheRoomRuleClosesOff`, on the same three
tiles, finds no square that offers a longer train nothing at all.

The room rule declines to judge almost everything else: the railway carries only a few measurements. The berths are
the ones Adam named himself, with the number he gave: *"bottommainb, which has a length of 1 leading up to its
switch"*, and MT-262's own report of `BottomMainA (eastbound)` offered *"with ONE measured unit of room behind it"*.
So the widening refuses the journeys he asked to have refused, at the squares he was looking at, and not - on this
railway, today - a wider set that bullet 1 has under-counted. A layout with lengths scattered over more of its track
would be a different answer, and the way to find out is to run that census again rather than to reason about it.

---

### 5d. Atomic Routes, and the track a train is handed back

**A path is held whole until the run ends - unless Atomic Routes is off, and then each edge is given
back as the tail clears it.**  Adam runs with `"atomicRoutes": false`, so this is his railway's
ordinary behaviour, not a corner of it.

Giving an edge back early is what lets a second train follow a first down the same road instead of
waiting for it to arrive.  The question is only ever *has the tail passed this edge*, and
`Layout.tailHasProvablyPassed` answers it from two facts: how far the head has travelled since the end
of the edge, and how long the train is.

**How far the head has travelled is counted to where the sensor that last answered saw it** (GS-B3): the edge whose
sensor answered, up to that sensor's own square.  A sensor answers when the head ENTERS its square, so the square is
counted behind the head only when the next sensor answers - the safe reading for a release, where the walk that claims
a running train's body takes the generous one (the head at the square's far end).  A Point with no sensor - a
hand-written configuration may have one; the builder's all carry one - is passed without waiting, so the track to it is
counted only once the next sensor answers - and so is the track to a sensor that already read occupied when its wait
began, one two Points in a row share or one another train stands on, whose answer came before the head did (RSA61-A1,
`testASensorTwoPointsShareIsNotProofTheHeadReachedTheSecond`).
`core.testTrainTailClearsEdges.testAnEdgeIsHandedBackOnceTheTailHasPassedIt`,
`testASensorsOwnSquareIsNotCountedBehindTheHeadYet`, `testAnEdgeIsNotHandedBackBeforeTheTailHasPassedIt`.

**It is wrongly true in exactly two states, and both of them are ordinary.**

- **A path with no measured edge anywhere on it.**  With nothing measured the honest answer to "how
  far has the head gone" is "no idea", and the rule reads that as clear.  A railway with lengths on
  its platforms and nowhere else is in this state for most of its paths.
- **A train whose length is 0**, which is what `Locomotive.trainLength` holds until somebody sets it.
  `behind >= trainLength` is then true the first time every edge is asked about, so the whole railway
  is handed back under the train however well the track is measured.  **Mass Assign Train Lengths** on
  Bulk Tools (§5b, FR-094) asks every train autonomy would run that has none.

In either state the edge behind a moving train is released while the train is still lying across it,
and the next dispatch is routed onto occupied track.  **So the railway is not allowed to run
non-atomic while either state holds**, and that rule is asked at every door that could start a train
or load a setting:

- the **Atomic Routes checkbox** refuses the gesture and says what is unmeasured - somebody is there,
  and one gesture from fixing it;
- the **load door** (the editor's apply, OB-254) forces the setting back ON and writes the reason to the log, because a
  file has nobody at it and refusing the load would make a configuration the operator already has unopenable;
- the **five dispatch doors** - Start, Execute Timetable, Return Home, and the two hand dispatches -
  do the same, because a TRAIN's length is written on the live layout long after any file was parsed.

**What counts is whole paths and trains, not squares.**  An unmeasured switch square does not count,
because an edge's length is the sum of its squares and one measured square gives the edge a length.  A
rail with measured track between it and every destination does not count either, because a path ends
at a destination and so can never be unmeasured end to end - which is enforced where paths are BUILT,
in `Layout.bfs` and `HomeStaging.firstClearRoute`, not where they are checked.  And a rail is counted
once, not once per direction.

**Track answered 0 on purpose is measured** (TDU-C6; Adam: *"0 lengths count
as measures, so non-atomic should be allowed"*). The checkbox, the load door and the dispatch doors do not count
it, and the escape above does not fire over it - all of them ask `Edge.isMeasured`, which is true of an edge
with a length or with every square answered 0 (a route tile counts as answered, since it takes no length). Such
a path gets no escape, so its edges are held until the measured track after them covers the train - and to the
end of the route where none does - the safe direction.  **Every other length rule asks the same question** (§5b,
ADU-C7): the gate and the escape have to ask one question, and so do the route in and the tail walk, or a platform
admits a train whose tail lies past the 0 on track nothing claims.

So the Unmeasured Track display in the editor is **not** the list to work from: it answers a different
question, about how far a tail reaches.  The refusal's own list is the one to measure, and it comes
back in alphabetical order because the message shows only the first three of it. `MT-470` is the hands-on test.

**A sensor is held for the train whose route runs to it until the rail to it is given back** (RSA2-B1; Adam: *"it
should be allowed once unlocked"*).  The first time a sensor reads occupied ends every journey waiting on it, so no
route may end at, or pass, a sensor another train's journey is waiting on - nor one whose rail that journey has not yet
given back.  A feedback double curve's two arcs are two pieces of metal with one contact (OB-238), and between the
head's arrival and the release only the sensor's own reading kept a second train off the other arc: a gap between two
axles reads clear.  The rail is given back as this section says - once the tail has passed where the track is
measured, one sensor on where it is not, at the journey's end with Atomic Routes on.  A train still claiming its route
holds its sensors as one under way does, and the claims are read before the journeys, since the hand-over from one to
the other writes the journey and then takes the claim away (RSA3-C1).
`core.testATrainIsDispatchedOnce`.
