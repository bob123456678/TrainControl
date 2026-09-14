# Validation of the fix round of 2026-09-13, round two

**Status:** closed 2026-09-13, except where the table below says otherwise

**What happened to each finding is in the status column and in `docs/manual-tests/findings.tsv`.**
The repairs were made after this document was written and were not validated by a further round -
Adam capped the validation at two - so the code they touch is reviewed by its own claims and by the
battery, and the report for him lists them.

**Prefix:** SVX

**Not SV2, which this document declared until 2026-09-13 and which was already taken.** The second
validation of 2026-09-01 numbered SV2-A1 to SV2-D6, and four of those are cited in the tree today -
`SV2-A1` from `HomeStaging.java` and `testReturnHomeSequencesAReversal`, `SV2-A2` and `SV2-C7` from
`docs/manual-tests/README.md`. Three of this round's own numbers landed on top of them. The catalogue
answers the question in one query and it was not asked:
`SELECT DISTINCT ref FROM finding` in `docs/manual-tests/triage.db`.

**Validated:** the repairs made to the nine findings of
[`2026-09-13-SVV-fix-validation.md`](2026-09-13-SVV-fix-validation.md) plus the four battery failures
fixed alongside them, as they stand in the uncommitted working tree on `autonomy-diagram-r0` on the
evening of 2026-09-13. The subject is the REPAIRS, not the branch;
[`2026-09-13-SEV-seven-day-review.md`](2026-09-13-SEV-seven-day-review.md) is the review both rounds
answer.

**Read-only, and nothing was run.** No build, no test, no `java`, no `ant`, no `battery.sh`, no Java
Preferences. Nothing under `cs2_sample_layout/` was written or read this round. `git` was used only for
`status`, `log` and `diff`.

## Method

The ten files touched since round one were found by mtime against the SVV report
(`Layout.java`, `AutonomySession.java`, `AutonomyEditorPanel.java`, `TrainControlUI.java`,
`messages.properties`, `messages_fr.properties`, `testABerthAndAPlatformJudgeAnOverhangDifferently`,
`testFiftyLocomotiveMappingPages`, `testSwitchingToACentralStationLayout`,
`testTheTurnAtTheDestinationReachesTheDiagram`), and the complete set of round-two edit sites in `src/`
and `test/` confirmed by `grep -rn "SVV-"` - six citations across four files. `Automation.md` and
`ManualReversalPrompt.java` were NOT touched, which settles two items below without reading them twice.

For each repair: the whole method in its current form, the guard or walk it was made to mirror, and then
the repair mutated on paper - broken in the smallest way that keeps its shape, and the claim set read to
see which claim goes red. Four repairs survive every claim in the suite and are named under C.

Also re-derived rather than taken from round one: `walkStandingTrains`'s edge measurement rule (6225)
against the berth rule's new place measurement rule (8134); `canAddLocMappingPage`/`addLocMappingPage`
and every other reader of `numLocMappings`, to judge the reflection fixture; the `places` JSON reader
(10330-10341); `Locomotive.setReversible` and every `saveState` trigger, to judge whether the reversible
fixture can reach Adam's own locomotive database; and the eight message bundles, counted for straight
apostrophes and for non-ASCII bytes.

---

## A - wrong behaviour on the layout

| id | status | where |
|---|---|---|
| (none) | | |

Nothing in this batch produces wrong behaviour on the railway. SVX-B1 is over-blocking, which is safe
and costly; SVX-B2 is an editor affordance.

---

## B - incorrect results or refusals in specific configurations

| id | status | where |
|---|---|---|
| SVX-B1 | fixed | `Layout.walkStandingTrains` 6225 against `whyABerthCannotHoldIt` 8134 |
| SVX-B2 | fixed | `AutonomyEditorPanel.addCaptionItems` 2463/2469 against `promptStationLabel` 2794 |

### SVX-B1 - the "measured where it counts" rule went into the berth rule and not into the walk, so SVV-B1's repair re-opens SEV-B1

**Where.** `src/org/traincontrol/automation/Layout.java`: the walk's edge measurement rule at 6225
(`if (segment.getLength() <= 0) break;`) against the berth rule's new place measurement rule at 8134
(`for (int n = 0; n < spans.size() - 1; n++)`).

