# Test-suite review: which of these tests cannot fail? - 2026-08-31

**Status:** open

**Prefix for citing this document: `TCS`.** Cite findings from here as `TCS-A1`, `TCS-B2` and so on.
`TS` (2026-08-19), `TA` (2026-08-24) and `TST` (2026-08-28) are the three earlier passes over this same
suite; nothing below repeats one of theirs, and where a finding is a new instance of a pattern they
named, it says so and cites them.

**What was reviewed.** Branch `autonomy-diagram-r0` at `e4c94ac9`, tagged `v3_0_0_rc4`, on 2026-08-31.
141 test classes on disk under `test/`, 140 registered in `build.xml`, 1,204 `@Test` methods, reported
as 140 classes green. **Nothing was run.** Every finding below was reached by reading the test beside
the production code it names, and each one says exactly what would confirm or refute it.

**One file moved under this pass.** `test/core/testHomeStaging.java` was written to at 03:16 today, part
way through, by something else working on the same tree - it gains a new
`testASecondLocomotiveDoesNotHomeOnTheOtherCopyOfAPlatform` about `claimHome`'s injectivity test
comparing Points rather than blocks. Everything below reads the **committed** version at `e4c94ac9`, and
every line number for that file is against the commit. The new test was not reviewed, and it is adjacent
to `TCS-C2`; whoever owns it should read the two together.

**Method.** Three passes, in this order:

1. Mechanical scans over all 141 classes - `@Test` methods with no assertion in their own body; methods
   where no assertion sits at the top level of the method (154 of them); loops with no floor;
   `SkipException`, `enabled = false` and headless gates.
2. Every production change made since the 2026-08-28 pass - `sanitizeMultiUnits`, `claimHome`/`atHome`,
   the three-layer diagram paint, the non-atomic lock release, `timetableSignature` - read against the
   test that claims to guard it.
3. The house's own `MUTATION this catches:` convention taken at its word: for each claim, read what the
   named mutation would actually change and decide whether *this* test would notice. 200 such claims
   exist; the ones examined are listed under `TCS-D1`.

**The shape that came up most.** Four of the fifteen findings below are one shape, and all three A
findings are among them: a fix has two halves - a new helper and the call that uses it - the test drives
the helper, usually by reflection, and never the call. The helper is then perfectly guarded and the call
site is the only uncovered part, which is where the defect was. `TCS-A1`, `TCS-A2`, `TCS-A3` and
`TCS-C4` are all this. It is worth naming because the suite is otherwise unusually strong: the failures
here are not carelessness, they are what happens when a fix is tested at the layer it was written at.

---

## Status

| | Finding | Disposition |
|---|---|---|
| **A1** | The MT-149 timetable-redraw guard tests the helper, not the guard or the call | open |
| **A2** | The OB-159 z-order test passes with OB-159 put back, two different ways | open |
| **A3** | The OB-164 change to `unlockPath` has no test, and the test that names that branch models the code before it | open |
| **B1** | Four classes restore the layout preference while their `TrainControlUI` is still alive; two more never dispose one | open |
| **B2** | `testEveryLanguageFits`'s two safety assertions are each satisfied by nothing | open |
| **B3** | `Outcome.NO_HOMES` is asserted by no test in the suite | open |
| **B4** | `testALocomotiveInOnePlaceIsNotReported` - a control that asserts nothing about its own fixture | open |
| **C1** | `testATrainIsNotPlannedIntoItsOwnDetectionSection` closes on `!= READY` | open |
| **C2** | A home on a split square is triaged and never planned or executed | open |
| **C3** | `testRouteTilePlacement` throws instead of reporting, on its own failure path | open |
| **C4** | The routing tooltip is refreshed by reflection, never through the control | open |
| **C5** | Three more vacuous-on-empty loops, outside `TST-C8`'s list | open |
| **C6** | The window-icon guard cannot see `JOptionPane.createDialog`, and two live dialogs have no icon | open |
| **C7** | Three negative controls with no floor | open |
| **C8** | A `MUTATION` line that names the wrong guard | open |
| **D1** | Checked and sound - receipts | closed |
| **D2** | `build.xml` reconciles exactly against disk | closed |
| **D3** | Withdrawn: "the multi-unit sweep's eviction half is untested" | closed |

---

# A - High

A finding is `A` here when a regression in the production code it names would ship unnoticed: the test
that exists to catch it stays green. That is the calibration `TST` used and it is kept, so the two
documents can be counted together.

## TCS-A1. The MT-149 timetable-redraw guard tests the helper, not the guard, and not the call

**Confidence: confirmed by reading.** Verified twice - once directly, and once by an independent reader
who was given the file and the convention and nothing else, and who reached the same conclusion from
the same two line numbers.

`test/ui/testARenameReachesTheTimetableOnScreen.java:46` claims:

> MUTATION this catches: keying the guard on `timeTable.hashCode()` again.

It does not. The guard is at `src/org/traincontrol/gui/TrainControlUI.java:23925-23929`, inside
`repaintTimetable()` (`:23871`):

```java
String showing = timetableSignature(timeTable);

if (showing.equals(lastTimetableState)) return;
```

The test never calls `repaintTimetable`, and it does no source scan. Its only entry into production code
is the extracted helper, reached by reflection at `:125-130` and `:152`:

```java
java.lang.reflect.Method signature = TrainControlUI.class.getDeclaredMethod(
    "timetableSignature", List.class);
```

All three payload assertions - `before.contains(BEFORE)` at `:132`, `assertNotEquals(after, before)` at
`:154`, `after.contains(AFTER)` at `:160` - read only that helper's return value. Restoring the
historical bug, i.e. putting `if (timeTable.hashCode() == lastTimetableState) return;` back at `:23927`
with `lastTimetableState` an `int` again, leaves `timetableSignature` byte-for-byte unchanged and every
assertion still passing. `timetableSignature` has exactly one caller in the whole of `src/`, which is
that line - so deleting the call is invisible to this test by construction.

**And the second half of the fix is untested too.** The same commit added `repaintTimetable();` at
`TrainControlUI.java:3908`, inside the `invokeLater` block the rename repair posts - and the class
javadoc credits it ("the rename repair asks for the repaint at all, which it never did"). Nothing in the
suite mentions `repaintTimetable` except this file's prose: `grep -rn "repaintTimetable" test/` returns
five hits, all comments. Delete `:3908` and the suite is green while Adam's reported symptom - "the
timetable is not updated" - comes straight back.

