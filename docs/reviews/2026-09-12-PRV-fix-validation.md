# Validation of the fix round of 2026-09-12

**Status:** open

**Prefix:** PRV

**Validated:** the six fixes recorded in `docs/reviews/2026-09-12-PFX-fix-round.md`, as they stand in
the uncommitted working tree on `autonomy-diagram-r0` on the evening of 2026-09-12, against the
findings in `docs/reviews/2026-09-12-PRW-three-day-review.md`. This is not a re-review of the branch:
it is an attack on the six fixes and on the claims that were written to hold them.

**Read-only, and nothing was run.** No build, no test, no `java`, no `ant`. Nothing under
`cs2_sample_layout/` was written; `config/autonomy/configuration-Main.json` was read for figures and
nothing else. Every number below comes from reading that file or from the source.

## Method

For each fix: the diff, then the whole method in its file, then every caller of anything whose
signature or semantics moved, then the machinery underneath it - `Point.setLocomotive`,
`Layout.unlockPath`, `lockPath`'s `reserve`, `Edge.isOccupied`, `AutonomyBuilder.nodesFor` and the
edge emission, `StationIndex.facingsAt`, `Point.heldBackBy` and both of its `Occupancy` variants,
`HomeStaging.firstClearRoute`/`canRest`/`plannedOccupancy`.

Then each new claim was mutated on paper: the fix was broken in the smallest way that preserves its
shape, and the claim set was read to see which claim goes red. Four mutations survive every claim in
the round, and they are `PRV-C1` to `PRV-C4`.

**Figures measured from `cs2_sample_layout/config/autonomy/configuration-Main.json`** (71 authored
points): **4** squares carry `canReverse`, **18** carry `mustReverse`, **none** carries both. So
`mayTurnTiles()` - `reversibleTiles()` minus `mandatoryTurnTiles()` - has exactly **four** members on
Adam's railway: `1 - Main:20,13`, `1 - Main:21,6`, `1 - Main:22,6`, `2 - Bottom:13,14`. **All twelve
`parking` berths are `mustReverse`**, not may-turn. That number bounds the blast radius of two of the
six fixes and is used repeatedly below.

---

## A - wrong behaviour on the layout

None. No fix in this round was found to produce wrong behaviour on the railway, and none was found to
be worse than the defect it replaced.

---

## B - incorrect results in specific configurations

| id | status | where |
|---|---|---|
| PRV-B1 | fixed | `AutonomySession.walkTo` (`src/org/traincontrol/automationui/AutonomySession.java:5721`) |
| PRV-B2 | fixed | `Layout.standOnTheCopyItDidNotTurnOn` (`src/org/traincontrol/automation/Layout.java:3272`), called from `7333` |

### PRV-B1 - the paste walk still transits a terminus, so "nearest" can be a copy no train could be driven to

**Where.** `src/org/traincontrol/automationui/AutonomySession.java`, the breadth-first loop at
5721-5739 and the nearest-copy scan at 5741-5752.

**What the fix fixed, and what it did not.** The rewrite is right about the two things it names: the
walk now runs to the end so `getNeighbors`' shuffle cannot reach the answer, and the tie between a
plain copy and its turning twin is broken by rule. Neither of those touches which copy is *nearest*.

The walk expands **every** outgoing edge of every Point it reaches, including the outgoing edges of a
turning copy - which lead back the way the train came, because that is what a turning copy is for. So
a copy of the target reached by "drive to X, turn round at X, come back" is measured at its hop count
as though it were a journey, and `nearest` can be that copy.

