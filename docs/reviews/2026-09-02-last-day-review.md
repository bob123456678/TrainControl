# The last day of commits, read line by line

**Status:** open

**Prefix for citing these findings elsewhere:** `DY3`

**Reviewed:** branch `autonomy-diagram-r0` at `cf048f9b`, on 2026-09-02. Scope is the 37 commits of the
preceding 24 hours, `a33b9ae1` through `cf048f9b`. This is the narrowest of the three commit-range
passes in this fan-out, so every source diff in the window was read in full rather than sampled; see
[D11](#d11-what-this-pass-did-not-cover) for what that does and does not mean.

**No tests were run.** Every claim below is from reading the code and the real configuration in
`test/operator_layout/config/`. Where a claim would have benefited from a measurement, it says so.

---

## Summary

| # | Severity | One line | Where |
|---|---|---|---|
| [A1](#a1) | High | `Layout.protectsAnOccupiedSquare` is `synchronized` and is called from the event thread beside `getActiveAccs`, which is documented as deliberately unsynchronised for that exact reason — the whole-window freeze `IAR-B2` removed is back, and route execution now waits on the same monitor | `87b6c10a` |
| [B1](#b1) | Medium | The lifted protecting-signal rule lost its aspect precondition at the diagram door: setting a signal to **red** by hand now raises a warning against the protective act, which the route door explicitly does not | `87b6c10a` |
| [C1](#c1) | Low | `active` on a plain square now reaches the graph from a **legacy import** too. Six of the twenty-four inactive points in the frozen legacy fixture are non-stations, and each becomes an unconditional block on every path through it, manual routes included | `1cfdf370` |
| [C2](#c2) | Low | Two documents still say `active` is ignored on a non-station, and one of them is the legacy import's own reason for carrying the key unfiltered | `1cfdf370` |
| [C3](#c3) | Low | `executeRoute` is not the funnel its new guard assumes — the diagram's route tile and the s88 trigger do not pass through it — and the harm the commit describes cannot happen, because `Route.setExecuting()` already refuses a second concurrent run | `87b6c10a` |
| [C4](#c4) | Low | `check()` now performs three full configuration builds and six builder constructions per call, on the event thread, and the start door calls it twice | `409d4ce8`, `06516f38`, `87b6c10a` |
| [C5](#c5) | Low | `clearAllHomes` re-derives the station index once per square — sixty-two builder constructions on one button press — where the bulk direction setter batches for exactly that reason | `1cfdf370` |
| [C6](#c6) | Low | The Clear All Home Locomotives button does not ask its own predicate: no greying, no tooltip, unlike the sibling it was placed beside | `1cfdf370` |
| [C7](#c7) | Low | The start door's new fallback message always says "one thing", where the load door it borrows the key from counts | `87b6c10a` |
| [C8](#c8) | Low | `ParkingTrack12` in the frozen fixture is a parking berth that is *also* `active: false` — the state ten other berths were converted out of on Adam's ruling | `409d4ce8` (fixture) |
| [C9](#c9) | Low | `HomeStaging.connected`'s javadoc still says `isPathClear` refuses a terminus to a non-reversible locomotive; that clause was deleted on 2026-09-01 | pre-window drift |
| [C10](#c10) | Low | The only comment in `build.xml` that says a battery failure is expected still describes the parking-berth arrangement the test was retargeted away from an hour later | `188cc1cf`, `7931e11a` |
| [D1](#d1-svn-a3-the-page-switch-ordering-traced-end-to-end) | Not a defect | `SVN-A3`: the page-switch ordering, traced end to end. Correct. |
| [D2](#d2-a-withdrawn-c-the-two-badge-sites-do-not-disagree-on-a-non-station) | Withdrawn C | The two Badge sites do not disagree on a non-station |
| [D3](#d3-svn-b13-asks-the-right-predicate-and-is-reachable-through-one-door-only) | Not a defect | `SVN-B13` asks `block`, not the s88, and is reachable through one door only |
| [D4](#d4-refreshonesignal-and-protectsanoccupiedsquare-ask-the-same-occupancy-question) | Not a defect | The copy-vs-square trap does not bite the new shared rule |
| [D5](#d5-tcx-a2-the-extraction-is-equivalent-at-both-call-sites) | Not a defect | `TCX-A2`: the extraction is equivalent at both call sites, and `connected` is correctly left out |
| [D6](#d6-the-five-strengthened-tests-can-now-fail) | Not a defect | The five strengthened tests can now fail |
| [D7](#d7-every-new-bundle-key-is-in-all-eight-bundles-and-ascii-only) | Not a defect | Every new bundle key is in all eight bundles, ASCII-only |
| [D8](#d8-clearallhomes-does-not-write-to-disk) | Not a defect | `clearAllHomes` does not write to disk, so its "Cancel undoes it" is true |
| [D9](#d9-svn-b14-cancel-really-does-restore-both-slots) | Not a defect | `SVN-B14`: Cancel really does restore both slots |
| [D10](#d10-the-builder-change-is-inert-on-adams-current-configuration) | Not a defect | The builder change is inert on Adam's current configuration |
| [D11](#d11-what-this-pass-did-not-cover) | Scope | What this pass did not cover |

---

## A

### A1

**`Layout.protectsAnOccupiedSquare` is `synchronized`, and the event thread now waits on the layout
monitor in the one branch `IAR-B2` was written about.**

**FIXED 2026-09-02 (`e6791631`).**  Found independently as `WK3-A1`.  `Layout.protectsAnOccupiedSquare` is no longer `synchronized`, and the reason is written at the method: it is called from the event thread four lines below `getActiveAccs`, whose own javadoc records that exact call freezing the window - `configureAndLockPath` holds the monitor across per-command sleeps, so a click on a signal could wait behind a whole route being thrown, Stop included.  Raised against `87b6c10a` (`SVN-B16`), which is where the lift happened.

`87b6c10a` lifted the protecting-signal half of `MarklinRoute.heldReason` onto `Layout` so both doors
could ask it. The lift is right. The modifier is not.

`src/org/traincontrol/automation/Layout.java:6134`

```java
    synchronized public boolean protectsAnOccupiedSquare(Accessory accessory)
    {
        if (accessory == null || this.control == null) return false;

        for (Point point : this.getPoints())
        {
            if (point.getCurrentLocomotive() == null) continue;
```

The new call site is `src/org/traincontrol/gui/LayoutLabel.java:400`, inside a
`SwingUtilities.invokeLater` opened at line 341 — so it runs **on the event thread** — and it sits
fourteen lines below the call it was added beside:

```java
386:  Collection<Accessory> activeAccs = tcUI.getModel().getAutoLayout().getActiveAccs();
...
400:  boolean protecting =
401:      tcUI.getModel().getAutoLayout().protectsAnOccupiedSquare(
402:          c.getAccessory())
```

`getActiveAccs` carries this javadoc, at `src/org/traincontrol/automation/Layout.java:851`:

```java
    /**
     * Returns all accessories along active routes
     *
     * NOT synchronized, deliberately (IAR-B2).
     *
     * This is read from the EVENT THREAD, by the layout label's click handler, and only in the branch
     * that runs while autonomy is going - which is exactly when `configureAndLockPath` holds this
     * object's monitor across its per-command sleeps. So throwing a turnout by hand during a run froze
     * the whole window, Stop included, for the length of the configuration phase. OB-079 fixed three
     * sites of this shape and its list was one short.
```

Every clause of that paragraph is true of the new method. Same thread, same branch — the enclosing
`else if` at `LayoutLabel.java:384` is `hasAutoLayout() && isAutonomyRunning()` — same monitor, and the
two calls are consecutive statements. `OB-079`'s list is one short again.

That the monitor is held for seconds is not inference; the class says so at
`src/org/traincontrol/automation/Layout.java:576`:

```java
     * configureAndLockPath holds the layout monitor across its whole lock loop - deliberately, because
     * claiming a path has to be atomic - and that loop sleeps CONFIGURE_SLEEP per edge and again per
     * accessory inside configureEdge, so it is held for seconds on a long path.
```

**And there is a second consumer, which is worse.** `MarklinRoute.heldReason` is called once per
accessory command inside the execution loop, `src/org/traincontrol/marklin/MarklinRoute.java:641`:

```java
    String[] now = override ? null : heldReason(rc);
```

and `heldReason` reaches the new synchronized method at line 475:

```java
    if (!rc.getSetting() && this.network.getAutoLayout().protectsAnOccupiedSquare(accessory))
```

So a route now blocks on the layout monitor before every GREEN/STRAIGHT accessory command. `6f729027`
took the *dialog* out of the emergency-stop path an hour earlier — "an emergency stop is never a
question" — but `heldReason` itself is not skipped for a stop-carrying route, only
`conflictingAccessoryAndReason` and the midway prompt are. A safety route whose trap point is set
STRAIGHT before its stop command now waits on a dispatch that is part way through configuring a path.
That is the s88 door, with nobody present.

Before this commit neither caller took the monitor: the route walked `getAutoLayout().getPoints()`
directly, and `getPoints()` is not synchronized (`Layout.java:5916`).

**What I did not establish.** I found no `invokeAndWait` on any path that runs while the layout monitor
is held — `showAutonomyAlert` uses `invokeLater` (`TrainControlUI.java:24434`) — so I am claiming a
stall, not a deadlock. I could not measure the stall, because this pass runs no JVM.

The smaller fix is to drop `synchronized` and snapshot, which is what `getActiveAccs` does and
documents: `activeLocomotives` is concurrent, and here `this.points` would need
`new ArrayList<>(this.getPoints())` or the same weak-consistency argument written down. Note that the
old implementation in `MarklinRoute` iterated `getPoints()` with no lock at all, so unsynchronised is
not a regression against what shipped — it is what shipped.

---

## B

### B1

**The protecting-signal rule lost its aspect precondition when it was lifted, so the diagram door now
warns against setting a signal to danger.**

**FIXED 2026-09-02 (`e6791631`).**  Same finding as `WK3-B1` and `D3F-C4`; see the note under `D3F-C4` in the three-days review for the fix and its cover.  Raised against `87b6c10a` (`SVN-B16`), which is where the aspect was dropped in the lift.

The route door asks two things. `src/org/traincontrol/marklin/MarklinRoute.java:460`:

```java
        // The ASPECT matters here, and it does not for the case above.
        //
        // A turnout on a locked path must not move at all - any position but the one the path
        // configured is wrong for the train crossing it. A protecting signal is different: the only
        // harmful command is the one that turns protection OFF. A route setting it red is doing
        // exactly what protection would do, and refusing that was pure over-strictness - it fired for
        // every route touching any signal of any platform with a train parked at it, and because
        // accessories are skipped as a group, it took the whole route's turnouts with it. Found by
        // review, which reproduced it with no path locked anywhere.
        //
        // `getSetting()` is true for RED and TURN, false for GREEN and STRAIGHT (Accessory.java).
475:    if (!rc.getSetting() && this.network.getAutoLayout().protectsAnOccupiedSquare(accessory))
```

The diagram door asks one. `src/org/traincontrol/gui/LayoutLabel.java:400`:

```java
        boolean protecting =
            tcUI.getModel().getAutoLayout().protectsAnOccupiedSquare(
                c.getAccessory())
            || (c.getAccessory2() != null
                && tcUI.getModel().getAutoLayout()
                    .protectsAnOccupiedSquare(c.getAccessory2()));
```

There is no `getSetting()` term, and none can be inherited from `protectsAnOccupiedSquare`, whose whole
job is now the occupancy half. The click toggles the accessory, so the direction it is about to move in
is knowable and is not asked.

The consequence is the message. `LayoutLabel.java:418`:

```java
        protecting
            ? I18n.t("layout.ui.confirmAccessoryProtecting")
            : I18n.t("layout.ui.confirmAccessoryActiveRoute"),
```

and `messages.properties:1207`:

```
layout.ui.confirmAccessoryProtecting=This signal is protecting a platform a train is standing at.  Switch it anyway?
```

Clicking that signal to **red**, with a train at the platform and autonomy running, is the protective
act — it is precisely what `refreshOneSignal` would command (`Layout.java:6105`). The operator is asked
whether they really want to do it, in wording that implies they should not. The route door was
explicitly relieved of this refusal after a review reproduced it, and the reason recorded there —
"refusing that was pure over-strictness" — applies unchanged to the hand door.

It also undercuts the commit's own claim. "One rule now, in the one place both can reach"
(`MarklinRoute.java:471`) is true of `protectsAnOccupiedSquare` and false of the question the two doors
build out of it: one asks *is this signal protecting an occupied platform and about to be turned off*,
the other asks *is this signal protecting an occupied platform*.

Rated B rather than C because the doors disagree in the direction that matters — Adam's standing
position is that a check with no way past it is worse than no check, and this one prompts against the
safe direction — and because the divergence is invisible from either file alone. It is not A: the
dialog has an OK, so nothing is refused outright.

The surface test added with it (`test/regression/testEditorSurfaceRules.java`,
`testSwitchingAnAccessoryByHandAsksAboutProtectingSignals`) asserts only that both files contain the
string `protectsAnOccupiedSquare`, so it passes with the precondition missing on either side.

---

## C

### C1

**`active` on a plain square now reaches the graph from a legacy import as well, and the legacy fixture
has six such points.**

**Status:** open. `1cfdf370` (`D24-B5`).

The builder no longer drops `active` for a non-station
(`src/org/traincontrol/automationui/AutonomyBuilder.java:936-953`), which is correct for the editor's
**Out of service** — that is the whole finding. But `extras` is not fed only by the editor. The legacy
import copies the key verbatim, `src/org/traincontrol/automationui/AutonomySession.java:516`:

```java
    private static final List<String> CARRIED_SETTINGS =
        Arrays.asList("priority", "speedMultiplier", "excludedLocs", "active", "maxTrainLength");
```

Adam's own frozen legacy graph carries twenty-four points with `"active": false`
(`test/operator_layout/config/autonomy_legacy/autonomy.json`). Six of them are **not stations**:

| Point | station | s88 | reversing |
|---|---|---|---|
| `LowerDown` | false | 2001 | – |
| `LowerDownPre` | false | 1001 | – |
| `LowerParkingInner` | false | – | – |
| `LowerParkingReverse` | false | 1003 | true |
| `TunnelLongParkReverse` | false | 2013 | true |
| `TunnelParkReverse` | false | – | true |

Before `1cfdf370` those six were dropped at the builder and the built graph left them active. After it
they are emitted, `parseAuto` applies them (`Layout.java:7427-7448`), and two rules then bite that did
not before:

- `Layout.isPathClear:2278-2287`, the intermediate rule, which is **not** fenced behind
  `isAutoRunning()` — its own comment says so — so a manually chosen route through one of those squares
  is refused as well as an autonomy one;
- `HomeStaging.canEnter:1174`, `if (!p.isActive()) return false;`, so Return Home will not route
  through them either.

Four of the six sit on parking spurs. Whether that is the right answer is a ruling rather than a bug —
in v2.8.1 the raw `autonomy.json` was loaded straight into `parseAuto` and those points blocked paths
then too, so the import is arguably being *restored* to fidelity. What makes it worth writing down is
that the change was justified entirely by the editor's menu, the import path is not mentioned in the
commit, and the import's own documentation still gives the opposite rule (see [C2](#c2)).

Not reachable on Adam's machine today — his configuration has migrated (see
[D10](#d10-the-builder-change-is-inert-on-adams-current-configuration)) — so this is about the upgrade
path for a 2.8.1 user, which is exactly who `importLegacy` exists for.

### C2

**Two javadocs still say `active` is ignored on a non-station.**

**Status:** open. `1cfdf370`.

`src/org/traincontrol/automation/Point.java:198`:

```java
    /**
     * Returns if the point is active.
     * Active means the point will be selected by autonomous logic
     * Ignored for non-stations
     * @return
     */
    public boolean isActive()
```

`isPathClear` has applied `isActive()` to intermediate points — which are overwhelmingly non-stations —
since `4e5fde8b` (2026-08-02), so this line was already loose. `1cfdf370` makes it plainly false: the
whole point of the change is that a non-station's `active` now decides whether trains may pass.

`src/org/traincontrol/automationui/AutonomySession.java:513`, the legacy import's key list:

```java
     *   active            a station's own switch.  The build ignores it on anything else, which is
     *                     why it is carried as given rather than filtered here.
```

Both halves are now wrong, and the second half is a *reason* — it is why the key is carried unfiltered,
which is the mechanism [C1](#c1) is about. This is the shape `CMT-B1` and `CMT-B2` were: a summary that
outlived the code it summarises, sitting where the next reader will trust it instead of reading on.

### C3

**`executeRoute` is not the funnel the new guard assumes, and the harm the commit describes cannot
happen.**

**Status:** open. `87b6c10a` (`SVN-B7`).

Two claims in the fix are checkable and neither holds.

**The harm.** `src/org/traincontrol/gui/TrainControlUI.java:16103`:

```java
        // NOT TWICE AT ONCE (SVN-B7).
        //
        // The comment below has always said every door comes through here, and every door does - but
        // the guard was not in here.  It was on the play button alone, so clicking the same route's
        // row and confirming, or right-clicking it and choosing Execute, started a second run of a
        // route that was already running: two threads throwing the same accessories, each unlocking
        // what the other had locked.
```

A second run cannot start. `src/org/traincontrol/marklin/MarklinRoute.java:503` wraps the whole of
`execRoute`'s body in `if (this.setExecuting())`, and `src/org/traincontrol/base/Route.java:115` is an
atomic compare-and-set:

```java
    synchronized public boolean setExecuting()
    {
        if (this.isExecuting)
        {
            return false;
        }

        this.isExecuting = true;

        return true;
    }
```

The second thread therefore does nothing at all — not one accessory, not one unlock. What the missing
guard actually cost is smaller and still real: the row-click door put a conflict dialog on screen for a
run that would then be silently discarded, and `routeStarted` re-armed the spinner for it.

**The funnel.** Two doors run a route without passing through `executeRoute`:

- `src/org/traincontrol/base/LayoutDiagramComponent.java:183` — a click on a route tile on the track
  diagram: `this.route.execRoute(false);`
- `src/org/traincontrol/gui/LayoutLabel.java:554` — the same click after an override:
  `onTile.execRouteOverridingConflicts();`

and the s88 trigger reaches `MarklinRoute.java:213` (`this.execRoute(true)`) directly. None of them
consults `routesExecuting`, none of them sets it, so none of them greys the route list's play button
while it runs, and the surface test added with the fix
(`testEveryDoorThatRunsARouteAsksIfItIsAlreadyRunning`) checks only `executeRoute` and
`RouteListMouseClicked`.

Rated C rather than B precisely because of the first half: `setExecuting` covers the doors the guard
does not reach, so the guard is defence in depth. The reason to record it is that the comment now
asserts a hazard the model already prevents, which is the sentence a future author would rely on if
they came to tidy `setExecuting` away.

### C4

**`check()` now costs three full configuration builds and six builder constructions per call, on the
event thread, and the start door calls it twice.**

**Status:** open. `409d4ce8`, `06516f38`, amplified by `87b6c10a`.

`src/org/traincontrol/automationui/AutonomySession.java:3524`:

```java
            destinationCopiesWithNoWayOut(), destinationCopiesWithNoWayIn(),
            destinationCopiesReachingNoStation());
```

Each of the three runs `builtForInspection()` — a whole `AutonomyBuilder.build()` — and then a second
`builder(null)` for `tilesByName()` (lines 1999, 2034, 2119, 2149). And `builder(...)` is not cheap on
its own, `AutonomySession.java:2272`:

```java
    public AutonomyBuilder builder(AutonomyBuilder.Globals globals)
    {
        return new AutonomyBuilder(reducer, globals)
            .withPointExtras(pointExtras())
            .withReversibleTiles(reversibleTiles())
            .withMandatoryTurns(mandatoryTurnTiles())
            .withParkingTiles(parkingTiles())
            .withBarredArrivals(barredArrivals())
            .withProtectingSignals(protectingSignalNames())
            .withBlockingPoints(store.getBlockingPoints());
    }
```

— six walks of the reduced graph per construction.

This matters because of what `check()` is already documented as costing, and where it is called from.
`src/org/traincontrol/gui/LayoutRightclickAutonomyMenu.java:184`:

```java
                // Asked ONCE each, and this is not tidiness (LD-C6).
                //
                // Both of these reach AutonomySession.check(), which is not cached: it rebuilds the
                // termini and turn-around sets over every point in the graph. Written out four times
                // - canStartAutonomy twice, autonomyErrorCount twice - that is four full walks of the
                // railway on the event thread, every time somebody right-clicks a station, to decide
                // one enabled flag and one tooltip.
```

`LD-C6` was raised for four walks. One `check()` is now three builds plus six builders plus everything
it did before, and it still runs on every station right-click.

`87b6c10a` then doubles it at the start door, `src/org/traincontrol/gui/TrainControlUI.java:5183`:

```java
        if (!getAutonomySession().hasErrors()) return false;

        int errors = getAutonomySession().errorCount();
```

`hasErrors()` is `hasBlockingProblems() || errorCount() > 0` (`AutonomySession.java:3574`), so pressing
Start runs `check()` twice — six configuration builds — where before it ran one.

**Unmeasured.** I cannot time it here. The fix is mechanical either way: hoist one
`builtForInspection()` and one `tilesByName()` for all three copy checks, and hold `errorCount()` in a
local at the start door instead of asking twice.

### C5

**`clearAllHomes` re-derives the station index once per square, where the bulk direction setter batches
for exactly that reason.**

**Status:** open. `1cfdf370` (`R28-C1`).

`src/org/traincontrol/gui/AutonomyEditorPanel.java:6471`, in `clearAllHomes` (declared at 6450):

```java
        for (TileKey tile : homed)
        {
            session.setHome(tile, null);
        }
```

`setHome` → `setPointProperty` → `src/org/traincontrol/automationui/AutonomySession.java:4142`:

```java
        deriveStationIndex();
```

which is `AutonomySession.java:2497`:

```java
    private StationIndex deriveStationIndex()
    {
        StationIndex derived = reducer == null ? StationIndex.EMPTY : new StationIndex(builder(null));
```

— a full builder construction, on the event thread, per home cleared. The feature exists because
clearing them one at a time is "up to sixty-two" right-clicks
(`AutonomySession.java:4074-4082`, the javadoc on `tilesWithAHome`), so sixty-two is the number of
builder constructions one press of the new button costs.

The pattern is already named in this class, at `AutonomySession.java:3619`:

```java
        // Recorded first and re-derived once at the end.  Going through the single-tile setter would
        // rebuild the entire graph per route - forty tiles meaning forty full rebuilds on the event
```

The javadoc's reason for going through `session.setHome` rather than writing the property directly is
good and should stand — "a second way of clearing a home is exactly how the two doors would come to
disagree later". What is missing is the bulk overload the direction setter already demonstrates.

### C6

**The Clear All Home Locomotives button does not ask its own predicate.**

**Status:** open. `1cfdf370` (`R28-C1`).

`src/org/traincontrol/gui/AutonomyEditorPanel.java:251` and `:480`:

```java
    // Offered only while something is still unnamed, which is the only time it does anything
    private JButton nameAll;

    private JButton clearHomes;
...
        clearHomes = new JButton(I18n.t("autolayout.ui.menuClearAllHomeLocomotives"));
        clearHomes.addActionListener(e -> clearAllHomes());
```

`nameAll` greys itself and explains, `AutonomyEditorPanel.java:6217`:

```java
        if (nameAll != null)
        {
            nameAll.setEnabled(unnamed > 0 && !ignored);
```

`clearHomes` is never touched by `refresh()` — the only mentions of it in the file are lines 253, 480,
481, 482, 505 and 515 — so it is always live and has no tooltip, where every other button in that
column has one. The handler's own predicate is `session.tilesWithAHome().isEmpty()`, which is exactly
what the enabled state should be.

The commit's own justification for its other change quotes the rule this misses: "the guard and the
affordance ask one question, which is the OB-057 and OB-090 shape". The chosen behaviour — answering
"nothing to clear" in the hint line rather than in a dialog — is defensible and can stay; greying is
what is missing beside it.

### C7

**The start door's new fallback message always says "one thing", where the door it borrows the key from
counts.**

**Status:** open. `87b6c10a` (`SVN-B10`).

`src/org/traincontrol/gui/TrainControlUI.java:5187`:

```java
        JOptionPane.showMessageDialog(this, errors > 0
            ? I18n.f("autolayout.ui.errorCannotStartWithErrors", errors)
            : I18n.t("autosetup.ui.errorCannotBuildDetailOne"));
```

`errorCannotBuildDetailOne` is the singular of a pair, and the load door picks between them,
`src/org/traincontrol/gui/AutonomyViewerPanel.java:791`:

```java
                // One of these is far and away the commonest case, and "1 things" in the one message
                // a user meets before their railway will run is not the first impression to make.
                int blocking = countBlocking();

                JOptionPane.showMessageDialog(ui, blocking == 1
                    ? I18n.t("autosetup.ui.errorCannotBuildDetailOne")
                    : I18n.f("autosetup.ui.errorCannotBuildDetail", blocking));
```

The start door takes the singular unconditionally, so with three blocking problems it says one has to
be dealt with.

**Nearly unreachable, and the finding says so.** `AutonomyChecks.java:404` copies every blocking
problem in as an `ERROR` finding, so `hasBlockingProblems()` normally implies `errorCount() > 0` and the
`errors > 0` limb is taken. The new limb is reached only when `check()` returns nothing while
`hasBlockingProblems()` is true, which `AutonomySession.java:3405` says is `graph != null &&
reducer == null`. That is the case the disjunction was added for, so the branch is not dead — it is
narrow.

### C8

**`ParkingTrack12` in the frozen fixture is a parking berth that is also out of service.**

**Status:** open — needs Adam's ruling, not a code change. Fixture as of `409d4ce8`.

**Put to Adam as [MT-260](../manual-tests/tests.md#mt-260) (2026-09-02).**  Still open - collecting it is not answering it.

`test/operator_layout/config/autonomy/configuration-Main.json` holds exactly one `"active": false`:

```json
    "2 - Bottom:8,7": {
      "mustReverse": true,
      "maxTrainLength": 0,
      "active": false,
      "autoDestination": false
    },
```

`2 - Bottom` is page id `1` (`test/operator_layout/config/gleisbild.cs2`), and
`test/operator_layout/config/autonomy/setup.json:139` names `"1:8,7"` as `ParkingTrack12`; line 13 lists
it among the stations. Twenty other squares in that file carry `autoDestination: false`; this is the
only one that carries both.

`2797d216` converted ten berths — "TunnelLongPark and ParkingTrack4 through 12" — out of exactly this
state on Adam's own words: *"those stations are closed because I don't want them chosen in full
autonomy. but trains can always return to them."* The commit records what `active: false` costs there:
`plan()` declares a train standing on such a square stranded, and `canRest` will not let one end there.

`ParkingTrack12` came back closed in the re-cut of his diagram captured by `409d4ce8` — the same commit
that removed its `parking: true` and added `autoDestination: false`, which is what the conversion looks
like. So this is either a berth he deliberately took out of service or the conversion catching one
square half way. Worth one question rather than a code change; recorded because it is the one square in
the fixture where the two flags say opposite things.

### C9

**`HomeStaging.connected`'s javadoc still says `isPathClear` refuses a terminus to a non-reversible
locomotive.**

**Status:** open. Predates this window — introduced by `fbc19cb9`, invalidated by `280ff08b` — found
while reading `975f157d`, which touches the method below it.

`src/org/traincontrol/automation/HomeStaging.java:1696`:

```java
     * The runtime already insists - `Layout.isPathClear` refuses a terminus to a locomotive that
     * cannot reverse unless the path passes a reversing point, so it arrives already turned, backs in,
     * and leaves forwards.
```

`Layout.isPathClear` says the opposite in as many words, `Layout.java:2312`:

```java
        // NO TERMINUS RULE HERE (Adam, 2026-09-01).
        //
        // "In manual operation, non reversing trains must be able to back into a terminus if the graph
        // makes that possible.  Otherwise we'd need a third kind of station."
```

The rule the paragraph describes now lives in selection (`pickPath`) and in `HomeStaging`'s own
`mustBackIn`, and `Layout.java:2327` says so. `connected`'s behaviour is unaffected — it carries its own
rule — but the sentence is the reason a reader is given for the seed the paragraph goes on to justify,
and it is now the wrong reason. Included in this document rather than left for the comments pass because
`975f157d` and `934018f3` both edit within forty lines of it.

### C10

**The one comment that tells a reader a battery failure is expected describes a test that no longer
exists under that name or that subject.**

**Status:** open. `188cc1cf` added it, `7931e11a` renamed the class beneath it an hour later.

`build.xml:265`:

```xml
        <!-- RED ON PURPOSE, Adam 2026-09-01: "ok, so that test should then be red."  Return Home
             finds no arrangement for his five-train parking berths, so nothing moves when it is
             pressed.  It was excused from the battery on the grounds that a permanently red run
             costs more than the test earns; that was the wrong trade.  It comes out when Return
             Home stages this arrangement, not before. -->
        <test-one-class class="testTrainsComeHomeToTheirPlatforms"/>
```

`7931e11a` changed the class name on line 270 and left the five lines above it alone — but that commit
is precisely the one that took the test *off* the berths:

> RETARGETED onto five plain through platforms - BottomMainA, TopMainR1, TopMainR2, Tunnel and
> LowerFront. No parking flag, no compulsory reversal, nothing out of service, and a floor assertion
> that says so, because a terminus creeping into that list would turn this quietly back into the
> question it was moved away from.

So the comment names the arrangement the test was moved away from, and its exit condition — "It comes
out when Return Home stages this arrangement" — points at an arrangement the class no longer builds.
Two later commits (`878fb8f4`, `56c6080e`) changed the fixture again.

This is the only place in the repository that tells somebody a red battery is expected rather than
broken, and it is the first thing a person will read when the battery goes red. `testEveryTestIsInTheBattery`'s
`DELIBERATELY_OUT` list, which used to carry the long explanation, was emptied of this entry by the same
commit that added this comment (`test/regression/testEveryTestIsInTheBattery.java:41`), so there is no
second copy to fall back on.

---

## D

### D1: `SVN-A3`, the page-switch ordering, traced end to end

Not a defect. Traced and correct.

`LayoutEditor.leaveFor` (`src/org/traincontrol/gui/LayoutEditor.java:5621`) posts
`layoutEditingCompleteThen(() -> invokeLater(() -> arriveAt(page, autonomy)))`. After `1cfdf370`:
`layoutEditingComplete` starts the worker; the worker's `finally`
(`TrainControlUI.java:19409`) posts the EDT half whatever `refreshLayouts()` did; the EDT half runs
`layoutRefreshCompleteInternal()`, whose last acts include `setEditLayoutEnabled(true)`
(`TrainControlUI.java:19494`); and only then does the `finally` at `TrainControlUI.java:19446` run
`after`, which *queues* `arriveAt`. `arriveAt` therefore runs in a later EDT task and calls
`parent.setEditLayoutEnabled(false)` (`LayoutEditor.java:5756`) last. The button is left off with the
editor open, which is the fix's claim.

Three details I checked and found sound rather than assumed:

- The `AtomicBoolean` in `layoutEditingCompleteThen` (`TrainControlUI.java:19533`) makes the
  throw-before-the-worker path and the normal path mutually exclusive, and the `catch` rethrows.
- The autonomy branch of `leaveFor` (`LayoutEditor.java:5576-5607`) did **not** need the same change:
  `autonomyEditorClosed` is synchronous on the EDT (`TrainControlUI.java:5348-5387`), so the `finally`
  that posts `arriveAt` already runs after its `setEditLayoutEnabled(true)`.
- `after` runs before the tab restore at `TrainControlUI.java:19417`, but because `after` only
  *queues* `arriveAt`, the tab restore still completes first.

### D2: a withdrawn C, the two Badge sites do not disagree on a non-station

Withdrawn. Original severity C.

I expected `1cfdf370`'s `worthABadge` fix to have taken one of two terms: the editor badges every point
in the reducer and computes `parking` as `active == false || !isAutoDestination(tile)`
(`AutonomyEditorPanel.java:5995`), while the running diagram draws no badge at all unless
`station || turnAround || shut` (`AutonomySession.java:4707`) — so a non-station carrying a stale
`autoDestination: false` looked as though it would be orange in one and blank in the other.

It cannot be. `AutonomySession.java:2726`:

```java
    public boolean isParking(TileKey tile)
    {
        if (!store.isStation(tile)) return false;
```

and `isAutoDestination` is `!isParking(tile)` (line 2745), so on a non-station the term is always
`false` and both sites reduce to `shut`. The two badge sites agree.

### D3: `SVN-B13` asks the right predicate, and is reachable through one door only

Not a defect, with a reachability note.

The new loop in `Layout.rebuildHomeStations` (`Layout.java:1155-1169`) compares with
`Point.isSamePlaceAs`, and that is `block`, not the sensor — `src/org/traincontrol/automation/Point.java:762`:

```java
        return this.block != null && this.block.equals(other.getBlock());
```

which is what the builder emits per tile (`AutonomyBuilder.java:841`). The obvious trap — sixteen real
sensors on Adam's railway are shared by different Points, so an s88 comparison would delete a home on an
unrelated platform — is not present.

Reachability is narrower than the commit says. `block` is only ever set by `parseAuto` from the JSON
(`Layout.java:7229`), the companion store holds one `home` per tile key, and the builder emits `home` on
one copy only (`AutonomyBuilder.java:925`). So a configuration cannot express two homes on one square,
and Adam's frozen legacy `autonomy.json` contains no `"block"` at all. The state arrives through a
hand-written or hand-edited graph JSON, which is what the new test builds. The commit's other named
door — "a setup written before the editor swept" — cannot produce it, as far as I can see, for the same
reason; that clause is the only part of the entry I would not repeat. `configuration-Main.json` has no
`home` key on any point, so nothing in the frozen fixture exercises it either.

Two smaller notes, neither worth a finding: the search loop takes the *last* match rather than breaking
on the first (harmless — at most one can match), and which of two homes survives is decided by
`HashMap` iteration order and then written back out. The sibling rule at `Layout.java:1122` has had that
property since it was written and its comment says so, so the new rule is consistent with it.

### D4: `refreshOneSignal` and `protectsAnOccupiedSquare` ask the same occupancy question

Not a defect. Checked because "the rule lives in one place now" is only true if the third place agrees.

`Layout.refreshOneSignal:6052` asks `other.getCurrentLocomotive() != null` over every Point holding the
signal's name; `protectsAnOccupiedSquare:6138` asks the same of the same collection. The copy-vs-square
trap — a train on the eastbound copy leaving the westbound copy looking free — does not bite either,
because the builder puts the protecting signal on *every* copy of a station on purpose
(`AutonomyBuilder.java:873-895`, "On every copy, because the copies are one platform"), so whichever
copy holds the locomotive is also a copy naming the signal. `getBlockLocomotive()` would be the other spelling and is not needed here.

### D5: `TCX-A2`, the extraction is equivalent at both call sites

Not a defect.

`Layout.measuredRoomToReverseInto` (`Layout.java:6178`) re-checks the three preconditions that the
`isPathClear` call site already guards at `Layout.java:2362-2367`, and the rewritten comparison
(`measuredRoom != null && loc.getTrainLength() > room`) is truth-table identical to the `measured &&`
form it replaced, including the unmeasured-is-unknown case.

On the planner side (`HomeStaging.java:1047`) the rule is applied to the route ending at `to`, which is
the same object `isPathClear` would be handed, and the method self-guards on
`ending.isTerminus() || ending.isReversing()` so placing it before the `mustBackIn` test changes
nothing for ordinary stations. `connected` is correctly left without it: it is the impossibility proof,
and a proof may be looser than the search it guards but never tighter — the invariant `SV2-A1` wrote
down. Adding the rule there would have been the `D24-B1` mistake in a third limb.

### D6: the five strengthened tests can now fail

Not a defect. Each of the five now has something that can go red for the reason it names:

- `TCX-B5` (`testAnUnmarkedLayoutIsUntouched`) gains a control that marks a real tile and asserts the
  two builds differ.
- `TCX-B6` (`badgeAt`) separates `parking` from `shut` and adds an `assertNotEquals` on the ink.
- `TCX-B8` (`testNothingIsLoadedWhenAlreadyHome`) asserts `isTimetableSequential()` before and after,
  which is the flag the guard actually decides; the empty timetable never could catch it.
- `TCX-B9` asserts `copies.size() >= 2` and moves to a fixture whose middle sensor really splits.
- `TCX-B13` runs the control before setting `setReversing(true)`, so a `pickPath` that refuses
  everything fails the control.

### D7: every new bundle key is in all eight bundles, and ASCII-only

Not a defect. `route.ui.infoAlreadyRunning`, `layout.ui.confirmAccessoryProtecting`,
`autolayout.warnHomeSquareAssignedTwice`, `autosetup.ui.infoNoHomesToClear`,
`autosetup.ui.infoHomesCleared`, `ui.confirmDeleteFromDatabaseWithRoutes`,
`autosetup.ui.checkCopyNoWayOut` and `autosetup.ui.checkCopyNoWayIn` are each present once in all eight
`messages*.properties`, with the right placeholder counts. A scan of all eight files returns zero
non-ASCII characters, so the Java 8 mojibake trap is clear.

### D8: `clearAllHomes` does not write to disk

Not a defect. `setPointProperty` sets `dirty = true` and calls `deriveStationIndex()`
(`AutonomySession.java:4135-4142`) and does not save, so the new method's javadoc claim — "Nothing is
written to disk here … a mistaken press is undone by Cancel" — is true. `setHome(tile, null)` reaches
`setPointProperty(tile, "home", null)` (`AutonomySession.java:4014`), which removes the key. The list is
materialised by `tilesWithAHome()` before the loop mutates the same `JSONObject`, so there is no
concurrent-modification hazard.

### D9: `SVN-B14`, Cancel really does restore both slots

Not a defect. `RightClickFunctionMenu.java:215` captures both `Integer`s before the autonomy block and
line 303 restores them in the `else`, which covers Cancel, Escape and the close box alike, since only
`OK_OPTION` takes the other branch. A slot is a single `Integer` on the locomotive, so restoring the two
values restores everything the ticks could have moved — including a slot taken from another function
number mid-dialog. With autonomy unloaded the two ticks are never built and the restore is a no-op, as
the comment says. The menu items themselves (line 154) still write immediately, which is right: a menu
item has no Cancel.

### D10: the builder change is inert on Adam's current configuration

Not a defect — the check that answers the question the fan-out briefing asked about `active: false`
reaching `isPathClear`.

`test/operator_layout/config/autonomy/configuration-Main.json` contains exactly one `"active": false`,
on `2 - Bottom:8,7`, and that square **is** a station (`setup.json:13`). The old builder emitted
`active` for stations, so `1cfdf370` changes nothing about what his graph is built with today. The
change is only reachable for him through the editor's **Out of service** on a plain square, which is
what it was written for. The upgrade path is the exposure, and that is [C1](#c1).

The same file contains no `home` key on any point, so `SVN-B13`'s new guard has nothing to act on
there either.

### D11: what this pass did not cover

- **No test was run, and no JVM was started.** Where a finding would be settled by a measurement —
  [A1](#a1)'s stall length, [C4](#c4)'s build cost — it says so rather than guessing a number.
- **Every source diff in the window was read in full**, including the fourteen commits that touch only
  `docs/`, `battery.sh` and `one.sh`. The shell-script commits (`a33b9ae1`, `c9153aaf`, `f59fa45e`,
  `208b3ee1`, `9fec3b71`) were read but not re-analysed: three validation passes have already been over
  them, and `SV2-A2` settled the mechanism.
- **Test diffs were read for whether they can fail, not re-derived.** The fixture changes to
  `test/operator_layout/` in `409d4ce8`, `56c6080e`, `878fb8f4` and `934018f3` are captures of Adam's
  own diagram; I checked the autonomy JSON for the specific properties the code changes depend on
  (`active`, `autoDestination`, `home`, the station list) and did not audit the `.cs2` geometry.
- **`testTrainsComeHomeToTheirPlatforms` is now in the battery and expected red** (`188cc1cf`,
  `build.xml:265-270`, on Adam's ruling "ok, so that test should then be red"). Several commits since
  then changed both the fixture and the planner. I could not determine whether it is still red, and a
  permanently red class makes a *second* failure in it hard to see — worth one run to record its
  current state, and see [C10](#c10) for the comment that will be read when it does go red.
- **Nothing outside the 24-hour window was reviewed**, except where an in-window change rests on it;
  [C9](#c9) is the one place that produced a finding.
- **`docs/reviews/2026-09-01-*` was read as context**, not audited. Where I disagree with a disposition
  from that round I have said so under its own finding: [C3](#c3) disputes `SVN-B7`'s account of the
  harm, and [D3](#d3-svn-b13-asks-the-right-predicate-and-is-reachable-through-one-door-only) narrows
  `SVN-B13`'s.
