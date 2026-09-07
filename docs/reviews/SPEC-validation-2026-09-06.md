# Validation against Adam's rulings of 2026-09-05/06

**Status:** open

**Prefix for citing these findings elsewhere:** `SPEC` (confirmed unused - `grep -rl "SPEC-" docs/`
returns nothing).

**Reviewed:** branch `autonomy-diagram-r0`, HEAD `7d4661d0`, **plus the uncommitted working tree**,
on 2026-09-06. The working tree matters: the Path Type radio that ruling 7 is about exists only
there (`src/org/traincontrol/gui/AutonomyEditorPanel.java`, the eight `messages*.properties`, and
`test/regression/testEditorSurfaceRules.java` are all modified and uncommitted). Every line number
below is a working-tree line number.

In scope: `7d4661d0`, `c9098a9b`, `df584d0b`, `d22df6c9`, `eeee62c7`, `48eca362`, `f22f29d5`,
`36734d33`, `fb2c0df6`, `a563f7e5`, `8fca0215`, and the uncommitted Path Type work.

**Method - and its limit.** This pass is **reading only**: no build, no test run, no probe. That is
the instruction under which it was run, and it is the single biggest caveat on everything below.
This repository's own record says nine of nine defects in the last comparable round needed
*execution* to find, and that reading passes had cleared them. So: the reasoning below traces call
graphs and predicates and says at each point what it could not measure. Two findings (`SPEC-A2`,
`SPEC-B5`) turn on whether a square on Adam's railway ever builds to more than one Point, which
`test/core/testAPastedTrainKeepsItsDirection.java:184` says it does not for *named* squares and says
nothing about the rest. Those need running before their severity is settled.

`cs2_sample_layout/` was read and never written. No file except this one was modified.

---

## Verdict

**Grade: C.**

Of the nine rulings, five are implemented correctly and at both doors, two are implemented at one
door of two, and two are not implemented at all - including ruling 8, the one Adam reported four
times.

The shape is the one this repository keeps finding in itself, and it is worth naming precisely
because it recurs three separate times below: **a rule was named, and the call site was left asking
a different question.** `shouldReverseAt` is the rule; `executePathInternal:5804` decides where to
stop with a *wider* predicate than the rule uses, and stops autonomy's trains at squares the rule
would never turn them at (`SPEC-A2`). `ManualReversalPrompt.forJourney:80` decides what to *ask*
with a *narrower* terminus test than the rule's, and asks a question the rule then throws away
(`SPEC-B1`). `rememberPlacement:6050` writes a facing derived from a Point that `speakerAt` chose
arbitrarily, while its comment asserts the layout "already chose" it deliberately (`SPEC-A1`).

The paste ruling (8) is the one to look at first. The fix that shipped for it records **the
destination square's own facing**, not the train's heading - and the test written with it says so in
its own javadoc while asserting something that cannot fail.

---

## Findings

| | Finding | Severity | Disposition | Confidence |
|---|---|---|---|---|
| SPEC-A1 | A pasted train's heading is never read; the paste records the destination copy's facing | A | Fixed | High on the mechanism, high on the spec gap |
| SPEC-A2 | Autonomy and Return Home now stop at every plain copy of a may-reverse square | A | Fixed | High on the logic, **unmeasured** on reachability |
| SPEC-B1 | A journey to a terminus asks the reversal question and then discards the answer | B | Fixed | High |
| SPEC-B2 | The Path Type tooltip puts Return Home on the Auto side, which is the opposite of ruling 7 | B | Fixed | High |
| SPEC-B3 | Return Home refuses an inactive START; manual allows it | B | Wont-fix | High |
| SPEC-B4 | The diagram's "is facing" radio writes the setup only - `DIR-B3`'s sibling | B | Fixed | High |
| SPEC-B5 | The paste guard and the menu that offers the same action ask different predicates | B | Fixed | High on the code, unmeasured on reachability |
| SPEC-C1 | The prompt still says the train "has reached" the square, after the move to departure | C | Fixed | High |
| SPEC-C2 | `tooltip.Active` still scopes the inactive rule to "autonomous operation" | C | Fixed | High |
| SPEC-C3 | `LayoutRightclickAutonomyMenu.reversalPolicy()` is dead, and is the old ask-on-arrival policy | C | Fixed | High |
| SPEC-C4 | One door asks the reversal question before the power check, the other after | C | Fixed | High |
| SPEC-C5 | The Auto tier note lists one of the three reasons autonomy will not choose a station | C | Fixed | High |
| SPEC-D1 | Rulings 2, 3, 4, 5, 6 and 9: checked, correct | D | Verified | See each |

