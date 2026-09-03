# Validation of the 2026-09-02 fixes: 2026-09-01 round

**Status:** open

**Citation prefix:** `V31`. Cite findings from this document as `V31-B1`, `V31-C2`, `V31-D5`, and so on.
`V3`, `V2`, `FV2` and `SV2` are taken by earlier validation passes; `V31` is not used by any other
document in this folder.

| | |
|---|---|
| **Reviewed** | branch `autonomy-diagram-r0`, v3.0.0 |
| **HEAD at the start and end of this pass** | `2e83b737` — "The fan-out's affordance sweep, and eight comments that argue for what the code no longer does" |
| **Scope** | the fixes made on 2026-09-02 for the 2026-09-01 round: `1cfdf370`, `87b6c10a`, `975f157d`, `8d1c17ca`, and the dispositions written in `cf048f9b` / `54a70c03`. The two follow-up commits `e6791631` and `2e83b737` were read as part of the same fixes, because they change the same code. |
| **Not in scope** | the seven 2026-09-02 fan-out review documents themselves, the code they cover outside these fixes, and the three defects they already found and fixed (`WK3-A1`/`DY3-A1`, `WK3-B2`/`D3F-B1`/`RT3-B1`, `WK3-B1`/`DY3-B1`/`D3F-C4`) — all three were re-read and confirmed fixed, and are recorded under D. |
| **Working tree during this pass** | `Readme.md` and three files under `cs2_sample_layout/` modified and uncommitted, exactly as the 2026-09-02 passes recorded. Nothing in this pass wrote to the repository except this file. |
| **Date** | 2026-09-02 |

---

## Method, and what this pass did NOT do

**No tests were run, nothing was compiled, no JVM was started, and the application was not launched.**
A battery was running on this machine throughout. Every statement below was reached by reading:
`git show` on each commit, `grep`, whole-method reads, and reading JSON fixtures as data with a
throwaway script.

`cs2_sample_layout/` was not read or written at all. Where a claim depends on the operator's real
data it was checked against the frozen copy in `test/operator_layout/config/`.

Where a claim is about a test I could not run, I say what the test asserts, what the stated mutation
would do to those assertions, and whether the preconditions make the assertion mean what it says. I
do not claim any test passes.

---

## Summary

| | Finding | Severity |
|---|---|---|
| `V31-B1` | The diagram strip decides Start-vs-Fix on `errorCount()`, which the guard stopped asking — and the rule written to catch exactly that now REQUIRES it there | B |
| `V31-B2` | `SVN-B7`'s disposition is not honest: the concurrent double-run it names cannot happen, and the guard added is a 600 ms debounce rather than an "already running" test | B |
| `V31-C1` | Two of the three "why can't I start" messages still split on `errorCount()`, so both can say "wait for trains" in the case `2e83b737` added a message for at the third | C |
| `V31-C2` | The accessory keyboard is a third hand-switching door, and asks neither half of the question `SVN-B16` moved onto `Layout` "so both doors ask it" | C |
| `V31-C3` | `AutonomyChecks` reachability is blind to `active`, so after `D24-B5` a plain square switched out of service can cut the railway with no finding raised | C |
| `V31-C4` | `rebuildHomeStations`' new square rule is keyed on the BLOCK; the harm it cites (`sharesSection` answering IMPOSSIBLE) is keyed on the S88 | C |
| `V31-D1`…`V31-D16` | Checked and found sound — see below | D |

Nothing rises to A. Nothing found here can drive a train onto the wrong track or lose data.

---

## B — Medium

### V31-B1 — the diagram strip still decides on `errorCount()`, and the rule that exists to catch that now enforces it

**Status: open.** Verified by reading, and by reading the regression test that is supposed to prevent
it.

`87b6c10a` widened the start GUARD from `errorCount() == 0` to `hasErrors()`
(`TrainControlUI.java:5183`):

```java
        if (!getAutonomySession().hasErrors()) return false;
```

`hasErrors()` is `hasBlockingProblems() || errorCount() > 0` (`AutonomySession.java:3583-3586`), so
the guard is now strictly WIDER than the count.

`2e83b737` found that `canStartAutonomy` had been left on the count (`TS3-B6` / `WK3-C1` / `D3F-C3`)
and moved it onto `autonomyHasErrors()` (`TrainControlUI.java:20164-20188`). **It did not move the
diagram strip**, which is the affordance `OB-090` was originally about. `AutonomyOverlayToggle.java:342-344`:

```java
        int errors = ui != null ? ui.autonomyErrorCount() : lastTotalErrors;

        fixing = source != null && source == start && errors > 0;
```

`fixing` is a decision, not a message: `run.setText(fixing ? btnFixSetup : source.getText())`
(`:359`), and the press does one of two different things (`:155-166`):