MT-149 was filed critical. Both halves of its fix are unguarded.

**How to confirm.** Two edits, one at a time, then `ant test` restricted to
`testARenameReachesTheTimetableOnScreen`:

1. At `TrainControlUI.java:23925-23929`, replace the two lines with the int-hash guard. Expect: green.
2. Delete `TrainControlUI.java:3908`. Expect: green.

**Smallest fix.** Behavioural, and it needs no new fixture: the test already builds the window and holds
the `Layout`. Read the `timetable` JTable by reflection - `testTimetableColumnHeadings.java:97-104`
already has that helper - call `repaintTimetable()` reflectively on the EDT before and after the rename,
and assert the table's cell text moves from `BEFORE` to `AFTER`. That drives the guard, and it drives
`:3908` too if the rename is put through the window rather than through `model.renameLoc` directly.

## TCS-A2. The OB-159 z-order test passes with OB-159 put back, two different ways

**Confidence: confirmed by reading.**

`test/ui/testDiagramLooksRight.java:2056`, `testTheTrainIsDrawnOverTheStationCaption`, is the only test
of the three-layer paint. Its claim at `:2050-2053`:

> MUTATION this catches: drop the paintTrainOverCaptions loop from newDiagramContainer and the middle of
> the square is the caption in both renders, so nothing changes and the first assertion fails. Paint the
> train inside the tile again instead and it fails the same way, because the caption is in front of the
> tile.

The first sentence is true. The second is false, and so is the test's coverage of the fix as shipped.

**(a) The pixel it samples is not under the caption.** With `size = 120` (`:2066`):

- the caption is bounded at `:2086` to `(0, 80, 120, 30)`, so it covers `y ∈ [80, 110)`;
- the first assertion samples `(size/2, size/2)` = `(60, 60)` at `:2105`, which is 20px above the
  caption's top edge;
- the train icon is centred by `TileOverlay.middle` (`TileOverlay.java:622-626`) at `(60, 60)` because
  `trackCentre` is null, and is `round(120 * ICON_SCALE)` = 91px on a side (`ICON_SCALE = 0.76`,
  `TileOverlay.java:184`), so it spans `[15, 106]` in both axes.

So the icon and the caption overlap only in `y ∈ [80, 106]`, and the assertion samples neither that band
nor anywhere else the caption reaches. (If `trainIcon()` ever returns null the dot is drawn instead -
`diameter = max(6, 120/3)` = 40px centred, spanning `[40, 80]` - which does not reach the caption at all,
so the argument holds either way.) Move the train's drawing back inside `TileOverlay.paint` - the
pre-OB-159 arrangement, which is exactly what Adam reported - and `(60, 60)` still changes between the
two renders, because nothing is painted over it. The assertion passes.

The second assertion at `:2117` samples `(10, 90)`: inside the caption and *outside* the icon's
`x ∈ [15, 106]`. It is unchanged under the mutation too - the caption paints last over that pixel in
both arrangements. So both assertions survive the regression the test is named for.

**(b) The container the diagram actually uses is not the one the test builds.** The test calls
`LayoutGrid.newDiagramContainer()` directly at `:2077`. The only thing that makes the real diagram use it
is `LayoutGrid.java:775`:

```java
container = newDiagramContainer();
```

Nothing in `test/` mentions `newDiagramContainer` except that one line of this test - `grep -rn
"newDiagramContainer" test/` returns exactly one hit. Change `LayoutGrid.java:775` back to
`container = new JPanel()` and every diagram in the application loses the third pass, while this test,
which builds its own container, stays green. This is `TCS-A1`'s shape again: the helper is public and
static *so a test can reach it* (`LayoutGrid.java:669-673` says so), and reaching it is all the test does.

**How to confirm.** Two edits, each on its own, then run `testDiagramLooksRight`:

1. `LayoutGrid.java:775` -> `container = new JPanel();`. Expect: green.
2. Move the body of `TileOverlay.paintTrain` back inside `TileOverlay.paint` behind `if (train)`, and
   delete the `paintTrainOverCaptions` loop from `newDiagramContainer`. Expect: green.

**Smallest fix.** Make the caption cover the pixel the first assertion samples - `caption.setBounds(0,
size / 3, size, size / 3)` puts it over `y ∈ [40, 80)`, so `(60, 60)` is inside both the caption and the
icon - and keep the second sample at an `x` below 15, which it already is. Then add a third render built
through a real `LayoutGrid` rather than through `newDiagramContainer`, so `LayoutGrid.java:775` is on the
path. The first change alone closes (a); (b) needs the second.

## TCS-A3. The OB-164 change to `unlockPath` has no test, and the test that names that branch models the code before it

**Confidence: confirmed by reading.**

OB-164 changed the early release in `executePath` from `setLockedEdgeUnoccupied()` to `setUnoccupied()`
(`Layout.java:5289`), so an edge given up when the tail passes now gives up its lock edges with it. That
made two sites in `unlockPath` wrong, and both were changed. The second is `Layout.java:3176-3183`:

```java
Set<Edge> givenUp = this.clearedEdges.get(loc);

if (givenUp == null || !givenUp.contains(e))
{
    for (Edge lockEdge : e.getLockEdges())
    {
        lockEdge.setLockedEdgeUnoccupied();
    }
}
```

That `if` is new, and nothing exercises it. The test that names this branch is
`test/core/testAutonomyPathValidation.java:802`,
`testUnlockPathReleasesLockEdgesOfASkippedEdge`, and at `:839` it simulates the early release the way
the code did it **before** OB-164:

```java
// What executePath's early unlock does once the train has cleared the first edge: release the
// edge but deliberately not its lock edges
ab.setLockedEdgeUnoccupied();
```

It never puts `ab` into `clearedEdges`. So `givenUp` is null when `unlockPath` reaches `:3176`, the
`if` is taken, the lock edges are released, and `assertFalse(crossing.isOccupied(...))` at `:846`
passes - by the branch that the real runtime, after OB-164, no longer reaches for an edge released early.

