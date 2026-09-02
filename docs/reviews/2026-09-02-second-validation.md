# Second validation: the fixes made in answer to the 2026-09-02 fan-out

**Status:** open

**Prefix for citing this document elsewhere:** `V32`

**Reviewed:** branch `autonomy-diagram-r0` at `2e83b737`, on 2026-09-02. Scope is the two newest
commits and nothing else:

- `e6791631` - "The fan-out's two A's and the room rule's escape, which four reviewers found closed"
- `2e83b737` - "The fan-out's affordance sweep, and eight comments that argue for what the code no
  longer does"

These answer the seven documents of the 2026-09-02 fan-out (`WK3`, `TS3`, `D3F`, `DY3`, `CD3`, `RG3`,
`RT3`). The working tree is dirty in `Readme.md` and three files under `cs2_sample_layout/`; none was
read for a claim and none was touched.

**Method: reading only.** No build, no test run, no `battery.sh`, no `one.sh`, no JVM of any kind was
started - a battery was running throughout. Every claim below is from the source and from `git show`,
with file and line numbers. Where a claim would need execution to settle I say so in the entry rather
than asserting it.

**The two findings I am most confident of are `V32-B1` and `V32-B2`,** and both are the pattern the
briefing named: the sweep stopped one site short, and the test written to pin the fix cannot see the
fix being undone.

---

## Verdict per fix

| Fix | Commit | Does what the message says? | Broke anything? | Test real? |
|---|---|---|---|---|
| `WK3-A1`/`DY3-A1` - `protectsAnOccupiedSquare` loses `synchronized` | `e6791631` | **Yes**, and it is safe on both callers - enumerated at `V32-D1` | No | No test; a surface rule pins the signature only |
| `TS3-A1` - `battery.sh` resolves its reaper from `$(dirname $0)` | `e6791631` | **Yes** for the documented invocation, and the warning is reachable (`V32-D3`) | No | Shell, no test |
| `WK3-B2`/`D3F-B1`/`RT3-B1` - the room rule is asked before `seen` | `e6791631` | **Yes**, and the reorder is meaning-preserving on every path (`V32-D2`) | No - the `TCX-A2` fixture still refuses, checked edge by edge | **Yes** - the new test really does fail without it, though not for the reason its comment gives (`V32-C4`) |
| `WK3-B1`/`DY3-B1`/`D3F-C4` - `aboutToClearProtection` | `e6791631` | **Yes** on the conclusion - the aspect inference is right for a signal, for all three three-way states, and for a lamp | The written justification does not cover two of the tile kinds it is asked about (`V32-C3`); and it is a TOCTOU now (`V32-C5`) | **Weakened** - the pin can no longer see the guard gutted (`V32-B2`) |
| `TS3-B2` - the audit test restores the shared locomotive | `e6791631` | **Yes**, both fields, `Integer`-safe, `finally` covers every exit (`V32-D5`) | No | n/a |
| `TS3-B1` - the control asks the emitted copies | `e6791631` | **Yes**, and it uses the same helper the assertion below it uses (`V32-D4`) | No | n/a |
| `TS3-B3` - the surface rule stops grepping a method name | `e6791631` | Partly. It pins more strings and dropped the one that tied the tile to the shared rule (`V32-B2`) | Yes, in the pin | n/a |
| `TS3-B6`/`WK3-C1`/`D3F-C3` - `canStartAutonomy` asks `autonomyHasErrors()` | `2e83b737` | **At that door, yes. Not at the strip** (`V32-B1`), and the tooltip beside it now says the wrong thing (`V32-C1`) | Yes - see `V32-B1` | **The rule names two questions rather than reading one** (`V32-C2`) |
| `DY3-C6` - Clear All Home Locomotives greys itself | `2e83b737` | **Yes**, guard and affordance ask the identical expression, and the guard stays (`V32-D6`) | No | No test |
| Eight comment and documentation corrections | `2e83b737` | **Yes** on all eight spot-checked, including the two that needed a fact checked in code (`V32-D7`) | No | n/a |
| Dispositions | both | **Not done at all** (`V32-C6`) | - | - |

---

## A - wrong behaviour on the layout, or data silently lost

**None.** I could not find one. Neither commit changes what the railway does except through
`HomeStaging.firstClearRoute`, and that change is meaning-preserving in the direction it claims -
traced path by path at `V32-D2`, including against the `TCX-A2` fixture it could have broken.

---

## B - incorrect results, or crashes in specific configurations