```java
            if (fixing)
            {
                ui.openAutonomyEditor(firstFinding);
                return;
            }

            if (source != null) source.doClick();
```

`source` is the main window's Start button (`:315`), whose enabled state is deliberately never told
about the checks (`TrainControlUI.java:20153-20155`). So with `hasBlockingProblems()` true and no
ERROR finding, the strip shows **Start**, the press goes to `source.doClick()`, and
`refuseAutonomyStartWhileBroken` refuses it — which is `OB-090` word for word: *"the fix it button is
not shown, rather just start autonomy when the config had worked before"*
(`AutonomySession.java:3545-3547`).

**The worse half is the rule.** `testErrorsStopTheSetupRunning.testTheAffordancesAskTheGuardsOwnQuestion`
was rewritten in `2e83b737` precisely because naming a question rather than reading the guard is how
it came to enforce the divergence it exists to catch (`test/regression/testErrorsStopTheSetupRunning.java:208-213`).
That correction was applied to `canStartAutonomy` only. Two assertions below it still say:

```java
        assertTrue(toggle.contains("autonomyErrorCount()"),
            "AutonomyOverlayToggle no longer reads autonomyErrorCount() at all - the diagram strip's "
            + "own OB-090 fix has gone");
```
(`:232-234`)

Its own javadoc names `AutonomyOverlayToggle` as one of the affordances that must ask the guard's
question (`:181-183`). Moving the strip onto `autonomyHasErrors()` — the fix — would leave that
assertion passing only by accident (the file would have to keep a `autonomyErrorCount()` call
somewhere), and moving it onto the guard's question *and* dropping the now-dead count would fail the
test. The rule as written pins the divergence in place.

**Reachability, honestly.** `errorCount()` counts ERROR findings from `check()`, and
`AutonomySession.check()` returns an empty list when `graph == null || reducer == null` (`:3411-3416`);
`rebuild()` assigns `graph` at `:349` and `reducer` at `:355`, with three statements between them that
can throw. So the divergent state is "a graph that will not build, with no reducer to check". That is
narrow — and it is exactly as narrow as the `canStartAutonomy` case three reviewers filed this
morning and the project fixed. `autonomyErrorCount`'s own javadoc states the standard:
*"The two are the same on any setup where a blocking problem also produced a finding, which is every
one we have - but 'the same in practice' is what OB-090 was, twice."* (`TrainControlUI.java:20196-20198`).

**Fix:** `AutonomyOverlayToggle` should decide `fixing` on `ui.autonomyHasErrors()`, keeping
`autonomyErrorCount()` only where the count is printed; and the two assertions at
`testErrorsStopTheSetupRunning.java:232-238` should read the guard the way the one above them now
does, rather than naming a method.

---

### V31-B2 — `SVN-B7` is marked FIXED, and the double-run it names could not happen; the guard that was added is a 600 ms debounce

**Status: open.** Verified by reading, including the pre-fix tree.

`SVN-B7`'s stated gesture (`docs/reviews/2026-09-01-week-of-commits-review.md:600-602`):

> **Gesture:** while a route's play button is grey, click its cell and confirm, or right-click →
> Execute. The route's accessory commands are issued a second time, concurrently with the first run.

It is dispositioned **FIXED 2026-09-02 (`87b6c10a`)** (`:577`), and the fix added a check on the
funnel (`TrainControlUI.java:16121-16126`):

```java
        if (route != null && routesExecuting.contains(route))
        {
            this.model.log(I18n.f("route.ui.infoAlreadyRunning", route));

            return;
        }
```

**Limb 1 — the concurrent double-run cannot happen and could not before the fix.** Every door,
including the two the finding names, the diagram's own route tile (which does not pass through
`executeRoute` at all, `LayoutLabel.java:566` and `:582`) and the s88 trigger, reaches
`MarklinRoute.execRoute`, whose thread body begins (`MarklinRoute.java:501-503`):

```java
        new Thread(() ->
        {
            if (this.setExecuting())
```

and `Route.setExecuting()` is the re-entrancy guard — `synchronized`, returning false while the route
is running (`src/org/traincontrol/base/Route.java:115-125`), cleared in a `finally`
(`MarklinRoute.java:889-894`). I checked the pre-fix tree: `git show 87b6c10a^` has both, unchanged.
A second press during a run therefore started a thread that did nothing. The defect was that it said
nothing, not that it ran the route twice.

**Limb 2 — `routesExecuting` does not mean "running".** It is the spinner set
(`TrainControlUI.java:24478-24488`). `executeRoute` marks it, then starts a worker whose body calls
`this.model.execRoute(route)` (`:16177`) — and that is
`MarklinControlStation.execRoute` → `r.execRoute(false)`, which **spawns a thread and returns**
(`MarklinControlStation.java:3133-3145`). So `runAndTimeTheRoute`'s `finally` reaches
`routeFinished(route)` (`:16215`) within milliseconds of the press, whatever the route is doing, and
`routeFinished` clears the set after a floor of `ROUTE_MINIMUM_VISIBLE_MS = 600`
(`:24557-24601`, `:24608-24610`).

