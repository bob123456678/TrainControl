# The last day of commits, read line by line

**Status:** open

**Citation prefix:** `D24`. Cite findings from this document as `D24-A1`, `D24-B2`, and so on.

**What was reviewed:** every commit in the 24 hours to 2026-09-01, plus the working tree.
Branch `autonomy-diagram-r0` (v3.0.0). Range `ea4ddd1e^..828b1ff1` - 22 commits, from
"DAY-A1, DAY-B2 and the guard that could not see half the windows" (2026-08-31 03:29) to
"The cross takes the colour and the weight it should, and its test opens a sandbox"
(2026-09-01 03:24). 42 files, +7456/-167.

**Working tree:** the only uncommitted changes are `cs2_sample_layout/config/autonomy/setup.json`
and `configuration-Main.json`. They are the operator's own editing state, not harness damage - see
D24-D7. No source or test file differs from HEAD.

**Method:** reading only. No test was run, nothing was built, nothing was written to the repository
except this file. Where a claim would need a run to settle, it is stated as an open question at the
bottom rather than asserted.

**Reviewed by:** Claude, 2026-09-01.

---

## Summary

| | Count |
|---|---|
| A | 1 (already recorded by the author; restated as a release gate) |
| B | 5 |
| C | 11 |
| D | 8 |

The three I am most confident are real and new: **D24-B1** (the reachability screen and the route
search disagree about the same starting square, and the stricter one is the one that answers
IMPOSSIBLE), **D24-B2** (the new train-length guard's completeness test is one layer above where
lengths are actually stored, so it both under-counts and, on the operator's own configuration,
almost never fires), and **D24-B3** (the "one home per platform" guard went into the one door with
no production caller, while the door a file comes through had its check removed in the same commit).

---

## A - wrong behaviour on the layout, or data silently lost

| | Finding | Status |
|---|---|---|
| A1 | Return Home cannot get the operator's parking berths back; the test that says so is out of the battery | ALREADY RECORDED - open, restated as a release gate |

### A1 - `testTheParkingBerthsGetTheirTrainsBack` is excluded because it fails on a real defect

`test/regression/testEveryTestIsInTheBattery.java:43-49` adds a second entry to
`DELIBERATELY_OUT`:

> `"FAILS on a measured defect, not on itself: BottomMainB (eastbound, reverse) is a destination with ZERO outgoing edges on the operator's derived graph, so a train that reverses there can never leave and Return Home is right to answer IMPOSSIBLE."`

The exclusion is honestly written and `testTheExclusionListIsStillOneEntry` was updated as a
decision, with the reason in the table - that is the discipline working. The finding is not the
exclusion; it is that v3.0.0 is being prepared with the feature Adam asked for
("it should be easily possible to get back") not working on his own railway, and with the
446-line test that proves it not running in `ant test`.

Nothing in this review is asked of it beyond: it is still open, and it is the only A on the list.

---

## B - incorrect results, or crashes in specific configurations

| | Finding | Status |
|---|---|---|
| B1 | `HomeStaging.connected` and `firstClearRoute` initialise `turned` differently for the same square; the stricter one decides IMPOSSIBLE | open |
| B2 | The train-too-long guard proves "measured" at the edge, but lengths are stored per tile | open |
| B3 | The "one home per platform across its copies" guard is on a door with no production caller | open |
| B4 | `turned` is a boolean where the runtime flips direction once per reversing point | open |
| B5 | The new cross reads a property `AutonomyBuilder` does not emit for a non-station square | open |

### B1 - the screen that proves a home unreachable is stricter than the search that would find the route

`HomeStaging.firstClearRoute` starts its search with the train already turned when it is standing
on a reversing point (`HomeStaging.java:949`):

```java
queue.add(new Candidate(from, new LinkedList<Edge>(),
    new HashMap<String, Accessory.accessorySetting>(), from.isReversing()));
```

with the comment above it: *"A train already standing on a reversing point sets off turned;
anywhere else it does not."*

`HomeStaging.connected(from, to, mustReverse)`, written the same day, does the opposite
(`HomeStaging.java:1682`):