**Why that is not a journey.** `Layout.isPathClear:2335` refuses it in as many words - *"Terminus
stations may only be at the end of a path"* - `e.getStart().isTerminus() && !e.getStart().equals(
path.get(0).getStart())`. `AutonomyBuilder:994` emits a may-turn square's turning copy as
`terminus: true` wherever the square is a station (`stops ? "terminus" : "reversing"`), so the copies
this walk drives through are exactly the ones the railway will not let a path pass through. The walk
and the path finder disagree about what a train can do.

**The consequence is the defect the fix exists to end.** The two copies reached through such a
transit face the *opposite* way from the ones reached without it, because a reversal is what stands
between them. So a paste can still record a flipped heading - now deterministically rather than half
the time, which is harder to notice and impossible to blame on a shuffle. `facingAfterAPaste` then
keeps that heading because the landing holds it, and `captureFromLayout` writes it into the setup.

**Reachable by** any target whose shortest walk from the train passes through a station's turning
copy. Not measured on Adam's railway - that needs a run, which is outside this pass - and it is
narrowed by there being only four may-turn squares plus 18 compulsory ones whose copies all carry the
flag. **One measurement settles it:** for each named square, compare the nearest copy found by the
current walk with the nearest found by a walk that does not expand a `isTerminus()` Point. If they
never differ, this retires to D.

**Remedy shape.** Record a terminus copy's distance and do not enqueue it - one line in the loop -
which makes the walk enforce the rule `isPathClear` already enforces, rather than a second opinion
about it. Whether a *reversing* copy should also be blocked from transit is a question for Adam:
`isPathClear` permits it (that is how a train backs into a terminus), but a paste asking "where would
it end up if it drove there" arguably should not model a reversal either.

### PRV-B2 - the re-stand drops the run's remaining reservations before the path is unlocked

**Where.** `src/org/traincontrol/automation/Layout.java:3270-3272`, reached from the arrival at 7333,
which runs **before** `unlockPath` at 7343.

**What happens.** `sibling.setLocomotive(loc)` is the shared setter, and its first statement is
`this.layout.clearLocomotiveExcept(l, this)` (`Point.java:512` -> `Layout.java:8650`), which takes
this locomotive off **every other Point in the layout**. At the moment it runs, the path is still
locked: `lockPath` reserved every point of the route with `e.getEnd().reserve(loc)` and nothing gives
those back until `unlockPath`. So the sweep collapses the run's reservations a few statements early.

**This is a hazard the file writes down twice, in the two places that avoided it.** At
`Layout.java:3403-3409`: *"RESERVED, not placed... `setLocomotive` would sweep the train off the point
it was just reserved on the moment the next point is reserved, collapsing the reservation to the
destination and freeing every junction."* And at `Layout.java:7525-7527`, on why `clearBlockExcept`
lives in `moveLocomotive` and not in the setter: *"a sweep on the shared setter is what collapsed
reservations and stranded trains the last time this rule was widened."* The new call is a sweep on the
shared setter, inside a locked run.

**What actually follows, traced rather than assumed.** Less than the warning implies, which is why
this is B and not A:

- `unlockPath` still releases correctly. Its atomic branch clears points that are already null and
  calls `e.setUnoccupied()` regardless. Its non-atomic branch tests
  `loc.equals(e.getEnd().getCurrentLocomotive()) || null == e.getEnd().getCurrentLocomotive()`
  (3756) - the second disjunct now carries what the first used to, and the outcome is identical.
- Edge occupancy is untouched by the sweep, so `Edge.isOccupied` still answers true for every edge of
  the path while the window is open.
- Signal state ends where it would have ended: `refreshOneSignal` asks "is anything this signal
  protects claimed" over every Point, so it is order-independent.

**What is left is a real window.** Between 7333 and 7343 a junction the train's tail has not yet
cleared is reserved by nobody, while the edges that name it are still occupied. A second train's
`isPathClear` running in that window can be cleared onto that junction by an edge that is not one of
ours, because the block check at `Edge.isOccupied` reads `end.getBlockLocomotive()` and there is now
nothing there. It is microseconds wide and closes when `unlockPath` releases the edges anyway, but it
is a collision-class window opened by a fix, in a method whose own comments forbid it.

**Remedy, and it is free.** Move the `standOnTheCopyItDidNotTurnOn` call to **after** `unlockPath`,
inside the `synchronized (this.activeLocomotives)` block and before the callbacks at 7350: by then
there are no reservations to sweep, `unlockPath` leaves the destination's occupant alone (3837 and
3746 both skip the last end), and the repaint sees the train on the corrected copy. And drop
`arrived.setLocomotive(null)` at 3270 - it is redundant, because `clearLocomotiveExcept` clears
`arrived` anyway, and removing it also closes the two-statement window in which the train stands on no
Point at all and `captureFromLayout` would find it nowhere.

---

## C - narrow, cosmetic, or a claim that cannot fail

| id | status | where |
|---|---|---|
| PRV-C1 | answered | `core/testAPasteDoesNotTurnTheTrainRound` - "prefer the plain copy" is unpinned |
| PRV-C2 | fixed | the same class - the no-path arm's "keep the heading" branch is never entered |
| PRV-C3 | fixed | `core/testTheArrivalHonoursTheAnswer` - the tail carry is unpinned |
| PRV-C4 | fixed | the same class - "chosen by the approach" is unpinned |
| PRV-C5 | fixed | `Layout.java:3276` - a hard-coded English operator log line |
| PRV-C6 | fixed | `Point.java:717-721` - the javadoc fix 5 depends on is stale |
| PRV-C7 | fixed | a may-turn dead end has no plain sibling and the re-stand no-ops silently |
| PRV-C8 | fixed | `AutonomySession.facingOf(String, Layout)` / `walkTo` take the first Point holding the train |
| PRV-C9 | answered | the new test's oracle implements a different rule from the code |
| PRV-C10 | fixed | MT-371 step 4 asks Adam to produce PRW-B4's symptom |

### PRV-C1 - no claim tells "prefer the plain copy" from "first in emission order"

`walkTo`'s tie-break has two rules: among the copies at the nearest distance, prefer one that is not a
turning copy, and otherwise take the first in build order. **On this fixture the second rule alone
gives the same answer as both together**, so deleting the first is invisible.

`AutonomyBuilder.nodesFor:543-545` emits, per arrival side, the plain copy and *then* its turning
copy; `StationIndex.facingsAt:292` preserves that order ("in emission order"); and the two copies of
one arrival side are always the same distance away, which
`testTheSquareCanGetThisWrong` measures (`atTheShortest.size() == 2`). So the first copy at the
nearest distance is already the plain one, and the preference does nothing here.

**Mutation:** replace the tie-break body with `return copy.getValue();` on the first entry at
`nearest`. All six claims pass. That includes `testItFacesTheWayTheDriveLeavesIt`, whose second
assertion (`Side.E`) is the strongest oracle in the class.

The preference only bites where the nearest set spans two arrival sides and the earlier side has no
plain copy - which `nodesFor` produces for a side with no onward departure. The class needs either a
square like that (measured, and said out loud if the railway has none, as its other control claims do)
or a hand-built `Layout` fixture in the style of `testTheArrivalHonoursTheAnswer`.

### PRV-C2 - the "keep the heading" arm is the headline of the ruling and no claim enters it

`facingByPath:5639-5641` is the answer to Adam's *"as long as the direction isnt flipped"*: the
no-path arm keeps the heading the train already has where the landing can hold it. Every claim that
reaches that arm reaches it with `ABSENT` - `"no such locomotive anywhere"` - and for a train that is
not on the railway `facingOf(locomotive, running)` returns null, so `already` is null and control
falls straight through to `departable.get(0)`.

`testAPastedTrainKeepsItsDirection:660-676` reaches the same arm the same way, and only on squares
with one copy.

**Mutation:** delete lines 5639-5641 entirely. Every claim in both classes passes. The arm's
determinism is pinned (`withoutATrain` in `testNoSquareAnswersTwiceDifferently`); its *rule* is not.
What it needs is a train that IS on the railway and whose landing it cannot be driven to - the class's
own header says no such destination exists on this snapshot, which makes a hand-built fixture the
honest way to get one.

### PRV-C3 - nothing pins the tail moving with the train

`standOnTheCopyItDidNotTurnOn` reads `arrived.getArrivedFrom()` before the move and writes it onto the
sibling after (`Layout.java:3268`, `3274`), and the javadoc spends a paragraph on why. **Mutation:**
delete line 3274. `testDecliningTheTurnDoesNotLeaveItOnTheTurningCopy` passes - it asserts only
`plain.getCurrentLocomotive() == loc`.

This is not a cosmetic omission. `walkStandingTrains:6100-6130` picks its first hop by `arrivedFrom`
and `break`s when it is absent, so a lost tail means the track behind the standing train silently
stops being blocked, and the grey stops being drawn. One line - `assertEquals(plain.getArrivedFrom(),
...)` - closes it.

### PRV-C4 - nothing pins "chosen by the approach"

The javadoc's second paragraph is the strongest claim the method makes: *"Picking by 'not a turning
copy' alone would put an eastbound train on the westbound copy half the time, which is the same defect
facing the other way."* The fixture has exactly one plain copy, so **mutation:** delete the
`this.getEdge(cameFrom.getName(), sibling.getName()) == null` guard at 3264 and the claim passes.

The fixture needs a second plain copy for the other arrival side, with no edge from `MT368_ARRIVE` to
it, and the claim needs to name which one the train landed on. That is three lines in `setUpClass`.

### PRV-C5 - the round added a hard-coded English log line while declining to sweep the eight it found

`Layout.java:3276` calls `this.control.log("A train that was asked to keep its direction has been
stood on ...")`. `PFX-C1` left eight such sentences open on the reasoning that a localisation sweep
wants its own commit; that reasoning is sound and this is the ninth, added by the same round, on a
line that fires on the operator's screen every time a may-turn arrival declines the turn.
`Layout.java` carries 79 `logf` calls with bundle keys against three raw ones. A key and eight
translations, or drop it to `isDebug()`.

### PRV-C6 - `Point.heldBackBy`'s javadoc still describes the fence that was removed

`src/org/traincontrol/automation/Point.java:717-721`: *"The runtime fences it behind `isAutoRunning`
because it shapes what AUTONOMY chooses."* It does not, and has not since 2026-09-10:
`Layout.isPathClear` asks `blockingOccupantOf` unconditionally (about 2733), under a comment headed
"IN EVERY TIER, and it was fenced twice before". This matters more than an ordinary stale comment
because it is the method **fix 5 has just made the staging oracle depend on**, and because the
sentence a reader would act on says the opposite of what the code does - the same shape as PFX-A1, one
file over. `docs/reviews/README.md`: *"When the two disagree, one of them is a bug and Adam decides
which."*

### PRV-C7 - a may-turn square with no onward departure has no plain sibling, and the re-stand says nothing

`AutonomyBuilder.nodesFor:543` emits the plain copy only where `!must && (onwards || !canTurn)`. A
square marked `canReverse` at which an arriving train has nowhere to go but back has *only* a turning
copy for that side. The operator is still asked there - `mayTurnTiles()` includes it - and "keep
direction" then leaves the train standing on the turning copy with no sibling to move it to, which is
PRW-A1 unrepaired. `standOnTheCopyItDidNotTurnOn` returns having done nothing and logs nothing, so the
one case the fix cannot handle is the one case it is silent about.

**Measured: not reachable on Adam's railway today.** All 12 parking berths are `mustReverse`, and the
compulsory case is correctly a no-op (no plain copy exists, and none should). Only four squares are
may-turn. This is worth a line in the method saying so - a warning where the search finds nothing at a
square that is not compulsory - rather than a code change.

### PRV-C8 - the first Point holding the train is not necessarily where it is standing

Both `facingOf(String, Layout):5868` and `walkTo:5699` find the train by scanning `running.getPoints()`
for the first Point whose `getCurrentLocomotive()` matches, and `break`. A locked path reserves the
locomotive onto **every point of its route** (`Layout.java:3409`), so during a run that scan can stop
at a junction rather than at the platform.

Pre-existing, and the PRW-B3 change makes it slightly louder: where the old body asked
`getFacing(where)` and usually got null for such a square and continued the loop, the new body asks
`facingsAt(where).get(point.getName())`, which answers for any named copy. Only the placement doors
and `facingByPath`'s fallback reach it, and those are gestures made with the railway stopped, so it is
narrow - but the loop should prefer a Point the index knows *and* that is not merely reserved, or the
scan should be documented as assuming a stopped railway.

### PRV-C9 - the new class's oracle is a different rule from the one under test

`testAPasteDoesNotTurnTheTrainRound.standingCopyFacing()` computes "the nearest **non-turning** copy".
`walkTo` computes "the non-turning copy **among those at the nearest distance**". Those differ
whenever the nearest copy overall is a turning copy and the nearest plain one is further away; they
agree on this fixture only because both sit at nine edges. Written as it is, the claim will one day go
red for a change that is not a defect, or green for one that is. State the implemented rule, or assert
the two agree as a separate measured precondition.

### PRV-C10 - MT-371 step 4 asks Adam to produce PRW-B4's symptom

PRW-B4 (left open, reasonably) says the drawn tail and the guard's claim are offset by the standing
square's own length, and names the figures MT-371 step 4 asks Adam to set - TunnelLongPark (10,9) = 2,
10,10 = 2 - as the ones that show it. Leaving the defect open is defensible; sending him to a manual
test that will show him a train drawn spilling out of a berth the guard accepted is not. The entry
wants one sentence saying the picture is known to lie by one square's length until PRW-B4 lands.

---

## D - checked and sound

| id | status | subject |
|---|---|---|
| PRV-D1 | sound | PRW-B1, the berth rule's unmeasured-approach arm |
| PRV-D2 | sound | fix 5, the staging oracle's restored FR-001 assertion |
| PRV-D3 | sound | fix 6, the two diagnostics in the timetable capture test |
| PRV-D4 | sound | PRW-B3, `facingOf(String, Layout)` and both its callers |
| PRV-D5 | sound | PRW-A1 at a compulsory turn and at a real terminus |
| PRV-D6 | sound | the self-paste (distance 0) behaviour change |
| PRV-D7 | correct | PFX-D3's reading of PRW-B2's third leg |
| PRV-D8 | sound | `unlockPath` after the re-stand, both branches |
| PRV-D9 | defensible | leaving PRW-B2, B4, C1, C2, C3 open |

**PRV-D1 - the berth rule.** The threshold is right and it is right for a checkable reason rather
than by taste. `walkStandingTrains`'s measurement rule is `if (segment.getLength() <= 0) break;`
(`Layout.java:6159`) - per **edge**; the new arm is "no place on the approach measures more than
zero" - per **place**. Those are the same test, because `GraphReducer.placesAlong` builds the places
so their lengths sum to the edge's length (PRW-D6 verified that by construction), so all-zero places
is exactly a zero-length edge. A partly measured approach keeps the refusing-side claim, which is what
PRW-B1's own remedy shape asked for. A genuinely fouling train is admitted only where nothing has been
measured, which is where the room rule already declines to judge.

`testAnUnmeasuredApproachRefusesNothing` discriminates in **both** directions: deleting
`if (!anyMeasured) return null;` fails its first assertion, and widening it to "all measured" fails
its second. It mutates the live `Edge` and restores in a `finally`, which is the right shape.

**PRV-D2 - the staging oracle.** The restored assertion asks `Point.heldBackBy(end, loc)`, which is
*verbatim* what the runtime asks: `Layout.blockingOccupantOf:4870` is `return Point.heldBackBy(
destination, loc);` and `isPathClear` calls it with no fence. So the oracle cannot be stricter than
the railway by construction.

Nor can it be stricter than the planner. `HomeStaging.canRest:1572` asks the same rule through
`plannedOccupancy`, which consults block copies **and sensor siblings** (`sameTrackAs`), while the
oracle's `onTheLiveBlock()` consults the block only. The planner's predicate strictly contains the
oracle's, and the replay applies the plan's moves in order, so any plan the planner produced satisfies
the weaker test. It is asked at every move's destination, which is where `firstClearRoute:906` asks
it. **The oracle-stricter-than-the-railway fault cannot recur in this direction.**

The cited test genuinely does not exist: `testAStagingRunIsNotRefusedByTheOccupancyRestriction`
appears only in prose - `docs/manual-tests/tests.md`, `docs/reviews/2026-09-12-PFX-fix-round.md`,
`docs/reviews-2026-09-09/AUT-autonomy-review.md` and the new comment in `testHomeStaging` - and in no
`.java` file anywhere. PFX-A1's account is accurate.

One caveat, recorded rather than raised: the oracle asks about `move.getEnd()`, while the runtime asks
about the destination Point of the route it actually picks, and `heldBackBy` reads `getBlockedBy()`
per Point. If a restriction is ever authored onto one copy of a split square and not its siblings, the
two can differ. Nothing in the builder's emission suggests that happens today.

**PRV-D3 - the diagnostics.** The second reading cannot change what it measures. `moved` is latched
before either reading; both readings are computed only when `!moved`; and the only other assertion in
the method (`getTimetable().isEmpty()`) is reached only when `moved` is true, in which case neither
diagnostic ran. `explainDestinations` is `synchronized` and previews configuration through
`EdgeConfigurationState` rather than commanding anything, so it moves no hardware; the live-collection
prints (`getActiveLocomotives`, `getLocomotivesToRun`) can throw `ConcurrentModificationException`
while driver threads wind down, and the existing `catch (RuntimeException)` covers it. The labelling of
the two readings is the part that makes them usable, and the comment states the one thing that is
still untrustworthy after the stop (rules fenced on `isAutoRunning`).

**PRV-D4 - `facingOf`.** Both callers want the arriving train's heading and neither depends on the
square-level answer. `GraphLocAssign.commitAndRecord:225` reads it before `commitChanges` precisely to
get "where this train was pointing"; `headingOfTheArrivingTrain:486` feeds
`ArrivalSidePrompt.suggestedFor`. `AutonomyEditorPanel:4568` uses the one-argument overload, which is
unchanged and which PRW-D8 already cleared. And the setup/railway pair cannot drift under the new
answer, because the facing menu writes both - `AutonomySession:1711` and `:5962` both call
`moveOntoFacingCopy` alongside `setFacing`. The fallback order (copy, then square, then the loop's
`continue`) preserves the old behaviour wherever the index has no facing.

**PRV-D5 - the compulsory cases.** `standOnTheCopyItDidNotTurnOn` cannot fire where it must not.
Traced through the builder rather than assumed: at a `mustReverse` square `nodesFor:543` emits
`!must && ...` = false, so **no plain copy exists** and the sibling search finds nothing; at a
`canReverse` dead end the same line suppresses it for the same reason. And the branch is not reached
at all in autonomy: with `reversals == null` or `ALWAYS_REVERSE`, `turnsOnArrival` falls to
`arrived.isTerminus() || current.isReversing()`, both of which a turning copy answers true. So the
whole fix is confined to a manual send that declines the turn at one of the **four** may-turn squares.

The sibling search is also correct about which copy it picks: `AutonomyBuilder`'s edge emission pairs
each `from` node with every `to` node that `arrivesBy` the same entry side (`1044-1052`,
`Node.arrivesBy:133`), so the plain copy of the arrival side is reachable from exactly the same
predecessor copy as the turning one, and `getEdge(cameFrom, sibling)` finds it. The
`sibling.getCurrentLocomotive() != null` skip is a safety net that cannot normally fire, because
`Edge.isOccupied` reads `end.getBlockLocomotive()` and would have refused the path onto an occupied
sibling in the first place. And the ordering of the two writes is right: the tail is read before
`setLocomotive` clears it (`Point.java:534`) and written after.

**PRV-D6 - the self-paste.** The old walk never tested its own start node, so a train asked about the
square it was already on was walked round the railway and answered with whichever copy it came back
to. Distance 0 now wins, which is correct and is what `testPastingOntoItsOwnSquareDoesNotTurnIt`
pins. No caller breaks: the only consumer is `facingByPath`, whose answer goes to `facingAfterAPaste`
as `keep`, and `held.containsValue(keep)` is true by construction because the value came out of
`held`. The door then calls `moveLocomotive`, which may put the train on a different copy of the same
square - but `placementCopy:629` re-derives the copy from the recorded facing at the next build, so
the two converge rather than drift.

**PRV-D7 - PFX-D3.** The note is correct. `whyABerthCannotHoldIt`'s javadoc reads *"Pure, like
`whyTooLongForThisRoute` beside it, so the staging planner can ask the same question rather than carry
a copy of it"* - a purpose clause explaining why the method is `static`, not an assertion that a caller
exists. PRW-B2's third leg overstated it. Worth adding: the sentence is still a promise nothing keeps,
which is the PFX-A1 pattern one method over, and the substantive half of PRW-B2 is untouched by the
correction.

**PRV-D8 - `unlockPath`.** Beyond the window PRV-B2 names, the release itself is unaffected by the
re-stand. Atomic branch: `e.setUnoccupied()` runs for every edge regardless, and the point clears at
3739/3746 are idempotent against null. Non-atomic branch: 3756's second disjunct carries what the
first used to, the `clearedEdges` double-release guard is untouched, and 3832/3837 skip work that is
already done. The destination's own occupancy is never cleared by `unlockPath` (3837 stops at
`size - 1`), which is what leaves the train standing where it arrived.