The guard is therefore armed for about 600 ms after a press and not at all after that. On a route
that takes ten seconds — the case the commit message describes — the second press at five seconds is
not refused here; it is refused silently by `setExecuting`, exactly as before.

**Limb 3 — what the fix does change.** A route that genuinely finishes inside the 600 ms floor cannot
be fired again from any door until the floor expires, and the refusal is logged as
`route.ui.infoAlreadyRunning` — *"Route {0} is already running, so it was not started again"* — which
in most of that window is untrue. That is a small behaviour loss and a false statement in the log,
in exchange for a protection that was already there.

I am not asking for the guard to be removed: `routeFinished`'s own comment
(`:24568-24572`) already defines the grey button as meaning "pressing it again would be refused", and
the funnel check makes that true rather than a claim about one door. What is wrong is the record. The
honest disposition is that `SVN-B7` was a **mistaken finding** — the README's own category for it is a
withdrawn B becoming a D, *"the single most useful thing in a review for calibrating how much to trust
the rest of it"* — and that what shipped is a debounce that makes the greyed button honest at all three
doors, with a log line that should say so rather than say "already running".

---

## C — Low

### V31-C1 — two of the three "why can't I start" messages still split on the count

**FIXED 2026-09-03**, and pinned so the next widening cannot leave a twin behind.

Both twins now carry the guard's third arm: `requestStartAutonomy`'s exception and the greyed
right-click item's tooltip say `errorCannotBuildDetailOne` when `hasErrors()` refuses and the count is
zero, instead of telling the operator to wait for trains that are not running.

`testEveryRefusalNamesTheReasonACountCannotSee` holds all three sites in one rule, and is
mutation-confirmed against deleting the arm from any of them.  This is the sweep-the-siblings miss this
project makes more often than any other, so it is pinned rather than fixed and forgotten.

`2e83b737` gave `refuseAutonomyStartWhileBroken` a second message for the case where `hasErrors()`
refuses and `errorCount()` is zero (`TrainControlUI.java:5193-5195`):

```java
        JOptionPane.showMessageDialog(this, errors > 0
            ? I18n.f("autolayout.ui.errorCannotStartWithErrors", errors)
            : I18n.t("autosetup.ui.errorCannotBuildDetailOne"));
```

Its two twins were not swept. `TrainControlUI.requestStartAutonomy` (`:20241-20247`):

```java
            int errors = autonomyErrorCount();

            throw new Exception(
                errors > 0
                    ? I18n.f("autolayout.ui.errorCannotStartWithErrors", errors)
                    : I18n.t("autolayout.errorUnableToStartAutonomyWaitForTrains")
            );
```

and `LayoutRightclickAutonomyMenu.java:203-208`, the tooltip on the greyed Start item, in the same
shape. Both fall back to "wait for trains" whenever the count is zero — which is now reachable with
Start refused for a reason no number of waiting will clear. `UXR-C5`'s comment three lines above the
first of them states the rule it breaks: *"so a caller of this API is not told to wait for trains when
the real problem is an error count nothing will clear on its own"* (`:20236-20240`).

Neither is a DECISION site — `LayoutRightclickAutonomyMenu` decides on `canStartAutonomy()`
(`:194`), which is correct — so this is about what the operator is told, not about what is allowed.

### V31-C2 — the accessory keyboard is a third hand-switching door and asks neither half

**FIXED 2026-09-02** (see `SVN-B17`), and the status line saying otherwise is corrected 2026-09-03.

**FIXED 2026-09-02.**  See `SVN-B17` for the fix; this finding is what got it built, because it put the number on it - three surfaces, one unguarded, and the invariant `87b6c10a` stated was "one rule, asked by both doors".

**Status: open. Pre-existing, not introduced by these fixes** — filed because `87b6c10a`'s stated
invariant is "one rule, asked by both doors", and there are three surfaces that command an accessory
by hand.

`Layout.protectsAnOccupiedSquare` (`Layout.java:6157`) is asked by `MarklinRoute.heldReason`
(`MarklinRoute.java:475`) and by the diagram tile (`LayoutLabel.java:1385`). The switch keyboard is
the third, and it asks nothing at all — neither the locked-path half nor the protecting-signal half
(`TrainControlUI.java:19178-19201`):

```java
        new Thread(() ->
            {
                this.model.setAccessoryState(switchId, getKeyboardProtocol(), b.isSelected());
            }).start();
```

