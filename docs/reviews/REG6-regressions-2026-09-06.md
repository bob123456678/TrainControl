# Regression review of the last three days: what the reversal work broke behind itself

**Status:** open

**Prefix for citing these findings elsewhere:** `REG6` (confirmed unused - `grep -rl "REG6-" docs/`
returns nothing).

**Reviewed:** branch `autonomy-diagram-r0`, 2026-09-06. Range `b4136747..ec4d5ec7` (2026-09-03 to
2026-09-06, 87 commits), reading `git log -p -- src/`. The pass is weighted to the 2026-09-06 block
`2790d8bb..ec4d5ec7`, which is where the reversal policy, `ManualReversalPrompt`, the two manual
dispatch doors, `followDirectionChanges`/`rememberPlacement`, `AutonomySession.flipFacing` and
`GraphReducer`'s closed-tile handling all landed. `cs2_sample_layout/` was read and never written.
HEAD moved twice during the pass (`ca0265f4`, `ec4d5ec7`); both were re-read and are in scope.

**Method.** Reading and tracing only - no tests were run, nothing was built, nothing was edited
except this file. Every finding below is marked **CONFIRMED** (the code path was traced end to end
and the claim follows from the source as written) or **PLAUSIBLE** (it smells wrong and the last step
could not be established without running it).

**Relationship to `2026-09-06-direction-and-consistency.md` (`DIR`).** That review found the defects;
this one reviews the fixes. `DIR-A1`, `A2`, `A3`, `B1`, `B3`, `B4`, `C3` and `C4` are all closed in
this range and none is re-raised. Everything below is new damage or damage the fix walked past.

---

## Verdict

**The reversal policy moved the decision to departure time and then kept consulting a predicate that
belongs to arrival time.** Three of the four A/B findings are one mechanism: `asksAbout` is used for
two different jobs - "is this a square the operator should be asked about" and "is this a square the
train must be stopped at" - and once the answer is fixed at departure those two stop being the same
question. The fourth is `mayReverseAt`, a new square-wide predicate that replaced a Point-wide one at
a call site that only ever wanted the Point.

**The most serious is `REG6-A1`.** A hand-driven send is now offered "keep direction?" at squares the
CHOSEN PATH turns the train round at, including compulsory-turn squares, with the
nothing-happens answer as the keyboard default. Taking that default does not mean "carry straight
on": the graph's next edge from a turning copy leaves by the side the train arrived from, so a train
that is not turned drives forward off its own reserved path. `ec4d5ec7`'s own commit message, written
three hours after the change, still asserts the opposite - *"a must-turn square turns both"* - which
is a good measure of how invisible this is from inside the change.

**`REG6-A2` is the same shape one layer up.** `ca0265f4` made a reversal made during a run get
followed once the run ends. It is followed onto the square the SETUP still records the train on -
which, between the end of a run and the next capture, is the square the train DEPARTED from. The
comment at `TrainControlUI:4736` states that staleness as an established fact and was not consulted.

The `MarklinRoute` MT-247 work (`46a9823c`) and the `AddLocomotive` name guard (`a0512166`) are
clean; the `GraphReducer` closed-tile sweep (`d4a3d498`) reaches every production caller. Details in
`REG6-D1`.

---

## A - high

### REG6-A1 - a manual send is offered "keep direction" at squares the path turns the train at, and the default drives it off the path

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | CONFIRMED (traced through `AutonomyBuilder`, `ManualReversalPrompt` and `Layout.executePathInternal`); the operational consequence is reasoned from `Node.leavesBy`, not measured |
| **Where** | `src/org/traincontrol/gui/ManualReversalPrompt.java:168`, `:242`; `src/org/traincontrol/automation/Layout.java:5178`, `:5804`; `src/org/traincontrol/automationui/AutonomyBuilder.java:543`, `:977` |
| **From** | `a563f7e5`, `48eca362`, `d22df6c9`, `df584d0b` |

`ManualReversalPrompt.forOperator().asksAbout` answers in two clauses:

```java
if (at.isReversing()) return true;                                   // :168
...
return square != null && session.mayTurnTiles().contains(square);    // :179
```