**What the repair did.** SVV-B1 was right that `anyMeasured` must not count the allowance, and the bound
is correct at both edges (SVX-D1). What it did not do is say what the change costs, and the cost is the
finding SEV-B1 was: the two walks stop agreeing again, in the opposite direction.

**The pair, re-derived.** Berth measured 2, approach otherwise unmeasured, a two-unit train - which is
`Automation.md` 170's advice followed one square at a time, and the state round one's own SVV-B1 said
*"is the layout the advice produces"*:

- **Berth rule, before the repair:** `anyMeasured` true (the berth measures 2), walk claims the whole
  approach because the allowance leaves nothing to spend, **refused**. That is PRW-B1's invented
  refusal, and it is what the repair removed.
- **Berth rule, after the repair:** `anyMeasured` false, `return null`, **accepted**. Correct: a
  two-unit train in a two-unit berth fouls nothing.
- **`walkStandingTrains`, once the train is parked there, either way:** the edge measurement rule at
  6225 asks `segment.getLength() <= 0`, and the edge's length INCLUDES the berth's 2 - so the walk
  judges. Then `onTheAllowance` (6294) skips exactly that 2, every other span is zero, `left` stays 2
  for the whole loop, and every place on the approach is claimed, the switch included.
  `isPathClear`'s shared-metal sweep then refuses `BottomMainAPre -> RampDown` and `-> BottomCrossover`.

So the berth rule accepts and the guard blocks - *"a train at a berth that the berth rule accepted,
fouling a road that nothing refused it"*, which is SEV-B1's own sentence and the reason it was B. Round
one recorded the pair as reconciled (*"Both are now on the refusing side"*) and its remedy moved one of
them off that side without saying so.

**The defect is the walk's, not the rule's.** Both guards ask "is anything known about this approach",
and after Adam's allowance ruling the answer must be computed over what the walk can SPEND. The berth
rule now does that; 6225 still counts an edge as measured on the strength of a number it has already
decided not to spend. `fix-one-site-sweep-the-siblings`, on the one pair whose agreement the berth
rule's own javadoc is a promise about (8062-8065).

