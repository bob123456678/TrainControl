# Length: track room and station capacity

Part of the behavior reference: [`../behaviour.md`](../behaviour.md) is the index.  Sections keep the numbers and headings they had in the single file, so a citation such as "behaviour.md §5c" still resolves.

## 5. Length: will the train fit?

Two separate rules, both about length, both easy to mistake for each other.

### 5a. Room, where the train comes to rest

**Two rules, not one, and they are asked in different places.**

| | what it measures | which squares it judges |
|---|---|---|
| **Track room** | the rail behind the square, from the last switch to it | **where the train comes to rest**: the destination, and a square it turns round at - **not a square it only passes** |
| **Station capacity** | the length the station says it accepts | **the destination**, when it states one |

Adam: *"the train should be refused any destination it does not fit in, i.e.
where the train length exceeds the accepted length."* The second rule does that, in all three
tiers: `Point.validateTrainLength` is asked from `Layout.isPathClear`, which every autonomy and manual
path goes through, and again from `HomeStaging`. A destination with no stated capacity is not judged
on capacity; that is what leaving the field at zero means.

The rest of this section is about the **first** rule.

- **It judges every destination** (MT-262), not only reversals. Adam: *"'75 407 DB' (length 4) is allowed to
  manually be sent from bottommainpost to bottomlongpark, even though track segments between the current
  position and there are 1+1 = 2."* A train comes to rest with its head at the destination sensor wherever it
  stops, so its tail lies back over the run in either way - which is what §5c's covered-track rule assumes.

  **In every tier**, autonomy included. A berth that cannot physically hold a train is not a
  preference manual may overrule, and §1 makes autonomy the stricter tier - a length rule that
  refused only the operator would invert it.

- The room is measured **from the last switch** to the square. A train that fits there fits behind any
  earlier switch too; one that does not comes to rest standing on the switch.

  **"Fit" here means the surrounding track**, not the length of the berth - that is the station
  capacity rule above, and it is asked separately. A train can fit the platform and still be refused
  because it would be left standing on the switch behind it.

