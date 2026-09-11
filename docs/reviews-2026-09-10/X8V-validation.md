# Validating the independent third pass's fixes

**Prefix for citing these findings elsewhere:** `X8V`

**Status:** closed 2026-09-10.  All ten findings dispositioned: nine repaired and tested, and `X8V-C3` answered by Adam - page order is by name, so the number the station uses for it decides nothing here.

Written 2026-09-10 on branch `autonomy-diagram-r0` at **`d86914a9`** ("The independent pass, worked"),
the commit that fixes the fifteen A/B/C findings of
[`X8-independent-review.md`](X8-independent-review.md). That review was written against `49c687d5`.

Scope: the FIXES, not the review. For each of the fifteen — does the code do what the finding asked,
does the named test go red when the fix is taken away, and did the fix cost something the finding was
not about. Then whatever else the fixes introduced that neither the reviewer nor the fixer saw.

`X8V` was checked against `docs/reviews/`, `docs/reviews-2026-09-09/`, `docs/reviews-2026-09-10/`,
`src/`, `test/` and `docs/manual-tests/findings.tsv` before it was chosen; it collides with nothing.
Live prefixes in this folder are `E8`, `E8V` and `X8`.

---

## Verdict on the fifteen

**9 HELD, 6 PARTIAL, 0 NOT FIXED.** Every one of the thirteen tests the commit added does pin what it
names: all thirteen were driven red by a mutation of the fix, and the two guards that could have been
written too wide were driven red by the narrower mutation as well. What the PARTIAL verdicts are about
is what the fixes did on the way past.

| id | what it asked for | verdict |
|---|---|---|
| `X8-A1` | a left-margin `key=value` kept on read and written back after the header | **HELD** — four claims, each mutation-verified; measured over all 65 CS2 text files in the tree and all 41 real pages. Two consequences worth Adam's eye: `X8V-C3`, `X8V-C6` |
| `X8-A2` | one question ("what does this locomotive command?") asked of both sides | **HELD** — the documentation half of the finding was not done (`X8V-C8`) |
| `X8-B1` | `initCopy` refuses when it has neither label nor component | **HELD** — and the guard is reachable from exactly the two key branches, by enumeration of all nine call sites (`X8V-D6`) |
| `X8-B2` | a page's `.xoffset`/`.yoffset` survive an index rewrite | **PARTIAL** — three claims pinned, and the genuine export's two offsets land on their own pages. On a **duplicate page id** they are written against the wrong page (`X8V-B1`), and the new reader disagrees with `readLayoutIndexIds` about case (`X8V-C4`) |
| `X8-B3` | ` .S88Flag` recognised as an array header, and `parseRoutes` reading both keys | **HELD** — and the two halves are pinned separately, which is the part worth knowing (`X8V-D1`) |
| `X8-B4` | `delete` forgets the whole square, as `clear`'s comment already claimed | **PARTIAL** — it does, both caption directions included. But `forgetTiles` answers "yes" to a non-empty list rather than to a change, so the sentence kept beside the call is now false of it and every track delete rebuilds the graph and writes the whole setup (`X8V-B2`) |
| `X8-B5` | `locked` carried across `editRoute`'s delete-and-re-add | **HELD** — including across a rename, which the shipped test does not exercise (`X8V-D7`) |
| `X8-B6` | answered as a documentation defect | **HELD** as an answer: `execRoute` unchanged, the help text rewritten in all eight bundles, `behaviour.md` §7a written, and a test that goes red if the monitor's guard is added. Still Adam's to overrule |
| `X8-C1` | citations by method name, not by line | **PARTIAL** — the shape is gone and a guard refuses it. Three of the eight rewrites name the method that sits at the STALE line rather than the code the sentence is about, so they are wrong in the same direction as before and now unfalsifiable (`X8V-C1`) |
| `X8-C2` | the delay floor shown rather than applied silently | **PARTIAL** — the executor's sleep is unchanged for every value, `delayOf` raises a typed value, and **neither half is pinned by anything** (`X8V-C5`). A delay already stored below the floor is still not raised (`X8V-C7`) |
| `X8-C3` | the javadoc stating the rule and naming all seven kinds | **HELD** — verified against `Kind` and against every branch of `RouteCommand.toLine` |
| `X8-C4` | the field saying milliseconds | **HELD** — all nine call sites re-read |
| `X8-C5` | one `roomToGrow` predicate replacing three copies of the ceiling | **PARTIAL** — three became one and the two shift gestures now ask it, which is the fix. The predicate asks about the dimension the gesture does not grow, so a page taller than `MAX_SIZE` refuses a COLUMN shift; measured. Nothing pins it in either direction (`X8V-C2`) |
| `X8-C6` | the paragraph naming `pageIdOrPosition` instead of quoting the removed expression | **HELD** |
| `X8-C7` | the javadoc naming all six fields and saying which way it is wrong | **HELD** |

---

## Method

**What was executed.** Ten runs through `docs/tools/one.sh` and one full battery — 209 classes green, 0
failures, 0 testing nothing (`X8V-D12`) — all against this working tree, with `TC_SCRATCH` in this
session's scratch directory.

Baseline first — the seven classes the commit touches or adds, at `d86914a9` unmodified:

```
--- core.testParseCS2Layout                              Total tests run: 25, Failures: 0, Skips: 0
--- core.testParseCS2Routes                              Total tests run: 16, Failures: 0, Skips: 0
--- regression.testEveryCitationResolves                 Total tests run:  3, Failures: 0, Skips: 0
--- core.testTwoCentralStationMultiUnitsShareAMember     Total tests run:  2, Failures: 0, Skips: 0
--- regression.testCutWithNothingHovered                 Total tests run:  2, Failures: 0, Skips: 0
--- regression.testDeleteForgetsTheWholeSquare           Total tests run:  1, Failures: 0, Skips: 0
--- core.testRoutes                                      Total tests run: 25, Failures: 0, Skips: 0
```

**Nineteen source edits in four batches — sixteen mutations and three controls — each restored from a
copy taken immediately before the edit.** Batched so that no two edits in one run could reach the same
test class, except where the whole point was to tell two apart by which test failed. After every batch
the files were put back from the copies and `git status --porcelain -- src test docs build.xml` was
empty.

