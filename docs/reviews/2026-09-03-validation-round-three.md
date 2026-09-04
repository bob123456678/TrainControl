# Validation round three of 2026-09-03 — the four commits nobody has validated, and round two's own fixes

**Status:** open

**Prefix:** `VD11`. Cite findings from here as `VD11-A1`, `VD11-C4`, and so on.
(`DAY DY3 IPR LE R28 RC REL RG3 RGN SG TSX SVN VD9 VD10` are in use elsewhere, along with the
two-and-three-letter prefixes listed in those documents.)

**Reviewed:** branch `autonomy-diagram-r0` at `6182e98d`, tagged `v3_0_0_rc10`, 2026-09-03 night. The
window is `f3dc0aec..HEAD` — the four commits no validation pass has seen: `fe4e787b` (`FR-058`),
`5c965948` (`VD10-B2`), `f7d3aee0` (`VD10` B1/B4/B5/B6/B7) and `6182e98d` (`VD10` C1–C16). Round two's
own fixes are the primary target, which is what round two was for round one. Nothing was changed by
this pass; the only artefact is this file. `cs2_sample_layout/` was read and never written; its one
uncommitted local change (`configuration-Main.json`) was left exactly as it was.

**Method.** Round one (`VD9`) found 0 A, 8 B, 20 C; round two (`VD10`) found 2 A, 7 B, 16 C, and most
of its serious findings were defects in round one's corrections. The established pattern is that a fix
reintroduces the defect class it fixes, so this pass hunted the **third generation** of the three shapes
that have now recurred twice each:

1. *the enumerating guard* — `MT-246` fixed nine doors and wrote a rule naming the two setters it
   happened to involve; `VD10-B2` widened it to twelve setters and it is **still an enumeration**, and
   still misses live doors (`A1`);
2. *the hunk that damages the comment around it* — `VD9-B1` → `VD10-C3` → `C1` here;
3. *the orphaned javadoc* — `VD9-C16` → `VD10-B3` → `B3` here.

All three recurred. Two of the three were found by mechanically re-running the round's own tool against
a wider input than the round chose, which is the cheapest thing in this document to repeat.

**Executed, not only read.** Three classes were run alone through `docs/tools/one.sh`:
`regression.testJavadocsAreAttached` (1/0/0), `regression.testEditorSurfaceRules` (37/0/0) and
`core.testAutoLayout` (24/0/0) — all green at HEAD, so `VD10-B3` really is fixed and the day's two new
tests really do pass. `python docs/tools/parity/compare-conditions.py --self-test` was run (3/3). All
eight message bundles were decoded and read. The full battery was **not** run, on instruction.

---

## Summary

| | | |
|---|---|---|
| **A1** | `VD10-B2`'s widened rule is still a list of setters, and at least **eight more doors on the same menu** write the setup and never tell the running layout - three of them writing *directions*, the exact category the commit added `setAllBranches` for. One `WRITERS` entry can never match anything | fixed |
| **A2** | Every door that ends in `setupChanged()` performs an **unconditional emergency stop of every locomotive** and a tab jump, because the rebuild runs `autonomyLoadedFromDiagram`. `VD10-B2` put that behind nine more doors, including typing a link's name | fixed |
| **B1** | `FR-058`'s ellipsis now fires when nothing is hidden: `possible` counts the *More Destinations* entries as left out. On Adam's own configuration 20 of 71 squares are non-choosable, so it fires on essentially every right-click | fixed |
| **B2** | `VD10-B6`'s "a failure is recorded" records into a field nothing reads and nothing clears. `didTheNoteRepairFail()` has no callers, and `VD9-C9`'s stated minimum - a log line - is still not met | fixed |
| **B3** | `5c965948` orphaned the 26-line javadoc of the test it was widening, three commits after `VD10-B3` was raised for the same thing. The ratchet cannot see it: it scans `src` only | fixed |
| **C1** | `Layout.java:5535-5541` - `VD10-C15`'s replacement was spliced into the middle of the sentence it was correcting, at the wrong indent, leaving `` turned on., so being in this set is the `` | fixed |
| **C2** | `FR-058`'s comment says *More Destinations* holds a terminus a non-reversible train could not leave. It does not - `isChoosableByAutonomy` passes a null locomotive, so that clause never runs | overturned by Adam |
| **C3** | When every valid destination is non-choosable, *More Destinations* is added with no separator and no locomotive-name header: both are gated on the top-level list being non-empty | fixed |
| **C4** | `VD10-C11` is dispositioned *"a skipped section exits 2"* and it exits **1**; and the third of its three bullets - a driver crash leaving a stale `report.md` - is untouched at HEAD | fixed |
| **C5** | The rename to `...WiderSweep` left a stale reference in `docs/manual-tests/tests.md:13493`, which is `VD9-C15`/`VD10-C8` a third time | fixed |
| **C6** | The new `FR-058` test's last assertion cannot fail: it asserts the setter it called two lines above | fixed |
| **C7** | `VD10-B1`'s hunk lost its paragraph breaks - the `VD10-A1` paragraph runs straight into the `VD10-B1` heading - and left a double blank line where the old block was | fixed |
| **C8** | What `VD10-C2`'s coalescing costs, which is small but is not nothing: `onChanged`'s two cache refreshes now run against the pre-rebuild layout, and the `isAutonomyBusy` gate is asked one event after the edit | fixed |
| **C9** | *More Destinations* is uncapped by argument rather than by measurement; ~25 items are reachable on the operator's layout | fixed |
| **C10** | `FR-058` changes a menu that dispatches trains and no manual test was written for it; `issues.md` carries "fixed unvalidated" with no `MT` tag beside it | fixed |
| **D1** | All eight `autolayout.errorRunStoppedByFailure` values decode to correct, idiomatic text, are pure ASCII, carry `{0}`, and use no straight apostrophe. Same for the new `autolayout.ui.menuMoreDestinations` | closed |
| **D2** | `destinationItem` preserves the power check, the off-thread dispatch, the failure dialog and the per-item `path` capture exactly | closed |
| **D3** | Three classes run alone at HEAD: `testJavadocsAreAttached` 1/0/0, `testEditorSurfaceRules` 37/0/0, `testAutoLayout` 24/0/0 | closed |
| **D4** | `VD10-B1`'s reorder is safe: nothing in the old ordering needed the release first, and the try/catch still cannot mask the original exception | closed |
| **D5** | The repaint does NOT overtake the deferred rebuild, though it looks as if it must | closed |
| **D6** | `setupChanged()` has no off-EDT caller, so `setupChangePending` is a single-threaded field | closed |
| **D7** | The parity dispositions are all true at HEAD: the floor, `TC_CONDITION_FLOOR`, canonical atoms, the frozen `ROUTES` default, the written report, and the `--self-test` | closed |
| **D8** | `VD10` `C1`, `C4`, `C5`, `C6`, `C7`, `C8`, `C9`, `C16` are each true at HEAD, checked at the file rather than from the disposition | closed |
| **D9** | `VD10-A1`'s fix is present and its test asserts it: `unlockPath` first, `clearedEdges.remove` after | closed |
| **D10** | The two doors that write blocking points DO announce, so the unmatched `WRITERS` entry is false coverage rather than a live hole | closed |
| **D11** | `radio()` and `toggle()` both end in `placementChanged()`, so the facing radios and the arm checkboxes are announced despite not being in `WRITERS` | closed |

