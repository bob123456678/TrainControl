# Validation: the single-pass rendering round, and the six findings it claimed to close

**Status:** open

**Prefix for citing these findings elsewhere:** `VLD` (confirmed unused - `grep -rl "VLD-" docs/`
returns nothing).

**Reviewed:** branch `autonomy-diagram-r0`, working tree at `77297e48`, on 2026-09-04 (2026-09-05
UTC). The four commits under review are `80249ebd`, `489273f1`, `30ce7232`, `77297e48`; only the
Group A rendering work and the six claimed-closed review findings were in scope, so the `AxisRuler`
and `CS2File` halves of `80249ebd` (OB-172) are named as not reached rather than assessed.
`cs2_sample_layout/` was read and never written.

**Method.** Nine test classes were run through `one.sh` at baseline, then two temporary probe
classes were written, run, and deleted, and the baseline was re-run to confirm the tree came back
clean. Every claim below is marked for whether it was established by **execution** or by
**reading**; where a suspicion could not be reproduced it is graded down and said so. The two
probes established, by running them: which of `testTheRebuildIsOnePass`'s four tests survives which
mutation; what `readLayoutIndexExtras` and `writeLayoutIndex` actually do to a genuine CS2 export;
what `legacySignalAddress` returns for twenty-two inputs including the names this program generates
for itself; that the new bundle key renders in all eight languages with both placeholders filled;
and exactly what the source-shape test can and cannot see. `compare.py` was run twice against
synthetic TSVs, once with a dropped lock and once with both directions empty.

**Test runs (all `Failures: 0, Skips: 0`).** Baseline: `ui.testTheRebuildIsOnePass` 4,
`core.testParseCS2Layout` 20, `core.testLocDB` 6, `core.testAutonomyDiagramSession` 114,
`regression.testEveryTestIsInTheBattery` 4, `ui.testDiagramLooksRight` 20,
`ui.testTheDiagramPrintsItsCoordinates` 9. Re-run after the probes were deleted:
`regression.testEveryTestIsInTheBattery` 4, `ui.testTheRebuildIsOnePass` 4,
`core.testParseCS2Layout` 20, `core.testAutonomyDiagramSession` 114. Nothing was left on disk;
`one.sh`'s live-layout fingerprint guard was silent on every run.

---

## Verdict

**Nothing found here should hold the release, and the two mechanisms at the centre of the round -
`singlePass` and the deferred `removeAll` - are both correct.** The `singlePass` contract was
mutation-tested by execution against both plausible mutants and all three of its behavioural tests
discriminate; the control the brief asked me to identify is
`testOffTheEventThreadItStillMarshals`, and it is the only one of the three that survives reverting
`singlePass` to an unconditional `invokeLater` - it kills the opposite mutant instead. `removeAll`
has no early return between the old and the new position, no code between them reads the panel's
contents, and `container` cannot equal `parent` in the current source, so the swap is unconditional
in practice. The caller census of `refreshRouteList` (22 sites) and `refreshLocSelectorList` (9)
turned up no deadlock and no caller that mutates model state after the call expecting the refresh
to see it - the one site that looked like it, `deleteRoute`, turns out to have been unprotected
before the change too (`VLD-C3`).

**What is wrong is that the round is one site short in three separate places, and each miss is the
same shape: the rule was applied where it was found and not swept to its siblings.**
`refreshLocSelectorList` was converted, but the `filterLocList` it calls kept its own `invokeLater`,
so the locomotive selector still arrives in two passes and is still the staggered thing Adam
reported (`VLD-B3`). Two of the three places that rebuild a diagram into an occupied panel got the
fix and `LayoutPopupUI` did not, because it empties the panel itself one line before the
constructor (`VLD-B4`). The index preservation carries the block the finding named and duplicates
the block the same finding named one item later, because the modelled-name set is
case-sensitive and the shipped CS2 export spells it `Version` (`VLD-B2`). And
`legacySignalAddress` matches only the MM2 spelling of a name this program itself generates as
`Signal 116 DCC`, so a DCC user's signals are silently absent from the one report written to stop a
signal being silently absent (`VLD-B1`).

Two of those are documentation faults as much as code faults: `singlePass`'s javadoc justifies its
own scope with a claim about which thread builds the grid that is false in the current source
(`VLD-B5`), and `AC2-C1`'s closure text, written in the same commit as the fix, states that the
capitalised `Version` block "is dropped by the first save" when the fix it accompanies makes the
file keep two of it.

`AC2-C3`, `AC2-C4`, `RG4-C2` and `RG4-C3` are sound as claimed; the bundle work is clean in all
eight languages; `build.xml` is right and the battery-membership test agrees. Details in
`VLD-D1`.

---

## Summary

