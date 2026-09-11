# Validating two concurrent passes against each other, by measurement

**Prefix for citing these findings elsewhere:** `NSV`

**Status:** closed 2026-09-10.  All ten of its own findings are fixed; its verdicts on the two reviews it validated are recorded in those documents, including the one finding it refuted and the four severities it moved.

**What was validated.** Every A, B and C finding of
[`N8-eight-day-review.md`](N8-eight-day-review.md) (1 A, 3 B, 4 C) and
[`S14-application-review.md`](S14-application-review.md) (2 A, 4 B, 8 C), plus the three places where
the two reports contradict each other or contradict a fix already committed. **This is not a fifth
review of the application.** It decides which findings are real, at what severity, and whether the fix
each one proposes would work.

Reviewed on branch `autonomy-diagram-r0` at `a281e3a2`, with `src/`, `test/`, `docs/` and `build.xml`
clean throughout. Both reviews reported contamination from each other's in-flight mutations, so every
number either of them reported was re-measured here on the clean tree rather than carried over.

**What was executed.** Baseline first, nothing mutated:

```
--- core.testParseCS2Layout       Total tests run: 27, Failures: 0, Skips: 0
--- core.testRoutes               Total tests run: 26, Failures: 0, Skips: 0
--- core.testRouteCommandParity   Total tests run:  3, Failures: 0, Skips: 0
--- regression.testPageIdsAreDurable            Total tests run: 19, Failures: 0, Skips: 0
--- regression.testDeleteForgetsTheWholeSquare  Total tests run:  2, Failures: 0, Skips: 0
```

Then a throwaway probe class, `test/regression/nsvProbe.java`, with five assertions and printed
measurements; then the same probe under one mutation of `LayoutDiagram.attribute`; then, with the
probe deleted and the mutation reverted,

```
--- regression.testEveryTestIsInTheBattery           Total tests run:  5, Failures: 0, Skips: 0
--- regression.testEveryCitationResolves             Total tests run:  3, Failures: 0, Skips: 0
--- regression.testSwitchingToACentralStationLayout  Total tests run: 12, Failures: 0, Skips: 0
--- core.testParseCS2Layout                          Total tests run: 27, Failures: 0, Skips: 0
```

**The probe has been deleted.** `test/regression/nsvProbe.java` was written, run three times and
removed; `git status --porcelain` shows nothing under `test/`, and the three guard classes above are
green again afterwards. Its output is quoted under the findings that rest on it. The mutation was
taken against a whole-file copy in the scratch directory and restored byte for byte (md5 verified);
`git diff --stat -- src test docs build.xml` is empty.

**`cs2_sample_layout/` was read once and never written.** `one.sh`'s before-and-after fingerprint did
not fire on any of the five runs. The three files `git status` lists there are Adam's running
application's and were already modified before this pass began.

**What was read and not executed.** `LayoutDiagram`'s two index readers, `attribute` and
`writeLayoutIndex` in full, plus the `d86914a9..a281e3a2` diff of that file; every production caller
of `AutonomySession.forgetTiles`/`moveTiles` and the gesture each sits in; `MarklinRoute`'s complete
constructor, `executeAutoRoute`, the monitor loop and `disable()`; `MarklinControlStation.importRoutes`,
`parseRoutesFromJson`, `deleteRoute` and all three `newRoute` overloads; `docs/tools/catalog-findings.py`
and `testEveryCitationResolves`'s catalogue guard. Three read-only reconnaissance agents were used for
breadth on the locomotive, protocol/JSON and page-link findings; every claim they returned that changes
a verdict was checked against the source before it was written here.

**The battery was not run.** `a281e3a2` is still not covered by anybody's battery run.

---

## Verdicts

`N8-eight-day-review.md`

| id | one line | verdict |
|---|---|---|
| N8-A1 | a page link's stored address is a position in the name-sorted page list, so every Manage Pages operation re-aims every arrow | **CONFIRMED AT A DIFFERENT SEVERITY: B** |
| N8-B1 | the duplicate-id withdrawal deletes offsets the file attributes unambiguously, and the code it replaced was right on the only real file with the shape | **CONFIRMED AT A DIFFERENT SEVERITY: C** |
| N8-B2 | the conditional rebuild was removed from the callers whose diagram changed | **CONFIRMED AT A DIFFERENT SEVERITY: C** |
| N8-B3 | the finding catalogue holds fifteen findings `X8V` never made | **CONFIRMED** (count and mechanism exact; the stated cause and the proposed fix are both wrong - see below) |
| N8-C1 | `fillCombinedPage` grows a page past `MAX_SIZE` with no check | **CONFIRMED** |
| N8-C2 | `behaviour.md` says nothing about the route-command delay floor | **CONFIRMED** (same defect as `S14-C8`) |
| N8-C3 | `Layout.java:2404`'s citation names the method the comment is inside | **CONFIRMED** |
| N8-C4 | `TrainControlUI.java:18560-18564`'s citation says the same thing twice | **CONFIRMED** |

`S14-application-review.md`

| id | one line | verdict |
|---|---|---|
| S14-A1 | `importRoutes` arms every route during the parse; a throw leaves armed, unreachable routes running | **CONFIRMED** - and demonstrated by execution |
| S14-A2 | the duplicate-page-id guard went to the wrong reader, so two pages share one autonomy setup | **CONFIRMED** |
| S14-B1 | `setF` fans a function out to every multi-unit member before bounds-checking it | **CONFIRMED** |
| S14-B2 | `RouteCommand.fromJSON` does not clamp a speed, and `setSpeed` clamps the members and not the head | **CONFIRMED** |
| S14-B3 | `Edge.toJSON` does not write `entrySide`, which `Layout.fromJSON` reads | **CONFIRMED** |
| S14-B4 | the save path reads `linkedLocomotives` with no lock, while the rebuild clears and refills it | **CONFIRMED AT A DIFFERENT SEVERITY: A** |
| S14-C1 | a logical accessory address checked against the raw maximum refuses the top address of each protocol | **CONFIRMED** |
| S14-C2 | the s88 route monitor is not a daemon thread | **CONFIRMED** |
| S14-C3 | `loadReturnToHomeTimetable`'s javadoc contradicts its own body | **CONFIRMED** |
| S14-C4 | `toggleF(int)`'s javadoc says one second; every locomotive overrides it with 300 ms | **CONFIRMED** |
| S14-C5 | `AutoJSONExport` is the one writer in the project that truncates its target | **REFUTED** |
| S14-C6 | `getPowerState()` reads unsynchronised a field written under the monitor | **CONFIRMED** (one factual error in it, and it missed the stronger sibling) |
| S14-C7 | `isFeedbackCommand` claims three commands, one is parsed, and an unknown id creates a module | **CONFIRMED** |
| S14-C8 | the route delay floor overrides what the operator typed and is not in `behaviour.md` | **CONFIRMED** (same defect as `N8-C2`) |

