# Night review - the autonomy-diagram round to 2026-08-23

**Status:** open

**Prefix for citing this document: `NR`.** Severities use the house words from
[README.md](README.md) - High is an **A**, Medium a **B**, Low a **C** - but the findings are numbered
`NR-1`, `NR-2`, ... in rank order rather than lettered, because the ranking is the point of this pass.

**Reviewed at `4e7daabe`** (`autonomy-diagram-r0` HEAD), covering `git diff 4ba329ad..HEAD` restricted
to `src/` and `test/`. Twenty-three commits, thirty-one files, about 2850 lines added.

**Nothing was changed by this pass.** This document is the only file it wrote. No source or test file
was touched, nothing was compiled, and the test battery was not run.

**Method.** The diff read hunk by hunk against the surrounding code, then five follow-through reads
where a change reaches past its own file: `placementChanged` down through
`rebuildRunningLayoutFromSetup` into `AutonomyViewerPanel.load` and `AutonomySession.captureFromLayout`;
`mayCarryACaption` against the `isStraightThrough` it replaced and against
`LayoutDiagramComponent.isClickable`; the `LayoutGrid` static map against every `new LayoutGrid(...)`
call site; `facingChoices` against `AutonomyBuilder.facingOf` and `StationIndex.facingsAt`; and each new
source-scanning test against the file it scans, asking what edit would make it fail.

**Nine findings. One of them is worth reading tonight** - `NR-1` says the change that publishes a
placement to the viewer also reverts it. The rest are a functional regression in the caption menu, a
memory leak, a guard that reaches one file of two, and five small ones.

**Most of the round is right, and section E says which parts were checked and left alone** - the
through-case of the run line, the facing rule, the `numOrientations` de-duplication, the reconcile
sweep, the shortcut moves, and the port table test. An honest short list of what held is more useful
than a long list of doubts.

---

## Findings, ranked by expected harm

| # | Finding | Severity | Confidence |
|---|---------|----------|------------|
| NR-1 | `placementChanged` now rebuilds the running layout unconditionally, and that rebuild captures from the STALE running layout - so the facing, placement or removal that provoked it is written back to what it was | High | Medium-high |
| NR-2 | `mayCarryACaption` uses `isClickable()`, which excludes sensors and uncouplers. A station caption can no longer be put on the platform road - the square the comment above it recommends | Medium | High |
| NR-3 | `LayoutGrid.LIVE` is a `WeakHashMap` whose value reaches its key, so no entry is ever collected. Every grid ever built - including one per diagram export - is retained for the life of the process | Medium | High |
| NR-4 | `testTheFacingIsWrittenFromOnePlaceAndThatPlaceRedraws` scans one file, and the second writer of a facing is in another - `LayoutRightclickAutonomyMenu`, which is the surface OB-039 was reported from | Medium | High |
| NR-5 | `AutonomyMenu.guardWhileEditing` appends its separator to the END of the menu instead of putting it under the item it belongs to | Low | High |
| NR-6 | `applyLength` dropped its `NumberFormatException` guard. Eleven digits pass the filter and throw; the user gets an untranslated Java message, and `errorNegativeLength` is now orphaned in eight bundles | Low | High |
| NR-7 | Four comments and javadocs now state the opposite of the code they sit on, including the one that explains why `placementChanged` is safe | Low | High |
| NR-8 | The store-collections matrix test counts a name mentioned in a COMMENT as the site handling it, and this change added exactly such a comment | Low | High |
| NR-9 | OB-026's fix reaches only squares whose annotation carries a badge or a mark, and nothing tests the call site that supplies it | Low | Medium |

---

## NR-1 - the rebuild that publishes a placement also reverts it

**Severity: High. Confidence: medium-high** - the call chain is explicit and cited below, but I could
not run it, and the last link depends on the running `AutoLayout` being valid, which I am inferring
rather than observing.

**Where.** `src/org/traincontrol/gui/AutonomyEditorPanel.java:2629`, and its three callers at `:881`,
`:1994` and `:2609`. Then `src/org/traincontrol/gui/TrainControlUI.java:3460`,
`src/org/traincontrol/gui/AutonomyViewerPanel.java:727`, and
`src/org/traincontrol/automationui/AutonomySession.java:2207`, `:2268`, `:2308`.