---

## A — high

### A1 — the widened rule is still a list, and eight more doors on the same menu are silent

| | |
|---|---|
| **Disposition** | fixed - the rule no longer holds a list; the wider sweep derives every mutator from AutonomySession's own source, so a door added tomorrow is covered the day it is written |
| **Confidence** | confirmed by re-running the rule's own algorithm over every `session.` writer the panel calls, then reading each hit; the announcing wrappers were opened before any door was called silent |

`5c965948`'s message diagnoses itself: *"A guard that lists the cases it knows about finds the cases it
knows about."* The fix was a longer list. `test/regression/testEditorSurfaceRules.java:2769-2774`:

```java
    private static final String[] WRITERS = {
        "session.setPointProperty(", "session.setHome(", "session.setTileLength(",
        "session.setPointName(", "session.setLinkName(", "session.setDirection(",
        "session.clearEveryHome(", "session.clearEveryPlacement(", "session.setProtectingSignals(",
        "session.setBarredArrivals(", "session.setBlockingPoints(", "session.setPortalDisabled(",
    };
```

`AutonomySession` exposes twenty-six public mutators. Re-running the test's exact algorithm — the same
brace walk, the same "does the body contain `setupChanged()` or `placementChanged()`" test — over the
writers the panel actually calls gives **eight live doors the rule cannot see**, every one of them on
`buildTileMenu`, which serves the track diagram as well as the editor:

| door | line | writes | reached from |
|---|---|---|---|
| `directionItem` | `AutonomyEditorPanel.java:6235` | `session.setRunDirection` | Connections → One way → *toward X*, built at `:1462`/`:1471` |
| `cycle` | `:5218` | `session.setRunDirection` | clicking a one-route square |
| `cycleBranching` → `applyArmMask` | `:5277` → `:5313` | `session.setDirections` | clicking a switch to cycle its arms |
| `pairFromList` | `:4371` | `session.pairPortals` | "Pair link", three items from the portal checkbox this commit *did* fix |
| `addLocomotiveSettings` | `:3805` | `session.placeLocomotive` | the locomotive submenu's Edit/Assign |
| `applyCaption` | `:2580` | `session.setCaption` | putting a station's name on a square |
| `placeLabelFor` | `:2686` | `session.placeCaption` | the same, automatically |
| `moveCaption` | `:2345` | `session.moveCaption` | dragging a caption to another square |

**The three direction doors are the sharp ones, and they are the commit's own category.** `5c965948`
says, in its message and again at `:3011`: *"Directions are edges in the running graph (VD10-B2)."* It
added `setupChanged()` to `setAllBranches`, which is the *bulk* answer — Connections → All branches →
Both / None. The **per-route radio right beside it on the same submenu** writes the same property
through `setRunDirection` and announces nothing, and so does the click-cycle. So on the diagram, "all
branches, both" reaches the running graph and "one way, toward A" does not: the setup says the edge is
one-way and the running layout still routes trains through it in both directions until something else
rebuilds. That is `MT-246` with a different setter, which is what `VD10-B2` said about `MT-246`.

`applyArmMask` is worth naming separately because of *why* it was missed. The rule matches
`session.setDirection(` — and the per-arm door calls `session.setDirections(`, plural. The string is
one character away from a match. `session.setDirection(` is a prefix of neither.

**And one entry in the list can never match anything.** `session.setBlockingPoints(` appears nowhere in
the panel: both writers go through the store, `session.getStore().setBlockingPoints(...)` at `:3501`
and `:5098`. Those two doors are in fact fine — both end in `placementChanged()` (`D10`) — but the rule
believes it is covering them and is not, so removing either `placementChanged()` would pass. Ten of the
thirty-one `session.getStore().` calls in this file are outside the rule's reach by construction.

The remedy that would end this shape rather than extend it is to invert the rule: instead of listing
writers, list the **non**-writers, or ask `AutonomySession` itself — every public `void set*`/`clear*`
that ends in `touched()` is a writer, and that set is derivable rather than typed. A rule maintained by
hand has now missed its target three rounds running.

Graded A because a direction set from the tile menu is wrong behaviour on the railway: autonomy plans
over a graph that does not have the restriction the operator just applied, and `pickPath` is what
consumes it.

### A2 — every rebuild stops every locomotive, and nine more doors were put behind it today

| | |
|---|---|
| **Disposition** | fixed - autonomyLoadedFromDiagram takes a resumed flag and only a genuine load stops the railway |
| **Confidence** | confirmed by reading the chain end to end; not observed on hardware |

`setupChanged()` → `TrainControlUI.rebuildRunningLayoutFromSetup()` (`:5425`) →
`AutonomyViewerPanel.load(name, false, false)` (`:746`) → on success →
`TrainControlUI.autonomyLoadedFromDiagram` (`:3565`), whose second statement is:

```java
        this.refreshRouteList();
        AltEmergencyStopActionPerformed(null);
```

