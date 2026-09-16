# The Return Home planner and its execution, reviewed whole

**Status:** open 2026-09-15 - round 1 fixed B1 to Adam's ruling and C3 (claims `8818d8cd`, fix `64169b0b`); round 2 fixed B2 to his answer on the hardware (claims and fix `c02f7000`); C1 seen happening and open; C2 open

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
| AMH-C1 | Open, and seen | one route per (train, square), chosen by a shuffled search - neither limit is written down; `core.testTrainsComeHomeToTheirPlatforms` answered NO_PLAN_FOUND once and READY once on the same code during round 2 |
| AMH-C2 | Open | the shared-metal half of the tail rule, and the reversing-intermediate room check, have no test |
| AMH-C3 | Fixed | test comments that the code contradicts |

### AMH-C1 - completeness and determinism

`firstClearRoute` returns the first route its breadth-first search finds over `Layout.getNeighbors`, which shuffles, and A* generates one successor per (train, station).  Since OB-228 the route decides the tail and the tail decides what is possible later, so a plan that needs a longer route is never generated, and among equal routes the same railway can answer READY on one press and NO_PLAN_FOUND on the next.  `NO_PLAN_FOUND` stays honest; the limits belong in §6.  Open.

### AMH-C2 - untested halves

`passesTheTailsOfTrainsItHasMoved`'s lock-edge and places branch, and `firstClearRoute`'s room check at a reversing intermediate, are exercised by nothing; the OB-228 oracle grades direct coverage only, where `Layout.isPathClear` on the replayed layout could grade every rule at once.  Open.

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