| # | mutation | classes run | result |
|---|---|---|---|
| A1a | the fourth arm of `parseFileContents` never matches | `testParseCS2Layout` | 2 red: `testTheHeaderKeyOfAPageSurvivesASave`, `testAPageWithNoBlocksStillKeepsItsHeaderKey` |
| A1b | the `bareKeys` loop in `exportToCS2TextFormat` writes an empty map | `testParseCS2Layout` | the same 2 red |
| A1c | the end-of-file flush is skipped | `testParseCS2Layout` | **1** red: `testAPageWithNoBlocksStillKeepsItsHeaderKey` alone — the discriminator that test claims to be |
| B2a | `appendPageExtras` finds nothing | `testParseCS2Layout` | 3 red: the page-edit, no-id and renamed claims |
| B2b | `readLayoutIndexPageExtras` skips a page with no `.id` | `testParseCS2Layout` | **1** red: `testAPageWithNoIdKeepsWhatTheStationWroteInsideIt` alone |
| B3a | the array-header arm back to `^ \.[a-z]+$` | `testParseCS2Routes` | **1** red: `testAConditionalRouteFirstInTheFileIsNotDropped`. `testConditionsOnTheRealRouteFile` stays green, which is the "works by accident" claim, measured |
| B3b | `parseRoutes` stops reading the `S88Flag` key | `testParseCS2Routes` | **2** red — including `testConditionsOnTheRealRouteFile`, so the second half is pinned by the test that was already there |
| A2 | `commandedLocomotives` back to the raw links | `testTwoCentralStationMultiUnitsShareAMember` | 2 of 2 red |
| B1a | the `initCopy` guard removed | `testCutWithNothingHovered` | 2 of 2 red |
| B1b | the guard widened back to the component alone | `testCutWithNothingHovered` | **1** red: the blank-square control, which is the column-drag case the commit message says forced the narrow guard |
| B4 | `delete` back to `forgetCaptionsAt` | `testDeleteForgetsTheWholeSquare` | 1 of 1 red |
| B5 | the `wasLocked` restore removed | `testRoutes` | 1 red: `testEditingARouteKeepsItsLock` |
| B6 | the monitor's `hasConditions() && !evaluate(network)` guard added to `execRoute` | `testRoutes` | 1 red: `testARouteRunByHandIgnoresItsConditions` |
| C1 | `// see Layout.java:1234` added to `Layout` | `testEveryCitationResolves` | 1 red: `testNoCommentCitesALineNumber` |
| **C2a** | `delayOf` stops raising a typed delay to the floor | `testParseCS2Layout`, `testCutWithNothingHovered`, `testTheEditorTellsAutonomy`, `testRoutes` | **nothing red** (`X8V-C5`) |
| **C5a** | the ceiling dropped out of `canShiftDown` | the same four | **nothing red** (`X8V-C2`) |

And three controls, applied together with C2a and C5a:

| control | what it says |
|---|---|
| the `initCopy` guard's two halves swapped — `component == null && label == null` | green, so the `X8-B1` assertions are not keyed to the expression's shape |
| `addBareKey` stops filtering the parser's own `_type` marker | green — nothing pins that filter, and without it every saved page would gain a `_type=_bareKeys` line (`X8V-D8`) |
| `roomToGrow` charges rows against `getSx()` and columns against `getSy()` | green — the predicate is unpinned in both directions (`X8V-C2`) |

**Three throwaway probe classes were written, run and deleted.** `test/regression/x8vProbe.java` —
eight measurements over the repository's own files, with no model and no window, every call either
static or on a `CS2File` with a null control, so nothing in it could reach a preference or the live
folder. `test/regression/x8vProbeTwo.java` — four measurements through a real `LayoutEditor` on the
frozen `live-snapshot` fixture, opened through `support.LayoutSandbox`. And
`test/core/x8vProbeThree.java` — one measurement of `isSimultaneousMultiUnitCompatible`'s direction, on
the `single-switch` scenario. Deleted after the runs, because a class in `test/` with no `build.xml`
entry reddens `regression.testEveryTestIsInTheBattery`. Their output is quoted in the findings that
used it.

**`cs2_sample_layout/` was never written.** Hashed before the first run and after the last: identical,
twelve files. `one.sh`'s own before-and-after fingerprint did not fire on any of the ten runs, and the
battery's did not fire either — it exits non-zero for that and exited 0. The
three files `git status` shows modified there are Adam's, they were modified before this pass began,
and nothing here touched them.

**Nothing else was left behind.** `git status --porcelain -- src test docs build.xml` is empty but for
this document.

**What was read and not executed.** The whole of `d86914a9`'s diff; `CS2File.parseFileContents` and
each of its five callers; `LayoutDiagram`'s three readers of `gleisbild.cs2` and `writeLayoutIndex`;
`AutonomySession.forgetTiles` down through `moveTiles` to `AutonomyCompanionStore.forgetSquares`;
`LayoutEditor.delete`, `deleteSelection`, `shiftDown`, `growEdges` and the right-click menu;
`RouteEditorFrame.delayOf` and its one call site; `MarklinRoute.execRoute`'s sleep; `CommandRow.Kind`
against `RouteCommand.toLine`; and each of the eight rewritten citations against the code it now names.

---

## A — high

Nothing. No fix in this commit was found to have broken anything on the layout or lost data, and none
of the fifteen was left unfixed.

---

## B — medium

| | | |
|---|---|---|
| `X8V-B1` | on a duplicate page id, the kept `seite` keys are written against the wrong page | closed |
| `X8V-B2` | `delete` now rebuilds the graph and writes the whole setup for every square, and the comment beside it says otherwise | closed |

---

### X8V-B1 — two pages holding one id: `writeLayoutIndex` reissues the second one's id and gives its scroll offsets to the first

**Status:** closed 2026-09-10.  Refused at the READ, not patched at the write: an id two pages claim attributes its keys to NOBODY.  It fails safe in both directions - a page that loses a scroll position is one the station will set again, and a page given somebody else's is one nothing will correct - and patching the writer alone could not have worked, because the misattribution is complete by the time the writer reads the map.  Two claims in `core.testParseCS2Layout`: the collision, and the control that one page claiming an id still keeps its keys.  Both mutations caught (no guard: 1 failure; refuse everything: 4).