`setAccessoryState` goes straight to `a.turn()` / `a.straight()`
(`MarklinControlStation.java:3089-3115`). So the `AU-A2` case the diagram tile is guarded against —
throwing a turnout on a locked path while a train runs over it — is reachable from the keyboard
panel with no dialog and no log line.

I checked the remaining `execSwitching` call site (`LayoutLabel.java:296`) and it is the FEEDBACK
branch, which toggles a sensor rather than an accessory: not a fourth door.

### V31-C3 — the editor's checks are blind to `active`, and `D24-B5` gave that teeth

**Status: open.**

`1cfdf370` made the builder emit `active` for every square (`AutonomyBuilder.java:936-953`), and
`Layout.isPathClear`'s intermediate rule is deliberately not fenced behind `isAutoRunning`
(`Layout.java:2268-2287`) — so a plain square switched out of service now blocks every path through
it, manual routes included. That is the intent, and it is what the cross promises.

`AutonomyChecks` never reads `active`. Its reachability analysis walks the reduced graph
(`AutonomyChecks.java:1137-1143`):

```java
            reach.put(station.getTile(),
                reducer.reachableTiles(station.getTile(), mayTurn, mustTurn, barred));
```

`mayTurn`, `mustTurn` and `barred` are the three things it is told about; being out of service is not
among them. So switching off one plain square in the middle of a single-track section makes every
station beyond it unreachable at runtime, and `STATION_UNREACHABLE` / `STATION_REACHES_NOTHING`
(`:1161-1192`) do not fire. Before this round that gap was harmless for a non-station, because the
flag never reached the graph.

**Measured, and it is not live today.** The operator's frozen configuration
(`test/operator_layout/config/autonomy/configuration-Main.json`) carries exactly one point with
`active: false`, `2 - Bottom:8,7`, and mapping the page name through `setup.json`'s page table
(`1 → "2 - Bottom"`) puts it in the station list as `1:8,7`. So there are **zero** non-station squares
out of service on his diagram setup, and `D24-B5` changes nothing there. The exposure is on the legacy
import path, which is documented at `AutonomySession.java:519-525` — see `V31-D9`.

### V31-C4 — the new loader rule keys on the block, the harm it names keys on the sensor

**FIXED 2026-09-02, as the finding asked - the comment rather than the rule.**  The rule matches `claimHome` exactly and that is right; what was wrong was the sentence above it claiming to prevent a state it only partly prevents.  It now says what the block catches, what the sensor case is, and why widening this to the sensor would settle the open MT-187 question by the back door and settle it wrongly - by refusing homes on squares that are not one square.  Both smaller notes are in too: the search `break`s at the first copy so the message names a stable one, and the iteration-order property is written down rather than left to be noticed.

**Status: open.**

`8d1c17ca` added the square rule to `rebuildHomeStations` (`Layout.java:1155-1169`) and it matches
`claimHome`'s predicate exactly (`:1088-1091`) — that part is right, and is recorded under `V31-D6`.
Both use `Point.isSamePlaceAs`, which is the **block** (`Point.java:762-769`):

```java
        return this.block != null && this.block.equals(other.getBlock());
```

The failure the new comment and the new message describe is `sharesSection` answering IMPOSSIBLE
naming both locomotives (`Layout.java:1143-1146`). `sharesSection` keys on the **S88**
(`HomeStaging.java:1863-1868`):

```java
        return a != null && b != null && !a.equals(b)
            && a.isActive() && b.isActive()
            && a.getS88() != null && a.getS88().equals(b.getS88());
```

`AutonomyBuilder` emits `block` only where a square becomes more than one Point
(`AutonomyBuilder.java:841`), and its own comment says why the two differ: *"Genuinely different places
share a sensor on a real layout - a station, its approach guard and a reversing point can be three
Points on one feedback - so the sensor cannot say which Points are one square"* (`:838-840`). So two
homes on two DIFFERENT squares that share one sensor still reach `homeStations` intact, and still
produce the IMPOSSIBLE-naming-both state the new warning exists to prevent. Neither door refuses it.

This is the open sensor-versus-block decision `sharesSection`'s own javadoc records and defers to Adam
(`HomeStaging.java:1829-1857`, MT-187), so I am not asking for it to be settled here. What should be
corrected is the new comment at `Layout.java:1143-1146`, which asserts the rule prevents a state it
only partly prevents.

Two smaller notes on the same loop, neither worth its own finding: the search does not `break`, so
`sameSquare` names the LAST copy found rather than the first, which only affects which name the
message prints; and which of two assignments survives is still decided by iteration order over
`points.values()`, the same property the sibling rule above it records as having cost a defect
(`:1128-1133`).

---

## D — Checked and found sound

This is the part of a validation pass worth the most: it says which of the fixes can be trusted.

### V31-D1 — `SVN-A3`, the page-switch teardown, is correctly fixed and correctly pinned

