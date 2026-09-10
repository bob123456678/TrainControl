# The eight-day review, weighted on the last three

**Prefix for citing these findings elsewhere:** `E8`

**Status:** closed

**Worked 2026-09-10.** Every finding above is fixed; the eight D entries were never defects. The
dispositions are in the tables at the head of each section, and the prose under each finding is left as
it was written - what was believed at the time, which is the record this folder keeps. Two of them
changed code the reviewer did not have to hand: `E8-B1` added the `isActive` clause to
`AutonomySession.stationsAutonomyWillNotChoose` and to the guard's paraphrase of the runtime, and
`E8-B4` gave the two-segment test a question only the room rule can answer - verified by re-running the
reviewer's own mutation, which now reddens five of that class's eight tests rather than four.

Reviewed 2026-09-10, on branch `autonomy-diagram-r0` at `8ae3a304` ("MT-278: the blocked square is
faded rather than washed over"). Scope: the 320 commits of `git log --since="8 days ago"`, with the
139 of the last three days read closely and the rest sampled around what they touch.

Confirmed absent from `docs/reviews/`, `docs/reviews-2026-09-09/` and `docs/manual-tests/findings.tsv`
before it was chosen: `E8` collides with nothing. Prefixes already live are `D2`, `D3`, `IND9X`,
`REV9`, `W7`, `W7B`.

---

## Method

**What was executed.** Twenty-three test classes were run against the working tree with
`docs/tools/one.sh`, in five batches. All were green at `8ae3a304`
(`Failures: 0`, `Skips: 0`): `core.testATrainMustFitEverySquareOnItsRoute`,
`core.testTheRoomRuleCensusOnTheRealLayout` (three separate JVMs),
`core.testWhichSquaresTheRoomRuleClosesOff`, `core.testTheLengthGuardsOnTheRealLayout`,
`core.testHomeStaging`, `core.testTheAutoTierScopeMatchesTheRuntime`,
`core.testACompulsoryTurnIsChosenLikeAnyOtherStation`,
`regression.testWhatReachesTheTracksAtEachKindOfArrival`,
`regression.testStationBlockedByAnotherPoint`, `regression.testControlEAsksTheMenusQuestion`,
`regression.testNoTwoShortcutsShareAKey`, `regression.testTheHoveredSquareIsForgotten`,
`regression.testClearAllTrackLengths`, `regression.testEditorSurfaceRules`,
`regression.testNothingOnTheEventThreadTakesTheRailwaysMonitor`,
`ui.testBlockedTrackIsGreyWhileAutonomyRuns`, `ui.testTheGreyAppearsAtIdleToo`.

**One mutation was performed on the shared tree and restored.**
`Layout.java:7584` — `if (room == null || loc.getTrainLength() <= room) continue;` was replaced by
`if (room == null || true) continue;`, which switches the whole track-room rule off while leaving the
station-capacity rule alone. `core.testTheLengthGuardsOnTheRealLayout` and
`core.testATrainMustFitEverySquareOnItsRoute` were run under it. The line was put back immediately and
`git status --porcelain` was checked: `src/` is clean, and the only modified files are the three under
`cs2_sample_layout/` that Adam's running TrainControl rewrites, exactly as they were before this
review started. `cs2_sample_layout/` was never read from or written to by anything here.

**One temporary probe class was written, run and deleted.** `test/core/E8Probe.java` — a printing
class, no assertions — was used to answer two questions execution can answer and reading cannot
(E8-B1 and E8-C3 below). It was removed after the run rather than left, because leaving a test class
in `test/` without a `build.xml` entry reddens `regression.testEveryTestIsInTheBattery`, and this
review does not change the tree. Its exact output is quoted under the findings it produced.

**The battery was not run.** It was green at this commit (205 classes, 0 failures, 0 that tested
nothing) and nothing here changed the code, so a second run would have measured the same thing.

**What was only read.** `docs/reference/behaviour.md` in full, against the code for every area it
touches that moved this week. The diffs of `Layout`, `HomeStaging`, `AutonomySession`,
`AutonomyCompanionStore`, `AutonomyChecks`, `AutonomyEditorPanel`, `LayoutEditor`, `LayoutLabel`,
`TrainControlUI` and `LayoutRightclickAutonomyMenu` over `7f6356fc..8ae3a304`, plus the surrounding
methods. Every finding below says which of the two it rests on.

**A note on the scratch directory.** `$TC_SCRATCH/oneout/junitreports/` accumulates across runs and is
not cleared. It contains XML for `core.testNoTrainIsStrandedByTheRoomRule` and
`core.testTheOnTheWayRuleIsBoundedByASwitch`, both showing failures. Neither class exists in the tree
and nothing cites either name; they are leftovers from an earlier session. Recorded here because they
look like evidence and are not — see E8-D7.

---

## A — high

Wrong behaviour on the layout, or data silently lost. Both entries here are places where
`behaviour.md` states the opposite of the code in an area that changed within the last two days, and
where a maintainer following the document would re-create a defect that has already been fixed. They
are graded A on the brief's own rule that a code/document disagreement carries the severity of the
defect it describes.

| id | what | disposition |
|---|---|---|
| E8-A1 | `behaviour.md` §6 says Return Home does not read the occupancy restrictions, in either half | fixed |
| E8-A2 | `behaviour.md` §3 still files OB-195 as an open question and describes the surface as un-narrowed | fixed |

### E8-A1 — §6 tells the reader Return Home ignores the occupancy restrictions, and both halves of that are now false

`docs/reference/behaviour.md:853-856`:

> - It does **not** read the occupancy restrictions of §1 — neither when planning, nor when the run
>   executes. A plan may therefore park a train at a station some other occupied square holds back,
>   and the railway carries it out.

**Planning** reads it: `HomeStaging.java:896` is `if (!canRest(loc, to, state) || state.containsKey(to)) return null;`, and that overload — `HomeStaging.java:1538-1545` — ends
`return Point.heldBackBy(at, loc, plannedOccupancy(state)) == null;`. **Execution** reads it: every
leg goes through `configureAndLockPath` → `Layout.isPathClear`, whose FR-001 block at
`Layout.java:2628-2659` has no tier test of any kind since `2f1e956f`.

The same document says so itself, 800 lines earlier — `behaviour.md:44-61`, "**Occupancy restrictions
bind every tier**… **The planner reads it again, and has to.**" — so §1 and §6 now contradict each
other on the tier that changed yesterday.

**How I know.** Read only, on both sides, and corroborated by execution:
`core.testHomeStaging.testAHomeHeldBackByAnOccupiedSquareIsNotStaged` and
`.testTwoHomesThatHoldEachOtherBackAreADeadlock` both pass at this commit (92 tests, 0 failures,
0 skips), and neither could pass if §6 were true.

**What I would change.** Replace the bullet with what §1 already says, and add the consequence §1
names: two homes that hold each other back are a deadlock and Return Home answers `NO_PLAN_FOUND`.
The reason this is graded A rather than C is the direction of the error: §6 is the section somebody
reads to decide whether a `NO_PLAN_FOUND` from Return Home is a bug, and it tells them the planner
should not be applying the rule at all. Acting on that removes `HomeStaging`'s copy, which is OB-073
— the planner offering a leg the runtime then refuses, the run retrying until it gives up, and the
fleet left half-staged.

### E8-A2 — §3 still says the compulsory-turn clause is there and the question is open

`docs/reference/behaviour.md:187-194`:

> **One surface is wider than this, on purpose or not (OB-195).**
> `AutonomySession.stationsAutonomyWillNotChoose` … counts a compulsory turn as one autonomy will
> never choose whether or not it is also parking. That is wider than `isSendableDestination` …
> whether the editor should narrow or the builder widen is filed as OB-195 and is Adam's to settle.

It was settled on 2026-09-09 and the editor narrowed. `AutonomySession.java:3597` is now one clause,
`if (store.isStation(tile) && !isAutoDestination(tile)) out.add(tile);`, and the same document records
the ruling at `behaviour.md:374-404`. So §3 says both that OB-195 is open and that it was decided.

A second sentence went with it: `behaviour.md:984` still says a magenta leg is one whose destination
is "a parking berth, **or a square that turns every train it takes**". The colour is decided at
`AutonomyEditorPanel.java:7102-7103` from the narrowed set, so a compulsory turn with **Can Be Chosen
in Full Autonomy** on is now drawn like any other station.

**How I know.** Read, then executed:
`core.testACompulsoryTurnIsChosenLikeAnyOtherStation` passes at this commit (4 tests, 0 failures),
and its `testItIsNotReportedAsOneAutonomyLeavesAlone` asserts exactly the behaviour §3:187 denies.

**What I would change.** Delete the "one surface is wider" block and fix the magenta sentence at
`:984`; §3:374-404 already carries the ruling and needs nothing added. A reader who trusts `:187`
re-adds `isMustTurnAround` to `stationsAutonomyWillNotChoose`, which is the clause Adam ruled out and
which `core.testACompulsoryTurnIsChosenLikeAnyOtherStation` exists to keep out.

---

## B — medium

| id | what | disposition |
|---|---|---|
| E8-B1 | `stationsAutonomyWillNotChoose` does not ask `isActive`, so a station switched OFF is reported as one autonomy will choose | fixed |
| E8-B2 | `behaviour.md` §1's cap bullet still says the occupancy restrictions ask `isFullAutonomyRunning` | fixed |
| E8-B3 | `behaviour.md` §5c still describes blocked track as a grey wash painted over the tile | fixed |
| E8-B4 | `testTwoOneUnitSegmentsRefuseTheFourUnitTrain` passes with the whole track-room rule switched off | fixed |
| E8-B5 | Two `HomeStaging` comments say the planner does not apply FR-001, and one argues from it | fixed |

### E8-B1 — a station the operator has switched out of service is reported to him as one autonomy will choose

`AutonomySession.java:3597` is the whole rule:

```java
if (store.isStation(tile) && !isAutoDestination(tile)) out.add(tile);
```

Its javadoc, `AutonomySession.java:3545-3551`, writes the runtime rule out in four clauses and then
says:

> **THE RUNTIME'S RULE, asked of the diagram.** The runtime is `Layout.isSendableDestination` -
> `isDestination() && isActive() && isAutoDestination() && !isReversing()` - and `isAutoDestination`
> is the same switch under a different name … **Nothing else decides it.**

`isActive()` also decides it (`Layout.java:7906-7910`), and unlike `!isReversing()` — which is about a
*copy* and genuinely cannot be asked of a square — `isActive` **is** a square-level property. The
session reads it one square at a time at `AutonomySession.java:6597` and in bulk at `:3176`
(`shutTiles`), and `AutonomyEditorPanel.java:3454` is the menu item that writes it.

**How I know.** Executed. `test/core/E8Probe.java` (written, run, deleted — see Method) opened
`test/layouts/single-switch`, marked `MainPlatform` as an auto destination, shut it with
`session.setPointProperty(berth, "active", Boolean.FALSE)` the way the menu does, and rebuilt:

```
E8 ---- before shutting ----
E8 willNotChoose contains berth = false
E8 runtime isSendableDestination = true
E8 ---- after shutting ----
E8 shutTiles contains berth = true
E8 willNotChoose contains berth = false
E8 runtime point isActive = false
E8 runtime isSendableDestination = false
```

The runtime's answer flips and the editor's does not.

**Three readers, and the compensating term does not reach them.** The obvious defence is that a shut
square is barred from the path walk anyway — `AutonomyEditorPanel` passes `session.shutTiles()` into
`GraphReducer.findPath`. It does not defend this: that method's own javadoc,
`GraphReducer.java:504-505`, says the closed set "refuses a closed square as an INTERMEDIATE and not
at either end, so it is a valid destination and never a way through", and then at `:512-516` that the
resulting overstatement is left in deliberately because "the findings may call a closed station
reachable when autonomy would never route there, which hides nothing that was not hidden before".
That was true before the Auto-tier note existed. It is not true now: the note is the thing that was
supposed to cover "a route exists and autonomy will never use it", and the one case the walk
knowingly overstates is the case the note cannot see. So

- `AutonomyEditorPanel.java:7037-7043` — the Path Type **Auto** note. A route to a shut station is
  drawn, and nothing says autonomy will never take it.
- `AutonomyEditorPanel.java:7102-7103` — the magenta leg colour. The leg is drawn as an ordinary
  autonomy route.
- `AutonomyChecks.java:451, 487` — `checkReversingGoesSomewhere`. A reversing point whose only
  reachable stations are all switched off counts as leading somewhere.

**And the guard has the same hole.** `core.testTheAutoTierScopeMatchesTheRuntime` exists to hold these
two statements of the rule together — its javadoc names `guard-and-affordance-same-question` as the
reason. Its paraphrase of the runtime, `testTheAutoTierScopeMatchesTheRuntime.java:126`, is
`boolean thisCopy = !point.isReversing() && point.isAutoDestination();` — the same two clauses, missing
the same one. So the divergence cannot be caught by the test written to catch it, which is why it
survived the narrowing.

**What I would change.** Add the clause at `AutonomySession.java:3597`
(`Boolean.FALSE.equals(getPointProperty(tile, "active"))` is already spelled at `:6597` and `:3181`),
add it to the paraphrase at `testTheAutoTierScopeMatchesTheRuntime.java:126`, and correct "Nothing else
decides it" in the javadoc — it is that sentence that made the omission invisible. If Adam would rather
the set stayed narrow, then the javadoc is the thing to change and the three readers each need their
own `shutTiles` term.

### E8-B2 — the cap bullet still contrasts itself with a fence that no longer exists

`behaviour.md:95-101`:

> **a timetable and Return Home ARE capped** — where the occupancy restrictions above, asking
> `isFullAutonomyRunning`, are not. The two fences answer the same-sounding question differently …
> **A question for him**, not a defect.

There is one fence now, not two. `Layout.isFullAutonomyRunning` (`Layout.java:1744`) has zero call
sites in `src/` or `test/` — its own javadoc at `:1729-1739` says so in capitals — so the comparison
the bullet draws, and the question it raises for Adam, are both about a state of the code that ended
on 2026-09-10. The paragraph fifty lines above it, `behaviour.md:44-54`, says "Both fences are gone."

**How I know.** Read, plus
`grep -rn "isFullAutonomyRunning" src/ test/ --include=*.java`, whose only non-comment hit is the
declaration itself.

**What I would change.** Cut the contrast and the "question for him" from the bullet; keep the first
sentence, which is still true and still worth saying (`isAutoRunning` is wider than "full autonomy",
so a timetable and Return Home are capped). This is B rather than A because acting on it changes a cap
that Adam has explicitly said needs no change, rather than restoring a defect.

### E8-B3 — §5c describes a grey wash; the mark is a fade applied to the tile

`8ae3a304` replaced the wash with transparency on Adam's own words — *"I want the shading to instead
be the same tile with more transparency"* — and touched no documentation. `behaviour.md` §5c still
says:

- `:670-671` — "Blocked track is drawn as a **grey wash over the whole square** … The wash goes
  **under** the line". There is no fill: `LayoutLabel.java:1356-1372` sets an `AlphaComposite` of
  `BLOCKED_ALPHA` (`:1510`, `0.40f`) and paints the tile through it.
- `:722` — "Neither mark is part of the tile's icon - both are painted over it." Half of this is now
  the opposite of the truth, and it is the half that matters: the fade *has* to be applied to the icon
  as it is painted, which is the entire reason it moved out of `paintCoveredMark` into
  `paintComponent`. A reader who trusts this sentence puts it back where a fill can go and gets a wash
  again.
- `:713`, `:715-719`, `:724` — "grey and not orange", "the grey is as long as the edge", "whose line
  or wash differs". The extent claims survive; the vocabulary does not.

**How I know.** Read `git show 8ae3a304 -- src/org/traincontrol/gui/LayoutLabel.java` and the current
`paintComponent`, against those lines of the document. `ui.testBlockedTrackIsGreyWhileAutonomyRuns`
and `ui.testTheGreyAppearsAtIdleToo` both pass at this commit and both now assert on *contrast*, not
on darkness — which is the code agreeing with itself and not with §5c.

**What I would change.** Rewrite the four §5c bullets around "faded to 40%" and say plainly that the
fade is applied to the icon while it is drawn and the train line is painted at full strength over it.

### E8-B4 — a test named for the length rule passes with the length rule switched off

`test/core/testTheLengthGuardsOnTheRealLayout.java:1013-1041`,
`testTwoOneUnitSegmentsRefuseTheFourUnitTrain`, ends:

```java
assertFalse(offers(train, "TunnelLongPark"),
    "with only the two one-unit segments he measured, a four-unit train is still offered"
    + " TunnelLongPark - so the guard is wrong and the stale lengths were not the cause");
```

Its own sibling forty lines earlier, `testTunnelLongParkIsRefusedForReasonsOtherThanLength` (`:698`),
asserts that the same berth refuses the same locomotive **with every tile on the railway measured at
eight units** — that is, that something other than length closes TunnelLongPark to `75 407 DB`. Given
that, no change to the room rule can redden `:1033`.

**How I know.** Mutation, run. With `Layout.java:7584` neutered to
`if (room == null || true) continue;` — the track-room rule off entirely —
`core.testTheLengthGuardsOnTheRealLayout` came back `Total tests run: 8, Failures: 4`. The four that
went red were `testExactlyFitsIsAdmittedAndOneMoreIsNot`, `testTheSameTrainIsAdmittedOnceThereIsRoom`,
`testTheOneUnitAtTwentyTwoSevenDecidesBottomMainPost` and `testWhyRampDownIsRefused`.
`testTwoOneUnitSegmentsRefuseTheFourUnitTrain` **passed**. (Control, same run:
`core.testATrainMustFitEverySquareOnItsRoute` went 3 of 6 red, so the mutation was reaching the rule.)
The line was restored and the tree verified clean.

**A second, smaller hole in the same file.** `offers()` (`:732`) returns false when
`getPossiblePaths` returns an empty list, and neither `:1033` nor `:713` asserts that
`75 407 DB` is offered *anything*. A railway that offers this train nothing at all — an unplaced
locomotive, a configuration that drops its Point — turns both green.
`core.testALongTrainIsOfferedNothingOnAOneUnitRailway` carries exactly that control and is solid
because of it.

**What I would change.** Point the test at a berth length actually closes. If the intent is to keep
Adam's own reported case on the record, then say in the assertion what it really pins — that
TunnelLongPark is refused, whatever the reason — and add the floor
(`assertFalse(offeredDestinations(train).isEmpty(), …)`) so it cannot pass on a railway that offers
nothing.

### E8-B5 — two comments in `HomeStaging` say the planner does not apply FR-001, and the second reasons from it

- `HomeStaging.java:427-430`: "since 2026-09-09 there is nothing anywhere else in this class either:
  the planner does not apply FR-001 at all … See firstClearRoute, where the state-aware canRest used to
  ask it." It asks it again, in that very method, at `:896`.
- `HomeStaging.java:513-521`, the note explaining why there is no cycle scan: "That proof rested
  entirely on this planner enforcing FR-001, and it does not any more - see firstClearRoute. The
  arrangement is an ordinary one the search stages in a move or two, and IMPOSSIBLE would be a false
  claim about a railway that works."

The **decision** is still right — `NO_PLAN_FOUND` rather than `IMPOSSIBLE`, which `behaviour.md:69-74`
records as Adam's — but every clause of the argument for it is now inverted. Two homes that hold each
other back are exactly *not* "an ordinary arrangement the search stages in a move or two": they are the
genuine deadlock `behaviour.md:69-74` names and `core.testHomeStaging.testTwoHomesThatHoldEachOtherBackAreADeadlock` pins.

**How I know.** Read, against `HomeStaging.java:896` and `:1538-1545`; and
`core.testHomeStaging` passes at this commit with both of those tests in it.

**What I would change.** Rewrite both comments to say what is true — the planner applies FR-001
against the planned occupancy, the pair really is unstageable, and the scan is still absent because the
weaker true answer is preferred to a verdict that was wrong three times. This is B rather than C
because `docs/reviews/README.md`'s own rule is that a comment must stand on its own, and a reader who
takes `:517` at face value concludes the planner has a redundant check in it.

---

## C — low

| id | what | disposition |
|---|---|---|
| E8-C1 | `LayoutLabel.BLOCKED_WASH` is dead, and its javadoc says one place draws it | fixed |
| E8-C2 | `behaviour.md` §5c's "three gestures" is now every setup edit | fixed |
| E8-C3 | The control station's log drops a repeated line, so `WireRecorder`'s exact counts can under-count | fixed |
| E8-C4 | The event-thread guard's allowlist cannot see four `synchronized (this)` members or twelve transitive reachers | fixed |
| E8-C5 | Two UI classes still call the fade a wash, in their method names and javadoc | fixed |
| E8-C6 | The "whole square, not a stroke" floor is `drawn > 0` | fixed |
| E8-C7 | `testControlEAsksTheMenusQuestion` compares an expression with itself | fixed |
| E8-C8 | `HomeStaging.plannedOccupancy` diverges from the runtime, and `canRest` says the audit proves they agree | fixed |
| E8-C9 | Two private helpers in `testTheLengthGuardsOnTheRealLayout` are unreachable, and one held a fixture invariant | fixed |
| E8-C10 | `behaviour.md` does not mention Control+E | fixed |

### E8-C1 — `BLOCKED_WASH` has no callers, and its javadoc claims exactly one

`LayoutLabel.java:1496`. `8ae3a304` moved the mark out of `paintCoveredMark` and left the colour
behind. Its javadoc, `:1490`, says "PAINTED, NOT TINTED INTO THE ICON, and there is exactly one place
that draws it" — there is none — and the paragraph goes on to warn, correctly, that "leaving one there
with no caller invites a second way of drawing the same thing". That is the state it is now in.

**How I know.** `grep -rn "BLOCKED_WASH" --include=*.java --include=*.md .` returns the declaration and
nothing else.

**What I would change.** Delete the constant and its javadoc. `BLOCKED_ALPHA` at `:1510` is the one
number now and already says why.

### E8-C2 — "the three gestures" is now every setup edit

`behaviour.md:693-707` lists three gestures that regenerate the marks with no train moving, and says
which route each takes. `8ae3a304` added an unconditional `blockedTrackChanged()` at the end of the
setup rebuild (`TrainControlUI.java:6255`), on Adam's *"Make sure the shading and orange repaints on
any autonomy or train/track length edit"* — so a station flag, an arrival side, a one-way direction and
a reversal marking all refresh them too. The bullet is not false so much as superseded: the tile-length
route it singles out as special is now the general one. Read only.

### E8-C3 — a repeated wire message is logged once, so "exactly one direction command" cannot tell one from two

`MarklinControlStation.log(String)` at `:2664-2666` is
`if (message != null && !message.equals(this.lastMessage))`. `WireRecorder` listens on that logger, so
two **identical, adjacent** wire messages reach it as one line. The absolute claims in
`regression.testWhatReachesTheTracksAtEachKindOfArrival` — one direction command at a terminus, one at
a reversing point answered yes — would therefore also pass on a rule that emitted two.

**How I know.** Executed, in the deleted probe:

```
E8 two identical lines logged, recorder heard 1
E8 three lines with no two adjacent equal, recorder heard 3
```

**What I would change.** Nothing in the code — the dedupe is right for a log a human reads. Say it in
`WireRecorder`'s javadoc, which currently explains how the recorder can see the wire and not what the
wire drops, and note that the counts are a lower bound where two identical commands could be adjacent.
The class is otherwise the strongest new test artefact of the week: the `CB_PRE_ARRIVAL` mark is what
makes the claims absolute rather than comparative, and it carries its own "the recorder heard
something" control.

### E8-C4 — the guard knows only what it lists, and what it lists is 25 of about 40 doors

`test/regression/testNothingOnTheEventThreadTakesTheRailwaysMonitor.java` builds its door list from
`synchronized` **method declarations** in `Layout.java` (24 of them) plus one hand-maintained name,
`REACHES_THE_MONITOR = { "triageReturnToHome" }` (`:96`). It is green and its allowlist has no stale
rows. What it cannot see:

- Four members that take the same monitor with a `synchronized (this)` **block** rather than a
  modifier: `configureAndLockPath` (`Layout.java:3180`), `handleMisconfiguredPath` (`:3442`),
  `getPathValidationFailureCount` (`:3538`) and `hasShownPathValidationAlert` (`:3550`). No UI call
  site today, but the last two are a public `int` and a public `boolean` — exactly the shape a status
  panel or tooltip would call from `actionPerformed`.
- Twelve `Layout` members that reach the monitor without being `synchronized` — `executePath`,
  `executePathInternal`, `executeTimetable`, `executeTimetableInternal`, `runLocomotive`,
  `runLocomotives`, `isPathClear`, `pickPath`, `firstClearOrWhyNot`, `debugPath`,
  `hasAutonomousDestination`, `checkForSlowerLoc` — of which five are already called from the UI, all
  five correctly on a worker: `AutoLocomotiveStatus.java:1085`,
  `LayoutRightclickAutonomyMenu.java:1209`, `TrainControlUI.java:21850`, `:23283` and `:23992`.
  `executePath` on the event thread would pass the guard in silence, and it holds the monitor for a
  whole dispatch.

**How I know.** Read: the guard's scanner was re-implemented and run against `HEAD`, reproducing its
24 keys exactly; the call sites were traced by hand. And executed: the guard itself is green
(5 tests, 0 failures). **No live defect was found** — see E8-D8.

**What I would change.** Put `executePath`, `executeTimetable`, `runLocomotives`,
`configureAndLockPath`, `getPathValidationFailureCount` and `hasShownPathValidationAlert` into
`REACHES_THE_MONITOR`, and write the five existing `new Thread` sites into the allowlist as
`OFF THE EVENT THREAD`. That turns five silent-but-correct calls into five sentences and makes the
sixth one fail.

### E8-C5 — the two pixel classes still call it a wash

`test/ui/testBlockedTrackIsGreyWhileAutonomyRuns.java:252-255` and
`test/ui/testTheGreyAppearsAtIdleToo.java:320-323`: the method is
`testTheBlockedMarkIsAWashOverTheWholeSquare` / `testTheIdleMarkIsAWashOverTheWholeSquare` and the
javadoc above it says "the whole square is darkened, not a stroke across it". `8ae3a304` moved both
assertions to contrast precisely because a fade over a light panel is *brighter*, and left the names
and the sentence. Read only.

### E8-C6 — the floor under "the tile itself, not a stroke across it" is one pixel

Same two methods, `:261-266` and `:329-334`. The claim is
`changed >= drawn * 9 / 10` where `drawn = drawnPixels(bareBlocked)`, guarded only by `drawn > 0`. It
is a real claim on this fixture — a stroke changes its own pixels and a fade changes all of them — but
the guard admits a tile with eight drawn pixels, where "seven changed" is satisfied by any mark at
all. A fraction of the tile (`drawn > TILE * TILE / 10`) would say what the assertion means. Read only.

### E8-C7 — the Control+E test compares one expression with itself

`test/regression/testControlEAsksTheMenusQuestion.java:285` gathers what the menu offers from
`buildTileMenu` and what the key does from `panel.offersALength(tile)`, and asserts they agree. The
menu adds its item inside `if (offersALength(tile))` (`AutonomyEditorPanel.java:1766`) — as that
line's own comment says, "Structurally the condition is already true at this point, so the call adds
no behaviour". So a wrong predicate cannot redden this; only re-duplicating the guard can. That is
worth having and is exactly what the test says it is for, but the floors at `:292`/`:296` come from the
same predicate and move with it, so nothing in the class pins the *content* of `offersALength` against
`buildTileMenu`'s three early returns. Read, and executed (3 tests, 0 failures).

**What I would change.** Add one case that fixes the content: assert `offersALength` is false on a
text square and on a square of an excluded page, read off the fixture rather than off the predicate.

### E8-C8 — the planner's occupancy is deliberately wider than the runtime's, and the method above it says the audit proves they agree

`HomeStaging.plannedOccupancy` (`:1568-1583`) consults the square, its block copies, **and the other
points reporting the same sensor** — and its own javadoc says the third is "a deliberate divergence
from the runtime … On such a layout this refuses arrivals the runtime would allow." Twenty lines
earlier, `canRest(loc, at, state)`'s javadoc leans on `auditAgainstRuntime` as what "proves the two
agree". Both cannot be unconditionally true. The divergence is declared, fails safe, and is covered by
`testHomeStaging.testTwoActivePointsSharingASensorAreNeverBothOccupied` and
`.testASharedSensorDoesNotMakeAnOrdinaryLayoutImpossible`; what is missing is one sentence in
`canRest` saying which way the audit's guarantee runs. Read only.

### E8-C9 — two private helpers are unreachable, and one carried the only assertion of a fixture invariant

`test/core/testTheLengthGuardsOnTheRealLayout.java:940` (`roomOf`) and `:1279` (`berthsOfferedTo`) are
declared and never called. `roomOf` holds `assertTrue(runIn.size() >= 2, …)`, the only check that the
run into TunnelLongPark is long enough for a two-number measurement; with the method dead, that
invariant is asserted nowhere. Read only (`grep -n` for each name in the file returns the declaration
alone).

### E8-C10 — Control+E is not in `behaviour.md`

The document lists the diagram's keyboard doors at `behaviour.md:700` (Control+X / Control+V /
Delete) and stops. Nothing in it contradicts FR-066; the feature simply is not written down, on a
release whose bar is that the behaviour is documented. Read only.

