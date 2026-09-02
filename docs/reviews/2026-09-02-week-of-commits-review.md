# A week of commits, reviewed: 2026-08-26 to 2026-09-02

**Status:** open

**Citation prefix:** `WK3`. Cite findings from this document as `WK3-A1`, `WK3-B2`, and so on. `WK` and
`WK2` are taken elsewhere; `WK3` is not used by any other document in this folder.

| | |
|---|---|
| **Reviewed** | branch `autonomy-diagram-r0`, v3.0.0 |
| **HEAD named in the briefing** | `cf048f9b` — "Disposition the 2026-09-01 round" |
| **HEAD when this pass finished** | `54a70c03` — "Two disposition corrections: SVN-B2 was ruled on, TCX-B2 was not". It landed during the pass and is documentation only; no finding below rests on it, and no finding below was invalidated by it. |
| **Scope** | the 154 commits since 2026-08-26, ending at `cf048f9b` |
| **Working tree during this pass** | `Readme.md` and three files under `cs2_sample_layout/` modified and uncommitted. Nothing in this pass wrote to the repository except this file, and `cs2_sample_layout/` was opened read-only. |
| **Date** | 2026-09-02 |

---

## Method, and what this pass did NOT look at

**No tests were run, nothing was compiled, no JVM was started, and the application was not launched.**
Every finding below was reached by reading: `git log` / `show`, `grep`, file reads, and reading the
operator's JSON as data. Where a claim depends on real data it was checked against
`test/operator_layout/config/` and, read-only, against `cs2_sample_layout/config/`.

154 diffs is more than one pass can read closely, so this is what was chosen and what was therefore
skipped.

**Read closely:**

- **The five newest commits** (`1cfdf370`, `87b6c10a`, `975f157d`, `8d1c17ca`, `cf048f9b`), line by
  line, source and comments, plus every method they touched and every caller of every method whose
  signature or semantics they changed. Four of the seven findings are in these five commits.
- **The files amended most often this week**, on the briefing's reasoning that a design amended four
  times has not settled: `Layout.java` (30 commits), `AutonomySession.java` (26),
  `LayoutEditor.java` (20), `AutonomyEditorPanel.java` (16), `HomeStaging.java` (14),
  `AutonomyChecks.java` (9). `TrainControlUI.java` (46) was sampled rather than read whole — the
  guard/affordance sites, the export/dialog rework and the page-switch teardown.
- **Everything made asynchronous or moved between threads**: `59b2db48` (nine dialogs off the event
  thread), `1cfdf370`'s `SVN-A3` page-switch reordering, `ff6368bb`'s tail-bookkeeping split.
- **The guard-versus-affordance pattern**, deliberately: every guard added or moved this week was
  read against the thing that offers the action.
- **Anything that writes the operator's accumulated work**: `AutonomyCompanionStore`'s snapshot and
  restore, `setPointProperty`, the new bulk home clear, `getLocalLayoutPath`'s null change, and the
  RC-A3/A4/A5 layout-folder work.

**Not looked at, and not claimed to be covered:**

- The **test suite**. `test/` was opened only to see whether a rule was pinned, never reviewed. A
  dedicated test-suite pass covered this in the 2026-09-01 round (`TCX`).
- **Rendering and the diagram's appearance**: `LayoutGrid`, `StationCaption`, `LocIconCropDialog`,
  `UsageHistogram`, `LoadingSpinner`, the tab icons and `tools/tab-icons.py`. That is roughly
  fifteen commits of the week, entirely unread here.
- **The eight message bundles** beyond checking that every key added this week exists in all eight
  and that no non-ASCII slipped in. Wording and translation quality were not reviewed.
- **`build.xml`**, `docs/tools/*.sh`, the triage app, and `docs/manual-tests/`.
- **The 2026-09-01 documents' C-level tails.** They are recorded as not dispositioned in
  `FX2`; nothing here re-opens or re-files them.
- **The parity harness** (`cb280a5e`, `59a9d62` and siblings) and the `docs/reviews/` prose commits.

Two other reviewers in this round cover the last three days and the last day. Where this pass and
theirs overlap, that is intentional; where it does not, the earlier half of the week
(2026-08-26 to 2026-08-30) is the part only this pass looked at.

---

## Summary