| # | Severity | One line | Where |
|---|---|---|---|
| B1 | B | `legacySignalAddress` matches only the MM2 spelling, so every DCC/MFX-protocol signal - a name this program generates itself, `Signal 116 DCC` - is silently absent from the RG4-B1 report | `AutonomySession.java:954-973` |
| B2 | B | The index preservation carries the capitalised `Version` block through as unmodelled, so a genuine CS2 export gains a *second* version block on the first page edit - the case `AC2-C1` item 3 named, and which its closure text says is dropped | `LayoutDiagram.java:982-985` |
| B3 | B | `refreshLocSelectorList` was converted but `filterLocList`, which it calls, kept its own `invokeLater` - so the locomotive selector still arrives in two passes | `LocomotiveSelector.java:82-131` |
| B4 | B | `LayoutPopupUI` empties the diagram panel itself, one line before building the grid, so the popup diagram window keeps the blank-panel flicker the fix removed everywhere else | `LayoutPopupUI.java:55-57` |
| B5 | B | `singlePass`'s javadoc justifies leaving the repaints out of the pass with a claim that is false: both renderers do nothing but `invokeLater`, so the grid *is* built on the event thread | `TrainControlUI.java:26125-26128`, `:26895`, `:9744` |
| C1 | C | The source-shape test passes with the defect fully present: it keeps the *last* `parent.removeAll();` in the file, so re-adding one at the top of the build reads as green | `testTheRebuildIsOnePass.java:141-181` |
| C2 | C | Two consequences of holding the panel: an exception mid-build now leaves a discarded, stale diagram mounted rather than an empty panel; and a rebuild started while the previous grid's spinner is up shows diagram and spinner side by side for the whole build | `LayoutGrid.java:826-841,1766` |
| C3 | C | `deleteRoute` is `duplicateRoute`'s sibling and never got its "and again after the sync" second refresh; the conversion removes the last accidental protection, which was already unreliable | `TrainControlUI.java:16647-16685` |
| C4 | C | `compare.py`'s new section calls itself "the direction that matters" but does not affect the exit code that the harmless direction gates | `compare.py:365-408,437` |
| C5 | C | `legacySignalAddress` accepts `Signal -5`, `Signal 0` and `Signal +116`; and the shipped test checks the count with `line.contains("2")`, a substring stand-in for a number | `AutonomySession.java:967`, `testAutonomyDiagramSession.java` |
| C6 | C | Nothing verifies Adam's actual report. The automated test declines the visual property on purpose, and no manual-test entry was added for the rendering pass | `docs/manual-tests/tests.md` |
| C7 | C | Two small corrections: a comment says "the four names this file writes for itself" over a three-element set, and the new signal scan is a second full traversal of `edges` beside the existing one | `LayoutDiagram.java:982`, `AutonomySession.java:1068-1090` |
| D1 | D | What was checked and found sound, and what this pass did not reach | - |

---

## VLD-B1 - Only MM2 signals are named; a DCC signal is silently left out of the list written to stop signals being silently left out

| | |
|---|---|
| **Severity** | B - the fix's own comment states the cost of an omission ("a list that quietly omits one costs them a signal that lies about occupancy for as long as the railway runs"), and this omits a whole protocol without saying so.  Not A: Adam's own five signals are MM2 and unaffected, and nothing is worse than before the fix - there simply was no list |
| **Disposition** | fixed |
| **Confidence** | **Executed.** A probe called `MarklinAccessory.getNameWithProtocol(116, SIGNAL, d)` for every `accessoryDecoderType` and fed each result straight to `legacySignalAddress`, and put the same names through `RouteCommand.fromLine` to show they are names the config-command parser really accepts.  Output quoted below verbatim.  NOT established: whether any 2.8.1 file in the wild actually carries one - every `"acc"` value in this repository is MM2-spelled (`grep -rho '"acc" *: *"[^"]*"' --include=*.json .`), so this is reachability by construction rather than a demonstrated user file |

`legacySignalAddress` (`AutonomySession.java:963`) requires the name to continue with a number
immediately after `"Signal "`:

```java
if (trimmed.length() < 8 || !trimmed.substring(0, 7).equalsIgnoreCase("Signal ")) return null;

try
{
    return Integer.valueOf(trimmed.substring(7).trim());
}
```

But the standardized accessory name this program generates carries the protocol when it is not the
implicit one - `MarklinAccessory.getNameWithProtocol`'s own javadoc says so: *"DCC Signal 1: `Signal
1 DCC`; MM2 Signal 1: `Signal 1`"*. Probe output:

```
  decoder MM2 -> standardized name "Signal 116"      legacySignalAddress -> 116
  decoder DCC -> standardized name "Signal 116 DCC"  legacySignalAddress -> null

  RouteCommand.fromLine("Signal 116,red")     -> addr=116 protocol=MM2 type=Signal
  RouteCommand.fromLine("Signal 116 DCC,red") -> addr=116 protocol=DCC type=Signal
  RouteCommand.fromLine("Switch 68 DCC,turn") -> addr=68  protocol=DCC type=Switch
```

So `Signal 116 DCC` is not a hypothetical string. It is the name `getNameWithProtocol` emits, the
name `Edge.validateConfigCommand` round-trips through `RouteCommand.fromLine`, and the name
`MarklinControlStation.getAccessoryByName` resolves. A 2.8.1 autonomy.json whose signals are on DCC
decoders therefore produces `legacySignalAddress -> null` for every one of them, the
`signalAddresses` set stays empty, and `autosetup.ui.leftSignalAuthors` is never added to the
report. The operator is told the aggregate command count and nothing else - which is the exact state
`RG4-B1` was filed to end.

The shipped test does not reach it: `testTheSignalNameParserIsNotFooled` covers `Signal 116`,
`signal 37`, `Switch 68`, `Signalbox 4`, `Signal east` and `null`. `Signal east` is the "does not
parse" row, and a protocol suffix is the same shape - a token where the number should end - so the
row that exists is the row that should have caught this, and it was written with a string nothing
generates instead of one this program generates.

The repair is the same shape as the parse: take the leading digits and stop, rather than requiring
the whole remainder to be a number. That also fixes `VLD-C5`'s `Signal -5` at the same time.

---

## VLD-B2 - The index now keeps *two* version blocks, and the finding's own closure text says otherwise

| | |
|---|---|
| **Severity** | B - the code's own comment states the invariant it breaks, the review text written in the same commit asserts the opposite behaviour, and the test fixture is the one shape that cannot see it.  Not A or C: no functional consequence - Adam has ruled the transfer one-way, and TrainControl's own reader ignores the block - but a program that writes a duplicate block into a user's file is doing the thing `AC2-C1` was about |
| **Disposition** | fixed |
| **Confidence** | **Executed.** A probe built a temp layout folder carrying the exact opening of the shipped `sample_layout/config/gleisbild.cs2` (capital `Version`, then `groesse`, then `zuletztBenutzt`), called `LayoutDiagram.readLayoutIndexExtras`, then `writeLayoutIndex`, and printed both.  Output quoted verbatim.  Also executed: empty file, header-only file, an unmodelled block *after* the pages, the same unmodelled block twice, a tab-indented key, keys with no leading space, and a blank line inside a page - all handled correctly, see `VLD-D1` |

The `modelled` set is case-sensitive (`LayoutDiagram.java:984`):

```java
final java.util.Set<String> modelled = new java.util.HashSet<>(
    java.util.Arrays.asList("version", "groesse", "seite"));
