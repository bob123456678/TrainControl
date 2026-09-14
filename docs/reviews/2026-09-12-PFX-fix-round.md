# The three-day fix round, 2026-09-12

**Status:** open

**Prefix:** PFX

**Reviewed:** the working tree at 2026-09-12, on top of `7f8630ba`, plus the 43 commits since
2026-09-10. **Answered:** `docs/reviews/2026-09-12-PRW-three-day-review.md` (one Fable reviewer over
the same range) and the manual-test sweep described below.

This document records what was fixed, what was left open and why, and the findings the fix round made
on its own. The validation of it is `docs/reviews/2026-09-12-PRV-fix-validation.md`.

---

## Method

Three passes, in this order, because each one found what the one before it could not.

1. **A battery** before anything: 229 classes green, three not.
2. **One reviewer** (Fable) over the three days of commits and the working tree, read-only, prefix
   `PRW`.
3. **A relevance sweep** over every manual test still waiting on Adam - 17 `needs test` and 20
   `fixed unvalidated` - asking of each whether it still describes live behaviour and whether a class
   already holds its automatable half.

Everything below was **measured** rather than reasoned about: each finding names the probe or the
failing claim that produced its numbers, and each fix was seen red first.

---

## A. High

| | | |
|---|---|---|
| **PFX-A1** | The staging oracle stopped grading plans against FR-001, citing a test that does not exist | **fixed** |

### PFX-A1 - a guard handed to a class nobody wrote

`test/core/testHomeStaging.java`, `applyPlan`.

The replay oracle asserts the invariant that makes a plan a plan: every move finds its destination free
when it runs. It also asserted FR-001 - a station held back while another square is occupied - and that
assertion was removed on 2026-09-09, with a note saying
`testAStagingRunIsNotRefusedByTheOccupancyRestriction` held it instead.

**No such method or class exists anywhere under `test/`**, and did not when the sentence was written.
OB-073's invariant - the planner offering a leg the railway then refuses, leaving the fleet
half-staged - was unguarded from that day, and the sentence is what made it invisible.

The reason it was removed also expired the day after it was written. Adam's ruling of 2026-09-09 put
the restriction in full autonomy alone; his ruling of **2026-09-10** - *"enforce the occupancy ruling
in all modes and then rely on isPathClear"* - put it back in every tier. `isPathClear` has carried
"NO FENCE ... the restriction is enforced in every tier" since, and `HomeStaging.canRest` reads
`Point.heldBackBy` against the planned state. The planner applied it, the railway applied it, and the
oracle that grades whether they agree was the only one of the three not asking.

**Fixed:** the replay asks it again, so every plan the class produces is graded rather than the two
cases named after it. **Mutation, run:** dropping the `heldBackBy` line from `HomeStaging.canRest`
fails four claims, one of them the plan OB-073 was reported against.

**Worth naming as a pattern.** This is the second javadoc this week found to be describing a guard that
is not there - `docs/reviews/README.md` already says "one status, one location" for the same reason.
A sentence that hands an invariant to a named test should be checkable; nothing checks these.
`testEveryCitationResolves` holds the count of dead FINDING ids at 62 and would not have caught this,
because a test-method name is not a finding id.

---

## B. Medium

| | | |
|---|---|---|
| **PFX-B1** | A paste's direction was decided by a deliberate shuffle | **fixed** (MT-377) |
| **PFX-B2** | The berth rule refused on the absence of a measurement | **fixed** (PRW-B1) |
| **PFX-B3** | A train that declined the turn was left on the copy that expected it | **fixed** (PRW-A1) |
| **PFX-B4** | `facingOf` answered about the square rather than the copy | **fixed** (PRW-B3) |

### PFX-B1 - the paste tossed a coin

Adam, 2026-09-12: *"When 2-8-4 is pasted, it should always face east. Does it?"*

**Measured on the frozen snapshot: east 21 times out of 40 and west the other 19**, from an unchanged
setup, in one process. Two dice, compounding:

- `facingByPath`'s walk answered with the first copy of the target it touched, and `Layout.getNeighbors`
  shuffles on purpose - *"Randomize order to allow for variation in paths"*, which is right for
  autonomy picking a journey and wrong for a question about where one square lies relative to another.
- Half of what it could touch is not an arrival at all. A may-turn square is a plain copy and a turning
  copy per arrival side, always the same distance away and facing opposite ways.
- And the no-path arm rolled a die outright, `departable.get(new Random().nextInt(...))`.

**Fixed:** the walk runs to the end and the distances decide; the nearest copy a train could be
STANDING on wins over the turning copy beside it; the no-path arm keeps the heading the train already
has where the landing can hold it, and is otherwise deterministic. Measured after: east ten times out
of ten on the live folder. `core.testAPasteDoesNotTurnTheTrainRound`, six claims, two seen red.