**What changed.** `placementChanged` was

```java
if (onDiagramChanged != null) onDiagramChanged.run();
else if (parentWindow() != null) parentWindow().rebuildRunningLayoutFromSetup();
```

and is now both, unconditionally. The reason given is MT-125 - a facing changed from the track diagram
did not refresh the viewer - and the reason is correct: `onDiagramChanged` is set on the menu-only
panel at `TrainControlUI.java:2746`, so the `else` branch had become unreachable and the OB-034/OB-035
seam had quietly stopped working.

**What is wrong.** `rebuildRunningLayoutFromSetup` is not a repaint. It calls
`AutonomyViewerPanel.load(activeDiagramConfiguration, false)`, and the second thing `load` does, at
`AutonomyViewerPanel.java:741`, is

```java
session().captureFromLayout(ui.getModel().getAutoLayout().toJSON(), ui.getActiveDiagramConfiguration());
```

`captureFromLayout` writes the RUNNING layout's state back into the configuration. For every square the
running layout emits as a Point:

- `AutonomySession.java:2309` - `for (String key : POINT_OPERATIONAL_KEYS) { if (captured.has(key)) before.put(...); else before.remove(key); }`. `POINT_OPERATIONAL_KEYS` (`:1634`) contains `"loc"` and `"home"`.
- `AutonomySession.java:2320` - `if (captured.has(FACING)) before.put(FACING, captured.get(FACING));`, where the captured facing is derived from WHICH COPY of the split square the locomotive was found on (`:2268`, via `AutonomyBuilder.facingOf`).

All three callers of `placementChanged` write the configuration and nothing else:

| Caller | What it writes | What the running layout still says |
|---|---|---|
| `:1994` `session.setFacing(target, facing)` | `points[X].facing = E` (`AutonomySession.java:3066` - config only) | the train is on the copy that means N, so `captured.facing = N` and `before.put(FACING, N)` |
| `:2609` `session.placeLocomotive(tile, name)` | `points[X].loc` (`AutonomySession.java:2888` - config only) | no train on X, so `captured` has no `"loc"` and `before.remove("loc")` runs |
| `:881` `session.placeLocomotive(target, null)` | removes `points[X].loc` | the train is still there in the running layout, so `before.put("loc", ...)` puts it back |

Then `load` calls `session().rebuild()` at `AutonomyViewerPanel.java:761`, so the layout is regenerated
from the configuration that has just been overwritten.

`captureFromLayout`'s own comment at `AutonomySession.java:2295` names the precondition it needs: "The
running Layout was built BEFORE any edits made in the editor since, so replacing the whole object
discarded them - set a terminus, press Apply, exit, and it was gone." That is why it merges per key
instead of substituting. But `"loc"`, `"home"` and `FACING` are precisely the keys it DOES replace from
the stale layout, and until this change nothing called it on the edit path. It ran at load and unload
boundaries, where the running layout is the newer of the two. `placementChanged` is now on the hot path
of every placement edit, where it is the older.

This is the familiar shape - a rule copied to where its precondition does not hold - arriving from the
other direction: the rule did not move, the call site did.

**Why it matters.** The user sets which way a train faces, the diagram redraws, and it shows the old
direction - which is MT-125 again, reported as fixed. Placing or removing a locomotive from the setup
behaves the same way. Nothing errors and nothing is logged.

**A second, softer version of the same thing.** Even where the capture agrees, `load` is not cheap: it
calls `prepareAutonomyReload` (which runs `resetLayoutStationLabels`), captures, rebuilds the reduction
and reloads the whole configuration - now once per placement edit, on top of the `repaintLayout` that
`onDiagramChanged` already does. Two full diagram rebuilds per menu click.

**And it contradicts a guard added in the same round.** `AutonomyMenu.guardWhileEditing`
(`AutonomyMenu.java:148`) greys every item while an editor is open, and says why at `:322`: "the
rebuild redraws the main diagram with the editor's edit flag still set on the shared page". The
autonomy editor now performs that same rebuild itself, unprompted, on every placement change. One of
the two beliefs is wrong. My reading is that the guard's reason is the stale one - `NR-7` - because the
`inEditor` change in `LayoutGrid` removed the shared-flag hazard it describes. Worth deciding
explicitly rather than leaving the two on the same branch.