**What is wrong.** `readLayoutIndexPageExtras` keys what it kept by page id, and `writeLayoutIndex`
resolves a duplicate id by giving the LATER page a fresh one (its `issued` set, DR-B4). So the extras
of the page that loses the argument are attached to the page that wins it, and the page that was
reissued gets nothing.

**How I know.** A probe, run at `d86914a9`. The index written was

```
seite
 .id=1
 .name=Alpha
seite
 .id=1
 .name=Beta
 .xoffset=42
 .yoffset=43
```

and the two readers answered

```
DUP ids:    {Alpha=1, Beta=1}
DUP extras: {1=[ .xoffset=42,  .yoffset=43]}
```

After `writeLayoutIndex(folder, ["Alpha", "Beta"])`:

```
seite
 .id=1
 .name=Alpha
 .xoffset=42
 .yoffset=43
seite
 .id=2
 .name=Beta
```

Beta's scroll position is now Alpha's, and Beta has none.

**It is not a hypothetical shape.** The genuine Central Station export this repository ships has
exactly this collision. `Oles kreds/config/gleisbild.cs2` opens with a `seite` carrying no `.id` at all
— which both readers resolve to its POSITION, 1 — and the page after it states `.id=1`:

```
OLES ids before: {0 stationer=1, 1 gods=1, 2 opstilling=2, 3 s88=3, ...}
OLES extras:     {1=[ .xoffset=1,  .yoffset=3], 2=[ .xoffset=5,  .yoffset=5]}
```

Writing that index reissues `1 gods` from 1 to 8, and the probe checked where the offsets landed: they
are correct, because only ONE of the two colliding pages carries any.
`testAPageWithNoIdKeepsWhatTheStationWroteInsideIt` is that same file's shape and passes for the same
reason. The day the station writes a scroll position into `1 gods` as well, it goes to `0 stationer`.

**Why it is B and not C today.** What is misattributed is a scroll offset, which is cosmetic on the
station. What the mechanism is FOR is everything the writer does not model — *"what TrainControl does
not understand it is not entitled to throw away"* — so the next key a firmware puts inside a `seite`
block inherits the same misattribution, and by then nobody will be looking.

**What I would change.** Have `writeLayoutIndex` drop the extras of an id it has just reissued: one
line inside the `while (!issued.add(id))` loop, and it fails safe — the page loses its scroll position
rather than inheriting somebody else's. Keying the extras by name as well as by id is the larger
answer and it has to survive a rename, which is why the id was chosen in the first place.

---

### X8V-B2 — `forgetTiles` answers "yes" to a non-empty list, not to a change, so every deleted square now rebuilds the graph and writes the setup

**Status:** closed 2026-09-10.  `Kept.forget` reports whether it removed anything, `forgetSquares` and `store.moveTiles` carry it up, and `AutonomySession.moveTiles` returns it.  **`touched()` is still unconditional for a real MOVE**, which is the load-bearing half: a move changes the DIAGRAM and the graph is built from the diagram, so making the rebuild conditional on the store would leave autonomy describing track that has walked away.  For a built-over-only call - `forgetTiles`, which a delete, a paste, a fill and a clear all reach - there is nothing to rebuild from unless something was stored, which is what this restores to its pre-`X8-B4` behaviour.  Pinned by `testForgettingNothingSaysNothingChanged` and its control, mutation-verified.  The comment in `delete` is true of the call beneath it again.  `LayoutEditor:2508`, the sibling SVN-C8 site this was inherited from, is fixed by the same change.

**What is wrong.** `delete` used to ask `forgetCaptionsAt`, which returns whether it actually cleared
anything, and saved only when it had. `X8-B4` replaced the call with `forgetTiles`, which is right
about WHAT it forgets and wrong about what it reports:

`src/org/traincontrol/automationui/AutonomySession.java:2070-2072`

```java
public boolean moveTiles(Map<TileKey, TileKey> moves, java.util.Collection<TileKey> builtOver)
{
    boolean any = builtOver != null && !builtOver.isEmpty();
```

`forgetTiles(c)` is `moveTiles(null, c)`, so for a one-element list `any` is true before anything has
been looked at. It then calls `store.moveTiles` and `touched()` — which is `dirty = true; rebuild();`,
a fresh `TileGraph` over every page, a `GraphReducer.reduce` and an `AutonomyBuilder` naming run — and
returns true. `delete` reads the true and calls `rememberAutonomy`, which is `saveQuietly()`: the whole
setup, every file of it, to a folder under OneDrive.

**The sentence kept beside the call is now false of it.** `LayoutEditor.delete` still reads:

> *"Only when something was actually forgotten. The return value used to be ignored, so deleting a
> square that had nothing on it still wrote the whole setup to disk, every file of it. Deleting a
> selection is one call per square."*

Both halves of that are the argument for the guard, and the guard no longer guards. The last sentence
is the cost: `deleteSelection()` — the Delete key at `LayoutEditor:7273` and **Delete Selected** on the
right-click menu — calls `delete` once per picked square with `tellAutonomy` true.

**How I know.** A probe through a real `LayoutEditor` on the frozen `live-snapshot` fixture, asking
both methods about a square nothing has ever been written about:

```
FORGET nothing-to-forget: forgetCaptionsAt=false (0ms) forgetTiles=true (8ms) saveQuietly=8ms
```

Roughly 16 ms per square, on the EDT, on a fixture whose main page is 24 x 15. Adam's railway is five
pages. `delete` returns early when the square holds no component, so a genuinely blank square is not
affected — what pays is every piece of plain track carrying no autonomy setup, which is most of a
diagram.

**This is inherited rather than invented, and that is the part worth writing down.** The same defect is
already at `LayoutEditor:2508`, where the comment for SVN-C8 states the rule and then calls
`forgetTiles`:

> *"ONLY IF IT CHANGED ANYTHING (SVN-C8). `delete` was fixed for exactly this and said why … Its
> sibling one method away was not swept, and this is the more expensive of the two: it is reached on
> every single-tile paste and every palette drop onto a blank square, and the layout folder is under
> OneDrive."*

