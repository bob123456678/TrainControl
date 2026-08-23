# Three-day history review — 2026-08-20 to 2026-08-23

**Status:** open

**Prefix for citing this document: `TD`.** Severities use the house words from
[README.md](README.md) — High is an **A**, Medium a **B**, Low a **C** — but findings are numbered
`TD-1`, `TD-2`, … in rank order, following the shape
[2026-08-23-night-review.md](2026-08-23-night-review.md) used.

**Reviewed at `44f00cac`** (`autonomy-diagram-r0` HEAD), covering `git log f38cfa24^..HEAD` and
`git diff f38cfa24^..HEAD` restricted to `src/` and `test/`. 166 commits, all by Adam, over four
calendar days: 40 on 08-20, 34 on 08-21, 70 on 08-22, 22 on 08-23. In `src/`, 73 files, +15832/−10524.
In `test/`, 99 files, +12075/−1260, including 35 new test classes.

**Nothing was changed by this pass.** This document is the only file it wrote. No source or test file
was touched, nothing was compiled, and the test battery was not run — another process holds UDP 15730.

**Method, and why it is different from the other reviews in this folder.** The end state has already
been read by [2026-08-22-general-code-review.md](2026-08-22-general-code-review.md),
[2026-08-22-duplication-and-design.md](2026-08-22-duplication-and-design.md) and
[2026-08-23-night-review.md](2026-08-23-night-review.md). This pass read the *sequence* instead: for
each commit that fixed a rule, `git log -S` on a distinctive string from the fix to find where else
that rule lives and whether it was carried there; `git log --oneline -- <path>` to find the files
touched three or more times in three days; and each new test read against the file it scans, asking
what edit would make it pass while the defect returned. Fifteen findings. Every one is anchored to a
line in the working tree that I opened and read; where I could not run the path I say so in the
finding's confidence line.

**Three of these are worth reading tonight.** `TD-1` says the setup edit that
`autonomyEditorClosed`'s own comment names as *the* one that must trigger a rebuild does not trigger
one when it is made from the track diagram. `TD-2` says a link switched off between 08-17 and 08-23 is
still open in one direction, on every setup saved in that window. `TD-3` says the test guarding OB-040
is satisfied by a comment, so a comment edit breaks the build and the real regression does not.

---

## What the three days actually consisted of

Four strands, running at once.

**A diagram editor rebuilt around multi-select.** 08-20 and 08-21 are the layout editor: a selection
model, a drag grip, cut/copy/paste, bulk row and column edits, insert and delete, grow and shrink, and
the four shift operations. Almost every one of those had to be taught, separately, that the per-square
autonomy setup travels with the track. That lesson was learned nine times (`941070da`, `38f4fa89`,
`2804f93f`, `8663ab63`, `fb833330`, `6f9234d8`, `d4e9366e`, `1ef71465`, `d12858e9`), each time at one
more call site, and `6f9234d8`'s own message names the shape: *"a rule restated at a call site, which
is how the last three of these started."*

**A route editor rewritten twice.** The conditions went from a typed formula to lettered terms to a
nested outline over three consecutive commits on 08-20 (`7b503ec9`, `432f32dd`, `c27fa199`), then the
old editor and the legacy external tool were deleted (`28bdfcc8`), then the two editors' feature gaps
were closed (`ef33f4a8`). `RouteEditorFrame` was touched 32 times.

**A manual-test and issue ledger, with its own application.** `docs/manual-tests/` and `triage.py`
arrived on 08-22 (`b73789e2`, `272f6701`) and were then reworked eight more times the same day —
separate tabs, prefixes, states, colours, a filter, a compile button. This is the largest single
consumer of commits in the window and it produces no shipped code.

**Review, and re-review.** Six review documents were written and acted on inside the window. The
proportion of the window spent fixing defects introduced *inside* the window is the most striking thing
about it, and section "Where the defects came from" at the end says what I think that means.

GraphStream and the graph window were deleted (`d8db4879`, −1201 lines), org.json and FlatLaf were
upgraded (`1efa3b9a`), and the test tree was reorganised into `core/ui/regression/support` with a
guard that the battery list stays complete (`34ae94ad`, `ae94421a`, `9f7dab8c`).

---

## Findings, ranked by expected harm

| # | Finding | Severity | Confidence |
|---|---------|----------|------------|
| TD-1 | Making a square a station, or changing where trains may turn, from the **track diagram's** Autonomy menu writes the setup and never rebuilds the running layout — the exact edit `autonomyEditorClosed`'s comment names as the one that must | Medium | Medium-high |
| TD-2 | A link switched off is only switched off at the end you clicked. The mutuality rule landed in the writer on 08-23; every reader, and the load path, still ask the near end | Medium | High (code) / Medium (reach) |
| TD-3 | `testTheSignalFocusIsAlwaysTurnedOffAgain` passes because the word "finally" appears in a **comment**. The `finally` keyword is not in the window it searches | Medium | High |
| TD-4 | OB-026 was fixed in `TileOverlay.paintRun` and left unfixed in `TileAnnotation.paintTraces` — the sibling painter, in the same file as the public helper the fix created | Low | High |
| TD-5 | `testConfirmedGoodState` skips on 100% of runs — no baseline is committed — and is counted in the "89 classes clean" figure the commits cite as evidence | Medium | High |
| TD-6 | `testEachPositionKeepsItsOrderThroughTheRouteText` compares a string against a concatenation of itself. Reversing the three-way point's command order — the hardware hazard it names — leaves it green | Medium | High |
| TD-7 | The release jar globs `resources/*.jar`, and the superseded FlatLaf, org.json and all three GraphStream jars are still on disk. `1efa3b9a`'s upgrade is not reliably in the shipped artefact | Medium | High (both packaged) / Medium (which wins) |
| TD-8 | The setup-side home editor has no "one locomotive, one station" rule. `Layout.rebuildHomeStations`'s comment says only a hand-edited file can reach that state; a menu can | Medium | High |
| TD-9 | `rebuildRunningLayoutFromSetup` was extracted so both callers could share it. `autonomyEditorClosed` still holds a verbatim copy — and the last code commit wrote its 10-line comment out twice rather than delete one | Low | High |
| TD-10 | Nine `if (!file.isFile()) return;` branches in tests whose entire value is reading that file. The correct pattern was written twice in this same window | Low | High |
| TD-11 | `TileAnnotation.editing` changes what is painted and is absent from `equals`/`hashCode`, which `setAutonomyAnnotation` uses as its "has anything changed" test — the exact omission the file's own OB-007 comment warns about | Low | High (code) / Low (reachable today) |
| TD-12 | The four shift operations' javadoc says they are kept out of autonomy mode because the setup keys do not move. The code under it moves them. Two of the four also carry a bounds guard the other two do not | Low | High / Medium |
| TD-13 | OB-032's guard was written, documented and never called. `hasItemsBesidesTitle` has zero callers; `menu.add(connections)` is unconditional; the commit's message says the defect is fixed | Low | High |
| TD-14 | `writeAtomically`'s entire rationale — the discipline protecting `LocDB.data`, `UIState.data` and `autonomy.json` — is attached to `sanitizeFilename`, and `writeAtomically` has none. One of 95 orphaned javadoc blocks in `src/` | Low | High |
| TD-15 | The `Badge` legend still describes the four-shape vocabulary that `4d069a91` replaced. Two of the four cells are now inverted — a plain point is a circle, and a diamond means "may turn" | Low | High |