---

## D — not defects

| id | what | disposition |
|---|---|---|
| E8-D1 | Control+E's guard and its affordance are one predicate | clean |
| E8-D2 | The manual route menu moved with the occupancy fence | clean |
| E8-D3 | Control+E cannot fire on a stale square or in track-editor mode | clean |
| E8-D4 | `NEWLY_REFUSED = 880` is stable across JVMs | clean |
| E8-D5 | The 40% fade cannot leave stale pixels behind | clean |
| E8-D6 | The planner's prefix rule is the stricter half at the berth | clean |
| E8-D7 | Two failing classes in the scratch JUnit output are leftovers | clean |
| E8-D8 | Nothing on the event thread takes the railway's monitor | clean |

### E8-D1 — Control+E asks the menu's own question, in one place

The obvious shape here was the one this codebase keeps producing: a key that acts where the menu
offers nothing. It was that, twice, and both were fixed in `4565be9b`. As it stands
`AutonomyEditorPanel.offersALength` (`:4896-4906`) is the single predicate, `buildTileMenu` adds its
item inside it (`:1766`), `promptLengthFor` refuses through it (`:4832`), and both act on
`squareTheLengthWouldGoOn` (`:4868`), the run leader. I traced `offersALength` against
`buildTileMenu`'s three early returns (`:1059`, `:1075`, and the ignored-square branch) and they are
the same three. **Ran** `regression.testControlEAsksTheMenusQuestion` — 3 tests, 0 failures, 0 skips.
The test is weaker than it reads (E8-C7); the code is right.