That site carries the same false claim over the same call. `X8-B4` did not create the broken contract;
it moved a fourth caller onto it — `:2433`, `:2514`, `:3701`, `:5191` — while keeping the sentence that
depended on the old one.

**What I would change.** Make `forgetTiles` mean what its callers read it as: have
`AutonomyCompanionStore.forgetSquares` report whether it removed anything, have
`AutonomySession.moveTiles` return that, and call `touched()` only when it did. Four call sites are
already written as though it does.

---

## C — low

| | | |
|---|---|---|
| `X8V-C1` | three of the eight rewritten citations name the method at the stale LINE, not the code the sentence is about | closed |
| `X8V-C2` | `canShiftDown`/`canShiftRight` ask about the dimension they do not grow, and nothing pins `roomToGrow` | closed |
| `X8V-C3` | a preserved `page=N` can now contradict the id `writeLayoutIndex` reissues | closed |
| `X8V-C4` | the three readers of `gleisbild.cs2` have three different rules for the block name's case | closed |
| `X8V-C5` | `X8-C2` and `X8-C5` changed behaviour with no test, against the commit message's claim | closed |
| `X8V-C6` | a repeated left-margin key collapses to the last, and one below the blocks moves to the top | closed |
| `X8V-C7` | a delay already stored below 150 is still shown as a number the railway will not use | closed |
| `X8V-C8` | `isSimultaneousMultiUnitCompatible`'s javadoc still does not say the predicate is one-directional | closed |

---

### X8V-C1 — three of the eight citations were rewritten with the name of whatever sits at the stale line

**Status:** closed 2026-09-10.  Repointed to `isPathClear`, `configureAndLockPath`, and the commented-out `showTab` in `LocomotiveSelector.addLocomotiveActionPerformed`.  The finding's wider point stands and is not closed by this: a wrong method name reads as correct, and `testNoCommentCitesALineNumber` refuses the shape and nothing else.

**What is wrong.** `X8-C1`'s own table has two different columns: *"What is actually there"* — the code
at the stale line number — and *"Where it really is"* — the code the sentence is about. Three of the
rewrites took the first column.

| citing comment | old citation | rewritten to | where the quoted sentence actually is |
|---|---|---|---|
| `Layout.java:2404` | `:2243` | **`trainsUnderway`** | the inline `isAutoRunning() && (!e.getStart().isActive() \|\| !e.getEnd().isActive())` inside `isPathClear`, `Layout.java:2314` |
| `Layout.java:6154` | `:2923` | **`setBlockedBy`'s loop** | `configureAndLockPath`, `Layout.java:3191` |
| `TrainControlUI.java:18523` | `LocomotiveSelector.java:394` | **`LocomotiveSelector.formWindowStateChanged`** | the commented-out `// this.parent.showTab("Tools");` in `addLocomotiveActionPerformed`, `LocomotiveSelector.java:406` |

**How I know.** Each was resolved in the tree at `d86914a9`.

- `trainsUnderway` is at `Layout.java:2243` and counts trains. It has no "stricter form" and says
  nothing about inactive endpoints. The comment now reads *"`trainsUnderway`'s stricter form - any edge
  with an inactive endpoint - keeps its `isAutoRunning` fence"*, which is about a method that does not
  contain that rule. The rule is the third `if` inside `isPathClear`.
- `grep -n "it went straight out to executePath" src/org/traincontrol/automation/Layout.java` returns
  one hit: the citing comment itself. The sentence being quoted — *"It went straight out to
  executePath's handler, which deliberately does not unlock"* — is at `Layout.java:3191`, inside
  `configureAndLockPath`. `Layout.java:2924` is a `setBlockedBy` loop inside `deletePoint`, which is
  about forgetting a deleted point's watchers.
- `formWindowStateChanged` is `LocomotiveSelector.java:392-394`, an empty GEN block. The commented-out
  `showTab` call is twelve lines below it, inside `addLocomotiveActionPerformed`.

The other five are right, and were checked the same way: `handleMisconfiguredPath` does contain
"Provably at its start" (`:3497`); `TrainControlUI.saveState` contains `:2491`;
`GraphLocAssign.commitChanges` does set the arrival and departure functions; `AutonomyBuilder.build`
does emit `json.put("block", …)` at `:844`; and `AutonomyViewerPanel.importLegacyGraph` does call
`importLegacy` and then `save()` on the next line.

**Why this is worse than what it replaced, even though it is still C.** A stale line number is
detectably stale — the review found all six by resolving them. A confidently wrong method NAME reads as
correct, and `testNoCommentCitesALineNumber` cannot help: it refuses the shape and nothing else,
deliberately, and its javadoc explains why resolving was rejected. That argument is exactly why nothing
can now notice these three.

**What I would change.** Repoint them: `isPathClear`'s inactive-endpoint check, `configureAndLockPath`,
and `LocomotiveSelector.addLocomotiveActionPerformed`.

---

### X8V-C2 — `roomToGrow(0, 1)` refuses a column because the page has too many rows, and nothing pins the predicate in either direction

**Status:** closed 2026-09-10.  `roomToGrow` tests each dimension only where the gesture adds to it.  Pinned by the new `regression.testTheDiagramCeiling` - three claims, including the mirror case and a control that a page with room is still offered both shifts - and both mutations caught: cross-charging the dimensions, and dropping the ceiling out of `canShiftDown`.  `LayoutEditor.addRowsAndColumns` still ignores its own arguments and asks `roomToGrow(1, 1)`; that is the pre-existing trap the finding notes, and `roomToGrow(rows, cols)` is now the call that would close it.

**What is wrong.** The one predicate takes both dimensions, and both shift gestures pass a zero:

`src/org/traincontrol/gui/LayoutEditor.java:4479-4482`

```java
public boolean roomToGrow(int rows, int columns)
{
    return layout.getSy() + rows <= MAX_SIZE && layout.getSx() + columns <= MAX_SIZE;
}
```

```java
public boolean canShiftDown()  { return lastHoveredY >= 0 && roomToGrow(1, 0); }
public boolean canShiftRight() { return lastHoveredX >= 0 && roomToGrow(0, 1); }
```