The `finally` really did cover the wrong three statements: `layoutEditingComplete` does
`setEditLayoutEnabled(false)`, one field read, and `Thread.start()` (`TrainControlUI.java:19382-19398`).
The guarantee is now in `layoutRefreshComplete`, which runs `after.run()` from a `finally` around the
dozen statements (`:19397-19420`); the worker posts the EDT half from a `finally` around
`refreshLayouts()` (`:19411-19422`); and `layoutEditingCompleteThen` keeps an `AtomicBoolean`-guarded
`once` for a throw before there is a worker at all (`:19533-19560`). Ordering is now
refresh-then-continuation, which is what `arriveAt` needs.

The three-limb surface rule at `test/regression/testEditorSurfaceRules.java:2136-2175` reads the
bodies of all three methods, and I confirmed all three signatures it looks up exist verbatim and that
`indexOf("finally") < indexOf("invokeLater")` holds in the current `layoutEditingComplete` body.

The disposition originally cited `87b6c10a` for this; `D3F-C5` caught it and `2e83b737` corrected it
to `1cfdf370`, which is the commit that contains the change. The corrected line is
`docs/reviews/2026-09-01-week-of-commits-review.md:194`.

### V31-D2 — `measuredRoomToReverseInto` is a faithful lift, and both call sites ask the identical question

`Layout.java:6201-6221` is the whole of the old inline loop with the two outer conditions folded in,
and returns null in the three cases where the question does not arise. The `isPathClear` call site
(`:2405-2419`) keeps its now-redundant outer guards and is behaviourally identical to what it
replaced: `measured && length > room` became `measuredRoom != null && length > room`, and the `int
room = ... : 0` is dead inside the branch. The planner's call (`HomeStaging.java:1041-1043`) asks the
same expression, differing only in that it refuses with `continue` rather than by failing the path,
which is correct for a search.

The rule is genuinely pure — path edge lengths, `ending.isTerminus() || ending.isReversing()`, and
the locomotive — so there is nothing left for the two sides to mis-copy. I checked
`loc.getTrainLength() > room` for an unboxing NPE at both sites: `room != null` implies
`getTrainLength() != null` by the method's own first line, so neither can throw.

### V31-D3 — the rule went into the search and deliberately not into the proof, and that is right

`connected` (`HomeStaging.java:1700-1817`) has no room rule and should not: it is the impossibility
proof, and `D24-B1` / `SV2-A1` both cost a false IMPOSSIBLE by making it tighter than the search.
`firstClearRoute` is reached from both the plan search (`:752`) and the A* (`:862`), and from the
audit (`:629`), so every path a staging move can be built on now carries the rule. There is no third
place in the file that builds a leg.

### V31-D4 — the `seen`-ordering fix is correct, and `mustBackIn`'s `continue` below it is not the same bug

The room check now runs before the arrival is written into `seen` (`HomeStaging.java:1039-1051`),
which is what makes its `continue` mean what its comment says.

I specifically checked whether the `mustBackIn` refusal twelve lines below it
(`:1061-1069`) has the same defect, since it `continue`s AFTER `seen.put`. It does not: the `seen` key
is `next.getUniqueId() + (turned ? "/turned" : "/straight")` (`:1046`), and `mustBackIn`'s refusal
depends only on `turned`. A route that arrives turned lands under a different key, so it cannot be
pruned as dominated by the straight arrival that was just refused.

The test `testALongerApproachIsStillTriedWhenTheShortOneHasNoRoom`
(`test/core/testHomeStaging.java:3353`) asserts every fixture length as a precondition, repeats twenty
times against the neighbour shuffle, and restores the shared locomotive's length and reversibility in
a `finally`. Its fixture makes `HS B` a non-station specifically so the A* cannot stage through it —
which is the reason the two earlier fixtures could not see the defect, and is written down.

### V31-D5 — `protectsAnOccupiedSquare` is right to be unsynchronised, and both callers ask the same thing

`Layout.java:6157-6178` is `getPoints()` plus each Point's protecting-signal list. `getPoints()` is
documented as *"Live and unsynchronized"* (`:5916-5926`), with the reasoning for taking the copy back
out at `getEdges` above it, and the residual hazard filed as `DR-B7`. So this reader joins a class the
file already accepts, and the freeze `WK3-A1`/`DY3-A1` found is genuinely gone.

Its answer matches the mechanism it guards: `refreshOneSignal` decides occupancy the same way, with
`other.getCurrentLocomotive() != null` over `this.points.values()` (`:6057-6068`), and resolves names
through `getAccessoryByName` in the same order. The lifted body is `MarklinRoute.isOneOf` with the
name set flattened away; the private copy and the helper are both gone from
`MarklinRoute.java` (grep for `isOneOf` finds nothing in `src/` or `test/`).