---

## TD-1 — the split-changing edit that does not rebuild, on the surface people actually use

**Severity: Medium. Confidence: medium-high** — the call chain is read, not run. I could not execute
it, and the conclusion depends on nothing else in the window subscribing to a setup change; I found no
such subscriber, but absence of a listener is harder to prove than presence of one.

**Commits.** `37009269` (OB-034, extracts the rebuild), `c7fb3019` (OB-035, placement),
`8ede34f6` (OB-039, facing), `8a5e1951` (MT-125, both surfaces), `68123083` (NR-1). Five commits in
two days, all on the same seam.

**Where.** `src/org/traincontrol/gui/AutonomyEditorPanel.java:964`, `:968`, `:974` (the station
radios), `:1014`, `:1018`, `:1022` (the turning radios), through `radio()` at `:2089`, into
`setUsage()` at `:2157`, `setStation()` at `:2166` and `setTurning()` at `:2116`. Compare
`placementChanged()` at `:2643`.

**What the window built.** `placementChanged` (`:2643`) now does all three things — refresh the
editor's grid, rebuild the running layout, refresh the panel:

```java
if (onDiagramChanged != null) onDiagramChanged.run();

if (parentWindow() != null) parentWindow().rebuildRunningLayoutFromSetup();

refresh();
```

That is MT-125's fix, and it is correct. It is reached from three callers: placing a locomotive
(`:2609`), clearing one (`:881`), and setting a facing (`:1994`).

**What is wrong.** `radio()` at `:2089` does not go through it:

```java
menuItem.addActionListener(e ->
{
    action.run();
    refresh();

    flashMenuTarget();
});
```

`refresh()` (`:4877`) re-reads the setup and rebuilds the panel's findings list. It does not touch the
running layout, and it does not call `onDiagramChanged` either. `toggle()` at `:1541` and `item()` at
`:1473` are the same.

The radios that reach `setStation` and `setTurning` are **not** behind `!menuOnly`. The `!menuOnly`
guard in this menu closes at `:921`; the station group starts at `:950` and the turning group at
`:1005`. So both appear on the track diagram's own right-click Autonomy menu — and on that surface
nothing else picks the change up. Inside the editor the rebuild is deferred to closing, deliberately
(`TrainControlUI.autonomyEditorClosed`), so the editor is covered. The diagram's own menu is never
closed and has no such moment. Its `onDiagramChanged` is set (`TrainControlUI.java:2746`) but is only
`repaintLayout()` — a repaint of tile art, not a rebuild — and `radio()` does not call it in any case.

Both of these edits change what the builder emits, and the turning one changes it structurally. I
checked which flags reach `AutonomyBuilder.nodesFor` (`:462`): the split is driven by the arrival sides
and by `reversible`/`mandatory` (`:479`–`:480`) — the two flags `setTurning` writes — so changing where
trains may turn genuinely changes how many Points a square becomes and what they are called. Station-ness
is narrower: it feeds `stops` at `:779`, so a square just made a station is emitted by the running
layout as a Point that does not accept trains. The symptom is the same family either way — a station
that cannot be chosen as a destination and has nothing to place a locomotive on, until a restart.

`TrainControlUI.autonomyEditorClosed` (`:3521`) states the consequence in words, and names the turning
edit as its example:

> *"anything that changes how a square SPLITS does not: turning a terminus into a may-turn station
> changes how many copies that square becomes and what they are called, and from that moment the
> running layout holds names the setup no longer knows. … The caption looks up its station and finds
> nothing, so the label goes blank; the right-click menu looks up the Point for that square and finds
> none, so there is nothing to place a locomotive on."*

That paragraph was written to justify the rebuild at the end of `autonomyEditorClosed`. `37009269`
then lifted the rebuild into `rebuildRunningLayoutFromSetup` (`TrainControlUI.java:3460`) precisely
*"so that an edit made from the DIAGRAM's own menu can ask for it too"* — and the menu item whose
example the comment uses still does not ask.

**What I would do.** Route `radio` and `toggle` through `placementChanged()` rather than `refresh()`,
or — better, because it stops the next item having to remember — give `AutonomySession` a single
"the setup changed structurally" notification and have the three menu-item helpers raise it. There is
already a list of the keys that matter: `AutonomySession.POINT_OPERATIONAL_KEYS` at `:1634`. The
narrow fix is three lines; the durable one is that nothing in `AutonomyEditorPanel` should be able to
write the setup without the surfaces being told.

---

## TD-2 — a doorway shut at one end, on every setup saved in the last week

**Severity: Medium. Confidence: high on the code, medium on the reach** — I have verified that the
writer only became mutual on 08-23 and that no migration exists, but I cannot see Adam's saved
configurations to say how many carry a one-ended disable.

**Commits.** `ed47019f` (2026-08-17, introduces "tell autonomy to ignore this link"), `d12858e9`
(2026-08-23, OB-041, makes the *writer* mutual).

**Where.** Writer: `src/org/traincontrol/automationui/AutonomyCompanionStore.java:694`. Load path:
`:2268`. Copy into the graph: `:2190`–`:2194`. Readers: `src/org/traincontrol/automationui/TileGraph.java:574`
(`exits()`), `:699` and `:737` (validation), `:1203` (`neighbours()`), and
`src/org/traincontrol/gui/AutonomyEditorPanel.java:4658` (`isPairedPortal`).

**What changed.** Before `d12858e9`, `setPortalDisabled` was:

```java
public void setPortalDisabled(TileKey tile, boolean disabled)
{
    if (disabled) disabledPortals.add(tile.toString());
    else disabledPortals.remove(tile.toString());
}
```

After it, the same method also sets the partner, with a javadoc (`:680`–`:692`) that states the rule
as a property of the model:

> *"A pair of links is one doorway with an end in two places, and autonomy walks through it in both
> directions. A doorway shut at one end and open at the other is not half shut — it is a route that
> exists going one way and not the other, which nothing on the diagram says and no train can be told."*

**What is wrong.** The rule is enforced at exactly one writer, and nowhere else:

- **The load path does not migrate.** `:2268` is `readStringSet(root, "disabledLinks", disabledPortals);`
  — a verbatim read. `reconcile` (`:2071`) only drops members whose square is gone. So a file written
  by any build between 2026-08-17 and 2026-08-23 — which is the whole period during which the feature
  existed and the only way to use it produced a one-ended disable — loads with one end shut.
- **The graph copies it one for one.** `:2190`–`:2194` iterates `disabledPortals` and calls
  `graph.disablePortal(tile)` per key. The asymmetry survives into the graph verbatim.
- **Every reader asks the near end only.** `TileGraph.java:574` is
  `if (stub != null && portals.containsKey(tile) && !disabledPortals.contains(tile))`, and `:1203` is
  `if (partner != null && !disabledPortals.contains(here.tile) && tiles.containsKey(partner))`.
  Neither consults `portals.get(tile)`.

The consequence is the one the javadoc describes, still live: from the end that was *not* clicked,
`exits()` offers the portal and `neighbours()` walks it, so autonomy plans and drives paths through a
link the operator switched off. On a railway where a link often means "this section is not wired for
autonomy", that is a train sent somewhere it was deliberately excluded from.

`AutonomyEditorPanel.isPairedPortal` at `:4658` has the same one-sidedness in the drawing: the far end
still draws as a live two-way door.

**What I would do.** Move the rule to the reader, which is where it cannot be forgotten:
`isPortalDisabled(tile)` returning `disabled(tile) || disabled(partner(tile))`, and have `TileGraph`'s
four sites call that instead of touching the set. That closes the old files too, without a migration
step — which is the argument for doing it at the reader rather than at load.

---

## TD-3 — the test that is satisfied by a comment

**Severity: Medium. Confidence: high** — measured, not inferred.

