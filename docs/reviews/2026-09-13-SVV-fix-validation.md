# Validation of the fix round of 2026-09-13

**Status:** closed 2026-09-13, except where the table below says otherwise

**What happened to each finding is in the status column and in `docs/manual-tests/findings.tsv`.**
The repairs were made after this document was written and were not validated by a further round -
Adam capped the validation at two - so the code they touch is reviewed by its own claims and by the
battery, and the report for him lists them.

**Prefix:** SVV

**Validated:** the ten items of the 2026-09-13 fix round as they stand in the uncommitted working tree
on `autonomy-diagram-r0` on the evening of 2026-09-13, against
`docs/reviews/2026-09-13-SEV-seven-day-review.md` and the MT-367/MT-368/MT-376/FR-074/FR-075 entries
those fixes answer. This is not a re-review of the branch: it is an attack on today's fixes and on the
claims written to hold them.

**Read-only, and nothing was run.** No build, no test, no `java`, no `ant`, no `battery.sh`. Nothing
under `cs2_sample_layout/` was written; `config/autonomy/configuration-Main.json` and
`config/autonomy/setup.json` were READ, for the point flags and the three measured tiles quoted below,
and for nothing else. Every figure here comes from those two files or from the source.

## Method

For each fix: the diff, then the whole method in its current form, then the methods it delegates to and
every caller whose answer could move - `GraphReducer.placesAlong`/`locationsOf`/`lengthOf`/`sumLength`,
`Edge.getPlaceIds`/`getPlaceLengths`, `Point.isSamePlaceAs`/`getBlock`, `Layout.isSendableDestination`,
the two path-end filters at 4011 and 4272, `barredFromAutonomy`, `standOnTheCopyItDidNotTurnOn`,
`executePath`'s two overloads and all four call sites, `ManualReversalPrompt.forJourney`/`KEEP_DIRECTION`,
`AutonomySession.setPointProperty`/`writePointProperty`/`placedLocomotives`/`isParking`/`setAutoDestination`,
`AutonomyEditorPanel.promptStationLabel`/`refuseCaptionDrop`, and `TrainControlUI`'s cut and paste
branches with the two paths that clear the clipboard.

Then each new claim was mutated on paper: the fix broken in the smallest way that keeps its shape, and
the claim set read to see which claim goes red. Four survive every claim and are `SVV-B2`, `SVV-C3`,
`SVV-C4` and part of `SVV-C9`.

**Figures measured from the live folder.** `configuration-Main.json` (71 authored points): all **12**
squares carrying `parking: true` also carry `autoDestination: false` - the berth exemption is not a
coincidence, and `AutonomySession.isParking`/`isAutoDestination` make it structural. Four squares carry
`canReverse` and 19 carry `mustReverse`; of the compulsory ones only `1 - Main:20,14` (BottomMainC) is
left as an autonomy destination. `setup.json` `tileLengths` holds exactly **three** measurements:
`5:19,12` = 1, `5:13,12` = 1, `5:10,10` = 2. Note that `5:10,9` - TunnelLongPark itself, per
`pointNames` - is **not** measured today; `5:10,10` is the tile behind it. That correction matters to
SEV-B1's worked example and to SVV-B1 below.

---

## A - wrong behaviour on the layout

| id | status | where |
|---|---|---|
| (none) | | |

Nothing in this round produces wrong behaviour on the railway. SVV-B1 is a refusal, and its train is
safe where it is.

---

## B - incorrect results or refusals in specific configurations

| id | status | where |
|---|---|---|
| SVV-B1 | fixed | `Layout.whyABerthCannotHoldIt` 8118-8125 against 8147 |
| SVV-B2 | fixed | `core/testABerthAndAPlatformJudgeAnOverhangDifferently` 334-345 |

### SVV-B1 - the berth rule's "nothing has been measured" guard was not moved with the allowance, so measuring a berth alone brings PRW-B1's invented refusal back