Delete the `if` at `:3178` and restore the unconditional loop, and this test passes (it always took the
`givenUp == null` path), and so does
`testAPathDoesNotReleaseAnEdgeItHasAlreadyReleased` at `:720` - that one populates `clearedEdges`
correctly but its `shared` edge has **no lock edges at all** (`:738`), so the loop it re-enables is
empty. The whole suite stays green while every crossing behind a train, in non-atomic mode, gets one
release too many - `Edge.occupancy` is a count (`Edge.java:40`), so the extra release frees a throat
under whatever train claimed it in between. That is RC-A9's fault arriving by the second door, and Adam
runs with non-atomic routes by default.

The floor in `release()` (`Edge.java:418-422`, "never fewer than none") does not rescue it, and it is
worth saying so because it is the first objection a reader will have: the sequence that hurts is the one
RC-A9 already wrote down and `testAPathDoesNotReleaseAnEdgeItHasAlreadyReleased:707-714` spells out -
A releases the edge early, **B** then raises the lock edge's count to 1, and A's late release lowers it
to 0 under B. The count is positive when the extra release lands, so the floor never engages.

The javadoc at `:793-800` is also now false in its own terms: it describes the early unlock as "using
setLockedEdgeUnoccupied - which deliberately leaves the edge's lock edges held until the path
completes", which stopped being true at `Layout.java:5289`.

**How to confirm.** Delete the `if (givenUp == null || !givenUp.contains(e))` wrapper at
`Layout.java:3178-3184`, leaving the loop unconditional. Run `testAutonomyPathValidation` and
`testTrainTailClearsEdges`. Expect: green.

**Smallest fix.** In `testUnlockPathReleasesLockEdgesOfASkippedEdge`, replace `ab.setLockedEdgeUnoccupied()`
at `:839` with what `executePath` now does - `ab.setUnoccupied()` plus the two lines that record it in
`clearedEdges`, which `testAPathDoesNotReleaseAnEdgeItHasAlreadyReleased:756-764` already writes out - and
then assert the crossing's count is what one release leaves, not merely that it is not occupied. The
cheapest form of that assertion is the one the sibling uses: have a second edge hold the crossing, and
assert it is still held after `unlockPath`.

---

# B - Medium

## TCS-B1. Four classes restore the layout preference while their `TrainControlUI` is still alive

**Confidence: the shape is confirmed by reading; whether these particular windows write the folder needs
execution.** Recorded at B rather than A for that reason. If the check below shows a write, it is an A.

`test/ui/testEveryLanguageFits.java:40-47` records what this shape cost:

> **This test damaged the operator's railway once, and the guard below is why it may run again.** The
> first version opened and closed a sandbox around each of the eight windows; something a disposed window
> had already scheduled then wrote after the sandbox had put the layout preference back, and the autonomy
> configuration of `cs2_sample_layout` - which is Adam's real railway and is not recoverable - was
> rebuilt against the fixture diagram [...]

Four classes have exactly that shape today, all four with the same copied `build()` helper - open the
sandbox, construct the window, **close the sandbox in the `finally`**, hand the live window back:

- `test/ui/testStagingOutcomeMessages.java:73-89` (open `:75`, close `:85`)
- `test/ui/testTimetableColumnHeadings.java:73-89` (open `:75`, close `:85`)
- `test/ui/testRoutingRuleTooltips.java:96-112` (open `:98`, close `:108`)
- `test/ui/testLocMappingPages.java:202-219` (open `:207`, close `:215`)

In all four the whole test body then runs with the preference already pointing back at Adam's railway,
and in none of them is the window ever disposed - so anything it schedules lands after the restore with
certainty, which is the one part of the recorded mechanism that here is not a maybe.

Two more never dispose the window either, though they at least hold the sandbox open for the class:
`test/ui/testDiagramLooksRight.java:65-82` and `test/ui/testLocIconCrop.java:40/167` and `:269/342`.

The first three of the four were written on 2026-08-30 in `fd31d2b2`; `testEveryLanguageFits`, which
records the incident and the rule, landed later the same day in `7fc1961b`. The rule was never swept back
over its siblings - which is the project's own most-repeated mistake, recorded in
`docs/reviews/README.md` under "When you fix a call site, grep for its twins".

**And the guard cannot see it.** `testNoTestOpensTheOperatorsRailway`
(`test/regression/testSwitchingToACentralStationLayout.java:433`) tests one thing about the window half,
at `:466-482`: that `LayoutSandbox.open()` appears at a lower character index than `new TrainControlUI()`.
All four classes satisfy that. Nothing checks that the sandbox is still open while the window is used, or
that the window is disposed at all.