---

### SPEC-A1 - The paste records which way the SQUARE points, not which way the TRAIN points

**Severity:** A. **Disposition:** Open. **Confidence:** High.

Ruling 8: *"pasted locomotives pasted on the succeeding/preceding station to a given station on the
same line face into the station and away from that station, respectively"*, and `c9098a9b`'s own
commit message draws the correct consequence from it: *"which are the same absolute heading, so the
facing must be PRESERVED, not dropped."* Plus *"for terminuses, they must reverse on paste."*

Neither half is implemented.

`src/org/traincontrol/gui/TrainControlUI.java:6048-6053`:

```java
if (point.getCurrentLocomotive() != null)
{
    org.traincontrol.automationui.TilePorts.Side side =
        session.facingsFor(tile).get(point.getName());

    if (side != null) session.setFacing(tile, side);
}
```

`facingsFor(tile)` is `StationIndex.facingsAt` (`StationIndex.java:282`) - the map from each **built
copy of the destination square** to the side a train standing on that copy faces. So the value
written is a property of *where the train landed*, computed from the destination square's geometry.
**The train's own heading is never read.** Nothing in this method, or in the paste handler above it
(`:5952-6002`), looks at the square the train came from, at `session.getFacing(sourceTile)`, or at
the copy it was standing on.

The comment at `:6040-6044` defends this:

> Taken from the copy the LAYOUT put it on, not from where it came from. The layout has already
> chosen a Point by the time this runs, and that Point's side is the heading the train actually has

**The premise is false.** The Point was chosen at `:5926` / `:5938` by
`getAutonomyPointForTile(aimed)`, which is `StationIndex.speakerAt` (`TrainControlUI.java:5048-5057`),
and that method's own javadoc says what it does when the square is empty - which it is, since the
train has not been put down yet: *"with none, any copy will do, because they are all the same square
and carry the same settings."* It is the first copy, arbitrarily. Nothing about the train entered
that decision. On a one-copy square the choice is forced; on a multi-copy square it is copy 0 -
which is *precisely* the "falls through to COPY 0 ... which is not a direction anybody chose" failure
this same comment describes two paragraphs earlier, now performed deliberately.