Both callers gate on autonomy running — the route at `MarklinRoute.java:430`, the tile inside the
`else if (... isAutonomyRunning())` branch at `LayoutLabel.java:384` — and both now ask the aspect.

### V31-D6 — the aspect logic in `aboutToClearProtection` is correct for every tile kind that can reach it

`LayoutLabel.java:1376-1386` returns false when `accessory.isStraight()`. `isStraight()` is
`!switched` and `isGreen()` is the same expression (`base/Accessory.java:230-233`, `:248-251`), and a
signal tile's click is `doSwitch()`, which is `isStraight() ? turn() : straight()` (`:156-166`,
`LayoutDiagramComponent.java:128-131`). So "currently green, about to be made red" is correctly read
as harmless, matching `!rc.getSetting()` on the route side (`MarklinRoute.java:470-475`).

I worked the three-way branch through as well (`LayoutDiagramComponent.java:147-167`), since the tile
asks about `accessory2` too: in each of its three combinations, every drive that ends GREEN is one
that is currently turned, and every drive that ends RED or unchanged is one that is currently
straight. The prediction holds. The only kind it can misread is an uncoupler, whose click sets a fixed
state by raw-address parity (`:132-142`) — an uncoupler named as a platform's protecting signal is not
a configuration this model can reasonably be asked about.

### V31-D7 — `rebuildHomeStations`' square rule matches `claimHome`'s predicate exactly

Both iterate `homeStations.values()` and ask `taken.isSamePlaceAs(p)`, both after the
already-has-a-home test, in the same order (`Layout.java:1088-1091`, `:1155-1169`). The two
deliberately differ in what they DO — `claimHome` returns and leaves a free agent, the loader drops
the loser with a warning — and both differences are stated at the code with the reason. The message
`autolayout.warnHomeSquareAssignedTwice` takes three arguments and is passed `p`, `sameSquare`, `l` in
the order the string uses them. The residual is `V31-C4`.

### V31-D8 — `SVN-B13`'s test can fail, and the mutation it names kills it

`testTwoHomesOnOneSquareDoNotBothSurviveTheLoader` (`test/core/testHomeStaging.java:3269`) asserts as
a precondition that the two fixture points really do share a block, then counts homes on that square
and separately counts Points still carrying a `homeLoc`. Walking `rebuildHomeStations` against the
fixture by hand: the assignment pass keeps `HS W1` for `LOC_A` and drops `HS W2`, the positional pass
then homes `LOC_B` at `HS B`, giving 1 and 1. With the square rule removed both assignments survive
and the first assertion sees 2. The stated mutation is the one that kills it.

### V31-D9 — the `active` consumers, traced, and the legacy-import figures are correct

Every reader of `Point.isActive()` was enumerated and assessed:

- `Layout.isPathClear:2224` (edge endpoints, auto only), `:2280` (intermediates, always), `:2301`
  (destination, auto only) — the intended effect, and the whole point of `D24-B5`.
- `Layout:1703` — a locomotive standing on a shut square is skipped at Start with a log line, which is
  what "out of service" should mean.
- `Layout:3741`, `:3788`, `:6356` — destination selection, all conjoined with `isDestination()`, so a
  non-station cannot reach them.
- `HomeStaging.canEnter:1188` — the planner refuses to route through it, which keeps the planner and
  the runtime agreeing.
- `HomeStaging:133`, `:443`, `:616`, `:638`, `:928`, `:1688`, `:1808`, `:1866` — all either
  station-scoped or the deliberate audit exemptions, and all documented as such.
- The legacy import (`Layout.java:7450-7471`) carries the key unfiltered, and `AutonomySession`'s
  `CARRIED_SETTINGS` javadoc (`:501-526`) now gives the honest reason.
- `Layout.hasOnlyInactiveIncoming:1974` / `hasOnlyInactiveNeighbors:1989` — dead; no callers anywhere
  in `src/` or `test/`.
- The badge sites (`AutonomySession:4716`, `AutonomyEditorPanel:5996`/`:6002`, `TrainControlUI:4657`,
  `AutonomyEditorPanel:2460`/`:6266`) read the SESSION property rather than the built graph, so the
  builder change does not touch them.

**Both of the import comment's numbers check out.** `test/operator_layout/config/autonomy_legacy/autonomy.json`
has 62 points, of which 24 carry `active: false` and exactly 6 of those are not stations —
`LowerDown`, `LowerDownPre`, `LowerParkingInner`, `LowerParkingReverse`, `TunnelLongParkReverse`,
`TunnelParkReverse`. And the "restoration rather than regression" claim is right: `master`, which is
v2.8.1, carries the same unfenced intermediate rule (`errorInactiveIntermediatePoint` at
`Layout.java:1431` there), so those six blocked paths then too.

### V31-D10 — the badge fix is the smallest correct one, and moved no existing ink