```java
seen.add(from.getUniqueId() + "/false");
queue.add(from);
turned.add(false);
```

with its own comment: *"The same test reversesAlongTheWay applies, asked one step at a time."*

Both are deliberate. They contradict, and the direction matters: `connected` is reached from
`canGetHome` (`HomeStaging.java:1556`), which feeds `unreachable` at `HomeStaging.java:443-445`,
and `unreachable` is a **proof of impossibility** - the plan comes back `IMPOSSIBLE` naming the
locomotive rather than `NO_PLAN_FOUND`. So for a non-reversible locomotive standing on a reversing
destination whose home is a terminus reachable without a further reversing point, Return Home
answers IMPOSSIBLE for a journey `firstClearRoute` would have routed.

**Is that reachable?** On the diagram-derived graph, `AutonomyBuilder.java:970` emits
`terminus` when the copy stops and `reversing` otherwise, so a reversing Point is not a
destination there and `firstClearRoute`'s `if (!from.isDestination()) return null;` fires first.
On a **legacy** graph it is reachable directly: in
`cs2_sample_layout/config/autonomy_legacy/autonomy.json`, thirteen points carry
`"reversing": true` **and** `"station": true` - `TunnelLeftPark`, `TunnelCenterPark`,
`TunnelRightPark`, `TunnelLongPark`, `TopMainR0Park`, `TopR1ParkLong`, `TopR1ParkShort`,
`ParkingTrack4`-`ParkingTrack12`. A 2.7.4c user who has not migrated is on exactly that graph.

There is a second, smaller disagreement inside the same expression: the runtime flips direction on
arrival at a **terminus or** a reversing point (`Layout.java:5575`), but
`firstClearRoute` only asks `from.isReversing()`, never `from.isTerminus()`.

This is the shape the codebase's own comment in `hasAutonomousDestination` warns about - *"every
clause pickPath applies to its candidates has to be mirrored here"* - and the memory rule that a
conservative screen may refuse but may never *prove* impossible using a rule the executor does not
enforce.

**Not covered by a test.** `testATrainThatCannotReverseHasToBackIntoItsHome`
(`test/core/testHomeStaging.java:3477`) starts the train on `HS S`, a plain station, so the
initialisation never differs.

### B2 - "the total has to be complete" is enforced one layer above where lengths live

`Layout.isPathClear`, `Layout.java:2330-2363`:

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

The comment above it states the rule it is enforcing: *"An unmeasured segment used to contribute
nothing while the sum went ahead without it, which is 'I do not know how long this is' answered as
'it is zero' - and that refuses a train that would have fitted."*

That is exactly what still happens, one level down. On the diagram model an Edge's length is not
authored; it is derived in `GraphReducer.java:946`:

```java
new LinkedHashMap<>(commands), sumLength(path) + lengthOf(tile),
```

and `lengthOf` / `sumLength` (`GraphReducer.java:1047-1061`) are
`Math.max(0, authored.getTileLength(tile))` summed over the tiles the edge spans, where
`AutonomyCompanionStore.getTileLength` returns `0` for any tile nobody has measured. So
`segment.getLength() > 0` proves only that **at least one tile** on that segment carries a length.
Every unmeasured tile on a segment that also holds a measured one contributes zero to `room`
while the path still counts as fully measured - and the guard then refuses a train that would have
fitted, which is the failure the comment says it removed.

The other half is worse in practice. The operator's live configuration
(`cs2_sample_layout/config/autonomy/setup.json`) records **six** tile lengths in total:
`1 - Main:0,11`, `1,10`, `5,4`, `14,3`, `20,13`, `20,14`. Every path whose approach edges contain
none of those six tiles has `measured == false` and is not judged at all. The guard Adam asked for
is, on his own railway today, close to inert - and the comment's claim that *"one that records some
is asked, in the editor, for the ones that decide this"* is not true of the squares that decide it
(see D24-C7).