`for terminuses, they must reverse on paste` is likewise not implemented: nothing tests
`isTerminus`. The comment at `:6043-6044` argues the terminus case falls out for free ("that side is
the only one there is"), which is an argument about a one-copy square, not a rule.

**What the test proves, and what it does not.** `test/core/testAPastedTrainKeepsItsDirection.java`
does not call `rememberPlacement`; `putDown` at `:263-270` is a hand-written model of it. The loop at
`:116-160` then reads `Side side = copies.get(station.getName())`, passes that same value to
`putDown`, and asserts `session.getFacing(square)` equals it. **The assertion cannot fail** - it
asserts the fixture value against itself, and it never establishes a prior heading at all, so it
cannot detect a failure to preserve one. The file's own javadoc at `:171-181` states the defect
plainly: *"on a one-copy square that value is forced to that copy's side regardless of which way the
train was actually pointing when it was put down."* That sentence is the finding; what is missing is
the conclusion that this is the ruling not being met.

**What the correct behaviour is, and where it belongs.**

1. In the paste handler, **before** `moveLocomotive` at `:5985`, capture the heading the train
   actually has: the square it is leaving (`session.getStationIndex().squareOf(...)` of its current
   Point) and the Side that Point stands for - `facingsFor(sourceSquare).get(sourcePoint.getName())`,
   falling back to `session.getFacing(sourceSquare)`.
2. Choose the landing copy from that heading, not from `speakerAt`: the copy of the destination
   square whose facing equals the carried Side. `AutonomySession.moveOntoFacingCopy`
   (`AutonomySession.java:1410-1451`) already does exactly this and is private to `flipFacing`;
   promoting it is the smaller change than writing a second copy-chooser.
3. Where the destination is a terminus or a must-turn square, record the **turned** heading, from the
   rule rather than from the accident that the square has one copy.
4. Where the destination cannot hold the carried heading at all - `facingChoices(target)` does not
   contain it - today's value is the right fallback, but it should be reached as a fallback and said
   so, not as the primary path.

`rememberPlacement` needs the origin passed in; it cannot recover it, because by the time it runs the
train has already been moved.

---

### SPEC-A2 - The run stops at squares the rule would never turn a train at, and autonomy is one of the callers

**Severity:** A (see the reachability note). **Disposition:** Open. **Confidence:** High on the
logic; the reachability was **not measured** and needs a run.

Ruling 5: *"No unprompted reversals in manual mode unless going to a true terminus"*, and *no
prompting in Return Home or autonomy*. `Layout.shouldReverseAt:5161` honours that exactly - for a
null policy and for `ALWAYS_REVERSE` (which is what autonomy at `:3701` and the timetable/Return Home
at `:4800` both pass) it returns `current.isReversing()`, unchanged from before.

The **stop** does not follow the same rule. `src/org/traincontrol/automation/Layout.java:5804-5806`:

```java
if (isCurrentLayout() && (mayReverseAt(current)
    || (reversals != null && reversals != ALWAYS_REVERSE
        && reversals.asksAbout(current))))
{
    loc.setSpeed(0).waitForSpeedBelow(1);

    if (shouldReverseAt(current, path.get(path.size() - 1).getEnd(), loc, reversals))
    ...
    loc.setSpeed(speed).waitForSpeedAtOrAbove(speed);
}
```

For `ALWAYS_REVERSE` the second disjunct is switched off by construction, so the condition reduces to
`mayReverseAt(current)` alone. `mayReverseAt` (`:5194-5211`) is true for **any copy sharing a block
with a reversing copy** - and a block is the tile (`AutonomyBuilder.java:844`, `json.put("block",
point.getTile().toString())` whenever `nodes.size() > 1`). So on a may-reverse square that the build
split into a plain copy and a turning one, the *plain* copy answers `mayReverseAt == true` and
`isReversing() == false`.

Consequence: **during autonomy and during Return Home a train now stops dead at every plain copy of
every split may-reverse square it passes, waits for the speed to fall below 1, does not turn, and
accelerates again.** Before `8fca0215` the condition was `current.isReversing() && isCurrentLayout()`
(confirmed by `git diff 1ffe6c50 HEAD`), so none of those stops happened. `pickPath` does not prevent
it: `reversesAlongTheWay` (`:3567-3578`) only rejects paths whose edge ends are `isReversing()`, so
plain copies of split squares are exactly what autonomy is *supposed* to route through - that is what
the split is for.

The comment written above the condition asserts the opposite of what it does:

> The same question the rule asks, so the train is stopped exactly where somebody may be asked
> something - and nowhere else (DIR-A1).

It is not the same question. It is wider than the rule for both `ALWAYS_REVERSE` callers, and wider
again for a manual journey whose destination is a terminus (where `:5165` also returns
`current.isReversing()`).

**Reachability, honestly.** `test/core/testAPastedTrainKeepsItsDirection.java:184` asserts that every
*named* square on the sample layout builds to exactly one copy, "even after being marked
may-reverse". If that holds for unnamed squares too, `mayReverseAt` can only be true where
`isReversing()` already is, and this defect is unreachable today - a trap rather than a fault. That
is not established: the test counts named squares only, and `nodes.size() > 1` is not restricted to
stations. **This needs one run to settle**, and it decides whether the severity is A or C.

**The fix** is to make the stop ask the rule's own question, rather than a wider one - one predicate,
used by both:

```java
private boolean stopsToDecideAt(Point current, Point destination, ReversalPolicy reversals)
{
    if (reversals == null || reversals == ALWAYS_REVERSE) return current.isReversing();
    if (destination != null && destination.isTerminus()) return current.isReversing();
    return mayReverseAt(current) || reversals.asksAbout(current);
}
```

which is `shouldReverseAt` with its final line removed. Extracting it puts the two in step by
construction, which is what `shouldReverseAt`'s own javadoc says naming the rule was for - *"naming a
rule moves the defect to the call"*, and this is that defect, at that call.

---

### SPEC-B1 - A journey to a terminus asks the question and throws the answer away

**Severity:** B. **Disposition:** Open. **Confidence:** High.

`Layout.shouldReverseAt:5165`:

```java
if (destination != null && destination.isTerminus()) return current.isReversing();
```

Correct, and it is ruling 5 written down: on a journey that ends at a terminus the turn on the way is
how the train gets there, so nothing is asked and the policy is never consulted.

`ManualReversalPrompt.forJourney` does not know that. `src/org/traincontrol/gui/ManualReversalPrompt.java:70-94`
skips a point that is **itself** a terminus (`:80`), and asks about the first point where
`asking.asksAbout(...)` is true. It never looks at where the journey **ends**.

So for a hand-driven send `A -> R -> T` where `R` is a may-reverse point and `T` a terminus:

- `forJourney` finds `R`, puts the modal dialog up, and the operator answers "keep direction" (Yes,
  the default).
- At runtime, `shouldReverseAt(R, T, ...)` sees `destination.isTerminus()` and returns
  `R.isReversing()` - **without consulting the policy at all**.
- The train turns, or does not, entirely independently of what the operator was just asked.

The railway behaviour is right; the dialog is a lie. It is worth fixing as the dialog, not as the
rule: `forJourney` should return `KEEP_DIRECTION` without asking anything whenever
`path.get(path.size() - 1).getEnd().isTerminus()`. That is one line, and it puts the two terminus
tests - the one that decides what to ask and the one that decides what to do - on the same question.

A smaller sibling in the same method, worth a note rather than a finding: the dialog names `first`,
the first asked-about square, while the answer governs **every** asked-about square on the path
(`:99-102`). The javadoc at `:51-54` says this is deliberate and gives a good reason. The wording of
`autolayout.ui.confirmManualReversal` names one square, so a two-reversal path is asked about under a
caption that describes half of it.

---

### SPEC-B2 - The Path Type tooltip says Return Home follows the same rules as AUTO

**Severity:** B. **Disposition:** Open. **Confidence:** High.

This is the control ruling 7 is about, and it is in the working tree, uncommitted:
`src/org/traincontrol/gui/AutonomyEditorPanel.java:295-299, 505-524, 566-568, 6028-6043` and
`src/org/traincontrol/resources/messages.properties:1944-1948`.

Ruling 7 as given: *"Return home follows the same rules as manual"*, and the tooltip should say so.

`messages.properties:1948` says the opposite:

> Which kind of run the check answers for. **Auto covers autonomy and Return Home, which follow the
> same rules;** Manual covers a train you send by hand. Both are stopped by the same inactive
> squares, barred arrivals and turning points - they differ only in where a train may be SENT,
> because autonomy will not choose a station it is told to leave alone.

And the code follows the tooltip. `AutonomyEditorPanel.java:6030` adds the "autonomy will never
choose this" note when **Auto** is selected, keyed on `session.stationsAutonomyWillNotChoose()`,
which is `isStation(tile) && !isAutoDestination(tile)` (`AutonomySession.java:3316-3328`).

**On the one axis this radio implements, Return Home behaves like Manual, not like Auto.** That is
Adam's ruling of 2026-09-01, and it is quoted verbatim thirty lines away in this same file, at
`AutonomyEditorPanel.java:1235-1250`:

> "Can Be Chosen in Full Autonomy - does not apply to returning home, of course. these should be
> allowed."
>
> ... Homing never consults this flag - `HomeStaging` does not mention it - which is what makes a
> parking berth work: somewhere the operator sends trains and autonomy leaves alone.

Confirmed by reading: `isAutoDestination` appears nowhere in `HomeStaging.java`.

So the concrete harm: an operator checking why Return Home will not fill a parking berth selects
Auto - the tooltip has just told them Return Home is Auto - and is shown *"autonomy will never choose
{0} ... Switch Path Type to Manual to check it as a hand-driven run."* Every clause of that is false
about Return Home, and it is precisely the fourteen-parking-berths state the comment at `:1240-1247`
records Adam having had to unpick by hand once already.

The fix is in the tooltip and in the tier test together: Return Home belongs on the Manual side of
this radio (or, better, the tooltip names the three tiers and says which two agree on this axis).
Note that the ruling is only true *on this axis* - see `SPEC-B3` for the axis on which Return Home
does not follow manual, which is a second reason the tooltip's flat claim is unsafe.

---

### SPEC-B3 - Return Home refuses an inactive START; manual allows it

**Severity:** B. **Disposition:** Open. **Confidence:** High.

Ruling 6: *"In manual mode, inactive endpoints and intermediates should be refused as well. Just not
inactive start points."* Ruling 7: *"Return home follows the same rules as manual."*

Manual is correct. `Layout.isPathClear` refuses an inactive **destination** unfenced
(`Layout.java:2335-2344`, added by `36734d33`) and an inactive **intermediate** unfenced
(`:2297-2306`), and leaves the start alone. The affordance agrees with the guard:
`isOfferableToOperator` (`:4141-4146`) drops an inactive square from the right-click list, and
`getPossiblePaths` (`:4478-4529`) filters through `isPathClear`, so the Locomotive-commands list drops
it too. Both doors, same predicate.

Return Home does **not** follow that. Two places refuse an inactive start:

- `Layout.java:2243` - `if (this.isAutoRunning() && (!e.getStart().isActive() || !e.getEnd().isActive()))`.
  `isAutoRunning()` is `this.running` (`:1673-1676`), and `executeTimetableInternal` sets `running`,
  so this fires for the whole of a staging run and it refuses the first edge's **start**.
- `HomeStaging.java:928` - `if (!from.isActive()) return null;`, whose comment explains that it exists
  only to mirror `:2243` so a plan is not made that the run would then refuse.

The commit that unfenced the destination rule left `:2243` alone deliberately and said why
(`Layout.java:2331-2334`):

> THE START STAYS EXEMPT, which is the whole of the exception ... `:2243`'s stricter form - any edge
> with an inactive endpoint - keeps its `isAutoRunning` fence, because it refuses the start too.

That preserves the *old* behaviour rather than applying the new ruling to it. The result is that
closing a square around a train - which the same comment says is exactly what closing a square is for,
because the train is then driven out by hand - leaves Return Home unable to move that train, while a
hand dispatch can. Adam is likely to meet this the same way he met the parking berths: a train that
Return Home silently declines to plan for, reported as `unreachable` at `HomeStaging.java:443-445`.

If ruling 7 is meant literally, `:2243` should split its start out (keep the endpoint half fenced or
unfenced as decided, exempt `path.get(0).getStart()`), and `HomeStaging:928` should be dropped with
it - it exists only as a mirror. If Adam wants Return Home to keep refusing an inactive start, that
is a decision worth writing down, because two comments currently justify it as an accident of the old
fence rather than as a choice.

---

### SPEC-B4 - The diagram's "is facing" radio writes the setup only - `DIR-B3`'s sibling was not swept

**Severity:** B. **Disposition:** Open. **Confidence:** High.

Ruling 9: *"flipFacing should also update the running layout."* Implemented, correctly:
`AutonomySession.flipFacing:1498-1544` calls `moveOntoFacingCopy(running, locomotive, tile, now)` at
`:1538`, and the javadoc at `:1480-1492` gives the reason - two records of one fact with no arbiter,
and `captureFromLayout` writing the layout's answer back over the setup at the next editor open.

That reason applies word for word to a sibling that was not changed. There are three writers of
`setFacing` in `src/`:

| writer | moves the running layout? |
|---|---|
| `AutonomySession.flipFacing:1536` | yes, `:1538` |
| `LayoutRightclickAutonomyMenu.placeFacing:980` | yes - `moveLocomotive(locName, pointName, false)` at `:966`, onto the named copy |
| `AutonomyEditorPanel.buildFacingMenu:2912` | **no** |

`AutonomyEditorPanel:2906-2913` is the "{loc} is facing >" radio group, and its action is
`() -> session.setFacing(target, facing)` and nothing else. It is not an editor-only surface: it is
served to the track diagram's right-click menu through `TrainControlUI.buildAutonomyFacingMenu:4224-4231`
(menu-only mode, `:4293`) and added by `LayoutRightclickAutonomyMenu:694-699` - **beside** the
placement items that do move the layout. So on one popup, "Place {loc}" keeps the two records in
step and "{loc} is facing N" does not.

The consequence is the one `flipFacing`'s own javadoc lists: `getPossiblePaths`, the right-click
destination list and `explainDestinations` go on answering for the old facing until something rebuilds
the layout, and `captureFromLayout` then writes the old copy's side back over the operator's choice at
the next editor open.

`test/regression/testEditorSurfaceRules.java:263-276` enumerates the writers of `setFacing` and was
just extended (uncommitted) for `TrainControlUI`. It asks whether each writer **redraws**. It does not
ask whether each writer **moves the locomotive**, which is the property `DIR-B3` added. A guard knows
only what it lists.

---

### SPEC-B5 - The paste guard and the menu offering the same action ask different questions

**Severity:** B. **Disposition:** Open. **Confidence:** High on the code; reachability unmeasured.

`TrainControlUI.java:5961-5983` refuses a paste onto a square with nowhere to go, and its comment
(`:5971-5974`) claims parity with the menu:

> Asked of the POINT rather than of the square's copies, because by here the copy is already chosen:
> the menu enumerates copies precisely to pick one, and its item goes grey only when every copy has
> nowhere to go at all. For a copy already in hand that is the same question, asked directly.

It is not the same question, for the same reason as `SPEC-A1`: the copy "already in hand" was chosen
by `speakerAt`, not by the menu's logic. The menu's logic is
`LayoutRightclickAutonomyMenu.placeableCopies:876-929`, which walks **every** copy and ranks them:
copies that can reach a destination (`canReachAnyDestination`) and are themselves destinations first,
then shut ones, then stranded ones - and its own comment records why the weaker test was not enough:

> "Has an outgoing edge" was the old test and it is not the same question. A copy of a split square
> can have somewhere to go and nowhere to be SENT ... and this list is drawn at RANDOM, so a train was
> put on a dead copy about half the time and then never moved.

The paste applies exactly that old test - `getNeighbors(point).isEmpty()` - to one arbitrarily chosen
copy, and does not try another. On a square with two copies where copy 0 is the dead one, the paste is
refused with `warnNoWayOutOfPoint` for a square the menu would place on happily; where copy 0 has edges
but reaches no destination, the paste succeeds and leaves the "nothing moves" train the menu exists to
avoid. Fixing `SPEC-A1` fixes this too, because choosing the copy from the train's heading replaces
`speakerAt` at this door.

Same reachability caveat as `SPEC-A2`: this needs a square that splits.

---

### SPEC-C1 - The prompt still says the train "has reached" the square

**Severity:** C. **Disposition:** Open. **Confidence:** High.

Ruling 3: *"Make it be on departure itself, that way there is no dispatch prior to user input."*
Implemented - `df584d0b` moved the question out of the run and into both doors, before the dispatch
thread starts (`LayoutRightclickAutonomyMenu.java:1051-1053`, `AutoLocomotiveStatus.java:1043-1046`).

The words did not move with it. `messages.properties:306`:

> `{0} has reached {1}, where trains may turn round.\n\nKeep its current direction?`

The train has not reached anything; it is standing where the operator left it and has not been
dispatched. All eight bundles say the same (`messages_da/de/es/fr/it/nl/pl:306` or `:308`). A
correct wording is about the journey - "{0} will pass {1}, where trains may turn round. Keep its
current direction?" - and it should also stop implying a single square, since the answer governs
every such square on the path (see `SPEC-B1`'s note).

`ManualReversalPrompt.java:195-197` has the same drift in its javadoc - *"The train is stopped before
this is called"* - which was made true by `DIR-A1` for the in-run path and is now false again for the
`forJourney` path, where the train has not started.

Per `traincontrol-properties-ascii-only`: any retranslation needs `\uXXXX` escapes, and per
`open-w-truncates-before-encoding`, write those files with a tool that will not truncate on a failed
encode.

---

### SPEC-C2 - `tooltip.Active` still scopes the inactive rule to autonomy

**Severity:** C. **Disposition:** Open. **Confidence:** High.

Ruling 6: *"Inactive really means nothing can pass."* The code now says so at every door
(`Layout.java:2297-2306`, `:2335-2344`, `isOfferableToOperator:4143`). The control that sets it does
not.

`AutonomyEditorPanel.java:1218-1221` is the "No - Nothing Can Pass" radio, and its tooltip key is
`autolayout.ui.tooltip.Active`, which reads (`messages.properties:422`):

> Inactive points will not be traversed in autonomous operation.

That was true before `36734d33`/`f22f29d5` and is now the narrower of the two claims: the square is
refused to a hand dispatch and to Return Home as well, as a destination and as an intermediate, and
is exempt only as a start. The radio's own label ("Nothing Can Pass") is already right; the tooltip
under it contradicts it. Eight bundles.

The one sentence worth adding is the exception, because it is the part a reader cannot guess: a train
already standing on a closed square can still be driven off it by hand.

---

### SPEC-C3 - The old ask-on-arrival policy is still there, with no caller

**Severity:** C. **Disposition:** Open. **Confidence:** High.

`LayoutRightclickAutonomyMenu.java:1015-1023`:

```java
private org.traincontrol.automation.Layout.ReversalPolicy reversalPolicy()
{
    java.awt.Window window = javax.swing.SwingUtilities.getWindowAncestor(this);
    return org.traincontrol.gui.ManualReversalPrompt.forOperator(session,
        window != null ? window : this);
}
```

`grep -rn "reversalPolicy" src/` returns this declaration and nothing else. `df584d0b` replaced its
call site with `forJourney`, and left it.

It is worth removing rather than leaving, because of what it is: a `ReversalPolicy` whose
`shouldReverse` puts a modal dialog up *from inside the run* - the `DIR-A1` behaviour, complete with a
javadoc (`:1004-1014`) that still reads as the recommended way to build one ("Asks whether a train
reaching a may-reverse point should be turned round", "Shared with the Locomotive commands tab").
The next person adding a dispatch door to this menu will find it and use it, and Adam's ruling 3 will
be undone at the new door only. `ManualReversalPrompt.forOperator` itself must stay - `forJourney`
delegates its `asksAbout` to it - but it should say that it is no longer for handing to `executePath`
on its own.

---

### SPEC-C4 - One door asks the question before checking the power, the other after

**Severity:** C. **Disposition:** Open. **Confidence:** High.

Both doors now prompt on departure, which is ruling 3. They disagree about the order of the two
things that can stop a dispatch:

- `AutoLocomotiveStatus.java:1010-1014` checks `getPowerState()` and returns, **then** asks
  (`:1043-1046`).
- `LayoutRightclickAutonomyMenu.java:1051-1062` asks **first**, on the event thread, and only then
  starts the thread that checks the power and shows `powerOnToStart`.

So from the track diagram with the power off, the operator answers a question about a reversal and is
then told the train cannot go anywhere. Ruling 3's own reason - *"there is no dispatch prior to user
input"* - is about not dispatching before asking; asking before finding out there will be no dispatch
is the mirror of it. Moving the power check above the `forJourney` call at `:1051` is the whole fix,
and it also makes the two doors read the same way.

---

### SPEC-C5 - The Auto tier note answers with one of three reasons, from a second list

**Severity:** C. **Disposition:** Open. **Confidence:** High.

`AutonomyEditorPanel.java:6030` decides "autonomy will never choose this" from
`session.stationsAutonomyWillNotChoose()`, which is `isStation(tile) && !isAutoDestination(tile)`
(`AutonomySession.java:3316-3328`).

`Layout.barredFromAutonomy:4164-4192` is the runtime's actual list, and it has four clauses:
inactive, `isReversing()`, `!isAutoDestination()`, and a terminus a non-reversible locomotive could
not leave. Its javadoc (`:4148-4163`) is explicit about what a second copy costs:

> Two copies of this list would be two answers to "can autonomy pick this station", and the popup
> that groups by it would disagree with the reason printed beside each line - which is the exact shape
> of defect this codebase keeps finding.

and `isChoosableByAutonomy:4100-4103` exists as the one public way to ask it, with its javadoc naming
the diagram-caption caller as the third that would otherwise have grown its own. The new tier note is
that caller. On Auto it is silent about a route ending at a reversing point - which autonomy will
never choose and a person may send a train to, so it is the *most* relevant thing the Auto tier could
say.

The tooltip at `messages.properties:1948` compounds it by asserting the opposite: *"Both are stopped
by the same inactive squares, barred arrivals and turning points."* A turning point is a legal manual
destination (`isOfferableToOperator:4141-4146` permits it; `barredFromAutonomy:4168` refuses it), so
turning points are a delta and not a shared rule.

The check has no locomotive, so the fourth clause (terminus vs non-reversible) genuinely does not
apply here and its absence is correct.

---

### SPEC-D1 - Checked and correct

**Severity:** D. **Disposition:** Open. **Confidence:** as noted.

**Ruling 2 - "yes/no keep direction, with no meaning change, and yes being default".** Correct.
`ManualReversalPrompt.java:227-242` uses `showOptionDialog` with `TrainControlUI.YES_NO_OPTS` and
`YES_NO_OPTS[0]` as the initial value, and returns `chose != 0` - so index 0 (Yes) means *do not*
reverse. `messages.properties:306-307` ask "Keep its current direction?" / "Keep direction?" and all
seven translations say the same. The `catch` at `:248-253` returns `false`, which is also "keep".
Meaning, default and failure mode all agree. (See `SPEC-C1` for the first half of the sentence.)

**Ruling 3 - asked on departure, at both doors.** Correct, other than the ordering point in
`SPEC-C4`. Both call `ManualReversalPrompt.forJourney` on the event thread before starting the
dispatch thread; `ask` at `:245` runs the dialog inline when already on the EDT. `executePath` has
exactly four callers (`Layout.java:3701`, `:4800`, `AutoLocomotiveStatus.java:1050`,
`LayoutRightclickAutonomyMenu.java:1065`) - so there is no third hand-driven door that was missed.

**Ruling 4 - one-way direction flow.** Correct in both halves. No writer of the facing emits a
locomotive command: `setFacing` (`AutonomySession.java:4763-4766`) writes a JSON property, and
`moveOntoFacingCopy`/`placeFacing` move Points, never call `switchDirection`. And
`TrainControlUI.followDirectionChanges:9875-9953` follows the decoder's echo onto the graph, with the
guards correctly placed **before** the `lastSeenDirection` write (`:9898-9909`) so a change made while
something is running is deferred rather than swallowed - which is the fix `:9885-9897` describes, and
it reads correctly.

One thing worth knowing rather than fixing: because `isRunning()` (`Layout.java:1663-1667`) is true
for the whole of a *hand* dispatch too, a reversal made during a manual send is not followed onto the
graph at the moment it happens. It waits for the next locomotive echo after the run ends. Whether one
reliably arrives was not measured and would need a run; if it does not, Adam's *"when the route
finishes, the reversal isn't painted"* would come back for the manual case only.

**Ruling 5 - no prompting in Return Home or autonomy.** Correct as to *prompting*.
`shouldReverseAt:5161` treats `ALWAYS_REVERSE` as "nobody to ask", and the comment there records that
leaving it out was a defect this rule introduced and was caught. Both non-UI callers take the
four-argument overload and land on it. The *stopping* is `SPEC-A2`.

**Ruling 6 - inactive endpoints and intermediates refused in manual, start exempt.** Correct for
manual, at the guard and at both affordances. See `SPEC-B3` for Return Home and `SPEC-C2` for the
tooltip.

**Ruling 9 - flipFacing updates the running layout.** Correct at `AutonomySession.java:1538`,
including the two "carry on rather than give up" corrections at `:1512-1532`. See `SPEC-B4` for the
sibling that was not swept.

**Not a finding:** `AutoLocomotiveStatus.java:1045` guards `this.parent == null` before
`getAutonomySession()`, but `parent` is dereferenced unconditionally in the constructor at `:51`, so
it cannot be null. Harmless, and the guard is not wrong.

---

## What a follow-up pass should run first

Three questions decide two severities and would take one run each:

1. Does **any** square on the sample layout build to more than one Point?
   `testEverySquareOnThisLayoutBuildsToOneCopy` answers it for named squares only. If any square
   splits, `SPEC-A2` is a live A and `SPEC-B5` is live.
2. Drive a train through `executePath` in simulate mode with `ALWAYS_REVERSE` over a path containing
   a plain copy of a split may-reverse square, and record the speed at that point. That is `SPEC-A2`
   measured rather than reasoned.
3. Paste a train between two squares whose built copies face opposite ways and read
   `session.getFacing(target)` against the heading it had. That is `SPEC-A1` measured, and it is the
   test `testAPastedTrainKeepsItsDirection` does not currently contain.