- **It is asked where the train comes to rest, and not at a square it only passes** (MT-333). Two different
  mechanisms are about a train overhanging the points, and only the second is this rule:

  1. **A train already standing still blocks other roads** - stopped at a station or parked - wherever the
     segments under it are measured. Its tail claims the places it lies on and no route is cleared over them
     (§5c; `OB-207`; a driven train's tail follows the road it came in on, a placed one stops at the fork), and
     a parking berth refuses a train that would foul a road that is not its own (below).
  2. **Whether a train being SENT fits** is asked where it will halt: the destination, and a square it turns
     round at, because it stops and reverses at every such square on its way. A square it only drives past is
     not asked - it stands nowhere there, and a moving train holds the track it runs on, so nothing is routed
     across it meanwhile.

  Adam: *"edges are already locked as trains pass through ... it's really about implementing the same
  mechanic"* - the mechanic of point 1, which exists only where a train stands. And: *"this SHOULD be allowed
  per the standing rule that this switch blocking should only affect berthes."* So a route is never refused
  for passing a short measured stretch after a switch. `regression.testAPassingTrainMayStandAcrossThePoints`
  is the test, each scenario on its own diagram; `core.testATrainIsJudgedOnlyWhereItStops` is the same on a
  fixture.

- **The snapshot's one-unit tiles are a test configuration, not a survey of the real track.** Adam: *"A/B/C are
  distinct pieces of track. We put the lengths of 1 in there for testing. Actual tracks are much longer."*
  `5:22,7`, `5:19,12` and `5:14,13` are the only lengths anywhere on `test/layouts/live-snapshot`, and both
  censuses set them deliberately and say so. `core.testTheRoomRuleCensusOnTheRealLayout` re-measures the rule
  on every run against that frozen snapshot, not the railway Adam is operating, and reports a band rather than
  a figure because which route a search finds first is not reproducible between JVMs;
  `core.testWhichSquaresTheRoomRuleClosesOff` pins which squares offer a train of a given length nowhere at
  all. Journeys are counted over every ordered pair of stations, so refusals that fall unevenly close whole
  squares rather than thinning the timetable: **a journey in three is not a train in three**. Whenever this
  rule is changed, those censuses are the figures to read (§5c's table gives today's).

- **The refusal names the square that is short**, and says a different sentence for a square the train turns
  at than for the berth. Naming the destination when the destination has room sends the operator to measure the
  one stretch that was already long enough.

- **The walk has two stopping conditions and only one of them is a measurement.** It stops at the last switch,
  which is the rule, and at the start of the route, which is the walk running out of track to look at. At the
  destination the second is harmless: the train comes to rest there and lies back over the route it came along.
  On Adam's railway every refusal the rule adds is bounded by a switch, which the census asserts on every run
  (*"refusals the unbounded walk would have added"*).

- **Exactly-fits is admitted.** Four units of room takes a four-unit train - otherwise every berth
  measured to the train that lives in it becomes unusable.
- **The total across the stretch is what counts**, not any single tile: 2 + 2 admits a four-unit
  train, 1 + 2 does not.

#### A train may overhang the points at a station, and may not at a parking berth

> *"we need a clear rule to govern that this is OK, or simply make a rule that parking berths cant
> block any other edges, but not make that check for active stations."* - Adam

The rule above measures the room **past the last switch**, so a train longer than that stretch is
refused because it would come to rest across the points. Adam's worked example: an approach measuring
6 with a switch in the middle and 3 either side. A six-unit train there necessarily stands on the
points - no distribution of the measurements changes that - and he rules it acceptable.

**What decides is how long the train stays.**

- At a **station autonomy may choose**, a train is PASSING. It fits if it fits past the last switch,
  as above, **or** within the measured track of the route it drives in on (FR-087; Adam: *"BottomMainA
  should be allowed at length 3, since it is not a parking spot, the station allows the length, and the
  length would be tracked"*). The route is counted back from the destination, leg by leg, for as long as
  each leg is measured and never back past a square the train turns at (the berth's own bound); a train
  that ends inside that stretch is accepted, and its tail lies on the route and blocks it (5c). A leg with
  no length stops the count, so a train longer than the measured track in is still refused.
  `Layout.theApproachItselfHoldsIt`;
  `regression.testAPassingTrainMayStandAcrossThePoints.testTheRouteInHoldsAThreeUnitTrainAndNotAFourUnitOne`.
- At a **parking berth** - a square with *Can Be Chosen In Full Autonomy* off - a train is STAYING,
  and a berth is not worth a road. On top of the room rule it must foul no track that is not a way in
  or out of its own square. Its own roads are excluded because the train being there blocks them
  anyway, and *"its own"* means the SQUARE: a split platform is several Points and one piece of track.

**What this spends, said plainly.** A train standing at a platform across the points closes the roads
through them while it is there. Measured on Adam's railway, a train at BottomMainA long enough to
reach the switch closes both ways to the lower level and 33 ordered pairs of stations stop being
reachable from one another. That is a STRANDING cost, not a collision one: a standing
train's tail claims the places it lies on and no route is cleared over them (`OB-207`), so nothing can be sent
into the overhang. The berth half is where the cost is refused, because a parked train pays it all
evening.

**A train that turns round at the destination is not excluded** from the relaxation. It stands there
for the same reason and for the same time; what it does on departure is another journey.

**Both halves are only as good as the measurements.** An unmeasured tile costs a tail nothing, so on
unmeasured track a short train is modelled as lying across many tiles. Where an approach carries no
places, or no length, both halves fall back to the answer 5a already gives.

`core.testABerthAndAPlatformJudgeAnOverhangDifferently` is the test, on Adam's own two squares.

### 5b. What "unmeasured" means

> *"You need to measure total distance between points, not validate that every edge has a length > 0.
> It is only indeterminate if the entire logical segment has length 0."* - Adam
>
> *"Generally, allow it. But: the example ... has measured segments before (that prevent it) ... Make
> sure you are actually enforcing this."* - Adam

- Unmeasured track contributes **no room**. It does not follow that it contributes **no answer**.
- A stretch where **nothing at all** is measured is not judged - the train is allowed. Refusing on no
  information would make an unmeasured layout unusable.
- Where part is measured, **the measured part still binds**: an unmeasured segment must not cancel a
  refusal the measured ones already earned.
- **A stretch is bounded by the segment it lies in.** If the room after the last switch is unmeasured
  but the segment measures one unit, a four-unit train does not fit - a part cannot be longer than
  the whole. This over-states the real room, so it only ever refuses more; nothing new is admitted.

**Mass Assign Lengths and Unmeasured Track: every leg, cut at switches** (FR-089). The length rules between them
read every leg on the railway: the room rule the track past the last switch, the FR-087 allowance (5a) whole legs
back until a reversal, and the tail and berth walks (5c) the standing square, the switch square and the track before
it. Measured on Adam's railway, that reach is every one of its 75 legs. So the tools cut every leg into
**pieces** between two fixed points - a sensor or a switch - and ask for each piece's whole length once; each square is
in exactly one piece. **A switch square is in no piece**: a share of a piece's length landing on it would sit where the
room rule does not count it, so switches are asked for together, one turnout length for all of a page's switches with
none (*"One length for all switches"*). Two switches back to back have no piece between them - there is no square of
track there to measure. **A piece needs a length only while its whole total is 0** - a square inside a measured piece
may rightly hold 0. The share is even; any unit left over goes first to a square a train stands on, whose length its
own tail never spends, which is the refusing direction (MAL-B2).

**A route tile is in no piece, and is never asked for a length** (OB-273). Adam: *"a route
tile should not need or accept a length.  it just implicitly connects things as if it were a
crossing."* It carries no rails of its own - section 8 - so no length rule reads it, the tools do not
ask for it, and a piece's length is shared over the track squares either side of it
(`assignStretchLength`; MT-476). His example: three plain squares and a route tile with 4 typed is 2, 1, 1 by
the even share. **The share stays even** (OB-275; Adam: *"let's stick to a then, since that is more visually
pleasing"*) - the other option, the whole length on one square, would also have made the tail depend on which
square a train happened to stop on (5c spends length square by square).

**A length a route tile already holds is folded into the track beside it** (OB-281; Adam: *"Fold them,
they were likely auto set during the mass assignment run."*). Opening a setup moves each onto the square
beside it along the road it carries - plain track before a switch - so every total is what it was.
`AutonomySession.foldRouteTileLengths`, `core.testMassAssignLengths.testHisFiveRouteTilesAreFoldedWhenHisRailwayIsOpened`.

**A deliberate 0 is an answer** (OB-274). Adam: *"we need to allow a length of 0 as a length
that is set deliberately, i.e. for adjacent tracks.  same meaning to the model, but this will allow
everything to get assigned without what appears to be a skip."* So the walks accept 0, and the piece is
recorded as answered and is not asked about again.  **And it is measured track of no length, to every length rule**
(ADU-C7; Adam: *"we can't possibly have positive lengths everywhere because the tracks just aren't that long.  We
need to find a way to allow trains in atomic mode in as well if the total track lengths allow"*; MT-586).  The route
in, the room walk, the walk that claims a standing train's tail, the berth rule and the tail question count it,
adding nothing, and walk on over it - so a train is let in wherever the measured track holds it in total, and a
train standing there is claimed over the 0 and the track behind it.  They all ask one question, `Edge.isMeasured` -
a length, or every place answered - so what the route in admits the tail walk claims.  **The own-tail rule asks it
too, of the way round** (OB-300) - place by place, since a way round begins and ends inside a leg - so a loop
answered 0 all the way round is judged as track of no length, and no train lying on it is let round into its own
body (§5c).  **A leg with any square nobody answered still ends every walk** - a switch or crossing on it
included, which Mass Assign Lengths still asks for.  On the frozen railway's Tunnel: with BottomSecondary
-> TunnelPre answered 0 throughout, a train of 5 from RampDown is admitted over the 8 measured end to end.  The
Atomic Routes gate keeps its own escape (§5d).
`core.testAnAnsweredZeroIsNotMissing.testAStretchAnsweredZeroIsMeasuredTrackOfNoLength`,
`core.testAutoLayout.testTrackAnsweredZeroIsMeasuredTrack`.  **Route choice by track length counts it 0 too**
(Adam: *"Yes, count answered 0 as 0"*): *Over the shortest track*, *Over the longest track* and balanced priority
count a section nobody measured as 1, so an unmeasured railway does not tie every route at 0, and one answered 0 as
0 - except that balanced priority divides by at least 1, so there a route answered 0 throughout ties with a route
of one unmeasured section (RLA-C7).
`core.testRoutePicking.testAnAnsweredZeroCountsNothingInTheLength`.

**Segment Length's 0 is the same answer, and Clear is its own button** (FR-097; Adam: *"no, add a clear
button"*). A 0 typed there records the run - or each selected square - as answered 0; **Clear**, or an emptied field,
removes the length and the answer. And **an answered 0 is not listed as missing** (*"stop listing answered zeros as
missing"*): the half-measured berth notice, the reversal notice and the berth refusal's "N squares still have no
length" leave answered squares out, while the rules read them as measured track of no length. The refusal is worded on the
running layout, so the build marks an answered place and the runtime keeps the mark.
`core.testMassAssignLengths.testSegmentLengthZeroIsAnAnswerAndClearTakesItAway`, `core.testAnAnsweredZeroIsNotMissing`.

**A square two roads cross is in no piece either** (SET-B2). Adam: *"For crossings: if its
length is set, count that length once in each direction."*  A crossing - or a double curve with track on both of its
roads - is an ordinary square to the room walk, which counts through it, and it lies on two legs at once.  Inside one
leg's piece its share would be shared out by that leg's answer and then counted again on the other road, so both roads
are cut at it and it is asked for on its own, one length for all such squares on a page.  The reduction adds that
length to each road, which is the ruling.  A square whose geometry carries two roads but which only one leg runs over
is ordinary track and stays in its piece.

**The bulk doors on Bulk Tools.**  **Mass Assign Train Lengths (N missing)** (FR-094) walks every
train autonomy would run that has no length, in the same prompt, writing each answer to the locomotive itself - so
Cancel in the editor does not take it back, and the prompt says so; a length is 1 to 40, the one limit both train-length
lists read (`TrainControlUI.ROUTE_TRAIN_LENGTH_MAX`; OB-294).  **It is never greyed, and its label counts the trains
missing a length** (MT-533; Adam: *"This option should never be greyed out completely (show the number of missing
trains in parens)"*); with none missing it goes through every train with the length it has, which Skip keeps.  Train
lengths and station maxima are named apart - *Locomotive* and *Station* in the labels.  **Mass Assign Lengths**
(FR-089) walks the pieces of the page, then its switches, then its crossings.  **Mass Assign Station Max Train
Lengths** (FR-091) walks the stations on the page that will take a train of any length, row by row, and asks each
one's maximum - the walk refuses 0, because 0 IS "any length" and the station already has it.  A
NEGATIVE is refused at the single door as well (SET-B1): `Layout.fromJSON` will not load a configuration
carrying one, and the bulk clear counts any non-zero maximum so that one already stored can be taken off.  **Clear All Track Lengths** (FR-069)
and **Clear All Station Max Train Lengths** (FR-092) each take their setting off every page after a confirmation that says how
many.  The three WALKS - track lengths, station maxima and train lengths - share one prompt: the number box has the keyboard focus
(asked for by name whenever the prompt gains the keyboard; MT-474), Enter submits,
Skip leaves the square as it was, Cancel or Escape stops, and the prompt opens where the last one was left
until a new round is started.  The two clears ask once, in a confirmation.

**Bulk Tools is on every square's menu** - an empty square's and a text square's as well as track's (MT-474; Adam:
*"when right clicking an empty square, show the bulk tools menu option"*).  Its items are about the whole setup
rather than the square under the pointer, so they sit at the bottom, behind a divider.
`ui.testBulkToolsHoldsTheWholeLayoutTools.testAnEmptySquaresMenuHasBulkTools`.

**Segment Length speaks for the whole run** (SET-B3).  A run of plain track has one square that speaks for it, and
that has to stay true now that Mass Assign Lengths shares a piece's length over every square it covers: the dialog
opens on what the RUN measures and writes the typed total to the run, leaving the squares that follow the leader at
nothing.  A shift-click selection is squares rather than runs, and gives each selected square the number typed.

The editor notice about turn-round squares with no length is a different question, said unprompted and so asking less
(MT-305).