```

and its own comment two lines above says what that has to prevent: *"A block whose name is one of
these is regenerated below and must not also be carried over, or the file gains a second copy."*

`sample_layout/config/gleisbild.cs2` - the genuine CS2 export this repository ships, and the file
`AC2-C1` was written from - spells it `Version`. Probe output, on that exact shape:

```
=== EXTRAS READ FROM A GENUINE CS2 EXPORT ===
  block: [Version,  .major=1]
  block: [zuletztBenutzt,  .name=Page 1]
=== FILE AFTER A PAGE EDIT ===
[gleisbild]
version
 .major=1
groesse
Version
 .major=1
zuletztBenutzt
 .name=Page 1
seite
 .id=1
 .name=Page 1
seite
 .id=2
 .name=Page 2

=== version blocks: lowercase=1 capitalised=1 ===
```

The file gains a second copy. It is stable rather than compounding - on the next write `version` is
matched and skipped and `Version` is carried once again - but a page rename now turns a station's
own file into one that has two version blocks, where before it had one.

**And `AC2-C1`'s closure text, written in the same commit, states the opposite.** From
`docs/reviews/2026-09-04-independent-acceptance.md`, added by `489273f1`:

> **Items 2 and 3 are closed as not defects.** A bare `page=1` and a capital-`Version` block are
> dropped by the first save, and nothing is worse for it [...]

Item 3 was closed on the strength of a behaviour the accompanying fix removed. The block is no
longer dropped; it is duplicated. The closure reasoning still holds on its own terms - TrainControl
is the only reader and has no use for either - but the sentence describing what happens is false as
of the commit that wrote it.

The test cannot see it because its fixture spells the block `version`
(`testAPageEditKeepsWhatTheStationWroteInTheIndex`, `test/core/testParseCS2Layout.java:106-110`).
Its "exactly once" assertion is on `zuletztBenutzt`, which is genuinely unmodelled; there is no
assertion that the *modelled* header appears once. Pointing the fixture at
`sample_layout/config/gleisbild.cs2`'s spelling, or adding
`assertEquals(count of "\nversion"-or-"\nVersion", 1)`, is what would have caught it.

Repair: compare case-insensitively, or add `"Version"`.

---

## VLD-B3 - The locomotive selector still arrives in two passes; `filterLocList` kept its `invokeLater`

| | |
|---|---|
| **Severity** | B - one of the three sites the round converted still does the thing the round exists to stop, and it is the one that touches Adam's own report about "some components appear faster than others" |
| **Disposition** | fixed |
| **Confidence** | **Read**, and the load-bearing fact is a quoted line rather than an inference: `filterLocList` contains `javax.swing.SwingUtilities.invokeLater`.  NOT executed: `LocomotiveSelector` is a `JFrame` that needs a live model, and I did not stand one up, so the *visual* consequence (a filtered list showing every locomotive for one pass) is reasoned from the code rather than seen |

`refreshLocSelectorList` was converted to `singlePass` (`LocomotiveSelector.java:82-84`), and its
last statement is `filterLocList()` (`:107`). `filterLocList` was not converted
(`LocomotiveSelector.java:111-113`):

```java
synchronized private void filterLocList()
{
    javax.swing.SwingUtilities.invokeLater(() ->
    {
        String filter = this.LocFilterBox.getText().toLowerCase();
        ...
        updateScrollArea();
    });
}
```

So every refresh of the locomotive selector is two events, not one: pass 1 removes the old items and
adds every locomotive; pass 2 hides the ones that do not match the filter and re-sizes the scroll
area. The filter box is deliberately *not* cleared on refresh - `refreshLocSelectorList` has
`// this.LocFilterBox.setText("");` commented out at `:105` - so whenever the user has typed a
filter, the intermediate pass shows the whole roster and the next one takes most of it away. That
is a visible flicker introduced by the refresh, in the window the conversion was meant to make
arrive in one piece. Even with an empty filter, `updateScrollArea()` still lands a pass later.

This also makes `doSync`'s claim narrower than its comment: the pass collects the route list, the
menus, and the *building* of the locomotive list, but the locomotive list's own second half is
still posted separately.

Repair is one word - `TrainControlUI.singlePass` in place of `invokeLater` in `filterLocList`. The
two other callers of `filterLocList` (`:358`, `:366`) are event handlers already on the event
thread, so they would gain the same collapse.