`worthABadge` gained the third term and `shut` is now computed once and used for both the gate and the
two Badge arguments (`AutonomySession.java:4716-4745`). The Badge's `parking` argument is unchanged in
value (`shut || !isAutoDestination(tile)` was the old inline expression), and the badge is still gated
so a plain in-service sensor draws nothing. The editor badges every Point (`AutonomyEditorPanel:5992-6003`),
which is the asymmetry the runtime comment describes, so the two now agree on the case that matters.

`testAShutPlainSquareDrawsItsCrossOnTheRunningDiagram` and `testAShutPlainSquareReachesTheRunningGraph`
(`test/core/testAutonomyDiagramSession.java:904`, `:958`) each open with a control that would fail if
the fixture already had the property under test, and the second asks about **every** emitted copy.
`e6791631` fixed its control (`TS3-B1`) after finding it compared a base name against copy names.

### V31-D11 — the five "tests that could not fail" are now able to fail

- `TCX-B5` (`testAutonomyDiagramReversal.java:349-390`): the added `assertNotEquals` is a real control
  — if the builder ignored the reversible set, it fails while the original assertion goes on passing.
- `TCX-B6` (`testAutonomyDiagramMonitor.java:1061-1071`, helper at `:1207-1241`): `parking` and `shut`
  are now separate arguments. They differ in DRAWING, not only in colour — `isImpassable()` replaces
  the mark with a stroked cross (`TileAnnotation.java:1649-1673`) while both share `POINT_INACTIVE`
  (`:1531`) — so the new `assertNotEquals` on ink is a real discriminator. The claim that no existing
  ink count moved also holds: the three-argument helper now passes `parking = false` where it used to
  pass `shut`, and the colour expression is a disjunction, so a shut square's colour is unchanged.
- `TCX-B8` (`testHomeStaging.java:799-833`): `Plan.isPossible()` is `outcome == READY`
  (`HomeStaging.java:314-317`), so an ALREADY_HOME plan returns at `loadReturnToHomeTimetable`'s first
  line (`Layout.java:6676`) — before `setTimetable` (`:6701`) and before `timetableSequential = true`
  (`:6711`).
  Deleting that guard would set the flag and fail the new assertion, and the precondition assert makes
  the "before" state meaningful.
- `TCX-B9` (`testAutonomyDiagramSession.java:3793-3828`): moved onto a two-ended station with a floor
  of two, so the nested loop no longer compares a name with itself.
- `TCX-B13` (`testLayoutPickPath.java:463-502`): the control runs before `setReversing(true)`, so a
  regression that refuses everything fails it.

### V31-D12 — `SVN-B14` and `R28-C1` are complete

`RightClickFunctionMenu.openEditDialog` captures both slots before the autonomy block and restores
them in the `else` of the OK test (`:215-216`, `:291-305`). Both accessors are `Integer`-typed
(`base/Locomotive.java:1295-1332`), so restoring a null slot cannot throw; the `else` catches Escape
and the close box, which return `CLOSED_OPTION`.

Clear All Home Locomotives goes through `session.setHome(tile, null)` — the same door the per-square
menu uses (`AutonomyEditorPanel.java:6467-6503`), with the 2.8.1 confirmation and a no-op path that
answers in the hint line. `2e83b737` added the greying on the guard's own question (`:6231-6240`) and
kept the guard, which is the `OB-057`/`OB-090` shape done correctly.

### V31-D13 — every new message key is present in all eight bundles, correctly escaped, with the right arity

`route.ui.infoAlreadyRunning`, `layout.ui.confirmAccessoryProtecting`,
`autolayout.warnHomeSquareAssignedTwice`, `autosetup.ui.errorCannotBuildDetailOne`,
`autosetup.ui.infoHomesCleared`, `autosetup.ui.infoNoHomesToClear`,
`autolayout.ui.menuClearAllHomeLocomotives` and `autolayout.ui.confirmClearAllHomeLocomotives` each
appear exactly once in each of the eight `messages*.properties` files. Every non-ASCII character in
the translations is a `\uXXXX` escape. `warnHomeSquareAssignedTwice` uses `{0}`, `{1}`, `{2}` in all
eight and is called with three arguments.

### V31-D14 — the disposition corrections in `54a70c03` are both right, and the distinction they draw holds elsewhere

`FX2-3` was put to Adam as *"when you said 'sum the track segments leading up to it', did 'it' mean the
reversal, or the berth the train ends up standing in?"* (`docs/reviews/2026-09-01-fanout-index.md:219-226`),
and names four findings.

- **`SVN-B2`** — named by `FX2-3`; closing it with its siblings is right.
- **`TCX-B2`** — asks about the editor NOTICE, not the rule. `FX2-3`'s own text lists it under *"the
  rest still holds"* rather than under what was decided. Reopening it was right, and `TCX-B3` and
  `SVN-B1`, named in the same sentence, were correctly left with no disposition at all.