| Finding | Severity | One line | Where |
|---|---|---|---|
| `WK3-A1` | A | `protectsAnOccupiedSquare` is `synchronized` and is called on the event thread, four lines from the method whose javadoc says that exact call froze the window and the Stop button | `Layout.java:6134`, `LayoutLabel.java:400` |
| `WK3-B1` | B | The diagram's accessory tile asks the protecting-signal rule without the ASPECT half, so setting a signal RED — the protective direction the route guard deliberately permits — now raises a warning | `LayoutLabel.java:400-409` vs `MarklinRoute.java:460-475` |
| `WK3-B2` | B | `TCX-A2`'s room check in the staging planner sits after the `seen` marking, so the "another route may be longer" escape it relies on is closed by `alreadyReached` in exactly the longer-approach case | `HomeStaging.java:1017-1049` |
| `WK3-C1` | C | `SVN-B10` widened the Start guard to `hasErrors()` and left the affordance on `errorCount()`, and left a javadoc saying they read the same number | `TrainControlUI.java:5183` vs `:20159-20167` |
| `WK3-C2` | C | `SVN-B7`'s stated hazard is already prevented by `Route.setExecuting()`; and the new guard reads a set held for 600 ms after a route ends, so two doors log "already running" about a route that has finished | `TrainControlUI.java:16115`, `:24541-24560`, `Route.java:115` |
| `WK3-C3` | C | `AutonomySession.tilesWithAHome()` is a third copy of the same "which squares carry a home" walk | `AutonomySession.java:3364`, `:4041`, `:4083` |
| `WK3-D1` | D | **Withdrawn**, originally a C: the "no page could be read" message did name a page — the key was corrected by `72234e18` later the same day | `MarklinControlStation.java:552` |
| `WK3-D2`..`D11` | D | Ten checks that came back clean, including two claims I traced and dropped | below |

---

## A — wrong behaviour on the layout, or data silently lost

### `WK3-A1` — the protecting-signal guard puts the event thread on the Layout monitor, which is the freeze `IAR-B2` removed

**FIXED 2026-09-02 (`e6791631`).**  Found independently as `DY3-A1`.  `Layout.protectsAnOccupiedSquare` is not `synchronized` any more, and the method now carries the reason: it is called from the event thread four lines below `getActiveAccs`, whose javadoc records that exact call freezing the window, because `configureAndLockPath` holds the monitor across per-command sleeps.  Introduced by `87b6c10a` (2026-09-02 03:27).

`87b6c10a` moved the "is this a signal protecting an occupied platform" rule out of `MarklinRoute` and
onto `Layout`, so that the route and the diagram's accessory tile could both ask it. The method it
created is synchronized on the Layout:

`src/org/traincontrol/automation/Layout.java:6134`

```java
    synchronized public boolean protectsAnOccupiedSquare(Accessory accessory)
    {
        if (accessory == null || this.control == null) return false;

        for (Point point : this.getPoints())
        {
```

It is called from the diagram tile's click handler, inside the `invokeLater` at
`LayoutLabel.java:341` — so on the event thread — in the branch that runs while autonomy is going:

`src/org/traincontrol/gui/LayoutLabel.java:384-409`

```java
                                    else if (tcUI.getModel().hasAutoLayout() && tcUI.getModel().isAutonomyRunning())
                                    {
                                        Collection<Accessory> activeAccs = tcUI.getModel().getAutoLayout().getActiveAccs();
                                        ...
                                        boolean protecting =
                                            tcUI.getModel().getAutoLayout().protectsAnOccupiedSquare(
                                                c.getAccessory())
```

**The method immediately beside it carries the warning against doing this.** `getActiveAccs()` — the
call on line 386, four lines above the new one — has this javadoc:

`src/org/traincontrol/automation/Layout.java:854-871`

```java
     * NOT synchronized, deliberately (IAR-B2).
     *
     * This is read from the EVENT THREAD, by the layout label's click handler, and only in the branch
     * that runs while autonomy is going - which is exactly when `configureAndLockPath` holds this
     * object's monitor across its per-command sleeps. So throwing a turnout by hand during a run froze
     * the whole window, Stop included, for the length of the configuration phase. OB-079 fixed three
     * sites of this shape and its list was one short.
     ...
     * a frozen Stop button is not.
```

Every clause of that description is true of the new call: same handler, same branch, same monitor.

**The monitor really is held for seconds.** `Layout.java:572-580`, at `pendingS88Monitor`:

```java
     * It used to be, and that put a RUNNING train behind a train that was still being dispatched.
     * configureAndLockPath holds the layout monitor across its whole lock loop - deliberately, because
     * claiming a path has to be atomic - and that loop sleeps CONFIGURE_SLEEP per edge and again per
     * accessory inside configureEdge, so it is held for seconds on a long path.
```

`configureAndLockPath`'s body confirms it: `Layout.java:2899` opens `synchronized (this)` around the
whole lock-and-configure loop.

**And this is the second time it has been refused.** `getEdges()`'s javadoc records the last attempt
to take this monitor from the UI, and why it was reverted — `Layout.java:5877-5890`:

```java
     * FIRST, it puts the event thread on the Layout monitor. `getPoints` is called from five places
     * in the UI - the editor, the viewer, three menu paths - and a dispatch holds that monitor across
     * its per-command sleeps. That is precisely the freeze IAR-B2 had just removed from
     * `getActiveAccs`, reintroduced by a different door in the same commit.
     *
     * SECOND, and this is the one the revert note first missed, it is an outright DEADLOCK rather
     * than a freeze.
```

`protectsAnOccupiedSquare` iterates `this.getPoints()` — the very collection that note is about —
under the monitor that note says the event thread must not take.

**What happens.** During an autonomy run, a click on any switch, signal or uncoupler tile on the
diagram blocks the event thread until the current dispatch finishes configuring its path. The whole
window is frozen for that time, including the Stop button. The click is the operator reaching for a
turnout that did not take its command, which is the situation this dialog exists for.

**Severity.** Filed as A rather than B because the thing that stops responding is the emergency stop,
and this class's own javadoc uses "a frozen Stop button" as the reason the sibling call is not
synchronized. It is wrong behaviour on the layout in the sense that matters: the operator cannot
intervene. A reviewer who reads "the UI recovers when the dispatch ends" as merely a stall would file
it B; the argument is recorded rather than assumed.

**The second door.** `MarklinRoute.heldReason` at `MarklinRoute.java:475` asks the same method from
the route thread, once per accessory command. That is not the event thread, so it does not freeze the
UI — but it does serialise route execution against dispatches for the length of a lock loop, per
command, where the previous private implementation walked `getPoints()` without the monitor. Same
fix.

**The deadlock half is NOT claimed.** The `getEdges` note names an AB-BA deadlock as its second
reason. I traced the outbound half — a dispatch holds the Layout monitor and reaches
`MarklinAccessory.setSwitched` → `TrainControlUI.repaintSwitch`, which is synchronized on the UI — but
could not find a concrete thread that holds the UI monitor and then wants the Layout monitor through
this new door, because the click handler holds no UI monitor. The freeze is certain; the deadlock is
a hazard this door reopens rather than one demonstrated here.

**Suggested shape of the fix**, for whoever takes it: `protectsAnOccupiedSquare` reads `points` and
each Point's protecting-signal list. Neither is mutated during a run in a way the monitor was
protecting here, and `getPoints()` is already documented as a live unsynchronized view for exactly
this class of reader. Dropping `synchronized` and saying why, in the same words `getActiveAccs` uses,
is the smaller change.

---

## B — incorrect results, or crashes in specific configurations

### `WK3-B1` — the accessory tile asks the protecting-signal rule without the aspect, so it warns when the operator makes a signal RED

**FIXED 2026-09-02 (`e6791631`).**  Found independently as `DY3-B1` and `D3F-C4`.  `aboutToClearProtection` returns false when the accessory `isStraight()` - a click on a straight signal is about to turn it to danger, which is the protective act the route door does not refuse.  Covered by `testEditorSurfaceRules`.  Introduced by `87b6c10a`.

`87b6c10a`'s stated purpose was that the two doors ask one question. They still do not, and the half
that is missing is the one a previous review already removed as over-strict.

The route door asks the rule **only for the green direction**, and the comment above it explains at
length why:

`src/org/traincontrol/marklin/MarklinRoute.java:460-479`

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
        ...
        if (!rc.getSetting() && this.network.getAutoLayout().protectsAnOccupiedSquare(accessory))