**Where.** `src/org/traincontrol/automation/Layout.java`: the `anyMeasured` sweep at 8118-8125
(`for (Integer span : spans) if (span != null && span > 0) anyMeasured = true;` - **over every span,
including the last**) against the allowance the same round added at 8147
(`boolean onTheAllowance = (n == ids.size() - 1);`, and the last place's span is then never spent).

**What the fix did, and what it left.** SEV-B1 was right about the disagreement and the repair is the
right shape: the last place IS the berth, verified by construction rather than by reading -
`GraphReducer.placesAlong` appends `new Place(edge.getEnd().toString(), lengthOf(edge.getEnd()))` after
the path steps (1508-1513), and `locationsOf` returns exactly one id per step, so the spans still sum
to `getLength()` and the two walks now spend the same squares. That half is sound and is recorded under
SVV-D3.

What was not moved is the guard in front of it. `anyMeasured` exists to answer PRW-B1: *"a guard that
refuses on nothing is the over-strict check he would rather not have at all"*, and it implements
"nothing is known about this approach" as "no span is greater than zero". After today the berth's own
span is **not** a fact about the approach - it is explicitly the one number the walk refuses to spend -
but it still flips `anyMeasured` to true.

**What happens.** Approach places `[t(0), u(0), berth(2)]`, train of two units:

- `anyMeasured` = true, because the berth measures 2.
- `n = 2` (the berth): claimed, `onTheAllowance`, **nothing spent**, `left` stays 2.
- `n = 1`, `n = 0`: spans are zero, `left` stays 2, both claimed.
- Claimed = the whole approach. The shared-metal loop then refuses the berth against any road that
  touches any part of it - which for TunnelLongPark is `BottomMainAPre -> RampDown` and
  `-> BottomCrossover`, the only two roads to the lower level, at a cost the rule's own javadoc puts at
  90 ordered pairs of stations.

Before the fix the same case was **accepted**: `left = 2 - 2 = 0`, break, claimed `{berth}`.

**Reachable by, and this is the part that makes it B.** It is the state the round's own documentation
tells Adam to create. `Automation.md`'s new section, added in this same working tree: *"Measure **the
squares a train comes to rest on**, and the run back to the switch behind each one"* - the berth is
named first in that sentence. His railway has three measured tiles and none of them is a berth, so the
first berth he measures, before he measures its approach, turns every short train's berth acceptance
into a refusal. Every door asks the same static method (both hand doors pre-check it, `isPathClear`
refuses on it, `HomeStaging.firstClearRoute` asks it at the destination), so they all refuse together,
by hand and through Return Home.

**Not a disagreement between the two walks.** `walkStandingTrains` over-claims identically here - its
measurement rule is per EDGE (`segment.getLength() <= 0`) and an edge whose only measured place is the
standing square still has a positive length - so the pair SEV-B1 set out to reconcile IS reconciled.
Both are now on the refusing side of a question neither can answer, and `Automation.md`'s own "one thing
to watch" paragraph describes that outcome for the guard. What is lost is the berth rule's ability to
say "I do not know", which is the whole of PRW-B1.

**Remedy shape.** Compute `anyMeasured` over `spans` **excluding the last** - one bound change, mirroring
the walk it was just made to mirror. That keeps the fix's agreement with the guard and restores the
PRW-B1 answer for an approach nobody has measured. See SVV-B2: the repair is currently blocked by a
claim.

### SVV-B2 - the claim that pins PRW-B1 now passes because of SVV-B1, and goes red against the repair

**Where.** `test/core/testABerthAndAPlatformJudgeAnOverhangDifferently.java` 334-345, the second half of
`testAnUnmeasuredApproachRefusesNothing`.

```java
List<Integer> some = new ArrayList<>(none);
some.set(some.size() - 1, 1);
approach.setPlaces(approach.getPlaceIds(), some);
assertNotNull(Layout.whyABerthCannotHoldIt(justTheApproach(approach), train), ...);
```

`some.size() - 1` is the LAST place, which after today's fix is the allowance - the one span the walk is
guaranteed not to spend. So the assertion's stated reason, *"the last tile of this approach measures 1
and the train is 3, so two units hang back over track the rule can see"*, is no longer what happens:
nothing is spent at all, the whole approach is claimed, and the refusal comes from the over-claim rather
than from the arithmetic. **The claim passes for the opposite reason to the one it gives.**

Worse, it is now load-bearing in the wrong direction. Apply SVV-B1's remedy - exclude the allowance from
`anyMeasured` - and this measurement is no longer "one measurement to reason from": `anyMeasured` is
false, the method returns null, and `assertNotNull` fails. So the guard argues for the defect, which is
the `||` pattern from MT-368 arriving one file over, in the same round that wrote the paragraph about it
(`core.testTheArrivalHonoursTheAnswer`'s header, and `testNonReversibleTrains` 804-828).

**Remedy shape.** Move the measurement onto a place the walk actually spends - `some.set(some.size() - 2, 1)`
- and add a precondition asserting that the place being measured is not the last, which is what makes the
claim about the MT-364 ruling rather than about the allowance. The class's other berth claims
(`testTheBerthRuleNamesTheRoadItWouldFoul`, `testAParkingBerthStillTakesATrainThatFits`) are unaffected
by today's fix and stay green, because `THE_BERTH` in that fixture is `1 - Main:10,10` - the tile
*behind* TunnelLongPark, not TunnelLongPark itself - so the last place measures zero and skipping it
changes nothing. Which also means **no claim in the suite discriminates SEV-B1's fix at all**; the one
that comes closest is the one above, and it discriminates it backwards.

---

## C - narrow, latent, or a claim that cannot fail

| id | status | where |
|---|---|---|
| SVV-C1 | fixed | `Layout.turnsOnArrival` 5857 against `shouldReverseAt` 5924 |
| SVV-C2 | answered | `ManualReversalPrompt.forJourney` 145 against `Layout.turnsOnArrival` 5857 |
| SVV-C3 | fixed | SEV-B3's fix (`Layout` 6320) is pinned by nothing |
| SVV-C4 | fixed | SEV-C1's fix (`Layout.hasAWayThrough` 4657) is pinned by nothing |
| SVV-C5 | fixed | `TrainControlUI` 6765-6770, the clipboard heading's condition |
| SVV-C6 | fixed | `AutonomyEditorPanel.addCaptionItems` 2455 against `promptStationLabel` 2786 |
| SVV-C7 | fixed | `AutonomyEditorPanel` 2078 and `AutonomySession` 6229 - two comments SEV named, unrepaired |
| SVV-C8 | fixed | `Layout.whyABerthCannotHoldIt` javadoc 8058-8062, and `Automation.md`'s berth example |
| SVV-C9 | fixed | `regression/testFiftyLocomotiveMappingPages` - an unbounded EDT loop, a loose dialog match, a silent skip |

### SVV-C1 - the new fence knows about `ALWAYS_REVERSE` and not about `null`, which the same file calls autonomy

`Layout.java:5857`:

```java
if (loc != null && !loc.isReversible() && reversals != ALWAYS_REVERSE && hasAWayThrough(arrived))
```

Fifty lines below, `shouldReverseAt` fences the identical question as
`if (reversals == null || reversals == ALWAYS_REVERSE) return current.isReversing();` (5924), and the
comment beside the new clause cites exactly that line as the precedent it follows - *"`shouldReverseAt`
fifty lines below fences the identical question the identical way"*. It does not: it omits the first
disjunct. `turnsOnArrival`'s own `@param` says *"the policy, or null for autonomy"*, so by the method's
documentation a non-reversible train under autonomy at a may-turn square is NOT turned - which is
SEV-B2's stranding, reached by the other door.

**Traced to the end, and it is not reachable today.** `executePath(path, loc, speed, ttp)` converts to
`ALWAYS_REVERSE` (6439) and both internal callers use that four-argument form (4097, 5416); the only two
five-argument callers are the hand doors, and both hand `ManualReversalPrompt.forJourney`
(`AutoLocomotiveStatus` 1147 -> 1153, `LayoutRightclickAutonomyMenu` 1342 -> 1349), which never returns
null. So nothing reaches `turnsOnArrival` with a null policy except the tests
(`testTheArrivalHonoursTheAnswer` 545, 549, with a reversible locomotive).

A trap for the next caller, of exactly the kind SEV-C1 recorded about `hasAWayThrough` - and this rule
has now had four in a fortnight. One disjunct, and the comment stops being false.

### SVV-C2 - the prompt half and the decision half ask different questions, and disagree at a may-turn dead end

`forJourney`'s new clause (145) fires on `first != null`, which means `asking.asksAbout(arrival)` - the
SETUP's `mayTurnTiles()`. `turnsOnArrival`'s clause fires on `hasAWayThrough(arrived)` - the GRAPH's
"some copy of this square is neither terminus nor reversing". Enumerated, they agree everywhere except
one shape: a square marked `canReverse` at which an arriving train has nowhere to go but back, where
`AutonomyBuilder.nodesFor` emits `!must && (onwards || !canTurn)` = false and therefore **no plain copy
at all** (PRV-C7).

There: `asksAbout` is true, so the operator is not asked, on the stated reason *"the only answer it
could give is the one it is going to get"*. `hasAWayThrough` is false, so the clause does not fire,
`KEEP_DIRECTION.asksAbout` is false, `arrived.isTerminus()` is true, and the train IS turned. The answer
it could have given is the one it does not get.

**The outcome is right** - a dead end has to turn a train or it can never leave, and this is the same
train MT-245 is about - so this is not a behaviour finding. The comment's reason is wrong there, and the
comment is the thing a reader will act on. PRV-C7 measured the shape as not reachable on Adam's railway
today (four may-turn squares, all with plain copies). One sentence in `forJourney` naming the case, or
the same `hasAWayThrough` test on both halves so the two rules are literally one.

Checked in the other direction and clean: at a compulsory terminus, at a compulsory reversing copy, at a
plain copy and at a may-turn turning copy with plain siblings, the prompt half and the decision half
give the same answer under every combination of `loc.isReversible()` and the four policy kinds. And
mid-path reversals still go through `shouldReverseAt`, which has no non-reversible clause - correct,
because `isPathClear` refuses a path that transits a terminus copy and a reversing copy mid-path is a
compulsory turn.

### SVV-C3 - SEV-B3's fix has no claim that can fail

`Layout.java:6320` is now `remaining -= segment.getLength() - allowance;`. The arithmetic is right
(SVV-D4), and nothing under `test/` can tell it from the line it replaced.

`core.testAStationsSizeIsAnAllowance` is a single-edge fixture and says so; its claim asks only that
`BEHIND_PARK` is in `placesCoveredByStandingTrains()`, which is decided by the `left` loop before
`remaining` is touched. **Mutation: delete `- allowance` at 6320.** Every claim in that class passes -
the walk stops one hop earlier, and the fixture has no second hop. SEV-B3 itself said the class cannot
see it; the fix shipped anyway with nothing added.

What it needs is the fixture SEV-B3 describes: a station square measured `a`, an approach whose other
places sum to `p1`, a second measured edge behind the approach joined by a single neighbour, and a train
with `p1 < L <= p1 + a`. The claim is that a place on the SECOND edge is covered.

### SVV-C4 - SEV-C1's fix has no claim that can fail either, and is a no-op at both callers

`hasAWayThrough` now tests the copy it is asked about (4657-4661, `square.isSamePlaceAs(copy)` is true
for `copy == square` because `isSamePlaceAs` returns true on `this.equals(other)`).

Both callers pass a copy they have already established is a turning copy - `isOfferableToOperator`
because the clause's own `(end.isTerminus() || end.isReversing())` precedes it, `turnsOnArrival` because
the answer is only consulted where the flags would otherwise decide - so including it changes no answer.
**Mutation: restore `if (copy == square) continue;`.** Every claim in
`testAMayTurnStationIsNotATerminus` and `testTheArrivalHonoursTheAnswer` passes; the two classes' own
`aWayThroughExists` helper still implements the OLD rule (skipping the square itself), which is
deliberate there and is a second reason nothing can see the change.

Correct as a trap removal, and worth recording as unpinned rather than as pinned: the next reader should
not read the two classes' green as evidence for it.

### SVV-C5 - the clipboard heading wins whenever the clipboard names the train, not only when the train is off the railway

`TrainControlUI.java:6765-6770`:

```java
if (this.cutFacing != null && placing == this.cutLocomotive
    && getAutonomySession() != null
    && getAutonomySession().facingsFor(aimed).containsValue(this.cutFacing))
```

`placing` is `cutLocomotive != null ? cutLocomotive : getActiveLoc()`, so `placing == this.cutLocomotive`
is true for every paste while the clipboard is loaded - it is not a test of anything. The comment beside
it says the opposite: *"Only for the train on the clipboard, so an ordinary drag of a train that is still
on the railway keeps the walked answer - which is the better one, because that walk really can say where
the train would end up."*

The lifetime is otherwise clean. `cutFacing` is written only in the cut/copy branch (6856, null on a
copy), cleared on a successful paste (6837) and on locomotive deletion (20596); the four early returns in
the paste branch deliberately keep both fields, which matches *"the clipboard still holds it"*. What
nothing clears is the clipboard when the cut train is put back on the railway by some OTHER door - the
diagram's right-click Place item and the Locomotive tab neither read nor clear `cutLocomotive`. So:
Control+X at A, place at B by right-click, Control+V at C. The walk from B can now answer properly, and
the pre-cut heading from A overrides it wherever C can hold that side - which on a plain two-sided
platform is always.

Narrow, and the fix is right for the case it was written for. Either test that the train is not on the
railway (`facingOf(placing.getName(), running) == null`, which is what the comment describes), or clear
`cutFacing` in the placement doors.

### SVV-C6 - "shows itself" is narrower than the door it explains, so the reported symptom survives on one path

`AutonomyEditorPanel.java:2455`: `boolean showsItself = session.getStore().isStation(tile) && tile.equals(captioned);`

`promptStationLabel` short-circuits on `isStation(tile)` ALONE (2786-2790: `applyCaption(tile, tile); return;`).
The two predicates differ for a station square whose caption points at a DIFFERENT station: the item
stays enabled, reads "Show a Different Station Here...", promises a chooser with its ellipsis, and on
being pressed silently rewrites the caption to the square's own station. That is Adam's sentence
verbatim - *"is it meant to reset the label?"* - and it is the one state in which the item really does
reset something.

Reachable: `refuseCaptionDrop` (2557-2588) refuses a control, another page, an occupied target and the
source square, and does NOT refuse a station square, so a caption can be dragged onto one. The third
state (a station square with no caption entry) is correctly left enabled - `applyCaption(tile, tile)`
there is a visible change, which SEV-D4 checked.

The affordance and the guard want the same question: `showsItself = session.getStore().isStation(tile)`,
with the "Stop Showing" item below already covering the removal. `guard-and-affordance-same-question`.

### SVV-C7 - two comments SEV named as part of the defect were left saying the wrong thing

- `AutonomyEditorPanel.java:2078`, inside the block SEV-C3 was raised about: *"Counted off the same
  question that greys it - `tilesWithALocomotive` - so the affordance and the guard cannot answer
  differently."* The item is now counted off `placementsAutonomyWillWrite()`. A comment naming the method
  the fix removed, sitting on the fix.
- `AutonomySession.java:6229`, `homeEveryPlacedTrain`'s javadoc: *"`setHome` is where that rule lives -
  so the loop calls it and only the re-derive is lifted out."* The loop calls `writeHome`. SEV-C2's
  closing sentence was *"The comment is the defect as much as the loop"*; the loop was fixed and the
  comment was not.

Both are `comments-self-contained` failures of the plainest kind - a reader with only the file would be
told the wrong method name in both places.

### SVV-C8 - the berth rule's javadoc and `Automation.md` still describe the arithmetic the fix replaced

`Layout.java` 8058-8062 still reads *"spend the train's length across the approach's places from the end
it comes in by, claiming each before spending it"* - which is now false of the last place - and 8055
still reads *"A two-unit train fits inside the berth and lies over nothing at all"*, which under the
allowance depends entirely on whether the tile BEHIND the berth is measured. SEV-B1's remedy asked for
both, and for the matching sentence in `Automation.md`'s berth example. None was made. The
`Automation.md` "one thing to watch" paragraph happens to describe the new behaviour correctly, which is
luck rather than a correction.

### SVV-C9 - the new mapping-page class can hang the JVM, matches any dialog, and skips silently

`test/regression/testFiftyLocomotiveMappingPages.java`:

- **123-126.** `SwingUtilities.invokeAndWait(() -> { while (ui.canAddLocMappingPage()) ui.addLocMappingPage(); });`
  is an unbounded loop on the event thread with no `timeOut` on the `@Test`. If `addLocMappingPage` ever
  fails to increment - the modal refusal firing early, a preference write failing - the EDT spins for
  ever and the run never ends. This class's own comment records having hung for twenty minutes once
  already; that hazard was closed for the refusal and left open for the fill. A ceiling on the loop
  (`for (int i = 0; i < ceiling + 1 && ui.canAddLocMappingPage(); i++)`) and `@Test(timeOut = ...)`.
- **181-196.** `waitForDialog` returns the first showing `java.awt.Dialog` in `Window.getWindows()`,
  whatever it is, and 162 then disposes it. Safe in a per-class JVM, wrong the day this class shares one.
- **199-213.** `pages()` throws `SkipException` if `numLocMappings` is renamed, so a rename turns the
  class from a guard into a green tick. `test-green-is-not-no-failures`.
- The two claims themselves DO discriminate, and the stated mutation works: with `canAddLocMappingPage`
  deleted from `addLocMappingPage` no dialog appears, `assertNotNull(refusal)` fails after twelve
  seconds, and the count claim would have failed too. The daemon thread calling a Swing method off the
  EDT is deliberate and documented; the alternative deadlocks.

---

## D - checked and sound

| id | status | subject |
|---|---|---|
| SVV-D1 | fixed | MT-367: `isTerminus() \|\| isReversing()`, and the berth exemption |
| SVV-D2 | fixed | MT-367: the three sibling sites that still read the flag alone |
| SVV-D3 | fixed | SEV-B1: the last place really is the berth |
| SVV-D4 | fixed | SEV-B3: the arithmetic, on every hop |
| SVV-D5 | fixed | MT-368 item 1: the `ALWAYS_REVERSE` fence closes SEV-B2, and turning beats stranding |
| SVV-D6 | fixed | SEV-C1/C2: `writeHome` skips only the re-derive |
| SVV-D7 | fixed | SEV-C3: `placementsAutonomyWillWrite` is right for the CLEAR door too |
| SVV-D8 | fixed | FR-075: `homeEveryPlacedTrain`, its item and its confirmation |
| SVV-D9 | fixed | MT-376: the blocker list's filter, and FR-074's sort and grey |
| SVV-D10 | fixed | the eight new bundle keys |
| SVV-D11 | fixed | the new claims that CAN fail |
| SVV-D12 | fixed | PRV-C5's hard-coded log line, repaired in passing |

**SVV-D1 - MT-367.** Refusing `isReversing()` copies refuses nothing that should be offered, and the
berth exemption holds structurally rather than by accident. `AutonomySession.isParking` (3778-3790) is
true when `autoDestination` is FALSE, or `parking` is TRUE, or the legacy `reversing` is TRUE, and
`isAutoDestination(tile) = !isParking(tile)` - so a square marked as a berth cannot also be an autonomy
destination, and the exemption cannot be lost by a setup edit. Measured on the live folder: all 12
`parking` squares carry `autoDestination: false`. A may-turn square keeps its plain copies and is still
offered, which `testAMayTurnSquareIsStillOffered` asserts and which the mutation (drop `hasAWayThrough`)
breaks. A reversing point that is also a through route cannot exist - a Point that is reversing is a
turning copy by construction, and the plain copy of the same square is a different Point, which
`hasAWayThrough` finds.

One rationale note rather than a finding: the comment argues the exemption from *"a square autonomy never
chooses is somewhere the operator is deliberately putting a train away"*, and `isAutoDestination()` is not
that set - `isSendableDestination` also excludes every `reversing` copy, so autonomy never chooses those
either, yet the clause refuses them. Adam asked for exactly that refusal (*"still offered it for
2-8-4 3505"*), so the code is right and the sentence is one case too wide.

**SVV-D2 - the sibling sites.** `fix-one-site-sweep-the-siblings` was checked and comes back clean.
Three other places still read `end.isTerminus()` alone against `loc.isReversible()` - 4011
(`canReachSomewhere`), 4272 (pickPath's candidate filter) and 4703 (`barredFromAutonomy`'s explanation) -
and none of them needs the widening, because all three are gated by `isSendableDestination`, which is
`end.isDestination() && end.isActive() && end.isAutoDestination() && !end.isReversing()`. The
`reversing` half of a compulsory turn is already excluded from autonomy; the `terminus` half is excluded
by the clause that is there. Per-copy, so a may-turn square remains reachable to autonomy through its
plain copies, which is what should happen. Left alone correctly.

**SVV-D3 - the last place.** Verified by construction, not by reading: `GraphReducer.placesAlong`
(1500-1516) walks `edge.getPath()` and then appends `new Place(edge.getEnd().toString(), ...)`, and
`locationsOf` (1426-1441) returns exactly one id per step, so the appended end place is always last and
the spans still sum to `sumLength(path) + lengthOf(tile)` = `getLength()`. Both SEV-B1's and SEV-B3's
arithmetic rest on that sum and it holds.

**SVV-D4 - the hop budget.** `allowance` is declared inside the `while` (6262) so it resets per hop; it
is written only under `onTheAllowance` = `fromTheEnd && step == 0 && here == standingHere` (6294), and
`here` can never return to `standingHere` because `walked` holds its name from the start and
`walked.add(next.getName())` breaks the loop on a repeat. So it is zero on the second and third hops and
`remaining -= segment.getLength()` there, unchanged. On the first hop the two budgets now agree exactly:
`remaining` becomes `L - p1` and `left` ends at `L - p1`, where `p1` is the sum of the non-allowance
spans. The early `break` on `left <= 0` cannot desynchronise them - if the places exhaust the train then
`p1 >= L` and `remaining <= 0` too. The `!fromTheEnd` case (a turned train walking an outgoing edge) is
also right: that edge's length and places both exclude the standing square, so nothing needs crediting.
The one gap, recorded rather than raised: an edge with a length but no places skips the allowance
entirely, which is the pre-fix behaviour for a configuration written before 3.0.0.

**SVV-D5 - the fence, and the question SEV asked about it.** The fence is in the right place and it does
close SEV-B2 completely: the staging planner and the recorded timetable both reach the arrival through
`executePath`'s four-argument form (4097, 5416), which is `ALWAYS_REVERSE`, so the plan's turning copy is
turned and the next leg starts where the plan expects. Only `null` is missing, and only in theory
(SVV-C1).

On "is turning it better than stranding it": yes, clearly. The stranding was `errorLocomotiveNotAtPathStart`,
`STAGING_MAX_ATTEMPTS` retries, `stopLocomotives()` and a half-staged fleet - OB-073's shape, and the case
the planner's own comment forbids. The turn is a `switchDirection()` on a train that cannot run
backwards, which the railway already does to every non-reversible train backing into a terminus on
Adam's own MT-245 ruling; and MT-367 keeps autonomy from CHOOSING a compulsory turn for such a train in
the first place, so what is left is a may-turn copy the route itself picked. The comment at 5852-5856
raises the remaining routing question with Adam rather than deciding it, which is the right disposition
under `flag-rather-than-decide-silently`.

Also checked: `standOnTheCopyItDidNotTurnOn` now fires on far more arrivals, because a non-reversible
train takes the `else` branch at every square. It is a no-op everywhere it should be - the first line
after the null checks is `if (!arrived.isTerminus() && !arrived.isReversing()) return;` (3250) - and it
still runs after `unlockPath` inside the monitor (7465), where PRV-B2 put it.

**SVV-D6 - `writeHome`.** It skips exactly one thing and it is the right thing. `setPointProperty`
(6359-6369) is `writePointProperty(...)` followed by `deriveStationIndex()`, with nothing else in it, and
`writePointProperty` (6383-6404) does the whole JSON write and sets `dirty`. The one-locomotive-one-station
sweep stays in `writeHome`, so both doors go through it; `setHome` is now `writeHome` plus the re-derive,
so the single-square door is unchanged in behaviour. The `tile == null` guard survived the split.

**SVV-D7 - the CLEAR door.** `placementsAutonomyWillWrite()` is the right set for it, and the reason is
already written down: `placedLocomotives`' own javadoc (1760-1771) says *"A placement on an excluded page
is not wrong... switch the page back on and the placement is there, which is the point of keeping it."*
So `clearEveryPlacement` deliberately leaves them, and narrowing the count to match the action is the
correct half to move. `bulkTools` computes `placed` once (2018) and both the clear item and the new home
item read it, so the two labels cannot drift from each other either.

**SVV-D8 - FR-075.** `homeEveryPlacedTrain` (6243-6262) iterates a single snapshot of `placedLocomotives()`,
calls the shared `writeHome` per train so the sweep cannot be lost, and re-derives once at the end -
which is `clearEveryHome`'s shape and now genuinely is. The panel's door (2081-2143) keeps the emptiness
check as a guard behind the greying, asks the same question both times, and calls `setupChanged()` for
VD10-B2's reason. The confirmation names what is overwritten rather than what is lost, which is right for
the only bulk tool that adds. One narrow inaccuracy worth nobody's time: if two squares hold the same
locomotive name, `assigned` counts both while only the second keeps its home.

**SVV-D9 - MT-376 and FR-074.** The station filter (3999) sits before every other `continue`, and the
`!already.contains(tile)` exemption is on it, so no stored non-station entry is hidden - and the recovery
loop at 4048-4051 catches any stored entry the walk never reached at all, so FBR-A2's deletion still
cannot recur. The sort (4053-4070) runs after both loops and keys on `describeTile`, which is the check
box's own text. The grey is a foreground colour with the box still clickable, and
`stationsAutonomyWillNotChoose()` is read once outside the loop.

**SVV-D10 - the bundle keys.** All eight new keys are present in all eight bundles. The namespaces match
their call sites exactly (`autolayout.ui.menuHomeEveryTrainWhereItStands` and
`autolayout.ui.confirmHomeEveryTrainWhereItStands` against `autosetup.ui.` for the other six - an
inconsistency, not a fault). ASCII-only: the non-English text is `\uXXXX`-escaped, including the German
quotation marks. `confirmHomeEveryTrainWhereItStands` carries `{0}` and correctly doubles its apostrophe
for MessageFormat; the no-placeholder keys are called through `I18n.t` and the rest through `I18n.f`.

**SVV-D11 - the claims that can fail.** Mutated on paper, one at a time:

- `testATrainThatCannotReverseIsNotTurnedWhereItNeedNot`, all three assertions. Delete the clause and the
  first two go red (`KEEP_DIRECTION.asksAbout` is false, so `isTerminus()` on the turning copy would turn
  it; and the answering policy would return `true`). Delete `reversals != ALWAYS_REVERSE` and the third
  goes red. This is the only claim in the round that pins the fence, and it does.
- `testTheReversingCopyOfACompulsoryTurnIsRefusedToo`. Its fixture clears the terminus flag before
  setting `reversing`, and asserts `assertFalse(reversing.isTerminus())` as a precondition precisely so
  that the old rule could not have refused it. Restore `end.isTerminus()` alone and it goes red.
- `testAMayTurnSquareIsStillOffered` and `testAParkingBerthIsStillOffered`: drop `hasAWayThrough` or
  `isAutoDestination` respectively and each goes red.
- `testTheAllowanceDoesNotAbsorbTheTrain`. Restore `left -= span` on the standing square and the park's
  measurement of 10 absorbs the two-unit train, `BEHIND_PARK` is not claimed, red. Its precondition
  (`getTileLength(PARK) > 2`) is the assertion that makes it discriminating, and it is there.
- `testTheFiftiethPageIsTheLast`, both halves, as described in SVV-C9.

**SVV-D12 - PRV-C5.** The hard-coded English sentence at the re-stand is gone: 3277 is now
`this.control.logf("autolayout.log.stoodOnTheCopyItFaces", ...)`. Not claimed by the round and worth
recording as done.

---

## What this pass did NOT cover

- **Anything run.** No test, no build, by instruction. Every mutation above was performed on paper. A
  mutation judged survivable may be caught by an interaction not seen here, and SVV-B2's prediction that
  the class currently passes for the wrong reason - and would go red against SVV-B1's repair - should be
  confirmed by actually making the change and running the class before it is acted on.
- **Whether any of today's classes is green, and whether any skips everything.** Two of the three new
  classes throw `SkipException` from `@BeforeClass` on a moved fixture and one from inside its own
  helper; `test-green-is-not-no-failures` applies and nothing here can speak to it.
- **The figures the round measured on the live railway** - which copies BottomMainB and BottomMainC are
  emitted as, and the "half refused and half offered" observation MT-367 rests on. What was checked is
  that the mechanism each figure describes is in the code, plus the point flags and the three tile
  lengths re-derived from the two live JSON files.
- **The rest of the working tree.** Only the ten items listed in the brief and the code they touch were
  read. The PFX/PRV fixes were re-read only where today's change lands on them (the berth rule, the
  walk, `walkTo`, `facingOf`, the re-stand); `HomeStaging`'s PRW-B2 legs, `GraphReducer` beyond
  `placesAlong`/`locationsOf`/`lengthOf`/`sumLength`, `AutonomyBuilder`, `TileAnnotation`, `TileOverlay`,
  `LayoutLabel`, `Locomotive.drivableFunctionCount` and `test/baseline/configuration.json` were left to
  SEV.
- **Concurrency.** `homeEveryPlacedTrain` and the caption menu were read as event-thread code and not
  traced against a running autonomy; `hasAWayThrough` still walks every Point under the Layout's monitor
  from `isOfferableToOperator` on the event thread (PRW-D10's allowance), and today's second caller
  `turnsOnArrival` adds one call per arrival on the driver thread - not measured.
- **The translations' text.** The eight bundles were checked for presence, namespace, escaping and
  MessageFormat quoting; the wording was not read.
- **SEV-B2's planner-side remedy.** The round fenced the runtime instead, which closes the stranding;
  whether `HomeStaging` should also stop resting a non-reversible train on a turning copy - so that the
  plan describes what the railway will do rather than relying on the fence - was not evaluated here.
- **SEV-C4** (whether a train can move while the editor is open) - untouched by this round and not
  examined.