**Not covered by a test.** `testATrainTooLongForTheBerthIsNotBackedOverTheSwitch` and
`testTheRoomIsEverySegmentLeadingUpToTheReversal` (`test/core/testNonReversibleTrains.java:264`,
`:327`) both call `Edge.setLength(...)` directly, so neither ever meets a partly-measured edge.

### B3 - the platform-uniqueness guard went onto the one door nothing calls

`Layout.setHomeLocomotive` gained (`Layout.java:1218-1235`):

```java
for (Point other : this.points.values())
{
    if (other != p && other.isSamePlaceAs(p)) other.setHomeLoc(null);
}
```

with a comment saying it closes DAY-A1: two locomotives homed on two copies of one platform, which
`sharesSection` (`HomeStaging.java:1762`) then reports as IMPOSSIBLE naming both for the rest of
the session.

`setHomeLocomotive` has **no caller in `src/`**. `grep -rn "setHomeLocomotive" --include=*.java .`
returns its own definition, two comments, and 20 call sites in `test/core/testHomeStaging.java`.
There is no scripting engine in this codebase (`grep -rln "ScriptEngine\|Nashorn"` over `src/` is
empty), so the comment's "it does not stop the scripting API" no longer describes anything.

The door a home actually arrives through is `parseAuto`, `Layout.java:7180`, and the **same commit**
reduced it to one unguarded line:

```java
homeAt.setHomeLoc(home);
```

`rebuildHomeStations` catches one locomotive named twice (`Layout.java:1134`) and `claimHome`'s
new `isSamePlaceAs` sweep (`Layout.java:1088-1091`) catches the positional case - but neither
catches two **different** locomotives assigned to two copies of one square, which is precisely
DAY-A1. From the diagram editor this cannot happen, because `AutonomyBuilder.homeCopy` writes the
home onto exactly one copy. From a hand-edited or legacy `autonomy.json` it can, and the check that
used to stop it there (LD-8) was deleted in this round.

This is the repository's own most-repeated mistake: *"When you fix a call site, grep for its twins
before closing the finding."*

### B4 - `turned` is a flag where the railway does arithmetic

`Layout.java:5296` flips the locomotive at every **intermediate** reversing point on a path, and
`Layout.java:5575` flips it again on arrival at a terminus or reversing destination. So a
non-reversible train leaves its terminus facing the right way only when the number of intermediate
reversing points is **odd**.

Both planner searches record a monotone boolean:

- `HomeStaging.java:961` - `boolean turned = current.turned || next.isReversing();`
- `HomeStaging.java:1697` - `boolean now = reversed || next.isReversing();`

Two intermediate reversing points therefore satisfy `mustBackIn` while returning the train to the
facing it set out with. On the operator's own legacy graph such routes exist: a walk of
`autonomy_legacy/autonomy.json` finds **20** routes from a destination to a terminus carrying two
or more reversing points, e.g.
`BottomMainA -> TunnelParkReverse -> TunnelCenterPark -> BottomMainAPre -> TopMainPostReverse -> TopMainR0`.

What makes this worth more than a C is the tier move made the same day: `isPathClear` no longer
refuses a terminus to a non-reversible locomotive (`Layout.java:2280-2297`), and the comment there
says so explicitly - *"Staging does not lose the rule by this: HomeStaging carries its own."*
HomeStaging is now the only enforcement, so a plan that satisfies the boolean and not the parity
executes, and leaves a locomotive that cannot reverse standing in a terminus pointing the wrong
way.

I have not shown that the BFS *selects* such a route - it returns the shortest route satisfying the
constraint, so a one-reversal route wins where one exists. Reachability is therefore real but
unproven; severity is set at B for the consequence, not the likelihood.

### B5 - the cross reads a property the model drops for non-station squares

Both badge sites now pass `shut`:

- `AutonomySession.java:4365` - `Boolean.FALSE.equals(getPointProperty(tile, "active"))`
- `AutonomyEditorPanel.java:5961` - the same expression

and `TileAnnotation.isImpassable()` (`TileAnnotation.java:291`) returns it unchanged, so the
diagram draws a cross meaning *nothing may stop here and nothing may pass through*.