With `rows == 0` the first conjunct is still `getSy() <= MAX_SIZE`, so a page taller than the ceiling
refuses a COLUMN shift — which adds nothing to its height. Symmetrically, a page wider than the ceiling
refuses a ROW shift. `shiftDown()` and `shiftRight()` each open with `if (!canShift…()) return;`, so
this is the gesture and not only the greying.

**How I know.** A probe through a real `LayoutEditor` on the `live-snapshot` fixture, growing only the
row count:

```
SIZE before: 24 x 15
SIZE after:  24 x 80   roomToGrow(0,1)=false   roomToGrow(1,0)=false
```

24 columns — 36 short of the ceiling — and a column shift is refused.

**Is that shape real?** Yes, in this repository. Page size comes from the largest element coordinate,
`x = coord % 256`, `y = (coord >> 8) % 256`, and nothing clamps it on load. Three fixture pages parse
at **17 x 129**: `test/layout_subpage/config/gleisbilder/Main.cs2`, `Sub_Page.cs2` and `Sub/Page.cs2`.
Adam's own pages are 31 x 17 and smaller, so this does not bite him today, which is why it is C.

**The three copies that became one were not all the same.** Two were:
`growEdges`'s `sx >= MAX_SIZE || sy >= MAX_SIZE` and the menu's `sx < MAX_SIZE && sy < MAX_SIZE` are
both exactly `roomToGrow(1, 1)`. The third is not quite. `LayoutEditor.addRowsAndColumns(int rows, int
cols)` now asks `roomToGrow(1, 1)` and ignores its own arguments, so a call with `rows > 1` can still
cross the ceiling — which was true before the fix too. A trap left standing rather than one introduced,
and `roomToGrow(rows, cols)` is exactly the call that would close it.

**And nothing pins any of it.** Two mutations: the ceiling dropped out of `canShiftDown` altogether,
and `roomToGrow` charging each increment against the other dimension. Both left
`core.testParseCS2Layout`, `regression.testCutWithNothingHovered`,
`regression.testTheEditorTellsAutonomy` and `core.testRoutes` green. `grep -rn "roomToGrow" test/`
returns nothing.

**What I would change.** Test each dimension only where the gesture adds to it — `rows == 0 ||
layout.getSy() + rows <= MAX_SIZE`, and the same for columns. And give it a test: it is a pure function
of two ints and the page's size, which is about the cheapest thing in this window to pin.

---

### X8V-C3 — the page file now says `page=1` while the index may have reissued that page's id

**Status:** closed 2026-09-10, answered by Adam.  *"The `.id` field is what the Central Station uses to order the pages.  We still order by name, which is the simpler behavior."*  So neither number decides anything in TrainControl - `getLayoutList` sorts by name, and an id is an identity the autonomy setup is keyed by rather than a position - and the disagreement costs nothing.  The line stays verbatim; rewriting it from the id would be this program asserting something about an ordering it deliberately does not use.  Recorded in `addBareKey`'s javadoc and in `behaviour.md` section 8.

`X8-A1` preserves the header key verbatim, which is the right default for a line nobody understands. It
does mean the file can now hold a number that disagrees with the index, where before it held nothing.

Measured on the genuine export. `Oles kreds/config/gleisbilder/1 gods.cs2` carries `page=1`; its `seite`
block states `.id=1`; and the first page in that index has no `.id` at all, which resolves to position
1 and collides. Writing the index — which happens on any page add, rename or delete — reissues
`1 gods` to **id 8**:

```
seite
 .id=8
 .name=1 gods
```

while the page file still says `page=1`. Before `X8-A1` the page file said nothing, so the two could
not disagree.

Whether a Central Station minds is the same open question `X8-A1` raised for Adam and did not settle.
Recorded because it is a NEW way for the two files to disagree, created by the fix, and because the
answer may be "rewrite `page=` from the id" rather than "preserve it" — which is a decision, not a
repair.

---

### X8V-C4 — `readLayoutIndexPageExtras` calls `Seite` a page and `readLayoutIndexIds` does not

**Status:** closed 2026-09-10.  `readLayoutIndexIds` matches the block name and its two keys without regard to case, which is what the other two readers of this file already did.  It was the one out of step and the one whose answer everything else is keyed by: an index spelling the block `Seite` gave no page an id at all, so a write reissued 1..n across the whole layout.

Three methods now read `config/gleisbild.cs2`, and each has its own rule for the block name:

| | matches `seite` |
|---|---|
| `readLayoutIndexIds` | `"seite".equals(trimmed)` — case SENSITIVE |
| `readLayoutIndexExtras` | `modelled.contains(trimmed.toLowerCase())` — case insensitive |
| `readLayoutIndexPageExtras` (new) | `"seite".equalsIgnoreCase(trimmed)` — case insensitive |

The new one agrees with the preserver and disagrees with the reader whose ids it is keyed by. That is
not an idle worry in this file: VLD-B2 exists because the genuine export spells its version block
`Version` with a capital V, and the fix for it was to make the modelled set match without regard to
case.

Measured, on an index spelling both `Version` and `Seite` with capitals:

```
CASE ids:    {}
CASE extras: {4=[ .xoffset=9]}
```

No page has an id and one page's keys were kept. `writeLayoutIndex` then reissues 1..n across the board
— pre-existing behaviour, not new — and the offsets, keyed 4, are dropped. Dropped is the benign
outcome; had the file's stated ids started at 1, the same disagreement would have attached them to
whichever page sorts first alphabetically.

The three should ask one question. `readLayoutIndexIds` is the one out of step with the other two, and
it is also the one whose answer everything else is keyed by.

---

### X8V-C5 — two of the fifteen fixes changed behaviour and nothing tests either; the commit says otherwise

**Status:** closed 2026-09-10 by testing both.  The delay floor is pinned by `core.testRoutes.testADelayBelowTheFloorIsShownAsTheFloor` - six claims, including that zero is not raised and that `THREEWAY_ROUTE_DELAY_MS` still sits above the floor - and the ceiling by `regression.testTheDiagramCeiling`.  Both mutation-verified.  The commit message's claim was wrong when it was written and is true of the tree now.