**22 findings: 17 confirmed as filed, 4 confirmed at a different severity, 1 refuted.** Nothing was
left unverifiable, though two questions inside confirmed findings need the real hardware and are named
in "What this pass did not settle".

---

## The three adjudications

### 1. `N8-B1` against `X8V-B1` - `N8-B1` is right, and the fix has to move to the key

**Measured, by execution, on every index file in the repository.** The probe copied each real
`gleisbild.cs2` into a temporary folder, read it through the production readers, wrote it back through
`writeLayoutIndex` with the page list sorted by name - which is what `getLayoutList` hands it - and
printed the result.

At `a281e3a2`, clean:

```
NSV OLES ids    = {0 stationer=1, 1 gods=1, 2 opstilling=2, 3 s88=3, 4 indre=4,
                   5 ydre=5, 6 autonom kreds=6, 7 autonom annotated=7}
NSV OLES extras = {2=[ .xoffset=5,  .yoffset=5]}
NSV OLES xoffset=1 survived = false
NSV OLES xoffset=5 survived = true
```

With `attribute`'s withdrawal mutated out - the pre-`X8V-B1` last-wins map - the same probe:

```
NSV OLES extras = {1=[ .xoffset=1,  .yoffset=3], 2=[ .xoffset=5,  .yoffset=5]}
NSV OLES xoffset=1 survived = true
```

and the written file puts them **on the right page**:

```
seite
 .id=1
 .name=0 stationer
 .xoffset=1
 .yoffset=3
seite
 .id=8
 .name=1 gods
```

So `N8-B1` is right on both halves. `0 stationer` carries `.xoffset=1`/`.yoffset=3` and no `.id`,
which `pageIdOrPosition` resolves to its position, 1; the page after it states `.id=1`; the withdrawal
deletes the keys of the page the file states them for.

**And the reason the code it replaced could not misattribute them here is arithmetic, not luck.**
`writeLayoutIndex` computes `next` as one above `floor` and above every id in `existing`
(`LayoutDiagram.java:1537-1541`), and `pageExtras` is keyed by ids read from the same file - so a
reissued id can never be a key the extras map has anything under. `appendPageExtras(contents, .., 8)`
finds nothing, which is exactly what the mutated run shows. Misattribution needs the extras to sit on
the page that *loses* the argument, and the page that loses is the later one in name order. That is the
shape of `X8V-B1`'s synthetic fixture (`testTwoPagesClaimingOneIdKeepNeithersKeys` gives both pages an
explicit `.id=1` and puts the offsets on `Beta`) and it is not the shape of any file here.

**Which files actually have a collision: two, not four.** `N8-D8` says *"the four files in the
repository that do have the collision all have it the same way ... `Oles kreds`, `sample_layout`,
`test/layout` and `tc_backup`"*. Measured, that is wrong:

| file | pages | ids | collision | extras lost |
|---|---|---|---|---|
| `Oles kreds/config/gleisbild.cs2` | 8 | 7 | yes, id 1 between `0 stationer` and `1 gods` | **yes** - `.xoffset=1`, `.yoffset=3` |
| `sample_layout/config/gleisbild.cs2` | 3 | 2 | yes, id 1 between `Page 1` and `Page 2` | no - neither page has any |
| `tc_backup/.../gleisbild.cs2` | 5 | 5 | no - first page has no `.id` but the rest run 2..5 | - |
| `test/layout/config/gleisbild.cs2` | 1 | 1 | no - single page | - |
| the other nine | - | - | no | - |

`S14`'s own table (13 indexes, 2 duplicates) is the correct one. A first `seite` with no `.id` is the
Central Station shape and four files have it; only two of those also have a page stating `.id=1`.

**Severity: C, down from B.** What is actually lost today is one page's scroll position, on one of
thirteen index files, none of them Adam's - and it was being lost entirely until `X8-B2` three commits
ago. The SOP's C is "cosmetic ... or narrow edge cases" and this is both. The argument for keeping it
at B is recorded because it is not silly: the mechanism exists to keep keys *this program has no name
for*, so the next firmware key that lands inside a `seite` block is lost the same way, and a comment
now states a rule the code breaks in the one real case it was built for.

**What the fix has to be, and it is not a revert.** `X8V-B1`'s defect is real in principle and the
withdrawal is a correct answer to the wrong question: the ambiguity is in the map key, not in the file.
Key the kept lines by the page's **name** - which `readLayoutIndexPageExtras` already reads and skips -
and look them up in `writeLayoutIndex` through the same `renamedFromTo` indirection the loop already
uses for `existing.get(renamed.getKey())` (`LayoutDiagram.java:1560-1567`). Then no page can inherit
another's keys, both real files keep their own, and the withdrawal becomes unnecessary rather than
load-bearing. The objection written into `attribute`'s javadoc - *"a rename is the one operation where
the writer is holding a name the index has never seen, and the id is precisely what is carried across
it"* - does not hold against this, because the writer is handed the rename map. Keying by the block's
ordinal position works equally well and needs a new name-to-position map that nothing has today; the
name is already there.

**Two consequences for whoever does it.** `testTwoPagesClaimingOneIdKeepNeithersKeys` currently
asserts the loss (`assertTrue(offset < 0, "the offsets were reattached to one of the two pages")`) and
must be inverted into the control its own javadoc describes: the page that keeps the id keeps its keys,
the page that is reissued gets none. And the fixture needs the real shape - a first page with no `.id`
at all - beside the synthetic one, because that is the shape that was never tested and is the only one
in the repository.

### 2. `N8-B2` against `S14-D1` - the per-caller table

The condition is `AutonomySession.java:2112`:

```java
if (changed || (moves != null && !moves.isEmpty())) touched();
```

`moveTiles` returns early when the caller gave it nothing to do, so the rebuild is skipped in exactly
one case: a call with **no moves** whose store reported **no change**. `forgetTiles(tiles)` is
`moveTiles(null, tiles)`, so every built-over-only caller is in that case whenever nothing was stored
about the squares.

Every production caller, the gesture it serves, and whether the rebuild happens now:

| call site | reached from | passes | diagram already changed? | rebuild now | rebuild before `X8V-B2` |
|---|---|---|---|---|---|
| `LayoutEditor:2406` `moveTiles(plan.moves, plan.builtOver)` | `applyBulkPlan` <- bulk column move (`:2247`), bulk row move (`:2309`) | moves + builtOver | yes | **yes** when the plan has a move (a bulk MOVE); **no** for a bulk COPY onto a line with nothing stored | yes |
| `LayoutEditor:2433` `forgetTiles(builtOver)` | `forgetBuiltOver` <- `pasteSelection` non-carry branch (`:3182`) | builtOver only | yes | only if something was stored | yes |
| `LayoutEditor:2433` `forgetTiles(builtOver)` | `forgetBuiltOver` <- `fillSelection` (`:3518`) | builtOver only | yes | only if something was stored | yes |
| `LayoutEditor:2514` `forgetTiles(one square)` | `execCopy(dest, move=false, tellAutonomy=true)` - every single-tile paste and every palette drop | builtOver only | yes | only if something was stored | yes |
| `LayoutEditor:3701` `forgetTiles(one square)` | `delete(label, true)`, once per square of a selection delete | builtOver only | yes | only if something was stored | yes |
| `LayoutEditor:5205` `forgetTiles(everywhere)` | `forgetWholePage` <- `clear()` (`:5276`) | builtOver only | **no - `layout.clear()` runs after** | only if something was stored | yes, but from the un-cleared diagram |
| `LayoutEditor:2530` `moveTile(from, to)` | `execCopy(dest, move=true)` - single-tile drag | moves (1) | yes | **yes** | yes |
| `LayoutEditor:3157` `moveTiles(moves, overwritten)` | `pasteSelection` cut-carry branch | moves + builtOver | yes | **yes** when the map is non-empty | yes |
| `LayoutEditor:3324` `moveTiles(moving)` | `moveSelection` - selection drag | moves | yes | **yes** | yes |
| `LayoutEditor:4614`, `:4671`, `:4716`, `:4773` `moveTiles(moving)` | the four grid shifts | moves | yes | **yes** when non-empty | yes |

Read off that table: the rebuild was removed from five gestures - group paste, fill, single-tile
paste/palette drop, delete, and clear - in the case where nothing had been stored about the squares.
`N8-B2`'s core claim is therefore right, and so is the narrower one its probe measured: a placement on
a blank square does not reach the tile graph, where an unconditional `touched()` carried it there.

**Two things in `N8-B2` are wrong.** It says *"all four have just added, replaced or removed track"*.
`clear()` calls `forgetWholePage()` **before** `layout.clear()` (`LayoutEditor:5276-5279`), so at the
moment `touched()` would run the page is still full - the rebuild there was never doing the job the
finding wants restored, in either version of the code. And it names four callers; there are five
built-over-only gestures plus the bulk-copy case of `applyBulkPlan`.

**`S14-D1` reached the right conclusion for a reason that is slightly too strong.** Its withdrawal is
correct: every door out of the editor re-derives the session (`layoutEditingComplete` ->
`layoutRefreshCompleteInternal` -> `resetAutonomySession()` at `TrainControlUI:22414`, and
`layoutEditingCompleteThen`/`autonomyEditorClosed` at `LayoutEditor:6053-6101`), and the track-editor
keys refuse to run in autonomy mode. But it asserts *"nothing reads the graph for a railway decision
while the editor holds it"*, and there is a reader: `TrainControlUI.showStaticAutonomyLayer`
(`:5123-5136`) walks `session.getGraph().getTiles().keySet()` and annotates each square, and it is
reachable from the main window's autonomy-overlay checkbox (`AutonomyOverlayToggle:711`) and the
restriction-arrows menu item (`:11190`) while the editor - a non-modal `JFrame` - is open. With a
configuration loaded, that draws a badge on a square whose track has just been deleted. It is not a
railway decision and nothing moves; it is a stale badge.

**Severity: C, down from B.** No reader makes a railway decision from the stale graph, which is what
`N8-B2` itself concedes (*"a trap rather than a live wrong answer today"*). The SOP is explicit that a
defect no caller can reach is a trap worth fixing and not a B.

**The fix `N8-B2` proposes is the right one and the reason it gives is the better one.** Split the two
questions: `return changed;` for the caller deciding whether to write the setup to disk - which is
what `X8V-B2` correctly fixed and what all five callers read - and `touched()` unconditionally, because
`forgetTiles` is only ever called about squares the diagram has already changed. The comment above it
has to change with it: the saving `X8V-B2` was after is the `saveQuietly` the caller now declines, not
the rebuild. And `clear()` should call `forgetWholePage()` after `layout.clear()`, or the rebuild it
then gets is from the diagram it is about to throw away.

**Nothing pins either half.** `N8-D5` is right, and it is the finding's strongest argument:
`testForgettingNothingSaysNothingChanged` and its control both assert the return value and neither asks
what was rebuilt, so the same change could be made or unmade again with the class green.

### 3. `S14-A1` against `X8-D4` - confirmed by execution; disarm in the import path

**Measured.** The probe built the exact JSON shape a hand-edited or shared route file has - a first
entry with `"auto":true` and an `"s88"`, a second entry whose command type does not exist - and handed
it to `MarklinControlStation.parseRoutesFromJson`:

```
NSV ORPHAN switch before = false
NSV ORPHAN parse threw: IllegalArgumentException
Route NSV orphan is running...
Route NSV orphan S88 triggered
Executing route NSV orphan
Executed route NSV orphan
NSV ORPHAN switch after the pulse = true
```

The parse threw on the second entry. The first route is in no database (`model.getRoute("NSV orphan")`
is null, asserted as a precondition). Its sensor was then pulsed and **it threw the turnout.** That
assertion was written to fail while the defect is present and it failed - `probeAParseThatThrowsLeavesARouteFiring`,
1 of 5.

The control and the two hops:

```
NSV CONTROL switch after the pulse = true     (an armed, enabled route does fire - the control)
NSV ROUTE executeAutoRoute again = false      (construction started a monitor: the guard refuses a second)
NSV DISABLED switch after the pulse = false   (disable() IS sufficient)
```

So: constructing a `MarklinRoute` from JSON arms it; a parse that throws part way leaves it armed,
invisible and unstoppable for the session; and `disable()` is genuinely enough to neuter one. The
monitor loop tests `enabled` immediately after the feedback wait and before `execRoute`
(`MarklinRoute.java:182-194`), and `enabled` is `volatile` (`:69`), so there is no visibility hole. What
`disable()` does not do is end the thread - that is `S14-C2`, separately confirmed.

**`X8-D4` was right about the layer it examined and `S14-A1` is right about the one below it.** Both
stand; nothing needs reopening.

**One half of `S14-A1` should be downgraded in the write-up.** The "on every import the sensors are
watched twice" window runs from `parseRoutesFromJson` returning to `deleteRoute` disabling each old
route - one log call and a loop over `routeDB.getItems()`, a few milliseconds. For both monitors to
fire, a complete clear-then-occupied transition has to land inside it, and the clear leg alone is
debounced by `FEEDBACK_DURATION_THRESHOLD`. That is C-weight. **The A is the throw path**, which the
measurement above produced at the first attempt.

**What I would do: disarm inside the import path. Do not take `executeAutoRoute()` out of the
constructor.** Three reasons, in order of weight.

1. **The codebase already chose this answer at the twin door, and said why.** `newRoute(MarklinRoute r)`
   (`MarklinControlStation:2054-2077`) disables a route it refuses, with a comment naming exactly this
   mechanism: *"MarklinRoute's complete constructor starts one as soon as the route is enabled and has
   an s88 - before the object has been offered to any database - so a hand-edited routes JSON with two
   entries sharing a name left the rejected one watching its sensor forever."* `S14-A1` did not find
   that comment and concluded *"this is the second caller that did not expect it"*; in fact the rule is
   already written down one method away from the fix. A `catch` in `parseRoutesFromJson` that disables
   everything it has built is the same remedy at the one remaining door.