**What I would do.** Do not reach `captureFromLayout` from an edit. Either give `TrainControlUI` a
narrower entry point that rebuilds the layout from the configuration WITHOUT capturing first, and call
that from `placementChanged`; or have `placementChanged` update the running layout to match the edit
before publishing it, the way `LayoutRightclickAutonomyMenu.placeFacing` already does
(`moveLocomotive`, then `placeLocomotive`, then `setFacing`, then `updateVisiblePoints` -
`LayoutRightclickAutonomyMenu.java:651`). The second is the smaller change and has the advantage that
the two facing paths would then agree.

**Test.** A model-level test can pin this without a window: place a locomotive through
`AutonomySession`, call `captureFromLayout` with a layout JSON built before the placement, and assert
the placement survives. That is the invariant, and it fails today.

---

## NR-2 - a station's name can no longer be put on the platform road

**Severity: Medium. Confidence: high.**

**Where.** `src/org/traincontrol/gui/AutonomyEditorPanel.java:1388` and `:1654`;
`src/org/traincontrol/base/LayoutDiagramComponent.java:200`.

The old gate was `isStraightThrough(tile)` - one route joining two opposite sides. Its javadoc named
the set it admitted: "straights, straight sensors, signals, uncouplers". The new gate is

```java
private boolean mayCarryACaption(LayoutDiagramComponent component)
{
    return component == null || !component.isClickable();
}
```

and `isClickable()` is

```java
return this.isRoute() || this.isSignal() || this.isSwitch() || this.isUncoupler() || this.isFeedback()
        || this.isLamp() || this.isLink();
```

OB-042 and OB-044 asked for curves and bumpers to be ADMITTED, and Adam's rule was "the only fair place
to disallow them are clickable elements like switches and signals". `isClickable()` is wider than the
two he named. It also excludes FEEDBACK, UNCOUPLER, ROUTE, LAMP and LINK - and of those, straight
FEEDBACK and straight UNCOUPLER squares were admitted by the rule this replaced.

FEEDBACK is the one that matters. A station IS a feedback square. The comment left in place directly
above the new gate, at `:1371`, still says: "on a platform the sensible place for the name is the
platform road itself." `AutonomyCompanionStore.forgetSquares`'s comment at `:1766` says the same thing
from the storage end - "A caption may sit on its own station's square - that is how a name gets drawn
over a platform rather than beside it". Both describe something the menu no longer offers, and there is
no other route to it: `buildTextMenu` (`:1571`) is reached only from `showTextMenu` (`:1562`), for text and blank
squares.

So a change that set out to widen the rule narrowed it at the one square a user is most likely to try.

**What I would do.** Say the exception rather than borrowing a predicate that means something else:

```java
return component == null
    || !(component.isSwitch() || component.isSignal() || component.isRoute() || component.isLink());
```

`isClickable` exists to decide whether a tile routes mouse clicks, which is a different question, and
borrowing it here couples the caption menu to any future change in what counts as clickable - a shared
predicate with two contracts. Whichever set is chosen, put a test on it that names FEEDBACK
explicitly, because the reason this slipped is that no test covers which types may carry a caption.

---

## NR-3 - the weak map is not weak

**Severity: Medium. Confidence: high** on the mechanism; medium on how much memory it actually costs,
which depends on how often the editor and the export are used.

**Where.** `src/org/traincontrol/gui/LayoutGrid.java:107`, with `:171` and `:708`.

```java
private static final java.util.Map<JPanel, LayoutGrid> LIVE =
    java.util.Collections.synchronizedMap(new java.util.WeakHashMap<JPanel, LayoutGrid>());
```

The javadoc says "Weak keys: a panel that has gone away takes its entry with it, and nothing here keeps
a window alive that the application has finished with." That is the intent, and a `WeakHashMap` does
not deliver it when the VALUE can reach the KEY. Here it can, by two hops:

- `LayoutGrid.container` is a strong field (`:78`);
- `parent.add(container)` at `:708` sets `container`'s parent pointer to `parent`, which is the map key.

So every entry is strongly self-reachable, the key is never weakly reachable, and nothing is ever
cleared. One entry per `JPanel` ever used as a grid parent, each retaining a whole page of
`LayoutLabel`s and their icons.

Three of the four call sites use a panel that lives as long as its window, so the leak is one page per
`LayoutEditor` and per `LayoutPopupUI` ever opened. The fourth is worse: `DiagramExport.java:115`
builds a grid over a freshly created `host` panel on every export, so every picture the user saves
pins a complete grid forever.

The retirement behaviour the map was added for is right and worth keeping - the finding is only about
how it is stored.

**What I would do.** Either make the value a `WeakReference<LayoutGrid>`, or drop the static map
entirely and hang the outgoing grid off the panel itself with
`parent.putClientProperty("layoutGrid", this)`. The second is the Swing-native version of the same
idea, has no global state, and cannot outlive the panel by construction.

`testANewGridRetiresTheOneItReplaces` (`test/ui/testDiagramExport.java:227`) is a good test of the
retirement and would keep passing through either fix.

---

## NR-4 - the one-writer guard reaches one file, and the second writer is in the other

**Severity: Medium. Confidence: high.**

**Where.** `test/regression/testEditorSurfaceRules.java:57`, against
`src/org/traincontrol/gui/LayoutRightclickAutonomyMenu.java:665`.

The test asserts

```java
assertEquals(writes, 1,
    "the facing is written to the setup from " + writes + " places in AutonomyEditorPanel...");
```

after scanning `AutonomyEditorPanel.java` alone. Across `src/` there are two callers of
`session.setFacing`: `AutonomyEditorPanel.java:1988` and `LayoutRightclickAutonomyMenu.java:665`.