`d86914a9`'s message says *"fifteen A/B/C findings, each with a test and each verified against a
mutation."* Thirteen tests were added and they do pin thirteen claims. Four of the remaining findings
are comment-only — `X8-C3`, `X8-C4`, `X8-C6`, `X8-C7` — and a test is not a sensible thing to ask for.
Two are not comment-only:

- **`X8-C2`** changed `RouteEditorFrame.delayOf` so a typed delay between 1 and 149 becomes 150.
- **`X8-C5`** changed `canShiftDown` and `canShiftRight` to consult a ceiling they did not have.

`grep -rn "delayOf\|roomToGrow\|canShiftRight" test/` finds nothing that exercises either, and the
mutations confirm it: reverting the `delayOf` raise, and dropping the ceiling out of `canShiftDown`,
each left every class run green, and the battery is green with both changes in place either way.

Filed at C because both changes are small and `X8V-C2` carries the one that is also wrong. Filed at all
because the claim is in the commit message, which is where the next reader will check.

---

### X8V-C6 — a repeated left-margin key keeps only the last, and one written after the blocks comes back before them

**Status:** closed 2026-09-10 as a comment.  The exporter's comment no longer claims the file comes out in the order it went in; it states what the preservation is - by key and by rule - and names the two shapes that do not survive unchanged, with the measurement that no file in this repository has either.

`bareKeys` is a `LinkedHashMap` and the writer emits it immediately after `[gleisbildseite]`. So the
preservation is by key rather than by line, and by rule rather than by position. Measured by
round-tripping four hand-made pages:

| page as written | what came back |
|---|---|
| `page=1` then `futurekey=hello` | both, in order — a second key a firmware adds survives |
| `page=` (empty value) | `page=` |
| `page=1` then `page=2` | **`page=2` only** |
| the key AFTER the element blocks (`trailing=9` last) | `trailing=9` FIRST, immediately after the header |

Neither of the last two is a regression — before the fix all four lost the line entirely — and no real
file in this repository has a repeated left-margin key or one below the blocks: 18 such lines across 65
files, every one a single `page=N` on line 2. Recorded because the fix's own comment says the keys are
put back *"so the file comes out in the order it went in"*, which is true of the shape a station writes
and not of these two.

---

### X8V-C7 — a delay already stored below the floor still shows a number the railway will not use

**Status:** closed 2026-09-10.  `delayTheRailwayWillUse` is one method asked in both directions: by `delayOf` on the way in and by the table's delay cell on the way out, so a delay that arrived from an earlier build, an import or a JSON file cannot show a number the railway will not wait.  Pinned by the same test as `X8V-C5`.

`X8-C2` raises a delay **as it is typed**: `delayOf` is called from exactly one place,
`RouteEditorFrame`'s table `setValueAt` for column 8. Nothing else re-parses the cell, so opening a
route whose delay is already 100 and saving it leaves 100 in the model and 100 in the file, while the
executor sleeps 150. That is the cell-and-railway disagreement the finding is about, still there for
every route saved before this build and every route imported from a station or a JSON file.

It cannot fire on any data in this repository — all fourteen `..sekunde=` values in the two real route
files are 2, 2.3, 3, 3.2 and 4 seconds — so it is a trap rather than a live defect, and the changelog
should not carry it.

The executor's own change is inert, which is worth stating plainly since that is the half touching the
railway: `Thread.sleep(SLEEP_INTERVAL + max(delay, 150))` equals the old two branches for every value
of `delay` — at 0, below 150, at exactly 150, and above. What changed is that `route.delay` is now
logged whenever a delay was asked for, including for a stored 100, where it logs **150**: the number
used rather than the number set. That is the right direction, and it is a second place the two can be
seen to disagree.

---

### X8V-C8 — the javadoc still does not say `isSimultaneousMultiUnitCompatible` is one-directional

**Status:** closed 2026-09-10.  The javadoc opens by saying the predicate is not symmetric, gives the measured asymmetry, and says that `Layout.sanitizeMultiUnits` is the only caller and asks both ways - so a second caller that asked once would let the pair through half the time.

`X8-A2` asked for two things: the second loop's missing branch, and *"The javadoc on
`isSimultaneousMultiUnitCompatible` should then say that it is symmetric, because `sanitizeMultiUnits`
asking both ways is the only thing that currently makes the mixed case work."* The first was done. The
javadoc is unchanged — still *"Checks if this locomotive can be in a multi-unit with another, at the
same time … Stricter check than `isLinkedTo`"* — with nothing about direction.

The predicate is not symmetric and the fix did not make it so. Asked with a Central Station multi-unit
on the left it walks that multi-unit's members; asked with a plain member on the left it walks the
member's links, which are empty for a locomotive nobody linked, and falls through to the address
comparison at the end. Measured, on a multi-unit at address 4003 holding a member at address 62:

```
ASYMMETRY  mu.isCompatible(member)=false   member.isCompatible(mu)=true
```

The added body comment does say *"`Layout.sanitizeMultiUnits` asks this both ways round"*, which is
most of what a reader needs — but it is written as part of the argument for the defect rather than as a
rule for the next caller, and it is in the body rather than on the door.

There is exactly one production caller, `Layout.java:6993-6995`, and it does ask both ways. So this is
a trap for a second caller rather than a live defect, which is why it is C. Under this codebase's own
rule — *"if this file were the only thing a reader had, would the comment be enough?"* — one sentence
on the javadoc closes it.

---

## D — not defects

| | | |
|---|---|---|
| `X8V-D1` | the `X8-B3` fix's two halves are pinned separately | clean, measured |
| `X8V-D2` | the fourth arm is inert for locomotives, accessories, routes, the index and CS3 | clean, 65 files |
| `X8V-D3` | every real page round-trips with its `page=` line exactly once | clean, 41 pages |
| `X8V-D4` | the `..key=` array regex was widened too, unasked | inert on all real data |
| `X8V-D5` | `forgetTiles` is a genuine superset of `forgetCaptionsAt` for a delete | it is, both caption directions |
| `X8V-D6` | `initCopy`'s guard is reachable only from Control+X and Control+C | nine call sites enumerated |
| `X8V-D7` | `editRoute`'s lock restore lands on the route the caller reads, across a rename | it does |
| `X8V-D8` | `addBareKey`'s `_`-prefix filter is load-bearing and untested | untested, and correct |
| `X8V-D9` | `X8-C3`, `X8-C4`, `X8-C6` and `X8-C7`'s comment claims | all four true |
| `X8V-D10` | `X8-B6`'s eight bundles and `behaviour.md` §7a | all eight rewritten, all ASCII |
| `X8V-D11` | several `S88Flag` groups, and an empty one | both correct |
| `X8V-D12` | the battery | 209 green, 0 failures, 0 testing nothing |

