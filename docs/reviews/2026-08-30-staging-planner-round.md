# The staging planner: the five findings the release candidate deferred

**Status:** open

**Prefix for citing these findings elsewhere:** `SG`

**Reviewed:** `HomeStaging.java` and the timetable dispatch loop in `Layout.java`, at `a3128cb7`, plus
Adam's rc3 feedback on MT-228 and MT-233 through MT-235.

**Why this pass happened.** The release-candidate review confirmed five defects in the return-to-home
planner and deliberately did not touch them: "the planner is a large enough surface that I did not want
to change it between a release candidate and your manual testing." Adam's manual testing is done and he
asked for them next. Every one was re-derived here before anything was changed, and one of them was
re-derived by running it - see SG-A3.

**What the round is really about.** Four of the five are the same shape: **a rule that exists in two
places, learned in one of them.** SG-A1 is an exemption the sibling scan twelve lines below carries and
this one does not. SG-A2 is the second half of a two-line rule in `isPathClear`, of which the planner
learned the first line. SG-A4 is an exemption the planner grants and the runtime does not. SG-A5 is a
skip `runLocomotives` learned in the release-candidate round and the dispatch loop one method over did
not. That is the "a lifted rule loses its precondition" pattern pointing the other way: not a rule copied without
its precondition, but a rule NOT copied at all, four times, in the one class whose entire job is to
re-implement the runtime's rules faithfully.

`HomeStaging` says so about itself, in the javadoc on `auditAgainstRuntime`: "Re-implementing a
specification is only safe if you check it against the original." That audit compares the planner and
the runtime **for the present state only**, and every one of these four is either about a hypothetical
state (SG-A1, SG-A2) or about live feedback the audit does not vary (SG-A4). The check that was supposed
to catch this class of defect is real, and its blind spot is exactly where the defects were.

**The one that is not that shape is SG-A3**, and it is the one where the obvious fix was wrong. See its
entry: counting the duplicate properly makes the planner produce a plan, and the plan departs from a
guessed point.

**Also in this round:** SG-B1, SG-B2 and SG-B4 came from Adam's feedback; SG-B3 was found while
writing the manual test for one of last round's own fixes, which is where it should have been
found then.

---

## A - high

### A1 - two homes on one sensor were proved impossible even with both trains already on them