| | Finding | Status |
|---|---|---|
| **V32-B1** | The affordance sweep stopped one site short: `AutonomyOverlayToggle` still decides Fix-setup-versus-Start from `autonomyErrorCount()`, and the regression rule shipped with the fix now *requires* it to | open |
| **V32-B2** | `testSwitchingAnAccessoryByHandAsksAboutProtectingSignals` can no longer see the tile's guard gutted, and its `MUTATION` line names a mutation it cannot catch | open |

### V32-B1 - the strip still asks the narrower question, and the new rule pins it there

`2e83b737` widened `canStartAutonomy()` to the guard's own question:

`src/org/traincontrol/gui/TrainControlUI.java:20164-20167`

```java
public boolean canStartAutonomy()
{
    return this.startAutonomy != null && this.startAutonomy.isEnabled()
        && !autonomyHasErrors();
}
```

The commit message states the rule it is enforcing: "the control that OFFERS an action asks the
predicate the guard asks", and its own javadoc at `TrainControlUI.java:20196-20198` says "'the same in
practice' is what OB-090 was, twice."

There are three controls that offer to start autonomy. Two were swept. The third was not:

`src/org/traincontrol/gui/AutonomyOverlayToggle.java:342-344`

```java
int errors = ui != null ? ui.autonomyErrorCount() : lastTotalErrors;

fixing = source != null && source == start && errors > 0;
```

`fixing` is not display-only. It decides what pressing the strip's button DOES:

`src/org/traincontrol/gui/AutonomyOverlayToggle.java:155-166`

```java
run.addActionListener(e ->
{
    // Fixing is not starting: this one goes to the editor, at the first thing found, exactly
    // as the count beside it does.
    if (fixing)
    {
        ui.openAutonomyEditor(firstFinding);
        return;
    }

    if (source != null) source.doClick();
});
```

So on a setup where `hasErrors()` is true and `errorCount()` is zero - the case
`refuseAutonomyStartWhileBroken` documents as legitimate at `TrainControlUI.java:5180-5182`, "It can
legitimately be zero while this refuses: a graph that cannot be BUILT is a blocking problem, and the
disjunction covers it whether or not the checks also managed to turn it into a finding" - the strip
shows **Start**, in the strip's ordinary colours, and pressing it goes to `source.doClick()`, which
reaches `refuseAutonomyStartWhileBroken` and refuses. That is OB-090's own sentence, at the site
OB-090 was reported from: "it says there are errors, but the start autonomy button is still visible."

**And the rule shipped in the same commit now requires this.**
`test/regression/testErrorsStopTheSetupRunning.java:232-238`:

```java
assertTrue(toggle.contains("autonomyErrorCount()"),
    "AutonomyOverlayToggle no longer reads autonomyErrorCount() at all - the diagram strip's "
    + "own OB-090 fix has gone");

assertTrue(menu.contains("autonomyErrorCount()"),
    "LayoutRightclickAutonomyMenu no longer reads autonomyErrorCount() at all - the right-click "
    + "Start item's own OB-090 fix has gone");
```

Twenty lines above those, the same commit wrote (`testErrorsStopTheSetupRunning.java:208-213`):

> This used to require the literal `autonomyErrorCount()`, and that is how it came to ENFORCE the
> divergence it exists to catch.

It removed that enforcement at `canStartAutonomy` and left it standing at the strip. Somebody moving
`AutonomyOverlayToggle` onto `autonomyHasErrors()` now has to change a test whose failure message
tells them they are deleting an OB-090 fix.

**Severity, honestly.** I cannot demonstrate the divergent state on a real setup by reading alone.
`hasErrors()` = `hasBlockingProblems() || errorCount() > 0`
(`AutonomySession.java:3583-3586`); `check()` returns an empty list only when `graph` or `reducer` is
null (`AutonomySession.java:3416`), and `rebuild()` sets both without nulling either
(`AutonomySession.java:339-360`), so the gap needs a rebuild that threw part way, or a blocking
problem `AutonomyChecks` did not copy in as an ERROR. The commit itself says "The two are the same on
any setup where a blocking problem also produced a finding, which is every one we have." It is filed
as B rather than C because that is the exact argument that let OB-090 recur twice, and because the
remedy is one line plus one assertion.

**Remedy.** `int errors = ...` stays for `paintState` and the count text; the decision becomes
`fixing = source != null && source == start && ui != null && ui.autonomyHasErrors();`, with
`errors > 0` kept only as the no-window fallback. Then relax
`testErrorsStopTheSetupRunning.java:232` to accept either question, or point it at the guard the way
line 214 does.

### V32-B2 - the surface rule can no longer see the tile's guard gutted

