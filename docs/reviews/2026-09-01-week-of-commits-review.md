# A week of commits, reviewed: 2026-08-25 to 2026-09-01

**Status:** open

**Citation prefix:** `SVN`. Cite findings from this document as `SVN-A1`, `SVN-B2`, and so on.

| | |
|---|---|
| **Reviewed** | branch `autonomy-diagram-r0`, v3.0.0 |
| **HEAD at review** | `828b1ff1` — "The cross takes the colour and the weight it should, and its test opens a sandbox" |
| **Scope** | the 170 commits from `b71b6a26` (2026-08-25) to `828b1ff1` (2026-09-01), plus the working tree |
| **Working tree at the start of this pass** | two files modified and uncommitted, both under `cs2_sample_layout/config/autonomy/`. That is `SVN-A1`. |
| **Date** | 2026-09-01 |

The briefing named `e9435bfc` as HEAD, plus uncommitted changes to `TileAnnotation.java`,
`LocomotivePlaceholder.java` and `test/core/testAutonomyDiagramMonitor.java`. Those three landed in
`828b1ff1` during this pass, and `828b1ff1` is what was reviewed.

**The working tree moved during this pass, and this header says so rather than going stale.** When it
began, the only uncommitted changes were the two files in `SVN-A1`. By the time it finished, a parallel
reviewer in this same round had also modified `Automation.md`, `AutomationAPI.md` and
`test/core/testTrainTailClearsEdges.java`. Those three are not mine — nothing in this pass wrote to the
repository except this file — and no finding below rests on them.

One of them is worth citing, because it is about the same commit as `SVN-B1` to `SVN-B4`. The edit to
`testTrainTailClearsEdges.java:207-225` records, as **TCX-A3**, that `17cad1fe`'s reversal-room rule
made a pre-existing assertion unfalsifiable: the test asked whether `loc.getTrainLength()` appeared
anywhere in `Layout.java`, and from the moment the new rule read the same length twice more, the
clearing loop's argument could have been deleted outright with the test still green. That is a fifth
consequence of the same commit, found by a different reviewer, and it belongs beside `SVN-B1`–`SVN-B4`
when that commit is revisited.

## Method, and what was not done

**No tests were run, nothing was built, and the application was not started.** Every finding was
reached by reading: `git log`/`show`/`diff`, `grep`, file reads, and comparing the JSON fixtures and
`cs2_sample_layout` as data. Nothing in the repository was written except this file. Where a claim
would be settled by running something, it is listed as an open question at the end rather than
answered.

The week's churn was ranked and the top subsystems read deeply rather than the commits read in
sequence: `TrainControlUI` (5,359 lines changed), `AutonomyCompanionStore` (1,788), `Layout` (1,197),
`LayoutEditor` (1,172), `LocIconCropDialog` (1,007), `StationCaption` (897, new), `AutonomySession`
(889), `AutonomyEditorPanel` (874), `LayoutGrid` (821), `HomeStaging` (770).

The hunt was aimed at the failure modes this repository's own record says a week of rapid change
produces: a fix applied at one call site and not its twins; a rule lifted from where it was safe to
where its precondition does not hold; a semantic changed without re-reading every caller; a field added
without the copy constructor; and state two rules must agree about and do not. Most of what follows is
an instance of the first, and `SVN-A2` is a textbook instance of the second — committed the same day the
author wrote the *correct* version of the same rule a few hundred lines away.

Every finding names a file, a line and a quotation. Where reachability is uncertain it says so;
severities are argued rather than asserted where the argument is not obvious.

---

## A — wrong behaviour on the layout, or data silently lost

| | | |
|---|---|---|
| **SVN-A1** | The real railway's configuration is sitting in the working tree in a damaged state | open |
| **SVN-A2** | `battery.sh`'s new concurrent-JVM guard fails open, and cannot see an `ant`/NetBeans run at all | open |
| **SVN-A3** | `layoutEditingCompleteThen` runs its continuation before the refresh it is named after | open |
| **SVN-A4** | A route refused at a human door discards its emergency stop, which is the defect its own comment claims to have removed | open |

### SVN-A1 — `cs2_sample_layout/config/autonomy/` currently holds a damaged configuration, uncommitted

`git status` shows two modified files, both Adam's real railway:

```
 M cs2_sample_layout/config/autonomy/configuration-Main.json
 M cs2_sample_layout/config/autonomy/setup.json
```

Compared **semantically** against `HEAD` — both parsed as JSON, so key reordering is not counted — 14
of the 71 points differ. Two have lost authored data outright:

| Square | Present at HEAD, absent in the working tree |
|---|---|
| `1 - Main:13,9` | `excludedLocs` (6 locomotives), `priority: -3`, `canReverse: true`, `facing: W`, `loc` |
| `1 - Main:14,3` | `excludedLocs` (the same 6), `priority: -2` |

The six names in both lists: `EA 3005 DSB`, `ER 2035 DSB`, `MF 5028 DSB`, `MY 1150 DSB`,
`MZ 1425 DSB`, `SP45-204`.

`1 - Main:10,6` also lost `active: false` outright, while `1 - Main:4,5` and `1 - Main:8,6` had the same
flag correctly migrated to `autoDestination: false` — so the migration ran on two squares and dropped
the flag on a third.

**This was not a person editing the railway.** The working-tree file carries locomotives only a test
creates:

- `globals.timetable[0].loc` is `"MT-x233 Test Loc"` (HEAD: `"EN57-947"`).
- `1 - Main:5,4` gained `"loc": {"name": "MT-233 Test Loc 2"}`.
- `globals.atomicRoutes` went `true` → `false`, `globals.pathPreference` went `MOST_STATIONS` →
  `RANDOM_ANY_STATION`.

The damage matches, item for item, the incident `test/ui/testEveryLanguageFits.java:40-47` already
records:

> "the autonomy configuration of `cs2_sample_layout` — which is Adam's real railway and is not
> recoverable — was rebuilt against the fixture diagram, losing facings, placements, priorities and an
> exclusion list."

Facings, placements, priorities and an exclusion list is exactly what is missing.

**This is not a defect in the week's source changes.** It is the residue of that incident, still in the
working tree and never reverted. It is filed at A because the good copy is one command away and stops
being so the moment anything commits that folder:

- `git diff HEAD -- cs2_sample_layout` is the whole of the loss.
- `git checkout -- cs2_sample_layout` restores it.

**Before doing that, somebody has to decide whether any working-tree change is Adam's own.** The
`setup.json` side moved several station-caption anchors (`5:19,14` replacing `5:20,14`, `5:4,4`
replacing `5:4,5`, `5:9,7` replacing `5:9,6`) — which is what dragging a label looks like. A blind
revert discards those. Safe order: copy the folder aside, revert, then re-apply anything deliberate.

Why this survived unnoticed: `docs/tools/battery.sh:205` takes `live_before=$(fingerprint)` at the
**start** of a run, so a folder already damaged before the run passes silently. Every battery since has
correctly agreed that *that run* changed nothing.

### SVN-A2 — the guard added today against concurrent test JVMs fails open, and is blind to the runner Adam uses

`docs/tools/battery.sh:67-101`. The comment above it is exactly right about the defect it replaces:

> THE LOCK IS A PROXY. THE TEST JVMS ARE THE HAZARD (2026-09-01). … it refuses only when `kill -0`
> SUCCEEDS, so every answer that is not a clear yes … falls through and starts a second run. **A guard
> that fails open on the dangerous side is not a guard, and this one failed open in exactly the case it
> exists for.**

The replacement has the same shape.

```sh
RUNNING_JVMS=$(powershell.exe -NoProfile -Command \
    "(Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { \$_.CommandLine -like '*anyReceivePort*' } | Measure-Object).Count" \
    2>/dev/null | tr -d '\r\n ')

case "$RUNNING_JVMS" in
    ''|0|*[!0-9]*) ;;
    *)
        echo "*** TEST JVMS ARE ALREADY RUNNING ($RUNNING_JVMS of them) ***"
```

**(a) An unanswerable question is answered "zero".** The `''` and `*[!0-9]*` arms are empty statements:
they fall through and start the run. `powershell.exe` missing from `PATH`, WMI refusing the query, the
CIM call erroring, MSYS mangling the nested quoting — every one yields an empty `RUNNING_JVMS`, and
`2>/dev/null` discards the only evidence that the question was never asked. Nothing in the output
distinguishes "no test JVMs are running" from "I could not find out".

That is the mistake the author got **right** the same day, a few hundred lines away, in
`Layout.java:2337-2350`:

> AND THE TOTAL HAS TO BE COMPLETE. An unmeasured segment used to contribute nothing while the sum went
> ahead without it, which is "I do not know how long this is" answered as "it is zero".

Unknown is not zero in the length guard. It is zero here.

**(b) It can only see JVMs `battery.sh` itself launched.** The filter is
`CommandLine -like '*anyReceivePort*'`, and the only thing that puts that flag on a command line is
`battery.sh:154`:

```sh
JAVA_FLAGS="${TC_JAVA_FLAGS:--Dtraincontrol.anyReceivePort=true}"
```

`build.xml` sets no `jvmarg` or `sysproperty` at all — `grep -n "jvmarg\|sysproperty\|anyReceivePort"
build.xml` returns nothing — which `docs/reviews/2026-08-28-test-suite-review.md:380` already recorded:
*"`build.xml` also does not pass `-Dtraincontrol.anyReceivePort=true`."* An `ant test` is how NetBeans
runs a test class, and NetBeans is how Adam builds and tests. It is invisible to this guard. So is any
JVM started before 2026-08-25, when `docs/tools/reap.ps1:17` says the marker changed.