| | |
|---|---|
| **Disposition** | fixed, `core.testHomeStaging.testTwoTrainsAlreadyHomeOnOneSensorAreNotRefused` |
| **Manual test** | [MT-238](../manual-tests/tests.md#mt-238) |

The pairwise goal scan proves that two homes reporting one sensor cannot both be occupied. True of an
ARRIVAL, and an arrival is the one thing that does not happen when the train is already standing there.
The cycle scan twelve lines below it carries exactly this exemption, in these words: "Both already
parked: nothing arrives, so nothing is checked." This one was written without it.

The cost is not a worse plan but no plan: `IMPOSSIBLE` refuses the WHOLE staging run, so a third
locomotive that only needed driving to the next platform never moved, and the two names the operator was
handed were the two trains already where they belonged.

**The release-candidate review called this "the one most likely to bite you in manual testing", and
that was wrong.** It said `BottomMainC` and `BottomMainCTerm` share feedback 4 on Adam's graph, having
read the hand-written `autonomy.json`; the 3.0.0 diagram derives its own, in which `BottomMainCTerm`
does not exist. Adam said so on MT-238 and he is right.

Measured against the derived graph afterwards: sixteen sensors carry more than one Point, and every
group of them holding two active stations is a single SQUARE emitted once per arrival side -
`BottomMainC` four times, `BottomMainB` three, `LowerFront` twice, `BottomInner` four. No two
DIFFERENT station squares share a sensor, and a home on a multi-Point square is refused by
`whyNotAHome` on Adam's own 2026-08-25 ruling.

So the defect is real and general - `AutonomyBuilder` is explicit that a station, its approach guard
and a reversing point can be three Points on one feedback - and his layout is not an instance of it.
MT-238 is superseded for that reason, and the code test is what covers this now.

The control that keeps the rule honest is `testTwoActivePointsSharingASensorAreNeverBothOccupied`, which
has both trains AWAY and must still answer `IMPOSSIBLE`. It does.

### A2 - a plan from a non-station origin was planned, refused, retried, and abandoned

| | |
|---|---|
| **Disposition** | fixed, `core.testHomeStaging.testALocomotiveOnANonStationIsNotPlannedHome` |
| **Manual test** | none - reproducing it means parking a train on a plain sensor, which the planner now refuses up front and names |

`isPathClear` refuses a path starting on an inactive point, and in the very next `if` refuses one
starting anywhere that is not a station. `firstClearRoute` learned the first and not the second, and the
pre-scan the same.

Staging is fully autonomous operation - `executeTimetable` sets `running`, which is exactly why the
reversing-point exclusion had to be moved out of `isPathClear` and into selection - so the rule is in
force for every leg of a Return Home.

What it cost is worse than a refused plan. The run STARTED, the first leg was refused, and the retry
loop asked again every two seconds until it abandoned the run with "the track it needs never became
free", which is not what was wrong: the track was clear, and the train was parked somewhere no automatic
path may begin. A train gets there by being placed by hand, which is the ordinary way to put one on the
layout.

Both halves are fixed, for the reason the inactive half is in both: `firstClearRoute` stops the search
finding the move, and the pre-scan turns "no arrangement found, it may still be possible" into a proof
with the locomotive's name on it.

The test's control is the same graph with the same train on the same square, differing only in whether
that square is a station. Without it the assertions above it would prove nothing.

### A3 - a locomotive held on two points made the goal unreachable, and counting it properly is the wrong fix

| | |
|---|---|
| **Disposition** | fixed, `core.testHomeStaging.testALocomotiveHeldOnTwoPointsIsReportedRatherThanGuessedAt` and `ui.testStagingOutcomeMessages` |
| **Manual test** | none - it needs a path that failed part-way through unlocking |

A locked path reserves every point along it for the one locomotive at once - that is how a junction
behind the train is held against a second train reaching it another way - and a path that failed
part-way through unlocking leaves those reservations standing. Nothing in the model tells a reservation
from a train: `reserve()` and `setLocomotive()` write the same field, and the only difference between
them is whether the other copies are swept.

`misplaced` counted map ENTRIES, so one train counted twice, and `apply` moved it by removing the first
entry it found, leaving the other standing for ever. `misplaced == 0` was therefore unreachable.

**Confirmed by running it**, not by reading: a throwaway probe with one locomotive reserved on two
points of a four-station ring, its home one edge away and empty, answered `NO_PLAN_FOUND` with no moves
in 0 ms.  The review had described the mechanism correctly; running it is what showed the shape -
no moves at all rather than a partial plan, and instantly rather than after a budget burn.

**The obvious fix is wrong.** Counting it properly makes the planner produce a plan, and the plan
departs from whichever of the two points `locationOf` yields first - so a real train is driven from a
place it is not standing. This class's own doctrine, written all through it, is that `NO_PLAN_FOUND`
claims less than it could and claims nothing false; a guessed origin claims something that may be false,
at the highest price this project has.

So it is reported instead, as a new outcome `POSITION_AMBIGUOUS`, with the locomotive named and a remedy
the operator can act on: place the train on the square it is actually standing on, which sweeps the
rest. Adam confirmed that gesture is intended - "Faking a train is OK and intended, since it lets us
test and unstick trains."

**A second finding fell out of writing that.** `describeStagingOutcome` is a switch with a `default` arm
that says "no return plan found", so an outcome added without a case does not fail - it silently tells
the operator the search ran out of room, whatever happened. Nothing tested it. `ui.testStagingOutcomeMessages`
now requires every outcome but `READY` to have its own sentence, and was confirmed to go red with the new
case removed.

### A4 - the planner exempted the mover from its own detection section; the runtime exempts nobody

| | |
|---|---|
| **Disposition** | fixed, `core.testHomeStaging.testATrainIsNotPlannedIntoItsOwnDetectionSection` |
| **Manual test** | none - it needs latching occupancy detection, which the fixtures do not have; the test sets the feedback by hand |

`canEnter` refuses a point whose sensor is held by another locomotive and exempted the MOVER from its
own. `isPathClear` grants no such exemption: it reads the live feedback for the end of every edge and
refuses the path if it is set, whoever is standing there.

The exemption reads as obviously right - a train must not be blocked by its own sensor - but the point it
is standing on is not what was being asked about. `isPathClear` never looks at the point a path STARTS
from. What was being asked about is a DIFFERENT point reporting the same sensor, and while the train is
still on that section the sensor really is set.

Hardware-conditional, which is why nothing caught it: on pulsed feedback the sensor clears behind the
train and the runtime's check never fires. On latching occupancy detection it fires every time.

**Both halves are asserted in the test** - what the runtime offers the train, and what the planner
readies - because the whole defect is that they disagreed, and a test that asked only the planner would
have been restating the planner.

### A5 - one locomotive with no speed ended the whole staging run

| | |
|---|---|
| **Disposition** | fixed, `core.testStagingSkipsALegWithNoSpeed` |
| **Manual test** | none - MT-233 covers the same fault at Start, which is where an operator meets it |

`runLocomotives` learned this in the release-candidate round (`RC-B5`): a locomotive whose preferred
speed is outside 1 to 100 is skipped, with a line in the log, and every other locomotive still starts.
The timetable's dispatch loop one method over never learned it.

`executePath` refuses such a locomotive immediately and returns false, which the retry loop read as a
busy track. It waited, asked again, and after three attempts declared the entry stuck - "the track it
needs never became free" - stopped every train and ended the run. On a Return Home that is every
remaining leg abandoned because one train, placed on the diagram by hand after the configuration was
loaded, had never been given a speed.

**The first version of the fix hung the dispatch loop**, and the test's own deadline caught it in two
minutes. Breaking out of the retry without stamping the entry left the NEXT entry waiting on "the
previous route has not started yet" - `executionTime == 0` - which nothing would ever end. That is a
worse failure than the one being fixed, and it is the third time this round that a fix needed attacking
as hard as the defect. The entry is stamped now: it has had its
turn, and the log line naming the locomotive is the whole account of what it did not get.

`abandoned` is deliberately NOT set. The run does go on to the end, and the abandoned dialog says it
stopped at an entry.

---

## B - medium

### B1 - a phantom row stayed highlighted below the diagram

| | |
|---|---|
| **Disposition** | fixed, `regression.testLayoutEditorBulkEdits.testTheOutlineNeverLandsOnTheGridsPadding` |
| **Manual test** | [MT-236](../manual-tests/tests.md#mt-236), and the third round of [MT-228](../manual-tests/tests.md#mt-228) |
| **Raised by** | Adam, on MT-228 |

The grid is built one row taller and one column wider than the diagram, and those blank labels hold the
GridBagLayout together (`OB-055`). `getValueAt` hands them out like any other square, so a group dragged
onto the last row had its landing outline painted onto the padding underneath - and
`clearBordersFromChildren` deliberately leaves spacers alone, being the grid's furniture rather than
squares, so nothing ever took it off.

**This is not a defect the OB-157 flicker fix introduced.** The spacer exclusion in the clearing routine
predates it; what the flicker fix did was make the outlines stable enough for Adam to notice one that
never went away.

Refused at `highlightLabel`, which every outline goes through, asked of what the label IS - which is how
the clearing side already asks it. Reachable in red as well, by releasing a selection box on that row,
and that goes with it.

Fixed alongside: a move refused for not fitting on the page now repaints. It emptied the landing set,
showed its dialog and returned without redrawing.

### B2 - the timetable showed the form designer's placeholder headings

| | |
|---|---|
| **Disposition** | fixed, `ui.testTimetableColumnHeadings` |
| **Manual test** | [MT-237](../manual-tests/tests.md#mt-237) |
| **Raised by** | Adam, directly |

The form designer starts every table off with four columns called "Title 1" through "Title 4" and four
blank rows. The real headings were installed only by `repaintTimetable`, which cannot be called at
startup - its first act is to ask the running `Layout` for a snapshot, and on a fresh installation there
is no `Layout` to ask. So the Timetable tab showed the placeholder for as long as it took the operator
to set autonomy up, which is exactly when they are most likely to look at it.

The block is MOVED into its own method, not copied: two copies of a column list is how the headings and
the data drift apart.

`LocomotiveStats` has the same designer placeholder and does not have this problem - its constructor
calls `refresh()`, which sets the model.


### B3 - the routing rules explained themselves to nobody

| | |
|---|---|
| **Disposition** | fixed, `ui.testRoutingRuleTooltips` |
| **Manual test** | [MT-241](../manual-tests/tests.md#mt-241) |
| **Raised by** | found while writing MT-240 for OB-156 |

Every routing rule has an explanation written for it - `autolayout.ui.tooltip.pathPreferenceFEWEST_STATIONS`
and eight siblings, translated into all eight languages. Nothing read one of them. The dropdown was
built from the rule NAMES and carried the general sentence about what the control is for; the per-rule
text had no caller anywhere in `src/`.

Seventy-two written and translated sentences, unreachable, on the one control where ten
similarly-worded options have to be told apart.

**It hid a second thing.** "Completely at Random", which last round added for OB-156, is the only rule
with no explanation written at all - a gap that would have been obvious the moment the text was on
screen. And "At Random, Respecting Priority" still described itself as "whatever free route is found
first", which was complete when the rule was called "At Random" and is half the story now.

Found by asking why the new option had no tooltip. **That question only came up because a manual test
was being written for it** - which is an argument for writing the test even when the change looks too
small to need one.

### B4 - the station captions painted over the locomotives

| | |
|---|---|
| **Disposition** | fixed, `ui.testDiagramLooksRight.testTheTrainIsDrawnOverTheStationCaption` |
| **Manual test** | [MT-242](../manual-tests/tests.md#mt-242) |
| **Raised by** | Adam, OB-159 |

**This one and OB-117 are the same overlap seen from opposite sides, and both are right.** A station
caption and the tile under it are separate components. Give the caption the front and the locomotive
standing on that platform is painted over, which is what Adam reported here; give the TILE the front
and the name is painted out and replaced by the tile's own background, which is what OB-117 reported.
Swing has one ordering and no notion of layers, so no arrangement of two components satisfies both -
and the previous fix could only pick a side.

The way out is to stop treating the train as a component's z-order. `TileOverlay.paint` no longer draws
it; `paintTrain` is a method of its own, and `LayoutGrid`'s container paints its children in the order
it always did and then walks them once more asking each tile for its train. Three layers, arranged once,
in the one place that owns all of them.

The OB-117 arrangement is untouched and its test still passes unedited, which is the point: the tile is
still behind the captions, and the locomotive is no longer the tile's business.

**What this pass did not do** is prove the layering on Adam's own diagram. The test builds a tile, a
caption and a container out of plain components and renders them, which establishes the rule but not
that his captions sit where I think they do — MT-242 asks him to look.

### B5 - the 2.8.1 parity comparison, read at the level that matters

| | |
|---|---|
| **Disposition** | reported; the four new journeys need a person, which is [MT-083](../manual-tests/tests.md#mt-083) |
| **Raised by** | Adam - "make sure it is properly tested against the 2.8.1 test harness for regression" |

`docs/tools/parity/` had already been run, on 2026-08-29, and its report was sitting unread. Its
headline - "4 route(s) are missing or reduced in 3.0.0" - counts route VARIANTS, several ways round
between the same two stations. Counted as JOURNEYS, which is the question Adam's tier-4 plan asks, the
answer is different and better:

| | |
|---|---|
| journeys 2.8.1 offers and 3.0.0 does not | 3, and the same 3 for each of the three trains |
| journeys 3.0.0 offers and 2.8.1 does not | 4 |
| concurrency pairs lost | none |

**The three losses are a classification change, not lost capability.** `TopMainR0Park`,
`TopR1ParkLong` and `TopR1ParkShort` are `isReversing` in 2.8.1 and `isTerminus` in 3.0.0. Autonomy
stopped choosing reversing stations on its own in 2.8.0, deliberately and at Adam's request - it is in
that release's changelog - and the harness excludes them on the 2.8.1 side for the same reason. What is
left is that the same three roads are now reached as termini instead, which only a reversible
locomotive may enter, and the parity trains are plain DCC ones.

**The four gains are the direction the plan calls dangerous** and are the thing that still needs a
person:

| Train stands at | Now offered |
|---|---|
| BottomMainA | BottomInnerOtherside |
| BottomMainC | BottomInnerOtherside |
| BottomInner | LowerBack |
| BottomInner | LowerFront |

MT-082 asks of exactly these: does the route reverse where a train cannot reverse, or change track
mid-square at a double curve? That cannot be answered from a route list, which is why MT-083 exists.

**And the journey Adam singled out is offered by BOTH engines.** MT-082's step 18b names
`BottomMainA -> BottomSecondary` as the known-bad one - "a red signal after the end requires a stop at
TopMainR1 or TopMainR2, a constraint that lived in the hand-authored edge config commands and that the
derivation cannot currently express". 3.0.0 offers it, over a route that runs through `TopMainR1`
without stopping. So does 2.8.1. It is a standing gap in both rather than something the derivation
introduced, which is worth knowing before anybody goes looking for a regression that is not there.

**What this did not do.** The report is from the 2026-08-29 jar. Nothing since has touched the graph
builder or `getPossiblePaths` - this round is the staging planner and the diagram's painting - so the
comparison still stands, but a re-run after the next NetBeans build would confirm rather than assume it.

**Update, 2026-09-03: the re-run was attempted and is blocked on one thing only, which is Adam's to
clear.**

The harness could not be set up at all - `TSX-C16`. `REPO` in both `setup-env.sh` and `run.sh` is
`dirname $0/../..`, which was the repository root while these scripts lived at `tools/parity/` and has
resolved to `docs/` since `fb3722f5` moved them. That is fixed, both scripts now find the jar, the
sample layout and the drivers, and `setup-env.sh` builds the environment cleanly against today's
`dist/TrainControl.jar`.

`run.sh` then builds the 3.0.0 configuration from the track diagram (207,089 characters over 5 pages,
as before) and stops recording 2.8.1:

```
Exception in thread "main" java.net.BindException: Address already in use: Cannot bind
    at org.traincontrol.marklin.udp.NetworkProxy.<init>(NetworkProxy.java:42)
```

**A TrainControl started on 2026-09-03 at 02:19:14 is still running** (`java ... TrainControl 0 1 1`,
from `build/classes`, so launched out of NetBeans) and is holding the port. `run.sh` passes
`-Dtraincontrol.anyReceivePort=true`, which is how the test suite shares a machine, but the 2.8.1 jar
predates that flag and cannot take another port - so the 2.8.1 half of any parity run needs the
application closed. Nothing here kills it: it is his session, and it has an unfinished layout edit in
it (`config/autonomy/setup-before-edit.json`, written at 02:19:22).

So the re-measure is **two commands after he closes it**, and it is in the report of things waiting on
him:

```bash
sh docs/tools/parity/setup-env.sh && sh docs/tools/parity/run.sh
```

The side note is fixed regardless. `docs/tools/parity/README.md:102` said `Layout.pathPreference` is
static and is loaded only by the window's menu builder; at HEAD it is a `private volatile` instance
field (`Layout.java:223`) that `fromJSON` reads back out of the configuration (`:7157`), because Adam
asked for the setting to travel with the config rather than with the UI. That is not a footnote for
this file - it is what `PathPreferenceProbe` exists to measure - so the paragraph now says what the
field does, and the "18 of 132 edges" figure beside it is dated to the build it was taken from.

## C - the test gaps

The five the release-candidate review named first. `A101` is above as part of the previous commit; the
four here are the rest of that list. Each was closed by mutating the code it guards and watching the
new test go red, and only that.

### C1 - A113: exclusions were only ever asked about one locomotive

| | |
|---|---|
| **Disposition** | fixed, `core.testLocomotiveExclusions.testExcludingOneLocomotiveDoesNotBarTheOthers` |

All three exclusion tests used a single locomotive, which makes `getExcludedLocs().contains(loc)` and
`!getExcludedLocs().isEmpty()` indistinguishable. Under that mutation one operator's exclusion shuts
the point to the whole fleet - silently, growing with the number of exclusions, and worst on a railway
that uses station exclusions on through routes, which is Adam's. Both enforcement points are covered,
because passage is refused by `isPathClear` and stopping by `pickPath`.

### C2 - A102: the automatic route door was only ever exercised with a conflict

| | |
|---|---|
| **Disposition** | fixed, `regression.testARouteDoesNotThrowSwitchesUnderATrain.testAnAutomaticRouteWithNoConflictStillSetsItsAccessory` |

Every `execRoute(true)` in the corpus was the one route whose turnout IS on a locked path, so the suite
said what that door refuses and nothing said what it does when there is nothing to refuse. The rule is
`skipAccessories = auto && conflict != null`; drop the second half and every s88-triggered route stops
setting anything for as long as autonomy is running - most of an evening here, since the sensors these
routes were written for are shared with autonomy.

### C3 - A105: the window's own capture method was never run

| | |
|---|---|
| **Disposition** | fixed, `regression.testARunSurvivesADiagramEdit.testTheWindowsCaptureActuallyCapturesAndStopsWhenTrainsMove` |

The RULE was tested and the window's source was read for the call; `captureRunningLayout` itself never
ran. It is six guard conditions and one call, and each condition is a way for OB-144's fix to be
silently absent. Both directions are asserted - it captures when stopped, and refuses while trains are
moving.

**The first version of the second half was asking nothing, and the mutation is what showed it.**
`whereItIsAfterARebuild` calls `parseAuto`, which REPLACES the `Layout` object - so the second move and
the running flag were applied to a layout the window no longer held, while the window captured the new
one that nothing had touched. The assertion passed whatever the guard did. Rewritten to re-read the
current layout, and only then did removing the guard fail it.

### C4 - A100: closed, after two fixtures that could not fail

| | |
|---|---|
| **Disposition** | fixed, `core.testAutonomyDiagramReducer.testALockNamesEveryCopyOfTheTrackItLocks` |

The claim is right: `testNoTwoRoutesCanOccupyTheSameTrackUnlocked` asks `reducer.getLocks()`, the
relation between REDUCED edges, and the builder's job is to carry that across the split into one named
edge per arrival side. Truncating its inner loop to the first emitted copy leaves the reduced relation
perfect and the second and later copies unlocked.

A test was written that states the invariant on the emitted JSON alone - if a lock names one copy of a
piece of track it must name all of them - and it **failed its own precondition**: on `test/test_layout`
no piece of track is emitted more than once. 135 edges, 135 distinct base pairs, though 38 of its 56
points ARE split. So the mutation is unobservable on the fixture and the test could only ever pass.

Measured against a real built configuration for comparison: Adam's own layout has 136 edges over 108
base pairs, with **22 pieces of track emitted twice or four times**. The invariant is testable there and
nowhere in the suite.

**What actually produces the multiplicity is a REVERSIBLE square.** `nodesFor` emits a turning copy per
side as well as a plain one, and an edge's pairs are (copies that may leave by the exit side) x (copies
that may be arrived at by the entry side). Adam's graph shows it plainly: every group emitted four times
has ", reverse" in its names. That is not something `test/test_layout` has anywhere a lock also lands.

**And two more fixtures could not fail before one did.** `nodesFor` does not emit the plain copy of a
DEAD END that can turn - a train arriving there has nowhere but back, so the turning copy is the whole
truth about it and the square is emitted once however it is marked. Marking the toe of a switch, and
then the two stubs beyond it, each gave one copy. The square has to be reversible AND have track on
both sides.

So the fixture is a switch with two routes over it - which is what produces a lock at all - and the
straight-ahead road continuing past a reversible sensor. Confirmed by putting a `break` after the first
emitted pair in the builder's lock loop, which is the mutation the suite review named: red.
---

## D - not defects

### D1 - the mover's exemption on the point ITSELF is correct and stays

`canEnter` has two occupancy tests: the point's own occupant, and the occupants of its siblings on the
same sensor. A4 removes the mover's exemption from the second. The first keeps it, and that is not an
oversight: a point occupied by the mover is the mover's own square, and `firstClearRoute` never passes
`from` to `canEnter` anyway.

### D2 - `blockedSensors` explaining a sensor by the ORIGINAL occupancy is correct

It tests `this.start` rather than the hypothetical `state`, which reads like a bug: a sensor stays
"explained" after the train that explained it has moved away in the plan. It is right. The set exists to
name sensors reading occupied for reasons the model cannot see, and a sensor a known train is standing
on is not one of those. Occupancy in the hypothetical is the sibling rule's job, and that one does read
`state`.

### D3 - `getLocomotiveLocation` returning the first match is not a defect to fix here

It has the same arbitrariness A3 is about. It is not worth changing: with A3 in place the planner never
reaches a state where a locomotive holds two points, and every other caller is asking about a railway
where nothing is mid-lock.

---

## What this pass missed, and what it did not look at

**The audit that should have caught four of these.** `auditAgainstRuntime` compares the planner against
`getPossiblePaths` for the present state and logs disagreements. It would have caught A2 and A4 if it
had ever been run on a layout in the right state - a train on a non-station, or a shared sensor reading
occupied - and it is logged rather than enforced, so nothing fails when it disagrees. Making it a test
over generated states is the obvious next thing and is not done here.

**All five named test gaps are closed.** A100, A101, A102, A105 and A113. The other eight of the
thirteen were never transcribed out of the suite reviewer's report and no longer exist in writing -
which is its own lesson about leaving a list in an agent's output.

**The 2.8.1 parity comparison is done**, as B5, and MT-083 confirmed the four new journeys on the
railway.

**The languages have never been looked at.** Adam, 2026-08-30: run the app in each of the eight
languages, screenshot it, and find text that spills or displaces a control. Nothing in this project has
ever checked that - the bundles are verified for keys, escapes and placeholders, and never for LENGTH.
German and Polish routinely run half again as long as the English, and the routing dropdown has already
had to be capped at 230px for exactly that reason. That is the next piece of work.

**A1 was measured against Adam's own configuration, after he said it was wrong** - see the correction
in its entry. The other four are structural and were not.