---

### X8V-D1 — the `X8-B3` fix's two halves are pinned by two different tests, and the split is the useful part

Reverting the array-header regex alone reddens the new
`testAConditionalRouteFirstInTheFileIsNotDropped` and leaves `testConditionsOnTheRealRouteFile` green —
which is the *"the shipped fixture survives it by accident"* claim, measured rather than argued.
Disabling the `S88Flag` read in `parseRoutes` while leaving the regex widened reddens BOTH, the
pre-existing test included. So the trap the commit message describes — *"widening the regex alone moved
every route's conditions to a key `parseRoutes` never read"* — is genuinely guarded, and by a test that
was already in the class.

### X8V-D2 — `_bareKeys` items appear only in page files, and only ever as `page=N`

`parseFile` has five callers — `parseRoutes`, `parseLocomotives`, `parseLayoutIndex`, `parseMags` and
the page loop — and each of the first four filters by `_type` with `equals`, so an item typed
`_bareKeys` is skipped. CS3 files are JSON and never reach `parseFileContents` at all.

Measured rather than argued. A probe parsed **every** `.cs2` file in the tree outside
`cs2_sample_layout/` — 65 of them, `lokomotive.cs2`, `magnetartikel.cs2`, `fahrstrassen.cs2` and twelve
`gleisbild.cs2` indexes included — and found 18 bare-key items, every one in a file under
`gleisbilder/`, every one holding the single key `page`:

```
PROBE bare keys: 65 files parsed, 18 bare-key items
BAREKEYS ./Oles kreds/config/gleisbilder/1 gods.cs2 -> {page=1}
...
BAREKEYS ./test/layouts/single-switch/config/gleisbilder/1 - Junction.cs2 -> {page=1}
```

The page loop handles `_bareKeys` BEFORE the `if (!"element".equals(...)) addUnmodelledBlock` arm below
it, which is what stops the item being written back out as a block named `_bareKeys`.

### X8V-D3 — every real page in the tree comes back with its header line exactly once

The same probe walked every `gleisbild.cs2` in the tree — twelve, of which eleven sit in a `config/`
folder `parseLayout` can open — exported all 41 pages it got back, and compared each export against the
file on disk for every left-margin line:

```
PROBE round trip: 41 pages, 18 left-margin lines checked
```

Each of the 18 appears exactly once in its export: not zero, which is the defect, and not twice, which
is what a line both carried over and regenerated would look like.

### X8V-D4 — the `..key=value` regex was widened as well, and nothing in the tree notices

`^ \.\.[a-z]+=.+$` became `^ \.\.[a-z0-9A-Z]+=.+$` in the same commit. `X8-B3` did not ask for it, and
neither the commit message nor the comment beside it mentions it. It is inert here: every `..` array
key in every CS2 file in the repository is lowercase — `dauer`, `hi`, `kont`, `lok`, `lokname`,
`magnetartikel`, `nr`, `sekunde`, `stellung`, `typ`, `wert` — so no line changes arm. It is also the
right direction: before, an array key a firmware wrote with a capital was dropped in silence.

Worth one line for the next reader: the two `.`-prefixed arms now accept `[a-z0-9A-Z]` and the new
left-margin arm accepts `[a-zA-Z0-9_]`. The underscore is in one class and not the other two.

### X8V-D5 — `forgetTiles` really does forget everything `forgetCaptionsAt` did

`forgetCaptionsAt` cleared BOTH caption directions: the caption sitting ON the deleted square, and every
caption naming it. `testDeleteForgetsTheWholeSquare` asserts only the second. The first was measured
separately, by putting a caption on the square about to be deleted, pointing at somewhere else:

```
CAPTION on the deleted square afterwards: null
```

`AutonomyCompanionStore.forgetSquares` says the same — *"Each collection drops what it knows about
these squares AND anything POINTING at them"* — and the caption-sparing `arriving` case does not apply:
that map is passed only on the MOVE path, and `forgetTiles` passes null, which is correct for a delete
because nothing is arriving.

The return value is the half that is not a superset, and that is `X8V-B2`.

### X8V-D6 — the `initCopy` guard is reachable only from the two branches it was written for

All nine call sites, read:

| site | passes | can both be null |
|---|---|---|
| `LayoutEditor:1243` | `(label, label.getComponent(), false)` | no — `label` is dereferenced on the same line |
| `LayoutEditor:1248` | `(label, null, true)` | no — same gesture, same non-null label |
| `LayoutEditor:2087` | `(label, label.getComponent(), false)` | no |
| `LayoutEditor:2157` | `(label, null, true)` | no — guarded by `label != null && label.getComponent() != null` |
| `LayoutEditor:7224` | `(getLastHoveredLabel(), null, true)` | **yes** — Control+X |
| `LayoutEditor:7235` | `(getLastHoveredLabel(), null, false)` | **yes** — Control+C |
| `LayoutEditorRightclickMenu:215` | `(label, null, true)` | no — inside `if (component != null)`, and the menu is built at `LayoutEditor:2131` from the label the right-click arrived on |
| `LayoutEditorRightclickMenu:231` | `(label, null, false)` | no — same |

So the guard refuses nothing a working gesture does — which is also what the two controls inside
`testItArmsNothingToPaste` say from the other side: a palette placement (a component, no label, and
coordinates of -1 on purpose) and a column drag's blank square (a real grid label, no component) both
still arm.

One correction to `X8-B1`'s prose while I was in there. It says the guard "also closes the
`receiveKeyEvent` door at `:889`". `receiveKeyEvent` does not call `initCopy` at all — it reaches
`executeTool`, which is a different door and still unguarded on this shape. It has no callers, as the
finding says, so nothing turns on it.