`AutonomyBuilder.java:941` deliberately does not emit that property for a square that is not a
station:

```java
if ("active".equals(key) && !point.isStation()) continue;
```

The editor's three-way menu offers **Out of service** on every square, station or not
(`AutonomyEditorPanel.java:1084-1087` - `setUsage(target, isStation, false)`), and
`setUsage` stores `active = FALSE` regardless (`AutonomyEditorPanel.java:2743`). Nothing in
`GraphReducer` reads `active`. So on a plain sensor the operator sets Out of service, the editor now
draws a cross asserting that nothing can pass, and the emitted graph has an active Point that
`isPathClear` will route trains straight through.

The dropped-at-build behaviour predates this round (commit `f11e96bb`, 2026-08-16). What is new is
that the diagram now states the opposite of the model instead of merely tinting the badge orange.
The underlying question - whether "Out of service" on a non-station square is meant to reach the
model at all - is Adam's, and is listed under the open questions.

---

## C - cosmetic, dead code, narrow edge cases

| | Finding | Status |
|---|---|---|
| C1 | `refreshAllProtectingSignals` has no production caller; its javadoc still says when it is called | open |
| C2 | `firstOnTheRailway` is computed, threaded through and documented, and used by nothing | open |
| C3 | The removed sweep's argument survives above its own removal, in two places | open |
| C4 | `setHomeLocomotive` keeps the "Refused at the MODEL door" paragraph above "NOTHING IS REFUSED HERE ANY MORE" | open |
| C5 | Two places state that `isPathClear` refuses a terminus unless the path reverses. It never did, and now it does not refuse at all | open |
| C6 | An inverted test kept its old javadoc, so its MUTATION line is now backwards | open |
| C7 | The editor asks for a length on the reversal square only; the guard needs the whole run-in - and it lists 20 squares on the live layout | DEFERRED - needs Adam |
| C8 | The signal ruling's cost is two-sided; MT-246 records one side | DEFERRED - needs Adam |
| C9 | A shut square that is neither a station nor a turnaround draws no cross on the running diagram | open |
| C10 | The cross is drawn at the track centre while the train star follows the offset badge | open |
| C11 | OB-167 was fixed in three commits and has no receipt row in `issues.md` | open |
| C12 | The placeholder locomotive is taller than a real icon, and its pantographs leave the canvas below about 20px | open |

### C1 - a public method that documents a call that no longer exists

`Layout.refreshAllProtectingSignals()` (`Layout.java:6089`) is now called only from
`test/core/testAutoLayout.java:966` and `test/regression/testBothProtectingSignalsAreThrown.java`.
Its javadoc still opens *"Called when a run begins, and forgetting the memo is not enough on its
own"*, and goes on to argue for the sweep at length. That paragraph is the reason a future reader
would put the call back.

A consequence worth stating separately: `this.signalAspects.clear()` now never runs in production.
That turns out to be harmless - see D24-D2 - but it is no longer true that the memo is dropped at
the start of a run, and nothing says so.

### C2 - a dead parameter with a paragraph of justification

`Layout.java:4913` still computes

```java
final boolean firstOnTheRailway = this.locomotiveThreads.incrementAndGet() == 1;
```

under six lines of comment explaining that the sweep has to ask "am I the first thing running" at
the increment rather than later; passes it at `:4919`; declares it at `:5019` with an `@param`
describing *"the fact, as it was at that instant, rather than the counter to ask again"*. Nothing in
`executePathInternal` reads it. The `incrementAndGet()` is still needed; the boolean and the
parameter are not.

### C3 - the argument for the sweep outlives the sweep

Two comment blocks now argue for something and then say it is gone:

- `Layout.java:5108-5141` - eight paragraphs beginning *"The signals are swept, exactly as the other
  two doors into a run sweep them (AU-B7)"*, ending *"The sweep that stood here is gone (OB-166)."*
- `Layout.java:5946-5960` region and the `refreshAllProtectingSignals` javadoc, as C1.

Not a defect; recorded because in this codebase the comment is the design record, and a record that
argues both ways in one block is the one thing it cannot afford to be. The removal reason is worth
keeping; the case for the thing removed is what has stopped being true.

