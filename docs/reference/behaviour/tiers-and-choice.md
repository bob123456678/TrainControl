# Tiers, autonomy's choice and inactive squares

Part of the behavior reference: [`../behaviour.md`](../behaviour.md) is the index.  Sections keep the numbers and headings they had in the single file, so a citation such as "behaviour.md §5c" still resolves.

## 1. The three tiers: autonomy, manual, Return Home

Three things move trains, and they do **not** obey the same rules.

| | chooses its own destinations | may go where autonomy is told not to | asks the operator anything |
|---|---|---|---|
| **Autonomy** | yes | no | never |
| **Manual** (right-click, Locomotive tab) | no, the operator chooses | yes | yes, about reversals |
| **Return Home** | no, the planner chooses | **yes** | never |

The middle column is about the **parking marking** (*Can Be Chosen in Full Autonomy*) and nothing else. The occupancy
restriction below is not a tier question.

**No tier moves trains over a setup with errors** (MT-263; Adam: *"trains can still be moved manually via
both the track diagram viewer and the autonomy tab, which should throw an error instead"*). Start, both hand doors,
Execute Timetable and Return Home are refused while the setup has errors: one question, `autonomyHasErrors`, in the
setup's own words. **The right-click Return Home item is grayed over such a setup, with the setup's sentence as its
tooltip**, as the right-click Start beside it is; the Return Home, Execute Timetable and Start buttons stay live and
explain on a click. `regression.testAHandSendIsRefusedWhileTheSetupIsBroken`.

**Return Home sits with Manual on the question of where a train may be sent.** `isAutoDestination` appears
nowhere in `HomeStaging`, and Adam: *"Return Home still fills it; only full autonomy leaves it alone."* The Path
Type control in the editor says Manual for this reason.

**Occupancy restrictions bind every tier.** A station can be marked unavailable while some other named square has a
train standing on it, and **autonomy, a hand-driven send and Return Home all obey it**. Adam:

> *"If it's cleaner to go with consistency across the board, then let's enforce the occupancy ruling
> in all modes and then rely on isPathClear. Revert the prior lax ruling."*

One rule, one answer, asked once, in `Layout.isPathClear`.

**Of every square a route arrives at, not only its destination** (OB-295; Adam: *"It means
trains shouldn't be sent to THIS square while trains are STANDING ON or hold a lock on the other specified
station(s)."*). The lock half (the watched square's approach held by a route) is built into every edge arriving at the
square, so it closes a square to routes through it as well. The standing half (a train standing on the watched square)
is asked of every square on the route, so a square trains only pass is shut by it too, and the notice
*"{0} is unavailable while {1} is occupied"* is true of such a square. Return Home's planner asks it of every square a
plan passes, against the occupancy the plan has reached. **Two restrictions can hold each other**: where each watched
train's only way out passes the square the other holds back, neither is sent, by any tier, until one is moved without
TrainControl's routing (driven on the throttle, or taken off the diagram) or one restriction is cleared.
`regression.testStationBlockedByAnotherPoint.testATrainStandingOnTheWatchedPointClosesASquareTrainsPass`,
`core.testHomeStaging.testAHomeBeyondAHeldBackSquareIsNotStagedThroughIt`.

**The planner reads it, and has to.** A plan whose first move the railway refuses is OB-073: the run retries until it
gives up with the fleet half-staged. `HomeStaging` asks the occupancy the **plan** has reached
(`HomeStaging.plannedOccupancy`), and `auditAgainstRuntime` proves the two agree.

**Consequences.**

- **The train leaving the watched square is exempt** (Adam: *"the condition should not apply to
  trains leaving, only departing"*), or a locomotive parked on the yard could never be sent to the platform the yard
  holds back, and the platform would be shut to everybody else meanwhile.
- **Two stations that hold each other back are a real deadlock.** Whichever train arrives last finds its station
  closed, and no order of moves avoids it. Return Home reports `NO_PLAN_FOUND`, not `IMPOSSIBLE` (which names
  locomotives and asserts no arrangement exists). `core.testHomeStaging.testTwoHomesThatHoldEachOtherBackAreADeadlock`.
- **A train that cannot leave where it stands is named** (OB-325). With every other train taken off, a train with no
  move at all (a reversible train of 4 on BottomMainB's turning copy, where every station reachable is shorter) can
  never get home, and Return Home answers `IMPOSSIBLE` with its name and the reason (`HomeStaging.hasAFirstMove`).
  Another train only ever takes moves away, so a train held in only by another is still planned
  (`core.testHomeStaging.testATrainHeldInOnlyByAnotherIsPlannedNotNamed`); a train held only by a sensor that reads
  occupied with no train known on it is not named either, and the answer stays `NO_PLAN_FOUND`
  (`core.testHomeStaging.testASensorOnTheOnlyWayOutIsNotAProof`).

**"Full autonomy" is narrower than "autonomy is running".** Executing a timetable sets the same running flag, and a
Return Home run *is* a timetable, so a rule fenced behind that flag applies to Return Home as well.
`Layout.isFullAutonomyRunning` (running, with no timetable driving it) exists for the narrower meaning; nothing asks it
today.

**How many trains may be out at once is a cap, and it binds only while the railway is running
itself** (Adam: it *"stays enforced only under full autonomy"*). The Autonomy tab's slider, `Layout.maxActiveTrains`,
runs from 0 to 20; **0 means no cap**, the default. A path is refused with `errorMaxActiveTrainsExceeded` when the
locomotives underway have reached it, counted as the UNION of the registered ones and those that have claimed a path
but not yet set off, because two trains once crossed the seconds-wide gap between claiming and being registered
together.

- **A hand dispatch is exempt.** A train sent by hand is marked as a hand send for its journey
  (`Layout.executePathByHand`), so the cap neither counts nor refuses it, and the right-click menu's reasons ask the
  same way. The cap is a preference about how much railway the operator wants moving at once, not a fact about what
  the track will hold; the anti-collision guarantees are the edge locks and the length rules of §5, which every tier
  obeys.
- **The fence is `isAutoRunning`, which is wider than "full autonomy"**, so **a timetable and Return Home ARE
  capped**. If Adam's *"only under full autonomy"* is meant literally, the fence is the thing to change, and that is a
  behavior change nobody has asked for. **The cap is the only rule in this reference with a tier fence on it.**
- `core.testMaxActiveTrains.testTheCapDoesNotBindAHandDispatch` goes red when the fence is removed; the class's other
  claims start by running the railway and would not notice.

**One deliberate difference remains between Return Home and a manual send**: Return Home refuses to start a train
standing on an **inactive** square, where a manual send allows it. The exemption is for a person who has looked at the
railway and decided to move that train now; Return Home plans for every staged locomotive at once, and driving out of
a square somebody switched off is the opposite of what switching it off meant.

## 1a. How autonomy chooses: priority, the routing rule, the yield and the pauses

Everything here is about **full autonomy**. A hand dispatch and Return Home do not choose (section 1), so none of it
applies to them.

Each locomotive on the run list loops: choose a journey, drive it, pause, choose again. **What follows only ORDERS the
journeys the rules of this reference already admit.** A candidate is a station autonomy may send a train to
(`Layout.isSendableDestination`: active, a destination, choosable in full autonomy, not a reversing point), with nothing
standing in its block; not a terminus for a train that cannot reverse, nor, for one that can, a terminus from which no
station it fits is reachable again (`Layout.leavesForAStationItFits`, asked by the Why Not Moving? reasons too); and not
a station that excludes this train (`Point.excludes`: one its list names or, where the list names the only locomotives
allowed, FR-121's `excludedLocsMode: whitelist`, any it does not, a locomotive added later included). A route to it is
used only if it turns the train nowhere on the way and `isPathClear` passes it.

**A paused train is left where it stands** (FR-117; Adam: *"in autonomy configs, track the paused/unpaused
status of locomotives, as designated on the autonomy locomotive controls tab"*, and, asked what a pause keeps a train
out of, *"Autonomy and Return Home"*). Autonomy never chooses a journey for it (`Layout.pickPath`), Why Not Moving? says
it is paused, and Return Home leaves it (section 6). A hand send still takes it, and so does Execute Timetable (Adam,
asked: *"Timetable can run paused trains"*). **The pause belongs to the configuration**: listed with its settings
(`pausedLocomotives`, written only while it names somebody), applied to every train as a configuration loads (start-up,
a load, an import, a rebuild), and the log names the paused ones (*"Make sure the log shows what locomotive are paused
when the autonomy import happens"*). Three doors set it: the Auto tab's pause button, which changes the train alone
while trains run and is taken into the setup by the next fold; and the **... Is Active** tick on the track diagram's and
the autonomy editor's right-click menus, offered where a train stands and only while autonomy is stopped, which changes
the train, the setup and the railway's settings as built together (so a later press of the pause button is not undone
by a rebuild). A rename of a train is carried into every configuration's list, and a deletion takes it off. A paused
train's icon is gray on the diagram. `core.testAPausedTrainStaysPaused`, `ui.testTheActiveTickPausesATrain`,
`core.testAutonomyDiagramMonitor.testAPausedTrainIsDrawnGrey`.

**Station priority is absolute, and it is the default behavior.** Every station has a whole-number priority, 0 unless
set (clearing the box means 0). The candidates are shuffled and then sorted highest first, keeping the shuffle within a
priority, so equals are chosen at random. A lower priority is looked at only when nothing of a higher one has a clear
route. So *"send it to the highest-priority station it can reach"* needs no setting of its own.

**The routing rule decides between the stations of that one priority** (`Layout.PathPreference`, the Autonomy tab's
routing dropdown). It is stored in the configuration, not the program's preferences (Adam: *"that way it travels with
the config, not the UI."*).

| rule | chooses, within the highest priority that has a clear route |
|---|---|
| **At random, respecting priority** (`RANDOM`), the default | the first clear route found; the shuffle makes that random |
| **Completely at random** (`RANDOM_ANY_STATION`) | the first clear route found, **with priority ignored** (OB-156, Adam: *"one completely random, and one that respects priority"*) |
| **Fewest / most stations** | the route passing the fewest, or most, OTHER stations |
| **Shortest / longest track** | the route over the least, or most, measured track; a section with no length counts ONE (Adam: *"a min length option that tries to minimize total track length, where we count each s88 as length 1 by default"*; `Layout.lengthOf`) |
| **Fewest / most sensors** | the route over the fewest, or most, distinct s88s: sensors, not graph hops, because a square is several Points |
| **Least recently visited** | the station longest without a train arriving; one never visited wins outright |
| **Balanced priority** (`BALANCED_PRIORITY`) | the most priority per unit of track, **the one rule that crosses priorities**, so a nearer, less important station can beat a far important one (Adam: *"one that balances priority vs distance as a ratio"*). With every priority at 0 it is the shortest route |

The "most" rules are for a railway somebody is watching, which wants its trains taking the long way round.

**A train that has gone quiet is given a turn** (`maxLocInactiveSeconds` on the Autonomy tab; 0, the default, switches
it off). After each attempt a train checks whether another train on the run list (not paused, and with somewhere
autonomy could send it) has gone that many seconds longer than itself without a journey. If so it waits up to thirty
seconds (`Layout.YIELD_SECONDS`) for that train to move before choosing again. A courtesy, not a queue.

**The pauses are a minimum and a maximum in seconds**; neither may be negative or cross the other (the setter refuses
both).

- **After every attempt**, found or not, a train waits the MINIMUM before choosing again.
- **Where a pause is meant to look like a person did it** (before a departure's functions, either side of an arrival's
  functions, before the change of direction at a terminus or a reversing point on the way, and when there was nowhere
  to go) it waits a random whole number of seconds between the two.

With the minimum at 0 a train with nowhere to go still waits a quarter of a second (`NO_PATH_IDLE_MS`), or it would
search the graph as fast as the processor allows. A pause is skipped once the run has been asked to stop.

**How many trains may be out at once is section 1's cap**, and it binds here as well.

The per-rule explanations are the dropdown's tooltip, for the rule chosen (OB-163, OB-265).

## 2. Inactive squares

> *"Inactive really means nothing can pass."* - Adam

An inactive square is refused as a **destination** and as an **intermediate** point, in every tier.

**The square a train is already standing on is exempt.** Switching a square off around a train is how you take it out
of service; the train then has to be driven off by hand, and a rule that refused that would strand it.

### 2a. A square that stops being a station keeps what it had

Adam, asked what setting a station to `No - Trains Cannot Stop Here` should do with its settings: *keep
them, ignored while it is not a station*. A demoted square keeps its caption, its barred arrival sides, its exit and
entry guard signals, its turn setting, its longest train (OB-291) and what blocks it. While it is not a station none of
them is read: no caption is drawn, no guard built, no side barred. Made a station again, it has them all back.
`core.testATrainIsPlacedOnceAndKeepsItsFacing.testADemotedStationHasItsSettingsBackWhenItIsAStationAgain`,
`testADemotedStationsSettingsAreIgnoredWhileItIsNotAStation`, `core.testACaptionStaysWithItsSquare.testOpeningKeepsTheCaptionOfASquareThatIsNotAStationNow`.

**And a route deleted takes the calls to it with it** (Adam, asked: remove the calls). The question before deleting a
route names the routes that run it; deleting it takes those "run this route" commands out of them, each logged, so a
new route given the old name does not inherit them. At the window's door only: editing a route deletes and remakes it
inside the model, and must not. `core.testAdvancedRoutes.testADeletedRouteIsNotRunByTheRoutesThatCalledIt`.