### X8V-D7 — the lock restore lands on the route the caller reads, including after a rename

`X8-B5` restores through `this.routeDB.getById(id)`, and the shipped `testEditingARouteKeepsItsLock`
calls `editRoute(name, name, …)` — the same name in and out — so it does not exercise the path where
`getById` could hand back something else. Probed with a real rename:

```
LOCK after rename: old=null new=Route X8V lock probe renamed
```

`model.getRoute(oldName)` is null, `model.getRoute(newName)` is there, and `isLocked()` is true. The
by-id restore and the by-name read agree.

### X8V-D8 — `addBareKey`'s underscore filter is what stops `_type=_bareKeys` reaching the file, and nothing tests it

The page loop hands `addBareKey` every entry of the item, `_type` included, and the filter is the only
thing that drops it:

```java
if (key == null || key.isEmpty() || key.startsWith("_")) return;
```

Removing it was run as a mutation: every class stayed green, and a saved page would gain a
`_type=_bareKeys` line. The filter is correct and it is doing real work; it is listed here so whoever
changes it knows nothing will say. Its side effect is that a firmware key genuinely beginning with `_`
would be dropped, which is a trade nobody is likely to have to make.

### X8V-D9 — the four comment-only findings say true things

- **`X8-C3`.** `CommandRow.Kind` has thirteen members; `hasDelay` admits six; the seven the javadoc
  names — FEEDBACK, STOP, FUNCTIONS_OFF, LIGHTS_ON, AUTONOMY_LIGHTS_ON, ROUTE, AUTO_LOCOMOTIVE — are
  exactly the complement. `RouteCommand.toLine` writes a delay in four branches and nowhere else
  (`:643` accessory, `:667` direction, `:671` speed, `:675` function), which is the rule the roll-call
  is meant to be checkable against.
- **`X8-C4`.** All nine `secondsToNext` sites re-read; every one is milliseconds, `testLayoutTimetable`'s
  `10000L` for ten seconds included.
- **`X8-C6`.** `LayoutDiagram:1521` now names `pageIdOrPosition`, which is the method `CS2File:2204`
  calls. It describes `parseLayoutIndex` as "CS2File's page loop", which is loose but not wrong.
- **`X8-C7`.** `timetableSignature` appends six things and the javadoc names all six; both extra fields
  are drawn by the row builder at `:27374`.

### X8V-D10 — the help text was rewritten in all eight bundles, and the intent is recorded

All eight `route.ui.frameHelp` values changed and every one grew by 235-293 bytes, which is the
CONDITIONS paragraph gaining its new clause rather than the English string being copied over seven
translations. Spot-read in German and Polish: both are genuine translations of the new sentence.

Every bundle is pure ASCII — `grep -cP '[^\x00-\x7F]'` returns zero for all eight — so the `\uXXXX`
rule is kept and Java 8 will not mojibake them. `behaviour.md` §7a carries the decision in full,
including which doors it applies to and why.

### X8V-D11 — several `S88Flag` groups concatenate correctly, and an empty one cannot arise

The new concatenation is `S88Flag + "|" + item`, which raises two questions the review did not answer.

Several groups: the array flush already joins repeated groups with `|` inside one value, so two
`.S88Flag` blocks arrive as one string. Measured:

```
ITEM  {item={stellung=1,magnetartikel=5}, S88Flag={hi=1,kont=11|hi=0,kont=12}, _type=fahrstrasse, ...}
ROUTE Two Groups  commands=[1 straight]  conditions=[s88 11 occupied, s88 12 clear]
```

Two conditions, one command, both settings right.

An empty `S88Flag`: the key is only ever created by the array flush, which runs only when the array is
non-empty, so `m.get("S88Flag")` is either null or a `{…}` group. A ` .S88Flag` line with nothing under
it sets `lastKey` and never writes a key, so the `if (m.get("S88Flag") != null)` arm is skipped and the
concatenation is the bare `item` — the pre-fix string exactly. And `m.get("item")` cannot be null at
that point: the `route.invalidCs2Route` guard twenty lines above returns first, so nothing appends the
string `"null"`. (That guard is also why a route with conditions and no commands is still dropped
whole, exactly as before the fix.)

### X8V-D12 — the battery is green, and it is the same number the commit claims

Run once at the end, against this tree with nothing mutated:

```
$ TC_SCRATCH=... bash docs/tools/battery.sh
SKIP core.testAutoDetect (needs a Central Station)
classes green: 209   classes with failures: 0   classes that tested nothing: 0
```

Exit 0, which is the part worth saying: `battery.sh` prints its summary before it checks whether the
source tree moved under the run and whether `cs2_sample_layout` changed, and exits non-zero for either.
Neither fired. So these 209 are a measurement of one commit, and nothing in the suite wrote to the live
railway.

`d86914a9`'s message claims *"Battery: 209 classes green, 0 failures, 0 testing nothing."* Reproduced
exactly. That is also the broadest answer available to "did a fix break something the finding was not
about": whatever `X8V-B2` and `X8V-C2` cost, no existing test can see it — which is `X8V-C5`.

---

## What I did not cover

- **`docs/manual-tests/`.** `d86914a9` adds 23 rows to `findings.tsv` and 8 kB to `triage.db`. Those
  rows are what `regression.testEveryCitationResolves` reads, and that class is green — including its
  "the catalogue is regenerable and current" test — but I did not read the rows or open the database.
  Three passes in a row have now declined this folder.
- **The `X8-B6` decision itself.** Whether a route run by hand should read its conditions is Adam's;
  this document only checks that the answer taken was carried out consistently. It was.
- **`cs2_sample_layout/`.** Never opened for writing, and its four working pages are still missing the
  `page=` line — the fix is not retroactive and `X8-A1` says so. They come back the next time each page
  is saved.
- **The four `forgetTiles` call sites other than `delete`.** `X8V-B2` is about a contract three older
  callers already depend on; I read them and did not measure what they cost.
- **Whether a Central Station minds a page file with no `page=` line, or one whose `page=` disagrees
  with the index.** That is `X8-A1`'s open question, and `X8V-C3` adds to it. It needs the real station.
