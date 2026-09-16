# The Return Home planner and its execution, reviewed whole

**Status:** open 2026-09-15 - round 1 fixed B1 to Adam's ruling and C3 (claims `8818d8cd`, fix `64169b0b`); round 2 fixed B2 to his answer on the hardware (claims and fix `c02f7000`); C1 measured and DEFERRED as OB-230 at Adam's word; C2 answered by claims

**Prefix:** AMH (checked free, with AMG, AMS, AMR and AMV: `SELECT DISTINCT ref FROM finding` in `docs/manual-tests/triage.db`, every declaration spelling in `docs/reviews/`, and a grep of `src/`, `test/` and `docs/`)

**Covers** `HomeStaging` in full and the Return Home path through `Layout` (`executeTimetable`, `turnsOnArrival`, `shouldReverseAt`, `isPathClear`) - one of four Fable reviewers Adam asked to look at the full autonomy model (AMG, AMS, AMR, AMH).  Read-only; every fix below was run.

**On the pending tests.**  B1 adds MT-445.  B2 bears on MT-440: on latching detection its expected "no plan" could hold for B2's reason rather than the one it is about.

---

## A - wrong behaviour on the layout

None.

---

## B - incorrect results or refusals in specific configurations

| id | status | where |
|---|---|---|
| AMH-B1 | Fixed | `HomeStaging.astar` - a train that cannot reverse rested on a square that turns it, then sent on |
| AMH-B2 | Fixed | `HomeStaging.blockedSensors` - a standing train's latched tail closes a section for the whole plan |

### AMH-B1 - Return Home turned a train that cannot reverse and sent it on

| | |
|---|---|
| **Disposition** | Fixed, to Adam's ruling |

Every active destination was a candidate rest, `canRest` has no reversibility rule, and the run turns a train at every terminus and reversing point a leg ends at (`ALWAYS_REVERSE`).  So a train that cannot reverse could be stepped aside onto an ordinary terminus - one the right-click menu will not offer it since OB-205 - turned there, and driven on running the other way.  SEV-B2's fence recorded *"whether a plan should route a non-reversible train to a turning copy at all"* as raised with Adam and not decided.  Adam, on this review: *"this should only be allowed if the train is going to reverse into its berth on the next turn."*  (He recalled it being in behaviour.md; it was not written anywhere, and §6 now carries it.)

**Confirmed by running.**  `core.testHomeStaging.testATrainThatCannotReverseIsTurnedOnTheWayOnlyToGoHome` - one way home, P -> T1 -> T2 -> R through two termini, so the only plan turns the train twice.  Red: *"HS alpha -> HS T1 then HS alpha -> HS T2"*.  Its control - the same railway for a train that can reverse - is READY with that plan.

**Fixed, round 1.**  In the successor loop: a train that cannot reverse, which the plan has moved onto a terminus or reversing point that is not its home, moves next only to its home; and one with no home is never moved onto such a square.  A train the railway already had standing on one moves as before.  Where nothing else works the answer is `NO_PLAN_FOUND`.  The existing triangle claim, where the stepped-aside train goes home next, is unchanged.  MT-445.

### AMH-B2 - a latched tail closes a section for the whole plan

| | |
|---|---|
| **Disposition** | Fixed, round 2 - Adam, asked: **"Yes, tails hold sensors."** |

`blockedSensors` treats a sensor as explained only where a train stands at start on a point reporting it, and the set is the same for every move of the plan.  On latching detection a long train at A holds the sensor of the point behind it; the planner then blocks that point for every move, including moves after A's train has left, while the runtime reads live feedback.  The shape is "planner stricter", its symptom `NO_PLAN_FOUND`.  `coveredAtStart` already knows which points a standing tail reaches, so such a sensor could count as explained.

**Confirmed by running**, once the claim was written the right way round.  The first version asserted a plan whose
FIRST move entered the held section, and that is a plan the railway refuses - `isPathClear` reads the live feedback -
so it would have made the planner the looser half, which is OB-073.  The defect is narrower: the section stayed shut
for the rest of the plan as well.  `core.testHomeStaging.testASensorHeldByAStandingTrainsTailIsNotBlocked` gives the
train whose tail it is a home of its own on a spur, so it can leave without entering the held section.  Red:
*"expected [READY] but found [NO_PLAN_FOUND]"*, with two controls - the sensor clear, and a sensor nothing accounts
for still blocking.

**Fixed, round 2.**  A sensor a standing train's tail lies over stops being an unexplained obstruction once the plan
has moved that train; while the train is still there it blocks, because the railway blocks it.  So `blockedSensors`
now reads the arrangement it is asked about - see D5.  MT-450.

