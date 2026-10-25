# Parking and Return Home

Part of the behavior reference: [`../behaviour.md`](../behaviour.md) is the index.  Sections keep the numbers and headings they had in the single file, so a citation such as "behaviour.md §5c" still resolves.

## 6. Parking and Return Home

- Return Home stages every locomotive that has a home, as one plan - **but a paused one** (FR-117): for the plan its home
  is where it stands, so it is home already and nothing is planned for it, the search never moves it out of another
  train's way, and the log says it is paused and stays where it stands. The check after a run counts it home, so the
  window does not report the run as stopped short. `core.testAPausedTrainStaysPaused`.
- **Place All Trains at Their Homes** (FR-115, Bulk Tools; Adam: *"Teleports locomotives to their home stations,
  facing the correct way, and clears all other locomotives from other stations (without actually moving anything)"*)
  rewrites the setup only: every train with a home is put on it facing the way it was homed (a home with no recorded
  facing takes the facing of the copy the running layout homes it on), every other train is taken off, and the running
  layout is rebuilt from that, naming every train it moved so the rebuild puts none of them back. Nothing is sent to the
  railway; a pause is kept; it is refused while trains run. `core.testPlaceAllAtTheirHomes`.
- One locomotive has one home; assigning a home takes it away from wherever it was.
- **A home has a facing, and Return Home brings the train back in it** (OB-282; Adam: *"yes, it should
  accomplish the facing.  but it's also reasonable to expect that the input facings are ones realistic on the
  layout.  we shouldn't allow an impossible facing to be saved."*). The facing is **the way the train is facing
  when the home is set** (*"Direction it is facing when home is set"*); a home given to a train standing elsewhere
  **asks** which way it should face (*"prompt the user for the direction"*), offering only the facings of copies
  trains may arrive at. The setup keeps it as `homeFacing`, the build puts the home on the copy facing that way and
  marks it, and a home set on the running diagram is held to the copy it was set on. Return Home counts a train
  home only on a copy FACING that way (MT-165 asked for the square alone): on a square with two platforms facing
  opposite ways, any copy would count a train turned round as home. Not by arrival side: the home copy's turning twin
  shares its arrival and points the other way, so on a square trains may turn at (BottomMainB, EN57-947's home) a
  train left turned round there would be reported already home (TDY-B1, AUT-B2);
  `core.testATrainComesHomeFacingTheWayItWasHomed.testATrainTurnedRoundOnItsHomeIsNotHome`. A home with no facing -
  an old setup, or a copy no train may arrive at - is still the square, either way.
  `core.testATrainComesHomeFacingTheWayItWasHomed`,
  `core.testATrainIsPlacedOnceAndKeepsItsFacing.testAHomeRemembersTheWayItsTrainWasFacing`.
- It refuses an inactive **start** (see §1) and obeys every length rule in §5.
- It **does** read the occupancy restrictions of §1, in both halves, since they bind every tier (Adam). **Planning**
  asks them against the occupancy the plan has reached (`HomeStaging.plannedOccupancy`), and **execution** asks them
  because every leg goes through `Layout.isPathClear`, which has no tier fence on that rule at all.

  The two have to move together: a planner that offered a leg the runtime refuses is OB-073 - the run
  retries until it gives up and stops with the fleet half-staged - and `auditAgainstRuntime` is what
  proves the planner offers nothing the runtime would refuse.

  **So two homes that hold each other back are a deadlock.** Whichever train arrives last finds its
  station closed by one already parked, and Return Home answers `NO_PLAN_FOUND` - not `IMPOSSIBLE`,
  which names locomotives and asserts no arrangement exists. See §1.
- It never asks the operator anything: the operator's decision was made when the homes were set.
- **The shortest plan where it can be had, and some plan where it cannot** (OB-230). The fifteen-second
  budget is shared: a third looks for the plan with the fewest moves from where the quick first pass left the trains,
  a third for the fewest from where they stand (the retry for a first pass that boxed itself in), and if both find
  nothing, the last third looks for any plan, weighing the moves still needed five times the moves made, so it may
  return a longer plan than the shortest (halves, fewest then any, when the first pass moved nobody). On Adam's
  measured layout, six trains each with a way home need the second kind of search to find a plan at all.
  `core.testReturnHomeFindsAPlanOnAFullRailway`.
- **A train that cannot reverse is turned on the way only to go home** (AMH-B1; Adam: *"this should
  only be allowed if the train is going to reverse into its berth on the next turn"*). Every leg of a plan ends
  with the train turned round if it stops at a terminus or a reversing point, so a plan may stop such a train on one
  only when the train has a home and its next move takes it there; a train the railway already had standing on one
  moves as before. Where the only arrangement needs anything else, the answer is `NO_PLAN_FOUND`.
  `core.testHomeStaging.testATrainThatCannotReverseIsTurnedOnTheWayOnlyToGoHome`.
- **And not turned in the middle of a move either** (AMV-B1). A route may turn a train at a
  reversing point on the way and carry on, which for a train that cannot reverse means running on backwards. That is
  allowed only where the move ends somewhere the turn was for: its home, a berth, or a square it comes to rest facing
  out of - which is how a train backs into a berth past a reversing point (MT-245). Adam, asked whether the ruling
  covers a turn mid-move: *"It should be the first option, but the locking mechanism will refuse it. That's why we
  started the 2 step process for parking, which is OK in my opinion."*
  `core.testHomeStaging.testATrainThatCannotReverseIsNotTurnedMidMoveAndSentOn`.
- **It knows where the tails of the trains it moves will lie** (OB-228; Adam: *"the path
  stayed blocked"*). A train the plan has moved stands at the end of the route the plan gave it, having come in by
  that route's last rail and along that route - what an arrival records - so its tail is walked by the runtime's own
  code (`Layout.walkOneTail`, asked through `edgesATailWouldCover`) and no later move is routed over it. A train
  that has not moved is judged by the tail it has on the railway (OB-184) - **every such train on a piece of
  track, not the first one found** (AUT2-C2): track two tails lay across stays shut until both trains have
  moved, as the railway, which sees the tails as they are, would keep it. Making every move it can, in
  the order it meets the trains, can leave a tail across the run another train needs, so when the search from
  that arrangement finds nothing it searches again from the start.
  `core.testReturnHomeKeepsClearOfTheTailsItLeaves`.

**Whether it is on offer is asked once, off the event thread** (OB-192). "Is anything
away from home" is cheap but not free of a lock: it builds a `HomeStaging.snapshot`, which calls
`Layout.getHomeStations` - `synchronized` on the `Layout`, the same monitor a dispatch holds for the
whole of `configureAndLockPath` and a path search holds for the whole of `getPossiblePaths`. Asked
from the event thread, as the button refresh, the diagram's right-click menu and the click handler
each did, that is the freeze of §5c by a second door. There is one asker, on a worker; the button
it paints is what every other surface reads, so no two surfaces can describe the situation
differently and none of them can wait on the railway to find out.

**THE RULE IS GENERAL, AND IT IS A CENSUS RATHER THAN A HABIT** (D3-B2):

> **Nothing on the event thread may call a `synchronized` method of `Layout`.**

They all take one monitor. A dispatch holds it for the whole of `configureAndLockPath` - a sleep per
accessory of the path - and `AutoLocomotiveStatus.findPaths` holds it for a search of the whole graph
on every panel refresh with nothing running at all. Waiting for it on the event thread is a window
that does not repaint and controls that do not answer while the trains go on running, which is what
Adam reported as OB-192.

`testNothingOnTheEventThreadTakesTheRailwaysMonitor` reads the list of `synchronized` methods out of
`Layout.java` and requires **every** call to one of them from `gui/` or `automationui/` to be written
down with the thread it is on. The way past is a line in that list, and it must say either
`OFF THE EVENT THREAD:` and name the thread, or `ON THE EVENT THREAD:` and give the reason - a menu
item the operator has just clicked and a placement made from a modal dialog are both legitimate, and
a guard with no way past is one people delete. What it cannot do is prove a thread, so
`testTheDiagramRefreshDoesNotWaitOnTheRailway` still *measures* the doors that matter with the monitor
actually held. The census says which doors exist; the measurement says they are shut.

Doors already shut: the diagram's **right-click menu**, which gathers its path list
on a worker before the menu is built (D3-A1); the **station captions**, whose "can autonomy choose
this square" answer is worked out on `CoveredTrackRenderer` and read from a field - so a page change
or a Show Inactive Labels toggle during a run costs a set lookup rather than the rest of somebody's
departure (D3-B1); and the editor's **"why is this train not moving"** tool, which asks the railway on
its own worker and paints the report when it lands (D3-C1).

**There is no `ON THE EVENT THREAD` allowance left for a graph walk.** Every one that remains is a
change of state the operator has just confirmed, or a read made when nothing else can be holding the
monitor; a rule with one standing exception is a rule with a way in.