### C4 - `setHomeLocomotive`'s two contradictory paragraphs

`Layout.java:1178-1206`. The block still reads *"Refused at the MODEL door, not only in the menu ...
this is a state Adam has ruled invalid rather than merely unwise - 'any home with two graph points
should be refused'"*, and then, without a break, *"NOTHING IS REFUSED HERE ANY MORE (Adam,
2026-08-31)"*. A reader arriving at the top of the block reads a rule that no longer exists as
though it did.

### C5 - the runtime rule two comments rely on was never what they say

`HomeStaging.connected(Point, Point, boolean)` javadoc (`HomeStaging.java:1650-1666`):

> *"The runtime already insists - `Layout.isPathClear` refuses a terminus to a locomotive that cannot reverse unless the path passes a reversing point, so it arrives already turned"*

`isPathClear` never had that exemption. The clause it removed was, verbatim:

```java
if (path.get(path.size() - 1).getEnd().isTerminus() && !loc.isReversible())
```

- a flat refusal with no reversal test at all. As of 2026-09-01 it does not refuse a terminus at
any tier. The same claim is repeated in the test:
`test/core/testHomeStaging.java:3503-3505` - *"The runtime refuses exactly that, so the plan could
only have failed on its first move."*

The planner's rule is still what Adam asked for. What is wrong is the stated reason for it, which is
now the only place a reader would go to learn why the boolean exists (see D24-B4).

### C6 - the assertion was inverted; its javadoc was not

`test/regression/testBothProtectingSignalsAreThrown.java:355-379`. The method was renamed to
`testAHandDispatchLeavesAStandingTrainsSignalAlone` and its assertion changed from `assertTrue` to
`assertFalse`, with a good comment at the assertion explaining the ruling. The javadoc above the
method still describes the old behaviour throughout - *"So starting autonomy threw that platform red
and hand-dispatching a different train left it green with a train standing at it ... Two of the
three doors did"* - and ends with

> `MUTATION: removing the refreshAllProtectingSignals() call from executePath fails this test.`

which is now exactly backwards: removing it is what makes the test pass. The test itself is sound -
restoring the sweep would command the signal RED and the assertion would fail.

### C7 - the notice asks about the reversal square, the guard needs the run-in

`AutonomySession.reversalsWithoutLength()` (`AutonomySession.java:1921-1943`) lists turnaround
squares whose **own** tile has no length:

```java
if (!isTurnAround(tile)) continue;

if (store.getTileLength(tile) <= 0) out.add(tile);
```

The guard in `isPathClear` needs every **segment of the approach** measured, and the plain track
between two sensors carries no point at all - `measuresAnyTrack`'s own javadoc says so. So an
operator who clears every notice this raises still has paths the guard will not judge.

Measured on the live configuration: 22 turnaround squares, 6 tiles with a length recorded, of which
2 are turnarounds - so this raises **20 new WARNING findings** in the autonomy editor
(`1 - Main:2,5`, `2,7`, `4,5`, `8,6`, `9,6`, `10,6`, `10,9`, `21,6`, `22,6`, and eleven squares on
`2 - Bottom`). The code comment says the "any length set anywhere" condition *"is what stops this
being a nag"*; on his own layout it does not.

**DEFERRED - needs Adam:** should the notice ask for the approach segments as well as the reversal
square, given that without them the guard never fires - and is a 20-item warning list what he wants
on first opening the editor after upgrading?

### C8 - the signal ruling gives up two things and MT-246 records one

`docs/manual-tests/tests.md`, MT-246, under "What this deliberately gives up", records only the
direction where a hand-placed train's platform stays **green**. The other direction follows from the
same removal: a platform a route left **red**, emptied by hand while nothing was running, now stays
red into the next run. `refreshProtectingSignal` returns early when `!isRunning()`
(`Layout.java:5960`), no occupancy change is coming for a square nobody touches, and there is no
longer a sweep at the start of a run. It self-corrects only after a train has arrived there and left
again.

