# Parking and Return Home

Part of the behavior reference: [`../behaviour.md`](../behaviour.md) is the index.  Sections keep the numbers and headings they had in the single file, so a citation such as "behaviour.md §5c" still resolves.

## 6. Parking and Return Home

- Return Home stages every locomotive that has a home, as one plan, **but a paused one** (FR-117): for the plan its home
  is where it stands, so nothing is planned for it, the search never moves it out of another train's way, and the log
  says it is paused and stays where it stands. The check after a run counts it home.
  `core.testAPausedTrainStaysPaused`.
- **Place All Trains at Their Homes** (FR-115, Bulk Tools; Adam: *"Teleports locomotives to their home stations,
  facing the correct way, and clears all other locomotives from other stations (without actually moving anything)"*)
  rewrites the setup only: every train with a home is put on it facing the way it was homed (a home with no recorded
  facing takes the facing of the copy the running layout homes it on), every other train is taken off, and the running
  layout is rebuilt from that, naming every train it moved so the rebuild puts none of them back. Nothing is sent to the
  railway; a pause is kept; it is refused while trains run. `core.testPlaceAllAtTheirHomes`.
- One locomotive has one home; assigning a home takes it away from wherever it was.
- **A home has a facing, and Return Home brings the train back in it** (OB-282; Adam: *"yes, it should
  accomplish the facing.  but it's also reasonable to expect that the input facings are ones realistic on the
  layout.  we shouldn't allow an impossible facing to be saved."*). The facing is **the way the train is facing when the
  home is set** (*"Direction it is facing when home is set"*); a home given to a train standing elsewhere **asks** (*"prompt the user for the direction"*),
  offering only the facings of copies trains may arrive at. The setup keeps it as `homeFacing`, the build puts the home
  on the copy facing that way and marks it, and a home set on the running diagram is held to the copy it was set on.
  Return Home counts a train home only on a copy FACING that way (MT-165 asked for the square alone): on a square with
  two platforms facing opposite ways, any copy would count a train turned round as home. Not by arrival side: the home
  copy's turning twin shares its arrival and points the other way, so a train left turned round on BottomMainB
  (EN57-947's home) would be reported already home.
  `core.testATrainComesHomeFacingTheWayItWasHomed.testATrainTurnedRoundOnItsHomeIsNotHome`. A home with no facing (an
  old setup, or a copy no train may arrive at) is still the square, either way.
  `core.testATrainComesHomeFacingTheWayItWasHomed`,
  `core.testATrainIsPlacedOnceAndKeepsItsFacing.testAHomeRemembersTheWayItsTrainWasFacing`.
- It refuses an inactive **start** (see §1) and obeys every length rule in §5.
- It **does** read the occupancy restrictions of §1, in both halves, since they bind every tier (Adam). **Planning**
  asks them against the occupancy the plan has reached (`HomeStaging.plannedOccupancy`), and **execution** asks them
  because every leg goes through `Layout.isPathClear`. The two move together: a planner offering a leg the runtime
  refuses is OB-073, and `auditAgainstRuntime` proves the planner offers nothing the runtime would refuse.

  **So two homes that hold each other back are a deadlock**, and Return Home answers `NO_PLAN_FOUND`, not `IMPOSSIBLE`.
  See §1.
- It never asks the operator anything: the decision was made when the homes were set.
- **The shortest plan where it can be had, and some plan where it cannot** (OB-230). The fifteen-second budget is
  shared: a third looks for the plan with the fewest moves from where the quick first pass left the trains, a third for
  the fewest from where they stand (the retry for a first pass that boxed itself in), and if both find nothing, the last
  third looks for any plan, weighing the moves still needed five times the moves made (halves, fewest then any, when the
  first pass moved nobody). On Adam's measured layout, six trains each with a way home need the second kind of search.
  `core.testReturnHomeFindsAPlanOnAFullRailway`.
- **A train that cannot reverse is turned on the way only to go home** (Adam: *"this should
  only be allowed if the train is going to reverse into its berth on the next turn"*). Every leg ends with the train
  turned if it stops at a terminus or a reversing point, so a plan may stop such a train on one only when its next move
  takes it home; a train already standing on one moves as before. Otherwise the answer is `NO_PLAN_FOUND`.
  `core.testHomeStaging.testATrainThatCannotReverseIsTurnedOnTheWayOnlyToGoHome`.
- **And not turned in the middle of a move either.** A route may turn a train at a reversing point and carry on, which
  for a train that cannot reverse means running on backwards; that is allowed only where the move ends somewhere the turn
  was for: its home, a berth, or a square it comes to rest facing out of (how a train backs into a berth past a
  reversing point, MT-245). Adam, asked whether the ruling covers a turn mid-move: *"It should be the first option, but
  the locking mechanism will refuse it. That's why we
  started the 2 step process for parking, which is OK in my opinion."*
  `core.testHomeStaging.testATrainThatCannotReverseIsNotTurnedMidMoveAndSentOn`.
- **It knows where the tails of the trains it moves will lie** (OB-228; Adam: *"the path
  stayed blocked"*). A train the plan has moved stands at the end of the route the plan gave it, having come in along
  that route, so its tail is walked by the runtime's own code (`Layout.walkOneTail`, through `edgesATailWouldCover`) and
  no later move is routed over it. A train that has not moved is judged by the tail it has on the railway (OB-184),
  **every such train on a piece of track, not the first one found**: track two tails lie across stays shut until both
  have moved. Making every move it can in the order it meets the trains can leave a tail across the run another train
  needs, so when the search from that arrangement finds nothing it searches again from the start.
  `core.testReturnHomeKeepsClearOfTheTailsItLeaves`.

**Whether it is on offer is asked once, off the event thread** (OB-192). "Is anything away from home" builds a
`HomeStaging.snapshot`, which calls `Layout.getHomeStations`, `synchronized` on the `Layout`. There is one asker, on a
worker; the button it paints is what every other surface reads, so no two surfaces can describe the situation
differently and none waits on the railway.

**THE RULE IS GENERAL, AND IT IS A CENSUS RATHER THAN A HABIT:**

> **Nothing on the event thread may call a `synchronized` method of `Layout`.**

They all take one monitor. A dispatch holds it for the whole of `configureAndLockPath` (a sleep per accessory of the
path), and `AutoLocomotiveStatus.findPaths` holds it for a search of the whole graph on every panel refresh. Waiting for
it on the event thread is a window that does not repaint while the trains run on (OB-192).

`testNothingOnTheEventThreadTakesTheRailwaysMonitor` reads the list of `synchronized` methods out of `Layout.java` and
requires **every** call to one of them from `gui/` or `automationui/` to be written down with its thread: a line saying
`OFF THE EVENT THREAD:` and the thread, or `ON THE EVENT THREAD:` and the reason (a menu item the operator has just
clicked, a placement made from a modal dialog). A census cannot prove a thread, so
`testTheDiagramRefreshDoesNotWaitOnTheRailway` *measures* the doors that matter with the monitor actually held.

Doors already shut: the diagram's **right-click menu**, which gathers its path list on a worker before the menu is built;
the **station captions**, whose "can autonomy choose this square" answer is worked out on `CoveredTrackRenderer` and read
from a field; and the editor's **"why is this train not moving"** tool, which asks the railway on its own worker.

**There is no `ON THE EVENT THREAD` allowance left for a graph walk.** Every one that remains is a change of state the
operator has just confirmed, or a read made when nothing else can be holding the monitor.