---

## VLD-B4 - The popup diagram window still empties its panel before the build

| | |
|---|---|
| **Severity** | B - the round's stated purpose is "the old diagram stays up until the new one is ready", and one of the three windows that rebuilds a diagram in place still goes blank for the whole build |
| **Disposition** | fixed |
| **Confidence** | **Read.** The census of `new LayoutGrid(` is complete (5 sites: `DiagramExport.java:115`, `LayoutEditor.java:5074`, `LayoutPopupUI.java:57`, `TrainControlUI.java:26968`, plus the array declaration) and each caller's surroundings were read.  NOT executed: I did not open the popup window and watch it |

`LayoutPopupUI.drawGrid` (`LayoutPopupUI.java:44-60`):

```java
if (this.grid != null) this.grid.discard();

this.ExtLayoutPanel.removeAll();

LayoutGrid grid = new LayoutGrid(this.layout, size, this.ExtLayoutPanel, this, true, parent);
```

The constructor's new `parent.removeAll()` at `LayoutGrid.java:1766` is now a no-op for this window,
because the caller has already emptied the panel eight hundred lines earlier - which is precisely
the position the fix moved it *out of*. The popup rebuilds on every size change and page change, so
this is a live path, not a corner.

The other two in-place callers are correct: `LayoutEditor.drawGrid` calls `discard()` and then the
constructor with no `removeAll` of its own, and `TrainControlUI`'s cached branch does
`InnerLayoutPanel.removeAll(); InnerLayoutPanel.add(cached);` back to back
(`TrainControlUI.java:26941-26945`). `DiagramExport` builds into a fresh `JPanel` and is unaffected.

Repair: delete the `removeAll()` line - the grid now does it, at the right moment.

---

## VLD-B5 - `singlePass`'s javadoc justifies its own scope with a claim that is false

| | |
|---|---|
| **Severity** | B - the project's standard is that a comment must not claim something that is no longer true, and this one is not incidental: it is the stated reason for what the fix deliberately does *not* collect, so a future reader deciding whether to widen the pass is reasoning from it |
| **Disposition** | fixed |
| **Confidence** | **Read**, but from a structural fact checked programmatically rather than by eye: a regex sweep over every `*Renderer.submit(` in `TrainControlUI.java` printed the first 200 characters of each lambda body.  For `LayoutGridRenderer` (`:26895`) and `LocRenderer` (`:9744`) the first statement of the submitted body is `javax.swing.SwingUtilities.invokeLater(`, and everything else is inside it.  NOT executed: I did not instrument the constructor to print its thread |

The javadoc at `TrainControlUI.java:26125-26128`:

> WHAT THIS CANNOT COLLECT, and deliberately: `repaintLayout` and `repaintLoc` go through their own
> single-thread renderers before they reach the event thread, **because building the grid is the slow
> part of a sync and must not be done on it.**

The second half is not true of the current source. `repaintLayout` submits to `LayoutGridRenderer`,
and the entire submitted body is one `SwingUtilities.invokeLater(...)` - including the
`new LayoutGrid(...)` at `:26968`. The renderer thread does no work at all; it exists only to
serialise the *posting*. The grid build, every image decode it triggers and every caption it
registers all run on the event dispatch thread. `LocRenderer` at `:9744` has the identical shape.
`DiagramExport` says as much from the other side: it *refuses* to run on the event thread with the
message *"a diagram export holds the event thread while it waits for tile images"*.

This matters beyond tidiness. The stated reason for leaving the two repaints out of the pass is that
folding them in "would mean the menus and the lists waiting for the diagram". They already do - the
diagram is built on the same thread, in a later event. What the renderers buy is serialisation
between successive repaints, not thread separation, and if the pass were ever widened it is that
property, not the false one, that would have to be preserved.

The correction is to the comment, not the code: the design decision (diagram last) is defensible;
the reason given for it is not the reason it works.

---

## VLD-C1 - The source-shape test passes with the defect fully present

| | |
|---|---|
| **Severity** | C - the test does discriminate against the exact mutation its own javadoc names, and the code is currently right; what it cannot see is a re-introduction, which is what a regression test is for |
| **Disposition** | fixed |
| **Confidence** | **Executed.** A probe ran the test's exact scan - the same trimmed string equality, the same `added - emptied <= 4` - over three synthetic sources and printed its verdict for each.  Output quoted below |

`testTheOldDiagramStaysUpUntilTheNewOneIsReady` scans every line of `LayoutGrid.java` and keeps the
**last** match of each statement:

```java
if (line.equals("parent.removeAll();")) emptied = i;
if (line.equals("parent.add(container);")) added = i;
```

An assignment, not a first-match-wins. So a build that empties the panel at the top *and* swaps at
the bottom - the defect entirely present, plus the new swap - leaves `emptied` pointing at the
bottom copy and the distance check passes. Probe output:

```
  GOOD (as shipped)                                              -> GREEN (removeAll@802 add@804)
  BAD (removeAll back at the top) - should be RED                -> RED   (removeAll@1   add@802)
  BAD (emptied at the top AND swapped at the bottom)             -> GREEN (removeAll@802 add@804)
```