**One thing found while checking this, which is NOT a test defect and needs Adam rather than a fix.**
`cs2_sample_layout/config/autonomy/configuration-Main.json` is modified in the working tree against
`e4c94ac9`, last written 2026-08-31 01:59:53. Most of it is plainly Adam's own hands-on work and not
damage: it carries locomotives called `MT-233 Test Loc 2` and `MT-x233 Test Loc`, which are his - he
names the first of them in his own MT-233 note in `docs/manual-tests/tests.md:12602` ("added MT-233 Test
Loc ... It was added via contorl+V on the track diagram viewer"), and `pathPreference` and
`atomicRoutes` differ in the direction a person testing would move them.

One part of the diff is not obviously his, and it is worth a question rather than a claim: the
`excludedLocs` list at `"1 - Main:14,3"` - six names, `EA 3005 DSB`, `ER 2035 DSB`, `MF 5028 DSB`,
`MY 1150 DSB`, `MZ 1425 DSB`, `SP45-204` - is gone, along with that square's `priority`.
`testEveryLanguageFits.java:40-47` records the 2026-08-30 incident as having lost "facings, placements,
priorities and **an exclusion list**", singular. It may be that this is the same list and it was never
put back; it may equally be that Adam cleared it himself while testing. **I did not touch the folder and
nothing here should be reverted on my say-so** - the two readings are told apart by asking him, and by
`git log -p -- cs2_sample_layout/config/autonomy/configuration-Main.json`, not by a reviewer guessing.

**How to confirm the finding itself.** Fingerprint `cs2_sample_layout` (size + mtime per file, which is
what `testEveryLanguageFits.fingerprint` does), run `ant test` restricted to `testStagingOutcomeMessages`
alone, fingerprint again. Repeat for the other three. Do it on a copy of the folder if there is any way
to arrange that. If any run moves a byte, this is an A and the class must not run again until it is
fixed.

**Smallest fix.** In each of the four, hold the sandbox for the class in `@BeforeClass`/`@AfterClass` -
the pattern `testDiagramLooksRight:65-82` uses - and dispose the window in `@AfterClass`. And extend
`testNoTestOpensTheOperatorsRailway` with a second rule: a file that contains `new TrainControlUI()` must
also contain `.dispose()`.

## TCS-B2. `testEveryLanguageFits`'s two safety assertions are each satisfied by nothing

**Confidence: confirmed by reading.**

The class carries two guards of its own, both added because it had already failed silently once. Both can
be satisfied by an absence.

**The locale guard.** `test/ui/testEveryLanguageFits.java:173`:

```java
assertFalse(sameBytes(new File(OUT, "window-en.png"), new File(OUT, "window-de.png")),
    "the English and German windows are byte-identical, so the locale is not reaching the "
    + "text and all eight measurements are of the same language");
```

`sameBytes` is at `:449-455` and its first line is `if (!a.exists() || !b.exists()) return false;`. So
"the two windows differ" is proven by "at least one screenshot was never written" - and `shoot()` at
`:438-445` swallows `IOException` on purpose ("A missing picture is not a reason to fail the measurement
it illustrates"). Make `shoot` a no-op, or run where `java.io.tmpdir` is not writable so `OUT.mkdirs()`
at `:93` fails, and the guard passes having compared nothing.

**The railway guard.** `:128`:

```java
assertEquals(fingerprint(LIVE), railwayBefore, ...)
```

`fingerprint` at `:465-467` returns `""` when its argument is not a directory. `LIVE` is
`new File("cs2_sample_layout")` at `:67`, resolved against the process working directory. Run this class
from anywhere but the project root and both sides are `""`, and the guard on the irreplaceable folder is
an assertion that the empty string equals the empty string. Nothing asserts that `railwayBefore` is
non-empty. This is `TST-C11`'s pattern with a much higher stake than any of its instances.

Neither is reachable in the battery as it stands today - ant's `basedir` is `.` and
`cs2_sample_layout/config` exists - so this is a trap for the next reader rather than a live fault. It is
worth closing anyway, because the class's whole claim to being allowed to run at all rests on the second
one.

**How to confirm.** No run needed for the reading. To see it: `assertFalse(sameBytes(a, b))` with both
files deleted immediately before the assertion passes; so does the whole class with
`assertEquals(fingerprint(LIVE), railwayBefore)` evaluated from a different working directory.

**Smallest fix.** Two lines. Before `:173`, assert both PNGs exist and are non-empty. Before `:128`,
`assertFalse(railwayBefore.isEmpty(), "the railway guard is looking at nothing - wrong working
directory")`.

## TCS-B3. `Outcome.NO_HOMES` is asserted by no test in the suite

**Confidence: confirmed by reading.**

`HomeStaging.Outcome` has eight values. A sweep of `test/` for `Outcome.<NAME>` finds assertions on six -
`ALREADY_HOME`, `READY`, `NO_LOCOMOTIVES`, `IMPOSSIBLE`, `NO_PLAN_FOUND` and `POSITION_AMBIGUOUS` -
across `testHomeStaging` and `testReturnHomeOnRealLayout`. Two are never asserted anywhere.

`LOCOMOTIVES_RUNNING` is the harmless one: nothing in `HomeStaging` produces it - it is written by
`TrainControlUI` at `:19009`, `:20063`, `:20323`, `:20634` and `:20657` when the operator presses the
button at the wrong moment - and `testStagingOutcomeMessages` does reach it, through its sweep over
`Outcome.values()`.

`NO_HOMES` is not. It appears nowhere in `test/` at all, and it is produced by the planner.

It is produced at `HomeStaging.java:709`:

```java
if (!anyHomed) return Outcome.NO_HOMES;
```

Delete that line and the method falls through to `if (misplaced(this.start) == 0) return
Outcome.ALREADY_HOME;` at `:711` - and with no homes at all, `misplaced` is 0 by definition
(`:1619-1631`; `if (home != null && !atHome(...)) count++` only counts locomotives that have one), so a layout where nothing has a home reports
"every locomotive is already home". The operator is told the fleet is staged when the feature has
nothing to work with. Nothing in the suite notices.

This is worth saying now specifically because `NO_HOMES` is the outcome MT-165's defect produced -
the commit message for `66c96736` records "Measured: 0 homes and NO_HOMES on a split square, 1 and
ALREADY_HOME on a plain one" - so the two outcomes this branch chooses between are exactly the two that
were confused last night.

**How to confirm.** Delete `HomeStaging.java:709`. Run `testHomeStaging`, `testReturnHomeOnRealLayout`,
`testStagingSkipsALegWithNoSpeed` and `testStagingOutcomeMessages`. Expect: green.

**Smallest fix.** The reachable case is the launch-pad one, which
`testALaunchPadPositionalHomeDoesNotBlockTheFleet` (`testHomeStaging.java:2791`) already builds most of:
a single locomotive whose only home is a positional claim on a launch pad it has since left is stripped
by `HomeStaging.snapshot:190-205`, leaving `homes` empty and `start` non-empty. Assert `triage()` is
`NO_HOMES` there.

## TCS-B4. `testALocomotiveInOnePlaceIsNotReported` - a control that asserts nothing about its own fixture

**Confidence: confirmed by reading.**

`test/core/testAutonomyDiagramSession.java:4546`. Its javadoc names its own job:

> The precondition that keeps the test above honest: a check that fired on every placement would satisfy
> it and make the list useless.

Its body places one locomotive at `:4559` and then asserts, inside a loop over `session.check()`, that no
finding is a `DUPLICATE_LOCOMOTIVE`. There is no assertion that the placement took. Make
`AutonomySession.placeLocomotive` a no-op and the test passes: nothing is placed, `check()` reports no
duplicate, and the control that is supposed to prove the check discriminates has been satisfied by a
layout with no locomotive on it.

It is the same shape as the sibling it guards - `testALocomotiveInTwoPlacesIsReportedAsAnError` at `:4488`
writes its two placements directly with `setPointProperty` and does assert them indirectly through
`reported`. This one goes through `placeLocomotive` and asserts nothing.

**How to confirm.** Make `placeLocomotive(TileKey, String)` return immediately. Run
`testAutonomyDiagramSession` and expect `testALocomotiveInOnePlaceIsNotReported` green (several of its
siblings will fail, which is the point - this one will not).

**Smallest fix.** One line after `:4559`, the mirror of the assertion the neighbouring "taken off" test
makes at `:4472`: `assertNotNull(session.getPointProperty(tile, AutonomyBuilder.LOCOMOTIVE), "the fixture
did not take - nothing was placed, so 'not reported as a duplicate' says nothing")`.

---

# C - Low

## TCS-C1. `testATrainIsNotPlannedIntoItsOwnDetectionSection` closes on `!= READY`

`test/core/testHomeStaging.java:3618`:

```java
assertNotEquals(plan.getOutcome(), HomeStaging.Outcome.READY, ...)
```

Seven of the eight outcomes satisfy that, including `NO_LOCOMOTIVES`, `NO_HOMES` and `ALREADY_HOME` -
the three that mean the fixture fell apart rather than that the rule fired. `TA-C1` made this exact point
about `!= IMPOSSIBLE` elsewhere. The preconditions here are stronger than that finding's were -
`getPossiblePaths(...).isEmpty()` at `:3612` cannot hold unless the locomotive is placed - so this is a
weak assertion rather than an empty one.

What makes it the weakest of the three: the same class uses `assertNotEquals(..., READY)` twice more, at
`:3339` and `:3455`, and **both of those are followed by a control** - "The control: one locomotive
moved, one graph, one answer changed" at `:3343`, and the sensor-sibling pair at `:3455`. `:3618` has
none. Nothing here runs the same fixture with the feedback clear and asserts the plan is `READY` then,
so "the planner refuses" is not distinguished from "this fixture can never produce a plan".

Two lines close it: assert the specific outcome (or
`assertTrue(outcome == IMPOSSIBLE || outcome == NO_PLAN_FOUND)`, which still excludes every degenerate
one), and add the control - the same layout with `setFeedbackState(sensor, false)`, expecting `READY`.

## TCS-C2. A home on a split square is triaged and never planned or executed

MT-165 changed `Layout.claimHome` to allow a positional home on a square emitted as several Points, and
added `HomeStaging.atHome` (`HomeStaging.java:1589`) so the copies count as one place. The rewritten
test, `testHomeStaging.java:1980`, asserts the claim is made (`:2028`) and that `triage()` answers
`ALREADY_HOME` with the train on the far copy (`:2048`). Both of those are right, and I checked the third
assertion carefully enough to be sure it can fail: the fixture puts `LOC_A` on `HS A` as well as on the
watched square, but `Point.setLocomotive` sweeps through `Layout.clearLocomotiveExcept`
(`Point.java:457`), so only one placement survives and the home really is the split square.

What is not covered is the case beyond triage. No test ever builds a `Plan` whose *goal* is a split
square, and none applies one. `atHome` is used in four more places - `plan()` at `:393`, the two
pairwise scans at `:477` and `:545`, and `search()` at `:739`, besides `misplaced()` at `:1627` - and the only one
under test is `misplaced`, through `triage`. A plan that has to route a train back onto a block, past
another train, is exactly the arrangement the planner is for, and it is asserted nowhere.

Worth adding: a fixture whose two copies of a platform sit at opposite ends of the `ring` graph, a second
locomotive in the way, and `applyPlan` + `assertEveryoneHome`. Note that `assertEveryoneHome`
(`testHomeStaging.java:208`) compares Points exactly, so it will need `atHome`'s rule too - which is
itself worth knowing.

## TCS-C3. `testRouteTilePlacement` throws instead of reporting, on its own failure path

`test/core/testRouteTilePlacement.java:288`:

```java
for (String one : refused) bare.add(one.substring(0, one.indexOf(" [")));
```

`neighbourhood` (`:306-343`) returns `"(page not found)"` when the page cannot be resolved, which
contains no `" ["`, so `indexOf` returns -1 and `substring(0, -1)` throws
`StringIndexOutOfBoundsException`. It only bites when the test is already failing - but that is the run
where the message matters, and a `StringIndexOutOfBoundsException` in the test harness tells the reader
nothing about which square on Adam's railway was refused.

Same file, `:275-284`: the comment says the list holds "the one square known to trip them" and
`known` is `Collections.emptyList()`. The comment is left over from an earlier state and now contradicts
the code beside it - the failure message at `:294` repeats the claim.

## TCS-C4. The routing tooltip is refreshed by reflection, never through the control

`test/ui/testRoutingRuleTooltips.java:79` drives `refreshRoutingLogicTooltip` directly:

```java
refresh.invoke(ui);
```

The claim it carries is true as far as it goes - I checked, and reverting the tooltip to the general
sentence fails `:85`. What it does not reach is the wiring. `refreshRoutingLogicTooltip` is called from
three places in `TrainControlUI` (`:8142`, `:8201`, `:8251`); remove all three and the control never
updates its tooltip while this test stays green. Lower than `TCS-A1` and `TCS-A2` only because there are
three call sites rather than one, so an accidental deletion of all of them is less likely.

The fix is one line: drop the reflection and let the dropdown's own listener fire, by posting the
selection change on the EDT and reading the tooltip afterwards.

## TCS-C5. Three more vacuous-on-empty loops, outside `TST-C8`'s list

`TST-C8` enumerated fifteen `@Test` methods whose every assertion sits inside a loop that can run zero
times. A fresh scan over all 1,204 methods found 154 with no top-level assertion; almost all are loops
over fixture constants or `enum.values()`, which cannot be empty. Three that iterate production output
and are not on `TST-C8`'s list:

- `test/core/testAutonomyDiagramReversal.java:248`, `testTheMarkItselfIsNeverEmitted` - loops over
  `built.getJSONArray("points")`. **Mutation:** `AutonomyBuilder.build()` emits no points.
- `test/core/testAutonomyDiagramReversal.java:704`, `testASplitCopyNeverCollidesWithAnAuthoredName` -
  same array, asserting uniqueness. Same mutation; also passes with one point.
- `test/ui/testRenderingCost.java:344`, `testEveryTileOfOneAccessoryStaysRegistered` - loops over
  `candidate.getAll()`.

All three are rescued today by siblings that would catch the same mutation, which is why they are C and
not higher. One `assertFalse(...isEmpty())` each closes them.

## TCS-C6. The window-icon guard cannot see the construction OB-105 was about

**Confidence: confirmed by reading.** Raised by a second reader over `test/regression`, and re-checked
here against the four files it names.

`test/regression/testEveryWindowWearsTheIcon.java` has two tests and both are blind to
`JOptionPane.createDialog(...)`, which is how three of this project's dialogs are built:

- `testEveryWindowAsksForTheIcon:53` examines a file only if `isAWindow(body)` - `WINDOWS` at `:35-40`
  is `extends J{Frame,Dialog}`, `extends PositionAwareJFrame`, `new JDialog(`.
- `testEveryInlineDialogAsksForItself:156` scans only for `new JDialog(` / `new javax.swing.JDialog(`
  (`:167`).

`src/org/traincontrol/marklin/MarklinControlStation.java` matches **none** of those markers - it is
`public class MarklinControlStation implements ViewListener, ModelListener` - so neither test ever reads
it. It builds a dialog at `:3627` with `optionPane.createDialog(...)` and ices it at `:3643`, and the
comment above that call is OB-105 itself: "This dialog has no owner window - it is the FIRST thing shown,
before the main window exists". Delete `:3643` and all three tests in the class stay green while the
first thing a new user sees goes back to the Java coffee cup.

**And there are two live instances already.** `src/org/traincontrol/gui/AutonomyEditorPanel.java:3019`
and `:6428` both build a dialog with `pane.createDialog(...)` and never call `applyWindowIcon`. That file
passes `testEveryWindowAsksForTheIcon` because it contains one `new javax.swing.JDialog(` at `:4136` with
an icon call at `:4142`, and the test's check is per **file**, not per construction. So the guard reports
the file as dressed while two of its three dialogs are not.

Recorded at C because the regression is cosmetic - a window with the wrong icon - which is where the
README's table puts it. A reader who thinks "the first screen a new user sees" is worth more than that is
welcome to raise it; the facts above are the same either way.

**How to confirm.** Delete `MarklinControlStation.java:3643` and run `testEveryWindowWearsTheIcon`.
Expect: green.

**Smallest fix.** Add `"createDialog("` to `WINDOWS` at `:39` and to the needles at `:167`. That closes
the blind spot and fails immediately on the two `AutonomyEditorPanel` dialogs, which is the point.

## TCS-C7. Three negative controls with no floor

Each of these asserts that something did **not** happen, and each is satisfied by nothing having
happened at all - which for a negative control is the failure mode, because green is what it says either
way. All three were re-checked here.

- `test/regression/testStationBlockedByAnotherPoint.java:148`, `testNoRestrictionAddsNoLocks`. Its only
  assertion (`:169`) is three levels deep: `for (edges)` -> `if (!"Bahnsteig".equals(end)) continue` ->
  `if (locks == null) continue`. If `session.buildConfigurationForInspection()` produced no edges, or
  none into the platform, it passes having examined nothing. **Its own sibling has the missing line** -
  `assertTrue(intoThePlatform > 0, "no edge arrives at the platform, so nothing below tests anything")`
  at `:111`. Count the matches and copy it.
- `test/regression/testEditorSwitchClearsPageState.java:238`,
  `testTheMountedFieldsAreTheOnesListed`. The regex `(autonomy[A-Za-z]+) = (?!null)` at `:243` produces
  a set; the set has the known names removed; the assertion is that what remains prints as `"[]"`. Zero
  matches - a renamed method, a reformatted assignment - gives an empty set and passes. Add
  `assertTrue(assigned.size() >= MUST_BE_UNMOUNTED.length, ...)` **before** `removeAll` at `:252`.
- `test/regression/testTriggerWaitsSayNothing.java:97`, `testWaitingOnATriggerSensorIsSilent`. Five
  monitor threads are started at `:126-129` and the sole assertion at `:151` is that no log line mentions
  the wait. Nothing asserts the threads are still waiting: if `waitForOccupiedFeedback` returned or threw
  immediately, there is no wait to be silent about and the test passes. Its near-twin
  `testStuckTrainAdvisory.java:200` carries exactly that precondition. One line before `:133`:
  `for (Thread m : monitors) assertTrue(m.isAlive(), "the waits ended, so silence proves nothing")`.

## TCS-C8. A `MUTATION` line that names the wrong guard

`test/regression/testARouteDoesNotThrowSwitchesUnderATrain.java:99`:

> MUTATION: removing the `accessoryHeldByAutonomy()` refusal from `MarklinRoute.execRoute` fails this
> test on its first assertion.

It does not. `conflict` is set at `MarklinRoute.java:576` and has exactly two readers - `:578`
(`if (conflict != null && auto)`, logging) and `:625` (`boolean skipAccessories = auto && conflict !=
null;`). Both are gated on `auto`, and the test fires the route with `execRoute(false)` at `:157`, which
reaches `execRoute(auto, 1, false)` with `auto == false`. What actually refuses the turnout on this path
is the per-command `heldReason(rc)` at `MarklinRoute.java:650` - which is the mutation the *next* test's
javadoc names, at `:243`.

**This is a documentation defect and not a coverage hole**, which is why it is C and not with `TCS-A1`.
The `auto && conflict != null` branch is genuinely covered, by `testTheStopInARefusedRouteStillRuns`
(`:819`) and `testAnAutomaticRouteWithNoConflictStillSetsItsAccessory` (`:939`), both of which use
`execRoute(true)`; and the file already knows the distinction, saying at `:873` that "with `auto` false
the whole rule is [off]". The fix is to correct the sentence at `:99` to name `heldReason(rc)`.

---

# D - Checked and sound, or withdrawn

## TCS-D1. Clean checks, with receipts

These were examined against the production code they name and the claim held. Listed because a reader
calibrating how much of the suite this pass actually looked at needs the denominator, not just the
numerator.

**Verified by reading the named mutation against the named production line:**

- `test/core/testALocomotiveDoesNotEvictItself.java:37` - "dropping the self test evicts the train and
  both assertions fail." True. `MarklinLocomotive.isSimultaneousMultiUnitCompatible` ends
  `return !this.hasEquivalentAddress((MarklinLocomotive) l);` (`:1114`), and `hasEquivalentAddress`
  (`:471-476`) compares address and decoder type - so a locomotive really is incompatible with itself,
  and removing `if (l.equals(p.getCurrentLocomotive())) continue;` at `Layout.java:5513` evicts it.
  `equals` is identity (`MarklinLocomotive.java:1016-1019`), so the guard cannot be over-broad.
- `test/core/testHomeStaging.java:1979`, `testTheHomeRuleReachesTheDoorsHomesActuallyComeFrom` - all
  three named mutations hold, including the new third one. I traced the fixture carefully because
  `blockOfTwoWatching` also puts `LOC_A` on `HS A` (`:3102`), which looks like a locomotive in two
  places: `Point.setLocomotive` sweeps first (`Point.java:457`), so it is not.
  `HomeStaging.snapshot` copies `layout.getHomeStations()` rather than re-deriving (`:188`), so the home
  stays on the near copy while the train stands on the far one, which is what makes the third assertion
  ask anything. `HS W2` is not a launch pad (both `HS W1 <-> HS W2` edges exist), so the launch-pad strip
  at `:190-205` does not remove the home first.
- `test/core/testLockEdgesSurviveTheFile.java:33` - both halves hold, and the second one genuinely tests
  enforcement rather than population: `rival.setOccupied()` raises `approach`'s count through
  `Edge.setOccupied` -> lock edges (`Edge.java:451-461`), so `isPathClear` refuses for the lock.
  The control at `:126` is real.
- `test/ui/testStagingOutcomeMessages.java:22` - true. Six explicit cases at
  `TrainControlUI.java:20363-20390`, `NO_PLAN_FOUND` served by `default` at `:20391`; deleting any case
  collides its message with `NO_PLAN_FOUND`'s, which the `seen` map catches. `namesOf` (`:20402`)
  handles a null list, so the two blocked-carrying outcomes are covered too.
- `test/ui/testTimetableColumnHeadings.java:24` - true. The call is in the constructor
  (`TrainControlUI.java:597`); without it the designer's four columns and four rows stand and both `:44`
  and `:61` fail.
- `test/core/testTheStationGoingAwayDoesNotJamSwitching.java:70` and `:132` - both hold; the second fails
  at the latch rather than hanging, as it claims, because the wait is on the latch and not on the pool.
- `test/regression/testEveryTestIsInTheBattery.java:146` - true, and the class is the strongest gate in
  the suite: comments are stripped (`:259-282`), the exclusion list is pinned at one entry (`:107`), the
  excused name must be a real file (`:119`), and the method scan has a floor of 500 (`:215`).
- `test/regression/testSwitchingToACentralStationLayout.java:433` - the window half is a hard rule with a
  floor (`checked >= 5`, `:486`); the model half is a ratchet pinned by name as well as by count
  (`:520-546`), which closes `VAL-C8`. What it does not cover is `TCS-B1`.

**Examined and found sound without a MUTATION line to check:**

- `test/core/testStationPriorityDistribution.java` - the whole class. Randomised without a seed, which it
  states and cannot fix (`pickPath` shuffles through a `Random` the test cannot reach), but the floors are
  real: `sample()` refuses an empty count (`:391`), the blind-rule test needs 40 of 400 on each of three
  stations (`:206`), and `testTheTwoRandomRulesAreNotTheSameRule` (`:221`) is a genuine control - without
  it both other tests pass if the two rules are one rule. The fixture asserts reachability, activity,
  train length and the flattening of every other prioritised station before it counts anything
  (`:259-320`). This is the model for how a statistical test should be written here.
- `test/core/testAutonomyPathValidation.java:189`,
  `testNonAtomicGivesBackTheLocksOfTrackThePassedTrainHasCleared` - the four-edge run is the right fix for
  the two-edge version that "passed for the wrong reason", and the `stillRunning` flag does separate
  early release from release-at-the-end. It is a 25ms poll against a microsecond window, so it is a race
  in principle; in practice the mutation leaves the crossing held for the rest of a multi-second run, so
  the poll cannot miss it. Not a finding.
- `test/support/LayoutSandbox.java` - restores the preference to unset when it was unset (`:84-91`),
  which is the case that matters. `open()` silently produces an empty temp folder if `test/test_layout` is
  missing (`:64`), which is `TST-C11`'s shape but harmless: the preference still points away from the real
  railway, which is the whole job.

**Deliberate skips, checked and correct:** `testAutonomyDiagramSession.java:2420`,
`@Test(enabled = false)` on `testALegacyImportDoesNotReExcludeAPageTheOperatorTurnedBackOn`. Its comment
says it encodes what the javadoc promises rather than what the code does, cites `TST-B15` as open, and
says enabling it is the reproduction. That is a disabled test used correctly.

## TCS-D2. `build.xml` reconciles exactly against disk

Checked because `DD-A2` found thirty-five classes missing from the battery once and six more the same
evening. Today: 140 `<test-one-class/>` entries, no duplicates, none naming a file that does not exist;
141 `.java` files under `test/core`, `test/ui` and `test/regression`; the one difference is
`testAutoDetect`, which is `testEveryTestIsInTheBattery`'s single documented exclusion. `test/support`
holds three files and no `@Test`. The gate and the reality agree.

## TCS-D3. Withdrawn: "the multi-unit sweep's eviction half is untested"

Raised, then refuted. `Layout.sanitizeMultiUnits` appears in `test/` only in
`testALocomotiveDoesNotEvictItself`, which tests the new guard and not the sweep - so it looked as though
making the whole method a no-op would leave the suite green, and that would have been a serious hole
given the method's job. It would not. `test/core/testAutoLayout.java:126-215` drives eleven placements
through `Layout.moveLocomotive`, which calls the sweep at `Layout.java:5617` before placing, and asserts
after each one that the conflicting locomotive has been taken off - `mu_1_2`, `mu_3_2`, `l2`, `l3`, the
Central-Station multi-unit and the duplicated-address pair are all covered. The eviction half is well
guarded; it is only the new self-exemption that has a single test, and that test is sound (`TCS-D1`).

Recorded rather than deleted, per the README: the reason it looked like a hole is that the coverage lives
in a class whose name says nothing about multi-units, and a grep for the method name does not find it.

---

## What this pass did not look at, and where it is weak

**It did not run anything, and three of its findings would take minutes to settle by running.** `TCS-A1`,
`TCS-A2` and `TCS-A3` are each one production edit and one class. `TCS-B1` needs a fingerprint either
side of a run and is the one that should be done first, because it is about Adam's data rather than about
a test.

**Coverage of the suite is uneven, and deliberately so.** Everything changed since 2026-08-28 was read
line by line - `testHomeStaging`'s new and rewritten methods, `testALocomotiveDoesNotEvictItself`,
`testARenameReachesTheTimetableOnScreen`, `testTheStationGoingAwayDoesNotJamSwitching`,
`testLockEdgesSurviveTheFile`, `testEveryLanguageFits`, `testStagingSkipsALegWithNoSpeed`,
`testStagingOutcomeMessages`, `testTimetableColumnHeadings`, `testRoutingRuleTooltips`,
`testRouteTilePlacement`, `testStationPriorityDistribution`, and the OB-159 and OB-164 additions to
`testDiagramLooksRight`, `testAutonomyDiagramMonitor` and `testAutonomyPathValidation`. The other ~120
classes got the mechanical scans plus two delegated `MUTATION` audits, one over `test/ui` and one over
`test/regression`.

**How much of the `MUTATION` convention was actually checked.** 200 claims exist. About 45 were checked
here directly; the `test/ui` audit covered 20 of its 57 and returned only the claim already found here
(`TCS-A1`), independently and from the same two line numbers; the `test/regression` audit covered 61 of
its 88. Everything either audit reported was re-verified here before being written up, and `TCS-C6`,
`TCS-C7` and `TCS-C8` are what survived that.

**What that leaves unexamined, by name.** `test/ui`: `testDiagramLooksRight`'s other 16 claims,
`testStationLabelDrag`'s 8, `testTheWaitMarkIsAnHourglass`'s 6, `testRouteEditorLocked`'s 6,
`testLocIconCrop`'s 4, `testLocMappingPages`'s 4, `testStationLabelPrefill`'s 4, and one each in
`testSidebarIcons`, `testRouteEditorShading` and `testDiagramExport`. `test/regression`, 27 claims across
12 files: `testPageIdsAreDurable` (8), `testSwitchingToACentralStationLayout` (5),
`testDiagramDrawingSettings` (5), `testTheCheckerAgreesWithTheBuild` (5),
`testAutonomyStoreSettingsMatrix` (4), `testCancelRestoresPlacements` (3),
`testARunSurvivesADiagramEdit` (2), `testRenameRoundTripThroughTheUIPath` (2), and one each in
`testARunSurvivesAPageRename`, `testAMovedTileCarriesItsSetup`, `testDiagramShiftKeepsSetup` and
`testAutonomyTileMove`. On the hit rate here - four false or misdirected claims in about 105 checked -
the remaining 95 probably hide two or three more.

**Reported to me and NOT re-checked, so not written up as findings.** These came from the
`test/regression` audit, are plausible on their face, and I ran out of budget before opening them. They
are recorded so somebody can pick them up rather than rediscover them, and they carry that reader's
confidence and not mine:

- `testARouteDoesNotThrowSwitchesUnderATrain.java:1104`, `testAMeasuredTrainKeepsItsTurnoutsRefused` -
  the claimed mutation ("restoring the escape - release when the last edge traversed had no length")
  may never fire on this fixture, because the unmeasured edge is the *last* edge and the clearing block
  at `Layout.java:5185-5253` does not run for the final leg. Proposed fix: a fourth leg, lengths
  `100, 100, 0, 100`. That reader called it "false under the phrasing the javadoc uses, true under one
  other phrasing" and asked for execution to settle it, which is the right call.
- Four floorless loops in `testTheGoldenLayoutHoldsTogether` (`:180`, `:251`, `:278`, `:347`) - a
  judgement call, since that class deliberately refuses to pin counts on Adam's live railway.
- `testEditorSurfaceRules.java:688`, `:352`, `:399` - three source-scan tests whose assertions are all
  behind a `contains(...)` gate with no count of how many sites were reached.
- `testEveryWindowWearsTheIcon`'s `REACH = 900` proximity rule (`:194`) accepts any `applyWindowIcon`
  within 900 characters, so a second `new JDialog(` inserted just before an iced one borrows its call.
- `testErrorsStopTheSetupRunning.java:402` - the mutation is caught, but by the fourth assertion rather
  than by the last one the javadoc names. A stale ordinal.
- All five `@Test`s in `testTheRoutingChoiceSurvivesTheUpgrade` throw `SkipException` when headless
  (`:114`, `:266`, `:367`, `:473`, `:567`), so on a headless runner the class asserts nothing and reports
  no failures. That is `TST-C14`'s pattern, recorded here only because it is a whole class rather than a
  method.

**Three specific things I looked for and did not find, which may mean they are not there or may mean I
looked wrongly:**

- A test asserting on an exact route where the shortest route is not unique. `getNeighbors` shuffles, and
  the file headers that permit exact-route assertions say when uniqueness holds. I read the headers and
  did not re-derive the uniqueness, so an exact-route assertion on a topology that has since gained a
  second shortest path would have got past me.
- Duplicated tests. I checked `testDiagramLooksRight:1019` against `testAutonomyDiagramMonitor:1190`,
  which make similar claims about `liftAboveLabels` from opposite sides and are not duplicates. I did not
  sweep the suite for near-identical bodies.
- Timing races. `testNonAtomicGivesBackTheLocksOfTrackThePassedTrainHasCleared` and
  `testTheStationGoingAwayDoesNotJamSwitching` both poll, and both are sound as far as reading goes; a
  flakiness question is not answerable by reading, and the only honest thing to say is that I did not
  measure them.

**One thing this pass got wrong on the way, recorded because it is the useful part.** I spent a while
convinced that `testTheHomeRuleReachesTheDoorsHomesActuallyComeFrom` could not pass at all, because its
fixture puts `LOC_A` on `HS A` and the test then puts the same locomotive on `HS W2`. That would have
been a locomotive in two places, `misplaced` would never reach zero, and the third assertion would fail.
It does pass, because `Point.setLocomotive` sweeps every other copy first - a rule added for a different
reason entirely, three layers below where the test is looking. The finding I nearly wrote would have been
confidently wrong, and the thing that stopped it was opening `Point.setLocomotive` rather than reasoning
about what the test "obviously" did. That is the README's "verify the layer you are actually claiming
about", met the hard way.
