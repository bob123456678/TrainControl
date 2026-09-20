# Opus validation of the C sweep, 2026-09-19

**Status:** open

**Prefix: `OP3`.** Cite findings from this document as `OP3-B1`, `OP3-C4` and so on. `OPV` and `OP2` are the two earlier Opus validations; this is the third, and the letter suffix was already taken twice.

**What was reviewed.** Branch `autonomy-diagram-r0` at `4e2d02b5` ("Shorten the remaining long autonomy tooltips", 2026-09-19 21:27). The subject is the ten C findings closed on 2026-09-19 and the tests that went in with them, across nine commits: `48ea5a49`, `75f69423`, `33a09886` (batches 1 and 2), `82828987` (the crossing pin), `104d3172` (batch 3: the arrow buttons, two sentences, 118 message keys), `5b483aaf` (batch 4a: the function copy, the export's grid, the local-route sync), `fca3246d` (RTX-C1), `f8f30b4b` (AUR-C1) and `6a397786` (the status and manual-test record).

**Method.** Read every source hunk and traced the real call sites rather than the edited lines; re-derived the dead message-key set independently, twice, with two different extractors; read every new test for vacuity against the SOP's own rules; checked the new comments against the code they describe. **No test was run and nothing was built** - the harness rule stands, and what that costs is written out in the last section. No file outside this document was written.

---

## Summary

| | count |
|---|---|
| A | 0 |
| B | 2 |
| C | 11 |
| D | 13 |

The three that need Adam first: **OP3-B1** (UIX-C4 removed 118 of the 237 keys it named, left 119, and the guard it installed cannot see them), **OP3-B2** (GUX-C5 gave one of two neighbouring doors the null guard and not the other), and **OP3-C3** (three things committed by accident, one of them inside `cs2_sample_layout/`).

---

## A - high

None. Nothing in the day's work produces wrong behaviour on the layout or loses data on it. AUR-C1 and RTX-C1 both hold up under tracing: see OP3-D6, OP3-D7 and OP3-D8.

---

## B - medium

| id | status | where |
|---|---|---|
| OP3-B1 | open | `testEveryMessageKeyIsAskedFor.java:129-137` (the prefix shield), `test/ui/testStagingOutcomeMessages.java:56`, `test/regression/testEditorSurfaceRules.java:1187`, `TrainControlUI.java:23101`; and the UIX-C4 status row in `2026-09-19-UIX-main-interface-review.md` |
| OP3-B2 | open | `TrainControlUI.enableOrDisableRoute` (`TrainControlUI.java:21481`, `:21489`, `:21513`) against the guard added beside it at `:21440-21445` |

### OP3-B1: UIX-C4 took out 118 of the 237 keys it named, and the guard that certifies the rest cannot see them

UIX-C4's own text says: *"A script that collects every string literal in `src/` and `test/` and subtracts the five prefixes the code builds keys from ... leaves 237 of the 2022 keys in `messages.properties` referenced nowhere."* It then names nine of them: `autolayout.ui.menuCreatePoint`, `menuConnectToNode`, `menuEditEdge`, `promptSelectEdgeToDelete`, `edgeConfigEdit`, `lockEdges`, `captureCommands`, `excludedLocs`, `allowedLocsDefault`, plus `route.ui.routeCommands`.

118 keys came out. **Every one of those ten is still in all eight bundles.**

Re-derived independently against `4e2d02b5`, using the review's own rule - every string literal in `src/` and `test/`, minus the five real builder prefixes - the bundle still holds **119** keys nothing asks for: 96 under `autolayout.ui.`, 19 under `autosetup.ui.`, 2 under `autolayout.info.`, 2 under `route.ui.`. 118 + 119 = 237, which is UIX-C4's own number to the key. Of those 119, **115 do not appear anywhere in `src/` or `test/` at all** - not as a literal, not as a fragment, not in a `.form`. Their text is still readable in `docs/reference/GraphEdgeEdit.java.txt` and `docs/reference/GraphRightClickPointMenu.java.txt`, which is where their windows went.

The reason the new guard reports zero is its prefix rule (`:129-137`): a key is shielded by **any** literal of six characters or more, containing a dot, that it starts with. Three literals do almost all of the shielding, and two of them are not key builders at all:

- `"autolayout."` - `test/ui/testStagingOutcomeMessages.java:56`, inside `assertFalse(message.startsWith("autolayout."), ...)`. It shields **98** keys.
- `"autosetup.ui."` - `test/regression/testEditorSurfaceRules.java:1187`, a genuine builder, but over a fixed list of suffixes. It shields **19**, of which the ones I checked (`labelPriority`, `menuCanReverse`, `tooltipGestures`, `errorNegativeLength`, ...) have no matching suffix literal anywhere either.
- `"route.ui.route"` - `TrainControlUI.java:23101`, `I18n.t("route.ui.route") + " %s"`. That is a live key meaning the word "Route"; it shields the dead `route.ui.routeName` and `route.ui.routeCommands`.

This is graded B rather than C because of what the record now says. The status row reads *"fixed 2026-09-19 - 118 keys out of all eight bundles, guarded by testEveryMessageKeyIsAskedFor"*, with `EXPECTED = 0` and a class-level comment that says the floors exist *"so a reader that stops matching cannot report a clean bill of health"*. Both halves of that protection are in place and both are satisfied by a bundle that still carries 119 dead lines in eight languages. The README's own sentence applies: *"A regression test that only sometimes catches the regression is worse than none, because it reads as protection."*

It is not A because nothing about it reaches the railway: the cost is the one UIX-C4 itself named - dead text the translators maintain, hiding the live keys among the dead.

**The way out is small.** Exclude `test/` from the literal collection, or require a shielding prefix to end in `.` AND be the argument of a concatenation, or simply name the three literals above as non-shields the way `MYSELF` is already named. Any of the three drops the count from 0 to 119 immediately, and then `EXPECTED` can be lowered as the keys go.

### OP3-B2: the null guard went into the bulk loop and not into the single-route door fifteen lines below it

`5b483aaf` gave `BulkEnableOrDisable` the guard MKR-C4 wrote for `getRouteList`:

```java
Route r = this.model.getRoute(routeName);
// A NAME FROM A LIST IS NOT A ROUTE (MKR-C4's shape).  The list is a copy; the lookup
// is not, and the sync's re-read of a station route deletes an id and puts it back a
// few statements later on its own thread.
if (r == null) continue;
```

`enableOrDisableRoute`, six lines further down the same file and edited by the same commit, still reads:

```java
Route r = this.model.getRoute(routeName);
if (!enable || r.hasS88())          // :21489
```

and, in its else arm, `I18n.f("route.ui.errorS88RequiredForAutoFire", r.getName())` at `:21513`. Both dereference without a check, both on the worker thread the method starts, and the hazard is the one MKR-C4 wrote down for the other door: `editRoute` itself is a delete-then-re-add (`MarklinControlStation.java:2057-2059`), and the Central Station sync re-reads a changed station route the same way. A reader landing between the two takes a `NullPointerException` on a thread with no handler - the toggle, the sync, the repaint and the refresh are all skipped and nothing is logged. The user sees a menu item that did nothing.

This is the July cycle's most repeated mistake, recorded in the README as *"a guard added to `deleteRoute` and not `changeRouteId`"*, and here the twin is in the same method's line of sight.

Two neighbours worth the same glance while the file is open, though I did not trace their reachability: `TrainControlUI.java:21037` and `:21360` take the same shape.

---

## C - low

| id | status | where |
|---|---|---|
| OP3-C1 | fixed 2026-09-20 - the sentence says the key has gone | `TrainControlUI.java:918` |
| OP3-C2 | fixed 2026-09-20 - every moved train is keyed /turned or /straight | `HomeStaging.tailKey` (`:2621-2686`) against `turnedByThePlan` (`:980-983`) |
| OP3-C3 | fixed 2026-09-20 - all seven untracked, files left on disk, .gitignore narrowed | `bash.exe.stackdump`, `cs2_sample_layout/config/autonomy/Main_bak.json`, `random_test_layout/**` - all added by `82828987` |
| OP3-C4 | fixed 2026-09-20 - the side is set before the save is asked; proved by reverting the gate | `test/core/testLockEdgesSurviveTheFile.java:291-303` |
| OP3-C5 | fixed 2026-09-20 - the export asserts it had captions to lose | `test/regression/testTheExportRetiresItsGrid.java:47-84` |
| OP3-C6 | fixed 2026-09-20 - the triggers are asserted too | `test/regression/testCancelUndoesACustomizationCopy.java:49-84` |
| OP3-C7 | fixed 2026-09-20 - the menu asks the running railway, pinned red-first | `Point.toJSON` (`:1268`, `:1282`) against `AutonomyEditorPanel.buildArrivedFromMenu` (`:3359-3453`) and `pointOnTheLayout` (`:5093-5110`) |
| OP3-C8 | closed 2026-09-20 - Adam ruled: align the buttons to the standard, bold 12 | the UIX-C3 status row in `2026-09-19-UIX-main-interface-review.md`, against `AutonomyBanner.java:211` |
| OP3-C9 | fixed 2026-09-20 - the comment says what the key actually spells | `HomeStaging.java:2636-2638` against `HomeStaging.java:1362` |
| OP3-C10 | fixed 2026-09-20 - total over every pair of different roads; a double slip has four | `test/core/testAutonomyDiagramReducer.java`, `locksItsTwoRoads` |
| OP3-C11 | fixed 2026-09-20 - the grid build is inside the bracket too | `DiagramExport.render` - the `try` opens after the grid is built |

### OP3-C1: a comment cites a key the same commit deleted

`TrainControlUI.java:918`:

> `A new key rather than reusing ui.main.toolbar.functions: that one is the word "Functions", which is a different menu and a different idea in this program.`

`ui.main.toolbar.functions` was one of the 118 keys `104d3172` removed, in the same commit. The sentence is present tense about a key that no longer exists, and a reader who greps for it to check the claim finds nothing. Either say it was the old Functions-menu heading and has since gone, or drop the clause.

### OP3-C2: `tailKey` keys one of the two facts `turnedByThePlan` reads out of `movedAlong`

The fix is right about the fact it keys. `turnedByThePlan` is

```java
!l.isReversible() && !atHome(ownHome, at)
    && this.movedAlong.containsKey(l)
    && (turnsATrainArrivingAt(at) || turnedOnTheWay(this.movedAlong.get(l)))
```

`isReversible`, `atHome(at)` and `turnsATrainArrivingAt(at)` are all functions of where the train stands, which `key(state)` already carries. `turnedOnTheWay(route)` was not, and now is. **`movedAlong.containsKey(l)` still is not.** Two states with the same arrangement can disagree about it: one where train X was moved away and back, one where a different train Y was. Both cost the same, both can leave empty tails, and neither turned - so they share a key, and whichever the search reaches first decides whether X or Y is restricted to its home on the next expansion.

It is narrow. It needs two non-reversible trains, both standing on squares that turn arriving trains, both away from home, and two equal-cost orders reaching the same arrangement. It is also the only remaining half of the same shape, which is why it is worth a line rather than a rewrite: the same `turned` list could carry every train in `movedAlong`, turned or not, at no extra cost.

The unequal-cost version of this is **not** a hazard, and that is worth recording so nobody re-opens it: a path that moves a train away and back is strictly dearer than one that leaves it alone, `misplaced` is consistent, so A\* closes the cheaper first and `nextCost < cost.get(nextKey)` refuses the dearer one afterwards.

### OP3-C3: three things committed by accident in `82828987`

`git show 82828987 --name-only` lists, beside the test it is named for:

- `bash.exe.stackdump` - a Git Bash crash dump at the repository root, 29 lines of `msys-2.0.dll` frames. Not in `.gitignore`.
- `cs2_sample_layout/config/autonomy/Main_bak.json` - 552 lines, a stale legacy autonomy graph, now inside the blessed sample layout that `support.LayoutSandbox` opens for every UI test. Nothing in `src/` or `test/` names it. It is inert today only because `AutonomyCompanionStore` enumerates `configuration-*.json` and this has no prefix - a rename away from appearing in the autonomy menu of the sample layout.
- `random_test_layout/` (five files) - Adam's own manual-test scratch layout, never tracked before this commit and referenced by nothing except a sentence in `testAnImportKeepsOnlyThisLayoutsPages`'s javadoc. The new test builds its fixture in memory (`page("main", 8, 6)`) and does not read it.

None of this is a defect in the fix. It is worth a C because the sample layout is a fixture the whole suite leans on, and a stray file inside it is exactly the sort of thing that is discovered later by a test that counts something.

### OP3-C4: the third of AUR-C1's "three doors" is not actually exercised

`testADroppedPlacementLeavesNoTailBehind` says *"Three doors, three claims: the load does not apply a side to an empty square, the save does not write one, and a reservation clears whatever it found."* The load and the reservation are driven. The save is asserted like this:

```java
assertFalse(layout.toJSON().toString().contains("arrivedFrom"), ...);
```

but two lines above, `assertNull(stand.getArrivedFrom(), ...)` has already established that the field is null - so `toJSON` cannot write it whatever `toJSON` does. **Revert the `&& this.currentLoc != null` in `Point.toJSON` and this test still passes.** The claim it makes is true; the test does not make it.

One line fixes it: set a side on the unoccupied point first, then serialise. The test already knows how - it does exactly that two statements later to drive the reservation door.

While the file is open: `assertEquals(stand.getArrivedFrom(), "LE Junction", ...)` at the end has the arguments the wrong way round for TestNG's `(actual, expected)`, which only affects the failure message. And the control in that last block works because `loc()` is `model.getLocByName(LOC)` and returns the same instance twice - the guard in `assign` is a reference comparison, so a `loc()` that built a new object would make the control fail. That is worth a sentence in the test rather than a change.

### OP3-C5: the export test does not assert that the export registers any captions

`testAnInterruptedExportStillHandsBackItsCaptions` compares `quiesced(ui)` before and after. Both tests pass trivially if the sandbox's first page registers no station captions at all - `layoutStations` is written only by `addLayoutStation`, which is per autonomy station square. Nothing asserts that the number moved at any point, and the SOP's rule is explicit: *"Assert the precondition that makes a test meaningful."*

The class already knows the number is non-zero - `quiesced`'s javadoc says *"the first draft of this class reported sixteen labels appearing across an export"* - so the assertion is one line: sample `captions(ui)` while the grid is alive, or assert that `before` itself is greater than zero on a page whose squares are stations.

Everything else about this class is right, and the note about why a source-shape rule was rejected is the best thing in the day's tests.

### OP3-C6: the cancel test checks the types and not the triggers

`undoCopiedCustomizations` restores two arrays and a flag. `testACancelledCopyLeavesTheFunctionsAlone` asserts the flag and `getFunctionTypes()`. Nothing reads `getFunctionTriggerTypes()`, so a regression that dropped `triggersBeforeACopy` from the save or the restore would pass. Same one-line fix.

`testTheCancelBranchCallsIt` is a source-shape check, and it is honest about why. Its anchor guard is right (it fails loudly if the departure-slot restore moves), but `source.indexOf("undoCopiedCustomizations()", cancel)` only proves the call is *somewhere after* the anchor in the file, not inside the Cancel branch. That is safe today because there is exactly one call site - and it is the shape GUX-C4's own commit message warns about: *"a source-shape rule passed against the unfixed file"*.

### OP3-C7: the save gate can drop a side the autonomy editor deliberately wrote

`Point.toJSON` now writes `arrivedFrom`/`arrivedAlong` only beside an occupant. The argument for that is sound for the case AUR-C1 is about. There is one producer of a side on an unoccupied Point that is not a phantom:

`AutonomyEditorPanel.buildArrivedFromMenu` is offered when `locomotiveAt(target) != null`, and `locomotiveAt` reads the **setup** (`session.getLocomotiveNameAt`). The Point it writes to comes from `pointOnTheLayout`, which prefers an occupied copy **and falls back to `first`** when no copy of the square holds a train. So an operator setting an arrival side for a square the setup assigns a train to, while the running layout has not placed it, writes onto an unoccupied Point - and the next save of the running layout silently drops it.

The loss is not permanent: the same click writes `session.setArrivedFrom(target, side)`, and the setup is the durable record for a diagram-built layout. What changes is that the two stores now disagree where they used to agree, which is the shape SPEC-B4 had and which the comment at `:3439` cites as the reason the running layout is written at all.

Cheapest honest answer is probably in the menu rather than in `toJSON`: do not offer the item when no copy of the square is occupied in the running layout, which is what its own javadoc already claims - *"Null when no train is standing here: a tail belongs to a train, and a square with none has nothing to say."*

### OP3-C8: UIX-C3 is marked fixed with half of its own `where` untouched

The finding names two sites. `GraphLocAssign.java:377` was fixed. `AutonomyBanner.java:211` still reads `action.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 12))`, and the status row records this as *"fixed 2026-09-19 - the hand-written label takes the standard's blue; AutonomyBanner left for Adam"*.

The SOP says a finding is closed when it is fixed, withdrawn, **or explicitly declined by Adam** - and "left for Adam" is none of the three. The review body already anticipated this (*"it was not wrong when written, so Adam may not want it counted"*), which makes the answer easy: ask, and record the answer. Either it is declined, or the row goes back to open.

The same finding's *"How to prove it"* proposed a grep test for `0, 0, 115` in hand-written code. No such test went in, and without it the standard has nothing holding the line for the next hand-written panel.

### OP3-C9: "the same spelling `firstClearRoute` uses" is not quite what either one does

`firstClearRoute`'s visited key (`HomeStaging.java:1362`) is

```java
String key = next.getUniqueId() + (turned ? "/turned" : "/straight");
```

- a word after the slash, written in **both** cases. `tailKey` appends `'/'` followed by a comma-separated list of locomotive names, and appends **nothing at all** when no train turned. They share the separator and nothing else. The substance of the comment - that this fact belongs in the key, and that the other search already keys it - is right; the sentence claims more than that.

Two other sentences in the same comment are worth a look while it is being edited. *"The loop below drops a train from the key entirely when its tail covers nothing, which is exactly where the two roads are otherwise indistinguishable"* - that is **a** place the two roads collide, not the only one: two roads leaving the same non-empty covered set also collided. And the separator itself is now load-bearing in a string built from user-chosen point and locomotive names; a name containing `/`, `#`, `|` or `,` can make two different states spell the same key. Pre-existing for three of those four characters and vanishingly unlikely, but it is the sort of thing this file writes down.

### OP3-C10: which of the two legs the crossing test compares depends on iteration order

`locksItsTwoRoads` picks its two edges by first match over `reduced.getEdges()`:

```java
if (edge.getStart().getY() == 2 && acrossOne == null) acrossOne = edge;
if (edge.getStart().getX() == 3 && acrossTwo == null) acrossTwo = edge;
```

Four directed legs run over the middle tile - east-west and west-east, north-south and south-north - and the filters match two each. So `acrossOne` is *some* east-west leg and `acrossTwo` is *some* north-south leg, chosen by whatever order `getEdges()` yields. The test then asks `getLocks().get(acrossOne).contains(acrossTwo)`, which is a question about one specific ordered pair.

If the reducer's lock relation is total over the four legs the answer is the same either way and this costs nothing. If it is not, this test answers differently on different runs, and the SOP's rule is *"Assert only what is deterministic"*. Pinning both directions - or asserting the answer over all four pairs - removes the question. It is also the difference between "a crossing locks its roads" and "a crossing locks these two of its four legs", and the javadoc claims the former.

The three claims themselves read correctly, and the assertion messages are the right way round for `assertTrue`/`assertFalse` - I checked, because they are the easy thing to get backwards here.

### OP3-C11: the export's `try` opens after the grid is built

```java
javax.swing.SwingUtilities.invokeAndWait(() -> { grid[0] = new LayoutGrid(...); });
...
try { ... } finally { ...discard()... }
```

`LayoutGrid`'s constructor is what registers the captions. If it throws part-way - after registering some and before returning - `grid[0]` is never assigned, the `try` is never entered, and those captions stay in `layoutStations` for the session: the exact NR-3 leak, by the one door the fix does not cover. Moving the build inside the `try` costs nothing, since the `finally` already null-checks.

Second, smaller: the `finally` itself calls `invokeAndWait`, which throws `InterruptedException` if the calling thread's interrupt flag is set - which is precisely the case the commit message names. The discard still runs (the invocation event is posted before the wait), but the interrupt replaces the original exception on the way out. A `try`/`catch` around the discard that swallows nothing but the marshalling failure would keep the first cause.

---

## D - not defects, and checks that came back clean

| id | what was checked |
|---|---|
| OP3-D1 | all eight bundles lost exactly the same 118 keys, and nothing was added |
| OP3-D2 | none of the 118 is reachable at runtime by any route I could construct |
| OP3-D3 | the new guard's floors are real, with the margins measured |
| OP3-D4 | `speedStepFor` matches the key handler's arms, and the arrow listeners have no third caller |
| OP3-D5 | `r.getId()` after `writeRouteEnabledState` is sound, and `anyOnTheStation` is set on every branch that writes |
| OP3-D6 | neither call site of `Point.reserve` can throw away a live train's real tail |
| OP3-D7 | the AUR-C1 defect is live on Adam's own railway, and the reader that makes it visible is the one the commit names |
| OP3-D8 | the RTX-C1 test genuinely fails without the fix, and carries a control |
| OP3-D9 | the toolbar test's discriminating half is the second press, and the class says so |
| OP3-D10 | CS3-C4's new lock has no ordering hazard |
| OP3-D11 | GUX-C2's new tooltip sentence is true, and landed in all eight bundles |
| OP3-D12 | GUX-C3's save and restore survive a copy from a locomotive with a different function count |
| OP3-D13 | the deprecated modifier API in `controlHeld`/`altHeld` works and is the same one the codebase already uses |

**OP3-D1.** Diffed the key set of each of the eight bundles at `104d3172^` against `4e2d02b5`: 118 removed, 0 added, identical set in all eight. `core.testMessageBundles` line 249 also enforces key-set parity against the English bundle, so a drift here has a second reader.

**OP3-D2.** Four routes checked for each of the 118. (1) Exact quoted literal anywhere in `.java`, `.form`, `.xml` or `.json` in the repository: none. (2) Concatenation: the only `I18n` calls with a built key are `route.kind.`, `autosetup.ui.side`, `autosetup.ui.facing` and the two `pathPreference` prefixes, and no removed key begins with any of them. (3) `.form` resource references: the 234 distinct `key="..."` values in the form files have empty intersection with the removed set - and the route is covered anyway, because the GUI builder writes `bundle.getString("...")` into `initComponents` in the `.java`, which is where `ui.main.allLayouts` is found. (4) Raw substring anywhere outside the bundles and `docs/`: the only hits are longer live keys (`layout.accessoryAddr`, `layout.switchAddr`, `layout.switchThreeWayAddr`, `layout.ui.promptDiagramSizeChoice`, `route.ui.highlightOnDiagram`, `ui.main.allLayouts`, `autosetup.ui.oneWay*`, `autosetup.ui.test*`) plus the one stale comment at OP3-C1. Corroborated from the other end: `git log -S` puts each key's last source use in the commit that deleted its window (`6f60b118`, `d8db4879`, `f38cfa24`).

**OP3-D3.** `testItReadTheBundleAndTheSource` asserts more than 1800 keys and more than 15000 literals. Measured: 1915 keys and 21,278 distinct literals by the class's own regex. The literal floor has comfortable room; the key floor has about 6%, so a future removal of more than 115 keys - which is what OP3-B1 asks for - will trip it and report *"the reader is broken"* about a reader that is fine. Worth raising the moment the rest of the dead set goes.

**OP3-D4.** `speedStepFor` returns `SPEED_STEP * 2` for Alt, `1` for Control, `SPEED_STEP` otherwise, and asks Alt first. `VK_UP`/`VK_DOWN` at `TrainControlUI.java:18930-18958` do exactly that, in that order. The four listeners have three callers between them: the generated button listeners (`:14615-14649`, which pass the real event), the keyboard's plain arms (`:18942-18979`, which pass `null` and handle the modifiers themselves), and the new test. Nothing else invokes them. `VK_LEFT`/`VK_RIGHT` are additionally gated on `!altPressed` because Alt+arrow cycles the mapping tabs; on the buttons an Alt click still toggles, exactly as before, so nothing was taken away.

**OP3-D5.** `MarklinControlStation.editRoute` reads `Integer id = existing.getId()` before the delete and passes it back to `newRoute`, so the id survives the delete-and-re-add and reading it off the caller's now-stale `Route` object afterwards gives the right answer. `anyOnTheStation` is set immediately after the only write in the loop, so no branch that touches a station route can leave it false; a write that fails sets it anyway, which costs one unnecessary sync and never skips a needed one. The `boolean` is a local of the lambda body, not a captured variable, so the assignment compiles.

**OP3-D6.** Two call sites. `Layout.java:3564` (`e.getEnd().reserve(loc)` while locking) only touches squares ahead of the train; the square it is standing on is the path's start and is not an edge end unless the path loops through it, in which case the loop at `:3800-3803` has already cleared it via `setLocomotive(null)` - which cleared both fields before AUR-C1 as well. `Layout.java:3810` (`path.get(0).getStart().reserve(loc)` after a configure failure) sees `previousOccupant == loc` in the ordinary case and clears nothing. The sibling re-stand at `:3304-3319` reads both fields before the move and writes them back after, and `AutonomySession`'s re-stand at `:1489-1512` does the same. Every other reader - `faceTheWayItCameIn`, `whereTheTrainsAre`, `putTheTrainsBack` - gates on the occupant first. And `assign` is the single door: `currentLoc` is written in exactly one place outside the constructor.

**OP3-D7.** `Layout.walkStandingTrains` (`:6685-6695`) reads `getArrivedFrom`/`getArrivedAlong` only for a Point with an occupant, which is precisely why an orphaned side is invisible until something reserves the square - the mechanism the commit describes. And the orphan is not hypothetical: Adam's own `autonomy.json` carries `BottomMainA (westbound)` with `"arrivedFrom": "W"` and no `loc`, beside `BottomMainA (eastbound)` which holds 75 407 DB with the same side. So this is the split-square copy left behind rather than the outlived-fleet case the commit message tells, but it is the same state and the same consequence. After the fix the next save drops it, which is right.

**OP3-D8.** `testARoadThatTurnedIsNotTheSameStateAsOneThatDidNot` builds two roads to `HS Z`, one through a reversing point and one not, over unmeasured edges so both tails are empty. Without the fix both keys are `key(state)` and the assertion fails; with it, one carries `/LOC_A`. It asserts the fixture's reversing flag as a precondition, and it runs the control - the same road twice - which is the check the README asks for and which most differential tests in this repository have had to learn.

**OP3-D9.** `testControlOnADirectionButtonForcesThatDirection` presses twice in each direction. The first press passes with or without the fix (a toggle from forward also ends up reversed); the second is the one that separates force from toggle, and the comment beside it says so. `SETTLE` exists because the first draft passed against the defect it was written for - which is recorded in the field's javadoc, and is the single most useful thing in that class.

**OP3-D10.** `autoLayoutLock` is held over field reads and writes only. `new Layout(this)` inside it is pure field initialisation with no call back into the model (`Layout.java:760-780`), and `stopLocomotives` is deliberately outside. No path takes this monitor while holding the model's, or the reverse, so the new lock cannot join a cycle.

**OP3-D11.** The new sentence claims sensors are captured and that a repeat within a few seconds is ignored. `feedbackChanged` (`TrainControlUI.java:4619-4628`) compares the command against `lastCapturedFeedbackCommand` and skips it inside `CAPTURE_COMMAND_THROTTLE`, which is 5000 ms. The throttle is keyed on the command string, so it covers accessories as well, which is what "both are captured" needs. One key modified, in all eight bundles, all eight verified.

**OP3-D12.** `rememberBeforeACopy` takes `Arrays.copyOf` of both arrays, which is necessary because `Locomotive.getFunctionTypes()` returns the live array. `Locomotive.setFunctionTypes` normalises both arguments to `this.numF` with `Arrays.copyOf`, so copying from a locomotive with a different function count and then restoring cannot leave a wrong-length array. Only the first press is remembered, which is what "undo to what was there before either copy" needs.

**OP3-D13.** `evt.getModifiers()` and `ActionEvent.CTRL_MASK`/`ALT_MASK` are deprecated, and `DefaultButtonModel` does populate them from the triggering input event, so a real Control or Alt click does arrive with the bits set. Flagged only so nobody is surprised later: `getModifiersEx()` with `InputEvent.CTRL_DOWN_MASK` is the replacement, and the day these are removed from the JDK all four buttons go quiet rather than loud. MT-465 is the right answer for now - no automated test can tell whether Windows swallows an Alt click on a button before Swing ever sees it.

---

## What this pass did not cover

**Nothing was run.** No test, no build, no application. Every claim above about a test is a claim about its source - that it *would* be red without the fix, that its preconditions *can* fail - reached by reading the code under test, not by reverting anything and watching. Where that matters most:

- **The RTX-C1 probe is unverified here.** *"994 of 83,881 keys were reached with both answers"* and *"every planner class and the blessed baseline are unchanged by it"* are both claims I can neither confirm nor refute without running `core.testReturnHomeOnRealLayout` and the planner battery. The reasoning behind the first is sound and the mechanism is real; the second is the one worth re-running, because a finer key means more states and `SEARCH_LIMIT` is 50,000. A 1.2% growth in distinct keys is not frightening, but `NO_PLAN_FOUND` is precisely a statement about that budget, and the change makes the budget buy fractionally less.
- **The four new test classes have not been seen failing.** `testEveryMessageKeyIsAskedFor` in particular is asserted above to currently report zero dead keys; that is my re-derivation of its rule in Python, run against the current tree, not the class itself.
- **The eight bundles were not loaded.** Key-set arithmetic only. A key that parses differently under `Properties` than under the line-splitting both the test and I use would not show up - though there are no continuation lines in the file, which is the realistic way that happens.

**Not looked at in any depth:** AUS-C1, AUS-C3, AUS-C4, MKR-C1, MKR-C2, MKR-C3, SVA-C1, OP2-C1, OP2-C3, OP2-C4, OP2-C12 from `48ea5a49` and `75f69423`. I read their commit messages and spot-checked MKR-C3 and MKR-C4 (both correct, and MKR-C4 is what OP3-B2 is measured against) and CS3-C4 (OP3-D10). The rest were taken on trust in favour of the seven items the brief named.

**Not looked at:** `docs/manual-tests/tests.md` MT-465..MT-469 as written, and the triage database `6a397786` rewrote. The five manual tests are the right answer to the things no test can read - a real Alt click, a modal Cancel, a Central Station that is switched off - but whether each one asks the discriminating question is a separate read.

**Not looked at:** the two commits after the sweep (`7ac40714`, `4e2d02b5`, the tooltip shortening). They change only message values, add and remove no keys, and so do not disturb OP3-B1's arithmetic - which is the only reason they were checked at all.

**One thing I deliberately did not do:** re-derive the dead key set by hand a third time. The two derivations above disagreed at first - a shell-level backslash problem in my own extractor, not in the code - and the second one was written with the Java regex transcribed character by character and checked against three keys the first had wrongly called dead (`network.hostReachable`, `route.ui.frameAndMoreProblems`, `search.ui.labelSimilarLocomotives`, all live). The numbers in OP3-B1 come from the corrected extractor and land exactly on UIX-C4's own 237, which is the best corroboration available without running the class.