- **`SVN-B3`** — closed by `FX2-3` although not named by it. I checked its one-sentence question
  (`week-of-commits:489-490`): *"should the room behind a reversing train be the whole route it came in
  over, or only the last N segments it will physically stand on?"* That is `FX2-3`'s question verbatim,
  so the closure is honest and the distinction against `TCX-B2` is drawn in the right place.
- **`RTG-B2`**, **`TCX-A1`**, **last-day `B2`** — all three are limbs `FX2-3` put. Last-day `B2`'s
  final paragraph raises the notice-versus-guard question that `TCX-B2` was reopened for, but it
  explicitly refers it to `D24-C7` rather than claiming it, and `D24-C7` is open. Marginal, and I
  think correctly marginal.

### V31-D15 — the remaining dispositions from `cf048f9b`, spot-checked against the artefacts they name

- `SVN-B6` / last-day `B5` / last-day `C9` — the badge and builder changes are as described (D10).
- `SVN-B4` and `TCX-A2` — the same finding by two passes, both accurately described (D2, D3).
- `SVN-B10` — the start door asks `hasErrors()` (`TrainControlUI.java:5183`) and the LOAD door asks
  `hasBlockingProblems()` (`AutonomyViewerPanel.java:782`), which is what the new javadoc claims
  (`AutonomySession.java:3572-3575`).
- `SVN-B16` — the private copy and `isOneOf` are gone; both doors ask the shared rule (D5).
- last-day `A1` — `testTrainsComeHomeToTheirPlatforms` exists (`test/core/testTrainsComeHomeToTheirPlatforms.java:71`)
  and `DELIBERATELY_OUT` is back to its single `testAutoDetect` entry
  (`test/regression/testEveryTestIsInTheBattery.java:40-44`).
- `R28-C1` — the button exists beside Name Everything with the 2.8.1 confirmation (D12).
  **`R28`, not `RGN` (REL-C14).** The prefix was a typo, and `RGN-C1` is a different, open
  finding: auto-save on exit is forced on and its checkbox hidden, so `autonomy.json` is
  rewritten for somebody who turned it off. It was never dispositioned here.
- `RTG-A1` closed by `FX2-4` — the mechanism question ("builder refuses or editor warns") WAS answered,
  by Adam on 2026-09-02: *"we need a warning for instances like the previous version of this"*, quoted
  at `AutonomyChecks.checkBadCopies` (`:738-747`), which is the per-copy check the disposition claims.
- last-day `C11` — OB-167 has its receipt row (`docs/manual-tests/issues.md:364`).
- `R28-B2` withdrawn — `AutonomyMenu.java:326` carries the ungated Export item.
  **`R28`, not `RGN` (REL-C14).** `RGN-B2` is a different finding and is a B about what a route
  does on the railway - an s88-fired route that meets a conflict sets none of its accessories
  and runs everything else. Nothing about it was withdrawn here. Its three descriptions were
  brought into agreement on 2026-09-03; the behaviour is a question for Adam.

### V31-D16 — things that looked wrong and are not

- **The tile's protecting-signal question is skipped with the power off.** `LayoutLabel.java:345-384`
  puts the whole autonomy branch in an `else if` after the power-off dialog. That is pre-existing
  structure shared with the locked-path half, and autonomy running with track power off is not a state
  the rest of the system can be in.
- **`heldReason` resolves by address and protocol, `protectsAnOccupiedSquare` by name.** They meet at
  `Accessory` identity in the database, and `getAccessoryByName` is the same fallback both used before.
- **`AutonomyOverlayToggle` reading the count for its BADGE.** Only the `fixing` decision at `:344` is
  wrong (`V31-B1`); the count beside it is a count and belongs there.
- **`issues.md`'s OB-167 row records Filed 2026-09-02 for an item filed 2026-08-31 23:20.** Not filed
  as a finding: the column is used inconsistently elsewhere in the same table (FR-054's row says
  2026-09-01 for an item filed 2026-08-31 23:25), so it appears to mean "picked up on" rather than
  "raised on", and one reading makes both rows right.
- **`requestStartAutonomy` bypassing `canStartAutonomy`.** It asks only the button's enabled state and
  then calls `startAutonomyActionPerformed`, which passes through `refuseAutonomyStartWhileBroken`
  (`TrainControlUI.java:20755`). Guarded; only its message is wrong (`V31-C1`).

---

## What this pass did not cover

The five commits' changes to `AutonomySession.tilesWithAHome` were read but not traced through every
caller of the JSON store; the `TCX-B*` test fixtures were reasoned about rather than executed; and
nothing in `1cfdf370`'s `testSwitchingToACentralStationLayout` window count (17 → 18) was verified
beyond reading the reason given. No claim above depends on any of those.