**PRV-D9 - the five findings left open.** All five are defensible to leave, with two conditions:

- **PRW-B2** is genuinely a design decision and not a repair, and PFX narrowed leg 3 correctly.
  But two things belong on the entry. Leg 1 is the planner being **tighter** than the runtime, which
  its own comment forbids - *"a proof may be looser than the search it guards, never tighter"* - and
  its user-visible symptom is Return Home answering IMPOSSIBLE for a goal the railway allows, which is
  OB-207's own scenario. And PFX's narrowing of leg 3 is contingent: it holds *because* Adam's railway
  is unmeasured, so the first berth approach he measures brings the planner/runtime disagreement
  straight back. Neither changes the decision to defer; both change what a reader will conclude from
  the entry.
- **PRW-B4** - see PRV-C10. Defensible, but MT-371 needs the sentence.
- **PRW-C1** (the tile-wide `refused` flag on a double curve) - cosmetic, confined, defensible.
- **PRW-C2** (a berth refusal reaching the operator as "check log") - defensible, and now on stronger
  ground than "low": PFX-B2 took the invented refusals from 26 to 0 on Adam's railway, so the door
  that cannot say why is a door nothing currently reaches. Worth recording *that* as the reason.
- **PRW-C3** (the turned-train hop over-claiming) - both errors on the refusing side, needs a turned
  train longer than its first edge, and it is recorded so nobody reads the "same arithmetic" comment
  as exact. Defensible.