The second clause is careful: `AutonomySession.mayTurnTiles()` (`:3296`) is
`reversibleTiles()` minus `mandatoryTurnTiles()`, so compulsory-turn squares are deliberately
excluded from it. The first clause puts them straight back. `AutonomyBuilder:543` emits only turning
copies for a `mustReverse` square - `if (!must && (onwards || !canTurn)) out.add(new Node(tile, side,
false));` - and `:977` puts `reversing` on every one of them. So every copy of a must-turn square
answers `at.isReversing() == true`, and every one of them is asked about in manual mode.

The same is true of the TURNING copy of a may-turn square, which is the copy `bfs` picks precisely
when the route needs the train turned there.

What the answer then does:

- `Layout.java:5178` - `if (!mayReverseAt(current) && !reversals.asksAbout(current)) return false;`
  then `return reversals.shouldReverse(loc, current);`. The operator's answer is authoritative at any
  such point.
- `ManualReversalPrompt:242` - `answer[0] = chose != 0;` with index 0 = Yes = *keep direction*, and
  index 0 is `TrainControlUI.YES_NO_OPTS[0]`, the dialog's default. Enter, Escape or a dismissed
  dialog all mean "do not turn". `ask()`'s `catch (Exception cannotAsk) { return false; }` means the
  same.

A turning copy's outgoing edges are exactly the ones that leave by the side the train arrived from
(`AutonomyBuilder.Node.leavesBy`: `if (reverse) return arrival == exitSide;`). Not turning there does
not mean "carry on through" - the plain copy that would carry on is a different Point, and on a
`mustReverse` square it does not exist at all. The locomotive is left running forward while the
remaining path expects it to travel back the way it came: the reserved edges are never occupied, the
sensors the run is waiting on never fire, and the train continues past the point into whatever is
beyond it.

Before this range, `executePathInternal` turned unconditionally at `current.isReversing()`, so this
state was unreachable.

The javadoc on `Layout.ReversalPolicy` (`:5060`) states the intended carve-out - *"A TRUE TERMINUS IS
NOT ASKED ABOUT, at either door. There the turn is not a choice: the train has run out of track"* -
and `shouldReverseAt` implements it for `isTerminus()` only. The other "not a choice" case,
`mustReverse`, is available to `forOperator` through the same session object it already holds
(`session.mandatoryTurnTiles()`) and is not consulted.

**The smaller reading.** If Adam's own railway has no `mustReverse` squares and no path the manual
menu offers routes through a turning copy, this is unreachable today and is a trap for the next
square he marks. That is worth establishing before sizing the fix, but it does not change the shape.

---

### REG6-A2 - the deferred post-run facing flip lands on the square the train left, not the one it is on

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | CONFIRMED that `flipFacing` walks the setup's placement map and that the setup is stale after a run; PLAUSIBLE that a qualifying echo always arrives before the next capture |
| **Where** | `src/org/traincontrol/gui/TrainControlUI.java:9875` (`followDirectionChanges`), `:4736` (the staleness note), `src/org/traincontrol/automationui/AutonomySession.java:1498` (`flipFacing`), `:1410` (`moveOntoFacingCopy`) |
| **From** | `fb2c0df6`, `ca0265f4` |

`ca0265f4` moved the guards above the recording so that a reversal made DURING a journey is no longer
swallowed. It now survives in `lastSeenDirection` and is acted on by the first echo that arrives
after `isRunning()` goes false.

`flipFacing` decides WHICH square to flip by walking `placedLocomotives()` - the setup's own record:

```java
for (Map.Entry<TileKey, String> placed : placedLocomotives().entrySet())   // :1502
```

`TrainControlUI:4736` states, as the reason `captureFromLayout` had to be moved ahead of the editor
constructor, that this record does not follow a run:

> *"A run moves a locomotive and where it ended up lives in `Point.currentLoc` and nowhere else.
> `captureFromLayout` is the only thing that folds it into the setup, and stopping autonomy is not
> one of its callers - so between the end of a run and the next load, exit or diagram edit, the setup
> on disk still says where the train STARTED."*

So the post-run flip writes a flipped facing onto the DEPARTURE square, which the train is no longer
on. `moveOntoFacingCopy` then finds no locomotive among that square's copies in the running layout,
sets `train = null` and returns at `:1441` without touching the layout - so `DIR-B3`'s "both records
in one method" property silently does not apply on this path, and only the wrong one is written.
`autonomySetupChanged()` runs and `autosetup.infoFacingFollowedDirection` is logged naming the
departure square, which reads as confirmation that the thing Adam asked for happened.