---

## C - documentation, tests, minor

| id | status | where |
|---|---|---|
| AMH-C1 | Deferred - **OB-230** | the A* budget on the constrained (measured-length) problem.  Measured below; the fix is the heuristic, which Adam deferred until the fully measured layout arrives |
| AMH-C2 | Answered by claims | the shared-metal half of the tail rule and the reversing-intermediate room check now have one each, both mutation-checked |
| AMH-C3 | Fixed | test comments that the code contradicts |

### AMH-C1 - completeness and determinism, with a scatter that shows it

| | |
|---|---|
| **Disposition** | Deferred as **OB-230**, at Adam's word (2026-09-15): *"I want to do the heuristic, but I think this needs to be deferred until I deliver you the fully measured layout.  So, let's mark this as an open OB for now and continue."*  The measurement below is recorded there; the agreed fix is the heuristic, `misplaced` giving no credit for getting closer |

**An instance, from the round 3 battery (2026-09-15).**  `core.testTrainsComeHomeToTheirPlatforms` let autonomy run and then could not be planned home from what it left:

| train | reversible | standing on | home | route home | turns on the way | clear now |
|---|---|---|---|---|---|---|
| loc 0 | no | `TopMainR2 (northbound)` | `BottomMainA (eastbound)` | yes | no | no |
| loc 1 | no | `LowerBack` | `TopMainR1 (northbound)` | yes | no | no |
| loc 2 | no | `BottomSecondary` | `TopMainR2 (northbound)` | yes | no | no |
| loc 3 | yes | `BottomInnerOtherside` | `Tunnel (southbound)` | yes | no | yes |
| loc 4 | yes | `LowerFront (eastbound, reverse)` | `LowerFront (eastbound)` | already home | - | - |

`NO_PLAN_FOUND`, with an empty blocked list.  **Every train has a route home**, and three of those routes are merely not clear yet - loc 0 is standing on loc 2's home, and so on.  That is an ordinary rearrangement, and the order that solves it is visible by eye: loc 3 home first (its route is clear), then loc 0 off loc 2's home, then loc 2, then loc 1.

**It is not the turn rulings.**  Asked again with EVERY train made reversible, the answer is still `NO_PLAN_FOUND` - so neither AMV-B1 nor AMW-B3 is what refuses it, and the round 3 changes are not implicated.

**MEASURED, on Adam's instruction** (2026-09-15: *"the whole point of the A* is to figure out how to rearrange other trains to park things when they belong, so we also need to differentiate issues with the track diagram from issues with the algorithm.  i recommend relaxing restrictions (track/station lengths) in the layout used for the A* tests."*).  The same arrangement, one relaxation at a time:

| variant | outcome | time |
|---|---|---|
| trains three units long | `NO_PLAN_FOUND` | 15.1 s - the whole budget and its retry |
| train lengths zeroed | **READY, 8 moves** | 6.6 s |
| and station maximums cleared | READY | 6.6 s - **none were set** |
| and FR-001 restrictions cleared | READY | 6.6 s - **none existed** |
| and every train reversible | `NO_PLAN_FOUND` | 15.0 s |

Then ten presses of the same arrangement with the lengths relaxed, to see whether the planner is INTERMITTENT on a railway where nothing but the order is in question: **READY on 10 of 10, 6.2 to 7.0 seconds, the identical eight-move plan every time.**

What that settles, and it corrects this section twice over:

- **This arrangement is not the budget case at all.**  Relaxed, it is solved reliably and identically on every press - the identical plan being item 8's determinism fix doing its job.  So the pinned class built from it is a REGRESSION instrument, and citing it as evidence of a search limit would be wrong.
- **The 15-second failures are real, and they are elsewhere.**  Two of them: the same arrangement with three-unit trains, which cannot be planned at all, and the live class's OTHER scatters, which reach the deadline.  Since the live class leaves train lengths unset, its failures are harder ARRANGEMENTS running out of time rather than length refusals - and none of them exhausted `SEARCH_LIMIT`'s 50000 configurations, so time is the binding constraint, not the state space.
- **Length is what makes the problem hard**, and that matters for real operation rather than for the tests: on Adam's railway the trains do have lengths, so a real Return Home faces the constrained problem this probe could not plan at all in 15 seconds.  The tests relax it on his instruction precisely so they measure the search instead.
- **The earlier sentence here was wrong** - one route per (train, station), so a longer route is never generated.  That was the validation's framing, and `firstClearRoute` searches over the points that can be entered, so it finds a longer clear route when one exists.  It is left visible rather than deleted, because what replaced it came from measuring instead of reading.
- **Length is the one restriction worth relaxing in an A* test**, exactly as he recommended: station maximums and FR-001 are not in play on his railway at all, and zeroing the train lengths is what turns this arrangement from unplannable into an eight-move plan.
- **More freedom can LOSE a plan**: making every train reversible widened the branching factor and the answer went back to `NO_PLAN_FOUND` inside the same deadline.  Non-monotonic behaviour of that kind is a budget symptom, and worth knowing before anyone reads a red as a rule.
- **One artefact of the measurement, recorded so it is not repeated:** the probe gave its trains a three-unit length, which the live class does not - so the battery's own failure is the borderline-budget case rather than the length case.