**DEFERRED - needs Adam:** on a railway where a protecting signal is wired to a braking section, a
signal stuck red over an empty platform holds a train up. Is that acceptable alongside the
green-over-occupied case, or should the sweep come back for **occupied** platforms only - which is
the option MT-246 already offers?

### C9 - the running diagram's badge test does not include the case its own comment names

`AutonomySession.java:4342`:

```java
boolean worthABadge = store.isStation(tile) || isTurnAround(tile);
```

Six lines above it, the comment says what a badge is for: *"this is a station, trains turn round
here, autonomy is not using it."* The third has no term in the expression, so a square that is
switched off and is neither a station nor a turnaround draws nothing at all on the running diagram,
while the editor (`AutonomyEditorPanel.badgeFor`, which gives every graph point a badge) draws its
cross. Unreachable on the live configuration - every `active: false` square in
`configuration-Main.json` also carries `mustReverse` - which is why this is a C and not a B.

### C10 - the cross and the train star are placed by different rules

`TileAnnotation.paintBadge` records `badgeDrawnAt` at `:1610`, **after** the rule that moves a
station's badge to the bottom-left corner on a bend (`:1582-1594`), and that field is what the
"train is standing here" star follows (`:1079`). The cross added at `:1651-1675` is drawn at
`on[0]/on[1]` - the raw track centre - and returns before anything else. So a shut station on a
curve, in the editor, with a train on it draws the cross on the rails and the star in the corner:
the collision the corner rule exists to prevent, with the two marks that are about the same square
in different places.

### C11 - OB-167 has no receipt

`docs/manual-tests/issues.md:314` files OB-167 in the open section. It was implemented across
`d155a1b6`, `e9435bfc` and `828b1ff1`, and has tests
(`testASquareNothingCanUseIsDrawnAsACross`, `testTheCrossKeepsItsWeightAsTheTileGrows`). OB-166 and
FR-054 both got rows in "What has been picked up" in the same round (`:346-347`); OB-167 did not,
so it still reads as outstanding.

### C12 - two small things about the placeholder locomotive

`LocomotivePlaceholder.ASPECT` is `0.4f`, so `image(LOC_ICON_WIDTH)` at
`TrainControlUI.java:9091` produces 296x118. A real cropped icon is exactly
`LOC_ICON_WIDTH` x `LOC_ICON_HEIGHT` = 296x114 (`TrainControlUI.java:287, 290`, and the comment at
`:22778`). The javadoc for `ASPECT` says the ratio exists so *"a placeholder in a different shape
would make the list jump wherever one appears"* - it is four pixels out of agreeing with the thing
it was chosen to match.

Separately, `pantograph()` draws from `roofTop - height`, and at the minimum size the arithmetic
puts that above the canvas: at `h = 10`, `roofTop` is 1 and `pantograph` is 3, so the diamond's tip
is at `y = -2`. Both current call sites (142 and 296) are far above that, so this is a trap for the
next caller rather than a visible fault - but the class comment promises *"PADDING ALL ROUND, so
nothing is cut off at the edges"* and `testNothingTouchesTheEdges` presumably does not test a small
one.

---

## D - not defects

| | What was checked | Result |
|---|---|---|
| D1 | The eight message bundles | clean |
| D2 | `signalAspects` never being cleared in production | harmless |
| D3 | The terminus rule's move from `isPathClear` to the selection tier | complete |
| D4 | The right-click menu's inactive-destination filter removing the only way there | it does not |
| D5 | `Badge.equals` / `hashCode` and the new `shut` field | handled |
| D6 | `LOAD_IMAGES` and the placeholder in the selector | unreachable |
| D7 | The working tree's edits to `cs2_sample_layout` | the operator's own state |
| D8 | The `isReversing` half of the new length guard reaching autonomy's own choices | it does not |

**D1.** `autolayout.errorTrainTooLongToReverse` is present in all eight bundles with the same three
placeholders; `autolayout.errorHomeSquareIsSeveralPoints` is removed from all eight and has no
remaining reference in `src/` or `test/`; the key sets of all eight are identical to
`messages.properties`; no file contains a non-ASCII byte.