### E8-D2 — taking the fence off `isPathClear` did not leave the manual menu offering what execution refuses

This was raised as the likeliest consequence of `2f1e956f`: the occupancy restriction now refuses a
hand-driven send, and `LayoutRightclickAutonomyMenu.destinationItem` (`:1176-1190`) deliberately checks
only the length rule before dispatching, with a comment saying so. Withdrawn on reading the layer that
builds the menu: `Layout.getPossiblePaths` (`:4840`) calls `isPathClear(path, loc, false)` on every
candidate at `:4871`, so a destination held back by an occupied square is not offered at all. The
guard and the affordance moved together. `regression.testStationBlockedByAnotherPoint` — 16 tests, 0
failures — includes `testAHandDrivenRouteIsRefusedThere`.

### E8-D3 — the hovered square cannot go stale, and Control+E cannot fire in the track editor

Two shapes checked. `LayoutEditor.hoveredSquare()` (`:975-995`) is the one reader for Control+H,
Control+S and Control+E; it refuses a label that is not on the current grid and clears the field while
it is there, and `leaveFor` (`:5914`) clears it as well. Control+E sits **above** the
`if (isAutonomyMode()) return;` guard at `:7109` and tests only `autonomyPanel != null`, not
`isVisible()` — but `autonomyHover` is assigned only inside `if (isAutonomyMode())`
(`LayoutEditor.java:1014-1024`), and both a page change and a mode change go through `leaveFor`, so
there is no path by which the key can act in the track editor. **Ran**
`regression.testTheHoveredSquareIsForgotten` (3 tests) and
`regression.testTheAutonomyEditorKnowsWhichSquare` (3 tests) — 0 failures.