**Reachable by.** The first berth Adam measures, before he measures the tile behind it. His railway has
three measured tiles and no berth among them, so this is the next state rather than a hypothetical, and
every door refuses together (`isPathClear`, both hand doors' pre-checks, `HomeStaging.firstClearRoute`).

**Not A, and why it is not simply cost.** The over-blocking IS documented -
`Automation.md` 186 tells him a short train blocking a surprising amount of track means an unmeasured
square behind it - and measuring `10,10` clears it. What makes it B rather than C is that the rule and
the guard now give opposite answers about one train on one piece of track, which is the exact condition
SEV-B1 opened and the berth rule's javadoc still claims cannot arise.

**Remedy shape.** Give 6225 the same treatment: on the first hop, an edge whose only positive span is
the standing square's allowance is unmeasured for spending purposes, so `break` rather than claim. One
bound, mirroring the one just put into 8134. The alternative - reverting 8134 - restores PRW-B1's
invented refusal and is worse.

**And it wants a claim either way.** Nothing in `core.testABerthAndAPlatformJudgeAnOverhangDifferently`
or `core.testAStationsSizeIsAnAllowance` reaches the configuration (SVX-C5).

### SVX-B2 - "shows itself" now greys the one state in which the item did visible work, and says something false while it does

**Where.** `src/org/traincontrol/gui/AutonomyEditorPanel.java` 2463
(`boolean showsItself = session.getStore().isStation(tile);`) and 2469 (`name.setEnabled(!showsItself)`).

SVV-C6's remedy was applied verbatim, and it is wrong for a state SEV-D4 had already checked and
cleared. Three states of a station square, not two:

| the square | before | after |
|---|---|---|
| captions itself | disabled, "This Square Shows Its Own Station" | unchanged |
| captions ANOTHER station | enabled, "Show a Different Station Here...", silently reset it - **SVV-C6's case** | disabled, and the label is FALSE: it does not show its own station, and "Stop Showing *the other one*" sits on the next line |
| has NO caption entry | enabled, "Show a Station Name Here...", `applyCaption(tile, tile)` - a **visible change**, which is exactly what SEV-D4 verified | disabled, and the square can no longer be made to show its own name from either menu |

**The third row is the finding.** `autonomyCaptionAt` returns null for a square with no caption entry
(`TrainControlUI` 5272-5290), so such a station draws nothing at all. The only two doors that write
`caption(tile) = tile` are `promptStationLabel` 2794 - now unreachable, because the item that calls it is
greyed - and `AutonomySession` 827, which runs only inside the LEGACY IMPORT and only
`if (captionsFor(tile).isEmpty())` on a fresh name. So a station square that has lost its caption cannot
get it back through the editor, and the only remaining route is dragging some other caption of the same
station onto it, which requires one to exist.

**And the menu creates that state one line below.** `menuClearStationHereNamed` ("Stop Showing {0}") is
added immediately under the greyed item whenever a caption exists. Press it on a station square and the
name disappears; the item above then reads "This Square Shows Its Own Station" over a square showing
nothing. The tooltip's own advice - *"Use Stop Showing to take the caption off"* - walks the operator
into it.

Both menus are built through this one method (the comment at 2427 says so), so the deep menu is dead the
same way.

**Remedy shape.** The question the affordance and the guard share is not `isStation(tile)`; it is
"pressing this changes what the square shows". That is
`isStation(tile) && tile.equals(session.getCaptionTarget(tile))` - the ORIGINAL expression - plus the
missing half: for a station square captioning a different station, keep the item enabled and let
`promptStationLabel`'s `applyCaption(tile, tile)` run, but say what it will do
("Show This Station Here" - no ellipsis, because there is no chooser). That answers Adam's *"is it meant
to reset the label?"* by naming the reset instead of hiding the item, and it leaves row three working.

---

## C - narrow, latent, or a claim that cannot fail

| id | status | where |
|---|---|---|
| SVX-C1 | fixed | `Layout.whyABerthCannotHoldIt` javadoc 8054/8062-8065, `Automation.md` 178 - SVV-C8 unrepaired |
| SVX-C2 | fixed | `AutonomySession.homeEveryPlacedTrain` javadoc 6231 - SVV-C7 half-repaired |
| SVX-C3 | fixed | `Layout.turnsOnArrival` 5861 - SVV-C1's fix is pinned by nothing |
| SVX-C4 | fixed | `TrainControlUI` 6775-6781 - SVV-C5's fix is pinned by nothing |
| SVX-C5 | fixed | `core/testABerthAndAPlatformJudgeAnOverhangDifferently` 344-359 - the repaired claim does not discriminate the repair |
| SVX-C6 | fixed | `regression/testFiftyLocomotiveMappingPages` 184-196, 202-236 - SVV-C9 points 2 and 3 unrepaired, point 3 worse |
| SVX-C7 | fixed | `regression/testFiftyLocomotiveMappingPages` 118-130 - the preference claim is false under the class's own mutation |
| SVX-C8 | fixed | `regression/testTheTurnAtTheDestinationReachesTheDiagram` 194-198 - the new block was inserted above the null guard |
| SVX-C9 | fixed | `Layout` 10330-10341 - a skipped `places` entry moves which square the allowance lands on |
| SVV-C2 | answered | `ManualReversalPrompt.forJourney` 145 - file untouched this round |
| SVV-C3 | fixed | SEV-B3's fix at `Layout` 6324 - still pinned by nothing |
| SVV-C4 | open, unpinnable | SEV-C1's fix at `Layout.hasAWayThrough` - still pinned by nothing |

### SVX-C1 - the berth rule's javadoc and `Automation.md` still describe the arithmetic two rounds ago, and one sentence is now false twice over

Neither was touched. `Layout.java` 8062-8065 still reads *"spend the train's length across the
approach's places from the end it comes in by, claiming each before spending it"* - which has not been
true of the last place since the allowance landed - and 8054 still reads *"A two-unit train fits inside
the berth and lies over nothing at all"*, which under the allowance is false as soon as the tile behind
the berth is measured (the walk then claims it).

The same sentence's second half - *"so what this refuses and what `walkStandingTrains` would then block
are the same stretch of railway, rather than two answers that have to agree"* - is now false for a
SECOND reason, SVX-B1: the two are no longer the same stretch. A javadoc that promises an invariant the
code has stopped holding is worse than one that is merely out of date, because it is the argument a
reader would use to decide SVX-B1 cannot happen.

`Automation.md` 178's berth example is unchanged and still reasons "fits end to end"; SEV-B1's remedy
asked for it and round one repeated the ask. `Automation.md` 186's "one thing to watch" paragraph
remains accidentally correct.

### SVX-C2 - SVV-C7's second bullet was answered by adding a sentence and leaving the wrong one

`AutonomySession.java` 6226 now reads *"It writes through `writeHome`, which is that rule without the
re-derive (SEV-C2, where this comment was true of the intention and not of the code)."* Good. But 6231,
five lines down, still reads *"`setHome` is where that rule lives - so the loop calls it and only the
re-derive is lifted out."* The loop calls `writeHome`. So the javadoc now names two different methods as
the one the loop calls, in the same paragraph block, and one of them is the method the fix removed. A
reader with only the file is worse off than before the sentence above it was added.

The first bullet IS repaired - `AutonomyEditorPanel` 2018-2020 now cites `placementsAutonomyWillWrite`
and `placed` is computed from it (SVX-D5).

### SVX-C3 - the `reversals != null` disjunct is correct and no claim can see it

`Layout.java` 5861 is now
`if (loc != null && !loc.isReversible() && reversals != null && reversals != ALWAYS_REVERSE && hasAWayThrough(arrived))`,
and the comment beside it is true for the first time. Traced: with a null policy the clause is skipped,
the `asksAbout` branch is skipped, and `arrived.isTerminus() || shouldReverseAt(..., null)` decides -
which is what `shouldReverseAt` 5924 does with its own `reversals == null ||`. Right.

**Mutation: delete `reversals != null &&`.** Every claim in `core.testTheArrivalHonoursTheAnswer` still
passes. The only method that hands in a null policy is `testAutonomyStillTurnsAtATerminus` (545, 549),
which uses `loc` in its default REVERSIBLE state and asks about `MT368_TERMINUS` and `MT368_PLAIN` -
neither a may-turn turning copy. So the clause's first conjunct is never reached with a null policy
anywhere under `test/`, and this fix joins SVV-C3 and SVV-C4 as a repair the suite cannot tell from its
predecessor.

One line closes it, beside the `ALWAYS_REVERSE` assertion already inside
`testATrainThatCannotReverseIsNotTurnedWhereItNeedNot`'s try block, where the locomotive is already
non-reversible: `assertTrue(layout.turnsOnArrival(may, loc, null), ...)`.

### SVX-C4 - the clipboard heading's new condition is right, and nothing under `test/` mentions it

`TrainControlUI.java` 6775-6781 is sound and is recorded under SVX-D4. It is also unpinned:
`grep -rn "cutFacing\|cutLocomotive\|stillLifted" test/` returns nothing, so neither MT-368's original
clipboard clause nor SVV-C5's narrowing of it has a claim. `testAPasteDoesNotTurnTheTrainRound` is about
`facingAfterAPaste` and does not reach the cut branch.

### SVX-C5 - the repaired berth claim can fail, but not against the thing it was rewritten for

`test/core/testABerthAndAPlatformJudgeAnOverhangDifferently.java` 344-359. The rewrite is correct as far
as it goes: `some.set(some.size() - 2, 1)` measures a place the walk really does spend, the precondition
`assertTrue(none.size() >= 2, ...)` is present and is the one SVV-B2 asked for, and index `size-2` is
always reached (the loop only breaks on `left <= 0`, and `left` is still 3 when it gets there). So the
off-by-one holds and an approach of one place is refused as unexercisable rather than mis-measured
(SVX-D2).

**But it does not discriminate SVV-B1's repair.** Mutation: restore `n < spans.size()`. `anyMeasured` is
true either way here, because the measured place is `size-2` and inside both bounds. The first half of
the same method sets every span to zero, where `anyMeasured` is false either way. So neither half moves.
The fixture that WOULD have moved - the last place measured and nothing else - is the one round one
found and it was RELOCATED rather than inverted. Under `red-before-green` and
`when a root fix lands, expect tests of the old bug to fail at their preconditions`, the right shape was
to keep both: `some.set(some.size() - 1, 1)` with `assertNull` (the berth's own measurement is not
something to reason from), and `some.set(some.size() - 2, 1)` with `assertNotNull` (the tile behind it
is). As it stands nothing pins the repair, and nothing pins SVX-B1's absence either.

Also worth noting, because the assertion message will be read on a failure: with only `size-2` measured
at 1 and the train at 3, `left` never reaches zero, so the refusal still comes from claiming the WHOLE
approach - not from *"two units hang back over track the rule can see"*. That is the documented reading
of an unmeasured square (`Automation.md` 186) rather than a defect, but the sentence overstates what the
rule saw.

### SVX-C6 - the mapping-page class's loop hazard is closed and its other two are not, and one is now load-bearing

SVV-C9's first point IS repaired: the unbounded `invokeAndWait` fill is gone, replaced by
`setPages(ceiling)`, and the method carries `@Test(timeOut = 120000)` (114).

- **184-196.** `waitForDialog` still returns the first showing `java.awt.Dialog` in
  `Window.getWindows()`, whatever it is, and 168 disposes it. Unrepaired.
- **202-236.** `pages()` and now `setPages()` both throw `SkipException` on a reflective miss, and
  `pages()` is called from `setUpClass` (72) - so a rename of `numLocMappings` skips the class from its
  own `@BeforeClass` and the suite reads green. That was SVV-C9's third point; the rewrite made it
  WORSE, because the reflection is no longer just how the class reads the count - it is how the class
  reaches the ceiling at all. There is no non-reflective path left. `test-green-is-not-no-failures`.

A compile-time reference would close both halves at once: expose the count through the existing public
`getNumLocMappings()` (1639) for the read, and make the ceiling reachable by the API rather than by the
field - or, failing that, `fail` rather than `SkipException` in both accessors, since a rename is a
broken guard rather than a moved fixture.

### SVX-C7 - "this test cannot leave a preference wrong however it dies" is false under the class's own stated mutation

`testFiftyLocomotiveMappingPages` 118-130 argues that setting the field touches no preference. True while
the guard holds. Under the mutation the class documents - *"delete the `canAddLocMappingPage()` guard
from `addLocMappingPage`"* - the daemon thread reaches 1714-1718 and executes
`prefs.putInt(LOC_MAPPING_PAGES_PREF, 51)` before anything else, and then throws out of
`switchLocMapping(51)` because `locKeyTabs` holds only the real number of tabs. The teardown restores
the FIELD (`setPages(back)`) and explicitly does not restore the preference, on the strength of the
comment above. So the run that catches the regression leaves Adam's window opening with fifty-one
mapping tabs - the same damage, from the same store, that the rewrite exists to prevent.

The assertions still fire (`assertNotNull(refusal)` goes red after twelve seconds), so the mutation is
caught; the harness cost is the finding. Reading `prefs.getInt(LOC_MAPPING_PAGES_PREF, ...)` in
`setUpClass` and putting it back in the teardown is two lines and makes the comment true.

**Separately, and for Adam rather than for the code:** the killed run that motivated this rewrite wrote
fifty into the real preference, and nothing in this round undoes it. Until it is put back by hand,
`TrainControlUI`'s constructor (922) will read fifty, build fifty tabs, and `ui.testEveryLanguageFits`
will keep failing on the tab strip. The Preferences store was not touched here, by instruction.

### SVX-C8 - the reversible block was inserted above the null guard, which is now dead

`test/regression/testTheTurnAtTheDestinationReachesTheDiagram.java`:

```java
194:        reversibleWas = train.isReversible();
196:        train.setReversible(true);
198:        assertNotNull(train, "there is no locomotive in the database to drive");
```

`assertNotNull(train, ...)` guarded the two lines that followed it; the new block went in above it, so
the guard now sits after two dereferences of the thing it checks and can never fire. An empty locomotive
database produces an NPE from `isReversible()` with no message instead of the sentence written for it.
`insert-above-the-javadoc`, one member over. Move line 198 up to just after the `getLocByName`.

### SVX-C9 - a `places` entry without `at` shifts which square the allowance lands on

`Layout.java` 10330-10341: the JSON reader skips an entry with no `at` from both lists together, so a
malformed FINAL entry makes the second-to-last place the last one. Both the berth rule's
`onTheAllowance` (8163) and the walk's `fromTheEnd && step == 0` (6294) then treat the wrong square as
the arrival, and 8134's `anyMeasured` bound excludes the wrong span. Not reachable from a built
configuration - `GraphReducer.placesAlong` always appends the end place (SVV-D3) - so this is a
hand-written or truncated file only, and it is recorded as a trap rather than as a defect. Both
allowance sites read the last element positionally; neither checks it against `getEnd()`.

---

## D - checked and sound

| id | status | subject |
|---|---|---|
| SVX-D1 | fixed | SVV-B1: the `anyMeasured` bound, at both edges |
| SVX-D2 | fixed | SVV-B2: the place behind the berth is present, reached and spent |
| SVX-D3 | fixed | SVV-C1: the fence, and its comment |
| SVX-D4 | fixed | SVV-C5: `getLocomotiveLocation` is the right question at the right moment |
| SVX-D5 | fixed | SVV-C7 bullet 1: the menu's count and its citation |
| SVX-D6 | fixed | FR-075's apostrophe: all eight bundles clean, and the convention matched |
| SVX-D7 | fixed | the window-class ratchet at 38 |
| SVX-D8 | fixed | the reversible fixture: right repair, restored on every path, cannot reach the real database |
| SVX-D9 | fixed | the reflection fixture reads the field the two rules actually read |
| SVX-D10 | fixed | which of the new and changed claims CAN fail |

**SVX-D1 - the bound.** `for (int n = 0; n < spans.size() - 1; n++)` covers indices `0 .. size-2`
inclusive, which is exactly the set the claim loop spends (`onTheAllowance` is `n == ids.size() - 1`
alone). The edges hold: `ids.isEmpty()` and `ids.size() != spans.size()` are refused above it, so
`spans.size() - 1 >= 0` and the loop cannot run negative. For an approach of ONE place - the berth by
itself - the loop runs zero times, `anyMeasured` is false, and the rule declines, which is right: the
only measurement there is the one it has decided not to spend. The place behind the berth being absent
is handled; the place behind the berth being unmeasured is handled the same way.

**SVX-D2 - the place behind the berth.** Present by the test's own precondition (`none.size() >= 2`,
344), reached because the claim loop only breaks on `left <= 0` and `left` is still the full train length
after the allowance, and spent because `onTheAllowance` is true for exactly one index. So "the place
behind the berth" is always spendable when it exists, and the class refuses to reason when it does not.
The off-by-one is right for an approach of one place. What the claim does not do is discriminate the
repair - SVX-C5.

