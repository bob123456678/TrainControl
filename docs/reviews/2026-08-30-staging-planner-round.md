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

**The thirteen A-severity test gaps** from the release-candidate suite review are still outstanding.
`grep -rn getLockEdges test/` still returns nothing.

**The 2.8.1 parity regression** Adam asked for - "make sure it is properly tested against the 2.8.1 test
harness for regression" - is not run in this round.

**Nothing was measured against Adam's own configuration** for these five, unlike the route-tile rules
last round. A1 names two of his squares from the review's reading of his graph; the rest are structural.