`TS3-B3` was that the rule grepped the whole file for `protectsAnOccupiedSquare`, so deleting either
call left it passing. The fix replaced that assertion. What it pins now
(`test/regression/testEditorSurfaceRules.java:637-645`):

```java
assertTrue(label.contains("aboutToClearProtection(tcUI, c.getAccessory())")
        && label.contains("aboutToClearProtection(tcUI, c.getAccessory2())"), ...);

assertTrue(label.contains("if (accessory.isStraight()) return false;"), ...);
```

`LayoutLabel.java` now contains exactly one call to `protectsAnOccupiedSquare`, at line 1385, inside
the helper - and nothing in the test asserts it is there. Mutate the helper's last line to
`return false;`:

`src/org/traincontrol/gui/LayoutLabel.java:1376-1386`

```java
private static boolean aboutToClearProtection(TrainControlUI tcUI, Accessory accessory)
{
    if (accessory == null || tcUI == null || tcUI.getModel() == null) return false;

    if (!tcUI.getModel().hasAutoLayout() || tcUI.getModel().getAutoLayout() == null) return false;

    // Currently straight means the click is about to TURN it - red, protective, harmless.
    if (accessory.isStraight()) return false;

    return tcUI.getModel().getAutoLayout().protectsAnOccupiedSquare(accessory);
}
```

Both call strings survive. `if (accessory.isStraight()) return false;` survives. `MarklinRoute.java`
still contains the name (line 475). `Layout.java` still declares it (line 6157). Every one of the
four assertions passes, and `SVN-B16` is undone at the tile door - clicking a protecting signal green
by hand goes unwarned again, which is the whole finding.

The javadoc has not been updated either. `testEditorSurfaceRules.java:622` still says:

```
 * MUTATION: deleting either call to `protectsAnOccupiedSquare` fails this.
```

There is one such call, the test does not assert on it, and deleting it does not fail this. Under the
README's rule that the `MUTATION` line is what a reader trusts a surface rule for, that sentence is
now worse than none.

**Remedy.** Add a third assertion over the helper's body -
`bodyOf(label, "private static boolean aboutToClearProtection(")` must contain
`protectsAnOccupiedSquare` - and rewrite line 622 to name the mutation the test actually catches.

---

## C - low: cosmetic, narrow, or a design record that is now false

| | Finding | Status |
|---|---|---|
| **V32-C1** | The right-click Start item's tooltip says "wait for trains" in exactly the case the widened guard added | open |
| **V32-C2** | The affordance rule names two questions rather than reading one, does not pin `autonomyHasErrors()`'s body, and its javadoc still says `hasErrors()` has no callers | open |
| **V32-C3** | The aspect comment justifies itself with `doSwitch()`, which is not what `execSwitching` runs for the second limb it was written for | open |
| **V32-C4** | The new staging test's comment says the defect is shuffle-dependent. It is not - the pre-fix code fails deterministically | open |
| **V32-C5** | The aspect is read on the event thread and re-read by `doSwitch` on the switching worker, so a protection command landing between them turns a "harmless" click into a clearing one, unwarned | open |
| **V32-C6** | Nothing either commit fixed has been dispositioned; all seven fan-out documents still read `**Status:** open` on findings that are fixed | open |

### V32-C1 - the greyed Start item now explains itself wrongly

`src/org/traincontrol/gui/LayoutRightclickAutonomyMenu.java:194-209`

```java
boolean canStart = ui.canStartAutonomy();

menuItem.setEnabled(canStart);

if (!canStart)
{
    // And say which of the two reasons it is.  The tooltip was hardcoded to the
    // waiting-for-trains message, which is a lie whenever Start is off for any other
    // reason - including the one immediately above.
    int errors = ui.autonomyErrorCount();

    menuItem.setToolTipText(AutonomyEditorPanel.wrapped(
        errors > 0
            ? I18n.f("autolayout.ui.errorCannotStartWithErrors", errors)
            : I18n.t("autolayout.errorUnableToStartAutonomyWaitForTrains")));
}
```

`canStart` is now false on `hasErrors()`. In the case `hasErrors()` adds - graph will not build, no
finding - `errors` is zero, so the tooltip falls to the `else` and tells the operator to wait for
trains that are not running and never will be. The guard has a third branch for exactly this and got
it in the same commit (`TrainControlUI.java:5193-5195`, `errorCannotBuildDetailOne`); the affordance
did not.

That the count reader here is otherwise display-only is right - it answers the briefing's question
about the remaining readers - but "display-only" and "displays the truth" are not the same thing, and
the comment three lines above is about this precise failure at this precise line.