**SVX-D3 - the fence.** `reversals != null && reversals != ALWAYS_REVERSE` is now literally
`shouldReverseAt` 5924's condition negated, which is what the comment beside it claims and what SVV-C1
asked for. Behaviour under a null policy: the clause is skipped, `asksAbout` is skipped, and the last
line decides - identical to `ALWAYS_REVERSE`, which is the point. Unreachable today (all four
`executePath` sites resolve to `ALWAYS_REVERSE` or to a `forJourney` policy) and no longer a trap.
Unpinned - SVX-C3.

**SVX-D4 - the clipboard heading.** `stillLifted` is `getAutoLayout().getLocomotiveLocation(placing) == null`,
and that is the right question. It is asked BEFORE `moveLocomotive` (6882), so the train this paste is
about is still standing wherever it stands; the cut branch really does clear the point
(`moveLocomotive(null, point.getName(), true)`, 6882 in the cut arm), so a genuinely lifted train answers
null; `cutFacing` is null on a COPY, so the clause needs a real cut; and the SVV-C5 sequence -
Control+X at A, place at B by another door, Control+V at C - now keeps the walked answer from B, which
is the better one. The `model == null || getAutoLayout() == null` arms are defensive only, both already
dereferenced above. `getLocomotiveLocation` scans the Points unsynchronised on the event thread, which
is the pattern at 1790 and 4420 and is not new here.