**Commit.** `8ede34f6` ("Three editor defects, and each was a decision written down twice or not at
all"), OB-040.

**Where.** `test/regression/testEditorSurfaceRules.java:134`–`:150`, against
`src/org/traincontrol/gui/AutonomyEditorPanel.java:3051`–`:3059`.

**What it does.**

```java
int at = source.indexOf("signalWindowOpen = false");

assertTrue(at > 0, "nothing ever turns the signal focus off (OB-040)");

String before = source.substring(Math.max(0, at - 400), at);

assertTrue(before.contains("finally"), ...);
```

**What is wrong.** In the production file, the `finally` keyword is at `:3051`, the assignment at
`:3059`, and between them sits a five-line comment. I counted the occurrences of the string `finally`
in the 400 characters preceding the assignment: **one**, at 373 characters back, and it is this:

```java
// Cleared HERE, in the finally, and not in the button handlers.
```

The `finally` keyword itself is further back than 400 characters and is **not in the window at all**.
So the assertion is currently satisfied by the comment and by nothing else, and it fails in both
directions:

- Move the clear into `Done`'s handler — *"the natural-looking edit that would break it"*, in the
  test's own javadoc — and a careful author moves the explanatory comment with it. Green, OB-040 back.
- Delete or reword that one comment line, changing no behaviour, and the build fails.

This is not a hypothetical class of error here: `testEditorSwitchClearsPageState.java:300` and
`testNoSelfRecursiveWrappers.java:84` both strip comments before matching, and
`testEditorSwitchClearsPageState`'s does so *because it was bitten by exactly this*. The fix is
already written twice in the repository; this file has neither copy.

**What I would do.** Two changes, and the second matters more than the first. Strip comments before
the search, as the two sibling tests do. Then stop asserting on source text for this at all — the
property is "after `pairProtectingSignal` returns, however it returns, `signalWindowOpen` is false",
and that is a behavioural test: call it with an `askAboutProtectingSignals` that throws, and assert
the flag is clear.

---

## TD-4 — OB-026 fixed in one painter and left in its sibling

**Severity: Low. Confidence: high.**

**Commits.** `c83ca493`, `430b0524`, `a3a5cc54`, `3017f719` — four commits over 08-22/08-23 to
diagnose and fix one drawing defect.

**Where.** Fixed: `src/org/traincontrol/automationui/TileOverlay.java:377` (`paintRun`), which
takes its centre at `:392` from `middle` at `:371`. Unfixed: `src/org/traincontrol/automationui/TileAnnotation.java:1048`, consumed at
`:1081`–`:1082` and `:1129`–`:1130`. The helper both need is `TileAnnotation.trackCentre` at `:1587`.

**What changed.** OB-026 was "the end of a run stops in the middle of the square rather than on the
rail". `3017f719` made `TileAnnotation.trackCentre` **public** and threaded it through `LayoutLabel`
into `TileOverlay.paint`, so the run overlay uses the track midpoint instead of the tile centre for a
segment with a null side. Its javadoc at `:1579`–`:1581` says:

> *"Public because the running overlay needs it too: the end of a run has one side and a null, and
> without this it stopped in the middle of the square (OB-026). … so the run line and the badge now
> agree about where the track is."*

**What is wrong.** `TileAnnotation.paintTraces` — the *editor's* "Test a run" trace, drawn on the same
squares, in the same class as `trackCentre` — still does:

```java
int[] centre = new int[] {width / 2, height / 2};
...
int[] a = trace.from == null ? centre : midpoint(trace.from, width, height);
int[] b = trace.to   == null ? centre : midpoint(trace.to,   width, height);
```

Null sides are not hypothetical here: `AutonomyEditorPanel.java:4349` builds them deliberately —
*"Null at the ends of the run, where the line stops in the middle of the square"* — for exactly the
first and last square of a traced run. So the editor's yellow trace has the defect that was just
fixed in the running overlay, on a bend, and the fix's own javadoc claim ("the run line and the badge
now agree") is false for this painter.

The pixel test written for OB-026,
`test/ui/testDiagramLooksRight.java:182 testEveryPixelOfARunLandsOnTrack`, drives `TileOverlay` only,
so nothing notices.

**What I would do.** Replace `:1048` with `trackCentre(width, height)`. It is one line, it is a method
on `this`, and the same call would fix `heading()` at `:1205`, which also falls back to the tile
centre.

---

## TD-5 — a test in the battery that has never compared anything

**Severity: Medium. Confidence: high.**

**Commit.** `44f00cac`, the HEAD commit.

**Where.** `test/regression/testConfirmedGoodState.java:94`, `:153`–`:161`, `:239`–`:242`. Listed in
`build.xml`, so `ant test` runs it.

**What is wrong.** Both `@Test` methods skip on every run:

```java
File from = capturing ? new File("cs2_sample_layout") : LAYOUT;   // LAYOUT = test/baseline/layout

if (!from.isDirectory())
{
    throw new SkipException(capturing ? ... : "no blessed baseline yet - capture one with ...");
}
```

`test/baseline` does not exist on disk and `git ls-files test/baseline` returns nothing. The commit
message is explicit — *"NO BASELINE IS COMMITTED … Until one exists the test skips"* — and the reason
given is right: blessing an unconfirmed railway is worse than having no baseline.

The problem is not the decision, it is that nothing tracks it. `testEveryTestIsInTheBattery` checks
that a class is *named* in `build.xml`, never that it asserts anything, so 276 lines and one class are
now counted in the "89 classes clean" and "Lite battery 83 classes clean" figures the commits cite as
their evidence. A number that includes a class which cannot fail is a slightly worse number than it
was yesterday, and nothing on screen says which classes contributed.

There is a second, sharper edge at `:94`:

```java
if (session.getReducer() == null) throw new SkipException("the baseline reduced to nothing");
```

A reduction that collapses to nothing is one of the regressions a baseline exists to catch. Once a
baseline is blessed, that line converts the loudest possible failure into a skip.

**What I would do.** Two things. Turn `:94` into an assertion — once there is a baseline, "it reduced
to nothing" is a failure, not an absence. And make the skip visible: either an entry in
`docs/manual-tests/tests.md` that stays open until the baseline is blessed, or a second `@Test` that
fails once the railway's open-issue count reaches zero and no baseline exists. Right now the only
record that this is pending is a commit message.

---

## TD-6 — the three-way point order test compares a string with itself

**Severity: Medium. Confidence: high.**

**Commit.** `3019f46f`.

**Where.** `test/regression/testRouteEditorRoundTripCases.java:155`–`:176`; the assertion is `:168`.

**What is wrong.**

```java
String first  = pair.get(0).toLine(null).trim();
String second = pair.get(1).toLine(null).trim();

String asARoute = pair.get(0).toLine(null) + pair.get(1).toLine(null);

assertTrue(asARoute.indexOf(first) < asARoute.indexOf(second),
    position + ": the two commands changed places in the route text, so the point is "
    + "thrown before it is released");
```

`asARoute` is built from the same list, in the same index order, that `first` and `second` are read
from. If `ThreeWaySwitch.expand` returned its pair the wrong way round, `first`, `second` **and**
`asARoute` all swap together and the comparison is unchanged. The assertion is order-invariant by
construction; it cannot fail for the reason its own message gives.

This matters more than a normal vacuous test because of what it claims to guard. A three-way point is
two motors on consecutive addresses, and the order is a hardware instruction — the message says so:
*"the point is thrown before it is released … and settles wherever the hardware happens to finish."*
That is real ironwork.

The property is genuinely tested elsewhere — `test/core/testThreeWaySwitch.java:38`–`:46` and
`test/ui/testRouteEditorValidation.java:109`–`:119` — so the rule is not unguarded. Only this copy is
inert, and its javadoc ("each position's pair round-trips as a route") promises a round trip that does
not happen: nothing parses the text back.

The second assertion in the same loop (`:172`, that the pair did not run onto one line) is live.

**What I would do.** Either delete the method and let `testThreeWaySwitch` own the rule, or make it a
real round trip: render the pair, parse it back through the route reader, and assert the parsed
commands come back in the order they went out. The second is worth doing — the parse direction is not
covered anywhere, and `4c8bd296` and `ef33f4a8` both touched it in this window.

---

## TD-7 — the release jar packages the superseded libraries

**Severity: Medium. Confidence: high that both versions are packaged; medium on which one wins.**

**Commits.** `1efa3b9a` (upgrades org.json and FlatLaf), `d8db4879` (deletes GraphStream).

**Where.** `build.xml:232`–`:251`, specifically `:241`:

```xml
<zipgroupfileset dir="resources" includes="*.jar"/>
```

**What is wrong.** Both commits removed the old jars from git without removing them from the working
tree. `git status` reports them as untracked and their file times are unchanged from when they were
first added:

```
resources/flatlaf-3.5.4.jar    907663  Dec 28  2024
resources/flatlaf-3.7.2.jar   1016186  Aug 21 02:35
resources/gs-algo-2.0.jar     8108677  May  6  2023
resources/gs-core-2.0.jar      916101  May  6  2023
resources/gs-ui-swing-2.0.jar  428322  May  6  2023
resources/json-20251224.jar     87620  Apr 17 05:14
resources/json-20260814.jar     89800  Aug 21 02:35
```

`package-for-store` globs the whole directory, and `Readme.md:306` says the distributed artefact is a
single `TrainControl.jar` — which is what that target produces. So a release built on this machine
today contains two copies of `com/formdev/flatlaf/*`, two of `org/json/*`, and 9.4 MB of GraphStream
for code deleted in this window. Which FlatLaf and which org.json actually load is decided by zip
entry order, and alphabetically `flatlaf-3.5.4.jar` and `json-20251224.jar` come first — so the more
likely outcome is that the upgrade in `1efa3b9a` is *not* in the shipped jar at all.

`.gitignore` was added in this window (`b73789e2`) and does not cover them.

**What I would do.** Delete the four superseded files from `resources/`, and then stop the class of
error rather than the instance: change `:241` to name the two jars explicitly, matching
`nbproject/project.properties:34` and `:36`, so that adding a jar to the directory cannot change what
ships. A one-line sanity check is available before either: build the store jar and look at its size —
if it is ~9 MB larger than the last release, the GraphStream jars are in it.

---

## TD-8 — one locomotive, one station: enforced at one door of two

**Severity: Medium. Confidence: high.**

**Commit.** `8e3066bc` (OB-022, "three safety rules come back to the door people actually use").

**Where.** `src/org/traincontrol/gui/AutonomyEditorPanel.java:2299` (`homeOf`), `:2307`–`:2340`
(`promptHome`). The rule that is missing: `src/org/traincontrol/automation/Layout.java:867`–`:888`
(`setHomeLocomotive`). The comment that is now wrong:
`src/org/traincontrol/automation/Layout.java:811`–`:817`.

**What changed.** DD-A6 found that `HomeLocomotiveMenu` had lost four of its five callers when the
graph window was deleted, taking three rules with it. `8e3066bc` brought three of them to the live
door: a retired home stays in the list, an unreachable home warns, and excluding a locomotive from its
own home warns. That work is sound and I checked all three.

**What is wrong.** A fourth rule was not on the list. The running layout enforces one station per
locomotive at `Layout.java:879`–`:885`:

```java
if (locName != null)
{
    for (Point other : this.points.values())
    {
        if (other != p && locName.equals(other.getHomeLoc())) other.setHomeLoc(null);
    }
}
```

The setup path does not. `promptHome` ends with `session.setPointProperty(tile, "home", picked)`
(`:2337`), and `AutonomySession.setPointProperty` (`:3084`) writes the JSON key and derives the
station index — nothing more. `AutonomyChecks` has exactly one home rule,
`checkHomesThatNeedReversing` (`:521`); there is no duplicate-home check. So two squares can be given
the same home from the menu, silently.

What happens then is at `Layout.rebuildHomeStations:811`:

```java
if (this.homeStations.containsKey(l))
{
    // Dropped, for the reason a dangling name is dropped: it can never be honoured.  One
    // locomotive has one station - setHomeLocomotive enforces exactly that when an
    // assignment is made - so only a hand-edited file reaches here, and keeping the loser
    // would re-warn on every load and be written back out on every save.
    this.control.logf("autolayout.warnHomeLocomotiveAssignedTwice", ...);
    p.setHomeLoc(null);
```

The comment is from `3c7ff3e1` (2026-07-28); the setup-side home editor arrived on 2026-08-16 and got
its safety rules on 2026-08-22. *"Only a hand-edited file reaches here"* is no longer true — a menu
reaches it — and which of the two assignments survives is decided by `points.values()` iteration
order, with a log line as the only notice. One of the two homes the operator set is discarded on load.

**What I would do.** Add the rule to `promptHome`, next to the two warnings `8e3066bc` already put
there, and make it a *warning* for the same reason those are: say "BR 218 is already the home of
Sonnenberg — move it here?" rather than refusing. Then correct the comment at `Layout.java:813`,
because a reader trusting it will not look for this.

---

## TD-9 — the extraction that left the original behind, and then had to be fixed twice

**Severity: Low. Confidence: high.**

**Commits.** `37009269` (extracts `rebuildRunningLayoutFromSetup`), `68123083` (NR-1, has to patch
both copies — *"The reviewer found one site; there were two"*).

**Where.** `src/org/traincontrol/gui/TrainControlUI.java:3460`–`:3477` and `:3539`–`:3554`.

**What is wrong.** `rebuildRunningLayoutFromSetup` exists, and its javadoc at `:3443` says why:

> *"The same thing autonomyEditorClosed does at the end, lifted out so that an edit made from the
> DIAGRAM's own menu can ask for it too."*

`autonomyEditorClosed` does not call it. It contains the body verbatim. I diffed the two sixteen-line
blocks: they differ by one comment line and a closing brace. Both carry the same ten-line explanation,
word for word, of why the load must skip the capture step.

And that explanation was added by `68123083` — the last code commit in the window — which wrote it
into *both* places rather than deleting one. The commit's own message says the reviewer found one site
and there were two; the reason there were two is this duplication, and it is still there, so there
will be two again.

**What I would do.** Replace `:3539`–`:3554` with `rebuildRunningLayoutFromSetup();`. The preceding
comment at `:3524`–`:3538`, which explains what a split is and why it matters, is the one worth
keeping — the javadoc on `rebuildRunningLayoutFromSetup` already quotes it, and quoting a comment from
another file is a third copy waiting to drift.

---

## TD-10 — nine silent-pass branches in tests whose only job is to read a file

**Severity: Low. Confidence: high.**

**Where.**

| File | Lines | Scanned |
|---|---|---|
| `test/regression/testEditorSurfaceRules.java` | 59, 102, 136, 162, 236, 280 | `AutonomyEditorPanel.java`, `LayoutGrid.java` |
| `test/regression/testStoreCollectionsAreHandledEverywhere.java` | 162, 198 | `AutonomyCompanionStore.java` |
| `test/regression/testHomeAssignmentRules.java` | 124 | `AutonomyEditorPanel.java` |
| `test/regression/testTriggerWaitsSayNothing.java` | 185 | all of `src/` |

Each is `if (!PANEL.isFile()) return;` or the directory equivalent. Rename `AutonomyEditorPanel`, or
move it to another package, and six tests in `testEditorSurfaceRules` go green while checking
nothing — including the OB-039 facing rule and the MT-116 station-label rule. This is not a remote
scenario in a window that moved every test file into a new folder (`34ae94ad`) and renamed two methods
in `TileGraph` (`e5f77c9c`).

**What makes this a finding rather than a nit** is that the correct pattern was written twice in the
same three days, by the same author:

```java
// test/regression/testEditorSwitchClearsPageState.java:268
assertTrue(file.exists(), "cannot find " + file.getAbsolutePath()
    + " - this test reads the source, so it has to run from the project root");
```

`testEveryTestIsInTheBattery.java:50` does the same for `build.xml`. The nine sites above are the
inherited idiom (`testNoSelfRecursiveWrappers.java:43`, which predates the window) copied forward
without picking up the correction.

**What I would do.** Change all nine to the `assertTrue(... .exists(), ...)` form. It is mechanical and
it is the difference between a suite that reports a rename and one that goes quiet about six rules.

---

## TD-11 — a field that changes the drawing, absent from the equality that decides whether to redraw

**Severity: Low. Confidence: high on the code fact, low that it is reachable today.**

**Commit.** `6e8edee6` ("The badge only leaves the track in the editor").

**Where.** `src/org/traincontrol/automationui/TileAnnotation.java:535` (`inTheEditor()`), `:542`
(the field), `:609` (`isBlank`), `:1675` (`equals`), `:1691` (`hashCode`). The consumer is
`src/org/traincontrol/gui/LayoutLabel.java:919`–`:929`.

**What is wrong.** `editing` decides whether a station's badge moves out to the corner on a bend — it
changes the pixels. It is not in `equals` and not in `hashCode`. `LayoutLabel.setAutonomyAnnotation`
uses `equals` as its "has anything changed" test:

```java
if (effective == null ? autonomyAnnotation == null
    : effective.equals(autonomyAnnotation)) return;
```

So two annotations that differ only in `editing` are indistinguishable to the repaint decision.

**Why the confidence on reachability is low.** I traced every producer. `inTheEditor()` has exactly one
caller, `AutonomyEditorPanel.java:4642`, and it is unconditional there; `DiagramTileRegistry` skips
labels in edit mode at `:94` and `:179`. So the two populations of labels are segregated today and I
could not construct a live path. This is a trap, not a defect.

**Why it is worth writing down anyway.** The file's own comment at `:600`–`:607`, twelve lines above
`isBlank`, is about precisely this:

> *"The field had been added to equals and to hashCode. It is the method that decides whether the
> object is worth drawing at all that tends to be missed, because it is not one anybody is looking at
> while adding a field."*

That comment records OB-007. `6e8edee6` — which added `editing` — is a later commit than the one that
wrote it, and made the mirror-image omission twelve lines away from the warning. The lesson was
written down and then not applied by the next person to add a field, who was the same person.

**What I would do.** Add `editing` to `equals` and `hashCode`. Leave `isBlank` alone: `editing` alone
paints nothing, so excluding it there is correct and worth one line saying so.

---

## TD-12 — the shift operations' javadoc, and two guards out of four

**Severity: Low. Confidence: high on the comment, medium on the guard.**

**Commit.** `2804f93f` ("The editor opens again, and the setup no longer leads the track").

**Where.** `src/org/traincontrol/gui/LayoutEditor.java:3212`–`:3227` (the javadoc), `:3240` and
`:3327` (the guards), `:3282` and `:3369` (no guard).

**The comment.** The javadoc over `shiftUp` ends:

> *"Worth knowing, and the reason these do not appear in autonomy mode: everything the autonomy setup
> holds about a page is keyed by SQUARE, and shifting the diagram moves the track without moving those
> keys. See the note on growEdges, which is why THAT one only ever grows at the right and the bottom."*

Eighteen lines below it, in the same method, `2804f93f` added:

```java
autonomy.moveTiles(moving);

rememberAutonomy(autonomy);
```

with its own comment saying the opposite — *"These four move every tile past one square and told the
setup nothing at all … every station, name, facing and restriction left behind on coordinates the
track had walked away from."* The stated reason for a UI decision is now false, and it is stated as
established fact ("the reason these do not appear in autonomy mode"), which is the form a reader
trusts without checking.

**The guards.** `shiftUp` (`:3240`) and `shiftLeft` (`:3327`) refuse on the last row/column, with a
nine-line comment explaining that `LayoutDiagram` normalises an out-of-range start to the *first*
row — *"the track moved and every station, name, length and pairing on the page stayed on the square
it used to be on … the whole page's setup was simply attached to the wrong tiles, silently."*
`shiftDown` (`:3282`) and `shiftRight` (`:3369`) have no such guard, and
`LayoutDiagram.java:686`, `:715`, `:749`, `:779` show all four normalising identically.

I worked through it and I believe the asymmetry is **defensible**: `shiftDown` and `shiftRight` call
`addRowsAndColumns` *before* the range check, so the threshold has already grown by one and
`startRow > sy - 2` can no longer be reached from a hover. The destructive pair got the guard; the
additive pair does not need it. **This is a smell, not a proven defect** — but nothing says so, the
argument depends on a side effect two files away, and this exact asymmetry produced a silent
whole-page corruption three days ago.

**What I would do.** Delete the two stale sentences from `:3224`–`:3227` and replace them with what is
true now: the setup follows the shift, and `growEdges` grows at the right and bottom for a different
reason. Then add one line to `shiftDown` and `shiftRight` saying why they do not need `shiftUp`'s
refusal — or add the refusal anyway, which costs nothing and removes the question.

---

## TD-13 — the guard for OB-032 was written, documented, and never called

**Severity: Low. Confidence: high** — `grep -rn 'hasItemsBesidesTitle' src/ test/` returns one line,
its own declaration.

**Commits.** `0651d57c` (removes the last item added outside the gate), `be749d46` (writes the helper
and reports OB-032 as fixed).

**Where.** `src/org/traincontrol/gui/AutonomyEditorPanel.java:1452`–`:1470` (the helper), `:1178`
(`connections` created), `:1181` (`title(connections, ...)`), `:1193` (`if (session.canCarryDirection(target))`),
`:1214`–`:1261` (every `connections.add(...)`, all inside that block), `:1279`
(`menu.add(connections);`, unconditional).

**What is wrong.** `be749d46`'s message says:

> *"OB-032. A 'Trains May Depart' submenu holding nothing but its own heading is no longer added. The
> check has to ignore the heading — `title()` puts one disabled item in, so `getItemCount() == 0` is
> never true — which is the same reasoning as the popup guard in `LayoutLabel`, where an empty menu
> 'reads as a fault'."*

The reasoning is correct and the helper implementing it is correct. Nothing calls it. `connections` is
seeded with a bold disabled heading at `:1181`, every item that could go into it is inside
`canCarryDirection` at `:1193`, and it is added to the parent menu unconditionally at `:1279`.

`AutonomySession.canCarryDirection` (`:2440`) returns false for any portal tile and for any tile with
no routes. A link is exactly that case — the comment at `:1185`–`:1192` says so — so right-clicking a
link opens a "Trains May Depart…" submenu containing one greyed word and nothing else. That is OB-032,
on a square type the surrounding comment names.

Note the shape rather than the severity: the guard exists, is documented, cites the ticket, and is
dead. `AutonomyEditorPanel` is 5000+ lines and was touched 40 times in three days; a private method
with no callers produces no warning in this build.

**What I would do.** `if (hasItemsBesidesTitle(connections)) menu.add(connections);` at `:1279`. Then
ask the wider question, because this is the second dead-guard finding in a fortnight after DD-A6:
nothing here notices an unreachable private method. `testEditorSurfaceRules` already reads this file;
one more assertion — that every `private` method in it is named somewhere else in the file — would
have caught both.

---

## TD-14 — the data-safety rationale is attached to the wrong method

**Severity: Low, as a defect. Confidence: high.** I rank it last on harm and first among the Lows on
importance, for the reason in the last paragraph.

**Commit.** `d6b9b00c`.

**Where.** `src/org/traincontrol/util/Util.java:210`–`:230` (the block), `:231`–`:251` (the block that
actually attaches), `:253` (`sanitizeFilename`), `:260` (`writeAtomically`).

**What is wrong.** The javadoc explaining why the locomotive database, the UI state and `autonomy.json`
must be staged in a sibling file and moved into place — including its `@param target`, `@param body`
and `@throws IOException` — is immediately followed by a second javadoc, for `sanitizeFilename`. In
Java only the second attaches. So:

- `sanitizeFilename(String name)` at `:253` is preceded by two doc comments, the first of which
  describes a completely different method and promises parameters it does not have.
- `writeAtomically(File target, StreamWriter body)` at `:260` — the primitive that stands between the
  operator's accumulated work and a truncated file — has **no javadoc at all**.

The stranded text is the best paragraph in the file:

> *"an unreadable database reads as a first launch, and the next Central Station sync repopulates the
> locomotive list so the customizations look mislaid rather than destroyed … REPLACE_EXISTING rather
> than ATOMIC_MOVE: the rename window is nanoseconds against a write window of milliseconds to
> seconds."*

Anyone who opens `writeAtomically` to change it meets an undocumented eight-line method and will not
find that reasoning unless they scroll past an unrelated method to get to it.

**This is one of 95.** I counted the doc comments in `src/` that are immediately followed by another
doc comment, so that the first attaches to nothing: **95**. The mechanism is always the same — an
insertion lands between a javadoc and its declaration, and nothing warns. Several are harmless
(`item()`'s two descriptions sit above `hasItemsBesidesTitle` and still describe `item()` accurately).
Several are not: `Layout.java:4565` puts `locomotiveInBlock`'s `@param`/`@return` above
`refreshProtectingSignal`, which commands real signals;
`AutonomyCompanionStore.java:1378` puts `moveTiles`'s whole "a tile that moves leaves its setup
behind" rationale above `forgetTiles`, leaving `moveTiles` undocumented;
`TileAnnotation.java:1353` heads `paintBadgeOverRun` with a legend for `paintBadge`, which has none.

**What I would do.** Fix this one by hand — it is the data-safety primitive. Then make the class
mechanical: a five-line check in the test suite that finds a `/** … */` immediately followed by
another and fails, run once with the 95 recorded as a baseline that must not grow. In a codebase where
the comment *is* the safety mechanism, a comment attached to the wrong member is the same kind of
defect as a guard on the wrong branch.

---

## TD-15 — the badge legend describes the vocabulary that was replaced

**Severity: Low. Confidence: high.**

**Commit.** `4d069a91` ("One grid of shapes: what turning means, and whether it is a station").

**Where.** `src/org/traincontrol/automationui/TileAnnotation.java:140`–`:148` (the `Badge` javadoc),
against `:1502`–`:1537` (`paintBadge`).

**What is wrong.** The `Badge` class javadoc — the place a reader goes to learn what the marks mean —
still says:

> *"Shapes and colours follow the graph window exactly, because that is the vocabulary the user
> already reads: a station is a circle, a terminus a square, a reversing point a smaller square, and a
> plain point a small diamond."*

`paintBadge` implements the grid that replaced it, and says so in its own body at `:1502`:

```
// THE SHAPE SAYS WHAT TURNING MEANS HERE.  THE SIZE SAYS WHETHER IT IS A STATION.
//
//                     trains do not turn    trains MAY turn    trains ALWAYS turn
//     a station            big circle          big diamond         big square
//     a passing point     small circle        small diamond       small square
```

with the explicit note that the old scheme "had grown up the other way … four shapes with no system
behind them". Two of the legend's four claims are now wrong in a way that matters: a plain point is a
small **circle**, not a diamond, and a **diamond** now means "trains may turn". Somebody reading the
class doc to interpret a screenshot would read a may-turn station as a plain point.

The reference to "the graph window" is also dead — `GraphViewer` went in `d8db4879` — so the stated
justification for the vocabulary no longer exists either.

**What I would do.** Replace `:143`–`:145` with the grid from `:1504`–`:1506`, in one place rather than
two. The paragraph below it about the five kinds being two independent flags is still correct and
should stay.

---

## What was checked and found clean

An honest short list of what held is more useful than a longer list of doubts.

- **The two "three of four sites" guards from `e5f77c9c` really did reach the fourth.**
  `LayoutRightclickAutonomyMenu`'s constructor is private (`:83`) and `showFor` (`:64`) is the only entry
  point; all four surfaces go through it (`LayoutGrid:418`, `LayoutLabel:623`, `LayoutPopupUI:227`,
  `TrainControlUI:18583`) and none bypasses it. `LayoutGrid`'s retire-the-outgoing-grid rule is in the
  constructor (`:180`–`:188`), covers all four `new LayoutGrid(...)` sites, and handles the null-parent
  case the export path uses.
- **No debug code was committed.** I grepped every added line in `src/` for `System.out`, `System.err`,
  `printStackTrace`, `TODO`, `FIXME` and `HACK`. The only hits were the strings "todo" and "print"
  inside Spanish and Italian message text.
- **The message bundles are in step.** All eight carry 1768 keys, all eight are pure ASCII, and every
  key used through `I18n.t` resolves — the six apparent misses are all dynamic key composition
  (`"autosetup.ui.side" + side.name()`) or a javadoc example.
- **The battery list is complete.** 94 `<test-one-class>` entries against 95 test files; the one
  omission is `testAutoDetect`, which `testEveryTestIsInTheBattery` excludes by name with its reason
  and guards against the exclusion list quietly growing. That test's two meta-assertions are the best
  thing in the new suite.
- **The selection-clearing rule from `0dd26322` survived.** `moveSelection` still clears at `:2588`,
  and the three other clear sites all refresh their borders afterwards.
- **The data file committed on 08-23** (`cs2_sample_layout/config/autonomy/configuration-Autonomy 1j.json`)
  was deliberate — `4e7daabe` says so and says why.
- **No test writes outside the test tree.**
- **No guard added in this window was later silently removed.** This was the third thing I went
  looking for and I did not find it. Every guard-shaped line added between `f38cfa24` and HEAD and
  absent from a later revision resolved to one of three things: moved into a helper or a caller,
  strengthened, or removed deliberately with the reason in the commit message — `synchronized` coming
  off `Layout.updatePendingS88` in `0b5f5e73`, the `!menuOnly` gates in `0651d57c`. The one apparent
  exception is `applyLength`'s `NumberFormatException` catch, dropped by `114a4444` on the premise
  that the digits-only filter makes the parse safe; it does not cap the length, so eleven pasted
  digits still throw. That is already NR-6 and is not counted again here.
- **The renames from `e5f77c9c` were followed through.** `gridSideTowards` and `sideTowardNeighbour`
  both carry updated javadoc, and the only surviving mention of the old names is the historical note
  at `TileGraph.java:1033`.
- **`autonomy.json` is never written when the running layout came from the diagram.** Both writers
  (`TrainControlUI.java:1519` and `:1552`) check `activeDiagramConfiguration == null` first.

---

## Where the defects came from

This is the part only a history pass can offer, so here it is plainly.

**Most of the defects fixed in these three days were introduced in these three days.** The commit
subjects say so without being asked: *"two of them were mine from yesterday"* (`1ef71465`),
*"one of them found that a fix of mine was the bug"* (`d6b9b00c`), *"twice about code written an hour
earlier"* (`9f7dab8c`), *"Verification found one real regression, and it was mine"* (`26b39cb7`),
*"two majors, both mine"* (`243cdba9`), *"the night review found four, two of them mine from earlier
tonight"* (`68123083`). That is six commits explicitly, and the OB- and MT- ledgers contain many more.
The window is not a codebase being repaired; it is a codebase being changed fast enough that the
review loop is the only thing keeping up, and the review loop is finding things hours after they were
written.

**The single largest source is a rule restated at call sites.** Every one of the recurring areas has
the same shape: a fact about the system is written out at N places, a defect is found at one of them,
the fix goes there, and the remaining N−1 wait. The window contains at least nine rounds of "the setup
follows the track", five of "which surface should be refreshed", four of "which end of a link/pair is
this about", and eight of "where is the centre of the track in this tile". `6f9234d8` names it in its
own message; `e5f77c9c` names it in a javadoc — *"Being remembered at three of four call sites is what
a rule looks like just before it is missed at the fourth"* — and then that same javadoc's file
acquired a new leak the same night. Naming the pattern has not stopped it.

**The consolidations themselves are now a defect source.** This is the part I did not expect. NR-2 was
caused by `114a4444` unifying the caption predicate; the unification shipped a regression the same
night. TD-9's duplicated block was written out twice *by the commit that fixed the bug the duplication
caused*. TD-1's `placementChanged` is a de-duplication that three menu helpers still do not use. The
signature is consistent: when a rule stated in several places gets pulled into one, the new single
statement is written from one of the copies and drops what another copy knew. This is the failure
mode the codebase is currently most prone to, and it is more dangerous than the duplication it
replaces, because a single wrong statement has no surviving copy to disagree with it.

**Comments are load-bearing here, and they are the thing least likely to be updated.** This codebase
defends itself with prose: nearly every non-obvious line carries a paragraph naming the defect it
prevents and the ticket it came from. That works — I found several defects *because* a comment
described behaviour the code no longer had. But it means a stale comment is not cosmetic. TD-8, TD-12
and TD-4 are all cases where the code moved and the sentence explaining it did not, and in TD-8 the
stale sentence ("only a hand-edited file reaches here") is precisely what would stop the next reader
looking. TD-15 is a legend for a vocabulary that was deliberately replaced. Worse, TD-3 shows a comment
being load-bearing in a way nobody intended: a test that passes because of a word inside a comment.
When prose is the safety mechanism, prose needs the same discipline as code, and it is not getting it —
I counted **95** javadoc blocks in `src/` that attach to nothing because something was inserted between
them and their declaration, including the one described in TD-14, where the whole rationale for the
atomic write that protects the operator's saved work now sits above `sanitizeFilename`. Nothing in the
build warns about any of them, and unlike a stale sentence they are mechanically detectable.

**A guard can be written, documented, cited by ticket, and never called — and nothing notices.** TD-13
is a private method written specifically to fix OB-032, with a javadoc explaining the subtlety that
makes it necessary, in a commit whose message reports the defect as fixed. It has no callers and the
defect is live. DD-A6 found the same shape a day earlier from the other direction: 200 lines of
`HomeLocomotiveMenu` holding three safety rules whose callers had been deleted, with their tests still
green because the tests called the dead code directly. Two instances in two days is a pattern, and it
is the one this codebase is least equipped to see: the build produces no unused-method warning, the
reviews read diffs rather than reachability, and a test that calls a rule directly cannot tell you
whether anything else does.

**The tests written in this window are much better than the tests written before it, and their weak
point is uniform.** 35 new classes, and most of them are genuinely strong — `testTrainMarkIsNotBlank`
asserts its fixture is blank before asserting the real property; `testAutoLayoutRace` has an explicit
anti-vacuity counter; `testEditorSwitchClearsPageState` strips comments, fails on a missing file, and
has a reflection companion that closes the rename hole. Where they fail, they fail the same way: they
read Java source text instead of driving behaviour, and then handle the "I could not find it" case by
returning rather than failing. TD-3, TD-5 and TD-10 are all that one habit. The habit exists for a
real reason — several of these rules are genuinely about code shape ("is this clear inside a finally",
"is every collection handled at every site") and cannot be driven. But two of the three fixes are
already written in this same window, in `testEditorSwitchClearsPageState`, and applying them
everywhere is an afternoon.

**The batteries are being deferred, and the cause is structural rather than careless.** Five commits in the window say the
battery was not run — *"Adam's app reclaimed the CS2 port mid-round and I am not killing it"*
(`e5f77c9c`), *"Battery not run - TrainControl is open"* (`8e3066bc`). The reason is written down at
`build.xml:84`: `NetworkProxy` binds UDP 15730 and nothing releases it, so the suite
runs one class per JVM and cannot run at all while the application is open. The build file already
names the fix — *"a shutdown path on MarklinControlStation that releases the socket"* — and notes it
would also make the hand-kept battery list unnecessary, retiring DD-A2's whole category of risk as a
side effect. Of everything in this document, that is the change with the best ratio of
effort to defects prevented, because it is the one that would let the fixes to hardware-facing code be
verified at the moment they are written rather than the next morning.