and `AltEmergencyStopActionPerformed` (`:20398-20403`) is `this.model.stopAllLocs()` on its own thread.
Six lines later the method ends in `jumpToLayoutTab()` (`:3636`).

It is deliberate where it came from, and the comment says so: *"the unconditional stop catches
hand-throttled trains that `isAutonomyBusy()` does not cover - a train somebody was driving keeps
rolling while the new layout thinks everything is parked."* That reasoning holds for a rebuild that
moves trains in the model — a placement, a rename that re-splits squares, a load.

`5c965948` put it behind nine doors where it does not hold:

| door | gesture | what now happens |
|---|---|---|
| `promptLinkName` `:4257` | typing a link's display name | every locomotive stops |
| `applyLength` `:4937` | typing a tile length | every locomotive stops |
| `addProtectingSignal` `:4769` / `removeProtectingSignal` `:4793` | pairing a signal | every locomotive stops |
| `setArrivalAllowed` `:2969` | ticking an arrival side | every locomotive stops |
| `setAllBranches` `:3011` | Connections → All branches | every locomotive stops |
| `nameEverything` `:6755` | the bulk naming walk | every locomotive stops |
| `clearAllHomes` `:6834` | Bulk tools → Clear All Home Locomotives | every locomotive stops |
| the portal checkbox `:1548-1556` | ticking "Use link" | every locomotive stops |

The gate above it is `!isAutonomyBusy()`, which is `stagingFlowActive || getAutoLayout().isRunning()`
(`:21204-21212`) — it says nothing about hand-driven trains, which is precisely the case the emergency
stop was put there for. So: autonomy idle, the operator driving a train by hand, right-click a square
on the diagram, type a name for a link — and the railway stops.

This is not new *in kind*: `placementChanged` and `MT-246`'s nine doors have reached the same code since
`c892ec03`, and neither round one nor round two noticed. What is new today is that it is now behind
gestures with no plausible connection to where trains are. It is the "lifted rules lose their
precondition" shape at one remove: `setupChanged()` was extracted so every door would do the same
thing, and the thing it does turned out to include an emergency stop.

Graded A because it is wrong behaviour on the layout, deterministic, and reachable by a gesture the
operator has no reason to associate with stopping trains. The remedy is Adam's to choose and there are
at least two: make the stop conditional on the rebuild actually changing placements, or give
`rebuildRunningLayoutFromSetup` a quieter path into `load` that skips `autonomyLoadedFromDiagram`'s
"a configuration was just chosen" ceremony — the tab jump has the same problem and is harmless.

---

## B — medium

### B1 — the ellipsis now says "there is more" when there is not

| | |
|---|---|
| **Disposition** | fixed - the ellipsis compares against shown + otherPaths.size(), so it fires only when something is on neither list |
| **Confidence** | confirmed by reading, and the frequency measured against the operator's own configuration |

`LayoutRightclickAutonomyMenu.java:291-296`:

```java
                        // EVERYTHING THAT IS POSSIBLE, counted before anything is left out.
                        final int possible = paths.size();
```

and `:375`:

```java
                            if (++shown >= Math.min(MAX_PATHS, paths.size()) && possible > shown)
```

Before `FR-058`, `possible - shown` was exactly the number of destinations the menu had decided not to
offer: switched-off squares dropped, plus anything beyond `MAX_PATHS = 12`. The comment at `:369-374`
says so.

`FR-058` added a third subtraction that is **not an omission**: the non-choosable paths are moved out of
`paths` and into `otherPaths`, and then shown, in the *More Destinations* submenu. They are still on the
menu. But `possible` counted them, so the ellipsis fires for them.

Concretely: five choosable destinations and three non-choosable, none inactive. `possible = 8`,
`paths.size() = 5`, `Math.min(12, 5) = 5`. At the fifth item `shown` becomes 5, `5 >= 5` and `8 > 5`, so
the `"..."` item is added — telling the operator there are options behind it and offering to jump him to
the autonomy tab, when all eight are already in front of him.

**On his own layout this is not an edge case.** `cs2_sample_layout/config/autonomy/configuration-Main.json`
carries 71 squares, of which **20** have `"autoDestination": false` and 5 more carry `canReverse`. Every
one of those is `isChoosableByAutonomy == false` and therefore lands in *More Destinations*. So the
ellipsis will fire on essentially every right-click that produces a submenu, which is the opposite of
what `FR-058` was asked for — Adam's complaint was *"the current setup lists both in one flat list,
which truncates active stations"*, and the fix now claims truncation on every menu.

The one-line remedy is to count `possible` after the split, or to subtract `otherPaths.size()` from it —
`possible > shown + otherPaths.size()`. Either restores the sentence the comment says the number means.

### B2 — "a failure is recorded" records it where nothing reads it

| | |
|---|---|
| **Disposition** | fixed - the rename door reads didTheNoteRepairFail() and logs the file the operator has to delete |
| **Confidence** | confirmed by grep over the whole tree: five occurrences, all inside one class, none of them a read |

`VD10-B6`'s disposition is *"serialised before the stream is opened, and a failure is recorded"*. The
first half is right and is the fix the finding asked for (`AutonomyCompanionStore.java:1461-1479`). The
second half is a field:

```java
    private boolean noteRepairFailed;

    public boolean didTheNoteRepairFail()
    {
        return noteRepairFailed;
    }
```

`grep -rn "didTheNoteRepairFail\|noteRepairFailed" --include=*.java .` returns five lines: the
declaration, the accessor, its own `return`, and the two `= true` assignments. **Nothing reads it.** Not
`repairLocomotive` at `:1382`, which `VD10-B6` named as the caller that cannot see the failure; not the
session; not a test.

Three consequences, in order of weight:

- `VD9-C9`'s stated minimum — *"at minimum the failed write deserves the one-line log that `dispose()`
  gives the failed delete"* — is **still not met**, two rounds after it was written. It has been moved
  from "swallowed in a catch" to "swallowed in a field", which is the same silence with more code.
- The comment at `:1482-1484` says the failure is recorded *"the way it records a failed migration, for
  whoever opened the session"*. A failed migration is read back and shown; this is not, so the analogy
  is the part that makes the sentence sound finished.