**SVX-D5 - the menu's count.** `AutonomyEditorPanel` 2020 computes `placed` from
`session.placementsAutonomyWillWrite().size()` and the comment above it now names that method. The
label, the tooltip, the greying and the guard all read the one `placed`, so SEV-C3's drift cannot recur.

**SVX-D6 - the apostrophe.** The convention is not invented for this fix: `core.testMessageBundles` 25-30
states it - *"The convention is the `’` escape, which is correct whether or not a value goes through
MessageFormat"* - and the repair matches it. Counted, in the same way the rule does: **zero** straight
apostrophes in any non-comment line of any of the eight bundles, and **zero** non-ASCII bytes in any of
them. `confirmHomeEveryTrainWhereItStands` carries `{0}` and now has no quoting character in it at all,
which is safer than the doubling it replaced; the French key gained three `’` and stays ASCII. The
six other languages had none to fix, which is why only two files moved.

**SVX-D7 - the ratchet.** Counted independently with the same pattern the test uses
(`new (org\.traincontrol\.gui\.)?TrainControlUI\s*\(` over `test/`): **38** files, of which
`testFiftyLocomotiveMappingPages` is the new one. The bump 37 -> 38 is the measured number, not a guess.
The class opens `LayoutSandbox.open()` before `MarklinControlStation.init(`, so it is not "loose" and
`MODELS_WITHOUT_A_SANDBOX` and its name list correctly did not move.
`testTheTurnAtTheDestinationReachesTheDiagram` takes the window from `model.getGUI()` rather than
building one, so it is not in the count and never was. The ratchet sits after all four safety assertions,
which is where REG9-B1 put it.