This is the exact complaint `ca0265f4` answers - *"when the route finishes, the reversal isn't
painted/visible"* - repainted onto the wrong tile.

Self-healing at the next `captureFromLayout` (editor open, exit, or diagram edit), which is why this
is A rather than a data-loss A: the wrong value is transient. It is still a wrong arrow on the
diagram, a wrong `facing` written to the store, and a log line asserting a correction that did not
happen.

The unverified step is whether a locomotive echo reliably arrives after `locomotiveThreads` reaches
zero without the operator touching anything. If it does not, the flip waits until the operator's next
throttle action, which makes the stale-square window wider rather than narrower.

---

## B - medium

### REG6-B1 - the prompt's answer is discarded whenever the journey ends at a terminus

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | CONFIRMED |
| **Where** | `src/org/traincontrol/gui/ManualReversalPrompt.java:80` and `:92`; `src/org/traincontrol/automation/Layout.java:5165` |
| **From** | `df584d0b` |

`forJourney` walks the path looking for the first square to ask about and skips only the squares that
are themselves a terminus:

```java
if (edge.getEnd().isTerminus()) continue;      // :80
if (asking.asksAbout(edge.getEnd())) { first = edge.getEnd(); break; }   // :82
```

`shouldReverseAt` exempts a whole JOURNEY whose destination is a terminus:

```java
if (destination != null && destination.isTerminus()) return current.isReversing();   // :5165
```