2. **Removing the constructor call is a silent behaviour change at start-up.** `newRoute(String name,
   int id, ...)` (`:2092`, `:2120`) constructs the route inside `routeDB.add(...)` and nothing calls
   `executeAutoRoute` afterwards, and `restoreState` reaches it at `:447`. Take the call out of the
   constructor and every saved route with an s88 trigger and auto-execution silently stops arming at
   start-up, which is worse than the defect. It would need arm calls added at `:2099` and `:2134`, plus
   a sweep of the twelve test files that construct `MarklinRoute` directly - `testRoutes:544` and
   `:729` assert on a monitor that the constructor starts.
3. It is the smaller fix, and the SOP's rule is to prefer it when the larger one changes behaviour.

Where to put the rule in the code: beside the `executeAutoRoute()` call in the constructor, stating
that construction arms the route and that any caller which may fail to place it in the database owes it
a `disable()` - and naming both callers that do (`newRoute`, and the new `catch`).

---

## The other verdicts

### N8-A1 - confirmed, at B rather than A

Every line of the chain is right, checked at the cited numbers: `LayoutLabel:343`/`:347` pass
`getRawAddress()` to `goToLayoutPage`; `TrainControlUI:18633` is `LayoutList.setSelectedIndex(index)`
and `LayoutList` is filled from `getLayoutList()` at `:8494`, `:9547` and `:22396`;
`pagesLinkedFrom:24923-24927` is `all.get(index)` on the same list; `LayoutDiagramComponent:886` writes
the number straight back as ` .artikel=N`; the tooltip at `:716` presents it as a position.
`MarklinControlStation:3638-3643` is `Collections.sort(l)`, and it is the only ordering TrainControl
has. `CS2File:2584` reads `.artikel` into `rawAddress` with no translation - the halving at `:2596-2604`
applies to `address` only - so the round trip is byte-identical. A full sweep of `isLink()`,
`componentType.LINK`, `pfeil` and `getRawAddress()` across `src/` finds no site that adjusts a stored
link for anything, and none of the four Manage Pages operations rewrites another page's file: rename
and duplicate write one page file (`LayoutPageEdit:158`), delete writes none, combine writes only the
new one. So the number on disk never changes and the list it indexes into does. Silent, and not
repaired anywhere.

**Why B.** The consumers are click navigation, the tooltip's number, `pagesLinkedFrom` ->
`combineLinkedPages`, and `TileGraph.leadsOutsideAutonomy`. Out of range degrades safely -
`goToLayoutPage:18625` logs `layout.ui.errorLayoutPageDoesNotExist` and `pagesLinkedFrom:24925` skips.
No train is routed through an arrow: portals are paired by the operator and stored by `TileKey`
(`AutonomyEditorPanel:1958`), so autonomy's graph does not read the address for a movement decision.
Wrong page opens, wrong set of pages combined - incorrect results in a specific configuration, which is
B. It sits at the top of B because of the one autonomy consumer, filed separately as `NSV-B3`.

**And one claim in it should be dropped.** `N8-A1` says *"`behaviour.md` now says the opposite, in a
paragraph written yesterday"*. Read in full, `behaviour.md:1105-1113` is scoped to page **ids** and to
display order; it never mentions link tiles, and its sentence *"neither number decides anything
TrainControl does"* is about an id disagreeing with a reissued id. It documents the ordering the defect
rides on rather than contradicting the finding. Citing it as a contradiction weakens a finding that does
not need the help - what `behaviour.md` actually needs is a sentence saying what a page link is stored
as, which is the finding's own closing recommendation.

**The fixes.** `N8-A1`'s preferred one - store links by page id - has an objection it does not price:
`.artikel` is the *station's* number in a file the station also reads, so an id written there would make
the real CS2 resolve the arrow to a different page. The id can only live in memory, with translation at
`LayoutDiagramComponent:886`, which is a `base` class with no page list - so the translation has to be
injected, and it touches the copy constructor `fillCombinedPage:24989` uses and the editor's undo
snapshots. Its smaller alternative works: `renamedFromTo` is old name -> new name
(`LayoutDiagram.java:1372`) and both sorted lists are available at each call site, so the permutation is
computable. The cost it does not price is that re-aiming means rewriting every page file that carries a
link, where all three gestures write at most one today.

### S14-B4 - confirmed, and raised from B to A

Everything in the finding holds. The swap is `clear()` then `putAll()` inside `synchronized (this)`
(`MarklinLocomotive:1227-1231`), so an unsynchronised reader can see the map empty, half-filled, or
throw `ConcurrentModificationException`; the three named readers are not synchronized; and
`MarklinSimpleComponent:161` is the save path.

I raised it for two reasons, both of which came out of re-checking rather than from the finding.

**The reader list is six names longer, and one of the extras runs on a thread of its own.** Also
unguarded on the same map: `getLinkedLocomotives()` (`:1334-1338`, which hands back the live mutable
map and is what `commandedLocomotives` goes through), `isLinkedTo` (`:1054-1061`),
`hasLinkedLocomotives` (`:1344-1348`), and `setLinkedLocomotives`'s own tail at `:1234` and `:1239`,
which reads `isEmpty()`/`size()` outside the monitor it just released. And
`TrainControlUI.applyPreferredFunctions` (`:9798-9813`, again at `:9861`) spawns its own thread that
iterates `getLinkedLocomotives().keySet()`.

**The two threads are real and nothing serialises them.** `setLinkedLocomotives` is reached from
`MarklinControlStation:1508-1509` inside `syncWithCS2`, which `TrainControlUI:11066-11089` runs off the
event thread in both branches; `saveState` is reached from the window-closing path
(`TrainControlUI:18486`) on the event thread and from Backup Data (`:21125`) on a thread whose own
comment says *"from this thread, which is not the event thread"*. Nothing gates `saveState` on
`syncInFlight`.

**Which makes the consequence the A one.** `MarklinSimpleComponent:161` reading the map mid-swap writes
a locomotive with **no linked locomotives** into `locdb.data`, and `restoreState` rebuilds consists from
exactly that field - so the consist is gone after the next start, silently, and the file is the only
record. The alternative outcome is a `ConcurrentModificationException` escaping into `saveState`, which
on the exit path loses the whole save. "Data silently lost" is the SOP's A, and the SOP grades by
whether a caller can reach the defect rather than by how often. `S14` graded B and said explicitly that
it did not produce the interleaving; neither did I, and that is the honest limit on this - what changed
is that the call graph is worse than the finding described.

**And the fix should not be the one proposed.** Making the three named readers `synchronized` would not
deadlock - the existing order is head monitor then member monitor (`setF:883`, `setSpeed:788`,
`setDirection:842`, `stop:630`), and none of the three acquires another locomotive's monitor - but it
leaves the six readers above, and it makes an event-thread save wait behind a fan-out that holds the
monitor across a UDP send (`MarklinControlStation:2609-2614`). Make `linkedLocomotives` a `volatile`
reference to an immutable map replaced in one assignment: the staging at `:1192-1231` is most of the way
there already, and it removes the empty window for every reader, named or not. `NSV-B2` is the part no
version of this fix reaches.