### E8-D4 — the exact census pin is not a flake, on three JVMs

`testTheRoomRuleCensusOnTheRealLayout.java:294` pins `NEWLY_REFUSED = 880` off a walk that takes one
`bfs` route per pair (`:745`), while the same file argues at `:327-333` that one-route-per-pair
measurements move by about two per cent between JVMs and uses a band for the other census. Checked by
execution: the class was run in three separate JVMs and came back
`Total tests run: 3, Failures: 0, Skips: 0` each time, with `journeys newly refused : 880`. Left as a
D rather than a C on the README's rule about "could happen" versus "does happen" — but the asymmetry
between the pinned number and the banded one is worth knowing about if it ever does move.

### E8-D5 — painting the tile at 0.4 alpha cannot blend with the previous frame

`LayoutLabel.paintComponent` (`:1356-1372`) sets an `AlphaComposite` before `super.paintComponent`, so
the icon *and* the component's own background go down at 40%. That is a stale-pixel hazard on an
opaque component, whose back buffer Swing does not clear. `LayoutLabel` is a `JLabel` and never calls
`setOpaque(true)` — `grep -n "setOpaque" src/org/traincontrol/gui/LayoutLabel.java` returns nothing —
so Swing repaints from the nearest opaque ancestor and the fade composites onto the parent's
background, which is what it is meant to do.