**Remedy.** Give the tooltip the guard's third arm: `errors > 0 ? ... : ui.autonomyHasErrors() ?
errorCannotBuildDetailOne : waitForTrains`.

### V32-C2 - the rule that pins the affordance to the guard still names both questions

The commit message says the rule "now READS the guard rather than naming a question, because naming
one is how it came to enforce the divergence it exists to catch", and the test's own comment
(`testErrorsStopTheSetupRunning.java:212-213`) says "It reads the guard and requires the affordance to
ask the same thing."

It does not. It names two literals (`testErrorsStopTheSetupRunning.java:218-226`):

```java
assertTrue(guard.contains("hasErrors()"), ...);
assertTrue(canStart.contains("autonomyHasErrors()"), ...);
```

That is better than the single literal it replaced - a third widening of the guard now fails the first
assertion loudly instead of silently enforcing a divergence - but it is not what either sentence
claims, and the correspondence between the two literals is knowledge held by a human, not by the test.
Nothing asserts that `autonomyHasErrors()` reaches `session.hasErrors()`; mutate its body to
`return false;` and every assertion in the method still passes.

And the method's javadoc has not been swept. `testErrorsStopTheSetupRunning.java:179-183`:

```
 * `AutonomySession.hasErrors()` - the method the test above exercises - has zero callers left in
 * `src/`: `grep -rn hasErrors src/` finds only its own declaration. What the guard
 * (`refuseAutonomyStartWhileBroken`) and the affordances (`TrainControlUI.canStartAutonomy`,
 * `AutonomyOverlayToggle`, `LayoutRightclickAutonomyMenu`) actually ask today is
 * `autonomyErrorCount()` / `errorCount()`.