Row 2 is the mutation the javadoc promises ("move `parent.removeAll()` back above the build and this
fails"), and it does fail - so the test is not a tautology and is not passing for the wrong reason
today. Row 3 is the shape a partial revert or a merge would most plausibly produce, and the guard
reports clean about it. This is the "a guard knows only what it lists" pattern: it lists one
occurrence, and there can be two.

Two other things the test does not catch, both by construction rather than by oversight: it says
nothing about the panel being emptied by a *caller* before the constructor runs, which is exactly
`VLD-B4`; and it asserts nothing about the `if (!container.equals(parent))` guard the statement now
sits inside, so moving the swap into a branch that does not always run would still read as green.

The cheap repair is `if (emptied < 0 && line.equals(...))` for a first-match scan, plus an assertion
that there is exactly one of each.

---

## VLD-C2 - Two consequences of holding the panel, one of them a documented concern coming back

| | |
|---|---|
| **Severity** | C - neither is demonstrated on a running window, and both were also wrong before the change; what the change does is widen the window from ~0 to the length of a whole build |
| **Disposition** | fixed |
| **Confidence** | **Read**, from a complete trace of the constructor's exit paths - a grep for `return`/`throw`/`continue` between lines 876 and 1766 found no early return and no throw between the old and the new position, so the *ordering* half of the fix is sound (see `VLD-D1`).  NOT executed: I did not force a throw mid-build, and I did not stand up two overlapping rebuilds to watch the spinner.  I could not reproduce either, and grade them accordingly |

**(a) An exception mid-build now leaves a stale diagram mounted.** The constructor registers itself
and discards its predecessor as its first act (`LayoutGrid.java:826-841`), and `discard()` sets
`discarded = true`, hands back the caption labels, calls `container.setVisible(true)` on the *old*
container and stops both timers. The `parent.removeAll()` that used to follow immediately now sits
at `:1766`. If anything between throws, the panel keeps the old container - visible, timers dead,
captions unregistered - and `LayoutEditor.drawGrid` catches and logs, so the window stays up. Before
the change the same failure left an empty panel. An empty panel is obviously broken; a diagram that
looks live and is not is the failure mode `OB-128` was filed about ("the grid stayed mounted, so
anything that reached this tab showed the railway that had just been unloaded, looking entirely
live"). No throw is known on this path, which is why this is C and not B.

**(b) The outgoing grid's spinner now sits beside the outgoing diagram for the whole build.** If a
rebuild starts while the previous grid has mounted its spinner (`showWhenTilesAreReady`'s 120ms
grace timer fired), `discard()` makes the old container visible again and kills the reveal that
would have removed the spinner - so `parent` holds *both* until `:1766`. `parent` is laid out with
`FlowLayout`, so they sit side by side. That is the artefact `LayoutEditor.drawGrid`'s own comment
says `discard()` exists to prevent: *"the old grid's grace timer drops a spinner into the middle of
the new one, and a FlowLayout with an extra component in it pushes the tiles along."* It used to
last from `discard()` to the next statement; it now lasts the length of a build.

The editor rebuilds the whole grid on every tile placement, which is the path most likely to
overlap. If this is worth fixing, `discard()` removing its own spinner from the panel it still holds
is the natural place, not a second `removeAll`.

---

## VLD-C3 - `deleteRoute` is `duplicateRoute`'s sibling and never got the second refresh

| | |
|---|---|
| **Severity** | C - the staleness is pre-existing and the change does not create it; what the change does is make it deterministic instead of timing-dependent |
| **Disposition** | fixed |
| **Confidence** | **Read.** The caller chain was established by census: `RightClickRouteMenu.java:153` is `menuItem.addActionListener(event -> ui.deleteRoute(routeName))`, so `TrainControlUI.deleteRoute` runs on the event thread.  I then traced `syncWithCS2` and found the thing that weakens my own first reading - `BusyDialog.run` pumps the queue - and downgraded accordingly.  NOT executed: I did not delete a station route and watch the table |

`TrainControlUI.deleteRoute` (`:16647-16685`), on the event thread:

```java
this.model.deleteRoute(route.getName());
refreshRouteList();

if (!wasLocal) this.syncWithCS2();
this.repaintLayout();
this.repaintLoc();
```

`syncWithCS2` "deletes and re-adds routes" (`doSync`'s own words), and `refreshRouteList` stores
live `Route` *objects* in the table model, not names. So the table can be left holding references
the sync has since replaced, and nothing in this method refreshes it again - `repaintLayout` and
`repaintLoc` do not rebuild the route table.

`duplicateRoute` (`:18445-18493`) has the identical shape and refreshes **twice**, with the reason
written out: *"The list first, and again after the sync."* `deleteRoute` has one refresh.

**Why C and not B.** My first reading was that `singlePass` broke this - that the old
`invokeLater` guaranteed the refresh landed after the sync, and inline execution moved it before.
That is wrong, and I checked it rather than filing it: on the event thread `syncWithCS2` goes
through `BusyDialog.run` (`TrainControlUI.java:9307`), which shows a modal dialog, and a modal
dialog pumps the event queue. The posted refresh would have been dispatched by that pump - near the
*start* of the sync, not after it. So the old behaviour was already unreliable in the same
direction. The conversion removes a protection that was not protecting.

The repair is `duplicateRoute`'s: a second `refreshRouteList()` after the sync.

---

## VLD-C4 - The new direction calls itself the one that matters and does not gate

| | |
|---|---|
| **Severity** | C - a harness observation, not a program defect; but a section that says "this is the direction that matters" and then leaves the exit code to the other one is a mixed signal for anyone wiring it into CI, which the existing comment says is the plan |
| **Disposition** | fixed |
| **Confidence** | **Executed.** `compare.py` was run twice against synthetic TSVs written for the purpose: once with a lock the old file had and the new one drops, once with both files identical.  Both outputs quoted.  The exit code was captured |

The mirror itself is **correct**, and I checked the two ways it could have been wrong:

- **Scope.** `old_routes` is bound at `compare.py:196` inside `main()` and used at `:381` in the same
  function. In scope.
- **Namespace.** The membership test `p[0] in old_routes` compares a `by_station` key against a
  `routes_of` key, and both are `(loc, base(start), base(end))` with facings stripped - so the test
  does not silently fail across the two engines' naming. Verified by running it with a 3.0.0 side
  whose every point carries a facing suffix; the pair was still found.
- **Pair ordering.** `concurrent_pairs` emits `(a, b)` with `a` earlier in a sort whose first element
  is the locomotive, and skips same-locomotive pairs - so `a[0] < b[0]` always, and a station-pair
  cannot appear as `(X, Y)` in one file and `(Y, X)` in the other. No false rows from ordering.

Both directions read correctly when empty:

```
- 2.8.1: 0 concurrent pair(s)
- 3.0.0: 0 concurrent pair(s)
- judgeable (both routes still exist): 0

**No pair that could run concurrently in 2.8.1, and still exists, has stopped.**

**No pair that 2.8.1 forbade, and whose routes both existed then, has been freed.**
```

and the dropped-lock case produced its table with the correct row. What it did not produce is a
non-zero exit:

```
| Loc1 | Alpha to Beta | Loc2 | Gamma to Delta |
EXIT=0
```

`main` still returns `1 if (lost or missing) else 0` (`:437`). Two smaller things: the summary block
above prints `judgeable (both routes still exist): N` for the first direction and prints no
corresponding count for the second, so a reader cannot tell how many pairs the new section was able
to judge; and the section carries no heading of its own, so it reads as a continuation of the lost
block rather than as its mirror.

Whether it should gate is a judgement - the section itself says each row "is a question rather than
a defect" - but the decision should be stated rather than inherited.

---

## VLD-C5 - The parser accepts three things that are not addresses, and the test checks a count with a substring

| | |
|---|---|
| **Severity** | C - none of the three is reachable from a file a 2.8.1 TrainControl wrote; filed because the fix for `VLD-B1` touches the same three lines |
| **Disposition** | fixed |
| **Confidence** | **Executed.** Twenty-two inputs through `legacySignalAddress`, output quoted |

```
  "Signal 1"               -> 1        "Signal +116"            -> 116
  "signal 116"             -> 116      "Signal -5"              -> -5
  "SIGNAL 116"             -> 116      "Signal 0"               -> 0
  "Signal  116"            -> 116      "Signal 007"             -> 7
  "  Signal 116  "         -> 116      "Signal 99999999999999"  -> null
  "Signalbox 3"            -> null     "Signal 116 red"         -> null
  "Switch 68"              -> null     "Signal<TAB>116"         -> null
  "SignalX116"             -> null     "Signal 1 2"             -> null
  "Signal"  "Signal "  ""  " "  null   -> null
```

The rejections are all right and the case-insensitivity works. `Integer.valueOf` accepts a leading
sign, so `Signal -5` yields `-5` and would be rendered into the operator's work list as an address
to go and pair; `Signal 0` likewise. Neither is a real accessory address. A digits-only parse fixes
both, and is the same repair `VLD-B1` needs.

Separately, in `testALegacyImportNamesTheSignalsItStopsDriving`, the distinct-signal count is
asserted as `assertTrue(line.contains("2"), ...)`. The rendered line is *"2 signals were switched by
hand ... (numbers 37, 116)"*, so it passes - but it would also pass on a wrong count of 3 if any
address in the list contained a digit 2, which is the ordinary case for a real file. Asserting the
count means asserting the count.

---

## VLD-C6 - Nothing verifies the thing Adam reported

| | |
|---|---|
| **Severity** | C - the mechanism is well tested and the visual property is genuinely hard to test; what is missing is the cheap half, which this project already has a place for |
| **Disposition** | fixed |
| **Confidence** | **Read.** `git diff HEAD~4 -- docs/manual-tests/tests.md` shows 22 added lines, all of them the OB-172 axis-number narrative; nothing about the rendering pass |

Adam's report was *"can we get the diagrams to re-render with less flickering... some components
appear faster than others"*. What the round produced for it is three behavioural tests of
`singlePass` (which are good - see `VLD-D1`) and one source-shape test that explicitly declines the
visual property:

> Checked as ordering rather than by catching a paint: reproducing the blank frame needs a paint to
> land inside a window that varies with disk and decode speed, and a test that reproduces only
> sometimes reports "fixed" on a bad day.

That reasoning is right, and the project's own answer to "right, and untestable in a JVM" is
`docs/manual-tests/tests.md` - nineteen hands-on tests Adam runs. Nothing was added there. So the
only record that the reported symptom is gone will be Adam noticing, and `VLD-B3` and `VLD-B4` are
two places where he plausibly would not.

One entry - *switch pages on the track diagram and reopen the popup; the old diagram should stay up
until the new one appears, and the locomotive selector should not show every locomotive before
filtering* - would cover all three.

---

## VLD-C7 - Two small corrections

| | |
|---|---|
| **Severity** | C - neither has a consequence |
| **Disposition** | fixed |
| **Confidence** | **Read** |

**(a)** `LayoutDiagram.java:982`: *"The four names this file writes for itself"* introduces a
three-element set. `[gleisbild]` is handled separately by the `startsWith("[")` branch, so the
sentence is presumably counting it; as written, the comment and the code disagree about a number,
in a place where the number is the whole point.

**(b)** `AutonomySession.java:1068-1090` walks `legacy.getJSONArray("edges")` and each edge's
`commands` a second time, immediately after the existing loop at `:999-1036` walks the same array
with the same `optJSONObject`/`isNull` guards. Two loops over one array with duplicated guards is
the drift shape this codebase keeps finding: a guard added to one and not the other. Folding the
signal collection into the existing loop costs nothing - `signalAddresses` would just be declared
above it - and removes the second place that has to be kept in step.

---

## VLD-D1 - What was checked and found sound, and what this pass did not reach

**Disposition:** record of coverage; nothing to fix.

### The `singlePass` mechanism and its tests - sound, mutation-confirmed by execution

The brief asked which of the four tests is the control that survives one mutation. A probe
reproduced both plausible mutants verbatim and ran the tests' own assertions against each:

```
--- MUTANT: unconditional invokeLater (the pre-fix behaviour)
    test1 (joined)        : RED   (seen=0)
    test2 (shared pass)   : RED   (order="")
    test3 (marshals)      : GREEN
--- MUTANT: always inline
    test1 (joined)        : GREEN
    test2 (shared pass)   : GREEN
    test3 (marshals)      : RED   (immediately=1 ran=1 onEdt=false)
--- MUTANT: as shipped
    test1, test2, test3   : GREEN
```

So the control is `testOffTheEventThreadItStillMarshals`, exactly as the brief expected - it is the
one that survives reverting to `invokeLater`. It is not a test that cannot fail: it kills the
opposite mutant, which is the one that would have moved two `setEnabled` calls onto a worker thread.
`testAPassAlreadyRunningIsJoined` and `testWrappedRefreshesShareThePass` both die on the pre-fix
mutant. None of the three is passing for the wrong reason. `testWrappedRefreshesShareThePass` is
killed by the same mutant as `testAPassAlreadyRunningIsJoined` and adds only the nesting-order
property, but that is redundancy rather than a defect.

`singlePass` is also correct from a thread inside `invokeAndWait`: the *body* of `invokeAndWait`
runs on the event thread, so a `singlePass` there runs inline, which is what is wanted; a different
thread calling `singlePass` while the event thread is inside such a body is not the event thread and
posts, which is also what is wanted. `test3` covers the second case by execution.

### The `removeAll` move itself - sound

- **No early exit between the two positions.** A sweep for `return`, `throw`, `continue` and `break`
  over `LayoutGrid.java:876-1766` found one `return` inside a lambda (`cell == null ? null : ...`),
  one `continue` inside the tile loop, and no method-level exit. Both `if (ui == null) return;` and
  `if (ui.tilesAreSettled()) return;` are in `showWhenTilesAreReady`, which runs *after* the swap.
- **Nothing between reads the panel's contents.** `parent` is touched exactly twice in the interval,
  both `parent.setLayout(new FlowLayout(...))` (`:921`, `:926`). No component count, no size query,
  no listener attach, no other `add`. Every tile goes into `container`, not `parent`.
- **`container.equals(parent)` cannot be true** in the current source: the `container = parent;`
  branch is commented out (`:934`), and `container = newDiagramContainer()` (`:892`) is a fresh panel.
  So the swap is unconditional in practice. (It is nonetheless a branch the test does not assert
  about - see `VLD-C1`.)
- **No leak across rebuilds.** The panel can hold the old container and the old spinner during a
  build, and `removeAll()` clears both before `add(container)`. `VLD-C2` is about what that looks
  like, not about anything being left behind.

### The caller censuses - no deadlock, no reliance on the deferral

- **`refreshRouteList`**: 22 call sites read (21 in `TrainControlUI`, one in `RouteEditorFrame`).
  Every site that follows the call with more work either does the refresh **last**
  (`:17203`, `:17328`, `:18550`, `:18574`, `:22164`, `:23366`) or is on a worker thread where
  `singlePass` still defers (`:18476`, `:18550`, `:18574`, `:20156`, `:20164`, `:16884`). The two
  sites at `:25494` and `:25572` already wrapped it in `invokeLater`, which now collapses from two
  posts to one. `autonomyLoadedFromDiagram` (`:3628`) calls it first and does more afterwards, and I
  chased that as a suspected reorder: the only state the refresh reads that the rest of the method
  could change is `isAutonomyBusy()`, which is `getAutoLayout().isRunning()` on a `Layout` that
  `parseAuto` has just replaced (so false either way), and the `AltEmergencyStopActionPerformed`
  below it starts a thread and returns, so it was never synchronous with the refresh in the first
  place. **Not a defect.** The one real reorder found is `VLD-C3`.
- **`refreshLocSelectorList`**: 9 call sites. Every one of them calls it **last** in its sequence
  (`AddLocomotive.java:339`, `TrainControlUI.java:17005`, `:17329`, `:18195`, `:23367`, `:24145`,
  `:24313`, `:24767`, plus `LocomotiveSelector.init()`), so inline execution cannot see stale state.
- **Re-entrancy and deadlock.** `refreshLocSelectorList` is `synchronized` and now holds the monitor
  for the duration of the body; `filterLocList` is `synchronized` on the same object and is called
  from inside it, which is reentrant and fine. For a deadlock some thread would have to hold the
  `LocomotiveSelector` monitor while blocking on the event thread; no method on that class blocks on
  the event thread (both synchronized methods post and return when off it), so there is no such
  thread. `repaintLayout` is not called from inside any `singlePass` body. **I could not construct a
  deadlock and do not believe one exists**; this is reasoned from a complete read of the class's
  synchronized members, not from execution.
- The one re-entrancy that does happen - `autoRouteListMouseReleased` (`:23513`) calling
  `refreshRouteList` inline, which replaces the model of the very `JList` whose mouse event is being
  dispatched - was traced and is safe: the handler reads its indices before the call and does nothing
  with the list afterwards.

### AC2-C1's other edge cases - all handled correctly, by execution

Eight index shapes were put through `readLayoutIndexIds`, `readLayoutIndexExtras` and
`writeLayoutIndex`:

| case | result |
|---|---|
| empty file | ids `{}`, extras `[]`, clean file written |
| header only (`[gleisbild]`) | same |
| modelled header only | same |
| unmodelled block *after* the pages | preserved, **moved** to before them (content intact) |
| the same unmodelled block twice | both preserved, in file order |
| tab-indented key (`\t.name=`) | read, and normalised to a leading space |
| keys with no leading space | ids still read correctly; not mistaken for block names |
| blank line inside a `seite` block | does not end the block; id and name still read |

The `inPage` guard breaks no existing caller: a `[`-prefixed line and an empty line both leave
`inPage` alone, a key line is dispatched by the two guarded branches, and any other left-margin
token sets `inPage` false - which is the intended behaviour for `zuletztBenutzt`. The write order
(after the regenerated header, before the pages) matches where the shipped export has it, and
`readLayoutIndexIds` reads the result back correctly. The one thing that goes wrong is `VLD-B2`.

### AC2-C3, AC2-C4, RG4-C3, and the bundles - sound

- **AC2-C3** (`exportLocsToCSV` null-guarding `this.view`) is correct and `core.testLocDB` runs
  6/0/0 including the new `testTheLocomotiveCsvNeedsNoWindow`, whose precondition
  (`assertNull(model.getGUI())`) is a real precondition rather than a decoration.
- **AC2-C4** (`Util.getLatestReleaseInfo` to try-with-resources) is exactly equivalent plus
  close-on-throw. `CS2File.fetchURL` never returns null (`CS2File.java:409-423`), so the
  try-with-resources null case is unreachable and the old and new code fail identically if it ever
  did. **Not having a test is acceptable here**: the reader comes from a `static` call on a concrete
  class, so there is no seam to inject a throwing reader without changing the signature, and the
  three sibling conversions in `CS2File` are untested for the same reason. A test would be testing
  `try`-with-resources, not this method.
- **RG4-C3** is a comment correction only; the claim it now makes ("REPORTED, since ACC-B2") is
  true - `autosetup.ui.leftRouteActivations` exists in the bundle and is raised by
  `whatALegacyImportLeaves`.
- **Bundles.** All eight files are pure ASCII (0 bytes > 127, measured), key sets are identical
  across all eight (no missing, no extra), and `autosetup.ui.leftSignalAuthors` renders in every
  locale with both `{0}` and `{1}` substituted and the address list intact - checked by execution
  through `I18n.f` with `I18n.setLocale`, asserting that no placeholder survives. No straight
  apostrophe reaches `MessageFormat`: the English and French texts use `’`, and the other six
  avoid the character. The key *name* (`leftSignalAuthors`) is odd for a list of addresses, but it
  matches the review's language about a signal's red "author" and is not worth churning.
- **`build.xml`** carries `<test-one-class class="testTheRebuildIsOnePass"/>` among the other `ui`
  classes, and `regression.testEveryTestIsInTheBattery` runs 4/0/0 - both at baseline and again
  after my probes were deleted.

### What this pass did not reach

- **The `AxisRuler` and `CS2File` halves of `80249ebd`** (OB-172, the two-pixel spacer, and the
  probe count going from 3 to 6 in `LayoutGrid`). In the diff window but out of the brief.
  `ui.testTheDiagramPrintsItsCoordinates` runs 9/0/0 and `ui.testDiagramLooksRight` 20/0/0, which is
  the only thing I can say about them.
- **Anything on a real window.** No UI was opened. `VLD-B3`, `VLD-B4`, `VLD-C2` and `VLD-C6` are all
  about what a user sees, and none of them was seen - they are reasoned from source with the
  load-bearing lines quoted. On this codebase that is the weaker kind of finding, and they are
  graded on the strength of the quoted line rather than on the reasoning around it.
- **`AC2-C2`** (the `parseMags` per-record guard) was listed as fixed in the same commit but is not
  in my brief and was not checked.
- **The real parity data.** `compare.py` was exercised on synthetic TSVs only; I did not re-run it
  against the 2026-09-03 harness output, so I have verified that the new section computes and reads
  correctly, not that it says the right thing about Adam's railway.
- **Whether a 2.8.1 file in the wild carries a protocol-suffixed accessory name.** `VLD-B1` is
  reachable by construction (the program generates the name) but is not demonstrated from a user
  file.

### Housekeeping

Two probe classes (`test/core/vldProbeTemporary.java`, `test/core/vldProbeTemporary2.java`) were
written, run, and deleted; `regression.testEveryTestIsInTheBattery` was re-run afterwards and is
green, so nothing was left behind. No source file was edited. Noted in passing and **not caused by
this pass**: `cs2_sample_layout/config/autonomy/configuration-Main.json` and `setup.json` carry
uncommitted modifications with an mtime of 16:42, more than an hour before the first test run of
this pass (17:57) and before the last commit under review (17:52) - Adam operating the railway, as
the two predecessor reviews also recorded.