The second one is not a live defect - `placeFacing` redraws at `:684` with
`ui.updateVisiblePoints()`. But it is the surface OB-039 was reported from ("when changing the
orientation of a loc from the track diagram..."), and it uses a completely different redraw from the
one the test pins. The test's message claims a project-wide property it does not check, and a third
copy added in any file other than `AutonomyEditorPanel.java` passes it.

There is a related asymmetry worth noticing while this is open: the two paths do different amounts of
work. `placeFacing` moves the locomotive in the running layout first; `buildFacingMenu`'s radio does
not. That difference is what makes `NR-1` bite on one and not the other.

**What I would do.** Scan `src/` recursively for `session.setFacing(` / `.setFacing(` rather than one
file, and assert the set of files that contain it equals a named list - the same shape
`testTriggerWaitsSayNothing.java:191` already uses (`assertEquals(callers, ... Arrays.asList("Layout.java"))`),
which is the better pattern of the two in this round.

---

## NR-5 - the separator lands at the bottom of the menu

**Severity: Low. Confidence: high.**

**Where.** `src/org/traincontrol/gui/AutonomyMenu.java:148`.

```java
insert(busy, 0);

addSeparator();
```

`insert(busy, 0)` puts the "an editor has the diagram" item first. `addSeparator()` APPENDS, so the
rule intended to sit under that item is drawn after the last item in the menu instead - a horizontal
line at the bottom of the popup with nothing below it.

The javadoc says this is "the same shape as `TrainControlUI.guardLayoutMenu`, deliberately".
`guardLayoutMenu` (`TrainControlUI.java:1977`) adds no separator at all, so the two are not the same
shape and the one that does add one puts it in the wrong place.

**What I would do.** `insertSeparator(1)`, which is the `JMenu` method for exactly this, or drop the
separator and
match `guardLayoutMenu` exactly, which is what the javadoc claims.

---

## NR-6 - the length field can still be given a number that is not an `int`

**Severity: Low. Confidence: high.**

**Where.** `src/org/traincontrol/gui/AutonomyEditorPanel.java:3449`.

```java
int length = entered.isEmpty() ? 0 : Integer.parseInt(entered);
```

The `try`/`catch (NumberFormatException)` around the old parse was removed, on the reasoning that the
`DocumentFilter` installed at `:3385` makes anything but digits impossible. It does - but it does not bound the
LENGTH of the string. `99999999999` is eleven digits, passes the filter, and `Integer.parseInt` throws.

It is caught: `item()` wraps every menu action in `catch (RuntimeException)` at `:1483` and shows
`error.generic` with the exception's message. So the user sees a dialog reading `For input string:
"99999999999"` - an untranslated Java string in a UI that is otherwise fully localised, where they used
to get `autosetup.ui.errorNegativeLength`.

That key is now referenced from nowhere in `src/` and remains in all eight message bundles. No test
checks for orphaned keys, so it will sit there.

**What I would do.** Cap the field in the filter - refuse an insert that would take the text past, say,
six digits, which is the same "cannot be got wrong" argument the filter is already built on. Then
delete `errorNegativeLength` from the eight bundles, or keep it and restore a bounds message.

---

## NR-7 - four comments that now say the opposite of the code

**Severity: Low. Confidence: high.** Grouped because they are one habit, and because in this codebase a
comment stating a decision is treated as the decision.

1. **`AutonomyEditorPanel.java:2619`** - `placementChanged`'s javadoc still reads "From the track
   diagram's DEEP MENU there is no grid of ours to rebuild - `onDiagramChanged` is null, because this
   panel is a menu builder with no window". It is not null: `TrainControlUI.java:2746` sets it. That
   false premise is exactly what let the OB-035 seam rot until MT-125 found it again. The last line -
   "Not done while an editor is open, because the editor defers that to closing on purpose" - now
   describes behaviour the body has removed, and it is the sentence a reader would use to argue
   `NR-1` cannot happen.

2. **`LayoutGrid.java:211`** - the comment introducing the `container` block still says the shared
   flag "is right for clickability below, where the viewer's tiles must stop routing clicks while an
   editor owns the page". The line it is talking about, `:284`, now passes `inEditor`, so the viewer's
   tiles stay clickable while an editor is open. I believe that IS the intended behaviour after this
   change - the whole point is that the viewer should behave like the viewer - but the comment says the
   opposite, and it is the only written record of why the flag was shared.

3. **`AutonomySession.java:2973`** - `facingChoices`'s javadoc still opens "One per side track arrives
   by, because a train that came in by the west side is pointing east", which is the rule
   `onwardFrom` was written to replace, and which its own javadoc twelve lines below calls "true by
   accident". The following sentence - "Ordered the same way the builder orders its copies" - is now
   more true than it was, since `onwardFrom` and `AutonomyBuilder.facingOf` finally agree, and that is
   worth saying where the old sentence is.

4. **`TileOverlay.java:351`** - inserting `middle` between `paintRun`'s javadoc and `paintRun` left the
   javadoc attached to `middle`, which now has two. `paintRun` has none. `TileAnnotation.java:1541`
   already carries an identical orphan from an earlier edit, so this is the second instance of the same
   accident, which suggests whatever moves these blocks is the common cause rather than either edit.

---

## NR-8 - the matrix test counts a mention in a comment

**Severity: Low. Confidence: high.**

**Where.** `test/regression/testStoreCollectionsAreHandledEverywhere.java:177`, against
`src/org/traincontrol/automationui/AutonomyCompanionStore.java:1803`.

The test asks `body.contains(kept)` for each collection at each site. Its own javadoc at `:224` is
honest about the weakness - "Something that reads the file cannot tell a real handling from a mention
in a comment" - and argues that "a collection nobody handled is a collection nobody wrote about
either".

This change produced the counter-example. `forgetSquares` lost its `tileDirections.remove(key)` line
and gained a comment explaining why:

```java
// `tileDirections.remove(key)` as its eleventh member - written because everything else was
// there, and dead from the day it was written...
```

The real handling is the loop below it, and the loop is right. But if that loop were deleted tomorrow,
`forgetSquares` would still contain the string `tileDirections` - in this comment - and the test would
stay green for the collection whose removal it is meant to guard. The comment is now load-bearing for
the test.

The same applies at `reconcile`, where several collection names appear in prose as well as in code.

**What I would do.** Strip `//` and `/* */` from `body` before the `contains` check. It is a few lines
in `bodyOf`, it does not weaken anything the test currently catches, and it removes the one way this
test can be satisfied by writing rather than by doing.

---

## NR-9 - the run line stops on the rail only where the annotation knows where the rail is

**Severity: Low. Confidence: medium.**

**Where.** `src/org/traincontrol/gui/LayoutLabel.java:965`, `TileAnnotation.java:1587`,
`TrainControlUI.java:3023` and `AutonomySession.java:3567`.

The overlay change is right and the two new tests in `testAutonomyDiagramMonitor` (`:402`, `:460`) pin
it well - they paint into a `BufferedImage` and read the pixels, which is the correct way to test a
drawing defect. But both call

```java
overlay.paint(g, size, size, new int[] {45, 15});
```

with the answer handed in. Nothing tests the call site that computes it, which is
`LayoutLabel.java:971`:

```java
overlay.paint(g2, getWidth(), getHeight(),
    annotation == null ? null : annotation.trackCentre(getWidth(), getHeight()));
```

That is the usual result of extracting a rule and testing the extract: the rule is now covered and the
call is the only uncovered part.

Following it through the running diagram: `TrainControlUI.showStaticAutonomyLayer` (`:3023`) annotates
from `session.staticAnnotationFor(tile)`, which returns `null` for any square that is not a Point
(`AutonomySession.java:3585`), and for a Point that is neither a station nor a turn-around returns an
annotation with no marks and no badge (`:3598`). `TileAnnotation.trackCentre` (`:1587`) falls back to
`{width/2, height/2}` in both cases.

So on the running diagram the fix applies to stations and turn-arounds and to nothing else. That covers
OB-026 as reported - "when arriving at a curved station" - and the train dot on a station. A train dot
on a curved PLAIN sensor still sits in the middle of the square, which is the same "offcenter" symptom
one tile along.

I am not sure this is worth fixing; it may be that no plain sensor on Adam's layout sits on a curve. It
is worth knowing, because the next report of it will read as the fix not having worked. The cheap
answer, if it is wanted, is for `TileOverlay` to derive the midpoint from its own segments' sides when
the caller hands it nothing - it already knows one side of the pair at the end of a run.

**What I would do regardless.** Add one test that goes through `LayoutLabel`, or at minimum through
`TileAnnotation.trackCentre`, for a curved badge - asserting it returns the corner and not the centre.
Without it the wiring between the two halves of this fix is untested.

---

## E - checked and left alone

Written out because a short honest list is worth more than manufactured doubt, and because each of
these was a plausible place for the defect shapes this round was searched for.

| What | Verdict |
|---|---|
| **The through-case of the run line** | Correct and unchanged. `paintRun` uses `centre` only where `segment.getFrom()` or `getTo()` is null (`TileOverlay.java:441`, `:444`, `:489`), and a curve the run passes through has both. The comment's warning about the forty-five degree line is preserved by construction, not by care. |
| **`trackCentre` for a curve** | Correct. For an N-E curve the midpoint of the two side-midpoints is exactly the midpoint of the chord the tile art draws, so the stub is the first half of the same line - not an approximation of it. |
| **`onwardFrom`** | Correct, and it now agrees with `AutonomyBuilder.facingOf` (`AutonomyBuilder.java:626`), which had already been fixed the same way. The ordering claim in the javadoc holds: `arrivals` is a `TreeSet`, so the first offered facing is the one the default copy carries. The `arrival.opposite()` fallback is the old answer kept for squares with no describable track, which is right. `testFacingFollowsTheTrack` asserts the RULE rather than a table of answers and would have caught the original defect - the best of the new tests. |
| **`facingsAt` / `StationIndex`** | Not a sibling of the fixed rule. It reads `builder.facingByName()`, which already went through `facingOf`. |
| **`numOrientations` delegation** | Correct. `LayoutDiagramComponent.getNumOrientations(type)` is a faithful lift of the instance method's body, the instance method now delegates to it, and `testOrientationDomainMatchesRotationalSymmetry` (`test/core/testAutonomyDiagramPorts.java:134`) pins the counts independently rather than by asking the same method twice. |
| **`reconcile`'s new sweep** | `linkNames` and `disabledPortals` are handled, and `portals` - which I expected to be the missed eleventh - is handled further down at `AutonomyCompanionStore.java:2105` by the broken-pairings loop. The sweep is complete against `forgetSquares`'s list. |
| **`setPortalDisabled` switching its partner** | Correct, and the recursion is avoided the right way. `AutonomySession.pairPortals` (`:3242`) now enables the second end twice, which the comment acknowledges and which costs nothing. One loose end that is a question rather than a defect: unpairing two links that were switched off together leaves both off, with nothing on the diagram saying why. |
| **`forgetSquares` handling a bare direction key** | Correct. `squares.contains(at >= 0 ? key.substring(0, at) : key)` is a strict widening of what was there, and the removed `tileDirections.remove(key)` was genuinely dead. |
| **`showFor` as the one way in** | Correct. All four call sites converted, the constructor is private, and the empty-menu guard - which one of the four was missing - is now unavoidable. This is the cleanest change in the round. |
| **`inEditor`** | Correct, and the test that pins it (`testTheViewerIsNotToldItIsAnEditor`) is a real guard: it fails on any new bare `layout.getEdit()` in that file. See `NR-7` for the comment that was left behind. |
| **`restingBorder`** | Correct, including the `|| isAutonomyMode()` added to `highlightLabel`'s reset (`LayoutEditor.java:3152`) - without it a null resting border would have made the first hover leave a line behind. That is the sibling of the change, and it was found. |
| **Ctrl+L and Ctrl+D above the autonomy guard** | Correct, and the sweep to the third key was the right instinct. No other shortcut in that dispatcher uses Ctrl+D or Ctrl+L with additional modifiers, so nothing is shadowed by moving them to the top. |
| **`homeChoices` / `homeBrokenBy`** | Faithful lifts, and the call site at `AutonomyEditorPanel.java:2297` is equivalent to the code it replaced, including the `names.size() == 1` test that follows. There is only one home-choices site in `src/`, so there is no sibling to have missed. `testHomeAssignmentRules` is slightly brittle - it asserts on the literal `"homeChoices(I18n.t("` - but it does test the extracted rules for real. |
| **`digitsOnly`** | Sound. Not overriding `remove` is right; rejecting a whole paste that contains a non-digit is a defensible choice. See `NR-6` for the one gap. |
| **`signalWindowOpen` and the de-clutter** | Correct, and clearing it in the `finally` is the right place for the reason given. `testTheDeclutterIsOneDecision` (`:157`) counts three uses and finds three; it excludes `//` lines but not javadoc `*` lines, so a future javadoc mentioning "focused" would break it - annoying rather than dangerous. |
| **`RouteEditorFrame`'s `...ForTest` hooks** | They call the same private methods the cell editor calls, which is the right way to build a test hook. `testCommandTableMarks` asserts real reordering and deletion. |
| **`testAutonomyDiagramPorts`** | The strongest new test in the round: it executes the table the retired Python script used to hold, and fails the build when a tile type is added without stating its ports. Retiring the script was right. |

**One thing I noticed that is NOT from this diff**, mentioned because it sits in a file the diff
touches: `testDiagramExport.testTheActivePageDrawsTheSamePictureAsChoosingIt` (`:187`) renders the same
page twice with the identical call, `DiagramExport.render(page, 60, ui)`, and its javadoc claims it
compares the active-page shortcut against choosing the page by name. It tests that rendering is
deterministic, which is worth something, but not the thing it names. It predates `4ba329ad` and is out
of scope for this round.

**And a note on the source-scanning tests generally.** Six of the new tests open a file with
`if (!SOURCE.isFile()) return;` and pass silently if it is not there. That follows the existing
`testNoSelfRecursiveWrappers` pattern and is fine as long as the battery always runs from the
repository root - but it means a whole class of guard can be switched off by a working-directory
change with no failure anywhere. `testDiagramLooksRight` has the same property by a different route: it
has six `SkipException` exits, one of which is "no start can reach a curved station", so the test that
would exercise `NR-9` end to end can pass by never running. Both are honest about it in their
javadocs. It is worth one small test asserting that these files ARE found, so the harness cannot go
quiet.