```

The tile door asks it with no aspect term at all:

`src/org/traincontrol/gui/LayoutLabel.java:400-409`

```java
                                        boolean protecting =
                                            tcUI.getModel().getAutoLayout().protectsAnOccupiedSquare(
                                                c.getAccessory())
                                            || (c.getAccessory2() != null
                                                && tcUI.getModel().getAutoLayout()
                                                    .protectsAnOccupiedSquare(c.getAccessory2()));

                                        if (activeAccs.contains(c.getAccessory()) ||
                                                (c.getAccessory2() != null && activeAccs.contains(c.getAccessory2()))
                                                || protecting)
```

**The aspect is knowable at that point.** A click on a signal tile toggles:
`LayoutDiagramComponent.execSwitching()` at `:128-131` calls `this.accessory.doSwitch()`, and
`Accessory.doSwitch()` at `:156-166` is `isStraight() ? turn() : straight()`. So the command the click
is about to send is fully determined by the accessory's current state before the dialog is raised —
green becomes red, red becomes green.

**What happens.** With autonomy running and a train standing at a platform, clicking that platform's
protecting signal to put it to RED — the safe direction, the one the protection mechanism itself
would command — raises

```
layout.ui.confirmAccessoryProtecting=This signal is protecting a platform a train is standing at.  Switch it anyway?
```

with OK and Cancel. The route guard, given the identical command, does not object. So the two doors
still ask different questions, and the hand door is now the stricter of the two — which is the
direction this project's record says costs most: an over-strict warning on the protective action can
be answered "Cancel", and then the signal stays green over an occupied platform because the operator
was warned off setting it red.

**Reachability on the real railway.** `test/operator_layout/config/autonomy/setup.json` records
`stationSignals` on 7 squares, so the rule has live subjects; it fires whenever one of those platforms
is occupied during a run, which is the ordinary state.

**The fix is one term**: `c.getAccessory().isStraight()` (about to be turned red) should suppress the
protecting half, leaving the locked-path half unchanged. That mirrors `!rc.getSetting()` exactly. Both
accessories of a three-way need the same treatment.

---

### `WK3-B2` — the staging planner's new room check is applied after the state is marked seen, so the escape it relies on is already closed

**FIXED 2026-09-02 (`e6791631`).**  Found independently as `D3F-B1` and `RT3-B1`, with `TS3-B7` filing the missing test.  `firstClearRoute` asks for the room before it records the arrival in `seen`, so a route refused for want of room leaves the destination reachable by the longer approach the escape clause promises.  Covered by `testALongerApproachIsStillTriedWhenTheShortOneHasNoRoom`, mutation-confirmed.  Introduced by `975f157d` (2026-09-02 03:37).

`975f157d` lifted the reversal-room rule into `Layout.measuredRoomToReverseInto` and gave it to the
staging planner, so the planner would stop offering berths the runtime then refuses. The call is a
`continue`, and the commit and the comment both rest on the same justification:

`src/org/traincontrol/automation/HomeStaging.java:1045-1049`

```java
                    // `continue` rather than a refusal: another route to the same berth may be longer,
                    // and a longer approach is more room.
                    Integer room = Layout.measuredRoomToReverseInto(route, loc);

                    if (room != null && loc.getTrainLength() > room) continue;
```

**The state is marked seen twenty-six lines earlier, before the room test runs**:

`src/org/traincontrol/automation/HomeStaging.java:1017-1022`

```java
                String key = next.getUniqueId() + (turned ? "/turned" : "/straight");

                if (alreadyReached(seen, key, commands)) continue;

                if (!seen.containsKey(key)) seen.put(key, new ArrayList<>());
                seen.get(key).add(commands);
```

`alreadyReached` prunes a later arrival whose command map agrees with an earlier one on everything the
earlier one set — it is a domination test, not an equality test:

```java
            for (Map.Entry<String, Accessory.accessorySetting> command : earlier.entrySet())
            {
                if (!command.getValue().equals(commands.get(command.getKey())))
                {
                    dominates = false;
                    break;
                }
            }

            if (dominates) return true;
```

So the case the comment names — *the same berth, reached by a longer approach* — is the case most
likely to be pruned. A longer run-in to the same berth over the same ironwork carries the short
route's commands plus more; the short route's map is a subset with equal values; it dominates; the
long route never reaches line 1035. Only an approach that sets one of the same switches the *other*
way survives the prune, and that is not what "a longer approach" usually means.

`turned` is in the key precisely because the sibling rule below it (`mustBackIn`, line 1051-1054) has
the same shape and needed the escape to work. Room is not in the key, and is not a function of the
key.

**What happens.** On a layout where the whole run-in to a berth is measured, Return Home can report
`NO_PLAN_FOUND` for an arrangement the railway would drive, because the runtime's own search
(`pickPath` enumerating every route through `isPathClear`) would have tried the longer approach and
this one did not. That is the failure mode this file's own comment thirty lines above calls out as the
worst way round for the two halves to disagree:

```java
                // That is the worst way round for it to be wrong.  The planner refused arrangements
                // the runtime would have driven, and reported NO_PLAN_FOUND after exhausting its whole
                // search budget - the vaguest message this can give, after the longest wait.