---

## What this pass did NOT cover

- **Anything run.** No test, no build, by instruction. Every mutation above was performed on paper,
  by reading the claim against the code; a mutation I judged survivable may be caught by an
  interaction I did not see, and the four in `PRV-C1` to `PRV-C4` should be confirmed by actually
  breaking the fix and running the class before they are acted on.
- **Whether the new classes are green**, whether any of them skips, and whether the two "seen red"
  claims PFX reports were red for the right reason. `test-green-is-not-no-failures` applies and
  nothing here can speak to it.
- **The figures PFX measured** - east 21 of 40, 26 refusals to 0, five runs out of five to
  `BottomMainC (eastbound, reverse)`, all 41 approaches unmeasured. Every one of those needs a run.
  What was checked instead is that the mechanism each figure describes is really in the code, and it
  is. The only figures re-derived here are the four/eighteen/twelve counts from
  `configuration-Main.json`.
- **PRW-B1's reachability claim on partly measured layouts.** The fix's behaviour there was reasoned
  from `placesAlong`'s construction rather than measured on a layout that has one - Adam has none.
- **The rest of the working tree.** Only the six fixes and the code they touch were read. The 43
  commits, `GraphReducer`, `AutonomyBuilder` beyond `nodesFor` and the edge emission, the GUI changes
  and the eight message bundles were left to PRW.
- **Concurrency beyond PRV-B2 and PRV-C8.** The arrival path was traced against `unlockPath`,
  `lockPath`, `reserve` and the signal refresh; `captureFromLayout`'s six callers were not traced
  against the two-statement window at 3270-3272, only noted.
- **`testHomeStaging`'s other claims.** Only `applyPlan` and the two predicates it now compares were
  read; whether the restored assertion is red against a plan the class currently produces is exactly
  the kind of thing only a run can say.