The comment claims the opposite, and that claim is the whole reason the check was thought sufficient:

> it is true whether the other run came from a battery, from one.sh, from an IDE, or from somebody
> else's session entirely — none of which the lock can see.

It is not true for an IDE.

**Why A rather than B.** The guard does not itself lose data; it permits the mechanism that has already
destroyed the irreplaceable configuration (2026-08-30, and `SVN-A1`'s residue), and it reports success
while doing so. It also demonstrably did not hold: two batteries ran concurrently on 2026-09-01 with
this code in place, and the JVM crash dumps still in the repository root name both runs —
`-Dtraincontrol.batteryRun=battery-40080` and `battery-35302` in `hs_err_pid*.log`. A reader who holds
that a fail-open guard is not itself data loss can reasonably move this to B; the facts do not change.

Two weaker limbs of the same shape are filed separately as `SVN-C10`.

### SVN-A3 — a track-mode page switch leaves the editor bound to a discarded diagram, and re-enables "Edit Layout" with the editor still open

`dd87f6bf` changed the page-switch teardown from passing its continuation *into*
`layoutEditingComplete` to a wrapper:

```java
-            parent.layoutEditingComplete(() ->
+            parent.layoutEditingCompleteThen(() ->
                 javax.swing.SwingUtilities.invokeLater(() -> arriveAt(page, autonomy))));
```

`src/org/traincontrol/gui/TrainControlUI.java:19391-19401`:

```java
    public void layoutEditingCompleteThen(Runnable after)
    {
        try
        {
            layoutEditingComplete(null);
        }
        finally
        {
            if (after != null) after.run();
        }
    }
```

`layoutEditingComplete` is **asynchronous** — `TrainControlUI.java:19304-19310`:

```java
        new Thread(() ->
        {
            this.model.refreshLayouts();

            javax.swing.SwingUtilities.invokeLater(() ->
            {
                layoutRefreshComplete(after);
```

so the `finally` fires at once and the EDT half runs later, from the worker's own `invokeLater`. Two
things follow, both verified by reading the three methods end to end.

**(a) The `finally` cannot catch what its javadoc says it catches.** The javadoc (`:19380-19383`) says
*"above it is the last statement of a dozen — so anything throwing on the way skipped it"*. Those dozen
statements are in `layoutRefreshComplete`, entirely outside this `try`. The `try` covers
`setEditLayoutEnabled(false)`, one field read and `Thread.start()`. The latch is lowered — by accident,
because `after` now runs early — but the stated mechanism is dead.

**(b) The ordering `arriveAt` depends on is inverted.** `arriveAt` is queued on the EDT immediately;
`layoutRefreshComplete` is queued only after `model.refreshLayouts()` has parsed every page file. So
`layoutRefreshComplete` is the last writer and `TrainControlUI.java:19367` wins:

```java
        setEditLayoutEnabled(true);
```

`LayoutEditor.java:5719-5723` states the dependency in so many words:

```java
            // The editor is still open, so the button that opens one stays shut.  Both teardowns above
            // hand it back - autonomyEditorClosed directly, layoutEditingComplete through the refresh
            // it finishes with - ...
            parent.setEditLayoutEnabled(false);
```

Consequences, after any track-mode page or mode switch:

- The Edit Layout button and menu item are live with `openEditor` still displayable.
  `editLayoutButtonActionPerformed` (`:19412`) and `openLayoutEditor` (`:4437`) both gate on
  `editLayoutButton.isEnabled()`, not on `isLayoutEditorOpen()` — so a second `LayoutEditor` is
  constructed over the first, which is the trap `:4431-4436` describes: *"Two editors share one
  LayoutDiagram and one edit flag: closing either one sets edit false under the other."*
- `LayoutEditor.java:5635` — `LayoutDiagram arriving = parent.getModel().getLayout(page);` — binds a page
  object `refreshLayouts()` is about to replace. `MarklinControlStation.syncLayoutsFromConfiguredSource`
  reaches `syncLayouts()`, whose call site comments *"syncLayouts clears for itself, once it has
  something to put back"* (`:457-458`) — the diagrams are cleared and repopulated wholesale. Meanwhile
  `TrainControlUI.java:19343-19352` calls `this.resetAutonomySession()`, rebuilding the session over the
  **new** objects. That is precisely the state the comment above that line forbids: *"a session still
  holding the old ones derives from geometry that no longer exists."*
- `LayoutEditor.java:5742` — `takeTheUndoPoint(parent.getAutonomySession());` — snapshots the session
  `resetAutonomySession()` is about to discard, so Cancel restores a pre-capture setup.
- `LayoutEditor.java:5610` — `changingPage = false;` — drops the overlap latch before the refresh worker
  finishes, so a second switch can start two concurrent `refreshLayouts()` threads.

**Reachable by** every sidebar page click, `+`/`-` (`stepPage`, `:5946`), and the track→autonomy mode tab
(`modeTab`, `:6092`) — all funnel into `leaveFor` with `isAutonomyMode()` false. The autonomy branch
(`:5555-5572`) is unaffected: `autonomyEditorClosed()` is synchronous and re-enables the button *before*
`arriveAt`. That is a clean twin asymmetry — one of the two teardowns was converted and the other was
not.

Limb (a) and the button re-enable are certain from the code. The stale-diagram binding is a race in
principle, but `arriveAt` is queued before a thread that does file I/O for every page, so it is decided
in practice.

### SVN-A4 — a route refused at a human door loses its emergency stop, which is exactly the defect the fix beside it claims to have removed

`6b6e6bd4` moved the conflict decision from a whole-route refusal to a per-command one, and its comment
at `MarklinRoute.java:598-611` states the reason:

> This used to set skipAccessories, and the loop below skips every accessory before the per-command
> check can ask about any of them. So the same conflict produced two different outcomes decided by
> sub-second timing … **The per-command check makes the decision at the two HUMAN doors now**

The per-command path does the right thing. `MarklinRoute.java:674-690`, on a refusal mid-route:

```java
                                        skipAccessories = true;

                                        continue;
```

`continue` keeps the loop going, so `else if (rc.isStop())` at `:698-707` is still reached and
`this.network.stop()` still cuts the power.

**The human doors never get there.** `TrainControlUI.java:16100-16104`:

```java
                    if (answer == RouteConflict.REFUSED)
                    {
                        refreshRouteList();

                        return;
                    }
```

and `LayoutLabel.java:537-540`:

```java
                                                if (answer == TrainControlUI.RouteConflict.REFUSED)
                                                {
                                                    return;
                                                }
```

`MarklinRoute.execRoute` is never called on either branch. So a safety route that cuts power **and**
sets a trap point — `6b6e6bd4`'s own worked example, *"the shape a safety route on an s88 trigger
naturally has"* — loses its stop if the conflict existed when the operator pressed Execute, and keeps
it if the conflict appeared a moment later. That is verbatim the "two different outcomes decided by
sub-second timing" the comment says was removed: the whole-route refusal was taken out of the model and
left standing at the two doors the comment names as fixed.

Worse, `askAboutRouteConflict` also returns `REFUSED` when the dialog cannot be shown at all
(`TrainControlUI.java:16046-16052`):

```java
        catch (InterruptedException | java.lang.reflect.InvocationTargetException couldNotAsk)
        {
            this.model.log(couldNotAsk);

            return RouteConflict.REFUSED;
        }
```

so a failed `invokeAndWait` silently drops the stop as well — "I could not ask" answered as "no", the
same shape as `SVN-A2`.

Reachable from the routes-tab play button (`:19230`), the table click (`:19255`), the right-click menu
(`RightClickRouteMenu.java:88`) and the diagram route tile. `testTheStopInARefusedRouteStillRuns` covers
only `execRoute(true)` — the s88 door — and there is no human-door twin in the suite, so this is
uncovered by construction rather than by accident.

This is the author's own recorded lesson, on the very fix that produced it: *"A fix can be worse than the
defect — refusing whole discarded an emergency stop."* The fix removed the whole-route refusal from one
of the three doors.

---

## B — incorrect results, or crashes in specific configurations

| | | |
|---|---|---|
| **SVN-B1** | The reversal-length notice and the guard it serves ask different questions | open |
| **SVN-B2** | A partially measured edge is treated as measured, and under-counts the room | open |
| **SVN-B3** | The reversal-length guard sums the whole journey, not the run-in | **DEFERRED — needs Adam** |
| **SVN-B4** | The reversal-length rule reached `isPathClear` and not the staging planner | open |
| **SVN-B5** | `connected` and `firstClearRoute` disagree about a train standing on a reversing point | **DEFERRED — needs Adam** |
| **SVN-B6** | The running diagram draws no cross on a shut *plain* point | open |
| **SVN-B7** | The "route already running" guard is on one door of three | open |
| **SVN-B8** | Undo cannot re-open a portal whose two halves are on different pages | open |
| **SVN-B9** | Nothing repairs the on-disk pre-edit note when a locomotive is renamed | open |
| **SVN-B10** | The load door asks a narrower question than the start door | open |
| **SVN-B11** | A cut consumed by a paste that carried nothing, then a paste back that forgets the setup | open |
| **SVN-B12** | The MT-149 timetable repaint sits behind two early returns | open |
| **SVN-B13** | `rebuildHomeStations` dedups by locomotive, not by square | open |
| **SVN-B14** | The autonomy function slots are written before OK is pressed | open |
| **SVN-B15** | The mid-route conflict question parks the rest of the route, stop included, behind a modal | open |
| **SVN-B16** | The plain-accessory tile guard never got the protecting-signal half its route twin has | open |
| **SVN-B17** | AU-C12 is still open: the Keyboard tab throws accessories with no guard at all | open |

`SVN-B1` through `SVN-B4` are all about the guard added in `17cad1fe`, the newest substantive commit of
the week. They are separate findings because they fail in different directions and can be fixed
independently.

### SVN-B1 — the notice names squares that are not the ones blinding the guard, and misses the ones that are

The guard, `Layout.java:2337-2350`, is disabled unless **every edge of the path** carries a length:

```java
                for (Edge segment : path)
                {
                    if (segment.getLength() <= 0)
                    {
                        measured = false;
                        break;
                    }

                    room += segment.getLength();
                }
```

The notice, `AutonomySession.java:1937-1939`, asks only about the **turn-around square's own tile**:

```java
            if (!isTurnAround(tile)) continue;

            if (store.getTileLength(tile) <= 0) out.add(tile);
```

and its javadoc claims they are the same set: *"the squares this names are exactly the ones where that
guard is blind."* They are not, in both directions.

- **Over-reports.** An edge's length is `sumLength(path) + lengthOf(tile)` (`GraphReducer.java:946`) —
  every tile it crosses, plus its end tile. A turn-around square with no length of its own, reached over
  an edge whose intermediate tiles *are* measured, has a positive last edge and is not blinding
  anything. It is still listed.
- **Under-reports, which is the dangerous direction.** A wholly unmeasured stretch of plain track
  anywhere earlier on the run blinds the guard on every path through it, and is never named. Nothing
  tells the operator which stretch to measure.

**Measured on the real railway, not hypothesised.** `cs2_sample_layout/config/autonomy/setup.json`
records exactly six tile lengths, all on page id 5 (`"1 - Main"`, which is in the active configuration):

```
{"5:20,13": 4, "5:0,11": 4, "5:20,14": 2, "5:1,10": 4, "5:14,3": 3, "5:5,4": 3}
```

`store.measuresAnyTrack()` is therefore true and the notice arms. `configuration-Main.json` at HEAD has
17 points with `mustReverse` and 6 with `canReverse`, disjoint — **23 turn-around squares**, all on
included pages. Of those, exactly two (`1 - Main:20,13`, `1 - Main:20,14`) carry a length. So the
editor will raise **21 `REVERSAL_NEEDS_LENGTH` warnings**, on the strength of six measurements, four of
which are not on turn-around squares at all. (A second reader counted 22/20 rather than 23/21; the
derivation above is the one to check, since it may differ by one square that `isTurnAround` treats
specially.)

Meanwhile the guard needs *every* edge of a path measured. With six measured tiles on a layout of
hundreds, no multi-edge path qualifies. The net result of `17cad1fe` on Adam's own railway today is 21
new warnings and zero new protection.

### SVN-B2 — a partially measured edge reports a positive but short length, so acting on the notice makes the guard refuse trains that fit

`Layout.java:2343` tests `segment.getLength() <= 0`, and treats anything above zero as measured. But
`GraphReducer.java:1052-1061` builds that number by skipping unmeasured tiles rather than by refusing:

```java
    private int sumLength(List<TileStep> path)
    {
        int total = 0;

        for (TileStep step : path)
        {
            total += Math.max(0, authored.getTileLength(step.getTile()));
        }

        return total;
    }
```

So an edge crossing ten tiles of which one is measured reports that one tile's length, and the guard
accepts it as a complete measurement. `room` then under-counts the real track and
`loc.getTrainLength() > room` refuses a train that would have fitted — with a wrong number in
`autolayout.errorTrainTooLongToReverse`, since `room` is what the message prints.

The commit's own stated invariant — *"A path carrying any unmeasured segment is not judged at all"* — is
true per edge and false per tile. And the transitional state `SVN-B1`'s notice creates is exactly the
worst one: measuring the turn-around square flips the last edge from zero to positive-but-short, which
turns the guard **on** with an under-count. Following the editor's advice is what makes this fire.

`isPathClear` is the tier every door passes through, so the refusal reaches the operator's own
right-click. Filed at B rather than A because refusing is the safe side of the error.

### SVN-B3 — the guard sums the whole journey rather than the run-in (DEFERRED — needs Adam)

`Layout.java:2337` iterates `path`, which `isPathClear` receives as the **whole route** from origin to
destination. On any long route the sum is the length of the entire trip, so the comparison against the
train's length is vacuous; only one- and two-edge paths can trip it.

Adam's words, quoted in the commit: *"Do you sum the track segments leading up to it? if they are long
enough, then we are good."* The commit records deliberately rejecting a "last two edges" reading as too
strict. Both readings are defensible and the difference decides whether the guard does anything at all
on a real route.

**The question for Adam, in one sentence:** should the room behind a reversing train be the whole route
it came in over, or only the last N segments it will physically stand on?

### SVN-B4 — the new rule is in `isPathClear` and nowhere in the staging planner

`HomeStaging`'s only length rule is `at.validateTrainLength(loc)` (`HomeStaging.java:1636`) — the typed
maximum. There is no edge-length sum anywhere in the file. Staging legs execute through the timetable →
`executePath` → `isPathClear`, so the planner can now return `READY` for a leg the runtime refuses on
its first move.

That is the OB-073 shape the file's own comments describe: *"the run STARTED, the first leg was refused,
and the retry loop asked again every two seconds until it abandoned the run"* — a fleet left
half-staged. It fires where the destination is a terminus or reversing point (most parking berths, so
most homes), the train length is set, and the path is measured. It is latent today for the reason in
`SVN-B1` — six measured tiles — and becomes live as soon as Adam acts on the new notice.

This is the exact sibling `fbc19cb9` swept for the *other* rule ten hours earlier: *"the runtime already
insisted … but the PLANNER did not know the rule."* The same sweep was not done for this one.

### SVN-B5 — `connected` and `firstClearRoute` disagree about a train standing on a reversing point (DEFERRED — needs Adam)

`HomeStaging.java:1682-1684`, in `connected`:

```java
        seen.add(from.getUniqueId() + "/false");
        queue.add(from);
        turned.add(false);
```

`HomeStaging.java:949`, in `firstClearRoute`:

```java
        // A train already standing on a reversing point sets off turned; anywhere else it does not.
        queue.add(new Candidate(from, new LinkedList<Edge>(),
            new HashMap<String, Accessory.accessorySetting>(), from.isReversing()));
```

`fbc19cb9` added the turned/straight state to `connected`; `fe211d30` added it to `firstClearRoute` and,
only there, seeded it from `from.isReversing()`. `fe211d30`'s own message says *"That is the same
collapse fixed in `connected`"* — the seeding half was not carried back.

Consequence: a non-reversible locomotive standing **on** a reversing point, homed at a terminus reachable
with no further reversing point, is added to `unreachable` (`HomeStaging.java:445`), so `plan()` returns
`IMPOSSIBLE` (`:578`) before `search()` runs — aborting the whole fleet's staging — even though
`firstClearRoute` would have returned that very route.

**Which side is wrong is genuinely open.** `Layout.reversesAlongTheWay` (`Layout.java:3427-3437`) states
the opposite convention for the runtime: *"The origin is exempt — a train standing on one is free to
leave it — so only the END of each edge is tested."* If that is the right reading, `firstClearRoute:949`
is the defect and it plans a leg the runtime rule would refuse. Either way the two must not disagree.

**The question for Adam, in one sentence:** does a train already standing on a reversing point count as
having turned, or must it turn again on the way out?

### SVN-B6 — the running diagram never draws the cross on a shut plain point

`e9435bfc` widened the cross to *"a square nothing can pass draws a cross whether or not it is a
station"*. `AutonomySession.java:4342` was not widened with it:

```java
        boolean worthABadge = store.isStation(tile) || isTurnAround(tile);
```

and at `:4354` the whole badge is `!worthABadge ? null : new TileAnnotation.Badge(…)`. So a plain sensor
that is neither a station nor a turn-around, marked out of service, gets no badge on the running diagram
and draws nothing at all — while `AutonomyEditorPanel.badgeFor` (`AutonomyEditorPanel.java:5951-5961`)
gives every Point in the graph a badge and therefore draws the X.

The comment directly above the gate lists what a badge is for, and the shut case is the third item on
its own list:

> What a badge is FOR is the things the art cannot say: this is a station, trains turn round here,
> **autonomy is not using it**.

The gate does not let that third case through. Reachable by marking any plain sensor "Out of service".
Whether the running diagram *should* show it is Adam's call — but the code and the comment beside it
currently disagree, and the editor and the viewer disagree with each other.

### SVN-B7 — the "route already running" guard is on one door of three

`TrainControlUI.java:19222-19236`:

```java
            // DISABLED MEANS DISABLED (MT-217).
            //
            // The button greys while its route runs, and a greyed button that still fires when pressed
            // is a lie - it would also start the same route twice, which is the thing the greying is
            // there to say cannot happen. Silently, because the button already says why: this is the
            // guard and the affordance asking one question, which is the OB-057 and OB-090 shape.
            if (!routesExecuting.contains(route.getName()))
            {
                executeRoute(route.getName());
            }
```

Two other doors reach the same method with no such check:

- `TrainControlUI.java:19255` — a left click anywhere else in the cell, then confirm → `executeRoute(route.getName());`
- `src/org/traincontrol/gui/RightClickRouteMenu.java:88` — `menuItem.addActionListener(event -> ui.executeRoute(routeName));`

`executeRoute` (`:16059`) has no guard of its own — it calls `routeStarted(route)` and starts a thread.
Its comment even asserts the opposite invariant: *"Every door that runs a route comes through here."*
Every door comes through it; the guard is not in it.

**Gesture:** while a route's play button is grey, click its cell and confirm, or right-click → Execute.
The route's accessory commands are issued a second time, concurrently with the first run. This is the
OB-057/OB-090 lesson the guarded site cites, applied at one site and not its twins.

### SVN-B8 — undo cannot re-open a portal whose two halves are on different pages

`AutonomyCompanionStore.java:4289-4294`, `SquareSetKept`:

```java
        @Override Object snapshotOf(String page) { return membersOnPage(set, page); }
...
            putMembersBack(set, page, (Set<TileKey>) snapshot);
```

`453a3ef4` changed two registry kinds to capture entries at *either* end — `PairMapKept` (`:4152`
`onPageEitherEnd(map, page)`) and `ListMapKept` (`:4232`) — and left `SquareSetKept` alone. But
`disabledPortals` is the one set whose members are paired across pages, and `setPortalDisabled`
(`:1138-1148`) writes both ends:

```java
        set(tile, disabled);
        TileKey partner = getPortalPartner(tile);
        if (partner != null) set(partner, disabled);
```

The real data confirms the halves live on different pages: `setup.json` pairs `1:10,9` with `5:15,5`.

Failure: pair A on page P, B on page Q, link enabled. Snapshot of P captures `{}`. Disable the link →
set becomes `{A, B}`. Undo → members on P are removed (A) and nothing is added back → set is `{B}`.
`isPortalDisabled(A)` (`:1096-1116`) still returns true through the partner check, so **the undo did not
re-open the link**, and `disabledLinks: ["5:15,5"]` goes to disk as a one-ended disable — the shape
`TileGraph.portalClosed:504` calls out as having no migration.

Reachable: `AutonomyEditorPanel.java:1460` `on -> session.setPortalDisabled(target, !on)` is a live
control; `LayoutEditor.restoreCaptions` → `AutonomySession.restorePage` → `store.restorePage` is the undo
path. Not confirmed whether the checkbox itself pushes an undo point; if it does not, the asymmetry
still fires on any Ctrl+Z whose snapshot predates the toggle.

### SVN-B9 — nothing repairs the on-disk pre-edit note when a locomotive is renamed

`LayoutEditor.takeTheUndoPoint` (`:503-510`) takes **two** snapshots: the in-memory `autonomyAsOpened`,
and a disk note written by `AutonomyCompanionStore.rememberBeforeEdit` (`:4454`) from
`store.snapshotSetup()` into `config/autonomy/setup-before-edit.json`. That note contains
`configurations.*.points.*.loc/home/excludedLocs` and `globals.timetable`, all by locomotive name.

`LayoutEditor.autonomyLocomotiveRenamed` (`:461-484`) repairs three in-memory holders —
`autonomyAsOpened`, `previousCaptions`, `previousCaptionsRedo` — under a comment naming the doors it
swept: *"Cancel and Ctrl+Z are two ways of saying the same thing, and only one of them was covered."*
`TrainControlUI.repairAutonomyLocomotive` (`:3860-3876`) repairs the live store and `setup.json`.

Nothing rewrites `beforeEditFile()`. The note is the identical `snapshotSetup()` shape that
`repairLocomotiveInSetup` (`AutonomyCompanionStore.java:1399`) already handles, and it is written only at
`takeTheUndoPoint`, so it stays stale for the rest of the session.

If the process dies with the editor open — the exact event the disk half was built for —
`AutonomySession.revertUnfinishedEdit` (`:1329-1338`) calls `restoreSetup(was)` **and `saveQuietly()`**,
writing the pre-rename name back to disk. By this codebase's own account (`LayoutEditor.java:450-452`)
that "is refused by parseAuto, which invalidates the whole layout". The editor being open is exactly
the condition under which the note exists, so this door is co-extensive with the two that were swept.
The javadoc at `:1493` calls the page snapshot *"the one that reaches DISK"* — it is not; the note is.

### SVN-B10 — the load door asks a narrower question than the start door, and the method written to be the one question has no callers

`AutonomySession.hasErrors()` (`:3256-3258`) is documented as *"the question every affordance that offers
to run it has to ask"*:

```java
    public boolean hasErrors()
    {
        return hasBlockingProblems() || errorCount() > 0;
    }
```

`grep -rn "hasErrors" src` returns the declaration and nothing else — **zero callers**. Every affordance
uses one half or the other:

- `AutonomyViewerPanel.java:782` — `if (session().hasBlockingProblems())` is the only thing between
  `load()` and `parseAuto(session().buildConfiguration())` at `:815`.
- `AutonomyMenu.java:283-292` offers the configuration radio items with no enablement predicate at all.
- `TrainControlUI.java:5173` and `:20031` use `errorCount()` for *starting*.

So a setup carrying `DUPLICATE_SENSOR_PAGE` — whose javadoc quotes Adam saying *"This config should not
be possible to run"* (`AutonomyChecks.java:191-194`) — loads into the running Layout. Starting autonomy
is then refused, but manual dispatch and Return Home run on a graph where one s88 is two Points.
`test/regression/testErrorsStopTheSetupRunning.java:180-184` pins the *start* family only.

### SVN-B11 — a cut is consumed by a paste that carried nothing, and pasting the block back then forgets its setup

`LayoutEditor.java:2884-2891`:

```java
                if (autonomy != null && (!moves.isEmpty() || !overwritten.isEmpty())
                    && autonomy.moveTiles(moves, overwritten))
                {
                    rememberAutonomy(autonomy);
                }

                // Used up either way.  Cut then paste twice is a move and then a copy.
                this.clipboardWasCut = false;
```

The flag is cleared inside `if (moves != null)` whether or not anything moved. `cutMoves` returns an
**empty but non-null** map whenever no origin is both cut and still empty *on this page*: the guard at
`:2663-2668` returns null only when `vacated` is null, and `emptyCutOrigins()` (`:2736-2753`) returns an
empty set, never null.

Two reachable sequences:

1. Cut a block on page A, switch to page B, paste there (a legitimate copy — the origins are elsewhere),
   switch back to A, paste at the origin. The flag is false, so `:2895` takes the `else` branch —
   `forgetBuiltOver(builtOver)` — and forgets every origin square's setup. That contradicts RC-A1's own
   comment at `:2709-2713`: *"Left on the source page instead, where it is recoverable — and where
   coming BACK to that page and pasting now picks it up correctly."*
2. Ctrl+X, Ctrl+Z (origins refilled → empty map → flag consumed), Ctrl+Y (origins emptied again, setup
   still on them), Ctrl+V at the origin → `forgetBuiltOver` destroys the block's setup. That is LE-A4
   reached through a door the LE-A3/RC-A1 rewrite opened.

### SVN-B12 — the MT-149 timetable repaint sits behind two early returns neither rename door can clear

`repaintTimetable()` — the MT-149 fix — appears once, at `TrainControlUI.java:3908`, inside
`repairAutonomyLocomotive`, after two early returns: `:3838` `if (session == null)` … `return;` at
`:3853`, and `:3860` `if (!session.exists()) return;`.

Neither rename door calls it. Both do only:

- `:16558-16559` (rename dialog) — `this.updateVisiblePoints(); this.repaintAutoLocList(false);`
- `:22242-22243` (CS-proposed rename) — the same two lines, under the comment *"This block is a copy of
  that one, so it had the same gap"*

So on a layout with no local path (`:3843` returns), one whose autonomy came from `autonomy.json` rather
than a diagram session, or one whose `setup.json` has never been saved, renaming or deleting a
locomotive leaves the timetable naming the old one — the symptom MT-149 was filed for.
`test/ui/testARenameReachesTheTimetableOnScreen.java` covers the signature keying, not the call site.
Adam's own railway is a local layout with a saved session, so this is probably the configuration he does
*not* hit.

### SVN-B13 — `rebuildHomeStations` dedups by locomotive; the square rule is only at the other door

`09777d4c` added the square sweep at the assignment door only (`Layout.java:1227-1235`):

```java
                if (other != p && other.isSamePlaceAs(p)) other.setHomeLoc(null);
```

`parseAuto` writes homes straight onto the Point (`Layout.java:7180`, `homeAt.setHomeLoc(home);` —
`7616d2a6` deliberately removed the loader's own check), and the shared funnel `rebuildHomeStations`
(`:1113-1141`) dedups only by locomotive:

```java
            Locomotive l = p.getHomeLoc();
            ...
            if (this.homeStations.containsKey(l)) { ... p.setHomeLoc(null); continue; }
```

Two *different* locomotives named on two copies of one square both survive into `homeStations` — the
DAY-A1 unsatisfiable state, reached through the door `7616d2a6` called *"the one door a person cannot be
warned at"*. `claimHome` (`:1088-1091`) has the square test; `rebuildHomeStations`, documented as
existing *"so the two cannot drift into deriving homes differently"*, does not.

Reachable from a hand-edited or imported configuration only: `AutonomyBuilder.homeCopy`
(`AutonomyBuilder.java:924`) emits one home per square, and `AutonomySession.setHome` sweeps per tile.

### SVN-B14 — the FR-045 autonomy function slots are written before OK is pressed

`src/org/traincontrol/gui/RightClickFunctionMenu.java:234-244`, inside `openEditDialog` — an OK/Cancel
dialog:

```java
            departure.addActionListener(ev ->
            {
                activeLoc.setDepartureFunc(departure.isSelected() ? editing[0] : null);
```

while `:279-282` applies everything else only on OK:

```java
            if (result == JOptionPane.OK_OPTION)
            {
                edit.doApply();
            }
```

Icon and trigger edits are discarded on Cancel; the two autonomy slots are already written. Its sibling
door applies at commit time — `src/org/traincontrol/gui/GraphLocAssign.java:253-254`.

**Gesture:** right-click a function button → Edit Function → tick "Autonomy Departure Function" →
Cancel. The slot has moved off whichever function held it, and that decides which function fires on
departure. The right-click *menu* copy (`:154`) must be immediate because it has no OK/Cancel; the
dialog copy need not be.

### SVN-B15 — the mid-route question parks the rest of the route, stop included, behind an unanswered modal

`MarklinRoute.java:668-670` calls `confirmRouteConflictMidway` **synchronously on the route's own
thread**, and `TrainControlUI.java:24261` → `askAboutRouteConflict` → `SwingUtilities.invokeAndWait`.
Everything after the conflicting accessory — `isStop`, `allFunctionsOff`, locomotive speeds, chained
routes — waits for a person to answer. `View.java:109`'s javadoc describes the answer as being about
*"the rest of its accessories"*; it is in fact about the rest of the route.

Same family as `SVN-A4`: the accessory half is the only part of a route that deserves to wait on a
human. This is the correct-direction sibling of that finding and should be fixed with it.

### SVN-B16 — the plain-accessory tile guard never got the protecting-signal half

`6b6e6bd4` gave `MarklinRoute.heldReason` (`:403-449`) a second half — signals protecting a square
somebody is standing on. The diagram's plain-accessory tile still asks only the locked-path set,
`LayoutLabel.java:383-389`:

```java
                                        Collection<Accessory> activeAccs = tcUI.getModel().getAutoLayout().getActiveAccs();

                                        if (activeAccs.contains(c.getAccessory()) ||
                                                (c.getAccessory2() != null && activeAccs.contains(c.getAccessory2())))
```

So clicking a protecting signal green **by hand** gets no warning, while a route that sets the same
accessory is refused or asked. The *route* tile on the same window does get both halves, because it goes
through `route.conflictingAccessoryAndReason()`. Fix-one-site-miss-the-twin, on the same window.

C-weight in isolation; B when a hand-driven train is standing at the platform that signal protects.

### SVN-B17 — AU-C12 is still open, and the Keyboard tab has no guard of any kind

`TrainControlUI.java:19085-19108`:

```java
        new Thread(() ->
            {
                this.model.setAccessoryState(switchId, getKeyboardProtocol(), b.isSelected());
            }).start();
```

No autonomy check, no power check, no confirmation, and `MarklinControlStation.setAccessoryState`
(`:3089-3117`) has none either. Filed by the author in `6b6e6bd4`'s own message and in
`docs/reviews/2026-08-25-autonomous-round.md:428`, and unchanged since. Recorded here because this week
added a third and fourth guarded door (`SVN-B16`'s route tile, and the mid-route check) without closing
it, so the set of doors that *do not* ask is now the minority and easier to miss.

---

## C — cosmetic, dead code, narrow edge cases

| | | |
|---|---|---|
| **SVN-C1** | `firstOnTheRailway` is a dead parameter, plumbed three levels | open |
| **SVN-C2** | `refreshAllProtectingSignals` has no production caller left | open |
| **SVN-C3** | The max-train-length check has no "does this layout measure anything" gate, though its twin does | open |
| **SVN-C4** | `AutoLocomotiveStatus`'s "autonomy will not choose this" dash was not swept for the terminus move | open |
| **SVN-C5** | `auditAgainstRuntime` has no exemption for the divergence `280ff08b` created | open |
| **SVN-C6** | The excluded-page filter was added to one check and not its twin | open |
| **SVN-C7** | Nine dead `AutonomyChecks.run` overloads, each defaulting an argument that switches a check off | open |
| **SVN-C8** | `execCopy` writes the whole setup to disk on every palette click | open |
| **SVN-C9** | The impassable badge discards its own placement and leaves `badgeDrawnAt` stale | open |
| **SVN-C10** | `parity/run.sh` has no lock, and the live-layout fingerprint is taken too late | open |
| **SVN-C11** | `isImpassable()` and `isShut()` are two names for one field; the colour disjunct is dead | open |
| **SVN-C12** | `versionWritten` inspects one of the two collections that write the version-2 shape | open |
| **SVN-C13** | The page-rename duplicate check sees two sentinels stored in `pageNames` | open |
| **SVN-C14** | Smaller items | open |
| **SVN-C15** | `accessoryHeldByAutonomy()` is computed and discarded at every human door | open |
| **SVN-C16** | One `OVERRIDE` disables the guard for accessories the operator was never shown | open |
| **SVN-C17** | `atomicRoutes` can in principle be flipped between the early release and `unlockPath` | open |

### SVN-C1 — `firstOnTheRailway` is a dead parameter

`fbc19cb9` replaced its only use with a comment:

```java
-        if (firstOnTheRailway && !isAutoRunning()) refreshAllProtectingSignals();
+        // The sweep that stood here is gone (OB-166).
```

The parameter survives at `Layout.java:4913` (`final boolean firstOnTheRailway = this.locomotiveThreads
.incrementAndGet() == 1;`), `:4919` (passed through the lambda), `:5014` (javadoc) and `:5019`
(signature). `grep -n "firstOnTheRailway" Layout.java` returns exactly those four lines: nothing reads
it. The `incrementAndGet()` is still needed; the flag derived from it is not.

### SVN-C2 — `refreshAllProtectingSignals` has no production caller

After `fbc19cb9` removed it from all three doors, `grep -rn "refreshAllProtectingSignals" src` returns
only the declaration at `Layout.java:6089`. The remaining callers are
`test/core/testAutoLayout.java:966` and `test/regression/testBothProtectingSignalsAreThrown.java:102,114`.
Keeping a public method for tests is defensible; leaving no note that it is test-only is the part worth
fixing, since `testBothProtectingSignalsAreThrown:305` records the mutation *"putting any of the three
refreshAllProtectingSignals() calls back fails this"* and a reader may reasonably think the method is
live.

There is a second limb. `refreshAllProtectingSignals` begins `this.signalAspects.clear();`
(`Layout.java:6091`), and it was the only thing that ever cleared that memo — so the memo now survives
across runs and across layout rearrangements for the life of the session. It is benign today only
because of the `acc.isRed() == claimed` half of the skip test at `:6046`; the `showing` half is now dead
weight that can only ever be stale. Either the memo or the method should go: a public method the
application no longer calls, still pinned by three tests, is the shape that makes the next reviewer
believe the sweep still happens.

### SVN-C3 — one length check gained the opt-in gate and its twin did not

`reversalsWithoutLength()` is gated at `AutonomySession.java:1931`:

```java
        if (!store.measuresAnyTrack()) return out;
```

on Adam's own condition, quoted in the commit: *"A railway that measures nothing has decided not to
model lengths."* Its six-day-older sibling `stationsWithoutMaxLength()` (`:4728-4732`) has no gate at
all:

```java
            Object value = getPointProperty(square, "maxTrainLength");
            int max = value instanceof Number ? ((Number) value).intValue() : 0;
            if (max <= 0) out.add(square);
```

All 30 stations in `configuration-Main.json` carry `maxTrainLength: 0`, so that is 30 more warnings on
top of `SVN-B1`'s 21 — on the list the file's own javadoc twice says is *"made useless by ordinary
things listed beside real problems"*.

### SVN-C4 — the commands panel's "autonomy will not choose this" dash was not swept

`src/org/traincontrol/gui/AutoLocomotiveStatus.java:139`:

```java
        return (p.isReversing() && p.isDestination()) || p.getExcludedLocs().contains(loc) ? " -" : "";
```

Its own javadoc says *"the full predicate is spelled out to match the rule in Layout."* `280ff08b` moved
the terminus rule out of `isPathClear` into three places — `pickPath` (`Layout.java:3733`),
`hasAutonomousDestination` (`:3475`) and `barredFromAutonomy` (`:4001`). This fourth mirror was missed.
Before the move a terminus never reached `getPossiblePaths` for a non-reversible locomotive; now it does,
and it is listed with no dash.

### SVN-C5 — the staging audit has no exemption for the divergence the terminus move created

`HomeStaging.auditAgainstRuntime` (`:602-687`) carries four documented exemptions so the instrument does
not cry wolf. Since `280ff08b`, `isPathClear` allows a terminus to a non-reversible locomotive while
`firstClearRoute` still refuses it unless the route turns (`mustBackIn`, `:1016`) — a deliberate
divergence the commit states outright. It is not exempted, so every such pair logs
`autolayout.warnStagingPlannerTooStrict` on any debug-mode Return Home, degrading the one instrument that
exists to find *real* divergence.

### SVN-C6 — the excluded-page filter reached one check and not its twin

`AutonomySession.java:1141` gained `if (store.getExcludedPages().contains(tile.getPage())) continue;`.
Its sibling at `:2977` did not:

```java
        for (Map.Entry<TileKey, List<TileKey>> pair : store.getProtectingSignals().entrySet())
```

`store.getProtectingSignals()` (`AutonomyCompanionStore.java:355`) is raw, unfiltered. A pairing whose
station or signal sits on an excluded page yields `graph.getTiles().get(tile) == null` → a `SIGNAL_GONE`
warning about a square not in play, named by raw `TileKey` (`AutonomyChecks.java:663-666`). The sample
setup has 6 pairings, none crossing an excluded page, so it is **not live today** — it becomes live the
moment a page carrying a pairing is switched off. `measuresAnyTrack()` (`AutonomyCompanionStore.java:993`)
has the same blindness: it scans raw `tileLengths.values()`, so lengths recorded only on excluded pages
would arm `SVN-B1`'s notice for a configuration that contains none of them.

### SVN-C7 — nine dead `AutonomyChecks.run` overloads

`AutonomyChecks.java:213-350`. Repo-wide there is exactly one call site, and it uses the fullest 20-argument
form (`AutonomySession.java:3203`); every test goes through `session.check()`. That leaves roughly 140
lines of unreached public API whose entire shape is `Collections.emptySet()` cascades — including
`Collections.<TileKey, Set<TilePorts.Side>>emptyMap()` for `barred` at `:332`, precisely the argument
whose absence caused OB-120. By the author's own rule (`AutonomySession.java:2961`): *"dead code that
looks load-bearing is worse than none."*

### SVN-C8 — `execCopy` writes the whole setup to disk on every palette click

`LayoutEditor.java:2317-2321` discards the return of `forgetTiles` and calls `rememberAutonomy(landing)`
unconditionally. `delete` was fixed for exactly this at `:3385-3395`: *"forgetCaptionsAt returns whether
it changed anything, and this ignored it — so deleting a square that had no caption still wrote the whole
setup to disk, every file of it."* Its sibling one method away was not. Reachable on every single-tile
paste and every palette drop onto a blank square; the layout folder is under OneDrive.

### SVN-C9 — the impassable badge throws away the placement it just computed

`TileAnnotation.java:1599-1610` clamps the badge inside the square and records where it went:

```java
        x = Math.max(1, Math.min(width - size - 1, x));
        y = Math.max(1, Math.min(height - size - 1, y));
        ...
        badgeDrawnAt = new int[] {x + size / 2, y + size / 2};
```

`:1646-1675` then ignores all of it for the impassable case, drawing from `trackCentre` instead. Three
consequences, all new since `e9435bfc` made a shut *station* impassable:

- The editor's corner rule (`if (editing && badge.isStation() && trackBends())`, `:1573`) no longer
  applies, so a shut station on a bend puts its cross back among the direction arrows the corner rule
  exists to avoid.
- The clamp is skipped. On an N–E bend `trackCentre` is `(3w/4, h/4)` and `mark/2 = min(w,h)/4`, so the
  arms land exactly on `x = width` and `y = 0` and the cross is clipped at the tile edge.
- `badgeDrawnAt` still points at the abandoned spot, and it is the anchor `paintTrainMark` uses
  (`:1082`) — so a shut station on a bend with a train placed draws the star in one corner and the cross
  at the track centre, the MT-124 disagreement the recorded-not-recomputed comment was written to
  prevent.

Narrow: needs the editor, a curved feedback station, and shut.

### SVN-C10 — two weaker limbs of `SVN-A2`

- `docs/tools/parity/run.sh:37,48,61,72` launches four JVMs with `-Dtraincontrol.anyReceivePort=true` and
  has no lock and no `RUNNING_JVMS` check of its own. The guard is asymmetric: parity makes a later
  battery refuse, but a battery does not stop parity.
- `battery.sh:205` takes `live_before=$(fingerprint)` *after* the run has already been allowed to start,
  so a folder damaged before the run (which is the state today — see `SVN-A1`) reads as clean.

### SVN-C11 — two names for one field, and a dead disjunct

`TileAnnotation.java:268-270` and `:291-293` are both `return shut;`, with javadoc insisting they mean
different things; `isShut()` has zero callers in `src/` or `test/`. `toString()` at `:326` collapses a
shut terminus, a shut parking berth and a shut plain point into the same string.

Relatedly, `:1528`:

```java
        Color colour = badge.isParking() || badge.isImpassable() ? POINT_INACTIVE : POINT_ACTIVE;
```

Both Badge doors compute `parking` as `inactive || !isAutoDestination` (`AutonomySession.java:4360`,
`AutonomyEditorPanel.java:5954-5955`), so `shut ⇒ parking` for every badge the application builds and the
new disjunct can never change the answer. `828b1ff1`'s premise — *"the same switched-off square drew blue
or orange depending on a setting that means nothing while it is switched off"* — does not hold for
production badges. Its mutation proof rests on `testAutonomyDiagramMonitor.java:1131-1133` constructing
`parking=false, shut=true`, a combination no door produces.

### SVN-C12 — `versionWritten` inspects one of the two collections that write the version-2 shape

`AutonomyCompanionStore.java:4612`:

```java
        for (List<TileKey> signals : stationSignals.values())
            if (signals != null && signals.size() > 1) return 2;
        return 1;
```

`translateTileListMap` (`:4915-4935`) emits `values.size() == 1 ? values.get(0) : new JSONArray(values)`
for **both** `stationSignals` and `blockedPoints`. A station held back by two squares therefore writes an
array into a file stamped version 1, contradicting the method's own contract. Harmless today —
`blockedPoints` arrived after `VERSION = 2` landed in `174178c5`, so no released build reads that field
with a string-only reader — but it is a latent contract violation.

### SVN-C13 — the page-rename duplicate check sees sentinels

`TrainControlUI.java:6430` tests `this.pageNames.containsValue(input)`, but `pageNames` is not only page
names: `:2106-2107` puts two sentinels into the same map, keyed `-1` and `-2`, holding
`Integer.toString(this.locMappingNumber)` and the key code of the active button. They survive the load
(`:6596` assigns the map wholesale with no sanitising). So after any start from an existing `UIState.data`,
naming a page the digits of the last-saved active page (`"3"`) or the key code of the active button
(`"81"` for Q) is rejected as "Duplicate page name". Keyed lookups are unaffected, since page numbers are
≥ 1.

### SVN-C14 — smaller items

- **Dead overload.** `HomeStaging.connected(Point, Point)` (`:1646`) has no caller in `src/` or `test/`
  since `09777d4c` replaced its last one.
- **Stale javadocs asserting a rule `20c30781` deleted.** `AutonomyBuilder.java:581` (*"HomeStaging.canRest
  refuses a terminus to a locomotive that cannot reverse"*) and `AutonomyChecks.java:713-717`
  (*"impossible for any other"*). `canRest` (`HomeStaging.java:1619-1637`) no longer holds that clause; the
  live rule is `mustBackIn` plus a turning route.
- **A misleading parameter.** `HomeStaging.blockedSensors(Map<Point,Locomotive> state)` (`:1218`) never
  reads `state`; it reads `this.start`. Reading `this.start` appears to be *correct* — a departed train's
  sensor should not become "unexplained" — so this is an invitation to a future reader to "fix" it into a
  bug, plus the per-state recomputation is waste. `astar`'s comment *"One set per state, not one per
  candidate move"* is wrong.
- **The home badge asks the Point where the ruling says the square.** `AutoLocomotiveStatus.java:240`,
  `locomotive.equals(at.getHomeLoc())`. Under `7616d2a6` a home lives on whichever copy `homeCopy` chose,
  so a train on another copy of its own home square reads as not-at-home here while `HomeStaging.atHome`
  (`:1791`, `home.isSamePlaceAs(where)`) says it is. Ten of Adam's 36 station squares are split. Teal
  badge only.
- **The menu's ellipsis vanishes in the one case where everything was left out.**
  `LayoutRightclickAutonomyMenu.java:284,365` — the `...` is added inside the loop over the *filtered*
  list, so if every reachable destination is switched off, `paths` is empty, the loop never runs, and
  neither the separator, the locomotive name, nor the way through to the autonomy tab appears, although
  `possible > 0`.
- **Two spellings of "which squares are stations" in adjacent checks.** `stationsWithNoSignal()`
  (`AutonomySession.java:2905`) walks `graph.getTiles().keySet()`; `stationsWithoutMaxLength()` (`:4724`)
  walks `reducer.getPoints().keySet()`.
- **`deletePage` removes one recorded page name; its sibling removes all.**
  `AutonomyCompanionStore.java:2384` `pageNamesWhenWritten.values().remove(page);` versus `:654`
  `removeAll(goneByName)`. `Collection.remove` on a values view drops a single mapping.
- **`restoreSetup` is less shape-checked than the note reader that feeds it.** `unfinishedEdit`
  (`:4592-4598`) checks `shared`, `configurations` and version; `restoreSetup` (`:1949-1965`) then does
  `copies.getJSONObject(name)` and `was.getString("active")` *after* `clearShared()` and
  `configurations.clear()`. A malformed note throws with the store half-emptied.
- **`importBundle` lets the exporter's page names win per id.** `AutonomyCompanionStore.java:1793-1810`.
  Any exporter page name that is not one of mine makes `AutonomySession.pagesSafeToJudge()` (`:4523-4533`)
  false, so `save()` declines to reconcile and raises the DR-B10 warning on every editor save afterwards,
  naming a page the operator has never had — and `sharedFields()` re-writes it, so it persists across
  reloads.
- **UI mutation from the image pool.** `TrainControlUI.java:9091` (inside `ImageLoaderLoc.submit`, opened
  at `:9039`) calls `locIcon.setIcon(...)`, `setText("")`, `setVisible(true)` off the EDT; the same shape at
  `LocomotiveSelectorItem.java:40-58`. Both match a pre-existing pattern, and the FR-054 placeholder
  branch beside them is a *new* instance of it.
- **`liftedForTrain` is not volatile** (`LayoutLabel.java:1031,1083-1085`) beside `autonomyOverlay` and
  `autonomyAnnotation`, which are (`:94,:98`), and is written from the monitor's publishing thread.
  Mitigated by OB-159's third pass.
- **Colour comment gone stale.** `TileAnnotation.java:1410` `TRACE = new Color(255, 214, 0)` and `:1429`
  `ARRIVAL = new Color(255, 205, 0)` differ by nine units of green only, while ARRIVAL's javadoc still says
  *"nor a tested path (yellow) … A colour of its own for a question of its own."*
- **`NOTICE`'s javadoc claims an ordinal it no longer has.** `AutonomyChecks.java:50-53` says *"Last on
  purpose — findings are sorted by this ordinal"*; `INFO` was added after it.
- **Add got a reason-tooltip and Delete twenty lines away did not.** `RightClickPageMenu.java:107-136`,
  under a comment claiming *"one that is greyed with an explanation says what to do about it."*
- **"Map to Page…" resolves the target by display string and then clears it unasked.**
  `TrainControlUI.java:17102-17135` keys a map by displayed name, and `page.ui.labelDefaultPageName` is
  byte-identical to `page.ui.pageNo` (`messages.properties:721,744`); `mapLocomotivesToPage` (`:7825-7826`)
  then calls `doClearCurrentPage()` with no confirmation.
- **`timetableSignature` guards a null locomotive; the row loop in the same method does not.**
  `TrainControlUI.java:23903` versus `:23979`. `TimetablePath.java:175-181` appears to refuse a null loc,
  so one side is dead or the other is a latent EDT NPE — they should agree.
- **`switchLocMapping`'s `else` branch cannot run.** `TrainControlUI.java:6273` `if (this.numLocMappings > 1)`,
  with `MIN_LOC_MAPPINGS = 2` and a constructor clamp at `:611-612`. It would hide Next/Prev permanently
  if it ever fired.
- **`arriveAt` clears two undo stacks and leaves their twins.** `LayoutEditor.java:5680-5681` clears
  `previousLayoutComponents`/`Redo` with a comment saying a carried-over snapshot *"would have been a
  data-loss bug"*; `previousCaptions`/`Redo` are not cleared and have the same property. Self-heals via the
  trim at `:4946-4949`, so this is fragility rather than a live defect — the invariant is nowhere stated
  at the clear site.

### SVN-C15 — `accessoryHeldByAutonomy()` is computed and thrown away at every human door

`MarklinRoute.java:576` computes it; it is read only at `:578` (`&& auto`) and `:625` (`auto && …`). With
`auto == false` the result cannot affect anything, but the call still walks every `Point` in the layout
and calls `getActiveAccs()` once per accessory command. Pure cost on the door a person is standing at.

### SVN-C16 — one `OVERRIDE` disables the guard for accessories the operator was never shown

`MarklinRoute.java:650` — `String[] now = override ? null : heldReason(rc);`. After one "OK", a
*different* accessory locked by a *different* dispatch five seconds later is set with no check.
`execRouteOverridingConflicts`'s javadoc (`:293-309`) describes the answer as covering "this route's
conflict", singular. Recorded as stated design with an overselling javadoc rather than as a defect —
asking per accessory is explicitly rejected at `:664-668` as unusable — but the javadoc should say what
the override actually covers.

### SVN-C17 — `atomicRoutes` can in principle be flipped between the early release and `unlockPath`

`TrainControlUI.java:18804-18809` guards on `isAutoLayoutRunning()` → `Layout.isRunning()`, which does
include hand dispatches, so the ordinary path is safe. The residual window is a check-then-set on the
EDT against a dispatch starting concurrently. If it ever flipped true→false mid-run, `unlockPath`'s
`alreadyGivenUp.contains(e)` branch (`Layout.java:3236-3260`) would skip a `setUnoccupied()` for an edge
that was never released early — rail held for the session. Not worth a fix; worth a sentence at
`setAtomicRoutes`.

---

## D — not defects

No status table: these are checks that came back clean, and things that looked wrong and are not. None
carries a disposition because none is outstanding. Two of them — `SVN-D1` and `SVN-D7` — are things this
pass nearly filed and then withdrew, and they are the entries most worth reading if you want to
calibrate how much to trust the rest.

**SVN-D1 — the terminus move was swept properly.** `280ff08b` removed the non-reversible/terminus refusal
from `isPathClear` and added it to `pickPath` (`Layout.java:3733`), `hasAutonomousDestination` (`:3475`)
and `barredFromAutonomy` (`:4001`). `isChoosableByAutonomy` (`:3960`) correctly passes a null locomotive
so the per-locomotive clause does not fire on a square-level question, and `canReachAnyDestination`
(`:6158`) takes no locomotive and correctly omits it. **The absence of the rule from `isPathClear` is not
filed** — it is the tier doctrine working as written. The one mirror that was missed is `SVN-C4`.

**SVN-D2 — the eight message bundles hold.** All eight have 1,865 keys; the key sets are byte-identical to
`messages.properties` for de/fr/es/it/nl/da/pl; every file is pure ASCII (`grep -c '[^ -~\t]'` returns 0
for all eight); and the `{n}` placeholder index set matches English for every key in every language (0
mismatches). The two keys added on 2026-09-01 — `autolayout.errorTrainTooLongToReverse` and
`autosetup.ui.checkReversalNeedsLength` — are present in all eight.

**SVN-D3 — every literal `I18n` key resolves.** 1,036 distinct literal keys across `src`; seven appear
missing and all seven are false positives: `autolayout.ui.pathPreference`, `autolayout.ui.tooltip
.pathPreference`, `autosetup.ui.facing`, `autosetup.ui.side` and `route.kind.` are concatenation prefixes
(`autosetup.ui.facingN`… and `autosetup.ui.sideN`… exist at `messages.properties:1503-1506,1808-1811`),
and `error.invalidLogin`/`log.userLogin` are the usage examples in `I18n`'s own javadoc.

**SVN-D4 — `ROUTING_ORDER` covers every `PathPreference`.** Ten enum constants, ten entries
(`TrainControlUI.java:8298-8310`). `costOf`'s switch handles eight and correctly returns 0 for the two
random rules.

**SVN-D5 — the `LayoutSandbox` sweep is complete.** Every test class that constructs a `TrainControlUI`
also uses `LayoutSandbox` — 11 for 11, checked by intersecting `grep -rln "new TrainControlUI" test` with
`grep -rln "LayoutSandbox" test`. The two classes that read `cs2_sample_layout` pass it to
`LayoutSandbox.open(File)`, which copies it. `SVN-A1` did not come through a hole in this sweep.

**SVN-D6 — the battery-membership guard is sound.** `testEveryTestIsInTheBattery` pins `build.xml`
membership, requires every exemption to name a real file so a rename cannot leave a silent one, and
records why `testTheParkingBerthsGetTheirTrainsBack` is excused.

**SVN-D7 — the orphaned javadoc above `locomotiveInBlock` is known and ratcheted.** `Layout.java:5923-5933`
documents `locomotiveInBlock(String block, Point except)` (`:6108`) and sits stacked above
`refreshProtectingSignal`'s own javadoc. `testJavadocsAreAttached` names this as one of "the three that
matter" and counts it in `ALLOWED = 93` with `Layout.java (3)` in the per-file list. Deliberately
accepted, not a new finding.

**SVN-D8 — the new concurrency structures are sound.** `takingPath`, `clearedEdges`,
`locomotiveMilestones` and `locomotivePendingS88` are all `ConcurrentHashMap` (`Layout.java:735-742`), the
per-locomotive cleared set is `ConcurrentHashMap.newKeySet()` (`:5167`), and `locDeleted` (`:975-985`)
removes the locomotive from all four plus `updatePendingS88(l, null)` to release waiters. The claim in
`takingPath` is dropped on every exit: three failure paths in `configureAndLockPath` (`:2922,2935,2954`),
the `!result` branch of `executePathInternal` (`:5155`), the success branch (`:5172`), and `unlockPath`
(`:3202`).

**SVN-D9 — the registry sweep in `AutonomyCompanionStore` is complete.** All twelve declared collections
appear exactly once in `kept()` (`:4374-4405`), and all ten mechanical sites walk it: `sharedFields`/save,
`clearShared`, `clear`, `readShared`, `renamePage`, `deletePage`, `moveTiles`, `snapshotPage`,
`restorePage`, `forgetSquares`. Save and load both key off `k.json`, so a field cannot be written-but-not-
read. `HELD_FIELDS` (`:3603-3618`) names all twelve with correct shapes. `TileKey` identity is by page
**name** in memory and page **id** in `setup.json`, converted through `toStored`/`fromStored`
(`:4692,4718`), and every rekeying path handles the `#state,index` suffix. The one gap found is
`SVN-B8`.

**SVN-D10 — the crop dialog's third data loss was looked for and not found.** `crop(...)` writes the view
back only on a non-null result; `recropLocIcon` carries the old note forward and withholds a view measured
over a different picture; `cropLocIcon` refuses to write a note when the source was itself a crop;
`deleteLocIconFile` takes the sidecar with the file; `superseded` is read before assignment and deleted
after. The one asymmetry — a crop produced but not writable still falls back to the uncropped photo and
deletes the old crop (`TrainControlUI.java:23167-23172`) — is documented as intended.

**SVN-D11 — the FR-033 fifty-page cap has no stale bound.** Enforced at `TrainControlUI.java:1366-1374`,
deliberately absent from the load loop (`:6537-6556`), and the constructor read has a floor and no ceiling,
so old and new preferences both load. No hardcoded 10 or 50 elsewhere: the digit keys are *function* keys
(`:15499-15535`) and page switching is `,`/`.` (`:15588-15597`), so "ten pages, ten digits" (`:337-340`) is
stale prose rather than a bound.

**SVN-D12 — the drawing layer's Graphics hygiene is clean.** `TileAnnotation.paint` saves and restores
font, colour, stroke, composite and the AA hint (`:836-843`); `paintBadgeOverRun`, `TileOverlay.paint`,
`TileOverlay.paintTrain`, `StationCaption.paintOnEnd`, `StationCaption.paintComponent`,
`LayoutLabel.paintComponent` and `paintTrainOverCaptions` each work on a disposed `g.create()` copy or
restore what they touch. No model mutation from any paint path. `Badge.equals`/`hashCode` were both
extended with `shut` (`:301,308-309`), so the annotation cache still repaints when only that changes.

**SVN-D13 — caption geometry and the port rotation are consistent.** `StationCaption.place()`,
`getPreferredSize()`, `pillBounds()` and `contains()` all derive from the same `width()`/`lineHeight()`/
insets, in both orientations; `drawnText()` rotation is a single-pass substitution with no compounding;
`TilePorts`'s `(4 - orientation)` rotation is shared between `ports()` and `numOrientations` rather than
duplicated.

**SVN-D14 — the shift/trim matrix against `LayoutDiagram` normalisation.** The `startRow == 0 || startRow >
sy - 2 → miny` arithmetic looked like a desync against `setupShift`, but `checkBounds` forces
`minx = miny = 0` while `edit` is true (`LayoutDiagram.java:190-251`), so the maps and the shifts agree row
for row. `moveTiles(moves)` with no `builtOver` is safe: `AutonomyCompanionStore.moveTiles` (`:2519-2531`)
derives the landing set itself.

**SVN-D15 — `59b2db48`'s nine off-EDT dialogs.** Each moved chooser is on the EDT and the residual thread
bodies touch only the model or marshal back. `LocomotiveFunctionAssign.reset` mutates the locomotive on the
EDT *before* handing only `syncWithCS2` to a thread, preserving the old ordering; `syncWithCS2`
(`:8507-8570`) is re-entrancy-guarded by `syncInFlight`, and the `resetRouteSpinners()` it calls before the
EDT check touches only concurrent collections.

**SVN-D16 — every door into a path and into a route funnels through one method.** Paths: autonomy
`runLocomotive` (`Layout.java:3561`), the timetable (`:4617`), the Auto-tab double click
(`AutoLocomotiveStatus.java:1027`), the diagram right-click (`LayoutRightclickAutonomyMenu.java:340`) and
staging (via `executeTimetable`) all reach `executePath`, so they inherit every guard added there.
Routes: the s88 monitor, the routes-tab play button and cell click, the right-click menu, the diagram
route tile and `MarklinControlStation.execRoute(String)` all reach `MarklinRoute.execRoute`, so the
*model* guard covers all of them. `RouteEditorFrame`'s Test button tests conditions only, and
`LayoutLabel:296`'s `execSwitching` is the feedback branch, not a route. The holes found are above the
funnel, not in it — `SVN-A4`, `SVN-B7`, `SVN-B16`, `SVN-B17`.

**SVN-D17 — the aspect logic is not testing a cosmetic field, and the occupancy count balances.**
`!rc.getSetting()` at `MarklinRoute.java:445` is GREEN/STRAIGHT and equals `!Accessory.isThrow`;
`Accessory.isRed()` is plain `switched` (`Accessory.java:257`), not `accessoryType`. The
signals-are-switches trap is absent from this path. Separately, `Edge.setOccupied`/`setUnoccupied`/
`setLockedEdge*` are all synchronized on the Edge with a floored count (`Edge.java:412-472`), so the
early tail release running under `synchronized(activeLocomotives)` while `configureAndLockPath` runs
under `synchronized(this)` is not a lost update; every raise traced has exactly one release, including
the OB-164 early release giving up the edge *and* its lock edges, and both `unlockPath` branches
checking `clearedEdges` before releasing again (`Layout.java:3236,3283`). No leak found.

**SVN-D18 — `6b6e6bd4`'s fix holds where it was applied.** At the s88 door, `skipAccessories` drops only
accessories: stop, functions-off, lights, speeds and chained routes still run. `isAutonomyRunning()` is
`isRunning() || isStagingInProgress()` (`MarklinControlStation.java:2892-2900`) and `Layout.isRunning()`
counts `locomotiveThreads`, so the route guard is live during a hand dispatch and during staging — the
hand-dispatch door is not the hole. `getAccessoryByName` never creates
(`MarklinControlStation.java:2024-2046`), and the guard and `Layout.refreshOneSignal` resolve through the
same Signal/Switch prefix fallback. THREE_WAY rows are stored as two `RouteCommandAccessory` entries, so
both drives pass through `heldReason`. The "Dummy Loc shares the waits" trap is avoided: the route
monitor uses the two-argument `waitForOccupiedFeedback` with no advisory (`Locomotive.java:726-734`).

---

## Open questions — things that need running, which this pass did not do

1. **`SVN-A1`: which of the working-tree changes to `cs2_sample_layout` are Adam's own?** The caption-anchor
   moves in `setup.json` look like deliberate label drags. A revert should be preceded by a copy.
2. **Does `test/core/testHomeStaging.java` still pass after `280ff08b`?** `SVN-C5` predicts the
   `auditAgainstRuntime`-expects-0 assertions (`:2610, 2631, 3751`) are the ones at risk on a fixture with a
   terminus and a non-reversible locomotive; `testATerminusIsRefusedToATrainThatCannotReverse` is the other
   candidate.
3. **Do the 21 + 30 warning counts in `SVN-B1` and `SVN-C3` match what the editor renders?** They were
   derived from the JSON, not from a run.
4. **Is the autonomy editor's grid ever built while `layout.isEdit()` is false?** `LayoutGrid.java:1296-1298`
   builds the caption-drag source key from raw grid indices while `:865-867` uses the page offsets. They
   agree only because `LayoutEditor.render()` calls `setEdit()`, which forces the offsets to zero — but
   `setAutonomyMode`'s javadoc says it deliberately does *not*, and `LayoutEditor.java:5545` calls
   `setEdit(false)` on a page change in autonomy mode. If the grid can be built in that state, every
   caption drag on a padded layout targets the wrong square.
5. **Can Adam's live configuration ever have a null autonomy session while a timetable is loaded?** That is
   the whole blast radius of `SVN-B12`.
6. **Is a second `LayoutEditor` actually constructible** through the re-enabled button in `SVN-A3`? The two
   guards were traced; the constructor was not exercised.
7. **Does `testBothProtectingSignalsAreThrown` still assert anything about production behaviour?** It calls
   `refreshAllProtectingSignals()` directly, and that method no longer has a production caller
   (`SVN-C2`). Its mutation note at `:305` describes putting calls back that are gone.
8. **`SVN-A4` and `SVN-B15` are uncovered by construction.** No behavioural test anywhere exercises
   `confirmRouteConflictMidway`; `testEditorSurfaceRules.java:1113-1145` only reads the source text. A
   human-door twin of `testTheStopInARefusedRouteStillRuns` is the test to write first.

---

## How this pass was distributed, for calibration

Seven independent read-only passes ran in parallel over separate subsystems — home staging and the
terminus rules; the companion store and its persistence; the diagram editor and what it tells autonomy;
the drawing layer; the autonomy session and its checks; routes, signals and the accessory doors; and
`TrainControlUI` with the locomotive-side dialogs — with `Layout.java`, the message bundles, the
fixtures and the harness read directly.

Three of the seven independently reached the same conclusion about `17cad1fe`'s notice-versus-guard
mismatch (`SVN-B1`) from three different directions, which is the strongest single signal in this
document. Every A finding and the load-bearing limb of each B was re-derived by hand before filing:
`SVN-A1` by parsing both JSON versions, `SVN-A2` by reading the `case` arms and grepping `build.xml`,
`SVN-A3` by tracing `layoutEditingComplete` through to `syncLayouts`, `SVN-A4` by reading the refusal
branch at both human doors against `MarklinRoute`'s loop, `SVN-B7` by opening all three route doors, and
`SVN-B1`'s counts by measuring `cs2_sample_layout` as data.