**SVX-D8 - the reversible fixture.** It is the right repair and not a paper-over. The class's subject is
the DRAIN - *"whether the setup is told, and whether a stale facing is written backwards"* - not whether
the turn is decided; MT-368 removed the fixture's ability to produce a turn at all, which is a
precondition failure and the protocol says so in as many words (*"expect tests of the old bug to fail at
their preconditions - that is confirmation, not regression"*). With the train reversible,
`turnsOnArrival(plain, loc, TURN_IT_ROUND)` takes the `asksAbout` branch and returns true, so all four
claims reach the arrival they are about, and `TURN_IT_ROUND` now models a state the interface really can
produce (a reversible train IS prompted, and "turn" is an answer somebody can give). Nothing about the
search in `aJourneyEndingInATurn` depends on reversibility - `bfs`, `moveLocomotive` and `isPathClear`
do not read it - so the fixture it finds is the same one.

Restored on every path: the capture (194) is adjacent to the set (196) so nothing can throw between them;
`@AfterClass(alwaysRun = true)` runs after a failed `@BeforeClass` as well as after a failed test, and
guards on `train != null`; and if `setUpClass` dies before `train` is assigned, nothing was changed.

The one thing worth having checked, because `init` opens Adam's own locomotive database and
`reversible` is a real property of a real locomotive: `Locomotive.setReversible` (1405-1408) is a plain
field setter with no write-through, and the only `saveState` triggers in the tree are the quit path
(18844) and the Backup menu item (21562) - no shutdown hook, no timer. So a killed JVM persists nothing,
and a JVM that exits normally has already run the teardown. The flag cannot reach the file. (The class's
new comment names the locomotive as `02 0314-1 DDR`; that is `getLocList().get(0)` and will go stale if
his database gains an earlier name, but the code is unconditional and works either way.)

**SVX-D9 - the reflection fixture.** `canAddLocMappingPage()` (1677-1679) is
`return this.numLocMappings < MAX_LOC_MAPPINGS;` and `addLocMappingPage` (1699-1706) asks the same
method, so setting the field by reflection exercises the two rules the class claims about rather than a
number nothing reads. Checked that the fiction cannot blow the window up while it is held: the other
size-dependent readers are `nextLocMapping`/`prevLocMapping` (12265-12272, which would throw with the
count at fifty over a ten-entry list) but their only callers are the drag-to-page handlers at 10030 and
10044; the loops at 1302 (the constructor) and 20334 (the search dialog) are user- or
construction-driven. Nothing on a timer or a repaint reads it, so the field can sit at fifty for the
length of the test. The teardown puts it back before `dispose()`.

**SVX-D10 - what can fail.** Every claim added or changed since round one, mutated on paper:

- `testAnUnmeasuredApproachRefusesNothing`, second half (`some.size() - 2`): **can fail** - it goes red
  if `anyMeasured` stops counting the spendable places, if the shared-metal sweep stops finding the two
  roads, or if the rule declines on partial measurement. It does NOT discriminate SVV-B1's bound
  (SVX-C5).
- `testAnUnmeasuredApproachRefusesNothing`, the new precondition `assertTrue(none.size() >= 2, ...)`:
  **can fail** - it is a fixture-shape assert on the approach the class names, and goes red if the
  snapshot's approach to TunnelLongPark loses a place. Not a control that cannot move.
- `testTheFiftiethPageIsTheLast`, all four assertions: **can fail.** `assertEquals(pages(), ceiling)`
  goes red if the reflection sets nothing; `assertFalse(canAddLocMappingPage())` if the ceiling rule
  moves; `assertNotNull(refusal)` if the method's own guard is deleted or made silent - which is the
  stated mutation and it works, at the cost in SVX-C7; `assertEquals(pages(), atTheCeiling)` if it
  increments. The last two are not the same claim: a guard that returned quietly would pass the count
  and fail the dialog.
- `testSwitchingToACentralStationLayout`'s `assertEquals(checked, 38)`: **can fail**, both ways, and it
  now sits behind the safety assertions rather than in front of them.
- `testTheTurnAtTheDestinationReachesTheDiagram`'s four claims: **unchanged and can fail** - each names
  a mutation that was run when they were written, and the fixture change restores the precondition they
  need rather than weakening any of them.
- **CANNOT FAIL - nothing pins these:** SVV-C1's `reversals != null` (SVX-C3), SVV-C5's `stillLifted`
  (SVX-C4), SVV-C6's `showsItself` (no test names it), SVV-B1's `anyMeasured` bound (SVX-C5), and -
  carried unchanged from round one - SEV-B3's `- allowance` at `Layout` 6324 (SVV-C3) and SEV-C1's
  `hasAWayThrough` widening (SVV-C4). **Six repairs in two rounds that the suite cannot tell from the
  code they replaced.**

**On SVV-C3 and SVV-C4 specifically, which the brief asked about:** still unpinned. No test file under
`test/core/` or `test/regression/` was added or modified for either - `testAStationsSizeIsAnAllowance`
and `testAMayTurnStationIsNotATerminus` are untouched since round one, and round one's mutations
(delete `- allowance`; restore `if (copy == square) continue;`) still survive every claim in them.
Whether that is acceptable differs between the two. SVV-C4 is a trap removal that is a no-op at both
callers, and leaving it unpinned costs nothing but a note - which round one wrote. SVV-C3 is not: it is
live arithmetic on the guard that stands between two trains, it is on the PERMITTING side (SEV-B3), and
the fixture that would pin it is described in SEV-B3 in enough detail to build. Shipping it unpinned is
a decision, and it should be recorded as one rather than inherited.

---

## What this pass did NOT cover

- **Anything run.** No test, no build, by instruction; a battery was running throughout. Every mutation
  above was performed on paper. SVX-B1's arithmetic in particular - the walk judging an edge whose only
  measurement is the allowance - should be confirmed by measuring a berth in a sandbox and asking
  `placesCoveredByStandingTrains()` before it is acted on.
- **The Java Preferences.** Not read and not written, by instruction. Whether the operator's
  `LOC_MAPPING_PAGES_PREF` is still at fifty after the killed run (SVX-C7) is therefore unknown here.
- **`cs2_sample_layout/`.** Not read this round at all. The figures round one measured from
  `configuration-Main.json` and `setup.json` were taken as given.
- **Everything round one covered and that did not change.** `HomeStaging`, `GraphReducer`,
  `AutonomyBuilder`, the eight bundles beyond the apostrophe and non-ASCII counts, the MT-376/FR-074
  work, `standOnTheCopyItDidNotTurnOn`, and the SVV-D1 to SVV-D12 checks were not re-derived. Where a
  round-two edit lands on one of them it was re-read (the berth rule, the walk, `turnsOnArrival`, the
  paste branch, `bulkTools`, `homeEveryPlacedTrain`).
- **SVV-C2** (`ManualReversalPrompt.forJourney` and the decision half disagreeing at a may-turn dead
  end). The file is untouched since round one, so the finding stands exactly as written; it was not
  re-derived.
- **Whether any class is green, and whether any skips everything.** Unknowable here.
  `testFiftyLocomotiveMappingPages` now has two skip doors instead of one (SVX-C6) and
  `testABerthAndAPlatformJudgeAnOverhangDifferently` has three, and nothing in this pass can say whether
  any of them is open.
- **The four battery failures as failures.** What was checked is that each repair addresses the stated
  cause and does not introduce a new one. Whether the battery is now green was not and cannot be
  established from here.
- **Concurrency.** `getLocomotiveLocation` on the event thread (SVX-D4) and the daemon thread calling
  `addLocMappingPage` off the EDT were read and left where round one left them.
- **SEV-B2's planner-side remedy** and **SEV-C4**, both still untouched by either fix round.