The two predicates disagree. A hand-driven send to a terminus that reaches it by turning at the
reversing point before it - Adam's MT-245 case, *"trains should be allowed to back into terminuses if
they are not reversible (that's why we have the reversing point at feedback 2013)"* - puts the
question up, takes the operator's answer, and then turns the train regardless because
`destination.isTerminus()` short-circuits before `reversals.shouldReverse` is ever called.

The turn is correct; the dialog is not. A question whose answer is thrown away is worse than no
question, because the operator now believes they chose. `forJourney` has the destination in hand
(`path.get(path.size() - 1).getEnd()`) and can apply the same exemption the rule does.

---

### REG6-B2 - one answer for the whole journey means the may-reverse DESTINATION is never the square that is asked about

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | CONFIRMED |
| **Where** | `src/org/traincontrol/gui/ManualReversalPrompt.java:73`-`:113` |
| **From** | `df584d0b`, which reworked `a563f7e5`/`48eca362`'s per-point question into one asked at departure |

`forJourney` breaks at the FIRST square `asksAbout` accepts and applies that one answer everywhere:

```java
if (asking.asksAbout(edge.getEnd())) { first = edge.getEnd(); break; }
...
final boolean turn = ask(parent, loc, first);
return new ReversalPolicy() { public boolean shouldReverse(...) { return turn; } ... };
```

The dialog text names `first`. So for a path `Start -> X (a may-turn square in passing) -> Dest (the
may-reverse point the operator actually chose)`, the operator is asked about **X** and the answer is
applied to Dest without Dest ever being named.

That is a regression against the requirement `DIR-A2` was raised for and this commit chain exists to
satisfy: *"if the user decides to send a train to a 'may reverse' point, explicitly ask the user if
the train should change direction."* The question is now asked about a square they did not choose,
and the square they did choose inherits the answer silently.

The javadoc at `:51` argues for once-per-journey and it is a good argument. What does not follow from
it is asking about the FIRST square rather than about the DESTINATION, which is the one square the
operator's gesture actually selected.

---

### REG6-B3 - autonomy and Return Home now stop dead at every plain copy of a split may-reverse square

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | CONFIRMED in code; reachability depends on the layout having a square that builds to more than one copy |
| **Where** | `src/org/traincontrol/automation/Layout.java:5804`-`:5820`, `:5194` (`mayReverseAt`), `src/org/traincontrol/automationui/AutonomyBuilder.java:844` |
| **From** | `a563f7e5` (DIR-A1) |

The stop that used to be inside the reversing branch was hoisted so the operator would not be asked
about a moving train. The predicate it was hoisted under is not the one it replaced:

```java
if (isCurrentLayout() && (mayReverseAt(current)
    || (reversals != null && reversals != ALWAYS_REVERSE && reversals.asksAbout(current))))
{
    loc.setSpeed(0).waitForSpeedBelow(1);
    if (shouldReverseAt(...)) { ...switchDirection... }
    loc.setSpeed(speed).waitForSpeedAtOrAbove(speed);
}
```

Autonomy and the timetable pass `ALWAYS_REVERSE`, so the second disjunct is false for them and the
condition is exactly `mayReverseAt(current)`. `mayReverseAt` (`:5194`) is deliberately about the
SQUARE, not the copy - it returns true for any Point sharing a `block` with a reversing sibling. And
`block` is emitted by `AutonomyBuilder:844` for **every** square that builds to more than one node
(`if (nodes.size() > 1) json.put("block", ...)`), not only for may-reverse splits.

So an autonomous or Return Home run now brings the train to a full stand, waits for speed below 1,
and re-accelerates at the PLAIN copy of any split square whose sibling reverses - squares it
previously passed at line speed, because the old test was `current.isReversing()`.

The comment written above the change asserts the opposite and is the thing to fix in the same edit:

> *"Autonomy is unaffected in substance - it stopped here anyway as the first act of turning."*

True of the reversing copy. Not true of the plain copy, which is the case `mayReverseAt` was
introduced to cover.

Second-order: `loc.setSpeed(speed)` on the way out of the block discards `calculatedSpeed`, the
per-point speed multiplier applied thirty lines above at `:5737`. That override existed before, but
only at points the train was actually turning at; it now applies at every plain copy too, so a
square given a low multiplier to slow a train down has that multiplier cancelled on the way past.

Not reachable on a layout where every named square builds to one copy - which `7d4661d0`'s commit
message says is true of the sample layout today, and which `testEverySquareOnThisLayoutBuildsToOneCopy`
now pins. It becomes reachable the day one does not.

---

### REG6-B4 - the run still stops at every may-turn square after the operator has already said "keep direction"

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | CONFIRMED |
| **Where** | `src/org/traincontrol/gui/ManualReversalPrompt.java:104`-`:110`; `src/org/traincontrol/automation/Layout.java:5804` |
| **From** | `df584d0b` |

The policy `forJourney` returns overrides `asksAbout` to keep answering yes:

```java
@Override
public boolean asksAbout(Point at)
{
    // The same squares, so the run still STOPS at them - a train that is about to be
    // turned has to be standing still whether or not anybody is asked at that moment.
    return asking.asksAbout(at);
}
```

The justification is sound for `turn == true` and empty for `turn == false`. Once the question moved
to departure, `turn` is a captured constant and the policy knows the answer before the train leaves.
A hand-driven send over a path with three may-turn squares, answered "keep direction", now performs
three full stops and three re-accelerations for no reason that survives to the moment they happen.

Before this range a manual send ran straight through. `return turn && asking.asksAbout(at);` restores
that and keeps the stop where it is still needed.

This is `REG6-B3`'s sibling at the manual door: both are "the stop guard asks a wider question than
the thing the stop exists for".

---

### REG6-B5 - `7d4661d0`'s facing write went into one of the three placement doors

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | CONFIRMED |
| **Where** | fixed at `src/org/traincontrol/gui/TrainControlUI.java:6053`; missing at `src/org/traincontrol/gui/AutonomyEditorPanel.java:3929` and `:4076` |
| **From** | `7d4661d0` |

`rememberPlacement` now records the facing alongside the locomotive, which is the fix for Adam's
three-times-repeated *"the paste fix didn't work either - reversals still happen on paste"*. The root
cause it names is general:

> *"This door wrote the locomotive and left the facing alone, so a pasted train had no recorded
> direction at all - and `placementCopy` falls through to COPY 0 when nothing matches."*

Two other doors do exactly that and were not swept:

- **`AutonomyEditorPanel:3929`** - the `GraphLocAssign` "edit or assign locomotive" dialog. Same
  shape, same expression, and it has the same `Point` in hand that `rememberPlacement` reads the side
  off:
  ```java
  session.placeLocomotive(target,
      point.getCurrentLocomotive() == null ? null : point.getCurrentLocomotive().getName());
  ```
  Copying the three lines from `TrainControlUI:6053` is the whole fix.
- **`AutonomyEditorPanel:4076`** - `placeLocomotive(TileKey)`, the picker. No Point in hand, so the
  facing genuinely is not known here; the honest fix is a facing prompt or a documented note that
  this door leaves it to `captureFromLayout`.

`LayoutRightclickAutonomyMenu:975` is the third door and it is correct - `placeFacing` writes
`session.setFacing(station, facing)` at `:980`.

The repository's own rule (`docs/reviews/README.md`, *"When you fix a call site, grep for its twins
before closing the finding"*) applies; `grep -rn "placeLocomotive(" src/` returns all four in one
command.

---

## C - low

### REG6-C1 - `reversalPolicy()` is dead the same day it was written

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | CONFIRMED |
| **Where** | `src/org/traincontrol/gui/LayoutRightclickAutonomyMenu.java:1015` |
| **From** | `a563f7e5` added it, `df584d0b` superseded it with `forJourney` and left it |

`grep -n reversalPolicy` in that file returns the declaration and nothing else. Its javadoc is the
only remaining statement of the "one class for both doors" reasoning at this door, so delete the
method and keep the sentence, rather than the other way round.

---

### REG6-C2 - the right-click door asks about the reversal before it checks the power; its sibling checks first

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | CONFIRMED |
| **Where** | `src/org/traincontrol/gui/LayoutRightclickAutonomyMenu.java:1042` vs `src/org/traincontrol/gui/AutoLocomotiveStatus.java:1010` and `:1043` |
| **From** | `df584d0b` |

In `AutoLocomotiveStatus` the power check (`if (!this.control.getPowerState()) ... return;`) runs at
`:1010`, before `forJourney` at `:1043`. In `LayoutRightclickAutonomyMenu` `forJourney` runs at
`:1051` on the event thread and the power check is inside the worker thread at `:1058`. With the
power off, the diagram door asks "keep direction for X at Y?", takes the answer, and then says
"power on to start".

Two doors, one gesture, opposite orders - the pattern `ManualReversalPrompt`'s own javadoc says the
shared class exists to prevent.

---

### REG6-C3 - the manual refusal reuses a message that says "in autonomous operation"

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | CONFIRMED |
| **Where** | `src/org/traincontrol/automation/Layout.java:2341`; `src/org/traincontrol/resources/messages.properties:173` |
| **From** | `36734d33` |

The destination-is-inactive rule lost its `isAutoRunning()` fence and now fires for hand-driven sends,
but still logs:

```
autolayout.errorInactiveStationInAutoRun=Disallowed because inactive station {0} cannot be chosen in autonomous operation
```

The operator's send was refused and the reason given names a mode they are not in. There is already a
correctly worded sibling for the intermediate case
(`autolayout.errorInactiveIntermediatePoint=Disallowed because the route passes through inactive
point {0}`) to copy. Eight bundles, so this is a real edit rather than a one-liner - which is
presumably why it was skipped.

The key name itself is now wrong too, but renaming it costs eight files and gains nothing; the text
is what a user reads.

---

### REG6-C4 - two identical adjacent `if (answer == RouteConflict.REFUSED)` blocks

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | CONFIRMED |
| **Where** | `src/org/traincontrol/gui/TrainControlUI.java:17141` and `:17146` |
| **From** | `46a9823c` |

The log line was added in a new `if` on the same condition immediately above the existing one instead
of inside it. Behaviour is identical; it reads as though one of the two was meant to test something
else, which is how the next reader loses a minute.

---

### REG6-C5 - `moveOntoFacingCopy` clears the locomotive off the sibling copies that `setLocomotive` already sweeps, and flickers the platform signal doing it

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | CONFIRMED for the redundancy; PLAUSIBLE that the signal flicker is visible on the railway |
| **Where** | `src/org/traincontrol/automationui/AutonomySession.java:1445`-`:1450`; `src/org/traincontrol/automation/Point.java` (`setLocomotive`) |
| **From** | `fb2c0df6` |

```java
// CLEARED FIRST, so the train is never on two copies of one square at once
for (Point point : here) { if (point.getCurrentLocomotive() == train) point.setLocomotive(null); }
onto.setLocomotive(train);
```

`Point.setLocomotive` already does this: `if (l != null && this.layout != null)
this.layout.clearLocomotiveExcept(l, this);`, and its comment says so - *"enforced HERE, because here
is the only door"*. The explicit loop is not wrong, but it goes through `setLocomotive(null)`, which
calls `refreshProtectingSignal` - so the platform's protecting signal is driven to green and back to
red for the duration of the two statements. `onto.setLocomotive(train)` alone would move it with the
signal never leaving red.

This matters because the whole point of `refreshProtectingSignal` living inside `setLocomotive` is
that *"a released or failed path cannot leave a signal stuck red"*; briefly showing green over an
occupied platform is the failure it exists to prevent, arriving from the other side.

---

### REG6-C6 - `flipFacing` follows one square and returns, so `DIR-C3` is half fixed

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | CONFIRMED |
| **Where** | `src/org/traincontrol/automationui/AutonomySession.java:1512`-`:1540` |
| **From** | `03f58b29` |

`DIR-C3` was that a locomotive recorded on two squares got no follow at all, because the loop
`return null`ed on the first undecidable one. It now `continue`s - and then `return tile`s on the
first square it CAN decide, leaving any second square holding the old facing. The finding's own
measured example (*"`moved=null facingAafter=null facingBafter=E`, with neither square moved"*) implies
both should move.

Defensible - the state is already invalid and the checks report it - but the comment claims the fix is
about not giving up, and it still gives up, one square later.

---

## D - checked and clean, or not defects

### REG6-D1

- **`MarklinRoute` MT-247 (`46a9823c`).** The new `CANCEL_ROUTE` branch `return`s from inside
  `new Thread(() -> ...)`'s lambda, inside the `try` whose `finally` at `MarklinRoute.java:1043` runs
  `stopExecuting()` and `updateTiles()`. The re-entrancy flag is cleared and the route tile is
  un-highlighted on the cancel path. `respondToConflict(askable, ...)` short-circuits the dialog call
  correctly for `askable == false`, so the s88 door still never shows a window. The `DIR-A3`
  emergency-stop carve-out is present at both doors (`askable = !auto && !this.hasEmergencyStop() &&
  gui != null`, and `conflictingAccessoryAndReason`'s own clause), and the two now agree.
- **`AddLocomotive` name guard (`a0512166`).** The check is on the add path only; no rename or import
  path acquired it, so an existing locomotive with an unusable name is not stranded. Not an "error for
  a fault the user cannot fix".
- **`GraphReducer` closed tiles (`d4a3d498`).** `grep -rn "findPath(\|reachableTiles("` shows every
  production caller passing `session.shutTiles()`, and `shutTiles()` is the same set
  `AutonomySession:4242` hands `AutonomyChecks`. The 5-argument `findPath` overload survives for tests
  only. The `closed` test in `findPath` is correctly placed BEFORE the arrival test.
- **`placedLocomotives()` re-expressed through `tilesWhere` (`03f58b29`).** `tilesWhere`
  (`AutonomySession:4035`) carries no extra qualification and preserves `points.keySet()` order; the
  excluded-page filter that belonged to this caller stayed at the call site. No behaviour change.
- **`forgetPlacementsElsewhere`.** Removes `AutonomyBuilder.FACING` as well as `loc` from the square
  a locomotive is moved off, so `REG6-B5`'s sibling problem does not extend to stale facings on
  vacated squares.
- **New message keys.** `route.cancelledByOperator`, `loc.ui.errorLocomotiveNameUnusable`,
  `autosetup.ui.facingOnlyOne`, `autosetup.ui.hintFacingOnlyOne`,
  `autolayout.ui.confirmManualReversal`, `autolayout.ui.confirmManualReversalTitle`,
  `autosetup.infoFacingFollowedDirection` and `autolayout.errorInactiveIntermediatePoint` are all
  present in all eight bundles.
- **`followDirectionChanges` is not reachable during a hand dispatch.** `Layout.isRunning()` is
  `running || !activeLocomotives.isEmpty() || locomotiveThreads.get() > 0`, and
  `executePath` increments `locomotiveThreads` before anything else - so the direction echo raised by
  `switchDirection()` at a reversing point cannot re-enter `flipFacing` and move the locomotive
  between copies mid-path. This was the first thing looked for and it is properly guarded.
- **`autonomyLocomotiveRenamed` / `autonomyLocomotiveDeleted`** both repair `lastSeenDirection`
  (`TrainControlUI:4034`, `:4052`), so the new by-name map does not join the by-name-state list in
  `traincontrol-by-name-vs-by-reference`.
- **The facing menu below two choices (`d4a3d498`).** `facings.isEmpty()` replacing `size() <= 1` is
  correct; the disabled explanation line is added only when `size() == 1`, which is right, because
  adding the unholdable `recorded` value makes the list 2 and the explanation would then be false.