- The field's own javadoc at `:1385-1392` says *"Whether the **last** rename or deletion failed"*. It is
  never cleared, so it is a latch: once true it is true for the life of the store, and it answers "has
  any ever failed". If a reader is added later, that reader gets the wrong question answered.

Smaller, same hunk: `didTheNoteRepairFail()` at `:1395-1398` is a public method with no javadoc, and it
is butted directly against the next method's doc comment with no blank line — which is one edit away
from `B3`'s shape in production code.

The remedy is the one `forgetBeforeEdit` already uses and the finding already named: return a boolean,
or clear the field at the top of `repairTheUnfinishedEditNote`, and have somebody ask.

### B3 — the javadoc orphan, three commits after the last one

| | |
|---|---|
| **Disposition** | fixed - the block was split and each javadoc re-emitted against its own method |
| **Confidence** | confirmed by reading, and by establishing that the ratchet's scope excludes it |

`test/regression/testEditorSurfaceRules.java:2742-2777`:

```java
    /**
     * Every door that writes a point property ends by announcing it (MT-246).
     *   … 24 lines …
     * MUTATION: removing `setupChanged();` from any one of the nine fails this.
     */
    /** Every session call that changes the setup, so the rule below covers all of them. */
    private static final String[] WRITERS = {
```

Two doc comments in a row; only the last attaches. So the 26-line paragraph written for the test now
documents a `String[]`, and `testEveryDoorThatWritesTheSetupAnnouncesItWiderSweep` — the method it was
written for — has no javadoc at all. That is `VD9-C16`, and it is `VD10-B3` three commits later, in the
commit whose subject is a guard that only knew the cases it was written for.

**`regression.testJavadocsAreAttached` cannot catch it.** It walks `new File("src")` (`:105`), so no test
source is scanned. It passes at HEAD — 1 test, 0 failures, 0 skips, run alone — and that green says
nothing about this file. The ratchet's blind spot is the same size as the test tree.

The orphaned text is also now false in three places, which is what an orphan costs: it says the omission
was in *"seven of the nine"* doors and that removing `setupChanged();` *"from any one of the nine"* fails
the test. There are eighteen `setupChanged();` call sites in the panel at HEAD and twelve strings in the
rule, and `A1` says at least eight more doors are outside it.

---

## C — low

### C1 — the third mangled comment, in the fix for the second one

| | |
|---|---|
| **Disposition** | fixed - the comment reads as written |
| **Confidence** | confirmed by reading the file at HEAD |

`src/org/traincontrol/automation/Layout.java:5535-5543`:

```java
                            // an active path - and with atomicRoutes ON the
                            // lock is held for the whole run by design.
        //
        // NOT "which is what Adam runs", which this said (VD10-C15): his active configuration
        // has `"atomicRoutes": false`, so the branch of `unlockPath` that reads this map is the
        // one his railway actually takes - which is the premise `VD10-A1` turned on., so being in this set is the
                            // ONLY thing that drops an edge's protection. An early clear lets a route
                            // throw a turnout on track the train is still standing on.
```

The replacement block was inserted into the middle of the sentence it was correcting. The surviving
clause `, so being in this set is the ONLY thing that drops an edge's protection` has been cut from its
subject and welded onto the end of the new paragraph after a full stop, and the whole insert sits at
class indent inside a block indented twenty-eight columns.

`VD9-B1` was a hunk that damaged the comment around what it replaced. `VD10-C3` was the same thing in
`VD9-B1`'s own fix. This is the same thing in `VD10-C15`'s fix, and it is a worse instance than either:
the sentence that lost its head is the one explaining why an early clear lets a route throw a turnout
under a standing train, which is the safety argument the whole block exists for.

The correction `VD10-C15` asked for is present and is true; only the surgery is wrong.

### C2 — *More Destinations* does not hold the terminus its comment promises

| | |
|---|---|
| **Disposition** | **overturned by Adam** - the recommendation was taken, he saw the result on his own railway and ruled the other way; the split asks the square form again and the comment lost its terminus clause, an exclusion now leaves the menu entirely, and the overload this finding added is deleted - see below |
| **Confidence** | confirmed by reading `isChoosableByAutonomy`, `barredFromAutonomy` and `isPathClear` |

`LayoutRightclickAutonomyMenu.java:395-400` says the submenu is where everything valid but not automatic
lives: *"a terminus a non-reversible train would have to be turned into, a reversing point, a square
marked as not an automatic destination."*

The first of the three does not go there. The split asks `isChoosableByAutonomy(end)`, which is
`barredFromAutonomy(end, null)` (`Layout.java:4062-4065`), and the terminus clause is
`if (loc != null && end.isTerminus() && !loc.isReversible())` (`:4103`). With a null locomotive it never
runs — as does the per-locomotive exclusion clause at `:4108`. The javadoc says the null is deliberate
and gives a good reason for it: the method answers a question about the *square*.