### The twelve confirmed as filed

Six needed only checking and are right as written, with nothing to add: **`S14-A2`** (measured here too -
two of thirteen indexes resolve two pages to one id, `Oles kreds` and `sample_layout`, and both are
genuine Central Station exports, so the door is "a user downloads their layout from the station"; the
narrow fix it proposes, giving `readLayoutIndexIds` the guard its sibling has, is correct and should be
done *instead of* `X8V-B1`'s read-side withdrawal rather than alongside it, since adjudication 1 moves
that key), **`S14-C1`** (exactly one address per protocol, and the sweep is complete: `isValidAddress`
has two callers in `src/` and only `RouteEditorFrame:2442` fails to convert - and it is a hard lockout,
`onSave:2596-2618` offers only Fix or Discard), **`S14-C2`**, **`S14-C3`**, **`S14-C4`**, **`N8-C3`**,
**`N8-C4`**.

Six are confirmed with a correction worth carrying into the fix:

- **`S14-B1`.** The configuration is legal - `canBeLinkedTo:1286-1328` refuses self, a Central Station
  multi-unit, a member that is itself a head and an address clash, and says nothing about decoder type -
  and the keyboard reaches every function 0 to 29 (`TrainControlUI:18266-18385`) with no `numF` gate.
  The sibling detail the finding missed is the one that makes it a defect rather than a design: the
  **mouse** path is already guarded, `TrainControlUI:11815-11823` disables every button from
  `getNumF()` up. The button cannot be pressed and the key binding for the same function can.
  *"Can never be switched off"* is too strong, though: `MarklinControlStation.allFunctionsOff`
  (`:2813-2819`) walks every locomotive in the database, so the member clears it - the head's own
  controls cannot. B stands.
- **`S14-B2`.** Confirmed in every hop, including that `_setSpeed` (`base/Locomotive.java:451-498`) has
  no `else`, so a head asked for 150 re-transmits its **old** speed while the members are sent 100. Two
  additions: the route editor is *not* a third door (`RouteEditorFrame:2419` range-checks and refuses to
  save), and the CS3 import is (`NSV-C2`). The proposed fix location -
  `RouteCommandLocomotiveSpeed` - is right because it is the only one that covers all four parsers.
- **`S14-B3`.** Confirmed, and stronger than filed: `entrySide` is the **only** persisted field
  `Layout.fromJSON` reads that `Edge.toJSON` drops, the builder's serialiser
  (`AutonomyBuilder:1060-1093`) emits the same seven keys with the same `crossesASwitch()` guard for
  `roomAtTheEnd`, and the real data is 101 of 101 edges in `test/baseline/configuration.json` against 0
  in anything that has been through `Layout.toJSON`. I considered A and left it at B for the finding's
  own reason: the running `Layout` is rebuilt by `AutonomyBuilder` on every change, so the loss needs a
  deliberate export and re-import. The fix is one guarded `put` and no consumer whitelists edge keys.
- **`S14-C6`.** One factual error: `on` (`:275`) is **not** written under the monitor - its writers at
  `:405`, `:458` and `:2649` are all plain - so it has no synchronisation on either side, which is a
  worse shape than described rather than the one described. `powerState` is exactly as claimed. The
  sweep also stopped one field short (`NSV-C7`) and its single-writer justification is false
  (`NSV-C6`).
- **`S14-C7`.** Confirmed, and the reachability question the finding left open is answerable from the
  source: there is no filter. `MarklinControlStation:2420` is a bare `if (message.isFeedbackCommand())`
  with no response-bit and no length check, where the very next arm at `:2448` is
  `else if (message.isLocCommand() && message.getResponse())`. A `0x21` or `0x23` frame therefore
  reaches `extractShortUID()` and `newFeedback`, and the module is persisted and restored. What stays
  unverifiable is whether a station emits one. C is right: a garbage id colliding with a real feedback
  is harmless, and the harm is confined to spurious persisted entries.
- **`N8-C1`.** Confirmed, and the sweep is complete: every growth path runs through
  `LayoutDiagram.addRowsAndColumns`, which has no ceiling, and `MAX_SIZE = 60` exists only at
  `LayoutEditor:52`. `fillCombinedPage` is the one gesture that does not ask - and `CS2File:2524` sizes
  a page from its largest element coordinate, also unclamped, which is how `test/layout_subpage`'s two
  pages parse at 17 x 129. Nothing refuses an oversized page, so C is right; the correction is that
  `roomToGrow`'s javadoc (`:4470`) says three fixture pages have that shape and there are two.

---

## A - high

Nothing. Both reports' A-level ground is covered by their own findings, and the two severities I raised
and lowered are recorded in the table above.

## B - medium

| id | what | disposition |
|---|---|---|
| NSV-B1 | `catalog-findings.py` re-ingests `N8-B3`'s fifteen phantom findings on every run, so deleting the rows fixes nothing - and the guard `N8-B3` proposes would fail on sixty-four legitimate rows | closed |
| NSV-B2 | `preSetLinkedLocomotives`/`setLinkedLocomotives` is an unlocked two-call protocol on a shared field, which `S14-B4`'s remedy does not touch | closed |
| NSV-B3 | a page whose file will not parse shortens the page list, so every link index past it resolves one page early - `N8-A1` with no edit in it, beside the comment that fixed the same hazard for page ids | closed |

### NSV-B1 - the fifteen phantom findings come from a parser, and they come back

**Status:** closed 2026-09-10 with `N8-B3`, and this is the finding the fix followed: the cause is the parser, so the rows had to stop being produced before they could be removed, and removing them needed `add_findings` to stop being add-only.  `triagedb.prune_findings` makes a re-add authoritative for the documents in it and prints every removal, because it drops our own status notes with the row.

`N8-B3`'s count and mechanism are exact. Queried read-only against `triage.db`, the refs filed under
`X8V-validation.md` that the document never makes are `X8V-A1a A1b A1c A2 B1a B1b B2a B2b B3a B3b B4
B5 B6 C2a C5a` - fifteen, of which four are severity A against an A section that says *"Nothing."*
`X8V-C1`'s disposition in the catalogue is `1 red: testNoCommentCitesALineNumber` where the document's
status table (`X8V-validation.md:281`) says `closed`, and `X8V-C3` reads `open` where the document
(`:283`) says `closed`. All four claims verified.

**But `N8-B3` has the cause backwards, and it is the half that decides the fix.** It says: *"Also read
`triagedb.py`: `load_findings` and `add_findings` take rows from the caller and there is no document
scanner, so these are hand-entered rows rather than a parser fault - which is why a guard is the only
thing that could have caught it."* The document scanner is `docs/tools/catalog-findings.py`, which
calls those two functions, and the fault is in it. `docs/tools/catalog-findings.py:105`:

```python
SHORT_ROW = r"\|\s*[`*]{0,2}\s*([A-Z]\d{1,3}[a-z]?)\s*[`*]{0,2}\s*\|(.*)$"
```

Any table row whose first cell is a letter followed by digits is read as a finding id and prefixed with
the document's declared prefix, for every document that declares one. `X8V-validation.md`'s Method
section has a mutation table whose first column is `A1a`, `B1a`, `C1` and so on, and its last cell is
the red count - which is why the dispositions are *"2 of 2 red"*. `severity_of` then takes the letter
straight off the ref (`:81-88`), which is where the four A's come from. And `table_rows` ends
`out.setdefault(ref, cells)` (`:137`): the Method table sits at line 87 and the real status table at
line 281, so **the mutation row wins and the real disposition is discarded** - the `X8V-C1` loss is the
same bug, not a second one.

Two consequences for the fix, and both are the reason this is filed rather than folded into `N8-B3`:

- **Deleting the fifteen rows is undone by the next run of `catalog-findings.py`.** The fix has to be
  in the parser: do not read a table that sits above the document's first severity banner, or require
  a short-form row's id to match a heading the document actually has, and make the disposition come
  from the **last** matching row rather than the first. Then re-run the generator rather than editing
  the database, which is the only repair that stays repaired.
- **The guard `N8-B3` proposes would be red on arrival.** It asks that *"every `PREFIX-[A-D]<n>` row in
  the catalogue must appear in that document"*. Sixty of the catalogued documents declare a prefix and
  then use short headings - `### A1 - ...` in `IND-independent-review.md`, whose prefix is `IND9X` - so
  the full ref never appears in the text. Run as written, that check fails on 64 legitimate rows across
  `AUT`, `D2`, `D3`, `IND`, `REV` and `W7` before it reaches X8V's 15. It has to compare against the
  document's own numbering convention, not against the literal string.

`N8-B3`'s claim that *"`E8`, `E8V` and `X8` are clean - only `X8V` is polluted"* is right, and measured:
those three write full refs in their headings, so the comparison is sound for them. Across the whole
catalogue only twelve refs carry a sub-letter and eleven are X8V's.

### NSV-B2 - the sibling race `S14-B4`'s fix would not close

**Status:** closed 2026-09-10.  `setLinkedLocomotives(Map)` stages and applies in one call, and the three callers that hold their own list use it - the two repair loops in `MarklinControlStation` and the multi-unit dialog.  The two-call form stays for `restoreState`, which cannot resolve a member by name until every locomotive is loaded, and which runs before the window exists.  `testApplyingAListDoesNotDisturbWhatIsStaged` pins that the one-call form does not touch the staging field at all, and catches the mutation that makes it delegate to the pair.

`MarklinLocomotive.preSetLinkedLocomotives` (`:1170-1173`) stages a list on the instance field
`preLinkedLocomotives` (`:64`), and `setLinkedLocomotives` (`:1195`, `:1206`) reads it. Nothing
serialises the pair. Both callers of the pair run on different threads - `MarklinControlStation:1508-1509`
inside `syncWithCS2`, which `TrainControlUI:11066-11089` runs off the event thread in both branches,
and `TrainControlUI:19731-19732` from the multi-unit dialog on the event thread. So one thread's
`preSet` can be overwritten before its own `set` reads it, and the consist is rebuilt from the other
thread's list.

This is the same window as `S14-B4` on a different variable, and making the three readers
`synchronized` - `S14-B4`'s proposal - does not touch it. Worth stating because it is the shape this
project keeps repeating: the fix enumerated the readers and the field beside them was not in the list.

### NSV-B3 - a skipped page re-aims every link after it, with no edit at all

**Status:** closed 2026-09-10 by Adam's ruling: *"make the page blank and put a text label in it saying it could not be loaded (label as a tile itself, as if you had parsed a .text at 1,1 with that message."*  So a page whose file will not read keeps its place in the list, which is what stops every arrow after it resolving one page early.  It carries the page's id, so the autonomy setup stays attached, and it is marked unreadable: `saveChanges` refuses on it, because writing a blank page over the file being recovered would turn a page that cannot be read into one that no longer exists.  `TileGraph.allPages`'s javadoc goes with it (`NSV-C5`).

`CS2File.java:2446-2460` skips a page the index names whose file will not parse, and advances
`pageIndex` deliberately - the comment says why: *"so a skipped page does not renumber the ones after
it. That matters more than it looks: the autonomy setup is keyed by page id."* Page **ids** are
protected. The page never enters `layoutDB`, so `MarklinControlStation.getLayoutList()` is one entry
short, and a `pfeil` tile's stored index - which every consumer resolves against that list
(`N8-A1`) - resolves one page early for every link at or past that position. Two pages sharing a name
alias to one entry in `layoutDB` and do the same.

No Manage Pages operation is involved, which is what separates this from `N8-A1`: an unhydrated
OneDrive placeholder is enough, and `readLayoutIndexIds`'s own comment calls that *"an ordinary
Tuesday"* on this railway. It reaches a railway-relevant decision by one route:
`TileGraph.leadsOutsideAutonomy` (`:1076-1084`) resolves a link's raw address through `allPages`, and
the `unpairable` it derives decides at `TileGraph:1004` whether an unpaired portal is a **blocking**
autonomy error or a warning. A mis-resolved index can turn a hole in the railway into a warning, or
block a build over an arrow the operator deliberately pointed at an excluded page.

Filed at B on that one consumer. Whoever fixes `N8-A1` should fix this at the same time - the remedy is
the same, an identity rather than a position - and `TileGraph.allPages`'s javadoc claim *"every page in
the layout IN FILE ORDER"* (`:591`) is false and should go with it: the list is name-sorted, because
`TrainControlUI:2853-2855` builds it from `getLayoutList()`.

## C - low

| id | what | disposition |
|---|---|---|
| NSV-C1 | `newRoute`'s null-name early return does not disable the route it refuses, where the arm below it does | closed |
| NSV-C2 | the CS3 route import does not clamp a speed, and its own comment says it does | closed |
| NSV-C3 | `LayoutEditor.addRowsAndColumns` asks the ceiling about one row and one column, then adds `rows` and `cols` | closed |
| NSV-C4 | `MarklinLocomotive.setSpeed` clamps a member's scaled speed at the top of the range and not at the bottom | closed |
| NSV-C5 | `fillCombinedPage`'s javadoc states as settled fact the thing `N8-A1` shows is false | closed |
| NSV-C6 | `powerState` has a second writer that bypasses `setPowerState`, falsifying the comment that justifies the wait design | closed |
| NSV-C7 | `lastLatency` is a non-volatile `double` written on the message thread and read by the UI, between two fields made volatile for that reason | closed |

### NSV-C1 - the disable was added to one arm of the method, not both

**Status:** closed 2026-09-10 with `S14-A1`.  Both arms of `newRoute` retire the monitor of a route they refuse; the reason does not depend on why it was refused.

`MarklinControlStation.newRoute(MarklinRoute r)` (`:2054-2077`) opens with

```java
if (r == null || r.getName() == null)
{
    return false;
}
```

and its `else` arm below disables the route it refuses, with the comment quoted in adjudication 3. The
early return refuses a route too and does not disable it. Unreachable today - `MarklinRoute.fromJSON`
gets the name with `getString`, which throws rather than returning null - so this is a trap, not a
defect, which is why it is C. It is worth closing in the same edit as `S14-A1` because it is the
literal form of this project's most repeated mistake: the guard went to one arm of the method it was
written in.

### NSV-C2 - a second unclamped speed door, at the station rather than the keyboard

**Status:** closed 2026-09-10 with `S14-B2`.  The clamp is at `RouteCommandLocomotiveSpeed`, which the CS3 import builds its commands through, so this door is covered without touching `CS2File`.

`CS2File.java:1290`, the CS3 route import:

```java
speed = (int) ((item.getDouble("wert") / 1000.0) * 100.0); // check this: round down to nearest integer, ensure number is 0-100
```

The comment promises the range the code does not enforce. This is `S14-B2`'s defect at a door `S14-B2`
did not find, and it matters for the fix: clamping in `RouteCommand.RouteCommandLocomotiveSpeed`, which
`S14-B2` proposes, covers `fromLine`, `fromJSON`, the editor's round trip **and** this - clamping in
`fromJSON` alone does not. Two cautions for whoever does it. The clamp must be `fromLine`'s, not
`clamp(0, 100)`: a negative is load-bearing, `fromLine` normalises any negative to `-1` and
`MarklinRoute:897-899` reads `-1` as instant stop. And `RouteEditorFrame` already range-checks a typed
speed (`:2419`) and refuses to save out of range, so the editor is not a door - which also means
clamping changes what an out-of-range *imported* route looks like in the editor from "refused at Save"
to "shown as 100".

Whether a CS3 can emit `wert > 1000` is unverifiable by reading. C on that basis.

### NSV-C3 - the ceiling is asked about the wrong amount

**Status:** closed 2026-09-10.  `addRowsAndColumns` asks `roomToGrow(rows, cols)`.  Pinned at the predicate rather than at the call, and the test says why: the guard's failure branch is a modal dialog, so a test that drives the refusal HANGS rather than fails - which it did, for ten minutes, leaving a JVM holding the runner's lock.

`LayoutEditor.addRowsAndColumns` (`:5039-5041`) is `if (!roomToGrow(1, 1))` and then adds `rows` and
`cols`. Its only caller is `drawGrid:5299` padding a blank page to 16 x 21, so it cannot overflow
today. It is `X8-C5`'s own "one predicate" rule applied to the wrong argument, in one of the methods
`X8-C5` touched; `roomToGrow(rows, cols)` is the whole fix. Pairs with `N8-C1`: both are holes in the
same consolidation.

### NSV-C4 - the clamp protects the members at one end of the range only

**Status:** closed 2026-09-10 with `S14-B2`.  The member clamp is two-sided and `setSpeed` clamps its own argument.

`MarklinLocomotive.setSpeed` (`:807`) is `roundedSpeed = Math.min(roundedSpeed, 100);`, with a comment
explaining that `_setSpeed` ignores rather than clamps an out-of-range value and that the member then
transmits its previous speed - *"the two engines of one consist pulled against each other"*. A negative
argument, scaled by a multiplier and floored, stays negative, `_setSpeed` ignores it for the same
reason, and the member re-transmits its old speed. The same failure the comment was written for, at the
other end. Fix it with `S14-B2`'s second half, which asks `setSpeed` to clamp its own argument: the
clamp wants to be two-sided in both places.

### NSV-C5 - a javadoc that asserts what `N8-A1` measures to be false

**Status:** closed 2026-09-10 with `NSV-B3`.  `TileGraph.allPages` says it is in the order the window lists them, which is sorted by name, and says why the distinction matters.

`TrainControlUI:24945-24946`: *"A link on a combined page keeps pointing at the page it always pointed
at: it is a redrawing, and following it should still arrive where the original does."* With the default
combined-page name the program itself offers - `layout.ui.combinedPageName={0} and neighbours`, which
sorts immediately after the page it was made from - that is false, and `N8-A1`'s measurement is the
proof. `pagesLinkedFrom` is evaluated at `:24784`, before the new page exists, so the combine picks the
right sources; the damage lands on the copies it writes and on every other page's arrows. Filed
separately from `N8-A1` because it is stated as a settled property and will be trusted.

### NSV-C6 - the "written in exactly one place" justification is not true

**Status:** closed 2026-09-10.  The sentence names the one other write and says why it cannot release the wait, rather than claiming there is none.

`MarklinControlStation:856-857` justifies `waitForPowerState`'s design with *"the power state is
written in exactly one place - the inbound GO/STOP echo"*. `model.powerState = true;` at `:4349`
bypasses `setPowerState` and its `notifyAll()`. It runs during construction in simulate mode, so it is
probably harmless - but it is the sentence a reader checks the wait design against, and it is false.
Found while checking `S14-C6`; separate from it because `S14-C6` is about the reads.

### NSV-C7 - the volatile sweep stopped one field short

**Status:** closed 2026-09-10.  `lastLatency` is volatile, with the tearing reason its two siblings carry.

`lastLatency` (`:319`) is a plain `double`, written on the message-processor thread at `:2565` - on the
same line that reads the `volatile pingStart` - and read by `getLastLatency()` (`:2602`) from the UI. A
non-volatile `double` can also tear. It sits immediately after `pingStart` (`:306`) and
`pingOutstandingSince` (`:317`), both of which were made volatile with the reason written out. This is
the field `S14-C6`'s sweep missed, and it has a stronger cross-thread shape than either of the two
`S14-C6` names.

## D - not defects, withdrawals, and checks that came back clean

| id | what | disposition |
|---|---|---|
| NSV-D1 | `S14-C5` - `AutoJSONExport`'s truncating write | **`S14-C5` refuted**; it is one of six exports with the same shape, and nothing can be lost |
| NSV-D2 | `disable()` is sufficient to neuter a route monitor | clean, measured |
| NSV-D3 | `S14-D1`'s withdrawal is correct; its stated reason is too strong | clean, with the correction in adjudication 2 |
| NSV-D4 | `N8-D8`'s "four files have the collision" is wrong - two do | measured |
| NSV-D5 | the two reports file the same defect twice under different ids | recorded |
| NSV-D6 | the working tree is exactly as it was found | verified |

### NSV-D1 - `S14-C5` refuted: the comparison class is wrong, and so is the count

`AutoJSONExport.jsonSaveAsActionPerformed` (`:125`) does use a truncating `Files.write`, and
`Util.writeAtomically` does exist. Both other claims in the finding are false.

The inventory is **nine** call sites, not eight - `AutonomyCompanionStore:4935`, `LayoutDiagram:543`
and `:1629`, `MarklinControlStation:1702`, `CS2File:2117`, `TrainControlUI:2409`, `:2573`, `:26747`,
`:26827`. And *"this is the one writer that does not"* is wrong: five others truncate -
`AutonomyViewerPanel:1255` and `:1411`, `LocomotiveStats:444`, `TrainControlUI:21161` and `:20669`.

The nine atomic writers all protect **accumulated application state** - the locomotive database, UI
state, `autonomy.json`, diagram pages, the companion store - which is the scope `Util.java:501-507`
claims for itself in so many words. `AutoJSONExport` writes a file named
`TC_autonomy_<yyyyMMdd_HHmmss>.json` (`:108-110`) chosen in a Save dialog: there is no prior content to
lose, and it is consistent with the five-member export family rather than an outlier. Withdrawn to D.
Originally C. (`NSV-C5`'s sibling observation stands on its own: `AutonomyViewerPanel:1411` writes
`autonomy-derived.json` into the process working directory with no chooser and no prompt, which is the
one member of that family worth changing.)

### NSV-D2 - `disable()` really is enough

Measured, and with its control, so that the claim does not rest on a route that was never watching
anything:

```
NSV DISABLED switch after the pulse = false   (disabled before the pulse)
NSV CONTROL  switch after the pulse = true    (the same route shape, not disabled)
```

The monitor re-tests `enabled` after the feedback wait and before `execRoute` (`MarklinRoute:194`), and
the field is `volatile`. So `deleteRoute`'s `r.disable()` is sufficient for a route the database can
reach, and `S14-A1`'s problem is entirely that nothing can reach the orphans.

### NSV-D4 - how many real files have the colliding shape

Two: `Oles kreds` and `sample_layout`. Four files open with a `seite` carrying no `.id` - the Central
Station shape - but `tc_backup`'s and `test/layout`'s remaining ids do not include 1, so nothing
collides. Table in adjudication 1. `S14`'s measurement of the same thing was correct; `N8-D8`'s was
not, and it is the sentence a reader would use to judge how widely `N8-B1` bites.

### NSV-D5 - one defect, two ids

`N8-C2` and `S14-C8` are the same finding: the route-command delay floor rewrites what the operator
typed and `behaviour.md` does not mention delays. Confirmed by grep (`grep -ci delay
docs/reference/behaviour.md` returns 0). Both stand; they should be closed by one edit, and whichever
id is cited, the other needs a line saying so. `S14-C8` carries the more useful detail - the actual gap
is `SLEEP_INTERVAL + max(delay, 150)`, so the editor's number is the floor on the delay and not on the
wait.

Three more `behaviour.md` gaps measured the same way, for whoever writes that paragraph:
`grep -ci "\blink"` returns 0 (page links - `N8-A1`), `grep -ciE "maximum size|ceiling"` returns 0 (the
page ceiling - `N8-C1`, `X8-C5`), and `grep -ci duplicate` returns 0 (a duplicate page id - `S14-A2`).
Section 8 now says what a page id *is* and says nothing about any of the four.

### NSV-D6 - the tree

`git status --porcelain` lists the three files under `cs2_sample_layout/` that Adam's running
application rewrites, and the two review documents this pass validated, and nothing else.
`git diff --stat -- src test docs build.xml` is empty. The probe class is deleted and
`regression.testEveryTestIsInTheBattery`, `regression.testEveryCitationResolves` and
`regression.testSwitchingToACentralStationLayout` are all green afterwards - which also confirms
`S14-D7`'s diagnosis: that class's failure belonged to the other session's probe file and cleared when
it went.

---

## What neither report found, in its own declared scope

- **`N8` named `docs/manual-tests/` as a weighted part of its scope and audited the database without
  reading the tool that writes it.** `catalog-findings.py` is the defect and it is in `docs/tools/`,
  which `N8` lists among what it read - but only `triagedb.py` was opened. The consequence is that
  `N8-B3`'s remedy repairs the symptom and its guard would be red on arrival (`NSV-B1`).
- **`S14` restricted itself to `src/` and so could not see that `newRoute` already states `S14-A1`'s
  rule.** That is not a scope miss - the comment is in `src/` - and finding it would have changed the
  finding's recommendation from "do not arm from the constructor" to "disarm where the route is
  refused", which is the smaller fix.
- **`S14-B3`'s fix needs a test that does not exist.** Nothing covers the `Edge.toJSON` ->
  `Layout.fromJSON` round trip for any field; the baseline comparison
  (`testConfirmedGoodState.java:135`) exercises the *builder's* serialiser, which is the one that
  writes `entrySide` correctly. That is why the omission shipped, and the fix should ship with a
  round-trip assertion seen red first.
- **Neither report checked `Edge`'s full serialisation asymmetry.** Measured: `entrySide` is the only
  persisted field `fromJSON` reads and `toJSON` drops (`occupancy` is runtime-only), and the real data
  carries 101 of them in `test/baseline/configuration.json` against 0 in anything that has been through
  `Layout.toJSON`. That is what makes `S14-B3` an omission rather than a decision, and it is a stronger
  statement than the finding makes.
- **Between them they still leave the 38 hands-on tests in `docs/manual-tests/tests.md` unaudited**,
  which is now five passes in a row. `N8` audited `findings.tsv` and `triage.db` for four documents and
  `S14` opened the folder only to check a prefix.
- **And neither looked at the network layer, the CS3 locomotive import, or `HomeStaging`/`GraphReducer`/
  `AutonomyChecks`.** `X8` declined the first two as well; that is three passes in a row for the
  network layer.

## What this pass did not settle

- **The battery.** Nine class runs here across three invocations, plus the probe. `a281e3a2` has still
  not been measured by anybody's battery run; the last green one was `X8V-D12` at `d86914a9`.
- **Whether a real Central Station resolves a `pfeil`'s `.artikel` by page position or by `.id`.** If
  by `.id`, TrainControl mis-resolves station-written arrows on any layout whose file order is not
  alphabetical, before any editing at all - which would move `N8-A1` and `NSV-B3` up. It needs one
  observation on the hardware: draw an arrow on a station whose page order is not alphabetical and read
  the number it writes.
- **Whether a CS2 or CS3 emits a `0x21` or `0x23` CAN frame** (`S14-C7`) or a `wert` above 1000
  (`NSV-C2`). Both code paths are reachable; whether the station produces the input is not answerable
  by reading. A packet capture settles the first.
- **`S14-B4`'s interleaving was not produced here either.** What changed is the argument, not the
  evidence: the reader list is six names longer than the finding's and one of the extra readers runs on
  a thread of its own. The severity I raised it to rests on the call graph, as the finding's did.
- **`N8-A1`'s and `NSV-B3`'s consequence for an autonomy build** - whether a mis-resolved link index
  actually flips `TileGraph:1004`'s blocking flag in practice - was traced and not executed. It needs a
  layout with a link pointing at an excluded page and an unpaired portal.