**The sweep that came with it.** All eleven `getNeighbors` callers were read. The shuffle decided an
answer at exactly one of them - this one. The rest are `any`/`all`/`isEmpty` tests, or autonomy's own
path picker where the variation is the point.

### PFX-B2 - a refusal invented out of an absent measurement

`Layout.whyABerthCannotHoldIt`. The claim walk spends the train's length backwards over the approach's
places and stops when it runs out. Against all zeroes it never runs out: it claimed the whole approach
and refused the berth against any road sharing any of it.

**Measured on the frozen snapshot: all 41 non-station approaches are wholly unmeasured, and 26 of them
refused a three-unit train.** Adam's railway has three measured tiles, so in practice every train with
a length was refused every parking berth, by hand and through Return Home.

**Fixed:** an approach with no measurement anywhere is not judged - the room rule's own answer at the
other end of the same question, and Adam's ruling on MT-364 that *"a stretch is only indeterminate when
ALL of it is zero"*. One measurement is still enough to judge, which the claim asserts separately.
After: 26 refusals to 0.

### PFX-B3 - the second half of honouring the answer

`Layout`, the arrival in `executePathInternal`. A path offered to a may-turn square routinely ends on
the TURNING copy - measured on the frozen snapshot, `BottomMainC (eastbound, reverse)` for both of
Adam's reversible trains, five runs out of five, because `distinctDestinations` keeps the first path
per square. A train that arrived there and declined the turn faced one way while the Point it stood on
said the other, so the next dispatch read that copy's outgoing edges and drove it off its route.

**This became reachable when MT-368 made the answer honoured.** The arrival used to turn every train at
a may-turn square whatever the operator said, and a turned train agrees with the turning copy - the copy
was right for the wrong reason. `a-fix-can-be-worse-than-the-defect`, arriving as a second half.

**Fixed:** `standOnTheCopyItDidNotTurnOn` re-stands the train on the sibling of the same block that is
not a turning copy and that the same approach reaches, carrying the tail with it. A compulsory turn has
no plain sibling, so nothing happens there - which is right. Driven through a real `executePath` in
`core.testTheArrivalHonoursTheAnswer`, red first.

### PFX-B4 - the square's answer given for the copy's

`AutonomySession.facingOf(String, Layout)` found the Point a train stands on in the running layout and
then returned `getFacing(square)` - one value per square, where a square is up to four Points facing
two ways. After a run that value is the previous occupant's, because `captureFromLayout` writes it when
the run ends and nothing does between. **Fixed:** it returns the facing of the copy the train is on,
falling back to the square's value where the index has none.

---

## C. Low

| | | |
|---|---|---|
| **PFX-C1** | Eight operator-facing log lines are hard-coded English | **fixed** (thirteen, in eight languages) |
| **PFX-C2** | The timetable capture test could not say why it failed | **fixed** (MT-374) |

### PFX-C1 - eight sentences the German operator reads in English

`grep` over `src/org/traincontrol/gui`: **90** log calls go through `logf` with a bundle key; **eight**
pass an English sentence to `log` directly - four in `LayoutEditor`, three in `TrainControlUI`, and the
setup-edit-declined message at `TrainControlUI.rebuildRunningLayoutFromSetup`, which MT-267 asks Adam
to read. The bundles carry eight languages and `testMessageBundles` guards them; these eight are outside
that guard.

**Left open deliberately.** It is eight sites in files this round already changed for other reasons, and
the protocol's "prefer the smaller fix when the larger one changes behaviour" cuts against bundling a
localisation sweep into a fix round. It wants its own commit and eight translations.

### PFX-C2 - a failure that could not be diagnosed

`core.testTimetableCaptureThroughARealRun` failed the battery again with *"no locomotive moved in 480
seconds"* and all three destinations reported as blocked. That reason is a **substitution**:
`explainDestinations` replaces the real refusal with "Blocked by a train or a route in progress"
whenever `isAutoRunning()`, deliberately, because the reason it would otherwise quote comes from a
static every driver thread writes.

**A probe outside the harness settles half of it.** Same fixture, emptied, one train at Station 1, asked
with the railway stopped: **all three destinations open**, and a dispatch inside three seconds. The
railway was not refusing.

**Fixed, as MT-374 asked:** the reason is read twice on the failing path - running and stopped, each
labelled - and the failure now reports whether the Layout being watched is still the one the model
holds, which `loadedConfiguration`'s own javadoc names as a silent killer ("a retired Layout refuses to
dispatch, silently"). The next failure will say which of the two it is.

---

## D. Not defects

| | |
|---|---|
| **PFX-D1** | The `getNeighbors` shuffle at its other ten callers |
| **PFX-D2** | MT-346's expectation against the shipped planner |
| **PFX-D3** | PRW-B2's third leg, as reported |
| **PFX-D4** | The two battery failures that were not the code |