So a terminus this locomotive cannot get out of is choosable, and stays in the **capped top-level list**,
costing exactly the line `FR-058` was filed to stop it costing. It cannot be filtered out either:
`isPathClear` deliberately carries no terminus rule since 2026-09-01 (*"In manual operation, non
reversing trains must be able to back into a terminus if the graph makes that possible"*), so those
paths do reach the menu.

Two ways out, and they are not equivalent: pass `locomotive` instead of `null` — which makes the split
per-train and correct for all three cases, at the cost of asking a different question from the diagram's
caption rule — or delete the clause from the comment. The comment is the cheap fix and the wrong one to
reach for first, because the behaviour it describes is the behaviour Adam asked for.

**It was not, and Adam overturned this.**

I took the first way out - passing `locomotive` - and he saw the result on his own railway: *"For
FR-058, why are BottomMainB and BottomMainC in the 'more destinations' list for 2-8-4 3505?  We should
still show the same number of base options"*, then *"(unless unselectable)"*.

**Measured on `test/operator_layout` before changing anything back**, because his next sentence -
*"it sounds like the bottomMainB exclusion was just a bug though"* - proposed a different cause, and I
had privately guessed at a third:

```
loc=2-8-4 3505 SP reversible=false
BottomMainB (eastbound, reverse)  terminus=true   excludesThisLoc=false  square=true  perTrain=false
BottomMainC (westbound, reverse)  terminus=true   excludesThisLoc=false  square=true  perTrain=false
BottomMainB (eastbound)           terminus=false  excludesThisLoc=false  square=true  perTrain=true
```

`excludesThisLoc=false` on every one: **there is no exclusion anywhere near those squares.**  Each is
emitted twice, once as a through arrival and once as a reverse arrival that is a terminus, and it was
the terminus copies that moved.  The whole effect was the terminus clause arriving with the argument.

And the comment this finding was written against is the part that was wrong - not the code that
disagreed with it.  Adam had ruled the other way on 2026-09-01, and the ruling is quoted at
`isPathClear`: *"In manual operation, non reversing trains must be able to back into a terminus if the
graph makes that possible.  Otherwise we'd need a third kind of station."*  This finding records that
`isPathClear` deliberately carries no terminus rule and reads it as the reason those paths reach the
menu - rather than as the ruling it is.  **That is the defect in the finding**: it treated a decision
as a mechanism.

What shipped instead.  The split asks the square form again and the comment lost the clause.  His other
ruling of the same day - *"if it excludes the loc, don't even include it in the list"* - is a new named
rule, `Layout.isOfferableToOperator`, which drops a switched-off or excluded square from the menu
altogether rather than demoting it; it is on `Layout` rather than inline in the menu because the menu
class is package-private and a rule nothing can reach is a rule nothing can test, which is how the
terminus went into it and out again twice.  The two-argument `isChoosableByAutonomy` this finding added
is deleted: it had one caller, and the ruling took it away.

`testATerminusIsOfferedByHandAndAnExcludedSquareIsNotOfferedAtAll` pins all three clauses.  MUTATION,
each run and each failing only its own assertion: putting the terminus clause into
`isOfferableToOperator` fails the first, dropping its exclusion clause fails the second, dropping its
`isActive` clause fails the third.
### C3 — the submenu can arrive with no separator and no locomotive name

| | |
|---|---|
| **Disposition** | fixed - the separator and the locomotive's name are added when the top-level list is empty, so the submenu always arrives under a heading |
| **Confidence** | confirmed by reading |

`:349-357` gates both `addSeparator()` and the disabled item carrying the locomotive's name on
`!paths.isEmpty()` — the *top-level* list. `:405` adds *More Destinations* outside that gate.

So a train standing where every reachable destination is a parking track or a reversing point gets a
bare `More Destinations` submenu butted against whatever precedes it, with nothing saying which
locomotive the menu is about. Before `FR-058` that case could not arise: any active destination put the
header up. Given twenty non-auto-destination squares on the operator's layout, it is reachable.

### C4 — `VD10-C11` is dispositioned fixed and two thirds of it is

| | |
|---|---|
| **Disposition** | fixed, all three - a skipped section exits 2 against a divergence's 1, and last run's report.md is deleted at the top of the script so a crash under set -e leaves no report rather than a stale one |
| **Confidence** | confirmed by reading `run.sh` at HEAD |

The disposition reads *"fixed - the frozen copy by default, and a skipped section exits 2"*.

The frozen copy is right: `:116` now defaults `ROUTES` to `test/operator_layout/...`, with the reasoning
at `:108-115` and `TC_ROUTES` still able to point it anywhere.

**It does not exit 2.** `:147` sets `CONDITION_STATUS=2` on a missing routes file, and `:163-166` then
does:

```sh
if [ "${CONDITION_STATUS:-0}" -ne 0 ]
then
    STATUS=1
fi
```

so the process exits **1**, which is the same code a genuine condition divergence produces. A caller
reading the exit status still cannot tell "the section did not run" from "the section failed", which is
the sentence `VD10-C11` was written around. Loud in stdout, indistinguishable in the exit code.

And the third of the finding's three bullets is untouched: the `for ENGINE` loop at `:123-127` still runs
under `set -e` (`:15`) and still sits before `compare.py` at `:157`, so a driver crash aborts the run
before the autonomy report is regenerated, leaving a stale `report.md` with no staleness marker.

Recorded here rather than reopened because both remainders are small; what makes it worth writing down is
that a disposition line said something specific and checkable that is not true, which is the class
`VD10-B7` exists for.

### C5 — the rename left a reference, a third time

| | |
|---|---|
| **Disposition** | fixed - and the paragraph no longer names a count; nine was the third wrong number in that sentence |
| **Confidence** | confirmed by grep over the tree |

`5c965948` renamed `testEveryDoorThatWritesTheSetupAnnouncesIt` to
`testEveryDoorThatWritesTheSetupAnnouncesItWiderSweep`. `docs/manual-tests/tests.md:13493` still says:

> *"All nine share one exit now, `setupChanged()`, and
> `testEditorSurfaceRules.testEveryDoorThatWritesTheSetupAnnouncesIt` fails if a writer does not end
> there."*

`VD9-C15` was a rename that left the sentence it was about; `VD10-C8` was its fix leaving a different
sentence; `VD10-D13` recorded, correctly, that *that* rename left nothing stale anywhere in the tree.
This one did. The same `MT-246` paragraph also still says "nine", which `A1` puts at seventeen or more.

### C6 — an assertion that cannot fail

| | |
|---|---|
| **Disposition** | fixed - the assertion is gone, with a line saying why rather than a silent deletion |
| **Confidence** | confirmed by reading; the class runs green either way |

`test/core/testAutoLayout.java:1612-1617`:

```java
        off.setActive(false);

        assertFalse(layout.isChoosableByAutonomy(off), "…");

        assertFalse(off.isActive(),
            "the switched-off square is the one the menu drops before the split, so this is the "
            + "property the drop reads");
```

The last assertion asserts the setter called two lines above it, with nothing in between. It is a
statement about `Point.setActive`, not about anything `FR-058` changed, and it will pass for as long as
a setter sets. The line above it is the real check and is a good one.

The rest of the test is sound and its MUTATION claim holds: making `isChoosableByAutonomy` ignore
`isAutoDestination` fails the `park` assertion. What the test does not do — and says so, which is the
right thing to have done — is exercise the menu, so `B1`, `C2` and `C3` are all outside its reach.

### C7 — `VD10-B1`'s hunk lost the paragraph breaks around it

| | |
|---|---|
| **Disposition** | fixed - the heading has its blank comment line and the double blank is closed up |
| **Confidence** | confirmed by reading |

`Layout.java:5111-5112`: the `VD10-A1` paragraph's last line, *"The clear follows it here for the same
reason it follows it there."*, is immediately followed by `// THE TRAIN ITSELF IS STOPPED FIRST
(VD10-B1).` with no blank comment line between them, so a heading reads as the continuation of the
paragraph above it. `:5147-5148` carries a double blank line where the old `setSpeed(0)` block was
lifted out, and `:5133-5134` runs the `catch` block straight into `synchronized (this.activeLocomotives)`.

Cosmetic, and recorded only because it is the same edit-mechanics failure as `C1` in the same file on the
same evening, at a severity nobody would otherwise write down.

### C8 — what the coalescing costs

| | |
|---|---|
| **Disposition** | fixed, on Adam's instruction - the loss is now reported with its remedy rather than deferred; deferring the rebuild would reintroduce OB-144 - see below |
| **Confidence** | confirmed by reading the call chain and both wrappers; the ordering questions traced to their event posts |

`VD10-C2`'s change is right and the reasoning for coalescing rather than pushing the exit back onto the
callers is right. Three things move with it, none large enough on its own to grade higher:

- **`onChanged`'s two cache refreshes now run against the old layout.** `TrainControlUI:4142-4143` ends the
  save runnable with `refreshStaticAutonomyLayer(); updateVisiblePoints();`, and `item()` calls
  `refresh()` — and therefore `onChanged` — *after* the door has already posted the rebuild. Before, the
  rebuild had completed by then. `updateVisiblePoints` decides which captions to draw by asking
  `isChoosableByAutonomy` of the **running** layout (`:1245-1247`), so it now answers from the layout the
  edit has not reached. It self-corrects — see `D5` — but the window exists where it did not.
- **The `isAutonomyBusy()` gate is asked one event later than the edit.** `rebuildRunningLayoutFromSetup`
  declines while autonomy is busy (`:5426`), and "busy" is now evaluated at the drain rather than at the
  write. The direction that matters is an autonomy run starting inside that window, which silently
  discards the rebuild for an edit that was made while nothing was running.
- **A pending rebuild at exit would be capture-fodder.** The exit path folds the **running** layout back
  over the configuration (`TrainControlUI:2259-2277`, `captureFromLayout` + `saveWithoutReconciling`), and
  `setupChanged`'s own javadoc explains that this is what deleted Adam's home setting. If the process ever
  died with the rebuild still queued, the capture would write the stale answer over the edit. Ordinary
  event ordering makes this hard to reach — the close event queues behind the `InvocationEvent` — and I
  could not construct a path to it, so it is recorded as a hazard the synchronous version did not have
  rather than as a defect.

**Fixed on 2026-09-04, and not the way this said it would be.**  The first bullet self-corrects, which
`D5` establishes independently, and the third could not be reached even by construction.  The second is
real: an autonomy run starting between the write and the drain discards the rebuild silently.

I proposed re-posting the declined rebuild when the run ends.  **That is worse than the defect.**
`rebuildRunningLayoutFromSetup`'s own comment says "the setup is the newer of the two" holds because
every caller reaches it BECAUSE the setup just changed, with the running layout captured upstream when
the editor opened.  A run breaks exactly that: it moves locomotives, `currentLoc` is the only place
that lives, and nothing folds it back.  Applying the rebuild afterwards regenerates every placement
from a setup that is now stale about where the trains are - which is `OB-144`, already fixed once in
this method.  The proposal would have paid a silent lost edit for a silent teleport.

So the loss is made loud instead, with the remedy in the sentence, and only at the door where a decline
is a surprise - `TrainControlUI:5566` has already warned about editing during a run.  `MT-267` carries
the check, because nothing in the suite can build the window, hold a configuration open and start a run
inside one event.
### C9 — the submenu is uncapped by argument rather than by measurement

| | |
|---|---|
| **Disposition** | fixed - the comment gives the measurement rather than the argument, and says what to do if a railway outgrows it |
| **Confidence** | confirmed by counting the operator's configuration |

`:402-404`: *"Uncapped, deliberately. The cap exists because the top level competes with the rest of the
menu for the first screenful; a submenu competes with nothing."* A submenu competes with the screen's
height, which is what `MAX_PATHS = 12` is really about. Swing renders an over-tall `JMenu` without
scrollers unless one is installed, and none is here.

Measured rather than guessed: 20 squares on `configuration-Main.json` carry `"autoDestination": false`
and 5 carry `canReverse`, and `withoutGoingNowhere` reduces to distinct destinations — so the ceiling
today is about 25 items, which fits. It is recorded because the reason given is not the reason that makes
it safe, and the number that makes it safe is a property of his railway rather than of the code.

### C10 — a menu that dispatches trains, shipped with no manual test

| | |
|---|---|
| **Disposition** | fixed - MT-266 is filed, covering B1, C2 and C3 in five steps |
| **Confidence** | confirmed by grep over `docs/manual-tests/` |

`docs/manual-tests/issues.md:393` records `FR-058` as **fixed unvalidated** with `-` in the "Became"
column, and `tests.md` has no entry for it. The only test is
`testTheDiagramMenuSplitsOnWhatAutonomyWouldChoose`, which asserts the predicate and says in its own
javadoc that the menu needs a window.

`docs/reviews/README.md` says *"When a round produces work for the layout, add entries there and put
their tags beside the findings here."* This is a menu whose items start trains, its list changed shape,
and three of this document's findings (`B1`, `C2`, `C3`) are about what the menu now shows. One `MT`
entry — right-click a train, count what is on the top level, open *More Destinations*, check the ellipsis
against what the autonomy tab lists — would cover all three.

---

## D — not defects

### D1 — the eight bundles are right, in letters and in meaning

| | |
|---|---|
| **Disposition** | closed |
| **Confidence** | confirmed by decoding every escape and reading each language |

`autolayout.errorRunStoppedByFailure` in all eight bundles: every file is pure ASCII, every value carries
`{0}` exactly once, no value contains a straight apostrophe, and `fr` and `it` use `’` where they
need one — which matters because the key goes through `MessageFormat`. Decoded:

- `de` — *"…auf ihrer Fahrstraße ausgefallen … damit die Anlage aufgeräumt … Prüfen Sie vor dem Start…"*
- `pl` — *"…uległa awarii … została zatrzymana … Autonomia zatrzymała się sama … uporządkować makietę … Sprawdź…"*
- `da` — *"…undervejs på sin rute … hvor det lokomotiv står, før du starter."*
- `es` — *"…falló a mitad de su recorrido … su vía se ha liberado … dónde está esa locomotora…"*
- `fr` — *"…est tombée en panne … L’autonomie s’est arrêtée d’elle-même … Vérifiez où…"*
- `it` — *"…si è guastata a metà percorso … L’autonomia si è fermata da sola così da…"*
- `nl` — needs no diacritic and has none, correctly.

Every one is idiomatic, not transliterated; `VD10-B4` is fully discharged. The new
`autolayout.ui.menuMoreDestinations` is present in all eight (`More Destinations`, `Flere destinationer`,
`Weitere Ziele`, `Más destinos`, `Autres destinations`, `Altre destinazioni`, `Meer bestemmingen`,
`Więcej celów`), all correct, all escaped, none needing a placeholder.

### D2 — `destinationItem` is behaviour-preserving

| | |
|---|---|
| **Disposition** | closed |
| **Confidence** | confirmed by diffing the extracted method against the block it replaced, statement by statement |

The power check, the `new Thread(...)` dispatch, both `invokeLater` dialogs, the outer try/catch and the
`"-> " + stationName(...)` label are all identical. The `path` capture is the one worth checking and it is
right: the old code captured the enhanced-`for` variable, which Java makes a fresh binding per iteration;
the new code captures a parameter, which is per call. Each item still holds its own path. The extraction
also removed a small hazard — the old block assigned into the method-scoped `menuItem` local that the
ellipsis at `:377` also uses.

### D3 — three classes run alone at HEAD, all green

| | |
|---|---|
| **Disposition** | closed |
| **Confidence** | confirmed by execution |

```
--- regression.testJavadocsAreAttached      Total tests run: 1,  Failures: 0, Skips: 0
--- regression.testEditorSurfaceRules       Total tests run: 37, Failures: 0, Skips: 0
--- core.testAutoLayout                     Total tests run: 24, Failures: 0, Skips: 0
```

Skips zero in all three, which is the half of "green" that a class skipping everything would satisfy.
`VD10-B3` is genuinely fixed: the ratchet passes with `setupChanged` moved off `refresh()`'s javadoc, and
the new `setupChangePending` field with its own one-line javadoc sits between the two methods without
orphaning anything — the field's doc attaches to the field and `refresh()`'s to `refresh()`.

The battery was not run and this says nothing about the other 145 classes.

### D4 — `VD10-B1`'s reorder is safe

| | |
|---|---|
| **Disposition** | closed |
| **Confidence** | confirmed by reading the handler end to end |

`loc.setSpeed(0)` now runs before `synchronized (this.activeLocomotives) { unlockPath(...); }`. Nothing in
the old ordering depended on the release having happened first: `unlockPath` reads `clearedEdges` and the
path, neither of which `setSpeed` touches, and the ordinary ending at `:5756` stops its locomotive by a
different route entirely. The try/catch is still positioned so a stop failure cannot mask the original —
the catch logs and falls through, and `throw e` is still the last statement of the block. Adam's ruling
("force a graceful stop, alert the user, then unlock") is now satisfied in that order, and
`testAutoLayout` asserts `loc.getSpeed() == 0` with a message that names the mechanism.

### D5 — the repaint does not overtake the rebuild, though it looks as if it must

| | |
|---|---|
| **Disposition** | closed |
| **Confidence** | confirmed by reading `repaintLayout` and the tail of `autonomyLoadedFromDiagram` |

The obvious hazard in `VD10-C2` is ordering: `setupChanged()` runs `onDiagramChanged` — which is
`invokeLater(repaintLayout())` (`TrainControlUI:4173-4174`) — **before** it posts the rebuild, so the
repaint's event is queued first and would paint the pre-rebuild layout.

It does not bite, for two independent reasons, and both are written down already.
`repaintLayout(boolean, boolean)` (`:26609`) does its work inside
`this.LayoutGridRenderer.submit(() -> SwingUtilities.invokeLater(...))` — a further executor hop and a
second event — which is exactly what `AutonomyViewerPanel:694-695` warns about: *"repaintLayout submits
to a background executor which then posts its own event, so hops queued here can and do run first."* And
`autonomyLoadedFromDiagram` ends with `invokeLater(() -> autonomySetupChanged())` (`:3641`), described at
`:3638-3640` as rebuilding both caches after a load. So the last paint of the sequence is the one after
the rebuild. `C8`'s first bullet is the part of this that does bite, and it is a different mechanism.

### D6 — `setupChangePending` is single-threaded

| | |
|---|---|
| **Disposition** | closed |
| **Confidence** | confirmed by grep over the class and by the method's visibility |

`setupChanged()` is `private` and has eighteen call sites, all in `AutonomyEditorPanel`. The class
contains exactly one `invokeLater` — the one `VD10-C2` added — and no `Thread`, `SwingWorker`,
`invokeAndWait` or executor. Every caller is a menu action listener, a checkbox listener or a method
called from one. So the plain `boolean` field is written and read on the event thread only, and the
missing `volatile` costs nothing. The modal-reentrancy question was chased separately: no door shows a
modal dialog *after* calling `setupChanged()` in the same event, and the one runnable that could
(`onChanged`) logs rather than shows, deliberately (`TrainControlUI:4130-4134`).

### D7 — the parity work is as dispositioned

| | |
|---|---|
| **Disposition** | closed |
| **Confidence** | confirmed by reading, and by executing the self-test |

`compare-conditions.py --self-test` prints three lines and the right three answers:

```
  reshaped   equivalent=True  expected=True
  mangled    equivalent=False expected=False
  identical  equivalent=True  expected=True
the comparison tells the three cases apart
```

`VD10-C12`'s "a comparison that cannot fail is not evidence" is answered by a fixture rather than a
sentence. `VD10-B5`'s floor is at `:241-250` with `TC_CONDITION_FLOOR` to override and a message that says
what to do. `VD10-C13`'s atom names now go through `json.dumps(..., sort_keys=True)` at `:42` and `:55`,
with a comment at `:39` saying why. `VD10-C10`'s report is written at `:287-303` and `run.sh:135-137`
asks for it — as `conditions.md` beside `report.md` rather than as a section inside it, which is what the
usage line always advertised. `C4` above is the one part of this block that is not as dispositioned.

### D8 — round two's remaining dispositions are true at HEAD

| | |
|---|---|
| **Disposition** | closed |
| **Confidence** | confirmed at the file in each case, not read off the disposition |

Checked the way `VD10-B7` found `VD9-C8` — by opening the thing rather than believing the line:

- `C1` — no `:5701` citation survives anywhere in `Layout.java`; the block that carried it is gone.
- `C4` — the summary table of `2026-09-03-validation-of-the-day.md` now reads 28 `fixed` and 13 `closed`,
  and **no** row reads `open`.
- `C5` — `CommandRow.java:289-292` now says there was no version in which four was right and gives the
  two-of-seven arithmetic.
- `C6` — `TrainControlUI:2663-2670` moved the count onto the removal clause, with the reason above it.
- `C7` — `AutonomyCompanionStore.java:1486-1496` retracts the `forgetBeforeEdit` citation explicitly and
  states the narrower licence that does apply.
- `C8` — corrected at both sites: `testSwitchingToACentralStationLayout.java:1121-1132` and
  `2026-09-03-test-suite-audit.md:1767-1774` both now say the weaker true thing.
- `C9` — `build.xml:117-138` cites by macro name, and names the route not taken.
- `C16` — `2026-08-31-independent-review.md:441-448` now quotes all seven lines and explains why the last
  two matter.

`C14`'s two `return`s are in place, `C3`'s paragraph break is back, and `C2` and `C15` are the two whose
fixes carry findings of their own here (`C8`, `C1`).

### D9 — `VD10-A1`'s fix is present and asserted

| | |
|---|---|
| **Disposition** | closed |
| **Confidence** | confirmed by reading and by running the test |

`Layout.java:5134-5139` now unlocks and *then* clears:

```java
                synchronized (this.activeLocomotives)
                {
                    this.unlockPath(path, loc);

                    this.clearedEdges.remove(loc);
                }
```

which is the ordinary ending's order. `testAutoLayout` carries the assertion with the `VD10-A1` reference,
and the class is green. The over-release `VD10-A1` described is closed at the sequencing level; whether it
ever happened on the railway is still untested, as `VD10` said.

### D10 — the unmatched `WRITERS` entry is false coverage, not a live hole

| | |
|---|---|
| **Disposition** | closed |
| **Confidence** | confirmed by reading both doors |

`session.setBlockingPoints(` matches nothing, but the two real writers —
`AutonomyEditorPanel.java:3501` (the checklist's OK) and `:5098` (the pick-on-the-diagram arm) — both end
in `placementChanged()`, with a comment at each saying the restriction is built into the configuration as
lock edges. So the behaviour is right today and the rule's belief that it is checking them is wrong. It is
counted in `A1` as coverage rather than as a defect.

### D11 — the wrappers announce, so two apparent holes are not

| | |
|---|---|
| **Disposition** | closed |
| **Confidence** | confirmed by opening both wrappers before calling anything silent |

A naive widening of the rule flags `buildFacingMenu` (`session.setFacing`, `:2809`) and `setArm`
(`session.setDirections` by way of `applyArmMask`, `:5331`). Both are fine: `radio(...)` at `:2898-2916`
and `toggle(...)` at `:2035-2084` (the call is at `:2081`) each run the action and then call
`placementChanged()`, with comments
recording that `OB-039`/`TD-1` moved the redraw into the helper for exactly this reason. `applyArmMask`
is still in `A1` because its *other* caller, `cycleBranching`, is not a wrapper and does not announce.
This is why `A1`'s table lists the door rather than the write site.

---

## What this pass did NOT examine

Said plainly, because a validation document that does not say where it stopped is not one:

- **The battery was not run**, on instruction. Three classes were run alone. The last full green (148
  classes) predates all four commits in this window, so nothing here establishes that the tree is green;
  it establishes that three classes are.
- **Nothing was tested on a railway, and nothing was tested through a window.** `A1`, `A2`, `B1`, `C2` and
  `C3` are all read from the code. `A2` in particular describes an emergency stop reached through five
  method calls; no train was watched stopping. `FR-058`'s menu was never rendered — the split, the
  ellipsis, the submenu's height and the missing header are all arguments about what the code will draw.
- **`FR-058`'s other half was not measured.** How many destinations actually reach the top level on the
  operator's layout depends on `getPossiblePaths`, which depends on where trains are standing. The 20
  non-auto-destination squares were counted from the configuration file, not from a running graph.
- **The parity harness was not run end to end.** Only `--self-test` was executed. No jar was downloaded,
  `setup-env.sh` and `run.sh` were read rather than invoked, and `C4`'s exit-code claim is read from the
  shell source rather than observed.
- **`VD10`'s 14 `D` findings were not re-derived.** This pass took them as given except where a `D` was
  directly load-bearing for something here.
- **The three `VD10` findings whose fixes are large were checked for correctness, not for completeness.**
  `A1`'s ordering fix, `B1`'s reorder and `B6`'s serialise-before-open were each read; none of the
  surrounding methods was re-reviewed.
- **`AutonomySession`'s twenty-six mutators were enumerated but not each read.** `A1` names eight doors it
  traced; the remaining `session.` and `session.getStore()` writers in the panel were classified by the
  rule's own algorithm and only the eight were opened.
- **`docs/manual-tests/tests.md` and `issues.md`** were read only where `FR-058` and `MT-246` touch them.
- **Everything before `f3dc0aec`.** `A2` and `C8` touch code older than the window and are flagged as such;
  `A2`'s mechanism has been reachable since `c892ec03` and neither earlier round found it, which is worth
  knowing when calibrating those documents as well as this one.