```

**Reachability, honestly.** `measuredRoomToReverseInto` returns null — no judgement — if *any* edge on
the route has `getLength() <= 0`. The operator's `setup.json` records `tileLengths` for six tiles in
total, so most routes contain at least one wholly unmeasured edge and are not judged at all. The case
that bites today is a short approach whose every edge happens to span one of those six tiles — the
single-edge run into `BottomMainB` (room 4) or `BottomMainC` (room 2), against the 42 locomotives whose
recorded train length exceeds 2 (measured and recorded in `FX2-3`). So: live but narrow now, and
wider on any layout the editor's own reversal-length notice persuades him to measure — which is what
that notice is for.

**Severity B rather than A** because nothing wrong is *driven*: the plan is refused, not mis-made.

**The fix is an ordering**: ask the room question before line 1019's `alreadyReached`/`seen` block for
the `next.equals(to)` case, or put the room verdict in the key. Whichever is chosen, the argument for
it belongs at the guard, because the comment currently states the opposite of what the code allows.

---

## C — cosmetic, dead code, or narrow edge cases

### `WK3-C1` — `SVN-B10` widened the Start guard and not the affordance, and left a javadoc saying the two read the same number

**Status: open. Introduced by `87b6c10a`.**

The guard now asks `hasErrors()`:

`src/org/traincontrol/gui/TrainControlUI.java:5183`

```java
        if (!getAutonomySession().hasErrors()) return false;