**PFX-D1.** Ten of the eleven `getNeighbors` callers cannot be affected by the shuffle: `any`/`all`/
`isEmpty` tests, reachability walks, and autonomy's `bfs`, where the variation is deliberate and
documented. Only `walkTo` turned an order into an answer.

**PFX-D2.** MT-346 tells Adam that Return Home will not stage a train into a held-back home. Checked
against the code rather than the record: `HomeStaging.canRest` reads `Point.heldBackBy` against the
planned state, and `testAHomeHeldBackByAnOccupiedSquareIsNotStaged` and
`testTwoHomesThatHoldEachOtherBackAreADeadlock` both assert it. The entry is accurate. (What was wrong
was the ORACLE, which is PFX-A1.)

**PFX-D3.** PRW-B2 reports that `whyABerthCannotHoldIt`'s javadoc says the staging planner asks it. It
says the planner *can* ask it - "Pure, like `whyTooLongForThisRoute` beside it, so the staging planner
can ask the same question rather than carry a copy of it" - which is a statement about why the method is
static, not a claim about a caller. The substantive half of PRW-B2 stands; this leg does not.

**PFX-D4.** `regression.testEveryScenarioIsUsedAndSaysSo` failed because a scenario README did not list
this round's new class - the guard working, fixed by listing it.
`regression.testTheFunctionButtonsFollowTheConsist` failed in the battery and passes alone, five times;
its message named only the conclusion, so it now prints the consist it actually saw - decoder, address,
function counts, and which locomotive the window was drawing - and the next occurrence will say which
of three causes it is.

---

## The manual-test sweep

Every entry still waiting on Adam was checked: **17 `needs test` and 20 `fixed unvalidated`.** All 37
still describe live behaviour; none names a control that has been removed.

Four had no automated coverage of any kind. Of those:

- **MT-372** is covered and was not marked so - `regression.testTheDestinationDoorsAgree` and the
  site-count claim on the "..." hold steps 1 to 5. Recorded on the entry.
- **MT-376**'s shading rule is covered by `testACompulsoryTurnIsChosenLikeAnyOtherStation`
  (`stationsAutonomyWillNotChoose`). Its SORT is **deliberately not automated**: the only cheap guard
  is a source-shape assertion, which is the shape of `testTheRunAsksThatRule` - a guard that asserted a
  buggy expression verbatim and therefore argued for the defect. A real guard wants the choice-list
  construction lifted out of `promptBlockingPoints`, and that method deleted stored restrictions once
  (FBR-A2), so it is not a refactor for the middle of a review round.
- **MT-267** and **MT-286** are a race and a window-focus question. Neither is automatable at a cost
  proportionate to what it protects.

---

## The second pass: what the validator changed

`docs/reviews/2026-09-12-PRV-fix-validation.md` attacked the fixes above and found two that were
wrong, four claims that could not fail, and three stale sentences. All of that is answered here; the
findings are catalogued under `PRV-`.

**Two fixes were wrong, and one of them was mine at its most dangerous.**

- **PRV-B2**, in PFX-B3's own repair: the re-stand ran BEFORE `unlockPath`, and `Point.setLocomotive`
  sweeps a train off every other Point - so it gave up the run's remaining reservations early, which
  is a hazard this very method writes down twice elsewhere. Moved after the unlock, inside the same
  monitor as everything else that rearranges the railway at the end of a path, and the redundant
  explicit clear dropped.
- **PRV-B1**, in PFX-B1's: the walk drove THROUGH turning copies, so "the nearest copy" could be one
  reachable only by reversing part way - a route `isPathClear` refuses mid-path. A turning copy may
  now be reached and not driven through.

**Four claims could not fail**, which is the calibration worth keeping: deleting the prefer-the-plain
tie-break (PRV-C1), the keep-the-heading arm (PRV-C2), `setArrivedFrom` on the re-stand (PRV-C3) and
the same-approach filter (PRV-C4) all left every claim green. C2, C3 and C4 now have claims that
discriminate - a placed train that cannot be driven to its target, the tail, and a second plain copy
on the other arrival side. **C1 does not, and cannot on this railway**: the builder emits a plain copy
before its turning twin, so the tie-break never decides anything here. That is asserted rather than
assumed, so a change in emission order fails a claim instead of quietly removing the coverage.

## PRW-B2, B4, C1 and C2, worked

None of these needed a ruling. They were mis-scoped in the first pass as decisions; they are
determinable from the code, and this is what each turned out to be.