**D2.** The obvious worry about C1 is that a stale `signalAspects` entry makes `refreshOneSignal`
skip a command it should send. It cannot: the early return at `Layout.java:6049` is
`showing != null && showing == claimed && acc.isRed() == claimed`, so the memo alone can never
authorise the skip. Walked through the four transitions across a run boundary; each either commands
correctly or is already showing the right aspect.

**D3.** The clause removed from `isPathClear` reappears in all three places that choose rather than
execute: `pickPath` (`Layout.java:3733`), `hasAutonomousDestination` (`:3475`), and
`barredFromAutonomy` (`:4003`). `isChoosableByAutonomy` passes a null locomotive and is therefore
unaffected, which is right - it answers a question about the square. `canReachAnyDestination`
(`:6158`) takes no locomotive and could not carry the clause.

**D4.** The menu comment claims *"They remain reachable - the autonomy tab lists everything."*
Verified: `AutoLocomotiveStatus.java:169` and `:354` call
`layout.getPossiblePaths(locomotive, true)` with no `isActive` filter.

**D5.** `shut` was added to `Badge.equals` (`TileAnnotation.java:312`) and `hashCode` (`:320`, bit
64), so the annotation cache cannot serve a stale badge - the "new field needs the copy constructor"
class of bug was not repeated here. The 8-argument constructor delegates with `shut = false`, so
existing callers are unchanged.

**D6.** `LocomotiveSelectorItem` now falls into the placeholder branch whenever
`TrainControlUI.LOAD_IMAGES` is false, which would put a drawn locomotive against every entry that
has a real picture. `LOAD_IMAGES` is `public static final boolean ... = true`
(`TrainControlUI.java:323`), so the branch is unreachable. Noted only because it would become live
the moment anybody makes that a setting.

**D7.** The two modified files under `cs2_sample_layout/` name `MT-233 Test Loc 2` and
`MT-x233 Test Loc`. Those are locomotives Adam created by hand while running manual test MT-233 -
`test/core/testRoutePicking.java:465` quotes him doing it - not names any automated test creates.
The rest of the diff is caption placements, barred arrivals and a home moving. This is his editing
state, not harness damage.

**D8.** The new guard fires for `ending.isTerminus() || ending.isReversing()`, which is wider than
Adam's wording. It does not widen what autonomy refuses: `pickPath` already excludes
`end.isReversing()` before `isPathClear` is consulted, so the `isReversing` half only reaches manual
dispatch and the staging planner, where a reversing point can be a destination on a legacy graph.

---

## Open questions - things I could not settle without running

1. **Does `testTheParkingBerthsGetTheirTrainsBack` still fail for the reason its exclusion gives?**
   (D24-A1). The claim - BottomMainB (eastbound, reverse) has zero outgoing edges on the derived
   graph - is checkable in one run and is the release gate.
2. **Does `testATrainThatCannotReverseHasToBackIntoItsHome` still pass?** It should, and its passing
   does not contradict D24-B1: its fixture starts on a plain station.
3. **A test for D24-B1:** the same fixture with `HS S` made reversing, a non-reversible locomotive
   on it, and its home the terminus, with nothing else reversing. `connected` should answer
   unreachable and `firstClearRoute` should return a route. If the plan comes back IMPOSSIBLE while
   `layout.getPossiblePaths` offers the journey, B1 is confirmed.
4. **A test for D24-B2:** two edges, the first spanning a measured tile and an unmeasured one. There
   is no unit-level door to that today - the existing tests set `Edge.setLength` directly - so it
   needs a `GraphReducer` fixture rather than a `Layout` one.
5. **Is "Out of service" meant to do anything on a square that is not a station?** (D24-B5). If yes,
   `AutonomyBuilder.java:941` is dropping the operator's instruction; if no, the menu should not
   offer the option and the cross should not be drawn for it. Adam's call.
6. **Does a protecting signal actually hold a train on the real railway** (D24-C8), or is it
   display only? The answer decides whether the stuck-red case is a C or something more.