```

where `AutonomySession.hasErrors()` (`:3572-3575`) is `hasBlockingProblems() || errorCount() > 0`. The
affordance still asks the narrower half:

`src/org/traincontrol/gui/TrainControlUI.java:20156-20159`

```java
    public boolean canStartAutonomy()
    {
        return this.startAutonomy != null && this.startAutonomy.isEnabled()
            && autonomyErrorCount() == 0;
```

and `autonomyErrorCount()`'s own javadoc, directly below it, now states something that is no longer
true — `:20165-20167`:

```java
     * The guard's own number. `refuseAutonomyStartWhileBroken` reads exactly this and refuses when it
     * is not zero, and it says why: "asked of the session rather than counted here, so that the strip
     * deciding what to OFFER and this deciding what to ALLOW cannot drift apart."
```

`refuseAutonomyStartWhileBroken` no longer reads exactly this. `autonomyErrorCount()` feeds
`canStartAutonomy()`, the autonomy overlay toggle (`AutonomyOverlayToggle.java:342`) and the diagram's
right-click menu (`LayoutRightclickAutonomyMenu.java:203`) — the three affordances OB-090 was about.

**Filed C, not B, because the divergence is unreachable in the steady state.** `AutonomyChecks.run`
copies every blocking problem in as an ERROR (`AutonomyChecks.java:400-406`), so
`hasBlockingProblems()` implies `errorCount() > 0` whenever `check()` runs at all. The only gap is
`check()`'s own guard, `AutonomySession.java:3405`:

```java
        if (graph == null || reducer == null) return new ArrayList<AutonomyChecks.Finding>();
```

against `hasBlockingProblems()`'s `graph != null && graph.hasBlockingProblems()`. That needs
`graph != null` with `reducer == null`, which `rebuild()` produces only transiently — it assigns
`graph` at `:349` and `reducer` at `:355`, and on the very first rebuild the old `reducer` is null.
A reader on another thread in that window sees it. Nobody will hit it in practice.

What is a defect either way is the javadoc: it is the sentence a future author will act on, and the
README is explicit that documentation is part of the method. Either widen `autonomyErrorCount`'s
consumers to `hasErrors()` — which is what "the question every affordance that offers to run it has
to ask" says — or change the sentence.

---

### `WK3-C2` — `SVN-B7`'s premise is already covered by `Route.setExecuting()`, and its new guard fires for 600 ms after the route has finished

**Status: open. Introduced by `87b6c10a`.**

Two separate points about the same eleven lines.

**First, the hazard as stated does not exist.** The commit message and the comment both say:

`src/org/traincontrol/gui/TrainControlUI.java:16106-16109`

```java
        // It was on the play button alone, so clicking the same route's
        // row and confirming, or right-clicking it and choosing Execute, started a second run of a
        // route that was already running: two threads throwing the same accessories, each unlocking
        // what the other had locked.
```

Every one of those doors reaches `MarklinRoute.execRoute(boolean, int, boolean)`, whose whole body is
behind an atomic re-entrancy guard — `MarklinRoute.java:501-503`:

```java
        new Thread(() ->
        {
            if (this.setExecuting())
```

and `Route.java:115-125`:

```java
    synchronized public boolean setExecuting()
    {
        if (this.isExecuting)
        {
            return false;
        }

        this.isExecuting = true;
```

A second start of the same route has always returned silently and thrown nothing. The new guard is a
UI improvement — it says so in the log instead of doing nothing visible — but it is not the
correctness fix the comment claims, and the "two threads throwing the same accessories" sentence will
be read as a fact about this codebase by the next person.

**Second, the set it reads outlives the route.** `routeFinished` deliberately holds the name for a
minimum of 600 ms so the greyed button is visible — `TrainControlUI.java:24536-24544`:

```java
        // A floor rather than a fixed hold, because the two say different things when a route is slow:
        // a fixed second would put the button back while the route was still running, which is the
        // interface saying something untrue at the one moment the operator might press it again. This
        // way the button is grey exactly while pressing it again would be refused - which is what
        // routesExecuting means everywhere else it is read.
        Long since = startedRunningAt.get(route);

        long showFor = since == null ? 0
            : ROUTE_MINIMUM_VISIBLE_MS - (System.currentTimeMillis() - since);
```

That reasoning was written when `routesExecuting` gated one door that greys itself. It now also gates
the row click and the right-click Execute, which do not grey and which now log:

```
route.ui.infoAlreadyRunning=Route {0} is already running, so it was not started again.
```

For a route that completes in under 600 ms — most of them — a second, legitimate press within that
window is refused with a sentence that is not true. The floor's own comment ("the button is grey
exactly while pressing it again would be refused") is what makes this consistent, so the smaller fix
is probably to say "was started a moment ago" rather than to unpick the floor.

---

### `WK3-C3` — a third copy of "which squares carry a home"

**Status: open. Introduced by `1cfdf370`.**

`1cfdf370` added `AutonomySession.tilesWithAHome()` at `:4083` for the new Clear All Home Locomotives
button. `AutonomySession.homeTiles()` at `:3364` already walks the same points of the same
configuration for the same property, and `homesElsewhere()` at `:4041` is a third variant of the same
walk with one extra filter. All three read `store.getConfiguration(store.getActiveConfiguration())`,
iterate `points`, read `"home"`, skip blanks and `parseTileKey` the key.

`homeTiles()` feeds `check()` — the findings the diagram shows. `tilesWithAHome()` decides what the
bulk clear touches. If either gains a qualification (homes on excluded pages, homes on squares that
no longer exist) the other will not have it, and the button will clear a set the findings panel does
not agree with. This is the shape recorded as *bulk-vs-single drift*, and it is a C only because the
two agree today.

---

## D — not defects, withdrawn findings, and checks that came back clean

### `WK3-D1` (was going to be a C) — WITHDRAWN: the "no page could be read" message did not name a page

**Original claim.** `10694670`'s `RC-A4` throw formatted `layout.warningPageCouldNotBeRead` — whose
`{0}` is a page name and `{1}` an exception — with a *count* and the literal `"*"`, producing a log
line naming a page that does not exist and ending "The rest of the layout was loaded" when nothing
had been.

**Why I was wrong.** The claim was true of `10694670` and false of the tree. `72234e18` — "RC-A6..B10:
the round's own fixes, attacked", the same day — replaced it with a key written for the case:

`src/org/traincontrol/marklin/MarklinControlStation.java:552-555`

```java
        if (parsed.isEmpty() && couldNotBeRead > 0)
        {
            throw new Exception(I18n.f("layout.errorNoPageCouldBeRead", couldNotBeRead));
        }
```

```
layout.errorNoPageCouldBeRead=None of the {0} track diagram page(s) in this folder could be read, so the layout was left as it was.
```

present in all eight bundles. **The general lesson, since it is the calibration data:** reviewing a
week by walking diffs produces findings against intermediate states. Every finding in this document
above was re-checked against the tree at `cf048f9b` before being filed; this one is recorded because
it is the one that did not survive that check.

### `WK3-D2` — the `SVN-A3` page-switch reordering is correct, traced end to end

`1cfdf370` moved the continuation's guarantee from `layoutEditingCompleteThen` into
`layoutRefreshComplete`'s `finally` (`TrainControlUI.java:19430-19448`), so `after` runs after the
dozen statements rather than before the worker. The invariant it exists to protect does hold:
`layoutRefreshCompleteInternal` ends with `setEditLayoutEnabled(true)` at `:19494`, and the editor's
continuation — `LayoutEditor.java:5622` — posts `arriveAt`, which calls
`parent.setEditLayoutEnabled(false)` at `:5756`. Every early exit from `arriveAt` (the page vanished
at `:5674`, the `catch` at `:5787`) goes through `confirmExitWithoutAsking`, which disposes the
window and re-enables the button legitimately. The `AtomicBoolean once` at `:19533` is not racy: its
second caller only runs if `layoutEditingComplete` threw before starting the thread. The two other
callers that pass a continuation (`:21631`, `:21859`) do `setSelectedItem` and `repaintLayout`, both
harmless in the new "runs even on a throw" case.

### `WK3-D3` — `SVN-B13` is correct, and narrower than its commit message says

`8d1c17ca` added the square rule to `rebuildHomeStations` (`Layout.java:1139-1169`). The rule itself
is right and matches `claimHome`'s. But the state it catches cannot be produced by the application's
own doors on a diagram-derived layout:

- `AutonomyBuilder.java:841` sets `block` to the **tile key** — `if (nodes.size() > 1) json.put("block", point.getTile().toString())` — so two Points share a block only when they are copies of one square;
- the builder emits `home` on exactly one copy — `AutonomyBuilder.java:925`, `if (HOME.equals(key) && copy != homeOn) continue;`;
- the store keys a home per tile, so the editor cannot author two on one square;
- `Layout.setHomeLocomotive`, which carries the square sweep at `:1261-1267`, has **no callers in `src/`** — only comments reference it.

So the reachable producer is a hand-edited or legacy `autonomy.json` read through `parseAuto`, which
is the first half of the commit message. The second half — "or a setup written before the editor
learned to sweep" — cannot happen, because the store has never had two home slots for one square.
Worth knowing before somebody sizes a fix around it. Not filed as a finding: a guard against a state
only a hand-edited file can reach is exactly the right place for one.

### `WK3-D4` — `D24-B5` (carrying `active` on every square) is inert on the operator's railway today

The builder now emits `active` for non-stations (`AutonomyBuilder.java:936-953`). Checked against the
data rather than reasoned about: `configuration-Main.json` holds 71 points, of which exactly **one**
carries `active: false` — `2 - Bottom:8,7` — and `setup.json`'s `pages` maps page id `1` to
`2 - Bottom`, and `1:8,7` is in `stations` under the name `ParkingTrack12`. It is a station, so it was
already being emitted. Same result in the frozen copy and in the live folder. The change alters
nothing on his layout now and will start mattering the first time he marks a plain sensor out of
service, which is the point of it.

### `WK3-D5` — `R28-C1`'s "nothing is written to disk, Cancel puts it back" holds

`clearAllHomes` (`AutonomyEditorPanel.java:6450-6478`) claims the bulk clear waits for Save. It does:
`session.setHome` (`AutonomySession.java:4002`) → `setPointProperty` (`:4114`), which mutates the in-memory
`JSONObject`, sets `dirty = true` and calls `deriveStationIndex()`, and writes nothing. And Cancel
really restores it: `AutonomyCompanionStore.snapshotSetup()` at `:1925-1938` deep-copies every entry
of `configurations` — where `home` lives — and `restoreSetup` at `:1949-1962` puts them back. The
confirmation defaults to No (`YES_NO_OPTS[1]`), and the empty case says so in the hint line rather
than in a dialog.

### `WK3-D6` — `TCX-A2`'s lift into `Layout.measuredRoomToReverseInto` is faithful

The extracted method (`Layout.java:6178`) reproduces the runtime's enclosing conditions exactly — the
train-length null/positive test, the non-empty path, and `ending.isTerminus() || ending.isReversing()`
— so `isPathClear`'s behaviour at `:2362-2412` is unchanged, including both unsoundnesses recorded
there. No unboxing hazard on either side: the helper returns null whenever `getTrainLength()` is null,
so `HomeStaging.java:1049`'s `loc.getTrainLength() > room` cannot NPE. (What the *placement* of the
new call in `HomeStaging` costs is `WK3-B2`; the lift itself is right.)

### `WK3-D7` — `SVN-B14`'s Cancel restore covers Escape and the close box

`RightClickFunctionMenu.java:215-216` captures both slots before the autonomy block, and the `else` at
`:291-305` restores them. `showOptionDialog` answers `CLOSED_OPTION` for Escape and the window's close
box, which is neither `OK_OPTION` nor anything else the `if` matches, so it lands in the same `else`.
Capturing outside the `if (tcui.isAutonomyLoaded())` block makes the no-autonomy case a no-op rather
than a special case, as the comment says.

### `WK3-D8` — every message key added this week is in all eight bundles, and ASCII

Checked `route.ui.infoAlreadyRunning`, `layout.ui.confirmAccessoryProtecting`,
`autolayout.warnHomeSquareAssignedTwice`, `autosetup.ui.errorCannotBuildDetailOne` and
`layout.errorNoPageCouldBeRead`: each present in `messages.properties` and all seven translations, one
definition each, non-ASCII written as `\uXXXX`.

### `WK3-D9` — `OB-127`'s `getLocalLayoutPath()` null change does not strand a caller

`720e62e9` made the accessor return null for the empty string it used to hand back
(`TrainControlUI.java:25697`). `new File(null)` would throw, so every remaining caller was checked:
`:2522`, `:2531`, `:3840` and `:18434` each test `path == null || path.isEmpty()` before use, and
`:21601`, `:21856`, `:21987`, `:22019` sit behind an `if (!this.isLocalLayout()) return` a few lines
above. `settleAbsentPages` at `:1894` now refuses first, as the commit says.

### `WK3-D10` — `VB-B1`'s nine dialog moves put the Swing work on the event thread, not the other way round

Sampled `AutoJSONExport.jsonSaveAsActionPerformed`, `loadJSONButtonActionPerformed`,
`exportJSONActionPerformed` and `BulkEnableOrDisable`. Each now does the chooser, the component reads
(`jsonTextArea.getText()`) and the component construction on the event thread and leaves only the
`Files.write` / `Files.readAllBytes` / payload generation on the worker, with the result marshalled
back through `invokeLater`. The button re-enable moved into the marshalled half in every case, so a
failed write cannot leave the button dead. `BulkEnableOrDisable`'s null-cancel guard survived the
move intact (`if (searchString == null || "".equals(searchString)) return;`).

### `WK3-D11` — considered and dropped: `rebuildHomeStations` clearing `p.setHomeLoc(null)` is not data loss to the file

`Layout.java:1135` and `:1167` clear the losing assignment off the Point, and the comment says the
loser is otherwise "written back out on every save". I started to file that as a path by which a load
silently deletes one of the operator's home assignments. It is not: on a diagram-derived layout the
Points are *derived* from `configuration-*.json` by `AutonomyBuilder`, and saving writes the store, not
the Layout — `Layout.toJSON()` is the legacy export path. So the drop is in-memory and per-session,
and the file keeps both assignments. The consequence is the opposite of data loss and much milder: the
warning re-appears on every load, which is the outcome the comment says it was avoiding. Not filed;
recorded so the next reader does not repeat the trace.

---

## Open questions I could not settle by reading

1. **`WK3-A1`'s deadlock half.** Whether any thread other than the event thread holds the
   `TrainControlUI` monitor and then reaches a `synchronized` `Layout` method would turn the freeze
   into the AB-BA the `getEdges` note describes. Settling it wants a thread dump under a dispatch, not
   a reading pass.
2. **`WK3-B2`'s reach.** How often `alreadyReached` actually dominates a longer approach on a real
   graph is a measurement — enumerate the routes `firstClearRoute` prunes against what `pickPath`
   returns for the same pair — and the harness for that comparison already exists as
   `auditAgainstRuntime`.
3. Whether the 600 ms floor in `WK3-C2` should be unpicked or the message softened is Adam's call, not
   a defect with an obvious fix.