### E8-D6 — the planner and the runtime cannot disagree about a prefix in the unsafe direction

`HomeStaging.java:1028-1029` chooses `measuredRoomAtTheEndOf` when `next.equals(to)` and
`roomAfterASwitchOnTheWay` otherwise; `Layout.whyTooLongForThisRoute` (`:7581-7582`) chooses on
`i == ordered.size() - 1`. These differ where a search route passes *through* the goal square and
carries on: the planner applies the berth rule, which is the stricter of the two because it needs no
switch behind it. Stricter on the planner's side is the safe direction — a refused plan, never a
movement the runtime would not make — and it is the direction `HomeStaging`'s own comments say the
class must never be wrong in. **Ran** `core.testHomeStaging` (92 tests) and
`core.testATrainMustFitEverySquareOnItsRoute` (6 tests) — 0 failures.

### E8-D7 — the failing classes in the scratch output do not exist

`$TC_SCRATCH/oneout/junitreports/` holds `TEST-core.testNoTrainIsStrandedByTheRoomRule.xml` and
`TEST-core.testTheOnTheWayRuleIsBoundedByASwitch.xml`, both with failures in them, and the directory is
not cleared between runs. Asking `one.sh` for either name answers "Cannot load class from file";
`ls test/core/` has neither, and `grep -rn` over `*.java`, `*.xml` and `*.md` finds no reference to
either name anywhere in the tree. They are leftovers from an earlier session, and nothing cites them —
so this is not a broken `build.xml` entry either.