- **PRW-B4 - REVERTED, and it wants Adam's ruling after all.** I made the picture spend the standing
  square so that it and the guard would claim the same squares, and the battery answered: **nothing
  was shaded at all**. `regression.testTheWashIsNoLongerThanTheTrain.testATrainOfLengthOneCoversOneSquare`
  holds that *"the whole of the edge it arrived along is washed however short the train is"*, against
  Adam's *"all of bottommaina stayed shaded ... as I set the length of EN57-203 to 1"*, and
  `core.testTheShadingFollowsTheTrain` shaded nothing with every tile measured at 10.

  The two walks describe different sets **by construction**: the guard claims the square the train is
  on and then the track behind it, and the picture never draws that square at all - "a train standing
  there would be shown standing there". So charging its length leaves a train shorter than its own
  square with nothing behind it to draw. **The open question is whether such a train should shade any
  track behind it**: the guard says no, the picture says yes, and Adam's own report is why the picture
  says yes. Left as it was, with the question written at the walk, and MT-371 carries a correction
  because the note I put on it yesterday said this was fixed.
- **PRW-C2 - fixed.** Both hand-driven doors now pre-check `whyABerthCannotHoldIt` as well as the
  length rule, so a berth refusal reaches the operator as its own sentence instead of "check the log".
  The menu door's comment already set the test this passes: the standing refusals belong here, the
  ones that clear themselves do not, and a berth that cannot hold this train cannot hold it in a
  minute either.
- **PRW-C1 - fixed.** The "already faint" flag was per TILE while the fade is per ROAD, so on a double
  curve the uncovered road lost its dim and its lock wash. Both paint methods take the SHAPE already
  faded now: the whole tile on an ordinary square, which keeps MT-375's fix exactly, and one road's
  band on a double curve, where the rest of the tile is washed as it always was.
- **PRW-B2 legs 2 and 3 - fixed.** At the destination the planner asked `measuredRoomAtTheEndOf`,
  which is the arithmetic under the length rule rather than the rule, so it missed the platform
  relaxation and refused platforms the railway accepts; and nothing asked the berth rule at all, so a
  plan could send a long train into a berth the runtime refuses at the first move. Both now ask the
  runtime's own static predicates - the pattern `Point.heldBackBy` sets for FR-001.
- **PRW-B2 leg 1 - fixed, and smaller than it looked.** `isPathClear` has two halves and only one was
  wrong in the planner. The DIRECT test - is a tail on the edge being entered - is whole-edge on
  purpose ("a path uses all of its own edges, so a tail anywhere on one of them is in the way") and the
  planner already matched it. The SHARED-METAL test is narrowed by `tailLiesOn`, and the planner had
  the symmetry test without the narrowing. So: `Layout.tailLiesOn` drops `private` (HomeStaging is in
  the same package), the planner takes `placesCoveredByStandingTrains()` at construction beside its
  coarser twin, and its shared loop asks that method with the same both-sides-have-places fallback.

  **It changes nothing on Adam's railway yet**, and that is worth knowing rather than discovering:
  `walkStandingTrains` claims a place before spending its length, so with every span zero it claims the
  whole segment and the narrowing cannot bite. The payoff arrives when tiles are measured - which is
  what MT-371 step 4 asks for.

  With this, the planner and the runtime ask the same question at all four places they diverged:
  FR-001, the length rule with its relaxation, the berth rule, and shared metal.
- **PRW-C3 - open, deliberately.** The turned-train hop over-claims by the standing square's length
  and double-charges a junction on a second hop. **Both errors are on the refusing side**, they need a
  turned train longer than its first edge, and the repair is arithmetic inside the rule OB-207 has
  just settled and Adam has not yet validated on the railway. Correcting it blind, with the battery
  mid-run, is how `a-fix-can-be-worse-than-the-defect` gets written again. It should follow his
  validation of OB-207, not precede it.

## PFX-C1, done

Thirteen sentences rather than the eight first reported - the original grep pattern missed five, and
one of the thirteen was added by this round's own PRW-A1 fix (PRV-C5). They are in the bundles now, in
all eight languages, with the two that carry values (`captionsMigrated`, and the re-stand's own line)
going through `logf` with parameters.

The debug-fenced route traces are deliberately left alone: they are behind `isDebug()` and are
diagnostics rather than operator prose.

**The seven translations want Adam's eye**, which is what MT-291 already says about the last batch of
agent-written ones. Nothing about them is guessed at - each is a plain rendering of the English - but
idiom is not something this pass can check.

## What this round did not cover

- **PRW-B2, PRW-B4, PRW-C1, PRW-C2, PRW-C3 are open**, with the reviewer's reasoning as filed. B2 is a
  three-way reconciliation between the planner, the runtime and the room rule and is a design decision
  rather than a repair; PFX-B2 narrowed its reachable surface, because the runtime now refuses nothing
  on an unmeasured approach and Adam's railway is entirely unmeasured.
- **The 43 commits' bodies** beyond what the working tree reaches.
- **The room-rule arithmetic itself** - both passes read the rules, neither recomputed the figures.
- **Concurrency**, beyond the one question PRW asked about the event thread.