The probes are not committed; the numbers above are their output.

### The finding as the review first stated it

`firstClearRoute` returns the first route its breadth-first search finds over `Layout.getNeighbors`, which shuffles, and A* generates one successor per (train, station).  Since OB-228 the route decides the tail and the tail decides what is possible later, so a plan that needs a longer route is never generated, and among equal routes the same railway can answer READY on one press and NO_PLAN_FOUND on the next.  `NO_PLAN_FOUND` stays honest; the limits belong in §6.  Open.

### AMH-C2 - untested halves

| | |
|---|---|
| **Disposition** | Answered by claims, 2026-09-15 |

`passesTheTailsOfTrainsItHasMoved`'s lock-edge and places branch, and `firstClearRoute`'s room check at a reversing intermediate, were exercised by nothing.  **Two claims, each shown failing under a mutation first:**

- `core.testReturnHomeKeepsClearOfTheTailsItLeaves.testARoadSharingMetalWithAParkedTailIsRefused` - three railways identical but for the thing each is about: two roads declared as one piece of metal refuse a move over a parked tail; the same lock with places the tail is not on allows it; the same lock with the place the tail IS on refuses again.  The order is forced by the railway - A starts on B's home, so A parks first and its tail lies over the shared metal - and the control is the same graph with no lock declared.
- `core.testHomeStaging.testATrainMustFitTheRoomWhereARouteTurnsIt` - a three-unit train is refused a turn at a square with one unit behind it; the same square not reversing admits it, which is **Adam's MT-333 ruling** (*a square it only passes is not asked about at all*); and a one-unit train is admitted the same turn, so the refusal is the room and not the flag.

**Two things the mutations measured that the reading did not predict.**  Dropping the `tailLiesOn` continue does not fail one assertion - it fails all four claims in that class, the new one's own control first, because `createEdge` locks edges that share a point: without the places refinement a tail anywhere closes every road that touches its own and ordinary railways stop being plannable.  And widening the reversing arm to every square fails `testALongerApproachIsStillTriedWhenTheShortOneHasNoRoom` as well as the new claim, which is the same pass-through strictness PRW-B2 bisected once already.

**A sibling, noted rather than fixed:** `passesTheTailsOfTrainsThatHaveNotMoved` carries the identical lock-edge loop twenty lines below.  The new claim exercises the moved-trains copy, which is the one this finding named; the standing-trains copy is reached by the older tail claims, and the two being separate spellings of one rule is the shape this project's defects keep.

### AMH-C3 - comments the code contradicts

`testReturnHomeSequencesAReversal` named a scoring mutation on a staging estimate that does not exist, and a `|| from.isTerminus()` seed clause with "its own assertion below" that has neither; `testTrainsComeHomeToTheirPlatforms` said it was expected to be red (PRW-B2 bisected it green) and that a non-reversible train needs `turningRoute=true` (removed 2026-09-04).  **Fixed:** each now says what the code does.

---

## D - looked wrong and is not

| id | what |
|---|---|
| AMH-D1 | `canEnter` exempts the mover's own point, so a route could in principle loop through its origin; a breadth-first search returns the suffix first |
| AMH-D2 | A* can rewrite a closed state's bookkeeping; never happens, because unit move cost and a change of at most one in `misplaced` make the heuristic consistent |
| AMH-D3 | Entry i never meets entry i-1's lock edges: `unlockPath` precedes `activeLocomotives.remove` inside one monitor |
| AMH-D4 | The reversal split into two paths leaves the train on `move.getEnd()` when the next leg's start check runs |
| AMH-D5 | `blockedSensors(state)` ignored `state` on purpose, and that is still true of every sensor no train accounts for.  Round 2's B2 fix gives it one use: a sensor held by a tail whose train the plan has moved |

---

## What the passes missed

- **A ruling recalled is not a ruling written.**  Adam remembered the berth rule being in behaviour.md; it lived in a code comment as a question "raised with Adam rather than decided here", and the planner never learned it.