### E8-D8 — no live event-thread defect

Every `synchronized` `Layout` method and every transitive reacher was traced to its UI call sites. All
five UI calls into the twelve unlisted reachers are inside `new Thread`. `Layout.java` contains no
reference to `javax.swing` at all and reaches the UI only through non-blocking callbacks. There is no
`SwingWorker`, no `Future.get()` and no `Thread.join()` in `src/`. The three helpers that would let the
event thread wait on a monitor-taking worker — `TrainControlUI.awaitCoveredTrack` (`:7162`),
`TrainControlUI.awaitReturnHomeTriage` (`:23620`), `AutonomyEditorPanel.awaitWhy` (`:6721`) — have no
production caller and are all deadline-bounded. The AB/BA order that matters (a driving thread inside
the `Layout` monitor calling `repaintSwitch`, which is `synchronized` on `TrainControlUI`) is intact:
all fifteen `synchronized` `TrainControlUI` methods were checked and none takes the `Layout` monitor;
three that appeared to were traced and are false positives, each reaching the door only from inside a
lambda or an `invokeLater`. The two last-three-days additions on the event thread are clean —
`LayoutLabel.java:1356` reads a `volatile` field, and `TrainControlUI.java:6255` submits to a worker.

---

## What I did not cover

- The 181 commits before 2026-09-07 were sampled through what the recent work touches, not read.
  Nothing older was found that the recent work invalidated, but that is a weaker statement than the
  rest of this document.
- `docs/manual-tests/` — the ledger, the triage database and the 38 tests awaiting Adam were read for
  context and not audited.
- The German, Danish, Spanish, French, Italian, Dutch and Polish bundles were not checked beyond
  noting that `core.testMessageBundles` is in the battery and was green.