```

`hasErrors()` has two callers in `src/` now - `TrainControlUI.java:5183` and
`TrainControlUI.java:20185`. The same commit corrected this exact claim in the assertion message at
line 90-92 and left the javadoc saying the opposite two hundred lines further down. (The last sentence
is also the one `V32-B1` is about: it is still true of `AutonomyOverlayToggle`.)

**Remedy.** Add `bodyOf(ui, "public boolean autonomyHasErrors()")` must contain `hasErrors()`, and
rewrite lines 179-185.

### V32-C3 - `doSwitch()` is not what the second limb runs

`src/org/traincontrol/gui/LayoutLabel.java:411-414`:

```
// Which direction this click is about is decided before it
// runs: `Accessory.doSwitch()` is `isStraight() ? turn() :
// straight()`, so a signal that is currently TURNED is the
// one about to be made straight - green, and harmful.
```

`c.getAccessory2()` is non-null only on a three-way (`MarklinControlStation.java:657-660` is the only
place it is set), and `execSwitching` never calls `doSwitch()` on a three-way. It drives the two
accessories directly, in three cases (`LayoutDiagramComponent.java:147-167`):

| state | command on `accessory` | command on `accessory2` |
|---|---|---|
| both straight | `setSwitched(true)` - RED | `setSwitched(false)` - green, already green |
| acc turned, acc2 straight | `setSwitched(false)` - **GREEN** | `setSwitched(true)` - RED |
| acc2 turned | `setSwitched(false)` - green, already green | `setSwitched(false)` - **GREEN** |

**The conclusion survives all four states** (the illegal both-turned combination
`getPrimaryDriveState` records as having happened included): in every row, the drive that is commanded
from red to green is exactly the one whose `isStraight()` is false, and the two rows where a drive is
re-commanded green were already green, so no protection is being cleared. So this is not a behaviour
finding - it is a design-record finding, and in this repository that is what gets a guard reverted:
the reader who checks the comment against a three-way finds that the method it cites is not the method
that runs.

It also does not hold for the one other tile kind that reaches this helper. An UNCOUPLER is driven by
`LayoutDiagramComponent.java:132-142`:

```java
else if (this.isUncoupler() && this.accessory != null)
{
    if (this.getRawAddress() % 2 == 0)
    {
        this.accessory.setSwitched(true);
    }
    else
    {
        this.accessory.setSwitched(false);
    }
}
```

An even-raw-address uncoupler is always commanded RED. If it is currently red, `isStraight()` is false
and `aboutToClearProtection` asks - so a protective command raises the warning the fix exists to
remove. I could not make this reachable through the editor: `isPairableSignal`
(`AutonomyEditorPanel.java`) requires `componentType.SIGNAL`, so an uncoupler tile cannot be paired.
It is reachable in principle through a hand-written or legacy `autonomy.json`, whose protecting-signal
names go straight onto the Point at `Layout.java:7278` and are resolved by name with the
Switch/Signal prefix fallback. Contrived; worth one clause in the comment rather than code.

**Remedy.** Say that the inference is about the COMMAND rather than about `doSwitch`, and that
`execSwitching`'s three-way branch reaches the same place by a different route - one sentence, at
`LayoutLabel.java:411`.

### V32-C4 - the staging test's shuffle claim is wrong

`test/core/testHomeStaging.java:3383-3391`:

```
// TWENTY TIMES, because `getNeighbors` shuffles.
//
// Which of the two ways into HS D the search reaches first is decided by that shuffle, and
// only the run that tries the SHORT one first can show the defect: it is the refused
// arrival being recorded as seen that then prunes the long one.  A single attempt passes
// about half the time ...
```

The queue is FIFO - `ArrayDeque` with `add`/`poll` (`HomeStaging.java:943`, 972, 1072) - so the whole
of `HS A`'s neighbour loop runs before `HS B` is ever polled. Whichever order the shuffle produces,
`A -> D` is evaluated during `HS A`'s own expansion, and under the pre-fix ordering that arrival is
written into `seen` before the room test refuses it. By the time `B -> D` is reached from `HS B`,
`seen["HS D/straight"]` holds the empty command map, and `alreadyReached`
(`HomeStaging.java:1154-1176`) dominates trivially against an empty map. The pre-fix code therefore
fails **20 of 20**, not about half.

The test is not weakened by this - it fails without the fix, and for the stated mechanism. What is
wrong is the sentence that justifies the loop, and a reader trimming the loop would be told they were
trading protection for speed when they are not. (`HomeStaging.java:1029` states the same measurement
correctly: "the test below reproduces it twenty times out of twenty.")

**Remedy.** Keep the twenty - it costs nothing and the shuffle is real elsewhere - and correct the
reason: the repeats guard against the neighbour order mattering, and it is asserted that it does not.

### V32-C5 - the aspect is read on one thread and acted on by another

The guard runs on the event thread inside `invokeLater` (`LayoutLabel.java:341`, 415-417). The command
runs later, on the single-thread switching pool (`LayoutLabel.java:482`, 582), and `doSwitch()` reads
`isStraight()` again at that moment. Between the two, `Layout.refreshOneSignal` can drive the same
accessory from an occupancy change.

The dangerous ordering is: signal GREEN at click time, so `aboutToClearProtection` returns false and
no dialog is shown; a train then occupies the platform and protection sets the signal RED; the
queued `doSwitch` reads RED and sets it GREEN - clearing protection over an occupied platform with no
warning at all. The window is the switching pool's queue depth, so hundreds of milliseconds at most,
and it needs an occupancy change inside it.

This is a narrowing the fix introduced: the pre-fix code had no state dependence and warned on every
click. It is filed as C because the window is small, the direction of the trade is the one the route
door already chose, and closing it properly means asking the aspect on the worker, where a dialog
cannot go. Worth recording so the next reader does not discover it as a surprise.

### V32-C6 - the record still says nothing is fixed

Neither commit dispositioned anything. All seven documents from this morning still carry
`**Status:** open` at the head, and each fixed finding's own section still carries its original
status line - for example `docs/reviews/2026-09-02-week-of-commits-review.md:86`:

```
**Status: open. Introduced by `87b6c10a` (2026-09-02 03:27), unreviewed.**
```

for `WK3-A1`, whose fix is the first item in `e6791631`. `TS3-A1`, `TS3-B1`, `TS3-B2`, `TS3-B3`,
`TS3-B6` and `TS3-B7` are the same at `docs/reviews/2026-09-02-test-suite-review.md:83`, 177, 238,
304, 372 and 487.

This is the safe direction to be wrong in, and the previous round dispositioned in a commit of its own
(`cf048f9b`, "17 fixed, 14 closed"), so this is most likely pending rather than mistaken. It is filed
because the briefing asks about disposition honesty and because, read today, the record says roughly
twenty-five findings are outstanding that are not - and two of them (`TS3-B3`, `TS3-B6`) should come
back not as FIXED but as partly fixed, per `V32-B1` and `V32-B2`.

---

## D - checked and sound

This is the part that says what can be trusted.

### V32-D1 - dropping `synchronized` gives up nothing that method was taking

`Layout.java:6157`. Enumerating what the monitor was serialising for `protectsAnOccupiedSquare`, term
by term:

1. **`this.control`** - a field read, then `getAccessoryByName` on the control station
   (`MarklinControlStation`), which is a lookup in `accDB` under a different object's lock entirely.
   The Layout monitor never protected it. **Safe.**
2. **`this.getPoints()`** - `this.points.values()`, a live view over a plain `HashMap`
   (`Layout.java:722`, `getPoints` at 5923). The monitor did exclude the three structural writers,
   which are all synchronized: `deletePoint` (2640), `renamePoint` (2793) and point creation. But both
   callers reach this only while autonomy is running - `LayoutLabel.java:384` is inside
   `isAutonomyRunning()`, `MarklinRoute.heldReason` returns at line 430 unless it is - and the graph
   is not restructured during a run: the editor is refused while busy, and `parseAuto` builds a whole
   new `Layout`. This is also the sixth reader of the hazard `getEdges`' own javadoc
   (`Layout.java:5899-5907`) files as "real and unaddressed" and deliberately not fixed with a copy or
   a lock. **Safe, and consistent with the decision already recorded there.**
3. **`point.getCurrentLocomotive()`** - the one thing here that really is written during a run. It is
   **volatile** (`Point.java:22-24`, with a comment saying exactly why), and its writers hold the
   Point's monitor rather than the Layout's (`Point.assign`, line 497). So the unsynchronised reader
   sees the latest write; what it gives up is only compound atomicity across several Points, which is
   the same trade `getActiveAccs` states at `Layout.java:867-870`. **Safe.**
4. **`point.getProtectingSignals()`** - an unmodifiable view over a `Point`-owned `ArrayList`
   (`Point.java:704-707`). Its only writers are `setProtectingSignal`/`setProtectingSignals`
   (715-743), and the only caller of either on the runtime `Point` is `Layout.java:7278`, inside
   `parseAuto`. The editor's identically-named methods are on `AutonomySession`/
   `AutonomyCompanionStore` and touch a different object. Not mutated during a run. **Safe.**

Both callers checked, as the briefing asked:

- **Event thread** (`LayoutLabel.java:415-417`). This is the whole point: it sits four lines below
  `getActiveAccs()` at line 386, whose javadoc (`Layout.java:851-873`) says the same call in the same
  branch froze the window including Stop. `configureAndLockPath` holds this monitor across per-command
  sleeps. Removing it removes the freeze and adds nothing.
- **Route thread** (`MarklinRoute.java:475`). Blocking there was not a UI freeze, but it was worse in
  one respect than it looks: `heldReason` is documented as "asked per command, immediately before the
  command" (`MarklinRoute.java:411`) precisely for freshness, and waiting on the dispatch's monitor
  between commands is the opposite of that. Nothing is lost by not waiting - the active-accessory half
  is already lock-free at line 432 and already covers a dispatch mid-configuration through
  `takingPath` (`Layout.java:878-903`, RC-A10). And it removes a lock ordering: `configureAndLockPath`
  holds the Layout monitor and reaches `TrainControlUI.repaintSwitch`, which is synchronized on the
  UI, which is the AB-BA that `getEdges`' javadoc (5889-5897) says a copy-under-the-monitor would have
  created.

No third caller exists (`grep` over `src/` and `test/` finds `LayoutLabel.java:1385` and
`MarklinRoute.java:475` only), and no internal call from a synchronized `Layout` method relies on it.

Two things the new javadoc says that I checked and found true: `getEdges` really does record the same
monitor being taken from the UI once and reverted (5876-5907), and `points` really is already
documented as a live unsynchronised view for this class of reader (5916-5926).

### V32-D2 - the room reorder means the same thing on every path through the loop

`HomeStaging.java:1017-1069`. The only movement is that the `next.equals(to)` room test now sits above
the key/`alreadyReached`/`seen` block instead of below it, and `route` is built one step earlier.
Taking every path through one iteration:

| arrival | pre-fix | post-fix | same? |
|---|---|---|---|
| `next != to` | key, dominance, record, enqueue | identical | yes - the new block is fenced behind `next.equals(to)` |
| `next == to`, dominated, room would refuse | pruned at `alreadyReached` | refused at room | same outcome (`continue`), nothing recorded either way |
| `next == to`, dominated, room passes | pruned at `alreadyReached` | measured, then pruned at `alreadyReached` | same outcome; one extra `measuredRoomToReverseInto` walk |
| `next == to`, not dominated, room refuses | **recorded**, then refused | refused, **not recorded** | the fix |
| `next == to`, not dominated, room passes | recorded, then `mustBackIn` | identical | yes |

So the set of recorded states is a subset of what it was, plus whatever later arrivals that unblocks -
which is the intended effect and the only one. Nothing is now recorded that was not, other than by
that unblocking. `measuredRoomToReverseInto` is pure (`Layout.java:6201-6221` - it reads the path, the
end point's flags and the locomotive, and nothing live), so calling it on paths that will be pruned
has no effect beyond the walk. `to` is never enqueued in either version (line 1072 only enqueues a
non-terminus, and both arms of the `next.equals(to)` block return or continue), so no loop is opened,
and `ROUTE_SEARCH_LIMIT` still bounds the whole thing.

**And it does not break the fixture the rule shipped with.** `testTheAuditSeesTheReversalRoomRuleThe...`
uses `shortBerth()` (`testHomeStaging.java:245-268`), whose edge list overrides `ring()`: there is no
`HS C - HS D` edge at all, so `HS D` is reachable only from `HS A`, every edge is measured at 5, and
the `seen` keys cap the longest route to `HS D` at 15 against a 40-unit train. Still refused, still
`auditAgainstRuntime() == 0`. Its own fixture comment at 254-260 says why every edge is measured, and
that is the property the reorder needed.

One thing the fix inherits and does not introduce: "a longer approach is more room" is true of the sum
the rule actually computes, and `Layout.java:2375-2387` records two ways that sum is unsound. The
second - "it may be the wrong segments" - bites where the train turns round *part way along*, so it
does not apply to the new fixture, which reverses at a terminus at the end of the path; there the sum
is exactly Adam's stated rule ("sum the track segments leading up to it"). Where a path does contain
an intermediate reversing point, the reorder does make that unsoundness easier to reach in Return
Home, because the longer route it now tries is the one whose extra length is in front of the reversal.
Adam has ruled on the counting - "OK", `FX2-3`, recorded at `Layout.java:2389-2390` - and the planner
and the runtime call the identical function, so the two layers cannot disagree. Recorded here rather
than filed.

### V32-D3 - the reaper resolves correctly for the documented invocation

`docs/tools/battery.sh:295`:

```sh
REAPER="$(cd "$(dirname "$0")" && pwd)/reap.ps1"
```

The script's own usage line (`battery.sh:8-10`) is `TC_SCRATCH=... bash docs/tools/battery.sh`, from
the project root. `$0` is then `docs/tools/battery.sh`, `dirname` gives `docs/tools`, and the
subshell's `cd`/`pwd` gives the absolute path - `docs/tools/reap.ps1`, which exists (checked: `ls
docs/tools/` lists `reap.ps1`, 2,909 bytes). The `cd` is inside `$( )`, so it does not move the
script's own working directory, and it is the only `cd` in the file - so `$0`'s relative form is still
resolved against the directory the operator invoked from, which is what makes it work.

The existence check at 297-306 is reachable and its `REAPER=""` really does suppress the call
(`if [ -n "$REAPER" ]` at 377). The path shape is unchanged from the pre-move version -
`$(pwd)/tools/reap.ps1` was the same MSYS-absolute form handed to `powershell.exe` - so whatever
argument conversion made it work before still applies.

**Every site swept.** `grep -rn "reap.ps1"` over the whole tree finds only `battery.sh:295` and two of
its own comments. `one.sh` has no reaper call - its PowerShell use is the two inline probes at lines
82 and 122 - so there is no twin to miss.

### V32-D4 - the diagram-session control now asks the copies

`test/core/testAutonomyDiagramSession.java:978-988`. The control resolves the base name through
`session.pointNamesFor(...)` - the same helper the assertion at line 1001 uses, so the two halves of
the test now speak the same language - asserts the list is non-empty first, and then asks
`inactivePointNames` about each emitted copy. `pointNamesFor` exists at
`AutonomySession.java:2566-2569` and delegates to the station index. The `TS3-B1` failure mode (a base
name compared against suffixed copy names, so the control could never fail) is gone.

### V32-D5 - the shared locomotive is put back

`test/core/testHomeStaging.java:169-176` and 232-237. Both mutated fields are captured before the
`try` opens, both are restored in the `finally`, and the `try` opens before the first mutation at line
181 so no early assertion can escape it. `setTrainLength` takes `Integer`
(`Locomotive.java:1417`), so restoring a null captured from a fresh locomotive is safe rather than an
unboxing NPE. Nothing else in the test writes to `tooLong`.

### V32-D6 - the Clear All button and its guard ask one question

`AutonomyEditorPanel.java:6231-6240` enables on `session.tilesWithAHome().size() > 0 && !ignored`;
`clearAllHomes` at 6467-6482 refuses on `session.tilesWithAHome().isEmpty()`. Identical expression,
same method, no second definition. The guard was kept, with the reason written at 6474-6477, which is
the right call - the panel's refresh is not synchronous with a home going away.

Swept for twins: `grep -rn "menuClearAllHomeLocomotives\|clearAllHomes\|tilesWithAHome"` finds one
button, one handler and one guard. There is no second door. All four message keys the two commits use
(`autosetup.ui.infoNoHomesToClear`, `autolayout.ui.confirmClearAllHomeLocomotives`,
`autosetup.ui.errorCannotBuildDetailOne`, `layout.ui.confirmAccessoryProtecting`) are present in all
eight bundles.

One observation rather than a finding: a disabled Swing button does not normally receive the mouse
events `ToolTipManager` needs, so `infoNoHomesToClear` may never be seen. I did not run it, and the
project already uses this pattern deliberately for the sibling `nameAll` button
(`AutonomyEditorPanel.java:6218-6223`) and for the editor's mode tabs, with the reasoning written out
at `LayoutEditor.java:6060-6062`. So it is consistent, and if it is wrong it is wrong in three places
that predate this commit.

### V32-D7 - the comment and documentation corrections check out

Spot-checked, including the two that needed a fact:

- **`Point.isActive`** (`Point.java:198-201`) now says the flag is asked of every square and that
  `isPathClear` refuses a path THROUGH an inactive point whatever it is. True: `Layout.java:2278-2287`
  applies the intermediate rule with no `isAutoRunning` fence, and says so in its own comment, while
  the endpoint rules at 2224 and 2301 are fenced.
- **`SVN-A3`'s commit reference** (`docs/reviews/2026-09-01-week-of-commits-review.md:194`) changed
  from `87b6c10a` to `1cfdf370`. Correct: `git log -S"layoutRefreshComplete"` on `TrainControlUI.java`
  returns `1cfdf370`, and `87b6c10a` does not touch it.
- **`AutomationAPI.md:531`** now describes Clear All Home Locomotives as existing, in the editor's
  tool column, asking first. All three are true (`AutonomyEditorPanel.java:479-482`, 6484-6491).
- **The reversal-room guard's "inert" claim** (`Layout.java:2392-2397`) is corrected, and the
  paragraph it replaces is the one `FV2-B1`/`FV2-C2` measured.
- **`AutonomyChecks`' "ERROR rather than a warning"** paragraph (`AutonomyChecks.java:746-753`) now
  agrees with its body and says why restoring it would stop the railway starting - the reason given,
  that `errorCount() > 0` refuses the start, is consistent with `AutonomySession.java:3583-3586`.
- **`AutonomySession.CARRIED_SETTINGS`** and **`layoutEditingCompleteThen`** both now say what the
  code does rather than what it used to; neither introduces a new claim I could falsify.

### V32-D8 - the new staging test does fail without its fix

Traced rather than run. `twoWaysToOneBerth()` (`testHomeStaging.java:3426-3453`) gives `HS D` two ways
in, five direct from `HS A` and ten by way of `HS B`, with `HS B` deliberately not a station so the
A* cannot stage through it. `canEnter` admits `HS B` - it is active, unoccupied, its sensor is its own
and it carries no exclusions, and `p.isDestination()` is not required
(`HomeStaging.java:1188-1253`). `mustBackIn` is false for a reversible locomotive
(`HomeStaging.java:1585-1588`), so the arrival returns rather than continuing. Under the pre-fix
ordering the `A -> D` arrival is recorded with an empty command map, `alreadyReached` dominates
trivially against it, and `B -> D` is pruned before its room is measured - so the plan is `IMPOSSIBLE`
and `assertEquals(refused, 0)` fails. Under the fix the refused arrival is not recorded and the
ten-unit route is returned. The four precondition assertions (3370-3381) all hold on the fixture as
written.
The only thing wrong with it is the sentence at `V32-C4`.

---

## What I did not check

- **Anything needing execution.** No test was run, so every "fails without the fix" above is a trace
  through the code, not an observation. `V32-B2`'s mutation and `V32-D8`'s trace are the two that
  would most repay a real run.
- **Whether `hasBlockingProblems()` can be true while `errorCount()` is zero on a real setup.**
  `V32-B1` and `V32-C1` both rest on that state, which the code asserts is legitimate
  (`TrainControlUI.java:5180-5182`, `AutonomySession.java:3551-3553`) and which I could not construct
  by reading. If Adam rules the state impossible, both drop to D and the two remaining count readers
  are display-only after all.
- **The five other files `e6791631` touched** - the seven fan-out review documents themselves. They
  are the reviewers' work, which is out of scope for a validation pass on the fixes.
- **`cs2_sample_layout/`**, which was not read or written.
